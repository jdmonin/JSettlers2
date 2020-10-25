/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2016-2018,2020 Jeremy D Monin <jeremy@nand.net>. Contents were
 * formerly part of SOCServer.java; portions of this file Copyright (C) 2007-2013 Jeremy D Monin.
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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
package soc.server;

import java.lang.reflect.Constructor;
import java.util.Hashtable;

import soc.baseclient.ServerConnectInfo;
import soc.game.SOCGameOptionSet;
import soc.robot.SOCRobotClient;

/**
 * Each local robot in the {@link SOCServer} gets its own client thread.
 * Equivalent to main thread used in {@link SOCRobotClient} when connected
 * over the TCP network. Create by calling convenience method
 * {@link #createAndStartRobotClientThread(String, ServerConnectInfo, SOCGameOptionSet, Constructor)}.
 *<P>
 * This class was originally SOCPlayerClient.SOCPlayerLocalRobotRunner,
 * then moved in 1.1.09 to SOCServer.SOCPlayerLocalRobotRunner.
 * Split out in 2.0.00 to its own top-level class.
 * Before 2.0.00, the thread name prefix was {@code robotrunner-} not {@code localrobotclient-}.
 *
 * @see SOCServer#setupLocalRobots(int, int)
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
/*package*/ class SOCLocalRobotClient implements Runnable
{
    /**
     * All the started {@link SOCRobotClient}s. Key is the bot nickname.
     *<P>
     *<b>Note:</b> If a bot is disconnected from the server, it's not
     * removed from this list, because the same bot will try to reconnect.
     * To see if a bot is connected, check {@link SOCServer#robots} instead.
     * @since 1.1.13
     */
    public static Hashtable<String, SOCRobotClient> robotClients = new Hashtable<String, SOCRobotClient>();

    /**
     * This bot's client in {@link #robotClients}.
     */
    final SOCRobotClient rob;

    protected SOCLocalRobotClient(SOCRobotClient rc)
    {
        rob = rc;
    }

    public void run()
    {
        final String rname = rob.getNickname();
        Thread.currentThread().setName("localrobotclient-" + rname);  // was robotrunner- in v1.x.xx
        robotClients.put(rname, rob);
        rob.init();
    }

    /**
     * Create and start a robot client within a {@link SOCLocalRobotClient} thread.
     * Optionally give it a copy of {@link SOCServer#knownOpts} or other Known Options.
     * After creating it, {@link Thread#yield() yield} the current thread (from caller)
     * and then sleep 75 milliseconds, to give the robot time to start itself up.
     * The {@link SOCLocalRobotClient}'s {@code run()} will add the {@link SOCRobotClient} to {@link #robotClients}.
     *
     * @param rname  Name of robot
     * @param sci  Server connect info (TCP or local) with {@code robotCookie}; not {@code null}
     * @param Set of Known Options to deep-copy for robot client, or {@code null} to use defaults
     * @param cliConstruc3p  For a third-party bot client, its constructor with same parameters and
     *     behavior as {@link SOCRobotClient#SOCRobotClient(ServerConnectInfo, String, String)};
     *     {@code null} for built-in bots
     * @since 1.1.09
     * @see SOCServer#setupLocalRobots(int, int)
     * @throws ClassNotFoundException  if a robot class, or SOCDisplaylessClient,
     *           can't be loaded. This can happen due to packaging of the server-only JAR.
     * @throws LinkageError  for same reason as ClassNotFoundException
     * @throws IllegalArgumentException if {@code sci == null}
     * @throws ReflectiveOperationException if there's a problem instantiating from a non-null {@link cliConstruc3p}
     */
    public static void createAndStartRobotClientThread
        (final String rname, final ServerConnectInfo sci, final SOCGameOptionSet knownOpts,
         final Constructor<? extends SOCRobotClient> cliConstruc3p)
        throws ClassNotFoundException, IllegalArgumentException, LinkageError, ReflectiveOperationException
    {
        final SOCRobotClient rcli =
            (cliConstruc3p == null)
            ? new SOCRobotClient(sci, rname, "pw")
            : cliConstruc3p.newInstance(sci, rname, "pw");

        if (knownOpts != null)
            rcli.knownOpts = new SOCGameOptionSet(knownOpts, true);
        rcli.printedInitialWelcome = true;  // don't clutter the server console

        Thread rth = new Thread(new SOCLocalRobotClient(rcli));
        rth.setDaemon(true);
        rth.start();  // run() will add to robotClients

        Thread.yield();
        try
        {
            Thread.sleep(75);  // Let that robot go for a bit.
                // robot runner thread will call its init()
        }
        catch (InterruptedException ie) {}
    }

}
