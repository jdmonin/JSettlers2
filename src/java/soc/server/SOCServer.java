/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2005 Chadwick A McHenry <mchenryc@acm.org>
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

import soc.debug.D;  // JM

import soc.game.*;
import soc.message.*;

import soc.robot.SOCRobotClient;
import soc.server.database.SOCDBHelper;

import soc.server.genericServer.LocalStringConnection;
import soc.server.genericServer.Server;
import soc.server.genericServer.StringConnection;

import soc.util.I18n;
import soc.util.SOCGameBoardReset;
import soc.util.SOCGameList;  // used in javadoc
import soc.util.SOCRobotParameters;
import soc.util.SOCServerFeatures;
import soc.util.SOCStringManager;
import soc.util.Triple;
import soc.util.Version;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.MessageFormat;  // used in javadocs
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

/**
 * A server for Settlers of Catan
 *
 * @author  Robert S. Thomas
 *
 * Note: This is an attempt at being more modular. 5/13/99 RST
 * Note: Hopefully fixed all of the deadlock problems. 12/27/01 RST
 *<P>
 * For server command line options, use the --help option.
 *<P>
 * If the database is used (see {@link SOCDBHelper}), users can
 * be set up with a username & password in that database to log in and play.
 * Users without accounts can connect by leaving the password blank,
 * as long as they aren't using a nickname which has a password in the database.
 * There's a database setup script parameter {@link SOCDBHelper#PROP_JSETTLERS_DB_SCRIPT_SETUP}.
 * If the setup script is ran, the server exits afterward, so that the
 * script won't be part of the command line for normal server operation.
 *<P>
 *<b>Network traffic:</b>
 * The first message over the connection is the client's version
 * and the second is the server's response:
 * Either {@link SOCRejectConnection}, or the lists of
 * channels and games ({@link SOCChannels}, {@link SOCGames}).
 *<UL>
 *<LI> See {@link SOCMessage} for details of the client/server protocol.
 *<LI> See {@link Server} for details of the server threading and processing.
 *<LI> To get a player's connection, use {@link #getConnection(Object) getConnection(plName)}.
 *<LI> To get a client's nickname, use <tt>(String)</tt> {@link StringConnection#getData() connection.getData()}.
 *<LI> To get the rest of a client's data, use ({@link SOCClientData})
 *       {@link StringConnection#getAppData() connection.getAppData()}.
 *<LI> To send a message to all players in a game, use {@link #messageToGame(String, SOCMessage)}
 *       and related methods.
 *</UL>
 *<P>
 * The server supports several <b>debug commands</b> when {@link #allowDebugUser enabled}, and
 * when sent as chat messages by a user named "debug".
 * (Or, by the only user in a practice game.)
 * See {@link #processDebugCommand(StringConnection, String, String, String)}
 * and {@link #handleGAMETEXTMSG(StringConnection, SOCGameTextMsg)}
 * for details.
 *<P>
 * The version check timer is set in {@link SOCClientData#setVersionTimer(SOCServer, StringConnection)}.
 * Before 1.1.06, the server's response was first message,
 * and client version was then sent in reply to server's version.
 *<P>
 * Java properties (starting with "jsettlers.") were added in 1.1.09, with constant names
 * starting with PROP_JSETTLERS_, and listed in {@link #PROPS_LIST}.
 */
@SuppressWarnings("serial")
public class SOCServer extends Server
{
    /**
     * Default tcp port number 8880 to listen, and for client to connect to remote server.
     * Should match SOCPlayerClient.SOC_PORT_DEFAULT.
     *<P>
     * 8880 is the default SOCPlayerClient port since jsettlers 1.0.4, per cvs history.
     * @since 1.1.09
     */
    public static final int SOC_PORT_DEFAULT = 8880;

    /**
     * Default number of bots to start (7; {@link #PROP_JSETTLERS_STARTROBOTS} property).
     * @since 1.1.19
     */
    public static final int SOC_STARTROBOTS_DEFAULT = 7;

    /**
     * Default maximum number of connected clients (40; {@link #maxConnections} field).
     * Always at least 20 more than {@link #SOC_STARTROBOTS_DEFAULT}.
     * @since 1.1.15
     */
    public static final int SOC_MAXCONN_DEFAULT = Math.max(40, 20 + SOC_STARTROBOTS_DEFAULT);

    /**
     * Filename {@code "jsserver.properties"} for the optional server startup properties file.
     * @since 1.1.20
     */
    public static final String SOC_SERVER_PROPS_FILENAME = "jsserver.properties";

    // If a new property is added, please add a PROP_JSETTLERS_ constant,
    // add it to PROPS_LIST, and update /src/bin/jsserver.properties.sample.

    /** Property <tt>jsettlers.port</tt> to specify the port the server binds to and listens on.
     * Default is {@link #SOC_PORT_DEFAULT}.
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_PORT = "jsettlers.port";

    /** Property <tt>jsettlers.connections</tt> to specify the maximum number of connections allowed.
     * Remember that robots count against this limit.
     * Default is {@link #SOC_MAXCONN_DEFAULT}.
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_CONNECTIONS = "jsettlers.connections";

    /**
     * String property <tt>jsettlers.bots.cookie</tt> to specify the robot connect cookie.
     * (By default a random one is generated.)
     * The value must pass {@link SOCMessage#isSingleLineAndSafe(String)}:
     * Must not contain the {@code '|'} or {@code ','} characters.
     * @see #PROP_JSETTLERS_BOTS_SHOWCOOKIE
     * @since 1.1.19
     */
    public static final String PROP_JSETTLERS_BOTS_COOKIE = "jsettlers.bots.cookie";

    /**
     * Boolean property <tt>jsettlers.bots.showcookie</tt> to print the
     * {@link #PROP_JSETTLERS_BOTS_COOKIE robot connect cookie} to System.err during server startup.
     * (The default is N, the cookie is not printed.)<P>
     * Format is:<P><tt>Robot cookie: 03883269284ee140cb907ea203846333</tt>
     * @since 1.1.19
     */
    public static final String PROP_JSETTLERS_BOTS_SHOWCOOKIE = "jsettlers.bots.showcookie";

    /**
     * Property <tt>jsettlers.bots.botgames.total</tt> will start robot-only games,
     * a few at a time, until this many have been played. (The default is 0.)
     *<P>
     * If this property's value != 0, a robots-only game can be started with the
     * {@code *STARTBOTGAME*} debug command. This can be used to test the bots with any given
     * combination of game options and scenarios.  To permit starting such games without
     * also starting any at server startup, use a value less than 0.
     *
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL = "jsettlers.bots.botgames.total";

    /**
     * Property <tt>jsettlers.startrobots</tt> to start some robots when the server starts.
     * (The default is {@link #SOC_STARTROBOTS_DEFAULT}.)
     *<P>
     * 30% will be "smart" robots, the other 70% will be "fast" robots.
     * Remember that robots count against the {@link #PROP_JSETTLERS_CONNECTIONS max connections} limit.
     *<P>
     * Before v1.1.19 the default was 0, no robots were started by default.
     * @since 1.1.09
     * @see #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL
     */
    public static final String PROP_JSETTLERS_STARTROBOTS = "jsettlers.startrobots";

    /**
     * Boolean property {@code jsettlers.accounts.open} to permit open registration.
     * If this property is Y, anyone can create their own user accounts.
     * Otherwise only existing users can create new accounts after the first account.
     *<P>
     * The default is N in version 1.1.19 and newer; previously was Y by default.
     * To require that all players have accounts in the database, see {@link #PROP_JSETTLERS_ACCOUNTS_REQUIRED}.
     * To restrict which users can create accounts, see {@link #PROP_JSETTLERS_ACCOUNTS_ADMINS}.
     *<P>
     * If this field is Y when the server is initialized, the server calls
     * {@link SOCServerFeatures#add(String) features.add}({@link SOCServerFeatures#FEAT_OPEN_REG}).
     * @since 1.1.19
     */
    public static final String PROP_JSETTLERS_ACCOUNTS_OPEN = "jsettlers.accounts.open";

    /**
     * Boolean property {@code jsettlers.accounts.required} to require that all players have user accounts.
     * If this property is Y, a jdbc database is required and all users must have an account and password
     * in the database. If a client tries to join or create a game or channel without providing a password,
     * they will be sent {@link SOCStatusMessage#SV_PW_REQUIRED}.
     * This property implies {@link SOCServerFeatures#FEAT_ACCTS}.
     *<P>
     * The default is N.
     *<P>
     * If {@link #PROP_JSETTLERS_ACCOUNTS_OPEN} is used, anyone can create their own account (Open Registration).
     * Otherwise see {@link #PROP_JSETTLERS_ACCOUNTS_ADMINS} for the list of user admin accounts.
     * @since 1.1.19
     */
    public static final String PROP_JSETTLERS_ACCOUNTS_REQUIRED = "jsettlers.accounts.required";

    /**
     * Property {@code jsettlers.accounts.admins} to restrict which usernames can create accounts.
     * If this property is set, it is a comma-separated list of usernames (nicknames), and
     * a user must authenticate and be on this whitelist to create user accounts.
     *<P>
     * If any other user requests account creation, the server will reply with
     * {@link SOCStatusMessage#SV_ACCT_NOT_CREATED_DENIED}.
     *<P>
     * The server doesn't require or check at startup that the named accounts all already
     * exist, this is just a list of names.
     *<P>
     * This property can't be set at the same time as {@link #PROP_JSETTLERS_ACCOUNTS_OPEN},
     * they ask for opposing security policies.
     * @since 1.1.19
     */
    public static final String PROP_JSETTLERS_ACCOUNTS_ADMINS = "jsettlers.accounts.admins";

    /**
     * Property <tt>jsettlers.allow.debug</tt> to permit debug commands over TCP.
     * (The default is N; to allow, set to Y)
     * @since 1.1.14
     */
    public static final String PROP_JSETTLERS_ALLOW_DEBUG = "jsettlers.allow.debug";

    /**
     * Property <tt>jsettlers.client.maxcreategames</tt> to limit the amount of
     * games that a client can create at once. (The default is 5.)
     * Once a game is completed and deleted (all players leave), they can create another.
     * Set this to -1 to disable it; 0 will disallow any game creation.
     * This limit is ignored for practice games.
     * @since 1.1.10
     * @see #CLIENT_MAX_CREATE_GAMES
     */
    public static final String PROP_JSETTLERS_CLI_MAXCREATEGAMES = "jsettlers.client.maxcreategames";

    /**
     * Property <tt>jsettlers.client.maxcreatechannels</tt> to limit the amount of
     * chat channels that a client can create at once. (The default is 2.)
     * Once a channel is deleted (all members leave), they can create another.
     * Set this to -1 to disable it; 0 will disallow any chat channel creation.
     * @since 1.1.10
     * @see #CLIENT_MAX_CREATE_CHANNELS
     * @see SOCServerFeatures#FEAT_CHANNELS
     */
    public static final String PROP_JSETTLERS_CLI_MAXCREATECHANNELS = "jsettlers.client.maxcreatechannels";

    /**
     * Property prefix {@code jsettlers.gameopt.} to specify game option defaults in a server properties file.
     * Option names are case-insensitive past this prefix. Syntax for default value is the same as on the
     * command line, for example:
     *<pre> jsettlers.gameopt.RD=y
     * jsettlers.gameopt.n7=t7</pre>
     *<P>
     * See {@link #parseCmdline_DashedArgs(String[])} for how game option properties are checked at startup.
     * @since 1.1.20
     */
    public static final String PROP_JSETTLERS_GAMEOPT_PREFIX = "jsettlers.gameopt.";

    /**
     * List and descriptions of all available JSettlers {@link Properties properties},
     * such as {@link #PROP_JSETTLERS_PORT} and {@link SOCDBHelper#PROP_JSETTLERS_DB_URL}.
     *<P>
     * Each property name is followed in the array by a brief description:
     * [0] is a property, [1] is its description, [2] is the next property, etc.
     * (This was added in 1.1.13 for {@link #printUsage(boolean)}).
     *<P>
     * When you add or update any property, please also update {@code /src/bin/jsserver.properties.sample}.
     * @since 1.1.09
     */
    public static final String[] PROPS_LIST =
    {
        PROP_JSETTLERS_PORT,     "TCP port number for server to listen for client connections",
        PROP_JSETTLERS_CONNECTIONS,   "Maximum connection count, including robots (default " + SOC_MAXCONN_DEFAULT + ")",
        PROP_JSETTLERS_STARTROBOTS,   "Number of robots to create at startup (default " + SOC_STARTROBOTS_DEFAULT + ")",
        PROP_JSETTLERS_ACCOUNTS_OPEN, "Permit open self-registration of new user accounts? (if Y and using a DB)",
        PROP_JSETTLERS_ACCOUNTS_REQUIRED, "Require all players to have a user account? (if Y; requires a DB)",
        PROP_JSETTLERS_ACCOUNTS_ADMINS, "Permit only these usernames to create accounts (comma-separated)",
        PROP_JSETTLERS_ALLOW_DEBUG,   "Allow remote debug commands? (if Y)",
        PROP_JSETTLERS_CLI_MAXCREATECHANNELS,   "Maximum simultaneous channels that a client can create",
        PROP_JSETTLERS_CLI_MAXCREATEGAMES,      "Maximum simultaneous games that a client can create",
        PROP_JSETTLERS_GAMEOPT_PREFIX + "*",    "Game option defaults, case-insensitive: jsettlers.gameopt.RD=y",
        // I18n.PROP_JSETTLERS_LOCALE,             "Locale override from the default, such as es or en_US, for console output",
            // -- not used yet at server
        PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL,     "Run this many robot-only games, a few at a time (default 0); allow bot-only games",
        PROP_JSETTLERS_BOTS_COOKIE,             "Robot cookie value (default is random generated each startup)",
        PROP_JSETTLERS_BOTS_SHOWCOOKIE,         "Flag to show the robot cookie value at startup",
        SOCDBHelper.PROP_JSETTLERS_DB_USER,     "DB username",
        SOCDBHelper.PROP_JSETTLERS_DB_PASS,     "DB password",
        SOCDBHelper.PROP_JSETTLERS_DB_URL,      "DB connection URL",
        SOCDBHelper.PROP_JSETTLERS_DB_JAR,      "DB driver jar filename",
        SOCDBHelper.PROP_JSETTLERS_DB_DRIVER,   "DB driver class name",
        SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP, "If set, full path or relative path to db setup sql script; will run and exit",
        SOCDBHelper.PROP_JSETTLERS_DB_SAVE_GAMES,  "Flag to save all games in DB (if 1 or Y)"
    };

    /**
     * Name used when sending messages from the server.
     */
    public static final String SERVERNAME = SOCGameTextMsg.SERVERNAME;  // "Server"

    /**
     * Minimum required client version, to connect and play a game.
     * Same format as {@link soc.util.Version#versionNumber()}.
     * Currently there is no enforced minimum (0000).
     * @see #setClientVersSendGamesOrReject(StringConnection, int, String, boolean)
     */
    public static final int CLI_VERSION_MIN = 0000;

    /**
     * Minimum required client version, in "display" form, like "1.0.00".
     * Currently there is no minimum.
     * @see #setClientVersSendGamesOrReject(StringConnection, int, String, boolean)
     */
    public static final String CLI_VERSION_MIN_DISPLAY = "0.0.00";

    /**
     * If client never tells us their version, assume they are version 1.0.0 (1000).
     * @see #CLI_VERSION_TIMER_FIRE_MS
     * @see #handleJOINGAME(StringConnection, SOCJoinGame)
     * @since 1.1.06
     */
    public static final int CLI_VERSION_ASSUMED_GUESS = 1000;

    /**
     * Client version is guessed after this many milliseconds (1200) if the client
     * hasn't yet sent it to us.
     * @see #CLI_VERSION_ASSUMED_GUESS
     * @since 1.1.06
     */
    public static final int CLI_VERSION_TIMER_FIRE_MS = 1200;

    /**
     * If game will expire in this or fewer minutes, warn the players. Default is 10.
     * Must be at least twice the sleep-time in {@link SOCGameTimeoutChecker#run()}.
     * The game expiry time is set at game creation in
     * {@link SOCGameListAtServer#createGame(String, String, String, Map, GameHandler)}.
     *<P>
     * If you update this field, also update {@link #GAME_TIME_EXPIRE_CHECK_MINUTES}.
     *<P>
     * Before v2.0.00 this field was named {@code GAME_EXPIRE_WARN_MINUTES}.
     *
     * @see #checkForExpiredGames(long)
     * @see SOCGameTimeoutChecker#run()
     * @see SOCGameListAtServer#GAME_TIME_EXPIRE_MINUTES
     * @see #GAME_TIME_EXPIRE_ADDTIME_MINUTES
     */
    public static int GAME_TIME_EXPIRE_WARN_MINUTES = 10;

    /**
     * Sleep time (minutes) between checks for expired games in {@link SOCGameTimeoutChecker#run()}.
     * Default is 5 minutes. Must be at most half of {@link #GAME_TIME_EXPIRE_WARN_MINUTES}
     * so the user has time to react after seeing the warning.
     * @since 2.0.00
     */
    public static int GAME_TIME_EXPIRE_CHECK_MINUTES = GAME_TIME_EXPIRE_WARN_MINUTES / 2;

    /**
     * Amount of time to add (30 minutes) when the {@code *ADDTIME*} command is used by a player.
     * @see #GAME_TIME_EXPIRE_WARN_MINUTES
     * @since 1.1.20
     */
    public static final int GAME_TIME_EXPIRE_ADDTIME_MINUTES = 30;
        // 30 minutes is hardcoded into some texts sent to players;
        // if you change it here, you will need to also search for those.

    /**
     * Force robot to end their turn after this many seconds
     * of inactivity. Default is 8.
     * @see #checkForExpiredTurns(long)
     * @since 1.1.11
     */
    public static int ROBOT_FORCE_ENDTURN_SECONDS = 8;

    /**
     * Maximum permitted game name length, default 30 characters.
     * Before 1.1.13, the default maximum was 20 characters.
     *
     * @see #createOrJoinGameIfUserOK(StringConnection, String, String, String, Map)
     * @since 1.1.07
     */
    public static int GAME_NAME_MAX_LENGTH = 30;

    /**
     * Maximum permitted player name length, default 20 characters.
     * The client already truncates to 20 characters in SOCPlayerClient.getValidNickname.
     *
     * @see #createOrJoinGameIfUserOK(StringConnection, String, String, String, Map)
     * @since 1.1.07
     */
    public static int PLAYER_NAME_MAX_LENGTH = 20;

    /**
     * Maximum number of games that a client can create at the same time (default 5).
     * Once this limit is reached, the client must delete a game before creating a new one.
     * Set this to -1 to disable it; 0 will disallow any game creation.
     * This limit is ignored for practice games.
     * @since 1.1.10
     * @see #PROP_JSETTLERS_CLI_MAXCREATEGAMES
     */
    public static int CLIENT_MAX_CREATE_GAMES = 5;

    /**
     * Maximum number of chat channels that a client can create at the same time (default 2).
     * Once this limit is reached, the client must delete a channel before creating a new one.
     * Set this to -1 to disable it; 0 will disallow any chat channel creation.
     *<P>
     * If this field is nonzero when the server is initialized, the server calls
     * {@link SOCServerFeatures#add(String) features.add}({@link SOCServerFeatures#FEAT_CHANNELS}).
     * If the field value is changed afterwards, that affects new clients joining the server
     * but does not clear {@code FEAT_CHANNELS} from the {@code features} list.
     *
     * @since 1.1.10
     * @see #PROP_JSETTLERS_CLI_MAXCREATECHANNELS
     */
    public static int CLIENT_MAX_CREATE_CHANNELS = 2;

    /**
     * For local practice games (pipes, not TCP), the name of the pipe.
     * Used to distinguish practice vs "real" games.
     *
     * @see soc.server.genericServer.LocalStringConnection
     */
    public static String PRACTICE_STRINGPORT = "SOCPRACTICE";

    // These AUTH_OR_REJECT constants are int not enum for backwards compatibility with 1.1.xx (java 1.4)

    /** {@link #authOrRejectClientUser(StringConnection, String, String, int, boolean, boolean) authOrRejectClientUser(....)}
     *  result: Failed authentication, failed name validation, or name is already logged in and that
     *  connection hasn't timed out yet
     *  @since 1.1.19
     */
    private static final int AUTH_OR_REJECT__FAILED = 1;

    /** {@link #authOrRejectClientUser(StringConnection, String, String, int, boolean, boolean) authOrRejectClientUser(....)}
     *  result: Authentication succeeded
     *  @since 1.1.19
     */
    private static final int AUTH_OR_REJECT__OK = 2;

    /** {@link #authOrRejectClientUser(StringConnection, String, String, int, boolean, boolean) authOrRejectClientUser(....)}
     *  result: Authentication succeeded, is taking over another connection
     *  @since 1.1.19
     */
    private static final int AUTH_OR_REJECT__TAKING_OVER = 3;

    /**
     * So we can get random numbers.
     */
    private Random rand = new Random();

    /**
     * Maximum number of connections allowed.
     * Remember that robots count against this limit.
     * Set with {@link #PROP_JSETTLERS_CONNECTIONS}.
     */
    protected int maxConnections;

    /**
     * Is a debug user allowed to run commands listed in {@link #DEBUG_COMMANDS_HELP}?
     * Default is false.  Set with {@link #PROP_JSETTLERS_ALLOW_DEBUG}.
     *<P>
     * Note that all practice games are debug mode, for ease of debugging;
     * to determine this, {@link #handleGAMETEXTMSG(StringConnection, SOCGameTextMsg)} checks if the
     * client is using {@link LocalStringConnection} to talk to the server.
     *
     * @see #processDebugCommand(StringConnection, String, String, String)
     * @since 1.1.14
     */
    private boolean allowDebugUser;

    /**
     * Properties for the server, or empty if that constructor wasn't used.
     * Property names are held in PROP_* and SOCDBHelper.PROP_* constants.
     * Some properties activate optional {@link #features}.
     * @see #SOCServer(int, Properties)
     * @see #PROPS_LIST
     * @see #getConfigBoolProperty(Properties, String, boolean)
     * @see #getConfigIntProperty(Properties, String, int)
     * @since 1.1.09
     */
    private Properties props;

    /**
     * True if {@link #props} contains a property which is used to run the server in Utility Mode
     * instead of Server Mode.  In Utility Mode the server reads its properties, initializes its
     * database connection if any, and performs one task such as a password reset or table/index creation.
     * It won't start other threads and won't fail startup if TCP port binding fails.
     *<P>
     * For a list of Utility Mode properties, see {@link #hasUtilityModeProperty()}.
     *<P>
     * This flag is set early in {@link #initSocServer(String, String, Properties)};
     * if you add a property which sets Utility Mode, update that code.
     * @see #utilityModeMessage
     * @since 1.1.20
     */
    private boolean hasUtilityModeProp;

    /**
     * If {@link #hasUtilityModeProp}, an optional status message to print before exiting, or {@code null}.
     * @since 1.1.20
     */
    private String utilityModeMessage;

    /**
     * Active optional server features, if any; see {@link SOCServerFeatures} constants for currently defined features.
     * Features are activated through the command line or {@link #props}.
     * @since 1.1.19
     */
    private SOCServerFeatures features = new SOCServerFeatures(false);

    /**
     * Game type handler, currently shared by all game instances.
     * @since 2.0.00
     */
    private final SOCGameHandler handler = new SOCGameHandler(this);

    /**
     * Server internal flag to indicate that user accounts are active, and authentication
     * is required to create accounts, and there aren't any accounts in the database yet.
     * (Server's active features include {@link SOCServerFeatures#FEAT_ACCTS} but not
     * {@link SOCServerFeatures#FEAT_OPEN_REG}.) This flag is set at startup, instead of
     * querying {@link SOCDBHelper#countUsers()} every time a client connects.
     *<P>
     * Used for signaling to {@code SOCAccountClient} that it shouldn't ask for a
     * password when connecting to create the first account, by sending the client
     * {@link SOCServerFeatures#FEAT_OPEN_REG} along with the actually active features.
     *<P>
     * The first successful account creation will clear this flag.
     *<P>
     * {@link #handleCREATEACCOUNT(StringConnection, SOCCreateAccount)} does call {@code countUsers()}
     * and requires auth if any account exists, even if this flag is set.
     * @since 1.1.19
     */
    private boolean acctsNotOpenRegButNoUsers;

    /**
     * JM temp - generated password to allow clean server shutdown.
     *   Must be used before {@link #srvShutPasswordExpire}.
     * @since 2.0.00
     */
    private String srvShutPassword;

    /**
     * JM temp - expiration of  {@link #srvShutPassword}.
     * @since 2.0.00
     */
    private long srvShutPasswordExpire;

    /**
     * Randomly generated cookie string required for robot clients to connect
     * and identify as bots using {@link SOCImARobot}.
     * It isn't sent encrypted and is a weak "shared secret".
     * Generated in {@link #generateRobotCookie()} unless the server is given
     * {@link #PROP_JSETTLERS_BOTS_COOKIE} at startup, which can set it to
     * any string or to {@code null} if the property is empty.
     *<P>
     * The value must pass {@link SOCMessage#isSingleLineAndSafe(String)}:
     * Must not contain the {@code '|'} or {@code ','} characters.
     * @since 1.1.19
     */
    private String robotCookie;

    /**
     * A list of robot {@link StringConnection}s connected to this server.
     * @see SOCLocalRobotClient#robotClients
     */
    protected Vector<StringConnection> robots = new Vector<StringConnection>();

    /**
     * Robot default parameters; copied for each newly connecting robot.
     * Changing this will not change parameters of any robots already connected.
     *
     * @see #handleIMAROBOT(StringConnection, soc.message.SOCImARobot)
     * @see soc.robot.SOCRobotDM
     */
    public static SOCRobotParameters ROBOT_PARAMS_DEFAULT
        = new SOCRobotParameters(120, 35, 0.13f, 1.0f, 1.0f, 3.0f, 1.0f, 1, 1);
        // Formerly a literal in handleIMAROBOT.
        // Strategy type 1 == SOCRobotDM.FAST_STRATEGY.
        // If you change values here, see handleIMAROBOT(..)
        // and SOCPlayerClient.startPracticeGame(..)
        // for assumptions which may also need to be changed.

    /**
     * Smarter robot default parameters. (For practice games; not referenced by server)
     * Same as ROBOT_PARAMS_DEFAULT but with SMART_STRATEGY, not FAST_STRATEGY.
     *
     * @see #ROBOT_PARAMS_DEFAULT
     * @see soc.robot.SOCRobotDM
     */
    public static SOCRobotParameters ROBOT_PARAMS_SMARTER
        = new SOCRobotParameters(120, 35, 0.13f, 1.0f, 1.0f, 3.0f, 1.0f, 0, 1);

    /**
     * Did the command line include an option that prints some information
     * (like --help or --version) and should exit, instead of starting the server?
     * Set in {@link #parseCmdline_DashedArgs(String[])}.
     * @since 1.1.15
     */
    private static boolean hasStartupPrintAndExit = false;

    /**
     * Did the properties or command line include --option / -o to set {@link SOCGameOption game option} values?
     * Checked in constructors for possible stderr option-values printout.
     * @since 1.1.07
     */
    public static boolean hasSetGameOptions = false;

    /** Status Message to send, nickname already logged into the system */
    public static final String MSG_NICKNAME_ALREADY_IN_USE
        = "Someone with that nickname is already logged into the system.";

    /**
     * Status Message to send, nickname already logged into the system.
     * Prepend to {@link #MSG_NICKNAME_ALREADY_IN_USE}.
     * The "take over" option is used for reconnect when a client loses
     * connection, and server doesn't realize it.
     * A new connection can "take over" the name after a minute's timeout.
     * @since 1.1.08
     */
    public static final String MSG_NICKNAME_ALREADY_IN_USE_WAIT_TRY_AGAIN
        = " and try again. ";

    /**
     * Part 1 of Status Message to send, nickname already logged into the system
     * with a newer client version.  Prepend to version number required.
     * The "take over" option is used for reconnect when a client loses
     * connection, and server doesn't realize it.
     * @see #MSG_NICKNAME_ALREADY_IN_USE_NEWER_VERSION_P2
     * @since 1.1.08
     */
    public static final String MSG_NICKNAME_ALREADY_IN_USE_NEWER_VERSION_P1
        = "You need client version ";

    /**
     * Part 2 of Status Message to send, nickname already logged into the system
     * with a newer client version.  Append to version number required.
     * @see #MSG_NICKNAME_ALREADY_IN_USE_NEWER_VERSION_P1
     * @since 1.1.08
     */
    public static final String MSG_NICKNAME_ALREADY_IN_USE_NEWER_VERSION_P2
        = " or newer to take over this connection.";

    /**
     * Number of seconds before a connection is considered disconnected, and
     * its nickname can be "taken over" by a new connection with the right password.
     * Used only when a password is given by the new connection.
     * @see #checkNickname(String, StringConnection, boolean)
     * @since 1.1.08
     */
    public static final int NICKNAME_TAKEOVER_SECONDS_SAME_PASSWORD = 15;

    /**
     * Number of seconds before a connection is considered disconnected, and
     * its nickname can be "taken over" by a new connection from the same IP.
     * Used when no password is given by the new connection.
     * @see #checkNickname(String, StringConnection, boolean)
     * @since 1.1.08
     */
    public static final int NICKNAME_TAKEOVER_SECONDS_SAME_IP = 30;

    /**
     * Number of seconds before a connection is considered disconnected, and
     * its nickname can be "taken over" by a new connection from a different IP.
     * Used when no password is given by the new connection.
     * @see #checkNickname(String, StringConnection, boolean)
     * @since 1.1.08
     */
    public static final int NICKNAME_TAKEOVER_SECONDS_DIFFERENT_IP = 150;

    /**
     * list of chat channels
     *<P>
     * Instead of calling {@link SOCChannelList#deleteChannel(String)},
     * call {@link #destroyChannel(String)} to also clean up related server data.
     */
    protected SOCChannelList channelList = new SOCChannelList();

    /**
     * list of soc games
     */
    protected SOCGameListAtServer gameList = new SOCGameListAtServer();

    /**
     * table of requests for robots to join games
     */
    protected Hashtable<String, Vector<StringConnection>> robotJoinRequests
        = new Hashtable<String, Vector<StringConnection>>();

    /**
     * table of requests for robots to leave games
     */
    protected Hashtable<String, Vector<SOCReplaceRequest>> robotDismissRequests
        = new Hashtable<String, Vector<SOCReplaceRequest>>();

    ///**
    // * table of game data files
    // */
    //protected Hashtable gameDataFiles = new Hashtable();
    //
    ///**
    // * the current game event record
    // */
    //protected SOCGameEventRecord currentGameEventRecord;

    /**
     * the time that this server was started
     */
    protected long startTime;

    /**
     * The total number of games that have been started:
     * {@link GameHandler#startGame(SOCGame)} has been called
     * and game play has begun. Game state became {@link SOCGame#READY}
     * or higher from an earlier/lower state.
     */
    protected int numberOfGamesStarted;

    /**
     * The total number of games finished: Game state became {@link SOCGame#OVER} or higher
     * from an earlier/lower state. Incremented in {@link #gameOverIncrGamesFinishedCount()}.
     *<P>
     * Before v1.1.20 this was the number of games destroyed, and {@code *STATS*}
     * wouldn't reflect a newly finished game until all players had left that game.
     */
    protected int numberOfGamesFinished;

    /**
     * Synchronization for {@link #numberOfGamesFinished} writes.
     * @since 2.0.00
     */
    private Object countFieldSync = new Object();

    /**
     * total number of users
     */
    protected int numberOfUsers;

    /**
     * Client version count stats since startup (includes bots).
     * Incremented from {@link #handleVERSION(StringConnection, SOCVersion)};
     * currently assumes single-threaded access to this map.
     *<P>
     * Key = version number, Value = client count.
     * @since 1.1.19
     */
    protected HashMap<Integer, Integer> clientPastVersionStats;

    /**
     * Number of robot-only games not yet started (optional feature).
     * Set at startup from {@link #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL}.
     * @since 2.0.00
     */
    private int numRobotOnlyGamesRemaining;

    /**
     * Description string for SOCGameOption {@code "PL"} hardcoded into the SOCGameOption class,
     * from {@link SOCGameOption#getOption(String, boolean) SOCGameOption.getOption("PL", false)}.
     * Used for determining whether a client's i18n locale has localized option descriptions,
     * by comparing {@code PL}'s {@link SOCVersionedItem#getDesc() SOCGameOption.desc} to
     * StringManager.get({@code "gameopt.PL"}).
     *<P>
     * String value is captured here as soon as SOCServer is referenced, in case SOCPlayerClient's
     * practice server will localize the descriptions used by {@link SOCGameOption#getOption(String, boolean)}.
     * @since 2.0.00
     * @see {@link #i18n_scenario_SC_WOND_desc}
     */
    private final static String i18n_gameopt_PL_desc;
    static
    {
        final SOCGameOption optPL = SOCGameOption.getOption("PL", false);
        i18n_gameopt_PL_desc = (optPL != null) ? optPL.getDesc() : "";
    }

    /**
     * Short description string for SOCScenario {@code "SC_WOND"} hardcoded into the SOCScenario class.
     * Used for determining whether a client's i18n locale has localized scenario descriptions,
     * by comparing {@code SC_WOND}'s {@link SOCVersionedItem#getDesc() SOCScenario.desc} to
     * StringManager.get({@code "gamescen.SC_WOND.n"}).
     *<P>
     * String value is captured here as soon as SOCServer is referenced, in case SOCPlayerClient's
     * practice server will localize the scenario descriptions.
     *
     * @see #clientHasLocalizedStrs_gameScenarios(StringConnection)
     * @since 2.0.00
     * @see #i18n_gameopt_PL_desc
     */
    final static String i18n_scenario_SC_WOND_desc;
    static
    {
        final SOCScenario scWond = SOCScenario.getScenario(SOCScenario.K_SC_WOND);
        i18n_scenario_SC_WOND_desc = (scWond != null) ? scWond.getDesc() : "";
    }


    /**
     * server robot pinger
     */
    SOCServerRobotPinger serverRobotPinger;

    /**
     * game timeout checker
     */
    SOCGameTimeoutChecker gameTimeoutChecker;

    String databaseUserName;
    String databasePassword;

    /**
     * User admins whitelist, from {@link #PROP_JSETTLERS_ACCOUNTS_ADMINS}, or {@code null} if disabled.
     * If not null, only usernames on this list can create user accounts in
     * {@link #handleCREATEACCOUNT(StringConnection, SOCCreateAccount)}.
     * @since 1.1.19
     */
    private Set<String> databaseUserAdmins;

    /**
     * Create a Settlers of Catan server listening on TCP port {@code p}.
     * Most server threads are started here; you must start its main thread yourself.
     * Optionally connect to a database for user info and game stats.
     *<P>
     * No bots will be started here ({@link #PROP_JSETTLERS_STARTROBOTS} == 0),
     * call {@link #setupLocalRobots(int, int)} if bots are wanted.
     *<P>
     * In 1.1.07 and later, will also print game options to stderr if
     * any option defaults require a minimum client version, or if
     * {@link #hasSetGameOptions} is set.
     *
     * @param p    the TCP port that the server listens on
     * @param mc   the maximum number of connections allowed;
     *            remember that robots count against this limit.
     * @param databaseUserName  the user name for accessing the database
     * @param databasePassword  the password for the db user, or ""
     * @throws SocketException  If a network setup problem occurs
     * @throws EOFException   If db setup script ran successfully and server should exit now
     * @throws SQLException   If db setup script fails
     */
    public SOCServer(int p, int mc, String databaseUserName, String databasePassword)
        throws SocketException, EOFException, SQLException
    {
        super(p, new SOCMessageDispatcher());

        maxConnections = mc;
        initSocServer(databaseUserName, databasePassword, null);
    }

