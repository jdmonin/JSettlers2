/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2010,2014,2016-2017,2020 Jeremy D Monin <jeremy@nand.net>
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

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import soc.game.SOCGame;


/**
 * This backwards-compatibility message lists the names of all the games currently
 * created on a server, without their {@link soc.game.SOCGameOption game options}.
 * It's constructed and sent to each connecting client
 * having an old version which doesn't support game options.
 *<P>
 * Version 1.1.07 and later clients are sent {@link SOCGamesWithOptions}
 * instead of this message type. (Check {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS})
 *<P>
 * Version 1.1.06 and later:
 * Any game's name within the list may start with the "unjoinable"
 * marker prefix {@link #MARKER_THIS_GAME_UNJOINABLE}.
 *<P>
 * Servers older than v1.1.07 always sent this message;
 * {@link SOCGamesWithOptions} was not yet defined.
 *
 * @author Robert S Thomas
 * @see SOCGamesWithOptions
 * @see SOCGameMembers
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
    private List<String> games;

    /**
     * Create a Games message at server.
     *
     * @param ga  the game names, as a mixed-content list of Strings and/or {@link SOCGame}s;
     *     if a client can't join a game, it should be a String prefixed with
     *     {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}. Any {@link SOCGame#getGameOptions()} will be ignored.
     * @since 2.4.50
     */
    public SOCGames(final List<?> ga)
    {
        this(new ArrayList<String>(), false);

        for (Object ob : ga)
            if (ob instanceof SOCGame)
                games.add(((SOCGame) ob).getName());
            else
                games.add(ob.toString());  // ob's almost certainly a String already
    }

    /**
     * Create a Games message at client.
     *
     * @param ga  list of game names (Strings).
     *         Mark unjoinable games with the prefix
     *         {@link #MARKER_THIS_GAME_UNJOINABLE}.
     * @param clientMarker  Parameter is here only to differentiate the public server-side (List&lt;Object>) constructor
     *     from this private/client-side (List&lt;String>) one
     */
    private SOCGames(List<String> ga, final boolean clientMarker)
    {
        messageType = GAMES;
        games = ga;
    }

    /**
     * @return the list of game names
     */
    public List<String> getGames()
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
        StringBuilder cmd = new StringBuilder();
        cmd.append(GAMES);
        cmd.append(sep);

        boolean first = true;
        for (Object ob : games)
        {
            if (! first)
                cmd.append(sep2);
            else
                first = false;

            if (ob instanceof SOCGame)
                cmd.append(((SOCGame) ob).getName());
            else
                cmd.append(ob.toString());  // ob's almost certainly a String already
        }

        return cmd.toString();
    }

    /**
     * Parse the command String into a Games message
     *
     * @param s   the String to parse
     * @return    a Games message, or null if the data is garbled
     */
    public static SOCGames parseDataStr(String s)
    {
        ArrayList<String> ga = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            while (st.hasMoreTokens())
            {
                ga.add(st.nextToken());
            }
        }
        catch (Exception e)
        {
            System.err.println("SOCGames parseDataStr ERROR - " + e);

            return null;
        }

        return new SOCGames(ga, true);
    }

    /**
     * @return a human readable form of the message
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SOCGames:games=");
        if (games != null)
            sb.append(games);  // "[game1, game2, ...]"

        return sb.toString();
    }

}
