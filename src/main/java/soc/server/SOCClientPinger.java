/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2016-2017,2020,2026 Jeremy D Monin <jeremy@nand.net>
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

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Vector;

import soc.disableDebug.D;
import soc.message.SOCServerPing;
import soc.server.genericServer.Connection;


/**
 * Pings the robots so they know they're connected to an active server,
 * or the human clients so they don't get disconnected while idle if they're behind NAT or a firewall with low timeout.
 * Sends a {@link SOCServerPing} to each bot every 2 minutes or so, and to humans at an interval from server config.
 *<P>
 * Before v2.7.00 this class was {@code SOCServerRobotPinger}.
 *
 * @author Robert S Thomas
 */
/*package*/ class SOCClientPinger extends Thread
{
    /**
     * True if pinging all robot connections, false if pinging human clients.
     * @since 2.7.00
     */
    public final boolean isRobotsMode;

    /**
     * A list of robot {@link Connection}s to ping, shared with and modified by the server.
     * Ignored unless {@link #isRobotsMode}.
     * @see #humanConnections
     */
    private Vector<Connection> robotConnections;

    /**
     * A map of huamn client {@link Connection}s to ping, shared with and modified by the server.
     * Ignored if {@link #isRobotsMode}.
     * @see #robotConnections
     * @since 2.7.00
     */
    private Map<?, Connection> humanConnections;

    /**
     * Sleep time (milliseconds) between pings to all robots: 150 seconds.
     * {@link #run()} method sleeps for 60 seconds less than this.
     * @see #sleepTime
     * @since 2.7.00
     */
    private final int SLEEP_TIME_MILLIS_BOTS = 150 * 1000;

    /**
     * Sleep time (milliseconds) between pings to all robots or human clients.
     * {@link #run()} method sleeps for 60 seconds less than this.
     */
    private final int sleepTime;

    /** Ping message (with {@link #sleepTime} param) to send to each bot */
    private final SOCServerPing ping;

    /**
     * Alive flag; loop while true.
     * @see #stopPinger()
     */
    private volatile boolean alive;

    /**
     * Our server.
     * @since 1.1.11
     */
    @SuppressWarnings("unused")
    private final SOCServer srv;

    /**
     * Create a server robot pinger
     *
     * @param robots  the connections to robots; a Vector of {@link Connection}s
     * @see #SOCClientPinger(SOCServer, Map, int)
     */
    public SOCClientPinger(SOCServer s, Vector<Connection> robots)
    {
        setDaemon(true);
        isRobotsMode = true;

        srv = s;
        robotConnections = robots;
        sleepTime = SLEEP_TIME_MILLIS_BOTS;
        ping = new SOCServerPing(sleepTime);
        alive = true;
        setName ("robotPinger-srv");  // Thread name for debug
    }

    /**
     * Create a human client pinger
     *
     * @param humanClients  the connections to clients
     * @see #SOCClientPinger(SOCServer, Vector)
     * @since 2.7.00
     */
    public SOCClientPinger(SOCServer s, Map<?, Connection> humanClients, final int intervalSeconds)
    {
        setDaemon(true);
        isRobotsMode = false;

        srv = s;
        humanConnections = humanClients;
        sleepTime = (intervalSeconds + 60) * 1000;
        ping = new SOCServerPing(intervalSeconds);
        alive = true;
        setName ("clientPinger-srv");  // Thread name for debug
    }

    /**
     * Client ping loop thread:
     * Send a {@link SOCServerPing} to each bot or human player connected to the server,
     * then sleep for {@link #sleepTime} minus 60 seconds.
     * Exits loop when {@link #stopPinger()} is called.
     */
    @Override
    public void run()
    {
        while (alive)
        {
            boolean retry = false;

            try
            {
                if (isRobotsMode)
                {
                    if (! robotConnections.isEmpty())
                        for (Connection robotConnection : robotConnections)
                        {
                            if (D.ebugIsEnabled())
                                D.ebugPrintlnINFO("(*)(*)(*)(*) PINGING " + robotConnection.getData());
                            robotConnection.put(ping);
                        }
                } else {
                    if (! humanConnections.isEmpty())
                        for (Connection conn : humanConnections.values())
                        {
                            if (! conn.isVersionKnown())
                                continue;
                            SOCClientData scd = (SOCClientData) conn.getAppData();
                            if ((scd == null) || scd.isRobot)
                                continue;

                            conn.put(ping);
                        }
                }
            } catch (ConcurrentModificationException e) {
                retry = true;
            }

            Thread.yield();

            final int msec = (retry) ? 250 : (sleepTime - 60000);
            try
            {
                sleep(msec);
            }
            catch (InterruptedException exc) {}
        }

        //
        //  cleanup
        //
        robotConnections = null;
        humanConnections = null;
    }

    /**
     * Cleanly exit the thread's run loop.
     */
    public void stopPinger()
    {
        alive = false;
    }

}
