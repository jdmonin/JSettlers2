/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2025 Jeremy D Monin <jeremy@nand.net>
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
package soc.baseclient;

import soc.disableDebug.D;

import soc.game.GameAction;
import soc.game.ResourceSet;
import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCInventory;
import soc.game.SOCInventoryItem;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCRoutePiece;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCSpecialItem;
import soc.game.SOCTradeOffer;
import soc.game.SOCVillage;

import soc.message.*;
import soc.message.SOCGameElements.GEType;
import soc.message.SOCPlayerElement.PEType;

import soc.robot.SOCRobotBrain;  // for javadocs only
import soc.robot.SOCRobotClient;  // javadocs only
import soc.server.genericServer.StringConnection;
import soc.util.SOCFeatureSet;
import soc.util.Version;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;

import java.net.Socket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;


/**
 * "Headless" standalone client for connecting to the SOCServer.
 * If you want another connection port, you have to specify it as the "port"
 * argument in the html source. If you run this as a stand-alone, you have to
 * specify the port.
 *<P>
 * The {@link soc.robot.SOCRobotClient SOCRobotClient} is based on this client.
 * Because of this, some methods (such as {@link #handleVERSION(boolean, SOCVersion)})
 * assume the client and server are the same version.
 *<P>
 * Some static methods here are used by {@link soc.client.SOCPlayerClient}
 * and {@link soc.robot.SOCRobotClient}, to prevent code duplication.
 *<P>
 * Since server and built-in robots are the same version, this client doesn't need
 * game option sync messages ({@link SOCGameOptionInfo}) but handles them if sent.
 * Ignores scenario synchronization messages ({@link SOCScenarioInfo}).
 * Being GUI-less, it ignores i18n localization messages ({@link SOCLocalizedStrings}).
 *<P>
 * Before v1.1.20, this class was in the {@code soc.client} package. In 1.1.20,
 * for server jar packaging it was moved into a new {@code soc.baseclient} package.
 *
 * @author Robert S Thomas
 */
public class SOCDisplaylessPlayerClient implements Runnable
{
    /**
     * Flag property <tt>jsettlers.debug.traffic</tt>: When present, the
     * contents of incoming and outgoing network message traffic should be debug-printed.
     * Used by this class, {@link soc.robot.SOCRobotClient SOCRobotClient}, and
     * {@link soc.client.SOCPlayerClient SOCPlayerClient}.
     * @since 1.2.00
     */
    public static final String PROP_JSETTLERS_DEBUG_TRAFFIC = "jsettlers.debug.traffic";

    /**
     * For client/robot testing, string property {@code "jsettlers.debug.client.gameopt3p"}
     * with name of a "third-party" Known Game Option to create; will have {@link SOCGameOption#FLAG_3RD_PARTY}.
     * At server connect, will report having client feature {@code "com.example.js.feat."} + optionName
     * along with default features or {@link soc.client.SOCPlayerClient#PROP_JSETTLERS_DEBUG_CLIENT_FEATURES}
     * in the {@link SOCVersion} message sent to server. See {@link SOCFeatureSet} for more info.
     *
     * @see soc.server.SOCServer#PROP_JVM_JSETTLERS_DEBUG_SERVER_GAMEOPT3P
     * @since 2.5.00
     */
    public static final String PROP_JSETTLERS_DEBUG_CLIENT_GAMEOPT3P = "jsettlers.debug.client.gameopt3p";

    protected static String STATSPREFEX = "  [";
    protected String doc;
    protected String lastMessage;

    /**
     * Time when next {@link SOCServerPing} is expected at, based on
     * previous ping's {@link SOCServerPing#getSleepTime()}, or 0.
     * Repetitive pings' {@link #debugTraffic} prints should be hidden
     * when the bot is otherwise idle. Receiving other in-game message types
     * resets this to 0.
     * @since 2.0.00
     */
    protected long nextServerPingExpectedAt;

    /**
     * Server connection info.
     * Robot clients should set non-null {@link ServerConnectInfo#robotCookie} when constructing.
     *<P>
     * Versions before 2.2.00 instead had {@code host}, {@code port}, {@code strSocketName} fields.
     *
     * @see #allOptsReceived
     * @since 2.2.00
     */
    protected final ServerConnectInfo serverConnectInfo;

    /**
     * This client's set of Known Options.
     * Initialized from {@link SOCGameOptionSet#getAllKnownOptions()}, updated by
     * {@link SOCGameOptionInfo} messages.
     * @see #allOptsReceived
     * @see #handleGAMEOPTIONINFO(SOCGameOptionInfo)
     * @since 2.5.00
     */
    public SOCGameOptionSet knownOpts = SOCGameOptionSet.getAllKnownOptions();

    /**
     * Since server and built-in robots are the same version,
     * this client doesn't need {@link SOCGameOption} synchronization messages.
     * Server still sends them in some conditions, such as when gameopts are limited by client features
     * or when inactive opts have been activated.
     *<P>
     * By default assumes true, there's no pending info to receive, because is same version as server.
     * If a gameopt sync message is sent, will set false until the "end of list" marker message is sent.
     *
     * @see #knownOpts
     * @see #handleGAMEOPTIONINFO(SOCGameOptionInfo)
     * @since 2.5.00
     */
    protected boolean allOptsReceived = true;

    /**
     * Network socket. Initialized in subclasses.
     * Before v2.5.00 this field was {@code s}.
     */
    protected Socket sock;

    protected DataInputStream in;
    protected DataOutputStream out;

    /**
     * Local server connection, if {@link ServerConnectInfo#stringSocketName} != null.
     * @see #sLocalVersion
     * @since 1.1.00
     */
    protected StringConnection sLocal;

    /**
     * Server version number, sent soon after connect, or -1 if unknown.
     * {@link #sLocalVersion} should always equal our own version.
     * @since 1.1.00
     */
    protected int sVersion, sLocalVersion;

    /**
     * Server's active optional features, sent soon after connect, or null if unknown.
     * {@link #sLocalFeatures} goes with our locally hosted server, if any.
     * @since 1.1.19
     */
    protected SOCFeatureSet sFeatures, sLocalFeatures;

    protected Thread reader = null;
    protected Exception ex = null;
    protected boolean connected = false;

    /**
     * were we rejected from server? (full or robot name taken)
     * @since 1.1.00
     */
    protected boolean rejected = false;

    /**
     * the nickname
     */
    protected String nickname = null;

    /**
     * the password
     */
    protected String password = null;

    /**
     * true if we've stored the password
     */
    protected boolean gotPassword;

    /**
     * Chat channels; for general use, or possibly in a future version to control bots.
     */
    protected Hashtable<String,?> channels = new Hashtable<String,Object>();

    /**
     * the games we're playing
     *<P>
     * Games are added by {@link #handleJOINGAMEAUTH(SOCJoinGameAuth, boolean)},
     * removed by {@link #leaveGame(String)}.
     * @see #getGame(String)
     */
    protected Hashtable<String, SOCGame> games = new Hashtable<String, SOCGame>();

    /**
     * True if contents of incoming and outgoing network message traffic should be debug-printed.
     * Set if optional system property {@link #PROP_JSETTLERS_DEBUG_TRAFFIC} is set.
     * @since 1.2.00
     */
    protected boolean debugTraffic;

    /**
     * Should client ignore all {@link SOCPlayerStats} messages,
     * although they may be about our {@link #nickname} client player? True by default.
     * @see #handlePLAYERSTATS(SOCPlayerStats, SOCGame, int)
     * @since 2.7.00
     */
    protected boolean ignorePlayerStats = true;

    /**
     * Constructor to set up using this server connect info. Does not actually connect here;
     * subclass methods such as {@link soc.robot.SOCRobotClient#init()} must do so.
     * 
     * @param sci  Server connect info (TCP or stringPort); not {@code null}
     * @param visual  true if this client is visual
     * @throws IllegalArgumentException if {@code sci == null}
     * @since 2.2.00
     */
    public SOCDisplaylessPlayerClient(ServerConnectInfo sci, boolean visual)
        throws IllegalArgumentException
    {
        if (sci == null)
            throw new IllegalArgumentException("sci");
        this.serverConnectInfo = sci;

        sVersion = -1;  sLocalVersion = -1;

        if (null != System.getProperty(PROP_JSETTLERS_DEBUG_TRAFFIC))
            debugTraffic = true;  // set flag if debug prop has any value at all
    }

    /**
     * @return the nickname of this user
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * Get a game that we've joined in this client.
     * @param gaName  game name to look for
     * @return Game if we've joined it, or {@code null} if not found or not joined
     * @since 2.5.00
     */
    public SOCGame getGame(final String gaName)
    {
        return games.get(gaName);
    }

    /**
     * Continuously read from the net in a separate thread.
     * if {@link java.io.EOFException} or another error occurs, breaks the loop:
     * Exception will be stored in {@link #ex}. {@link #destroy()} will be called
     * unless {@code ex} is an {@link InterruptedIOException} (socket timeout).
     */
    public void run()
    {
        // Thread name for debug
        try
        {
            Thread.currentThread().setName("robot-netread-" + nickname);
        }
        catch (Throwable th) {}

        try
        {
            while (connected)
            {
                String s;
                if (sLocal == null)
                    s = in.readUTF();
                else
                    s = sLocal.readNext();

                SOCMessage msg = SOCMessage.toMsg(s);
                if (msg != null)
                    treat(msg);
                else if (debugTraffic)
                    soc.debug.D.ebugERROR(nickname + ": Could not parse net message: " + s);
            }
        }
        catch (InterruptedIOException e)
        {
            ex = e;
            System.err.println("Socket timeout in run: " + e);
        }
        catch (IOException e)
        {
            if (! connected)
            {
                return;
            }

            ex = e;
            destroy();
        }
    }

    /**
     * resend the last message
     */
    public void resend()
    {
        if (lastMessage != null)
            put(lastMessage);
    }

    /**
     * write a message to the net
     *
     * @param s  the message
     * @return true if the message was sent, false if not
     * @throws IllegalArgumentException if {@code s} is {@code null}
     */
    public synchronized boolean put(String s)
        throws IllegalArgumentException
    {
        if (s == null)
            throw new IllegalArgumentException("null");

        lastMessage = s;

        if (debugTraffic || D.ebugIsEnabled())
            soc.debug.D.ebugPrintlnINFO("OUT - " + nickname + " - " + s);

        if ((ex != null) || ! connected)
        {
            return false;
        }

        try
        {
            if (sLocal == null)
            {
                out.writeUTF(s);
                out.flush();
            } else {
                sLocal.put(s);
            }
        }
        catch (InterruptedIOException x)
        {
            System.err.println("Socket timeout in put: " + x);
        }
        catch (IOException e)
        {
            ex = e;
            System.err.println("could not write to the net: " + ex);
            destroy();

            return false;
        }

        return true;
    }

    /**
     * Treat the incoming messages.
     *<P>
     * For message types relevant to robots and automated clients, will update our data from the
     * message contents. Other types will be ignored. Messages of unknown type are ignored
     * ({@code mes} will be null from {@link SOCMessage#toMsg(String)}).
     *<P>
     * If {@link #PROP_JSETTLERS_DEBUG_TRAFFIC} is set, debug-prints message contents.
     *<P>
     *<B>Note:</B> <tt>SOCRobotClient.treat(mes)</tt> calls this method as its default case, for
     * message types which have no robot-specific handling. For those that do, the robot treat's
     * switch case can call <tt>super.treat(mes)</tt> before or after any robot-specific handling.
     * (Before v2.0.00, the bot didn't call this method by default.)
     *<P>
     *<B>New message types:</B><BR>
     * If the message type is relevant to bots and other automated clients, add it here. If handling
     * differs between displayless and the robot client, add it to <tt>SOCRobotClient.treat</tt> too.
     *
     * @param mes    the message
     */
    public void treat(SOCMessage mes)
    {
        treat(mes, false);
    }

