/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009,2011-2022 Jeremy D Monin <jeremy@nand.net>
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
package soc.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import soc.message.SOCMessage;
import soc.util.SOCFeatureSet;
import soc.util.Version;

/**
 * Game-specific options, configurable at game creation.
 * This class has two purposes:
 *<UL>
 * <LI> Per-game values of options
 * <LI> Static set of Known Options: see {@link SOCGameOptionSet#getAllKnownOptions()} for the current list.
 *</UL>
 * Also handles packing/parsing sets of options to/from Strings.
 *<P>
 * Many static methods expect the caller to pass in their set of Known Options.
 * Before v2.5.00 this class used a static shared copy of those known options, which caused problems when server
 * and robot clients both want to change their "known options" in different ways.
 *<P>
 * For information about adding or changing game options in a
 * later version of JSettlers, please see {@link SOCGameOptionSet#getAllKnownOptions()}.
 *
 *<H3>Naming and referencing Game Options</H3>
 *
 * Each game option has a short unique name key. Most keys are 2 or 3 uppercase letters.
 * Numbers and underscores are also permitted (details below).
 * Before v2.0, the maximum length was 3. The maximum key length is currently 8,
 * but v1.x clients will reject keys longer than 3 or having underscores.
 * Such options must have {@link SOCVersionedItem#minVersion} >= 2000.
 *<P>
 * All in-game code uses those key strings to refer to and change
 * game option settings; only a few places use SOCGameOption
 * objects.  To search the code for uses of a game option, search for
 * its key. You will see calls to {@link SOCGame#isGameOptionDefined(String)},
 * {@link SOCGame#getGameOptionIntValue(String, int, boolean)},
 * {@link SOCGameOptionSet#getOptionIntValue(String)}, etc.
 * Also search {@link SOCScenario} for the option as part of a string,
 * such as <tt>"SBL=t,VP=12"</tt>.
 *
 *<H3>Name and value formats</H3>
 *
 * Option name keys must start with a letter and contain only ASCII uppercase
 * letters ('A' through 'Z') and digits ('0' through '9'), in order to normalize
 * handling and network message formats.  This is enforced in constructors via
 * {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)}.
 * Version 2.0 and newer allow '_'; please check {@link #minVersion},
 * name keys with '_' or longer than 3 characters can't be sent to older clients.
 * Options starting with '_' are meant to be set by the server during game creation,
 * not requested by the client. They're set during
 * {@link SOCGameOptionSet#adjustOptionsToKnown(Map, boolean, SOCFeatureSet) SOCGameOptionSet.adjustOptionsToKnown(getAllKnownOptions(), true, cliFeats)}.
 *<P>
 * For the same reason, option string values (and enum choices) must not contain
 * certain characters or span more than 1 line; this is checked by calling
 * {@link SOCMessage#isSingleLineAndSafe(String)} within constructors and setters.
 *
 *<H3>Known Options and interaction</H3>
 *
 * The "known options" are initialized via {@link SOCGameOptionSet#getAllKnownOptions()}. See that
 * method's description for more details on adding an option.
 * If a new option changes previously expected behavior of the game, it should default to
 * the old behavior; its default value on your server can be changed at runtime.
 *<P>
 * Since 1.1.13, when the user changes options while creating a new game, related
 * options can be changed on-screen for consistency; see {@link SOCGameOption.ChangeListener} for details.
 * If you create a ChangeListener, consider adding equivalent code to
 * {@link SOCGameOptionSet#adjustOptionsToKnown(Map, Map, boolean)} for the server side.
 *
 *<H3>Sea Board Scenarios</H3>
 *
 * Game scenarios were introduced with the large sea board in 2.0.
 * Game options are used to indicate which {@link SOCGameEvent}s, {@link SOCPlayerEvent}s,
 * and rules are possible in the current game.
 * These all start with <tt>"_SC_"</tt> and have a static key string;
 * an example is {@link SOCGameOptionSet#K_SC_SANY} for scenario game option <tt>"_SC_SANY"</tt>.
 *
 *<H3>Inactive Options/Activated Options</H3>
 *
 * Some game options might be useful only for developers or in other special situations,
 * and would only be clutter if they always appeared in every client's New Game Options window.
 * To help with this:
 *<P>
 * An Inactive Option is a Known Option which remains hidden and unused at server and clients which
 * have its definition, and not sent to older clients which don't, as if the option doesn't exist.
 * The server's owner can choose to {@link SOCGameOptionSet#activate(String)} the option during server startup,
 * making it visible and available for games.
 *<P>
 * Added in 2.5.00, also compatible with earlier clients. Example: {@link #K_PLAY_VPO "PLAY_VPO"}.
 *
 *<H3>Third-Party Options</H3>
 *
 * "Third-party" game options can be defined by any 3rd-party client, bot, or server JSettlers fork,
 * as a way to add features or flags but remain backwards-compatible with standard JSettlers;
 * such game opts might not be known by all currently connected clients/servers at the same version.
 * These are defined as having {@link #FLAG_3RD_PARTY} to avoid problems while syncing game option info
 * when clients connect to servers. To use such an option, the client and server must both be
 * from the same third-party source and have its definition. See {@link #FLAG_3RD_PARTY} javadoc for details.
 *<P>
 * Added in 2.5.00, not compatible with earlier clients because they won't have such an option's definition
 * or its required client feature.
 *
 *<H3>Version negotiation</H3>
 *
 * Game options were introduced in 1.1.07; check server, client versions against
 * {@link soc.message.SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
 *<P>
 * Each option has version information, because options can be added or changed
 * with new versions of JSettlers.  Since games run on the server, the server is
 * authoritative about game options:  If the client is newer, it must defer to the
 * server's older set of known options.
 *<P>
 * At client connect, the client compares its JSettlers version number to the server's,
 * and asks for any changes to options if client and server versions differ.
 * The newer server or client calls methods which check Known Options'
 * {@link SOCVersionedItem#minVersion minVersion} and {@link SOCVersionedItem#lastModVersion lastModVersion}.
 * Server also sends any compatible Activated Options.
 * Also if connecting client has limited features, server sends all
 * unsupported game options as unknowns by checking each option's {@link #getClientFeature()}.
 *
 *<H3>I18N</H3>
 *
 * Game option descriptions are also stored as {@code gameopt.*} in
 * {@code server/strings/toClient_*.properties} to be sent to clients if needed
 * during version negotiation.
 *<P>
 * At the client, option's text can be localized with {@link #setDesc(String)}.
 * To help with localizations, that can optionally start with a numeric "sort ranking" which is parsed and removed
 * in v2.6.00 and newer.
 *<P>
 * See unit test {@link soctest.TestI18NGameoptScenStrings} and
 * {@link soc.server.SOCServerMessageHandler#handleGAMEOPTIONGETINFOS(soc.server.genericServer.Connection, soc.message.SOCGameOptionGetInfos) SOCServerMessageHandler.handleGAMEOPTIONGETINFOS(..)}.
 *<P>
 * @author Jeremy D. Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.07
 */
