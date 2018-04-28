/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2016-2018 Jeremy D Monin <jeremy@nand.net>. Contents were
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

import java.util.Hashtable;

import soc.robot.SOCRobotClient;

/**
 * Each local robot in the {@link SOCServer} gets its own client thread.
 * Equivalent to main thread used in {@link SOCRobotClient} when connected
 * over the TCP network.
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
class SOCLocalRobotClient implements Runnable
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
     * After creating it, {@link Thread#yield() yield} the current thread and then sleep
     * 75 milliseconds, to give the robot time to start itself up.
     * The SOCPlayerLocalRobotRunner's run() will add the {@link SOCRobotClient} to {@link #robotClients}.
     * @param rname  Name of robot
     * @param strSocketName  Server's stringport socket name, or null
     * @param port    Server's tcp port, if <tt>strSocketName</tt> is null
     * @param cookie  Cookie for robot connections to server
     * @since 1.1.09
     * @see SOCServer#setupLocalRobots(int, int)
     * @throws ClassNotFoundException  if a robot class, or SOCDisplaylessClient,
     *           can't be loaded. This can happen due to packaging of the server-only JAR.
     * @throws LinkageError  for same reason as ClassNotFoundException
     */
    public static void createAndStartRobotClientThread
        (final String rname, final String strSocketName, final int port, final String cookie)
        throws ClassNotFoundException, LinkageError
    {
        SOCRobotClient rcli;
        if (strSocketName != null)
            rcli = new SOCRobotClient(strSocketName, rname, "pw", cookie);
        else
            rcli = new SOCRobotClient("localhost", port, rname, "pw", cookie);
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

}  // class SOCPlayerLocalRobotRunner
