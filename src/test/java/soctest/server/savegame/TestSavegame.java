/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020-2024 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Arrays;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

import soc.game.SOCBoard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCGame.SeatLockState;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCInventory;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.game.SOCScenario;
import soc.game.SOCTradeOffer;
import soc.message.SOCPlayerElement.PEType;
import soc.server.SOCServer;
import soc.server.savegame.GameLoaderJSON;
import soc.server.savegame.GameSaverJSON;
import soc.server.savegame.SavedGameModel;
import soc.server.savegame.SavedGameModel.UnsupportedSGMOperationException;
import soc.util.Version;
import soctest.game.TestPlayer;

/**
 * A few tests for {@link GameSaverJSON} and {@link SavedGameModel},
 * using JSON test artifacts under {@code /src/test/resources/resources/savegame}
 * and a junit temporary folder.
 *
 * @see TestLoadgame
 * @since 2.4.00
 */
public class TestSavegame
{
    private static SOCServer srv;

    /** dummy server setup, to avoid IllegalStateException etc from {@link GameLoaderJSON} or {@link GameSaverJSON} */
    @BeforeClass
    public static void setup()
        throws Exception
    {
        srv = new SOCServer("dummy", 0, null, null);
    }

    /** This folder and all contents are created at start of each test method, deleted at end of it */
    @Rule
    public TemporaryFolder testTmpFolder = new TemporaryFolder();

    /** Can't save during initial placement */
    @Test(expected=IllegalStateException.class)
    public void testSaveInitialPlacement()
        throws IOException
    {
        final SOCGame ga = new SOCGame("basic", null, null);
        ga.addPlayer("p0", 0);
        ga.addPlayer("third", 3);

        ga.startGame();  // create board layout
        assertEquals(SOCGame.START1A, ga.getGameState());
        GameSaverJSON.saveGame(ga, testTmpFolder.getRoot(), "wontsave.game.json", srv);
    }

    /** Saving a game which uses a scenario not yet supported by savegame ({@code SC_PIRI}) should fail. */
    @Test(expected=UnsupportedSGMOperationException.class)
    public void testSaveUnsupportedScenario()
        throws IOException
    {
        final SOCGameOptionSet opts = new SOCGameOptionSet();
        SOCGameOption opt = srv.knownOpts.getKnownOption("SC", true);
        opt.setStringValue(SOCScenario.K_SC_PIRI);
        opts.put(opt);
        assertNull(opts.adjustOptionsToKnown(srv.knownOpts, true, null));  // apply SC's scenario game opts
        assertTrue(opts.containsKey(SOCGameOptionSet.K_SC_PIRI));
        final SOCGame ga = new SOCGame("scen", opts, srv.knownOpts);

        UnsupportedSGMOperationException checkResult = null;
        try
        {
            SavedGameModel.checkCanSave(ga);
        } catch (UnsupportedSGMOperationException e) {
            checkResult = e;
        }
        assertNotNull(checkResult);
        assertEquals("admin.savegame.cannot_save.scen", checkResult.getMessage());
        assertEquals("SC_PIRI", checkResult.param1);

        GameSaverJSON.saveGame(ga, testTmpFolder.getRoot(), "wontsave.game.json", srv);
    }

