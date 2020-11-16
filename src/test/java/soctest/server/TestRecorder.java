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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Vector;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCMoveRobberResult;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;
import soc.message.SOCBuildRequest;
import soc.message.SOCChoosePlayer;
import soc.message.SOCGameServerText;
import soc.server.SOCClientData;
import soc.server.SOCGameHandler;
import soc.server.SOCGameListAtServer;
import soc.server.SOCServer;
import soc.server.genericServer.Connection;
import soc.server.savegame.SavedGameModel;
import soc.util.SOCStringManager;
import soc.util.Version;
import soctest.server.RecordingTesterServer.QueueEntry;
import soctest.server.savegame.TestLoadgame;

/**
 * A few tests for {@link SOCServer#recordGameEvent(String, soc.message.SOCMessage)} and similar methods,
 * using {@link RecordingTesterServer} and its {@link QueueEntry} format.
 * Covers a few core game actions and message sequences. For more complete coverage of those,
 * you should periodically run {@code extraTest} {@code soctest.server.TestActionsMessages}.
 *<P>
 * Also has convenience methods like
 * {@link #connectLoadJoinResumeGame(RecordingTesterServer, String, String, int, SavedGameModel, boolean, int, boolean, boolean)}
 * and {@link #compareRecordsToExpected(List, String[][])} which other test classes can use.
 *
 * @since 2.4.50
 */
public class TestRecorder
{
    private static RecordingTesterServer srv;

    /**
     * Client name tracking, to prevent accidentally using same name in 2 test methods:
     * That would intermittently cause auth problems for a previously-stable test
     * if one logged out before/while the other was testing.
     */
    private static Set<String> clientNamesUsed = new HashSet<>();

    /** Localized text for {@link #compareRecordsToExpected(List, String[][])} to ignore */
    private static final String GAME_RENAMED_LOCAL_TEXT
        = SOCStringManager.getFallbackServerManagerForClient().get("admin.loadgame.ok.game_renamed", "x");
            // "Game was renamed: Original name {0} is already used."

    /** Localized text for {@link #compareRecordsToExpected(List, String[][])} to ignore */
    private static final String GAME_RENAMED_TEXT_PREFIX
        = GAME_RENAMED_LOCAL_TEXT.substring(0, 17);

    @BeforeClass
    public static void startStringportServer()
    {
        assertEquals("internal test comparison", "Game was renamed:", GAME_RENAMED_TEXT_PREFIX);

        SOCGameHandler.DESTROY_BOT_ONLY_GAMES_WHEN_OVER = false;  // keep games around, to check asserts

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
        // for clearer sequences in System.out, wait for other threads' prints to complete
        try
        {
            Thread.sleep(250);
        }
        catch (InterruptedException e) {}

        System.out.flush();
        System.out.println();
        srv.stopServer();
    }

