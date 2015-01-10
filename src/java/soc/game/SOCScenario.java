/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012-2015 Jeremy D Monin <jeremy@nand.net>
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import soc.message.SOCMessage;

/**
 * Scenarios for game rules and options on the {@link SOCBoardLarge large sea board}.
 * Chooseable at game creation.
 * This class holds the known scenarios, at the client or server,
 * in a static dictionary of known scenarios; see {@link #getAllKnownScenarios()}
 * for the current list of all known scenarios.
 *<P>
 * For information about adding or changing game scenarios in a
 * later version of JSettlers, please see {@link #initAllScenarios()}.
 *<P>
 * Scenarios use {@link SOCGameOption}s to change the game to the scenario's concept.
 * Each scenario's {@link #scOpts} field gives the scenario's option names and values.
 * The game also knows its scenario by setting {@link SOCGameOption} "SC" = {@link SOCVersionedItem#key key}.
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
 * method's description for more details on adding or changing a scenario.
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
public class SOCScenario
    extends SOCVersionedItem implements Cloneable, Comparable<Object>
{
    /**
     * Version 2.0.00 (2000) introduced game scenarios.
     * @since 2.0.00
     */
    public static final int VERSION_FOR_SCENARIOS = 2000;

    /**
     * Set of "known scenarios".
     * allScenarios must never be null, because other places assume it is filled.
     * All scenarios here have their {@link SOCVersionedItem#isKnown isKnown} flag set true.
     * To add or change a scenario, see {@link #initAllScenarios()}.
     */
    private static Map<String, SOCScenario> allScenarios = initAllScenarios();

    /**
     * Create a set of the known scenarios.
     * This method creates and returns a Map, but does not set the static {@link #allScenarios} field.
     * See {@link #getAllKnownScenarios()} for the current list of known scenarios.
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
     *<LI> Rarely, a scenario changes the pirate or robber behavior.  If the new scenario does this,
     *   see {@link SOCGame#canChooseMovePirate()} or {@link SOCGame#rollDice()}.
     *<LI> Not all scenarios require a game option.  {@link #K_SC_TTD SC_TTD} has only a board layout,
     *   and doesn't change any game behavior from standard, so there is no {@code "_SC_TTD"} SOCGameOption.
     *<LI> Add the scenario's key to the list of "game scenario keynames"
     *   as a public static final String, such as {@link #K_SC_FOG}.
     *   Put a short description in the javadoc there and in {@link #getAllKnownScenarios()} javadoc's scenario list.
     *<LI> Create the scenario by calling {@code allSc.put} here in initAllScenarios.
     *   Use the current version for the "last modified" field.
     *<LI> Create the board layout; see {@link soc.server.SOCBoardLargeAtServer} javadoc.
     *<LI> Within {@link SOCGame}, don't change any code based on the scenario name;
     *   game behavior changes are based only on the {@link SOCGameOption}s implementing the scenario.
     *</UL>
     *
     * <h3>If you want to change a scenario (in a later version):</h3>
     *
     *   Typical changes to a game scenario would be:
     *<UL>
     *<LI> Change the {@link SOCVersionedItem#desc description}
     *<LI> Change the {@link #scLongDesc long description}
     *<LI> Change the {@link #scOpts options}
     *</UL>
     *   Things you can't change about a scenario, because inconsistencies would occur:
     *<UL>
     *<LI> {@link SOCVersionedItem#key name key}
     *<LI> {@link SOCVersionedItem#minVersion minVersion}
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
    private static Map<String, SOCScenario> initAllScenarios()
    {
        Map<String, SOCScenario> allSc = new HashMap<String, SOCScenario>();

        // Game scenarios, and their SOCGameOptions (rules and events)

        allSc.put(K_SC_NSHO, new SOCScenario
            (K_SC_NSHO, 2000, 2000,
             "New Shores",
             null,
             "_SC_SEAC=t,PLL=t,VP=t13"));

        allSc.put(K_SC_4ISL, new SOCScenario
            (K_SC_4ISL, 2000, 2000,
             "The Four Islands",
             null,
             "_SC_SEAC=t,PLL=t,VP=t12"));

        allSc.put(K_SC_FOG, new SOCScenario
            (K_SC_FOG, 2000, 2000,
             "Fog Islands",
             "Some hexes are initially hidden by fog. When you build a ship or road to a foggy hex, that hex is revealed. "
             + "Unless it's water, you are given its resource as a reward.",
             "_SC_FOG=t,PLL=t,VP=t12"));

        allSc.put(K_SC_TTD, new SOCScenario
            (K_SC_TTD, 2000, 2000,
             "Through The Desert",
             null,
             "_SC_SEAC=t,PLL=t,VP=t12"));

        allSc.put(K_SC_CLVI, new SOCScenario
            (K_SC_CLVI, 2000, 2000,
             "Cloth Trade with neutral villages",
             "The small islands' villages give you Cloth; every 2 cloth you have is 1 extra Victory Point. To gain cloth, "
             + "build ships to a village. Each player to reach a village get of its 1 cloth when reached, and 1 more "
             + "whenever its number is rolled, until the village runs out. You can't move the pirate until you've "
             + "reached a village. If more than half the villages run out, the game ends, and the player "
             + "with the most VP wins. (If tied, player with most cloth wins.)",
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

        allSc.put(K_SC_WOND, new SOCScenario
                (K_SC_WOND, 2000, 2000,
                 "Wonders",
                 "Players must choose a unique Wonder and build all 4 of its levels. "
                 + "Each Wonder has its own requirements before you may start it, "
                 + "such as a harbor location or number of cities built. To win, you "
                 + "must complete your Wonder's 4 levels, or reach 10 VP and complete "
                 + "more levels than any other player.",
                 "_SC_WOND=t,PLL=t,VP=t10,_SC_SANY=t"));  // win condition: Complete Wonder, or 10 VP _and_ built the most levels

        return allSc;

        // OBSOLETE SCENARIOS, REMOVED SCENARIOS - Move its allSc.put down here, commented out,
        //       including the version, date, and reason of the removal.
    }

    // Game scenario keynames.

    /**
     * Scenario key <tt>SC_NSHO</tt> for New Shores.
     * Board layout is the standard 4- or 6-player board, or a smaller 3-player main island, plus small
     * outlying islands. No main option or special rules, only the board layout and 2 SVP for reaching each island.
     */
    public static final String K_SC_NSHO = "SC_NSHO";

    /**
     * Scenario key <tt>SC_4ISL</tt> for The Four Islands.
     * No main option or special rules, only a board layout and SVP.
     */
    public static final String K_SC_4ISL = "SC_4ISL";

    /**
     * Scenario key <tt>SC_FOG</tt> for {@link SOCScenarioGameEvent#SGE_FOG_HEX_REVEALED} (The Fog Islands scenario).
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
     * Trade ports at these far areas can be claimed by players and must be moved adjacent to one
     * of their coastal settlements/cities, unless they have none that isn't at least separated 1 edge
     * from an existing port.  If that's the case, the claimed port is "set aside" for the
     * player to place later when they have such a coastal settlement.
     *<P>
     * When a player reaches a Special Edge and is awarded a gift, the game clears that edge's special
     * type, then fires a {@link SOCScenarioPlayerEvent#DEV_CARD_REACHED_SPECIAL_EDGE} or
     * {@link SOCScenarioPlayerEvent#SVP_REACHED_SPECIAL_EDGE} event.
     *<P>
     * When a player reaches a "gift" trade port, either the port is added to their inventory
     * as a {@link SOCInventoryItem} for later placement, or they must immediately place it:
     * {@link SOCGame#setPlacingItem(SOCInventoryItem)} is called, state becomes {@link SOCGame#PLACING_INV_ITEM}.
     */
    public static final String K_SC_FTRI = "SC_FTRI";

    /**
     * Scenario key {@code SC_WOND} for Wonders.
     * Main option is {@link SOCGameOption#K_SC_WOND "_SC_WOND"}.
     * The pirate ship is not used in this scenario.
     *<P>
     * Players must choose a unique Wonder and build all 4 of its levels.
     * Each Wonder has its own requirements before they may start it,
     * such as a harbor location or number of cities built.
     *<P>
     * When a player starts to build a wonder, it's added to their Special Items for visibility; see below.
     *<P>
     * To win, the player must complete their Wonder's 4 levels, or reach 10 VP and
     * complete more levels than any other player.
     *<P>
     * Certain sets of nodes are special in this scenario's board layout.
     * Node sets are retrieved from {@link SOCBoardLarge#getAddedLayoutPart(String)} by key:
     *<UL>
     * <LI> {@code "N1"}: Desert Wasteland (for Great Wall wonder)
     * <LI> {@code "N2"}: Strait (for Great Bridge wonder)
     * <LI> {@code "N3"}: Adjacent to Strait ({@code N2}); initial placement not allowed here
     *     (this set is emptied after initial placement)
     *</UL>
     * This scenario also uses added layout part {@code "AL"} to specify that the nodes in {@code N1, N2,}
     * and {@code N3} become legal locations for settlements after initial placement.
     *<P>
     * The Wonders are stored as per-game Special Items: There are (1 + {@link SOCGame#maxPlayers}) wonders available,
     * held in game Special Item indexes 1 - <em>n</em>, with type key {@link SOCGameOption#K_SC_WOND},
     * initialized in {@link SOCGame#updateAtBoardLayout()}.  When a player starts to build a Wonder, a reference
     * to its {@link SOCSpecialItem} is placed into index 0 of their Special Items:
     * {@link SOCPlayer#setSpecialItem(String, int, SOCSpecialItem) pl.setSpecialItem("_SC_WOND", 0, item)}.
     *<P>
     * The player's request to build must use player item index (pi) 0, game item index (gi) 1 to <em>n</em>.
     */
    public static final String K_SC_WOND = "SC_WOND";

    /**
     * Scenario's {@link SOCGameOption}s, as a formatted string
     * from {@link SOCGameOption#packOptionsToString(Map, boolean)}.
     */
    public String scOpts;

    /**
     * Detailed text for the scenario description and special rules, or null.  Shown as a reminder at start of a game.
     * Must not contain network delimiter character {@link SOCMessage#sep_char}; {@link SOCMessage#sep2_char} is okay.
     * Must pass {@link SOCMessage#isSingleLineAndSafe(String, boolean) SOCMessage.isSingleLineAndSafe(String, true)}.
     * Don't include the description of any scenario game option, such as {@link SOCGameOption#K_SC_SANY};
     * those will be taken from {@link SOCVersionedItem#desc SOCGameOption.desc} and shown in the reminder message.
     */
    public final String scLongDesc;

    /**
     * Create a new unknown scenario ({@link SOCVersionedItem#isKnown isKnown} false).
     * Minimum version will be {@link Integer#MAX_VALUE}.
     * scDesc and scOpts will be an empty string.
     * @param key   Alphanumeric key name for this option;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     * @throws IllegalArgumentException if key length is > 8 or not alphanumeric
     */
    public SOCScenario(final String key)
        throws IllegalArgumentException
    {
        this(false, key, Integer.MAX_VALUE, 0, "", null, "");
    }

    /**
     * Create a new known game scenario.
     *
     * @param key     Alphanumeric key name for this scenario;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     * @param minVers Minimum client version supporting this scenario, or -1.
     *                Same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     *                If not -1, <tt>minVers</tt> must be at least 2000
     *                ({@link #VERSION_FOR_SCENARIOS}).  To get the minimum version of a set of
     *                scenarios, use {@link SOCVersionedItem#itemsMinimumVersion(Map)}.
     * @param lastModVers Last-modified version for this scenario, or version which added it
     * @param desc    Descriptive brief text, to appear in the scenarios dialog.
     *             Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param longDesc  Longer descriptive text, or null; see {@link #scLongDesc} for requirements.
     * @param opts Scenario's {@link SOCGameOption}s, as a formatted string
     *             from {@link SOCGameOption#packOptionsToString(Map, boolean)}.
     * @throws IllegalArgumentException if key length is > 8 or not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 2000 but not -1
     */
    public SOCScenario
        (final String key, final int minVers, final int lastModVers,
         final String desc, final String longDesc, final String opts)
        throws IllegalArgumentException
    {
	this(true, key, minVers, lastModVers, desc, longDesc, opts);
    }

    /**
     * Create a new game scenario - common constructor.
     * @param isKnown True if scenario is known here ({@link SOCVersionedItem#isKnown isKnown} true)
     * @param key     Alphanumeric uppercase code for this scenario;
     *                see {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)} for format.
     *                Keys can be up to 8 characters long.
     * @param minVers Minimum client version supporting this scenario, or -1.
     *                Same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     *                If not -1, <tt>minVers</tt> must be at least 2000
     *                ({@link #VERSION_FOR_SCENARIOS}).  To get the minimum version of a set of
     *                scenarios, use {@link SOCVersionedItem#itemsMinimumVersion(Map)}.
     * @param lastModVers Last-modified version for this scenario, or version which added it
     * @param desc Descriptive brief text, to appear in the scenarios dialog.
     *             Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param longDesc  Longer descriptive text, or null; see {@link #scLongDesc} for requirements.
     * @param opts Scenario's {@link SOCGameOption}s, as a formatted string
     *             from {@link SOCGameOption#packOptionsToString(Map, boolean)}.
     * @throws IllegalArgumentException
     *        or if key is not alphanumeric or length is > 8,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if opts is null,
     *        or if minVers or lastModVers is under 2000 but not -1
     */
    protected SOCScenario
        (final boolean isKnown, final String key, final int minVers, final int lastModVers,
         final String desc, final String longDesc, final String opts)
        throws IllegalArgumentException
    {
        super(key, minVers, lastModVers, isKnown, desc);
            // checks isAlphanumericUpcaseAscii(key), isSingleLineAndSafe(desc)

        // validate & set scenario properties:

        if (key.length() > 8)
            throw new IllegalArgumentException("Key length > 8: " + key);
        if ((minVers < VERSION_FOR_SCENARIOS) && (minVers != -1))
            throw new IllegalArgumentException("minVers " + minVers + " for key " + key);
        if ((lastModVers < VERSION_FOR_SCENARIOS) && (lastModVers != -1))
            throw new IllegalArgumentException("lastModVers " + lastModVers + " for key " + key);
        if (longDesc != null)
        {
            if (! SOCMessage.isSingleLineAndSafe(longDesc, true))
                throw new IllegalArgumentException("longDesc fails isSingleLineAndSafe");
            if (longDesc.contains(SOCMessage.sep))
                throw new IllegalArgumentException("longDesc contains " + SOCMessage.sep);
        }
        if (opts == null)
            throw new IllegalArgumentException("opts null");

        scOpts = opts;
	scLongDesc = longDesc;
    }

    /**
     * Get all known scenario objects, mapping from their key names (such as {@link #K_SC_4ISL SC_4ISL}).
     *
     * <H3>Current Known Scenarios:</H3>
     *<UL>
     *<LI> {@link #K_SC_NSHO SC_NSHO}  New Shores
     *<LI> {@link #K_SC_4ISL SC_4ISL}  The Four Islands (Six on the 6-player board)
     *<LI> {@link #K_SC_FOG  SC_FOG}   A hex has been revealed from behind fog:
     *                                  {@link SOCScenarioGameEvent#SGE_FOG_HEX_REVEALED}
     *<LI> {@link #K_SC_TTD  SC_TTD}   Through The Desert
     *<LI> {@link #K_SC_CLVI SC_CLVI}  Cloth trade with neutral {@link SOCVillage villages}
     *<LI> {@link #K_SC_PIRI SC_PIRI}  Pirate Islands and {@link SOCFortress fortresses}
     *<LI> {@link #K_SC_FTRI SC_FTRI}  The Forgotten Tribe
     *<LI> {@link #K_SC_WOND SC_WOND}  Wonders
     *</UL>
     *  (See each scenario name field's javadoc for more details.)
     *
     * @return a deep copy of all known scenario objects
     * @see #addKnownScenario(SOCScenario)
     */
    public static Map<String, SOCScenario> getAllKnownScenarios()
    {
        // To add a new scenario, see initAllScenarios().

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
	final String scKey = scNew.key;
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
    public static Map<String, SOCScenario> cloneScenarios(final Map<String, SOCScenario> scens)
    {
    	if (scens == null)
    	    return null;

    	Map<String, SOCScenario> scens2 = new HashMap<String, SOCScenario>();
    	for (Map.Entry<String, SOCScenario> e : scens.entrySet())
    	{
    	    final SOCScenario sc = e.getValue();

    	    try
    	    {
                scens2.put(sc.key, (SOCScenario) sc.clone());
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
     *         any scenarios with {@link SOCVersionedItem#isKnown isKnown} false will not be
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
    	    if (! (sc.isKnown && (sc.minVersion <= cliVers)))
    	        continue;

    	    if (hadAny)
    		sb.append(SOCMessage.sep2_char);
    	    else
    		hadAny = true;
            sb.append(sc.key);
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
     *         will be in the map with {@link SOCVersionedItem#isKnown isKnown} false.
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

            scMap.put(copySc.key, copySc);
        }  // while (moreTokens)

        return scMap;
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
	    SOCScenario knownSc = knownScenarios.get(sc.key);
	    if (knownSc == null)
	    {
                allKnown = false;
                scProblems.append(sc.key);
                scProblems.append(": unknown. ");
	    } else {
		if (knownSc.lastModVersion != sc.lastModVersion)
		{
		    allKnown = false;
		    scProblems.append(sc.key);
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
     * @return {@link SOCVersionedItem#key key}
     */
    public String toString()
    {
        return key;
    }

    /**
     * Compare two scenarios, for display purposes. ({@link Comparable} interface)
     * Two game scenarios are considered equal if they have the same {@link SOCVersionedItem#key key}.
     * Greater/lesser is determined by {@link SOCVersionedItem#desc desc}.{@link String#compareTo(String) compareTo()}.
     * @param other A SOCScenario to compare, or another object;  if other isn't a
     *              scenario, the {@link #hashCode()}s are compared.
     */
    public int compareTo(Object other)
    {
        if (other instanceof SOCScenario)
        {
            SOCScenario osc = (SOCScenario) other;
            if (key.equals(osc.key))
                return 0;
            return desc.compareTo(osc.desc);
        } else {
            return hashCode() - other.hashCode();
        }
    }

}
