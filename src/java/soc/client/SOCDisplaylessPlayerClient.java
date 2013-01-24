/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2013 Jeremy D Monin <jeremy@nand.net>
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
package soc.client;

import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCTradeOffer;
import soc.game.SOCVillage;

import soc.message.*;
import soc.robot.SOCRobotClient;
import soc.server.genericServer.LocalStringConnection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;

import java.net.Socket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;


/**
 * GUI-less standalone client for connecting to the SOCServer.
 * If you want another connection port, you have to specify it as the "port"
 * argument in the html source. If you run this as a stand-alone, you have to
 * specify the port.
 *<P>
 * The {@link soc.robot.SOCRobotClient} is based on this client.
 * Because of this, some methods (such as {@link #handleVERSION(boolean, SOCVersion)})
 * assume the client and server are the same version.
 *
 * @author Robert S Thomas
 */
public class SOCDisplaylessPlayerClient implements Runnable
{
    protected static String STATSPREFEX = "  [";
    protected String doc;
    protected String lastMessage;

    protected String host;
    protected int port;
    protected String strSocketName;  // For robots in local practice games
    protected Socket s;
    protected DataInputStream in;
    protected DataOutputStream out;
    protected LocalStringConnection sLocal;  // if strSocketName not null
    /**
     * Server version number, sent soon after connect, or -1 if unknown.
     * {@link #sLocalVersion} should always equal our own version.
     */
    protected int sVersion, sLocalVersion;
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
     * Create a SOCDisplaylessPlayerClient, which would connect to localhost port 8889.
     * Does not actually connect; subclass must connect, such as {@link soc.robot.SOCRobotClient#init()}
     *<P>
     * <b>Note:</b> The default JSettlers server port is 8880.
     */
    public SOCDisplaylessPlayerClient()
    {
        host = null;
        port = 8889;
        strSocketName = null;
        gotPassword = false;
        sVersion = -1;  sLocalVersion = -1;
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
        this();         
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
            if (!connected)
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
        put(lastMessage);
    }

    /**
     * write a message to the net
     *
     * @param s  the message
     * @return true if the message was sent, false if not
     */
    public synchronized boolean put(String s)
    {
        lastMessage = s;

        D.ebugPrintln("OUT - " + s);

        if ((ex != null) || !connected)
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
     * Messages of unknown type are ignored (mes will be null from {@link SOCMessage#toMsg(String)}).
     *<P>
     *<B>Note:</B> Currently, <tt>SOCRobotClient.treat(mes)</tt> does not call this method.
     * New messages should be added in both places if both displayless and robot should handle them.
     *
     * @param mes    the message
     */
    public void treat(SOCMessage mes)
    {
        if (mes == null)
            return;  // Msg parsing error

        D.ebugPrintln(mes.toString());

        try
        {
            switch (mes.getType())
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
             * join channel authorization
             */
            case SOCMessage.JOINAUTH:
                handleJOINAUTH((SOCJoinAuth) mes);

                break;

            /**
             * someone joined a channel
             */
            case SOCMessage.JOIN:
                handleJOIN((SOCJoin) mes);

                break;

            /**
             * list of members for a channel
             */
            case SOCMessage.MEMBERS:
                handleMEMBERS((SOCMembers) mes);

                break;

            /**
             * a new channel has been created
             */
            case SOCMessage.NEWCHANNEL:
                handleNEWCHANNEL((SOCNewChannel) mes);

                break;

            /**
             * list of channels on the server
             */
            case SOCMessage.CHANNELS:
                handleCHANNELS((SOCChannels) mes);

                break;

            /**
             * text message
             */
            case SOCMessage.TEXTMSG:
                handleTEXTMSG((SOCTextMsg) mes);

                break;

            /**
             * someone left the channel
             */
            case SOCMessage.LEAVE:
                handleLEAVE((SOCLeave) mes);

                break;

            /**
             * delete a channel
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
            case SOCMessage.DEVCARD:
                handleDEVCARD((sLocal != null), (SOCDevCard) mes);

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
             * move a previous piece (a ship) somewhere else on the board.
             * Added 2011-12-05 for v2.0.00.
             */
            case SOCMessage.MOVEPIECE:
                handleMOVEPIECE((SOCMovePiece) mes);
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

            }
        }
        catch (Exception e)
        {
            System.out.println("SOCDisplaylessPlayerClient treat ERROR - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * handle the "status message" message
     * @param mes  the message
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes) {}

    /**
     * handle the "join authorization" message
     * @param mes  the message
     */
    protected void handleJOINAUTH(SOCJoinAuth mes)
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
     *
     * @param isPractice Is the server local, or remote?  Client can be connected
     *                only to local, or remote.
     * @param mes  the messsage
     */
    private void handleVERSION(boolean isLocal, SOCVersion mes)
    {
        D.ebugPrintln("handleVERSION: " + mes);
        int vers = mes.getVersionNumber();
        if (isLocal)
            sLocalVersion = vers;
        else
            sVersion = vers;

        // TODO check for minimum,maximum

        // Clients v1.1.07 and later send SOCVersion right away at connect,
        // so no need to reply here with our client version.

        // Don't check for game options different at version, unlike SOCPlayerClient.handleVERSION.
    }

    /**
     * handle the "join channel" message
     * @param mes  the message
     */
    protected void handleJOIN(SOCJoin mes) {}

    /**
     * handle the "members" message
     * @param mes  the message
     */
    protected void handleMEMBERS(SOCMembers mes) {}

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
    protected void handleTEXTMSG(SOCTextMsg mes) {}

    /**
     * handle the "leave channel" message
     * @param mes  the message
     */
    protected void handleLEAVE(SOCLeave mes) {}

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
     * handle the "game text message" message
     * @param mes  the message
     */
    protected void handleGAMETEXTMSG(SOCGameTextMsg mes) {}

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
        }
    }

    /**
     * handle the "board layout" message, new format
     * @param mes  the message
     * @since 1.1.08
     */
    protected void handleBOARDLAYOUT2(SOCBoardLayout2 mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        SOCBoard bd = ga.getBoard();
        final int bef = mes.getBoardEncodingFormat();
        bd.setBoardEncodingFormat(bef);
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
        }
    }

    /**
     * handle the "start game" message
     * @param mes  the message
     */
    protected void handleSTARTGAME(SOCStartGame mes) {}

    /**
     * handle the "game state" message
     * @param mes  the message
     */
    protected void handleGAMESTATE(SOCGameState mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            ga.setGameState(mes.getState());
        }
    }

    /**
     * handle the "set turn" message
     * @param mes  the message
     */
    protected void handleSETTURN(SOCSetTurn mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            ga.setCurrentPlayerNumber(mes.getPlayerNumber());
        }
    }

