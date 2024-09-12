/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020-2023 Jeremy D Monin <jeremy@nand.net>
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.MissingResourceException;

import soc.game.GameAction;
import soc.game.GameAction.ActionType;
import soc.game.GameAction.EffectType;
import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCDevCard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCGame.SeatLockState;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCInventory;
import soc.game.SOCInventoryItem;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCRoutePiece;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCTradeOffer;
import soc.message.SOCPlayerElement.PEType;
import soc.server.SOCGameListAtServer;
import soc.server.SOCServer;
import soc.server.genericServer.StringConnection;
import soc.server.savegame.GameLoaderJSON;
import soc.server.savegame.SavedGameModel;
import soc.server.savegame.SavedGameModel.PlayerInfo;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link GameLoaderJSON} and {@link SavedGameModel},
 * and some basic gameplay behavior in loaded and resumed games,
 * using JSON test artifacts under {@code /src/test/resources/resources/savegame}.
 *
 * @see TestSavegame
 * @since 2.4.00
 */
public class TestLoadgame
{
    private static SOCServer srv;

    /** dummy server setup, to avoid IllegalStateException etc from {@link GameLoaderJSON} */
    @BeforeClass
    public static void setup()
        throws Exception
    {
        try
        {
            assertTrue(null != Class.forName("com.google.gson.Gson"));
        } catch(Throwable th) {
            String msg = "TestLoadgame: can't load com.google.gson.Gson, check CLASSPATH: " + th;
            System.err.println(msg);
            fail(msg);
        }

        srv = new SOCServer("dummy", 0, null, null);

        // - create the inactive game option used in bad-gameopt-inactive.game.json
        //   and testLoadInactiveGameopt, and no other unit test methods in this class
        //   (same as TestGameOptions.testFlagInactiveActivate)
        final SOCGameOptionSet knowns = srv.knownOpts;
        final SOCGameOption newKnown2 = new SOCGameOption
            ("_TESTACT", 2000, 2000, 0, 0, 0xFFFF, SOCGameOption.FLAG_INACTIVE_HIDDEN,
             "For unit test");
        assertNull(knowns.getKnownOption("_TESTACT", false));
        knowns.addKnownOption(newKnown2);
        assertNotNull(knowns.getKnownOption("_TESTACT", false));
        assertTrue(newKnown2.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertFalse(newKnown2.hasFlag(SOCGameOption.FLAG_ACTIVATED));
    }

    /**
     * Attempt to load a savegame test artifact by calling
     * {@link GameLoaderJSON#loadGame(File, SOCServer)}.
     * Doesn't postprocess or call {@link SavedGameModel#resumePlay(boolean)}.
     * If not found, will fail an {@code assertNotNull}. Doesn't try to catch
     * {@link SavedGameModel.UnsupportedSGMOperationException} or SGM's other declared runtime exceptions,
     * most of which aren't declared as {@code throws} here.
     *
     * @param testResFilename  Base name of test artifact, like {@code "classic-botturn.game.json"},
     *     to be loaded from {@code /src/test/resources/resources/savegame/}
     * @param server  SOCServer that will host this game later; its {@link SOCServer#getGameList()} is needed here
     * @throws IllegalArgumentException if {@code server} is null
     * @throws IOException if file can't be loaded
     * @throws SavedGameModel.UnsupportedSGMOperationException if unsupported feature; see {@code GameLoaderJson.loadGame}
     * @see soctest.server.TestGameEventLog#load(String, boolean, boolean)
     */
    public static SavedGameModel load(final String testRsrcFilename, final SOCServer server)
        throws IllegalArgumentException, IOException, SavedGameModel.UnsupportedSGMOperationException
    {
        if (server == null)
            throw new IllegalArgumentException("server");

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

        return GameLoaderJSON.loadGame(f, server);
    }

    /**
     * Check savegame/game data against expected contents.
     * @param names Player names
     * @param locks  SeatLockStates to check, or {@code null} to skip;
     *     individual elements can also be {@code null} to skip checking them
     * @param totalVP Players' total VP
     * @param resources Players' resources (clay, ore, sheep, wheat, wood), or {@code null} to skip
     * @param pieceCounts Players' piece counts (roads, settlements, cities, and ships remaining; knights/soldiers played)
     *     or {@code null} to skip
     * @param pieceLocations Some or all players' piece locations, or {@code null} to skip checking them:
     *     For each player, either {@code null} or an int[][] array of piece types' expected locations
     *     (roads, settlements, cities, ships)
     */
    static void checkPlayerData
        (final SavedGameModel sgm, final String[] names, final SeatLockState[] locks, final int[] totalVP,
         final int[][] resources, final int[][] pieceCounts, final int[][][] pieceLocations)
    {
        final SOCGame ga = sgm.getGame();
        final SOCBoard board = ga.getBoard();
        assertEquals("maxPlayers", names.length, ga.maxPlayers);

        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            final SavedGameModel.PlayerInfo pi = sgm.playerSeats[pn];
            final SOCPlayer pl = ga.getPlayer(pn);
            assertEquals("names SGM[" + pn + "]", names[pn], pi.name);
            assertEquals("names ga[" + pn + "]", names[pn], pl.getName());
            if ((locks != null) && (locks[pn] != null))
            {
                if (sgm.playerSeatLocks != null)  // might be null in sgm when testing SOCGame default lock values
                    assertEquals("locks SGM[" + pn + "]", locks[pn], sgm.playerSeatLocks[pn]);
                assertEquals("locks ga[" + pn + "]", locks[pn], ga.getSeatLock(pn));
            }
            assertEquals("totalVP SGM[" + pn + "]", totalVP[pn], pi.totalVP);
            assertEquals("totalVP ga[" + pn + "]", totalVP[pn], pl.getTotalVP());

            if (resources != null)
            {
                final SOCResourceSet rs = (resources[pn] != null)
                    ? new SOCResourceSet(resources[pn])
                    : new SOCResourceSet();
                assertTrue("resources[" + pn + "]", rs.equals(pl.getResources()));
            }

            if (pieceCounts != null)
            {
                for (int ptype = SOCPlayingPiece.ROAD; ptype <= SOCPlayingPiece.SHIP; ++ptype)
                    assertEquals("pieceCounts[" + pn + "][" + ptype + "]", pieceCounts[pn][ptype], pl.getNumPieces(ptype));
                    // ROAD, SETTLEMENT, CITY, SHIP == 0, 1, 2, 3: tested in TestPlayingPiece
                assertEquals("pieceCounts[" + pn + "][4]", pieceCounts[pn][4], pl.getNumKnights());
            }

            if ((pieceLocations != null) && (pieceLocations[pn] != null))
            {
                final int[][] pieceLocs = pieceLocations[pn];

                // consistency-check expected data
                assertEquals(4, pieceLocs.length);
                assertEquals(SOCPlayer.ROAD_COUNT - pieceCounts[pn][0], pieceLocs[0].length);
                assertEquals(SOCPlayer.SETTLEMENT_COUNT - pieceCounts[pn][1], pieceLocs[1].length);
                assertEquals(SOCPlayer.CITY_COUNT - pieceCounts[pn][2], pieceLocs[2].length);
                if (ga.hasSeaBoard)
                    assertEquals(SOCPlayer.SHIP_COUNT - pieceCounts[pn][3], pieceLocs[3].length);
                else
                    assertEquals(0, pieceLocs[3].length);

                // check vs actual piece counts and locations
                assertEquals("pn[" + pn + "].getRoadsAndShips() count",
                    pieceLocs[0].length + pieceLocs[3].length, pl.getRoadsAndShips().size());
                for (final int edge : pieceLocs[0])
                {
                    SOCRoutePiece piece = pl.getRoadOrShip(edge);
                    assertNotNull("pn[" + pn + "] road at 0x" + Integer.toHexString(edge), piece);
                    assertTrue("pn[" + pn + "] should be road at 0x" + Integer.toHexString(edge),
                        piece.isRoadNotShip());
                    piece = board.roadOrShipAtEdge(edge);
                    assertNotNull(piece);
                    assertEquals(SOCPlayingPiece.ROAD, piece.getType());
                    assertTrue(piece.isRoadNotShip());
                    assertEquals(pn, piece.getPlayerNumber());
                }
                assertEquals("pn[" + pn + "].getSettlements() count",
                    pieceLocs[1].length, pl.getSettlements().size());
                for (final int node : pieceLocs[1])
                {
                    SOCPlayingPiece piece = pl.getSettlementOrCityAtNode(node);
                    assertNotNull("pn[" + pn + "] settlement at 0x" + Integer.toHexString(node), piece);
                    assertEquals("pn[" + pn + "] should be settlement at 0x" + Integer.toHexString(node),
                        SOCPlayingPiece.SETTLEMENT, piece.getType());
                    piece = board.settlementAtNode(node);
                    assertNotNull(piece);
                    assertEquals(SOCPlayingPiece.SETTLEMENT, piece.getType());
                    assertEquals(pn, piece.getPlayerNumber());
                }
                assertEquals("pn[" + pn + "].getCities() count",
                    pieceLocs[2].length, pl.getCities().size());
                for (final int node : pieceLocs[2])
                {
                    SOCPlayingPiece piece = pl.getSettlementOrCityAtNode(node);
                    assertNotNull("pn[" + pn + "] city at 0x" + Integer.toHexString(node), piece);
                    assertEquals("pn[" + pn + "] should be city at 0x" + Integer.toHexString(node),
                        SOCPlayingPiece.CITY, piece.getType());
                    piece = board.settlementAtNode(node);
                    assertNotNull(piece);
                    assertEquals(SOCPlayingPiece.CITY, piece.getType());
                    assertEquals(pn, piece.getPlayerNumber());
                }
                for (final int edge : pieceLocs[3])
                {
                    SOCRoutePiece piece = pl.getRoadOrShip(edge);
                    assertNotNull("pn[" + pn + "] ship at 0x" + Integer.toHexString(edge), piece);
                    assertFalse("pn[" + pn + "] should be ship not road at 0x" + Integer.toHexString(edge),
                        piece.isRoadNotShip());
                    piece = board.roadOrShipAtEdge(edge);
                    assertNotNull(piece);
                    assertEquals(SOCPlayingPiece.SHIP, piece.getType());
                    assertFalse(piece.isRoadNotShip());
                    assertEquals(pn, piece.getPlayerNumber());
                }
            }

            final boolean isVacant = (names[pn] == null);
            assertEquals("isVacant SGM[" + pn + "]", isVacant, pi.isSeatVacant);
            assertEquals("isVacant ga[" + pn + "]", isVacant, ga.isSeatVacant(pn));
            if (isVacant)
                continue;

            final boolean isBot = names[pn].startsWith("robot") || names[pn].startsWith("droid")
                || names[pn].startsWith("samplebot");
                // not a completely rigorous test, but works for our savegame artifacts
            assertEquals("isBot SGM[" + pn + "]", isBot, pi.isRobot);
            assertEquals("isBot ga[" + pn + "]", isBot, pl.isRobot());
        }

    }

