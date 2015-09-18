/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2015 Jeremy D Monin <jeremy@nand.net>
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

/**
 * Message from server for i18n localization of item types such as game options or scenarios,
 * such as {@link #TYPE_GAMEOPT} or {@link #TYPE_SCENARIO}, or request from client to get
 * localized strings for specific keys of certain item types.
 *<P>
 * This message is always about one string type.  The meaning of the keys and their strings may differ per
 * string type.  For example, keys for {@link #TYPE_SCENARIO} are the same as in {@code SOCScenario}
 * and the server sends two strings per scenario, its short and long description.
 *<P>
 * Normally sent from the server when client needs all keys or some keys for a type; see type constant javadocs.
 * For example: When joining a game with a scenario, the client needs strings for that
 * scenario. When creating a new game, the client needs strings for all scenarios in order
 * to read about them and maybe choose one.  The server's message includes the string type such as
 * {@link #TYPE_SCENARIO}, the flags field, and then each key and its string(s) as described above.
 *<P>
 * This message is not commonly sent from client to server, which is included to allow clients
 * to get localized strings for scenarios or gameopts newer than the client or changed since
 * the client's release.  The client's request includes the string type such as {@link #TYPE_SCENARIO},
 * the flags field, and then any keys for which it wants localized strings, or an empty list if message also sets
 * {@link #FLAG_REQ_ALL} to request all items. The server's response is as described above, including all
 * keys requested by the client. If a key isn't known at the server,
 * in the response that key will be followed by {@link #MARKER_KEY_UNKNOWN} instead of by its string(s).
 *<P>
 * The first element of {@link #getParams()} is a string type such as {@link #TYPE_GAMEOPT}
 * or {@link #TYPE_SCENARIO}.  This is followed by the integer flag field (hex string) which
 * is removed from the list at the receiving end's parser.
 * The rest of {@code getParams()} is organized according to the type; see type constant javadocs.
 * Since {@code getParams()} can't contain empty strings, check contents for {@link #EMPTY}.
 *<P>
 * Not a per-game message; {@link #getGame()} returns {@link SOCMessage#GAME_NONE}.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't have a locale.
 *<P>
 * Introduced in 2.0.00; check client version against {@link soc.util.SOCStringManager#VERSION_FOR_I18N}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCLocalizedStrings extends SOCMessageTemplateMs
{
    /**
     * Symbol to represent a null or empty string value, because
     * empty {@code pa[]} elements can't be parsed over the network.
     */
    public static final String EMPTY = "\t";

    /**
     * Game Option localized names, for {@link soc.game.SOCGameOption}.
     * After the string type at element 0, {@link #getParams()} contents are pairs
     * of strings, each of which is a game option keyname and localized name.
     */
    public static final String TYPE_GAMEOPT = "G";

    /**
     * Game Scenario localized names and descriptions, for {@link soc.game.SOCScenario}. After the
     * string type at element 0, {@link #getParams()} contents are triples of strings, each of which
     * is a game scenario keyname, localized name, and optional localized long description or {@link #EMPTY}.
     *<P>
     * If the client has requested specific scenario keynames for this type, the server replies with all known
     * localized strings for those items.  Items without localized strings will not be included in the reply.
     * If none of the items are localized, server replies with an empty list with the {@link #FLAG_SENT_ALL}
     * flag.
     */
    public static final String TYPE_SCENARIO = "S";

    /** First character of all MARKERs */
    private static final String MARKER_PREFIX = "\026";  // 0x16 ^V (SYN)

    /**
     * "Type is unknown" flag, for server's response when it doesn't recognize
     * the string type requested by the client.
     */
    public static final int FLAG_TYPE_UNKNOWN = 0x01;

    /**
     * "Request all" flag, for client to request all items of a requested type.
     * This flag is sent with an empty string list.
     */
    public static final int FLAG_REQ_ALL = 0x02;

    /**
     * "Sent all of them" flag, for server's response when it has sent all known items
     * of the string type requested by the client.  The client should not request any
     * further items of that string type.
     *<P>
     * This flag can also be sent when no known items are available for a recognized
     * string type; the server will send an empty list with this flag set.
     */
    public static final int FLAG_SENT_ALL = 0x04;

    /**
     * "Key is unknown" marker token, for server's response when it doesn't have a localized
     * string for the key requested by the client.
     */
    public static final String MARKER_KEY_UNKNOWN = "\026K";

    private static final long serialVersionUID = 2000L;  // no structural change since introduced in v2.0.00

    /**
     * Request or response flags such as {@link #FLAG_SENT_ALL}.
     * @see #isFlagSet(int)
     */
    private int flags;

    /**
     * Server-side constructor.
     * The server usually uses static {@link #toCmd(String, int, List)} to send this message to clients,
     * but the constructor is used with the Practice client's local server.
     *
     * @param type  String type such as {@link #TYPE_SCENARIO};
     *     must pass {@link SOCMessage#isSingleLineAndSafe(String)}.
     *     This constructor will prepend {@code type} to the {@code strs} list.
     * @param flags  Any flags such as {@link #FLAG_SENT_ALL}, or 0
     * @param strs  the list of strings, organized in a type-specific way; see {@code type} constant javadocs.
     *     Each element must pass
     *     {@link SOCMessage#isSingleLineAndSafe(String, boolean) isSingleLineAndSafe(String, true)}:
     *     {@link SOCMessage#sep2} characters are allowed, but {@link SOCMessage#sep} are not.
     *    <P>
     *     The list may be empty or null.  Since this constructor builds an object and not a
     *     network message command, will not replace empty or null elements with {@link #EMPTY} or "".
     *     The constructor will prepend {@code type} to the {@code strs} list, creating it if null.
     *    <P>
     *     If any string starts with {@link #MARKER_PREFIX}, it must be a recognized marker:
     *     ({@link #MARKER_KEY_UNKNOWN}) declared in this class.
     *
     * @throws IllegalArgumentException  If {@code type} or any element of {@code strs} fails
     *     {@link SOCMessage#isSingleLineAndSafe(String)}.
     */
    public SOCLocalizedStrings(final String type, final int flags, final List<String> strs)
        throws IllegalArgumentException
    {
        super(LOCALIZEDSTRINGS, SOCMessage.GAME_NONE,
              ((strs != null) ? strs : new ArrayList<String>()));
              // strs becomes pa field
        checkParams(type, strs);  // isSingleLineAndSafe(type), isSingleLineAndSafe(each str), etc

        pa.add(0, type);  // client will expect first list element is the type
        this.flags = flags;
    }

    /**
     * Constructor for client to parse server's list of localized strings.
     * See {@link SOCLocalizedStrings class javadoc} for interpreting contents of this message.
     *<P>
     * The {@link #flags} field (for {@link #FLAG_SENT_ALL}, {@link #FLAG_TYPE_UNKNOWN}, etc)
     * is parsed here and removed from the list of strings.
     *
     * @param strs  String list; assumes caller has validated length >= 2 (type, flags).
     * @throws NumberFormatException  if flags field isn't a valid hex number
     */
    private SOCLocalizedStrings(final List<String> strs)
        throws NumberFormatException
    {
        super(LOCALIZEDSTRINGS, SOCMessage.GAME_NONE, strs);

        // flag field; parse error throws exception for parseDataStr to catch
        flags = Integer.parseInt(strs.get(1), 16);
        strs.remove(1);
    }

    /**
     * Is this flag bit set in the {@code flags} field?
     * @param flag  A flag such as {@link #FLAG_SENT_ALL}
     * @return  True if set
     */
    public final boolean isFlagSet(final int flag)
    {
        return (0 != (flags & flag));
    }

    /**
     * Minimum version where this message type is used.
     * LOCALIZEDSTRINGS introduced in 2.0.00 for i18n localization.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    @Override
    public final int getMinimumVersion() { return 2000; }

    /**
     * Parse the command String list into a SOCLocalizedStrings message.
     *
     * @param strs  the data list; length must be at least 1 to indicate the type
     * @return    a SOCLocalizedStrings message, or null if parsing errors
     */
    public static SOCLocalizedStrings parseDataStr(List<String> strs)
    {
        if ((strs == null) || (strs.size() < 2))
            return null;  // must have at least 2 strings: type, flags

        try
        {
            return new SOCLocalizedStrings(strs);
        } catch (Exception e) {
            // catch NumberFormatException and anything else from a malformed message
            return null;
        }
    }

    /**
     * Build the command string from a type and list of strings; used at server side.
     * @param type  String type such as {@link #TYPE_SCENARIO};
     *     must pass {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param flags  Any flags such as {@link #FLAG_SENT_ALL}, or 0
     * @param strs  the list of strings, organized in a type-specific way; see {@code type} constant javadocs.
     *     Each element must pass
     *     {@link SOCMessage#isSingleLineAndSafe(String, boolean) isSingleLineAndSafe(String, true)}:
     *     {@link SOCMessage#sep2} characters are allowed, but {@link SOCMessage#sep} are not.
     *    <P>
     *     The list may be empty or null.  Empty or null elements in {@code strs} are automatically replaced here
     *     with {@link #EMPTY}, but {@link #getParams()} will not automatically replace {@link #EMPTY}
     *     with "" at the receiver.
     *    <P>
     *     If any string starts with {@link #MARKER_PREFIX}, it must be a recognized marker:
     *     ({@link #MARKER_KEY_UNKNOWN}) declared in this class.
     *
     * @return    the command string
     * @throws IllegalArgumentException  If {@code type} or any element of {@code strs} fails
     *     {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @throws NullPointerException if {@code strs} is null
     */
    public static String toCmd(final String type, final int flags, List<String> strs)
        throws IllegalArgumentException
    {
        checkParams(type, strs);  // isSingleLineAndSafe(type), isSingleLineAndSafe(each str), etc

        StringBuilder sb = new StringBuilder(Integer.toString(SOCMessage.LOCALIZEDSTRINGS));
        sb.append(sep);
        sb.append(type);
        sb.append(sep);
        sb.append(Integer.toHexString(flags));

        if (strs != null)
        {
            for (int i = 0; i < strs.size(); ++i)
            {
                sb.append(sep);

                String itm = strs.get(i);
                if ((itm == null) || (itm.length() == 0))
                    itm = EMPTY;

                sb.append(itm);
            }
        }

        return sb.toString();
    }

    /**
     * Build the command string from a type and single string key; used for requests from client side.
     * @param type  String type such as {@link #TYPE_SCENARIO};
     *     must pass {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param flags  Any flags such as {@link #FLAG_SENT_ALL}, or 0
     * @param str  String key being requested, with type-specific meaning; see {@code type} constant javadocs.
     *     Must pass
     *     {@link SOCMessage#isSingleLineAndSafe(String, boolean) isSingleLineAndSafe(String, true)}:
     *     {@link SOCMessage#sep2} characters are allowed, but {@link SOCMessage#sep} are not.
     *     If any string starts with {@link #MARKER_PREFIX}, it must be a recognized marker
     *     ({@link #MARKER_KEY_UNKNOWN}) declared in this class.
     * @return    the command string
     * @throws IllegalArgumentException  If {@code type} or {@code str} fails
     *     {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @throws NullPointerException if {@code str} is null
     */
    public static String toCmd(final String type, final int flags, String str)
        throws IllegalArgumentException, NullPointerException
    {
        if (str == null)
            throw new NullPointerException();

        // Convenience method: create a List of Strings
        // instead of constructing toCmd from the single str,
        // because required checkParams validator needs a List anyway.

        ArrayList<String> strs = new ArrayList<String>();
        strs.add(str);
        return toCmd(type, flags, strs);
    }

    /**
     * Check parameter format.
     * Used by {@link #toCmd(String, int, List)} and {@link #SOCLocalizedStrings(String, int, List)};
     * see {@code toCmd(..)} for required format and thus the checks performed.
     */
    private static void checkParams(final String type, final List<String> strs)
        throws IllegalArgumentException
    {
        if (! isSingleLineAndSafe(type))
            throw new IllegalArgumentException("type: " + type);

        if (strs == null)
            return;

        for (int i = 0; i < strs.size(); ++i)
        {
            final String itm = strs.get(i);
            if ((itm == null) || (itm.length() == 0))
                continue;
            else if (itm.startsWith(MARKER_PREFIX))
                if (! itm.equals(MARKER_KEY_UNKNOWN))
                    throw new IllegalArgumentException("item " + i + ": " + itm);
            else if ((itm.indexOf(SOCMessage.sep_char) != -1) || ! isSingleLineAndSafe(itm, true))
                throw new IllegalArgumentException("item " + i + ": " + itm);
        }
    }

}
