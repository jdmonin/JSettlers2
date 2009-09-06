/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2009 Jeremy D. Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.robot;

import soc.client.SOCDisplaylessPlayerClient;

import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;

import soc.message.SOCAcceptOffer;
import soc.message.SOCAdminPing;
import soc.message.SOCAdminReset;
import soc.message.SOCBoardLayout;
import soc.message.SOCCancelBuildRequest;
import soc.message.SOCChangeFace;
import soc.message.SOCChoosePlayerRequest;
import soc.message.SOCClearOffer;
import soc.message.SOCClearTradeMsg;
import soc.message.SOCDeleteGame;
import soc.message.SOCDevCard;
import soc.message.SOCDevCardCount;
import soc.message.SOCDiceResult;
import soc.message.SOCDiscardRequest;
import soc.message.SOCFirstPlayer;
import soc.message.SOCGameMembers;
import soc.message.SOCGameState;
import soc.message.SOCGameTextMsg;
import soc.message.SOCImARobot;
import soc.message.SOCJoinGame;
import soc.message.SOCJoinGameAuth;
import soc.message.SOCJoinGameRequest;
import soc.message.SOCLargestArmy;
import soc.message.SOCLeaveAll;
import soc.message.SOCLeaveGame;
import soc.message.SOCLongestRoad;
import soc.message.SOCMakeOffer;
import soc.message.SOCMessage;
import soc.message.SOCMoveRobber;
import soc.message.SOCPlayerElement;
import soc.message.SOCPotentialSettlements;
import soc.message.SOCPutPiece;
import soc.message.SOCRejectConnection;
import soc.message.SOCRejectOffer;
import soc.message.SOCResetBoardAuth;
import soc.message.SOCResourceCount;
import soc.message.SOCRobotDismiss;
import soc.message.SOCServerPing;
import soc.message.SOCSetPlayedDevCard;
import soc.message.SOCSetTurn;
import soc.message.SOCSitDown;
import soc.message.SOCStartGame;
import soc.message.SOCStatusMessage;
import soc.message.SOCTurn;
import soc.message.SOCUpdateRobotParams;
import soc.message.SOCVersion;

import soc.server.genericServer.LocalStringServerSocket;

import soc.util.CappedQueue;
import soc.util.CutoffExceededException;
import soc.util.SOCRobotParameters;
import soc.util.Version;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.net.Socket;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;


/**
 * This is a client that can play Settlers of Catan.
 *
 * @author Robert S Thomas
 */
public class SOCRobotClient extends SOCDisplaylessPlayerClient
{
    /**
     * constants for debug recording
     */
    public static final String CURRENT_PLANS = "CURRENT_PLANS";
    public static final String CURRENT_RESOURCES = "RESOURCES";

    /**
     * the thread the reads incoming messages
     */
    private Thread reader;

    /**
     * the current robot parameters for robot brains
     */
    private SOCRobotParameters currentRobotParameters;

    /**
     * the robot's "brains" for different games
     */
    private Hashtable robotBrains = new Hashtable();

    /**
     * the message queues for the different brains
     */
    private Hashtable brainQs = new Hashtable();

    /**
     * a table of requests from the server to sit at games
     */
    private Hashtable seatRequests = new Hashtable();

    /**
     * options for all games on the server we've been asked to join.
     * Some games may have no options, so will have no entry here,
     * although they will have an entry in {@link #games} once joined.
     * Key = game name, Value = hashtable of {@link SOCGameOption}.
     * Entries are added in {@link #handleJOINGAMEREQUEST(SOCJoinGameRequest)}.
     * Since the robot and server are the same version, the
     * set of "known options" will always be in sync.
     */
    private Hashtable gameOptions = new Hashtable();

    /**
     * number of games this bot has played
     */
    protected int gamesPlayed;

    /**
     * number of games finished
     */
    protected int gamesFinished;

    /**
     * number of games this bot has won
     */
    protected int gamesWon;

    /**
     * number of clean brain kills
     */
    protected int cleanBrainKills;

