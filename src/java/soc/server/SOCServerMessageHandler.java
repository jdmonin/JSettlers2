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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
import soc.message.SOCJoinAuth;
import soc.message.SOCJoinGame;
import soc.message.SOCLeave;
import soc.message.SOCLeaveGame;
import soc.message.SOCLocalizedStrings;
import soc.message.SOCMembers;
import soc.message.SOCMessage;
import soc.message.SOCNewChannel;
import soc.message.SOCNewGameWithOptionsRequest;
import soc.message.SOCResetBoardRequest;
import soc.message.SOCResetBoardVote;
import soc.message.SOCScenarioInfo;
import soc.message.SOCServerPing;
import soc.message.SOCSetSeatLock;
import soc.message.SOCSitDown;
import soc.message.SOCStartGame;
import soc.message.SOCStatusMessage;
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
    private final SOCServer srv;

    /**
     * List of {@link #srv}'s games.
     */
    private final SOCGameListAtServer gameList;

    /**
     * List of {@link #srv}'s chat channels.
     */
    private final SOCChannelList channelList;

    public SOCServerMessageHandler
        (SOCServer srv, final SOCGameListAtServer gameList, final SOCChannelList channelList)
    {
        this.srv = srv;
        this.gameList = gameList;
        this.channelList = channelList;
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
            handleSERVERPING(c, (SOCServerPing) mes);
            break;

        /**
         * client's "version" message
         */
        case SOCMessage.VERSION:
            handleVERSION(c, (SOCVersion) mes);
            break;

        /**
         * client's optional authentication request before creating a game
         * or when connecting using {@code SOCAccountClient} (v1.1.19+).
         */
        case SOCMessage.AUTHREQUEST:
            handleAUTHREQUEST(c, (SOCAuthRequest) mes);
            break;

        /**
         * "join a channel" message
         */
        case SOCMessage.JOIN:
            handleJOIN(c, (SOCJoin) mes);
            break;

        /**
         * "leave a channel" message
         */
        case SOCMessage.LEAVE:
            handleLEAVE(c, (SOCLeave) mes);
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
                    channelList.takeMonitor();

                    try
                    {
                        srv.destroyChannel(textMsgMes.getChannel());
                    }
                    catch (Exception e)
                    {
                        D.ebugPrintStackTrace(e, "Exception in KILLCHANNEL");
                    }

                    channelList.releaseMonitor();
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
            handleJOINGAME(c, (SOCJoinGame) mes);

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
            handleLOCALIZEDSTRINGS(c, (SOCLocalizedStrings) mes);
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


    /// Accepting connections and authentication ///


    /**
     * Handle the "version" message, client's version report.
     * May ask to disconnect, if version is too old.
     * Otherwise send the game list.
     * If we've already sent the game list, send changes based on true version.
     * If they send another VERSION later, with a different version, disconnect the client.
     *<P>
     * Along with the game list, the client will need to know the game option info.
     * This is sent when the client asks (after VERSION) for {@link SOCGameOptionGetInfos GAMEOPTIONGETINFOS}.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.1.00
     */
    void handleVERSION(StringConnection c, SOCVersion mes)
    {
        if (c == null)
            return;

        srv.setClientVersSendGamesOrReject(c, mes.getVersionNumber(), mes.localeOrFeats, true);
    }

    /**
     * Handle the optional {@link SOCAuthRequest "authentication request"} message.
     * Sent by clients since v1.1.19 before creating a game or when connecting using {@code SOCAccountClient}.
     *<P>
     * If {@link StringConnection#getData() c.getData()} != {@code null}, the client already authenticated and
     * this method replies with {@link SOCStatusMessage#SV_OK} without checking the password in this message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @see #isUserDBUserAdmin(String, boolean)
     * @since 1.1.19
     */
    void handleAUTHREQUEST(StringConnection c, final SOCAuthRequest mes)
    {
        if (c == null)
            return;

        if (c.getData() == null)
        {
            final int cliVersion = c.getVersion();
            if (cliVersion <= 0)
            {
                // unlikely: AUTHREQUEST was added in 1.1.19, version message timing was stable years earlier
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NOT_OK_GENERIC, "AUTHREQUEST: Send version first"));  // I18N OK: rare error
                return;
            }

            if (mes.authScheme != SOCAuthRequest.SCHEME_CLIENT_PLAINTEXT)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NOT_OK_GENERIC, "AUTHREQUEST: Auth scheme unknown: " + mes.authScheme));
                        // I18N OK: rare error
                return;
            }

            // Check user authentication.  Don't call setData or nameConnection yet, in case
            // of role-specific things to check and reject during this initial connection.
            final String mesUser = mes.nickname.trim();  // trim here because we'll send it in messages to clients
            final int authResult = srv.authOrRejectClientUser(c, mesUser, mes.password, cliVersion, false, false);

            if (authResult == SOCServer.AUTH_OR_REJECT__FAILED)
                return;  // <---- Early return; authOrRejectClientUser sent the status message ----

            if (mes.role.equals(SOCAuthRequest.ROLE_USER_ADMIN))
            {
                // Check if we're using a user admin whitelist
                if (! srv.isUserDBUserAdmin(mesUser, false))
                {
                    c.put(SOCStatusMessage.toCmd
                            (SOCStatusMessage.SV_ACCT_NOT_CREATED_DENIED, cliVersion,
                             c.getLocalized("account.create.not_auth")));
                                // "Your account is not authorized to create accounts."

                    srv.printAuditMessage
                        (mesUser,
                         "Requested jsettlers account creation, this requester not on account admin whitelist",
                         null, null, c.host());

                    return;
                }
            }

            // no role-specific problems: complete the authentication
            c.setData(mesUser);
            srv.nameConnection(c, false);
        }

        c.put(SOCStatusMessage.toCmd
                (SOCStatusMessage.SV_OK, c.getLocalized("member.welcome")));  // "Welcome to Java Settlers of Catan!"
    }


    /// Communications with authenticated clients ///


    /**
     * Handle the client's echo of a {@link SOCMessage#SERVERPING}.
     * Resets its {@link SOCClientData#disconnectLastPingMillis} to 0
     * to indicate client is actively responsive to server.
     * @since 1.1.08
     */
    void handleSERVERPING(StringConnection c, SOCServerPing mes)
    {
        SOCClientData cd = (SOCClientData) c.getAppData();
        if (cd == null)
            return;
        cd.disconnectLastPingMillis = 0;

        // TODO any other reaction or flags?
    }

    /**
     * Handle client request for localized i18n strings for game items.
     * Added 2015-01-14 for v2.0.00.
     */
    void handleLOCALIZEDSTRINGS(final StringConnection c, final SOCLocalizedStrings mes)
    {
        final List<String> str = mes.getParams();
        final String type = str.get(0);
        List<String> rets = null;  // for reply to client; built in localizeGameScenarios or other type-specific method
        int flags = 0;

        if (type.equals(SOCLocalizedStrings.TYPE_GAMEOPT))
        {
            // Already handled when client connects
            flags = SOCLocalizedStrings.FLAG_SENT_ALL;
        }
        else if (type.equals(SOCLocalizedStrings.TYPE_SCENARIO))
        {
            // Handle individual scenario keys; ignores FLAG_REQ_ALL

            final SOCClientData scd = (SOCClientData) c.getAppData();
            if (SOCServer.clientHasLocalizedStrs_gameScenarios(c))
            {
                rets = SOCServer.localizeGameScenarios(scd.locale, str, true, scd);
            } else {
                flags = SOCLocalizedStrings.FLAG_SENT_ALL;
                scd.sentAllScenarioStrings = true;
            }
        }
        else
        {
            // Unrecognized string type
            flags = SOCLocalizedStrings.FLAG_TYPE_UNKNOWN;
        }

        if (rets == null)
            rets = new ArrayList<String>();
        c.put(SOCLocalizedStrings.toCmd(type, flags, rets));
    }


    /// Channel lifecycle ///


    /**
     * Handle the "join a channel" message.
     * If client hasn't yet sent its version, assume is
     * version 1.0.00 ({@link #CLI_VERSION_ASSUMED_GUESS}), disconnect if too low.
     *<P>
     * Requested channel name must pass {@link SOCMessage#isSingleLineAndSafe(String)}.
     * Channel name {@code "*"} is also rejected to avoid conflicts with admin commands.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    void handleJOIN(StringConnection c, SOCJoin mes)
    {
        if (c == null)
            return;

        D.ebugPrintln("handleJOIN: " + mes);

        int cliVers = c.getVersion();
        final String msgUser = mes.getNickname().trim();  // trim here because we'll send it in messages to clients
        String msgPass = mes.getPassword();

        /**
         * Check the reported version; if none, assume 1000 (1.0.00)
         */
        if (cliVers == -1)
        {
            if (! srv.setClientVersSendGamesOrReject(c, SOCServer.CLI_VERSION_ASSUMED_GUESS, null, false))
                return;  // <--- Discon and Early return: Client too old ---

            cliVers = c.getVersion();
        }

        /**
         * Check that the nickname is ok, check password if supplied; if not ok, sends a SOCStatusMessage.
         */
        final int authResult = srv.authOrRejectClientUser(c, msgUser, msgPass, cliVers, true, false);
        if (authResult == SOCServer.AUTH_OR_REJECT__FAILED)
            return;  // <---- Early return ----

        /**
         * Check that the channel name is ok
         */

        /*
           if (!checkChannelName(mes.getChannel())) {
           return;
           }
         */
        final String ch = mes.getChannel().trim();
        if ( (! SOCMessage.isSingleLineAndSafe(ch))
             || "*".equals(ch))
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWGAME_NAME_REJECTED, cliVers,
                     SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED));
              // "This game name is not permitted, please choose a different name."

              return;  // <---- Early return ----
        }

        /**
         * If creating a new channel, ensure they are below their max channel count.
         */
        if ((! channelList.isChannel(ch))
            && (SOCServer.CLIENT_MAX_CREATE_CHANNELS >= 0)
            && (SOCServer.CLIENT_MAX_CREATE_CHANNELS <= ((SOCClientData) c.getAppData()).getcurrentCreatedChannels()))
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWCHANNEL_TOO_MANY_CREATED, cliVers,
                     SOCStatusMessage.MSG_SV_NEWCHANNEL_TOO_MANY_CREATED
                     + Integer.toString(SOCServer.CLIENT_MAX_CREATE_CHANNELS)));
            // Too many of your chat channels still active; maximum: 2

            return;  // <---- Early return ----
        }

        /**
         * Tell the client that everything is good to go
         */
        c.put(SOCJoinAuth.toCmd(msgUser, ch));
        c.put(SOCStatusMessage.toCmd
                (SOCStatusMessage.SV_OK, c.getLocalized("member.welcome")));  // "Welcome to Java Settlers of Catan!"

        /**
         * Add the StringConnection to the channel
         */

        if (channelList.takeMonitorForChannel(ch))
        {
            try
            {
                srv.connectToChannel(c, ch);
            }
            catch (Exception e)
            {
                D.ebugPrintStackTrace(e, "Exception in handleJOIN (connectToChannel)");
            }

            channelList.releaseMonitorForChannel(ch);
        }
        else
        {
            /**
             * the channel did not exist, create it
             */
            channelList.takeMonitor();

            try
            {
                channelList.createChannel(ch, (String) c.getData());
                ((SOCClientData) c.getAppData()).createdChannel();
            }
            catch (Exception e)
            {
                D.ebugPrintStackTrace(e, "Exception in handleJOIN (createChannel)");
            }

            channelList.releaseMonitor();
            srv.broadcast(SOCNewChannel.toCmd(ch));
            c.put(SOCMembers.toCmd(ch, channelList.getMembers(ch)));
            if (D.ebugOn)
                D.ebugPrintln("*** " + c.getData() + " joined the channel " + ch + " at "
                    + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
            channelList.takeMonitorForChannel(ch);

            try
            {
                channelList.addMember(c, ch);
            }
            catch (Exception e)
            {
                D.ebugPrintStackTrace(e, "Exception in handleJOIN (addMember)");
            }

            channelList.releaseMonitorForChannel(ch);
        }

        /**
         * let everyone know about the change
         */
        srv.messageToChannel(ch, new SOCJoin(msgUser, "", "dummyhost", ch));
    }

    /**
     * Handle the "leave a channel" message
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    void handleLEAVE(StringConnection c, SOCLeave mes)
    {
        D.ebugPrintln("handleLEAVE: " + mes);

        if (c == null)
            return;

        boolean destroyedChannel = false;
        channelList.takeMonitorForChannel(mes.getChannel());

        try
        {
            destroyedChannel = srv.leaveChannel(c, mes.getChannel(), true, false);
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in handleLEAVE");
        }

        channelList.releaseMonitorForChannel(mes.getChannel());

        if (destroyedChannel)
        {
            srv.broadcast(SOCDeleteChannel.toCmd(mes.getChannel()));
        }
    }


    /// Game lifecycle ///


    /**
     * Handle the "join a game" message: Join or create a game.
     * Will join the game, or return a STATUSMESSAGE if nickname is not OK.
     * Clients can join game as an observer, if they don't SITDOWN after joining.
     *<P>
     * If client hasn't yet sent its version, assume is version 1.0.00
     * ({@link SOCServer#CLI_VERSION_ASSUMED_GUESS CLI_VERSION_ASSUMED_GUESS}), disconnect if too low.
     * If the client is too old to join a specific game, return a STATUSMESSAGE (since 1.1.06).
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    void handleJOINGAME(StringConnection c, SOCJoinGame mes)
    {
        if (c == null)
            return;

        D.ebugPrintln("handleJOINGAME: " + mes);

        /**
         * Check the client's reported version; if none, assume 1000 (1.0.00)
         */
        if (c.getVersion() == -1)
        {
            if (! srv.setClientVersSendGamesOrReject(c, SOCServer.CLI_VERSION_ASSUMED_GUESS, null, false))
                return;  // <--- Early return: Client too old ---
        }

        srv.createOrJoinGameIfUserOK
            (c, mes.getNickname(), mes.getPassword(), mes.getGame(), null);
    }

}
