/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2016 (C) Jeremy D Monin <jeremy@nand.net>
 * Some contents were formerly part of SOCServer.java
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

import soc.debug.D;
import soc.message.SOCAuthRequest;
import soc.message.SOCChangeFace;
import soc.message.SOCCreateAccount;
import soc.message.SOCDeleteChannel;
import soc.message.SOCGameOptionGetDefaults;
import soc.message.SOCGameOptionGetInfos;
import soc.message.SOCGameTextMsg;
import soc.message.SOCImARobot;
import soc.message.SOCJoin;
import soc.message.SOCJoinGame;
import soc.message.SOCLeave;
import soc.message.SOCLeaveGame;
import soc.message.SOCLocalizedStrings;
import soc.message.SOCMessage;
import soc.message.SOCNewGameWithOptionsRequest;
import soc.message.SOCResetBoardRequest;
import soc.message.SOCResetBoardVote;
import soc.message.SOCScenarioInfo;
import soc.message.SOCServerPing;
import soc.message.SOCSetSeatLock;
import soc.message.SOCSitDown;
import soc.message.SOCStartGame;
import soc.message.SOCTextMsg;
import soc.message.SOCVersion;
import soc.server.genericServer.StringConnection;

/**
 * Server class to dispatch clients' actions and messages received from the
 * {@link soc.server.genericServer.InboundMessageQueue} not related to game play
 * in specific current games and not handled by {@link SOCGameMessageHandler}.
 *<P>
 * Before v2.0.00, these methods and fields were part of {@link SOCServer}:
 * processCommand(String, StringConnection) and related methods
 * So, some may have {@code @since} javadoc labels with versions older than 2.0.00.
 * Game message handler for the {@link SOCGameHandler} game type.
 *
 * @see SOCGameMessageHandler
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCServerMessageHandler
{
    final SOCServer srv;

    public SOCServerMessageHandler(SOCServer srv)
    {
        this.srv = srv;
    }

    /**
     * Process any inbound message which isn't handled by {@link SOCGameMessageHandler}:
     * Coming from a client, not for a specific game.
     *<P>
     * This method is called from {@link SOCMessageDispatcher#dispatch(String, StringConnection)}.
     * Caller of this method will catch any thrown Exceptions.
     *<P>
     *<B>Note:</B> When there is a choice, always use local information
     *       over information from the message.  For example, use
     *       the nickname from the connection to get the player
     *       information rather than the player information from
     *       the message.  This makes it harder to send false
     *       messages making players do things they didn't want
     *       to do.
     * @param mes  Message from {@code c}. Never {@code null}.
     * @param c    Connection (client) sending this message. Never null.
     * @throws NullPointerException  if {@code mes} is {@code null}
     * @throws Exception  Caller must catch any exceptions thrown because of
     *    conditions or bugs in any server methods called from here.
     */
    final void dispatch(final SOCMessage mes, final StringConnection c)
        throws NullPointerException, Exception
    {
        switch (mes.getType())
        {

        /**
         * client's echo of a server ping
         */
        case SOCMessage.SERVERPING:
            srv.handleSERVERPING(c, (SOCServerPing) mes);
            break;

        /**
         * client's "version" message
         */
        case SOCMessage.VERSION:
            srv.handleVERSION(c, (SOCVersion) mes);
            break;

        /**
         * client's optional authentication request before creating a game
         * or when connecting using {@code SOCAccountClient} (v1.1.19+).
         */
        case SOCMessage.AUTHREQUEST:
            srv.handleAUTHREQUEST(c, (SOCAuthRequest) mes);
            break;

        /**
         * "join a channel" message
         */
        case SOCMessage.JOIN:
            srv.handleJOIN(c, (SOCJoin) mes);
            break;

        /**
         * "leave a channel" message
         */
        case SOCMessage.LEAVE:
            srv.handleLEAVE(c, (SOCLeave) mes);
            break;

        /**
         * "leave all channels" message
         */
        case SOCMessage.LEAVEALL:
            srv.removeConnection(c, true);
            break;

        /**
         * text message
         */
        case SOCMessage.TEXTMSG:

            final SOCTextMsg textMsgMes = (SOCTextMsg) mes;

            if (srv.isDebugUserEnabled() && c.getData().equals("debug"))
            {
                if (textMsgMes.getText().startsWith("*KILLCHANNEL*"))
                {
                    srv.messageToChannel(textMsgMes.getChannel(), new SOCTextMsg
                        (textMsgMes.getChannel(), SOCServer.SERVERNAME,
                         "********** " + (String) c.getData() + " KILLED THE CHANNEL **********"));
                    srv.channelList.takeMonitor();

                    try
                    {
                        srv.destroyChannel(textMsgMes.getChannel());
                    }
                    catch (Exception e)
                    {
                        D.ebugPrintStackTrace(e, "Exception in KILLCHANNEL");
                    }

                    srv.channelList.releaseMonitor();
                    srv.broadcast(SOCDeleteChannel.toCmd(textMsgMes.getChannel()));
                }
                else
                {
                    /**
                     * Send the message to the members of the channel
                     */
                    srv.messageToChannel(textMsgMes.getChannel(), mes);
                }
            }
            else
            {
                /**
                 * Send the message to the members of the channel
                 */
                srv.messageToChannel(textMsgMes.getChannel(), mes);
            }

            break;

        /**
         * a robot has connected to this server
         */
        case SOCMessage.IMAROBOT:
            srv.handleIMAROBOT(c, (SOCImARobot) mes);
            break;

        /**
         * text message from a game (includes debug commands)
         */
        case SOCMessage.GAMETEXTMSG:
            srv.handleGAMETEXTMSG(c, (SOCGameTextMsg) mes);
            break;

        /**
         * "join a game" message
         */
        case SOCMessage.JOINGAME:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            srv.handleJOINGAME(c, (SOCJoinGame) mes);

            //ga = (SOCGame)gamesData.get(((SOCJoinGame)mes).getGame());
            //if (ga != null) {
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCJoinGame)mes).getGame());
            //}
            break;

        /**
         * "leave a game" message
         */
        case SOCMessage.LEAVEGAME:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            srv.handleLEAVEGAME(c, (SOCLeaveGame) mes);

            //ga = (SOCGame)gamesData.get(((SOCLeaveGame)mes).getGame());
            //if (ga != null) {
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCLeaveGame)mes).getGame());
            //}
            break;

        /**
         * someone wants to sit down
         */
        case SOCMessage.SITDOWN:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            srv.handleSITDOWN(c, (SOCSitDown) mes);

            //ga = (SOCGame)gamesData.get(((SOCSitDown)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCSitDown)mes).getGame());
            break;

        /**
         * someone is starting a game
         */
        case SOCMessage.STARTGAME:

            //createNewGameEventRecord();
            //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
            srv.handleSTARTGAME(c, (SOCStartGame) mes, 0);

            //ga = (SOCGame)gamesData.get(((SOCStartGame)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCStartGame)mes).getGame());
            break;

        case SOCMessage.CHANGEFACE:
            srv.handleCHANGEFACE(c, (SOCChangeFace) mes);
            break;

        case SOCMessage.SETSEATLOCK:
            srv.handleSETSEATLOCK(c, (SOCSetSeatLock) mes);
            break;

        case SOCMessage.RESETBOARDREQUEST:
            srv.handleRESETBOARDREQUEST(c, (SOCResetBoardRequest) mes);
            break;

        case SOCMessage.RESETBOARDVOTE:
            srv.handleRESETBOARDVOTE(c, (SOCResetBoardVote) mes);
            break;

        case SOCMessage.CREATEACCOUNT:
            srv.handleCREATEACCOUNT(c, (SOCCreateAccount) mes);
            break;

        /**
         * Handle client request for localized i18n strings for game items.
         * Added 2015-01-14 for v2.0.00.
         */
        case SOCMessage.LOCALIZEDSTRINGS:
            srv.handleLOCALIZEDSTRINGS(c, (SOCLocalizedStrings) mes);
            break;

        /**
         * Game option messages. For the best writeup of these messages' interaction with
         * the client, see {@link soc.client.SOCPlayerClient.GameOptionServerSet}'s javadoc.
         * Added 2009-06-01 for v1.1.07.
         */

        case SOCMessage.GAMEOPTIONGETDEFAULTS:
            srv.handleGAMEOPTIONGETDEFAULTS(c, (SOCGameOptionGetDefaults) mes);
            break;

        case SOCMessage.GAMEOPTIONGETINFOS:
            srv.handleGAMEOPTIONGETINFOS(c, (SOCGameOptionGetInfos) mes);
            break;

        case SOCMessage.NEWGAMEWITHOPTIONSREQUEST:
            srv.handleNEWGAMEWITHOPTIONSREQUEST(c, (SOCNewGameWithOptionsRequest) mes);
            break;

        /**
         * Client request for updated scenario info.
         * Added 2015-09-21 for v2.0.00.
         */
        case SOCMessage.SCENARIOINFO:
            srv.handleSCENARIOINFO(c, (SOCScenarioInfo) mes);
            break;

        }  // switch (mes.getType)
    }

}
