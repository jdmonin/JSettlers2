/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009 Jeremy Monin <jeremy@nand.net>
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
package soc.game;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import soc.message.SOCMessage;

/**
 * Game-specific options, configurable at game creation.
 * This class has two purposes:
 *<UL>
 * <LI> Per-game values of options
 * <LI> Static dictionary of known options;
 *    see {@link #initAllOptions()} for the current list.
 *</UL>
 * All in-game code uses 2-letter key strings to query and change
 * game option settings; only a very few places use SOCGameOption
 * objects.  To search the code for uses of a game option, search for
 * its capitalized key string.
 *<P>
 * Option name keys must start with a letter and contain only ASCII uppercase
 * letters ('A' through 'Z') and digits ('0' through '9'), in order to normalize
 * handling and network message formats.  This is enforced in constructors via
 * {@link #isAlphanumericUpcaseAscii(String)}.
 *<P>
 * For the same reason, option string values (and enum choices) must not contain
 * certain characters or span more than 1 line; this is checked by calling
 * {@link SOCMessage#isSingleLineAndSafe(String)} within constructors and setters.
 *<P>
 * The "known options" are initialized via {@link #initAllOptions()}.  See that
 * method's description for more details on adding an option.
 * If a new option changes previously expected behavior, it should default to
 * the old behavior; its default value on your server can be changed at runtime.
 *<P>
 * Introduced in 1.1.07; check server, client versions against
 * {@link soc.message.SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
 * Each option has version information, because options can be added or changed
 * with new versions of JSettlers.  Since games run on the server, the server is
 * authoritative about game options:  If the client is newer, it must defer to the
 * server's older set of known options.  At client connect, the client compares its
 * JSettlers version number to the server's, and asks for any changes to options if
 * their versions differ.
 *<P>
 * @author Jeremy D. Monin <jeremy@nand.net>
 * @since 1.1.07
 */
public class SOCGameOption implements Cloneable
{
    /**
     * Set of "known options".
     * allOptions must never be null, because other places assume it is filled.
     */
    private static Hashtable allOptions = initAllOptions();

    /**
     * Create a set of the known options.
     *
     * <h3>Current known options:</h3>
     *<UL>
     *<LI> PL  Maximum # players (2-4)
     *<LI> RD  Robber can't return to the desert
     *<LI> N7  Roll no 7s during first # rounds
     *<LI> BC  Break up clumps of # or more same-type ports/hexes
     *<LI> NT  No trading allowed
     *</UL>
     *
     * <h3>If you want to add a game option:</h3>
     *<UL>
     *<LI> Choose an unused 2-character key name: for example, "PL" for "max players".
     *   All in-game code uses these key strings to query and change
     *   game option settings; only a very few places use SOCGameOption
     *   objects, and you won't need to adjust those places.
     *   The list of already-used key names is here within initAllOptions.
     *<LI> Decide which {@link #optType option type} your option will be
     *   (boolean, enumerated, int+bool, etc.), and its default value.
     *   Typically the default will let the game behave as it did before
     *   the option existed (for example, the "max players" default is 4).
     *   Its default value on your own server can be changed at runtime.
     *<LI> Decide if all client versions can use your option.  Typically, if the option
     *   requires server changes but not any client changes, all clients can use it.
     *   (For example, "N7" for "roll no 7s early in the game" is strictly server-side.)
     *   If only <em>some values</em> of the option will require client changes,
     *   also update {@link #getMinVersion()}.  (For example, if "PL"'s value is 5 or 6,
     *   a new client would be needed to display that many players at once, but 2 - 4
     *   can use any client version.)
     *<LI> Create the option by calling opt.put here in initAllOptions.
     *   Use the current version for the "last modified" field.
     *<LI> Within {@link SOCGame}, don't add any object fields due to the new option;
     *   instead call {@link SOCGame#isGameOptionDefined(String)},
     *   {@link SOCGame#getGameOptionIntValue(String)}, etc.
     *   Look for game methods where game behavior changes with the new option,
     *   and adjust those.
     *<LI> Check the server and clients for places which must check for the new option.
     *   Typically these will be the <strong>places which call the game methods</strong> affected.
     *   <UL>
     *   <LI> {@link soc.server.SOCServer} is the server class,
     *           see its "handle" methods for network messages
     *   <LI> {@link soc.client.SOCPlayerClient} is the graphical client
     *   <LI> {@link soc.client.SOCRobotClient} and {@link soc.robot.SOCRobotBrain#run()}
     *           together handle the robot client messages
     *   <LI> {@link soc.client.SOCDisplaylessClient} is the foundation for the robot client,
     *           and handles some of its messages
     *   </UL>
     *   Some options don't need any code at the robot; for example, the robot doesn't
     *   care about the maximum number of players in a game, because it doesn't decide when
     *   to join a game; the server tells it that.
     *<LI> To find other places which may possibly need an update from your new option,
     *   search the source for this marker: <code> // NEW_OPTION </code>
     *   <br>
     *   This would include places like
     *   {@link soc.util.SOCRobotParameters#copyIfOptionChanged(Hashtable)}
     *   which ignore most, but not all, game options.
     *</UL>
     *
     * <h3>If you want to change a game option:</h3>
     *
     *   Typical changes to a game option would be:
     *<UL>
     *<LI> Add new values to an {@link #OTYPE_ENUM enumerated} option
     *<LI> Change the maximum or minimum permitted values for an
     *   {@link #OTYPE_INT integer} option
     *<LI> Change the default value, although this can also be done
     *   at runtime on the command line
     *</UL>
     *   Things you can't change about an option, because inconsistencies would occur:
     *<UL>
     *<LI> {@link #optKey name key}
     *<LI> {@link #optType}
     *<LI> {@link #minVersion}
     *<LI> {@link #dropIfUnused} flag
     *</UL>
     *
     *   <b>To make the change:</b>
     *<UL>
     *<LI> Change the option here in initAllOptions; change the "last modified" field to
     *   the current game version. Otherwise the server can't tell the client what has
     *   changed about the option.
     *<LI> Search the source for its key name, to find places which may need an update.
     *<LI> Consider if any other places listed above (for add) need adjustment.
     *</UL>
     *
     *<P>
     * This method creates and returns a Hashtable, but does not set the static {@link #allOptions} field.
     *
     * @return a fresh copy of the "known" options, with their hardcoded default values
     */
    private static Hashtable initAllOptions()
    {
        Hashtable opt = new Hashtable();

        opt.put("PL", new SOCGameOption
                ("PL", -1, 1107, 4, 2, 4, "Maximum # players"));
        opt.put("RD", new SOCGameOption
                ("RD", -1, 1107, false, false, "Robber can't return to the desert"));
        opt.put("N7", new SOCGameOption
                ("N7", -1, 1107, false, 7, 1, 999, false, "Roll no 7s during first # rounds"));
        opt.put("BC", new SOCGameOption
                ("BC", -1, 1107, true, 3, 3, 9, false, "Break up clumps of # or more same-type hexes/ports"));
        opt.put("NT", new SOCGameOption
                ("NT", 1107, 1107, false, true, "No Trading allowed"));

        // NEW_OPTION - Add opt.put here at end of list, and update the
        //       list of "current known options" in javadoc just above.

        /*
        opt.put("DEBUG_ENUM", new SOCGameOption
                ("DEBUG_ENUM", 1107, 1107, 
                 3, new String[]{ "First", "Second", "Third", "Fourth"}, "Test option enum"));
        opt.put("DEBUG_STR", new SOCGameOption
                ("DEBUG_STR", 1107, 1107, 20, false, true, "Test option str"));
        opt.put("DEBUG_STRHIDE", new SOCGameOption
                ("DEBUG_STRHIDE", 1107, 1107, 20, true, true, "Test option strhide"));
        */

        return opt;
    }

