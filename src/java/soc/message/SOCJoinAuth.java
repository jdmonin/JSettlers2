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

import java.util.StringTokenizer;


/**
 * This message means that the server has authorized
 * this client to join a channel
 *
 * @author Robert S Thomas
 */
@SuppressWarnings("serial")
public class SOCJoinAuth extends SOCMessage
{
    /**
     * Nickname of the joining member
     */
    private String nickname;

    /**
     * Name of channel
     */
    private String channel;

    /**
     * Create a JoinAuth message.
     *
     * @param nn  nickname
     * @param ch  name of chat channel
     */
    public SOCJoinAuth(String nn, String ch)
    {
        messageType = JOINAUTH;
        nickname = nn;
        channel = ch;
    }

    /**
     * @return the nickname
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * @return the channel name
     */
    public String getChannel()
    {
        return channel;
    }

    /**
     * JOINAUTH sep nickname sep2 channel
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(nickname, channel);
    }

    /**
     * JOINAUTH sep nickname sep2 channel
     *
     * @param nn  the neckname
     * @param ch  the channel name
     * @return    the command string
     */
    public static String toCmd(String nn, String ch)
    {
        return JOINAUTH + sep + nn + sep2 + ch;
    }

    /**
     * Parse the command String into a Join message
     *
     * @param s   the String to parse
     * @return    a Join message, or null of the data is garbled
     */
    public static SOCJoinAuth parseDataStr(String s)
    {
        String nn;
        String ch;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            ch = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCJoinAuth(nn, ch);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCJoinAuth:nickname=" + nickname + "|channel=" + channel;

        return s;
    }
}
