/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/

package soctest.server.savegame;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCGame;
import soc.game.SOCGame.SeatLockState;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.game.SOCRoutePiece;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCTradeOffer;
import soc.server.SOCGameListAtServer;
import soc.server.genericServer.StringConnection;
import soc.server.savegame.GameLoaderJSON;
import soc.server.savegame.SavedGameModel;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link GameLoaderJSON} and {@link SavedGameModel},
 * using JSON test artifacts under {@code /src/test/resources/resources/savegame}.
 *
 * @since 2.4.00
 */
public class TestLoadgame
{
    /** dummy server setup, to avoid IllegalStateException from {@link GameLoaderJSON} */
    @BeforeClass
    public static void setup()
    {
        SavedGameModel.glas = new SOCGameListAtServer(null);
    }

    /**
     * Attempt to load a savegame test artifact by calling {@link GameLoaderJSON#loadGame(File)}.
     * Doesn't postprocess or call {@link SavedGameModel#resumePlay(boolean)}.
     * If not found, will fail an {@code assertNotNull}. Doesn't try to catch
     * {@link SavedGameModel.UnsupportedSGMOperationException} or SGM's other declared runtime exceptions.
     * @param testResFilename  Base name of test artifact, like {@code "classic-botturn.game.json"},
     *     to be loaded from {@code /src/test/resources/resources/savegame/}
     * @throws IOException if file can't be loaded
     * @throws SavedGameModel.UnsupportedSGMOperationException if unsupported feature; see {@code GameLoaderJson.loadGame}
     */
    private static SavedGameModel load(final String testRsrcFilename)
        throws IOException, SavedGameModel.UnsupportedSGMOperationException
    {
        final String rsrcPath = "/resources/savegame/" + testRsrcFilename;
        final URL u = TestLoadgame.class.getResource(rsrcPath);
        assertNotNull("Couldn't find " + rsrcPath, u);
        final File f;
        try
        {
            f = new File(u.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("unlikely internal error", e);
        }

        return GameLoaderJSON.loadGame(f);
    }

    /**
     * Check savegame/game data against expected contents.
     * @param names Player names
     * @param locks  SeatLockStates to check, or {@code null} to skip;
     *     individual elements can also be {@code null} to skip checking them
     * @param totalVP Players' total VP
     * @param resources Players' resources (clay, ore, sheep, wheat, wood)
     * @param pieceCounts Players' piece counts (roads, settlements, cities, and ships remaining; knights/soldiers played)
     */
    private static void checkPlayerData
        (final SavedGameModel sgm, final String[] names, final SeatLockState[] locks, final int[] totalVP,
         final int[][] resources, final int[][] pieceCounts)
    {
        final SOCGame ga = sgm.getGame();
        assertEquals("maxPlayers", names.length, ga.maxPlayers);

        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            final SavedGameModel.PlayerInfo pi = sgm.playerSeats[pn];
            final SOCPlayer pl = ga.getPlayer(pn);
            assertEquals("names[" + pn + "]", names[pn], pi.name);
            assertEquals("names[" + pn + "]", names[pn], pl.getName());
            if ((locks != null) && (locks[pn] != null))
            {
                if (sgm.playerSeatLocks != null)  // might be null in sgm when testing SOCGame default lock values
                    assertEquals("locks[" + pn + "]", locks[pn], sgm.playerSeatLocks[pn]);
                assertEquals("locks[" + pn + "]", locks[pn], ga.getSeatLock(pn));
            }
            assertEquals("totalVP[" + pn + "]", totalVP[pn], pi.totalVP);
            assertEquals("totalVP[" + pn + "]", totalVP[pn], pl.getTotalVP());

            final SOCResourceSet rs = (resources[pn] != null)
                ? new SOCResourceSet(resources[pn])
                : new SOCResourceSet();
            assertTrue(rs.equals(pl.getResources()));

            for (int ptype = SOCPlayingPiece.ROAD; ptype <= SOCPlayingPiece.SHIP; ++ptype)
                assertEquals(pieceCounts[pn][ptype], pl.getNumPieces(ptype));
                // ROAD, SETTLEMENT, CITY, SHIP == 0, 1, 2, 3: tested in TestPlayingPiece
            assertEquals(pieceCounts[pn][4], pl.getNumKnights());

            // TODO piece locations, etc

            final boolean isVacant = (names[pn] == null);
            assertEquals(isVacant, pi.isSeatVacant);
            assertEquals(isVacant, ga.isSeatVacant(pn));
            if (isVacant)
                continue;

            final boolean isBot = (names[pn].startsWith("robot") || names[pn].startsWith("droid"));
                // not a completely rigorous test, but works for our savegame artifacts
            assertEquals(isBot, pi.isRobot);
            assertEquals(isBot, pl.isRobot());
        }

    }