    /** Lowest OTYPE value known at this version */
    public static final int OTYPE_MIN = 0;

    /** Option type: unknown (probably due to version mismatch)  */
    public static final int OTYPE_UNKNOWN = 0;

    /** Option type: boolean  */
    public static final int OTYPE_BOOL = 1;

    /** Option type: integer  */
    public static final int OTYPE_INT = 2;

    /** Option type: integer + boolean */
    public static final int OTYPE_INTBOOL = 3;

    /** Option type: enumeration (1 of several possible choices, described with text strings,
     *  stored here as intVal).  Choices' strings are stored in {@link #enumVals}.
     */
    public static final int OTYPE_ENUM = 4;

    /** Option type: text string (max string length is {@link #maxIntValue}, default value is "") */
    public static final int OTYPE_STR = 5;

    /** Option type: text string (like {@link #OTYPE_STR}) but hidden from view; is NOT encrypted,
     *  but contents show up as "*" when typed into a text field.
     */
    public static final int OTYPE_STRHIDE = 6;

    /** Highest OTYPE value known at this version */
    public static final int OTYPE_MAX = OTYPE_STRHIDE;  // OTYPE_* - adj OTYPE_MAX if adding new type

    // If you create a new option type,
    // please update parseOptionsToHash(), packOptionsToString(),
    // adjustOptionsToKnown(), and soc.message.SOCGameOptionGetInfo,
    // and other places.
    // (Search *.java for "// OTYPE_*" to find all locations)

    /** Option type.
     *<UL>
     * <LI> {@link #OTYPE_BOOL} Boolean
     * <LI> {@link #OTYPE_INT}  Integer, with min/max value
     * <LI> {@link #OTYPE_INTBOOL} Int plus bool (Ex. [x] no robber rolls in first _5_ turns)
     * <LI> {@link #OTYPE_ENUM} Enumerated-choice (Ex. Standard vs Seafarers):
     *        Stored like integer {@link #OTYPE_INT} in range 1-n, described to user with text strings.
     * <LI> {@link #OTYPE_STR} short text string: max string length is {@link #maxIntValue}; default value is the empty string.
     * <LI> {@link #OTYPE_STRHIDE} text string (like {@link #OTYPE_STR}) but hidden from view; is NOT encrypted.
     *</UL>
     */
    public final int optType;  // OTYPE_* - if a new type is added, update this field's javadoc.

