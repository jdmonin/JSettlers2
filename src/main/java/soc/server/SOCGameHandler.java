/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013-2020 Jeremy D Monin <jeremy@nand.net>.
 * Contents were formerly part of SOCServer.java;
 * portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
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
package soc.server;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import soc.debug.D;
import soc.game.*;
import soc.message.SOCAcceptOffer;  // for javadocs only
import soc.message.SOCBankTrade;
import soc.message.SOCBoardLayout;
import soc.message.SOCBoardLayout2;
import soc.message.SOCBotJoinGameRequest;
import soc.message.SOCCancelBuildRequest;
import soc.message.SOCChangeFace;
import soc.message.SOCChoosePlayerRequest;
import soc.message.SOCClearOffer;
import soc.message.SOCClearTradeMsg;
import soc.message.SOCDebugFreePlace;
import soc.message.SOCDevCardAction;
import soc.message.SOCDevCardCount;
import soc.message.SOCDiceResult;
import soc.message.SOCDiscard;
import soc.message.SOCDiscardRequest;
import soc.message.SOCFirstPlayer;
import soc.message.SOCGameElements;
import soc.message.SOCGameElements.GEType;
import soc.message.SOCGameMembers;
import soc.message.SOCGameServerText;
import soc.message.SOCGameState;
import soc.message.SOCGameStats;
import soc.message.SOCGameTextMsg;
import soc.message.SOCInventoryItemAction;
import soc.message.SOCJoinGame;
import soc.message.SOCJoinGameAuth;
import soc.message.SOCKeyedMessage;
import soc.message.SOCLocalizedStrings;
import soc.message.SOCLargestArmy;
import soc.message.SOCLastSettlement;
import soc.message.SOCLeaveGame;
import soc.message.SOCLongestRoad;
import soc.message.SOCMakeOffer;
import soc.message.SOCMessage;
import soc.message.SOCMovePiece;
import soc.message.SOCMoveRobber;
import soc.message.SOCPieceValue;
import soc.message.SOCPlayerElement;
import soc.message.SOCPlayerElement.PEType;
import soc.message.SOCPlayerElements;
import soc.message.SOCPlayerStats;
import soc.message.SOCPotentialSettlements;
import soc.message.SOCPutPiece;
import soc.message.SOCResetBoardReject;
import soc.message.SOCRevealFogHex;
import soc.message.SOCRollDice;
import soc.message.SOCRollDicePrompt;
import soc.message.SOCSVPTextMessage;
import soc.message.SOCScenarioInfo;
import soc.message.SOCSetPlayedDevCard;
import soc.message.SOCSetSeatLock;
import soc.message.SOCSetSpecialItem;
import soc.message.SOCSetTurn;
import soc.message.SOCSimpleAction;
import soc.message.SOCSimpleRequest;
import soc.message.SOCSitDown;
import soc.message.SOCStartGame;
import soc.message.SOCStatusMessage;
import soc.message.SOCTurn;
import soc.server.genericServer.Connection;
import soc.util.IntPair;
import soc.util.SOCFeatureSet;
import soc.util.SOCGameList;
import soc.util.SOCStringManager;
import soc.util.Version;

