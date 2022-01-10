/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020-2022 Jeremy D Monin <jeremy@nand.net>
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import soc.game.SOCGameOption;
import soc.server.SOCServer;  // for javadocs only
import soc.server.savegame.SavedGameModel;  // for javadocs only
import soc.util.DataUtils;
import soc.util.SOCFeatureSet;
import soc.util.Version;
import static soc.game.SOCGameOption.FLAG_DROP_IF_UNUSED;  // for convenience in getAllKnownOptions()

/**
 * A set of {@link SOCGameOption}s, either those of a game,
 * or all possible Known Options at a server or client: {@link #getAllKnownOptions()}.
 *<P>
 * Internally this is a {@code Map} whose keys are the options' {@link SOCVersionedItem#key}s.
 *<P>
 * Before v2.5.00 such sets were represented by <tt>Map&lt;String, SOCGameOption&gt;</tt>
 * and these methods were part of {@link SOCGameOption}.
 * So, some methods here are marked <tt>@since</tt> earlier version numbers.
 * Many classes still use the Map format for simplicity:
 * Use {@link #getAll()} for a lightweight Map of the set.
 *
 *<H3>Known Options</H3>
 *
 * For the list of all known options, see {@link #getAllKnownOptions()}.
 *<P>
 * Methods to work with a set of Known Options:
 *
 *<H4>Synchronizing options between server/client versions</H4>
 *<UL>
 * <LI> {@link #optionsForVersion(int)}
 * <LI> {@link #optionsNewerThanVersion(int, boolean, boolean)}
 * <LI> {@link #adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)}
 * <LI> {@link SOCGameOption#packKnownOptionsToString(SOCGameOptionSet, boolean, boolean)}
 * <LI> {@link SOCGameOption#parseOptionsToSet(String, SOCGameOptionSet)}
 * <LI> {@link SOCGameOption#getMaxIntValueForVersion(String, int)}
 * <LI> {@link SOCGameOption#getMaxEnumValueForVersion(String, int)}
 * <LI> {@link SOCGameOption#trimEnumForVersion(SOCGameOption, int)}
 *</UL>
 *
 *<H4>Individual Options</H4>
 *<UL>
 * <LI> {@link #getKnownOption(String, boolean)}
 * <LI> {@link #addKnownOption(SOCGameOption)}
 * <LI> {@link #setKnownOptionCurrentValue(SOCGameOption)}
 *</UL>
 *
 *<H4>Options available only when Activated or when client has a Feature</H4>
 *<UL>
 * <LI> {@link #optionsNotSupported(SOCFeatureSet)}
 * <LI> {@link #optionsTrimmedForSupport(SOCFeatureSet)}
 * <LI> {@link #optionsWithFlag(int, int)}
 * <LI> {@link #activate(String)}
 *</UL>
 *
 * @author Jeremy D. Monin &lt;jeremy@nand.net&gt;
 * @since 2.5.00
 */