    /**
     * Option key/name: Short alphanumeric name (2 characters, uppercase, starting with a letter)
     */
    public final String optKey;

    /**
     * Minimum game version supporting this option, or -1;
     * same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     * Public direct usage of this is discouraged;
     * use {@link #optionsMinimumVersion(Hashtable)} or {@link #getMinVersion()} instead,
     * because the current value of an option can change its minimum version.
     * For example, a 5- or 6-player game will need a newer client than 4 players,
     * but option "PL"'s minVersion is -1, to allow 2- or 3-player games with any client.
     */
    public final int minVersion;  // or -1

    /**
     * Most recent game version in which this option changed, or if not modified, the version which added it.
     * changes would include different min/max values, new choices for an {@link #OTYPE_ENUM}, etc.
     * Same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     */
    public final int lastModVersion;

    /**
     * Should we drop this option from game options, and not send over the network (to reduce overhead),
     * if the value is un-set or blank? (un-set for {@link #OTYPE_BOOL} or {@link #OTYPE_INTBOOL},
     * blank for {@link #OTYPE_STR} or {@link #OTYPE_STRHIDE})
     * Only recommended for seldom-used options.
     * The removal is done in {@link #adjustOptionsToKnown(Hashtable, Hashtable)}.
     * Once this flag is set for an option, it should not be un-set if the
     * option is changed in a later version.
     *<P>
     * For {@link #OTYPE_INTBOOL}, both the integer and boolean values are checked
     * against defaults.
     */
    public final boolean dropIfUnused;  // OTYPE_* - mention in javadoc if this applies to the new type.

    /**
     * Default value for boolean part of this option, if any
     */
    public final boolean defaultBoolValue;

    /**
     * Default value for integer part of this option, if any
     */
    public final int defaultIntValue;

    /**
     * Minumum and maximum permitted values for integer part of this option, if any,
     * or maximum length of a string value. (There is no minimum length)
     */
    public final int minIntValue, maxIntValue;

    /**
     * Descriptive text for the option. Must not contain the network delimiter
     * characters {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}.
     * If {@link #OTYPE_INTBOOL}, may contain a placeholder '#' where the value is typed onscreen.
     * For {@link #OTYPE_UNKNOWN}, an empty string.
     */
    public final String optDesc;   // OTYPE_* - if a new type is added, update this field's javadoc.

    /**
     * For type {@link #OTYPE_ENUM}, descriptive text for each possible value,
     * otherwise null.  If a value is added or changed in a later version, the option's
     * {@link #lastModVersion} field must be updated, so server/client will know
     * to ask for the proper version with all available options.
     * Although the option's intVals are in the range 1 to n, this array is indexed 0 to n-1.
     */
    public final String[] enumVals;

    private boolean boolValue;
    private int     intValue;
    private String  strValue;  // no default value: is "", stored as null

