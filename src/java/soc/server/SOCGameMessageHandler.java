/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2016 Alessandro D'Ottavio
 * Some contents were formerly part of SOCGameHandler.java;
 * portions of this file Copyright (C) Jeremy D Monin <jeremy@nand.net> and others
 * (details to be added soon from project source history).
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

import java.util.ArrayList;

import soc.debug.D;
import soc.game.SOCBoardLarge;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCInventoryItem;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;
import soc.game.SOCVillage;
import soc.message.*;
import soc.server.genericServer.StringConnection;

/**
 * Game message handler for the {@link SOCGameHandler} game type.
 * The purpose of this class is to dispatch the messages received from the
 * {@link soc.server.genericServer.InboundMessageQueue} related to specific games
 * (which implement {@link SOCMessageForGame}). All other messages are handled by
 * {@link SOCServerMessageHandler} instead.
 *<P>
 * when the {@link SOCGameMessageHandler} is solicited to process the message, it identify the exact type of message
 * and call the correct method
 * of the {@link SOCGameHandler} that instead is responsible to execute the business
 *
 * <UL>
 * <LI> See the method {@link #dispatch(SOCGameHandler, SOCGame, SOCMessageForGame, StringConnection)} to get more
 *      details of this class logic
 * <LI> See {@link SOCGameHandler} for details of the business logic for each message.
 * </UL>
 *<P>
 * Before v2.0.00, this class was {@link SOCServer}.processCommand(String, StringConnection) and related
 * handler methods, all part of {@link SOCServer}. So, some may have {@code @since} javadoc labels with
 * versions older than 2.0.00.
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
     * Dispatch any event that is coming from a client player for a specific game.
     *<P>
     * Some game messages (such as player sits down, or board reset voting) are handled the same for all game types.
     * These are handled at {@link SOCServer}; they should be ignored here and not appear in your switch statement.
     *<P>
     * this method is called from {@link SOCMessageDispatcher#dispatch(String, StringConnection)} when the message is
     * recognized as a command or event for a game.
     * <P>
     * Caller of this method will catch any thrown Exceptions.
     *
     * @param game  Game in which client {@code connection} is sending {@code message}.
     *     Never null; from {@link SOCMessageForGame#getGame()}.
     * @param message  Message from client {@code connection}. Never null.
     * @param connection  Connection to the Client sending {@code message}. Never null.
     * @return true if processed, false if ignored or unknown message type
     */
    public boolean dispatch
        (SOCGame game, SOCMessageForGame message, StringConnection connection)
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
            handler.handlePUTPIECE(game, connection, (SOCPutPiece) message);

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
            handler.handleMOVEROBBER(game, connection, (SOCMoveRobber) message);

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
            handler.handleENDTURN(game, connection, (SOCEndTurn) message);

            //ga = (SOCGame)gamesData.get(((SOCEndTurn)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCEndTurn)mes).getGame());
            break;

        case SOCMessage.CHOOSEPLAYER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleCHOOSEPLAYER(game, connection, (SOCChoosePlayer) message);

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

        case SOCMessage.BUYCARDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleBUYCARDREQUEST(game, connection, (SOCBuyCardRequest) message);

            //ga = (SOCGame)gamesData.get(((SOCBuyCardRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCBuyCardRequest)mes).getGame());
            break;

        case SOCMessage.PLAYDEVCARDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handlePLAYDEVCARDREQUEST(game, connection, (SOCPlayDevCardRequest) message);

            //ga = (SOCGame)gamesData.get(((SOCPlayDevCardRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCPlayDevCardRequest)mes).getGame());
            break;

        case SOCMessage.DISCOVERYPICK:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleDISCOVERYPICK(game, connection, (SOCDiscoveryPick) message);

            //ga = (SOCGame)gamesData.get(((SOCDiscoveryPick)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCDiscoveryPick)mes).getGame());
            break;

        case SOCMessage.MONOPOLYPICK:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleMONOPOLYPICK(game, connection, (SOCMonopolyPick) message);

            //ga = (SOCGame)gamesData.get(((SOCMonopolyPick)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCMonopolyPick)mes).getGame());
            break;

        /**
         * debug piece Free Placement (as of 20110104 (v 1.1.12))
         */
        case SOCMessage.DEBUGFREEPLACE:
            handler.handleDEBUGFREEPLACE(game, connection, (SOCDebugFreePlace) message);
            break;

        /**
         * Generic simple request from a player.
         * Added 2013-02-17 for v1.1.18.
         */
        case SOCMessage.SIMPLEREQUEST:
            handler.handleSIMPLEREQUEST(game, connection, (SOCSimpleRequest) message);
            break;

        /**
         * Special inventory item action (play request) from a player.
         * Added 2013-11-28 for v2.0.00.
         */
        case SOCMessage.INVENTORYITEMACTION:
            handler.handleINVENTORYITEMACTION(game, connection, (SOCInventoryItemAction) message);
            break;

        /**
         * Asking to move a previous piece (a ship) somewhere else on the board.
         * Added 2011-12-04 for v2.0.00.
         */
        case SOCMessage.MOVEPIECEREQUEST:
            handler.handleMOVEPIECEREQUEST(game, connection, (SOCMovePieceRequest) message);
            break;

        /**
         * Picking resources to gain from a Gold Hex.
         * Added 2012-01-12 for v2.0.00.
         */
        case SOCMessage.PICKRESOURCES:
            handlePICKRESOURCES(game, connection, (SOCPickResources) message);
            break;

        /**
         * Special Item requests.
         * Added 2014-05-17 for v2.0.00.
         */
        case SOCMessage.SETSPECIALITEM:
            handler.handleSETSPECIALITEM(game, connection, (SOCSetSpecialItem) message);
            break;

        /**
         * Ignore all other message types, unknown message types.
         */
        default:
            return false;

        }  // switch (mes.getType)

        return true;  // Message was handled
    }


    /// Roll dice and pick resources ///


    /**
     * handle "roll dice" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    void handleROLLDICE(SOCGame ga, StringConnection c, final SOCRollDice mes)
    {
        final String gn = ga.getName();

        ga.takeMonitor();

        try
        {
            final String plName = (String) c.getData();
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
                            final StringConnection vCon = srv.getConnection(vicName);
                            final int pn = vic.getPlayerNumber();
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
                                handler.reportRsrcGainLoss(gn, loot, true, pn, -1, null, vCon);
                                srv.messageToPlayerKeyedSpecial
                                    (vCon, ga, "action.rolled.sc_piri.you.lost.rsrcs.to.fleet", loot, strength);
                                    // "You lost {0,rsrcs} to the pirate fleet (strength {1,number})."

                                /**
                                 * tell everyone else that the player lost unknown resources
                                 */
                                srv.messageToGameExcept(gn, vCon, new SOCPlayerElement
                                    (gn, pn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, lootTotal), true);
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
                    boolean[] plGained = new boolean[SOCGame.MAXPLAYERS];  // send total rsrcs only to players who gain

                    /**
                     * Clients v2.0.00 and newer get an i18n-neutral SOCDiceResultResources message.
                     * Older clients get a string such as "Joe gets 3 sheep. Mike gets 1 clay."
                     */
                    String rollRsrcOldCli = null;
                    SOCDiceResultResources rollRsrcNewCli = null;

                    if (ga.clientVersionHighest >= SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES)
                    {
                        // build a SOCDiceResultResources message
                        ArrayList<Integer> pnum = null;
                        ArrayList<SOCResourceSet> rsrc = null;

                        for (int i = 0; i < ga.maxPlayers; i++)
                        {
                            if (ga.isSeatVacant(i))
                                continue;

                            final SOCPlayer pli = ga.getPlayer(i);
                            final SOCResourceSet rs = pli.getRolledResources();
                            if (rs.getKnownTotal() == 0)
                                continue;

                            plGained[i] = true;
                            if (noPlayersGained)
                            {
                                noPlayersGained = false;
                                pnum = new ArrayList<Integer>();
                                rsrc = new ArrayList<SOCResourceSet>();
                            }
                            pnum.add(Integer.valueOf(i));
                            rsrc.add(rs);
                        }

                        if (! noPlayersGained)
                            rollRsrcNewCli = new SOCDiceResultResources(gn, pnum, rsrc);
                    }

                    if (ga.clientVersionLowest < SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES)
                    {
                        // Build a string
                    StringBuffer gainsText = new StringBuffer();

                    noPlayersGained = true;  // for string spacing; might be false due to loop for new clients in game
                    for (int i = 0; i < ga.maxPlayers; i++)
                    {
                        if (! ga.isSeatVacant(i))
                        {
                            SOCPlayer pli = ga.getPlayer(i);
                            SOCResourceSet rsrcs = pli.getRolledResources();

                            if (rsrcs.getKnownTotal() != 0)
                            {
                                plGained[i] = true;
                                if (noPlayersGained)
                                    noPlayersGained = false;
                                else
                                    gainsText.append(" ");

                                gainsText.append
                                    (c.getLocalizedSpecial(ga, "_nolocaliz.roll.gets.resources", pli.getName(), rsrcs));
                                    // "{0} gets {1,rsrcs}."
                                    // get it from any connection's StringManager, because that string is never localized

                                // Announce SOCPlayerElement.GAIN messages
                                handler.reportRsrcGainLoss(gn, rsrcs, false, i, -1, null, null);
                            }

                        }  // if (! ga.isSeatVacant(i))
                    }  // for (i)

                    if (! noPlayersGained)
                        rollRsrcOldCli = gainsText.toString();

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
                        if (rollRsrcOldCli == null)
                            srv.messageToGame(gn, rollRsrcNewCli);
                        else if (rollRsrcNewCli == null)
                            srv.messageToGame(gn, rollRsrcOldCli);
                        else
                        {
                            // neither is null: we have old and new clients
                            srv.messageToGameForVersions(ga, 0, (SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES - 1),
                                new SOCGameTextMsg(gn, SOCGameTextMsg.SERVERNAME, rollRsrcOldCli), true);
                            srv.messageToGameForVersions(ga, SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES, Integer.MAX_VALUE,
                                rollRsrcNewCli, true);
                        }

                        //
                        //  Send gaining players all their resource info for accuracy
                        //
                        for (int pn = 0; pn < ga.maxPlayers; ++pn)
                        {
                            if (! plGained[pn])
                                continue;  // skip if player didn't gain; before v2.0.00, each player in game got these

                            final SOCPlayer pli = ga.getPlayer(pn);
                            StringConnection playerCon = srv.getConnection(pli.getName());
                            if (playerCon == null)
                                continue;

                            // CLAY, ORE, SHEEP, WHEAT, WOOD
                            final SOCResourceSet resources = pli.getResources();
                            for (int res = SOCPlayerElement.CLAY; res <= SOCPlayerElement.WOOD; ++res)
                                srv.messageToPlayer(playerCon, new SOCPlayerElement(gn, pn, SOCPlayerElement.SET, res, resources.getAmount(res)));
                            srv.messageToGame(gn, new SOCResourceCount(gn, pn, resources.getTotal()));

                            // we'll send gold picks text, PLAYERELEMENT, and PICKRESOURCESREQUEST after the per-player loop
                        }
                    }

                    if (roll.cloth != null)
                    {
                        // Send village cloth trade distribution

                        final int coord = roll.cloth[1];
                        final SOCBoardLarge board = (SOCBoardLarge) (ga.getBoard());
                        SOCVillage vi = board.getVillageAtNode(coord);
                        if (vi != null)
                            srv.messageToGame(gn, new SOCPieceValue(gn, coord, vi.getCloth(), 0));

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
                        // gold picks text, PLAYERELEMENT, and PICKRESOURCESREQUESTs
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
                        for (int i = 0; i < ga.maxPlayers; i++)
                        {
                            final SOCPlayer ipl = ga.getPlayer(i);
                            if (( ! ga.isSeatVacant(i)) && ipl.getNeedToDiscard())
                            {
                                // Request to discard half (round down)
                                StringConnection con = srv.getConnection(ipl.getName());
                                if (con != null)
                                    con.put(SOCDiscardRequest.toCmd(gn, ipl.getResources().getTotal() / 2));
                            }
                        }
                    }
                    else if (ga.getGameState() == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                    {
                        // Used in _SC_PIRI, when 7 is rolled and a player wins against the pirate fleet
                        for (int i = 0; i < ga.maxPlayers; ++i)
                        {
                            final SOCPlayer ipl = ga.getPlayer(i);
                            final int numPick = ipl.getNeedToPickGoldHexResources();
                            if (( ! ga.isSeatVacant(i)) && (numPick > 0))
                            {
                                StringConnection con = srv.getConnection(ipl.getName());
                                if (con != null)
                                {
                                    srv.messageToGame(gn, new SOCPlayerElement
                                        (gn, i, SOCPlayerElement.SET, SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES, numPick));
                                    con.put(SOCPickResourcesRequest.toCmd(gn, numPick));
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
    void handleDISCARD(SOCGame ga, StringConnection c, final SOCDiscard mes)
    {
        final String gn = ga.getName();
        final SOCPlayer player = ga.getPlayer((String) c.getData());
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

            if (ga.canDiscard(pn, mes.getResources()))
            {
                ga.discard(pn, mes.getResources());  // discard, change gameState

                // Same resource-loss messages are sent in handleROLLDICE after a pirate fleet attack (_SC_PIRI).

                /**
                 * tell the player client that the player discarded the resources
                 */
                handler.reportRsrcGainLoss(gn, mes.getResources(), true, pn, -1, null, c);

                /**
                 * tell everyone else that the player discarded unknown resources
                 */
                srv.messageToGameExcept(gn, c, new SOCPlayerElement(gn, pn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, mes.getResources().getTotal()), true);
                srv.messageToGameKeyed(ga, true, "action.discarded", (String) c.getData(), mes.getResources().getTotal());
                    // "{0} discarded {1} resources."

                /**
                 * send the new state, or end turn if was marked earlier as forced
                 */
                if ((ga.getGameState() != SOCGame.PLAY1) || ! ga.isForcingEndTurn())
                {
                    handler.sendGameState(ga);
                        // if state is WAITING_FOR_ROB_CHOOSE_PLAYER (_SC_PIRI), also sends CHOOSEPLAYERREQUEST
                } else {
                    handler.endGameTurn(ga, player, true);  // already did ga.takeMonitor()
                }
            }
            else
            {
                /**
                 * (TODO) there could be a better feedback message here
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

    /**
     * Handle "pick resources" message (gold hex).
     * Game state {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE},
     * or rarely {@link SOCGame#STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}.
     * Also used with <tt>_SC_PIRI</tt> after winning a pirate fleet battle at dice roll.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     */
    final void handlePICKRESOURCES(SOCGame ga, StringConnection c, final SOCPickResources mes)
    {
        final String gn = ga.getName();
        final SOCPlayer player = ga.getPlayer((String) c.getData());
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

            int gstate = ga.getGameState();
            final SOCResourceSet rsrcs = mes.getResources();
            if (ga.canPickGoldHexResources(pn, rsrcs))
            {
                final boolean fromInitPlace = ga.isInitialPlacement();
                final boolean fromPirateFleet = ga.isPickResourceIncludingPirateFleet(pn);

                ga.pickGoldHexResources(pn, rsrcs);
                gstate = ga.getGameState();

                /**
                 * tell everyone what the player gained
                 */
                handler.reportRsrcGainGold(ga, player, pn, rsrcs, ! fromPirateFleet);

                /**
                 * send the new state, or end turn if was marked earlier as forced
                 * -- for gold during initial placement, current player might also change.
                 */
                if ((gstate != SOCGame.PLAY1) || ! ga.isForcingEndTurn())
                {
                    if (! fromInitPlace)
                    {
                        handler.sendGameState(ga);

                        if (gstate == SOCGame.WAITING_FOR_DISCARDS)
                        {
                            // happens only in scenario _SC_PIRI, when 7 is rolled, player wins against pirate fleet
                            // and has picked their won resource, and then someone must discard
                            for (int i = 0; i < ga.maxPlayers; ++i)
                            {
                                SOCPlayer pl = ga.getPlayer(i);
                                if (( ! ga.isSeatVacant(i) ) && pl.getNeedToDiscard())
                                {
                                    // Request to discard half (round down)
                                    StringConnection con = srv.getConnection(pl.getName());
                                    if (con != null)
                                        con.put(SOCDiscardRequest.toCmd(gn, pl.getResources().getTotal() / 2));
                                }
                            }
                        }
                    } else {
                        // send state, and current player if changed

                        switch (gstate)
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
                            // Player probably changed, announce new player if so
                            handler.sendGameState(ga, false);
                            if (! handler.checkTurn(c, ga))
                                handler.sendTurn(ga, true);
                            break;

                        case SOCGame.PLAY:
                            // The last initial road was placed
                            final boolean toldRoll = handler.sendGameState(ga, false);
                            if (! handler.checkTurn(c, ga))
                                // Announce new player (after START3A)
                                handler.sendTurn(ga, true);
                            else if (toldRoll)
                                // When play starts, or after placing 2nd free road,
                                // announce even though player unchanged,
                                // to trigger auto-roll for the player.
                                srv.messageToGame(gn, new SOCRollDicePrompt(gn, ga.getCurrentPlayerNumber()));
                            break;
                        }
                    }
                } else {
                    // force-end game turn
                    handler.endGameTurn(ga, player, true);  // locking: already did ga.takeMonitor()
                }
            }
            else
            {
                srv.messageToPlayer(c, gn, "You can't pick that many resources.");
                final int npick = player.getNeedToPickGoldHexResources();
                if ((npick > 0) && (gstate < SOCGame.OVER))
                    srv.messageToPlayer(c, new SOCPickResourcesRequest(gn, npick));
                else
                    srv.messageToPlayer(c, new SOCPlayerElement
                        (gn, pn, SOCPlayerElement.SET, SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES, 0));
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }


    /// Player trades and bank trades ///


    /**
     * handle "make offer" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    void handleMAKEOFFER(SOCGame ga, StringConnection c, final SOCMakeOffer mes)
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
            SOCPlayer player = ga.getPlayer((String) c.getData());

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

                    srv.messageToGameKeyedSpecial(ga, true, "trade.offered.rsrcs.for",
                        player.getName(), offGive, offGet);
                        // "{0} made a trade offer to give {1,rsrcs} for {2,rsrcs}."
                }

                SOCMakeOffer makeOfferMessage = new SOCMakeOffer(gaName, remadeOffer);
                srv.messageToGame(gaName, makeOfferMessage);

                srv.recordGameEvent(gaName, makeOfferMessage.toCmd());

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
    void handleCLEAROFFER(SOCGame ga, StringConnection c, final SOCClearOffer mes)
    {
        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            ga.getPlayer((String) c.getData()).setCurrentOffer(null);
            srv.messageToGame(gaName, new SOCClearOffer(gaName, ga.getPlayer((String) c.getData()).getPlayerNumber()));
            srv.recordGameEvent(mes.getGame(), mes.toCmd());

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
    void handleREJECTOFFER(SOCGame ga, StringConnection c, final SOCRejectOffer mes)
    {
        SOCPlayer player = ga.getPlayer((String) c.getData());
        if (player == null)
            return;

        final String gaName = ga.getName();
        SOCRejectOffer rejectMessage = new SOCRejectOffer(gaName, player.getPlayerNumber());
        srv.messageToGame(gaName, rejectMessage);

        srv.recordGameEvent(gaName, rejectMessage.toCmd());
    }

    /**
     * handle "accept offer" message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    void handleACCEPTOFFER(SOCGame ga, StringConnection c, final SOCAcceptOffer mes)
    {
        ga.takeMonitor();

        try
        {
            SOCPlayer player = ga.getPlayer((String) c.getData());

            if (player != null)
            {
                final int acceptingNumber = player.getPlayerNumber();
                final int offeringNumber = mes.getOfferingNumber();
                final String gaName = ga.getName();

                if (ga.canMakeTrade(offeringNumber, acceptingNumber))
                {
                    ga.makeTrade(offeringNumber, acceptingNumber);
                    handler.reportTrade(ga, offeringNumber, acceptingNumber);

                    srv.recordGameEvent(mes.getGame(), mes.toCmd());

                    /**
                     * clear all offers
                     */
                    for (int i = 0; i < ga.maxPlayers; i++)
                    {
                        ga.getPlayer(i).setCurrentOffer(null);
                    }
                    srv.gameList.takeMonitorForGame(gaName);
                    if (ga.clientVersionLowest >= SOCClearOffer.VERSION_FOR_CLEAR_ALL)
                    {
                        srv.messageToGameWithMon(gaName, new SOCClearOffer(gaName, -1));
                    } else {
                        for (int i = 0; i < ga.maxPlayers; i++)
                            srv.messageToGameWithMon(gaName, new SOCClearOffer(gaName, i));
                    }
                    srv.gameList.releaseMonitorForGame(gaName);

                    /**
                     * send a message to the bots that the offer was accepted
                     */
                    srv.messageToGame(gaName, mes);
                }
                else
                {
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
    void handleBANKTRADE(SOCGame ga, StringConnection c, final SOCBankTrade mes)
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

                    final int cpn = ga.getCurrentPlayerNumber();
                    final SOCPlayer cpl = ga.getPlayer(cpn);
                    if (cpl.isRobot())
                        c.put(SOCSimpleAction.toCmd(gaName, cpn, SOCSimpleAction.TRADE_SUCCESSFUL, 0, 0));
                }
                else
                {
                    srv.messageToPlayer(c, gaName, "You can't make that trade.");
                    SOCClientData scd = (SOCClientData) c.getAppData();
                    if ((scd != null) && scd.isRobot)
                        D.ebugPrintln("ILLEGAL BANK TRADE: " + c.getData()
                          + ": give " + give + ", get " + get);
                }
            }
            else
            {
                srv.messageToPlayer(c, gaName, "It's not your turn.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }


    /// Building and placement ///


    /**
     * handle "build request" message.
     * If client is current player, they want to buy a {@link SOCPlayingPiece}.
     * Otherwise, if 6-player board, they want to build during the
     * {@link SOCGame#SPECIAL_BUILDING Special Building Phase}.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleBUILDREQUEST(SOCGame ga, StringConnection c, final SOCBuildRequest mes)
    {
        final String gaName = ga.getName();
        ga.takeMonitor();

        try
        {
            final boolean isCurrent = handler.checkTurn(c, ga);
            SOCPlayer player = ga.getPlayer((String) c.getData());
            final int pn = player.getPlayerNumber();
            final int pieceType = mes.getPieceType();
            boolean sendDenyReply = false;  // for robots' benefit

            if (isCurrent)
            {
                if ((ga.getGameState() == SOCGame.PLAY1) || (ga.getGameState() == SOCGame.SPECIAL_BUILDING))
                {
                    switch (pieceType)
                    {
                    case SOCPlayingPiece.ROAD:

                        if (ga.couldBuildRoad(pn))
                        {
                            ga.buyRoad(pn);
                            srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.CLAY, 1));
                            srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                            handler.sendGameState(ga);
                        }
                        else
                        {
                            srv.messageToPlayer(c, gaName, "You can't build a road.");
                            sendDenyReply = true;
                        }

                        break;

                    case SOCPlayingPiece.SETTLEMENT:

                        if (ga.couldBuildSettlement(pn))
                        {
                            ga.buySettlement(pn);
                            srv.gameList.takeMonitorForGame(gaName);
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.CLAY, 1));
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1));
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 1));
                            srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                            srv.gameList.releaseMonitorForGame(gaName);
                            handler.sendGameState(ga);
                        }
                        else
                        {
                            srv.messageToPlayer(c, gaName, "You can't build a settlement.");
                            sendDenyReply = true;
                        }

                        break;

                    case SOCPlayingPiece.CITY:

                        if (ga.couldBuildCity(pn))
                        {
                            ga.buyCity(pn);
                            srv.messageToGame(ga.getName(), new SOCPlayerElement(ga.getName(), pn, SOCPlayerElement.LOSE, SOCPlayerElement.ORE, 3));
                            srv.messageToGame(ga.getName(), new SOCPlayerElement(ga.getName(), pn, SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 2));
                            handler.sendGameState(ga);
                        }
                        else
                        {
                            srv.messageToPlayer(c, gaName, "You can't build a city.");
                            sendDenyReply = true;
                        }

                        break;

                    case SOCPlayingPiece.SHIP:

                        if (ga.couldBuildShip(pn))
                        {
                            ga.buyShip(pn);
                            srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1));
                            srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                            handler.sendGameState(ga);
                        }
                        else
                        {
                            srv.messageToPlayer(c, gaName, "You can't build a ship.");
                            sendDenyReply = true;
                        }

                        break;
                    }
                }
                else if (pieceType == -1)
                {
                    // 6-player board: Special Building Phase
                    // during start of own turn
                    try
                    {
                        ga.askSpecialBuild(pn, true);
                        srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 1));
                        handler.endGameTurn(ga, player, true);  // triggers start of SBP
                    } catch (IllegalStateException e) {
                        srv.messageToPlayer(c, gaName, "You can't ask to build now.");
                        sendDenyReply = true;
                    }
                }
                else
                {
                    srv.messageToPlayer(c, gaName, "You can't build now.");
                    sendDenyReply = true;
                }
            }
            else
            {
                if (ga.maxPlayers <= 4)
                {
                    srv.messageToPlayer(c, gaName, "It's not your turn.");
                    sendDenyReply = true;
                } else {
                    // 6-player board: Special Building Phase
                    // during other player's turn
                    try
                    {
                        ga.askSpecialBuild(pn, true);  // will validate that they can build now
                        srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 1));
                    } catch (IllegalStateException e) {
                        srv.messageToPlayer(c, gaName, "You can't ask to build now.");
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
     * handle "cancel build request" message.
     * Cancel placement and send new game state, if cancel is allowed.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleCANCELBUILDREQUEST(SOCGame ga, StringConnection c, final SOCCancelBuildRequest mes)
    {
        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            if (handler.checkTurn(c, ga))
            {
                final SOCPlayer player = ga.getPlayer((String) c.getData());
                final int pn = player.getPlayerNumber();
                final int gstate = ga.getGameState();

                switch (mes.getPieceType())
                {
                case SOCPlayingPiece.ROAD:

                    if ((gstate == SOCGame.PLACING_ROAD) || (gstate == SOCGame.PLACING_FREE_ROAD2))
                    {
                        ga.cancelBuildRoad(pn);
                        if (gstate == SOCGame.PLACING_ROAD)
                        {
                            srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.CLAY, 1));
                            srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                        } else {
                            srv.messageToGameKeyed(ga, true, "action.card.roadbuilding.skip.r", player.getName());
                                // "{0} skipped placing the second road."
                        }
                        handler.sendGameState(ga);
                    }
                    else
                    {
                        srv.messageToPlayer(c, gaName, /*I*/"You didn't buy a road."/*18N*/ );
                    }

                    break;

                case SOCPlayingPiece.SETTLEMENT:

                    if (gstate == SOCGame.PLACING_SETTLEMENT)
                    {
                        ga.cancelBuildSettlement(pn);
                        srv.gameList.takeMonitorForGame(gaName);
                        srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.CLAY, 1));
                        srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.SHEEP, 1));
                        srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WHEAT, 1));
                        srv.messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                        srv.gameList.releaseMonitorForGame(gaName);
                        handler.sendGameState(ga);
                    }
                    else if ((gstate == SOCGame.START1B) || (gstate == SOCGame.START2B) || (gstate == SOCGame.START3B))
                    {
                        SOCSettlement pp = new SOCSettlement(player, player.getLastSettlementCoord(), null);
                        ga.undoPutInitSettlement(pp);
                        srv.messageToGame(gaName, mes);  // Re-send to all clients to announce it
                            // (Safe since we've validated all message parameters)
                        srv.messageToGameKeyed(ga, true, "action.built.stlmt.cancel", player.getName());  //  "{0} cancelled this settlement placement."
                        handler.sendGameState(ga);  // This send is redundant, if client reaction changes game state
                    }
                    else
                    {
                        srv.messageToPlayer(c, gaName, /*I*/"You didn't buy a settlement."/*18N*/ );
                    }

                    break;

                case SOCPlayingPiece.CITY:

                    if (gstate == SOCGame.PLACING_CITY)
                    {
                        ga.cancelBuildCity(pn);
                        srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.ORE, 3));
                        srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WHEAT, 2));
                        handler.sendGameState(ga);
                    }
                    else
                    {
                        srv.messageToPlayer(c, gaName, /*I*/"You didn't buy a city."/*18N*/ );
                    }

                    break;

                case SOCPlayingPiece.SHIP:

                    if ((gstate == SOCGame.PLACING_SHIP) || (gstate == SOCGame.PLACING_FREE_ROAD2))
                    {
                        ga.cancelBuildShip(pn);
                        if (gstate == SOCGame.PLACING_SHIP)
                        {
                            srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.SHEEP, 1));
                            srv.messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                        } else {
                            srv.messageToGameKeyed(ga, true, "action.card.roadbuilding.skip.s", player.getName());
                                // "{0} skipped placing the second ship."
                        }
                        handler.sendGameState(ga);
                    }
                    else
                    {
                        srv.messageToPlayer(c, gaName, /*I*/"You didn't buy a ship."/*18N*/ );
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
                        handler.sendGameState(ga);
                    } else {
                        srv.messageToPlayerKeyed(c, gaName, "reply.placeitem.cancel.cannot");
                            // "Cannot cancel item placement."
                    }
                    break;

                default:
                    throw new IllegalArgumentException("Unknown piece type " + mes.getPieceType());
                }
            }
            else
            {
                srv.messageToPlayerKeyed(c, gaName, "reply.not.your.turn");  // "It's not your turn."
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

}
