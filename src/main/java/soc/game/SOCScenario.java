/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012-2020 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Set;
import java.util.StringTokenizer;

import soc.message.SOCMessage;

/**
 * Scenarios for game rules and options on the {@link SOCBoardLarge large sea board}.
 * Optional and chooseable at game creation.
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
 * Some scenarios restrict initial placement (see "land areas" in {@link SOCBoardLarge} class javadoc)
 * or have special winning conditions (see {@link SOCGame#checkForWinner()}).
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
 * <B>I18N:</B><br>
 * Game scenario names and descriptions are also stored as {@code gamescen.*.n}, {@code .d}
 * in {@code server/strings/toClient_*.properties} to be sent to clients if needed.
 * At the client, scenario's text can be localized with {@link #setDesc(String, String)}.
 * See unit test {@link soctest.TestI18NGameoptScenStrings}.
 *<P>
 * @author Jeremy D. Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCScenario
    extends SOCVersionedItem implements Cloneable, Comparable<Object>
{
    /** Version 2.0.00 (2000) introduced game scenarios. */
    public static final int VERSION_FOR_SCENARIOS = 2000;

    /**
     * Set of "known scenarios".
     * allScenarios must never be null, because other places assume it is filled.
     * All scenarios here have their {@link SOCVersionedItem#isKnown isKnown} flag set true.
     * To add or change a scenario, see {@link #initAllScenarios()}.
     */
    private static final Map<String, SOCScenario> allScenarios = initAllScenarios();

    /**
     * The highest {@link SOCVersionedItem#minVersion} of all scenarios in {@link #getAllKnownScenarios()}, or 0.
     * Value may change when a new JSettlers server version is released.
     *<P>
     * This is meant for use at the server, so its value won't be changed by calls to
     * {@link #addKnownScenario(SOCScenario)} or {@link #removeUnknownScenario(String)};
     * those methods aren't used at the server.
     */
    public static final int ALL_KNOWN_SCENARIOS_MIN_VERSION = findAllScenariosGreatestMinVersion();

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
     *   If the new scenario specifies any {@link #scOpts}, be sure the scenario's declared
     *   minimum version is >= those options' minimum versions; this won't be validated at runtime.
     *<LI> If your scenario requires new {@link SOCGameOption}s to change the rules or game behavior,
     *   create and test those; scenario game options all start with "_SC_".
     *   See {@link SOCGameOptionSet#getAllKnownOptions()} for details.
     *   If the new scenario has a new game option just for itself, instead of a reusable one like
     *   {@link SOCGameOptionSet#K_SC_SANY _SC_SANY}, the option name is "_" + scenario name:
     *   {@code "_SC_PIRI"} for scenario {@link #K_SC_PIRI SC_PIRI}.
     *<LI> If your scenario has special winning conditions, see {@link SOCGame#checkForWinner()}.
     *<LI> Rarely, a scenario changes the pirate or robber behavior.  If the new scenario does this,
     *   see {@link SOCGame#canChooseMovePirate()} or {@link SOCGame#rollDice()}.
     *   Currently no scenarios have a pirate but no robber, besides special case {@link #K_SC_PIRI SC_PIRI}
     *   where pirate fleet movement isn't controlled by the player. If adding a scenario with pirate but no robber,
     *   the client and robot would need changes to handle that; probably a {@link SOCGameOption} should be created
     *   for it and used in the scenario opts, client, and robot code.
     *<LI> Not all scenarios require a game option.  {@link #K_SC_TTD SC_TTD} has only a board layout,
     *   and doesn't change any game behavior from standard, so there is no {@code "_SC_TTD"} SOCGameOption.
     *<LI> Add the scenario's key to the list of "game scenario keynames"
     *   as a public static final String, such as {@link #K_SC_FOG}.
     *   Put a short description in the javadoc there and in {@link #getAllKnownScenarios()} javadoc's scenario list.
     *<LI> Create the scenario by calling {@code allSc.put} here in {@code initAllScenarios()}.
     *   Use the current version for the "last modified" field.
     *<LI> Create the board layout; see {@link soc.server.SOCBoardAtServer} javadoc.
     *<LI> Within {@link SOCGame}, don't change any code based on the scenario name;
     *   game behavior changes are based only on the {@link SOCGameOption}s implementing the scenario.
     *</UL>
     *
     * <h3>If you want to change a scenario (in a later version):</h3>
     *
     *   Typical changes to a game scenario would be:
     *<UL>
     *<LI> Change the {@link SOCVersionedItem#getDesc() description}
     *<LI> Change the {@link #getLongDesc() long description}
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
     * See {@link SOCGameOptionSet#getAllKnownOptions()} for things to think about when removing
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
             "_SC_SEAC=t,SBL=t,VP=t13"));

        allSc.put(K_SC_4ISL, new SOCScenario
            (K_SC_4ISL, 2000, 2000,
             "The Four Islands",
             "Start on one or two islands. Explore and gain SVP by building to others.",
             "_SC_SEAC=t,SBL=t,VP=t12"));

        allSc.put(K_SC_FOG, new SOCScenario
            (K_SC_FOG, 2000, 2000,
             "Fog Islands",
             "Some hexes are initially hidden by fog. When you build a ship or road to a foggy hex, that hex is revealed. "
             + "Unless it's water, you are given its resource as a reward.",
             "_SC_FOG=t,SBL=t,VP=t12"));

        allSc.put(K_SC_TTD, new SOCScenario
            (K_SC_TTD, 2000, 2000,
             "Through The Desert",
             "Start on the main island. Explore and gain SVP by building to the small islands, or through the desert to the coast.",
             "_SC_SEAC=t,SBL=t,VP=t12"));

        allSc.put(K_SC_CLVI, new SOCScenario
            (K_SC_CLVI, 2000, 2000,
             "Cloth Trade with neutral villages",
             "The small islands' villages give you Cloth; every 2 cloth you have is 1 extra Victory Point. To gain cloth, "
             + "build ships to a village. You can't move the pirate until you've reached a village. "
             + "Each player to reach a village gets 1 of its cloth at that time, and 1 more "
             + "whenever its number is rolled, until the village runs out. Pirate can steal cloth or resources. "
             + "If fewer than 4 villages still have cloth, the game ends and the player "
             + "with the most VP wins. (If tied, player with most cloth wins.)",
             "_SC_CLVI=t,SBL=t,VP=t14,_SC_3IP=t,_SC_0RVP=t"));

        allSc.put(K_SC_PIRI, new SOCScenario
            (K_SC_PIRI, 2000, 2000,
             "Pirate Islands and Fortresses",
             "A pirate fleet patrols, attacking to steal resources from weak players with adjacent settlements/cities until "
             + "the player builds a strong fleet of Warships. Build ships directly to the "
             + "Fortress of your color, which the pirates have captured from you. To win the game, you must reach the "
             + "victory point goal and defeat the Fortress 3 times using warships. "
             + "Ship routes can't branch out, only follow dotted lines to the Fortress. "
             + "Strengthen your fleet by playing Warship development cards to upgrade your ships. "
             + "When the pirate fleet attacks, you win if you have more Warships than the pirate fleet strength (randomly 1-6). "
             + "No robber or largest army. When 7 is rolled, any pirate fleet attack happens before the usual discards.",
             "_SC_PIRI=t,SBL=t,VP=t10,_SC_0RVP=t"));  // win condition: 10 VP _and_ defeat a pirate fortress

        allSc.put(K_SC_FTRI, new SOCScenario
            (K_SC_FTRI, 2000, 2000,
             "The Forgotten Tribe",
             "Far areas of the board have small habitations of a \"forgotten tribe\" of settlers. "
             + "When players build ships to reach them, they are greeted with \"gifts\" of a development card, "
             + "Special Victory Point, or a Port given to the player which must be moved adjacent to one "
             + "of their coastal settlements/cities if possible, or set aside to place later.",
             "_SC_FTRI=t,SBL=t,VP=t13"));

        allSc.put(K_SC_WOND, new SOCScenario
            (K_SC_WOND, 2000, 2000,
             "Wonders",
             "Each player chooses a unique Wonder and can build all 4 of its levels. "
             + "Each Wonder has its own requirements before you may start it, such as "
             + "several cities built or a port at a certain location. To win you "
             + "must complete your Wonder's 4 levels, or reach 10 VP and complete "
             + "more levels than any other player. Has no pirate ship.",
             "_SC_WOND=t,SBL=t,VP=t10,_SC_SANY=t"));  // win condition: Complete Wonder, or 10 VP _and_ built the most levels
                // The "all 4 levels" win condition is also stored in SOCSpecialItem.SC_WOND_WIN_LEVEL.

        // Uncomment to test scenario sync/negotiation between server and client versions.
        // Update the version numbers to current and current + 1.
        // Assumptions for testing:
        //   - Client and server are both current version (if current is v2.0.00, use 2000 here)
        //   - For testing, client or server version has been temporarily set to current + 1 (2001)
        // i18n/localization test reminder: resources/strings/server/toClient_*.properties:
        //   gamescen.SC_TSTNC.n = test-localizedname SC_TSTNC ...
        /*
        allSc.put("SC_TSTNC", new SOCScenario
            ("SC_TSTNC", 2000, 2001,
            "New: v+1 back-compat", null, "PLB=t,VP=t11,NT=y"));
        allSc.put("SC_TSTNO", new SOCScenario
            ("SC_TSTNO", 2001, 2001,
            "New: v+1 only", null, "PLB=t,VP=t15"));
         */

        return allSc;

        // OBSOLETE SCENARIOS, REMOVED SCENARIOS - Move its allSc.put down here, commented out,
        //       including the version, date, and reason of the removal.
    }

    /** Find max(minVersion) among {@link #allScenarios} for {@link #ALL_KNOWN_SCENARIOS_MIN_VERSION} init. */
    private static final int findAllScenariosGreatestMinVersion()
    {
        int min = 0;

        for (SOCScenario sc : allScenarios.values())
            if (sc.minVersion > min)
                min = sc.minVersion;

        return min;
    }

    // Game scenario keynames.

    /**
     * Scenario key {@code SC_NSHO} for New Shores.
     * Board layout is based on the classic 4- or 6-player board, or a smaller 3-player main island, plus small
     * outlying islands. No main option or special rules, only the board layout and 2 SVP for reaching each island.
     */
    public static final String K_SC_NSHO = "SC_NSHO";

    /**
     * Scenario key {@code SC_4ISL} for The Four Islands.
     * No main option or special rules, only a board layout and SVP.
     */
    public static final String K_SC_4ISL = "SC_4ISL";

    /**
     * Scenario key {@code SC_FOG} for Fog Islands.
     * When a hex has been revealed from behind fog,
     * {@link SOCGameEvent#SGE_FOG_HEX_REVEALED} is fired.
     * Main option is {@link SOCGameOptionSet#K_SC_FOG}.
     */
    public static final String K_SC_FOG = "SC_FOG";

    /**
     * Scenario key {@code SC_TTD} for Through The Desert.
     * No main option or special rules, only a board layout and SVP.
     */
    public static final String K_SC_TTD = "SC_TTD";

    /**
     * Scenario key {@code SC_CLVI} for {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}:
     * Cloth Trade with neutral {@link SOCVillage villages}.
     * Main option is {@link SOCGameOptionSet#K_SC_CLVI}.
     *<P>
     * Game ends immediately if fewer than 4 villages still have cloth ({@link #SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN}):
     * Winner is player with most VP, or most cloth if tied.
     *<P>
     * While starting a new game, the neutral villages are placed and sent to clients as part {@code "CV"}
     * of the board layout message while game state is still &lt; {@link SOCGame#START1A START1A}.
     */
    public static final String K_SC_CLVI = "SC_CLVI";

    /**
     * In scenario {@link #K_SC_CLVI SC_CLVI}, game ends immediately if
     * fewer than this many {@link SOCVillage villages} (4) still have cloth.
     * Per scenario rules, 4- and 6-player games use the same number here;
     * the 6-player layout has more villages and more players to reach them.
     */
    public static final int SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN = 4;
        // If this value changes, must update scenario description text
        // and its translations (keys: gamescen.SC_CLVI.d, event.sc_clvi.game.ending.villages)

    /**
     * Scenario key {@code SC_PIRI} for Pirate Islands and {@link SOCFortress Fortresses}.
     * Main option is {@link SOCGameOptionSet#K_SC_PIRI}.
     *<P>
     * A pirate fleet circulates on a predefined path, stealing resources from weak players with
     * adjacent settlements/cities until the player upgrades their ships to warships.  To win,
     * the player must build ships directly to the Fortress with their color, and defeat it several
     * times using warships.  Also, ship routes can't branch in different directions in this scenario,
     * only extend from their ends.
     *<P>
     * Each player starts with an initial coastal settlement and ship. While starting a new game these
     * are placed and sent to clients while game state is still &lt; {@link SOCGame#START1A START1A}.
     *<P>
     * The pirate fleet moves with every dice roll, and battles whenever 1 player's settlement/city is
     * adjacent. See {@link SOCGame.RollResult#sc_piri_fleetAttackVictim} javadoc and fields linked there.
     * When a 7 is rolled, the fleet moves and any battle is resolved before the usual discards/robbery.
     * Players may choose to not rob from anyone on 7.
     *<P>
     * When a player defeats their Fortress, it's replaced by a {@link SOCSettlement}.
     */
    public static final String K_SC_PIRI = "SC_PIRI";

    /**
     * Scenario key {@code SC_FTRI} for the Forgotten Tribe.
     * Main option is {@link SOCGameOptionSet#K_SC_FTRI "_SC_FTRI"}.
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
     * type, then fires a {@link SOCPlayerEvent#DEV_CARD_REACHED_SPECIAL_EDGE} or
     * {@link SOCPlayerEvent#SVP_REACHED_SPECIAL_EDGE} event.
     *<P>
     * When a player reaches a "gift" trade port, either the port is added to their inventory
     * as a {@link SOCInventoryItem} for later placement, or they must immediately place it:
     * {@link SOCGame#setPlacingItem(SOCInventoryItem)} is called, state becomes {@link SOCGame#PLACING_INV_ITEM}.
     */
    public static final String K_SC_FTRI = "SC_FTRI";

    /**
     * Scenario key {@code SC_WOND} for Wonders.
     * Main option is {@link SOCGameOptionSet#K_SC_WOND "_SC_WOND"}.
     * The pirate ship is not used in this scenario.
     *<P>
     * Players choose a unique Wonder and can build all 4 of its levels.
     * Each Wonder has its own requirements before they may start it,
     * such as several cities built or a port at a certain location.
     * Player must also use an unplaced {@link SOCShip} to start building a Wonder.
     *<P>
     * When a player starts to build a Wonder, it's added to their Special Items for visibility; see below.
     *<P>
     * To win the player must complete their Wonder's 4 levels, or reach 10 VP and
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
     * held in game Special Item indexes 1 - <em>n</em>, with type key {@link SOCGameOptionSet#K_SC_WOND},
     * initialized in {@link SOCGame#updateAtBoardLayout()}.  When a player starts to build a Wonder, a reference
     * to its {@link SOCSpecialItem} is placed into index 0 of their Special Items:
     * {@link SOCPlayer#setSpecialItem(String, int, SOCSpecialItem) pl.setSpecialItem("_SC_WOND", 0, item)}.
     * Server will subtract 1 from player's available Ship count.
     *<P>
     * The player's request to build must use player item index (pi) 0, game item index (gi) 1 to <em>n</em>.
     * Completing all 4 levels of a Wonder ({@link SOCSpecialItem#SC_WOND_WIN_LEVEL}) wins the game.
     */
    public static final String K_SC_WOND = "SC_WOND";

    /**
     * Scenario's {@link SOCGameOption}s, as a formatted string
     * from {@link SOCGameOption#packOptionsToString(Map, boolean, boolean)}.
     * Never {@code null} or empty; {@code "-"} if scenario has no game options.
     */
    public final String scOpts;

    /**
     * Detailed text for the scenario description and special rules, or null.
     * See {@link #getLongDesc()} for more info and requirements.
     */
    private String scLongDesc;

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
     *                If not -1, {@code minVers} must be at least 2000 ({@link #VERSION_FOR_SCENARIOS}).
     *                To calculate the minimum version of a set of game options which might include a scenario,
     *                use {@link SOCVersionedItem#itemsMinimumVersion(Map) SOCVersionedItem.itemsMinimumVersion(opts)}.
     *                That calculation won't be done automatically by this constructor.
     * @param lastModVers Last-modified version for this scenario, or version which added it.
     *             This is the last change to the scenario itself as declared in {@link #getAllKnownScenarios()}:
     *             Ignore changes to {@code opts} last-modified versions, because changed option info
     *             is sent separately and automatically when the client connects.
     * @param desc    Descriptive brief text, to appear in the scenarios dialog.
     *             Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param longDesc  Longer descriptive text, or null; see {@link #getLongDesc()} for requirements.
     * @param opts Scenario's {@link SOCGameOption}s, as a formatted string
     *             from {@link SOCGameOption#packOptionsToString(Map, boolean, boolean)}.
     *             Never "" or {@code null}.
     * @throws IllegalArgumentException if key length is > 8 or not alphanumeric,
     *        or if opts is {@code null} or the empty string "",
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}
     *        or fail their described requirements,
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
     *                If not -1, {@code minVers} must be at least 2000 ({@link #VERSION_FOR_SCENARIOS}).
     *                To calculate the minimum version of a set of game options which might include a scenario,
     *                use {@link SOCVersionedItem#itemsMinimumVersion(Map) SOCVersionedItem.itemsMinimumVersion(opts)}.
     *                That calculation won't be done automatically by this constructor.
     * @param lastModVers Last-modified version for this scenario, or version which added it.
     *             This is the last change to the scenario itself as declared in {@link #getAllKnownScenarios()}:
     *             Ignore changes to {@code opts} last-modified versions, because changed option info
     *             is sent separately and automatically when the client connects.
     * @param desc Descriptive brief text, to appear in the scenarios dialog.
     *             Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param longDesc  Longer descriptive text, or null; see {@link #getLongDesc()} for requirements.
     * @param opts Scenario's {@link SOCGameOption}s, as a formatted string
     *             from {@link SOCGameOption#packOptionsToString(Map, boolean, boolean)}.
     *             Never "" or {@code null}.
     * @throws IllegalArgumentException  if key is not alphanumeric or length is > 8,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if opts is {@code null} or the empty string "",
     *        or if minVers or lastModVers is under 2000 but not -1
     */
    private SOCScenario
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
        if (opts.length() == 0)
            throw new IllegalArgumentException("opts empty");

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
     *<LI> {@link #K_SC_FOG  SC_FOG}   Fog Islands
     *<LI> {@link #K_SC_TTD  SC_TTD}   Through The Desert
     *<LI> {@link #K_SC_CLVI SC_CLVI}  Cloth trade with neutral {@link SOCVillage villages}
     *<LI> {@link #K_SC_PIRI SC_PIRI}  Pirate Islands and {@link SOCFortress fortresses}
     *<LI> {@link #K_SC_FTRI SC_FTRI}  The Forgotten Tribe
     *<LI> {@link #K_SC_WOND SC_WOND}  Wonders
     *</UL>
     *  (See each scenario name field's javadoc for more details.)
     *
     * @return a deep copy of all known scenario objects
     * @see #getAllKnownScenarioKeynames()
     * @see #addKnownScenario(SOCScenario)
     * @see SOCGameOptionSet#getAllKnownOptions()
     */
    public static Map<String, SOCScenario> getAllKnownScenarios()
    {
        // To add a new scenario, see initAllScenarios().

        return cloneScenarios(allScenarios);
    }

    /**
     * Get the key names for all known scenarios.
     * This method avoids the copying overhead of {@link #getAllKnownScenarios()}.
     * @return The set of all scenarios' key names, such as {@link #K_SC_4ISL SC_4ISL}.
     *    Please treat the returned set as read-only.
     * @see #getAllKnownScenarios()
     */
    public static Set<String> getAllKnownScenarioKeynames()
    {
        return allScenarios.keySet();
    }

    /**
     * Add a new known scenario (received from a server having a newer or older version),
     * or update the scenario's information.
     *<P>
     * Because this method is client-only, it won't update the {@link #ALL_KNOWN_SCENARIOS_MIN_VERSION}
     * field used at the server.
     *
     * @param scNew New scenario, or a changed version of one we already know.
     * @return true if it's new, false if we already had that key and it was updated
     * @see #getAllKnownScenarios()
     * @see #removeUnknownScenario(String)
     */
    public static boolean addKnownScenario(SOCScenario scNew)
    {
        final boolean hadOld = (null != allScenarios.put(scNew.key, scNew));

        return ! hadOld;
    }

    /**
     * Remove a scenario from known scenarios, based on info received from a server having an older or newer version.
     * If {@code scKey} isn't a known scenario, does nothing.
     *<P>
     * Because this method is client-only, it won't update the {@link #ALL_KNOWN_SCENARIOS_MIN_VERSION}
     * field used at the server.
     *
     * @param scKey  Scenario key marked as unknown by the server
     * @see #getAllKnownScenarios()
     * @see #addKnownScenario(SOCScenario)
     */
    public static void removeUnknownScenario(final String scKey)
    {
        allScenarios.remove(scKey);  // OK if scKey wasn't in map
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
     * Treat the returned value as read-only (is not cloned).
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
        StringBuilder sb = new StringBuilder();
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
     * @return {@code null} if all are known; or, a human-readable problem description if:
     *            <UL>
     *            <LI> any of {@code newScens} are unknown
     *            <LI> or a scenario's type differs from that in knownScenarios
     *            <LI> or a {@link #lastModVersion} differs from in knownScenarios
     *            </UL>
     */
    public static StringBuilder adjustScenariosToKnown
        (Map<String, SOCScenario> newScens, Map<String, SOCScenario> knownScenarios,
         final boolean doServerPreadjust)
    {
        if (knownScenarios == null)
            knownScenarios = allScenarios;

        StringBuilder scProblems = new StringBuilder();

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
     * @param newScens Set to set {@code scKey} within
     * @param scKey   Key name for scenario to include
     * @throws NullPointerException  if {@code scKey} isn't in {@code newScens}
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
     * Detailed text for the scenario description and special rules, or null.  Shown as a reminder at start of a game.
     * Must not contain network delimiter character {@link SOCMessage#sep_char}; {@link SOCMessage#sep2_char} is okay.
     * Must pass {@link SOCMessage#isSingleLineAndSafe(String, boolean) SOCMessage.isSingleLineAndSafe(String, true)}.
     * Don't include the description of any scenario game option, such as {@link SOCGameOptionSet#K_SC_SANY};
     * those will be taken from {@link SOCVersionedItem#getDesc() SOCGameOption.desc} and shown in the reminder message.
     *<P>
     * To update this field use {@link #setDesc(String, String)}.
     *
     * @return The long description, or null if none
     */
    public String getLongDesc()
    {
        return scLongDesc;
    }

    /**
     * For i18n, update the scenario's description text fields:
     * The name/short description ({@link #getDesc()}) and optional long description ({@link #getLongDesc()}).
     *
     * @param desc    Descriptive brief text, to appear in the scenarios dialog. Not null.
     *     Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *     and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param longDesc  Longer descriptive text, or null; see {@link #getLongDesc()} for requirements.
     *     If null, keeps scenario's current (probably hardcoded unlocalized) longDesc.
     * @throws IllegalArgumentException if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or desc or longDesc fails {@link SOCMessage#isSingleLineAndSafe(String, boolean)}
     * @see SOCVersionedItem#setDesc(String)
     */
    public void setDesc(final String desc, final String longDesc)
        throws IllegalArgumentException
    {
        if (longDesc != null)
        {
            if (! SOCMessage.isSingleLineAndSafe(longDesc, true))
                throw new IllegalArgumentException("longDesc fails isSingleLineAndSafe");
            if (longDesc.contains(SOCMessage.sep))
                throw new IllegalArgumentException("longDesc contains " + SOCMessage.sep);

            scLongDesc = longDesc;
        }

        setDesc(desc);  // checks isSingleLineAndSafe(desc)
    }

    /**
     * Get this scenario's description, for use in user-facing displays and GUI elements.
     * For a short unique identifier use {@link #key} instead.
     * @return {@link SOCVersionedItem#desc desc}
     */
    @Override
    public String toString()
    {
        return desc;
    }

    /**
     * Compare two scenarios, for display purposes. ({@link Comparable} interface)
     * Two game scenarios are considered equal if they have the same {@link SOCVersionedItem#key key}.
     * Greater/lesser is determined by
     * {@link SOCVersionedItem#getDesc() getDesc()}.{@link String#compareTo(String) compareTo()}.
     * @param other A SOCScenario to compare, or another object;  if other isn't a
     *              scenario, the {@link #hashCode()}s are compared.
     * @see #equals(Object)
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

    /**
     * Test if this scenario equals another object.
     * Two game scenarios are considered equal if they have the same {@link SOCVersionedItem#key key}.
     * @param other A SOCScenario to compare, or another object;  if other isn't a
     *      scenario, calls {@link Object#equals(Object) Object.equals(other)}.
     * @see #compareTo(Object)
     * @see #hashCode()
     */
    @Override
    public boolean equals(final Object other)
    {
        if (other == null)
            return false;
        else if (other instanceof SOCScenario)
            return key.equals(((SOCScenario) other).key);
        else
            return super.equals(other);
    }

    /**
     * Return this scenario's hashCode for comparison purposes,
     * which is its {@link SOCVersionedItem#key key}'s {@link String#hashCode()}.
     * @see #equals(Object)
     */
    @Override
    public int hashCode() { return key.hashCode(); }

}