    /**
     * start time
     */
    protected long startTime;

    /**
     * used to maintain connection
     */
    SOCRobotResetThread resetThread;

    /**
     * Have we printed the initial welcome msg from server?
     * Suppress further ones (disconnect-reconnect).
     * @since 1.1.06
     */
    boolean printedInitialWelcome = false;

    /**
     * Constructor for connecting to the specified host, on the specified port
     *
     * @param h  host
     * @param p  port
     * @param nn nickname for robot
     * @param pw password for robot
     */
    public SOCRobotClient(String h, int p, String nn, String pw)
    {
        gamesPlayed = 0;
        gamesFinished = 0;
        gamesWon = 0;
        cleanBrainKills = 0;
        startTime = System.currentTimeMillis();
        host = h;
        port = p;
        nickname = nn;
        password = pw;
        strSocketName = null;
    }

    /**
     * Constructor for connecting to a local game (practice) on a local stringport.
     *
     * @param s    the stringport that the server listens on
     * @param nn   nickname for robot
     * @param pw   password for robot
     */
    public SOCRobotClient(String s, String nn, String pw)
    {
        this(null, 0, nn, pw);
        strSocketName = s;
    }

    /**
     * Initialize the robot player; connect to server, send first messages
     */
    public void init()
    {
        try
        {
            if (strSocketName == null)
            {
                s = new Socket(host, port);
                s.setSoTimeout(300000);
                in = new DataInputStream(s.getInputStream());
                out = new DataOutputStream(s.getOutputStream());
            }
            else
            {
                sLocal = LocalStringServerSocket.connectTo(strSocketName);
            }               
            connected = true;
            reader = new Thread(this);
            reader.start();

            //resetThread = new SOCRobotResetThread(this);
            //resetThread.start();
            put(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum()));
            put(SOCImARobot.toCmd(nickname));
        }
        catch (Exception e)
        {
            ex = e;
            System.err.println("Could not connect to the server: " + ex);
        }
    }

    /**
     * disconnect and then try to reconnect.
     * If the reconnect fails, {@link #ex} is set. Otherwise ex is null.
     */
    public void disconnectReconnect()
    {
        D.ebugPrintln("(*)(*)(*)(*)(*)(*)(*) disconnectReconnect()");
        ex = null;

        try
        {
            connected = false;
            if (strSocketName == null)
            {
                s.close();
                s = new Socket(host, port);
                in = new DataInputStream(s.getInputStream());
                out = new DataOutputStream(s.getOutputStream());
            }
            else
            {
                sLocal.disconnect();
                sLocal = LocalStringServerSocket.connectTo(strSocketName);
            }
            connected = true;
            reader = new Thread(this);
            reader.start();

            //resetThread = new SOCRobotResetThread(this);
            //resetThread.start();
            put(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum()));
            put(SOCImARobot.toCmd(nickname));
        }
        catch (Exception e)
        {
            ex = e;
            System.err.println("disconnectReconnect error: " + ex);
        }
    }

    /**
     * Treat the incoming messages.
     * Messages of unknown type are ignored (mes will be null from {@link SOCMessage#toMsg(String)}).
     *
     * @param mes    the message
     */
    public void treat(SOCMessage mes)
    {
        if (mes == null)
            return;  // Message syntax error or unknown type

        D.ebugPrintln("IN - " + mes);

        try
        {
            switch (mes.getType())
            {
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
             * admin ping
             */
            case SOCMessage.ADMINPING:
                handleADMINPING((SOCAdminPing) mes);

                break;

            /**
             * admin reset
             */
            case SOCMessage.ADMINRESET:
                handleADMINRESET((SOCAdminReset) mes);

                break;

            /**
             * update the current robot parameters
             */
            case SOCMessage.UPDATEROBOTPARAMS:
                handleUPDATEROBOTPARAMS((SOCUpdateRobotParams) mes);

                break;

            /**
             * join game authorization
             */
            case SOCMessage.JOINGAMEAUTH:
                handleJOINGAMEAUTH((SOCJoinGameAuth) mes);

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
             * game text message
             */
            case SOCMessage.GAMETEXTMSG:
                handleGAMETEXTMSG((SOCGameTextMsg) mes);

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
             * receive resource count
             */
            case SOCMessage.RESOURCECOUNT:
                handleRESOURCECOUNT((SOCResourceCount) mes);

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
             * the current player has cancelled an initial settlement
             */
            case SOCMessage.CANCELBUILDREQUEST:
                handleCANCELBUILDREQUEST((SOCCancelBuildRequest) mes);

                break;

            /**
             * the robber moved
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
             * a player has accepted an offer
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
             * the current number of development cards
             */
            case SOCMessage.DEVCARDCOUNT:
                handleDEVCARDCOUNT((SOCDevCardCount) mes);

                break;

            /**
             * a dev card action, either draw, play, or add to hand
             */
            case SOCMessage.DEVCARD:
                handleDEVCARD((SOCDevCard) mes);

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
                handlePOTENTIALSETTLEMENTS((SOCPotentialSettlements) mes);

                break;

            /**
             * the server is requesting that we join a game
             */
            case SOCMessage.JOINGAMEREQUEST:
                handleJOINGAMEREQUEST((SOCJoinGameRequest) mes);

                break;

            /**
             * message that means the server wants us to leave the game
             */
            case SOCMessage.ROBOTDISMISS:
                handleROBOTDISMISS((SOCRobotDismiss) mes);

                break;

            /**
             * handle the reject connection message - JM TODO: placement within switch? (vs displaylesscli, playercli) 
             */
            case SOCMessage.REJECTCONNECTION:
                handleREJECTCONNECTION((SOCRejectConnection) mes);

                break;

            /**
             * handle board reset (new game with same players, same game name, new layout).
             */
            case SOCMessage.RESETBOARDAUTH:
                handleRESETBOARDAUTH((SOCResetBoardAuth) mes);

                break;
            }
        }
        catch (Throwable e)
        {
            System.err.println("SOCRobotClient treat ERROR - " + e + " " + e.getMessage());
            e.printStackTrace();
            while (e.getCause() != null)
            {
                e = e.getCause();
                System.err.println(" -> nested: " + e.getClass());
                e.printStackTrace();
            }
            System.err.println("-- end stacktrace --");
        }
    }

    /**
     * handle the server ping message
     * @param mes  the message
     */
    protected void handleSERVERPING(SOCServerPing mes)
    {
        /*
           D.ebugPrintln("(*)(*) ServerPing message = "+mes);
           D.ebugPrintln("(*)(*) ServerPing sleepTime = "+mes.getSleepTime());
           D.ebugPrintln("(*)(*) resetThread = "+resetThread);
           resetThread.sleepMore();
         */
    }

    /**
     * handle the admin ping message
     * @param mes  the message
     */
    protected void handleADMINPING(SOCAdminPing mes)
    {
        D.ebugPrintln("*** Admin Ping message = " + mes);

        SOCGame ga = (SOCGame) games.get(mes.getGame());

        //
        //  if the robot hears a PING and is in the game
        //  where the admin is, then just say "OK".
        //  otherwise, join the game that the admin is in
        //
        //  note: this is a hack because the bot never 
        //        leaves the game and the game must be 
        //        killed by the admin
        //
        if (ga != null)
        {
            sendText(ga, "OK");
        }
        else
        {
            put(SOCJoinGame.toCmd(nickname, password, host, mes.getGame()));
        }
    }

    /**
     * handle the admin reset message
     * @param mes  the message
     */
    protected void handleADMINRESET(SOCAdminReset mes)
    {
        D.ebugPrintln("*** Admin Reset message = " + mes);
        disconnectReconnect();
    }

    /**
     * handle the update robot params message
     * @param mes  the message
     */
    protected void handleUPDATEROBOTPARAMS(SOCUpdateRobotParams mes)
    {
        currentRobotParameters = new SOCRobotParameters(mes.getRobotParameters());
        D.ebugPrintln("*** current robot parameters = " + currentRobotParameters);
    }

    /**
     * handle the "join game request" message.
     * Remember the game options, and record in {@link #seatRequests}.
     * Send a {@link SOCJoinGame JOINGAME} to server in response.
     * Server will reply with {@link SOCJoinGameAuth JOINGAMEAUTH}.
     *<P>
     * Board resets are handled similarly.
     * @param mes  the message
     *
     * @see #handleRESETBOARDAUTH(SOCResetBoardAuth)
     */
    protected void handleJOINGAMEREQUEST(SOCJoinGameRequest mes)
    {
        D.ebugPrintln("**** handleJOINGAMEREQUEST ****");
	final String gaName = mes.getGame();
	Hashtable gaOpts = mes.getOptions();
	if (gaOpts != null)
	    gameOptions.put(gaName, gaOpts);

        seatRequests.put(gaName, new Integer(mes.getPlayerNumber()));
        if (put(SOCJoinGame.toCmd(nickname, password, host, gaName)))
        {
            D.ebugPrintln("**** sent SOCJoinGame ****");
        }
    }

    /**
     * handle the "status message" message by printing it to System.err;
     * messages with status value 0 are ignored (no problem is being reported)
     * once the initial welcome message has been printed.
     * @param mes  the message
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes)
    {
        final int sv = mes.getStatusValue();
        if ((sv != 0) || ! printedInitialWelcome)
        {
            System.err.println("Robot " + getNickname() + ": Status "
                + sv + " from server: " + mes.getStatus());
            if (sv == 0)
                printedInitialWelcome = true;
        }
    }

    /**
     * handle the "join game authorization" message
     * @param mes  the message
     */
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes)
    {
        gamesPlayed++;

	final String gaName = mes.getGame();

	SOCGame ga = new SOCGame(gaName, true, (Hashtable) gameOptions.get(gaName));
        games.put(gaName, ga);

        CappedQueue brainQ = new CappedQueue();
        brainQs.put(gaName, brainQ);

        SOCRobotBrain rb = new SOCRobotBrain(this, currentRobotParameters, ga, brainQ);
        robotBrains.put(gaName, rb);
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
    protected void handleLEAVEGAME(SOCLeaveGame mes) {}

    /**
     * handle the "game members" message
     * @param mes  the message
     */
    protected void handleGAMEMEMBERS(SOCGameMembers mes)
    {
        /**
         * sit down to play
         */
        Integer pn = (Integer) seatRequests.get(mes.getGame());

        try
        {
            //wait(Math.round(Math.random()*1000));
        }
        catch (Exception e)
        {
            ;
        }

        if (pn != null)
        {
            put(SOCSitDown.toCmd(mes.getGame(), nickname, pn.intValue(), true));
        } else {
            System.err.println("** Cannot sit down: Assert failed: null pn for game " + mes.getGame());
        }
    }

    /**
     * handle the "game text message" message
     * @param mes  the message
     */
    protected void handleGAMETEXTMSG(SOCGameTextMsg mes)
    {
        //D.ebugPrintln(mes.getNickname()+": "+mes.getText());
        if (mes.getText().startsWith(nickname + ":debug-off"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if (brain != null)
            {
                brain.turnOffDRecorder();
                sendText(ga, "Debug mode OFF");
            }
        }

        if (mes.getText().startsWith(nickname + ":debug-on"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if (brain != null)
            {
                brain.turnOnDRecorder();
                sendText(ga, "Debug mode ON");
            }
        }

        if (mes.getText().startsWith(nickname + ":current-plans") || mes.getText().startsWith(nickname + ":cp"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                sendRecordsText(ga, brain.getDRecorder().getRecord(CURRENT_PLANS));
            }
        }

        if (mes.getText().startsWith(nickname + ":current-resources") || mes.getText().startsWith(nickname + ":cr"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                sendRecordsText(ga, brain.getDRecorder().getRecord(CURRENT_RESOURCES));
            }
        }

        if (mes.getText().startsWith(nickname + ":last-plans") || mes.getText().startsWith(nickname + ":lp"))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                Vector record = brain.getOldDRecorder().getRecord(CURRENT_PLANS);

                if (record != null)
                {
                    SOCGame ga = (SOCGame) games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        if (mes.getText().startsWith(nickname + ":last-resources") || mes.getText().startsWith(nickname + ":lr"))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                Vector record = brain.getOldDRecorder().getRecord(CURRENT_RESOURCES);

                if (record != null)
                {
                    SOCGame ga = (SOCGame) games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        if (mes.getText().startsWith(nickname + ":last-move") || mes.getText().startsWith(nickname + ":lm"))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getOldDRecorder().isOn()))
            {
                SOCPossiblePiece lastMove = brain.getLastMove();

                if (lastMove != null)
                {
                    String key = null;

                    switch (lastMove.getType())
                    {
                    case SOCPossiblePiece.CARD:
                        key = "DEVCARD";

                        break;

                    case SOCPossiblePiece.ROAD:
                        key = "ROAD" + lastMove.getCoordinates();

                        break;

                    case SOCPossiblePiece.SETTLEMENT:
                        key = "SETTLEMENT" + lastMove.getCoordinates();

                        break;

                    case SOCPossiblePiece.CITY:
                        key = "CITY" + lastMove.getCoordinates();

                        break;
                    }

                    Vector record = brain.getOldDRecorder().getRecord(key);

                    if (record != null)
                    {
                        SOCGame ga = (SOCGame) games.get(mes.getGame());
                        sendRecordsText(ga, record);
                    }
                }
            }
        }

        if (mes.getText().startsWith(nickname + ":consider-move ") || mes.getText().startsWith(nickname + ":cm "))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getOldDRecorder().isOn()))
            {
                String[] tokens = mes.getText().split(" ");
                String key = null;

                if (tokens[1].trim().equals("card"))
                {
                    key = "DEVCARD";
                }
                else if (tokens[1].equals("road"))
                {
                    key = "ROAD" + tokens[2].trim();
                }
                else if (tokens[1].equals("settlement"))
                {
                    key = "SETTLEMENT" + tokens[2].trim();
                }
                else if (tokens[1].equals("city"))
                {
                    key = "CITY" + tokens[2].trim();
                }

                Vector record = brain.getOldDRecorder().getRecord(key);

                if (record != null)
                {
                    SOCGame ga = (SOCGame) games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        if (mes.getText().startsWith(nickname + ":last-target") || mes.getText().startsWith(nickname + ":lt"))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                SOCPossiblePiece lastTarget = brain.getLastTarget();

                if (lastTarget != null)
                {
                    String key = null;

                    switch (lastTarget.getType())
                    {
                    case SOCPossiblePiece.CARD:
                        key = "DEVCARD";

                        break;

                    case SOCPossiblePiece.ROAD:
                        key = "ROAD" + lastTarget.getCoordinates();

                        break;

                    case SOCPossiblePiece.SETTLEMENT:
                        key = "SETTLEMENT" + lastTarget.getCoordinates();

                        break;

                    case SOCPossiblePiece.CITY:
                        key = "CITY" + lastTarget.getCoordinates();

                        break;
                    }

                    Vector record = brain.getDRecorder().getRecord(key);

                    if (record != null)
                    {
                        SOCGame ga = (SOCGame) games.get(mes.getGame());
                        sendRecordsText(ga, record);
                    }
                }
            }
        }

        if (mes.getText().startsWith(nickname + ":consider-target ") || mes.getText().startsWith(nickname + ":ct "))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                String[] tokens = mes.getText().split(" ");
                String key = null;

                if (tokens[1].trim().equals("card"))
                {
                    key = "DEVCARD";
                }
                else if (tokens[1].equals("road"))
                {
                    key = "ROAD" + tokens[2].trim();
                }
                else if (tokens[1].equals("settlement"))
                {
                    key = "SETTLEMENT" + tokens[2].trim();
                }
                else if (tokens[1].equals("city"))
                {
                    key = "CITY" + tokens[2].trim();
                }

                Vector record = brain.getDRecorder().getRecord(key);

                if (record != null)
                {
                    SOCGame ga = (SOCGame) games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        if (mes.getText().startsWith(nickname + ":stats"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            sendText(ga, "Games played:" + gamesPlayed);
            sendText(ga, "Games finished:" + gamesFinished);
            sendText(ga, "Games won:" + gamesWon);
            sendText(ga, "Clean brain kills:" + cleanBrainKills);
            sendText(ga, "Brains running: " + robotBrains.size());

            Runtime rt = Runtime.getRuntime();
            sendText(ga, "Total Memory:" + rt.totalMemory());
            sendText(ga, "Free Memory:" + rt.freeMemory());
        }

        if (mes.getText().startsWith(nickname + ":gc"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            Runtime rt = Runtime.getRuntime();
            rt.gc();
            sendText(ga, "Free Memory:" + rt.freeMemory());
        }

        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "someone is sitting down" message
     * @param mes  the message
     */
    protected void handleSITDOWN(SOCSitDown mes)
    {
        /**
         * tell the game that a player is sitting
         */
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            ga.addPlayer(mes.getNickname(), mes.getPlayerNumber());

            /**
             * set the robot flag
             */
            ga.getPlayer(mes.getPlayerNumber()).setRobotFlag(mes.isRobot());

            /**
             * let the robot brain find our player object if we sat down
             */
            if (nickname.equals(mes.getNickname()))
            {
                SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

                /**
                 * retrieve the proper face for our strategy
                 */
                int faceId;
                switch (brain.getRobotParameters().getStrategyType())
                {
                case SOCRobotDM.SMART_STRATEGY:
                    faceId = -1;  // smarter robot face
                    break;

                default:
                    faceId = 0;   // default robot face
                }

                brain.setOurPlayerData();
                brain.start();

                /**
                 * change our face to the robot face
                 */
                put(SOCChangeFace.toCmd(ga.getName(), mes.getPlayerNumber(), faceId));
            }
            else
            {
                /**
                 * add tracker for player in previously vacant seat
                 */
                SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

                if (brain != null)
                {
                    brain.addPlayerTracker(mes.getPlayerNumber());
                }
            }
        }
    }

    /**
     * handle the "board layout" message
     * @param mes  the message
     */
    protected void handleBOARDLAYOUT(SOCBoardLayout mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCBoard bd = ga.getBoard();
            bd.setHexLayout(mes.getHexLayout());
            bd.setNumberLayout(mes.getNumberLayout());
            bd.setRobberHex(mes.getRobberHex());
        }
    }

    /**
     * handle the "start game" message
     * @param mes  the message
     */
    protected void handleSTARTGAME(SOCStartGame mes) {}

    /**
     * handle the "delete game" message
     * @param mes  the message
     */
    protected void handleDELETEGAME(SOCDeleteGame mes)
    {
        SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

        if (brain != null)
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());

            if (ga != null)
            {
                if (ga.getGameState() == SOCGame.OVER)
                {
                    gamesFinished++;

                    if (ga.getPlayer(nickname).getTotalVP() >= SOCGame.VP_WINNER)
                    {
                        gamesWon++;
                        // TODO: hardcoded, assumes 10 to win (VP_WINNER)
                    }
                }

                brain.kill();
                robotBrains.remove(mes.getGame());
                brainQs.remove(mes.getGame());
                games.remove(mes.getGame());
            }
        }
    }

    /**
     * handle the "game state" message
     * @param mes  the message
     */
    protected void handleGAMESTATE(SOCGameState mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

            if (brainQ != null)
            {
                try
                {
                    brainQ.put(mes);
                }
                catch (CutoffExceededException exc)
                {
                    D.ebugPrintln("CutoffExceededException" + exc);
                }
            }
        }
    }

    /**
     * handle the "set turn" message
     * @param mes  the message
     */
    protected void handleSETTURN(SOCSetTurn mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "set first player" message
     * @param mes  the message
     */
    protected void handleFIRSTPLAYER(SOCFirstPlayer mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "turn" message
     * @param mes  the message
     */
    protected void handleTURN(SOCTurn mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "player element" message
     * @param mes  the message
     */
    protected void handlePLAYERELEMENT(SOCPlayerElement mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle "resource count" message
     * @param mes  the message
     */
    protected void handleRESOURCECOUNT(SOCResourceCount mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "dice result" message
     * @param mes  the message
     */
    protected void handleDICERESULT(SOCDiceResult mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "put piece" message
     * @param mes  the message
     */
    protected void handlePUTPIECE(SOCPutPiece mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }

            SOCGame ga = (SOCGame) games.get(mes.getGame());

            if (ga != null)
            {
                // SOCPlayer pl = ga.getPlayer(mes.getPlayerNumber());
                // JDM TODO - Was this in stock client?
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
     *<P>
     * - During piece placement (PLACING_ROAD, PLACING_CITY, PLACING_SETTLEMENT,
     *                           PLACING_FREE_ROAD1 or PLACING_FREE_ROAD2):
     *<P>
     *      Sent from server, CANCELBUILDREQUEST means the player has sent
     *      an illegal PUTPIECE (bad building location). Humans can probably
     *      decide a better place to put their road, but robots must cancel
     *      the build request and decide on a new plan.
     *<P>
     *      Our robot client sends this to the brain to act on.
     *
     * @param mes  the message
     */
    protected void handleCANCELBUILDREQUEST(SOCCancelBuildRequest mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "move robber" message
     * @param mes  the message
     */
    protected void handleMOVEROBBER(SOCMoveRobber mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "discard request" message
     * @param mes  the message
     */
    protected void handleDISCARDREQUEST(SOCDiscardRequest mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "choose player request" message
     * @param mes  the message
     */
    protected void handleCHOOSEPLAYERREQUEST(SOCChoosePlayerRequest mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "make offer" message
     * @param mes  the message
     */
    protected void handleMAKEOFFER(SOCMakeOffer mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "clear offer" message
     * @param mes  the message
     */
    protected void handleCLEAROFFER(SOCClearOffer mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "reject offer" message
     * @param mes  the message
     */
    protected void handleREJECTOFFER(SOCRejectOffer mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "accept offer" message
     * @param mes  the message
     */
    protected void handleACCEPTOFFER(SOCAcceptOffer mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "clear trade" message
     * @param mes  the message
     */
    protected void handleCLEARTRADEMSG(SOCClearTradeMsg mes) {}

    /**
     * handle the "development card count" message
     * @param mes  the message
     */
    protected void handleDEVCARDCOUNT(SOCDevCardCount mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "development card action" message
     * @param mes  the message
     */
    protected void handleDEVCARD(SOCDevCard mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "set played development card" message
     * @param mes  the message
     */
    protected void handleSETPLAYEDDEVCARD(SOCSetPlayedDevCard mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "dismiss robot" message
     * @param mes  the message
     */
    protected void handleROBOTDISMISS(SOCRobotDismiss mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if ((ga != null) && (brainQ != null))
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }

            /**
             * if the brain isn't alive, then we need to leave
             * the game
             */
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain == null) || (!brain.isAlive()))
            {
                leaveGame((SOCGame) games.get(mes.getGame()), "brain not alive");
            }
        }
    }

    /**
     * handle the "potential settlements" message
     * @param mes  the message
     */
    protected void handlePOTENTIALSETTLEMENTS(SOCPotentialSettlements mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "change face" message
     * @param mes  the message
     */
    protected void handleCHANGEFACE(SOCChangeFace mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
            player.setFaceId(mes.getFaceId());
        }
    }

    /**
     * handle the "longest road" message
     * @param mes  the message
     */
    protected void handleLONGESTROAD(SOCLongestRoad mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            if (mes.getPlayerNumber() == -1)
            {
                ga.setPlayerWithLongestRoad((SOCPlayer) null);
            }
            else
            {
                ga.setPlayerWithLongestRoad(ga.getPlayer(mes.getPlayerNumber()));
            }
        }
    }

    /**
     * handle the "largest army" message
     * @param mes  the message
     */
    protected void handleLARGESTARMY(SOCLargestArmy mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            if (mes.getPlayerNumber() == -1)
            {
                ga.setPlayerWithLargestArmy((SOCPlayer) null);
            }
            else
            {
                ga.setPlayerWithLargestArmy(ga.getPlayer(mes.getPlayerNumber()));
            }
        }
    }

    /**
     * handle board reset
     * (new game with same players, same game name).
     * Destroy old Game object.
     * Take robotbrain out of old game, don't yet put it in new game.
     * Treat as a JOINGAMEREQUEST: ask server for us to join the new game.
     *
     * @param mes  the message
     * 
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     * @see soc.game.SOCGame#resetAsCopy()
     * @see #handleJOINGAMEREQUEST(SOCJoinGameRequest)
     */
    protected void handleRESETBOARDAUTH(SOCResetBoardAuth mes)
    {
        D.ebugPrintln("**** handleRESETBOARDAUTH ****");

        String gname = mes.getGame();
        SOCGame ga = (SOCGame) games.get(gname);
        if (ga == null)
            return;  // Not one of our games

        SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(gname);
        if (brain != null)
        {
            brain.kill();
            robotBrains.remove(gname);
        }
        brainQs.remove(gname);
        games.remove(gname);
        ga.destroyGame();

        // now, react like handleJOINGAMEREQUEST
        seatRequests.put(gname, new Integer(mes.getRejoinPlayerNumber()));

        if (put(SOCJoinGame.toCmd(nickname, password, host, gname)))
        {
            D.ebugPrintln("**** sent SOCJoinGame ****");
        }
    }

    /**
     * Call sendText on each string element of record.
     * @param ga Game to sendText to
     * @param record Strings to send, or null
     */
    protected void sendRecordsText(SOCGame ga, Vector record)
    {
        if (record != null)
        {
            Enumeration renum = record.elements();

            while (renum.hasMoreElements())
            {
                String str = (String) renum.nextElement();
                sendText(ga, str);
            }
        }
    }

    /**
     * the user leaves the given game
     *
     * @param ga   the game
     * @param leaveReason reason for leaving
     */
    public void leaveGame(SOCGame ga, String leaveReason)
    {
        if (ga != null)
        {
            robotBrains.remove(ga.getName());
            brainQs.remove(ga.getName());
            games.remove(ga.getName());
            System.err.println("L1833 robot leaving game" + ga + " due to " + leaveReason);
            soc.debug.D.ebugPrintStackTrace(null, "Leaving game here");
            System.err.flush();
            put(SOCLeaveGame.toCmd(nickname, host, ga.getName()));
        }
    }

    /**
     * add one the the number of clean brain kills
     */
    public void addCleanKill()
    {
        cleanBrainKills++;
    }

    /** losing connection to server; leave all games, then try to reconnect */
    public void destroy()
    {
        SOCLeaveAll leaveAllMes = new SOCLeaveAll();
        put(leaveAllMes.toCmd());
        disconnectReconnect();
        if (ex != null)
            System.err.println("Reconnect to server failed: " + ex);
    }

    /**
     * for stand-alones
     */
    public static void main(String[] args)
    {
		if (args.length < 4)
		{
			System.err.println("usage: java soc.robot.SOCRobotClient host port_number userid password");

			return;
		}
    	
        SOCRobotClient ex1 = new SOCRobotClient(args[0], Integer.parseInt(args[1]), args[2], args[3]);
        ex1.init();
    }
}