/**
 * Server class to handle game-specific actions and messages for the SoC game type.
 * Clients' inbound messages are received by {@link SOCGameMessageHandler}, which
 * calls this game handler for frequently used game logic and response methods.
 * Use {@link #getMessageHandler()} to obtain the game handler's message handler.
 *<P>
 * Before v2.0.00, these methods and fields were part of {@link SOCServer}.
 * So, some may have {@code @since} javadoc labels with versions older than 2.0.00.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCGameHandler extends GameHandler
    implements SOCGameEventListener
{
    /**
     * Force robot to end their turn after this much inactivity,
     * while they've made a trade offer. Default is 60 seconds.
     * This is longer than the usual robot turn timeout,
     * and used only if waiting for a human player's response.
     *<P>
     * This field was originally in SOCServer, moved in v2.0.00.
     * @see SOCServer#ROBOT_FORCE_ENDTURN_SECONDS
     * @see SOCServer#checkForExpiredTurns(long)
     * @since 1.1.11
     */
    public static int ROBOT_FORCE_ENDTURN_TRADEOFFER_SECONDS = 60;

    /**
     * Used by {@link #SOC_DEBUG_COMMANDS_HELP}, etc.
     * @see #DEBUG_COMMANDS_HELP_PLAYER
     */
    private static final String DEBUG_COMMANDS_HELP_RSRCS
        = "rsrcs: #cl #or #sh #wh #wo player";

    /**
     * Used by {@link #SOC_DEBUG_COMMANDS_HELP}, etc.
     * @see #DEBUG_COMMANDS_HELP_PLAYER
     */
    private static final String DEBUG_COMMANDS_HELP_DEV
        = "dev: #typ player";

    /**
     * Debug help: player name or number. Used by {@link #SOC_DEBUG_COMMANDS_HELP}, etc.
     * @since 1.1.20
     */
    private static final String DEBUG_COMMANDS_HELP_PLAYER
        = "'Player' is a player name or #number (upper-left is #0, increasing clockwise)";

    /**
     * Debug help: 1-line summary of dev card types, from {@link SOCDevCardConstants}.
     * @see #SOC_DEBUG_COMMANDS_HELP
     * @since 1.1.17
     */
    private static final String DEBUG_COMMANDS_HELP_DEV_TYPES =
        "### 1:road  2:year of plenty  3:mono  4:gov  5:market  6:univ  7:temple  8:chapel  9:soldier";

    /**
     * Used by {@link #SOC_DEBUG_COMMANDS_HELP}, etc. Used with {@link SOCGame#debugFreePlacement}.
     */
    private static final String DEBUG_CMD_FREEPLACEMENT = "*FREEPLACE*";

    /**
     * Debug command prefix for scenario-related debugging. Used with
     * {@link #processDebugCommand_scenario(Connection, SOCGame, String)}.
     * @since 2.0.00
     */
    private static final String DEBUG_CMD_PFX_SCENARIO = "*SCEN* ";

    /**
     * Debug help text to place at the end of {@link SOCServer#DEBUG_COMMANDS_HELP} via {@link #getDebugCommandsHelp()}.
     */
    private static final String[] SOC_DEBUG_COMMANDS_HELP =
        {
        DEBUG_CMD_FREEPLACEMENT + " 1 or 0  Start or end 'Free Placement' mode",
        "--- Debug Resources ---",
        DEBUG_COMMANDS_HELP_RSRCS,
        "Example  rsrcs: 0 3 0 2 0 Myname  or  rsrcs: 0 3 0 2 0 #3",
        DEBUG_COMMANDS_HELP_DEV,
        "Example  dev: 2 Myname   or  dev: 2 #3",
        DEBUG_COMMANDS_HELP_PLAYER,
        "Development card types are:",  // see SOCDevCardConstants
        "1 road-building",
        "2 year of plenty",
        "3 monopoly",
        "4 governors house",
        "5 market",
        "6 university",
        "7 temple",
        "8 chapel",
        "9 robber",
        "--- Scenario Debugging ---",  // see processDebugCommand_scenario(..)
        "For SC_FTRI: *scen* giveport #typenum #placeflag player",
        };

    /**
     * The 5 resource types, for sending {@link SOCPlayerElements}:
     * {@link PEType#CLAY}, ORE, SHEEP, WHEAT, {@link PEType#WOOD}.
     * @see #ELEM_RESOURCES_WITH_UNKNOWN
     * @since 2.0.00
     */
    public static final PEType[] ELEM_RESOURCES =
        {PEType.CLAY, PEType.ORE, PEType.SHEEP, PEType.WHEAT, PEType.WOOD};

    /**
     * The 5 resource types plus Unknown, for sending {@link SOCPlayerElements}:
     * {@link PEType#CLAY}, ORE, SHEEP, WHEAT, {@link PEType#WOOD},
     * {@link PEType#UNKNOWN_RESOURCE}.
     * @see #ELEM_RESOURCES
     * @since 2.0.00
     */
    public static final PEType[] ELEM_RESOURCES_WITH_UNKNOWN =
        {PEType.CLAY, PEType.ORE, PEType.SHEEP, PEType.WHEAT, PEType.WOOD, PEType.UNKNOWN_RESOURCE};

    /**
     * Classic board piece type elements, for sending {@link SOCPlayerElements}:
     * {@link #ELEM_PIECETYPES_SEA} without {@link PEType#SHIPS}.
     * @since 2.0.00
     */
    private static final PEType[] ELEM_PIECETYPES_CLASSIC =
        { PEType.ROADS, PEType.SETTLEMENTS, PEType.CITIES };

    /**
     * Sea board piece type elements, for sending {@link SOCPlayerElements}:
     * {@link #ELEM_PIECETYPES_CLASSIC} plus {@link PEType#SHIPS}.
     * @since 2.0.00
     */
    private static final PEType[] ELEM_PIECETYPES_SEA =
        { PEType.ROADS, PEType.SETTLEMENTS, PEType.CITIES, PEType.SHIPS };

    /**
     * For {@link #joinGame}; element types for last Settlement node, unknown resources,
     * {@link PEType#NUMKNIGHTS}, and classic piece types, for sending {@link SOCPlayerElements}:
     * {@link #ELEM_JOINGAME_WITH_PIECETYPES_SEA} without {@link PEType#SHIPS}.
     * @since 2.0.00
     */
    private static final PEType[] ELEM_JOINGAME_WITH_PIECETYPES_CLASSIC =
        { PEType.LAST_SETTLEMENT_NODE, PEType.UNKNOWN_RESOURCE, PEType.NUMKNIGHTS,
          PEType.ROADS, PEType.SETTLEMENTS, PEType.CITIES };

    /**
     * For {@link #joinGame}; element types for last Settlement node, unknown resources,
     * {@link PEType#NUMKNIGHTS}, and classic piece types, for sending {@link SOCPlayerElements}:
     * {@link #ELEM_JOINGAME_WITH_PIECETYPES_CLASSIC} plus {@link PEType#SHIPS}.
     * @since 2.0.00
     */
    private static final PEType[] ELEM_JOINGAME_WITH_PIECETYPES_SEA =
        { PEType.LAST_SETTLEMENT_NODE, PEType.UNKNOWN_RESOURCE, PEType.NUMKNIGHTS,
          PEType.ROADS, PEType.SETTLEMENTS, PEType.CITIES, PEType.SHIPS };

    /**
     * For {@link #joinGame}; {@link SOCGameElements} types for number of development cards,
     * number of rounds played, and player numbers for first player, longest road, largest army.
     * @since 2.0.00
     */
    private static final GEType[] ELEM_JOINGAME_DEVCARDS_ROUNDS_PLNUMS_FIRST_LONGEST_LARGEST =
        { GEType.DEV_CARD_COUNT, GEType.ROUND_COUNT, GEType.FIRST_PLAYER,
          GEType.LONGEST_ROAD_PLAYER, GEType.LARGEST_ARMY_PLAYER };

    /**
     * Game message handler for {@link SOCGameHandler}, shared by all game instances of this type.
     * @since 2.0.00
     */
    private final SOCGameMessageHandler gameMessageHandler;

    public SOCGameHandler(final SOCServer server)
    {
        super(server);
        gameMessageHandler = new SOCGameMessageHandler(server, this);
    }

    // javadoc inherited from GameHandler
    public GameMessageHandler getMessageHandler()
    {
        return gameMessageHandler;
    }

    // javadoc inherited from GameHandler
    public boolean processDebugCommand
        (final Connection debugCli, final SOCGame ga, final String dcmd, final String dcmdU)
    {
        if (dcmdU.startsWith("RSRCS:"))
        {
            debugGiveResources(debugCli, dcmd, ga);
            return true;
        }
        else if (dcmdU.startsWith("DEV:"))
        {
            debugGiveDevCard(debugCli, dcmd, ga);
            return true;
        }
        else if (dcmd.charAt(0) != '*')
        {
            return false;
        }

        if (dcmdU.startsWith(DEBUG_CMD_FREEPLACEMENT))
        {
            processDebugCommand_freePlace(debugCli, ga, dcmd.substring(DEBUG_CMD_FREEPLACEMENT.length()).trim());
            return true;
        } else if (dcmdU.startsWith(DEBUG_CMD_PFX_SCENARIO))
        {
            processDebugCommand_scenario(debugCli, ga, dcmd.substring(DEBUG_CMD_PFX_SCENARIO.length()).trim());
            return true;
        } else {
            return false;
        }
    }

    // javadoc inherited from GameHandler
    public final String[] getDebugCommandsHelp()
    {
        return SOC_DEBUG_COMMANDS_HELP;
    }

    /**
     * Process the <tt>*FREEPLACE*</tt> Free Placement debug command.
     * Can turn it off at any time, but can only turn it on during
     * your own turn after rolling (during game state {@link SOCGame#PLAY1}).
     * @param c   Connection (client) sending this message
     * @param ga  Game to which this applies; not null
     * @param arg  1 or 0, to turn on or off, or empty string or
     *    null to print current value
     * @since 1.1.12
     */
    final void processDebugCommand_freePlace
        (final Connection c, final SOCGame ga, final String arg)
    {
        final String gaName = ga.getName();
        final boolean wasInitial = ga.isInitialPlacement();
        final boolean ppValue = ga.isDebugFreePlacement();

        final boolean ppWanted;
        if ((arg == null) || (arg.length() == 0))
            ppWanted = ppValue;
        else
            ppWanted = arg.equals("1");

        if (ppValue != ppWanted)
        {
            if (! ppWanted)
            {
                try
                {
                    ga.setDebugFreePlacement(false);
                }
                catch (IllegalStateException e)
                {
                    if (wasInitial)
                    {
                        srv.messageToPlayer
                          (c, gaName, "* To exit this debug mode, all players must have either");
                        srv.messageToPlayer
                          (c, gaName, "  1 settlement and 1 road, or all must have at least 2 of each.");
                    } else {
                        srv.messageToPlayer
                          (c, gaName, "* Could not exit this debug mode: " + e.getMessage());
                    }
                    return;  // <--- early return ---
                }
            } else {
                if (c.getVersion() < SOCDebugFreePlace.VERSION_FOR_DEBUGFREEPLACE)
                {
                    srv.messageToPlayer
                        (c, gaName, "* Requires client version "
                         + Version.version(SOCDebugFreePlace.VERSION_FOR_DEBUGFREEPLACE)
                         + " or newer.");
                    return;  // <--- early return ---
                }
                SOCPlayer cliPl = ga.getPlayer(c.getData());
                if (cliPl == null)
                    return;  // <--- early return ---
                if (ga.getCurrentPlayerNumber() != cliPl.getPlayerNumber())
                {
                    srv.messageToPlayer
                        (c, gaName, "* Can do this only on your own turn.");
                    return;  // <--- early return ---
                }
                if ((ga.getGameState() != SOCGame.PLAY1)
                    && ! ga.isInitialPlacement())
                {
                    srv.messageToPlayer
                        (c, gaName, "* Can do this only after rolling the dice.");
                    return;  // <--- early return ---
                }

                ga.setDebugFreePlacement(true);
            }
        }

        srv.messageToPlayer
            (c, gaName, "- Free Placement mode is "
             + (ppWanted ? "ON -" : "off -" ));

        if (ppValue != ppWanted)
        {
            srv.messageToPlayer(c, new SOCDebugFreePlace(gaName, ga.getCurrentPlayerNumber(), ppWanted));
            if (wasInitial && ! ppWanted)
            {
                if (! checkTurn(c, ga))
                {
                    // Player changed (or play started), announce new player.
                    sendTurn(ga, false);
                } else {
                    sendGameState(ga, false, false);
                }
            }
        }
    }

    /**
     * Process any {@code *SCEN*} scenario debug commands.
     *
     *<H5>Currently recognized commands, per scenario:</H5>
     *<UL>
     *  <LI> <B>{@link SOCGameOption#K_SC_FTRI SC_FTRI}:</B>
     *    <UL>
     *      <LI> giveport #typenum #placeflag player
     *    </UL>
     *<UL>
     * If you add a debug command, also update {@link #SOC_DEBUG_COMMANDS_HELP}.
     *
     * @param c   Connection (client) sending this message
     * @param ga  Game to which this applies; not null
     * @param argStr  Debug command string from the user.
     *     Caller must remove prefix {@link #DEBUG_CMD_PFX_SCENARIO} and call {@link String#trim()}.
     *     Returns immediately if "".
     * @since 2.0.00
     */
    private final void processDebugCommand_scenario
        (final Connection c, final SOCGame ga, final String argStr)
    {
        if (argStr.isEmpty())
            return;

        final String gaName = ga.getName();

        if (ga.getGameOptionStringValue("SC") == null)
        {
            srv.messageToPlayer(c, gaName, "This game has no scenario");
            return;
        }
        if (! ga.isGameOptionSet(SOCGameOption.K_SC_FTRI))
        {
            srv.messageToPlayer(c, gaName, "This scenario has no debug commands");
            return;
        }

        // Tokenize the command arguments:
        // Don't use string.split("\\s+") because the last argument might be a player name with a space,
        // and "un-splitting" isn't easy
        StringTokenizer st = new StringTokenizer(argStr);
        if (! st.hasMoreTokens())
            return;  // unlikely: argStr was already trimmed and then checked length != 0

        final String subCmd = st.nextToken();

        // _SC_FTRI debug commands:
        if (subCmd.equalsIgnoreCase("giveport"))
        {
            // giveport #typenum #placeflag player

            boolean parseOK = false;
            int ptype = 0;
            boolean placeNow = false;
            SOCPlayer pl = null;

            try
            {
                ptype = Integer.parseInt(st.nextToken());
                int i = Integer.parseInt(st.nextToken());
                placeNow = (i == 1);
                if (placeNow || (i == 0))  // must be 0 or 1
                {
                    parseOK = (ptype >= SOCBoard.MISC_PORT) && (ptype <= SOCBoard.WOOD_PORT);
                    if (parseOK)
                    {
                        // get all of the rest for player name, by choosing an unlikely delimiter character
                        String plName = st.nextToken(Character.toString( (char) 1 )).trim();
                        pl = debug_getPlayer(c, ga, plName);
                        if (pl == null)
                            return;  // debug_getPlayer has sent not-found message
                    }
                }
            }
            catch (NumberFormatException e) {}
            catch (NoSuchElementException e) { parseOK = false; }  // not enough tokens; can occur at name when parseOK.

            if (! parseOK)
            {
                srv.messageToPlayer(c, gaName, "### Usage: giveport #typenum #placeflag player");
                srv.messageToPlayer(c, gaName, "### typenum: 0 for 3:1 port, or 1 to 5 (clay, ore, sheep, wheat, wood)");
                srv.messageToPlayer(c, gaName, "### placeflag: 1 to force placement now, 0 to add to inventory");
                return;
            }

            // Note: some logic from SOCGame.removePort(..); update there if this changes.
            // Message-send logic is from playerEvent(..).
            if (placeNow)
            {
                if ((ga.getCurrentPlayerNumber() != pl.getPlayerNumber())
                    || (pl.getPortMovePotentialLocations(false) == null))
                {
                    srv.messageToPlayer(c, gaName, "Player must be current and have a potential location for the port");
                    return;
                }

                // Fake placement off-board so we can call ga.removePort,
                // which will handle game states and notification, at a
                // vertical edge just past the side of the board: 0x113, 0x115, ...
                final int edge = (ga.getBoard().getBoardWidth() + 2) | 0x101;
                ga.placePort(null, edge, ptype);
                ga.removePort(pl, edge);
                // removePort calls gameEventListener.playerEvent(REMOVED_TRADE_PORT),
                // which sends some messages but not GAMESTATE
                sendGameState(ga);
            } else {
                pl.getInventory().addItem
                    (SOCInventoryItem.createForScenario(ga, -ptype, true, false, false, ! placeNow));
                srv.messageToGame(gaName, new SOCInventoryItemAction
                    (gaName, pl.getPlayerNumber(), SOCInventoryItemAction.ADD_PLAYABLE, -ptype, false, false, true));
            }

        } else {
            srv.messageToPlayer(c, gaName, "Unknown debug command: " + subCmd);
        }
    }

    final public void calcGameClientFeaturesRequired(SOCGame ga)
    {
        SOCFeatureSet fs = null;

        if (ga.isGameOptionSet("SBL"))
        {
            fs = new SOCFeatureSet((String) null);
            fs.add(SOCFeatureSet.CLIENT_SEA_BOARD);
        }

        if (ga.maxPlayers > 4)
        {
            if (fs == null)
                fs = new SOCFeatureSet((String) null);
            fs.add(SOCFeatureSet.CLIENT_6_PLAYERS);
        }

        String scKey = ga.getGameOptionStringValue("SC");
        if (scKey != null)
        {
            if (fs == null)
                fs = new SOCFeatureSet((String) null);
            SOCScenario sc = SOCScenario.getScenario(scKey);
            int scVers = (sc != null) ? sc.minVersion : Integer.MAX_VALUE;
            fs.add(SOCFeatureSet.CLIENT_SCENARIO_VERSION, scVers);
        }

        if (fs != null)
            ga.setClientFeaturesRequired(fs);
    }

    /**
     * Make sure it's the player's turn.
     *
     * @param c  the connection for player
     * @param ga the game
     *
     * @return true if it is the player's turn;
     *         false if another player's turn, or if this player isn't in the game
     */
    final boolean checkTurn(Connection c, SOCGame ga)
    {
        if ((c == null) || (ga == null))
            return false;

        try
        {
            return (ga.getCurrentPlayerNumber() == ga.getPlayer(c.getData()).getPlayerNumber());
        }
        catch (Throwable th)
        {
            return false;
        }
    }

    /**
     * Pre-checking already done, end the current player's turn in this game.
     * Alter game state and send messages to players.
     * (Clear all the Ask Special Building, Reset Board Request, and Trade Offer flags; send Game State; send Turn).
     *<P>
     * Calls {@link SOCGame#endTurn()}, which may also end the game.
     * On the 6-player board, this may begin the {@link SOCGame#SPECIAL_BUILDING Special Building Phase},
     * or end a player's placements during that phase.
     * Otherwise, calls {@link #sendTurn(SOCGame, boolean)} and begins
     * the next player's turn.
     *<P>
     * Assumes:
     * <UL>
     * <LI> ga.canEndTurn already called, to validate player
     * <LI> ga.takeMonitor already called (not the same as {@link SOCGameList#takeMonitorForGame(String)})
     * <LI> gamelist.takeMonitorForGame is NOT called, we do NOT have that monitor
     * </UL>
     *<P>
     * As a special case, endTurn is used to begin the Special Building Phase during the
     * start of a player's own turn, if permitted.  (Added in 1.1.09)
     *<P>
     * A simplified version of this logic (during initial placement) is used in
     * {@link SOCGameMessageHandler#handlePUTPIECE(SOCGame, Connection, SOCPutPiece)}.
     *
     * @param ga Game to end turn
     * @param pl Current player in <tt>ga</tt>, or null. Not needed except in SPECIAL_BUILDING.
     *           If null, will be determined within this method.
     * @param callEndTurn  Almost always true; if false, don't call {@link SOCGame#endTurn()}
     *           because it was called before calling this method.
     *           If false, be sure to set {@code pl} to the player whose turn it was before {@code endTurn()} was called.
     * @since 1.1.00
     */
    void endGameTurn(SOCGame ga, SOCPlayer pl, final boolean callEndTurn)
    {
        // Reminder: if this method's logic is changed or added to,
        // please also look at SOCGameMessageHandler.handlePUTPIECE
        // to see if the simplified version there should also be
        // updated.

        final String gname = ga.getName();

        if (ga.getGameState() == SOCGame.SPECIAL_BUILDING)
        {
            if (pl == null)
                pl = ga.getPlayer(ga.getCurrentPlayerNumber());
            pl.setAskedSpecialBuild(false);
            srv.messageToGame(gname, new SOCPlayerElement
                (gname, pl.getPlayerNumber(), SOCPlayerElement.SET, PEType.ASK_SPECIAL_BUILD, 0));
        }

        final boolean hadBoardResetRequest = (-1 != ga.getResetVoteRequester());

        /**
         * End the Turn:
         */
        if (callEndTurn)
        {
            ga.endTurn();  // May set state to OVER, if new player has enough points to win.
                           // May begin or continue the Special Building Phase.
        }

        /**
         * Send the results out:
         */

        if (hadBoardResetRequest)
        {
            // Cancel voting at end of turn
            srv.messageToGame(gname, new SOCResetBoardReject(gname));
        }

        /**
         * clear any trade offers
         */
        srv.gameList.takeMonitorForGame(gname);
        if (ga.clientVersionLowest >= SOCClearOffer.VERSION_FOR_CLEAR_ALL)
        {
            srv.messageToGameWithMon(gname, new SOCClearOffer(gname, -1));
        } else {
            for (int i = 0; i < ga.maxPlayers; i++)
                srv.messageToGameWithMon(gname, new SOCClearOffer(gname, i));
        }
        srv.gameList.releaseMonitorForGame(gname);

        /**
         * send new state number; if game is now OVER,
         * also send end-of-game messages.
         * Send whose turn it is.
         */
        sendTurn(ga, false);
        if (ga.getGameState() == SOCGame.SPECIAL_BUILDING)
            srv.messageToGameKeyed
                (ga, true, "action.sbp.turn.to.place", ga.getPlayer(ga.getCurrentPlayerNumber()).getName());
                // "Special building phase: {0}''s turn to place."
    }

    /**
     * Try to force-end the current player's turn in this game.
     * Alter game state and send messages to players.
     * Will call {@link #endGameTurn(SOCGame, SOCPlayer, boolean)} if appropriate.
     * Will send gameState and current player (turn) to clients.
     *<P>
     * If the current player has lost connection, send the {@link SOCLeaveGame LEAVEGAME}
     * message out <b>before</b> calling this method.
     *<P>
     * Assumes, as {@link #endGameTurn(SOCGame, SOCPlayer, boolean)} does:
     * <UL>
     * <LI> ga.canEndTurn already called, returned false
     * <LI> ga.takeMonitor already called (not the same as {@link SOCGameList#takeMonitorForGame(String)})
     * <LI> gamelist.takeMonitorForGame is NOT called, we do NOT have that monitor
     * </UL>
     * @param ga Game to force end turn
     * @param plName Current player's name. Needed because if they have been disconnected by
     *          {@link #leaveGame(SOCGame, Connection, boolean, boolean)},
     *          their name within game object is already null.
     * @return true if the turn was ended and game is still active;
     *          false if we find that all players have left and
     *          the gamestate has been changed here to {@link SOCGame#OVER}.
     *
     * @see #endGameTurnOrForce(SOCGame, int, String, Connection, boolean)
     * @see SOCGame#forceEndTurn()
     * @since 1.1.00
     */
    private final boolean forceEndGameTurn(SOCGame ga, final String plName)
    {
        final String gaName = ga.getName();
        final int cpn = ga.getCurrentPlayerNumber();
        final int endFromGameState = ga.getGameState();

        SOCPlayer cp = ga.getPlayer(cpn);
        if (cp.hasAskedSpecialBuild())
        {
            cp.setAskedSpecialBuild(false);
            srv.messageToGame(gaName, new SOCPlayerElement
                (gaName, cpn, SOCPlayerElement.SET, PEType.ASK_SPECIAL_BUILD, 0));
        }

        final SOCForceEndTurnResult res = ga.forceEndTurn();
            // State now hopefully PLAY1, or SPECIAL_BUILDING;
            // also could be initial placement (START1A or START2A or START3A).
        if (SOCGame.OVER == ga.getGameState())
            return false;  // <--- Early return: All players have left ---

        /**
         * Report any resources lost or gained.
         * See also forceGamePlayerDiscardOrGain for same reporting code.
         */
        SOCResourceSet resGainLoss = res.getResourcesGainedLost();
        if (resGainLoss != null)
        {
            /**
             * If gold hex or returning resources to player (not discarding), report actual types/amounts.
             * For discard, tell the discarding player's client that they discarded the resources,
             * tell everyone else that the player discarded unknown resources.
             */
            if (! res.isLoss())
            {
                if ((endFromGameState == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                    || (endFromGameState == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE))
                {
                    // Send SOCPlayerElement messages, "gains" text
                    reportRsrcGainGold(ga, cp, cpn, resGainLoss, true, false);
                } else {
                    // Send SOCPlayerElement messages
                    reportRsrcGainLoss(gaName, resGainLoss, false, false, cpn, -1, null);
                }
            } else {
                Connection c = srv.getConnection(plName);
                if ((c != null) && c.isConnected())
                    reportRsrcGainLoss(gaName, resGainLoss, true, true, cpn, -1, c);
                int totalRes = resGainLoss.getTotal();
                srv.messageToGameExcept
                    (gaName, c, new SOCPlayerElement
                        (gaName, cpn, SOCPlayerElement.LOSE, PEType.UNKNOWN_RESOURCE, totalRes, true),
                     true);
                srv.messageToGameKeyed(ga, true, "action.discarded", plName, totalRes);  //  "{0} discarded {1} resources."
            }
        }

        /**
         * report any dev-card or item returned to player's hand
         */
        final SOCInventoryItem itemCard = res.getReturnedInvItem();
        SOCInventoryItemAction retItemActionMsg = null;
            // details of item returning to player's hand, maybe send to other players too

        if (itemCard != null)
        {
            Connection c = srv.getConnection(plName);
            if ((c != null) && c.isConnected())
            {
                if (itemCard instanceof SOCDevCard)
                {
                    int card = itemCard.itype;
                    if ((card == SOCDevCardConstants.KNIGHT)
                        && (c.getVersion() < SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES))
                        card = SOCDevCardConstants.KNIGHT_FOR_VERS_1_X;
                    srv.messageToPlayer(c, new SOCDevCardAction(gaName, cpn, SOCDevCardAction.ADD_OLD, card));
                } else {
                    retItemActionMsg = new SOCInventoryItemAction
                        (gaName, cpn,
                         (itemCard.isPlayable() ? SOCInventoryItemAction.ADD_PLAYABLE : SOCInventoryItemAction.ADD_OTHER),
                         itemCard.itype, itemCard.isKept(), itemCard.isVPItem(), itemCard.canCancelPlay);
                    srv.messageToPlayer(c, retItemActionMsg);
                }
            }

            boolean announceAsInvItemAction = false;  // Announce item to game with same retItemActionMsg sent to player?
            boolean announceAsUnknown = true;  // Announce this item to game as an unknown dev card type?
                // Ignored if announceAsInvItemAction true.

            if (! (itemCard instanceof SOCDevCard))
            {
                // SOCInventoryItem: Add any new kinds here, to announce to all players.
                // If it needs a special message, do so and set announceAsUnknown = false
                // If it's private and doesn't need a special message, set handled = true and let it announce as unknown
                boolean handled = false;

                if (ga.isGameOptionSet(SOCGameOption.K_SC_FTRI))
                {
                    // endFromGameState is PLACING_INV_ITEM.
                    // "Gift port" item details are public, send return message to whole game:
                    handled = true;
                    announceAsInvItemAction = true;
                    announceAsUnknown = false;
                }

                // Fallback:
                if (! handled)
                    System.err.println
                        ("forceEndGameTurn: Unhandled inventory item type " + itemCard.itype
                         + " class " + itemCard.getClass());
            }

            if (announceAsInvItemAction)
            {
                srv.messageToGameExcept(gaName, c, retItemActionMsg, true);
            }
            else if (announceAsUnknown)
            {
                if (ga.clientVersionLowest >= SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES)
                {
                    srv.messageToGameExcept
                        (gaName, c, new SOCDevCardAction
                            (gaName, cpn, SOCDevCardAction.ADD_OLD, SOCDevCardConstants.UNKNOWN), true);
                } else {
                    srv.messageToGameForVersionsExcept
                        (ga, -1, SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES - 1,
                         c, new SOCDevCardAction
                             (gaName, cpn, SOCDevCardAction.ADD_OLD, SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X), true);
                    srv.messageToGameForVersionsExcept
                        (ga, SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES, Integer.MAX_VALUE,
                         c, new SOCDevCardAction
                             (gaName, cpn, SOCDevCardAction.ADD_OLD, SOCDevCardConstants.UNKNOWN), true);
                }

                srv.messageToGameKeyed(ga, true, "forceend.devcard.returned", plName);
                    // "{0}''s just-played development card was returned."
            }
        }

        /**
         * For initial placements, we don't end turns as normal.
         * (Player number may go forward or backwards, new state isn't ROLL_OR_CARD, etc.)
         * Update clients' gamestate, but don't call endGameTurn.
         */
        final int forceRes = res.getResult();
        if ((forceRes == SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADV)
            || (forceRes == SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADVBACK))
        {
            if (res.didUpdateFP() || res.didUpdateLP())
            {
                final int fpn = ga.getFirstPlayer();
                final SOCMessage msg =
                    (ga.clientVersionLowest >= SOCGameElements.MIN_VERSION)
                    ? new SOCGameElements(gaName, GEType.FIRST_PLAYER, fpn)
                    : new SOCFirstPlayer(gaName, fpn);

                // will cause clients to recalculate lastPlayer too
                srv.messageToGame(gaName, msg);
            }
            sendTurn(ga, false);
            return true;  // <--- Early return ---
        }

        /**
         * If the turn can now end, proceed as if player requested it.
         * Otherwise, send current gamestate.  We'll all wait for other
         * players to send discard messages, and afterwards this turn can end.
         */
        if (ga.canEndTurn(cpn))
            endGameTurn(ga, null, true);  // could force gamestate to OVER, if a client leaves
        else
            sendGameState(ga, false, false);

        return (ga.getGameState() != SOCGame.OVER);
    }

    /**
     * Client has been approved to join game; send JOINGAMEAUTH and the entire state of the game to client.
     * Unless {@code isTakingOver}, announce {@link SOCJoinGame} client join event to other players.
     *<P>
     * Does not add the client to the game's or server's list of players,
     * that should be done before calling this method.
     *<P>
     * Assumes {@link SOCServer#connectToGame(Connection, String, Map, SOCGame)} was already called.
     *<P>
     * Assumes NEWGAME (or NEWGAMEWITHOPTIONS) has already been sent out.
     * The game's first message<B>*</B> sent to the connecting client is JOINGAMEAUTH, unless isReset.
     *<P>
     * Among other messages, player names are sent via SITDOWN, and pieces on board
     * sent by PUTPIECE.  See comments here for further details.
     * If {@code isTakingOver}, some details are sent by calling
     * {@link #sitDown_sendPrivateInfo(SOCGame, Connection, int, boolean)}.
     * The group of messages sent here ends with GAMEMEMBERS, SETTURN and GAMESTATE.
     * If state is {@link SOCGame#OVER}: Right after sending GAMESTATE, calls
     * {@link #sendGameStateOVER(SOCGame, Connection) sendGameStateOver(gameData, c)}.
     *<P>
     * After all that is sent, the entire game is sent a JOINGAME for the new game member.
     *<P>
     * *<B>I18N:</B> If the game has a {@link SOCScenario} and the client needs scenario info or localized strings
     * for the scenario name and description, {@link SOCScenarioInfo} or {@link SOCLocalizedStrings} is
     * sent before JOINGAMEAUTH.  This handles i18n and scenarios added or changed between the client's
     * and server's versions.
     *
     * @param gameData Game to join
     * @param c        The connection of joining client
     * @param isReset  Game is a board-reset of an existing game.  This is always false when
     *          called from SOCServer instead of from inside the SOCGameHandler.
     * @param isLoading  Game is being reloaded from snapshot by {@code c}'s request; state is {@link SOCGame#LOADING}
     * @param isTakingOver  If true, client is re-joining; {@code c} replaces an earlier connection which
     *          is defunct/frozen because of a network problem. Also true when a human player joins a
     *          game being reloaded and has the same nickname as a player there.
     *          If {@code isTakingOver}, sends {@code c} their hand's private info for game in progress.
     * @see SOCServer#createOrJoinGameIfUserOK(Connection, String, String, String, Map)
     * @since 1.1.00
     */
    @SuppressWarnings("unchecked")  // for new ArrayList<SOCSpecialItem>[]
    public void joinGame
        (final SOCGame gameData, final Connection c,
         final boolean isReset, final boolean isLoading, final boolean isTakingOver)
    {
        boolean hasRobot = false;  // If game's already started, true if any bot is seated (can be taken over)
        final String gameName = gameData.getName(), cliName = c.getData();
        final int gameState = gameData.getGameState(), cliVers = c.getVersion();

        if (! isReset)
        {
            // First, send updated scenario info or localized strings if needed
            // (SOCScenarioInfo or SOCLocalizedStrings); checks c.getVersion(), scd.scenariosInfoSent etc.
            final String gameScen = gameData.getGameOptionStringValue("SC");
            if (gameScen != null)
                srv.sendGameScenarioInfo(gameScen, null, c, false, false);

            // Now, join game
            final SOCBoard board = gameData.getBoard();
            final int bh, bw;
            final int[] boardVS;
            if (board instanceof SOCBoardLarge)
            {
                bh = board.getBoardHeight();
                bw = board.getBoardWidth();
                boardVS = ((SOCBoardLarge) board).getAddedLayoutPart("VS");  // might be null, is OK
            } else {
                bh = bw = 0;
                boardVS = null;
            }
            c.put(new SOCJoinGameAuth(gameName, bh, bw, boardVS));

            final SOCClientData scd = (SOCClientData) c.getAppData();
            if ((! scd.sentPostAuthWelcome) || (c.getVersion() < SOCStringManager.VERSION_FOR_I18N))
            {
                c.put(SOCStatusMessage.toCmd
                       (SOCStatusMessage.SV_OK, srv.getClientWelcomeMessage(c)));  // "Welcome to Java Settlers of Catan!"
                scd.sentPostAuthWelcome = true;
            }
        }

        /**
         * When sending seated player info and this client is loading a saved game:
         * if client isn't a player in the game, give them option to sit at any player's seat.
         */
        boolean allSeatsBots;
        if (isLoading)
        {
            allSeatsBots = true;
            for (int pn = 0; pn < gameData.maxPlayers; ++pn)
            {
                if ((! gameData.isSeatVacant(pn))
                    && gameData.getPlayer(pn).getName().equals(cliName))
                {
                    allSeatsBots = false;
                    break;
                }
            }
        } else {
            allSeatsBots = false;
        }

        if (cliVers >= SOCSetSeatLock.VERSION_FOR_ALL_SEATS)
            srv.messageToPlayer(c, new SOCSetSeatLock(gameName, gameData.getSeatLocks()));

        for (int i = 0; i < gameData.maxPlayers; i++)
        {
            /**
             * send the seat lock information, if client needs per-seat messages.
             */
            if (cliVers < SOCSetSeatLock.VERSION_FOR_ALL_SEATS)
            {
                final SOCGame.SeatLockState sl = gameData.getSeatLock(i);
                // old client doesn't have CLEAR_ON_RESET
                srv.messageToPlayer(c, new SOCSetSeatLock
                    (gameName, i,
                     (sl != SOCGame.SeatLockState.CLEAR_ON_RESET) ? sl : SOCGame.SeatLockState.UNLOCKED));
            }

            /**
             * send them basic info on the already-seated players;
             * if isReset, don't send, because sitDown will
             * be sent from SOCServer.resetBoardAndNotify.
             */
            if (! isReset)
            {
                SOCPlayer pl = gameData.getPlayer(i);
                String plName = pl.getName();
                if ((plName != null) && ! gameData.isSeatVacant(i))
                {
                    boolean isRobot = pl.isRobot();
                    if ((gameState == SOCGame.LOADING)
                        && ! (isRobot || allSeatsBots || isLoading || srv.gameList.isMember(plName, gameName)))
                    {
                        // To make sure joining client shows "Take Over" buttons for unclaimed seats
                        // left by human players when game was saved, show them as bots
                        isRobot = true;
                    }
                    c.put(SOCSitDown.toCmd(gameName, plName, i, isRobot || allSeatsBots));

                    if (isRobot)
                        hasRobot = true;
                }
            }
        }

        /**
         * Send board layout info.
         * Optimization: If the game is still forming, client already has data for the empty board.
         */
        if ((gameState != SOCGame.NEW)
            || (cliVers < SOCBoardLayout.VERSION_FOR_OMIT_IF_EMPTY_NEW_GAME))
        {
            c.put(getBoardLayoutMessage(gameData));
            //    No need to catch IllegalArgumentException:
            //    Since game is already started, getBoardLayoutMessage has previously
            //    been called for the creating player, and the board encoding is OK.
        }

        for (final SOCPotentialSettlements psMsg : gatherBoardPotentials(gameData, cliVers))
            c.put(psMsg);

        /**
         * Any other misc data to send if game hasn't started yet:
         */
        if (gameState < SOCGame.START1A)
        {
            if (gameData.isGameOptionSet(SOCGameOption.K_SC_CLVI))
                // Board's general supply of cloth:
                c.put(SOCPlayerElement.toCmd
                    (gameName, -1, SOCPlayerElement.SET,
                     PEType.SCENARIO_CLOTH_COUNT, ((SOCBoardLarge) (gameData.getBoard())).getCloth()));
                // Individual villages' cloth counts are sent soon below.
                // If game has started, will send board's cloth count after per-player info and putpieces.
        }

        /**
         * If normal game play has started:
         * _SC_CLVI: Send updated Cloth counts for any changed villages.
         * _SC_FTRI: Send any changed Special Edges.
         */
        if (gameData.hasSeaBoard && (gameState >= SOCGame.ROLL_OR_CARD))
        {
            final SOCBoardLarge bl = (SOCBoardLarge) gameData.getBoard();

            // SC_CLVI:
            final HashMap<Integer, SOCVillage> villages = bl.getVillages();
            if (villages != null)
                for (final SOCVillage vi : villages.values())
                {
                    final int cl = vi.getCloth();
                    if (cl != SOCVillage.STARTING_CLOTH)
                        srv.messageToGame(gameName, new SOCPieceValue
                            (gameName, SOCPlayingPiece.VILLAGE, vi.getCoordinates(), cl, 0));
                }

            // SC_FTRI:
            boolean sendEdgeChanges = bl.hasSpecialEdges();
            if (! sendEdgeChanges)
            {
                // In case they've all been removed already during game play,
                // check the board for any Special Edge layout part
                for (String ap : SOCBoardLarge.SPECIAL_EDGE_LAYOUT_PARTS)
                {
                    if (bl.getAddedLayoutPart(ap) != null)
                    {
                        sendEdgeChanges = true;
                        break;
                    }
                }
            }
            if (sendEdgeChanges)
                joinGame_sendBoardSpecialEdgeChanges(gameData, bl, c);
        }

        /**
         * Send the current player number.
         * Before v2.0.00, this wasn't sent so early; was sent
         * just before SOCGameState and the "joined the game" text.
         * This earlier send has been tested against 1.1.07 (released 2009-10-31).
         */
        if (cliVers >= SOCGameElements.MIN_VERSION)
            c.put(new SOCGameElements
                (gameName, GEType.CURRENT_PLAYER, gameData.getCurrentPlayerNumber()));
        else
            c.put(SOCSetTurn.toCmd(gameName, gameData.getCurrentPlayerNumber()));

        /**
         * Send the game's Special Item info, if any, if game has started:
         */
        final String[] gameSITypes;
        if (gameState >= SOCGame.START1A)
        {
            Set<String> ty = gameData.getSpecialItemTypes();
            gameSITypes = (ty != null) ? ty.toArray(new String[ty.size()]) : null;
        } else {
            gameSITypes = null;
        }

        /**
         * Holds any special items shared between game and player. Those must be sent just once, not twice,
         * when per-game and then per-player special item info is sent. Per-player loop should check
         * {@code gameSItoPlayer.get(typeKey)[playerNumber].get(itemIndex)}; unused per-player lists
         * and typeKeys are null, so check each dereference; also check itemIndex versus list length.
         */
        final HashMap<String, ArrayList<SOCSpecialItem>[]> gameSItoPlayer;

        if (gameSITypes == null)
        {
            gameSItoPlayer = null;
        } else {
            gameSItoPlayer = new HashMap<String, ArrayList<SOCSpecialItem>[]>();

            for (int i = 0; i < gameSITypes.length; ++i)
            {
                final String tkey = gameSITypes[i];
                ArrayList<SOCSpecialItem> gsi = gameData.getSpecialItems(tkey);
                if (gsi == null)
                    continue;  // shouldn't happen

                final int L = gsi.size();
                for (int gi = 0; gi < L; ++gi)  // use this loop type to avoid ConcurrentModificationException if locking bug
                {
                    final SOCSpecialItem si = gsi.get(gi);
                    if (si == null)
                    {
                        c.put(new SOCSetSpecialItem(gameName, SOCSetSpecialItem.OP_CLEAR, tkey, gi, -1, -1));
                        continue;
                    }

                    int pi = -1;  // player index, or -1: if pl != null, must search pl's items for a match
                    final SOCPlayer pl = si.getPlayer();
                    if (pl != null)
                    {
                        ArrayList<SOCSpecialItem> iList = pl.getSpecialItems(tkey);
                        if (iList != null)
                        {
                            for (int k = 0; k < iList.size(); ++k)
                            {
                                if (si == iList.get(k))
                                {
                                    pi = k;
                                    break;
                                }
                            }
                        }
                    }

                    c.put(new SOCSetSpecialItem(gameData, SOCSetSpecialItem.OP_SET, tkey, gi, pi, si));

                    if (pi != -1)
                    {
                        // remember for use when sending per-player info

                        ArrayList<SOCSpecialItem>[] toAllPl = gameSItoPlayer.get(tkey);
                        if (toAllPl == null)
                        {
                            toAllPl = new ArrayList[gameData.maxPlayers];
                            gameSItoPlayer.put(tkey, toAllPl);
                        }

                        ArrayList<SOCSpecialItem> iList = toAllPl[pl.getPlayerNumber()];
                        if (iList == null)
                        {
                            iList = new ArrayList<SOCSpecialItem>();
                            toAllPl[pl.getPlayerNumber()] = iList;
                        }

                        int iLL = iList.size();
                        while (iLL <= pi)
                        {
                            iList.add(null);
                            ++iLL;
                        }

                        iList.set(pi, si);
                    }
                }
            }
        }

        /**
         * send the per-player information
         */
        for (int i = 0; i < gameData.maxPlayers; i++)
        {
            final SOCPlayer pl = gameData.getPlayer(i);
            final boolean isTakingOverThisSeat = isTakingOver && c.getData().equals(pl.getName());

            /**
             * send scenario info before any putpiece, so they know their
             * starting land areas and scenario events
             */
            int itm = pl.getSpecialVP();
            if (itm != 0)
            {
                srv.messageToPlayer(c, new SOCPlayerElement
                    (gameName, i, SOCPlayerElement.SET, PEType.SCENARIO_SVP, itm));

                ArrayList<SOCPlayer.SpecialVPInfo> svpis = pl.getSpecialVPInfo();
                if (svpis != null)
                    for (SOCPlayer.SpecialVPInfo svpi : svpis)
                        srv.messageToPlayer(c, new SOCSVPTextMessage(gameName, i, svpi.svp, c.getLocalized(svpi.desc)));
            }

            itm = pl.getPlayerEvents();
            if (itm != 0)
                srv.messageToPlayer(c, new SOCPlayerElement
                    (gameName, i, SOCPlayerElement.SET, PEType.PLAYEREVENTS_BITMASK, itm));

            itm = pl.getScenarioSVPLandAreas();
            if (itm != 0)
                srv.messageToPlayer(c, new SOCPlayerElement
                    (gameName, i, SOCPlayerElement.SET, PEType.SCENARIO_SVP_LANDAREAS_BITMASK, itm));

            itm = pl.getStartingLandAreasEncoded();
            if (itm != 0)
                srv.messageToPlayer(c, new SOCPlayerElement
                    (gameName, i, SOCPlayerElement.SET, PEType.STARTING_LANDAREAS, itm));

            itm = pl.getCloth();
            if (itm != 0)
                srv.messageToPlayer(c, new SOCPlayerElement
                    (gameName, i, SOCPlayerElement.SET, PEType.SCENARIO_CLOTH_COUNT, itm));

            // Send piece info even if player has left the game (pl.getName() == null).
            // This lets them see "their" pieces before srv.sitDown(), if they rejoin at same position.

            Enumeration<SOCPlayingPiece> piecesEnum = pl.getPieces().elements();
            while (piecesEnum.hasMoreElements())
            {
                SOCPlayingPiece piece = piecesEnum.nextElement();

                if (piece.getType() == SOCPlayingPiece.CITY)
                    c.put(SOCPutPiece.toCmd(gameName, i, SOCPlayingPiece.SETTLEMENT, piece.getCoordinates()));

                c.put(SOCPutPiece.toCmd(gameName, i, piece.getType(), piece.getCoordinates()));
            }

            // _SC_PIRI: special-case piece not part of getPieces
            {
                final SOCFortress piece = pl.getFortress();
                if (piece != null)
                {
                    final int coord = piece.getCoordinates(),
                              str   = piece.getStrength();

                    c.put(SOCPutPiece.toCmd(gameName, i, piece.getType(), coord));

                    if (str != SOCFortress.STARTING_STRENGTH)
                        c.put(new SOCPieceValue(gameName, SOCPlayingPiece.FORTRESS, coord, str, 0));
                }
            }

            // _SC_PIRI: for display, send count of warships only after SOCShip pieces are sent
            itm = pl.getNumWarships();
            if (itm != 0)
                srv.messageToPlayer(c, new SOCPlayerElement
                    (gameName, i, SOCPlayerElement.SET, PEType.SCENARIO_WARSHIP_COUNT, itm));

            /**
             * send node coord of the last settlement, resources,
             * knight cards played, number of playing pieces in hand
             */
            final int[] counts = new int[(gameData.hasSeaBoard) ? 7 : 6];
            counts[0] = pl.getLastSettlementCoord();
            counts[1] = pl.getResources().getTotal();  // will send with SOCPlayerElement.UNKNOWN
            counts[2] = pl.getNumKnights();
            counts[3] = pl.getNumPieces(SOCPlayingPiece.ROAD);
            counts[4] = pl.getNumPieces(SOCPlayingPiece.SETTLEMENT);
            counts[5] = pl.getNumPieces(SOCPlayingPiece.CITY);
            if (gameData.hasSeaBoard)
                counts[6] = pl.getNumPieces(SOCPlayingPiece.SHIP);
            if (cliVers >= SOCPlayerElements.MIN_VERSION)
            {
                c.put(new SOCPlayerElements
                    (gameName, i, SOCPlayerElement.SET,
                     (gameData.hasSeaBoard) ? ELEM_JOINGAME_WITH_PIECETYPES_SEA : ELEM_JOINGAME_WITH_PIECETYPES_CLASSIC,
                     counts));
            } else {
                c.put(SOCLastSettlement.toCmd(gameName, i, counts[0]));
                    // client too old for SOCPlayerElement.LAST_SETTLEMENT_NODE
                for (int j = 1; j < counts.length; ++j)
                    c.put(new SOCPlayerElement
                        (gameName, i, SOCPlayerElement.SET, ELEM_JOINGAME_WITH_PIECETYPES_CLASSIC[j], counts[j]));
            }

            if (pl.hasAskedSpecialBuild())
                c.put(new SOCPlayerElement
                    (gameName, i, SOCPlayerElement.SET, SOCPlayerElement.PEType.ASK_SPECIAL_BUILD, 1));

            if (! isTakingOverThisSeat)
            {
                final int numDevCards = pl.getInventory().getTotal();
                final int unknownType =
                    (cliVers >= SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES)
                    ? SOCDevCardConstants.UNKNOWN
                    : SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X;
                final SOCMessage cardUnknownMsg =
                    new SOCDevCardAction(gameName, i, SOCDevCardAction.ADD_OLD, unknownType);
                for (int j = 0; j < numDevCards; j++)
                    c.put(cardUnknownMsg);
            }

            if (gameSITypes != null)
            {
                // per-player Special Item info

                for (int j = 0; j < gameSITypes.length; ++j)
                {
                    final String tkey = gameSITypes[j];
                    ArrayList<SOCSpecialItem> plsi = pl.getSpecialItems(tkey);
                    if (plsi == null)
                        continue;  // shouldn't happen

                    // pi loop body checks gameSItoPlayer to see if already sent (object shared with game)
                    final ArrayList<SOCSpecialItem>[] toAllPl = gameSItoPlayer.get(tkey);
                    final ArrayList<SOCSpecialItem> iList = (toAllPl != null) ? toAllPl[i] : null;

                    final int L = plsi.size();
                    for (int pi = 0; pi < L; ++pi)  // use this loop type to avoid ConcurrentModificationException
                    {
                        final SOCSpecialItem si = plsi.get(pi);
                        if (si == null)
                        {
                            c.put(new SOCSetSpecialItem
                                    (gameName, SOCSetSpecialItem.OP_CLEAR, tkey, -1, pi, i));
                            continue;
                        }

                        if ((iList != null) && (iList.size() > pi) && (iList.get(pi) == si))
                            continue;  // already sent (shared with game)

                        c.put(new SOCSetSpecialItem(gameData, SOCSetSpecialItem.OP_SET, tkey, -1, pi, si));
                    }
                }
            }

            if ((i == 0) && (cliVers < SOCGameElements.MIN_VERSION))
            {
                // per-game data, send once; send here only if client is
                // too old to send together with other game elements,
                // otherwise send soon with longest road / largest army

                c.put(SOCFirstPlayer.toCmd(gameName, gameData.getFirstPlayer()));

                c.put(SOCDevCardCount.toCmd(gameName, gameData.getNumDevCards()));
            }

            c.put(SOCChangeFace.toCmd(gameName, i, pl.getFaceId()));

            if (i == 0)
            {
                // per-game data, send once

                c.put(SOCDiceResult.toCmd(gameName, gameData.getCurrentDice()));
            }

            // more per-player data to send after dice result

            sendTradeOffer(pl, c);
        }

        ///
        /// send dev card count, rounds count, first player, who has longest road and largest army
        ///
        final SOCPlayer lrPlayer = gameData.getPlayerWithLongestRoad(),
                        laPlayer = gameData.getPlayerWithLargestArmy();
        final int lrPlayerNum = (lrPlayer != null) ? lrPlayer.getPlayerNumber() : -1,
                  laPlayerNum = (laPlayer != null) ? laPlayer.getPlayerNumber() : -1;
        if (cliVers < SOCGameElements.MIN_VERSION)
        {
            c.put(SOCLongestRoad.toCmd(gameName, lrPlayerNum));
            c.put(SOCLargestArmy.toCmd(gameName, laPlayerNum));
        } else {
            c.put(new SOCGameElements
                (gameName, ELEM_JOINGAME_DEVCARDS_ROUNDS_PLNUMS_FIRST_LONGEST_LARGEST,
                 new int[]{ gameData.getNumDevCards(), gameData.getRoundCount(),
                     gameData.getFirstPlayer(), lrPlayerNum, laPlayerNum }
                 ));
        }

        /**
         * Any other misc data to send after per-player/pieces, if game has started:
         */
        if (gameState >= SOCGame.START1A)
        {
            if (gameData.isGameOptionSet(SOCGameOption.K_SC_CLVI))
                c.put(SOCPlayerElement.toCmd
                    (gameName, -1, SOCPlayerElement.SET,
                     PEType.SCENARIO_CLOTH_COUNT, ((SOCBoardLarge) (gameData.getBoard())).getCloth()));
        }

        /**
         * If we're rejoining and taking over a seat after a network problem,
         * send our resource and hand information.
         */
        if (isTakingOver && ! isLoading)
        {
            SOCPlayer cliPl = gameData.getPlayer(cliName);
            if (cliPl != null)
            {
                int pn = cliPl.getPlayerNumber();
                if ((pn != -1) && ! gameData.isSeatVacant(pn))
                    sitDown_sendPrivateInfo(gameData, c, pn, true);
            }
        }

        /**
         * Send chat recap; same sequence is in SOCServerMessageHandler.handleJOINCHANNEL_postAuth with
         * different message type
         */
        final SOCChatRecentBuffer buf = srv.gameList.getChatBuffer(gameName);
        {
            List<SOCChatRecentBuffer.Entry> recents;
            synchronized(buf)
            {
                recents = buf.getAll();
            }
            if (! recents.isEmpty())
            {
                c.put(new SOCGameTextMsg(gameName, SOCGameTextMsg.SERVER_FOR_CHAT,
                        c.getLocalized("member.join.recap_begin")));  // [:: ]"Recap of recent chat ::"
                for (SOCChatRecentBuffer.Entry e : recents)
                    c.put(new SOCGameTextMsg(gameName, e.nickname, e.text));
                c.put(new SOCGameTextMsg(gameName, SOCGameTextMsg.SERVER_FOR_CHAT,
                        c.getLocalized("member.join.recap_end")));    // [:: ]"Recap ends ::"
            }
        }

        String membersCommand = null;
        srv.gameList.takeMonitorForGame(gameName);

        /**
         * Almost done; send GAMEMEMBERS as a hint to client that we're almost ready for its input.
         * There's no new data in GAMEMEMBERS, because player names have already been sent by
         * the SITDOWN messages above.
         */
        try
        {
            membersCommand = SOCGameMembers.toCmd(gameName, srv.gameList.getMembers(gameName));
        }
        catch (Exception e)
        {
            D.ebugPrintln("Exception in SGH.joinGame (gameMembers) - " + e);
        }

        srv.gameList.releaseMonitorForGame(gameName);

        if (membersCommand != null)
            c.put(membersCommand);

        // before v2.0.00, current player number (SETTURN) was sent here,
        // between membersCommand and GAMESTATE.

        c.put(SOCGameState.toCmd(gameName, gameState));
        if (gameState == SOCGame.OVER)
            sendGameStateOVER(gameData, c);

        if (D.ebugOn)
            D.ebugPrintln("*** " + cliName + " joined the game " + gameName + " at "
                + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));

        if (isTakingOver && (gameState != SOCGame.LOADING))
        {
            return;
        }

        /**
         * Let everyone else know about the change
         */
        srv.messageToGame(gameName, new SOCJoinGame(cliName, "", SOCMessage.EMPTYSTR, gameName));
        if (isTakingOver)
        {
            return;
        }

        if ((! isReset) && (gameState >= SOCGame.START2A) && (gameState < SOCGame.OVER))
        {
            srv.messageToPlayerKeyed
                (c, gameName,
                 (hasRobot) ? "member.join.game.started.bots"  // "This game has started. To play, take over a robot."
                            : "member.join.game.started");     // "This game has started; no new players can sit down."
        }
    }

    /**
     * Players' potential settlements and related values.
     * Will return either 1 per player, or 1 for all players with {@code playerNumber} == -1:
     *<P>
     * If game hasn't started yet, each player's potentialSettlements are identical,
     * so gather that info once for all players (unless clients are too old for a message having PN -1).
     *<P>
     * Otherwise each player has a unique potential settlement list,
     * to populate legal sets before sending any of their PutPieces.
     *<P>
     * Before v2.3.00 this code was part of {@code joinGame}.
     * @param gameData  Game to gather potentials from
     * @param cliVers  Client version, or {@link Integer#MAX_VALUE} for latest version
     * @since 2.3.00
     */
    public static SOCPotentialSettlements[] gatherBoardPotentials
        (final SOCGame gameData, final int cliVers)
    {
        final String gameName = gameData.getName();
        final SOCPotentialSettlements[] ret;

        if ((gameData.getGameState() < SOCGame.START1A)
            && (cliVers >= SOCPotentialSettlements.VERSION_FOR_PLAYERNUM_ALL))
        {
            // Some boards may have multiple land areas.
            // See also below, and startGame which has very similar code.
            final HashSet<Integer> psSet;
            final HashSet<Integer>[] lan;
            final int pan;
            if (gameData.hasSeaBoard)
            {
                final SOCBoardLarge bl = (SOCBoardLarge) gameData.getBoard();
                lan = bl.getLandAreasLegalNodes();
                pan = bl.getStartingLandArea();
                psSet = (lan == null)
                    ? (gameData.getPlayer(0).getPotentialSettlements())
                    : null;  // send lan instead of psList
            } else {
                psSet = gameData.getPlayer(0).getPotentialSettlements();
                lan = null;
                pan = 0;
            }

            final SOCPotentialSettlements psMsg;
            if (lan == null)
            {
                psMsg = new SOCPotentialSettlements
                    (gameName, -1, new ArrayList<Integer>(psSet));
            } else {
                final List<Integer> psList = (psSet != null) ? new ArrayList<Integer>(psSet) : null;
                psMsg = new SOCPotentialSettlements
                    (gameName, -1, psList, pan, lan, SOCBoardAtServer.getLegalSeaEdges(gameData));
            }

            ret = new SOCPotentialSettlements[]{ psMsg };

        } else {
            // Game has started (initial placement or normal play),
            // and/or at least 1 client is too old for "all players" pn=-1 message

            ret = new SOCPotentialSettlements[gameData.maxPlayers];
            final int[][] lse = SOCBoardAtServer.getLegalSeaEdges(gameData);  // null except in SC_PIRI

            for (int pn = 0; pn < gameData.maxPlayers; ++pn)
            {
                final SOCPlayer pl = gameData.getPlayer(pn);
                final List<Integer> psList = new ArrayList<Integer>(pl.getPotentialSettlements());

                // Some boards may have multiple land areas.
                // See also above, and startGame which has very similar code.
                final HashSet<Integer>[] lan;
                if (gameData.hasSeaBoard && (pn == 0))
                {
                    // Send legal node info once, not per-player.
                    // Assumes all players have same legal nodes.
                    // Legal Sea Edges is sent once, as a list of all players' LSE, as part of pn=0 message.
                    final SOCBoardLarge bl = (SOCBoardLarge) gameData.getBoard();
                    lan = bl.getLandAreasLegalNodes();
                } else {
                    lan = null;
                }

                final int[][] plLse = (lse != null) ? (new int[][] { lse[pn] }) : null;
                final SOCPotentialSettlements psMsg;
                if (lan == null)
                {
                    if (lse == null)
                        psMsg = new SOCPotentialSettlements(gameName, pn, psList);
                    else
                        psMsg = new SOCPotentialSettlements(gameName, pn, psList, plLse);
                } else {
                    psMsg = new SOCPotentialSettlements(gameName, pn, psList, 0, lan, plLse);
                }
                ret[pn] = psMsg;
            }
        }

        return ret;
    }

    /**
     * Client is joining this game, which uses {@link SOCBoardLarge} with {@link SOCBoardLarge#hasSpecialEdges()};
     * send any changes to special edges from the starting board layout.
     *<P>
     * Compares the current {@link SOCBoardLarge#getSpecialEdges()} against each
     * {@link SOCBoardLarge#getAddedLayoutPart(String)} which defines special edges
     * (currently {@code "CE"}, {@code "VE"}).
     *<P>
     * Called as part of {@link #joinGame(SOCGame, Connection, boolean, boolean, boolean)}.
     * @param game   Game being joined
     * @param board  Game's board layout
     * @param c      Client joining
     */
    private final void joinGame_sendBoardSpecialEdgeChanges
        (final SOCGame game, final SOCBoardLarge board, final Connection c)
    {
        final String gaName = game.getName();

        // - Iterate through added layout parts vs getSpecialEdgeType, to see if any removed or changed.
        // - Build array of each seType's original edge coordinates;
        //   seCoord[i] == special edges of type SPECIAL_EDGE_TYPES[i]

        int[][] seCoord = new int[SOCBoardLarge.SPECIAL_EDGE_LAYOUT_PARTS.length][];

        for (int i = 0; i < SOCBoardLarge.SPECIAL_EDGE_LAYOUT_PARTS.length; ++i)
        {
            final String part = SOCBoardLarge.SPECIAL_EDGE_LAYOUT_PARTS[i];
            final int[] edges = board.getAddedLayoutPart(part);
            if (edges == null)
                continue;
            seCoord[i] = edges;

            final int edgeSEType = SOCBoardLarge.SPECIAL_EDGE_TYPES[i];
            for (int j = 0; j < edges.length; ++j)
            {
                final int edge = edges[j];
                final int seType = board.getSpecialEdgeType(edge);

                if (seType != edgeSEType)
                    // removed (type 0) or changed type
                    c.put(SOCSimpleAction.toCmd(gaName, -1, SOCSimpleAction.BOARD_EDGE_SET_SPECIAL, edge, seType));
            }
        }

        // - Iterate through getSpecialEdges map vs type's added layout part, to see if any added.

        final Iterator<Map.Entry<Integer, Integer>> seIter = board.getSpecialEdges();
        while (seIter.hasNext())
        {
            Map.Entry<Integer, Integer> entry = seIter.next();
            final int edge = entry.getKey(), seType = entry.getValue();

            boolean found = false;
            for (int i = 0; i < SOCBoardLarge.SPECIAL_EDGE_LAYOUT_PARTS.length; ++i)
            {
                if (seType == SOCBoardLarge.SPECIAL_EDGE_TYPES[i])
                {
                    if (seCoord[i] != null)
                    {
                        // search its type's original-edges array; there aren't
                        // many edges per type, so simple linear search is okay

                        for (int j = 0; j < seCoord[i].length; ++j)
                        {
                            if (edge == seCoord[i][j])
                            {
                                found = true;
                                break;
                            }
                        }
                    }

                    break;
                }
            }

            if (! found)
                // added since start of game
                c.put(SOCSimpleAction.toCmd(gaName, -1, SOCSimpleAction.BOARD_EDGE_SET_SPECIAL, edge, seType));
        }
    }

    // javadoc inherited from GameHandler
    public void sitDown_sendPrivateInfo
        (final SOCGame ga, final Connection c, final int pn, final boolean isTakingOver)
    {
        final String gaName = ga.getName();
        final SOCPlayer pl = ga.getPlayer(pn);

        /**
         * send all the private information
         */
        SOCResourceSet resources = pl.getResources();
        // CLAY, ORE, SHEEP, WHEAT, WOOD, UNKNOWN
        final int[] counts = resources.getAmounts(true);
        if (c.getVersion() >= SOCPlayerElements.MIN_VERSION)
            srv.messageToPlayer(c, new SOCPlayerElements
                (gaName, pn, SOCPlayerElement.SET, ELEM_RESOURCES_WITH_UNKNOWN, counts));
        else
            for (int i = 0; i < counts.length; ++i)
                srv.messageToPlayer(c, new SOCPlayerElement
                    (gaName, pn, SOCPlayerElement.SET, ELEM_RESOURCES_WITH_UNKNOWN[i], counts[i]));

        SOCInventory cardsInv = pl.getInventory();

        final boolean cliVersionRecent = (c.getVersion() >= SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES);

        /**
         * remove the unknown cards, if client's too old for receiving SITDOWN to imply doing so;
         * skip if isTakingOver, unknowns weren't sent for that player during joinGame
         */
        if ((! isTakingOver) && (c.getVersion() < SOCDevCardAction.VERSION_FOR_SITDOWN_CLEARS_INVENTORY))
        {
            final SOCDevCardAction cardUnknown = (cliVersionRecent)
                ? new SOCDevCardAction(gaName, pn, SOCDevCardAction.PLAY, SOCDevCardConstants.UNKNOWN)
                : new SOCDevCardAction(gaName, pn, SOCDevCardAction.PLAY, SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X);
            for (int i = cardsInv.getTotal(); i > 0; --i)
                srv.messageToPlayer(c, cardUnknown);
        }

        /**
         * send all new dev cards first, then all playable, then all kept (VP cards)
         */
        for (int dcState = SOCInventory.NEW; dcState <= SOCInventory.KEPT; ++dcState)
        {
            final int dcAge = (dcState == SOCInventory.NEW) ? SOCInventory.NEW : SOCInventory.OLD;
            final int addCmd = (dcAge == SOCInventory.NEW) ? SOCDevCardAction.ADD_NEW : SOCDevCardAction.ADD_OLD;

            for (final SOCInventoryItem iitem : cardsInv.getByState(dcState))
            {
                final SOCMessage addMsg;
                if (iitem instanceof SOCDevCard)
                {
                    final int dcType = iitem.itype;
                    if (cliVersionRecent || (dcType != SOCDevCardConstants.KNIGHT))
                        addMsg = new SOCDevCardAction(gaName, pn, addCmd, dcType);
                    else
                        addMsg = new SOCDevCardAction(gaName, pn, addCmd, SOCDevCardConstants.KNIGHT_FOR_VERS_1_X);
                } else {
                    // SC_FTRI "gift port" to be placed later
                    // or another general inventory item
                    addMsg = new SOCInventoryItemAction
                        (gaName, pn,
                         (iitem.isPlayable() ? SOCInventoryItemAction.ADD_PLAYABLE : SOCInventoryItemAction.ADD_OTHER),
                         iitem.itype, iitem.isKept(), iitem.isVPItem(), iitem.canCancelPlay);
                }

                srv.messageToPlayer(c, addMsg);

            }  // for (item)
        }  // for (dcState)

        /**
         * send game state info such as requests for discards
         */
        sendGameState(ga);

        if ((ga.getCurrentDice() == 7) && pl.getNeedToDiscard())
        {
            srv.messageToPlayer(c, new SOCDiscardRequest(gaName, pl.getCountToDiscard()));
        }
        else if (ga.hasSeaBoard)
        {
            final int numGoldRes = pl.getNeedToPickGoldHexResources();
            if (numGoldRes > 0)
                srv.messageToPlayer(c, new SOCSimpleRequest
                    (gaName, pn, SOCSimpleRequest.PROMPT_PICK_RESOURCES, numGoldRes));
        }

        /**
         * send what face this player is using
         */
        srv.messageToGame(gaName, new SOCChangeFace(gaName, pn, pl.getFaceId()));
    }

    // javadoc inherited from GameHandler. Return true if game is empty and should be ended.
    public boolean leaveGame
        (SOCGame ga, Connection c, final boolean hasReplacement, final boolean hasHumanReplacement)
    {
        final String gm = ga.getName();
        final String plName = c.getData();  // Retain name, since will become null within game obj.

        boolean gameHasHumanPlayer = hasHumanReplacement;
        boolean gameHasObserver = false;
        @SuppressWarnings("unused")
        boolean gameVotingActiveDuringStart = false;  // TODO checks/messages; added in v1.1.01, TODO not used yet

        final int gameState = ga.getGameState();

        boolean isPlayer = false;
        int playerNumber;    // removing this player number
        for (playerNumber = 0; playerNumber < ga.maxPlayers;
                playerNumber++)
        {
            SOCPlayer player = ga.getPlayer(playerNumber);

            if ((player != null) && (player.getName() != null)
                && (player.getName().equals(plName)))
            {
                isPlayer = true;

                /**
                 * About to remove this player from the game. Before doing so:
                 * If a board-reset vote is in progress, they cannot vote
                 * once they have left. So to keep the game moving,
                 * fabricate their response: vote No.
                 */
                if (ga.getResetVoteActive())
                {
                    if (gameState <= SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
                        gameVotingActiveDuringStart = true;

                    if (ga.getResetPlayerVote(playerNumber) == SOCGame.VOTE_NONE)
                    {
                        srv.gameList.releaseMonitorForGame(gm);
                        ga.takeMonitor();
                        srv.resetBoardVoteNotifyOne(ga, playerNumber, plName, false);
                        ga.releaseMonitor();
                        srv.gameList.takeMonitorForGame(gm);
                    }
                }

                /**
                 * Remove the player.
                 */
                ga.removePlayer(plName, hasReplacement);  // player obj name becomes null

                //broadcastGameStats(cg);
                break;
            }
        }

        SOCLeaveGame leaveMessage = new SOCLeaveGame(plName, "-", gm);
        srv.messageToGameWithMon(gm, leaveMessage);
        srv.recordGameEvent(gm, leaveMessage);

        if (D.ebugOn)
            D.ebugPrintln("*** " + plName + " left the game " + gm + " at "
                + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
        srv.messageToGameKeyed(ga, false, "member.left.game", plName);  // "{0} left the game"

        /**
         * check if there is at least one person playing the game
         */
        if (! hasHumanReplacement)
            for (int pn = 0; pn < ga.maxPlayers; pn++)
            {
                SOCPlayer player = ga.getPlayer(pn);

                if ((player != null) && (player.getName() != null) && ! (player.isRobot() || ga.isSeatVacant(pn)))
                {
                    gameHasHumanPlayer = true;
                    break;
                }
            }

        //D.ebugPrintln("*** gameHasHumanPlayer = "+gameHasHumanPlayer+" for "+gm);

        /**
         * if no human players, check if there is at least one person watching the game (observing).
         * Even with observers, end it unless ga.isBotsOnly or PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL != 0
         * or game is still forming (no one has sat yet, but they probably want to sit soon).
         */
        if ( (! gameHasHumanPlayer) && ! srv.gameList.isGameEmpty(gm))
        {
            Enumeration<Connection> membersEnum = srv.gameList.getMembers(gm).elements();

            while (membersEnum.hasMoreElements())
            {
                Connection member = membersEnum.nextElement();

                //D.ebugPrintln("*** "+member.data+" is a member of "+gm);
                boolean nameMatch = false;

                for (int pn = 0; pn < ga.maxPlayers; pn++)
                {
                    SOCPlayer player = ga.getPlayer(pn);

                    if ((player != null) && (player.getName() != null) && (player.getName().equals(member.getData())))
                    {
                        nameMatch = true;
                        break;
                    }
                }

                if (! nameMatch)
                {
                    gameHasObserver = true;
                    break;
                }
            }

            if (gameHasObserver && ! ((gameState == SOCGame.NEW) || (gameState == SOCGame.LOADING) || ga.isBotsOnly))
            {
                if (0 == srv.getConfigIntProperty(SOCServer.PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL, 0))
                    gameHasObserver = false;
            }

        }
        //D.ebugPrintln("*** gameHasObserver = "+gameHasObserver+" for "+gm);

        /**
         * if the leaving member was playing the game, and
         * the game isn't over, then decide:
         * - Do we need to force-end the current turn?
         * - Do we need to cancel their initial settlement placement?
         * - Should we replace the leaving player with a robot?
         */
        if (isPlayer && (gameHasHumanPlayer || gameHasObserver)
                && ((ga.getPlayer(playerNumber).getPublicVP() > 0)
                    || (gameState == SOCGame.START1A)
                    || (gameState == SOCGame.START1B))
                && (gameState < SOCGame.LOADING)
                && ! (gameState < SOCGame.START1A))
        {
            boolean foundNoRobots;

            if (ga.getPlayer(playerNumber).isRobot())
            {
                /**
                 * don't replace bot with bot; force end-turn instead.
                 */
                foundNoRobots = true;
            }
            else
            {
                /**
                 * get a robot to replace this human player;
                 * just in case, check game-version vs robots-version,
                 * like at new-game (readyGameAskRobotsJoin).
                 */
                foundNoRobots = ! findRobotAskJoinGame(ga, Integer.valueOf(playerNumber), true);
            }  // if (should try to find a robot)

            /**
             * What to do if no robot was found to fill their spot?
             * Must keep the game going, might need to force-end current turn.
             */
            if (foundNoRobots)
            {
                final boolean stillActive = endGameTurnOrForce(ga, playerNumber, plName, c, true);
                if (! stillActive)
                {
                    // force game destruction below
                    gameHasHumanPlayer = false;
                    gameHasObserver = false;
                }
            }
        } else {
            // observer leaving: If game is bot-only, don't end the game despite no human players/observers
            if (ga.isBotsOnly && (ga.getGameState() < SOCGame.OVER))
                gameHasObserver = true;
        }

        return ! (gameHasHumanPlayer || gameHasObserver);
    }

    /**
     * When a human player has left an active game, or a game is starting and a
     * bot from that game's {@link SOCServer#robotJoinRequests} has disconnected,
     * look for a robot player which can take a seat and continue the game.
     *<P>
     * If found the bot will be added to {@link SOCServer#robotJoinRequests} and
     * sent a {@link SOCBotJoinGameRequest}. Otherwise the game will be sent a
     * {@link SOCGameServerText} explaining failure to find any robot; human players
     * might need to leave the game and start a new one.
     * @param ga   Game to look in
     * @param seatNumberObj  Seat number to fill, as an {@link Integer} object.
     *     If {@code ! gameIsActive}, this comes from {@link SOCServer#robotJoinRequests}
     *     via {@link SOCServer#leaveConnection(Connection)}.
     * @param gameIsActive  True if for active game, not a game still starting
     * @return true if an available bot was found
     * @since 2.0.00
     */
    public boolean findRobotAskJoinGame
        (final SOCGame ga, Object seatNumberObj, final boolean gameIsActive)
    {
        if (gameIsActive)
            srv.messageToGameKeyed(ga, false, "member.bot.join.fetching");  // "Fetching a robot player..."

        if (srv.robots.isEmpty())
        {
            srv.messageToGameKeyed(ga, false, "member.bot.join.no.bots.server");
                // "No robot can join the game, there are no robots on this server."

            return false;  // <--- Early return: No bot available ---
        }
        else if (ga.getClientVersionMinRequired() > Version.versionNumber())
        {
            srv.messageToGameKeyed
                (ga, false, "member.bot.join.interror.version", ga.getClientVersionMinRequired());
                // "Internal error: The robots can't join this game; game's version {0} is newer than the robots.

            return false;  // <--- Early return: No bot available ---
        }

        Connection robotConn = null;  // the bot selected to join
        boolean nameMatch = true;  // false if can select a bot that isn't already playing in or requested in this game
        final String gaName = ga.getName();
        final boolean gameHasLimitedFeats = (ga.getClientFeaturesRequired() != null);
        Hashtable<Connection, Object> requestedBots = srv.robotJoinRequests.get(gaName);

        if (! (seatNumberObj instanceof Integer))  // should not happen; check just in case
        {
            seatNumberObj = null;
            // nameMatch remains true; will announce can't find a bot
        } else {

            /**
             * request a robot that isn't already playing this game or
             * is not already requested to play in this game
             */

            final HashSet<String> gameBots = new HashSet<String>();
            for (int i = 0; i < ga.maxPlayers; i++)
            {
                SOCPlayer pl = ga.getPlayer(i);
                if (pl != null)
                {
                    String pname = pl.getName();
                    if (pname != null)
                        gameBots.add(pname);
                }
            }

            final int[] robotIndexes = srv.robotShuffleForJoin();  // Shuffle to distribute load

            for (int idx = 0; idx < srv.robots.size(); idx++)
            {
                robotConn = srv.robots.get(robotIndexes[idx]);

                nameMatch = gameBots.contains(robotConn.getData());

                if ((! nameMatch) && (requestedBots != null))
                    nameMatch = requestedBots.containsKey(robotConn);

                if (! nameMatch)
                {
                    if (gameHasLimitedFeats && ! ga.canClientJoin((((SOCClientData) (robotConn.getAppData())).feats)))
                        nameMatch = true;  // try the next bot instead
                    else
                        break;
                }
            }
        }

        if (! nameMatch)
        {
            /**
             * make the request
             */
            // D.ebugPrintln("@@@ JOIN GAME REQUEST for " + robotConn.getData());

            final int seatNumber = ((Integer) seatNumberObj).intValue();

            if (ga.getSeatLock(seatNumber) != SOCGame.SeatLockState.UNLOCKED)
            {
                // make sure bot can sit
                ga.setSeatLock(seatNumber, SOCGame.SeatLockState.UNLOCKED);
                srv.messageToGameWithMon
                    (gaName, new SOCSetSeatLock(gaName, seatNumber, SOCGame.SeatLockState.UNLOCKED));
            }

            /**
             * record the request
             */
            if (requestedBots == null)
            {
                requestedBots = new Hashtable<Connection, Object>();
                requestedBots.put(robotConn, seatNumberObj);
                srv.robotJoinRequests.put(gaName, requestedBots);
            }
            else
            {
                requestedBots.put(robotConn, seatNumberObj);
            }

            robotConn.put(SOCBotJoinGameRequest.toCmd(gaName, seatNumber, ga.getGameOptions()));
        }
        else
        {
            srv.messageToGameKeyed(ga, false, "member.bot.join.cantfind");  // "*** Can't find a robot! ***"

            return false;  // <--- Early return: No bot available ---
        }

        return true;
    }

    /**
     * {@inheritDoc}
     *<P>
     * Equivalent to: {@link #sendGameState(SOCGame, boolean, boolean) sendGameState(ga, false, true)}.
     */
    public void sendGameState(SOCGame ga)
    {
        sendGameState(ga, false, true);
    }

    /**
     * Send all game members the current state of the game with a message.
     * May also send other messages to the game and/or specific players if noted here.
     * Note that the current (or new) player number is not sent here.
     *<P>
     * See {@link SOCGameState} for complete list of game states,
     * related messages sent, and expected client responses.
     *<P>
     * Summarized here:
     *<P>
     * State {@link SOCGame#ROLL_OR_CARD}:
     * If {@code sendRollPrompt}, send game a {@code RollDicePrompt} announcement.
     * Clients v2.0.00 or newer ({@link SOCStringManager#VERSION_FOR_I18N}) will print
     * "It's Joe's turn to roll the dice." Older clients will be sent a text message
     * to say that.
     *<P>
     * State {@link SOCGame#WAITING_FOR_DISCARDS}:
     * If a 7 is rolled, will also say who must discard (in a GAMESERVERTEXT).
     * Can use {@code omitGameStateMessage} to send only that GAMESERVERTEXT
     * if responding to a player's discard.<BR>
     * <B>Note:</B> This method sends only the prompt text, not the {@link SOCDiscardRequest}s
     * sent by {@link #sendGameState_sendDiscardRequests(SOCGame, String)}.
     *<P>
     * State {@link SOCGame#WAITING_FOR_ROB_CHOOSE_PLAYER}:
     * If current player must choose which player to rob,
     * will also prompt their client to choose (in a CHOOSEPLAYERREQUEST).
     *<P>
     * State {@link SOCGame#STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}:
     * To announce the player must pick a resource to gain from the gold hex initial placement,
     * please call {@link #sendGameState_sendGoldPickAnnounceText(SOCGame, String, Connection, SOCGame.RollResult)}.
     *<P>
     * State {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE}:
     * If a gold hex is rolled, does not say who
     * must pick resources to gain (because of timing).  Please call
     * {@link #sendGameState_sendGoldPickAnnounceText(SOCGame, String, Connection, SOCGame.RollResult)}
     * after sending the resource gain text ("x gets 1 sheep").
     *<P>
     * State {@link SOCGame#OVER OVER}: Announces winner, each player's total VP, and related game and player stats.
     *<P>
     * <b>Note:</b> If game is now {@code OVER} and the {@link SOCGame#isBotsOnly} flag is set,
     * {@link #sendGameStateOVER(SOCGame, Connection)} will call {@link SOCServer#destroyGameAndBroadcast(String, String)}.
     * Be sure that callers to {@code sendGameState} don't assume the game will still exist after calling this method.
     * Also, {@code destroyGame} might create more {@link SOCGame#isBotsOnly} games, depending on server settings.
     *<P>
     * <b>Locks:</b> Does not hold {@link SOCGameList#takeMonitor()} or
     * {@link SOCGameList#takeMonitorForGame}<tt>(gaName)</tt> when called.
     * Some callers call {@link SOCGame#takeMonitor()} before calling; not important here.
     *
     * @see #sendTurn(SOCGame, boolean)
     * @see #sendGameState(SOCGame)
     * @see #sendGameStateOVER(SOCGame, Connection)
     *
     * @param ga  the game
     * @param omitGameStateMessage  if true, don't send the {@link SOCGameState} message itself
     *    but do send any other messages as described above. For use just after sending a message which
     *    includes a Game State field. Ignored if gamestate >= {@link SOCGame#OVER}.
     * @param sendRollPrompt  If true and state is {@link SOCGame#ROLL_OR_CARD}, send game a {@code RollDicePrompt}.
     * @return  If true, caller ({@code sendTurn}) should send game a {@code RollDicePrompt}
     *    because {@code sendRollPrompt} is false, although they may send other messages first.
     * @since 1.1.00
     */
    boolean sendGameState(SOCGame ga, final boolean omitGameStateMessage, final boolean sendRollPrompt)
    {
        if (ga == null)
            return false;

        final int gaState = ga.getGameState();
        final int cpn = ga.getCurrentPlayerNumber();
        final String gname = ga.getName();
        boolean wantRollPrompt = false;

        if (gaState == SOCGame.OVER)
        {
            /**
             * Before sending state "OVER", enforce current player number.
             * This helps the client's copy of game recognize winning condition.
             */
            srv.messageToGame(gname, (ga.clientVersionLowest >= SOCGameElements.MIN_VERSION)
                ? new SOCGameElements(gname, GEType.CURRENT_PLAYER, cpn)
                : new SOCSetTurn(gname, cpn));
        }

        if ((! omitGameStateMessage) || (gaState >= SOCGame.OVER))
            srv.messageToGame(gname, new SOCGameState(gname, gaState));

        SOCPlayer player = null;

        if (cpn != -1)
            player = ga.getPlayer(cpn);

        switch (gaState)
        {
        case SOCGame.START1A:
        case SOCGame.START2A:
        case SOCGame.START3A:
            srv.messageToGameKeyed(ga, true, "prompt.turn.to.build.stlmt",  player.getName());
                // "It's Joe's turn to build a settlement."
            if ((gaState >= SOCGame.START2A)
                && ga.isGameOptionSet(SOCGameOption.K_SC_3IP))
            {
                // reminder to player before their 2nd, 3rd settlements
                Connection con = srv.getConnection(player.getName());
                if (con != null)
                {
                    srv.messageToPlayerKeyed(con, gname, "prompt.gameopt._SC_3IP.part1");
                        // "This game gives you 3 initial settlements and roads."
                    srv.messageToPlayerKeyed(con, gname, "prompt.gameopt._SC_3IP.part2");
                        // "Your free resources will be from the third settlement."
                }
            }
            break;

        case SOCGame.START1B:
        case SOCGame.START2B:
        case SOCGame.START3B:
            srv.messageToGameKeyed(ga, true,
                ((ga.hasSeaBoard) ? "prompt.turn.to.build.road.or.ship"  // "It's Joe's turn to build a road or ship."
                    : "prompt.turn.to.build.road"),
                player.getName());
            break;

        case SOCGame.ROLL_OR_CARD:
            if (ga.clientVersionLowest < SOCStringManager.VERSION_FOR_I18N)
            {
                // v2.0.00 and newer clients will announce this with localized text;
                // older clients need text sent from the server
                final String prompt = "It's " + player.getName() + "'s turn to roll the dice.";
                    // "It's Joe's turn to roll the dice."
                    // I18N OK: Pre-2.0.00 clients always use english
                srv.messageToGameForVersions
                    (ga, 0, SOCStringManager.VERSION_FOR_I18N - 1,
                     new SOCGameTextMsg(gname, SOCServer.SERVERNAME, prompt), true);
            }
            if (sendRollPrompt)
                srv.messageToGame(gname, new SOCRollDicePrompt(gname, player.getPlayerNumber()));
            else
                wantRollPrompt = true;
            break;

        case SOCGame.WAITING_FOR_DISCARDS:
            {
                ArrayList<String> names = new ArrayList<String>();

                for (int i = 0; i < ga.maxPlayers; i++)
                    if (ga.getPlayer(i).getNeedToDiscard())
                        names.add(ga.getPlayer(i).getName());

                if (names.size() == 1)
                    srv.messageToGameKeyed(ga, true, "prompt.discard.1", names.get(0));  // "Joe needs to discard"
                else
                    srv.messageToGameKeyedSpecial(ga, true, "prompt.discard.n", names);  // "Joe and Ed need to discard"
            }
            break;

        // case SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE and
        // case SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE are now
            // handled in SOCGameMessageHandler.handlePUTPIECE and handleROLLDICE,
            // so that the new state is sent after resource texts ("x gets 1 sheep") and not before.
            // These methods directly call sendGameState_sendGoldPickAnnounceText.

        case SOCGame.WAITING_FOR_ROBBER_OR_PIRATE:
            srv.messageToGameKeyed(ga, true, "robber.willmove.choose", player.getName());  // "{0} must choose to move the robber or the pirate."
            break;

        case SOCGame.PLACING_ROBBER:
            srv.messageToGameKeyed(ga, true, "robber.willmove", player.getName());  // "{0} will move the robber."
            break;

        case SOCGame.PLACING_PIRATE:
            srv.messageToGameKeyed(ga, true, "robber.willmove.pirate", player.getName());  // "{0} will move the pirate ship."
            break;

        case SOCGame.WAITING_FOR_ROB_CHOOSE_PLAYER:
            /**
             * ask the current player to choose a player to steal from
             */
            Connection con = srv.getConnection(ga.getPlayer(cpn).getName());
            if (con != null)
            {
                final boolean canChooseNone = ga.isGameOptionSet(SOCGameOption.K_SC_PIRI);
                boolean[] choices = new boolean[ga.maxPlayers];
                for (SOCPlayer pl : ga.getPossibleVictims())
                    choices[pl.getPlayerNumber()] = true;

                con.put(SOCChoosePlayerRequest.toCmd(gname, choices, canChooseNone));
            }
            break;

        case SOCGame.OVER:
            sendGameStateOVER(ga, null);
            break;

        }  // switch ga.getGameState

        return wantRollPrompt;
    }

    /**
     * If any player needs to discard, prompt them to discard half (round down);
     * call in game state {@link SOCGame#WAITING_FOR_DISCARDS} after calling {@link #sendGameState(SOCGame)}.
     * This method sends only {@link SOCDiscardRequest}s, not the prompt text "Joe and Lily must discard"
     * sent by {@code sendGameState}.
     *<P>
     * Checks each player's {@link SOCGame#isSeatVacant(int)} and {@link SOCPlayer#getNeedToDiscard()} flags.
     * Number of resources to discard is {@link SOCPlayer#getCountToDiscard()}:
     * Half of {@link SOCPlayer#getResources()}.
     *
     * @param ga  Game to prompt
     * @param gaName  Game name for convenience; not {@code null}
     * @since 2.0.00
     */
    final void sendGameState_sendDiscardRequests(SOCGame ga, final String gaName)
    {
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            final SOCPlayer pl = ga.getPlayer(pn);
            if (( ! ga.isSeatVacant(pn)) && pl.getNeedToDiscard())
            {
                Connection con = srv.getConnection(pl.getName());
                if (con != null)
                    con.put(SOCDiscardRequest.toCmd(gaName, pl.getCountToDiscard()));
            }
        }
    }

    /**
     * Send a game text message "x, y, and z need to pick resources from the gold hex."
     * and, for each picking player, a
     * {@link SOCPlayerElement}({@link PEType#NUM_PICK_GOLD_HEX_RESOURCES NUM_PICK_GOLD_HEX_RESOURCES}).
     * To prompt the specific players to choose a resource, also sends their clients a
     * {@link SOCSimpleRequest}({@link SOCSimpleRequest#PROMPT_PICK_RESOURCES PROMPT_PICK_RESOURCES}).
     *<P>
     * Used in game state {@link SOCGame#STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}
     * and {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE}.
     *<P>
     * The text and SOCPlayerElement messages are sent to the entire game.
     * Any {@code PROMPT_PICK_RESOURCES} are sent to those player clients only.
     * If you know that only 1 player will pick gold, pass their <tt>playerCon</tt> for efficiency.
     *<P>
     * This is separate from {@link #sendGameState(SOCGame)} because when the dice are rolled,
     * <tt>sendGameState</tt> is called, then resource distribution messages are sent out,
     * then this method is called.
     *
     * @param ga  Game object
     * @param gname  Game name
     * @param playerCon  <tt>null</tt>, or current player's client connection to send the
     *          {@code PROMPT_PICK_RESOURCES} if they are the only one to pick gold.
     *          If more than 1 player has {@link SOCPlayer#getNeedToPickGoldHexResources()},
     *          no message will be sent to <tt>playerCon</tt>.
     * @param roll  For gold gained from dice rolls, the roll details, otherwise null.
     *          In scenario SC_PIRI, is used to avoid announcing twice for a pick after victory against pirate fleet.
     * @since 2.0.00
     */
    final void sendGameState_sendGoldPickAnnounceText
        (SOCGame ga, final String gname, Connection playerCon, SOCGame.RollResult roll)
    {
        /**
         * If not 0, this number of the free-resource picks are from
         * winning an SC_PIRI fleet battle, not from a gold hex
         */
        final int ignoreAmountFromPirateFleet;
        if ((roll != null) && ga.isGameOptionSet(SOCGameOption.K_SC_PIRI) && (roll.sc_piri_fleetAttackRsrcs != null))
            ignoreAmountFromPirateFleet = roll.sc_piri_fleetAttackRsrcs.getAmount(SOCResourceConstants.GOLD_LOCAL);
        else
            ignoreAmountFromPirateFleet = 0;

        int count = 0, amount = 0, firstPN = -1;
        ArrayList<String> names = new ArrayList<String>();
        int[] num = new int[ga.maxPlayers];

        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            final SOCPlayer pp = ga.getPlayer(pn);
            int numGoldRes = pp.getNeedToPickGoldHexResources();
            if (numGoldRes > 0)
            {
                num[pn] = numGoldRes;
                if ((ignoreAmountFromPirateFleet > 0) && (pp == roll.sc_piri_fleetAttackVictim))
                    numGoldRes -= ignoreAmountFromPirateFleet;
                if (numGoldRes > 0)
                {
                    names.add(pp.getName());
                    count++;
                    if (count == 1)
                    {
                        amount = numGoldRes;
                        firstPN = pn;
                    }
                }
            }
        }

        if (count > 1)
            srv.messageToGameKeyedSpecial(ga, true, "prompt.pick.gold.n", names);
                // "... need to pick resources from the gold hex."
        else if (count == 1)
            srv.messageToGameKeyed(ga, true, "prompt.pick.gold.1", names.get(0));
                // "Joe needs to pick resources from the gold hex."

        final boolean singlePlayerGetsPickRequest = ((playerCon != null) && (count == 1));
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            if (num[pn] > 0)
            {
                srv.messageToGame(gname, new SOCPlayerElement
                    (gname, pn, SOCPlayerElement.SET, PEType.NUM_PICK_GOLD_HEX_RESOURCES, num[pn]));

                if (! singlePlayerGetsPickRequest)
                {
                    Connection plCon = srv.getConnection(ga.getPlayer(pn).getName());
                    if (plCon != null)
                        srv.messageToPlayer(plCon, new SOCSimpleRequest
                            (gname, pn, SOCSimpleRequest.PROMPT_PICK_RESOURCES, num[pn]));
                }
            }
        }

        if (singlePlayerGetsPickRequest)
            srv.messageToPlayer(playerCon, new SOCSimpleRequest
                (gname, firstPN, SOCSimpleRequest.PROMPT_PICK_RESOURCES, amount));
    }

    /**
     *  Either announce Game Over to entire game (with side effects), or send end-of-game
     *  info to a single {@code joiningConn}'s connection (no side effects):
     *<P>
     *  If game is OVER, send messages reporting winner, final score,
     *  and each player's victory-point cards.
     *<P>
     *  If {@code joiningConn != null} or {@link SOCGame#hasDoneGameOverTasks}, returns at that point.
     *  The rest of the method has side effects, like stat increments and DB updates,
     *  which should happen only once per game.
     *  Server structure more or less ensures sendGameStateOVER(ga, null) is called only once.
     *  Sets {@code ga.hasDoneGameOverTasks} flag just in case.
     *<P>
     *  Give stats on game length, and send each player their connect time.
     *  If player has finished more than 1 game since connecting, send their win-loss count.
     *<P>
     *  Increments server stats' numberOfGamesFinished.
     *  If db is active, calls {@link SOCServer#storeGameScores(SOCGame)} to save game stats.
     *<P>
     *  If {@link SOCGame#isBotsOnly}, calls {@link SOCServer#destroyGameAndBroadcast(String, String)} to make room
     *  for more games to run: The bots don't know on their own to leave, it's easier for the
     *  server to dismiss them within {@code destroyGame}.
     *<P>
     *  Make sure {@link SOCGameState}({@link SOCGame#OVER OVER}) is sent before calling this method.
     *
     * @param ga This game is over; state should be OVER
     * @param joiningConn  Send the messages only to this connection, not all game members, if not {@code null}
     * @since 1.1.00
     */
    private void sendGameStateOVER(final SOCGame ga, final Connection joiningConn)
    {
        final String gname = ga.getName();

        /**
         * Find and announce the winner
         * (the real "found winner" code is in SOCGame.checkForWinner;
         *  that was already called before sendGameStateOVER.)
         */
        SOCPlayer winPl = ga.getPlayer(ga.getCurrentPlayerNumber());

        if ((joiningConn == null) && (winPl.getTotalVP() < ga.vp_winner) && ! ga.hasScenarioWinCondition)
        {
            // Should not happen: By rules FAQ, only current player can be winner.
            // This is fallback code.
            for (int i = 0; i < ga.maxPlayers; i++)
            {
                if (winPl.getTotalVP() >= ga.vp_winner)
                {
                    winPl = ga.getPlayer(i);
                    break;
                }
            }
        }

        if (joiningConn == null)
            srv.messageToGameKeyed(ga, true, "stats.game.winner.withpoints", winPl.getName(), winPl.getTotalVP());
                // "{0} has won the game with {1,number} points."
        else
            srv.messageToPlayerKeyed
                (joiningConn, gname, "stats.game.winner.withpoints", winPl.getName(), winPl.getTotalVP());

        ///
        /// send a message saying what VP cards each player has;
        /// before v2.0.00 this was sent as text messages after GameStats, not data messages before it
        ///
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            final SOCPlayer pl = ga.getPlayer(pn);
            final List<SOCInventoryItem> vpCards = pl.getInventory().getByState(SOCInventory.KEPT);
            if (vpCards.isEmpty())
                continue;

            if ((ga.clientVersionHighest >= SOCDevCardAction.VERSION_FOR_MULTIPLE) || (joiningConn != null))
            {
                List<Integer> vpCardsITypes = new ArrayList<Integer>();
                for (SOCInventoryItem i : vpCards)
                    vpCardsITypes.add(Integer.valueOf(i.itype));
                final SOCDevCardAction dcaMsg = new SOCDevCardAction(gname, pn, SOCDevCardAction.ADD_OLD, vpCardsITypes);

                if (joiningConn != null)
                {
                    if (joiningConn.getVersion() >= SOCDevCardAction.VERSION_FOR_MULTIPLE)
                        joiningConn.put(dcaMsg);
                    // else:
                    //    Server v1.x never sent these to a client joining a game after it ends;
                    //    don't send them to a 1.x client
                } else if (ga.clientVersionLowest >= SOCDevCardAction.VERSION_FOR_MULTIPLE) {
                    // clients are all 2.0 or newer
                    srv.messageToGame(gname, dcaMsg);
                } else {
                    // mixed versions:
                    // v2.0.00 and newer clients will announce this with localized text;
                    // older clients need it sent from the server.
                    final String txt = SOCStringManager.getFallbackServerManagerForClient().formatSpecial
                        (ga, "{0} has {1,dcards}.", pl.getName(), vpCards);
                            // "Joe has a Gov.House (+1VP) and a Market (+1VP)"
                            // I18N OK: Pre-2.0.00 clients always use english
                    srv.messageToGameForVersions(ga, 0, SOCDevCardAction.VERSION_FOR_MULTIPLE - 1,
                        new SOCGameTextMsg(gname, SOCServer.SERVERNAME, txt), true);
                    srv.messageToGameForVersions(ga, SOCDevCardAction.VERSION_FOR_MULTIPLE, Integer.MAX_VALUE,
                        dcaMsg, true);
                }
            } else {
                // clients are all v1.x, and there's no joiningConn
                srv.messageToGame
                    (gname, SOCStringManager.getFallbackServerManagerForClient().formatSpecial
                        (ga, "{0} has {1,dcards}.", pl.getName(), vpCards));
                            // "Joe has a Gov.House (+1VP) and a Market (+1VP)"
                            // I18N OK: Pre-2.0.00 clients always use english
            }
        }

        /// send a message with the revealed final scores;
        /// client can use this message as a signal to reveal
        /// hidden interesting game info
        {
            int[] scores = new int[ga.maxPlayers];
            boolean[] isRobot = new boolean[ga.maxPlayers];
            for (int i = 0; i < ga.maxPlayers; ++i)
            {
                scores[i] = ga.getPlayer(i).getTotalVP();
                isRobot[i] = ga.getPlayer(i).isRobot();
            }

            final SOCGameStats statsMsg = new SOCGameStats(gname, scores, isRobot);
            if (joiningConn == null)
                srv.messageToGame(gname, statsMsg);
            else
                joiningConn.put(statsMsg);
        }

        if ((joiningConn != null) || ga.hasDoneGameOverTasks)
        {
            return;  // <--- Early return, to avoid side effects that should happen only once ---
        }

        ga.hasDoneGameOverTasks = true;

        /**
         * send game-length and connect-length messages, possibly win-loss count.
         */
        {
            final int gameRounds = ga.getRoundCount();
            int gameSeconds = ga.getDurationSeconds();
            final int gameMinutes = gameSeconds / 60;
            gameSeconds = gameSeconds % 60;

            if (gameSeconds == 0)
                srv.messageToGameKeyed
                    (ga, true, "stats.game.was.roundsminutes", gameRounds, gameMinutes);
                    // "This game was # rounds, and took # minutes."
            else
                srv.messageToGameKeyed
                    (ga, true, "stats.game.was.roundsminutessec", gameRounds, gameMinutes, gameSeconds);
                    // "This game was # rounds, and took # minutes # seconds." [or 1 second.]

            // Ignore possible "1 minutes"; that game is too short to worry about.

            /**
             * Update each player's win-loss count for this session.
             * Tell each player their resource roll totals.
             * Tell each player how long they've been connected.
             * (Robot players aren't told this, it's not necessary.)
             */
            for (int i = 0; i < ga.maxPlayers; i++)
            {
                if (ga.isSeatVacant(i))
                    continue;

                SOCPlayer pl = ga.getPlayer(i);
                Connection plConn = srv.getConnection(pl.getName());
                SOCClientData cd;
                if (plConn != null)
                {
                    // Update win-loss count, even for robots
                    cd = (SOCClientData) plConn.getAppData();
                    if (pl == winPl)
                        cd.wonGame();
                    else
                        cd.lostGame();
                } else {
                    cd = null;  // To satisfy compiler warning
                }

                if (pl.isRobot())
                    continue;  // <-- Don't bother to send any stats text to robots --

                if (plConn != null)
                {
                    if (plConn.getVersion() >= SOCPlayerStats.VERSION_FOR_RES_ROLL)
                    {
                        // Send total resources rolled
                        srv.messageToPlayer(plConn, new SOCPlayerStats(pl, SOCPlayerStats.STYPE_RES_ROLL));
                    }

                    // Send client how long they've been connected
                    // and, if more than 1 game has been played,
                    // their win-loss count for this session
                    srv.processDebugCommand_connStats(plConn, ga, true);
                }
            }  // for each player

        }  // send game timing stats, win-loss stats

        srv.gameOverIncrGamesFinishedCount(ga);
        srv.storeGameScores(ga);

        if (ga.isBotsOnly)
        {
            srv.destroyGameAndBroadcast(gname, "sendGameStateOVER");
        }
    }

    /**
     * The current player is stealing from another player.
     * Send messages saying what was stolen.
     *
     * @param ga  the game
     * @param pe  the perpetrator
     * @param vi  the victim
     * @param rsrc  type of resource stolen, as in {@link SOCResourceConstants#SHEEP},
     *          or {@link SOCResourceConstants#CLOTH_STOLEN_LOCAL} for cloth
     *          (scenario option {@link SOCGameOption#K_SC_CLVI _SC_CLVI}).
     */
    void reportRobbery(SOCGame ga, SOCPlayer pe, SOCPlayer vi, final int rsrc)
    {
        if (ga == null)
            return;

        final String gaName = ga.getName();
        final String peName = pe.getName();
        final String viName = vi.getName();
        final int pePN = pe.getPlayerNumber();
        final int viPN = vi.getPlayerNumber();

        if (rsrc == SOCResourceConstants.CLOTH_STOLEN_LOCAL)
        {
            // Send players' cloth counts and text.
            // Client's game will recalculate players' VP based on
            // the cloth counts, so we don't need to also send VP.

            srv.messageToGame(gaName,
                new SOCPlayerElement(gaName, viPN, SOCPlayerElement.SET,
                    PEType.SCENARIO_CLOTH_COUNT, vi.getCloth(), true));
            srv.messageToGame(gaName,
                new SOCPlayerElement(gaName, pePN, SOCPlayerElement.SET,
                    PEType.SCENARIO_CLOTH_COUNT, pe.getCloth()));
            srv.messageToGameKeyed(ga, true, "robber.stole.cloth.from", peName, viName);  // "{0} stole a cloth from {1}."

            return;  // <--- early return: cloth is announced to entire game ---
        }

        SOCPlayerElement gainRsrc = null;
        SOCPlayerElement loseRsrc = null;
        SOCPlayerElement gainUnknown;
        SOCPlayerElement loseUnknown;

        // This works because SOCPlayerElement.SHEEP == SOCResourceConstants.SHEEP.
        gainRsrc = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, rsrc, 1);
        loseRsrc = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, rsrc, 1, true);

        /**
         * send the game data messages
         */
        Connection peCon = srv.getConnection(peName);
        Connection viCon = srv.getConnection(viName);
        srv.messageToPlayer(peCon, gainRsrc);
        srv.messageToPlayer(peCon, loseRsrc);
        srv.messageToPlayer(viCon, gainRsrc);
        srv.messageToPlayer(viCon, loseRsrc);
        // Don't send generic message to pe or vi
        List<Connection> sendNotTo = new ArrayList<Connection>(2);
        sendNotTo.add(peCon);
        sendNotTo.add(viCon);
        gainUnknown = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, PEType.UNKNOWN_RESOURCE, 1);
        loseUnknown = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, PEType.UNKNOWN_RESOURCE, 1);
        srv.messageToGameExcept(gaName, sendNotTo, gainUnknown, true);
        srv.messageToGameExcept(gaName, sendNotTo, loseUnknown, true);

        /**
         * send the text messages:
         * "You stole a sheep from viName."  [In v1.x.xx, "stole a sheep resource"]
         * "peName stole a sheep from you."
         * "peName stole a resource from viName."
         */
        srv.messageToPlayerKeyedSpecial(peCon, ga, "robber.you.stole.resource.from", -1, rsrc, viName);  // "You stole {0,rsrcs} from {2}."
        srv.messageToPlayerKeyedSpecial(viCon, ga, "robber.stole.resource.from.you", peName, -1, rsrc);  // "{0} stole {1,rsrcs} from you."
        srv.messageToGameKeyedSpecialExcept(ga, true, sendNotTo, "robber.stole.resource.from", peName, viName);  // "{0} stole a resource from {1}."
    }

    /**
     * {@inheritDoc}
     *<P>
     * Before v2.4.00, this method's code was part of {@code SOCGameMessageHandler.handleMAKEOFFER(..)}.
     */
    public void sendTradeOffer(final SOCPlayer player, final Connection toJoiningClient)
    {
        final SOCTradeOffer offer = player.getCurrentOffer();
        if (offer == null)
            return;

        final SOCGame ga = player.getGame();
        final String gaName = ga.getName();
        final int lowestVersion = (toJoiningClient != null)
            ? toJoiningClient.getVersion()
            : ga.clientVersionLowest;

        // v2.0.00 and newer clients will announce this with localized text;
        // older clients need it sent from the server
        if (lowestVersion < SOCStringManager.VERSION_FOR_I18N)
        {
            // I18N OK: Pre-2.0.00 clients always use english
            final String txt = SOCStringManager.getFallbackServerManagerForClient().formatSpecial
                (ga, "{0} offered to give {1,rsrcs} for {2,rsrcs}.", player.getName(), offer.getGiveSet(), offer.getGetSet());

            if (toJoiningClient == null)
                srv.messageToGameForVersions
                    (ga, 0, SOCStringManager.VERSION_FOR_I18N - 1,
                     new SOCGameTextMsg(gaName, SOCServer.SERVERNAME, txt), true);
            else
                srv.messageToPlayer(toJoiningClient, gaName, txt);
        }

        SOCMakeOffer makeOfferMessage = new SOCMakeOffer(gaName, offer);

        if (toJoiningClient == null)
        {
            srv.messageToGame(gaName, makeOfferMessage);
            srv.recordGameEvent(gaName, makeOfferMessage);
        } else {
            srv.messageToPlayer(toJoiningClient, makeOfferMessage);
        }

        if (toJoiningClient == null)
        {
            /**
             * clear client UIs of any previous trade messages/responses, because a new offer has been made
             */
            if (ga.clientVersionLowest >= SOCClearTradeMsg.VERSION_FOR_CLEAR_ALL)
            {
                srv.messageToGame(gaName, new SOCClearTradeMsg(gaName, -1));
            } else {
                srv.gameList.takeMonitorForGame(gaName);
                for (int i = 0; i < ga.maxPlayers; i++)
                    srv.messageToGameWithMon(gaName, new SOCClearTradeMsg(gaName, i));
                srv.gameList.releaseMonitorForGame(gaName);
            }
        }
        // else client is joining now, so there aren't any previous to be cleared
    }

    /**
     * Report a trade that has taken place between players, using {@link SOCPlayerElement}.
     * Also announces the trade to pre-v2.0.00 clients with a {@link SOCGameTextMsg}
     * ("Joe gave 1 sheep for 1 wood from Lily.").
     *<P>
     * Caller must also report trade player numbers by sending a {@link SOCAcceptOffer}
     * message to the game after calling this method. In v2.0.00 and newer clients,
     * that message announces the trade instead of {@link SOCGameTextMsg}.
     *
     * @param ga        the game
     * @param offering  the number of the player making the offer
     * @param accepting the number of the player accepting the offer
     * @see #reportBankTrade(SOCGame, SOCResourceSet, SOCResourceSet)
     */
    void reportTrade(SOCGame ga, int offering, int accepting)
    {
        final String gaName = ga.getName();
        final SOCTradeOffer offer = ga.getPlayer(offering).getCurrentOffer();
        final SOCResourceSet giveSet = offer.getGiveSet(),
                             getSet  = offer.getGetSet();

        reportRsrcGainLoss(gaName, giveSet, true, false, offering, accepting, null);
        reportRsrcGainLoss(gaName, getSet, false, false, offering, accepting, null);
        if (ga.clientVersionLowest < SOCStringManager.VERSION_FOR_I18N)
        {
            // v2.0.00 and newer clients will announce this with localized text;
            // older clients need it sent from the server.
            // I18N OK: Pre-2.0.00 clients always use english
            final String txt = SOCStringManager.getFallbackServerManagerForClient().formatSpecial
                (ga, "{0} gave {1,rsrcs} for {2,rsrcs} from {3}.",
                 ga.getPlayer(offering).getName(), giveSet, getSet, ga.getPlayer(accepting).getName());
            srv.messageToGameForVersions
                (ga, 0, SOCStringManager.VERSION_FOR_I18N - 1,
                 new SOCGameTextMsg(gaName, SOCServer.SERVERNAME, txt), true);
        }
    }

    /**
     * report that the current player traded with the bank or a port,
     * using {@link SOCPlayerElement} and {@link SOCBankTrade} messages
     * (or {@link SOCGameTextMsg} to older clients).
     *
     * @param ga        the game
     * @param give      the number of the player making the offer
     * @param get       the number of the player accepting the offer
     * @see #reportTrade(SOCGame, int, int)
     */
    void reportBankTrade(SOCGame ga, SOCResourceSet give, SOCResourceSet get)
    {
        final String gaName = ga.getName();
        final int    cpn    = ga.getCurrentPlayerNumber();

        reportRsrcGainLoss(gaName, give, true, false, cpn, -1, null);
        reportRsrcGainLoss(gaName, get, false, false, cpn, -1, null);

        SOCBankTrade bt = null;
        if (ga.clientVersionHighest >= SOCStringManager.VERSION_FOR_I18N)
            bt = new SOCBankTrade(gaName, give, get, cpn);

        if (ga.clientVersionLowest >= SOCStringManager.VERSION_FOR_I18N)
        {
            srv.messageToGame(gaName, bt);
        } else {
            if (bt != null)
                srv.messageToGameForVersions
                    (ga, SOCStringManager.VERSION_FOR_I18N, Integer.MAX_VALUE, bt, true);

            // Text announcement for older clients:

            // use total rsrc counts to determine bank or port
            final int giveTotal = give.getTotal(),
                      getTotal  = get.getTotal();
            final boolean isUndo = (giveTotal < getTotal);
            final boolean isFromBank;  // from port if false
            StringBuilder fmt = new StringBuilder("{0} traded {1,rsrcs} for {2,rsrcs} from ");
            if (isUndo)
                isFromBank = ((getTotal / giveTotal) == 4);
            else
                isFromBank = ((giveTotal / getTotal) == 4);

            fmt.append(isFromBank ? "the bank." : "a port.");
            if (isUndo)
                fmt.append(" (Undo previous trade)");

            // v2.0.00 and newer clients will announce this with localized text;
            // older clients need it sent from the server.
            // I18N OK: Pre-2.0.00 clients always use english
            final String txt = SOCStringManager.getFallbackServerManagerForClient().formatSpecial
                (ga, fmt.toString(), ga.getPlayer(cpn).getName(), give, get);
            srv.messageToGameForVersions
                (ga, 0, SOCStringManager.VERSION_FOR_I18N - 1,
                 new SOCGameTextMsg(gaName, SOCServer.SERVERNAME, txt), true);
        }
    }

    /**
     * Report the resources gained/lost by a player, and optionally (for trading)
     * lost/gained by a second player.
     * Sends PLAYERELEMENT messages, either to entire game, or to player only.
     *<P>
     * Used to report the resources gained from a roll, discard, or discovery (year-of-plenty) pick.
     * Also used to report the "give" or "get" half of a resource trade.
     *<P>
     * Takes and releases the gameList monitor for this game.
     *
     * @param gaName  Game name
     * @param resourceSet    Resource set (from a roll, or the "give" or "get" side of a trade).
     *                Resource type {@link SOCResourceConstants#UNKNOWN UNKNOWN} or
     *                {@link SOCResourceConstants#GOLD_LOCAL GOLD_LOCAL} is ignored.
     *                Only positive resource amounts are sent (negative is ignored).
     * @param isLoss  If true, "give" ({@link SOCPlayerElement#LOSE}), otherwise "get" ({@link SOCPlayerElement#GAIN})
     * @param isNews  Is this element change notably good or an unexpected bad change or loss?
     *                Sets the {@link SOCPlayerElement#isNews()} flag in messages sent by this method.
     *                If there are multiple resource types, flag is set only for the first type sent
     *                to avoid several alert sounds at client.
     * @param mainPlayer     Player number "giving" if isLose==true, otherwise "getting".
     *                For each nonzero resource involved, PLAYERELEMENT messages will be sent about this player.
     * @param tradingPlayer  Player number on other side of trade, or -1 if no second player is involved.
     *                If not -1, PLAYERELEMENT messages will also be sent about this player.
     * @param playerConn     Null to announce to the entire game, or mainPlayer's connection to send messages
     *                there instead of sending to all players in game.  Because trades are public, there is no
     *                such parameter for tradingPlayer.
     *
     * @see #reportTrade(SOCGame, int, int)
     * @see #reportBankTrade(SOCGame, SOCResourceSet, SOCResourceSet)
     * @see #reportRsrcGainGold(SOCGame, SOCPlayer, int, SOCResourceSet, boolean, boolean)
     * @see SOCGameMessageHandler#handleDISCARD(SOCGame, Connection, SOCDiscard)
     * @see SOCGameMessageHandler#handlePICKRESOURCES(SOCGame, Connection, SOCPickResources)
     * @see SOCGameMessageHandler#handleROLLDICE(SOCGame, Connection, SOCRollDice)
     * @since 1.1.00
     */
    void reportRsrcGainLoss
        (final String gaName, final ResourceSet resourceSet, final boolean isLoss, boolean isNews,
         final int mainPlayer, final int tradingPlayer, Connection playerConn)
    {
        final int losegain  = isLoss ? SOCPlayerElement.LOSE : SOCPlayerElement.GAIN;  // for pnA
        final int gainlose  = isLoss ? SOCPlayerElement.GAIN : SOCPlayerElement.LOSE;  // for pnB

        srv.gameList.takeMonitorForGame(gaName);

        for (int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; ++res)
        {
            // This works because SOCPlayerElement.SHEEP == SOCResourceConstants.SHEEP.

            final int amt = resourceSet.getAmount(res);
            if (amt <= 0)
                continue;

            if (playerConn != null)
                srv.messageToPlayer(playerConn, new SOCPlayerElement(gaName, mainPlayer, losegain, res, amt, isNews));
            else
                srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, mainPlayer, losegain, res, amt, isNews));
            if (tradingPlayer != -1)
                srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, tradingPlayer, gainlose, res, amt, isNews));
            if (isNews)
                isNews = false;
        }

        srv.gameList.releaseMonitorForGame(gaName);
    }

    /**
     * Report to game members what a player picked from the gold hex.
     * Sends {@link SOCPlayerElement} for resources and to reset the
     * {@link PEType#NUM_PICK_GOLD_HEX_RESOURCES} counter.
     * Sends text "playername has picked ___ from the gold hex.".
     * @param ga      Game with gaining player
     * @param player  Player gaining
     * @param pn      <tt>player</tt>{@link SOCPlayer#getPlayerNumber() .getPlayerNumber()}
     * @param rsrcs   Resources picked
     * @param isNews  Is this element change notably good or an unexpected bad change or loss?
     *                Sets the {@link SOCPlayerElement#isNews()} flag in messages sent by this method.
     *                If there are multiple resource types, flag is set only for the first type sent
     *                to avoid several alert sounds at client.
     * @param includeGoldHexText  If true, text ends with "from the gold hex." after the resource name.
     * @since 2.0.00
     */
    void reportRsrcGainGold
        (final SOCGame ga, final SOCPlayer player, final int pn, final SOCResourceSet rsrcs,
         final boolean isNews, final boolean includeGoldHexText)
    {
        final String gn = ga.getName();

        // Send SOCPlayerElement messages
        reportRsrcGainLoss(gn, rsrcs, false, isNews, pn, -1, null);
        srv.messageToGameKeyedSpecial(ga, true,
            ((includeGoldHexText) ? "action.picked.rsrcs.goldhex" : "action.picked.rsrcs"),
            player.getName(), rsrcs);
        srv.messageToGame(gn, new SOCPlayerElement
            (gn, pn, SOCPlayerElement.SET, PEType.NUM_PICK_GOLD_HEX_RESOURCES, 0));
    }

    // javadoc inherited from GameHandler
    /**
     * {@inheritDoc}
     *<P>
     * If {@link SOCGame#hasSeaBoard}: Once the board is made, send the updated
     * {@link SOCPotentialSettlements potential settlements}.
     *<P>
     * If this code changes, must also update {@link soctest.game.TestBoardLayouts#testSingleLayout(SOCScenario, int)}.
     */
    public void startGame(SOCGame ga)
    {
        if (ga == null)
            return;

        final String gaName = ga.getName();

        srv.numberOfGamesStarted++;  // TODO once multiple handler threads, encapsulate this

        /**
         * start the game, place any initial pieces.
         * If anything is added to this game object setup code,
         * update soctest.TestBoardLayouts.testSingleLayout(..).
         */

        ga.setGameEventListener(this);  // for playerEvent, gameEvent callbacks (since 2.0.00)
        ga.startGame();

        final int[][] legalSeaEdges;  // used on sea board; if null, all are legal
        if (ga.hasSeaBoard)
            legalSeaEdges = SOCBoardAtServer.startGame_scenarioSetup(ga);
        else
            legalSeaEdges = null;

        srv.gameList.takeMonitorForGame(gaName);

        try
        {

            /**
             * send the board layout
             */
            try
            {
                srv.messageToGameWithMon(gaName, getBoardLayoutMessage(ga));

                // For scenario option _SC_CLVI, the board layout message
                // includes villages and the general supply cloth count.
                // For _SC_PIRI, it includes the Pirate Path (additional layout part "PP").
            } catch (IllegalArgumentException e) {
                System.err.println("startGame: Cannot send board for " + gaName + ": " + e.getMessage());
                // the enclosing try-finally will releaseMonitorForGame(gaName) before returning

                return;
            }

            if (ga.hasSeaBoard)
            {
                // See also gatherBoardPotentials which has very similar code
                // and sends classic game's sole SOCPotentialSettlements message.

                // Send the updated Potential/Legal Settlement node list:
                // Since game is starting, assumes all players have same potential settlements.
                // Some boards may have multiple land areas.

                final SOCBoardLarge bl = (SOCBoardLarge) ga.getBoard();
                final HashSet<Integer>[] lan = bl.getLandAreasLegalNodes();
                final int pan = bl.getStartingLandArea();

                if (lan == null)
                    srv.messageToGameWithMon
                        (gaName, new SOCPotentialSettlements
                            (gaName, -1, new ArrayList<Integer>(ga.getPlayer(0).getPotentialSettlements())));
                else
                    srv.messageToGameWithMon
                        (gaName, new SOCPotentialSettlements(gaName, -1, null, pan, lan, legalSeaEdges));
            }

            /**
             * send the player info
             */
            boolean sentInitPiecesState = false;
            for (int i = 0; i < ga.maxPlayers; i++)
            {
                if (ga.isSeatVacant(i))
                    continue;

                final SOCPlayer pl = ga.getPlayer(i);

                final int[] counts = new int[(ga.hasSeaBoard) ? 4 : 3];
                counts[0] = pl.getNumPieces(SOCPlayingPiece.ROAD);
                counts[1] = pl.getNumPieces(SOCPlayingPiece.SETTLEMENT);
                counts[2] = pl.getNumPieces(SOCPlayingPiece.CITY);

                if (ga.hasSeaBoard)
                {
                    // Some scenarios like SC_PIRI may place initial pieces at fixed locations.
                    // Usually, pieces will be empty.
                    final Vector<SOCPlayingPiece> pieces = pl.getPieces();
                    if (! pieces.isEmpty())
                    {
                        if (! sentInitPiecesState)
                        {
                            // Temporary state change, to avoid initial-piece placement actions.
                            // The actual game state will be sent soon.
                            srv.messageToGameWithMon
                                (gaName, new SOCGameState(gaName, SOCGame.READY));
                            sentInitPiecesState = true;
                        }

                        for (SOCPlayingPiece pp : pieces)
                            srv.messageToGameWithMon
                                (gaName, new SOCPutPiece(gaName, i, pp.getType(), pp.getCoordinates()));

                        SOCPlayingPiece pp = pl.getFortress();
                        if (pp != null)
                            srv.messageToGameWithMon
                                (gaName, new SOCPutPiece(gaName, i, pp.getType(), pp.getCoordinates()));
                    }

                    counts[3] = pl.getNumPieces(SOCPlayingPiece.SHIP);
                }

                if (ga.clientVersionLowest >= SOCPlayerElements.MIN_VERSION)
                    srv.messageToGameWithMon(gaName, new SOCPlayerElements
                        (gaName, i, SOCPlayerElement.SET,
                         (ga.hasSeaBoard) ? ELEM_PIECETYPES_SEA : ELEM_PIECETYPES_CLASSIC, counts));
                else
                    for (int j = 0; j < counts.length; ++j)
                        srv.messageToGameWithMon(gaName, new SOCPlayerElement
                            (gaName, i, SOCPlayerElement.SET, ELEM_PIECETYPES_SEA[j], counts[j]));

                if (ga.clientVersionLowest < SOCPlayerElement.VERSION_FOR_CARD_ELEMENTS)
                    srv.messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, i, false));
            }

            if (ga.clientVersionLowest >= SOCPlayerElement.VERSION_FOR_CARD_ELEMENTS)
                srv.messageToGameWithMon(gaName, new SOCPlayerElement
                    (gaName, -1, SOCPlayerElement.SET, PEType.PLAYED_DEV_CARD_FLAG, 0));

            /**
             * send the number of dev cards.
             * needed for SC_PIRI because if PL<4, startGame() removed some cards.
             */
            srv.messageToGameWithMon(gaName, (ga.clientVersionLowest >= SOCGameElements.MIN_VERSION)
                ? new SOCGameElements(gaName, GEType.DEV_CARD_COUNT, ga.getNumDevCards())
                : new SOCDevCardCount(gaName, ga.getNumDevCards()));

            /**
             * ga.startGame() picks who goes first, but feedback is nice
             */
            srv.messageToGameKeyed
                (ga, false, "start.picking.random.starting.player");  // "Randomly picking a starting player..."

        } finally {
            srv.gameList.releaseMonitorForGame(gaName);
        }

        /**
         * send the game state and start the game.
         * send game state and whose turn it is.
         */
        if (ga.clientVersionLowest >= SOCGameState.VERSION_FOR_GAME_STATE_AS_FIELD)
        {
            srv.messageToGame(gaName, new SOCStartGame(gaName, ga.getGameState()));
            sendTurn(ga, false);
        } else {
            final int cpn = ga.getCurrentPlayerNumber();
            final boolean sendRoll = sendGameState(ga, false, false);
            srv.messageToGame(gaName, new SOCStartGame(gaName, 0));
            srv.messageToGame(gaName, new SOCTurn(gaName, cpn, 0));
            if (sendRoll)
                srv.messageToGame(gaName, new SOCRollDicePrompt(gaName, cpn));
        }
    }

    /**
     * After a player action during initial placement: Send new game state.
     * If current player changed, an initial-placement round ended ({@link SOCGame#isInitialPlacementRoundDone(int)}),
     * or regular game play started, announce the new player with
     * {@link #sendTurn(SOCGame, boolean)} or send {@link SOCRollDicePrompt}
     * to trigger auto-roll for the new player's client.
     *<P>
     * Call after an initial road/ship placement's {@link soc.game.SOCGame#putPiece(SOCPlayingPiece)},
     * or after a player has chosen free resources from a gold hex with
     * {@link soc.game.SOCGame#pickGoldHexResources(int, SOCResourceSet)},
     * and only after {@link #sendGameState(SOCGame, boolean, boolean)}.
     *
     * @param ga  The game
     * @param pl  Player who did the gold pick or piece placement action
     * @param c   {@code pl}'s connection
     * @param prevGameState  {@link soc.game.SOCGame#getGameState()} before piece placement,
     *     or for gold pick action the pre-reveal game state returned from {@code ga.pickGoldHexResources(..)}
     * @since 2.0.00
     */
    void sendTurnStateAtInitialPlacement
        (SOCGame ga, SOCPlayer pl, Connection c, final int prevGameState)
    {
        if (! checkTurn(c, ga))
        {
            // Player changed (or normal play started), announce new state and player
            sendTurn(ga, true);
        }
        else if (pl.isRobot() && ga.isInitialPlacementRoundDone(prevGameState))
        {
            // Player didn't change, but bot must be prompted to
            // place its next settlement or roll its first turn
            sendTurn(ga, false);
        }
        else
        {
            final boolean sendRoll = sendGameState(ga, false, false);

            if (sendRoll)
            {
                // When normal play starts, or after placing 2nd free road,
                // announce even though player unchanged,
                // to trigger auto-roll for the player client
                final String gaName = ga.getName();
                srv.messageToGame(gaName, new SOCRollDicePrompt(gaName, pl.getPlayerNumber()));
            }
        }
    }

    /**
     * At start of a new turn, send game its new {@link SOCGame#getGameState()}
     * and {@link SOCTurn} for whose turn it is. Optionally also send a prompt to roll.
     *<P>
     * The {@link SOCTurn} sent will have a field for the Game State unless
     * {@link SOCGame#clientVersionLowest} &lt; 2.0.00 ({@link SOCGameState#VERSION_FOR_GAME_STATE_AS_FIELD}),
     * in which case a separate {@link SOCGameState} message will be sent first.
     * Calls {@link #sendGameState(SOCGame, boolean, boolean)} in either case,
     * to send any text prompts or other gamestate-related messages.
     *<P>
     * sendTurn should be called whenever the current player changes, including
     * during and after initial placement.
     *
     * @param ga  the game
     * @param sendRollPrompt  If true, also send a {@code RollDicePrompt} message after {@code Turn}
     */
    void sendTurn(final SOCGame ga, boolean sendRollPrompt)
    {
        if (ga == null)
            return;

        final boolean useGSField = (ga.clientVersionLowest >= SOCGameState.VERSION_FOR_GAME_STATE_AS_FIELD);

        sendRollPrompt |= sendGameState(ga, useGSField, false);

        String gname = ga.getName();
        final int gs = ga.getGameState(),
            cpn = ga.getCurrentPlayerNumber();

        if (ga.clientVersionLowest >= SOCPlayerElement.VERSION_FOR_CARD_ELEMENTS)
            srv.messageToGame(gname, new SOCPlayerElement
                (gname, cpn, SOCPlayerElement.SET, PEType.PLAYED_DEV_CARD_FLAG, 0));
        else
            srv.messageToGame(gname, new SOCSetPlayedDevCard(gname, cpn, false));

        final SOCTurn turnMessage = new SOCTurn(gname, cpn, (useGSField) ? gs : 0);
        srv.messageToGame(gname, turnMessage);
        srv.recordGameEvent(gname, turnMessage);

        if (sendRollPrompt)
            srv.messageToGame(gname, new SOCRollDicePrompt(gname, cpn));
    }

    /**
     * Put together the board layout message for this game.
     * Message type will be {@link SOCBoardLayout} or {@link SOCBoardLayout2},
     * depending on {@link SOCBoard#getBoardEncodingFormat() ga.getBoard().getBoardEncodingFormat()}
     * and {@link SOCGame#getClientVersionMinRequired()}.
     *
     * @param  ga   the game
     * @return   a board layout message
     * @throws IllegalArgumentException  if game board's encoding is unrecognized
     */
    public static SOCMessage getBoardLayoutMessage(SOCGame ga)
        throws IllegalArgumentException
    {
        final SOCBoard board;
        int[] hexes;
        int[] numbers;
        int robber;

        board = ga.getBoard();
        final int bef = board.getBoardEncodingFormat();
        if (bef == SOCBoard.BOARD_ENCODING_6PLAYER ||
            bef == SOCBoard.BOARD_ENCODING_ORIGINAL)
        {
            // v1 or v2
            hexes = board.getHexLayout();
            numbers = board.getNumberLayout();
        } else {
            // v3
            hexes = null;
            numbers = null;
        }
        robber = board.getRobberHex();

        if ((bef == 1) && (ga.getClientVersionMinRequired() < SOCBoardLayout2.VERSION_FOR_BOARDLAYOUT2))
        {
            // SOCBoard.BOARD_ENCODING_ORIGINAL: v1
            return new SOCBoardLayout(ga.getName(), hexes, numbers, robber);
        }

        switch (bef)
        {
        case SOCBoard.BOARD_ENCODING_ORIGINAL: // v1
            // fall through to v2

        case SOCBoard.BOARD_ENCODING_6PLAYER:  // v2
            return new SOCBoardLayout2(ga.getName(), bef, hexes, numbers, board.getPortsLayout(), robber);

        case SOCBoard.BOARD_ENCODING_LARGE:    // v3
            final SOCBoardLarge bl = (SOCBoardLarge) board;
            return new SOCBoardLayout2
                (ga.getName(), bef, bl.getLandHexLayout(), board.getPortsLayout(),
                 robber, bl.getPirateHex(), bl.getPlayerExcludedLandAreas(), bl.getRobberExcludedLandAreas(),
                 bl.getAddedLayoutParts());

        default:
            throw new IllegalArgumentException("unknown board encoding v" + bef);
        }
    }

    /**
     * this is a debugging command that gives resources to a player.
     * Format: rsrcs: #cl #or #sh #wh #wo playername
     */
    private final void debugGiveResources(Connection c, String mes, SOCGame game)
    {
        final String gaName = game.getName();
        StringTokenizer st = new StringTokenizer(mes.substring(6));
        int[] resources = new int[SOCResourceConstants.WOOD + 1];
        int resourceType = SOCResourceConstants.CLAY;
        String name = "";
        boolean parseError = false;

        while (st.hasMoreTokens())
        {
            if (resourceType <= SOCResourceConstants.WOOD)
            {
                String token = st.nextToken();
                try
                {
                    int amt = Integer.parseInt(token);
                    if (amt < 0)
                        parseError = true;
                    resources[resourceType] = amt;
                    resourceType++;
                }
                catch (NumberFormatException e)
                {
                    parseError = true;
                    break;
                }
            }
            else
            {
                // read entire remaining string, in case there's
                //  a space in the player name ("robot 7"),
                //  by choosing an unlikely separator character
                name = st.nextToken(Character.toString( (char) 1 )).trim();
                break;
            }
        }

        SOCPlayer pl = null;
        if (! parseError)
        {
            pl = debug_getPlayer(c, game, name);
            if (pl == null)
                parseError = true;
        }

        if (parseError)
        {
            srv.messageToPlayer(c, gaName, "### Usage: " + DEBUG_COMMANDS_HELP_RSRCS);
            srv.messageToPlayer(c, gaName, DEBUG_COMMANDS_HELP_PLAYER);

            return;  // <--- early return ---
        }

        SOCResourceSet rset = new SOCResourceSet();
        int pnum = pl.getPlayerNumber();
        final boolean hasOldClients = (game.clientVersionLowest < SOCPlayerElements.MIN_VERSION);
        StringBuilder outTxt = new StringBuilder("### " + pl.getName() + " gets");  // I18N OK: debug only

        for (resourceType = SOCResourceConstants.CLAY;
             resourceType <= SOCResourceConstants.WOOD; ++resourceType)
        {
            final int amt = resources[resourceType];
            outTxt.append(' ');
            outTxt.append(amt);
            if (amt == 0)
                continue;

            rset.add(amt, resourceType);

            // SOCResourceConstants.CLAY == SOCPlayerElement.CLAY
            if (hasOldClients)
                srv.messageToGame
                    (gaName, new SOCPlayerElement(gaName, pnum, SOCPlayerElement.GAIN, resourceType, amt));
        }
        if (! hasOldClients)
            srv.messageToGame
                (gaName, new SOCPlayerElements(gaName, pnum, SOCPlayerElement.GAIN, rset));

        pl.getResources().add(rset);

        srv.messageToGame(gaName, outTxt.toString());
    }

    /** this is a debugging command that gives a dev card to a player.
     *  <PRE> dev: cardtype player </PRE>
     *  For card-types numbers, see {@link SOCDevCardConstants}
     *  or {@link #DEBUG_COMMANDS_HELP_DEV_TYPES}.
     */
    private final void debugGiveDevCard(Connection c, String mes, SOCGame game)
    {
        StringTokenizer st = new StringTokenizer(mes.substring(5));
        String name = "";
        int cardType = -1;
        boolean parseError = false;

        while (st.hasMoreTokens())
        {
            if (cardType < 0)
            {
                try
                {
                    cardType = Integer.parseInt(st.nextToken());
                    if ((cardType < SOCDevCardConstants.MIN_KNOWN) || (cardType >= SOCDevCardConstants.MAXPLUSONE))
                        parseError = true;  // Can't give unknown dev cards
                }
                catch (NumberFormatException e)
                {
                    parseError = true;
                    break;
                }
            }
            else
            {
                // get all of the line, in case there's a space in the player name ("robot 7"),
                //  by choosing an unlikely separator character
                name = st.nextToken(Character.toString( (char) 1 )).trim();
                break;
            }
        }

        SOCPlayer pl = null;
        if (! parseError)
        {
            pl = debug_getPlayer(c, game, name);
            if (pl == null)
                parseError = true;
        }

        if (parseError)
        {
            srv.messageToPlayer(c, game.getName(), "### Usage: " + DEBUG_COMMANDS_HELP_DEV);
            srv.messageToPlayer(c, game.getName(), DEBUG_COMMANDS_HELP_PLAYER);
            srv.messageToPlayer(c, game.getName(), DEBUG_COMMANDS_HELP_DEV_TYPES);

            return;  // <--- early return ---
        }

        pl.getInventory().addDevCard(1, SOCInventory.NEW, cardType);

        final int pnum = pl.getPlayerNumber();
        if ((cardType != SOCDevCardConstants.KNIGHT)
            || (game.clientVersionLowest >= SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES))
        {
            srv.messageToGame(game.getName(), new SOCDevCardAction
                (game.getName(), pnum, SOCDevCardAction.DRAW, cardType));
        } else {
            srv.messageToGameForVersions
                (game, -1, SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES - 1,
                 new SOCDevCardAction
                     (game.getName(), pnum, SOCDevCardAction.DRAW, SOCDevCardConstants.KNIGHT_FOR_VERS_1_X), true);
            srv.messageToGameForVersions
                (game, SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES, Integer.MAX_VALUE,
                 new SOCDevCardAction
                     (game.getName(), pnum, SOCDevCardAction.DRAW, SOCDevCardConstants.KNIGHT), true);
        }
        srv.messageToGameKeyedSpecial(game, true, "debug.dev.gets", pl.getName(), Integer.valueOf(cardType));
            // ""### joe gets a Road Building card."
    }

    /**
     * Given a player {@code name} or player number, find that player in the game.
     * If not found by name, or player number doesn't match expected format, sends a message to the
     * requesting user.
     *
     * @param c  Connection of requesting debug user
     * @param ga  Game to find player
     * @param name  Player name, or player position number in format "{@code #3}"
     *     numbered 0 to {@link SOCGame#maxPlayers ga.maxPlayers}-1 inclusive
     * @return  {@link SOCPlayer} with this name or number, or {@code null} if an error was sent to the user
     * @since 1.1.20
     */
    private SOCPlayer debug_getPlayer(final Connection c, final SOCGame ga, final String name)
    {
        if (name.length() == 0)
        {
            return null;  // <--- early return ---
        }

        SOCPlayer pl = null;

        if (name.startsWith("#") && (name.length() > 1) && Character.isDigit(name.charAt(1)))
        {
            String err = null;
            final int max = ga.maxPlayers - 1;
            try
            {
                final int i = Integer.parseInt(name.substring(1).trim());
                if (i > max)
                    err = "Max player number is " + Integer.toString(max);
                else if (ga.isSeatVacant(i))
                    err = "Player number " + Integer.toString(i) + " is vacant";
                else
                    pl = ga.getPlayer(i);
            }
            catch (NumberFormatException e) {
                err = "Player number format is # followed by the number (0 to "
                    + Integer.toString(max) + " inclusive)";
            }

            if (err != null)
            {
                srv.messageToPlayer(c, ga.getName(), "### " + err);

                return null;  // <--- early return ---
            }
        }

        if (pl == null)
            pl = ga.getPlayer(name);
        if (pl == null)
            srv.messageToPlayer(c, ga.getName(), "### Player name not found: " + name);

        return pl;
    }

    // javadoc inherited from GameHandler
    public void endTurnIfInactive(final SOCGame ga, final long currentTimeMillis)
    {
        final int gameState = ga.getGameState();
        final boolean isDiscardOrPickRsrc = (gameState == SOCGame.WAITING_FOR_DISCARDS)
            || (gameState == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
            || (gameState == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE);

        SOCPlayer pl = ga.getPlayer(ga.getCurrentPlayerNumber());

        if (isDiscardOrPickRsrc)
        {
            // Check if we're waiting on any humans too, not on robots only

            SOCPlayer plEnd = null;  // bot the game is waiting to hear from
            for (int pn = 0; pn < ga.maxPlayers; ++pn)
            {
                final SOCPlayer pli = ga.getPlayer(pn);
                if ((! pli.getNeedToDiscard()) && (pli.getNeedToPickGoldHexResources() == 0))
                    continue;

                if (pli.isRobot())
                {
                    if (plEnd == null)
                        plEnd = pli;
                } else {
                    return;  // <--- Waiting on humans, don't end bot's turn ---
                }
            }

            if (plEnd == null)
                return;  // <--- Not waiting on any bot ---

            pl = plEnd;
        } else {
            if (! pl.isRobot())
                return;  // <--- not a robot's turn, and not isDiscardOrPickRsrc ---
        }

        final SOCTradeOffer plCurrentOffer = pl.getCurrentOffer();
        if (plCurrentOffer != null)
        {
            // Robot is waiting for response to a trade offer;
            // have the humans all responded already?
            boolean waitingForHuman = false;
            for (int pn = 0; pn < ga.maxPlayers; ++pn)
                if (plCurrentOffer.isWaitingReplyFrom(pn) && ! ga.getPlayer(pn).isRobot())
                {
                    waitingForHuman = true;
                    break;
                }

            // If waiting for any humans' response, check against that longer timeout
            if (waitingForHuman)
            {
                final long tradeInactiveTime
                    = currentTimeMillis - (1000L * ROBOT_FORCE_ENDTURN_TRADEOFFER_SECONDS);
                if (ga.lastActionTime > tradeInactiveTime)
                    return;  // <--- Wait longer for humans ---
            }
        }

        new SOCForceEndTurnThread(srv, this, ga, pl).start();
    }

    /**
     * A bot is unresponsive, or a human player has left the game.
     * End this player's turn cleanly, or force-end if needed.
     *<P>
     * Can be called for a player still in the game, or for a player
     * who has left ({@link SOCGame#removePlayer(String, boolean)} has been called).
     * Can be called for a player who isn't current player; in that case
     * it takes action if the game was waiting for the player (picking random
     * resources for discard or gold-hex picks) but won't end the current turn.
     *<P>
     * If they were placing an initial road, also cancels that road's
     * initial settlement.
     *<P>
     * <b>Locks:</b> Must not have ga.takeMonitor() when calling this method.
     * May or may not have <tt>gameList.takeMonitorForGame(ga)</tt>;
     * use <tt>hasMonitorFromGameList</tt> to indicate.
     *<P>
     * Not public, but package visibility, for use by {@link SOCForceEndTurnThread} for {@link SOCGameTimeoutChecker}.
     *
     * @param ga   The game to end turn if called for current player, or to otherwise stop waiting for a player
     * @param plNumber  player.getNumber; may or may not be current player
     * @param plName    player.getName
     * @param plConn    player's client connection
     * @param hasMonitorFromGameList  if false, have not yet called
     *          {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(ga)};
     *          if false, this method will take this monitor at its start,
     *          and release it before returning.
     * @return true if the turn was ended and game is still active;
     *          false if we find that all players have left and
     *          the gamestate has been changed here to {@link SOCGame#OVER OVER}.
     */
    boolean endGameTurnOrForce
        (SOCGame ga, final int plNumber, final String plName, Connection plConn,
         final boolean hasMonitorFromGameList)
    {
        boolean gameStillActive = true;

        final String gaName = ga.getName();
        if (! hasMonitorFromGameList)
        {
            srv.gameList.takeMonitorForGame(gaName);
        }
        final int cpn = ga.getCurrentPlayerNumber();
        final int gameState = ga.getGameState();

        /**
         * Is a board-reset vote is in progress?
         * If they're still a sitting player, to keep the game
         * moving, fabricate their response: vote No.
         */
        boolean gameVotingActiveDuringStart = false;

        if (ga.getResetVoteActive())
        {
            if (gameState <= SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
                gameVotingActiveDuringStart = true;

            if ((! ga.isSeatVacant(plNumber))
                && (ga.getResetPlayerVote(plNumber) == SOCGame.VOTE_NONE))
            {
                srv.gameList.releaseMonitorForGame(gaName);
                ga.takeMonitor();
                srv.resetBoardVoteNotifyOne(ga, plNumber, plName, false);
                ga.releaseMonitor();
                srv.gameList.takeMonitorForGame(gaName);
            }
        }

        /**
         * Now end their turn, or handle any needed responses if not current player.
         * Don't call forceEndGameTurn()/ga.forceEndTurn() unless we need to.
         */
        if (plNumber == cpn)
        {
            /**
             * End their turn just to keep the game limping along.
             * To prevent deadlock, we must release gamelist's monitor for
             * this game before calling endGameTurn.
             */

            if ((gameState == SOCGame.START1B) || (gameState == SOCGame.START2B) || (gameState == SOCGame.START3B))
            {
                /**
                 * Leaving during initial road placement.
                 * Cancel the settlement they just placed,
                 * and send that cancel to the other players.
                 * Don't change gameState yet.
                 * Note that their most recent init settlement is removed here,
                 * but not earlier settlement(s). (That would impact the robots much more.)
                 */
                SOCPlayer pl = ga.getPlayer(plNumber);
                SOCSettlement pp = new SOCSettlement(pl, pl.getLastSettlementCoord(), null);
                ga.undoPutInitSettlement(pp);
                ga.setGameState(gameState);  // state was changed by undoPutInitSettlement
                srv.messageToGameWithMon(gaName, new SOCCancelBuildRequest(gaName, SOCSettlement.SETTLEMENT));
            }

            if (ga.canEndTurn(plNumber) && (gameState != SOCGame.PLACING_FREE_ROAD1))
            {
                srv.gameList.releaseMonitorForGame(gaName);
                ga.takeMonitor();
                endGameTurn(ga, null, true);
                ga.releaseMonitor();
                srv.gameList.takeMonitorForGame(gaName);
            } else {
                /**
                 * Cannot easily end turn: Must back out something in progress.
                 * May or may not end turn; see javadocs
                 * of forceEndGameTurn and game.forceEndTurn.
                 * All start phases are covered here (START1A..START2B)
                 * because canEndTurn returns false in those gameStates.
                 * Also includes PLACING_FREE_ROAD1 so the dev card is returned to player
                 * (unlike when a player actively decides to end their turn in that state).
                 */
                srv.gameList.releaseMonitorForGame(gaName);
                ga.takeMonitor();
                if (gameVotingActiveDuringStart)
                {
                    /**
                     * If anyone has requested a board-reset vote during
                     * game-start phases, we have to tell clients to cancel
                     * the vote request, because {@link soc.message.SOCTurn}
                     * isn't always sent during start phases.  (Voting must
                     * end when the turn ends.)
                     */
                    srv.messageToGame(gaName, new SOCResetBoardReject(gaName));
                    ga.resetVoteClear();
                }

                /**
                 * Force turn to end
                 */
                gameStillActive = forceEndGameTurn(ga, plName);
                ga.releaseMonitor();
                if (gameStillActive)
                {
                    srv.gameList.takeMonitorForGame(gaName);
                }
            }
        }
        else
        {
            /**
             * Check if game is waiting for input from the player who
             * is leaving, but who isn't current player.
             * To keep the game moving, fabricate their response.
             * - Board-reset voting: Handled above.
             * - Waiting for discard: Handle here.
             * - Waiting for gold-hex pick: Handle here.
             */
            if (   ((gameState == SOCGame.WAITING_FOR_DISCARDS) && ga.getPlayer(plNumber).getNeedToDiscard())
                || (  ((gameState == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                       || (gameState == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE))
                    && (ga.getPlayer(plNumber).getNeedToPickGoldHexResources() > 0)  ))
            {
                /**
                 * For discard, tell the discarding player's client that they discarded the resources,
                 * tell everyone else that the player discarded unknown resources.
                 * For gold pick, announce the picked resources.
                 */
                srv.gameList.releaseMonitorForGame(gaName);
                System.err.println("L5789: Waiting too long for bot discard or gain: game="
                    + ga.getName() + ", pn=" + plNumber + "  " + plName);
                ga.takeMonitor();
                forceGamePlayerDiscardOrGain(ga, cpn, plConn, plName, plNumber);
                sendGameState(ga, false, false);  // WAITING_FOR_DISCARDS or MOVING_ROBBER for discard;
                    // PLAY1 or WAITING_FOR_PICK_GOLD_RESOURCE for gain
                ga.releaseMonitor();
                srv.gameList.takeMonitorForGame(gaName);
            }

        }  // current player?

        if (! hasMonitorFromGameList)
        {
            srv.gameList.releaseMonitorForGame(gaName);
        }

        return gameStillActive;
    }

    /**
     * Force this player (not current player) to discard, or gain random resources from a gold hex,
     * and report resources to all players. Does not send gameState, which may have changed when
     * this method called {@link SOCGame#playerDiscardOrGainRandom(int, boolean)}.
     *<P>
     * Discards if {@link SOCGame#getGameState() cg.getGameState()} == {@link SOCGame#WAITING_FOR_DISCARDS},
     * otherwise picks enough random resources for {@link SOCPlayer#getNeedToPickGoldHexResources()}.
     *<P>
     * Also calls {@code pn}'s {@link SOCPlayer#addForcedEndTurn()} because we're forcing an action they
     * should have taken on their own.
     *<P>
     * Assumes, as {@link #endGameTurn(SOCGame, SOCPlayer, boolean)} does:
     * <UL>
     * <LI> ga.takeMonitor already called (not the same as {@link SOCGameList#takeMonitorForGame(String)})
     * <LI> gamelist.takeMonitorForGame is NOT called, we do NOT have that monitor
     * </UL>
     *
     * @param cg  Game object
     * @param cpn Game's current player number
     * @param c   Connection of discarding/gaining player
     * @param plName Discarding/gaining player {@code pn}'s name, for GameTextMsg
     * @param pn  Player number who must discard/gain resources
     * @throws IllegalStateException if {@code pn} is current player, or if incorrect game state or incorrect
     *     player status; see {@link SOCGame#playerDiscardOrGainRandom(int, boolean)} for details
     * @since 1.1.00
     */
    private final void forceGamePlayerDiscardOrGain
        (final SOCGame cg, final int cpn, final Connection c, final String plName, final int pn)
        throws IllegalStateException
    {
        final boolean isDiscard = (cg.getGameState() == SOCGame.WAITING_FOR_DISCARDS);

        final SOCResourceSet rset = cg.playerDiscardOrGainRandom(pn, isDiscard);

        cg.getPlayer(pn).addForcedEndTurn();

        // Report resources lost or gained; see also forceEndGameTurn for same reporting code.

        final String gaName = cg.getName();
        final int totalRes = rset.getTotal();
        if (isDiscard)
        {
            if ((c != null) && c.isConnected())
                reportRsrcGainLoss(gaName, rset, true, true, pn, -1, c);

            srv.messageToGameExcept
                (gaName, c, new SOCPlayerElement
                    (gaName, pn, SOCPlayerElement.LOSE, PEType.UNKNOWN_RESOURCE, totalRes, true),
                 true);
            srv.messageToGameKeyed(cg, true, "action.discarded", plName, totalRes);  // "{0} discarded {1} resources."

            System.err.println("Forced discard: " + totalRes + " from " + plName + " in game " + gaName);
        } else {
            // Send SOCPlayerElement messages, "gains" text
            reportRsrcGainGold(cg, cg.getPlayer(pn), pn, rset, true, false);

            System.err.println("Forced gold picks: " + totalRes + " to " + plName + " in game " + gaName);
        }
    }

    /**
     * Listener callback for scenario events on the large sea board which affect the game or board,
     * not a specific player. For example, a hex might be revealed from fog.
     *<P>
     * <em>Threads:</em> The game's treater thread handles incoming client messages and calls
     * game methods that change state. Those same game methods will trigger the scenario events;
     * so, the treater thread will also run this <tt>gameEvent</tt> callback.
     *
     * @param ga  Game
     * @param evt  Event code
     * @param detail  Game piece, coordinate, or other data about the event, or null, depending on <tt>evt</tt>
     * @see #playerEvent(SOCGame, SOCPlayer, SOCPlayerEvent, boolean, Object)
     * @since 2.0.00
     */
    public void gameEvent(final SOCGame ga, final SOCGameEvent evt, final Object detail)
    {
        switch (evt)
        {
        case SGE_FOG_HEX_REVEALED:
            {
                final SOCBoard board = ga.getBoard();
                final int hexCoord = ((Integer) detail).intValue(),
                          hexType  = board.getHexTypeFromCoord(hexCoord),
                          diceNum  = board.getNumberOnHexFromCoord(hexCoord);
                final String gaName = ga.getName();

                srv.messageToGame
                    (gaName, new SOCRevealFogHex(gaName, hexCoord, hexType, diceNum));

                final int cpn = ga.getCurrentPlayerNumber();
                if (cpn != -1)
                {
                    final int res = board.getHexTypeFromNumber(hexCoord);
                    if ((res >= SOCResourceConstants.CLAY) && (res <= SOCResourceConstants.WOOD))
                    {
                        ga.pendingMessagesOut.add
                            (new SOCPlayerElement(gaName, cpn, SOCPlayerElement.GAIN, res, 1, true));
                        ga.pendingMessagesOut.add
                            (new UnlocalizedString
                                (true, "event.fog.reveal",  // "{0} gets 1 {1,rsrcs} by revealing the fog hex."
                                 ga.getPlayer(cpn).getName(), Integer.valueOf(1), Integer.valueOf(res)));
                    }
                }
            }
            break;

        case SGE_CLVI_WIN_VILLAGE_CLOTH_EMPTY:
            {
                // don't need to send to client to designate this event & winning player number:
                //   general gameOVER code will send that player number

                srv.messageToGameKeyed(ga, true, "event.sc_clvi.game.ending.villages");
                    // "Game is ending: Less than half the villages have cloth remaining."
                srv.messageToGameKeyed(ga, true, "event.won.special.cond", ((SOCPlayer) detail).getName());
                    // "{0} has won due to this special win condition."
            }
            break;

        case SGE_PIRI_LAST_FORTRESS_FLEET_DEFEATED:
            {
                final String gaName = ga.getName();
                srv.messageToGameKeyedSpecial(ga, true, "event.sc_piri.fleet.defeated");
                    // "All pirate fortresses have been recaptured, the pirate fleet is defeated."
                srv.messageToGame(gaName, new SOCMoveRobber(gaName, ga.getCurrentPlayerNumber(), 0));
            }
            break;

        default:
            // Some game events, such as SGE_STARTPLAY_BOARD_SPECIAL_NODES_EMPTIED, are ignored at the server.
            // Default case does nothing, prevents a compiler warning.
        }
    }

    /**
     * Listener callback for per-player game/scenario events on the large sea board.
     * For example, there might be an SVP awarded for settlements.
     * Server sends messages to the game to announce it (PLAYERELEMENT,
     * {@link #updatePlayerSVPPendingMessage(SOCGame, SOCPlayer, int, String)}, etc).
     *<P>
     * <em>Threads:</em> The game's treater thread handles incoming client messages and calls
     * game methods that change state. Those same game methods will trigger the scenario events;
     * so, the treater thread will also run this <tt>playerEvent</tt> callback.
     *
     * @param ga  Game
     * @param pl  Player
     * @param evt  Event code
     * @param flagsChanged  True if this event changed {@link SOCPlayer#getPlayerEvents()},
     *             {@link SOCPlayer#getSpecialVP()}, or another flag documented for <tt>evt</tt> in
     *             {@link SOCPlayerEvent}
     * @param obj  Object related to the event, or null; documented for <tt>evt</tt> in {@link SOCPlayerEvent}.
     *             Example: The {@link SOCVillage} for {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     * @see #gameEvent(SOCGame, SOCGameEvent, Object)
     * @since 2.0.00
     */
    public void playerEvent(final SOCGame ga, final SOCPlayer pl, final SOCPlayerEvent evt,
        final boolean flagsChanged, final Object obj)
    {
        // Note: Some SOCGameHandler code assumes that player events are fired only during
        // SOCGameMessageHandler.handlePUTPIECE and handleMOVEPIECE.
        // Most handle* methods don't check pendingMessagesOut before sending game state.
        // If a new player event breaks this assumption, adjust SOCGameHandler.playerEvent(...)
        // and related code; search where SOCGame.pendingMessagesOut is used.

        final String gaName = ga.getName(),
                     plName = pl.getName();
        final int pn = pl.getPlayerNumber();

        boolean sendSVP = true;
        boolean sendPlayerEventsBitmask = true;

        switch (evt)
        {
        case SVP_SETTLED_ANY_NEW_LANDAREA:
            {
                final String newSettleEventStr =
                    (playerEvent_newSettlementIsByShip(ga, (SOCSettlement) obj))
                    ? "event.svp.sc_sany.island"  // "growing past the main island"
                    : "event.svp.sc_sany.area";   // "growing to a new area"
                updatePlayerSVPPendingMessage(ga, pl, 1, newSettleEventStr);
            }
            break;

        case SVP_SETTLED_EACH_NEW_LANDAREA:
            {
                final String newSettleEventStr =
                    (playerEvent_newSettlementIsByShip(ga, (SOCSettlement) obj))
                    ? "event.svp.sc_seac.island"  // "settling a new island"
                    : "event.svp.sc_seac.area";   // "settling a new area"
                updatePlayerSVPPendingMessage(ga, pl, 2, newSettleEventStr);
                sendPlayerEventsBitmask = false;
                final int las = pl.getScenarioSVPLandAreas();
                if (las != 0)
                    ga.pendingMessagesOut.add(new SOCPlayerElement
                        (gaName, pn, SOCPlayerElement.SET,
                         PEType.SCENARIO_SVP_LANDAREAS_BITMASK, las));
            }
            break;

        case CLOTH_TRADE_ESTABLISHED_VILLAGE:
            {
                sendSVP = false;
                if (! flagsChanged)
                    sendPlayerEventsBitmask = false;
                ga.pendingMessagesOut.add(new UnlocalizedString
                    ("event.sc_clvi.established", plName));  // "{0} established a trade route with a village."
                if (flagsChanged)
                    srv.messageToPlayerPendingKeyed(pl, gaName, "event.sc_clvi.not.prevented.pirate");
                        // "You are no longer prevented from moving the pirate ship."

                // Player gets 1 cloth for establishing trade
                SOCVillage vi = (SOCVillage) obj;
                srv.messageToGame(gaName, new SOCPieceValue
                    (gaName, SOCPlayingPiece.VILLAGE, vi.getCoordinates(), vi.getCloth(), 0));
                srv.messageToGame(gaName, new SOCPlayerElement
                    (gaName, pn, SOCPlayerElement.SET, PEType.SCENARIO_CLOTH_COUNT, pl.getCloth()));
            }
            break;

        case DEV_CARD_REACHED_SPECIAL_EDGE:
            {
                sendPlayerEventsBitmask = false;
                sendSVP = false;
                IntPair edge_cardType = (IntPair) obj;
                Connection c = srv.getConnection(plName);
                ga.pendingMessagesOut.add(new UnlocalizedString
                    ("action.built.sc_ftri.dev", plName));  // "{0} gets a Development Card as a gift from the Forgotten Tribe."
                srv.messageToPlayer
                    (c, new SOCDevCardAction(gaName, pn, SOCDevCardAction.DRAW, edge_cardType.getB()));
                srv.messageToGameExcept
                    (gaName, c, new SOCDevCardAction(gaName, pn, SOCDevCardAction.DRAW, SOCDevCardConstants.UNKNOWN), true);
                srv.messageToGame(gaName, new SOCSimpleAction
                    (gaName, -1, SOCSimpleAction.BOARD_EDGE_SET_SPECIAL, edge_cardType.getA(), 0));
            }
            break;

        case SVP_REACHED_SPECIAL_EDGE:
            {
                updatePlayerSVPPendingMessage(ga, pl, 1, "event.svp.sc_ftri.gift");  // "a gift from the Forgotten Tribe"
                sendPlayerEventsBitmask = false;
                srv.messageToGame(gaName, new SOCSimpleAction
                    (gaName, -1, SOCSimpleAction.BOARD_EDGE_SET_SPECIAL, ((Integer) obj).intValue(), 0));
            }
            break;

        case REMOVED_TRADE_PORT:
            {
                sendPlayerEventsBitmask = false;
                sendSVP = false;
                IntPair edge_portType = (IntPair) obj;
                final int edge = edge_portType.getA(),
                          portType = edge_portType.getB();
                if ((edge & 0xFF) <= ga.getBoard().getBoardWidth())
                    // announce removal from board, unless (for debugging)
                    // this port wasn't really on the board at clients
                    srv.messageToGame(gaName, new SOCSimpleAction
                        (gaName, pn, SOCSimpleAction.TRADE_PORT_REMOVED, edge, portType));
                if (ga.getGameState() == SOCGame.PLACING_INV_ITEM)
                {
                    // Removal happens during ship piece placement, which is followed at server with sendGameState.
                    // When sendGameState gives the new state, client will prompt current player to place now.
                    // We just need to send the client PLACING_EXTRA, for the port type and not-cancelable flag.
                    Connection c = srv.getConnection(plName);
                    srv.messageToPlayer(c, new SOCInventoryItemAction
                        (gaName, pn, SOCInventoryItemAction.PLACING_EXTRA, -portType, false, false, false));
                } else {
                    // port was added to player's inventory;
                    // if this message changes, also update SOCGameHandler.processDebugCommand_scenario
                    srv.messageToGame(gaName, new SOCInventoryItemAction
                        (gaName, pn, SOCInventoryItemAction.ADD_PLAYABLE, -portType, false, false, true));
                }
            }
            break;

        default:
            break;  // Suppress warning; not all enum values need a handler here
        }

        if (sendSVP)
            ga.pendingMessagesOut.add(new SOCPlayerElement
                (gaName, pn, SOCPlayerElement.SET,
                 PEType.SCENARIO_SVP, pl.getSpecialVP()));

        if (sendPlayerEventsBitmask)
            ga.pendingMessagesOut.add(new SOCPlayerElement
                (gaName, pn, SOCPlayerElement.SET,
                 PEType.PLAYEREVENTS_BITMASK, pl.getPlayerEvents()));
    }

    /**
     * For Special VP player events, check if a new settlement was apparently reached by land or sea.
     * Most new LandAreas are on other islands, but a few (SC_TTD) are on the main island.
     * @param ga  Game with this new settlement
     * @param se  Newly placed settlement to check, passed to
     *     {@link #playerEvent(SOCGame, SOCPlayer, SOCPlayerEvent, boolean, Object)}
     * @return  Does the new settlement have more adjacent ships than roads?
     * @since 2.0.00
     */
    private final boolean playerEvent_newSettlementIsByShip(final SOCGame ga, final SOCSettlement se)
    {
        if (se == null)
            return true;  // shouldn't happen, but fail gracefully; most new areas are on new islands

        final SOCBoard board = ga.getBoard();

        int shipCount = 0, roadCount = 0;
        for (final int edge : board.getAdjacentEdgesToNode(se.getCoordinates()))
        {
            SOCRoutePiece pp = board.roadOrShipAtEdge(edge);
            if (pp == null)
                continue;

            if (pp.isRoadNotShip())
                ++roadCount;
            else
                ++shipCount;
        }

        return (shipCount > roadCount);
    }

    /**
     * A player has been awarded Special Victory Points (SVP), so send
     * a {@link SOCSVPTextMessage} to the game about the SVP description,
     * and also call {@link SOCPlayer#addSpecialVPInfo(int, String)}.
     * Should be called before sending {@link SOCPlayerElement}({@link PEType#SCENARIO_SVP SCENARIO_SVP}),
     * not after.
     *<P>
     * Adds the message to {@link SOCGame#pendingMessagesOut}; note that
     * right now, that field is checked only in
     * {@link SOCGameMessageHandler#handlePUTPIECE(SOCGame, Connection, SOCPutPiece)}
     * and {@link SOCGameMessageHandler#handleMOVEPIECE(SOCGame, Connection, SOCMovePiece)},
     * because no other method currently awards SVP.
     * @param ga  Game
     * @param pl  Player
     * @param svp  Number of SVP
     * @param descKey  String key for description of the player's action that led to SVP
     * @since 2.0.00
     * @see #sendGamePendingMessages(SOCGame, boolean)
     */
    private static void updatePlayerSVPPendingMessage(SOCGame ga, SOCPlayer pl, final int svp, final String descKey)
    {
        pl.addSpecialVPInfo(svp, descKey);
        final String gaName = ga.getName();
        ga.pendingMessagesOut.add(new SOCSVPTextMessage(gaName, pl.getPlayerNumber(), svp, descKey));
    }

    /**
     * Sends the contents of this game's {@link SOCGame#pendingMessagesOut} and its players'
     * {@link SOCPlayer#pendingMessagesOut}, then empties those lists.
     * To avoid unnecessary work here, check if the game's pending list is empty before calling this method.
     *<P>
     * <B>I18N:</B> Checks {@code pendingMessagesOut} for {@link SOCKeyedMessage}s and handles them accordingly.
     * Currently this is the only method that checks for those, because other places send text messages
     * immediately instead of queueing them and localizing/sending later.
     * Also checks for {@link UnlocalizedString}s, to be localized and sent with
     * {@link SOCServer#messageToGameKeyed(SOCGame, boolean, String, Object...)}
     * or {@link SOCServer#messageToGameKeyedSpecial(SOCGame, boolean, String, Object...)}.
     *<P>
     * <B>Locks:</B> If {@code takeMon} is true, takes and releases
     * {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gameName)}.
     * Otherwise call {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gameName)}
     * before calling this method.
     *
     * @param ga  game with pending messages
     * @param takeMon Should this method take and release game's monitor via
     *     {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gameName)}?
     *     True unless caller already holds that monitor.
     * @see #updatePlayerSVPPendingMessage(SOCGame, SOCPlayer, int, String)
     * @since 2.0.00
     */
    void sendGamePendingMessages(SOCGame ga, final boolean takeMon)
    {
        final String gaName = ga.getName();

        if (takeMon)
            srv.gameList.takeMonitorForGame(gaName);

        for (final Object msg : ga.pendingMessagesOut)
        {
            if (msg instanceof SOCKeyedMessage)
                srv.messageToGameKeyedType(ga, (SOCKeyedMessage) msg, false);
            else if (msg instanceof SOCMessage)
                srv.messageToGameWithMon(gaName, (SOCMessage) msg);
            else if (msg instanceof UnlocalizedString)
            {
                final UnlocalizedString us = (UnlocalizedString) msg;
                if (us.isSpecial)
                    srv.messageToGameKeyedSpecial(ga, false, us.key, us.params);
                else
                    srv.messageToGameKeyed(ga, false, us.key, us.params);
            }
            // else: ignore
        }
        ga.pendingMessagesOut.clear();

        for (SOCPlayer p : ga.getPlayers())
        {
            final List<Object> pq = p.pendingMessagesOut;
            final int L = pq.size();
            if (L >= 0)
            {
                final Connection c = srv.getConnection(p.getName());
                if (c != null)
                    for (int i = 0; i < L; ++i)
                        c.put(((SOCMessage) pq.get(i)));

                pq.clear();
            }
        }

        if (takeMon)
            srv.gameList.releaseMonitorForGame(gaName);
    }

}
