/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017 Jeremy D Monin <jeremy@nand.net>
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

import soc.proto.Message;


/**
 * This message means that a soc game has been destroyed.
 *
 * @author Robert S Thomas
 */
public class SOCDeleteGame extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of the game.
     */
    private String game;

    /**
     * Create a DeleteGame message.
     *
     * @param ga  name of the game
     */
    public SOCDeleteGame(String ga)
    {
        messageType = DELETEGAME;
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
     * DELETEGAME sep game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game);
    }

    /**
     * DELETEGAME sep game
     *
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(String ga)
    {
        return DELETEGAME + sep + ga;
    }

    /**
     * Parse the command String into a DeleteGame message
     *
     * @param s   the String to parse
     * @return    a Delete Game message
     */
    public static SOCDeleteGame parseDataStr(String s)
    {
        return new SOCDeleteGame(s);
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        Message.DeleteGame.Builder b = Message.DeleteGame.newBuilder()
            .setGaName(game);
        return Message.FromServer.newBuilder()
            .setGaDelete(b).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCDeleteGame:game=" + game;
    }
}