public class SOCGameOption
    extends SOCVersionedItem implements Cloneable, Comparable<Object>
{
    /**
     * {@link #optFlags} bitfield constant to indicate option should be dropped if unset/default.
     * If this option's value is the default, then server should not add it to game options
     * or send over the network (to reduce overhead).
     * Only recommended if game behavior without the option is well-established
     * (for example, trading is allowed unless option NT is present).
     *<P>
     *<b>Details:</b><BR>
     * Should the server drop this option from game options, and not send over
     * the network, if the value is false or blank?
     * (Meaning false (not set) for {@link #OTYPE_BOOL}, {@link #OTYPE_ENUMBOOL}
     * or {@link #OTYPE_INTBOOL}; blank for {@link #OTYPE_STR} or {@link #OTYPE_STRHIDE};
     * {@link #defaultIntValue} for {@link #OTYPE_INT} or {@link #OTYPE_ENUM})
     *<P>
     * Only recommended for seldom-used options.
     * The removal is done in {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)}.
     * Once this flag is set for an option, it should not be un-set if the
     * option is changed in a later version.
     *<P>
     * For {@link #OTYPE_INTBOOL} and {@link #OTYPE_ENUMBOOL}, both the integer and
     * boolean values are checked against defaults.
     *<P>
     * This flag is ignored at the client when asking to create a new game:
     * <tt>NewGameOptionsFrame</tt> sends all options it has displayed, even those
     * which would be dropped because they're unused and they have this flag.
     *<P>
     * Recognized in v1.1.07 and newer.
     * This is the only flag recognized by clients older than v2.0.00.
     * Clients older than v2.0.00 also ignore this flag for {@link #OTYPE_INT}, {@link #OTYPE_ENUM}.
     */
    public static final int FLAG_DROP_IF_UNUSED = 0x01;  // OTYPE_* - mention in javadoc how this applies to the new type

    /**
     * {@link #optFlags} bitfield constant to indicate option is an internal property.
     * Set if the purpose of this option is to hold information about the option's game or its board.
     * The user shouldn't be able to set this option when creating a game,
     * and it should be hidden not shown in the Game Options window during play ({@code NewGameOptionsFrame}).
     *<P>
     * Options with this flag should have a {@link SOCVersionedItem#key key} starting with '_', although not all options
     * which start with '_' are hidden for internal use.  (Options starting with '_' are meant to be set
     * by the server during game creation, not requested by the client.)
     *
     * @since 2.0.00
     */
    public static final int FLAG_INTERNAL_GAME_PROPERTY = 0x02;  // NEW_OPTION - decide if this applies to your option

    /**
     * {@link #optFlags} bitfield constant for an Inactive Option.
     * See {@link SOCGameOption} class javadoc for more about Inactive Options.
     *<P>
     * If an inactive Known Option is activated during server startup by calling
     * {@link SOCGameOptionSet#activate(String)}, it loses this flag and gains {@link #FLAG_ACTIVATED}.
     *
     * @since 2.5.00
     */
    public static final int FLAG_INACTIVE_HIDDEN = 0x04;  // NEW_OPTION - decide if this applies to your option

    /**
     * {@link #optFlags} bitfield constant for a formerly inactive game option ({@link #FLAG_INACTIVE_HIDDEN})
     * which has been activated (made visible) at server by a call to {@link SOCGameOptionSet#activate(String)}.
     * This separate flag bit helps the server tell connecting clients the option is available,
     * as long as their version &gt;= its {@link SOCVersionedItem#minVersion minVersion}.
     *
     * @see SOCGameOptionSet#optionsWithFlag(int, int)
     * @since 2.5.00
     */
    public static final int FLAG_ACTIVATED = 0x08;

    /**
     * {@link #optFlags} bitfield constant for a "third-party" game option defined by
     * a 3rd-party client, bot, or server JSettlers fork, as a way to add features or flags
     * but remain backwards-compatible with standard JSettlers; this game option might not be known by all
     * currently connected clients/servers at the same version.
     *<UL>
     * <LI> Each such game opt requires an accompanying client feature name, so a server
     *      which knows about the opt can easily tell if a client knows it.
     *      See {@link SOCFeatureSet} for more about third-party client features.
     * <LI> A client which knows 3rd-party game opts should ask server about all of them
     *      during game option info sync, to see whether server knows them.
     *      (Client call to {@link SOCGameOptionSet#optionsNewerThanVersion(int, boolean, boolean)} handles this.)
     * <LI> The client feature name can be as long as needed, and named using the same
     *      "reverse DNS" convention as java package names
     * <LI> The game option name key should use {@code '3'} as the second character of
     *      their name key: {@code "_3"}, {@code "T3"}, etc. This avoids a naming conflict,
     *      since built-in options will never have {@code '3'} in that position
     * <LI> Unless the server and client both know the third-party option and its client feature
     *      in their {@link SOCGameOptionSet#getAllKnownOptions()} methods,
     *      it will be ignored/unknown during the game option info synchronization/negotiation
     *      process done when the client connects
     *</UL>
     *
     * @since 2.5.00
     */
    public static final int FLAG_3RD_PARTY = 0x10;

    // -- Option Types --
    // OTYPE_*: See comment above optType for "If you create a new option type"

    /** Lowest OTYPE_ ({@link #optType}) value known at this version */
    public static final int OTYPE_MIN = 0;

    /**
     * Option type: unknown (probably due to version mismatch).
     * Options of this type will also set their {@link SOCVersionedItem#isKnown isKnown} flag false.
     */
    public static final int OTYPE_UNKNOWN = 0;

    /** Option type: boolean  */
    public static final int OTYPE_BOOL = 1;

    /**
     * Option type: integer.
     *<P>
     * In v1.1.20 and newer, while reading values in the NewGameOptionsFrame dialog, a blank textfield is treated as 0.
     * If 0 is out of range, the user will have to enter a valid number.
     */
    public static final int OTYPE_INT = 2;

    /**
     * Option type: integer + boolean.  Both {@link #boolValue} and {@link #intValue} fields are used.
     *<P>
     * In v1.1.20 and newer, while reading values in the NewGameOptionsFrame dialog, a blank int value textfield
     * is treated as 0. If 0 is out of range, the user will have to enter a valid number.
     * If the option's boolean value checkbox isn't set, the int value isn't set or changed.
     */
    public static final int OTYPE_INTBOOL = 3;

    /** Option type: enumeration (1 of several possible choices, described with text strings,
     *  stored here as intVal).  Choices' strings are stored in {@link #enumVals}.
     */
    public static final int OTYPE_ENUM = 4;

    /** Option type: enumeration + boolean; see {@link #OTYPE_ENUM}.
     *  Like {@link #OTYPE_INTBOOL}, both {@link #boolValue} and {@link #intValue} fields are used.
     */
    public static final int OTYPE_ENUMBOOL = 5;

    /** Option type: text string (max string length is option's {@link #maxIntValue}, default value is "") */
    public static final int OTYPE_STR = 6;

    /** Option type: text string (like {@link #OTYPE_STR}) but hidden from view; is NOT encrypted,
     *  but contents show up as "*" when typed into a text field.
     */
    public static final int OTYPE_STRHIDE = 7;

    /** Highest OTYPE value known at this version */
    public static final int OTYPE_MAX = OTYPE_STRHIDE;  // OTYPE_* - adj OTYPE_MAX if adding new type

    // -- End of option types --

    /**
     * Version 2.0.00 introduced longer option keynames (8 characters, earlier max was 3)
     * and underscores '_' in option names.
     * Game option names sent to v1.x.xx servers must be 3 characters or less, alphanumeric, no underscores ('_').
     * @since 2.0.00
     */
    public static final int VERSION_FOR_LONGER_OPTNAMES = 2000;

    /**
     * Maximum possible length of any text-type SOCGameOption's value, to conserve network bandwidth.
     * Checked in option's constructor. Individual SOCGameOptions may choose a shorter max length
     * (stored in {@link #maxIntValue} field).
     *<P>
     * Types: {@link #OTYPE_STR}, {@link #OTYPE_STRHIDE}.
     * @since 2.0.00
     */
    public static final int TEXT_OPTION_MAX_LENGTH = 255;

    /**
     * List of options to refresh on-screen after a change during game creation;
     * filled by {@link #refreshDisplay()}.  Not thread-safe.
     * @see ChangeListener
     * @since 1.1.13
     */
    private static List<SOCGameOption> refreshList;

    // If you create a new option type,
    // please update parseOptionsToMap(), packOptionsToString(),
    // SOCGameOptionSet.adjustOptionsToKnown(), and soc.message.SOCGameOptionGetInfo,
    // and other places.
    // (Search *.java for "// OTYPE_*" to find all locations)

    /** Option type.
     *<UL>
     * <LI> {@link #OTYPE_BOOL} Boolean
     * <LI> {@link #OTYPE_INT}  Integer, with min/max value
     * <LI> {@link #OTYPE_INTBOOL} Int plus bool (Ex. [x] no robber rolls in first _5_ turns)
     * <LI> {@link #OTYPE_ENUM} Enumerated-choice (Ex. Classic vs Seafarers):
     *        Stored like integer {@link #OTYPE_INT} in range 1-n, described to user with text strings.
     * <LI> {@link #OTYPE_ENUMBOOL} Enum plus bool; stored like {@link #OTYPE_INTBOOL}.
     * <LI> {@link #OTYPE_STR} short text string: max string length is {@link #maxIntValue}; default value is the empty string.
     * <LI> {@link #OTYPE_STRHIDE} text string (like {@link #OTYPE_STR}) but hidden from view; is NOT encrypted.
     *</UL>
     */
    public final int optType;  // OTYPE_* - if a new type is added, update this field's javadoc.

    /**
     * Sum of all of option's flags, if any, such as {@link #FLAG_DROP_IF_UNUSED}.
     * @see #hasFlag(int)
     * @since 2.0.00
     */
    public final int optFlags;

    /**
     * Default value for boolean part of this option, if option's {@link #optType} uses that part.
     * @see #copyDefaults(SOCGameOption)
     */
    public final boolean defaultBoolValue;

    /**
     * Default value for integer part of this option, if option's {@link #optType} uses that part.
     * @see #copyDefaults(SOCGameOption)
     */
    public final int defaultIntValue;

    /**
     * Minumum and maximum permitted values for integer part of this option, if any,
     * or maximum length of a string value. (There is no minimum length)
     */
    public final int minIntValue, maxIntValue;

    /**
     * For type {@link #OTYPE_ENUM} and {@link #OTYPE_ENUMBOOL}, descriptive text for each possible value;
     * null for other types.  If a value is added or changed in a later version, the option's
     * {@link #lastModVersion} field must be updated, so server/client will know
     * to ask for the proper version with all available options.
     * Although the option's intVals are in the range 1 to n, this array is indexed 0 to n-1.
     */
    public final String[] enumVals;

    /**
     * Any client feature required to use this game option, or null; see {@link #getClientFeature()} for details.
     * Set at server only.
     * @since 2.4.00
     */
    private String clientFeat;

    private boolean boolValue;
    private int     intValue;

    /** no default value field; "" if unset, stored as null */
    private String  strValue;

    /**
     * The option's ChangeListener, or null.
     * @since 1.1.13
     */
    private transient ChangeListener optCL;

    /**
     * Has the user selected a value?
     * False if unchanged, or if changed only by
     * a {@link ChangeListener} or other automatic means.
     *<P>
     * If a {@link ChangeListener} later changes the
     * option's value, consider clearing <tt>userChanged</tt>
     * because the user hasn't set that.
     *<P>
     * Client use only; not sent over the network.
     * Set in <tt>NewGameOptionsFrame</tt>.
     * @since 1.1.20
     */
    public transient boolean userChanged;

    /**
     * Create a new game option of unknown type ({@link #OTYPE_UNKNOWN}).
     * Minimum version will be {@link Integer#MAX_VALUE}.
     * Value will be false/0. desc will be an empty string.
     * @param key   Alphanumeric short unique key for this option;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     * @throws IllegalArgumentException if key is not alphanumeric or length is > 8;
     *        {@link Throwable#getMessage()} will have details
     */
    public SOCGameOption(final String key)
        throws IllegalArgumentException
    {
        this(OTYPE_UNKNOWN, key, Integer.MAX_VALUE, Integer.MAX_VALUE, false, 0, 0, 0, null, 0, key);
    }

    /**
     * Create a new boolean game option ({@link #OTYPE_BOOL}).
     *
     * @param key     Alphanumeric short unique key for this option;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     *                See {@link SOCGameOption} class javadoc for max length, which depends on {@code minVers}.
     * @param minVers Minimum client version if this option is set (is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default value (true if set, false if not set)
     * @param flags   Option flags such as {@link #FLAG_DROP_IF_UNUSED}, or 0;
     *                Remember that older clients won't recognize some gameoption flags.
     * @param desc    Descriptive brief text, to appear in the options dialog.
     *               <BR>
     *                To help with localizations, can optionally start with a numeric "sort ranking".
     *                If found, that prefix is parsed and removed in v2.6.00 and newer.
     *                Older clients will keep that prefix visible and use it to help sort alphabetically.
     *                See {@link SOCVersionedItem#setDesc(String)} for details.
     *
     * @throws IllegalArgumentException if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}
     *        or its optional "sort ranking" prefix has a bad format,
     *        or if minVers or lastModVers is under 1000 but not -1,
     *        or if flags {@link #FLAG_INACTIVE_HIDDEN} and {@link #FLAG_ACTIVATED} are both set;
     *        {@link Throwable#getMessage()} will have details
     */
    public SOCGameOption(final String key, final int minVers, final int lastModVers,
        final boolean defaultValue, final int flags, final String desc)
        throws IllegalArgumentException
    {
        this(OTYPE_BOOL, key, minVers, lastModVers, defaultValue, 0, 0, 0, null, flags, desc);
    }

    /**
     * Create a new integer game option ({@link #OTYPE_INT}).
     *<P>
     * If {@link #FLAG_DROP_IF_UNUSED} is set, the option will be dropped if == {@link #defaultIntValue}.<BR>
     * Before v2.0.00, there was no dropIfUnused flag for integer options.
     *
     * @param key     Alphanumeric short unique key for this option;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     *                See {@link SOCGameOption} class javadoc for max length, which depends on {@code minVers}.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default int value
     * @param minValue Minimum permissible value
     * @param maxValue Maximum permissible value; the width of the options-dialog
     *                 value field is based on the number of digits in maxValue.
     * @param flags   Option flags such as {@link #FLAG_DROP_IF_UNUSED}, or 0;
     *                Remember that older clients won't recognize some gameoption flags.
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the int value goes.
     *             If no placeholder is found, the value text field appears at left,
     *             like boolean options.
     *            <BR>
     *             To help with localizations, can optionally start with a numeric "sort ranking".
     *             If found, that prefix is parsed and removed in v2.6.00 and newer.
     *             Older clients will keep that prefix visible and use it to help sort alphabetically.
     *             See {@link SOCVersionedItem#setDesc(String)} for details.
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}
     *        or its optional "sort ranking" prefix has a bad format,
     *        or if minVers or lastModVers is under 1000 but not -1,
     *        or if flags {@link #FLAG_INACTIVE_HIDDEN} and {@link #FLAG_ACTIVATED} are both set;
     *        {@link Throwable#getMessage()} will have details
     */
    public SOCGameOption(final String key, final int minVers, final int lastModVers,
        final int defaultValue, final int minValue, final int maxValue,
        final int flags, final String desc)
        throws IllegalArgumentException
    {
        this(OTYPE_INT, key, minVers, lastModVers, false, defaultValue,
             minValue, maxValue, null, flags, desc);
    }

    /**
     * Create a new int+boolean game option ({@link #OTYPE_INTBOOL}).
     * @param key     Alphanumeric short unique key for this option;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     *                See {@link SOCGameOption} class javadoc for max length, which depends on {@code minVers}.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultBoolValue Default value (true if set, false if not set)
     * @param defaultIntValue Default int value, to use if option is set
     * @param minValue Minimum permissible value
     * @param maxValue Maximum permissible value; the width of the options-dialog
     *                 value field is based on the number of digits in maxValue.
     * @param flags   Option flags such as {@link #FLAG_DROP_IF_UNUSED}, or 0;
     *                Remember that older clients won't recognize some gameoption flags.
     * @param desc Descriptive brief text, to appear in the options dialog; should
     *             contain a placeholder character '#' where the int value goes.
     *            <BR>
     *             To help with localizations, can optionally start with a numeric "sort ranking".
     *             If found, that prefix is parsed and removed in v2.6.00 and newer.
     *             Older clients will keep that prefix visible and use it to help sort alphabetically.
     *             See {@link SOCVersionedItem#setDesc(String)} for details.
     * @throws IllegalArgumentException if defaultIntValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}
     *        or its optional "sort ranking" prefix has a bad format,
     *        or if minVers or lastModVers is under 1000 but not -1,
     *        or if flags {@link #FLAG_INACTIVE_HIDDEN} and {@link #FLAG_ACTIVATED} are both set;
     *        {@link Throwable#getMessage()} will have details
     */
    public SOCGameOption(final String key, final int minVers, final int lastModVers,
        final boolean defaultBoolValue, final int defaultIntValue,
        final int minValue, final int maxValue, final int flags, final String desc)
        throws IllegalArgumentException
    {
        this(OTYPE_INTBOOL, key, minVers, lastModVers, defaultBoolValue, defaultIntValue,
             minValue, maxValue, null, flags, desc);
    }

    /**
     * Create a new enumerated game option ({@link #OTYPE_ENUM}).
     * The {@link #minIntValue} will be 1, {@link #maxIntValue} will be enumVals.length.
     *<P>
     * If {@link #FLAG_DROP_IF_UNUSED} is set, the option will be dropped if == {@link #defaultIntValue}.<BR>
     * Before v2.0.00, there was no dropIfUnused flag for enum options.
     *
     * @param key     Alphanumeric short unique key for this option;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     *                See {@link SOCGameOption} class javadoc for max length, which depends on {@code minVers}.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default int value, in range 1 - n (n == number of possible values)
     * @param flags   Option flags such as {@link #FLAG_DROP_IF_UNUSED}, or 0;
     *                Remember that older clients won't recognize some gameoption flags.
     * @param enumVals text to display for each possible choice of this option.
     *                Please see the explanation at {@link SOCGameOptionSet#getAllKnownOptions()} about
     *                changing or adding to enumVals in later versions.
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the enum's popup-menu goes.
     *             If no placeholder is found, the value field appears at left,
     *             like boolean options.
     *            <BR>
     *             To help with localizations, can optionally start with a numeric "sort ranking".
     *             If found, that prefix is parsed and removed in v2.6.00 and newer.
     *             Older clients will keep that prefix visible and use it to help sort alphabetically.
     *             See {@link SOCVersionedItem#setDesc(String)} for details.
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}
     *        or its optional "sort ranking" prefix has a bad format,
     *        or if minVers or lastModVers is under 1000 but not -1,
     *        or if flags {@link #FLAG_INACTIVE_HIDDEN} and {@link #FLAG_ACTIVATED} are both set;
     *        {@link Throwable#getMessage()} will have details
     */
    public SOCGameOption(final String key, final int minVers, final int lastModVers,
        final int defaultValue, final String[] enumVals, final int flags, final String desc)
        throws IllegalArgumentException
    {
        this(OTYPE_ENUM, key, minVers, lastModVers, false, defaultValue,
             1, enumVals.length, enumVals, flags, desc);
    }

    /**
     * Create a new enumerated + boolean game option ({@link #OTYPE_ENUMBOOL}).
     * The {@link #minIntValue} will be 1, {@link #maxIntValue} will be enumVals.length.
     *
     * @param key     Alphanumeric short unique key for this option;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     *                See {@link SOCGameOption} class javadoc for max length, which depends on {@code minVers}.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultBoolValue Default value (true if set, false if not set)
     * @param defaultIntValue Default int value, in range 1 - n (n == number of possible values)
     * @param enumVals text to display for each possible choice of this option
     * @param flags   Option flags such as {@link #FLAG_DROP_IF_UNUSED}, or 0;
     *                Remember that older clients won't recognize some gameoption flags.
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the enum's popup-menu goes.
     *             If no placeholder is found, the value field appears at left,
     *             like boolean options.
     *            <BR>
     *             To help with localizations, can optionally start with a numeric "sort ranking".
     *             If found, that prefix is parsed and removed in v2.6.00 and newer.
     *             Older clients will keep that prefix visible and use it to help sort alphabetically.
     *             See {@link SOCVersionedItem#setDesc(String)} for details.
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}
     *        or its optional "sort ranking" prefix has a bad format,
     *        or if minVers or lastModVers is under 1000 but not -1,
     *        or if flags {@link #FLAG_INACTIVE_HIDDEN} and {@link #FLAG_ACTIVATED} are both set;
     *        {@link Throwable#getMessage()} will have details
     */
    public SOCGameOption(final String key, final int minVers, final int lastModVers, final boolean defaultBoolValue,
        final int defaultIntValue, final String[] enumVals, final int flags, final String desc)
        throws IllegalArgumentException
    {
        this(OTYPE_ENUMBOOL, key, minVers, lastModVers,
             defaultBoolValue, defaultIntValue,
             1, enumVals.length, enumVals, flags, desc);
    }

    /**
     * Create a new text game option ({@link #OTYPE_STR} or {@link #OTYPE_STRHIDE}).
     * The {@link #maxIntValue} field will hold {@code maxLength}.
     * @param key     Alphanumeric short unique key for this option;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     *                See {@link SOCGameOption} class javadoc for max length, which depends on {@code minVers}.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param maxLength   Maximum length, between 1 and 255 ({@link #TEXT_OPTION_MAX_LENGTH})
     *                for network bandwidth conservation
     * @param hideTyping  Should type be {@link #OTYPE_STRHIDE} instead of {@link #OTYPE_STR}?
     * @param flags   Option flags such as {@link #FLAG_DROP_IF_UNUSED}, or 0;
     *                Remember that older clients won't recognize some gameoption flags.
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the text value goes.
     *             If no placeholder is found, the value text field appears at left,
     *             like boolean options.
     *            <BR>
     *             To help with localizations, can optionally start with a numeric "sort ranking".
     *             If found, that prefix is parsed and removed in v2.6.00 and newer.
     *             Older clients will keep that prefix visible and use it to help sort alphabetically.
     *             See {@link SOCVersionedItem#setDesc(String)} for details.
     * @throws IllegalArgumentException if maxLength > {@link #TEXT_OPTION_MAX_LENGTH},
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}
     *        or its optional "sort ranking" prefix has a bad format,
     *        or if minVers or lastModVers is under 1000 but not -1,
     *        or if flags {@link #FLAG_INACTIVE_HIDDEN} and {@link #FLAG_ACTIVATED} are both set;
     *        {@link Throwable#getMessage()} will have details
     */
    public SOCGameOption(final String key, final int minVers, final int lastModVers,
        final int maxLength, final boolean hideTyping, final int flags, final String desc)
        throws IllegalArgumentException
    {
        this( (hideTyping ? OTYPE_STRHIDE : OTYPE_STR),
             key, minVers, lastModVers, false, 0,
             0, maxLength, null, flags, desc);
        if ((maxLength < 1) || (maxLength > TEXT_OPTION_MAX_LENGTH))
            throw new IllegalArgumentException("maxLength");
    }

    /**
     * Create a new game option - common constructor.
     *<P>
     * If {@code key} starts with {@code "_SC_"}, constructor will automatically call
     * {@link #setClientFeature(String) setClientFeature}({@link SOCFeatureSet#CLIENT_SEA_BOARD}).
     * If you need a different client feature instead, or none, call that setter afterwards.
     *
     * @param otype   Option type; use caution, as this is unvalidated against
     *                {@link #OTYPE_MIN} or {@link #OTYPE_MAX}.
     * @param key     Alphanumeric short unique key for this option;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     *                See {@link SOCGameOption} class javadoc for max length, which depends on {@code minVers}.
     * @param minVers Minimum client version for games where this option is set (its boolean field is true), or -1.
     *                If <tt>key</tt> is longer than 3 characters, <tt>minVers</tt> must be at least 2000
     *                ({@link #VERSION_FOR_LONGER_OPTNAMES}).
     *                For {@link #OTYPE_UNKNOWN}, use {@link Integer#MAX_VALUE}.
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultBoolValue Default value (true if set, false if not set)
     * @param defaultIntValue Default int value, to use if option is set
     * @param minValue Minimum permissible value
     * @param maxValue Maximum permissible value; the width of the options-dialog
     *                 value field is based on the number of digits in maxValue.
     * @param enumVals Possible choice texts for {@link #OTYPE_ENUM} or {@link #OTYPE_ENUMBOOL}, or null;
     *                 value(s) must pass same checks as desc.
     * @param flags   Option flags such as {@link #FLAG_DROP_IF_UNUSED}, or 0;
     *                Remember that older clients won't recognize some gameoption flags.
     * @param desc Descriptive brief text, to appear in the options dialog; should
     *             contain a placeholder character '#' where the int value goes.
     *             Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     *            <BR>
     *             To help with localizations, can optionally start with a numeric "sort ranking".
     *             If found, that prefix is parsed and removed in v2.6.00 and newer.
     *             Older clients will keep that prefix visible and use it to help sort alphabetically.
     *             See {@link SOCVersionedItem#setDesc(String)} for details.
     * @throws IllegalArgumentException if defaultIntValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}
     *        or its optional "sort ranking" prefix has a bad format,
     *        or if minVers or lastModVers is under 1000 but not -1,
     *        or if flags {@link #FLAG_INACTIVE_HIDDEN} and {@link #FLAG_ACTIVATED} are both set;
     *        {@link Throwable#getMessage()} will have details
     */
    protected SOCGameOption(int otype, final String key, int minVers, int lastModVers,
        boolean defaultBoolValue, int defaultIntValue,
        final int minValue, final int maxValue,
        final String[] enumVals, final int flags, final String desc)
        throws IllegalArgumentException
    {
        super(key, minVers, lastModVers, (otype != OTYPE_UNKNOWN), desc);
            // super checks against these:
            // (! SOCVersionedItem.isAlphanumericUpcaseAscii(key)) || key.equals("-")
            // (minVers < 1000) && (minVers != -1)
            // (lastModVers < 1000) && (lastModVers != -1)
            // ! SOCMessage.isSingleLineAndSafe(desc)

        // validate & set option properties:
        final int L = key.length();
        if ((L > 3) && ! key.startsWith("DEBUG"))
        {
            if (L > 8)
                throw new IllegalArgumentException("Key length > 8: " + key);
            else if (minVers < VERSION_FOR_LONGER_OPTNAMES)
                throw new IllegalArgumentException("Key length > 3 needs minVers 2000 or newer: " + key);
        }
        if ((minVers < VERSION_FOR_LONGER_OPTNAMES) && key.contains("_"))
            throw new IllegalArgumentException("Key with '_' needs minVers 2000 or newer: " + key);
        if ((flags & (FLAG_ACTIVATED | FLAG_INACTIVE_HIDDEN)) == (FLAG_ACTIVATED | FLAG_INACTIVE_HIDDEN))
            throw new IllegalArgumentException("Can't set both FLAG_ACTIVATED and FLAG_INACTIVE_HIDDEN");

        optType = otype;
        this.defaultBoolValue = defaultBoolValue;
        this.defaultIntValue = defaultIntValue;
        minIntValue = minValue;
        maxIntValue = maxValue;
        optFlags = flags;
        this.enumVals = enumVals;

        if (enumVals != null)
        {
            for (int i = enumVals.length - 1; i>=0; --i)
                if (! SOCMessage.isSingleLineAndSafe(enumVals[i]))
                    throw new IllegalArgumentException("enumVal fails isSingleLineAndSafe");
        }

        // starting values (= defaults)
        boolValue = defaultBoolValue;
        intValue = defaultIntValue;
        strValue = null;
        if ((intValue < minIntValue) || (intValue > maxIntValue))
            throw new IllegalArgumentException("defaultIntValue");

        if (key.startsWith("_SC_"))
            clientFeat = SOCFeatureSet.CLIENT_SCENARIO_VERSION;
    }

    /**
     * Copy constructor for i18n localization of {@link SOCVersionedItem#getDesc() getDesc()}.
     * @param opt  Option to copy
     * @param newDesc  Localized option description, or {@code null} to use {@link SOCVersionedItem#getDesc() getDesc()}
     * @since 2.0.00
     */
    public SOCGameOption(final SOCGameOption opt, final String newDesc)
    {
        this(opt.optType, opt.key, opt.minVersion, opt.lastModVersion,
             opt.defaultBoolValue, opt.defaultIntValue, opt.minIntValue, opt.maxIntValue,
             opt.enumVals, opt.optFlags,
             (newDesc != null) ? newDesc : opt.desc);
        copyMiscFields(opt);
    }

    /**
     * Copy constructor to change {@link #optFlags} value.
     * @param opt  Option to copy
     * @param newFlags  New value for {@link #optFlags}
     * @since 2.5.00
     * @throws IllegalArgumentException if flags {@link #FLAG_INACTIVE_HIDDEN} and {@link #FLAG_ACTIVATED} are both set
     */
    /*package*/ SOCGameOption(final int newFlags, final SOCGameOption opt)
        throws IllegalArgumentException
    {
        this(opt.optType, opt.key, opt.minVersion, opt.lastModVersion,
            opt.defaultBoolValue, opt.defaultIntValue, opt.minIntValue, opt.maxIntValue,
            opt.enumVals, newFlags, opt.desc);
        copyMiscFields(opt);
    }

    /**
     * Copy constructor for enum-valued types ({@link #OTYPE_ENUM}, {@link #OTYPE_ENUMBOOL}),
     * for restricting (trimming) values for a certain client version.
     * @param enumOpt  Option object to copy.  If its <tt>defaultIntValue</tt> is greater than
     *                 <tt>keptEnumVals.length</tt>, the default will be reduced to that.
     * @param keptEnumVals  Enum values to keep; should be a subset of enumOpt.{@link #enumVals}
     *                 containing the first n values of that list.
     * @see #getMaxEnumValueForVersion(String, int)
     * @see SOCGameOptionSet#optionsNewerThanVersion(int, boolean, boolean)
     * @throws NullPointerException  if keptEnumVals is null
     */
    protected SOCGameOption(final SOCGameOption enumOpt, final String[] keptEnumVals)
        throws NullPointerException
    {
        // OTYPE_* - If enum-valued, add to javadoc.
        this(enumOpt.optType, enumOpt.key, enumOpt.minVersion, enumOpt.lastModVersion,
             enumOpt.defaultBoolValue,
             enumOpt.defaultIntValue <= keptEnumVals.length ? enumOpt.defaultIntValue : keptEnumVals.length,
             1, keptEnumVals.length, keptEnumVals, enumOpt.optFlags, enumOpt.desc);
        copyMiscFields(enumOpt);
    }

    /**
     * Copy constructor for int-valued types ({@link #OTYPE_INT}, {@link #OTYPE_INTBOOL}),
     * for restricting (trimming) max value for a certain client version.
     * @param intOpt  Option object to copy.  If its <tt>defaultIntValue</tt> is greater than
     *                <tt>maxIntValue</tt>, the default will be reduced to that.
     * @param maxIntValue  Maximum value to keep, in the copy
     * @see #getMaxIntValueForVersion(String, int)
     * @see SOCGameOptionSet#optionsNewerThanVersion(int, boolean, boolean)
     * @since 1.1.08
     */
    protected SOCGameOption(final SOCGameOption intOpt, final int maxIntValue)
    {
        // OTYPE_* - If int-valued, add to javadoc.
        this(intOpt.optType, intOpt.key, intOpt.minVersion, intOpt.lastModVersion,
             intOpt.defaultBoolValue,
             intOpt.defaultIntValue <= maxIntValue ? intOpt.defaultIntValue : maxIntValue,
             intOpt.minIntValue, maxIntValue,
             null, intOpt.optFlags, intOpt.desc);
        copyMiscFields(intOpt);
    }

    /**
     * If {@code opt}'s {@link #defaultBoolValue} or {@link #defaultIntValue} are different
     * from its current {@link #getBoolValue()} or {@link #getIntValue()},
     * copy to a new {@code SOCGameOption} which sets those default fields from current.
     * Useful at client when server sends game option defaults.
     * @param opt  Option to check defaults and copy if needed, or {@code null} to return {@code null}
     * @return  {@code opt} if defaults are same as current, or a new {@code SOCGameOption} with updated defaults
     * @since 2.5.00
     */
    public static SOCGameOption copyDefaults(SOCGameOption opt)
    {
        if (opt == null)
            return null;

        final boolean currBool = opt.boolValue;
        final int currInt = opt.intValue;
        if ((currBool != opt.defaultBoolValue) || (currInt != opt.defaultIntValue))
        {
            SOCGameOption updatedOpt = new SOCGameOption
                (opt.optType, opt.key, opt.minVersion, opt.lastModVersion,
                 currBool, currInt, opt.minIntValue, opt.maxIntValue, opt.enumVals,
                 opt.optFlags, opt.desc);
            updatedOpt.copyMiscFields(opt);
            opt = updatedOpt;
        }

        return opt;
    }

    /**
     * For copy constructors, copy miscellanous fields into the new object from the previous one.
     * Handles fields which aren't individual parameters of the common constructor: {@link #clientFeat}.
     * @param copyFrom  Option object to copy fields from
     * @since 2.5.00
     */
    private void copyMiscFields(final SOCGameOption copyFrom)
    {
        clientFeat = copyFrom.clientFeat;
    }

    /**
     * Is a value currently stored in this option?
     * Any of:
     *<UL>
     * <LI> {@link #getBoolValue()} true
     * <LI> {@link #getIntValue()} != 0
     * <LI> {@link #getStringValue()} != ""
     *</UL>
     *
     * @return true if any value field is set
     * @since 2.5.00
     */
    public boolean hasValue()
    {
        return (boolValue || (intValue != 0) || ((strValue != null) && ! strValue.isEmpty()));
    }

    /**
     * Is this option set, if this option's type has a boolean component?
     * @return current boolean value of this option
     * @see #hasValue()
     * @see SOCGame#isGameOptionSet(String)
     * @see SOCGameOptionSet#isOptionSet(String)
     */
    public boolean getBoolValue() { return boolValue; }

    public void setBoolValue(boolean v) { boolValue = v; }

    /**
     * This option's integer value, if this option's type has an integer component.
     * @return current integer value of this option
     * @see #hasValue()
     * @see SOCGame#getGameOptionIntValue(String)
     * @see SOCGame#getGameOptionIntValue(String, int, boolean)
     * @see SOCGameOptionSet#getOptionIntValue(String)
     * @see SOCGameOptionSet#getOptionIntValue(String, int, boolean)
     */
    public int getIntValue() { return intValue; }

    /**
     * Set this option's integer value to new value v, or clip to min/max allowed values.
     * @param v set to this value, if it's within min/max for this option
     */
    public void setIntValue(int v)
    {
        if (v < minIntValue)
            intValue = minIntValue;
        else if (v > maxIntValue)
            intValue = maxIntValue;
        else
            intValue = v;
    }

    /**
     * @return current string value of this option, or "" (empty string) if not set.
     * Will not contain newlines or otherwise fail {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @see #hasValue()
     * @see SOCGame#getGameOptionStringValue(String)
     * @see SOCGameOptionSet#getOptionStringValue(String)
     */
    public String getStringValue()
    {
        if (strValue != null)
            return strValue;
        else
            return "";
    }

    /**
     * Set this option's string value to new value v
     * @param v set to this value, or "" (empty string) if null; should
     *          be a simple string (no newlines or control characters).
     *          if v.length > {@link #maxIntValue}, length will be truncated.
     *          Must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char} ('|' or ',').
     * @throws IllegalArgumentException if v contains characters reserved for
     *          message handling: {@link SOCMessage#sep} or
     *          {@link SOCMessage#sep2} ('|' or ','), or is
     *          multi-line or otherwise fails {@link SOCMessage#isSingleLineAndSafe(String)}.
     */
    public void setStringValue(String v)
        throws IllegalArgumentException
    {
        if (v != null)
        {
            final int vl = v.length();
            if (vl == 0)
            {
                v = null;
            } else {
                if (vl > maxIntValue)
                    v = v.substring(0, maxIntValue);
                if (! SOCMessage.isSingleLineAndSafe(v))
                    throw new IllegalArgumentException("new value fails isSingleLineAndSafe");
            }
        }
        strValue = v;
    }

    /**
     * If this game option requires one, its client feature from {@link SOCFeatureSet}.
     * Not set or used at client, or sent over network as part of game option info sync.
     *<P>
     * Third-party options should always have a client feature; see {@link #FLAG_3RD_PARTY}.
     *
     * @return the client feature required to use this game option,
     *     like {@link SOCFeatureSet#CLIENT_SEA_BOARD}, or null if none
     * @see #setClientFeature(String)
     * @see SOCGameOptionSet#optionsNotSupported(SOCFeatureSet)
     * @see SOCGameOptionSet#optionsTrimmedForSupport(SOCFeatureSet)
     * @since 2.4.00
     */
    public String getClientFeature()
    {
        return clientFeat;
    }

    /**
     * Set the client feature required by this game option, if any, at server.
     * A game option can require at most 1 feature in the current implementation,
     * not a {@link SOCFeatureSet} with multiple members.
     *
     * @param clientFeat Feature to require, like {@link SOCFeatureSet#CLIENT_SEA_BOARD}, or {@code null} for none
     * @see #getClientFeature()
     * @since 2.4.00
     */
    public void setClientFeature(final String clientFeat)
    {
        this.clientFeat = clientFeat;
    }

    /**
     * Minimum game version supporting this option, given {@link #minVersion} and the option's current value.
     * The current value of an option can change its minimum version.
     * For example, option {@code "PL"}'s minVersion is -1 for 2- to 4-player games with any client version,
     * but a 5- or 6-player game will need client 1.1.08 or newer.
     * For boolean-valued option types ({@link #OTYPE_BOOL}, {@link #OTYPE_ENUMBOOL} and
     * {@link #OTYPE_INTBOOL}), the minimum
     * value is -1 unless {@link #getBoolValue()} is true (that is, unless the option is set).
     *<P>
     * Occasionally, an older client version supports a new option, but only by changing
     * the value of some other options it recognizes.
     * This method will calculate the minimum client version at which options are unchanged,
     * if <tt>opts</tt> != null.
     *<P>
     * Because this calculation is hardcoded here, the version returned may be too low when
     * called at an older-version client.  The server will let the client know if it's too
     * old to join or create a game due to options.
     *
     * @param opts  If null, return the minimum version supporting this option.
     *               Otherwise, the minimum version at which this option's value isn't changed
     *               (for compatibility) by the presence of other options.
     * @return minimum version, or -1;
     *     same format as {@link Version#versionNumber()}.
     *     If <tt>opts != null</tt>, the returned version will either be -1 or >= 1107
     *     (the first version with game options).
     * @see SOCVersionedItem#itemsMinimumVersion(Map)
     * @see #getMaxEnumValueForVersion(String, int)
     * @see #getMaxIntValueForVersion(String, int)
     */
    @Override
    public int getMinVersion(final Map<?, ? extends SOCVersionedItem> opts)
    {
        // Check for unset/droppable options
        switch (optType)
        {
        case OTYPE_BOOL:      // OTYPE_*: check here if boolean-valued
        case OTYPE_INTBOOL:
        case OTYPE_ENUMBOOL:
            if (! boolValue)
                return -1;  // Option not set: any client version is OK
            break;

        case OTYPE_INT:
        case OTYPE_ENUM:
            if (hasFlag(FLAG_DROP_IF_UNUSED) && (intValue == defaultIntValue))
                return -1;  // Option not set: any client version is OK
            break;

        case OTYPE_STR:
        case OTYPE_STRHIDE:
            if (hasFlag(FLAG_DROP_IF_UNUSED) && ((strValue == null) || (strValue.length() == 0)))
                return -1;  // Option not set: any client version is OK
            break;
        }

        int minVers = minVersion;

        // Any option value checking for minVers is done here.

        if (key.equals("SC"))
        {
            SOCScenario sc = SOCScenario.getScenario(getStringValue());
            if ((sc != null) && (sc.minVersion > minVers))
                minVers = sc.minVersion;
        }

        // None of the other current options change minVers based on their value.

        // NEW_OPTION:
        // If your option changes the minVers based on its current value,
        // check the key and current value and set the appropriate version like "SC" does.
        //
        // ADDITIONAL BACKWARDS-COMPATIBLE CHECK (opts != null):
        // If opts != null, also check if your option is supported in lower versions
        // (backwards-compatible) only with possible changes to its value.  If this
        // situation applies and opts != null, return the lowest client version
        // where you _don't_ need to change the value to be backwards-compatible.
        // (That version is probably the one in which you introduced the game option.)
        // To simplify the server-side code, do not return less than 1107 (the first
        // version with game options) for this backwards-compatible version number
        // unless you are returning -1.
        //
        // EXAMPLE:
        // For clients below 1.1.13, if game option PLB is set, option
        // PL must be changed to 5 or 6 to force use of the 6-player board.
        // For clients 1.1.13 and newer, PLB is recognized at the client,
        // so PL can be less than 5 and still use the 6-player board.
        // So, if PLB is set, and PL > 4, return 1113 because client
        // versions <= 1112 would need PL changed.
        // If PLB is set, and PL <= 4, return 1108 (the first version with a 6-player board).

        // Gameopt "PL" has an ADDITIONAL CHECK, this can be used as sample code for a new option:

        if (key.equals("PL"))
        {
            if ((opts != null) && (intValue <= 4) && opts.containsKey("PLB"))
            {
                // For clients below 1.1.13, if PLB is set,
                // PL must be changed to 5 or 6 to force use of the 6-player board.
                // For clients 1.1.13 and newer, PLB is recognized at the client,
                // so PL can be less than 5 and still use the 6-player board.

                SOCVersionedItem plb = opts.get("PLB");
                if ((plb instanceof SOCGameOption) && ((SOCGameOption) plb).boolValue)
                    return 1113;
            }

            if (intValue > 4)
                return 1108;  // 5 or 6 players
        }

        return minVers;
    }

    /**
     * For use at server, for enum options where some values require a newer client version.
     * Given the option's keyname and a version, what is the maximum permitted enum value?
     * The server, when giving option info to a connecting client, can remove the too-new values,
     * and send only the permitted values to an older client.
     *
     * @param optKey Option's keyname
     * @param vers   Version of client, same format as {@link soc.message.SOCVersion#getVersionNumber()}
     *               and {@link Version#versionNumber()}
     * @return  Maximum permitted value for this version, or {@link Integer#MAX_VALUE}
     *          if this option has no restriction.
     *          Enum values range from 1 to n, not from 0 to n-1.
     */
    public static final int getMaxEnumValueForVersion(final String optKey, final int vers)
    {
        // SAMPLE CODE:
        /*
        if (optKey.equals("DEBUGENUMBOOL"))
        {
            if (vers >= 1108)
                return 4;
            else
                return 2;
        }
        */
        // END OF SAMPLE CODE.

        return Integer.MAX_VALUE;
    }

    /**
     * For use at server, for int options where some values require a newer client version.
     * For example, versions below 1.1.08 limit "max players" to 4.
     * Given the option's keyname and a version, what is the maximum permitted int value?
     * The server, when giving option info to a connecting client, can remove the too-new values.
     *
     * @param optKey Option's keyname
     * @param vers   Version of client, same format as {@link soc.message.SOCVersion#getVersionNumber()}
     *               and {@link Version#versionNumber()}
     * @return  Maximum permitted value for this version, or {@link Integer#MAX_VALUE}
     *          if this option has no restriction.
     * @since 1.1.08
     * @see SOCGameOptionSet#optionsTrimmedForSupport(SOCFeatureSet)
     */
    public static final int getMaxIntValueForVersion(final String optKey, final int vers)
    {
        if (optKey.equals("PL"))  // Max players
        {
            if (vers >= 1108)
                return Integer.MAX_VALUE;
            else
                return 4;
        }

        return Integer.MAX_VALUE;
    }

    /**
     * Utility - build a string of option name-value pairs from the Known Options' current values.
     *
     * @param knownOpts  Known Options, from {@link SOCGameOptionSet#getAllKnownOptions()}; not null
     * @param hideEmptyStringOpts omit string-valued options which are empty?
     *            Suitable only for sending defaults.
     * @param hideLongNameOpts omit options with long key names or underscores?
     *            Set true if client's version &lt; {@link #VERSION_FOR_LONGER_OPTNAMES}.
     * @return string of name-value pairs, same format as
     *         {@link #packOptionsToString(Map, boolean, boolean) packOptionsToString(Map, hideEmptyStringOpts, false)};
     *         any gameoptions of {@link #OTYPE_UNKNOWN} will not be
     *         part of the string.
     * @see #parseOptionsToMap(String, SOCGameOptionSet)
     * @see #parseOptionsToSet(String, SOCGameOptionSet)
     */
    public static String packKnownOptionsToString
        (final SOCGameOptionSet knownOpts, final boolean hideEmptyStringOpts, final boolean hideLongNameOpts)
    {
        return packOptionsToString(knownOpts.getAll(), hideEmptyStringOpts, false, (hideLongNameOpts) ? -3 : -2);
    }

    /**
     * Utility - build a string of option name-value pairs.
     * This can be unpacked with {@link #parseOptionsToMap(String, SOCGameOptionSet)}
     * or {@link #parseOptionsToSet(String, SOCGameOptionSet)}.
     *<P>
     * For sending options to a client, use {@link #packOptionsToString(Map, boolean, boolean, int)} instead.
     *
     * @param omap  Map of SOCGameOptions, or null
     * @param hideEmptyStringOpts omit string-valued options which are empty?
     *            Suitable only for sending defaults.
     * @param sortByKey  If true, sort the options by {@link SOCVersionedItem#key}
     *            (using {@link String#compareTo(String)}) to make the returned string stable and canonical
     * @return string of name-value pairs, or "-" for an empty or null {@code omap};
     *     any gameoptions of {@link #OTYPE_UNKNOWN} will not be part of the string.
     *     Also skips any option which has {@link #FLAG_INACTIVE_HIDDEN}.
     *     <P>
     *     Format: k1=t,k2=f,k3=10,k4=t7,k5=f7. <BR>
     *     Pair separator is the ordinary comma character {@link SOCMessage#sep2_char}.
     *     <P>
     *     The format for each value depends on its type:
     *     <UL>
     *       <LI> OTYPE_BOOL: t or f
     *       <LI> OTYPE_ENUM: int in range 1-n
     *       <LI> OTYPE_INTBOOL: t or f followed immediately by int value, as in: t7 or f9
     *       <LI> All other optTypes: int value or string value, as appropriate
     *     </UL>
     *
     * @see #parseOptionNameValue(String, boolean, SOCGameOptionSet)
     * @see #parseOptionNameValue(String, String, boolean, SOCGameOptionSet)
     * @see #packValue(StringBuilder)
     */
    public static String packOptionsToString
        (final Map<String, SOCGameOption> omap, boolean hideEmptyStringOpts, final boolean sortByKey)
    {
        return packOptionsToString(omap, hideEmptyStringOpts, sortByKey, -2);
    }

    /**
     * Utility - build a string of option name-value pairs,
     * adjusting for old clients if necessary.
     * This can be unpacked with {@link #parseOptionsToMap(String, SOCGameOptionSet)}
     * or {@link #parseOptionsToSet(String, SOCGameOptionSet)}.
     * See {@link #packOptionsToString(Map, boolean, boolean)} javadoc for details.
     *
     * @param omap  Map of SOCGameOptions, or null
     * @param hideEmptyStringOpts omit string-valued options which are empty?
     *            Suitable only for sending defaults.
     * @param sortByKey  If true, sort the options by {@link SOCVersionedItem#key}
     *            (using {@link String#compareTo(String)}) to make the returned string stable and canonical
     * @param cliVers  Client version; assumed >= {@link soc.message.SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
     *            If any game's options need adjustment for an older client, cliVers triggers that.
     *            Use -2 if the client version doesn't matter, or if adjustment should not be done.
     *            Use -3 to omit options with long names, and do no other adjustment;
     *               for use with clients older than {@link SOCGameOption#VERSION_FOR_LONGER_OPTNAMES}.
     * @return string of name-value pairs, or "-" for an empty or null omap;
     *         see {@link #packOptionsToString(Map, boolean, boolean)} javadoc for details.
     * @see #packValue(StringBuilder)
     */
    public static String packOptionsToString
        (final Map<String, SOCGameOption> omap, boolean hideEmptyStringOpts, final boolean sortByKey, final int cliVers)
    {
        if ((omap == null) || omap.size() == 0)
            return "-";

        // If the "PLB" option is set, old client versions
        //  may need adjustment of the "PL" option.
        final boolean hasOptPLB = (cliVers > -2) && omap.containsKey("PLB")
            && omap.get("PLB").boolValue;

        // Pack all non-unknown options:
        StringBuilder sb = new StringBuilder();
        boolean hadAny = false;
        Collection<SOCGameOption> opts = omap.values();
        if (sortByKey)
        {
            ArrayList<SOCGameOption> olist = new ArrayList<>(opts);
            Collections.sort(olist, new Comparator<SOCGameOption>()
            {
                public int compare(SOCGameOption a, SOCGameOption b)
                {
                    return a.key.compareTo(b.key);
                }
            });
            opts = olist;
        }
        for (SOCGameOption op : opts)
        {
            if (op.optType == OTYPE_UNKNOWN)
                continue;  // <-- Skip this one --
            if (hideEmptyStringOpts
                && ((op.optType == OTYPE_STR) || (op.optType == OTYPE_STRHIDE))  // OTYPE_* - add here if string-valued
                && op.getStringValue().length() == 0)
                    continue;  // <-- Skip this one --
            if ((cliVers == -3) && ((op.key.length() > 3) || op.key.contains("_")))
                continue;  // <-- Skip this one -- (VERSION_FOR_LONGER_OPTNAMES)
            if (op.hasFlag(FLAG_INACTIVE_HIDDEN))
                continue;

            if (hadAny)
                sb.append(SOCMessage.sep2_char);
            else
                hadAny = true;
            sb.append(op.key);
            sb.append('=');

            boolean wroteValueAlready = false;
            if (cliVers > -2)
            {
                if (hasOptPLB && op.key.equals("PL")
                    && (cliVers < 1113) && (op.intValue < 5))
                {
                    // When "PLB" is used (Use 6-player board)
                    // but the client is too old to recognize PLB,
                    // make sure "PL" is large enough to make the
                    // client use that board.

                    sb.append('5');  // big enough for 6-player
                    wroteValueAlready = true;
                }

                // NEW_OPTION - Check your option vs old clients here.
            }

            if (! wroteValueAlready)
                op.packValue(sb);
        }

        return sb.toString();
    }

    /**
     * Pack current value of this option into a string.
     * This is used in {@link #packOptionsToString(Map, boolean, boolean)} and
     * read in {@link #parseOptionNameValue(String, boolean, SOCGameOptionSet)}
     * and {@link #parseOptionsToMap(String, SOCGameOptionSet)} / {@link #parseOptionsToSet(String, SOCGameOptionSet)}.
     * See {@link #packOptionsToString(Map, boolean, boolean)} for the string's format.
     *
     * @param sb Pack into (append to) this buffer
     * @eee #getPackedValue()
     * @see #toString()
     */
    public void packValue(StringBuilder sb)
    {
        switch (optType)  // OTYPE_* - update this switch, and javadoc of packOptionsToString and of parseOptionNameValue.
        {                 //           The format produced must match that expected in parseOptionNameValue.
        case OTYPE_BOOL:
            sb.append(boolValue ? 't' : 'f');
            break;

        case OTYPE_INT:
        case OTYPE_ENUM:
            sb.append(intValue);
            break;

        case OTYPE_INTBOOL:
        case OTYPE_ENUMBOOL:
            sb.append(boolValue ? 't' : 'f');
            sb.append(intValue);
            break;

        case OTYPE_STR:
        case OTYPE_STRHIDE:
            if (strValue != null)
                sb.append(strValue);  // value is checked in setter vs SEP, SEP2
            break;

        default:
            sb.append ('?');  // Shouldn't happen
        }
    }

    /**
     * Utility - build a map of keys to {@link SOCGameOption}s by parsing a list of option name-value pairs.
     * For each pair in {@code ostr}, calls
     * {@link #parseOptionNameValue(String, boolean, SOCGameOptionSet) parseOptionNameValue(pair, false, knownOpts)}.
     *<P>
     * Before v2.0.00, this was {@code parseOptionsToHash}.
     *
     * @param ostr string of name-value pairs, as created by
     *             {@link #packOptionsToString(Map, boolean, boolean)}.
     *             A leading comma is OK (possible artifact of StringTokenizer
     *             coming from over the network).
     *             If ostr is "-", returned map will be null.
     * @param knownOpts  all Known Options
     * @return map of SOCGameOptions, or null if ostr is null or empty ("-")
     *         or malformed.  Any unrecognized options
     *         will be in the map as type {@link #OTYPE_UNKNOWN}.
     *         The returned known SGOs are clones from the set of all known options.
     * @see #parseOptionsToSet(String, SOCGameOptionSet)
     * @see #parseOptionNameValue(String, boolean, SOCGameOptionSet)
     * @see #parseOptionNameValue(String, String, boolean, SOCGameOptionSet)
     * @throws IllegalArgumentException if any game option keyname in {@code ostr} is unknown
     *     and not a valid alphanumeric keyname by the rules listed at {@link #SOCGameOption(String)}
     */
    public static Map<String,SOCGameOption> parseOptionsToMap(final String ostr, final SOCGameOptionSet knownOpts)
    {
        if ((ostr == null) || ostr.equals("-"))
            return null;

        HashMap<String, SOCGameOption> ohash = new HashMap<>();

        StringTokenizer st = new StringTokenizer(ostr, SOCMessage.sep2);
        String nvpair;
        while (st.hasMoreTokens())
        {
            nvpair = st.nextToken();  // skips any leading commas or doubled commas
            SOCGameOption copyOpt = parseOptionNameValue(nvpair, false, knownOpts);
            if (copyOpt == null)
                return null;  // parse error
            ohash.put(copyOpt.key, copyOpt);
        }  // while (moreTokens)

        return ohash;
    }

    /**
     * Utility - build a set of {@link SOCGameOption}s by parsing a list of option name-value pairs.
     * For each pair in {@code ostr}, calls
     * {@link #parseOptionNameValue(String, boolean, SOCGameOptionSet) parseOptionNameValue(pair, false, knownOpts)}.
     *
     * @param ostr string of name-value pairs, as created by {@link #packOptionsToString(Map, boolean, boolean)}.
     *     A leading comma is OK (possible artifact of StringTokenizer coming from over the network).
     *     If ostr is "-", returned map will be null.
     * @param knownOpts  all Known Options
     * @return set of SOCGameOptions, or null if ostr is null or empty ("-") or malformed.
     *     Any unrecognized options will be in the map as type {@link #OTYPE_UNKNOWN}.
     *     The returned known SGOs are clones from the set of all known options.
     * @see #parseOptionsToMap(String, SOCGameOptionSet)
     * @see #parseOptionNameValue(String, boolean, SOCGameOptionSet)
     * @see #parseOptionNameValue(String, String, boolean, SOCGameOptionSet)
     * @throws IllegalArgumentException if any game option keyname in {@code ostr} is unknown
     *     and not a valid alphanumeric keyname by the rules listed at {@link #SOCGameOption(String)}
     * @since 2.5.00
     */
    public static SOCGameOptionSet parseOptionsToSet(final String ostr, final SOCGameOptionSet knownOpts)
    {
        final Map<String, SOCGameOption> omap = parseOptionsToMap(ostr, knownOpts);
        if (omap == null)
            return null;

        return new SOCGameOptionSet(omap);
    }

    /**
     * Utility - parse a single name-value pair produced by packOptionsToString.
     * Expected format of nvpair: "optname=optvalue".
     * Expected format of optvalue depends on its type.
     * See {@link #packOptionsToString(Map, boolean, boolean)} for the format.
     *
     * @param nvpair Name-value pair string, as created by
     *               {@link #packOptionsToString(Map, boolean, boolean)}.
     *               <BR>
     *               'T', 't', 'Y', 'y' are always allowed for bool true values, regardless of {@code forceNameUpcase}.
     *               'F', 'f', 'N', 'n' are the valid bool false values.
     * @param forceNameUpcase Call {@link String#toUpperCase()} on keyname within nvpair?
     *               For friendlier parsing of manually entered (command-line) nvpair strings.
     * @param knownOpts  All Known Options
     * @return Parsed option, or null if parse error;
     *         if known, the returned object is a clone of the SGO from the set of all known options.
     *         if nvpair's option keyname is not a known option, returned optType will be {@link #OTYPE_UNKNOWN}.
     * @throws IllegalArgumentException if {@code optkey} is unknown and not a valid alphanumeric keyname
     *         by the rules listed at {@link #SOCGameOption(String)}
     * @see #parseOptionNameValue(String, String, boolean, SOCGameOptionSet)
     * @see #parseOptionsToMap(String, SOCGameOptionSet)
     * @see #parseOptionsToSet(String, SOCGameOptionSet)
     * @see #packValue(StringBuilder)
     */
    public static SOCGameOption parseOptionNameValue
        (final String nvpair, final boolean forceNameUpcase, final SOCGameOptionSet knownOpts)
        throws IllegalArgumentException
    {
        int i = nvpair.indexOf('=');  // don't just tokenize for this (efficiency, and param value may contain a "=")
        if (i < 1)
            return null;  // malformed

        String optkey = nvpair.substring(0, i);
        String optval = nvpair.substring(i+1);
        return parseOptionNameValue(optkey, optval, forceNameUpcase, knownOpts);
    }

    /**
     * Utility - parse an option name-value pair produced by {@link #packValue(StringBuilder)} or
     * {@link #packOptionsToString(Map, boolean, boolean)}. Expected format of {@code optval} depends on its type.
     * See {@code packOptionsToString(..)} for the format.
     *
     * @param optkey  Game option's alphanumeric keyname, known or unknown.
     *               Optkey must be a valid key by the rules listed at {@link #SOCGameOption(String)}.
     * @param optval  Game option's value, as created by {@link #packOptionsToString(Map, boolean, boolean)}.
     *               <BR>
     *               'T', 't', 'Y', 'y' are always allowed for bool true values, regardless of {@code forceNameUpcase}.
     *               'F', 'f', 'N', 'n' are the valid bool false values.
     * @param forceNameUpcase  Call {@link String#toUpperCase()} on {@code optkey}?
     *               For friendlier parsing of manually entered (command-line) name=value pairs.
     * @param knownOpts  All Known Options
     * @return Parsed option, or null if parse error;
     *         if known, the returned object is a clone of the SGO from the set of all known options.
     *         if {@code optkey} is not a known option, returned optType will be {@link #OTYPE_UNKNOWN}.
     * @throws IllegalArgumentException if {@code optkey} is unknown and not a valid alphanumeric keyname
     *         by the rules listed at {@link #SOCGameOption(String)}; {@link Throwable#getMessage()} will have details
     * @see #parseOptionNameValue(String, boolean, SOCGameOptionSet)
     * @see #parseOptionsToMap(String, SOCGameOptionSet)
     * @see #parseOptionsToSet(String, SOCGameOptionSet)
     * @see #packValue(StringBuilder)
     * @since 2.0.00
     */
    public static SOCGameOption parseOptionNameValue
        (String optkey, final String optval, final boolean forceNameUpcase, final SOCGameOptionSet knownOpts)
        throws IllegalArgumentException
    {
        if (forceNameUpcase)
            optkey = optkey.toUpperCase();

        SOCGameOption knownOpt = knownOpts.get(optkey);
        SOCGameOption copyOpt;
        if (knownOpt == null)
        {
            copyOpt = new SOCGameOption(optkey);  // OTYPE_UNKNOWN; may throw IllegalArgumentException
        }
        else
        {
            if ((optval.length() == 0)
                    && (knownOpt.optType != OTYPE_STR)  // OTYPE_* - if string-type, add here
                    && (knownOpt.optType != OTYPE_STRHIDE))
            {
                return null;  // malformed: no value
            }
            try
            {
                copyOpt = (SOCGameOption) knownOpt.clone();
            } catch (CloneNotSupportedException ce)
            {
                return null;
            }

            switch (copyOpt.optType)  // OTYPE_* - update this switch, must match format produced
            {                         //           in packValue / packOptionsToString
            case OTYPE_BOOL:
                if (optval.length() == 1)
                {
                    final boolean bv;
                    switch (optval.charAt(0))
                    {
                    case 't':  // fall through
                    case 'T':
                    case 'y':
                    case 'Y':
                        bv = true;
                        break;

                    case 'f':  // fall through
                    case 'F':
                    case 'n':
                    case 'N':
                        bv = false;
                        break;

                    default:
                        return null;  // malformed
                    }
                    copyOpt.setBoolValue(bv);
                } else {
                    return null;  // malformed
                }
                break;

            case OTYPE_INT:
            case OTYPE_ENUM:
                try
                {
                    copyOpt.setIntValue(Integer.parseInt(optval));
                } catch (NumberFormatException e)
                {
                    return null;  // malformed
                }
                break;

            case OTYPE_INTBOOL:
            case OTYPE_ENUMBOOL:
                try
                {
                    final boolean bv;
                    switch (optval.charAt(0))
                    {
                    case 't':  // fall through
                    case 'T':
                    case 'y':
                    case 'Y':
                        bv = true;
                        break;

                    case 'f':  // fall through
                    case 'F':
                    case 'n':
                    case 'N':
                        bv = false;
                        break;

                    default:
                        return null;  // malformed
                    }
                    copyOpt.setBoolValue(bv);
                    copyOpt.setIntValue(Integer.parseInt(optval.substring(1)));
                } catch (NumberFormatException e)
                {
                    return null;  // malformed
                }
                break;

            case OTYPE_STR:
            case OTYPE_STRHIDE:
                copyOpt.setStringValue(optval);
                break;

            default:
                copyOpt = new SOCGameOption(optkey);  // OTYPE_UNKNOWN
            }
        }

        return copyOpt;
    }

    /**
     * Copy this option and restrict its enumerated values (type {@link #OTYPE_ENUM} or similar)
     * by trimming {@link #enumVals} shorter.
     * Assumes {@link #getMaxEnumValueForVersion(String, int)} indicates this is needed.
     * @param opt Option to restrict
     * @param vers Version to restrict to
     * @return   A copy of the option, containing only the enum
     *       values permitted at <tt>vers</tt>.
     *       If no restriction is needed, return <tt>opt</tt>.
     */
    public static SOCGameOption trimEnumForVersion(final SOCGameOption opt, final int vers)
    {
        final int ev = getMaxEnumValueForVersion(opt.key, vers);
        if ((ev == Integer.MAX_VALUE) || (ev == opt.enumVals.length))
            return opt;
        String[] evkeep = new String[ev];
        System.arraycopy(opt.enumVals, 0, evkeep, 0, ev);
        return new SOCGameOption(opt, evkeep);  // Copy option and restrict enum values
    }

    /**
     * For user output, the string name of the option type's constant.
     * The prefix "OTYPE_" is omitted.
     * @param optType An option's {@link #optType} value
     * @return String for this otype constant, such as "INTBOOL" or "UNKNOWN",
     *         or null if optType is outside the known type value range.
     */
    public static String optionTypeName(final int optType)
    {
        String otname;
        switch (optType)  // OTYPE_*
        {
        case OTYPE_UNKNOWN:
            otname = "UNKNOWN";  break;

        case OTYPE_BOOL:
            otname = "BOOL";  break;

        case OTYPE_INT:
            otname = "INT";  break;

        case OTYPE_INTBOOL:
            otname = "INTBOOL";  break;

        case OTYPE_ENUM:
            otname = "ENUM";  break;

        case OTYPE_ENUMBOOL:
            otname = "ENUMBOOL";  break;

        case OTYPE_STR:
            otname = "STR";  break;

        case OTYPE_STRHIDE:
            otname = "STRHIDE"; break;

        default:
            otname = null;
        }
        return otname;
    }

    /**
     * Get the list of {@link SOCGameOption}s whose {@link #refreshDisplay()}
     * methods have been called, and clear the internal static copy.
     * Not thread-safe, assumes only 1 GUI thread or 1 NewGameOptionsFrame at a time.
     * @return the list, or null if refreshDisplay wasn't called on any option
     * @since 1.1.13
     */
    public static final List<SOCGameOption> getAndClearRefreshList()
    {
        List<SOCGameOption> refr = refreshList;
        refreshList = null;
        if ((refr != null) && (refr.size() == 0))
            refr = null;

        return refr;
    }

    /**
     * If this game option is displayed on-screen, refresh it;
     * call this after changing the value.
     *<P>
     * Should be called when value has changed programatically through setters or other methods,
     * not when user has changed the value on-screen in client GUI code.
     *
     * @since 1.1.13
     */
    public void refreshDisplay()
    {
        if (refreshList == null)
            refreshList = new ArrayList<SOCGameOption>();
        else if (refreshList.contains(this))
            return;

        refreshList.add(this);
    }

    /**
     * Does this game option have these specified flag(s)?
     * @param flagMask  Option flag such as {@link #FLAG_DROP_IF_UNUSED}, or multiple flags or'd together
     * @return  True if {@link #optFlags} contains all flags in {@code flagMask}
     * @since 2.0.00
     */
    public final boolean hasFlag(final int flagMask)
    {
        return ((optFlags & flagMask) == flagMask);
    }

    /**
     * Add or remove this option's change listener.
     * Does not support multiple listeners, so if it's already set, it will
     * be changed to the new value.
     * @param cl  Change listener to add, or null to remove.
     * @see #hasChangeListener()
     * @since 1.1.13
     */
    public void addChangeListener(ChangeListener cl)
    {
        optCL = cl;
    }

    /**
     * Does this option have a non-null {@link ChangeListener}?
     * @see #getChangeListener()
     * @since 1.1.13
     */
    public boolean hasChangeListener()
    {
        return (optCL != null);
    }

    /**
     * Get this option's {@link ChangeListener}, if any
     * @return the changelistener, or null if none
     * @see #hasChangeListener()
     * @see #addChangeListener(ChangeListener)
     * @since 1.1.13
     */
    public ChangeListener getChangeListener()
    {
        return optCL;
    }

    /**
     * Form a string with the key and current value, useful for debugging purposes.
     * @return string such as "PL=4" or "BC=t3", with the same format
     *    as {@link #packKnownOptionsToString(SOCGameOptionSet, boolean, boolean)}
     * @see #packValue(StringBuilder)
     * @see #getPackedValue()
     * @see #optionTypeName(int)
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder(key);
        sb.append('=');
        packValue(sb);
        return sb.toString();
    }

    /**
     * Form a StringBuilder containing the current value. This is a convenience method.
     * @return stringbuffer such as "4" or "t3", with the same value format
     *    as {@link #packKnownOptionsToString(SOCGameOptionSet, boolean, boolean)}
     * @since 1.1.20
     * @see #packValue(StringBuilder)
     * @see #toString()
     */
    public final StringBuilder getPackedValue()
    {
        StringBuilder sb = new StringBuilder();
        packValue(sb);
        return sb;
    }

    /**
     * Compare two options, for display purposes. ({@link Comparable} interface)
     * Two gameoptions are considered equal if they have the same {@link SOCVersionedItem#key key}.
     * Greater/lesser is determined from {@link SOCVersionedItem#getSortRank()} and if that's equal,
     * {@link SOCVersionedItem#getDesc() desc}.{@link String#toLowerCase() toLowercase()}.{@link String#compareTo(String) compareTo(otherDesc.toLowercase())}.
     * @param other A SOCGameOption to compare, or another object;  if other isn't a
     *              gameoption, the {@link #hashCode()}s are compared.
     * @see #equals(Object)
     */
    public int compareTo(Object other)
    {
        if (other instanceof SOCGameOption)
        {
            SOCGameOption oopt = (SOCGameOption) other;
            if (key.equals(oopt.key))
                return 0;

            final int rankA = this.getSortRank(), rankB = oopt.getSortRank();
            if (rankA < rankB)
                return -1;
            else if (rankA > rankB)
                return 1;

            return desc.toLowerCase().compareTo(oopt.desc.toLowerCase());
        } else {
            return hashCode() - other.hashCode();
        }
    }

    /**
     * Check for equality to another {@link SOCGameOption} or other object.
     *
     * @return true if {@code other} is a {@link SOCGameOption} having the same {@link #key},
     *     {@link #optType}, {@link #optFlags}, {@link #getBoolValue()},
     *     {@link #getIntValue()}, and {@link #getStringValue()} as this option.
     * @see Object#equals(Object)
     * @see #compareTo(Object)
     * @since 2.5.00
     */
    public boolean equals(final Object other)
    {
        if (! (other instanceof SOCGameOption))
            return false;

        final SOCGameOption oopt = (SOCGameOption) other;
        return (optType == oopt.optType) && (optFlags == oopt.optFlags) && key.equals(oopt.key)
            && (boolValue == oopt.boolValue) && (intValue == oopt.intValue)
            && ((strValue == null)
                ? (oopt.strValue == null)
                : strValue.equals(oopt.strValue));
    }

    /**
     * Call {@link Object#clone()}; added here for access by {@link SOCGameOptionSet}.
     * @since 2.5.00
     */
    @Override
    protected Object clone()
        throws CloneNotSupportedException
    {
        return super.clone();
    }

    /**
     * Listener for option value changes <em>at the client</em> during game creation.
     * When the user changes an option, allows a related option to change.
     * For example, when the max players is changed to 5 or 6,
     * the listener can check the box for "use 6-player board".
     *<P>
     * Once written, a newer server can't do anything to update an older client's
     * ChangeListener code, so be careful and write them defensively.
     *<P>
     * Callback method is {@link #valueChanged(SOCGameOption, Object, Object, SOCGameOptionSet, SOCGameOptionSet)}.
     * Called from <tt>NewGameOptionsFrame</tt>.
     *<P>
     * For <em>server-side</em> consistency adjustment of values before creating games,
     * add code to {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)}
     * that's equivalent to your ChangeListener.
     *
     * @see SOCGameOption#addChangeListener(ChangeListener)
     * @since 1.1.13
     */
    public interface ChangeListener
    {
        /**
         * Called when the user changes <tt>opt</tt>'s value during game creation.
         * Not called when a game option's setters are called.
         *<P>
         * If you change any other <tt>currentOpts</tt> values,
         * be sure to call {@link SOCGameOption#refreshDisplay()} to update the GUI.
         * Do not call <tt>refreshDisplay()</tt> on <tt>opt</tt>.
         *<P>
         * Remember that the related option may not be present in <tt>currentOpts</tt>
         * because different servers may have different default options, or different versions.
         *<P>
         * Write this method carefully, because the server can't update a client's
         * buggy version.  Although calls to this method are surrounded by a try/catch({@link Throwable})
         * block, a bug-free version is best for the user.
         *<P>
         * For {@link SOCGameOption#OTYPE_STR} or <tt>OTYPE_STRHIDE</tt>, this method is
         * called once for each character entered or deleted.
         *<P>
         * For {@link SOCGameOption#OTYPE_INT} or <tt>OTYPE_INTBOOL</tt>, this method is
         * called once for each digit typed or deleted, so long as the resulting number
         * is parsable and within the min/max range for the option.
         *<P>
         * For {@link SOCGameOption#OTYPE_ENUMBOOL} or <tt>OTYPE_INTBOOL</tt>, if both the
         * boolean and int value fields are set at once, then after updating both fields,
         * <tt>valueChanged</tt> will be called twice (boolean and then int).
         * If the boolean value is cleared to false, there won't be a second call for the
         * int value, because when cleared the game option has no effect.
         *
         * @param opt  Option that has changed, already updated to new value
         * @param oldValue  Old value; an Integer, Boolean, or String.
         * @param newValue  New value; always the same class as <tt>oldValue</tt>
         * @param currentOpts  The current value of all {@link SOCGameOption}s in this set
         * @param knownOpts  All of server's Known Options
         */
        public void valueChanged
            (final SOCGameOption opt, final Object oldValue, final Object newValue,
             final SOCGameOptionSet currentOpts, final SOCGameOptionSet knownOpts);
    }

}
