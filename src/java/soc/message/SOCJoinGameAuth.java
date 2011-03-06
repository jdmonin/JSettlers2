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


/**
 * This message means that the player is allowed to join the game
 * this client to join a game
 *
 * @author Robert S Thomas
 */
public class SOCJoinGameAuth extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * Create a JoinGameAuth message.
     *
     * @param ga  name of game
     */
    public SOCJoinGameAuth(String ga)
    {
        messageType = JOINGAMEAUTH;
        game = ga;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * JOINGAMEAUTH sep game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game);
    }

    /**
     * JOINGAMEAUTH sep game
     *
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(String ga)
    {
        return JOINGAMEAUTH + sep + ga;
    }

    /**
     * Parse the command String into a JoinGameAuth message
     *
     * @param s   the String to parse
     * @return    a JoinGameAuth message, or null of the data is garbled
     */
    public static SOCJoinGameAuth parseDataStr(String s)
    {
        return new SOCJoinGameAuth(s);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCJoinGameAuth:game=" + game;

        return s;
    }
}
