/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2016-2021 Jeremy D Monin <jeremy@nand.net>
 * Some contents were formerly part of SOCServer.java;
 * Portions of this file Copyright (C) 2003 Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2016 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
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

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.TimerTask;
import java.util.Vector;
import java.util.regex.Pattern;

import soc.debug.D;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCGameOptionVersionException;
import soc.game.SOCPlayer;
import soc.game.SOCScenario;
import soc.game.SOCVersionedItem;
import soc.message.*;
import soc.server.database.SOCDBHelper;
import soc.server.genericServer.Connection;
import soc.server.genericServer.StringConnection;
import soc.server.savegame.*;
import soc.util.I18n;
import soc.util.SOCFeatureSet;
import soc.util.SOCGameBoardReset;
import soc.util.SOCGameList;
import soc.util.SOCStringManager;
import soc.util.Version;

/**
 * Server class to dispatch clients' actions and messages received from the
 * {@link soc.server.genericServer.InboundMessageQueue} not related to game play
 * in specific current games and not handled by {@link SOCGameMessageHandler}.
 * This also includes some messages related to game lifecycle like
 * {@link SOCJoinGame} and {@link SOCSitDown}.
 *<P>
 * Before v2.0.00, these methods and fields were part of {@link SOCServer}
 * {@code .processCommand(String, Connection)} and related methods.
 * So, some may have {@code @since} javadoc labels with versions older than 2.0.00.
 *
 * @see SOCGameMessageHandler
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCServerMessageHandler
{
    /**
     * Filename regex for {@code *LOADGAME*} and {@code *SAVEGAME*} debug commands:
     * Allows letters and digits ({@link Character#isLetterOrDigit(int)})
     * along with dashes (-) and underscores (_).
     * @since 2.3.00
     */
    protected static final Pattern DEBUG_COMMAND_SAVEGAME_FILENAME_REGEX
        = Pattern.compile("^[\\p{IsLetter}\\p{IsDigit}_-]+$");

    protected final SOCServer srv;

    /**
     * List of {@link #srv}'s games.
     */
    protected final SOCGameListAtServer gameList;

    /**
     * List of {@link #srv}'s chat channels.
     */
    protected final SOCChannelList channelList;

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
     * This method is called from {@link SOCMessageDispatcher#dispatch(SOCMessage, Connection)}.
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
     *     {@link Connection#getData()} won't be {@code null}
     *     unless {@code mes} implements {@link SOCMessageFromUnauthClient}.
     * @throws NullPointerException  if {@code mes} is {@code null}
     * @throws Exception  Caller must catch any exceptions thrown because of
     *     conditions or bugs in any server methods called from here.
     */
    final void dispatch(final SOCMessage mes, final Connection c)
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
         * "join a chat channel" message
         */
        case SOCMessage.JOINCHANNEL:
            handleJOINCHANNEL(c, (SOCJoinChannel) mes);
            break;

        /**
         * "leave a chat channel" message
         */
        case SOCMessage.LEAVECHANNEL:
            handleLEAVECHANNEL(c, (SOCLeaveChannel) mes);
            break;

        /**
         * "leave all games and chat channels" message (SOCLeaveAll)
         */
        case SOCMessage.LEAVEALL:
            srv.removeConnection(c, true);
            break;

        /**
         * text message to a channel (includes channel debug commands)
         */
        case SOCMessage.CHANNELTEXTMSG:
            handleCHANNELTEXTMSG(c, (SOCChannelTextMsg) mes);
            break;

        /**
         * a robot has connected to this server
         */
        case SOCMessage.IMAROBOT:
            handleIMAROBOT(c, (SOCImARobot) mes);
            break;

        /**
         * text message from a game (includes debug commands)
         */
        case SOCMessage.GAMETEXTMSG:
            handleGAMETEXTMSG(c, (SOCGameTextMsg) mes);
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
            handleLEAVEGAME(c, (SOCLeaveGame) mes);

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
            handleSITDOWN(c, (SOCSitDown) mes);

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
            handleSTARTGAME(c, (SOCStartGame) mes, 0);

            //ga = (SOCGame)gamesData.get(((SOCStartGame)mes).getGame());
            //currentGameEventRecord.setSnapshot(ga);
            //saveCurrentGameEventRecord(((SOCStartGame)mes).getGame());
            break;

        case SOCMessage.CHANGEFACE:
            handleCHANGEFACE(c, (SOCChangeFace) mes);
            break;

        case SOCMessage.SETSEATLOCK:
            handleSETSEATLOCK(c, (SOCSetSeatLock) mes);
            break;

        case SOCMessage.RESETBOARDREQUEST:
            handleRESETBOARDREQUEST(c, (SOCResetBoardRequest) mes);
            break;

        case SOCMessage.RESETBOARDVOTE:
            handleRESETBOARDVOTE(c, (SOCResetBoardVote) mes);
            break;

        case SOCMessage.CREATEACCOUNT:
            {
                final SOCCreateAccount m = (SOCCreateAccount) mes;
                srv.createAccount(m.getNickname(), m.getPassword(), m.getEmail(), c);
            }
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
         * the client, see {@link soc.client.ServerGametypeInfo}'s javadoc.
         * Added 2009-06-01 for v1.1.07.
         */

        case SOCMessage.GAMEOPTIONGETDEFAULTS:
            handleGAMEOPTIONGETDEFAULTS(c, (SOCGameOptionGetDefaults) mes);
            break;

        case SOCMessage.GAMEOPTIONGETINFOS:
            handleGAMEOPTIONGETINFOS(c, (SOCGameOptionGetInfos) mes);
            break;

        case SOCMessage.NEWGAMEWITHOPTIONSREQUEST:
            handleNEWGAMEWITHOPTIONSREQUEST(c, (SOCNewGameWithOptionsRequest) mes);
            break;

        /**
         * Client request for updated scenario info.
         * Added 2015-09-21 for v2.0.00.
         */
        case SOCMessage.SCENARIOINFO:
            handleSCENARIOINFO(c, (SOCScenarioInfo) mes);
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
    void handleVERSION(Connection c, SOCVersion mes)
    {
        if (c == null)
            return;

        srv.setClientVersSendGamesOrReject(c, mes.getVersionNumber(), mes.feats, mes.cliLocale, true);
    }

    /**
     * Handle the optional {@link SOCAuthRequest "authentication request"} message.
     * Sent by clients since v1.1.19 before creating a game or when connecting using {@code SOCAccountClient}.
     *<P>
     * If {@link Connection#getData() c.getData()} != {@code null}, the client already authenticated and
     * this method replies with {@link SOCStatusMessage#SV_OK} without checking the password in this message.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @see SOCServer#isUserDBUserAdmin(String)
     * @since 1.1.19
     */
    private void handleAUTHREQUEST(Connection c, final SOCAuthRequest mes)
    {
        if (c == null)
            return;

        final String mesUser = mes.nickname.trim();  // trim before db query calls
        final String mesRole = mes.role;
        final boolean isPlayerRole = mesRole.equals(SOCAuthRequest.ROLE_GAME_PLAYER);
        final int cliVersion = c.getVersion();

        if (c.getData() != null)
        {
            handleAUTHREQUEST_postAuth(c, mesUser, mesRole, isPlayerRole, cliVersion, SOCServer.AUTH_OR_REJECT__OK);
        } else {
            if (cliVersion <= 0)
            {
                // unlikely: AUTHREQUEST was added in 1.1.19, version message timing was stable years earlier
                c.put(new SOCStatusMessage
                        (SOCStatusMessage.SV_NOT_OK_GENERIC, "AUTHREQUEST: Send version first"));  // I18N OK: rare error
                return;
            }

            if (mes.authScheme != SOCAuthRequest.SCHEME_CLIENT_PLAINTEXT)
            {
                c.put(new SOCStatusMessage
                        (SOCStatusMessage.SV_NOT_OK_GENERIC, "AUTHREQUEST: Auth scheme unknown: " + mes.authScheme));
                        // I18N OK: rare error
                return;
            }

            // Check user authentication.  Don't call setData or nameConnection yet if there
            // are role-specific things to check and reject during this initial connection.
            srv.authOrRejectClientUser
                (c, mesUser, mes.password, cliVersion, isPlayerRole, false,
                 new SOCServer.AuthSuccessRunnable()
                 {
                    public void success(final Connection conn, final int authResult)
                    {
                        handleAUTHREQUEST_postAuth(conn, mesUser, mesRole, isPlayerRole, cliVersion, authResult);
                    }
                 });
        }
    }

    /**
     * After successful client user auth, take care of the rest of
     * {@link #handleAUTHREQUEST(Connection, SOCAuthRequest)}.
     * @since 1.2.00
     */
    private void handleAUTHREQUEST_postAuth
        (final Connection c, final String mesUser, final String mesRole, final boolean isPlayerRole,
         final int cliVersion, int authResult)
    {
        if (c.getData() == null)
        {
            if (! isPlayerRole)
            {
                if (mesRole.equals(SOCAuthRequest.ROLE_USER_ADMIN))
                {
                    if (! srv.isUserDBUserAdmin(mesUser))
                    {
                        c.put(SOCStatusMessage.buildForVersion
                                (SOCStatusMessage.SV_ACCT_NOT_CREATED_DENIED, cliVersion,
                                 c.getLocalized("account.create.not_auth")));
                                    // "Your account is not authorized to create accounts."

                        srv.printAuditMessage
                            (mesUser,
                             "Requested jsettlers account creation, this requester not on account admins list",
                             null, null, c.host());

                        return;
                    }
                }

                // no role-specific problems: complete the authentication
                try
                {
                    c.setData(srv.db.getUser(mesUser));  // case-insensitive db search on mesUser
                    srv.nameConnection(c, false);
                } catch (SQLException e) {
                    // unlikely, we've just queried db in authOrRejectClientUser
                    c.put(SOCStatusMessage.buildForVersion
                            (SOCStatusMessage.SV_PROBLEM_WITH_DB, c.getVersion(),
                            "Problem connecting to database, please try again later."));
                    return;
                }
            }
            // else isPlayerRole: auth/connection.setData done by handleAUTHREQUEST's call to authOrRejectClientUser
        }

        final String txt = srv.getClientWelcomeMessage(c);  // "Welcome to Java Settlers of Catan!"
        if (0 == (authResult & SOCServer.AUTH_OR_REJECT__SET_USERNAME))
            c.put(new SOCStatusMessage
                (SOCStatusMessage.SV_OK, txt));
        else
            c.put(new SOCStatusMessage
                (SOCStatusMessage.SV_OK_SET_NICKNAME, c.getData() + SOCMessage.sep2_char + txt));

        final SOCClientData scd = (SOCClientData) c.getAppData();
        if (scd != null)  // very unlikely to be null; checks here anyway to be extra-careful during auth
            scd.sentPostAuthWelcome = true;
    }

    /**
     * Handle the "I'm a robot" message.
     * Robots send their {@link SOCVersion} before sending this message.
     * Their version is checked here, must equal server's version.
     * For stability and control, the cookie in this message must
     * match this server's {@link SOCServer#robotCookie}.
     * Otherwise the bot is rejected and they're disconnected by calling
     * {@link SOCServer#removeConnection(Connection, boolean)}.
     *<P>
     * Bot tuning parameters are sent here to the bot, from
     * {@link SOCServer#getRobotParameters(String)}.
     * See that method for default bot params.
     * See {@link SOCServer#authOrRejectClientRobot(Connection, String, String, String)}
     * for {@link SOCClientData} flags and fields set for the bot's connection
     * and for other misc work done, such as {@link Server#cliConnDisconPrintsPending} updates.
     *<P>
     * The server's built-in bots are named and started in {@link SOCServer#setupLocalRobots(int, int)},
     * then must authenticate with this message just like external or third-party bots.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleIMAROBOT(final Connection c, final SOCImARobot mes)
    {
        if (c == null)
            return;

        final String botName = mes.getNickname();
        final String rejectReason = srv.authOrRejectClientRobot
            (c, botName, mes.getCookie(), mes.getRBClass());

        if (rejectReason != null)
        {
            if (rejectReason.equals(SOCServer.MSG_NICKNAME_ALREADY_IN_USE))
                c.put(SOCStatusMessage.buildForVersion
                        (SOCStatusMessage.SV_NAME_IN_USE, c.getVersion(), rejectReason));
            c.put(new SOCRejectConnection(rejectReason));
            c.disconnectSoft();

            // make an effort to send reject message before closing socket
            final Connection rc = c;
            srv.miscTaskTimer.schedule(new TimerTask()
            {
                public void run()
                {
                    srv.removeConnection(rc, true);
                }
            }, 300);

            return;  // <--- Early return: rejected ---
        }

        //
        // send the current robot parameters
        //
        c.put(new SOCUpdateRobotParams(srv.getRobotParameters(botName)));
    }


    /// Communications with authenticated clients ///


    /**
     * Handle the client's echo of a {@link SOCMessage#SERVERPING}.
     * Resets its {@link SOCClientData#disconnectLastPingMillis} to 0
     * to indicate client is actively responsive to server.
     * @since 1.1.08
     */
    private void handleSERVERPING(Connection c, SOCServerPing mes)
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
    private void handleLOCALIZEDSTRINGS(final Connection c, final SOCLocalizedStrings mes)
    {
        final List<String> strs = mes.getParams();
        final String type = strs.get(0);
        List<String> rets = null;  // for reply to client; built in localizeGameScenarios or other type-specific method
        int flags = 0;

        if (type.equals(SOCLocalizedStrings.TYPE_GAMEOPT))
        {
            // Already handled when client connects
            // and sends GAMEOPTIONGETINFOS
            flags = SOCLocalizedStrings.FLAG_SENT_ALL;
        }
        else if (type.equals(SOCLocalizedStrings.TYPE_SCENARIO))
        {
            // Handle individual scenario keys for any client version,
            // or FLAG_REQ_ALL from same version as server.
            // (If client version != server version, set of all scenarios' localized strings
            //  are instead sent in response to client's SOCScenarioInfo message.)

            final SOCClientData scd = (SOCClientData) c.getAppData();
            if (scd.localeHasGameScenarios(c))
            {
                boolean wantsAll = mes.isFlagSet(SOCLocalizedStrings.FLAG_REQ_ALL) || (strs.size() == 1);
                    // if list is empty after first element (string type), is requesting all
                if (wantsAll)
                {
                    flags = SOCLocalizedStrings.FLAG_SENT_ALL;
                    scd.sentAllScenarioStrings = true;
                }
                rets = SOCServer.localizeGameScenarios(scd.locale, strs, wantsAll, true, scd);
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

        c.put(new SOCLocalizedStrings(type, flags, rets));  // null "rets" is ok
    }

    /**
     * process the "game option get defaults" message.
     * User has clicked the "New Game" button for the first time, client needs {@link SOCGameOption} values.
     * Responds to client by sending {@link SOCGameOptionGetDefaults GAMEOPTIONGETDEFAULTS}.
     * All of server's Known Options are sent, except empty string-valued options.
     * Depending on client version, server's response may include option names that
     * the client is too old to use; the client is able to ignore them.
     * If the client is older than {@link SOCGameOption#VERSION_FOR_LONGER_OPTNAMES},
     * options with long names won't be sent.
     *<P>
     * <B>I18N:</B> Since the New Game dialog will need localized strings for {@link SOCScenario}s,
     * v2.0.00 sends those strings before the game option default values, so the client will have them
     * before showing the dialog.
     *
     * @param c  the connection
     * @param mes  the message
     * @since 1.1.07
     */
    private void handleGAMEOPTIONGETDEFAULTS(Connection c, SOCGameOptionGetDefaults mes)
    {
        if (c == null)
            return;

        final boolean hideLongNameOpts = (c.getVersion() < SOCGameOption.VERSION_FOR_LONGER_OPTNAMES);
        c.put(new SOCGameOptionGetDefaults
              (SOCGameOption.packKnownOptionsToString(srv.knownOpts, true, hideLongNameOpts)));
    }

    /**
     * process the "game option get infos" message; reply with the info, with
     * one {@link SOCGameOptionInfo GAMEOPTIONINFO} message per option keyname.
     * If client version >= 2.0.00, send any unchanged but localized options using
     * {@link SOCLocalizedStrings}({@link SOCLocalizedStrings#TYPE_GAMEOPT TYPE_GAMEOPT}).
     * Mark the end of the option list with {@link SOCGameOptionInfo GAMEOPTIONINFO}("-").
     * If this list is empty, "-" will be the only GAMEOPTIONGETINFO message sent.
     *<P>
     * We check the default values, not current values, so the list is unaffected by
     * cases where some option values are restricted to newer client versions.
     * Any option where opt.{@link SOCGameOption#minVersion minVersion} is too new for
     * this client's version, is sent as {@link SOCGameOption#OTYPE_UNKNOWN}.
     * If the client is older than {@link SOCGameOption#VERSION_FOR_LONGER_OPTNAMES},
     * options with long names won't be sent.
     *<P>
     * If {@link SOCClientData#hasLimitedFeats c.hasLimitedFeats} and any known options require client features
     * not supported by client {@code c}, they'll be sent to the client as {@link SOCGameOption#OTYPE_UNKNOWN}
     * even if not asked for. Also sends info for any game options whose value range is limited by client features.
     *<P>
     * If any third-party options are active ({@link SOCGameOption#FLAG_3RD_PARTY}), always checks client features
     * for compatibility with those 3P options.
     *
     * @param c  the connection
     * @param mes  the message
     * @since 1.1.07
     */
    private void handleGAMEOPTIONGETINFOS(Connection c, SOCGameOptionGetInfos mes)
    {
        if (c == null)
            return;

        final int cliVers = c.getVersion();
        final SOCClientData scd = (SOCClientData) c.getAppData();
        final boolean hasLimitedFeats = scd.hasLimitedFeats;

        boolean alreadyTrimmedEnums = false;
        Map<String, SOCGameOption> opts = new HashMap<>();  // opts to send as SOCGameOptionInfo
        final Map<String, SOCGameOption> optsToLocal;  // opts to send in a SOCLocalizedStrings instead

        // check for request for i18n localized descriptions (client v2.0.00 or newer);
        // if we don't have game opt localization for client's locale, ignore that request flag.
        if (mes.hasTokenGetI18nDescs && (c.getI18NLocale() != null))
            scd.wantsI18N = true;
        final boolean wantsLocalDescs =
            scd.wantsI18N
            && ! SOCServer.i18n_gameopt_PL_desc.equals(c.getLocalized("gameopt.PL"));

        if (wantsLocalDescs)
        {
            // Gather all game opts we have that we could possibly localize;
            // this list will be narrowed down soon
            optsToLocal = new HashMap<>();
            for (final SOCGameOption opt : srv.knownOpts.optionsForVersion(cliVers))
                optsToLocal.put(opt.key, opt);
        } else {
            optsToLocal = null;
        }

        // Gather requested game option info:

        if (mes.optionKeys != null)
        {
            for (String okey : mes.optionKeys)
            {
                SOCGameOption opt = srv.knownOpts.getKnownOption(okey, false);

                if ((opt == null) || (opt.minVersion > cliVers))  // don't use dynamic opt.getMinVersion(Map) here
                    opt = new SOCGameOption(okey);  // OTYPE_UNKNOWN

                opts.put(okey, opt);
            }
        }

        if (mes.hasTokenGetAnyChanges
            || ((mes.optionKeys == null) && ! mes.hasOnlyTokenI18n))
        {
            // received "-" or "?CHANGES", so look for newer options (cli is older than us).

            List<SOCGameOption> newerOpts = srv.knownOpts.optionsNewerThanVersion(cliVers, false, true);
            if (newerOpts != null)
                for (SOCGameOption opt : newerOpts)
                    opts.put(opt.key, opt);

            if (mes.optionKeys == null)
                alreadyTrimmedEnums = true;

            if (cliVers < SOCGameOption.VERSION_FOR_LONGER_OPTNAMES)
            {
                // Client is older than 2.0.00; we can't send it any long option names.
                Iterator<String> opi = opts.keySet().iterator();
                while (opi.hasNext())
                {
                    final String okey = opi.next();
                    if ((okey.length() > 3) || okey.contains("_"))
                        opi.remove();
                }
            }
        }

        // If any known opts which require client features limited/not supported by this client,
        // add them to opts so unknowns will be sent as OTYPE_UNKNOWN.
        // Overwrites any which happened to already be in opts (unknown or otherwise).
        // See also SOCGameOptionSet.adjustOptionsToKnown which has very similar code for limited client feats.

        // Unsupported 3rd-party opts aren't sent unless client asked about them by key.

        SOCFeatureSet limitedCliFeats = srv.checkLimitClientFeaturesForServerDisallows(scd.feats);
        if ((limitedCliFeats == null) && hasLimitedFeats)
            limitedCliFeats = scd.feats;

        final Map<String, SOCGameOption> unsupportedOpts =
            (limitedCliFeats != null) ? srv.knownOpts.optionsNotSupported(limitedCliFeats) : null;
        if (unsupportedOpts != null)
            for (SOCGameOption opt : unsupportedOpts.values())
                opts.put(opt.key, new SOCGameOption(opt.key));  // OTYPE_UNKNOWN

        final Map<String, SOCGameOption> trimmedOpts =
            (limitedCliFeats != null) ? srv.knownOpts.optionsTrimmedForSupport(limitedCliFeats) : null;
        if (trimmedOpts != null)
            opts.putAll(trimmedOpts);

        final SOCGameOptionSet opts3p = srv.knownOpts.optionsWithFlag(SOCGameOption.FLAG_3RD_PARTY, 0);
        if (opts3p != null)
        {
            final SOCFeatureSet cliFeats = scd.feats;
            final List<String> requestedKeys = mes.optionKeys;

            for (SOCGameOption opt : opts3p)
            {
                final String ofeat = opt.getClientFeature();
                if (ofeat == null)
                    continue;

                if ((cliFeats == null) || ! cliFeats.isActive(ofeat))
                {
                    final String okey = opt.key;

                    if ((requestedKeys != null) && requestedKeys.contains(okey))
                    {
                        opts.put(okey, new SOCGameOption(okey));  // OTYPE_UNKNOWN
                    } else {
                        opts.remove(okey);
                        if (wantsLocalDescs)
                            optsToLocal.remove(okey);
                    }
                }
            }
        }

        // Iterate through requested or calculated opts list.
        // Send requested options' info, and remove them from optsToLocal to
        // avoid sending separate message with those opts' localization info:

        for (SOCGameOption opt : opts.values())
        {
            // this iteration's opt reference may be changed in loop body to customize gameopt info
            // sent to client; same key, but might change optType or other fields

            final String okey = opt.key;
            String localDesc = null;  // i18n-localized opt.desc, if wantsLocalDescs

            if (opt.optType != SOCGameOption.OTYPE_UNKNOWN)
            {
                if ((opt.minVersion > cliVers)
                    || (hasLimitedFeats && unsupportedOpts.containsKey(okey)))
                    opt = new SOCGameOption(okey);  // OTYPE_UNKNOWN
                else if (wantsLocalDescs)
                    try {
                        localDesc = c.getLocalized("gameopt." + okey);
                    } catch (MissingResourceException e) {}
            }

            if (wantsLocalDescs)
            {
                // don't send opt's localization info again after GAMEOPTIONINFOs
                optsToLocal.remove(okey);

                if (opt.getDesc().equals(localDesc))
                    // don't send desc if not localized, client already has unlocalized desc string
                    localDesc = null;
            }

            // Enum-type options may have their values restricted by version.
            if ( (! alreadyTrimmedEnums)
                && (opt.enumVals != null)
                && (opt.optType != SOCGameOption.OTYPE_UNKNOWN)
                && (opt.lastModVersion > cliVers))
            {
                opt = SOCGameOption.trimEnumForVersion(opt, cliVers);
            }

            c.put(new SOCGameOptionInfo(opt, cliVers, localDesc));
        }

        // send any opts which are localized but otherwise unchanged between server's/client's version
        if (optsToLocal != null)  // empty is OK
        {
            List<String> strs = new ArrayList<>(2 * optsToLocal.size());
            for (final SOCGameOption opt : optsToLocal.values())
            {
                if (opt.hasFlag(SOCGameOption.FLAG_ACTIVATED))
                    continue;  // already sent localized during SOCServer.setClientVersSendGamesOrReject

                try {
                    String localDesc = c.getLocalized("gameopt." + opt.key);
                    if (! opt.getDesc().equals(localDesc))
                    {
                        strs.add(opt.key);
                        strs.add(localDesc);
                    }
                } catch (MissingResourceException e) {}
            }

            c.put(new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_GAMEOPT, SOCLocalizedStrings.FLAG_SENT_ALL, strs));
        }

        // mark end of list, even if list was empty
        c.put(SOCGameOptionInfo.OPTINFO_NO_MORE_OPTS);  // GAMEOPTIONINFO("-")
    }

    /**
     * Process client request for updated {@link SOCScenario} info.
     * Added 2015-09-21 for v2.0.00.
     */
    private void handleSCENARIOINFO(final Connection c, final SOCScenarioInfo mes)
    {
        if (c == null)
            return;

        List<String> params = mes.getParams();
        int L = params.size();
        if (L == 0)
            return;  // malformed

        final boolean hasAnyChangedMarker = params.get(L - 1).equals(SOCScenarioInfo.MARKER_ANY_CHANGED);
        if (hasAnyChangedMarker)
        {
            params.remove(L - 1);
            --L;
        }
        else if (L == 1)
        {
            // requesting one scenario
            srv.sendGameScenarioInfo(params.get(0), null, c, true, false);
            return;
        }

        // Calculate and respond; be sure to include any requested scKeys from params

        final SOCClientData scd = (SOCClientData) c.getAppData();
        final int cliVers = scd.scenVersion;

        Map<String, SOCScenario> knownScens = null;  // caches SOCScenario.getAllKnownScenarios() if called

        List<SOCScenario> changes = null;
        if (hasAnyChangedMarker && (cliVers < Version.versionNumber()))
        {
            knownScens = SOCScenario.getAllKnownScenarios();
            changes = SOCVersionedItem.itemsNewerThanVersion
                (cliVers, false, knownScens);
        }

        if (L > 0)
        {
            if (changes == null)
                changes = new ArrayList<SOCScenario>();

            for (String scKey : params)
            {
                SOCScenario sc = SOCScenario.getScenario(scKey);
                if ((sc == null) || (sc.minVersion > cliVers))
                    // unknown scenario, or too new; send too-new ones in case client encounters one as a listed game's
                    // scenario (server also sends too-new SOCGameOptions as unknowns, with the same intention)
                    c.put(new SOCScenarioInfo(scKey, true));
                else if (! changes.contains(sc))
                    changes.add(sc);
            }
        }

        if (changes != null)
            for (final SOCScenario sc : changes)
                if (sc.minVersion <= cliVers)
                    srv.sendGameScenarioInfo(null, sc, c, true, false);
                else
                    c.put(new SOCScenarioInfo(sc.key, true));

        if (hasAnyChangedMarker && scd.wantsI18N && ! scd.sentAllScenarioStrings)
        {
            // if available send each scenario's localized strings, unless we've already sent its full info

            if (! scd.checkedLocaleScenStrings)
            {
                scd.localeHasScenStrings = scd.localeHasGameScenarios(c);
                scd.checkedLocaleScenStrings = true;
            }

            if (scd.localeHasScenStrings)
            {
                if (knownScens == null)
                    knownScens = SOCScenario.getAllKnownScenarios();

                ArrayList<String> scKeys = new ArrayList<String>();
                for (final SOCScenario sc : SOCVersionedItem.itemsForVersion(cliVers, knownScens))
                    if ((changes == null) || ! changes.contains(sc))
                        scKeys.add(sc.key);

                List<String> scenStrs;
                if (! scKeys.isEmpty())
                    scenStrs = SOCServer.localizeGameScenarios(scd.locale, scKeys, false, false, scd);
                else
                    scenStrs = scKeys;  // re-use the empty list object

                c.put(new SOCLocalizedStrings
                        (SOCLocalizedStrings.TYPE_SCENARIO, SOCLocalizedStrings.FLAG_SENT_ALL, scenStrs));
            }

            scd.sentAllScenarioStrings = true;
        }

        c.put(new SOCScenarioInfo(null, null, null));  // send end of list

        if (hasAnyChangedMarker)
        {
            scd.sentAllScenarioInfo = true;
            scd.sentAllScenarioStrings = true;
        }
    }


    /// General messages during a game ///


    /**
     * handle "change face" message.
     *
     * @param c  the connection
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleCHANGEFACE(Connection c, final SOCChangeFace mes)
    {
        final String gaName = mes.getGame();
        final SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;
        SOCPlayer player = ga.getPlayer(c.getData());
        if (player == null)
            return;
        final int id = mes.getFaceId();
        if ((id <= 0) && ! player.isRobot())
            return;  // only bots should use bot icons

        player.setFaceId(id);
        srv.messageToGame(gaName, true, new SOCChangeFace(gaName, player.getPlayerNumber(), id));

        final SOCClientData scd = (SOCClientData) c.getAppData();
        if ((scd != null) && ! scd.isRobot)
            scd.faceId = id;
    }

    /**
     * handle "set seat lock" message.
     *
     * @param c  the connection
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleSETSEATLOCK(Connection c, final SOCSetSeatLock mes)
    {
        final SOCGame.SeatLockState sl = mes.getLockState();
        final String gaName = mes.getGame();
        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;
        SOCPlayer player = ga.getPlayer(c.getData());
        if (player == null)
            return;

        try
        {
            final int pn = mes.getPlayerNumber();
            ga.setSeatLock(pn, sl);
            if ((sl != SOCGame.SeatLockState.CLEAR_ON_RESET) || (ga.clientVersionLowest >= 2000))
            {
                srv.messageToGame(gaName, true, mes);
            } else {
                // older clients won't recognize that lock state
                srv.messageToGameForVersions
                    (ga, 2000, Integer.MAX_VALUE, mes, true);
                srv.messageToGameForVersions
                    (ga, -1, 1999, new SOCSetSeatLock(gaName, pn, SOCGame.SeatLockState.UNLOCKED), true);

                srv.recordGameEvent(gaName, mes);
            }
        }
        catch (IllegalStateException e) {
            srv.messageToPlayerKeyed
                (c, gaName, player.getPlayerNumber(), "reply.lock.cannot");  // "Cannot set that lock right now."
        }
    }

    /**
     * Handle text message to a channel, including {@code *KILLCHANNEL*} channel debug command.
     *<P>
     * Was part of {@code SOCServer.processCommand(..)} before v1.2.00.
     * Before v2.0.00 this method was {@code handleTEXTMSG}.
     *
     * @param c  the connection
     * @param mes  the message
     * @since 1.2.00
     */
    void handleCHANNELTEXTMSG(final Connection c, final SOCChannelTextMsg mes)
    {
        final String chName = mes.getChannel(), mName = c.getData(), txt = mes.getText().trim();

        if (srv.isDebugUserEnabled() && mName.equals("debug"))
        {
            if (txt.startsWith("*KILLCHANNEL*"))
            {
                srv.messageToChannel(chName, new SOCChannelTextMsg
                    (chName, SOCServer.SERVERNAME,
                     "********** " + mName + " KILLED THE CHANNEL **********"));

                channelList.takeMonitor();
                try
                {
                    srv.destroyChannel(chName);
                }
                catch (Exception e)
                {
                    D.ebugPrintStackTrace(e, "Exception in KILLCHANNEL");
                }
                finally
                {
                    channelList.releaseMonitor();
                }

                srv.broadcast(new SOCDeleteChannel(chName));

                return;
            }
        }

        /**
         * Send the message to the members of the channel
         * (don't send all message fields received from client)
         */
        if (srv.channelList.isMember(c, chName))
        {
            srv.messageToChannel(chName, new SOCChannelTextMsg(chName, mName, txt));

            final SOCChatRecentBuffer buf = srv.channelList.getChatBuffer(chName);
            synchronized(buf)
            {
                buf.add(mName, txt);
            }
        }
    }

    /**
     * Handle game text messages, including debug commands.
     * Was part of SOCServer.processCommand before 1.1.07.
     *<P>
     * Some commands are unprivileged and can be run by any client:
     *<UL>
     * <LI> *ADDTIME*
     * <LI> *CHECKTIME*
     * <LI> *VERSION*
     * <LI> *STATS*
     * <LI> *WHO*
     *</UL>
     * These commands are processed in this method.
     *<P>
     * Some admin/debug commands like {@code *BCAST*} and {@code *DBSETTINGS*} are processed here,
     * by calling {@link #processAdminCommand(Connection, SOCGame, String, String)}.
     *<P>
     * Others can be run only by certain users or when certain server flags are set.
     * Those are processed in {@link SOCServer#processDebugCommand(Connection, SOCGame, String, String)}.
     *
     * @param c  User sending the text message which may be a debug/admin command
     * @param gameTextMsgMes  Text message/command to process
     * @since 1.1.07
     */
    void handleGAMETEXTMSG(Connection c, SOCGameTextMsg gameTextMsgMes)
    {
        //createNewGameEventRecord();
        //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
        final String gaName = gameTextMsgMes.getGame();

        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;  // <---- early return: no game by that name ----

        final String plName = c.getData();

        final boolean userIsDebug =
            ((srv.isDebugUserEnabled() && plName.equals("debug"))
            || (c instanceof StringConnection));
            // 1.1.07: all practice games are debug mode, for ease of debugging;
            //         not much use for a chat window in a practice game anyway.

        boolean canChat = userIsDebug || (null != ga.getPlayer(plName)) || srv.isUserDBUserAdmin(plName);
        if ((! canChat) && gameList.isMember(c, gaName))
        {
            // To avoid disruptions by game observers, only players can chat after initial placement.
            // To help form the game, non-seated members can also participate in the chat until then.
            final int gstate = ga.getGameState();
            canChat =
                (gstate < SOCGame.ROLL_OR_CARD) || (gstate == SOCGame.LOADING) || (gstate == SOCGame.LOADING_RESUMING);
        }

        //currentGameEventRecord.setSnapshot(ga);

        final String cmdText = gameTextMsgMes.getText().trim();
        String cmdTxtUC = null;

        if (canChat && (cmdText.charAt(0) == '*'))
        {
            cmdTxtUC = cmdText.toUpperCase(Locale.US);
            boolean matchedHere = true;

            if (cmdTxtUC.startsWith("*ADDTIME*") || cmdTxtUC.startsWith("ADDTIME"))
            {
                // Unless this is a practice game, if reasonable
                // add 30 minutes to the expiration time.  If this
                // changes to another timespan, please update the
                // warning text sent in checkForExpiredGames().
                // Use ">>>" in message text to mark as urgent.
                // Note: If the command text changes from '*ADDTIME*' to something else,
                // please update the warning text sent in checkForExpiredGames().

                if (ga.isPractice)
                {
                    srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_NON_EVENT, "reply.addtime.practice.never");
                        // ">>> Practice games never expire."
                } else if (ga.getGameState() >= SOCGame.OVER) {
                    srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_NON_EVENT, "reply.addtime.game_over");
                        // "This game is over, cannot extend its time."
                } else {
                    // check game time currently remaining: if already more than
                    // the original GAME_TIME_EXPIRE_MINUTES + GAME_TIME_EXPIRE_ADDTIME_MINUTES,
                    // don't add more now.
                    final long now = System.currentTimeMillis();
                    long exp = ga.getExpiration();
                    int minRemain = (int) ((exp - now) / (60 * 1000));

                    final int gameMaxMins = SOCGameListAtServer.GAME_TIME_EXPIRE_MINUTES
                        + SOCServer.GAME_TIME_EXPIRE_ADDTIME_MINUTES;
                    if (minRemain > gameMaxMins - 4)
                    {
                        srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                            "reply.addtime.not_expire_soon", Integer.valueOf(minRemain));
                            // "Ask again later: This game does not expire soon, it has {0} minutes remaining."
                        // This check time subtracts 4 minutes to keep too-frequent addtime requests
                        // from spamming all game members with announcements
                    } else {
                        int minAdd = SOCServer.GAME_TIME_EXPIRE_ADDTIME_MINUTES;
                        if (minRemain + minAdd > gameMaxMins)
                            minAdd = gameMaxMins - minRemain;
                        exp += (minAdd * 60 * 1000);
                        minRemain += minAdd;
                        if (minRemain < SOCServer.GAME_TIME_EXPIRE_ADDTIME_MINUTES)
                        {
                            // minRemain might be small or negative; can happen if server was on a sleeping laptop
                            minRemain = SOCServer.GAME_TIME_EXPIRE_ADDTIME_MINUTES;
                            exp = now + (minRemain * 60 * 1000);
                        }

                        ga.setExpiration(exp);
                        srv.messageToGameKeyed(ga, true, true, "reply.addtime.extended");  // ">>> Game time has been extended."
                        srv.messageToGameKeyed(ga, true, true, "stats.game.willexpire.urgent",
                            Integer.valueOf(minRemain));
                            // ">>> This game will expire in 45 minutes."
                    }
                }
            }
            else if (cmdTxtUC.startsWith("*CHECKTIME*"))
            {
                /// Check the time remaining for this game
                processDebugCommand_gameStats(c, ga, true);
            }
            else if (cmdTxtUC.startsWith("*VERSION*"))
            {
                srv.messageToPlayer(c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                    "Java Settlers Server " +Version.versionNumber()
                    + " (" + Version.version() + ") build " + Version.buildnum());
            }
            else if (cmdTxtUC.startsWith("*STATS*"))
            {
                processDebugCommand_serverStats(c, ga);
            }
            else if (cmdTxtUC.startsWith("*WHO*"))
            {
                processDebugCommand_who(c, ga, cmdText);
            }
            else if (userIsDebug || srv.isUserDBUserAdmin(plName))
            {
                matchedHere = processAdminCommand(c, ga, cmdText, cmdTxtUC);
            } else {
                matchedHere = false;
            }

            if (matchedHere)
                return;  // <---- early return: matched and ran a command ----
        }

        //
        // check for admin/debugging commands; if not a command,
        // send chat message text to game members
        //
        if (! canChat)
        {
            srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_OBSERVER, "member.chat.not_observers");
                // "Observers can't chat during the game."

            return;  // <---- early return: not a player in that game ----
        }

        if (cmdTxtUC == null)
            cmdTxtUC = cmdText.toUpperCase(Locale.US);

        if (cmdTxtUC.startsWith("*HELP"))
        {
            for (int i = 0; i < SOCServer.GENERAL_COMMANDS_HELP.length; ++i)
                srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT, SOCServer.GENERAL_COMMANDS_HELP[i]);

            if (userIsDebug || srv.isUserDBUserAdmin(plName))
            {
                srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT, SOCServer.ADMIN_COMMANDS_HEADING);
                for (int i = 0; i < SOCServer.ADMIN_USER_COMMANDS_HELP.length; ++i)
                    srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT,
                        SOCServer.ADMIN_USER_COMMANDS_HELP[i]);
            }

            if (userIsDebug)
            {
                for (int i = 0; i < SOCServer.DEBUG_COMMANDS_HELP.length; ++i)
                    srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT,
                        SOCServer.DEBUG_COMMANDS_HELP[i]);

                GameHandler hand = gameList.getGameTypeHandler(gaName);
                if (hand != null)
                {
                    final String[] GAMETYPE_DEBUG_HELP = hand.getDebugCommandsHelp();
                    if (GAMETYPE_DEBUG_HELP != null)
                        for (int i = 0; i < GAMETYPE_DEBUG_HELP.length; ++i)
                            srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT,
                                GAMETYPE_DEBUG_HELP[i]);
                }
            }
        }
        else
        {
            if (! (userIsDebug && srv.processDebugCommand(c, ga, cmdText, cmdTxtUC)))
            {
                //
                // Send chat message text to the members of the game
                //
                srv.messageToGame(gaName, true, new SOCGameTextMsg(gaName, plName, cmdText));

                final SOCChatRecentBuffer buf = gameList.getChatBuffer(gaName);
                if (buf != null)
                    synchronized(buf)
                    {
                        buf.add(plName, cmdText);
                    }
            }
        }

        //saveCurrentGameEventRecord(gameTextMsgMes.getGame());
    }

    /**
     * Recognize and process admin commands, runnable if
     * {@link SOCServer#isUserDBUserAdmin(String)} or is debug user.
     * Handles the commands listed in {@link SOCServer#ADMIN_USER_COMMANDS_HELP}.
     *<P>
     * <B>Security:</B> Assumes caller has already checked authorization; does not do so here.
     *
     * @param c  Client sending the admin command
     * @param ga  Game in which to reply
     * @param cmdText  Command text
     * @param cmdTextUC  For convenience, {@link String#toUpperCase() cmdText.toUpperCase(Locale.US)} from caller
     * @return true if {@code cmdText} is an admin command and has been handled here, false otherwise
     * @since 2.3.00
     */
    public boolean processAdminCommand
        (final Connection c, final SOCGame ga, final String cmdText, final String cmdTextUC)
    {
        final String gaName = ga.getName();
        boolean matchedHere = true;

        if (cmdTextUC.startsWith("*GC*"))
        {
            Runtime rt = Runtime.getRuntime();
            rt.gc();
            srv.messageToGame(gaName, true, "> GARBAGE COLLECTING DONE");
            srv.messageToGame
                (gaName, true, "> Free Memory: "
                 + getSettingsFormatted_freeMemory(rt.freeMemory(), rt.totalMemory()));  // as MB, % total
        }
        else if (cmdTextUC.startsWith("*BCAST* "))
        {
            srv.broadcast(new SOCBCastTextMsg(c.getData() + ": " + cmdText.substring(8).trim()));
        }
        else if (cmdTextUC.startsWith("*BOTLIST*"))
        {
            StringBuilder sb = new StringBuilder("Currently connected bots: ");
            if (! srv.getConnectedRobotNames(sb))
                sb.append("(None)");
            srv.messageToPlayer(c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED, sb.toString());
        }
        else if (cmdTextUC.startsWith("*RESETBOT* "))
        {
            String botName = cmdText.substring(11).trim();
            srv.messageToGame(gaName, true, "> Admin: RESETBOT " + botName);

            final Connection robotConn = srv.getRobotConnection(botName);
            if (robotConn != null)
            {
                srv.messageToGame(gaName, true, "> Admin: SENDING RESET COMMAND TO " + botName);
                robotConn.put(new SOCAdminReset());
            } else {
                srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT, "Bot not found to reset: " + botName);
            }
        }
        else if (cmdTextUC.startsWith("*KILLBOT* "))
        {
            final String botName = cmdText.substring(10).trim();
            srv.messageToGame(gaName, true, "> Admin: KILLBOT " + botName);

            final Connection robotConn = srv.getRobotConnection(botName);
            if (robotConn != null)
            {
                srv.messageToGame(gaName, true, "> Admin: DISCONNECTING " + botName);
                srv.removeConnection(robotConn, true);
            } else {
                srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT, "Bot not found to disconnect: " + botName);
            }
        }
        else if (cmdTextUC.startsWith("*LOADGAME*"))
        {
            processDebugCommand_loadGame(c, gaName, cmdText.substring(10).trim());
        }
        else if (cmdTextUC.startsWith("*RESUMEGAME*"))
        {
            processDebugCommand_resumeGame(c, ga, cmdText.substring(12).trim());
        }
        else if (cmdTextUC.startsWith("*SAVEGAME*"))
        {
            processDebugCommand_saveGame(c, ga, cmdText.substring(10).trim());
        }
        else if (cmdTextUC.startsWith("*DBSETTINGS*"))
        {
            processDebugCommand_dbSettings(c, ga);
        }
        else
        {
            matchedHere = false;
        }

        return matchedHere;
    }

    /**
     * Process the {@code *DBSETTINGS*} privileged admin command:
     * If {@link SOCDBHelper#isInitialized()},
     * sends the client a formatted list of server DB settings
     * from {@link SOCDBHelper#getSettingsFormatted(SOCServer)}.
     *<P>
     * Assumes caller has verified the client is an admin; doesn't check {@link SOCServer#isUserDBUserAdmin(String)}.
     *
     * @param c  Client sending the admin command
     * @param ga  Game in which to reply
     * @since 1.2.00
     * @see #processDebugCommand_serverStats(Connection, SOCGame)
     */
    private void processDebugCommand_dbSettings(final Connection c, final SOCGame ga)
    {
        final String gaName = ga.getName();

        if (! srv.db.isInitialized())
        {
            srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT, "Not using a database.");
            return;
        }

        srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT, "Database settings:");
        Iterator<String> it = srv.db.getSettingsFormatted(srv).iterator();
        while (it.hasNext())
            srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT, "> " + it.next() + ": " + it.next());
    }

    /**
     * Send connection stats text to a client, appearing in the message pane of a game they're a member of.
     * Handles {@link SOCServer#processDebugCommand_connStats(Connection, SOCGame, boolean)};
     * see that method's javadoc for details and parameters.
     *
     * @since 2.2.00
     * @see #processDebugCommand_serverStats(Connection, SOCGame)
     * @see #processDebugCommand_gameStats(Connection, SOCGame, boolean)
     */
    final void processDebugCommand_connStats
        (final Connection c, final SOCGame ga, final boolean skipWinLossBefore2)
    {
        final String gaName = ga.getName();

        final long connMinutes = (((System.currentTimeMillis() - c.getConnectTime().getTime())) + 30000L) / 60000L;
        final String connMsgKey = (ga.isPractice)
            ? "stats.cli.connected.minutes.prac"  // "You have been practicing # minutes."
            : "stats.cli.connected.minutes";      // "You have been connected # minutes."
        srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_NON_EVENT, connMsgKey, connMinutes);

        final SOCClientData scd = (SOCClientData) c.getAppData();
        if (scd == null)
            return;

        int wins = scd.getWins();
        int losses = scd.getLosses();
        if (wins + losses < ((skipWinLossBefore2) ? 2 : 1))
            return;  // Not enough games completed so far

        if (wins > 0)
        {
            if (losses == 0)
                srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_NON_EVENT,  "stats.cli.winloss.won", wins);
                    // "You have won {0,choice, 1#1 game|1<{0,number} games} since connecting."
            else
                srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_NON_EVENT, "stats.cli.winloss.wonlost", wins, losses);
                    // "You have won {0,choice, 1#1 game|1<{0,number} games} and lost {1,choice, 1#1 game|1<{1,number} games} since connecting."
        } else {
            srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_NON_EVENT, "stats.cli.winloss.lost", losses);
                // "You have lost {0,choice, 1#1 game|1<{0,number} games} since connecting."
        }
    }

    /**
     * Print time-remaining and other game stats.
     * Includes more detail beyond the end-game stats sent in
     * {@link SOCGameHandler#sendGameStateOVER(SOCGame, Connection)}.
     *<P>
     * Before v1.1.20, this method was {@code processDebugCommand_checktime(..)}.
     *
     * @param c  Client requesting the stats
     * @param gameData  Game to print stats; does nothing if {@code null}
     * @param isCheckTime  True if called from *CHECKTIME* server command, false for *STATS*.
     *     If true, mark text as urgent when sending remaining time before game expires.
     * @see #processDebugCommand_connStats(Connection, SOCGame, boolean)
     * @since 1.1.07
     */
    void processDebugCommand_gameStats
        (final Connection c, final SOCGame gameData, final boolean isCheckTime)
    {
        if (gameData == null)
            return;

        final String gaName = gameData.getName();

        srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
            "stats.game.title");  // "-- Game statistics: --"
        srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
            "stats.game.rounds", gameData.getRoundCount());  // Rounds played: 20

        // player's stats
        final int cliVers = c.getVersion();
        if (cliVers >= SOCPlayerStats.VERSION_FOR_RES_ROLL)
        {
            SOCPlayer cp = gameData.getPlayer(c.getData());
            if (cp != null)
            {
                final int pn = cp.getPlayerNumber();
                srv.messageToPlayer(c, gaName, pn,
                    new SOCPlayerStats(cp, SOCPlayerStats.STYPE_RES_ROLL));
                if (cliVers >= SOCPlayerStats.VERSION_FOR_TRADES)
                    srv.messageToPlayer(c, gaName, pn,
                        new SOCPlayerStats(cp, SOCPlayerStats.STYPE_TRADES));
            }
        }

        // time
        int gameSeconds = gameData.getDurationSeconds();
        int gameMinutes = (gameSeconds + 29) / 60;
        gameSeconds = gameSeconds % 60;
        if (gameData.getGameState() < SOCGame.OVER)
            srv.messageToPlayerKeyed
                (c, gaName,  SOCServer.PN_REPLY_TO_UNDETERMINED,
                 "stats.game.startedago", gameMinutes);
                 // "This game started 5 minutes ago."
        else if (gameSeconds == 0)
            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                 "stats.game.was.minutes", gameMinutes);
                 // "This game took # minutes."
        else
            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                 "stats.game.was.minutessec", gameMinutes, gameSeconds);
                 // "This game took # minutes # seconds." [or 1 second.]
        // Ignore possible "1 minutes"; that game is too short to worry about.

        if (! gameData.isPractice)   // practice games don't expire
        {
            // If isCheckTime, use ">>>" in message text to mark as urgent:
            // ">>> This game will expire in 15 minutes."
            srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                ((isCheckTime) ? "stats.game.willexpire.urgent" : "stats.game.willexpire"),
                Integer.valueOf((int) ((gameData.getExpiration() - System.currentTimeMillis()) / 60000)));
        }
    }

    /**
     * Convenience method to add a pair of strings (a statistic's name and value)
     * to this list of server statistics.
     * @param li  List to add to; not null
     * @param name  Stat name to add
     * @param val  Stat value
     * @see #listAddStat(List, String, int)
     * @see #getSettingsFormatted(SOCStringManager)
     * @since 2.3.00
     */
    private void listAddStat(final List<String> li, final String name, final String val)
    {
        li.add(name);
        li.add(val);
    }

    /**
     * Convenience method to add a pair of strings (a statistic's name and int value as string)
     * to this list of server statistics.
     * @param li  List to add to; not null
     * @param name  Stat name to add
     * @param val  Stat value
     * @see #listAddStat(List, String, String)
     * @see #getSettingsFormatted(SOCStringManager)
     * @since 2.3.00
     */
    private void listAddStat(final List<String> li, final String name, final int val)
    {
        li.add(name);
        li.add(Integer.toString(val));
    }

    /**
     * Build a list of server stats for {@link #processDebugCommand_serverStats(Connection, SOCGame)}.
     * @param strings  String manager for localization if this is for a client connection,
     *     or {@code null} to use {@link SOCStringManager#getFallbackServerManagerForClient()}
     * @return Formatted list of server stats strings.
     *     Always an even number of items, a name and then a value for each setting.
     *     A few stats are multi-line lists; each continuation lines is a pair whose "name" is {@code "  "}.
     * @see SOCDBHelper#getSettingsFormatted(SOCServer)
     * @since 2.3.00
     */
    final List<String> getSettingsFormatted(SOCStringManager strings)
    {
        if (strings == null)
            strings = SOCStringManager.getFallbackServerManagerForClient();
        // TODO i18n: localize field names

        final Runtime rt = Runtime.getRuntime();

        ArrayList<String> li = new ArrayList<>();

        listAddStat(li, "Uptime", I18n.durationToDaysHoursMinutesSeconds
            (System.currentTimeMillis() - srv.startTime, strings));
        listAddStat(li, "Connections since startup", srv.getRunConnectionCount());
        listAddStat(li, "Current named connections", srv.getNamedConnectionCount());
        listAddStat(li, "Current connections including unnamed", srv.getCurrentConnectionCount());
        listAddStat(li, "Total Users", srv.numberOfUsers);
        listAddStat(li, "Games started", srv.numberOfGamesStarted);
        listAddStat(li, "Games finished", srv.numberOfGamesFinished);
        listAddStat(li, "Games finished which had bots", srv.numberOfGamesFinishedWithBots);
        listAddStat(li, "Number of bots in finished games", srv.numberOfBotsInFinishedGames);
        final long totalMem = rt.totalMemory(), freeMem = rt.freeMemory();
        listAddStat
            (li, "Total Memory", totalMem + " (" + I18n.bytesToHumanUnits(totalMem) + ')');
        listAddStat
            (li, "Free Memory", getSettingsFormatted_freeMemory(freeMem, totalMem));  // as MB, % total
        listAddStat
            (li, "Version", Version.versionNumber() + " (" + Version.version() + ") build " + Version.buildnum());

        if (! srv.clientPastVersionStats.isEmpty())
        {
            if (srv.clientPastVersionStats.size() == 1)
            {
                listAddStat
                    (li, "Client versions since startup",
                     "all " + Version.version(srv.clientPastVersionStats.keySet().iterator().next()));
            } else {
                // TODO sort it
                listAddStat(li, "Client versions since startup", "(includes bots)");
                for (Integer v : srv.clientPastVersionStats.keySet())
                    listAddStat
                        (li, "  ", Version.version(v) + ": " + srv.clientPastVersionStats.get(v));
            }
        }

        return li;
    }

    /**
     * For display, format the runtime Free Memory stat for {@code *STATS*} and {@code *GC*},
     * incuding MB or GB and % of total: {@code "92384376 (88.1 MB: 71%)"}
     * @param freeMem  Free memory in bytes, from {@link Runtime#freeMemory()}
     * @param totalMem  Total memory in bytes, from {@link Runtime#totalMemory()}
     * @return Formatted display string for free memory
     * @since 2.3.00
     */
    private String getSettingsFormatted_freeMemory(final long freeMem, final long totalMem)
    {
        return freeMem + " (" + I18n.bytesToHumanUnits(freeMem) + ": "
            + ((100 * freeMem) / totalMem) + "%)";
    }

    /**
     * Process the {@code *STATS*} unprivileged debug command:
     * Send the client a list of server statistics, and stats for the game and connection they sent the command from.
     * Calls {@link #processDebugCommand_gameStats(Connection, SOCGame, boolean)}.
     *<P>
     * Before v2.0.00 this method was part of {@code SOCServer.handleGAMETEXTMSG(..)}.
     *
     * @param c  Client sending the {@code *STATS*} command
     * @param ga  Game in which the message is sent
     * @since 2.0.00
     * @see #processDebugCommand_dbSettings(Connection, SOCGame)
     * @see #processDebugCommand_connStats(Connection, SOCGame, boolean)
     */
    final void processDebugCommand_serverStats(final Connection c, final SOCGame ga)
    {
        final String gaName = ga.getName();

        Iterator<String> it = getSettingsFormatted(c.getI18NStringManager()).iterator();
        while (it.hasNext())
            srv.messageToPlayer(c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                "> " + it.next() + ": " + it.next());

        // show range of current game's member client versions if not server version (added to *STATS* in 1.1.19)
        if ((ga.clientVersionLowest != Version.versionNumber())
            || (ga.clientVersionLowest != ga.clientVersionHighest))
            srv.messageToPlayer(c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                "> This game's client versions: "
                + Version.version(ga.clientVersionLowest) + " - " + Version.version(ga.clientVersionHighest));

        processDebugCommand_gameStats(c, ga, false);
        processDebugCommand_connStats(c, ga, false);
    }

    /**
     * Process the {@code *LOADGAME*} debug/admin command: Load the named game and have this client join it.
     * Calls {@link SOCServer#createAndJoinReloadedGame(SavedGameModel, Connection, String)}.
     * @param c  Client sending the command
     * @param connGaName  Client's current game in which the command was sent, for sending response messages
     * @param argsStr  Args for command (trimmed), or ""
     * @since 2.3.00
     */
    /* package */ void processDebugCommand_loadGame(final Connection c, final String connGaName, final String argsStr)
    {
        if (argsStr.isEmpty() || argsStr.indexOf(' ') != -1)
        {
            srv.messageToPlayerKeyed
                (c, connGaName, SOCServer.PN_NON_EVENT, "admin.loadgame.resp.usage");
                // "Usage: *LOADGAME* gamename"
            return;
        }

        if (! DEBUG_COMMAND_SAVEGAME_FILENAME_REGEX.matcher(argsStr).matches())
        {
            srv.messageToPlayerKeyed
                (c, connGaName, SOCServer.PN_NON_EVENT, "admin.loadsavegame.resp.gamename.chars");
                // "gamename can only include letters, numbers, dashes, underscores."
            return;
        }

        if (! processDebugCommand_loadSaveGame_checkDir("LOADGAME", c, connGaName))
            return;

        SavedGameModel sgm = null;
        String errText = null;
        try
        {
            sgm = GameLoaderJSON.loadGame
                (new File(srv.savegameDir, argsStr + GameSaverJSON.FILENAME_EXTENSION), srv);
        } catch (SOCGameOptionVersionException e) {
            errText = c.getLocalized("admin.loadgame.err.too_new.vers", argsStr, e.gameOptsVersion);
                // "Problem loading {0}: Too new: gameMinVersion is {1}"
        } catch (NoSuchElementException e) {
            errText = c.getLocalized("admin.loadgame.err.too_new", argsStr, e.getMessage());
                // "Problem loading {0}: Too new: {1}"
        } catch (SavedGameModel.UnsupportedSGMOperationException e) {
            String hasWhat = e.getMessage();
            try
            {
                // "admin.savegame.cannot_save.scen" -> "a scenario", etc
                hasWhat = c.getLocalized(hasWhat, e.param1, e.param2);
            } catch (MissingResourceException mre) {}
            errText = c.getLocalized("admin.loadgame.err.too_new", argsStr, hasWhat);
        } catch (IOException|StringIndexOutOfBoundsException e) {
            errText = c.getLocalized("admin.loadgame.err.problem_loading", argsStr, e.getMessage());
                // "Problem loading {0}: {1}"
        } catch (IllegalArgumentException e) {
            errText = c.getLocalized("admin.loadgame.err.cant_create", argsStr, e.getCause());
                // "Problem loading {0}: Can't create game: {1}"
        } catch (Throwable th) {
            errText = c.getLocalized("admin.loadgame.err.problem_loading", argsStr, th);
                // "Problem loading {0}: {1}"
            if ("debug".equals(c.getData()))
            {
                soc.debug.D.ebugPrintStackTrace(th, errText);
                errText += c.getLocalized("admin.loadgame.err.append__see_console");
                    // ": See server console"
            }
        }
        if (errText != null)
        {
            srv.messageToPlayer(c, connGaName, SOCServer.PN_NON_EVENT, errText);
            return;
        }

        srv.createAndJoinReloadedGame(sgm, c, connGaName);
    }

    /**
     * Process the {@code *RESUMEGAME*} debug/admin command: Resume the current game,
     * which was recently loaded with {@code *LOADGAME*}.
     * Must be in state {@link SOCGame#LOADING} or {@link SOCGame#LOADING_RESUMING}.
     * Calls {@link SOCServer#resumeReloadedGame(Connection, SOCGame)}.
     *
     * @param c  Client sending the command, game owner if being called by server after last bot has sat down,
     *     or null if owner not available
     * @param ga  Game in which the command was sent
     * @param argsStr  Args for command (trimmed), or ""
     * @since 2.3.00
     */
    /* package */ void processDebugCommand_resumeGame(final Connection c, final SOCGame ga, final String argsStr)
    {
        final String gaName = ga.getName();

        if (! argsStr.isEmpty())
        {
            // TODO once constraints are implemented: have an arg to override them

            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_NON_EVENT, "admin.resumegame.resp.usage");
                // "Usage: *RESUMEGAME* with no arguments"
            return;
        }

        SavedGameModel sgm = (SavedGameModel) ga.savedGameModel;
        if (((ga.getGameState() != SOCGame.LOADING) && (ga.getGameState() != SOCGame.LOADING_RESUMING))
            || (sgm == null))
        {
            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_NON_EVENT, "admin.resumegame.resp.not_waiting");
                // "This game is not waiting to be resumed."
            return;
        }

        srv.resumeReloadedGame(c, ga);
    }

    /**
     * Process the {@code *SAVEGAME*} debug/admin command: Save the current game.
     * @param c  Client sending the command
     * @param ga  Game in which the command was sent
     * @param argsStr  Args for command (trimmed), or ""
     * @since 2.3.00
     */
    /* package */ void processDebugCommand_saveGame(final Connection c, final SOCGame ga, String argsStr)
    {
        final String gaName = ga.getName();
        boolean askedForce = false;

        // very basic flag parse, as a stopgap until something better is needed
        if (argsStr.startsWith("-f "))
        {
            askedForce = true;
            argsStr = argsStr.substring(3).trim();
        } else {
            int i = argsStr.indexOf(' ');
            if ((i != -1) && (argsStr.substring(i + 1).trim().equals("-f")))
            {
                askedForce = true;
                argsStr = argsStr.substring(0, i);
            }
        }

        if (argsStr.isEmpty() || argsStr.indexOf(' ') != -1)
        {
            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_NON_EVENT, "admin.savegame.resp.usage");
                // "Usage: *SAVEGAME* [-f] gamename"
            return;
        }

        if (! processDebugCommand_loadSaveGame_checkDir("SAVEGAME", c, gaName))
            return;

        final String fname = argsStr + GameSaverJSON.FILENAME_EXTENSION;

        if (! askedForce)
            try
            {
                File f = new File(srv.savegameDir, fname);
                if (f.exists())
                {
                    srv.messageToPlayerKeyed
                        (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED, "admin.savegame.resp.file_exists");
                        // "Game file already exists: Add -f flag to force, or use a different name"
                    return;
                }
            } catch (SecurityException e) {}
                // ignore until actual save; that code covers this & other situations

        final int gstate = ga.getGameState();
        if (gstate < SOCGame.ROLL_OR_CARD)
        {
            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED, "admin.savegame.resp.must_initial_placement");
                // "Must finish initial placement before saving."
            return;
        }
        else if ((gstate == SOCGame.LOADING) || (gstate == SOCGame.LOADING_RESUMING))
        {
            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED, "admin.savegame.resp.must_resume");
                // "Must resume loaded game before saving again."
            return;
        }

        if (! DEBUG_COMMAND_SAVEGAME_FILENAME_REGEX.matcher(argsStr).matches())
        {
            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_NON_EVENT, "admin.loadsavegame.resp.gamename.chars");
                // "gamename can only include letters, numbers, dashes, underscores."
            return;
        }

        try
        {
            GameSaverJSON.saveGame(ga, srv.savegameDir, fname, srv);  // <--- The actual save game method ---

            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                 "admin.savegame.ok.saved_to", fname);
                 // "Saved game to {0}"
        } catch (SavedGameModel.UnsupportedSGMOperationException e) {
            String hasWhat = e.getMessage();
            try
            {
                // "admin.savegame.cannot_save.scen" -> "a scenario", etc
                hasWhat = c.getLocalized(hasWhat, e.param1, e.param2);
            } catch (MissingResourceException mre) {}
            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                 "admin.savegame.err.cannot_save_has", hasWhat);
                 // "Cannot save this game, because it has {0}"
        } catch (IllegalArgumentException|IllegalStateException|IOException e) {
            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                 "admin.savegame.err.problem_saving", e);
                  // "Problem saving game: {0}"
        }
    }

    /**
     * Check status of {@link SOCServer#savegameDir} and {@link SOCServer#savegameInitFailed}
     * for {@code *LOADGAME*} and {@code *SAVEGAME*} debug commands.
     * If problem found (dir doesn't exist, etc), sends error message to client.
     *
     * @param cmdName  "LOADGAME" or "SAVEGAME"
     * @param c  Client sending the debug command
     * @param connGaName  Client's current game, for sending error messages
     * @return True if everything looks OK, false if a message was printed to user
     * @since 2.3.00
     */
    private boolean processDebugCommand_loadSaveGame_checkDir
        (final String cmdName, final Connection c, final String connGaName)
    {
        final File dir = srv.savegameDir;

        String errMsgKey = null;  // i18n message key
        Object errMsgObj, errMsgO1 = null;

        if (srv.savegameInitFailed)
        {
            errMsgKey = "admin.loadsavegame.resp.disabled_init";
                // "{0} is disabled: Initialization failed. Check startup messages on server console."
            errMsgObj = cmdName;
        } else if (dir == null) {
            errMsgKey = "admin.loadsavegame.resp.disabled_prop";
                // "{0} is disabled: Must set {1} property"
            errMsgObj = cmdName;
            errMsgO1 = SOCServer.PROP_JSETTLERS_SAVEGAME_DIR;
        } else {
            errMsgObj = dir.getPath();

            try
            {
                if (! dir.exists())
                {
                    errMsgKey = "admin.loadsavegame.err.dir_not_found";
                        // "savegame.dir not found: {0}"
                } else if (! dir.isDirectory()) {
                    errMsgKey = "admin.loadsavegame.err.dir_not_dir";
                        // "savegame.dir file exists but isn't a directory: {0}"
                }
            } catch (SecurityException e) {
                errMsgKey = "admin.loadsavegame.err.dir_no_access";
                    // "Warning: Can't access savegame.dir {0}: {1}"
                errMsgO1 = e;
            }
        }

        if (errMsgKey != null)
        {
            srv.messageToPlayerKeyed(c, connGaName, SOCServer.PN_NON_EVENT, errMsgKey, errMsgObj, errMsgO1);
            return false;
        }

        return true;
    }

    /**
     * Process unprivileged command {@code *WHO*} to show members of current game,
     * or privileged {@code *WHO* gameName|all|*} to show all connected clients or some other game's members.
     *<P>
     * <B>Locks:</B> Takes/releases {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gaName)}
     * to call {@link SOCGameListAtServer#getMembers(String)}.
     *
     * @param c  Client sending the *WHO* command
     * @param ga  Game in which the command was sent
     * @param cmdText   Text of *WHO* command
     * @since 1.1.20
     */
    private void processDebugCommand_who
        (final Connection c, final SOCGame ga, final String cmdText)
    {
        final String gaName = ga.getName();  // name of game where c is connected and sent *WHO* command
        String gaNameWho = gaName;  // name of game to find members; if sendToCli, not equal to gaName
        boolean sendToCli = false;  // if true, send member list only to c instead of whole game

        int i = cmdText.indexOf(' ');
        if (i != -1)
        {
            // look for a game name or */all
            String gname = cmdText.substring(i+1).trim();

            if (gname.length() > 0)
            {
                // Check if using user admins; if not, if using debug user

                final String cliUsername = c.getData();
                final boolean isAdmin =
                    (c instanceof StringConnection)  // practice game
                    || srv.isUserDBUserAdmin(cliUsername)
                    || (srv.isDebugUserEnabled() && cliUsername.equals("debug"));
                if (! isAdmin)
                {
                    srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_NON_EVENT, "reply.must_be_admin.view");
                        // "Must be an administrator to view that."
                    return;
                }

                sendToCli = true;

                if (gname.equals("*") || gname.toUpperCase(Locale.US).equals("ALL"))
                {
                    // Instead of listing the game's members, list all connected clients.
                    // Build list of StringBuilder not String to do as little as possible
                    // inside synchronization block.

                    final ArrayList<StringBuilder> sbs = new ArrayList<StringBuilder>();
                    sbs.add(new StringBuilder(c.getLocalized("reply.who.conn_to_srv")));
                        // "Currently connected to server:"

                    final Integer nUnnamed = srv.getConnectedClientNames(sbs);
                    if (nUnnamed.intValue() != 0)
                    {
                        StringBuilder sb = new StringBuilder("- ");
                        sb.append(c.getLocalized("reply.who.and_unnamed", nUnnamed));
                            // "and {0} unnamed connections"
                        sbs.add(sb);
                    }

                    for (StringBuilder sbb : sbs)
                        srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT, sbb.toString());

                    return;  // <--- Early return; Not listing a game's members ---
                }

                if (gameList.isGame(gname))
                {
                    gaNameWho = gname;
                } else {
                    srv.messageToPlayerKeyed
                        (c, gaName, SOCServer.PN_NON_EVENT, "reply.game.not.found");  // "Game not found."
                    return;
                }
            }
        }

        Vector<Connection> gameMembers = null;

        gameList.takeMonitorForGame(gaNameWho);
        try
        {
            gameMembers = gameList.getMembers(gaNameWho);
            if (! sendToCli)
                srv.messageToGameKeyed(ga, false, false, "reply.game_members.this");  // "This game's members:"
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in *WHO* (gameMembers)");
        } finally {
            gameList.releaseMonitorForGame(gaNameWho);
        }

        if (gameMembers == null)
        {
            return;  // unlikely since empty games are destroyed
        }

        if (sendToCli)
            srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_NON_EVENT, "reply.game_members.of", gaNameWho);  // "Members of game {0}:"

        Enumeration<Connection> membersEnum = gameMembers.elements();
        while (membersEnum.hasMoreElements())
        {
            Connection conn = membersEnum.nextElement();
            String mNameStr = "> " + conn.getData();

            if (sendToCli)
                srv.messageToPlayer(c, gaName, SOCServer.PN_NON_EVENT, mNameStr);
            else
                srv.messageToGame(gaName, false, mNameStr);
        }
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
    private void handleJOINCHANNEL(Connection c, SOCJoinChannel mes)
    {
        if (c == null)
            return;

        if (D.ebugIsEnabled())
            D.ebugPrintlnINFO("handleJOINCHANNEL: " + mes);

        int cliVers = c.getVersion();

        /**
         * Check the reported version; if none, assume 1000 (1.0.00)
         */
        if (cliVers == -1)
        {
            if (! srv.setClientVersSendGamesOrReject(c, SOCServer.CLI_VERSION_ASSUMED_GUESS, null, null, false))
                return;  // <--- Discon and Early return: Client too old ---

            cliVers = c.getVersion();
        }

        final String chName = mes.getChannel().trim();
        if (c.getData() != null)
        {
            handleJOINCHANNEL_postAuth(c, chName, cliVers, SOCServer.AUTH_OR_REJECT__OK);
        } else {
            /**
             * Check that the nickname is ok, check password if supplied; if not ok, sends a SOCStatusMessage.
             */
            final String msgUser = mes.getNickname().trim();  // trim before db query calls
            final String msgPass = mes.getPassword();

            final int cv = cliVers;
            srv.authOrRejectClientUser
                (c, msgUser, msgPass, cliVers, true, false,
                 new SOCServer.AuthSuccessRunnable()
                 {
                    public void success(final Connection conn, final int authResult)
                    {
                        handleJOINCHANNEL_postAuth(conn, chName, cv, authResult);
                    }
                 });
        }
    }

    /**
     * After successful client user auth, take care of the rest of
     * {@link #handleJOINCHANNEL(Connection, SOCJoinChannel)}.
     * @since 1.2.00
     */
    private void handleJOINCHANNEL_postAuth
        (final Connection c, final String ch, final int cliVers, final int authResult)
    {
        final SOCClientData scd = (SOCClientData) c.getAppData();
        final boolean mustSetUsername = (0 != (authResult & SOCServer.AUTH_OR_REJECT__SET_USERNAME));
        final String msgUsername = c.getData();
            // if mustSetUsername, will tell client to set nickname to original case from db case-insensitive search

        /**
         * Check that the channel name is ok
         */

        /*
           if (!checkChannelName(mes.getChannel())) {
           return;
           }
         */
        if ( (! SOCMessage.isSingleLineAndSafe(ch))
             || "*".equals(ch))
        {
            c.put(SOCStatusMessage.buildForVersion
                   (SOCStatusMessage.SV_NEWGAME_NAME_REJECTED, cliVers,
                    c.getLocalized("netmsg.status.common.newgame_name_rejected")));
            // "This name is not permitted, please choose a different name."

            return;  // <---- Early return ----
        }

        /**
         * If creating a new channel, ensure they are below their max channel count.
         */
        if ((! channelList.isChannel(ch))
            && (SOCServer.CLIENT_MAX_CREATE_CHANNELS >= 0)
            && (SOCServer.CLIENT_MAX_CREATE_CHANNELS <= scd.getcurrentCreatedChannels()))
        {
            c.put(SOCStatusMessage.buildForVersion
                   (SOCStatusMessage.SV_NEWCHANNEL_TOO_MANY_CREATED, cliVers,
                    c.getLocalized("netmsg.status.newchannel_too_many_created", SOCServer.CLIENT_MAX_CREATE_CHANNELS)));
            // "Too many of your chat channels still active; maximum: 2"

            return;  // <---- Early return ----
        }

        /**
         * Tell the client that everything is good to go
         */
        final String txt = srv.getClientWelcomeMessage(c);  // "Welcome to Java Settlers of Catan!"
        if (! mustSetUsername)
        {
            if ((! scd.sentPostAuthWelcome) || (c.getVersion() < SOCStringManager.VERSION_FOR_I18N))
            {
                c.put(new SOCStatusMessage
                    (SOCStatusMessage.SV_OK, txt));
                scd.sentPostAuthWelcome = true;
            }
        } else {
            c.put(new SOCStatusMessage
                (SOCStatusMessage.SV_OK_SET_NICKNAME, msgUsername + SOCMessage.sep2_char + txt));
        }
        c.put(new SOCJoinChannelAuth(msgUsername, ch));

        /**
         * Add the Connection to the channel
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
                channelList.createChannel(ch, msgUsername);
                scd.createdChannel();
            }
            catch (Exception e)
            {
                D.ebugPrintStackTrace(e, "Exception in handleJOIN (createChannel)");
            }

            channelList.releaseMonitor();
            srv.broadcast(new SOCNewChannel(ch));
            c.put(new SOCChannelMembers(ch, channelList.getMembers(ch)));
            if (D.ebugOn)
                D.ebugPrintlnINFO("*** " + msgUsername + " joined new channel " + ch + " at "
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
        srv.messageToChannel(ch, new SOCJoinChannel(msgUsername, "", SOCMessage.EMPTYSTR, ch));

        /**
         * Send recap; same sequence is in SOCGameHandler.joinGame with different message type
         */
        final SOCChatRecentBuffer buf = channelList.getChatBuffer(ch);
        {
            List<SOCChatRecentBuffer.Entry> recents;
            synchronized(buf)
            {
                recents = buf.getAll();
            }
            if (! recents.isEmpty())
            {
                c.put(new SOCChannelTextMsg(ch, SOCGameTextMsg.SERVER_FOR_CHAT,
                        c.getLocalized("member.join.recap_begin")));  // [:: ]"Recap of recent chat ::"
                for (SOCChatRecentBuffer.Entry e : recents)
                    c.put(new SOCChannelTextMsg(ch, e.nickname, e.text));
                c.put(new SOCChannelTextMsg(ch, SOCGameTextMsg.SERVER_FOR_CHAT,
                        c.getLocalized("member.join.recap_end")));    // [:: ]"Recap ends ::"
            }
        }
    }

    /**
     * Handle the "leave a channel" message
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleLEAVECHANNEL(Connection c, SOCLeaveChannel mes)
    {
        if (D.ebugIsEnabled())
            D.ebugPrintlnINFO("handleLEAVECHANNEL: " + mes);

        if (c == null)
            return;

        final String chName = mes.getChannel();

        boolean destroyedChannel = false;
        channelList.takeMonitorForChannel(chName);

        try
        {
            destroyedChannel = srv.leaveChannel(c, chName, true, false);
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in handleLEAVECHANNEL");
        }
        finally
        {
            channelList.releaseMonitorForChannel(chName);
        }

        if (destroyedChannel)
        {
            srv.broadcast(new SOCDeleteChannel(chName));
        }
    }


    /// Game lifecycle ///


    /**
     * process the "new game with options request" message.
     * For messages sent, and other details,
     * see {@link SOCServer#createOrJoinGameIfUserOK(Connection, String, String, String, SOCGameOptionSet)}.
     * <P>
     * Because this message is sent only by clients newer than 1.1.06, we definitely know that
     * the client has already sent its version information.
     *
     * @param c  the connection
     * @param mes  the message
     * @since 1.1.07
     */
    private void handleNEWGAMEWITHOPTIONSREQUEST(Connection c, SOCNewGameWithOptionsRequest mes)
    {
        if (c == null)
            return;

        final Map<String, SOCGameOption> optsMap = mes.getOptions(srv.knownOpts);
        srv.createOrJoinGameIfUserOK
            (c, mes.getNickname(), mes.getPassword(), mes.getGame(),
             (optsMap != null) ? new SOCGameOptionSet(optsMap) : null);
    }

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
    private void handleJOINGAME(Connection c, SOCJoinGame mes)
    {
        if (c == null)
            return;

        // D.ebugPrintln("handleJOINGAME: " + mes);

        /**
         * Check the client's reported version; if none, assume 1000 (1.0.00)
         */
        if (c.getVersion() == -1)
        {
            if (! srv.setClientVersSendGamesOrReject(c, SOCServer.CLI_VERSION_ASSUMED_GUESS, null, null, false))
                return;  // <--- Early return: Client too old ---
        }

        srv.createOrJoinGameIfUserOK
            (c, mes.getNickname(), mes.getPassword(), mes.getGame(), null);
    }

    /**
     * Handle the "leave game" message
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleLEAVEGAME(Connection c, SOCLeaveGame mes)
    {
        if (c == null)
            return;

        boolean isMember = false;
        final String gaName = mes.getGame();
        if (! gameList.takeMonitorForGame(gaName))
        {
            return;  // <--- Early return: game not in gamelist ---
        }

        try
        {
            isMember = gameList.isMember(c, gaName);
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in handleLEAVEGAME (isMember)");
        }
        finally
        {
            gameList.releaseMonitorForGame(gaName);
        }

        if (isMember)
        {
            srv.leaveGameMemberAndCleanup(c, null, gaName);
        }
        else if (((SOCClientData) c.getAppData()).isRobot)
        {
            handleLEAVEGAME_maybeGameReset_oldRobot(gaName);

            // During a game reset, this robot player
            // will not be found among cg's players
            // (isMember is false), because it's
            // attached to the old game object
            // instead of the new one.
            // So, check game state and update game's reset data.
        }
    }

    /**
     * Handle an unattached robot saying it is leaving the game,
     * from {@link #handleLEAVEGAME(Connection, SOCLeaveGame)}.
     * Ignore the robot (since it's not a member of the game) unless
     * gamestate is {@link SOCGame#READY_RESET_WAIT_ROBOT_DISMISS}.
     *
     * @since 1.1.07
     */
    private void handleLEAVEGAME_maybeGameReset_oldRobot(final String gaName)
    {
        SOCGame cg = gameList.getGameData(gaName);
        if ((cg == null) || (cg.getGameState() != SOCGame.READY_RESET_WAIT_ROBOT_DISMISS))
            return;

        boolean gameResetRobotsAllDismissed = false;

        // TODO locks
        SOCGameBoardReset gr = cg.boardResetOngoingInfo;
        if (gr != null)
        {
            --gr.oldRobotCount;
            if (0 == gr.oldRobotCount)
                gameResetRobotsAllDismissed = true;
        }

        if (gameResetRobotsAllDismissed)
            srv.resetBoardAndNotify_finish(gr, cg);  // TODO locks?
    }

    /**
     * handle "sit down" message
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleSITDOWN(Connection c, SOCSitDown mes)
    {
        if (c == null)
            return;

        final String gaName = mes.getGame();
        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
        {
            // Out of date client info, or may be observing a deleted game.
            // Already authenticated (dispatcher enforces c.getData != null); replying won't reveal too much
            srv.messageToPlayerKeyed
                (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED, "reply.game.not.found");  // "Game not found."

            return;  // <--- Early return: No active game found ---
        }

        final SOCClientData scd = (SOCClientData) c.getAppData();
        final boolean isArrivingRobot = (scd != null) && ((SOCClientData) scd).isRobot;

        /**
         * make sure this player isn't already sitting
         */
        boolean canSit = true;
        boolean gameIsFull = false, gameAlreadyStarted = false, sentBotDismiss = false;

        /*
           for (int i = 0; i < SOCGame.MAXPLAYERS; i++) {
           if (ga.getPlayer(i).getName() == c.getData()) {
           canSit = false;
           break;
           }
           }
         */
        //D.ebugPrintln("ga.isSeatVacant(mes.getPlayerNumber()) = "+ga.isSeatVacant(mes.getPlayerNumber()));

        /**
         * if this is a robot, remove it from the request list
         */
        boolean isBotJoinRequest = false;
        {
            Hashtable<Connection, Object> joinRequests = srv.robotJoinRequests.get(gaName);
            if (joinRequests != null)
                isBotJoinRequest = (null != joinRequests.remove(c));
        }

        /**
         * make sure a person isn't sitting here already;
         * if a robot is sitting there, dismiss the robot.
         * Can't sit at a vacant seat after everyone has
         * placed 1st settlement+road (state >= START2A).
         *
         * If a human leaves after game is started, seat will appear vacant when the
         * requested bot sits to replace them, so let the bot sit at that vacant seat.
         *
         * While loading or resuming a savegame:
         * - Human players can sit at any position (human or bot)
         *   unless there's already a game member with that position's player name
         * - Server can ask bots to join and sit at places which
         *   (in the savegame data) have seated players already,
         *   which may be marked as human or possibly as a bot with same name as
         *   the random joining bot by coincidence. Since server asked those bots to join,
         *   don't restrict the joining bot client at this point.
         */
        final int pn = mes.getPlayerNumber();
        final int gameState = ga.getGameState();

        ga.takeMonitor();

        try
        {
            if (ga.isSeatVacant(pn))
            {
                gameAlreadyStarted = (gameState >= SOCGame.START2A);
                if (! gameAlreadyStarted)
                    gameIsFull = (1 > ga.getAvailableSeatCount());

                if (gameIsFull || (gameAlreadyStarted && ! isBotJoinRequest))
                    canSit = false;
            } else {
                canSit = false;
                final SOCPlayer seatedPlayer = ga.getPlayer(pn);
                final String seatedName = seatedPlayer.getName();

                /**
                 * if loading a savegame: allow taking over current player,
                 * and taking over a saved non-bot seat if not claimed by a current game member
                 */
                final boolean isLoadingState =
                    (gameState == SOCGame.LOADING) || (gameState == SOCGame.LOADING_RESUMING);
                boolean isloadingBot =
                    isLoadingState && isArrivingRobot;
                final boolean canTakeOverPlayer =
                    seatedPlayer.isRobot()
                    || (isLoadingState && (isloadingBot || ! gameList.isMember(seatedName, gaName)));

                if (isloadingBot && c.getData().equals(seatedName))
                {
                    canSit = true;

                    // The other clients think this client's already seated
                    // because when they joined the game, there was already a seated player
                    // with this name from the loaded savegame data.
                    // So, don't announce "seated" bot is leaving or tell it to leave.
                    // Ideally, no sit-down announcement would be made to the game at this point,
                    // and we'd send player's private info only to the arriving bot, but this is
                    // a corner case, not worth complicating the code that much.
                }
                else if (canTakeOverPlayer
                    && (((ga.getSeatLock(pn) != SOCGame.SeatLockState.LOCKED) && (ga.getCurrentPlayerNumber() != pn))
                        || isLoadingState))
                {
                    /**
                     * boot the robot out of the game
                     */
                    final Connection robotCon = srv.getConnection(seatedName);

                    if ((robotCon != null) && gameList.isMember(robotCon, gaName))
                    {
                        /**
                         * this connection has to wait for the robot to leave,
                         * will then be told they've sat down
                         */
                        Vector<SOCReplaceRequest> disRequests = srv.robotDismissRequests.get(gaName);
                        SOCReplaceRequest req = new SOCReplaceRequest(c, robotCon, mes);

                        if (disRequests == null)
                        {
                            disRequests = new Vector<>();
                            srv.robotDismissRequests.put(gaName, disRequests);
                        }
                        disRequests.addElement(req);

                        sentBotDismiss = true;
                        srv.messageToPlayer(robotCon, gaName, pn, new SOCRobotDismiss(gaName));
                    } else {
                        /**
                         * robotCon wasn't in the game.
                         * Is this a game being reloaded, where robotCon player was
                         * originally a human player, relabeled as a bot during load?
                         * If so, tell game robotCon has left so clients see seat as available
                         * for player sitting now to take over.
                         */
                        if ((ga.savedGameModel != null) && (gameState >= SOCGame.LOADING))
                        {
                            canSit = true;
                            srv.messageToGame(gaName, true, new SOCLeaveGame(seatedName, "-", gaName));
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught at handleSITDOWN");
        } finally {
            ga.releaseMonitor();
        }

        //D.ebugPrintln("canSit 2 = "+canSit);
        if (canSit)
        {
            srv.sitDown(ga, c, pn, isArrivingRobot, false);

            // loadgame: If seat was temporarily unlocked while fetching bot to sit here, re-lock it.
            // Don't do so in state LOADING_RESUMING, because that change might have been done by a human player.

            if ((gameState == SOCGame.LOADING) && isArrivingRobot
                 && (ga.savedGameModel != null)
                 && (((SavedGameModel) ga.savedGameModel).playerSeatLocks != null))
            {
                SOCGame.SeatLockState gaLock = ga.getSeatLock(pn),
                    modelLock = ((SavedGameModel) ga.savedGameModel).playerSeatLocks[pn];
                if ((gaLock != modelLock) && (modelLock != null))
                {
                    ga.setSeatLock(pn, modelLock);
                    srv.messageToGame(gaName, true, new SOCSetSeatLock(gaName, pn, modelLock));
                }
            }
        }
        else
        {
            /**
             * if the robot can't sit, tell it to go away.
             * otherwise if game is full, tell the player.
             */
            if (isArrivingRobot)
            {
                srv.messageToPlayer(c, gaName, SOCServer.PN_OBSERVER, new SOCRobotDismiss(gaName));
            } else if (gameAlreadyStarted) {
                srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_OBSERVER, "member.sit.game.started");
                    // "This game has already started; to play you must take over a robot."
            } else if (gameIsFull) {
                srv.messageToPlayerKeyed(c, gaName, SOCServer.PN_OBSERVER, "member.sit.game.full");
                    // "This game is full; you cannot sit down."
            } else if (! sentBotDismiss) {
                srv.messageToPlayer
                    (c, gaName, SOCServer.PN_NON_EVENT,
                     "This seat is claimed by another game member, choose another.");
                         // I18N OK: client shouldn't ask to take that seat
            }
        }
    }

    /**
     * handle "start game" message.  Game state must be NEW, or this message is ignored.
     * Calls {@link SOCServer#readyGameAskRobotsJoin(SOCGame, boolean[], Connection[], int)}
     * to ask some robots to fill empty seats,
     * or {@link GameHandler#startGame(SOCGame) begin the game} if no robots needed.
     *<P>
     * Called when clients have sat at a new game and a client asks to start it,
     * not called during game board reset.
     *<P>
     * For robot debugging, a client can start and observe a robots-only game if the
     * {@link SOCServer#PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL} property != 0 (including &lt; 0).
     *<P>
     * Visibility is package-level, not private, so {@link SOCServer} can start robot-only games.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @param botsOnly_maxBots  For bot debugging, maximum number of bots to add to the game,
     *     or 0 to fill all empty seats. This parameter is used only when requesting a new
     *     robots-only game using the *STARTBOTGAME* debug command; ignored otherwise.
     * @since 1.0.0
     */
    void handleSTARTGAME
        (Connection c, final SOCStartGame mes, final int botsOnly_maxBots)
    {
        final String gn = mes.getGame();
        SOCGame ga = gameList.getGameData(gn);
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            if (ga.getGameState() == SOCGame.NEW)
            {
                boolean allowStart = true;
                boolean seatsFull = true;
                boolean anyLocked = false;
                int numEmpty = 0;
                int numPlayers = 0;

                //
                // count the number of unlocked empty seats
                //
                for (int i = 0; i < ga.maxPlayers; i++)
                {
                    if (ga.isSeatVacant(i))
                    {
                        if (ga.getSeatLock(i) == SOCGame.SeatLockState.UNLOCKED)
                        {
                            seatsFull = false;
                            ++numEmpty;
                        }
                        else
                        {
                            anyLocked = true;
                        }
                    }
                    else
                    {
                        ++numPlayers;
                    }
                }

                // Check vs max-players allowed in game (option "PL").
                // Like seat locks, this can cause robots to be unwanted
                // in otherwise-empty seats.
                {
                    final int numAvail = ga.getAvailableSeatCount();
                    if (numAvail < numEmpty)
                    {
                        numEmpty = numAvail;
                        if (numEmpty == 0)
                            seatsFull = true;
                    }
                }

                if (numPlayers == 0)
                {
                    // No one has sat, human client who requested STARTGAME is an observer.
                    // Is server configured for robot-only games?  Prop's value can be < 0
                    // to allow this without creating bots-only games at startup.

                    if (0 == srv.getConfigIntProperty(SOCServer.PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL, 0))
                    {
                        allowStart = false;
                        srv.messageToGameKeyed(ga, true, true, "start.player.must.sit");
                            // "To start the game, at least one player must sit down."
                    } else {
                        if ((botsOnly_maxBots != 0) && (botsOnly_maxBots < numEmpty))
                            numEmpty = botsOnly_maxBots;
                    }
                }

                if (seatsFull && (numPlayers < 2))
                {
                    // Don't start the game; client must have more humans sit or unlock some seats for bots.

                    allowStart = false;
                    numEmpty = 3;
                    srv.messageToGameKeyed(ga, true, true, "start.only.cannot.lock.all");
                        // "The only player cannot lock all seats. To start the game, other players or robots must join."
                }
                else if (allowStart && ! seatsFull)
                {
                    // Look for some bots

                    final int numBots = srv.getRobotCount();
                    if (numBots == 0)
                    {
                        if (numPlayers < SOCGame.MINPLAYERS)
                            srv.messageToGameKeyed(ga, true, true, "start.no.robots.on.server", SOCGame.MINPLAYERS);
                                // "No robots on this server, please fill at least {0} seats before starting."
                        else
                            seatsFull = true;  // Enough players to start game.
                    }
                    else
                    {
                        //
                        // make sure there are enough robots connected,
                        // then set gamestate READY and ask them to connect.
                        //
                        if (numEmpty > numBots)
                        {
                            final String m;
                            if (anyLocked)
                                m = "start.not.enough.robots";
                                    // "Not enough robots to fill all the seats. Only {0} robots are available."
                            else
                                m = "start.not.enough.robots.lock";
                                    // "Not enough robots to fill all the seats. Lock some seats. Only {0} robots are available."
                            srv.messageToGameKeyed(ga, true, true, m, numBots);
                        }
                        else
                        {
                            ga.setGameState(SOCGame.READY);

                            /**
                             * Fill all the unlocked empty seats with robots.
                             * Build a hash of Connections of robots asked
                             * to join, and add it to the robotJoinRequests table.
                             */
                            boolean invitedBots = false;
                            IllegalStateException e = null;
                            try
                            {
                                invitedBots = srv.readyGameAskRobotsJoin(ga, null, null, numEmpty);
                            } catch (IllegalStateException ex) {
                                e = ex;
                            }

                            if (! invitedBots)
                            {
                                System.err.println
                                    ("Robot-join problem in game " + gn + ": "
                                     + ((e != null) ? e : " no matching bots available"));

                                // recover, so that human players can still start a game
                                ga.setGameState(SOCGame.NEW);
                                allowStart = false;

                                gameList.takeMonitorForGame(gn);
                                if (e != null)
                                    srv.messageToGameKeyed(ga, true, false, "start.robots.cannot.join.problem", e.getMessage());
                                        // "Sorry, robots cannot join this game: {0}"
                                else
                                    srv.messageToGameKeyed(ga, true, false, "start.robots.cannot.join.options");
                                        // "Sorry, robots cannot join this game because of its options."
                                srv.messageToGameKeyed(ga, true, false, "start.to.start.without.robots");
                                    // "To start the game without robots, lock all empty seats."
                                gameList.releaseMonitorForGame(gn);
                            }
                        }
                    }
                }

                /**
                 * If this doesn't need robots, then start the game.
                 * Otherwise wait for them to sit before starting the game.
                 */
                if (seatsFull && allowStart)
                {
                    GameHandler hand = gameList.getGameTypeHandler(gn);
                    if (hand != null)
                        hand.startGame(ga);
                }
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }
        finally
        {
            ga.releaseMonitor();
        }
    }

    /**
     * handle "reset-board request" message.
     * If {@link SOCGame#getResetVoteActive()} already or {@link SOCPlayer#hasAskedBoardReset()} this turn,
     * ignore. Otherwise: If multiple human players, start a vote with {@link SOCGame#resetVoteBegin(int)}.
     * If requester is sole human player, reset the game to a copy with same name and (copy of) same players,
     * new layout, by calling {@link SOCServer#resetBoardAndNotify(String, int)}.
     *<P>
     * The requesting player doesn't vote, but server still sends them a vote-request message to tell that client their
     * request was accepted and voting has begun.
     *<P>
     * If only one player remains (all other humans have left at end), ask them to start a new game instead.
     * This is a rare occurrence and we shouldn't bring in new robots and all,
     * since we already have an interface to set up a game.
     *<P>
     * If any human player's client is too old to vote for reset, assume they vote Yes.
     *
     * @param c  the connection
     * @param mes  the message
     * @see #handleRESETBOARDVOTE(Connection, SOCResetBoardVote)
     * @since 1.1.00
     */
    private void handleRESETBOARDREQUEST(Connection c, final SOCResetBoardRequest mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;
        SOCPlayer reqPlayer = ga.getPlayer(c.getData());
        if (reqPlayer == null)
        {
            return;  // Not playing in that game (Security)
        }

        /**
         * Is voting already active from another player?
         * Or, has this player already asked for voting this turn?
         */
        if (ga.getResetVoteActive() || reqPlayer.hasAskedBoardReset())
        {
            // Ignore this second request. Can't send REJECT because
            // that would end the already-active round of voting.
            return;
        }

        /**
         * Is there more than one human player?
         * Grab connection information for humans and robots.
         */
        Connection[] humanConns = new Connection[ga.maxPlayers];
        Connection[] robotConns = new Connection[ga.maxPlayers];
        final int numHuman = SOCGameBoardReset.sortPlayerConnections
            (ga, null, gameList.getMembers(gaName), humanConns, robotConns);

        final int reqPN = reqPlayer.getPlayerNumber();
        if (numHuman < 2)
        {
            // Are there robots? Go ahead and reset if so.
            boolean hadRobot = false, hadUnlockedRobot = false;
            for (int i = robotConns.length-1; i>=0; --i)
            {
                if (robotConns[i] != null)
                {
                    hadRobot = true;
                    if (ga.getSeatLock(i) == SOCGame.SeatLockState.UNLOCKED)
                    {
                        hadUnlockedRobot = true;
                        break;
                    }
                }
            }
            if (hadUnlockedRobot)
            {
                srv.resetBoardAndNotify(gaName, reqPN);
            } else if (hadRobot) {
                srv.messageToPlayerKeyed(c, gaName, reqPN, "resetboard.request.unlock.bot");
                    // "Please unlock at least one bot, so you will have an opponent."
            } else {
                srv.messageToGameKeyed(ga, true, true, "resetboard.request.everyone.left");
                    // "Everyone has left this game. Please start a new game with players or bots."
            }
        }
        else
        {
            // Probably put it to a vote.

            // First, Count number of other players who can vote (connected, version chk)
            gameList.takeMonitorForGame(gaName);
            int votingPlayers = 0;
            try
            {
                for (int i = ga.maxPlayers - 1; i>=0; --i)
                {
                    if ((i != reqPN) && ! ga.isSeatVacant(i))
                    {
                        Connection pc = srv.getConnection(ga.getPlayer(i).getName());
                        if ((pc != null) && pc.isConnected() && (pc.getVersion() >= 1100))
                             ++votingPlayers;
                    }
                }
            } finally {
                gameList.releaseMonitorForGame(gaName);
            }

            if (votingPlayers == 0)
            {
                // No one else is capable of voting.
                // Reset the game immediately.
                srv.messageToGameKeyed(ga, true, false, "resetboard.vote.request.alloldcli", c.getData());
                    // ">>> {0} is resetting the game - other connected players are unable to vote (client too old)."

                srv.resetBoardAndNotify(gaName, reqPN);
            }
            else
            {
                // Put it to a vote
                srv.messageToGameKeyed(ga, true, false, "resetboard.vote.request", c.getData());
                    // "requests a board reset - other players please vote."
                final SOCMessage vr = new SOCResetBoardVoteRequest(gaName, reqPN);

                ga.resetVoteBegin(reqPN);

                for (int i = 0; i < ga.maxPlayers; ++i)
                    if (humanConns[i] != null)
                        if (humanConns[i].getVersion() >= 1100)
                            srv.messageToPlayer(humanConns[i], gaName, i, vr);
                        else
                            ga.resetVoteRegister
                                (ga.getPlayer(humanConns[i].getData()).getPlayerNumber(), true);
            }
        }
    }

    /**
     * handle message of player's vote for a "reset-board" request.
     * Register the player's vote with {@link SOCServer#resetBoardVoteNotifyOne(SOCGame, int, String, boolean)}.
     * If all votes have now arrived and the vote is unanimous,
     * resets the game to a copy with same name and players, new layout.
     *
     * @param c  the connection
     * @param mes  the message
     * @see #handleRESETBOARDREQUEST(Connection, SOCResetBoardRequest)
     * @see SOCServer#resetBoardAndNotify(String, int)
     * @since 1.1.00
     */
    private void handleRESETBOARDVOTE(Connection c, final SOCResetBoardVote mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;
        final String plName = c.getData();
        SOCPlayer reqPlayer = ga.getPlayer(plName);
        if (reqPlayer == null)
        {
            return;  // Not playing in that game (security)
        }

        // Register this player's vote, and let game members know.
        // If vote succeeded, go ahead and reset the game.
        // If vote rejected, let everyone know.

        srv.resetBoardVoteNotifyOne(ga, reqPlayer.getPlayerNumber(), plName, mes.getPlayerVote());
    }

}