    /**
     * Create a new game option of unknown type ({@link #OTYPE_UNKNOWN}).
     * Minimum version will be {@link Integer#MAX_VALUE}.
     * Value will be false/0. optDesc will be an empty string.
     * @param key   Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @throws IllegalArgumentException if key length is > 3 or not alphanumeric,
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    public SOCGameOption(String key)
        throws IllegalArgumentException
    {
        this(OTYPE_UNKNOWN, key, Integer.MAX_VALUE, Integer.MAX_VALUE, false, 0, 0, 0, false, null, "");
    }

    /**
     * Create a new boolean game option. Type is {@link #OTYPE_BOOL}.
     *
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version if this option is set (is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default value (true if set, false if not set)
     * @param dropIfUnused If this option's value is unset, should we not add it to game options
     *           or send over the network (to reduce overhead)?
     *           Only recommended if game behavior without the option is well-established
     *           (for example, trading is allowed unless option NT is present).
     * @param desc    Descriptive brief text, to appear in the options dialog
     * @throws IllegalArgumentException if key length is > 3 or not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    public SOCGameOption(String key, int minVers, int lastModVers,
        boolean defaultValue, boolean dropIfUnused, String desc)
        throws IllegalArgumentException
    {
	this(OTYPE_BOOL, key, minVers, lastModVers, defaultValue, 0, 0, 0, dropIfUnused, null, desc);
    }

    /**
     * Create a new integer game option.  Type is {@link #OTYPE_INT}.
     * There is no dropIfUnused parameter for integer options,
     * because they have no 'blank' value.
     *
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default int value
     * @param minValue Minimum permissible value
     * @param maxValue Maximum permissible value; the width of the options-dialog
     *                 value field is based on the number of digits in maxValue.
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the int value goes.
     *             If no placeholder is found, the value text field appears at left,
     *             like boolean options.
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue,
     *        or if key length is > 3 or not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    public SOCGameOption(String key, int minVers, int lastModVers,
        int defaultValue, int minValue, int maxValue, String desc)
        throws IllegalArgumentException 
    {
	this(OTYPE_INT, key, minVers, lastModVers, false, defaultValue,
	     minValue, maxValue, false, null, desc);
    }

    /**
     * Create a new int+boolean game option. Type is {@link #OTYPE_INTBOOL}.
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultBoolValue Default value (true if set, false if not set)
     * @param defaultIntValue Default int value, to use if option is set
     * @param minValue Minimum permissible value
     * @param maxValue Maximum permissible value; the width of the options-dialog
     *                 value field is based on the number of digits in maxValue.
     * @param dropIfUnused If this option's bool value is unset, and its int value is the default,
     *           should we not add it to game options
     *           or send over the network (to reduce overhead)?
     *           Only recommended if game behavior without the option is well-established
     *           (for example, trading is allowed unless option NT is present).
     * @param desc Descriptive brief text, to appear in the options dialog; should
     *             contain a placeholder character '#' where the int value goes.
     * @throws IllegalArgumentException if defaultIntValue < minValue or is > maxValue,
     *        or if key length is > 3 or not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    public SOCGameOption(String key, int minVers, int lastModVers, boolean defaultBoolValue, int defaultIntValue,
        int minValue, int maxValue, boolean dropIfUnused, String desc)
        throws IllegalArgumentException
    {
	this(OTYPE_INTBOOL, key, minVers, lastModVers, defaultBoolValue, defaultIntValue,
	     minValue, maxValue, dropIfUnused, null, desc);
    }

    /**
     * Create a new enumerated game option.  Type is {@link #OTYPE_ENUM}.
     * The {@link #minIntValue} will be 1, {@link #maxIntValue} will be enumVals.length.
     * There is no dropIfUnused parameter for enum options,
     * because they have no 'blank' value.
     *
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default int value, in range 1 - n (n == number of possible values)
     * @param enumVals text to display for each possible choice of this option
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the enum's popup-menu goes.
     *             If no placeholder is found, the value field appears at left,
     *             like boolean options.
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue,
     *        or if key length is > 3 or not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    public SOCGameOption(String key, int minVers, int lastModVers,
        int defaultValue, String[] enumVals, String desc)
        throws IllegalArgumentException 
    {
	this(OTYPE_ENUM, key, minVers, lastModVers, false, defaultValue,
	     1, enumVals.length, false, enumVals, desc);
    }

    /**
     * Create a new text game option.  Type is {@link #OTYPE_STR} or {@link #OTYPE_STRHIDE}.
     * The {@link #maxIntValue} will be maxLength.
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param maxLength   Maximum length, between 1 and 255 (for network bandwidth conservation)
     * @param hideTyping  Should type be {@link #OTYPE_STRHIDE} instead of {@link #OTYPE_STR}?
     * @param dropIfUnused If this option's value is blank, should we not add it to game options
     *           or send over the network (to reduce overhead)?
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the text value goes.
     *             If no placeholder is found, the value text field appears at left,
     *             like boolean options.
     * @throws IllegalArgumentException if maxLength > 255,
     *        or if key length is > 3 or not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    public SOCGameOption(String key, int minVers, int lastModVers,
	int maxLength, boolean hideTyping, boolean dropIfUnused, String desc)
        throws IllegalArgumentException 
    {
	this( (hideTyping ? OTYPE_STRHIDE : OTYPE_STR ),
	     key, minVers, lastModVers, false, 0,
	     0, maxLength, dropIfUnused, null, desc);
	if ((maxLength < 1) || (maxLength > 255))
	    throw new IllegalArgumentException("maxLength");
    }

    /**
     * Create a new game option - common constructor.
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param otype   Option type; use caution, as this is unvalidated against
     *                {@link #OTYPE_MIN} or {@link #OTYPE_MAX}.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultBoolValue Default value (true if set, false if not set)
     * @param defaultIntValue Default int value, to use if option is set
     * @param minValue Minimum permissible value
     * @param maxValue Maximum permissible value; the width of the options-dialog
     *                 value field is based on the number of digits in maxValue.
     * @param dropIfUnused If this option's value is blank or unset, should we not add it to game options?
     *                 See {@link #dropIfUnused} javadoc for more details.
     * @param enumVals Possible choice texts for {@link #OTYPE_ENUM}, or null;
     *                 value(s) must pass same checks as desc.
     * @param desc Descriptive brief text, to appear in the options dialog; should
     *             contain a placeholder character '#' where the int value goes.
     *             Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @throws IllegalArgumentException if defaultIntValue < minValue or is > maxValue,
     *        or if key length is > 3 or not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    protected SOCGameOption(int otype, String key, int minVers, int lastModVers,
        boolean defaultBoolValue, int defaultIntValue,
        int minValue, int maxValue, boolean dropIfUnused,
        String[] enumVals, String desc)
        throws IllegalArgumentException
    {
	// validate & set option properties:

        if (key.length() > 3)
            throw new IllegalArgumentException("Key length: " + key);
        if (! isAlphanumericUpcaseAscii(key))
            throw new IllegalArgumentException("Key not alphanumeric: " + key);
        if ((minVers < 1000) && (minVers != -1))
            throw new IllegalArgumentException("minVers " + minVers + " for key " + key);
        if ((lastModVers < 1000) && (lastModVers != -1))
            throw new IllegalArgumentException("lastModVers " + lastModVers + " for key " + key);
        if (! SOCMessage.isSingleLineAndSafe(desc))
            throw new IllegalArgumentException("desc fails isSingleLineAndSafe");

	optKey = key;
	optType = otype;
	minVersion = minVers;
	lastModVersion = lastModVers;
	this.defaultBoolValue = defaultBoolValue;
	this.defaultIntValue = defaultIntValue;
	minIntValue = minValue;
	maxIntValue = maxValue;
	this.dropIfUnused = dropIfUnused;
        this.enumVals = enumVals;
	optDesc = desc;

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
    }

    /**
     * Is this option set, if this option's type has a boolean component?
     * @return current boolean value of this option
     */
    public boolean getBoolValue() { return boolValue; }

