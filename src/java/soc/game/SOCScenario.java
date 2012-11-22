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
 * Scenarios use {@link SOCGameOption}s to change the game to the scenario's concept.
 * Each scenario's {@link #scOpts} field gives the scenario's option names and values.
 * The game also knows its scenario by setting {@link SOCGameOption} "SC" = {@link #scKey}.
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
 * <B>Sea Board Scenario game options:</B><br>
 * Game scenarios were introduced with the large sea board in 2.0.00.
 * Game options are used to indicate which {@link SOCScenarioPlayerEvent scenario events}
 * and rules are possible in the current game.
 * These all start with <tt>"_SC_"</tt> and have a static key string;
 * an example is {@link SOCGameOption#K_SC_3IP} for scenario game option <tt>"_SC_3IP"</tt>.
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
     * This method creates and returns a Map, but does not set the static {@link #allScenarios} field.
     *
     * <h3>Current Game Scenarios:</h3>
     *<UL>
     *<LI> {@link #K_SC_FOG  _SC_FOG}   A hex has been revealed from behind fog:
     *                                  {@link SOCScenarioGameEvent#SGE_FOG_HEX_REVEALED}
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
     *   Typical changes to a game scenario would be:
     *<UL>
     *<LI> Change the {@link #scDesc description}
     *<LI> Change the {@link #scOpts options}
     *</UL>
     *   Things you can't change about a scenario, because inconsistencies would occur:
     *<UL>
     *<LI> {@link #scKey name key}
     *<LI> {@link #minVersion}
     *</UL>
     *
     *   <b>To make the change:</b>
     *<UL>
     *<LI> Change the option here in initAllScenarios; change the "last modified" field to
     *   the current game version. Otherwise the server can't tell the client what has
     *   changed about the scenario.
     *<LI> Search the entire source tree for its key name, to find places which may need an update.
     *<LI> Consider if any other places listed above (for add) need adjustment.
     *</UL>
     *
     * <h3>If you want to remove or obsolete a game scenario (in a later version):</h3>
     *
     * Please think twice beforehand; breaking compatibility with older clients shouldn't
     * be done without a very good reason.  That said, the server is authoritative on scenarios.
     * If a scenario isn't in its known list ({@link #initAllScenarios()}), the client won't be
     * allowed to ask for it.  Any obsolete scenario should be kept around as commented-out code.
     *
     * @return a fresh copy of the "known" scenarios, with their hardcoded default values
     */
    public static Map<String, SOCScenario> initAllScenarios()
    {
        Map<String, SOCScenario> allSc = new HashMap<String, SOCScenario>();

        // Game scenario options (rules and events)
        allSc.put(K_SC_FOG, new SOCScenario
            (K_SC_FOG, 2000, 2000, true,
             "Some land hexes initially hidden by fog",
             "_SC_FOG=t,PLL=t,VP=12"));
        allSc.put(K_SC_CLVI, new SOCScenario
            (K_SC_CLVI, 2000, 2000, true,
             "Cloth Trade with neutral villages",
             "_SC_CLVI=t,PLL=t,VP=14,_SC_3IP=t,_SC_0RVP=t"));

        // NEW_OPTION - Add opt.put here at end of list, and update the
        //       list of "current known options" in javadoc just above.

        return allSc;

        // OBSOLETE SCENARIOS, REMOVED SCENARIOS - Move its allSc.put down here, commented out,
        //       including the version, date, and reason of the removal.
    }

    /** Lowest OTYPE value known at this version */
    public static final int OTYPE_MIN = 0;

    /** Option type: unknown (probably due to version mismatch)  */
    public static final int OTYPE_UNKNOWN = 0;

    /** Option type: boolean  */
    public static final int OTYPE_BOOL = 1;

    // Game scenario keynames.

    /**
     * Scenario key <tt>SC_FOG</tt> for {@link SOCScenarioGameEvent#SGE_FOG_HEX_REVEALED}.
     * Main option is {@link SOCGameOption#K_SC_FOG}.
     */
    public static final String K_SC_FOG = "SC_FOG";

    /**
     * Scenario key <tt>SC_CLVI</tt> for {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}:
     * Cloth Trade with neutral {@link SOCVillage villages}.
     * Main option is {@link SOCGameOption#K_SC_CLVI}.
     */
    public static final String K_SC_CLVI = "SC_CLVI";

    public final int optType;    // TODO remove

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
     * changes would include different {@link #scOpts}, description, etc.
     * Same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     */
    public final int lastModVersion;

    /**
     * Default value for boolean part of this option, if any
     */
    public final boolean defaultBoolValue;

    /**
     * Scenario's {@link SOCGameOption}s, as a formatted string
     * from {@link SOCGameOption#packOptionsToString(Hashtable, boolean)}.
     */
    public String scOpts;

    /**
     * Descriptive text for the scenario. Must not contain the network delimiter
     * characters {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}.
     */
    public String scDesc;

    private boolean boolValue;

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
        this(OTYPE_UNKNOWN, key, 0, 0, false, "", "");
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
     * @param opts Scenario's {@link SOCGameOption}s, as a formatted string
     *             from {@link SOCGameOption#packOptionsToString(Hashtable, boolean)}.
     * @param desc    Descriptive brief text, to appear in the options dialog
     * @throws IllegalArgumentException if key length is > 3 or not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    public SOCScenario(String key, int minVers, int lastModVers,
        boolean defaultValue, String opts, String desc)
        throws IllegalArgumentException
    {
	this(OTYPE_BOOL, key, minVers, lastModVers, defaultValue, null, desc);
    }

    /**
     * Create a new game scenario - common constructor.
     * @param key     Alphanumeric uppercase code for this option;
     *                see {@link #isAlphanumericUpcaseAscii(String)} for format.
     *                Keys can be up to 8 characters long.
     * @param minVers Minimum client version for games where this option is set (its boolean field is true), or -1.
     *                If not -1, <tt>minVers</tt> must be at least 2000.
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultBoolValue Default value (true if set, false if not set)
     * @param opts Scenario's {@link SOCGameOption}s, as a formatted string
     *             from {@link SOCGameOption#packOptionsToString(Hashtable, boolean)}.
     * @param desc Descriptive brief text, to appear in the options dialog; should
     *             contain a placeholder character '#' where the int value goes.
     *             Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @throws IllegalArgumentException if defaultIntValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if opts is null,
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    protected SOCScenario(int otype, String key, int minVers, int lastModVers,
        boolean defaultBoolValue,
        String opts, String desc)
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
        if (opts == null)
            throw new IllegalArgumentException("opts null");

	scKey = key;
	optType = otype;
	minVersion = minVers;
	lastModVersion = lastModVers;
	this.defaultBoolValue = defaultBoolValue;
        scOpts = opts;
	scDesc = desc;

	// starting values (= defaults)
	boolValue = defaultBoolValue;
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
     * The minimum value is -1 unless {@link #getBoolValue()} is true (that is, unless the option is set).
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
     * @return true if it's new, false if we already had that key and it was updated
     * @see #getAllKnownOptions()
     */
    public static boolean addKnownOption(SOCScenario onew)
    {
	final String oKey = onew.scKey;
	final boolean hadIt = allScenarios.containsKey(oKey);
	if (hadIt)
	    allScenarios.remove(oKey);
	allScenarios.put(oKey, onew);
	return ! hadIt;
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
    public static SOCScenario getScenario(String key)
    {
        return allScenarios.get(key);  // null is ok
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
     * @return string of name-value pairs, or "-" for an empty or null ohash.
     *         Format: k1=t,k2=f,k3=t
     *
     * @throws ClassCastException if hashtable contains anything other
     *         than SOCGameOptions
     * @see #parseOptionNameValue(String, boolean)
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
    	    if (hadAny)
    		sb.append(SOCMessage.sep2_char);
    	    else
    		hadAny = true;
    	    sb.append(sc.scKey);
    	    sb.append('=');
    
    	    boolean wroteValueAlready = false;
    	    if (! wroteValueAlready)
    	        sb.append(sc.boolValue ? 't' : 'f');
    	}
    	return sb.toString();
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
     * Compare a set of options with known-good values.
     * If any are above/below maximum/minimum, clip to the max/min value in knownOpts.
     * If any are unknown, return a description. Will still check (and clip) the known ones.
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
	        // Clip int values, check default values

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
        sb.append(boolValue ? 't' : 'f');
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
