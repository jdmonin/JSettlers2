/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010 Jeremy D Monin <jeremy@nand.net>
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.message;

import java.util.StringTokenizer;


/**
 * This message tells the server that the client is a robot.
 *<P>
 * The server is distributed with the original robot AI, and this
 * permits optimized communications and server simplifications.
 * So, the server requires that robot clients are the same version as the server.
 *<P>
 * In 1.1.09 and later, 3rd-party robots can connect and be treated as robots.
 * (For example, games where all humans leave, but robots remain, are stopped.)
 * The 3rd-party robots can report any version, just like human player clients.
 *<P>
 * The IMAROBOT message includes a {@link #getRBClass()} parameter to indicate
 * whether the robot is 3rd-party or is {@link #RBCLASS_BUILTIN the original built-in AI}.
 *
 * @author Robert S Thomas
 */
public class SOCImARobot extends SOCMessage
{
    /**
     * Version 1.1.09: add rbclass. This is 1st change since the original class.
     * @since 1.1.09
     */
    private static final long serialVersionUID = 1109L;

    /**
     * Name of built-in robot brain class.
     * This robot is the original robot, distributed with the JSettlers server,
     * which permits some optimized communications.
     * Other (3rd-party) robots must use a different class in their IMAROBOT messages.
     * See the class javadoc for more details.
     * @since 1.1.09
     */
    public static final String RBCLASS_BUILTIN = "soc.robot.SOCRobotBrain";

    /**
     * Nickname of the robot
     */
    private String nickname;

    /**
     * The robot's brain class, to show 3rd-party robots.
     * The built-in robot is {@link #RBCLASS_BUILTIN}.
     * @since 1.1.09 
     */
    private String rbclass;

    /**
     * Create a ImARobot message.
     *
     * @param nn  nickname
     * @param rbclass robot brain class, such as {@link #RBCLASS_BUILTIN}.
     *     Other (3rd-party) robots must use a different rbclass in their IMAROBOT messages.
     * @since 1.1.09
     */
    public SOCImARobot(String nn, String rbclass)
    {
        messageType = IMAROBOT;
        nickname = nn;
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
        return toCmd(nickname, rbclass);
    }

    /**
     * IMAROBOT sep nickname sep2 rbclass
     *
     * @param nn  the nickname
     * @param rbclass the robot class
     * @return    the command string
     */
    public static String toCmd(String nn, String rbclass)
    {
        if (rbclass == null)
            return IMAROBOT + sep + nn;  // back-compat only (pre-1.1.09)
        else
            return IMAROBOT + sep + nn + sep2 + rbclass;
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
        String rbc = null;  // robot class: 1.1.09 or newer

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            if (st.hasMoreTokens())
                rbc = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCImARobot(nn, rbc);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCImARobot:nickname=" + nickname + "|rbclass=" + rbclass;

        return s;
    }
}
