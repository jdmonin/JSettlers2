/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2014,2016-2017,2019-2020 Jeremy D Monin <jeremy@nand.net>
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
     * Our token separator; to avoid collision with any possible text from user, not the normal {@link SOCMessage#sep2}.
     * Same separator as in {@link SOCGameTextMsg}.
     *<P>
     * Before v2.4.50 this field was named {@code sep2}.
     */
    private static String sep2_alt = "" + (char) 0;

    /**
     * Name of channel
     */
    private String channel;

    /**
     * Nickname of sender from server, or {@link SOCGameTextMsg#SERVER_FOR_CHAT}; see {@link #getNickname()}.
     */
    private String nickname;

    /**
     * Text message contents.
     * For expected format when {@link #nickname} is {@link SOCGameTextMsg#SERVER_FOR_CHAT},
     * see that nickname constant's javadoc.
     */
    private String text;

    /**
     * Create a ChannelTextMsg message.
     *
     * @param ch  name of chat channel
     * @param nn  nickname of sender;
     *     announcements from the server (not from a player) use {@link SOCGameTextMsg#SERVER_FOR_CHAT}.
     *     Ignored from client by server 1.2.01 and newer, can send "-" but not blank.
     * @param tm  message text, which may contain {@link SOCMessage#sep2} but not {@link SOCMessage#sep}.
     *     For expected format when {@code nn} is {@link SOCGameTextMsg#SERVER_FOR_CHAT},
     *     see that constant's javadoc.
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
     * Get the sender's nickname when message is from server; field ignored from client.
     * @return the nickname, or {@link SOCGameTextMsg#SERVER_FOR_CHAT} ({@code ":"})
     *     for server messages which should appear in the chat area (recap, etc).
     *     Ignored from client by server 1.2.01 and newer, can send "-" but not blank.
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * @return the text message.
     *    For expected format when {@link #getNickname()} is {@link SOCGameTextMsg#SERVER_FOR_CHAT},
     *    see that constant's javadoc.
     */
    public String getText()
    {
        return text;
    }

    /**
     * CHANNELTEXTMSG sep channel sep2_alt nickname sep2 text
     *
     * @return the command String
     */
    public String toCmd()
    {
        return CHANNELTEXTMSG + sep + channel + sep2_alt + nickname + sep2_alt + text;
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

        StringTokenizer st = new StringTokenizer(s, sep2_alt);

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

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a list formatted for {@link SOCMessage#parseMsgStr(String)}.
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.4.50
     */
    public static String stripAttribNames(String messageStrParams)
    {
        return SOCGameTextMsg.stripAttribNamesToTextMsg("channel=", messageStrParams);
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
