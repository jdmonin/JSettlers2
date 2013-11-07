/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012-2013 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Hashtable;  // for javadoc
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import soc.message.SOCMessage;

/**
 * Scenarios for game rules and options on the {@link SOCBoardLarge large sea board}.
 * Chooseable at game creation.
 * This class holds the known scenarios, at the client or server,
 * in a static dictionary of known scenarios;
 * see {@link #initAllScenarios()} for the current list.
 * To get the list of known scenarios, use {@link #getAllKnownScenarios()}.
 *<P>
 * For information about adding or changing game scenarios in a
 * later version of JSettlers, please see {@link #initAllScenarios()}.
 *<P>
 * Scenarios use {@link SOCGameOption}s to change the game to the scenario's concept.
 * Each scenario's {@link #scOpts} field gives the scenario's option names and values.
 * The game also knows its scenario by setting {@link SOCGameOption} "SC" = {@link #scKey}.
 *<P>
 * Scenario name keys must start with a letter and contain only ASCII uppercase
 * letters ('A' through 'Z'), underscore ('_'), and digits ('0' through '9'), in order to normalize
 * handling and network message formats.  This is enforced in constructors via
 * {@link SOCGameOption#isAlphanumericUpcaseAscii(String)}.
 *<P>
 * For the same reason, descriptions must not contain
 * certain characters or span more than 1 line; this is checked by calling
 * {@link SOCMessage#isSingleLineAndSafe(String)} within constructors and setters.
 *<P>
 * The "known scenarios" are initialized via {@link #initAllScenarios()}.  See that
 * method's description for more details on adding a scenario.
 *<P>
 * <B>Version negotiation:</B><br>
 * Game options were introduced in 1.1.07; check server, client versions against
 * {@link soc.message.SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
 * Scenarios were introduced in 2.0.00, and negotiate the same way.
 * Each scenario has version information, because scenarios can be added or changed
 * with new versions of JSettlers.  Since games run on the server, the server is
 * authoritative about game scenarios and options:  If the client is newer, it must defer to the
 * server's older set of known scenarios and options.  At client connect, the client compares its
 * JSettlers version number to the server's, and asks for any changes if
 * their versions differ.
 *<P>
 * @author Jeremy D. Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCScenario implements Cloneable, Comparable<Object>
{
    /**
     * Version 2.0.00 (2000) introduced game scenarios.
     * @since 2.0.00
     */
    public static final int VERSION_FOR_SCENARIOS = 2000;

    /**
     * Set of "known scenarios".
     * allScenarios must never be null, because other places assume it is filled.
     */
    private static Map<String, SOCScenario> allScenarios = initAllScenarios();

    /**
     * Create a set of the known scenarios.
     * This method creates and returns a Map, but does not set the static {@link #allScenarios} field.
     *
     * <h3>Current Game Scenarios:</h3>
     *<UL>
     *<LI> {@link #K_SC_4ISL SC_4ISL}  The Four Islands (Six on the 6-player board)
     *<LI> {@link #K_SC_FOG  SC_FOG}   A hex has been revealed from behind fog:
     *                                  {@link SOCScenarioGameEvent#SGE_FOG_HEX_REVEALED}
     *<LI> {@link #K_SC_TTD  SC_TTD}   Through The Desert
     *<LI> {@link #K_SC_CLVI SC_CLVI}  Cloth trade with neutral {@link SOCVillage villages}
     *<LI> {@link #K_SC_PIRI SC_PIRI}  Pirate Islands and {@link SOCFortress fortresses}
     *<LI> {@link #K_SC_FTRI SC_FTRI}  The Forgotten Tribe
     *</UL>
     *  (See each scenario name field's javadoc for more details.)
     *
     * <h3>If you want to add a game scenario:</h3>
     *<UL>
     *<LI> Choose an unused key name: for example, {@code "SC_FOG"} for Fog Islands.
     *   The list of already-used key names is here within initAllScenarios().
     *<LI> Decide if all client versions can use your scenario.  Typically, if the scenario
     *   requires server changes but not any client changes, all clients can use it.
     *   (For example, the scenario doesn't add any new game options.)
     *<LI> If your scenario requires new {@link SOCGameOption}s to change the rules or game behavior,
     *   create and test those; scenario game options all start with "_SC_".
     *   See {@link SOCGameOption#initAllOptions()} for details.
     *   If the new scenario has a new game option just for itself, instead of a reusable one like
     *   {@link SOCGameOption#K_SC_SANY _SC_SANY}, the option name is "_" + scenario name:
     *   {@code "_SC_PIRI"} for scenario {@link #K_SC_PIRI SC_PIRI}.
     *<LI> Not all scenarios require a game option.  {@link #K_SC_TTD SC_TTD} has only a board layout,
     *   and doesn't change any game behavior from standard, so there is no {@code "_SC_TTD"} SOCGameOption.
     *<LI> Add the scenario's key to the list of "game scenario keynames"
     *   as a public static final String, such as {@link #K_SC_FOG}.
     *   Put a short description in the javadoc there, and in this javadoc's scenario list.
     *<LI> Create the scenario by calling {@code allSc.put} here in initAllScenarios.
     *   Use the current version for the "last modified" field.
     *<LI> Within {@link SOCGame}, don't change any code based on the scenario name;
     *   game behavior changes are based only on the {@link SOCGameOption}s implementing the scenario.
     *</UL>
     *
     * <h3>If you want to change a scenario (in a later version):</h3>
     *
     *   Typical changes to a game scenario would be:
     *<UL>
     *<LI> Change the {@link #scDesc description}
     *<LI> Change the {@link #scLongDesc long description}
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
     *<LI> Change the scenario here in initAllScenarios; change the "last modified" field to
     *   the current game version. Otherwise the server can't tell the client what has
     *   changed about the scenario.
     *<LI> Search the entire source tree for its options' key names, to find places which may need an update.
     *<LI> Consider if any other places listed above (for add) need adjustment.
     *</UL>
     *
     * <h3>If you want to remove or obsolete a game scenario (in a later version):</h3>
     *
     * Please think twice beforehand; users may be surprised when something is missing, so this shouldn't
     * be done without a very good reason.  That said, the server is authoritative on scenarios.
     * If a scenario isn't in its known list ({@link #initAllScenarios()}), the client won't be
     * allowed to ask for it.  Any obsolete scenario should be kept around as commented-out code.
     * See {@link SOCGameOption#initAllOptions()} for things to think about when removing
     * game options used only in the obsolete scenario.
     *
     * @return a fresh copy of the "known" scenarios, with their hardcoded default values
     */
    public static Map<String, SOCScenario> initAllScenarios()
    {
        Map<String, SOCScenario> allSc = new HashMap<String, SOCScenario>();

        // Game scenarios, and their SOCGameOptions (rules and events)

        allSc.put(K_SC_4ISL, new SOCScenario
            (K_SC_4ISL, 2000, 2000,
             "The Four Islands",
             null,
             "_SC_SEAC=t,PLL=t,VP=t12"));

        allSc.put(K_SC_FOG, new SOCScenario
            (K_SC_FOG, 2000, 2000,
             "Some hexes initially hidden by fog",
             "When you build a ship or road to a foggy hex, that hex is revealed. Unless it's water, you are given its resource as a reward.",
             "_SC_FOG=t,PLL=t,VP=t12"));

        allSc.put(K_SC_TTD, new SOCScenario
            (K_SC_TTD, 2000, 2000,
             "Through The Desert",
             null,
             "_SC_SEAC=t,PLL=t,VP=t12"));

        allSc.put(K_SC_CLVI, new SOCScenario
            (K_SC_CLVI, 2000, 2000,
             "Cloth Trade with neutral villages",
             "The small villages give you Cloth; every 2 cloth you have is 1 extra Victory Point. To gain cloth, "
             + "build ships to a village. Each player to reach a village get 1 cloth when it's reached, and 1 more "
             + "whenever its number is rolled, until the village runs out. You can't move the pirate until you've "
             + "reached a village.",
             "_SC_CLVI=t,PLL=t,VP=t14,_SC_3IP=t,_SC_0RVP=t"));

        allSc.put(K_SC_PIRI, new SOCScenario
            (K_SC_PIRI, 2000, 2000,
             "Pirate Islands and Fortresses",
             "A pirate fleet circulates, stealing resources from weak players with adjacent settlements/cities until "
             + "the player upgrades their ships to warships.  To win, you must build ships directly to the Fortress "
             + "with your color, which the pirates have captured from you, and defeat it 3 times using warships.  "
             + "So, ship routes can't branch in different directions, only extend from their ends.  "
             + "No robber or largest army.",
             "_SC_PIRI=t,PLL=t,VP=t10"));  // win condition: 10 VP _and_ defeat a pirate fortress

        allSc.put(K_SC_FTRI, new SOCScenario
                (K_SC_FTRI, 2000, 2000,
                 "The Forgotten Tribe",
                 "Far areas of the board have small habitations of a \"forgotten tribe\" of settlers. "
                 + "When players reach them they are greeted with \"gifts\" of a development card or Special Victory Point. "
                 + "Harbors at these far areas can be claimed by players and must be moved adjacent to one "
                 + "of their coastal settlements/cities if possible, or set aside for the "
                 + "player to place later when they have one.",
                 "_SC_FTRI=t,PLL=t,VP=t13"));

        return allSc;

        // OBSOLETE SCENARIOS, REMOVED SCENARIOS - Move its allSc.put down here, commented out,
        //       including the version, date, and reason of the removal.
    }

    // Game scenario keynames.

    /**
     * Scenario key <tt>SC_4ISL</tt> for The Four Islands.
     * No main option or special rules, only a board layout and SVP.
     */
    public static final String K_SC_4ISL = "SC_4ISL";

    /**
     * Scenario key <tt>SC_FOG</tt> for {@link SOCScenarioGameEvent#SGE_FOG_HEX_REVEALED}.
     * Main option is {@link SOCGameOption#K_SC_FOG}.
     */
    public static final String K_SC_FOG = "SC_FOG";

    /**
     * Scenario key <tt>SC_TTD</tt> for Through The Desert.
     * No main option or special rules, only a board layout and SVP.
     */
    public static final String K_SC_TTD = "SC_TTD";

    /**
     * Scenario key <tt>SC_CLVI</tt> for {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}:
     * Cloth Trade with neutral {@link SOCVillage villages}.
     * Main option is {@link SOCGameOption#K_SC_CLVI}.
     */
    public static final String K_SC_CLVI = "SC_CLVI";

    /**
     * Scenario key <tt>SC_PIRI</tt> for Pirate Islands and {@link SOCFortress fortresses}.
     * Main option is {@link SOCGameOption#K_SC_PIRI}.
     *<P>
     * A pirate fleet circulates on a predefined path, stealing resources from weak players with
     * adjacent settlements/cities until the player upgrades their ships to warships.  To win,
     * the player must build ships directly to the Fortress with their color, and defeat it several
     * times using warships.  Also, ship routes can't branch in different directions in this scenario,
     * only extend from their ends.
     */
    public static final String K_SC_PIRI = "SC_PIRI";

    /**
     * Scenario key {@code SC_FTRI} for the Forgotten Tribe.
     * Main option is {@link SOCGameOption#K_SC_FTRI "_SC_FTRI"}.
     *<P>
     * Far areas of the board have small habitations of a "forgotten tribe" of settlers.
     * When players reach them (with a ship adjacent to various edge coordinates),
     * they are greeted with "gifts" of a development card or Special Victory Point.
     *<P>
     * Harbors at these far areas can be claimed by players and must be moved adjacent to one
     * of their coastal settlements/cities, unless they have none that isn't at least separated 1 edge
     * from an existing harbor.  If that's the case, the claimed harbor is "set aside" for the
     * player to place later when they have such a coastal settlement.
     *<P>
     * When a player reaches a Special Edge and is awarded a gift, the game clears that edge's special
     * type, then fires a {@link SOCScenarioPlayerEvent#DEV_CARD_REACHED_SPECIAL_EDGE} or
     * {@link SOCScenarioPlayerEvent#SVP_REACHED_SPECIAL_EDGE} event.
     */
    public static final String K_SC_FTRI = "SC_FTRI";

    /**
     * Is this an unknown scenario?  Used in cross-version compatibility.
     */
    public final boolean isUnknown;

    /**
     * Scenario key/technical name: Short alphanumeric name (max 8 characters, uppercase, starting with a letter).
     */
    public final String scKey;

    /**
     * Minimum game version supporting this scenario, or -1 for all;
     * same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     * To get the minimum version of a set of scenarios, use {@link #scenariosMinimumVersion(Map)}.
     * If this isn't -1, it's &gt;= 2000 (the version that introduced scenarios, {@link #VERSION_FOR_SCENARIOS}).
     * @see #lastModVersion
     */
    public final int minVersion;  // or -1

    /**
     * Most recent game version in which this scenario changed, or if not modified, the version which added it.
     * changes would include different {@link #scOpts}, description, etc.
     * Same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     * @see #minVersion
     */
    public final int lastModVersion;

    /**
     * Scenario's {@link SOCGameOption}s, as a formatted string
     * from {@link SOCGameOption#packOptionsToString(Hashtable, boolean)}.
     */
    public String scOpts;

    /**
     * Descriptive text for the scenario. Must not contain the network delimiter
     * characters {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}.
     * Must pass {@link SOCMessage#isSingleLineAndSafe(String)}.
     */
    public String scDesc;

    /**
     * Detailed text for the scenario description and special rules, or null.  Shown as a reminder at start of a game.
     * Must not contain network delimiter character {@link SOCMessage#sep_char}; {@link SOCMessage#sep2_char} is okay.
     * Must pass {@link SOCMessage#isSingleLineAndSafe(String, boolean) SOCMessage.isSingleLineAndSafe(String, true)}.
     * Don't include the description of any scenario game option, such as {@link SOCGameOption#K_SC_SANY};
     * those will be taken from {@link SOCGameOption#optDesc} and shown in the reminder message.
     */
    public final String scLongDesc;

    /**
     * Create a new unknown scenario ({@link #isUnknown}).
     * Minimum version will be {@link Integer#MAX_VALUE}.
     * scDesc and scOpts will be an empty string.
     * @param key   Alphanumeric key name for this option;
     *                see {@link SOCGameOption#isAlphanumericUpcaseAscii(String)} for format.
     * @throws IllegalArgumentException if key length is > 8 or not alphanumeric
     */
    public SOCScenario(String key)
        throws IllegalArgumentException
    {
        this(true, key, Integer.MAX_VALUE, 0, "", null, "");
    }

    /**
     * Create a new known game scenario.
     *
     * @param key     Alphanumeric key name for this scenario;
     *                see {@link SOCGameOption#isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version supporting this scenario, or -1.
     *                If not -1, <tt>minVers</tt> must be at least 2000
     *                ({@link #VERSION_FOR_SCENARIOS}).
     * @param lastModVers Last-modified version for this scenario, or version which added it
     * @param desc    Descriptive brief text, to appear in the scenarios dialog.
     *             Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param longDesc  Longer descriptive text, or null; see {@link #scLongDesc} for requirements.
     * @param opts Scenario's {@link SOCGameOption}s, as a formatted string
     *             from {@link SOCGameOption#packOptionsToString(Hashtable, boolean)}.
     * @throws IllegalArgumentException if key length is > 8 or not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 2000 but not -1
     */
    public SOCScenario
        (String key, int minVers, int lastModVers, final String desc, final String longDesc, final String opts)
        throws IllegalArgumentException
    {
	this(false, key, minVers, lastModVers, desc, longDesc, opts);
    }

    /**
     * Create a new game scenario - common constructor.
     * @param unknown True if scenario is unknown here ({@link #isUnknown})
     * @param key     Alphanumeric uppercase code for this scenario;
     *                see {@link SOCGameOption#isAlphanumericUpcaseAscii(String)} for format.
     *                Keys can be up to 8 characters long.
     * @param minVers Minimum client version supporting this scenario, or -1.
     *                If not -1, <tt>minVers</tt> must be at least 2000
     *                ({@link #VERSION_FOR_SCENARIOS}).
     * @param lastModVers Last-modified version for this scenario, or version which added it
     * @param desc Descriptive brief text, to appear in the scenarios dialog.
     *             Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param longDesc  Longer descriptive text, or null; see {@link #scLongDesc} for requirements.
     * @param opts Scenario's {@link SOCGameOption}s, as a formatted string
     *             from {@link SOCGameOption#packOptionsToString(Hashtable, boolean)}.
     * @throws IllegalArgumentException
     *        or if key is not alphanumeric or length is > 8,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if opts is null,
     *        or if minVers or lastModVers is under 2000 but not -1
     */
    protected SOCScenario
        (final boolean unknown, final String key, final int minVers, final int lastModVers,
         final String desc, final String longDesc, final String opts)
        throws IllegalArgumentException
    {
	// validate & set scenario properties:

        if (key.length() > 8)
            throw new IllegalArgumentException("Key length > 8: " + key);
        if (! (SOCGameOption.isAlphanumericUpcaseAscii(key) || key.equals("-")))  // "-" is for server/network use
            throw new IllegalArgumentException("Key not alphanumeric: " + key);
        if ((minVers < VERSION_FOR_SCENARIOS) && (minVers != -1))
            throw new IllegalArgumentException("minVers " + minVers + " for key " + key);
        if ((lastModVers < VERSION_FOR_SCENARIOS) && (lastModVers != -1))
            throw new IllegalArgumentException("lastModVers " + lastModVers + " for key " + key);
        if (! SOCMessage.isSingleLineAndSafe(desc))
            throw new IllegalArgumentException("desc fails isSingleLineAndSafe");
        if (longDesc != null)
        {
            if (! SOCMessage.isSingleLineAndSafe(longDesc, true))
                throw new IllegalArgumentException("longDesc fails isSingleLineAndSafe");
            if (longDesc.contains(SOCMessage.sep))
                throw new IllegalArgumentException("longDesc contains " + SOCMessage.sep);
        }
        if (opts == null)
            throw new IllegalArgumentException("opts null");

	scKey = key;
        isUnknown = unknown;
	minVersion = minVers;
	lastModVersion = lastModVers;
        scOpts = opts;
	scDesc = desc;
	scLongDesc = longDesc;
    }

    /**
     * Get all known scenario objects, mapping from their key names (such as {@link #K_SC_4ISL SC_4ISL}).
     * @return a deep copy of all known scenario objects
     * @see #addKnownScenario(SOCScenario)
     */
    public static Map<String, SOCScenario> getAllKnownScenarios()
    {
        return cloneScenarios(allScenarios);
    }

    /**
     * Add a new known scenario (presumably received from a server of newer or older version),
     * or update the scenario's information.
     * @param scNew New scenario, or a changed version of one we already know.
     * @return true if it's new, false if we already had that key and it was updated
     * @see #getAllKnownScenarios()
     */
    public static boolean addKnownScenario(SOCScenario scNew)
    {
	final String scKey = scNew.scKey;
	final boolean hadIt = allScenarios.containsKey(scKey);
	if (hadIt)
	    allScenarios.remove(scKey);
	allScenarios.put(scKey, scNew);
	return ! hadIt;
    }

    /**
     * Clone this scenario map and its contents.
     * @param scens  a map of {@link SOCScenario}s, or null
     * @return a deep copy of all scenario objects within scens, or null if scens is null
     */
    public static Map<String, SOCScenario> cloneScenarios(Map<String, SOCScenario> scens)
    {
    	if (scens == null)
    	    return null;
    
    	Map<String, SOCScenario> scens2 = new HashMap<String, SOCScenario>();
    	for (Map.Entry<String, SOCScenario> e : scens.entrySet())
    	{
    	    final SOCScenario sc = e.getValue();

    	    try
    	    {
    	        scens2.put(sc.scKey, (SOCScenario) sc.clone());
    	    } catch (CloneNotSupportedException ce) {
    	        // required, but not expected to happen
    	    }
    	}
    	return scens2;
    }

    /**
     * Get the scenario information about this known scenario.
     * @param key  Scenario key name, such as {@link #K_SC_4ISL SC_4ISL}, from {@link #getAllKnownScenarios()}
     * @return information about a known scenario, or null if none with that key
     */
    public static SOCScenario getScenario(String key)
    {
        return allScenarios.get(key);  // null is ok
    }

    /**
     * Utility - build a string of known scenario key names from the
     *           {@link #getAllKnownScenarios() known scenarios}.
     *
     * @return string of key names, same format as {@link #packScenariosToString(Map)};
     *         any scenarios with {@link #isUnknown} will not be
     *         part of the string.
     * @see #parseScenariosToMap(String)
     */
    public static String packKnownScenariosToString()
    {
        return packScenariosToString(allScenarios);
    }

    /**
     * Utility - build a string of known scenario names.
     * This can be unpacked with {@link #parseScenariosToMap(String)}.
     *
     * @param scMap Map of scenarios, or null
     * @return string of scenario key names, or "-" for an empty or null scMap.
     *         Format: k1,k2,k3
     */
    public static String packScenariosToString
        (Map<String, SOCScenario> scMap)
    {
        return packScenariosToString(scMap, -2);
    }

    /**
     * Utility - build a string of known scenario names,
     * adjusting for old clients if necessary.
     * This can be unpacked with {@link #parseScenariosToMap(String)}.
     * See {@link #packScenariosToString(Map)} javadoc for details.
     * 
     * @param scMap Map of SOCScenarios, or null
     * @param cliVers  Client version; assumed >= 2000 ({@link #VERSION_FOR_SCENARIOS}).
     *            If any game's scenarios can't be sent to an older client, skip them.
     * @return string of scenario key names, or "-" for an empty or null scMap;
     *         see {@link #packScenariosToString(Map)} javadoc for details.
     */
    public static String packScenariosToString
        (Map<String, SOCScenario> scMap, final int cliVers)
    {
    	if ((scMap == null) || scMap.size() == 0)
    	    return "-";
    
    	// Pack all non-unknown scenarios:
    	StringBuffer sb = new StringBuffer();
    	boolean hadAny = false;
    	for (SOCScenario sc : scMap.values())
    	{
    	    if (sc.isUnknown || (sc.minVersion > cliVers))
    	        continue;

    	    if (hadAny)
    		sb.append(SOCMessage.sep2_char);
    	    else
    		hadAny = true;
    	    sb.append(sc.scKey);
    	}
    	return sb.toString();
    }

    /**
     * Utility - build a map by parsing a list of scenario names.
     *
     * @param scstr string of key names, as created by
     *             {@link #packScenariosToString(Map)}.
     *             A leading comma is OK (possible artifact of StringTokenizer
     *             coming from over the network).
     *             If scstr=="-", the map will be null.
     * @return map of SOCScenarios, or null if scstr==null or empty ("-")
     *         or if scstr is malformed.  Any unrecognized scenarios
     *         will be in the map with {@link #isUnknown} set.
     */
    public static Map<String,SOCScenario> parseScenariosToMap(String scstr)
    {
        if ((scstr == null) || scstr.equals("-"))
            return null;

        Map<String,SOCScenario> scMap = new HashMap<String,SOCScenario>();

        StringTokenizer st = new StringTokenizer(scstr, SOCMessage.sep2);
        String nvpair;
        while (st.hasMoreTokens())
        {
            nvpair = st.nextToken();  // skips any leading commas or doubled commas
            SOCScenario copySc;
            if (allScenarios.containsKey(nvpair))
            {
                try
                {
                    copySc = (SOCScenario) (allScenarios.get(nvpair).clone());
                }
                catch (CloneNotSupportedException e)
                {
                    // required but not expected
                    copySc = new SOCScenario(nvpair);  // isUnknown
                }
            } else {
                copySc = new SOCScenario(nvpair);  // isUnknown
            }

            scMap.put(copySc.scKey, copySc);
        }  // while (moreTokens)

        return scMap;
    }

    /**
     * Examine this set of scenarios, finding the minimum required version to support
     * all these scenarios.
     *<P>
     * This calculation is done at the server when creating a new game.  Although the client's
     * version and scenarios (and thus its copy of scenariosMinimumVersion) may be newer or older,
     * and would give a different result if called, the server is authoritative for scenarios and game options.
     * Calls at the client to scenariosMinimumVersion should keep this in mind, especially if
     * a client's scenario's {@link #lastModVersion} is newer than the server.
     *
     * @param scens  a set of SOCScenarios; not null
     * @return the highest 'minimum version' among these scenarios, or -1
     */
    public static int scenariosMinimumVersion(Map<?, SOCScenario> scens)
    {
    	int minVers = -1;
    	for (SOCScenario sc : scens.values())
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
     * @return List of the newer {@link SOCScenario}s, or null
     *     if all are known and unchanged since <tt>vers</tt>.
     */
    public static ArrayList<SOCScenario> scenariosNewerThanVersion
        (final int vers, Map<String, SOCScenario> scens)
    {
        if (scens == null)
            scens = allScenarios;
        ArrayList<SOCScenario> uSc = null;  // add problems to uSc
        
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
     * Compare a set of scenarios with known-good values.
     * If any are unknown, return a description. Will still check the known ones.
     *
     * @param newScens Set of SOCScenarios to check against knownScenarios;
     *            Must not be null.
     * @param knownScenarios Set of known SOCScenarios to check against, or null to use
     *            the server's static copy
     * @return <tt>null</tt> if all are known; or, a human-readable problem description if:
     *            <UL>
     *            <LI> any of <tt>newScens</tt> are unknown
     *            <LI> or a scenario's type differs from that in knownScenarios
     *            <LI> or a {@link #lastModVersion} differs from in knownScenarios
     *            </UL>
     */
    public static StringBuffer adjustScenariosToKnown
        (Map<String, SOCScenario> newScens, Map<String, SOCScenario> knownScenarios,
         final boolean doServerPreadjust)
    {
        if (knownScenarios == null)
            knownScenarios = allScenarios;

        StringBuffer scProblems = new StringBuffer();

        // use Iterator in loop, so we can remove from the map if needed
        boolean allKnown = true;
	for (Iterator<Map.Entry<String, SOCScenario>> ikv = newScens.entrySet().iterator();
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
     * If a set of scenarios doesn't already include this known scenario, clone and add it.
     * @param newScens Set to set <tt>scKey</tt> within
     * @param scKey   Key name for scenario to include
     * @throws NullPointerException  if <tt>scKey</tt> isn't in <tt>newScens</tt>
     *   and doesn't exist in the set of known scenarios
     */
    public static void addScenario(Map<String, SOCScenario> newScens, final String scKey)
        throws NullPointerException
    {
        if (newScens.containsKey(scKey))
            return;

        try
        {
            SOCScenario sc = (SOCScenario) (allScenarios.get(scKey).clone());
            newScens.put(scKey, sc);
        }
        catch (CloneNotSupportedException e)
        {
            // required stub; is Cloneable, so won't be thrown
        }
    }

    /**
     * Get this scenario's key name.
     * @return {@link #scKey}
     */
    public String toString()
    {
        return scKey;
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
