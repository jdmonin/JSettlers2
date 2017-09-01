/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2013-2014,2017 Jeremy D Monin <jeremy@nand.net>
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


/**
 * From client, this message means that a player wants to start the game;
 * from server, it means that a game has just started, leaving state {@code NEW}.
 * The server sends the game's new {@link SOCGameState} before sending {@code SOCStartGame}.
 *<P>
 * If a client joins a game in progress, it won't be sent a {@code SOCStartGame} message,
 * only the game's current {@code SOCGameState} and other parts of the game's and
 * players' current status.
 *
 * @author Robert S. Thomas
 */
public class SOCStartGame extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * Create a StartGame message.
     *
     * @param ga  the name of the game
     */
    public SOCStartGame(String ga)
    {
        messageType = STARTGAME;
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
     * STARTGAME sep game
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game);
    }

    /**
     * STARTGAME sep game
     *
     * @param ga  the name of the game
     * @return the command string
     */
    public static String toCmd(String ga)
    {
        return STARTGAME + sep + ga;
    }

    /**
     * Parse the command String into a StartGame message
     *
     * @param s   the String to parse
     * @return    a StartGame message, or null if the data is garbled
     */
    public static SOCStartGame parseDataStr(String s)
    {
        return new SOCStartGame(s);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCStartGame:game=" + game;
    }
}