    /**
     * Do the required {@link SOCGameListAtServer} setup for this loaded game
     * so {@link SavedGameModel#resumePlay(boolean)} won't complain.
     * Doesn't actually call {@code resumePlay(..)}.
     *<P>
     * Don't call this method if the loaded game doesn't need this setup:
     * See {@link SavedGameModel#findSeatsNeedingBots()}.
     */
    static void fillSeatsForResume(final SavedGameModel sgm)
    {
        assertNotNull
            ("Should fillSeatsForResume not be called? Couldn't find non-vacant seats still needing player",
             sgm.findSeatsNeedingBots());

        final SOCGame ga = sgm.getGame();
        final String gaName = ga.getName();

        SOCGameListAtServer glas = sgm.glas;
        glas.addGame(gaName, null, false);
            // this isn't the proper server-side addGame call, but that one's much more work to set up for

        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            final String pName = sgm.playerSeats[pn].name;
            boolean isVacant = (null == pName);
            assertEquals(isVacant, ga.isSeatVacant(pn));
            if (isVacant)
                continue;

            // GameList membership required by findSeatsNeedingBots
            assertFalse(glas.isMember(pName, gaName));
            StringConnection sc = new StringConnection();
            sc.setData(pName);
            glas.addMember(sc, gaName);
            assertTrue(glas.isMember(pName, gaName));
        }
    }

    @Test
    public void testBasicLoading()
        throws IOException
    {
        checkReloaded_ClassicBotturn(load("classic-botturn.game.json", srv));
    }

    /**
     * Check a loaded game model to see if it has same known data as savegame artifact {@code "classic-botturn.game.json"}.
     * @param sgm  Loaded game with contents of {@code "classic-botturn"}; not null
     * @see #checkReloaded_BadFieldContents(SavedGameModel, boolean)
     * @see soctest.server.TestRecorder#testLoadAndBasicSequences(soc.extra.server.RecordingSOCServer, String, List, boolean)
     */
    static void checkReloaded_ClassicBotturn(final SavedGameModel sgm)
    {
        assertNotNull(sgm);

        final SOCGame ga = sgm.getGame();

        assertNotNull(ga.savedGameModel);
        assertEquals("game name", "classic", sgm.gameName);
        assertEquals("game name", sgm.gameName, ga.getName());
        assertEquals(1, ga.getCurrentPlayerNumber());
        assertEquals(9, ga.getCurrentDice());
        assertEquals("gamestate", SOCGame.PLAY1, sgm.gameState);
        assertEquals("gamestate", SOCGame.LOADING, ga.getGameState());
        final int dur = ga.getDurationSeconds(), secondsFromExpected = Math.abs(dur - 49);
        assertTrue("ga.getDurationSeconds() is ~ 49 (actual " + dur + ')', secondsFromExpected < 3);

        assertEquals("BC=t4,N7=t7,PL=4,RD=f", sgm.gameOptions);
        {
            final SOCGameOptionSet opts = ga.getGameOptions();
            assertEquals(4, opts.size());

            SOCGameOption opt = opts.get("BC");
            assertNotNull(opt);
            assertTrue(opt.getBoolValue());
            assertEquals(4, opt.getIntValue());

            opt = opts.get("N7");
            assertNotNull(opt);
            assertTrue(opt.getBoolValue());
            assertEquals(7, opt.getIntValue());

            opt = opts.get("PL");
            assertNotNull(opt);
            assertEquals(4, opt.getIntValue());
            assertFalse(opt.getBoolValue());  // default; PL is int, not intbool

            opt = opts.get("RD");
            assertNotNull(opt);
            assertFalse(opt.getBoolValue());
            assertEquals(0, opt.getIntValue());  // default; RD is bool, not intbool
        }

        assertEquals(4, sgm.playerSeats.length);
        assertEquals(4, ga.maxPlayers);
        assertFalse(ga.hasSeaBoard);

        assertEquals(SOCBoard.BOARD_ENCODING_ORIGINAL, ga.getBoard().getBoardEncodingFormat());
        assertEquals("robberHex", 155, ga.getBoard().getRobberHex());

        final String[] NAMES = {null, "robot 4", "robot 2", "debug"};
        // other tests and release-testing require these lock values for these seats; don't change them
        final SeatLockState[] LOCKS =
            {SeatLockState.UNLOCKED, SeatLockState.CLEAR_ON_RESET, SeatLockState.LOCKED, SeatLockState.UNLOCKED};
        final int[] TOTAL_VP = {0, 3, 2, 3};
        final int[][] RESOURCES = {null, {0, 1, 0, 2, 0}, {2, 2, 0, 0, 0}, {1, 3, 1, 0, 1}};
        final int[][] PIECE_COUNTS = {{15, 5, 4, 0, 0}, {13, 4, 3, 0, 0}, {13, 3, 4, 0, 0}, {12, 3, 4, 0, 0}};
        final int[][][] PIECE_LOCATIONS =
            {
                null,
                {{0xBA, 0xB8}, {0xC9}, {0xBA}, {}},
                {{0x44, 0x66}, {0x45, 0x76}, {}, {}},
                {{0x69, 0x63, 0x52}, {0x69, 0x74}, {}, {}},
            };
        checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS, PIECE_LOCATIONS);
        assertFalse(sgm.warnHasHumanPlayerWithBotName);

        assertArrayEquals("devCardDeck",
            new int[]{ 8, 9, 6, 3, 9, 9, 9, 9, 9, 9, 9, 3, 4, 1, 9, 2, 7, 5, 9, 9, 9, 2, 1, 9, 9 },
            ga.getDevCardDeck());
        assertEquals(-1, sgm.warnDevCardDeckUnknownTypeAtIndex);

        checkExpectedPlayerDevCards(ga);
    }

    /**
     * If any non-vacant player hasn't joined the game before {@link SavedGameModel#resumePlay(boolean)},
     * should throw an exception
     */
    @Test(expected=MissingResourceException.class)
    public void testNeedPlayers()
        throws IOException, MissingResourceException
    {
        final SavedGameModel sgm = load("classic-botturn.game.json", srv);
        sgm.resumePlay(true);
    }

    /** Test successful {@link SavedGameModel#resumePlay(boolean)} after all player connections have joined. */
    @Test
    public void testResumePlay()
        throws IOException
    {
        final String GAME_NAME = "classic";
        final SavedGameModel sgm = load("classic-botturn.game.json", srv);
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
        final SavedGameModel sgm = load("classic-over.game.json", srv);
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "classic", sgm.gameName);
        assertEquals("gamestate", SOCGame.OVER, sgm.gameState);
        final int dur = ga.getDurationSeconds(), secondsFromExpected = Math.abs(dur - 471);
        assertTrue("ga.getDurationSeconds() is ~ 471 (actual " + dur + ')', secondsFromExpected < 3);

        sgm.resumePlay(true);
        assertEquals("gamestate", SOCGame.OVER, ga.getGameState());
        assertEquals(2, ga.getPlayerWithWin().getPlayerNumber());
        assertNull(ga.savedGameModel);
    }

    /**
     * Test loading a game saved with {@link SavedGameModel#MODEL_VERSION} 2300:
     * Checks for backwards-compatible parsing of any features changed since then:
     *<UL>
     * <LI> Players' dev cards ({@link PlayerInfo#oldDevCards}, {@code newDevCards})
     *      were ints in 2300, changed to strings in 2400.
     * <LI> Dev card deck also uses those strings in 2400.
     * <LI> Playing piece types ({@link SOCPlayingPiece#getType()} within {@link PlayerInfo#pieces})
     *      were ints in 2300, changed to strings in 2400.
     *</UL>
     */
    @Test
    public void testLoadModelVersion2300()
        throws IOException
    {
        final SavedGameModel sgm = load("modelversion-2300.game.json", srv);
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "testmodel-2300-3h", sgm.gameName);
        assertEquals("gamestate", SOCGame.PLAY1, sgm.gameState);
        assertEquals(6, sgm.playerSeats.length);
        assertEquals(6, ga.maxPlayers);

        final String[] NAMES = {null, "p2", "samplebot2", "p3", "robot 2", "debug"};
        final SeatLockState[] LOCKS =
            {SeatLockState.UNLOCKED, SeatLockState.UNLOCKED, SeatLockState.UNLOCKED,
             SeatLockState.UNLOCKED, SeatLockState.UNLOCKED, SeatLockState.UNLOCKED};
        final int[] TOTAL_VP = {0, 2, 2, 3, 2, 3};
        final int[][] RESOURCES = {null, {0, 0, 1, 1, 3}, {1, 2, 0, 3, 1}, {1, 1, 0, 0, 3}, {2, 3, 0, 1, 0}, {0, 0, 0, 3, 3}};
        final int[][] PIECE_COUNTS =
            {{15, 5, 4, 0, 0}, {11, 3, 4, 0, 0}, {13, 3, 4, 0, 0}, {12, 4, 3, 0, 0}, {13, 3, 4, 0, 0}, {12, 3, 4, 0, 0}};
        // check piece locations for player 3 only, since they're the player with a city
        final int[][][] PIECE_LOCATIONS_ONLY_PL3 =
            {
                null, null, null,
                {{0x45, 0x14, 0x24}, {0x14}, {0x56}, {}},
                null, null
            };
        checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS, PIECE_LOCATIONS_ONLY_PL3);

        // Check game and players' dev cards:

        assertArrayEquals("devCardDeck",
            new int[]{ 9, 3, 9, 4, 1, 9, 9, 5, 9, 9, 2, 9, 9, 2, 8, 1, 2, 9, 9, 9, 7, 3, 9, 3, 1, 9, 9, 9, 9, 6, 9, 9, 9, 9 },
            ga.getDevCardDeck());

        checkExpectedPlayerDevCards(ga);
    }

    /**
     * For artifacts {@code classic-botturn.game.json} and {@code modelversion-2300.game.json},
     * check loaded/parsed game for the expected player dev cards
     * ({@link PlayerInfo#oldDevCards}, {@link PlayerInfo#newDevCards newDevCards}).
     */
    private static void checkExpectedPlayerDevCards(final SOCGame ga)
    {
        // Bot pn 2: new discovery/year of plenty(2); old knight(9)
        SOCInventory cardsInv = ga.getPlayer(2).getInventory();
        List<SOCInventoryItem> cards = cardsInv.getByState(SOCInventory.NEW);
        assertEquals(1, cards.size());
        assertEquals(SOCDevCardConstants.DISC, cards.get(0).itype);
        cards = cardsInv.getByState(SOCInventory.PLAYABLE);
        assertEquals(1, cards.size());
        assertEquals(SOCDevCardConstants.KNIGHT, cards.get(0).itype);
        cards = cardsInv.getByState(SOCInventory.KEPT);
        assertEquals(0, cards.size());

        // Human pn MAX - 1: new roadbuilding(1); old discovery/year of plenty(2), university(6)
        cardsInv = ga.getPlayer(ga.maxPlayers - 1).getInventory();
        cards = cardsInv.getByState(SOCInventory.NEW);
        assertEquals(1, cards.size());
        assertEquals(SOCDevCardConstants.ROADS, cards.get(0).itype);
        cards = cardsInv.getByState(SOCInventory.PLAYABLE);
        assertEquals(1, cards.size());
        assertEquals(SOCDevCardConstants.DISC, cards.get(0).itype);
        cards = cardsInv.getByState(SOCInventory.KEPT);
        assertEquals(1, cards.size());
        assertEquals(SOCDevCardConstants.UNIV, cards.get(0).itype);
    }

    /**
     * Test loading and resuming a 6-player game, during Special Building Phase.
     * Artifact {@code "test6p-sbp.game.json"} is used here and in
     * extraTest {@code soctest.server.TestActionsMessages.test6pAskSpecialBuild()}.
     */
    @Test
    public void testLoad6PlayerSBP()
        throws IOException
    {
        final SavedGameModel sgm = load("test6p-sbp.game.json", srv);
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
        checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS, null);

        assertArrayEquals("devCardDeck",
            new int[]{ 3, 9, 8, 9, 1, 9, 9, 9, 9, 3, 9, 9, 9, 5, 4, 2, 6, 9, 9, 9, 1, 2, 2, 1, 9, 7, 3, 9, 9, 9, 9, 9 },
            ga.getDevCardDeck());

        fillSeatsForResume(sgm);
        sgm.resumePlay(true);
        assertEquals("gamestate", SOCGame.SPECIAL_BUILDING, ga.getGameState());
    }

    /**
     * Test loading and resuming a Sea Board game, including open/closed ship routes ({@link SOCShip#isClosed()}).
     * Because {@code testsea-closed.game.json} is a known setup, use it to test a few basic game actions
     * like {@link SOCGame#putPiece(SOCPlayingPiece)} and {@link SOCGame#moveShip(SOCShip, int)}.
     */
    @Test
    public void testLoadSeaBoard()
        throws IOException
    {
        final int CURRENT_PLAYER_NUMBER = 0;
        final SavedGameModel sgm = load("testsea-closed.game.json", srv);
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "testgame-sea-closedships", sgm.gameName);
        assertEquals(CURRENT_PLAYER_NUMBER, ga.getCurrentPlayerNumber());
        assertEquals("gamestate", SOCGame.PLAY1, sgm.gameState);
        assertEquals("oldgamestate", SOCGame.PLAY1, sgm.oldGameState);
        assertEquals(4, sgm.playerSeats.length);
        assertEquals(4, ga.maxPlayers);
        assertTrue(ga.hasSeaBoard);

        assertEquals(SOCBoard.BOARD_ENCODING_LARGE, ga.getBoard().getBoardEncodingFormat());
        final SOCBoardLarge board = (SOCBoardLarge) ga.getBoard();
        assertEquals("robberHex", 2312, board.getRobberHex());
        assertEquals("pirateHex", 2316, board.getPirateHex());

        final String[] NAMES = {"debug", "robot 4", "robot 2", null};
        final SeatLockState[] LOCKS =
            {SeatLockState.UNLOCKED, SeatLockState.CLEAR_ON_RESET, SeatLockState.UNLOCKED, SeatLockState.UNLOCKED};
        final int[] TOTAL_VP = {6, 2, 5, 0};
        final int[][] RESOURCES = {{0, 1, 0, 3, 0}, null, {1, 0, 0, 1, 0}, null};
        final int[][] PIECE_COUNTS = {{8, 1, 4, 10, 1}, {11, 3, 4, 15, 0}, {9, 2, 3, 13, 0}, {15, 5, 4, 15, 0}};
        final int[][][] PIECE_LOCATIONS =
            {
                {
                    {0x507, 0x606, 0x602, 0x702, 0x802, 0x903, 0xA03}, {0x407, 0x602, 0xA03, 0xC02},
                    {}, {0xA02, 0xB02, 0x901, 0xA01, 0xC02}
                },
                {{0x608, 0x708, 0x907, 0x806}, {0x608, 0xA07}, {}, {}},
                {{0x109, 0x008, 0x409, 0x40A, 0x40D, 0x30E}, {0x209, 0x409, 0x40B}, {0x40D}, {0x40B, 0x40C}},
                null
            };
        checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS, PIECE_LOCATIONS);

        final int[] SHIPS_OPEN_P0 = {2305, 2561, 3074};  // 0x901, 0xA01, 0xC02
        final int[][] SHIPS_CLOSED = {{2562, 2818}, null, {1035, 1036}, null};  // 0xA02, 0xB02; 0x40B, 0x40C
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
        pl = ga.getPlayer(CURRENT_PLAYER_NUMBER);

        assertNull(board.settlementAtNode(0x606));
        assertTrue(pl.canPlaceSettlement(0x606));
        ga.putPiece(new SOCSettlement(pl, 0x606, board));
        assertTrue("settlement built at 0x606", board.settlementAtNode(0x606) instanceof SOCSettlement);
        assertEquals(new GameAction(ActionType.BUILD_PIECE, SOCPlayingPiece.SETTLEMENT, 0x606, 0), ga.getLastAction());
        assertFalse(pl.canPlaceSettlement(0x606));

        assertNull(board.roadOrShipAtEdge(0x605));
        assertTrue(pl.isPotentialRoad(0x605));
        assertFalse(pl.isPotentialRoad(0x604));
        ga.putPiece(new SOCRoad(pl, 0x605, board));
        assertTrue("road built at 0x605", board.roadOrShipAtEdge(0x605) instanceof SOCRoad);
        assertEquals(new GameAction(ActionType.BUILD_PIECE, SOCPlayingPiece.ROAD, 0x605, 0), ga.getLastAction());
        assertFalse(pl.isPotentialRoad(0x605));
        assertTrue(pl.isPotentialRoad(0x604));

        assertTrue(pl.isPotentialCity(0x606));
        ga.putPiece(new SOCCity(pl, 0x606, board));
        assertTrue("city built at 0x606", board.settlementAtNode(0x606) instanceof SOCCity);
        assertEquals(new GameAction(ActionType.BUILD_PIECE, SOCPlayingPiece.CITY, 0x606, 0), ga.getLastAction());
        assertFalse(pl.isPotentialCity(0x606));

        assertNull(board.roadOrShipAtEdge(0xc01));
        assertTrue(pl.isPotentialShip(0xc01));
        assertFalse(pl.isPotentialShip(0xd01));
        ga.putPiece(new SOCShip(pl, 0xc01, board));
        assertTrue("ship built at 0xc01", board.roadOrShipAtEdge(0xc01) instanceof SOCShip);
        assertEquals(new GameAction(ActionType.BUILD_PIECE, SOCPlayingPiece.SHIP, 0xc01, 0), ga.getLastAction());
        assertFalse(pl.isPotentialShip(0xc01));
        assertTrue(pl.isPotentialShip(0xd01));
        assertTrue(ga.getShipsPlacedThisTurn().contains(Integer.valueOf(0xc01)));

        // Can't move ship built this turn, but can move ship built previously:
        // built this turn, after loading:
        SOCRoutePiece srp = board.roadOrShipAtEdge(0xc01);
        assertTrue(srp instanceof SOCShip);
        assertEquals(CURRENT_PLAYER_NUMBER, srp.getPlayerNumber());
        assertFalse(((SOCShip) srp).isClosed());
        assertNull("ship at 0xc01 should not be movable", ga.canMoveShip(CURRENT_PLAYER_NUMBER, 0xc01));
        // built this turn, before saving (shipsPlacedThisTurn in saved game):
        srp = board.roadOrShipAtEdge(0xc02);
        assertTrue(srp instanceof SOCShip);
        assertEquals(CURRENT_PLAYER_NUMBER, srp.getPlayerNumber());
        assertNull("ship at 0xc02 should not be movable", ga.canMoveShip(CURRENT_PLAYER_NUMBER, 0xc02));
        // previously built:
        srp = board.roadOrShipAtEdge(0x901);
        assertTrue(srp instanceof SOCShip);
        assertEquals(CURRENT_PLAYER_NUMBER, srp.getPlayerNumber());
        assertEquals("ship at 0x901 can be moved", srp, ga.canMoveShip(CURRENT_PLAYER_NUMBER, 0x901));
        assertTrue(pl.canMoveShip((SOCShip) srp));
        assertNull(board.roadOrShipAtEdge(0xa00));
        assertEquals("ship at 0x901 can be moved to 0xa00", srp, ga.canMoveShip(CURRENT_PLAYER_NUMBER, 0x901, 0xa00));
        ga.moveShip((SOCShip) srp, 0xa00);
        assertNull(board.roadOrShipAtEdge(0x901));
        srp = board.roadOrShipAtEdge(0xa00);
        assertTrue(srp instanceof SOCShip);
        assertEquals(CURRENT_PLAYER_NUMBER, srp.getPlayerNumber());
        assertEquals(new GameAction(ActionType.MOVE_PIECE, SOCPlayingPiece.SHIP, 0x901, 0xa00), ga.getLastAction());
        assertTrue(ga.getShipsPlacedThisTurn().contains(Integer.valueOf(0xa00)));
        // quick direct test of addShipPlacedThisTurn
        assertFalse(ga.getShipsPlacedThisTurn().contains(Integer.valueOf(0xc04)));
        ga.addShipPlacedThisTurn(0xc04);
        assertTrue(ga.getShipsPlacedThisTurn().contains(Integer.valueOf(0xc04)));

        // Close ship route from building a settlement:
        // (close from building and moving a ship are tested in TestActionsMessages.testUndoBuildAndMove)

        assertNull(board.roadOrShipAtEdge(0xd01));
        assertTrue(pl.isPotentialShip(0xd01));
        ga.putPiece(new SOCShip(pl, 0xd01, board));
        srp = board.roadOrShipAtEdge(0xd01);
        assertTrue("ship built at 0xd01", srp instanceof SOCShip);
        assertFalse(((SOCShip) srp).isClosed());
        assertEquals(new GameAction(ActionType.BUILD_PIECE, SOCPlayingPiece.SHIP, 0xd01, 0), ga.getLastAction());
        assertFalse(pl.isPotentialShip(0xd01));
        assertTrue(pl.isPotentialSettlement(0xe01));

        assertNull(board.settlementAtNode(0xe01));
        assertTrue(pl.canPlaceSettlement(0xe01));
        ga.putPiece(new SOCSettlement(pl, 0xe01, board));
        assertTrue("settlement built at 0xe01", board.settlementAtNode(0xe01) instanceof SOCSettlement);
        assertTrue(((SOCShip) board.roadOrShipAtEdge(0xc01)).isClosed());
        assertTrue(((SOCShip) board.roadOrShipAtEdge(0xd01)).isClosed());
        GameAction act = ga.getLastAction();
        assertEquals(ActionType.BUILD_PIECE, act.actType);
        assertEquals(SOCPlayingPiece.SETTLEMENT, act.param1);
        assertEquals(0xe01, act.param2);
        assertEquals(0, act.param3);
        assertNotNull(act.effects);
        assertEquals(1, act.effects.size());
        GameAction.Effect ef = act.effects.get(0);
        assertEquals(EffectType.CLOSE_SHIP_ROUTE, ef.eType);
        // params should contain both of the ships which became closed
        assertEquals(2, ef.params.length);
        assertTrue(Arrays.stream(ef.params).anyMatch(i -> i == 0xc01));
        assertTrue(Arrays.stream(ef.params).anyMatch(i -> i == 0xd01));

        // Basic dev card check:

        assertTrue(ga.getNumDevCards() > 0);
        assertFalse("not enough resources", ga.couldBuyDevCard(CURRENT_PLAYER_NUMBER));
        pl.addRolledResources(SOCDevCard.COST);
        assertTrue(ga.couldBuyDevCard(CURRENT_PLAYER_NUMBER));
        ga.buyDevCard();
        assertNull(ga.getLastAction());  // later might be BUY_DEV_CARD, esp. if that becomes undoable
    }

    /**
     * Test loading a game with Dev Card stats elements for {@link SOCPlayer#numRBCards} etc.
     * @since 2.5.00
     */
    @Test
    public void testLoadPlayerElementStats()
        throws IOException
    {
        final SavedGameModel sgm = load("devcard-stats.game.json", srv);
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "devcard-stats", sgm.gameName);
        assertEquals("gamestate", SOCGame.PLAY1, sgm.gameState);
        assertEquals(4, ga.maxPlayers);
        final int dur = ga.getDurationSeconds(), secondsFromExpected = Math.abs(dur - 163);
        assertTrue("ga.getDurationSeconds() is ~ 163 (actual " + dur + ')', secondsFromExpected < 3);

        final String[] NAMES = {null, "robot 4", "robot 3", "debug"};
        final int[] TOTAL_VP = {0, 3, 3, 3};
        checkPlayerData(sgm, NAMES, null, TOTAL_VP, null, null, null);

        final int[][] PLAYER_DEVCARD_STATS = {{0, 0, 0}, {0, 0, 2}, {0, 0, 0}, {1, 1, 0}};
        final int[][] PLAYER_CARDS_PLAYED_LISTS =
            {null, {SOCDevCardConstants.ROADS, SOCDevCardConstants.ROADS},
             null, {SOCDevCardConstants.MONO, SOCDevCardConstants.DISC}};
        for (int pn = 0; pn < 4; ++pn)
        {
            final SOCPlayer pl = ga.getPlayer(pn);
            final SavedGameModel.PlayerInfo pi = sgm.playerSeats[pn];

            int amount = PLAYER_DEVCARD_STATS[pn][0];
            assertEquals(amount, pl.numDISCCards);
            if (amount != 0)
                assertEquals(Integer.valueOf(amount), pi.elements.get(PEType.NUM_PLAYED_DEV_CARD_DISC));
            else
                assertFalse(pi.elements.containsKey(PEType.NUM_PLAYED_DEV_CARD_DISC));

            amount = PLAYER_DEVCARD_STATS[pn][1];
            assertEquals(amount, pl.numMONOCards);
            if (amount != 0)
                assertEquals(Integer.valueOf(amount), pi.elements.get(PEType.NUM_PLAYED_DEV_CARD_MONO));
            else
                assertFalse(pi.elements.containsKey(PEType.NUM_PLAYED_DEV_CARD_MONO));

            amount = PLAYER_DEVCARD_STATS[pn][2];
            assertEquals(amount, pl.numRBCards);
            if (amount != 0)
                assertEquals(Integer.valueOf(amount), pi.elements.get(PEType.NUM_PLAYED_DEV_CARD_ROADS));
            else
                assertFalse(pi.elements.containsKey(PEType.NUM_PLAYED_DEV_CARD_ROADS));

            final int[] expected = PLAYER_CARDS_PLAYED_LISTS[pn];
            final List<Integer> played = pl.getDevCardsPlayed();
            if (expected == null)
            {
                assertNull("pn " + pn + ": no devcards played", played);
            } else {
                List<Integer> expectedList = new ArrayList<>();
                for (int ctype : expected)
                    expectedList.add(ctype);
                assertEquals("pn " + pn + " devcards played", expectedList, played);
            }
        }
    }

    /**
     * Test parsing and loading game where various field contents are invalid.
     */
    @Test
    public void testLoadBadFieldContents()
        throws IOException
    {
        final SavedGameModel sgm = load("bad-field-contents.game.json", srv);
        checkReloaded_BadFieldContents(sgm, true);
    }

    /**
     * Check a loaded game model to see if it has same known data as savegame artifact {@code "bad-field-contents.game.json"}.
     * Calls {@link SavedGameModel#resumePlay(boolean)} if {@code doGameActions}.
     * @param sgm  Loaded game with contents of {@code "bad-field-contents"}; not null
     * @param doGameActions  If true, call {@code resumePlay}, then do and check some in-game actions
     * @see #checkReloaded_ClassicBotturn(SavedGameModel)
     */
    static void checkReloaded_BadFieldContents(final SavedGameModel sgm, final boolean doGameActions)
    {
        assertNotNull(sgm);

        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "bad-field-contents", sgm.gameName);
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

        // game has some invalid/unknown elements; rest of elements should load OK
        assertEquals(1, ga.getFirstPlayer());
        assertEquals(1, ga.getCurrentPlayerNumber());
        assertEquals(2, ga.getRoundCount());  // is after unknowns in elements
        assertEquals(null, ga.getPlayerWithLargestArmy());
        assertEquals(null, ga.getPlayerWithLongestRoad());

        // player 1 has some invalid/unknown elements; rest of elements should load OK
        final String[] NAMES = {null, "robot 4", "robot 2", "debug"};
        final int[] TOTAL_VP = {0, 3, 2, 2};
        final int[][] RESOURCES = {null, {0, 1, 0, 2, 0}, {2, 2, 0, 0, 0}, {1, 3, 1, 0, 1}};
        final int[][] PIECE_COUNTS = {{15, 5, 4, 0, 0}, {13, 4, 3, 0, 2}, {13, 3, 4, 0, 0}, {12, 3, 4, 0, 0}};
        checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS, null);
        assertEquals("PEType.NUMKNIGHTS still recognized after unknown elements", 2, ga.getPlayer(1).getNumKnights());

        // player 1 oldDevCards has some unknown type strings and numbers;
        // should still parse the rest of them properly
        assertEquals(0, SOCDevCard.getCardType("NOT_A_DEFINED_NAME"));
        assertEquals("42000", SOCDevCard.getCardTypeName(42000));  // if any of these become known, fix is to
        assertEquals("42001", SOCDevCard.getCardTypeName(42001));  // edit the artifact to use still-unknown numbers/names
        List<SOCInventoryItem> cards = ga.getPlayer(1).getInventory().getByState(SOCInventory.PLAYABLE);
        int[] expected = {SOCDevCardConstants.DISC, SOCDevCardConstants.UNKNOWN, 42000, 42001, SOCDevCardConstants.ROADS};
        assertEquals(expected.length, cards.size());
        for (int i = 0; i < expected.length; ++i)
            assertEquals("pn[1].oldDevCards[" + i + "]", expected[i], cards.get(i).itype);

        // devCardDeck has an unknown type string;
        // should still parse the rest of them properly
        int n = sgm.devCardDeck.size() - 1;
        assertEquals(0, SOCDevCard.getCardType("NOT_A_DEFINED_CARD_NAME"));
        assertEquals("devCardDeck[n-2]", SOCDevCardConstants.ROADS, sgm.devCardDeck.get(n-2).intValue());
        assertEquals("devCardDeck[n-1]", SOCDevCardConstants.UNKNOWN, sgm.devCardDeck.get(n-1).intValue());
        assertEquals("devCardDeck[n]", SOCDevCardConstants.KNIGHT, sgm.devCardDeck.get(n).intValue());
        assertEquals(n-1, sgm.warnDevCardDeckUnknownTypeAtIndex);

        if (! doGameActions)
            return;

        // can buy dev cards, including that unknown one
        fillSeatsForResume(sgm);
        sgm.resumePlay(true);
        assertEquals("gamestate", SOCGame.PLAY1, ga.getGameState());
        assertEquals(SOCDevCardConstants.KNIGHT,  ga.buyDevCard());
        assertEquals(SOCDevCardConstants.UNKNOWN, ga.buyDevCard());
        assertEquals(SOCDevCardConstants.ROADS,   ga.buyDevCard());
    }

    /** Test loading a game where current player, another player are making trade offers. */
    @Test
    public void testLoadTradeOffers()
        throws IOException
    {
        final SavedGameModel sgm = load("tradeoffers.game.json", srv);
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

    /**
     * Test loading a game whose players have {@link SavedGameModel.PlayerInfo#resTradeStats}:
     * {@code tradestats.game.json}.
     * @since 2.6.00
     */
    @Test
    public void testLoadResTradeStats()
        throws IOException
    {
        final SavedGameModel sgm = load("tradestats.game.json", srv);
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "ts", sgm.gameName);
        assertEquals(4, sgm.playerSeats.length);

        final int[] PLAYER_VP = {0, 2, 3, 4};
        final int[][][][] PLAYER_TRADE_STATS =
            {
                {
                    {null, null}, {null, null}, {null, null}, {null, null},
                    {null, null}, {null, null}, {null, null}, {null, null}
                },
                {
                    {{0, 0, 6, 0, 0}, {1, 1, 0, 0, 0}}, {null, null}, {null, null}, {null, null},
                    {null, null}, {null, null}, {null, null}, {null, null}
                },
                {
                    {null, null}, {null, null}, {null, null}, {null, null},
                    {null, null}, {null, null}, {null, null}, {{1, 0, 1, 0, 0}, {0, 1, 0, 1, 0}}
                },
                {
                    {{0, 0, 3, 3, 0}, {1, 0, 0, 0, 1}}, {null, null},
                    {{0, 2, 0, 0, 0}, {0, 0, 1, 0, 0}}, {null, null},
                    {null, null}, {null, null},
                    {{0, 0, 4, 0, 0}, {0, 0, 0, 1, 0}},
                    {{0, 1, 0, 1, 0}, {1, 0, 1, 0, 0}}
                }
            };
        for (int pn = 0; pn < 4; ++pn)
        {
            final SOCPlayer pl = ga.getPlayer(pn);
            final String desc = "player(" + pn + ")";

            final boolean expectVacant = (PLAYER_VP[pn] == 0);
            assertEquals(desc, expectVacant, ga.isSeatVacant(pn));
            assertEquals(desc, PLAYER_VP[pn], pl.getTotalVP());

            final SOCResourceSet[][] plStats = pl.getResourceTradeStats();
            for (int trType = 0; trType < PLAYER_TRADE_STATS[0].length; ++trType)
            {
                final String trDesc = desc + "(type=" + trType + ")";

                int[] expectGive = PLAYER_TRADE_STATS[pn][trType][0],
                    expectReceive = PLAYER_TRADE_STATS[pn][trType][1];
                if (expectGive == null)
                    assertTrue(trDesc + " give", plStats[0][trType].isEmpty());
                else
                    assertArrayEquals(trDesc + " give", expectGive, plStats[0][trType].getAmounts(false));

                if (expectReceive == null)
                    assertTrue(trDesc + " receive", plStats[1][trType].isEmpty());
                else
                    assertArrayEquals(trDesc + " receive", expectReceive, plStats[1][trType].getAmounts(false));
            }
        }
    }

    /** Test loading and resuming a simple scenario, including SVP for {@code _SC_SEAC}. */
    @Test
    public void testLoadScenSimple4ISL()
        throws IOException
    {
        final SavedGameModel sgm = load("testscen-simple-4isl.game.json", srv);
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
        checkPlayerData(sgm, NAMES, null, TOTAL_VP, RESOURCES, PIECE_COUNTS, null);

        final SOCPlayer plDebug = ga.getPlayer(3);
        assertEquals(3, plDebug.getStartingLandAreasEncoded());
        assertEquals(2, plDebug.getSpecialVP());
        assertEquals(1, plDebug.getScenarioSVPLandAreas());
        final List<SOCPlayer.SpecialVPInfo> svpInfo = plDebug.getSpecialVPInfo();
        assertNotNull(svpInfo);
        assertEquals(1, svpInfo.size());
        {
            final SOCPlayer.SpecialVPInfo info = svpInfo.get(0);
            assertEquals(2, info.svp);
            assertEquals("event.svp.sc_seac.island", info.desc);
        }

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
        // Shouldn't change SVP
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
    @Test
    public void testLoadUnsupportedScenFOG()
        throws IOException
    {
        try
        {
            final SavedGameModel sgm = load("bad-scen-unsupported.game.json", srv);
            // should throw exception before here
            assertEquals("game name", "hopefully-unreached-code", sgm.gameName);
        } catch (SavedGameModel.UnsupportedSGMOperationException e) {
            assertEquals("SC_FOG", e.param1);
            assertEquals("_SC_FOG", e.param2);
        }
    }

    /**
     * Test loading an unknown {@link SOCGameOption}: {@code bad-gameopt-unknown.game.json}
     * @since 2.5.00
     */
    @Test
    public void testLoadUnknownGameopt()
        throws IOException
    {
        assertNull(srv.knownOpts.getKnownOption("_NOEXIST", false));

        try
        {
            final SavedGameModel sgm = load("bad-gameopt-unknown.game.json", srv);
            // should throw exception before here
            assertEquals("game name", "hopefully-unreached-code", sgm.gameName);
        } catch (IllegalArgumentException e) {
            final String msg = e.getMessage();
            assertTrue
                ("IllegalArgExcep message text should contain: \"unknown option(s): _NOEXIST: unknown\" but was: " + msg,
                 msg.contains("unknown option(s): _NOEXIST: unknown"));
        }
    }

    /**
     * Test loading an inactive {@link SOCGameOption}: {@code bad-gameopt-inactive.game.json}
     * @since 2.5.00
     */
    @Test
    public void testLoadInactiveGameopt()
        throws IOException
    {
        SOCGameOption knownOpt = srv.knownOpts.getKnownOption("_TESTACT", false);
        assertNotNull(knownOpt);
        assertTrue(knownOpt.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertFalse(knownOpt.hasFlag(SOCGameOption.FLAG_ACTIVATED));

        try
        {
            final SavedGameModel sgm = load("bad-gameopt-inactive.game.json", srv);
            // should throw exception before here
            assertEquals("game name", "hopefully-unreached-code", sgm.gameName);
        } catch (IllegalArgumentException e) {
            final String msg = e.getMessage();
            assertTrue
                ("IllegalArgExcep message text should contain: \"unknown option(s): _TESTACT: inactive\" but was: " + msg,
                 msg.contains("unknown option(s): _TESTACT: inactive"));
        }

        // activate the inactive game opt and try again; this is safe to do because
        // only this test method uses _TESTACT
        srv.knownOpts.activate("_TESTACT");
        knownOpt = srv.knownOpts.getKnownOption("_TESTACT", false);
        assertNotNull(knownOpt);
        assertFalse(knownOpt.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertTrue(knownOpt.hasFlag(SOCGameOption.FLAG_ACTIVATED));

        final SavedGameModel sgm = load("bad-gameopt-inactive.game.json", srv);
        // should no longer throw exception before here
        assertEquals("game name", "bad-gameopt-inactive", sgm.gameName);
        SOCGame ga = sgm.getGame();
        assertNotNull(ga);
        // should have active SGO _TESTACT == 777
        SOCGameOption loadedOpt = ga.getGameOptions().get("_TESTACT");
        assertNotNull("loaded game obj should have _TESTACT gameopt", loadedOpt);
        assertEquals(777, loadedOpt.getIntValue());
        assertFalse(loadedOpt.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertTrue(loadedOpt.hasFlag(SOCGameOption.FLAG_ACTIVATED));
        // and test another way to access its value
        assertEquals(777, ga.getGameOptionIntValue("_TESTACT", 0, false));
    }

}
