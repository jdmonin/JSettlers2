/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2010,2014,2017-2018 Jeremy D Monin <jeremy@nand.net>
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
 * This message to all clients means that a new game has been created.
 * If the client is requesting the game, NEWGAME will be followed
 * by JOINGAMEAUTH.
 *<P>
 * Version 1.1.06 and later:
 * Game name may include a marker prefix if the client can't join;
 * see {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
 * This marker will be retained within the game name returned by
 * {@link #getGame()}.
 *<P>
 * Just like {@link SOCNewGameWithOptions}, robot clients don't need to handle
 * this message type. Bots ignore new-game announcements and are asked to
 * join specific games.
 *
 * @author Robert S Thomas
 */
public class SOCNewGame extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of the new game.
     */
    private String game;

    /**
     * Create a NewGame message.
     *
     * @param ga  the name of the game; may have
     *            the {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE} prefix.
     */
    public SOCNewGame(String ga)
    {
        messageType = NEWGAME;
        game = ga;
    }

    /**
     * @return the name of the game; may have
     *         the {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE} prefix.
     */
    public String getGame()
    {
        return game;
    }

    /**
     * NEWGAME sep game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game);
    }

    /**
     * NEWGAME sep game
     *
     * @param ga  the name of the new game; may have
     *            the {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE} prefix.
     * @return    the command string
     */
    public static String toCmd(String ga)
    {
        return NEWGAME + sep + ga;
    }

    /**
     * Parse the command String into a NewGame message
     *
     * @param s   the String to parse
     * @return    a NewGame message
     */
    public static SOCNewGame parseDataStr(String s)
    {
        return new SOCNewGame(s);
    }

    /** Same protobuf message type as {@link SOCNewGameWithOptions} and {@link SOCNewGameWithOptionsRequest}. */
    @Override
    protected Message.FromServer toProtoFromServer()
    {
        Message._GameWithOptions.Builder gb = Message._GameWithOptions.newBuilder()
            .setGaName(game)
            .setOpts("-");
        Message.NewGame.Builder b = Message.NewGame.newBuilder()
             .setGame(gb);
        return Message.FromServer.newBuilder()
            .setGaNew(b).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCNewGame:game=" + game;
    }
}