    /**
     * Resume a game after loading it. Prints player connection details to {@link System#out},
     * calls {@link SOCServer#resumeReloadedGame(Connection, SOCGame)}.
     *<P>
     * For unit tests, should be called only all players have clients connected and sitting down.
     * For players flagged with {@link SavedGameModel.PlayerInfo#isRobot},
     * bots were already automatically connected and sat if possible by
     * {@link SOCServer#createAndJoinReloadedGame(SavedGameModel, Connection, String)}.
     *
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

        assertNotNull("cliConn is authed and named: game " + gaName, cliConn.getData());

        // output all at once, in case of parallel tests
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- Resuming loaded game: " + gaName + "\n");
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            if (ga.isSeatVacant(pn))
                continue;

            SOCPlayer pl = ga.getPlayer(pn);
            String plName = pl.getName();
            if ((plName == null) || plName.isEmpty())
            {
                sb.append("** pn[" + pn + "] empty\n");
                continue;
            }
            Connection plConn = server.getConnection(plName);
            sb.append("pn[" + pn + "] name=" + plName + ", isRobot=" + pl.isRobot()
                + ", hasConn=" + (null != plConn) + ", isMember=" + glas.isMember(plConn, gaName) + "\n");

            if (plConn != null)
                assertEquals("pn[" + pn + "] connection name: game " + gaName, plName, plConn.getData());
            else
                sb.append("** pn[" + pn + "] no connection, should have already joined: " + plName + "\n");
        }
        System.out.flush();
        System.out.println(sb);
        System.out.flush();

        assertNull
            ("resume loaded game; if trouble, search output for \"--- Resuming loaded game\"",
             server.resumeReloadedGame(cliConn, ga));
                // note: SOCServer.RESUME_RELOADED_FETCHING_ROBOTS counts as "trouble" here because
                // unit test should have connected all "human" players already before calling this method
                // to prevent timing/gamestate-assert problems
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
            (RecordingTesterServer.STRINGPORT_NAME, CLIENT_NAME, null, null);
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

        // similar code is in testBasics_SendToClientWithLocale;
        // if you change one, consider changing the other

        // unique client nickname, in case tests run in parallel
        final String CLIENT_NAME = "testLoadgame";

        assertNotNull(server);

        DisplaylessTesterClient tcli = new DisplaylessTesterClient
            (RecordingTesterServer.STRINGPORT_NAME, CLIENT_NAME, null, null);
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
        server.destroyGameAndBroadcast(ga.getName(), null);
        tcli.destroy();
    }

    /**
     * Load a game, join a client with locale {@code "es"}, test the server's recording of messages sent to that client.
     * For consistency, server should always record game logs as if client locale is {@code "en_US"}.
     *
     * @throws IOException if problem occurs during {@link TestLoadgame#load(String, SOCServer)}
     */
    @Test
    public void testBasics_SendToClientWithLocale()
        throws IOException
    {
        // code is based on testBasics_Loadgame;
        // if you change one, consider changing the other

        // unique client nickname, in case tests run in parallel
        final String CLIENT_NAME = "testCliLocale";

        assertNotNull(srv);

        DisplaylessTesterClient tcli = new DisplaylessTesterClient
            (RecordingTesterServer.STRINGPORT_NAME, CLIENT_NAME, "es", null);
        tcli.init();
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertEquals("get version from test SOCServer", Version.versionNumber(), tcli.getServerVersion());

        SavedGameModel sgm = TestLoadgame.load("classic-botturn.game.json", srv);
        assertNotNull(sgm);
        assertEquals("classic", sgm.gameName);
        assertEquals("debug", sgm.playerSeats[3].name);
        sgm.playerSeats[3].name = CLIENT_NAME;

        Connection tcliConn = srv.getConnection(CLIENT_NAME);
        assertNotNull(tcliConn);
        String loadedName = srv.createAndJoinReloadedGame(sgm, tcliConn, null);
        assertNotNull("reloaded game name", loadedName);

        // wait to join in client's thread
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertTrue("debug cli member of reloaded game?", srv.getGameList().isMember(tcliConn, loadedName));

        final SOCGame ga = srv.getGame(loadedName);
        assertNotNull("game object at server", ga);
        final int PN = 3;
        assertEquals(1, ga.getCurrentPlayerNumber());
        final SOCPlayer cliPl = ga.getPlayer(PN);
        assertEquals(CLIENT_NAME, cliPl.getName());

        SOCClientData scd = srv.getClientData(CLIENT_NAME);
        assertNotNull(scd);
        assertEquals("es", scd.localeStr);

        final Vector<QueueEntry> records = srv.records.get(loadedName);
        assertNotNull("record queue for game", records);

        // directly call messageToPlayerKeyed methods being tested
        records.clear();
        srv.messageToPlayerKeyed
            (tcliConn, loadedName, PN, "base.reply.not.your.turn");
        srv.messageToPlayerKeyed
            (tcliConn, loadedName, SOCServer.PN_NON_EVENT, "reply.addtime.practice.never");
        srv.messageToPlayerKeyed
            (tcliConn, loadedName, PN, "action.built.stlmt", "xyz");
        srv.messageToPlayerKeyedSpecial
            (tcliConn, ga, PN, "robber.common.you.stole.resource.from", -1, SOCResourceConstants.SHEEP, "xyz");

        // compare results to fallback en_US text
        final SOCStringManager strings = SOCStringManager.getFallbackServerManagerForClient();
        StringBuilder compares = compareRecordsToExpected
            (records, new String[][]
                {
                    {"p3:SOCGameServerText:", "text="
                        + strings.get("base.reply.not.your.turn")},
                    // no record here for PN_NON_EVENT call
                    {"p3:SOCGameServerText:", "text="
                        + strings.get("action.built.stlmt", "xyz")},
                    {"p3:SOCGameServerText:", "text="
                        + strings.getSpecial
                            (ga, "robber.common.you.stole.resource.from", -1, SOCResourceConstants.SHEEP, "xyz")}
                });

        // TODO test client: Add flag field to record messages,
        //      make sure that actual client receives text localized to es not en_US
        //      For now, can verify by searching test's System.out for SOCGameServerText

        // leave game
        srv.destroyGameAndBroadcast(ga.getName(), null);
        tcli.destroy();

        if (compares != null)
        {
            compares.insert(0, "testBasics_SendToClientWithLocale: Message mismatch: ");
            System.err.println(compares);
            fail(compares.toString());
        }
    }

