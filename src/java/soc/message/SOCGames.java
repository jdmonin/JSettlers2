/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2010,2014 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;

import soc.game.SOCGame;


/**
 * This message lists all the soc games currently on a server,
 * without {@link soc.game.SOCGameOption game options}.
 * It's constructed and sent for each connecting client
 * which can't understand game options (older than 1.1.07).
 *<P>
 * Version 1.1.06 and later:
 * Any game's name within the list may start with the "unjoinable"
 * marker prefix {@link #MARKER_THIS_GAME_UNJOINABLE}.
 *
 * @author Robert S Thomas
 * @see SOCGamesWithOptions
 */
public class SOCGames extends SOCMessage
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * If this is the first character of a game name,
     * the client is too limited to be able to play that game,
     * due to properties of the game (large board, expansion rules, etc.)
     * which may require a newer client.
     *<P>
     * This marker may be used in other message types which may introduce a new game,
     * such as {@link SOCNewGame} and {@link SOCNewGameWithOptions}.
     * Besides those, this marker is not used in any other message types, such as {@link SOCDeleteGame}.
     * The game name appears 'un-marked' in those other types.
     *
     * @since 1.1.06
     */
    public static final char MARKER_THIS_GAME_UNJOINABLE = '\077';  // 0x7F

    /**
     * Minimum version (1.1.06) of client/server which recognize
     * and send {@link #MARKER_THIS_GAME_UNJOINABLE}.
     *
     * @since 1.1.06
     */
    public static final int VERSION_FOR_UNJOINABLE = 1106;


    /**
     * List of games (Strings)
     */
    private Vector<String> games;

    /**
     * Create a Games Message.
     *
     * @param ga  list of game names (Strings).
     *         Mark unjoinable games with the prefix
     *         {@link #MARKER_THIS_GAME_UNJOINABLE}.
     */
    public SOCGames(Vector<String> ga)
    {
        messageType = GAMES;
        games = ga;
    }

    /**
     * @return the list of games, a vector of Strings
     */
    public Vector<String> getGames()
    {
        return games;
    }

    /**
     * GAMES sep games
     *
     * @return the command string
     */
    @Override
    public String toCmd()
    {
        return toCmd(games);
    }

    /**
     * GAMES sep games
     *
     * @param ga  the list of games, as a mixed-content vector of Strings and/or {@link SOCGame}s;
     *            if a client can't join a game, it should be a String prefixed with
     *            {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @return    the command string
     */
    public static String toCmd(Vector<?> ga)
    {
        String cmd = GAMES + sep;

        try
        {
            Enumeration<?> gaEnum = ga.elements();
            Object ob = gaEnum.nextElement();
            if (ob instanceof SOCGame)
                cmd += ((SOCGame) ob).getName();
            else
                cmd += (String) ob;

            while (gaEnum.hasMoreElements())
            {
                ob = gaEnum.nextElement();
                if (ob instanceof SOCGame)
                    cmd += sep2 + ((SOCGame) ob).getName();
                else
                    cmd += sep2 + (String) ob;
            }
        }
        catch (Exception e) {}

        return cmd;
    }

    /**
     * Parse the command String into a Games message
     *
     * @param s   the String to parse
     * @return    a Games message, or null of the data is garbled
     */
    public static SOCGames parseDataStr(String s)
    {
        Vector<String> ga = new Vector<String>();
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            while (st.hasMoreTokens())
            {
                ga.addElement(st.nextToken());
            }
        }
        catch (Exception e)
        {
            System.err.println("SOCGames parseDataStr ERROR - " + e);

            return null;
        }

        return new SOCGames(ga);
    }

    /**
     * @return a human readable form of the message
     */
    @Override
    public String toString()
    {
        StringBuffer sb = new StringBuffer("SOCGames:games=");
        if (games != null)
            enumIntoStringBuf(games.elements(), sb);
        return sb.toString();
    }
}
