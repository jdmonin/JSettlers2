/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2019 Colin Werner
 * Extracted in 2019 from SOCPlayerClient.java, so:
 * Portions of this file Copyright (C) 2007-2023 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
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
package soc.client;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.disableDebug.D;
import soc.game.SOCBoardLarge;
import soc.game.SOCDevCardConstants;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCInventory;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCScenario;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;
import soc.game.SOCVillage;

import soc.message.*;
import soc.message.SOCGameElements.GEType;
import soc.message.SOCPlayerElement.PEType;
import soc.util.SOCFeatureSet;
import soc.util.SOCGameList;
import soc.util.SOCStringManager;
import soc.util.Version;

/**
 * Nested class for processing incoming messages (treating).
 * {@link ClientNetwork}'s reader thread calls
 * {@link #handle(SOCMessage, boolean)} to dispatch messages to their
 * handler methods such as {@link #handleBANKTRADE(SOCBankTrade, boolean)}.
 *<P>
 * Before v2.0.00, most of these fields and methods were part of the main {@link SOCPlayerClient} class.
 *
 * @author paulbilnoski
 * @since 2.0.00
 */
public class MessageHandler
{
    private SOCPlayerClient client;
    private GameMessageSender gms;

    /**
     * Create a MessageHandler.
     * Must call {@link #init(SOCPlayerClient)} for initial setup before
     * first call to {@link #handle(SOCMessage, boolean)}.
     */
    public MessageHandler()
    {
    }

    /**
     * Initial setup for {@code client} and {@code messageSender} fields.
     * To allow subclassing, that isn't done in the constructor: The client constructor
     * would want a MessageHandler, and the MessageHandler constructor would want a client.
     *
     * @param cli  Client for this MessageHandler; its {@link SOCPlayerClient#getGameMessageSender()} must not be null
     * @throw IllegalArgumentException if {@code cli} or its {@code getGameMessageSender()} is null
     * @since 2.5.00
     */
    public void init(final SOCPlayerClient cli)
        throws IllegalArgumentException
    {
        if (cli == null)
            throw new IllegalArgumentException("client is null");

        this.client = cli;
        gms = cli.getGameMessageSender();
        if (gms == null)
            throw new IllegalArgumentException("client GameMessageSender is null");
    }

    /**
     * Get this MessageHandler's client.
     * @since 2.5.00
     */
    public SOCPlayerClient getClient()
    {
        return client;
    }

    /**
     * Treat the incoming messages.
     * Messages of unknown type are ignored
     * ({@code mes} will be null from {@link SOCMessage#toMsg(String)}).
     *<P>
     * Must call {@link #init(SOCPlayerClient)} for initial setup before first call to {@code handle(..)}.
     *<P>
     * Before v2.0.00 this method was {@code SOCPlayerClient.treat(..)}.
     *
     * @param mes    the message
     * @param isPractice  Message is coming from {@link ClientNetwork#practiceServer}, not a TCP server
     */
    public void handle(SOCMessage mes, final boolean isPractice)
    {
        if (mes == null)
            return;  // Parsing error

        if (client.debugTraffic || D.ebugIsEnabled())
            soc.debug.D.ebugPrintlnINFO(mes.toString());

        try
        {
            final String gaName;
            final SOCGame ga;
            if (mes instanceof SOCMessageForGame)
            {
                gaName = ((SOCMessageForGame) mes).getGame();
                ga = (gaName != null) ? client.games.get(gaName) : null;
                // Allows null gaName, for the few message types (like SOCScenarioInfo) which
                // for convenience use something like SOCTemplateMs which extends SOCMessageForGame
                // but aren't actually game-specific messages.
            } else {
                gaName = null;
                ga = null;
            }

            switch (mes.getType())
            {

            /**
             * echo the server ping, to ensure we're still connected.
             * (ignored before version 1.1.08)
             */
            case SOCMessage.SERVERPING:
                handleSERVERPING((SOCServerPing) mes, isPractice);
                break;

            /**
             * server's version message
             */
            case SOCMessage.VERSION:
                handleVERSION(isPractice, (SOCVersion) mes);
                break;

            /**
             * status message
             */
            case SOCMessage.STATUSMESSAGE:
                handleSTATUSMESSAGE((SOCStatusMessage) mes, isPractice);
                break;

            /**
             * join channel authorization
             */
            case SOCMessage.JOINCHANNELAUTH:
                handleJOINCHANNELAUTH((SOCJoinChannelAuth) mes);
                break;

            /**
             * someone joined a channel
             */
            case SOCMessage.JOINCHANNEL:
                handleJOINCHANNEL((SOCJoinChannel) mes);
                break;

            /**
             * list of members for a chat channel
             */
            case SOCMessage.CHANNELMEMBERS:
                handleCHANNELMEMBERS((SOCChannelMembers) mes);
                break;

            /**
             * a new chat channel has been created
             */
            case SOCMessage.NEWCHANNEL:
                handleNEWCHANNEL((SOCNewChannel) mes);
                break;

            /**
             * List of chat channels on the server: Server connection is complete.
             * (sent at connect after VERSION, even if no channels)
             * Show main panel if not already showing; see handleCHANNELS javadoc.
             */
            case SOCMessage.CHANNELS:
                handleCHANNELS((SOCChannels) mes, isPractice);
                break;

            /**
             * text message to a chat channel
             */
            case SOCMessage.CHANNELTEXTMSG:
                handleCHANNELTEXTMSG((SOCChannelTextMsg) mes);
                break;

            /**
             * someone left the chat channel
             */
            case SOCMessage.LEAVECHANNEL:
                handleLEAVECHANNEL((SOCLeaveChannel) mes);
                break;

            /**
             * delete a chat channel
             */
            case SOCMessage.DELETECHANNEL:
                handleDELETECHANNEL((SOCDeleteChannel) mes);
                break;

            /**
             * list of games on the server
             */
            case SOCMessage.GAMES:
                handleGAMES((SOCGames) mes, isPractice);
                break;

            /**
             * join game authorization
             */
            case SOCMessage.JOINGAMEAUTH:
                handleJOINGAMEAUTH((SOCJoinGameAuth) mes, isPractice);
                break;

            /**
             * someone joined a game
             */
            case SOCMessage.JOINGAME:
                handleJOINGAME((SOCJoinGame) mes);
                break;

            /**
             * someone left a game
             */
            case SOCMessage.LEAVEGAME:
                handleLEAVEGAME((SOCLeaveGame) mes);
                break;

            /**
             * new game has been created
             */
            case SOCMessage.NEWGAME:
                handleNEWGAME((SOCNewGame) mes, isPractice);
                break;

            /**
             * game has been destroyed
             */
            case SOCMessage.DELETEGAME:
                handleDELETEGAME((SOCDeleteGame) mes, isPractice);
                break;

            /**
             * list of game members
             */
            case SOCMessage.GAMEMEMBERS:
                handleGAMEMEMBERS((SOCGameMembers) mes);
                break;

            /**
             * game stats
             */
            case SOCMessage.GAMESTATS:
                handleGAMESTATS((SOCGameStats) mes);
                break;

            /**
             * game text message
             */
            case SOCMessage.GAMETEXTMSG:
                handleGAMETEXTMSG((SOCGameTextMsg) mes);
                break;

            /**
             * broadcast text message
             */
            case SOCMessage.BCASTTEXTMSG:
                handleBCASTTEXTMSG((SOCBCastTextMsg) mes);
                break;

            /**
             * someone is sitting down
             */
            case SOCMessage.SITDOWN:
                handleSITDOWN((SOCSitDown) mes);
                break;

            /**
             * receive a board layout
             */
            case SOCMessage.BOARDLAYOUT:
                handleBOARDLAYOUT((SOCBoardLayout) mes);
                break;

            /**
             * receive a board layout (new format, as of 20091104 (v 1.1.08))
             */
            case SOCMessage.BOARDLAYOUT2:
                handleBOARDLAYOUT2((SOCBoardLayout2) mes);
                break;

            /**
             * message that the game is starting
             */
            case SOCMessage.STARTGAME:
                handleSTARTGAME((SOCStartGame) mes);
                break;

            /**
             * update the state of the game
             */
            case SOCMessage.GAMESTATE:
                handleGAMESTATE((SOCGameState) mes);
                break;

            /**
             * set the current turn
             */
            case SOCMessage.SETTURN:
                handleGAMEELEMENT(ga, GEType.CURRENT_PLAYER, ((SOCSetTurn) mes).getPlayerNumber());
                break;

            /**
             * set who the first player is
             */
            case SOCMessage.FIRSTPLAYER:
                handleGAMEELEMENT(ga, GEType.FIRST_PLAYER, ((SOCFirstPlayer) mes).getPlayerNumber());
                break;

            /**
             * update who's turn it is
             */
            case SOCMessage.TURN:
                handleTURN((SOCTurn) mes);
                break;

            /**
             * receive player information
             */
            case SOCMessage.PLAYERELEMENT:
                handlePLAYERELEMENT((SOCPlayerElement) mes);
                break;

            /**
             * receive player information.
             * Added 2017-12-10 for v2.0.00.
             */
            case SOCMessage.PLAYERELEMENTS:
                handlePLAYERELEMENTS((SOCPlayerElements) mes);
                break;

            /**
             * update game element information.
             * Added 2017-12-24 for v2.0.00.
             */
            case SOCMessage.GAMEELEMENTS:
                handleGAMEELEMENTS((SOCGameElements) mes);
                break;

            /**
             * receive resource count
             */
            case SOCMessage.RESOURCECOUNT:
                handleRESOURCECOUNT((SOCResourceCount) mes);
                break;

            /**
             * receive player's last settlement location.
             * Added 2017-12-23 for v2.0.00.
             */
            case SOCMessage.LASTSETTLEMENT:
                SOCDisplaylessPlayerClient.handleLASTSETTLEMENT
                    ((SOCLastSettlement) mes, client.games.get(((SOCLastSettlement) mes).getGame()));
                break;

            /**
             * the latest dice result
             */
            case SOCMessage.DICERESULT:
                handleDICERESULT((SOCDiceResult) mes);
                break;

            /**
             * a player built something
             */
            case SOCMessage.PUTPIECE:
                handlePUTPIECE((SOCPutPiece) mes);
                break;

            /**
             * the current player has cancelled an initial settlement,
             * or has tried to place a piece illegally.
             */
            case SOCMessage.CANCELBUILDREQUEST:
                handleCANCELBUILDREQUEST((SOCCancelBuildRequest) mes);
                break;

            /**
             * the robber or pirate moved
             */
            case SOCMessage.MOVEROBBER:
                handleMOVEROBBER((SOCMoveRobber) mes);
                break;

            /**
             * prompt this player to discard
             */
            case SOCMessage.DISCARDREQUEST:
                handleDISCARDREQUEST((SOCDiscardRequest) mes);
                break;

            /**
             * prompt this player to choose a player to rob
             */
            case SOCMessage.CHOOSEPLAYERREQUEST:
                handleCHOOSEPLAYERREQUEST((SOCChoosePlayerRequest) mes);
                break;

            /**
             * Prompt this player to choose to rob cloth or rob resources.
             * Added 2012-11-17 for v2.0.00.
             */
            case SOCMessage.CHOOSEPLAYER:
                handleCHOOSEPLAYER((SOCChoosePlayer) mes);
                break;

            /**
             * A player has discarded resources.
             * Added 2021-11-26 for v2.5.00.
             */
            case SOCMessage.DISCARD:
                handleDISCARD((SOCDiscard) mes);
                break;

            /**
             * a player has made a bank/port trade
             */
            case SOCMessage.BANKTRADE:
                handleBANKTRADE((SOCBankTrade) mes, isPractice);
                break;

            /**
             * a player has made an offer
             */
            case SOCMessage.MAKEOFFER:
                handleMAKEOFFER((SOCMakeOffer) mes);
                break;

            /**
             * a player has cleared her offer
             */
            case SOCMessage.CLEAROFFER:
                handleCLEAROFFER((SOCClearOffer) mes);
                break;

            /**
             * a player has rejected an offer,
             * or server has disallowed our trade-related request.
             */
            case SOCMessage.REJECTOFFER:
                handleREJECTOFFER((SOCRejectOffer) mes);
                break;

            /**
             * a player has accepted a trade offer
             */
            case SOCMessage.ACCEPTOFFER:
                handleACCEPTOFFER((SOCAcceptOffer) mes);
                break;

            /**
             * the trade message needs to be cleared
             */
            case SOCMessage.CLEARTRADEMSG:
                handleCLEARTRADEMSG((SOCClearTradeMsg) mes);
                break;

            /**
             * update game data, like the current number of development cards
             */
            case SOCMessage.DEVCARDCOUNT:
                handleGAMEELEMENT(ga, GEType.DEV_CARD_COUNT, ((SOCDevCardCount) mes).getNumDevCards());
                break;

            /**
             * a dev card action, either draw, play, or add to hand
             */
            case SOCMessage.DEVCARDACTION:
                handleDEVCARDACTION(isPractice, (SOCDevCardAction) mes);
                break;

            /**
             * set the flag that tells if a player has played a
             * development card this turn
             */
            case SOCMessage.SETPLAYEDDEVCARD:
                handleSETPLAYEDDEVCARD((SOCSetPlayedDevCard) mes);
                break;

            /**
             * receive a list of all the potential settlements for a player
             */
            case SOCMessage.POTENTIALSETTLEMENTS:
                handlePOTENTIALSETTLEMENTS((SOCPotentialSettlements) mes);
                break;

            /**
             * handle the change face message
             */
            case SOCMessage.CHANGEFACE:
                handleCHANGEFACE((SOCChangeFace) mes);
                break;

            /**
             * handle the reject connection message
             */
            case SOCMessage.REJECTCONNECTION:
                handleREJECTCONNECTION((SOCRejectConnection) mes);
                break;

            /**
             * handle the longest road message
             */
            case SOCMessage.LONGESTROAD:
                handleGAMEELEMENT(ga, GEType.LONGEST_ROAD_PLAYER, ((SOCLongestRoad) mes).getPlayerNumber());
                break;

            /**
             * handle the largest army message
             */
            case SOCMessage.LARGESTARMY:
                handleGAMEELEMENT(ga, GEType.LARGEST_ARMY_PLAYER, ((SOCLargestArmy) mes).getPlayerNumber());
                break;

            /**
             * handle the seat lock state message
             */
            case SOCMessage.SETSEATLOCK:
                handleSETSEATLOCK((SOCSetSeatLock) mes);
                break;

            /**
             * handle the roll dice prompt message
             * (it is now x's turn to roll the dice)
             */
            case SOCMessage.ROLLDICEPROMPT:
                handleROLLDICEPROMPT((SOCRollDicePrompt) mes);
                break;

            /**
             * handle board reset (new game with same players, same game name, new layout).
             */
            case SOCMessage.RESETBOARDAUTH:
                handleRESETBOARDAUTH((SOCResetBoardAuth) mes);
                break;

            /**
             * another player is requesting a board reset: we must vote
             */
            case SOCMessage.RESETBOARDVOTEREQUEST:
                handleRESETBOARDVOTEREQUEST((SOCResetBoardVoteRequest) mes);
                break;

            /**
             * another player has voted on a board reset request
             */
            case SOCMessage.RESETBOARDVOTE:
                handleRESETBOARDVOTE((SOCResetBoardVote) mes);
                break;

            /**
             * voting complete, board reset request rejected
             */
            case SOCMessage.RESETBOARDREJECT:
                handleRESETBOARDREJECT((SOCResetBoardReject) mes);
                break;

            /**
             * for game options (1.1.07)
             */
            case SOCMessage.GAMEOPTIONGETDEFAULTS:
                handleGAMEOPTIONGETDEFAULTS((SOCGameOptionGetDefaults) mes, isPractice);
                break;

            case SOCMessage.GAMEOPTIONINFO:
                handleGAMEOPTIONINFO((SOCGameOptionInfo) mes, isPractice);
                break;

            case SOCMessage.NEWGAMEWITHOPTIONS:
                handleNEWGAMEWITHOPTIONS((SOCNewGameWithOptions) mes, isPractice);
                break;

            case SOCMessage.GAMESWITHOPTIONS:
                handleGAMESWITHOPTIONS((SOCGamesWithOptions) mes, isPractice);
                break;

            /**
             * player stats (as of 20100312 (v 1.1.09))
             */
            case SOCMessage.PLAYERSTATS:
                handlePLAYERSTATS((SOCPlayerStats) mes);
                break;

            /**
             * debug piece Free Placement (as of 20110104 (v 1.1.12))
             */
            case SOCMessage.DEBUGFREEPLACE:
                handleDEBUGFREEPLACE((SOCDebugFreePlace) mes);
                break;

            /**
             * generic 'simple request' response from the server.
             * Added 2013-02-19 for v1.1.18.
             */
            case SOCMessage.SIMPLEREQUEST:
                handleSIMPLEREQUEST((SOCSimpleRequest) mes);
                break;

            /**
             * generic "simple action" announcements from the server.
             * Added 2013-09-04 for v1.1.19.
             */
            case SOCMessage.SIMPLEACTION:
                handleSIMPLEACTION((SOCSimpleAction) mes);
                break;

            /**
             * game server text and announcements.
             * Added 2013-09-05 for v2.0.00.
             */
            case SOCMessage.GAMESERVERTEXT:
                handleGAMESERVERTEXT((SOCGameServerText) mes);
                break;

            /**
             * All players' dice roll result resources.
             * Added 2013-09-20 for v2.0.00.
             */
            case SOCMessage.DICERESULTRESOURCES:
                handleDICERESULTRESOURCES((SOCDiceResultResources) mes);
                break;

            /**
             * move a previous piece (a ship) somewhere else on the board.
             * Added 2011-12-05 for v2.0.00.
             */
            case SOCMessage.MOVEPIECE:
                handleMOVEPIECE((SOCMovePiece) mes);
                break;

            /**
             * remove a piece (a ship) from the board in certain scenarios.
             * Added 2013-02-19 for v2.0.00.
             */
            case SOCMessage.REMOVEPIECE:
                handleREMOVEPIECE((SOCRemovePiece) mes);
                break;

            /**
             * reveal a hidden hex on the board.
             * Added 2012-11-08 for v2.0.00.
             */
            case SOCMessage.REVEALFOGHEX:
                handleREVEALFOGHEX((SOCRevealFogHex) mes);
                break;

            /**
             * update a village piece's value on the board (cloth remaining).
             * Added 2012-11-16 for v2.0.00.
             */
            case SOCMessage.PIECEVALUE:
                handlePIECEVALUE((SOCPieceValue) mes);
                break;

            /**
             * Text that a player has been awarded Special Victory Point(s).
             * Added 2012-12-21 for v2.0.00.
             */
            case SOCMessage.SVPTEXTMSG:
                handleSVPTEXTMSG((SOCSVPTextMessage) mes);
                break;

            /**
             * a special inventory item action: either add or remove,
             * or we cannot play our requested item.
             * Added 2013-11-26 for v2.0.00.
             */
            case SOCMessage.INVENTORYITEMACTION:
                handleINVENTORYITEMACTION((SOCInventoryItemAction) mes);
                break;

            /**
             * Special Item change announcements.
             * Added 2014-04-16 for v2.0.00.
             */
            case SOCMessage.SETSPECIALITEM:
                handleSETSPECIALITEM(client.games, (SOCSetSpecialItem) mes);
                break;

            /**
             * Localized i18n strings for game items.
             * Added 2015-01-11 for v2.0.00.
             */
            case SOCMessage.LOCALIZEDSTRINGS:
                handleLOCALIZEDSTRINGS((SOCLocalizedStrings) mes, isPractice);
                break;

            /**
             * Updated scenario info.
             * Added 2015-09-21 for v2.0.00.
             */
            case SOCMessage.SCENARIOINFO:
                handleSCENARIOINFO((SOCScenarioInfo) mes, isPractice);
                break;

            /**
             * Report robbery result.
             * Added 2020-09-15 for v2.5.00.
             */
            case SOCMessage.ROBBERYRESULT:
                handleROBBERYRESULT
                    ((SOCRobberyResult) mes, client.games.get(((SOCMessageForGame) mes).getGame()));
                break;

            /**
             * Player has Picked Resources.
             * Added 2020-12-14 for v2.5.00.
             */
            case SOCMessage.PICKRESOURCES:
                handlePICKRESOURCES
                    ((SOCPickResources) mes, client.games.get(((SOCMessageForGame) mes).getGame()));
                break;

            /**
             * Server has declined player's request.
             * Added 2021-12-08 for v2.5.00.
             */
            case SOCMessage.DECLINEPLAYERREQUEST:
                handleDECLINEPLAYERREQUEST((SOCDeclinePlayerRequest) mes);
                break;

            /**
             * Undo moving or placing a piece.
             * Added 2022-11-14 for v2.7.00.
             */
            case SOCMessage.UNDOPUTPIECE:
                handleUNDOPUTPIECE((SOCUndoPutPiece) mes);
                break;

            /**
             * Update last-action data.
             * Added 2022-12-20 for v2.7.00.
             */
            case SOCMessage.SETLASTACTION:
                SOCDisplaylessPlayerClient.handleSETLASTACTION
                    ((SOCSetLastAction) mes, client.games.get(((SOCSetLastAction) mes).getGame()));
                break;

            /**
             * Reopen or close a shipping trade route.
             * Added 2022-12-18 for v2.7.00.
             */
            case SOCMessage.SETSHIPROUTECLOSED:
                SOCDisplaylessPlayerClient.handleSETSHIPROUTECLOSED
                    ((SOCSetShipRouteClosed) mes, client.games.get(((SOCSetShipRouteClosed) mes).getGame()));
                break;

            }  // switch (mes.getType())
        }
        catch (Throwable th)
        {
            System.out.println("SOCPlayerClient treat ERROR - " + th.getMessage());
            th.printStackTrace();
            System.out.println("  For message: " + mes);
        }

    }  // treat

