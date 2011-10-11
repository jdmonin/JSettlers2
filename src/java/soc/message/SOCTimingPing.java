/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2011 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003-2004  Robert S. Thomas
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
 **/
package soc.message;

import java.util.StringTokenizer;


/**
 * This message contains a timing ping sent by the server to
 * each robot, once each second. Used by SOCRobotBrain for timing.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @see SOCGameTextMsg
 * @since 1.1.13
 */
public class SOCTimingPing extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * Create a SOCTimingPing message.
     *
     * @param ga  name of game
     */
    public SOCTimingPing(String ga)
    {
        messageType = TIMINGPING;
        game = ga;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * TIMINGPING sep game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game);
    }

    /**
     * TIMINGPING sep game
     *
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(String ga)
    {
        return TIMINGPING + sep + ga;
    }

    /**
     * Parse the command String into a GameTextMsg message
     *
     * @param s   the String to parse; should contain only the game name.
     * @return    a GameTextMsg message, or null of the data is garbled
     */
    public static SOCTimingPing parseDataStr(String s)
    {
        return new SOCTimingPing(s);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCTimingPing:game=" + game ;

        return s;
    }
}