    /**
     * Do the required {@link SOCGameListAtServer} setup for this loaded game
     * so {@link SavedGameModel#resumePlay(boolean)} won't complain.
     * Doesn't actually call {@code resumePlay(..)}.
     */
    private static void fillSeatsForResume(final SavedGameModel sgm)
    {
        assertNotNull(sgm.findSeatsNeedingBots());

        final SOCGame ga = sgm.getGame();
        final String gaName = ga.getName();

        SavedGameModel.glas.addGame(gaName, null, false);
            // this isn't the server-side call, but that one's much more work to set up for

        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            final String pName = sgm.playerSeats[pn].name;
            boolean isVacant = (null == pName);
            assertEquals(isVacant, ga.isSeatVacant(pn));
            if (isVacant)
                continue;

            // GameList membership required by findSeatsNeedingBots
            assertFalse(SavedGameModel.glas.isMember(pName, gaName));
            StringConnection sc = new StringConnection();
            sc.setData(pName);
            SavedGameModel.glas.addMember(sc, gaName);
            assertTrue(SavedGameModel.glas.isMember(pName, gaName));
        }
    }

    @Test
    public void testBasicLoading()
        throws IOException
    {
        final SavedGameModel sgm = load("classic-botturn.game.json");
        final SOCGame ga = sgm.getGame();

        assertNotNull(ga.savedGameModel);
        assertEquals("game name", "classic", sgm.gameName);
        assertEquals("game name", sgm.gameName, ga.getName());
        assertEquals(1, ga.getCurrentPlayerNumber());
        assertEquals(9, ga.getCurrentDice());
        assertEquals("gamestate", SOCGame.PLAY1, sgm.gameState);
        assertEquals("gamestate", SOCGame.LOADING, ga.getGameState());

        assertEquals(4, sgm.playerSeats.length);
        assertEquals(4, ga.maxPlayers);
        assertFalse(ga.hasSeaBoard);

        assertEquals(SOCBoard.BOARD_ENCODING_ORIGINAL, ga.getBoard().getBoardEncodingFormat());
        assertEquals("robberHex", 155, ga.getBoard().getRobberHex());

        final String[] NAMES = {null, "robot 4", "robot 2", "debug"};
        final SeatLockState[] LOCKS =
            {SeatLockState.UNLOCKED, SeatLockState.CLEAR_ON_RESET, SeatLockState.LOCKED, SeatLockState.UNLOCKED};
        final int[] TOTAL_VP = {0, 3, 2, 2};
        final int[][] RESOURCES = {null, {0, 1, 0, 2, 0}, {2, 2, 0, 0, 0}, {1, 3, 1, 0, 1}};
        final int[][] PIECE_COUNTS = {{15, 5, 4, 0, 0}, {13, 4, 3, 0, 0}, {13, 3, 4, 0, 0}, {12, 3, 4, 0, 0}};
        checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS);
    }

    /**
     * If any non-vacant player hasn't joined the game before {@link SavedGameModel#resumePlay(boolean)},
     * should throw an exception
     */
    @Test(expected=java.util.MissingResourceException.class)
    public void testNeedPlayers()
        throws IOException
    {
        final SavedGameModel sgm = load("classic-botturn.game.json");
        sgm.resumePlay(true);
    }

    /** Test successful {@link SavedGameModel#resumePlay(boolean)} after all player connections have joined. */
    @Test
    public void testResumePlay()
        throws IOException
    {
        final String GAME_NAME = "classic";
        final SavedGameModel sgm = load("classic-botturn.game.json");
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", GAME_NAME, sgm.gameName);
        assertTrue(ga.isSeatVacant(0));

        fillSeatsForResume(sgm);
        sgm.resumePlay(true);
        assertEquals("gamestate", SOCGame.PLAY1, ga.getGameState());
    }

    /** When loading a game that's over, should call {@link SavedGameModel#resumePlay(boolean)} successfully */
    @Test
    public void testLoadResumeGameOver()
        throws IOException
    {
        final SavedGameModel sgm = load("classic-over.game.json");
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "classic", sgm.gameName);
        assertEquals("gamestate", SOCGame.OVER, sgm.gameState);

        sgm.resumePlay(true);
        assertEquals("gamestate", SOCGame.OVER, ga.getGameState());
        assertEquals(2, ga.getPlayerWithWin().getPlayerNumber());
        assertNull(ga.savedGameModel);
    }

    /** Test loading and resuming a 6-player game, during Special Building Phase. */
    @Test
    public void testLoad6PlayerSBP()
        throws IOException
    {
        final SavedGameModel sgm = load("test6p-sbp.game.json");
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "test6p-sbp", sgm.gameName);
        assertEquals(1, ga.getCurrentPlayerNumber());
        assertEquals("gamestate", SOCGame.SPECIAL_BUILDING, sgm.gameState);
        assertEquals("should be 6 players", 6, sgm.playerSeats.length);
        assertEquals("should be 6 players", 6, ga.maxPlayers);
        assertFalse(ga.hasSeaBoard);
        assertNull(sgm.playerSeatLocks);  // locks deliberately omitted in file, to test loading with defaults

        assertEquals(SOCBoard.BOARD_ENCODING_6PLAYER, ga.getBoard().getBoardEncodingFormat());
        assertEquals("robberHex", 21, ga.getBoard().getRobberHex());

        final String[] NAMES = {null, "p", "droid 1", "robot 2", "robot 4", "debug"};
        final SeatLockState[] LOCKS =
            {SeatLockState.UNLOCKED, SeatLockState.UNLOCKED, SeatLockState.UNLOCKED,
             SeatLockState.UNLOCKED, SeatLockState.UNLOCKED, SeatLockState.UNLOCKED};
        final int[] TOTAL_VP = {0, 2, 2, 2, 2, 2};
        final int[][] RESOURCES = {null, {2, 0, 1, 1, 1}, null, {0, 1, 3, 0, 2}, {0, 0, 2, 0, 0}, {4, 0, 0, 0, 3}};
        final int[][] PIECE_COUNTS =
            {{15, 5, 4, 0, 0}, {13, 3, 4, 0, 0}, {12, 3, 4, 0, 0}, {13, 3, 4, 0, 1}, {12, 3, 4, 0, 0}, {13, 3, 4, 0, 0}};
        checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS);

        fillSeatsForResume(sgm);
        sgm.resumePlay(true);
        assertEquals("gamestate", SOCGame.SPECIAL_BUILDING, ga.getGameState());
    }

    /** Test loading and resuming a Sea Board game, including open/closed ship routes ({@link SOCShip#isClosed()}). */
    @Test
    public void testLoadSeaBoard()
        throws IOException
    {
        final SavedGameModel sgm = load("testsea-closed.game.json");
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "testgame-sea-closedships", sgm.gameName);
        assertEquals(0, ga.getCurrentPlayerNumber());
        assertEquals("gamestate", SOCGame.PLAY1, sgm.gameState);
        assertEquals("oldgamestate", SOCGame.PLAY1, sgm.oldGameState);
        assertEquals(4, sgm.playerSeats.length);
        assertEquals(4, ga.maxPlayers);
        assertTrue(ga.hasSeaBoard);

        assertEquals(SOCBoard.BOARD_ENCODING_LARGE, ga.getBoard().getBoardEncodingFormat());
        assertEquals("robberHex", 2312, ga.getBoard().getRobberHex());
        assertEquals("pirateHex", 2316, ((SOCBoardLarge) ga.getBoard()).getPirateHex());

        final String[] NAMES = {"debug", "robot 4", "robot 2", null};
        final SeatLockState[] LOCKS =
            {SeatLockState.UNLOCKED, SeatLockState.CLEAR_ON_RESET, SeatLockState.UNLOCKED, SeatLockState.UNLOCKED};
        final int[] TOTAL_VP = {6, 2, 5, 0};
        final int[][] RESOURCES = {{0, 1, 0, 3, 0}, null, {1, 0, 0, 1, 0}, null};
        final int[][] PIECE_COUNTS = {{8, 1, 4, 10, 1}, {11, 3, 4, 15, 0}, {9, 2, 3, 13, 0}, {15, 5, 4, 15, 0}};
        checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS);

        final int[] SHIPS_OPEN_P0 = {2305, 2561, 3074};
        final int[][] SHIPS_CLOSED = {{2562, 2818}, null, {1035, 1036}, null};
        SOCPlayer pl = ga.getPlayer(0);
        for (final int edge : SHIPS_OPEN_P0)
        {
            final SOCRoutePiece rp = pl.getRoadOrShip(edge);
            final String assertDesc = "pn 0 ship at edge " + edge;
            assertNotNull(assertDesc, rp);
            assertFalse(assertDesc, rp.isRoadNotShip());
            assertFalse("shouldn't be closed: " + assertDesc, ((SOCShip) rp).isClosed());
        }
        for (int pn = 0; pn < 4; ++pn)
        {
            if (! ga.isSeatVacant(pn))
                assertEquals(1, pl.getStartingLandAreasEncoded());

            if (null == SHIPS_CLOSED[pn])
                continue;

            pl = ga.getPlayer(pn);
            for (final int edge : SHIPS_CLOSED[pn])
            {
                final SOCRoutePiece rp = pl.getRoadOrShip(edge);
                final String assertDesc = "pn " + pn + " ship at edge " + edge;
                assertNotNull(assertDesc, rp);
                assertFalse(assertDesc, rp.isRoadNotShip());
                assertTrue("should be closed: " + assertDesc, ((SOCShip) rp).isClosed());
            }
        }

        fillSeatsForResume(sgm);
        sgm.resumePlay(true);
        assertEquals("gamestate", SOCGame.PLAY1, ga.getGameState());
    }

    /**
     * Test parsing and loading game where various field contents are invalid.
     * @since 2.4.00
     */
    public void testLoadBadFieldContents()
        throws IOException
    {
        final SavedGameModel sgm = load("bad-field-contents.game.json");
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "bad-field-contents", sgm.gameName);
        assertEquals(1, ga.getCurrentPlayerNumber());
        assertEquals("gamestate", SOCGame.PLAY1, sgm.gameState);
        assertEquals(4, sgm.playerSeats.length);

        // in file, playerSeatLocks[0] is "INVALID_SEATLOCK_STATE"
        assertNotNull(sgm.playerSeatLocks);
        SeatLockState[] LOCKS =
            new SeatLockState[]{ null, SeatLockState.CLEAR_ON_RESET, SeatLockState.LOCKED, SeatLockState.UNLOCKED };
        for (int pn = 0; pn < 4; ++pn)
        {
            SeatLockState modelLock = sgm.playerSeatLocks[pn];
            assertEquals("locks[" + pn + "]", LOCKS[pn], modelLock);
            if (modelLock == null)
                modelLock = SeatLockState.UNLOCKED;
            assertEquals("locks[" + pn + "]", modelLock, ga.getSeatLock(pn));
        }
    }

    /** Test loading a game where current player, another player are making trade offers. */
    @Test
    public void testLoadTradeOffers()
        throws IOException
    {
        final SavedGameModel sgm = load("tradeoffers.game.json");
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "tradeoffers", sgm.gameName);
        assertEquals(0, ga.getCurrentPlayerNumber());
        assertEquals("gamestate", SOCGame.PLAY1, sgm.gameState);
        assertEquals("oldgamestate", SOCGame.PLAY1, sgm.oldGameState);
        assertEquals(4, sgm.playerSeats.length);

        SOCTradeOffer tr = ga.getPlayer(0).getCurrentOffer();
        assertNotNull("player(0) trade offer", tr);
        assertEquals(0, tr.getFrom());
        assertArrayEquals(new boolean[]{false, true, true, true}, tr.getTo());
        assertEquals(new SOCResourceSet(0, 0, 0, 0, 1, 0), tr.getGiveSet());
        assertEquals(new SOCResourceSet(1, 0, 0, 0, 0, 0), tr.getGetSet());

        assertNull("player(1) trade offer", ga.getPlayer(1).getCurrentOffer());

        tr = ga.getPlayer(2).getCurrentOffer();
        assertNotNull("player(2) trade offer", tr);
        assertEquals(2, tr.getFrom());
        assertArrayEquals(new boolean[]{true, false, false, false}, tr.getTo());
        assertEquals(new SOCResourceSet(0, 0, 0, 1, 0, 0), tr.getGiveSet());
        assertEquals(new SOCResourceSet(0, 0, 0, 0, 1, 0), tr.getGetSet());

        assertNull("player(3) trade offer", ga.getPlayer(3).getCurrentOffer());
    }

    /** Test loading and resuming a simple scenario, including SVP for {@code _SC_SEAC}. */
    @Test
    public void testLoadScenSimple4ISL()
        throws IOException
    {
        final SavedGameModel sgm = load("testscen-simple-4isl.game.json");
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "testscen-simple-4isl", sgm.gameName);
        assertEquals(3, ga.getCurrentPlayerNumber());
        assertEquals("gamestate", SOCGame.PLAY1, sgm.gameState);
        assertEquals("oldgamestate", SOCGame.PLAY1, sgm.oldGameState);
        assertEquals(4, sgm.playerSeats.length);
        assertEquals(4, ga.maxPlayers);
        assertTrue(ga.hasSeaBoard);
        assertEquals("SC_4ISL", ga.getGameOptionStringValue("SC"));

        assertEquals(SOCBoard.BOARD_ENCODING_LARGE, ga.getBoard().getBoardEncodingFormat());
        final SOCBoardLarge board = (SOCBoardLarge) ga.getBoard();
        assertEquals("robberHex", 777, board.getRobberHex());
        assertEquals("pirateHex", 1805, board.getPirateHex());

        final String[] NAMES = {null, "robot 5", "droid 1", "debug"};
        final int[] TOTAL_VP = {0, 2, 2, 5};
        final int[][] RESOURCES = {null, {0, 0, 0, 3, 1}, {1, 0, 1, 1, 0}, {0, 0, 1, 0, 0}};
        final int[][] PIECE_COUNTS = {{15, 5, 4, 15, 0}, {13, 3, 4, 15, 0}, {13, 3, 4, 15, 0}, {15, 2, 4, 11, 0}};
        checkPlayerData(sgm, NAMES, null, TOTAL_VP, RESOURCES, PIECE_COUNTS);

        final SOCPlayer plDebug = ga.getPlayer(3);
        assertEquals(3, plDebug.getStartingLandAreasEncoded());
        assertEquals(2, plDebug.getSpecialVP());
        assertEquals(1, plDebug.getScenarioSVPLandAreas());

        final int[] SHIPS_OPEN = {0x609};
        final int[] SHIPS_CLOSED = {0x406, 0x405, 0x404};
        for (final int edge : SHIPS_OPEN)
        {
            final SOCRoutePiece rp = plDebug.getRoadOrShip(edge);
            final String assertDesc = "debug pl ship at edge " + edge;
            assertNotNull(assertDesc, rp);
            assertFalse(assertDesc, rp.isRoadNotShip());
            assertFalse("shouldn't be closed: " + assertDesc, ((SOCShip) rp).isClosed());
        }
        for (final int edge : SHIPS_CLOSED)
        {
            final SOCRoutePiece rp = plDebug.getRoadOrShip(edge);
            final String assertDesc = "debug pl ship at edge " + edge;
            assertNotNull(assertDesc, rp);
            assertFalse(assertDesc, rp.isRoadNotShip());
            assertTrue("should be closed: " + assertDesc, ((SOCShip) rp).isClosed());
        }

        fillSeatsForResume(sgm);
        sgm.resumePlay(true);
        assertEquals("gamestate", SOCGame.PLAY1, ga.getGameState());

        // Another ship & coastal settlement in a landarea we've already built to
        ga.putPiece(new SOCShip(plDebug, 0x505, board));
        ga.putPiece(new SOCSettlement(plDebug, 0x605, board));
        assertEquals(2, plDebug.getSpecialVP());
        assertEquals(1, plDebug.getScenarioSVPLandAreas());

        // Now build to a new LA
        ga.putPiece(new SOCShip(plDebug, 0x604, board));
        ga.putPiece(new SOCShip(plDebug, 0x704, board));
        ga.putPiece(new SOCSettlement(plDebug, 0x804, board));
        assertEquals(4, plDebug.getSpecialVP());
        assertEquals(3, plDebug.getScenarioSVPLandAreas());
    }

    /** Test loading a not-yet-supported scenario: {@code bad-scen-unsupported.game.json} */
    @Test(expected=SavedGameModel.UnsupportedSGMOperationException.class)
    public void testLoadUnsupportedScenFOG()
        throws IOException
    {
        try
        {
            final SavedGameModel sgm = load("bad-scen-unsupported.game.json");
            // should throw exception before here
            assertEquals("game name", "hopefully-unreached-code", sgm.gameName);
        } catch (SavedGameModel.UnsupportedSGMOperationException e) {
            assertEquals("SC_FOG", e.param1);
            assertEquals("_SC_FOG", e.param2);
            throw e;
        }
    }

}