    /**
     * Common code to use when beginning a test:
     *<UL>
     * <LI> Assert {@code server} not null
     * <LI> Connect to test server with a new client
     * <LI> Optionally connect with another new client
     * <LI> Load 4-player savegame artifact {@code "message-seqs.game.json"} or a different one
     * <LI> server will connect client and robots to it
     * <LI> Confirm and retrieve {@link SOCGame} and client {@link SOCPlayer} info
     * <LI> Override all players' {@link SOCPlayer#isRobot()} flags to test varied server response message sequences
     * <LI> Optionally resume the game:
     *      If using default artifact, can call {@link #resumeLoadedGame(SOCGame, SOCServer, Connection)}
     * <LI> Client player number will be 3 or 5: {@link SOCGame#maxPlayers} - 1
     * <LI> If using default artifact {@code "message-seqs.game.json"}:
     *   <UL>
     *     <LI> {@link StartedTestGameObjects#board} is a {@link SOCBoardLarge}
     *     <LI> will be client player's turn
     *     <LI> game state will be {@link SOCGame#PLAY1 PLAY1}
     *   </UL>
     *</UL>
     * When {@code clientAsRobot}, the client's locale and i18n manager are cleared to null as a bot's would be.
     *<P>
     * Limitation: Even when ! {@code othersAsRobot}, those fields can't be set non-null for robot clients because
     * those bot connections are also in other games which may have a different value for {@code othersAsRobot}.
     *
     * @param clientName  Unique client name to use for this client and game; will sit at player number 3
     * @param client2Name  Optional unique second client name to use, or null
     * @param client2PN  Player number if using {@code client2Name}: A robot player number to take over.
     *     If using default artifact, either 1 or 2 (0 is vacant, 3 is first client).
     *     If {@code client2Name} is null, should be 0.
     * @param gameArtifactSGM  Null to load default artifact {@code "message-seqs.game.json"}
     *     with {@link soctest.server.savegame.TestLoadgame#load(String, SOCServer)},
     *     or a different savegame artifact already loaded and changed if needed by the caller.
     *     The highest player number (3 or 5) must be named {@code "debug"};
     *     that player number will be used for {@code clientName}.
     * @param withResume  If true and {@code gameArtifactSGM} is null,
     *     call {@link #resumeLoadedGame(SOCGame, SOCServer, Connection)}
     * @param observabilityMode Whether to test using normally-inactive game options for "observability":
     *     <UL>
     *      <LI> 0: Normal mode: Resources and Victory Point/development cards are hidden as usual
     *      <LI> 1: Activate and test with VP Observable mode: {@link SOCGameOptionSet#K_PLAY_VPO PLAY_VPO}
     *      <LI> 2: Activate and test with Fully Observable mode: {@link SOCGameOptionSet#K_PLAY_FO PLAY_FO}
     *     </UL>
     * @param clientAsRobot  Whether to mark client player as robot before resuming game:
     *     Calls {@link SOCPlayer#setRobotFlag(boolean, boolean)}
     *     and {@link Connection#setI18NStringManager(soc.util.SOCStringManager, String)}
     * @param othersAsRobot  Whether to mark other players as robot before resuming game:
     *     Calls {@link SOCPlayer#setRobotFlag(boolean, boolean)}
     * @return  all the useful objects mentioned above
     * @throws IllegalArgumentException if {@code clientName} is null or too long
     *     (max length is {@link SOCServer#PLAYER_NAME_MAX_LENGTH});
     *     or if using {@code client2Name} but {@code client2PN} isn't a non-vacant robot player number;
     *     or if {@code withResume} but {@code gameArtifactSGM != null};
     *     or if {@code observabilityMode} not in range 0..2
     * @throws IllegalStateException if {@code clientName} or {@code client2Name} was already used for
     *     a different call to this method: Use unique names for each call to avoid intermittent auth problems
     * @throws IOException if game artifact file can't be loaded
     */
    public static StartedTestGameObjects connectLoadJoinResumeGame
        (final RecordingTesterServer server, final String clientName, final String client2Name, final int client2PN,
         final SavedGameModel gameArtifactSGM, final boolean withResume,
         final int observabilityMode, final boolean clientAsRobot, final boolean othersAsRobot)
        throws IllegalArgumentException, IllegalStateException, IOException
    {
        if (withResume && (gameArtifactSGM != null))
            throw new IllegalArgumentException("withResume is only for default artifact, but gameArtifactSGM != null");
        if (clientName == null)
            throw new IllegalArgumentException("clientName");
        if (clientName.length() > SOCServer.PLAYER_NAME_MAX_LENGTH)
            throw new IllegalArgumentException("clientName.length " + clientName.length()
                + ", max is " + SOCServer.PLAYER_NAME_MAX_LENGTH);
        if (client2Name == null)
        {
            if (client2PN != 0)
                throw new IllegalArgumentException("client2PN should be 0 when client2Name null");
        } else {
            if (client2Name.equals(clientName))
                throw new IllegalArgumentException("clientName == client2Name: " + clientName);

            // client2PN will be checked soon, once sgm seats' robot and vacant flags are known
        }
        if ((observabilityMode < 0) || (observabilityMode > 2))
            throw new IllegalArgumentException("observabilityMode: " + observabilityMode);

        synchronized(clientNamesUsed)
        {
            if (clientNamesUsed.contains(clientName))
                throw new IllegalStateException("already used clientName " + clientName);

            clientNamesUsed.add(clientName);

            if (client2Name != null)
            {
                if (clientNamesUsed.contains(client2Name))
                    throw new IllegalStateException("already used client2Name " + client2Name);

                clientNamesUsed.add(client2Name);
            }
        }

        assertNotNull(server);

        SOCGameOptionSet clientKnownOpts = null;
        final SOCGameOption observabilityOpt;
        if (observabilityMode > 0)
        {
            final String key = (observabilityMode == 1) ? SOCGameOptionSet.K_PLAY_VPO : SOCGameOptionSet.K_PLAY_FO;
            SOCGameOption opt = server.knownOpts.getKnownOption(key, true);
            assertNotNull("server found option " + key, opt);
            if (opt.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN))
            {
                assertTrue
                    ("server.activateKnownOption(" + key + ") success",
                     server.activateKnownOption(key));
                opt = server.knownOpts.getKnownOption(key, true);
                assertNotNull("server found option " + key, opt);
                assertTrue(opt.hasFlag(SOCGameOption.FLAG_ACTIVATED));
            }

            clientKnownOpts = SOCGameOptionSet.getAllKnownOptions();
            clientKnownOpts.activate(key);

            assertTrue("option activated at server: " + key, opt.hasFlag(SOCGameOption.FLAG_ACTIVATED));
            assertTrue
                ("option activated at client: " + key, clientKnownOpts.get(key).hasFlag(SOCGameOption.FLAG_ACTIVATED));
            observabilityOpt = opt;
        } else {
            observabilityOpt = null;
        }