    /**
     * Treat the incoming messages, callable from subclasses. For details see {@link #treat(SOCMessage)}.
     * This method adds a flag parameter to prevent debug printing message contents twice.
     *
     * @param mes  The message
     * @param didDebugPrintAlready  If true, don't debug print {@code mes.toString()}
     *     even if {@link D#ebugIsEnabled()}. Intended for use from subclasses which
     *     would have done that debug print if enabled.
     * @since 2.0.00
     */
    protected void treat(final SOCMessage mes, final boolean didDebugPrintAlready)
    {
        if (mes == null)
            return;  // Msg parsing error

        if ((debugTraffic || D.ebugIsEnabled()) && (! didDebugPrintAlready)
            && ! ((mes instanceof SOCServerPing) && (nextServerPingExpectedAt != 0)
                  && (Math.abs(System.currentTimeMillis() - nextServerPingExpectedAt) <= 66000)))
                          // within 66 seconds of the expected time; see handleSERVERPING
        {
            soc.debug.D.ebugPrintlnINFO("IN - " + nickname + " - " + mes.toString());
        }

        final int typ = mes.getType();
        if (mes instanceof SOCMessageForGame)
            nextServerPingExpectedAt = 0;  // bot not idle, is in a game

        try
        {
            switch (typ)
            {
            /**
             * server's version message
             */
            case SOCMessage.VERSION:
                handleVERSION((sLocal != null), (SOCVersion) mes);
                break;

            /**
             * status message
             */
            case SOCMessage.STATUSMESSAGE:
                handleSTATUSMESSAGE((SOCStatusMessage) mes);
                break;

            /**
             * server ping
             */
            case SOCMessage.SERVERPING:
                handleSERVERPING((SOCServerPing) mes);
                break;

            /**
             * join channel authorization
             */
            case SOCMessage.JOINCHANNELAUTH:
                handleJOINCHANNELAUTH((SOCJoinChannelAuth) mes);
                break;

            /**
             * someone joined a chat channel
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
             * list of chat channels on the server
             */
            case SOCMessage.CHANNELS:
                handleCHANNELS((SOCChannels) mes);
                break;

            /**
             * channel text message
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
                handleGAMES((SOCGames) mes);
                break;

            /**
             * list of games with options on the server
             */
            case SOCMessage.GAMESWITHOPTIONS:
                handleGAMESWITHOPTIONS((SOCGamesWithOptions) mes);
                break;

            /**
             * join game authorization
             */
            case SOCMessage.JOINGAMEAUTH:
                handleJOINGAMEAUTH((SOCJoinGameAuth) mes, (sLocal != null));
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
                handleNEWGAME((SOCNewGame) mes);
                break;

            /**
             * new game with options has been created
             */
            case SOCMessage.NEWGAMEWITHOPTIONS:
                handleNEWGAMEWITHOPTIONS((SOCNewGameWithOptions) mes);
                break;

            /**
             * game has been destroyed
             */
            case SOCMessage.DELETEGAME:
                handleDELETEGAME((SOCDeleteGame) mes);
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
                handleBOARDLAYOUT((SOCBoardLayout) mes, games.get(((SOCBoardLayout) mes).getGame()));
                break;

            /**
             * receive a board layout (new format, as of 20091104 (v 1.1.08))
             */
            case SOCMessage.BOARDLAYOUT2:
                handleBOARDLAYOUT2((SOCBoardLayout2) mes, games.get(((SOCBoardLayout2) mes).getGame()));
                break;

            /**
             * message that the game is starting
             */
            case SOCMessage.STARTGAME:
                handleSTARTGAME(games, (SOCStartGame) mes);
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
                handleSETTURN((SOCSetTurn) mes);
                break;

            /**
             * set who the first player is
             */
            case SOCMessage.FIRSTPLAYER:
                handleFIRSTPLAYER((SOCFirstPlayer) mes);
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
                handleLASTSETTLEMENT((SOCLastSettlement) mes, games.get(((SOCLastSettlement) mes).getGame()));
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
                {
                    final SOCPutPiece ppm = (SOCPutPiece) mes;
                    handlePUTPIECE(ppm, games.get(ppm.getGame()));
                }
                break;

            /**
             * the current player has cancelled an initial settlement,
             * or has tried to place a piece illegally.
             */
            case SOCMessage.CANCELBUILDREQUEST:
                handleCANCELBUILDREQUEST((SOCCancelBuildRequest) mes);
                break;

            /**
             * A player has discarded resources.
             * Added 2021-11-26 for v2.5.00.
             */
            case SOCMessage.DISCARD:
                handleDISCARD((SOCDiscard) mes, games.get(((SOCDiscard) mes).getGame()));
                break;

            /**
             * the robber or pirate moved
             */
            case SOCMessage.MOVEROBBER:
                handleMOVEROBBER((SOCMoveRobber) mes);
                break;

            /**
             * the server wants this player to discard
             */
            case SOCMessage.DISCARDREQUEST:
                handleDISCARDREQUEST((SOCDiscardRequest) mes);
                break;

            /**
             * the server wants this player to choose a player to rob
             */
            case SOCMessage.CHOOSEPLAYERREQUEST:
                handleCHOOSEPLAYERREQUEST((SOCChoosePlayerRequest) mes);
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
             * a player has rejected an offer
             */
            case SOCMessage.REJECTOFFER:
                handleREJECTOFFER((SOCRejectOffer) mes);
                break;

            /**
             * update game data for a trade between players. Added 2021-08-02 for v2.5.00
             */
            case SOCMessage.ACCEPTOFFER:
                handleACCEPTOFFER((SOCAcceptOffer) mes, games.get(((SOCMessageForGame) mes).getGame()));
                break;

            /**
             * the trade message needs to be cleared
             */
            case SOCMessage.CLEARTRADEMSG:
                handleCLEARTRADEMSG((SOCClearTradeMsg) mes);
                break;

            /**
             * Update game data for bank trade. Added 2021-01-20 for v2.5.00
             */
            case SOCMessage.BANKTRADE:
                handleBANKTRADE((SOCBankTrade) mes, games.get(((SOCMessageForGame) mes).getGame()));
                break;

            /**
             * the current number of development cards
             */
            case SOCMessage.DEVCARDCOUNT:
                handleDEVCARDCOUNT((SOCDevCardCount) mes);
                break;

            /**
             * a dev card action, either draw, play, or add to hand
             */
            case SOCMessage.DEVCARDACTION:
                handleDEVCARDACTION((sLocal != null), (SOCDevCardAction) mes);
                break;

            /**
             * set the flag that tells if a player has played a
             * development card this turn
             */
            case SOCMessage.SETPLAYEDDEVCARD:
                handleSETPLAYEDDEVCARD((SOCSetPlayedDevCard) mes);
                break;

            /**
             * get a list of all the potential settlements for a player
             */
            case SOCMessage.POTENTIALSETTLEMENTS:
                handlePOTENTIALSETTLEMENTS
                    ((SOCPotentialSettlements) mes, games.get(((SOCPotentialSettlements) mes).getGame()));
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
                handleLONGESTROAD((SOCLongestRoad) mes);
                break;

            /**
             * handle the largest army message
             */
            case SOCMessage.LARGESTARMY:
                handleLARGESTARMY((SOCLargestArmy) mes);
                break;

            /**
             * handle the seat lock state message
             */
            case SOCMessage.SETSEATLOCK:
                handleSETSEATLOCK((SOCSetSeatLock) mes);
                break;

            /**
             * handle board reset (new game with same players, same game name, new layout).
             */
            case SOCMessage.RESETBOARDAUTH:
                handleRESETBOARDAUTH((SOCResetBoardAuth) mes);
                break;

            /**
             * handle updated game option info from server
             */
            case SOCMessage.GAMEOPTIONINFO:
                handleGAMEOPTIONINFO((SOCGameOptionInfo) mes);
                break;

            /**
             * player statistics. Generally ignored by bots;
             * to support unit tests, added here 2023-11-27 for v2.7.00.
             */
            case SOCMessage.PLAYERSTATS:
                if (! ignorePlayerStats)
                {
                    SOCGame ga = games.get(((SOCPlayerStats) mes).getGame());
                    if (ga != null)
                    {
                        SOCPlayer pn = ga.getPlayer(nickname);
                        if (pn != null)
                            handlePLAYERSTATS((SOCPlayerStats) mes, ga, pn.getPlayerNumber());
                    }
                }
                break;

            /**
             * generic "simple action" announcements from the server.
             * Added 2013-09-04 for v1.1.19.
             */
            case SOCMessage.SIMPLEACTION:
                handleSIMPLEACTION((SOCSimpleAction) mes, games.get(((SOCSimpleAction) mes).getGame()));
                break;

            /**
             * game server text and announcements.
             * Ignored by bots; stub is here for future use by other subclasses.
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
             * Update player inventory.
             * Added 2013-11-26 for v2.0.00.
             */
            case SOCMessage.INVENTORYITEMACTION:
                handleINVENTORYITEMACTION(games, (SOCInventoryItemAction) mes);
                break;

            /**
             * Special Item change announcements.
             * Added 2014-04-16 for v2.0.00.
             */
            case SOCMessage.SETSPECIALITEM:
                handleSETSPECIALITEM(games, (SOCSetSpecialItem) mes);
                break;

            /**
             * Report robbery result.
             * Added 2020-09-15 for v2.5.00.
             */
            case SOCMessage.ROBBERYRESULT:
                handleROBBERYRESULT
                    ((SOCRobberyResult) mes, games.get(((SOCMessageForGame) mes).getGame()));
                break;

            /**
             * Player has Picked Resources.
             * Added 2020-12-14 for v2.5.00.
             */
            case SOCMessage.PICKRESOURCES:
                handlePICKRESOURCES
                    ((SOCPickResources) mes, games.get(((SOCMessageForGame) mes).getGame()));
                break;

            /**
             * Server has declined player's request.
             * Added 2021-12-11 for v2.5.00.
             */
            case SOCMessage.DECLINEPLAYERREQUEST:
                handleDECLINEPLAYERREQUEST
                    ((SOCDeclinePlayerRequest) mes, games.get(((SOCMessageForGame) mes).getGame()));
                break;

            /**
             * Undo moving or placing a piece.
             * Added 2022-11-11 for v2.7.00.
             */
            case SOCMessage.UNDOPUTPIECE:
                handleUNDOPUTPIECE
                    ((SOCUndoPutPiece) mes, games.get(((SOCUndoPutPiece) mes).getGame()));
                break;

            /**
             * Update last-action data.
             * Added 2022-12-20 for v2.7.00.
             */
            case SOCMessage.SETLASTACTION:
                handleSETLASTACTION
                    ((SOCSetLastAction) mes, games.get(((SOCSetLastAction) mes).getGame()));
                break;

            /**
             * Can't undo the most recent action.
             * Added 2025-01-07 for v2.7.00.
             */
            case SOCMessage.UNDONOTALLOWEDREASONTEXT:
                handleUNDONOTALLOWEDREASONTEXT
                    ((SOCUndoNotAllowedReasonText) mes, games.get(((SOCUndoNotAllowedReasonText) mes).getGame()));
                break;

            /**
             * Reopen or close a shipping trade route.
             * Added 2022-12-18 for v2.7.00.
             */
            case SOCMessage.SETSHIPROUTECLOSED:
                handleSETSHIPROUTECLOSED
                    ((SOCSetShipRouteClosed) mes, games.get(((SOCSetShipRouteClosed) mes).getGame()));
                break;

            }
        }
        catch (Exception e)
        {
            System.out.println("SOCDisplaylessPlayerClient treat ERROR - " + e.getMessage());
            e.printStackTrace();
            System.out.println("  For message: " + mes);
        }
    }

    /**
     * Handle the "status message" message.
     * This stub does nothing except for {@link SOCStatusMessage#SV_SERVER_SHUTDOWN},
     * which calls {@link #disconnect()}.
     * @param mes  the message
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes)
    {
        if (mes.getStatusValue() == SOCStatusMessage.SV_SERVER_SHUTDOWN)
            disconnect();
    }

    /**
     * handle the server ping message.
     * Echo back to server, to ensure we're still connected.
     *<P>
     * Message was ignored before version 1.1.08 (this method was an empty stub).
     * Moved for v2.0.00 from subclass {@code SOCRobotClient}.
     *
     * @param mes  the message
     */
    protected void handleSERVERPING(SOCServerPing mes)
    {
        final long now = System.currentTimeMillis();
        boolean hidePingDebug = (debugTraffic && (nextServerPingExpectedAt != 0))
            && (Math.abs(now - nextServerPingExpectedAt) <= 66000);  // within 66 seconds of expected time)

        nextServerPingExpectedAt = now + mes.getSleepTime();

        if (hidePingDebug)
            debugTraffic = false;

        put(mes.toCmd());

        if (hidePingDebug)
            debugTraffic = true;

        /*
           D.ebugPrintln("(*)(*) ServerPing message = "+mes);
           D.ebugPrintln("(*)(*) ServerPing sleepTime = "+mes.getSleepTime());
           D.ebugPrintln("(*)(*) resetThread = "+resetThread);
           resetThread.sleepMore();
         */
    }

    /**
     * handle the "join channel authorization" message.
     * @param mes  the message
     */
    protected void handleJOINCHANNELAUTH(SOCJoinChannelAuth mes)
    {
        gotPassword = true;
    }

    /**
     * Handle the "version" message, server's version report.
     *<P>
     * Because SOCDisplaylessPlayerClient is used only for the
     * robot, and the robot should always be the same version as
     * the server, don't ask server for info about
     * {@link soc.game.SOCGameOption game option} deltas between
     * the two versions.
     *<P>
     * If somehow the server isn't our version, print an error and disconnect.
     *
     * @param isLocal  Is the server local, or remote?  Client can be connected
     *                only to local, or remote.
     * @param mes  the message
     * @since 1.1.00
     */
    protected void handleVERSION(boolean isLocal, SOCVersion mes)
    {
        D.ebugPrintlnINFO("handleVERSION: " + mes);
        int vers = mes.getVersionNumber();
        final SOCFeatureSet feats =
            (vers >= SOCFeatureSet.VERSION_FOR_SERVERFEATURES)
            ? new SOCFeatureSet(mes.feats)
            : new SOCFeatureSet(true, true);

        if (isLocal)
        {
            sLocalVersion = vers;
            sLocalFeatures = feats;
        } else {
            sVersion = vers;
            sFeatures = feats;
        }

        final int ourVers = Version.versionNumber();
        if (vers != ourVers)
        {
            final String errmsg =
                "Internal error SOCDisplaylessPlayerClient.handleVERSION: Server must be same as our version "
                + ourVers + ", not " + vers;  // i18n: Unlikely error, keep un-localized for possible bug reporting
            System.err.println(errmsg);
            ex = new IllegalStateException(errmsg);
            destroy();
        }

        // Clients v1.1.07 and later send SOCVersion right away at connect,
        // so no need to reply here with our client version.

        // Don't check for game options different at version, unlike SOCPlayerClient.handleVERSION.
    }

    /**
     * handle the "a client joined a channel" message.
     * @param mes  the message
     */
    protected void handleJOINCHANNEL(SOCJoinChannel mes) {}

    /**
     * handle the "channel members" message.
     * @param mes  the message
     */
    protected void handleCHANNELMEMBERS(SOCChannelMembers mes) {}

    /**
     * handle the "new channel" message
     * @param mes  the message
     */
    protected void handleNEWCHANNEL(SOCNewChannel mes) {}

    /**
     * handle the "list of channels" message
     * @param mes  the message
     */
    protected void handleCHANNELS(SOCChannels mes) {}

    /**
     * handle a broadcast text message
     * @param mes  the message
     */
    protected void handleBCASTTEXTMSG(SOCBCastTextMsg mes) {}

    /**
     * handle a text message
     * @param mes  the message
     */
    protected void handleCHANNELTEXTMSG(SOCChannelTextMsg mes) {}

    /**
     * handle the "leave channel" message
     * @param mes  the message
     */
    protected void handleLEAVECHANNEL(SOCLeaveChannel mes) {}

    /**
     * handle the "delete channel" message
     * @param mes  the message
     */
    protected void handleDELETECHANNEL(SOCDeleteChannel mes) {}

    /**
     * handle the "list of games" message
     * @param mes  the message
     */
    protected void handleGAMES(SOCGames mes) {}

    /**
     * handle the "list of games with options" message
     * @since 2.5.00
     */
    protected void handleGAMESWITHOPTIONS(final SOCGamesWithOptions mes) {}