    /**
     * Create a Settlers of Catan server listening on TCP port {@code p}.
     * Most server threads are started here; you must start its main thread yourself.
     * Optionally connect to a database for user info and game stats.
     *<P>
     * The database properties are {@link SOCDBHelper#PROP_JSETTLERS_DB_USER}
     * and {@link SOCDBHelper#PROP_JSETTLERS_DB_PASS}.
     *<P>
     * To run a DB setup script to create database tables, send its filename
     * or relative path as {@link SOCDBHelper#PROP_JSETTLERS_DB_SCRIPT_SETUP}.
     *<P>
     * If a db URL or other DB properties are specified in {@code props}, but {@code SOCServer}
     * can't successfully connect to that database, this constructor throws {@code SQLException};
     * for details see {@link #initSocServer(String, String, Properties)}.
     * Other constructors can't set those properties, and will instead
     * continue {@code SOCServer} startup and run without any database.
     *<P>
     * Will also print game options to stderr if
     * any option defaults require a minimum client version, or if
     * {@link #hasSetGameOptions} is set.
     *
     *<H3>Utility Mode:</H3>
     * Some properties such as {@link SOCDBHelper#PROP_JSETTLERS_DB_SCRIPT_SETUP}
     * will initialize the server environment, connect to the database, perform
     * a single task, and exit.  This is called <B>Utility Mode</B>.  In Utility Mode
     * the caller should not start threads or continue normal startup (Server Mode).
     * See {@link #hasUtilityModeProperty()} for more details.
     *<P>
     * For the password reset property {@link SOCDBHelper#PROP_IMPL_JSETTLERS_PW_RESET}, the
     * caller will need to prompt for and change the password; this constructor will not do that.
     *
     * @param p    the TCP port that the server listens on
     * @param props  null, or properties containing {@link #PROP_JSETTLERS_CONNECTIONS}
     *       and any other desired properties. If {@code props} contains game option default values
     *       (see below) with non-uppercase gameopt names, cannot be read-only: Startup will
     *       replace keys such as {@code "jsettlers.gameopt.vp"} with their canonical
     *       uppercase equivalent: {@code "jsettlers.gameopt.VP"}
     *       <P>
     *       If {@code props} is null, the properties will be created empty
     *       and no bots will be started ({@link #PROP_JSETTLERS_STARTROBOTS} == 0).
     *       If {@code props} != null but doesn't contain {@link #PROP_JSETTLERS_STARTROBOTS},
     *       the default value {@link #SOC_STARTROBOTS_DEFAULT} will be used.
     *       <P>
     *       {@code props} may contain game option default values (property names starting
     *       with {@link #PROP_JSETTLERS_GAMEOPT_PREFIX}).
     *       Calls {@link #parseCmdline_GameOption(SOCGameOption, String, HashMap)}
     *       for each one found, to set its default (current) value.  If a default scenario is
     *       specified (game option {@code "SC"}), the scenario may include game options which
     *       conflict with those in {@code props}: Consider calling {@link #checkScenarioOpts(Map, boolean, String)}
     *       to check for that and warn the user.
     *       <P>
     *       If you provide {@code props}, consider checking for a {@code jsserver.properties} file
     *       ({@link #SOC_SERVER_PROPS_FILENAME}) and calling {@link Properties#load(java.io.InputStream)}
     *       with it before calling this constructor.
     * @since 1.1.09
     * @throws SocketException  If a network setup problem occurs
     * @throws EOFException   If db setup script ran successfully and server should exit now
     * @throws SQLException   If db setup script fails, or need db but can't connect
     * @throws IllegalArgumentException  If {@code props} contains game options ({@code jsettlers.gameopt.*})
     *       with bad syntax. See {@link #PROP_JSETTLERS_GAMEOPT_PREFIX} for expected syntax.
     *       See {@link #parseCmdline_DashedArgs(String[])} for how game option properties are checked.
     *       {@link Throwable#getMessage()} will have problem details.
     * @see #PROPS_LIST
     */
    public SOCServer(final int p, Properties props)
        throws SocketException, EOFException, SQLException, IllegalArgumentException
    {
        super(p, new SOCMessageDispatcher());

        maxConnections = getConfigIntProperty(props, PROP_JSETTLERS_CONNECTIONS, SOC_MAXCONN_DEFAULT);
        allowDebugUser = getConfigBoolProperty(props, PROP_JSETTLERS_ALLOW_DEBUG, false);
        CLIENT_MAX_CREATE_GAMES = getConfigIntProperty(props, PROP_JSETTLERS_CLI_MAXCREATEGAMES, CLIENT_MAX_CREATE_GAMES);
        CLIENT_MAX_CREATE_CHANNELS = getConfigIntProperty(props, PROP_JSETTLERS_CLI_MAXCREATECHANNELS, CLIENT_MAX_CREATE_CHANNELS);
        String dbuser = props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_USER, "socuser");
        String dbpass = props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_PASS, "socpass");
        initSocServer(dbuser, dbpass, props);
    }

    /**
     * Create a Settlers of Catan server listening on local stringport {@code s}.
     * Most server threads are started here; you must start its main thread yourself.
     * Optionally connect to a database for user info and game stats.
     *<P>
     * No bots will be started here ({@link #PROP_JSETTLERS_STARTROBOTS} == 0),
     * call {@link #setupLocalRobots(int, int)} if bots are wanted.
     *<P>
     * In 1.1.07 and later, will also print game options to stderr if
     * any option defaults require a minimum client version, or if
     * {@link #hasSetGameOptions} is set.
     *
     * @param s    the stringport that the server listens on.
     *             If this is a "practice game" server on the user's local computer,
     *             please use {@link #PRACTICE_STRINGPORT}.
     * @param mc   the maximum number of connections allowed;
     *            remember that robots count against this limit.
     * @param databaseUserName  the user name for accessing the database
     * @param databasePassword  the password for the user
     * @throws SocketException  If a network setup problem occurs
     * @throws EOFException   If db setup script ran successfully and server should exit now
     * @throws SQLException   If db setup script fails
     */
    public SOCServer(String s, int mc, String databaseUserName, String databasePassword)
        throws SocketException, EOFException, SQLException
    {
        super(s, new SOCMessageDispatcher());

        maxConnections = mc;
        initSocServer(databaseUserName, databasePassword, null);
    }

    /**
     * Common init for all constructors.
     * Prints some progress messages to {@link System#err}.
     * Sets game option default values via {@link #init_propsSetGameopts(Properties)}.
     * Calls {@link SOCMessageDispatcher#setServer(SOCServer, SOCGameListAtServer)}.
     * Starts all server threads except the main thread, unless constructed in Utility Mode
     * ({@link #hasUtilityModeProp}).
     * If {@link #PROP_JSETTLERS_STARTROBOTS} is specified, those aren't started until {@link #serverUp()}.
     *<P>
     * If there are problems with the network setup ({@link #error} != null),
     * this method will throw {@link SocketException}.
     *<P>
     * If problems running a {@link SOCDBHelper#PROP_JSETTLERS_DB_SCRIPT_SETUP db setup script},
     * this method will throw {@link SQLException}.
     *<P>
     * If we can't connect to a database, but it looks like we need one (because
     * {@link SOCDBHelper#PROP_JSETTLERS_DB_URL}, {@link SOCDBHelper#PROP_JSETTLERS_DB_DRIVER}
     * or {@link SOCDBHelper#PROP_JSETTLERS_DB_JAR} is specified in {@code props}),
     * this method will throw {@link SQLException}.
     *
     *<H5>Utility Mode</H5>
     * If a db setup script runs successfully, or {@code props} contains the password reset parameter
     * {@link SOCDBHelper#PROP_IMPL_JSETTLERS_PW_RESET}, the server does not complete its startup;
     * this method will set {@link #hasUtilityModeProp} and (only for setup script) throw {@link EOFException}.
     *<P>
     * For the password reset parameter, the caller will need to prompt for and change the password;
     * this method will not do that.
     *
     * @param databaseUserName Used for DB connect - not retained
     * @param databasePassword Used for DB connect - not retained
     * @param props  null, or properties containing {@link #PROP_JSETTLERS_CONNECTIONS}
     *       and any other desired properties. If {@code props} contains game option default values
     *       (see below) with non-uppercase gameopt names, cannot be read-only: Startup will
     *       replace keys such as {@code "jsettlers.gameopt.vp"} with their canonical
     *       uppercase equivalent: {@code "jsettlers.gameopt.VP"}
     *       <P>
     *       If {@code props} is null, the properties will be created empty
     *       and no bots will be started ({@link #PROP_JSETTLERS_STARTROBOTS} == 0).
     *       If {@code props} != null but doesn't contain {@link #PROP_JSETTLERS_STARTROBOTS},
     *       the default value {@link #SOC_STARTROBOTS_DEFAULT} will be used.
     *       <P>
     *       {@code props} may contain game option default values (property names starting
     *       with {@link #PROP_JSETTLERS_GAMEOPT_PREFIX}).
     *       Calls {@link #parseCmdline_GameOption(SOCGameOption, String, HashMap)}
     *       for each one found, to set its default (current) value. If a default scenario is
     *       specified (game option {@code "SC"}), the scenario may include game options which
     *       conflict with those in {@code props}: Consider calling {@link #checkScenarioOpts(Map, boolean, String)}
     *       to check for that and warn the user.
     * @throws SocketException  If a network setup problem occurs
     * @throws EOFException   If db setup script ran successfully and server should exit now;
     *       thrown in Utility Mode ({@link #hasUtilityModeProp}).
     * @throws SQLException   If db setup script fails, or need db but can't connect
     * @throws IllegalArgumentException  If {@code props} contains game options ({@code jsettlers.gameopt.*})
     *       with bad syntax. See {@link #PROP_JSETTLERS_GAMEOPT_PREFIX} for expected syntax.
     *       See {@link #parseCmdline_DashedArgs(String[])} for how game option properties are checked.
     *       {@link Throwable#getMessage()} will have problem details.
     */
    private void initSocServer(String databaseUserName, String databasePassword, Properties props)
        throws SocketException, EOFException, SQLException, IllegalArgumentException
    {
        Version.printVersionText(System.err, "Java Settlers Server ");

        // Set this flag as early as possible
        hasUtilityModeProp = (props != null)
            && ((null != props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP))
                || (null != props.getProperty(SOCDBHelper.PROP_IMPL_JSETTLERS_PW_RESET)));

        /* Check for problems during super setup (such as port already in use).
         * Ignore net errors if we're running a DB setup script and then exiting.
         */
        if ((error != null) && ! hasUtilityModeProp)
        {
            final String errMsg = "* Exiting due to network setup problem: " + error.toString();
            throw new SocketException(errMsg);
        }

        if (props == null)
        {
            props = new Properties();
        } else {
            // Add any default properties if not specified.

            if (! props.containsKey(PROP_JSETTLERS_STARTROBOTS))
                props.setProperty(PROP_JSETTLERS_STARTROBOTS, Integer.toString(SOC_STARTROBOTS_DEFAULT));

            // Set game option defaults from any jsettlers.gameopt.* properties found.
            // If problems found, throws IllegalArgumentException with details.
            // Ignores unknown scenario ("SC"), see init_checkScenarioOpts for that.
            init_propsSetGameopts(props);
        }

        this.props = props;
        ((SOCMessageDispatcher) inboundMsgDispatcher).setServer(this, gameList);

        if (allowDebugUser)
        {
            System.err.println("Warning: Remote debug commands are allowed.");
        }

        /**
         * See if the user specified a non-random robot cookie value.
         */
        if (props.containsKey(PROP_JSETTLERS_BOTS_COOKIE))
        {
            final String cook = props.getProperty(PROP_JSETTLERS_BOTS_COOKIE).trim();
            if (cook.length() > 0)
            {
                if (SOCMessage.isSingleLineAndSafe(cook))
                {
                    robotCookie = cook;
                } else {
                    final String errmsg = "Error: The robot cookie value (param " + PROP_JSETTLERS_BOTS_COOKIE
                        + ") can't contain comma or pipe characters.";
                    System.err.println(errmsg);
                    throw new IllegalArgumentException(errmsg);
                }
            }
            // else robotCookie remains null
        } else {
            robotCookie = generateRobotCookie();
        }

        /**
         * Try to connect to the DB, if any.
         */
        try
        {
            SOCDBHelper.initialize(databaseUserName, databasePassword, props);
            features.add(SOCServerFeatures.FEAT_ACCTS);
            System.err.println("User database initialized.");

            if (props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP) != null)
            {
                // the sql script was ran by initialize

                final String msg = "DB setup script successful";
                utilityModeMessage = msg;
                throw new EOFException(msg);
            }

            // reminder: if props.getProperty(SOCDBHelper.PROP_IMPL_JSETTLERS_PW_RESET),
            // caller will need to prompt for and change the password

            // open reg for user accounts?  if not, see if we have any yet
            if (getConfigBoolProperty(props, PROP_JSETTLERS_ACCOUNTS_OPEN, false))
            {
                features.add(SOCServerFeatures.FEAT_OPEN_REG);
                if (! hasUtilityModeProp)
                    System.err.println("User database Open Registration is active, anyone can create accounts.");
            } else {
                if (SOCDBHelper.countUsers() == 0)
                    acctsNotOpenRegButNoUsers = true;
            }
        }
        catch (SQLException sqle) // just a warning
        {
            System.err.println("No user database available: " + sqle.getMessage());
            Throwable cause = sqle.getCause();

            while ((cause != null) && ! (cause instanceof ClassNotFoundException))
            {
                System.err.println("\t" + cause);
                cause = cause.getCause();
            }

            if (props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP) != null)
            {
                // the sql script was ran by initialize, but failed to complete;
                // don't continue server startup with just a warning
                throw sqle;
            }

            if (props.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_URL)
                || props.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_JAR)
                || props.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_DRIVER))
            {
                // If other db props were asked for, the user is expecting a DB.
                // So, fail instead of silently continuing without it.
                System.err.println("* Exiting because current startup properties specify a database.");
                throw sqle;
            }

            if (props.containsKey(SOCDBHelper.PROP_IMPL_JSETTLERS_PW_RESET))
            {
                System.err.println("* Exiting because --pw-reset requires a database.");
                throw sqle;
            }

            System.err.println("Users will not be authenticated.");
        }
        catch (EOFException eox)  // successfully ran script, signal to exit
        {
            throw eox;
        }
        catch (IOException iox) // error from requested script
        {
            System.err.println("\n* Could not run database setup script: " + iox.getMessage());
            Throwable cause = iox.getCause();
            while ((cause != null) && ! (cause instanceof ClassNotFoundException))
            {
                System.err.println("\t" + cause);
                cause = cause.getCause();
            }

            try
            {
                SOCDBHelper.cleanup(true);
            }
            catch (SQLException x) { }

            SQLException sqle = new SQLException("Error running DB setup script");
            sqle.initCause(iox);
            throw sqle;
        }

        // No errors; continue normal startup.

        if (hasUtilityModeProp)
        {
            return;  // <--- don't continue startup if Utility Mode ---
        }

        final boolean accountsRequired = getConfigBoolProperty(props, PROP_JSETTLERS_ACCOUNTS_REQUIRED, false);

        if (SOCDBHelper.isInitialized())
        {
            if (accountsRequired)
                System.err.println("User database accounts are required for all players.");

            // Note: This hook is not triggered under eclipse debugging.
            //    https://bugs.eclipse.org/bugs/show_bug.cgi?id=38016  "WONTFIX/README"
            try
            {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        System.err.println("\n--\n-- shutdown; disconnecting from db --\n--\n");
                        System.err.flush();
                        try
                        {
                            SOCDBHelper.cleanup(true);
                        }
                        catch (SQLException x) { }
                    }
                });
            } catch (Throwable th)
            {
                // just a warning
                System.err.println("Warning: Could not register shutdown hook for database disconnect. Check java security settings.");
            }
        }
        else if (accountsRequired)
        {
            final String errmsg = "* Property " + PROP_JSETTLERS_ACCOUNTS_REQUIRED + " requires a database.";
            System.err.println(errmsg);
            throw new IllegalArgumentException(errmsg);
        }

        startTime = System.currentTimeMillis();
        numberOfGamesStarted = 0;
        numberOfGamesFinished = 0;
        numberOfUsers = 0;
        clientPastVersionStats = new HashMap<Integer, Integer>();
        numRobotOnlyGamesRemaining = getConfigIntProperty(props, PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL, 0);
        if (numRobotOnlyGamesRemaining > 0)
        {
                final int n = SOCGame.MAXPLAYERS_STANDARD;
                if (n > getConfigIntProperty(props, PROP_JSETTLERS_STARTROBOTS, 0))
                {
                    final String errmsg =
                        ("*** To start robot-only games, server needs at least " + n + " robots started.");
                    System.err.println(errmsg);
                    throw new IllegalArgumentException(errmsg);
                }
        }

        if (CLIENT_MAX_CREATE_CHANNELS != 0)
            features.add(SOCServerFeatures.FEAT_CHANNELS);

        if (props.containsKey(PROP_JSETTLERS_ACCOUNTS_ADMINS))
        {
            String errmsg = null;

            final String userAdmins = props.getProperty(PROP_JSETTLERS_ACCOUNTS_ADMINS);
            if (! SOCDBHelper.isInitialized())
            {
                errmsg = "* Property " + PROP_JSETTLERS_ACCOUNTS_ADMINS + " requires a database.";
            } else if (userAdmins.length() == 0) {
                errmsg = "* Property " + PROP_JSETTLERS_ACCOUNTS_ADMINS + " cannot be an empty string.";
            } else if (features.isActive(SOCServerFeatures.FEAT_OPEN_REG)) {
                errmsg = "* Cannot use Open Registration with User Accounts Admin List.";
            } else {
                databaseUserAdmins = new HashSet<String>();
                for (String adm : userAdmins.split(SOCMessage.sep2))  // split on "," - sep2 will never be in a username
                {
                    String na = adm.trim();
                    if (na.length() > 0)
                        databaseUserAdmins.add(na);
                }
                if (databaseUserAdmins.isEmpty())  // was it commas only?
                    errmsg = "* Property " + PROP_JSETTLERS_ACCOUNTS_ADMINS + " cannot be an empty list.";
            }

            if (errmsg != null)
            {
                System.err.println(errmsg);
                throw new IllegalArgumentException(errmsg);
            }

            System.err.println("User account administrators limited to: " + userAdmins);
            if (acctsNotOpenRegButNoUsers)
                System.err.println("** User database is currently empty: Run SOCAccountClient to create the user admin account(s) named above.");
        }
        else if (acctsNotOpenRegButNoUsers)
        {
            if (accountsRequired)
                System.err.println("** User database is currently empty. You must run SOCAccountClient to create users.");
            else
                System.err.println("User database is currently empty. You can run SOCAccountClient to create users.");
        }

        /**
         * Start various threads.
         */
        serverRobotPinger = new SOCServerRobotPinger(this, robots);
        serverRobotPinger.start();
        gameTimeoutChecker = new SOCGameTimeoutChecker(this);
        gameTimeoutChecker.start();
        this.databaseUserName = databaseUserName;
        this.databasePassword = databasePassword;

        /**
         * Print game options if we've set them on commandline, or if
         * any option defaults require a minimum client version.
         */
        if (hasSetGameOptions || (SOCVersionedItem.itemsMinimumVersion(SOCGameOption.getAllKnownOptions()) > -1))
        {
            Thread.yield();  // wait for other output to appear first
            try { Thread.sleep(200); } catch (InterruptedException ie) {}

            printGameOptions();
        }

        if (getConfigBoolProperty(props, PROP_JSETTLERS_BOTS_SHOWCOOKIE, false))
            System.err.println("Robot cookie: " + robotCookie);

        System.err.print("The server is ready.");
        if (port > 0)
            System.err.print(" Listening on port " + port);
        System.err.println();
        System.err.println();
    }

    /**
     * Get and parse an integer config property, or use its default instead.
     *<P>
     * Before v2.0.00, this method was <tt>init_getIntProperty</tt>.
     *
     * @param props  Properties to look in, such as {@link SOCServer#props}, or null for <tt>pDefault</tt>
     * @param pName  Property name
     * @param pDefault  Default value to use if not found or not parsable
     * @return The property's parsed integer value, or <tt>pDefault</tt>
     * @since 1.1.10
     * @see #getConfigBoolProperty(Properties, String, boolean)
     */
    private static int getConfigIntProperty(Properties props, final String pName, final int pDefault)
    {
        if (props == null)
            return pDefault;

        try
        {
            String mcs = props.getProperty(pName, Integer.toString(pDefault));
            if (mcs != null)
                return Integer.parseInt(mcs);
        }
        catch (NumberFormatException e) { }

        return pDefault;
    }

    /**
     * Get and parse a boolean config property, or use its default instead.
     * True values are: T Y 1.
     * False values are: F N 0.
     * Not case-sensitive.
     * Any other value will be ignored and get <tt>pDefault</tt>.
     * @param props  Properties to look in, such as {@link SOCServer#props}, or null for <tt>pDefault</tt>
     *<P>
     * Before v2.0.00, this method was <tt>init_getBoolProperty</tt>.
     *
     * @param pName  Property name
     * @param pDefault  Default value to use if not found or not parsable
     * @return The property's parsed value, or <tt>pDefault</tt>
     * @since 1.1.14
     * @see #getConfigIntProperty(Properties, String, int)
     */
    private static boolean getConfigBoolProperty(Properties props, final String pName, final boolean pDefault)
    {
        if (props == null)
            return pDefault;

        try
        {
            String mcs = props.getProperty(pName);
            if (mcs == null)
                return pDefault;
            if (mcs.equalsIgnoreCase("Y") || mcs.equalsIgnoreCase("T"))
                return true;
            else if (mcs.equalsIgnoreCase("N") || mcs.equalsIgnoreCase("F"))
                return false;

            final int iv = Integer.parseInt(mcs);
            if (iv == 0)
                return false;
            else if (iv == 1)
                return true;
        }
        catch (NumberFormatException e) { }

        return pDefault;
    }

    /**
     * Callback to take care of things when server comes up, after the server socket
     * is bound and listening, in the main thread.
     * If {@link #PROP_JSETTLERS_STARTROBOTS} is specified, starts those {@link SOCRobotClient}s now.
     * If {@link #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL} is specified, waits briefly and then
     * calls {@link #startRobotOnlyGames(boolean)}.
     *<P>
     * Once this method completes, server begins its main loop of listening for incoming
     * client connections, and starting a Thread for each one to handle that client's messages.
     *
     * @throws IllegalStateException If server was constructed in Utility Mode and shouldn't continue
     *    normal startup; see {@link #hasUtilityModeProperty()} for details.
     * @since 1.1.09
     */
    @Override
    public void serverUp()
        throws IllegalStateException
    {
        if (hasUtilityModeProp)
            throw new IllegalStateException();

        /**
         * If we have any STARTROBOTS, start them up now.
         * Each bot will have its own thread and {@link SOCRobotClient}.
         */
        if ((props != null) && (props.containsKey(PROP_JSETTLERS_STARTROBOTS)))
        {
            try
            {
                final int rcount = Integer.parseInt(props.getProperty(PROP_JSETTLERS_STARTROBOTS));
                final int hcount = maxConnections - rcount;  // max human client connection count
                int fast30 = (int) (0.30f * rcount);
                boolean loadSuccess = setupLocalRobots(fast30, rcount - fast30);  // each bot gets a thread
                if (! loadSuccess)
                {
                    System.err.println("** Cannot start robots with this JAR.");
                    System.err.println("** For robots, please use the Full JAR instead of the server-only JAR.");
                }
                else if ((hcount < 6) || (hcount < rcount))
                {
                    new Thread() {
                        @Override
                        public void run()
                        {
                            try {
                                Thread.sleep(1600);  // wait for bot-connect messages to print
                            } catch (InterruptedException e) {}
                            System.err.println("** Warning: Only " + hcount
                                + " player connections available, because of the robot connections.");
                        }
                    }.start();
                }

                if (numRobotOnlyGamesRemaining > 0)
                {
                    final int n = SOCGame.MAXPLAYERS_STANDARD;
                    if (n > rcount)
                    {
                        // This message is a backup: initSocServer should have already errored on this during startup.
                        System.err.println
                            ("** To start robot-only games, server needs at least " + n +  " robots started.");
                    } else {
                        new Thread() {
                            @Override
                            public void run()
                            {
                                try {
                                    Thread.sleep(1600);  // wait for bots to connect
                                } catch (InterruptedException e) {}

                                startRobotOnlyGames(false);
                            }
                        }.start();
                    }
                }
            }
            catch (NumberFormatException e)
            {
                System.err.println("Not starting robots: Bad number format, ignoring property " + PROP_JSETTLERS_STARTROBOTS);
            }
        }
    }

    /**
     * The 16 hex characters to use in {@link #generateRobotCookie()}.
     * @since 1.1.19
     */
    private final static char[] GENERATEROBOTCOOKIE_HEX
        = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    /**
     * Generate and return a string to use for {@link #robotCookie}.
     * Currently a lowercase hex string; format or length does not have to be compatible
     * between versions.  The contents are randomly generated for each server run.
     * @return Robot connect cookie contents to use for this server
     * @since 1.1.19
     */
    private final String generateRobotCookie()
    {
        byte[] rnd = new byte[16];
        rand.nextBytes(rnd);
        char[] rndChars = new char[2 * 16];
        int ic = 0;  // index into rndChars
        for (int i = 0; i < 16; ++i)
        {
            final int byt = rnd[i] & 0xFF;
            rndChars[ic] = GENERATEROBOTCOOKIE_HEX[byt >>> 4];   ++ic;
            rndChars[ic] = GENERATEROBOTCOOKIE_HEX[byt & 0x0F];  ++ic;
        }

        return new String(rndChars);
    }

    /**
     * Adds a connection to a chat channel.
     *
     * WARNING: MUST HAVE THE channelList.takeMonitorForChannel(ch)
     * before calling this method
     *
     * @param c    the Connection to be added
     * @param ch   the name of the channel
     *
     */
    public void connectToChannel(StringConnection c, String ch)
    {
        if (c == null)
            return;

        if (channelList.isChannel(ch))
        {
            if (!channelList.isMember(c, ch))
            {
                c.put(SOCMembers.toCmd(ch, channelList.getMembers(ch)));
                if (D.ebugOn)
                    D.ebugPrintln("*** " + c.getData() + " joined the channel " + ch + " at "
                        + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
                channelList.addMember(c, ch);
            }
        }
    }

    /**
     * Connection {@code c} leaves the channel {@code ch}.
     * If the channel becomes empty after removing {@code c}, this method can destroy it.
     *<P>
     * <B>Locks:</B> Must have {@link SOCChannelList#takeMonitorForChannel(String) channelList.takeMonitorForChannel(ch)}
     * when calling this method.
     * May or may not have {@link SOCChannelList#takeMonitor()}, see {@code channelListLock} parameter.
     *
     * @param c  the connection
     * @param ch the channel
     * @param destroyIfEmpty  if true, this method will destroy the channel if it's now empty.
     *           If false, the caller must call {@link #destroyChannel(String)}
     *           before calling {@link SOCChannelList#releaseMonitor()}.
     * @param channelListLock  true if we have the {@link SOCChannelList#takeMonitor()} lock
     *           when called; false if it must be acquired and released within this method
     * @return true if we destroyed the channel, or if it would have been destroyed but {@code destroyIfEmpty} is false.
     */
    public boolean leaveChannel
        (final StringConnection c, final String ch, final boolean destroyIfEmpty, final boolean channelListLock)
    {
        if (c == null)
            return false;

        D.ebugPrintln("leaveChannel: " + c.getData() + " " + ch + " " + channelListLock);

        if (channelList.isMember(c, ch))
        {
            channelList.removeMember(c, ch);

            SOCLeave leaveMessage = new SOCLeave((String) c.getData(), c.host(), ch);
            messageToChannelWithMon(ch, leaveMessage);
            if (D.ebugOn)
                D.ebugPrintln("*** " + c.getData() + " left the channel " + ch + " at "
                    + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
        }

        final boolean isEmpty = channelList.isChannelEmpty(ch);
        if (isEmpty && destroyIfEmpty)
        {
            if (channelListLock)
            {
                destroyChannel(ch);
            }
            else
            {
                channelList.takeMonitor();

                try
                {
                    destroyChannel(ch);
                }
                catch (Exception e)
                {
                    D.ebugPrintStackTrace(e, "Exception in leaveChannel");
                }

                channelList.releaseMonitor();
            }
        }

        return isEmpty;
    }

    /**
     * Destroy a channel and then clean up related data, such as the owner's count of
     * {@link SOCClientData#getcurrentCreatedChannels()}.
     * Calls {@link SOCChannelList#deleteChannel(String)}.
     *<P>
     * <B>Locks:</B> Must have {@link #channelList}{@link SOCChannelList#takeMonitor() .takeMonitor()}
     * before calling this method.
     *
     * @param ch  Name of the channel to destroy
     * @see #leaveChannel(StringConnection, String, boolean, boolean)
     * @since 1.1.20
     */
    protected final void destroyChannel(final String ch)
    {
        channelList.deleteChannel(ch);

        // Reduce the owner's channels-active count
        StringConnection oConn = conns.get(channelList.getOwner(ch));
        if (oConn != null)
            ((SOCClientData) oConn.getAppData()).deletedChannel();
    }

    /**
     * Adds a connection to a game, unless they're already a member.
     * If the game doesn't yet exist, creates it and announces the new game to all clients
     * by calling {@link #createGameAndBroadcast(StringConnection, String, Map, int, boolean, boolean)}.
     *<P>
     * After this, human players are free to join, until someone clicks "Start Game".
     * At that point, server will look for robots to fill empty seats.
     *
     * @param c    the Connection to be added to the game; its name, version, and locale should already be set.
     * @param gaName  the name of the game.  Not validated or trimmed, see
     *             {@link #createOrJoinGameIfUserOK(StringConnection, String, String, String, Map)} for that.
     * @param gaOpts  if creating a game with options, its {@link SOCGameOption}s; otherwise null.
     *                Should already be validated, by calling
     *                {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean)}
     *                with <tt>doServerPreadjust</tt> true.
     *
     * @return     true if {@code c} was not a member of the game before, or if new game created;
     *             false if {@code c} was already in this game
     *
     * @throws SOCGameOptionVersionException if asking to create a game (gaOpts != null),
     *           but client's version is too low to join because of a
     *           requested game option's minimum version in gaOpts.
     *           Calculated via {@link SOCGameOption#optionsNewerThanVersion(int, boolean, boolean, Map)}.
     *           (this exception was added in 1.1.07)
     * @throws IllegalArgumentException if client's version is too low to join for any
     *           other reason. (this exception was added in 1.1.06)
     * @see #joinGame(SOCGame, StringConnection, boolean, boolean)
     * @see #handleSTARTGAME(StringConnection, SOCStartGame)
     * @see #handleJOINGAME(StringConnection, SOCJoinGame)
     */
    public boolean connectToGame(StringConnection c, final String gaName, Map<String, SOCGameOption> gaOpts)
        throws SOCGameOptionVersionException, IllegalArgumentException
    {
        if (c == null)
        {
            return false;  // shouldn't happen
        }

        boolean result = false;

        final int cliVers = c.getVersion();
        boolean gameExists = false;
        gameList.takeMonitor();

        try
        {
            gameExists = gameList.isGame(gaName);
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in connectToGame");
        }

        gameList.releaseMonitor();

        if (gameExists)
        {
            boolean cliVersOld = false;
            gameList.takeMonitorForGame(gaName);
            SOCGame ga = gameList.getGameData(gaName);

            try
            {
                if (gameList.isMember(c, gaName))
                {
                    result = false;
                }
                else
                {
                    if (ga.getClientVersionMinRequired() <= cliVers)
                    {
                        gameList.addMember(c, gaName);
                        result = true;
                    } else {
                        cliVersOld = true;
                    }
                }
            }
            catch (Exception e)
            {
                D.ebugPrintStackTrace(e, "Exception in connectToGame (isMember)");
            }

            gameList.releaseMonitorForGame(gaName);
            if (cliVersOld)
                throw new IllegalArgumentException("Client version");

                // <---- Exception: Early return ----
        }
        else
        {
            /**
             * the game did not exist, create it after checking options
             */
            final int gVers;
            if (gaOpts == null)
            {
                gVers = -1;
            } else {
                gVers = SOCVersionedItem.itemsMinimumVersion(gaOpts);
                if (gVers > cliVers)
                {
                    // Which requested option(s) are too new for client?
                    List<SOCGameOption> optsValuesTooNew = SOCGameOption.optionsNewerThanVersion
                        (cliVers, true, false, gaOpts);
                    throw new SOCGameOptionVersionException(gVers, cliVers, optsValuesTooNew);

                    // <---- Exception: Early return ----
                }
            }

            // Create new game, expiring in SOCGameListAtServer.GAME_TIME_EXPIRE_MINUTES.
            SOCGame newGame = createGameAndBroadcast(c, gaName, gaOpts, gVers, false, false);
            if (newGame != null)
                result = true;
        }

        return result;
    }

    /**
     * Create a new game, and announce it with a broadcast.
     * Called from {@link #connectToGame(StringConnection, String, Map)}.
     *<P>
     * The new game is created with {@link SOCGameListAtServer#createGame(String, String, String, Map, GameHandler)}
     * and will expire in {@link SOCGameListAtServer#GAME_TIME_EXPIRE_MINUTES} unless extended during play.
     *<P>
     * The broadcast will send {@link SOCNewGameWithOptions} if {@code gaOpts != null}, {@link SOCNewGame} otherwise.
     * If some connected clients are older than {@code gVers}, the message sent to those older clients will
     * let them know they can't connect to the new game.
     *<P>
     * <b>Locks:</b> Uses {@link SOCGameList#takeMonitor()} / {@link SOCGameList#releaseMonitor()};
     * see {@code hasGameListMonitor} parameter.
     *
     * @param c    the Connection creating and owning this game; its name, version, and locale should already be set.
     *             This client connection will be added as a member of the game, and its {@link SOCClientData#createdGame()}
     *             will be called.  Can be null, especially if {@code isBotsOnly}.
     * @param gaName  the name of the game, no game should exist yet with this name. Not validated or trimmed, see
     *             {@link #createOrJoinGameIfUserOK(StringConnection, String, String, String, Map)} for that.
     * @param gaOpts  if creating a game with options, its {@link SOCGameOption}s; otherwise null.
     *                Should already be validated, by calling
     *                {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean)}
     *                with <tt>doServerPreadjust</tt> true.
     * @param gVers  Game's minimum version, from
     *                {@link SOCVersionedItem#itemsMinimumVersion(Map) SOCVersionedItem.itemsMinimumVersion}
     *                {@code (gaOpts)}, or -1 if null gaOpts
     * @param isBotsOnly  True if the game's only players are bots, no humans and no owner
     * @param hasGameListMonitor  True if caller holds the {@link SOCGameList#takeMonitor()} lock already.
     *                If true, this method won't take or release that monitor.  Otherwise will take it before creating
     *                the game, and release it before calling {@link #broadcast(String)}.
     * @return  Newly created game, or null if game name exists or an unexpected error occurs during creation
     * @since 2.0.00
     */
    private SOCGame createGameAndBroadcast
        (StringConnection c, final String gaName, Map<String, SOCGameOption> gaOpts,
         final int gVers, final boolean isBotsOnly, final boolean hasGameListMonitor)
    {
        final SOCClientData scd = (c != null) ? (SOCClientData) c.getAppData() : null;
        SOCGame newGame = null;

        if (! hasGameListMonitor)
            gameList.takeMonitor();
        boolean monitorReleased = false;

        try
        {
            // Create new game, expiring in SOCGameListAtServer.GAME_TIME_EXPIRE_MINUTES.

            newGame = gameList.createGame
                (gaName, (c != null) ? (String) c.getData() : null, (scd != null) ? scd.localeStr : null,
                 gaOpts, handler);

            if (isBotsOnly)
                newGame.isBotsOnly = true;
            else if ((strSocketName != null) && (strSocketName.equals(PRACTICE_STRINGPORT)))
                newGame.isPractice = true;  // flag if practice game (set since 1.1.09)

            if (c != null)
                // Add this (creating) player to the game
                gameList.addMember(c, gaName);

            // should release monitor before we broadcast
            if (! hasGameListMonitor)
                gameList.releaseMonitor();
            monitorReleased = true;

            if (scd != null)
                scd.createdGame();

            // check required client version before we broadcast
            final int cversMin = getMinConnectedCliVersion();

            if ((gVers <= cversMin) && (gaOpts == null))
            {
                // All clients can join it, and no game options: use simplest message
                broadcast(SOCNewGame.toCmd(gaName));

            } else {
                // Send messages, based on clients' version
                // and whether there are game options.

                // Client version variables:
                // cversMax: maximum version connected to server
                // cversMin: minimum version connected to server
                // VERSION_FOR_NEWGAMEWITHOPTIONS: minimum to understand game options

                // Game version variables:
                // gVersMinGameOptsNoChange: minimum to understand these game options
                //           without backwards-compatibility changes to their values
                // gVers: minimum to play the game

                final int gVersMinGameOptsNoChange;
                if (cversMin < Version.versionNumber())
                    gVersMinGameOptsNoChange = SOCVersionedItem.itemsMinimumVersion(gaOpts, true);
                else
                    gVersMinGameOptsNoChange = -1;  // all clients are our current version

                if ((cversMin >= gVersMinGameOptsNoChange)
                    && (cversMin >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS))
                {
                    // All cli can understand msg with version/options included
                    broadcast
                        (SOCNewGameWithOptions.toCmd(gaName, gaOpts, gVers, -2));
                } else {
                    // Only some can understand msg with version/options included;
                    // send at most 1 message to each connected client, split by client version.
                    // Send the old simple NEWGAME message to connected clients of version
                    // newgameSimpleMsgMaxCliVers and lower.  If no game options, send that
                    // message type to all clients.

                    final int cversMax = getMaxConnectedCliVersion();
                    final int newgameSimpleMsgMaxCliVers;  // max version to get simple no-opts newgame message

                    if ((gaOpts != null) && (cversMax >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS))
                    {
                        // Announce to the connected clients with versions new enough for game options:

                        if ((cversMin < gVersMinGameOptsNoChange)  // client versions are connected
                            && (gVers < gVersMinGameOptsNoChange)) // able to play, but needs value changes
                        {
                            // Some clients' versions are too old to understand these game
                            // option values without change; send them an altered set for
                            // compatibility with those clients.

                            // Since cversMin < gVersMinGameOptsNoChange,
                            //   we know gVersMinGameOptsNoChange > -1 and thus >= 1107.
                            // cversMax and VERSION_FOR_NEWGAMEWITHOPTIONS are also 1107.
                            // So:
                            //  1107 <= cversMax
                            //  gVers < gVersMinGameOptsNoChange
                            //  1107 <= gVersMinGameOptsNoChange

                            // Loop through "joinable" client versions < gVersMinGameOptsNoChange.
                            // A separate message is sent below to clients < gVers.
                            int cv = cversMin;  // start loop with min cli version
                            if (gVers > cv)
                                cv = gVers;  // game version is higher, start there

                            for ( ; cv < gVersMinGameOptsNoChange; ++cv)
                            {
                                if (isCliVersionConnected(cv))
                                    broadcastToVers
                                      (SOCNewGameWithOptions.toCmd(gaName, gaOpts, gVers, cv),
                                       cv, cv);
                            }
                            // Now send to newer clients, no changes needed
                            broadcastToVers
                              (SOCNewGameWithOptions.toCmd(gaName, gaOpts, gVers, -2),
                               gVersMinGameOptsNoChange, Integer.MAX_VALUE);
                        } else {
                            // No clients need backwards-compatible option value changes.
                            broadcastToVers
                              (SOCNewGameWithOptions.toCmd(gaName, gaOpts, gVers, -2),
                               SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS, Integer.MAX_VALUE);
                        }

                        // Simple announcement will go only to
                        // clients too old to understand NEWGAMEWITHOPTIONS
                        newgameSimpleMsgMaxCliVers = SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS - 1;
                    } else {

                        // Game has no opts, or no clients are new enough for opts;
                        // simple announcement will go to all clients
                        newgameSimpleMsgMaxCliVers = Integer.MAX_VALUE;
                    }

                    // "Simple" announcement message without game options:
                    final int newgameSimpleMsgCantJoinVers;  // narrow down the versions for announcement
                    if (gVers <= newgameSimpleMsgMaxCliVers)
                    {
                        // To older clients who can join, announce game without its options/version
                        broadcastToVers(SOCNewGame.toCmd(gaName), gVers, newgameSimpleMsgMaxCliVers);
                        newgameSimpleMsgCantJoinVers = gVers - 1;
                    } else {
                        // No older clients can join.  This game's already been announced to
                        // some clients (new enough for NEWGAMEWITHOPTIONS).
                        newgameSimpleMsgCantJoinVers = newgameSimpleMsgMaxCliVers;
                    }

                    // To older clients who can't join, announce game with cant-join prefix
                    if (cversMin <= newgameSimpleMsgCantJoinVers)
                    {
                        StringBuffer sb = new StringBuffer();
                        sb.append(SOCGames.MARKER_THIS_GAME_UNJOINABLE);
                        sb.append(gaName);
                        broadcastToVers
                            (SOCNewGame.toCmd(sb.toString()),
                             SOCGames.VERSION_FOR_UNJOINABLE, newgameSimpleMsgCantJoinVers);
                    }
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in createGameAndBroadcast");
        }
        finally
        {
            if (! (monitorReleased || hasGameListMonitor))
                gameList.releaseMonitor();
        }

        return newGame;
    }

    /**
     * the connection c leaves the game gm.  Clean up; if needed, force the current player's turn to end.
     *<P>
     * If the game becomes empty after removing {@code c}, this method can destroy it if all these
     * conditions are true (determined by {@link GameHandler#leaveGame(SOCGame, StringConnection)}):
     * <UL>
     *  <LI> {@code c} was the last non-robot player
     *  <LI> No one was watching/observing
     *  <LI> {@link SOCGame#isBotsOnly} flag is false
     * </UL>
     *<P>
     * <B>Locks:</B> Has {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gm)}
     * when calling this method; should not have {@link SOCGame#takeMonitor()}.
     * May or may not have {@link SOCGameList#takeMonitor()}, see {@code gameListLock} parameter.
     *
     * @param c  the connection; if c is being dropped because of an error,
     *           this method assumes that {@link StringConnection#disconnect()}
     *           has already been called.  This method won't exclude c from
     *           any communication about leaving the game, in case they are
     *           still connected and in other games.
     * @param gm the game
     * @param destroyIfEmpty  if true, this method will destroy the game if it's now empty.
     *           If false, the caller must call {@link #destroyGame(String)}
     *           before calling {@link SOCGameList#releaseMonitor()}.
     * @param gameListLock  true if we have the {@link SOCGameList#takeMonitor()} lock when called;
     *           false if it must be acquired and released within this method
     * @return true if the game was destroyed, or if it would have been destroyed but {@code destroyIfEmpty} is false.
     */
    public boolean leaveGame(StringConnection c, String gm, final boolean destroyIfEmpty, final boolean gameListLock)
    {
        if (c == null)
        {
            return false;  // <---- Early return: no connection ----
        }

        boolean gameDestroyed = false;

        gameList.removeMember(c, gm);

        SOCGame ga = gameList.getGameData(gm);
        if (ga == null)
        {
            return false;  // <---- Early return: no game ----
        }

        GameHandler hand = gameList.getGameTypeHandler(gm);
        if (hand != null)
        {
            gameDestroyed = hand.leaveGame(ga, c) || gameList.isGameEmpty(gm);
        } else {
            gameDestroyed = true;
                // should not happen. If no handler, game data is inconsistent
        }

        if (gameDestroyed && destroyIfEmpty)
        {
            /**
             * if the game has no players, or if they're all
             * robots, then end the game and update stats.
             */
            if (gameListLock)
            {
                destroyGame(gm);
            }
            else
            {
                gameList.takeMonitor();

                try
                {
                    destroyGame(gm);
                }
                catch (Exception e)
                {
                    D.ebugPrintStackTrace(e, "Exception in leaveGame (destroyGame)");
                }

                gameList.releaseMonitor();
            }
        }

        //D.ebugPrintln("*** gameDestroyed = "+gameDestroyed+" for "+gm);
        return gameDestroyed;
    }

    /**
     * shuffle the indexes to distribute load among {@link #robots}
     * @return a shuffled array of robot indexes, from 0 to ({@link #robots}.size() - 1)
     * @since 1.1.06
     */
    int[] robotShuffleForJoin()
    {
        int[] robotIndexes = new int[robots.size()];

        for (int i = 0; i < robots.size(); i++)
        {
            robotIndexes[i] = i;
        }

        for (int j = 0; j < 3; j++)
        {
            for (int i = 0; i < robotIndexes.length; i++)
            {
                // Swap a random robot, below the ith robot, with the ith robot
                int idx = Math.abs(rand.nextInt() % (robotIndexes.length - i));
                int tmp = robotIndexes[idx];
                robotIndexes[idx] = robotIndexes[i];
                robotIndexes[i] = tmp;
            }
        }
        return robotIndexes;
    }

    /**
     * Set up some robot opponents, running in our JVM for operator convenience.
     * Set up more than needed; when a game is started, game setup will
     * randomize whether its humans will play against smart or fast ones.
     * (Some will be SOCRobotDM.FAST_STRATEGY, some SMART_STRATEGY).
     *<P>
     * The bots will start up and connect in separate threads, then be given their
     * {@code FAST} or {@code SMART} strategy params in {@link #handleIMAROBOT(StringConnection, SOCImARobot)}
     * based on their name prefixes ("droid " or "robot " respectively).
     *<P>
     * Before 1.1.09, this method was part of SOCPlayerClient.
     *
     * @param numFast number of fast robots, with {@link soc.robot.SOCRobotDM#FAST_STRATEGY FAST_STRATEGY}
     * @param numSmart number of smart robots, with {@link soc.robot.SOCRobotDM#SMART_STRATEGY SMART_STRATEGY}
     * @return True if robots were set up, false if an exception occurred.
     *     This typically happens if a robot class or SOCDisplaylessClient
     *     can't be loaded, due to packaging of the server-only JAR.
     * @see soc.client.SOCPlayerClient#startPracticeGame()
     * @see soc.client.SOCPlayerClient.GameAwtDisplay#startLocalTCPServer(int)
     * @see #startRobotOnlyGames(boolean)
     * @see SOCLocalRobotClient
     * @since 1.1.00
     */
    public boolean setupLocalRobots(final int numFast, final int numSmart)
    {
        try
        {
            // Make some faster ones first.
            for (int i = 0; i < numFast; ++i)
            {
                String rname = "droid " + (i+1);
                SOCLocalRobotClient.createAndStartRobotClientThread(rname, strSocketName, port, robotCookie);
                    // includes yield() and sleep(75 ms) this thread.
            }

            // Make a few smarter ones now:
            // handleIMAROBOT will give them SOCServer.ROBOT_PARAMS_SMARTER
            // based on their name prefixes being "robot " not "droid ".

            for (int i = 0; i < numSmart; ++i)
            {
                String rname = "robot " + (i+1+numFast);
                SOCLocalRobotClient.createAndStartRobotClientThread(rname, strSocketName, port, robotCookie);
                    // includes yield() and sleep(75 ms) this thread.
            }
        }
        catch (Exception e)
        {
            //TODO: log
            return false;
        }
        catch (LinkageError e)
        {
            return false;
        }

        return true;
    }

    /**
     * Destroy a game and clean up related data, such as the owner's count of
     * {@link SOCClientData#getCurrentCreatedGames()}.
     *<P>
     * Note that if this game had the {@link SOCGame#isBotsOnly} flag, and {@link #numRobotOnlyGamesRemaining} &gt; 0,
     *  will call {@link #startRobotOnlyGames(boolean)}.
     *<P>
     * <B>Locks:</B> Must have {@link #gameList}{@link SOCGameList#takeMonitor() .takeMonitor()}
     * before calling this method.
     *
     * @param gm  Name of the game to destroy
     * @see #leaveGame(StringConnection, String, boolean, boolean)
     * @see #destroyGameAndBroadcast(String, String)
     */
    public void destroyGame(String gm)
    {
        //D.ebugPrintln("***** destroyGame("+gm+")");
        SOCGame cg = null;

        cg = gameList.getGameData(gm);
        if (cg == null)
            return;

        final boolean wasBotsOnly = cg.isBotsOnly;

        ///
        /// write out game data
        ///

        /*
           currentGameEventRecord.setSnapshot(cg);
           saveCurrentGameEventRecord(gm);
           SOCGameRecord gr = (SOCGameRecord)gameRecords.get(gm);
           writeGameRecord(gm, gr);
         */

        ///
        /// delete the game from gamelist,
        /// tell all robots to leave
        ///
        Vector<StringConnection> members = null;
        members = gameList.getMembers(gm);

        gameList.deleteGame(gm);  // also calls SOCGame.destroyGame

        if (members != null)
        {
            Enumeration<StringConnection> conEnum = members.elements();

            while (conEnum.hasMoreElements())
            {
                StringConnection con = conEnum.nextElement();
                con.put(SOCRobotDismiss.toCmd(gm));
            }
        }

        // Reduce the owner's games-active count
        final String gaOwner = cg.getOwner();
        if (gaOwner != null)
        {
            StringConnection oConn = conns.get(gaOwner);
            if (oConn != null)
                ((SOCClientData) oConn.getAppData()).deletedGame();
        }

        if (wasBotsOnly && (numRobotOnlyGamesRemaining > 0))
            startRobotOnlyGames(true);
    }

    /**
     * Destroy a game and then broadcast its deletion, including lock handling.
     * Calls {@link SOCGameList#takeMonitor()}, {@link #destroyGame(String)},
     * {@link SOCGameList#releaseMonitor()}, and {@link #broadcast(String) broadcast}({@link SOCDeleteGame}).
     * @param gaName  Game name to destroy
     * @param descForStackTrace  Activity description in case of exception thrown from destroyGame;
     *     will debug-print a mesasge "Exception in " + desc, followed by a stack trace.
     * @since 2.0.00
     */
    public void destroyGameAndBroadcast(final String gaName, final String descForStackTrace)
    {
        gameList.takeMonitor();

        try
        {
            destroyGame(gaName);
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in " + descForStackTrace);
        }

        gameList.releaseMonitor();
        broadcast(SOCDeleteGame.toCmd(gaName));
    }

    /**
     * Used when SOCPlayerClient is also hosting games.
     * @return The names (Strings) of games on this server
     */
    public Collection<String> getGameNames()
    {
        return gameList.getGameNames();
    }

    /**
     * Given a game name on this server, return its game object.
     * @param gaName  Game name
     * @return The game, or {@code null} if none found in game list
     * @since 2.0.00
     */
    public SOCGame getGame(final String gaName)
    {
        return gameList.getGameData(gaName);
    }

    /**
     * Given a game name on this server, return its state.
     *
     * @param gm Game name
     * @return Game's state, or -1 if no game with that name on this server
     * @since 1.1.00
     */
    public int getGameState(String gm)
    {
        SOCGame g = gameList.getGameData(gm);
        if (g != null)
            return g.getGameState();
        else
            return -1;
    }

    /**
     * Given a game name on this server, return its game options.
     *
     * @param gm Game name
     * @return the game options, or null if the game doesn't exist or has no options
     * @since 1.1.07
     */
    public Map<String,SOCGameOption> getGameOptions(String gm)
    {
        return gameList.getGameOptions(gm);
    }

    /**
     * True if the server was constructed with a property or command line argument which is used
     * to run the server in Utility Mode instead of Server Mode.  In Utility Mode the server reads
     * its properties, initializes its database connection if any, and performs one task such as a
     * password reset or table/index creation. It won't start other threads and won't fail startup
     * if TCP port binding fails.
     *<P>
     * Utility Mode may also set a status message, see {@link #getUtilityModeMessage()}.
     *<P>
     * The current Utility Mode properties/arguments are:
     *<UL>
     * <LI> {@link SOCDBHelper#PROP_JSETTLERS_DB_SCRIPT_SETUP} property
     * <LI> {@code --pw-reset=username} argument
     *</UL>
     *
     * @return  True if server was constructed with a Utility Mode property or command line argument
     * @since 1.1.20
     */
    public final boolean hasUtilityModeProperty()
    {
        return hasUtilityModeProp;
    }

    /**
     * If {@link #hasUtilityModeProperty()}, get the optional status message to print before exiting.
     * @return  Optional status message, or {@code null}
     * @since 1.1.20
     */
    public final String getUtilityModeMessage()
    {
         return utilityModeMessage;
    }

    /**
     * Given a StringManager (for a client's locale), return all known
     * game options, localizing the descriptive names if available.
     * @param loc  Client's locale for StringManager i18n lookups:
     *          <tt>smgr.get("gameopt." + {@link SOCVersionedItem#key SOCGameOption.key})</tt>
     * @param updateStaticKnownOpts  If true, localize each {@link SOCVersionedItem#getDesc() SOCGameOption.desc} in the
     *          static set of known options used by {@link SOCGameOption#getOption(String, boolean)} and
     *          {@link SOCGameOption#getAllKnownOptions()}; for use only by client's practice-game server
     * @return  {@link SOCGameOption#getAllKnownOptions()}, with descriptions localized if available
     * @since 2.0.00
     */
    public static Map<String,SOCGameOption> localizeKnownOptions
        (final Locale loc, final boolean updateStaticKnownOpts)
    {
        // Get copy of all known options
        Map<String,SOCGameOption> knownOpts = SOCGameOption.getAllKnownOptions();

        // See if we have localized opt descs for sm's locale
        final SOCStringManager sm = SOCStringManager.getServerManagerForClient(loc);
        final boolean hasLocalDescs = ! i18n_gameopt_PL_desc.equals(sm.get("gameopt.PL"));

        // If we can't localize, just return knownOpts
        if (! hasLocalDescs)
        {
            return knownOpts;
        }

        // Localize and return
        HashMap<String,SOCGameOption> opts = new HashMap<String, SOCGameOption>();
        for (SOCGameOption opt : knownOpts.values())
        {
            final String optKey = opt.key;
            try {
                final SOCGameOption oLocal = new SOCGameOption(opt, sm.get("gameopt." + optKey));
                opts.put(optKey, oLocal);
                if (updateStaticKnownOpts)
                    SOCGameOption.addKnownOption(oLocal);
                    // for-loop iteration isn't affected: updates static originals, not the copy in knownOpts
            } catch (MissingResourceException e) {
                opts.put(optKey, opt);
            }
        }

        return opts;
    }

    /**
     * Given a connection's key, return the connected client.
     * Package-level access for other server classes.
     * @param connKey Client name (Object key data), as in {@link StringConnection#getData()}; if null, returns null
     * @return The connection with this key, or null if none
     * @since 2.0.00
     */
    StringConnection getConnection(final String connKey)
    {
        return super.getConnection(connKey);
    }

    /**
     * Connection {@code c} is leaving the server; remove from all channels it was in.
     * In channels where {@code c} was the last connection, calls {@link #destroyChannel(String)}.
     *
     * @param c  the connection
     */
    public void leaveAllChannels(StringConnection c)
    {
        if (c == null)
            return;

        List<String> toDestroy = new ArrayList<String>();  // channels where c was the last member

        channelList.takeMonitor();

        try
        {
            for (Enumeration<String> k = channelList.getChannels(); k.hasMoreElements();)
            {
                String ch = k.nextElement();

                if (channelList.isMember(c, ch))
                {
                    boolean thisChannelDestroyed = false;
                    channelList.takeMonitorForChannel(ch);

                    try
                    {
                        thisChannelDestroyed = leaveChannel(c, ch, false, true);
                    }
                    catch (Exception e)
                    {
                        D.ebugPrintStackTrace(e, "Exception in leaveAllChannels (leaveChannel)");
                    }

                    channelList.releaseMonitorForChannel(ch);

                    if (thisChannelDestroyed)
                        toDestroy.add(ch);
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in leaveAllChannels");
        }

        /** After iterating through all channels, destroy newly empty ones */
        for (String ch : toDestroy)
            destroyChannel(ch);

        channelList.releaseMonitor();

        /**
         * let everyone know about the destroyed channels
         */
        for (String ga : toDestroy)
        {
            broadcast(SOCDeleteChannel.toCmd(ga));
        }
    }

    /**
     * Connection {@code c} is leaving the server; remove from all games it was in.
     * In games where {@code c} was the last human player, calls {@link #destroyGame(String)}.
     *
     * @param c  the connection
     */
    public void leaveAllGames(StringConnection c)
    {
        if (c == null)
            return;

        List<String> toDestroy = new ArrayList<String>();  // games where c was the last human player

        gameList.takeMonitor();

        try
        {
            for (String ga : gameList.getGameNames())
            {
                Vector<StringConnection> v = gameList.getMembers(ga);

                if (v.contains(c))
                {
                    boolean thisGameDestroyed = false;
                    gameList.takeMonitorForGame(ga);

                    try
                    {
                        thisGameDestroyed = leaveGame(c, ga, false, true);
                    }
                    catch (Exception e)
                    {
                        D.ebugPrintStackTrace(e, "Exception in leaveAllGames (leaveGame)");
                    }

                    gameList.releaseMonitorForGame(ga);

                    if (thisGameDestroyed)
                        toDestroy.add(ga);
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in leaveAllGames");
        }

        /** After iterating through all games, destroy newly empty ones */
        for (String ga : toDestroy)
            destroyGame(ga);

        gameList.releaseMonitor();

        /**
         * let everyone know about the destroyed games
         */
        for (String ga : toDestroy)
        {
            D.ebugPrintln("** Broadcasting SOCDeleteGame " + ga);
            broadcast(SOCDeleteGame.toCmd(ga));
        }
    }

    /**
     * Send a message to the given channel
     *
     * @param ch  the name of the channel
     * @param mes the message to send
     */
    public void messageToChannel(String ch, SOCMessage mes)
    {
        final String mesCmd = mes.toCmd();

        channelList.takeMonitorForChannel(ch);

        try
        {
            Vector<StringConnection> v = channelList.getMembers(ch);

            if (v != null)
            {
                Enumeration<StringConnection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection c = menum.nextElement();

                    if (c != null)
                    {
                        c.put(mesCmd);
                    }
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in messageToChannel");
        }

        channelList.releaseMonitorForChannel(ch);
    }

    /**
     * Send a message to the given channel
     *
     * WARNING: MUST HAVE THE gameList.takeMonitorForChannel(ch) before
     * calling this method
     *
     * @param ch  the name of the channel
     * @param mes the message to send
     */
    public void messageToChannelWithMon(String ch, SOCMessage mes)
    {
        Vector<StringConnection> v = channelList.getMembers(ch);

        if (v != null)
        {
            final String mesCmd = mes.toCmd();

            Enumeration<StringConnection> menum = v.elements();

            while (menum.hasMoreElements())
            {
                StringConnection c = menum.nextElement();

                if (c != null)
                {
                    c.put(mesCmd);
                }
            }
        }
    }

    /**
     * Send a message to a player and record it
     *
     * @param c   the player connection
     * @param mes the message to send
     */
    public void messageToPlayer(StringConnection c, SOCMessage mes)
    {
        if ((c == null) || (mes == null))
            return;

        //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", c.getData()));
        c.put(mes.toCmd());
    }

    /**
     * Send a {@link SOCGameServerText} or {@link SOCGameTextMsg} game text message to a player.
     * Equivalent to: messageToPlayer(conn, new {@link SOCGameServerText}(ga, txt));
     *
     * @param c   the player connection; if their version is 2.0.00 or newer,
     *            they will be sent {@link SOCGameServerText}, otherwise {@link SOCGameTextMsg}.
     * @param ga  game name
     * @param txt the message text to send
     * @since 1.1.08
     * @see #messageToPlayerKeyed(StringConnection, String, String)
     */
    public void messageToPlayer(StringConnection c, final String ga, final String txt)
    {
        if (c == null)
            return;

        if (c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT)
            c.put(SOCGameServerText.toCmd(ga, txt));
        else
            c.put(SOCGameTextMsg.toCmd(ga, SERVERNAME, txt));
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message to a player.
     * Equivalent to: messageToPlayer(conn, new {@link SOCGameServerText}(ga,
     * {@link StringConnection#getLocalized(String) c.getLocalized(key)}));
     *
     * @param c   the player connection; if their version is 2.0.00 or newer,
     *            they will be sent {@link SOCGameServerText}, otherwise {@link SOCGameTextMsg}.
     *            Null {@code c} is ignored and not an error.
     * @param gaName  game name
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of
     * @since 2.0.00
     * @see #messageToPlayerKeyed(StringConnection, String, String, Object...)
     */
    public final void messageToPlayerKeyed(StringConnection c, final String gaName, final String key)
    {
        if (c == null)
            return;

        if (c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT)
            c.put(SOCGameServerText.toCmd(gaName, c.getLocalized(key)));
        else
            c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, c.getLocalized(key)));
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message with arguments to a player.
     * Equivalent to: messageToPlayer(conn, new {@link SOCGameServerText}(ga,
     * {@link StringConnection#getLocalized(String, Object...) c.getLocalized(key, args)}));
     *<P>
     * The localized message text must be formatted as in {@link MessageFormat}:
     * Placeholders for {@code args} are <tt>{0}</tt> etc, single-quotes must be repeated: {@code ''}.
     *
     * @param c   the player connection; if their version is 2.0.00 or newer,
     *            they will be sent {@link SOCGameServerText}, otherwise {@link SOCGameTextMsg}.
     *            Null {@code c} is ignored and not an error.
     * @param gaName  game name
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of
     * @param args  Any parameters within {@code txt}'s placeholders
     * @since 2.0.00
     * @see #messageToPlayerKeyed(StringConnection, String, String)
     * @see #messageToPlayerKeyedSpecial(StringConnection, SOCGame, String, Object...)
     */
    public final void messageToPlayerKeyed
        (StringConnection c, final String gaName, final String key, final Object ... args)
    {
        if (c == null)
            return;

        if (c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT)
            c.put(SOCGameServerText.toCmd(gaName, c.getLocalized(key, args)));
        else
            c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, c.getLocalized(key, args)));
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message with arguments to a player,
     * with special formatting like <tt>{0,rsrcs}</tt>.
     * Equivalent to: messageToPlayer(conn, new {@link SOCGameServerText}(ga,
     * {@link StringConnection#getLocalizedSpecial(SOCGame, String, Object...) c.getLocalizedSpecial(ga, key, args)}));
     *<P>
     * The localized message text must be formatted as in {@link MessageFormat}:
     * Placeholders for {@code args} are <tt>{0}</tt> etc, single-quotes must be repeated: {@code ''}.
     * For the SoC-specific parameters such as <tt>{0,rsrcs}</tt>, see the javadoc for
     * {@link SOCStringManager#getSpecial(SOCGame, String, Object...)}.
     *
     * @param c   the player connection; if their version is 2.0.00 or newer,
     *            they will be sent {@link SOCGameServerText}, otherwise {@link SOCGameTextMsg}.
     *            Null {@code c} is ignored and not an error.
     * @param ga  the game
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of
     * @param args  Any parameters within {@code txt}'s placeholders
     * @since 2.0.00
     * @see #messageToPlayerKeyed(StringConnection, String, String, Object...)
     * @see #messageToPlayerKeyed(StringConnection, String, String)
     */
    public final void messageToPlayerKeyedSpecial
        (StringConnection c, final SOCGame ga, final String key, final Object ... args)
    {
        if (c == null)
            return;

        if (c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT)
            c.put(SOCGameServerText.toCmd(ga.getName(), c.getLocalizedSpecial(ga, key, args)));
        else
            c.put(SOCGameTextMsg.toCmd(ga.getName(), SERVERNAME, c.getLocalizedSpecial(ga, key, args)));
    }

    /**
     * Send a message to the given game.
     *<P>
     * <b>Locks:</b> Takes, releases {@link SOCGameList#takeMonitorForGame(String)}.
     *
     * @param ga  the name of the game
     * @param mes the message to send. If mes is a SOCGameTextMsg whose
     *            text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @see #messageToGame(String, String)
     * @see #messageToGameWithMon(String, SOCMessage)
     * @see #messageToGameForVersions(SOCGame, int, int, SOCMessage, boolean)
     */
    public void messageToGame(String ga, SOCMessage mes)
    {
        final String mesCmd = mes.toCmd();

        gameList.takeMonitorForGame(ga);

        try
        {
            Vector<StringConnection> v = gameList.getMembers(ga);

            if (v != null)
            {
                //D.ebugPrintln("M2G - "+mes);
                Enumeration<StringConnection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection c = menum.nextElement();

                    if (c != null)
                    {
                        //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", c.getData()));
                        c.put(mesCmd);
                    }
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in messageToGame");
        }

        gameList.releaseMonitorForGame(ga);
    }

    /**
     * Send a server text message to the given game.
     * Equivalent to: messageToGame(ga, new SOCGameServerText(ga, txt));
     *<P>
     * Do not pass SOCSomeMessage.toCmd() into this method; the message type number
     * will be GAMESERVERTEXT, not the desired SOMEMESSAGE.
     *<P>
     * Client versions older than v2.0.00 will be sent
     * {@link SOCGameTextMsg}(ga, {@link #SERVERNAME}, txt).
     *<P>
     * <b>Locks:</b> Takes, releases {@link SOCGameList#takeMonitorForGame(String)}.
     *
     * @param ga  the name of the game
     * @param txt the message text to send. If
     *            text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @see #messageToGameKeyed(SOCGame, boolean, String)
     * @see #messageToGameKeyed(SOCGame, boolean, String, Object...)
     * @see #messageToGame(String, SOCMessage)
     * @see #messageToGameWithMon(String, SOCMessage)
     * @see #messageToGameExcept(String, StringConnection, String, boolean)
     * @since 1.1.08
     */
    public void messageToGame(final String ga, final String txt)
    {
        final String gameServTxtMsg = SOCGameServerText.toCmd(ga, txt);

        gameList.takeMonitorForGame(ga);

        try
        {
            Vector<StringConnection> v = gameList.getMembers(ga);

            if (v != null)
            {
                Enumeration<StringConnection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection c = menum.nextElement();
                    if (c != null)
                    {
                        if (c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT)
                            c.put(gameServTxtMsg);
                        else
                            c.put(SOCGameTextMsg.toCmd(ga, SERVERNAME, txt));
                    }
                }
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception in messageToGame");
        }

        gameList.releaseMonitorForGame(ga);
    }

    /**
     * Send a game a message containing data fields and also a text field to be localized.
     * Same as {@link #messageToGame(String, SOCMessage)} but calls each member connection's
     * {@link StringConnection#getLocalized(String) c.getLocalized(key)} for the localized text to send.
     *<P>
     * <B>Locks:</B> If {@code takeMon} is true, takes and releases
     * {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gameName)}.
     * Otherwise call {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gameName)}
     * before calling this method.
     *
     * @param ga  The game
     * @param msg  The data message to be sent after localizing text.
     *     This message's fields are not changed here, the localization results are not kept with {@code msg}.
     * @param takeMon Should this method take and release game's monitor via
     *     {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gameName)}?
     *     True unless caller already holds that monitor.
     * @see #messageToGameKeyed(SOCGame, boolean, String)
     * @see #messageToGameKeyed(SOCGame, boolean, String, Object...)
     * @since 2.0.00
     */
    public void messageToGameKeyedType(SOCGame ga, SOCKeyedMessage msg, final boolean takeMon)
    {
        // similar code as the two messageToGameKeyed methods;
        // if you change code here, consider changing it there too

        final boolean hasMultiLocales = ga.hasMultiLocales;
        final String gaName = ga.getName();
        boolean rsrcMissing = false;

        if (takeMon)
            gameList.takeMonitorForGame(gaName);

        try
        {
            Vector<StringConnection> v = gameList.getMembers(gaName);

            if (v != null)
            {
                Enumeration<StringConnection> menum = v.elements();

                final String msgKey = msg.getKey();
                String gameLocalMsg = null, localText = null, gameTxtLocale = null;
                while (menum.hasMoreElements())
                {
                    StringConnection c = menum.nextElement();
                    if (c == null)
                        continue;

                    final String cliLocale = c.getI18NLocale();
                    if ((gameLocalMsg == null)
                        || (hasMultiLocales
                             && (  (cliLocale == null)
                                   ? (gameTxtLocale != null)
                                   : ! cliLocale.equals(gameTxtLocale)  )))
                    {
                        if (msgKey != null)
                            try
                            {
                                localText = c.getLocalized(msgKey);
                            } catch (MissingResourceException e) {
                                localText = msgKey;  // fallback so data fields will still be sent
                                rsrcMissing = true;
                            }

                        gameLocalMsg = msg.toCmd(localText);
                        gameTxtLocale = cliLocale;
                    }

                    if (gameLocalMsg != null)
                        c.put(gameLocalMsg);
                }

                if (rsrcMissing)
                    D.ebugPrintln("Missing string key in messageToGameKeyedType: " + msgKey);
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception in messageToGameKeyedType");
        }

        if (takeMon)
            gameList.releaseMonitorForGame(gaName);
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message to a game.
     * Same as {@link #messageToGame(String, String)} but calls each member connection's
     * {@link StringConnection#getLocalized(String) c.getLocalized(key)} for the localized text to send.
     *<P>
     * Client versions older than v2.0.00 will be sent {@link SOCGameTextMsg}(ga, {@link #SERVERNAME}, txt).
     *<P>
     * <b>Locks:</b> If {@code takeMon} is true, takes and releases {@link SOCGameList#takeMonitorForGame(String)}.
     * Otherwise call {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gaName)}
     * before calling this method.
     *
     * @param ga  the game object
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                True unless caller already holds that monitor.
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of.
     *            If its localized text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @see #messageToGameKeyed(SOCGame, boolean, String, Object...)
     * @see #messageToGameKeyedSpecial(SOCGame, boolean, String, Object...)
     * @see #messageToGameKeyedSpecialExcept(SOCGame, boolean, StringConnection, String, Object...)
     * @see #messageToGameKeyedType(SOCGame, SOCKeyedMessage, boolean)
     * @see #messageToGame(String, String)
     * @since 2.0.00
     */
    public void messageToGameKeyed(SOCGame ga, final boolean takeMon, final String key)
        throws MissingResourceException
    {
        // same code as the other messageToGameKeyed, except for the call to cli.getLocalized;
        // messageToGameKeyedType is also very similar.
        // if you change code here, change it there too

        final boolean hasMultiLocales = ga.hasMultiLocales;
        final String gaName = ga.getName();

        if (takeMon)
            gameList.takeMonitorForGame(gaName);

        try
        {
            Vector<StringConnection> v = gameList.getMembers(gaName);

            if (v != null)
            {
                Enumeration<StringConnection> menum = v.elements();

                String gameTextMsg = null, gameTxtLocale = null;
                while (menum.hasMoreElements())
                {
                    StringConnection c = menum.nextElement();
                    if (c != null)
                    {
                        final String cliLocale = c.getI18NLocale();
                        if ((gameTextMsg == null)
                            || (hasMultiLocales
                                 && (  (cliLocale == null)
                                       ? (gameTxtLocale != null)
                                       : ! cliLocale.equals(gameTxtLocale)  )))
                        {
                            gameTextMsg = SOCGameServerText.toCmd(gaName, c.getLocalized(key));
                            gameTxtLocale = cliLocale;
                        }

                        if ((c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT) && (gameTextMsg != null))
                            c.put(gameTextMsg);
                        else
                            // old client (not common) gets a different message type
                            c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, c.getLocalized(key)));
                    }
                }
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception in messageToGameKeyed");
        }

        if (takeMon)
            gameList.releaseMonitorForGame(gaName);
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message (with parameters) to a game.
     * Same as {@link #messageToGame(String, String)} but calls each member connection's
     * {@link StringConnection#getLocalized(String) c.getLocalized(key)} for the localized text to send.
     *<P>
     * Client versions older than v2.0.00 will be sent {@link SOCGameTextMsg}(ga, {@link #SERVERNAME}, txt).
     *<P>
     * <b>Locks:</b> If {@code takeMon} is true, takes and releases {@link SOCGameList#takeMonitorForGame(String)}.
     * Otherwise call {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gaName)}
     * before calling this method.
     *
     * @param ga  the game object
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                True unless caller already holds that monitor.
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of.
     *            If its localized text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @param params  Objects to use with <tt>{0}</tt>, <tt>{1}</tt>, etc in the localized string
     *             by calling {@link MessageFormat#format(String, Object...)}.
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @see #messageToGameKeyed(SOCGame, boolean, String)
     * @see #messageToGameKeyedSpecial(SOCGame, boolean, String, Object...)
     * @see #messageToGameKeyedSpecialExcept(SOCGame, boolean, StringConnection, String, Object...)
     * @see #messageToGameKeyedType(SOCGame, SOCKeyedMessage, boolean)
     * @see #messageToGame(String, String)
     * @since 2.0.00
     */
    public void messageToGameKeyed(SOCGame ga, final boolean takeMon, final String key, final Object ... params)
        throws MissingResourceException
    {
        // same code as the other messageToGameKeyed, except for the call to cli.getLocalized;
        // messageToGameKeyedType is also very similar.
        // if you change code here, change it there too

        final boolean hasMultiLocales = ga.hasMultiLocales;
        final String gaName = ga.getName();

        if (takeMon)
            gameList.takeMonitorForGame(gaName);

        try
        {
            Vector<StringConnection> v = gameList.getMembers(gaName);

            if (v != null)
            {
                Enumeration<StringConnection> menum = v.elements();

                String gameTextMsg = null, gameTxtLocale = null;
                while (menum.hasMoreElements())
                {
                    StringConnection c = menum.nextElement();
                    if (c != null)
                    {
                        final String cliLocale = c.getI18NLocale();
                        if ((gameTextMsg == null)
                            || (hasMultiLocales
                                 && (  (cliLocale == null)
                                       ? (gameTxtLocale != null)
                                       : ! cliLocale.equals(gameTxtLocale)  )))
                        {
                            gameTextMsg = SOCGameServerText.toCmd(gaName, c.getLocalized(key, params));
                            gameTxtLocale = cliLocale;
                        }

                        if ((c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT) && (gameTextMsg != null))
                            c.put(gameTextMsg);
                        else
                            // old client (not common) gets a different message type
                            c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, c.getLocalized(key, params)));
                    }
                }
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception in messageToGameKeyed");
        }

        if (takeMon)
            gameList.releaseMonitorForGame(gaName);
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message (with parameters) to a game,
     * optionally with special formatting like <tt>{0,rsrcs}</tt>, optionally excluding one connection.
     * Same as {@link #messageToGame(String, String)} but calls each member connection's
     * {@link StringConnection#getLocalizedSpecial(SOCGame, String, Object...) c.getLocalizedSpecial(...)} for the localized text to send.
     *<P>
     * For the SoC-specific parameters such as <tt>{0,rsrcs}</tt>, see the javadoc for
     * {@link SOCStringManager#getSpecial(SOCGame, String, Object...)}.
     *<P>
     * Client versions older than v2.0.00 will be sent {@link SOCGameTextMsg}(ga, {@link #SERVERNAME}, txt).
     *<P>
     * <b>Locks:</b> If {@code takeMon} is true, takes and releases {@link SOCGameList#takeMonitorForGame(String)}.
     * Otherwise call {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gaName)}
     * before calling this method.
     *
     * @param ga  the game object
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                True unless caller already holds that monitor.
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of.
     *            If its localized text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @param params  Objects to use with <tt>{0}</tt>, <tt>{1}</tt>, etc in the localized string
     *             by calling {@link MessageFormat#format(String, Object...)}.
     *             <P>
     *             These objects can include {@link SOCResourceSet} or pairs of
     *             Integers for a resource count and type; see {@link SOCStringManager#getSpecial(SOCGame, String, Object...)}.
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @throws IllegalArgumentException if the localized pattern string has a parse error (closing '}' brace without opening '{' brace, etc)
     * @see #messageToGameKeyedSpecialExcept(SOCGame, boolean, StringConnection, String, Object...)
     * @see #messageToGameKeyed(SOCGame, boolean, String)
     * @see #messageToGame(String, String)
     * @since 2.0.00
     */
    public final void messageToGameKeyedSpecial
        (SOCGame ga, final boolean takeMon, final String key, final Object ... params)
        throws MissingResourceException, IllegalArgumentException
    {
        impl_messageToGameKeyedSpecial(ga, takeMon, gameList.getMembers(ga.getName()), null, true, key, params);
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message (with parameters) to a game,
     * optionally with special formatting like <tt>{0,rsrcs}</tt>, optionally excluding one connection.
     * Same as {@link #messageToGame(String, String)} but calls each member connection's
     * {@link StringConnection#getLocalizedSpecial(SOCGame, String, Object...) c.getLocalizedSpecial(...)} for the
     * localized text to send.
     *
     * @param ga  the game object
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                True unless caller already holds that monitor.
     * @param ex  the excluded connection, or {@code null}
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of.
     *            If its localized text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @param params  Objects to use with <tt>{0}</tt>, <tt>{1}</tt>, etc in the localized string
     *             by calling {@link MessageFormat#format(String, Object...)}.
     *             <P>
     *             These objects can include {@link SOCResourceSet} or pairs of
     *             Integers for a resource count and type; see {@link SOCStringManager#getSpecial(SOCGame, String, Object...)}.
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @throws IllegalArgumentException if the localized pattern string has a parse error (closing '}' brace without opening '{' brace, etc)
     * @see #messageToGameKeyedSpecialExcept(SOCGame, boolean, List, String, Object...)
     * @see #messageToGameKeyed(SOCGame, boolean, String)
     * @see #messageToGame(String, String)
     * @since 2.0.00
     */
    public final void messageToGameKeyedSpecialExcept
        (SOCGame ga, final boolean takeMon, StringConnection ex, final String key, final Object ... params)
        throws MissingResourceException, IllegalArgumentException
    {
        impl_messageToGameKeyedSpecial(ga, takeMon, gameList.getMembers(ga.getName()), ex, true, key, params);
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message (with parameters) to a game,
     * optionally with special formatting like <tt>{0,rsrcs}</tt>, optionally excluding some connections.
     * Same as {@link #messageToGame(String, String)} but calls each member connection's
     * {@link StringConnection#getLocalizedSpecial(SOCGame, String, Object...) c.getLocalizedSpecial(...)} for the
     * localized text to send.
     *
     * @param ga  the game object
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                True unless caller already holds that monitor.
     * @param ex  the excluded connections, or {@code null}
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of.
     *            If its localized text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @param params  Objects to use with <tt>{0}</tt>, <tt>{1}</tt>, etc in the localized string
     *             by calling {@link MessageFormat#format(String, Object...)}.
     *             <P>
     *             These objects can include {@link SOCResourceSet} or pairs of
     *             Integers for a resource count and type; see {@link SOCStringManager#getSpecial(SOCGame, String, Object...)}.
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @throws IllegalArgumentException if the localized pattern string has a parse error (closing '}' brace without opening '{' brace, etc)
     * @see #messageToGameKeyedSpecialExcept(SOCGame, boolean, StringConnection, String, Object...)
     * @see #messageToGameKeyed(SOCGame, boolean, String)
     * @see #messageToGame(String, String)
     * @since 2.0.00
     */
    public final void messageToGameKeyedSpecialExcept
        (SOCGame ga, final boolean takeMon, List<StringConnection> ex, final String key, final Object ... params)
        throws MissingResourceException, IllegalArgumentException
    {
        List<StringConnection> sendTo = gameList.getMembers(ga.getName());
        if ((ex != null) && ! ex.isEmpty())
        {
            // Copy the members list, then remove the excluded connections.
            // This method isn't called for many situations, so this is efficient enough.
            sendTo = new ArrayList<StringConnection>(sendTo);
            for (StringConnection excl : ex)
                sendTo.remove(excl);
        }

        impl_messageToGameKeyedSpecial(ga, takeMon, sendTo, null, true, key, params);
    }

    /**
     * Implement {@code messageToGameKeyedSpecial} and {@code messageToGameKeyedSpecialExcept}.
     *
     * @param ga  the game object
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                True unless caller already holds that monitor.
     * @param members  Game members to send to, from {@link SOCGameListAtServer#getMembers(String)}.
     *            If we're excluding several members of the game, make a new list from getMembers, remove them from
     *            that list, then pass it to this method.
     * @param ex  the excluded connection, or {@code null}
     * @param fmtSpecial  Should this method call {@link SOCStringManager#getSpecial(SOCGame, String, Object...)}
     *            instead of the usual {@link SOCStringManager#get(String, Object...)} ?
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of.
     *            If its localized text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @param params  Objects to use with <tt>{0}</tt>, <tt>{1}</tt>, etc in the localized string
     *             by calling {@link MessageFormat#format(String, Object...)}.
     *             <P>
     *             If {@code fmtSpecial}, these objects can include {@link SOCResourceSet} or pairs of
     *             Integers for a resource count and type; see {@link SOCStringManager#getSpecial(SOCGame, String, Object...)}.
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @throws IllegalArgumentException if the localized pattern string has a parse error (closing '}' brace without opening '{' brace, etc)
     * @since 2.0.00
     */
    private final void impl_messageToGameKeyedSpecial
        (SOCGame ga, final boolean takeMon, final List<StringConnection> members, final StringConnection ex,
         final boolean fmtSpecial, final String key, final Object ... params)
        throws MissingResourceException, IllegalArgumentException
    {
        if (members == null)
            return;

        // same code as the other messageToGameKeyed, except for checking ex and the call to c.getKeyedSpecial;
        // if you change code here, change it there too

        final boolean hasMultiLocales = ga.hasMultiLocales;
        final String gaName = ga.getName();

        if (takeMon)
            gameList.takeMonitorForGame(gaName);

        try
        {
                Iterator<StringConnection> miter = members.iterator();

                String gameTextMsg = null, gameTxtLocale = null;
                while (miter.hasNext())
                {
                    StringConnection c = miter.next();
                    if ((c != null) && (c != ex))
                    {
                        final String cliLocale = c.getI18NLocale();
                        if ((gameTextMsg == null)
                            || (hasMultiLocales
                                 && (  (cliLocale == null)
                                       ? (gameTxtLocale != null)
                                       : ! cliLocale.equals(gameTxtLocale)  )))
                        {
                            if (fmtSpecial)
                                gameTextMsg = SOCGameServerText.toCmd(gaName, c.getLocalizedSpecial(ga, key, params));
                            else
                                gameTextMsg = SOCGameServerText.toCmd(gaName, c.getLocalized(key, params));
                            gameTxtLocale = cliLocale;
                        }

                        if ((c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT) && (gameTextMsg != null))
                            c.put(gameTextMsg);
                        else
                            // old client (not common) gets a different message type
                            if (fmtSpecial)
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, c.getLocalizedSpecial(ga, key, params)));
                            else
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, c.getLocalized(key, params)));
                    }
                }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception in messageToGameKeyedSpecial");
        }

        if (takeMon)
            gameList.releaseMonitorForGame(gaName);
    }

    /**
     * Send a message to the given game.
     *<P>
     *<b>Locks:</b> MUST HAVE THE
     * {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(ga)}
     * before calling this method.
     *
     * @param ga  the name of the game
     * @param mes the message to send
     * @see #messageToGame(String, SOCMessage)
     * @see #messageToGameKeyed(SOCGame, boolean, String)
     * @see #messageToGameForVersions(SOCGame, int, int, SOCMessage, boolean)
     */
    public void messageToGameWithMon(String ga, SOCMessage mes)
    {
        Vector<StringConnection> v = gameList.getMembers(ga);
        if (v == null)
            return;

        //D.ebugPrintln("M2G - "+mes);
        final String mesCmd = mes.toCmd();
        Enumeration<StringConnection> menum = v.elements();

        while (menum.hasMoreElements())
        {
            StringConnection c = menum.nextElement();

            if (c != null)
            {
                //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", c.getData()));
                c.put(mesCmd);
            }
        }
    }

    /**
     * Send a server text message to all the connections in a game excluding one.
     * Equivalent to: messageToGameExcept(gn, new SOCGameTextMsg(gn, {@link #SERVERNAME}, txt), takeMon);
     *<P>
     * Do not pass SOCSomeMessage.toCmd() into this method; the message type number
     * will be GAMETEXTMSG, not the desired SOMEMESSAGE.
     *
     * @param gn  the name of the game
     * @param ex  the excluded connection, or null
     * @param txt the message text to send. <P>
     *            If you need to format the message (with placeholders for i18n),
     *            call {@link MessageFormat MessageFormat}.format(fmt, args) on it first. <P>
     *            If text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                True unless caller already holds that monitor.
     * @see #messageToGame(String, String)
     * @see #messageToGameExcept(String, StringConnection, SOCMessage, boolean)
     * @since 2.0.00
     */
    public void messageToGameExcept(final String gn, final StringConnection ex, final String txt, final boolean takeMon)
    {
        // TODO I18N: Find calls to this method; consider connection's locale and version
        messageToGameExcept(gn, ex, new SOCGameTextMsg(gn, SERVERNAME, txt), takeMon);
    }

    /**
     * Send a message to all the connections in a game
     * excluding some.
     *
     * @param gn  the name of the game
     * @param ex  the list of exceptions
     * @param mes the message
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     * @see #messageToGameExcept(String, StringConnection, SOCMessage, boolean)
     */
    public void messageToGameExcept(String gn, Vector<StringConnection> ex, SOCMessage mes, boolean takeMon)
    {
        if (takeMon)
            gameList.takeMonitorForGame(gn);

        try
        {
            Vector<StringConnection> v = gameList.getMembers(gn);

            if (v != null)
            {
                //D.ebugPrintln("M2GE - "+mes);
                final String mesCmd = mes.toCmd();
                Enumeration<StringConnection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection con = menum.nextElement();

                    if ((con != null) && (!ex.contains(con)))
                    {
                        //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", con.getData()));
                        con.put(mesCmd);
                    }
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in messageToGameExcept");
        }

        if (takeMon)
            gameList.releaseMonitorForGame(gn);
    }

    /**
     * Send a message to all the connections in a game
     * excluding one.
     *
     * @param gn  the name of the game
     * @param ex  the excluded connection, or null
     * @param mes the message
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     * @see #messageToGameExcept(String, StringConnection, String, boolean)
     * @see #messageToGameExcept(String, Vector, SOCMessage, boolean)
     * @see #messageToGameForVersionsExcept(SOCGame, int, int, StringConnection, SOCMessage, boolean)
     */
    public void messageToGameExcept(String gn, StringConnection ex, SOCMessage mes, boolean takeMon)
    {
        if (takeMon)
            gameList.takeMonitorForGame(gn);

        try
        {
            Vector<StringConnection> v = gameList.getMembers(gn);

            if (v != null)
            {
                //D.ebugPrintln("M2GE - "+mes);
                final String mesCmd = mes.toCmd();
                Enumeration<StringConnection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection con = menum.nextElement();
                    if ((con == null) || (con == ex))
                        continue;

                    //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", con.getData()));
                    con.put(mesCmd);
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in messageToGameExcept");
        }

        if (takeMon)
            gameList.releaseMonitorForGame(gn);
    }

    /**
     * Send a message to all the connections in a game in a certain version range.
     * Used for backwards compatibility.
     *
     * @param ga  the game
     * @param vmin  Minimum version to send to, or -1.  Same format as
     *                {@link Version#versionNumber()} and {@link StringConnection#getVersion()}.
     * @param vmax  Maximum version to send to, or {@link Integer#MAX_VALUE}
     * @param mes  the message
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                If the game's clients are all older than <tt>vmin</tt> or
     *                newer than <tt>vmax</tt>, nothing happens and the monitor isn't taken.
     * @since 1.1.19
     */
    public final void messageToGameForVersions
        (final SOCGame ga, final int vmin, final int vmax, final SOCMessage mes, final boolean takeMon)
    {
        messageToGameForVersionsExcept(ga, vmin, vmax, null, mes, takeMon);
    }

    /**
     * Send a message to all the connections in a game in a certain version range, excluding one.
     * Used for backwards compatibility.
     *
     * @param ga  the game
     * @param vmin  Minimum version to send to, or -1.  Same format as
     *                {@link Version#versionNumber()} and {@link StringConnection#getVersion()}.
     * @param vmax  Maximum version to send to, or {@link Integer#MAX_VALUE}
     * @param ex  the excluded connection, or null
     * @param mes  the message
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                If the game's clients are all older than <tt>vmin</tt> or
     *                newer than <tt>vmax</tt>, nothing happens and the monitor isn't taken.
     * @since 1.1.19
     * @see #messageToGameExcept(String, StringConnection, SOCMessage, boolean)
     */
    public final void messageToGameForVersionsExcept
        (final SOCGame ga, final int vmin, final int vmax, final StringConnection ex,
         final SOCMessage mes, final boolean takeMon)
    {
        if ((ga.clientVersionLowest > vmax) || (ga.clientVersionHighest < vmin))
            return;  // <--- All clients too old or too new ---

        final String gn = ga.getName();

        if (takeMon)
            gameList.takeMonitorForGame(gn);

        try
        {
            Vector<StringConnection> v = gameList.getMembers(gn);
            if (v != null)
            {
                String mesCmd = null;  // lazy init, will be mes.toCmd()
                Enumeration<StringConnection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection con = menum.nextElement();
                    if ((con == null) || (con == ex))
                        continue;

                    final int cv = con.getVersion();
                    if ((cv < vmin) || (cv > vmax))
                        continue;

                    //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", con.getData()));
                    if (mesCmd == null)
                        mesCmd = mes.toCmd();
                    con.put(mesCmd);
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in messageToGameForVersions");
        }

        if (takeMon)
            gameList.releaseMonitorForGame(gn);
    }

    /**
     * Send an urgent SOCGameTextMsg to the given game.
     * An "urgent" message is a SOCGameTextMsg whose text
     * begins with ">>>"; the client should draw the user's
     * attention in some way.
     *<P>
     * <b>Locks:</b> Like {@link #messageToGame(String, String)}, will take and release the game's monitor.
     *
     * @param ga  the name of the game
     * @param mes the message to send. If mes does not begin with ">>>",
     *            will prepend ">>> " before sending mes.
     */
    public void messageToGameUrgent(String ga, String mes)
    {
        if (! mes.startsWith(">>>"))
            mes = ">>> " + mes;
        messageToGame(ga, mes);
    }

    /**
     * things to do when the connection c leaves
     *<P>
     * This method is called within a per-client thread,
     * after connection is removed from conns collection
     * and version collection, and after c.disconnect() has been called.
     *
     * @param c  the connection
     */
    @Override
    public void leaveConnection(StringConnection c)
    {
        if ((c == null) || (c.getData() == null))
            return;

        leaveAllChannels(c);
        leaveAllGames(c);

        /**
         * if it is a robot, remove it from the list
         */
        robots.removeElement(c);
    }

    /**
     * Things to do when a new connection comes.
     *<P>
     * If we already have {@link #maxConnections} named clients, reject this new one
     * by sending {@link SOCRejectConnection}.
     *<P>
     * If the connection is accepted, it's added to {@link #unnamedConns} until the
     * player "names" it by joining or creating a game under their player name.
     * Other communication is then done, in {@link #newConnection2(StringConnection)}.
     *<P>
     * Also set client's "assumed version" to -1, until we have sent and
     * received a VERSION message.
     *<P>
     * This method is called within a per-client thread.
     * You can send to client, but can't yet receive messages from them.
     *<P>
     *  SYNCHRONIZATION NOTE: During the call to newConnection1, the monitor lock of
     *  {@link #unnamedConns} is held.  Thus, defer as much as possible until
     *  {@link #newConnection2(StringConnection)} (after the connection is accepted).
     *
     * @param c  the new Connection
     * @return true to accept and continue, false if you have rejected this connection;
     *         if false, addConnection will call {@link StringConnection#disconnectSoft()}.
     *
     * @see #addConnection(StringConnection)
     * @see #newConnection2(StringConnection)
     * @see #nameConnection(StringConnection, boolean)
     */
    @Override
    public boolean newConnection1(StringConnection c)
    {
        if (c == null)
            return false;

        /**
         * see if we are under the connection limit
         */
        try
        {
            if (getNamedConnectionCount() >= maxConnections)
            {
                SOCRejectConnection rcCommand = new SOCRejectConnection("Too many connections, please try another server.");
                c.put(rcCommand.toCmd());
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Caught exception in SOCServer.newConnection(Connection)");
        }

        try
        {
            /**
             * prevent someone from connecting twice from
             * the same machine
             * (Commented out: This is a bad idea due to proxies, NAT, etc.)
             */
            boolean hostMatch = false;
            /*
            Enumeration allConnections = this.getConnections();

               while(allConnections.hasMoreElements()) {
               StringConnection tempCon = (StringConnection)allConnections.nextElement();
               if (!(c.host().equals("pippen")) && (tempCon.host().equals(c.host()))) {
               hostMatch = true;
               break;
               }
               }
             */
            if (hostMatch)
            {
                SOCRejectConnection rcCommand = new SOCRejectConnection("Can't connect to the server more than once from one machine.");
                c.put(rcCommand.toCmd());
            }
            else
            {
                /**
                 * Accept this connection.
                 * Once it's added to the list,
                 * {@link #newConnection2(StringConnection)} will
                 * try to wait for client version, and
                 * will send the list of channels and games.
                 */
                c.setVersion(-1);
                return true;
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Caught exception in SOCServer.newConnection(Connection)");
        }

        return false;  // Not accepted
    }

    /**
     * Send welcome messages (server version and features, and the lists of channels and games
     * ({@link SOCChannels}, {@link SOCGames})) when a new
     * connection comes, part 2 - c has been accepted and added to a connection list.
     * Unlike {@link #newConnection1(StringConnection)},
     * no connection-list locks are held when this method is called.
     *<P>
     * Client's {@link SOCClientData} appdata is set here.
     *<P>
     * This method is called within a per-client thread.
     * You can send to client, but can't yet receive messages from them.
     */
    @Override
    protected void newConnection2(StringConnection c)
    {
        SOCClientData cdata = new SOCClientData();
        c.setAppData(cdata);

        // VERSION of server
        SOCServerFeatures feats = features;
        if (acctsNotOpenRegButNoUsers)
        {
            feats = new SOCServerFeatures(features);
            feats.add(SOCServerFeatures.FEAT_OPEN_REG);  // no accounts: don't require a password from SOCAccountClient
        }
        c.put(SOCVersion.toCmd
            (Version.versionNumber(), Version.version(), Version.buildnum(), feats.getEncodedList()));

        // CHANNELS
        Vector<String> cl = new Vector<String>();
        channelList.takeMonitor();

        try
        {
            Enumeration<String> clEnum = channelList.getChannels();

            while (clEnum.hasMoreElements())
            {
                cl.addElement(clEnum.nextElement());
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in newConnection (channelList)");
        }

        channelList.releaseMonitor();

        c.put(SOCChannels.toCmd(cl));

        // GAMES

        /**
         * Has the client sent us its VERSION message, as the first inbound message?
         * Games will be sent once we know the client's version, or have guessed
         * that it's too old (if the client doesn't tell us soon enough).
         * So: Check if input is waiting for us. If it turns out
         * the waiting message is something other than VERSION,
         * server callback {@link #processFirstCommand} will set up the version TimerTask
         * using {@link SOCClientData#setVersionTimer}.
         * The version timer will call {@link #sendGameList} when it expires.
         * If no input awaits us right now, set up the timer here.
         */
        if (! c.isInputAvailable())
        {
            cdata.setVersionTimer(this, c);
        }

    }  // newConnection2

    /**
     * Name a current connection to the system, which may replace an older connection.
     * Call c.setData(name) just before calling this method.
     * Calls {@link Server#nameConnection(StringConnection)} to move the connection
     * from the unnamed to the named connection list.  Increments {@link #numberOfUsers}.
     *<P>
     * If {@code isReplacing}:
     *<UL>
     * <LI> Replaces the old connection with the new one in all its games and channels
     * <LI> Calls {@link SOCClientData#copyClientPlayerStats(SOCClientData)}
     *      for win/loss record and current game and channel count
     * <LI> Sends the old connection an informational disconnect {@link SOCServerPing SOCServerPing(-1)}
     *</UL>
     *
     * @param c  Connected client; its key data ({@link StringConnection#getData()}) must not be null
     * @param isReplacing  Are we replacing / taking over a current connection?
     * @throws IllegalArgumentException If c isn't already connected, if c.getData() returns null,
     *          or if nameConnection has previously been called for this connection.
     * @since 1.1.08
     */
    private void nameConnection(StringConnection c, boolean isReplacing)
        throws IllegalArgumentException
    {
        StringConnection oldConn = null;
        if (isReplacing)
        {
            Object cKey = c.getData();
            if (cKey == null)
                throw new IllegalArgumentException("null c.getData");
            oldConn = conns.get(cKey);
            if (oldConn == null)
                isReplacing = false;  // shouldn't happen, but fail gracefully
        }

        super.nameConnection(c);

        if (isReplacing)
        {
            gameList.replaceMemberAllGames(oldConn, c);
            channelList.replaceMemberAllChannels(oldConn, c);

            SOCClientData scdNew = (SOCClientData) (c.getAppData());
            SOCClientData scdOld = (SOCClientData) (oldConn.getAppData());
            if ((scdNew != null) && (scdOld != null))
                scdNew.copyClientPlayerStats(scdOld);

            // Let the old one know it's disconnected now,
            // in case it ever does get its connection back.
            if (oldConn.getVersion() >= 1108)
                oldConn.put(SOCServerPing.toCmd(-1));
        }

        numberOfUsers++;
    }

    /**
     * Send the entire list of games to this client; this is sent once per connecting client.
     * Or, send the set of changed games, if the client's guessed version was wrong.
     * The list includes a flag on games which can't be joined by this client version
     * ({@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}).
     *<P>
     * If <b>entire list</b>, then depending on client's version, the message sent will be
     * either {@link SOCGames GAMES} or {@link SOCGamesWithOptions GAMESWITHOPTIONS}.
     * If <b>set of changed games</b>, sent as matching pairs of {@link SOCDeleteGame DELETEGAME}
     * and either {@link SOCNewGame NEWGAME} or {@link SOCNewGameWithOptions NEWGAMEWITHOPTIONS}.
     *<P>
     * There are 2 possible scenarios for when this method is called:
     *<P>
     * - (A) Sending game list to client, for the first time:
     *    Iterate through all games, looking for ones the client's version
     *    is capable of joining.  If not capable, mark the game name as such
     *    before sending it to the client.  (As a special case, very old
     *    client versions "can't know" about the game they can't join, because
     *    they don't recognize the marker.)
     *    Also set the client data's hasSentGameList flag.
     *<P>
     * - (B) The client didn't give its version, and was thus
     *    identified as an old version.  Now we know its newer true version,
     *    so we must tell it about games that it can now join,
     *    which couldn't have been joined by the older assumed version.
     *    So:  Look for games with those criteria.
     *<P>
     * Sending the list is done here, and not in newConnection2, because we must first
     * know the client's version.
     *<P>
     * The minimum version which recognizes the "can't join" marker is
     * 1.1.06 ({@link SOCGames#VERSION_FOR_UNJOINABLE}).  Older clients won't be sent
     * the game names they can't join.
     *<P>
     * <b>Locks:</b> Calls {@link SOCGameListAtServer#takeMonitor()} / releaseMonitor
     *
     * @param c Client's connection; will call getVersion() on it
     * @param prevVers  Previously assumed version of this client;
     *                  if re-sending the list, should be less than c.getVersion.
     * @since 1.1.06
     */
    public void sendGameList(StringConnection c, int prevVers)
    {
        final int cliVers = c.getVersion();   // Need to know this before sending

        // Before send list of games, try for a client version.
        // Give client 1.2 seconds to send it, before we assume it's old
        // (too old to know VERSION).
        // This waiting is done from SOCClientData.setVersionTimer;
        // time to wait is SOCServer.CLI_VERSION_TIMER_FIRE_MS.

        // GAMES / GAMESWITHOPTIONS

        // Based on version:
        // If client is too old (< 1.1.06), it can't be told names of games
        // that it isn't capable of joining.

        boolean cliCanKnow = (cliVers >= SOCGames.VERSION_FOR_UNJOINABLE);
        final boolean cliCouldKnow = (prevVers >= SOCGames.VERSION_FOR_UNJOINABLE);

        Vector<Object> gl = new Vector<Object>();  // contains Strings and/or SOCGames;
                                   // strings are names of unjoinable games,
                                   // with the UNJOINABLE prefix.
        gameList.takeMonitor();

        // Note this flag now, while gamelist monitor is held
        final boolean alreadySent = ((SOCClientData) c.getAppData()).hasSentGameList();
        boolean cliVersionChange = alreadySent && (cliVers > prevVers);

        if (alreadySent && ! cliVersionChange)
        {
            gameList.releaseMonitor();

            return;  // <---- Early return: Nothing to do ----
        }

        if (! alreadySent)
        {
            ((SOCClientData) c.getAppData()).setSentGameList();  // Set while gamelist monitor is held
        }

        /**
         * We release the monitor as soon as we can, even though we haven't yet
         * sent the list to the client.  It's theoretically possible the client will get
         * a NEWGAME message, which is OK, or a DELETEGAME message, before it receives the list
         * we're building.
         * NEWGAME is OK because the GAMES message won't clear the list contents at client.
         * DELETEGAME is less OK, but it's not very likely.
         * If the game is deleted, and then they see it in the list, trying to join that game
         * will create a new empty game with that name.
         */
        Collection<SOCGame> gaEnum = gameList.getGamesData();
        gameList.releaseMonitor();

        if (cliVersionChange && cliCouldKnow)
        {
            // If they already have the names of games they can't join,
            // no need to re-send those names.
            cliCanKnow = false;
        }

        try
        {
            // Build the list of game names.  This loop is used for the
            // initial list, or for sending just the delta after the version fix.

            for (SOCGame g : gaEnum)
            {
                int gameVers = g.getClientVersionMinRequired();

                if (cliVersionChange && (prevVers >= gameVers))
                {
                    continue;  // No need to re-announce, they already
                               // could join it with lower (prev-assumed) version
                }

                if (cliVers >= gameVers)
                {
                    gl.addElement(g);  // Can join
                } else if (cliCanKnow)
                {
                    //  Cannot join, but can see it
                    StringBuffer sb = new StringBuffer();
                    sb.append(SOCGames.MARKER_THIS_GAME_UNJOINABLE);
                    sb.append(g.getName());
                    gl.addElement(sb.toString());
                }
                // else
                //   can't join, and won't see it

            }

            // We now have the list of game names / socgame objs.

            if (! alreadySent)
            {
                // send the full list as 1 message
                if (cliVers >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
                    c.put(SOCGamesWithOptions.toCmd(gl, cliVers));
                else
                    c.put(SOCGames.toCmd(gl));
            } else {
                // send deltas only
                for (int i = 0; i < gl.size(); ++i)
                {
                    Object ob = gl.elementAt(i);
                    String gaName;
                    if (ob instanceof SOCGame)
                        gaName = ((SOCGame) ob).getName();
                    else
                        gaName = (String) ob;

                    if (cliCouldKnow)
                    {
                        // first send delete, if it's on their list already
                        c.put(SOCDeleteGame.toCmd(gaName));
                    }
                    // announce as 'new game' to client
                    if ((ob instanceof SOCGame) && (cliVers >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS))
                        c.put(SOCNewGameWithOptions.toCmd((SOCGame) ob, cliVers));
                    else
                        c.put(SOCNewGame.toCmd(gaName));
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in newConnection(sendgamelist)");
        }

        /*
           gaEnum = gameList.getGames();
           int scores[] = new int[SOCGame.MAXPLAYERS];
           boolean robots[] = new boolean[SOCGame.MAXPLAYERS];
           while (gaEnum.hasMoreElements()) {
           String gameName = (String)gaEnum.nextElement();
           SOCGame theGame = gameList.getGameData(gameName);
           for (int i = 0; i < SOCGame.MAXPLAYERS; i++) {
           SOCPlayer player = theGame.getPlayer(i);
           if (player != null) {
           if (theGame.isSeatVacant(i)) {
           scores[i] = -1;
           robots[i] = false;
           } else {
           scores[i] = player.getPublicVP();
           robots[i] = player.isRobot();
           }
           } else {
           scores[i] = 0;
           }
           }
           c.put(SOCGameStats.toCmd(gameName, scores, robots));
           }
         */

    }  // sendGameList

    /**
     * Check if a nickname is okay, and, if they're already logged in, whether a
     * new replacement connection can "take over" the existing one.
     *<P>
     * a name is ok if it hasn't been used yet, isn't {@link #SERVERNAME the server's name},
     * and (since 1.1.07) passes {@link SOCMessage#isSingleLineAndSafe(String)}.
     *<P>
     * The "take over" option is used for reconnect when a client loses
     * connection, and server doesn't realize it.
     * A new connection can "take over" the name after a timeout; check
     * the return value.
     * (After {@link #NICKNAME_TAKEOVER_SECONDS_SAME_IP} or
     *  {@link #NICKNAME_TAKEOVER_SECONDS_DIFFERENT_IP} seconds)
     * When taking over, the new connection's client version must be able
     * to join all games that the old connection is playing, as returned
     * by {@link SOCGameListAtServer#playerGamesMinVersion(StringConnection) gameList.playerGamesMinVersion}.
     *
     * @param n  the name
     * @param newc  A new incoming connection, asking for this name
     * @param withPassword  Did the connection supply a password?
     * @return   0 if the name is okay; <BR>
     *          -1 if OK <strong>and you are taking over a connection;</strong> <BR>
     *          -2 if not OK by rules (fails isSingleLineAndSafe); <BR>
     *          -vers if not OK by version (for takeover; will be -1000 lower); <BR>
     *          or, the number of seconds after which <tt>newc</tt> can
     *             take over this name's games.
     * @see #checkNickname_getRetryText(int)
     */
    private int checkNickname(String n, StringConnection newc, final boolean withPassword)
    {
        if (n.equals(SERVERNAME))
        {
            return -2;
        }

        if (! SOCMessage.isSingleLineAndSafe(n))
        {
            return -2;
        }

        // check conns hashtable
        StringConnection oldc = getConnection(n);
        if (oldc == null)
        {
            return 0;  // OK: no player by that name already
        }

        // Can we take over this one?
        SOCClientData scd = (SOCClientData) oldc.getAppData();
        if (scd == null)
        {
            return -2;  // Shouldn't happen; name and SCD are assigned at same time
        }
        final int timeoutNeeded;
        if (withPassword)
            timeoutNeeded = NICKNAME_TAKEOVER_SECONDS_SAME_PASSWORD;
        else if (newc.host().equals(oldc.host()))
            // same IP address or hostname
            timeoutNeeded = NICKNAME_TAKEOVER_SECONDS_SAME_IP;
        else
            timeoutNeeded = NICKNAME_TAKEOVER_SECONDS_DIFFERENT_IP;

        final long now = System.currentTimeMillis();
        if (scd.disconnectLastPingMillis != 0)
        {
            int secondsSincePing = (int) (((now - scd.disconnectLastPingMillis)) / 1000L);
            if (secondsSincePing >= timeoutNeeded)
            {
                // Already sent ping, timeout has expired.
                // Re-check version just in case.
                int minVersForGames = gameList.playerGamesMinVersion(oldc);
                if (minVersForGames > newc.getVersion())
                {
                    if (minVersForGames < 1000)
                        minVersForGames = 1000;
                    return -minVersForGames;  // too old to play
                }
                // it's OK to take over this nickname.  A call made soon
                // to nameConnection(c,true) will transfer data from old conn, to new conn.
                return -1;
            } else {
                // Already sent ping, timeout not yet expired.
                return timeoutNeeded - secondsSincePing;
            }
        }

        // Have not yet sent a ping.
        int minVersForGames = gameList.playerGamesMinVersion(oldc);
        if (minVersForGames > newc.getVersion())
        {
            if (minVersForGames < 1000)
                minVersForGames = 1000;
            return -minVersForGames;  // too old to play
        }
        scd.disconnectLastPingMillis = now;
        if (oldc.getVersion() >= 1108)
        {
            // Already-connected client should respond to ping.
            // If not, consider them disconnected.
            oldc.put(SOCServerPing.toCmd(timeoutNeeded));
        }
        return timeoutNeeded;
    }

    /**
     * For a nickname that seems to be in use, build a text message with the
     * time remaining before someone can attempt to take over that nickname.
     * Used for reconnect when a client loses connection, and server doesn't realize it.
     * A new connection can "take over" the name after a timeout.
     * ({@link #NICKNAME_TAKEOVER_SECONDS_SAME_IP},
     *  {@link #NICKNAME_TAKEOVER_SECONDS_DIFFERENT_IP})
     *
     * @param nameTimeout  Number of seconds before trying to reconnect
     * @return message starting with "Please wait x seconds" or "Please wait x minute(s)"
     * @since 1.1.08
     */
    private static final String checkNickname_getRetryText(final int nameTimeout)
    {
        StringBuffer sb = new StringBuffer("Please wait ");
        if (nameTimeout <= 90)
        {
            sb.append(nameTimeout);
            sb.append(" seconds");
        } else {
            sb.append((int) ((nameTimeout + 20) / 60));
            sb.append(" minute(s)");
        }
        sb.append(MSG_NICKNAME_ALREADY_IN_USE_WAIT_TRY_AGAIN);
        sb.append(MSG_NICKNAME_ALREADY_IN_USE);
        return sb.toString();
    }

    /**
     * For a nickname that seems to be in use, build a text message with the
     * minimum version number needed to take over that nickname.
     * Used for reconnect when a client loses connection, and server doesn't realize it.
     * A new connection can "take over" the name after a timeout.
     *
     * @param needsVersion Version number required to take it over;
     *         a positive integer in the same format as {@link SOCGame#getClientVersionMinRequired()}
     * @return string containing the version,
     *         starting with {@link #MSG_NICKNAME_ALREADY_IN_USE_NEWER_VERSION_P1}.
     * @since 1.1.08
     */
    private static final String checkNickname_getVersionText(final int needsVersion)
    {
        StringBuffer sb = new StringBuffer(MSG_NICKNAME_ALREADY_IN_USE_NEWER_VERSION_P1);
        sb.append(needsVersion);
        sb.append(MSG_NICKNAME_ALREADY_IN_USE_NEWER_VERSION_P2);
        return sb.toString();
    }

    /**
     * Callback to process the client's first message command specially.
     * Look for VERSION message; if none is received, set up a timer to wait
     * for version and (if never received) send out the game list soon.
     *
     * @param str Contents of first message from the client.
     *         Will be parsed with {@link SOCMessage#toMsg(String)}.
     * @param con Connection (client) sending this message.
     * @return true if processed here (VERSION), false if this message should be
     *         queued up and processed as normal by
     *         {@link SOCMessageDispatcher#dispatch(String, StringConnection)}.
     */
    @Override
    public boolean processFirstCommand(String str, StringConnection con)
    {
        try
        {
            SOCMessage mes = SOCMessage.toMsg(str);
            if ((mes != null) && (mes.getType() == SOCMessage.VERSION))
            {
                handleVERSION(con, (SOCVersion) mes);

                return true;  // <--- Early return: Version was handled ---
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "ERROR -> processFirstCommand");
        }

        // It wasn't version, it was something else.  Set the
        // timer to wait for version, and return false for normal
        // processing of the message.

        ((SOCClientData) con.getAppData()).setVersionTimer(this, con);
        return false;
    }

    /**
     * Process any inbound message which isn't handled by {@link SOCGameMessageHandler}.
     *<P>
     *<B>Note:</B> When there is a choice, always use local information
     *       over information from the message.  For example, use
     *       the nickname from the connection to get the player
     *       information rather than the player information from
     *       the message.  This makes it harder to send false
     *       messages making players do things they didn't want
     *       to do.
     * @param mes  Message from {@code c}; not {@code null}
     * @param c    Connection (client) sending this message.
     * @throws NullPointerException  if {@code mes} is {@code null}
     * @throws Exception  Caller must catch any exceptions thrown because of
     *    conditions or bugs in any server methods called from here.
     * @since 2.0.00
     */
    final void processServerCommand(final SOCMessage mes, final StringConnection c)
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
            removeConnection(c);
            removeConnectionCleanup(c);
            break;

        /**
         * text message
         */
        case SOCMessage.TEXTMSG:

            final SOCTextMsg textMsgMes = (SOCTextMsg) mes;

            if (allowDebugUser && c.getData().equals("debug"))
            {
                if (textMsgMes.getText().startsWith("*KILLCHANNEL*"))
                {
                    messageToChannel(textMsgMes.getChannel(), new SOCTextMsg
                        (textMsgMes.getChannel(), SERVERNAME,
                         "********** " + (String) c.getData() + " KILLED THE CHANNEL **********"));
                    channelList.takeMonitor();

                    try
                    {
                        destroyChannel(textMsgMes.getChannel());
                    }
                    catch (Exception e)
                    {
                        D.ebugPrintStackTrace(e, "Exception in KILLCHANNEL");
                    }

                    channelList.releaseMonitor();
                    broadcast(SOCDeleteChannel.toCmd(textMsgMes.getChannel()));
                }
                else
                {
                    /**
                     * Send the message to the members of the channel
                     */
                    messageToChannel(textMsgMes.getChannel(), mes);
                }
            }
            else
            {
                /**
                 * Send the message to the members of the channel
                 */
                messageToChannel(textMsgMes.getChannel(), mes);
            }

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
            handleCREATEACCOUNT(c, (SOCCreateAccount) mes);
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

    /**
     * List and description of general commands that any game member can run.
     * Used by {@link #processDebugCommand(StringConnection, String, String, String)}
     * when {@code *HELP*} is requested.
     * @see #ADMIN_USER_COMMANDS_HELP
     * @see #DEBUG_COMMANDS_HELP
     * @since 1.1.20
     */
    public static final String[] GENERAL_COMMANDS_HELP =
        {
        "--- General Commands ---",
        "*ADDTIME*  add 30 minutes before game expiration",
        "*CHECKTIME*  print time remaining before expiration",
        "*HELP*   info on available commands",
        "*STATS*   server stats and current-game stats",
        "*VERSION*  show version and build information",
        "*WHO*   show players and observers of this game",
        };

    /**
     * Heading to show above any admin commands the user is authorized to run.  Declared separately
     * from {@link #ADMIN_USER_COMMANDS_HELP} for use when other admin types are added.
     *<br>
     *  {@code --- Admin Commands ---}
     *
     * @since 1.1.20
     */
    private static final String ADMIN_COMMANDS_HEADING = "--- Admin Commands ---";

    /**
     * List and description of user-admin commands. Along with {@link #GENERAL_COMMANDS_HELP}
     * and {@link #DEBUG_COMMANDS_HELP}, used by
     * {@link #processDebugCommand(StringConnection, String, String, String)}
     * when {@code *HELP*} is requested by a debug/admin user who passes
     * {@link #isUserDBUserAdmin(String, boolean) isUserDBUserAdmin(username, true)}.
     * Preceded by {@link #ADMIN_COMMANDS_HEADING}.
     * @since 1.1.20
     * @see #GENERAL_COMMANDS_HELP
     * @see #DEBUG_COMMANDS_HELP
     */
    public static final String[] ADMIN_USER_COMMANDS_HELP =
        {
        "*WHO* gameName   show players and observers of gameName",
        "*WHO* *  show all connected clients",
        };

    /**
     * List and description of debug/admin commands. Along with {@link #GENERAL_COMMANDS_HELP}
     * and {@link #ADMIN_USER_COMMANDS_HELP},
     * used by {@link #processDebugCommand(StringConnection, String, String, String)}
     * when {@code *HELP*} is requested by a debug/admin user.
     * @since 1.1.07
     * @see #GENERAL_COMMANDS_HELP
     * @see #ADMIN_USER_COMMANDS_HELP
     * @see GameHandler#getDebugCommandsHelp()
     */
    public static final String[] DEBUG_COMMANDS_HELP =
        {
        "--- Debug Commands ---",
        "*BCAST*  broadcast msg to all games/channels",
        "*GC*    trigger the java garbage-collect",
        "*KILLBOT*  botname  End a bot's connection",
        "*KILLGAME*  end the current game",
        "*RESETBOT* botname  End a bot's connection",
        "*STARTBOTGAME* [maxBots]  Start this game (no humans have sat) with bots only",
        "*STOP*  kill the server",
        };

    /**
     * Process a debug command, sent by the "debug" client/player.
     * Some debug commands are server-wide, some apply to a specific game.
     * If no server-wide commands match, server will call
     * {@link GameHandler#processDebugCommand(StringConnection, String, String, String)}
     * to check for those.
     *<P>
     * Check {@link #allowDebugUser} before calling this method.
     * For list of commands see {@link #GENERAL_COMMANDS_HELP}, {@link #DEBUG_COMMANDS_HELP},
     * {@link #ADMIN_USER_COMMANDS_HELP}, and {@link GameHandler#getDebugCommandsHelp()}.
     * "Unprivileged" general commands are handled by
     * {@link #handleGAMETEXTMSG(StringConnection, SOCGameTextMsg)}.
     *
     * @param debugCli  Client sending the potential debug command
     * @param ga  Game in which the message is sent
     * @param dcmd   Text message which may be a debug command
     * @param dcmdU  {@code dcmd} as uppercase, for efficiency (it's already been uppercased in caller)
     * @return true if {@code dcmd} is a recognized debug command, false otherwise
     */
    public boolean processDebugCommand(StringConnection debugCli, String ga, final String dcmd, final String dcmdU)
    {
        // See handleGAMETEXTMSG for "unprivileged" debug commands like *HELP*, *STATS*, and *ADDTIME*.

        boolean isCmd = true;  // eventual return value; will set false if unrecognized

        if (dcmdU.startsWith("*KILLGAME*"))
        {
            messageToGameUrgent(ga, ">>> ********** " + (String) debugCli.getData() + " KILLED THE GAME!!! ********** <<<");
            destroyGameAndBroadcast(ga, "KILLGAME");
        }
        else if (dcmdU.startsWith("*GC*"))
        {
            Runtime rt = Runtime.getRuntime();
            rt.gc();
            messageToGame(ga, "> GARBAGE COLLECTING DONE");
            messageToGame(ga, "> Free Memory: " + rt.freeMemory());
        }
        else if (dcmd.startsWith("*STOP*"))  // dcmd to force case-sensitivity
        {
            // Extra info needed to shut it down: Server console output

            boolean shutNow = false;

            final long now = System.currentTimeMillis();
            if ((srvShutPassword != null) && (now <= srvShutPasswordExpire))
            {
                // look for trailing \n, look for shutdown pw preceding it
                int end = dcmd.length();
                while (Character.isISOControl(dcmd.charAt(end - 1)))
                    --end;

                final int i = dcmd.lastIndexOf(' ');
                if ((i < dcmd.length()) && (dcmd.substring(i+1, end).equals(srvShutPassword)))
                    shutNow = true;
            } else {
                srvShutPasswordExpire = now + (45 * 1000L);
                StringBuffer sb = new StringBuffer();
                for (int i = 12 + rand.nextInt(5); i > 0; --i)
                    sb.append((char) (33 + rand.nextInt(126 - 33)));
                srvShutPassword = sb.toString();
                System.err.println("** Shutdown password generated: " + srvShutPassword);
                broadcast(SOCBCastTextMsg.toCmd((String) debugCli.getData() + " WANTS TO STOP THE SERVER"));
                messageToPlayer(debugCli, ga, "Send stop command again with the password.");
            }

            if (shutNow)
            {
                String stopMsg = ">>> ********** " + (String) debugCli.getData() + " KILLED THE SERVER!!! ********** <<<";
                stopServer(stopMsg);
                System.exit(0);
            }
        }
        else if (dcmdU.startsWith("*BCAST* "))
        {
            ///
            /// broadcast to all chat channels and games
            ///
            broadcast(SOCBCastTextMsg.toCmd(dcmd.substring(8)));
        }
        else if (dcmdU.startsWith("*BOTLIST*"))
        {
            Enumeration<StringConnection> robotsEnum = robots.elements();

            while (robotsEnum.hasMoreElements())
            {
                StringConnection robotConn = robotsEnum.nextElement();
                messageToGame(ga, "> Robot: " + robotConn.getData());
                robotConn.put(SOCAdminPing.toCmd((ga)));
            }
        }
        else if (dcmdU.startsWith("*RESETBOT* "))
        {
            String botName = dcmd.substring(11).trim();
            messageToGame(ga, "> botName = '" + botName + "'");

            Enumeration<StringConnection> robotsEnum = robots.elements();

            boolean botFound = false;
            while (robotsEnum.hasMoreElements())
            {
                StringConnection robotConn = robotsEnum.nextElement();
                if (botName.equals(robotConn.getData()))
                {
                    botFound = true;
                    messageToGame(ga, "> SENDING RESET COMMAND TO " + botName);

                    SOCAdminReset resetCmd = new SOCAdminReset();
                    robotConn.put(resetCmd.toCmd());

                    break;
                }
            }
            if (! botFound)
                D.ebugPrintln("L2590 Bot not found to reset: " + botName);
        }
        else if (dcmdU.startsWith("*KILLBOT* "))
        {
            String botName = dcmd.substring(10).trim();
            messageToGame(ga, "> botName = '" + botName + "'");

            Enumeration<StringConnection> robotsEnum = robots.elements();

            boolean botFound = false;
            while (robotsEnum.hasMoreElements())
            {
                StringConnection robotConn = robotsEnum.nextElement();

                if (botName.equals(robotConn.getData()))
                {
                    botFound = true;
                    messageToGame(ga, "> DISCONNECTING " + botName);
                    removeConnection(robotConn);
                    removeConnectionCleanup(robotConn);

                    break;
                }
            }
            if (! botFound)
                D.ebugPrintln("L2614 Bot not found to disconnect: " + botName);
        }
        else if (dcmdU.startsWith("*STARTBOTGAME*"))
        {
            if (0 == getConfigIntProperty(props, PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL, 0))
            {
                messageToPlayer(debugCli, ga,
                    "To start a bots-only game, must restart server with "
                    + PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL + " != 0.");
                return true;
            }

            SOCGame gameObj = getGame(ga);
            if (gameObj == null)
                return true;  // we're sitting in this game, shouldn't happen
            if (gameObj.getGameState() != SOCGame.NEW)
            {
                messageToPlayer(debugCli, ga, "This game has already started; you must create a new one.");
                return true;
            }

            int maxBots = 0;
            if (dcmdU.length() > 15)
            {
                try {
                    maxBots = Integer.parseInt(dcmdU.substring(15).trim());
                } catch (NumberFormatException e) {}
            }

            handleSTARTGAME(debugCli, new SOCStartGame(ga), maxBots);
            return true;
        }
        else
        {
            // See if game type's handler finds a debug command
            GameHandler hand = gameList.getGameTypeHandler(ga);
            if (hand != null)
                isCmd = hand.processDebugCommand(debugCli, ga, dcmd, dcmdU);
            else
                isCmd = false;
        }

        return isCmd;
    }

    /**
     * The server is being cleanly stopped.
     * Shut down with a final message "The game server is shutting down".
     */
    @Override
    public synchronized void stopServer()
    {
        stopServer(">>> The game server is shutting down. <<<");
    }

    /**
     * The server is being cleanly stopped.  Send a final message, disconnect all
     * the connections, disconnect from database if connected.
     * Currently called only by the debug command "*STOP*",
     * and by SOCPlayerClient's locally hosted TCP server.
     *
     * @param stopMsg Final text message to send to all connected clients, or null.
     *         Will be sent as a {@link SOCBCastTextMsg}.
     *         As always, if message starts with ">>" it will be considered urgent.
     */
    public synchronized void stopServer(String stopMsg)
    {
        if (stopMsg != null)
        {
            System.out.println("stopServer: " + stopMsg);
            System.out.println();
            broadcast(SOCBCastTextMsg.toCmd(stopMsg));
        }

        /// give time for messages to drain (such as urgent text messages
        /// about stopping the server)
        try
        {
            Thread.sleep(500);
        }
        catch (InterruptedException ie)
        {
            Thread.yield();
        }

        /// now continue with shutdown
        try
        {
            SOCDBHelper.cleanup(true);
        }
        catch (SQLException x) { }

        super.stopServer();

        System.out.println("Server shutdown completed.");
    }

    /**
     * Check that the username and password (if any) is okay: Length versus {@link #PLAYER_NAME_MAX_LENGTH}, name
     * in use but not timed out versus takeover, etc. Calls {@link #checkNickname(String, StringConnection, boolean)}
     * and {@link #authenticateUser(StringConnection, String, String)}.
     *<P>
     * If not okay, sends client a {@link SOCStatusMessage} with an appropriate status code.
     *<P>
     * If this user is already logged into another connection, checks here whether this new
     * replacement connection can "take over" the existing one according to a timeout calculation
     * in {@link #checkNickname(String, StringConnection, boolean)}.
     *<P>
     * If this connection isn't already logged on and named ({@link StringConnection#getData() c.getData()}
     * == {@code null}) and all checks pass: Unless {@code doNameConnection} is false, calls
     * {@link StringConnection#setData(Object) c.setData(msgUser)} and
     * {@link #nameConnection(StringConnection, boolean) nameConnection(c, isTakingOver)} before
     * returning {@link #AUTH_OR_REJECT__OK} or {@link #AUTH_OR_REJECT__TAKING_OVER}.
     *<P>
     * If this connection is already logged on and named ({@link StringConnection#getData() c.getData()} != {@code null}),
     * does nothing.  Won't check username or password, just returns {@link #AUTH_OR_REJECT__OK}.
     *
     * @param c  Client's connection
     * @param msgUser  Client username (nickname) to validate and authenticate; will be {@link String#trim() trim()med}.
     *     Ignored if connection is already authenticated
     *     ({@link StringConnection#getData() c.getData()} != {@code null}).
     * @param msgPass  Password to supply to {@link #authenticateUser(StringConnection, String, String)},
     *     or ""; will be {@link String#trim() trim()med}.
     * @param cliVers  Client version, from {@link StringConnection#getVersion()}
     * @param doNameConnection  True if successful auth of an unnamed connection should have this method call
     *     {@link StringConnection#setData(Object) c.setData(msgUser)} and
     *     {@link #nameConnection(StringConnection, boolean) nameConnection(c, isTakingOver)}.
     *     <P>
     *     For the usual connect sequence, callers will want {@code true}.  Some callers might want to check
     *     other things after this method and possibly reject the connection at that point; they will want
     *     {@code false}. Those callers must remember to call {@code c.setData(msgUser)} and
     *     <tt>nameConnection(c, (result == {@link #AUTH_OR_REJECT__TAKING_OVER}))</tt> themselves to finish
     *     authenticating a connection.
     * @param allowTakeover  True if the new connection can "take over" an older connection in response to the
     *     message it sent.  If true, the caller must be prepared to send all game info/channel info that the
     *     old connection had joined, so the new connection has full info to participate in them.
     * @return  Result of the auth check: {@link #AUTH_OR_REJECT__FAILED},
     *     {@link #AUTH_OR_REJECT__OK}, or (only if {@code allowTakeover}) {@link #AUTH_OR_REJECT__TAKING_OVER}
     * @since 1.1.19
     */
    private int authOrRejectClientUser
        (StringConnection c, String msgUser, String msgPass, final int cliVers,
         final boolean doNameConnection, final boolean allowTakeover)
    {
        if (c.getData() != null)
        {
            return AUTH_OR_REJECT__OK;  // <---- Early return: Already authenticated ----
        }

        boolean isTakingOver = false;  // will set true if a human player is replacing another player in the game

        msgUser = msgUser.trim();
        msgPass = msgPass.trim();

        /**
         * If connection doesn't already have a nickname, check that the nickname is ok
         */
        if (msgUser.length() > PLAYER_NAME_MAX_LENGTH)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWGAME_NAME_TOO_LONG, cliVers,
                     SOCStatusMessage.MSG_SV_NEWGAME_NAME_TOO_LONG + Integer.toString(PLAYER_NAME_MAX_LENGTH)));
            return AUTH_OR_REJECT__FAILED;
        }

        /**
         * check if a nickname is okay, and, if they're already logged in,
         * whether a new replacement connection can "take over" the existing one.
         */
        final int nameTimeout = checkNickname(msgUser, c, (msgPass != null) && (msgPass.trim().length() > 0));
        System.err.println
            ("L4910 past checkNickname at " + System.currentTimeMillis()
             + (((nameTimeout == 0) || (nameTimeout == -1))
                ? (" for " + msgUser)
                : ""));

        if (nameTimeout == -1)
        {
            if (allowTakeover)
            {
                isTakingOver = true;
            } else {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                         MSG_NICKNAME_ALREADY_IN_USE));
                return AUTH_OR_REJECT__FAILED;
            }
        } else if (nameTimeout == -2)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                     MSG_NICKNAME_ALREADY_IN_USE));
            return AUTH_OR_REJECT__FAILED;
        } else if (nameTimeout <= -1000)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                     checkNickname_getVersionText(-nameTimeout)));
            return AUTH_OR_REJECT__FAILED;
        } else if (nameTimeout > 0)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                     (allowTakeover) ? checkNickname_getRetryText(nameTimeout) : MSG_NICKNAME_ALREADY_IN_USE));
            return AUTH_OR_REJECT__FAILED;
        }

        /**
         * account and password required?
         */
        if (getConfigBoolProperty(props, PROP_JSETTLERS_ACCOUNTS_REQUIRED, false))
        {
            if (msgPass.length() == 0)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_PW_REQUIRED, cliVers,
                         "This server requires user accounts and passwords."));
                return AUTH_OR_REJECT__FAILED;
            }

            // Assert: msgPass isn't "".
            // authenticateUser queries db and requires an account there when msgPass is not "".
        }

        /**
         * password check new connection from database, if not done already and if possible
         */
        if (! authenticateUser(c, msgUser, msgPass))
        {
            return AUTH_OR_REJECT__FAILED;  // <---- Early return: Password auth failed ----
        }

        /**
         * Now that everything's validated, name this connection/user/player.
         * If isTakingOver, also copies their current game/channel count.
         */
        if (doNameConnection)
        {
            c.setData(msgUser);
            nameConnection(c, isTakingOver);
        }

        return (isTakingOver) ? AUTH_OR_REJECT__TAKING_OVER : AUTH_OR_REJECT__OK;
    }

    /**
     * authenticate the user:
     * see if the user is in the db, if so then check the password.
     * if they're not in the db, but they supplied a password,
     * then send a message (not OK).
     * if they're not in the db, and no password, then ok.
     *
     * @param c         the user's connection
     * @param userName  the user's nickname; trim before calling
     * @param password  the user's password; trim before calling
     * @return true if the user has been authenticated
     */
    private boolean authenticateUser(StringConnection c, final String userName, final String password)
    {
        String userPassword = null;

        try
        {
            userPassword = SOCDBHelper.getUserPassword(userName);
        }
        catch (SQLException sqle)
        {
            // Indicates a db problem: don't authenticate empty password
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_PROBLEM_WITH_DB, c.getVersion(),
                    "Problem connecting to database, please try again later."));
            return false;
        }

        int replySV = 0;
        if (userPassword != null)
        {
            if (! userPassword.equals(password))
                replySV = SOCStatusMessage.SV_PW_WRONG;
        }
        else if (! password.equals(""))
        {
            // No password found in database.
            // (Or, no database connected.)
            // If they supplied a password, it won't work here.

            replySV = SOCStatusMessage.SV_PW_WRONG;
        }

        if (replySV != 0)
        {
            final String txt  // I18N TODO: Check client; might already substitute text based on SV value
                = /*I*/"Incorrect password for '" + userName + "'." /*18N*/;

            c.put(SOCStatusMessage.toCmd(replySV, c.getVersion(), txt));
            return false;
        }

        //
        // Update the last login time
        //
        //Date currentTime = new Date();

        //SOCDBHelper.updateLastlogin(userName, currentTime.getTime());
        //
        // Record the login info for this user
        //
        //SOCDBHelper.recordLogin(userName, c.host(), currentTime.getTime());
        return true;
    }

    /**
     * Is this username on the {@link #databaseUserAdmins} whitelist, if that whitelist is being used?
     * @param uname  Username to check; if null, returns false.
     * @param requireList  If true, the whitelist cannot be null.
     *     If false, this function returns true for any user when we aren't using the whitelist and its field is null.
     * @return  True only if the user is on the whitelist, or there is no list and {@code requireList} is false
     * @since 1.1.20
     */
    private boolean isUserDBUserAdmin(final String uname, final boolean requireList)
    {
        if (uname == null)
            return false;

        // Check if we're using a user admin whitelist, and if uname's on it; this check is also in handleCREATEACCOUNT.

        if (databaseUserAdmins == null)
            return ! requireList;
        else
            return databaseUserAdmins.contains(uname);
    }

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
     */
    private void handleVERSION(StringConnection c, SOCVersion mes)
    {
        if (c == null)
            return;

        setClientVersSendGamesOrReject(c, mes.getVersionNumber(), mes.localeOrFeats, true);
    }

    /**
     * Set client's version and locale, and check against minimum required version {@link #CLI_VERSION_MIN}.
     * If version is too low, send {@link SOCRejectConnection REJECTCONNECTION}.
     * If we haven't yet sent the game list, send now.
     * If we've already sent the game list, send changes based on true version.
     *<P>
     * Along with the game list, the client will need to know the game option info.
     * This is sent when the client asks (after VERSION) for {@link SOCGameOptionGetInfos GAMEOPTIONGETINFOS}.
     * Game options are sent after client version is known, so the list of
     * sent options is based on client version.
     *<P>
     *<B>I18N:</B> If client doesn't send a locale string, the default locale {@code en_US} is used.
     * Robot clients will get the default locale and localeStr here, those will be cleared soon in
     * {@link #handleIMAROBOT(StringConnection, SOCImARobot)}.
     *<P>
     *<b>Locks:</b> To set the version, will synchronize briefly on {@link Server#unnamedConns unnamedConns}.
     * If {@link StringConnection#getVersion() c.getVersion()} is already == cvers,
     * don't bother to lock and set it.
     *<P>
     * Package access (not private) is strictly for use of {@link SOCClientData.SOCCDCliVersionTask#run()}.
     *
     * @param c     Client's connection
     * @param cvers Version reported by client, or assumed version if no report
     * @param clocale  Locale reported by client, or null if none given (was added to {@link SOCVersion} in 2.0.00)
     * @param isKnown Is this the client's definite version, or just an assumed one?
     *                Affects {@link StringConnection#isVersionKnown() c.isVersionKnown}.
     *                Can set the client's known version only once; a second "known" call with
     *                a different cvers will be rejected.
     * @return True if OK, false if rejected
     */
    boolean setClientVersSendGamesOrReject
        (StringConnection c, final int cvers, String clocale, final boolean isKnown)
    {
        final int prevVers = c.getVersion();
        final boolean wasKnown = c.isVersionKnown();

        SOCClientData scd = (SOCClientData) c.getAppData();

        // Message to send/log if client must be disconnected
        String rejectMsg = null;
        String rejectLogMsg = null;

        if (clocale == null)
            clocale = "en_US";  // backwards compatibility with clients older than v2.0.00

        final int hashIdx = clocale.indexOf("_#");
        if (hashIdx != -1)
        {
            // extended info from java 1.7+ Locale.toString();
            // if our server is an older JRE version, strip that out.
            final String jreVersStr = System.getProperty("java.specification.version");
            if (jreVersStr.startsWith("1.5") || jreVersStr.startsWith("1.6"))
            {
                clocale = clocale.substring(0, hashIdx);
            }
        }
        scd.localeStr = clocale;
        try
        {
            scd.locale = I18n.parseLocale(clocale);
        } catch (IllegalArgumentException e) {
            rejectMsg = "Sorry, cannot parse your locale.";
            rejectLogMsg = "Rejected client: Cannot parse locale";  // unsanitized data, don't print clocale to log
        }
        c.setI18NStringManager(SOCStringManager.getServerManagerForClient(scd.locale), clocale);

        if (prevVers == -1)
            scd.clearVersionTimer();

        if (prevVers != cvers)
        {
            synchronized (unnamedConns)
            {
                c.setVersion(cvers, isKnown);
            }
        } else if (wasKnown)
        {
            return true;  // <--- Early return: Already knew it ----
        }

        if (cvers < CLI_VERSION_MIN)
        {
            if (cvers > 0)
                rejectMsg = "Sorry, your client version number " + cvers + " is too old, version ";
            else
                rejectMsg = "Sorry, your client version is too old, version number ";
            rejectMsg += Integer.toString(CLI_VERSION_MIN)
                + " (" + CLI_VERSION_MIN_DISPLAY + ") or above is required.";
            rejectLogMsg = "Rejected client: Version " + cvers + " too old";
        }
        if (wasKnown && isKnown && (cvers != prevVers))
        {
            // can't change the version once known
            rejectMsg = "Sorry, cannot report two different versions.";
            rejectLogMsg = "Rejected client: Already gave VERSION(" + prevVers
                + "), now says VERSION(" + cvers + ")";
        }

        if (rejectMsg != null)
        {
            c.put(new SOCRejectConnection(rejectMsg).toCmd());
            c.disconnectSoft();
            System.out.println(rejectLogMsg);
            return false;
        }

        // Send game list?
        // Will check c.getAppData().hasSentGameList() flag.
        // prevVers is ignored unless already sent game list.
        sendGameList(c, prevVers);

        // Warn if debug commands are allowed.
        // This will be displayed in the client's status line (v1.1.17 and newer).
        if (allowDebugUser)
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_OK_DEBUG_MODE_ON, cvers,
                     c.getLocalized("member.welcome.debug")));  // "Debugging is On.  Welcome to Java Settlers of Catan!"

        // Increment version stats; currently assumes single-threaded access to the map.
        // We don't know yet if client is a bot, so bots are included in the stats.
        // (If this is not wanted, the bot could be subtracted at handleIMAROBOT.)
        final Integer cversObj = Integer.valueOf(cvers);
        final int prevCount;
        Integer prevCObj = clientPastVersionStats.get(cversObj);
        prevCount = (prevCObj != null) ? prevCObj.intValue() : 0;
        clientPastVersionStats.put(cversObj, Integer.valueOf(1 + prevCount));

        // This client version is OK to connect
        return true;
    }

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
     */
    private void handleJOIN(StringConnection c, SOCJoin mes)
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
            if (! setClientVersSendGamesOrReject(c, CLI_VERSION_ASSUMED_GUESS, null, false))
                return;  // <--- Discon and Early return: Client too old ---
            cliVers = c.getVersion();
        }

        /**
         * Check that the nickname is ok, check password if supplied; if not ok, sends a SOCStatusMessage.
         */
        final int authResult = authOrRejectClientUser(c, msgUser, msgPass, cliVers, true, false);
        if (authResult == AUTH_OR_REJECT__FAILED)
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
            && (CLIENT_MAX_CREATE_CHANNELS >= 0)
            && (CLIENT_MAX_CREATE_CHANNELS <= ((SOCClientData) c.getAppData()).getcurrentCreatedChannels()))
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWCHANNEL_TOO_MANY_CREATED, cliVers,
                     SOCStatusMessage.MSG_SV_NEWCHANNEL_TOO_MANY_CREATED + Integer.toString(CLIENT_MAX_CREATE_CHANNELS)));
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
                connectToChannel(c, ch);
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
            broadcast(SOCNewChannel.toCmd(ch));
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
        messageToChannel(ch, new SOCJoin(msgUser, "", "dummyhost", ch));
    }

    /**
     * Handle the "leave a channel" message
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     */
    private void handleLEAVE(StringConnection c, SOCLeave mes)
    {
        D.ebugPrintln("handleLEAVE: " + mes);

        if (c == null)
            return;

        boolean destroyedChannel = false;
        channelList.takeMonitorForChannel(mes.getChannel());

        try
        {
            destroyedChannel = leaveChannel(c, mes.getChannel(), true, false);
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in handleLEAVE");
        }

        channelList.releaseMonitorForChannel(mes.getChannel());

        if (destroyedChannel)
        {
            broadcast(SOCDeleteChannel.toCmd(mes.getChannel()));
        }
    }

    /**
     * Handle the "I'm a robot" message.
     * Robots send their {@link SOCVersion} before sending this message.
     * Their version is checked here, must equal server's version.
     * For stability and control, the cookie in this message must
     * match this server's {@link #robotCookie}.
     *<P>
     * Bot tuning parameters are sent here to the bot.  Its {@link SOCClientData#isRobot} flag is set.  Its
     * {@link SOCClientData#locale} is cleared, but not its
     * {@link StringConnection#setI18NStringManager(SOCStringManager, String)}.
     *<P>
     * Before connecting here, bots are named and started in {@link #setupLocalRobots(int, int)}.
     * Default bot params are {@link #ROBOT_PARAMS_SMARTER} if the robot name starts with "robot "
     * or {@link #ROBOT_PARAMS_DEFAULT} otherwise (starts with "droid ").
     *<P>
     * Sometimes a bot disconnects and quickly reconnects.  In that case
     * this method removes the disconnect/reconnect messages from
     * {@link Server#cliConnDisconPrintsPending} so they won't be printed.
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     */
    private void handleIMAROBOT(StringConnection c, SOCImARobot mes)
    {
        if (c == null)
            return;

        final String botName = mes.getNickname();

        /**
         * Check the cookie given by this bot.
         */
        if ((robotCookie != null) && ! robotCookie.equals(mes.getCookie()))
        {
            final String rejectMsg = "Cookie contents do not match the running server.";
            c.put(new SOCRejectConnection(rejectMsg).toCmd());
            c.disconnectSoft();
            System.out.println("Rejected robot " + botName + ": Wrong cookie");
            return;  // <--- Early return: Robot client didn't send our cookie value ---
        }

        /**
         * Check the reported version; if none, assume 1000 (1.0.00)
         */
        final int srvVers = Version.versionNumber();
        int cliVers = c.getVersion();
        final String rbc = mes.getRBClass();
        final boolean isBuiltIn = (rbc == null)
            || (rbc.equals(SOCImARobot.RBCLASS_BUILTIN));
        if (isBuiltIn)
        {
            if (cliVers != srvVers)
            {
                String rejectMsg = "Sorry, robot client version does not match, version number "
                    + Version.version(srvVers) + " is required.";
                c.put(new SOCRejectConnection(rejectMsg).toCmd());
                c.disconnectSoft();
                System.out.println("Rejected robot " + botName + ": Version "
                    + cliVers + " does not match server version");

                return;  // <--- Early return: Robot client too old ---
            } else {
                System.out.println("Robot arrived: " + botName + ": built-in type");
            }
        } else {
            System.out.println("Robot arrived: " + botName + ": type " + rbc);
        }

        /**
         * Check that the nickname is ok
         */
        if ((c.getData() == null) && (0 != checkNickname(botName, c, false)))
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                     MSG_NICKNAME_ALREADY_IN_USE));
            SOCRejectConnection rcCommand = new SOCRejectConnection(MSG_NICKNAME_ALREADY_IN_USE);
            c.put(rcCommand.toCmd());
            System.err.println("Robot login attempt, name already in use: " + botName);
            // c.disconnect();
            c.disconnectSoft();

            return;
        }

        // Idle robots disconnect and reconnect every so often (socket timeout).
        // In case of disconnect-reconnect, don't print the error or re-arrival debug announcements.
        // The robot's nickname is used as the key for the disconnect announcement.
        {
            ConnExcepDelayedPrintTask depart
                = cliConnDisconPrintsPending.get(botName);
            if (depart != null)
            {
                depart.cancel();
                cliConnDisconPrintsPending.remove(botName);
                ConnExcepDelayedPrintTask arrive
                    = cliConnDisconPrintsPending.get(c);
                if (arrive != null)
                {
                    arrive.cancel();
                    cliConnDisconPrintsPending.remove(c);
                }
            }
        }

        SOCRobotParameters params = null;
        //
        // send the current robot parameters
        //
        try
        {
            params = SOCDBHelper.retrieveRobotParams(botName);
            if ((params != null) && D.ebugIsEnabled())
                D.ebugPrintln("*** Robot Parameters for " + botName + " = " + params);
        }
        catch (SQLException sqle)
        {
            System.err.println("Error retrieving robot parameters from db: Using defaults.");
        }

        if (params == null)
            if (botName.startsWith("robot "))
                params = ROBOT_PARAMS_SMARTER;  // uses SOCRobotDM.SMART_STRATEGY
            else  // startsWith("droid ")
                params = ROBOT_PARAMS_DEFAULT;  // uses SOCRobotDM.FAST_STRATEGY

        c.put(SOCUpdateRobotParams.toCmd(params));

        //
        // add this connection to the robot list
        //
        c.setData(botName);
        c.setHideTimeoutMessage(true);
        robots.addElement(c);
        SOCClientData scd = (SOCClientData) c.getAppData();
        scd.isRobot = true;
        scd.isBuiltInRobot = isBuiltIn;
        if (! isBuiltIn)
            scd.robot3rdPartyBrainClass = rbc;

        scd.locale = null;  // bots don't care about message text contents
        scd.localeStr = null;
        // Note that if c.setI18NStringManager was called, it's not cleared here

        nameConnection(c);
    }

    /**
     * Handle game text messages, including debug commands.
     * Was part of processCommand before 1.1.07.
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
     * Those are processed in {@link #processDebugCommand(StringConnection, String, String, String)}.
     *
     * @since 1.1.07
     */
    private void handleGAMETEXTMSG(StringConnection c, SOCGameTextMsg gameTextMsgMes)
    {
        //createNewGameEventRecord();
        //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
        final String gaName = gameTextMsgMes.getGame();
        recordGameEvent(gaName, gameTextMsgMes.toCmd());

        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;  // <---- early return: no game by that name ----

        final String plName = (String) c.getData();

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
                messageToPlayerKeyed(c, gaName, "reply.addtime.practice.never");  // ">>> Practice games never expire."
            } else if (ga.getGameState() >= SOCGame.OVER) {
                messageToPlayerKeyed(c, gaName, "reply.addtime.game_over");  // "This game is over, cannot extend its time."
            } else {
                // check game time currently remaining: if already more than
                // the original GAME_TIME_EXPIRE_MINUTES, don't add more now.
                final long now = System.currentTimeMillis();
                long exp = ga.getExpiration();
                int minRemain = (int) ((exp - now) / (60 * 1000));

                if (minRemain > SOCGameListAtServer.GAME_TIME_EXPIRE_MINUTES)
                {
                    messageToPlayerKeyed(c, gaName, "reply.addtime.not_expire_soon", Integer.valueOf(minRemain));
                        // "Ask again later: This game does not expire soon, it has {0} minutes remaining."
                } else {
                    exp += (GAME_TIME_EXPIRE_ADDTIME_MINUTES * 60 * 1000);
                    minRemain += GAME_TIME_EXPIRE_ADDTIME_MINUTES;

                    ga.setExpiration(exp);
                    messageToGameKeyed(ga, true, "reply.addtime.extended");  // ">>> Game time has been extended."
                    messageToGameKeyed(ga, true, "stats.game.willexpire.urgent",
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
            messageToPlayer(c, gaName,
                "Java Settlers Server " +Version.versionNumber() + " (" + Version.version() + ") build " + Version.buildnum());
        }
        else if (cmdTxtUC.startsWith("*STATS*"))
        {
            final long diff = System.currentTimeMillis() - startTime;
            final long hours = diff / (60 * 60 * 1000),
                  minutes = (diff - (hours * 60 * 60 * 1000)) / (60 * 1000),
                  seconds = (diff - (hours * 60 * 60 * 1000) - (minutes * 60 * 1000)) / 1000;
            Runtime rt = Runtime.getRuntime();
            if (hours < 24)
            {
                messageToPlayer(c, gaName, "> Uptime: " + hours + ":" + minutes + ":" + seconds);
            } else {
                final int days = (int) (hours / 24),
                          hr   = (int) (hours - (days * 24L));
                messageToPlayer(c, gaName, "> Uptime: " + days + "d " + hr + ":" + minutes + ":" + seconds);
            }
            messageToPlayer(c, gaName, "> Connections since startup: " + numberOfConnections);
            messageToPlayer(c, gaName, "> Current named connections: " + getNamedConnectionCount());
            messageToPlayer(c, gaName, "> Current connections including unnamed: " + getCurrentConnectionCount());
            messageToPlayer(c, gaName, "> Total Users: " + numberOfUsers);
            messageToPlayer(c, gaName, "> Games started: " + numberOfGamesStarted);
            messageToPlayer(c, gaName, "> Games finished: " + numberOfGamesFinished);
            messageToPlayer(c, gaName, "> Total Memory: " + rt.totalMemory());
            messageToPlayer(c, gaName, "> Free Memory: " + rt.freeMemory());
            final int vers = Version.versionNumber();
            messageToPlayer(c, gaName, "> Version: "
                + vers + " (" + Version.version() + ") build " + Version.buildnum());

            if (! clientPastVersionStats.isEmpty())
            {
                if (clientPastVersionStats.size() == 1)
                {
                    messageToPlayer(c, gaName, "> Client versions since startup: all "
                            + Version.version(clientPastVersionStats.keySet().iterator().next()));
                } else {
                    // TODO sort it
                    messageToPlayer(c, gaName, "> Client versions since startup: (includes bots)");
                    for (Integer v : clientPastVersionStats.keySet())
                        messageToPlayer(c, gaName, ">   " + Version.version(v) + ": " + clientPastVersionStats.get(v));
                }
            }

            // show range of current game's member client versions if not server version (added to *STATS* in 1.1.19)
            if ((ga.clientVersionLowest != vers) || (ga.clientVersionLowest != ga.clientVersionHighest))
                messageToPlayer(c, gaName, "> This game's client versions: "
                    + Version.version(ga.clientVersionLowest) + " - " + Version.version(ga.clientVersionHighest));

            processDebugCommand_gameStats(c, gaName, ga, false);
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
                ((allowDebugUser && plName.equals("debug"))
                || (c instanceof LocalStringConnection));

            if (cmdTxtUC.startsWith("*HELP"))
            {
                for (int i = 0; i < GENERAL_COMMANDS_HELP.length; ++i)
                    messageToPlayer(c, gaName, GENERAL_COMMANDS_HELP[i]);

                if ((userIsDebug && ! (c instanceof LocalStringConnection))  // no user admins in practice games
                    || isUserDBUserAdmin(plName, true))
                {
                    messageToPlayer(c, gaName, ADMIN_COMMANDS_HEADING);
                    for (int i = 0; i < ADMIN_USER_COMMANDS_HELP.length; ++i)
                        messageToPlayer(c, gaName, ADMIN_USER_COMMANDS_HELP[i]);
                }

                if (userIsDebug)
                {
                    for (int i = 0; i < DEBUG_COMMANDS_HELP.length; ++i)
                        messageToPlayer(c, gaName, DEBUG_COMMANDS_HELP[i]);

                    GameHandler hand = gameList.getGameTypeHandler(gaName);
                    if (hand != null)
                    {
                        final String[] GAMETYPE_DEBUG_HELP = hand.getDebugCommandsHelp();
                        if (GAMETYPE_DEBUG_HELP != null)
                            for (int i = 0; i < GAMETYPE_DEBUG_HELP.length; ++i)
                                messageToPlayer(c, gaName, GAMETYPE_DEBUG_HELP[i]);
                    }
                }
            }
            else
            {
                boolean isCmd = userIsDebug && processDebugCommand(c, ga.getName(), cmdText, cmdTxtUC);

                if (! isCmd)
                    //
                    // Send the message to the members of the game
                    //
                    messageToGame(gaName, new SOCGameTextMsg(gaName, plName, cmdText));
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
    private void processDebugCommand_gameStats
        (StringConnection c, final String gaName, SOCGame gameData, final boolean isCheckTime)
    {
        if (gameData == null)
            return;

        messageToPlayerKeyed(c, gaName, "stats.game.title");  // "-- Game statistics: --"
        messageToPlayerKeyed(c, gaName, "stats.game.rounds", gameData.getRoundCount());  // Rounds played: 20

        // player's stats
        if (c.getVersion() >= SOCPlayerStats.VERSION_FOR_RES_ROLL)
        {
            SOCPlayer cp = gameData.getPlayer((String) c.getData());
            if (cp != null)
                messageToPlayer(c, new SOCPlayerStats(cp, SOCPlayerStats.STYPE_RES_ROLL));
        }

        // time
        Date gstart = gameData.getStartTime();
        if (gstart != null)
        {
            long gameSeconds = ((new Date().getTime() - gstart.getTime())+500L) / 1000L;
            long gameMinutes = (gameSeconds+29L)/60L;
            messageToPlayerKeyed(c, gaName, "stats.game.startedago", gameMinutes);  // "This game started 5 minutes ago."
            // Ignore possible "1 minutes"; that game is too short to worry about.
        }

        if (! gameData.isPractice)   // practice games don't expire
        {
            // If isCheckTime, use ">>>" in message text to mark as urgent:
            // ">>> This game will expire in 15 minutes."
            messageToPlayerKeyed(c, gaName,
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

                final String uname = (String) c.getData();
                boolean isAdmin = isUserDBUserAdmin(uname, true);
                if (! isAdmin)
                    isAdmin = (allowDebugUser && uname.equals("debug"));
                if (! isAdmin)
                {
                    messageToPlayerKeyed(c, gaName, "reply.must_be_admin.view");
                        // "Must be an administrator to view that."
                    return;
                }

                sendToCli = true;

                if (gname.equals("*") || gname.toUpperCase(Locale.US).equals("ALL"))
                {
                    // Instead of listing the game's members, list all connected clients.
                    // Do as little as possible inside synchronization block.

                    final ArrayList<StringBuilder> sbs = new ArrayList<StringBuilder>();
                    StringBuilder sb = new StringBuilder(c.getLocalized("reply.who.conn_to_srv"));
                        // "Currently connected to server:"
                    sbs.add(sb);
                    sb = new StringBuilder("- ");
                    sbs.add(sb);

                    int nUnnamed;
                    synchronized (unnamedConns)
                    {
                        nUnnamed = unnamedConns.size();

                        Enumeration<StringConnection> ec = getConnections();  // the named ones
                        while (ec.hasMoreElements())
                        {
                            String cname = (String) (ec.nextElement().getData());

                            int L = sb.length();
                            if (L + cname.length() > 50)
                            {
                                sb.append(',');  // TODO I18N list
                                sb = new StringBuilder("- ");
                                sbs.add(sb);
                                L = 2;
                            }

                            if (L > 2)
                                sb.append(", ");  // TODO I18N list with "line wrap"
                            sb.append(cname);
                        }
                    }

                    if (nUnnamed != 0)
                    {
                        final String unnamed = c.getLocalized("reply.who.and_unnamed", Integer.valueOf(nUnnamed));
                            // "and {0} unnamed connections"
                        if (sb.length() + unnamed.length() + 2 > 50)
                        {
                            sb.append(',');  // TODO I18N list
                            sb = new StringBuilder("- ");
                            sb.append(unnamed);
                            sbs.add(sb);
                        } else {
                            sb.append(", ");  // TODO I18N list
                            sb.append(unnamed);
                        }
                    }

                    for (StringBuilder sbb : sbs)
                        messageToPlayer(c, gaName, sbb.toString());

                    return;  // <--- Early return; Not listing a game's members ---
                }

                if (gameList.isGame(gname))
                {
                    gaNameWho = gname;
                } else {
                    messageToPlayerKeyed(c, gaName, "reply.game.not.found");  // "Game not found."
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
                messageToGameKeyed(ga, false, "reply.game_members.this");  // "This game's members:"
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
            messageToPlayerKeyed(c, gaName, "reply.game_members.of", gaNameWho);  // "Members of game {0}:"

        Enumeration<StringConnection> membersEnum = gameMembers.elements();
        while (membersEnum.hasMoreElements())
        {
            StringConnection conn = membersEnum.nextElement();
            String mNameStr = "> " + conn.getData();

            if (sendToCli)
                messageToPlayer(c, gaName, mNameStr);
            else
                messageToGame(gaName, mNameStr);
        }
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
            final int authResult = authOrRejectClientUser(c, mesUser, mes.password, cliVersion, false, false);

            if (authResult == AUTH_OR_REJECT__FAILED)
                return;  // <---- Early return; authOrRejectClientUser sent the status message ----

            if (mes.role.equals(SOCAuthRequest.ROLE_USER_ADMIN))
            {
                // Check if we're using a user admin whitelist
                if (! isUserDBUserAdmin(mesUser, false))
                {
                    c.put(SOCStatusMessage.toCmd
                            (SOCStatusMessage.SV_ACCT_NOT_CREATED_DENIED, cliVersion,
                             c.getLocalized("account.create.not_auth")));
                                // "Your account is not authorized to create accounts."

                    printAuditMessage
                        (mesUser,
                         "Requested jsettlers account creation, this requester not on account admin whitelist",
                         null, null, c.host());

                    return;
                }
            }

            // no role-specific problems: complete the authentication
            c.setData(mesUser);
            nameConnection(c, false);
        }

        c.put(SOCStatusMessage.toCmd
                (SOCStatusMessage.SV_OK, c.getLocalized("member.welcome")));  // "Welcome to Java Settlers of Catan!"
    }

    /**
     * Handle the "join a game" message: Join or create a game.
     * Will join the game, or return a STATUSMESSAGE if nickname is not OK.
     * Clients can join game as an observer, if they don't SITDOWN after joining.
     *<P>
     * If client hasn't yet sent its version, assume is version 1.0.00 ({@link #CLI_VERSION_ASSUMED_GUESS}), disconnect if too low.
     * If the client is too old to join a specific game, return a STATUSMESSAGE. (since 1.1.06)
     *
     * @param c  the connection that sent the message
     * @param mes  the message
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
            if (! setClientVersSendGamesOrReject(c, CLI_VERSION_ASSUMED_GUESS, null, false))
                return;  // <--- Early return: Client too old ---
        }

        createOrJoinGameIfUserOK
            (c, mes.getNickname(), mes.getPassword(), mes.getGame(), null);
    }

    /**
     * Check username/password and create new game, or join game.
     * Called by handleJOINGAME and handleNEWGAMEWITHOPTIONSREQUEST.
     * JOINGAME or NEWGAMEWITHOPTIONSREQUEST may be the first message with the
     * client's username and password, so c.getData() may be null.
     * Assumes client's version is already received or guessed.
     *<P>
     * Game name and player name have a maximum length and some disallowed characters; see parameters.
     * Check the client's {@link SOCClientData#getCurrentCreatedGames()} vs {@link #CLIENT_MAX_CREATE_GAMES}.
     *<P>
     * If client is replacing/taking over their own lost connection,
     * first tell them they're rejoining all their other games.
     * That way, the requested game's window will appear last,
     * not hidden behind the others.
     *<P>
     *<b>Process if gameOpts != null:</b>
     *<UL>
     *  <LI> if game with this name already exists, respond with
     *      STATUSMESSAGE({@link SOCStatusMessage#SV_NEWGAME_ALREADY_EXISTS SV_NEWGAME_ALREADY_EXISTS})
     *  <LI> compare cli's param name-value pairs, with srv's known values. <br>
     *      - if any are above/below max/min, clip to the max/min value <br>
     *      - if any are unknown, resp with
     *        STATUSMESSAGE({@link SOCStatusMessage#SV_NEWGAME_OPTION_UNKNOWN SV_NEWGAME_OPTION_UNKNOWN}) <br>
     *      - if any are too new for client's version, resp with
     *        STATUSMESSAGE({@link SOCStatusMessage#SV_NEWGAME_OPTION_VALUE_TOONEW SV_NEWGAME_OPTION_VALUE_TOONEW}) <br>
     *      Comparison is done by {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean)}.
     *  <LI> if ok: create new game with params;
     *      socgame will calc game's minCliVersion,
     *      and this method will check that against cli's version.
     *  <LI> announce to all players using NEWGAMEWITHOPTIONS;
     *       older clients get NEWGAME, won't see the options
     *  <LI> send JOINGAMEAUTH to requesting client, via {@link GameHandler#joinGame(SOCGame, StringConnection, boolean, boolean)}
     *  <LI> send game status details to requesting client, via {@link GameHandler#joinGame(SOCGame, StringConnection, boolean, boolean)}
     *       -- If the game is already in progress, this will include all pieces on the board, and the rest of game state.
     *</UL>
     *
     * @param c connection requesting the game, must not be null
     * @param msgUser username of client in message. Must pass {@link SOCMessage#isSingleLineAndSafe(String)}
     *                  and be at most {@link #PLAYER_NAME_MAX_LENGTH} characters.
     *                  Calls {@link String#trim() msgUser.trim()} before checking length.
     * @param msgPass password of client in message; will be {@link String#trim() trim()med}.
     * @param gameName  name of game to create/join. Must pass {@link SOCMessage#isSingleLineAndSafe(String)}
     *                  and be at most {@link #GAME_NAME_MAX_LENGTH} characters.
     *                  Calls {@link String#trim() gameName.trim()} before checking length.
     *                  Game name {@code "*"} is also rejected to avoid conflicts with admin commands.
     * @param gameOpts  if game has options, contains {@link SOCGameOption} to create new game; if not null, will not join an existing game.
     *                  Will validate and adjust by calling
     *                  {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean)}
     *                  with <tt>doServerPreadjust</tt> true.
     *
     * @since 1.1.07
     */
    private void createOrJoinGameIfUserOK
        (StringConnection c, String msgUser, String msgPass,
         String gameName, Map<String, SOCGameOption> gameOpts)
    {
        System.err.println("L4885 createOrJoinGameIfUserOK at " + System.currentTimeMillis());
        if (msgUser != null)
            msgUser = msgUser.trim();
        if (msgPass != null)
            msgPass = msgPass.trim();
        if (gameName != null)
            gameName = gameName.trim();
        final int cliVers = c.getVersion();

        /**
         * Check that the nickname is ok, check password if supplied; if not ok, sends a SOCStatusMessage.
         */
        final int authResult = authOrRejectClientUser(c, msgUser, msgPass, cliVers, true, true);
        if (authResult == AUTH_OR_REJECT__FAILED)
            return;  // <---- Early return ----

        final boolean isTakingOver = (authResult == AUTH_OR_REJECT__TAKING_OVER);

        /**
         * Check that the game name is ok
         */
        if ( (! SOCMessage.isSingleLineAndSafe(gameName))
             || "*".equals(gameName))
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWGAME_NAME_REJECTED, cliVers,
                     SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED));
              // "This game name is not permitted, please choose a different name."

              return;  // <---- Early return ----
        }
        if (gameName.length() > GAME_NAME_MAX_LENGTH)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWGAME_NAME_TOO_LONG, cliVers,
                     SOCStatusMessage.MSG_SV_NEWGAME_NAME_TOO_LONG + Integer.toString(GAME_NAME_MAX_LENGTH)));
            // Please choose a shorter name; maximum length: 30

            return;  // <---- Early return ----
        }

        System.err.println("L4965 past user,pw check at " + System.currentTimeMillis());

        /**
         * If creating a new game, ensure they are below their max game count.
         * (Don't limit max games on the practice server.)
         */
        if ((! gameList.isGame(gameName))
            && ((strSocketName == null) || ! strSocketName.equals(PRACTICE_STRINGPORT))
            && (CLIENT_MAX_CREATE_GAMES >= 0)
            && (CLIENT_MAX_CREATE_GAMES <= ((SOCClientData) c.getAppData()).getCurrentCreatedGames()))
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWGAME_TOO_MANY_CREATED, cliVers,
                     SOCStatusMessage.MSG_SV_NEWGAME_TOO_MANY_CREATED + Integer.toString(CLIENT_MAX_CREATE_GAMES)));
            // Too many of your games still active; maximum: 5

            return;  // <---- Early return ----
        }

        /**
         * If we have game options, we're being asked to create a new game.
         * Validate them and ensure the game doesn't already exist.
         * For SOCScenarios, adjustOptionsToKnown will recognize game opt "SC".
         */
        System.err.println("L4965 game opts check at " + System.currentTimeMillis());
        if (gameOpts != null)
        {
            if (gameList.isGame(gameName))
            {
                c.put(SOCStatusMessage.toCmd
                      (SOCStatusMessage.SV_NEWGAME_ALREADY_EXISTS, cliVers,
                       SOCStatusMessage.MSG_SV_NEWGAME_ALREADY_EXISTS));
                // "A game with this name already exists, please choose a different name."

                return;  // <---- Early return ----
            }

            // Make sure all options are known.  If has game opt "SC" for scenarios,
            // also adds that scenario's options into gameOpts.
            final StringBuilder optProblems = SOCGameOption.adjustOptionsToKnown(gameOpts, null, true);
            if (optProblems != null)
            {
                c.put(SOCStatusMessage.toCmd
                      (SOCStatusMessage.SV_NEWGAME_OPTION_UNKNOWN, cliVers,
                       "Unknown game option(s) were requested, cannot create this game. " + optProblems));

                return;  // <---- Early return ----
            }
        }

        /**
         * Try to create or add player to game, and tell the client that everything is ready;
         * if game doesn't yet exist, it's created in connectToGame, and announced
         * there to all clients.
         *<P>
         * If client's version is too low (based on game options, etc),
         * connectToGame will throw an exception; tell the client if that happens.
         *<P>
         * If rejoining after a lost connection, first rejoin all their other games.
         */
        System.err.println("L5034 ready connectToGame at " + System.currentTimeMillis());
        try
        {
            if (isTakingOver)
            {
                /**
                 * Rejoin the requested game.
                 * First, rejoin all other games of this client.
                 * That way, the requested game's window will
                 * appear last, not hidden behind the others.
                 * For each game, calls joinGame to send JOINGAMEAUTH
                 * and the entire state of the game to client.
                 */
                Vector<SOCGame> allConnGames = gameList.memberGames(c, gameName);
                if (allConnGames.size() == 0)
                {
                    c.put(SOCStatusMessage.toCmd(SOCStatusMessage.SV_OK,
                            /*I*/"You've taken over the connection, but aren't in any games."/*18N*/ ));
                } else {
                    // Send list backwards: requested game will be sent last.
                    for (int i = allConnGames.size() - 1; i >= 0; --i)
                        joinGame(allConnGames.elementAt(i), c, false, true);
                }
            }
            else if (connectToGame(c, gameName, gameOpts))  // join or create the game
            {
                /**
                 * send JOINGAMEAUTH to client,
                 * send the entire state of the game to client,
                 * send client join event to other players of game
                 */
                System.err.println("L5065 past connectToGame at " + System.currentTimeMillis());
                SOCGame gameData = gameList.getGameData(gameName);

                if (gameData != null)
                {
                    joinGame(gameData, c, false, false);
                }
                System.err.println("L5072 past joinGame at " + System.currentTimeMillis());
            }
        } catch (SOCGameOptionVersionException e)
        {
            // Let them know they can't join; include the game's version.
            // This cli asked to created it, otherwise gameOpts would be null.
            c.put(SOCStatusMessage.toCmd
              (SOCStatusMessage.SV_NEWGAME_OPTION_VALUE_TOONEW, cliVers,
                "Cannot create game with these options; requires version "
                + Integer.toString(e.gameOptsVersion)
                + SOCMessage.sep2_char + gameName
                + SOCMessage.sep2_char + e.problemOptionsList()));
        } catch (IllegalArgumentException e)
        {
            SOCGame game = gameList.getGameData(gameName);
            if (game == null)
            {
                D.ebugPrintStackTrace(e, "Exception in createOrJoinGameIfUserOK");
            } else {
                // Let them know they can't join; include the game's version.
                c.put(SOCStatusMessage.toCmd
                  (SOCStatusMessage.SV_CANT_JOIN_GAME_VERSION, cliVers,
                    "Cannot join game; requires version "
                    + Integer.toString(game.getClientVersionMinRequired())
                    + ": " + gameName));
            }
        }
        System.err.println("L5099 done createOrJoinGameIfUserOK at " + System.currentTimeMillis());

    }  //  createOrJoinGameIfUserOK

    /**
     * Start a few robot-only games if {@link #numRobotOnlyGamesRemaining} &gt; 0.
     *<P>
     * <B>Locks:</b> May or may not have {@link SOCGameList#takeMonitor()} when calling;
     * see {@code hasGameListMonitor} parameter.  If not already held, this method takes and releases that monitor.
     *
     * @param hasGameListMonitor  True if caller holds the {@link SOCGameList#takeMonitor()} lock already
     * @since 2.0.00
     */
    private void startRobotOnlyGames(final boolean hasGameListMonitor)
    {
        if (numRobotOnlyGamesRemaining <= 0)
            return;

        // TODO start more than one here
        // TODO property to control # "a few" games started here

        String gaName = "~botsOnly~" + numRobotOnlyGamesRemaining;

        SOCGame newGame = createGameAndBroadcast
            (null, gaName, SOCGameOption.getAllKnownOptions(), Version.versionNumber(), true, hasGameListMonitor);

        if (newGame != null)
        {
            --numRobotOnlyGamesRemaining;

            System.out.println("Started bot-only game: " + gaName);
            newGame.setGameState(SOCGame.READY);
            readyGameAskRobotsJoin(newGame, null, 0);
        } else {
            // TODO game name existed
        }
    }

    /**
     * Handle the "leave game" message
     *
     * @param c  the connection that sent the message
     * @param mes  the message
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
     * Handle a member leaving the game, from {@link #handleLEAVEGAME(StringConnection, SOCLeaveGame)}.
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
            gameDestroyed = leaveGame(c, gaName, true, false);
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in handleLEAVEGAME (leaveGame)");
        }

        gameList.releaseMonitorForGame(gaName);

        if (gameDestroyed)
        {
            broadcast(SOCDeleteGame.toCmd(gaName));
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
        Vector<SOCReplaceRequest> requests = robotDismissRequests.get(gaName);

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
                sitDown(ga, req.getArriving(), pn, isRobot, false);
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
            resetBoardAndNotify_finish(gr, cg);  // TODO locks?
    }

    /**
     * handle "sit down" message
     *
     * @param c  the connection that sent the message
     * @param mes  the message
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
            Vector<StringConnection> joinRequests = robotJoinRequests.get(gaName);
            if (joinRequests != null)
                isBotJoinRequest = joinRequests.removeElement(c);
        }

        /**
         * make sure a person isn't sitting here already;
         * can't sit at a vacant seat after everyone has placed 1st settlement+road;
         * if a robot is sitting there, dismiss the robot.
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
                    StringConnection robotCon = getConnection(seatedPlayer.getName());
                    robotCon.put(SOCRobotDismiss.toCmd(gaName));

                    /**
                     * this connection has to wait for the robot to leave
                     * and then it can sit down
                     */
                    Vector<SOCReplaceRequest> disRequests = robotDismissRequests.get(gaName);
                    SOCReplaceRequest req = new SOCReplaceRequest(c, robotCon, mes);

                    if (disRequests == null)
                    {
                        disRequests = new Vector<SOCReplaceRequest>();
                        disRequests.addElement(req);
                        robotDismissRequests.put(gaName, disRequests);
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
            sitDown(ga, c, pn, mes.isRobot(), false);
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
                messageToPlayerKeyed(c, gaName, "member.sit.game.started");
                    // "This game has already started; to play you must take over a robot."
            } else if (gameIsFull) {
                messageToPlayerKeyed(c, gaName, "member.sit.game.full");
                    // "This game is full; you cannot sit down."
            }
        }
    }

    /**
     * handle "start game" message.  Game state must be NEW, or this message is ignored.
     * {@link #readyGameAskRobotsJoin(SOCGame, StringConnection[], int) Ask some robots} to fill
     * empty seats, or {@link GameHandler#startGame(SOCGame) begin the game} if no robots needed.
     *<P>
     * Called when clients have sat at a new game and a client asks to start it,
     * not called during game board reset.
     *<P>
     * For robot debugging, a client can start and observe a robots-only game if the
     * {@link #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL} property != 0 (including &lt; 0).
     *
     * @param c  the connection that sent the message
     * @param mes  the message
     * @param botsOnly_maxBots  For bot debugging, maximum number of bots to add to the game,
     *     or 0 to fill all empty seats. This parameter is used only when requesting a new
     *     robots-only game using the *STARTBOTGAME* debug command; ignored otherwise.
     */
    private void handleSTARTGAME
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

                    if (0 == getConfigIntProperty(props, PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL, 0))
                    {
                        allowStart = false;
                        messageToGameKeyed(ga, true, "start.player.must.sit");
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
                    messageToGameKeyed(ga, true, "start.only.cannot.lock.all");
                        // "The only player cannot lock all seats. To start the game, other players or robots must join."
                }
                else if (allowStart && ! seatsFull)
                {
                    // Look for some bots

                    if (robots.isEmpty())
                    {
                        if (numPlayers < SOCGame.MINPLAYERS)
                            messageToGameKeyed(ga, true, "start.no.robots.on.server", SOCGame.MINPLAYERS);
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
                        if (numEmpty > robots.size())
                        {
                            final String m;
                            if (anyLocked)
                                m = "start.not.enough.robots";
                                    // "Not enough robots to fill all the seats. Only {0} robots are available."
                            else
                                m = "start.not.enough.robots.lock";
                                    // "Not enough robots to fill all the seats. Lock some seats. Only {0} robots are available."
                            messageToGameKeyed(ga, true, m, robots.size());
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
                                readyGameAskRobotsJoin(ga, null, numEmpty);
                            }
                            catch (IllegalStateException e)
                            {
                                System.err.println("Robot-join problem in game " + gn + ": " + e);

                                // recover, so that human players can still start a game
                                ga.setGameState(SOCGame.NEW);
                                allowStart = false;

                                gameList.takeMonitorForGame(gn);
                                messageToGameKeyed(ga, false, "start.robots.cannot.join.problem", e.getMessage());
                                    // "Sorry, robots cannot join this game: {0}"
                                messageToGameKeyed(ga, false, "start.to.start.without.robots");
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
     * Fill all the unlocked empty seats with robots, by asking them to join.
     * Builds a Vector of StringConnections of robots asked to join,
     * and adds it to the robotJoinRequests table.
     * Game state should be READY.
     * At most {@link SOCGame#getAvailableSeatCount()} robots will
     * be asked.
     *<P>
     * Called by {@link #handleSTARTGAME(StringConnection, SOCStartGame) handleSTARTGAME},
     * {@link #resetBoardAndNotify(String, int) resetBoardAndNotify}.
     *<P>
     * Once the robots have all responded (from their own threads/clients)
     * and joined up, the game can begin.
     *
     * @param ga  Game to ask robots to join
     * @param robotSeats If robotSeats is null, robots are randomly selected.
     *                   If non-null, a MAXPLAYERS-sized array of StringConnections.
     *                   Any vacant non-locked seat, with index i,
     *                   is filled with the robot whose connection is robotSeats[i].
     *                   Other indexes should be null, and won't be used.
     * @param maxBots Maximum number of bots to add, or 0 to fill all empty seats
     *
     * @throws IllegalStateException if {@link SOCGame#getGameState() ga.gamestate} is not READY,
     *         or if {@link SOCGame#getClientVersionMinRequired() ga.version} is
     *         somehow newer than server's version (which is assumed to be robots' version).
     * @throws IllegalArgumentException if robotSeats is not null but wrong length,
     *           or if a robotSeat element is null but that seat wants a robot (vacant non-locked).
     */
    private void readyGameAskRobotsJoin(SOCGame ga, StringConnection[] robotSeats, final int maxBots)
        throws IllegalStateException, IllegalArgumentException
    {
        if (ga.getGameState() != SOCGame.READY)
            throw new IllegalStateException("SOCGame state not READY: " + ga.getGameState());

        if (ga.getClientVersionMinRequired() > Version.versionNumber())
            throw new IllegalStateException("SOCGame version somehow newer than server and robots, it's "
                    + ga.getClientVersionMinRequired());

        Vector<StringConnection> robotRequests = null;

        int[] robotIndexes = null;
        if (robotSeats == null)
        {
            // shuffle the indexes to distribute load
            robotIndexes = robotShuffleForJoin();
        }
        else
        {
            // robotSeats not null: check length
            if (robotSeats.length != ga.maxPlayers)
                throw new IllegalArgumentException("robotSeats Length must be MAXPLAYERS");
        }

        final String gname = ga.getName();
        final Map<String, SOCGameOption> gopts = ga.getGameOptions();
        int seatsOpen = ga.getAvailableSeatCount();
        if ((maxBots > 0) && (maxBots < seatsOpen))
            seatsOpen = maxBots;

        int idx = 0;
        StringConnection[] robotSeatsConns = new StringConnection[ga.maxPlayers];

        for (int i = 0; (i < ga.maxPlayers) && (seatsOpen > 0); i++)
        {
            if (ga.isSeatVacant(i) && (ga.getSeatLock(i) == SOCGame.SeatLockState.UNLOCKED))
            {
                /**
                 * fetch a robot player; game will start when all bots have arrived.
                 * Similar to SOCGameHandler.leaveGame, where a player has left and must be replaced by a bot.
                 */
                if (idx < robots.size())
                {
                    messageToGameKeyed(ga, true, "member.bot.join.fetching");  // "Fetching a robot player..."

                    StringConnection robotConn;
                    if (robotSeats != null)
                    {
                        robotConn = robotSeats[i];
                        if (robotConn == null)
                            throw new IllegalArgumentException("robotSeats[" + i + "] was needed but null");
                    }
                    else
                    {
                        robotConn = robots.get(robotIndexes[idx]);
                    }
                    idx++;
                    --seatsOpen;
                    robotSeatsConns[i] = robotConn;

                    /**
                     * record the request
                     */
                    D.ebugPrintln("@@@ JOIN GAME REQUEST for " + (String) robotConn.getData());
                    if (robotRequests == null)
                        robotRequests = new Vector<StringConnection>();
                    robotRequests.addElement(robotConn);
                }
            }
        }

        if (robotRequests != null)
        {
            // we know it isn't empty,
            // so add to the request table
            robotJoinRequests.put(gname, robotRequests);

            // now, make the requests
            for (int i = 0; i < ga.maxPlayers; ++i)
                if (robotSeatsConns[i] != null)
                    robotSeatsConns[i].put(SOCRobotJoinGameRequest.toCmd(gname, i, gopts));
        }
    }

    /**
     * Temporary debugging; call when "no player gets anything" will be printed after a roll.
     * @param ga  Game data
     * @param message  "no player gets anything" string
     * @since 2.0.00
     */
    void debug_printPieceDiceNumbers(SOCGame ga, String message)
    {
        final int roll = ga.getCurrentDice();
        final SOCBoard board = ga.getBoard();
        boolean hadAny = false;

        System.err.println(" " + roll + "\t" + message);
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            if (ga.isSeatVacant(pn))
                continue;
            SOCPlayer pl = ga.getPlayer(pn);
            hadAny |= debug_printPieceDiceNumbers_pl
                (pl, roll, board, "settle", pl.getSettlements().elements());
            hadAny |= debug_printPieceDiceNumbers_pl
                (pl, roll, board, "city", pl.getCities().elements());
        }
        if (hadAny)
            System.err.println("    ** hadAny true");
        else
            System.err.println("    -- hadAny false");
    }

    /**
     * Temporary debugging; for 1 player.
     * Similar code to {@link SOCGame#getResourcesGainedFromRollPieces}.
     * @return true if this player appears to have a resource on a hex numbered <tt>roll</tt>
     * @since 2.0.00
     */
    private boolean debug_printPieceDiceNumbers_pl
        (SOCPlayer pl, final int roll, final SOCBoard board, final String pieceType, Enumeration<? extends SOCPlayingPiece> pe)
    {
        final int robberHex = board.getRobberHex();
        boolean hadMatch = false;
        boolean wroteCall = false;

        while (pe.hasMoreElements())
        {
            System.err.print("\t");
            SOCPlayingPiece sc = pe.nextElement();
            Enumeration<Integer> hexes = board.getAdjacentHexesToNode(sc.getCoordinates()).elements();

            while (hexes.hasMoreElements())
            {
                final int hexCoord = hexes.nextElement().intValue();
                final int hdice = board.getNumberOnHexFromCoord(hexCoord);
                if (hdice != 0)
                    System.err.print(hdice);
                else
                    System.err.print(' ');
                if (hexCoord == robberHex)
                    System.err.print("(r)");
                if (hdice == roll)
                {
                    System.err.print('*');
                    if (hexCoord != robberHex)
                        hadMatch = true;
                }
                System.err.print("  ");
            }
            System.err.print(pieceType + " " + pl.getName());
            if (hadMatch && ! wroteCall)
            {
                // roll resources: 1 0 0 0 1 0
                System.err.print
                    ("  roll " + pl.getRolledResources().toShortString());
                wroteCall = true;
            }
            System.err.println();
        }

        return hadMatch;
    }

    /**
     * handle "change face" message.
     *
     * @param c  the connection
     * @param mes  the message
     */
    private void handleCHANGEFACE(StringConnection c, final SOCChangeFace mes)
    {
        final String gaName = mes.getGame();
        final SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;
        SOCPlayer player = ga.getPlayer((String) c.getData());
        if (player == null)
            return;
        final int id = mes.getFaceId();
        if ((id <= 0) && ! player.isRobot())
            return;  // only bots should use bot icons

        player.setFaceId(id);
        messageToGame(gaName, new SOCChangeFace(gaName, player.getPlayerNumber(), id));
    }

    /**
     * handle "set seat lock" message.
     *
     * @param c  the connection
     * @param mes  the message
     */
    private void handleSETSEATLOCK(StringConnection c, final SOCSetSeatLock mes)
    {
        final SOCGame.SeatLockState sl = mes.getLockState();
        final String gaName = mes.getGame();
        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;
        SOCPlayer player = ga.getPlayer((String) c.getData());
        if (player == null)
            return;

        try
        {
            final int pn = mes.getPlayerNumber();
            ga.setSeatLock(pn, sl);
            if ((sl != SOCGame.SeatLockState.CLEAR_ON_RESET) || (ga.clientVersionLowest >= 2000))
            {
                messageToGame(gaName, mes);
            } else {
                // older clients won't recognize that lock state
                messageToGameForVersions
                    (ga, 2000, Integer.MAX_VALUE, mes, true);
                messageToGameForVersions
                    (ga, -1, 1999, new SOCSetSeatLock(gaName, pn, SOCGame.SeatLockState.LOCKED), true);
            }
        }
        catch (IllegalStateException e) {
            messageToPlayerKeyed(c, gaName, "reply.lock.cannot");  // "Cannot set that lock right now."
        }
    }

    /**
     * handle "reset-board request" message.
     * If multiple human players, start a vote.
     * Otherwise, reset the game to a copy with
     * same name and (copy of) same players, new layout.
     *<P>
     * The requesting player doesn't vote, but server still
     * sends the vote-request-message, to tell that client their
     * request was accepted and voting has begun.
     *<P>
     * If only one player remains (all other humans have left at end),
     * ask them to start a new game instead. This is a rare occurrence
     * and we shouldn't bring in new robots and all,
     * since we already have an interface to set up a game.
     *<P>
     * If any human player's client is too old to vote for reset,
     * assume they vote yes.
     *
     * @see #resetBoardAndNotify(String, int)
     *
     * @param c  the connection
     * @param mes  the message
     */
    private void handleRESETBOARDREQUEST(StringConnection c, final SOCResetBoardRequest mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;
        SOCPlayer reqPlayer = ga.getPlayer((String) c.getData());
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
        final int numHuman = SOCGameBoardReset.sortPlayerConnections(ga, null, gameList.getMembers(gaName), humanConns, robotConns);

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
                resetBoardAndNotify(gaName, reqPN);
            } else if (hadRobot) {
                messageToPlayerKeyed(c, gaName, "resetboard.request.unlock.bot");
                    // "Please unlock at least one bot, so you will have an opponent."
            } else {
                messageToGameKeyed(ga, true, "resetboard.request.everyone.left");
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
                    StringConnection pc = getConnection(ga.getPlayer(i).getName());
                    if ((pc != null) && pc.isConnected() && (pc.getVersion() >= 1100))
                         ++votingPlayers;
                }
            }

            if (votingPlayers == 0)
            {
                // No one else is capable of voting.
                // Reset the game immediately.
                messageToGameKeyed(ga, false, "resetboard.vote.request.alloldcli", (String) c.getData());
                    // ">>> {0} is resetting the game - other connected players are unable to vote (client too old)."
                gameList.releaseMonitorForGame(gaName);
                resetBoardAndNotify(gaName, reqPN);
            }
            else
            {
                // Put it to a vote
                messageToGameKeyed(ga, false, "resetboard.vote.request", (String) c.getData());
                    // "requests a board reset - other players please vote."
                String vrCmd = SOCResetBoardVoteRequest.toCmd(gaName, reqPN);
                ga.resetVoteBegin(reqPN);
                gameList.releaseMonitorForGame(gaName);
                for (int i = 0; i < ga.maxPlayers; ++i)
                    if (humanConns[i] != null)
                    {
                        if (humanConns[i].getVersion() >= 1100)
                            humanConns[i].put(vrCmd);
                        else
                            ga.resetVoteRegister
                                (ga.getPlayer((String)(humanConns[i].getData())).getPlayerNumber(), true);
                    }
            }
        }
    }

    /**
     * handle message of player's vote for a "reset-board" request.
     * Register the player's vote.
     * If all votes have now arrived, and the vote is unanimous,
     * reset the game to a copy with same name and players, new layout.
     *
     * @see #resetBoardAndNotify(String, int)
     *
     * @param c  the connection
     * @param mes  the message
     */
    private void handleRESETBOARDVOTE(StringConnection c, final SOCResetBoardVote mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;
        final String plName = (String) c.getData();
        SOCPlayer reqPlayer = ga.getPlayer(plName);
        if (reqPlayer == null)
        {
            return;  // Not playing in that game (Security)
        }

        // Register this player's vote, and let game members know.
        // If vote succeeded, go ahead and reset the game.
        // If vote rejected, let everyone know.

        resetBoardVoteNotifyOne(ga, reqPlayer.getPlayerNumber(), plName, mes.getPlayerVote());
    }


    /**
     * "Reset-board" request: Register one player's vote, and let game members know.
     * If vote succeeded, go ahead and reset the game.
     * If vote rejected, let everyone know.
     *
     * @param ga      Game for this reset vote
     * @param pn      Player number who is voting
     * @param plName  Name of player who is voting
     * @param vyes    Player's vote, Yes or no
     */
    void resetBoardVoteNotifyOne(SOCGame ga, final int pn, final String plName, final boolean vyes)
    {
        boolean votingComplete = false;

        final String gaName = ga.getName();
        try
        {
            // Register in game
            votingComplete = ga.resetVoteRegister(pn, vyes);
            // Tell other players
            messageToGame (gaName, new SOCResetBoardVote(gaName, pn, vyes));
        }
        catch (IllegalArgumentException e)
        {
            D.ebugPrintln("*Error in player voting: game " + gaName + ": " + e);
            return;
        }
        catch (IllegalStateException e)
        {
            D.ebugPrintln("*Voting not active: game " + gaName);
            return;
        }

        if (! votingComplete)
        {
            return;
        }

        if (ga.getResetVoteResult())
        {
            // Vote succeeded - Go ahead and reset.
            resetBoardAndNotify(gaName, ga.getResetVoteRequester());
        }
        else
        {
            // Vote rejected - Let everyone know.
            messageToGame(gaName, new SOCResetBoardReject(gaName));
        }
    }

    /**
     * Does this client's locale have localized {@link SOCScenario} names and descriptions?
     * Checks these conditions:
     * <UL>
     *  <LI> {@link SOCClientData#wantsI18N c.scd.wantsI18N} flag is set:
     *      Has locale, new-enough version, has requested I18N strings (see that flag's javadocs).
     *  <LI> {@link StringConnection#getLocalized(String) c.getLocalized}({@code "gamescen.SC_WOND.n"})
     *      returns a string different than {@link #i18n_scenario_SC_WOND_desc}:
     *      This checks whether a fallback is being used because the client's locale has no scenario strings
     * </UL>
     * @param c  Client connection
     * @return  True if the client meets all the conditions listed above, false otherwise
     * @since 2.0.00
     */
    public static final boolean clientHasLocalizedStrs_gameScenarios(final StringConnection c)
    {
        final SOCClientData scd = (SOCClientData) c.getAppData();
        return
            scd.wantsI18N
            && ! i18n_scenario_SC_WOND_desc.equals(c.getLocalized("gamescen.SC_WOND.n"));
    }

    /**
     * Get localized strings for known {@link SOCScenario}s.  Assumes client locale has scenario strings:
     * Call {@link #clientHasLocalizedStrs_gameScenarios(StringConnection)} before calling this method.
     * Fills and returns a list with each {@code scKeys} key, scenario name, scenario description
     * from {@code c.getLocalized("gamescen." + scKey + ".n")} and {@code ("gamescen." + scKey + ".d")}.
     *
     * @param loc  Client's locale for StringManager i18n lookups.  This is passed instead of the client connection
     *    to simplify SOCPlayerClient's localizations before starting its practice server.
     * @param scKeys  Scenario keynames to localize, such as a {@link List} of keynames or the {@link Set}
     *    returned from {@link SOCScenario#getAllKnownScenarioKeynames()}.
     *    {@code null} to use {@link SOCScenario#getAllKnownScenarioKeynames()}.
     * @param checkUnknowns_skipFirst  Switch to allow calling this method from multiple places:
     *    <UL>
     *    <LI> If false, assumes {@code scKeys} has no unknown keys, will not call
     *         {@link SOCScenario#getScenario(String)} to verify them.
     *         {@code scKeys} could be {@link SOCScenario#getAllKnownScenarioKeynames()}, for example.
     *         The localized strings for each scKey are looked up and added to the list if found.
     *         If any {@code scKey} is missing localized string(s), that key won't be in the returned list.
     *    <LI> If true, assumes {@code scKeys} is a {@link List} of keys from a client, and may have
     *         scenario names unknown at this server version. Will ignore the first entry because in the
     *         client message, the first list entry isn't a scenario key.  Will call
     *         {@link SOCScenario#getScenario(String)} on each key to verify it exists.  The localized strings
     *         for each known scKey are looked up and added to the list.  If the scenario is unknown or its
     *         strings aren't localized, the key and {@link SOCLocalizedStrings#MARKER_KEY_UNKNOWN} are added instead.
     *    </UL>
     * @param scd  Optional client data to track which scenario strings are sent to client, or {@code null}.
     *    This method will update {@link SOCClientData#scenariosInfoSent scd.scenariosInfoSent}.
     * @return  Localized string list, may be empty but will never be null, in same format as the message returned
     *    from server to client: Scenario keys with localized strings have 3 consecutive entries in the list:
     *    Key, name, description.  If {@code checkUnknowns_skipFirst}, unknown scenarios have 2 consecutive entries
     *    in the list: Key, {@link SOCLocalizedStrings#MARKER_KEY_UNKNOWN}.
     * @since 2.0.00
     */
    public static List<String> localizeGameScenarios
        (final Locale loc, Collection<String> scKeys, final boolean checkUnknowns_skipFirst, final SOCClientData scd)
    {
        if (scKeys == null)
            scKeys = SOCScenario.getAllKnownScenarioKeynames();

        final SOCStringManager sm = SOCStringManager.getServerManagerForClient(loc);
        // No need to check hasLocalDescs = ! i18n_gameopt_PL_desc.equals(sm.get("gamescen.SC_WOND.n"))
        // because caller has done so

        Map<String, String> scensSent;  // for optional tracking
        if (scd != null)
        {
            scensSent = scd.scenariosInfoSent;
            if (scensSent == null)
            {
                scensSent = new HashMap<String, String>();
                scd.scenariosInfoSent = scensSent;
            }
        } else {
            scensSent = null;
        }

        List<String> rets = new ArrayList<String>();  // for reply to client

        boolean skippedAlready = ! checkUnknowns_skipFirst;
        for (final String scKey : scKeys)
        {
            if (! skippedAlready)
            {
                skippedAlready = true;
                continue;  // assumes scKeys is a List
            }

            if ((scensSent != null) && ! scensSent.containsKey(scKey))
                scensSent.put(scKey, SOCClientData.SENT_SCEN_STRINGS);

            String nm = null, desc = null;

            if (! (checkUnknowns_skipFirst && (SOCScenario.getScenario(scKey) == null)))
            {
                try { nm = sm.get("gamescen." + scKey + ".n"); }
                catch (MissingResourceException e) {}

                try { desc = sm.get("gamescen." + scKey + ".d"); }
                catch (MissingResourceException e) {}
            }

            if (nm != null)
            {
                rets.add(scKey);
                rets.add(nm);
                rets.add(desc);  // null is OK
            } else if (checkUnknowns_skipFirst) {
                rets.add(scKey);
                rets.add(SOCLocalizedStrings.MARKER_KEY_UNKNOWN);
            }
            // else localized not found, and not checkUnknowns_skipFirst: leave scKey out of rets entirely
        }

        return rets;
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
            flags = SOCLocalizedStrings.FLAG_SENT_ALL;
        }
        else if (type.equals(SOCLocalizedStrings.TYPE_SCENARIO))
        {
            // Handle individual scenario keys; ignores FLAG_REQ_ALL

            final SOCClientData scd = (SOCClientData) c.getAppData();
            if (clientHasLocalizedStrs_gameScenarios(c))
            {
                rets = localizeGameScenarios(scd.locale, str, true, scd);
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
        List<SOCGameOption> opts = null;

        // check for request for i18n localized descriptions (client v2.0.00 or newer);
        // if we don't have game opt localization for client's locale, ignore the request.
        if (mes.hasTokenGetI18nDescs() && (c.getI18NLocale() != null))
            scd.wantsI18N = true;
        final boolean wantsLocalDescs =
            scd.wantsI18N
            && ! i18n_gameopt_PL_desc.equals(c.getLocalized("gameopt.PL"));

        if (okeys == null)
        {
            // received "-", look for newer options (cli is older than us), or wantsLocalDescs.
            // okeys will be null if nothing is new.
            if (wantsLocalDescs)
                opts = SOCGameOption.optionsForVersion(cliVers, null);
            else
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
        else if (wantsLocalDescs)
        {
            // Received some okeys: cli is newer than this server, and
            // also wants localized descriptions.
            //
            // We need to send them all the localized options we have,
            // and also include the okeys they're asking for, which may
            // not be known to our older server.
            //
            // This situation is not common, and okeys won't be a long list,
            // so linear search should be good enough.

            opts = SOCGameOption.optionsForVersion(cliVers, null);
            for (final String okey : okeys)
            {
                boolean found = false;
                for (final SOCGameOption opt : opts)
                {
                    if (opt.key.equals(okey))
                    {
                        found = true;
                        break;
                    }
                }

                if (! found)
                    opts.add(new SOCGameOption(okey));  // OTYPE_UNKNOWN
            }

            okeys = null;  // merged into opts
        }

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

                    if ((opt == null) || (opt.minVersion > cliVers))  // Don't use opt.getMinVersion() here
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
            sendGameScenarioInfo(params.get(0), null, c, false);
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
                    sendGameScenarioInfo(null, sc, c, false);
                else
                    c.put(new SOCScenarioInfo(sc.key, true).toCmd());

        final SOCClientData scd = (SOCClientData) c.getAppData();

        if (hasAnyChangedMarker && scd.wantsI18N && ! scd.sentAllScenarioStrings)
        {
            // if available send each scenario's localized strings, unless we've already sent its full info

            if (! scd.checkedLocaleScenStrings)
            {
                scd.localeHasScenStrings = clientHasLocalizedStrs_gameScenarios(c);
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
                    scenStrs = localizeGameScenarios(scd.locale, scKeys, false, scd);
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

    /**
     * If needed, send this scenario's updated info and i18n localized short/long description strings to the client.
     * Checks whether the scenario has been added or changed since the client's version,
     * whether the scenario has strings in the client's locale, and whether the client has
     * already been sent this scenario's info or strings.
     *<P>
     * Sends nothing if {@code scKey} and {@code sc} are both null.
     * Sends nothing if client's version is older than 2.0.00 ({@link SOCScenario#VERSION_FOR_SCENARIOS}).
     * Will not send localized strings if locale is null.
     * Checks and updates the connection's {@link SOCClientData#sentAllScenarioStrings},
     * {@link SOCClientData#sentAllScenarioInfo}, {@link SOCClientData#scenariosInfoSent} and
     * related tracking fields.
     *<P>
     * Scenario's {@link SOCVersionedItem#minVersion minVersion} isn't checked here; may send information
     * about a scenario that's too new for the client's version to join games with it.
     *
     * @param scKey  Scenario keyname, from
     *     {@link SOCGame#getGameOptionStringValue(String) game.getGameOptionStringValue("SC")}, or null.
     *     Sends nothing if {@code scKey} and {@code sc} are both null.
     * @param sc  Scenario data if known, or null to use
     *     {@link SOCScenario#getScenario(String) SOCScenario.getScenario(scKey)}.
     *     When {@code sc != null}, will always send a {@link SOCScenarioInfo} message
     *     even if {@link SOCClientData#sentAllScenarioInfo} is set, unless client version is too old.
     * @param c  Client connection
     * @param stringsOnly  If true, send only localized strings, not entire {@link SOCScenarioInfo}.
     * @since 2.0.00
     */
    void sendGameScenarioInfo
        (String scKey, final SOCScenario sc, final StringConnection c, final boolean stringsOnly)
    {
        if (scKey == null)
        {
            if (sc == null)
                return;
            else
                scKey = sc.key;
        }

        final SOCClientData scd = (SOCClientData) c.getAppData();

        if ((scd.sentAllScenarioInfo || (stringsOnly && scd.sentAllScenarioStrings))
            && (sc == null))
        {
            return;  // <--- Already checked, nothing left to send ---
        }

        final int cliVers = c.getVersion();
        if (cliVers < SOCScenario.VERSION_FOR_SCENARIOS)
        {
            scd.sentAllScenarioStrings = true;
            scd.sentAllScenarioInfo = true;

            return;  // <--- Client is too old ---
        }

        // Have we already sent this scenario's info or strings?
        // If not, send now and update scd.scenariosInfoSent.

        Map<String, String> scensSent = scd.scenariosInfoSent;

        if ((sc == null) && (scensSent != null))
        {
            final String alreadySent = scensSent.get(scKey);
            if ((alreadySent != null)
                && (stringsOnly || alreadySent.equals(SOCClientData.SENT_SCEN_INFO)))
            {
                return;  // <--- Already sent ---
            }
        }

        SOCScenario scSend = null;  // If not null, send full scenario info instead of only strings

        if (sc != null)
        {
            scSend = sc;
        }
        else if (! stringsOnly)
        {
            SOCScenario s = SOCScenario.getScenario(scKey);
            if ((s != null) && (s.lastModVersion > cliVers))
                scSend = s;
        }

        String nm = null, desc = null;

        final boolean localeHasScenStrs;
        if (scd.checkedLocaleScenStrings)
        {
            localeHasScenStrs = scd.localeHasScenStrings;
        } else {
            localeHasScenStrs = SOCServer.clientHasLocalizedStrs_gameScenarios(c);

            scd.localeHasScenStrings = localeHasScenStrs;
            scd.checkedLocaleScenStrings = true;

            if (! localeHasScenStrs)
            {
                // client's locale has no localized scenario strings, or c.getI18NLocale() == null
                scd.sentAllScenarioStrings = true;
            }
        }

        if (localeHasScenStrs)
        {
            try { nm = c.getLocalized("gamescen." + scKey + ".n"); }
            catch (MissingResourceException e) {}

            if (nm != null)
            {
                try { desc = c.getLocalized("gamescen." + scKey + ".d"); }
                catch (MissingResourceException e) {}
            }
        }
        else if (scSend == null)
        {
            return;  // <--- No scenario strings in locale, and no full info to send ---
        }

        if (scSend != null)
        {
            c.put(new SOCScenarioInfo(scSend, nm, desc).toCmd());
        } else {
            List<String> scenStrs = new ArrayList<String>();
            scenStrs.add(scKey);
            if (nm != null)
            {
                scenStrs.add(nm);
                scenStrs.add(desc);  // null is OK
            } else {
                scenStrs.add(SOCLocalizedStrings.MARKER_KEY_UNKNOWN);
            }

            c.put(SOCLocalizedStrings.toCmd(SOCLocalizedStrings.TYPE_SCENARIO, 0, scenStrs));
        }

        // Remember what we sent it
        if (scensSent == null)
        {
            scensSent = new HashMap<String, String>();
            scd.scenariosInfoSent = scensSent;
        }
        scensSent.put(scKey, (stringsOnly) ? SOCClientData.SENT_SCEN_STRINGS : SOCClientData.SENT_SCEN_INFO);
    }

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

        createOrJoinGameIfUserOK
            (c, mes.getNickname(), mes.getPassword(), mes.getGame(), mes.getOptions());
    }

    /**
     * handle "create account" message
     *
     * @param c  the connection
     * @param mes  the message
     */
    private void handleCREATEACCOUNT(StringConnection c, SOCCreateAccount mes)
    {
        final int cliVers = c.getVersion();

        if (! SOCDBHelper.isInitialized())
        {
            // Send same SV_ status code as previous versions (before 1.1.19) which didn't check db.isInitialized
            // but instead fell through and sent "Account not created due to error."

            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_ACCT_NOT_CREATED_ERR, cliVers,
                     c.getLocalized("account.common.no_accts")));  // "This server does not use accounts and passwords."
            return;
        }

        final String requester = (String) c.getData();  // null if client isn't authenticated
        final Date currentTime = new Date();
        boolean isDBCountedEmpty = false;  // with null requester, did we query and find the users table is empty?
            // Not set if FEAT_OPEN_REG is active.

        // If client is not authenticated, does this server have open registration
        // or is an account required to create user accounts?
        if ((requester == null) && ! features.isActive(SOCServerFeatures.FEAT_OPEN_REG))
        {
            // SOCAccountClients older than v1.1.19 (VERSION_FOR_AUTHREQUEST, VERSION_FOR_SERVERFEATURES)
            // can't authenticate; all their user creation requests are anonymous (FEAT_OPEN_REG).
            // They can't be declined when SOCAccountClient connects, because v1.1.19 is when
            // SOCAuthRequest(ROLE_USER_ADMIN) message was added; we don't know why an older client
            // has connected until they try to create or join a game or channel or create a user.
            // It's fine for them to connect for games or channels, but user creation requires authentication.
            // Check client version now; an older client could create the first account without auth,
            // then not be able to create further ones which would be confusing.

            if (cliVers < SOCAuthRequest.VERSION_FOR_AUTHREQUEST)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_CANT_JOIN_GAME_VERSION,  // cli knows this status value: defined in 1.1.06
                         cliVers, c.getLocalized
                             ("account.create.client_version_minimum",
                              Version.version(SOCServerFeatures.VERSION_FOR_SERVERFEATURES))));
                              // "To create accounts, use client version {1} or newer."
                return;
            }

            // If account is required, are there any accounts in the db at all?
            // if none, this first account creation won't require auth.

            int count;
            try
            {
                count = SOCDBHelper.countUsers();
            }
            catch (SQLException e)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_PROBLEM_WITH_DB, cliVers,
                         c.getLocalized("account.create.error_db_conn")));
                             // "Problem connecting to database, please try again later."
                return;
            }

            if (count > 0)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_PW_WRONG, cliVers, c.getLocalized("account.common.must_auth")));
                             // "You must log in with a username and password before you can create accounts."
                return;
            }

            isDBCountedEmpty = true;
        }

        //
        // check to see if the requested nickname is permissable
        //
        final String userName = mes.getNickname().trim();

        if (! SOCMessage.isSingleLineAndSafe(userName))
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWGAME_NAME_REJECTED, cliVers,
                     SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED));
            return;
        }

        //
        // Check if we're using a user admin whitelist; this check is also in isUserDBUserAdmin.
        //
        // If databaseUserAdmins != null, then requester != null because FEAT_OPEN_REG can't also be active.
        // If requester is null because db is empty, check new userName instead of requester name:
        // The first account created must be on the whitelist in order to create further accounts.
        // If the db is empty when account client connects, server sends it FEAT_OPEN_REG so it won't require
        // user/password auth to create that first account; then requester == null, covered by isDBCountedEmpty.
        //
        if (databaseUserAdmins != null)
        {
            final String chkName = (isDBCountedEmpty) ? userName : requester;
            if ((chkName == null) || ! databaseUserAdmins.contains(chkName))
            {
                // Requester not on user-admin whitelist.

                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_ACCT_NOT_CREATED_DENIED, cliVers,
                         c.getLocalized("account.create.not_auth")));  // "Your account is not authorized to create accounts."

                printAuditMessage
                    (requester,
                     (isDBCountedEmpty)
                         ? "Requested jsettlers account creation, database is empty - first, create a user named in account admin whitelist"
                         : "Requested jsettlers account creation, this requester not on account admin whitelist",
                     null, currentTime, c.host());

                if (isDBCountedEmpty)
                    System.err.println("User requested new account but database is currently empty: Run SOCAccountClient to create admin account(s) named in the whitelist.");
                    // probably don't need to also print databaseUserAdmins list contents here

                return;
            }
        }

        //
        // check if there's already an account with requested nickname
        //
        try
        {
            if (SOCDBHelper.doesUserExist(userName))
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                         c.getLocalized("account.create.already_exists", userName)));
                             // "The nickname "{0}" is already in use."

                printAuditMessage
                    (requester, "Requested jsettlers account creation, already exists",
                     userName, currentTime, c.host());

                return;
            }
        }
        catch (SQLException sqle)
        {
            // Indicates a db problem: don't continue
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_PROBLEM_WITH_DB, cliVers,
                     c.getLocalized("account.create.error_db_conn")));
                         // "Problem connecting to database, please try again later."
            return;
        }

        //
        // create the account
        //
        boolean success = false;

        try
        {
            success = SOCDBHelper.createAccount(userName, c.host(), mes.getPassword(), mes.getEmail(), currentTime.getTime());
        }
        catch (SQLException sqle)
        {
            System.err.println("SQL Error creating account in db.");
        }

        if (success)
        {
            final int stat = (isDBCountedEmpty)
                ? SOCStatusMessage.SV_ACCT_CREATED_OK_FIRST_ONE
                : SOCStatusMessage.SV_ACCT_CREATED_OK;
            c.put(SOCStatusMessage.toCmd
                    (stat, cliVers,
                     c.getLocalized("account.create.created", userName)));  // "Account created for "{0}"."

            printAuditMessage(requester, "Created jsettlers account", userName, currentTime, c.host());

            if (acctsNotOpenRegButNoUsers)
                acctsNotOpenRegButNoUsers = false;
        }
        else
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_ACCT_NOT_CREATED_ERR, cliVers,
                     c.getLocalized("account.create.error")));  // "Account not created due to error."
        }
    }

    /**
     * Client has been approved to join game; send the entire state of the game to client.
     * Gets the game's handler and calls {@link GameHandler#joinGame(SOCGame, StringConnection, boolean, boolean)};
     * see that method's javadoc for details.
     *<P>
     * @param gameData Game to join
     * @param c        The connection of joining client
     * @param isReset  Game is a board-reset of an existing game; should always be false when server is calling,
     *                 board resets are up to the GameHandler.
     * @param isTakingOver  Client is re-joining; this connection replaces an earlier one which
     *                      is defunct because of a network problem.
     *                      If <tt>isTakingOver</tt>, don't send anything to other players.
     *
     * @see #connectToGame(StringConnection, String, Map)
     * @see #createOrJoinGameIfUserOK(StringConnection, String, String, String, Map)
     */
    private void joinGame(SOCGame gameData, StringConnection c, boolean isReset, boolean isTakingOver)
    {
        final String gameName = gameData.getName();
        GameHandler hand = gameList.getGameTypeHandler(gameName);
        if (hand == null)
        {
            // not likely, but could happen if there's a bug
            D.ebugPrintln("L6708 SOCServer.joinGame: null handler for " + gameName);
            return;
        }

        hand.joinGame(gameData, c, isReset, isTakingOver);
    }

    /**
     * This player is sitting down at the game.
     * The server has already validated that the game isn't full and their seat is empty,
     * or has removed a bot to make room at that seat.
     *<P>
     * Calls {@link SOCGame#addPlayer(String, int)}. Announces with {@link SOCSitDown} to all game members.
     * Sends sitting player their own data via {@link GameHandler#sitDown_sendPrivateInfo(SOCGame, StringConnection, int)}.
     * If game is waiting for robots to join, and sitting player is the last bot, start the game.
     *
     * @param ga     the game
     * @param c      the connection for the player
     * @param pn     which seat the player is taking
     * @param robot  true if this player is a robot
     * @param isReset Game is a board-reset of an existing game
     */
    private void sitDown(SOCGame ga, StringConnection c, int pn, boolean robot, boolean isReset)
    {
        if ((c == null) || (ga == null))
            return;

        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            if (! isReset)
            {
                // Call addPlayer and set or clear the robot flag.
                // If isReset, player is already added and knows if robot.

                try
                {
                    SOCClientData cd = (SOCClientData) c.getAppData();
                    ga.addPlayer((String) c.getData(), pn);
                    ga.getPlayer(pn).setRobotFlag(robot, (cd != null) && cd.isBuiltInRobot);
                }
                catch (IllegalStateException e)
                {
                    // Maybe already seated? (network lag)
                    if (! robot)
                        messageToPlayerKeyed(c, gaName, "member.sit.not.here");  // "You cannot sit down here."
                    ga.releaseMonitor();
                    return;  // <---- Early return: cannot sit down ----
                }
            }

            /**
             * if the player can sit, then tell the other clients in the game
             */
            SOCSitDown sitMessage = new SOCSitDown(gaName, (String) c.getData(), pn, robot);
            messageToGame(gaName, sitMessage);

            D.ebugPrintln("*** sent SOCSitDown message to game ***");

            recordGameEvent(gaName, sitMessage.toCmd());

            Vector<StringConnection> requests;
            if (! isReset)
            {
                requests = robotJoinRequests.get(gaName);
            }
            else
            {
                requests = null;  // Game already has all players from old game
            }

            if (requests != null)
            {
                /**
                 * if the request list is empty and the game hasn't started yet,
                 * then start the game
                 */
                if (requests.isEmpty() && (ga.getGameState() < SOCGame.START1A))
                {
                    GameHandler hand = gameList.getGameTypeHandler(ga.getName());
                    if (hand != null)
                        hand.startGame(ga);
                }

                /**
                 * if the request list is empty, remove the empty list
                 */
                if (requests.isEmpty())
                {
                    robotJoinRequests.remove(gaName);
                }
            }

            /**
             * send all the private information
             * and (if applicable) prompt for discard or other decision
             */
            GameHandler hand = gameList.getGameTypeHandler(gaName);
            if (hand != null)
                hand.sitDown_sendPrivateInfo(ga, c, pn);
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception caught at sitDown");
        }

        ga.releaseMonitor();
    }

    /**
     * Reset the board, to a copy with same players but new layout.
     * Here's the general outline; step 1 and 2 are done immediately here,
     * steps 3 through n are done (after robots are dismissed) within
     * {@link #resetBoardAndNotify_finish(SOCGameBoardReset, SOCGame)}.
     *<OL>
     * <LI value=1> Reset the board, remember player positions.
     *              If there are robots, set game state to
     *              {@link SOCGame#READY_RESET_WAIT_ROBOT_DISMISS}.
     * <LI value=2a> Send ResetBoardAuth to each client (like sending JoinGameAuth at new game)
     *    Humans will reset their copy of the game.
     *    Robots will leave the game, and soon be requested to re-join.
     *    (This simplifies the robot client.)
     *    If the game was in initial placement or was already over at reset time, different robots will
     *    be randomly chosen to join the reset game.
     * <LI value=2b> If there were robots, wait for them all to leave the old game.
     *    Otherwise, (race condition) they may leave the new game as it is forming.
     *    Set {@link SOCGame#boardResetOngoingInfo}.
     *    Wait for them to leave the old game before continuing.
     *    The call will be made from {@link #handleLEAVEGAME_maybeGameReset_oldRobot(String)}.
     * <LI value=2c> If no robots, immediately call {@link #resetBoardAndNotify_finish(SOCGameBoardReset, SOCGame)}.
     *   <P>
     *    <b>This ends this method.</b>  Step 3 and the rest are in
     *    {@link #resetBoardAndNotify_finish(SOCGameBoardReset, SOCGame)}.
     * <LI value=3> Send messages as if each human player has clicked "join" (except JoinGameAuth)
     * <LI value=4> Send as if each human player has clicked "sit here"
     * <LI value=5a> If no robots, send to game as if someone else has
     *              clicked "start game", and set up state to begin game play.
     * <LI value=5b>  If there are robots, set up wait-request
     *     queue (robotJoinRequests). Game will wait for robots to send
     *     JOINGAME and SITDOWN, as they do when joining a newly created game.
     *     Once all robots have re-joined, the game will begin.
     *</OL>
     */
    private void resetBoardAndNotify(final String gaName, final int requestingPlayer)
    {
        /**
         * 1. Reset the board, remember player positions.
         *    Takes the monitorForGame and (when reset is ready) releases it.
         *    If robots, resetBoard will also set gamestate
         *    and boardResetOngoingInfo field.
         */
        SOCGameBoardReset reBoard = gameList.resetBoard(gaName);
        if (reBoard == null)
        {
            final SOCGame ga = gameList.getGameData(gaName);
            if (ga != null)
                messageToGameKeyed(ga, true, "resetboard.doit.interror", gaName);
                    // ">>> Internal error, Game {0} board reset failed"

            return;  // <---- Early return: reset failed ----
        }
        SOCGame reGame = reBoard.newGame;

        // Announce who asked for this reset
        {
            String plName = reGame.getPlayer(requestingPlayer).getName();
            final String key = (plName != null)
                ? "resetboard.doit.announce.requester"       // ">>> Game {0} board reset by {1}"
                : "resetboard.doit.announce.playerwholeft";  // ">>> Game {0} board reset by a player who left"
            messageToGameKeyed(reGame, true, key, gaName, plName);
        }

        // If game is still initial-placing or was over, we'll shuffle the robots
        final boolean resetWithShuffledBots =
            (reBoard.oldGameState < SOCGame.PLAY) || (reBoard.oldGameState == SOCGame.OVER);

        /**
         * Player connection data:
         * - Humans are copied from old to new game
         * - Robots aren't copied to new game, must re-join
         */
        StringConnection[] huConns = reBoard.humanConns;
        StringConnection[] roConns = reBoard.robotConns;

        /**
         * Notify old game's players. (Humans and robots)
         *
         * 2a. Send ResetBoardAuth to each (like sending JoinGameAuth at new game).
         *    Humans will reset their copy of the game.
         *    Robots will leave the game, and soon will be requested to re-join.
         */
        for (int pn = 0; pn < reGame.maxPlayers; ++pn)
        {
            SOCResetBoardAuth resetMsg = new SOCResetBoardAuth(gaName, pn, requestingPlayer);
            if (huConns[pn] != null)
                messageToPlayer(huConns[pn], resetMsg);
            else if (roConns[pn] != null)
            {
                if (! resetWithShuffledBots)
                    messageToPlayer(roConns[pn], resetMsg);  // same robot will rejoin
                else
                    messageToPlayer(roConns[pn], new SOCRobotDismiss(gaName));  // could be different bot
            }
        }

        // If there are robots, wait for them to leave
        // before doing anything else.  Otherwise, go ahead.

        if (! reBoard.hadRobots)
            resetBoardAndNotify_finish(reBoard, reGame);
        // else
        //  gameState is READY_RESET_WAIT_ROBOT_DISMISS,
        //  and once the last robot leaves this game,
        //  handleLEAVEGAME will take care of the reset,
        //  by calling resetBoardAndNotify_finish.

    }  // resetBoardAndNotify

    /**
     * Complete steps 3 - n of the board-reset process
     * outlined in {@link #resetBoardAndNotify(String, int)},
     * after any robots have left the old game.
     * @param reBoard  Board reset data, from {@link SOCGameListAtServer#resetBoard(String)}
     *                   or {@link SOCGame#boardResetOngoingInfo reGame.boardResetOngoingInfo}
     * @param reGame   The new game created by the reset, with gamestate {@link SOCGame#NEW NEW}
     *                   or {@link SOCGame#READY_RESET_WAIT_ROBOT_DISMISS READY_RESET_WAIT_ROBOT_DISMISS}
     * @since 1.1.07
     */
    private void resetBoardAndNotify_finish(SOCGameBoardReset reBoard, SOCGame reGame)
    {
        final boolean resetWithShuffledBots =
            (reBoard.oldGameState < SOCGame.PLAY) || (reBoard.oldGameState == SOCGame.OVER);
        StringConnection[] huConns = reBoard.humanConns;

        /**
         * 3. Send messages as if each human player has clicked "join" (except JoinGameAuth)
         */
        for (int pn = 0; pn < reGame.maxPlayers; ++pn)
        {
            if (huConns[pn] != null)
                joinGame(reGame, huConns[pn], true, false);
        }

        /**
         * 4. Send as if each human player has clicked "sit here"
         */
        for (int pn = 0; pn < reGame.maxPlayers; ++pn)
        {
            if (huConns[pn] != null)
                sitDown(reGame, huConns[pn], pn, false /* isRobot*/, true /*isReset */ );
        }

        /**
         * 5a. If no robots, send to game as if someone else has
         *     clicked "start game", and set up state to begin game play.
         */
        if (! reBoard.hadRobots)
        {
            GameHandler hand = gameList.getGameTypeHandler(reGame.getName());
            if (hand != null)
                hand.startGame(reGame);
        }
        else
        {

        /**
         * 5b. If there are robots, set up wait-request queue
         *     (robotJoinRequests) and ask robots to re-join.
         *     Game will wait for robots to send JOINGAME and SITDOWN,
         *     as they do when joining a newly created game.
         *     Once all robots have re-joined, the game will begin.
         */
            reGame.setGameState(SOCGame.READY);
            readyGameAskRobotsJoin
              (reGame, resetWithShuffledBots ? null : reBoard.robotConns, 0);
        }

        // All set.
    }  // resetBoardAndNotify_finish

    /**
     * Increment the "number of games finished" server-statistics field.
     * Call when a game's state becomes {@link SOCGame#OVER} (or higher)
     * from a lower/earlier state.
     *<P>
     * Thread-safe; synchronizes on an internal object.
     * Package-level access for calls from {@link GameHandler}s.
     * @since 2.0.00
     */
    void gameOverIncrGamesFinishedCount()
    {
        synchronized (countFieldSync)
        {
            ++numberOfGamesFinished;
        }
    }

    /**
     * create a new game event record
     */
    // private void createNewGameEventRecord()
    // {
        /*
           currentGameEventRecord = new SOCGameEventRecord();
           currentGameEventRecord.setTimestamp(new Date());
         */
    // }

    /**
     * save the current game event record in the game record
     *
     * @param gn  the name of the game
     */
    // private void saveCurrentGameEventRecord(String gn)
    // {
        /*
           SOCGameRecord gr = (SOCGameRecord)gameRecords.get(gn);
           SOCGameEventRecord ger = currentGameEventRecord.myClone();
           gr.addEvent(ger);
         */
    // }

    /**
     * write a gameRecord out to disk
     *
     * @param na  the name of the record
     * @param gr  the game record
     */

    /*
       private void writeGameRecord(String na, SOCGameRecord gr) {
       FileOutputStream os = null;
       ObjectOutput output = null;

       try {
       Date theTime = new Date();
       os = new FileOutputStream("dataFiles/"+na+"."+theTime.getTime());
       output = new ObjectOutputStream(os);
       } catch (Exception e) {
       D.ebugPrintln(e.toString());
       D.ebugPrintln("Unable to open output stream.");
       }
       try{
       output.writeObject(gr);
       // D.ebugPrintln("*** Wrote "+na+" out to disk. ***");
       output.close();
       } catch (Exception e) {
       D.ebugPrintln(e.toString());
       D.ebugPrintln("Unable to write game record to disk.");
       }
       }
     */

    /**
     * Save game stats in the database.
     * if all the players stayed for the whole game,
     * or if the game has any human players,
     * record the scores in the database.
     *<P>
     * Does nothing unless property {@code jsettlers.db.save.games}
     * is true. ({@link SOCDBHelper#PROP_JSETTLERS_DB_SAVE_GAMES})
     *
     * @param ga  the game; state should be {@link SOCGame#OVER}
     */
    protected void storeGameScores(SOCGame ga)
    {
        if ((ga == null) || ! SOCDBHelper.isInitialized())
            return;
        if (! getConfigBoolProperty(props, SOCDBHelper.PROP_JSETTLERS_DB_SAVE_GAMES, false))
            return;

        //D.ebugPrintln("allOriginalPlayers for "+ga.getName()+" : "+ga.allOriginalPlayers());
        if (! ((ga.getGameState() == SOCGame.OVER)
               && (ga.allOriginalPlayers() || ga.hasHumanPlayers())))
            return;

        try
        {
            // TODO 6-player: save their scores too, if
            // those fields are in the database.
            final long gameSeconds = ((System.currentTimeMillis() - ga.getStartTime().getTime())+500L) / 1000L;
            SOCDBHelper.saveGameScores(ga, gameSeconds);
        }
        catch (SQLException sqle)
        {
            System.err.println("Error saving game scores in db: " + sqle);
        }
    }

    /**
     * record events that happen during the game
     *
     * @param gameName   the name of the game
     * @param event      the event
     */
    protected void recordGameEvent(String gameName, String event)
    {
        /*
           FileWriter fw = (FileWriter)gameDataFiles.get(gameName);
           if (fw != null) {
           try {
           fw.write(event+"\n");
           //D.ebugPrintln("WROTE |"+event+"|");
           } catch (Exception e) {
           D.ebugPrintln(e.toString());
           D.ebugPrintln("Unable to write to disk.");
           }
           }
         */
    }

    /**
     * check for games that have expired and destroy them.
     * If games are about to expire, send a warning.
     * As of version 1.1.09, practice games ({@link SOCGame#isPractice} flag set) don't expire.
     * Is callback method every few minutes from {@link SOCGameTimeoutChecker#run()}.
     *
     * @param currentTimeMillis  The time when called, from {@link System#currentTimeMillis()}
     * @see #GAME_TIME_EXPIRE_WARN_MINUTES
     * @see #checkForExpiredTurns(long)
     */
    public void checkForExpiredGames(final long currentTimeMillis)
    {
        Vector<String> expired = new Vector<String>();

        gameList.takeMonitor();

        // Add 2 extra minutes because of coarse 5-minute granularity in SOCGameTimeoutChecker.run()
        long warn_ms = (2 + GAME_TIME_EXPIRE_WARN_MINUTES) * 60L * 1000L;

        try
        {
            for (SOCGame gameData : gameList.getGamesData())
            {
                if (gameData.isPractice)
                    continue;  // <--- Skip practice games, they don't expire ---

                long gameExpir = gameData.getExpiration();

                // Start our text messages with ">>>" to mark as urgent to the client.

                if (gameExpir <= currentTimeMillis)
                {
                    final String gameName = gameData.getName();
                    expired.addElement(gameName);
                    messageToGameKeyed(gameData, true, "game.time.expire.destroyed");
                        // ">>> The time limit on this game has expired, it will now be destroyed."
                }
                else if ((gameExpir - warn_ms) <= currentTimeMillis)
                {
                    //
                    //  Give people a few minutes' warning (they may have a few warnings)
                    //
                    int minutes = (int) ((gameExpir - currentTimeMillis) / 60000);
                    if (minutes < 1)
                        minutes = 1;  // in case of rounding down

                    messageToGameKeyed(gameData, true, "game.time.expire.soon.addtime", Integer.valueOf(minutes));
                        // ">>> Less than {0} minutes remaining. Type *ADDTIME* to extend this game another 30 minutes."
                }
                else if ((currentTimeMillis - gameData.lastActionTime) > (60 * 1000 * GAME_TIME_EXPIRE_CHECK_MINUTES))
                {
                    // If game is idle since previous check, send keepalive ping to its clients
                    // so the network doesn't disconnect while all players are taking a break

                    messageToGame(gameData.getName(), new SOCServerPing(60 * GAME_TIME_EXPIRE_CHECK_MINUTES));
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintln("Exception in checkForExpiredGames - " + e);
        }

        gameList.releaseMonitor();

        //
        // destroy the expired games
        //    Assumes the list will be short, so the monitor take/release overhead will be acceptable.
        //
        for (Enumeration<String> ex = expired.elements(); ex.hasMoreElements();)
        {
            String ga = ex.nextElement();
            destroyGameAndBroadcast(ga, "checkForExpired");
        }
    }

    /**
     * Check all games for robot turns that have expired, and end that turn,
     * or stop waiting for non-current-player robot actions (discard picks, etc).
     * Robot turns may end from inactivity or from an illegal placement.
     * Checks each game's {@link SOCGame#lastActionTime} field, and calls
     * {@link GameHandler#endTurnIfInactive(SOCGame, long)} if the
     * last action is older than {@link #ROBOT_FORCE_ENDTURN_SECONDS}.
     *<P>
     * Is callback method every few seconds from {@link SOCGameTimeoutChecker#run()}.
     *
     * @param currentTimeMillis  The time when called, from {@link System#currentTimeMillis()}
     * @see #ROBOT_FORCE_ENDTURN_SECONDS
     * @see #checkForExpiredGames(long)
     * @since 1.1.11
     */
    public void checkForExpiredTurns(final long currentTimeMillis)
    {
        // Because nothing's currently happening in such a turn,
        // and we force the end in another thread,
        // we shouldn't need to worry about locking.
        // So, we don't need gameList.takeMonitor().

        final long inactiveTime = currentTimeMillis - (1000L * ROBOT_FORCE_ENDTURN_SECONDS);

        try
        {
            for (SOCGame ga : gameList.getGamesData())
            {
                // lastActionTime is a recent time, or might be 0 to force end
                long lastActionTime = ga.lastActionTime;
                if (lastActionTime > inactiveTime)
                    continue;

                if (ga.getGameState() >= SOCGame.OVER)
                {
                    // nothing to do.
                    // bump out that time, so we don't see
                    // it again every few seconds
                    ga.lastActionTime
                        += (SOCGameListAtServer.GAME_TIME_EXPIRE_MINUTES * 60L * 1000L);
                    continue;
                }

                final int cpn = ga.getCurrentPlayerNumber();
                if (cpn == -1)
                    continue;  // not started yet

                GameHandler hand = gameList.getGameTypeHandler(ga.getName());
                if (hand != null)
                    hand.endTurnIfInactive(ga, currentTimeMillis);

                // TODO consider keeping stats on forced end turns (return false or true from endTurnIfInactive, etc)
            }
        }
        catch (Exception e)
        {
            D.ebugPrintln("Exception in checkForExpiredTurns - " + e);
        }
    }

    /**
     * Quick-and-dirty command line parsing of a game option.
     * Calls {@link SOCGameOption#setKnownOptionCurrentValue(SOCGameOption)}.
     * If problems, throws an error message with text to print to console.
     *<P>
     * Note that an unknown {@link SOCScenario} name (value of game option {@code "SC"})
     * isn't treated as an error here; {@code SC}'s value will be set to the unknown scenario.
     * Code elsewhere will print an error and halt startup.
     *
     * @param op  Game option, as parsed by
     *   {@link SOCGameOption#parseOptionNameValue(String, boolean) SGO.parseOptionNameValue(optNameValue, true)} or
     *   {@link SOCGameOption#parseOptionNameValue(String, String, boolean) SGO.parseOptionNameValue(optkey, optval, true)}.
     *   <BR>
     *   Keyname should be case-insensitive; note both of those calls include {@code forceNameUpcase == true}.
     *   <BR>
     *   {@code null} is allowed and will throw
     *   {@code IllegalArgumentException("Unknown or malformed game option: " + optRaw)}.
     * @param optRaw  To include in exception text or a value into {@code optsAlreadySet},
     *         the option name=value from command line. Should not be null.
     *         For cleaner messages, option name should be uppercased.
     * @param optsAlreadySet  For tracking, game option names we've already encountered on the command line.
     *                        This method will add ({@code optName}, {@code optNameValue}) to this map.
     *                        Can be {@code null} if not needed.
     * @return the parsed SOCGameOption
     * @throws IllegalArgumentException if bad name, bad value, or already set from command line.
     *         {@link Throwable#getMessage()} will have problem details:
     *         <UL>
     *         <LI> Unknown or malformed game option name, from
     *           {@link SOCGameOption#parseOptionNameValue(String, boolean)}
     *         <LI> Bad option value, from {@link SOCGameOption#setKnownOptionCurrentValue(SOCGameOption)}
     *         <LI> Appears twice on command line, name is already in {@code optsAlreadySet}
     *         </UL>
     * @since 1.1.07
     */
    public static SOCGameOption parseCmdline_GameOption
        (final SOCGameOption op, final String optRaw, HashMap<String, String> optsAlreadySet)
        throws IllegalArgumentException
    {
        if (op == null)
            throw new IllegalArgumentException("Unknown or malformed game option: " + optRaw);

        if (op.optType == SOCGameOption.OTYPE_UNKNOWN)
            throw new IllegalArgumentException("Unknown game option: " + op.key);

        if ((optsAlreadySet != null) && optsAlreadySet.containsKey(op.key))
            throw new IllegalArgumentException("Game option cannot appear twice on command line: " + op.key);

        try
        {
            SOCGameOption.setKnownOptionCurrentValue(op);
            if (optsAlreadySet != null)
                optsAlreadySet.put(op.key, optRaw);
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad value, cannot set game option: " + op.key);
        }

        return op;
    }

    /**
     * Quick-and-dirty parsing of command-line arguments with dashes.
     *<P>
     * Checks first for the optional server startup properties file {@code "jsserver.properties"}
     * ({@link #SOC_SERVER_PROPS_FILENAME}).
     * If the file exists but there is an error reading it, calls {@link System#exit(int) System.exit(1)}
     * to exit because currently only {@code main(..)} calls this method.
     * For details on the java properties file syntax ({@code #} starts a comment line, etc),
     * see {@link Properties#load(java.io.InputStream)}.
     *<P>
     * If a property appears on the command line and also in {@code jsserver.properties},
     * the command line's value overrides the file's.
     *<P>
     * If any game options are set ("-o", "--option"), then
     * {@link #hasSetGameOptions} is set to true, and
     * {@link SOCGameOption#setKnownOptionCurrentValue(SOCGameOption)}
     * is called to set them globally.
     *<P>
     * If {@code jsserver.properties} file contains game option properties ({@code jsettlers.gameopt.*}),
     * they will be checked for possible problems:
     *<UL>
     * <LI> Empty game option name after {@code jsettlers.gameopt.} prefix
     * <LI> Unknown option name
     * <LI> Problem with name or value reported from {@link #parseCmdline_GameOption(SOCGameOption, String, HashMap)}
     * <LI> Default scenario's options override other options in properties file
     *</UL>
     * See {@link #PROP_JSETTLERS_GAMEOPT_PREFIX} for game option property syntax.
     *<P>
     * If <tt>args[]</tt> is empty, it will use defaults for
     * {@link #PROP_JSETTLERS_PORT} and {@link #PROP_JSETTLERS_CONNECTIONS}.
     *<P>
     * Does not use a {@link #PROP_JSETTLERS_STARTROBOTS} default, that's
     * handled in {@link #initSocServer(String, String, Properties)}.
     *<P>
     * Sets {@link #hasStartupPrintAndExit} if appropriate.
     *
     * @param args args as passed to main
     * @return Properties collection of args, or null for argument error or unknown argument(s).
     *     Will contain at least {@link #PROP_JSETTLERS_PORT},
     *     {@link #PROP_JSETTLERS_CONNECTIONS},
     *     {@link SOCDBHelper#PROP_JSETTLERS_DB_USER},
     *     {@link SOCDBHelper#PROP_JSETTLERS_DB_PASS}.
     * @since 1.1.07
     */
    public static Properties parseCmdline_DashedArgs(String[] args)
    {
        // javadoc note: This public method's javadoc section about game option properties
        // is copied for visibility from private init_propsSetGameopts.  If you update the
        // text here, also update the same text in init_propsSetGameopts's javadoc.

        Properties argp = new Properties();  // returned props, from "jsserver.properties" file and args[]
        boolean hasUnknowns = false;  // warn about each during parsing, instead of returning after first one

        // Check against options which are on command line twice: Can't just check argp keys because
        // argp is loaded from jsserver.properties, then command-line properties can override
        // anything set from there
        HashSet<String> cmdlineOptsSet = new HashSet<String>();
        HashMap<String, String> gameOptsAlreadySet = new HashMap<String, String>();
            // used and updated by parseCmdline_GameOption

        /**
         * Read jsserver.properties first
         */
        try
        {
            final File pf = new File(SOC_SERVER_PROPS_FILENAME);
            if (pf.exists())
            {
                if (pf.isFile() && pf.canRead())
                {
                    System.err.println("Reading startup properties from " + SOC_SERVER_PROPS_FILENAME);
                    FileInputStream fis = new FileInputStream(pf);
                    argp.load(fis);
                    fis.close();
                    try
                    {
                        init_propsSetGameopts(argp);

                        // Prints warnings if conflicts. Prints error and returns false if "SC"
                        // scenario name is unknown. When command line game opts are parsed,
                        // another call to init_checkScenarioOpts will check the command line's
                        // specified "SC" (if any) against both argp and command line's gameopts.
                        if (! init_checkScenarioOpts(argp, true, SOC_SERVER_PROPS_FILENAME, null, null))
                            throw new IllegalArgumentException();
                    }
                    catch (IllegalArgumentException e)
                    {
                        final String msg = e.getMessage();
                        if (msg != null)
                            System.err.println(msg);
                        System.err.println
                            ("*** Error in properties file " + SOC_SERVER_PROPS_FILENAME + ": Exiting.");
                        System.exit(1);
                    }
                } else {
                    System.err.println
                        ("*** Properties file " + SOC_SERVER_PROPS_FILENAME
                         + " exists but isn't a readable plain file: Exiting.");
                    System.exit(1);
                }
            }
        }
        catch (Exception e)
        {
            // SecurityException from .exists, .isFile, .canRead
            // IOException from FileInputStream construc [FileNotFoundException], props.load
            // IllegalArgumentException from props.load (malformed Unicode escape)
            System.err.println
                ("*** Error reading properties file " + SOC_SERVER_PROPS_FILENAME
                 + ", exiting: " + e.toString());
            if (e.getMessage() != null)
                System.err.println("    : " + e.getMessage());
            System.exit(1);
        }

        /**
         * Now parse args[]
         */
        final int pfxL = PROP_JSETTLERS_GAMEOPT_PREFIX.length();
        int aidx = 0;
        while ((aidx < args.length) && (args[aidx].startsWith("-")))
        {
            String arg = args[aidx];

            if (arg.equals("-V") || arg.equalsIgnoreCase("--version"))
            {
                Version.printVersionText(System.err, "Java Settlers Server ");
                hasStartupPrintAndExit = true;
            }
            else if (arg.equalsIgnoreCase("-h") || arg.equals("?") || arg.equals("-?")
                     || arg.equalsIgnoreCase("--help"))
            {
                printUsage(true);
                hasStartupPrintAndExit = true;
            }
            else if (arg.startsWith("-o") || arg.equalsIgnoreCase("--option"))
            {
                hasSetGameOptions = true;

                boolean printedMsg = false;
                String argValue;
                if (arg.startsWith("-o") && (arg.length() > 2))
                {
                    argValue = arg.substring(2);
                } else {
                    ++aidx;
                    if (aidx < args.length)
                        argValue = args[aidx];
                    else
                        argValue = null;
                }
                if (argValue != null)
                {
                    try
                    {
                        // canonicalize opt's keyname to all-uppercase
                        {
                            final int i = argValue.indexOf('=');
                            if (i > 0)
                            {
                                String oKey = argValue.substring(0, i),
                                       okUC = oKey.toUpperCase(Locale.US);
                                if (! oKey.equals(okUC))
                                    argValue = okUC + argValue.substring(i);
                            }
                        }
                        // parse this opt, update known option's current value
                        SOCGameOption opt = parseCmdline_GameOption
                            (SOCGameOption.parseOptionNameValue(argValue, false),  // null if parse fails
                             argValue, gameOptsAlreadySet);

                        // Add or update in argp, in case this gameopt property also appears in the properties file;
                        // otherwise the SOCServer constructor will reset the known opt current value
                        // back to the properties file's contents, instead of keeping the command-line opt value.
                        // if not found, don't need to add it to argp: option's current value is already set.
                        final String propKey = PROP_JSETTLERS_GAMEOPT_PREFIX + opt.key;
                        if (argp.containsKey(propKey))
                            argp.put(propKey, opt.getPackedValue().toString());
                    } catch (IllegalArgumentException e) {
                        argValue = null;
                        System.err.println(e.getMessage());
                        printedMsg = true;
                    }
                }
                if (argValue == null)
                {
                    if (! printedMsg)
                        System.err.println("Missing required option name/value after " + arg);
                    System.err.println();
                    printGameOptions();
                    return null;
                }
            } else if (arg.startsWith("-D"))  // java-style props defines
            {
                // We get to here when a user uses -Dname=value. However, in
                // some cases, the OS goes ahead and parses this out to args
                //   {"-Dname", "value"}
                // so instead of parsing on "=", we just make the "-D"
                // characters go away and skip one argument forward.

                String name;
                if (arg.length() == 2) // "-D something"
                {
                    ++aidx;
                    if (aidx < args.length)
                    {
                        name = args[aidx];
                    } else {
                        System.err.println("Missing property name after -D");
                        return null;
                    }
                } else {
                    name = arg.substring(2, arg.length());
                }
                String value = null;
                int posEq = name.indexOf("=");
                if (posEq > 0)
                {
                    value = name.substring(posEq + 1);
                    name = name.substring(0, posEq);
                }
                else if (aidx < args.length - 1)
                {
                    ++aidx;
                    value = args[aidx];
                }
                else {
                    System.err.println("Missing value for property " + name);
                    return null;
                }

                if (cmdlineOptsSet.contains(name))
                {
                    System.err.println("Property cannot appear twice on command line: " + name);
                    return null;
                }
                argp.setProperty(name, value);
                cmdlineOptsSet.add(name);

                // Is it a game option default value?
                if (name.startsWith(PROP_JSETTLERS_GAMEOPT_PREFIX))
                {
                    final String optKey = name.substring(pfxL).toUpperCase(Locale.US);
                    boolean ok = true;
                    if (optKey.length() == 0)
                    {
                        System.err.println("Empty game option name in property key: " + name);
                        ok = false;
                    } else {
                        hasSetGameOptions = true;
                        try
                        {
                            parseCmdline_GameOption
                                (SOCGameOption.parseOptionNameValue(optKey, value, false),
                                 optKey + "=" + value, gameOptsAlreadySet);
                            // Reminder: This call adds optKey to gameOptsAlreadySet
                            // or throws exception if already there (opt twice on command line, etc)
                        } catch (IllegalArgumentException e) {
                            ok = false;
                            System.err.println(e.getMessage());
                            System.err.println();
                            printGameOptions();
                        }
                    }

                    if (! ok)
                    {
                        return null;
                    }
                }
            }
            else if (arg.startsWith("--pw-reset"))
            {
                String name = null;

                if (arg.length() == 10)
                {
                    // next arg should be username
                    ++aidx;
                    if (aidx < args.length)
                        name = args[aidx];
                } else {
                    // this arg should continue: =username
                    if (arg.charAt(10) != '=')
                    {
                        System.err.println("Unknown argument: " + arg);
                        return null;
                    }
                    name = arg.substring(11);
                }

                if ((name == null) || (name.length() == 0))
                {
                    System.err.println("Missing username after --pw-reset");
                    return null;
                }
                argp.setProperty(SOCDBHelper.PROP_IMPL_JSETTLERS_PW_RESET, name);

            } else {
                System.err.println("Unknown argument: " + arg);
                hasUnknowns = true;
            }

            ++aidx;
        }
        // End of named-parameter loop

        if (! gameOptsAlreadySet.isEmpty())
        {
            // check cmdline's "SC" game opt vs any others; prints warnings if conflicts,
            // prints error and returns false if "SC" scenario name is unknown
            if (! init_checkScenarioOpts
                (gameOptsAlreadySet, false, "Command line", null, null))
            {
                return null;  // <--- Early return: Unknown scenario name ---
            }

            if (gameOptsAlreadySet.containsKey("SC") && ! argp.isEmpty())
            {
                // also check cmdline's "SC" vs gameopts in properties file
                final String scName = SOCGameOption.getOption("SC", false).getStringValue();
                if (scName.length() > 0)
                    init_checkScenarioOpts(argp, true, SOC_SERVER_PROPS_FILENAME, scName, "command line");
            }
        }

        // Done parsing flagged parameters.
        // Look for the positional ones.
        if ((args.length - aidx) == 0)
        {
            // No positional parameters: Take defaults.
            // Check each one before setting it, in case was specified in properties file
            if (! argp.containsKey(PROP_JSETTLERS_PORT))
                argp.setProperty(PROP_JSETTLERS_PORT, Integer.toString(SOC_PORT_DEFAULT));
            if (! argp.containsKey(PROP_JSETTLERS_CONNECTIONS))
                argp.setProperty(PROP_JSETTLERS_CONNECTIONS, Integer.toString(SOC_MAXCONN_DEFAULT));
            // PROP_JSETTLERS_DB_USER, _PASS are set below
        } else {
            // Require at least 2 parameters
            if ((args.length - aidx) < 2)
            {
                if (! printedUsageAlready)
                {
                    // Print this hint only if parsed OK up to now, and
                    // if we haven't responded to -h / --help already.
                    System.err.println("SOCServer: Some required command-line parameters are missing.");
                }
                printUsage(false);

                return null;
            }

            argp.setProperty(PROP_JSETTLERS_PORT, args[aidx]);  ++aidx;
            argp.setProperty(PROP_JSETTLERS_CONNECTIONS, args[aidx]);  ++aidx;

            // Optional DB user and password
            if ((args.length - aidx) > 0)
            {
                // Check DB user and password against any -D parameters in properties
                if (cmdlineOptsSet.contains(SOCDBHelper.PROP_JSETTLERS_DB_USER)
                    || cmdlineOptsSet.contains(SOCDBHelper.PROP_JSETTLERS_DB_PASS))
                {
                    System.err.println("SOCServer: DB user and password cannot appear twice on command line.");
                    printUsage(false);
                    return null;
                }

                argp.setProperty(SOCDBHelper.PROP_JSETTLERS_DB_USER, args[aidx]);  ++aidx;
                if ((args.length - aidx) > 0)
                {
                    argp.setProperty(SOCDBHelper.PROP_JSETTLERS_DB_PASS, args[aidx]);  ++aidx;
                } else {
                    argp.setProperty(SOCDBHelper.PROP_JSETTLERS_DB_PASS, "");
                }
            }
        }

        // If no positional parameters db_user db_pass, take defaults.
        // Check each one before setting it, in case was specified in properties file
        if (! argp.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_USER))
        {
            argp.setProperty(SOCDBHelper.PROP_JSETTLERS_DB_USER, "socuser");
            if (! argp.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_PASS))
                argp.setProperty(SOCDBHelper.PROP_JSETTLERS_DB_PASS, "socpass");
        }
        else if (! argp.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_PASS))
        {
            // specified _USER but not _PASS: store "" for empty password instead of default
            argp.setProperty(SOCDBHelper.PROP_JSETTLERS_DB_PASS, "");
        }

        // Make sure no more flagged parameters
        if (aidx < args.length)
        {
            if (! printedUsageAlready)
            {
                if (args[aidx].startsWith("-"))
                {
                    System.err.println("SOCServer: Options must appear before, not after, the port number.");
                } else {
                    System.err.println("SOCServer: Options must appear before the port number, not after dbuser/dbpass.");
                }
                printUsage(false);
            }
            return null;
        }

        if (hasUnknowns)
            return null;

        // Done parsing.
        return argp;
    }

    /**
     * Set game option defaults from any {@code jsettlers.gameopt.*} server properties found.
     * Option keynames are case-insensitive past that prefix.
     * See {@link #PROP_JSETTLERS_GAMEOPT_PREFIX} for expected syntax.
     * Calls {@link #parseCmdline_GameOption(SOCGameOption, String, HashMap)} for each one found,
     * to set its current value in {@link SOCGameOptions}'s static set of known opts.
     *<P>
     * Note that an unknown {@link SOCSscenario} name (value of game option {@code "SC"})
     * is not an error here; {@link #init_checkScenarioOpts(Map, boolean, String, String, String)}
     * will check for that and its caller will halt startup if found.
     *
     * @param pr  Properties which may contain {@link #PROP_JSETTLERS_GAMEOPT_PREFIX}* entries.
     *     If {@code pr} contains entries with non-uppercase gameopt names, cannot be read-only:
     *     Will replace keys such as {@code "jsettlers.gameopt.vp"} with their canonical
     *     uppercase equivalent: {@code "jsettlers.gameopt.VP"}
     * @throws IllegalArgumentException if any game option property has a bad name or value.
     *     {@link Throwable#getMessage()} will collect all option problems to 1 string, separated by {@code "\n"}:
     *     <UL>
     *     <LI> Empty game option name after {@code jsettlers.gameopt.} prefix
     *     <LI> Unknown option name
     *     <LI> Problem with name or value reported from {@link #parseCmdline_GameOption(SOCGameOption, String, HashMap)}
     *     </UL>
     * @since 1.1.20
     */
    private static final void init_propsSetGameopts(Properties pr)
        throws IllegalArgumentException
    {
        // javadoc note: This method is private; public parseCmdline_DashedArgs calls it, so for visibility
        // this method's javadoc section about game option properties is also there.  If you update javadocs here,
        // also update the same text in parseCmdline_DashedArgs's javadoc.

        final int pfxL = PROP_JSETTLERS_GAMEOPT_PREFIX.length();
        StringBuilder problems = null;

        // First, canonicalize any game opt key names to uppercase
        {
            ArrayList<String> makeUpper = new ArrayList<String>();
            for (Object k : pr.keySet())
            {
                if (! ((k instanceof String) && ((String) k).startsWith(PROP_JSETTLERS_GAMEOPT_PREFIX)))
                    continue;

                final String optKey = ((String) k).substring(pfxL),
                             optUC = optKey.toUpperCase(Locale.US);
                if (! optKey.equals(optUC))
                {
                    makeUpper.add((String) k);
                    makeUpper.add(PROP_JSETTLERS_GAMEOPT_PREFIX + optUC);
                }
            }

            for (int i = 0; i < makeUpper.size(); i += 2)
            {
                final String propKey = makeUpper.get(i),
                             propUC = makeUpper.get(i + 1);
                pr.put(propUC, pr.get(propKey));
                pr.remove(propKey);
            }
        }

        // Now parse, set current values, and look for problems
        for (Object k : pr.keySet())
        {
            if (! ((k instanceof String) && ((String) k).startsWith(PROP_JSETTLERS_GAMEOPT_PREFIX)))
                continue;

            final String optKey = ((String) k).substring(pfxL);  // "jsettlers.gameopt.N7" -> "N7"
            if (optKey.length() == 0)
            {
                if (problems == null)
                    problems = new StringBuilder();
                else
                    problems.append("\n");
                problems.append("Empty game option name in property key: ");
                problems.append(k);
                continue;
            }

            try
            {
                // parse this gameopt and set its current value in SOCGameOptions static set of known opts
                final String optVal = pr.getProperty((String) k);
                parseCmdline_GameOption
                    (SOCGameOption.parseOptionNameValue(optKey, optVal, false),
                     optKey + '=' + optVal, null);
                hasSetGameOptions = true;
            } catch (IllegalArgumentException e) {
                if (problems == null)
                    problems = new StringBuilder();
                else
                    problems.append("\n");
                problems.append(e.getMessage());
            }
        }

        if (problems != null)
            throw new IllegalArgumentException(problems.toString());
    }

    /**
     * When a server's properties or command line contain a default scenario (game option {@code "SC"}),
     * check that the scenario is known and that its game options don't conflict with any others specified
     * in the properties or command line.
     *<P>
     * The scenario named in {@code opts.get("SC")} or {@code scName} is retrieved with
     * {@link SOCScenario#getScenario(String)}; an empty scenario name "" is treated as an unknown scenario.
     *<P>
     * The scenario will be the default scenario, but its option values aren't set as default at server startup.
     * When a new game begins using that scenario, at that time scenario options override any other options
     * specified for the game (except whichever {@code "VP"} is greater is kept).  Since the user may specify
     * a different scenario, 'conflicting' options in {@code opts} are only potentially a problem and will be
     * returned as a list for warnings (not errors) to be printed during server init.
     *
     * @param opts  Option name key and value strings, typically from command line or
     *    properties file parsing.  See {@code optsIsFromProps} for format of {@code opts} keys and values.
     * @param optsAreProps  If <B>true</B>, {@code opts} keys and values are from the properties file:
     *    key = {@link #PROP_JSETTLERS_GAMEOPT_PREFIX} + optname, value = option value.
     *    Option names are not case-sensitive but {@link #PROP_JSETTLERS_GAMEOPT_PREFIX} is.
     *    <br>
     *    If <B>false</B>, keys and values are from command line parsing:
     *    key = uppercase optname, value = optkey + "=" + option value.
     * @param scName  Scenario name to check against, or {@code null} to use value of {@code opts.get("SC"}); never ""
     * @returns A list of game option names and value strings from {@code opts} which would be overwritten by
     *     those from opts' {@code "SC"} scenario, or {@code null} if no potential conflicts.
     *    <P>
     *     For ease of use by caller, the extracted default scenario is the first item in the list.
     *     (If there are no conflicts, the list is {@code null}; the scenario will not be returned.)
     *     This list item's {@link Triple} contains:
     *     <UL>
     *      <LI> {@code "SC"}
     *      <LI> specified scenario name, such as {@code "SC_FOG"}
     *      <LI> scenario game options string ({@link SOCScenario#scOpts}), never "" or {@code null}
     *     </UL>
     *    <P>
     *     Each other list item is a {@link Triple} containing:
     *     <UL>
     *      <LI> option name
     *      <LI> value in {@code opts}
     *      <LI> value in default scenario
     *    </UL>
     *    <P>
     *     If the specified scenario is unknown at this version, the returned list will contain only 1 item,
     *     a {@code Triple} with:
     *     <UL>
     *      <LI> {@code "SC"}
     *      <LI> specified scenario name
     *      <LI> {@code null}
     *     </UL>
     * @since 2.0.00
     */
    public static List<Triple> checkScenarioOpts
        (Map<?, ?> opts, final boolean optsAreProps, String scName)
    {
        List<Triple> scenConflictWarns = null;

        if (scName == null)
        {
            final String scKey = (optsAreProps) ? (PROP_JSETTLERS_GAMEOPT_PREFIX + "SC") : "SC";
            if (opts.containsKey(scKey))
            {
                scName = (String) opts.get(scKey);
                if (! optsAreProps)
                    scName = scName.substring(scName.indexOf('=') + 1).trim();
                       // indexOf should be okay, because this is called after parsing cmdline options;
                       // if somehow it's -1 then we get entire string from substring(0).
            } else {
                return null;  // <--- Early return: no default scenario ---
            }
        }

        scenConflictWarns = new ArrayList<Triple>();

        final SOCScenario sc = SOCScenario.getScenario(scName);
        if (sc == null)
        {
            scenConflictWarns.add(new Triple("SC", scName, null));

            return scenConflictWarns;  // <--- Early return: unknown scenario ---
        }

        final String scOptsStr = sc.scOpts;
        if ((scOptsStr == null) || (scOptsStr.length() == 0))
            return null;  // <--- Early return: no gameopts in scenario ---

        if (optsAreProps)
        {
            // Normalize to allow case-insensitive searching of key names:
            // jsettlers.gameopt.NT -> jsettlers.gameopt.nt

            Map<String, Object> normOpts = new HashMap<String, Object>();
            for (Object k : opts.keySet())
            {
                if (! ((k instanceof String) && ((String) k).startsWith(PROP_JSETTLERS_GAMEOPT_PREFIX)))
                    continue;

                normOpts.put(((String) k).toLowerCase(Locale.US), opts.get(k));
            }

            opts = normOpts;
        }

        final Map<String, SOCGameOption> scOpts = SOCGameOption.parseOptionsToMap(scOptsStr);

        StringBuilder sb = new StringBuilder();
        for (SOCGameOption scOpt : scOpts.values())
        {
            final String optKey = (optsAreProps)
                ? (PROP_JSETTLERS_GAMEOPT_PREFIX + scOpt.key).toLowerCase(Locale.US)
                : scOpt.key;
            if (! opts.containsKey(optKey))
                continue;

            String mapOptVal = (String) opts.get(optKey);
            if (! optsAreProps)
                mapOptVal = mapOptVal.substring(mapOptVal.indexOf('=') + 1).trim();
                   // indexOf should be okay, because this is called after parsing cmdline options;
                   // if somehow it's -1 then we get entire string from substring(0).
            mapOptVal = mapOptVal.toLowerCase(Locale.US);  // for intbool t/f chars

            sb.setLength(0);  // reset from previous iteration
            scOpt.packValue(sb);

            if (! scOpt.key.equals("VP"))
            {
                final String scOptVal = sb.toString().toLowerCase(Locale.US);
                if (! mapOptVal.equals(scOptVal))
                    scenConflictWarns.add(new Triple(scOpt.key, mapOptVal, scOptVal));
            } else {
                // VP: special case: warn only if scen has false or a lower int value

                if (mapOptVal.charAt(0) == 'f')
                    continue;  // opts map doesn't specify VP

                if (scOpt.getBoolValue())
                {
                    int mapVP;
                    try {
                        mapVP = Integer.parseInt(mapOptVal.substring(1));
                    } catch (NumberFormatException e ) {
                        mapVP = 0;  // unlikely, would already have been caught by cmdline parsing
                    }

                    if (mapVP <= scOpt.getIntValue())
                        continue;  // opts map's VP not greater than scen's VP
                }

                scenConflictWarns.add(new Triple(scOpt.key, mapOptVal, sb.toString()));
            }
        }

        if (scenConflictWarns.isEmpty())
            return null;

        // insert scenario name and opts at start of list
        scenConflictWarns.add(0, new Triple("SC", scName, scOptsStr));

        return scenConflictWarns;
    }

    /**
     * During startup, call {@link #checkScenarioOpts(Map, boolean, String)} and print any warnings it returns
     * to {@link System#err}.  An unknown scenario name is printed as an error not a warning.
     * An empty scenario name "" from {@code opts.get("SC")} or {@code scName} is treated as an unknown scenario.
     * @param opts  Options to check, see {@link #checkScenarioOpts(Map, boolean, String)}
     * @param optsAreProps  Are {@code opts} from properties or command line?
     *     See {@link #checkScenarioOpts(Map, boolean, String)}.
     * @param srcDesc  Description of {@code opts} for warning message text:
     *     "Command line" or properties filename "jsserver.properties"
     * @param scName  Scenario name to check against, or {@code null} to use value of {@code opts.get("SC"}); never ""
     * @param scNameSrcDesc  If {@code scName} isn't from {@code opts}, lowercase description of its source
     *     for warnings (like {@code srcDesc}), otherwise {@code null}.
     *     If {@code scNameSrcDesc != null}, will not print a warning if {@code scName} is unknown, to avoid
     *     repeating the warning already printed when that SC was checked while parsing its source.
     * @return True if the provided scenario name is known or there is no {@code "SC"} option, false if unknown.
     * @since 2.0.00
     */
    private static boolean init_checkScenarioOpts
        (final Map<?, ?> opts, final boolean optsAreProps, final String srcDesc, String scName, String scNameSrcDesc)
    {
        List<Triple> warns = checkScenarioOpts(opts, optsAreProps, scName);
        if (warns == null)
            return true;

        if (scName == null)
            scName = (String) (warns.get(0).getB());  // first list item is scenario info, optName "SC"

        boolean scenKnown = true;
        for (Triple warn : warns)
        {
            final String optName = (String) (warn.getA());
            if (optName.equals("SC"))
            {
                if ((warn.getC() == null) && (scNameSrcDesc == null))
                {
                    System.err.println("Error: " + srcDesc + " default scenario " + scName + " is unknown");
                    scenKnown = false;
                }
            } else {
                System.err.println("Warning: " + srcDesc + " game option " + optName + " value " + warn.getB()
                    + ((scNameSrcDesc != null)
                           ? (" is changed in " + scNameSrcDesc + " default scenario ")
                           : " is changed in default scenario ")
                    + scName + " to " + warn.getC());
            }
        }

        return scenKnown;
    }

    /**
     * If command line contains {@code --pw-reset=username},
     * prompt for and change that user's password.
     *<P>
     * If successful, sets {@link #getUtilityModeMessage()} to "The password was changed"
     * or similar; if unsuccessful (no db, user not found, etc), prints an error and
     * sets {@link #getUtilityModeMessage()} to {@code null}.
     *
     * @param uname  Username to change password
     * @since 1.1.20
     */
    private void init_resetUserPassword(final String uname)
    {
        utilityModeMessage = null;

        if (! SOCDBHelper.isInitialized())
        {
            System.err.println("--pw-reset requires database connection properties.");
            return;
        }

        System.out.println("Resetting password for " + uname + ".");

        try
        {
            if (! SOCDBHelper.doesUserExist(uname))
            {
                System.err.println("pw-reset user " + uname + " not found in database.");
                return;
            }
        } catch (SQLException e) {
            System.err.println("Error while querying user " + uname + ": " + e.getMessage());
            return;
        }

        StringBuilder pw1 = null;
        boolean hasNewPW = false;
        for (int tries = 0; tries < 3; ++tries)
        {
            if (tries > 0)
                System.out.println("Passwords do not match; try again.");

            pw1 = readPassword("Enter the new password:");
            if (pw1 == null)
                break;

            StringBuilder pw2 = readPassword("Confirm new password:  ");

            if (pw2 == null)
            {
                break;
            } else {
                // compare; unfortunately there is no StringBuffer.equals(sb) method

                final int L1 = pw1.length(), L2 = pw2.length();
                if (L1 == L2)
                {
                    final char[] pc1 = new char[L1], pc2 = new char[L2];
                    pw1.getChars(0, L1, pc1, 0);
                    pw2.getChars(0, L2, pc2, 0);

                    hasNewPW = (Arrays.equals(pc1, pc2));

                    Arrays.fill(pc1, (char) 0);
                    Arrays.fill(pc2, (char) 0);
                }

                if (hasNewPW)
                {
                    clearBuffer(pw2);
                    break;
                }
            }
        }

        if (! hasNewPW)
        {
            if (pw1 != null)
                clearBuffer(pw1);
            System.err.println("Password reset cancelled.");
            return;
        }

        try
        {
            SOCDBHelper.updateUserPassword(uname, pw1.toString());
            clearBuffer(pw1);
            utilityModeMessage = "The password was changed";
        } catch (SQLException e) {
            System.err.println("Error while resetting password: " + e.getMessage());
        }

    }

    /**
     * Print a security-action audit message to {@link System#out} in a standard format.
     *<H5>Example with object:</H5>
     *   Audit: Requested jsettlers account creation, already exists: '{@code obj}'
     *      by '{@code req}' from {@code reqHost} at {@code at}
     *<H5>Example without object:</H5>
     *   Audit: Requested jsettlers account creation, this requester not on account admin whitelist:
     *      '{@code req}' from {@code reqHost} at {@code at}
     *
     * @param req  Requesting user, or {@code null} if unknown
     * @param msg  Message text
     * @param obj  Object affected by the action, or {@code null} if none
     * @param at   Timestamp, or {@code null} to use current time
     * @param reqHost  Requester client's hostname, from {@link StringConnection#host()}
     * @since 1.1.20
     */
    private void printAuditMessage
        (final String req, final String msg, final String obj, Date at, final String reqHost)
    {
        if (at == null)
            at = new Date();

        if (obj != null)
            System.out.println
                ("Audit: " + msg + ": '" + obj
                 + ((req != null) ? "' by '" + req : "")
                 + "' from " + reqHost + " at " + at);
        else
            System.out.println
                ("Audit: " + msg + ": "
                 + ((req != null) ? "'" + req + "'" : "")
                 + " from " + reqHost + " at " + at);
    }

    /**
     * Track whether we've already called {@link #printUsage(boolean)}.
     * @since 1.1.07
     */
    public static boolean printedUsageAlready = false;

    /**
     * Print command line parameter information, including options ("--" / "-").
     * @param longFormat short or long?
     * Long format gives details and also calls {@link Version#printVersionText(java.io.PrintStream, String)} beforehand.
     * Short format is printed at most once, after checking {@link #printedUsageAlready}.
     * @since 1.1.07
     */
    public static void printUsage(final boolean longFormat)
    {
        if (printedUsageAlready && ! longFormat)
            return;
        printedUsageAlready = true;

        if (longFormat)
        {
            Version.printVersionText(System.err, "Java Settlers Server ");
        }
        System.err.println("usage: java soc.server.SOCServer [option...] port_number max_connections [dbUser [dbPass]]");
        if (longFormat)
        {
            System.err.println("usage: recognized options:");
            System.err.println("       -V or --version    : print version information");
            System.err.println("       -h or --help or -? : print this screen");
            System.err.println("       -o or --option name=value : set per-game options' default values");
            System.err.println("       -D name=value : set properties such as " + SOCDBHelper.PROP_JSETTLERS_DB_USER);
            System.err.println("-- Recognized properties: --");
            for (int i = 0; i < PROPS_LIST.length; ++i)
            {
                System.err.print("\t");
                System.err.print(PROPS_LIST[i]);    // name
                ++i;
                System.err.print("\t");
                System.err.println(PROPS_LIST[i]);  // description
            }
            printGameOptions();
        } else {
            System.err.println("       use java soc.server.SOCServer --help to see recognized options");
        }
    }

    /**
     * Print out the list of possible game options, and current values.
     * @since 1.1.07
     */
    public static void printGameOptions()
    {
        final Map<String, SOCGameOption> allopts = SOCGameOption.getAllKnownOptions();

        System.err.println("-- Current default game options: --");

        ArrayList<String> okeys = new ArrayList<String>(allopts.keySet());
        Collections.sort(okeys);
        for (final String okey : okeys)
        {
            SOCGameOption opt = allopts.get(okey);

            if (opt.hasFlag(SOCGameOption.FLAG_INTERNAL_GAME_PROPERTY))
                continue;

            boolean quotes = (opt.optType == SOCGameOption.OTYPE_STR) || (opt.optType == SOCGameOption.OTYPE_STRHIDE);
            // OTYPE_* - consider any type-specific output in this method.

            StringBuilder sb = new StringBuilder("  ");
            sb.append(okey);
            sb.append(" (");
            sb.append(SOCGameOption.optionTypeName(opt.optType));
            sb.append(") ");
            if (quotes)
                sb.append('"');
            opt.packValue(sb);
            if (quotes)
                sb.append('"');
            sb.append("  ");
            sb.append(opt.getDesc());
            System.err.println(sb.toString());

            if (opt.enumVals != null)  // possible values of OTYPE_ENUM
            {
                sb.setLength(0);
                sb.append("    option choices (1-n): ");
                for (int i = 1; i <= opt.maxIntValue; ++i)
                {
                    sb.append(' ');
                    sb.append(i);
                    sb.append(' ');
                    sb.append(opt.enumVals[i-1]);
                    sb.append(' ');
                }
                System.err.println(sb.toString());
            }
        }

        int optsVers = SOCVersionedItem.itemsMinimumVersion(allopts);
        if (optsVers > -1)
        {
            System.err.println
                ("*** Note: Client version " + Version.version(optsVers)
                 + " or newer is required for these game options. ***");
            System.err.println
                ("          Games created with different options may not have this restriction.");
        }
    }

    /**
     * Clear the contents of a StringBuffer by setting to ' '
     * each character in its current {@link StringBuilder#length()}.
     * @param sb  StringBuilder to clear
     * @since 1.1.20
     */
    private static void clearBuffer(StringBuilder sb)
    {
        final int L = sb.length();
        for (int i = 0; i < L; ++i)
            sb.setCharAt(i, (char) 0);
    }

    /**
     * Buffered {@link System#in} for {@link #readPassword(String)},
     * is {@code null} until first call to that method.
     * @since 1.1.20
     */
    private static BufferedReader sysInBuffered = null;

    /**
     * Read a password from the console; currently used for password reset.
     * Blocks the calling thread while waiting for input.
     *<P>
     * This rudimentary method exists for compatability: java 1.5 doesn't have
     * {@code System.console.readPassword()}, and the Eclipse console also
     * doesn't offer {@code System.console}.
     *<P>
     * <B>The input is not masked</B> because there's no cross-platform way to do so in 1.5.
     *
     * @param prompt  Optional password prompt; default is "Password:"
     * @return  The password read, or an empty string "" if an error occurred.
     *     This is returned as a mutable StringBuilder
     *     so the caller can clear its contents when done, using
     *     {@link #clearBuffer(StringBuilder)}.
     *     If ^C or an error occurs, returns {@code null}.
     * @since 1.1.20
     */
    private static StringBuilder readPassword(String prompt)
    {
        // java 1.5 doesn't have System.console.readPassword
        // (TODO) consider reflection for 1.6+ JREs

        // System.in can read only an entire line (no portable raw mode in 1.5),
        // so we can't mask after each character.

        if (prompt == null)
            prompt = "Password:";

        System.out.print(prompt);
        System.out.print(' ');
        System.out.flush();

        if (sysInBuffered == null)
            sysInBuffered = new BufferedReader(new InputStreamReader(System.in));

        try
        {
            StringBuilder sb = new StringBuilder();
            sb.append(sysInBuffered.readLine());

            // Remove trailing newline char(s)
            while (true)
            {
                int L = sb.length();
                if (L == 0)
                    break;

                final char ch = sb.charAt(L - 1);
                if ((ch != '\n') && (ch != '\r'))
                    break;

                sb.setLength(L - 1);
            }

            return sb;
        }
        catch (IOException e) {
            return null;
        }
    }

    /**
     * Starting the server from the command line
     *<P>
     * Checks for the optional server startup properties file {@code "jsserver.properties"},
     * and parses the command line for switches. If a property appears on the command line and
     * also in {@code jsserver.properties}, the command line's value overrides the file's.
     *<P>
     * Creates and starts a {@link SOCServer} via {@link #SOCServer(int, Properties)}.
     *<P>
     * If there are problems with the network setup,
     * or with running a {@link SOCDBHelper#PROP_JSETTLERS_DB_SCRIPT_SETUP db setup script},
     * this method will call {@link System#exit(int) System.exit(1)}.
     *<P>
     * If a db setup script runs successfully,
     * this method will call {@link System#exit(int) System.exit(2)}.
     *
     * @param args  arguments: port number, etc
     * @see #printUsage(boolean)
     */
    static public void main(String[] args)
    {
        Properties argp = parseCmdline_DashedArgs(args);  // also reads jsserver.properties if exists
        if (argp == null)
        {
            printUsage(false);
            return;
        }
        if (hasStartupPrintAndExit)
        {
            return;
        }

        try
        {
            int port = Integer.parseInt(argp.getProperty(PROP_JSETTLERS_PORT));

            // SOCServer constructor will also print game options if we've set them on
            // commandline, or if any option defaults require a minimum client version.

            try
            {
                SOCServer server = new SOCServer(port, argp);
                if (! server.hasUtilityModeProperty())
                {
                    server.setPriority(5);
                    server.start();  // <---- Start the Main SOCServer Thread: serverUp() method ----
                } else {
                    String pval = argp.getProperty(SOCDBHelper.PROP_IMPL_JSETTLERS_PW_RESET);
                    if (pval != null)
                        server.init_resetUserPassword(pval);

                    final String msg = server.getUtilityModeMessage();
                    System.err.println(
                        (msg != null)
                            ? "\n" + msg + ". Exiting now.\n"
                            : "\nExiting now.\n"
                        );
                }

                // Most threads are started in the SOCServer constructor, via initSocServer.
                // initSocServer also handles command line and properties-file contents,
                // including game option default/current values.
                // Messages from clients are handled in SOCMessageHandler.dispatch()
                // called from a loop in InboundMessageQueue's treater thread.
            }
            catch (SocketException e)
            {
                // network setup problem
                System.err.println(e.getMessage());  // "* Exiting due to network setup problem: ..."
                System.exit (1);
            }
            catch (EOFException e)
            {
                // The sql setup script was ran successfully by initialize;
                // exit server, user will re-run without the setup script param.
                System.err.println("\nDB setup script was successful. Exiting now.\n");
                System.exit(2);
            }
            catch (SQLException e)
            {
                // the sql setup script was ran by initialize, but failed to complete.
                // or, a db URL was specified and server was unable to connect.
                // exception detail was printed in initSocServer.
                if (argp.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP))
                    System.err.println("\n* DB setup script failed. Exiting now.\n");
                System.exit(1);
            }
            catch (IllegalArgumentException e)
            {
                System.err.println(e.getMessage());
                System.err.println("\n* Error in game options properties: Exiting now.\n");
                System.exit(1);
            }
        }
        catch (Throwable e)
        {
            printUsage(false);
            return;
        }

    }  // main

}  // public class SOCServer
