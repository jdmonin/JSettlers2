/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012 Jeremy D Monin <jeremy@nand.net>
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
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import soc.message.SOCMessage;

/**
 * Scenarios for game rules and options on the {@link SOCBoardLarge large sea board}.
 * Chooseable at game creation.
 * This class has two purposes:
 *<UL>
 * <LI> Per-game values of options
 * <LI> Static dictionary of known scenarios;
 *    see {@link #initAllScenarios()} for the current list.
 *</UL>
 *<P>
 * For information about adding or changing game scenarios in a
 * later version of JSettlers, please see {@link #initAllScenarios()}.
 *<P>
 * All in-game code uses the 2-letter or 3-letter key strings to query and change
 * game option settings; only a very few places use SOCGameOption
 * objects.  To search the code for uses of a game option, search for
 * its capitalized key string.
 * You will see calls to {@link SOCGame#isGameOptionDefined(String)},
 * {@link SOCGame#getGameOptionIntValue(Hashtable, String, int, boolean)}, etc.
 *<P>
 * Most option name keys are 2 or 3 characters; before 2.0.00, the maximum length was 3.
 * The maximum key length is now 8, but older clients will reject keys longer than 3.
 *<P>
 * Option name keys must start with a letter and contain only ASCII uppercase
 * letters ('A' through 'Z') and digits ('0' through '9'), in order to normalize
 * handling and network message formats.  This is enforced in constructors via
 * {@link #isAlphanumericUpcaseAscii(String)}.
 * Version 2.0.00 and newer allow '_'; please check {@link #minVersion},
 * name keys with '_' can't be sent to older clients.
 * Options starting with '_' are meant to be set by the server during game creation,
 * not requested by the client. They're set during
 * {@link #adjustOptionsToKnown(Hashtable, Hashtable, boolean) adjustOptionsToKnown(Hashtable, null, true)}.
 *<P>
 * For the same reason, option string values (and enum choices) must not contain
 * certain characters or span more than 1 line; this is checked by calling
 * {@link SOCMessage#isSingleLineAndSafe(String)} within constructors and setters.
 *<P>
 * The "known options" are initialized via {@link #initAllScenarios()}.  See that
 * method's description for more details on adding an option.
 * If a new option changes previously expected behavior of the game, it should default to
 * the old behavior; its default value on your server can be changed at runtime.
 *<P>
 * Since 1.1.13, when the user changes options while creating a new game, related
 * options can be changed on-screen for consistency; see {@link SOCScenario.ChangeListener} for details.
 * If you create a ChangeListener, consider adding equivalent code to
 * {@link #adjustOptionsToKnown(Hashtable, Hashtable, boolean)} for the server side.
 *<P>
 * <B>Sea Board Scenarios:</B><br>
 * Game scenarios were introduced with the large sea board in 2.0.00.
 * Game options are used to indicate which {@link SOCScenarioPlayerEvent scenario events}
 * and rules are possible in the current game.
 * These all start with <tt>"_SC_"</tt> and have a static key string;
 * an example is {@link #K_SC_SANY} for scenario game option <tt>"_SC_SANY"</tt>.
 *<P>
 * <B>Version negotiation:</B><br>
 * Game options were introduced in 1.1.07; check server, client versions against
 * {@link soc.message.SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
 * Scenarios were introduced in 2.0.00, and negotiate the same way.
 * Each option has version information, because options can be added or changed
 * with new versions of JSettlers.  Since games run on the server, the server is
 * authoritative about game options:  If the client is newer, it must defer to the
 * server's older set of known options.  At client connect, the client compares its
 * JSettlers version number to the server's, and asks for any changes to options if
 * their versions differ.
 *<P>
 * @author Jeremy D. Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCScenario implements Cloneable, Comparable<Object>
{
    /**
     * Set of "known scenarios".
     * allScenarios must never be null, because other places assume it is filled.
     */
    private static Map<String, SOCScenario> allScenarios = initAllScenarios();

    /**
     * Create a set of the known options.
     * This method creates and returns a Hashtable, but does not set the static {@link #allScenarios} field.
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
     *<LI> DH  Dev Cards for house rules (swap/destroy)
     *</UL>
     *  * Grouping: PLB is 3 characters, not 2, and its first 2 characters match an
     *    existing option.  So in NewGameOptionsFrame, it appears on the line following
     *    the PL option in client version 1.1.13 and above.
     *
     * <h3>Current Game Scenario options:</h3>
     *<UL>
     *<LI> {@link #K_SC_SANY _SC_SANY}  SVP to settle in any new land area:
     *                                  {@link SOCScenarioPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}
     *<LI> {@link #K_SC_SEAC _SC_SEAC}  2 SVP each time settle in another new land area:
     *                                  {@link SOCScenarioPlayerEvent#SVP_SETTLED_EACH_NEW_LANDAREA}
     *<LI> {@link #K_SC_FOG  _SC_FOG}   A hex has been revealed from behind fog:
     *                                  {@link SOCScenarioGameEvent#SGE_FOG_HEX_REVEALED}
     *<LI> {@link #K_SC_0RVP _SC_0RVP}  No VP for longest road / longest trade route
     *<LI> {@link #K_SC_3IP  _SC_3IP}   Third initial settlement and road placement
     *<LI> {@link #K_SC_CLVI _SC_CLVI}  Cloth trade with neutral {@link SOCVillage villages}
     *</UL>
     *
     * <h3>If you want to add a game option:</h3>
     *<UL>
     *<LI> Choose an unused 2-character key name: for example, "PL" for "max players".
     *   All in-game code uses these key strings to query and change
     *   game option settings; only a very few places use SOCGameOption
     *   objects, and you won't need to adjust those places.
     *   The list of already-used key names is here within initAllScenarios().
     *<LI> Decide which {@link #optType option type} your option will be
     *   (boolean, enumerated, int+bool, etc.), and its default value.
     *   Typically the default will let the game behave as it did before
     *   the option existed (for example, the "max players" default is 4).
     *   Its default value on your own server can be changed at runtime.
     *<LI> Decide if all client versions can use your option.  Typically, if the option
     *   requires server changes but not any client changes, all clients can use it.
     *   (For example, "N7" for "roll no 7s early in the game" is strictly server-side.)
     *<LI> Create the option by calling opt.put here in initAllScenarios.
     *   Use the current version for the "last modified" field.
     *<LI> If only <em>some values</em> of the option will require client changes,
     *   also update {@link #getMinVersion()}.  (For example, if "PL"'s value is 5 or 6,
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
     *   Things you can't change about a scenario, because inconsistencies would occur:
     *<UL>
     *<LI> {@link #scKey name key}
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
     *<LI> Change the option here in initAllScenarios; change the "last modified" field to
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
     * If an option isn't in its known list ({@link #initAllScenarios()}), the client won't be
     * allowed to ask for it.  Any obsolete options should be kept around as commented-out code.
     *
     * @return a fresh copy of the "known" options, with their hardcoded default values
     */
    public static Map<String, SOCScenario> initAllScenarios()
    {
        Map<String, SOCScenario> opt = new HashMap<String, SOCScenario>();

        final SOCScenario plb = new SOCScenario
                ("PLB", 1108, 1113, false, true, "Use 6-player board");
        opt.put("PLB", plb);
        // TODO PLL for SOCBoardLarge: Decide final name
        opt.put("PLL", new SOCScenario
                ("PLL", 2000, 2000, false, true, "Experimental: Use large board"));
        opt.put("RD", new SOCScenario
                ("RD", -1, 1107, false, false, "Robber can't return to the desert"));
        opt.put("DH", new SOCScenario
                ("DH", 2000, 2000, false, true, "Experimental: Dev Cards for house rules (swap/destroy)"));
                // TODO no robot players for DH

        // Game scenario options (rules and events)
        opt.put(K_SC_SANY, new SOCScenario
                (K_SC_SANY, 2000, 2000, false, true, "Scenarios: SVP for your first settlement on any island"));
        opt.put(K_SC_SEAC, new SOCScenario
                (K_SC_SEAC, 2000, 2000, false, true, "Scenarios: 2 SVP for your first settlement on each island"));
        opt.put(K_SC_FOG, new SOCScenario
                (K_SC_FOG, 2000, 2000, false, true, "Scenarios: Some land hexes initially hidden by fog"));
        opt.put(K_SC_0RVP, new SOCScenario
                (K_SC_0RVP, 2000, 2000, false, true, "Scenarios: No longest trade route VP (no Longest Road)"));
        opt.put(K_SC_3IP, new SOCScenario
                (K_SC_3IP, 2000, 2000, false, true, "Scenarios: Third initial settlement"));
        opt.put(K_SC_CLVI, new SOCScenario
                (K_SC_CLVI, 2000, 2000, false, true, "Scenarios: Cloth Trade with neutral villages"));

        // NEW_OPTION - Add opt.put here at end of list, and update the
        //       list of "current known options" in javadoc just above.

        return opt;

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

    // Game option keynames for scenario flags.
    // Not all scenario keynames have scenario events, some are just properties of the game.

    /**
     * Scenario key <tt>_SC_SANY</tt> for {@link SOCScenarioPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}.
     * @since 2.0.00
     */
    public static final String K_SC_SANY = "_SC_SANY";

    /**
     * Scenario key <tt>_SC_SEAC</tt> for {@link SOCScenarioPlayerEvent#SVP_SETTLED_EACH_NEW_LANDAREA}.
     * @since 2.0.00
     */
    public static final String K_SC_SEAC = "_SC_SEAC";

    /**
     * Scenario key <tt>_SC_FOG</tt> for {@link SOCScenarioGameEvent#SGE_FOG_HEX_REVEALED}.
     * @since 2.0.00
     */
    public static final String K_SC_FOG = "_SC_FOG";

    /**
     * Scenario key <tt>_SC_0RVP</tt>: No "longest trade route" VP / Longest Road.
     * @since 2.0.00
     */
    public static final String K_SC_0RVP = "_SC_0RVP";

    /**
     * Scenario key <tt>_SC_3IP</tt>: Third initial placement of settlement and road.
     * Initial resources are given for this one, not the second settlement.
     * @since 2.0.00
     */
    public static final String K_SC_3IP = "_SC_3IP";

    /**
     * Scenario key <tt>_SC_CLVI</tt> for {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}:
     * Cloth Trade with neutral {@link SOCVillage villages}.
     * Villages and cloth are in a game only if this option is set.
     * @since 2.0.00
     */
    public static final String K_SC_CLVI = "_SC_CLVI";

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
     * Scenario key/technical name: Short alphanumeric name (8 characters, uppercase, starting with a letter)
     */
    public final String scKey;

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
    public final String scDesc;

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
     * Create a new game option of unknown type ({@link #OTYPE_UNKNOWN}).
     * Minimum version will be {@link Integer#MAX_VALUE}.
     * Value will be false/0. optDesc will be an empty string.
     * @param key   Alphanumeric 2-character code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     * @throws IllegalArgumentException if key length is > 3 or not alphanumeric,
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    public SOCScenario(String key)
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
    public SOCScenario(String key, int minVers, int lastModVers,
        boolean defaultValue, boolean dropIfUnused, String desc)
        throws IllegalArgumentException
    {
	this(OTYPE_BOOL, key, minVers, lastModVers, defaultValue, 0, 0, 0, dropIfUnused, null, desc);
    }

    /**
     * Create a new game scenario - common constructor.
     * @param otype   Option type; use caution, as this is unvalidated against
     *                {@link #OTYPE_MIN} or {@link #OTYPE_MAX}.
     * @param key     Alphanumeric uppercase code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     *                Keys can be up to 8 characters long.
     * @param minVers Minimum client version for games where this option is set (its boolean field is true), or -1.
     *                If not -1, <tt>minVers</tt> must be at least 2000.
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
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    protected SOCScenario(int otype, String key, int minVers, int lastModVers,
        boolean defaultBoolValue, int defaultIntValue,
        int minValue, int maxValue, boolean dropIfUnused,
        String[] enumVals, String desc)
        throws IllegalArgumentException
    {
	// validate & set option properties:

        final int L = key.length(); 
        if ((L > 3) && ! key.startsWith("DEBUG"))
        {
            if (L > 8)
                throw new IllegalArgumentException("Key length > 8: " + key);
        }
        if (! (isAlphanumericUpcaseAscii(key) || key.equals("-")))  // "-" is for server/network use
            throw new IllegalArgumentException("Key not alphanumeric: " + key);
        if ((minVers < 2000) && (minVers != -1))
            throw new IllegalArgumentException("minVers " + minVers + " for key " + key);
        if ((lastModVers < 2000) && (lastModVers != -1))
            throw new IllegalArgumentException("lastModVers " + lastModVers + " for key " + key);
        if (! SOCMessage.isSingleLineAndSafe(desc))
            throw new IllegalArgumentException("desc fails isSingleLineAndSafe");

	scKey = key;
	optType = otype;
	minVersion = minVers;
	lastModVersion = lastModVers;
	this.defaultBoolValue = defaultBoolValue;
	this.defaultIntValue = defaultIntValue;
	minIntValue = minValue;
	maxIntValue = maxValue;
	this.dropIfUnused = dropIfUnused;
        this.enumVals = enumVals;
	scDesc = desc;

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
     * @see #scenariosNewerThanVersion(int, Map)
     * @throws NullPointerException  if keptEnumVals is null
     */
    protected SOCScenario(SOCScenario enumOpt, String[] keptEnumVals)
        throws NullPointerException
    {
        // OTYPE_* - If enum-valued, add to javadoc.
        this(enumOpt.optType, enumOpt.scKey, enumOpt.minVersion, enumOpt.lastModVersion,
             enumOpt.defaultBoolValue,
             enumOpt.defaultIntValue <= keptEnumVals.length ? enumOpt.defaultIntValue : keptEnumVals.length,
             1, keptEnumVals.length, enumOpt.dropIfUnused,
             keptEnumVals, enumOpt.scDesc);
    }

    /**
     * Copy constructor for int-valued types ({@link #OTYPE_INT}, {@link #OTYPE_INTBOOL}),
     * for restricting (trimming) max value for a certain client version.
     * @param intOpt  Option object to copy.  If its <tt>defaultIntValue</tt> is greater than
     *                <tt>maxIntValue</tt>, the default will be reduced to that.
     * @param maxIntValue  Maximum value to keep, in the copy
     * @see #getMaxIntValueForVersion(String, int)
     * @see #scenariosNewerThanVersion(int, Map)
     * @since 1.1.08
     */
    protected SOCScenario(SOCScenario intOpt, final int maxIntValue)
    {
        // OTYPE_* - If int-valued, add to javadoc.
        this(intOpt.optType, intOpt.scKey, intOpt.minVersion, intOpt.lastModVersion,
             intOpt.defaultBoolValue,
             intOpt.defaultIntValue <= maxIntValue ? intOpt.defaultIntValue : maxIntValue,
             intOpt.minIntValue, maxIntValue, intOpt.dropIfUnused,
             null, intOpt.scDesc);
    }

    /**
     * Is this option set, if this option's type has a boolean component?
     * @return current boolean value of this option
     * @see SOCGame#isGameOptionSet(Hashtable, String)
     */
    public boolean getBoolValue() { return boolValue; }

    public void setBoolValue(boolean v) { boolValue = v; }

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
     * @return minimum version, or -1;
     *     same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     * @see #optionsMinimumVersion(Map)
     * @see #getMaxEnumValueForVersion(String, int)
     * @see #getMaxIntValueForVersion(String, int)
     */
    public int getMinVersion()
    {
        if (! boolValue)
            return -1;  // Option not set: any client version is OK

        // NEW_OPTION:
        // Any option value checking for minVers is done here.
        // None of the current options change minVers based on their value.
        // If your option changes the minVers based on its current value,
        // check the optKey and current value, and return the appropriate version,
        // instead of just returning minVersion.

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
     * @see #addKnownOption(SOCScenario)
     */
    public static Map<String, SOCScenario> getAllKnownOptions()
    {
        return cloneOptions(allScenarios);
    }

    /**
     * Add a new known option (presumably received from a server of newer or older version),
     * or update the option's information.
     * @param onew New option, or a changed version of an option we already know.
     *             If onew.optType == {@link #OTYPE_UNKNOWN}, will remove from the known table.
     * @return true if it's new, false if we already had that key and it was updated
     * @see #getAllKnownOptions()
     */
    public static boolean addKnownOption(SOCScenario onew)
    {
	final String oKey = onew.scKey;
	final boolean hadIt = allScenarios.containsKey(oKey);
	if (hadIt)
	    allScenarios.remove(oKey);
	if (onew.optType != OTYPE_UNKNOWN)
	    allScenarios.put(oKey, onew);
	return ! hadIt;
    }

    /**
     * Set the current value of a known option, based on the current value of
     * another object with the same {@link #scKey}.
     * If there is no known option with oCurr.{@link #scKey}, it is ignored and nothing is set.
     * @param scCurr Option with the requested current value
     * @throws  IllegalArgumentException if value is not permitted; note that
     *            intValues outside of range are silently clipped, and will not
     *            throw this exception.
     */
    public static void setKnownOptionCurrentValue(SOCScenario scCurr)
        throws IllegalArgumentException
    {
        final String scKey = scCurr.scKey;
        SOCScenario scKnown = allScenarios.get(scKey);
        if (scKnown == null)
            return;
        switch (scKnown.optType)  // OTYPE_*
        {
        case OTYPE_BOOL:
            scKnown.boolValue = scCurr.boolValue;
            break;

        }
    }

    /**
     * @param scens  a map of SOCScenario, or null
     * @return a deep copy of all scenario objects within scens, or null if scens is null
     */
    public static Map<String, SOCScenario> cloneOptions(Map<String, SOCScenario> scens)
    {
    	if (scens == null)
    	    return null;
    
    	Map<String, SOCScenario> scens2 = new HashMap<String, SOCScenario>();
    	for (Map.Entry<String, SOCScenario> e : scens.entrySet())
    	{
    	    SOCScenario sc = e.getValue();
    	    try
    	    {
    	        scens2.put(sc.scKey, (SOCScenario) sc.clone());
    	    } catch (CloneNotSupportedException ce) {
    	        // required, but not expected to happen
    	        throw new IllegalStateException("Clone failed!", ce);
    	    }
    	}
    	return scens2;
    }

    /**
     * @return information about a known option, or null if none with that key
     */
    public static SOCScenario getOption(String key)
    {
        return allScenarios.get(key);  // null is ok
    }

    /**
     * Search these options and find any unknown ones (type {@link #OTYPE_UNKNOWN})
     * @param opts map of SOCScenarios
     * @return List(SOCScenario) of unknown options, or null if all are known
     */
    public static ArrayList<String> findUnknowns(Map<String, SOCScenario> opts)
    {
        ArrayList<String> unknowns = null;
        for (Map.Entry<String, SOCScenario> e : opts.entrySet())
        {
            SOCScenario sc = e.getValue();
            if (sc.optType == SOCScenario.OTYPE_UNKNOWN)
            {
                if (unknowns == null)
                    unknowns = new ArrayList<String>();
                unknowns.add(sc.scKey);
            }
        }
        return unknowns;
    }

    /**
     * Utility - build a string of option name-value pairs from the
     *           {@link #getAllKnownOptions() known options}' current values.
     *
     * @return string of name-value pairs, same format as {@link #packOptionsToString(Hashtable, boolean)};
     *         any gameoptions of {@link #OTYPE_UNKNOWN} will not be
     *         part of the string.
     * @see #parseOptionsToHash(String)
     */
    public static String packKnownOptionsToString()
    {
        return packOptionsToString(allScenarios);
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
    public static String packOptionsToString
        (Map<String, SOCScenario> ohash)
	throws ClassCastException
    {
        return packOptionsToString(ohash, -2);
    }

    /**
     * Utility - build a string of option name-value pairs,
     * adjusting for old clients if necessary.
     * This can be unpacked with {@link #parseOptionsToHash(String)}.
     * See {@link #packOptionsToString(Hashtable, boolean)} javadoc for details.
     * 
     * @param scMap Map of SOCScenarios, or null
     * @param cliVers  Client version; assumed >= 2000.
     *            If any game's scenarios need adjustment for an older client, cliVers triggers that.
     *            Use -2 if the client version doesn't matter, or if adjustment should not be done.
     * @return string of name-value pairs, or "-" for an empty or null scMap;
     *         see {@link #packOptionsToString(Hashtable, boolean)} javadoc for details.
     * @throws ClassCastException if map contains anything other
     *         than SOCScenario
     */
    public static String packOptionsToString
        (Map<String, SOCScenario> scMap, final int cliVers)
        throws ClassCastException
    {
    	if ((scMap == null) || scMap.size() == 0)
    	    return "-";
    
    	// Pack all non-unknown options:
    	StringBuffer sb = new StringBuffer();
    	boolean hadAny = false;
    	for (SOCScenario sc : scMap.values())
    	{
    	    if (sc.optType == OTYPE_UNKNOWN)
    	        continue;  // <-- Skip this one --
    
    	    if (hadAny)
    		sb.append(SOCMessage.sep2_char);
    	    else
    		hadAny = true;
    	    sb.append(sc.scKey);
    	    sb.append('=');
    
    	    boolean wroteValueAlready = false;
    	    if (! wroteValueAlready)
    	        sc.packValue(sb);
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
     * @param scstr string of name-value pairs, as created by
     *             {@link #packOptionsToString(Hashtable, boolean)}.
     *             A leading comma is OK (possible artifact of StringTokenizer
     *             coming from over the network).
     *             If ostr=="-", the map will be null.
     * @return map of SOCGameOptions, or null if ostr==null or empty ("-")
     *         or if ostr is malformed.  Any unrecognized scenarios
     *         will be in the map as type {@link #OTYPE_UNKNOWN}.
     * @see #parseOptionNameValue(String, boolean)
     */
    public static Map<String,SOCScenario> parseOptionsToHash(String scstr)
    {
        if ((scstr == null) || scstr.equals("-"))
            return null;

        Map<String,SOCScenario> scMap = new HashMap<String,SOCScenario>();

        StringTokenizer st = new StringTokenizer(scstr, SOCMessage.sep2);
        String nvpair;
        while (st.hasMoreTokens())
        {
            nvpair = st.nextToken();  // skips any leading commas or doubled commas
            SOCScenario copySc = parseOptionNameValue(nvpair, false);
            if (copySc == null)
                return null;  // parse error
            scMap.put(copySc.scKey, copySc);
        }  // while (moreTokens)

        return scMap;
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
    public static SOCScenario parseOptionNameValue(final String nvpair, final boolean forceNameUpcase)
    {
        int i = nvpair.indexOf('=');  // don't just tokenize for this (efficiency, and param value may contain a "=")
        if (i < 1)
            return null;  // malformed

        String sckey = nvpair.substring(0, i);
        String scval = nvpair.substring(i+1);
        if (forceNameUpcase)
            sckey = sckey.toUpperCase();
        SOCScenario knownSc = allScenarios.get(sckey);
        SOCScenario copySc;
        if (knownSc == null)
        {
            copySc = new SOCScenario(sckey);  // OTYPE_UNKNOWN
        }
        else
        {
            if (scval.length() == 0)
            {
                return null;  // malformed: no value
            }
            try
            {
                copySc = (SOCScenario) knownSc.clone();
            } catch (CloneNotSupportedException ce)
            {
                return null;
            }

            switch (copySc.optType)  // OTYPE_* - update this switch, must match format produced
            {                         //           in packValue / packOptionsToString
            case OTYPE_BOOL:
                copySc.setBoolValue(scval.equals("t") || scval.equals("T"));
                break;

            default:
                copySc = new SOCScenario(sckey);  // OTYPE_UNKNOWN
            }
        }
        return copySc;
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
     * @throws ClassCastException if values contain a non-{@link SOCScenario}
     */
    public static int optionsMinimumVersion(Map<?, SOCScenario> opts)
	throws ClassCastException
    {
    	int minVers = -1;
    	for (SOCScenario sc : opts.values())
    	{
            int scMin = sc.minVersion;
    	    if (scMin > minVers)
    	        minVers = scMin;
    	}
    	return minVers;
    }

    /**
     * Compare a set of scenarios against the specified version.
     * Make a list of all which are new or changed since that version.
     *<P>
     * This method is called to sync client-server known-scenario info.
     *
     * @param vers  Version to compare scens against
     * @param scens  Set of {@link SOCScenario}s to check current values;
     *              if null, use the "known scenarios" set
     * @return Vector of the newer {@link SOCScenario}s, or null
     *     if all are known and unchanged since <tt>vers</tt>.
     */
    public static ArrayList<SOCScenario> scenariosNewerThanVersion
        (final int vers, Map<String, SOCScenario> scens)
    {
        if (scens == null)
            scens = allScenarios;
        ArrayList<SOCScenario> uSc = null;  // add problems to uopt
        
        for (SOCScenario sc : scens.values())
        {
            if (sc.lastModVersion > vers)
            {
                // modified since vers
                if (uSc == null)
                    uSc = new ArrayList<SOCScenario>();
                uSc.add(sc);
            }
        }

        return uSc;
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
    public static SOCScenario trimEnumForVersion(SOCScenario opt, final int vers)
    {
        final int ev = getMaxEnumValueForVersion(opt.scKey, vers);
        if ((ev == Integer.MAX_VALUE) || (ev == opt.enumVals.length))
            return opt;
        String[] evkeep = new String[ev];
        System.arraycopy(opt.enumVals, 0, evkeep, 0, ev);
        return new SOCScenario(opt, evkeep);  // Copy option and restrict enum values
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
     * @param knownScenarios Set of known SOCGameOptions to check against, or null to use
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
        (Map<String, SOCScenario> newOpts, Map<String, SOCScenario> knownScenarios,
         final boolean doServerPreadjust)
        throws IllegalArgumentException
    {
        if (knownScenarios == null)
            knownScenarios = allScenarios;

        StringBuffer scProblems = new StringBuffer();

        // use Iterator in loop, so we can remove from the hash if needed
        boolean allKnown = true;
	for (Iterator<Map.Entry<String, SOCScenario>> ikv = newOpts.entrySet().iterator();
	     ikv.hasNext(); )
	{
	    Map.Entry<String, SOCScenario> sckv = ikv.next();

	    SOCScenario sc = sckv.getValue();
	    SOCScenario knownSc = knownScenarios.get(sc.scKey);
	    if (knownSc == null)
	    {
                allKnown = false;
                scProblems.append(sc.scKey);
                scProblems.append(": unknown. ");
	    } else {
	        // Clip int values, check default values, check dropIfUnused

		if (knownSc.lastModVersion != sc.lastModVersion)
		{
		    allKnown = false;
		    scProblems.append(sc.scKey);
		    scProblems.append(": lastModVersion mismatch (");
		    scProblems.append(knownSc.lastModVersion);
		    scProblems.append(" != ");
                    scProblems.append(sc.lastModVersion);
                    scProblems.append("). ");
		}

		switch (sc.optType)  // OTYPE_*
		{
		case OTYPE_BOOL:
                    if (knownSc.dropIfUnused && ! sc.boolValue)
                        ikv.remove();
		    break;

                // no default: all types should be handled above.

		}  // endsw
	    }
	}

	if (allKnown)
	    return null;
	else
	    return scProblems;
    }

    /**
     * Within a set of options, include a boolean option and make it true.
     * If the option object doesn't exist in <tt>newOpts</tt>, it will be cloned from
     * the set of known options.
     * @param newOpts Options to set <tt>boKey</tt> within
     * @param boKey   Key name for boolean option to set
     * @throws NullPointerException  if <tt>boKey</tt> isn't in <tt>newOpts</tt>
     *   and doesn't exist in the set of known options
     * @since 1.1.17
     */
    public static void setBoolOption(Hashtable<String, SOCScenario> newOpts, final String boKey)
        throws NullPointerException
    {
        SOCScenario opt = newOpts.get(boKey);
        if (opt == null)
        {
            try
            {
                opt = (SOCScenario) (allScenarios.get(boKey).clone());
            }
            catch (CloneNotSupportedException e)
            {
                // required stub; is Cloneable, so won't be thrown
            }
            opt.boolValue = true;
            newOpts.put(boKey, opt);
        }
        else if (! opt.boolValue)
        {
            opt.boolValue = true;
        }
    }

    /**
     * Within a set of options, include an int or intbool option and set its value.
     * If the option object doesn't exist in <tt>newOpts</tt>, it will be cloned from
     * the set of known options.
     * @param newOpts Options to set <tt>ioKey</tt> within
     * @param ioKey   Key name for int option to set
     * @param ivalue  Set option to this int value
     * @param bvalue  Set option to this boolean value (ignored if option type not intbool)
     * @throws NullPointerException  if <tt>ioKey</tt> isn't in <tt>newOpts</tt>
     *   and doesn't exist in the set of known options
     * @since 1.1.17
     */
    public static void setIntOption
        (Hashtable<String, SOCScenario> newOpts, final String ioKey, final int ivalue, final boolean bvalue)
        throws NullPointerException
    {
        SOCScenario opt = newOpts.get(ioKey);
        if (opt == null)
        {
            try
            {
                opt = (SOCScenario) (allScenarios.get(ioKey).clone());
            }
            catch (CloneNotSupportedException e)
            {
                // required stub; is Cloneable, so won't be thrown
            }
            opt.intValue = ivalue;
            opt.boolValue = bvalue;
            newOpts.put(ioKey, opt);
        }
        else
        {
            opt.intValue = ivalue;
            opt.boolValue = bvalue;
        }
    }

    /**
     * Test whether a string's characters are all within the strict
     * ranges 0-9, A-Z. The first character must be A-Z. Scenario name keys
     * must start with a letter and contain only ASCII uppercase letters
     * ('A' through 'Z') underscores '_' and digits ('0' through '9'),
     * in order to normalize handling and network message formats.
     *
     * @param s string to test
     * @return true if all characters are OK, false otherwise
     */
    public static final boolean isAlphanumericUpcaseAscii(String s)
    {
        for (int i = s.length()-1; i>=0; --i)
        {
            final char c = s.charAt(i);
            if (((c < '0') || (c > '9'))
                && ((c < 'A') || (c > 'Z'))
                && (c != '_'))
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
        StringBuffer sb = new StringBuffer(scKey);
        sb.append('=');
        packValue(sb);
        return sb.toString();
    }

    /**
     * Compare two scenarios, for display purposes. ({@link Comparable} interface)
     * Two game scenarios are considered equal if they have the same {@link #scKey}.
     * Greater/lesser is determined by {@link #scDesc}.{@link String#compareTo(String) compareTo()}.
     * @param other A SOCScenario to compare, or another object;  if other isn't a
     *              scenario, the {@link #hashCode()}s are compared.
     */
    public int compareTo(Object other)
    {
        if (other instanceof SOCScenario)
        {
            SOCScenario osc = (SOCScenario) other;
            if (scKey.equals(osc.scKey))
                return 0;
            return scDesc.compareTo(osc.scDesc);
        } else {
            return hashCode() - other.hashCode();
        }
    }

}
