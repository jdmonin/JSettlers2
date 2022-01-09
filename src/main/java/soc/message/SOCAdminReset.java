/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2014,2017,2020 Jeremy D Monin <jeremy@nand.net>
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
 * This message allows the admin to reset a robot client remotely;
 * bot will disconnect and reconnect. Sent from server to robot client only.
 *
 * @author Robert S Thomas
 */
public class SOCAdminReset extends SOCMessage
{
    private static final long serialVersionUID = 100L;  // last structural change v1.0.0 or earlier

    /**
     * Create a AdminReset message.
     *
     */
    public SOCAdminReset()
    {
        messageType = ADMINRESET;
    }

    /**
     * <ADMINRESET>
     *
     * @return the command String
     */
    public String toCmd()
    {
        return Integer.toString(ADMINRESET);
    }

    /**
     * Parse the command String into a AdminReset message
     *
     * @param s   the String to parse; contents ignored, since this message has no parameters
     * @return    a AdminReset message
     */
    public static SOCAdminReset parseDataStr(String s)
    {
        return new SOCAdminReset();
    }

    /**
     * @return a human readable form of the message, which has no parameters
     */
    public String toString()
    {
        return "SOCAdminReset:";
    }
}
