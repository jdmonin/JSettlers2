/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2015,2017-2020 Jeremy D Monin <jeremy@nand.net>
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
 * Message from server with i18n localization strings for certain item types such as game options or scenarios
 * ({@link #TYPE_GAMEOPT} or {@link #TYPE_SCENARIO}), or request from client to get
 * localized strings for specific keys of those certain item types.
 * Is not requested by or sent to clients with locale {@code en_US}:
 * Strings in that locale are already hardcoded in game classes for debugging.
 *<P>
 * This message is always about one string type.  The meaning of the keys and their strings may differ per
 * string type.  For example, keys for {@link #TYPE_SCENARIO} are the same as those in {@code SOCScenario}
 * and the server sends two strings per scenario, its short and long description.
 *<P>
 * Normally sent from the server when client needs all keys or some keys for a type; see type constant javadocs.
 * For example: When joining a game with a scenario, the client needs strings for that
 * scenario. When creating a new game, the client needs strings for all scenarios in order
 * to read about them and maybe choose one.  The server's message includes the string type such as
 * {@link #TYPE_SCENARIO}, the flags field, and then each key and its string(s) as described above.
 *<P>
 * Client can send this message to ask for localized strings for one or more specific (or use flag to request all)
 * scenarios or gameopts, and/or for any ones added/changed in server versions newer than the client.
 * The client's request includes the string type such as {@link #TYPE_SCENARIO}, the {@code flags} field,
 * and then any keys for which it wants localized strings (or an empty list if message also sets
 * {@link #FLAG_REQ_ALL} to request all items). The server's response is as described above, including all
 * keys requested by the client. If a key isn't known at the server,
 * in the response that key will be followed by {@link #MARKER_KEY_UNKNOWN} instead of by its string(s).
 *<P>
 * The first element of {@link #getParams()} is a string type such as {@link #TYPE_GAMEOPT}
 * or {@link #TYPE_SCENARIO}.  This is followed by the integer flag field (hex string) which
 * is removed from the list at the receiving end's parser.
 * The rest of {@code getParams()} is organized according to the type; see type constant javadocs.
 * Receiving end automatically translates {@link SOCMessage#EMPTYSTR} elements to "".
 *<P>
 * <B>Max Length:</B> When sending a long list, watch for the 65535-character limit mentioned at
 * {@link soc.server.genericServer.Connection#MAX_MESSAGE_SIZE_UTF8}. Remember that limit is
 * against the strings' {@code UTF-8} encoding, not the internal encoding used with {@link String#length()}.
 * If unsure, you can test length of the final message string with code like:
 * <pre><code>
 *   final String msg = {@link SOCLocalizedStrings#SOCLocalizedStrings(String, int, List) new SOCLocalizedStrings(...)}{@link #toCmd() .toCmd()};
 *   final int len = msg.{@link String#getBytes(String) getBytes("utf-8")}.length;
 *   if (len > {@link soc.server.genericServer.Connection#MAX_MESSAGE_SIZE_UTF8 Connection.MAX_MESSAGE_SIZE_UTF8})
 *   {
 *       ....
 *   }
 * </code></pre>
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
    implements SOCMessageFromUnauthClient
{
    /**
     * Game Option localized names, for {@link soc.game.SOCGameOption}: {@code "O"}.
     * After the string type at element 0, {@link #getParams()} contents are pairs
     * of strings, each of which is a game option keyname and its localized name or "".
     */
    public static final String TYPE_GAMEOPT = "O";

    /**
     * Game Scenario localized names and descriptions, for {@link soc.game.SOCScenario}: {@code "S"}.
     *
     * <H4>From Client:</H4>
     *
     * {@link #getParams()} is a list of scenario keys ({@link soc.game.SOCVersionedItem#key sc.key} field)
     * for which the client is requesting localized strings, or an empty list with {@link #FLAG_REQ_ALL}
     * to get updated strings for all scenarios.
     *<P>
     * A client with a different version than server might instead send
     * {@link SOCScenarioInfo} to ask for all scenarios' details and localized strings.
     *
     * <H4>From Server:</H4>
     *
     * After the string type at element 0, {@link #getParams()} contents are triples of strings, each of which
     * is a game scenario keyname, localized name, and optional localized long description or "".
     * As with any string type, an unknown keyname is a pair of strings here
     * (not a triple): keyname, {@link #MARKER_KEY_UNKNOWN}.
     *<P>
     * If the client has requested specific scenario keynames for this type, the server replies with all known
     * localized strings for those items.  Items without localized strings will not be included in the reply.
     * If none of the items are localized, server replies with an empty list with the {@link #FLAG_SENT_ALL}
     * flag.
     *<P>
     * If the client has requested all scenarios ({@link #FLAG_REQ_ALL}), server responds with a list
     * including all localized scenarios in {@link soc.game.SOCScenario#getAllKnownScenarioKeynames()} and
     * the {@link #FLAG_SENT_ALL} flag.
     *<P>
     * When client sends a {@link SOCJoinGame} request, if that game has a scenario and the client hasn't yet
     * been sent the scenario's info or localized strings, server may respond with {@link SOCScenarioInfo}
     * or {@code SOCLocalizedStrings} for the scenario before it sends {@link SOCJoinGameAuth}.
     */
    public static final String TYPE_SCENARIO = "S";

    /**
     * First character of all {@code MARKER}s: {@code ^V (SYN)}: (char) 22.
     *<P>
     * Currently {@link #MARKER_KEY_UNKNOWN} is the only recognized marker.
     */
    private static final char MARKER_PREFIX = '\026';  // 0x16 ^V (SYN)

    /**
     * "Type is unknown" flag, for server's response when it doesn't recognize
     * the string type requested by the client.
     */
    public static final int FLAG_TYPE_UNKNOWN = 0x01;

    /**
     * "Request all" flag, for client to request all items of a requested type.
     * This flag is sent with an empty string list.
     * Not all string types support this flag.
     * @see #FLAG_SENT_ALL
     */
    public static final int FLAG_REQ_ALL = 0x02;

    /**
     * "Sent all of them" flag, for server's response when it has sent all known items
     * of the string type requested by the client.  The client should not request any
     * further items of that string type.
     *<P>
     * This flag can also be sent when no known items are available for a recognized
     * string type; the server will send an empty list with this flag set.
     * @see #FLAG_REQ_ALL
     */
    public static final int FLAG_SENT_ALL = 0x04;

    /**
     * "Key is unknown" marker token, for server's response when it doesn't have a localized
     * string for the key requested by the client.
     *<P>
     * For known {@link soc.game.SOCGameOption SOCGameOption}s and {@link soc.game.SOCScenario SOCScenario}s,
     * client should use the fallback text it has from the item's initializer.
     * If the game option / scenario was unknown to the client, instead of {@code SOCLocalizedStrings}
     * server would send a {@link SOCGameOptionInfo} / {@link SOCScenarioInfo} with those fallback texts.
     */
    public static final String MARKER_KEY_UNKNOWN = "\026K";

    private static final long serialVersionUID = 2000L;  // no structural change since introduced in v2.0.00

    /**
     * Request or response flags such as {@link #FLAG_SENT_ALL}.
     * @see #isFlagSet(int)
     */
    private int flags;

    /**
     * Client-side request constructor, for a single string key.
     *<P>
     * Before v2.4.50, a static {@code toCmd(..)} method was called by the client.
     *
     * @param type  String type such as {@link #TYPE_SCENARIO};
     *     must pass {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param flags  Any flags such as {@link #FLAG_SENT_ALL}, or 0
     * @param str  String key being requested, with type-specific meaning; see {@code type} constant javadocs.
     *     Must pass
     *     {@link SOCMessage#isSingleLineAndSafe(String, boolean) isSingleLineAndSafe(String, true)}:
     *     {@link SOCMessage#sep2} characters are allowed, but {@link SOCMessage#sep} are not.
     *     If any string starts with {@link #MARKER_PREFIX}, it must be a recognized marker
     *     (like {@link #MARKER_KEY_UNKNOWN}) declared in this class.
     * @throws IllegalArgumentException  If {@code type} or (if not empty) {@code str} fails
     *     {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @throws NullPointerException if {@code str} is null
     * @since 2.4.50
     */
    public SOCLocalizedStrings(final String type, final int flags, String str)
        throws IllegalArgumentException, NullPointerException
    {
        this(type, flags, (List<String>) null);
        if (str == null)
            throw new NullPointerException("str");

        // checkParams validator needs a list
        ArrayList<String> strs = new ArrayList<>();
        strs.add(str);
        checkParams(type, strs);  // isSingleLineAndSafe(type), isSingleLineAndSafe(non-empty str), etc

        // add to actual list field
        pa.add(str);
    }

    /**
     * Server-side constructor.
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
     *     network message command, will not replace empty or null elements with {@link SOCMessage#EMPTYSTR}.
     *     The constructor will prepend {@code type} to the {@code strs} list, creating it if null.
     *    <P>
     *     If any string starts with {@link #MARKER_PREFIX}, it must be a recognized marker
     *     (like {@link #MARKER_KEY_UNKNOWN}) declared in this class.
     *    <P>
     *     <B>Max Length:</B> See {@link SOCLocalizedStrings class javadoc} for combined max length of list's strings.
     *
     * @throws IllegalArgumentException  If {@code type} or any element of {@code strs} fails
     *     {@link SOCMessage#isSingleLineAndSafe(String)}.
     */
    public SOCLocalizedStrings(final String type, final int flags, final List<String> strs)
        throws IllegalArgumentException
    {
        super(LOCALIZEDSTRINGS, ((strs != null) ? new ArrayList<>(strs) : new ArrayList<String>()));
              // pa field gets copy of strs, if any
        checkParams(type, strs);  // isSingleLineAndSafe(type), isSingleLineAndSafe(each non-empty str), etc

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
     *     Will replace any {@link SOCMessage#EMPTYSTR} with "".
     * @throws NumberFormatException  if flags field isn't a valid hex number
     */
    private SOCLocalizedStrings(final List<String> strs)
        throws NumberFormatException
    {
        super(LOCALIZEDSTRINGS, parseData_FindEmptyStrs(strs));

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
            return new SOCLocalizedStrings(strs);  // calls parseData_FindEmptyStrs
        } catch (Exception e) {
            // catch NumberFormatException and anything else from a malformed message
            return null;
        }
    }

    /**
     * See {@link #SOCLocalizedStrings(String, int, List)} for field/parameter details.
     * Will build empty or null elements as {@link SOCMessage#EMPTYSTR}.
     * Relies on callers to make sure {@code type} and every element of {@code strs} passes
     * {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param skipFirstStr  If true, {@code str}'s first element is {@code type}: skip it while building cmd.
     */
    private static String toCmd
        (final String type, final int flags, List<String> strs, final boolean skipFirstStr)
    {
        StringBuilder sb = new StringBuilder(Integer.toString(SOCMessage.LOCALIZEDSTRINGS));
        sb.append(sep);
        sb.append(type);
        sb.append(sep);
        sb.append(Integer.toHexString(flags));

        if (strs != null)
        {
            for (int i = 0; i < strs.size(); ++i)
            {
                if ((i == 0) && skipFirstStr)
                    continue;

                sb.append(sep);

                String itm = strs.get(i);
                if ((itm == null) || (itm.length() == 0))
                    itm = EMPTYSTR;

                sb.append(itm);
            }
        }

        return sb.toString();
    }

    /**
     * Build the command string; used at server side.
     * Empty or null elements will be built as {@link SOCMessage#EMPTYSTR}.
     * At the receiver, {@link #parseDataStr(List)} will automatically replace {@code EMPTYSTR} with "".
     * See {@link #SOCLocalizedStrings(String, int, List)} for field/parameter details.
     */
    public String toCmd()
    {
        return toCmd(pa.get(0), flags, pa, true);
    }

    /**
     * Check parameter format.
     * See {@link #SOCLocalizedStrings(String, int, List)} constructor for
     * required format and checks performed.
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

            if (itm.charAt(0) == MARKER_PREFIX)
            {
                if (! itm.equals(MARKER_KEY_UNKNOWN))
                    throw new IllegalArgumentException("item " + i + ": " + itm);
            } else if ((itm.indexOf(SOCMessage.sep_char) != -1) || ! isSingleLineAndSafe(itm, true)) {
                throw new IllegalArgumentException("item " + i + ": " + itm);
            }
        }
    }

    /**
     * Build and return a human-readable form of the message:
     *<BR>
     * {@code "SOCLocalizedStrings:type="} + {@link #getType() getType()}
     * + {@code "|flags=0x"} + hex({@link #flags flags})
     * + {@code |strs=str1|str2|str3|...}
     *<P>
     * Null string elements are shown as {@code "(null)"}.
     * If the string list is empty, builds {@code "(strs empty)"} instead.
     *
     * @return a human-readable form of the message
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SOCLocalizedStrings:type=");
        sb.append(pa.get(0));
        sb.append("|flags=0x").append(Integer.toHexString(flags));
        final int L = pa.size();
        if (L < 2)
        {
            sb.append("|(strs empty)");
        } else {
            sb.append("|strs=");
            for (int i = 1; i < L; ++i)
            {
                if (i > 1)
                    sb.append(SOCMessage.sep_char);  // '|'
                final String p = pa.get(i);
                sb.append((p != null) ? p : "(null)");
            }
        }

        return sb.toString();
    }

}
