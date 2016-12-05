/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2014,2016 Jeremy D Monin <jeremy@nand.net>
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

import java.util.StringTokenizer;


/**
 * From a client, this message tells the server the client is leaving a chat channel.
 * From server, it announces to all members of a channel that someone has left it.
<P>
 * Before v2.0.00 this class was named {@code SOCLeave}.
 *
 * @author Robert S Thomas
 * @see SOCLeaveAll
 * @see SOCLeaveGame
 */
public class SOCLeaveChannel extends SOCMessage
{
    private static final long serialVersionUID = 2000L;  // renamed in v2.0.00; previous structural change v1.1.11

    /**
     * Nickname of the leaving member
     */
    private String nickname;

    /**
     * Name of channel
     */
    private String channel;

    /**
     * Host name
     */
    private String host;

    /**
     * Create a Leave message.
     *
     * @param nn  nickname
     * @param hn  host name
     * @param ch  name of chat channel
     */
    public SOCLeaveChannel(String nn, String hn, String ch)
    {
        messageType = LEAVECHANNEL;
        nickname = nn;
        channel = ch;
        host = hn;
    }

    /**
     * @return the nickname
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * @return the host name
     */
    public String getHost()
    {
        return host;
    }

    /**
     * @return the channel name
     */
    public String getChannel()
    {
        return channel;
    }

    /**
     * {@code LEAVECHANNEL} sep <em>nickname</em> sep2 <em>host</em> sep2 <em>channel</em>
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(nickname, host, channel);
    }

    /**
     * {@code LEAVECHANNEL} sep <em>nickname</em> sep2 <em>host</em> sep2 <em>channel</em>
     *
     * @param nn  the neckname
     * @param hn  the host name
     * @param ch  the new channel name
     * @return    the command string
     */
    public static String toCmd(String nn, String hn, String ch)
    {
        return LEAVECHANNEL + sep + nn + sep2 + hn + sep2 + ch;
    }

    /**
     * Parse the command String into a Leave Channel message.
     *
     * @param s   the String to parse
     * @return    a Leave message, or null of the data is garbled
     */
    public static SOCLeaveChannel parseDataStr(String s)
    {
        String nn;
        String hn;
        String ch;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            hn = st.nextToken();
            ch = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCLeaveChannel(nn, hn, ch);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCLeaveChannel:nickname=" + nickname + "|host=" + host + "|channel=" + channel;

        return s;
    }
}
