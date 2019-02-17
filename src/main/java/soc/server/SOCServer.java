/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2005 Chadwick A McHenry <mchenryc@acm.org>
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
package soc.server;

import soc.debug.D;  // JM

import soc.game.*;
import soc.message.*;

import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.server.database.DBSettingMismatchException;
import soc.server.database.SOCDBHelper;

import soc.server.genericServer.Connection;
import soc.server.genericServer.InboundMessageQueue;
import soc.server.genericServer.Server;
import soc.server.genericServer.StringConnection;

import soc.util.I18n;
import soc.util.SOCFeatureSet;
import soc.util.SOCGameBoardReset;
import soc.util.SOCGameList;  // used in javadoc
import soc.util.SOCRobotParameters;
import soc.util.SOCStringManager;
import soc.util.Triple;
import soc.util.Version;

import java.io.BufferedReader;
import java.io.Console;
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
import java.util.Timer;
import java.util.TimerTask;
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
 *
 *<H3>Network Traffic and Message Flow:</H3>
 *
 * All messages from client connections arrive through {@link SOCMessageDispatcher}
 * into either {@link SOCGameMessageHandler} or {@link SOCServerMessageHandler},
 * depending on whether the message is about a specific game. See those classes'
 * javadocs for more details.
 *<P>
 * The first message the server sends over the connection is its version
 * and features ({@link SOCVersion}). This is sent immediately without
 * first waiting for the client to send its version.
 *<P>
 * The first message the client sends over the connection is its version and locale
 * ({@link SOCVersion}), to which the server responds with either {@link SOCRejectConnection}
 * or the lists of channels and games ({@link SOCChannels}, {@link SOCGames}).
 *
 *<H3>The most important server systems:</H3>
 *<UL>
 *<LI> {@code SOCServer} manages the overall lifecycle of the server
 *     and its games; game creation occurs in
 *     {@link SOCGameListAtServer#createGame(String, String, String, Map, GameHandler)}.
 *<LI> See {@link Server} for details of the server threading and processing.
 *     After constructing a {@code SOCServer} instance, the caller must
 *     call {@link Thread#start()} to run the server's main thread.
 *<LI> Any built-in bots are started at {@link #serverUp()}.
 *<LI> After a game is created, in-game actions are handled by {@link SOCGameHandler}
 *     as called by the message handlers for client requests and actions in {@link SOCGameMessageHandler}
 *     and the game-lifecycle message handlers in {@link SOCServerMessageHandler}.
 *<LI> See {@link SOCMessage} for details of the client/server protocol.
 *<LI> To get a player's connection, use {@link Server#getConnection(String) getConnection(plName)}.
 *<LI> To get a client's nickname, use <tt>(String)</tt> {@link Connection#getData() connection.getData()}.
 *<LI> To get the rest of a client's data, use ({@link SOCClientData})
 *     {@link Connection#getAppData() connection.getAppData()}.
 *<LI> To send a message to all players in a game, use {@link #messageToGame(String, SOCMessage)}
 *     and related methods. Send text with {@link #messageToGameKeyed(SOCGame, boolean, String)}.
 *<LI> For i18n, nearly all text sent from the server starts as a unique key
 *     appearing in {@code soc/server/strings/*.properties} and is localized
 *     to the client's locale through {@link Connection#getLocalized(String)}.
 *<LI> Timer threads are used to check for inactive robots and idle games: See
 *     {@link SOCGameTimeoutChecker} and {@link SOCGameHandler#endTurnIfInactive(SOCGame, long)}.
 *</UL>
 *
 *<H3>Properties and features:</H3>
 * Java properties (starting with {@code "jsettlers."}) were added in 1.1.09, with constant names
 * starting with {@code PROP_JSETTLERS_} and listed in {@link #PROPS_LIST}.
 * Some properties activate optional {@link #features} of the server.
 *
 *<H3>Debug Commands:</H3>
 * The server supports several debug commands when {@link #isDebugUserEnabled()}, and
 * when sent as chat messages by a user named {@code "debug"} or by the only human in a practice game.
 * See {@link #processDebugCommand(Connection, String, String, String)} and
 * {@link SOCServerMessageHandler#handleGAMETEXTMSG(Connection, SOCGameTextMsg)}
 * for details.
 *
 *<H3>Other Notes:</H3>
 * The version check timer is set in {@link SOCClientData#setVersionTimer(SOCServer, Connection)}.
 * Before 1.1.06 the server's currently active game and channel lists were sent beforehand,
 * and client version was then sent in reply to server's version.
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
     * Since 30% of bots are smart, this will start 5 fast and 2 smart robots.
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
    // add it to PROPS_LIST, and update /src/main/bin/jsserver.properties.sample.
    // If a new robot property, consider mentioning it in soc.robot.sample3p.Sample3PClient javadocs.

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
     * Integer property <tt>jsettlers.bots.percent3p</tt> to set a goal for the minimum
     * percentage of third-party bots when randomly picking robots to start a game.
     * If set, should be in range 0 to 100 inclusive.
     *<P>
     * If not enough third-party bots are connected to the server when starting a game,
     * the built-in bots will be used instead so that the game can begin. If also using
     * {@link #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL}, remember those games will be started
     * as soon as the server is ready, so the third-party bots may not yet be connected.
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_BOTS_PERCENT3P = "jsettlers.bots.percent3p";

    /**
     * Integer property <tt>jsettlers.bots.timeout.turn</tt> to increase
     * {@link #ROBOT_FORCE_ENDTURN_SECONDS} for third-party robots, which may
     * have more complex logic than the built-in bots. The third-party bots will
     * have this many seconds to make a move before the server ends their turn.
     *<P>
     * Default is {@link #ROBOT_FORCE_ENDTURN_SECONDS}. The built-in robots are limited
     * to {@code ROBOT_FORCE_ENDTURN_SECONDS} even when this property is set.
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_BOTS_TIMEOUT_TURN = "jsettlers.bots.timeout.turn";

    /**
     * Integer property <tt>jsettlers.bots.fast_pause_percent</tt> to adjust
     * the speed-up factor for bots' pause times between actions when {@link SOCGame#isBotsOnly}:
     * Sets {@link soc.robot.SOCRobotBrain#BOTS_ONLY_FAST_PAUSE_FACTOR}.
     *<P>
     * Default is 25, for 25% of normal pauses (4x speed). Use 1 for a shorter delay (1% of normal pauses).
     * @see #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_BOTS_FAST__PAUSE__PERCENT = "jsettlers.bots.fast_pause_percent";

    /**
     * Integer property <tt>jsettlers.bots.botgames.total</tt> will start robot-only games,
     * a few at a time, until this many have been played. (The default is 0.)
     * As the first few games end, the server will start new games until the total is reached.
     *<P>
     * To adjust the robot-only game speed and server load, use
     * {@link #PROP_JSETTLERS_BOTS_FAST__PAUSE__PERCENT} and
     * {@link #PROP_JSETTLERS_BOTS_BOTGAMES_PARALLEL}.
     *<P>
     * To wait at server startup time before starting these games, use
     * {@link #PROP_JSETTLERS_BOTS_BOTGAMES_WAIT__SEC}. To shut down the server
     * after they all finish, use {@link #PROP_JSETTLERS_BOTS_BOTGAMES_SHUTDOWN}.
     *<P>
     * If this property's value != 0, a robots-only game can be started with the
     * {@code *STARTBOTGAME*} debug command. This can be used to test the bots with any given
     * combination of game options and scenarios.  To permit starting such games without
     * automatically starting any at server startup, use a value less than 0.
     *<P>
     * If this property's value != 0, a game with 1 human player against bots, and 1 or more observers,
     * won't be ended if that sole human player quits. A bot will replace the human, and
     * the game will continue as a robots-only game. Otherwise any robots-only game will be
     * ended even if it has observers.
     *
     * @see #PROP_JSETTLERS_BOTS_PERCENT3P
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL = "jsettlers.bots.botgames.total";

    /**
     * Integer property <tt>jsettlers.bots.botgames.parallel</tt>:
     * When server is starting robot-only games ({@link #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL} > 0),
     * start this many at once.
     *<P>
     * Default is 2. Use 0 to start them all.
     *
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_BOTS_BOTGAMES_PARALLEL = "jsettlers.bots.botgames.parallel";

    /**
     * Boolean property <tt>jsettlers.bots.botgames.shutdown</tt>:
     * If true, when server has started robot-only games ({@link #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL} > 0)
     * and those have finished, shut down the server if no other games are active. (Default is false.)
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_BOTS_BOTGAMES_SHUTDOWN = "jsettlers.bots.botgames.shutdown";

    /**
     * Integer property <tt>jsettlers.bots.botgames.wait_sec</tt> to wait this many seconds
     * before starting robot-only games with {@link #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL}.
     * This is useful if some bots are slow to start, or are third-party bots not automatically
     * started with the server. (The default is 1.6 seconds.)
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_BOTS_BOTGAMES_WAIT__SEC = "jsettlers.bots.botgames.wait_sec";

    /**
     * Property <tt>jsettlers.startrobots</tt> to start some robots when the server's threads start.
     * (The default is {@link #SOC_STARTROBOTS_DEFAULT}.)
     *<P>
     * 30% will be "smart" robots, the other 70% will be "fast" robots.
     * Remember that robots count against the {@link #PROP_JSETTLERS_CONNECTIONS max connections} limit.
     *<P>
     * Before v1.1.19 the default was 0, no robots were started by default.
     * Before v2.0.00 no bots were started unless the server constructor was
     * given a non-null {@link Properties}.
     * @since 1.1.09
     * @see #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL
     */
    public static final String PROP_JSETTLERS_STARTROBOTS = "jsettlers.startrobots";

    /**
     * Open Registration Mode boolean property {@code jsettlers.accounts.open}.
     * If this property is Y, anyone can self-register to create their own user accounts.
     * Otherwise only users in {@link #PROP_JSETTLERS_ACCOUNTS_ADMINS} can
     * create new accounts after the first account.
     *<P>
     * The default is N in version 1.1.19 and newer; previously was Y by default.
     * To require that all players have accounts in the database, see {@link #PROP_JSETTLERS_ACCOUNTS_REQUIRED}.
     *<P>
     * If this field is Y when the server is initialized, the server calls
     * {@link SOCFeatureSet#add(String) features.add}({@link SOCFeatureSet#SERVER_OPEN_REG}).
     * @since 1.1.19
     */
    public static final String PROP_JSETTLERS_ACCOUNTS_OPEN = "jsettlers.accounts.open";

    /**
     * Boolean property {@code jsettlers.accounts.required} to require that all players have user accounts.
     * If this property is Y, a jdbc database is required and all users must have an account and password
     * in the database. If a client tries to join or create a game or channel without providing a password,
     * they will be sent {@link SOCStatusMessage#SV_PW_REQUIRED}.
     * This property implies {@link SOCFeatureSet#SERVER_ACCOUNTS}.
     *<P>
     * The default is N.
     *<P>
     * If {@link #PROP_JSETTLERS_ACCOUNTS_OPEN} is used, anyone can create their own account (Open Registration).
     * Otherwise see {@link #PROP_JSETTLERS_ACCOUNTS_ADMINS} for the list of user admin accounts.
     * @since 1.1.19
     */
    public static final String PROP_JSETTLERS_ACCOUNTS_REQUIRED = "jsettlers.accounts.required";

    /**
     * Property {@code jsettlers.accounts.admins} to specify the Account Admin usernames
     * which can create accounts and run user-related commands. If this property is set,
     * it is a comma-separated list of usernames (nicknames), and a user must authenticate
     * and be on this list to create user accounts. If not set, no new accounts can be created
     * unless {@link #PROP_JSETTLERS_ACCOUNTS_OPEN} is true.
     *<P>
     * If any other user requests account creation, the server will reply with
     * {@link SOCStatusMessage#SV_ACCT_NOT_CREATED_DENIED}.
     *<P>
     * The server doesn't require or check at startup that the named accounts all already
     * exist, this is just a list of names.
     *<P>
     * This property can't be set at the same time as {@link #PROP_JSETTLERS_ACCOUNTS_OPEN},
     * they ask for opposing security policies.
     *<P>
     * Before v1.2.00, any authenticated user could create accounts.
     *
     * @see #isUserDBUserAdmin(String)
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
     * @see SOCFeatureSet#SERVER_CHANNELS
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
     * Boolean property {@code jsettlers.test.db} to test database methods,
     * then exit with code 0 if OK or 1 if any required tests failed.
     * @see SOCDBHelper#testDBHelper()
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_TEST_DB = "jsettlers.test.db";

    /**
     * From command-line flag {@code -t} or {@code --test-config},
     * boolean property {@code jsettlers.test.validate_config} to validate any server properties
     * given in {@code jsserver.properties} or on the command line, print whether there were
     * any problems, then exit with code 0 if OK or 1 if problems.
     *<P>
     * If DB connect properties are given, validation will include connecting to the database.
     * Failure to successfully connect will cause exit code 1.
     *<P>
     * Setting this property true will also set {@link #hasUtilityModeProperty()}.
     *
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_TEST_VALIDATE__CONFIG = "jsettlers.test.validate_config";

    /**
     * List and descriptions of all available JSettlers {@link Properties properties},
     * such as {@link #PROP_JSETTLERS_PORT} and {@link SOCDBHelper#PROP_JSETTLERS_DB_URL}.
     *<P>
     * Each property name is followed in the array by a brief description:
     * [0] is a property, [1] is its description, [2] is the next property, etc.
     * (This was added in 1.1.13 for {@link #printUsage(boolean)}).
     *<P>
     * When you add or update any property, please also update {@code /src/main/bin/jsserver.properties.sample}.
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
        PROP_JSETTLERS_BOTS_BOTGAMES_PARALLEL,  "Start this many robot-only games at a time (default 2)",
        PROP_JSETTLERS_BOTS_BOTGAMES_WAIT__SEC, "Wait at startup before starting robot-only games (default 1.6 seconds)",
        PROP_JSETTLERS_BOTS_BOTGAMES_SHUTDOWN,  "After running the robot-only games, shut down the server if no other games are active (if Y)",
        PROP_JSETTLERS_BOTS_COOKIE,             "Robot cookie value (default is random generated each startup)",
        PROP_JSETTLERS_BOTS_SHOWCOOKIE,         "Flag to show the robot cookie value at startup",
        PROP_JSETTLERS_BOTS_FAST__PAUSE__PERCENT, "Pause at percent of normal pause time (0 to 100) for robot-only games (default 25)",
        PROP_JSETTLERS_BOTS_PERCENT3P,          "Percent of bots which should be third-party (0 to 100) if available",
        PROP_JSETTLERS_BOTS_TIMEOUT_TURN,       "Robot turn timeout (seconds) for third-party bots",
        PROP_JSETTLERS_TEST_VALIDATE__CONFIG,   "Flag to validate server and DB config, then exit (same as -t command-line option)",
        PROP_JSETTLERS_TEST_DB,                 "Flag to test database methods, then exit",
        SOCDBHelper.PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR, "For user accounts in DB, password encryption Work Factor (see README) (9 to "
            + soc.server.database.BCrypt.GENSALT_MAX_LOG2_ROUNDS + ')',
        SOCDBHelper.PROP_JSETTLERS_DB_SAVE_GAMES,  "Flag to save all games in DB (if 1 or Y)",
        SOCDBHelper.PROP_JSETTLERS_DB_USER,     "DB username",
        SOCDBHelper.PROP_JSETTLERS_DB_PASS,     "DB password",
        SOCDBHelper.PROP_JSETTLERS_DB_URL,      "DB connection URL",
        SOCDBHelper.PROP_JSETTLERS_DB_JAR,      "DB driver jar filename",
        SOCDBHelper.PROP_JSETTLERS_DB_DRIVER,   "DB driver class name",
        SOCDBHelper.PROP_JSETTLERS_DB_SETTINGS, "If set to \"write\", save DB settings properties values to the settings table and exit",
        SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP, "If set, full path or relative path to db setup sql script; will run and exit",
        SOCDBHelper.PROP_JSETTLERS_DB_UPGRADE__SCHEMA, "Flag: If set, server will upgrade the DB schema to latest version and exit (if 1 or Y)",
    };

    /**
     * Name used when sending messages from the server.
     */
    public static final String SERVERNAME = SOCGameTextMsg.SERVERNAME;  // "Server"

    /**
     * Minimum required client version, to connect and play a game.
     * Same format as {@link soc.util.Version#versionNumber()}.
     * Currently there is no enforced minimum (0000).
     * @see #setClientVersSendGamesOrReject(Connection, int, String, String, boolean)
     */
    public static final int CLI_VERSION_MIN = 0000;

    /**
     * Minimum required client version, in "display" form, like "1.0.00".
     * Currently there is no minimum.
     * @see #setClientVersSendGamesOrReject(Connection, int, String, String, boolean)
     */
    public static final String CLI_VERSION_MIN_DISPLAY = "0.0.00";

    /**
     * If client never tells us their version, assume they are version 1.0.0 (1000).
     * @see #CLI_VERSION_TIMER_FIRE_MS
     * @see SOCServerMessageHandler#handleJOINGAME(Connection, SOCJoinGame)
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
     * If game will expire in this or fewer minutes, warn the players. Default is 15.
     * Must be at least twice the sleep-time in {@link SOCGameTimeoutChecker#run()}.
     * The game expiry time is set at game creation in
     * {@link SOCGameListAtServer#createGame(String, String, String, Map, GameHandler)}.
     *<P>
     * If you update this field, also update {@link #GAME_TIME_EXPIRE_CHECK_MINUTES}.
     *<P>
     * Before v2.0.00 this field was named {@code GAME_EXPIRE_WARN_MINUTES}. <BR>
     * Before v1.2.01 the default was 10.
     *
     * @see #checkForExpiredGames(long)
     * @see SOCGameTimeoutChecker#run()
     * @see SOCGameListAtServer#GAME_TIME_EXPIRE_MINUTES
     * @see #GAME_TIME_EXPIRE_ADDTIME_MINUTES
     */
    public static int GAME_TIME_EXPIRE_WARN_MINUTES = 15;

    /**
     * Sleep time (minutes) between checks for expired games in {@link SOCGameTimeoutChecker#run()}.
     * Default is 5 minutes. Must be at most half of {@link #GAME_TIME_EXPIRE_WARN_MINUTES}
     * so the user has time to react after seeing the warning.
     * @see SOCGameListAtServer#GAME_TIME_EXPIRE_MINUTES
     * @since 1.2.00
     */
    public static int GAME_TIME_EXPIRE_CHECK_MINUTES = 5;

    /**
     * Amount of time to add (30 minutes) when the {@code *ADDTIME*} command is used by a player.
     * @see #GAME_TIME_EXPIRE_WARN_MINUTES
     * @since 1.1.20
     */
    public static final int GAME_TIME_EXPIRE_ADDTIME_MINUTES = 30;
        // 30 minutes is hardcoded into some texts sent to players;
        // if you change it here, you will need to also search for those.

    /**
     * Force robot to end their turn after this many seconds of inactivity.
     * Keeps the game moving if bot is stuck or indecisive because of a bug.
     * Default is 8.
     *<P>
     * Can override this for third-party bots by setting
     * {@link #PROP_JSETTLERS_BOTS_TIMEOUT_TURN}.
     *<P>
     * After a bot is forced several times to end its turn, it's considered "stubborn" and
     * given a shorter timeout ({@link #ROBOT_FORCE_ENDTURN_STUBBORN_SECONDS})
     * so human players won't always have to wait so long.
     *
     * @see #checkForExpiredTurns(long)
     * @since 1.1.11
     */
    public static int ROBOT_FORCE_ENDTURN_SECONDS = 8;
        // If this value is changed, also update the jsettlers.bots.timeout.turn
        // comments in /src/main/bin/jsserver.properties.sample.

    /**
     * Force a particularly slow or buggy ("stubborn") robot to end their turn after this many seconds of inactivity.
     * Must be shorter than {@link #ROBOT_FORCE_ENDTURN_SECONDS}. Default is 4. Sets frequency of
     * {@link SOCGameTimeoutChecker}'s checks for expired turns.
     *
     * @see SOCPlayer#isStubbornRobot()
     * @see SOCPlayer#STUBBORN_ROBOT_FORCE_END_TURN_THRESHOLD
     * @since 2.0.00
     */
    public static int ROBOT_FORCE_ENDTURN_STUBBORN_SECONDS = 4;

    /**
     * Maximum permitted player name length, default 20 characters.
     * The client already truncates to 20 characters in SOCPlayerClient.getValidNickname.
     *
     * @see #createOrJoinGameIfUserOK(Connection, String, String, String, Map)
     * @see SOCGameList#GAME_NAME_MAX_LENGTH
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
     * {@link SOCFeatureSet#add(String) features.add}({@link SOCFeatureSet#SERVER_CHANNELS}).
     * If the field value is changed afterwards, that affects new clients joining the server
     * but does not clear {@code SERVER_CHANNELS} from the {@code features} list.
     *
     * @since 1.1.10
     * @see #PROP_JSETTLERS_CLI_MAXCREATECHANNELS
     */
    public static int CLIENT_MAX_CREATE_CHANNELS = 2;

    /**
     * For local practice games (pipes, not TCP), the name of the pipe.
     * Used to distinguish practice vs "real" games.
     *
     * @see StringConnection
     */
    public static String PRACTICE_STRINGPORT = "SOCPRACTICE";

    /** {@link AuthSuccessRunnable#success(Connection, int)}
     *  result flag bit: Authentication succeeded.
     *  @see #AUTH_OR_REJECT__SET_USERNAME
     *  @see #AUTH_OR_REJECT__TAKING_OVER
     *  @since 1.1.19
     */
    static final int AUTH_OR_REJECT__OK = 0x1;

    /** {@link AuthSuccessRunnable#success(Connection, int)}
     *  result flag bit: Authentication succeeded, is taking over another connection
     *  @see #AUTH_OR_REJECT__OK
     *  @since 1.1.19
     */
    static final int AUTH_OR_REJECT__TAKING_OVER = 0x2;

    /** {@link AuthSuccessRunnable#success(Connection, int)}
     *  result flag bit: Authentication succeeded, but nickname is not an exact case-sensitive match to DB username;
     *  client must be sent a status message with its exact nickname. See
     *  {@link #authOrRejectClientUser(Connection, String, String, int, boolean, boolean, AuthSuccessRunnable)}
     *  javadoc.
     *  @see #AUTH_OR_REJECT__OK
     *  @since 1.2.00
     */
    static final int AUTH_OR_REJECT__SET_USERNAME = 0x4;

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
     * Is a debug user enabled and allowed to run the commands listed in {@link #DEBUG_COMMANDS_HELP}?
     * Default is false.  Set with {@link #PROP_JSETTLERS_ALLOW_DEBUG}.
     *<P>
     * Note that all practice games are debug mode, for ease of debugging;
     * to determine this, {@link SOCServerMessageHandler#handleGAMETEXTMSG(Connection, SOCGameTextMsg)}
     * checks if the client is using {@link StringConnection} to talk to the server.
     *<P>
     * Publicly visible via {@link #isDebugUserEnabled()}.
     *
     * @see #processDebugCommand(Connection, String, String, String)
     * @since 1.1.14
     */
    private boolean allowDebugUser;

    /**
     * True if {@link #props} contains a property which is used to run the server in Utility Mode
     * instead of Server Mode.  In Utility Mode the server reads its properties, initializes its
     * database connection if any, and performs one task such as a password reset or table/index creation.
     * It won't start other threads and won't fail startup if TCP port binding fails.
     *<P>
     * For a list of Utility Mode properties, see {@link #hasUtilityModeProperty()}.
     *<P>
     * This flag is set early in {@link #initSocServer(String, String)};
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
     * Active optional server features, if any; see {@link SOCFeatureSet} constants for currently defined features.
     * Features are activated through the command line or {@link #props}.
     * @since 1.1.19
     */
    private SOCFeatureSet features = new SOCFeatureSet(false, false);

    /**
     * Game type handler, currently shared by all game instances.
     * Includes a {@link SOCGameMessageHandler}.
     * @see #srvMsgHandler
     * @since 2.0.00
     */
    private final SOCGameHandler handler = new SOCGameHandler(this);

    /**
     * Server internal flag to indicate that user accounts are active, and authentication
     * is required to create accounts, and there aren't any accounts in the database yet.
     * (Server's active features include {@link SOCFeatureSet#SERVER_ACCOUNTS} but not
     * {@link SOCFeatureSet#SERVER_OPEN_REG}.) This flag is set at startup, instead of
     * querying {@link SOCDBHelper#countUsers()} every time a client connects.
     *<P>
     * Used for signaling to {@code SOCAccountClient} that it shouldn't ask for a
     * password when connecting to create the first account, by sending the client
     * {@link SOCFeatureSet#SERVER_OPEN_REG} along with the actually active features.
     *<P>
     * The first successful account creation will clear this flag.
     *<P>
     * {@link #createAccount(String, String, String, Connection)} does call {@code countUsers()}
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
     * A list of all robot client {@link Connection}s connected to this server.
     * Includes built-in bots and any third-party bots (which are also in {@link #robots3p}).
     * @see SOCLocalRobotClient#robotClients
     */
    protected Vector<Connection> robots = new Vector<Connection>();

    /**
     * A list of third-party bot clients connected to this server, if any.
     * A subset of {@link #robots} which also includes built-in bots.
     * Third-party bot clients' {@link SOCClientData#robot3rdPartyBrainClass} != {@code null}.
     *<P>
     *<B>Locking:</B> Adding or removing from this list should synchronize on {@link #robots}
     * to keep the two lists in sync.
     * @since 2.0.00
     */
    protected Vector<Connection> robots3p = new Vector<Connection>();

    /**
     * The limited-feature clients' connections: Those with the {@link SOCClientData#hasLimitedFeatures} flag set.
     * These may be named or unnamed.
     *<BR>
     * <B>Locks:</B> All adding/removing of connections synchronizes on {@link Server#unnamedConns}.
     * @see Server#conns
     * @see Server#unnamedConns
     * @since 2.0.00
     */
    protected HashSet<Connection> limitedConns = new HashSet<Connection>();

    /**
     * Robot default parameters; copied for each newly connecting robot.
     * Changing this will not change parameters of any robots already connected.
     *
     * @see #authOrRejectClientRobot(Connection, String, String, String)
     * @see SOCServerMessageHandler#handleIMAROBOT(Connection, soc.message.SOCImARobot)
     * @see SOCDBHelper#retrieveRobotParams(String, boolean)
     * @see soc.robot.SOCRobotDM
     * @since 1.1.00
     */
    public static SOCRobotParameters ROBOT_PARAMS_DEFAULT
        = new SOCRobotParameters(120, 35, 0.13f, 1.0f, 1.0f, 3.0f, 1.0f, 1, 1);
        // Formerly a literal in handleIMAROBOT.
        // Strategy type 1 == SOCRobotDM.FAST_STRATEGY.
        // If you change values here, see authOrRejectClientRobot(..),
        // setupLocalRobots(..), SOCDBHelper.retrieveRobotParams(..),
        // and SOCPlayerClient.startPracticeGame(..)
        // for assumptions which may also need to be changed.

    /**
     * Smarter robot default parameters. (For practice games; not referenced by server)
     * Same as ROBOT_PARAMS_DEFAULT but with SMART_STRATEGY, not FAST_STRATEGY.
     *
     * @see #ROBOT_PARAMS_DEFAULT
     * @see soc.robot.SOCRobotDM
     * @since 1.1.00
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
     * @see #checkNickname(String, Connection, boolean)
     * @since 1.1.08
     */
    public static final int NICKNAME_TAKEOVER_SECONDS_SAME_PASSWORD = 15;

    /**
     * Number of seconds before a connection is considered disconnected, and
     * its nickname can be "taken over" by a new connection from the same IP.
     * Used when no password is given by the new connection.
     * @see #checkNickname(String, Connection, boolean)
     * @since 1.1.08
     */
    public static final int NICKNAME_TAKEOVER_SECONDS_SAME_IP = 30;

    /**
     * Number of seconds before a connection is considered disconnected, and
     * its nickname can be "taken over" by a new connection from a different IP.
     * Used when no password is given by the new connection.
     * @see #checkNickname(String, Connection, boolean)
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
     * Server message handler to process inbound messages from clients.
     * Messages related to actions in specific games are instead processed
     * by {@link SOCGameMessageHandler}.
     * @see #handler
     * @since 2.0.00
     */
    private final SOCServerMessageHandler srvMsgHandler = new SOCServerMessageHandler(this, gameList, channelList);

    /**
     * Holds current requests for robots to join games:<BR>
     * Key = Game name, value = Synchronized Hashtable with requested bot {@link Connection}s
     * and arbitrary related data; {@link SOCGameHandler} stores the bot's in-game seat number
     * there as an {@link Integer}.
     *<P>
     * Before v2.0.00 the per-game value was a {@code Vector<Connection>}
     * without per-bot related data.
     *
     * @see #readyGameAskRobotsJoin(SOCGame, Connection[], int)
     * @see #leaveConnection(Connection)
     * @see GameHandler#findRobotAskJoinGame(SOCGame, Object, boolean)
     */
    final Hashtable<String, Hashtable<Connection, Object>> robotJoinRequests
        = new Hashtable<String, Hashtable<Connection, Object>>();

    /**
     * table of requests for robots to leave games
     */
    final Hashtable<String, Vector<SOCReplaceRequest>> robotDismissRequests
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
     * Incremented from {@link #setClientVersSendGamesOrReject(Connection, int, String, String, boolean)};
     * currently assumes single-threaded access to this map.
     *<P>
     * Key = version number, Value = client count.
     * @since 1.1.19
     */
    protected HashMap<Integer, Integer> clientPastVersionStats;

    /**
     * Number of robot-only games not yet started (optional feature).
     * Set at startup from {@link #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL},
     * then counts down to 0 as games are played: See
     * {@link #startRobotOnlyGames(boolean, boolean)}.
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
     * @see #i18n_scenario_SC_WOND_desc
     * @see soctest.TestI18NGameoptScenStrings
     */
    final static String i18n_gameopt_PL_desc;
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
     * @see #clientHasLocalizedStrs_gameScenarios(Connection)
     * @since 2.0.00
     * @see #i18n_gameopt_PL_desc
     * @see soctest.TestI18NGameoptScenStrings
     */
    final static String i18n_scenario_SC_WOND_desc;
    static
    {
        final SOCScenario scWond = SOCScenario.getScenario(SOCScenario.K_SC_WOND);
        i18n_scenario_SC_WOND_desc = (scWond != null) ? scWond.getDesc() : "";
    }

    /**
     * Timer for delaying auth replies for consistency with {@code BCrypt} timing. Used when
     * {@code ! hadDelay} in {@link SOCDBHelper.AuthPasswordRunnable#authResult(String, boolean)} callbacks.
     * @since 1.2.00
     */
    private Timer replyAuthTimer = new Timer(true);  // use daemon thread

    /**
     * Timer to queue and soon run miscellaneous short-duration {@link Runnable} tasks
     * without tying up any single-threaded part of the server.
     * @since 2.0.00
     */
    final Timer miscTaskTimer = new Timer(true);  // use daemon thread

    /**
     * server robot pinger
     */
    SOCServerRobotPinger serverRobotPinger;

    /**
     * Game timeout and and turn timeout checker. Forces end of turn if a robot is
     * too slow to act. See its class javadoc and {@link SOCForceEndTurnThread}.
     */
    SOCGameTimeoutChecker gameTimeoutChecker;

    String databaseUserName;
    String databasePassword;

    /**
     * User admins list, from {@link #PROP_JSETTLERS_ACCOUNTS_ADMINS}, or {@code null} if not specified.
     * Unless {@link SOCFeatureSet#SERVER_OPEN_REG} is active, only usernames on this list
     * can create user accounts in {@link #createAccount(String, String, String, Connection)}.
     *<P>
     * If DB schema &gt;= {@link SOCDBHelper#SCHEMA_VERSION_1200}, this list is
     * made lowercase for case-insensitive checks in {@link #isUserDBUserAdmin(String)}.
     *<P>
     * Before v1.2.00, if this was {@code null} any authenticated user could create other accounts.
     *
     * @since 1.1.19
     */
    private Set<String> databaseUserAdmins;

    /**
     * Create a Settlers of Catan server listening on TCP port {@code p}.
     * Most server threads are started here; you must start its main thread yourself.
     * Optionally connect to a database for user info and game stats.
     *<P>
     * The default number of bots will be started here ({@link #SOC_STARTROBOTS_DEFAULT})
     * since this constructor has no {@link Properties} to override that.
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
     * @throws SQLException   If db setup script fails,
     *       or if required tests failed in {@link SOCDBHelper#testDBHelper()}
     * @throws IllegalStateException  If {@link Version#versionNumber()} returns 0 (packaging error)
     */
    public SOCServer(int p, int mc, String databaseUserName, String databasePassword)
        throws SocketException, EOFException, SQLException, IllegalStateException
    {
        super(p, new SOCMessageDispatcher(), null);

        maxConnections = mc;
        initSocServer(databaseUserName, databasePassword);
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
     * for details see {@link #initSocServer(String, String)}.
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
     *       and any other desired properties.
     *       <P>
     *       Property names are held in PROP_* and SOCDBHelper.PROP_* constants; see {@link #PROPS_LIST}.
     *       <P>
     *       If {@code props} contains game option default values
     *       (see below) with non-uppercase gameopt names, cannot be read-only: Startup will
     *       replace keys such as {@code "jsettlers.gameopt.vp"} with their canonical
     *       uppercase equivalent: {@code "jsettlers.gameopt.VP"}
     *       <P>
     *       If {@code props} is null, the properties will be created empty.
     *       <P>
     *       Unless {@code props} contains {@link #PROP_JSETTLERS_STARTROBOTS} == 0,
     *       the default number {@link #SOC_STARTROBOTS_DEFAULT} of bots will be started.
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
     * @throws SQLException   If db setup script fails, or need db but can't connect,
     *       or if required tests failed in {@link SOCDBHelper#testDBHelper()},
     *       or if other problems with DB-related contents of {@code props}
     *       (exception's {@link Throwable#getCause()} will be an {@link IllegalArgumentException} or
     *       {@link DBSettingMismatchException}); see {@link SOCDBHelper#initialize(String, String, Properties)} javadoc.
     *       This constructor prints the SQLException details to {@link System#err},
     *       caller doesn't need to extract the cause and print those same details.
     * @throws IllegalArgumentException  If {@code props} contains game options ({@code jsettlers.gameopt.*})
     *       with bad syntax. See {@link #PROP_JSETTLERS_GAMEOPT_PREFIX} for expected syntax.
     *       See {@link #parseCmdline_DashedArgs(String[])} for how game option properties are checked.
     *       {@link Throwable#getMessage()} will have problem details.
     * @throws IllegalStateException  If {@link Version#versionNumber()} returns 0 (packaging error)
     * @see #PROPS_LIST
     */
    public SOCServer(final int p, Properties props)
        throws SocketException, EOFException, SQLException, IllegalArgumentException, IllegalStateException
    {
        super(p, new SOCMessageDispatcher(), props);
        props = this.props;  // if was null, use empty props created by super constructor

        maxConnections = getConfigIntProperty(PROP_JSETTLERS_CONNECTIONS, SOC_MAXCONN_DEFAULT);
        allowDebugUser = getConfigBoolProperty(PROP_JSETTLERS_ALLOW_DEBUG, false);
        CLIENT_MAX_CREATE_GAMES = getConfigIntProperty(PROP_JSETTLERS_CLI_MAXCREATEGAMES, CLIENT_MAX_CREATE_GAMES);
        CLIENT_MAX_CREATE_CHANNELS = getConfigIntProperty(PROP_JSETTLERS_CLI_MAXCREATECHANNELS, CLIENT_MAX_CREATE_CHANNELS);

        String dbuser = props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_USER, "socuser");
        String dbpass = props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_PASS, "socpass");

        initSocServer(dbuser, dbpass);
    }

    /**
     * Create a Settlers of Catan server listening on local stringport {@code s}.
     * Most server threads are started here; you must start its main thread yourself.
     * Optionally connect to a database for user info and game stats.
     *<P>
     * The default number of bots will be started here ({@link #SOC_STARTROBOTS_DEFAULT})
     * since this constructor has no {@link Properties} to override that.
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
     * @throws SQLException   If db setup script fails,
     *       or if required tests failed in {@link SOCDBHelper#testDBHelper()}
     * @throws IllegalStateException  If {@link Version#versionNumber()} returns 0 (packaging error)
     * @since 1.1.00
     */
    public SOCServer(String s, int mc, String databaseUserName, String databasePassword)
        throws SocketException, EOFException, SQLException, IllegalStateException
    {
        super(s, new SOCMessageDispatcher(), null);

        maxConnections = mc;
        initSocServer(databaseUserName, databasePassword);
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
     * If problems running a {@link SOCDBHelper#PROP_JSETTLERS_DB_SCRIPT_SETUP db setup script}
     * or {@link SOCDBHelper#PROP_JSETTLERS_DB_UPGRADE__SCHEMA schema upgrade},
     * this method will throw {@link SQLException}.
     *<P>
     * If we can't connect to a database, but it looks like we need one (because
     * {@link SOCDBHelper#PROP_JSETTLERS_DB_URL}, {@link SOCDBHelper#PROP_JSETTLERS_DB_DRIVER}
     * or {@link SOCDBHelper#PROP_JSETTLERS_DB_JAR} is specified in {@code props}),
     * or there are other problems with DB-related contents of {@code props}
     * (see {@link SOCDBHelper#initialize(String, String, Properties)}; exception's {@link Throwable#getCause()}
     * will be an {@link IllegalArgumentException} or {@link DBSettingMismatchException}) this method will
     * print details to {@link System#err} and throw {@link SQLException}.
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
     * @throws SocketException  If a network setup problem occurs
     * @throws EOFException   If db setup script ran successfully and server should exit now;
     *       thrown in Utility Mode ({@link #hasUtilityModeProp}).
     * @throws SQLException   If db setup script fails, or need db but can't connect
     * @throws IllegalArgumentException  If {@code props} contains game options ({@code jsettlers.gameopt.*})
     *       with bad syntax. See {@link #PROP_JSETTLERS_GAMEOPT_PREFIX} for expected syntax.
     *       See {@link #parseCmdline_DashedArgs(String[])} for how game option properties are checked.
     *       Also thrown if {@link SOCDBHelper#PROP_JSETTLERS_DB_UPGRADE__SCHEMA} flag
     *       is set, but {@link SOCDBHelper#isSchemaLatestVersion()}. {@link Throwable#getMessage()} will have
     *       problem details for any {@code IllegalArgumentException} thrown here.
     * @throws IllegalStateException  If {@link Version#versionNumber()} returns 0 (packaging error)
     * @since 1.1.00
     */
    private void initSocServer(String databaseUserName, String databasePassword)
        throws SocketException, EOFException, SQLException, IllegalArgumentException, IllegalStateException
    {
        Version.printVersionText(System.err, "Java Settlers Server ");
        if (Version.versionNumber() == 0)
        {
            throw new IllegalStateException("Packaging error: Cannot determine JSettlers version");
        }

        /**
         * If true, connect to DB (like validate_config_mode) but start no threads.
         * Will run the requested tests and exit.
         */
        final boolean test_mode_with_db = getConfigBoolProperty(PROP_JSETTLERS_TEST_DB, false);

        final boolean validate_config_mode = getConfigBoolProperty(PROP_JSETTLERS_TEST_VALIDATE__CONFIG, false);
        final boolean wants_upg_schema = getConfigBoolProperty(SOCDBHelper.PROP_JSETTLERS_DB_UPGRADE__SCHEMA, false);
        boolean db_test_bcrypt_mode = false;

        final String val = props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR);
        if (val != null)
        {
            db_test_bcrypt_mode = (val.equalsIgnoreCase("test"));
            if (db_test_bcrypt_mode)
                // make sure DBH.initialize won't try to parse "test" as an integer
                props.remove(SOCDBHelper.PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR);
        }

        // Set this flag as early as possible
        hasUtilityModeProp = validate_config_mode || test_mode_with_db || wants_upg_schema || db_test_bcrypt_mode ||
            (null != props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP))
            || props.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_SETTINGS)
            || (null != props.getProperty(SOCDBHelper.PROP_IMPL_JSETTLERS_PW_RESET));

        if (test_mode_with_db)
            System.err.println("* DB Test Mode: Will run tests and exit.");
        if (validate_config_mode)
            System.err.println("* Config Validation Mode: Checking configuration and exiting.");

        /* Check for problems during super setup (such as port already in use).
         * Ignore net errors if we're running a DB setup script and then exiting.
         */
        if ((error != null) && ! hasUtilityModeProp)
        {
            final String errMsg = "* Exiting due to network setup problem: " + error.toString();
            throw new SocketException(errMsg);
        }

        // Add any default properties if not specified.

        if (! props.containsKey(PROP_JSETTLERS_STARTROBOTS))
            props.setProperty(PROP_JSETTLERS_STARTROBOTS, Integer.toString(SOC_STARTROBOTS_DEFAULT));

        // Set game option defaults from any jsettlers.gameopt.* properties found.
        // If problems found, throws IllegalArgumentException with details.
        // Does not apply scenario's game options, if any.
        // Ignores unknown scenario ("SC"), see init_checkScenarioOpts for that.
        init_propsSetGameopts(props);

        int v = getConfigIntProperty(PROP_JSETTLERS_BOTS_FAST__PAUSE__PERCENT, -1);
        if (v != -1)
        {
            if ((v >= 0) && (v <= 100))
                SOCRobotBrain.BOTS_ONLY_FAST_PAUSE_FACTOR = .01f * v;
            else
                throw new IllegalArgumentException
                    ("Error: Property out of range (0 to 100): " + PROP_JSETTLERS_BOTS_FAST__PAUSE__PERCENT);
        }

        ((SOCMessageDispatcher) inboundMsgDispatcher).setServer(this, srvMsgHandler, gameList);

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

        final boolean accountsRequired = getConfigBoolProperty(PROP_JSETTLERS_ACCOUNTS_REQUIRED, false);

        /**
         * Try to connect to the DB, if any.
         * Running SOCDBHelper.initialize(..) will handle some Utility Mode properties
         * like PROP_JSETTLERS_DB_SETTINGS if present.
         */
        boolean db_err_printed = false;
        try
        {
            SOCDBHelper.initialize(databaseUserName, databasePassword, props);
            features.add(SOCFeatureSet.SERVER_ACCOUNTS);
            System.err.println("User database initialized.");

            if (props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP) != null)
            {
                // the sql script was ran by initialize

                final String msg = "DB setup script successful";
                utilityModeMessage = msg;
                throw new EOFException(msg);
            }

            // set some DB-related SOCServer fields: acctsNotOpenRegButNoUsers, databaseUserAdmins
            initSocServer_dbParamFields(accountsRequired, wants_upg_schema);

            // check schema version, upgrade if requested:
            if (! SOCDBHelper.isSchemaLatestVersion())
            {
                if (wants_upg_schema)
                {
                    try
                    {
                        SOCDBHelper.upgradeSchema(databaseUserAdmins);

                        String msg = "DB schema upgrade was successful";
                        if (SOCDBHelper.doesSchemaUpgradeNeedBGTasks())
                            msg += "; some upgrade tasks will complete in the background during normal server operation";
                        utilityModeMessage = msg;

                        throw new EOFException(msg);
                    }
                    catch (EOFException e)
                    {
                        throw e;
                    }
                    catch (Exception e)
                    {
                        db_err_printed = true;
                        if (e instanceof MissingResourceException)
                            System.err.println("* To begin schema upgrade, please fix and rerun: " + e.getMessage());
                        else
                            System.err.println(e);

                        if (e instanceof SQLException)
                        {
                            throw (SQLException) e;
                        } else {
                            SQLException sqle = new SQLException("Error during DB schema upgrade");
                            sqle.initCause(e);
                            throw sqle;
                        }
                    }
                } else {
                    System.err.println("\n* Database schema upgrade is recommended: To upgrade, use -D"
                        + SOCDBHelper.PROP_JSETTLERS_DB_UPGRADE__SCHEMA + "=Y command line flag.\n");
                }
            }
            else if (wants_upg_schema)
            {
                db_err_printed = true;
                final String errmsg = "* Cannot upgrade database schema: Already at latest version";
                System.err.println(errmsg);
                throw new IllegalArgumentException(errmsg);
            }

            // reminder: if props.getProperty(SOCDBHelper.PROP_IMPL_JSETTLERS_PW_RESET),
            // caller will need to prompt for and change the password
        }
        catch (SQLException sqle)  // just a warning at this point; other code checks if db failed but is required
        {
            if (wants_upg_schema && db_err_printed)
            {
                // the schema upgrade failed to complete; upgradeSchema() printed the exception.
                // don't continue server startup with just a warning

                throw sqle;
            }

            System.err.println("Warning: No user database available: " + sqle.getMessage());
            Throwable cause = sqle.getCause();

            while ((cause != null) && ! (cause instanceof ClassNotFoundException))
            {
                System.err.println("\t" + cause);
                cause = cause.getCause();
            }

            if (wants_upg_schema || (props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP) != null))
            {
                // the sql script ran in initialize failed to complete;
                // now that we've printed the exception, don't continue server startup with just a warning

                throw sqle;
            }

            String propReqDB = null;
            if (accountsRequired)
                propReqDB = PROP_JSETTLERS_ACCOUNTS_REQUIRED;
            else if (props.containsKey(PROP_JSETTLERS_ACCOUNTS_ADMINS))
                propReqDB = PROP_JSETTLERS_ACCOUNTS_ADMINS;

            if (propReqDB != null)
            {
                final String errMsg = "* Property " + propReqDB + " requires a database.";
                System.err.println(errMsg);
                System.err.println("\n* Exiting because current startup properties specify a database.");
                throw new SQLException(errMsg);
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
        catch (EOFException eox)  // successfully ran script or schema upgrade, signal to exit
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
        catch (IllegalArgumentException iax)
        {
            System.err.println("\n* Error in specified database properties: " + iax.getMessage());
            SQLException sqle = new SQLException("Error with DB props");
            sqle.initCause(iax);
            throw sqle;
        }
        catch (DBSettingMismatchException dx)
        {
            // initialize(..) already printed details to System.err
            System.err.println("\n* Mismatch between database settings and specified properties");
            SQLException sqle = new SQLException("DB settings mismatch");
            sqle.initCause(dx);
            throw sqle;
        }

        // No errors; continue normal startup.

        if (db_test_bcrypt_mode)
            SOCDBHelper.testBCryptSpeed();

        if (hasUtilityModeProp && ! (test_mode_with_db || validate_config_mode))
        {
            return;  // <--- don't continue startup if Utility Mode ---
        }

        if (SOCDBHelper.isInitialized())
        {
            if (accountsRequired)
                System.err.println("User database accounts are required for all players.");

            // Note: This hook is not triggered under eclipse debugging.
            //    https://bugs.eclipse.org/bugs/show_bug.cgi?id=38016  "WONTFIX/README" since 2007-07-18
            try
            {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        System.err.println("\n--\n-- shutdown; disconnecting from db --\n--\n");
                        System.err.flush();
                        try
                        {
                            // Before disconnect, do a final check for unexpected DB settings changes
                            try
                            {
                                SOCDBHelper.checkSettings(true, false);
                            } catch (Exception x) {}

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

        startTime = System.currentTimeMillis();
        numberOfGamesStarted = 0;
        numberOfGamesFinished = 0;
        numberOfUsers = 0;
        clientPastVersionStats = new HashMap<Integer, Integer>();
        numRobotOnlyGamesRemaining = getConfigIntProperty(PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL, 0);
        if (numRobotOnlyGamesRemaining > 0)
        {
                final int n = SOCGame.MAXPLAYERS_STANDARD;
                if (n > getConfigIntProperty(PROP_JSETTLERS_STARTROBOTS, 0))
                {
                    final String errmsg =
                        ("*** To start robot-only games, server needs at least " + n + " robots started.");
                    System.err.println(errmsg);
                    throw new IllegalArgumentException(errmsg);
                }
        }

        if (CLIENT_MAX_CREATE_CHANNELS != 0)
            features.add(SOCFeatureSet.SERVER_CHANNELS);

        /**
         * Start various threads.
         */
        if (! (test_mode_with_db || validate_config_mode))
        {
            serverRobotPinger = new SOCServerRobotPinger(this, robots);
            serverRobotPinger.start();
            gameTimeoutChecker = new SOCGameTimeoutChecker(this);
            gameTimeoutChecker.start();
        }

        this.databaseUserName = databaseUserName;
        this.databasePassword = databasePassword;

        /**
         * Print game options if we've set them on commandline, or if
         * any option defaults require a minimum client version.
         */
        if (hasSetGameOptions || validate_config_mode
            || (SOCVersionedItem.itemsMinimumVersion(SOCGameOption.getAllKnownOptions()) > -1))
        {
            Thread.yield();  // wait for other output to appear first
            try { Thread.sleep(200); } catch (InterruptedException ie) {}

            printGameOptions();
        }

        if (getConfigBoolProperty(PROP_JSETTLERS_BOTS_SHOWCOOKIE, false))
            System.err.println("Robot cookie: " + robotCookie);

        if (validate_config_mode)
        {
            // Print configured known properties (ignore if not in PROPS_LIST);
            // this also gives them in the same order as PROPS_LIST,
            // which is the same order --help prints them out.
            System.err.println();
            System.err.println("-- Configured server properties: --");
            for (int i = 0; i < PROPS_LIST.length; i += 2)
            {
                final String pkey = PROPS_LIST[i];
                if ((! pkey.equals(PROP_JSETTLERS_TEST_VALIDATE__CONFIG)) && props.containsKey(pkey))
                    System.err.format("%-40s %s\n", pkey, props.getProperty(pkey));
            }

            System.err.println();
            System.err.println("* Config Validation Mode: No problems found.");
        }

        if (test_mode_with_db)
        {
            SOCDBHelper.testDBHelper();  // failures/errors throw SQLException for our caller to catch
        }

        if (! (test_mode_with_db || validate_config_mode))
        {
            System.err.print("The server is ready.");
            if (port > 0)
                System.err.print(" Listening on port " + port);
            System.err.println();

            if (SOCDBHelper.isInitialized() && SOCDBHelper.doesSchemaUpgradeNeedBGTasks())
                SOCDBHelper.startSchemaUpgradeBGTasks();  // includes 5-second sleep before conversions begin
        }

        System.err.println();
    }

    /**
     * Set some DB-related SOCServer fields and features:
     * {@link #databaseUserAdmins} from {@link #PROP_JSETTLERS_ACCOUNTS_ADMINS},
     * {@link #features}({@link SOCFeatureSet#SERVER_OPEN_REG}) and {@link #acctsNotOpenRegButNoUsers}
     * from {@link #PROP_JSETTLERS_ACCOUNTS_OPEN}.
     *<P>
     * Prints some status messages and any problems to {@link System#err}.
     *<P>
     * Must not call this method until after {@link SOCDBHelper#initialize(String, String, Properties)}.
     *
     * @param accountsRequired  Are accounts required? Caller should check {@link #PROP_JSETTLERS_ACCOUNTS_REQUIRED}.
     * @param wantsUpgSchema  If true, server is preparing to try to upgrade the schema and exit.
     *     Certain hint messages here won't be printed, because the server is exiting afterwards.
     * @throws IllegalArgumentException if {@link #PROP_JSETTLERS_ACCOUNTS_ADMINS} is inconsistent or empty
     * @throws SQLException  if unexpected problem with DB when calling {@link SOCDBHelper#countUsers()}
     *     for {@link #acctsNotOpenRegButNoUsers}
     * @since 1.2.00
     */
    private void initSocServer_dbParamFields(final boolean accountsRequired, final boolean wantsUpgSchema)
        throws IllegalArgumentException, SQLException
    {
        // open reg for user accounts?  if not, see if we have any yet
        if (getConfigBoolProperty(PROP_JSETTLERS_ACCOUNTS_OPEN, false))
        {
            features.add(SOCFeatureSet.SERVER_OPEN_REG);
            if (! hasUtilityModeProp)
                System.err.println("User database Open Registration is active, anyone can create accounts.");
        } else {
            if (SOCDBHelper.countUsers() == 0)
                acctsNotOpenRegButNoUsers = true;
        }

        if (props.containsKey(PROP_JSETTLERS_ACCOUNTS_ADMINS))
        {
            String errmsg = null;

            final String userAdmins = props.getProperty(PROP_JSETTLERS_ACCOUNTS_ADMINS);
            if (userAdmins.length() == 0)
            {
                errmsg = "* Property " + PROP_JSETTLERS_ACCOUNTS_ADMINS + " cannot be an empty string.";
            } else if (features.isActive(SOCFeatureSet.SERVER_OPEN_REG)) {
                errmsg = "* Cannot use Open Registration with User Account Admins List.";
            } else {
                final boolean downcase = (SOCDBHelper.getSchemaVersion() >= SOCDBHelper.SCHEMA_VERSION_1200);
                databaseUserAdmins = new HashSet<String>();

                for (String adm : userAdmins.split(SOCMessage.sep2))  // split on "," - sep2 will never be in a username
                {
                    String na = adm.trim();
                    if (na.length() > 0)
                    {
                        if (downcase)
                            na = na.toLowerCase(Locale.US);
                        databaseUserAdmins.add(na);
                    }
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
            if (acctsNotOpenRegButNoUsers && ! wantsUpgSchema)
                System.err.println
                    ("** User database is currently empty: Run SOCAccountClient to create the user admin account(s) named above.");
        }
        else if (acctsNotOpenRegButNoUsers && ! wantsUpgSchema)
        {
            System.err.println
                ("** To create users, you must list admin names in property " + PROP_JSETTLERS_ACCOUNTS_ADMINS + ".");
        }

    }

    /**
     * Callback to take care of things when server comes up, after the server socket
     * is bound and listening, in the server's main thread.
     *<P>
     * Unless {@link #PROP_JSETTLERS_STARTROBOTS} is 0, starts those {@link SOCRobotClient}s now
     * by calling {@link #setupLocalRobots(int, int)}. If {@link #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL}
     * is specified, waits briefly and then calls {@link #startRobotOnlyGames(boolean, boolean)}.
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
        if (props.containsKey(PROP_JSETTLERS_STARTROBOTS))
        {
            try
            {
                // 0 bots is OK with the logic here
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
                    final int incr = 6 - hcount, newMaxC = maxConnections + incr;
                    maxConnections = newMaxC;

                    new Thread() {
                        @Override
                        public void run()
                        {
                            try {
                                Thread.sleep(1600);  // wait for bot-connect messages to print
                            } catch (InterruptedException e) {}
                            System.err.println("** Warning: Only " + hcount
                                + " player connections would be available because of the started robots.");
                            System.err.println("   Using " + maxConnections + " for max connection count (+" + incr + ").");
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
                        final int waitSec = getConfigIntProperty(PROP_JSETTLERS_BOTS_BOTGAMES_WAIT__SEC, 0);
                        final int waitmSec = (waitSec > 0) ? (1000 * waitSec) : 1600;
                        if (waitSec > 2)
                            System.err.println("\nWaiting " + waitSec + " seconds before starting robot-only games.\n");

                        new Thread() {
                            @Override
                            public void run()
                            {
                                try {
                                    Thread.sleep(waitmSec);  // wait for bots to connect
                                } catch (InterruptedException e) {}

                                if (waitSec > 2)
                                    System.err.println
                                        ("\nStarting robot-only games now, after waiting " + waitSec + " seconds.\n");

                                startRobotOnlyGames(false, false);
                            }
                        }.start();
                    }
                }
            }
            catch (NumberFormatException e)
            {
                System.err.println
                    ("** Not starting robots: Bad number format, ignoring property " + PROP_JSETTLERS_STARTROBOTS);
            }
        }
    }

    /**
     * Get the number of robots currently on this server.
     * @return The number of robot clients currently connected to this server
     * @since 2.0.00
     */
    public final int getRobotCount() { return robots.size(); }

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
    public void connectToChannel(Connection c, String ch)
    {
        if (c == null)
            return;

        if (channelList.isChannel(ch))
        {
            if (! channelList.isMember(c, ch))
            {
                c.put(SOCChannelMembers.toCmd(ch, channelList.getMembers(ch)));
                if (D.ebugOn)
                    D.ebugPrintln("*** " + c.getData() + " joined the channel " + ch + " at "
                        + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
                channelList.addMember(c, ch);
            }
        }
    }

    /**
     * Connection {@code c} leaves the channel {@code ch}.
     * Send {@link SOCLeaveChannel} message to remaining members of {@code ch}.
     * If the channel becomes empty after removing {@code c}, this method can destroy it.
     *<P>
     * <B>Note:</B> Caller must send {@link SOCDeleteChannel} message, this method does not do so.
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
        (final Connection c, final String ch, final boolean destroyIfEmpty, final boolean channelListLock)
    {
        if (c == null)
            return false;

        final String mName = c.getData();
        D.ebugPrintln("leaveChannel: " + mName + " " + ch + " " + channelListLock);

        if (channelList.isMember(c, ch))
        {
            channelList.removeMember(c, ch);

            SOCLeaveChannel leaveMessage = new SOCLeaveChannel(mName, "-", ch);
            messageToChannelWithMon(ch, leaveMessage);
            if (D.ebugOn)
                D.ebugPrintln("*** " + mName + " left the channel " + ch + " at "
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
     * <B>Note:</B> Caller must send {@link SOCDeleteChannel} message, this method does not do so.
     *<P>
     * <B>Locks:</B> Must have {@link #channelList}{@link SOCChannelList#takeMonitor() .takeMonitor()}
     * before calling this method.
     *
     * @param ch  Name of the channel to destroy
     * @see #leaveChannel(Connection, String, boolean, boolean)
     * @since 1.1.20
     */
    protected final void destroyChannel(final String ch)
    {
        channelList.deleteChannel(ch);

        // Reduce the owner's channels-active count
        Connection oConn = conns.get(channelList.getOwner(ch));
        if (oConn != null)
            ((SOCClientData) oConn.getAppData()).deletedChannel();
    }

    /**
     * Adds a connection to a game, unless they're already a member.
     * If the game doesn't yet exist, creates it and announces the new game to all clients
     * by calling {@link #createGameAndBroadcast(Connection, String, Map, int, boolean, boolean)}.
     * After this method returns, caller must call {@link #joinGame(SOCGame, Connection, boolean, boolean)}
     * to send game state to the player/observer.
     *<P>
     * If this method creates a new game: After it returns, other human players may join until
     * someone clicks "Start Game". At that point, server will look for robots to fill empty seats.
     *
     * @param c    the Connection to be added to the game; its name, version, and locale should already be set.
     * @param gaName  the name of the game.  Not validated or trimmed, see
     *             {@link #createOrJoinGameIfUserOK(Connection, String, String, String, Map)} for that.
     * @param gaOpts  if creating a game with options, its {@link SOCGameOption}s; otherwise null.
     *                Must already be validated, by calling
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
     * @throws MissingResourceException if client has {@link SOCClientData#hasLimitedFeats} and
     *           <tt>! {@link SOCGame#canClientJoin(SOCFeatureSet)}</tt>.
     *           The missing feature(s) are in the {@link MissingResourceException#getKey()} field,
     *           in the format returned by {@link SOCGame#checkClientFeatures(SOCFeatureSet, boolean)}.
     *           (this exception was added in 2.0.00)
     * @throws IllegalArgumentException if client's version is too low to join for any
     *           other reason. (this exception was added in 1.1.06)
     * @see #joinGame(SOCGame, Connection, boolean, boolean)
     * @see SOCServerMessageHandler#handleSTARTGAME(Connection, SOCStartGame)
     * @see SOCServerMessageHandler#handleJOINGAME(Connection, SOCJoinGame)
     */
    public boolean connectToGame(Connection c, final String gaName, Map<String, SOCGameOption> gaOpts)
        throws SOCGameOptionVersionException, MissingResourceException, IllegalArgumentException
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
            String cliMissingFeats = null;  // if non-null, list of optional features not in client but needed by game

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
                    if (ga.getClientVersionMinRequired() > cliVers)
                    {
                        cliVersOld = true;
                    } else {
                        SOCClientData scd = (SOCClientData) c.getAppData();
                        if (scd.hasLimitedFeats)
                        {
                            cliMissingFeats = ga.checkClientFeatures(scd.feats, false);
                            if (cliMissingFeats != null)
                                cliVersOld = true;
                        }
                    }

                    if (! cliVersOld)
                    {
                        gameList.addMember(c, gaName);
                        result = true;
                    }
                }
            }
            catch (Exception e)
            {
                D.ebugPrintStackTrace(e, "Exception in connectToGame (isMember)");
            }

            gameList.releaseMonitorForGame(gaName);
            if (cliMissingFeats != null)
                throw new MissingResourceException("Client missing a feature", "unused", cliMissingFeats);
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
                if ((gVers > cliVers) && (gVers < Integer.MAX_VALUE))
                {
                    // Which requested option(s) are too new for client?
                    // (Ignored if gVers was MAX_VALUE, which is used
                    //  only by test-gameopt DEBUGNOJOIN.)
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
     * Called from {@link #connectToGame(Connection, String, Map)}.
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
     *             {@link #createOrJoinGameIfUserOK(Connection, String, String, String, Map)} for that.
     * @param gaOpts  if creating a game with options, its {@link SOCGameOption}s; otherwise null.
     *                Must already be validated, by calling
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
        (Connection c, final String gaName, Map<String, SOCGameOption> gaOpts,
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
                (gaName, (c != null) ? c.getData() : null, (scd != null) ? scd.localeStr : null,
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

            broadcastNewGame(newGame, gaName, gaOpts, gVers);
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
     * Announce a newly created game to all clients; called from
     * {@link #createGameAndBroadcast(Connection, String, Map, int, boolean, boolean)}.
     * If some clients can't join, based on their version or limited {@link SOCClientData#feats},
     * announce to those clients with the "can't join" prefix flag.
     *
     * @param newGame  Newly created game
     * @param gaName  New game's name
     * @param gaOpts  New game's options if any, or null
     * @param gVers   New game's minimum version
     * @since 2.0.00
     */
    private void broadcastNewGame
        (final SOCGame newGame, final String gaName, Map<String, SOCGameOption> gaOpts, final int gVers)
    {
        // check required client version before we broadcast
        final int cversMin = getMinConnectedCliVersion();

        if ((gVers <= cversMin) && (gaOpts == null))
        {
            // All clients can join it, and no game options: use simplest message
            broadcast(SOCNewGame.toCmd(gaName));

        } else {
            // Send messages, based on clients' versions/features
            // and whether there are game options.

            // Client version variables:
            // cversMin: minimum version connected to server
            // VERSION_FOR_NEWGAMEWITHOPTIONS: minimum to understand game options

            // Game version variables:
            // gVers: minimum to play the game
            // gVersMinGameOptsNoChange: minimum to understand these game options
            //           without backwards-compatibility changes to their values

            final int gVersMinGameOptsNoChange;
            if (cversMin < Version.versionNumber())
                gVersMinGameOptsNoChange = SOCVersionedItem.itemsMinimumVersion(gaOpts, true);
            else
                gVersMinGameOptsNoChange = -1;  // all clients are our current version

            // Check whether any clients have only limited features and can't join:

            Connection cliLimited = null;  // the first limited connection found, if any
            String cannotJoinMsg = null;  // if needed, lazy init in loop body

            if (! limitedConns.isEmpty())
            {
                final SOCFeatureSet gameFeats = newGame.getClientFeaturesRequired();
                if (gameFeats != null)
                {
                    synchronized (unnamedConns)
                    {
                        for (final Connection lc : limitedConns)
                        {
                            final SOCClientData scd = (SOCClientData) lc.getAppData();

                            if (scd.isRobot)
                                continue;  // bots don't care about new-game announcements

                            if ((gVers <= lc.getVersion()) && ! newGame.canClientJoin(scd.feats))
                            {
                                cliLimited = lc;
                                break;
                            }
                        }
                    }

                    if (cliLimited != null)
                    {
                        cannotJoinMsg = SOCNewGame.toCmd(SOCGames.MARKER_THIS_GAME_UNJOINABLE + gaName);  // was null
                        cliLimited.put(cannotJoinMsg);
                    }
                }
            }

            if ((cversMin >= gVersMinGameOptsNoChange)
                && (cversMin >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
                && (cliLimited == null))
            {
                // All cli can understand msg with version/options included
                broadcast
                    (SOCNewGameWithOptions.toCmd(gaName, gaOpts, gVers, -2));
            } else {
                // Only some can understand msg with version/options included;
                // send at most 1 message to each connected client, split by client version.
                // If no game options, send simple NEWGAME message type to all clients.

                final HashMap<Integer, String> msgCacheForVersion = new HashMap<Integer, String>();
                    // key = client version. Special keys:
                    // 1 if older than VERSION_FOR_NEWGAMEWITHOPTIONS;
                    // -1 if older than that and can't join

                synchronized (unnamedConns)
                {
                    broadcastNewGame_toConns
                        (newGame, gaOpts, gVers, gVersMinGameOptsNoChange,
                         conns.values(), cliLimited, msgCacheForVersion, cannotJoinMsg);
                    broadcastNewGame_toConns
                        (newGame, gaOpts, gVers, gVersMinGameOptsNoChange,
                         unnamedConns, cliLimited, msgCacheForVersion, cannotJoinMsg);
                }
            }
        }
    }

    /**
     * Utility method to loop through {@link #conns} or {@link #unnamedConns} during
     * {@link #broadcastNewGame(SOCGame, String, Map, int)}.
     *<P>
     * For parameter meanings, see source of {@code broadcastNewGame(..)}.
     * @since 2.0.00
     */
    private void broadcastNewGame_toConns
        (final SOCGame newGame, final Map<String, SOCGameOption> gaOpts, final int gVers,
         final int gVersMinGameOptsNoChange, Collection<Connection> connSet, Connection cliLimited,
         final HashMap<Integer, String> msgCacheForVersion, String cannotJoinMsg)
    {
        final String gaName = newGame.getName();

        for (Connection c : connSet)
        {
            if (cliLimited != null)
            {
                if (c == cliLimited)
                    continue;  // already sent

                if (limitedConns.contains(c)
                    && ! newGame.canClientJoin(((SOCClientData) c.getAppData()).feats))
                {
                    if (cannotJoinMsg == null)
                        cannotJoinMsg = SOCNewGame.toCmd(SOCGames.MARKER_THIS_GAME_UNJOINABLE + gaName);
                        // cannotJoinMsg will be used during this loop, but discarded between first and
                        // second call to this method; second call will create it again, which is OK
                    c.put(cannotJoinMsg);

                    continue;  // can't join
                }
            }

            int cvers = c.getVersion();
            if (cvers < gVers)
                cvers = -1;
            else if ((gaOpts == null) || (cvers < SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS))
                cvers = 1;
            final Integer cversKey = Integer.valueOf(cvers);

            String cacheMsg = msgCacheForVersion.get(cversKey);
            if (cacheMsg != null)
            {
                c.put(cacheMsg);
                continue;
            }

            // Based on client's version, determine the message to send
            if (cvers == -1)
            {
                // Older clients who can't join: announce game with cant-join prefix
                cacheMsg = new SOCNewGame(SOCGames.MARKER_THIS_GAME_UNJOINABLE + gaName).toCmd();
            }
            else if (cvers == 1)
            {
                // No game options, or older client who can join: announce game without its options/version
                cacheMsg = new SOCNewGame(gaName).toCmd();
            }
            else
            {
                // Client's version is new enough for game options:
                // Some clients' versions are too old to understand these game
                // option values without change; send them an altered set for
                // compatibility with those clients.

                if (cvers >= gVersMinGameOptsNoChange)
                    cacheMsg = new SOCNewGameWithOptions(gaName, gaOpts, gVers, -2).toCmd();
                else
                    // needs value changes
                    cacheMsg = new SOCNewGameWithOptions(gaName, gaOpts, gVers, cvers).toCmd();
            }

            msgCacheForVersion.put(cversKey, cacheMsg);
            c.put(cacheMsg);
        }
    }

    /**
     * the connection c leaves the game gm.  Clean up; if needed, force the current player's turn to end.
     *<P>
     * If the game becomes empty after removing {@code c}, this method can destroy it if all these
     * conditions are true (determined by {@link GameHandler#leaveGame(SOCGame, Connection)}):
     * <UL>
     *  <LI> {@code c} was the last non-robot player
     *  <LI> No one was watching/observing
     *  <LI> {@link SOCGame#isBotsOnly} flag is false
     * </UL>
     *<P>
     * <B>Locks:</B> Has {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gm)}
     * when calling this method; should not have {@link SOCGame#takeMonitor()}.
     * May or may not have {@link SOCGameList#takeMonitor()}, see {@code gameListLock} parameter.
     *<P>
     * Before v1.2.01, games where all players were bots would continue playing if at least one client was
     * watching/observing. In v2.0.00 and newer, such games can continue only if bot-development property
     * {@code jsettlers.bots.botgames.total} != 0 and there is an observer. (v1.2.xx does not have that property,
     * and will destroy the game.)
     *
     * @param c  the connection; if c is being dropped because of an error,
     *           this method assumes that {@link Connection#disconnect()}
     *           has already been called.  This method won't exclude c from
     *           any communication about leaving the game, in case they are
     *           still connected and in other games.
     * @param ga  game to leave, if already known from {@link SOCGameListAtServer#getGameData(String)}
     * @param gm  game name, if {@code ga} object not already known
     * @param destroyIfEmpty  if true, this method will destroy the game if it's now empty.
     *           If false, the caller must call {@link #destroyGame(String)}
     *           before calling {@link SOCGameList#releaseMonitor()}.
     * @param gameListLock  true if we have the {@link SOCGameList#takeMonitor()} lock when called;
     *           false if it must be acquired and released within this method
     * @return true if the game was destroyed, or if it would have been destroyed but {@code destroyIfEmpty} is false.
     * @throws IllegalArgumentException if both {@code ga} and {@code gm} are null
     */
    public boolean leaveGame
        (Connection c, SOCGame ga, String gm, final boolean destroyIfEmpty, final boolean gameListLock)
        throws IllegalArgumentException
    {
        if (c == null)
        {
            return false;  // <---- Early return: no connection ----
        }

        if (gm == null)
        {
            if (ga == null)
                throw new IllegalArgumentException("both null");

            gm = ga.getName();
        }

        boolean gameDestroyed = false;

        gameList.removeMember(c, gm);

        if (ga == null)
        {
            ga = gameList.getGameData(gm);

            if (ga == null)
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
     * Handle a member leaving the game:
     *<UL>
     * <LI> Manages game list locks
     * <LI> Calls {@link #leaveGame(Connection, SOCGame, String, boolean, boolean)}
     * <LI> If game now destroyed, announces game deletion
     * <LI> If leaving member was a bot, remove it from {@link #robotDismissRequests} for this game.
     *      Calls {@link #sitDown(SOCGame, Connection, int, boolean, boolean)} if a human player
     *      is replacing the bot.
     *</UL>
     * Before v2.0.00 this method was {@code handleLEAVEGAME_member} called only from
     * {@link SOCServerMessageHandler#handleLEAVEGAME(Connection, SOCLeaveGame)}.
     *
     * @param c  the connection leaving the game
     * @param game  game to leave, if already known from {@link SOCGameListAtServer#getGameData(String)}
     * @param gaName  game name, if {@code game} object not already known
     * @throws IllegalArgumentException if both {@code game} and {@code gaName} are null
     * @since 1.1.07
     */
    void leaveGameMemberAndCleanup(Connection c, SOCGame game, String gaName)
        throws IllegalArgumentException
    {
        if (gaName == null)
        {
            if (game == null)
                throw new IllegalArgumentException("both null");

            gaName = game.getName();
        }

        boolean gameDestroyed = false;
        if (! gameList.takeMonitorForGame(gaName))
        {
            return;  // <--- Early return: game not in gamelist ---
        }

        try
        {
            gameDestroyed = leaveGame(c, game, gaName, true, false);
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
               SOCLeaveGame leaveMessage = new SOCLeaveGame(c.getData(), c.host(), gaName);
               messageToGame(gaName, leaveMessage);
               recordGameEvent(gaName, leaveMessage);
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
                if (game == null)
                    game = gameList.getGameData(gaName);
                final int pn = req.getSitDownMessage().getPlayerNumber();
                final boolean isRobot = req.getSitDownMessage().isRobot();
                if (! isRobot)
                {
                    game.getPlayer(pn).setFaceId(1);  // Don't keep the robot face icon
                }
                sitDown(game, req.getArriving(), pn, isRobot, false);
            }
        }
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
     * {@code FAST} or {@code SMART} strategy params in {@link #handleIMAROBOT(Connection, SOCImARobot)}
     * based on their name prefixes ("droid " or "robot " respectively).
     *<P>
     * In v1.2.00 and newer, human players can't use names with bot prefixes "droid " or "robot ":
     * see {@link #checkNickname(String, Connection, boolean, boolean)}.
     *<P>
     * Before 1.1.09, this method was part of SOCPlayerClient.
     *
     * @param numFast number of fast robots, with {@link soc.robot.SOCRobotDM#FAST_STRATEGY FAST_STRATEGY}
     * @param numSmart number of smart robots, with {@link soc.robot.SOCRobotDM#SMART_STRATEGY SMART_STRATEGY}
     * @return True if robots were set up, false if an exception occurred.
     *     This typically happens if a robot class or SOCDisplaylessClient
     *     can't be loaded, due to packaging of the server-only JAR.
     * @see soc.client.SOCPlayerClient#startPracticeGame()
     * @see soc.client.MainDisplay#startLocalTCPServer(int)
     * @see #startRobotOnlyGames(boolean, boolean)
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
            // Packaging error, robot classes not included in JAR
            return false;
        }

        return true;
    }

    /**
     * Destroy a game and clean up related data, such as the owner's count of
     * {@link SOCClientData#getCurrentCreatedGames()}.
     *<P>
     * Note that if this game had the {@link SOCGame#isBotsOnly} flag, and {@link #numRobotOnlyGamesRemaining} &gt; 0,
     * will call {@link #startRobotOnlyGames(boolean, boolean)}. If none remain, will shut down server if
     * {@link #PROP_JSETTLERS_BOTS_BOTGAMES_SHUTDOWN} is true and active game list is empty.
     *<P>
     * <B>Locks:</B> Must have {@link #gameList}{@link SOCGameList#takeMonitor() .takeMonitor()}
     * before calling this method.
     *
     * @param gm  Name of the game to destroy
     * @see #leaveGame(Connection, SOCGame, String, boolean, boolean)
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
        Vector<Connection> members = null;
        members = gameList.getMembers(gm);

        gameList.deleteGame(gm);  // also calls SOCGame.destroyGame

        if (members != null)
        {
            Enumeration<Connection> conEnum = members.elements();

            while (conEnum.hasMoreElements())
            {
                Connection con = conEnum.nextElement();
                con.put(SOCRobotDismiss.toCmd(gm));
            }
        }

        // Reduce the owner's games-active count
        final String gaOwner = cg.getOwner();
        if (gaOwner != null)
        {
            Connection oConn = conns.get(gaOwner);
            if (oConn != null)
                ((SOCClientData) oConn.getAppData()).deletedGame();
        }

        if (! wasBotsOnly)
        {
            return;
        }

        if (numRobotOnlyGamesRemaining > 0)
        {
            startRobotOnlyGames(true, true);
        }
        else if (getConfigIntProperty(PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL, 0) > 0)
        {
            // Other robot-only games could still be active; remaining = 0 was set when the last one was started

            if ((gameList.size() == 0) && getConfigBoolProperty(PROP_JSETTLERS_BOTS_BOTGAMES_SHUTDOWN, false))
            {
                stopServer(">>> All Robot-only games have finished. Shutting down server. <<<");

                System.exit(0);  // TODO nonzero exit code if any exceptions thrown while bot games ran?
            }
        }
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
     * @since 1.1.00
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
     * Is a debug user enabled and allowed to run the commands listed in {@link #DEBUG_COMMANDS_HELP}?
     * Default is false.  Set with {@link #PROP_JSETTLERS_ALLOW_DEBUG}.
     * @return  True if a debug user is enabled.
     * @since 2.0.00
     */
    public final boolean isDebugUserEnabled()
    {
        return allowDebugUser;
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
     * <LI> {@link #PROP_JSETTLERS_TEST_DB} flag property
     * <LI> {@link #PROP_JSETTLERS_TEST_VALIDATE__CONFIG} flag property
     * <LI> <tt>{@link SOCDBHelper#PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR}=test</tt> prop value
     * <LI> {@link SOCDBHelper#PROP_JSETTLERS_DB_SCRIPT_SETUP} property
     * <LI> {@link SOCDBHelper#PROP_JSETTLERS_DB_UPGRADE__SCHEMA} flag property
     * <LI> <tt>{@link SOCDBHelper#PROP_JSETTLERS_DB_SETTINGS}=write</tt> prop value
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
     * Connection {@code c} is leaving the server; remove from all channels it was in.
     * In channels where {@code c} was the last connection, calls {@link #destroyChannel(String)}.
     * Sends {@link SOCDeleteChannel} to announce any destroyed channels.
     *
     * @param c  the connection
     */
    public void leaveAllChannels(Connection c)
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
    public void leaveAllGames(Connection c)
    {
        if (c == null)
            return;

        List<String> toDestroy = new ArrayList<String>();  // games where c was the last human player

        gameList.takeMonitor();

        try
        {
            for (String ga : gameList.getGameNames())
            {
                Vector<Connection> v = gameList.getMembers(ga);

                if (v.contains(c))
                {
                    boolean thisGameDestroyed = false;
                    gameList.takeMonitorForGame(ga);

                    try
                    {
                        thisGameDestroyed = leaveGame(c, null, ga, false, true);
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
            Vector<Connection> v = channelList.getMembers(ch);

            if (v != null)
            {
                Enumeration<Connection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    Connection c = menum.nextElement();

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
        Vector<Connection> v = channelList.getMembers(ch);

        if (v != null)
        {
            final String mesCmd = mes.toCmd();

            Enumeration<Connection> menum = v.elements();

            while (menum.hasMoreElements())
            {
                Connection c = menum.nextElement();

                if (c != null)
                {
                    c.put(mesCmd);
                }
            }
        }
    }

    /**
     * Send a message to a player, and record it if that debugging type is enabled.
     *
     * @param c   the player connection
     * @param mes the message to send
     */
    public void messageToPlayer(Connection c, SOCMessage mes)
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
     * @see #messageToPlayerKeyed(Connection, String, String)
     */
    public void messageToPlayer(Connection c, final String ga, final String txt)
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
     * {@link Connection#getLocalized(String) c.getLocalized(key)}));
     *
     * @param c   the player connection; if their version is 2.0.00 or newer,
     *            they will be sent {@link SOCGameServerText}, otherwise {@link SOCGameTextMsg}.
     *            Null {@code c} is ignored and not an error.
     * @param gaName  game name
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of
     * @since 2.0.00
     * @see #messageToPlayerKeyed(Connection, String, String, Object...)
     * @see #messageToPlayerPendingKeyed(SOCPlayer, String, String)
     */
    public final void messageToPlayerKeyed(Connection c, final String gaName, final String key)
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
     * {@link Connection#getLocalized(String, Object...) c.getLocalized(key, args)}));
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
     * @see #messageToPlayerKeyed(Connection, String, String)
     * @see #messageToPlayerKeyedSpecial(Connection, SOCGame, String, Object...)
     * @see #messageToPlayerPendingKeyed(SOCPlayer, String, String)
     */
    public final void messageToPlayerKeyed
        (Connection c, final String gaName, final String key, final Object ... args)
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
     * {@link Connection#getLocalizedSpecial(SOCGame, String, Object...) c.getLocalizedSpecial(ga, key, args)}));
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
     * @see #messageToPlayerKeyed(Connection, String, String, Object...)
     * @see #messageToPlayerKeyed(Connection, String, String)
     */
    public final void messageToPlayerKeyedSpecial
        (Connection c, final SOCGame ga, final String key, final Object ... args)
    {
        if (c == null)
            return;

        if (c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT)
            c.put(SOCGameServerText.toCmd(ga.getName(), c.getLocalizedSpecial(ga, key, args)));
        else
            c.put(SOCGameTextMsg.toCmd(ga.getName(), SERVERNAME, c.getLocalizedSpecial(ga, key, args)));
    }

    /**
     * Add a pending localized {@link SOCGameServerText} game text message to {@link SOCPlayer#pendingMessagesOut},
     * to be sent soon to player's client with {@link SOCGameHandler#sendGamePendingMessages(SOCGame, boolean)}.
     *<P>
     * If client's version is 2.0.00 or newer they will be sent
     * {@link SOCGameServerText}, otherwise {@link SOCGameTextMsg}.
     *<P>
     * <b>Note:</b> Only a few of the server message-handling methods check the queue;
     * see {@link SOCGame#pendingMessagesOut}.
     *
     * @param pl  the player; {@code null} is ignored and not an error.
     * @param gaName  game name
     * @param key the message localization key, from {@link SOCStringManager#get(String)}, to look up and send text of
     * @see #messageToPlayerKeyed(Connection, String, String)
     * @since 2.0.00
     */
    public final void messageToPlayerPendingKeyed
        (final SOCPlayer pl, final String gaName, final String key)
    {
        if (pl == null)
            return;
        final Connection c = getConnection(pl.getName());
        if (c == null)
            return;

        if (c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT)
            pl.pendingMessagesOut.add(new SOCGameServerText(gaName, c.getLocalized(key)));
        else
            pl.pendingMessagesOut.add(new SOCGameTextMsg(gaName, SERVERNAME, c.getLocalized(key)));
    }

    /**
     * Send a message to the given game.
     * Also record the message in that game's {@link SOCChatRecentBuffer}.
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
            Vector<Connection> v = gameList.getMembers(ga);

            if (v != null)
            {
                //D.ebugPrintln("M2G - "+mes);
                Enumeration<Connection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    Connection c = menum.nextElement();

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
     * @see #messageToGameExcept(String, Connection, String, boolean)
     * @since 1.1.08
     */
    public void messageToGame(final String ga, final String txt)
    {
        final String gameServTxtMsg = SOCGameServerText.toCmd(ga, txt);

        gameList.takeMonitorForGame(ga);

        try
        {
            Vector<Connection> v = gameList.getMembers(ga);

            if (v != null)
            {
                Enumeration<Connection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    Connection c = menum.nextElement();
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
     * {@link Connection#getLocalized(String) c.getLocalized(key)} for the localized text to send.
     *<P>
     * <B>Locks:</B> If {@code takeMon} is true, takes and releases
     * {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gameName)}.
     * Otherwise call {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gameName)}
     * before calling this method.
     *
     * @param ga  The game
     * @param msg  The data message to be sent after localizing text.
     *     This message object's fields are not changed here, the localization results are not kept with {@code msg}.
     * @param takeMon Should this method take and release game's monitor via
     *     {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gameName)}?
     *     True unless caller already holds that monitor.
     * @see #messageToGameKeyed(SOCGame, boolean, String)
     * @see #messageToGameKeyed(SOCGame, boolean, String, Object...)
     * @since 2.0.00
     */
    public void messageToGameKeyedType(SOCGame ga, SOCKeyedMessage msg, final boolean takeMon)
    {
        // Very similar code to impl_messageToGameKeyedSpecial:
        // if you change code here, consider changing it there too

        final boolean hasMultiLocales = ga.hasMultiLocales;
        final String gaName = ga.getName();
        boolean rsrcMissing = false;

        if (takeMon)
            gameList.takeMonitorForGame(gaName);

        try
        {
            Vector<Connection> v = gameList.getMembers(gaName);

            if (v != null)
            {
                Enumeration<Connection> menum = v.elements();

                final String msgKey = msg.getKey();
                String gameLocalMsg = null, localText = null, gameTxtLocale = null;  // as rendered during prev. iter.
                while (menum.hasMoreElements())
                {
                    Connection c = menum.nextElement();
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
     * {@link Connection#getLocalized(String) c.getLocalized(key)} for the localized text to send.
     *<P>
     * Game members with null locale (such as robots) will not be sent the message.
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
     * @see #messageToGameKeyedSpecialExcept(SOCGame, boolean, Connection, String, Object...)
     * @see #messageToGameKeyedType(SOCGame, SOCKeyedMessage, boolean)
     * @see #messageToGame(String, String)
     * @since 2.0.00
     */
    public void messageToGameKeyed(SOCGame ga, final boolean takeMon, final String key)
        throws MissingResourceException
    {
        messageToGameKeyed(ga, takeMon, key, (Object[]) null);
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message (with parameters) to a game.
     * Same as {@link #messageToGame(String, String)} but calls each member connection's
     * {@link Connection#getLocalized(String) c.getLocalized(key)} for the localized text to send.
     *<P>
     * Game members with null locale (such as robots) will not be sent the message.
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
     * @see #messageToGameKeyedSpecialExcept(SOCGame, boolean, Connection, String, Object...)
     * @see #messageToGameKeyedType(SOCGame, SOCKeyedMessage, boolean)
     * @see #messageToGame(String, String)
     * @since 2.0.00
     */
    public void messageToGameKeyed(SOCGame ga, final boolean takeMon, final String key, final Object ... params)
        throws MissingResourceException
    {
        impl_messageToGameKeyedSpecial
            (ga, takeMon, gameList.getMembers(ga.getName()), null, false, key, params);
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message (with parameters) to a game,
     * optionally with special formatting like <tt>{0,rsrcs}</tt>, optionally excluding one connection.
     * Same as {@link #messageToGame(String, String)} but calls each member connection's
     * {@link Connection#getLocalizedSpecial(SOCGame, String, Object...) c.getLocalizedSpecial(...)} for the localized text to send.
     *<P>
     * For the SoC-specific parameters such as <tt>{0,rsrcs}</tt>, see the javadoc for
     * {@link SOCStringManager#getSpecial(SOCGame, String, Object...)}.
     *<P>
     * Game members with null locale (such as robots) will not be sent the message.
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
     * @see #messageToGameKeyedSpecialExcept(SOCGame, boolean, Connection, String, Object...)
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
     * {@link Connection#getLocalizedSpecial(SOCGame, String, Object...) c.getLocalizedSpecial(...)} for the
     * localized text to send.
     *<P>
     * Game members with null locale (such as robots) will not be sent the message.
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
        (SOCGame ga, final boolean takeMon, Connection ex, final String key, final Object ... params)
        throws MissingResourceException, IllegalArgumentException
    {
        impl_messageToGameKeyedSpecial(ga, takeMon, gameList.getMembers(ga.getName()), ex, true, key, params);
    }

    /**
     * Send a localized {@link SOCGameServerText} game text message (with parameters) to a game,
     * optionally with special formatting like <tt>{0,rsrcs}</tt>, optionally excluding some connections.
     * Same as {@link #messageToGame(String, String)} but calls each member connection's
     * {@link Connection#getLocalizedSpecial(SOCGame, String, Object...) c.getLocalizedSpecial(...)} for the
     * localized text to send.
     *<P>
     * Game members with null locale (such as robots) will not be sent the message.
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
     * @see #messageToGameKeyedSpecialExcept(SOCGame, boolean, Connection, String, Object...)
     * @see #messageToGameKeyed(SOCGame, boolean, String)
     * @see #messageToGame(String, String)
     * @since 2.0.00
     */
    public final void messageToGameKeyedSpecialExcept
        (SOCGame ga, final boolean takeMon, List<Connection> ex, final String key, final Object ... params)
        throws MissingResourceException, IllegalArgumentException
    {
        List<Connection> sendTo = gameList.getMembers(ga.getName());
        if ((ex != null) && ! ex.isEmpty())
        {
            // Copy the members list, then remove the excluded connections.
            // This method isn't called for many situations, so this is efficient enough.
            sendTo = new ArrayList<Connection>(sendTo);
            for (Connection excl : ex)
                sendTo.remove(excl);
        }

        impl_messageToGameKeyedSpecial(ga, takeMon, sendTo, null, true, key, params);
    }

    /**
     * Implement {@link #messageToGameKeyed(SOCGame, boolean, String, Object...)},
     * {@code messageToGameKeyedSpecial}, and {@code messageToGameKeyedSpecialExcept}.
     *
     * @param ga  the game object
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                True unless caller already holds that monitor.
     * @param members  Game members to send to, from {@link SOCGameListAtServer#getMembers(String)}.
     *            Any member in this list with null locale (such as robots) will be skipped and not sent the message.
     *            If we're excluding several members of the game, make a new list from getMembers, remove them from
     *            that list, then pass it to this method.
     *            Returns immediately if {@code null}.
     * @param ex  the excluded connection, or {@code null}
     * @param fmtSpecial  Should this method call {@link SOCStringManager#getSpecial(SOCGame, String, Object...)}
     *            instead of the usual {@link SOCStringManager#get(String, Object...)} ?
     *            True if called from {@code messageToGameKeyedSpecial*}, false from other
     *            {@code messageToGameKeyed} methods.
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
        (SOCGame ga, final boolean takeMon, final List<Connection> members, final Connection ex,
         final boolean fmtSpecial, final String key, final Object ... params)
        throws MissingResourceException, IllegalArgumentException
    {
        if (members == null)
            return;

        // Very similar code to messageToGameKeyedType:
        // If you change code here, change it there too.
        // Indentation within try/catch matches messageToGameKeyedType's.

        final boolean hasMultiLocales = ga.hasMultiLocales;
        final String gaName = ga.getName();

        if (takeMon)
            gameList.takeMonitorForGame(gaName);

        try
        {
                Iterator<Connection> miter = members.iterator();

                String gameTextMsg = null, gameTxtLocale = null;  // as rendered for previous client during loop
                while (miter.hasNext())
                {
                    Connection c = miter.next();
                    if ((c == null) || (c == ex))
                        continue;

                    final String cliLocale = c.getI18NLocale();
                    if (cliLocale == null)
                        continue;  // skip bots

                    if ((gameTextMsg == null)
                        || (hasMultiLocales && ! cliLocale.equals(gameTxtLocale)))
                    {
                        if (fmtSpecial)
                            gameTextMsg = SOCGameServerText.toCmd
                                (gaName, c.getLocalizedSpecial(ga, key, params));
                        else
                            gameTextMsg = SOCGameServerText.toCmd
                                (gaName, (params != null) ? c.getLocalized(key, params) : c.getLocalized(key));
                        gameTxtLocale = cliLocale;
                    }

                    if ((c.getVersion() >= SOCGameServerText.VERSION_FOR_GAMESERVERTEXT) && (gameTextMsg != null))
                        c.put(gameTextMsg);
                    else
                        // old client (this is uncommon) needs a different message type
                        if (fmtSpecial)
                            c.put(SOCGameTextMsg.toCmd
                                (gaName, SERVERNAME, c.getLocalizedSpecial(ga, key, params)));
                        else
                            c.put(SOCGameTextMsg.toCmd
                                (gaName, SERVERNAME,
                                 (params != null) ? c.getLocalized(key, params) : c.getLocalized(key)));
                }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace
                (e, (fmtSpecial) ? "Exception in messageToGameKeyedSpecial" : "Exception in messageToGameKeyed");
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
        Vector<Connection> v = gameList.getMembers(ga);
        if (v == null)
            return;

        //D.ebugPrintln("M2G - "+mes);
        final String mesCmd = mes.toCmd();
        Enumeration<Connection> menum = v.elements();

        while (menum.hasMoreElements())
        {
            Connection c = menum.nextElement();

            if (c != null)
            {
                //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", c.getData()));
                c.put(mesCmd);
            }
        }
    }

    /**
     * Send a server text message to all the connections in a game, excluding one.
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
     * @see #messageToGameExcept(String, Connection, SOCMessage, boolean)
     * @since 2.0.00
     */
    public void messageToGameExcept(final String gn, final Connection ex, final String txt, final boolean takeMon)
    {
        // TODO I18N: Find calls to this method; consider connection's locale and version
        messageToGameExcept(gn, ex, new SOCGameTextMsg(gn, SERVERNAME, txt), takeMon);
    }

    /**
     * Send a message to all the connections in a game, excluding some.
     *
     * @param gn  the name of the game
     * @param ex  the list of excluded connections; not {@code null}
     * @param mes the message
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     * @see #messageToGameExcept(String, Connection, SOCMessage, boolean)
     */
    public void messageToGameExcept
        (final String gn, final List<Connection> ex, final SOCMessage mes, final boolean takeMon)
    {
        if (takeMon)
            gameList.takeMonitorForGame(gn);

        try
        {
            Vector<Connection> v = gameList.getMembers(gn);

            if (v != null)
            {
                //D.ebugPrintln("M2GE - "+mes);
                final String mesCmd = mes.toCmd();
                Enumeration<Connection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    Connection con = menum.nextElement();

                    if ((con != null) && ! ex.contains(con))
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
     * Send a message to all the connections in a game, excluding one.
     *
     * @param gn  the name of the game
     * @param ex  the excluded connection, or null
     * @param mes the message
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     * @see #messageToGameExcept(String, Connection, String, boolean)
     * @see #messageToGameExcept(String, List, SOCMessage, boolean)
     * @see #messageToGameForVersionsExcept(SOCGame, int, int, Connection, SOCMessage, boolean)
     */
    public void messageToGameExcept(String gn, Connection ex, SOCMessage mes, boolean takeMon)
    {
        if (takeMon)
            gameList.takeMonitorForGame(gn);

        try
        {
            Vector<Connection> v = gameList.getMembers(gn);

            if (v != null)
            {
                //D.ebugPrintln("M2GE - "+mes);
                final String mesCmd = mes.toCmd();
                Enumeration<Connection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    Connection con = menum.nextElement();
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
     *                {@link Version#versionNumber()} and {@link Connection#getVersion()}.
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
     *                {@link Version#versionNumber()} and {@link Connection#getVersion()}.
     * @param vmax  Maximum version to send to, or {@link Integer#MAX_VALUE}
     * @param ex  the excluded connection, or null
     * @param mes  the message
     * @param takeMon Should this method take and release
     *                game's monitor via {@link SOCGameList#takeMonitorForGame(String)} ?
     *                If the game's clients are all older than <tt>vmin</tt> or
     *                newer than <tt>vmax</tt>, nothing happens and the monitor isn't taken.
     * @since 1.1.19
     * @see #messageToGameExcept(String, Connection, SOCMessage, boolean)
     */
    public final void messageToGameForVersionsExcept
        (final SOCGame ga, final int vmin, final int vmax, final Connection ex,
         final SOCMessage mes, final boolean takeMon)
    {
        if ((ga.clientVersionLowest > vmax) || (ga.clientVersionHighest < vmin))
            return;  // <--- All clients too old or too new ---

        final String gn = ga.getName();

        if (takeMon)
            gameList.takeMonitorForGame(gn);

        try
        {
            Vector<Connection> v = gameList.getMembers(gn);
            if (v != null)
            {
                String mesCmd = null;  // lazy init, will be mes.toCmd()
                Enumeration<Connection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    Connection con = menum.nextElement();
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
     * Things to do when the connection c leaves:
     * Calls {@link #leaveAllChannels(Connection)}
     * and {@link #leaveAllGames(Connection)}.
     *<P>
     * If {@code c} is a robot, looks for any games waiting for that bot to join
     * in order to start the game. If any found, tries to find another bot instead
     * by calling {@link GameHandler#findRobotAskJoinGame(SOCGame, Object, boolean)}.
     *<P>
     * This method is called within a per-client thread,
     * after connection is removed from conns collection
     * and version collection, and after c.disconnect() has been called.
     *
     * @param c  the connection
     */
    @Override
    public void leaveConnection(Connection c)
    {
        if ((c == null) || (c.getData() == null))
            return;

        leaveAllChannels(c);
        leaveAllGames(c);

        /**
         * if it is a robot, remove it from the list
         */
        final SOCClientData scd = (SOCClientData) (c.getAppData());
        if (scd.isRobot)
        {
            synchronized(robots)
            {
                robots.removeElement(c);
                if (! scd.isBuiltInRobot)
                    robots3p.removeElement(c);
            }

            /**
             * Are any games waiting for this robot to join?
             * If so, try to find other bots to join those games
             */
            final Map<String, Object> waitingGames = new HashMap<String, Object>();  // <gaName, join-req-related info>
            synchronized(robotJoinRequests)
            {
                for (Map.Entry<String, Hashtable<Connection, Object>> gaReqs : robotJoinRequests.entrySet())
                {
                    final Hashtable<Connection, Object> rConns = gaReqs.getValue();
                    Object reqInfo = rConns.remove(c);
                    if (reqInfo != null)
                    {
                        if (null != System.getProperty(SOCRobotClient.PROP_JSETTLERS_BOTS_TEST_QUIT_AT_JOINREQ))
                            System.err.println
                                ("srv.leaveConnection('" + c.getData() + "') found waiting ga: '"
                                 + gaReqs.getKey() + "' (" + reqInfo + ")");
                        waitingGames.put(gaReqs.getKey(), reqInfo);
                    }
                }
            }

            if (! waitingGames.isEmpty())
            {
                for (Map.Entry<String, Object> wReq : waitingGames.entrySet())
                {
                    final String gaName = wReq.getKey();
                    SOCGame ga = getGame(gaName);
                    if (ga != null)
                    {
                        GameHandler gh = gameList.getGameTypeHandler(gaName);
                        if (gh != null)
                            gh.findRobotAskJoinGame(ga, wReq.getValue(), false);
                    }
                }
            }
        }
    }

    /**
     * Things to do when a new connection comes.
     *<P>
     * If we already have {@link #maxConnections} named clients, reject this new one
     * by sending {@link SOCRejectConnection}.
     *<P>
     * If the connection is accepted, it's added to {@link #unnamedConns} until the
     * player "names" it by joining or creating a game under their player name.
     * Other communication is then done, in {@link #newConnection2(Connection)}.
     *<P>
     * Also set client's "assumed version" to -1, until we have sent and
     * received a VERSION message.
     *<P>
     * This method is called within a per-client thread.
     * You can send to client, but can't yet receive messages from them.
     *<P>
     *  SYNCHRONIZATION NOTE: During the call to newConnection1, the monitor lock of
     *  {@link #unnamedConns} is held.  Thus, defer as much as possible until
     *  {@link #newConnection2(Connection)} (after the connection is accepted).
     *
     * @param c  the new Connection
     * @return true to accept and continue, false if you have rejected this connection;
     *         if false, addConnection will call {@link Connection#disconnectSoft()}.
     *
     * @see #addConnection(Connection)
     * @see #newConnection2(Connection)
     * @see #nameConnection(Connection, boolean)
     */
    @Override
    public boolean newConnection1(Connection c)
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
                c.put(new SOCRejectConnection("Too many connections, please try another server.").toCmd());
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
               Connection tempCon = (Connection)allConnections.nextElement();
               if (!(c.host().equals("pippen")) && (tempCon.host().equals(c.host()))) {
               hostMatch = true;
               break;
               }
               }
             */
            if (hostMatch)
            {
                c.put(new SOCRejectConnection("Can't connect to the server more than once from one machine.").toCmd());
            }
            else
            {
                /**
                 * Accept this connection.
                 * Once it's added to the list,
                 * {@link #newConnection2(Connection)} will
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
     * Unlike {@link #newConnection1(Connection)},
     * no connection-list locks are held when this method is called.
     *<P>
     * Client's {@link SOCClientData} appdata is set here.
     *<P>
     * This method is called within a per-client thread.
     * You can send to client, but can't yet receive messages from them.
     */
    @Override
    protected void newConnection2(Connection c)
    {
        SOCClientData cdata = new SOCClientData();
        c.setAppData(cdata);

        // VERSION of server
        SOCFeatureSet feats = features;
        if (acctsNotOpenRegButNoUsers)
        {
            feats = new SOCFeatureSet(features);
            feats.add(SOCFeatureSet.SERVER_OPEN_REG);  // no accounts: don't require a password from SOCAccountClient
        }
        c.put(SOCVersion.toCmd
            (Version.versionNumber(), Version.version(), Version.buildnum(), feats.getEncodedList(), null));

        // CHANNELS
        List<String> cl = new ArrayList<String>();
        channelList.takeMonitor();

        try
        {
            Enumeration<String> clEnum = channelList.getChannels();

            while (clEnum.hasMoreElements())
                cl.add(clEnum.nextElement());
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
         * The version timer will call {@link SOCServer#sendGameList} when it expires.
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
     * Calls {@link Server#nameConnection(Connection, boolean)} to move the connection
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
     * @param c  Connected client; its name key ({@link Connection#getData()}) must not be null
     * @param isReplacing  Are we replacing / taking over a current connection?
     * @throws IllegalArgumentException If c isn't already connected, if c.getData() returns null,
     *          or if nameConnection has previously been called for this connection.
     * @since 1.1.08
     */
    @Override
    public void nameConnection(Connection c, boolean isReplacing)
        throws IllegalArgumentException
    {
        Connection oldConn = null;
        if (isReplacing)
        {
            String cKey = c.getData();
            if (cKey == null)
                throw new IllegalArgumentException("null c.getData");
            oldConn = conns.get(cKey);
            if (oldConn == null)
                isReplacing = false;  // shouldn't happen, but fail gracefully
        }

        super.nameConnection(c, isReplacing);

        if (isReplacing)
        {
            final List<SOCGame> cannotReplace = gameList.replaceMemberAllGames(oldConn, c);
            channelList.replaceMemberAllChannels(oldConn, c);

            SOCClientData scdNew = (SOCClientData) (c.getAppData());
            SOCClientData scdOld = (SOCClientData) (oldConn.getAppData());
            if ((scdNew != null) && (scdOld != null))
                scdNew.copyClientPlayerStats(scdOld);

            // Let the old one know it's disconnected now,
            // in case it ever does get its connection back.
            if (oldConn.getVersion() >= 1108)
                oldConn.put(SOCServerPing.toCmd(-1));

            // If oldConn was in some games which c can't join because of limited client features,
            // remove oldConn from those now. This is unlikely: see GLAS.replaceMemberAllGames javadoc.
            if (cannotReplace != null)
                for (SOCGame ga : cannotReplace)
                    leaveGameMemberAndCleanup(oldConn, ga, null);
        }

        numberOfUsers++;
    }

    /**
     * Build a list of the names of all connected clients.
     * The list is {@link StringBuilder} not {@link String} to do as little work as possible
     * while holding the {@link Server#unnamedConns} synchronization lock.
     * @param sbs  List of {@link StringBuilder}s to hold reply to clients,
     *     max length 50 chars. Not null.
     * @return The number of <B>unnamed</B> connections, for statistics
     * @since 2.0.00
     */
    final Integer getConnectedClientNames(final List<StringBuilder> sbs)
    {
        StringBuilder sb = new StringBuilder("- ");
        sbs.add(sb);

        int nUnnamed;
        synchronized (unnamedConns)  // sync on that not on conns, per javadoc
        {
            nUnnamed = unnamedConns.size();

            Enumeration<Connection> ec = getConnections();  // the named ones
            while (ec.hasMoreElements())
            {
                String cname = ec.nextElement().getData();

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

        return Integer.valueOf(nUnnamed);
    }

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
     * by {@link SOCGameListAtServer#playerGamesMinVersion(Connection) gameList.playerGamesMinVersion}.
     *
     * @param n  the name; check for max length before calling this method
     * @param newc  A new incoming connection, asking for this name
     * @param withPassword  Did the connection supply a password?
     * @param isBot  True if authenticating as robot, false if human
     * @return   0 if the name is okay; <BR>
     *          -1 if OK <strong>and you are taking over a connection;</strong> <BR>
     *          -2 if not OK by rules (fails isSingleLineAndSafe,
     *             named "debug" or {@link #SERVERNAME},
     *             human with bot name prefix, etc); <BR>
     *          -vers if not OK by version (for takeover; will be -1000 lower); <BR>
     *          or, the number of seconds after which <tt>newc</tt> can
     *             take over this name's games.
     * @see #checkNickname_getRetryText(int)
     */
    private int checkNickname
        (String n, Connection newc, final boolean withPassword, final boolean isBot)
    {
        if (n.equals(SERVERNAME) || ! SOCMessage.isSingleLineAndSafe(n))
        {
            return -2;
        }

        if (SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher(n).matches())
        {
            return -2;  // TODO distinct ret value, to send localized error to client
        }

        // check "debug" and bot name prefixes used in setupLocalRobots
        final String nLower = n.toLowerCase(Locale.US);
        if ((nLower.equals("debug") && ! isDebugUserEnabled())
            || ((! isBot)
                && (nLower.startsWith("droid ") || nLower.startsWith("robot "))))
        {
            return -2;
        }

        // check conns hashtable
        Connection oldc = getConnection(n, false);
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
     *         {@link SOCMessageDispatcher#dispatch(SOCMessage, Connection)}.
     */
    @Override
    public boolean processFirstCommand(final SOCMessage mes, Connection con)
    {
        try
        {
            if ((mes != null) && (mes.getType() == SOCMessage.VERSION))
            {
                srvMsgHandler.handleVERSION(con, (SOCVersion) mes);

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
     * List and description of general commands that any game member can run.
     * Used by {@link #processDebugCommand(Connection, String, String, String)}
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
    static final String ADMIN_COMMANDS_HEADING = "--- Admin Commands ---";

    /**
     * List and description of user-admin commands. Along with {@link #GENERAL_COMMANDS_HELP}
     * and {@link #DEBUG_COMMANDS_HELP}, used by
     * {@link #processDebugCommand(Connection, String, String, String)}
     * when {@code *HELP*} is requested by a debug/admin user who passes
     * {@link #isUserDBUserAdmin(String) isUserDBUserAdmin(username)}.
     * Preceded by {@link #ADMIN_COMMANDS_HEADING}.
     * @since 1.1.20
     * @see #GENERAL_COMMANDS_HELP
     * @see #DEBUG_COMMANDS_HELP
     */
    public static final String[] ADMIN_USER_COMMANDS_HELP =
        {
        "*WHO* gameName   show players and observers of gameName",
        "*WHO* *  show all connected clients",
        "*DBSETTINGS*  show current database settings, if any",  // processed in SOCServerMessageHandler
        };

    /**
     * List and description of debug/admin commands. Along with {@link #GENERAL_COMMANDS_HELP}
     * and {@link #ADMIN_USER_COMMANDS_HELP},
     * used by {@link #processDebugCommand(Connection, String, String, String)}
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
     * {@link GameHandler#processDebugCommand(Connection, String, String, String)}
     * to check for those.
     *<P>
     * Check {@link #allowDebugUser} before calling this method.
     * For list of commands see {@link #GENERAL_COMMANDS_HELP}, {@link #DEBUG_COMMANDS_HELP},
     * {@link #ADMIN_USER_COMMANDS_HELP}, and {@link GameHandler#getDebugCommandsHelp()}.
     * "Unprivileged" general commands are handled by
     * {@link SOCServerMessageHandler#handleGAMETEXTMSG(Connection, SOCGameTextMsg)}.
     *
     * @param debugCli  Client sending the potential debug command
     * @param ga  Game in which the message is sent
     * @param dcmd   Text message which may be a debug command
     * @param dcmdU  {@code dcmd} as uppercase, for efficiency (it's already been uppercased in caller)
     * @return true if {@code dcmd} is a recognized debug command, false otherwise
     */
    public boolean processDebugCommand(Connection debugCli, String ga, final String dcmd, final String dcmdU)
    {
        // See SOCServerMessageHandler.handleGAMETEXTMSG for "unprivileged" debug commands like *HELP*, *STATS*, and *ADDTIME*.

        boolean isCmd = true;  // eventual return value; will set false if unrecognized

        if (dcmdU.startsWith("*KILLGAME*"))
        {
            messageToGameUrgent(ga, ">>> ********** " + debugCli.getData() + " KILLED THE GAME!!! ********** <<<");
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
                broadcast(SOCBCastTextMsg.toCmd(debugCli.getData() + " WANTS TO STOP THE SERVER"));
                messageToPlayer(debugCli, ga, "Send stop command again with the password.");
            }

            if (shutNow)
            {
                String stopMsg = ">>> ********** " + debugCli.getData() + " KILLED THE SERVER!!! ********** <<<";
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
            Enumeration<Connection> robotsEnum = robots.elements();

            while (robotsEnum.hasMoreElements())
            {
                Connection robotConn = robotsEnum.nextElement();
                messageToGame(ga, "> Robot: " + robotConn.getData());
                robotConn.put(SOCAdminPing.toCmd((ga)));
            }
        }
        else if (dcmdU.startsWith("*RESETBOT* "))
        {
            String botName = dcmd.substring(11).trim();
            messageToGame(ga, "> botName = '" + botName + "'");

            Enumeration<Connection> robotsEnum = robots.elements();

            boolean botFound = false;
            while (robotsEnum.hasMoreElements())
            {
                Connection robotConn = robotsEnum.nextElement();
                if (botName.equals(robotConn.getData()))
                {
                    botFound = true;
                    messageToGame(ga, "> SENDING RESET COMMAND TO " + botName);

                    robotConn.put(new SOCAdminReset().toCmd());

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

            Enumeration<Connection> robotsEnum = robots.elements();

            boolean botFound = false;
            while (robotsEnum.hasMoreElements())
            {
                Connection robotConn = robotsEnum.nextElement();

                if (botName.equals(robotConn.getData()))
                {
                    botFound = true;
                    messageToGame(ga, "> DISCONNECTING " + botName);
                    removeConnection(robotConn, true);

                    break;
                }
            }
            if (! botFound)
                D.ebugPrintln("L2614 Bot not found to disconnect: " + botName);
        }
        else if (dcmdU.startsWith("*STARTBOTGAME*"))
        {
            if (0 == getConfigIntProperty(PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL, 0))
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

            srvMsgHandler.handleSTARTGAME(debugCli, new SOCStartGame(ga, 0), maxBots);
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
     * Process the {@code *STATS*} unprivileged debug command:
     * Send the client a list of server statistics and stats for the game they sent the command from.
     * Calls {@link SOCServerMessageHandler#processDebugCommand_gameStats(Connection, String, SOCGame, boolean)}.
     *<P>
     * Before v2.0.00, this method was part of {@code handleGAMETEXTMSG(..)}.
     * @param c  Client sending the {@code *STATS*} command
     * @param ga  Game in which the message is sent
     * @since 2.0.00
     * @see SOCServerMessageHandler#processDebugCommand_dbSettings(Connection, SOCGame)
     */
    final void processDebugCommand_serverStats(final Connection c, final SOCGame ga)
    {
        final long diff = System.currentTimeMillis() - startTime;
        final long hours = diff / (60 * 60 * 1000),
              minutes = (diff - (hours * 60 * 60 * 1000)) / (60 * 1000),
              seconds = (diff - (hours * 60 * 60 * 1000) - (minutes * 60 * 1000)) / 1000;
        Runtime rt = Runtime.getRuntime();
        final String gaName = ga.getName();

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

        srvMsgHandler.processDebugCommand_gameStats(c, gaName, ga, false);
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
     * The server is being cleanly stopped.  Send a final message, wait 500 milliseconds,
     * disconnect all the connections, disconnect from database if connected.
     * Does not call {@link System#exit(int)} in case caller wants to use a different exit code status value.
     *<P>
     * Currently called only by the debug command "*STOP*", by
     * {@link #destroyGame(String)} when {@link #PROP_JSETTLERS_BOTS_BOTGAMES_SHUTDOWN} is set,
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
     * in use but not timed out versus takeover, etc. Checks password if using the optional database.
     * Calls {@link #checkNickname(String, Connection, boolean, boolean)} and
     * {@link SOCDBHelper#authenticateUserPassword(String, String, soc.server.database.SOCDBHelper.AuthPasswordRunnable)}.
     *<P>
     * If not okay, sends client a {@link SOCStatusMessage} with an appropriate status code.
     *<P>
     * If this connection is already logged on and named ({@link Connection#getData() c.getData()} != {@code null}),
     * does nothing: Won't check username or password, just calls {@code authCallback} with {@link #AUTH_OR_REJECT__OK}.
     *<P>
     * Otherwise:
     *<UL>
     * <LI> If this user is already logged into another connection, checks whether this new
     *     replacement connection can "take over" the existing one according to a timeout calculation
     *     in {@link #checkNickname(String, Connection, boolean, boolean)}.
     * <LI> Checks username format, password if using DB, etc. If any check fails,
     *     send client a rejection {@code SOCStatusMessage} and return.
     * <LI> If {@code doNameConnection}, calls {@link Connection#setData(String) c.setData(nickname)} and
     *     {@link #nameConnection(Connection, boolean) nameConnection(c, isTakingOver)}.
     *     If username was found in the optional database, those calls use the exact-case name found by
     *     querying there case-insensitively (see below).
     * <LI> Calls {@code authCallback} with the {@link #AUTH_OR_REJECT__OK} flag, and possibly also the
     *     {@link #AUTH_OR_REJECT__SET_USERNAME} and/or {@link #AUTH_OR_REJECT__TAKING_OVER} flags.
     *</UL>
     * If the password is correct but the username is only a case-insensitive match with the database,
     * the client must update its internal nickname field to the exact-case username:
     *<UL>
     * <LI> If client's version is new enough to do that (v1.2.00+), caller's {@code authCallback} must send
     *     {@link SOCStatusMessage}({@link SOCStatusMessage#SV_OK_SET_NICKNAME SV_OK_SET_NICKNAME}):
     *     Calls {@code authCallback} with {@link #AUTH_OR_REJECT__OK} | {@link #AUTH_OR_REJECT__SET_USERNAME}.
     *     If {@code doNameConnection}, caller can get the exact-case username from {@link Connection#getData()};
     *     otherwise {@link SOCDBHelper#getUser(String)} must be called.
     * <LI> If client is too old, this method sends
     *     {@link SOCStatusMessage}({@link SOCStatusMessage#SV_NAME_NOT_FOUND SV_NAME_NOT_FOUND})
     *     and does not call {@code authCallback}.
     *</UL>
     *<P>
     * Before v1.2.00, this method had fewer possible status combinations and returned a single result instead
     * of passing a set of flag bits into {@code authCallback}. v1.2.00 also inlines {@code authenticateUser(..)} into
     * this method, its only caller.
     *
     * @param c  Client's connection
     * @param msgUser  Client username (nickname) to validate and authenticate; will be {@link String#trim() trim()med}.
     *     Ignored if connection is already authenticated
     *     ({@link Connection#getData() c.getData()} != {@code null}).
     * @param msgPass  Password to supply to {@code SOCDBHelper.authenticateUserPassword(..), or "";
     *     will be {@link String#trim() trim()med}. If {@code msgUser} is in the optional DB, the trimmed
     *     {@code msgPass} must match their password there. If {@code msgPass != ""} but {@code msgUser} isn't found
     *     in the DB or there is no DB, rejects authentication.
     * @param cliVers  Client version, from {@link Connection#getVersion()}
     * @param doNameConnection  True if successful auth of an unnamed connection should have this method call
     *     {@link Connection#setData(String) c.setData(nickname)} and
     *     {@link #nameConnection(Connection, boolean) nameConnection(c, isTakingOver)}.
     *     <P>
     *     If using the optional user DB, {@code nickname} is queried from the database by case-insensitive search; see
     *     {@link SOCDBHelper#authenticateUserPassword(String, String, soc.server.database.SOCDBHelper.AuthPasswordRunnable)}.
     *     Otherwise {@code nickname} is {@code msgUser}.
     *     <P>
     *     For the usual connect sequence, callers will want {@code true}.  Some callers might want to check
     *     other things after this method and possibly reject the connection at that point; they will want
     *     {@code false}. Those callers must remember to call {@code c.setData(nickname)} and
     *     <tt>nameConnection(c, (0 != (result &amp; {@link #AUTH_OR_REJECT__TAKING_OVER})))</tt> themselves to finish
     *     authenticating a connection. They will also need to get the originally-cased nickname by
     *     calling {@link SOCDBHelper#getUser(String)}.
     * @param allowTakeover  True if the new connection can "take over" an older connection in response to the
     *     message it sent.  If true, the caller must be prepared to send all game info/channel info that the
     *     old connection had joined, so the new connection has full info to participate in them.
     * @param authCallback  Callback to make if authentication succeeds, or if {@code c} was already authenticated.
     *     Calls {@link AuthSuccessRunnable#success(Connection, int)} with the {@link #AUTH_OR_REJECT__OK}
     *     flag bit set, and possibly also {@link #AUTH_OR_REJECT__SET_USERNAME} and/or (only if
     *     {@code allowTakeover}) {@link #AUTH_OR_REJECT__TAKING_OVER}.
     *     <BR>
     *     <B>Threads:</B> This callback will always run on the {@link InboundMessageQueue}'s Treater thread.
     * @throws IllegalArgumentException if {@code authCallback} is null
     * @see #authOrRejectClientRobot(Connection, String, String, String)
     * @since 1.1.19
     */
    void authOrRejectClientUser
        (final Connection c, String msgUser, String msgPass, final int cliVers,
         final boolean doNameConnection, final boolean allowTakeover,
         final AuthSuccessRunnable authCallback)
        throws IllegalArgumentException
    {
        if (authCallback == null)
            throw new IllegalArgumentException("authCallback");

        if (c.getData() != null)
        {
            authCallback.success(c, AUTH_OR_REJECT__OK);

            return;  // <---- Early return: Already authenticated ----
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
            return;
        }

        /**
         * check if a nickname is okay, and, if they're already logged in,
         * whether a new replacement connection can "take over" the existing one.
         */
        final int nameTimeout = checkNickname(msgUser, c, (msgPass != null) && (msgPass.length() > 0), false);
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
                return;
            }
        } else if (nameTimeout == -2)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NAME_NOT_ALLOWED, cliVers,
                     c.getLocalized("account.auth.nickname_not_allowed")));  // "This nickname is not allowed."
            return;
        } else if (nameTimeout <= -1000)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                     checkNickname_getVersionText(-nameTimeout)));
            return;
        } else if (nameTimeout > 0)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                     (allowTakeover) ? checkNickname_getRetryText(nameTimeout) : MSG_NICKNAME_ALREADY_IN_USE));
            return;
        }

        /**
         * account and password required?
         */
        if (getConfigBoolProperty(PROP_JSETTLERS_ACCOUNTS_REQUIRED, false))
        {
            if (msgPass.length() == 0)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_PW_REQUIRED, cliVers,
                         "This server requires user accounts and passwords."));
                return;
            }

            // Assert: msgPass isn't "".
            // authenticateUserPassword queries db and requires an account there when msgPass is not "".
        }

        if (msgPass.length() > SOCAuthRequest.PASSWORD_LEN_MAX)
        {
            final String txt  // I18N TODO: Check client; might already substitute text based on SV value
                = /*I*/"Incorrect password for '" + msgUser + "'." /*18N*/;
            c.put(SOCStatusMessage.toCmd(SOCStatusMessage.SV_PW_WRONG, c.getVersion(), txt));
            return;
        }

        /**
         * password check new connection from optional database, if not done already and if possible
         */
        try
        {
            final String msgUserName = msgUser;
            final boolean takingOver = isTakingOver;
            SOCDBHelper.authenticateUserPassword
                (msgUser, msgPass, new SOCDBHelper.AuthPasswordRunnable()
                {
                    public void authResult(final String dbUserName, final boolean hadDelay)
                    {
                        // If no DB: If msgPass is "" then dbUserName is msgUser, else is null

                        if (inQueue.isCurrentThreadTreater())
                            authOrRejectClientUser_postDBAuth
                                (c, msgUserName, dbUserName, cliVers,
                                 doNameConnection, takingOver, authCallback, hadDelay);
                        else
                            inQueue.post(new Runnable()
                            {
                                public void run()
                                {
                                    authOrRejectClientUser_postDBAuth
                                        (c, msgUserName, dbUserName, cliVers,
                                         doNameConnection, takingOver, authCallback, hadDelay);
                                }
                            });
                    }
                });;
        }
        catch (SQLException sqle)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_PROBLEM_WITH_DB, c.getVersion(),
                     "Problem connecting to database, please try again later."));

            return;  // <---- Early return: DB problem ----
        }
    }

    /**
     * After client user/password auth succeeds or fails, take care of the rest of
     * {@link #authOrRejectClientUser(Connection, String, String, int, boolean, boolean, AuthSuccessRunnable)}.
     * See that method's javadoc for most parameters.
     *<P>
     * That method also ensures this method and {@code authCallback} run in the Treater thread; see
     * {@link Server#inQueue inQueue}.{@link InboundMessageQueue#isCurrentThreadTreater() isCurrentThreadTreater()}.
     *
     * @param hadDelay  If true, this callback has been delayed by {@code BCrypt} calculations;
     *     otherwise it's an immediate callback (user not found, password didn't use BCrypt hashing)
     * @since 1.2.00
     */
    private void authOrRejectClientUser_postDBAuth
        (final Connection c, final String msgUser, final String authUsername,
         final int cliVers, final boolean doNameConnection, final boolean isTakingOver,
         final AuthSuccessRunnable authCallback, final boolean hadDelay)
    {
        if (authUsername == null)
        {
            // Password too long, or user found in database but password incorrect

            final String txt  // I18N TODO: Check client; might already substitute text based on SV value
                = /*I*/"Incorrect password for '" + msgUser + "'." /*18N*/;
            final String msg = SOCStatusMessage.toCmd(SOCStatusMessage.SV_PW_WRONG, c.getVersion(), txt);
            if (hadDelay)
                c.put(msg);
            else
                // TODO consider timing actual delay of BCrypt calcs & use that
                replyAuthTimer.schedule
                    (new TimerTask()
                     {
                        public void run() { c.put(msg); }
                     }, 350 + rand.nextInt(250));  // roughly same range as DBH.testBCryptSpeed

            return;  // <---- Early return: Password auth failed ----
        }

        final boolean mustSetUsername = ! authUsername.equals(msgUser);
        if (mustSetUsername && (cliVers < 1200))
        {
            // Case differs: must reject if client too old for SOCStatusMessage.SV_OK_SET_NICKNAME
            final String msg = SOCStatusMessage.toCmd
                (SOCStatusMessage.SV_NAME_NOT_FOUND, cliVers,
                 "Nickname is case-sensitive: Use " + authUsername);
            if (hadDelay)
                c.put(msg);
            else
                replyAuthTimer.schedule
                    (new TimerTask()
                     {
                        public void run() { c.put(msg); }
                     }, 350 + rand.nextInt(250));

            return;  // <---- Early return: Client can't change nickname case ----
        }

        /**
         * Now that everything's validated, name this connection/user/player.
         * If isTakingOver, also copies their current game/channel count.
         */
        if (doNameConnection)
        {
            c.setData(authUsername);
            nameConnection(c, isTakingOver);
        }

        int ret = AUTH_OR_REJECT__OK;
        if (isTakingOver)
            ret |= AUTH_OR_REJECT__TAKING_OVER;
        if (mustSetUsername)
            ret |= AUTH_OR_REJECT__SET_USERNAME;

        authCallback.success(c, ret);
    }

    /**
     * Is this username on the {@link #databaseUserAdmins} list?
     * @param uname  Username to check; if null, returns false.
     *     If supported by DB schema version, this check is case-insensitive.
     * @return  True only if list != {@code null} and the user is on the list
     * @since 1.1.20
     */
    boolean isUserDBUserAdmin(String uname)
    {
        if ((uname == null) || (databaseUserAdmins == null))
            return false;

        // Check if uname's on the user admins list; this check is also in createAccount.
        if (SOCDBHelper.getSchemaVersion() >= SOCDBHelper.SCHEMA_VERSION_1200)
            uname = uname.toLowerCase(Locale.US);

        return databaseUserAdmins.contains(uname);
    }

    /**
     * Set client's version and locale, and check against minimum required version {@link #CLI_VERSION_MIN}.
     * If version is too low, send {@link SOCRejectConnection REJECTCONNECTION}
     * and call {@link #removeConnection(Connection, boolean)}.
     * If we haven't yet sent the game list, send now.
     * If we've already sent the game list, send changes based on true version.
     *<P>
     * Along with the game list, the client will need to know the game option info.
     * This is sent when the client asks (after VERSION) for {@link SOCGameOptionGetInfos GAMEOPTIONGETINFOS}.
     * Game options are sent after client version is known, so the list of
     * sent options is based on client version.
     *<P>
     *<B>I18N:</B> If client doesn't send a locale string, the default locale {@code en_US} is used.
     * Robot clients will get the default locale and localeStr here; those will be cleared to {@code null} by
     * {@link #authOrRejectClientRobot(Connection, String, String, String)} when the bot sends {@link SOCImARobot}.
     *<P>
     *<b>Locks:</b> To set the version, will synchronize briefly on {@link Server#unnamedConns unnamedConns}.
     * If {@link Connection#getVersion() c.getVersion()} is already == cvers,
     * don't bother to lock and set it.
     *<P>
     * Package access (not private) is strictly for use of {@link SOCServerMessageHandler}
     * and {@link SOCClientData.SOCCDCliVersionTask#run()}.
     *
     * @param c     Client's connection
     * @param cvers Version reported by client, or assumed version if no report
     * @param cfeats  Optional features reported by client, or null if none given
     *     (was added to {@link SOCVersion} message in 2.0.00)
     * @param clocale  Locale reported by client, or null if none given
     *     (was added to {@link SOCVersion} message in 2.0.00)
     * @param isKnown Is this the client's definite version, or just an assumed one?
     *     Affects {@link Connection#isVersionKnown() c.isVersionKnown}.
     *     Can set the client's known version only once; a second "known" call with
     *     a different cvers will be rejected.
     * @return True if OK, false if rejected
     */
    boolean setClientVersSendGamesOrReject
        (Connection c, final int cvers, String cfeats, String clocale, final boolean isKnown)
    {
        final int prevVers = c.getVersion();
        final boolean wasKnown = c.isVersionKnown();

        final SOCFeatureSet cfeatSet;
        if ((cfeats == null) && (cvers < SOCFeatureSet.VERSION_FOR_CLIENTFEATURES))
            cfeatSet = new SOCFeatureSet(true, false);  // default features for 1.x.xx client
        else
            cfeatSet = (cfeats != null) ? new SOCFeatureSet(cfeats) : null;

        // Check for limited features
        boolean hasLimitedFeats = false;
        int scenVers = (cfeatSet != null) ? cfeatSet.getValue(SOCFeatureSet.CLIENT_SCENARIO_VERSION, 0) : 0;
        if (scenVers > cvers)
            scenVers = cvers;

        if (cfeatSet == null)
        {
            hasLimitedFeats = true;
        }
        else if (! (cfeatSet.isActive(SOCFeatureSet.CLIENT_6_PLAYERS)
                    && cfeatSet.isActive(SOCFeatureSet.CLIENT_SEA_BOARD)))
        {
            hasLimitedFeats = true;
        }
        else
        {
            hasLimitedFeats = (scenVers != cvers) && (scenVers < SOCScenario.ALL_KNOWN_SCENARIOS_MIN_VERSION);
            // If scenVers >= that MIN_VERSION, there should be no new scenario-related info missing from it
        }

        // Store this client's features
        SOCClientData scd = (SOCClientData) c.getAppData();
        scd.feats = cfeatSet;
        scd.hasLimitedFeats = hasLimitedFeats;
        scd.scenVersion = scenVers;

        if (hasLimitedFeats)
        {
            synchronized(unnamedConns)
            {
                limitedConns.add(c);
            }
        }

        // Message to send/log if client must be disconnected
        String rejectMsg = null;
        String rejectLogMsg = null;
        // Message to warn user, localized if possible, but continue the connection
        String warnMsg = null;

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
            warnMsg = "Sorry, cannot parse your locale.";  // i18n OK: We don't know client locale
            scd.localeStr = "en_US";  // fallback
            scd.locale = Locale.US;
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

            // make an effort to send reject message before closing socket
            final Connection rc = c;
            miscTaskTimer.schedule(new TimerTask()
            {
                public void run()
                {
                    removeConnection(rc, true);
                }
            }, 300);

            return false;  // <--- Early return: Rejected client ---
        }

        // Send game list?
        // Will check c.getAppData().hasSentGameList() flag.
        // prevVers is ignored unless already sent game list.
        gameList.sendGameList(c, prevVers);

        // Warn if debug commands are allowed.
        // This will be displayed in the client's status line (v1.1.17 and newer).
        if (allowDebugUser)
        {
            StringBuilder txt = new StringBuilder(c.getLocalized("member.welcome.debug"));  // "Debugging is On."
            txt.append(' ');
            if (warnMsg != null)
            {
                txt.append(warnMsg);
                txt.append(' ');
            }
            txt.append(c.getLocalized("member.welcome"));  // "Welcome to Java Settlers of Catan!"
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_OK_DEBUG_MODE_ON, cvers, txt.toString()));
        }
        else if (warnMsg != null)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_OK, cvers, warnMsg));
        }

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
     * Handle robot authentication (the "I'm a robot" message).
     * Robots send their {@link SOCVersion} before sending that message.
     * Their version is checked here (from {@link Connection#getVersion() c.getVersion()}),
     * must equal server's version. For stability and control, the cookie contents sent by the bot must
     * match this server's {@link #robotCookie}.
     *<P>
     * If authorization is succesful, this method will set the bot client's {@link SOCClientData#isRobot} flag.
     * Its {@link SOCClientData#locale} and {@link Connection#setI18NStringManager(SOCStringManager, String)}
     * are also cleared.
     *<P>
     * If this method returns sucessful auth, caller must send bot tuning parameters to the bot from
     * {@link SOCDBHelper#retrieveRobotParams(String, boolean) SOCDBHelper.retrieveRobotParams(botName, true)}.
     *<P>
     * If a bot is rejected, returns a disconnect reason to send to the bot client;
     * returns {@code null} if accepted.  If the returned reason is
     * {@link #MSG_NICKNAME_ALREADY_IN_USE}, caller should send a {@link SOCStatusMessage}
     * ({@link SOCStatusMessage#SV_NAME_IN_USE SV_NAME_IN_USE})
     * before sending the disconnect message.
     *<P>
     * Before connecting here, bot clients are named and started in {@link #setupLocalRobots(int, int)}.
     * Bot params can be stored in the database, see {@link SOCDBHelper#retrieveRobotParams(String, boolean)}:
     * Default bot params are {@link #ROBOT_PARAMS_SMARTER} if the robot name starts with "robot "
     * or {@link #ROBOT_PARAMS_DEFAULT} otherwise (starts with "droid ").
     *<P>
     * Sometimes a bot disconnects and quickly reconnects.  In that case
     * this method removes the disconnect/reconnect messages from
     * {@link Server#cliConnDisconPrintsPending} so they won't be printed.
     *<P>
     * Before v2.0.00 this method was {@code handleIMAROBOT}. v2.0.00 renamed that method and
     * also moved sending the responses to
     * {@link SOCServerMessageHandler#handleIMAROBOT(Connection, soc.message.SOCImARobot)}.
     *
     * @param c  the connection that sent the bot auth request; not null
     *     but {@link Connection#getData() c.getData()} should be null
     * @param botName  Robot name sent in message from {@code c}
     * @param cookie  robot cookie string sent in message from {@code c}
     * @param rbc  {@code c}'s robot brain class; built-in bots use {@link SOCImARobot#RBCLASS_BUILTIN}
     * @return {@code null} for successful authorization, or a failure string. See this method's
     *     javadocs for required messages to send to client on auth success or failure.
     * @throws NullPointerException if {@code c} is {@code null}
     * @see #authOrRejectClientUser(Connection, String, String, int, boolean, boolean, AuthSuccessRunnable)
     * @since 2.0.00
     */
    final String authOrRejectClientRobot
        (final Connection c, final String botName, final String cookie, final String rbc)
        throws NullPointerException
    {
        /**
         * Check that client hasn't already auth'd, as a human or bot
         */
        if (c.getData() != null)
        {
            System.out.println("Rejected robot " + botName + ": Client sent authorize already");

            return "Client has already authorized.";  // <---- Early return: Already authenticated ----
        }

        /**
         * Check the cookie given by this bot.
         */
        if ((robotCookie != null) && ! robotCookie.equals(cookie))
        {
            System.out.println("Rejected robot " + botName + ": Wrong cookie");

            return "Cookie contents do not match the running server.";
                // <--- Early return: Robot client didn't send our cookie value ---
        }

        /**
         * Check the reported version; if none, assume 1000 (1.0.00)
         */
        final int srvVers = Version.versionNumber();
        int cliVers = c.getVersion();
        final boolean isBuiltIn = (rbc == null)
            || (rbc.equals(SOCImARobot.RBCLASS_BUILTIN));
        if (isBuiltIn)
        {
            if (cliVers != srvVers)
            {
                System.out.println("Rejected robot " + botName + ": Version "
                    + cliVers + " does not match server version");
                String rejectMsg = "Sorry, robot client version does not match, version number "
                    + Version.version(srvVers) + " is required.";

                return rejectMsg;  // <--- Early return: Robot client too old ---
            } else {
                System.out.println("Robot arrived: " + botName + ": built-in type");
            }
        } else {
            System.out.println("Robot arrived: " + botName + ": type " + rbc);
        }

        /**
         * Check that the nickname is ok
         */
        if (0 != checkNickname(botName, c, false, true))
        {
            printAuditMessage
                (null, "Robot login attempt, name already in use or bad", botName, null, c.host());

            return MSG_NICKNAME_ALREADY_IN_USE;  // <--- Early return: Name in use ---
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

        //
        // add this connection to the robot list
        //
        c.setData(botName);
        c.setHideTimeoutMessage(true);
        SOCClientData scd = (SOCClientData) c.getAppData();
        scd.isRobot = true;
        scd.isBuiltInRobot = isBuiltIn;
        if (! isBuiltIn)
            scd.robot3rdPartyBrainClass = rbc;
        synchronized(robots)
        {
            robots.addElement(c);
            if (! isBuiltIn)
                robots3p.add(c);
        }

        scd.locale = null;  // bots don't care about message text contents
        scd.localeStr = null;
        c.setI18NStringManager(null, null);

        super.nameConnection(c, false);

        return null;  // accepted: no rejection reason string
    }

    /**
     * Remove a connection from the system.
     *<P>
     * After calling parent method {@link Server#removeConnection(Connection, boolean)},
     * cleans up any {@code SOCServer}-specific connection list info.
     *<P>
     * Description from parent method:
     *<BR>
     * {@inheritDoc}
     * @since 2.0.00
     */
    @Override
    public void removeConnection(final Connection c, final boolean doCleanup)
    {
        super.removeConnection(c, doCleanup);

        synchronized(unnamedConns)
        {
            limitedConns.remove(c);
        }
    }

    /**
     * Check username/password and create new game, or join game.
     * Called by {@link SOCServerMessageHandler}.handleJOINGAME and handleNEWGAMEWITHOPTIONSREQUEST.
     *<P>
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
     *  <LI> send JOINGAMEAUTH to requesting client, via {@link GameHandler#joinGame(SOCGame, Connection, boolean, boolean)}
     *  <LI> send game status details to requesting client, via {@link GameHandler#joinGame(SOCGame, Connection, boolean, boolean)}
     *       -- If the game is already in progress, this will include all pieces on the board, and the rest of game state.
     *</UL>
     *
     * @param c connection requesting the game, must not be null
     * @param msgUser username of client in message. Must pass {@link SOCMessage#isSingleLineAndSafe(String)}
     *                  and be at most {@link #PLAYER_NAME_MAX_LENGTH} characters.
     *                  Calls {@link String#trim() msgUser.trim()} before checking length.
     * @param msgPass password of client in message; will be {@link String#trim() trim()med}.
     * @param gameName  name of game to create/join. Must pass {@link SOCMessage#isSingleLineAndSafe(String)}
     *                  and be at most {@link SOCGameList#GAME_NAME_MAX_LENGTH} characters.
     *                  Calls {@link String#trim() gameName.trim()} before checking length.
     *                  Game name {@code "*"} is also rejected to avoid conflicts with admin commands.
     * @param gameOpts  if game has options, contains {@link SOCGameOption} to create new game; if not null, will not join an existing game.
     *                  Will validate and adjust by calling
     *                  {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean)}
     *                  with <tt>doServerPreadjust</tt> true.
     *
     * @since 1.1.07
     */
    void createOrJoinGameIfUserOK
        (Connection c, String msgUser, String msgPass,
         String gameName, final Map<String, SOCGameOption> gameOpts)
    {
        System.err.println("L4885 createOrJoinGameIfUserOK at " + System.currentTimeMillis());
        if (gameName != null)
            gameName = gameName.trim();
        final int cliVers = c.getVersion();

        if (c.getData() != null)
        {
            createOrJoinGameIfUserOK_postAuth(c, cliVers, gameName, gameOpts, AUTH_OR_REJECT__OK);
        } else {
            /**
             * Check that the nickname is ok, check password if supplied; if not ok, sends a SOCStatusMessage.
             */
            if (msgUser != null)
                msgUser = msgUser.trim();
            if (msgPass != null)
                msgPass = msgPass.trim();

            final String gName = gameName;
            authOrRejectClientUser
                (c, msgUser, msgPass, cliVers, true, true,
                 new AuthSuccessRunnable()
                 {
                    public void success(Connection c, int authResult)
                    {
                        createOrJoinGameIfUserOK_postAuth(c, cliVers, gName, gameOpts, authResult);
                    }
                 });
        }
    }

    /**
     * After successful client user auth, take care of the rest of
     * {@link #createOrJoinGameIfUserOK(Connection, String, String, String, Map)}.
     * @since 1.2.00
     */
    private void createOrJoinGameIfUserOK_postAuth
        (final Connection c, final int cliVers, final String gameName,
         final Map<String, SOCGameOption> gameOpts, final int authResult)
    {
        final boolean isTakingOver = (0 != (authResult & AUTH_OR_REJECT__TAKING_OVER));

        /**
         * Check that the game name length is ok
         */
        // TODO I18N
        if (gameName.length() > SOCGameList.GAME_NAME_MAX_LENGTH)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWGAME_NAME_TOO_LONG, cliVers,
                     SOCStatusMessage.MSG_SV_NEWGAME_NAME_TOO_LONG + SOCGameList.GAME_NAME_MAX_LENGTH));
            // Please choose a shorter name; maximum length: 30

            return;  // <---- Early return ----
        }
        System.err.println("L4965 past user,pw check at " + System.currentTimeMillis());

        /**
         * If creating a new game, check game name format
         * and ensure they are below their max game count.
         * (Don't limit max games on the practice server.)
         */
        if (! gameList.isGame(gameName))
        {
            if (((strSocketName == null) || ! strSocketName.equals(PRACTICE_STRINGPORT))
                && (CLIENT_MAX_CREATE_GAMES >= 0)
                && (CLIENT_MAX_CREATE_GAMES <= ((SOCClientData) c.getAppData()).getCurrentCreatedGames()))
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NEWGAME_TOO_MANY_CREATED, cliVers,
                         SOCStatusMessage.MSG_SV_NEWGAME_TOO_MANY_CREATED + Integer.toString(CLIENT_MAX_CREATE_GAMES)));
                // Too many of your games still active; maximum: 5

                return;  // <---- Early return ----
            }

            if ( (! SOCMessage.isSingleLineAndSafe(gameName))
                 || "*".equals(gameName))
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NEWGAME_NAME_REJECTED, cliVers,
                         SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED));
                // "This name is not permitted, please choose a different name."

                return;  // <---- Early return ----
            }

            if (SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher(gameName).matches())
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NEWGAME_NAME_REJECTED, cliVers,
                         SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED_DIGITS_OR_PUNCT));
                // "A name with only digits or punctuation is not permitted, please add a letter."

                return;  // <---- Early return ----
            }
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
            // If has game opt "VP" but boolean part is false, use server default instead.

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
            if (0 != (authResult & SOCServer.AUTH_OR_REJECT__SET_USERNAME))
                c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_OK_SET_NICKNAME,
                     c.getData() + SOCMessage.sep2_char +
                     c.getLocalized("member.welcome")));  // "Welcome to Java Settlers of Catan!"

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
                List<SOCGame> allConnGames = gameList.memberGames(c, gameName);
                if (allConnGames.size() == 0)
                {
                    c.put(SOCStatusMessage.toCmd(SOCStatusMessage.SV_OK,
                            /*I*/"You've taken over the connection, but aren't in any games."/*18N*/ ));
                } else {
                    // Send list backwards: requested game will be sent last.
                    for (int i = allConnGames.size() - 1; i >= 0; --i)
                        joinGame(allConnGames.get(i), c, false, true);
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
        } catch (MissingResourceException e)
        {
            // Let them know they can't join or create it because
            // client is missing an optional feature the game needs.
            // Does not need I18N, because client v2.0.00 and newer will parse this text and
            // show a localized message instead of the raw status text.
            final String verb = (gameList.isGame(gameName)) ? "join" : "create";  // I18N OK
            final String feats = e.getKey();  // semicolon-separated (';')
            c.put(SOCStatusMessage.toCmd
              (SOCStatusMessage.SV_GAME_CLIENT_FEATURES_NEEDED, cliVers,
                "Cannot " + verb + "; this client is incompatible with features of the game"
                + SOCMessage.sep2_char + gameName
                + SOCMessage.sep2_char + feats));
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
     * Later as these games end, the server will start new games as long as
     * {@link #numRobotOnlyGamesRemaining} &gt; 0 at the time.
     *<P>
     * Starts 2 games here unless {@link #PROP_JSETTLERS_BOTS_BOTGAMES_PARALLEL} is set.
     *<P>
     * <B>Locks:</b> May or may not have {@link SOCGameList#takeMonitor()} when calling;
     * see {@code hasGameListMonitor} parameter.  If not already held, this method takes and releases that monitor.
     *
     * @param wasGameDestroyed  True if caller has just destroyed a game and should start 1 more to replace it
     * @param hasGameListMonitor  True if caller holds the {@link SOCGameList#takeMonitor()} lock already
     * @see #PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL
     * @since 2.0.00
     */
    private void startRobotOnlyGames(final boolean wasGameDestroyed, final boolean hasGameListMonitor)
    {
        int nParallel;
        if (wasGameDestroyed)
        {
            nParallel = 1;
        } else {
            nParallel = getConfigIntProperty(PROP_JSETTLERS_BOTS_BOTGAMES_PARALLEL, 2);
            if (nParallel == 0)
                nParallel = numRobotOnlyGamesRemaining;
        }

        for (int i = 0; (i < nParallel) && (numRobotOnlyGamesRemaining > 0); ++i)
        {
            String gaName = "~botsOnly~" + numRobotOnlyGamesRemaining;

            SOCGame newGame = createGameAndBroadcast
                (null, gaName, SOCGameOption.getAllKnownOptions(), Version.versionNumber(), true, hasGameListMonitor);

            if (newGame != null)
            {
                --numRobotOnlyGamesRemaining;

                System.out.println("Started bot-only game: " + gaName);
                newGame.setGameState(SOCGame.READY);
                if (! readyGameAskRobotsJoin(newGame, null, 0))
                {
                    System.out.println("Bot-only game " + gaName + ": Not enough bots can join, not starting");
                    newGame.setGameState(SOCGame.OVER);
                }
            } else {
                // TODO game name existed
            }
        }
    }

    /**
     * Fill all the unlocked empty seats with robots, by asking them to join.
     * Builds a set of the {@link Connection}s of robots asked to join,
     * and adds it to the {@code robotJoinRequests} table.
     * Game state should be {@code READY}.
     *<P>
     * At most {@link SOCGame#getAvailableSeatCount()} robots will
     * be asked. If third-party bots are connected to the server,
     * optional property {@link #PROP_JSETTLERS_BOTS_PERCENT3P} can
     * set a goal for the minimum percentage of third-party bots in
     * the game; see its javadoc.
     *<P>
     * Called by {@link SOCServerMessageHandler#handleSTARTGAME(Connection, SOCStartGame) handleSTARTGAME},
     * {@link #resetBoardAndNotify(String, int) resetBoardAndNotify}.
     *<P>
     * Once the robots have all responded (from their own threads/clients)
     * and joined up, the game can begin.
     *<P>
     * Before v1.1.00, this method was part of {@code handleSTARTGAME}.
     *
     * @param ga  Game to ask robots to join
     * @param robotSeats If null, robots are randomly selected. May be non-null for a board reset.
     *                   If non-null, a MAXPLAYERS-sized array of Connections.
     *                   Any vacant non-locked seat, with index i,
     *                   is filled with the robot whose connection is robotSeats[i].
     *                   Other indexes should be null, and won't be used.
     * @param maxBots Maximum number of bots to add, or 0 to fill all empty seats
     * @return  True if some bots were found and invited, false if none could be invited
     * @throws IllegalStateException if {@link SOCGame#getGameState() ga.gamestate} is not {@link SOCGame#READY},
     *         or if {@link SOCGame#getClientVersionMinRequired()} is
     *         somehow newer than server's version (which is assumed to be robots' version).
     * @throws IllegalArgumentException if robotSeats is not null but wrong length,
     *           or if a robotSeat element is null but that seat wants a robot (vacant non-locked).
     * @since 1.1.00
     */
    boolean readyGameAskRobotsJoin(SOCGame ga, Connection[] robotSeats, final int maxBots)
        throws IllegalStateException, IllegalArgumentException
    {
        if (ga.getGameState() != SOCGame.READY)
            throw new IllegalStateException("SOCGame state not READY: " + ga.getGameState());

        if (ga.getClientVersionMinRequired() > Version.versionNumber())
            throw new IllegalStateException("SOCGame min version somehow newer than server and robots, it's "
                    + ga.getClientVersionMinRequired());

        // These bots will be asked to join.
        // Key = bot Connection, value = seat number as {@link Integer} like in SOCServer.robotJoinRequests
        Hashtable<Connection, Object> robotsRequested = null;

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

        final int nRobotsAvailable = robots.size();
        final String gname = ga.getName();
        final Map<String, SOCGameOption> gopts = ga.getGameOptions();
        final boolean gameHasLimitedFeats = (ga.getClientFeaturesRequired() != null);
        int seatsOpen = ga.getAvailableSeatCount();
        if ((maxBots > 0) && (maxBots < seatsOpen))
            seatsOpen = maxBots;

        int idx = 0;
        Connection[] robotSeatsConns = new Connection[ga.maxPlayers];

        for (int i = 0; (i < ga.maxPlayers) && (seatsOpen > 0); i++)
        {
            if (ga.isSeatVacant(i) && (ga.getSeatLock(i) == SOCGame.SeatLockState.UNLOCKED))
            {
                /**
                 * fetch a robot player; game will start when all bots have arrived.
                 * Similar to SOCGameHandler.findRobotAskJoinGame (called from SGH.leaveGame),
                 * where a player has left and must be replaced by a bot.
                 */
                if (idx < nRobotsAvailable)
                {
                    messageToGameKeyed(ga, true, "member.bot.join.fetching");  // "Fetching a robot player..."

                    Connection robotConn;
                    if (robotSeats != null)
                    {
                        robotConn = robotSeats[i];
                        if (robotConn == null)
                            throw new IllegalArgumentException("robotSeats[" + i + "] was needed but null");
                    }
                    else
                    {
                        do
                        {
                            robotConn = robots.get(robotIndexes[idx]);
                            if (gameHasLimitedFeats &&
                                ! ga.canClientJoin(((SOCClientData) (robotConn.getAppData())).feats))
                            {
                                // try the next bot instead
                                robotConn = null;
                                ++idx;
                            }
                        } while ((robotConn == null) && (idx < nRobotsAvailable));

                        if (robotConn == null)
                            break;
                    }

                    idx++;
                    --seatsOpen;
                    robotSeatsConns[i] = robotConn;

                    /**
                     * record the request
                     */
                    if (robotsRequested == null)
                        robotsRequested = new Hashtable<Connection, Object>();
                    robotsRequested.put(robotConn, Integer.valueOf(i));
                }
            }
        }

        if (robotsRequested != null)
        {
            // request third-party bots, if available and wanted
            final int reqPct3p = getConfigIntProperty(PROP_JSETTLERS_BOTS_PERCENT3P, 0);
            if (reqPct3p > 0)
                readyGameAskRobotsMix3p(ga, reqPct3p, robotsRequested, robotSeatsConns);

            // we know robotRequests isn't empty,
            // so add to the request table
            robotJoinRequests.put(gname, robotsRequested);

            // now, make the requests
            for (int i = 0; i < ga.maxPlayers; ++i)
            {
                if (robotSeatsConns[i] != null)
                {
                    D.ebugPrintln("@@@ JOIN GAME REQUEST for " + robotSeatsConns[i].getData());
                    robotSeatsConns[i].put(SOCBotJoinGameRequest.toCmd(gname, i, gopts));
                }
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * While readying a game in {@link #readyGameAskRobotsJoin(SOCGame, Connection[], int)},
     * try to adjust the mix of requested bots as needed when third-party bots are wanted.
     * Third-party bots will be randomly picked and swapped into {@code robotsRequested} and {@code robotSeatsConns}
     * until enough are added or no more are available to add.
     *<P>
     * <B>Note:</B> Currently treats {@code reqPct3p} as a minimum percentage; third-party
     * bots are only added to, not removed from, the bots requested for the new game.
     *
     * @param ga  Game to ask robots to join
     * @param reqPct3p  Requested third-party bot percentage, from {@link #PROP_JSETTLERS_BOTS_PERCENT3P}
     * @param robotsRequested  Set of randomly-selected bots joining the game; third-party bots may be
     *        swapped into here. Key = bot Connection, value = seat number as {@link Integer}
     *        like in {@link #robotJoinRequests}
     * @param robotSeatsConns  Array of player positions (seats) to be occupied by bots; all non-null elements
     *        are bots in {@code robotsRequested}; third-party bots may be swapped into here
     * @since 2.0.00
     */
    private void readyGameAskRobotsMix3p
        (final SOCGame ga, final int reqPct3p,
         final Hashtable<Connection, Object> robotsRequested, final Connection[] robotSeatsConns)
    {
        // TODO this algorithm isn't elegant or very efficient

        final int numBotsReq = robotsRequested.size();
        final int num3pReq = Math.round((numBotsReq * reqPct3p) / 100f);
        int curr3pReq = 0;
        boolean[] curr3pSeat = new boolean[robotSeatsConns.length];
        for (int i = 0; i < robotSeatsConns.length; ++i)
        {
            if ((robotSeatsConns[i] != null)
                && ! ((SOCClientData) (robotSeatsConns[i].getAppData())).isBuiltInRobot)
            {
                ++curr3pReq;
                curr3pSeat[i] = true;
            }
        }

        // TODO handle reduction if too many 3p bots (curr3pReq > num3pReq)

        if (curr3pReq >= num3pReq)
            return;  // <--- Early return: Already the right minimum percentage ---

        // fill unused3p, the list of 3p bots which aren't already requested and in robotRequests
        List<Connection> unused3p;
        synchronized (robots3p)
        {
            unused3p = new ArrayList<Connection>(robots3p);
        }
        for (int i = 0; i < robotSeatsConns.length; ++i)
            if (curr3pSeat[i])
                unused3p.remove(robotSeatsConns[i]);

        // use random bots from unused3p in robotRequests and robotSeatsConns:
        final boolean gameHasLimitedFeats = (ga.getClientFeaturesRequired() != null);
        int nAdd = num3pReq - curr3pReq;
        while ((nAdd > 0) && ! unused3p.isEmpty())
        {
            // pick iNon, a non-3p bot seat index to remove
            int iNon = -1;
            int nSkip = (nAdd > 1) ? rand.nextInt(num3pReq - curr3pReq) : 0;
            for (int i = 0; i < robotSeatsConns.length; ++i)
            {
                if ((robotSeatsConns[i] != null) && ! curr3pSeat[i])
                {
                    if (nSkip == 0)
                    {
                        iNon = i;
                        break;
                    } else {
                        --nSkip;
                    }
                }
            }
            if (iNon == -1)
                return;  // <--- Early return: Non-3p bot seat not found ---

            // pick bot3p, an unused 3p bot to fill iNon:
            int s = unused3p.size();
            Connection bot3p = null;
            while ((bot3p == null) && (s > 0))
            {
                bot3p = unused3p.remove((s > 1) ? rand.nextInt(s) : 0);
                if (gameHasLimitedFeats && ! ga.canClientJoin(((SOCClientData) bot3p.getAppData()).feats))
                {
                    bot3p = null;
                    --s;
                }
            }
            if (bot3p == null)
                break;  // no more available bots

            // update structures
            Integer iObj = Integer.valueOf(iNon);
            synchronized(robotsRequested)
            {
                robotsRequested.remove(robotSeatsConns[iNon]);
                robotsRequested.put(bot3p, iObj);
            }
            robotSeatsConns[iNon] = bot3p;
            curr3pSeat[iNon] = true;

            --nAdd;
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
     * "Reset-board" request: Register one player's vote, and let game members know.
     * Calls {@link SOCGame#resetVoteRegister(int, boolean)}.
     * Check results so far from {@link SOCGame#getResetVoteResult()}.
     * If vote succeeded, go ahead and reset the game with {@link #resetBoardAndNotify(String, int)}.
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
     *  <LI> {@link Connection#getLocalized(String) c.getLocalized}({@code "gamescen.SC_WOND.n"})
     *      returns a string different than {@link #i18n_scenario_SC_WOND_desc}:
     *      This checks whether a fallback is being used because the client's locale has no scenario strings
     * </UL>
     * @param c  Client connection
     * @return  True if the client meets all the conditions listed above, false otherwise
     * @since 2.0.00
     */
    public static final boolean clientHasLocalizedStrs_gameScenarios(final Connection c)
    {
        final SOCClientData scd = (SOCClientData) c.getAppData();
        return
            scd.wantsI18N
            && ! i18n_scenario_SC_WOND_desc.equals(c.getLocalized("gamescen.SC_WOND.n"));
    }

    /**
     * Get localized strings for known {@link SOCScenario}s.  Assumes client locale has scenario strings:
     * Call {@link #clientHasLocalizedStrs_gameScenarios(Connection)} before calling this method.
     * Fills and returns a list with each {@code scKeys} key, scenario name, scenario description
     * from {@code c.getLocalized("gamescen." + scKey + ".n")} and {@code ("gamescen." + scKey + ".d")}.
     *
     * @param loc  Client's locale for StringManager i18n lookups.  This is passed instead of the client connection
     *    to simplify SOCPlayerClient's localizations before starting its practice server.
     * @param scKeys  Scenario keynames to localize, such as a {@link List} of keynames or the {@link Set}
     *    returned from {@link SOCScenario#getAllKnownScenarioKeynames()}.
     *    If {@code null}, this method will call {@link SOCScenario#getAllKnownScenarioKeynames()}.
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
        (String scKey, final SOCScenario sc, final Connection c, final boolean stringsOnly)
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

        final int cliVers = scd.scenVersion;
        if (cliVers < SOCScenario.VERSION_FOR_SCENARIOS)
        {
            scd.sentAllScenarioStrings = true;
            scd.sentAllScenarioInfo = true;

            return;  // <--- Client is too old or doesn't support scenarios ---
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
     * Handle "create account" request from a client, either creating the account
     * or rejecting the request. If called when ! {@link SOCDBHelper#isInitialized()},
     * rejects with {@link SOCStatusMessage#SV_ACCT_NOT_CREATED_ERR}.
     * Will check if the requesting connection {@code c} is authorized to create accounts
     * or if {@link SOCFeatureSet#SERVER_OPEN_REG} is active.
     * Sends {@link SOCStatusMessage} to {@code c} to report results.
     *<P>
     * Before v2.0.00, this method was {@code handleCREATEACCOUNT}.
     *
     * @param nn  the account nickname to create
     * @param pw  the new account's password; must not be null or ""
     * @param em  the new accout's contact email; optional, can use null or ""
     * @param c  the connection requesting the account creation.
     *     If the account is created, {@link Connection#host() c.host()} is written to the db
     *     as the requesting hostname.
     */
    final void createAccount
        (final String nn, final String pw, final String em, final Connection c)
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

        final String requester = c.getData();  // null if client isn't authenticated
        final Date currentTime = new Date();
        final boolean isOpenReg = features.isActive(SOCFeatureSet.SERVER_OPEN_REG);

        if ((databaseUserAdmins == null) && ! isOpenReg)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_ACCT_NOT_CREATED_DENIED, cliVers,
                     c.getLocalized("account.create.not_auth")));  // "Your account is not authorized to create accounts."

            printAuditMessage
                (requester,
                 "Requested jsettlers account creation, but no account admins list",
                 null, currentTime, c.host());

            return;
        }

        boolean isDBCountedEmpty = false;  // with null requester, did we query and find the users table is empty?
            // Not set if SERVER_OPEN_REG is active.

        // If client is not authenticated, does this server have open registration
        // or is an account required to create user accounts?
        if ((requester == null) && ! isOpenReg)
        {
            // SOCAccountClients older than v1.1.19 (VERSION_FOR_AUTHREQUEST, VERSION_FOR_SERVERFEATURES)
            // can't authenticate; all their user creation requests are anonymous (SERVER_OPEN_REG).
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
                              Version.version(SOCFeatureSet.VERSION_FOR_SERVERFEATURES))));
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
        final String userName = nn.trim();

        if (! SOCMessage.isSingleLineAndSafe(userName))
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWGAME_NAME_REJECTED, cliVers,
                     SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED));
            return;
        }

        //
        // Check if requester is on the user admins list; this check is also in isUserDBUserAdmin.
        //
        // If databaseUserAdmins != null, then requester != null because SERVER_OPEN_REG can't also be active.
        // If requester is null because db is empty, check new userName instead of requester name:
        // The first account created must be on the list in order to create further accounts.
        // If the db is empty when account client connects, server sends it SERVER_OPEN_REG so it won't require
        // user/password auth to create that first account; then requester == null, covered by isDBCountedEmpty.
        //
        if (databaseUserAdmins != null)
        {
            String chkName = (isDBCountedEmpty) ? userName : requester;
            if ((chkName != null) && (SOCDBHelper.getSchemaVersion() >= SOCDBHelper.SCHEMA_VERSION_1200))
                chkName = chkName.toLowerCase(Locale.US);

            if ((chkName == null) || ! databaseUserAdmins.contains(chkName))
            {
                // Requester not on user-admins list.

                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_ACCT_NOT_CREATED_DENIED, cliVers,
                         c.getLocalized("account.create.not_auth")));  // "Your account is not authorized to create accounts."

                printAuditMessage
                    (requester,
                     (isDBCountedEmpty)
                         ? "Requested jsettlers account creation, database is empty - first, create a user named in account admins list"
                         : "Requested jsettlers account creation, this requester not on account admins list",
                     null, currentTime, c.host());

                if (isDBCountedEmpty)
                    System.err.println
                        ("User requested new account but database is currently empty: Run SOCAccountClient to create account(s) named in the admins list.");
                    // probably don't need to also print databaseUserAdmins list contents here

                return;
            }
        }

        //
        // check if there's already an account with requested nickname
        //
        try
        {
            final String dbUserName = SOCDBHelper.getUser(userName);
            if (dbUserName != null)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                         c.getLocalized("account.create.already_exists", dbUserName)));
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
        boolean success = false, pwTooLong = false;

        try
        {
            success = SOCDBHelper.createAccount(userName, c.host(), pw, em, currentTime.getTime());
        }
        catch (IllegalArgumentException e)
        {
            pwTooLong = true;
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
            String errText = c.getLocalized
                ((pwTooLong)
                 ? "account.common.password_too_long"  // "That password is too long."
                 : "account.create.error");  // "Account not created due to error."
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_ACCT_NOT_CREATED_ERR, cliVers, errText));
        }
    }

    /**
     * Client has been approved to join game; send JOINGAMEAUTH and the entire state of the game to client.
     * Unless <tt>isTakingOver</tt>, announces {@link SOCJoinGame} client join event to other players.
     * Gets the game's handler and calls {@link GameHandler#joinGame(SOCGame, Connection, boolean, boolean)};
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
     * @see #connectToGame(Connection, String, Map)
     * @see #createOrJoinGameIfUserOK(Connection, String, String, String, Map)
     */
    private void joinGame(SOCGame gameData, Connection c, boolean isReset, boolean isTakingOver)
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
     * Sends sitting player their own data via {@link GameHandler#sitDown_sendPrivateInfo(SOCGame, Connection, int)}.
     * If game is waiting for robots to join, and sitting player is the last bot, start the game.
     *
     * @param ga     the game
     * @param c      the connection for the player
     * @param pn     which seat the player is taking
     * @param robot  true if this player is a robot
     * @param isReset Game is a board-reset of an existing game
     */
    void sitDown(final SOCGame ga, Connection c, int pn, boolean robot, boolean isReset)
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
                    ga.addPlayer(c.getData(), pn);
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
            SOCSitDown sitMessage = new SOCSitDown(gaName, c.getData(), pn, robot);
            messageToGame(gaName, sitMessage);

            D.ebugPrintln("*** sent SOCSitDown message to game ***");

            recordGameEvent(gaName, sitMessage);

            Hashtable<Connection, Object> requestedBots;
            if (! isReset)
            {
                requestedBots = robotJoinRequests.get(gaName);
            } else {
                requestedBots = null;  // Game already has all players from old game
            }

            final boolean willStartGame = (requestedBots != null) && requestedBots.isEmpty()
                && (ga.getGameState() < SOCGame.START1A);

            /**
             * if the request list is now empty, remove it from request tracking
             */
            if ((requestedBots != null) && requestedBots.isEmpty())
                robotJoinRequests.remove(gaName);

            /**
             * send all the private information
             * and (if applicable) prompt for discard or other decision
             */
            GameHandler hand = gameList.getGameTypeHandler(gaName);
            if (hand != null)
                hand.sitDown_sendPrivateInfo(ga, c, pn);

            /**
             * if the request list is now empty and the game hasn't started yet,
             * then start the game
             */
            if (willStartGame && (hand != null))
                hand.startGame(ga);    // <--- Everyone's here, start the game ---
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
     *    The call will be made from {@link SOCServerMessageHandler#handleLEAVEGAME_maybeGameReset_oldRobot(String)}.
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
     * @since 1.1.00
     */
    void resetBoardAndNotify(final String gaName, final int requestingPlayer)
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
            (reBoard.oldGameState < SOCGame.ROLL_OR_CARD) || (reBoard.oldGameState == SOCGame.OVER);

        /**
         * Player connection data:
         * - Humans are copied from old to new game
         * - Robots aren't copied to new game, must re-join
         */
        Connection[] huConns = reBoard.humanConns;
        Connection[] roConns = reBoard.robotConns;

        /**
         * Notify old game's players. (Humans and robots)
         *
         * 2a. Send ResetBoardAuth to each (like sending JoinGameAuth at new game).
         *    Humans will reset their copy of the game.
         *    Robots will leave the game, and soon will be requested to re-join.
         */
        final SOCResetBoardAuth resetMsg = new SOCResetBoardAuth(gaName, -1, requestingPlayer);
        final boolean hasOldClients = (reGame.clientVersionLowest < SOCResetBoardAuth.VERSION_FOR_BLANK_PLAYERNUM);
        for (int pn = 0; pn < reGame.maxPlayers; ++pn)
        {
            if (huConns[pn] != null)
            {
                final SOCResetBoardAuth rMsg;
                if (hasOldClients && (huConns[pn].getVersion() < SOCResetBoardAuth.VERSION_FOR_BLANK_PLAYERNUM))
                    rMsg = new SOCResetBoardAuth(gaName, pn, requestingPlayer);
                else
                    rMsg = resetMsg;
                messageToPlayer(huConns[pn], rMsg);
            }
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
        //  SOCServerMessageHandler.handleLEAVEGAME will take care of the reset,
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
    void resetBoardAndNotify_finish(SOCGameBoardReset reBoard, SOCGame reGame)
    {
        final boolean resetWithShuffledBots =
            (reBoard.oldGameState < SOCGame.ROLL_OR_CARD) || (reBoard.oldGameState == SOCGame.OVER);
        Connection[] huConns = reBoard.humanConns;

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
         * 5a. If no robots in new game, send to game as if someone else has
         *     clicked "start game", and set up state to begin game play.
         */
        if (! reBoard.hasRobots)
        {
            final GameHandler hand = gameList.getGameTypeHandler(reGame.getName());
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
            if (! readyGameAskRobotsJoin
                    (reGame, resetWithShuffledBots ? null : reBoard.robotConns, 0))
            {
                // Unlikely, since we were just playing this game with bots
                reGame.setGameState(SOCGame.OVER);
                final GameHandler hand = gameList.getGameTypeHandler(reGame.getName());
                if (hand != null)
                    handler.sendGameState(reGame);
                messageToGameKeyed(reGame, true, "member.bot.join.cantfind");  // "*** Can't find a robot! ***"
            }
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
     * record the winner and scores in the database.
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
        if (! getConfigBoolProperty(SOCDBHelper.PROP_JSETTLERS_DB_SAVE_GAMES, false))
            return;

        //D.ebugPrintln("allOriginalPlayers for "+ga.getName()+" : "+ga.allOriginalPlayers());
        if (! ((ga.getGameState() == SOCGame.OVER)
               && (ga.allOriginalPlayers() || ga.hasHumanPlayers())))
            return;

        try
        {
            final int gameSeconds = (int) (((System.currentTimeMillis() - ga.getStartTime().getTime())+500L) / 1000L);
            SOCDBHelper.saveGameScores(ga, gameSeconds);
        }
        catch (Exception e)
        {
            System.err.println("Error saving game scores in db: " + e);
        }
    }

    /**
     * Record events that happen during the game. This stub can be overridden.
     *<P>
     * Before v2.0.00 {@link event} was a String from {@link SOCMessage#toCmd()}.
     *
     * @param gameName   the name of the game
     * @param event      the event data
     */
    protected void recordGameEvent(String gameName, SOCMessage event)
    {
        /*
           FileWriter fw = (FileWriter)gameDataFiles.get(gameName);
           if (fw != null) {
           try {
           fw.write(event.toCmd()+"\n");
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
     * @see SOCGameListAtServer#GAME_TIME_EXPIRE_MINUTES
     * @see #checkForExpiredTurns(long)
     */
    public void checkForExpiredGames(final long currentTimeMillis)
    {
        List<String> expired = new ArrayList<String>();

        gameList.takeMonitor();

        // Warn 3 minutes earlier, because of coarse 5-minute granularity in SOCGameTimeoutChecker.run()
        long warn_ms = (3 + GAME_TIME_EXPIRE_WARN_MINUTES) * 60L * 1000L;

        try
        {
            for (SOCGame gameData : gameList.getGamesData())
            {
                if (gameData.isPractice)
                    continue;  // <--- Skip practice games, they don't expire ---

                long gameExpir = gameData.getExpiration();
                final boolean hasWarned = gameData.hasWarnedExpiration();

                // Start our text messages with ">>>" to mark as urgent to the client.

                if (hasWarned && (gameExpir <= currentTimeMillis))
                {
                    final String gameName = gameData.getName();
                    expired.add(gameName);
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

                    if (! hasWarned)
                        gameData.setWarnedExpiration();
                }
                else if ((currentTimeMillis - gameData.lastActionTime) > (GAME_TIME_EXPIRE_CHECK_MINUTES * 60 * 1000))
                {
                    // If game is idle since previous check, send keepalive ping to its clients
                    // so the network doesn't disconnect while all players are taking a break

                    messageToGame(gameData.getName(), new SOCServerPing(GAME_TIME_EXPIRE_CHECK_MINUTES * 60));
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
        //    Assumes the list will be short, so the game list monitor take/release overhead will be acceptable.
        //
        for (String ga : expired)
            destroyGameAndBroadcast(ga, "checkForExpired");
    }

    /**
     * Check all games for robot turns that have expired, and end that turn,
     * or stop waiting for non-current-player robot actions (discard picks, etc).
     * Robot turns may end from inactivity or from an illegal placement.
     * Checks each game's {@link SOCGame#lastActionTime} field, and calls
     * {@link GameHandler#endTurnIfInactive(SOCGame, long)} if the
     * last action is older than {@link #ROBOT_FORCE_ENDTURN_SECONDS}
     * (or for third-party bots, {@link #PROP_JSETTLERS_BOTS_TIMEOUT_TURN}).
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

        final long inactiveTime = currentTimeMillis - (ROBOT_FORCE_ENDTURN_SECONDS * 1000L),
                   inactiveTimeStubborn = currentTimeMillis - (ROBOT_FORCE_ENDTURN_STUBBORN_SECONDS * 1000L);
        final long inactiveTime3p;  // if set, longer time for 3rd-party bot players
        {
            final int timeout3p = getConfigIntProperty(PROP_JSETTLERS_BOTS_TIMEOUT_TURN, 0);
            inactiveTime3p = (timeout3p <= ROBOT_FORCE_ENDTURN_SECONDS)
                ? 0
                : currentTimeMillis - (timeout3p * 1000L);
        }

        try
        {
            for (SOCGame ga : gameList.getGamesData())
            {
                // lastActionTime is a recent time, or might be 0 to force end
                long lastActionTime = ga.lastActionTime;
                if (lastActionTime > ((ga.isCurrentPlayerStubbornRobot() ? inactiveTimeStubborn : inactiveTime)))
                    continue;

                if (ga.getGameState() >= SOCGame.OVER)
                {
                    // nothing to do.
                    // bump out that time, so we don't see
                    // it again every few seconds
                    ga.lastActionTime
                        += (SOCGameListAtServer.GAME_TIME_EXPIRE_MINUTES * 60 * 1000);
                    continue;
                }

                final int cpn = ga.getCurrentPlayerNumber();
                if (cpn == -1)
                    continue;  // not started yet

                if ((inactiveTime3p != 0) && (lastActionTime > inactiveTime3p))
                {
                    final SOCPlayer pl = ga.getPlayer(cpn);
                    if (pl.isRobot() && ! pl.isBuiltInRobot())
                        continue;  // third-party robot player has more time
                }

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
     * handled in {@link #initSocServer(String, String)}.
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
        boolean hasArgProblems = false;  // warn about each during parsing, instead of returning after first one
        boolean doPrintOptions = false;  // if true, call printGameOptions() at end of method

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
                    {
                        System.err.println("Missing required option name/value after " + arg);
                        System.err.println();
                    }
                    hasArgProblems = true;
                    doPrintOptions = true;
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
                            doPrintOptions = true;
                        }
                    }

                    if (! ok)
                        hasArgProblems = true;
                }
            }
            else if (arg.equals("-t") || arg.equalsIgnoreCase("--test-config"))
            {
                argp.put(PROP_JSETTLERS_TEST_VALIDATE__CONFIG, "y");
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
                hasArgProblems = true;
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

        if (doPrintOptions)
            printGameOptions();

        if (hasArgProblems)
            return null;

        // Done parsing.
        return argp;
    }

    /**
     * Set static game option defaults from any {@code jsettlers.gameopt.*} server properties found.
     * Option keynames are case-insensitive past that prefix.
     * See {@link #PROP_JSETTLERS_GAMEOPT_PREFIX} for expected syntax.
     * Calls {@link #parseCmdline_GameOption(SOCGameOption, String, HashMap)} for each one found,
     * to set its current value in {@link SOCGameOptions}'s static set of known opts.
     *<P>
     * If {@code pr} contains a {@link SOCScenario} keyname (value of game option {@code "SC"}),
     * this method sets that as the default scenario but won't apply that scenario's game options
     * to the default values. Note that an unknown scenario keyname is not an error here;
     * {@link #init_checkScenarioOpts(Map, boolean, String, String, String)}
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

        String dbUname = null;
        try
        {
            dbUname = SOCDBHelper.getUser(uname);
            if (dbUname == null)
            {
                System.err.println("pw-reset user " + uname + " not found in database.");
                return;
            }
        } catch (SQLException e) {
            System.err.println("Error while querying user " + uname + ": " + e.getMessage());
            return;
        }

        System.out.println("Resetting password for " + dbUname + ".");

        StringBuilder pw1 = null;
        boolean hasNewPW = false;
        for (int tries = 0; tries < 3; ++tries)
        {
            if (tries > 0)
                System.out.println("Passwords do not match; try again.");

            pw1 = readPassword("Enter the new password:");
            if ((pw1 == null) || (pw1.length() == 0))
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
            SOCDBHelper.updateUserPassword(dbUname, pw1.toString());
            clearBuffer(pw1);
            utilityModeMessage = "The password was changed";
        } catch (IllegalArgumentException e) {
            System.err.println("Password was too long, max length is " + SOCDBHelper.getMaxPasswordLength());
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
     *   Audit: Requested jsettlers account creation, this requester not on account admins list:
     *      '{@code req}' from {@code reqHost} at {@code at}
     *
     * @param req  Requesting user, or {@code null} if unknown
     * @param msg  Message text
     * @param obj  Object affected by the action, or {@code null} if none
     * @param at   Timestamp, or {@code null} to use current time
     * @param reqHost  Requester client's hostname, from {@link Connection#host()}
     * @since 1.1.20
     */
    void printAuditMessage
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
            System.err.println("       -t or --test-config: validate server and DB config, then exit");
            System.err.println("       -o or --option name=value : set per-game options' default values");
            System.err.println("       -D name=value : set properties such as " + SOCDBHelper.PROP_JSETTLERS_DB_USER);
            System.err.println("       --pw-reset uname   : reset password in DB for user uname, then exit");
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
     * Uses {@link Console#readPassword()} if available.
     *<P>
     * This rudimentary method exists for compatibility:
     * Neither the Eclipse console nor java 1.5 had {@code System.console}.
     *<P>
     * If not using {@code Console.readPassword()}, <B>the input is not masked</B>
     * because there's no cross-platform way to do so.
     *
     * @param prompt  Optional password prompt; default is "Password:"
     *     Must avoid any java format-string characters; see {@link Console#readPassword(String, Object...)}.
     * @return  The password read, or an empty string "" if none.
     *     This is returned as a mutable StringBuilder
     *     so the caller can clear its contents when done, using
     *     {@link #clearBuffer(StringBuilder)}.
     *     If ^C or an {@link IOException} occurs, returns {@code null}.
     * @since 1.1.20
     */
    private static StringBuilder readPassword(String prompt)
    {
        // System.in can read only an entire line (no portable raw mode in 1.5),
        // so we can't mask after each character. This also applies to the Eclipse console.

        if (prompt == null)
            prompt = "Password:";

        final Console con = System.console();
        if (con != null)
        {
            char[] pw = System.console().readPassword(prompt);
            if (pw == null)
                return new StringBuilder();  // EOF or error

            return new StringBuilder().append(pw);  // keep it mutable
        }

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
     * If there are problems with the network setup, the jar packaging,
     * or with running a {@link SOCDBHelper#PROP_JSETTLERS_DB_SCRIPT_SETUP db setup script}
     * or {@link SOCDBHelper#PROP_JSETTLERS_DB_UPGRADE__SCHEMA schema upgrade},
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

        if (Version.versionNumber() == 0)
        {
            System.err.println("\n*** Packaging Error in server JAR: Cannot determine JSettlers version. Exiting now.");
                // I18N: Can't localize this, the i18n files are provided by the same packaging steps
                // which would create /resources/version.info
            System.exit(1);
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
                    final boolean validate_config_mode
                        = server.getConfigBoolProperty(PROP_JSETTLERS_TEST_VALIDATE__CONFIG, false);

                    String pval = argp.getProperty(SOCDBHelper.PROP_IMPL_JSETTLERS_PW_RESET);
                    if (pval != null)
                        if (validate_config_mode)
                            System.err.println("Skipping password reset: Config Validation mode");
                        else
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
                // The sql setup script or schema upgrade was ran successfully by initialize;
                // exit server, user will re-run without the setup script or schema upgrade param.

                if (argp.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP))
                {
                    System.err.println("\nDB setup script was successful. Exiting now.\n");
                } else {
                    // assume is from SOCDBHelper.PROP_JSETTLERS_DB_UPGRADE__SCHEMA
                    // and getMessage() is from initSocServer's call to SOCDBHelper.upgradeSchema();
                    // text will be "DB schema upgrade was successful", possibly with detail like
                    // "some upgrade tasks will complete in the background during normal server operation".

                    System.err.println("\n" + e.getMessage() + ". Exiting now.\n");
                }
                System.exit(2);
            }
            catch (SQLException e)
            {
                // the sql setup script was ran by initialize, but failed to complete.
                // or, a db URL was specified and server was unable to connect.
                // or, required database test(s) failed in SOCDBHelper.testDBHelper().
                // exception detail was printed in initSocServer.
                if (argp.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP))
                    System.err.println("\n* DB setup script failed. Exiting now.\n");
                else if (argp.containsKey(SOCDBHelper.PROP_JSETTLERS_DB_UPGRADE__SCHEMA))
                    System.err.println("\n* DB schema upgrade failed. Exiting now.\n");
                else
                    System.err.println("\n* Exiting now.\n");
                System.exit(1);
            }
            catch (IllegalArgumentException e)
            {
                System.err.println
                    ("\n" + e.getMessage()
                     + "\n* Error in game options properties: Exiting now.\n");
                System.exit(1);
            }
            catch (IllegalStateException e)
            {
                System.err.println
                    ("\n" + e.getMessage()
                     + "\n* Packaging Error in server JAR: Exiting now.\n");
                System.exit(1);
            }
        }
        catch (RuntimeException e)
        {
            System.err.println
                ("\n" + e.getMessage()
                 + "\n* Internal error during startup: Exiting now.\n");
            e.printStackTrace();
            System.exit(1);
        }
        catch (Throwable e)
        {
            printUsage(false);
            return;
        }

    }  // main

    /**
     * Interface for asynchronous callbacks from
     * {@link SOCServer#authOrRejectClientUser(Connection, String, String, int, boolean, boolean, AuthSuccessRunnable)}
     * for better multithreading granularity.
     * If auth succeeds, calls {@link #success(Connection, int)}.
     * If auth fails, {@code authOrRejectClientUser(..)} sends the client a failure message
     * and no callback is made.
     *<P>
     * Before v1.2.00, {@code authOrRejectClientUser(..)} returned status flags like
     * {@link SOCServer#AUTH_OR_REJECT__OK AUTH_OR_REJECT__OK} directly instead of using a callback.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.2.00
     */
    public interface AuthSuccessRunnable
    {
        /**
         * Called on successful client authentication, or if user was already authenticated.
         * @param c  Client connection which was authenticated
         * @param authResult  Auth check result flags: {@link SOCServer#AUTH_OR_REJECT__OK AUTH_OR_REJECT__OK},
         *     {@link SOCServer#AUTH_OR_REJECT__SET_USERNAME AUTH_OR_REJECT__SET_USERNAME}, etc. See
         *     {@link SOCServer#authOrRejectClientUser(Connection, String, String, int, boolean, boolean, AuthSuccessRunnable)}
         *     for details.
         */
        void success(final Connection c, final int authResult);
    }

}  // public class SOCServer
