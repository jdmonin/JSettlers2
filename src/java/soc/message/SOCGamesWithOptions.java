/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.message;

import java.util.Vector;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.util.SOCGameList;

/**
 * List of all games currently on the server, including
 * their {@link soc.game.SOCGameOption game options}.
 * It's constructed and sent for each connecting client
 * which can understand game options (1.1.07 and newer),
 * by calling {@link #toCmd(Vector)}.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't create games.
 *<P>
 * Introduced in 1.1.07; check client version against
 * {@link soc.message.SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.07
 * @see SOCGames
 */
public class SOCGamesWithOptions extends SOCMessageTemplateMs
{
    /**
     * Constructor for client to parse server's list of games.
     * Creates opt with the proper type, even if unknown locally.
     * This parses the games+names into a string array,
     * but not the game options into {@link soc.game.SOCGameOption game option}
     * objects; call {@link #parseGameOptions(boolean)} for that.
     *<P>
     * There is no server-side constructor, because the server
     * instead calls {@link #toCmd(Vector)}.
     *
     * @param gla Game list array
     */
    protected SOCGamesWithOptions(String[] gla)
    {
	super(GAMESWITHOPTIONS, "-", gla);
    }

    /**
     * Parse the list of games (and options).
     * Uses the option strings sent from server, via
     * {@link SOCGameOption#parseOptionsToHash(String)}.
     * @return list of games contained in this message, or an empty SOCGameList
     */
    public SOCGameList parseGameList()
    {
        SOCGameList gamelist = new SOCGameList();
        for (int ii = 0; ii < pa.length; )
        {
            final String gaName = pa[ii];
            ++ii;
            gamelist.addGame(gaName, SOCGameOption.parseOptionsToHash(pa[ii]), false);
            ++ii;
        }
        return gamelist;
    }

    /**
     * Minimum version where this message type is used.
     * GAMESWITHOPTIONS introduced in 1.1.07 for game-options feature.
     * @return Version number, 1107 for JSettlers 1.1.07.
     */
    public int getMinimumVersion() { return 1107; }

    /**
     * Parse the command String array into a SOCGamesWithOptions message.
     *
     * @param gla  the game-list array; must contain an even number of strings
     *             (pairs of game names+options)
     * @return    a SOCGamesWithOptions message, or null if parsing errors
     */
    public static SOCGamesWithOptions parseDataStr(String[] gla)
    {
        if ((gla == null) || ((gla.length % 2) != 0))
            return null;  // must have an even# of strings

        return new SOCGamesWithOptions(gla);
    }

    /**
     * Build the command string from a set of games; used at server side.
     * @param ga  the list of games, as a mixed-content vector of Strings and/or {@link SOCGame}s;
     *            if a client can't join a game, it should be a String prefixed with
     *            {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @return    the command string
     */
    public static String toCmd(Vector ga)
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
                sb.append(SOCGameOption.packOptionsToString(((SOCGame) ob).getGameOptions()));
            } else {
                sb.append((String) ob);
                sb.append(sep);
                sb.append("-");
            }
        }
        return sb.toString();
    }
}