        final DisplaylessTesterClient tcli = new DisplaylessTesterClient
            (RecordingTesterServer.STRINGPORT_NAME, clientName, null, clientKnownOpts);
        tcli.init();
        assertEquals(clientName, tcli.getNickname());

        try { Thread.sleep(120); }
        catch(InterruptedException e) {}
        assertEquals("get version from test SOCServer", Version.versionNumber(), tcli.getServerVersion());

        final DisplaylessTesterClient tcli2;
        if (client2Name != null)
        {
            tcli2 = new DisplaylessTesterClient
                (RecordingTesterServer.STRINGPORT_NAME, client2Name, null, clientKnownOpts);
            tcli2.init();
            assertEquals(client2Name, tcli2.getNickname());

            try { Thread.sleep(120); }
            catch(InterruptedException e) {}
            assertEquals("get version from test SOCServer", Version.versionNumber(), tcli2.getServerVersion());
        } else {
            tcli2 = null;
        }

        final SavedGameModel sgm = (gameArtifactSGM != null)
            ? gameArtifactSGM
            : soctest.server.savegame.TestLoadgame.load("message-seqs.game.json", server);
        assertNotNull(sgm);
        if (gameArtifactSGM == null)
        {
            assertEquals("message-seqs", sgm.gameName);
            assertEquals(4, sgm.playerSeats.length);
        }
        final int clientPN = sgm.playerSeats.length - 1;
        assertEquals("debug", sgm.playerSeats[clientPN].name);
        sgm.playerSeats[clientPN].name = clientName;

        final Connection tcliConn = server.getConnection(clientName);
        assertNotNull("server has tcliConn(" + clientName + ")", tcliConn);
        assertEquals("conn.getData==" + clientName, clientName, tcliConn.getData());

        final Connection tcli2Conn;
        if (client2Name != null)
        {
            SavedGameModel.PlayerInfo client2PI = sgm.playerSeats[client2PN];
            if (client2PI.isSeatVacant || ! client2PI.isRobot)
                throw new IllegalArgumentException("seat number " + client2PN + " must be non-vacant robot");

            tcli2Conn = server.getConnection(client2Name);
            assertNotNull("server has tcli2Conn(" + client2Name + ")", tcli2Conn);
            assertEquals("conn2.getData==" + client2Name, client2Name, tcli2Conn.getData());
        } else {
            tcli2Conn = null;
        }

        if (observabilityMode > 0)
        {
            assertNotNull("SGM has gameopts: " + sgm.gameName, sgm.gameOptions);
            SOCGame sgmGame = sgm.getGame();
            SOCGameOptionSet opts = sgmGame.getGameOptions();
            assertNotNull("SGM.getGame has gameopts: " + sgmGame.getName(), opts);
            final String optKey = observabilityOpt.key;
            assertFalse
                ("game shouldn't already have observ gameopt " + optKey,
                 opts.containsKey(optKey));

            observabilityOpt.setBoolValue(true);
            opts.put(observabilityOpt);
            sgm.gameOptions += ';' + observabilityOpt.toString();
        }

        String loadedName = server.createAndJoinReloadedGame(sgm, tcliConn, null);
        assertNotNull("message-seqs game name", loadedName);

        // wait to join in client's thread
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertTrue("debug cli member of reloaded game?", server.getGameList().isMember(tcliConn, loadedName));

        final SOCGame ga = server.getGame(loadedName);
        assertNotNull("game object at server", ga);
        if (gameArtifactSGM == null)
        {
            assertTrue("game uses sea board", ga.getBoard() instanceof SOCBoardLarge);
            assertEquals(SOCGame.PLAY1, sgm.gameState);
        }

        if (clientAsRobot && othersAsRobot)
            ga.isBotsOnly = true;

        if (tcli2 != null)
        {
            tcli2.askJoinGame(loadedName);

            // wait to join in client's thread
            try { Thread.sleep(150); }
            catch(InterruptedException e) {}

            assertTrue("cli2 member of reloaded game?", server.getGameList().isMember(tcli2Conn, loadedName));

            tcli2.sitDown(ga, client2PN);

            try { Thread.sleep(90); }
            catch(InterruptedException e) {}

            // checks results soon: ga.getPlayer(client2PN) below
        }


        if (gameArtifactSGM == null)
        {
            // has N7: roll no 7s, to simplify test assumptions
            SOCGameOption opt = ga.getGameOptions().get("N7");
            assertNotNull(opt);
            assertTrue(opt.getBoolValue());
            assertEquals(999, opt.getIntValue());

            assertEquals(clientPN, ga.getCurrentPlayerNumber());
        }

