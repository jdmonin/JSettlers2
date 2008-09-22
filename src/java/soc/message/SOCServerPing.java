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
package soc.message;


/**
 * This is a ping message from the server
 *
 * @author Robert S Thomas
 */
@SuppressWarnings("serial")
public class SOCServerPing extends SOCMessage
{
    /**
     * the ammount of time to sleep waiting for the next ping
     */
    int sleepTime;

    /**
     * Create a ServerPing message.
     *
     * @param st  the sleep time
     */
    public SOCServerPing(int st)
    {
        messageType = SERVERPING;
        sleepTime = st;
    }

    /**
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
    public String toCmd(int st)
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
