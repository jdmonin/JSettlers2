/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2010,2013-2015,2017,2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;


/**
 * This message means that the server is asking this robot client to join a game.
 * The bot should record the game options (if any) and respond with {@link SOCJoinGame JOINGAME},
 * the message sent by human players to request joining a game.
 *<P>
 * In 1.1.07, added optional parameter: game options.
 * Because this is sent only to robots, and robots' versions always
 * match the server version, we don't need to worry about backwards
 * compatibility.
 *<P>
 * Before 2.0.00, this class was called {@code SOCJoinGameRequest};
 * renamed to clarify versus {@link SOCJoinGame}.
 *
 * @author Robert S Thomas
 * @see SOCJoinGameAuth
 */
public class SOCBotJoinGameRequest extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2450L;  // last structural change v2.4.50

    /**
     * Name of game
     */
    private String game;

    /**
     * Where the robot should sit
     */
    private int playerNumber;

    /**
     * Packed game options if any, as created by
     * {@link SOCGameOption#packOptionsToString(Map, boolean, boolean) SOCGameOption.packOptionsToString(opts, false, false)}.
     * Won't be null, even if opts is null, due to {@code packOptionsToString(..)} format.
     * @since 2.4.50
     */
    private String optsStr;

    /**
     * Parsed game options, if any.
     * May be null if {@link #getOptions(SOCGameOptionSet)} hasn't been called, or if optsStr is {@code "-"}
     * @since 1.1.07
     */
    private Map<String,SOCGameOption> opts = null;

    /**
     * Create a BotJoinGameRequest message with a set of {@link SOCGameOption}s.
     *
     * @param ga  name of game
     * @param pn  the seat number
     * @param opts  game's {@link SOCGameOption}s, or null
     * @see #SOCBotJoinGameRequest(String, int, String)
     */
    public SOCBotJoinGameRequest(String ga, int pn, SOCGameOptionSet opts)
    {
        messageType = BOTJOINGAMEREQUEST;
        game = ga;
        playerNumber = pn;
        Map<String, SOCGameOption> optsMap = (opts != null) ? opts.getAll() : null;
        this.opts = optsMap;
        optsStr = SOCGameOption.packOptionsToString(optsMap, false, false);
    }

    /**
     * Create a BotJoinGameRequest message with a packed string of game options.
     *
     * @param ga  name of game
     * @param pn  the seat number
     * @param optsStr {@link SOCGameOption game options}, or null
     * @see #SOCBotJoinGameRequest(String, int, SOCGameOptionSet)
     * @since 2.4.50
     */
    public SOCBotJoinGameRequest(String ga, int pn, final String optsStr)
    {
        messageType = BOTJOINGAMEREQUEST;
        game = ga;
        playerNumber = pn;
        opts = null;
        this.optsStr = optsStr;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the player seat number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * Get the parsed {@link SOCGameOption}s, if any.
     * @param knownOpts  all Known Options
     * @return game options, or null
     * @throws IllegalArgumentException if {@link SOCGameOption#parseOptionsToMap(String, SOCGameOptionSet)}
     *     can't parse optsStr field
     * @since 1.1.07
     */
    public Map<String,SOCGameOption> getOptions(final SOCGameOptionSet knownOpts)
        throws IllegalArgumentException
    {
        if (opts == null)
            opts = SOCGameOption.parseOptionsToMap(optsStr, knownOpts);

        return opts;
    }

    /**
     * BOTJOINGAMEREQUEST sep game sep2 playerNumber sep2 optionstring
     *
     * @return the command String
     */
    @Override
    public String toCmd()
    {
        return BOTJOINGAMEREQUEST + sep + game + sep2 + playerNumber + sep2 + optsStr;
    }

    /**
     * Parse the command String into a BotJoinGameRequest message
     *
     * @param s   the String to parse
     * @return    a BotJoinGameRequest message, or null if the data is garbled
     */
    public static SOCBotJoinGameRequest parseDataStr(String s)
    {
        String ga; // the game name
        int pn; // the seat number
        String optstr;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            optstr = st.nextToken(sep);  // NOT sep2: options may contain ","
            if ((! optstr.isEmpty()) && (optstr.charAt(0) == sep2_char))
                optstr = optstr.substring(1);  // discard ',' between pn and optstr
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCBotJoinGameRequest(ga, pn, optstr);
    }

    /**
     * Simple human-readable delimited representation, used for debug purposes.
     *<P>
     * Before v2.4.50, opts didn't contain game option details, only "null" or "(non-null)".
     * @return a human readable form of the message
     */
    @Override
    public String toString()
    {
        String s = "SOCBotJoinGameRequest:game=" + game + "|playerNumber=" + playerNumber + "|opts=" + optsStr;
        return s;
    }

}
