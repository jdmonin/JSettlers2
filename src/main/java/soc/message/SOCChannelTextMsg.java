/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2014,2016-2017 Jeremy D Monin <jeremy@nand.net>
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

import soc.proto.Message;


/**
 * This message contains a text message sent to a chat channel.
 *<P>
 * Text messages from clients in games use {@link SOCGameTextMsg} instead.
 *<P>
 * Before v2.0.00 this message class was {@code SOCTextMsg}.
 *
 * @author Robert S Thomas
 */
public class SOCChannelTextMsg extends SOCMessage
{
    private static final long serialVersionUID = 100L;  // last structural change v1.0.0 or earlier

    /**
     * our token seperator
     */
    private static String sep2 = "" + (char) 0;

    /**
     * Name of channel
     */
    private String channel;

    /**
     * Nickname of sender
     */
    private String nickname;

    /**
     * Text message contents
     */
    private String text;

    /**
     * Create a ChannelTextMsg message.
     *
     * @param ch  name of chat channel
     * @param nn  nickname of sender
     * @param tm  text message
     */
    public SOCChannelTextMsg(String ch, String nn, String tm)
    {
        messageType = CHANNELTEXTMSG;
        channel = ch;
        nickname = nn;
        text = tm;
    }

    /**
     * @return the channel name
     */
    public String getChannel()
    {
        return channel;
    }

    /**
     * @return the nickname
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * @return the text message
     */
    public String getText()
    {
        return text;
    }

    /**
     * CHANNELTEXTMSG sep channel sep2 nickname sep2 text
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(channel, nickname, text);
    }

    /**
     * CHANNELTEXTMSG sep channel sep2 nickname sep2 text
     *
     * @param ch  the channel name
     * @param nn  the nickname
     * @param tm  the text message
     * @return    the command string
     */
    public static String toCmd(String ch, String nn, String tm)
    {
        return CHANNELTEXTMSG + sep + ch + sep2 + nn + sep2 + tm;
    }

    /**
     * Parse the command String into a ChannelTextMsg message
     *
     * @param s   the String to parse
     * @return    a ChannelTextMsg message, or null if the data is garbled
     */
    public static SOCChannelTextMsg parseDataStr(String s)
    {
        String ch;
        String nn;
        String tm;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ch = st.nextToken();
            nn = st.nextToken();
            tm = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCChannelTextMsg(ch, nn, tm);
    }

    /** SOCJoinChannel maps to the same protobuf message as {@link SOCJoinChannelAuth}. */
    @Override
    protected Message.FromServer toProtoFromServer()
    {
        Message.ChannelText.Builder b = Message.ChannelText.newBuilder()
            .setChName(channel)
            .setMemberName(nickname)
            .setText(text);
        return Message.FromServer.newBuilder()
            .setChText(b).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCChannelTextMsg:channel=" + channel + "|nickname=" + nickname + "|text=" + text;

        return s;
    }
}