public class SOCGameOptionSet
    implements Iterable<SOCGameOption>
{

    // Some game option keynames, for convenient reference in code and javadocs:

    // -- Game option keynames for scenario flags --
    // Not all scenario keynames have scenario events, some are just properties of the game.

    /**
     * Scenario key <tt>_SC_SANY</tt> for {@link SOCPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}.
     * See that event's javadoc for details.
     * @since 2.0.00
     */
    public static final String K_SC_SANY = "_SC_SANY";

    /**
     * Scenario key <tt>_SC_SEAC</tt> for {@link SOCPlayerEvent#SVP_SETTLED_EACH_NEW_LANDAREA}.
     * See that event's javadoc for details.
     * @since 2.0.00
     */
    public static final String K_SC_SEAC = "_SC_SEAC";

    /**
     * Scenario key <tt>_SC_FOG</tt> for {@link SOCGameEvent#SGE_FOG_HEX_REVEALED}.
     * @see SOCScenario#K_SC_FOG
     * @since 2.0.00
     */
    public static final String K_SC_FOG = "_SC_FOG";

    /**
     * Scenario key <tt>_SC_0RVP</tt>: No "longest trade route" VP / Longest Road.
     * @since 2.0.00
     */
    public static final String K_SC_0RVP = "_SC_0RVP";

    /**
     * Scenario key <tt>_SC_3IP</tt>: Third initial placement of settlement and road or ship.
     * Initial resources are given for this one, not the second settlement.
     * @since 2.0.00
     */
    public static final String K_SC_3IP = "_SC_3IP";

    /**
     * Scenario key <tt>_SC_CLVI</tt> for {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}:
     * Cloth Trade with neutral {@link SOCVillage villages}.
     * Villages and cloth are in a game only if this option is set.
     * @since 2.0.00
     * @see SOCScenario#K_SC_CLVI
     */
    public static final String K_SC_CLVI = "_SC_CLVI";

    /**
     * Scenario key <tt>_SC_PIRI</tt> for Pirate Islands and {@link SOCFortress Fortresses}.
     * Fortresses and player warships are in a game only if this option is set.
     * For more details and special rules see {@link SOCScenario#K_SC_PIRI}.
     * @since 2.0.00
     */
    public static final String K_SC_PIRI = "_SC_PIRI";

    /**
     * Scenario key {@code _SC_FTRI} for the Forgotten Tribe.
     * Special edges with SVP, dev cards, and "gift" ports placed via {@link SOCInventoryItem}.
     * For more details and special rules see {@link SOCScenario#K_SC_FTRI}.
     * @since 2.0.00
     */
    public static final String K_SC_FTRI = "_SC_FTRI";

    /**
     * Scenario key {@code _SC_WOND} for Wonders.
     * Special unique "wonders" claimed by players and built up to several levels. No pirate ship.
     * For more details, special rules, and {@link SOCSpecialItem Special Item}s, see {@link SOCScenario#K_SC_WOND}.
     * @since 2.0.00
     */
    public static final String K_SC_WOND = "_SC_WOND";

    // -- End of scenario flag keynames --

    /**
     * Inactive boolean game option {@code "PLAY_FO"}:
     * All player info is fully observable. If activated and true,
     * server announces all resource and dev card details with actual types, not "unknown".
     * Useful for developers. Minimum client version 2.0.00.
     * @see #K_PLAY_VPO
     * @since 2.5.00
     */
    public static final String K_PLAY_FO = "PLAY_FO";

    /**
     * Inactive boolean game option {@code "PLAY_VPO"}:
     * All player VP/card info is observable. If activated and true,
     * server announces all dev card details with actual types, not "unknown".
     * Useful for developers. Minimum client version 2.0.00.
     * @see #K_PLAY_FO
     * @since 2.5.00
     */
    public static final String K_PLAY_VPO = "PLAY_VPO";

    // -- Extra option keynames --

    /**
     * An "extra" option key {@code _EXT_BOT} available for robot development.
     * Available for third-party bot developers: Not used by JSettlers core itself.
     * Can hold a string of data which is sent to all robot clients joining a game,
     * entered on the server command line or properties file. A third-party bot might
     * want to use this option's value to configure its behavior or debug settings.
     * Maximum length of this option's value is {@link #TEXT_OPTION_MAX_LENGTH}.
     * @see #K__EXT_CLI
     * @see #K__EXT_GAM
     * @since 2.0.00
     */
    public static final String K__EXT_BOT = "_EXT_BOT";

    /**
     * An "extra" option key {@code _EXT_CLI} available for client development.
     * Available for third-party developers: Not used by JSettlers core itself.
     * Can hold a string of data which is sent to all clients,
     * entered on the server command line or properties file.
     * Maximum length of this option's value is {@link #TEXT_OPTION_MAX_LENGTH}.
     * @see #K__EXT_BOT
     * @see #K__EXT_GAM
     * @since 2.0.00
     */
    public static final String K__EXT_CLI = "_EXT_CLI";

    /**
     * An "extra" option key {@code _EXT_GAM} available for game development.
     * Available for third-party developers: Not used by JSettlers core itself.
     * Can hold a string of data which is sent to the game at all clients,
     * entered on the server command line or properties file.
     * Maximum length of this option's value is {@link #TEXT_OPTION_MAX_LENGTH}.
     * @see #K__EXT_BOT
     * @see #K__EXT_CLI
     * @since 2.0.00
     */
    public static final String K__EXT_GAM = "_EXT_GAM";

    // -- End of extra option keynames --

    /**
     * The options within this set; never {@code null}.
     */
    private final Map<String, SOCGameOption> options;

    /**
     * Create a new empty set.
     */
    public SOCGameOptionSet()
    {
        options = new HashMap<>();
    }

    /**
     * Create an independent copy or deep copy of another set.
     * @param opts  Set to copy; not null
     * @param deepCopy If true also clone each {@link SOCGameOption} in the set,
     *     instead of doing a shallow copy to a new set with to those same option references
     * @throws NullPointerException if {@code opts} is null
     */
    public SOCGameOptionSet(final SOCGameOptionSet opts, final boolean deepCopy)
        throws NullPointerException
    {
        if (deepCopy)
        {
            options = new HashMap<>();
            try
            {
                for (final SOCGameOption opt : opts.options.values())
                    options.put(opt.key, (SOCGameOption) opt.clone());
            } catch (CloneNotSupportedException e) {
                // catch required, but not expected to ever happen
                throw new IllegalStateException("clone failed");
            }
        } else {
            options = new HashMap<>(opts.options);
        }
    }

    /**
     * Create a set which contains options.
     * @param opts  Options to include, or null to make an empty set
     */
    public SOCGameOptionSet(final Map<String, SOCGameOption> opts)
    {
        options = (opts != null) ? new HashMap<>(opts) : new HashMap<String, SOCGameOption>();
    }

    /**
     * Create and return a set of the Known Options.
     *<P>
     * Before v2.5.00 this method was {@code SOCGameOption.initAllOptions()}.
     *
     * <h3>Current known options:</h3>
     *<UL>
     *<LI> PL  Maximum # players (2-6)
     *<LI> PLB Use 6-player board*
     *<LI> PLP 6-player board: Can Special Build only if 5 or 6 players in game*
     *<LI> SBL Use sea board layout (has a large, varying max size)
     *<LI> RD  Robber can't return to the desert
     *<LI> N7  Roll no 7s during first # rounds
     *<LI> N7C Roll no 7s until a city is built
     *<LI> BC  Break up clumps of # or more same-type ports/hexes
     *<LI> NT  No trading allowed
     *<LI> VP  Victory points (10-15)
     *<LI> SC  Game Scenario (optional groups of rules; see {@link SOCScenario})
     *<LI> _BHW  Board height and width, if not default, for {@link SOCBoardLarge}: 0xRRCC.
     *           Used only at client, for board size received in JoinGame message from server
     *           to pass through SOCGame constructor into SOCBoard factory
     *<LI> _VP_ALL  If true in server's Known Options, server's default VP is used for all scenarios, instead of scenario's higher VP amount
     *</UL>
     *  * Grouping: PLB, PLP are 3 characters, not 2, and the first 2 characters match an
     *    existing option. So in NewGameOptionsFrame, they appear on the lines following
     *    the PL option in client version 1.1.13 and above.
     *<P>
     * The original set of options (v1.1.07) was {@code PL, RD, N7, BC, NT}. All others were added in newer versions.
     *
     * <h3>Current Game Scenario options:</h3>
     *<UL>
     *<LI> {@link #K_SC_SANY _SC_SANY}  SVP to settle in any new land area:
     *         {@link SOCPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}
     *<LI> {@link #K_SC_SEAC _SC_SEAC}  2 SVP each time settle in another new land area:
     *         {@link SOCPlayerEvent#SVP_SETTLED_EACH_NEW_LANDAREA}
     *<LI> {@link #K_SC_FOG  _SC_FOG}   A hex has been revealed from behind fog:
     *         {@link SOCGameEvent#SGE_FOG_HEX_REVEALED}: See {@link SOCScenario#K_SC_FOG}
     *<LI> {@link #K_SC_0RVP _SC_0RVP}  No VP for longest road / longest trade route
     *<LI> {@link #K_SC_3IP  _SC_3IP}   Third initial settlement and road/ship placement
     *<LI> {@link #K_SC_CLVI _SC_CLVI}  Cloth trade with neutral {@link SOCVillage villages}: See {@link SOCScenario#K_SC_CLVI}
     *<LI> {@link #K_SC_FTRI _SC_FTRI}  The Forgotten Tribe: See {@link SOCScenario#K_SC_FTRI}
     *<LI> {@link #K_SC_PIRI _SC_PIRI}  Pirate Islands and {@link SOCFortress fortresses}: See {@link SOCScenario#K_SC_PIRI}
     *<LI> {@link #K_SC_WOND _SC_WOND}  Wonders: See {@link SOCScenario#K_SC_WOND}
     *</UL>
     *
     * <h3>Options for quick tests/prototyping:</h3>
     *
     * For quick tests or prototyping, including third-party bot/AI/client development,
     * there are a few predefined but unused game options available:
     *<UL>
     *<LI> {@link #K__EXT_BOT _EXT_BOT}  Extra option for robot development
     *<LI> {@link #K__EXT_CLI _EXT_CLI}  Extra option for client development
     *<LI> {@link #K__EXT_GAM _EXT_GAM}  Extra option for game development
     *</UL>
     * These can be used to easily send config or debug settings to your bot or client when it joins a game,
     * by setting a default value at the server's command line or properties file.
     *
     * <h3>If you want to add a game option:</h3>
     *<UL>
     *<LI> Choose an unused unique name key: for example, "PL" for "max players".
     *   All in-game code uses these key strings to query and change
     *   game option settings; only a very few places use SOCGameOption
     *   objects, and you won't need to adjust those places.
     *   The list of already-used key names is here within getAllKnownOptions.
     *   <P>
     *   If your option is useful only for developers or in other special situations,
     *   and should normally be hidden from clients, define it as an Inactive Option
     *   by using the {@link SOCGameOption#FLAG_INACTIVE_HIDDEN} flag.
     *   <P>
     *   If you're forking JSettlers or developing a third-party client, server, or bot,
     *   any game options you add should use {@code '3'} as the second character of
     *   their name key: {@code "_3"}, {@code "T3"}, etc.
     *   Use {@link SOCGameOption#FLAG_3RD_PARTY} when specifying your options; see its javadoc for details.
     *   <P>
     *   If your option supports a {@link SOCScenario}, its name should
     *   start with "_SC_" and it should have a constant name field here
     *   (like {@link #K_SC_3IP}) with a short descriptive javadoc.
     *   Link in javadoc to the SOCScenario field and add it to the list above.
     *   Because name starts with "_SC_", constructor will automatically call
     *   {@link SOCGameOption#setClientFeature(String) setClientFeature}({@link SOCFeatureSet#CLIENT_SEA_BOARD}).
     *   If you need a different client feature instead, or none, call that setter afterwards.
     *<LI> Decide which {@link SOCGameOption#optType option type} your option will be
     *   (boolean, enumerated, int+bool, etc.), and its default value.
     *   Typically the default will let the game behave as it did before
     *   the option existed (for example, the "max players" default is 4).
     *   Its default value on your own server can be changed at runtime.
     *<LI> Decide if all client versions can use your option.  Typically, if the option
     *   requires server changes but not any client changes, all clients can use it.
     *   (For example, "N7" for "roll no 7s early in the game" works with any client
     *   because dice rolls are done at the server.)
     *<LI> Create the option by calling opt.put here in getAllKnownOptions.
     *   Use the current version for the "last modified" field.
     *<LI> Add the new option's description to the {@code gameopt.*} section of
     *   {@code server/strings/toClient_*.properties} to be sent to clients if needed.
     *<LI> If only <em>some values</em> of the option will require client changes,
     *   also update {@link SOCGameOption#getMinVersion(Map)}.  (For example, if "PL"'s value is 5 or 6,
     *   a new client would be needed to display that many players at once, but 2 - 4
     *   can use any client version.) <BR>
     *   If this is the case and your option type
     *   is {@link SOCGameOption#OTYPE_ENUM} or {@link SOCGameOption#OTYPE_ENUMBOOL}, also update
     *   {@link SOCGameOption#getMaxEnumValueForVersion(String, int)}.
     *   Otherwise, update {@link #getMaxIntValueForVersion(String, int)}.
     *<LI> If the new option can be used by old clients by changing the values of
     *   <em>other</em> related options when game options are sent to those versions,
     *   add code to {@link SOCGameOption#getMinVersion(Map)}. <BR>
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
     *   <LI> {@link soc.baseclient.SOCDisplaylessPlayerClient} is the foundation for the robot client,
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
     *   {@link soc.util.SOCRobotParameters#copyIfOptionChanged(SOCGameOptionSet)}
     *   which ignore most, but not all, game options.
     *<LI> If the new option adds new game/player/board fields or piece types which aren't
     *   currently in {@link SavedGameModel}:
     *   <UL>
     *   <LI> Either add the fields there, and test to make sure SAVEGAME/LOADGAME handles their data properly
     *   <LI> Or, check for and reject the new option in {@link SavedGameModel#checkCanSave(SOCGame)}
     *       and {@link SavedGameModel#checkCanLoad(SOCGameOptionSet)};
     *       add a {@code TODO} to later add support to SavedGameModel
     *   </UL>
     *</UL>
     *
     *<h3>If you want to change a game option (in a later version):</h3>
     *
     *   Typical changes to a game option would be:
     *<UL>
     *<LI> Add new values to an {@link SOCGameOption#OTYPE_ENUM} enumerated option;
     *   they must be added to the end of the list
     *<LI> Change the maximum or minimum permitted values for an
     *   {@link SOCGameOption#OTYPE_INT} integer option
     *<LI> Change the default value, although this can also be done
     *   at runtime on the command line
     *<LI> Change the value at the server based on other options' values
     *</UL>
     *   Things you can't change about an option, because inconsistencies would occur:
     *<UL>
     *<LI> {@link SOCVersionedItem#key name key}
     *<LI> {@link SOCGameOption#optType}
     *<LI> {@link SOCGameOption#minVersion}
     *<LI> {@link SOCGameOption#optFlags} such as {@link SOCGameOption#FLAG_DROP_IF_UNUSED}:
     *     Newly defined flags could maybe be added, if old versions can safely ignore them,
     *     but a flag can't be removed from an option in a later version.
     *<LI> For {@link SOCGameOption#OTYPE_ENUM} and {@link SOCGameOption#OTYPE_ENUMBOOL}, you can't remove options
     *     or change the meaning of current ones, because this would mean that the option's intValue (sent over
     *     the network) would mean different things to different-versioned clients in the game.
     *</UL>
     *
     *<H4>To make the change:</H4>
     *<UL>
     *<LI> Change the option here in getAllKnownOptions; change the "last modified" field to
     *   the current game version. Otherwise the server can't tell the client what has
     *   changed about the option.
     *<LI> If new values require a newer minimum client version, add code to {@link SOCGameOption#getMinVersion(Map)}.
     *<LI> If adding a new enum value for {@link SOCGameOption#OTYPE_ENUM} and {@link SOCGameOption#OTYPE_ENUMBOOL},
     *   add code to {@link SOCGameOption#getMaxEnumValueForVersion(String, int)}.
     *<LI> If increasing the maximum value of an int-valued parameter, and the new maximum
     *   requires a certain version, add code to {@link SOCGameOption#getMaxIntValueForVersion(String, int)}.
     *   For example, versions below 1.1.08 limit "max players" to 4.
     *<LI> Search the entire source tree for its key name, to find places which may need an update.
     *<LI> Consider if any other places listed above (for add) need adjustment.
     *</UL>
     *
     * <h3>If you want to remove or obsolete a game option (in a later version):</h3>
     *
     * Please think twice beforehand; breaking compatibility with older clients shouldn't
     * be done without a very good reason.  That said, the server is authoritative on options.
     * If an option isn't in its Known Options set (from when it called this {@code getAllKnownOptions()} method),
     * the client won't be allowed to ask for it.  Any obsolete options should be kept around as commented-out code.
     *
     * @return a fresh copy of the "known" options, with their hardcoded default values.
     *     Includes all defined options, including those with {@link SOCGameOption#FLAG_INACTIVE_HIDDEN}
     *     or {@link SOCGameOption#FLAG_3RD_PARTY}.
     * @see #getKnownOption(String, boolean)
     * @see #addKnownOption(SOCGameOption)
     * @see SOCScenario#getAllKnownScenarios()
     * @since 1.1.07
     */
    public static SOCGameOptionSet getAllKnownOptions()
    {
        final SOCGameOptionSet opts = new SOCGameOptionSet();

        // I18N: Game option descriptions are also stored as gameopt.* in server/strings/toClient_*.properties
        //       to be sent to clients if needed.

        final SOCGameOption optPL = new SOCGameOption
            ("PL", -1, 1108, 4, 2, 6, 0, "Maximum # players");
        opts.add(optPL);

        final SOCGameOption optPLB = new SOCGameOption
            ("PLB", 1108, 1113, false, FLAG_DROP_IF_UNUSED, "Use 6-player board");
        optPLB.setClientFeature(SOCFeatureSet.CLIENT_6_PLAYERS);
        opts.add(optPLB);

        final SOCGameOption optPLP = new SOCGameOption
            ("PLP", 1108, 2300, false, FLAG_DROP_IF_UNUSED, "6-player board: Can Special Build only if 5 or 6 players in game");
        optPLP.setClientFeature(SOCFeatureSet.CLIENT_6_PLAYERS);
        opts.add(optPLP);

        SOCGameOption optSBL = new SOCGameOption
            ("SBL", 2000, 2000, false, FLAG_DROP_IF_UNUSED, "Use sea board");  // see also SOCBoardLarge
        optSBL.setClientFeature(SOCFeatureSet.CLIENT_SEA_BOARD);
        opts.add(optSBL);

        opts.add(new SOCGameOption
            ("_BHW", 2000, 2000, 0, 0, 0xFFFF, FLAG_DROP_IF_UNUSED | SOCGameOption.FLAG_INTERNAL_GAME_PROPERTY,
             "Large board's height and width (0xRRCC) if not default (local to client only)"));
        opts.add(new SOCGameOption
            ("RD", -1, 1107, false, 0, "Robber can't return to the desert"));
        opts.add(new SOCGameOption
            ("N7", -1, 1107, false, 7, 1, 999, 0, "Roll no 7s during first # rounds"));
        // N7C's keyname puts it after N7 in the NewGameOptionsFrame list
        opts.add(new SOCGameOption
            ("N7C", -1, 1119, false, FLAG_DROP_IF_UNUSED, "Roll no 7s until a city is built"));
        opts.add(new SOCGameOption
            ("BC", -1, 1107, true, 4, 3, 9, 0, "Break up clumps of # or more same-type hexes/ports"));
        opts.add(new SOCGameOption
            ("NT", 1107, 1107, false, FLAG_DROP_IF_UNUSED, "No trading allowed between players"));
        opts.add(new SOCGameOption
            ("VP", -1, 2000, false, 10, 10, 20, FLAG_DROP_IF_UNUSED, "Victory points to win: #"));
            // If min or max changes, test client to make sure New Game dialog still shows it as a dropdown
            // (not a text box) for user convenience
        opts.add(new SOCGameOption
            ("_VP_ALL", 2000, 2500, false, FLAG_DROP_IF_UNUSED, "Use default VP in all scenarios"));

        final SOCGameOption optSC = new SOCGameOption
            ("SC", 2000, 2000, 8, false, FLAG_DROP_IF_UNUSED, "Game Scenario: #");
        optSC.setClientFeature(SOCFeatureSet.CLIENT_SCENARIO_VERSION);
        opts.add(optSC);

        // Game scenario options (rules and events)
        //      Constructor calls setClientFeature(SOCFeatureSet.CLIENT_SCENARIO_VERSION) for these
        //      because keyname.startsWith("_SC_")

        //      I18N note: NewGameOptionsFrame.showScenarioInfoDialog() assumes these
        //      all start with the text "Scenarios:". When localizing, be sure to
        //      keep a consistent prefix that showScenarioInfoDialog() knows to look for.
        //      In client/strings/data_*.properties, set game.options.scenario.optprefix to that prefix.

        opts.add(new SOCGameOption
            (K_SC_SANY, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
             "Scenarios: SVP for your first settlement on any island after initial placement"));
        opts.add(new SOCGameOption
            (K_SC_SEAC, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
             "Scenarios: 2 SVP for your first settlement on each island after initial placement"));
        opts.add(new SOCGameOption
            (K_SC_FOG, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
             "Scenarios: Some hexes initially hidden by fog"));
        opts.add(new SOCGameOption
            (K_SC_0RVP, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
             "Scenarios: No longest trade route VP (no Longest Road)"));
        opts.add(new SOCGameOption
            (K_SC_3IP, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
             "Scenarios: Third initial settlement"));
        opts.add(new SOCGameOption
            (K_SC_CLVI, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
             "Scenarios: Cloth Trade with neutral villages"));
        opts.add(new SOCGameOption
            (K_SC_PIRI, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
             "Scenarios: Pirate Islands and fortresses"));
        opts.add(new SOCGameOption
            (K_SC_FTRI, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
             "Scenarios: The Forgotten Tribe"));
        opts.add(new SOCGameOption
            (K_SC_WOND, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
             "Scenarios: Wonders"));

        // "Extra" options for third-party developers

        opts.add(new SOCGameOption
            (K__EXT_BOT, 2000, 2000, SOCGameOption.TEXT_OPTION_MAX_LENGTH, false, FLAG_DROP_IF_UNUSED,
             "Extra non-core option available for robots in this game"));
        opts.add(new SOCGameOption
            (K__EXT_CLI, 2000, 2000, SOCGameOption.TEXT_OPTION_MAX_LENGTH, false, FLAG_DROP_IF_UNUSED,
             "Extra non-core option available for clients in this game"));
        opts.add(new SOCGameOption
            (K__EXT_GAM, 2000, 2000, SOCGameOption.TEXT_OPTION_MAX_LENGTH, false, FLAG_DROP_IF_UNUSED,
             "Extra non-core option available for this game"));

        // Player info observability, for developers

        opts.add(new SOCGameOption
            (K_PLAY_FO, 2000, 2500, false, SOCGameOption.FLAG_INACTIVE_HIDDEN | FLAG_DROP_IF_UNUSED,
             "Show all player info and resources"));
        opts.add(new SOCGameOption
            (K_PLAY_VPO, 2000, 2500, false, SOCGameOption.FLAG_INACTIVE_HIDDEN | FLAG_DROP_IF_UNUSED,
             "Show all VP/dev card info"));

        // NEW_OPTION - Add opt.put here at end of list, and update the
        //       list of "current known options" in javadoc just above.

        // ChangeListeners for client convenience:
        // Remember that a new server version can't update this code at an older client:
        // If you create a ChangeListener, also update adjustOptionsToKnown for server-side code.

        // If PL goes over 4, set PLB.
        optPL.addChangeListener(new SOCGameOption.ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption op, Object oldValue, Object newValue,
                 final SOCGameOptionSet currentOpts, final SOCGameOptionSet knownOpts)
            {
                if  (! (oldValue instanceof Integer))
                    return;  // ignore unless int
                final int ov = ((Integer) oldValue).intValue();
                final int nv = ((Integer) newValue).intValue();
                if ((ov <= 4) && (nv > 4))
                {
                    SOCGameOption plb = currentOpts.get("PLB");
                    if (plb == null)
                        return;
                    plb.setBoolValue(true);
                    plb.refreshDisplay();
                }
            }
        });

        // If PLB becomes unchecked, set PL to 4 if it's 5 or 6;
        // if it becomes checked, set PL to 6 if <= 4, unless PL.userChanged already
        optPLB.addChangeListener(new SOCGameOption.ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption op, Object oldValue, Object newValue,
                 final SOCGameOptionSet currentOpts, final SOCGameOptionSet knownOpts)
            {
                SOCGameOption pl = currentOpts.get("PL");
                if (pl == null)
                    return;
                final int numPl = pl.getIntValue();
                boolean refreshPl = false;

                if (Boolean.TRUE.equals(newValue))
                {
                    // PLB became checked; check PL 4 vs 6
                    if ((numPl <= 4) && ! pl.userChanged)
                    {
                        pl.setIntValue(6);
                        refreshPl = true;
                    }
                } else {
                    // PLB became unchecked

                    if (numPl > 4)
                    {
                        pl.setIntValue(4);
                        pl.userChanged = false;  // so re-check will set to 6
                        refreshPl = true;
                    }

                    // numPl <= 4, so PLP doesn't apply
                    SOCGameOption plp = currentOpts.get("PLP");
                    if ((plp != null) && plp.getBoolValue() && ! plp.userChanged)
                    {
                        plp.setBoolValue(false);
                        plp.refreshDisplay();
                    }
                }

                if (refreshPl)
                    pl.refreshDisplay();
            }
        });

        // If PLP is set or cleared, also set or clear PLB unless user's already changed it
        optPLP.addChangeListener(new SOCGameOption.ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption op, Object oldValue, Object newValue,
                 final SOCGameOptionSet currentOpts, final SOCGameOptionSet knownOpts)
            {
                final boolean changedTo = (Boolean.TRUE.equals(newValue));

                SOCGameOption plb = currentOpts.get("PLB");
                if ((plb == null) || plb.userChanged || (changedTo == plb.getBoolValue()))
                    return;

                plb.setBoolValue(changedTo);
                plb.refreshDisplay();
            }
        });

        // If SC (scenario) is chosen, also set SBL (use sea board)
        // and VP (vp to win), unless already changed by user or using "_VP_ALL".
        // This is for NGOF responsiveness during new-game option setup at the client:
        // Game creation at the server doesn't rely on these updates.
        // For game creation with scenario options, see adjustOptionsToKnown(doServerPreadjust=true).

        optSC.addChangeListener(new SOCGameOption.ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption optSc, Object oldValue, Object newValue,
                 final SOCGameOptionSet currentOpts, final SOCGameOptionSet knownOpts)
            {
                final String newSC = optSc.getStringValue();
                final boolean isScenPicked = optSc.getBoolValue() && (newSC.length() != 0);

                // check/update #VP if scenario specifies larger, otherwise revert to standard
                SOCGameOption vp = currentOpts.get("VP");
                if ((vp != null) && ! (vp.userChanged || currentOpts.isOptionSet("_VP_ALL")))
                {
                    int newVP = vp.defaultIntValue;  // usually == SOCGame.VP_WINNER_STANDARD
                    if (isScenPicked)
                    {
                        final SOCScenario scen = SOCScenario.getScenario(newSC);
                        if (scen != null)
                        {
                            final Map<String, SOCGameOption> scenOpts =
                                SOCGameOption.parseOptionsToMap(scen.scOpts, knownOpts);
                            final SOCGameOption scOptVP = (scenOpts != null) ? scenOpts.get("VP") : null;
                            if (scOptVP != null)
                            {
                                final int scenVP = scOptVP.getIntValue();
                                if (scenVP > newVP)
                                    newVP = scenVP;
                            }

                            // TODO possibly update other scen opts, not just VP
                        }
                    }

                    if (newVP != vp.getIntValue())
                    {
                        vp.setIntValue(newVP);
                        vp.setBoolValue(newVP != SOCGame.VP_WINNER_STANDARD);
                        vp.refreshDisplay();
                    }
                }

                // check/update SBL
                SOCGameOption sbl = currentOpts.get("SBL");
                if ((sbl != null) && ! sbl.userChanged)
                {
                    if (isScenPicked != sbl.getBoolValue())
                    {
                        sbl.setBoolValue(isScenPicked);
                        sbl.refreshDisplay();
                    }
                }
            }
        });

        /*
            // A commented-out debug option for testing convenience:
            // Un-comment to let client create games that no one can join.

        opts.add(new SOCGameOption
            ("DEBUGNOJOIN", Integer.MAX_VALUE, Integer.MAX_VALUE, false,
             SOCGameOption.FLAG_DROP_IF_UNUSED, "Cannot join this game"));
        */

        /*
            // A commented-out debug option is kept here for each option type's testing convenience.
            // OTYPE_* - Add a commented-out debug of the new type, for testing the new type.

        opts.add(new SOCGameOption
            ("DEBUGBOOL", 2000, Version.versionNumber(), false, SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option bool"));
        opts.add(new SOCGameOption
            ("DEBUGENUM", 1107, Version.versionNumber(), 3,
             new String[]{ "First", "Second", "Third", "Fourth"},
             SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option # enum"));
        opts.add(new SOCGameOption
            ("DEBUGENUMBOOL", 1107, Version.versionNumber(), true, 3,
             new String[]{ "First", "Second", "Third", "Fourth"},
             SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option # enumbool"));
        opts.add(new SOCGameOption
            ("DEBUGINT", -1, Version.versionNumber(), 500, 1, 1000,
             SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option int # (range 1-1000)"));
        opts.add(new SOCGameOption
            ("DEBUGSTR", 1107, Version.versionNumber(), 20, false, SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option str"));
        opts.add(new SOCGameOption
            ("DEBUGSTRHIDE", 1107, Version.versionNumber(), 20, true, SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option strhide"));

        opts.add(new SOCGameOption
            ("_3P", 2000, Version.versionNumber(), false, SOCGameOption.FLAG_3RD_PARTY | SOCGameOption.FLAG_DROP_IF_UNUSED, "Test third-party option"));
        opts.add(new SOCGameOption
            ("_3P2", 2000, Version.versionNumber(), false, SOCGameOption.FLAG_3RD_PARTY | SOCGameOption.FLAG_DROP_IF_UNUSED, "Second test third-party option"));
        */

        return opts;

        /*
            // TEST CODE: simple callback for each option, that just echoes old/new value

        SOCGameOption.ChangeListener testCL = new SOCGameOption.ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption opt, Object oldValue, Object newValue,
                 final SOCGameOptionSet currentOpts, final SOCGameOptionSet knownOpts)
            {
                System.err.println("Test ChangeListener: " + opt.key
                    + " changed from " + oldValue + " to " + newValue);
            }
        };
        for (SOCGameOption op : opts)
        {
            if (! op.hasChangeListener())
                op.addChangeListener(testCL);
        }

            // END TEST CODE
        */

        // OBSOLETE OPTIONS, REMOVED OPTIONS - Move its opt.put down here, commented out,
        //       including the version, date, and reason of the removal.
    }

    /**
     * Get this set's size (number of options).
     * @return number of {@link SOCGameOption}s currently in this set
     * @see #isEmpty()
     * @see #clear()
     */
    public int size()
    {
        return options.size();
    }

    /**
     * Is this set empty, containing 0 options?
     * @return true if {@link #size()} is 0
     * @see #clear()
     */
    public boolean isEmpty()
    {
        return options.isEmpty();
    }

    /**
     * Does the set contain an option with {@link SOCVersionedItem#key opt.key}?
     * @param opt  Option to look for by key; not null
     * @return True if set contains an option with this {@link SOCVersionedItem#key opt.key}
     * @throws NullPointerException if {@code opt} is null
     * @see #containsKey(String)
     * @see #get(String)
     */
    public boolean contains(final SOCGameOption opt)
        throws NullPointerException
    {
        return options.containsKey(opt.key);
    }

    /**
     * Does the set contain an option with this key?
     * @param optKey  Option key to look for
     * @return True if set contains an option with this {@link SOCVersionedItem#key}
     * @see #contains(SOCGameOption)
     * @see #get(String)
     */
    public boolean containsKey(final String optKey)
    {
        return options.containsKey(optKey);
    }

    /**
     * Add this option to the set, replacing any previous option there
     * with the same {@link SOCVersionedItem#key opt.key}.
     * If you need the replaced previous option, call {@link #put(SOCGameOption)} instead.
     * @param opt  Option to add by key; not null. May be any type, including {@link SOCGameOption#OTYPE_UNKNOWN}.
     * @return True if set didn't already contain an option with that key
     * @throws NullPointerException if {@code opt} is null
     * @see #remove(String)
     * @see #addKnownOption(SOCGameOption)
     */
    public boolean add(final SOCGameOption opt)
    {
        return (options.put(opt.key, opt) == null);
    }

    /**
     * Put this option into the set, replacing any previous option there
     * with the same {@link SOCVersionedItem#key opt.key}.
     * If you don't need the previous option, you can call {@link #add(SOCGameOption)} instead.
     * @param opt  Option to add by key; not null. May be any type, including {@link SOCGameOption#OTYPE_UNKNOWN}.
     * @return Previously contained option with that key, or {@code null} if none
     * @throws NullPointerException if {@code opt} is null
     * @see #get(String)
     * @see #remove(String)
     * @see #addKnownOption(SOCGameOption)
     */
    public SOCGameOption put(final SOCGameOption opt)
    {
        return options.put(opt.key, opt);
    }

    /**
     * Get the {@link SOCGameOption} in the set having this key, if any.
     * @param optKey  Option key to get
     * @return The option in this set having {@link SOCVersionedItem#key} == {@code optKey},
     *     or {@code null} if none
     * @see #getKnownOption(String, boolean)
     * @see #containsKey(String)
     * @see #getAll()
     */
    public SOCGameOption get(final String optKey)
    {
        return options.get(optKey);
    }

    /**
     * Get all options in the set, as a convenient Map backed by the set; treat as read-only.
     * For simplicity, many classes use that Map format instead of SOCGameOptionSet.
     * @return Map of options in the set, or an empty Map
     * @see #keySet()
     * @see #values()
     * @see #getAllKnownOptions()
     */
    public Map<String, SOCGameOption> getAll()
    {
        return options;  // for performance, skip copying to a new Map
    }

    /**
     * Get all options in the set. This collection is backed by the option set,
     * and supports iteration like {@link Map#values()}.
     * @return {@link SOCGameOption}s in the set, or an empty Collection
     * @see #keySet()
     * @see #getAll()
     */
    public Collection<SOCGameOption> values()
    {
        return options.values();
    }

    /**
     * Get all keys in the set. This set of keys is backed by the option set,
     * and supports iteration and element removal like {@link Map#keySet()}.
     * @return Option keys in the set, from each {@link SOCVersionedItem#key}, or an empty Set
     * @see #values()
     * @see #getAll()
     * @see #iterator()
     */
    public Set<String> keySet()
    {
        return options.keySet();
    }

    /**
     * For use in {@code for} loops, make and return an iterator;
     * calls {@link Map#values() map.values()}{@link Collection#iterator() .iterator()}.
     * {@link Iterator#remove()} is supported.
     * @see #keySet()
     */
    public Iterator<SOCGameOption> iterator()
    {
        return options.values().iterator();
    }

    /**
     * Remove the {@link SOCGameOption} in the set having this key, if any, and return it.
     * @param optKey  Option key to remove
     * @return The option removed from this set which has {@link SOCVersionedItem#key} == {@code optKey},
     *     or {@code null} if none was removed
     * @see #clear()
     */
    public SOCGameOption remove(final String optKey)
    {
        return options.remove(optKey);
    }

    /**
     * Remove all options from this set. Will be empty afterwards.
     * @see #isEmpty()
     * @see #remove(String)
     */
    public void clear()
    {
        options.clear();
    }

    // Examining and updating values within the set:

    /**
     * Is this boolean-valued or intbool-valued game option currently set to true?
     *<P>
     * Before v2.5.00 this method was {@code SOCGame.isGameOptionSet(opts, optKey)}.
     *
     * @param optKey Name of a {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_BOOL OTYPE_BOOL},
     *     {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL} or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     * @return True if option's boolean value is set, false if not set or not defined in this set of options
     * @see #setBoolOption(String, SOCGameOptionSet)
     * @see #getOptionIntValue(String)
     * @see #getOptionStringValue(String)
     * @since 1.1.07
     */
    public boolean isOptionSet(final String optKey)
    {
        // OTYPE_* - if a new type is added and it uses a boolean field, update this method's javadoc.

        SOCGameOption op = options.get(optKey);
        if (op == null)
            return false;
        return op.getBoolValue();
    }

    /**
     * Within this set, include a boolean or intbool option and make it true.
     * If the option object isn't already in the set, it will be cloned from {@code knownOpts}.
     * @param boKey   Key name for boolean option to set
     * @param knownOpts  Set of Known Options, if needed for adding the option
     * @throws NullPointerException  if {@code boKey} isn't in the set and doesn't exist in {@code knownOpts}
     * @see #isOptionSet(String)
     * @see #setIntOption(String, int, boolean, SOCGameOptionSet)
     * @since 1.1.17
     */
    public void setBoolOption(final String boKey, final SOCGameOptionSet knownOpts)
        throws NullPointerException
    {
        SOCGameOption opt = options.get(boKey);
        if (opt == null)
        {
            opt = knownOpts.getKnownOption(boKey, true);
            opt.setBoolValue(true);
            options.put(boKey, opt);
        }
        else
        {
            opt.setBoolValue(true);
        }
    }

    /**
     * Within this set, include an int or intbool option and set its value.
     * If the option object doesn't exist in this set, it will be cloned from {@code knownOpts}.
     * @param ioKey   Key name for int option to set
     * @param ivalue  Set option to this int value
     * @param bvalue  Set option to this boolean value (ignored if option type not intbool)
     * @param knownOpts  Set of Known Options, if needed for adding the option
     * @throws NullPointerException  if {@code ioKey} isn't in the set and doesn't exist in {@code knownOpts}
     * @see #getOptionIntValue(String)
     * @see #setBoolOption(Map, String)
     * @since 1.1.17
     */
    public void setIntOption
        (final String ioKey, final int ivalue, final boolean bvalue, final SOCGameOptionSet knownOpts)
        throws NullPointerException
    {
        SOCGameOption opt = options.get(ioKey);
        if (opt == null)
        {
            opt = knownOpts.getKnownOption(ioKey, true);
            opt.setIntValue(ivalue);
            opt.setBoolValue(bvalue);
            options.put(ioKey, opt);
        }
        else
        {
            opt.setIntValue(ivalue);
            opt.setBoolValue(bvalue);
        }
    }

    /**
     * What is this integer game option's current value?
     *<P>
     * Does not reference {@link SOCGameOption#getBoolValue()}, only the int value,
     * so this will return a value even if the bool value is false.
     *
     * @param optKey A {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_INT OTYPE_INT},
     *     {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL},
     *     {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM}
     *     or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     * @return Option's current {@link SOCGameOption#getIntValue() intValue},
     *     or 0 if not defined in the set of options;
     *     OTYPE_ENUM's and _ENUMBOOL's choices give an intVal in range 1 to n.
     * @see #isOptionSet(String)
     * @see #getOptionIntValue(String, int, boolean)
     * @see #getOptionStringValue(String)
     * @since 1.1.07
     */
    public int getOptionIntValue(final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return getOptionIntValue(optKey, 0, false);
    }

    /**
     * What is this integer game option's current value?
     *<P>
     * Can optionally reference {@link SOCGameOption#getBoolValue()}, not only the int value.
     *
     * @param optKey A {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_INT OTYPE_INT},
     *     {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL},
     *     {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM}
     *     or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     * @param defValue  Default value to use if <tt>optKey</tt> not defined
     * @param onlyIfBoolSet  Check the option's {@link SOCGameOption#getBoolValue()} too;
     *     if false, return <tt>defValue</tt>.
     *     Do not set this parameter if the type doesn't use a boolean component.
     * @return Option's current {@link SOCGameOption#getIntValue() intValue},
     *     or <tt>defValue</tt> if not defined in the set of options;
     *     OTYPE_ENUM's and _ENUMBOOL's choices give an intVal in range 1 to n.
     * @see #isOptionSet(String)
     * @see #getOptionIntValue(String)
     * @see #getOptionStringValue(String)
     * @since 1.1.14
     */
    public int getOptionIntValue
        (final String optKey, final int defValue, final boolean onlyIfBoolSet)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        SOCGameOption op = options.get(optKey);
        if (op == null)
            return defValue;
        if (onlyIfBoolSet && ! op.getBoolValue())
            return defValue;
        return op.getIntValue();
    }

    /**
     * What is this string game option's current value?
     *
     * @param optKey A {@link SOCGameOption} of type
     *     {@link SOCGameOption#OTYPE_STR OTYPE_STR}
     *     or {@link SOCGameOption#OTYPE_STRHIDE OTYPE_STRHIDE}
     * @return Option's current {@link SOCGameOption#getStringValue() getStringValue}
     *     or null if not defined in this set of options
     * @see #isOptionSet(String)
     * @see #getOptionIntValue(Map, String)
     * @since 1.1.07
     */
    public String getOptionStringValue(final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        SOCGameOption op = options.get(optKey);
        if (op == null)
            return null;
        return op.getStringValue();
    }

    // For Known Options:

    /**
     * Get information about a known option. See {@link #getAllKnownOptions()} for a summary of each known option.
     * Will return the info if known, even if option has {@link #FLAG_INACTIVE_HIDDEN}.
     *<P>
     * Before v2.5.00 this method was {@code SOCGameOption.getOption(key, clone)}.
     *
     * @param key  Option key
     * @param clone  True if a copy of the option is needed; set this true
     *               unless you're sure you won't be changing any fields of
     *               its original object, which is a shared copy in a static namekey->object map.
     * @return information about a known option, or null if none with that key
     * @throws IllegalStateException  if {@code clone} but the object couldn't be cloned; this isn't expected to ever happen
     * @see #addKnownOption(SOCGameOption)
     * @see #setKnownOptionCurrentValue(SOCGameOption)
     * @since 1.1.07
     */
    public SOCGameOption getKnownOption(final String key, final boolean clone)
        throws IllegalStateException
    {
        SOCGameOption op;
        synchronized (options)
        {
            op = options.get(key);
        }
        if (op == null)
            return null;

        if (clone)
        {
            try
            {
                op = (SOCGameOption) op.clone();
            } catch (CloneNotSupportedException ce) {
                // required, but not expected to happen
                throw new IllegalStateException("Clone failed!", ce);
            }
        }

        return op;
    }

    /**
     * Add a new known option (presumably received from a server of newer or older version),
     * or update the option's information.
     * @param onew New option, or a changed version of an option we already know.
     *     If onew.optType == {@link SOCGameOption#OTYPE_UNKNOWN}, will remove from this Known set.
     *     If this option is already known and the old copy has a {@link SOCGameOption#getChangeListener()},
     *     that listener is copied to {@code onew}.
     * @return true if it's new, false if we already had that key and it was updated
     * @see #getKnownOption(String, boolean)
     * @see #getAllKnownOptions()
     * @see #setKnownOptionCurrentValue(SOCGameOption)
     * @since 1.1.07
     */
    public boolean addKnownOption(final SOCGameOption onew)
    {
        final String oKey = onew.key;
        final boolean hadOld;

        synchronized (options)
        {
            final SOCGameOption oldcopy = options.remove(oKey);
            hadOld = (oldcopy != null);

            if (onew.optType != SOCGameOption.OTYPE_UNKNOWN)
            {
                if (hadOld)
                {
                    final SOCGameOption.ChangeListener cl = oldcopy.getChangeListener();
                    if (cl != null)
                        onew.addChangeListener(cl);
                }

                options.put(oKey, onew);
            }
        }

        return ! hadOld;
    }

    // Comparison and synchronization, Known Options:

    /**
     * In a set of Known Options, activate an "inactive" known option:
     * Drop its {@link SOCGameOption#FLAG_INACTIVE_HIDDEN} flag
     * and add {@link SOCGameOption#FLAG_ACTIVATED}. Does nothing if already activated.
     * See {@link SOCGameOption} class javadoc for more about Inactive Options.
     *<P>
     * Since {@link SOCGameOption#optFlags} field is {@code final}, copies to a new option object with updated flags,
     * replacing the old one in the set of known options.
     *<P>
     * To get the list of currently activated options compatible with a certain client version,
     * call {@link #optionsWithFlag(int, int) knownOpts.optionsWithFlag(FLAG_ACTIVATED, cliVersion)}.
     *<P>
     * At the server, activate needed options before any clients connect.
     * Do so by editing/overriding {@link SOCServer#serverUp()} to call this method,
     * or setting property {@link SOCServer#PROP_JSETTLERS_GAMEOPTS_ACTIVATE}.
     *
     * @param optKey  Known game option's alphanumeric keyname
     * @throws IllegalArgumentException if {@code optKey} isn't a known game option, or if that option
     *     has neither {@link SOCGameOption#FLAG_INACTIVE_HIDDEN} nor {@link SOCGameOption#FLAG_ACTIVATED}
     */
    public void activate(final String optKey)
        throws IllegalArgumentException
    {
        synchronized (options)
        {
            final SOCGameOption orig = options.get(optKey);
            if (orig == null)
                throw new IllegalArgumentException("unknown: " + optKey);

            if (! orig.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN))
            {
                if (orig.hasFlag(SOCGameOption.FLAG_ACTIVATED))
                    return;

                throw new IllegalArgumentException("not inactive: " + optKey);
            }

            options.put(optKey, new SOCGameOption
                ((orig.optFlags | SOCGameOption.FLAG_ACTIVATED) & ~SOCGameOption.FLAG_INACTIVE_HIDDEN, orig));
        }
    }

    /**
     * In a set of Known Options, set the current value(s) of an option based on the current value(s) of
     * another object {@code ocurr} with the same {@link SOCVersionedItem#key key}.
     * If there is no known option with oCurr.{@link SOCVersionedItem#key key}, it is ignored and nothing is set.
     * @param ocurr Option with the requested current value.
     *     {@code ocurr}'s value field contents are copied to the known option's values,
     *     the {@code ocurr} reference won't be added to the known option set.
     * @throws  IllegalArgumentException if string value is not permitted; note that
     *     int values outside of range are silently clipped, and will not throw this exception.
     * @see #getKnownOption(String, boolean)
     * @since 1.1.07
     */
    public void setKnownOptionCurrentValue(SOCGameOption ocurr)
        throws IllegalArgumentException
    {
        final String oKey = ocurr.key;

        synchronized (options)
        {
            final SOCGameOption oKnown = options.get(oKey);

            if (oKnown == null)
                return;

            switch (oKnown.optType)  // OTYPE_*
            {
            case SOCGameOption.OTYPE_BOOL:
                oKnown.setBoolValue(ocurr.getBoolValue());
                break;

            case SOCGameOption.OTYPE_INT:
            case SOCGameOption.OTYPE_ENUM:
                oKnown.setIntValue(ocurr.getIntValue());
                break;

            case SOCGameOption.OTYPE_INTBOOL:
            case SOCGameOption.OTYPE_ENUMBOOL:
                oKnown.setBoolValue(ocurr.getBoolValue());
                oKnown.setIntValue(ocurr.getIntValue());
                break;

            case SOCGameOption.OTYPE_STR:
            case SOCGameOption.OTYPE_STRHIDE:
                oKnown.setStringValue(ocurr.getStringValue());
                break;
            }
        }
    }

    /**
     * Compare a set of options against the specified version.
     * Make a list of all which are new or changed since that version.
     *<P>
     * This method has 2 modes, because it's called for 2 different purposes:
     *<UL>
     * <LI> sync client-server Known Option set info, in general: <tt>checkValues</tt> == false
     * <LI> check if client can create game with a specific set of option values: <tt>checkValues</tt> == true,
     *     call on game's proposed Set of game opts instead of Known Opts.
     *    <BR>
     *     Before calling this method, server should call
     *     {@link #adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)}
     *     to help validate option values.
     *</UL>
     * See <tt>checkValues</tt> for method's behavior in each mode.
     *<P>
     * <B>Game option names:</B><br>
     * When running this at the client (<tt>vers</tt> is the older remote server's version),
     * some of the returned too-new options have long names that can't be sent to a v1.x.xx
     * server (<tt>vers</tt> &lt; {@link SOCGameOption#VERSION_FOR_LONGER_OPTNAMES}).
     * You must check for this and remove them before sending them to the remote server.
     * Game option names sent to 1.x.xx servers must be 3 characters or less, alphanumeric, no underscores ('_').
     *<P>
     * When running at the server, we will never send an option whose name is invalid to v1.x.xx clients,
     * because the SOCGameOption constructors enforce <tt>minVers >= 2000</tt> when the name is longer than 3
     * characters or contains '_'.
     *
     * @param vers  Version to compare known options against
     * @param checkValues  Which mode: Check options' current values and {@link SOCGameOption#minVersion},
     *     not their {@link SOCGameOption#lastModVersion}?  An option's minimum version
     *     can increase based on its value; see {@link SOCGameOption#getMinVersion(Map)}.
     *     <P>
     *     If false, returns list of any game options to send to older server or client {@code vers}.
     *     Ignores any option with {@link SOCGameOption#FLAG_INACTIVE_HIDDEN}.
     *     Adds all options with {@link SOCGameOption#FLAG_ACTIVATED}
     *     having {@link SOCVersionedItem#minVersion minVersion} &lt;= {@code vers},
     *     ignoring their {@link SOCVersionedItem#lastModVersion lastModVersion}
     *     in case activation is the opt's only recent change.
     *     <BR>
     *     If {@code checkValues} and {@code trimEnums} both false, assumes is a client-side call
     *     and also adds any opts with {@link SOCGameOption#FLAG_3RD_PARTY} to the returned list.
     *     <P>
     *     If true, any returned items are in this Set but too new for client {@code vers}:
     *     Game creation should be rejected.
     *     Does not check {@link SOCGameOption#FLAG_INACTIVE_HIDDEN} in this mode; use
     *     {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)} for that check.
     * @param trimEnums  For enum-type options where minVersion changes based on current value,
     *     should we remove too-new values from the returned option info?
     *     This lets us send only the permitted values to an older client.
     *     Also trims int-type options' max value where needed (example: {@code "PL"}).
     * @return List of the newer (added or changed) {@link SOCGameOption}s, or null
     *     if all are known and unchanged since <tt>vers</tt>.
     *     <BR>
     *     <B>Note:</B> May include options with {@link SOCGameOption#minVersion} &gt; {@code vers}
     *     if client has asked about them by name.
     * @see #optionsForVersion(int)
     * @see #optionsTrimmedForSupport(SOCFeatureSet)
     * @since 1.1.07
     */
    public List<SOCGameOption> optionsNewerThanVersion
        (final int vers, final boolean checkValues, final boolean trimEnums)
    {
        return implOptionsVersionCheck(vers, false, checkValues, trimEnums);
    }

    /**
     * In a set of Known Options, get all options valid at version {@code vers}.
     * If necessary, trims enum value ranges or int value ranges if range was smaller at {@code vers},
     * like {@link #optionsNewerThanVersion(int, boolean, boolean)} does.
     *<P>
     * If {@code vers} from a client is newer than this version of SOCGameOption, will return all options known at this
     * version, which may not include all of the newer version's options.  Client game-option negotiation handles this
     * by having the newer client send all its new (added or changed) option keynames to the older server to allow,
     * adjust, or reject.
     *<P>
     * Will omit any option that has {@link SOCGameOption#FLAG_INACTIVE_HIDDEN}.
     *
     * @param vers  Version to compare options against
     * @return  List of all {@link SOCGameOption}s valid at version {@code vers}, or {@code null} if none.
     * @see #optionsNewerThanVersion(int, boolean, boolean)
     * @see #optionsTrimmedForSupport(SOCFeatureSet)
     * @since 2.0.00
     */
    public List<SOCGameOption> optionsForVersion(final int vers)
    {
        return implOptionsVersionCheck(vers, true, false, true);
    }

    /**
     * In a set of Known Options, get all options added or changed since version {@code vers}, or all options valid
     * at {@code vers}, to implement {@link #optionsNewerThanVersion(int, boolean, boolean)}
     * and {@link #optionsForVersion(int)}.
     * @param vers  Version to compare options against
     * @param getAllForVersion  True to get all valid options ({@code optionsForVersion} mode),
     *     false for newer added or changed options only ({@code optionsNewerThanVersion} modes).
     *     If true and {@code vers} is newer than this version of SOCGameOption, will return
     *     all options known at this version.
     * @param checkValues  If not {@code getAllForVersion}, which mode to run in:
     *     Check options' current values and {@link SOCGameOption#minVersion},
     *     not their {@link SOCGameOption#lastModVersion}?
     *     An option's minimum version can increase based on its value; see {@link SOCGameOption#getMinVersion(Map)}.
     *     <P>
     *     If false, returns list of any game options to send to older server or client {@code vers}.
     *     Ignores any option with {@link SOCGameOption#FLAG_INACTIVE_HIDDEN}.
     *     Adds all options with {@link SOCGameOption#FLAG_ACTIVATED}
     *     having {@link SOCVersionedItem#minVersion minVersion} &lt;= {@code vers},
     *     ignoring their {@link SOCVersionedItem#lastModVersion lastModVersion}
     *     in case activation is the opt's only recent change.
     *     <BR>
     *     If {@code checkValues}, {@code getAllForVersion}, and {@code trimEnums} all false, assumes is
     *     a client-side call and also adds any opts with {@link SOCGameOption#FLAG_3RD_PARTY} to the returned list.
     *     <P>
     *     If true, any returned items are from this Set but too new for client {@code vers}:
     *     Game creation should be rejected.
     *     Does not check {@link SOCGameOption#FLAG_INACTIVE_HIDDEN} in this mode; use
     *     {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)} for that check.
     * @param trimEnums  For enum-type options where minVersion changes based on current value,
     *     should we remove too-new values from the returned option info?
     *     This lets us send only the permitted values to an older client.
     *     Also trims int-type options' max value where needed (example: {@code "PL"}).
     * @return List of the requested {@link SOCGameOption}s, or null if none match the conditions, at {@code vers};
     *     see {@code optionsNewerThanVersion} and {@code optionsForVersion} for return details.
     *     <BR>
     *     <B>Note:</B> If not {@code getAllForVersion}, may include options with
     *     {@link SOCGameOption#minVersion} &gt; {@code vers} if client has asked about them by name.
     * @throws IllegalArgumentException  if {@code getAllForVersion && checkValues}: Cannot combine these modes
     * @since 2.0.00
     */
    private List<SOCGameOption> implOptionsVersionCheck
        (final int vers, final boolean getAllForVersion, final boolean checkValues, final boolean trimEnums)
        throws IllegalArgumentException
    {
        /** collect newer options here, or all options if getAllForVersion */
        List<SOCGameOption> uopt
            = SOCVersionedItem.implItemsVersionCheck(vers, getAllForVersion, checkValues, options);
                // throws IllegalArgumentException if (getAllForVersion && checkValues)

        if (! checkValues)
        {
            if (uopt != null)
            {
                ListIterator<SOCGameOption> li = uopt.listIterator();
                while (li.hasNext())
                {
                    SOCGameOption opt = li.next();
                    if (opt.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN))
                        li.remove();
                }
            }

            // add any activated ones, even if unchanged since vers
            {
                SOCGameOptionSet actives = optionsWithFlag(SOCGameOption.FLAG_ACTIVATED, vers);
                if (actives != null)
                {
                    if (uopt != null)
                        for (SOCGameOption opt : uopt)
                            actives.remove(opt.key);  // remove if happens to already be in list, to avoid double add
                    else
                        uopt = new ArrayList<>();

                    for (SOCGameOption aopt : actives)
                        uopt.add(aopt);
                }
            }

            // if client-side, also add any with FLAG_3RD_PARTY
            if (! (getAllForVersion || trimEnums))
            {
                for (SOCGameOption opt : options.values())
                {
                    if ((0 == (opt.optFlags & SOCGameOption.FLAG_3RD_PARTY))
                        || (0 != (opt.optFlags & SOCGameOption.FLAG_INACTIVE_HIDDEN)))
                        continue;

                    if (uopt != null)
                    {
                        if (uopt.contains(opt))
                            continue;
                    } else {
                        uopt = new ArrayList<>();
                    }

                    uopt.add(opt);
                }
            }

            if ((uopt != null) && uopt.isEmpty())
                uopt = null;
        }

        if ((uopt != null) && trimEnums)
        {
            ListIterator<SOCGameOption> li = uopt.listIterator();
            while (li.hasNext())
            {
                boolean changed = false;
                SOCGameOption opt = li.next();

                if ((opt.lastModVersion > vers)   // opt has been modified since vers
                    && (opt.minVersion <= vers))  // vers is new enough to use this opt
                {
                    if (opt.enumVals != null)
                    {
                        // Possibly trim enum values. (OTYPE_ENUM, OTYPE_ENUMBOOL)
                        // OTYPE_* - Add here in comment if enum-valued option type
                        final int ev = SOCGameOption.getMaxEnumValueForVersion(opt.key, vers);
                        if (ev < opt.enumVals.length)
                        {
                            opt = SOCGameOption.trimEnumForVersion(opt, vers);
                            changed = true;
                        }
                    } else if (opt.maxIntValue != opt.minIntValue)
                    {
                        // Possibly trim max int value. (OTYPE_INT, OTYPE_INTBOOL)
                        // OTYPE_* - Add here in comment if int-valued option type
                        final int iv = SOCGameOption.getMaxIntValueForVersion(opt.key, vers);
                        if ((iv != opt.maxIntValue) && (iv != Integer.MAX_VALUE))
                        {
                            opt = new SOCGameOption(opt, iv);
                            changed = true;
                        }
                    }

                    if (changed)
                        li.set(opt);
                }
            }
        }

        return uopt;
    }

    /**
     * Compare this set of options with known-good values, and optionally apply options from
     * the set's scenario (game option <tt>"SC"</tt>) if present.
     *<P>
     * If any values are above/below maximum/minimum, clip to the max/min value given in {@code knownOpts}.
     * If any are unknown or inactive, return a description. Will still check (and clip) the known ones.
     * If any options are default, and unset/blank, and
     * their {@link SOCGameOption#FLAG_DROP_IF_UNUSED} flag is set, remove them from this set.
     * For {@link SOCGameOption#OTYPE_INTBOOL} and {@link SOCGameOption#OTYPE_ENUMBOOL}, both the integer and
     * boolean values are checked against defaults.
     *<P>
     * If <tt>doServerPreadjust</tt> is true, then the server might also change some
     * option values before creating the game, for overall consistency of the set of options.
     * This is a server-side equivalent to the client-side {@link SOCGameOption.ChangeListener}s.
     * For example, if <tt>"PL"</tt> (number of players) > 4, but <tt>"PLB"</tt> (use 6-player board)
     * is not set, <tt>doServerPreadjust</tt> wil set the <tt>"PLB"</tt> option.
     * If {@code "VP=t..."} isn't in the set, will copy server's default VP (if any) from {@code knownOpts},
     * or if set has a scenario, scenario's VP if larger than default and not using bool option <tt>"_VP_ALL"</tt>.
     * {@code doServerPreadjust} will also remove any game-internal options the client has sent.
     *<P>
     * Before any other adjustments when <tt>doServerPreadjust</tt>, will check for
     * the game scenario option <tt>"SC"</tt>. If that option is set, call
     * {@link SOCScenario#getScenario(String)}; the scenario name must be known.
     * Then, add that scenario's {@link SOCScenario#scOpts .scOpts} into this set.
     * Scenario option values always overwrite those already in the set, except for <tt>"VP"</tt>
     * whose current value is kept. If VP not in set but server has a default VP larger than scenario
     * (or bool option <tt>"_VP_ALL"</tt> is set) that's used instead.
     * For convenience, {@link SOCServer#checkScenarioOpts(Map, boolean, String)} may warn about such overwrites.
     *<P>
     * Client-side gameopt code also assumes all scenarios use the sea board,
     * and sets game option <tt>"SBL"</tt> when a scenario is chosen by the user.
     *<P>
     * Before v2.5.00 this method was {@code SOCGameOption.adjustOptionsToKnown(newOpts, knownOpts, boolean)}.
     *
     * @param knownOpts Set of known {@link SOCGameOption}s to check against; not null.
     *     Caller can use {@link #getAllKnownOptions()} if they don't already have such a set.
     * @param doServerPreadjust  If true, we're calling from the server before creating a game;
     *     pre-adjust any values for consistency.
     *     This is a server-side equivalent to the client-side {@link SOCGameOption.ChangeListener}s.
     *     (Added in 1.1.13)
     * @param limitedCliFeats For {@link doServerPreadjust}, client's set of features if limited compared to
     *     the standard client; null if client doesn't have limited feats.
     *     See {@link soc.server.SOCClientData#hasLimitedFeats} for details.
     * @return <tt>null</tt> if all are known; or, a map of game options to human-readable problem descriptions if:
     *     <UL>
     *       <LI> any option in this set not a Known Option
     *            or is inactive (has {@link SOCGameOption#FLAG_INACTIVE_HIDDEN})
     *       <LI> or an opt's type differs from that in knownOpts
     *       <LI> or an opt's {@link SOCGameOption#lastModVersion} differs from in knownOpts
     *       <LI> or an opt requires a {@link SOCGameOption#getClientFeature()} which the client doesn't have
     *            (checked only if {@code limitedCliFeats} != null and {@code doServerPreadjust})
     *       <LI> set has option {@code "SC"} but its scenario keyname isn't known
     *            by {@link SOCScenario#getScenario(String)}:
     *            {@code "SC"}'s text value will be "unknown scenario " + the scenario keyname.
     *     </UL>
     *     <P>
     *      If you need to flatten the returned Map to something stringlike, call
     *      {@link DataUtils#mapIntoStringBuilder(Map, StringBuilder, String, String)}.
     * @throws IllegalArgumentException if {@code knownOpts} is null
     * @since 1.1.07
     */
    public Map<String, String> adjustOptionsToKnown
        (final SOCGameOptionSet knownOpts, final boolean doServerPreadjust, final SOCFeatureSet limitedCliFeats)
        throws IllegalArgumentException
    {
        if (knownOpts == null)
            throw new IllegalArgumentException("null");

        String unknownScenario = null;

        if (doServerPreadjust)
        {
            // Remove any game-internal options, before adding scenario opts
            {
                Iterator<String> ki = options.keySet().iterator();  // keySet lets us remove without disrupting iterator
                while (ki.hasNext())
                {
                    SOCGameOption op = options.get(ki.next());
                    if (0 != (op.optFlags & SOCGameOption.FLAG_INTERNAL_GAME_PROPERTY))
                        ki.remove();
                }
            }

            // Use server default for "VP" unless options has "VP" with boolean part true.
            // If "VP" not known at server, client shouldn't have sent it; code later in method will handle that
            int wantedVP = 0;
            boolean using_VP_ALL = false;  // unless true, make sure _VP_ALL isn't in adjusted set of opts
            SOCGameOption opt = options.get("VP");
            {
                final SOCGameOption knownOptVP = knownOpts.get("VP");

                if (opt == null)
                {
                    if ((knownOptVP != null) && knownOptVP.getBoolValue())
                        wantedVP = knownOptVP.getIntValue();
                }
                else if (opt.getBoolValue())
                {
                    wantedVP = opt.getIntValue();
                }
                else if (knownOptVP != null)
                {
                    if (knownOptVP.getBoolValue())
                        wantedVP = knownOptVP.getIntValue();
                    else
                        options.remove("VP");
                }
            }

            // Apply scenario options, if any
            opt = options.get("SC");
            if (opt != null)
            {
                final String scKey = opt.getStringValue();
                if (scKey.length() > 0)
                {
                    SOCScenario sc = SOCScenario.getScenario(scKey);
                    if (sc == null)
                    {
                        unknownScenario = scKey;
                    } else {
                        // include this scenario's opts,
                        // overwriting any values for those
                        // opts if already in options.
                        // keep scen VP only if options don't specify VP
                        // and scen's VP is greater than default
                        // and "_VP_ALL" isn't set in server options

                        final Map<String, SOCGameOption> scOpts = SOCGameOption.parseOptionsToMap(sc.scOpts, knownOpts);
                        final boolean with_VP_ALL = knownOpts.isOptionSet("_VP_ALL");
                        using_VP_ALL = with_VP_ALL && knownOpts.isOptionSet("VP") && ! isOptionSet("VP");
                        opt = options.get("VP");
                        final SOCGameOption scOptVP = scOpts.get("VP");
                        if (scOptVP != null)
                        {
                            if (((opt == null) || ! opt.getBoolValue())
                                && (scOptVP.getIntValue() > wantedVP)
                                && scOptVP.getBoolValue()
                                && ((wantedVP == 0) || ! with_VP_ALL))
                            {
                                wantedVP = 0;
                            } else {
                                scOpts.remove("VP");
                            }
                        }
                        options.putAll(scOpts);
                    }
                }

                // Client-side gameopt code also assumes all scenarios use
                // the sea board, and sets game option "SBL" when a scenario
                // is chosen by the user.
            }

            if (wantedVP > 0)
            {
                SOCGameOption optVP = options.get("VP");
                if (optVP == null)
                {
                    optVP = knownOpts.getKnownOption("VP", true);
                    if (optVP != null)
                        options.put("VP", optVP);
                }

                if (optVP != null)
                {
                    optVP.setBoolValue(true);
                    optVP.setIntValue(wantedVP);
                }
            }

            // If game has scenario, server's using _VP_ALL, and client didn't ask to override default/scenario VP,
            // show that by setting _VP_ALL in the new game's options
            if (! using_VP_ALL)
                options.remove("_VP_ALL");
            else if (! options.containsKey("_VP_ALL"))
                add(knownOpts.getKnownOption("_VP_ALL", true));

            // NEW_OPTION: If you created a ChangeListener, you should probably add similar code
            //    here. Set or change options if it makes sense; if a user has deliberately
            //    set a boolean option, think carefully before un-setting it and surprising them.

            // Set PLB if PL>4 or PLP
            opt = options.get("PL");
            SOCGameOption optPLP = options.get("PLP");
            if (((opt != null) && (opt.getIntValue() > 4))
                || ((optPLP != null) && optPLP.getBoolValue()))
                setBoolOption("PLB", knownOpts);

        }  // if(doServerPreadjust)

        // OTYPE_* - adj javadoc above (re dropIfUnused) if a string-type or bool-type is added.

        Map<String, String> optProblems = new TreeMap<>();

        boolean allKnown;

        if (unknownScenario != null)
        {
            allKnown = false;
            optProblems.put("SC", "unknown scenario " + unknownScenario);
        } else {
            allKnown = true;  // might be set false in loop below
        }

        // use Iterator in loop, so we can remove from the hash if needed
        for (Iterator<Map.Entry<String, SOCGameOption>> ikv = options.entrySet().iterator();
             ikv.hasNext(); )
        {
            Map.Entry<String, SOCGameOption> okv = ikv.next();

            SOCGameOption op;
            try {
                op = okv.getValue();
            }
            catch (ClassCastException ce)
            {
                throw new IllegalArgumentException("wrong class, expected gameoption");
            }

            SOCGameOption knownOp = knownOpts.get(op.key);
            if (knownOp == null)
            {
                allKnown = false;
                optProblems.put(op.key, "unknown");
            }
            else if (knownOp.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN))
            {
                allKnown = false;
                optProblems.put(op.key, "inactive");
            }
            else if (knownOp.optType != op.optType)
            {
                allKnown = false;
                optProblems.put(op.key, "optType mismatch (" + knownOp.optType + " != " + op.optType + ")");
            } else {
                // Clip int values, check default values, check dropIfUnused

                if (knownOp.lastModVersion != op.lastModVersion)
                {
                    allKnown = false;
                    optProblems.put
                        (op.key, "lastModVersion mismatch (" + knownOp.lastModVersion + " != " + op.lastModVersion + ")");
                }

                switch (op.optType)  // OTYPE_*
                {
                case SOCGameOption.OTYPE_INT:
                case SOCGameOption.OTYPE_INTBOOL:
                case SOCGameOption.OTYPE_ENUM:
                case SOCGameOption.OTYPE_ENUMBOOL:
                    {
                        int iv = op.getIntValue();
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

                        if (knownOp.hasFlag(FLAG_DROP_IF_UNUSED)
                            && (iv == knownOp.defaultIntValue))
                        {
                            // ignore boolValue unless also boolean-type: OTYPE_INTBOOL and OTYPE_ENUMBOOL.
                            if ((op.optType == SOCGameOption.OTYPE_INT) || (op.optType == SOCGameOption.OTYPE_ENUM)
                                || ! op.getBoolValue())
                                ikv.remove();
                        }
                    }
                    break;

                case SOCGameOption.OTYPE_BOOL:
                    if (knownOp.hasFlag(FLAG_DROP_IF_UNUSED) && ! op.getBoolValue())
                        ikv.remove();
                    break;

                case SOCGameOption.OTYPE_STR:
                case SOCGameOption.OTYPE_STRHIDE:
                    if (knownOp.hasFlag(FLAG_DROP_IF_UNUSED))
                    {
                        String sval = op.getStringValue();
                        if ((sval == null) || (sval.length() == 0))
                            ikv.remove();
                    }
                    break;

                // no default: all types should be handled above.

                }
            }
        }

        if (doServerPreadjust && allKnown && (limitedCliFeats != null))
        {
            // See also SOCServerMessageHandler.handleGAMEOPTIONGETINFOS which has
            // very similar code for limited client feats.

            final Map<String, SOCGameOption> unsupportedOpts = optionsNotSupported(limitedCliFeats);

            if (unsupportedOpts != null)
            {
                allKnown = false;
                for (String okey : unsupportedOpts.keySet())
                    optProblems.put(okey, "requires missing feature(s)");
            } else {
                final Map<String, SOCGameOption> trimmedOpts = optionsTrimmedForSupport(limitedCliFeats);

                if (trimmedOpts != null)
                    for (SOCGameOption opt : trimmedOpts.values())
                        options.put(opt.key, opt);
            }
        }

        if (allKnown)
            return null;
        else
            return optProblems;
    }

    /**
     * In a set of options or Known Options, do any require client features
     * not supported by a limited client's {@link SOCFeatureSet}?
     * Checks each option having a {@link #getClientFeature()}.
     *<P>
     * Doesn't check integer value of features like {@code sc} ({@link SOCFeatureSet#getValue(String, int)}):
     * Use {@link SOCGame#checkClientFeatures(SOCFeatureSet, boolean)} for that.
     *
     * @param cliFeats  Client's limited subset of optional features,
     *     from {@link soc.server.SOCClientData#feats}, or {@code null} or empty set if no features
     * @return Map of known options not supported by {@code cliFeats},
     *     or {@code null} if all known options are supported.
     *     Each map key is its option value's {@link SOCVersionedItem#key key}.
     * @see #optionsTrimmedForSupport(SOCFeatureSet)
     * @since 2.4.00
     */
    public Map<String, SOCGameOption> optionsNotSupported(final SOCFeatureSet cliFeats)
    {
        Map<String, SOCGameOption> ret = null;

        for (SOCGameOption opt : options.values())
        {
            final String cliFeat = opt.getClientFeature();
            if (cliFeat == null)
                continue;
            if ((cliFeats != null) && cliFeats.isActive(cliFeat))
                continue;

            if (ret == null)
                ret = new HashMap<>();
            ret.put(opt.key, opt);
        }

        return ret;
    }

    /**
     * In a set of options or Known Options, do any require changes for a limited client's {@link SOCFeatureSet}?
     * For example, clients without {@link SOCFeatureSet#CLIENT_6_PLAYERS} limit "max players" to 4.
     *<P>
     * Assumes client is new enough that its version wouldn't also cause trimming of those same options' values
     * by {@link #optionsNewerThanVersion(int, boolean, boolean)} or {@link #optionsForVersion(int)}.
     *
     * @param cliFeats  Client's limited subset of optional features,
     *     from {@link soc.server.SOCClientData#feats}, or {@code null} or empty set if no features
     * @return Map of trimmed known options, or {@code null} if no trimming was needed.
     *     Each map key is its option value's {@link SOCVersionedItem#key key}.
     * @see #optionsNotSupported(SOCFeatureSet)
     * @see SOCGameOption#getMaxIntValueForVersion(String, int)
     * @since 2.4.00
     */
    public Map<String, SOCGameOption> optionsTrimmedForSupport(final SOCFeatureSet cliFeats)
    {
        if ((cliFeats != null) && cliFeats.isActive(SOCFeatureSet.CLIENT_6_PLAYERS))
            return null;

        SOCGameOption pl = getKnownOption("PL", false);
        if (pl == null)
            return null;  // shouldn't happen, PL is a known option

        Map<String, SOCGameOption> ret = new HashMap<>();
        ret.put("PL", new SOCGameOption(pl, SOCGame.MAXPLAYERS_STANDARD));
        return ret;
    }

    /**
     * Find all opts in this set having the specified flag(s) and optional minimum version,
     * or all Known Options when called on a server or client's set of Known Options.
     *<P>
     * Some uses:
     *<UL>
     * <LI> {@link SOCGameOption#FLAG_3RD_PARTY}:
     *   Find any Third-party Options defined at client, to ask server if it knows them too.
     *   Client calls this as part of connect to server, ignoring minVersion so all are asked about.
     * <LI> {@link SOCGameOption#FLAG_ACTIVATED}:
     *   Find any Activated Options compatible with client version.
     *   Server calls this as part of client connect, with {@code minVers} = client version.
     *</UL>
     * Ignores any option with {@link SOCGameOption#FLAG_INACTIVE_HIDDEN} unless that's part of {@code flagMask}.
     *<P>
     * If calling at server and the connecting client has limited features, assumes has already
     * called {@link #optionsNotSupported(SOCFeatureSet)} and {@link #optionsTrimmedForSupport(SOCFeatureSet)}.
     * So this method filters only by minVersion, not by feature requirement or any other field.
     *
     * @param flagMask  Flag(s) to check for; {@link #hasFlag(int)} return value is the filter
     * @param minVers  Minimum compatible version to look for, same format as {@link Version#versionNumber()},
     *     or 0 to ignore {@link SOCVersionedItem#minVersion opt.minVersion}
     * @return  Map of found options compatible with {@code minVers}, or {@code null} if none.
     *     Each map key is its option value's {@link SOCVersionedItem#key key}.
     * @see SOCGameOption#activate()
     */
    public SOCGameOptionSet optionsWithFlag(final int flagMask, final int minVers)
    {
        Map<String, SOCGameOption> ret = null;
        final boolean ignoreInactives = (0 == (flagMask & SOCGameOption.FLAG_INACTIVE_HIDDEN));

        for (final SOCGameOption opt : options.values())
        {
            if ((minVers != 0) && (opt.minVersion > minVers))
                continue;

            if (ignoreInactives && (0 != (opt.optFlags & SOCGameOption.FLAG_INACTIVE_HIDDEN)))
                continue;

            if (opt.hasFlag(flagMask))
            {
                if (ret == null)
                    ret = new HashMap<>();
                ret.put(opt.key, opt);
            }
        }

        return (ret != null) ? new SOCGameOptionSet(ret) : null;
    }

    /**
     * Human-readable contents of the Set: Returns its game options {@link Map#toString()}.
     */
    @Override
    public String toString() { return options.toString(); }

}
