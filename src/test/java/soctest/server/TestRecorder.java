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

package soctest.server;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCBoardLarge;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCMoveRobberResult;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.message.SOCBuildRequest;
import soc.message.SOCChoosePlayer;
import soc.server.SOCGameListAtServer;
import soc.server.SOCServer;
import soc.server.genericServer.Connection;
import soc.server.savegame.SavedGameModel;
import soc.util.Version;
import soctest.server.RecordingTesterServer.QueueEntry;
import soctest.server.savegame.TestLoadgame;

/**
 * A few tests for {@link SOCServer#recordGameEvent(String, soc.message.SOCMessage)} and similar methods.
 * Covers a few core game actions and message sequences. For more complete coverage of those,
 * you should periodically run {@code extraTest} {@code soctest.server.TestActionsMessages}.
 *<P>
 * Also has convenience methods like {@link #connectLoadJoinResumeGame(RecordingTesterServer, String)}
 * and {@link #compareRecordsToExpected(List, String[][])} which other test classes can use.
 *
 * @since 2.4.10
 */
public class TestRecorder
{
    private static RecordingTesterServer srv;

    @BeforeClass
    public static void startStringportServer()
    {
        srv = new RecordingTesterServer();
        srv.setPriority(5);  // same as in SOCServer.main
        srv.start();

        // wait for startup
        try
        {
            Thread.sleep(500);
        }
        catch (InterruptedException e) {}
    }

    @AfterClass
    public static void shutdownServer()
    {
        srv.stopServer();
    }

    /**
     * Resume a game after loading it. Prints player connection details to {@link System#out},
     * calls {@link SOCServer#resumeReloadedGame(Connection, SOCGame)}.
     * @param ga  Loaded game to resume
     * @param server  Server to resume in
     * @param cliConn  Client connection; should already be a member of this game at server
     */
    public static void resumeLoadedGame(final SOCGame ga, final SOCServer server, final Connection cliConn)
    {
        if (server == null)
            throw new IllegalArgumentException("server");
        if (cliConn == null)
            throw new IllegalArgumentException("cliConn");

        final String gaName = ga.getName();
        final SOCGameListAtServer glas = server.getGameList();

        // output all at once, in case of parallel tests
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- Resuming loaded game: " + gaName + "\n");
        for (int pn = 0; pn < 4; ++pn)
        {
            if (ga.isSeatVacant(pn))
                continue;
            SOCPlayer pl = ga.getPlayer(pn);
            String plName = pl.getName();
            if ((plName == null) || plName.isEmpty())
            {
                sb.append("pn[" + pn + "] ** empty\n");
                continue;
            }
            Connection plConn = server.getConnection(plName);
            sb.append("pn[" + pn + "] name=" + pl.getName() + ", isRobot=" + pl.isRobot()
                + ", hasConn=" + (null != plConn) + ", isMember=" + glas.isMember(plConn, gaName) + "\n");
        }
        System.out.flush();
        System.out.println(sb);
        System.out.flush();
        assertTrue("resume loaded game", server.resumeReloadedGame(cliConn, ga));
    }

    /**
     * Test the basics, to rule out problems with that if other tests fail:
     *<UL>
     * <LI> {@link RecordingTesterServer} is up
     * <LI> {@link SOCServer#recordGameEventsIsActive()} is true
     * <LI> Bots are connected to test server
     * <LI> {@link DisplaylessTesterClient} can connect and see server's version
     * <LI> Server sees test client connection
     *</UL>
     */
    @Test
    public void testBasics_ServerUpWithBotsConnectClient()
    {
        // unique client nickname, in case tests run in parallel
        final String CLIENT_NAME = "testServerUp";

        assertNotNull(srv);
        assertEquals(RecordingTesterServer.STRINGPORT_NAME, srv.getLocalSocketName());

        assertTrue("recordGameEvents shouldn't be stubbed out", srv.recordGameEventsIsActive());

        final int nConn = srv.getNamedConnectionCount();
        assertTrue
            ("some bots are connected; actual nConn=" + nConn, nConn >= RecordingTesterServer.NUM_STARTROBOTS);

        DisplaylessTesterClient tcli = new DisplaylessTesterClient
            (RecordingTesterServer.STRINGPORT_NAME, CLIENT_NAME);
        tcli.init();
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertEquals("get version from test SOCServer", Version.versionNumber(), tcli.getServerVersion());
        Connection tcliAtServer = srv.getConnection(CLIENT_NAME);
        assertNotNull(tcliAtServer);

        tcli.destroy();
    }

