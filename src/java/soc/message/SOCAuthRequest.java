/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2014 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
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
 * Authentication request, to connect and check password without creating or joining a game or channel.
 *<P>
 * Before v1.1.19, the client needed to request joining or creating a game or channel in order to send
 * a username and password.  Games have a game option dialog before creation; if the password was wrong
 * those older versions wouldn't reject the client until after they had filled out that dialog, which is
 * less than ideal.
 *<P>
 * This message also has the password as its last field, to avoid imposing character restrictions
 * from field delimiters.
 *<P>
 * The server responds to this message from the client with a {@link SOCStatusMessage}:
 *<UL>
 * <LI> {@link SOCStatusMessage#SV_OK} if the username and password were authenticated
 * <LI> {@link SOCStatusMessage#SV_NOT_OK_GENERIC} if the auth scheme number is unknown
 *      or if the client hasn't already sent {@link SOCVersion} to the server
 * <LI> {@link SOCStatusMessage#SV_PW_WRONG} if the username and password are not a valid combination:
 *      The password is wrong, or if the nickname is unknown at the server.
 *      The server never replies to this message with {@link SOCStatusMessage#SV_NAME_NOT_FOUND}.
 *</UL>
 *<P>
 * Bots don't need or use this message, they authenticate to the server with {@link SOCImARobot}.
 *<P>
 * This message includes an {@link #authScheme} number field for future expansion.
 * The only currently implemented auth scheme number is:
 *<OL>
 * <LI> {@link #SCHEME_CLIENT_PLAINTEXT}
 *</OL>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.19
 * @see SOCNewGameWithOptionsRequest
 */
public class SOCAuthRequest extends SOCMessage
{
    private static final long serialVersionUID = 1119L;

    /** Minimum version (1.1.19) of client/server which send and recognize AUTHREQUEST */
    public static final int VERSION_FOR_AUTHREQUEST = 1119;

    /** Scheme #1, for client to connect using a plaintext password */
    public static final int SCHEME_CLIENT_PLAINTEXT = 1;

    /** Token to indicate host is <tt>null</tt> or "", to avoid adjacent delimiters */
    private final static String NULLHOST = "\t";

    /** Nickname (username) of the joining client */
    public final String nickname;

    /**Optional password, or "" */
    public final String password;

    /**
     * Authentication scheme number, such as {@link #SCHEME_CLIENT_PLAINTEXT}.
     */
    public final int authScheme;

    /**
     * Server host name to which the client is connecting, or null for client's local TCP server.
     * Since the client is already connected when it sends the message, this is only informational.
     * The empty string "" is stored as null here.
     */
    public final String host;

    /**
     * Create an AuthRequest message.
     *
     * @param nn  nickname
     * @param pw  optional password, or ""; this is the last field of the message
     *     so that it can contain delimiter chars
     * @param sch  auth scheme number, such as {@link #SCHEME_CLIENT_PLAINTEXT}
     * @param hn  server host name, or null; "" is stored as null in the {@link #host} field
     * @throws IllegalArgumentException if <tt>nn</tt> or <tt>hn</tt> contains a delimiter character
     *     or otherwise doesn't pass {@link SOCMessage#isSingleLineAndSafe(String)}
     */
    public SOCAuthRequest(final String nn, final String pw, final int sch, final String hn)
        throws IllegalArgumentException
    {
        if (! SOCMessage.isSingleLineAndSafe(nn))
            throw new IllegalArgumentException("nickname: " + nn);
        if ((hn != null) && ! SOCMessage.isSingleLineAndSafe(hn))
            throw new IllegalArgumentException("hostname: " + hn);

        messageType = AUTHREQUEST;
        nickname = nn;
        authScheme = sch;
        host = ((hn != null) && (hn.length() > 0)) ? hn : null;
        password = pw;
    }

    /**
     * AUTHREQUEST sep nickname sep2 authScheme sep2 host sep2 password
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(nickname, password, authScheme, host);
    }

    /**
     * AUTHREQUEST sep nickname sep2 authScheme sep2 host sep2 password
     *
     * @param nn  the nickname
     * @param pw  the optional password, or ""; this is the last field of the message
     *     so that it can contain delimiter chars
     * @param sch  auth scheme number, such as {@link #SCHEME_CLIENT_PLAINTEXT}
     * @param hn  the server host name, or null
     * @return    the command string
     * @throws IllegalArgumentException if <tt>nn</tt> or <tt>hn</tt> contains a delimiter character
     *     or otherwise doesn't pass {@link SOCMessage#isSingleLineAndSafe(String)}
     */
    public static String toCmd(final String nn, String pw, final int sch, final String hn)
        throws IllegalArgumentException
    {
        if (! SOCMessage.isSingleLineAndSafe(nn))
            throw new IllegalArgumentException("nickname: " + nn);
        if ((hn != null) && ! SOCMessage.isSingleLineAndSafe(hn))
            throw new IllegalArgumentException("hostname: " + hn);

        return AUTHREQUEST + sep + nn + sep2 + sch + sep2 + ((hn != null) ? hn : NULLHOST) + sep2 + pw;
    }

    /**
     * Parse the command string into an AuthRequest message.
     *<P>
     * The constructor called here checks received field contents against
     * {@link SOCMessage#isSingleLineAndSafe(String)} to sanitize; if that
     * fails, null is returned.
     *
     * @param s   the string to parse, as output from {@link #toCmd()}
     * @return    an AuthRequest message, or null of the data is garbled
     */
    public static SOCAuthRequest parseDataStr(String s)
    {
        String nn;
        int sch;
        String hn;
        String pw;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            sch = Integer.parseInt(st.nextToken());
            hn = st.nextToken();
            if (hn.equals(NULLHOST))
                hn = null;

            // get all of the rest for password, by choosing an unlikely delimiter character
            pw = st.nextToken(Character.toString( (char) 1 )).trim();
            if (pw.startsWith(SOCMessage.sep2))
                pw = pw.substring(1);  // sep2 was returned at start of pw, since it isn't delimiter anymore

            return new SOCAuthRequest(nn, pw, sch, hn);  // may throw IllegalArgumentException
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Minimum version where this message type is used.
     * AUTHREQUEST was introduced in 1.1.19.
     * @return Version number, 1119 for JSettlers 1.1.19.
     */
    public final int getMinimumVersion() { return VERSION_FOR_AUTHREQUEST; }

    /**
     * @return a human readable form of the message; indicates whether password is empty or set, but obscures its value
     */
    public final String toString()
    {
        return "SOCAuthRequest:nickname=" + nickname + "|scheme=" + authScheme + "|host=" + host
            + (((password != null) && (password.length() > 0)) ? "|password=***" : "|password empty");
    }

}
