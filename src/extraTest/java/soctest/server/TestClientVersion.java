/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2026 Jeremy D Monin <jeremy@nand.net>
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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.extra.server.RecordingSOCServer;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.robot.SOCRobotBrain;
import soc.server.SOCGameHandler;
import soc.server.genericServer.Connection;
import soc.util.Version;
import soctest.server.TestRecorder.StartedTestGameObjects;

/**
 * Extra testing of a few things related to different client versions.
 * Expands coverage past the basic unit tests done by {@link TestRecorder}.
 *
 * @since 2.7.00
 */
public class TestClientVersion
{
    private static RecordingSOCServer srv;

    @BeforeClass
    public static void startStringportServer()
    {
        SOCGameHandler.DESTROY_BOT_ONLY_GAMES_WHEN_OVER = false;  // keep games around, to check asserts
        SOCRobotBrain.ALWAYS_PAUSE_FASTER = true;

        srv = new RecordingSOCServer();
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
     * Join a {@link DisplaylessTesterClient} with default version and one with an older version;
     * tests {@link SOCGameOption#FLAG_OPPORTUNISTIC} when older client sits down and starts a game.
     */
    @Test
    public void testGameOpportunistic()
        throws IOException
    {
        // This setup code is based on TestRecorder.testBasics_Loadgame;
        // if you change one, consider changing all occurrences

        // unique client nickname, in case tests run in parallel
        final String CLIENT_NAME = "testCliVersion";

        assertNotNull(srv);

        /** with defaults */

        DisplaylessTesterClient tcli = new DisplaylessTesterClient
            (RecordingSOCServer.STRINGPORT_NAME, CLIENT_NAME, null, null);
        assertEquals(Version.versionNumber(), tcli.getVersion());
        tcli.init();
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertEquals("get version from test SOCServer", Version.versionNumber(), tcli.getServerVersion());

        Connection cliConnAtSrv = srv.getConnection(CLIENT_NAME);
        assertNotNull(cliConnAtSrv);
        assertEquals(Version.versionNumber(), cliConnAtSrv.getVersion());

        /** reporting other version */
        final int OTHER_VERSION_NUMBER = 1117;
        DisplaylessTesterClient tcliOld = new DisplaylessTesterClient
            (RecordingSOCServer.STRINGPORT_NAME, CLIENT_NAME + "O", null, null);
        tcliOld.setVersion(OTHER_VERSION_NUMBER);
        assertEquals(OTHER_VERSION_NUMBER, tcliOld.getVersion());
        tcliOld.init();
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}

        assertEquals("get version from test SOCServer", Version.versionNumber(), tcliOld.getServerVersion());

        cliConnAtSrv = srv.getConnection(CLIENT_NAME + "O");
        assertNotNull(cliConnAtSrv);
        assertEquals(OTHER_VERSION_NUMBER, cliConnAtSrv.getVersion());

        // Have tcli make and join a game with an option with FLAG_OPPORTUNISTIC
        final int VERS_OPT_UB = 2700;
        SOCGameOption optUB =  SOCGameOptionSet.getAllKnownOptions().get("UB");
        assertNotNull(optUB);
        assertEquals(VERS_OPT_UB, optUB.minVersion);
        assertTrue(optUB.hasFlag(SOCGameOption.FLAG_OPPORTUNISTIC));
        assertTrue("tcli new enough for gameopt UB", tcli.getVersion() >= VERS_OPT_UB);
        assertTrue("tcliOld older than gameopt UB", tcliOld.getVersion() < VERS_OPT_UB);
        final StartedTestGameObjects objs =
            TestRecorder.createJoinNewGame
                (srv, tcli, SOCGameOption.parseOptionsToSet("PL=2,UB=t,N7=t7", srv.knownOpts));
        final SOCGame gaAtSrv = objs.gameAtServer;
        final String gaName = gaAtSrv.getName();
        final SOCGame gaAtCli = tcli.getGame(gaName);

        // What game opts and details does first cli see?
        assertNotNull("announced to creating client", gaAtCli);
        assertEquals(-1, gaAtCli.getClientVersionMinRequired());
        // TODO check opts etc

        // What game opts and details does older cli see?
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}
        assertTrue("announced to older client", tcliOld.isServerGame(gaName));
        final SOCGameOptionSet gaOptsAtCliOld = tcliOld.getServerGameOptions(gaName);
        assertNotNull("announced to older client with opts", gaOptsAtCliOld);
        assertTrue(gaOptsAtCliOld.isOptionSet("UB"));
        // TODO check opt flags?

        // can older cli join it?
        tcliOld.joinGame(gaName);
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}
        final SOCGame gaAtCliOld = tcliOld.getGame(gaName);
        assertNotNull(gaAtCliOld);

        // TODO now have another modern cli join srv, chk game opts ,see if UB=t

        // what happens at sitdown/startgame?  At tcliOld, does gameopt UB gain flag CLI JOIN ONLY?
        // verify seat # is empty, sit down
        final int PN_SIT_CLI_OLD = 1;
        assertTrue(gaAtSrv.isSeatVacant(PN_SIT_CLI_OLD));
        assertTrue(gaAtCliOld.isSeatVacant(PN_SIT_CLI_OLD));
        tcliOld.sitDown(gaAtCliOld, PN_SIT_CLI_OLD);
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}
        assertFalse(gaAtSrv.isSeatVacant(PN_SIT_CLI_OLD));
        assertFalse(gaAtCliOld.isSeatVacant(PN_SIT_CLI_OLD));
        assertEquals(tcliOld.getNickname(), gaAtSrv.getPlayer(PN_SIT_CLI_OLD).getName());
        assertEquals(tcliOld.getNickname(), gaAtCliOld.getPlayer(PN_SIT_CLI_OLD).getName());

        // check gameopt UB at this point
        SOCGameOptionSet opts = tcli.getServerGameOptions(gaName);
        assertNotNull(opts);
        assertTrue("at tcli", opts.isOptionSet("UB"));
        opts = tcliOld.getServerGameOptions(gaName);
        assertNotNull(opts);
        assertTrue("at tcliOld", opts.isOptionSet("UB"));

        // TODO at other new cli, see if UB changed yet

        // start game
        tcli.startGame(gaAtCli);
        try { Thread.sleep(120); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.START1A, gaAtSrv.getGameState());
        assertEquals(SOCGame.START1A, gaAtCli.getGameState());
        assertEquals(SOCGame.START1A, gaAtCliOld.getGameState());
        // at all cli, see if UB changed now
        opts = tcli.getServerGameOptions(gaName);
        assertNotNull(opts);
        assertFalse("gameopt UB removed now for compat with old client", opts.containsKey("UB"));
        opts = tcliOld.getServerGameOptions(gaName);
        assertNotNull(opts);
        assertTrue("existing game's opts can't be changed/removed at old client", opts.isOptionSet("UB"));

        // cleanup
        tcli.leaveGame(gaName);
        tcliOld.leaveGame(gaName);
        try { Thread.sleep(40); }
        catch(InterruptedException e) {}
        tcli.destroy();
        tcliOld.destroy();
    }

}

