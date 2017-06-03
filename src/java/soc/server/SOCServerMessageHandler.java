/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2016-2017 Jeremy D Monin <jeremy@nand.net>
 * Some contents were formerly part of SOCServer.java;
 * Portions of this file Copyright (C) 2003 Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2016 Jeremy D Monin <jeremy@nand.net>
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
package soc.server;

import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Vector;

import soc.debug.D;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCScenario;
import soc.game.SOCVersionedItem;
import soc.message.*;
import soc.server.database.SOCDBHelper;
import soc.server.genericServer.LocalStringConnection;
import soc.server.genericServer.StringConnection;
import soc.util.SOCGameBoardReset;
import soc.util.SOCGameList;
import soc.util.SOCRobotParameters;
import soc.util.Version;

/**
 * Server class to dispatch clients' actions and messages received from the
 * {@link soc.server.genericServer.InboundMessageQueue} not related to game play
 * in specific current games and not handled by {@link SOCGameMessageHandler}.
 * This also includes some messages related to game lifecycle like
 * {@link SOCJoinGame} and {@link SOCSitDown}.
 *<P>
 * Before v2.0.00, these methods and fields were part of {@link SOCServer}
 * {@code .processCommand(String, StringConnection)} and related methods.
 * So, some may have {@code @since} javadoc labels with versions older than 2.0.00.
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
        case SOCMessage.TEXTMSG:
            handleTEXTMSG(c, (SOCTextMsg) mes);
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
         * the client, see {@link soc.client.SOCPlayerClient.GameOptionServerSet}'s javadoc.
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
    private void handleAUTHREQUEST(StringConnection c, final SOCAuthRequest mes)
    {
        if (c == null)
            return;

        int authResult = -1;

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

            // Check user authentication.  Don't call setData or nameConnection yet if there
            // are role-specific things to check and reject during this initial connection.
            final boolean isPlayerRole = mes.role.equals(SOCAuthRequest.ROLE_GAME_PLAYER);
            final String mesUser = mes.nickname.trim();  // trim before db query calls
            authResult = srv.authOrRejectClientUser
                (c, mesUser, mes.password, cliVersion, isPlayerRole, false);

            if (authResult == SOCServer.AUTH_OR_REJECT__FAILED)
                return;  // <---- Early return; authOrRejectClientUser sent the status message ----

            if (! isPlayerRole)
            {
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
                try
                {
                    c.setData(SOCDBHelper.getUser(mesUser));  // case-insensitive db search on mesUser
                    srv.nameConnection(c, false);
                } catch (SQLException e) {
                    // unlikely, we've just queried db in authOrRejectClientUser
                    c.put(SOCStatusMessage.toCmd
                            (SOCStatusMessage.SV_PROBLEM_WITH_DB, c.getVersion(),
                            "Problem connecting to database, please try again later."));
                    return;
                }
            }
        }

        final String txt = c.getLocalized("member.welcome");  // "Welcome to Java Settlers of Catan!"
        if (0 == (authResult & SOCServer.AUTH_OR_REJECT__SET_USERNAME))
            c.put(SOCStatusMessage.toCmd
                (SOCStatusMessage.SV_OK, txt));
        else
            c.put(SOCStatusMessage.toCmd
                (SOCStatusMessage.SV_OK_SET_NICKNAME, c.getData() + SOCMessage.sep2_char + txt));
    }

    /**
     * Handle the "I'm a robot" message.
     * Robots send their {@link SOCVersion} before sending this message.
     * Their version is checked here, must equal server's version.
     * For stability and control, the cookie in this message must
     * match this server's {@link SOCServer#robotCookie}.
     *<P>
     * Bot tuning parameters are sent here to the bot, from
     * {@link SOCDBHelper#retrieveRobotParams(String, boolean) SOCDBHelper.retrieveRobotParams(botName, true)}.
     * See that method for default bot params.
     * See {@link SOCServer#authOrRejectClientRobot(StringConnection, String, String, String)}
     * for {@link SOCClientData} flags and fields set for the bot's connection
     * and for other misc work done, such as {@link Server#cliConnDisconPrintsPending} updates.
     *<P>
     * Before connecting here, bots are named and started in {@link SOCServer#setupLocalRobots(int, int)}.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleIMAROBOT(final StringConnection c, final SOCImARobot mes)
    {
        if (c == null)
            return;

        final String botName = mes.getNickname();
        final String rejectReason = srv.authOrRejectClientRobot
            (c, botName, mes.getCookie(), mes.getRBClass());

        if (rejectReason != null)
        {
            if (rejectReason.equals(SOCServer.MSG_NICKNAME_ALREADY_IN_USE))
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NAME_IN_USE, c.getVersion(), rejectReason));
            c.put(new SOCRejectConnection(rejectReason).toCmd());
            c.disconnectSoft();

            return;  // <--- Early return: rejected ---
        }

        //
        // send the current robot parameters
        //
        SOCRobotParameters params = null;
        try
        {
            params = SOCDBHelper.retrieveRobotParams(botName, true);
                // if no DB in use, returns srv.ROBOT_PARAMS_SMARTER (uses SOCRobotDM.SMART_STRATEGY)
                // or srv.ROBOT_PARAMS_DEFAULT (SOCRobotDM.FAST_STRATEGY).
            if ((params != null) && (params != SOCServer.ROBOT_PARAMS_SMARTER)
                && (params != SOCServer.ROBOT_PARAMS_DEFAULT) && D.ebugIsEnabled())
                D.ebugPrintln("*** Robot Parameters for " + botName + " = " + params);
        }
        catch (SQLException sqle)
        {
            System.err.println("Error retrieving robot parameters from db: Using defaults.");
        }

        if (params == null)
            params = SOCServer.ROBOT_PARAMS_DEFAULT;  // fallback in case of SQLException

        c.put(SOCUpdateRobotParams.toCmd(params));
    }


    /// Communications with authenticated clients ///


    /**
     * Handle the client's echo of a {@link SOCMessage#SERVERPING}.
     * Resets its {@link SOCClientData#disconnectLastPingMillis} to 0
     * to indicate client is actively responsive to server.
     * @since 1.1.08
     */
    private void handleSERVERPING(StringConnection c, SOCServerPing mes)
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
    private void handleLOCALIZEDSTRINGS(final StringConnection c, final SOCLocalizedStrings mes)
    {
        final List<String> str = mes.getParams();
        final String type = str.get(0);
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

    /**
     * process the "game option get defaults" message.
     * User has clicked the "New Game" button for the first time, client needs {@link SOCGameOption} values.
     * Responds to client by sending {@link SOCGameOptionGetDefaults GAMEOPTIONGETDEFAULTS}.
     * All of server's known options are sent, except empty string-valued options.
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
    private void handleGAMEOPTIONGETDEFAULTS(StringConnection c, SOCGameOptionGetDefaults mes)
    {
        if (c == null)
            return;

        final boolean hideLongNameOpts = (c.getVersion() < SOCGameOption.VERSION_FOR_LONGER_OPTNAMES);
        c.put(SOCGameOptionGetDefaults.toCmd
              (SOCGameOption.packKnownOptionsToString(true, hideLongNameOpts)));
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
     *
     * @param c  the connection
     * @param mes  the message
     * @since 1.1.07
     */
    private void handleGAMEOPTIONGETINFOS(StringConnection c, SOCGameOptionGetInfos mes)
    {
        if (c == null)
            return;

        final int cliVers = c.getVersion();
        final SOCClientData scd = (SOCClientData) c.getAppData();
        boolean alreadyTrimmedEnums = false;
        Vector<String> okeys = mes.getOptionKeys();
        List<SOCGameOption> opts = null;  // opts to send as SOCGameOptionInfo
        final Map<String, SOCGameOption> optsToLocal;  // opts to send in a SOCLocalizedStrings instead

        // check for request for i18n localized descriptions (client v2.0.00 or newer);
        // if we don't have game opt localization for client's locale, ignore that request flag.
        if (mes.hasTokenGetI18nDescs() && (c.getI18NLocale() != null))
            scd.wantsI18N = true;
        final boolean wantsLocalDescs =
            scd.wantsI18N
            && ! SOCServer.i18n_gameopt_PL_desc.equals(c.getLocalized("gameopt.PL"));

        if (wantsLocalDescs)
        {
            // Gather all game opts we have that we could possibly localize;
            // this list will be narrowed down soon
            optsToLocal = new HashMap<String, SOCGameOption>();
            for (final SOCGameOption opt : SOCGameOption.optionsForVersion(cliVers, null))
                optsToLocal.put(opt.key, opt);
        } else {
            optsToLocal = null;
        }

        // Gather requested game option info:
        if (okeys == null)
        {
            // received "-", look for newer options (cli is older than us).
            // opts will be null if there are no newer ones.
            opts = SOCGameOption.optionsNewerThanVersion(cliVers, false, true, null);
            alreadyTrimmedEnums = true;

            if ((opts != null) && (cliVers < SOCGameOption.VERSION_FOR_LONGER_OPTNAMES))
            {
                // Client is older than 2.0.00; we can't send it any long option names.
                Iterator<SOCGameOption> opi = opts.iterator();
                while (opi.hasNext())
                {
                    final SOCGameOption op = opi.next();
                    if ((op.key.length() > 3) || op.key.contains("_"))
                        opi.remove();
                }

                if (opts.isEmpty())
                    opts = null;
            }
        }

        // Iterate through requested okeys or calculated opts list.
        // Send requested options' info, and remove them from optsToLocal to
        // avoid sending separate message with those opts' localization info:

        if ((opts != null) || (okeys != null))
        {
            final int L = (opts != null) ? opts.size() : okeys.size();
            for (int i = 0; i < L; ++i)
            {
                SOCGameOption opt;
                String localDesc = null;  // i18n-localized opt.desc, if wantsLocalDescs

                if (opts != null)
                {
                    opt = opts.get(i);
                    if (opt.minVersion > cliVers)
                    {
                        opt = new SOCGameOption(opt.key);  // OTYPE_UNKNOWN
                    }
                    else if (wantsLocalDescs)
                    {
                        try {
                            localDesc = c.getLocalized("gameopt." + opt.key);
                        } catch (MissingResourceException e) {}
                    }
                } else {
                    final String okey = okeys.elementAt(i);
                    opt = SOCGameOption.getOption(okey, false);

                    if ((opt == null) || (opt.minVersion > cliVers))  // Don't use dynamic opt.getMinVersion(Map) here
                    {
                        opt = new SOCGameOption(okey);  // OTYPE_UNKNOWN
                    }
                    else if (wantsLocalDescs)
                    {
                        try {
                            localDesc = c.getLocalized("gameopt." + okey);
                        } catch (MissingResourceException e) {}
                    }
                }

                if (wantsLocalDescs)
                {
                    // don't send opt's localization info again after GAMEOPTIONINFOs
                    optsToLocal.remove(opt.key);

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

                c.put(new SOCGameOptionInfo(opt, cliVers, localDesc).toCmd());
            }
        }

        // send any opts which are localized but otherwise unchanged between server's/client's version
        if (optsToLocal != null)  // empty is OK
        {
            List<String> strs = new ArrayList<String>(2 * optsToLocal.size());
            for (final SOCGameOption opt : optsToLocal.values())
            {
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
                (SOCLocalizedStrings.TYPE_GAMEOPT, SOCLocalizedStrings.FLAG_SENT_ALL, strs).toCmd());
        }

        // mark end of list, even if list was empty
        c.put(SOCGameOptionInfo.OPTINFO_NO_MORE_OPTS.toCmd());  // GAMEOPTIONINFO("-")
    }

    /**
     * Process client request for updated {@link SOCScenario} info.
     * Added 2015-09-21 for v2.0.00.
     */
    private void handleSCENARIOINFO(final StringConnection c, final SOCScenarioInfo mes)
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
            srv.sendGameScenarioInfo(params.get(0), null, c, false);
            return;
        }

        // Calculate and respond; be sure to include any requested scKeys from params

        final int cliVers = c.getVersion();
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
                    c.put(new SOCScenarioInfo(scKey, true).toCmd());
                else if (! changes.contains(sc))
                    changes.add(sc);
            }
        }

        if (changes != null)
            for (final SOCScenario sc : changes)
                if (sc.minVersion <= cliVers)
                    srv.sendGameScenarioInfo(null, sc, c, false);
                else
                    c.put(new SOCScenarioInfo(sc.key, true).toCmd());

        final SOCClientData scd = (SOCClientData) c.getAppData();

        if (hasAnyChangedMarker && scd.wantsI18N && ! scd.sentAllScenarioStrings)
        {
            // if available send each scenario's localized strings, unless we've already sent its full info

            if (! scd.checkedLocaleScenStrings)
            {
                scd.localeHasScenStrings = SOCServer.clientHasLocalizedStrs_gameScenarios(c);
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
                    scenStrs = SOCServer.localizeGameScenarios(scd.locale, scKeys, false, scd);
                else
                    scenStrs = scKeys;  // re-use the empty list object

                c.put(SOCLocalizedStrings.toCmd
                        (SOCLocalizedStrings.TYPE_SCENARIO, SOCLocalizedStrings.FLAG_SENT_ALL, scenStrs));
            }

            scd.sentAllScenarioStrings = true;
        }

        c.put(new SOCScenarioInfo(null, null, null).toCmd());  // send end of list

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
    private void handleCHANGEFACE(StringConnection c, final SOCChangeFace mes)
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
        srv.messageToGame(gaName, new SOCChangeFace(gaName, player.getPlayerNumber(), id));
    }

    /**
     * handle "set seat lock" message.
     *
     * @param c  the connection
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleSETSEATLOCK(StringConnection c, final SOCSetSeatLock mes)
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
                srv.messageToGame(gaName, mes);
            } else {
                // older clients won't recognize that lock state
                srv.messageToGameForVersions
                    (ga, 2000, Integer.MAX_VALUE, mes, true);
                srv.messageToGameForVersions
                    (ga, -1, 1999, new SOCSetSeatLock(gaName, pn, SOCGame.SeatLockState.LOCKED), true);
            }
        }
        catch (IllegalStateException e) {
            srv.messageToPlayerKeyed(c, gaName, "reply.lock.cannot");  // "Cannot set that lock right now."
        }
    }

    /**
     * Handle text message to a channel, including {@code *KILLCHANNEL*} channel debug command.
     * Was part of {@code SOCServer.processCommand} before 2.0.00.
     *
     * @param c  the connection
     * @param mes  the message
     * @since 2.0.00
     */
    void handleTEXTMSG(final StringConnection c, final SOCTextMsg mes)
    {
        final String chName = mes.getChannel();

        if (srv.isDebugUserEnabled() && c.getData().equals("debug"))
        {
            if (mes.getText().startsWith("*KILLCHANNEL*"))
            {
                srv.messageToChannel(chName, new SOCTextMsg
                    (chName, SOCServer.SERVERNAME,
                     "********** " + c.getData() + " KILLED THE CHANNEL **********"));
                channelList.takeMonitor();

                try
                {
                    srv.destroyChannel(chName);
                }
                catch (Exception e)
                {
                    D.ebugPrintStackTrace(e, "Exception in KILLCHANNEL");
                }

                channelList.releaseMonitor();
                srv.broadcast(SOCDeleteChannel.toCmd(chName));

                return;
            }
        }

        /**
         * Send the message to the members of the channel
         */
        if (srv.channelList.isMember(c, chName))
            srv.messageToChannel(chName, mes);
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
     * Others can be run only by certain users or when certain server flags are set.
     * Those are processed in {@link SOCServer#processDebugCommand(StringConnection, String, String, String)}.
     *
     * @since 1.1.07
     */
    void handleGAMETEXTMSG(StringConnection c, SOCGameTextMsg gameTextMsgMes)
    {
        //createNewGameEventRecord();
        //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
        final String gaName = gameTextMsgMes.getGame();
        srv.recordGameEvent(gaName, gameTextMsgMes.toCmd());

        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;  // <---- early return: no game by that name ----

        final String plName = c.getData();
        if (null == ga.getPlayer(plName))
        {
            // c isn't a seated player in that game; have they joined it?
            // To avoid disruptions by game observers, only players can chat after initial placement.
            // To help form the game, non-seated members can also participate in the chat until then.

            final boolean canChat = gameList.isMember(c, gaName) && (ga.getGameState() < SOCGame.PLAY);
            if (! canChat)
            {
                srv.messageToPlayerKeyed(c, gaName, "member.chat.not_observers");  // "Observers can't chat during the game."

                return;  // <---- early return: not a player in that game ----
            }
        }

        //currentGameEventRecord.setSnapshot(ga);

        final String cmdText = gameTextMsgMes.getText();
        final String cmdTxtUC = cmdText.toUpperCase();

        ///
        /// command to add time to a game
        /// If the command text changes from '*ADDTIME*' to something else,
        /// please update the warning text sent in checkForExpiredGames().
        ///
        if (cmdTxtUC.startsWith("*ADDTIME*") || cmdTxtUC.startsWith("ADDTIME"))
        {
            // Unless this is a practice game, if reasonable
            // add 30 minutes to the expiration time.  If this
            // changes to another timespan, please update the
            // warning text sent in checkForExpiredGames().
            // Use ">>>" in message text to mark as urgent.

            if (ga.isPractice)
            {
                srv.messageToPlayerKeyed(c, gaName, "reply.addtime.practice.never");  // ">>> Practice games never expire."
            } else if (ga.getGameState() >= SOCGame.OVER) {
                srv.messageToPlayerKeyed(c, gaName, "reply.addtime.game_over");  // "This game is over, cannot extend its time."
            } else {
                // check game time currently remaining: if already more than
                // the original GAME_TIME_EXPIRE_MINUTES, don't add more now.
                final long now = System.currentTimeMillis();
                long exp = ga.getExpiration();
                int minRemain = (int) ((exp - now) / (60 * 1000));

                if (minRemain > SOCGameListAtServer.GAME_TIME_EXPIRE_MINUTES)
                {
                    srv.messageToPlayerKeyed(c, gaName, "reply.addtime.not_expire_soon", Integer.valueOf(minRemain));
                        // "Ask again later: This game does not expire soon, it has {0} minutes remaining."
                } else {
                    exp += (SOCServer.GAME_TIME_EXPIRE_ADDTIME_MINUTES * 60 * 1000);
                    minRemain += SOCServer.GAME_TIME_EXPIRE_ADDTIME_MINUTES;

                    ga.setExpiration(exp);
                    srv.messageToGameKeyed(ga, true, "reply.addtime.extended");  // ">>> Game time has been extended."
                    srv.messageToGameKeyed(ga, true, "stats.game.willexpire.urgent",
                        Integer.valueOf(minRemain));
                        // ">>> This game will expire in 45 minutes."
                }
            }
        }

        ///
        /// Check the time remaining for this game
        ///
        else if (cmdTxtUC.startsWith("*CHECKTIME*"))
        {
            processDebugCommand_gameStats(c, gaName, ga, true);
        }
        else if (cmdTxtUC.startsWith("*VERSION*"))
        {
            srv.messageToPlayer(c, gaName,
                "Java Settlers Server " +Version.versionNumber() + " (" + Version.version() + ") build " + Version.buildnum());
        }
        else if (cmdTxtUC.startsWith("*STATS*"))
        {
            srv.processDebugCommand_serverStats(c, ga);
        }
        else if (cmdTxtUC.startsWith("*WHO*"))
        {
            processDebugCommand_who(c, ga, cmdText);
        }

        //
        // check for admin/debugging commands
        //
        // 1.1.07: all practice games are debug mode, for ease of debugging;
        //         not much use for a chat window in a practice game anyway.
        //
        else
        {
            final boolean userIsDebug =
                ((srv.isDebugUserEnabled() && plName.equals("debug"))
                || (c instanceof LocalStringConnection));

            if (cmdTxtUC.startsWith("*HELP"))
            {
                for (int i = 0; i < SOCServer.GENERAL_COMMANDS_HELP.length; ++i)
                    srv.messageToPlayer(c, gaName, SOCServer.GENERAL_COMMANDS_HELP[i]);

                if ((userIsDebug && ! (c instanceof LocalStringConnection))  // no user admins in practice games
                    || srv.isUserDBUserAdmin(plName, true))
                {
                    srv.messageToPlayer(c, gaName, SOCServer.ADMIN_COMMANDS_HEADING);
                    for (int i = 0; i < SOCServer.ADMIN_USER_COMMANDS_HELP.length; ++i)
                        srv.messageToPlayer(c, gaName, SOCServer.ADMIN_USER_COMMANDS_HELP[i]);
                }

                if (userIsDebug)
                {
                    for (int i = 0; i < SOCServer.DEBUG_COMMANDS_HELP.length; ++i)
                        srv.messageToPlayer(c, gaName, SOCServer.DEBUG_COMMANDS_HELP[i]);

                    GameHandler hand = gameList.getGameTypeHandler(gaName);
                    if (hand != null)
                    {
                        final String[] GAMETYPE_DEBUG_HELP = hand.getDebugCommandsHelp();
                        if (GAMETYPE_DEBUG_HELP != null)
                            for (int i = 0; i < GAMETYPE_DEBUG_HELP.length; ++i)
                                srv.messageToPlayer(c, gaName, GAMETYPE_DEBUG_HELP[i]);
                    }
                }
            }
            else
            {
                boolean isCmd = userIsDebug && srv.processDebugCommand(c, ga.getName(), cmdText, cmdTxtUC);

                if (! isCmd)
                    //
                    // Send the message to the members of the game
                    //
                    srv.messageToGame(gaName, new SOCGameTextMsg(gaName, plName, cmdText));
            }
        }

        //saveCurrentGameEventRecord(gameTextMsgMes.getGame());
    }

    /**
     * Print time-remaining and other game stats.
     * Includes more detail beyond the end-game stats sent in {@link SOCGameHandler#sendGameStateOVER(SOCGame)}.
     *<P>
     * Before v1.1.20, this method was {@code processDebugCommand_checktime(..)}.
     *
     * @param c  Client requesting the stats
     * @param gaName  {@code gameData.getName()}
     * @param gameData  Game to print stats
     * @param isCheckTime  True if called from *CHECKTIME* server command, false for *STATS*.
     *     If true, mark text as urgent when sending remaining time before game expires.
     * @since 1.1.07
     */
    void processDebugCommand_gameStats
        (StringConnection c, final String gaName, SOCGame gameData, final boolean isCheckTime)
    {
        if (gameData == null)
            return;

        srv.messageToPlayerKeyed(c, gaName, "stats.game.title");  // "-- Game statistics: --"
        srv.messageToPlayerKeyed(c, gaName, "stats.game.rounds", gameData.getRoundCount());  // Rounds played: 20

        // player's stats
        if (c.getVersion() >= SOCPlayerStats.VERSION_FOR_RES_ROLL)
        {
            SOCPlayer cp = gameData.getPlayer(c.getData());
            if (cp != null)
                srv.messageToPlayer(c, new SOCPlayerStats(cp, SOCPlayerStats.STYPE_RES_ROLL));
        }

        // time
        Date gstart = gameData.getStartTime();
        if (gstart != null)
        {
            long gameSeconds = ((new Date().getTime() - gstart.getTime())+500L) / 1000L;
            long gameMinutes = (gameSeconds+29L)/60L;
            srv.messageToPlayerKeyed(c, gaName, "stats.game.startedago", gameMinutes);  // "This game started 5 minutes ago."
            // Ignore possible "1 minutes"; that game is too short to worry about.
        }

        if (! gameData.isPractice)   // practice games don't expire
        {
            // If isCheckTime, use ">>>" in message text to mark as urgent:
            // ">>> This game will expire in 15 minutes."
            srv.messageToPlayerKeyed(c, gaName,
                ((isCheckTime) ? "stats.game.willexpire.urgent" : "stats.game.willexpire"),
                Integer.valueOf((int) ((gameData.getExpiration() - System.currentTimeMillis()) / 60000)));
        }
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
        (final StringConnection c, final SOCGame ga, final String cmdText)
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

                final String uname = c.getData();
                boolean isAdmin = srv.isUserDBUserAdmin(uname, true);
                if (! isAdmin)
                    isAdmin = (srv.isDebugUserEnabled() && uname.equals("debug"));
                if (! isAdmin)
                {
                    srv.messageToPlayerKeyed(c, gaName, "reply.must_be_admin.view");
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
                        srv.messageToPlayer(c, gaName, sbb.toString());

                    return;  // <--- Early return; Not listing a game's members ---
                }

                if (gameList.isGame(gname))
                {
                    gaNameWho = gname;
                } else {
                    srv.messageToPlayerKeyed(c, gaName, "reply.game.not.found");  // "Game not found."
                    return;
                }
            }
        }

        Vector<StringConnection> gameMembers = null;

        gameList.takeMonitorForGame(gaNameWho);
        try
        {
            gameMembers = gameList.getMembers(gaNameWho);
            if (! sendToCli)
                srv.messageToGameKeyed(ga, false, "reply.game_members.this");  // "This game's members:"
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in *WHO* (gameMembers)");
        }
        gameList.releaseMonitorForGame(gaNameWho);

        if (gameMembers == null)
        {
            return;  // unlikely since empty games are destroyed
        }

        if (sendToCli)
            srv.messageToPlayerKeyed(c, gaName, "reply.game_members.of", gaNameWho);  // "Members of game {0}:"

        Enumeration<StringConnection> membersEnum = gameMembers.elements();
        while (membersEnum.hasMoreElements())
        {
            StringConnection conn = membersEnum.nextElement();
            String mNameStr = "> " + conn.getData();

            if (sendToCli)
                srv.messageToPlayer(c, gaName, mNameStr);
            else
                srv.messageToGame(gaName, mNameStr);
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
    private void handleJOINCHANNEL(StringConnection c, SOCJoinChannel mes)
    {
        if (c == null)
            return;

        if (D.ebugIsEnabled())
            D.ebugPrintln("handleJOINCHANNEL: " + mes);

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
        c.put(SOCJoinChannelAuth.toCmd(msgUser, ch));
        final String txt = c.getLocalized("member.welcome");  // "Welcome to Java Settlers of Catan!"
        if (0 == (authResult & SOCServer.AUTH_OR_REJECT__SET_USERNAME))
            c.put(SOCStatusMessage.toCmd
                (SOCStatusMessage.SV_OK, txt));
        else
            c.put(SOCStatusMessage.toCmd
                (SOCStatusMessage.SV_OK_SET_NICKNAME, c.getData() + SOCMessage.sep2_char + txt));

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
                channelList.createChannel(ch, c.getData());
                ((SOCClientData) c.getAppData()).createdChannel();
            }
            catch (Exception e)
            {
                D.ebugPrintStackTrace(e, "Exception in handleJOIN (createChannel)");
            }

            channelList.releaseMonitor();
            srv.broadcast(SOCNewChannel.toCmd(ch));
            c.put(SOCChannelMembers.toCmd(ch, channelList.getMembers(ch)));
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
        srv.messageToChannel(ch, new SOCJoinChannel(msgUser, "", "dummyhost", ch));
    }

    /**
     * Handle the "leave a channel" message
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleLEAVECHANNEL(StringConnection c, SOCLeaveChannel mes)
    {
        if (D.ebugIsEnabled())
            D.ebugPrintln("handleLEAVECHANNEL: " + mes);

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
            D.ebugPrintStackTrace(e, "Exception in handleLEAVECHANNEL");
        }

        channelList.releaseMonitorForChannel(mes.getChannel());

        if (destroyedChannel)
        {
            srv.broadcast(SOCDeleteChannel.toCmd(mes.getChannel()));
        }
    }


    /// Game lifecycle ///


    /**
     * process the "new game with options request" message.
     * For messages sent, and other details,
     * see {@link #createOrJoinGameIfUserOK(StringConnection, String, String, String, Map)}.
     * <P>
     * Because this message is sent only by clients newer than 1.1.06, we definitely know that
     * the client has already sent its version information.
     *
     * @param c  the connection
     * @param mes  the message
     * @since 1.1.07
     */
    private void handleNEWGAMEWITHOPTIONSREQUEST(StringConnection c, SOCNewGameWithOptionsRequest mes)
    {
        if (c == null)
            return;

        srv.createOrJoinGameIfUserOK
            (c, mes.getNickname(), mes.getPassword(), mes.getGame(), mes.getOptions());
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
    private void handleJOINGAME(StringConnection c, SOCJoinGame mes)
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

    /**
     * Handle the "leave game" message
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @since 1.0.0
     */
    private void handleLEAVEGAME(StringConnection c, SOCLeaveGame mes)
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

        gameList.releaseMonitorForGame(gaName);

        if (isMember)
        {
            handleLEAVEGAME_member(c, gaName);
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
     * Handle a member leaving the game, from
     * {@link #handleLEAVEGAME(StringConnection, SOCLeaveGame)}.
     * @since 1.1.07
     */
    private void handleLEAVEGAME_member(StringConnection c, final String gaName)
    {
        boolean gameDestroyed = false;
        if (! gameList.takeMonitorForGame(gaName))
        {
            return;  // <--- Early return: game not in gamelist ---
        }

        try
        {
            gameDestroyed = srv.leaveGame(c, gaName, true, false);
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in handleLEAVEGAME (leaveGame)");
        }

        gameList.releaseMonitorForGame(gaName);

        if (gameDestroyed)
        {
            srv.broadcast(SOCDeleteGame.toCmd(gaName));
        }
        else
        {
            /*
               SOCLeaveGame leaveMessage = new SOCLeaveGame((String)c.getData(), c.host(), mes.getGame());
               messageToGame(mes.getGame(), leaveMessage);
               recordGameEvent(mes.getGame(), leaveMessage.toCmd());
             */
        }

        /**
         * if it's a robot, remove it from the request list
         */
        Vector<SOCReplaceRequest> requests = srv.robotDismissRequests.get(gaName);

        if (requests != null)
        {
            Enumeration<SOCReplaceRequest> reqEnum = requests.elements();
            SOCReplaceRequest req = null;

            while (reqEnum.hasMoreElements())
            {
                SOCReplaceRequest tempReq = reqEnum.nextElement();

                if (tempReq.getLeaving() == c)
                {
                    req = tempReq;
                    break;
                }
            }

            if (req != null)
            {
                requests.removeElement(req);

                /**
                 * Taking over a robot spot: let the person replacing the robot sit down
                 */
                SOCGame ga = gameList.getGameData(gaName);
                final int pn = req.getSitDownMessage().getPlayerNumber();
                final boolean isRobot = req.getSitDownMessage().isRobot();
                if (! isRobot)
                {
                    ga.getPlayer(pn).setFaceId(1);  // Don't keep the robot face icon
                }
                srv.sitDown(ga, req.getArriving(), pn, isRobot, false);
            }
        }
    }

    /**
     * Handle an unattached robot saying it is leaving the game,
     * from {@link #handleLEAVEGAME(StringConnection, SOCLeaveGame)}.
     * Ignore the robot (since it's not a member of the game) unless
     * gamestate is {@link SOCGame#READY_RESET_WAIT_ROBOT_DISMISS}.
     *
     * @since 1.1.07
     */
    private void handleLEAVEGAME_maybeGameReset_oldRobot(final String gaName)
    {
        SOCGame cg = gameList.getGameData(gaName);
        if (cg.getGameState() != SOCGame.READY_RESET_WAIT_ROBOT_DISMISS)
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
    private void handleSITDOWN(StringConnection c, SOCSitDown mes)
    {
        if (c == null)
            return;

        final String gaName = mes.getGame();
        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;

        /**
         * make sure this player isn't already sitting
         */
        boolean canSit = true;
        boolean gameIsFull = false, gameAlreadyStarted = false;

        /*
           for (int i = 0; i < SOCGame.MAXPLAYERS; i++) {
           if (ga.getPlayer(i).getName() == (String)c.getData()) {
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
            Vector<StringConnection> joinRequests = srv.robotJoinRequests.get(gaName);
            if (joinRequests != null)
                isBotJoinRequest = joinRequests.removeElement(c);
        }

        /**
         * make sure a person isn't sitting here already;
         * if a robot is sitting there, dismiss the robot.
         * Can't sit at a vacant seat after everyone has
         * placed 1st settlement+road (state >= START2A).
         *
         * If a human leaves after game is started, seat will appear vacant when the
         * requested bot sits to replace them, so let the bot sit at that vacant seat.
         */
        final int pn = mes.getPlayerNumber();

        ga.takeMonitor();

        try
        {
            if (ga.isSeatVacant(pn))
            {
                gameAlreadyStarted = (ga.getGameState() >= SOCGame.START2A);
                if (! gameAlreadyStarted)
                    gameIsFull = (1 > ga.getAvailableSeatCount());

                if (gameIsFull || (gameAlreadyStarted && ! isBotJoinRequest))
                    canSit = false;
            } else {
                SOCPlayer seatedPlayer = ga.getPlayer(pn);

                if (seatedPlayer.isRobot()
                    && (ga.getSeatLock(pn) != SOCGame.SeatLockState.LOCKED)
                    && (ga.getCurrentPlayerNumber() != pn))
                {
                    /**
                     * boot the robot out of the game
                     */
                    StringConnection robotCon = srv.getConnection(seatedPlayer.getName());
                    robotCon.put(SOCRobotDismiss.toCmd(gaName));

                    /**
                     * this connection has to wait for the robot to leave
                     * and then it can sit down
                     */
                    Vector<SOCReplaceRequest> disRequests = srv.robotDismissRequests.get(gaName);
                    SOCReplaceRequest req = new SOCReplaceRequest(c, robotCon, mes);

                    if (disRequests == null)
                    {
                        disRequests = new Vector<SOCReplaceRequest>();
                        disRequests.addElement(req);
                        srv.robotDismissRequests.put(gaName, disRequests);
                    }
                    else
                    {
                        disRequests.addElement(req);
                    }
                }

                canSit = false;
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught at handleSITDOWN");
        }

        ga.releaseMonitor();

        //D.ebugPrintln("canSit 2 = "+canSit);
        if (canSit)
        {
            srv.sitDown(ga, c, pn, mes.isRobot(), false);
        }
        else
        {
            /**
             * if the robot can't sit, tell it to go away.
             * otherwise if game is full, tell the player.
             */
            if (mes.isRobot())
            {
                c.put(SOCRobotDismiss.toCmd(gaName));
            } else if (gameAlreadyStarted) {
                srv.messageToPlayerKeyed(c, gaName, "member.sit.game.started");
                    // "This game has already started; to play you must take over a robot."
            } else if (gameIsFull) {
                srv.messageToPlayerKeyed(c, gaName, "member.sit.game.full");
                    // "This game is full; you cannot sit down."
            }
        }
    }

    /**
     * handle "start game" message.  Game state must be NEW, or this message is ignored.
     * {@link SOCServer#readyGameAskRobotsJoin(SOCGame, StringConnection[], int) Ask some robots} to fill
     * empty seats, or {@link GameHandler#startGame(SOCGame) begin the game} if no robots needed.
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
        (StringConnection c, final SOCStartGame mes, final int botsOnly_maxBots)
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
                        srv.messageToGameKeyed(ga, true, "start.player.must.sit");
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
                    srv.messageToGameKeyed(ga, true, "start.only.cannot.lock.all");
                        // "The only player cannot lock all seats. To start the game, other players or robots must join."
                }
                else if (allowStart && ! seatsFull)
                {
                    // Look for some bots

                    final int numBots = srv.getRobotCount();
                    if (numBots == 0)
                    {
                        if (numPlayers < SOCGame.MINPLAYERS)
                            srv.messageToGameKeyed(ga, true, "start.no.robots.on.server", SOCGame.MINPLAYERS);
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
                            srv.messageToGameKeyed(ga, true, m, numBots);
                        }
                        else
                        {
                            ga.setGameState(SOCGame.READY);

                            /**
                             * Fill all the unlocked empty seats with robots.
                             * Build a Vector of StringConnections of robots asked
                             * to join, and add it to the robotJoinRequests table.
                             */
                            try
                            {
                                srv.readyGameAskRobotsJoin(ga, null, numEmpty);
                            }
                            catch (IllegalStateException e)
                            {
                                System.err.println("Robot-join problem in game " + gn + ": " + e);

                                // recover, so that human players can still start a game
                                ga.setGameState(SOCGame.NEW);
                                allowStart = false;

                                gameList.takeMonitorForGame(gn);
                                srv.messageToGameKeyed(ga, false, "start.robots.cannot.join.problem", e.getMessage());
                                    // "Sorry, robots cannot join this game: {0}"
                                srv.messageToGameKeyed(ga, false, "start.to.start.without.robots");
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

        ga.releaseMonitor();
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
     * @see #handleRESETBOARDVOTE(StringConnection, SOCResetBoardVote)
     * @since 1.1.00
     */
    private void handleRESETBOARDREQUEST(StringConnection c, final SOCResetBoardRequest mes)
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
        StringConnection[] humanConns = new StringConnection[ga.maxPlayers];
        StringConnection[] robotConns = new StringConnection[ga.maxPlayers];
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
                srv.messageToPlayerKeyed(c, gaName, "resetboard.request.unlock.bot");
                    // "Please unlock at least one bot, so you will have an opponent."
            } else {
                srv.messageToGameKeyed(ga, true, "resetboard.request.everyone.left");
                    // "Everyone has left this game. Please start a new game with players or bots."
            }
        }
        else
        {
            // Probably put it to a vote.
            gameList.takeMonitorForGame(gaName);

            // First, Count number of other players who can vote (connected, version chk)
            int votingPlayers = 0;
            for (int i = ga.maxPlayers - 1; i>=0; --i)
            {
                if ((i != reqPN) && ! ga.isSeatVacant(i))
                {
                    StringConnection pc = srv.getConnection(ga.getPlayer(i).getName());
                    if ((pc != null) && pc.isConnected() && (pc.getVersion() >= 1100))
                         ++votingPlayers;
                }
            }

            if (votingPlayers == 0)
            {
                // No one else is capable of voting.
                // Reset the game immediately.
                srv.messageToGameKeyed(ga, false, "resetboard.vote.request.alloldcli", c.getData());
                    // ">>> {0} is resetting the game - other connected players are unable to vote (client too old)."

                gameList.releaseMonitorForGame(gaName);
                srv.resetBoardAndNotify(gaName, reqPN);
            }
            else
            {
                // Put it to a vote
                srv.messageToGameKeyed(ga, false, "resetboard.vote.request", c.getData());
                    // "requests a board reset - other players please vote."
                String vrCmd = SOCResetBoardVoteRequest.toCmd(gaName, reqPN);

                ga.resetVoteBegin(reqPN);

                gameList.releaseMonitorForGame(gaName);
                for (int i = 0; i < ga.maxPlayers; ++i)
                    if (humanConns[i] != null)
                        if (humanConns[i].getVersion() >= 1100)
                            humanConns[i].put(vrCmd);
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
     * @see #handleRESETBOARDREQUEST(StringConnection, SOCResetBoardRequest)
     * @see SOCServer#resetBoardAndNotify(String, int)
     * @since 1.1.00
     */
    private void handleRESETBOARDVOTE(StringConnection c, final SOCResetBoardVote mes)
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
