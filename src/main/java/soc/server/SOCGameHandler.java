/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013-2017 Jeremy D Monin <jeremy@nand.net>.
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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
import soc.message.SOCBoardLayout;
import soc.message.SOCBoardLayout2;
import soc.message.SOCBotJoinGameRequest;
import soc.message.SOCCancelBuildRequest;
import soc.message.SOCChangeFace;
import soc.message.SOCChoosePlayerRequest;
import soc.message.SOCClearOffer;
import soc.message.SOCDebugFreePlace;
import soc.message.SOCDevCardAction;
import soc.message.SOCDevCardCount;
import soc.message.SOCDiceResult;
import soc.message.SOCDiscard;
import soc.message.SOCDiscardRequest;
import soc.message.SOCFirstPlayer;
import soc.message.SOCGameMembers;
import soc.message.SOCGameServerText;
import soc.message.SOCGameState;
import soc.message.SOCGameStats;
import soc.message.SOCInventoryItemAction;
import soc.message.SOCJoinGame;
import soc.message.SOCJoinGameAuth;
import soc.message.SOCKeyedMessage;
import soc.message.SOCLocalizedStrings;
import soc.message.SOCLargestArmy;
import soc.message.SOCLastSettlement;
import soc.message.SOCLeaveGame;
import soc.message.SOCLongestRoad;
import soc.message.SOCMessage;
import soc.message.SOCMovePieceRequest;
import soc.message.SOCMoveRobber;
import soc.message.SOCPieceValue;
import soc.message.SOCPlayerElement;
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
import soc.util.SOCGameList;
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
    implements SOCScenarioEventListener
{
    /**
     * Force robot to end their turn after this much inactivity,
     * while they've made a trade offer. Default is 60 seconds.
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
     * {@link #processDebugCommand_scenario(Connection, String, String)}.
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
    public boolean processDebugCommand(Connection debugCli, String gaName, final String dcmd, final String dcmdU)
    {
        if (dcmdU.startsWith("RSRCS:"))
        {
            SOCGame ga = srv.getGame(gaName);
            if (ga != null)
                debugGiveResources(debugCli, dcmd, ga);
            return true;
        }
        else if (dcmdU.startsWith("DEV:"))
        {
            SOCGame ga = srv.getGame(gaName);
            if (ga != null)
                debugGiveDevCard(debugCli, dcmd, ga);
            return true;
        }
        else if (dcmd.charAt(0) != '*')
        {
            return false;
        }

        if (dcmdU.startsWith(DEBUG_CMD_FREEPLACEMENT))
        {
            processDebugCommand_freePlace(debugCli, gaName, dcmd.substring(DEBUG_CMD_FREEPLACEMENT.length()).trim());
            return true;
        } else if (dcmdU.startsWith(DEBUG_CMD_PFX_SCENARIO))
        {
            processDebugCommand_scenario(debugCli, gaName, dcmd.substring(DEBUG_CMD_PFX_SCENARIO.length()).trim());
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
     * @param gaName  Game to which this applies
     * @param arg  1 or 0, to turn on or off, or empty string or
     *    null to print current value
     * @since 1.1.12
     */
    final void processDebugCommand_freePlace
        (Connection c, final String gaName, final String arg)
    {
        SOCGame ga = srv.gameList.getGameData(gaName);
        if (ga == null)
            return;

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
                boolean toldRoll = sendGameState(ga, false);
                if (!checkTurn(c, ga))
                {
                    // Player changed (or play started), announce new player.
                    sendTurn(ga, toldRoll);
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
     * @param gaName  Game to which this applies
     * @param args  Debug command string from the user.
     *     Caller must remove prefix {@link #DEBUG_CMD_PFX_SCENARIO} and then {@link String#trim()}.
     * @since 2.0.00
     */
    private final void processDebugCommand_scenario
        (final Connection c, final String gaName, final String argStr)
    {
        if (argStr.length() == 0)
            return;

        final SOCGame ga = srv.gameList.getGameData(gaName);
        if (ga == null)
            return;
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
                // removePort calls scenarioEventListener.playerEvent(REMOVED_TRADE_PORT),
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
                    (gname, pl.getPlayerNumber(), SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 0));
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
         * send new state number; if game is now OVER,
         * also send end-of-game messages.
         */
        boolean wantsRollPrompt = sendGameState(ga, false);

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
         * send whose turn it is
         */
        sendTurn(ga, wantsRollPrompt);
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
     *               {@link #leaveGame(SOCGame, Connection)},
     *               their name within game object is already null.
     * @return true if the turn was ended and game is still active;
     *          false if we find that all players have left and
     *          the gamestate has been changed here to {@link SOCGame#OVER}.
     *
     * @see #endGameTurnOrForce(SOCGame, int, String, Connection, boolean)
     * @see SOCGame#forceEndTurn()
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
            srv.messageToGame(gaName, new SOCPlayerElement(gaName, cpn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 0));
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
                    reportRsrcGainLoss(gaName, resGainLoss, false, false, cpn, -1, null, null);
                }
            } else {
                Connection c = srv.getConnection(plName);
                if ((c != null) && c.isConnected())
                    reportRsrcGainLoss(gaName, resGainLoss, true, true, cpn, -1, null, c);
                int totalRes = resGainLoss.getTotal();
                srv.messageToGameExcept
                    (gaName, c, new SOCPlayerElement
                        (gaName, cpn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, totalRes, true),
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
                    if ((card == SOCDevCardConstants.KNIGHT) && (c.getVersion() < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
                        card = SOCDevCardConstants.KNIGHT_FOR_VERS_1_X;
                    srv.messageToPlayer(c, new SOCDevCardAction(gaName, cpn, SOCDevCardAction.ADDOLD, card));
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
                if (ga.clientVersionLowest >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES)
                {
                    srv.messageToGameExcept
                        (gaName, c, new SOCDevCardAction(gaName, cpn, SOCDevCardAction.ADDOLD, SOCDevCardConstants.UNKNOWN), true);
                } else {
                    srv.messageToGameForVersionsExcept
                        (ga, -1, SOCDevCardConstants.VERSION_FOR_NEW_TYPES - 1,
                         c, new SOCDevCardAction(gaName, cpn, SOCDevCardAction.ADDOLD, SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X), true);
                    srv.messageToGameForVersionsExcept
                        (ga, SOCDevCardConstants.VERSION_FOR_NEW_TYPES, Integer.MAX_VALUE,
                         c, new SOCDevCardAction(gaName, cpn, SOCDevCardAction.ADDOLD, SOCDevCardConstants.UNKNOWN), true);
                }

                srv.messageToGameKeyed(ga, true, "forceend.devcard.returned", plName);
                    // "{0}''s just-played development card was returned."
            }
        }

        /**
         * For initial placements, we don't end turns as normal.
         * (Player number may go forward or backwards, new state isn't PLAY, etc.)
         * Update clients' gamestate, but don't call endGameTurn.
         */
        final int forceRes = res.getResult();
        if ((forceRes == SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADV)
            || (forceRes == SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADVBACK))
        {
            if (res.didUpdateFP() || res.didUpdateLP())
            {
                // will cause clients to recalculate lastPlayer too
                srv.messageToGame(gaName, new SOCFirstPlayer(gaName, ga.getFirstPlayer()));
            }
            sendGameState(ga, false);
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
            sendGameState(ga, false);

        return (ga.getGameState() != SOCGame.OVER);
    }

    /**
     * Client has been approved to join game; send the entire state of the game to client.
     * Unless <tt>isTakingOver</tt>, send client join event to other players.
     *<P>
     * Does not add the client to the game's or server's list of players,
     * that should be done before calling this method.
     *<P>
     * Assumes NEWGAME (or NEWGAMEWITHOPTIONS) has already been sent out.
     * The game's first message<B>*</B> sent to the connecting client is JOINGAMEAUTH, unless isReset.
     *<P>
     * Among other messages, player names are sent via SITDOWN, and pieces on board
     * sent by PUTPIECE.  See comments here for further details.
     * If <tt>isTakingOver</tt>, some details are sent by calling
     * {@link #sitDown_sendPrivateInfo(SOCGame, Connection, int)}.
     * The group of messages sent here ends with GAMEMEMBERS, SETTURN and GAMESTATE.
     * Then, the entire game is sent a JOINGAME for the new game member.
     *<P>
     * *<B>I18N:</B> If the game has a {@link SOCScenario} and the client needs scenario info or localized strings
     * for the scenario name and description, {@link SOCScenarioInfo} or {@link SOCLocalizedStrings} is
     * sent before JOINGAMEAUTH.  This covers i18n and scenarios added or changed between the client's
     * and server's version.
     *
     * @param gameData Game to join
     * @param c        The connection of joining client
     * @param isReset  Game is a board-reset of an existing game.  This is always false when
     *                 called from SOCServer instead of from inside the SOCGameHandler.
     * @param isTakingOver  Client is re-joining; this connection replaces an earlier one which
     *                      is defunct because of a network problem.
     *                      If <tt>isTakingOver</tt>, don't send anything to other players.
     *
     * @see SOCServer#connectToGame(Connection, String, Map)
     * @see SOCServer#createOrJoinGameIfUserOK(Connection, String, String, String, Map)
     */
    @SuppressWarnings("unchecked")
    public void joinGame(SOCGame gameData, Connection c, final boolean isReset, final boolean isTakingOver)
    {
        boolean hasRobot = false;  // If game's already started, true if any bot is seated (can be taken over)
        String gameName = gameData.getName();
        if (! isReset)
        {
            // First, send updated scenario info or localized strings if needed
            // (SOCScenarioInfo or SOCLocalizedStrings); checks c.getVersion(), scd.scenariosInfoSent etc.

            final String gameScen = gameData.getGameOptionStringValue("SC");
            if (gameScen != null)
                srv.sendGameScenarioInfo(gameScen, null, c, false);

            // Now, join game
            c.put(SOCJoinGameAuth.toCmd(gameName));
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_OK, c.getLocalized("member.welcome")));  // "Welcome to Java Settlers of Catan!"
        }

        //c.put(SOCGameState.toCmd(gameName, gameData.getGameState()));
        for (int i = 0; i < gameData.maxPlayers; i++)
        {
            /**
             * send them the already-seated player information;
             * if isReset, don't send, because sitDown will
             * be sent from resetBoardAndNotify.
             */
            if (! isReset)
            {
                SOCPlayer pl = gameData.getPlayer(i);
                String plName = pl.getName();
                if ((plName != null) && ! gameData.isSeatVacant(i))
                {
                    final boolean isRobot = pl.isRobot();
                    if (isRobot)
                        hasRobot = true;
                    c.put(SOCSitDown.toCmd(gameName, plName, i, isRobot));
                }
            }

            /**
             * send the seat lock information
             */
            final SOCGame.SeatLockState sl = gameData.getSeatLock(i);
            if ((sl != SOCGame.SeatLockState.CLEAR_ON_RESET) || (c.getVersion() >= 2000))
                srv.messageToPlayer(c, new SOCSetSeatLock(gameName, i, sl));
            else
                srv.messageToPlayer(c, new SOCSetSeatLock(gameName, i, SOCGame.SeatLockState.LOCKED));  // old client
        }

        c.put(getBoardLayoutMessage(gameData).toCmd());
        //    No need to catch IllegalArgumentException:
        //    Since game is already started, getBoardLayoutMessage has previously
        //    been called for the creating player, and the board encoding is OK.

        /**
         * if game hasn't started yet, each player's potentialSettlements are
         * identical, so send that info once for all players.
         */
        if ((gameData.getGameState() == SOCGame.NEW)
            && (c.getVersion() >= SOCPotentialSettlements.VERSION_FOR_PLAYERNUM_ALL))
        {
            final HashSet<Integer> psList = gameData.getPlayer(0).getPotentialSettlements();

            // Some boards may have multiple land areas.
            // See also below, and startGame which has very similar code.
            final HashSet<Integer>[] lan;
            final int pan;
            boolean addedPsList = false;
            if (gameData.hasSeaBoard)
            {
                final SOCBoardLarge bl = (SOCBoardLarge) gameData.getBoard();
                lan = bl.getLandAreasLegalNodes();
                pan = bl.getStartingLandArea();
                if ((lan != null) && ! lan[pan].equals(psList))
                {
                    // If potentials != legals[startingLandArea], send as legals[0]
                    lan[0] = psList;
                    addedPsList = true;
                }
            } else {
                lan = null;
                pan = 0;
            }

            if (lan == null)
            {
                c.put(SOCPotentialSettlements.toCmd(gameName, -1, new Vector<Integer>(psList)));
            } else {
                c.put(SOCPotentialSettlements.toCmd
                    (gameName, -1, pan, lan, SOCBoardLargeAtServer.getLegalSeaEdges(gameData, -1)));
            }

            if (addedPsList)
                lan[0] = null;  // Undo change to game's copy of landAreasLegalNodes

            if (gameData.isGameOptionSet(SOCGameOption.K_SC_CLVI))
                c.put(SOCPlayerElement.toCmd
                    (gameName, -1, SOCPlayerElement.SET,
                     SOCPlayerElement.SCENARIO_CLOTH_COUNT, ((SOCBoardLarge) (gameData.getBoard())).getCloth()));
        }

        /**
         * _SC_FTRI: If game has started, send any changed Special Edges.
         */
        if (gameData.hasSeaBoard && (gameData.getGameState() >= SOCGame.PLAY))
        {
            final SOCBoardLarge bl = (SOCBoardLarge) gameData.getBoard();
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
        c.put(SOCSetTurn.toCmd(gameName, gameData.getCurrentPlayerNumber()));

        /**
         * Send the game's Special Item info, if any, if game has started:
         */
        final String[] gameSITypes;
        if (gameData.getGameState() >= SOCGame.START1A)
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
                        c.put(new SOCSetSpecialItem(gameName, SOCSetSpecialItem.OP_CLEAR, tkey, gi, -1, -1).toCmd());
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

                    c.put(new SOCSetSpecialItem(gameData, SOCSetSpecialItem.OP_SET, tkey, gi, pi, si).toCmd());

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
            SOCPlayer pl = gameData.getPlayer(i);

            /**
             * send scenario info before any putpiece, so they know their
             * starting land areas and scenario events
             */
            int itm = pl.getSpecialVP();
            if (itm != 0)
            {
                srv.messageToPlayer(c, new SOCPlayerElement
                        (gameName, i, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_SVP, itm));

                ArrayList<SOCPlayer.SpecialVPInfo> svpis = pl.getSpecialVPInfo();
                if (svpis != null)
                    for (SOCPlayer.SpecialVPInfo svpi : svpis)
                        srv.messageToPlayer(c, new SOCSVPTextMessage(gameName, i, svpi.svp, c.getLocalized(svpi.desc)));
            }

            itm = pl.getScenarioPlayerEvents();
            if (itm != 0)
                srv.messageToPlayer(c, new SOCPlayerElement
                        (gameName, i, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_PLAYEREVENTS_BITMASK, itm));

            itm = pl.getScenarioSVPLandAreas();
            if (itm != 0)
                srv.messageToPlayer(c, new SOCPlayerElement
                    (gameName, i, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_SVP_LANDAREAS_BITMASK, itm));

            itm = pl.getStartingLandAreasEncoded();
            if (itm != 0)
                srv.messageToPlayer(c, new SOCPlayerElement
                        (gameName, i, SOCPlayerElement.SET, SOCPlayerElement.STARTING_LANDAREAS, itm));

            itm = pl.getCloth();
            if (itm != 0)
                srv.messageToPlayer(c, new SOCPlayerElement
                    (gameName, i, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_CLOTH_COUNT, itm));

            // Send piece info even if player has left the game (pl.getName() == null).
            // This lets them see "their" pieces before srv.sitDown(), if they rejoin at same position.

            Enumeration<SOCPlayingPiece> piecesEnum = pl.getPieces().elements();
            while (piecesEnum.hasMoreElements())
            {
                SOCPlayingPiece piece = piecesEnum.nextElement();

                if (piece.getType() == SOCPlayingPiece.CITY)
                {
                    c.put(SOCPutPiece.toCmd(gameName, i, SOCPlayingPiece.SETTLEMENT, piece.getCoordinates()));
                }

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
                        c.put(SOCPieceValue.toCmd(gameName, coord, str, 0));
                }
            }

            // _SC_PIRI: for display, send count of warships only after SOCShip pieces are sent
            itm = pl.getNumWarships();
            if (itm != 0)
                srv.messageToPlayer(c, new SOCPlayerElement
                    (gameName, i, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_WARSHIP_COUNT, itm));

            /**
             * send each player's unique potential settlement list,
             * if game has started
             */
            if ((gameData.getGameState() != SOCGame.NEW)
                || (c.getVersion() < SOCPotentialSettlements.VERSION_FOR_PLAYERNUM_ALL))
            {
                final HashSet<Integer> psList = pl.getPotentialSettlements();

                // Some boards may have multiple land areas.
                // Note: Assumes all players have same legal nodes.
                // See also above, and startGame which has very similar code.
                final HashSet<Integer>[] lan;
                final int pan;
                if (gameData.hasSeaBoard && (i == 0))
                {
                    // send this info once, not per-player
                    final SOCBoardLarge bl = (SOCBoardLarge) gameData.getBoard();
                    lan = bl.getLandAreasLegalNodes();
                    pan = bl.getStartingLandArea();
                    if (lan != null)
                        lan[0] = psList;
                } else {
                    lan = null;
                    pan = 0;
                }

                if (lan == null)
                {
                    c.put(SOCPotentialSettlements.toCmd(gameName, i, new Vector<Integer>(psList)));
                } else {
                    c.put(SOCPotentialSettlements.toCmd
                        (gameName, i, pan, lan, SOCBoardLargeAtServer.getLegalSeaEdges(gameData, i)));
                    lan[0] = null;  // Undo change to game's copy of landAreasLegalNodes
                }
            }

            /**
             * send coords of the last settlement
             */
            c.put(SOCLastSettlement.toCmd(gameName, i, pl.getLastSettlementCoord()));

            /**
             * send number of playing pieces in hand
             */
            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.ROADS, pl.getNumPieces(SOCPlayingPiece.ROAD)));
            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.SETTLEMENTS, pl.getNumPieces(SOCPlayingPiece.SETTLEMENT)));
            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.CITIES, pl.getNumPieces(SOCPlayingPiece.CITY)));
            if (gameData.hasSeaBoard)
                c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.SHIPS, pl.getNumPieces(SOCPlayingPiece.SHIP)));

            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.UNKNOWN, pl.getResources().getTotal()));

            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.NUMKNIGHTS, pl.getNumKnights()));

            final int numDevCards = pl.getInventory().getTotal();
            final int unknownType;
            if (c.getVersion() >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES)
                unknownType = SOCDevCardConstants.UNKNOWN;
            else
                unknownType = SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X;
            final String cardUnknownCmd = SOCDevCardAction.toCmd(gameName, i, SOCDevCardAction.ADDOLD, unknownType);
            for (int j = 0; j < numDevCards; j++)
            {
                c.put(cardUnknownCmd);
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
                                    (gameName, SOCSetSpecialItem.OP_CLEAR, tkey, -1, pi, i).toCmd());
                            continue;
                        }

                        if ((iList != null) && (iList.size() > pi) && (iList.get(pi) == si))
                            continue;  // already sent (shared with game)

                        c.put(new SOCSetSpecialItem(gameData, SOCSetSpecialItem.OP_SET, tkey, -1, pi, si).toCmd());
                    }
                }
            }

            if (i == 0)
            {
                // per-game data, send once
                c.put(SOCFirstPlayer.toCmd(gameName, gameData.getFirstPlayer()));

                c.put(SOCDevCardCount.toCmd(gameName, gameData.getNumDevCards()));
            }

            c.put(SOCChangeFace.toCmd(gameName, i, pl.getFaceId()));

            if (i == 0)
            {
                // per-game data, send once

                c.put(SOCDiceResult.toCmd(gameName, gameData.getCurrentDice()));
            }
        }

        ///
        /// send who has longest road
        ///
        SOCPlayer lrPlayer = gameData.getPlayerWithLongestRoad();
        int lrPlayerNum = -1;

        if (lrPlayer != null)
        {
            lrPlayerNum = lrPlayer.getPlayerNumber();
        }

        c.put(SOCLongestRoad.toCmd(gameName, lrPlayerNum));

        ///
        /// send who has largest army
        ///
        final SOCPlayer laPlayer = gameData.getPlayerWithLargestArmy();
        final int laPlayerNum;
        if (laPlayer != null)
        {
            laPlayerNum = laPlayer.getPlayerNumber();
        }
        else
        {
            laPlayerNum = -1;
        }

        c.put(SOCLargestArmy.toCmd(gameName, laPlayerNum));

        /**
         * If we're rejoining and taking over a seat after a network problem,
         * send our resource and hand information.
         */
        if (isTakingOver)
        {
            SOCPlayer cliPl = gameData.getPlayer(c.getData());
            if (cliPl != null)
            {
                int pn = cliPl.getPlayerNumber();
                if ((pn != -1) && ! gameData.isSeatVacant(pn))
                    sitDown_sendPrivateInfo(gameData, c, pn);
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
            Vector<Connection> gameMembers = srv.gameList.getMembers(gameName);
            membersCommand = SOCGameMembers.toCmd(gameName, gameMembers);
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
        c.put(SOCGameState.toCmd(gameName, gameData.getGameState()));
        if (D.ebugOn)
            D.ebugPrintln("*** " + c.getData() + " joined the game " + gameName + " at "
                + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));

        //messageToGame(gameName, new SOCGameServerText(gameName, SERVERNAME, n+" joined the game"));
        /**
         * Let everyone else know about the change
         */
        if (isTakingOver)
        {
            return;
        }
        srv.messageToGame(gameName, new SOCJoinGame(c.getData(), "", "dummyhost", gameName));

        if ((! isReset) && gameData.getGameState() >= SOCGame.START2A)
        {
            srv.messageToPlayerKeyed
                (c, gameName,
                 (hasRobot) ? "member.join.game.started.bots"  // "This game has started. To play, take over a robot."
                            : "member.join.game.started");     // "This game has started; no new players can sit down."
        }
    }

    /**
     * Client is joining this game, which uses {@link SOCBoardLarge} with {@link SOCBoardLarge#hasSpecialEdges()};
     * send any changes to special edges from the starting board layout.
     *<P>
     * Compares the current {@link SOCBoardLarge#getSpecialEdges()} against each
     * {@link SOCBoardLarge#getAddedLayoutPart(String)} which defines special edges
     * (currently {@code "CE"}, {@code "VE"}).
     *<P>
     * Called as part of {@link #joinGame(SOCGame, Connection, boolean, boolean)}.
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
    public void sitDown_sendPrivateInfo(final SOCGame ga, final Connection c, final int pn)
    {
        final String gaName = ga.getName();
        final SOCPlayer pl = ga.getPlayer(pn);

        /**
         * send all the private information
         */
        SOCResourceSet resources = pl.getResources();
        // CLAY, ORE, SHEEP, WHEAT, WOOD, UNKNOWN
        for (int res = SOCPlayerElement.CLAY; res <= SOCPlayerElement.UNKNOWN; ++res)
            srv.messageToPlayer(c, new SOCPlayerElement(gaName, pn, SOCPlayerElement.SET, res, resources.getAmount(res)));

        SOCInventory cardsInv = pl.getInventory();

        final boolean cliVersionNew = (c.getVersion() >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES);

        /**
         * remove the unknown cards
         */
        final SOCDevCardAction cardUnknown = (cliVersionNew)
            ? new SOCDevCardAction(gaName, pn, SOCDevCardAction.PLAY, SOCDevCardConstants.UNKNOWN)
            : new SOCDevCardAction(gaName, pn, SOCDevCardAction.PLAY, SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X);
        for (int i = cardsInv.getTotal(); i > 0; --i)
        {
            srv.messageToPlayer(c, cardUnknown);
        }

        /**
         * send all new dev cards first, then all playable, then all kept (VP cards)
         */
        for (int dcState = SOCInventory.NEW; dcState <= SOCInventory.KEPT; ++dcState)
        {
            final int dcAge = (dcState == SOCInventory.NEW) ? SOCInventory.NEW : SOCInventory.OLD;
            final int addCmd = (dcAge == SOCInventory.NEW) ? SOCDevCardAction.ADDNEW : SOCDevCardAction.ADDOLD;

            for (final SOCInventoryItem card : cardsInv.getByState(dcState))
            {
                final SOCMessage addMsg;
                if (card instanceof SOCDevCard)
                {
                    final int dcType = card.itype;
                    if (cliVersionNew || (dcType != SOCDevCardConstants.KNIGHT))
                        addMsg = new SOCDevCardAction(gaName, pn, addCmd, dcType);
                    else
                        addMsg = new SOCDevCardAction(gaName, pn, addCmd, SOCDevCardConstants.KNIGHT_FOR_VERS_1_X);
                } else {
                    // None yet
                    System.err.println("L1385: Unrecognized inventory item type " + card.getClass());
                    addMsg = null;
                }

                if (addMsg != null)
                    srv.messageToPlayer(c, addMsg);

            }  // for (card)
        }  // for (dcState)

        /**
         * send game state info such as requests for discards
         */
        sendGameState(ga);

        if ((ga.getCurrentDice() == 7) && pl.getNeedToDiscard())
        {
            srv.messageToPlayer(c, new SOCDiscardRequest(gaName, pl.getResources().getTotal() / 2));
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
    public boolean leaveGame(SOCGame ga, Connection c)
    {
        final String gm = ga.getName();
        final String plName = c.getData();  // Retain name, since will become null within game obj.

        boolean gameHasHumanPlayer = false;
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
                ga.removePlayer(plName);  // player obj name becomes null

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
        for (int pn = 0; pn < ga.maxPlayers; pn++)
        {
            SOCPlayer player = ga.getPlayer(pn);

            if ((player != null) && (player.getName() != null) && (!ga.isSeatVacant(pn)) && (!player.isRobot()))
            {
                gameHasHumanPlayer = true;
                break;
            }
        }

        //D.ebugPrintln("*** gameHasHumanPlayer = "+gameHasHumanPlayer+" for "+gm);

        /**
         * if no human players, check if there is at least one person watching the game
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

                if (!nameMatch)
                {
                    gameHasObserver = true;
                    break;
                }
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
                && (gameState < SOCGame.OVER)
                && !(gameState < SOCGame.START1A))
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
                    break;
            }
        }

        if (! nameMatch)
        {
            /**
             * make the request
             */
            D.ebugPrintln("@@@ JOIN GAME REQUEST for " + robotConn.getData());

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
     * Send the current state of the game with a message.
     * Assumes current player does not change during this state.
     * If we send a text message to prompt the new player to roll,
     * also sends a RollDicePrompt data message.
     *<P>
     * For more details and references, see {@link #sendGameState(SOCGame, boolean)}.
     * Be sure that callers to {@code sendGameState} don't assume the game will still
     * exist after calling this method, if the game state was {@link SOCGame#OVER OVER}.
     *<P>
     * Equivalent to: {@link #sendGameState(SOCGame, boolean) sendGameState(ga, true)}.
     *
     * @param ga  the game
     */
    void sendGameState(SOCGame ga)
    {
        sendGameState(ga, true);
    }

    /**
     * send the current state of the game with a message.
     * Note that the current (or new) player number is not sent here.
     * If game is now OVER, send appropriate messages.
     *<P>
     * State {@link SOCGame#WAITING_FOR_DISCARDS}:
     * If a 7 is rolled, will also say who must discard (in a GAMETEXTMSG).
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
     * <b>Note:</b> If game is now {@link SOCGame#OVER OVER} and the {@link SOCGame#isBotsOnly} flag is set,
     * {@link #sendGameStateOVER(SOCGame)} will call {@link SOCServer#destroyGameAndBroadcast(String, String)}.
     * Be sure that callers to {@code sendGameState} don't assume the game will still exist after calling this method.
     * Also, {@code destroyGame} might create more {@link SOCGame#isBotsOnly} games, depending on server settings.
     *<P>
     * <b>Locks:</b> Does not hold {@link SOCGameList#takeMonitor()} or
     * {@link SOCGameList#takeMonitorForGame}<tt>(gaName)</tt> when called.
     * Some callers call {@link SOCGame#takeMonitor()} before calling; not important here.
     *
     * @see #sendTurn(SOCGame, boolean)
     * @see #sendGameState(SOCGame)
     * @see #sendGameStateOVER(SOCGame)
     *
     * @param ga  the game
     * @param sendRollPrompt  If true, and if we send a text message to prompt
     *    the player to roll, send a RollDicePrompt data message.
     *    If the client is too old (1.0.6), it will ignore the prompt.
     *
     * @return    did we send a text message to prompt the player to roll?
     *    If so, sendTurn can also send a RollDicePrompt data message.
     * @since 1.1.00
     */
    boolean sendGameState(SOCGame ga, boolean sendRollPrompt)
    {
        if (ga == null)
            return false;

        final String gname = ga.getName();
        boolean promptedRoll = false;
        if (ga.getGameState() == SOCGame.OVER)
        {
            /**
             * Before sending state "OVER", enforce current player number.
             * This helps the client's copy of game recognize winning condition.
             */
            srv.messageToGame(gname, new SOCSetTurn(gname, ga.getCurrentPlayerNumber()));
        }
        srv.messageToGame(gname, new SOCGameState(gname, ga.getGameState()));

        SOCPlayer player = null;

        if (ga.getCurrentPlayerNumber() != -1)
        {
            player = ga.getPlayer(ga.getCurrentPlayerNumber());
        }

        switch (ga.getGameState())
        {
        case SOCGame.START1A:
        case SOCGame.START2A:
        case SOCGame.START3A:
            srv.messageToGameKeyed(ga, true, "prompt.turn.to.build.stlmt",  player.getName());  // "It's Joe's turn to build a settlement."
            if ((ga.getGameState() >= SOCGame.START2A)
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

        case SOCGame.PLAY:
            srv.messageToGameKeyed(ga, true, "prompt.turn.to.roll.dice", player.getName());  // "It's Joe's turn to roll the dice."
            promptedRoll = true;
            if (sendRollPrompt)
                srv.messageToGame(gname, new SOCRollDicePrompt (gname, player.getPlayerNumber()));

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
            // handled in SOCGameMessageHandler.handlePUTPIECE and handleROLLDICE, so it's sent after
            // the resource texts ("x gets 1 sheep") and not before.
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
             * get the choices from the game
             */
            final boolean canStealNone = ga.isGameOptionSet(SOCGameOption.K_SC_PIRI);
            boolean[] choices = new boolean[ga.maxPlayers + (canStealNone ? 1 : 0)];
            Arrays.fill(choices, false);
            if (canStealNone)
                choices[ga.maxPlayers] = true;

            Enumeration<SOCPlayer> plEnum = ga.getPossibleVictims().elements();

            while (plEnum.hasMoreElements())
            {
                SOCPlayer pl = plEnum.nextElement();
                choices[pl.getPlayerNumber()] = true;
            }

            /**
             * ask the current player to choose a player to steal from
             */
            Connection con = srv.getConnection(ga.getPlayer(ga.getCurrentPlayerNumber()).getName());
            if (con != null)
            {
                con.put(SOCChoosePlayerRequest.toCmd(gname, choices));
            }

            break;

        case SOCGame.OVER:
            sendGameStateOVER(ga);
            break;

        }  // switch ga.getGameState

        return promptedRoll;
    }

    /**
     * Send a game text message "x, y, and z need to pick resources from the gold hex."
     * and, for each picking player, a {@link SOCPlayerElement}({@link SOCPlayerElement#NUM_PICK_GOLD_HEX_RESOURCES NUM_PICK_GOLD_HEX_RESOURCES}).
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
     *                   {@code PROMPT_PICK_RESOURCES} if they are the only one to pick gold.
     *                   If more than 1 player has {@link SOCPlayer#getNeedToPickGoldHexResources()},
     *                   no message will be sent to <tt>playerCon</tt>.
     * @param roll  For gold gained from dice rolls, the roll details, otherwise null.
     *                   In scenario SC_PIRI, is used to avoid announcing twice for a pick from victory against pirate fleet.
     * @since 2.0.00
     */
    final void sendGameState_sendGoldPickAnnounceText
        (SOCGame ga, final String gname, Connection playerCon, SOCGame.RollResult roll)
    {
        final int ignoreRollPirateVictory;
        if ((roll != null) && ga.isGameOptionSet(SOCGameOption.K_SC_PIRI) && (roll.sc_piri_fleetAttackRsrcs != null))
            ignoreRollPirateVictory = roll.sc_piri_fleetAttackRsrcs.getAmount(SOCResourceConstants.GOLD_LOCAL);
        else
            ignoreRollPirateVictory = 0;

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
                if ((ignoreRollPirateVictory > 0) && (pp == roll.sc_piri_fleetAttackVictim))
                    numGoldRes -= ignoreRollPirateVictory;
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
                    (gname, pn, SOCPlayerElement.SET, SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES, num[pn]));

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
     *  If game is OVER, send messages reporting winner, final score,
     *  and each player's victory-point cards.
     *  Also give stats on game length, and on each player's connect time.
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
     * @since 1.1.00
     */
    private void sendGameStateOVER(SOCGame ga)
    {
        final String gname = ga.getName();

        /**
         * Find and announce the winner
         * (the real "found winner" code is in SOCGame.checkForWinner;
         *  that was already called before sendGameStateOVER.)
         */
        SOCPlayer winPl = ga.getPlayer(ga.getCurrentPlayerNumber());

        if ((winPl.getTotalVP() < ga.vp_winner) && ! ga.hasScenarioWinCondition)
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

        srv.messageToGameKeyed(ga, true, "stats.game.winner.withpoints", winPl.getName(), winPl.getTotalVP());
            // "{0} has won the game with {1,number} points."

        /// send a message with the revealed final scores
        {
            int[] scores = new int[ga.maxPlayers];
            boolean[] isRobot = new boolean[ga.maxPlayers];
            for (int i = 0; i < ga.maxPlayers; ++i)
            {
                scores[i] = ga.getPlayer(i).getTotalVP();
                isRobot[i] = ga.getPlayer(i).isRobot();
            }
            srv.messageToGame(gname, new SOCGameStats(gname, scores, isRobot));
        }

        ///
        /// send a message saying what VP cards each player has
        ///
        for (int i = 0; i < ga.maxPlayers; i++)
        {
            SOCPlayer pl = ga.getPlayer(i);
            List<SOCInventoryItem> vpCards = pl.getInventory().getByState(SOCInventory.KEPT);

            if (! vpCards.isEmpty())
                srv.messageToGameKeyedSpecial
                    (ga, true, "endgame.player.has.vpcards", pl.getName(), vpCards);
                    // "Joe has a Gov.House (+1VP) and a Market (+1VP)" ["{0} has {1,dcards}."]

        }  // for each player

        /**
         * send game-length and connect-length messages, possibly win-loss count.
         */
        {
            Date now = new Date();
            Date gstart = ga.getStartTime();
            if (gstart != null)
            {
                final int gameRounds = ga.getRoundCount();
                long gameSeconds = ((now.getTime() - gstart.getTime())+500L) / 1000L;
                final long gameMinutes = gameSeconds / 60L;
                gameSeconds = gameSeconds % 60L;

                if (gameSeconds == 0)
                    srv.messageToGameKeyed(ga, true, "stats.game.was.roundsminutes", gameRounds, gameMinutes);
                        // "This game was # rounds, and took # minutes."
                else
                    srv.messageToGameKeyed(ga, true, "stats.game.was.roundsminutessec", gameRounds, gameMinutes, gameSeconds);
                        // "This game was # rounds, and took # minutes # seconds." [or 1 second.]

                // Ignore possible "1 minutes"; that game is too short to worry about.
            }

            /**
             * Update each player's win-loss count for this session.
             * Tell each player their resource roll totals.
             * Tell each player how long they've been connected.
             * (Robot players aren't told this, it's not necessary.)
             */
            final String connMsgKey;
            if (ga.isPractice)
                connMsgKey = "stats.cli.connected.minutes.prac";  // "You have been practicing # minutes."
            else
                connMsgKey = "stats.cli.connected.minutes";  // "You have been connected # minutes."

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

                    final long connTime = plConn.getConnectTime().getTime();
                    final long connMinutes = (((now.getTime() - connTime)) + 30000L) / 60000L;
                    srv.messageToPlayerKeyed(plConn, gname, connMsgKey, connMinutes);  // "You have been connected # minutes."

                    // Send client's win-loss count for this session,
                    // if more than 1 game has been played
                    {
                        int wins = cd.getWins();
                        int losses = cd.getLosses();
                        if (wins + losses < 2)
                            continue;  // Only 1 game played so far

                        if (wins > 0)
                        {
                            if (losses == 0)
                                srv.messageToPlayerKeyed(plConn, gname, "stats.cli.winloss.won", wins);
                                    // "You have won {0,choice, 1#1 game|1<{0,number} games} since connecting."
                            else
                                srv.messageToPlayerKeyed(plConn, gname, "stats.cli.winloss.wonlost", wins, losses);
                                    // "You have won {0,choice, 1#1 game|1<{0,number} games} and lost {1,choice, 1#1 game|1<{1,number} games} since connecting."
                        } else {
                            srv.messageToPlayerKeyed(plConn, gname, "stats.cli.winloss.lost", losses);
                                // "You have lost {0,choice, 1#1 game|1<{0,number} games} since connecting."
                        }
                    }
                }
            }  // for each player

        }  // send game timing stats, win-loss stats

        srv.gameOverIncrGamesFinishedCount();
        srv.storeGameScores(ga);

        if (ga.isBotsOnly)
        {
            srv.destroyGameAndBroadcast(gname, "sendGameStateOVER");
        }

        // Server structure more or less ensures sendGameStateOVER is called only once.
        // TODO consider refactor to be completely sure, especially for storeGameScores.
    }

    /**
     * The current player is stealing from another player.
     * Send messages saying what was stolen.
     *
     * @param ga  the game
     * @param pe  the perpetrator
     * @param vi  the victim
     * @param rsrc  type of resource stolen, as in {@link SOCResourceConstants#SHEEP},
     *              or {@link SOCResourceConstants#CLOTH_STOLEN_LOCAL} for cloth
     *              (scenario option {@link SOCGameOption#K_SC_CLVI _SC_CLVI}).
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
                    SOCPlayerElement.SCENARIO_CLOTH_COUNT, vi.getCloth(), true));
            srv.messageToGame(gaName,
                new SOCPlayerElement(gaName, pePN, SOCPlayerElement.SET,
                    SOCPlayerElement.SCENARIO_CLOTH_COUNT, pe.getCloth()));
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
        Vector<Connection> exceptions = new Vector<Connection>(2);
        exceptions.addElement(peCon);
        exceptions.addElement(viCon);
        gainUnknown = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, SOCPlayerElement.UNKNOWN, 1);
        loseUnknown = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, 1);
        srv.messageToGameExcept(gaName, exceptions, gainUnknown, true);
        srv.messageToGameExcept(gaName, exceptions, loseUnknown, true);

        /**
         * send the text messages:
         * "You stole a sheep from viName."  [In v1.x.xx, "stole a sheep resource"]
         * "peName stole a sheep from you."
         * "peName stole a resource from viName."
         */
        srv.messageToPlayerKeyedSpecial(peCon, ga, "robber.you.stole.resource.from", -1, rsrc, viName);  // "You stole {0,rsrcs} from {2}."
        srv.messageToPlayerKeyedSpecial(viCon, ga, "robber.stole.resource.from.you", peName, -1, rsrc);  // "{0} stole {1,rsrcs} from you."
        srv.messageToGameKeyedSpecialExcept(ga, true, exceptions, "robber.stole.resource.from", peName, viName);  // "{0} stole a resource from {1}."
    }

    /**
     * report a trade that has taken place between players, using {@link SOCPlayerElement}
     * and {@link SOCGameServerText} messages.
     *<P>
     * Callers must also report trades to robots by re-sending the accepting player's
     * {@link SOCAcceptOffer} message to the game after calling this method.
     *
     * @param ga        the game
     * @param offering  the number of the player making the offer
     * @param accepting the number of the player accepting the offer
     *
     * @see #reportBankTrade(SOCGame, SOCResourceSet, SOCResourceSet)
     */
    void reportTrade(SOCGame ga, int offering, int accepting)
    {
        final String gaName = ga.getName();
        final SOCTradeOffer offer = ga.getPlayer(offering).getCurrentOffer();
        final SOCResourceSet giveSet = offer.getGiveSet(),
                             getSet  = offer.getGetSet();

        reportRsrcGainLoss(gaName, giveSet, true, false, offering, accepting, null, null);
        reportRsrcGainLoss(gaName, getSet, false, false, offering, accepting, null, null);
        srv.messageToGameKeyedSpecial(ga, true, "trade.gave.rsrcs.for.from.player",
            ga.getPlayer(offering).getName(), giveSet, getSet, ga.getPlayer(accepting).getName());
            // "{0} gave {1,rsrcs} for {2,rsrcs} from {3}."
    }

    /**
     * report that the current player traded with the bank or a port,
     * using {@link SOCPlayerElement} and {@link SOCGameServerText} messages.
     *
     * @param ga        the game
     * @param give      the number of the player making the offer
     * @param get       the number of the player accepting the offer
     *
     * @see #reportTrade(SOCGame, int, int)
     */
    void reportBankTrade(SOCGame ga, SOCResourceSet give, SOCResourceSet get)
    {
        final String gaName = ga.getName();
        final int    cpn    = ga.getCurrentPlayerNumber();

        reportRsrcGainLoss(gaName, give, true, false, cpn, -1, null, null);
        reportRsrcGainLoss(gaName, get, false, false, cpn, -1, null, null);

        // use total rsrc counts to determine bank or port
        final int giveTotal = give.getTotal(),
                  getTotal  = get.getTotal();
        final int tradeFrom;  // 1 = "the bank" -- 4:1 trade; 2 = "a port" -- 3:1 or 2:1 trade
        final String msgKey;
        if (giveTotal > getTotal)
        {
            msgKey = "trade.traded.rsrcs.for.from.bankport";  // "{0} traded {1,rsrcs} for {2,rsrcs} from {3,choice, 1#the bank|2#a port}."
            tradeFrom = ((giveTotal / getTotal) == 4) ? 1 : 2;
        } else {
            msgKey = "trade.traded.rsrcs.for.from.bankport.undoprevious";  // same + " (Undo previous trade)"
            tradeFrom = ((getTotal / giveTotal) == 4) ? 1 : 2;
        }

        srv.messageToGameKeyedSpecial(ga, true, msgKey, ga.getPlayer(cpn).getName(), give, get, tradeFrom);
    }

    /**
     * Report the resources gained/lost by a player, and optionally (for trading)
     * lost/gained by a second player.
     * Sends PLAYERELEMENT messages, either to entire game, or to player only.
     * Builds the resource-amount string used to report the trade as text.
     * Takes and releases the gameList monitor for this game.
     *<P>
     * Used to report the resources gained from a roll, discard, or discovery (year-of-plenty) pick.
     * Also used to report the "give" or "get" half of a resource trade.
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
     * @param message Append resource numbers/types to this stringbuffer,
     *                format like "3 clay,3 wood"; can be null.
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
     */
    void reportRsrcGainLoss
        (final String gaName, final ResourceSet resourceSet, final boolean isLoss, boolean isNews,
         final int mainPlayer, final int tradingPlayer, StringBuffer message, Connection playerConn)
    {
        final int losegain  = isLoss ? SOCPlayerElement.LOSE : SOCPlayerElement.GAIN;  // for pnA
        final int gainlose  = isLoss ? SOCPlayerElement.GAIN : SOCPlayerElement.LOSE;  // for pnB

        boolean needComma = false;  // Has a resource already been appended to message?

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

            if (message != null)
            {
                if (needComma)
                    message.append(", ");
                message.append
                    (MessageFormat.format( /*I*/"{0,number} {1}"/*18N*/, amt, SOCResourceConstants.resName(res))); // "3 clay"
                needComma = true;
            }
        }

        srv.gameList.releaseMonitorForGame(gaName);
    }

    /**
     * Report to game members what a player picked from the gold hex.
     * Sends {@link SOCPlayerElement} for resources and to reset the
     * {@link SOCPlayerElement#NUM_PICK_GOLD_HEX_RESOURCES} counter.
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
        reportRsrcGainLoss(gn, rsrcs, false, isNews, pn, -1, null, null);
        srv.messageToGameKeyedSpecial(ga, true,
            ((includeGoldHexText) ? "action.picked.rsrcs.goldhex" : "action.picked.rsrcs"),
            player.getName(), rsrcs);
        srv.messageToGame(gn, new SOCPlayerElement
            (gn, pn, SOCPlayerElement.SET, SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES, 0));
    }

    // javadoc inherited from GameHandler
    /**
     * {@inheritDoc}
     *<P>
     * If {@link SOCGame#hasSeaBoard}: Once the board is made, send the updated
     * {@link SOCPotentialSettlements potential settlements}.
     *<P>
     * If this code changes, must also update {@link soctest.TestBoardLayouts#testSingleLayout(SOCScenario, int)}.
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

        ga.setScenarioEventListener(this);  // for playerEvent, gameEvent callbacks (since 2.0.00)
        ga.startGame();

        final int[][] legalSeaEdges;  // used on sea board; if null, all are legal
        if (ga.hasSeaBoard)
        {
            legalSeaEdges = SOCBoardLargeAtServer.getLegalSeaEdges(ga, -1);
            if (legalSeaEdges != null)
                for (int pn = 0; pn < ga.maxPlayers; ++pn)
                    ga.getPlayer(pn).setRestrictedLegalShips(legalSeaEdges[pn]);

            if (ga.isGameOptionSet(SOCGameOption.K_SC_FTRI) || ga.isGameOptionSet(SOCGameOption.K_SC_PIRI))
            {
                // scenario has initial pieces
                ((SOCBoardLargeAtServer) (ga.getBoard())).startGame_putInitPieces(ga);
            }
        } else {
            legalSeaEdges = null;
        }

        srv.gameList.takeMonitorForGame(gaName);

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
            srv.gameList.releaseMonitorForGame(gaName);
            System.err.println("startGame: Cannot send board for " + gaName + ": " + e.getMessage());
            return;
        }
        if (ga.hasSeaBoard)
        {
            // See also joinGame which has very similar code.

            // Send the updated Potential/Legal Settlement node list
            // Note: Assumes all players have same potential settlements
            //    (sends with playerNumber -1 == all)
            final HashSet<Integer> psList = ga.getPlayer(0).getPotentialSettlements();

            // Some boards may have multiple land areas.
            final HashSet<Integer>[] lan;
            final int pan;
            boolean addedPsList = false;

            final SOCBoardLarge bl = (SOCBoardLarge) ga.getBoard();
            lan = bl.getLandAreasLegalNodes();
            pan = bl.getStartingLandArea();

            if ((lan != null) && (pan != 0) && ! lan[pan].equals(psList))
            {
                // If potentials != legals[startingLandArea], send as legals[0]
                lan[0] = psList;
                addedPsList = true;
            }

            if (lan == null)
                srv.messageToGameWithMon(gaName, new SOCPotentialSettlements(gaName, -1, new ArrayList<Integer>(psList)));
            else
                srv.messageToGameWithMon(gaName, new SOCPotentialSettlements(gaName, -1, pan, lan, legalSeaEdges));

            if (addedPsList)
                lan[0] = null;  // Undo change to game's copy of landAreasLegalNodes
        }

        /**
         * send the player info
         */
        boolean sentInitPiecesState = false;
        for (int i = 0; i < ga.maxPlayers; i++)
        {
            if (! ga.isSeatVacant(i))
            {
                SOCPlayer pl = ga.getPlayer(i);
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

                    srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.SHIPS, pl.getNumPieces(SOCPlayingPiece.SHIP)));
                }
                srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.ROADS, pl.getNumPieces(SOCPlayingPiece.ROAD)));
                srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.SETTLEMENTS, pl.getNumPieces(SOCPlayingPiece.SETTLEMENT)));
                srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.CITIES, pl.getNumPieces(SOCPlayingPiece.CITY)));
                srv.messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, i, false));
            }
        }

        /**
         * send the number of dev cards
         */
        srv.messageToGameWithMon(gaName, new SOCDevCardCount(gaName, ga.getNumDevCards()));

        /**
         * ga.startGame() picks who goes first, but feedback is nice
         */
        srv.messageToGameKeyed(ga, false, "start.picking.random.starting.player");  // "Randomly picking a starting player..."

        srv.gameList.releaseMonitorForGame(gaName);

        /**
         * send the game state
         */
        sendGameState(ga, false);

        /**
         * start the game
         */
        srv.messageToGame(gaName, new SOCStartGame(gaName));

        /**
         * send whose turn it is
         */
        sendTurn(ga, false);
    }

    /**
     * send {@link SOCTurn whose turn it is}. Optionally also send a prompt to roll.
     * If the client is too old (1.0.6), it will ignore the prompt.
     *<P>
     * sendTurn should be called whenever the current player changes, including
     * during and after initial placement.
     *
     * @param ga  the game
     * @param sendRollPrompt  whether to send a RollDicePrompt message afterwards
     */
    void sendTurn(final SOCGame ga, final boolean sendRollPrompt)
    {
        if (ga == null)
            return;

        String gname = ga.getName();
        int pn = ga.getCurrentPlayerNumber();

        srv.messageToGame(gname, new SOCSetPlayedDevCard(gname, pn, false));

        SOCTurn turnMessage = new SOCTurn(gname, pn);
        srv.messageToGame(gname, turnMessage);
        srv.recordGameEvent(gname, turnMessage);

        if (sendRollPrompt)
            srv.messageToGame(gname, new SOCRollDicePrompt(gname, pn));
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
    private SOCMessage getBoardLayoutMessage(SOCGame ga)
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
                    resources[resourceType] = Integer.parseInt(token);
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
                // get all the of the line, in case there's a space in the player name ("robot 7"),
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
            srv.messageToPlayer(c, game.getName(), "### Usage: " + DEBUG_COMMANDS_HELP_RSRCS);
            srv.messageToPlayer(c, game.getName(), DEBUG_COMMANDS_HELP_PLAYER);

            return;  // <--- early return ---
        }

        SOCResourceSet rset = pl.getResources();
        int pnum = pl.getPlayerNumber();
        String outMes = "### " + pl.getName() + " gets";

        for (resourceType = SOCResourceConstants.CLAY;
                resourceType <= SOCResourceConstants.WOOD; resourceType++)
        {
            rset.add(resources[resourceType], resourceType);
            outMes += (" " + resources[resourceType]);

            // SOCResourceConstants.CLAY == SOCPlayerElement.CLAY
            srv.messageToGame(game.getName(), new SOCPlayerElement(game.getName(), pnum, SOCPlayerElement.GAIN, resourceType, resources[resourceType]));
        }

        srv.messageToGame(game.getName(), outMes);
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
        if ((cardType != SOCDevCardConstants.KNIGHT) || (game.clientVersionLowest >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
        {
            srv.messageToGame(game.getName(), new SOCDevCardAction(game.getName(), pnum, SOCDevCardAction.DRAW, cardType));
        } else {
            srv.messageToGameForVersions
                (game, -1, SOCDevCardConstants.VERSION_FOR_NEW_TYPES - 1,
                 new SOCDevCardAction(game.getName(), pnum, SOCDevCardAction.DRAW, SOCDevCardConstants.KNIGHT_FOR_VERS_1_X), true);
            srv.messageToGameForVersions
                (game, SOCDevCardConstants.VERSION_FOR_NEW_TYPES, Integer.MAX_VALUE,
                 new SOCDevCardAction(game.getName(), pnum, SOCDevCardAction.DRAW, SOCDevCardConstants.KNIGHT), true);
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
            for (int i = 0; i < ga.maxPlayers; ++i)
            {
                final SOCPlayer pli = ga.getPlayer(i);
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

        if (pl.getCurrentOffer() != null)
        {
            // Robot is waiting for response to a trade offer;
            // check against that longer timeout.
            final long tradeInactiveTime
                = currentTimeMillis - (1000L * ROBOT_FORCE_ENDTURN_TRADEOFFER_SECONDS);
            if (ga.lastActionTime > tradeInactiveTime)
                return;  // <-- Waiting on humans --
        }

        new SOCForceEndTurnThread(srv, this, ga, pl).start();
    }

    /**
     * A bot is unresponsive, or a human player has left the game.
     * End this player's turn cleanly, or force-end if needed.
     *<P>
     * Can be called for a player still in the game, or for a player
     * who has left ({@link SOCGame#removePlayer(String)} has been called).
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

            if (ga.canEndTurn(plNumber))
            {
                srv.gameList.releaseMonitorForGame(gaName);
                ga.takeMonitor();
                endGameTurn(ga, null, true);
                ga.releaseMonitor();
                srv.gameList.takeMonitorForGame(gaName);
            } else {
                /**
                 * Cannot easily end turn.
                 * Must back out something in progress.
                 * May or may not end turn; see javadocs
                 * of forceEndGameTurn and game.forceEndTurn.
                 * All start phases are covered here (START1A..START2B)
                 * because canEndTurn returns false in those gameStates.
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
                sendGameState(ga, false);  // WAITING_FOR_DISCARDS or MOVING_ROBBER for discard;
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
     */
    private final void forceGamePlayerDiscardOrGain
        (final SOCGame cg, final int cpn, final Connection c, final String plName, final int pn)
        throws IllegalStateException
    {
        final boolean isDiscard = (cg.getGameState() == SOCGame.WAITING_FOR_DISCARDS);

        final SOCResourceSet rset = cg.playerDiscardOrGainRandom(pn, isDiscard);

        // Report resources lost or gained; see also forceEndGameTurn for same reporting code.

        final String gaName = cg.getName();
        final int totalRes = rset.getTotal();
        if (isDiscard)
        {
            if ((c != null) && c.isConnected())
                reportRsrcGainLoss(gaName, rset, true, true, pn, -1, null, c);

            srv.messageToGameExcept
                (gaName, c, new SOCPlayerElement
                    (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, totalRes, true),
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
     * @see #playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)
     * @since 2.0.00
     */
    public void gameEvent(final SOCGame ga, final SOCScenarioGameEvent evt, final Object detail)
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
     * Listener callback for per-player scenario events on the large sea board.
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
     * @see #gameEvent(SOCGame, SOCScenarioGameEvent, Object)
     * @param flagsChanged  True if this event changed {@link SOCPlayer#getScenarioPlayerEvents()},
     *             {@link SOCPlayer#getSpecialVP()}, or another flag documented for <tt>evt</tt> in
     *             {@link SOCScenarioPlayerEvent}
     * @param obj  Object related to the event, or null; documented for <tt>evt</tt> in {@link SOCScenarioPlayerEvent}.
     *             Example: The {@link SOCVillage} for {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     * @since 2.0.00
     */
    public void playerEvent(final SOCGame ga, final SOCPlayer pl, final SOCScenarioPlayerEvent evt,
        final boolean flagsChanged, final Object obj)
    {
        // Note: Some SOCGameHandler code assumes that player events are fired only during
        // SOCGameMessageHandler.handlePUTPIECE and handleMOVEPIECEREQUEST.
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
                         SOCPlayerElement.SCENARIO_SVP_LANDAREAS_BITMASK, las));
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
                srv.messageToGame(gaName, new SOCPieceValue(gaName, vi.getCoordinates(), vi.getCloth(), 0));
                srv.messageToGame(gaName, new SOCPlayerElement
                    (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_CLOTH_COUNT, pl.getCloth()));
            }
            break;

        case DEV_CARD_REACHED_SPECIAL_EDGE:
            {
                sendPlayerEventsBitmask = false;
                sendSVP = false;
                IntPair edge_cardType = (IntPair) obj;
                Connection c = srv.getConnection(plName);
                ga.pendingMessagesOut.add(new UnlocalizedString
                    ("action.built.sc_ftri.dev", plName));  // "{0} gets a Development Card as a gift from the Lost Tribe."
                srv.messageToPlayer(c, new SOCDevCardAction(gaName, pn, SOCDevCardAction.DRAW, edge_cardType.getB()));
                srv.messageToGameExcept(gaName, c, new SOCDevCardAction(gaName, pn, SOCDevCardAction.DRAW, SOCDevCardConstants.UNKNOWN), true);
                srv.messageToGame(gaName, new SOCSimpleAction
                    (gaName, -1, SOCSimpleAction.BOARD_EDGE_SET_SPECIAL, edge_cardType.getA(), 0));
            }
            break;

        case SVP_REACHED_SPECIAL_EDGE:
            {
                updatePlayerSVPPendingMessage(ga, pl, 1, "event.svp.sc_ftri.gift");  // "a gift from the Lost Tribe"
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
                 SOCPlayerElement.SCENARIO_SVP, pl.getSpecialVP()));

        if (sendPlayerEventsBitmask)
            ga.pendingMessagesOut.add(new SOCPlayerElement
                (gaName, pn, SOCPlayerElement.SET,
                 SOCPlayerElement.SCENARIO_PLAYEREVENTS_BITMASK, pl.getScenarioPlayerEvents()));
    }

    /**
     * For Special VP player events, check if a new settlement was apparently reached by land or sea.
     * Most new LandAreas are on other islands, but a few (SC_TTD) are on the main island.
     * @param ga  Game with this new settlement
     * @param se  Newly placed settlement to check, passed to
     *     {@link #playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)}
     * @return  Does the new settlement have more adjacent ships than roads?
     * @since 2.0.00
     */
    private final boolean playerEvent_newSettlementIsByShip(final SOCGame ga, final SOCSettlement se)
    {
        if (se == null)
            return true;  // shouldn't happen, but fail gracefully; most new areas are on new islands

        final SOCBoard board = ga.getBoard();
        Vector<Integer> seEdges = board.getAdjacentEdgesToNode(se.getCoordinates());

        int shipCount = 0, roadCount = 0;
        for (int edge : seEdges)
        {
            SOCRoad pp = board.roadAtEdge(edge);
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
     * Should be called before {@link SOCPlayerElement}({@link SOCPlayerElement#SCENARIO_SVP SCENARIO_SVP}),
     * not after.
     *<P>
     * Adds the message to {@link SOCGame#pendingMessagesOut}; note that
     * right now, that field is checked only in
     * {@link SOCGameMessageHandler#handlePUTPIECE(SOCGame, Connection, SOCPutPiece)}
     * and {@link SOCGameMessageHandler#handleMOVEPIECEREQUEST(SOCGame, Connection, SOCMovePieceRequest)},
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
     * Sends the contents of this game's {@link SOCGame#pendingMessagesOut}, then empties that list.
     * To avoid unnecessary work here, check if the list is empty before calling this method.
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
                        c.put(((SOCMessage) pq.get(i)).toCmd());

                pq.clear();
            }
        }

        if (takeMon)
            srv.gameList.releaseMonitorForGame(gaName);
    }

}