    /**
     * Test the basics, to rule out problems with that if other tests fail:
     * Load a game, server should invite test client to join it because of player name.
     */
    @Test
    public void testBasics_Loadgame()
        throws IOException
    {
        assertNotNull(srv);
        testBasics_Loadgame(srv);
    }

    /**
     * Test the basics, to rule out problems with that if other tests fail:
     * Load a game, server should invite test client to join it because of player name.
     * Parameterized for use from other test/extraTest classes.
     *
     * @param server  Server to use
     * @throws IOException if problem occurs during {@link TestLoadgame#load(String, SOCServer)}
     */
    public static void testBasics_Loadgame(SOCServer server)
        throws IOException
    {
        if (server == null)
            throw new IllegalArgumentException("server");

        // unique client nickname, in case tests run in parallel
        final String CLIENT_NAME = "testLoadgame";

        assertNotNull(server);

        DisplaylessTesterClient tcli = new DisplaylessTesterClient
            (RecordingTesterServer.STRINGPORT_NAME, CLIENT_NAME);
        tcli.init();
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertEquals("get version from test SOCServer", Version.versionNumber(), tcli.getServerVersion());

        SavedGameModel sgm = TestLoadgame.load("classic-botturn.game.json", server);
        assertNotNull(sgm);
        assertEquals("classic", sgm.gameName);
        assertEquals("debug", sgm.playerSeats[3].name);
        sgm.playerSeats[3].name = CLIENT_NAME;

        Connection tcliConn = server.getConnection(CLIENT_NAME);
        assertNotNull(tcliConn);
        String loadedName = server.createAndJoinReloadedGame(sgm, tcliConn, null);
        assertNotNull("reloaded game name", loadedName);

        // wait to join in client's thread
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertTrue("debug cli member of reloaded game?", server.getGameList().isMember(tcliConn, loadedName));

        final SOCGame ga = server.getGame(loadedName);
        assertNotNull("game object at server", ga);
        final int PN = 3;
        assertEquals(1, ga.getCurrentPlayerNumber());
        final SOCPlayer cliPl = ga.getPlayer(PN);
        assertEquals(CLIENT_NAME, cliPl.getName());

        // leave game
        tcli.destroy();
    }