    /**
     * handle the "first player" message
     * @param mes  the message
     */
    protected void handleFIRSTPLAYER(SOCFirstPlayer mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            ga.setFirstPlayer(mes.getPlayerNumber());
        }
    }

    /**
     * handle the "turn" message
     * @param mes  the message
     */
    protected void handleTURN(SOCTurn mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            ga.setCurrentPlayerNumber(mes.getPlayerNumber());
            ga.updateAtTurn();
        }
    }

    /**
     * handle the "player information" message
     * @param mes  the message
     */
    protected void handlePLAYERELEMENT(SOCPlayerElement mes)
    {
        final SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            final int pn = mes.getPlayerNumber();
            final SOCPlayer pl = (pn != -1) ? ga.getPlayer(pn) : null;

            switch (mes.getElementType())
            {
            case SOCPlayerElement.ROADS:

                handlePLAYERELEMENT_numPieces(mes, pl, SOCPlayingPiece.ROAD);
                break;

            case SOCPlayerElement.SETTLEMENTS:

                handlePLAYERELEMENT_numPieces(mes, pl, SOCPlayingPiece.SETTLEMENT);
                break;

            case SOCPlayerElement.CITIES:

                handlePLAYERELEMENT_numPieces(mes, pl, SOCPlayingPiece.CITY);
                break;

            case SOCPlayerElement.SHIPS:
                handlePLAYERELEMENT_numPieces(mes, pl, SOCPlayingPiece.SHIP);
                break;

            case SOCPlayerElement.NUMKNIGHTS:

                // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
                handlePLAYERELEMENT_numKnights(mes, pl, ga);
                break;

            case SOCPlayerElement.CLAY:

                handlePLAYERELEMENT_numRsrc(mes, pl, SOCResourceConstants.CLAY);
                break;

            case SOCPlayerElement.ORE:

                handlePLAYERELEMENT_numRsrc(mes, pl, SOCResourceConstants.ORE);
                break;

            case SOCPlayerElement.SHEEP:

                handlePLAYERELEMENT_numRsrc(mes, pl, SOCResourceConstants.SHEEP);
                break;

            case SOCPlayerElement.WHEAT:

                handlePLAYERELEMENT_numRsrc(mes, pl, SOCResourceConstants.WHEAT);
                break;

            case SOCPlayerElement.WOOD:

                handlePLAYERELEMENT_numRsrc(mes, pl, SOCResourceConstants.WOOD);
                break;

            case SOCPlayerElement.UNKNOWN:

                /**
                 * Note: if losing unknown resources, we first
                 * convert player's known resources to unknown resources,
                 * then remove mes's unknown resources from player.
                 */
                handlePLAYERELEMENT_numRsrc(mes, pl, SOCResourceConstants.UNKNOWN);
                break;

            default:
                handlePLAYERELEMENT_simple(mes, ga, pl, pn);
                break;

            }
        }
    }

    /**
     * Update game data for a simple player element or flag, for {@link #handlePLAYERELEMENT(SOCPlayerElement)}.
     * Handles ASK_SPECIAL_BUILD, NUM_PICK_GOLD_HEX_RESOURCES, SCENARIO_CLOTH_COUNT, etc.
     *<P>
     * To avoid code duplication, also called from
     * {@link SOCPlayerClient#handlePLAYERELEMENT(SOCPlayerElement)}
     * and {@link soc.robot.SOCRobotBrain#run()}.
     *
     * @param mes  Message with amount and action (SET/GAIN/LOSE)
     * @param ga   Game to update
     * @param pl   Player to update
     * @param pn   Player number from message (sometimes -1 for none)
     * @since 2.0.00
     */
    public static void handlePLAYERELEMENT_simple
        (SOCPlayerElement mes, SOCGame ga, SOCPlayer pl, final int pn)
    {
        final int val = mes.getValue();

        switch (mes.getElementType())
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
            switch (mes.getAction())
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
     * Update a player's amount of a playing piece, for {@link #handlePLAYERELEMENT(SOCPlayerElement)}.
     * To avoid code duplication, also called from
     * {@link SOCPlayerClient#handlePLAYERELEMENT(SOCPlayerElement)}
     * and {@link soc.robot.SOCRobotBrain#run()}.
     *
     * @param mes       Message with amount and action (SET/GAIN/LOSE)
     * @param pl        Player to update
     * @param pieceType Playing piece type, as in {@link SOCPlayingPiece#ROAD}
     */
    public static void handlePLAYERELEMENT_numPieces
        (SOCPlayerElement mes, final SOCPlayer pl, int pieceType)
    {
        switch (mes.getAction())
        {
        case SOCPlayerElement.SET:
            pl.setNumPieces(pieceType, mes.getValue());

            break;

        case SOCPlayerElement.GAIN:
            pl.setNumPieces(pieceType, pl.getNumPieces(pieceType) + mes.getValue());

            break;

        case SOCPlayerElement.LOSE:
            pl.setNumPieces(pieceType, pl.getNumPieces(pieceType) - mes.getValue());

            break;
        }
    }

    /**
     * Update a player's amount of knights, and game's largest army,
     * for {@link #handlePLAYERELEMENT(SOCPlayerElement)}.
     * To avoid code duplication, also called from
     * {@link SOCPlayerClient#handlePLAYERELEMENT(SOCPlayerElement)}
     * and {@link soc.robot.SOCRobotBrain#run()}.
     *
     * @param mes  Message with amount and action (SET/GAIN/LOSE)
     * @param pl   Player to update
     * @param ga   Game of player
     */
    public static void handlePLAYERELEMENT_numKnights
        (SOCPlayerElement mes, final SOCPlayer pl, final SOCGame ga)
    {
        switch (mes.getAction())
        {
        case SOCPlayerElement.SET:
            pl.setNumKnights(mes.getValue());
    
            break;
    
        case SOCPlayerElement.GAIN:
            pl.setNumKnights(pl.getNumKnights() + mes.getValue());
    
            break;
    
        case SOCPlayerElement.LOSE:
            pl.setNumKnights(pl.getNumKnights() - mes.getValue());
    
            break;
        }
    
        ga.updateLargestArmy();
    }
    
    /**
     * Update a player's amount of a resource, for {@link #handlePLAYERELEMENT(SOCPlayerElement)}.
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
     * {@link SOCPlayerClient#handlePLAYERELEMENT(SOCPlayerElement)}
     * and {@link soc.robot.SOCRobotBrain#run()}.
     *
     * @param mes    Message with amount and action (SET/GAIN/LOSE)
     * @param pl     Player to update
     * @param rtype  Type of resource, like {@link SOCResourceConstants#CLAY}
     */
    public static void handlePLAYERELEMENT_numRsrc
        (SOCPlayerElement mes, final SOCPlayer pl, int rtype)
    {
        final int amount = mes.getValue();

        switch (mes.getAction())
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
                pl.getResources().subtract(mes.getValue(), SOCResourceConstants.UNKNOWN);
            }

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

        if (ga != null)
        {
            final SOCPlayer pl = ga.getPlayer(mes.getPlayerNumber());

            if (mes.getCount() != pl.getResources().getTotal())
            {
                SOCResourceSet rsrcs = pl.getResources();

                if (D.ebugOn)
                {
                    //pi.print(">>> RESOURCE COUNT ERROR: "+mes.getCount()+ " != "+rsrcs.getTotal());
                }

                //
                //  fix it
                //
                if (!pl.getName().equals(nickname))
                {
                    rsrcs.clear();
                    rsrcs.setAmount(mes.getCount(), SOCResourceConstants.UNKNOWN);
                }
            }
        }
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
     * @param mes  the message
     */
    protected void handlePUTPIECE(SOCPutPiece mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayer pl = ga.getPlayer(mes.getPlayerNumber());

            switch (mes.getPieceType())
            {
            case SOCPlayingPiece.ROAD:

                SOCRoad rd = new SOCRoad(pl, mes.getCoordinates(), null);
                ga.putPiece(rd);
                break;

            case SOCPlayingPiece.SETTLEMENT:

                SOCSettlement se = new SOCSettlement(pl, mes.getCoordinates(), null);
                ga.putPiece(se);
                break;

            case SOCPlayingPiece.CITY:

                SOCCity ci = new SOCCity(pl, mes.getCoordinates(), null);
                ga.putPiece(ci);
                break;

            case SOCPlayingPiece.VILLAGE:

                SOCVillage vi = new SOCVillage(mes.getCoordinates(), ga.getBoard());
                ga.putPiece(vi);
                break;

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
    *      Our client can ignore this case, because the server also sends a text
    *      message that the human player is capable of reading and acting on.
    *
    * @param mes  the message
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
            if (newHex >= 0)
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
    protected void handleCHOOSEPLAYERREQUEST(SOCChoosePlayerRequest mes)
    {
        boolean[] ch = mes.getChoices();
        int[] choices = new int[ch.length];  // == SOCGame.maxPlayers
        int count = 0;

        for (int i = 0; i < ch.length; i++)
        {
            if (ch[i])
            {
                choices[count] = i;
                count++;
            }
        }
    }

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
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            ga.setNumDevCards(mes.getNumDevCards());
        }
    }

    /**
     * handle the "development card action" message
     * @param isPractice  Is the server local, or remote?  Client can be connected
     *                only to local, or remote.
     * @param mes  the message
     */
    protected void handleDEVCARD(final boolean isPractice, SOCDevCard mes)
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
            case SOCDevCard.DRAW:
                player.getDevCards().add(1, SOCDevCardSet.NEW, ctype);

                break;

            case SOCDevCard.PLAY:
                player.getDevCards().subtract(1, SOCDevCardSet.OLD, ctype);

                break;

            case SOCDevCard.ADDOLD:
                player.getDevCards().add(1, SOCDevCardSet.OLD, ctype);

                break;

            case SOCDevCard.ADDNEW:
                player.getDevCards().add(1, SOCDevCardSet.NEW, ctype);

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
        {
            SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
            player.setPlayedDevCard(mes.hasPlayedDevCard());
        }
    }

    /**
     * handle the "list of potential settlements" message
     * @param mes  the message
     * @param games  The hashtable of client's {@link SOCGame}s; key = game name
     */
    public static void handlePOTENTIALSETTLEMENTS(SOCPotentialSettlements mes, Hashtable<String, SOCGame> games)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final Vector<Integer> vset = mes.getPotentialSettlements();
        final HashSet<Integer>[] las = mes.landAreasLegalNodes;
        int pn = mes.getPlayerNumber();
        if (ga.hasSeaBoard)
        {
            SOCBoardLarge bl = ((SOCBoardLarge) ga.getBoard());
            if ((pn == -1) || bl.getLegalAndPotentialSettlements().isEmpty())
                bl.setLegalAndPotentialSettlements
                  (vset, mes.startingLandArea, las);
        }
        if (pn != -1)
        {
            SOCPlayer player = ga.getPlayer(pn);
            player.setPotentialAndLegalSettlements(vset, true, las);
        } else {
            for (pn = ga.maxPlayers - 1; pn >= 0; --pn)
                ga.getPlayer(pn).setPotentialAndLegalSettlements(vset, true, las);
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
        SOCGame ga = games.get(mes.getGame());

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
        SOCGame ga = games.get(mes.getGame());

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
     * handle the "set seat lock" message
     * @param mes  the message
     */
    protected void handleSETSEATLOCK(SOCSetSeatLock mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            if (mes.getLockState() == true)
            {
                ga.lockSeat(mes.getPlayerNumber());
            }
            else
            {
                ga.unlockSeat(mes.getPlayerNumber());
            }
        }
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
     * Update a village piece's value on the board (cloth remaining).
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

        SOCVillage vi = ((SOCBoardLarge) (ga.getBoard())).getVillageAtNode(mes.getParam1());
        vi.setCloth(mes.getParam2());
    }

    /**
     * send a text message to a channel
     *
     * @param ch   the name of the channel
     * @param mes  the message
     */
    public void chSend(String ch, String mes)
    {
        put(SOCTextMsg.toCmd(ch, nickname, mes));
    }

    /**
     * the user leaves the given channel
     *
     * @param ch  the name of the channel
     */
    public void leaveChannel(String ch)
    {
        channels.remove(ch);
        put(SOCLeave.toCmd(nickname, host, ch));
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
        put(SOCBuyCardRequest.toCmd(ga.getName()));
    }

    /**
     * request to build something
     *
     * @param ga     the game
     * @param piece  the type of piece, from {@link soc.game.SOCPlayingPiece} constants,
     *               or -1 to request the Special Building Phase.
     */
    public void buildRequest(SOCGame ga, int piece)
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
     * @param pp  the piece being placed
     */
    public void putPiece(SOCGame ga, SOCPlayingPiece pp)
    {
        /**
         * send the command
         */
        put(SOCPutPiece.toCmd(ga.getName(), pp.getPlayerNumber(), pp.getType(), pp.getCoordinates()));
    }

    /**
     * the player wants to move the robber
     *
     * @param ga  the game
     * @param pl  the player
     * @param coord  where the player wants the robber
     */
    public void moveRobber(SOCGame ga, SOCPlayer pl, int coord)
    {
        put(SOCMoveRobber.toCmd(ga.getName(), pl.getPlayerNumber(), coord));
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
        put(SOCStartGame.toCmd(ga.getName()));
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
     * The user wants to pick these resources to gain from the gold hex.
     *
     * @param ga  the game
     * @param rs  Resources to gain
     * @since 2.0.00
     */
    public void pickFreeResources(SOCGame ga, SOCResourceSet rs)
    {
        put(SOCPickResources.toCmd(ga.getName(), rs));
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
     * the user wants to trade with the bank
     *
     * @param ga    the game
     * @param give  what is being offered
     * @param get   what the player wants
     */
    public void bankTrade(SOCGame ga, SOCResourceSet give, SOCResourceSet get)
    {
        put(SOCBankTrade.toCmd(ga.getName(), give, get));
    }

    /**
     * the user is making an offer to trade
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
     * the user picked 2 resources to discover
     *
     * @param ga    the game
     * @param rscs  the resources
     */
    public void discoveryPick(SOCGame ga, SOCResourceSet rscs)
    {
        put(SOCDiscoveryPick.toCmd(ga.getName(), rscs));
    }

    /**
     * the user picked a resource to monopolize
     *
     * @param ga   the game
     * @param res  the resource
     */
    public void monopolyPick(SOCGame ga, int res)
    {
        put(SOCMonopolyPick.toCmd(ga.getName(), res));
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
     * the user is locking a seat
     *
     * @param ga  the game
     * @param pn  the seat number
     */
    public void lockSeat(SOCGame ga, int pn)
    {
        put(SOCSetSeatLock.toCmd(ga.getName(), pn, true));
    }

    /**
     * the user is unlocking a seat
     *
     * @param ga  the game
     * @param pn  the seat number
     */
    public void unlockSeat(SOCGame ga, int pn)
    {
        put(SOCSetSeatLock.toCmd(ga.getName(), pn, false));
    }

    /** destroy the applet */
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
