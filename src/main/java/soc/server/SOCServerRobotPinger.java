/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2016-2017 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Vector;

import soc.disableDebug.D;
import soc.message.SOCServerPing;
import soc.server.genericServer.Connection;


/**
 * Pings the robots so they know they're connected to an active server.
 * Sends a {@link SOCServerPing} to each bot every 2 minutes or so.
 *
 * @author Robert S Thomas
 */
public class SOCServerRobotPinger extends Thread
{
    /** A list of robot {@link Connection}s to ping, shared with and modified by the server. */
    private Vector<Connection> robotConnections;

    /**
     * Sleep time (milliseconds) between pings: 150 seconds.
     * {@link #run()} method sleeps for 60 seconds less than this.
     */
    private final int sleepTime = 150000;

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
     */
    public SOCServerRobotPinger(SOCServer s, Vector<Connection> robots)
    {
        srv = s;
        robotConnections = robots;
        ping = new SOCServerPing(sleepTime);
        alive = true;
        setName ("robotPinger-srv");  // Thread name for debug
    }

    /**
     * Robot ping loop thread:
     * Send a {@link SOCServerPing} to each bot connected to the server,
     * then sleep for {@link #sleepTime} minus 60 seconds.
     * Exits loop when {@link #stopPinger()} is called.
     */
    @Override
    public void run()
    {
        final String pingCmdStr = ping.toCmd();

        while (alive)
        {
            boolean retry = false;

            if (! robotConnections.isEmpty())
            {
                try
                {
                    for (Connection robotConnection : robotConnections)
                    {
                        if (D.ebugIsEnabled())
                            D.ebugPrintln("(*)(*)(*)(*) PINGING " + robotConnection.getData());
                        robotConnection.put(pingCmdStr);
                    }
                } catch (ConcurrentModificationException e) {
                    retry = true;
                }
            }

            yield();

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
    }

    /**
     * Cleanly exit the thread's run loop.
     */
    public void stopPinger()
    {
        alive = false;
    }

}  // public class SOCServerRobotPinger