    /**
     * Common code to use when beginning a test:
     *<UL>
     * <LI> Assert {@code server} not null
     * <LI> Connect to test server with a new client
     * <LI> Load game artifact {@code "message-seqs.game.json"}
     * <LI> Confirm and retrieve {@link SOCGame} and client {@link SOCPlayer} info
     * <LI> Resume the game; will be client player's turn (player number 3) and game state {@link SOCGame#PLAY1 PLAY1}
     *</UL>
     * @param clientName  Unique client name to use for this client and game
     * @return  all the useful objects mentioned above
     * @throws IllegalArgumentException if {@code clientName} is null
     * @throws IOException if game artifact file can't be loaded
     */
    public static StartedTestGameObjects connectLoadJoinResumeGame
        (final RecordingTesterServer server, final String clientName)
        throws IllegalArgumentException, IOException
    {
        if (clientName == null)
            throw new IllegalArgumentException("clientName");

        assertNotNull(server);

        DisplaylessTesterClient tcli = new DisplaylessTesterClient
            (RecordingTesterServer.STRINGPORT_NAME, clientName);
        tcli.init();
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertEquals("get version from test SOCServer", Version.versionNumber(), tcli.getServerVersion());

        SavedGameModel sgm = soctest.server.savegame.TestLoadgame.load("message-seqs.game.json", server);
        assertNotNull(sgm);
        assertEquals("message-seqs", sgm.gameName);
        assertEquals("debug", sgm.playerSeats[3].name);
        sgm.playerSeats[3].name = clientName;

        Connection tcliConn = server.getConnection(clientName);
        assertNotNull(tcliConn);
        String loadedName = server.createAndJoinReloadedGame(sgm, tcliConn, null);
        assertNotNull("message-seqs game name", loadedName);

        // wait to join in client's thread
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertTrue("debug cli member of reloaded game?", server.getGameList().isMember(tcliConn, loadedName));

        final SOCGame ga = server.getGame(loadedName);
        assertNotNull("game object at server", ga);
        assertTrue("game uses sea board", ga.getBoard() instanceof SOCBoardLarge);

        final int PN = 3;
        assertEquals(PN, ga.getCurrentPlayerNumber());
        final SOCPlayer cliPl = ga.getPlayer(PN);
        assertEquals(clientName, cliPl.getName());
        assertEquals(SOCGame.PLAY1, sgm.gameState);

        resumeLoadedGame(ga, server, tcliConn);
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertEquals(SOCGame.PLAY1, ga.getGameState());

        final Vector<QueueEntry> records = server.records.get(loadedName);
        assertNotNull("record queue for game", records);

        return new StartedTestGameObjects(tcli, sgm, ga, (SOCBoardLarge) ga.getBoard(), cliPl, records);
    }

