/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.server;

import java.util.Enumeration;
import java.util.Vector;

import org.apache.log4j.Logger;

import soc.message.SOCServerPing;
import soc.server.genericServer.StringConnection;


/**
 * Pings the robots so that they know that they're connected
 * to the server
 *
 * @author Robert S Thomas
 */
public class SOCServerRobotPinger extends Thread
{
    Vector robotConnections;
    int sleepTime = 150000;
    SOCServerPing ping;
    boolean alive;
    private transient Logger log = Logger.getLogger(this.getClass().getName());

    /**
     * Create a server robot pinger
     *
     * @param robots  the connections to robots
     */
    public SOCServerRobotPinger(Vector robots)
    {
        robotConnections = robots;
        ping = new SOCServerPing(sleepTime);
        alive = true;
        setName ("robotPinger-srv");  // Thread name for debug
    }

    /**
     * DOCUMENT ME!
     */
    public void run()
    {
        while (alive)
        {
            if (!robotConnections.isEmpty())
            {
                Enumeration robotConnectionsEnum = robotConnections.elements();

                while (robotConnectionsEnum.hasMoreElements())
                {
                    StringConnection robotConnection = (StringConnection) robotConnectionsEnum.nextElement();
                    log.debug("(*)(*)(*)(*) PINGING " + robotConnection.getData());
                    robotConnection.put(ping.toCmd());
                }
            }

            yield();

            try
            {
                sleep(sleepTime - 60000);
            }
            catch (InterruptedException exc) {}
        }

        //
        //  cleanup
        //
        robotConnections = null;
        ping = null;
    }

    /**
     * DOCUMENT ME!
     */
    public void stopPinger()
    {
        alive = false;
    }
}
