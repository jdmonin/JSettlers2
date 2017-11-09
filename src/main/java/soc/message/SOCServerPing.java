/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2014,2016-2017 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;


/**
 * This is a ping message from the server, or its reply from the client.
 *<P>
 * In version 1.1.08 and higher, the client echoes
 * the ping back to the server.  Server can use
 * this to ensure the client is still connected.
 * If another client attempts to connect with the same name,
 * same host (IP address), the first client will be pinged.
 * If the client does not respond within a minute or so,
 * it is replaced in all games by the newly connecting client.
 * Server attempts to send a final SERVERPING to the old client,
 * with sleepTime -1, to let it know it's no longer connected.
 *<P>
 * The server sends bot clients {@code SOCServerPing} about once every 2 minutes,
 * and those clients also locally generate and send themselves
 * one {@link SOCTimingPing} per second in their active games.
 *
 * @author Robert S Thomas
 * @see SOCAdminPing
 */
public class SOCServerPing extends SOCMessage
{
    private static final long serialVersionUID = 100L;  // last structural change v1.0.0 or earlier

    /**
     * the amount of time to sleep waiting for the next ping, or -1;
     * see {@link #getSleepTime()} for description.
     */
    private final int sleepTime;

    /**
     * Create a ServerPing message.
     *
     * @param st  the sleep time; see {@link #getSleepTime()} for description
     */
    public SOCServerPing(int st)
    {
        messageType = SERVERPING;
        sleepTime = st;
    }

    /**
     * Get the sleep time sent from the server:
     * For human clients, the value to send back to the server,
     * or -1 if server is telling a client it's being disconnected
     * because a new client is replacing it, or for bots (informational)
     * the amount of time server will sleep waiting to send the next ping.
     * @return the sleep time
     */
    public int getSleepTime()
    {
        return sleepTime;
    }

    /**
     * SERVERPING sep sleepTime
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(sleepTime);
    }

    /**
     * SERVERPING sep sleepTime
     *
     * @param  st  the sleep time
     * @return the command String
     */
    public static String toCmd(int st)
    {
        return SERVERPING + sep + st;
    }

    /**
     * Parse the command String into a ServerPing message
     *
     * @param s   the String to parse
     * @return    a ServerPing message
     */
    public static SOCServerPing parseDataStr(String s)
    {
        return new SOCServerPing(Integer.parseInt(s));
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCServerPing:sleepTime=" + sleepTime;
    }
}