    /**
     * handle the "join game authorization" message
     * @param mes  the message
     * @param isPractice Is the server local for practice, or remote?
     */
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes, final boolean isPractice)
    {
        gotPassword = true;

        final SOCGameOptionSet opts;
        final int bh = mes.getBoardHeight(), bw = mes.getBoardWidth();
        if ((bh != 0) || (bw != 0))
        {
            // Encode board size to pass through game constructor.
            opts = new SOCGameOptionSet();
            SOCGameOption opt = knownOpts.getKnownOption("_BHW", true);
            opt.setIntValue((bh << 8) | bw);
            opts.put(opt);
        } else {
            opts = null;
        }

        final SOCGame ga = new SOCGame(mes.getGame(), opts, knownOpts);
        ga.isPractice = isPractice;
        ga.serverVersion = (isPractice) ? sLocalVersion : sVersion;
        games.put(mes.getGame(), ga);
    }

    /**
     * handle the "join game" message
     * @param mes  the message
     */
    protected void handleJOINGAME(SOCJoinGame mes) {}

    /**
     * handle the "leave game" message
     * @param mes  the message
     */
    protected void handleLEAVEGAME(SOCLeaveGame mes)
    {
        String gn = (mes.getGame());
        SOCGame ga = games.get(gn);
        if (ga == null)
            return;

        SOCPlayer player = ga.getPlayer(mes.getNickname());

        if (player != null)
        {
            //  This user was not a spectator
            ga.removePlayer(mes.getNickname(), false);
        }
    }

    /**
     * handle the "new game" message
     * @param mes  the message
     */
    protected void handleNEWGAME(SOCNewGame mes) {}

    /**
     * handle the "new game with options" message
     * @since 2.5.00
     */
    protected void handleNEWGAMEWITHOPTIONS(final SOCNewGameWithOptions mes) {}

    /**
     * handle the "delete game" message
     * @param mes  the message
     */
    protected void handleDELETEGAME(SOCDeleteGame mes) {}

    /**
     * handle the "game members" message
     * @param mes  the message
     */
    protected void handleGAMEMEMBERS(SOCGameMembers mes) {}

    /**
     * handle the "game stats" message. Looks up the game and calls
     * {@link #handleGAMESTATS(SOCGameStats, SOCGame)}.
     *<P>
     * Instead of overriding this method, {@link SOCRobotClient#treat(SOCMessage)} bypasses it
     * and processes stats in {@link SOCRobotBrain#handleGAMESTATS(SOCGameStats)}.
     */
    protected void handleGAMESTATS(SOCGameStats mes)
    {
        handleGAMESTATS(mes, games.get(mes.getGame()));
    }

    /**
     * Handle the "game stats" message: static version to share with SOCPlayerClient.
     * Updates timing fields for {@link SOCGameStats#TYPE_TIMING}, otherwise does nothing.
     * @param mes  Message data
     * @param ga  Game to update; does nothing if {@code null}
     * @since 2.7.00
     */
    public static final void handleGAMESTATS(final SOCGameStats mes, final SOCGame ga)
    {
        if (ga == null)
            return;
        if (mes.getStatType() != SOCGameStats.TYPE_TIMING)
            return;

        // similar logic is in soc.client.MessageHandler.handleGAMESTATS

        final long[] stats = mes.getScores();
        final long timeCreated = stats[0], timeFinished = stats[2],
            timeNow = System.currentTimeMillis() / 1000;
        if (timeCreated > timeNow)
            return;

        ga.setTimeSinceCreated((int) (timeNow - timeCreated));
        if (timeFinished > timeCreated)
            ga.setDurationSecondsFinished((int) (timeFinished - timeCreated));
    }

    /**
     * handle the "game text message" message; stub.
     * Overridden by bot to look for its debug commands.
     * @param mes  the message
     */
    protected void handleGAMETEXTMSG(SOCGameTextMsg mes) {}

    /**
     * Handle the "game server text" message; stub.
     * Ignored by bots. This stub can be overridden by future subclasses.
     * @param mes  the message
     */
    protected void handleGAMESERVERTEXT(SOCGameServerText mes) {}

    /**
     * Handle all players' dice roll result resources.  Looks up the game and calls
     * {@link #handleDICERESULTRESOURCES(SOCDiceResultResources, SOCGame)}
     * so the players gain resources.
     * @since 2.0.00
     */
    protected void handleDICERESULTRESOURCES(final SOCDiceResultResources mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        handleDICERESULTRESOURCES(mes, ga, nickname, false);
    }

    /**
     * Handle all players' dice roll result resources: static version to share with SOCPlayerClient.
     * Calls players' {@link SOCPlayer#addRolledResources(SOCResourceSet)} to gain resources and
     * update {@link SOCPlayer#getResourceRollStats()}.
     * @param mes  Message data
     * @param ga  Game to update
     * @param nickname  Our client player's nickname, needed only if {@code skipResourceCount} is false.
     *     Can be {@code null} otherwise.
     *     See {@link #handlePLAYERELEMENT_simple(SOCGame, SOCPlayer, int, int, PEType, int, String)}.
     * @param skipResourceCount  If true, ignore the resource-totals part of the message
     *     because caller will handle that separately; {@code nickname} can be {@code null}
     * @since 2.0.00
     */
    public static final void handleDICERESULTRESOURCES
        (final SOCDiceResultResources mes, final SOCGame ga, final String nickname, final boolean skipResourceCount)
    {
        final int n = mes.playerNum.size();
        for (int p = 0; p < n; ++p)  // current index reading from playerNum and playerRsrc
        {
            final SOCResourceSet rs = mes.playerRsrc.get(p);
            final int pn = mes.playerNum.get(p);
            final SOCPlayer pl = ga.getPlayer(pn);

            pl.addRolledResources(rs);

            if (! skipResourceCount)
                handlePLAYERELEMENT_simple
                    (ga, pl, pn, SOCPlayerElement.SET,
                     PEType.RESOURCE_COUNT, mes.playerResTotal.get(p), nickname);
        }
    }

    /**
     * handle the "player sitting down" message
     * @param mes  the message
     */
    protected SOCGame handleSITDOWN(final SOCSitDown mes)
    {
        /**
         * tell the game that a player is sitting
         */
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return null;

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

            return null;
        }
        finally
        {
            ga.releaseMonitor();
        }

        if (nickname.equals(plName)
            && (ga.isPractice || (sVersion >= SOCDevCardAction.VERSION_FOR_SITDOWN_CLEARS_INVENTORY)))
        {
            // server is about to send our dev-card inventory contents
            player.getInventory().clear();
        }

        return ga;
    }

    /**
     * handle the "board layout" message
     * @param mes  the message
     * @param ga  Game to apply layout to, for method reuse; does nothing if null
     * @see #handleBOARDLAYOUT2(SOCBoardLayout2, SOCGame)
     */
    public static void handleBOARDLAYOUT(SOCBoardLayout mes, final SOCGame ga)
    {
        if (ga == null)
            return;

        // BOARDLAYOUT is always the v1 board encoding (oldest format)
        SOCBoard bd = ga.getBoard();
        bd.setHexLayout(mes.getHexLayout());
        bd.setNumberLayout(mes.getNumberLayout());
        bd.setRobberHex(mes.getRobberHex(), false);
        ga.updateAtBoardLayout();
    }

    /**
     * handle the "board layout" message, new format
     * @param mes  the message
     * @param ga  Game to apply layout to, for method reuse; does nothing if null
     * @since 1.1.08
     * @return True if layout was understood and game != null, false otherwise
     * @see #handleBOARDLAYOUT(SOCBoardLayout, SOCGame)
     */
    public static boolean handleBOARDLAYOUT2(SOCBoardLayout2 mes, final SOCGame ga)
    {
        if (ga == null)
            return false;

        SOCBoard bd = ga.getBoard();
        final int bef = mes.getBoardEncodingFormat();
        if (bef == SOCBoard.BOARD_ENCODING_LARGE)
        {
            // v3
            ((SOCBoardLarge) bd).setLandHexLayout(mes.getIntArrayPart("LH"));
            ga.setPlayersLandHexCoordinates();
            int hex = mes.getIntPart("RH");
            if (hex != 0)
                bd.setRobberHex(hex, false);
            hex = mes.getIntPart("PH");
            if (hex != 0)
                ((SOCBoardLarge) bd).setPirateHex(hex, false);
            int[] portLayout = mes.getIntArrayPart("PL");
            if (portLayout != null)
                bd.setPortsLayout(portLayout);
            int[] x = mes.getIntArrayPart("PX");
            if (x != null)
                ((SOCBoardLarge) bd).setPlayerExcludedLandAreas(x);
            x = mes.getIntArrayPart("RX");
            if (x != null)
                ((SOCBoardLarge) bd).setRobberExcludedLandAreas(x);
            x = mes.getIntArrayPart("CV");
            if (x != null)
                ((SOCBoardLarge) bd).setVillageAndClothLayout(x);
            x = mes.getIntArrayPart("LS");
            if (x != null)
                ((SOCBoardLarge) bd).addLoneLegalSettlements(ga, x);

            HashMap<String, int[]> others = mes.getAddedParts();
            if (others != null)
                ((SOCBoardLarge) bd).setAddedLayoutParts(others);
        }
        else if (bef <= SOCBoard.BOARD_ENCODING_6PLAYER)
        {
            // v1 or v2
            bd.setHexLayout(mes.getIntArrayPart("HL"));
            bd.setNumberLayout(mes.getIntArrayPart("NL"));
            bd.setRobberHex(mes.getIntPart("RH"), false);
            int[] portLayout = mes.getIntArrayPart("PL");
            if (portLayout != null)
                bd.setPortsLayout(portLayout);
        } else {
            // Should not occur: Server has sent an unrecognized format
            System.err.println
                ("Cannot recognize game encoding v" + bef + " for game " + ga.getName());
            return false;
        }

        ga.updateAtBoardLayout();
        return true;
    }

    /**
     * handle the "start game" message
     * @param games  The hashtable of client's {@link SOCGame}s; key = game name
     * @param mes  the message
     */
    protected static void handleSTARTGAME(Hashtable<String, SOCGame> games, SOCStartGame mes)
    {
        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        handleGAMESTATE(ga, mes.getGameState());
        handleSTARTGAME_checkIsBotsOnly(ga);
    }

    /**
     * Check this game's seats for human players to determine {@link SOCGame#isBotsOnly} in game's local copy.
     * Calls {@link SOCGame#isSeatVacant(int)} and {@link SOCPlayer#isRobot()}.
     * @param ga  Game to check
     * @since 2.0.00
     */
    public static final void handleSTARTGAME_checkIsBotsOnly(SOCGame ga)
    {
        boolean isBotsOnly = true;

        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            if (! (ga.isSeatVacant(pn) || ga.getPlayer(pn).isRobot()))
            {
                isBotsOnly = false;
                break;
            }
        }

        ga.isBotsOnly = isBotsOnly;
    }

    /**
     * Handle the "game state" message; calls {@link #handleGAMESTATE(SOCGame, int)}.
     * @param mes  the message
     */
    protected void handleGAMESTATE(SOCGameState mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga != null)
            handleGAMESTATE(ga, mes.getState());
    }

    /**
     * Handle game state message: Update {@link SOCGame}.
     * Call for any message type which contains a Game State field.
     * Although this method is simple, it's useful as a central place to update that state.
     *
     * @param ga  Game to update state; not null
     * @param newState  New state from message, like {@link SOCGame#ROLL_OR_CARD}, or 0. Does nothing if 0.
     * @see #handleGAMESTATE(SOCGameState)
     * @since 2.0.00
     */
    public static void handleGAMESTATE(final SOCGame ga, final int newState)
    {
        if (newState == 0)
            return;

        ga.setGameState(newState);
    }

    /**
     * Server has declined player's request.
     * Unless {@link SOCDeclinePlayerRequest#gameState} is 0 or same as current {@link SOCGame#getGameState()},
     * calls {@link #handleGAMESTATE(SOCGame, int)}.
     * @since 2.5.00
     */
    public static void handleDECLINEPLAYERREQUEST(final SOCDeclinePlayerRequest mes, final SOCGame ga)
    {
        if (ga == null)
            return;

        final int currState = mes.gameState;
        if ((currState != 0) && (currState != ga.getGameState()))
            handleGAMESTATE(ga, currState);
    }

    /**
     * handle the "set turn" message
     * @param mes  the message
     */
    protected void handleSETTURN(SOCSetTurn mes)
    {
        handleGAMEELEMENT(games.get(mes.getGame()), GEType.CURRENT_PLAYER, mes.getPlayerNumber());
    }

    /**
     * handle the "first player" message
     * @param mes  the message
     */
    protected void handleFIRSTPLAYER(SOCFirstPlayer mes)
    {
        handleGAMEELEMENT(games.get(mes.getGame()), GEType.FIRST_PLAYER, mes.getPlayerNumber());
    }

    /**
     * handle the "turn" message
     * @param mes  the message
     */
    protected void handleTURN(SOCTurn mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        handleGAMESTATE(ga, mes.getGameState());

        ga.setCurrentPlayerNumber(mes.getPlayerNumber());
        ga.updateAtTurn();
    }

    /**
     * Handle the PlayerElements message: Finds game by name, and loops calling
     * {@link #handlePLAYERELEMENT(SOCGame, SOCPlayer, int, int, int, int)}.
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handlePLAYERELEMENTS(final SOCPlayerElements mes)
    {
        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final int pn = mes.getPlayerNumber();
        final SOCPlayer pl = (pn != -1) ? ga.getPlayer(pn) : null;
        final int action = mes.getAction();
        final int[] etypes = mes.getElementTypes(), amounts = mes.getAmounts();

        for (int i = 0; i < etypes.length; ++i)
            handlePLAYERELEMENT(ga, pl, pn, action, PEType.valueOf(etypes[i]), amounts[i], nickname);

        if ((action == SOCPlayerElement.SET) && (etypes.length == 5) && (etypes[0] == SOCResourceConstants.CLAY)
            && (pl != null) && (ga.getGameState() == SOCGame.ROLL_OR_CARD))
            // dice roll results: when sent all known resources, clear UNKNOWN to 0
            pl.getResources().setAmount(0, SOCResourceConstants.UNKNOWN);
    }

    /**
     * handle the "player information" message: Finds game by name and calls
     * {@link #handlePLAYERELEMENT(SOCGame, SOCPlayer, int, int, int, int)}.
     * @param mes  the message
     */
    protected void handlePLAYERELEMENT(SOCPlayerElement mes)
    {
        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final int pn = mes.getPlayerNumber();
        final int action = mes.getAction(), amount = mes.getAmount();
        final PEType etype = PEType.valueOf(mes.getElementType());

        handlePLAYERELEMENT(ga, null, pn, action, etype, amount, nickname);
    }

    /**
     * Handle a player information update from a {@link SOCPlayerElement} or {@link SOCPlayerElements} message.
     * @param ga   Game to update; does nothing if null
     * @param pl   Player to update; some elements take null. If null and {@code pn != -1}, will find {@code pl}
     *     using {@link SOCGame#getPlayer(int) ga.getPlayer(pn)}.
     * @param pn   Player number from message (sometimes -1 for none or all)
     * @param action   {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN GAIN},
     *     or {@link SOCPlayerElement#LOSE LOSE}
     * @param etype  Element type, such as {@link PEType#SETTLEMENTS} or {@link PEType#NUMKNIGHTS}.
     *     Does nothing if {@code null}.
     * @param amount  The new value to set, or the delta to gain/lose
     * @param nickname  Our client player nickname/username, for the only element where that matters:
     *     {@link PEType#RESOURCE_COUNT}. Can be {@code null} otherwise.
     * @since 2.0.00
     */
    public static final void handlePLAYERELEMENT
        (final SOCGame ga, SOCPlayer pl, final int pn, final int action,
         final PEType etype, final int amount, final String nickname)
    {
        if ((ga == null) || (etype == null))
            return;
        if ((pl == null) && (pn != -1))
            pl = ga.getPlayer(pn);

        switch (etype)
        {
        case ROADS:
            handlePLAYERELEMENT_numPieces(pl, action, SOCPlayingPiece.ROAD, amount);
            break;

        case SETTLEMENTS:
            handlePLAYERELEMENT_numPieces(pl, action, SOCPlayingPiece.SETTLEMENT, amount);
            break;

        case CITIES:
            handlePLAYERELEMENT_numPieces(pl, action, SOCPlayingPiece.CITY, amount);
            break;

        case SHIPS:
            handlePLAYERELEMENT_numPieces(pl, action, SOCPlayingPiece.SHIP, amount);
            break;

        case NUMKNIGHTS:
            // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
            handlePLAYERELEMENT_numKnights(ga, pl, action, amount);
            break;

        case CLAY:
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.CLAY, amount);
            break;

        case ORE:
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.ORE, amount);
            break;

        case SHEEP:
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.SHEEP, amount);
            break;

        case WHEAT:
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.WHEAT, amount);
            break;

        case WOOD:
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.WOOD, amount);
            break;

        case UNKNOWN_RESOURCE:
            /**
             * Note: if losing unknown resources, we first
             * convert player's known resources to unknown resources,
             * then remove mes's unknown resources from player.
             */
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.UNKNOWN, amount);
            break;

        default:
            handlePLAYERELEMENT_simple(ga, pl, pn, action, etype, amount, nickname);
            break;

        }
    }

    /**
     * Update game data for a simple player element or flag, for
     * {@link #handlePLAYERELEMENT(SOCGame, SOCPlayer, int, int, int, int)}.
     * Handles ASK_SPECIAL_BUILD, NUM_PICK_GOLD_HEX_RESOURCES, SCENARIO_CLOTH_COUNT, etc.
     *<P>
     * To avoid code duplication, also called from
     * {@link soc.client.MessageHandler#handlePLAYERELEMENT(SOCPlayerElement)}
     * and {@link soc.robot.SOCRobotBrain#run()}.
     *<P>
     * Before v2.0.00 this method directly took a {@link SOCPlayerElement} instead of that message's
     * {@code action}, {@code etype}, and {@code val} fields.
     *
     * @param ga   Game to update
     * @param pl   Player to update; some elements take null. If null and {@code pn != -1}, will find {@code pl}
     *     using {@link SOCGame#getPlayer(int) ga.getPlayer(pn)}.
     * @param pn   Player number from message (sometimes -1 for none)
     * @param action   {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN GAIN},
     *     or {@link SOCPlayerElement#LOSE LOSE}
     * @param etype  Element type, such as {@link PEType#SETTLEMENTS} or {@link PEType#NUMKNIGHTS}.
     *     Does nothing if {@code null}.
     * @param val  The new value to set, or the delta to gain/lose
     * @param cliNickname  Our client player nickname/username, for the only element where that matters:
     *     {@link PEType#RESOURCE_COUNT}, or {@code null} if {@code pl} is definitely not the client player.
     *     Can be {@code null} for all other element types.
     * @since 2.0.00
     */
    public static void handlePLAYERELEMENT_simple
        (SOCGame ga, SOCPlayer pl, final int pn, final int action,
         final PEType etype, final int val, final String cliNickname)
    {
        if (etype == null)
            return;
        if ((pl == null) && (pn != -1))
            pl = ga.getPlayer(pn);

        switch (etype)
        {
        case ASK_SPECIAL_BUILD:
            if (0 != val)
            {
                try {
                    ga.askSpecialBuild(pn, false);  // set per-player, per-game flags
                }
                catch (RuntimeException e) {}
            } else {
                pl.setAskedSpecialBuild(false);
            }
            break;

        case HAS_SPECIAL_BUILT:
            pl.setSpecialBuilt((0 != val));
            break;

        case RESOURCE_COUNT:
            {
                SOCResourceSet rsrcs = pl.getResources();
                if (val != rsrcs.getTotal())
                {
                    // Update count if possible; convert known to unknown if needed.
                    // For our own player, server sends resource specifics, not just total count

                    if ((cliNickname == null) || ! pl.getName().equals(cliNickname))
                    {
                        int numKnown = rsrcs.getKnownTotal();
                        if (numKnown <= val)
                        {
                            rsrcs.setAmount(val - numKnown, SOCResourceConstants.UNKNOWN);
                        } else {
                            rsrcs.clear();
                            rsrcs.setAmount(val, SOCResourceConstants.UNKNOWN);
                        }
                    }
                }
            }
            break;

        case LAST_SETTLEMENT_NODE:
            pl.setLastSettlementCoord(val);
            break;

        case PLAYED_DEV_CARD_FLAG:
            {
                final boolean changeTo = (val != 0);
                if (pn != -1)
                {
                    pl.setPlayedDevCard(changeTo);
                    if (! changeTo)
                        ga.setPlacingRobberForKnightCard(false);
                } else {
                    for (int p = 0; p < ga.maxPlayers; ++p)
                        ga.getPlayer(p).setPlayedDevCard(changeTo);
                }
            }
            break;

        case DISCARD_FLAG:
            pl.setNeedToDiscard(val != 0);
            break;

        case NUM_PLAYED_DEV_CARD_DISC:
            pl.numDISCCards = val;
            break;

        case NUM_PLAYED_DEV_CARD_MONO:
            pl.numMONOCards = val;
            break;

        case NUM_PLAYED_DEV_CARD_ROADS:
            pl.numRBCards = val;
            break;

        case NUM_UNDOS_REMAINING:
            pl.setUndosRemaining(val);
            break;

        case NUM_PICK_GOLD_HEX_RESOURCES:
            pl.setNeedToPickGoldHexResources(val);
            break;

        case SCENARIO_SVP:
            pl.setSpecialVP(val);
            break;

        case PLAYEREVENTS_BITMASK:
            pl.setPlayerEvents(val);
            break;

        case SCENARIO_SVP_LANDAREAS_BITMASK:
            pl.setScenarioSVPLandAreas(val);
            break;

        case STARTING_LANDAREAS:
            pl.setStartingLandAreasEncoded(val);
            break;

        case SCENARIO_CLOTH_COUNT:
            if (pn != -1)
                pl.setCloth(val);
            else
                ((SOCBoardLarge) (ga.getBoard())).setCloth(val);
            break;

        case SCENARIO_WARSHIP_COUNT:
            switch (action)
            {
            case SOCPlayerElement.SET:
                pl.setNumWarships(val);
                break;

            case SOCPlayerElement.GAIN:
                pl.setNumWarships(pl.getNumWarships() + val);
                break;
            }
            break;

        default:
            ;  // no action needed, default is only to avoid compiler warning
        }
    }

    /**
     * Update a player's amount of a playing piece, for
     * {@link #handlePLAYERELEMENT(SOCGame, SOCPlayer, int, int, int, int)}.
     * To avoid code duplication, also called from
     * {@link soc.client.MessageHandler#handlePLAYERELEMENT(SOCPlayerElement)}
     * and {@link soc.robot.SOCRobotBrain#run()}.
     *<P>
     * Before v2.0.00 this method directly took a {@link SOCPlayerElement} instead of that message's
     * {@code action} and {@code amount} fields.
     *
     * @param pl        Player to update
     * @param action   {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN GAIN},
     *     or {@link SOCPlayerElement#LOSE LOSE}
     * @param pieceType Playing piece type, like {@link SOCPlayingPiece#ROAD}
     * @param amount    The new value to set, or the delta to gain/lose
     * @since 1.1.00
     */
    public static void handlePLAYERELEMENT_numPieces
        (final SOCPlayer pl, final int action, final int pieceType, final int amount)
    {
        switch (action)
        {
        case SOCPlayerElement.SET:
            pl.setNumPieces(pieceType, amount);
            break;

        case SOCPlayerElement.GAIN:
            pl.setNumPieces(pieceType, pl.getNumPieces(pieceType) + amount);
            break;

        case SOCPlayerElement.LOSE:
            pl.setNumPieces(pieceType, pl.getNumPieces(pieceType) - amount);
            break;
        }
    }

    /**
     * Update a player's amount of knights, and game's largest army,
     * for {@link #handlePLAYERELEMENT(SOCGame, SOCPlayer, int, int, int, int)}.
     * Calls {@link SOCGame#updateLargestArmy() ga.updateLargestArmy()}.
     * To avoid code duplication, also called from
     * {@link soc.client.MessageHandler#handlePLAYERELEMENT(SOCPlayerElement)}
     * and {@link soc.robot.SOCRobotBrain#run()}.
     *<P>
     * Before v2.0.00 this method directly took a {@link SOCPlayerElement} instead of that message's
     * {@code action} and {@code amount} fields.
     *
     * @param ga   Game of player
     * @param pl   Player to update
     * @param action   {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN GAIN},
     *     or {@link SOCPlayerElement#LOSE LOSE}
     * @param amount    The new value to set, or the delta to gain/lose
     * @since 1.1.00
     */
    public static void handlePLAYERELEMENT_numKnights
        (final SOCGame ga, final SOCPlayer pl, final int action, final int amount)
    {
        switch (action)
        {
        case SOCPlayerElement.SET:
            pl.setNumKnights(amount);
            break;

        case SOCPlayerElement.GAIN:
            pl.setNumKnights(pl.getNumKnights() + amount);
            break;

        case SOCPlayerElement.LOSE:
            pl.setNumKnights(pl.getNumKnights() - amount);
            break;
        }

        ga.updateLargestArmy();
    }

    /**
     * Update a player's amount of a resource, for {@link #handlePLAYERELEMENT(SOCGame, SOCPlayer, int, int, int, int)}.
     *<ul>
     *<LI> If this is a {@link SOCPlayerElement#LOSE} action,
     *     and the player does not have enough of that {@code rtype},
     *     the rest are taken from the player's UNKNOWN rtype amount.
     *     (This often happens for non-client players).
     *<LI> If we are losing from {@code rtype} UNKNOWN,
     *     first convert player's known resources to unknown resources
     *     (individual amount information will be lost),
     *     then remove mes's unknown resources from player.
     *</ul>
     *<P>
     * For consistent resource management and to avoid code duplication, is also called from
     * {@link soc.client.MessageHandler#handlePLAYERELEMENT(SOCPlayerElement)},
     * {@link #handleDISCARD(SOCDiscard, SOCGame)}, and {@link soc.robot.SOCRobotBrain#run()}.
     *<P>
     * Before v2.0.00 this method directly took a {@link SOCPlayerElement} instead of that message's
     * {@code action} and {@code amount} fields.
     *
     * @param pl     Player to update
     * @param action   {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN GAIN},
     *     or {@link SOCPlayerElement#LOSE LOSE}
     * @param rtype  Type of resource, like {@link SOCResourceConstants#CLAY}
     * @param amount    The new value to set, or the delta to gain/lose
     * @since 1.1.00
     */
    public static void handlePLAYERELEMENT_numRsrc
        (final SOCPlayer pl, final int action, final int rtype, final int amount)
    {
        switch (action)
        {
        case SOCPlayerElement.SET:
            pl.getResources().setAmount(amount, rtype);
            break;

        case SOCPlayerElement.GAIN:
            pl.getResources().add(amount, rtype);
            break;

        case SOCPlayerElement.LOSE:
            pl.getResources().subtract(amount, rtype, true);
            break;
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
        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final int[] etypes = mes.getElementTypes(), values = mes.getValues();
        for (int i = 0; i < etypes.length; ++i)
            handleGAMEELEMENT(ga, GEType.valueOf(etypes[i]), values[i]);
    }

    /**
     * Update one game element field from a {@link SOCGameElements} message.
     * @param ga   Game to update; does nothing if null
     * @param etype  Element type, such as {@link GEType#ROUND_COUNT} or {@link GEType#DEV_CARD_COUNT}.
     *     Does nothing if {@code null}.
     * @param value  The new value to set
     * @since 2.0.00
     */
    public static final void handleGAMEELEMENT
        (final SOCGame ga, final GEType etype, final int value)
    {
        if ((ga == null) || (etype == null))
            return;

        switch (etype)
        {
        case ROUND_COUNT:
            ga.setRoundCount(value);
            break;

        case DEV_CARD_COUNT:
            ga.setNumDevCards(value);
            break;

        case FIRST_PLAYER:
            ga.setFirstPlayer(value);
            break;

        case CURRENT_PLAYER:
            ga.setCurrentPlayerNumber(value);
            break;

        case LARGEST_ARMY_PLAYER:
            ga.setPlayerWithLargestArmy((value != -1) ? ga.getPlayer(value) : null);
            break;

        case LONGEST_ROAD_PLAYER:
            ga.setPlayerWithLongestRoad((value != -1) ? ga.getPlayer(value) : null);
            break;

        case SPECIAL_BUILDING_AFTER_PLAYER:
            ga.setSpecialBuildingPlayerNumberAfter(value);
            break;

        case SHIP_PLACED_THIS_TURN_EDGE:
            ga.addShipPlacedThisTurn(value);
            break;

        case IS_PLACING_ROBBER_FOR_KNIGHT_CARD_FLAG:
            ga.setPlacingRobberForKnightCard(value != 0);
            break;

        case HAS_BUILT_CITY_N7C:
            ga.setHasBuiltCity(value != 0);
            break;

        case UNKNOWN_TYPE:
            ;  // no action needed, UNKNOWN_TYPE is mentioned only to avoid compiler warning
        }
    }

    /**
     * handle "resource count" message
     * @param mes  the message
     */
    protected void handleRESOURCECOUNT(SOCResourceCount mes)
    {
        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        handlePLAYERELEMENT_simple
            (ga, null, mes.getPlayerNumber(), SOCPlayerElement.SET,
             PEType.RESOURCE_COUNT, mes.getCount(), nickname);
    }

    /**
     * handle the "set last settlement" message.
     *<P>
     * This method is public static for access by other client classes.
     * @param mes  the message
     * @param ga  Message's game from {@link SOCLastSettlement#getGame()}; if {@code null}, message is ignored
     * @since 2.0.00
     */
    public static final void handleLASTSETTLEMENT(SOCLastSettlement mes, final SOCGame ga)
    {
        if (ga == null)
            return;

        handlePLAYERELEMENT_simple
            (ga, null, mes.getPlayerNumber(), SOCPlayerElement.SET,
             PEType.LAST_SETTLEMENT_NODE, mes.getCoordinates(), null);
    }

    /**
     * handle the "dice result" message
     * @param mes  the message
     */
    protected void handleDICERESULT(SOCDiceResult mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        ga.setCurrentDice(mes.getResult());
    }

    /**
     * handle the "put piece" message
     *<P>
     * This method is public static for access by
     * {@code SOCRobotBrain.handlePUTPIECE_updateGameData(SOCPutPiece)}.
     * @param mes  the message
     * @param ga  Message's game from {@link SOCPutPiece#getGame()}; if {@code null}, message is ignored
     */
    public static void handlePUTPIECE(final SOCPutPiece mes, final SOCGame ga)
    {
        if (ga == null)
            return;

        final int pieceType = mes.getPieceType();
        final int coord = mes.getCoordinates();
        final SOCPlayer pl = (pieceType != SOCPlayingPiece.VILLAGE)
            ? ga.getPlayer(mes.getPlayerNumber())
            : null;

        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:
            ga.putPiece(new SOCRoad(pl, coord, null));
            break;

        case SOCPlayingPiece.SETTLEMENT:
            ga.putPiece(new SOCSettlement(pl, coord, null));
            break;

        case SOCPlayingPiece.CITY:
            ga.putPiece(new SOCCity(pl, coord, null));
            break;

        case SOCPlayingPiece.SHIP:
            ga.putPiece(new SOCShip(pl, coord, null));
            break;

        case SOCPlayingPiece.FORTRESS:
            ga.putPiece(new SOCFortress(pl, coord, ga.getBoard()));
            break;

        case SOCPlayingPiece.VILLAGE:
            ga.putPiece(new SOCVillage(coord, ga.getBoard()));
            break;

        default:
            System.err.println
                ("Displayless.handlePUTPIECE: game " + ga.getName() + ": Unknown pieceType " + pieceType);
        }
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
    *       This handler method calls <tt>{@link SOCGame#undoPutInitSettlement(SOCPlayingPiece) ga.undoPutInitSettlement}
    *       (new SOCSettlement {@link SOCPlayer#getLastSettlementCoord() (currPlayer.getLastSettlementCoord())})</tt>.
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
    *
    * @param mes  the message
    * @since 1.1.00
    */
    protected void handleCANCELBUILDREQUEST(SOCCancelBuildRequest mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final int sta = ga.getGameState();
        if ((sta != SOCGame.START1B) && (sta != SOCGame.START2B) && (sta != SOCGame.START3B))
        {
            // The human player gets a text message from the server informing
            // about the bad piece placement.  So, we can ignore this message type.
            // The robot player will override this method and react.
            return;
        }
        if (mes.getPieceType() != SOCPlayingPiece.SETTLEMENT)
            return;

        SOCPlayer pl = ga.getPlayer(ga.getCurrentPlayerNumber());
        SOCSettlement pp = new SOCSettlement(pl, pl.getLastSettlementCoord(), null);
        ga.undoPutInitSettlement(pp);
    }

    /**
     * A player has discarded resources. Update player data by calling
     * {@link #handlePLAYERELEMENT_numRsrc(SOCPlayer, int, int, int)}
     * for each resource type in the message.
     *
     * @param mes  the message
     * @param ga  game object for {@link SOCMessageForGame#getGame() mes.getGame()}; if {@code null}, message is ignored
     * @return  the player discarding, for convenience of player client,
     *     or {@code null} if {@link SOCDiscard#getPlayerNumber()} out of range
     * @since 2.5.00
     */
    public static SOCPlayer handleDISCARD(final SOCDiscard mes, final SOCGame ga)
    {
        if (ga == null)
            return null;
        final int pn = mes.getPlayerNumber();
        if ((pn < 0) || (pn >= ga.maxPlayers))
            return null;

        // for consistent resource management, calls handlePLAYERELEMENT_numRsrc
        // instead of SOCResourceSet.subtract directly

        final SOCPlayer pl = ga.getPlayer(pn);
        final ResourceSet res = mes.getResources();
        for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.UNKNOWN; ++rtype)
        {
            int amount = res.getAmount(rtype);
            if (amount != 0)
                handlePLAYERELEMENT_numRsrc(pl, SOCPlayerElement.LOSE, rtype, amount);
        }

        return pl;
    }

    /**
     * handle the "robber moved" or "pirate moved" message.
     * @param mes  the message
     */
    protected void handleMOVEROBBER(SOCMoveRobber mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        /**
         * Note: Don't call ga.moveRobber() because that will call the
         * functions to do the stealing.  We just want to say where
         * the robber moved without seeing if something was stolen.
         */
        ga.setPlacingRobberForKnightCard(false);
        final int newHex = mes.getCoordinates();
        if (newHex > 0)
            ga.getBoard().setRobberHex(newHex, true);
        else
            ((SOCBoardLarge) ga.getBoard()).setPirateHex(-newHex, true);
    }

    /**
     * handle the "discard request" message
     * @param mes  the message
     */
    protected void handleDISCARDREQUEST(SOCDiscardRequest mes) {}

    /**
     * handle the "choose player request" message
     * @param mes  the message
     */
    protected void handleCHOOSEPLAYERREQUEST(SOCChoosePlayerRequest mes) {}

    /**
     * Handle the "robbery result" message.
     * Updates game data by calling {@link #handlePLAYERELEMENT_numRsrc(SOCPlayer, int, int, int)}
     * or {@link #handlePLAYERELEMENT(SOCGame, SOCPlayer, int, int, PEType, int, String)}.
     * Does nothing if {@link SOCRobberyResult#isGainLose mes.isGainLose} but
     * {@link SOCRobberyResult#amount mes.amount} == 0 and {@link SOCRobberyResult#resSet} is null.
     *<P>
     * This method is public static for access by {@code SOCPlayerClient} and robot client classes.
     *
     * @param mes  the message
     * @param ga  game object for {@link SOCMessageForGame#getGame() mes.getGame()}; if {@code null}, message is ignored
     * @since 2.5.00
     */
    public static void handleROBBERYRESULT(final SOCRobberyResult mes, SOCGame ga)
    {
        if (ga == null)
            return;

        final int perpPN = mes.perpPN, victimPN = mes.victimPN, amount = mes.amount;
        final SOCResourceSet resSet = mes.resSet;
        if (mes.isGainLose && (amount == 0) && (resSet == null))
            return;

        final SOCPlayer perp = (perpPN >= 0) ? ga.getPlayer(perpPN) : null,
            victim = (victimPN >= 0) ? ga.getPlayer(victimPN) : null;

        final PEType peType = mes.peType;
        if (peType != null)
        {
            if (mes.isGainLose)
            {
                if (perp != null)
                    handlePLAYERELEMENT(ga, perp, perpPN, SOCPlayerElement.GAIN, peType, amount, null);
                if (victim != null)
                    handlePLAYERELEMENT(ga, victim, victimPN, SOCPlayerElement.LOSE, peType, amount, null);
            } else {
                if (perp != null)
                    handlePLAYERELEMENT(ga, perp, perpPN, SOCPlayerElement.SET, peType, amount, null);
                if (victim != null)
                    handlePLAYERELEMENT(ga, victim, victimPN, SOCPlayerElement.SET, peType, mes.victimAmount, null);
            }
        } else if (resSet != null) {
            // note: when using resSet, isGainLose is always true
            for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype)
            {
                final int amt = resSet.getAmount(rtype);
                if (amt == 0)
                    continue;

                if (perp != null)
                    handlePLAYERELEMENT_numRsrc(perp, SOCPlayerElement.GAIN, rtype, amt);
                if (victim != null)
                    handlePLAYERELEMENT_numRsrc(victim, SOCPlayerElement.LOSE, rtype, amt);
            }
        } else {
            final int resType = mes.resType;

            if (mes.isGainLose)
            {
                if (perp != null)
                    handlePLAYERELEMENT_numRsrc(perp, SOCPlayerElement.GAIN, resType, amount);
                if (victim != null)
                    handlePLAYERELEMENT_numRsrc(victim, SOCPlayerElement.LOSE, resType, amount);
            } else {
                if (perp != null)
                    handlePLAYERELEMENT_numRsrc(perp, SOCPlayerElement.SET, resType, amount);
                if (victim != null)
                    handlePLAYERELEMENT_numRsrc(victim, SOCPlayerElement.SET, resType, mes.victimAmount);
            }
        }
    }

    /**
     * handle the "make offer" message.
     * @param mes  the message
     */
    protected void handleMAKEOFFER(SOCMakeOffer mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        SOCTradeOffer offer = mes.getOffer();
        ga.getPlayer(offer.getFrom()).setCurrentOffer(offer);
    }

    /**
     * handle the "clear offer" message
     * @param mes  the message
     */
    protected void handleCLEAROFFER(SOCClearOffer mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final int pn = mes.getPlayerNumber();
        if (pn != -1)
        {
            ga.getPlayer(pn).setCurrentOffer(null);
        } else {
            for (int i = 0; i < ga.maxPlayers; ++i)
                ga.getPlayer(i).setCurrentOffer(null);
        }
    }

    /**
     * handle the "reject offer" message
     * @param mes  the message
     */
    protected void handleREJECTOFFER(SOCRejectOffer mes) {}

    /**
     * Update game data for a trade between players: static to share with SOCPlayerClient.
     * Calls {@link SOCGame#makeTrade(int, int)} if trade details match current offer,
     * to update player resources and {@link SOCPlayer#getResourceTradeStats()}.
     * Otherwise directly updates player resources.
     *<P>
     * Added for v2.5.00, the first server version to include trade details in {@code SOCAcceptOffer} message.
     * Older servers send PLAYERELEMENT messages before ACCEPTOFFER instead, and their ACCEPTOFFER won't have
     * trade detail fields; this method does nothing if those fields are {@code null}.
     *
     *<H3>Threads:</H3>
     * If client is multi-threaded (for example, robot with a message treater thread and per-game brain threads),
     * call this method from the same thread that needs the updated player resource data.
     * Other threads may cache stale values for the resource count fields.
     *
     * @param mes  Message data
     * @param ga  Game to update
     * @since 2.5.00
     */
    public static void handleACCEPTOFFER(final SOCAcceptOffer mes, final SOCGame ga)
    {
        if (ga == null)
            return;

        final SOCResourceSet resToAccPl = mes.getResToAcceptingPlayer();
        if (resToAccPl == null)
            return;

        final int offPN = mes.getOfferingNumber(), accPN = mes.getAcceptingNumber();
        final SOCResourceSet resToOffPl = mes.getResToOfferingPlayer();

        // Sanity check message resources vs trade offer info;
        // if everything matches, call ga.makeTrade to update stats too
        final SOCPlayer offPl = ga.getPlayer(offPN);
        final SOCTradeOffer offOffered = offPl.getCurrentOffer();
        if ((offOffered != null)
            && resToOffPl.equals(offOffered.getGetSet()) && resToAccPl.equals(offOffered.getGiveSet()))
        {
            ga.makeTrade(offPN, accPN);
        } else {
            final SOCResourceSet accPlRes = ga.getPlayer(accPN).getResources(), offPlRes = offPl.getResources();

            accPlRes.add(resToAccPl);
            accPlRes.subtract(resToOffPl, true);
            offPlRes.add(resToOffPl);
            offPlRes.subtract(resToAccPl, true);
        }
    }

    /**
     * handle the "clear trade message" message
     * @param mes  the message
     */
    protected void handleCLEARTRADEMSG(SOCClearTradeMsg mes) {}

    /**
     * Update a player's resource data from a "bank trade" announcement from the server.
     * Calls {@link SOCPlayer#makeBankTrade(ResourceSet, ResourceSet)} to update player resources
     * and {@link SOCPlayer#getResourceTradeStats()}.
     * See {@link #handlePLAYERELEMENT_numRsrc(SOCPlayer, int, int, int)} for behavior
     * if subtracting more than the known amount of those resources
     * (which often happens for non-client players).
     *<P>
     * Call this method only if server is v2.5.00 or newer ({@link SOCBankTrade#VERSION_FOR_OMIT_PLAYERELEMENTS}).
     * Older servers send PLAYERELEMENT messages before BANKTRADE, so calling this would subtract/add resources twice.
     *
     *<H3>Threads:</H3>
     * If client is multi-threaded (for example, robot with a message treater thread and per-game brain threads),
     * call this method from the same thread that needs the updated player resource data.
     * Other threads may cache stale values for the resource count fields.
     *
     * @param mes  the message
     * @param ga  Game to update, from Map of games the client is playing,
     *     or {@code null} if not found in that map
     * @return  True if updated, false if {@code ga} is null
     * @since 2.5.00
     */
    public static boolean handleBANKTRADE(final SOCBankTrade mes, final SOCGame ga)
    {
        if (ga == null)
            return false;

        ga.getPlayer(mes.getPlayerNumber()).makeBankTrade(mes.getGiveSet(), mes.getGetSet());

        return true;
    }

    /**
     * handle the "number of development cards" message
     * @param mes  the message
     */
    protected void handleDEVCARDCOUNT(SOCDevCardCount mes)
    {
        handleGAMEELEMENT(games.get(mes.getGame()), GEType.DEV_CARD_COUNT, mes.getNumDevCards());
    }

    /**
     * handle the "development card action" message for 1 card in this game.
     * Ignores messages where {@link SOCDevCardAction#getCardTypes()} != {@code null}.
     * @param isPractice  Is the server local, or remote?  Client can be connected
     *                only to local, or remote.
     * @param mes  the message
     */
    protected void handleDEVCARDACTION(final boolean isPractice, final SOCDevCardAction mes)
    {
        if (mes.getCardTypes() != null)
            return;  // <--- ignore: bots don't care about game-end VP card reveals ---

        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());

        int ctype = mes.getCardType();
        if ((! isPractice) && (sVersion < SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES))
        {
            if (ctype == SOCDevCardConstants.KNIGHT_FOR_VERS_1_X)
                ctype = SOCDevCardConstants.KNIGHT;
            else if (ctype == SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X)
                ctype = SOCDevCardConstants.UNKNOWN;
        }

        handleDEVCARDACTION(ga, player, mes.getAction(), ctype);
    }

    /**
     * Handle one dev card's game data update for {@link #handleDEVCARDACTION(boolean, SOCDevCardAction)}.
     * For {@link SOCDevCardAction#PLAY}, calls {@link SOCPlayer#updateDevCardsPlayed(int, boolean)}.
     *
     * @param ga  Game being updated
     * @param player  Player in {@code ga} being updated
     * @param act  Action being done: {@link SOCDevCardAction#DRAW}, {@link SOCDevCardAction#PLAY PLAY},
     *     {@link SOCDevCardAction#ADD_OLD ADD_OLD}, or {@link SOCDevCardAction#ADD_NEW ADD_NEW}
     * @param ctype  Type of development card from {@link SOCDevCardConstants}
     * @see soc.client.MessageHandler#handleDEVCARDACTION(SOCGame, SOCPlayer, boolean, int, int)
     * @since 2.5.00
     */
    protected void handleDEVCARDACTION
        (final SOCGame ga, final SOCPlayer player, final int act, final int ctype)
    {
        // if you change this method, consider changing MessageHandler.handleDEVCARDACTION
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

        case SOCDevCardAction.REMOVE_OLD:
            player.getInventory().removeDevCard(SOCInventory.OLD, ctype);
            break;

        case SOCDevCardAction.REMOVE_NEW:
            player.getInventory().removeDevCard(SOCInventory.NEW, ctype);
            break;
        }
    }

    /**
     * handle the "set played dev card flag" message
     * @param mes  the message
     */
    protected void handleSETPLAYEDDEVCARD(SOCSetPlayedDevCard mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        handlePLAYERELEMENT_simple
            (ga, null, mes.getPlayerNumber(), SOCPlayerElement.SET,
             PEType.PLAYED_DEV_CARD_FLAG, mes.hasPlayedDevCard() ? 1 : 0, null);
    }

    /**
     * Handle the "Player has Picked Resources" message by updating player resource data.
     * @param mes  the message
     * @param ga  Game to update
     * @return  True if updated, false if player number not found
     * @since 2.5.00
     */
    public static boolean handlePICKRESOURCES
        (final SOCPickResources mes, final SOCGame ga)
    {
        final SOCPlayer pl = ga.getPlayer(mes.getPlayerNumber());
        if (pl == null)
            return false;

        pl.getResources().add(mes.getResources());

        return true;
    }

    /**
     * Handle the "inventory item action" message by updating player inventory.
     * @param games  The hashtable of client's {@link SOCGame}s; key = game name
     * @param mes  the message
     * @return  True if this message is a "cannot play this type now" from server for our client player.
     * @since 2.0.00
     */
    @SuppressWarnings("fallthrough")
    public static boolean handleINVENTORYITEMACTION(Hashtable<String, SOCGame> games, SOCInventoryItemAction mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return false;

        if ((mes.playerNumber == -1) || (mes.action == SOCInventoryItemAction.CANNOT_PLAY))
            return true;

        SOCPlayer pl = ga.getPlayer(mes.playerNumber);
        if (pl == null)
            return false;

        SOCInventory inv = pl.getInventory();
        SOCInventoryItem item = null;

        switch (mes.action)
        {
        case SOCInventoryItemAction.ADD_PLAYABLE:
            // fall through

        case SOCInventoryItemAction.ADD_OTHER:
            inv.addItem(SOCInventoryItem.createForScenario
                (ga, mes.itemType, (mes.action == SOCInventoryItemAction.ADD_PLAYABLE),
                 mes.isKept, mes.isVP, mes.canCancelPlay));
            break;

        case SOCInventoryItemAction.PLAYED:
            if (mes.isKept)
                inv.keepPlayedItem(mes.itemType);
            else
                item = inv.removeItem(SOCInventory.PLAYABLE, mes.itemType);

            if (! SOCInventoryItem.isPlayForPlacement(ga, mes.itemType))
                break;
            // fall through to PLACING_EXTRA if isPlayForPlacement

        case SOCInventoryItemAction.PLACING_EXTRA:
            if (item == null)
                item = SOCInventoryItem.createForScenario
                    (ga, mes.itemType, true, mes.isKept, mes.isVP, mes.canCancelPlay);

            ga.setPlacingItem(item);
            break;

        // case SOCInventoryItemAction.CANNOT_PLAY: already covered above: returns true

        case SOCInventoryItemAction.REMOVE_PLAYABLE:
            inv.removeItem(SOCInventory.PLAYABLE, mes.itemType);
            break;

        case SOCInventoryItemAction.REMOVE_OTHER:
            inv.removeItem(mes.isKept ? SOCInventory.KEPT : SOCInventory.NEW, mes.itemType);
            break;
        }

        return false;
    }

    /**
     * handle the "list of potential settlements" message
     * @param mes  the message
     * @param ga  Game to apply message to, for method reuse; does nothing if null
     * @throws IllegalStateException if the board has
     *     {@link SOCBoardLarge#getAddedLayoutPart(String) SOCBoardLarge.getAddedLayoutPart("AL")} != {@code null} but
     *     badly formed (node list number 0, or a node list number not followed by a land area number).
     *     This Added Layout Part is rarely used, and this would be discovered quickly while testing
     *     the board layout that contained it.
     */
    public static void handlePOTENTIALSETTLEMENTS
        (SOCPotentialSettlements mes, final SOCGame ga)
        throws IllegalStateException
    {
        if (ga == null)
            return;

        List<Integer> ps = mes.getPotentialSettlements(false);  // may be null if lan != null
        final HashSet<Integer>[] lan = mes.landAreasLegalNodes;
        final int[] loneSettles;    // usually null, except in _SC_PIRI
        final int[][] legalSeaEdges = mes.legalSeaEdges;  // usually null, except in _SC_PIRI

        int pn = mes.getPlayerNumber();
        if (ga.hasSeaBoard)
        {
            SOCBoardLarge bl = ((SOCBoardLarge) ga.getBoard());
            if ((pn == -1) || ((pn == 0) && bl.getLegalSettlements().isEmpty()))
                bl.setLegalSettlements
                  (ps, mes.startingLandArea, lan);  // throws IllegalStateException if board layout
                                                    // has malformed Added Layout Part "AL"
            loneSettles = bl.getAddedLayoutPart("LS");  // usually null, except in _SC_PIRI
        } else {
            loneSettles = null;
        }

        if (ps == null)
            // bl.setLegalSettlements expects sometimes-null ps,
            // but pl.setPotentialAndLegalSettlements needs non-null
            // from lan[] nodes during game start
            ps = mes.getPotentialSettlements(true);

        if (pn != -1)
        {
            SOCPlayer pl = ga.getPlayer(pn);
            pl.setPotentialAndLegalSettlements(ps, true, lan);
            if (loneSettles != null)
                pl.addLegalSettlement(loneSettles[pn], false);
            if (legalSeaEdges != null)
                pl.setRestrictedLegalShips(legalSeaEdges[0]);
        } else {
            for (pn = ga.maxPlayers - 1; pn >= 0; --pn)
            {
                SOCPlayer pl = ga.getPlayer(pn);
                pl.setPotentialAndLegalSettlements(ps, true, lan);
                if (loneSettles != null)
                    pl.addLegalSettlement(loneSettles[pn], false);
                if (legalSeaEdges != null)
                    pl.setRestrictedLegalShips(legalSeaEdges[pn]);
            }
        }
    }

    /**
     * handle the "change face" message
     * @param mes  the message
     */
    protected void handleCHANGEFACE(SOCChangeFace mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        player.setFaceId(mes.getFaceId());
    }

    /**
     * handle the "reject connection" message
     * @param mes  the message
     */
    protected void handleREJECTCONNECTION(SOCRejectConnection mes)
    {
        rejected = true;
        System.err.println("Rejected by server: " + mes.getText());
        disconnect();
    }

    /**
     * handle the "longest road" message
     * @param mes  the message
     */
    protected void handleLONGESTROAD(SOCLongestRoad mes)
    {
        handleGAMEELEMENT(games.get(mes.getGame()), GEType.LONGEST_ROAD_PLAYER, mes.getPlayerNumber());
    }

    /**
     * handle the "largest army" message
     * @param mes  the message
     */
    protected void handleLARGESTARMY(SOCLargestArmy mes)
    {
        handleGAMEELEMENT(games.get(mes.getGame()), GEType.LARGEST_ARMY_PLAYER, mes.getPlayerNumber());
    }

    /**
     * handle the "set seat lock" message
     * @param mes  the message
     */
    protected void handleSETSEATLOCK(SOCSetSeatLock mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final SOCGame.SeatLockState[] sls = mes.getLockStates();
        if (sls == null)
            ga.setSeatLock(mes.getPlayerNumber(), mes.getLockState());
        else
            ga.setSeatLocks(sls);
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
        SOCGame ga = games.get(gname);
        if (ga == null)
            return;  // Not one of our games

        SOCGame greset = ga.resetAsCopy();
        greset.isPractice = ga.isPractice;
        games.put(gname, greset);
        ga.destroyGame();
    }

    /**
     * process the "game option info" message
     * by calling {@link SOCGameOptionSet#addKnownOption(SOCGameOption)}.
     * If all are now received, sets {@link #allOptsReceived} flag.
     * @param optInfo Info message for this {@link SOCGameOption}
     * @since 2.5.00
     */
    protected void handleGAMEOPTIONINFO(final SOCGameOptionInfo optInfo)
    {
        final SOCGameOption opt = optInfo.getOptionInfo();

        if ((opt.key.equals("-")) && (opt.optType == SOCGameOption.OTYPE_UNKNOWN))
        {
            allOptsReceived = true;
        } else {
            if (allOptsReceived)
                allOptsReceived = false;

            knownOpts.addKnownOption(opt);
        }
    }

    /**
     * Update last-action data game data; may be sent from server while joining a game.
     * @param mes  the message
     * @param ga  Game the client is playing, from {@link SOCMessageForGame#getGame() mes.getGame()},
     *     for method reuse by SOCPlayerClient; does nothing if {@code null}
     * @since 2.7.00
     */
    public static void handleSETLASTACTION(final SOCSetLastAction mes, SOCGame ga)
    {
        if (ga == null)
            return;  // Not one of our games

        GameAction.ActionType at = GameAction.ActionType.valueOf(mes.getActionTypeValue());
        if (at == null)
            ga.setLastAction(null);
        else
            ga.setLastAction(new GameAction
                (at, mes.getParam1(), mes.getParam2(), mes.getParam3(), mes.getRS1(), mes.getRS2()));
    }

    /**
     * Can't undo the most recent action; update game data.
     * If {@link SOCGame#getLastAction() ga.getLastAction()} is {@code null}, does nothing.
     * Otherwise sets lastAction's {@link GameAction#cannotUndoReason .cannotUndoReason} field
     * to {@link SOCUndoNotAllowedReasonText#reason mes.reason}, or to {@code "?"} as fallback
     * if {@code reason} is {@code null} but {@link SOCUndoNotAllowedReasonText#isNotAllowed mes.isNotAllowed} is true.
     * @param mes  the message
     * @param ga  Game the client is playing, from {@link SOCMessageForGame#getGame() mes.getGame()},
     *     for method reuse by SOCPlayerClient; does nothing if {@code null}
     * @since 2.7.00
     */
    public static void handleUNDONOTALLOWEDREASONTEXT
        (final SOCUndoNotAllowedReasonText mes, final SOCGame ga)
    {
        if (ga == null)
            return;  // Not one of our games

        String reason;
        if (! mes.isNotAllowed)
        {
            reason = null;
        } else {
            reason = mes.reason;
            if (reason == null)
                reason = "?";
        }

        final GameAction lastAct = ga.getLastAction();
        if (lastAct == null)
            return;
        lastAct.cannotUndoReason = reason;
    }

    /**
     * Update player statistics.
     * Server currently sends only for client player.
     * This message is handled by some client types, but ignored in the base
     * {@link SOCDisplaylessPlayerClient#treat(SOCMessage)} unless its
     * {@link SOCDisplaylessPlayerClient#ignorePlayerStats} flag is cleared.
     * @param mes  the message
     * @param ga  Game the client is playing, from {@link SOCMessageForGame#getGame() mes.getGame()},
     *     for method reuse by SOCPlayerClient; does nothing if {@code null}
     * @param client player number in {@code ga}; update this player's stats; does nothing if -1
     * @since 2.7.00
     */
    public static void handlePLAYERSTATS(SOCPlayerStats mes, final SOCGame ga, final int clientPN)
    {
        if ((ga == null) || (clientPN == -1))
            return;  // Not one of our games

        final SOCPlayer pl = ga.getPlayer(clientPN);

        final int[] stats = mes.getParams();
        switch (mes.getStatType())
        {
        case SOCPlayerStats.STYPE_RES_ROLL:
            {
                int[] rollStats = new int[1 + SOCResourceConstants.GOLD_LOCAL];
                int rMax = SOCResourceConstants.GOLD_LOCAL;
                if (rMax >= stats.length)
                    rMax = stats.length - 1;
                for (int rtype = SOCResourceConstants.CLAY; rtype <= rMax; ++rtype)
                    rollStats[rtype] = stats[rtype];

                pl.setResourceRollStats(rollStats);
            }
            break;

        case SOCPlayerStats.STYPE_TRADES:
            {
                final int subArrLen = stats[1], resArrLen = subArrLen / 2;
                int numTypes = (stats.length - 2) / subArrLen;  // probably == SOCPlayer.TRADE_STATS_ARRAY_LEN
                SOCResourceSet[][] tradeStats = new SOCResourceSet[2][numTypes];
                for(int i = 2, tradeType = 0; i < stats.length; i += subArrLen, ++tradeType)
                {
                    tradeStats[0][tradeType] = new SOCResourceSet(stats[i], stats[i+1], stats[i+2], stats[i+3], stats[i+4], 0);
                    i += resArrLen;
                    tradeStats[1][tradeType] = new SOCResourceSet(stats[i], stats[i+1], stats[i+2], stats[i+3], stats[i+4], 0);
                    i -= resArrLen;
                }

                pl.setResourceTradeStats(tradeStats);
            }
            break;

        }
    }

    /**
     * Update any game data from "simple request" announcements from the server.
     * Currently ignores them except for:
     *<UL>
     * <LI> {@link SOCSimpleRequest#TRADE_PORT_PLACE TRADE_PORT_PLACE}:
     *     Calls {@link SOCGame#placePort(SOCPlayer, int, int)} if {@code pn} &gt;= 0
     *</UL>
     *
     * @param mes  the message
     * @param ga  Game the client is playing, from {@link SOCMessageForGame#getGame() mes.getGame()},
     *     for method reuse by SOCPlayerClient; does nothing if {@code null}
     * @since 2.0.00
     */
    public static void handleSIMPLEREQUEST(final SOCSimpleRequest mes, final SOCGame ga)
    {
        if (ga == null)
            return;  // Not one of our games

        final int pn = mes.getPlayerNumber(),
            rtype = mes.getRequestType(),
            value1 = mes.getValue1(),
            value2 = mes.getValue2();

        switch (rtype)
        {
            // Types which may update some game data:

            case SOCSimpleRequest.TRADE_PORT_PLACE:
                if (pn >= 0)  // if pn -1, request was rejected
                    ga.placePort(ga.getPlayer(pn), value1, value2);
                break;

            // Known types with no game data update:
            // Catch these before default case, so 'unknown type' won't be printed

            case SOCSimpleRequest.PROMPT_PICK_RESOURCES:
            case SOCSimpleRequest.SC_PIRI_FORT_ATTACK:
                break;

            default:
                // Ignore unknown types.
                // Since the bots and server are almost always the same version, this
                // shouldn't often occur: print for debugging.
                System.err.println
                    ("DPC.handleSIMPLEREQUEST: Unknown type ignored: " + rtype + " in game " + ga.getName());
        }
    }

    /**
     * Update any game data from "simple action" announcements from the server.
     * Currently ignores them except for:
     *<UL>
     * <LI> {@link SOCSimpleAction#TRADE_PORT_REMOVED TRADE_PORT_REMOVED}:
     *     Calls {@link SOCGame#removePort(SOCPlayer, int)}
     *</UL>
     *
     * @param mes  the message
     * @param ga  Game the client is playing, from {@link SOCMessageForGame#getGame() mes.getGame()},
     *     for method reuse by SOCPlayerClient; does nothing if {@code null}
     * @since 1.1.19
     */
    public static void handleSIMPLEACTION(final SOCSimpleAction mes, final SOCGame ga)
    {
        if (ga == null)
            return;  // Not one of our games

        final int atype = mes.getActionType();
        switch (atype)
        {
        // Types which may update some game data:

        case SOCSimpleAction.BOARD_EDGE_SET_SPECIAL:
            {
                final SOCBoard bd = ga.getBoard();
                if (bd instanceof SOCBoardLarge)
                    ((SOCBoardLarge) bd).setSpecialEdge(mes.getValue1(), mes.getValue2());
            }
            break;

        case SOCSimpleAction.TRADE_PORT_REMOVED:
            if (ga.hasSeaBoard)
                ga.removePort(null, mes.getValue1());
            break;

        case SOCSimpleAction.DEVCARD_BOUGHT:
            ga.setNumDevCards(mes.getValue1());
            break;

        case SOCSimpleAction.SC_FTRI_VILLAGE_PLAYER_REMOVED:
            {
                final SOCBoard bd = ga.getBoard();
                if (bd instanceof SOCBoardLarge)
                {
                    final SOCVillage vi = ((SOCBoardLarge) bd).getVillageAtNode(mes.getValue1());
                    if (vi != null)
                        vi.removeTradingPlayer(ga.getPlayer(mes.getPlayerNumber()));
                }
            }
            break;

        // Known types with no game data update:
        // Catch these before default case, so 'unknown type' won't be printed

        case SOCSimpleAction.RSRC_TYPE_MONOPOLIZED:
        case SOCSimpleAction.SC_PIRI_FORT_ATTACK_RESULT:
            // game data updates are sent in preceding or following messages, can ignore this one
            break;

        default:
            // ignore unknown types
            // Since the bots and server are almost always the same version, this
            // shouldn't often occur: print for debugging.
            if (mes.getPlayerNumber() >= 0)
                System.err.println
                    ("handleSIMPLEACTION: Unknown type ignored: " + atype + " in game " + ga.getName());
        }
    }

    /**
     * Handle moving a piece (a ship) around on the board.
     * @since 2.0.00
     */
    protected void handleMOVEPIECE(SOCMovePiece mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games

        SOCShip sh = new SOCShip
            (ga.getPlayer(mes.getPlayerNumber()), mes.getFromCoord(), null);
        ga.moveShip(sh, mes.getToCoord());

    }

    /**
     * A player's piece (a ship) has been removed from the board. Updates game state.
     *<P>
     * Currently, only ships can be removed, in game scenario {@code _SC_PIRI}.
     * Other {@code pieceType}s are ignored.
     * @since 2.0.00
     */
    protected void handleREMOVEPIECE(SOCRemovePiece mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games

        SOCPlayer player = ga.getPlayer(mes.getParam1());
        final int pieceType = mes.getParam2();
        final int pieceCoordinate = mes.getParam3();

        switch (pieceType)
        {
        case SOCPlayingPiece.SHIP:
            ga.removeShip(new SOCShip(player, pieceCoordinate, null));
            break;

        default:
            System.err.println("Displayless.updateAtPieceRemoved called for un-handled type " + pieceType);
        }
    }

    /**
     * Undo moving or placing a piece.
     * Does nothing for server decline reply ({@link SOCUndoPutPiece#getPlayerNumber()} &lt; 0).
     * @param mes  the message
     * @param ga  Game the client is playing, from {@link SOCMessageForGame#getGame() mes.getGame()},
     *     for method reuse by SOCPlayerClient; does nothing if {@code null}
     * @since 2.7.00
     */
    public static void handleUNDOPUTPIECE(final SOCUndoPutPiece mes, SOCGame ga)
    {
        if (ga == null)
            return;  // Not one of our games
        if (mes.getPlayerNumber() < 0)
            return;

        final SOCBoard board = ga.getBoard();
        int pieceCurrCoord = mes.getCoordinates(), movedFromCoord = mes.getMovedFromCoordinates(),
            pieceType = mes.getPieceType();
        if (movedFromCoord != 0)
        {
            if (pieceType == SOCPlayingPiece.SHIP)
            {
                final SOCRoutePiece ship = board.roadOrShipAtEdge(pieceCurrCoord);
                if (ship instanceof SOCShip)  // also checks non-null
                    ga.undoMoveShip((SOCShip) ship);
            } else {
                System.err.println("Displayless.handleUNDOPUTPIECE: Un-handled move pieceType " + pieceType);
            }
        } else {
            SOCPlayingPiece pp = null;
            if ((pieceType == SOCPlayingPiece.ROAD) || (pieceType == SOCPlayingPiece.SHIP))
                pp = board.roadOrShipAtEdge(pieceCurrCoord);
            else if ((pieceType == SOCPlayingPiece.SETTLEMENT) || (pieceType == SOCPlayingPiece.CITY))
                pp = board.settlementAtNode(pieceCurrCoord);
            else
                System.err.println("Displayless.handleUNDOPUTPIECE: Un-handled move pieceType " + pieceType);

            if (pp != null)
                ga.undoPutPiece(pp);
        }
    }

    /**
     * Reveal a hidden hex on the board.
     * @since 2.0.00
     */
    protected void handleREVEALFOGHEX(final SOCRevealFogHex mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games
        if (! ga.hasSeaBoard)
            return;  // should not happen

        ga.revealFogHiddenHex
            (mes.getParam1(), mes.getParam2(), mes.getParam3());
    }

    /**
     * Update a village piece's value on the board (cloth remaining) in _SC_CLVI,
     * or a pirate fortress's strength in _SC_PIRI.
     * @since 2.0.00
     */
    protected void handlePIECEVALUE(final SOCPieceValue mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games
        if (! ga.hasSeaBoard)
            return;  // should not happen

        final int coord = mes.getParam2();
        final int pv = mes.getParam3();

        if (ga.isGameOptionSet(SOCGameOptionSet.K_SC_CLVI))
        {
            SOCVillage vi = ((SOCBoardLarge) (ga.getBoard())).getVillageAtNode(coord);
            if (vi != null)
                vi.setCloth(pv);
        }
        else if (ga.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
        {
            SOCFortress fort = ga.getFortress(coord);
            if (fort != null)
                fort.setStrength(pv);
        }
    }

    /**
     * Handle the "reopen or close a shipping trade route" message.
     * @param mes  the message
     * @param ga  Game the client is playing, from {@link SOCMessageForGame#getGame() mes.getGame()},
     *     for method reuse by SOCPlayerClient; does nothing if {@code null}
     *     or if game doesn't use {@link SOCBoardLarge}
     * @since 2.7.00
     */
    public static void handleSETSHIPROUTECLOSED(final SOCSetShipRouteClosed mes, SOCGame ga)
    {
        if (ga == null)
            return;  // Not one of our games

        final SOCBoard board = ga.getBoard();
        if (board instanceof SOCBoardLarge)
            ((SOCBoardLarge) board).setShipsClosed(mes.isClosed(), mes.getParams(), 1);
    }

    /**
     * Handle the "set special item" message.
     * This method handles only {@link SOCSetSpecialItem#OP_SET OP_SET} and {@link SOCSetSpecialItem#OP_CLEAR OP_CLEAR}
     * (and the "set" or "clear" part of {@link SOCSetSpecialItem#OP_SET_PICK OP_SET_PICK} and
     * {@link SOCSetSpecialItem#OP_CLEAR_PICK OP_CLEAR_PICK}), and ignores other operations
     * such as {@link SOCSetSpecialItem#OP_PICK OP_PICK} and {@link SOCSetSpecialItem#OP_DECLINE OP_DECLINE}.
     * If your client needs to react to PICK or other operations, override this method
     * or check {@link SOCSetSpecialItem#op} and call something else for those ops.
     *
     * @param games  Games the client is playing, for method reuse by SOCPlayerClient
     * @param mes  the message
     * @since 2.0.00
     */
    public static final void handleSETSPECIALITEM(final Map<String, SOCGame> games, SOCSetSpecialItem mes)
    {
        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final String typeKey = mes.typeKey;
        final int gi = mes.gameItemIndex, pi = mes.playerItemIndex, pn = mes.playerNumber;

        switch (mes.op)
        {
        case SOCSetSpecialItem.OP_CLEAR:
            // fall through
        case SOCSetSpecialItem.OP_CLEAR_PICK:
            {
                if (gi != -1)
                    ga.setSpecialItem(typeKey, gi, null);

                if ((pn != -1) && (pi != -1))
                {
                    SOCPlayer pl = ga.getPlayer(pn);
                    if (pl != null)
                        pl.setSpecialItem(typeKey, pi, null);
                }
            }
            break;

        case SOCSetSpecialItem.OP_SET:
            // fall through
        case SOCSetSpecialItem.OP_SET_PICK:
            {
                if ((gi == -1) && ((pi == -1) || (pn == -1)))
                {
                    return;  // malformed message
                }

                SOCSpecialItem item = ga.getSpecialItem(typeKey, gi, pi, pn);
                final SOCPlayer pl = (pn != -1) ? ga.getPlayer(pn) : null;

                if (item != null)
                {
                    item.setPlayer(pl);
                    item.setCoordinates(mes.coord);
                    item.setLevel(mes.level);
                    item.setStringValue(mes.sv);
                } else {
                    item = new SOCSpecialItem(pl, mes.coord, mes.level, mes.sv, null, null);
                }

                if (gi != -1)
                {
                    item.setGameIndex(gi);
                    ga.setSpecialItem(typeKey, gi, item);
                }

                if ((pi != -1) && (pl != null))
                    pl.setSpecialItem(typeKey, pi, item);
            }
            break;
        }
    }

    /**
     * send a text message to a chat channel
     *
     * @param ch   the name of the channel
     * @param mes  the message
     */
    public void chSend(String ch, String mes)
    {
        put(new SOCChannelTextMsg(ch, nickname, mes).toCmd());
    }

    /**
     * the user leaves the given chat channel
     *
     * @param ch  the name of the channel
     */
    public void leaveChannel(String ch)
    {
        channels.remove(ch);
        put(SOCLeaveChannel.toCmd(nickname, "-", ch));
    }

    /**
     * disconnect from the net, and from any local practice server
     * Called by {@link #destroy()}.
     */
    protected void disconnect()
    {
        connected = false;

        // reader will die once 'connected' is false, and socket is closed

        if (sLocal != null)
            sLocal.disconnect();

        try
        {
            sock.close();
        }
        catch (Exception e)
        {
            ex = e;
        }
    }

    /**
     * Ask to join a game; robots don't send this, server instead tells them to join games.
     * Useful for testing and maybe third-party clients.
     * @param gaName  Name of game to ask to join
     * @since 2.5.00
     */
    public void joinGame(final String gaName)
    {
        put(new SOCJoinGame("-", "", "-", gaName).toCmd());
    }

    /**
     * request to buy a development card
     *
     * @param ga     the game
     */
    public void buyDevCard(SOCGame ga)
    {
        put(new SOCBuyDevCardRequest(ga.getName()).toCmd());
    }

    /**
     * Request to build something or ask for the 6-player Special Building Phase (SBP).
     *
     * @param ga     the game
     * @param piece  the type of piece, from {@link soc.game.SOCPlayingPiece} constants,
     *               or -1 to request the Special Building Phase.
     * @throws IllegalArgumentException if {@code piece} &lt; -1
     */
    public void buildRequest(SOCGame ga, int piece)
        throws IllegalArgumentException
    {
        put(new SOCBuildRequest(ga.getName(), piece).toCmd());
    }

    /**
     * request to cancel building something or playing a dev card
     *
     * @param ga     the game
     * @param piece  the type of piece from SOCPlayingPiece, or {@link SOCCancelBuildRequest#CARD}
     */
    public void cancelBuildRequest(SOCGame ga, int piece)
    {
        put(new SOCCancelBuildRequest(ga.getName(), piece).toCmd());
    }

    /**
     * Send request to put a piece on the board.
     *
     * @param ga  the game where the action is taking place
     * @param pp  the piece being placed; {@link SOCPlayingPiece#getCoordinates() pp.getCoordinates()}
     *     and {@link SOCPlayingPiece#getType() pp.getType()} must be >= 0
     * @throws IllegalArgumentException if {@code pp.getCoordinates()} &lt; 0
     *     or {@code pp.getType()} &lt; 0
     */
    public void putPiece(SOCGame ga, SOCPlayingPiece pp)
        throws IllegalArgumentException
    {
        final int pt = pp.getType();
        if (pt < 0)
            throw new IllegalArgumentException("pt: " + pt);

        /**
         * send the command
         */
        put(SOCPutPiece.toCmd(ga.getName(), pp.getPlayerNumber(), pt, pp.getCoordinates()));
    }

    /**
     * Ask the server to move this piece to a different coordinate.
     * @param ga  the game where the action is taking place
     * @param pn  The piece's player number
     * @param ptype    The piece type, such as {@link SOCPlayingPiece#SHIP}; must be >= 0
     * @param fromCoord  Move the piece from here; must be >= 0
     * @param toCoord    Move the piece to here; must be >= 0
     * @throws IllegalArgumentException if {@code ptype} &lt; 0, {@code fromCoord} &lt; 0, or {@code toCoord} &lt; 0
     * @since 2.5.00
     */
    public void movePieceRequest
        (final SOCGame ga, final int pn, final int ptype, final int fromCoord, final int toCoord)
        throws IllegalArgumentException
    {
        put(new SOCMovePiece(ga.getName(), pn, ptype, fromCoord, toCoord).toCmd());
    }

    /**
     * Ask the server to undo placing or moving a piece.
     * @param ga  game where the action is taking place; will call {@link SOCGame#getCurrentPlayerNumber()}
     * @param ptype  piece type, such as {@link SOCPlayingPiece#SHIP}; must be &gt;= 0
     * @param coord  coordinate where piece was placed or moved to; must be &gt; 0
     * @param movedFromCoord  if undoing a move, the coordinate where piece was moved from, otherwise 0
     * @throws IllegalArgumentException if {@code ptype} &lt; 0, {@code coord} &lt;= 0, or {@code movedFromCoord} &lt; 0
     * @since 2.7.00
     */
    public void undoPutOrMovePieceRequest
        (final SOCGame ga, final int ptype, final int coord, final int movedFromCoord)
        throws IllegalArgumentException
    {
        put(new SOCUndoPutPiece(ga.getName(), ga.getCurrentPlayerNumber(), ptype, coord, movedFromCoord).toCmd());
    }

    /**
     * the player wants to move the robber or the pirate ship.
     *
     * @param ga  the game
     * @param pl  the player
     * @param coord  hex where the player wants the robber, or negative hex for the pirate ship
     */
    public void moveRobber(SOCGame ga, SOCPlayer pl, int coord)
    {
        put(SOCMoveRobber.toCmd(ga.getName(), pl.getPlayerNumber(), coord));
    }

    /**
     * Send a {@link SOCSimpleRequest} to the server.
     * {@code reqType} gives the request type, and the optional
     * {@code value1} and {@code value2} depend on request type.
     *
     * @param ga  the game
     * @param ourPN  our player's player number
     * @param reqType  Request type, such as {@link SOCSimpleRequest#SC_PIRI_FORT_ATTACK}.
     *        See {@link SOCSimpleRequest} public int fields for possible types and their meanings.
     * @param value1  First optional detail value, or 0
     * @param value2  Second optional detail value, or 0
     * @since 2.0.00
     */
    public void simpleRequest
        (final SOCGame ga, final int ourPN, final int reqType, final int value1, final int value2)
    {
        put(SOCSimpleRequest.toCmd(ga.getName(), ourPN, reqType, value1, value2));
    }

    /**
     * Send a request to pick a {@link SOCSpecialItem Special Item}, using a
     * {@link SOCSetSpecialItem}{@code (PICK, typeKey, gi, pi, owner=-1, coord=-1, level=0)} message.
     * @param ga  Game
     * @param typeKey  Special item type.  Typically a {@link SOCGameOption} keyname; see the {@link SOCSpecialItem}
     *     class javadoc for details.
     * @param gi  Game Item Index, as in {@link SOCGame#getSpecialItem(String, int)} or
     *     {@link SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)}, or -1
     * @param pi  Player Item Index, as in {@link SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)},
     *     or -1
     * @since 2.0.00
     */
    public void pickSpecialItem(SOCGame ga, final String typeKey, final int gi, final int pi)
    {
        put(new SOCSetSpecialItem(ga.getName(), SOCSetSpecialItem.OP_PICK, typeKey, gi, pi, -1).toCmd());
    }

    /**
     * Send a request to play a {@link soc.game.SOCInventoryItem SOCInventoryItem}
     * (not a standard {@link soc.game.SOCDevCard SOCDevCard}) using a
     * {@link SOCInventoryItemAction}{@code (ourPN, PLAY, itype, rc=0)} message.
     * @param ga     the game
     * @param ourPN  our player's player number
     * @param itype  the special inventory item type picked by player,
     *     from {@link soc.game.SOCInventoryItem#itype SOCInventoryItem.itype}
     * @since 2.0.00
     */
    public void playInventoryItem(SOCGame ga, final int ourPN, final int itype)
    {
        put(SOCInventoryItemAction.toCmd
            (ga.getName(), ourPN, SOCInventoryItemAction.PLAY, itype, 0));
    }

    /**
     * send a text message to the people in the game
     *
     * @param ga   the game; does nothing if {@code null}
     * @param me   the message
     */
    public void sendText(SOCGame ga, String me)
    {
        if (ga == null)
            return;

        put(new SOCGameTextMsg(ga.getName(), nickname, me).toCmd());
    }

    /**
     * user wants to leave a game.
     * Calls {@link #leaveGame(String)}.
     *
     * @param ga   the game
     */
    public void leaveGame(SOCGame ga)
    {
        leaveGame(ga.getName());
    }

    /**
     * user wants to leave a game, which they may not have been able to fully join.
     * Also removes game from client's game list; {@link #getGame(String)} would return {@code null} for it afterwards.
     *
     * @param gaName  the game name
     * @see #leaveGame(SOCGame)
     * @since 2.5.00
     */
    public void leaveGame(final String gaName)
    {
        games.remove(gaName);
        put(new SOCLeaveGame(nickname, "-", gaName).toCmd());
    }

    /**
     * User is sending server a request to sit down to play.
     *
     * @param ga   the game
     * @param pn   the number of the seat where the user wants to sit
     */
    public void sitDown(SOCGame ga, int pn)
    {
        put(SOCSitDown.toCmd(ga.getName(), SOCMessage.EMPTYSTR, pn, false));
    }

    /**
     * the user is starting the game
     *
     * @param ga  the game
     */
    public void startGame(SOCGame ga)
    {
        put(SOCStartGame.toCmd(ga.getName(), 0));
    }

    /**
     * the user rolls the dice
     *
     * @param ga  the game
     */
    public void rollDice(SOCGame ga)
    {
        put(SOCRollDice.toCmd(ga.getName()));
    }

    /**
     * the user is done with the turn
     *
     * @param ga  the game
     */
    public void endTurn(SOCGame ga)
    {
        put(SOCEndTurn.toCmd(ga.getName()));
    }

    /**
     * the user wants to discard
     *
     * @param ga  the game
     * @param rs  Resources to discard
     */
    public void discard(SOCGame ga, SOCResourceSet rs)
    {
        put(new SOCDiscard(ga.getName(), -1, rs).toCmd());
    }

    /**
     * The user chose a player to steal from,
     * or (game state {@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE})
     * chose whether to move the robber or the pirate,
     * or (game state {@link SOCGame#WAITING_FOR_ROB_CLOTH_OR_RESOURCE})
     * chose whether to steal a resource or cloth.
     *<P>
     * If choice is from playing a {@link SOCDevCardConstants#KNIGHT} card, to cancel the card
     * call {@link #cancelBuildRequest(SOCGame, int) cancelBuildRequest}({@link SOCCancelBuildRequest#CARD}) instead.
     *
     * @param ga  the game
     * @param ch  the player number,
     *   or {@link SOCChoosePlayer#CHOICE_MOVE_ROBBER} to move the robber
     *   or {@link SOCChoosePlayer#CHOICE_MOVE_PIRATE} to move the pirate ship.
     *   See {@link SOCChoosePlayer#SOCChoosePlayer(String, int)} for meaning
     *   of <tt>ch</tt> for game state <tt>WAITING_FOR_ROB_CLOTH_OR_RESOURCE</tt>.
     */
    public void choosePlayer(SOCGame ga, final int ch)
    {
        put(SOCChoosePlayer.toCmd(ga.getName(), ch));
    }

    /**
     * the user is rejecting the current offers
     *
     * @param ga  the game
     */
    public void rejectOffer(SOCGame ga)
    {
        put(new SOCRejectOffer(ga.getName(), 0).toCmd());
    }

    /**
     * the user is accepting an offer
     *
     * @param ga  the game
     * @param from the number of the player that is making the offer
     */
    public void acceptOffer(SOCGame ga, int from)
    {
        put(new SOCAcceptOffer(ga.getName(), 0, from).toCmd());
    }

    /**
     * the user is clearing an offer
     *
     * @param ga  the game
     */
    public void clearOffer(SOCGame ga)
    {
        put(SOCClearOffer.toCmd(ga.getName(), 0));
    }

    /**
     * the user wants to trade with the bank or a port.
     *
     * @param ga    the game
     * @param give  what is being offered
     * @param get   what the player wants
     */
    public void bankTrade(SOCGame ga, SOCResourceSet give, SOCResourceSet get)
    {
        put(new SOCBankTrade(ga.getName(), give, get, -1).toCmd());
    }

    /**
     * the user is making an offer to trade with other players.
     *
     * @param ga    the game
     * @param offer the trade offer
     */
    public void offerTrade(SOCGame ga, SOCTradeOffer offer)
    {
        put(SOCMakeOffer.toCmd(ga.getName(), offer));
    }

    /**
     * the user wants to play a development card
     *
     * @param ga  the game
     * @param dc  the type of development card
     */
    public void playDevCard(SOCGame ga, int dc)
    {
        if ((! ga.isPractice) && (sVersion < SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES))
        {
            // Unlikely; the displayless client is currently used for SOCRobotClient,
            // and the built-in robots must be the same version as the server.
            // This code is here for a third-party bot or other user of displayless.

            if (dc == SOCDevCardConstants.KNIGHT)
                dc = SOCDevCardConstants.KNIGHT_FOR_VERS_1_X;
            else if (dc == SOCDevCardConstants.UNKNOWN)
                dc = SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X;
        }
        put(SOCPlayDevCardRequest.toCmd(ga.getName(), dc));
    }

    /**
     * the user picked 2 resources to discover (Discovery/Year of Plenty),
     * or picked these resources to gain from a gold hex.
     *<P>
     * Before v2.0.00, this method was {@code discoveryPick(..)}.
     *
     * @param ga    the game
     * @param rscs  the resources
     */
    public void pickResources(SOCGame ga, SOCResourceSet rscs)
    {
        put(new SOCPickResources(ga.getName(), rscs).toCmd());
    }

    /**
     * the client player picked a resource type to monopolize.
     *<P>
     * Before v2.0.00 this method was {@code monopolyPick}.
     *
     * @param ga   the game
     * @param res  the resource type, such as
     *     {@link SOCResourceConstants#CLAY} or {@link SOCResourceConstants#SHEEP}
     */
    public void pickResourceType(SOCGame ga, int res)
    {
        put(new SOCPickResourceType(ga.getName(), res).toCmd());
    }

    /**
     * the user is changing the face image
     *
     * @param ga  the game
     * @param id  the image id
     */
    public void changeFace(SOCGame ga, int id)
    {
        put(new SOCChangeFace(ga.getName(), ga.getPlayer(nickname).getPlayerNumber(), id).toCmd());
    }

    /**
     * The user is locking or unlocking a seat.
     *
     * @param ga  the game
     * @param pn  the seat number
     * @param sl  new seat lock state; remember that servers older than v2.0.00 won't recognize {@code CLEAR_ON_RESET}
     * @since 2.0.00
     */
    public void setSeatLock(SOCGame ga, int pn, SOCGame.SeatLockState sl)
    {
        put(SOCSetSeatLock.toCmd(ga.getName(), pn, sl));
    }

    /**
     * Connection to server has raised an error that wasn't {@link InterruptedIOException};
     * {@link #ex} contains exception detail. Leave all games, then disconnect.
     *<P>
     * {@link soc.robot.SOCRobotClient} overrides this to try and reconnect.
     */
    public void destroy()
    {
        SOCLeaveAll leaveAllMes = new SOCLeaveAll();
        put(leaveAllMes.toCmd());
        disconnect();
    }

    /**
     * for stand-alones
     */
    public static void main(String[] args)
    {
        SOCDisplaylessPlayerClient ex1 = new SOCDisplaylessPlayerClient
            (new ServerConnectInfo(args[0], Integer.parseInt(args[1]), null), true);
        new Thread(ex1).start();
        Thread.yield();
    }

}