        final SOCPlayer cliPl = ga.getPlayer(clientPN);
        assertEquals(clientName, cliPl.getName());
        final SOCPlayer cli2Pl = (client2Name != null) ? ga.getPlayer(client2PN) : null;
        if (client2Name != null)
            assertEquals(client2Name, cli2Pl.getName());

        cliPl.setRobotFlag(clientAsRobot, clientAsRobot);
        if (cli2Pl != null)
            cli2Pl.setRobotFlag(clientAsRobot, clientAsRobot);
        if (clientAsRobot)
        {
            tcliConn.setI18NStringManager(null, null);
            if (tcli2Conn != null)
                tcli2Conn.setI18NStringManager(null, null);
        }
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
            if ((pn != clientPN) && (! ga.isSeatVacant(pn)) && ((client2Name == null) || (pn != client2PN)))
                ga.getPlayer(pn).setRobotFlag(othersAsRobot, othersAsRobot);

        if (withResume)
        {
            resumeLoadedGame(ga, server, tcliConn);
            try { Thread.sleep(120); }
            catch(InterruptedException e) {}

            assertEquals(SOCGame.PLAY1, ga.getGameState());
        }

        final Vector<QueueEntry> records = server.records.get(loadedName);
        assertNotNull("record queue for game", records);

        return new StartedTestGameObjects
            (tcli, tcli2, tcliConn, tcli2Conn, sgm, ga, ga.getBoard(), cliPl, cli2Pl, records);
    }

    /**
     * Test loading {@code message-seqs.game.json} and recording a few basic game action sequences,
     * which also test the different {@link SOCServer#recordGameEvent(String, soc.message.SOCMessage)} methods:
     *<UL>
     * <LI> Build a road (without optional preceding SOCBuildRequest): Sent to all players
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

        final StartedTestGameObjects objs =
            connectLoadJoinResumeGame(srv, CLIENT_NAME, null, 0, null, true, 0, false, true);
        final DisplaylessTesterClient tcli = objs.tcli;
        final SOCGame ga = objs.gameAtServer;
        final SOCPlayer cliPl = objs.clientPlayer;
        final Vector<QueueEntry> records = objs.records;

        /* sequence recording: build road */

        records.clear();
        StringBuilder comparesBuild = buildRoadSequence(tcli, ga, cliPl, records, false);

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
                {"p3:SOCDevCardAction:", "|playerNum=3|actionType=DRAW|cardType=5"},  // type known from savegame devCardDeck
                {"!p3:SOCDevCardAction:", "|playerNum=3|actionType=DRAW|cardType=0"},
                {"all:SOCSimpleAction:", "|pn=3|actType=1|v1=22|v2=0"},
                {"all:SOCGameState:", "|state=20"}
            });

        /* sequence recording: choose and move robber, steal from 1 player */

        records.clear();

        assertFalse(cliPl.hasPlayedDevCard());
        assertEquals(1, cliPl.getNumKnights());
        assertEquals(Arrays.asList(SOCDevCardConstants.KNIGHT), cliPl.getDevCardsPlayed());
        tcli.playDevCard(ga, SOCDevCardConstants.KNIGHT);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertTrue(cliPl.hasPlayedDevCard());
        assertEquals(2, cliPl.getNumKnights());
        assertEquals(Arrays.asList(SOCDevCardConstants.KNIGHT, SOCDevCardConstants.KNIGHT), cliPl.getDevCardsPlayed());
        StringBuilder comparesSoldierMoveRobber = moveRobberStealSequence
            (tcli, ga, cliPl, 0, records,
             new String[][]
                {
                    {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " played a Soldier card."},
                    {"all:SOCDevCardAction:", "|playerNum=3|actionType=PLAY|cardType=9"},
                    {"all:SOCPlayerElement:", "|playerNum=3|actionType=SET|elementType=19|amount=1"},
                    {"all:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=15|amount=1"}
                });

        /* leave game, consolidate results */

        srv.destroyGameAndBroadcast(ga.getName(), null);
        tcli.destroy();

        StringBuilder compares = new StringBuilder();
        if (comparesBuild != null)
        {
            compares.append("Build road: Message mismatch: ");
            compares.append(comparesBuild);
        }
        if (comparesBuyCard != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Buy dev card: Message mismatch: ");
            compares.append(comparesBuyCard);
        }
        if (comparesSoldierMoveRobber != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Play soldier Move robber: Message mismatch: ");
            compares.append(comparesSoldierMoveRobber);
        }

        if (compares.length() > 0)
        {
            System.err.println(compares);
            fail(compares.toString());
        }

    }

    /**
     * Build a road on board of savegame artifact {@code message-seqs},
     * optionally with preceding SOCBuildRequest message (which is optional in v2.0 and newer).
     * Tests that sequence and {@code compareRecordsToExpected}'s null-element handling.
     *
     * @param tcli  Test client connected to server and playing in {@code ga}
     * @param ga  Game loaded and started by
     *     {@link #connectLoadJoinResumeGame(RecordingTesterServer, String, String, int, SavedGameModel, boolean, int, boolean, boolean)}
     * @param cliPl  Client's Player object at test server
     * @param records {@code ga}'s message records at test server; doesn't call {@link Vector#clear()}.
     * @return any discrepancies found by {@link #compareRecordsToExpected(List, String[][])}, or null if no differences
     */
    public static StringBuilder buildRoadSequence
        (final DisplaylessTesterClient tcli, final SOCGame ga, final SOCPlayer cliPl,
         final Vector<QueueEntry> records, final boolean withBuildRequest)
    {
        final SOCBoard board = ga.getBoard();
        final String clientName = tcli.getNickname();

        final int ROAD_COORD = 0x40a;
        assertNull(board.roadOrShipAtEdge(ROAD_COORD));
        assertEquals(12, cliPl.getNumPieces(SOCPlayingPiece.ROAD));
        assertArrayEquals(new int[]{3, 3, 3, 4, 4}, cliPl.getResources().getAmounts(false));

        if (withBuildRequest)
        {
            tcli.buildRequest(ga, SOCPlayingPiece.ROAD);

            try { Thread.sleep(60); }
            catch(InterruptedException e) {}
            assertEquals(SOCGame.PLACING_ROAD, ga.getGameState());
            assertArrayEquals(new int[]{2, 3, 3, 4, 3}, cliPl.getResources().getAmounts(false));
        }

        tcli.putPiece(ga, new SOCRoad(cliPl, ROAD_COORD, board));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertNotNull("road built", board.roadOrShipAtEdge(ROAD_COORD));
        assertEquals(11, cliPl.getNumPieces(SOCPlayingPiece.ROAD));
        assertArrayEquals(new int[]{2, 3, 3, 4, 3}, cliPl.getResources().getAmounts(false));

        // for now, quick rough comparison of record contents
        return compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElements:", "|playerNum=3|actionType=LOSE|e1=1,e5=1"},
                ((withBuildRequest) ? new String[]{"all:SOCGameState:", "|state=30"} : null),
                {"all:SOCGameServerText:", "|text=" + clientName + " built a road."},
                {"all:SOCPutPiece:", "|playerNumber=3|pieceType=0|coord=40a"},
                {"all:SOCGameState:", "|state=20"}
            });
    }

    /**
     * Robber hex to move to (0x305, decimal 773) during
     * {@link #moveRobberStealSequence(DisplaylessTesterClient, SOCGame, SOCPlayer, int, Vector, String[][])}.
     */
    public static final int MOVE_ROBBER_STEAL_SEQUENCE_NEW_HEX = 0x305;

    /**
     * Choose and move robber to a known location on board of savegame artifact {@code message-seqs},
     * steal from 1 player. Current game state should be {@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE}.
     * Robber hex should be 0x90a (decimal 2314); will move to {@link #MOVE_ROBBER_STEAL_SEQUENCE_NEW_HEX}.
     *
     * @param tcli  Test client connected to server and playing in {@code ga}
     * @param ga  Game loaded and started by
     *     {@link #connectLoadJoinResumeGame(RecordingTesterServer, String, String, int, SavedGameModel, boolean, int, boolean, boolean)}
     * @param cliPl  Client's Player object at test server
     * @param observabilityMode  Observability mode (0..2, normally 0): See {@code connectLoadJoinResumeGame(..)} javadoc
     * @param records {@code ga}'s message records at test server; doesn't call {@link Vector#clear()}.
     * @param seqPrefix null, or messages which start the sequence to be compared here
     * @return any discrepancies found by {@link #compareRecordsToExpected(List, String[][])}, or null if no differences
     */
    public static StringBuilder moveRobberStealSequence
        (final DisplaylessTesterClient tcli, final SOCGame ga, final SOCPlayer cliPl, final int observabilityMode,
         final Vector<QueueEntry> records, final String[][] seqPrefix)
        throws UnsupportedOperationException
    {
        assertTrue(ga.hasSeaBoard);
        final SOCBoardLarge board = (SOCBoardLarge) ga.getBoard();
        final String clientName = tcli.getNickname();

        assertEquals("old robberHex", 2314, board.getRobberHex());  // 0x90a
        // victim's settlement should be sole adjacent piece
        {
            final int EXPECTED_VICTIM_SETTLEMENT_NODE = 0x405;

            Set<SOCPlayingPiece> pp = new HashSet<>();
            List<SOCPlayer> players = ga.getPlayersOnHex(MOVE_ROBBER_STEAL_SEQUENCE_NEW_HEX, pp);

            assertNotNull(players);
            assertEquals(1, players.size());
            assertEquals(1, players.get(0).getPlayerNumber());

            assertEquals(1, pp.size());
            SOCPlayingPiece vset = (SOCPlayingPiece) (pp.toArray()[0]);
            assertTrue(vset instanceof SOCSettlement);
            assertEquals(1, vset.getPlayerNumber());
            assertEquals(EXPECTED_VICTIM_SETTLEMENT_NODE, vset.getCoordinates());

            assertTrue(board.settlementAtNode(EXPECTED_VICTIM_SETTLEMENT_NODE) instanceof SOCSettlement);

            final int[] ROBBER_HEX_OTHER_NODES = {0x204, 0x205, 0x206, 0x406, 0x404};
            for (final int node : ROBBER_HEX_OTHER_NODES)
                assertNull(board.settlementAtNode(node));
        }

        assertEquals(SOCGame.WAITING_FOR_ROBBER_OR_PIRATE, ga.getGameState());
        tcli.choosePlayer(ga, SOCChoosePlayer.CHOICE_MOVE_ROBBER);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.PLACING_ROBBER, ga.getGameState());
        tcli.moveRobber(ga, cliPl, MOVE_ROBBER_STEAL_SEQUENCE_NEW_HEX);

        try { Thread.sleep(70); }
        catch(InterruptedException e) {}
        assertEquals("new robberHex", MOVE_ROBBER_STEAL_SEQUENCE_NEW_HEX, board.getRobberHex());
        SOCMoveRobberResult robRes = ga.getRobberyResult();
        assertNotNull(robRes);
        final int resType = robRes.getLoot();
        assertTrue(resType > 0);

        final String[][] SEQ = new String[][]
            {
                {"all:SOCGameState:", "|state=54"},
                {"all:SOCGameServerText:", "|text=" + clientName + " must choose to move the robber or the pirate."},
                {"all:SOCGameState:", "|state=33"},
                {"all:SOCGameServerText:", "|text=" + clientName + " will move the robber."},
                {"all:SOCMoveRobber:", "|playerNumber=3|coord=305"},
                {
                    ((observabilityMode != 2) ? "p3:SOCReportRobbery:" : "all:SOCReportRobbery:"),
                    "|perp=3|victim=1|resType=" + resType + "|amount=1|isGainLose=true"
                },
                (observabilityMode != 2)
                    ? new String[]{"p1:SOCReportRobbery:", "|perp=3|victim=1|resType=" + resType + "|amount=1|isGainLose=true"}
                    : null,
                (observabilityMode != 2)
                    ? new String[]{"!p[3, 1]:SOCReportRobbery:", "|perp=3|victim=1|resType=6|amount=1|isGainLose=true"}
                    : null,
                {"all:SOCGameState:", "|state=20"}
            };
        final String[][] expectedSeq;
        if (seqPrefix == null)
        {
            expectedSeq = seqPrefix;
        } else {
            expectedSeq = new String[seqPrefix.length + SEQ.length][];
            System.arraycopy(seqPrefix, 0, expectedSeq, 0, seqPrefix.length);
            System.arraycopy(SEQ, 0, expectedSeq, seqPrefix.length, SEQ.length);
        }

        return compareRecordsToExpected(records, expectedSeq);
    }

    /**
     * Connect with 2 clients, have one offer a trade to the other, decline it.
     * Tests 2-client basics and trade decline message sequence.
     */
    @Test
    public void testTradeDecline2Clients()
        throws IOException
    {
        final String CLIENT1_NAME = "testTradeDecline_p3", CLIENT2_NAME = "testTradeDecline_p2";
        final int PN_C1 = 3, PN_C2 = 2;

        final StartedTestGameObjects objs =
            connectLoadJoinResumeGame
                (srv, CLIENT1_NAME, CLIENT2_NAME, PN_C2, null, true, 0, false, true);
        final DisplaylessTesterClient tcli1 = objs.tcli, tcli2 = objs.tcli2;
        final SOCGame ga = objs.gameAtServer;
        final String gaName = ga.getName();
        final SOCPlayer cli1Pl = objs.clientPlayer;
        final Vector<QueueEntry> records = objs.records;

        records.clear();

        /* client 1: offer trade only to client 2 */

        final boolean[] OFFERED_TO = {false, false, true, false};
        final SOCResourceSet GIVING = new SOCResourceSet(0, 1, 0, 1, 0, 0),
            GETTING = new SOCResourceSet(0, 0, 1, 0, 0, 0);
        tcli1.offerTrade(ga, new SOCTradeOffer
            (gaName, PN_C1, OFFERED_TO, GIVING, GETTING));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        SOCTradeOffer offer = cli1Pl.getCurrentOffer();
        assertNotNull(offer);
        assertEquals(PN_C1, offer.getFrom());
        assertArrayEquals(OFFERED_TO, offer.getTo());
        assertEquals(GIVING, offer.getGiveSet());
        assertEquals(GETTING, offer.getGetSet());
        assertTrue(offer.isWaitingReplyFrom(PN_C2));

        /* client 2: decline offered trade */

        tcli2.rejectOffer(ga);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertFalse(offer.isWaitingReplyFrom(PN_C2));

        StringBuilder compares = compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCMakeOffer:", "|offer=game=" + gaName + "|from=" + PN_C1
                 + "|to=false,false,true,false|give=clay=0|ore=1|sheep=0|wheat=1|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0"},
                {"all:SOCClearTradeMsg:", "|playerNumber=-1"},
                {"all:SOCRejectOffer:", "|playerNumber=" + PN_C2}
            });

        /* leave game, check results */

        srv.destroyGameAndBroadcast(ga.getName(), null);
        tcli1.destroy();
        tcli2.destroy();

        if (compares != null)
        {
            compares.append("testTradeDecline2Clients: Message mismatch: ");
            compares.append(compares);

            System.err.println(compares);
            fail(compares.toString());
        }
    }

    /**
     * Compare game event records against expected sequence.
     * @param records  Game records from server
     * @param expected  Expected: Per-record lists of prefix, any other contained strings
     *     to ignore game name and variable fields.
     *     {@code expected[i]} which are {@code null} are skipped
     *     as if the array didn't contain the null and was 1 element shorter.
     * @return {@code null} if no differences, or the differences found
     */
    public static StringBuilder compareRecordsToExpected
        (final List<QueueEntry> records, final String[][] expected)
    {
        StringBuilder compares = new StringBuilder();

        // preprocess: ignore/remove incidental rename message from artifact loading
        {
            ListIterator<QueueEntry> ri = records.listIterator();
            while (ri.hasNext())
            {
                QueueEntry rec = ri.next();
                if ((rec.toPN == SOCServer.PN_REPLY_TO_UNDETERMINED)
                    && (rec.event instanceof SOCGameServerText)
                    && ((SOCGameServerText) rec.event).getText().startsWith(GAME_RENAMED_TEXT_PREFIX))
                    ri.remove();
            }
        }

        int nExpected = 0;
        for (int i = 0; i < expected.length; ++i)
            if (expected[i] != null)
                ++nExpected;
        int n = records.size();
        if (n > nExpected)
            n = nExpected;

        if (records.size() != nExpected)
            compares.append("Length mismatch: Expected " + nExpected + ", got " + records.size());

        StringBuilder comp = new StringBuilder();
        for (int iExpected = 0, iRecords = 0; iRecords < n; ++iExpected)
        {
            final String[] exps = expected[iExpected];
            if (exps == null)
                // skip null; this loop iteration did ++iExpected but not ++iRecords
                continue;

            comp.setLength(0);
            final String recStr = records.get(iRecords).toString();
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
                compares.append(" [" + iExpected + "]: " + comp);

            ++iRecords;
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
        assertEquals("!p3:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new QueueEntry(event, new int[]{2,3,4});
        assertEquals(-1, qe.toPN);
        assertEquals(event, qe.event);
        assertArrayEquals(new int[]{2,3,4}, qe.excludedPN);
        assertEquals("!p[2, 3, 4]:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new QueueEntry(null, -1);
        assertEquals(-1, qe.toPN);
        assertNull(qe.event);
        assertNull(qe.excludedPN);
        assertEquals("all:null", qe.toString());

        qe = new QueueEntry(null, 3);
        assertEquals(3, qe.toPN);
        assertNull(qe.event);
        assertNull(qe.excludedPN);
        assertEquals("p3:null", qe.toString());
    }

    /**
     * Data class for useful objects returned from
     * {@link TestRecorder#connectLoadJoinResumeGame(RecordingTesterServer, String, String, int, SavedGameModel, boolean, int, boolean, boolean)}
     */
    public static final class StartedTestGameObjects
    {
        /**
         * Client connected to test server; not null.
         * @see #tcliConn
         * @see #clientPlayer
         */
        public final DisplaylessTesterClient tcli;

        /**
         * Optional second client connected to test server, or null.
         * @see #tcli2Conn
         * @see #client2Player
         */
        public final DisplaylessTesterClient tcli2;

        /** Client connection at server for {@link #tcli}; not null */
        public final Connection tcliConn;

        /** Optional second client connection at server for {@link #tcli2}, or null */
        public final Connection tcli2Conn;

        /**
         * Reloaded game data which created {@link #gameAtServer}; not null.
         * Since that game has been created, treat {@code sgm} as read-only:
         * If changes are needed for test conditions, change {@code gameAtServer}.
         */
        public final SavedGameModel sgm;

        /**
         * Reloaded game within the test server, from {@link #sgm}; not null.
         * @see #board
         * @see #clientPlayer
         * @see #records
         */
        public final SOCGame gameAtServer;

        /** {@link #gameAtServer}'s board; not null */
        public final SOCBoard board;

        /** Player in {@link #gameAtServer} controlled by {@link #tcli}; not null */
        public final SOCPlayer clientPlayer;

        /** Player in {@link #gameAtServer} controlled by {@link #tcli2}, or null if not using tcli2 */
        public final SOCPlayer client2Player;

        /** Game message records for {@link #gameAtServer}; not null */
        public final Vector<QueueEntry> records;

        public StartedTestGameObjects
            (DisplaylessTesterClient tcli, DisplaylessTesterClient tcli2, Connection tcliConn, Connection tcli2Conn,
             SavedGameModel sgm, SOCGame gameAtServer, SOCBoard board,
             SOCPlayer clientPlayer, SOCPlayer client2Player, Vector<QueueEntry> records)
        {
            this.tcli = tcli;
            this.tcli2 = tcli2;
            this.tcliConn = tcliConn;
            this.tcli2Conn = tcli2Conn;
            this.sgm = sgm;
            this.gameAtServer = gameAtServer;
            this.board = board;
            this.clientPlayer = clientPlayer;
            this.client2Player = client2Player;
            this.records = records;
        }
    }

}