    /**
     * Handle the "version" message, server's version report.
     * Ask server for game-option info if client's version differs.
     * If remote, store the server's version for {@link #getServerVersion(SOCGame)}
     * and display the version on the main panel.
     * (Local server's version is always {@link Version#versionNumber()}.)
     *
     * @param isPractice Is the server {@link ClientNetwork#practiceServer}, not remote?  Client can be connected
     *                only to one at a time.
     * @param mes  the message
     * @since 1.1.00
     */
    private void handleVERSION(final boolean isPractice, SOCVersion mes)
    {
        D.ebugPrintlnINFO("handleVERSION: " + mes);
        int vers = mes.getVersionNumber();

        if (! isPractice)
        {
            client.sVersion = vers;
            client.sFeatures = (vers >= SOCFeatureSet.VERSION_FOR_SERVERFEATURES)
                    ? new SOCFeatureSet(mes.feats)
                    : new SOCFeatureSet(true, true);

            client.getMainDisplay().showVersion(vers, mes.getVersionString(), mes.getBuild(), client.sFeatures);
        }

        // If we ever require a minimum server version, would check that here.

        // Check known game options vs server's version. (added in 1.1.07)
        // Server's responses will add, remove or change our "known options".
        // In v2.0.00 and later, also checks for game option localized descriptions.
        // In v2.5.00 and later, also checks for 3rd-party game opts.

        final int cliVersion = Version.versionNumber();
        final boolean sameVersion = (client.sVersion == cliVersion);
        final boolean withTokenI18n =
            (client.cliLocale != null) && (isPractice || (client.sVersion >= SOCStringManager.VERSION_FOR_I18N))
            && ! ("en".equals(client.cliLocale.getLanguage()) && "US".equals(client.cliLocale.getCountry()));
        SOCGameOptionSet opts3p =
            ((client.sVersion >= cliVersion) && ! isPractice)
            ? client.tcpServGameOpts.knownOpts.optionsWithFlag(SOCGameOption.FLAG_3RD_PARTY, 0)
            : null;   // sVersion < cliVersion, so SOCGameOptionSet.optionsNewerThanVersion will find any 3rd-party opts

        if ( ((! isPractice) && (client.sVersion > cliVersion))
             || ((isPractice || sameVersion) && (withTokenI18n || (opts3p != null))) )
        {
            // Newer server: Ask it to list any options we don't know about yet.
            // Same version: Ask for all localized option descs if available.
            // Also ask about any 3rd-party options known at client.

            final SOCGameOptionGetInfos ogiMsg;
            if (opts3p != null)
            {
                ArrayList<String> olist = new ArrayList<>(opts3p.keySet());
                if (! sameVersion)
                    olist.add(SOCGameOptionGetInfos.OPTKEY_GET_ANY_CHANGES);
                ogiMsg = new SOCGameOptionGetInfos(olist, withTokenI18n, false);
                    // sends opts and maybe "?I18N"
            } else {
                ogiMsg = new SOCGameOptionGetInfos(null, withTokenI18n, withTokenI18n && sameVersion);
                    // sends "-" and/or "?I18N"
            }

            if (! isPractice)
                client.getMainDisplay().optionsRequested();
            gms.put(ogiMsg.toCmd(), isPractice);
        }
        else if ((client.sVersion < cliVersion) && ! isPractice)
        {
            if (client.sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
            {
                // Older server: Look for options created or changed since server's version
                // (and any 3rd-party options).
                // Ask it what it knows about them.

                SOCGameOptionSet knownOpts = client.tcpServGameOpts.knownOpts;
                if (knownOpts == null)
                {
                    knownOpts = SOCGameOptionSet.getAllKnownOptions();
                    client.tcpServGameOpts.knownOpts = knownOpts;
                }

                List<SOCGameOption> tooNewOpts =
                    knownOpts.optionsNewerThanVersion(client.sVersion, false, false);

                if ((tooNewOpts != null) && (client.sVersion < SOCGameOption.VERSION_FOR_LONGER_OPTNAMES))
                {
                    // Server is older than 2.0.00; we can't send it any long option names.
                    // Remove them from our set of options usable for games on that server.

                    Iterator<SOCGameOption> opi = tooNewOpts.iterator();
                    while (opi.hasNext())
                    {
                        final SOCGameOption op = opi.next();
                        //TODO i18n how to?
                        if ((op.key.length() > 3) || op.key.contains("_"))
                        {
                            knownOpts.remove(op.key);
                            opi.remove();
                        }
                    }
                    if (tooNewOpts.isEmpty())
                        tooNewOpts = null;
                }

                if (tooNewOpts != null)
                {
                    client.getMainDisplay().optionsRequested();
                    gms.put(new SOCGameOptionGetInfos(tooNewOpts, withTokenI18n).toCmd(), isPractice);
                }
                else if (withTokenI18n)
                {
                    // server is older than client but understands i18n: request gameopt localized strings

                    gms.put(new SOCGameOptionGetInfos(null, true, false).toCmd(), false);  // sends opt list "-,?I18N"
                }
            } else {
                // server is too old to understand options. Can't happen with local practice srv,
                // because that's our version (it runs from our own JAR file).

                client.tcpServGameOpts.noMoreOptions(true);
                client.tcpServGameOpts.knownOpts = null;
            }
        } else {
            // client.sVersion == cliVersion, so we have same info/code as server for getAllKnownOptions, scenarios, etc
            // and found nothing else to ask about (i18n, 3rd-party gameopts).

            // For practice games, knownOpts may already be initialized, so check vs null.
            ServerGametypeInfo opts = (isPractice ? client.practiceServGameOpts : client.tcpServGameOpts);
            if (opts.knownOpts == null)
                opts.knownOpts = SOCGameOptionSet.getAllKnownOptions();
            opts.noMoreOptions(isPractice);  // defaults not known unless it's practice

            if (! (withTokenI18n || isPractice))
            {
                // won't need i18n strings: set flags so we won't ask server later for scenario details
                opts.allScenStringsReceived = true;
                opts.allScenInfoReceived = true;
            }
        }
    }

    /**
     * handle the {@link SOCStatusMessage "status"} message.
     * Used for server events, also used if player tries to join a game
     * but their nickname is not OK.
     *<P>
     * Also used (v1.1.19 and newer) as a reply to {@link SOCAuthRequest} sent
     * before showing {@link NewGameOptionsFrame}, so check whether the
     * {@link SOCPlayerClient#isNGOFWaitingForAuthStatus isNGOFWaitingForAuthStatus client.isNGOFWaitingForAuthStatus}
     * flag is set.
     *
     * @param mes  the message
     * @param isPractice from practice server, not remote server?
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes, final boolean isPractice)
    {
        int sv = mes.getStatusValue();
        String statusText = mes.getStatus();

        if ((sv == SOCStatusMessage.SV_OK_SET_NICKNAME))
        {
            sv = SOCStatusMessage.SV_OK;

            final int i = statusText.indexOf(SOCMessage.sep2_char);
            if (i > 0)
            {
                client.nickname = statusText.substring(0, i);
                statusText = statusText.substring(i + 1);
                client.getMainDisplay().setNickname(client.nickname);

                // SV_OK_SET_NICKNAME won't ever come from to the practice server;
                // leave client.practiceNickname unchanged.
            }
        }

        final boolean srvDebugMode;
        if (isPractice || (client.sVersion >= 2000))
        {
            final boolean svIsOKDebug = (sv == SOCStatusMessage.SV_OK_DEBUG_MODE_ON);
            srvDebugMode = svIsOKDebug;
            if (svIsOKDebug)
                sv = SOCStatusMessage.SV_OK;
        } else {
            srvDebugMode = statusText.toLowerCase().contains("debug");
        }

        client.getMainDisplay().showStatus(statusText, (sv == SOCStatusMessage.SV_OK), srvDebugMode);

        // Are we waiting for auth response in order to show NGOF?
        if ((! isPractice) && client.isNGOFWaitingForAuthStatus)
        {
            client.isNGOFWaitingForAuthStatus = false;

            if (sv == SOCStatusMessage.SV_OK)
            {
                client.gotPassword = true;

                EventQueue.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        client.getMainDisplay().gameWithOptionsBeginSetup(false, true);
                    }
                });
            }
        }

        switch (sv)
        {
        case SOCStatusMessage.SV_PW_WRONG:
            client.getMainDisplay().focusPassword();
            break;

        case SOCStatusMessage.SV_NEWGAME_OPTION_VALUE_TOONEW:
        {
            // Extract game name and failing game-opt keynames,
            // and pop up an error message window.
            String errMsg;
            StringTokenizer st = new StringTokenizer(statusText, SOCMessage.sep2);
            try
            {
                String gameName = null;
                ArrayList<String> optNames = new ArrayList<String>();
                errMsg = st.nextToken();
                gameName = st.nextToken();
                while (st.hasMoreTokens())
                    optNames.add(st.nextToken());

                StringBuffer opts = new StringBuffer();
                final SOCGameOptionSet knowns =
                    (isPractice) ? client.practiceServGameOpts.knownOpts : client.tcpServGameOpts.knownOpts;
                for (String oname : optNames)
                {
                    opts.append('\n');
                    SOCGameOption oinfo = null;
                    if (knowns != null)
                        oinfo = knowns.get(oname);
                    if (oinfo != null)
                        oname = oinfo.getDesc();
                    opts.append(client.strings.get("options.error.valuesproblem.which", oname));
                }
                errMsg = client.strings.get("options.error.valuesproblem", gameName, errMsg, opts.toString());
            }
            catch (Throwable t)
            {
                errMsg = statusText;  // fallback, not expected to happen
            }

            client.getMainDisplay().showErrorDialog(errMsg, client.strings.get("base.cancel"));
        }
        break;

        case SOCStatusMessage.SV_GAME_CLIENT_FEATURES_NEEDED:
        {
            // Extract game name and missing client feature keynames,
            // and pop up an error message window.
            String errMsg;
            StringTokenizer st = new StringTokenizer(statusText, SOCMessage.sep2);
            try
            {
                errMsg = st.nextToken();
                final String gameName = st.nextToken();
                final String featsList = (st.hasMoreTokens()) ? st.nextToken() : "?";
                final String msgKey = (client.doesGameExist(gameName, true))
                    ? "pcli.gamelist.client_feats.cannot_join"
                        // "Cannot create game {0}\nThis client does not have required feature(s): {1}"
                    : "pcli.gamelist.client_feats.cannot_create";
                        // "Cannot join game {0}\nThis client does not have required feature(s): {1}"
                errMsg = client.strings.get(msgKey, gameName, featsList);
            }
            catch (Throwable t)
            {
                errMsg = statusText;  // fallback, not expected to happen
            }

            client.getMainDisplay().showErrorDialog(errMsg, client.strings.get("base.cancel"));
        }
        break;

        case SOCStatusMessage.SV_SERVER_SHUTDOWN:
        {
            handleBCASTTEXTMSG(statusText);
            client.getNet().ex = new RuntimeException(statusText);
            client.shutdownFromNetwork();
        }
        break;

        }
    }

    /**
     * handle the "join channel authorization" message
     * @param mes  the message
     */
    protected void handleJOINCHANNELAUTH(SOCJoinChannelAuth mes)
    {
        client.gotPassword = true;
        client.getMainDisplay().channelJoined(mes.getChannel());
    }

    /**
     * handle the "join channel" message
     * @param mes  the message
     */
    protected void handleJOINCHANNEL(SOCJoinChannel mes)
    {
        client.getMainDisplay().channelJoined(mes.getChannel(), mes.getNickname());
    }

    /**
     * handle the "channel members" message
     * @param mes  the message
     */
    protected void handleCHANNELMEMBERS(SOCChannelMembers mes)
    {
        client.getMainDisplay().channelMemberList(mes.getChannel(), mes.getMembers());
    }

    /**
     * handle the "new channel" message
     * @param mes  the message
     */
    protected void handleNEWCHANNEL(SOCNewChannel mes)
    {
        client.getMainDisplay().channelCreated(mes.getChannel());
    }

    /**
     * handle the "list of channels" message; this message indicates that
     * we're newly connected to the server, and is sent even if the server
     * isn't using {@link SOCFeatureSet#SERVER_CHANNELS}: Server connection is complete.
     * Unless {@code isPractice}, show {@link #MAIN_PANEL}.
     * @param mes  the message
     * @param isPractice is the server actually {@link ClientNetwork#practiceServer} (practice game)?
     */
    protected void handleCHANNELS(final SOCChannels mes, final boolean isPractice)
    {
        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                MainDisplay mdisp = client.getMainDisplay();
                mdisp.channelList(mes.getChannels(), isPractice);
                mdisp.repaintGameAndChannelLists();
            }
        });
    }

    /**
     * Handle a broadcast text message. Calls {@link #handleBCASTTEXTMSG(String)}.
     * @param mes  the message
     */
    protected void handleBCASTTEXTMSG(SOCBCastTextMsg mes)
    {
        handleBCASTTEXTMSG(mes.getText());
    }

    /**
     * Handle a broadcast message containing text as if it were {@link SOCBCastTextMsg}.
     * @param txt  the message text
     * @since 2.1.00
     */
    protected void handleBCASTTEXTMSG(final String txt)
    {
        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                client.getMainDisplay().chatMessageBroadcast(txt);

                for (PlayerClientListener pcl : client.getClientListeners().values())
                    pcl.messageBroadcast(txt);
            }
        });
    }

    /**
     * handle a text message received in a channel
     * @param mes  the message
     * @see #handleGAMETEXTMSG(SOCGameTextMsg)
     */
    protected void handleCHANNELTEXTMSG(SOCChannelTextMsg mes)
    {
        client.getMainDisplay().chatMessageReceived(mes.getChannel(), mes.getNickname(), mes.getText());
    }

    /**
     * handle the "leave channel" message
     * @param mes  the message
     */
    protected void handleLEAVECHANNEL(SOCLeaveChannel mes)
    {
        client.getMainDisplay().channelLeft(mes.getChannel(), mes.getNickname());
    }

    /**
     * handle the "delete channel" message
     * @param mes  the message
     */
    protected void handleDELETECHANNEL(SOCDeleteChannel mes)
    {
        final String chName = mes.getChannel();

        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                client.getMainDisplay().channelDeleted(chName);
            }
        });
    }

    /**
     * handle the "list of games" message
     * @param mes  the message
     */
    protected void handleGAMES(final SOCGames mes, final boolean isPractice)
    {
        // Any game's name in this msg may start with the "unjoinable" prefix
        // SOCGames.MARKER_THIS_GAME_UNJOINABLE.
        // We'll recognize and remove it in methods called from here.

        List<String> gameNames = mes.getGames();

        if (! isPractice)  // practiceServer's gameoption data is set up in handleVERSION
        {
            if (client.serverGames == null)
                client.serverGames = new SOCGameList(client.tcpServGameOpts.knownOpts);
            client.serverGames.addGames(gameNames, Version.versionNumber());

            // No more game-option info will be received,
            // because that's always sent before game names are sent.
            // We may still ask for GAMEOPTIONGETDEFAULTS if asking to create a game,
            // but that will happen when user clicks that button, not yet.
            client.tcpServGameOpts.noMoreOptions(false);
        }

        // update displayed list on AWT event thread, not network message thread,
        // to ensure right timing for repaint to avoid appearing empty.
        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                for (String gn : mes.getGames())
                    client.addToGameList(gn, null, false);

                client.getMainDisplay().repaintGameAndChannelLists();
            }
        });
    }

    /**
     * handle the "join game authorization" message: create new {@link SOCGame} and
     * {@link SOCPlayerInterface} so user can join the game
     * @param mes  the message
     * @param isPractice  if server is practiceServer (not normal tcp network)
     * @throws IllegalStateException if board size {@link SOCGameOption} "_BHW" isn't defined (unlikely internal error)
     */
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes, final boolean isPractice)
        throws IllegalStateException
    {
        if (! isPractice)
            client.gotPassword = true;

        final SOCGameOptionSet knownOpts =
            ((isPractice) ? client.practiceServGameOpts : client.tcpServGameOpts).knownOpts;
        final String gaName = mes.getGame();
        SOCGameOptionSet gameOpts;
        if (isPractice)
        {
            gameOpts = client.getNet().practiceServer.getGameOptions(gaName);
            if (gameOpts != null)
                gameOpts = new SOCGameOptionSet(gameOpts, false); // so _BHW change here won't change practiceServ's copy
        } else {
            if (client.serverGames != null)
                gameOpts = client.serverGames.parseGameOptions(gaName);
            else
                gameOpts = null;
        }

        final int bh = mes.getBoardHeight(), bw = mes.getBoardWidth();
        if ((bh != 0) || (bw != 0))
        {
            // Encode board size to pass through game constructor.
            // gameOpts won't be null, because bh, bw are from SOCBoardLarge which requires a gameopt to use.
            SOCGameOption opt = knownOpts.getKnownOption("_BHW", true);
            if (opt == null)
                throw new IllegalStateException("Internal error: Game opt _BHW not known");
            opt.setIntValue((bh << 8) | bw);
            if (gameOpts == null)
                gameOpts = new SOCGameOptionSet();  // unlikely: no-opts board has 0 height,width in message
            gameOpts.put(opt);
        }

        SOCGame ga = new SOCGame(gaName, gameOpts, knownOpts);
        if (ga != null)
        {
            ga.isPractice = isPractice;
            ga.serverVersion = (isPractice) ? Version.versionNumber() : client.sVersion;

            PlayerClientListener clientListener =
                client.getMainDisplay().gameJoined(ga, mes.getLayoutVS(), client.getGameReqLocalPrefs().get(gaName));
            client.getClientListeners().put(gaName, clientListener);
            client.games.put(gaName, ga);
        }
    }

    /**
     * handle the "join game" message
     * @param mes  the message
     */
    protected void handleJOINGAME(SOCJoinGame mes)
    {
        final String gn = mes.getGame();
        final String name = mes.getNickname();
        if (name == null)
            return;

        PlayerClientListener pcl = client.getClientListener(gn);
        pcl.playerJoined(name);
    }

    /**
     * handle the "leave game" message
     * @param mes  the message
     */
    protected void handleLEAVEGAME(SOCLeaveGame mes)
    {
        String gn = mes.getGame();
        SOCGame ga = client.games.get(gn);
        if (ga == null)
            return;

        final String name = mes.getNickname();
        final SOCPlayer player = ga.getPlayer(name);

        // Give the listener a chance to clean up while the player is still in the game
        PlayerClientListener pcl = client.getClientListener(gn);
        pcl.playerLeft(name, player);

        if (player != null)
        {
            //  This user was not a spectator.
            //  Remove first from listener, then from game data.
            ga.removePlayer(name, false);
        }
    }

    /**
     * handle the "new game" message
     * @param mes  the message
     */
    protected void handleNEWGAME(SOCNewGame mes, final boolean isPractice)
    {
        // Run in network message thread, not AWT event thread,
        // in case client is about to be auth'd to join this game:
        // messages must take effect in the order sent

        client.addToGameList(mes.getGame(), null, ! isPractice);
    }

    /**
     * handle the "delete game" message
     * @param mes  the message
     */
    protected void handleDELETEGAME(SOCDeleteGame mes, final boolean isPractice)
    {
        final String gaName = mes.getGame();

        // run on AWT event thread, not network thread, to avoid occasional ArrayIndexOutOfBoundsException
        // console stack trace (javax.swing.DefaultListModel.getElementAt) after deleteFromGameList

        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                final MainDisplay mdisp = client.getMainDisplay();
                if (! mdisp.deleteFromGameList(gaName, isPractice, false))
                    mdisp.deleteFromGameList(gaName, isPractice, true);

                PlayerClientListener pcl = client.getClientListener(gaName);
                if (pcl != null)
                    pcl.gameDisconnected(true, null);
            }
        });
    }

    /**
     * handle the "game members" message, the server's hint that it's almost
     * done sending us the complete game state in response to JOINGAME.
     * @param mes  the message
     */
    protected void handleGAMEMEMBERS(final SOCGameMembers mes)
    {
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        pcl.membersListed(mes.getMembers());
    }

    /**
     * handle the "game stats" message
     */
    protected void handleGAMESTATS(SOCGameStats mes)
    {
        final String gaName = mes.getGame();
        final int stype = mes.getStatType();

        SOCGame ga = client.games.get(gaName);
        if (ga != null)
            SOCDisplaylessPlayerClient.handleGAMESTATS(mes, ga);

        switch (stype)
        {
        case SOCGameStats.TYPE_PLAYERS:
            // If we're playing in a game, update the scores. (SOCPlayerInterface)
            // This is used to show the true scores, including hidden
            // victory-point cards, at the game's end.
            if (ga != null)
                client.updateGameEndStats(gaName, mes.getScores());
            break;

        case SOCGameStats.TYPE_TIMING:
            {
                // similar logic is in SOCDisplaylessPlayerClient.handleGAMESTATS

                final long[] stats = mes.getScores();
                final long timeCreated = stats[0], timeFinished = stats[2],
                    timeNow = System.currentTimeMillis() / 1000;
                if (timeCreated > timeNow)
                    return;
                final int durationFinishedSeconds =
                    (timeFinished > timeCreated) ? ((int) (timeFinished - timeCreated)) : 0;

                client.getMainDisplay().gameTimingStatsReceived
                    (gaName, timeCreated, (stats[1] == 1), durationFinishedSeconds);
                break;
            }
        }
    }

    /**
     * handle the "game text message" message.
     * Messages not from Server go to the chat area.
     * Messages from Server go to the game text window.
     * Urgent messages from Server (starting with ">>>") also go to the chat area,
     * which has less activity, so they are harder to miss.
     *
     * @param mes  the message
     * @see #handleGAMESERVERTEXT(SOCGameServerText)
     * @see #handleCHANNELTEXTMSG(SOCChannelTextMsg)
     */
    protected void handleGAMETEXTMSG(SOCGameTextMsg mes)
    {
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;

        String fromNickname = mes.getNickname();
        if (fromNickname.equals(SOCGameTextMsg.SERVERNAME))  // for pre-2.0.00 servers not using SOCGameServerText
            fromNickname = null;
        pcl.messageReceived(fromNickname, mes.getText());
    }

    /**
     * handle the "player sitting down" message
     * @param mes  the message
     */
    protected void handleSITDOWN(final SOCSitDown mes)
    {
        /**
         * tell the game that a player is sitting
         */
        final SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        final int pn = mes.getPlayerNumber();
        final String plName = mes.getNickname();
        SOCPlayer player = null;

        ga.takeMonitor();
        try
        {
            ga.addPlayer(plName, pn);
            player = ga.getPlayer(pn);
            player.setRobotFlag(mes.isRobot(), false);
        }
        catch (Exception e)
        {
            System.out.println("Exception caught - " + e);
            e.printStackTrace();

            return;
        }
        finally
        {
            ga.releaseMonitor();
        }

        final boolean playerIsClient = client.getNickname(ga.isPractice).equals(plName);

        if (playerIsClient
            && (ga.isPractice || (client.sVersion >= SOCDevCardAction.VERSION_FOR_SITDOWN_CLEARS_INVENTORY)))
        {
            // server is about to send our dev-card inventory contents
            player.getInventory().clear();
        }

        /**
         * tell the GUI that a player is sitting
         */
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        pcl.playerSitdown(pn, plName);

        /**
         * if player is client, use face icon from last requested change instead of default
         * (this is so that an old face isn't requested anew); skip if reset.
         */
        if (playerIsClient && ! ga.isBoardReset())
        {
            player.setFaceId(client.lastFaceChange);
            gms.changeFace(ga, client.lastFaceChange);
        }
    }

    /**
     * Handle the old "board layout" message (original 4-player board, no options).
     * Most game boards will call {@link #handleBOARDLAYOUT2(SOCBoardLayout2)} instead.
     * @param mes  the message
     */
    protected void handleBOARDLAYOUT(SOCBoardLayout mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;

        SOCDisplaylessPlayerClient.handleBOARDLAYOUT(mes, ga);

        PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl != null)
            pcl.boardLayoutUpdated();
    }

    /**
     * echo the server ping, to ensure we're still connected.
     * Ping may be a keepalive check or an attempt to kick by another
     * client with the same nickname; may call
     * {@link SOCPlayerClient#shutdownFromNetwork()} if so.
     *<P>
     * (message ignored before v1.1.08)
     * @since 1.1.08
     */
    private void handleSERVERPING(SOCServerPing mes, final boolean isPractice)
    {
        int timeval = mes.getSleepTime();
        if (timeval != -1)
        {
            gms.put(mes.toCmd(), isPractice);
        } else {
            client.getNet().ex = new RuntimeException(client.strings.get("pcli.error.kicked.samename"));
                // "Kicked by player with same name."
            client.shutdownFromNetwork();
        }
    }

    /**
     * Handle the "board layout" message, in its usual format.
     * (Some simple games can use the old {@link #handleBOARDLAYOUT(SOCBoardLayout)} instead.)
     * @param mes  the message
     * @since 1.1.08
     */
    protected void handleBOARDLAYOUT2(SOCBoardLayout2 mes)
    {
        final String gaName = mes.getGame();
        if (SOCDisplaylessPlayerClient.handleBOARDLAYOUT2(mes, client.games.get(gaName)))
        {
            PlayerClientListener pcl = client.getClientListener(gaName);
            if (pcl != null)
                pcl.boardLayoutUpdated();
        }
    }

    /**
     * handle the "start game" message; calls {@link #handleGAMESTATE(SOCGame, int, boolean, boolean)}
     * which will call {@link PlayerClientListener#gameStarted()} if needed.
     * @param mes  the message
     */
    protected void handleSTARTGAME(SOCStartGame mes)
    {
        final SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        handleGAMESTATE(ga, mes.getGameState(), false, false);
    }

    /**
     * Handle the "game state" message; calls {@link #handleGAMESTATE(SOCGame, int, boolean, boolean)}.
     * @param mes  the message
     */
    protected void handleGAMESTATE(SOCGameState mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        handleGAMESTATE(ga, mes.getState(), false, false);
    }

    /**
     * Handle game state message: Update {@link SOCGame} and {@link PlayerClientListener} if any.
     * Call for any message type which contains a Game State field.
     *<P>
     * Checks current {@link SOCGame#getGameState()}; if current state is {@link SOCGame#NEW NEW}
     * and {@code newState != NEW}, calls {@link PlayerClientListener#gameStarted()} before
     * its usual {@link PlayerClientListener#gameStateChanged(int)} call.
     *<P>
     * If current state is &lt; {@link SOCGame#ROLL_OR_CARD} and {@code newState == ROLL_OR_CARD},
     * and server older than v2.5.00 hasn't sent {@link SOCTurn} to increment {@link SOCGame#getRoundCount()} from 0,
     * calls {@link SOCGame#updateAtTurn()} to do so.
     *
     * @param ga  Game to update state; not null
     * @param newState  New state from message, like {@link SOCGame#ROLL_OR_CARD}, or 0. Does nothing if 0.
     * @param isForTurn  True if being called from {@link #handleTURN(SOCTurn)}.
     *     If true, will avoid calling {@link SOCGame#updateAtTurn()}; caller does so instead.
     * @param isForDecline  True if being called from {@link #handleDECLINEPLAYERREQUEST(SOCDeclinePlayerRequest)}.
     *     If true, won't call {@link SOCGame#setGameState(int)} if {@code newState} same as current state.
     *     Also passed to {@code PlayerClientListener.gameStateChanged(..)}.
     * @see #handleGAMESTATE(SOCGameState)
     * @since 2.0.00
     */
    protected void handleGAMESTATE
        (final SOCGame ga, final int newState, final boolean isForTurn, final boolean isForDecline)
    {
        if (newState == 0)
            return;

        final int gaState = ga.getGameState();
        final boolean gameStarting = (gaState == SOCGame.NEW) && (newState != SOCGame.NEW);

        if ((newState != gaState) || ! isForDecline)
            ga.setGameState(newState);

        if ((! (gameStarting || isForTurn))
            && (gaState < SOCGame.ROLL_OR_CARD) && (newState == SOCGame.ROLL_OR_CARD)
            && (ga.getRoundCount() == 0))
        {
            // Servers older than v2.5.00 didn't always send SOCTurn before game's first turn
            // (SOCTurn.VERSION_FOR_SEND_BEGIN_FIRST_TURN)
            ga.updateAtTurn();
        }

        PlayerClientListener pcl = client.getClientListener(ga.getName());
        if (pcl == null)
            return;

        if (gameStarting)
        {
            // call here, not in handleSTARTGAME, in case we joined a game in progress
            pcl.gameStarted();
        }
        pcl.gameStateChanged(newState, false);
    }

    /**
     * handle the "turn" message
     * @param mes  the message
     */
    protected void handleTURN(SOCTurn mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;

        handleGAMESTATE(ga, mes.getGameState(), true, false);

        final int pnum = mes.getPlayerNumber();
        ga.setCurrentPlayerNumber(pnum);
        ga.updateAtTurn();
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        pcl.playerTurnSet(pnum);
    }

    /**
     * handle the "player information" message: Finds game and its {@link PlayerClientListener} by name
     * and calls {@link #handlePLAYERELEMENT(PlayerClientListener, SOCGame, SOCPlayer, int, int, PEType, int, boolean)}
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handlePLAYERELEMENTS(SOCPlayerElements mes)
    {
        final SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        final PlayerClientListener pcl = client.getClientListener(mes.getGame());
        final int pn = mes.getPlayerNumber();
        final SOCPlayer pl = (pn != -1) ? ga.getPlayer(pn) : null;
        final int action = mes.getAction();
        final int[] etypes = mes.getElementTypes(), amounts = mes.getAmounts();

        for (int i = 0; i < etypes.length; ++i)
            handlePLAYERELEMENT
                (pcl, ga, pl, pn, action, PEType.valueOf(etypes[i]), amounts[i], false);

        if ((action == SOCPlayerElement.SET) && (etypes.length == 5) && (etypes[0] == SOCResourceConstants.CLAY)
            && (pl != null) && (ga.getGameState() == SOCGame.ROLL_OR_CARD))
            // dice roll results: when sent all known resources, clear UNKNOWN to 0
            pl.getResources().setAmount(0, SOCResourceConstants.UNKNOWN);
    }

    /**
     * handle the "player information" message: Finds game and its {@link PlayerClientListener} by name
     * and calls {@link #handlePLAYERELEMENT(PlayerClientListener, SOCGame, SOCPlayer, int, int, PEType, int, boolean)}.
     *<P>
     * To update game information, that method defaults to calling
     * {@link SOCDisplaylessPlayerClient#handlePLAYERELEMENT_simple(SOCGame, SOCPlayer, int, int, PEType, int, String)}
     * or {@link SOCDisplaylessPlayerClient#handlePLAYERELEMENT_numRsrc(SOCPlayer, int, int, int)}
     * for elements that don't need special handling.
     *
     * @param mes  the message
     */
    protected void handlePLAYERELEMENT(SOCPlayerElement mes)
    {
        final SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        final int pn = mes.getPlayerNumber();
        final int action = mes.getAction(), amount = mes.getAmount();
        final PEType etype = PEType.valueOf(mes.getElementType());

        handlePLAYERELEMENT
            (client.getClientListener(mes.getGame()), ga, null, pn, action, etype, amount, mes.isNews());
    }

    /**
     * Handle a player information update from a {@link SOCPlayerElement} or {@link SOCPlayerElements} message.
     * Update game information, then update {@code pcl} display if appropriate.
     *<P>
     * To update game information, defaults to calling
     * {@link SOCDisplaylessPlayerClient#handlePLAYERELEMENT_simple(SOCGame, SOCPlayer, int, int, PEType, int, String)}
     * or {@link SOCDisplaylessPlayerClient#handlePLAYERELEMENT_numRsrc(SOCPlayer, int, int, int)}
     * for elements that don't need special handling for this client class.
     *
     * @param pcl  PlayerClientListener for {@code ga}, to update display if not null
     * @param ga   Game to update; does nothing if null
     * @param pl   Player to update; some elements take null. If null and {@code pn != -1}, will find {@code pl}
     *     using {@link SOCGame#getPlayer(int) ga.getPlayer(pn)}.
     * @param pn   Player number from message (sometimes -1 for none or all)
     * @param action   {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN GAIN},
     *     or {@link SOCPlayerElement#LOSE LOSE}
     * @param etype  Element type, such as {@link PEType#SETTLEMENTS} or {@link PEType#NUMKNIGHTS}.
     *     Does nothing if {@code null}.
     * @param amount  The new value to set, or the delta to gain/lose
     * @param isNews  True if message's isNews() flag is set; used when calling
     *     {@link PlayerClientListener#playerElementUpdated(SOCPlayer, soc.client.PlayerClientListener.UpdateType, boolean, boolean)}
     * @since 2.0.00
     */
    private void handlePLAYERELEMENT
        (final PlayerClientListener pcl, final SOCGame ga, SOCPlayer pl, final int pn,
         final int action, final PEType etype, final int amount, final boolean isNews)
    {
        if ((ga == null) || (etype == null))
            return;

        if ((pl == null) && (pn != -1))
            pl = ga.getPlayer(pn);

        PlayerClientListener.UpdateType utype = null;  // If not null, update this type's amount display

        switch (etype)
        {
        case ROADS:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.ROAD, amount);
            utype = PlayerClientListener.UpdateType.Road;
            break;

        case SETTLEMENTS:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.SETTLEMENT, amount);
            utype = PlayerClientListener.UpdateType.Settlement;
            break;

        case CITIES:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.CITY, amount);
            utype = PlayerClientListener.UpdateType.City;
            break;

        case SHIPS:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.SHIP, amount);
            utype = PlayerClientListener.UpdateType.Ship;
            break;

        case NUMKNIGHTS:
            // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
            {
                final SOCPlayer oldLargestArmyPlayer = ga.getPlayerWithLargestArmy();
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numKnights
                    (ga, pl, action, amount);
                utype = PlayerClientListener.UpdateType.Knight;

                // Check for change in largest-army player; update handpanels'
                // LARGESTARMY and VICTORYPOINTS counters if so, and
                // announce with text message.
                pcl.largestArmyRefresh(oldLargestArmyPlayer, ga.getPlayerWithLargestArmy());
            }

        break;

        case CLAY:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.CLAY, amount);
            utype = PlayerClientListener.UpdateType.Clay;
            break;

        case ORE:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.ORE, amount);
            utype = PlayerClientListener.UpdateType.Ore;
            break;

        case SHEEP:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.SHEEP, amount);
            utype = PlayerClientListener.UpdateType.Sheep;
            break;

        case WHEAT:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.WHEAT, amount);
            utype = PlayerClientListener.UpdateType.Wheat;
            break;

        case WOOD:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.WOOD, amount);
            utype = PlayerClientListener.UpdateType.Wood;
            break;

        case UNKNOWN_RESOURCE:
            /**
             * Note: if losing unknown resources, we first
             * convert player's known resources to unknown resources,
             * then remove mes's unknown resources from player.
             */
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.UNKNOWN, amount);
            utype = PlayerClientListener.UpdateType.Unknown;
            break;

        case ASK_SPECIAL_BUILD:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (ga, pl, pn, action, etype, amount, null);
            // This case is not really an element update, so route as a 'request'
            pcl.requestedSpecialBuild(pl);
            break;

        case RESOURCE_COUNT:
            if (amount != pl.getResources().getTotal())
            {
                // Update count if possible; convert known to unknown if needed.
                // For our own player, server sends resource specifics, not just total count

                boolean isClientPlayer = pl.getName().equals(client.getNickname(ga.isPractice));
                if (! isClientPlayer)
                {
                    SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                        (ga, pl, pn, action, etype, amount, null);
                    pcl.playerResourcesUpdated(pl);
                }
            }
            break;

        case NUM_UNDOS_REMAINING:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (ga, pl, pn, action, etype, amount, null);
            utype = PlayerClientListener.UpdateType.UndosRemaining;
            break;

        case NUM_PICK_GOLD_HEX_RESOURCES:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (ga, pl, pn, action, etype, amount, null);
            pcl.requestedGoldResourceCountUpdated(pl, amount);
            break;

        case SCENARIO_SVP:
            pl.setSpecialVP(amount);
            utype = PlayerClientListener.UpdateType.SpecialVictoryPoints;
            break;

        case SCENARIO_CLOTH_COUNT:
            if (pn != -1)
            {
                pl.setCloth(amount);
            } else {
                ((SOCBoardLarge) (ga.getBoard())).setCloth(amount);
            }
            utype = PlayerClientListener.UpdateType.Cloth;
            break;

        case SCENARIO_WARSHIP_COUNT:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (ga, pl, pn, action, etype, amount, null);
            utype = PlayerClientListener.UpdateType.Warship;
            break;

        default:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (ga, pl, pn, action, etype, amount, null);
        }

        if ((pcl != null) && (utype != null))
        {
            if (! isNews)
                pcl.playerElementUpdated(pl, utype, false, false);
            else if (action == SOCPlayerElement.GAIN)
                pcl.playerElementUpdated(pl, utype, true, false);
            else
                pcl.playerElementUpdated(pl, utype, false, true);
        }
    }

    /**
     * Handle the GameElements message: Finds game by name, and loops calling
     * {@link #handleGAMEELEMENT(SOCGame, GEType, int)}.
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handleGAMEELEMENTS(final SOCGameElements mes)
    {
        final SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        final int[] etypes = mes.getElementTypes(), values = mes.getValues();
        for (int i = 0; i < etypes.length; ++i)
            handleGAMEELEMENT(ga, GEType.valueOf(etypes[i]), values[i]);
    }

    /**
     * Update one game element field from a {@link SOCGameElements} message,
     * then update game's {@link PlayerClientListener} display if appropriate.
     *<P>
     * To update game information, calls
     * {@link SOCDisplaylessPlayerClient#handleGAMEELEMENT(SOCGame, GEType, int)}.
     *
     * @param ga   Game to update; does nothing if null
     * @param etype  Element type, such as {@link GEType#ROUND_COUNT} or {@link GEType#DEV_CARD_COUNT}.
     *     Does nothing if {@code null}.
     * @param value  The new value to set
     * @since 2.0.00
     */
    @SuppressWarnings("incomplete-switch")
    protected void handleGAMEELEMENT
        (final SOCGame ga, final GEType etype, final int value)
    {
        if ((ga == null) || (etype == null))
            return;

        final PlayerClientListener pcl = client.getClientListener(ga.getName());

        // A few etypes need to give PCL the old and new values.
        // For those, update game state and PCL together and return.
        if (pcl != null)
        {
            switch (etype)
            {
            // SOCGameElements.GEType.ROUND_COUNT:
            // Doesn't need a case here because it's sent only during joingame;
            // SOCBoardPanel will check ga.getRoundCount() as part of joingame

            case LARGEST_ARMY_PLAYER:
            {
                SOCPlayer oldLargestArmyPlayer = ga.getPlayerWithLargestArmy();
                SOCDisplaylessPlayerClient.handleGAMEELEMENT(ga, etype, value);
                SOCPlayer newLargestArmyPlayer = ga.getPlayerWithLargestArmy();

                // Update player victory points; check for and announce change in largest army
                pcl.largestArmyRefresh(oldLargestArmyPlayer, newLargestArmyPlayer);

                return;
            }

            case LONGEST_ROAD_PLAYER:
            {
                SOCPlayer oldLongestRoadPlayer = ga.getPlayerWithLongestRoad();
                SOCDisplaylessPlayerClient.handleGAMEELEMENT(ga, etype, value);
                SOCPlayer newLongestRoadPlayer = ga.getPlayerWithLongestRoad();

                // Update player victory points; check for and announce change in longest road
                pcl.longestRoadRefresh(oldLongestRoadPlayer, newLongestRoadPlayer);

                return;
            }
            }
        }

        SOCDisplaylessPlayerClient.handleGAMEELEMENT(ga, etype, value);

        if (pcl == null)
            return;

        switch (etype)
        {
        case DEV_CARD_COUNT:
            pcl.devCardDeckUpdated();
            break;

        case CURRENT_PLAYER:
            pcl.playerTurnSet(value);
            break;
        }
    }

    /**
     * handle "resource count" message
     * @param mes  the message
     */
    protected void handleRESOURCECOUNT(SOCResourceCount mes)
    {
        final SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        handlePLAYERELEMENT
            (client.getClientListener(mes.getGame()), ga, null, mes.getPlayerNumber(),
             SOCPlayerElement.SET, PEType.RESOURCE_COUNT, mes.getCount(), false);
    }

    /**
     * handle the "dice result" message
     * @param mes  the message
     */
    protected void handleDICERESULT(SOCDiceResult mes)
    {
        final String gameName = mes.getGame();
        SOCGame ga = client.games.get(gameName);
        if (ga == null)
            return;

        final int cpn = ga.getCurrentPlayerNumber();
        SOCPlayer p = null;
        if (cpn >= 0)
            p = ga.getPlayer(cpn);

        final int roll = mes.getResult();
        final SOCPlayer player = p;

        // update game state
        ga.setCurrentDice(roll);

        // notify listener
        PlayerClientListener listener = client.getClientListener(gameName);
        listener.diceRolled(player, roll);
    }

    /**
     * handle the "put piece" message
     * @param mes  the message
     */
    protected void handlePUTPIECE(SOCPutPiece mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        final SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        final int coord = mes.getCoordinates();
        final int ptype = mes.getPieceType();

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;
        pcl.playerPiecePlaced(player, coord, ptype);
    }

    /**
     * handle the rare "cancel build request" message; usually not sent from
     * server to client.
     *<P>
     * - When sent from client to server, CANCELBUILDREQUEST means the player has changed
     *   their mind about spending resources to build a piece.  Only allowed during normal
     *   game play (PLACING_ROAD, PLACING_SETTLEMENT, or PLACING_CITY).
     *<P>
     *  When sent from server to client:
     *<P>
     * - During game startup (START1B or START2B): <BR>
     *       Sent from server, CANCELBUILDREQUEST means the current player
     *       wants to undo the placement of their initial settlement.
     *<P>
     * - During piece placement (PLACING_ROAD, PLACING_CITY, PLACING_SETTLEMENT,
     *                           PLACING_FREE_ROAD1 or PLACING_FREE_ROAD2):
     *<P>
     *      Sent from server, CANCELBUILDREQUEST means the player has sent
     *      an illegal PUTPIECE (bad building location). Humans can probably
     *      decide a better place to put their road, but robots must cancel
     *      the build request and decide on a new plan.
     *<P>
     *      Our client can ignore this case, because the server also sends a text
     *      message that the human player is capable of reading and acting on.
     *      For convenience, during initial placement
     *      {@link PlayerClientListener#buildRequestCanceled(SOCPlayer)}
     *      is called to reset things like {@link SOCBoardPanel} hovering pieces.
     *
     * @param mes  the message
     * @since 1.1.00
     */
    protected void handleCANCELBUILDREQUEST(SOCCancelBuildRequest mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        final int ptype = mes.getPieceType();
        final SOCPlayer pl = ga.getPlayer(ga.getCurrentPlayerNumber());

        if (ptype >= SOCPlayingPiece.SETTLEMENT)
        {
            final int sta = ga.getGameState();
            if ((sta != SOCGame.START1B) && (sta != SOCGame.START2B) && (sta != SOCGame.START3B))
            {
                // The human player gets a text message from the server informing
                // about the bad piece placement.  So, we can ignore this message type.
                return;
            }

            if (ptype == SOCPlayingPiece.SETTLEMENT)
            {
                SOCSettlement pp = new SOCSettlement(pl, pl.getLastSettlementCoord(), null);
                ga.undoPutInitSettlement(pp);
            }
        } else {
            // ptype is -3 (SOCCancelBuildRequest.INV_ITEM_PLACE_CANCEL)
        }

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        pcl.buildRequestCanceled(pl);
    }

    /**
     * A player has discarded resources. Update player data and announce the discard.
     * @param mes  the message
     * @since 2.5.00
     */
    protected void handleDISCARD(final SOCDiscard mes)
    {
        final String gaName = mes.getGame();
        final SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;

        final SOCPlayer pl = SOCDisplaylessPlayerClient.handleDISCARD(mes, ga);
        if (pl == null)
            return;

        final PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl == null)
            return;
        pcl.playerDiscarded(pl, mes.getResources());
    }

    /**
     * handle the "robber moved" or "pirate moved" message.
     * @param mes  the message
     */
    protected void handleMOVEROBBER(SOCMoveRobber mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        /**
         * Note: Don't call ga.moveRobber() because that will call the
         * functions to do the stealing.  We just want to say where
         * the robber moved without seeing if something was stolen.
         */
        ga.setPlacingRobberForKnightCard(false);
        int newHex = mes.getCoordinates();
        final boolean isPirate = (newHex <= 0);
        if (! isPirate)
        {
            ga.getBoard().setRobberHex(newHex, true);
        } else {
            newHex = -newHex;
            ((SOCBoardLarge) ga.getBoard()).setPirateHex(newHex, true);
        }

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        pcl.robberMoved(newHex, isPirate);
    }

    /**
     * handle the "discard request" message
     * @param mes  the message
     */
    protected void handleDISCARDREQUEST(SOCDiscardRequest mes)
    {
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        pcl.requestedDiscard(mes.getNumberOfDiscards());
    }

    /**
     * handle the "choose player request" message
     * @param mes  the message
     */
    protected void handleCHOOSEPLAYERREQUEST(SOCChoosePlayerRequest mes)
    {
        SOCGame game = client.games.get(mes.getGame());
        final int maxPl = game.maxPlayers;
        final boolean[] ch = mes.getChoices();

        List<SOCPlayer> choices = new ArrayList<SOCPlayer>();
        for (int i = 0; i < maxPl; i++)
        {
            if (ch[i])
            {
                SOCPlayer p = game.getPlayer(i);
                choices.add(p);
            }
        }

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        pcl.requestedChoosePlayer(choices, mes.canChooseNone());
    }

    /**
     * The server wants this player to choose to rob cloth or rob resources,
     * after moving the pirate ship.  Added 2012-11-17 for v2.0.00.
     */
    protected void handleCHOOSEPLAYER(SOCChoosePlayer mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        int victimPlayerNumber = mes.getChoice();
        SOCPlayer player = ga.getPlayer(victimPlayerNumber);

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        pcl.requestedChooseRobResourceType(player);
    }

    /**
     * Handle the "robbery result" message.
     * @param mes  the message
     * @param ga  game object for {@link SOCMessageForGame#getGame() mes.getGame()}
     * @since 2.5.00
     */
    protected void handleROBBERYRESULT(final SOCRobberyResult mes, SOCGame ga)
    {
        SOCDisplaylessPlayerClient.handleROBBERYRESULT(mes, ga);

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl != null)
            pcl.reportRobberyResult
                (mes.perpPN, mes.victimPN, mes.resType, mes.resSet, mes.peType,
                 mes.isGainLose, mes.amount, mes.victimAmount, mes.extraValue);
    }

    /**
     * handle the "bank trade" message from a v2.0.00 or newer server.
     * Calls {@link SOCDisplaylessPlayerClient#handleBANKTRADE(SOCBankTrade, SOCGame)}
     * if server is v2.5.00 or newer ({@link SOCBankTrade#VERSION_FOR_OMIT_PLAYERELEMENTS}).
     *
     * @param mes  the message
     * @param isPractice  Is the server {@link ClientNetwork#practiceServer}, not remote?
     * @since 2.0.00
     */
    protected void handleBANKTRADE(final SOCBankTrade mes, final boolean isPractice)
    {
        final String gaName = mes.getGame();
        final SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;

        if (isPractice || (client.sVersion >= SOCBankTrade.VERSION_FOR_OMIT_PLAYERELEMENTS))
            SOCDisplaylessPlayerClient.handleBANKTRADE(mes, ga);

        PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl == null)
            return;

        pcl.playerBankTrade(ga.getPlayer(mes.getPlayerNumber()), mes.getGiveSet(), mes.getGetSet());
    }

    /**
     * handle the "make offer" message
     * @param mes  the message
     */
    protected void handleMAKEOFFER(final SOCMakeOffer mes)
    {
        final String gaName = mes.getGame();
        final SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;

        SOCTradeOffer offer = mes.getOffer();
        final int fromPN = offer.getFrom();
        SOCPlayer from = ga.getPlayer(fromPN);
        from.setCurrentOffer(offer);

        PlayerClientListener pcl = client.getClientListener(gaName);
        pcl.requestedTrade(from, fromPN);
    }

    /**
     * handle the "clear offer" message
     * @param mes  the message
     */
    protected void handleCLEAROFFER(final SOCClearOffer mes)
    {
        final SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        final int pn = mes.getPlayerNumber();
        SOCPlayer player = null;
        if (pn != -1)
        {
            player = ga.getPlayer(pn);
            player.setCurrentOffer(null);
        } else {
            for (int i = 0; i < ga.maxPlayers; ++i)
                ga.getPlayer(i).setCurrentOffer(null);
        }

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        pcl.requestedTradeClear(player, false);
    }

    /**
     * handle the "reject offer"/"disallowed trade" message:
     * a player has rejected an offer,
     * or server has disallowed our trade-related request.
     * @param mes  the message
     */
    protected void handleREJECTOFFER(SOCRejectOffer mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        final int pn = mes.getPlayerNumber();
        SOCPlayer player = (pn >= 0) ? ga.getPlayer(pn) : null;

        PlayerClientListener pcl = client.getClientListener(mes.getGame());

        int rc = mes.getReasonCode();
        switch (rc)
        {
        case 0:
            pcl.requestedTradeRejection(player);
            break;

        case SOCRejectOffer.REASON_NOT_YOUR_TURN:
            pcl.playerTradeDisallowed(-1, false, true);
            break;

        case SOCRejectOffer.REASON_CANNOT_MAKE_OFFER:
            pcl.playerTradeDisallowed(pn, true, false);
            break;

        default:
            pcl.playerTradeDisallowed(pn, false, false);
        }
    }

    /**
     * handle the "accept offer" message
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handleACCEPTOFFER(final SOCAcceptOffer mes)
    {
        final String gaName = mes.getGame();
        final SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;

        SOCDisplaylessPlayerClient.handleACCEPTOFFER(mes, ga);

        PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl == null)
            return;

        pcl.playerTradeAccepted
            (ga.getPlayer(mes.getOfferingNumber()), ga.getPlayer(mes.getAcceptingNumber()),
             mes.getResToOfferingPlayer(), mes.getResToAcceptingPlayer());
    }

    /**
     * handle the "clear trade message" message
     * @param mes  the message
     */
    protected void handleCLEARTRADEMSG(SOCClearTradeMsg mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        int pn = mes.getPlayerNumber();
        SOCPlayer player = null;
        if (pn != -1)
            player = ga.getPlayer(pn);

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        pcl.requestedTradeReset(player);
    }

    /**
     * Handle the "development card action" message, which may have multiple cards.
     * Updates game data by calling {@link #handleDEVCARDACTION(SOCGame, SOCPlayer, boolean, int, int)},
     * then calls {@link PlayerClientListener#playerDevCardsUpdated(SOCPlayer, boolean)}.
     *<P>
     * If message is about the player's own cards at end of game when server reveals all VP cards
     * ({@link SOCDevCardAction#ADD_OLD} in state {@link SOCGame#OVER}),
     * returns immediately and doesn't call those methods.
     *
     * @param mes  the message
     */
    protected void handleDEVCARDACTION(final boolean isPractice, final SOCDevCardAction mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        final int pn = mes.getPlayerNumber();
        final SOCPlayer player = ga.getPlayer(pn);
        final PlayerClientListener pcl = client.getClientListener(mes.getGame());
        final boolean isClientPlayer = (pcl != null) && (pn >= 0) && (pn == pcl.getClientPlayerNumber());
        final int act = mes.getAction();

        if (isClientPlayer && (act == SOCDevCardAction.ADD_OLD) && (ga.getGameState() == SOCGame.OVER))
        {
            return;
        }

        final List<Integer> ctypes = mes.getCardTypes();
        if (ctypes != null)
        {
            for (final int ctype : ctypes)
                handleDEVCARDACTION(ga, player, isClientPlayer, act, ctype);
        } else {
            int ctype = mes.getCardType();
            if ((! isPractice) && (client.sVersion < SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES))
            {
                if (ctype == SOCDevCardConstants.KNIGHT_FOR_VERS_1_X)
                    ctype = SOCDevCardConstants.KNIGHT;
                else if (ctype == SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X)
                    ctype = SOCDevCardConstants.UNKNOWN;
            }

            handleDEVCARDACTION(ga, player, isClientPlayer, act, ctype);
        }

        if (pcl != null)
            pcl.playerDevCardsUpdated(player, (act == SOCDevCardAction.ADD_OLD));
    }

    /**
     * Handle one dev card's game data update for {@link #handleDEVCARDACTION(boolean, SOCDevCardAction)}.
     * In case this is part of a list of cards, does not call
     * {@link PlayerClientListener#playerDevCardsUpdated(SOCPlayer, boolean)}: Caller must do so afterwards.
     * For {@link SOCDevCardAction#PLAY}, calls {@link SOCPlayer#updateDevCardsPlayed(int, boolean)}.
     *
     * @param ga  Game being updated
     * @param player  Player in {@code ga} being updated
     * @param isClientPlayer  True if {@code player} is our client playing in {@code ga}
     * @param act  Action being done: {@link SOCDevCardAction#DRAW}, {@link SOCDevCardAction#PLAY PLAY},
     *     {@link SOCDevCardAction#ADD_OLD ADD_OLD}, or {@link SOCDevCardAction#ADD_NEW ADD_NEW}
     * @param ctype  Type of development card from {@link SOCDevCardConstants}
     * @see SOCDisplaylessPlayerClient#handleDEVCARDACTION(SOCGame, SOCPlayer, int, int)
     */
    protected void handleDEVCARDACTION
        (final SOCGame ga, final SOCPlayer player, final boolean isClientPlayer, final int act, final int ctype)
    {
        // if you change this method, consider changing SOCDisplaylessPlayerClient.handleDEVCARDACTION
        // and SOCRobotBrain.handleDEVCARDACTION too

        switch (act)
        {
        case SOCDevCardAction.DRAW:
            player.getInventory().addDevCard(1, SOCInventory.NEW, ctype);
            break;

        case SOCDevCardAction.PLAY:
            player.getInventory().removeDevCard(SOCInventory.OLD, ctype);
            player.updateDevCardsPlayed(ctype, false);
            if ((ctype == SOCDevCardConstants.KNIGHT) && ! ga.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
                ga.setPlacingRobberForKnightCard(true);
            break;

        case SOCDevCardAction.ADD_OLD:
            player.getInventory().addDevCard(1, SOCInventory.OLD, ctype);
            break;

        case SOCDevCardAction.ADD_NEW:
            player.getInventory().addDevCard(1, SOCInventory.NEW, ctype);
            break;
        }
    }

    /**
     * handle the "set played dev card flag" message
     * @param mes  the message
     */
    protected void handleSETPLAYEDDEVCARD(SOCSetPlayedDevCard mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
            (ga, null, mes.getPlayerNumber(), SOCPlayerElement.SET,
             PEType.PLAYED_DEV_CARD_FLAG, mes.hasPlayedDevCard() ? 1 : 0, null);
    }

    /**
     * Handle the "Player has Picked Resources" message by updating player resource data.
     * @param mes  the message
     * @param ga  Game to update
     * @since 2.5.00
     */
    public void handlePICKRESOURCES
        (final SOCPickResources mes, final SOCGame ga)
    {
        if (! SOCDisplaylessPlayerClient.handlePICKRESOURCES(mes, ga))
            return;

        PlayerClientListener pcl = client.getClientListener(ga.getName());
        if (pcl != null)
            pcl.playerPickedResources
                (ga.getPlayer(mes.getPlayerNumber()), mes.getResources(), mes.getReasonCode());
    }

    /**
     * handle the "list of potential settlements" message
     * @param mes  the message
     * @throws IllegalStateException if the board has
     *     {@link SOCBoardLarge#getAddedLayoutPart(String) SOCBoardLarge.getAddedLayoutPart("AL")} != {@code null} but
     *     badly formed (node list number 0, or a node list number not followed by a land area number).
     *     This Added Layout Part is rarely used, and that would be discovered quickly while testing
     *     the board layout that contained it (see TestBoardLayout.testSingleLayout).
     */
    protected void handlePOTENTIALSETTLEMENTS(SOCPotentialSettlements mes)
            throws IllegalStateException
    {
        final String gaName = mes.getGame();
        SOCDisplaylessPlayerClient.handlePOTENTIALSETTLEMENTS(mes, client.games.get(gaName));

        PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl != null)
            pcl.boardPotentialsUpdated();
    }

    /**
     * handle the "change face" message
     * @param mes  the message
     */
    protected void handleCHANGEFACE(SOCChangeFace mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        player.setFaceId(mes.getFaceId());
        pcl.playerFaceChanged(player, mes.getFaceId());
    }

    /**
     * handle the "reject connection" message
     * @param mes  the message
     */
    protected void handleREJECTCONNECTION(SOCRejectConnection mes)
    {
        client.getNet().disconnect();

        String txt = mes.getText();
        if (client.sVersion > 0)
        {
            StringBuilder sb = new StringBuilder
                (client.strings.get("pcli.error.server.disconnected_by_version", Version.version(client.sVersion)));
                    // "Disconnected by server version {0}:"
            sb.append('\n').append(txt);
            txt = sb.toString();
        }
        client.getMainDisplay().showErrorPanel(txt, (client.getNet().ex_P == null));
    }

    /**
     * handle the "set seat lock" message
     * @param mes  the message
     */
    protected void handleSETSEATLOCK(SOCSetSeatLock mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;

        final SOCGame.SeatLockState[] sls = mes.getLockStates();
        if (sls == null)
            ga.setSeatLock(mes.getPlayerNumber(), mes.getLockState());
        else
            ga.setSeatLocks(sls);

        PlayerClientListener pcl = client.getClientListener(gaName);
        pcl.seatLockUpdated();
    }

    /**
     * handle the "roll dice prompt" message;
     *   if we're in a game and we're the dice roller,
     *   either set the auto-roll timer, or prompt to roll or choose card.
     *
     * @param mes  the message
     * @since 1.1.00
     */
    protected void handleROLLDICEPROMPT(SOCRollDicePrompt mes)
    {
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        pcl.requestedDiceRoll(mes.getPlayerNumber());
    }

    /**
     * handle board reset
     * (new game with same players, same game name, new layout).
     * Create new Game object, destroy old one.
     * For human players, the reset message will be followed
     * with others which will fill in the game state.
     * For robots, they must discard game state and ask to re-join.
     *
     * @param mes  the message
     *
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     * @see soc.game.SOCGame#resetAsCopy()
     * @since 1.1.00
     */
    protected void handleRESETBOARDAUTH(SOCResetBoardAuth mes)
    {
        String gname = mes.getGame();
        SOCGame ga = client.games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCGame greset = ga.resetAsCopy();
        greset.isPractice = ga.isPractice;
        client.games.put(gname, greset);
        pcl.boardReset(greset, mes.getRejoinPlayerNumber(), mes.getRequestingPlayerNumber());
        ga.destroyGame();
    }

    /**
     * a player is requesting a board reset: we must update
     * local game state, and vote unless we are the requester.
     *
     * @param mes  the message
     * @since 1.1.00
     */
    protected void handleRESETBOARDVOTEREQUEST(SOCResetBoardVoteRequest mes)
    {
        String gname = mes.getGame();
        SOCGame ga = client.games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCPlayer player = ga.getPlayer(mes.getRequestingPlayer());
        pcl.boardResetVoteRequested(player);
    }

    /**
     * another player has voted on a board reset request: display the vote.
     *
     * @param mes  the message
     * @since 1.1.00
     */
    protected void handleRESETBOARDVOTE(SOCResetBoardVote mes)
    {
        String gname = mes.getGame();
        SOCGame ga = client.games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        pcl.boardResetVoteCast(player, mes.getPlayerVote());
    }

    /**
     * voting complete, board reset request rejected
     *
     * @param mes  the message
     * @since 1.1.00
     */
    protected void handleRESETBOARDREJECT(SOCResetBoardReject mes)
    {
        String gname = mes.getGame();
        SOCGame ga = client.games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        pcl.boardResetVoteRejected();
    }

    /**
     * process the "game option get defaults" message.
     * If any default option's keyname is unknown, ask the server.
     * @see ServerGametypeInfo
     * @since 1.1.07
     */
    private void handleGAMEOPTIONGETDEFAULTS(SOCGameOptionGetDefaults mes, final boolean isPractice)
    {
        ServerGametypeInfo servOpts;
        if (isPractice)
            servOpts = client.practiceServGameOpts;
        else
            servOpts = client.tcpServGameOpts;

        final List<String> unknowns;
        synchronized(servOpts)
        {
            // receiveDefaults sets opts.defaultsReceived, may set opts.allOptionsReceived
            unknowns = servOpts.receiveDefaults
                (SOCGameOption.parseOptionsToMap(mes.getOpts(), servOpts.knownOpts));
        }

        if (unknowns != null)
        {
            if (! isPractice)
                client.getMainDisplay().optionsRequested();

            gms.put(new SOCGameOptionGetInfos(unknowns, client.wantsI18nStrings(isPractice), false).toCmd(), isPractice);
        } else {
            servOpts.newGameWaitingForOpts = false;
            client.getMainDisplay().optionsReceived(servOpts, isPractice);
        }
    }

    /**
     * process the "game option info" message
     * by calling {@link ServerGametypeInfo#receiveInfo(SOCGameOptionInfo)}.
     * If all are now received, possibly show game info/options window for new game or existing game.
     *<P>
     * For a summary of the flags and variables involved with game options,
     * and the client/server interaction about their values, see
     * {@link ServerGametypeInfo}.
     *<P>
     * When first connected to a server having a different version, the client negotiates available options.
     * To avoid hanging on this process because of a very slow network or bug,
     * {@link SwingMainDisplay.GameOptionsTimeoutTask} can eventually call this
     * method to signal that all options have been received.
     *
     * @since 1.1.07
     */
    /*package*/ void handleGAMEOPTIONINFO(SOCGameOptionInfo mes, final boolean isPractice)
    {
        ServerGametypeInfo opts;
        if (isPractice)
            opts = client.practiceServGameOpts;
        else
            opts = client.tcpServGameOpts;

        boolean hasAllNow;
        synchronized(opts)
        {
            hasAllNow = opts.receiveInfo(mes);
        }

        boolean isDash = mes.getOptionNameKey().equals("-");  // I18N OK: do not localize "-" or any other keyname
        client.getMainDisplay().optionsReceived(opts, isPractice, isDash, hasAllNow);
    }

    /**
     * process the "new game with options" message
     * @since 1.1.07
     */
    private void handleNEWGAMEWITHOPTIONS(final SOCNewGameWithOptions mes, final boolean isPractice)
    {
        // Note: Must run in network message thread, not AWT event thread,
        // in case client is about to be auth'd to join this game:
        // messages must take effect in the order sent

        String gname = mes.getGame();
        final String opts = mes.getOptionsString();

        boolean canJoin = (mes.getMinVersion() <= Version.versionNumber());
        if (gname.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE)
        {
            gname = gname.substring(1);
            canJoin = false;
        }

        client.getMainDisplay().addToGameList(! canJoin, gname, opts, ! isPractice);
    }

    /**
     * handle the "list of games with options" message
     * @since 1.1.07
     */
    private void handleGAMESWITHOPTIONS(SOCGamesWithOptions mes, final boolean isPractice)
    {
        // Any game's name in this msg may start with the "unjoinable" prefix
        // SOCGames.MARKER_THIS_GAME_UNJOINABLE.
        // This is recognized and removed in mes.getGameList.

        final SOCGameList msgGames = mes.getGameList
            ((isPractice ? client.practiceServGameOpts : client.tcpServGameOpts).knownOpts);
        if (msgGames == null)
            return;

        if (! isPractice)  // practice gameoption data is set up in handleVERSION;
        {                  // practice srv's gamelist is reached through practiceServer obj.
            if (client.serverGames == null)
                client.serverGames = msgGames;
            else
                client.serverGames.addGames(msgGames, Version.versionNumber());

            // No more game-option info will be received,
            // because that's always sent before game names are sent.
            // We may still ask for GAMEOPTIONGETDEFAULTS if asking to create a game,
            // but that will happen when user clicks that button, not yet.
            client.tcpServGameOpts.noMoreOptions(false);
        }

        // update displayed list on AWT event thread, not network message thread,
        // to ensure right timing for repaint to avoid appearing empty.
        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                final MainDisplay mdisp = client.getMainDisplay();

                for (String gaName : msgGames.getGameNames())
                    mdisp.addToGameList
                        (msgGames.isUnjoinableGame(gaName), gaName, msgGames.getGameOptionsString(gaName), false);

                mdisp.repaintGameAndChannelLists();
            }
        });

    }

    /**
     * Localized i18n strings for game items.
     * Added 2015-01-11 for v2.0.00.
     * @param isPractice  Is the server {@link ClientNetwork#practiceServer}, not remote?
     */
    private void handleLOCALIZEDSTRINGS(final SOCLocalizedStrings mes, final boolean isPractice)
    {
        final SOCGameOptionSet knownOpts =
            ((isPractice) ? client.practiceServGameOpts : client.tcpServGameOpts).knownOpts;
        final List<String> strs = mes.getParams();
        final String type = strs.get(0);

        if (type.equals(SOCLocalizedStrings.TYPE_GAMEOPT))
        {
            final int L = strs.size();
            for (int i = 1; i < L; i += 2)
            {
                SOCGameOption opt = knownOpts.getKnownOption(strs.get(i), false);
                if (opt != null)
                {
                    final String desc = strs.get(i + 1);
                    if ((desc != null) && (desc.length() > 0))
                        opt.setDesc(desc);
                }
            }

        }
        else if (type.equals(SOCLocalizedStrings.TYPE_SCENARIO))
        {
            client.localizeGameScenarios
                (strs, true, mes.isFlagSet(SOCLocalizedStrings.FLAG_SENT_ALL), isPractice);
        }
        else
        {
            System.err.println("L4916: Unknown localized string type " + type);
        }
    }

    /**
     * Updated scenario info.
     * Added 2015-09-21 for v2.0.00.
     * @param isPractice  Is the server {@link ClientNetwork#practiceServer}, not remote?
     */
    private void handleSCENARIOINFO(final SOCScenarioInfo mes, final boolean isPractice)
    {
        ServerGametypeInfo opts;
        if (isPractice)
            opts = client.practiceServGameOpts;
        else
            opts = client.tcpServGameOpts;

        if (mes.noMoreScens)
        {
            synchronized (opts)
            {
                opts.allScenStringsReceived = true;
                opts.allScenInfoReceived = true;
            }
        } else {
            final String scKey = mes.getScenarioKey();

            if (mes.isKeyUnknown)
                SOCScenario.removeUnknownScenario(scKey);
            else
                SOCScenario.addKnownScenario(mes.getScenario());

            synchronized (opts)
            {
                opts.scenKeys.add(scKey);  // OK if was already present from received localized strings
            }
        }
    }

    /**
     * handle the "player stats" message
     * @since 1.1.09
     */
    private void handlePLAYERSTATS(SOCPlayerStats mes)
    {
        final String gaName = mes.getGame();
        PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl == null)
            return;  // Not one of our games
        final int clientPN = pcl.getClientPlayerNumber();
        if (clientPN == -1)
            return;  // Not one of our games

        SOCDisplaylessPlayerClient.handlePLAYERSTATS(mes, pcl.getGame(), clientPN);  // update player data

        final int stype = mes.getStatType();
        switch (stype)
        {
        case SOCPlayerStats.STYPE_RES_ROLL:
            // fallthrough
        case SOCPlayerStats.STYPE_TRADES:
            pcl.playerStats(stype, mes.getParams(), true, true);
            break;

        default:
            System.err.println("handlePLAYERSTATS: unrecognized player stat type " + stype);
        }
    }

    /**
     * Handle the server's debug piece placement on/off message.
     * @since 1.1.12
     */
    private final void handleDEBUGFREEPLACE(SOCDebugFreePlace mes)
    {
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        pcl.debugFreePlaceModeToggled(mes.getCoordinates() == 1);
    }

    /**
     * Handle server responses from the "simple request" handler.
     * @since 1.1.18
     */
    private final void handleSIMPLEREQUEST(SOCSimpleRequest mes)
    {
        final String gaName = mes.getGame();

        PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl == null)
            return;  // Not one of our games

        SOCDisplaylessPlayerClient.handleSIMPLEREQUEST(mes, client.games.get(gaName));  // update any game data
        pcl.simpleRequest(mes.getPlayerNumber(), mes.getRequestType(), mes.getValue1(), mes.getValue2());
    }

    /**
     * Handle "simple action" announcements from the server.
     * @since 1.1.19
     */
    @SuppressWarnings("fallthrough")
    private final void handleSIMPLEACTION(final SOCSimpleAction mes)
    {
        final String gaName = mes.getGame();
        PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl == null)
            return;  // Not one of our games

        final int atype = mes.getActionType();
        switch (atype)
        {
        case SOCSimpleAction.SC_PIRI_FORT_ATTACK_RESULT:
            // present the server's response to a Pirate Fortress Attack request
            pcl.scen_SC_PIRI_pirateFortressAttackResult(false, mes.getValue1(), mes.getValue2());
            break;

        case SOCSimpleAction.BOARD_EDGE_SET_SPECIAL:
            // fall through: displayless sets game data, pcl.simpleAction displays updated board layout

        case SOCSimpleAction.TRADE_PORT_REMOVED:
        case SOCSimpleAction.DEVCARD_BOUGHT:
            SOCDisplaylessPlayerClient.handleSIMPLEACTION(mes, client.games.get(gaName));
            // fall through so pcl.simpleAction updates displayed board and game data

        case SOCSimpleAction.RSRC_TYPE_MONOPOLIZED:
            pcl.simpleAction(mes.getPlayerNumber(), atype, mes.getValue1(), mes.getValue2());
            break;

        default:
            // ignore unknown types
            {
                final int mesPN = mes.getPlayerNumber();
                if ((mesPN >= 0) && (mesPN == pcl.getClientPlayerNumber()))
                    System.err.println
                        ("handleSIMPLEACTION: Unknown type ignored: " + atype + " in game " + gaName);
            }
        }
    }

    /**
     * Handle game server text and announcements.
     * @see #handleGAMETEXTMSG(SOCGameTextMsg)
     * @since 2.0.00
     */
    protected void handleGAMESERVERTEXT(SOCGameServerText mes)
    {
        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;

        pcl.messageReceived(null, mes.getText());
    }

    /**
     * Handle all players' dice roll result resources.  Looks up the game,
     * players gain resources, and announces results.
     * @since 2.0.00
     */
    protected void handleDICERESULTRESOURCES(final SOCDiceResultResources mes)
    {
        SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;

        SOCDisplaylessPlayerClient.handleDICERESULTRESOURCES(mes, ga, null, true);
        pcl.diceRolledResources(mes.playerNum, mes.playerRsrc);

        // handle total counts here, visually updating any discrepancies
        final int n = mes.playerNum.size();
        for (int i = 0; i < n; ++i)
            handlePLAYERELEMENT
                (client.getClientListener(mes.getGame()), ga, null, mes.playerNum.get(i),
                 SOCPlayerElement.SET, PEType.RESOURCE_COUNT, mes.playerResTotal.get(i), false);
    }

    /**
     * Handle moving a piece (a ship) around on the board.
     * @since 2.0.00
     */
    private final void handleMOVEPIECE(SOCMovePiece mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;  // Not one of our games

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;
        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        pcl.playerPieceMoved(player, mes.getFromCoord(), mes.getToCoord(), mes.getPieceType());
    }

    /**
     * Handle removing a piece (a ship) from the board in certain scenarios.
     * @since 2.0.00
     */
    private final void handleREMOVEPIECE(SOCRemovePiece mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;  // Not one of our games

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;
        SOCPlayer player = ga.getPlayer(mes.getParam1());
        pcl.playerPieceRemoved(player, mes.getParam3(), mes.getParam2());
    }

    /**
     * Handle an undo put piece / move piece.
     * @since 2.7.00
     */
    private void handleUNDOPUTPIECE(final SOCUndoPutPiece mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;  // Not one of our games

        SOCDisplaylessPlayerClient.handleUNDOPUTPIECE(mes, ga);

        PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl == null)
            return;  // Not one of our games
        final int pn = mes.getPlayerNumber(),
            pieceType = mes.getPieceType(),
            fromCoord = mes.getMovedFromCoordinates();
        if (pn >= 0)
            pcl.playerPiecePlacementUndone(ga.getPlayer(pn), mes.getCoordinates(), fromCoord, pieceType);
        else
            pcl.playerPiecePlacementUndoDeclined(pieceType, (fromCoord != 0));
    }

    /**
     * Reveal a hidden hex on the board.
     * @since 2.0.00
     */
    protected void handleREVEALFOGHEX(final SOCRevealFogHex mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;  // Not one of our games
        if (! ga.hasSeaBoard)
            return;  // should not happen

        ga.revealFogHiddenHex(mes.getParam1(), mes.getParam2(), mes.getParam3());

        PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl == null)
            return;  // Not one of our games
        pcl.boardUpdated();
    }

    /**
     * Update a village piece's value on the board (cloth remaining) in _SC_CLVI,
     * or a pirate fortress's strength in _SC_PIRI.
     * @since 2.0.00
     */
    protected void handlePIECEVALUE(final SOCPieceValue mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;  // Not one of our games
        if (! ga.hasSeaBoard)
            return;  // should not happen

        final int coord = mes.getParam2();
        final int pv = mes.getParam3();
        SOCPlayingPiece updatePiece = null;  // if not null, call pcl.pieceValueUpdated

        if (ga.isGameOptionSet(SOCGameOptionSet.K_SC_CLVI))
        {
            SOCVillage vi = ((SOCBoardLarge) (ga.getBoard())).getVillageAtNode(coord);
            if (vi != null)
            {
                vi.setCloth(pv);
                updatePiece = vi;
            }
        }
        else if (ga.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
        {
            SOCFortress fort = ga.getFortress(coord);
            if (fort != null)
            {
                fort.setStrength(pv);
                updatePiece = fort;
            }
        }

        if (updatePiece != null)
        {
            PlayerClientListener pcl = client.getClientListener(gaName);
            if (pcl != null)
                pcl.pieceValueUpdated(updatePiece);
        }
    }

    /**
     * Text that a player has been awarded Special Victory Point(s).
     * The server will also send a {@link SOCPlayerElement} with the SVP total.
     * Also sent for each player's SVPs when client is joining a game in progress.
     * @since 2.0.00
     */
    protected void handleSVPTEXTMSG(final SOCSVPTextMessage mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;  // Not one of our games
        final SOCPlayer pl = ga.getPlayer(mes.pn);
        if (pl == null)
            return;

        pl.addSpecialVPInfo(mes.svp, mes.desc);
        PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl == null)
            return;

        pcl.playerSVPAwarded(pl, mes.svp, mes.desc);
    }

    /**
     * Update player inventory. Refresh our display. If it's a reject message, give feedback to the user.
     * @since 2.0.00
     */
    private void handleINVENTORYITEMACTION(final SOCInventoryItemAction mes)
    {
        final boolean isReject = SOCDisplaylessPlayerClient.handleINVENTORYITEMACTION
                (client.games, (SOCInventoryItemAction) mes);

        PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;

        if (isReject)
        {
            pcl.invItemPlayRejected(mes.itemType, mes.reasonCode);
        } else {
            SOCGame ga = client.games.get(mes.getGame());
            if (ga != null)
            {
                final SOCPlayer pl = ga.getPlayer(mes.playerNumber);
                pcl.playerDevCardsUpdated
                    (pl, (mes.action == SOCInventoryItemAction.ADD_PLAYABLE));
                if (mes.action == SOCInventoryItemAction.PLAYED)
                    pcl.playerCanCancelInvItemPlay(pl, mes.canCancelPlay);
            }
        }
    }

    /**
     * Handle the "set special item" message.
     * Calls {@link SOCDisplaylessPlayerClient#handleSETSPECIALITEM(Map, SOCSetSpecialItem)},
     * then calls {@link PlayerClientListener} to update the game display.
     *
     * @param games  Games the client is playing
     * @param mes  the message
     * @since 2.0.00
     */
    private void handleSETSPECIALITEM(final Map<String, SOCGame> games, SOCSetSpecialItem mes)
    {
        // update game data:

        SOCDisplaylessPlayerClient.handleSETSPECIALITEM(games, (SOCSetSpecialItem) mes);

        final PlayerClientListener pcl = client.getClientListener(mes.getGame());
        if (pcl == null)
            return;

        final SOCGame ga = client.games.get(mes.getGame());
        if (ga == null)
            return;

        // update displays:

        final String typeKey = mes.typeKey;
        final int gi = mes.gameItemIndex, pi = mes.playerItemIndex, pn = mes.playerNumber;
        final SOCPlayer pl = ((pn != -1) && (pi != -1)) ? ga.getPlayer(pn) : null;

        switch (mes.op)
        {
        case SOCSetSpecialItem.OP_SET:
            // fall through
        case SOCSetSpecialItem.OP_CLEAR:
            pcl.playerSetSpecialItem(typeKey, ga, pl, gi, pi, (mes.op == SOCSetSpecialItem.OP_SET));
            break;

        case SOCSetSpecialItem.OP_PICK:
            // fall through
        case SOCSetSpecialItem.OP_DECLINE:
            pcl.playerPickSpecialItem(typeKey, ga, pl, gi, pi, (mes.op == SOCSetSpecialItem.OP_PICK),
                mes.coord, mes.level, mes.sv);
            break;

        case SOCSetSpecialItem.OP_SET_PICK:
            // fall through
        case SOCSetSpecialItem.OP_CLEAR_PICK:
            pcl.playerSetSpecialItem(typeKey, ga, pl, gi, pi, (mes.op == SOCSetSpecialItem.OP_SET_PICK));
            pcl.playerPickSpecialItem(typeKey, ga, pl, gi, pi, true,
                mes.coord, mes.level, mes.sv);
            break;
        }
    }

    /**
     * Server has declined our player's request.
     * Calls {@link PlayerClientListener#playerRequestDeclined(int, int, int, String)}
     * and maybe {@link #handleGAMESTATE(SOCGame, int, boolean, boolean)}.
     * @since 2.5.00
     */
    private void handleDECLINEPLAYERREQUEST(final SOCDeclinePlayerRequest mes)
    {
        final String gaName = mes.getGame();
        final PlayerClientListener pcl = client.getClientListener(gaName);
        if (pcl == null)
            return;
        final SOCGame ga = client.games.get(gaName);
        if (ga == null)
            return;

        pcl.playerRequestDeclined(mes.reasonCode, mes.detailValue1, mes.detailValue2, mes.reasonText);
        final int currState = mes.gameState;
        if (currState != 0)
            handleGAMESTATE(ga, currState, false, true);
    }

}  // class MessageHandler