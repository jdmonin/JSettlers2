/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009,2011-2020 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.StringTokenizer;

import soc.message.SOCMessage;
import soc.server.savegame.SavedGameModel;  // for javadocs only
import soc.util.SOCFeatureSet;

/**
 * Game-specific options, configurable at game creation.
 * This class has two purposes:
 *<UL>
 * <LI> Per-game values of options
 * <LI> Static dictionary of known options;
 *      see {@link #initAllOptions()} for the current list.
 *</UL>
 * To get the list of known options, use {@link #getAllKnownOptions()}.
 *<P>
 * For information about adding or changing game options in a
 * later version of JSettlers, please see {@link #initAllOptions()}.
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
 * {@link SOCGame#getGameOptionIntValue(Map, String, int, boolean)}, etc.
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
 * {@link #adjustOptionsToKnown(Map, Map, boolean) adjustOptionsToKnown(opts, null, true)}.
 *<P>
 * For the same reason, option string values (and enum choices) must not contain
 * certain characters or span more than 1 line; this is checked by calling
 * {@link SOCMessage#isSingleLineAndSafe(String)} within constructors and setters.
 *
 *<H3>Known Options and interaction</H3>
 *
 * The "known options" are initialized via {@link #initAllOptions()}.  See that
 * method's description for more details on adding an option.
 * If a new option changes previously expected behavior of the game, it should default to
 * the old behavior; its default value on your server can be changed at runtime.
 *<P>
 * Since 1.1.13, when the user changes options while creating a new game, related
 * options can be changed on-screen for consistency; see {@link SOCGameOption.ChangeListener} for details.
 * If you create a ChangeListener, consider adding equivalent code to
 * {@link #adjustOptionsToKnown(Map, Map, boolean)} for the server side.
 *
 *<H3>Sea Board Scenarios</H3>
 *
 * Game scenarios were introduced with the large sea board in 2.0.
 * Game options are used to indicate which {@link SOCGameEvent}s, {@link SOCPlayerEvent}s,
 * and rules are possible in the current game.
 * These all start with <tt>"_SC_"</tt> and have a static key string;
 * an example is {@link #K_SC_SANY} for scenario game option <tt>"_SC_SANY"</tt>.
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
 * and asks for any changes to options if their versions differ.
 * Also if connecting client has limited features, server sends all
 * unsupported game options as unknowns by checking each option's {@link #getClientFeature()}.
 *
 *<H3>I18N</H3>
 *
 * Game option descriptions are also stored as {@code gameopt.*} in
 * {@code server/strings/toClient_*.properties} to be sent to clients if needed
 * during version negotiation. At the client, option's text can be localized with {@link #setDesc(String)}.
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
     * The removal is done in {@link #adjustOptionsToKnown(Map, Map, boolean)}.
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

    // -- Game option keynames for scenario flags --
    // Not all scenario keynames have scenario events, some are just properties of the game.

    /**
     * Scenario key <tt>_SC_SANY</tt> for {@link SOCPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}.
     * @since 2.0.00
     */
    public static final String K_SC_SANY = "_SC_SANY";

    /**
     * Scenario key <tt>_SC_SEAC</tt> for {@link SOCPlayerEvent#SVP_SETTLED_EACH_NEW_LANDAREA}.
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
     * Set of "known options".
     * allOptions must never be null, because other places assume it is filled.
     */
    private static Map<String, SOCGameOption> allOptions = initAllOptions();

    /**
     * List of options to refresh on-screen after a change during game creation;
     * filled by {@link #refreshDisplay()}.  Not thread-safe.
     * @see ChangeListener
     * @since 1.1.13
     */
    private static List<SOCGameOption> refreshList;

    /**
     * Create a set of the known options.
     * This method creates and returns a new map, but does not set the static {@link #allOptions} field.
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
     *</UL>
     *  * Grouping: PLB, PLP are 3 characters, not 2, and the first 2 characters match an
     *    existing option. So in NewGameOptionsFrame, they appear on the lines following
     *    the PL option in client version 1.1.13 and above.
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
     * <h3>If you want to add a game option:</h3>
     *<UL>
     *<LI> Choose an unused 2-character key name: for example, "PL" for "max players".
     *   All in-game code uses these key strings to query and change
     *   game option settings; only a very few places use SOCGameOption
     *   objects, and you won't need to adjust those places.
     *   The list of already-used key names is here within initAllOptions.
     *   <P>
     *   If your option supports a {@link SOCScenario}, its name should
     *   start with "_SC_" and it should have a constant name field here
     *   (like {@link #K_SC_3IP}) with a short descriptive javadoc.
     *   Link in javadoc to the SOCScenario field and add it to the list above.
     *   Because name starts with "_SC_", constructor will automatically call
     *   {@link #setClientFeature(String) setClientFeature}({@link SOCFeatureSet#CLIENT_SEA_BOARD}).
     *   If you need a different client feature instead, or none, call that setter afterwards.
     *<LI> Decide which {@link #optType option type} your option will be
     *   (boolean, enumerated, int+bool, etc.), and its default value.
     *   Typically the default will let the game behave as it did before
     *   the option existed (for example, the "max players" default is 4).
     *   Its default value on your own server can be changed at runtime.
     *<LI> Decide if all client versions can use your option.  Typically, if the option
     *   requires server changes but not any client changes, all clients can use it.
     *   (For example, "N7" for "roll no 7s early in the game" works with any client
     *   because dice rolls are done at the server.)
     *<LI> Create the option by calling opt.put here in initAllOptions.
     *   Use the current version for the "last modified" field.
     *<LI> Add the new option's description to the {@code gameopt.*} section of
     *   {@code server/strings/toClient_*.properties} to be sent to clients if needed.
     *<LI> If only <em>some values</em> of the option will require client changes,
     *   also update {@link #getMinVersion(Map)}.  (For example, if "PL"'s value is 5 or 6,
     *   a new client would be needed to display that many players at once, but 2 - 4
     *   can use any client version.) <BR>
     *   If this is the case and your option type
     *   is {@link #OTYPE_ENUM} or {@link #OTYPE_ENUMBOOL}, also update
     *   {@link #getMaxEnumValueForVersion(String, int)}.
     *   Otherwise, update {@link #getMaxIntValueForVersion(String, int)}.
     *<LI> If the new option can be used by old clients by changing the values of
     *   <em>other</em> related options when game options are sent to those versions,
     *   add code to {@link #getMinVersion(Map)}. <BR>
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
     *   {@link soc.util.SOCRobotParameters#copyIfOptionChanged(Map)}
     *   which ignore most, but not all, game options.
     *<LI> If the new option adds new game/player/board fields or piece types which aren't
     *   currently in {@link SavedGameModel}:
     *   <UL>
     *   <LI> Either add the fields there, and test to make sure SAVEGAME/LOADGAME handles their data properly
     *   <LI> Or, check for and reject the new option in {@link SavedGameModel#checkCanSave(SOCGame)}
     *       and {@link SavedGameModel#checkCanLoad()}; add a {@code TODO} to later add support to SavedGameModel
     *   </UL>
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
     *<LI> {@link SOCVersionedItem#key name key}
     *<LI> {@link #optType}
     *<LI> {@link #minVersion}
     *<LI> {@link #optFlags} such as {@link #FLAG_DROP_IF_UNUSED} -- newly defined flags could maybe be added,
     *     if old versions can safely ignore them, but a flag can't be removed from an option in a later version.
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
     *<LI> If new values require a newer minimum client version, add code to {@link #getMinVersion(Map)}.
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
    public static Map<String, SOCGameOption> initAllOptions()
    {
        HashMap<String, SOCGameOption> opt = new HashMap<String, SOCGameOption>();

        // I18N: Game option descriptions are also stored as gameopt.* in server/strings/toClient_*.properties
        //       to be sent to clients if needed.

        final SOCGameOption pl = new SOCGameOption
                ("PL", -1, 1108, 4, 2, 6, 0, "Maximum # players");
        opt.put("PL", pl);

        final SOCGameOption plb = new SOCGameOption
                ("PLB", 1108, 1113, false, FLAG_DROP_IF_UNUSED, "Use 6-player board");
        plb.setClientFeature(SOCFeatureSet.CLIENT_6_PLAYERS);
        opt.put("PLB", plb);

        final SOCGameOption plp = new SOCGameOption
                ("PLP", 1108, 2300, false, FLAG_DROP_IF_UNUSED, "6-player board: Can Special Build only if 5 or 6 players in game");
        plp.setClientFeature(SOCFeatureSet.CLIENT_6_PLAYERS);
        opt.put("PLP", plp);

        SOCGameOption op = new SOCGameOption
                ("SBL", 2000, 2000, false, FLAG_DROP_IF_UNUSED, "Use sea board");  // see also SOCBoardLarge
        op.setClientFeature(SOCFeatureSet.CLIENT_SEA_BOARD);
        opt.put("SBL", op);

        opt.put("_BHW", new SOCGameOption
                ("_BHW", 2000, 2000, 0, 0, 0xFFFF, FLAG_DROP_IF_UNUSED | FLAG_INTERNAL_GAME_PROPERTY,
                 "Large board's height and width (0xRRCC) if not default (local to client only)"));
        opt.put("RD", new SOCGameOption
                ("RD", -1, 1107, false, 0, "Robber can't return to the desert"));
        opt.put("N7", new SOCGameOption
                ("N7", -1, 1107, false, 7, 1, 999, 0, "Roll no 7s during first # rounds"));
        // N7C's keyname puts it after N7 in the NewGameOptionsFrame list
        opt.put("N7C", new SOCGameOption
                ("N7C", -1, 1119, false, FLAG_DROP_IF_UNUSED, "Roll no 7s until a city is built"));
        opt.put("BC", new SOCGameOption
                ("BC", -1, 1107, true, 4, 3, 9, 0, "Break up clumps of # or more same-type hexes/ports"));
        opt.put("NT", new SOCGameOption
                ("NT", 1107, 1107, false, FLAG_DROP_IF_UNUSED, "No trading allowed between players"));
        opt.put("VP", new SOCGameOption
                ("VP", -1, 2000, false, 10, 10, 20, FLAG_DROP_IF_UNUSED, "Victory points to win: #"));
                // If min or max changes, test client to make sure New Game dialog still shows it as a dropdown
                // (not a text box) for user convenience

        final SOCGameOption sc = new SOCGameOption
                ("SC", 2000, 2000, 8, false, FLAG_DROP_IF_UNUSED, "Game Scenario: #");
        sc.setClientFeature(SOCFeatureSet.CLIENT_SCENARIO_VERSION);
        opt.put("SC", sc);

        // Game scenario options (rules and events)
        //      Constructor calls setClientFeature(SOCFeatureSet.CLIENT_SCENARIO_VERSION) for these
        //      because keyname.startsWith("_SC_")

        //      I18N note: NewGameOptionsFrame.showScenarioInfoDialog() assumes these
        //      all start with the text "Scenarios:". When localizing, be sure to
        //      keep a consistent prefix that showScenarioInfoDialog() knows to look for.
        //      In client/strings/data_*.properties, set game.options.scenario.optprefix to that prefix.

        opt.put(K_SC_SANY, new SOCGameOption
                (K_SC_SANY, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
                 "Scenarios: SVP for your first settlement on any island after initial placement"));
        opt.put(K_SC_SEAC, new SOCGameOption
                (K_SC_SEAC, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
                 "Scenarios: 2 SVP for your first settlement on each island after initial placement"));
        opt.put(K_SC_FOG, new SOCGameOption
                (K_SC_FOG, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
                 "Scenarios: Some hexes initially hidden by fog"));
        opt.put(K_SC_0RVP, new SOCGameOption
                (K_SC_0RVP, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
                 "Scenarios: No longest trade route VP (no Longest Road)"));
        opt.put(K_SC_3IP, new SOCGameOption
                (K_SC_3IP, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
                 "Scenarios: Third initial settlement"));
        opt.put(K_SC_CLVI, new SOCGameOption
                (K_SC_CLVI, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
                 "Scenarios: Cloth Trade with neutral villages"));
        opt.put(K_SC_PIRI, new SOCGameOption
                (K_SC_PIRI, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
                 "Scenarios: Pirate Islands and fortresses"));
        opt.put(K_SC_FTRI, new SOCGameOption
                (K_SC_FTRI, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
                 "Scenarios: The Forgotten Tribe"));
        opt.put(K_SC_WOND, new SOCGameOption
                (K_SC_WOND, 2000, 2000, false, FLAG_DROP_IF_UNUSED,
                 "Scenarios: Wonders"));

        // "Extra" options for third-party developers

        opt.put(K__EXT_BOT, new SOCGameOption
                (K__EXT_BOT, 2000, 2000, TEXT_OPTION_MAX_LENGTH, false, FLAG_DROP_IF_UNUSED,
                 "Extra non-core option available for robots in this game"));
        opt.put(K__EXT_CLI, new SOCGameOption
                (K__EXT_CLI, 2000, 2000, TEXT_OPTION_MAX_LENGTH, false, FLAG_DROP_IF_UNUSED,
                 "Extra non-core option available for clients in this game"));
        opt.put(K__EXT_GAM, new SOCGameOption
                (K__EXT_GAM, 2000, 2000, TEXT_OPTION_MAX_LENGTH, false, FLAG_DROP_IF_UNUSED,
                 "Extra non-core option available for this game"));

        // NEW_OPTION - Add opt.put here at end of list, and update the
        //       list of "current known options" in javadoc just above.

        // ChangeListeners for client convenience:
        // Remember that a new server version can't update this code at an older client:
        // If you create a ChangeListener, also update adjustOptionsToKnown for server-side code.

        // If PL goes over 4, set PLB.
        pl.addChangeListener(new ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption opt, Object oldValue, Object newValue, Map<String, SOCGameOption> currentOpts)
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
        plb.addChangeListener(new ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption opt, Object oldValue, Object newValue, Map<String, SOCGameOption> currentOpts)
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
        plp.addChangeListener(new ChangeListener()
        {
            public void valueChanged(SOCGameOption opt, Object oldValue, Object newValue,
                    Map<String, SOCGameOption> currentOpts)
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
        // and VP (vp to win), unless already changed by user.
        // This is for NGOF responsiveness during new-game option setup at the client:
        // Game creation at the server doesn't rely on these updates.
        // For game creation with scenario options, see adjustOptionsToKnown(doServerPreadjust=true).

        sc.addChangeListener(new ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption optSc, Object oldValue, Object newValue, Map<String, SOCGameOption> currentOpts)
            {
                final String newSC = optSc.getStringValue();
                final boolean isScenPicked = optSc.getBoolValue() && (newSC.length() != 0);

                // check/update #VP if scenario specifies it, otherwise revert to standard
                SOCGameOption vp = currentOpts.get("VP");
                if ((vp != null) && ! vp.userChanged)
                {
                    int newVP = SOCGame.VP_WINNER_STANDARD;
                    if (isScenPicked)
                    {
                        final SOCScenario scen = SOCScenario.getScenario(newSC);
                        if (scen != null)
                        {
                            final Map<String, SOCGameOption> scenOpts = SOCGameOption.parseOptionsToMap(scen.scOpts);
                            final SOCGameOption scOptVP = (scenOpts != null) ? scenOpts.get("VP") : null;
                            if (scOptVP != null)
                                newVP = scOptVP.getIntValue();

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

        opt.put("DEBUGNOJOIN", new SOCGameOption
                ("DEBUGNOJOIN", Integer.MAX_VALUE, Integer.MAX_VALUE, false,
                 SOCGameOption.FLAG_DROP_IF_UNUSED, "Cannot join this game"));
        */

        /*
            // A commented-out debug option is kept here for each option type's testing convenience.
            // OTYPE_* - Add a commented-out debug of the new type, for testing the new type.

        opt.put("DEBUGBOOL", new SOCGameOption
                ("DEBUGBOOL", 2000, 2000, false, SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option bool"));
        opt.put("DEBUGENUM", new SOCGameOption
                ("DEBUGENUM", 1107, 1107, 3,
                 new String[]{ "First", "Second", "Third", "Fourth"},
                 SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option # enum"));
        opt.put("DEBUGENUMBOOL", new SOCGameOption
                ("DEBUGENUMBOOL", 1107, 1108, true, 3,
                 new String[]{ "First", "Second", "Third", "Fourth"},
                 SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option # enumbool"));
        opt.put("DEBUGINT", new SOCGameOption
                ("DEBUGINT", -1, 1113, 500, 1, 1000, SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option int # (range 1-1000)"));
        opt.put("DEBUGSTR", new SOCGameOption
                ("DEBUGSTR", 1107, 1107, 20, false, SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option str"));
        opt.put("DEBUGSTRHIDE", new SOCGameOption
                ("DEBUGSTRHIDE", 1107, 1107, 20, true, SOCGameOption.FLAG_DROP_IF_UNUSED, "Test option strhide"));
        */

        return opt;

        /*
            // TEST CODE: simple callback for each option, that just echoes old/new value

        ChangeListener testCL = new ChangeListener()
        {
            public void valueChanged
                (final SOCGameOption opt, Object oldValue, Object newValue, Map<String,SOCGameOption> currentOpts)
            {
                System.err.println("Test ChangeListener: " + opt.key
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

    // If you create a new option type,
    // please update parseOptionsToMap(), packOptionsToString(),
    // adjustOptionsToKnown(), and soc.message.SOCGameOptionGetInfo,
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
    private String  strValue;  // no default value: is "", stored as null

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
     * @param desc    Descriptive brief text, to appear in the options dialog
     * @throws IllegalArgumentException if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1;
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
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1;
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
     * @throws IllegalArgumentException if defaultIntValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1;
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
     *                Please see the explanation at {@link #initAllOptions()} about
     *                changing or adding to enumVals in later versions.
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the enum's popup-menu goes.
     *             If no placeholder is found, the value field appears at left,
     *             like boolean options.
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1;
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
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1;
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
     * @throws IllegalArgumentException if maxLength > {@link #TEXT_OPTION_MAX_LENGTH},
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1;
     *        {@link Throwable#getMessage()} will have details
     */
    public SOCGameOption(final String key, final int minVers, final int lastModVers,
        final int maxLength, final boolean hideTyping, final int flags, final String desc)
        throws IllegalArgumentException
    {
        this( (hideTyping ? OTYPE_STRHIDE : OTYPE_STR ),
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
     * @throws IllegalArgumentException if defaultIntValue < minValue or is > maxValue,
     *        or if key is not alphanumeric or length is > 8,
     *        or if key length > 3 and minVers &lt; 2000,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1;
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
    }

    /**
     * Copy constructor for enum-valued types ({@link #OTYPE_ENUM}, {@link #OTYPE_ENUMBOOL}),
     * for restricting (trimming) values for a certain client version.
     * @param enumOpt  Option object to copy.  If its <tt>defaultIntValue</tt> is greater than
     *                 <tt>keptEnumVals.length</tt>, the default will be reduced to that.
     * @param keptEnumVals  Enum values to keep; should be a subset of enumOpt.{@link #enumVals}
     *                 containing the first n values of that list.
     * @see #getMaxEnumValueForVersion(String, int)
     * @see #optionsNewerThanVersion(int, boolean, boolean, Map)
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
    }

    /**
     * Copy constructor for int-valued types ({@link #OTYPE_INT}, {@link #OTYPE_INTBOOL}),
     * for restricting (trimming) max value for a certain client version.
     * @param intOpt  Option object to copy.  If its <tt>defaultIntValue</tt> is greater than
     *                <tt>maxIntValue</tt>, the default will be reduced to that.
     * @param maxIntValue  Maximum value to keep, in the copy
     * @see #getMaxIntValueForVersion(String, int)
     * @see #optionsNewerThanVersion(int, boolean, boolean, Map)
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
    }

    /**
     * Is this option set, if this option's type has a boolean component?
     * @return current boolean value of this option
     * @see SOCGame#isGameOptionSet(Map, String)
     */
    public boolean getBoolValue() { return boolValue; }

    public void setBoolValue(boolean v) { boolValue = v; }

    /**
     * This option's integer value, if this option's type has an integer component.
     * @return current integer value of this option
     * @see SOCGame#getGameOptionIntValue(Map, String)
     * @see SOCGame#getGameOptionIntValue(Map, String, int, boolean)
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
     * @see SOCGame#getGameOptionStringValue(Map, String)
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
     *
     * @return the client feature required to use this game option,
     *     like {@link SOCFeatureSet#CLIENT_SEA_BOARD}, or null if none
     * @see #setClientFeature(String)
     * @see #optionsNotSupported(SOCFeatureSet)
     * @see #optionsTrimmedForSupport(SOCFeatureSet)
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
     *     same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
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
     *               and {@link soc.util.Version#versionNumber()}
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
     *               and {@link soc.util.Version#versionNumber()}
     * @return  Maximum permitted value for this version, or {@link Integer#MAX_VALUE}
     *          if this option has no restriction.
     * @since 1.1.08
     * @see #optionsTrimmedForSupport(SOCFeatureSet)
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
     * Make and return a copy of all known objects. Calls {@link #cloneOptions(Map)}.
     * @return a deep copy of all known option objects
     * @see #addKnownOption(SOCGameOption)
     * @see #initAllOptions()
     */
    public static Map<String, SOCGameOption> getAllKnownOptions()
    {
        return cloneOptions(allOptions);
    }

    /**
     * Add a new known option (presumably received from a server of newer or older version),
     * or update the option's information.
     * @param onew New option, or a changed version of an option we already know.
     *             If onew.optType == {@link #OTYPE_UNKNOWN}, will remove from the known table.
     *             If this option is already known and the old copy has a {@link SOCGameOption#getChangeListener()},
     *             that listener is copied to {@code onew}.
     * @return true if it's new, false if we already had that key and it was updated
     * @see #getAllKnownOptions()
     */
    public static boolean addKnownOption(SOCGameOption onew)
    {
        final String oKey = onew.key;
        final boolean hadOld;

        synchronized (allOptions)
        {
            final SOCGameOption oldcopy = allOptions.remove(oKey);
            hadOld = (oldcopy != null);

            if (onew.optType != OTYPE_UNKNOWN)
            {
                if (hadOld)
                {
                    final ChangeListener cl = oldcopy.getChangeListener();
                    if (cl != null)
                        onew.addChangeListener(cl);
                }

                allOptions.put(oKey, onew);
            }
        }

        return ! hadOld;
    }

    /**
     * Set the current value(s) of a known option, based on the current value(s) of
     * another object {@code ocurr} with the same {@link SOCVersionedItem#key key}.
     * If there is no known option with oCurr.{@link SOCVersionedItem#key key}, it is ignored and nothing is set.
     * @param ocurr Option with the requested current value.
     *            {@code ocurr}'s value field contents are copied to the known option's values,
     *            the {@code ocurr} reference won't be added to the known option set.
     * @throws  IllegalArgumentException if string value is not permitted; note that
     *            int values outside of range are silently clipped, and will not
     *            throw this exception.
     * @see #getOption(String, boolean)
     */
    public static void setKnownOptionCurrentValue(SOCGameOption ocurr)
        throws IllegalArgumentException
    {
        final String oKey = ocurr.key;

        synchronized (allOptions)
        {
            final SOCGameOption oKnown = allOptions.get(oKey);

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
    }

    /**
     * Make a deep copy of a group of options.
     * Iterates over {@link Map#entrySet() opts.entrySet()} (ignores map keys).
     * @param opts  a map of SOCGameOptions, or null; method synchronizes on {@code opts}
     * @return a deep copy of all option objects within opts, or null if opts is null.
     *    Each item's map key will be its {@link SOCVersionedItem#key}.
     */
    public static Map<String, SOCGameOption> cloneOptions(final Map<String, SOCGameOption> opts)
    {
        if (opts == null)
            return null;

        HashMap<String, SOCGameOption> opts2 = new HashMap<String, SOCGameOption>();
        synchronized (opts)
        {
            for (Map.Entry<String, SOCGameOption> e : opts.entrySet())
            {
                SOCGameOption op = e.getValue();
                try
                {
                    opts2.put(op.key, (SOCGameOption) op.clone());
                } catch (CloneNotSupportedException ce) {
                    // required, but not expected to happen
                    throw new IllegalStateException("Clone failed!", ce);
                }
            }
        }

        return opts2;
    }

    /**
     * Get information about a known option. See {@link #initAllOptions()} for a summary of each known option.
     * @param key  Option key
     * @param clone  True if a copy of the option is needed; set this true
     *               unless you're sure you won't be changing any fields of
     *               its original object, which is a shared copy in a static namekey->object map.
     * @return information about a known option, or null if none with that key
     * @throws IllegalStateException  if {@code clone} but the object couldn't be cloned; this isn't expected to ever happen
     * @see #getAllKnownOptions()
     */
    public static SOCGameOption getOption(final String key, final boolean clone)
        throws IllegalStateException
    {
        SOCGameOption op;
        synchronized (allOptions)
        {
            op = allOptions.get(key);
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
     * Gameopt-specific version of {@link SOCVersionedItem#itemsMinimumVersion(Map, boolean)},
     * in case any special logic is needed. See that method for javadocs.
     *<P>
     * When {@code calcMinVersionForUnchanged}, checks opt {@code "SBL"} for v2 clients:
     * See {@link SOCBoardLarge#VERSION_FOR_ALSO_CLASSIC}.
     *
     * @since 3.0.00
     */
    public static int optionsMinimumVersion
        (final Map<?, SOCGameOption> items, final boolean calcMinVersionForUnchanged)
         throws NullPointerException
    {
        int minVers = SOCVersionedItem.itemsMinimumVersion(items, calcMinVersionForUnchanged);

        if (calcMinVersionForUnchanged && (minVers < SOCBoardLarge.VERSION_FOR_ALSO_CLASSIC))
        {
            // force SBL true for clients < 3.0
            if ((items == null) || ! (items.containsKey("SBL") && items.get("SBL").boolValue))
                minVers = SOCBoardLarge.VERSION_FOR_ALSO_CLASSIC;
        }

        return minVers;
    }

    /**
     * Utility - build a string of option name-value pairs from the
     *           {@link #getAllKnownOptions() known options}' current values.
     *
     * @param hideEmptyStringOpts omit string-valued options which are empty?
     *            Suitable only for sending defaults.
     * @param hideLongNameOpts omit options with long key names or underscores?
     *            Set true if client's version &lt; {@link #VERSION_FOR_LONGER_OPTNAMES}.
     * @return string of name-value pairs, same format as
     *         {@link #packOptionsToString(Map, boolean, boolean) packOptionsToString(Map, hideEmptyStringOpts, false)};
     *         any gameoptions of {@link #OTYPE_UNKNOWN} will not be
     *         part of the string.
     * @see #parseOptionsToMap(String)
     */
    public static String packKnownOptionsToString(final boolean hideEmptyStringOpts, final boolean hideLongNameOpts)
    {
        return packOptionsToString(allOptions, hideEmptyStringOpts, false, (hideLongNameOpts) ? -3 : -2);
    }

    /**
     * Utility - build a string of option name-value pairs.
     * This can be unpacked with {@link #parseOptionsToMap(String)}.
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
     * @throws ClassCastException if {@code omap} contains anything other
     *         than {@code SOCGameOption}s
     * @see #parseOptionNameValue(String, boolean)
     * @see #parseOptionNameValue(String, String, boolean)
     * @see #packValue(StringBuilder)
     */
    public static String packOptionsToString
        (final Map<String, SOCGameOption> omap, boolean hideEmptyStringOpts, final boolean sortByKey)
        throws ClassCastException
    {
        return packOptionsToString(omap, hideEmptyStringOpts, sortByKey, -2);
    }

    /**
     * Utility - build a string of option name-value pairs,
     * adjusting for old clients if necessary.
     * This can be unpacked with {@link #parseOptionsToMap(String)}.
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
     * @throws ClassCastException if {@code omap} contains anything other
     *         than {@code SOCGameOption}s
     * @see #packValue(StringBuilder)
     */
    public static String packOptionsToString
        (final Map<String, SOCGameOption> omap, boolean hideEmptyStringOpts, final boolean sortByKey, final int cliVers)
        throws ClassCastException
    {
        /** true if client version must be told to create SOCBoardLarge for all boards from this server version */
        final boolean forceSBLTrue = (cliVers < SOCBoardLarge.VERSION_FOR_ALSO_CLASSIC) && (cliVers > -2);

        if ((omap == null) || omap.size() == 0)
        {
            return (forceSBLTrue) ? "SBL=t" : "-";
        }

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

                else if (forceSBLTrue && op.key.equals("SBL") && ! op.boolValue)
                {
                    sb.append('t');
                    wroteValueAlready = true;
                }

                // NEW_OPTION - Check your option vs old clients here.
            }

            if (! wroteValueAlready)
                op.packValue(sb);
        }

        if (forceSBLTrue && ! omap.containsKey("SBL"))
        {
            if (hadAny)
                sb.append(SOCMessage.sep2_char);
            sb.append("SBL=t");
        }

        return sb.toString();
    }

    /**
     * Pack current value of this option into a string.
     * This is used in {@link #packOptionsToString(Map, boolean, boolean)} and
     * read in {@link #parseOptionNameValue(String, boolean)} and {@link #parseOptionsToMap(String)}.
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
     * Utility - build a map of keys to SOCGameOptions by parsing a list of option name-value pairs.
     * For each pair in {@code ostr}, calls
     * {@link #parseOptionNameValue(String, boolean) parseOptionNameValue(pair, false)}.
     *<P>
     * Before v2.0.00, this was {@code parseOptionsToHash}.
     *
     * @param ostr string of name-value pairs, as created by
     *             {@link #packOptionsToString(Map, boolean, boolean)}.
     *             A leading comma is OK (possible artifact of StringTokenizer
     *             coming from over the network).
     *             If ostr=="-", returned map will be null.
     * @return map of SOCGameOptions, or null if ostr==null or empty ("-")
     *         or if ostr is malformed.  Any unrecognized options
     *         will be in the map as type {@link #OTYPE_UNKNOWN}.
     *         The returned known SGOs are clones from the set of all known options.
     * @see #parseOptionNameValue(String, boolean)
     * @see #parseOptionNameValue(String, String, boolean)
     * @throws IllegalArgumentException if any game option keyname in {@code ostr} is unknown
     *     and not a valid alphanumeric keyname by the rules listed at {@link #SOCGameOption(String)}
     */
    public static Map<String,SOCGameOption> parseOptionsToMap(final String ostr)
    {
        if ((ostr == null) || ostr.equals("-"))
            return null;

        HashMap<String,SOCGameOption> ohash = new HashMap<String,SOCGameOption>();

        StringTokenizer st = new StringTokenizer(ostr, SOCMessage.sep2);
        String nvpair;
        while (st.hasMoreTokens())
        {
            nvpair = st.nextToken();  // skips any leading commas or doubled commas
            SOCGameOption copyOpt = parseOptionNameValue(nvpair, false);
            if (copyOpt == null)
                return null;  // parse error
            ohash.put(copyOpt.key, copyOpt);
        }  // while (moreTokens)

        return ohash;
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
     * @return Parsed option, or null if parse error;
     *         if known, the returned object is a clone of the SGO from the set of all known options.
     *         if nvpair's option keyname is not a known option, returned optType will be {@link #OTYPE_UNKNOWN}.
     * @throws IllegalArgumentException if {@code optkey} is unknown and not a valid alphanumeric keyname
     *         by the rules listed at {@link #SOCGameOption(String)}
     * @see #parseOptionNameValue(String, String, boolean)
     * @see #parseOptionsToMap(String)
     * @see #packValue(StringBuilder)
     */
    public static SOCGameOption parseOptionNameValue(final String nvpair, final boolean forceNameUpcase)
        throws IllegalArgumentException
    {
        int i = nvpair.indexOf('=');  // don't just tokenize for this (efficiency, and param value may contain a "=")
        if (i < 1)
            return null;  // malformed

        String optkey = nvpair.substring(0, i);
        String optval = nvpair.substring(i+1);
        return parseOptionNameValue(optkey, optval, forceNameUpcase);
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
     * @return Parsed option, or null if parse error;
     *         if known, the returned object is a clone of the SGO from the set of all known options.
     *         if {@code optkey} is not a known option, returned optType will be {@link #OTYPE_UNKNOWN}.
     * @throws IllegalArgumentException if {@code optkey} is unknown and not a valid alphanumeric keyname
     *         by the rules listed at {@link #SOCGameOption(String)}; {@link Throwable#getMessage()} will have details
     * @see #parseOptionNameValue(String, boolean)
     * @see #parseOptionsToMap(String)
     * @see #packValue(StringBuilder)
     * @since 2.0.00
     */
    public static SOCGameOption parseOptionNameValue
        (String optkey, final String optval, final boolean forceNameUpcase)
        throws IllegalArgumentException
    {
        if (forceNameUpcase)
            optkey = optkey.toUpperCase();

        SOCGameOption knownOpt = allOptions.get(optkey);
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
     * Compare a set of options against the specified version.
     * Make a list of all which are new or changed since that version.
     *<P>
     * This method has 2 modes, because it's called for 2 different purposes:
     *<UL>
     * <LI> sync client-server known-option info, in general: <tt>checkValues</tt> == false
     * <LI> check if client can create game with a specific set of option values: <tt>checkValues</tt> == true
     *</UL>
     * See <tt>checkValues</tt> for method's behavior in each mode.
     *<P>
     * <B>Game option names:</B><br>
     * When running this at the client (<tt>vers</tt> is the older remote server's version),
     * some of the returned too-new options have long names that can't be sent to a v1.x.xx
     * server (<tt>vers</tt> &lt; {@link #VERSION_FOR_LONGER_OPTNAMES}).
     * You must check for this and remove them before sending them to the remote server.
     * Game option names sent to 1.x.xx servers must be 3 characters or less, alphanumeric, no underscores ('_').
     *<P>
     * When running at the server, we will never send an option whose name is invalid to v1.x.xx clients,
     * because the SOCGameOption constructors enforce <tt>minVers >= 2000</tt> when the name is longer than 3
     * characters or contains '_'.
     *
     * @param vers  Version to compare known options against
     * @param checkValues  Which mode: Check options' current values and {@link #minVersion},
     *              not their {@link #lastModVersion}?  An option's minimum version
     *              can increase based on its value; see {@link #getMinVersion(Map)}.
     * @param trimEnums  For enum-type options where minVersion changes based on current value,
     *              should we remove too-new values from the returned option info?
     *              This lets us send only the permitted values to an older client.
     *              Also trims int-type options' max value where needed (example: {@code "PL"}).
     * @param opts  Set of {@link SOCGameOption}s to check versions and current values;
     *              if null, use the "known option" set
     * @return List of the newer (added or changed) {@link SOCGameOption}s, or null
     *     if all are known and unchanged since <tt>vers</tt>.
     *     <BR>
     *     <B>Note:</B> May include options with {@link #minVersion} &gt; {@code vers};
     *     the client may want to know about those.
     * @see #optionsForVersion(int, Map)
     * @see #optionsTrimmedForSupport(SOCFeatureSet)
     */
    public static List<SOCGameOption> optionsNewerThanVersion
        (final int vers, final boolean checkValues, final boolean trimEnums, final Map<String, SOCGameOption> opts)
    {
        return implOptionsVersionCheck(vers, false, checkValues, trimEnums, opts);
    }

    /**
     * Get all options valid at version {@code vers}.  If necessary, trim enum value ranges or int value ranges if
     * range was smaller at {@code vers}, like {@link #optionsNewerThanVersion(int, boolean, boolean, Map)} does.
     *<P>
     * If {@code vers} from a client is newer than this version of SOCGameOption, will return all options known at this
     * version, which may not include all of the newer version's options.  Client game-option negotiation handles this
     * by having the newer client send all its new (added or changed) option keynames to the older server to allow,
     * adjust, or reject.
     *
     * @param vers  Version to compare options against
     * @param opts  Set of {@link SOCGameOption}s to check versions, or {@code null} to use the "known option" set
     * @return  List of all {@link SOCGameOption}s valid at version {@code vers}, or {@code null} if none.
     * @see #optionsNewerThanVersion(int, boolean, boolean, Map)
     * @see #optionsTrimmedForSupport(SOCFeatureSet)
     * @since 2.0.00
     */
    public static List<SOCGameOption> optionsForVersion
        (final int vers, final Map<String, SOCGameOption> opts)
    {
        return implOptionsVersionCheck(vers, true, false, true, opts);
    }

    /**
     * Get all options added or changed since version {@code vers}, or all options valid at {@code vers},
     * to implement {@link #optionsNewerThanVersion(int, boolean, boolean, Map)}
     * and {@link #optionsForVersion(int, Map)}.
     * @param vers  Version to compare options against
     * @param getAllForVersion  True to get all valid options ({@code optionsForVersion} mode),
     *              false for newer added or changed options only ({@code optionsNewerThanVersion} modes).
     *              If true and {@code vers} is newer than this version of SOCGameOption, will return
     *              all options known at this version.
     * @param checkValues  If not {@code getAllForVersion}, which mode to run in:
     *              Check options' current values and {@link #minVersion}, not their {@link #lastModVersion}?
     *              An option's minimum version can increase based on its value; see {@link #getMinVersion(Map)}.
     * @param trimEnums  For enum-type options where minVersion changes based on current value,
     *              should we remove too-new values from the returned option info?
     *              This lets us send only the permitted values to an older client.
     *              Also trims int-type options' max value where needed (example: {@code "PL"}).
     * @param opts  Set of {@link SOCGameOption}s to check versions and current values;
     *              if null, use the "known option" set
     * @return List of the requested {@link SOCGameOption}s, or null if none match the conditions, at {@code vers};
     *     see {@code optionsNewerThanVersion} and {@code optionsForVersion} for return details.
     *     <BR>
     *     <B>Note:</B> If not {@code getAllForVersion}, may include options with
     *     {@link #minVersion} &gt; {@code vers}; the client may want to know about those.
     * @throws IllegalArgumentException  if {@code getAllForVersion && checkValues}: Cannot combine these modes
     * @since 2.0.00
     */
    private static List<SOCGameOption> implOptionsVersionCheck
        (final int vers, final boolean getAllForVersion, final boolean checkValues, final boolean trimEnums,
         Map<String, SOCGameOption> opts)
        throws IllegalArgumentException
    {
        if (opts == null)
            opts = allOptions;

        /** collect newer options here, or all options if getAllForVersion */
        List<SOCGameOption> uopt
            = SOCVersionedItem.implItemsVersionCheck(vers, getAllForVersion, checkValues, opts);
                // throws IllegalArgumentException if (getAllForVersion && checkValues)

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
                        final int ev = getMaxEnumValueForVersion(opt.key, vers);
                        if (ev < opt.enumVals.length)
                        {
                            opt = trimEnumForVersion(opt, vers);
                            changed = true;
                        }
                    } else if (opt.maxIntValue != opt.minIntValue)
                    {
                        // Possibly trim max int value. (OTYPE_INT, OTYPE_INTBOOL)
                        // OTYPE_* - Add here in comment if int-valued option type
                        final int iv = getMaxIntValueForVersion(opt.key, vers);
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
     * Do any known options require client features
     * not supported by a limited client's {@link SOCFeatureSet}?
     * Checks each option having a {@link #getClientFeature()}.
     *
     * @param cliFeats  Client's limited subset of optional features,
     *     from {@link soc.server.SOCClientData#feats}, or {@code null} or empty set if no features
     * @return Map of known options not supported by {@code cliFeats},
     *     or {@code null} if all known options are supported.
     *     Each map key is its option value's {@link SOCVersionedItem#key key}.
     * @see #optionsTrimmedForSupport(SOCFeatureSet)
     * @since 2.4.00
     */
    public static Map<String, SOCGameOption> optionsNotSupported(final SOCFeatureSet cliFeats)
    {
        Map<String, SOCGameOption> ret = null;

        for (SOCGameOption opt : allOptions.values())
        {
            if (opt.clientFeat == null)
                continue;
            if ((cliFeats != null) && cliFeats.isActive(opt.clientFeat))
                continue;

            if (ret == null)
                ret = new HashMap<>();
            ret.put(opt.key, opt);
        }

        return ret;
    }

    /**
     * Do any known options require changes for a limited client's {@link SOCFeatureSet}?
     * For example, clients without {@link SOCFeatureSet#CLIENT_6_PLAYERS} limit "max players" to 4.
     *<P>
     * Assumes client is new enough that its version wouldn't also cause trimming of option values
     * by {@link #optionsNewerThanVersion(int, boolean, boolean, Map)} or {@link #optionsForVersion(int, Map)}.
     *
     * @param cliFeats  Client's limited subset of optional features,
     *     from {@link soc.server.SOCClientData#feats}, or {@code null} or empty set if no features
     * @return Map of trimmed known options, or {@code null} if no trimming was needed
     *     Each map key is its option value's {@link SOCVersionedItem#key key}.
     * @see #optionsNotSupported(SOCFeatureSet)
     * @see #getMaxIntValueForVersion(String, int)
     * @since 2.4.00
     */
    public static Map<String, SOCGameOption> optionsTrimmedForSupport(final SOCFeatureSet cliFeats)
    {
        if ((cliFeats != null) && cliFeats.isActive(SOCFeatureSet.CLIENT_6_PLAYERS))
            return null;

        SOCGameOption pl = getOption("PL", false);
        if (pl == null)
            return null;  // shouldn't happen, PL is a known option

        Map<String, SOCGameOption> ret = new HashMap<>();
        ret.put("PL", new SOCGameOption(pl, SOCGame.MAXPLAYERS_STANDARD));
        return ret;
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
     * Compare a set of options with known-good values, and optionally apply options from
     * the new game's scenario (game option <tt>"SC"</tt>) if present.
     *<P>
     * If any values are above/below maximum/minimum, clip to the max/min value in knownOpts.
     * If any are unknown, return a description. Will still check (and clip) the known ones.
     * If any options are default, and unset/blank, and
     * their {@link #FLAG_DROP_IF_UNUSED} flag is set, remove them from newOpts.
     * For {@link #OTYPE_INTBOOL} and {@link #OTYPE_ENUMBOOL}, both the integer and
     * boolean values are checked against defaults.
     *<P>
     * If <tt>doServerPreadjust</tt> is true, then the server might also change some
     * option values before creating the game, for overall consistency of the set of options.
     * This is a server-side equivalent to the client-side {@link ChangeListener}s.
     * For example, if <tt>"PL"</tt> (number of players) > 4, but <tt>"PLB"</tt> (use 6-player board)
     * is not set, <tt>doServerPreadjust</tt> wil set the <tt>"PLB"</tt> option.
     * {@code doServerPreadjust} will also remove any game-internal options the client has sent.
     *<P>
     * Before any other adjustments when <tt>doServerPreadjust</tt>, will check for
     * the game scenario option <tt>"SC"</tt>. If that option is set, call
     * {@link SOCScenario#getScenario(String)}; the scenario name must be known.
     * Then, add that scenario's {@link SOCScenario#scOpts .scOpts} into <tt>newOpts</tt>.
     * Scenario option values always overwrite those in <tt>newOpts</tt>, except for <tt>"VP"</tt>
     * where <tt>newOpts</tt> value (if any) is kept.
     *<P>
     * Client-side gameopt code also assumes all scenarios use the sea board,
     * and sets game option <tt>"SBL"</tt> when a scenario is chosen by the user.
     *
     * @param newOpts Set of SOCGameOptions to check against knownOpts;
     *            an option's current value will be changed if it's outside of
     *            the min/max for that option in knownOpts.
     *            Must not be null.
     *            If {@code doServerPreadjust} and contains {@code "SC"},
     *            adds the scenario's game options to this set.
     * @param knownOpts Set of known SOCGameOptions to check against, or null to use
     *            the server's static copy
     * @param doServerPreadjust  If true, we're calling from the server before creating a game;
     *            pre-adjust any values for consistency.
     *            This is a server-side equivalent to the client-side {@link ChangeListener}s.
     *            (Added in 1.1.13)
     * @return <tt>null</tt> if all are known; or, a human-readable problem description if:
     *            <UL>
     *            <LI> any of {@code newOpts} are unknown
     *            <LI> or an opt's type differs from that in knownOpts
     *            <LI> or an opt's {@link #lastModVersion} differs from in knownOpts
     *            <LI> opt {@code "SC"} is in {@code newOpts} but its scenario name isn't known
     *                 by {@link SOCScenario#getScenario(String)}
     *            </UL>
     * @throws IllegalArgumentException if newOpts contains a non-SOCGameOption
     */
    public static StringBuilder adjustOptionsToKnown
        (final Map<String, SOCGameOption> newOpts, Map<String, SOCGameOption> knownOpts,
         final boolean doServerPreadjust)
        throws IllegalArgumentException
    {
        if (knownOpts == null)
            knownOpts = allOptions;

        String unknownScenario = null;

        if (doServerPreadjust)
        {
            // Remove any game-internal options, before adding scenario opts
            {
                Iterator<String> ki = newOpts.keySet().iterator();  // keySet lets us remove without disrupting iterator
                while (ki.hasNext())
                {
                    SOCGameOption op = newOpts.get(ki.next());
                    if (0 != (op.optFlags & SOCGameOption.FLAG_INTERNAL_GAME_PROPERTY))
                        ki.remove();
                }
            }

            // If has "VP" but boolean part is false, use server default instead
            SOCGameOption opt = newOpts.get("VP");
            if ((opt != null) && ! opt.boolValue)
                newOpts.remove("VP");

            // Apply scenario options, if any
            opt = newOpts.get("SC");
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
                        // opts if already in newOpts, except
                        // keep VP if specified.
                        opt = newOpts.get("VP");

                        final Map<String, SOCGameOption> scOpts = parseOptionsToMap(sc.scOpts);
                        if (scOpts.containsKey("VP") && (opt != null))
                            scOpts.remove("VP");

                        newOpts.putAll(scOpts);
                    }
                }

                // Client-side gameopt code also assumes all scenarios use
                // the sea board, and sets game option "SBL" when a scenario
                // is chosen by the user.
            }

            // NEW_OPTION: If you created a ChangeListener, you should probably add similar code
            //    here. Set or change options if it makes sense; if a user has deliberately
            //    set a boolean option, think carefully before un-setting it and surprising them.

            // Set PLB if PL>4 or PLP
            opt = newOpts.get("PL");
            SOCGameOption optPLP = newOpts.get("PLP");
            if (((opt != null) && (opt.getIntValue() > 4))
                || ((optPLP != null) && optPLP.getBoolValue()))
                setBoolOption(newOpts, "PLB");

        }  // if(doServerPreadjust)

        // OTYPE_* - adj javadoc above (re dropIfUnused) if a string-type or bool-type is added.

        StringBuilder optProblems = new StringBuilder();

        boolean allKnown;

        if (unknownScenario != null)
        {
            allKnown = false;
            optProblems.append("SC: unknown scenario ");
            optProblems.append(unknownScenario);
            optProblems.append(". ");
        } else {
            allKnown = true;  // might be set false in loop below
        }

        // use Iterator in loop, so we can remove from the hash if needed
        for (Iterator<Map.Entry<String, SOCGameOption>> ikv = newOpts.entrySet().iterator();
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
                optProblems.append(op.key);
                optProblems.append(": unknown. ");
            }
            else if (knownOp.optType != op.optType)
            {
                allKnown = false;
                optProblems.append(op.key);
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
                    optProblems.append(op.key);
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

                        if (knownOp.hasFlag(FLAG_DROP_IF_UNUSED)
                            && (iv == knownOp.defaultIntValue))
                        {
                            // ignore boolValue unless also boolean-type: OTYPE_INTBOOL and OTYPE_ENUMBOOL.
                            if ((op.optType == OTYPE_INT) || (op.optType == OTYPE_ENUM) || ! op.boolValue)
                                ikv.remove();
                        }
                    }
                    break;

                case OTYPE_BOOL:
                    if (knownOp.hasFlag(FLAG_DROP_IF_UNUSED) && ! op.boolValue)
                        ikv.remove();
                    break;

                case OTYPE_STR:
                case OTYPE_STRHIDE:
                    if (knownOp.hasFlag(FLAG_DROP_IF_UNUSED) &&
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
     * Within a set of options, include a boolean option and make it true.
     * If the option object doesn't exist in <tt>newOpts</tt>, it will be cloned from
     * the set of known options.
     * @param newOpts Options to set <tt>boKey</tt> within
     * @param boKey   Key name for boolean option to set
     * @throws NullPointerException  if <tt>boKey</tt> isn't in <tt>newOpts</tt>
     *   and doesn't exist in the set of known options
     * @see #setIntOption(Map, String, int, boolean)
     * @since 1.1.17
     */
    public static void setBoolOption(final Map<String, SOCGameOption> newOpts, final String boKey)
        throws NullPointerException
    {
        SOCGameOption opt = newOpts.get(boKey);
        if (opt == null)
        {
            try
            {
                opt = (SOCGameOption) (allOptions.get(boKey).clone());
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
     * @see #setBoolOption(Map, String)
     * @since 1.1.17
     */
    public static void setIntOption
        (final Map<String, SOCGameOption> newOpts, final String ioKey, final int ivalue, final boolean bvalue)
        throws NullPointerException
    {
        SOCGameOption opt = newOpts.get(ioKey);
        if (opt == null)
        {
            try
            {
                opt = (SOCGameOption) (allOptions.get(ioKey).clone());
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
     * @param flagMask  Option flag such as {@link #FLAG_DROP_IF_UNUSED}, or multiple flags added together
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
     *    as {@link #packKnownOptionsToString(boolean, boolean)}.
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
     *    as {@link #packKnownOptionsToString(boolean)}.
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
     * Greater/lesser is determined by
     * {@link SOCVersionedItem#getDesc() desc}.{@link String#compareTo(String) compareTo()}.
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
            return desc.compareTo(oopt.desc);
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
     * @since 2.4.10
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
     * Listener for option value changes <em>at the client</em> during game creation.
     * When the user changes an option, allows a related option to change.
     * For example, when the max players is changed to 5 or 6,
     * the listener can check the box for "use 6-player board".
     *<P>
     * Once written, a newer server can't do anything to update an older client's
     * ChangeListener code, so be careful and write them defensively.
     *<P>
     * Callback method is {@link #valueChanged(SOCGameOption, Object, Object, Map)}.
     * Called from <tt>NewGameOptionsFrame</tt>.
     *<P>
     * For <em>server-side</em> consistency adjustment of values before creating games,
     * add code to {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean)}
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
            (final SOCGameOption opt, final Object oldValue, final Object newValue,
             Map<String, SOCGameOption> currentOpts);
    }

}
