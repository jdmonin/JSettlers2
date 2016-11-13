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

import soc.game.SOCGame;
import soc.message.SOCAcceptOffer;
import soc.message.SOCBankTrade;
import soc.message.SOCBuildRequest;
import soc.message.SOCBuyCardRequest;
import soc.message.SOCCancelBuildRequest;
import soc.message.SOCChoosePlayer;
import soc.message.SOCClearOffer;
import soc.message.SOCDebugFreePlace;
import soc.message.SOCDiscard;
import soc.message.SOCDiscoveryPick;
import soc.message.SOCEndTurn;
import soc.message.SOCInventoryItemAction;
import soc.message.SOCMakeOffer;
import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;
import soc.message.SOCMonopolyPick;
import soc.message.SOCMovePieceRequest;
import soc.message.SOCMoveRobber;
import soc.message.SOCPickResources;
import soc.message.SOCPlayDevCardRequest;
import soc.message.SOCPutPiece;
import soc.message.SOCRejectOffer;
import soc.message.SOCRollDice;
import soc.message.SOCSetSpecialItem;
import soc.message.SOCSimpleRequest;
import soc.server.genericServer.StringConnection;

/**
 * Game message handler for the {@link SOCGameHandler} game type.
 * The purpose of this class is to dispatch the messages received from the
 * {@link soc.server.genericServer.InboundMessageQueue} related to specific games.
 *<P>
 * the message for the games are recognized because they implement the interface {@link SOCMessageForGame}
 *<P>
 * when the {@link SOCGameMessageHandler} is solicited to process the message, it identify the exact type of message
 * and call the correct method
 * of the {@link SOCGameHandler} that instead is responsible to execute the business
 *
 * <UL>
 * <LI> See the method {@link #dispatch(SOCGameHandler, SOCGame, SOCMessageForGame, StringConnection)} to get more
 *      details of this class logic
 * <LI> See {@link SOCGameHandler} for details of the busines logic for each message.
 * </UL>
 *<P>
 * Before v2.0.00, this class was {@link SOCServer#processCommand(String, StringConnection)} and related methods.
 *
 * @author Alessandro D'Ottavio
 * @since 2.0.00
 */
public class SOCGameMessageHandler
{
    private final SOCGameHandler handler;

    public SOCGameMessageHandler(SOCGameHandler sgh)
    {
        handler = sgh;
    }

    /**
     * Dispatch any event that is coming from a client player for a specific game.
     *<P>
     * Some game messages (such as player sits down, or board reset voting) are handled the same for all game types.
     * These are handled at {@link SOCServer}; they should be ignored here and not appear in your switch statement.
     *<P>
     * this method is called from {@link SOCServer#processCommand(String, StringConnection)} when it is recognized as
     * an event for game.
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
            handler.handleROLLDICE(game, connection, (SOCRollDice) message);

            //ga = (SOCGame)gamesData.get(((SOCRollDice)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCRollDice)mes).getGame());
            break;

        case SOCMessage.DISCARD:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleDISCARD(game, connection, (SOCDiscard) message);

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
            handler.handleMAKEOFFER(game, connection, (SOCMakeOffer) message);

            //ga = (SOCGame)gamesData.get(((SOCMakeOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCMakeOffer)mes).getGame());
            break;

        case SOCMessage.CLEAROFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleCLEAROFFER(game, connection, (SOCClearOffer) message);

            //ga = (SOCGame)gamesData.get(((SOCClearOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCClearOffer)mes).getGame());
            break;

        case SOCMessage.REJECTOFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleREJECTOFFER(game, connection, (SOCRejectOffer) message);

            //ga = (SOCGame)gamesData.get(((SOCRejectOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCRejectOffer)mes).getGame());
            break;

        case SOCMessage.ACCEPTOFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleACCEPTOFFER(game, connection, (SOCAcceptOffer) message);

            //ga = (SOCGame)gamesData.get(((SOCAcceptOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCAcceptOffer)mes).getGame());
            break;

        case SOCMessage.BANKTRADE:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleBANKTRADE(game, connection, (SOCBankTrade) message);

            //ga = (SOCGame)gamesData.get(((SOCBankTrade)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCBankTrade)mes).getGame());
            break;

        case SOCMessage.BUILDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleBUILDREQUEST(game, connection, (SOCBuildRequest) message);

            //ga = (SOCGame)gamesData.get(((SOCBuildRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCBuildRequest)mes).getGame());
            break;

        case SOCMessage.CANCELBUILDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            handler.handleCANCELBUILDREQUEST(game, connection, (SOCCancelBuildRequest) message);

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
            handler.handlePICKRESOURCES(game, connection, (SOCPickResources) message);
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

}
