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
 * This is a text message that shows in a status box on the client.
 * Used for "welcome" message at initial connect (follows JOINAUTH).
 *
 * @author Robert S. Thomas
 */
public class SOCStatusMessage extends SOCMessage
{
    /**
     * Status message
     */
    private String status;

    /**
     * Create a StatusMessage message.
     *
     * @param st  the status
     */
    public SOCStatusMessage(String st)
    {
        messageType = STATUSMESSAGE;
        status = st;
    }

    /**
     * @return the status
     */
    public String getStatus()
    {
        return status;
    }

    /**
     * STATUSMESSAGE sep game
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(status);
    }

    /**
     * STATUSMESSAGE sep status
     *
     * @param st  the status
     * @return the command string
     */
    public static String toCmd(String st)
    {
        return STATUSMESSAGE + sep + st;
    }

    /**
     * Parse the command String into a StatusMessage message
     *
     * @param s   the String to parse
     * @return    a StatusMessage message, or null of the data is garbled
     */
    public static SOCStatusMessage parseDataStr(String s)
    {
        return new SOCStatusMessage(s);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCStatusMessage:status=" + status;
    }
}
