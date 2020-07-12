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
import java.util.Vector;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.server.SOCServer;
import soc.server.genericServer.Connection;
import soc.server.savegame.SavedGameModel;
import soc.util.Version;
import soctest.server.RecordingTesterServer.QueueEntry;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCServer#recordGameEvent(String, soc.message.SOCMessage)} and similar methods.
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
     * Test the basics, to rule out problems with that if other tests fail:
     *<UL>
     * <LI> {@link RecordingTesterServer} is up
     * <LI> Bots are connected to test server
     * <LI> {@link DisplaylessTesterClient} can connect as "debug" and see server's version
     * <LI> Server sees "debug" client connection
     *</UL>
     */
    @Test
    public void testBasics_ServerUpWithBotsConnectAsDebug()
    {
        assertNotNull(srv);
        assertEquals(RecordingTesterServer.STRINGPORT_NAME, srv.getLocalSocketName());

        final int nConn = srv.getNamedConnectionCount();
        assertEquals
            ("some bots are connected; actual nConn=" + nConn, RecordingTesterServer.NUM_STARTROBOTS, nConn);

        DisplaylessTesterClient tcli = new DisplaylessTesterClient
            (RecordingTesterServer.STRINGPORT_NAME, "debug");
        tcli.init();
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertEquals("get version from test SOCServer", Version.versionNumber(), tcli.getServerVersion());
        assertEquals(1 + RecordingTesterServer.NUM_STARTROBOTS, srv.getNamedConnectionCount());
        Connection tcliAtServer = srv.getConnection("debug");
        assertNotNull(tcliAtServer);

        tcli.destroy();

        try { Thread.sleep(120); }
        catch(InterruptedException e) {}
        assertEquals(RecordingTesterServer.NUM_STARTROBOTS, srv.getNamedConnectionCount());
}

    @Test
    public void testLoadgame()
        throws IOException
    {
        assertNotNull(srv);

        DisplaylessTesterClient tcli = new DisplaylessTesterClient
            (RecordingTesterServer.STRINGPORT_NAME, "debug");
        tcli.init();
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertEquals("get version from test SOCServer", Version.versionNumber(), tcli.getServerVersion());

        SavedGameModel sgm = soctest.server.savegame.TestLoadgame.load("classic-botturn.game.json");
        assertNotNull(sgm);
        assertEquals("classic", sgm.gameName);

        Connection tcliConn = srv.getConnection("debug");
        assertNotNull(tcliConn);
        String loadedName = srv.createAndJoinReloadedGame(sgm, tcliConn, null);
        assertNotNull("reloaded game name", loadedName);

        // wait to join in client's thread
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertTrue("debug cli member of reloaded game?", srv.isGameMember(tcliConn, loadedName));

        // leave game
        tcli.destroy();
    }

    @Test
    public void testLoadAndBuildRoadSeq()
        throws IOException
    {
        assertNotNull(srv);

        DisplaylessTesterClient tcli = new DisplaylessTesterClient
            (RecordingTesterServer.STRINGPORT_NAME, "debug");
        tcli.init();
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertEquals("get version from test SOCServer", Version.versionNumber(), tcli.getServerVersion());

        SavedGameModel sgm = soctest.server.savegame.TestLoadgame.load("message-seqs.game.json");
        assertNotNull(sgm);
        assertEquals("message-seqs", sgm.gameName);

        Connection tcliConn = srv.getConnection("debug");
        assertNotNull(tcliConn);
        String loadedName = srv.createAndJoinReloadedGame(sgm, tcliConn, null);
        assertNotNull("message-seqs game name", loadedName);

        // wait to join in client's thread
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertTrue("debug cli member of reloaded game?", srv.isGameMember(tcliConn, loadedName));

        final SOCGame ga = srv.getGame(loadedName);
        assertNotNull("game object at server", ga);
        final int PN = 3;
        assertEquals(PN, ga.getCurrentPlayerNumber());
        final SOCPlayer cliPl = ga.getPlayer(PN);
        assertEquals("debug", cliPl.getName());

        assertTrue("resume loaded game", srv.resumeReloadedGame(tcliConn, ga));
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        final Vector<QueueEntry> records = srv.records.get(loadedName);
        assertNotNull("record queue for game", records);

        /* test build-road sequence recording */

        records.clear();
        final int ROAD_COORD = 0x40a;
        assertEquals(14, cliPl.getNumPieces(SOCPlayingPiece.ROAD));
        tcli.putPiece(ga, new SOCRoad(cliPl, ROAD_COORD, ga.getBoard()));

        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertNotNull("road built", ga.getBoard().roadOrShipAtEdge(ROAD_COORD));
        assertEquals(13, cliPl.getNumPieces(SOCPlayingPiece.ROAD));

        // for now, quick rough comparison of record contents
        final String[][] ROAD_EXPECTED_RECORDS =
            {
                {"all:SOCPlayerElements:", "|playerNum=3|actionType=LOSE|e1=1,e5=1"},
                {"all:SOCGameServerText:", null},
                {"all:SOCPutPiece:", "|playerNumber=3|pieceType=0|coord=40a"},
                {"all:SOCGameState:", "|state=20"}
            };
        StringBuilder compares = compareRecordsToExpected(records, ROAD_EXPECTED_RECORDS);

        // leave game
        tcli.destroy();

        if (compares != null)
        {
            compares.insert(0, "Records mismatch: ");
            System.err.println(compares);
            fail(compares.toString());
        }
    }

    /**
     * Compare game event records against expected sequence.
     * @param records  Game records from server
     * @param expected  Expected: Per-record pairs of prefix, suffix strings (suffix can be null)
     *     to ignore game name in middle
     * @return {@code null} if no differences, or the differences found
     */
    private StringBuilder compareRecordsToExpected
        (final Vector<QueueEntry> records, final String[][] expected)
    {
        StringBuilder compares = new StringBuilder();

        int n = records.size();
        if (expected.length < n)
            n = expected.length;

        if (records.size() != expected.length)
            compares.append("Length mismatch: Expected " + expected.length + ", got " + records.size());

        for (int i = 0; i < n; ++i)
        {
            final String recStr = records.get(i).toString();
            final String expStart = expected[i][0], expEnd = expected[i][1];
            if (recStr.startsWith(expStart) &&
                ((expEnd == null) || recStr.endsWith(expEnd)))
                continue;

            compares.append(" [" + i + "]: Expected "
                + expStart + "..." + ((expEnd != null) ? expEnd : "")
                + ", saw " + recStr);
        }

        if (compares.length() == 0)
            return null;
        else
            return compares;
    }

}