    public void setBoolValue(boolean v) { boolValue = v; }

    /**
     * This option's integer value, if this option's type has an integer component.
     * @return current integer value of this option
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
     * Minimum game version supporting this option, given the option's current value.
     * The current value of an option can change its minimum version.
     * For example, a 5- or 6-player game will need a newer client than 4 players,
     * but option "PL"'s {@link #minVersion} is -1, to allow 2- or 3-player games with any client.
     * For option types {@link #OTYPE_BOOL} and {@link #OTYPE_INTBOOL}, the minimum
     * value is -1 unless {@link #getBoolValue()} is true (that is, unless the option is set).
     *
     * @return minimum version, or -1;
     *     same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     * @see #optionsMinimumVersion(Hashtable)
     */
    public int getMinVersion()
    {
        if ((optType == OTYPE_BOOL) || (optType == OTYPE_INTBOOL))  // OTYPE_*: check here if boolean-valued
        {
            if (! boolValue)
                return -1;  // Option not set: any client version is OK
        }

        // NEW_OPTION:
        // Any option value checking for minVers is done here.
        // None of the current options change minVers based on their value.
        // If your option changes the minVers based on its current value,
        // check the optKey and current value, and return the appropriate version,
        // instead of just returning minVersion.

        // SAMPLE CODE:
        /*
        if (optKey.equals("N7") && (intValue == 42))
        {
            return 1108;
        }
        */
        // END OF SAMPLE CODE.

        return minVersion;
    }

    /**
     * @return a deep copy of all known option objects
     * @see #addKnownOption(SOCGameOption)
     */
    public static Hashtable getAllKnownOptions()
    {
	return cloneOptions(allOptions);
    }

    /**
     * Add a new known option (presumably received from a server of newer or older version),
     * or update the option's information.
     * @param onew New option, or a changed version of an option we already know.
     *             If onew.optType == {@link #OTYPE_UNKNOWN}, will remove from the known table.
     * @return true if it's new, false if we already had that key and it was updated
     * @see #getAllKnownOptions()
     */
    public static boolean addKnownOption(SOCGameOption onew)
    {
	final String oKey = onew.optKey;
	final boolean hadIt = allOptions.containsKey(oKey);
	if (hadIt)
	    allOptions.remove(oKey);
	if (onew.optType != OTYPE_UNKNOWN)
	    allOptions.put(oKey, onew);
	return ! hadIt;
    }

    /**
     * Set the current value of a known option, based on the current value of
     * another object with the same {@link #optKey}.
     * If there is no known option with oCurr.{@link #optKey}, it is ignored and nothing is set.
     * @param ocurr Option with the requested current value
     * @throws  IllegalArgumentException if value is not permitted; note that
     *            intValues outside of range are silently clipped, and will not
     *            throw this exception.
     */
    public static void setKnownOptionCurrentValue(SOCGameOption ocurr)
        throws IllegalArgumentException
    {
        final String oKey = ocurr.optKey;
        SOCGameOption oKnown = (SOCGameOption) allOptions.get(oKey);
        if (oKnown == null)
            return;
        switch (oKnown.optType)  // OTYPE_*
        {
        case OTYPE_BOOL:
            oKnown.boolValue = ocurr.boolValue;
            break;

        case OTYPE_INT:
        case OTYPE_ENUM:
            oKnown.setIntValue(ocurr.intValue);
            break;

        case OTYPE_INTBOOL:
            oKnown.boolValue = ocurr.boolValue;
            oKnown.setIntValue(ocurr.intValue);
            break;

        case OTYPE_STR:
        case OTYPE_STRHIDE:
            oKnown.setStringValue(ocurr.strValue);
            break;
        }
    }

    /**
     * @param opts  a hashtable of SOCGameOptions, or null
     * @return a deep copy of all option objects within opts, or null if opts is null
     */
    public static Hashtable cloneOptions(Hashtable opts)
    {
	if (opts == null)
	    return null;

	Hashtable opts2 = new Hashtable();
	for (Enumeration e = opts.keys(); e.hasMoreElements(); )
	{
	    SOCGameOption op = (SOCGameOption) opts.get(e.nextElement());
	    try
	    {
	        opts2.put(op.optKey, (SOCGameOption) op.clone());
	    } catch (CloneNotSupportedException ce) {}
	}
	return opts2;
    }