    /**
     * Save a basic game, reload it, check field contents.
     * For a more detailed test, see {@link #testSaveLoadPlayerMiscFields()}.
     */
    @Test
    public void testBasicSaveLoad()
        throws IOException
    {
        final SOCGame gaSave = new SOCGame("basic", null, null);
        gaSave.addPlayer("p0", 0);
        gaSave.addPlayer("third", 3);
        assertFalse(gaSave.isSeatVacant(0));
        assertTrue(gaSave.isSeatVacant(1));
        assertTrue(gaSave.isSeatVacant(2));
        assertFalse(gaSave.isSeatVacant(3));
        gaSave.setSeatLock(0, SeatLockState.LOCKED);
        gaSave.setSeatLock(3, SeatLockState.CLEAR_ON_RESET);

        gaSave.startGame();  // create board layout
        assertEquals(SOCGame.START1A, gaSave.getGameState());
        final int firstPN = gaSave.getCurrentPlayerNumber();
        assertEquals(firstPN, gaSave.getFirstPlayer());

        // no pieces placed, but can't save during initial placement
        gaSave.setGameState(SOCGame.ROLL_OR_CARD);
        gaSave.getPlayer(0).getResources().add(new SOCResourceSet(1, 3, 0, 2, 0, 0));

        File saveFile = testTmpFolder.newFile("basic.game.json");
        GameSaverJSON.saveGame(gaSave, testTmpFolder.getRoot(), saveFile.getName(), srv);

        final SavedGameModel sgm = GameLoaderJSON.loadGame(saveFile, srv);
        assertNotNull(sgm);
        assertEquals(SavedGameModel.MODEL_VERSION, sgm.modelVersion);
        assertEquals(Version.versionNumber(), sgm.savedByVersion);
        final SOCGame ga = sgm.getGame();

        assertEquals("basic", ga.getName());
        assertEquals(4, ga.maxPlayers);
        assertEquals(firstPN, ga.getCurrentPlayerNumber());
        assertEquals(firstPN, ga.getFirstPlayer());

        final String[] NAMES = {"p0", null, null, "third"};
        final SeatLockState[] LOCKS =
            {SeatLockState.LOCKED, SeatLockState.UNLOCKED, SeatLockState.UNLOCKED, SeatLockState.CLEAR_ON_RESET};
        final int[] TOTAL_VP = {0, 0, 0, 0};
        final int[][] RESOURCES = {{1, 3, 0, 2, 0}, null, null, {0, 0, 0, 0, 0}};
        final int[] PIECES_ALL = {15, 5, 4, 0, 0};
        final int[][] PIECE_COUNTS = {PIECES_ALL, PIECES_ALL, PIECES_ALL, PIECES_ALL};
        TestLoadgame.checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS, null);
    }

    /** Save and reload when players have trade offers  */
    @Test
    public void testTradeOffersSaveLoad()
        throws IOException
    {
        final String GAME_NAME = "trades";
        final SOCGame gaSave = new SOCGame(GAME_NAME, null, null);
        gaSave.addPlayer("p0", 0);
        gaSave.addPlayer("third", 3);

        gaSave.startGame();  // create board layout
        assertEquals(SOCGame.START1A, gaSave.getGameState());

        // no pieces placed, but can't save during initial placement
        gaSave.setGameState(SOCGame.ROLL_OR_CARD);
        final SOCPlayer pl0 = gaSave.getPlayer(0),
            pl3 = gaSave.getPlayer(3);
        pl0.getResources().add(new SOCResourceSet(1, 3, 0, 2, 0, 0));
        pl3.getResources().add(new SOCResourceSet(0, 0, 2, 2, 1, 0));

        final SOCResourceSet PL0_GIVES = new SOCResourceSet(0, 2, 0, 1, 0, 0),  // multiple rsrc types & amounts
            PL0_GETS = new SOCResourceSet(0, 0, 1, 0, 1, 0),   // multiple types, single amount
            PL3_GIVES = new SOCResourceSet(0, 0, 2, 0, 0, 0),  // single rsrc type, multiple amount
            PL3_GETS = new SOCResourceSet(1, 0, 0, 0, 0, 0);   // single rsrc type & amount
        pl0.setCurrentOffer(new SOCTradeOffer
            (GAME_NAME, 0, new boolean[]{false, false, false, true}, PL0_GIVES, PL0_GETS));
        pl3.setCurrentOffer(new SOCTradeOffer
            (GAME_NAME, 0, new boolean[]{true, false, false, false}, PL3_GIVES, PL3_GETS));

        File saveFile = testTmpFolder.newFile("trades.game.json");
        GameSaverJSON.saveGame(gaSave, testTmpFolder.getRoot(), saveFile.getName(), srv);

        final SavedGameModel sgm = GameLoaderJSON.loadGame(saveFile, srv);
        assertNotNull(sgm);
        final SOCGame ga = sgm.getGame();

        assertEquals(GAME_NAME, ga.getName());
        assertEquals(4, ga.maxPlayers);

        final String[] NAMES = {"p0", null, null, "third"};
        final int[] TOTAL_VP = {0, 0, 0, 0};
        final int[][] RESOURCES = {{1, 3, 0, 2, 0}, null, null, {0, 0, 2, 2, 1}};
        final int[] PIECES_ALL = {15, 5, 4, 0, 0};
        final int[][] PIECE_COUNTS = {PIECES_ALL, PIECES_ALL, PIECES_ALL, PIECES_ALL};
        TestLoadgame.checkPlayerData(sgm, NAMES, null, TOTAL_VP, RESOURCES, PIECE_COUNTS, null);

        SOCTradeOffer offer = ga.getPlayer(0).getCurrentOffer();
        assertNotNull(offer);
        assertEquals(GAME_NAME, offer.getGame());
        assertEquals(0, offer.getFrom());
        assertArrayEquals(new boolean[]{false,  false, false, true}, offer.getTo());
        assertEquals(PL0_GIVES, offer.getGiveSet());
        assertEquals(PL0_GETS, offer.getGetSet());

        offer = ga.getPlayer(3).getCurrentOffer();
        assertNotNull(offer);
        assertEquals(GAME_NAME, offer.getGame());
        assertEquals(3, offer.getFrom());
        assertArrayEquals(new boolean[]{true, false,  false, false}, offer.getTo());
        assertEquals(PL3_GIVES, offer.getGiveSet());
        assertEquals(PL3_GETS, offer.getGetSet());
    }

    /**
     * Test whether player dev-card stats fields like {@link SOCPlayer#numRBCards} are properly added into SGM;
     * doesn't need to actually save the file.
     * @throws IOException
     * @since 2.5.00
     */
    @Test
    public void testSGM_playerDevCardStats()
        throws IOException
    {
        SOCGame ga = new SOCGame("test");
        ga.addPlayer("p0", 0);
        ga.addPlayer("third", 3);
        ga.startGame();  // create board layout
        ga.setGameState(SOCGame.ROLL_OR_CARD);  // no pieces placed, but can't save during initial placement

        SOCPlayer pl = ga.getPlayer(0);
        pl.updateDevCardsPlayed(SOCDevCardConstants.ROADS, false);
        pl.updateDevCardsPlayed(SOCDevCardConstants.ROADS, false);
        assertEquals(2, pl.numRBCards);
        assertEquals(0, pl.numDISCCards);
        assertEquals(0, pl.numMONOCards);
        assertEquals(Arrays.asList(SOCDevCardConstants.ROADS, SOCDevCardConstants.ROADS), pl.getDevCardsPlayed());

        pl = ga.getPlayer(3);
        pl.updateDevCardsPlayed(SOCDevCardConstants.DISC, false);
        pl.updateDevCardsPlayed(SOCDevCardConstants.MONO, false);
        assertEquals(0, pl.numRBCards);
        assertEquals(1, pl.numDISCCards);
        assertEquals(1, pl.numMONOCards);
        assertEquals(Arrays.asList(SOCDevCardConstants.DISC, SOCDevCardConstants.MONO), pl.getDevCardsPlayed());

        SavedGameModel sgm = new SavedGameModel(ga, srv);
        assertNotNull(sgm);

        SavedGameModel.PlayerInfo pi = sgm.playerSeats[0];
        assertEquals(Integer.valueOf(2), pi.elements.get(PEType.NUM_PLAYED_DEV_CARD_ROADS));
        assertFalse(pi.elements.containsKey(PEType.NUM_PLAYED_DEV_CARD_DISC));
        assertFalse(pi.elements.containsKey(PEType.NUM_PLAYED_DEV_CARD_MONO));
        assertEquals(Arrays.asList(SOCDevCardConstants.ROADS, SOCDevCardConstants.ROADS), pi.playedDevCards);

        pi = sgm.playerSeats[3];
        assertFalse(pi.elements.containsKey(PEType.NUM_PLAYED_DEV_CARD_ROADS));
        assertEquals(Integer.valueOf(1), pi.elements.get(PEType.NUM_PLAYED_DEV_CARD_DISC));
        assertEquals(Integer.valueOf(1), pi.elements.get(PEType.NUM_PLAYED_DEV_CARD_MONO));
        assertEquals(Arrays.asList(SOCDevCardConstants.DISC, SOCDevCardConstants.MONO), pi.playedDevCards);
    }

    /**
     * Save a game, reload it, check various player statistics and misc other fields.
     * @see #testBasicSaveLoad()
     * @since 2.6.00
     */
    @Test
    public void testSaveLoadPlayerMiscFields()
        throws IOException
    {
        final SOCGame gaSave = new SOCGame("stats", null, null);
        gaSave.addPlayer("p0", 0);
        gaSave.addPlayer("third", 3);
        assertFalse(gaSave.isSeatVacant(0));
        assertTrue(gaSave.isSeatVacant(1));
        assertTrue(gaSave.isSeatVacant(2));
        assertFalse(gaSave.isSeatVacant(3));
        gaSave.setSeatLock(0, SeatLockState.LOCKED);
        gaSave.setSeatLock(3, SeatLockState.CLEAR_ON_RESET);

        gaSave.startGame();  // create board layout
        assertEquals(SOCGame.START1A, gaSave.getGameState());
        final int firstPN = gaSave.getCurrentPlayerNumber();
        assertEquals(firstPN, gaSave.getFirstPlayer());

        // no pieces placed, but can't save during initial placement
        gaSave.setGameState(SOCGame.ROLL_OR_CARD);
        gaSave.getPlayer(0).addRolledResources(new SOCResourceSet(1, 3, 0, 2, 0, 0));

        // workaround: give VP dev cards so players' VP != 0, so save/load code doesn't think they're vacant
        gaSave.getPlayer(0).getInventory().addDevCard(1, SOCInventory.OLD, SOCDevCardConstants.UNIV);
        gaSave.getPlayer(3).getInventory().addDevCard(1, SOCInventory.OLD, SOCDevCardConstants.CAP);

        // resource roll stats
        final int[][] RES_ROLLED = new int[][]
            { {0, 3, 3, 5, 2, 4, 0}, null, null, {0, 0, 1, 0, 0, 0, 0} };

        final SOCPlayer pl = gaSave.getPlayer(0);

        // trade res stats
        int[][][] plExpectedStats = new int[SOCPlayer.TRADE_STATS_ARRAY_LEN][2][5],  // [trType][give/get][resType]
            plTradeExpectedStats  = new int[SOCPlayer.TRADE_STATS_ARRAY_LEN][2][5];
        {
            // simplified from TestPlayer.testTradeAndStats()

            final SOCResourceSet ORE_1 = new SOCResourceSet(0, 1, 0, 0, 0, 0);

            // player trade
            {
                final SOCPlayer plTrade = gaSave.getPlayer(3);

                final SOCResourceSet SHEEP_WOOD_1 = new SOCResourceSet(0, 0, 1, 0, 1, 0);
                pl.addRolledResources(SHEEP_WOOD_1);
                plTrade.addRolledResources(ORE_1);
                pl.makeTrade(SHEEP_WOOD_1, ORE_1);
                plTrade.makeTrade(ORE_1, SHEEP_WOOD_1);
                {
                    plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][0] = new int[]{0, 0, 1, 0, 1};  // sheep, wood
                    plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][1] = new int[]{0, 1, 0, 0, 0};  // ore
                    plTradeExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][0] = new int[]{0, 1, 0, 0, 0};
                    plTradeExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][1] = new int[]{0, 0, 1, 0, 1};
                }
                TestPlayer.assertTradeStatsEqual(plExpectedStats, pl);
                TestPlayer.assertTradeStatsEqual(plTradeExpectedStats, plTrade);
                assertArrayEquals(RES_ROLLED[3], plTrade.getResourceRollStats());
            }

            // basic 4:1 bank trade
            final SOCResourceSet SHEEP_4 = new SOCResourceSet(0, 0, 4, 0, 0, 0);
            pl.addRolledResources(SHEEP_4);
            pl.makeBankTrade(SHEEP_4, ORE_1);
            plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_BANK][0][2] += 4;  // SHEEP
            plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_BANK][1][1]++;  // ORE

            // set and use 3:1
            pl.setPortFlag(SOCBoard.MISC_PORT, true);
            final SOCResourceSet WOOD_3 = new SOCResourceSet(0, 0, 0, 0, 3, 0);
            pl.addRolledResources(WOOD_3);
            pl.makeBankTrade(WOOD_3, ORE_1);
            plExpectedStats[SOCBoard.MISC_PORT][0][4] += 3;  // WOOD
            plExpectedStats[SOCBoard.MISC_PORT][1][1]++;  // ORE

            // set and use 2:1
            pl.setPortFlag(SOCBoard.CLAY_PORT, true);
            final SOCResourceSet CLAY_2 = new SOCResourceSet(2, 0, 0, 0, 0, 0);
            pl.addRolledResources(CLAY_2);
            pl.makeBankTrade(CLAY_2, ORE_1);
            plExpectedStats[SOCBoard.CLAY_PORT][0][0] += 2;  // CLAY
            plExpectedStats[SOCBoard.CLAY_PORT][1][1]++;  // ORE

            TestPlayer.assertTradeStatsEqual(plExpectedStats, pl);
            assertArrayEquals(RES_ROLLED[0], pl.getResourceRollStats());
        }

        // misc other fields
        gaSave.setPlacingRobberForKnightCard(true);
        pl.setUndosRemaining(2);

        File saveFile = testTmpFolder.newFile("stats.game.json");
        GameSaverJSON.saveGame(gaSave, testTmpFolder.getRoot(), saveFile.getName(), srv);

        final SavedGameModel sgm = GameLoaderJSON.loadGame(saveFile, srv);
        assertNotNull(sgm);
        assertEquals(SavedGameModel.MODEL_VERSION, sgm.modelVersion);
        assertEquals(Version.versionNumber(), sgm.savedByVersion);
        final SOCGame ga = sgm.getGame();

        assertEquals("stats", ga.getName());
        assertEquals(4, ga.maxPlayers);
        assertEquals(firstPN, ga.getCurrentPlayerNumber());
        assertEquals(firstPN, ga.getFirstPlayer());

        final String[] NAMES = {"p0", null, null, "third"};
        final SeatLockState[] LOCKS =
            {SeatLockState.LOCKED, SeatLockState.UNLOCKED, SeatLockState.UNLOCKED, SeatLockState.CLEAR_ON_RESET};
        final int[] TOTAL_VP = {1, 0, 0, 1};
        final int[][] RESOURCES = {{1, 7, 0, 2, 0}, null, null, {0, 0, 1, 0, 1}};
        final int[] PIECES_ALL = {15, 5, 4, 0, 0};
        final int[][] PIECE_COUNTS = {PIECES_ALL, PIECES_ALL, PIECES_ALL, PIECES_ALL};
        TestLoadgame.checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS, null);
        // check player stats
        final SOCPlayer plLoaded = ga.getPlayer(0), plTradeLoaded = ga.getPlayer(3);
        assertEquals(1, plLoaded.getInventory().getAmount(SOCInventory.OLD, SOCDevCardConstants.UNIV));
        TestPlayer.assertTradeStatsEqual(plExpectedStats, plLoaded);
        TestPlayer.assertTradeStatsEqual(plTradeExpectedStats, plTradeLoaded);
        assertArrayEquals(RES_ROLLED[0], plLoaded.getResourceRollStats());
        assertArrayEquals(RES_ROLLED[3], plTradeLoaded.getResourceRollStats());

        // check misc other fields
        assertTrue(ga.isPlacingRobberForKnightCard());
        assertEquals(2, plLoaded.getUndosRemaining());
    }

    /**
     * Test whether game data is consistent when savegame is loaded, saved, and reloaded.
     * @throws IOException
     * @see {@link #testRoundtripReload_BadFieldContents()}
     */
    @Test
    public void testRoundtripReload_ClassicBotturn()
        throws IOException
    {
        SavedGameModel sgm1 = TestLoadgame.load("classic-botturn.game.json", srv);
        TestLoadgame.checkReloaded_ClassicBotturn(sgm1);  // detailed check of game and player data

        // must resume before can save
        TestLoadgame.fillSeatsForResume(sgm1);
        sgm1.resumePlay(true);

        File saveFile = testTmpFolder.newFile("classic-copy.game.json");
        GameSaverJSON.saveGame(sgm1.getGame(), testTmpFolder.getRoot(), saveFile.getName(), srv);

        final SavedGameModel sgm2 = GameLoaderJSON.loadGame(saveFile, srv);
        TestLoadgame.checkReloaded_ClassicBotturn(sgm2);  // looks for same details again
    }

    /**
     * Test whether game data is consistent when savegame is loaded, saved, and reloaded,
     * preserving unknown-field contents where possible.
     * @throws IOException
     * @see #testRoundtripReload_ClassicBotturn()
     */
    @Test
    public void testRoundtripReload_BadFieldContents()
        throws IOException
    {
        SavedGameModel sgm1 = TestLoadgame.load("bad-field-contents.game.json", srv);
        TestLoadgame.checkReloaded_BadFieldContents(sgm1, false);  // detailed check of game and player data

        TestLoadgame.fillSeatsForResume(sgm1);
        sgm1.resumePlay(true);

        File saveFile = testTmpFolder.newFile("bad-fields-copy.game.json");
        GameSaverJSON.saveGame(sgm1.getGame(), testTmpFolder.getRoot(), saveFile.getName(), srv);

        final SavedGameModel sgm2 = GameLoaderJSON.loadGame(saveFile, srv);
        sgm2.playerSeatLocks[0] = null;  // this unknown field isn't preserved during roundtrip; that's OK
        TestLoadgame.checkReloaded_BadFieldContents(sgm2, false);  // looks for same details again
    }

}

