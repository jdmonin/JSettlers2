/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2016 Alessandro D'Ottavio
 * Some contents were formerly part of SOCServer.java and SOCGameHandler.java;
 * Portions of this file Copyright (C) 2003 Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2019 Jeremy D Monin <jeremy@nand.net>
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
package soc.server;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import soc.debug.D;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCDevCard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCInventoryItem;
import soc.game.SOCMoveRobberResult;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCSpecialItem;
import soc.game.SOCTradeOffer;
import soc.game.SOCVillage;
import soc.message.*;
import soc.server.genericServer.Connection;
import soc.util.SOCStringManager;

/**
 * Game message handler for {@link SOCGameHandler}: Dispatches all messages received from the
 * {@link soc.server.genericServer.InboundMessageQueue} related to specific games
 * (implementing {@link SOCMessageForGame}). All other messages are handled by
 * {@link SOCServerMessageHandler} instead.
 *
 *<H4>Message Flow:</H4>
 *<UL>
 * <LI> Inbound game messages arrive here from {@link SOCMessageDispatcher#dispatch(SOCMessage, Connection)}.
 * <LI> Each specific message class is identified in
 *      {@link #dispatch(SOCGame, SOCMessageForGame, Connection) dispatch(..)}
 *      which calls handler methods such as {@link #handleBANKTRADE(SOCGame, Connection, SOCBankTrade)}.
 *      See {@code dispatch(..)} method's javadoc for more details on per-message handling.
 * <LI> Most handler methods call into {@link SOCGameHandler} for the game's "business logic"
 *      abstracted from inbound message processing, and then {@link SOCServer} to send
 *      result messages to all players and observers in the game.
 *</UL>
 *
 * Before v2.0.00 this class was {@link SOCServer}{@code .processCommand(String, Connection)} and related
 * handler methods, all part of {@link SOCServer}. So, some may have {@code @since} javadoc labels with
 * versions older than 2.0.00. Refactoring for 2.0.00 in 2013 moved the handler methods from
 * {@link SOCServer} to {@link SOCGameHandler}, and in 2016 to this class.
 *
 * @see SOCServerMessageHandler
 * @author Alessandro D'Ottavio
 * @since 2.0.00
 */
