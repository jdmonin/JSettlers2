/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2016 Alessandro D'Ottavio
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
 * 
 * @author Alessandro D'Ottavio
 * @since 2.0.00
 */
public class SOCGameMessageHandler
{

    public boolean dispatch(SOCGameHandler hand, SOCGame ga,SOCMessageForGame mes, StringConnection c)
    {

        switch (mes.getType())
        {

        /**
         * someone put a piece on the board
         */
        case SOCMessage.PUTPIECE:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handlePUTPIECE(ga, c, (SOCPutPiece) mes);

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
            hand.handleMOVEROBBER(ga, c, (SOCMoveRobber) mes);

            //ga = (SOCGame)gamesData.get(((SOCMoveRobber)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCMoveRobber)mes).getGame());
            break;

        case SOCMessage.ROLLDICE:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleROLLDICE(ga, c, (SOCRollDice) mes);

            //ga = (SOCGame)gamesData.get(((SOCRollDice)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCRollDice)mes).getGame());
            break;

        case SOCMessage.DISCARD:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleDISCARD(ga, c, (SOCDiscard) mes);

            //ga = (SOCGame)gamesData.get(((SOCDiscard)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCDiscard)mes).getGame());
            break;

        case SOCMessage.ENDTURN:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleENDTURN(ga, c, (SOCEndTurn) mes);

            //ga = (SOCGame)gamesData.get(((SOCEndTurn)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCEndTurn)mes).getGame());
            break;

        case SOCMessage.CHOOSEPLAYER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleCHOOSEPLAYER(ga, c, (SOCChoosePlayer) mes);

            //ga = (SOCGame)gamesData.get(((SOCChoosePlayer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCChoosePlayer)mes).getGame());
            break;

        case SOCMessage.MAKEOFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleMAKEOFFER(ga, c, (SOCMakeOffer) mes);

            //ga = (SOCGame)gamesData.get(((SOCMakeOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCMakeOffer)mes).getGame());
            break;

        case SOCMessage.CLEAROFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleCLEAROFFER(ga, c, (SOCClearOffer) mes);

            //ga = (SOCGame)gamesData.get(((SOCClearOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCClearOffer)mes).getGame());
            break;

        case SOCMessage.REJECTOFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleREJECTOFFER(ga, c, (SOCRejectOffer) mes);

            //ga = (SOCGame)gamesData.get(((SOCRejectOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCRejectOffer)mes).getGame());
            break;

        case SOCMessage.ACCEPTOFFER:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleACCEPTOFFER(ga, c, (SOCAcceptOffer) mes);

            //ga = (SOCGame)gamesData.get(((SOCAcceptOffer)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCAcceptOffer)mes).getGame());
            break;

        case SOCMessage.BANKTRADE:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleBANKTRADE(ga, c, (SOCBankTrade) mes);

            //ga = (SOCGame)gamesData.get(((SOCBankTrade)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCBankTrade)mes).getGame());
            break;

        case SOCMessage.BUILDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleBUILDREQUEST(ga, c, (SOCBuildRequest) mes);

            //ga = (SOCGame)gamesData.get(((SOCBuildRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCBuildRequest)mes).getGame());
            break;

        case SOCMessage.CANCELBUILDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleCANCELBUILDREQUEST(ga, c, (SOCCancelBuildRequest) mes);

            //ga = (SOCGame)gamesData.get(((SOCCancelBuildRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCCancelBuildRequest)mes).getGame());
            break;

        case SOCMessage.BUYCARDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleBUYCARDREQUEST(ga, c, (SOCBuyCardRequest) mes);

            //ga = (SOCGame)gamesData.get(((SOCBuyCardRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCBuyCardRequest)mes).getGame());
            break;

        case SOCMessage.PLAYDEVCARDREQUEST:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handlePLAYDEVCARDREQUEST(ga, c, (SOCPlayDevCardRequest) mes);

            //ga = (SOCGame)gamesData.get(((SOCPlayDevCardRequest)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCPlayDevCardRequest)mes).getGame());
            break;

        case SOCMessage.DISCOVERYPICK:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleDISCOVERYPICK(ga, c, (SOCDiscoveryPick) mes);

            //ga = (SOCGame)gamesData.get(((SOCDiscoveryPick)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCDiscoveryPick)mes).getGame());
            break;

        case SOCMessage.MONOPOLYPICK:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            hand.handleMONOPOLYPICK(ga, c, (SOCMonopolyPick) mes);

            //ga = (SOCGame)gamesData.get(((SOCMonopolyPick)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCMonopolyPick)mes).getGame());
            break;

        /**
         * debug piece Free Placement (as of 20110104 (v 1.1.12))
         */
        case SOCMessage.DEBUGFREEPLACE:
            hand.handleDEBUGFREEPLACE(ga, c, (SOCDebugFreePlace) mes);
            break;

        /**
         * Generic simple request from a player.
         * Added 2013-02-17 for v1.1.18.
         */
        case SOCMessage.SIMPLEREQUEST:
            hand.handleSIMPLEREQUEST(ga, c, (SOCSimpleRequest) mes);
            break;

        /**
         * Special inventory item action (play request) from a player.
         * Added 2013-11-28 for v2.0.00.
         */
        case SOCMessage.INVENTORYITEMACTION:
            hand.handleINVENTORYITEMACTION(ga, c, (SOCInventoryItemAction) mes);
            break;

        /**
         * Asking to move a previous piece (a ship) somewhere else on the board.
         * Added 2011-12-04 for v2.0.00.
         */
        case SOCMessage.MOVEPIECEREQUEST:
            hand.handleMOVEPIECEREQUEST(ga, c, (SOCMovePieceRequest) mes);
            break;

        /**
         * Picking resources to gain from a Gold Hex.
         * Added 2012-01-12 for v2.0.00.
         */
        case SOCMessage.PICKRESOURCES:
            hand.handlePICKRESOURCES(ga, c, (SOCPickResources) mes);
            break;

        /**
         * Special Item requests.
         * Added 2014-05-17 for v2.0.00.
         */
        case SOCMessage.SETSPECIALITEM:
            hand.handleSETSPECIALITEM(ga, c, (SOCSetSpecialItem) mes);
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
