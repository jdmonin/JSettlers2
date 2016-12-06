/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2009,2011,2013-2016 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Vector;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.util.SOCGameList;

/**
 * List of all games currently on the server, including
 * their {@link soc.game.SOCGameOption game options}.
 * It's constructed and sent for each connecting client
 * which can understand game options (1.1.07 and newer),
 * by calling {@link #toCmd(Vector, int)}.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't create games.
 *<P>
 * Introduced in 1.1.07; check client version against
 * {@link soc.message.SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.07
 * @see SOCGames
 * @see SOCGameMembers
 */
public class SOCGamesWithOptions extends SOCMessageTemplateMs
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Constructor for client to parse server's list of games.
     * This collects the paired games and options into a string list,
     * but doesn't parse the game option strings into {@link soc.game.SOCGameOption}
     * objects; call {@link soc.game.SOCGameOption#parseOptionsToMap(String)} for that.
     *<P>
     * There is no server-side constructor, because the server
     * instead calls {@link #toCmd(Vector, int)}.
     *
     * @param gl  Game list; can be empty, but not null
     */
    protected SOCGamesWithOptions(List<String> gl)
    {
        super(GAMESWITHOPTIONS, "-", gl);
    }

    /**
     * Get the list of games (and option strings).
     * List contains each game's name and option strings sent from server, as packed by
     * {@link soc.game.SOCGameOption#packOptionsToString(java.util.Map, boolean) SOCGameOption.packOptionsToString(opts, boolean)}.
     *<P>
     * Game names may be marked with the prefix {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE};
     * this will be removed from their names before adding to the returned game list.
     * To see if a game cannot be joined, call {@link SOCGameList#isUnjoinableGame(String)}.
     *
     * @return list of games contained in this message, or an empty SOCGameList
     * @see SOCGameList#parseGameOptions(String)
     */
    public SOCGameList getGameList()
    {
        SOCGameList gamelist = new SOCGameList();

        final int L = pa.size();
        for (int ii = 0; ii < L; )
        {
            final String gaName = pa.get(ii);
            ++ii;
            gamelist.addGame(gaName, pa.get(ii), false);
            ++ii;
        }

        return gamelist;
    }

    /**
     * Minimum version where this message type is used.
     * GAMESWITHOPTIONS introduced in 1.1.07 for game-options feature.
     * @return Version number, 1107 for JSettlers 1.1.07.
     */
    @Override
    public int getMinimumVersion() { return 1107; }

    /**
     * Parse the command String array into a SOCGamesWithOptions message.
     *
     * @param gl  the game list; must contain an even number of strings
     *            (pairs of game names+options); can be null or empty
     * @return    a SOCGamesWithOptions message, or null if parsing errors
     */
    public static SOCGamesWithOptions parseDataStr(List<String> gl)
    {
        if (gl == null)
            gl = new ArrayList<String>();
        else if ((gl.size() % 2) != 0)
            return null;  // must have an even # of strings

        return new SOCGamesWithOptions(gl);
    }

    /**
     * Build the command string from a set of games; used at server side.
     * @param ga  the list of games, as a mixed-content vector of Strings and/or {@link SOCGame}s;
     *            if a client can't join a game, it should be a String prefixed with
     *            {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @param cliVers  Client version; assumed >= {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
     *            If any game's options need adjustment for an older client, cliVers triggers that.
     * @return    the command string
     */
    public static String toCmd(Vector<?> ga, final int cliVers)
    {
        // build by iteration
        StringBuffer sb = new StringBuffer(Integer.toString(SOCMessage.GAMESWITHOPTIONS));
        for (int i = 0; i < ga.size(); ++i)
        {
            sb.append(sep);
            Object ob = ga.elementAt(i);
            if (ob instanceof SOCGame)
            {
                sb.append(((SOCGame) ob).getName());
                sb.append(sep);
                sb.append(SOCGameOption.packOptionsToString(((SOCGame) ob).getGameOptions(), false, cliVers));
            } else {
                sb.append((String) ob);
                sb.append(sep);
                sb.append("-");
            }
        }
        return sb.toString();
    }

}
