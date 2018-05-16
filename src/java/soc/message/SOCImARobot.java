/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2013-2014,2018 Jeremy D Monin <jeremy@nand.net>
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
 * This message tells the server that the client is a robot.
 * If server accepts this bot, it responds with {@link SOCUpdateRobotParams}.
 *<P>
 * The server is distributed together with the built-in {@code soc.robot} AI,
 * which enables optimized communications and server simplifications.
 * So, the server requires that robot clients are the same version as the server.
 *<P>
 * In 1.1.09 and later, 3rd-party robots can connect and are treated like built-in bots.
 * (For example, games where all humans leave, but robots remain, are stopped.)
 * The 3rd-party robots can report any compatible version, just like human player clients.
 *<P>
 * The IMAROBOT message includes a {@link #getRBClass()} parameter to indicate
 * whether the robot is 3rd-party or is the original built-in AI ({@link #RBCLASS_BUILTIN}).
 *<P>
 * In 1.1.19 and later, the IMAROBOT message includes a shared secret cookie
 * to authenticate the bot to the server.
 *
 * @author Robert S Thomas
 */
public class SOCImARobot extends SOCMessage
{
    /**
     * Version 1.1.09: add rbclass. This is the first change since the original class.<P>
     * Version 1.1.19: add cookie.<P>
     * @since 1.1.09
     */
    private static final long serialVersionUID = 1119L;

    /**
     * Name of built-in robot brain class: {@code "soc.robot.SOCRobotBrain"}.
     * This robot is the original robot, distributed with the JSettlers server,
     * which permits some optimized communications.
     * Other (3rd-party) robots must use a different class in their IMAROBOT messages.
     * See the {@link SOCImARobot class javadoc} for more details.
     * @since 1.1.09
     */
    public static final String RBCLASS_BUILTIN = "soc.robot.SOCRobotBrain";

    /**
     * Nickname of the robot
     */
    private String nickname;

    /**
     * The security cookie value expected by the server.
     * It isn't sent encrypted and is a weak "shared secret".
     * @since 1.1.19
     */
    private final String cookie;

    /**
     * The robot's brain class, to show 3rd-party robots.
     * The built-in robot is {@link #RBCLASS_BUILTIN}.
     * If {@link #cookie} != null, then <tt>rbclass</tt> != null.
     * @since 1.1.09 
     */
    private String rbclass;

    /**
     * Create a ImARobot message.
     *
     * @param nn  nickname
     * @param cookie  security cookie to send to the server for this connection;
     *     required by server v1.1.19 and higher, or <tt>null</tt>.
     *     Must pass {@link SOCMessage#isSingleLineAndSafe(String)} unless <tt>null</tt>.
     * @param rbclass robot brain class, such as {@link #RBCLASS_BUILTIN}.
     *     Other (3rd-party) robots must use a different rbclass in their IMAROBOT messages.
     * @since 1.1.09
     * @throws IllegalArgumentException if <tt>cookie</tt> is non-null, and
     *     cookie fails {@link SOCMessage#isSingleLineAndSafe(String)} or
     *     <tt>rbclass</tt> is null.
     */
    public SOCImARobot(final String nn, final String cookie, final String rbclass)
        throws IllegalArgumentException
    {
        if (cookie != null)
        {
            if (! SOCMessage.isSingleLineAndSafe(cookie))
                throw new IllegalArgumentException("cookie");
            else if (rbclass == null)
                throw new IllegalArgumentException("null rbclass");
        }

        messageType = IMAROBOT;
        nickname = nn;
        this.cookie = cookie;
        this.rbclass = rbclass;
    }

    /**
     * @return the nickname
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * Get the security cookie to send to the server for this connection.
     * It isn't sent encrypted and is a weak "shared secret".
     * Required by server v1.1.19 and higher.
     * @return the cookie
     * @since 1.1.19
     */
    public String getCookie()
    {
        return cookie;
    }

    /**
     * @return the robot brain class, or null for pre-1.1.09 built-in robots
     * @since 1.1.09
     */
    public String getRBClass()
    {
        return rbclass;
    }

    /**
     * IMAROBOT sep nickname
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(nickname, cookie, rbclass);
    }

    /**
     * IMAROBOT sep nickname sep2 cookie sep2 rbclass
     *
     * @param nn  the nickname
     * @param cookie  the security cookie
     * @param rbclass the robot class
     * @return    the command string
     */
    public static String toCmd(final String nn, final String cookie, final String rbclass)
    {
        if (cookie != null)
            return IMAROBOT + sep + nn + sep2 + cookie + sep2 + rbclass;
        if (rbclass == null)
            return IMAROBOT + sep + nn;  // back-compat only (pre-1.1.09: no rbclass)
        else
            return IMAROBOT + sep + nn + sep2 + rbclass;  // back-compat (pre-1.1.19: no cookie)
    }

    /**
     * Parse the command String into a ImARobot message
     *
     * @param s   the String to parse
     * @return    a ImARobot message, or null of the data is garbled
     */
    public static SOCImARobot parseDataStr(String s)
    {
        String nn;  // robot name
        String cook = null;  // security cookie: 1.1.19 or newer
        String rbc = null;  // robot class: 1.1.09 or newer

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            if (st.hasMoreTokens())
            {
                cook = st.nextToken();
                if (st.hasMoreTokens())
                {
                    rbc = st.nextToken();
                } else {
                    // message has name and rbc only
                    rbc = cook;
                    cook = null;
                }
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCImARobot(nn, cook, rbc);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        final String s = (cookie != null)
            ? ("SOCImARobot:nickname=" + nickname + "|cookie=**|rbclass=" + rbclass)
            : ("SOCImARobot:nickname=" + nickname + "|cookie=null|rbclass=" + rbclass);

        return s;
    }
}
