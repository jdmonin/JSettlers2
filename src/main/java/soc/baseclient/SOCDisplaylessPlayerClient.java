/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
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
package soc.baseclient;

import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCInventory;
import soc.game.SOCInventoryItem;
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

import soc.robot.SOCRobotClient;
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
 * GUI-less standalone client for connecting to the SOCServer.
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
 * Since robot client and server are the same version, this client ignores game option sync
 * and scenario synchronization messages ({@link SOCGameOptionInfo}, {@link SOCScenarioInfo}).
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

    protected String host;
    protected int port;
    protected String strSocketName;  // For robots in local practice games
    protected Socket s;
    protected DataInputStream in;
    protected DataOutputStream out;
    protected StringConnection sLocal;  // if strSocketName not null

    /**
     * Server version number, sent soon after connect, or -1 if unknown.
     * {@link #sLocalVersion} should always equal our own version.
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
     */
    protected Hashtable<String, SOCGame> games = new Hashtable<String, SOCGame>();

    /**
     * True if contents of incoming and outgoing network message traffic should be debug-printed.
     * Set if optional system property {@link #PROP_JSETTLERS_DEBUG_TRAFFIC} is set.
     * @since 1.2.00
     */
    protected boolean debugTraffic;

    /**
     * Create a SOCDisplaylessPlayerClient, which would connect to localhost port 8889.
     * Does not actually connect; subclass must connect, such as {@link soc.robot.SOCRobotClient#init()}
     *<P>
     * <b>Note:</b> The default JSettlers server port is 8880.
     */
    public SOCDisplaylessPlayerClient()
    {
        this(null, 8889, false);
    }

    /**
     * Constructor for connecting to the specified host, on the specified port.
     * Does not actually connect; subclass must connect, such as {@link soc.robot.SOCRobotClient#init()}
     *
     * @param h  host
     * @param p  port
     * @param visual  true if this client is visual
     */
    public SOCDisplaylessPlayerClient(String h, int p, boolean visual)
    {
        host = h;
        port = p;
        strSocketName = null;
        sVersion = -1;  sLocalVersion = -1;

        if (null != System.getProperty(PROP_JSETTLERS_DEBUG_TRAFFIC))
            debugTraffic = true;  // set flag if debug prop has any value at all
    }

    /**
     * Constructor for connecting to a local game (practice) on a local stringport.
     * Does not actually connect; subclass must connect, such as {@link soc.robot.SOCRobotClient#init()}
     *
     * @param s    the stringport that the server listens on
     * @param visual  true if this client is visual
     */
    public SOCDisplaylessPlayerClient(String s, boolean visual)
    {
        this(null, 0, visual);

        strSocketName = s;
    }

    /**
     * @return the nickname of this user
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * continuously read from the net in a separate thread
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
                treat(SOCMessage.toMsg(s));
            }
        }
        catch (InterruptedIOException x)
        {
            System.err.println("Socket timeout in run: " + x);
        }
        catch (IOException e)
        {
            if (! connected)
            {
                return;
            }

            ex = e;
            if (! ((e instanceof java.io.EOFException)
                   && (this instanceof SOCRobotClient)))
            {
                System.err.println("could not read from the net: " + ex);
                /**
                 * Robots are periodically disconnected from server;
                 * they will try to reconnect.  Any error message
                 * from that is printed in {@link soc.robot.SOCRobotClient#destroy()}.
                 * So, print nothing here if that's the situation.
                 */
            }
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
            soc.debug.D.ebugPrintln("OUT - " + nickname + " - " + s);

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
            soc.debug.D.ebugPrintln("IN - " + nickname + " - " + mes.toString());
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
                handleBOARDLAYOUT((SOCBoardLayout) mes);
                break;

            /**
             * receive a board layout (new format, as of 20091104 (v 1.1.08))
             */
            case SOCMessage.BOARDLAYOUT2:
                handleBOARDLAYOUT2(games, (SOCBoardLayout2) mes);
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
             * the trade message needs to be cleared
             */
            case SOCMessage.CLEARTRADEMSG:
                handleCLEARTRADEMSG((SOCClearTradeMsg) mes);
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
                handlePOTENTIALSETTLEMENTS((SOCPotentialSettlements) mes, games);
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
             * generic "simple action" announcements from the server.
             * Added 2013-09-04 for v1.1.19.
             */
            case SOCMessage.SIMPLEACTION:
                handleSIMPLEACTION(games, (SOCSimpleAction) mes);
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
     * handle the "status message" message
     * @param mes  the message
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes) {}

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
     */
    private void handleVERSION(boolean isLocal, SOCVersion mes)
    {
        D.ebugPrintln("handleVERSION: " + mes);
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
     * handle the "join game authorization" message
     * @param mes  the message
     * @param isPractice Is the server local for practice, or remote?
     */
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes, final boolean isPractice)
    {
        gotPassword = true;

        SOCGame ga = new SOCGame(mes.getGame());

        if (ga != null)
        {
            ga.isPractice = isPractice;
            games.put(mes.getGame(), ga);
        }
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

        if (ga != null)
        {
            SOCPlayer player = ga.getPlayer(mes.getNickname());

            if (player != null)
            {
                //
                //  This user was not a spectator
                //
                ga.removePlayer(mes.getNickname());
            }
        }
    }

    /**
     * handle the "new game" message
     * @param mes  the message
     */
    protected void handleNEWGAME(SOCNewGame mes) {}

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
     * handle the "game stats" message
     */
    protected void handleGAMESTATS(SOCGameStats mes) {}

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
     * Game players gain resources.
     * @param mes  Message data
     * @param ga  Game to update
     * @param nickname  Our client player's nickname, needed for element data update
     * @param skipResourceCount  If true, ignore the resource part of the message
     *     because caller will handle that separately.
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

            pl.getResources().add(rs);

            if (! skipResourceCount)
                handlePLAYERELEMENT_simple
                    (ga, pl, pn, SOCPlayerElement.SET,
                     SOCPlayerElement.RESOURCE_COUNT, mes.playerResTotal.get(p), nickname);
        }
    }

    /**
     * handle the "player sitting down" message
     * @param mes  the message
     */
    protected void handleSITDOWN(SOCSitDown mes)
    {
        /**
         * tell the game that a player is sitting
         */
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            ga.takeMonitor();

            try
            {
                ga.addPlayer(mes.getNickname(), mes.getPlayerNumber());

                /**
                 * set the robot flag
                 */
                ga.getPlayer(mes.getPlayerNumber()).setRobotFlag(mes.isRobot(), false);
            }
            catch (Exception e)
            {
                ga.releaseMonitor();
                System.out.println("Exception caught - " + e);
                e.printStackTrace();
            }

            ga.releaseMonitor();
        }
    }

    /**
     * handle the "board layout" message
     * @param mes  the message
     */
    protected void handleBOARDLAYOUT(SOCBoardLayout mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            // BOARDLAYOUT is always the v1 board encoding (oldest format)
            SOCBoard bd = ga.getBoard();
            bd.setHexLayout(mes.getHexLayout());
            bd.setNumberLayout(mes.getNumberLayout());
            bd.setRobberHex(mes.getRobberHex(), false);
            ga.updateAtBoardLayout();
        }
    }

    /**
     * handle the "board layout" message, new format
     * @param games  Games the client is playing, for method reuse by SOCPlayerClient
     * @param mes  the message
     * @since 1.1.08
     * @return True if game was found and layout understood, false otherwise
     */
    public static boolean handleBOARDLAYOUT2(Map<String, SOCGame> games, SOCBoardLayout2 mes)
    {
        SOCGame ga = games.get(mes.getGame());
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
    public final static void handleSTARTGAME_checkIsBotsOnly(SOCGame ga)
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
     * handle the "set turn" message
     * @param mes  the message
     */
    protected void handleSETTURN(SOCSetTurn mes)
    {
        handleGAMEELEMENT(games.get(mes.getGame()), SOCGameElements.CURRENT_PLAYER, mes.getPlayerNumber());
    }

    /**
     * handle the "first player" message
     * @param mes  the message
     */
    protected void handleFIRSTPLAYER(SOCFirstPlayer mes)
    {
        handleGAMEELEMENT(games.get(mes.getGame()), SOCGameElements.FIRST_PLAYER, mes.getPlayerNumber());
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
            handlePLAYERELEMENT(ga, pl, pn, action, etypes[i], amounts[i], nickname);
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
        final int etype = mes.getElementType();

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
     * @param etype  Element type, such as {@link SOCPlayerElement#SETTLEMENTS} or {@link SOCPlayerElement#NUMKNIGHTS}
     * @param amount  The new value to set, or the delta to gain/lose
     * @param nickname  Our client player nickname/username, for a few elements where that matters.
     *     Some callers use {@code null} for elements where this isn't needed.
     * @since 2.0.00
     */
    public static final void handlePLAYERELEMENT
        (final SOCGame ga, SOCPlayer pl, final int pn, final int action,
         final int etype, final int amount, final String nickname)
    {
        if (ga == null)
            return;
        if ((pl == null) && (pn != -1))
            pl = ga.getPlayer(pn);

        switch (etype)
        {
        case SOCPlayerElement.ROADS:
            handlePLAYERELEMENT_numPieces(pl, action, SOCPlayingPiece.ROAD, amount);
            break;

        case SOCPlayerElement.SETTLEMENTS:
            handlePLAYERELEMENT_numPieces(pl, action, SOCPlayingPiece.SETTLEMENT, amount);
            break;

        case SOCPlayerElement.CITIES:
            handlePLAYERELEMENT_numPieces(pl, action, SOCPlayingPiece.CITY, amount);
            break;

        case SOCPlayerElement.SHIPS:
            handlePLAYERELEMENT_numPieces(pl, action, SOCPlayingPiece.SHIP, amount);
            break;

        case SOCPlayerElement.NUMKNIGHTS:
            // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
            handlePLAYERELEMENT_numKnights(ga, pl, action, amount);
            break;

        case SOCPlayerElement.CLAY:
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.CLAY, amount);
            break;

        case SOCPlayerElement.ORE:
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.ORE, amount);
            break;

        case SOCPlayerElement.SHEEP:
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.SHEEP, amount);
            break;

        case SOCPlayerElement.WHEAT:
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.WHEAT, amount);
            break;

        case SOCPlayerElement.WOOD:
            handlePLAYERELEMENT_numRsrc(pl, action, SOCResourceConstants.WOOD, amount);
            break;

        case SOCPlayerElement.UNKNOWN:
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
     * @param etype  Element type, such as {@link SOCPlayerElement#SETTLEMENTS} or {@link SOCPlayerElement#NUMKNIGHTS}
     * @param val  The new value to set, or the delta to gain/lose
     * @param nickname  Our client player nickname/username, for the only element where that matters:
     *     {@link SOCPlayerElement#RESOURCE_COUNT}. Can be {@code null} otherwise.
     * @since 2.0.00
     */
    public static void handlePLAYERELEMENT_simple
        (SOCGame ga, SOCPlayer pl, final int pn, final int action,
         final int etype, final int val, final String nickname)
    {
        if ((pl == null) && (pn != -1))
            pl = ga.getPlayer(pn);

        switch (etype)
        {
        case SOCPlayerElement.ASK_SPECIAL_BUILD:
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

        case SOCPlayerElement.RESOURCE_COUNT:
            if (val != pl.getResources().getTotal())
            {
                SOCResourceSet rsrcs = pl.getResources();

                if (D.ebugOn)
                {
                    //pi.print(">>> RESOURCE COUNT ERROR: "+mes.getCount()+ " != "+rsrcs.getTotal());
                }

                //
                //  fix it if possible
                //
                if ((nickname != null) && ! pl.getName().equals(nickname))
                {
                    rsrcs.clear();
                    rsrcs.setAmount(val, SOCResourceConstants.UNKNOWN);
                }
            }
            break;

        case SOCPlayerElement.LAST_SETTLEMENT_NODE:
            pl.setLastSettlementCoord(val);
            break;

        case SOCPlayerElement.PLAYED_DEV_CARD_FLAG:
            {
                final boolean changeTo = (val != 0);
                if (pn != -1)
                    pl.setPlayedDevCard(changeTo);
                else
                    for (int p = 0; p < ga.maxPlayers; ++p)
                        ga.getPlayer(p).setPlayedDevCard(changeTo);
            }
            break;

        case SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES:
            pl.setNeedToPickGoldHexResources(val);
            break;

        case SOCPlayerElement.SCENARIO_SVP:
            pl.setSpecialVP(val);
            break;

        case SOCPlayerElement.SCENARIO_PLAYEREVENTS_BITMASK:
            pl.setScenarioPlayerEvents(val);
            break;

        case SOCPlayerElement.SCENARIO_SVP_LANDAREAS_BITMASK:
            pl.setScenarioSVPLandAreas(val);
            break;

        case SOCPlayerElement.STARTING_LANDAREAS:
            pl.setStartingLandAreasEncoded(val);
            break;

        case SOCPlayerElement.SCENARIO_CLOTH_COUNT:
            if (pn != -1)
                pl.setCloth(val);
            else
                ((SOCBoardLarge) (ga.getBoard())).setCloth(val);
            break;

        case SOCPlayerElement.SCENARIO_WARSHIP_COUNT:
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
     * @since 2.0.00
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
     *<LI> If this is a {@link SOCPlayerElement#LOSE} action, and the player does not have enough of that type,
     *     the rest are taken from the player's UNKNOWN amount.
     *<LI> If we are losing from type UNKNOWN,
     *     first convert player's known resources to unknown resources
     *     (individual amount information will be lost),
     *     then remove mes's unknown resources from player.
     *</ul>
     *<P>
     * To avoid code duplication, also called from
     * {@link soc.client.MessageHandler#handlePLAYERELEMENT(SOCPlayerElement)}
     * and {@link soc.robot.SOCRobotBrain#run()}.
     *<P>
     * Before v2.0.00 this method directly took a {@link SOCPlayerElement} instead of that message's
     * {@code action} and {@code amount} fields.
     *
     * @param pl     Player to update
     * @param action   {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN GAIN},
     *     or {@link SOCPlayerElement#LOSE LOSE}
     * @param rtype  Type of resource, like {@link SOCResourceConstants#CLAY}
     * @param amount    The new value to set, or the delta to gain/lose
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
            if (rtype != SOCResourceConstants.UNKNOWN)
            {
                int playerAmt = pl.getResources().getAmount(rtype);
                if (playerAmt >= amount)
                {
                    pl.getResources().subtract(amount, rtype);
                }
                else
                {
                    pl.getResources().subtract(amount - playerAmt, SOCResourceConstants.UNKNOWN);
                    pl.getResources().setAmount(0, rtype);
                }
            }
            else
            {
                SOCResourceSet rs = pl.getResources();

                /**
                 * first convert player's known resources to unknown resources,
                 * then remove mes's unknown resources from player
                 */
                rs.convertToUnknown();
                pl.getResources().subtract(amount, SOCResourceConstants.UNKNOWN);
            }

            break;
        }
    }

    /**
     * Handle the GameElements message: Finds game by name, and loops calling
     * {@link #handleGAMEELEMENT(SOCGame, int, int)}.
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
            handleGAMEELEMENT(ga, etypes[i], values[i]);
    }

    /**
     * Update one game element field from a {@link SOCGameElements} message.
     * @param ga   Game to update; does nothing if null
     * @param etype  Element type, such as {@link SOCGameElements#ROUND_COUNT} or {@link SOCGameElements#DEV_CARD_COUNT}
     * @param value  The new value to set
     * @since 2.0.00
     */
    public static final void handleGAMEELEMENT
        (final SOCGame ga, final int etype, final int value)
    {
        if (ga == null)
            return;

        switch (etype)
        {
        case SOCGameElements.ROUND_COUNT:
            ga.setRoundCount(value);
            break;

        case SOCGameElements.DEV_CARD_COUNT:
            ga.setNumDevCards(value);
            break;

        case SOCGameElements.FIRST_PLAYER:
            ga.setFirstPlayer(value);
            break;

        case SOCGameElements.CURRENT_PLAYER:
            ga.setCurrentPlayerNumber(value);
            break;

        case SOCGameElements.LARGEST_ARMY_PLAYER:
            ga.setPlayerWithLargestArmy((value != -1) ? ga.getPlayer(value) : null);
            break;

        case SOCGameElements.LONGEST_ROAD_PLAYER:
            ga.setPlayerWithLongestRoad((value != -1) ? ga.getPlayer(value) : null);
            break;
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
             SOCPlayerElement.RESOURCE_COUNT, mes.getCount(), nickname);
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
                SOCPlayerElement.LAST_SETTLEMENT_NODE, mes.getCoordinates(), null);
    }

    /**
     * handle the "dice result" message
     * @param mes  the message
     */
    protected void handleDICERESULT(SOCDiceResult mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            ga.setCurrentDice(mes.getResult());
        }
    }

    /**
     * handle the "put piece" message
     *<P>
     * This method is public static for access by
     * {@code SOCRobotBrain.handlePUTPIECE_updateGameData(SOCPutPiece)}.
     * @param mes  the message
     * @param ga  Message's game from {@link SOCPutPiece#getGame()}; if {@code null}, message is ignored
     */
    public static void handlePUTPIECE(final SOCPutPiece mes, SOCGame ga)
    {
        if (ga != null)
        {
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
     * handle the "robber moved" or "pirate moved" message.
     * @param mes  the message
     */
    protected void handleMOVEROBBER(SOCMoveRobber mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            /**
             * Note: Don't call ga.moveRobber() because that will call the
             * functions to do the stealing.  We just want to say where
             * the robber moved without seeing if something was stolen.
             */
            final int newHex = mes.getCoordinates();
            if (newHex > 0)
                ga.getBoard().setRobberHex(newHex, true);
            else
                ((SOCBoardLarge) ga.getBoard()).setPirateHex(-newHex, true);
        }
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
     * handle the "make offer" message
     * @param mes  the message
     */
    protected void handleMAKEOFFER(SOCMakeOffer mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            SOCTradeOffer offer = mes.getOffer();
            ga.getPlayer(offer.getFrom()).setCurrentOffer(offer);
        }
    }

    /**
     * handle the "clear offer" message
     * @param mes  the message
     */
    protected void handleCLEAROFFER(SOCClearOffer mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            final int pn = mes.getPlayerNumber();
            if (pn != -1)
            {
                ga.getPlayer(pn).setCurrentOffer(null);
            } else {
                for (int i = 0; i < ga.maxPlayers; ++i)
                    ga.getPlayer(i).setCurrentOffer(null);
            }
        }
    }

    /**
     * handle the "reject offer" message
     * @param mes  the message
     */
    protected void handleREJECTOFFER(SOCRejectOffer mes) {}

    /**
     * handle the "clear trade message" message
     * @param mes  the message
     */
    protected void handleCLEARTRADEMSG(SOCClearTradeMsg mes) {}

    /**
     * handle the "number of development cards" message
     * @param mes  the message
     */
    protected void handleDEVCARDCOUNT(SOCDevCardCount mes)
    {
        handleGAMEELEMENT(games.get(mes.getGame()), SOCGameElements.DEV_CARD_COUNT, mes.getNumDevCards());
    }

    /**
     * handle the "development card action" message
     * @param isPractice  Is the server local, or remote?  Client can be connected
     *                only to local, or remote.
     * @param mes  the message
     */
    protected void handleDEVCARDACTION(final boolean isPractice, final SOCDevCardAction mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());

            int ctype = mes.getCardType();
            if ((! isPractice) && (sVersion < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
            {
                if (ctype == SOCDevCardConstants.KNIGHT_FOR_VERS_1_X)
                    ctype = SOCDevCardConstants.KNIGHT;
                else if (ctype == SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X)
                    ctype = SOCDevCardConstants.UNKNOWN;
            }

            switch (mes.getAction())
            {
            case SOCDevCardAction.DRAW:
                player.getInventory().addDevCard(1, SOCInventory.NEW, ctype);
                break;

            case SOCDevCardAction.PLAY:
                player.getInventory().removeDevCard(SOCInventory.OLD, ctype);
                break;

            case SOCDevCardAction.ADD_OLD:
                player.getInventory().addDevCard(1, SOCInventory.OLD, ctype);
                break;

            case SOCDevCardAction.ADD_NEW:
                player.getInventory().addDevCard(1, SOCInventory.NEW, ctype);
                break;
            }
        }
    }

    /**
     * handle the "set played dev card flag" message
     * @param mes  the message
     */
    protected void handleSETPLAYEDDEVCARD(SOCSetPlayedDevCard mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
            handlePLAYERELEMENT_simple
                (ga, null, mes.getPlayerNumber(), SOCPlayerElement.SET,
                 SOCPlayerElement.PLAYED_DEV_CARD_FLAG, mes.hasPlayedDevCard() ? 1 : 0, null);
    }

    /**
     * Handle the "inventory item action" message by updating player inventory.
     * @param games  The hashtable of client's {@link SOCGame}s; key = game name
     * @param mes  the message
     * @return  True if this message is a "cannot play this type now" from server for our client player.
     * @since 2.0.00
     */
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
        }

        return false;
    }

    /**
     * handle the "list of potential settlements" message
     * @param mes  the message
     * @param games  The hashtable of client's {@link SOCGame}s; key = game name
     * @throws IllegalStateException if the board has
     *     {@link SOCBoardLarge#getAddedLayoutPart(String) SOCBoardLarge.getAddedLayoutPart("AL")} != {@code null} but
     *     badly formed (node list number 0, or a node list number not followed by a land area number).
     *     This Added Layout Part is rarely used, and this would be discovered quickly while testing
     *     the board layout that contained it.
     */
    public static void handlePOTENTIALSETTLEMENTS(SOCPotentialSettlements mes, Hashtable<String, SOCGame> games)
        throws IllegalStateException
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final List<Integer> vset = mes.getPotentialSettlements();
        final HashSet<Integer>[] las = mes.landAreasLegalNodes;
        final int[] loneSettles;  // must set for players after pl.setPotentialAndLegalSettlements, if not null
        final int[][] legalSeaEdges = mes.legalSeaEdges;  // usually null, except in _SC_PIRI

        int pn = mes.getPlayerNumber();
        if (ga.hasSeaBoard)
        {
            SOCBoardLarge bl = ((SOCBoardLarge) ga.getBoard());
            if ((pn == -1) || ((pn == 0) && bl.getLegalSettlements().isEmpty()))
                bl.setLegalSettlements
                  (vset, mes.startingLandArea, las);  // throws IllegalStateException if board layout
                                                      // has malformed Added Layout Part "AL"
            loneSettles = bl.getAddedLayoutPart("LS");  // usually null, except in _SC_PIRI
        } else {
            loneSettles = null;
        }

        if (pn != -1)
        {
            SOCPlayer player = ga.getPlayer(pn);
            player.setPotentialAndLegalSettlements(vset, true, las);
            if (loneSettles != null)
                player.addLegalSettlement(loneSettles[pn], false);
            if (legalSeaEdges != null)
                player.setRestrictedLegalShips(legalSeaEdges[0]);
        } else {
            for (pn = ga.maxPlayers - 1; pn >= 0; --pn)
            {
                SOCPlayer pl = ga.getPlayer(pn);
                pl.setPotentialAndLegalSettlements(vset, true, las);
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

        if (ga != null)
        {
            SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
            player.setFaceId(mes.getFaceId());
        }
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
        handleGAMEELEMENT(games.get(mes.getGame()), SOCGameElements.LONGEST_ROAD_PLAYER, mes.getPlayerNumber());
    }

    /**
     * handle the "largest army" message
     * @param mes  the message
     */
    protected void handleLARGESTARMY(SOCLargestArmy mes)
    {
        handleGAMEELEMENT(games.get(mes.getGame()), SOCGameElements.LARGEST_ARMY_PLAYER, mes.getPlayerNumber());
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
     * Update any game data from "simple request" announcements from the server.
     * Currently ignores them except for:
     *<UL>
     * <LI> {@link SOCSimpleRequest#TRADE_PORT_PLACE TRADE_PORT_PLACE}:
     *     Calls {@link SOCGame#placePort(SOCPlayer, int, int)} if {@code pn} &gt;= 0
     *</UL>
     *
     * @param games  Games the client is playing, for method reuse by SOCPlayerClient
     * @param mes  the message
     * @since 2.0.00
     */
    public static void handleSIMPLEREQUEST(final Map<String, SOCGame> games, final SOCSimpleRequest mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
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
                    ("DPC.handleSIMPLEREQUEST: Unknown type ignored: " + rtype + " in game " + gaName);
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
     * @param games  Games the client is playing, for method reuse by SOCPlayerClient
     * @param mes  the message
     * @since 1.1.19
     */
    public static void handleSIMPLEACTION(final Map<String, SOCGame> games, final SOCSimpleAction mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
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

        // Known types with no game data update:
        // Catch these before default case, so 'unknown type' won't be printed

        case SOCSimpleAction.DEVCARD_BOUGHT:
        case SOCSimpleAction.RSRC_TYPE_MONOPOLIZED:
        case SOCSimpleAction.SC_PIRI_FORT_ATTACK_RESULT:
            // game data updates are sent in preceding or following messages, can ignore this one
            break;

        default:
            // ignore unknown types
            // Since the bots and server are almost always the same version, this
            // shouldn't often occur: print for debugging.
            System.err.println
                ("handleSIMPLEACTION: Unknown type ignored: " + atype + " in game " + gaName);
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

        if (ga.isGameOptionSet(SOCGameOption.K_SC_CLVI))
        {
            SOCVillage vi = ((SOCBoardLarge) (ga.getBoard())).getVillageAtNode(coord);
            if (vi != null)
                vi.setCloth(pv);
        }
        else if (ga.isGameOptionSet(SOCGameOption.K_SC_PIRI))
        {
            SOCFortress fort = ga.getFortress(coord);
            if (fort != null)
                fort.setStrength(pv);
        }
    }

    /**
     * Handle the "set special item" message.
     * This method handles only {@link SOCSetSpecialItem#OP_SET OP_SET} and {@link SOCSetSpecialItem#OP_CLEAR OP_CLEAR}
     * and ignores other operations, such as {@link SOCSetSpecialItem#OP_PICK OP_PICK}.  If your client needs to react
     * to those other operations, override this method.
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
        put(SOCChannelTextMsg.toCmd(ch, nickname, mes));
    }

    /**
     * the user leaves the given chat channel
     *
     * @param ch  the name of the channel
     */
    public void leaveChannel(String ch)
    {
        channels.remove(ch);
        put(SOCLeaveChannel.toCmd(nickname, host, ch));
    }

    /**
     * disconnect from the net, and from any local practice server
     */
    protected void disconnect()
    {
        connected = false;

        // reader will die once 'connected' is false, and socket is closed

        if (sLocal != null)
            sLocal.disconnect();

        try
        {
            s.close();
        }
        catch (Exception e)
        {
            ex = e;
        }
    }

    /**
     * request to buy a development card
     *
     * @param ga     the game
     */
    public void buyDevCard(SOCGame ga)
    {
        put(SOCBuyDevCardRequest.toCmd(ga.getName()));
    }

    /**
     * request to build something
     *
     * @param ga     the game
     * @param piece  the type of piece, from {@link soc.game.SOCPlayingPiece} constants,
     *               or -1 to request the Special Building Phase.
     * @throws IllegalArgumentException if {@code piece} &lt; -1
     */
    public void buildRequest(SOCGame ga, int piece)
        throws IllegalArgumentException
    {
        put(SOCBuildRequest.toCmd(ga.getName(), piece));
    }

    /**
     * request to cancel building something
     *
     * @param ga     the game
     * @param piece  the type of piece from SOCPlayingPiece
     */
    public void cancelBuildRequest(SOCGame ga, int piece)
    {
        put(SOCCancelBuildRequest.toCmd(ga.getName(), piece));
    }

    /**
     * put a piece on the board
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
     * @param ga   the game
     * @param me   the message
     */
    public void sendText(SOCGame ga, String me)
    {
        if (ga == null)
            return;
        put(SOCGameTextMsg.toCmd(ga.getName(), nickname, me));
    }

    /**
     * the user leaves the given game
     *
     * @param ga   the game
     */
    public void leaveGame(SOCGame ga)
    {
        games.remove(ga.getName());
        put(SOCLeaveGame.toCmd(nickname, host, ga.getName()));
    }

    /**
     * the user sits down to play
     *
     * @param ga   the game
     * @param pn   the number of the seat where the user wants to sit
     */
    public void sitDown(SOCGame ga, int pn)
    {
        put(SOCSitDown.toCmd(ga.getName(), "dummy", pn, false));
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
        put(SOCDiscard.toCmd(ga.getName(), rs));
    }

    /**
     * The user chose a player to steal from,
     * or (game state {@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE})
     * chose whether to move the robber or the pirate,
     * or (game state {@link SOCGame#WAITING_FOR_ROB_CLOTH_OR_RESOURCE})
     * chose whether to steal a resource or cloth.
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
        put(SOCRejectOffer.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber()));
    }

    /**
     * the user is accepting an offer
     *
     * @param ga  the game
     * @param from the number of the player that is making the offer
     */
    public void acceptOffer(SOCGame ga, int from)
    {
        put(SOCAcceptOffer.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber(), from));
    }

    /**
     * the user is clearing an offer
     *
     * @param ga  the game
     */
    public void clearOffer(SOCGame ga)
    {
        put(SOCClearOffer.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber()));
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
        if ((! ga.isPractice) && (sVersion < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
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
     * the user picked 2 resources to discover (Year of Plenty),
     * or picked these resources to gain from the gold hex.
     *<P>
     * Before v2.0.00, this method was {@code discoveryPick(..)}.
     *
     * @param ga    the game
     * @param rscs  the resources
     */
    public void pickResources(SOCGame ga, SOCResourceSet rscs)
    {
        put(SOCPickResources.toCmd(ga.getName(), rscs));
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
        put(SOCPickResourceType.toCmd(ga.getName(), res));
    }

    /**
     * the user is changing the face image
     *
     * @param ga  the game
     * @param id  the image id
     */
    public void changeFace(SOCGame ga, int id)
    {
        put(SOCChangeFace.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber(), id));
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
     * Connection to server has raised an error; leave all games, then disconnect.
     * {@link SOCRobotClient} overrides this to try and reconnect.
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
        SOCDisplaylessPlayerClient ex1 = new SOCDisplaylessPlayerClient(args[0], Integer.parseInt(args[1]), true);
        new Thread(ex1).start();
        Thread.yield();
    }
}
