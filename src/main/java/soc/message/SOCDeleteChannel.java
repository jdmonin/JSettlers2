/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2014,2017 Jeremy D Monin <jeremy@nand.net>
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
 * This message means that a chat channel has been destroyed.
 *
 * @author Robert S Thomas
 * @see SOCDeleteGame
 */
public class SOCDeleteChannel extends SOCMessage
{
    private static final long serialVersionUID = 100L;  // last structural change v1.0.0 or earlier

    /**
     * Name of the channel.
     */
    private String channel;

    /**
     * Create a DeleteChannel message.
     *
     * @param ch  name of the channel
     */
    public SOCDeleteChannel(String ch)
    {
        messageType = DELETECHANNEL;
        channel = ch;
    }

    /**
     * @return the name of the channel
     */
    public String getChannel()
    {
        return channel;
    }

    /**
     * DELETECHANNEL sep channel
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(channel);
    }

    /**
     * DELETECHANNEL sep channel
     *
     * @param ch  the channel name
     * @return    the command string
     */
    public static String toCmd(String ch)
    {
        return DELETECHANNEL + sep + ch;
    }

    /**
     * Parse the command String into a DeleteChannel message
     *
     * @param s   the String to parse
     * @return    a Delete Channel message
     */
    public static SOCDeleteChannel parseDataStr(String s)
    {
        return new SOCDeleteChannel(s);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCDeleteChannel:channel=" + channel;
    }
}
