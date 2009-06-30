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
 * This message contains a text message
 *
 * @author Robert S Thomas
 */
public class SOCTextMsg extends SOCMessage
{
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
     * Text message
     */
    private String text;

    /**
     * Create a TextMsg message.
     *
     * @param ch  name of chat channel
     * @param nn  nickname of sender
     * @param tm  text message
     */
    public SOCTextMsg(String ch, String nn, String tm)
    {
        messageType = TEXTMSG;
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
     * TEXTMSG sep channel sep2 nickname sep2 text
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(channel, nickname, text);
    }

    /**
     * TEXTMSG sep channel sep2 nickname sep2 text
     *
     * @param ch  the channel name
     * @param nn  the nickname
     * @param tm  the text message
     * @return    the command string
     */
    public static String toCmd(String ch, String nn, String tm)
    {
        return TEXTMSG + sep + ch + sep2 + nn + sep2 + tm;
    }

    /**
     * Parse the command String into a TextMsg message
     *
     * @param s   the String to parse
     * @return    a TextMsg message, or null of the data is garbled
     */
    public static SOCTextMsg parseDataStr(String s)
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

        return new SOCTextMsg(ch, nn, tm);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCTextMsg:channel=" + channel + "|nickname=" + nickname + "|text=" + text;

        return s;
    }
}
