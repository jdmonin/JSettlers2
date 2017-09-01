/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file Copyright (C) 2009,2013-2015 Jeremy D Monin <jeremy@nand.net>
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
 **/
package soc.message;

/**
 * Information on current defaults for new games' {@link soc.game.SOCGameOption game options}.
 * Based on server's current values ({@link soc.game.SOCGameOption#getIntValue() .getIntValue()},
 * not {@link soc.game.SOCGameOption#defaultIntValue .defaultIntValue} field).
 * Client sends this when the user clicks the "New Game" button for the first time.
 *<P>
 * Server responds to client's GAMEOPTIONGETDEFAULTS by sending its own GAMEOPTIONGETDEFAULTS.
 * All of server's known options are sent, except empty string-valued options.
 * Depending on client version, server's response may include option names that
 * the client is too old to use; the client is able to ignore them.
 * If the client asks about such an option (by sending {@link SOCGameOptionInfo GAMEOPTIONINFO}),
 * the server will respond with {@link soc.game.SOCGameOption#OTYPE_UNKNOWN GAMEOPTIONINFO(OTYPE_UNKNOWN)}.
 *<P>
 * Introduced in 1.1.07; check server version against {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}
 * before sending this message.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't create games.
 *<P>
 * <B>I18N:</B> Since the client's New Game dialog will need localized strings for all
 * {@link soc.game.SOCScenario SOCScenario}s, v2.0.00 sends those strings before the game option
 * default values so that the client will have them before showing the dialog.
 * The strings are sent using {@link SOCLocalizedStrings}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.07
 */
public class SOCGameOptionGetDefaults extends SOCMessage
{
    private static final long serialVersionUID = 1107L;  // last structural change v1.1.07

    /**
     * String of the options (name-value pairs) as sent over network
     */
    private String opts;

    /**
     * Create a GameOptionGetDefaults message.
     *
     * @param opts  the options string, or null if none (client to server).
     *              To create the string, call
     *              {@link soc.game.SOCGameOption#packOptionsToString(Map, boolean) SOCGameOption.packOptionsToString(opts, true)}.
     */
    public SOCGameOptionGetDefaults(String opts)
    {
        messageType = GAMEOPTIONGETDEFAULTS;
        this.opts = opts;
    }

    /**
     * Get the string of option name-value pairs sent over the network.
     * To turn this into a group of {@link soc.game.SOCGameOption SOCGameOptions},
     * call {@link soc.game.SOCGameOption#parseOptionsToMap(String) SOCGameOption.parseOptionsToMap()}.
     * @return the string of options, or null if none (client to server)
     */
    public String getOpts()
    {
        return opts;
    }

    /**
     * GAMEOPTIONGETDEFAULTS [sep opts]
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(opts);
    }

    /**
     * GAMEOPTIONGETDEFAULTS [sep opts]
     *
     * @param opts  the options string, or null if none (cli->serv)
     * @return    the command string
     */
    public static String toCmd(String opts)
    {
        if (opts != null)
            return GAMEOPTIONGETDEFAULTS + sep + opts;
        else
            return Integer.toString(GAMEOPTIONGETDEFAULTS);
    }

    /**
     * Parse the command String into a GameOptionGetDefaults message
     *
     * @param s   the String to parse
     * @return    a GameOptionGetDefaults message
     */
    public static SOCGameOptionGetDefaults parseDataStr(String s)
    {
        if (s.length() == 0)
            s = null;
        return new SOCGameOptionGetDefaults(s);
    }

    /**
     * Minimum version where this message type is used.
     * GAMEOPTIONGETDEFAULTS introduced in 1.1.07 for game-options feature.
     * @return Version number, 1107 for JSettlers 1.1.07.
     */
    public int getMinimumVersion() { return 1107; }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCGameOptionGetDefaults:opts=" + opts;
    }

}
