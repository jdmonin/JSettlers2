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
 * From a client, this message is a request to join or create a chat channel.
 * If successful, server will send {@link SOCJoinChannelAuth} to requesting client
 * and {@link SOCJoinChannel} to all members of the channel.
 *<P>
 * Once a client has successfully joined or created any channel or game, the
 * nickname and password fields can be left blank in later join/create requests.
 * All server versions ignore the password field after a successful request.
 *<P>
 * Before v2.0.00 this class was named {@code SOCJoin}.
 *
 * @author Robert S Thomas
 * @see SOCJoinGame
 */
public class SOCJoinChannel extends SOCMessage
    implements SOCMessageFromUnauthClient
{
    private static final long serialVersionUID = 2000L;  // renamed in v2.0.00; previous structural change v1.0.0 or earlier

    /**
     * Nickname of the joining member, or "-" from client; see {@link #getNickname()}.
     */
    private String nickname;

    /**
     * Optional password, or "" if none
     */
    private String password;

    /**
     * Name of channel
     */
    private String channel;

    /**
     * Unused; server host name to which the client is connected; see {@link #getHost()}
     */
    private String host;

    /**
     * Create a Join Channel message.
     *
     * @param nn  nickname when announced from server, or "-" from client if already auth'd to server;
     *     ignored from client by server 1.2.01 and newer, can send "-" but not blank
     * @param pw  optional password, or "" if none
     * @param hn  unused; optional server host name, or "-" or {@link SOCMessage#EMPTYSTR}
     * @param ch  name of chat channel
     */
    public SOCJoinChannel(String nn, String pw, String hn, String ch)
    {
        messageType = JOINCHANNEL;
        nickname = nn;
        password = pw;
        channel = ch;
        host = hn;
    }

    /**
     * Nickname of the joining member, or "-" from client if already auth'd to server.
     * ignored from client by server 1.2.01 and newer, can send "-" but not blank.
     * @return the nickname, or "-" if already auth'd to server
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * @return the optional password, or "" if none
     */
    public String getPassword()
    {
        return password;
    }

    /**
     * Get the optional server host name to which client is connected; unused, ignored and not used by any server version.
     * Since the client is already connected when it sends the message, this is only informational.
     * Is always {@link SOCMessage#EMPTYSTR} when sent by v2.0.00 or newer server or client.
     * @return the unused optional server host name to which client is connected, or "-" or {@link SOCMessage#EMPTYSTR}
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
     * JOINCHANNEL sep nickname sep2 password sep2 host sep2 channel
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(nickname, password, host, channel);
    }

    /**
     * JOINCHANNEL sep nickname sep2 password sep2 host sep2 channel
     *
     * @param nn  nickname when announced from server, or "-" from client if already auth'd to server;
     *     ignored from client by server 1.2.01 and newer, can send "-" but not blank
     * @param pw  the optional password, or "" if none
     * @param hn  unused; optional server host name, or "-" or {@link SOCMessage#EMPTYSTR}
     * @param ch  the channel name
     * @return    the command string
     */
    public static String toCmd(String nn, String pw, String hn, String ch)
    {
        String temppw = pw;
        if (pw.length() == 0)
            temppw = EMPTYSTR;

        return JOINCHANNEL + sep + nn + sep2 + temppw + sep2 + hn + sep2 + ch;
    }

    /**
     * Parse the command String into a Join Channel message.
     *
     * @param s   the String to parse
     * @return    a Join Channel message, or null if the data is garbled
     */
    public static SOCJoinChannel parseDataStr(String s)
    {
        String nn;
        String pw;
        String hn;
        String ch;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            pw = st.nextToken();
            hn = st.nextToken();
            ch = st.nextToken();

            if (pw.equals(EMPTYSTR))
                pw = "";
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCJoinChannel(nn, pw, hn, ch);
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list
     * for {@link SOCMessage#parseMsgStr(String)}.
     * Converts "password empty" to {@link SOCMessage#EMPTYSTR}.
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.4.50
     */
    public static String stripAttribNames(String messageStrParams)
    {
        final int pwEmptyIdx = messageStrParams.indexOf("|password empty|host=");
        if (pwEmptyIdx > 0)
            messageStrParams =
                messageStrParams.substring(0, pwEmptyIdx + 1)
                + EMPTYSTR
                + messageStrParams.substring(pwEmptyIdx + 15);

        return SOCMessage.stripAttribNames(messageStrParams);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        final String pwmask;
        if ((password == null) || (password.length() == 0) || password.equals("\t"))
            pwmask = "|password empty";
        else
            pwmask = "|password=***";

        String s = "SOCJoinChannel:nickname=" + nickname + pwmask + "|host=" + host + "|channel=" + channel;
        return s;
    }

}
