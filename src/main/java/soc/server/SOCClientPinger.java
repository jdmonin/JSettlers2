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
 * Sends a {@link SOCServerPing} to each bot every 2 minutes or so, or to humans at an interval from server config.
 * When pinging only humans, checks each connection's {@link SOCClientData#isRobot}.
 *<P>
 * Before v2.7.00 this class was {@code SOCServerRobotPinger}.
 *
 * @author Robert S Thomas
 */
/*package*/ class SOCClientPinger extends Thread
{
    /**
     * True if pinging all robot connections, false if pinging only human clients.
     * @since 2.7.00
     */
    public final boolean isRobotsMode;

    /**
     * Client {@link Connection}s to ping, shared with and modified by the server (synchronized).
     * Ignored if {@link #namedConnections} != {@code null}.
     *<P>
     * Before v2.7.00 this was {@code robotConnections}.
     */
    private Vector<Connection> cliConnections;

    /**
     * Named client {@link Connection}s to ping, shared with and modified by the server,
     * or {@code null} if using {@link #cliConnections}.
     * @since 2.7.00
     */
    private Map<?, Connection> namedConnections;

    /**
     * Sleep time (milliseconds) between pings to all robots: 150 seconds.
     * {@link #run()} method sleeps for 60 seconds less than this.
     * @see #sleepTime
     * @since 2.7.00
     */
    public final int SLEEP_TIME_MILLIS_BOTS = 150 * 1000;

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
     * Create a robot or human client pinger.
     *
     * @param clients  the connections to ping; not {@code null}
     * @param intervalSeconds  Ping interval if humans, or 0 if robots (uses {@link #SLEEP_TIME_MILLIS_BOTS} millis)
     * @see #SOCClientPinger(SOCServer, Map, int)
     * @throws IllegalArgumentException  if {@code clients} is {@code null}
     */
    public SOCClientPinger(SOCServer s, Vector<Connection> clients, final int intervalSeconds)
        throws IllegalArgumentException
    {
        this(s, clients, null, intervalSeconds);
    }

    /**
     * Create a robot or human client pinger.
     *
     * @param clients  the connections to ping; not {@code null}
     * @param intervalSeconds  Ping interval if humans, or 0 if robots (uses {@link #SLEEP_TIME_MILLIS_BOTS} millis)
     * @see #SOCClientPinger(SOCServer, Vector, int)
     * @throws IllegalArgumentException  if {@code clients} is {@code null}
     * @since 2.7.00
     */
    public SOCClientPinger(SOCServer s, Map<?, Connection> clients, final int intervalSeconds)
        throws IllegalArgumentException
    {
        this(s, null, clients, intervalSeconds);
    }

    /**
     * Common constructor.
     * @since 2.7.00
     */
    private SOCClientPinger(SOCServer s, Vector<Connection> clis, Map<?, Connection> namedClis, final int intervalSeconds)
        throws IllegalArgumentException
    {
        if ((clis == null) && (namedClis == null))
            throw new IllegalArgumentException("clients");

        setDaemon(true);
        isRobotsMode = (intervalSeconds == 0);

        srv = s;
        cliConnections = clis;
        namedConnections = namedClis;
        sleepTime = (intervalSeconds == 0) ? SLEEP_TIME_MILLIS_BOTS : (intervalSeconds + 60) * 1000;
        ping = new SOCServerPing((intervalSeconds == 0) ? sleepTime : intervalSeconds);
        alive = true;
        setName (isRobotsMode ? "robotPinger-srv" : "clientPinger-srv");  // Thread name for debug
    }

    /**
     * Client ping loop thread:
     * Send a {@link SOCServerPing} to each bot or human player passed to us,
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
                if (cliConnections != null)
                    for (Connection conn : cliConnections)
                        pingOne(conn);
                else
                    for (Connection conn : namedConnections.values())
                        pingOne(conn);
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
        cliConnections = null;
        namedConnections = null;
    }

    /**
     * Ping one connection, checking its {@link SOCClientData} if not {@link #isRobotsMode}.
     * @param conn  Connection to ping; not {@code null}
     * @since 2.7.00
     */
    private void pingOne(final Connection conn)
    {
        if (isRobotsMode)
        {
            if (D.ebugIsEnabled())
                D.ebugPrintlnINFO("(*)(*)(*)(*) PINGING " + conn.getData());
        } else {
            if (! conn.isVersionKnown())
                return;

            SOCClientData scd = (SOCClientData) conn.getAppData();
            if ((scd == null) || scd.isRobot)
                return;
        }

        conn.put(ping);
    }

    /**
     * Cleanly exit the thread's run loop.
     */
    public void stopPinger()
    {
        alive = false;
    }

}
