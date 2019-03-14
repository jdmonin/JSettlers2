/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009,2011,2013-2014,2018-2019 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Map;
import java.util.StringTokenizer;

import soc.game.SOCGame;
import soc.game.SOCGameOption;

/**
 * This broadcast message from server announces a new game, with a certain
 * version and possibly {@link SOCGameOption game options}, to all clients.
 *<UL>
 * <LI> This message is followed by {@link SOCJoinGameAuth JOINGAMEAUTH}
 *      to the requesting client, so they will join the game they just created.
 *</UL>
 * Introduced in 1.1.07; check server version against {@link #VERSION_FOR_NEWGAMEWITHOPTIONS}
 * before sending this message.
 *<P>
 * Game name may include a prefix marker if the client can't join;
 * see {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
 * This marker will be retained within the game name returned by
 * {@link #getGame()}.
 *<P>
 * Just like {@link SOCNewGame NEWGAME}, robot clients don't need to handle
 * this message type. Bots ignore new-game announcements and are asked to
 * join specific games.
 *
 * @author Jeremy D. Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.07
 */
public class SOCNewGameWithOptions extends SOCMessageTemplate2s
{
    private static final long serialVersionUID = 1107L;  // last structural change v1.1.07

    /**
     * Minimum version (1.1.07) of client/server which recognize
     * and send NEWGAMEWITHOPTIONS and other messages related
     * to {@link soc.game.SOCGameOption}.
     */
    public static final int VERSION_FOR_NEWGAMEWITHOPTIONS = 1107;

    private int gameMinVers = -1;

    /**
     * Create a SOCNewGameWithOptions message.
     *
     * @param ga  the name of the game; may have the
     *            {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE} prefix.
     *            minVers also designates if the game is joinable.
     * @param optstr Requested game options, in the format returned by
     *            {@link soc.game.SOCGameOption#packOptionsToString(Map, boolean) SOCGameOption.packOptionsToString(opts, false)},
     *            or null
     * @param minVers Minimum client version required for this game, or -1.
     */
    public SOCNewGameWithOptions(final String ga, final String optstr, final int minVers)
    {
        super(NEWGAMEWITHOPTIONS,
              ga,
              Integer.toString(minVers),
              ((optstr != null) && (optstr.length() > 0) ? optstr : "-"));
        gameMinVers = minVers;
    }

    /**
     * Create a SOCNewGameWithOptions message, optionally for a specific client version.
     * This constructor may adjust encoded option values for backwards compatibility with the client version.
     * If so, contents of the referenced {@code opts} map aren't changed: Calls
     * {@link SOCGameOption#packOptionsToString(Map, boolean, int) SOCGameOption.packOptionsToString(opts, false, cliVers)}.
     *
     * @param ga  the name of the game; the game name may have
     *            the {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE} prefix.
     * @param opts Requested game options, as a read-only map
     * @param gameMinVers Minimum client version required, or -1
     * @param cliVers  Client version, if any game's options need adjustment for an older client.
     *            Use -2 if the client version doesn't matter, or if adjustment should not be done.
     * @since 2.0.00
     */
    public SOCNewGameWithOptions
        (final String ga, final Map<String, SOCGameOption> opts, final int minVers, final int cliVers)
    {
        this(ga, SOCGameOption.packOptionsToString(opts, false, cliVers), minVers);
    }

    /**
     * Get the encoded game options, if any.
     * @return the options for the new game, in the format returned by
     *     {@link soc.game.SOCGameOption#packOptionsToString(Map, boolean) SOCGameOption.packOptionsToString(opts, false)},
     *     or null if no options
     */
    public String getOptionsString()
    {
        return p2;
    }

    /**
     * @return the game's minimum required client version, or -1.
     *         This is when sending from server to all clients.
     */
    public int getMinVersion()
    {
        return gameMinVers;
    }

    /**
     * NEWGAMEWITHOPTIONS sep game sep2 minVers sep2 optionstring
     *
     * @param ga  the name of the game; the game name may have
     *            the {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE} prefix.
     * @param optstr Requested game options, in the format returned by
     *            {@link soc.game.SOCGameOption#packOptionsToString(Map, boolean)},
     *            or null
     * @param minVers Minimum client version required, or -1
     * @return the command string
     */
    public static String toCmd(final String ga, final String optstr, final int minVers)
    {
        return NEWGAMEWITHOPTIONS + sep + ga + sep2 + Integer.toString(minVers) + sep2
               + (((optstr != null) && (optstr.length() > 0)) ? optstr : "-");
    }

    /**
     * NEWGAMEWITHOPTIONS sep game sep2 minVers sep2 optionstring
     *
     * @param ga  the name of the game; the game name may have
     *            the {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE} prefix.
     * @param opts Requested game options, as a read-only map
     * @param gameMinVers Minimum client version required, or -1
     * @param cliVers  Client version, if any game's options need adjustment for an older client.
     *            Use -2 if the client version doesn't matter, or if adjustment should not be done.
     * @return the command string
     */
    public static String toCmd
        (final String ga, final Map<String,SOCGameOption> opts, final int gameMinVers, final int cliVers)
    {
        return toCmd(ga, SOCGameOption.packOptionsToString(opts, false, cliVers), gameMinVers);
    }

    /**
     * NEWGAMEWITHOPTIONS sep game sep2 minVers sep2 optionstring
     *<P>
     * Game's options and minimum required version will be extracted from game.
     *
     * @param ga  the game
     * @param cliVers  Client version; assumed >= {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
     *            If any game's options need adjustment for an older client, cliVers triggers that.
     *            Use -2 if the client version doesn't matter.
     * @return the command string
     */
    public static String toCmd(final SOCGame ga, final int cliVers)
    {
        return toCmd(ga.getName(),
            SOCGameOption.packOptionsToString(ga.getGameOptions(), false, cliVers),
            ga.getClientVersionMinRequired());
    }

    /**
     * Parse the command String into a SOCNewGameWithOptions message.
     *
     * @param s   the String to parse: NEWGAMEWITHOPTIONS sep game sep2 opts
     * @return    a SOCNewGameWithOptions message, or null if the data is garbled
     */
    public static SOCNewGameWithOptions parseDataStr(String s)
    {
        String ga; // the game name
        int minVers;
        String opts;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            minVers = Integer.parseInt(st.nextToken());
            opts = st.nextToken(sep);  // NOT sep2! options may contain commas.
            // Will begin with "," (sep2) due to the separator change. This is cosmetic only.
        }
        catch (Exception e)
        {
            return null;
        }
        if (opts.equals("-"))
            opts = null;

        return new SOCNewGameWithOptions(ga, opts, minVers);
    }

    /**
     * Minimum version where this message type is used.
     * NEWGAMEWITHOPTIONS introduced in 1.1.07 for game-options feature.
     * @return Version number, 1107 for JSettlers 1.1.07.
     */
    @Override
    public int getMinimumVersion() { return VERSION_FOR_NEWGAMEWITHOPTIONS; /* == 1107 */ }

}
