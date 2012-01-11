/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009,2011,2012 Jeremy D Monin <jeremy@nand.net>
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
 *<P>
 * For information about adding or changing game options in a
 * later version of JSettlers, please see {@link #initAllOptions()}.
 *<P>
 * All in-game code uses the 2-letter or 3-letter key strings to query and change
 * game option settings; only a very few places use SOCGameOption
 * objects.  To search the code for uses of a game option, search for
 * its capitalized key string.
 * You will see calls to {@link SOCGame#isGameOptionDefined(String)},
 * {@link SOCGame#getGameOptionIntValue(Hashtable, String, int, boolean)}, etc.
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
 * If a new option changes previously expected behavior of the game, it should default to
 * the old behavior; its default value on your server can be changed at runtime.
 *<P>
 * Since 1.1.13, when the user changes options while creating a new game, related
 * options can be changed on-screen for consistency; see {@link SOCGameOption.ChangeListener} for details.
 * If you create a ChangeListener, consider adding equivalent code to
 * {@link #adjustOptionsToKnown(Hashtable, Hashtable, boolean)} for the server side.
 *<P>
 * Game options were introduced in 1.1.07; check server, client versions against
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
public class SOCGameOption implements Cloneable, Comparable
{
    /**
     * Set of "known options".
     * allOptions must never be null, because other places assume it is filled.
     */
    private static Hashtable allOptions = initAllOptions();

    /**
     * List of options to refresh on-screen after a change during game creation;
     * filled by {@link #refreshDisplay()}.  Not thread-safe.
     * @see ChangeListener
     * @since 1.1.13
     */
    private static Vector refreshList;

    /**
     * Create a set of the known options.
     * This method creates and returns a Hashtable, but does not set the static {@link #allOptions} field.
     *
     * <h3>Current known options:</h3>
     *<UL>
     *<LI> PL  Maximum # players (2-6)
     *<LI> PLB Use 6-player board*
     *<LI> PLL Use large board* (experimental; name may change)
     *<LI> RD  Robber can't return to the desert
     *<LI> N7  Roll no 7s during first # rounds
     *<LI> BC  Break up clumps of # or more same-type ports/hexes
     *<LI> NT  No trading allowed
     *<LI> VP  Victory points (10-15)
     *</UL>
     *  * Grouping: PLB is 3 characters, not 2, and its first 2 characters match an
     *    existing option.  So in NewGameOptionsFrame, it appears on the line following
     *    the PL option in client version 1.1.13 and above.
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
     *<LI> Create the option by calling opt.put here in initAllOptions.
     *   Use the current version for the "last modified" field.
     *<LI> If only <em>some values</em> of the option will require client changes,
     *   also update {@link #getMinVersion(Hashtable)}.  (For example, if "PL"'s value is 5 or 6,
     *   a new client would be needed to display that many players at once, but 2 - 4
     *   can use any client version.) <BR>
     *   If this is the case and your option type
     *   is {@link #OTYPE_ENUM} or {@link #OTYPE_ENUMBOOL}, also update
     *   {@link #getMaxEnumValueForVersion(String, int)}.
     *   Otherwise, update {@link #getMaxIntValueForVersion(String, int)}.
     *<LI> If the new option can be used by old clients by changing the values of
     *   <em>other</em> related options when game options are sent to those versions,
     *   add code to {@link #getMinVersion(Hashtable)}. <BR>
     *   For example, the boolean "PLB" can force use of the 6-player board in
     *   versions 1.1.08 - 1.1.12 by changing "PL"'s value to 5 or 6.
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
     *   <LI> {@link soc.robot.SOCRobotClient} and {@link soc.robot.SOCRobotBrain#run()}
     *           together handle the robot client messages
     *   <LI> {@link soc.client.SOCDisplaylessPlayerClient} is the foundation for the robot client,
     *           and handles some of its messages
     *   </UL>
     *   Some options don't need any code at the robot; for example, the robot doesn't
     *   care about the maximum number of players in a game, because the server tells the
     *   robot when to join a game.
     *   <P>
     *   Some options need code only in the {@link SOCGame} constructor.
     *<LI> To find other places which may possibly need an update from your new option,
     *   search the entire source tree for this marker: <code> // NEW_OPTION</code>
     *   <br>
     *   This would include places like
     *   {@link soc.util.SOCRobotParameters#copyIfOptionChanged(Hashtable)}
     *   which ignore most, but not all, game options.
     *</UL>
     *
     * <h3>If you want to change a game option (in a later version):</h3>
     *
     *   Typical changes to a game option would be:
     *<UL>
     *<LI> Add new values to an {@link #OTYPE_ENUM enumerated} option;
     *   they must be added to the end of the list
     *<LI> Change the maximum or minimum permitted values for an
     *   {@link #OTYPE_INT integer} option
     *<LI> Change the default value, although this can also be done
     *   at runtime on the command line
     *<LI> Change the value at the server based on other options' values
     *</UL>
     *   Things you can't change about an option, because inconsistencies would occur:
     *<UL>
     *<LI> {@link #optKey name key}
     *<LI> {@link #optType}
     *<LI> {@link #minVersion}
     *<LI> {@link #dropIfUnused} flag
     *<LI> For {@link #OTYPE_ENUM} and {@link #OTYPE_ENUMBOOL}, you can't remove options or change
     *     the meaning of current ones, because this would mean that the option's intValue (sent over
     *     the network) would mean different things to different-versioned clients in the game.
     *</UL>
     *
     *   <b>To make the change:</b>
     *<UL>
     *<LI> Change the option here in initAllOptions; change the "last modified" field to
     *   the current game version. Otherwise the server can't tell the client what has
     *   changed about the option.
     *<LI> If new values require a newer minimum client version, add code to {@link #getMinVersion(Hashtable)}.
     *<LI> If adding a new enum value for {@link #OTYPE_ENUM} and {@link #OTYPE_ENUMBOOL},
     *   add code to {@link #getMaxEnumValueForVersion(String, int)}.
     *<LI> If increasing the maximum value of an int-valued parameter, and the new maximum
     *   requires a certain version, add code to {@link #getMaxIntValueForVersion(String, int)}.
     *   For example, versions below 1.1.08 limit "max players" to 4.
     *<LI> Search the entire source tree for its key name, to find places which may need an update.
     *<LI> Consider if any other places listed above (for add) need adjustment.
     *</UL>
     *
     * <h3>If you want to remove or obsolete a game option (in a later version):</h3>
     *
     * Please think twice beforehand; breaking compatibility with older clients shouldn't
     * be done without a very good reason.  That said, the server is authoritative on options.
     * If an option isn't in its known list ({@link #initAllOptions()}), the client won't be
     * allowed to ask for it.  Any obsolete options should be kept around as commented-out code.
     *
     * @return a fresh copy of the "known" options, with their hardcoded default values
     */
    public static Hashtable initAllOptions()
    {
        Hashtable opt = new Hashtable();

        final SOCGameOption pl = new SOCGameOption
                ("PL", -1, 1108, 4, 2, 6, "Maximum # players");
        opt.put("PL", pl);
        final SOCGameOption plb = new SOCGameOption
                ("PLB", 1108, 1113, false, true, "Use 6-player board");
        opt.put("PLB", plb);
        // TODO PLL for SOCBoardLarge: Decide final name
        opt.put("PLL", new SOCGameOption
                ("PLL", 2000, 2000, false, true, "Experimental: Use large board"));
        opt.put("RD", new SOCGameOption
                ("RD", -1, 1107, false, false, "Robber can't return to the desert"));
        opt.put("N7", new SOCGameOption
                ("N7", -1, 1107, false, 7, 1, 999, false, "Roll no 7s during first # rounds"));
        opt.put("BC", new SOCGameOption
                ("BC", -1, 1107, true, 4, 3, 9, false, "Break up clumps of # or more same-type hexes/ports"));
        opt.put("NT", new SOCGameOption
                ("NT", 1107, 1107, false, true, "No trading allowed between players"));
        opt.put("VP", new SOCGameOption
                ("VP", -1, 2000, false, 10, 10, 15, true, "Victory points to win: #"));

        // NEW_OPTION - Add opt.put here at end of list, and update the
        //       list of "current known options" in javadoc just above.

        // ChangeListeners for client convenience:
        // Remember that the server can't update this code at the client.
        // If you create a ChangeListener, also update adjustOptionsToKnown for server-side code.

        // If PL goes over 4, set PLB.
        pl.addChangeListener(new ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption opt, Object oldValue, Object newValue, Hashtable currentOpts)
            {
                if  (! (oldValue instanceof Integer))
                    return;  // ignore unless int
                final int ov = ((Integer) oldValue).intValue();
                final int nv = ((Integer) newValue).intValue();
                if ((ov <= 4) && (nv > 4))
                {
                    SOCGameOption plb = (SOCGameOption) currentOpts.get("PLB");
                    if (plb == null)
                        return;
                    plb.setBoolValue(true);
                    plb.refreshDisplay();
                }
            }
        });

        // If PLB is unchecked, set PL to 4 if it's 5 or 6
        plb.addChangeListener(new ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption opt, Object oldValue, Object newValue, Hashtable currentOpts)
            {
                if (Boolean.TRUE.equals(newValue))
                    return;  // ignore unless it became false
                SOCGameOption pl = (SOCGameOption) currentOpts.get("PL");
                if (pl == null)
                    return;
                if (pl.getIntValue() > 4)
                {
                    pl.setIntValue(4);
                    pl.refreshDisplay();
                }
            }
        });

        return opt;

        /*
                // A commented-out debug option is kept here for each option type's testing convenience.
                // OTYPE_* - Add a commented-out debug of the new type, for testing the new type.

        opt.put("DEBUGENUM", new SOCGameOption
                ("DEBUGENUM", 1107, 1107, 
                 3, new String[]{ "First", "Second", "Third", "Fourth"}, "Test option # enum"));
        opt.put("DEBUGENUMBOOL", new SOCGameOption
                ("DEBUGENUMBOOL", 1107, 1108, true,
                 3, new String[]{ "First", "Second", "Third", "Fourth"}, true, "Test option # enumbool"));
        opt.put("DEBUGINT", new SOCGameOption
                ("DEBUGINT", -1, 1113, 500, 1, 1000, "Test option int # (range 1-1000)"));
        opt.put("DEBUGSTR", new SOCGameOption
                ("DEBUGSTR", 1107, 1107, 20, false, true, "Test option str"));
        opt.put("DEBUGSTRHIDE", new SOCGameOption
                ("DEBUGSTRHIDE", 1107, 1107, 20, true, true, "Test option strhide"));
        */

        /*
                // TEST CODE: simple callback for each option, that just echoes old/new value

        ChangeListener testCL = new ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption opt, Object oldValue, Object newValue, Hashtable currentOpts)
            {
                System.err.println("Test ChangeListener: " + opt.optKey
                    + " changed from " + oldValue + " to " + newValue);                
            }
        };
        Enumeration okeys = opt.keys();
        while (okeys.hasMoreElements())
        {
            SOCGameOption op = (SOCGameOption) opt.get(okeys.nextElement());
            if (! op.hasChangeListener())
                op.addChangeListener(testCL);
        }

                // END TEST CODE
        */

        // OBSOLETE OPTIONS, REMOVED OPTIONS - Move its opt.put down here, commented out,
        //       including the version, date, and reason of the removal.
    }

    /** Lowest OTYPE value known at this version */
    public static final int OTYPE_MIN = 0;

    /** Option type: unknown (probably due to version mismatch)  */
    public static final int OTYPE_UNKNOWN = 0;

    /** Option type: boolean  */
    public static final int OTYPE_BOOL = 1;

    /** Option type: integer  */
    public static final int OTYPE_INT = 2;

    /** Option type: integer + boolean.  Both {@link #boolValue} and {@link #intValue} fields are used. */
    public static final int OTYPE_INTBOOL = 3;

    /** Option type: enumeration (1 of several possible choices, described with text strings,
     *  stored here as intVal).  Choices' strings are stored in {@link #enumVals}.
     */
    public static final int OTYPE_ENUM = 4;

    /** Option type: enumeration + boolean; see {@link #OTYPE_ENUM}.
     *  Like {@link #OTYPE_INTBOOL}, both {@link #boolValue} and {@link #intValue} fields are used.
     */
    public static final int OTYPE_ENUMBOOL = 5;

    /** Option type: text string (max string length is {@link #maxIntValue}, default value is "") */
    public static final int OTYPE_STR = 6;

    /** Option type: text string (like {@link #OTYPE_STR}) but hidden from view; is NOT encrypted,
     *  but contents show up as "*" when typed into a text field.
     */
    public static final int OTYPE_STRHIDE = 7;

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
     * <LI> {@link #OTYPE_ENUMBOOL} Enum plus bool; stored like {@link #OTYPE_INTBOOL}.
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
     * use {@link #optionsMinimumVersion(Hashtable)} or {@link #getMinVersion(Hashtable)} instead,
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
     * Should the server drop this option from game options, and not send over
     * the network (to reduce overhead), if the value is un-set or blank?
     * (Meaning not set (false) for {@link #OTYPE_BOOL}, {@link #OTYPE_ENUMBOOL}
     * or {@link #OTYPE_INTBOOL}; blank for {@link #OTYPE_STR} or {@link #OTYPE_STRHIDE})
     *<P>
     * Only recommended for seldom-used options.
     * The removal is done in {@link #adjustOptionsToKnown(Hashtable, Hashtable, boolean)}.
     * Once this flag is set for an option, it should not be un-set if the
     * option is changed in a later version.
     *<P>
     * For {@link #OTYPE_INTBOOL} and {@link #OTYPE_ENUMBOOL}, both the integer and
     * boolean values are checked against defaults.
     *<P>
     * This flag is ignored at the client when asking to create a new game:
     * <tt>NewGameOptionsFrame</tt> sends all options it has displayed, even those
     * which would be dropped because they're unused and they have this flag.
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
     * If option type is integer-valued ({@link #OTYPE_ENUM}, {@link #OTYPE_INTBOOL}, etc),
     * may contain a placeholder '#' where the value is typed onscreen.
     * For {@link #OTYPE_UNKNOWN}, an empty string.
     */
    public final String optDesc;   // OTYPE_* - if a new type is added, update this field's javadoc.

    /**
     * For type {@link #OTYPE_ENUM} and {@link #OTYPE_ENUMBOOL}, descriptive text for each possible value;
     * null for other types.  If a value is added or changed in a later version, the option's
     * {@link #lastModVersion} field must be updated, so server/client will know
     * to ask for the proper version with all available options.
     * Although the option's intVals are in the range 1 to n, this array is indexed 0 to n-1.
     */
    public final String[] enumVals;

    private boolean boolValue;
    private int     intValue;
    private String  strValue;  // no default value: is "", stored as null

    /**
     * The option's ChangeListener, or null.
     * @since 1.1.13
     */
    private transient ChangeListener optCL;

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
        this(OTYPE_UNKNOWN, key, Integer.MAX_VALUE, Integer.MAX_VALUE, false, 0, 0, 0, false, null, key);
    }

    /**
     * Create a new boolean game option ({@link #OTYPE_BOOL}).
     *
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version if this option is set (is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default value (true if set, false if not set)
     * @param dropIfUnused If this option's value is unset, should server not add it to game options
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
     * Create a new integer game option ({@link #OTYPE_INT}).
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
     * Create a new int+boolean game option ({@link #OTYPE_INTBOOL}).
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
     *           should server not add it to game options
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
     * Create a new enumerated game option ({@link #OTYPE_ENUM}).
     * The {@link #minIntValue} will be 1, {@link #maxIntValue} will be enumVals.length.
     * There is no dropIfUnused parameter for enum options,
     * because they have no 'blank' value.
     *
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default int value, in range 1 - n (n == number of possible values)
     * @param enumVals text to display for each possible choice of this option.
     *                Please see the explanation at {@link #initAllOptions()} about
     *                changing or adding to enumVals in later versions.
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
     * Create a new enumerated + boolean game option ({@link #OTYPE_ENUMBOOL}).
     * The {@link #minIntValue} will be 1, {@link #maxIntValue} will be enumVals.length.
     *
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultBoolValue Default value (true if set, false if not set)
     * @param defaultIntValue Default int value, in range 1 - n (n == number of possible values)
     * @param enumVals text to display for each possible choice of this option
     * @param dropIfUnused If this option's bool value is unset, and its int value is the default,
     *           should server not add it to game options
     *           or send over the network (to reduce overhead)?
     *           Only recommended if game behavior without the option is well-established
     *           (for example, trading is allowed unless option NT is present).
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the enum's popup-menu goes.
     *             If no placeholder is found, the value field appears at left,
     *             like boolean options.
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue,
     *        or if key length is > 3 or not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    public SOCGameOption(String key, int minVers, int lastModVers, boolean defaultBoolValue,
        int defaultIntValue, String[] enumVals, boolean dropIfUnused, String desc)
        throws IllegalArgumentException
    {
        this(OTYPE_ENUMBOOL, key, minVers, lastModVers,
             defaultBoolValue, defaultIntValue,
             1, enumVals.length, dropIfUnused, enumVals, desc);
    }

    /**
     * Create a new text game option ({@link #OTYPE_STR} or {@link #OTYPE_STRHIDE}).
     * The {@link #maxIntValue} will be maxLength.
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param maxLength   Maximum length, between 1 and 255 (for network bandwidth conservation)
     * @param hideTyping  Should type be {@link #OTYPE_STRHIDE} instead of {@link #OTYPE_STR}?
     * @param dropIfUnused If this option's value is blank, should
     *           server not add it to game options
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
     * @param otype   Option type; use caution, as this is unvalidated against
     *                {@link #OTYPE_MIN} or {@link #OTYPE_MAX}.
     * @param key     Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultBoolValue Default value (true if set, false if not set)
     * @param defaultIntValue Default int value, to use if option is set
     * @param minValue Minimum permissible value
     * @param maxValue Maximum permissible value; the width of the options-dialog
     *                 value field is based on the number of digits in maxValue.
     * @param dropIfUnused If this option's value is blank or unset, should
     *                 server not add it to game options?
     *                 See {@link #dropIfUnused} javadoc for more details.
     * @param enumVals Possible choice texts for {@link #OTYPE_ENUM} or {@link #OTYPE_ENUMBOOL}, or null;
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

        if ((key.length() > 3) && ! key.startsWith("DEBUG"))
            throw new IllegalArgumentException("Key length: " + key);
        if (! (isAlphanumericUpcaseAscii(key) || key.equals("-")))  // "-" is for server/network use
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
     * Copy constructor for enum-valued types ({@link #OTYPE_ENUM}, {@link #OTYPE_ENUMBOOL}),
     * for restricting (trimming) values for a certain client version.
     * @param enumOpt  Option object to copy.  If its <tt>defaultIntValue</tt> is greater than
     *                 <tt>keptEnumVals.length</tt>, the default will be reduced to that.
     * @param keptEnumVals  Enum values to keep; should be a subset of enumOpt.{@link #enumVals}
     *                 containing the first n values of that list.
     * @see #getMaxEnumValueForVersion(String, int)
     * @see #optionsNewerThanVersion(int, boolean, boolean, Hashtable)
     * @throws NullPointerException  if keptEnumVals is null
     */
    protected SOCGameOption(SOCGameOption enumOpt, String[] keptEnumVals)
        throws NullPointerException
    {
        // OTYPE_* - If enum-valued, add to javadoc.
        this(enumOpt.optType, enumOpt.optKey, enumOpt.minVersion, enumOpt.lastModVersion,
             enumOpt.defaultBoolValue,
             enumOpt.defaultIntValue <= keptEnumVals.length ? enumOpt.defaultIntValue : keptEnumVals.length,
             1, keptEnumVals.length, enumOpt.dropIfUnused,
             keptEnumVals, enumOpt.optDesc);
    }

    /**
     * Copy constructor for int-valued types ({@link #OTYPE_INT}, {@link #OTYPE_INTBOOL}),
     * for restricting (trimming) max value for a certain client version.
     * @param intOpt  Option object to copy.  If its <tt>defaultIntValue</tt> is greater than
     *                <tt>maxIntValue</tt>, the default will be reduced to that.
     * @param maxIntValue  Maximum value to keep, in the copy
     * @see #getMaxIntValueForVersion(String, int)
     * @see #optionsNewerThanVersion(int, boolean, boolean, Hashtable)
     * @since 1.1.08
     */
    protected SOCGameOption(SOCGameOption intOpt, final int maxIntValue)
    {
        // OTYPE_* - If int-valued, add to javadoc.
        this(intOpt.optType, intOpt.optKey, intOpt.minVersion, intOpt.lastModVersion,
             intOpt.defaultBoolValue,
             intOpt.defaultIntValue <= maxIntValue ? intOpt.defaultIntValue : maxIntValue,
             intOpt.minIntValue, maxIntValue, intOpt.dropIfUnused,
             null, intOpt.optDesc);
    }

    /**
     * Is this option set, if this option's type has a boolean component?
     * @return current boolean value of this option
     * @see SOCGame#isGameOptionSet(Hashtable, String)
     */
    public boolean getBoolValue() { return boolValue; }

    public void setBoolValue(boolean v) { boolValue = v; }

    /**
     * This option's integer value, if this option's type has an integer component.
     * @return current integer value of this option
     * @see SOCGame#getGameOptionIntValue(Hashtable, String)
     * @see SOCGame#getGameOptionIntValue(Hashtable, String, int, boolean)
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
     * @see SOCGame#getGameOptionStringValue(Hashtable, String)
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
     * Minimum game version supporting this option, given {@link #minVersion} and the option's current value.
     * The current value of an option can change its minimum version.
     * For example, a 5- or 6-player game will need a newer client than 4 players,
     * but option "PL"'s {@link #minVersion} is -1, to allow 2- or 3-player games with any client.
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
     * @param  opts  If null, return the minimum version supporting this option.
     *               Otherwise, the minimum version at which this option's value isn't changed
     *               (for compatibility) by the presence of other options. 
     * @return minimum version, or -1;
     *     same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     *     If <tt>opts != null</tt>, the returned version will either be -1 or >= 1107
     *     (the first version with game options).
     * @see #optionsMinimumVersion(Hashtable)
     * @see #getMaxEnumValueForVersion(String, int)
     * @see #getMaxIntValueForVersion(String, int)
     */
    public int getMinVersion(Hashtable opts)
    {
        if ((optType == OTYPE_BOOL) || (optType == OTYPE_INTBOOL)
            || (optType == OTYPE_ENUMBOOL))  // OTYPE_*: check here if boolean-valued
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
        // EXAMPLE:
        // For clients below 1.1.13, if game option PLB is set, option
        // PL must be changed to 5 or 6 to force use of the 6-player board.
        // For clients 1.1.13 and newer, PLB is recognized at the client,
        // so PL can be less than 5 and still use the 6-player board.
        // So, if PLB is set, and PL > 4, return 1113 because client
        // versions <= 1112 would need PL changed.
        // If PLB is set, and PL <= 4, return 1108 (the first version with a 6-player board).

        // SAMPLE CODE: (without ADDITIONAL CHECK)
        /*
        if (optKey.equals("N7") && (intValue == 42))
        {
            return 1108;
        }
        */
        // END OF SAMPLE CODE.
        // The following non-sample code demonstrates the ADDITIONAL CHECK:

        if (optKey.equals("PL"))
        {
            if ((opts != null) && (intValue <= 4) && opts.containsKey("PLB"))
            {
                // For clients below 1.1.13, if PLB is set,
                // PL must be changed to 5 or 6 to force use of the 6-player board.
                // For clients 1.1.13 and newer, PLB is recognized at the client,
                // so PL can be less than 5 and still use the 6-player board.

                SOCGameOption plb = (SOCGameOption) opts.get("PLB");
                if (plb.boolValue)
                    return 1113;
            }
            if (intValue > 4)
                return 1108;  // 5 or 6 players
        }

        return minVersion;
    }

    /**
     * For use at server, for enum options where some values require a newer client version.
     * Given the option's keyname and a version, what is the maximum permitted enum value?
     * The server, when giving option info to a connecting client, can remove the too-new values,
     * and send only the permitted values to an older client.
     *
     * @param optKey Option's keyname
     * @param vers   Version of client, same format as {@link SOCVersion#getVersionNumber()}
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
     * @param vers   Version of client, same format as {@link SOCVersion#getVersionNumber()}
     * @return  Maximum permitted value for this version, or {@link Integer#MAX_VALUE}
     *          if this option has no restriction.
     * @since 1.1.08
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
        case OTYPE_ENUMBOOL:
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
     * @return string of name-value pairs, same format as {@link #packOptionsToString(Hashtable, boolean)};
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
        return packOptionsToString(ohash, hideEmptyStringOpts, -2);
    }

    /**
     * Utility - build a string of option name-value pairs,
     * adjusting for old clients if necessary.
     * This can be unpacked with {@link #parseOptionsToHash(String)}.
     * See {@link #packOptionsToString(Hashtable, boolean)} javadoc for details.
     * 
     * @param ohash Hashtable of SOCGameOptions, or null
     * @param hideEmptyStringOpts omit string-valued options which are empty?
     *            Suitable only for sending defaults.
     * @param cliVers  Client version; assumed >= {@link soc.message.SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
     *            If any game's options need adjustment for an older client, cliVers triggers that.
     *            Use -2 if the client version doesn't matter, or if adjustment should not be done.
     * @return string of name-value pairs, or "-" for an empty or null ohash;
     *         see {@link #packOptionsToString(Hashtable, boolean)} javadoc for details.
     * @throws ClassCastException if hashtable contains anything other
     *         than SOCGameOptions
     */
    public static String packOptionsToString(Hashtable ohash, boolean hideEmptyStringOpts, final int cliVers)
        throws ClassCastException
    {
	if ((ohash == null) || ohash.size() == 0)
	    return "-";

	// If the "PLB" option is set, old client versions
	//  may need adjustment of the "PL" option.
	final boolean hasOptPLB = (cliVers != -2) && ohash.containsKey("PLB")
	    && ((SOCGameOption) ohash.get("PLB")).boolValue;

	// Pack all non-unknown options:
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

	    boolean wroteValueAlready = false;
	    if (cliVers != -2)
	    {
	        if (hasOptPLB && op.optKey.equals("PL")
	            && (cliVers < 1113) && (op.intValue < 5))
	        {
	            // When "PLB" is used (Use 6-player board)
	            // but the client is too old to recognize PLB,
	            // make sure "PL" is large enough to make the
	            // client use that board.

	            final int realValue = op.intValue;
	            op.intValue = 5;  // big enough for 6-player
	            op.packValue(sb);
                    wroteValueAlready = true;
	            op.intValue = realValue;
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
            case OTYPE_ENUMBOOL:
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
     * @param opts  a set of SOCGameOptions; not null
     * @return the highest 'minimum version' among these options, or -1
     * @throws ClassCastException if values contain a non-{@link SOCGameOption}
     * @see #optionsMinimumVersion(Hashtable, boolean)
     * @see #getMinVersion(Hashtable)
     */
    public static int optionsMinimumVersion(Hashtable opts)
        throws ClassCastException
    {
        return optionsMinimumVersion(opts, false);
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
     *<P>
     * <b>Backwards-compatibility support: <tt>minCliVersionForUnchangedOpts</tt> parameter:</b><br>
     * Occasionally, an older client version supports a new option, but only by changing
     * the value of some other options it recognizes.  If this parameter is true,
     * this method will calculate the minimum client version at which options are understood
     * without backwards-compatibility changes to their values.
     *
     * @param opts  a set of SOCGameOptions; not null
     * @param minCliVersionForUnchangedOpts  If true, return the minimum version at which these
     *         options' values aren't changed (for compatibility) by the presence of new options.
     * @return the highest 'minimum version' among these options, or -1.
     *         If <tt>minCliVersionForUnchangedOpts</tt>, the returned version will either be -1 or >= 1107
     *         (the first version with game options).
     * @throws ClassCastException if values contain a non-{@link SOCGameOption}
     * @see #optionsMinimumVersion(Hashtable)
     * @see #getMinVersion(Hashtable)
     */
    public static int optionsMinimumVersion(Hashtable opts, final boolean minCliVersionForUnchangedOpts)
	throws ClassCastException
    {
	int minVers = -1;
	final Hashtable oarg = minCliVersionForUnchangedOpts ? opts : null;
	for (Enumeration e = opts.keys(); e.hasMoreElements(); )
	{
	    SOCGameOption op = (SOCGameOption) opts.get(e.nextElement());
            int opMin = op.getMinVersion(oarg);  // includes any option value checking for minVers
	    if (opMin > minVers)
		minVers = opMin;
	}
	return minVers;
    }

    /**
     * Compare a set of options against the specified version.
     * Make a list of all which are new or changed since that version.
     *<P>
     * This method has 2 modes, because it's called for 2 different purposes:
     *<UL>
     * <LI> sync client-server known-option info, in general: <tt>checkValues</tt> == false
     * <LI> check if client can create game with a specific set of option values: <tt>checkValues</tt> == true
     *</UL>
     * See <tt>checkValues</tt> for method's behavior in each mode.
     *
     * @param vers  Version to compare known options against
     * @param checkValues  Which mode: Check options' current values and {@link #minVersion},
     *              not their {@link #lastModVersion}?
     *              An option's minimum version can increase based
     *              on its value; see {@link #getMinVersion(Hashtable)}.
     * @param trimEnums  For enum-type options where minVersion changes based on current value,
     *              should we remove too-new values from the returned option info?
     *              This lets us send only the permitted values to an older client.
     * @param opts  Set of {@link SOCGameOption}s to check current values;
     *              if null, use the "known option" set
     * @return Vector of the newer {@link SOCGameOption}s, or null
     *     if all are known and unchanged since <tt>vers</tt>.
     */
    public static Vector optionsNewerThanVersion(final int vers, final boolean checkValues, final boolean trimEnums, Hashtable opts)
    {
        if (opts == null)
            opts = allOptions;
        Vector uopt = null;  // add problems to uopt
        
        for (Enumeration e = opts.elements(); e.hasMoreElements(); )
        {
            SOCGameOption opt = (SOCGameOption) e.nextElement();

            if (checkValues)
            {
                if (opt.getMinVersion(null) <= vers)
                    opt = null;  // not too new
            } else {
                if (opt.lastModVersion <= vers)
                    opt = null;  // not modified since vers
            }

            if (trimEnums && (opt != null)
                && (opt.minVersion <= vers))  // vers is new enough to use this opt
            {
                if (opt.enumVals != null)
                {
                    // Possibly trim enum values. (OTYPE_ENUM, OTYPE_ENUMBOOL)
                    // OTYPE_* - Add here in comment if enum-valued option type
                    final int ev = getMaxEnumValueForVersion(opt.optKey, vers);
                    if (ev < opt.enumVals.length)
                        opt = trimEnumForVersion(opt, vers);
                } else if (opt.maxIntValue != opt.minIntValue)
                {
                    // Possibly trim max int value. (OTYPE_INT, OTYPE_INTBOOL)
                    // OTYPE_* - Add here in comment if int-valued option type
                    final int iv = getMaxIntValueForVersion(opt.optKey, vers);
                    if ((iv != opt.maxIntValue) && (iv != Integer.MAX_VALUE))
                        opt = new SOCGameOption(opt, iv);
                }
            }

            if (opt != null)
            {
                if (uopt == null)
                    uopt = new Vector();
                uopt.addElement(opt);                
            }
        }

        return uopt;
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
    public static SOCGameOption trimEnumForVersion(SOCGameOption opt, final int vers)
    {
        final int ev = getMaxEnumValueForVersion(opt.optKey, vers);
        if ((ev == Integer.MAX_VALUE) || (ev == opt.enumVals.length))
            return opt;
        String[] evkeep = new String[ev];
        System.arraycopy(opt.enumVals, 0, evkeep, 0, ev);
        return new SOCGameOption(opt, evkeep);  // Copy option and restrict enum values
    }

    /**
     * Compare a set of options with known-good values.
     * If any are above/below maximum/minimum, clip to the max/min value in knownOpts.
     * If any are unknown, return a description. Will still check (and clip) the known ones.
     * If any boolean or string-valued options are default, and unset/blank, and
     * their {@link #dropIfUnused} flag is set, remove them from newOpts.
     * For {@link #OTYPE_INTBOOL} and {@link #OTYPE_ENUMBOOL}, both the integer and
     * boolean values are checked against defaults.
     *<P>
     * If <tt>doServerPreadjust</tt> is true, then the server might also change some
     * option values before creating the game, for overall consistency of the set of options.
     * This is a server-side equivalent to the client-side {@link ChangeListener}s.
     * For example, if <tt>"PL"</tt> (number of players) > 4, but <tt>"PLB"</tt> (use 6-player board)
     * is not set, <tt>doServerPreadjust</tt> wil set the <tt>"PLB"</tt> option.
     *
     * @param newOpts Set of SOCGameOptions to check against knownOpts;
     *            an option's current value will be changed if it's outside of
     *            the min/max for that option in knownOpts.
     *            Must not be null.
     * @param knownOpts Set of known SOCGameOptions to check against, or null to use
     *            the server's static copy
     * @param doServerPreadjust  If true, we're calling from the server before creating a game;
     *            pre-adjust any values for consistency.
     *            This is a server-side equivalent to the client-side {@link ChangeListener}s.
     *            (Added in 1.1.13)
     * @return <tt>null</tt> if all are known; or, a human-readable problem description if:
     *            <UL>
     *            <LI> any of <tt>newOpts</tt> are unknown
     *            <LI> or an opt's type differs from that in knownOpts
     *            <LI> or an opt's {@link #lastModVersion} differs from in knownOpts
     *            </UL>
     * @throws IllegalArgumentException if newOpts contains a non-SOCGameOption
     */
    public static StringBuffer adjustOptionsToKnown
        (Hashtable newOpts, Hashtable knownOpts, final boolean doServerPreadjust)
        throws IllegalArgumentException
    {
        if (knownOpts == null)
            knownOpts = allOptions;

        if (doServerPreadjust)
        {
            // NEW_OPTION: If you created a ChangeListener, you should probably add similar code
            //   code here. Set or change options if it makes sense; if a user has deliberately
            //    set a boolean option, think carefully before un-setting it and surprising them.

            // Set PLB if PL>4
            SOCGameOption optPL = (SOCGameOption) newOpts.get("PL");
            if ((optPL != null) && (optPL.getIntValue() > 4))
            {
                SOCGameOption optPLB = (SOCGameOption) newOpts.get("PLB");
                if (optPLB == null)
                {
                    try
                    {
                        optPLB = (SOCGameOption) (((SOCGameOption) allOptions.get("PLB")).clone());
                    }
                    catch (CloneNotSupportedException e)
                    {
                        // required stub; is Cloneable, so won't happen
                    }
                    optPLB.boolValue = true;
                    newOpts.put("PLB", optPLB);
                }
                else if (! optPLB.boolValue)
                {
                    optPLB.boolValue = true;
                }
            }
        }  // if(doServerPreadjust)

        // OTYPE_* - adj javadoc above (re dropIfUnused) if a string-type or bool-type is added.

        StringBuffer optProblems = new StringBuffer();

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
	    if (knownOp == null)
	    {
                allKnown = false;
                optProblems.append(op.optKey);
                optProblems.append(": unknown. ");
	    }
	    else if (knownOp.optType != op.optType)
	    {
                allKnown = false;
                optProblems.append(op.optKey);
                optProblems.append(": optType mismatch (");
                optProblems.append(knownOp.optType);
                optProblems.append(" != ");
                optProblems.append(op.optType);
                optProblems.append("). ");
	    } else {
	        // Clip int values, check default values, check dropIfUnused

		if (knownOp.lastModVersion != op.lastModVersion)
		{
		    allKnown = false;
		    optProblems.append(op.optKey);
		    optProblems.append(": lastModVersion mismatch (");
		    optProblems.append(knownOp.lastModVersion);
		    optProblems.append(" != ");
                    optProblems.append(op.lastModVersion);
                    optProblems.append("). ");
		}

		switch (op.optType)  // OTYPE_*
		{
		case OTYPE_INT:
		case OTYPE_INTBOOL:
		case OTYPE_ENUM:
                case OTYPE_ENUMBOOL:
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
                        // except when also boolean-type: OTYPE_INTBOOL and OTYPE_ENUMBOOL.
                        if (((op.optType == OTYPE_INTBOOL) || (op.optType == OTYPE_ENUMBOOL))
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

	if (allKnown)
	    return null;
	else
	    return optProblems;
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
     * Get the list of {@link SOCGameOption}s whose {@link #refreshDisplay()}
     * methods have been called, and clear the internal static copy.
     * Not thread-safe, assumes only 1 GUI thread or 1 NewGameOptionsFrame at a time.
     * @return the list, or null if refreshDisplay wasn't called on any option
     * @since 1.1.13
     */
    public static final Vector getAndClearRefreshList()
    {
        Vector refr = refreshList;
        refreshList = null;
        if ((refr != null) && (refr.size() == 0))
            refr = null;
        return refr;
    }

    /**
     * If this game option is displayed on-screen, refresh it;
     * call this after changing the value.
     * @since 1.1.13
     */
    public void refreshDisplay()
    {
        if (refreshList == null)
            refreshList = new Vector();
        else if (refreshList.contains(this))
            return;
        refreshList.addElement(this);
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
     *    as {@link #packKnownOptionsToString(boolean)}.
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer(optKey);
        sb.append('=');
        packValue(sb);
        return sb.toString();
    }

    /**
     * Compare two options, for display purposes. ({@link Comparable} interface)
     * Two gameoptions are considered equal if they have the same {@link #optKey}.
     * Greater/lesser is determined by {@link #optDesc}.{@link String#compareTo(String) compareTo()}.
     * @param other A SOCGameOption to compare, or another object;  if other isn't a
     *              gameoption, the {@link #hashCode()}s are compared.
     */
    public int compareTo(Object other)
    {
        if (other instanceof SOCGameOption)
        {
            SOCGameOption oopt = (SOCGameOption) other;
            if (optKey.equals(oopt.optKey))
                return 0;
            else
                return optDesc.compareTo(oopt.optDesc);
        }
        return hashCode() - other.hashCode();
    }

    /**
     * Listener for option value changes <em>at the client</em> during game creation.
     * When the user changes an option, allows a related option to change.
     * For example, when the max players is changed to 5 or 6,
     * the listener can check the box for "use 6-player board".
     *<P>
     * Once written, the server can't do anything to update the client's
     * ChangeListener code, so be careful and write them defensively.
     *<P> 
     * Callback method is {@link #valueChanged(SOCGameOption, Object, Object, Hashtable)}.
     *<P>
     * For <em>server-side</em> consistency adjustment of values before creating games,
     * add code to {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable, boolean)}
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
         */
        public void valueChanged
          (final SOCGameOption opt, final Object oldValue, final Object newValue, Hashtable currentOpts);
    }
}