public class SOCGameMessageHandler
    implements GameMessageHandler
{
    /** Server reference, for data and responses */
    private final SOCServer srv;

    /** Our SOCGameHandler */
    private final SOCGameHandler handler;

    public SOCGameMessageHandler(SOCServer srv, SOCGameHandler sgh)
    {
        this.srv = srv;
        handler = sgh;
    }

    /**
     * Dispatch any request or event coming from a client player for a specific game.
     * This method is called from {@link SOCMessageDispatcher#dispatch(SOCMessage, Connection)} when the message is
     * recognized as a game-related request, command, or event.
     *<P>
     * Some game messages (such as player sits down, or board reset voting) are handled the same for all game types.
     * These are handled by {@link SOCServerMessageHandler}; they should be ignored here and not appear in the
     * switch statement.
     *<P>
     * Caller of this method will catch any thrown Exceptions.
     *
     * @param game  Game in which client {@code connection} is sending {@code message}.
     *     Never null; from {@link SOCMessageForGame#getGame()}.
     * @param message  Message from client {@code connection}. Never null.
     * @param connection  Connection to the Client sending {@code message}. Never null.
     * @return true if processed, false if ignored or unknown message type
     */
    public boolean dispatch
        (SOCGame game, SOCMessageForGame message, Connection connection)
        throws Exception
    {
        switch (message.getType())
        {

        /**
         * someone put a piece on the board
         */
        case SOCMessage.PUTPIECE:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handlePUTPIECE(game, connection, (SOCPutPiece) message);

            //ga = (SOCGame)gamesData.get(((SOCPutPiece)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCPutPiece)mes).getGame());
            break;

        /**
         * a player is moving the robber or pirate
         */
        case SOCMessage.MOVEROBBER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleMOVEROBBER(game, connection, (SOCMoveRobber) message);

            //ga = (SOCGame)gamesData.get(((SOCMoveRobber)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCMoveRobber)mes).getGame());
            break;

        case SOCMessage.ROLLDICE:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleROLLDICE(game, connection, (SOCRollDice) message);

            //ga = (SOCGame)gamesData.get(((SOCRollDice)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCRollDice)mes).getGame());
            break;

        case SOCMessage.DISCARD:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleDISCARD(game, connection, (SOCDiscard) message);

            //ga = (SOCGame)gamesData.get(((SOCDiscard)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCDiscard)mes).getGame());
            break;

        case SOCMessage.ENDTURN:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleENDTURN(game, connection, (SOCEndTurn) message);

            //ga = (SOCGame)gamesData.get(((SOCEndTurn)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCEndTurn)mes).getGame());
            break;

        case SOCMessage.CHOOSEPLAYER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleCHOOSEPLAYER(game, connection, (SOCChoosePlayer) message);

            //ga = (SOCGame)gamesData.get(((SOCChoosePlayer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCChoosePlayer)mes).getGame());
            break;

        case SOCMessage.MAKEOFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleMAKEOFFER(game, connection, (SOCMakeOffer) message);

            //ga = (SOCGame)gamesData.get(((SOCMakeOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCMakeOffer)mes).getGame());
            break;

        case SOCMessage.CLEAROFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleCLEAROFFER(game, connection, (SOCClearOffer) message);

            //ga = (SOCGame)gamesData.get(((SOCClearOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCClearOffer)mes).getGame());
            break;

        case SOCMessage.REJECTOFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleREJECTOFFER(game, connection, (SOCRejectOffer) message);

            //ga = (SOCGame)gamesData.get(((SOCRejectOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCRejectOffer)mes).getGame());
            break;

        case SOCMessage.ACCEPTOFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleACCEPTOFFER(game, connection, (SOCAcceptOffer) message);

            //ga = (SOCGame)gamesData.get(((SOCAcceptOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCAcceptOffer)mes).getGame());
            break;

        case SOCMessage.BANKTRADE:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleBANKTRADE(game, connection, (SOCBankTrade) message);

            //ga = (SOCGame)gamesData.get(((SOCBankTrade)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCBankTrade)mes).getGame());
            break;

        case SOCMessage.BUILDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleBUILDREQUEST(game, connection, (SOCBuildRequest) message);

            //ga = (SOCGame)gamesData.get(((SOCBuildRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCBuildRequest)mes).getGame());
            break;

        case SOCMessage.CANCELBUILDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleCANCELBUILDREQUEST(game, connection, (SOCCancelBuildRequest) message);

            //ga = (SOCGame)gamesData.get(((SOCCancelBuildRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCCancelBuildRequest)mes).getGame());
            break;

        case SOCMessage.BUYDEVCARDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handleBUYDEVCARDREQUEST(game, connection, (SOCBuyDevCardRequest) message);

            //ga = (SOCGame)gamesData.get(((SOCBuyDevCardRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCBuyDevCardRequest)mes).getGame());
            break;

        case SOCMessage.PLAYDEVCARDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handlePLAYDEVCARDREQUEST(game, connection, (SOCPlayDevCardRequest) message);

            //ga = (SOCGame)gamesData.get(((SOCPlayDevCardRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCPlayDevCardRequest)mes).getGame());
            break;

        case SOCMessage.PICKRESOURCES:  // Discovery / Year of Plenty / Gold Hex resource picks

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handlePICKRESOURCES(game, connection, (SOCPickResources) message);

            //ga = (SOCGame)gamesData.get(((SOCPickResources)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCPickResources)mes).getGame());
            break;

        case SOCMessage.PICKRESOURCETYPE:  // Resource Type / Monopoly pick

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handlePICKRESOURCETYPE(game, connection, (SOCPickResourceType) message);

            //ga = (SOCGame)gamesData.get(((SOCPickResourceType)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCPickResourceType)mes).getGame());
            break;

        /**
         * debug piece Free Placement (as of 20110104 (v 1.1.12))
         */
        case SOCMessage.DEBUGFREEPLACE:
            handleDEBUGFREEPLACE(game, connection, (SOCDebugFreePlace) message);
            break;

        /**
         * Generic simple request from a player.
         * Added 2013-02-17 for v1.1.18.
         */
        case SOCMessage.SIMPLEREQUEST:
            handleSIMPLEREQUEST(game, connection, (SOCSimpleRequest) message);
            break;

        /**
         * Special inventory item action (play request) from a player.
         * Added 2013-11-28 for v2.0.00.
         */
        case SOCMessage.INVENTORYITEMACTION:
            handleINVENTORYITEMACTION(game, connection, (SOCInventoryItemAction) message);
            break;

        /**
         * Asking to move a previous piece (a ship) somewhere else on the board.
         * Added 2011-12-04 for v2.0.00.
         */
        case SOCMessage.MOVEPIECE:
            handleMOVEPIECE(game, connection, (SOCMovePiece) message);
            break;

        /**
         * Special Item requests.
         * Added 2014-05-17 for v2.0.00.
         */
        case SOCMessage.SETSPECIALITEM:
            handleSETSPECIALITEM(game, connection, (SOCSetSpecialItem) message);
            break;

        /**
         * Ignore all other message types, unknown message types.
         */
        default:
            return false;

        }  // switch (mes.getType)

        return true;  // Message was handled in a non-default case above
    }


    /// Roll dice and pick resources ///


    /**
     * handle "roll dice" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleROLLDICE(SOCGame ga, Connection c, final SOCRollDice mes)
    {
        final String gn = ga.getName();

        ga.takeMonitor();

        try
        {
            final String plName = c.getData();
            final SOCPlayer pl = ga.getPlayer(plName);
            if ((pl != null) && ga.canRollDice(pl.getPlayerNumber()))
            {
                /**
                 * Roll dice, distribute resources in game
                 */
                SOCGame.RollResult roll = ga.rollDice();

                /**
                 * Send roll results and then text to client.
                 * Note that only the total is sent, not the 2 individual dice.
                 * (Only the _SC_PIRI scenario cares about them indivdually, and
                 * in that case it prints the result when needed.)
                 *
                 * If a 7 is rolled, sendGameState will also say who must discard
                 * (in a GAMETEXTMSG).
                 * If a gold hex is rolled, sendGameState will also say who
                 * must pick resources to gain (in a GAMETEXTMSG).
                 */
                srv.messageToGame(gn, new SOCDiceResult(gn, ga.getCurrentDice()));
                if (ga.clientVersionLowest < SOCGameTextMsg.VERSION_FOR_DICE_RESULT_INSTEAD)
                {
                    // backwards-compat: this text message is redundant to v2.0.00 and newer clients
                    // because they print the roll results from SOCDiceResult.  Use SOCGameTextMsg
                    // because pre-2.0.00 clients don't understand SOCGameServerText messages.
                    srv.messageToGameForVersions(ga, 0, SOCGameTextMsg.VERSION_FOR_DICE_RESULT_INSTEAD - 1,
                        new SOCGameTextMsg
                            (gn, SOCGameTextMsg.SERVERNAME, plName + " rolled a " + roll.diceA + " and a " + roll.diceB + "."), // I18N
                        true);
                }
                handler.sendGameState(ga);  // For 7, give visual feedback before sending discard request

                if (ga.isGameOptionSet(SOCGameOption.K_SC_PIRI))
                {
                    // pirate moves on every roll
                    srv.messageToGame(gn, new SOCMoveRobber
                        (gn, ga.getCurrentPlayerNumber(), -( ((SOCBoardLarge) ga.getBoard()).getPirateHex() )));

                    if (roll.sc_piri_fleetAttackVictim != null)
                    {
                        final SOCResourceSet loot = roll.sc_piri_fleetAttackRsrcs;
                        final int lootTotal = (loot != null) ? loot.getTotal() : 0;
                        if (lootTotal != 0)
                        {
                            // use same resource-loss messages sent in handleDISCARD

                            final boolean won = (loot.contains(SOCResourceConstants.GOLD_LOCAL));
                            SOCPlayer vic = roll.sc_piri_fleetAttackVictim;
                            final String vicName = vic.getName();
                            final Connection vCon = srv.getConnection(vicName);
                            final int vpn = vic.getPlayerNumber();
                            final int strength = (roll.diceA < roll.diceB) ? roll.diceA : roll.diceB;

                            if (won)
                            {
                                srv.messageToGameKeyed
                                    (ga, true, "action.rolled.sc_piri.player.won.pick.free", vicName, strength);
                                    // "{0} won against the pirate fleet (strength {1}) and will pick a free resource."
                            } else {
                                /**
                                 * tell the victim client that the player lost the resources
                                 */
                                handler.reportRsrcGainLoss(gn, loot, true, true, vpn, -1, null, vCon);
                                srv.messageToPlayerKeyedSpecial
                                    (vCon, ga, "action.rolled.sc_piri.you.lost.rsrcs.to.fleet", loot, strength);
                                    // "You lost {0,rsrcs} to the pirate fleet (strength {1,number})."

                                /**
                                 * tell everyone else that the player lost unknown resources
                                 */
                                srv.messageToGameExcept(gn, vCon, new SOCPlayerElement
                                    (gn, vpn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, lootTotal), true);
                                srv.messageToGameKeyedSpecialExcept(ga, true, vCon,
                                    "action.rolled.sc_piri.player.lost.rsrcs.to.fleet", vicName, lootTotal, strength);
                                    // "Joe lost 1 resource to pirate fleet attack (strength 3)." or
                                    // "Joe lost 3 resources to pirate fleet attack (strength 3)."
                            }
                        }
                    }
                }

                /**
                 * if the roll is not 7, tell players what they got
                 * (if 7, sendGameState already told them what they lost).
                 */
                if (ga.getCurrentDice() != 7)
                {
                    boolean noPlayersGained = true;

                    /**
                     * Clients v2.0.00 and newer get an i18n-neutral SOCDiceResultResources message.
                     * Older clients get a string such as "Joe gets 3 sheep. Mike gets 1 clay."
                     */
                    String rollRsrcTxtOldCli = null;
                    SOCDiceResultResources rollRsrcMsgNewCli = null;

                    if (ga.clientVersionHighest >= SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES)
                    {
                        rollRsrcMsgNewCli = SOCDiceResultResources.buildForGame(ga);
                        noPlayersGained = (rollRsrcMsgNewCli == null);
                    }

                    if (ga.clientVersionLowest < SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES)
                    {
                        // Build a string to announce to v1.x.xx clients
                    StringBuffer gainsText = new StringBuffer();

                    noPlayersGained = true;  // for string spacing; might be false due to loop for new clients in game
                    for (int pn = 0; pn < ga.maxPlayers; ++pn)
                    {
                        if (! ga.isSeatVacant(pn))
                        {
                            SOCPlayer pp = ga.getPlayer(pn);
                            SOCResourceSet rsrcs = pp.getRolledResources();

                            if (rsrcs.getKnownTotal() != 0)
                            {
                                if (noPlayersGained)
                                    noPlayersGained = false;
                                else
                                    gainsText.append(" ");

                                gainsText.append
                                    (c.getLocalizedSpecial(ga, "_nolocaliz.roll.gets.resources", pp.getName(), rsrcs));
                                    // "{0} gets {1,rsrcs}."
                                    // get it from any connection's StringManager, because that string is never localized

                                // Announce SOCPlayerElement.GAIN messages
                                handler.reportRsrcGainLoss(gn, rsrcs, false, false, pn, -1, null, null);
                            }
                        }
                    }

                    if (! noPlayersGained)
                        rollRsrcTxtOldCli = gainsText.toString();

                    }

                    if (noPlayersGained)
                    {
                        String key;
                        if (roll.cloth == null)
                            key = "action.rolled.no.player.gets.anything";  // "No player gets anything."
                        else
                            key = "action.rolled.no.player.gets.resources";  // "No player gets resources."
                        // debug_printPieceDiceNumbers(ga, message);
                        srv.messageToGameKeyed(ga, true, key);
                    } else {
                        if (rollRsrcTxtOldCli == null)
                            srv.messageToGame(gn, rollRsrcMsgNewCli);
                        else if (rollRsrcMsgNewCli == null)
                            srv.messageToGame(gn, rollRsrcTxtOldCli);
                        else
                        {
                            // neither is null: we have old and new clients
                            srv.messageToGameForVersions
                                (ga, 0, (SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES - 1),
                                 new SOCGameTextMsg(gn, SOCGameTextMsg.SERVERNAME, rollRsrcTxtOldCli), true);
                            srv.messageToGameForVersions
                                (ga, SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES, Integer.MAX_VALUE,
                                 rollRsrcMsgNewCli, true);
                        }

                        //
                        //  Send gaining players all their resource info for accuracy
                        //  and announce their new resource totals to game
                        //
                        for (int pn = 0; pn < ga.maxPlayers; ++pn)
                        {
                            final SOCPlayer pp = ga.getPlayer(pn);
                            if (pp.getRolledResources().getKnownTotal() == 0)
                                continue;  // skip if player didn't gain; before v2.0.00 each player in game got these
                            final Connection playerCon = srv.getConnection(pp.getName());
                            if (playerCon == null)
                                continue;

                            // send CLAY, ORE, SHEEP, WHEAT, WOOD even if player's amount is 0
                            final SOCResourceSet resources = pp.getResources();
                            final int[] counts = resources.getAmounts(false);
                            if (playerCon.getVersion() >= SOCPlayerElements.MIN_VERSION)
                                srv.messageToPlayer(playerCon, new SOCPlayerElements
                                    (gn, pn, SOCPlayerElement.SET, SOCGameHandler.ELEM_RESOURCES, counts));
                            else
                                for (int i = 0; i < counts.length; ++i)
                                    srv.messageToPlayer(playerCon, new SOCPlayerElement
                                        (gn, pn, SOCPlayerElement.SET, SOCGameHandler.ELEM_RESOURCES[i], counts[i]));

                            if (ga.clientVersionLowest < SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES)
                                srv.messageToGame(gn, new SOCResourceCount(gn, pn, resources.getTotal()));
                            // else, already-sent SOCDiceResultResources included players' new resource totals

                            // we'll send gold picks text, PLAYERELEMENT, and SIMPLEREQUEST(PROMPT_PICK_RESOURCES)
                            // after the per-player loop
                        }
                    }

                    if (roll.cloth != null)
                    {
                        // Send village cloth trade distribution

                        final int coord = roll.cloth[1];
                        final SOCBoardLarge board = (SOCBoardLarge) (ga.getBoard());
                        SOCVillage vi = board.getVillageAtNode(coord);
                        if (vi != null)
                            srv.messageToGame(gn, new SOCPieceValue
                                (gn, SOCPlayingPiece.VILLAGE, coord, vi.getCloth(), 0));

                        if (roll.cloth[0] > 0)
                            // some taken from board general supply
                            srv.messageToGame(gn, new SOCPlayerElement
                                (gn, -1, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_CLOTH_COUNT, board.getCloth()));

                        String clplName = null;   // name of first player to receive cloth
                        ArrayList<String> clpls = null;  // names of all players receiving cloth, if more than one
                        for (int i = 2; i < roll.cloth.length; ++i)
                        {
                            if (roll.cloth[i] == 0)
                                continue;  // this player didn't receive cloth

                            final int pn = i - 2;
                            final SOCPlayer clpl = ga.getPlayer(pn);
                            srv.messageToGame(gn, new SOCPlayerElement
                                (gn, pn, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_CLOTH_COUNT, clpl.getCloth()));

                            if (clplName == null)
                            {
                                // first pl to receive cloth
                                clplName = clpl.getName();
                            } else {
                                // second or further player
                                if (clpls == null)
                                {
                                    clpls = new ArrayList<String>();
                                    clpls.add(clplName);
                                }
                                clpls.add(clpl.getName());
                            }
                        }

                        if (clpls == null)
                            srv.messageToGameKeyed(ga, true, "action.rolled.sc_clvi.received.cloth.1", clplName);
                                // "{0} received 1 cloth from a village."
                        else
                            srv.messageToGameKeyedSpecial(ga, true, "action.rolled.sc_clvi.received.cloth.n", clpls);
                                // "{0,list} each received 1 cloth from a village."
                    }

                    if (ga.getGameState() == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                        // gold picks text, PLAYERELEMENT, and SIMPLEREQUEST(PROMPT_PICK_RESOURCES)s
                        handler.sendGameState_sendGoldPickAnnounceText(ga, gn, null, roll);

                    /*
                       if (D.ebugOn) {
                       for (int i=0; i < SOCGame.MAXPLAYERS; i++) {
                       SOCResourceSet rsrcs = ga.getPlayer(i).getResources();
                       String resourceMessage = "PLAYER "+i+" RESOURCES: ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.CLAY)+" ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.ORE)+" ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.SHEEP)+" ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.WHEAT)+" ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.WOOD)+" ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.UNKNOWN)+" ";
                       messageToGame(gn, new SOCGameTextMsg(gn, SERVERNAME, resourceMessage));
                       }
                       }
                     */
                }
                else
                {
                    /**
                     * player rolled 7
                     * If anyone needs to discard, prompt them.
                     */
                    if (ga.getGameState() == SOCGame.WAITING_FOR_DISCARDS)
                    {
                        handler.sendGameState_sendDiscardRequests(ga, gn);
                    }
                    else if (ga.getGameState() == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                    {
                        // Used in _SC_PIRI, when 7 is rolled and a player wins against the pirate fleet
                        for (int pn = 0; pn < ga.maxPlayers; ++pn)
                        {
                            final SOCPlayer pp = ga.getPlayer(pn);
                            final int numPick = pp.getNeedToPickGoldHexResources();
                            if (( ! ga.isSeatVacant(pn)) && (numPick > 0))
                            {
                                Connection con = srv.getConnection(pp.getName());
                                if (con != null)
                                {
                                    srv.messageToGame(gn, new SOCPlayerElement
                                        (gn, pn, SOCPlayerElement.SET, SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES, numPick));
                                    con.put
                                        (SOCSimpleRequest.toCmd
                                            (gn, pn, SOCSimpleRequest.PROMPT_PICK_RESOURCES, numPick, 0));
                                }
                            }
                        }
                    }
                }
            }
            else
            {
                srv.messageToPlayer(c, gn, "You can't roll right now.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught at handleROLLDICE" + e);
        }

        ga.releaseMonitor();
    }

    /**
     * handle "discard" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleDISCARD(SOCGame ga, Connection c, final SOCDiscard mes)
    {
        final String gn = ga.getName();
        final SOCPlayer player = ga.getPlayer(c.getData());
        final int pn;
        if (player != null)
            pn = player.getPlayerNumber();
        else
            pn = -1;  // c's client no longer in the game

        ga.takeMonitor();
        try
        {
            if (player == null)
            {
                // The catch block will print this out semi-nicely
                throw new IllegalArgumentException("player not found in game");
            }

            final SOCResourceSet res = mes.getResources();
            if (ga.canDiscard(pn, res))
            {
                ga.discard(pn, res);  // discard, maybe change gameState

                // Same resource-loss messages are sent in handleROLLDICE after a pirate fleet attack (_SC_PIRI).

                /**
                 * tell the player client that the player discarded the resources
                 */
                handler.reportRsrcGainLoss(gn, res, true, false, pn, -1, null, c);

                /**
                 * tell everyone else that the player discarded unknown resources
                 */
                final int numRes = res.getTotal();
                srv.messageToGameExcept
                    (gn, c, new SOCPlayerElement
                        (gn, pn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, numRes, true),
                     true);
                srv.messageToGameKeyed(ga, true, "action.discarded", player.getName(), numRes);
                    // "{0} discarded {1} resources."

                /**
                 * send the new state, or end turn if was marked earlier as forced
                 */
                final int gstate = ga.getGameState();
                if ((gstate != SOCGame.PLAY1) || ! ga.isForcingEndTurn())
                {
                    if (gstate == SOCGame.WAITING_FOR_DISCARDS)
                        handler.sendGameState(ga, true, false);  // send only text prompt, not redundant GAMESTATE
                    else
                        handler.sendGameState(ga);
                            // if state is WAITING_FOR_ROB_CHOOSE_PLAYER (_SC_PIRI), also sends CHOOSEPLAYERREQUEST
                } else {
                    handler.endGameTurn(ga, player, true);  // already did ga.takeMonitor()
                }
            } else {
                /**
                 * (TODO) client knows how many discards are needed and should prevent this,
                 * but there could be a better feedback message here
                 */
                srv.messageToPlayer(c, gn, "You can't discard that many cards.");
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }


    /// Robber/pirate robbery ///


    /**
     * handle "move robber" message (move the robber or the pirate).
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleMOVEROBBER(SOCGame ga, Connection c, SOCMoveRobber mes)
    {
        ga.takeMonitor();

        try
        {
            SOCPlayer player = ga.getPlayer(c.getData());

            /**
             * make sure the player can do it
             */
            final String gaName = ga.getName();
            final boolean isPirate = ga.getRobberyPirateFlag();
            final int pn = player.getPlayerNumber();
            int coord = mes.getCoordinates();  // negative for pirate
            final boolean canDo =
                (isPirate == (coord < 0))
                && (isPirate ? ga.canMovePirate(pn, -coord)
                             : ga.canMoveRobber(pn, coord));
            if (canDo)
            {
                SOCMoveRobberResult result;

                SOCMoveRobber moveMsg;
                if (isPirate)
                {
                    result = ga.movePirate(pn, -coord);
                    moveMsg = new SOCMoveRobber(gaName, pn, coord);
                } else {
                    result = ga.moveRobber(pn, coord);
                    moveMsg = new SOCMoveRobber(gaName, pn, coord);
                }
                srv.messageToGame(gaName, moveMsg);

                final List<SOCPlayer> victims = result.getVictims();

                /** only one possible victim */
                if ((victims.size() == 1) && (ga.getGameState() != SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE))
                {
                    /**
                     * report what was stolen
                     */
                    SOCPlayer victim = victims.get(0);
                    handler.reportRobbery(ga, player, victim, result.getLoot());
                } else {
                    final String msgKey;
                    // These messages use ChoiceFormat to choose "robber" or "pirate":
                    //    robberpirate.moved={0} moved {1,choice, 1#the robber|2#the pirate}.

                    /** no victim */
                    if (victims.size() == 0)
                    {
                        /**
                         * just say it was moved; nothing is stolen
                         */
                        msgKey = "robberpirate.moved";  // "{0} moved the robber" or "{0} moved the pirate"
                    }
                    else if (ga.getGameState() == SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE)
                    {
                        /**
                         * only one possible victim, they have both clay and resources
                         */
                        msgKey = "robberpirate.moved.choose.cloth.rsrcs";
                            // "{0} moved the robber/pirate. Must choose to steal cloth or steal resources."
                    }
                    else
                    {
                        /**
                         * else, the player needs to choose a victim
                         */
                        msgKey = "robberpirate.moved.choose.victim";
                            // "{0} moved the robber/pirate. Must choose a victim."
                    }

                    srv.messageToGameKeyed(ga, true, msgKey, player.getName(), ((isPirate) ? 2 : 1));
                }

                handler.sendGameState(ga);
                    // For WAITING_FOR_ROB_CHOOSE_PLAYER, sendGameState also sends messages
                    // with victim info to prompt the client to choose.
                    // For WAITING_FOR_ROB_CLOTH_OR_RESOURCE, no need to recalculate
                    // victims there, just send the prompt from here:
                if (ga.getGameState() == SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE)
                {
                    final int vpn = victims.get(0).getPlayerNumber();
                    srv.messageToPlayer(c, new SOCChoosePlayer(gaName, vpn));
                }
            } else {
                srv.messageToPlayerKeyed
                    (c, gaName, ((coord < 0) ? "robber.cantmove.pirate" : "robber.cantmove"));
                    // "You can't move the pirate" / "You can't move the robber"
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "choose player" message during robbery.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleCHOOSEPLAYER(SOCGame ga, Connection c, final SOCChoosePlayer mes)
    {
        ga.takeMonitor();

        try
        {
            if (handler.checkTurn(c, ga))
            {
                final int choice = mes.getChoice();
                switch (ga.getGameState())
                {
                case SOCGame.WAITING_FOR_ROBBER_OR_PIRATE:
                    ga.chooseMovePirate(choice == SOCChoosePlayer.CHOICE_MOVE_PIRATE);
                    handler.sendGameState(ga);
                    break;

                case SOCGame.WAITING_FOR_ROB_CHOOSE_PLAYER:
                    if ((choice == SOCChoosePlayer.CHOICE_NO_PLAYER) && ga.canChoosePlayer(-1))
                    {
                        ga.choosePlayerForRobbery(-1);  // state becomes PLAY1
                        srv.messageToGameKeyed(ga, true, "robber.declined", c.getData());  // "{0} declined to steal."
                        handler.sendGameState(ga);
                    }
                    else if (ga.canChoosePlayer(choice))
                    {
                        final int rsrc = ga.choosePlayerForRobbery(choice);
                        final boolean waitingClothOrRsrc = (ga.getGameState() == SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE);
                        if (! waitingClothOrRsrc)
                        {
                            handler.reportRobbery
                                (ga, ga.getPlayer(c.getData()), ga.getPlayer(choice), rsrc);
                        } else {
                            srv.messageToGameKeyed(ga, true, "robber.moved.choose.cloth.rsrcs",
                                c.getData(), ga.getPlayer(choice).getName());
                                // "{0} moved the pirate, must choose to steal cloth or steal resources from {1}."
                        }
                        handler.sendGameState(ga);
                        if (waitingClothOrRsrc)
                            srv.messageToPlayer(c, new SOCChoosePlayer(ga.getName(), choice));
                    } else {
                        srv.messageToPlayerKeyed(c, ga.getName(), "robber.cantsteal");  // "You can't steal from that player."
                    }
                    break;

                case SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE:
                    {
                        final boolean stealCloth;
                        final int pn;
                        if (choice < 0)
                        {
                            stealCloth = true;
                            pn = (-choice) - 1;
                        } else {
                            stealCloth = false;
                            pn = choice;
                        }
                        if (ga.canChoosePlayer(pn) && ga.canChooseRobClothOrResource(pn))
                        {
                            final int rsrc = ga.stealFromPlayer(pn, stealCloth);
                            handler.reportRobbery
                                (ga, ga.getPlayer(c.getData()), ga.getPlayer(pn), rsrc);
                            handler.sendGameState(ga);
                            break;
                        }
                        // else, fall through and send "can't steal" message
                    }

                default:
                    srv.messageToPlayerKeyed(c, ga.getName(), "robber.cantsteal");  // "You can't steal from that player."
                }
            } else {
                srv.messageToPlayerKeyed(c, ga.getName(), "reply.not.your.turn");  // "It's not your turn."
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }


    /// Flow of Game ///


    /**
     * handle "end turn" message.
     * This normally ends a player's normal turn (phase {@link SOCGame#PLAY1}).
     * On the 6-player board, it ends their placements during the
     * {@link SOCGame#SPECIAL_BUILDING Special Building Phase}.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleENDTURN(SOCGame ga, Connection c, final SOCEndTurn mes)
    {
        final String gname = ga.getName();

        if (ga.isDebugFreePlacement())
        {
            // turn that off before ending current turn
            handler.processDebugCommand_freePlace(c, gname, "0");
        }

        ga.takeMonitor();

        final int gaState = ga.getGameState();

        try
        {
            final String plName = c.getData();
            if (gaState == SOCGame.OVER)
            {
                // Should not happen; is here just in case.
                SOCPlayer pl = ga.getPlayer(plName);
                if (pl != null)
                {
                    String msg = ga.gameOverMessageToPlayer(pl);
                        // msg = "The game is over; you are the winner!";
                        // msg = "The game is over; <someone> won.";
                        // msg = "The game is over; no one won.";
                    srv.messageToPlayer(c, gname, msg);
                }
            } else if (handler.checkTurn(c, ga)) {
                SOCPlayer pl = ga.getPlayer(plName);
                if ((pl != null) && ga.canEndTurn(pl.getPlayerNumber()))
                {
                    if (gaState == SOCGame.PLACING_FREE_ROAD1)
                        srv.messageToGameKeyed(ga, true, "action.card.roadbuilding.cancel", pl.getName());
                            // "{0} cancelled the Road Building card."
                    else if (gaState == SOCGame.PLACING_FREE_ROAD2)
                        srv.messageToGameKeyed(ga, true, "action.card.roadbuilding.skip.r", pl.getName());
                            // "{0} skipped placing the second road."

                    handler.endGameTurn(ga, pl, true);
                }
                else
                    srv.messageToPlayer(c, gname, "You can't end your turn yet.");
            } else {
                srv.messageToPlayer(c, gname, "It's not your turn.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught at handleENDTURN");
        }

        ga.releaseMonitor();
    }

    /**
     * Handle the "simple request" message.
     * @param c  the connection
     * @param mes  the message
     * @since 1.1.18
     */
    private void handleSIMPLEREQUEST(SOCGame ga, Connection c, final SOCSimpleRequest mes)
    {
        final String gaName = ga.getName();
        SOCPlayer clientPl = ga.getPlayer(c.getData());
        if (clientPl == null)
            return;

        final int pn = mes.getPlayerNumber();
        final boolean clientIsPN = (pn == clientPl.getPlayerNumber());  // probably required for most request types
        final int reqtype = mes.getRequestType();
        final int cpn = ga.getCurrentPlayerNumber();

        boolean replyDecline = false;  // if true, reply with generic decline (pn = -1, reqtype, 0, 0)

        switch (reqtype)
        {
        case SOCSimpleRequest.SC_PIRI_FORT_ATTACK:
            {
                final SOCShip adjac = ga.canAttackPirateFortress();
                if ((! clientIsPN) || (pn != cpn) || (adjac == null) || (adjac.getPlayerNumber() != cpn))
                {
                    c.put(SOCSimpleRequest.toCmd(gaName, -1, reqtype, 0, 0));
                    return;  // <--- early return: deny ---
                }

                final int prevState = ga.getGameState();
                final SOCPlayer cp = ga.getPlayer(cpn);
                final int prevNumWarships = cp.getNumWarships();  // in case some are lost, we'll announce that
                final SOCFortress fort = cp.getFortress();

                final int[] res = ga.attackPirateFortress(adjac);

                if (res.length > 1)
                {
                    // lost 1 or 2 ships adjacent to fortress.  res[1] == adjac.coordinate

                    srv.messageToGame(gaName, new SOCRemovePiece(gaName, adjac));
                    if (res.length > 2)
                        srv.messageToGame(gaName, new SOCRemovePiece(gaName, cpn, SOCPlayingPiece.SHIP, res[2]));

                    final int n = cp.getNumWarships();
                    if (n != prevNumWarships)
                        srv.messageToGame(gaName, new SOCPlayerElement
                            (gaName, cpn, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_WARSHIP_COUNT, n));
                } else {
                    // player won battle

                    final int fortStrength = fort.getStrength();
                    srv.messageToGame(gaName, new SOCPieceValue
                        (gaName, SOCPlayingPiece.FORTRESS, fort.getCoordinates(), fortStrength, 0));
                    if (0 == fortStrength)
                        srv.messageToGame(gaName, new SOCPutPiece
                            (gaName, cpn, SOCPlayingPiece.SETTLEMENT, fort.getCoordinates()));
                }

                srv.messageToGame(gaName, new SOCSimpleAction
                    (gaName, cpn, SOCSimpleAction.SC_PIRI_FORT_ATTACK_RESULT, res[0], res.length - 1));

                // check for end of player's turn
                if (! handler.checkTurn(c, ga))
                {
                    handler.endGameTurn(ga, cp, false);
                } else {
                    // still player's turn, even if they won
                    final int gstate = ga.getGameState();
                    if (gstate != prevState)
                        handler.sendGameState(ga);  // might be OVER, if player won
                }
            }
            break;

        case SOCSimpleRequest.TRADE_PORT_PLACE:
            {
                if (clientIsPN && (pn == cpn))
                {
                    final int edge = mes.getValue1();
                    if ((ga.getGameState() == SOCGame.PLACING_INV_ITEM) && ga.canPlacePort(clientPl, edge))
                    {
                        final int ptype = ga.placePort(edge);

                        handler.sendGameState(ga);  // PLAY1 or SPECIAL_BUILDING
                        srv.messageToGame(gaName, new SOCSimpleRequest
                            (gaName, cpn, SOCSimpleRequest.TRADE_PORT_PLACE, edge, ptype));
                    } else {
                        replyDecline = true;  // client will print a text message, no need to send one
                    }
                } else {
                    srv.messageToPlayerKeyed(c, gaName, "reply.not.your.turn");
                    replyDecline = true;
                }
            }
            break;

        default:
            // deny unknown types
            replyDecline = true;
            System.err.println
                ("handleSIMPLEREQUEST: Unknown type " + reqtype + " from " + c.getData() + " in game " + ga);
        }

        if (replyDecline)
            c.put(SOCSimpleRequest.toCmd(gaName, -1, reqtype, 0, 0));
    }


    /// Player trades and bank trades ///


    /**
     * handle "make offer" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleMAKEOFFER(SOCGame ga, Connection c, final SOCMakeOffer mes)
    {
        final String gaName = ga.getName();
        if (ga.isGameOptionSet("NT"))
        {
            srv.messageToPlayer(c, gaName, "Trading is not allowed in this game.");
            return;  // <---- Early return: No Trading ----
        }

        ga.takeMonitor();

        try
        {
            SOCTradeOffer offer = mes.getOffer();

            /**
             * remake the offer with data that we know is accurate,
             * namely the 'from' datum
             */
            SOCPlayer player = ga.getPlayer(c.getData());

            /**
             * announce the offer, including text message similar to bank/port trade.
             */
            if (player != null)
            {
                SOCTradeOffer remadeOffer;
                {
                    SOCResourceSet offGive = offer.getGiveSet(),
                                   offGet  = offer.getGetSet();
                    remadeOffer = new SOCTradeOffer(gaName, player.getPlayerNumber(), offer.getTo(), offGive, offGet);
                    player.setCurrentOffer(remadeOffer);

                    // v2.0.00 and newer clients will announce this with localized text;
                    // older clients need it sent from the server
                    if (ga.clientVersionLowest < SOCStringManager.VERSION_FOR_I18N)
                    {
                        // I18N OK: Pre-2.0.00 clients always use english
                        final String txt = SOCStringManager.getFallbackServerManagerForClient().formatSpecial
                            (ga, "{0} offered to give {1,rsrcs} for {2,rsrcs}.", player.getName(), offGive, offGet);
                        srv.messageToGameForVersions
                            (ga, 0, SOCStringManager.VERSION_FOR_I18N - 1,
                             new SOCGameTextMsg(gaName, SOCServer.SERVERNAME, txt), true);
                    }
                }

                SOCMakeOffer makeOfferMessage = new SOCMakeOffer(gaName, remadeOffer);
                srv.messageToGame(gaName, makeOfferMessage);

                srv.recordGameEvent(gaName, makeOfferMessage);

                /**
                 * clear all the trade messages because a new offer has been made
                 */
                srv.gameList.takeMonitorForGame(gaName);
                if (ga.clientVersionLowest >= SOCClearTradeMsg.VERSION_FOR_CLEAR_ALL)
                {
                    srv.messageToGameWithMon(gaName, new SOCClearTradeMsg(gaName, -1));
                } else {
                    for (int i = 0; i < ga.maxPlayers; i++)
                        srv.messageToGameWithMon(gaName, new SOCClearTradeMsg(gaName, i));
                }
                srv.gameList.releaseMonitorForGame(gaName);
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "clear offer" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleCLEAROFFER(SOCGame ga, Connection c, final SOCClearOffer mes)
    {
        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            ga.getPlayer(c.getData()).setCurrentOffer(null);
            srv.messageToGame(gaName, new SOCClearOffer(gaName, ga.getPlayer(c.getData()).getPlayerNumber()));
            srv.recordGameEvent(gaName, mes);

            /**
             * clear all the trade messages
             */
            srv.gameList.takeMonitorForGame(gaName);
            if (ga.clientVersionLowest >= SOCClearTradeMsg.VERSION_FOR_CLEAR_ALL)
            {
                srv.messageToGameWithMon(gaName, new SOCClearTradeMsg(gaName, -1));
            } else {
                for (int i = 0; i < ga.maxPlayers; i++)
                    srv.messageToGameWithMon(gaName, new SOCClearTradeMsg(gaName, i));
            }
            srv.gameList.releaseMonitorForGame(gaName);
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "reject offer" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleREJECTOFFER(final SOCGame ga, final Connection c, final SOCRejectOffer mes)
    {
        SOCPlayer player = ga.getPlayer(c.getData());
        if (player == null)
            return;
        final int pn = player.getPlayerNumber();

        try
        {
            ga.takeMonitor();
            ga.rejectTradeOffersTo(pn);
        } finally {
            ga.releaseMonitor();
        }

        final String gaName = ga.getName();
        SOCRejectOffer rejectMessage = new SOCRejectOffer(gaName, pn);
        srv.messageToGame(gaName, rejectMessage);

        srv.recordGameEvent(gaName, rejectMessage);
    }

    /**
     * handle "accept offer" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleACCEPTOFFER
        (final SOCGame ga, final Connection c, final SOCAcceptOffer mes)
    {
        ga.takeMonitor();

        try
        {
            SOCPlayer player = ga.getPlayer(c.getData());

            if (player != null)
            {
                final int acceptingNumber = player.getPlayerNumber();
                final int offeringNumber = mes.getOfferingNumber();
                final String gaName = ga.getName();

                if (ga.canMakeTrade(offeringNumber, acceptingNumber))
                {
                    ga.makeTrade(offeringNumber, acceptingNumber);
                    handler.reportTrade(ga, offeringNumber, acceptingNumber);

                    /**
                     * announce the accepted offer to game; won't re-send mes from client
                     * because its acceptingNumber isn't required or sanitized
                     */
                    final SOCAcceptOffer mesOut
                        = new SOCAcceptOffer(gaName, acceptingNumber, offeringNumber);
                    srv.messageToGame(gaName, mesOut);
                    srv.recordGameEvent(gaName, mesOut);

                    /**
                     * clear all offers
                     */
                    for (int i = 0; i < ga.maxPlayers; i++)
                        ga.getPlayer(i).setCurrentOffer(null);
                    srv.gameList.takeMonitorForGame(gaName);
                    if (ga.clientVersionLowest >= SOCClearOffer.VERSION_FOR_CLEAR_ALL)
                        srv.messageToGameWithMon(gaName, new SOCClearOffer(gaName, -1));
                    else
                        for (int i = 0; i < ga.maxPlayers; i++)
                            srv.messageToGameWithMon(gaName, new SOCClearOffer(gaName, i));
                    srv.gameList.releaseMonitorForGame(gaName);
                } else {
                    srv.messageToPlayer(c, gaName, "You can't make that trade.");
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "bank trade" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleBANKTRADE(SOCGame ga, Connection c, final SOCBankTrade mes)
    {
        final String gaName = ga.getName();
        final SOCResourceSet give = mes.getGiveSet(),
            get = mes.getGetSet();

        ga.takeMonitor();

        try
        {
            if (handler.checkTurn(c, ga))
            {
                if (ga.canMakeBankTrade(give, get))
                {
                    ga.makeBankTrade(give, get);
                    handler.reportBankTrade(ga, give, get);
                } else {
                    srv.messageToPlayer(c, gaName, "You can't make that trade.");
                    SOCClientData scd = (SOCClientData) c.getAppData();
                    if ((scd != null) && scd.isRobot)
                        D.ebugPrintln("ILLEGAL BANK TRADE: " + c.getData()
                          + ": give " + give + ", get " + get);
                }
            } else {
                srv.messageToPlayer(c, gaName, "It's not your turn.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }


    /// Game piece building, placement, and moving ///


    /**
     * handle "build request" message.
     * If client is current player, they want to buy a {@link SOCPlayingPiece}.
     * Otherwise, if 6-player board, they are requesting to build during the
     * next {@link SOCGame#SPECIAL_BUILDING Special Building Phase}.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     * @see #handleBUILDREQUEST(SOCGame, SOCPlayer, Connection, int, boolean)
     */
    private void handleBUILDREQUEST(SOCGame ga, Connection c, final SOCBuildRequest mes)
    {
        final String gaName = ga.getName();
        ga.takeMonitor();

        try
        {
            final boolean isCurrent = handler.checkTurn(c, ga);
            SOCPlayer player = ga.getPlayer(c.getData());
            final int pn = player.getPlayerNumber();
            final int pieceType = mes.getPieceType();
            boolean sendDenyReply = false;  // for robots' benefit

            if (isCurrent)
            {
                if ((ga.getGameState() == SOCGame.PLAY1) || (ga.getGameState() == SOCGame.SPECIAL_BUILDING))
                {
                    sendDenyReply = ! handleBUILDREQUEST(ga, player, c, pieceType, true);
                } else if (pieceType == -1) {
                    // 6-player board: Special Building Phase
                    // during start of own turn
                    try
                    {
                        ga.askSpecialBuild(pn, true);
                        srv.messageToGame
                            (gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 1));
                        handler.endGameTurn(ga, player, true);  // triggers start of SBP
                    } catch (IllegalStateException e) {
                        srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now.ask");  // "You can't ask to build now."
                        sendDenyReply = true;
                    }
                } else {
                    srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now");  // "You can't build now."
                    sendDenyReply = true;
                }
            } else {
                if (ga.maxPlayers <= 4)
                {
                    srv.messageToPlayerKeyed(c, gaName, "reply.not.your.turn");  // "It's not your turn."
                    sendDenyReply = true;
                } else {
                    // 6-player board: Special Building Phase
                    // during other player's turn
                    try
                    {
                        ga.askSpecialBuild(pn, true);  // will validate that they can build now
                        srv.messageToGame
                            (gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 1));
                    } catch (IllegalStateException e) {
                        srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now.ask");  // "You can't ask to build now."
                        sendDenyReply = true;
                    }
                }
            }

            if (sendDenyReply && ga.getPlayer(pn).isRobot())
            {
                srv.messageToPlayer(c, new SOCCancelBuildRequest(gaName, pieceType));
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught at handleBUILDREQUEST");
        }

        ga.releaseMonitor();
    }

    /**
     * Handle a client player's request to buy a type of playing piece,
     * for {@link #handleBUILDREQUEST(SOCGame, Connection, SOCBuildRequest)}
     * and {@link #handlePUTPIECE(SOCGame, Connection, SOCPutPiece)}:
     * Checks player piece counts and resources, buys the piece in game,
     * announces {@link SOCPlayerElement} messages for the resources spent,
     * optionally announces new game state to game's members.
     * If player can't buy, tells them that in a server text.
     *<P>
     * <B>Locks and preconditions:</B>
     *<UL>
     * <LI> Caller already called {@link SOCGame#takeMonitor() ga.takeMonitor()}
     * <LI> {@code player} already checked to be current player
     * <LI> {@link SOCGame#getGameState() ga.getGameState()} is either
     *      {@link SOCGame#PLAY1} or {@link SOCGame#SPECIAL_BUILDING}
     *</UL>
     * @param ga  The game
     * @param player  Requesting player; must be current player
     * @param c   Requesting {@code player}'s connection
     * @param pieceType {@link SOCPlayingPiece#SETTLEMENT}, {@link SOCPlayingPiece#SHIP}, etc
     * @param sendGameState  True if {@link SOCGameHander#sendGameState(SOCGame)} should be called
     *            after buying the piece
     * @return  True if piece build was allowed, false if it was rejected.<BR>
     *   If false, game state is unchanged. If true, it's a state like {@link SOCGame#PLACING_ROAD}
     *   after being changed here by a method like {@link SOCGame#buyRoad(int)}.
     * @since 2.0.00
     */
    private boolean handleBUILDREQUEST
        (final SOCGame ga, final SOCPlayer player, final Connection c, final int pieceType, final boolean sendGameState)
    {
        final String gaName = ga.getName();
        final int pn = player.getPlayerNumber();

        final boolean usePlayerElements = (ga.clientVersionLowest >= SOCPlayerElements.MIN_VERSION);
        boolean sendDenyReply = false;

        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:

            if (ga.couldBuildRoad(pn))
            {
                ga.buyRoad(pn);
                if (usePlayerElements)
                {
                    srv.messageToGame(gaName, new SOCPlayerElements
                        (gaName, pn, SOCPlayerElement.LOSE, SOCRoad.COST));
                } else {
                    srv.messageToGame(gaName, new SOCPlayerElement
                        (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.CLAY, 1));
                    srv.messageToGame(gaName, new SOCPlayerElement
                        (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                }
                if (sendGameState)
                    handler.sendGameState(ga);
            } else {
                srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now.road");  // "You can't build a road now."
                sendDenyReply = true;
            }

            break;

        case SOCPlayingPiece.SETTLEMENT:

            if (ga.couldBuildSettlement(pn))
            {
                ga.buySettlement(pn);
                if (usePlayerElements)
                {
                    srv.messageToGame(gaName, new SOCPlayerElements
                        (gaName, pn, SOCPlayerElement.LOSE, SOCSettlement.COST));
                } else {
                    srv.gameList.takeMonitorForGame(gaName);
                    srv.messageToGameWithMon(gaName, new SOCPlayerElement
                        (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.CLAY, 1));
                    srv.messageToGameWithMon(gaName, new SOCPlayerElement
                        (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1));
                    srv.messageToGameWithMon(gaName, new SOCPlayerElement
                        (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 1));
                    srv.messageToGameWithMon(gaName, new SOCPlayerElement
                        (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                    srv.gameList.releaseMonitorForGame(gaName);
                }
                if (sendGameState)
                    handler.sendGameState(ga);
            } else {
                srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now.stlmt");  // "You can't build a settlement now."
                sendDenyReply = true;
            }

            break;

        case SOCPlayingPiece.CITY:

            if (ga.couldBuildCity(pn))
            {
                ga.buyCity(pn);
                if (usePlayerElements)
                {
                    srv.messageToGame(gaName, new SOCPlayerElements
                        (gaName, pn, SOCPlayerElement.LOSE, SOCCity.COST));
                } else {
                    srv.messageToGame(ga.getName(), new SOCPlayerElement
                        (ga.getName(), pn, SOCPlayerElement.LOSE, SOCPlayerElement.ORE, 3));
                    srv.messageToGame(ga.getName(), new SOCPlayerElement
                        (ga.getName(), pn, SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 2));
                }
                if (sendGameState)
                    handler.sendGameState(ga);
            } else {
                srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now.city");  // "You can't build a city now."
                sendDenyReply = true;
            }

            break;

        case SOCPlayingPiece.SHIP:

            if (ga.couldBuildShip(pn))
            {
                ga.buyShip(pn);
                if (usePlayerElements)
                {
                    srv.messageToGame(gaName, new SOCPlayerElements
                        (gaName, pn, SOCPlayerElement.LOSE, SOCShip.COST));
                } else {
                    srv.messageToGame(gaName, new SOCPlayerElement
                        (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1));
                    srv.messageToGame(gaName, new SOCPlayerElement
                        (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                }
                if (sendGameState)
                    handler.sendGameState(ga);
            } else {
                srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now.ship");  // "You can't build a ship now."
                sendDenyReply = true;
            }

            break;

        default:
            srv.messageToPlayerKeyed(c, gaName, "reply.piece.type.unknown");  // "Unknown piece type."
            sendDenyReply = true;
        }

        return ! sendDenyReply;
    }

    /**
     * handle "cancel build request" message.
     * Cancel placement and send new game state, if cancel is allowed.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleCANCELBUILDREQUEST(SOCGame ga, Connection c, final SOCCancelBuildRequest mes)
    {
        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            if (handler.checkTurn(c, ga))
            {
                final SOCPlayer player = ga.getPlayer(c.getData());
                final int pn = player.getPlayerNumber();
                final int gstate = ga.getGameState();
                final boolean usePlayerElements = (ga.clientVersionLowest >= SOCPlayerElements.MIN_VERSION);

                boolean noAction = false;  // If true, there was nothing cancelable: Don't call handler.sendGameState

                switch (mes.getPieceType())
                {
                case SOCPlayingPiece.ROAD:

                    if ((gstate == SOCGame.PLACING_ROAD) || (gstate == SOCGame.PLACING_FREE_ROAD2))
                    {
                        ga.cancelBuildRoad(pn);
                        if (gstate == SOCGame.PLACING_ROAD)
                        {
                            if (usePlayerElements)
                            {
                                srv.messageToGame(gaName, new SOCPlayerElements
                                    (gaName, pn, SOCPlayerElement.GAIN, SOCRoad.COST));
                            } else {
                                srv.messageToGame(gaName, new SOCPlayerElement
                                    (gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.CLAY, 1));
                                srv.messageToGame(gaName, new SOCPlayerElement
                                    (gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                            }
                        } else {
                            srv.messageToGameKeyed(ga, true, "action.card.roadbuilding.skip.r", player.getName());
                                // "{0} skipped placing the second road."
                        }
                    } else {
                        srv.messageToPlayer(c, gaName, /*I*/"You didn't buy a road."/*18N*/ );
                        noAction = true;
                    }

                    break;

                case SOCPlayingPiece.SETTLEMENT:

                    if (gstate == SOCGame.PLACING_SETTLEMENT)
                    {
                        ga.cancelBuildSettlement(pn);
                        if (usePlayerElements)
                        {
                            srv.messageToGame(gaName, new SOCPlayerElements
                                (gaName, pn, SOCPlayerElement.GAIN, SOCSettlement.COST));
                        } else {
                            srv.gameList.takeMonitorForGame(gaName);
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.CLAY, 1));
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.SHEEP, 1));
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WHEAT, 1));
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                            srv.gameList.releaseMonitorForGame(gaName);
                        }
                    }
                    else if ((gstate == SOCGame.START1B) || (gstate == SOCGame.START2B) || (gstate == SOCGame.START3B))
                    {
                        SOCSettlement pp = new SOCSettlement(player, player.getLastSettlementCoord(), null);
                        ga.undoPutInitSettlement(pp);
                        srv.messageToGame(gaName, mes);  // Re-send to all clients to announce it
                            // (Safe since we've validated all message parameters)
                        srv.messageToGameKeyed(ga, true, "action.built.stlmt.cancel", player.getName());  //  "{0} cancelled this settlement placement."
                        // The handler.sendGameState below is redundant if client reaction changes game state
                    } else {
                        srv.messageToPlayer(c, gaName, /*I*/"You didn't buy a settlement."/*18N*/ );
                        noAction = true;
                    }

                    break;

                case SOCPlayingPiece.CITY:

                    if (gstate == SOCGame.PLACING_CITY)
                    {
                        ga.cancelBuildCity(pn);
                        if (usePlayerElements)
                        {
                            srv.messageToGame(gaName, new SOCPlayerElements
                                (gaName, pn, SOCPlayerElement.GAIN, SOCCity.COST));
                        } else {
                            srv.messageToGame(gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.ORE, 3));
                            srv.messageToGame(gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WHEAT, 2));
                        }
                    } else {
                        srv.messageToPlayer(c, gaName, /*I*/"You didn't buy a city."/*18N*/ );
                        noAction = true;
                    }

                    break;

                case SOCPlayingPiece.SHIP:

                    if ((gstate == SOCGame.PLACING_SHIP) || (gstate == SOCGame.PLACING_FREE_ROAD2))
                    {
                        ga.cancelBuildShip(pn);
                        if (gstate == SOCGame.PLACING_SHIP)
                        {
                            if (usePlayerElements)
                            {
                                srv.messageToGame(gaName, new SOCPlayerElements
                                    (gaName, pn, SOCPlayerElement.GAIN, SOCShip.COST));
                            } else {
                                srv.messageToGame(gaName, new SOCPlayerElement
                                    (gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.SHEEP, 1));
                                srv.messageToGame(gaName, new SOCPlayerElement
                                    (gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                            }
                        } else {
                            srv.messageToGameKeyed(ga, true, "action.card.roadbuilding.skip.s", player.getName());
                                // "{0} skipped placing the second ship."
                        }
                    } else {
                        srv.messageToPlayer(c, gaName, /*I*/"You didn't buy a ship."/*18N*/ );
                        noAction = true;
                    }

                    break;

                case SOCCancelBuildRequest.INV_ITEM_PLACE_CANCEL:
                    SOCInventoryItem item = null;
                    if (gstate == SOCGame.PLACING_INV_ITEM)
                        item = ga.cancelPlaceInventoryItem(false);

                    if (item != null)
                        srv.messageToGame(gaName, new SOCInventoryItemAction
                            (gaName, pn, SOCInventoryItemAction.ADD_PLAYABLE, item.itype,
                             item.isKept(), item.isVPItem(), item.canCancelPlay));

                    if ((item != null) || (gstate != ga.getGameState()))
                    {
                        srv.messageToGameKeyed(ga, true, "reply.placeitem.cancel", player.getName());
                            // "{0} canceled placement of a special item."
                    } else {
                        srv.messageToPlayerKeyed(c, gaName, "reply.placeitem.cancel.cannot");
                            // "Cannot cancel item placement."
                        noAction = true;
                    }
                    break;

                default:
                    throw new IllegalArgumentException("Unknown piece type " + mes.getPieceType());
                }

                if (! noAction)
                {
                    handler.sendGameState(ga);
                } else {
                    // bot is waiting for a gamestate reply, not text
                    final SOCClientData scd = (SOCClientData) c.getAppData();
                    if ((scd != null) && scd.isRobot)
                        c.put(SOCGameState.toCmd(gaName, gstate));
                }
            } else {
                srv.messageToPlayerKeyed(c, gaName, "reply.not.your.turn");  // "It's not your turn."
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "put piece" message.
     *<P>
     * Because the current player changes during initial placement,
     * this method has a simplified version of some of the logic from
     * {@link SOCGameHandler#endGameTurn(SOCGame, SOCPlayer, boolean)}
     * to detect and announce the new turn.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handlePUTPIECE(SOCGame ga, Connection c, SOCPutPiece mes)
    {
        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            final String plName = c.getData();
            SOCPlayer player = ga.getPlayer(plName);

            /**
             * make sure the player can do it
             */
            if (handler.checkTurn(c, ga))
            {
                boolean sendDenyReply = false;
                /*
                   if (D.ebugOn) {
                   D.ebugPrintln("BEFORE");
                   for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++) {
                   SOCPlayer tmpPlayer = ga.getPlayer(pn);
                   D.ebugPrintln("Player # "+pn);
                   for (int i = 0x22; i < 0xCC; i++) {
                   if (tmpPlayer.isPotentialRoad(i))
                   D.ebugPrintln("### POTENTIAL ROAD AT "+Integer.toHexString(i));
                   }
                   }
                   }
                 */

                int gameState = ga.getGameState();
                final int coord = mes.getCoordinates();
                final int pieceType = mes.getPieceType();
                final int pn = player.getPlayerNumber();
                final boolean isBuyAndPut = (gameState == SOCGame.PLAY1) || (gameState == SOCGame.SPECIAL_BUILDING);

                if (isBuyAndPut)
                {
                    // Handle combined buildrequest + putpiece message: (client v2.0.00 or newer)

                    if (! handleBUILDREQUEST(ga, player, c, pieceType, false))
                    {
                        return;  // <--- Can't build right now ---
                            // will call ga.releaseMonitor() in finally-block before returning
                    }

                    gameState = ga.getGameState();  // updated by handleBUILDREQUEST
                }

                switch (pieceType)
                {
                case SOCPlayingPiece.ROAD:

                    if ((gameState == SOCGame.START1B) || (gameState == SOCGame.START2B) || (gameState == SOCGame.START3B)
                        || (gameState == SOCGame.PLACING_ROAD)
                        || (gameState == SOCGame.PLACING_FREE_ROAD1) || (gameState == SOCGame.PLACING_FREE_ROAD2))
                    {
                        if (player.isPotentialRoad(coord) && (player.getNumPieces(SOCPlayingPiece.ROAD) >= 1))
                        {
                            final SOCRoad rd = new SOCRoad(player, coord, null);
                            ga.putPiece(rd);  // Changes game state and (if initial placement) player

                            // If placing this piece reveals a fog hex, putPiece will call srv.gameEvent
                            // which will send a SOCRevealFogHex message to the game.

                            /*
                               if (D.ebugOn) {
                               D.ebugPrintln("AFTER");
                               for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++) {
                               SOCPlayer tmpPlayer = ga.getPlayer(pn);
                               D.ebugPrintln("Player # "+pn);
                               for (int i = 0x22; i < 0xCC; i++) {
                               if (tmpPlayer.isPotentialRoad(i))
                               D.ebugPrintln("### POTENTIAL ROAD AT "+Integer.toHexString(i));
                               }
                               }
                               }
                             */
                            srv.gameList.takeMonitorForGame(gaName);
                            srv.messageToGameKeyed(ga, false, "action.built.road", plName);  // "Joe built a road."
                            srv.messageToGameWithMon(gaName, new SOCPutPiece(gaName, pn, SOCPlayingPiece.ROAD, coord));
                            if (! ga.pendingMessagesOut.isEmpty())
                                handler.sendGamePendingMessages(ga, false);
                            srv.gameList.releaseMonitorForGame(gaName);

                            // If needed, call sendTurn or send SOCRollDicePrompt
                            handler.sendTurnStateAtInitialPlacement(ga, player, c, gameState);

                            int newState = ga.getGameState();
                            if ((newState == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
                                || (newState == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE))
                            {
                                // gold hex revealed from fog (scenario SC_FOG)
                                handler.sendGameState_sendGoldPickAnnounceText(ga, gaName, c, null);
                            }
                        } else {
                            D.ebugPrintln("ILLEGAL ROAD: 0x" + Integer.toHexString(coord)
                                + ": player " + pn);
                            if (player.isRobot() && D.ebugOn)
                            {
                                D.ebugPrintln(" - pl.isPotentialRoad: " + player.isPotentialRoad(coord));
                                SOCPlayingPiece pp = ga.getBoard().roadOrShipAtEdge(coord);
                                D.ebugPrintln(" - roadAtEdge: " + ((pp != null) ? pp : "none"));
                            }

                            srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.there.road");
                                // "You can't build a road there."
                            sendDenyReply = true;
                        }
                    } else {
                        srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now.road");
                            // "You can't build a road now."
                    }

                    break;

                case SOCPlayingPiece.SETTLEMENT:

                    if ((gameState == SOCGame.START1A) || (gameState == SOCGame.START2A)
                        || (gameState == SOCGame.START3A) || (gameState == SOCGame.PLACING_SETTLEMENT))
                    {
                        if (player.canPlaceSettlement(coord) && (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) >= 1))
                        {
                            final SOCSettlement se = new SOCSettlement(player, coord, null);
                            ga.putPiece(se);   // Changes game state and (if initial placement) player

                            srv.gameList.takeMonitorForGame(gaName);
                            srv.messageToGameKeyed(ga, false, "action.built.stlmt", plName);  // "Joe built a settlement."
                            srv.messageToGameWithMon(gaName, new SOCPutPiece(gaName, pn, SOCPlayingPiece.SETTLEMENT, coord));
                            if (! ga.pendingMessagesOut.isEmpty())
                                handler.sendGamePendingMessages(ga, false);
                            srv.gameList.releaseMonitorForGame(gaName);

                            // Check player and send new game state
                            if (! handler.checkTurn(c, ga))
                                handler.sendTurn(ga, false);  // Announce new state and new current player
                            else
                                handler.sendGameState(ga);

                            if (ga.hasSeaBoard && (ga.getGameState() == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE))
                            {
                                // Prompt to pick from gold: send text and SOCSimpleRequest(PROMPT_PICK_RESOURCES)
                                handler.sendGameState_sendGoldPickAnnounceText(ga, gaName, c, null);
                            }
                        } else {
                            D.ebugPrintln("ILLEGAL SETTLEMENT: 0x" + Integer.toHexString(coord)
                                + ": player " + pn);
                            if (player.isRobot() && D.ebugOn)
                            {
                                D.ebugPrintln(" - pl.isPotentialSettlement: "
                                    + player.isPotentialSettlement(coord));
                                SOCPlayingPiece pp = ga.getBoard().settlementAtNode(coord);
                                D.ebugPrintln(" - settlementAtNode: " + ((pp != null) ? pp : "none"));
                            }

                            srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.there.stlmt");
                                // "You can't build a settlement there."
                            sendDenyReply = true;
                        }
                    } else {
                        srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now.stlmt");
                            // "You can't build a settlement now."
                    }

                    break;

                case SOCPlayingPiece.CITY:

                    if (gameState == SOCGame.PLACING_CITY)
                    {
                        if (player.isPotentialCity(coord) && (player.getNumPieces(SOCPlayingPiece.CITY) >= 1))
                        {
                            boolean houseRuleFirstCity = ga.isGameOptionSet("N7C") && ! ga.hasBuiltCity();
                            if (houseRuleFirstCity && ga.isGameOptionSet("N7")
                                && (ga.getRoundCount() < ga.getGameOptionIntValue("N7")))
                            {
                                // If "No 7s for first # rounds" is active, and this isn't its last round, 7s won't
                                // be rolled soon: Don't announce "Starting next turn, dice rolls of 7 may occur"
                                houseRuleFirstCity = false;
                            }

                            final SOCCity ci = new SOCCity(player, coord, null);
                            ga.putPiece(ci);  // changes game state and maybe player

                            srv.gameList.takeMonitorForGame(gaName);
                            srv.messageToGameKeyed(ga, false, "action.built.city", plName);  // "Joe built a city."
                            srv.messageToGameWithMon(gaName, new SOCPutPiece(gaName, pn, SOCPlayingPiece.CITY, coord));
                            if (! ga.pendingMessagesOut.isEmpty())
                                handler.sendGamePendingMessages(ga, false);
                            if (houseRuleFirstCity)
                                srv.messageToGameKeyed(ga, false, "action.built.nextturn.7.houserule");
                                // "Starting next turn, dice rolls of 7 may occur (house rule)."
                            srv.gameList.releaseMonitorForGame(gaName);

                            // Check player and send new game state
                            if (! handler.checkTurn(c, ga))
                                handler.sendTurn(ga, false);  // Announce new state and new current player
                            else
                                handler.sendGameState(ga);
                        } else {
                            D.ebugPrintln("ILLEGAL CITY: 0x" + Integer.toHexString(coord)
                                + ": player " + pn);
                            if (player.isRobot() && D.ebugOn)
                            {
                                D.ebugPrintln(" - pl.isPotentialCity: " + player.isPotentialCity(coord));
                                SOCPlayingPiece pp = ga.getBoard().settlementAtNode(coord);
                                D.ebugPrintln(" - city/settlementAtNode: " + ((pp != null) ? pp : "none"));
                            }

                            srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.there.city");
                                // "You can't build a city there."
                            sendDenyReply = true;
                        }
                    } else {
                        srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now.city");
                            // "You can't build a city now."
                    }

                    break;

                case SOCPlayingPiece.SHIP:

                    if ((gameState == SOCGame.START1B) || (gameState == SOCGame.START2B) || (gameState == SOCGame.START3B)
                        || (gameState == SOCGame.PLACING_SHIP)
                        || (gameState == SOCGame.PLACING_FREE_ROAD1) || (gameState == SOCGame.PLACING_FREE_ROAD2))
                    {
                        // Place it if we can; canPlaceShip checks potentials and pirate ship location
                        if (ga.canPlaceShip(player, coord) && (player.getNumPieces(SOCPlayingPiece.SHIP) >= 1))
                        {
                            final SOCShip sh = new SOCShip(player, coord, null);
                            ga.putPiece(sh);  // Changes game state and (during initial placement) sometimes player

                            srv.gameList.takeMonitorForGame(gaName);
                            srv.messageToGameKeyed(ga, false, "action.built.ship", plName);  // "Joe built a ship."
                            srv.messageToGameWithMon(gaName, new SOCPutPiece(gaName, pn, SOCPlayingPiece.SHIP, coord));
                            if (! ga.pendingMessagesOut.isEmpty())
                                handler.sendGamePendingMessages(ga, false);
                            srv.gameList.releaseMonitorForGame(gaName);

                            // If needed, call sendTurn or send SOCRollDicePrompt
                            handler.sendTurnStateAtInitialPlacement(ga, player, c, gameState);

                            int newState = ga.getGameState();
                            if ((newState == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
                                || (newState == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE))
                            {
                                // gold hex revealed from fog (scenario SC_FOG)
                                handler.sendGameState_sendGoldPickAnnounceText(ga, gaName, c, null);
                            }
                        } else {
                            D.ebugPrintln("ILLEGAL SHIP: 0x" + Integer.toHexString(coord)
                                + ": player " + pn);
                            if (player.isRobot() && D.ebugOn)
                            {
                                D.ebugPrintln(" - pl.isPotentialShip: " + player.isPotentialShip(coord));
                                SOCPlayingPiece pp = ga.getBoard().roadOrShipAtEdge(coord);
                                D.ebugPrintln(" - ship/roadAtEdge: " + ((pp != null) ? pp : "none"));
                            }

                            srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.there.ship");
                                // "You can't build a ship there."
                            sendDenyReply = true;
                        }
                    } else {
                        srv.messageToPlayerKeyed(c, gaName, "action.build.cannot.now.ship");
                            // "You can't build a ship now."
                    }

                    break;

                }

                if (sendDenyReply)
                {
                    if (isBuyAndPut)
                        handler.sendGameState(ga);  // is probably now PLACING_*, was PLAY1 or SPECIAL_BUILDING
                    srv.messageToPlayer(c, new SOCCancelBuildRequest(gaName, mes.getPieceType()));
                    if (player.isRobot())
                    {
                        // Set the "force end turn soon" field
                        ga.lastActionTime = 0L;
                    }
                }
            } else {
                srv.messageToPlayerKeyed(c, gaName, "reply.not.your.turn");  // "It's not your turn."
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught in handlePUTPIECE");
        }
        finally
        {
            ga.releaseMonitor();
        }
    }

    /**
     * Handle the client's "move piece request" message.
     * Currently, ships are the only pieces that can be moved.
     */
    private void handleMOVEPIECE(SOCGame ga, Connection c, final SOCMovePiece mes)
    {
        final String gaName = ga.getName();

        boolean denyRequest = false;
        final int fromEdge = mes.getFromCoord(),
                  toEdge   = mes.getToCoord();
        if ((mes.getPieceType() != SOCPlayingPiece.SHIP)
            || ! handler.checkTurn(c, ga))
        {
            denyRequest = true;
        } else {
            final int pn = ga.getCurrentPlayerNumber();
            SOCShip moveShip = ga.canMoveShip(pn, fromEdge, toEdge);
            if (moveShip == null)
            {
                denyRequest = true;
            } else {
                final int gstate = ga.getGameState();

                ga.moveShip(moveShip, toEdge);

                srv.messageToGame(gaName, new SOCMovePiece
                    (gaName, pn, SOCPlayingPiece.SHIP, fromEdge, toEdge));
                // client will also print "* Joe moved a ship.", no need to send a SOCGameServerText.

                if (! ga.pendingMessagesOut.isEmpty())
                    handler.sendGamePendingMessages(ga, true);

                if (ga.getGameState() == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                {
                    // If ship placement reveals a gold hex in _SC_FOG,
                    // the player gets to pick a free resource.
                    handler.sendGameState(ga, false, false);
                    handler.sendGameState_sendGoldPickAnnounceText(ga, gaName, c, null);
                }
                else if (gstate != ga.getGameState())
                {
                    // announce new state (such as PLACING_INV_ITEM in _SC_FTRI),
                    // or if state is now SOCGame.OVER, announce end of game
                    handler.sendGameState(ga, false, false);
                }
            }
        }

        if (denyRequest)
        {
            D.ebugPrintln("ILLEGAL MOVEPIECE: 0x" + Integer.toHexString(fromEdge) + " -> 0x" + Integer.toHexString(toEdge)
                + ": player " + c.getData());
            srv.messageToPlayerKeyed(c, gaName, "reply.movepiece.cannot.now.ship");  // "You can't move that ship now."
            srv.messageToPlayer(c, new SOCCancelBuildRequest(gaName, SOCPlayingPiece.SHIP));
        }
    }

    /**
     * Handle the client's debug Free Placement putpiece request.
     * @since 1.1.12
     */
    private void handleDEBUGFREEPLACE(final SOCGame ga, final Connection c, final SOCDebugFreePlace mes)
    {
        if (! ga.isDebugFreePlacement())
            return;
        final String gaName = ga.getName();

        final int coord = mes.getCoordinates();
        final SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        if (player == null)
            return;

        boolean didPut = false;
        final int pieceType = mes.getPieceType();

        final boolean initialDeny
            = ga.isInitialPlacement() && ! player.canBuildInitialPieceType(pieceType);

        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:
            if (player.isPotentialRoad(coord) && ! initialDeny)
            {
                ga.putPiece(new SOCRoad(player, coord, null));
                didPut = true;
            }
            break;

        case SOCPlayingPiece.SETTLEMENT:
            if (player.canPlaceSettlement(coord) && ! initialDeny)
            {
                ga.putPiece(new SOCSettlement(player, coord, null));
                didPut = true;
            }
            break;

        case SOCPlayingPiece.CITY:
            if (player.isPotentialCity(coord) && ! initialDeny)
            {
                ga.putPiece(new SOCCity(player, coord, null));
                didPut = true;
            }
            break;

        case SOCPlayingPiece.SHIP:
            if (ga.canPlaceShip(player, coord) && ! initialDeny)
            {
                ga.putPiece(new SOCShip(player, coord, null));
                didPut = true;
            }
            break;

        default:
            srv.messageToPlayer(c, gaName, "* Unknown piece type: " + pieceType);
        }

        if (didPut)
        {
            srv.messageToGame(gaName, new SOCPutPiece
                              (gaName, mes.getPlayerNumber(), pieceType, coord));

            // Check for initial settlement next to gold hex
            if (pieceType == SOCPlayingPiece.SETTLEMENT)
            {
                final int numGoldRes = player.getNeedToPickGoldHexResources();
                if (numGoldRes > 0)
                    srv.messageToPlayer(c, new SOCSimpleRequest
                        (gaName, player.getPlayerNumber(), SOCSimpleRequest.PROMPT_PICK_RESOURCES, numGoldRes));
            }

            if (ga.getGameState() >= SOCGame.OVER)
            {
                // exit debug mode, announce end of game
                handler.processDebugCommand_freePlace(c, gaName, "0");
                handler.sendGameState(ga, false, false);
            }
        } else {
            if (initialDeny)
            {
                final String pieceTypeFirst =
                    ((player.getPieces().size() % 2) == 0)
                    ? "settlement"
                    : "road";
                srv.messageToPlayer(c, gaName, "Place a " + pieceTypeFirst + " before placing that.");
            } else {
                srv.messageToPlayer(c, gaName, "Not a valid location to place that.");
            }
        }
    }


    /// Development Cards ///


    /**
     * handle "buy dev card request" message.
     *<P>
     * Before v2.0.00 this method was {@code handleBUYCARDREQUEST}.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleBUYDEVCARDREQUEST(SOCGame ga, Connection c, final SOCBuyDevCardRequest mes)
    {
        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            SOCPlayer player = ga.getPlayer(c.getData());
            final int pn = player.getPlayerNumber();
            boolean sendDenyReply = false;  // for robots' benefit

            if (handler.checkTurn(c, ga))
            {
                if (((ga.getGameState() == SOCGame.PLAY1) || (ga.getGameState() == SOCGame.SPECIAL_BUILDING))
                    && (ga.couldBuyDevCard(pn)))
                {
                    int card = ga.buyDevCard();
                    final int devCount = ga.getNumDevCards();

                    srv.gameList.takeMonitorForGame(gaName);
                    if (ga.clientVersionLowest >= SOCPlayerElements.MIN_VERSION)
                    {
                        srv.messageToGameWithMon(gaName, new SOCPlayerElements
                            (gaName, pn, SOCPlayerElement.LOSE, SOCDevCard.COST));
                        srv.messageToGameWithMon
                            (gaName, new SOCGameElements(gaName, SOCGameElements.DEV_CARD_COUNT, devCount));
                    } else {
                        srv.messageToGameWithMon(gaName, new SOCPlayerElement
                            (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.ORE, 1));
                        srv.messageToGameWithMon(gaName, new SOCPlayerElement
                            (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1));
                        srv.messageToGameWithMon(gaName, new SOCPlayerElement
                            (gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 1));
                        srv.messageToGameWithMon
                            (gaName, new SOCDevCardCount(gaName, devCount));
                    }
                    srv.gameList.releaseMonitorForGame(gaName);

                    if ((card == SOCDevCardConstants.KNIGHT) && (c.getVersion() < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
                        card = SOCDevCardConstants.KNIGHT_FOR_VERS_1_X;
                    srv.messageToPlayer(c, new SOCDevCardAction(gaName, pn, SOCDevCardAction.DRAW, card));

                    if (ga.clientVersionLowest >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES)
                    {
                        srv.messageToGameExcept(gaName, c, new SOCDevCardAction(gaName, pn, SOCDevCardAction.DRAW, SOCDevCardConstants.UNKNOWN), true);
                    } else {
                        srv.messageToGameForVersionsExcept
                            (ga, -1, SOCDevCardConstants.VERSION_FOR_NEW_TYPES - 1,
                             c, new SOCDevCardAction(gaName, pn, SOCDevCardAction.DRAW, SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X), true);
                        srv.messageToGameForVersionsExcept
                            (ga, SOCDevCardConstants.VERSION_FOR_NEW_TYPES, Integer.MAX_VALUE,
                             c, new SOCDevCardAction(gaName, pn, SOCDevCardAction.DRAW, SOCDevCardConstants.UNKNOWN), true);
                    }

                    final int remain = ga.getNumDevCards();
                    final SOCSimpleAction actmsg = new SOCSimpleAction
                        (gaName, pn, SOCSimpleAction.DEVCARD_BOUGHT, remain, 0);

                    if (ga.clientVersionLowest >= SOCSimpleAction.VERSION_FOR_SIMPLEACTION)
                    {
                        srv.messageToGame(gaName, actmsg);
                    } else {
                        srv.gameList.takeMonitorForGame(gaName);

                        srv.messageToGameForVersions
                            (ga, SOCSimpleAction.VERSION_FOR_SIMPLEACTION, Integer.MAX_VALUE, actmsg, false);

                        // Only pre-1.1.19 clients will see the game text messages. Since they're
                        // older than the i18n work: Skip text key lookups, always use english,
                        // and use SOCGameTextMsg not SOCGameServerText.

                        final String boughtTxt = MessageFormat.format("{0} bought a development card.", player.getName());
                        srv.messageToGameForVersions(ga, -1, SOCSimpleAction.VERSION_FOR_SIMPLEACTION - 1,
                                new SOCGameTextMsg(gaName, SOCGameTextMsg.SERVERNAME, boughtTxt), false);

                        final String remainTxt;
                        switch(remain)
                        {
                        case 0:
                            remainTxt = "There are no more Development cards.";  break;
                        case 1:
                            remainTxt = "There is 1 card left.";  break;
                        default:
                            remainTxt = MessageFormat.format("There are {0,number} cards left.", ga.getNumDevCards());  // I18N OK: for old version compat
                        }
                        srv.messageToGameForVersions(ga, -1, SOCSimpleAction.VERSION_FOR_SIMPLEACTION - 1,
                                new SOCGameTextMsg(gaName, SOCGameTextMsg.SERVERNAME, remainTxt), false);

                        srv.gameList.releaseMonitorForGame(gaName);
                    }

                    handler.sendGameState(ga);
                } else {
                    if (ga.getNumDevCards() == 0)
                        srv.messageToPlayer(c, gaName, "There are no more Development cards.");
                    else
                        srv.messageToPlayer(c, gaName, "You can't buy a development card now.");
                    sendDenyReply = true;
                }
            } else {
                if (ga.maxPlayers <= 4)
                {
                    srv.messageToPlayer(c, gaName, "It's not your turn.");
                } else {
                    // 6-player board: Special Building Phase
                    try
                    {
                        ga.askSpecialBuild(pn, true);
                        srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 1));
                    } catch (IllegalStateException e) {
                        srv.messageToPlayer(c, gaName, "You can't ask to buy a card now.");
                    }
                }
                sendDenyReply = true;
            }

            if (sendDenyReply && ga.getPlayer(pn).isRobot())
            {
                srv.messageToPlayer(c, new SOCCancelBuildRequest(gaName, -2));  // == SOCPossiblePiece.CARD
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "play development card request" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handlePLAYDEVCARDREQUEST(SOCGame ga, Connection c, final SOCPlayDevCardRequest mes)
    {
        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            boolean denyPlayCardNow = false;  // if player can't play right now, send "You can't play a (cardtype) card now."
            String denyTextKey = null;  // if player can't play right now, for a different reason than denyPlayCardNow, send this

            if (handler.checkTurn(c, ga))
            {
                final SOCPlayer player = ga.getPlayer(c.getData());
                final int pn = player.getPlayerNumber();

                int ctype = mes.getDevCard();
                if ((ctype == SOCDevCardConstants.KNIGHT_FOR_VERS_1_X)
                    && (c.getVersion() < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
                    ctype = SOCDevCardConstants.KNIGHT;

                switch (ctype)
                {
                case SOCDevCardConstants.KNIGHT:

                    final boolean isWarshipConvert = ga.isGameOptionSet(SOCGameOption.K_SC_PIRI);

                    if (ga.canPlayKnight(pn))
                    {
                        final int peType = (isWarshipConvert)
                            ? SOCPlayerElement.SCENARIO_WARSHIP_COUNT : SOCPlayerElement.NUMKNIGHTS;

                        ga.playKnight();

                        final String cardplayed = (isWarshipConvert)
                            ? "action.card.soldier.warship"  // "converted a ship to a warship."
                            : "action.card.soldier";         // "played a Soldier card."
                        srv.gameList.takeMonitorForGame(gaName);
                        srv.messageToGameKeyed(ga, false, cardplayed, player.getName());
                        if (ga.clientVersionLowest >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES)
                        {
                            srv.messageToGameWithMon(gaName, new SOCDevCardAction(gaName, pn, SOCDevCardAction.PLAY, SOCDevCardConstants.KNIGHT));
                        } else {
                            D.ebugPrintln("L7870: played soldier; clientVersionLowest = " + ga.clientVersionLowest);  // JM temp
                            srv.messageToGameForVersions
                                (ga, -1, SOCDevCardConstants.VERSION_FOR_NEW_TYPES - 1,
                                 new SOCDevCardAction(gaName, pn, SOCDevCardAction.PLAY, SOCDevCardConstants.KNIGHT_FOR_VERS_1_X), false);
                            srv.messageToGameForVersions
                                (ga, SOCDevCardConstants.VERSION_FOR_NEW_TYPES, Integer.MAX_VALUE,
                                 new SOCDevCardAction(gaName, pn, SOCDevCardAction.PLAY, SOCDevCardConstants.KNIGHT), false);
                        }
                        if (ga.clientVersionLowest >= SOCPlayerElement.VERSION_FOR_CARD_ELEMENTS)
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.PLAYED_DEV_CARD_FLAG, 1));
                        else
                            srv.messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, pn, true));
                        srv.messageToGameWithMon
                            (gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, peType, 1));
                        srv.gameList.releaseMonitorForGame(gaName);
                        if (! isWarshipConvert)
                        {
                            handler.sendGameState(ga);
                        }
                    } else {
                        denyPlayCardNow = true;
                        // "You can't play a " + ((isWarshipConvert) ? "Warship" : "Soldier") + " card now."
                    }

                    break;

                case SOCDevCardConstants.ROADS:

                    if (ga.canPlayRoadBuilding(pn))
                    {
                        ga.playRoadBuilding();

                        srv.gameList.takeMonitorForGame(gaName);
                        srv.messageToGameWithMon(gaName, new SOCDevCardAction(gaName, pn, SOCDevCardAction.PLAY, SOCDevCardConstants.ROADS));
                        if (ga.clientVersionLowest >= SOCPlayerElement.VERSION_FOR_CARD_ELEMENTS)
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.PLAYED_DEV_CARD_FLAG, 1));
                        else
                            srv.messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, pn, true));
                        srv.messageToGameKeyed(ga, false, "action.card.roadbuilding", player.getName());  // "played a Road Building card."
                        srv.gameList.releaseMonitorForGame(gaName);
                        handler.sendGameState(ga);
                        if (ga.getGameState() == SOCGame.PLACING_FREE_ROAD1)
                            srv.messageToPlayerKeyed
                                (c, gaName, (ga.hasSeaBoard) ? "action.card.road.place.2s" : "action.card.road.place.2r");
                            // "You may place 2 roads/ships." or "You may place 2 roads."
                        else
                            srv.messageToPlayerKeyed
                                (c, gaName, (ga.hasSeaBoard) ? "action.card.road.place.1s" : "action.card.road.place.1r");
                            // "You may place your 1 remaining road or ship." or "... place your 1 remaining road."
                    } else {
                        denyPlayCardNow = true;  // "You can't play a Road Building card now."
                    }

                    break;

                case SOCDevCardConstants.DISC:

                    if (ga.canPlayDiscovery(pn))
                    {
                        ga.playDiscovery();

                        srv.gameList.takeMonitorForGame(gaName);
                        srv.messageToGameWithMon(gaName, new SOCDevCardAction(gaName, pn, SOCDevCardAction.PLAY, SOCDevCardConstants.DISC));
                        if (ga.clientVersionLowest >= SOCPlayerElement.VERSION_FOR_CARD_ELEMENTS)
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.PLAYED_DEV_CARD_FLAG, 1));
                        else
                            srv.messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, pn, true));
                        srv.messageToGameKeyed(ga, false, "action.card.discoveryplenty", player.getName());
                            // "played a Year of Plenty card."
                        srv.gameList.releaseMonitorForGame(gaName);
                        handler.sendGameState(ga);
                    } else {
                        denyPlayCardNow = true;  // "You can't play a Year of Plenty card now."
                    }

                    break;

                case SOCDevCardConstants.MONO:

                    if (ga.canPlayMonopoly(pn))
                    {
                        ga.playMonopoly();

                        srv.gameList.takeMonitorForGame(gaName);
                        srv.messageToGameWithMon(gaName, new SOCDevCardAction(gaName, pn, SOCDevCardAction.PLAY, SOCDevCardConstants.MONO));
                        if (ga.clientVersionLowest >= SOCPlayerElement.VERSION_FOR_CARD_ELEMENTS)
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement
                                (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.PLAYED_DEV_CARD_FLAG, 1));
                        else
                            srv.messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, pn, true));
                        srv.messageToGameKeyed(ga, false, "action.card.mono", player.getName());  // "played a Monopoly card."
                        srv.gameList.releaseMonitorForGame(gaName);
                        handler.sendGameState(ga);
                    } else {
                        denyPlayCardNow = true;  // "You can't play a Monopoly card now."
                    }

                    break;

                // VP cards are secretly played when bought.
                // (case SOCDevCardConstants.CAP, MARKET, UNIV, TEMP, CHAPEL):
                // If player clicks "Play Card" the message is handled at the
                // client, in SOCHandPanel.actionPerformed case CARD.
                //  "You secretly played this VP card when you bought it."
                //  break;

                default:
                    denyTextKey = "reply.playdevcard.type.unknown";  // "That card type is unknown."
                    D.ebugPrintln("* srv handlePLAYDEVCARDREQUEST: asked to play unhandled type " + mes.getDevCard());
                    // debug prints dev card type from client, not ctype,
                    // in case ctype was changed here from message value.

                }
            } else {
                denyTextKey = "reply.not.your.turn";  // "It's not your turn."
            }

            if (denyPlayCardNow || (denyTextKey != null))
            {
                final SOCClientData scd = (SOCClientData) c.getAppData();
                if ((scd == null) || ! scd.isRobot)
                {
                    if (denyTextKey != null)
                        srv.messageToPlayerKeyed(c, gaName, denyTextKey);
                    else
                        srv.messageToPlayerKeyedSpecial(c, ga, "reply.playdevcard.cannot.now", mes.getDevCard());
                } else {
                    srv.messageToPlayer(c, new SOCDevCardAction(gaName, -1, SOCDevCardAction.CANNOT_PLAY, mes.getDevCard()));
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "discovery pick" (while playing Discovery/Year of Plenty card) / Gold Hex resource pick message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handlePICKRESOURCES(SOCGame ga, Connection c, final SOCPickResources mes)
    {
        final String gaName = ga.getName();
        final SOCResourceSet rsrcs = mes.getResources();

        ga.takeMonitor();

        final SOCPlayer player = ga.getPlayer(c.getData());
        final int pn;
        if (player != null)
            pn = player.getPlayerNumber();
        else
            pn = -1;  // c's client no longer in the game

        try
        {
            if (player == null)
            {
                // The catch block will print this out semi-nicely
                throw new IllegalArgumentException("player not found in game");
            }

            final int gstate = ga.getGameState();
            if (gstate == SOCGame.WAITING_FOR_DISCOVERY)
            {
                // Message is Discovery/Year of Plenty picks

                if (handler.checkTurn(c, ga))
                {
                    if (ga.canDoDiscoveryAction(rsrcs))
                    {
                        ga.doDiscoveryAction(rsrcs);

                        handler.reportRsrcGainLoss(gaName, rsrcs, false, false, pn, -1, null, null);
                        srv.messageToGameKeyedSpecial(ga, true, "action.card.discov.received", player.getName(), rsrcs);
                            // "{0} received {1,rsrcs} from the bank."
                        handler.sendGameState(ga);
                    } else {
                        srv.messageToPlayerKeyed(c, gaName, "action.card.discov.notlegal");  // "That is not a legal Year of Plenty pick."
                    }
                } else {
                    srv.messageToPlayerKeyed(c, gaName, "reply.not.your.turn");  // "It's not your turn."
                }
            } else {
                // Message is Gold Hex picks

                if (ga.canPickGoldHexResources(pn, rsrcs))
                {
                    final boolean fromInitPlace = ga.isInitialPlacement();
                    final boolean fromPirateFleet = ga.isPickResourceIncludingPirateFleet(pn);

                    final int prevState = ga.pickGoldHexResources(pn, rsrcs);
                    final int newState = ga.getGameState();

                    /**
                     * tell everyone what the player gained
                     */
                    handler.reportRsrcGainGold(ga, player, pn, rsrcs, false, ! fromPirateFleet);

                    /**
                     * send the new state, or end turn if was marked earlier as forced
                     * -- for gold during initial placement, current player might also change.
                     */
                    if ((gstate != SOCGame.PLAY1) || (newState == SOCGame.WAITING_FOR_DISCARDS)
                        || ! ga.isForcingEndTurn())
                    {
                        if (! fromInitPlace)
                        {
                            handler.sendGameState(ga);

                            if (newState == SOCGame.WAITING_FOR_DISCARDS)
                            {
                                // happens only in scenario _SC_PIRI, when 7 is rolled, player wins against pirate fleet
                                // and has picked their won resource, and then someone must discard
                                handler.sendGameState_sendDiscardRequests(ga, gaName);
                            }
                        } else {
                            // send state, and current player if changed

                            switch (newState)
                            {
                            case SOCGame.START1B:
                            case SOCGame.START2B:
                            case SOCGame.START3B:
                                // pl not changed: previously placed settlement, now placing road or ship
                                handler.sendGameState(ga);
                                break;

                            case SOCGame.START1A:
                            case SOCGame.START2A:
                            case SOCGame.START3A:
                            case SOCGame.ROLL_OR_CARD:
                                // Current player probably changed, announce new player if so
                                // with sendTurn and/or SOCRollDicePrompt
                                handler.sendTurnStateAtInitialPlacement(ga, player, c, prevState);
                                break;
                            }
                        }
                    } else {
                        // force-end game turn
                        handler.endGameTurn(ga, player, true);  // locking: already did ga.takeMonitor()
                    }
                } else {
                    srv.messageToPlayer(c, gaName, "You can't pick that many resources.");
                    final int npick = player.getNeedToPickGoldHexResources();
                    if ((npick > 0) && (gstate < SOCGame.OVER))
                        srv.messageToPlayer(c, new SOCSimpleRequest
                            (gaName, pn, SOCSimpleRequest.PROMPT_PICK_RESOURCES, npick));
                    else
                        srv.messageToPlayer(c, new SOCPlayerElement
                            (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES, 0));
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "pick resource type" (monopoly) message.
     *<P>
     * Before v2.0.00 this method was {@code handleMONOPOLYPICK}.
     *
     * @param c     the connection that sent the message
     * @param mes   the message
     * @since 1.0.0
     */
    private void handlePICKRESOURCETYPE(SOCGame ga, Connection c, final SOCPickResourceType mes)
    {
        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            if (handler.checkTurn(c, ga))
            {
                if (ga.canDoMonopolyAction())
                {
                    final int rsrc = mes.getResourceType();
                    final int[] monoPicks = ga.doMonopolyAction(rsrc);
                    final boolean[] isVictim = new boolean[ga.maxPlayers];
                    final int cpn = ga.getCurrentPlayerNumber();
                    final String monoPlayerName = c.getData();
                    int monoTotal = 0;
                    for (int pn = 0; pn < ga.maxPlayers; ++pn)
                    {
                        final int n = monoPicks[pn];
                        if (n > 0)
                        {
                            monoTotal += n;
                            isVictim[pn] = true;
                        }
                    }

                    srv.gameList.takeMonitorForGame(gaName);

                    final SOCSimpleAction actMsg = new SOCSimpleAction
                        (gaName, cpn,
                         SOCSimpleAction.RSRC_TYPE_MONOPOLIZED, monoTotal, rsrc);
                         // Client will print "You monopolized 5 sheep." or "Joe monopolized 5 Sheep."

                    if (ga.clientVersionLowest >= SOCStringManager.VERSION_FOR_I18N)
                    {
                        srv.messageToGameWithMon(gaName, actMsg);
                    } else {
                        srv.messageToGameForVersions
                            (ga, SOCStringManager.VERSION_FOR_I18N, Integer.MAX_VALUE, actMsg, false);

                        // Only pre-2.0.00 clients need this game text message. Since they're older than
                        // the i18n work: Always use english, send SOCGameTextMsg not SOCGameServerText.

                        final String monoTxt = monoPlayerName + " monopolized " +
                            SOCStringManager.getFallbackServerManagerForClient().getSOCResourceCount(rsrc, monoTotal);
                            // "Joe monopolized 5 Sheep."
                            // If acting player's client is old, they'll see that instead of "You monopolized 5 sheep";
                            // that cosmetic change is OK.

                        srv.messageToGameForVersions(ga, -1, SOCStringManager.VERSION_FOR_I18N - 1,
                            new SOCGameTextMsg(gaName, SOCGameTextMsg.SERVERNAME, monoTxt), false);
                    }

                    /**
                     * send each affected player's resource counts for the monopolized resource;
                     * set isNews flag for each victim player's count
                     */
                    for (int pn = 0; pn < ga.maxPlayers; ++pn)
                        if ((pn == cpn) || isVictim[pn])
                            // sending rsrc number works because SOCPlayerElement.CLAY == SOCResourceConstants.CLAY
                            srv.messageToGameWithMon
                                (gaName, new SOCPlayerElement
                                    (gaName, pn, SOCPlayerElement.SET,
                                     rsrc, ga.getPlayer(pn).getResources().getAmount(rsrc), (pn != cpn)));

                    srv.gameList.releaseMonitorForGame(gaName);

                    /**
                     * now that monitor is released, notify the
                     * victim(s) of resource amounts taken.
                     * Skip robot victims, they ignore text messages.
                     */
                    for (int pn = 0; pn < ga.maxPlayers; ++pn)
                    {
                        if (! isVictim[pn])
                            continue;
                        int picked = monoPicks[pn];
                        String viName = ga.getPlayer(pn).getName();
                        Connection viCon = srv.getConnection(viName);
                        if ((viCon != null) && ! ((SOCClientData) viCon.getAppData()).isRobot)
                            srv.messageToPlayerKeyedSpecial
                                (viCon, ga,
                                 ((picked == 1) ? "action.mono.took.your.1" : "action.mono.took.your.n"),
                                 monoPlayerName, picked, rsrc);
                                // "Joe's Monopoly took your 3 sheep."
                    }

                    handler.sendGameState(ga);
                } else {
                    srv.messageToPlayerKeyedSpecial(c, ga, "reply.playdevcard.cannot.now", SOCDevCardConstants.MONO);
                        // "You can't play a Monopoly card now."  Before v2.0.00, was "You can't do a Monopoly pick now."
                }
            } else {
                srv.messageToPlayerKeyed(c, gaName, "reply.not.your.turn");  // "It's not your turn."
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }


    /// Inventory Items and Special Items ///


    /**
     * Special inventory item action (play request) from a player.
     * Ignored unless {@link SOCInventoryItemAction#action mes.action} == {@link SOCInventoryItemAction#PLAY PLAY}.
     * Calls {@link SOCGame#canPlayInventoryItem(int, int)}, {@link SOCGame#playInventoryItem(int)}.
     * If game state changes here, calls {@link SOCGameHandler#sendGameState(SOCGame)} just before returning.
     *
     * @param ga  game with {@code c} as a client player
     * @param c  the connection sending the message
     * @param mes  the message
     */
    private void handleINVENTORYITEMACTION(SOCGame ga, Connection c, final SOCInventoryItemAction mes)
    {
        if (mes.action != SOCInventoryItemAction.PLAY)
            return;

        final String gaName = ga.getName();
        SOCPlayer clientPl = ga.getPlayer(c.getData());
        if (clientPl == null)
            return;

        final int pn = clientPl.getPlayerNumber();

        final int replyCannot = ga.canPlayInventoryItem(pn, mes.itemType);
        if (replyCannot != 0)
        {
            srv.messageToPlayer(c, new SOCInventoryItemAction
                (gaName, -1, SOCInventoryItemAction.CANNOT_PLAY, mes.itemType, replyCannot));
            return;
        }

        final int oldGameState = ga.getGameState();

        final SOCInventoryItem item = ga.playInventoryItem(mes.itemType);  // <--- Play the item ---

        if (item == null)
        {
            // Wasn't able to play.  Assume canPlay was recently called and returned OK; the most
            // volatile of its conditions is player's inventory, so assume that's what changed.
            srv.messageToPlayer(c, new SOCInventoryItemAction
                (gaName, -1, SOCInventoryItemAction.CANNOT_PLAY, mes.itemType, 1));  // 1 == item not in inventory
            return;
        }

        // Item played.  Announce play and removal (or keep) from player's inventory.
        // Announce game state if changed.
        srv.messageToGame(gaName, new SOCInventoryItemAction
            (gaName, pn, SOCInventoryItemAction.PLAYED, item.itype, item.isKept(), item.isVPItem(), item.canCancelPlay));

        final int gstate = ga.getGameState();
        if (gstate != oldGameState)
            handler.sendGameState(ga);
    }

    /**
     * Handle Special Item requests from a player.
     * Calls {@link SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)}
     * or {@link SOCSpecialItem#playerSetItem(String, SOCGame, SOCPlayer, int, int, boolean)}
     * which provide scenario-specific responses or decline the request.
     * @param c  the connection that sent the message
     * @param mes  the message
     */
    private void handleSETSPECIALITEM(SOCGame ga, Connection c, final SOCSetSpecialItem mes)
    {
        final String gaName = ga.getName();
        final SOCPlayer pl = ga.getPlayer(c.getData());
        final String typeKey = mes.typeKey;
        final int op = mes.op, gi = mes.gameItemIndex, pi = mes.playerItemIndex;
        final int pn = (pl != null) ? pl.getPlayerNumber() : -1;  // don't trust mes.playerNumber
        boolean sendDenyReply = false;

        try
        {
            SOCSpecialItem itm = null;
            final boolean paidCost;  // if true, itm's cost was paid by player to PICK or SET or CLEAR

            ga.takeMonitor();
            if ((pl == null) || (op < SOCSetSpecialItem.OP_SET) || (op > SOCSetSpecialItem.OP_PICK))
            {
                sendDenyReply = true;
                paidCost = false;
            } else {
                final int prevState = ga.getGameState();

                if (op == SOCSetSpecialItem.OP_PICK)
                {
                    int pickCoord = -1, pickLevel = 0;  // field values to send in reply/announcement
                    String pickSV = null;  // sv field value to send

                    // When game index and player index are both given,
                    // compare items before and after PICK in case they change
                    final SOCSpecialItem gBefore, pBefore;
                    if ((gi != -1) && (pi != -1))
                    {
                        gBefore = ga.getSpecialItem(typeKey, gi);
                        pBefore = pl.getSpecialItem(typeKey, pi);
                    } else {
                        gBefore = null;  pBefore = null;
                    }

                    // Before pick, get item as per playerPickItem javadoc for cost, coord, level,
                    // in case it's cleared by the pick. If not cleared, will get it again afterwards.
                    itm = ga.getSpecialItem(typeKey, gi, pi, pn);
                    if (itm != null)
                    {
                        pickCoord = itm.getCoordinates();
                        pickLevel = itm.getLevel();
                        pickSV = itm.getStringValue();
                    }

                    // perform the PICK in game
                    paidCost = SOCSpecialItem.playerPickItem(typeKey, ga, pl, gi, pi);

                    // if cost paid, send resource-loss first
                    if (paidCost && (itm != null))
                        handler.reportRsrcGainLoss(gaName, itm.getCost(), true, false, pn, -1, null, null);
                        // TODO i18n-neutral rsrc text to report cost paid?  or, encapsulate that into reportRsrcGainLoss

                    // Next, send SET/CLEAR before sending PICK announcement

                    // For now, this send logic handles everything we need it to do.
                    // Depending on usage of PICK messages in future scenarios,
                    // we might need more info returned from playerPickItem then.

                    if ((gi == -1) || (pi == -1))
                    {
                        // request didn't specify both gi and pi: only 1 SET/CLEAR message to send

                        final SOCSpecialItem itmAfter = ga.getSpecialItem(typeKey, gi, pi, pn);
                        final SOCSetSpecialItem msg;
                        if (itmAfter != null)
                        {
                            msg = new SOCSetSpecialItem(ga, SOCSetSpecialItem.OP_SET, typeKey, gi, pi, itmAfter);

                            pickCoord = itmAfter.getCoordinates();
                            pickLevel = itmAfter.getLevel();
                            pickSV = itmAfter.getStringValue();
                        } else {
                            msg = new SOCSetSpecialItem
                                (gaName, SOCSetSpecialItem.OP_CLEAR, typeKey, gi, pi, pn);
                        }
                        srv.messageToGame(gaName, msg);
                    } else {
                        // request specified both gi and pi: might need to send 1 SET/CLEAR message if shared,
                        // or 2 messages if not the same object for both

                        final SOCSpecialItem gAfter, pAfter;
                        gAfter = ga.getSpecialItem(typeKey, gi);
                        pAfter = pl.getSpecialItem(typeKey, pi);

                        if (gAfter == pAfter)
                        {
                            final SOCSetSpecialItem msg;
                            if (gAfter != null)
                            {
                                msg = new SOCSetSpecialItem(ga, SOCSetSpecialItem.OP_SET, typeKey, gi, pi, gAfter);

                                pickCoord = gAfter.getCoordinates();
                                pickLevel = gAfter.getLevel();
                                pickSV = gAfter.getStringValue();
                            } else {
                                msg = new SOCSetSpecialItem
                                    (gaName, SOCSetSpecialItem.OP_CLEAR, typeKey, gi, pi, pn);
                            }
                            srv.messageToGame(gaName, msg);
                        } else {
                            // gi and pi don't share the same object; might need to send 2 messages out if both changed.

                            boolean hasgAfterCoordLevel = false;

                            if (gAfter == null)
                            {
                                if (gBefore != null)
                                    srv.messageToGame(gaName, new SOCSetSpecialItem
                                        (gaName, SOCSetSpecialItem.OP_CLEAR, typeKey, gi, -1, -1));
                            } else {
                                srv.messageToGame(gaName, new SOCSetSpecialItem
                                    (ga, SOCSetSpecialItem.OP_SET, typeKey, gi, -1, gAfter));

                                pickCoord = gAfter.getCoordinates();
                                pickLevel = gAfter.getLevel();
                                pickSV = gAfter.getStringValue();
                                hasgAfterCoordLevel = true;
                            }

                            if (pAfter == null)
                            {
                                if (pBefore != null)
                                    srv.messageToGame(gaName, new SOCSetSpecialItem
                                        (gaName, SOCSetSpecialItem.OP_CLEAR, typeKey, -1, pi, pn));
                            } else {
                                srv.messageToGame(gaName, new SOCSetSpecialItem
                                    (ga, SOCSetSpecialItem.OP_SET, typeKey, -1, pi, pAfter));
                                if (! hasgAfterCoordLevel)
                                {
                                    pickCoord = pAfter.getCoordinates();
                                    pickLevel = pAfter.getLevel();
                                    pickSV = pAfter.getStringValue();
                                }
                            }
                         }
                    }

                    srv.messageToGame(gaName, new SOCSetSpecialItem
                            (gaName, SOCSetSpecialItem.OP_PICK, typeKey, gi, pi, pn, pickCoord, pickLevel, pickSV));

                } else {
                    // OP_SET or OP_CLEAR

                    if (op == SOCSetSpecialItem.OP_CLEAR)
                        // get item before CLEAR
                        itm = ga.getSpecialItem(typeKey, gi, pi, pn);

                    paidCost = SOCSpecialItem.playerSetItem
                        (typeKey, ga, pl, gi, pi, (op == SOCSetSpecialItem.OP_SET));

                    // if cost paid, send resource-loss first
                    if (paidCost && (itm != null))
                        handler.reportRsrcGainLoss(gaName, itm.getCost(), true, false, pn, -1, null, null);
                        // TODO i18n-neutral rsrc text to report cost paid?  or, encapsulate that into reportRsrcGainLoss

                    // get item after SET, in case it's changed
                    if (op != SOCSetSpecialItem.OP_CLEAR)
                        itm = ga.getSpecialItem(typeKey, gi, pi, pn);

                    if ((op == SOCSetSpecialItem.OP_CLEAR) || (itm == null))
                        srv.messageToGame(gaName, new SOCSetSpecialItem
                            (gaName, SOCSetSpecialItem.OP_CLEAR, typeKey, gi, pi, pn));
                    else
                        srv.messageToGame(gaName, new SOCSetSpecialItem(ga, op, typeKey, gi, pi, itm));
                }

                // check game state, check for winner
                final int gstate = ga.getGameState();
                if (gstate != prevState)
                    handler.sendGameState(ga);  // might be OVER, if player won
            }
        }
        catch (IllegalStateException e)
        {
            sendDenyReply = true;
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }
        finally
        {
            ga.releaseMonitor();
        }

        if (sendDenyReply)
            c.put(new SOCSetSpecialItem
                (gaName, SOCSetSpecialItem.OP_DECLINE, typeKey, gi, pi, mes.playerNumber).toCmd());
    }

}