    /**
     * Test loading {@code message-seqs.game.json} and recording a few basic game action sequences,
     * which also test the different {@link SOCServer#recordGameEvent(String, soc.message.SOCMessage)} methods:
     *<UL>
     * <LI> Build a road: Sent to all players
     * <LI> Buy a dev card: Some messages sent to 1 player, or all but 1
     * <LI> Choose and move robber, steal: Some messages sent to all but 2 players
     *</UL>
     */
    @Test
    public void testLoadAndBasicSequences()
        throws IOException
    {
        // unique client nickname, in case tests run in parallel
        final String CLIENT_NAME = "testBasicSequences";

        final StartedTestGameObjects objs = connectLoadJoinResumeGame(srv, CLIENT_NAME);
        final DisplaylessTesterClient tcli = objs.tcli;
        // final SavedGameModel sgm = objs.sgm;
        final SOCGame ga = objs.gameAtServer;
        final SOCBoardLarge board = objs.board;
        final SOCPlayer cliPl = objs.clientPlayer;
        final Vector<QueueEntry> records = objs.records;

        /* sequence recording: build road */

        records.clear();
        final int ROAD_COORD = 0x40a;
        assertNull(board.roadOrShipAtEdge(ROAD_COORD));
        assertEquals(12, cliPl.getNumPieces(SOCPlayingPiece.ROAD));
        assertArrayEquals(new int[]{3, 3, 3, 4, 4}, cliPl.getResources().getAmounts(false));
        tcli.putPiece(ga, new SOCRoad(cliPl, ROAD_COORD, board));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertNotNull("road built", board.roadOrShipAtEdge(ROAD_COORD));
        assertEquals(11, cliPl.getNumPieces(SOCPlayingPiece.ROAD));
        assertArrayEquals(new int[]{2, 3, 3, 4, 3}, cliPl.getResources().getAmounts(false));

        // for now, quick rough comparison of record contents
        StringBuilder comparesBuild = compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElements:", "|playerNum=3|actionType=LOSE|e1=1,e5=1"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " built a road."},
                {"all:SOCPutPiece:", "|playerNumber=3|pieceType=0|coord=40a"},
                {"all:SOCGameState:", "|state=20"}
            });

        /* sequence recording: buy dev card */

        records.clear();
        assertEquals(23, ga.getNumDevCards());
        assertEquals(5, cliPl.getInventory().getTotal());
        tcli.buyDevCard(ga);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(22, ga.getNumDevCards());
        assertEquals(6, cliPl.getInventory().getTotal());

        StringBuilder comparesBuyCard = compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElements:", "|playerNum=3|actionType=LOSE|e2=1,e3=1,e4=1"},
                {"all:SOCGameElements:", "|e2=22"},
                {"pn=3:SOCDevCardAction:", "|playerNum=3|actionType=DRAW|cardType=5"},  // type known from savegame devCardDeck
                {"pn=!3:SOCDevCardAction:", "|playerNum=3|actionType=DRAW|cardType=0"},
                {"all:SOCSimpleAction:", "|pn=3|actType=1|v1=22|v2=0"},
                {"all:SOCGameState:", "|state=20"}
            });

        /* sequence recording: choose and move robber, steal from 1 player */

        records.clear();
        assertEquals("old robberHex", 2314, board.getRobberHex());  // 0x90a
        assertFalse(cliPl.hasPlayedDevCard());
        assertEquals(1, cliPl.getNumKnights());
        tcli.playDevCard(ga, SOCDevCardConstants.KNIGHT);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertTrue(cliPl.hasPlayedDevCard());
        assertEquals(2, cliPl.getNumKnights());
        assertEquals(SOCGame.WAITING_FOR_ROBBER_OR_PIRATE, ga.getGameState());
        tcli.choosePlayer(ga, SOCChoosePlayer.CHOICE_MOVE_ROBBER);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.PLACING_ROBBER, ga.getGameState());
        final int ROBBER_HEX = 773;  // 0x305
        tcli.moveRobber(ga, cliPl, ROBBER_HEX);

        try { Thread.sleep(70); }
        catch(InterruptedException e) {}
        assertEquals("new robberHex", ROBBER_HEX, board.getRobberHex());
        SOCMoveRobberResult robRes = ga.getRobberyResult();
        assertNotNull(robRes);
        final int resType = robRes.getLoot();
        assertTrue(resType > 0);

        StringBuilder comparesMoveRobber = compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " played a Soldier card."},
                {"all:SOCDevCardAction:", "|playerNum=3|actionType=PLAY|cardType=9"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=SET|elementType=19|amount=1"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=15|amount=1"},
                {"all:SOCGameState:", "|state=54"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " must choose to move the robber or the pirate."},
                {"all:SOCGameState:", "|state=33"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " will move the robber."},
                {"all:SOCMoveRobber:", "|playerNumber=3|coord=305"},
                {"pn=3:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=" + resType + "|amount=1"},
                {"pn=3:SOCPlayerElement:", "|playerNum=1|actionType=LOSE|elementType=" + resType + "|amount=1|news=Y"},
                {"pn=1:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=" + resType + "|amount=1"},
                {"pn=1:SOCPlayerElement:", "|playerNum=1|actionType=LOSE|elementType=" + resType + "|amount=1|news=Y"},
                {"pn=![3, 1]:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=6|amount=1"},
                {"pn=![3, 1]:SOCPlayerElement:", "|playerNum=1|actionType=LOSE|elementType=6|amount=1"},
                {"pn=3:SOCGameServerText:", "|text=You stole a", " from "},  // "an ore", "a sheep", etc
                {"pn=1:SOCGameServerText:", "|text=" + CLIENT_NAME + " stole a", " from you."},
                {"pn=![3, 1]:SOCGameServerText:", "|text=" + CLIENT_NAME + " stole a resource from "},
                {"all:SOCGameState:", "|state=20"}
            });

        /* leave game, consolidate results */

        tcli.destroy();

        StringBuilder compares = new StringBuilder();
        if (comparesBuild != null)
        {
            compares.append("Build road: Records mismatch: ");
            compares.append(comparesBuild);
        }
        if (comparesBuyCard != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Buy dev card: Records mismatch: ");
            compares.append(comparesBuyCard);
        }
        if (comparesMoveRobber != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Move robber: Records mismatch: ");
            compares.append(comparesMoveRobber);
        }

        if (compares.length() > 0)
        {
            System.err.println(compares);
            fail(compares.toString());
        }

    }

    /**
     * Compare game event records against expected sequence.
     * @param records  Game records from server
     * @param expected  Expected: Per-record lists of prefix, any other contained strings
     *     to ignore game name and variable fields
     * @return {@code null} if no differences, or the differences found
     */
    public static StringBuilder compareRecordsToExpected
        (final List<QueueEntry> records, final String[][] expected)
    {
        StringBuilder compares = new StringBuilder();

        int n = records.size();
        if (expected.length < n)
            n = expected.length;

        if (records.size() != expected.length)
            compares.append("Length mismatch: Expected " + expected.length + ", got " + records.size());

        StringBuilder comp = new StringBuilder();
        for (int i = 0; i < n; ++i)
        {
            comp.setLength(0);
            final String recStr = records.get(i).toString();
            final String[] exps = expected[i];
            if (! recStr.startsWith(exps[0]))
                comp.append("expected start " + exps[0] + ", saw " + recStr.substring(0, exps[0].length()));

            boolean failContains = false;
            for (int j = 1; j < exps.length; ++j)
            {
                if (! recStr.contains(exps[j]))
                {
                    comp.append(" expected " + exps[j]);
                    failContains = true;
                }
            }
            if (failContains)
                comp.append(", saw message " + recStr);

            if (comp.length() > 0)
                compares.append(" [" + i + "]: " + comp);
        }

        if (compares.length() == 0)
            return null;
        else
            return compares;
    }

    /** Comprehensive tests for {@link RecordingTesterServer.QueueEntry}: Constructors, toString */
    @Test
    public void testQueueEntry()
    {
        final SOCBuildRequest event = new SOCBuildRequest("testgame", 2);

        QueueEntry qe = new QueueEntry(event, -1);
        assertEquals(-1, qe.toPN);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("all:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new QueueEntry(event, new int[]{3});
        assertEquals(-1, qe.toPN);
        assertEquals(event, qe.event);
        assertArrayEquals(new int[]{3}, qe.excludedPN);
        assertEquals("pn=!3:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new QueueEntry(event, new int[]{2,3,4});
        assertEquals(-1, qe.toPN);
        assertEquals(event, qe.event);
        assertArrayEquals(new int[]{2,3,4}, qe.excludedPN);
        assertEquals("pn=![2, 3, 4]:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new QueueEntry(null, -1);
        assertEquals(-1, qe.toPN);
        assertNull(qe.event);
        assertNull(qe.excludedPN);
        assertEquals("all:null", qe.toString());

        qe = new QueueEntry(null, 3);
        assertEquals(3, qe.toPN);
        assertNull(qe.event);
        assertNull(qe.excludedPN);
        assertEquals("pn=3:null", qe.toString());
    }

    /**
     * Data class for useful objects returned from
     * {@link #connectLoadJoinResumeGame(RecordingTesterServer, String)}.
     */
    public static final class StartedTestGameObjects
    {
        public final DisplaylessTesterClient tcli;
        public final SavedGameModel sgm;
        public final SOCGame gameAtServer;
        public final SOCBoardLarge board;
        public final SOCPlayer clientPlayer;
        public final Vector<QueueEntry> records;

        public StartedTestGameObjects
            (DisplaylessTesterClient tcli, SavedGameModel sgm, SOCGame gameAtServer,
             SOCBoardLarge board, SOCPlayer clientPlayer, Vector<QueueEntry> records)
        {
            this.tcli = tcli;
            this.sgm = sgm;
            this.gameAtServer = gameAtServer;
            this.board = board;
            this.clientPlayer = clientPlayer;
            this.records = records;
        }
    }

}