    /**
     * @return information about a known option, or null if none with that key
     */
    public static SOCGameOption getOption(String key)
    {
	return (SOCGameOption) allOptions.get(key);  // null is ok
    }

    /**
     * Search these options and find any unknown ones (type {@link #OTYPE_UNKNOWN})
     * @param opts hashtable of SOCGameOption
     * @return vector(SOCGameOption) of unknown options, or null if all are known
     */
    public static Vector findUnknowns(Hashtable opts)
    {
        Vector unknowns = null;
        for (Enumeration e = opts.keys(); e.hasMoreElements(); )
        {
            SOCGameOption op = (SOCGameOption) opts.get((String) e.nextElement());
            if (op.optType == SOCGameOption.OTYPE_UNKNOWN)
            {
                if (unknowns == null)
                    unknowns = new Vector();
                unknowns.addElement(op.optKey);
            }
        }
        return unknowns;
    }

    /**
     * Utility - build a string of option name-value pairs from the
     *           {@link #getAllKnownOptions() known options}' current values.
     *
     * @param hideEmptyStringOpts omit string-valued options which are empty?
     *            Suitable only for sending defaults.
     * @return string of name-value pairs;
     *         any gameoptions of {@link #OTYPE_UNKNOWN} will not be
     *         part of the string.
     * @see #parseOptionsToHash(String)
     */
    public static String packKnownOptionsToString(boolean hideEmptyStringOpts)
    {
	return packOptionsToString(allOptions, hideEmptyStringOpts);
    }

    /**
     * Utility - build a string of option name-value pairs.
     * This can be unpacked with {@link #parseOptionsToHash(String)}.
     *
     * @param ohash Hashtable of SOCGameOptions, or null
     * @param hideEmptyStringOpts omit string-valued options which are empty?
     *            Suitable only for sending defaults.
     * @return string of name-value pairs, or "-" for an empty or null ohash;
     *         any gameoptions of {@link #OTYPE_UNKNOWN} will not be
     *         part of the string. Format: k1=t,k2=f,k3=10,k4=t7,k5=f7.
     * The format for each value depends on its type:
     *<UL>
     *<LI>OTYPE_BOOL: t or f
     *<LI>OTYPE_ENUM: int in range 1-n
     *<LI>OTYPE_INTBOOL: t or f followed immediately by int value, as in: t7 or f9
     *<LI>All other optTypes: int value or string value, as appropriate
     *</UL>
     *
     * @throws ClassCastException if hashtable contains anything other
     *         than SOCGameOptions
     * @see #parseOptionNameValue(String, boolean)
     * @see #packValue(StringBuffer)
     */
    public static String packOptionsToString(Hashtable ohash, boolean hideEmptyStringOpts)
	throws ClassCastException
    {
	if ((ohash == null) || ohash.size() == 0)
	    return "-";

	StringBuffer sb = new StringBuffer();
	boolean hadAny = false;
	for (Enumeration e = ohash.keys(); e.hasMoreElements(); )
	{
	    SOCGameOption op = (SOCGameOption) ohash.get(e.nextElement());
	    if (op.optType == OTYPE_UNKNOWN)
		continue;  // <-- Skip this one --
	    if (hideEmptyStringOpts
	        && ((op.optType == OTYPE_STR) || (op.optType == OTYPE_STRHIDE))  // OTYPE_* - add here if string-valued
	        && op.getStringValue().length() == 0)
                continue;  // <-- Skip this one --       

	    if (hadAny)
		sb.append(SOCMessage.sep2_char);
	    else
		hadAny = true;
	    sb.append(op.optKey);
	    sb.append('=');
            op.packValue(sb);
	}
	return sb.toString();
    }

