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
 * This message means that someone is leaving all the channels
 *
 * @author Robert S Thomas
 */
public class SOCLeaveAll extends SOCMessage
{
    /**
     * Create a LeaveAll message.
     *
     */
    public SOCLeaveAll()
    {
        messageType = LEAVEALL;
    }

    /**
     * <LEAVEALL>
     *
     * @return the command String
     */
    public String toCmd()
    {
        return Integer.toString(LEAVEALL);
    }

    /**
     * Parse the command String into a LeaveAll message
     *
     * @param s   the String to parse
     * @return    a LeaveAll message
     */
    public static SOCLeaveAll parseDataStr(String s)
    {
        return new SOCLeaveAll();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCLeaveAll:";
    }
}
