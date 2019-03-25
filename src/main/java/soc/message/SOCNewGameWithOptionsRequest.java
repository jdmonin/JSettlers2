/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2009,2013-2014,2016-2017 Jeremy D Monin <jeremy@nand.net>
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

/**
 * This message from client is a request to create a new game with options;
 * needs same username/password options as {@link SOCJoinGame JOINGAME}.
 * If it can create the game, server's reply is a broadcast {@link SOCNewGameWithOptions}.
 *<P>
 * Once a client has successfully joined or created any game or channel, the
 * nickname and password fields can be left blank in later join/create requests.
 * All server versions ignore the password field after a successful request.
 *<P>
 * Introduced in 1.1.07; check server version against {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}
 * before sending this message.  Older servers should be given {@link SOCJoinGame JOINGAME} instead.
 *<P>
 * Some game options have the {@link SOCGameOption#FLAG_INTERNAL_GAME_PROPERTY} flag.
 * The client should not send these as part of a new-game request message,
 * and the server should ignore them if it does.  The server may send out
 * such options, as part of a game it has created, in a {@link SOCNewGameWithOptions} message.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't create games.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.07
 */
public class SOCNewGameWithOptionsRequest extends SOCMessageTemplateJoinGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /** won't be null, even if opts is null, due to {@link SOCGameOption#packOptionsToString(Map, boolean)} format. */
    private String optsStr;

    /** may be null */
    private Map<String, SOCGameOption> opts;

    /**
     * Create a NewGameWithOptionsRequest message.
     *
     * @param nn  nickname, or "-" if already auth'd to server
     * @param pw  optional password, or "" if none
     * @param hn  optional server host name, or "-"
     * @param ga  name of the game
     * @param optstr the game options as a string name-value pairs, as created by
     *             {@link SOCGameOption#packOptionsToString(Map, boolean)}.
     */
    public SOCNewGameWithOptionsRequest(String nn, String pw, String hn, String ga, String optstr)
    {
        super(nn, pw, hn, ga);
        messageType = NEWGAMEWITHOPTIONSREQUEST;
        optsStr = optstr;
        opts = SOCGameOption.parseOptionsToMap(optstr);
    }

    /**
     * @return the game options (map of {@link SOCGameOption}), or null;
     *   does not parse optsStr.
     */
    public Map<String, SOCGameOption> getOptions()
    {
       return opts;
    }

    /**
     * NEWGAMEWITHOPTIONSREQUEST sep nickname sep2 password sep2 host sep2 game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(nickname, password, host, game, optsStr);
    }

    /**
     * NEWGAMEWITHOPTIONSREQUEST sep nickname sep2 password sep2 host sep2 game sep2 options
     *
     * @param nn  the nickname, or "-" if already auth'd to server
     * @param pw  the optional password, or "" if none; not null
     * @param hn  the optional server host name, or "-"
     * @param ga  the game name
     * @param optstr the game options as a string name-value pairs, as created by
     *             {@link SOCGameOption#packOptionsToString(Map, boolean)}.
     * @return    the command string
     */
    public static String toCmd(String nn, String pw, String hn, String ga, String optstr)
    {
        if (pw.length() == 0)
            pw = EMPTYSTR;

        return NEWGAMEWITHOPTIONSREQUEST + sep + nn + sep2 + pw + sep2 + hn + sep2 + ga + sep2 + optstr;
    }

    /**
     * NEWGAMEWITHOPTIONSREQUEST sep nickname sep2 password sep2 host sep2 game sep2 options
     *
     * @param nn  the nickname, or "-" if already auth'd to server
     * @param pw  the optional password, or "" if none
     * @param hn  the optional server host name, or "-"
     * @param ga  the game name
     * @param opts the game options ({@link SOCGameOption})
     * @return    the command string
     */
    public static String toCmd(String nn, String pw, String hn, String ga, Map<String, SOCGameOption> opts)
    {
        return toCmd(nn, pw, hn, ga, SOCGameOption.packOptionsToString(opts, false));
    }

    /**
     * Parse the command String into a NewGameWithOptionsRequest message
     *
     * @param s   the String to parse
     * @return    a NewGameWithOptionsRequest message, or null if the data is garbled;
     *            this would include string-valued options which fail
     *            {@link SOCMessage#isSingleLineAndSafe(String)}.
     */
    public static SOCNewGameWithOptionsRequest parseDataStr(String s)
    {
        String nn;
        String pw;
        String hn;
        String ga;
        String optstr;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            pw = st.nextToken();
            hn = st.nextToken();
            ga = st.nextToken();
            optstr = st.nextToken(sep);  // not sep2 ! Commas are used to sep options.

            if (pw.equals(EMPTYSTR))
                pw = "";
        }
        catch (Exception e)
        {
            System.err.println("L168 NGWOR.parseDataStr failed for \"" + s + "\": " + e);
            e.printStackTrace();
            return null;
        }

        return new SOCNewGameWithOptionsRequest(nn, pw, hn, ga, optstr);
    }

    /**
     * Minimum version where this message type is used.
     * NEWGAMEWITHOPTIONS introduced in 1.1.07 for game-options feature.
     * @return Version number, 1107 for JSettlers 1.1.07.
     */
    public int getMinimumVersion() { return 1107; }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return super.toString("SOCNewGameWithOptionsRequest", "opts=" + optsStr);
    }

}