    /**
     * Pack current value of this option into a string.
     * This is used in {@link #packOptionsToString(Hashtable, boolean)} and
     * read in {@link #parseOptionNameValue(String, boolean)} and {@link #parseOptionsToHash(String)}.
     * See {@link #packOptionsToString(Hashtable, boolean)} for the string's format.
     *
     * @param sb Pack into (append to) this buffer
     */
    public void packValue(StringBuffer sb)
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
     * Utility - build a hashtable by parsing a list of option name-value pairs.
     *
     * @param ostr string of name-value pairs, as created by
     *             {@link #packOptionsToString(Hashtable, boolean)}.
     *             A leading comma is OK (possible artifact of StringTokenizer
     *             coming from over the network).
     *             If ostr=="-", hashtable will be null.
     * @return hashtable of SOCGameOptions, or null if ostr==null or empty ("-")
     *         or if ostr is malformed.  Any unrecognized options
     *         will be in the hashtable as type {@link #OTYPE_UNKNOWN}.
     * @see #parseOptionNameValue(String, boolean)
     */
    public static Hashtable parseOptionsToHash(String ostr)
    {
	if ((ostr == null) || ostr.equals("-"))
	    return null;

	Hashtable ohash = new Hashtable();

	StringTokenizer st = new StringTokenizer(ostr, SOCMessage.sep2);
	String nvpair;
	while (st.hasMoreTokens())
	{
	    nvpair = st.nextToken();  // skips any leading commas or doubled commas
            SOCGameOption copyOpt = parseOptionNameValue(nvpair, false);
            if (copyOpt == null)
                return null;  // parse error
            ohash.put(copyOpt.optKey, copyOpt);
	}  // while (moreTokens)

	return ohash;
    }

    /**
     * Utility - parse a single name-value pair produced by packOptionsToString.
     * Expected format of nvpair: "optname=optvalue".
     * Expected format of optvalue depends on its type.
     * See {@link #packOptionsToString(Hashtable, boolean)} for the format.
     *
     * @param nvpair Name-value pair string, as created by
     *               {@link #packOptionsToString(Hashtable, boolean)}.
     *               'T' or 't' is always allowed for bool value, regardless of forceNameUpcase.
     * @param forceNameUpcase Call {@link String#toUpperCase()} on keyname within nvpair?
     *               For friendlier parsing of manually entered (command-line) nvpair strings.
     * @return Parsed option, or null if parse error;
     *         if nvpair's option keyname is not a known option, returned optType will be {@link #OTYPE_UNKNOWN}.
     * @see #parseOptionsToHash(String)
     * @see #packValue(StringBuffer)
     */
    public static SOCGameOption parseOptionNameValue(final String nvpair, final boolean forceNameUpcase)
    {
        int i = nvpair.indexOf('=');  // don't just tokenize for this (efficiency, and param value may contain a "=")
        if (i < 1)
            return null;  // malformed

        String optkey = nvpair.substring(0, i);
        String optval = nvpair.substring(i+1);
        if (forceNameUpcase)
            optkey = optkey.toUpperCase();
        SOCGameOption knownOpt = (SOCGameOption) allOptions.get(optkey);
        SOCGameOption copyOpt;
        if (knownOpt == null)
        {
            copyOpt = new SOCGameOption(optkey);  // OTYPE_UNKNOWN
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
                copyOpt.setBoolValue(optval.equals("t") || optval.equals("T"));
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
                try
                {
                    final char ch0 = optval.charAt(0);
                    copyOpt.setBoolValue((ch0 == 't') || (ch0 == 'T'));
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
     * Examine this set of options, finding the minimum required version to support
     * a game with these options.  The current value of an option can change its minimum version.
     * For example, a 5- or 6-player game will need a newer client than 4 players,
     * but option "PL"'s minVersion is -1, to allow 2- or 3-player games with any client.
     *<P>
     * This calculation is done at the server when creating a new game.  Although the client's
     * version and options (and thus its copy of optionsMinimumVersion) may be newer or older,
     * and would give a different result if called, the server is authoritative for game options.
     * Calls at the client to optionsMinimumVersion should keep this in mind, especially if
     * a client's game option's {@link #lastModVersion} is newer than the server.
     *
     * @return the highest 'minimum version' among these options, or -1
     * @throws ClassCastException if values contain a non-{@link SOCGameOption}
     * @see #getMinVersion()
     */
    public static int optionsMinimumVersion(Hashtable opts)
	throws ClassCastException
    {
	int minVers = -1;
	for (Enumeration e = opts.keys(); e.hasMoreElements(); )
	{
	    SOCGameOption op = (SOCGameOption) opts.get(e.nextElement());
            int opMin = op.getMinVersion();  // includes any option value checking for minVers
	    if (opMin > minVers)
		minVers = opMin;
	}
	return minVers;
    }

    /**
     * Compare a set of options against the specified version.
     * Make a list of all which are new or changed since that version.
     *
     * @param vers  Version to compare known options against
     * @param checkValues  Check options' current values,
     *              not just their {@link #lastModVersion}?
     *              An option's minimum version can increase based
     *              on its value; see {@link #getMinVersion()}.
     * @param opts  Set of {@link SOCGameOption}s to check current values;
     *              if null, use the "known option" set
     * @return Vector of the unknown {@link SOCGameOption}s, or null
     *     if all are known and unchanged since vers.
     *     If checkValues, any options whose version is based on current value
     *     will appear first in the vector.  When looking for these,
     *     look for getMinVersion() > vers.
     */
    public static Vector optionsNewerThanVersion(final int vers, final boolean checkValues, Hashtable opts)
    {
        if (opts == null)
            opts = allOptions;
        Vector uopt = null;  // add problems to uopt
        if (checkValues)
        {
            for (Enumeration e = opts.elements(); e.hasMoreElements(); )
            {
                SOCGameOption opt = (SOCGameOption) e.nextElement();
                if (opt.getMinVersion() > vers)
                {
                    if (uopt == null)
                        uopt = new Vector();
                    uopt.addElement(opt);
                }
            }
        }
	for (Enumeration e = opts.elements(); e.hasMoreElements(); )
	{
	    SOCGameOption opt = (SOCGameOption) e.nextElement();
	    if (opt.lastModVersion > vers)
	    {
		if (uopt == null)
		    uopt = new Vector();
                else if (! (checkValues && uopt.contains(opt)))
                    uopt.addElement(opt);
	    }
	}
	return uopt;
    }

    /**
     * Compare a set of options with known-good values.
     * If any are above/below maximum/minimum, clip to the max/min value in knownOpts.
     * If any are unknown, return false. Will still check (and clip) the known ones.
     * If any boolean or string-valued options are default, and unset/blank, and
     * their {@link #dropIfUnused} flag is set, remove them from newOpts.
     * For {@link #OTYPE_INTBOOL}, both the integer and boolean values are checked
     * against defaults.
     *
     * @param newOpts Set of SOCGameOptions to check against knownOpts;
     *            an option's current value will be changed if it's outside of
     *            the min/max for that option in knownOpts.
     *            Must not be null.
     * @param knownOpts Set of known SOCGameOptions to check against, or null to use
     *            the server's static copy
     * @return true if all are known; false if any of newOpts are unknown
     *            or if an opt's type differs from that in knownOpts,
     *            or if opt's {@link #lastModVersion} differs from in knownOpts.
     * @throws IllegalArgumentException if newOpts contains a non-SOCGameOption
     */
    public static boolean adjustOptionsToKnown(Hashtable newOpts, Hashtable knownOpts)
        throws IllegalArgumentException
    {
        if (knownOpts == null)
            knownOpts = allOptions;

        // OTYPE_* - adj javadoc above (re dropIfUnused) if a string-type or bool-type is added.

        // use Iterator in loop, so we can remove from the hash if needed
        boolean allKnown = true;
	for (Iterator ikv = newOpts.entrySet().iterator();
	     ikv.hasNext(); )
	{
	    Map.Entry okv = (Map.Entry) ikv.next();

	    SOCGameOption op;
	    try {
	        op = (SOCGameOption) okv.getValue();
	    }
	    catch (ClassCastException ce)
	    {
                throw new IllegalArgumentException("wrong class, expected gameoption");
	    }
	    SOCGameOption knownOp = (SOCGameOption) knownOpts.get(op.optKey);
	    if ((knownOp == null) || (knownOp.optType != op.optType))
	    {
		allKnown = false;
	    } else {
	        // Clip int values, check default values

		if (knownOp.lastModVersion != op.lastModVersion)
		    allKnown = false;

		switch (op.optType)  // OTYPE_*
		{
		case OTYPE_INT:
		case OTYPE_INTBOOL:
		case OTYPE_ENUM:
		    {
			int iv = op.intValue;
			if (iv < knownOp.minIntValue)
			{
			    iv = knownOp.minIntValue;
			    op.setIntValue(iv);
			}
			else if (iv > knownOp.maxIntValue)
			{
			    iv = knownOp.maxIntValue;
			    op.setIntValue(iv);
			}

                        // integer-type options are not subject to dropIfUnused,
                        // except for OTYPE_INTBOOL.
                        if ((op.optType == OTYPE_INTBOOL)
                               && knownOp.dropIfUnused
                               && (iv == knownOp.defaultIntValue)
                               && (! op.boolValue))
                             ikv.remove();
		    }
		    break;

		case OTYPE_BOOL:
                    if (knownOp.dropIfUnused && ! op.boolValue)
                        ikv.remove();
		    break;

                case OTYPE_STR:
                case OTYPE_STRHIDE:
                    if (knownOp.dropIfUnused &&
                          ((op.strValue == null) || (op.strValue.length() == 0)))
                        ikv.remove();
                    break;

                // no default: all types should be handled above.

		}  // endsw
	    }
	}
	return allKnown;
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
     * Test whether a string's characters are all within the strict
     * ranges 0-9, A-Z. The first character must be A-Z. Option name keys
     * must start with a letter and contain only ASCII uppercase letters
     * ('A' through 'Z') and digits ('0' through '9'), in order to normalize
     * handling and network message formats.
     * @param s string to test
     * @return true if all characters are OK, false otherwise
     */
    public static final boolean isAlphanumericUpcaseAscii(String s)
    {
        for (int i = s.length()-1; i>=0; --i)
        {
            final char c = s.charAt(i);
            if (((c < '0') || (c > '9'))
                && (c < 'A') || (c > 'Z'))
                return false;
            if ((i == 0) && (c < 'A'))
                return false;

            // We use range checks, and not methods such as
            // Character.isLetterOrDigit(ch), because those
            // methods also permit unicode characters beyond
            // what we'd like to accept here.
        }
        return true;
    }

    /**
     * Form a string with the key and current value, useful for debugging purposes.
     * @return string such as "PL=4" or "BC=t3", with the same format
     *    as {@link #packKnownOptionsToString(boolean)}.
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer(optKey);
        sb.append('=');
        packValue(sb);
        return sb.toString();
    }
}
