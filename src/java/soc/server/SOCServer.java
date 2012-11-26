/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2005 Chadwick A McHenry <mchenryc@acm.org>
 * Portions of this file Copyright (C) 2007-2012 Jeremy D Monin <jeremy@nand.net>
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

import soc.util.IntPair;
import soc.util.SOCGameBoardReset;
import soc.util.SOCGameList;  // used in javadoc
import soc.util.SOCRobotParameters;
import soc.util.Version;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Random;
import java.util.StringTokenizer;
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
 * See {@link #processDebugCommand(StringConnection, String, String)}
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
public class SOCServer extends Server
    implements SOCScenarioEventListener
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
     * Default maximum number of connected clients (30; {@link #maxConnections} field).
     * @since 1.1.15
     */
    public static final int SOC_MAXCONN_DEFAULT = 30;

    // If a new property is added, please add a PROP_JSETTLERS_ constant
    // and also add it to PROPS_LIST.

    /** Property <tt>jsettlers.port</tt> to specify the port the server listens on.
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_PORT = "jsettlers.port";

    /** Property <tt>jsettlers.connections</tt> to specify the maximum number of connections allowed.
     * Remember that robots count against this limit.
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_CONNECTIONS = "jsettlers.connections";

    /**
     * Property <tt>jsettlers.startrobots</tt> to start some robots when the server starts.
     * (The default is 0, no robots are started by default.)
     *<P>
     * 30% will be "smart" robots, the other 70% will be "fast" robots.
     * Remember that robots count against the {@link #PROP_JSETTLERS_CONNECTIONS max connections} limit.
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_STARTROBOTS = "jsettlers.startrobots";

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
     * @since 1.1.10
     */
    public static final String PROP_JSETTLERS_CLI_MAXCREATEGAMES = "jsettlers.client.maxcreategames";

    /**
     * Property <tt>jsettlers.client.maxcreatechannels</tt> to limit the amount of
     * chat channels that a client can create at once. (The default is 2.)
     * Once a channel is deleted (all members leave), they can create another.
     * @since 1.1.10
     */
    public static final String PROP_JSETTLERS_CLI_MAXCREATECHANNELS = "jsettlers.client.maxcreatechannels";

    /**
     * List and descriptions of all available JSettlers {@link Properties properties},
     * such as {@link #PROP_JSETTLERS_PORT} and {@link SOCDBHelper#PROP_JSETTLERS_DB_URL}.
     *<P>
     * Each property name is followed in the array by a brief description:
     * [0] is a property, [1] is its description, [2] is the next property, etc.
     * (This was added in 1.1.13 for {@link #printUsage(boolean)}}.
     * @since 1.1.09
     */
    public static final String[] PROPS_LIST =
    {
        PROP_JSETTLERS_PORT,     "TCP port number for server to bind to",
        PROP_JSETTLERS_CONNECTIONS,   "Maximum connection count, including robots",
        PROP_JSETTLERS_STARTROBOTS,   "Number of robots to create at startup",
        PROP_JSETTLERS_ALLOW_DEBUG,   "Allow remote debug commands? (if Y)",
        PROP_JSETTLERS_CLI_MAXCREATECHANNELS,   "Maximum simultaneous channels that a client can create",
        PROP_JSETTLERS_CLI_MAXCREATEGAMES,      "Maximum simultaneous games that a client can create",
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
    public static final String SERVERNAME = "Server";

    /**
     * Minimum required client version, to connect and play a game.
     * Same format as {@link soc.util.Version#versionNumber()}.
     * Currently there is no enforced minimum (0000).
     * @see #setClientVersSendGamesOrReject(StringConnection, int, boolean)
     */
    public static final int CLI_VERSION_MIN = 0000;

    /**
     * Minimum required client version, in "display" form, like "1.0.00".
     * Currently there is no minimum.
     * @see #setClientVersSendGamesOrReject(StringConnection, int, boolean)
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
     * If game will expire in this or fewer minutes, warn the players. Default 10.
     * Must be at least twice the sleep-time in {@link SOCGameTimeoutChecker#run()}.
     * The game expiry time is set at game creation in {@link SOCGameListAtServer#createGame(String, String, Hashtable)}.
     *
     * @see #checkForExpiredGames(long)
     * @see SOCGameTimeoutChecker#run()
     * @see SOCGameListAtServer#GAME_EXPIRE_MINUTES
     */
    public static int GAME_EXPIRE_WARN_MINUTES = 10;

    /**
     * Force robot to end their turn after this many seconds
     * of inactivity.
     * @see #checkForExpiredTurns(long)
     * @since 1.1.11
     */
    public static int ROBOT_FORCE_ENDTURN_SECONDS = 8;

    /**
     * Force robot to end their turn after this much inactivity,
     * while they've made a trade offer.
     * @see #checkForExpiredTurns(long)
     * @since 1.1.11
     */
    public static int ROBOT_FORCE_ENDTURN_TRADEOFFER_SECONDS = 60;

    /**
     * Maximum permitted game name length, default 30 characters.
     * Before 1.1.13, the default maximum was 20 characters.
     *
     * @see #createOrJoinGameIfUserOK(StringConnection, String, String, String, Hashtable)
     * @since 1.1.07
     */
    public static int GAME_NAME_MAX_LENGTH = 30;

    /**
     * Maximum permitted player name length, default 20 characters.
     * The client already truncates to 20 characters in SOCPlayerClient.getValidNickname.
     *
     * @see #createOrJoinGameIfUserOK(StringConnection, String, String, String, Hashtable)
     * @since 1.1.07
     */
    public static int PLAYER_NAME_MAX_LENGTH = 20;

    /**
     * Maximum number of games that a client can create at the same time (default 5).
     * Once this limit is reached, the client must delete a game before creating a new one.
     * Set this to -1 to disable it; 0 will disallow any game creation.
     * @since 1.1.10
     */
    public static int CLIENT_MAX_CREATE_GAMES = 5;

    /**
     * Maximum number of chat channels that a client can create at the same time (default 2).
     * Once this limit is reached, the client must delete a channel before creating a new one.
     * Set this to -1 to disable it; 0 will disallow any chat channel creation.
     * @since 1.1.10
     */
    public static int CLIENT_MAX_CREATE_CHANNELS = 2;

    /**
     * For local practice games (pipes, not TCP), the name of the pipe.
     * Used to distinguish practice vs "real" games.
     * 
     * @see soc.server.genericServer.LocalStringConnection
     */
    public static String PRACTICE_STRINGPORT = "SOCPRACTICE";

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
     * @see #processDebugCommand(StringConnection, String, String)
     * @since 1.1.14
     */
    private boolean allowDebugUser;

    /**
     * Properties for the server, or empty if that constructor wasn't used.
     * Property names are held in PROP_* and SOCDBHelper.PROP_* constants.
     * @see #SOCServer(int, Properties)
     * @see #PROPS_LIST
     * @since 1.1.09
     */
    private Properties props;

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
     * A list of robot {@link StringConnection}s connected to this server.
     * @see SOCPlayerLocalRobotRunner#robotClients
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
        // If you change values here, see SOCPlayerClient.startPracticeGame
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
     * Did the command line include --option / -o to set {@link SOCGameOption game option} values?
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
     * @see #checkNickname(String, StringConnection)
     * @since 1.1.08
     */
    public static final int NICKNAME_TAKEOVER_SECONDS_SAME_PASSWORD = 15;

    /**
     * Number of seconds before a connection is considered disconnected, and
     * its nickname can be "taken over" by a new connection from the same IP.
     * Used when no password is given by the new connection.
     * @see #checkNickname(String, StringConnection)
     * @since 1.1.08
     */
    public static final int NICKNAME_TAKEOVER_SECONDS_SAME_IP = 30;

    /**
     * Number of seconds before a connection is considered disconnected, and
     * its nickname can be "taken over" by a new connection from a different IP.
     * Used when no password is given by the new connection.
     * @see #checkNickname(String, StringConnection)
     * @since 1.1.08
     */
    public static final int NICKNAME_TAKEOVER_SECONDS_DIFFERENT_IP = 150;

    /**
     * list of chat channels
     */
    protected SOCChannelList channelList = new SOCChannelList();

    /**
     * list of soc games
     */
    protected SOCGameListAtServer gameList = new SOCGameListAtServer();

    /**
     * table of requests for robots to join games
     */
    protected Hashtable<String, Vector<StringConnection>> robotJoinRequests = new Hashtable<String, Vector<StringConnection>>();

    /**
     * table of requestst for robots to leave games
     */
    protected Hashtable<String, Vector<SOCReplaceRequest>> robotDismissRequests = new Hashtable<String, Vector<SOCReplaceRequest>>();

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
     * the total number of games that have been started
     */
    protected int numberOfGamesStarted;

    /**
     * the total number of games finished
     */
    protected int numberOfGamesFinished;

    /**
     * total number of users
     */
    protected int numberOfUsers;

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
     * Create a Settlers of Catan server listening on TCP port p.
     * You must start its thread yourself.
     *<P>
     * In 1.1.07 and later, will also print game options to stderr if
     * any option defaults require a minimum client version, or if
     * {@link #hasSetGameOptions} is set.
     *
     * @param p    the TCP port that the server listens on
     * @param mc   the maximum number of connections allowed;
     *            remember that robots count against this limit.
     * @param databaseUserName  the user name for accessing the database
     * @param databasePassword  the password for the user
     * @throws SocketException  If a network setup problem occurs
     * @throws EOFException   If db setup script ran successfully and server should exit now
     * @throws SQLException   If db setup script fails
     */
    public SOCServer(int p, int mc, String databaseUserName, String databasePassword)
        throws SocketException, EOFException, SQLException
    {
        super(p);
        maxConnections = mc;
        initSocServer(databaseUserName, databasePassword, null);
    }

    /**
     * Create a Settlers of Catan server listening on TCP port p.
     * You must start its thread yourself.
     *<P>
     * The database properties are {@link SOCDBHelper#PROP_JSETTLERS_DB_USER}
     * and {@link SOCDBHelper#PROP_JSETTLERS_DB_PASS}.
     *<P>
     * Will also print game options to stderr if
     * any option defaults require a minimum client version, or if
     * {@link #hasSetGameOptions} is set.
     * 
     * @param p    the TCP port that the server listens on
     * @param props  null, or properties containing {@link #PROP_JSETTLERS_CONNECTIONS}
     *               and any other desired properties.
     * @since 1.1.09
     * @throws SocketException  If a network setup problem occurs
     * @throws EOFException   If db setup script ran successfully and server should exit now
     * @throws SQLException   If db setup script fails
     * @see #PROPS_LIST
     */
    public SOCServer(final int p, Properties props)
        throws SocketException, EOFException, SQLException
    {
        super(p);
        maxConnections = init_getIntProperty(props, PROP_JSETTLERS_CONNECTIONS, SOC_MAXCONN_DEFAULT);
        allowDebugUser = init_getBoolProperty(props, PROP_JSETTLERS_ALLOW_DEBUG, false);
        CLIENT_MAX_CREATE_GAMES = init_getIntProperty(props, PROP_JSETTLERS_CLI_MAXCREATEGAMES, CLIENT_MAX_CREATE_GAMES);
        CLIENT_MAX_CREATE_CHANNELS = init_getIntProperty(props, PROP_JSETTLERS_CLI_MAXCREATECHANNELS, CLIENT_MAX_CREATE_CHANNELS);
        String dbuser = props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_USER, "socuser");
        String dbpass = props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_PASS, "socpass");
        initSocServer(dbuser, dbpass, props);
    }

    /**
     * Create a Settlers of Catan server listening on local stringport s.
     * You must start its thread yourself.
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
        super(s);
        maxConnections = mc;
        initSocServer(databaseUserName, databasePassword, null);
    }

    /**
     * Common init for all constructors.
     * Prints some progress messages to {@link System#err}.
     * Starts all server threads except the main thread.
     * If {@link #PROP_JSETTLERS_STARTROBOTS} is specified, those aren't started until {@link #serverUp()}.
     *<P>
     * If there are problems with the network setup ({@link #error} != null),
     * this method will throw {@link SocketException}.
     * If problems running a {@link SOCDBHelper#PROP_JSETTLERS_DB_SCRIPT_SETUP db setup script},
     * this method will throw {@link SQLException}.
     *<P>
     * If a db setup script runs successfully,
     * the server does not complete its startup; this method will throw {@link EOFException}.
     *
     * @param databaseUserName Used for DB connect - not retained
     * @param databasePassword Used for DB connect - not retained
     * @param props  null, or properties containing {@link #PROP_JSETTLERS_CONNECTIONS}
     *       and any other desired properties.
     *       If <code>props</code> is null, the properties will be created empty.
     * @throws SocketException  If a network setup problem occurs
     * @throws EOFException   If db setup script ran successfully and server should exit now
     * @throws SQLException   If db setup script fails
     */
    private void initSocServer(String databaseUserName, String databasePassword, Properties props)
        throws SocketException, EOFException, SQLException
    {
        printVersionText();

        /* Check for problems during super setup (such as port already in use).
         * Ignore net errors if we're running a DB setup script and then exiting.
         */
        if ((error != null)
            && ((props == null) || (null == props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP))))
        {
            final String errMsg = "* Exiting due to network setup problem: " + error.toString();
            throw new SocketException(errMsg);
        }

        if (props == null)
            props = new Properties();
        this.props = props;

        if (allowDebugUser)
        {
            System.err.println("Warning: Remote debug commands are allowed.");
        }

        try
        {
            SOCDBHelper.initialize(databaseUserName, databasePassword, props);
            System.err.println("User database initialized.");

            if (props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP) != null)
            {
                // the sql script was ran by initialize
                throw new EOFException("DB setup script successful");
            }
        }
        catch (SQLException x) // just a warning
        {
            System.err.println("No user database available: " +
                               x.getMessage());
            Throwable cause = x.getCause();

            while ((cause != null) && ! (cause instanceof ClassNotFoundException))
            {
                System.err.println("\t" + cause);
                cause = cause.getCause();
            }

            if (props.getProperty(SOCDBHelper.PROP_JSETTLERS_DB_SCRIPT_SETUP) != null)
            {
                // the sql script was ran by initialize, but failed to complete;
                // don't continue server startup with just a warning
                throw x;  // x is SQLException
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

        if (SOCDBHelper.isInitialized())
        {
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

        startTime = System.currentTimeMillis();
        numberOfGamesStarted = 0;
        numberOfGamesFinished = 0;
        numberOfUsers = 0;
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
        if (hasSetGameOptions || (SOCGameOption.optionsMinimumVersion(SOCGameOption.getAllKnownOptions()) > -1))
        {
            Thread.yield();  // wait for other output to appear first
            try { Thread.sleep(200); } catch (InterruptedException ie) {}

            printGameOptions();
        }

        System.err.print("The server is ready.");
        if (port > 0)
            System.err.print(" Listening on port " + port);
        System.err.println();
        System.err.println();
    }

    /**
     * For initialization, get and parse an integer property, or use its default instead.
     * @param props  Properties to look in
     * @param pName  Property name
     * @param pDefault  Default value to use if not found or not parsable
     * @return The property's parsed integer value, or <tt>pDefault</tt>
     * @since 1.1.10
     */
    private static int init_getIntProperty(Properties props, final String pName, final int pDefault)
    {
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
     * Get and parse a boolean property, or use its default instead.
     * True values are: T Y 1.
     * False values are: F N 0.
     * Not case-sensitive.
     * Any other value will be ignored and get <tt>pDefault</tt>.
     * @param props  Properties to look in, such as {@link SOCServer#props}, or null for <tt>pDefault</tt>
     * @param pName  Property name
     * @param pDefault  Default value to use if not found or not parsable
     * @return The property's parsed value, or <tt>pDefault</tt>
     * @since 1.1.14
     */
    private static boolean init_getBoolProperty(Properties props, final String pName, final boolean pDefault)
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
     * Print the version and attribution text. Formerly inside constructors.
     * @since 1.1.07
     */
    public static void printVersionText()
    {
        System.err.println("Java Settlers Server " + Version.version() +
                ", build " + Version.buildnum() + ", " + Version.copyright());
        System.err.println("Network layer based on code by Cristian Bogdan; local network by Jeremy Monin.");
    }

    /**
     * Callback to take care of things when server comes up, after the server socket
     * is bound and listening, in the main thread.
     * If {@link #PROP_JSETTLERS_STARTROBOTS} is specified, start those {@link SOCRobotClient}s now.
     * @since 1.1.09
     */
    @Override
    public void serverUp()
    {
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
            }
            catch (NumberFormatException e)
            {
                System.err.println("Not starting robots: Bad number format, ignoring property " + PROP_JSETTLERS_STARTROBOTS);
            }
        }
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
                D.ebugPrintln("*** " + c.getData() + " joined the channel " + ch);
                channelList.addMember(c, ch);
            }
        }
    }

    /**
     * the connection c leaves the channel ch
     *
     * WARNING: MUST HAVE THE channelList.takeMonitorForChannel(ch) before
     * calling this method
     *
     * @param c  the connection
     * @param ch the channel
     * @param channelListLock  true if we have the channelList monitor
     * @return true if we destroyed the channel
     */
    public boolean leaveChannel(StringConnection c, String ch, boolean channelListLock)
    {
        if (c == null)
            return false;

        D.ebugPrintln("leaveChannel: " + c.getData() + " " + ch + " " + channelListLock);

        boolean result = false;

        if (channelList.isMember(c, ch))
        {
            channelList.removeMember(c, ch);

            SOCLeave leaveMessage = new SOCLeave((String) c.getData(), c.host(), ch);
            messageToChannelWithMon(ch, leaveMessage);
            D.ebugPrintln("*** " + (String) c.getData() + " left the channel " + ch);
        }

        if (channelList.isChannelEmpty(ch))
        {
            final String chOwner = channelList.getOwner(ch);

            if (channelListLock)
            {
                channelList.deleteChannel(ch);
            }
            else
            {
                channelList.takeMonitor();

                try
                {
                    channelList.deleteChannel(ch);
                }
                catch (Exception e)
                {
                    D.ebugPrintStackTrace(e, "Exception in leaveChannel");
                }

                channelList.releaseMonitor();
            }

            // Reduce the owner's channels-active count
            StringConnection oConn = conns.get(chOwner);
            if (oConn != null)
                ((SOCClientData) oConn.getAppData()).deletedChannel();

            result = true;
        }

        return result;
    }

    /**
     * Adds a connection to a game, unless they're already a member.
     * If the game doesn't yet exist, create it,
     * and announce the new game to all clients.
     *<P>
     * After this, human players are free to join, until someone clicks "Start Game".
     * At that point, server will look for robots to fill empty seats.
     *
     * @param c    the Connection to be added; its name and version should already be set.
     * @param gaName  the name of the game
     * @param gaOpts  if creating a game with options, hashtable of {@link SOCGameOption}; otherwise null.
     *                Should already be validated, by calling
     *                {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable, boolean)}
     *                with <tt>doServerPreadjust</tt> true.
     *
     * @return     true if c was not a member of ch before,
     *             false if c was already in this game
     *
     * @throws SOCGameOptionVersionException if asking to create a game (gaOpts != null),
     *           but client's version is too low to join because of a
     *           requested game option's minimum version in gaOpts.
     *           Calculated via {@link SOCGameOption#optionsNewerThanVersion(int, boolean, boolean, Hashtable)}.
     *           (this exception was added in 1.1.07)
     * @throws IllegalArgumentException if client's version is too low to join for any
     *           other reason. (this exception was added in 1.1.06)
     * @see #joinGame(SOCGame, StringConnection, boolean, boolean)
     * @see #handleSTARTGAME(StringConnection, SOCStartGame)
     * @see #handleJOINGAME(StringConnection, SOCJoinGame)
     */
    public boolean connectToGame(StringConnection c, final String gaName, Hashtable<String, SOCGameOption> gaOpts)
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
                gVers = SOCGameOption.optionsMinimumVersion(gaOpts);
                if (gVers > cliVers)
                {
                    // Which option(s) are too new for client?
                    Vector<SOCGameOption> optsValuesTooNew = SOCGameOption.optionsNewerThanVersion
                        (cliVers, true, false, gaOpts);
                    throw new SOCGameOptionVersionException(gVers, cliVers, optsValuesTooNew);

                    // <---- Exception: Early return ----
                }
            }

            gameList.takeMonitor();
            boolean monitorReleased = false;

            try
            {
                // Create new game, expiring in SOCGameListAtServer.GAME_EXPIRE_MINUTES .
                SOCGame newGame = gameList.createGame(gaName, (String) c.getData(), gaOpts);
                if ((strSocketName != null) && (strSocketName.equals(PRACTICE_STRINGPORT)))
                {
                    newGame.isPractice = true;  // flag if practice game (set since 1.1.09)
                }
                newGame.setScenarioEventListener(this);  // for playerEvent, gameEvent callbacks (since 2.0.00)

                // Add this (creating) player to the game
                gameList.addMember(c, gaName);

                // must release monitor before we broadcast
                gameList.releaseMonitor();
                monitorReleased = true;
                result = true;
                ((SOCClientData) c.getAppData()).createdGame();

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
                        gVersMinGameOptsNoChange = SOCGameOption.optionsMinimumVersion(gaOpts, true);
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
            catch (IllegalArgumentException e)
            {
                if (! monitorReleased)
                    gameList.releaseMonitor();
                throw e;  // caller handles it
            }
            catch (Exception e)
            {
                D.ebugPrintStackTrace(e, "Exception in connectToGame");
            }

            if (!monitorReleased)
            {
                gameList.releaseMonitor();
            }
        }

        return result;
    }

    /**
     * the connection c leaves the game gm.  Clean up; if needed, call {@link #forceEndGameTurn(SOCGame, String)}.
     *<P>
     * <B>Locks:</b> May or may not have the gameList.takeMonitorForGame(gm) before
     * calling this method; should not have {@link SOCGame#takeMonitor()}.
     *
     * @param c  the connection; if c is being dropped because of an error,
     *           this method assumes that {@link StringConnection#disconnect()}
     *           has already been called.  This method won't exclude c from
     *           any communication about leaving the game, in case they are
     *           still connected and in other games.
     * @param gm the game
     * @param gameListLock  true if we have the gameList.takeMonitor() lock
     * @return true if the game was destroyed (because c was the last non-robot player,
     *              and no one was watching)
     */
    public boolean leaveGame(StringConnection c, String gm, boolean gameListLock)
    {
        System.err.println("L712: leaveGame(" + c + ", " + gm + ")");  // JM TEMP
        if (c == null)
        {
            return false;  // <---- Early return: no connection ----
        }

        boolean gameDestroyed = false;

        gameList.removeMember(c, gm);

        boolean isPlayer = false;
        int playerNumber = 0;    // removing this player number
        SOCGame ga = gameList.getGameData(gm);
        if (ga == null)
        {
            return false;  // <---- Early return: no game ----
        }

        boolean gameHasHumanPlayer = false;
        boolean gameHasObserver = false;
        boolean gameVotingActiveDuringStart = false;

        final int gameState = ga.getGameState();
        final String plName = (String) c.getData();  // Retain name, since will become null within game obj.

        for (playerNumber = 0; playerNumber < ga.maxPlayers;
                playerNumber++)
        {
            SOCPlayer player = ga.getPlayer(playerNumber);

            if ((player != null) && (player.getName() != null)
                && (player.getName().equals(plName)))
            {
                isPlayer = true;

                /**
                 * About to remove this player from the game. Before doing so:
                 * If a board-reset vote is in progress, they cannot vote
                 * once they have left. So to keep the game moving,
                 * fabricate their response: vote No.
                 */
                if (ga.getResetVoteActive())
                {
                    if (gameState <= SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
                        gameVotingActiveDuringStart = true;

                    if (ga.getResetPlayerVote(playerNumber) == SOCGame.VOTE_NONE)
                    {
                        gameList.releaseMonitorForGame(gm);
                        ga.takeMonitor();
                        resetBoardVoteNotifyOne(ga, playerNumber, plName, false);
                        ga.releaseMonitor();
                        gameList.takeMonitorForGame(gm);
                    }
                }

                /**
                 * Remove the player.
                 */
                ga.removePlayer(plName);  // player obj name becomes null

                //broadcastGameStats(cg);
                break;
            }
        }

        SOCLeaveGame leaveMessage = new SOCLeaveGame(plName, c.host(), gm);
        messageToGameWithMon(gm, leaveMessage);
        recordGameEvent(gm, leaveMessage.toCmd());

        D.ebugPrintln("*** " + plName + " left the game " + gm);
        messageToGameWithMon(gm, new SOCGameTextMsg(gm, SERVERNAME, plName + " left the game"));

        /**
         * check if there is at least one person playing the game
         */
        for (int pn = 0; pn < ga.maxPlayers; pn++)
        {
            SOCPlayer player = ga.getPlayer(pn);

            if ((player != null) && (player.getName() != null) && (!ga.isSeatVacant(pn)) && (!player.isRobot()))
            {
                gameHasHumanPlayer = true;
                break;
            }
        }

        //D.ebugPrintln("*** gameHasHumanPlayer = "+gameHasHumanPlayer+" for "+gm);

        /**
         * if no human players, check if there is at least one person watching the game
         */
        if (!gameHasHumanPlayer && !gameList.isGameEmpty(gm))
        {
            Enumeration<StringConnection> membersEnum = gameList.getMembers(gm).elements();

            while (membersEnum.hasMoreElements())
            {
                StringConnection member = membersEnum.nextElement();

                //D.ebugPrintln("*** "+member.data+" is a member of "+gm);
                boolean nameMatch = false;

                for (int pn = 0; pn < ga.maxPlayers; pn++)
                {
                    SOCPlayer player = ga.getPlayer(pn);

                    if ((player != null) && (player.getName() != null) && (player.getName().equals(member.getData())))
                    {
                        nameMatch = true;
                        break;
                    }
                }

                if (!nameMatch)
                {
                    gameHasObserver = true;
                    break;
                }
            }
        }
        //D.ebugPrintln("*** gameHasObserver = "+gameHasObserver+" for "+gm);

        /**
         * if the leaving member was playing the game, and
         * the game isn't over, then decide:
         * - Do we need to force-end the current turn?
         * - Do we need to cancel their initial settlement placement?
         * - Should we replace the leaving player with a robot?
         */
        if (isPlayer && (gameHasHumanPlayer || gameHasObserver)
                && ((ga.getPlayer(playerNumber).getPublicVP() > 0)
                    || (gameState == SOCGame.START1A)
                    || (gameState == SOCGame.START1B))
                && (gameState < SOCGame.OVER)
                && !(gameState < SOCGame.START1A))
        {
            boolean foundNoRobots;

            if (ga.getPlayer(playerNumber).isRobot())
            {
                /**
                 * don't replace bot with bot; force end-turn instead.
                 */
                foundNoRobots = true;
            }
            else
            {
                /**
                 * get a robot to replace this human player;
                 * just in case, check game-version vs robots-version,
                 * like at new-game (readyGameAskRobotsJoin).
                 */
                foundNoRobots = false;

                messageToGameWithMon(gm, new SOCGameTextMsg(gm, SERVERNAME, "Fetching a robot player..."));

                if (robots.isEmpty())
                {
                    messageToGameWithMon(gm, new SOCGameTextMsg(gm, SERVERNAME, "Sorry, no robots on this server."));
                    foundNoRobots = true;
                }
                else if (ga.getClientVersionMinRequired() > Version.versionNumber())
                {
                    messageToGameWithMon(gm, new SOCGameTextMsg
                            (gm, SERVERNAME,
                             "Sorry, the robots can't join this game; its version is somehow newer than server and robots, it's "
                             + ga.getClientVersionMinRequired()));
                    foundNoRobots = true;
                }
                else
                {
                    /**
                     * request a robot that isn't already playing this game or
                     * is not already requested to play in this game
                     */
                    boolean nameMatch = false;
                    StringConnection robotConn = null;
    
                    final int[] robotIndexes = robotShuffleForJoin();  // Shuffle to distribute load
    
                    Vector<StringConnection> requests = robotJoinRequests.get(gm);
    
                    for (int idx = 0; idx < robots.size(); idx++)
                    {
                        robotConn = robots.get(robotIndexes[idx]);
                        nameMatch = false;
    
                        for (int i = 0; i < ga.maxPlayers; i++)
                        {
                            SOCPlayer pl = ga.getPlayer(i);
    
                            if (pl != null)
                            {
                                String pname = pl.getName();
    
                                // D.ebugPrintln("CHECKING " + (String) robotConn.getData() + " == " + pname);
    
                                if ((pname != null) && (pname.equals(robotConn.getData())))
                                {
                                    nameMatch = true;
    
                                    break;
                                }
                            }
                        }
    
                        if ((!nameMatch) && (requests != null))
                        {
                            Enumeration<StringConnection> requestsEnum = requests.elements();
    
                            while (requestsEnum.hasMoreElements())
                            {
                                StringConnection tempCon = requestsEnum.nextElement();
    
                                // D.ebugPrintln("CHECKING " + robotConn + " == " + tempCon);
    
                                if (tempCon == robotConn)
                                {
                                    nameMatch = true;
                                }
    
                                break;
                            }
                        }
    
                        if (!nameMatch)
                        {
                            break;
                        }
                    }
    
                    if (!nameMatch)
                    {
                        /**
                         * make the request
                         */
                        D.ebugPrintln("@@@ JOIN GAME REQUEST for " + (String) robotConn.getData());
    
                        robotConn.put(SOCJoinGameRequest.toCmd(gm, playerNumber, ga.getGameOptions()));
    
                        /**
                         * record the request
                         */
                        if (requests == null)
                        {
                            requests = new Vector<StringConnection>();
                            requests.addElement(robotConn);
                            robotJoinRequests.put(gm, requests);
                        }
                        else
                        {
                            requests.addElement(robotConn);
                        }
                    }
                    else
                    {
                        messageToGameWithMon(gm, new SOCGameTextMsg(gm, SERVERNAME, "*** Can't find a robot! ***"));
                        foundNoRobots = true;
                    }
                }
            }  // if (should try to find a robot)

            /**
             * What to do if no robot was found to fill their spot?
             * Must keep the game going, might need to force-end current turn.
             */
            if (foundNoRobots)
            {
                final boolean stillActive = endGameTurnOrForce(ga, playerNumber, plName, c, true);
                if (! stillActive)
                {
                    // force game destruction below
                    gameHasHumanPlayer = false;
                    gameHasObserver = false;
                }
            }
        }

        /**
         * if the game has no players, or if they're all
         * robots, then end the game and write the data
         * to disk.
         */
        final boolean emptyGame = gameList.isGameEmpty(gm);

        if (emptyGame || (!gameHasHumanPlayer && !gameHasObserver))
        {
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

            gameDestroyed = true;
        }

        //D.ebugPrintln("*** gameDestroyed = "+gameDestroyed+" for "+gm);
        return gameDestroyed;
    }

    /**
     * A bot is unresponsive, or a human player has left the game.
     * End this player's turn cleanly, or force-end if needed.
     *<P>
     * Can be called for a player still in the game, or for a player
     * who has left ({@link SOCGame#removePlayer(String)} has been called).
     *<P>
     * If they were placing an initial road, also cancels that road's
     * initial settlement.
     *<P>
     * <b>Locks:</b> Must not have ga.takeMonitor() when calling this method.
     * May or may not have <tt>gameList.takeMonitorForGame(ga)</tt>;
     * use <tt>hasMonitorFromGameList</tt> to indicate.
     *<P>
     * Not public, but package visibility, for use by {@link SOCGameTimeoutChecker}.
     *
     * @param ga   The game to end turn
     * @param plNumber  player.getNumber; may or may not be current player
     * @param plName    player.getName
     * @param plConn    player's client connection
     * @param hasMonitorFromGameList  if false, have not yet called
     *          {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(ga)};
     *          if false, this method will take this monitor at its start,
     *          and release it before returning.
     * @return true if the turn was ended and game is still active;
     *          false if we find that all players have left and
     *          the gamestate has been changed here to {@link #OVER}.
     */
    boolean endGameTurnOrForce(SOCGame ga, final int plNumber, final String plName, StringConnection plConn, final boolean hasMonitorFromGameList)
    {
        boolean gameStillActive = true;

        final String gm = ga.getName();
        if (! hasMonitorFromGameList)
        {
            gameList.takeMonitorForGame(gm);
        }
        final int cpn = ga.getCurrentPlayerNumber();
        final int gameState = ga.getGameState();

        /**
         * Is a board-reset vote is in progress?
         * If they're still a sitting player, to keep the game
         * moving, fabricate their response: vote No.
         */
        boolean gameVotingActiveDuringStart = false;

        if (ga.getResetVoteActive())
        {
            if (gameState <= SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
                gameVotingActiveDuringStart = true;

            if ((! ga.isSeatVacant(plNumber))
                && (ga.getResetPlayerVote(plNumber) == SOCGame.VOTE_NONE))
            {
                gameList.releaseMonitorForGame(gm);
                ga.takeMonitor();
                resetBoardVoteNotifyOne(ga, plNumber, plName, false);
                ga.releaseMonitor();
                gameList.takeMonitorForGame(gm);
            }
        }

        /**
         * Now end their turn, or handle any needed responses if not current player.
         * Don't call forceEndGameTurn()/ga.forceEndTurn() unless we need to.
         */
        if (plNumber == cpn)
        {
            /**
             * End their turn just to keep the game limping along.
             * To prevent deadlock, we must release gamelist's monitor for
             * this game before calling endGameTurn.
             */

            if ((gameState == SOCGame.START1B) || (gameState == SOCGame.START2B) || (gameState == SOCGame.START3B))
            {
                /**
                 * Leaving during initial road placement.
                 * Cancel the settlement they just placed,
                 * and send that cancel to the other players.
                 * Don't change gameState yet.
                 * Note that their most recent init settlement is removed here,
                 * but not earlier settlement(s). (That would impact the robots much more.)
                 */
                SOCPlayer pl = ga.getPlayer(plNumber);
                SOCSettlement pp = new SOCSettlement(pl, pl.getLastSettlementCoord(), null);
                ga.undoPutInitSettlement(pp);
                ga.setGameState(gameState);  // state was changed by undoPutInitSettlement
                messageToGameWithMon(gm, new SOCCancelBuildRequest(gm, SOCSettlement.SETTLEMENT));
            }

            if (ga.canEndTurn(plNumber))
            {
                gameList.releaseMonitorForGame(gm);
                ga.takeMonitor();
                endGameTurn(ga, null);
                ga.releaseMonitor();
                gameList.takeMonitorForGame(gm);
            } else {
                /**
                 * Cannot easily end turn.
                 * Must back out something in progress.
                 * May or may not end turn; see javadocs
                 * of forceEndGameTurn and game.forceEndTurn.
                 * All start phases are covered here (START1A..START2B)
                 * because canEndTurn returns false in those gameStates.
                 */
                gameList.releaseMonitorForGame(gm);
                ga.takeMonitor();
                if (gameVotingActiveDuringStart)
                {
                    /**
                     * If anyone has requested a board-reset vote during
                     * game-start phases, we have to tell clients to cancel
                     * the vote request, because {@link soc.message.SOCTurn}
                     * isn't always sent during start phases.  (Voting must
                     * end when the turn ends.)
                     */
                    messageToGame(gm, new SOCResetBoardReject(gm));
                    ga.resetVoteClear();
                }

                /**
                 * Force turn to end
                 */
                gameStillActive = forceEndGameTurn(ga, plName);
                ga.releaseMonitor();
                if (gameStillActive)
                {
                    gameList.takeMonitorForGame(gm);
                }
            }
        }
        else
        {
            /**
             * Check if game is waiting for input from the player who
             * is leaving, but who isn't current player.
             * To keep the game moving, fabricate their response.
             * - Board-reset voting: Handled above.
             * - Waiting for discard: Handle here.
             */
            if ((gameState == SOCGame.WAITING_FOR_DISCARDS)
                 && (ga.getPlayer(plNumber).getNeedToDiscard()))
            {
                /**
                 * For discard, tell the discarding player's client that they discarded the resources,
                 * tell everyone else that the player discarded unknown resources.
                 */
                gameList.releaseMonitorForGame(gm);
                ga.takeMonitor();
                forceGamePlayerDiscard(ga, cpn, plConn, plName, plNumber);
                sendGameState(ga, false);  // WAITING_FOR_DISCARDS or MOVING_ROBBER
                ga.releaseMonitor();
                gameList.takeMonitorForGame(gm);
            }
        }  // current player?

        if (! hasMonitorFromGameList)
        {
            gameList.releaseMonitorForGame(gm);
        }

        return gameStillActive;
    }

    /**
     * Listener callback for scenario events on the large sea board which affect the game or board,
     * not a specific player. For example, a hex might be revealed from fog.
     *<P>
     * <em>Threads:</em> The game's treater thread handles incoming client messages and calls
     * game methods that change state. Those same game methods will trigger the scenario events;
     * so, the treater thread will also run this <tt>gameEvent</tt> callback.
     *
     * @param ga  Game
     * @param evt  Event code
     * @param detail  Game piece, coordinate, or other data about the event, or null, depending on <tt>evt</tt>  
     * @see #playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)
     * @since 2.0.00
     */
    public void gameEvent(final SOCGame ga, final SOCScenarioGameEvent evt, final Object detail)
    {
        switch (evt)
        {
        case SGE_FOG_HEX_REVEALED:
            {
                final SOCBoard board = ga.getBoard();
                final int hexCoord = ((Integer) detail).intValue(),
                          hexType  = board.getHexTypeFromCoord(hexCoord),
                          diceNum  = board.getNumberOnHexFromCoord(hexCoord);
                final String gaName = ga.getName();
                messageToGame
                    (gaName, new SOCRevealFogHex(gaName, hexCoord, hexType, diceNum));

                final int cpn = ga.getCurrentPlayerNumber();
                if (cpn != -1)
                {
                    final int res = board.getHexTypeFromNumber(hexCoord);
                    if ((res >= SOCResourceConstants.CLAY) && (res <= SOCResourceConstants.WOOD))
                    {
                        messageToGame
                            (gaName, new SOCPlayerElement(gaName, cpn, SOCPlayerElement.GAIN, res, 1));
                        messageToGame
                            (gaName, ga.getPlayer(ga.getCurrentPlayerNumber()).getName() + " gets 1 "
                             + SOCResourceConstants.resName(res) + " by revealing the fog hex.");
                    }
                }
            }
            break;

        }
    }

    /**
     * Listener callback for per-player scenario events on the large sea board.
     * For example, there might be an SVP awarded for settlements.
     *<P>
     * <em>Threads:</em> The game's treater thread handles incoming client messages and calls
     * game methods that change state. Those same game methods will trigger the scenario events;
     * so, the treater thread will also run this <tt>playerEvent</tt> callback.
     *
     * @param ga  Game
     * @param pl  Player
     * @param evt  Event code
     * @see #gameEvent(SOCGame, SOCScenarioGameEvent, Object)
     * @param flagsChanged  True if this event changed {@link SOCPlayer#getScenarioPlayerEvents()},
     *             {@link SOCPlayer#getSpecialVP()}, or another flag documented for <tt>evt</tt> in
     *             {@link SOCScenarioPlayerEvent}
     * @param obj  Object related to the event, or null; documented for <tt>evt</tt> in {@link SOCScenarioPlayerEvent}.
     *             Example: The {@link SOCVillage} for {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     * @since 2.0.00
     */
    public void playerEvent(final SOCGame ga, final SOCPlayer pl, final SOCScenarioPlayerEvent evt,
        final boolean flagsChanged, final Object obj)
    {
        // Note: Some SOCServer code assumes that player events are fired only during handlePUTPIECE.
        // If a new player event breaks this assumption, adjust SOCServer.playerEvent(...) and related code;
        // search where SOCGame.pendingMessagesOut is used.

        final String gaName = ga.getName(),
                     plName = pl.getName();
        final int pn = pl.getPlayerNumber();

        boolean sendSVP = true;
        boolean sendPlayerEventsBitmask = true;

        switch (evt)
        {
        case SVP_SETTLED_ANY_NEW_LANDAREA:
            {
                messageToGame
                    (gaName, plName + " gets a Special Victory Point for growing past the main island.");
                // TODO adjust wording
            }
            break;

        case SVP_SETTLED_EACH_NEW_LANDAREA:
            {
                messageToGame
                    (gaName, plName + " gets 2 Special Victory Points for settling a new island.");

                sendPlayerEventsBitmask = false;
                final int las = pl.getScenarioSVPLandAreas();
                if (las != 0)
                    ga.pendingMessagesOut.add(new SOCPlayerElement
                        (gaName, pn, SOCPlayerElement.SET,
                         SOCPlayerElement.SCENARIO_SVP_LANDAREAS_BITMASK, las));
            }
            break;

        case CLOTH_TRADE_ESTABLISHED_VILLAGE:
            {
                sendSVP = false;
                if (! flagsChanged)
                    sendPlayerEventsBitmask = false;
                StringConnection c = getConnection(plName);
                if (c != null)
                {
                    String txt = (flagsChanged)
                        ? "Trade route established with village. You are no longer prevented from moving the pirate ship."
                        : "Trade route established with village.";
                    c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, txt));
                }

                // Player gets 1 cloth for establishing trade
                SOCVillage vi = (SOCVillage) obj;
                messageToGame(gaName, new SOCPieceValue(gaName, vi.getCoordinates(), vi.getCloth(), 0));
                messageToGame(gaName, new SOCPlayerElement
                    (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_CLOTH_COUNT, pl.getCloth()));
            }
            break;

        }

        if (sendSVP)
            ga.pendingMessagesOut.add(new SOCPlayerElement
                (gaName, pn, SOCPlayerElement.SET,
                 SOCPlayerElement.SCENARIO_SVP, pl.getSpecialVP()));
        if (sendPlayerEventsBitmask)
            ga.pendingMessagesOut.add(new SOCPlayerElement
                (gaName, pn, SOCPlayerElement.SET,
                 SOCPlayerElement.SCENARIO_PLAYEREVENTS_BITMASK, pl.getScenarioPlayerEvents()));
    }

    /**
     * shuffle the indexes to distribute load among {@link #robots}
     * @return a shuffled array of robot indexes, from 0 to ({#link {@link #robots}}.size() - 1
     * @since 1.1.06
     */
    private int[] robotShuffleForJoin()
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
     * Before 1.1.09, this method was part of SOCPlayerClient.
     *
     * @param numFast number of fast robots, with {@link soc.robot.SOCRobotDM#FAST_STRATEGY FAST_STRATEGY}
     * @param numSmart number of smart robots, with {@link soc.robot.SOCRobotDM#SMART_STRATEGY SMART_STRATEGY}
     * @return True if robots were set up, false if an exception occurred.
     *     This typically happens if a robot class, or SOCDisplaylessClient,
     *     can't be loaded, due to packaging of the server-only JAR.
     * @see #startPracticeGame()
     * @see #startLocalTCPServer(int)
     * @since 1.1.00
     */
    public boolean setupLocalRobots(final int numFast, final int numSmart)
    {
        try
        {
            // ASSUMPTION: Server ROBOT_PARAMS_DEFAULT uses SOCRobotDM.FAST_STRATEGY.

            // Make some faster ones first.
            for (int i = 0; i < numFast; ++i)
            {
                String rname = "droid " + (i+1);
                SOCPlayerLocalRobotRunner.createAndStartRobotClientThread(rname, strSocketName, port);
                    // includes yield() and sleep(75 ms) this thread.
            }

            try
            {
                Thread.sleep(150);
                    // Wait for these robots' accept and UPDATEROBOTPARAMS,
                    // before we change the default params.
            }
            catch (InterruptedException ie) {}

            // Make a few smarter ones now:

            // Switch params to SMARTER for future new robots.
            SOCRobotParameters prevSetting = SOCServer.ROBOT_PARAMS_DEFAULT;
            SOCServer.ROBOT_PARAMS_DEFAULT = SOCServer.ROBOT_PARAMS_SMARTER;   // SOCRobotDM.SMART_STRATEGY

            for (int i = 0; i < numSmart; ++i)
            {
                String rname = "robot " + (i+1+numFast);
                SOCPlayerLocalRobotRunner.createAndStartRobotClientThread(rname, strSocketName, port);
                    // includes yield() and sleep(75 ms) this thread.
            }

            SOCServer.ROBOT_PARAMS_DEFAULT = prevSetting;
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
     * Force this player (not current player) to discard, and report resources to all players.
     * Does not send gameState, which may have changed; see
     * {@link SOCGame#discardOrGainPickRandom(SOCResourceSet, int, boolean, SOCResourceSet, Random)}
     *<P>
     * Assumes, as {@link #endGameTurn(SOCGame, SOCPlayer)} does:
     * <UL>
     * <LI> ga.takeMonitor already called (not the same as {@link SOCGameList#takeMonitorForGame(String)})
     * <LI> gamelist.takeMonitorForGame is NOT called, we do NOT have that monitor
     * </UL>
     *
     * @param cg  Game object
     * @param cpn Game's current player number
     * @param c   Connection of discarding player
     * @param plName Discarding player's name, for GameTextMsg
     * @param pn  Player number who must discard
     */
    private void forceGamePlayerDiscard(SOCGame cg, final int cpn, StringConnection c, String plName, final int pn)
    {
        SOCResourceSet discard = cg.playerDiscardRandom(pn, true);
        final String gaName = cg.getName();
        if ((c != null) && c.isConnected())
            reportRsrcGainLoss(gaName, discard, true, cpn, -1, null, c);
        int totalRes = discard.getTotal();
        messageToGameExcept(gaName, c, new SOCPlayerElement(gaName, cpn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, totalRes), true);
        messageToGame(gaName, plName + " discarded " + totalRes + " resources.");
    }

    /**
     * destroy the game
     *
     * WARNING: MUST HAVE THE gameList.takeMonitor() before
     * calling this method
     *
     * @param gm  the name of the game
     */
    public void destroyGame(String gm)
    {
        //D.ebugPrintln("***** destroyGame("+gm+")");
        SOCGame cg = null;

        cg = gameList.getGameData(gm);
        if (cg == null)
            return;

        if (cg.getGameState() == SOCGame.OVER)
        {
            numberOfGamesFinished++;
        }

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
     * @return the game options (hashtable of {@link SOCGameOption}), or
     *       null if the game doesn't exist or has no options
     * @since 1.1.07
     */
    public Hashtable<String,SOCGameOption> getGameOptions(String gm)
    {
        return gameList.getGameOptions(gm);
    }

    /**
     * the connection c leaves all channels it was in
     *
     * @param c  the connection
     * @return   the channels it was in
     */
    public Vector<?> leaveAllChannels(StringConnection c)
    {
        if (c == null)
            return null;

        Vector<?> ret = new Vector<Object>();
        Vector<String> destroyed = new Vector<String>();

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
                        thisChannelDestroyed = leaveChannel(c, ch, true);
                    }
                    catch (Exception e)
                    {
                        D.ebugPrintStackTrace(e, "Exception in leaveAllChannels (leaveChannel)");
                    }

                    channelList.releaseMonitorForChannel(ch);

                    if (thisChannelDestroyed)
                    {
                        destroyed.addElement(ch);
                    }
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in leaveAllChannels");
        }

        channelList.releaseMonitor();

        /**
         * let everyone know about the destroyed channels
         */
        for (Enumeration<String> de = destroyed.elements(); de.hasMoreElements();)
        {
            String ga = de.nextElement();
            broadcast(SOCDeleteChannel.toCmd(ga));
        }

        return ret;
    }

    /**
     * the connection c leaves all games it was in
     *
     * @param c  the connection
     * @return   the games it was in
     */
    public Vector<String> leaveAllGames(StringConnection c)
    {
        if (c == null)
            return null;

        Vector<String> ret = new Vector<String>();
        Vector<String> destroyed = new Vector<String>();

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
                        thisGameDestroyed = leaveGame(c, ga, true);
                    }
                    catch (Exception e)
                    {
                        D.ebugPrintStackTrace(e, "Exception in leaveAllGames (leaveGame)");
                    }

                    gameList.releaseMonitorForGame(ga);

                    if (thisGameDestroyed)
                    {
                        destroyed.addElement(ga);
                    }

                    ret.addElement(ga);
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in leaveAllGames");
        }

        gameList.releaseMonitor();

        /**
         * let everyone know about the destroyed games
         */
        for (Enumeration<String> de = destroyed.elements(); de.hasMoreElements();)
        {
            String ga = de.nextElement();
            D.ebugPrintln("** Broadcasting SOCDeleteGame " + ga);
            broadcast(SOCDeleteGame.toCmd(ga));
        }

        return ret;
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
     * Send a {@link SOCGameTextMsg} game text message to a player.
     * Equivalent to: messageToPlayer(conn, new {@link SOCGameTextMsg}(ga, {@link #SERVERNAME}, txt));
     *
     * @param c   the player connection
     * @param ga  game name
     * @param txt the message text to send
     * @since 1.1.08
     */
    public void messageToPlayer(StringConnection c, final String ga, final String txt)
    {
        if (c == null)
            return;
        c.put(SOCGameTextMsg.toCmd(ga, SERVERNAME, txt));
    }

    /**
     * Send a message to the given game.
     * <b>Locks:</b> Takes, releases {@link SOCGameList#takeMonitorForGame(String)}.
     *
     * @param ga  the name of the game
     * @param mes the message to send. If mes is a SOCGameTextMsg whose
     *            text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @see #messageToGame(String, String)
     * @see #messageToGameWithMon(String, SOCMessage)
     * @see #messageToGameForVersions(String, int, int, SOCMessage, boolean)
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
     * Equivalent to: messageToGame(ga, new SOCGameTextMsg(ga, {@link #SERVERNAME}, txt));
     *<P>
     * Do not pass SOCSomeMessage.toCmd() into this method; the message type number
     * will be GAMETEXTMSG, not the desired SOMEMESSAGE.
     *<P>
     * <b>Locks:</b> Takes, releases {@link SOCGameList#takeMonitorForGame(String)}.
     *
     * @param ga  the name of the game
     * @param txt the message text to send. If
     *            text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     * @see #messageToGame(String, SOCMessage)
     * @see #messageToGameWithMon(String, SOCMessage)
     * @since 1.1.08
     */
    public void messageToGame(final String ga, final String txt)
    {
        gameList.takeMonitorForGame(ga);

        try
        {
            Vector<StringConnection> v = gameList.getMembers(ga);

            if (v != null)
            {
                final String gameTextMsg = SOCGameTextMsg.toCmd(ga, SERVERNAME, txt);
                Enumeration<StringConnection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection c = menum.nextElement();
                    if (c != null)
                        c.put(gameTextMsg);
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
     * Send a message to the given game.
     *<P>
     *<b>Locks:</b> MUST HAVE THE
     * {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(ga)}
     * before calling this method.
     *
     * @param ga  the name of the game
     * @param mes the message to send
     * @see #messageToGame(String, SOCMessage)
     * @see #messageToGameForVersions(String, int, int, SOCMessage, boolean)
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
     * @since 2.0.00
     */
    public void messageToGameForVersions
        (SOCGame ga, final int vmin, final int vmax, SOCMessage mes, final boolean takeMon)
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
     * @since 2.0.00
     * @see #messageToGameExcept(String, StringConnection, SOCMessage, boolean)
     */
    public void messageToGameForVersionsExcept
        (SOCGame ga, final int vmin, final int vmax, StringConnection ex, SOCMessage mes, final boolean takeMon)
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
                String mesCmd = null;  // will be mes.toCmd()
                Enumeration<StringConnection> menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection con = menum.nextElement();
                    if ((con == null) || (con == ex))
                        continue;

                    final int cv = con.getVersion();
                    if ((cv >= vmin) && (cv <= vmax))
                    {
                        //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", con.getData()));
                        if (mesCmd == null)
                            mesCmd = mes.toCmd();
                        con.put(mesCmd);
                    }
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
     * Like {@link #messageToGame(String, String)}, will take and release the game's monitor.
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
     * Send welcome messages (server version, and the lists of channels and games
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
        c.put(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum()));

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
     * Calls {@link Server#nameConnection(StringConnection)}.
     * Will then adjust game list/channel list if <tt>isReplacing</tt>.
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
        System.err.println("L1819: nameConn(" + c + ", " + isReplacing + ")");  // JM TEMP
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
     * @param str Contents of first message from the client
     * @param con Connection (client) sending this message
     * @return true if processed here (VERSION), false if this message should be
     *         queued up and processed by the normal {@link #processCommand(String, StringConnection)}.
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
     * Treat the incoming messages.  Messages of unknown type are ignored.
     *<P>
     * Called from the single 'treater' thread.
     * <em>Do not block or sleep</em> because this is single-threaded.
     *<P>
     * The first message from a client is treated by
     * {@link #processFirstCommand(String, StringConnection)} instead.
     *<P>
     * Note: When there is a choice, always use local information
     *       over information from the message.  For example, use
     *       the nickname from the connection to get the player
     *       information rather than the player information from
     *       the message.  This makes it harder to send false
     *       messages making players do things they didn't want
     *       to do.
     *
     * @param s    Contents of message from the client
     * @param c    Connection (client) sending this message
     */
    @Override
    public void processCommand(String s, StringConnection c)
    {
        try
        {
            SOCMessage mes = SOCMessage.toMsg(s);

            // D.ebugPrintln(c.getData()+" - "+mes);
            if (mes != null)
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

                    SOCTextMsg textMsgMes = (SOCTextMsg) mes;

                    if (allowDebugUser && c.getData().equals("debug"))
                    {
                        if (textMsgMes.getText().startsWith("*KILLCHANNEL*"))
                        {
                            messageToChannel(textMsgMes.getChannel(), new SOCTextMsg(textMsgMes.getChannel(), SERVERNAME, "********** " + (String) c.getData() + " KILLED THE CHANNEL **********"));
                            channelList.takeMonitor();

                            try
                            {
                                channelList.deleteChannel(textMsgMes.getChannel());
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
                 * someone put a piece on the board
                 */
                case SOCMessage.PUTPIECE:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handlePUTPIECE(c, (SOCPutPiece) mes);

                    //ga = (SOCGame)gamesData.get(((SOCPutPiece)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCPutPiece)mes).getGame());
                    break;

                /**
                 * a player is moving the robber or pirate
                 */
                case SOCMessage.MOVEROBBER:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleMOVEROBBER(c, (SOCMoveRobber) mes);

                    //ga = (SOCGame)gamesData.get(((SOCMoveRobber)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCMoveRobber)mes).getGame());
                    break;

                /**
                 * someone is starting a game
                 */
                case SOCMessage.STARTGAME:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleSTARTGAME(c, (SOCStartGame) mes);

                    //ga = (SOCGame)gamesData.get(((SOCStartGame)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCStartGame)mes).getGame());
                    break;

                case SOCMessage.ROLLDICE:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleROLLDICE(c, (SOCRollDice) mes);

                    //ga = (SOCGame)gamesData.get(((SOCRollDice)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCRollDice)mes).getGame());
                    break;

                case SOCMessage.DISCARD:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleDISCARD(c, (SOCDiscard) mes);

                    //ga = (SOCGame)gamesData.get(((SOCDiscard)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCDiscard)mes).getGame());
                    break;

                case SOCMessage.ENDTURN:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleENDTURN(c, (SOCEndTurn) mes);

                    //ga = (SOCGame)gamesData.get(((SOCEndTurn)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCEndTurn)mes).getGame());
                    break;

                case SOCMessage.CHOOSEPLAYER:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleCHOOSEPLAYER(c, (SOCChoosePlayer) mes);

                    //ga = (SOCGame)gamesData.get(((SOCChoosePlayer)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCChoosePlayer)mes).getGame());
                    break;

                case SOCMessage.MAKEOFFER:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleMAKEOFFER(c, (SOCMakeOffer) mes);

                    //ga = (SOCGame)gamesData.get(((SOCMakeOffer)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCMakeOffer)mes).getGame());
                    break;

                case SOCMessage.CLEAROFFER:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleCLEAROFFER(c, (SOCClearOffer) mes);

                    //ga = (SOCGame)gamesData.get(((SOCClearOffer)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCClearOffer)mes).getGame());
                    break;

                case SOCMessage.REJECTOFFER:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleREJECTOFFER(c, (SOCRejectOffer) mes);

                    //ga = (SOCGame)gamesData.get(((SOCRejectOffer)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCRejectOffer)mes).getGame());
                    break;

                case SOCMessage.ACCEPTOFFER:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleACCEPTOFFER(c, (SOCAcceptOffer) mes);

                    //ga = (SOCGame)gamesData.get(((SOCAcceptOffer)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCAcceptOffer)mes).getGame());
                    break;

                case SOCMessage.BANKTRADE:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleBANKTRADE(c, (SOCBankTrade) mes);

                    //ga = (SOCGame)gamesData.get(((SOCBankTrade)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCBankTrade)mes).getGame());
                    break;

                case SOCMessage.BUILDREQUEST:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleBUILDREQUEST(c, (SOCBuildRequest) mes);

                    //ga = (SOCGame)gamesData.get(((SOCBuildRequest)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCBuildRequest)mes).getGame());
                    break;

                case SOCMessage.CANCELBUILDREQUEST:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleCANCELBUILDREQUEST(c, (SOCCancelBuildRequest) mes);

                    //ga = (SOCGame)gamesData.get(((SOCCancelBuildRequest)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCCancelBuildRequest)mes).getGame());
                    break;

                case SOCMessage.BUYCARDREQUEST:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleBUYCARDREQUEST(c, (SOCBuyCardRequest) mes);

                    //ga = (SOCGame)gamesData.get(((SOCBuyCardRequest)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCBuyCardRequest)mes).getGame());
                    break;

                case SOCMessage.PLAYDEVCARDREQUEST:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handlePLAYDEVCARDREQUEST(c, (SOCPlayDevCardRequest) mes);

                    //ga = (SOCGame)gamesData.get(((SOCPlayDevCardRequest)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCPlayDevCardRequest)mes).getGame());
                    break;

                case SOCMessage.DISCOVERYPICK:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleDISCOVERYPICK(c, (SOCDiscoveryPick) mes);

                    //ga = (SOCGame)gamesData.get(((SOCDiscoveryPick)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCDiscoveryPick)mes).getGame());
                    break;

                case SOCMessage.MONOPOLYPICK:

                    //createNewGameEventRecord();
                    //currentGameEventRecord.setMessageIn(new SOCMessageRecord(mes, c.getData(), "SERVER"));
                    handleMONOPOLYPICK(c, (SOCMonopolyPick) mes);

                    //ga = (SOCGame)gamesData.get(((SOCMonopolyPick)mes).getGame());
                    //currentGameEventRecord.setSnapshot(ga);
                    //saveCurrentGameEventRecord(((SOCMonopolyPick)mes).getGame());
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
                 * debug piece Free Placement (as of 20110104 (v 1.1.12))
                 */
                case SOCMessage.DEBUGFREEPLACE:
                    handleDEBUGFREEPLACE(c, (SOCDebugFreePlace) mes);
                    break;

                /**
                 * Asking to move a previous piece (a ship) somewhere else on the board.
                 * Added 2011-12-04 for v2.0.00.
                 */
                case SOCMessage.MOVEPIECEREQUEST:
                    handleMOVEPIECEREQUEST(c, (SOCMovePieceRequest) mes);
                    break;

                /**
                 * Picking resources to gain from a Gold Hex.
                 * Added 2012-01-12 for v2.0.00.
                 */
                case SOCMessage.PICKRESOURCES:
                    handlePICKRESOURCES(c, (SOCPickResources) mes);
                    break;

                }  // switch (mes.getType)
            }  // if (mes != null)
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "ERROR -> processCommand");
        }

    }  // processCommand

    /**
     * Used by {@link #DEBUG_COMMANDS_HELP}, etc.
     */
    private static final String DEBUG_COMMANDS_HELP_RSRCS
        = "rsrcs: #cl #or #sh #wh #wo playername";

    /**
     * Used by {@link #DEBUG_COMMANDS_HELP}, etc.
     */
    private static final String DEBUG_COMMANDS_HELP_DEV
        = "dev: #typ playername";

    /**
     * Used by {@link #DEBUG_COMMANDS_HELP}, etc. Used with {@link SOCGame#debugFreePlacement}.
     */
    private static final String DEBUG_CMD_FREEPLACEMENT = "*FREEPLACE*";

    /**
     * Used by {@link #processDebugCommand(StringConnection, String, String)}}
     * when *HELP* is requested.
     * @since 1.1.07
     */
    public static final String[] DEBUG_COMMANDS_HELP =
        {
        "--- General Commands ---",
        "*ADDTIME*  add 30 minutes before game expiration",
        "*CHECKTIME*  print time remaining before expiration",
        "*VERSION*  show version and build information",
        "*WHO*   show players and observers of this game",
        "--- Debug Commands ---",
        "*BCAST*  broadcast msg to all games/channels",
        "*GC*    trigger the java garbage-collect",
        "*KILLBOT*  botname  End a bot's connection",
        "*KILLGAME*  end the current game",
        DEBUG_CMD_FREEPLACEMENT + " 1 or 0  Start or end 'Free Placement' mode",
        "*RESETBOT* botname  End a bot's connection",
        "*STATS*   server stats and current-game stats",
        "*STOP*  kill the server",
        "--- Debug Resources ---",
        DEBUG_COMMANDS_HELP_RSRCS,
        "Example  rsrcs: 0 3 0 2 0 Myname",
        DEBUG_COMMANDS_HELP_DEV,
        "Example  dev: 2 Myname",
        "Development card types are:",  // see SOCDevCardConstants
        "1 road-building",
        "2 year of plenty",
        "3 monopoly",
        "4 governors house",
        "5 market",
        "6 university",
        "7 temple",
        "8 chapel",
        "9 robber"
        };

    /**
     * Process a debug command, sent by the "debug" client/player.
     * Check {@link #allowDebugUser} before calling this method.
     * See {@link #DEBUG_COMMANDS_HELP} for list of commands.
     */
    public void processDebugCommand(StringConnection debugCli, String ga, String dcmd)
    {
        final String dcmdU = dcmd.toUpperCase();
        if (dcmdU.startsWith("*HELP"))
        {
            for (int i = 0; i < DEBUG_COMMANDS_HELP.length; ++i)
                messageToPlayer(debugCli, ga, DEBUG_COMMANDS_HELP[i]);
            return;
        }

        if (dcmdU.startsWith("*KILLGAME*"))
        {
            messageToGameUrgent(ga, ">>> ********** " + (String) debugCli.getData() + " KILLED THE GAME!!! ********** <<<");
            gameList.takeMonitor();

            try
            {
                destroyGame(ga);
            }
            catch (Exception e)
            {
                D.ebugPrintStackTrace(e, "Exception in KILLGAME");
            }

            gameList.releaseMonitor();
            broadcast(SOCDeleteGame.toCmd(ga));
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
        else if (dcmdU.startsWith(DEBUG_CMD_FREEPLACEMENT))
        {
            processDebugCommand_freePlace(debugCli, ga, dcmd.substring(DEBUG_CMD_FREEPLACEMENT.length()).trim());
        }
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
     * authenticate the user:
     * see if the user is in the db, if so then check the password.
     * if they're not in the db, but they supplied a password,
     * then send a message (not OK).
     * if they're not in the db, and no password, then ok.
     *
     * @param c         the user's connection
     * @param userName  the user's nickname
     * @param password  the user's password; trim before calling
     * @return true if the user has been authenticated
     */
    private boolean authenticateUser(StringConnection c, String userName, String password)
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

        if (userPassword != null)
        {
            if (!userPassword.equals(password))
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_PW_WRONG, c.getVersion(),
                         "Incorrect password for '" + userName + "'."));

                return false;
            }
        }
        else if (!password.equals(""))
        {
            // No password found in database.
            // (Or, no database connected.)
            // If they supplied a password, it won't work here.

            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NAME_NOT_FOUND, c.getVersion(),
                     "No user with the nickname '" + userName + "' is registered with the system."));

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
     * Handle the client's echo of a {@link SOCMessage#SERVERPING}.
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
     * @param mes  the messsage
     */
    private void handleVERSION(StringConnection c, SOCVersion mes)
    {
        if (c == null)
            return;

        setClientVersSendGamesOrReject(c, mes.getVersionNumber(), true);
    }

    /**
     * Set client's version, and check against minimum required version {@link #CLI_VERSION_MIN}.
     * If version is too low, send {@link SOCRejectConnection REJECTCONNECTION}.
     * If we haven't yet sent the game list, send now.
     * If we've already sent the game list, send changes based on true version.
     *<P>
     * Along with the game list, the client will need to know the game option info.
     * This is sent when the client asks (after VERSION) for {@link SOCGameOptionGetInfos GAMEOPTIONGETINFOS}.
     * Game options are sent after client version is known, so the list of
     * sent options is based on client version.
     *<P>
     *<b>Locks:</b> To set the version, will synchronize briefly on {@link Server#unnamedConns unnamedConns}.
     * If {@link StringConnection#getVersion() c.getVersion()} is already == cvers,
     * don't bother to lock and set it.
     *<P>
     * Package access (not private) is strictly for use of {@link SOCClientData.SOCCDCliVersionTask#run()}.
     *
     * @param c     Client's connection
     * @param cvers Version reported by client, or assumed version if no report
     * @param isKnown Is this the client's definite version, or just an assumed one?
     *                Affects {@link StringConnection#isVersionKnown() c.isVersionKnown}.
     *                Can set the client's known version only once; a second "known" call with
     *                a different cvers will be rejected.
     * @return True if OK, false if rejected
     */
    boolean setClientVersSendGamesOrReject(StringConnection c, final int cvers, final boolean isKnown)
    {
        final int prevVers = c.getVersion();
        final boolean wasKnown = c.isVersionKnown();

        if (prevVers == -1)
            ((SOCClientData) c.getAppData()).clearVersionTimer();

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

        String rejectMsg = null;
        String rejectLogMsg = null;

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
        // This will be displayed in the client's status line.
        if (allowDebugUser)
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_OK, "Debugging is On.  Welcome to Java Settlers of Catan!"));            

        // This client version is OK to connect
        return true;
    }

    /**
     * Handle the "join a channel" message.
     * If client hasn't yet sent its version, assume is
     * version 1.0.00 ({@link #CLI_VERSION_ASSUMED_GUESS}), disconnect if too low.
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleJOIN(StringConnection c, SOCJoin mes)
    {
        if (c == null)
            return;

        D.ebugPrintln("handleJOIN: " + mes);

        int cliVers = c.getVersion();

        /**
         * Check the reported version; if none, assume 1000 (1.0.00)
         */
        if (cliVers == -1)
        {
            if (! setClientVersSendGamesOrReject(c, CLI_VERSION_ASSUMED_GUESS, false))
                return;  // <--- Discon and Early return: Client too old ---
            cliVers = c.getVersion();
        }

        /**
         * Check that the nickname is ok
         */
        boolean isTakingOver = false;

        final String msgUser = mes.getNickname().trim();
        String msgPass = mes.getPassword();
        if (msgPass != null)
            msgPass = msgPass.trim();

        if (c.getData() == null)
        {
            if (msgUser.length() > PLAYER_NAME_MAX_LENGTH)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NEWGAME_NAME_TOO_LONG, cliVers,
                         SOCStatusMessage.MSG_SV_NEWGAME_NAME_TOO_LONG + Integer.toString(PLAYER_NAME_MAX_LENGTH)));
                return;
            }

            final int nameTimeout = checkNickname(msgUser, c, (msgPass != null) && (msgPass.trim().length() > 0));
            if (nameTimeout == -1)
            {
                isTakingOver = true;
            } else if (nameTimeout == -2)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                         MSG_NICKNAME_ALREADY_IN_USE));
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
                         checkNickname_getRetryText(nameTimeout)));
                return;
            }
        }

        if ((c.getData() == null) && (!authenticateUser(c, msgUser, msgPass)))
        {
            return;
        }

        /**
         * Check that the channel name is ok
         */

        /*
           if (!checkChannelName(mes.getChannel())) {
           return;
           }
         */
        final String ch = mes.getChannel().trim();
        if (! SOCMessage.isSingleLineAndSafe(ch))
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NEWGAME_NAME_REJECTED, cliVers,
                     SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED));
              // "This game name is not permitted, please choose a different name."

              return;  // <---- Early return ----
        }

        /**
         * Now that everything's validated, name this connection/user/player.
         * If isTakingOver, also copies their current game/channel count.
         */
        if (c.getData() == null)
        {
            c.setData(msgUser);
            nameConnection(c, isTakingOver);
            numberOfUsers++;
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
                (SOCStatusMessage.SV_OK, "Welcome to Java Settlers of Catan!"));

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
            D.ebugPrintln("*** " + c.getData() + " joined the channel " + ch);
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
     * @param mes  the messsage
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
            destroyedChannel = leaveChannel(c, mes.getChannel(), false);
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
     *<P>
     * Sometimes a bot disconnects and quickly reconnects.  In that case
     * this method removes the disconnect/reconnect messages from
     * {@link Server#cliConnDisconPrintsPending} so they won't be printed.
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleIMAROBOT(StringConnection c, SOCImARobot mes)
    {
        if (c == null)
            return;

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
                System.out.println("Rejected robot " + mes.getNickname() + ": Version " + cliVers + " does not match server version");
                return;  // <--- Early return: Robot client too old ---
            } else {
                System.out.println("Robot arrived: " + mes.getNickname() + ": built-in type");
            }
        } else {
            System.out.println("Robot arrived: " + mes.getNickname() + ": type " + rbc);
        }

        /**
         * Check that the nickname is ok
         */
        if ((c.getData() == null) && (0 != checkNickname(mes.getNickname(), c, false)))
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                     MSG_NICKNAME_ALREADY_IN_USE));
            SOCRejectConnection rcCommand = new SOCRejectConnection(MSG_NICKNAME_ALREADY_IN_USE);
            c.put(rcCommand.toCmd());
            System.err.println("Robot login attempt, name already in use: " + mes.getNickname());
            // c.disconnect();
            c.disconnectSoft();

            return;
        }

        // Idle robots disconnect and reconnect every so often (socket timeout).
        // In case of disconnect-reconnect, don't print the error or re-arrival debug announcements.
        // The robot's nickname is used as the key for the disconnect announcement.
        {
            ConnExcepDelayedPrintTask depart
                = cliConnDisconPrintsPending.get(mes.getNickname());
            if (depart != null)
            {
                depart.cancel();
                cliConnDisconPrintsPending.remove(mes.getNickname());
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
            params = SOCDBHelper.retrieveRobotParams(mes.getNickname());
            if (params != null)
                D.ebugPrintln("*** Robot Parameters for " + mes.getNickname() + " = " + params);
        }
        catch (SQLException sqle)
        {
            System.err.println("Error retrieving robot parameters from db: Using defaults.");
        }

        if (params == null)
        {
            params = new SOCRobotParameters(ROBOT_PARAMS_DEFAULT);
        }

        c.put(SOCUpdateRobotParams.toCmd(params));

        //
        // add this connection to the robot list
        //
        c.setData(mes.getNickname());
        c.setHideTimeoutMessage(true);
        robots.addElement(c);
        SOCClientData scd = (SOCClientData) c.getAppData();
        scd.isRobot = true;
        scd.isBuiltInRobot = isBuiltIn;
        if (! isBuiltIn)
            scd.robot3rdPartyBrainClass = rbc;
        nameConnection(c);
    }

    /**
     * Handle game text messages, including debug commands.
     * Was part of processCommand before 1.1.07.
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

        //currentGameEventRecord.setSnapshot(ga);
        ///
        /// command to add time to a game
        /// If the command text changes from '*ADDTIME*' to something else,
        /// please update the warning text sent in checkForExpiredGames().
        ///
        final String cmdText = gameTextMsgMes.getText();
        final String cmdTxtUC = cmdText.toUpperCase();
        if (cmdTxtUC.startsWith("*ADDTIME*") || cmdTxtUC.startsWith("ADDTIME"))
        {
            // add 30 minutes to the expiration time.  If this
            // changes to another timespan, please update the
            // warning text sent in checkForExpiredGames().
            // Use ">>>" in messageToGame to mark as urgent.
            if (ga.isPractice)
            {
                messageToGameUrgent(gaName, ">>> Practice games never expire.");
            } else {
                ga.setExpiration(ga.getExpiration() + (30 * 60 * 1000));
                messageToGameUrgent(gaName, ">>> This game will expire in " + ((ga.getExpiration() - System.currentTimeMillis()) / 60000) + " minutes.");
            }
        }

        ///
        /// Check the time remaining for this game
        ///
        if (cmdTxtUC.startsWith("*CHECKTIME*"))
        {
            processDebugCommand_checktime(c, gaName, ga);
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
            messageToPlayer(c, gaName, "> Uptime: " + hours + ":" + minutes + ":" + seconds);
            messageToPlayer(c, gaName, "> Connections since startup: " + numberOfConnections);
            messageToPlayer(c, gaName, "> Current named connections: " + getNamedConnectionCount());
            messageToPlayer(c, gaName, "> Current connections including unnamed: " + getCurrentConnectionCount());
            messageToPlayer(c, gaName, "> Total Users: " + numberOfUsers);
            messageToPlayer(c, gaName, "> Games started: " + numberOfGamesStarted);
            messageToPlayer(c, gaName, "> Games finished: " + numberOfGamesFinished);
            messageToPlayer(c, gaName, "> Total Memory: " + rt.totalMemory());
            messageToPlayer(c, gaName, "> Free Memory: " + rt.freeMemory());
            messageToPlayer(c, gaName, "> Version: "
                + Version.versionNumber() + " (" + Version.version() + ") build " + Version.buildnum());

            processDebugCommand_checktime(c, gaName, ga);
        }
        else if (cmdTxtUC.startsWith("*WHO*"))
        {
            Vector<StringConnection> gameMembers = null;
            gameList.takeMonitorForGame(gaName);

            try
            {
                gameMembers = gameList.getMembers(gaName);
            }
            catch (Exception e)
            {
                D.ebugPrintStackTrace(e, "Exception in *WHO* (gameMembers)");
            }

            gameList.releaseMonitorForGame(gaName);

            if (gameMembers != null)
            {
                Enumeration<StringConnection> membersEnum = gameMembers.elements();

                while (membersEnum.hasMoreElements())
                {
                    StringConnection conn = membersEnum.nextElement();
                    messageToGame(gaName, "> " + conn.getData());
                }
            }
        }

        //
        // useful for debugging
        //
        // 1.1.07: all practice games are debug mode, for ease of debugging;
        //         not much use for a chat window in a practice game anyway.
        //
        else if ((allowDebugUser && c.getData().equals("debug")) || (c instanceof LocalStringConnection))
        {
            if (cmdTxtUC.startsWith("RSRCS:"))
            {
                giveResources(c, cmdText, ga);
            }
            else if (cmdTxtUC.startsWith("DEV:"))
            {
                giveDevCard(c, cmdText, ga);
            }
            else if (cmdText.charAt(0) == '*')
            {
                processDebugCommand(c, ga.getName(), cmdText);
            }
            else
            {
                //
                // Send the message to the members of the game
                //
                messageToGame(gaName, new SOCGameTextMsg(gaName, (String) c.getData(), cmdText));
            }
        }
        else
        {
            //
            // Send the message to the members of the game
            //
            messageToGame(gaName, new SOCGameTextMsg(gaName, (String) c.getData(), cmdText));
        }

        //saveCurrentGameEventRecord(gameTextMsgMes.getGame());
    }

    /**
     * Print time-remaining and other game stats.
     * Includes more detail beyond the end-game stats sent in {@link #sendGameStateOVER(SOCGame)}.
     * @param c  Client requesting the stats
     * @param gameData  Game to print stats
     * @since 1.1.07
     */
    private void processDebugCommand_checktime(StringConnection c, final String gaName, SOCGame gameData)
    {
        if (gameData == null)
            return;
        messageToPlayer(c, gaName, "-- Game statistics: --");
        messageToPlayer(c, gaName, "Rounds played: " + gameData.getRoundCount());

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
            String gLengthMsg = "This game started " + gameMinutes + " minutes ago.";
            messageToPlayer(c, gaName, gLengthMsg);
            // Ignore possible "1 minutes"; that game is too short to worry about.
        }

        if (! gameData.isPractice)   // practice games don't expire
        {
            String expireMsg = ">>> This game will expire in " + ((gameData.getExpiration() - System.currentTimeMillis()) / 60000) + " minutes.";
            messageToPlayer(c, gaName, expireMsg);
        }
    }

    /**
     * Process the <tt>*FREEPLACE*</tt> Free Placement debug command.
     * Can turn it off at any time, but can only turn it on during
     * your own turn after rolling (during game state {@link SOCGame#PLAY1}).
     * @param c   Connection (client) sending this message
     * @param gaName  Game to which this applies
     * @param arg  1 or 0, to turn on or off, or empty string or
     *    null to print current value
     * @since 1.1.12
     */
    private final void processDebugCommand_freePlace
        (StringConnection c, final String gaName, final String arg)
    {
        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;
        final boolean wasInitial = ga.isInitialPlacement();
        final boolean ppValue = ga.isDebugFreePlacement();
        final boolean ppWanted;
        if ((arg == null) || (arg.length() == 0))
            ppWanted = ppValue;
        else
            ppWanted = arg.equals("1");

        if (ppValue != ppWanted)
        {
            if (! ppWanted)
            {
                try
                {
                    ga.setDebugFreePlacement(false);
                }
                catch (IllegalStateException e)
                {
                    if (wasInitial)
                    {
                        messageToPlayer
                          (c, gaName, "* To exit this debug mode, all players must have either");
                        messageToPlayer
                          (c, gaName, "  1 settlement and 1 road, or all must have at least 2 of each.");
                    } else {
                        messageToPlayer
                          (c, gaName, "* Could not exit this debug mode: " + e.getMessage());
                    }
                    return;  // <--- early return ---
                }
            } else {
                if (c.getVersion() < SOCDebugFreePlace.VERSION_FOR_DEBUGFREEPLACE)
                {
                    messageToPlayer
                        (c, gaName, "* Requires client version "
                         + Version.version(SOCDebugFreePlace.VERSION_FOR_DEBUGFREEPLACE)
                         + " or newer.");
                    return;  // <--- early return ---
                }
                SOCPlayer cliPl = ga.getPlayer((String) c.getData());
                if (cliPl == null)
                    return;  // <--- early return ---
                if (ga.getCurrentPlayerNumber() != cliPl.getPlayerNumber())
                {
                    messageToPlayer
                        (c, gaName, "* Can do this only on your own turn.");
                    return;  // <--- early return ---
                }
                if ((ga.getGameState() != SOCGame.PLAY1)
                    && ! ga.isInitialPlacement())
                {
                    messageToPlayer
                        (c, gaName, "* Can do this only after rolling the dice.");
                    return;  // <--- early return ---
                }

                ga.setDebugFreePlacement(true);
            }
        }

        messageToPlayer
            (c, gaName, "- Free Placement mode is "
             + (ppWanted ? "ON -" : "off -" ));

        if (ppValue != ppWanted)
        {
            messageToPlayer(c, new SOCDebugFreePlace(gaName, ga.getCurrentPlayerNumber(), ppWanted));
            if (wasInitial && ! ppWanted)
            {
                boolean toldRoll = sendGameState(ga, false);
                if (!checkTurn(c, ga))
                {
                    // Player changed (or play started), announce new player.
                    sendTurn(ga, toldRoll);
                }
            }
        }
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
     * @param mes  the messsage
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
            if (! setClientVersSendGamesOrReject(c, CLI_VERSION_ASSUMED_GUESS, false))
                return;  // <--- Early return: Client too old ---
        }

        createOrJoinGameIfUserOK
            (c, mes.getNickname().trim(), mes.getPassword(), mes.getGame().trim(), null);
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
     *      Comparison is done by {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable, boolean)}.
     *  <LI> if ok: create new game with params;
     *      socgame will calc game's minCliVersion,
     *      and this method will check that against cli's version.
     *  <LI> announce to all players using NEWGAMEWITHOPTIONS;
     *       older clients get NEWGAME, won't see the options
     *  <LI> send JOINGAMEAUTH to requesting client, via {@link #joinGame(SOCGame, StringConnection, boolean, boolean)}
     *  <LI> send game status details to requesting client, via {@link #joinGame(SOCGame, StringConnection, boolean, boolean)}
     *</UL>
     *
     * @param c connection requesting the game, must not be null
     * @param msgUser username of client in message. Must pass {@link SOCMessage#isSingleLineAndSafe(String)}
     *                  and be at most {@link #PLAYER_NAME_MAX_LENGTH} characters.
     * @param msgPass password of client in message
     * @param gameName  name of game to create/join. Must pass {@link SOCMessage#isSingleLineAndSafe(String)}
     *                  and be at most {@link #GAME_NAME_MAX_LENGTH} characters.
     * @param gameOpts  if game has options, contains {@link SOCGameOption} to create new game; if not null, will not join an existing game.
     *                  Will validate and adjust by calling
     *                  {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable, boolean)}
     *                  with <tt>doServerPreadjust</tt> true.
     *
     * @since 1.1.07
     */
    private void createOrJoinGameIfUserOK
        (StringConnection c, final String msgUser, String msgPass,
         final String gameName, Hashtable<String, SOCGameOption> gameOpts)
    {
        if (msgPass != null)
            msgPass = msgPass.trim();

        /**
         * Check that the nickname is ok
         */
        final int cliVers = c.getVersion();
        boolean isTakingOver = false;
        if (c.getData() == null)
        {
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
            final int nameTimeout = checkNickname(msgUser, c, (msgPass != null) && (msgPass.trim().length() > 0));

            if (nameTimeout == -1)
            {
                isTakingOver = true;
            } else if (nameTimeout == -2)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                         MSG_NICKNAME_ALREADY_IN_USE));
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
                         checkNickname_getRetryText(nameTimeout)));
                return;
            }
        }

        /**
         * password check new connection from database, if possible
         */
        if ((c.getData() == null) && (!authenticateUser(c, msgUser, msgPass)))
        {
            return;  // <---- Early return: Password auth failed ----
        }

        /**
         * Check that the game name is ok
         */
        if (! SOCMessage.isSingleLineAndSafe(gameName))
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

        /**
         * Now that everything's validated, name this connection/user/player.
         * If isTakingOver, also copies their current game/channel count.
         */
        if (c.getData() == null)
        {
            c.setData(msgUser);
            nameConnection(c, isTakingOver);
            numberOfUsers++;
        }

        /**
         * If creating a new game, ensure they are below their max game count.
         */
        if ((! gameList.isGame(gameName))
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
            final StringBuffer optProblems = SOCGameOption.adjustOptionsToKnown(gameOpts, null, true);
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
                            "You've taken over the connection, but aren't in any games."));
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
                SOCGame gameData = gameList.getGameData(gameName);

                if (gameData != null)
                {
                    joinGame(gameData, c, false, false);
                }
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

    }  //  createOrJoinGameIfUserOK

    /**
     * Handle the "leave game" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
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
            gameDestroyed = leaveGame(c, gaName, false);
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
     * @param mes  the messsage
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
        boolean gameIsFull = false;

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
         * make sure a person isn't sitting here already;
         * if a robot is sitting there, dismiss the robot.
         */
        ga.takeMonitor();

        try
        {
            if (ga.isSeatVacant(mes.getPlayerNumber()))
            {
                gameIsFull = (1 > ga.getAvailableSeatCount());
                if (gameIsFull)
                    canSit = false;
            } else {
                SOCPlayer seatedPlayer = ga.getPlayer(mes.getPlayerNumber());

                if (seatedPlayer.isRobot() && (!ga.isSeatLocked(mes.getPlayerNumber())) && (ga.getCurrentPlayerNumber() != mes.getPlayerNumber()))
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

        /**
         * if this is a robot, remove it from the request list
         */
        Vector<StringConnection> joinRequests = robotJoinRequests.get(gaName);

        if (joinRequests != null)
        {
            joinRequests.removeElement(c);
        }

        //D.ebugPrintln("canSit 2 = "+canSit);
        if (canSit)
        {
            sitDown(ga, c, mes.getPlayerNumber(), mes.isRobot(), false);
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
            } else if (gameIsFull) {
                messageToPlayer(c, gaName, "This game is full, you cannot sit down.");
            }
        }
    }

    /**
     * handle "put piece" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handlePUTPIECE(StringConnection c, SOCPutPiece mes)
    {
        if (c == null)
            return;
        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            final String plName = (String) c.getData();
            SOCPlayer player = ga.getPlayer(plName);

            /**
             * make sure the player can do it
             */
            if (checkTurn(c, ga))
            {
                boolean sendDenyReply = false;
                /*
                   if (D.ebugOn) {
                   D.ebugPrintln("BEFORE");
                   for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++) {
                   SOCPlayer tmpPlayer = ga.getPlayer(pn);
                   D.ebugPrintln("Player # "+pn);
                   for (int i = 0x22; i < 0xCC; i++) {
                   if (tmpPlayer.isPotentialRoad(i))
                   D.ebugPrintln("### POTENTIAL ROAD AT "+Integer.toHexString(i));
                   }
                   }
                   }
                 */

                final int gameState = ga.getGameState();
                final int coord = mes.getCoordinates();
                final int pn = player.getPlayerNumber();

                switch (mes.getPieceType())
                {
                case SOCPlayingPiece.ROAD:

                    SOCRoad rd = new SOCRoad(player, coord, null);

                    if ((gameState == SOCGame.START1B) || (gameState == SOCGame.START2B) || (gameState == SOCGame.START3B)
                        || (gameState == SOCGame.PLACING_ROAD) || (gameState == SOCGame.PLACING_FREE_ROAD1) || (gameState == SOCGame.PLACING_FREE_ROAD2))
                    {
                        if (player.isPotentialRoad(coord) && (player.getNumPieces(SOCPlayingPiece.ROAD) >= 1))
                        {
                            ga.putPiece(rd);  // Changes state and sometimes player (initial placement)

                            // If placing this piece reveals a fog hex, putPiece will call srv.gameEvent
                            // which will send a SOCRevealFogHex message to the game.

                            /*
                               if (D.ebugOn) {
                               D.ebugPrintln("AFTER");
                               for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++) {
                               SOCPlayer tmpPlayer = ga.getPlayer(pn);
                               D.ebugPrintln("Player # "+pn);
                               for (int i = 0x22; i < 0xCC; i++) {
                               if (tmpPlayer.isPotentialRoad(i))
                               D.ebugPrintln("### POTENTIAL ROAD AT "+Integer.toHexString(i));
                               }
                               }
                               }
                             */
                            gameList.takeMonitorForGame(gaName);
                            messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, plName + " built a road."));
                            messageToGameWithMon(gaName, new SOCPutPiece(gaName, pn, SOCPlayingPiece.ROAD, coord));
                            if (! ga.pendingMessagesOut.isEmpty())
                            {
                                for (final Object msg : ga.pendingMessagesOut)
                                    messageToGameWithMon(gaName, (SOCMessage) msg);
                                ga.pendingMessagesOut.clear();
                            }
                            gameList.releaseMonitorForGame(gaName);

                            boolean toldRoll = sendGameState(ga, false);
                            broadcastGameStats(ga);
                            if ((ga.getGameState() == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
                                || (ga.getGameState() == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE))
                            {
                                // gold hex revealed from fog (scenario SC_FOG)
                                sendGameState_sendGoldPickAnnounceText(ga, gaName, c);
                            }

                            if (!checkTurn(c, ga))
                            {
                                // Player changed (or play started), announce new player.
                                sendTurn(ga, true);
                            }
                            else if (toldRoll)
                            {
                                // When play starts, or after placing 2nd free road,
                                // announce even though player unchanged,
                                // to trigger auto-roll for the player.
                                // If the client is too old (1.0.6), it will ignore the prompt.
                                messageToGame(gaName, new SOCRollDicePrompt (gaName, pn));
                            }
                        }
                        else
                        {
                            D.ebugPrintln("ILLEGAL ROAD: 0x" + Integer.toHexString(coord)
                                + ": player " + pn);
                            messageToPlayer(c, gaName, "You can't build a road there.");
                            sendDenyReply = true;
                        }
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You can't build a road right now.");
                    }

                    break;

                case SOCPlayingPiece.SETTLEMENT:

                    SOCSettlement se = new SOCSettlement(player, coord, null);

                    if ((gameState == SOCGame.START1A) || (gameState == SOCGame.START2A)
                        || (gameState == SOCGame.START3A) || (gameState == SOCGame.PLACING_SETTLEMENT))
                    {
                        if (player.canPlaceSettlement(coord) && (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) >= 1))
                        {
                            ga.putPiece(se);   // Changes game state and (if game start) player
                            gameList.takeMonitorForGame(gaName);
                            messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, plName + " built a settlement."));
                            messageToGameWithMon(gaName, new SOCPutPiece(gaName, pn, SOCPlayingPiece.SETTLEMENT, coord));
                            if (! ga.pendingMessagesOut.isEmpty())
                            {
                                for (final Object msg : ga.pendingMessagesOut)
                                    messageToGameWithMon(gaName, (SOCMessage) msg);
                                ga.pendingMessagesOut.clear();
                            }
                            gameList.releaseMonitorForGame(gaName);
                            broadcastGameStats(ga);

                            // Check and send new game state
                            sendGameState(ga);
                            if (ga.hasSeaBoard && (ga.getGameState() == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE))
                            {
                                // Prompt to pick from gold: send text and SOCPickResourcesRequest
                                sendGameState_sendGoldPickAnnounceText(ga, gaName, c);
                            }

                            if (!checkTurn(c, ga))
                            {
                                sendTurn(ga, false);  // Announce new current player.
                            }
                        }
                        else
                        {
                            D.ebugPrintln("ILLEGAL SETTLEMENT: 0x" + Integer.toHexString(coord)
                                + ": player " + pn);
                            messageToPlayer(c, gaName, "You can't build a settlement there.");
                            sendDenyReply = true;
                        }
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You can't build a settlement right now.");
                    }

                    break;

                case SOCPlayingPiece.CITY:

                    SOCCity ci = new SOCCity(player, coord, null);

                    if (gameState == SOCGame.PLACING_CITY)
                    {
                        if (player.isPotentialCity(coord) && (player.getNumPieces(SOCPlayingPiece.CITY) >= 1))
                        {
                            ga.putPiece(ci);  // changes game state and maybe player
                            gameList.takeMonitorForGame(gaName);
                            messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, plName + " built a city."));
                            messageToGameWithMon(gaName, new SOCPutPiece(gaName, pn, SOCPlayingPiece.CITY, coord));
                            if (! ga.pendingMessagesOut.isEmpty())
                            {
                                for (final Object msg : ga.pendingMessagesOut)
                                    messageToGameWithMon(gaName, (SOCMessage) msg);
                                ga.pendingMessagesOut.clear();
                            }
                            gameList.releaseMonitorForGame(gaName);
                            broadcastGameStats(ga);
                            sendGameState(ga);

                            if (!checkTurn(c, ga))
                            {
                                sendTurn(ga, false);  // announce new current player
                            }
                        }
                        else
                        {
                            D.ebugPrintln("ILLEGAL CITY: 0x" + Integer.toHexString(coord)
                                + ": player " + pn);
                            messageToPlayer(c, gaName, "You can't build a city there.");
                            sendDenyReply = true;
                        }
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You can't build a city right now.");
                    }

                    break;

                case SOCPlayingPiece.SHIP:

                    SOCShip sh = new SOCShip(player, coord, null);

                    if ((gameState == SOCGame.START1B) || (gameState == SOCGame.START2B) || (gameState == SOCGame.START3B)
                        || (gameState == SOCGame.PLACING_SHIP) || (gameState == SOCGame.PLACING_FREE_ROAD1) || (gameState == SOCGame.PLACING_FREE_ROAD2))
                    {
                        // Place it if we can; canPlaceShip checks potentials and pirate ship location
                        if (ga.canPlaceShip(player, coord) && (player.getNumPieces(SOCPlayingPiece.SHIP) >= 1))
                        {
                            ga.putPiece(sh);  // Changes state and sometimes player (during initial placement)

                            gameList.takeMonitorForGame(gaName);
                            messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, plName + " built a ship."));
                            messageToGameWithMon(gaName, new SOCPutPiece(gaName, pn, SOCPlayingPiece.SHIP, coord));
                            if (! ga.pendingMessagesOut.isEmpty())
                            {
                                for (final Object msg : ga.pendingMessagesOut)
                                    messageToGameWithMon(gaName, (SOCMessage) msg);
                                ga.pendingMessagesOut.clear();
                            }
                            gameList.releaseMonitorForGame(gaName);

                            boolean toldRoll = sendGameState(ga, false);
                            broadcastGameStats(ga);
                            if ((ga.getGameState() == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
                                || (ga.getGameState() == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE))
                            {
                                // gold hex revealed from fog (scenario SC_FOG)
                                sendGameState_sendGoldPickAnnounceText(ga, gaName, c);
                            }

                            if (!checkTurn(c, ga))
                            {
                                // Player changed (or play started), announce new player.
                                sendTurn(ga, true);
                            }
                            else if (toldRoll)
                            {
                                // When play starts, or after placing 2nd free road,
                                // announce even though player unchanged,
                                // to trigger auto-roll for the player.
                                // If the client is too old (1.0.6), it will ignore the prompt.
                                messageToGame(gaName, new SOCRollDicePrompt (gaName, pn));
                            }
                        }
                        else
                        {
                            D.ebugPrintln("ILLEGAL SHIP: 0x" + Integer.toHexString(coord)
                                + ": player " + pn);
                            messageToPlayer(c, gaName, "You can't build a ship there.");
                            sendDenyReply = true;
                        }
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You can't build a ship right now.");
                    }

                    break;

                }  // switch (mes.getPieceType())

                if (sendDenyReply)
                {
                    messageToPlayer(c, new SOCCancelBuildRequest(gaName, mes.getPieceType()));
                    if (player.isRobot())
                    {
                        // Set the "force end turn soon" field
                        ga.lastActionTime = 0L;
                    }
                }
            }
            else
            {
                messageToPlayer(c, gaName, "It's not your turn.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught in handlePUTPIECE");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "move robber" message (move the robber or the pirate).
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleMOVEROBBER(StringConnection c, SOCMoveRobber mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            SOCPlayer player = ga.getPlayer((String) c.getData());

            /**
             * make sure the player can do it
             */
            final String gaName = ga.getName();
            final boolean isPirate = ga.getRobberyPirateFlag();
            final int pn = player.getPlayerNumber();
            int coord = mes.getCoordinates();  // negative for pirate
            final boolean canDo =
                (isPirate == (coord < 0))
                && (isPirate ? ga.canMovePirate(pn, -coord)
                             : ga.canMoveRobber(pn, coord));
            if (canDo)
            {
                SOCMoveRobberResult result;
                SOCMoveRobber moveMsg;
                if (isPirate)
                {
                    result = ga.movePirate(pn, -coord);
                    moveMsg = new SOCMoveRobber(gaName, pn, coord);
                } else {
                    result = ga.moveRobber(pn, coord);
                    moveMsg = new SOCMoveRobber(gaName, pn, coord);
                }
                messageToGame(gaName, moveMsg);

                Vector<SOCPlayer> victims = result.getVictims();

                /** only one possible victim */
                if ((victims.size() == 1) && (ga.getGameState() != SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE))
                {
                    /**
                     * report what was stolen
                     */
                    SOCPlayer victim = victims.firstElement();
                    reportRobbery(ga, player, victim, result.getLoot());
                }

                else
                {
                    StringBuffer msgtext = new StringBuffer(player.getName());
                    msgtext.append(" moved the ");
                    if (isPirate)
                        msgtext.append("pirate");
                    else
                        msgtext.append("robber");

                    /** no victim */
                    if (victims.size() == 0)
                    {
                        /**
                         * just say it was moved; nothing is stolen
                         */
                        msgtext.append(".");
                    }
                    else if (ga.getGameState() == SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE)
                    {
                        /**
                         * only one possible victim, they have both clay and resources
                         */
                        msgtext.append(", must choose to steal cloth or steal resources.");
                    }
                    else
                    {
                        /**
                         * else, the player needs to choose a victim
                         */
                        msgtext.append(", must choose a victim.");
                    }

                    messageToGame(gaName, msgtext.toString());
                }

                sendGameState(ga);
                    // For WAITING_FOR_CHOICE, sendGameState also sends messages
                    // with victim info to prompt the client to choose.
                    // For WAITING_FOR_ROB_CLOTH_OR_RESOURCE, no need to recalculate
                    // victims there, just send the prompt from here:
                if (ga.getGameState() == SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE)
                {
                    final int vpn = victims.firstElement().getPlayerNumber();
                    messageToPlayer(c, new SOCChoosePlayer(gaName, vpn));
                }
            }
            else
            {
                messageToPlayer(c, gaName, "You can't move the " + ((coord < 0) ? "pirate." : "robber."));
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "start game" message.  Game state must be NEW, or this message is ignored.
     * {@link #readyGameAskRobotsJoin(SOCGame, StringConnection[]) Ask some robots} to fill
     * empty seats, or {@link #startGame(SOCGame) begin the game} if no robots needed.
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleSTARTGAME(StringConnection c, SOCStartGame mes)
    {
        if (c == null)
            return;

        String gn = mes.getGame();
        SOCGame ga = gameList.getGameData(gn);

        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            if (ga.getGameState() == SOCGame.NEW)
            {
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
                        if (ga.isSeatLocked(i))
                        {
                            anyLocked = true;
                        }
                        else
                        {
                            seatsFull = false;
                            ++numEmpty;
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

                if (seatsFull && (numPlayers < 2))
                {
                    seatsFull = false;
                    numEmpty = 3;
                    String m = "Sorry, the only player cannot lock all seats.";
                    messageToGame(gn, m);
                }
                else if (!seatsFull)
                {
                    if (robots.isEmpty())
                    {
                        if (numPlayers < SOCGame.MINPLAYERS)
                        {
                            messageToGame(gn,
                                "No robots on this server, please fill at least "
                                + SOCGame.MINPLAYERS + " seats before starting." );
                        }
                        else
                        {
                            seatsFull = true;  // Enough players to start game.
                        }
                    }
                    else
                    {
                        //
                        // make sure there are enough robots connected,
                        // then set gamestate READY and ask them to connect.
                        //
                        if (numEmpty > robots.size())
                        {
                            String m;
                            if (anyLocked)
                                m = "Sorry, not enough robots to fill all the seats.  Only " + robots.size() + " robots are available.";
                            else
                                m = "Sorry, not enough robots to fill all the seats.  Lock some seats.  Only " + robots.size() + " robots are available.";
                            messageToGame(gn, m);
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
                                readyGameAskRobotsJoin(ga, null);
                            }
                            catch (IllegalStateException e)
                            {
                                String m = "Sorry, robots cannot join this game: " + e.getMessage();
                                messageToGame(gn, m);
                                System.err.println("Robot-join problem in game " + gn + ": " + m);
                            }
                        }
                    }
                }

                /**
                 * If this doesn't need robots, then start the game.
                 * Otherwise wait for them to sit before starting the game.
                 */
                if (seatsFull)
                {
                    startGame(ga);
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
     *
     * @throws IllegalStateException if {@link SOCGame#getGameState() ga.gamestate} is not READY,
     *         or if {@link SOCGame#getClientVersionMinRequired() ga.version} is
     *         somehow newer than server's version (which is assumed to be robots' version).
     * @throws IllegalArgumentException if robotSeats is not null but wrong length,
     *           or if a robotSeat element is null but that seat wants a robot (vacant non-locked).
     */
    private void readyGameAskRobotsJoin(SOCGame ga, StringConnection[] robotSeats)
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
        final Hashtable<String, SOCGameOption> gopts = ga.getGameOptions();
        int seatsOpen = ga.getAvailableSeatCount();
        int idx = 0;
        StringConnection[] robotSeatsConns = new StringConnection[ga.maxPlayers];

        for (int i = 0; (i < ga.maxPlayers) && (seatsOpen > 0); i++)
        {
            if (ga.isSeatVacant(i) && ! ga.isSeatLocked(i))
            {
                /**
                 * fetch a robot player
                 */
                if (idx < robots.size())
                {
                    messageToGame(gname, "Fetching a robot player...");

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
                    robotSeatsConns[i].put(SOCJoinGameRequest.toCmd(gname, i, gopts));
        }
    }

    /**
     * handle "roll dice" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleROLLDICE(StringConnection c, SOCRollDice mes)
    {
        if (c == null)
            return;

        final String gn = mes.getGame();
        SOCGame ga = gameList.getGameData(gn);
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            final String plName = (String) c.getData();
            final SOCPlayer pl = ga.getPlayer(plName);
            if ((pl != null) && ga.canRollDice(pl.getPlayerNumber()))
            {
                /**
                 * Roll dice, distribute resources in game
                 */
                SOCGame.RollResult roll = ga.rollDice();

                /**
                 * Send roll results and then text to client.
                 * Client expects to see DiceResult first, then text message;
                 * to reduce visual clutter, SOCPlayerInterface.print
                 * expects text message to follow a certain format.
                 * If a 7 is rolled, sendGameState will also say who must discard
                 * (in a GAMETEXTMSG).
                 * If a gold hex is rolled, sendGameState will also say who
                 * must pick resources to gain (in a GAMETEXTMSG).
                 */
                messageToGame(gn, new SOCDiceResult(gn, ga.getCurrentDice()));
                messageToGame(gn, plName + " rolled a " + roll.diceA + " and a " + roll.diceB + ".");
                sendGameState(ga);  // For 7, give visual feedback before sending discard request

                /**
                 * if the roll is not 7, tell players what they got
                 */
                if (ga.getCurrentDice() != 7)
                {
                    boolean noPlayersGained = true;
                    StringBuffer gainsText = new StringBuffer();

                    for (int i = 0; i < ga.maxPlayers; i++)
                    {
                        if (! ga.isSeatVacant(i))
                        {
                            SOCPlayer pli = ga.getPlayer(i);
                            SOCResourceSet rsrcs = pli.getRolledResources();

                            if (rsrcs.getKnownTotal() != 0)
                            {
                                if (noPlayersGained)
                                {
                                    noPlayersGained = false;
                                }
                                else
                                {
                                    gainsText.append(" ");
                                }

                                gainsText.append(pli.getName());
                                gainsText.append(" gets ");
                                // Announce SOCPlayerElement.GAIN messages,
                                // build resource-text in gainsText.
                                reportRsrcGainLoss(gn, rsrcs, false, i, -1, gainsText, null);
                                gainsText.append(".");
                            }

                            //
                            //  Send player all their resource info for accuracy
                            //  and prompt if they must pick for GOLD_HEX
                            //
                            StringConnection playerCon = getConnection(pli.getName());
                            if (playerCon != null)
                            {
                                // CLAY, ORE, SHEEP, WHEAT, WOOD
                                SOCResourceSet resources = pli.getResources();
                                for (int res = SOCPlayerElement.CLAY; res <= SOCPlayerElement.WOOD; ++res)
                                    messageToPlayer(playerCon, new SOCPlayerElement(gn, i, SOCPlayerElement.SET, res, resources.getAmount(res)));
                                messageToGame(gn, new SOCResourceCount(gn, i, resources.getTotal()));

                                if (ga.hasSeaBoard)
                                {
                                    final int numGoldRes = pli.getNeedToPickGoldHexResources();
                                    if (numGoldRes > 0)
                                    {
                                        messageToPlayer(playerCon, new SOCPickResourcesRequest(gn, numGoldRes));
                                        // We'll send text and PLAYERELEMENT about the gold picks after the loop.
                                    }
                                }
                            }
                        }  // if (! ga.isSeatVacant(i))
                    }  // for (i)

                    String message;
                    if (noPlayersGained)
                    {
                        if (roll.cloth == null)
                            message = "No player gets anything.";
                        else
                            message = "No player gets resources.";
                        // debug_printPieceDiceNumbers(ga, message);
                    }
                    else
                    {
                        message = gainsText.toString();
                    }
                    messageToGame(gn, message);

                    if (roll.cloth != null)
                    {
                        // Send village cloth trade distribution

                        final int coord = roll.cloth[1];
                        final SOCBoardLarge board = (SOCBoardLarge) (ga.getBoard()); 
                        SOCVillage vi = board.getVillageAtNode(coord);
                        if (vi != null)
                            messageToGame(gn, new SOCPieceValue(gn, coord, vi.getCloth(), 0));

                        if (roll.cloth[0] > 0)
                            // some taken from board general supply
                            messageToGame(gn, new SOCPlayerElement
                                (gn, -1, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_CLOTH_COUNT, board.getCloth()));

                        StringBuilder sb = null;
                        int nPl = 0;
                        for (int i = 2; i < roll.cloth.length; ++i)
                        {
                            if (roll.cloth[i] == 0)
                                continue;  // this player didn't receive cloth

                            final int pn = i - 2;
                            final SOCPlayer clpl = ga.getPlayer(pn);
                            messageToGame(gn, new SOCPlayerElement
                                (gn, pn, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_CLOTH_COUNT, clpl.getCloth()));

                            if (sb == null)
                            {
                                sb = new StringBuilder(clpl.getName());
                            } else {
                                sb.append(", ");
                                sb.append(clpl.getName());
                            }
                            ++nPl;
                        }
                        if (nPl > 1)
                            sb.append(" each");
                        sb.append(" received 1 cloth from a village.");
                        messageToGame(gn, sb.toString());
                    }

                    if (ga.getGameState() == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                        sendGameState_sendGoldPickAnnounceText(ga, gn, null);

                    /*
                       if (D.ebugOn) {
                       for (int i=0; i < SOCGame.MAXPLAYERS; i++) {
                       SOCResourceSet rsrcs = ga.getPlayer(i).getResources();
                       String resourceMessage = "PLAYER "+i+" RESOURCES: ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.CLAY)+" ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.ORE)+" ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.SHEEP)+" ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.WHEAT)+" ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.WOOD)+" ";
                       resourceMessage += rsrcs.getAmount(SOCResourceConstants.UNKNOWN)+" ";
                       messageToGame(gn, new SOCGameTextMsg(gn, SERVERNAME, resourceMessage));
                       }
                       }
                     */
                }
                else
                {
                    /**
                     * player rolled 7
                     * If anyone needs to discard, prompt them.
                     */
                    for (int i = 0; i < ga.maxPlayers; i++)
                    {
                        if (( ! ga.isSeatVacant(i))
                            && (ga.getPlayer(i).getResources().getTotal() > 7))
                        {
                            // Request to discard half (round down)
                            StringConnection con = getConnection(ga.getPlayer(i).getName());
                            if (con != null)
                            {
                                con.put(SOCDiscardRequest.toCmd(ga.getName(), ga.getPlayer(i).getResources().getTotal() / 2));
                            }
                        }
                    }
                }
            }
            else
            {
                messageToPlayer(c, gn, "You can't roll right now.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught at handleROLLDICE" + e);
        }

        ga.releaseMonitor();
    }

    /**
     * Temporary debugging; call when "no player gets anything" will be printed after a roll.
     * @param ga  Game data
     * @param message  "no player gets anything" string
     * @since 2.0.00
     */
    private void debug_printPieceDiceNumbers(SOCGame ga, String message)
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
     * handle "discard" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleDISCARD(StringConnection c, SOCDiscard mes)
    {
        if (c == null)
            return;

        final String gn = mes.getGame();
        SOCGame ga = gameList.getGameData(gn);
        if (ga == null)
            return;

        final SOCPlayer player = ga.getPlayer((String) c.getData());
        final int pn;
        if (player != null)
            pn = player.getPlayerNumber();
        else
            pn = -1;  // c's client no longer in the game

        ga.takeMonitor();
        try
        {
            if (player == null)
            {
                // The catch block will print this out semi-nicely
                throw new IllegalArgumentException("player not found in game");
            }

            if (ga.canDiscard(pn, mes.getResources()))
            {
                ga.discard(pn, mes.getResources());

                /**
                 * tell the player client that the player discarded the resources
                 */
                reportRsrcGainLoss(gn, mes.getResources(), true, pn, -1, null, c);

                /**
                 * tell everyone else that the player discarded unknown resources
                 */
                messageToGameExcept(gn, c, new SOCPlayerElement(gn, pn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, mes.getResources().getTotal()), true);
                messageToGame(gn, (String) c.getData() + " discarded " + mes.getResources().getTotal() + " resources.");

                /**
                 * send the new state, or end turn if was marked earlier as forced
                 */
                if ((ga.getGameState() != SOCGame.PLAY1) || ! ga.isForcingEndTurn())
                {
                    sendGameState(ga);
                } else {
                    endGameTurn(ga, player);  // already did ga.takeMonitor()
                }
            }
            else
            {
                /**
                 * (TODO) there could be a better feedback message here
                 */
                messageToPlayer(c, gn, "You can't discard that many cards.");
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * Handle "pick resources" message (gold hex).
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     * @since 2.0.00
     */
    private void handlePICKRESOURCES(StringConnection c, SOCPickResources mes)
    {
        if (c == null)
            return;

        final String gn = mes.getGame();
        SOCGame ga = gameList.getGameData(gn);
        if (ga == null)
            return;

        final SOCPlayer player = ga.getPlayer((String) c.getData());
        final int pn;
        if (player != null)
            pn = player.getPlayerNumber();
        else
            pn = -1;  // c's client no longer in the game

        ga.takeMonitor();
        try
        {
            if (player == null)
            {
                // The catch block will print this out semi-nicely
                throw new IllegalArgumentException("player not found in game");
            }

            final SOCResourceSet rsrcs = mes.getResources();
            if (ga.canPickGoldHexResources(pn, rsrcs))
            {
                final boolean fromInitPlace = ga.isInitialPlacement();

                ga.pickGoldHexResources(pn, rsrcs);

                /**
                 * tell everyone what the player gained
                 */
                reportRsrcGainGold(gn, player, pn, rsrcs);

                /**
                 * send the new state, or end turn if was marked earlier as forced
                 * -- for gold during initial placement, current player might also change.
                 */
                final int gstate = ga.getGameState();
                if ((gstate != SOCGame.PLAY1) || ! ga.isForcingEndTurn())
                {
                    if (! fromInitPlace)
                    {
                        sendGameState(ga);
                    } else {
                        // send state, and current player if changed

                        switch (gstate)
                        {
                        case SOCGame.START1B:
                        case SOCGame.START2B:
                        case SOCGame.START3B:
                            // pl not changed: previously placed settlement, now placing road or ship
                            sendGameState(ga);
                            break;

                        case SOCGame.START1A:
                        case SOCGame.START2A:
                        case SOCGame.START3A:
                            // Player probably changed, announce new player if so
                            sendGameState(ga, false);
                            if (! checkTurn(c, ga))
                                sendTurn(ga, true);
                            break;

                        case SOCGame.PLAY:
                            // The last initial road was placed
                            final boolean toldRoll = sendGameState(ga, false);
                            if (! checkTurn(c, ga))
                                // Announce new player (after START3A)
                                sendTurn(ga, true);
                            else if (toldRoll)
                                // When play starts, or after placing 2nd free road,
                                // announce even though player unchanged,
                                // to trigger auto-roll for the player.
                                messageToGame(gn, new SOCRollDicePrompt(gn, ga.getCurrentPlayerNumber()));
                            break;
                        }
                    }
                } else {
                    // force-end game turn
                    endGameTurn(ga, player);  // already did ga.takeMonitor()
                }
            }
            else
            {
                messageToPlayer(c, gn, "You can't pick that many resources.");
                messageToPlayer(c, new SOCPickResourcesRequest(gn, player.getNeedToPickGoldHexResources()));
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "end turn" message.
     * This normally ends a player's normal turn (phase {@link SOCGame#PLAY1}).
     * On the 6-player board, it ends their placements during the
     * {@link SOCGame#SPECIAL_BUILDING Special Building Phase}.
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleENDTURN(StringConnection c, SOCEndTurn mes)
    {
        if (c == null)
            return;
        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        final String gname = ga.getName();

        if (ga.isDebugFreePlacement())
        {
            // turn that off before ending current turn
            processDebugCommand_freePlace(c, gname, "0");
        }

        ga.takeMonitor();

        try
        {
            final String plName = (String) c.getData();
            if (ga.getGameState() == SOCGame.OVER)
            {
                // Should not happen; is here just in case.
                SOCPlayer pl = ga.getPlayer(plName);
                if (pl != null)
                {
                    String msg = ga.gameOverMessageToPlayer(pl);
                        // msg = "The game is over; you are the winner!";
                        // msg = "The game is over; <someone> won.";
                        // msg = "The game is over; no one won.";
                    messageToPlayer(c, gname, msg);
                }
            }
            else if (checkTurn(c, ga))
            {
                SOCPlayer pl = ga.getPlayer(plName);
                if ((pl != null) && ga.canEndTurn(pl.getPlayerNumber()))
                {
                    endGameTurn(ga, pl);
                }
                else
                {
                    messageToPlayer(c, gname, "You can't end your turn yet.");
                }
            }
            else
            {
                messageToPlayer(c, gname, "It's not your turn.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught at handleENDTURN");
        }

        ga.releaseMonitor();
    }

    /**
     * Pre-checking already done, end the current player's turn in this game.
     * Alter game state and send messages to players.
     * Calls {@link SOCGame#endTurn()}, which may also end the game.
     * On the 6-player board, this may begin the {@link SOCGame#SPECIAL_BUILDING Special Building Phase},
     * or end a player's placements during that phase.
     * Otherwise, calls {@link #sendTurn(SOCGame, boolean)} and begins
     * the next player's turn.
     *<P>
     * Assumes:
     * <UL>
     * <LI> ga.canEndTurn already called, to validate player
     * <LI> ga.takeMonitor already called (not the same as {@link SOCGameList#takeMonitorForGame(String)})
     * <LI> gamelist.takeMonitorForGame is NOT called, we do NOT have that monitor
     * </UL>
     *<P>
     * As a special case, endTurn is used to begin the Special Building Phase during the
     * start of a player's own turn, if permitted.  (Added in 1.1.09)
     *
     * @param ga Game to end turn
     * @param pl Current player in <tt>ga</tt>, or null. Not needed except in SPECIAL_BUILDING.
     *           If null, will be determined within this method.
     */
    private void endGameTurn(SOCGame ga, SOCPlayer pl)
    {
        final String gname = ga.getName();

        if (ga.getGameState() == SOCGame.SPECIAL_BUILDING)
        {
            final int cpn = ga.getCurrentPlayerNumber();
            if (pl == null)
                pl = ga.getPlayer(cpn);
            pl.setAskedSpecialBuild(false);
            messageToGame(gname, new SOCPlayerElement(gname, cpn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 0));
        }

        boolean hadBoardResetRequest = (-1 != ga.getResetVoteRequester());

        /**
         * End the Turn:
         */

        ga.endTurn();  // May set state to OVER, if new player has enough points to win.
                       // May begin or continue the Special Building Phase.

        /**
         * Send the results out:
         */

        if (hadBoardResetRequest)
        {
            // Cancel voting at end of turn
            messageToGame(gname, new SOCResetBoardReject(gname));
        }

        /**
         * send new state number; if game is now OVER,
         * also send end-of-game messages.
         */
        boolean wantsRollPrompt = sendGameState(ga, false);

        /**
         * clear any trade offers
         */
        gameList.takeMonitorForGame(gname);
        if (ga.clientVersionLowest >= SOCClearOffer.VERSION_FOR_CLEAR_ALL)
        {
            messageToGameWithMon(gname, new SOCClearOffer(gname, -1));
        } else {
            for (int i = 0; i < ga.maxPlayers; i++)
                messageToGameWithMon(gname, new SOCClearOffer(gname, i));
        }
        gameList.releaseMonitorForGame(gname);

        /**
         * send whose turn it is
         */
        sendTurn(ga, wantsRollPrompt);
        if (ga.getGameState() == SOCGame.SPECIAL_BUILDING)
            messageToGame(gname,
                "Special building phase: "
                  + ga.getPlayer(ga.getCurrentPlayerNumber()).getName()
                  + "'s turn to place.");
    }

    /**
     * Try to force-end the current player's turn in this game.
     * Alter game state and send messages to players.
     * Will call {@link #endGameTurn(SOCGame, SOCPlayer)} if appropriate.
     * Will send gameState and current player (turn) to clients.
     *<P>
     * If the current player has lost connection, send the {@link SOCLeaveGame LEAVEGAME}
     * message out <b>before</b> calling this method.
     *<P>
     * Assumes, as {@link #endGameTurn(SOCGame, SOCPlayer)} does:
     * <UL>
     * <LI> ga.canEndTurn already called, returned false
     * <LI> ga.takeMonitor already called (not the same as {@link SOCGameList#takeMonitorForGame(String)})
     * <LI> gamelist.takeMonitorForGame is NOT called, we do NOT have that monitor
     * </UL>
     * @param ga Game to force end turn
     * @param plName Current player's name. Needed because if they have been disconnected by
     *               {@link #leaveGame(StringConnection, String, boolean)},
     *               their name within game object is already null.
     * @return true if the turn was ended and game is still active;
     *          false if we find that all players have left and
     *          the gamestate has been changed here to {@link #OVER}.
     *
     * @see #endPlayerTurnOrForce(SOCGame, int, String)
     * @see SOCGame#forceEndTurn()
     */
    private boolean forceEndGameTurn(SOCGame ga, final String plName)
    {
        final String gaName = ga.getName();
        final int cpn = ga.getCurrentPlayerNumber();
        final int endFromGameState = ga.getGameState();

        SOCPlayer cp = ga.getPlayer(cpn);
        if (cp.hasAskedSpecialBuild())
        {
            cp.setAskedSpecialBuild(false);
            messageToGame(gaName, new SOCPlayerElement(gaName, cpn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 0));
        }

        final SOCForceEndTurnResult res = ga.forceEndTurn();
            // State now hopefully PLAY1, or SPECIAL_BUILDING;
            // also could be initial placement (START1A or START2A or START3A).
        if (SOCGame.OVER == ga.getGameState())
            return false;  // <--- Early return: All players have left ---

        /**
         * report any resources lost, gained
         */
        SOCResourceSet resGainLoss = res.getResourcesGainedLost();
        if (resGainLoss != null)
        {
            /**
             * If gold hex or returning resources to player (not discarding), report actual types/amounts.
             * For discard, tell the discarding player's client that they discarded the resources,
             * tell everyone else that the player discarded unknown resources.
             */
            if (! res.isLoss())
            {
                if ((endFromGameState == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                    || (endFromGameState == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE))
                {
                    // Send SOCPlayerElement messages, "gains" text
                    reportRsrcGainGold(gaName, cp, cpn, resGainLoss);
                } else {
                    // Send SOCPlayerElement messages
                    reportRsrcGainLoss(gaName, resGainLoss, false, cpn, -1, null, null);
                }
            } else {
                StringConnection c = getConnection(plName);
                if ((c != null) && c.isConnected())
                    reportRsrcGainLoss(gaName, resGainLoss, true, cpn, -1, null, c);
                int totalRes = resGainLoss.getTotal();
                messageToGameExcept(gaName, c, new SOCPlayerElement(gaName, cpn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, totalRes), true);
                messageToGame(gaName, plName + " discarded " + totalRes + " resources.");
            }
        }

        /**
         * report any dev-card returned to player's hand
         */
        int card = res.getDevCardType();
        if (card != -1)
        {
            StringConnection c = getConnection(plName);
            if ((c != null) && c.isConnected())
            {
                if ((card == SOCDevCardConstants.KNIGHT) && (c.getVersion() < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
                    card = SOCDevCardConstants.KNIGHT_FOR_VERS_1_X;
                messageToPlayer(c, new SOCDevCard(gaName, cpn, SOCDevCard.ADDOLD, card));
            }
            if (ga.clientVersionLowest >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES)
            {
                messageToGameExcept
                    (gaName, c, new SOCDevCard(gaName, cpn, SOCDevCard.ADDOLD, SOCDevCardConstants.UNKNOWN), true);
            } else {
                messageToGameForVersionsExcept
                    (ga, -1, SOCDevCardConstants.VERSION_FOR_NEW_TYPES - 1,
                     c, new SOCDevCard(gaName, cpn, SOCDevCard.ADDOLD, SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X), true);
                messageToGameForVersionsExcept
                    (ga, SOCDevCardConstants.VERSION_FOR_NEW_TYPES, Integer.MAX_VALUE,
                     c, new SOCDevCard(gaName, cpn, SOCDevCard.ADDOLD, SOCDevCardConstants.UNKNOWN), true);
            }
            messageToGame(gaName, plName + "'s just-played development card was returned.");
        }

        /**
         * For initial placements, we don't end turns as normal.
         * (Player number may go forward or backwards, new state isn't PLAY, etc.)
         * Update clients' gamestate, but don't call endGameTurn.
         */
        final int forceRes = res.getResult();
        if ((forceRes == SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADV)
            || (forceRes == SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADVBACK))
        {
            if (res.didUpdateFP() || res.didUpdateLP())
            {
                // will cause clients to recalculate lastPlayer too
                messageToGame(gaName, new SOCFirstPlayer(gaName, ga.getFirstPlayer()));
            }
            sendGameState(ga, false);
            sendTurn(ga, false);
            return true;  // <--- Early return ---
        }

        /**
         * If the turn can now end, proceed as if player requested it.
         * Otherwise, send current gamestate.  We'll all wait for other
         * players to send discard messages, and afterwards this turn can end.
         */
        if (ga.canEndTurn(cpn))
            endGameTurn(ga, null);  // could force gamestate to OVER, if a client leaves
        else
            sendGameState(ga, false);

        return (ga.getGameState() != SOCGame.OVER);
    }

    /**
     * handle "choose player" message during robbery
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleCHOOSEPLAYER(StringConnection c, SOCChoosePlayer mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            if (checkTurn(c, ga))
            {
                final int choice = mes.getChoice();
                switch (ga.getGameState())
                {
                case SOCGame.WAITING_FOR_ROBBER_OR_PIRATE:
                    ga.chooseMovePirate(choice == -2);
                    sendGameState(ga);
                    break;

                case SOCGame.WAITING_FOR_CHOICE:
                    if (ga.canChoosePlayer(choice))
                    {
                        final int rsrc = ga.choosePlayerForRobbery(choice);
                        final boolean waitingClothOrRsrc = (ga.getGameState() == SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE);
                        if (! waitingClothOrRsrc)
                        {
                            reportRobbery
                                (ga, ga.getPlayer((String) c.getData()), ga.getPlayer(choice), rsrc);
                        } else {
                            messageToGame(ga.getName(),
                                ((String) c.getData())
                                + " moved the pirate, must choose to steal cloth or steal resources from "
                                + ga.getPlayer(choice).getName() + ".");
                        }
                        sendGameState(ga);
                        if (waitingClothOrRsrc)
                            messageToPlayer(c, new SOCChoosePlayer(ga.getName(), choice));
                    } else {
                        messageToPlayer(c, ga.getName(), "You can't steal from that player.");
                    }
                    break;

                case SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE:
                    {
                        final boolean stealCloth;
                        final int pn;
                        if (choice < 0)
                        {
                            stealCloth = true;
                            pn = (-choice) - 1;
                        } else {
                            stealCloth = false;
                            pn = choice;
                        }
                        if (ga.canChoosePlayer(pn) && ga.canChooseRobClothOrResource(pn))
                        {
                            final int rsrc = ga.stealFromPlayer(pn, stealCloth);
                            reportRobbery
                                (ga, ga.getPlayer((String) c.getData()), ga.getPlayer(pn), rsrc);
                            sendGameState(ga);
                            break;
                        }
                        // else, fall through and send "can't steal" message
                    }
                    
                default:
                    messageToPlayer(c, ga.getName(), "You can't steal from that player.");
                }
            }
            else
            {
                messageToPlayer(c, ga.getName(), "It's not your turn.");
            }
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "make offer" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleMAKEOFFER(StringConnection c, SOCMakeOffer mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        final String gaName = ga.getName();
        if (ga.isGameOptionSet("NT"))
        {
            messageToPlayer(c, gaName, "Trading is not allowed in this game.");
            return;  // <---- Early return: No Trading ----
        }

        ga.takeMonitor();

        try
        {
            SOCTradeOffer offer = mes.getOffer();

            /**
             * remake the offer with data that we know is accurate,
             * namely the 'from' datum
             */
            SOCPlayer player = ga.getPlayer((String) c.getData());

            /**
             * announce the offer, including text message similar to bank/port trade.
             */
            if (player != null)
            {
                SOCTradeOffer remadeOffer;
                {
                    SOCResourceSet offGive = offer.getGiveSet(),
                                   offGet  = offer.getGetSet();
                    remadeOffer = new SOCTradeOffer(gaName, player.getPlayerNumber(), offer.getTo(), offGive, offGet);
                    player.setCurrentOffer(remadeOffer);
                    StringBuffer offMsgText = new StringBuffer((String) c.getData());
                    offMsgText.append(" made an offer to trade ");
                    offGive.toFriendlyString(offMsgText);
                    offMsgText.append(" for ");
                    offGet.toFriendlyString(offMsgText);
                    offMsgText.append('.');
                    messageToGame(gaName, offMsgText.toString() );
                }

                SOCMakeOffer makeOfferMessage = new SOCMakeOffer(gaName, remadeOffer);
                messageToGame(gaName, makeOfferMessage);

                recordGameEvent(gaName, makeOfferMessage.toCmd());

                /**
                 * clear all the trade messages because a new offer has been made
                 */
                gameList.takeMonitorForGame(gaName);
                if (ga.clientVersionLowest >= SOCClearTradeMsg.VERSION_FOR_CLEAR_ALL)
                {
                    messageToGameWithMon(gaName, new SOCClearTradeMsg(gaName, -1));
                } else {
                    for (int i = 0; i < ga.maxPlayers; i++)
                        messageToGameWithMon(gaName, new SOCClearTradeMsg(gaName, i));
                }
                gameList.releaseMonitorForGame(gaName);
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "clear offer" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleCLEAROFFER(StringConnection c, SOCClearOffer mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            ga.getPlayer((String) c.getData()).setCurrentOffer(null);
            messageToGame(gaName, new SOCClearOffer(gaName, ga.getPlayer((String) c.getData()).getPlayerNumber()));
            recordGameEvent(mes.getGame(), mes.toCmd());

            /**
             * clear all the trade messages
             */
            gameList.takeMonitorForGame(gaName);
            if (ga.clientVersionLowest >= SOCClearTradeMsg.VERSION_FOR_CLEAR_ALL)
            {
                messageToGameWithMon(gaName, new SOCClearTradeMsg(gaName, -1));
            } else {
                for (int i = 0; i < ga.maxPlayers; i++)
                    messageToGameWithMon(gaName, new SOCClearTradeMsg(gaName, i));
            }
            gameList.releaseMonitorForGame(gaName);
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "reject offer" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleREJECTOFFER(StringConnection c, SOCRejectOffer mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        SOCPlayer player = ga.getPlayer((String) c.getData());
        if (player == null)
            return;

        final String gaName = ga.getName();
        SOCRejectOffer rejectMessage = new SOCRejectOffer(gaName, player.getPlayerNumber());
        messageToGame(gaName, rejectMessage);

        recordGameEvent(gaName, rejectMessage.toCmd());
    }

    /**
     * handle "accept offer" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleACCEPTOFFER(StringConnection c, SOCAcceptOffer mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            SOCPlayer player = ga.getPlayer((String) c.getData());

            if (player != null)
            {
                final int acceptingNumber = player.getPlayerNumber();
                final int offeringNumber = mes.getOfferingNumber();
                final String gaName = ga.getName();

                if (ga.canMakeTrade(offeringNumber, acceptingNumber))
                {
                    ga.makeTrade(offeringNumber, acceptingNumber);
                    reportTrade(ga, offeringNumber, acceptingNumber);

                    recordGameEvent(mes.getGame(), mes.toCmd());

                    /**
                     * clear all offers
                     */
                    for (int i = 0; i < ga.maxPlayers; i++)
                    {
                        ga.getPlayer(i).setCurrentOffer(null);
                    }
                    gameList.takeMonitorForGame(gaName);
                    if (ga.clientVersionLowest >= SOCClearOffer.VERSION_FOR_CLEAR_ALL)
                    {
                        messageToGameWithMon(gaName, new SOCClearOffer(gaName, -1));
                    } else {
                        for (int i = 0; i < ga.maxPlayers; i++)
                            messageToGameWithMon(gaName, new SOCClearOffer(gaName, i));
                    }
                    gameList.releaseMonitorForGame(gaName);

                    /**
                     * send a message to the bots that the offer was accepted
                     */
                    messageToGame(gaName, mes);
                }
                else
                {
                    messageToPlayer(c, gaName, "You can't make that trade.");
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "bank trade" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleBANKTRADE(StringConnection c, SOCBankTrade mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        final SOCResourceSet give = mes.getGiveSet(),
            get = mes.getGetSet();

        ga.takeMonitor();

        try
        {
            if (checkTurn(c, ga))
            {
                if (ga.canMakeBankTrade(give, get))
                {
                    ga.makeBankTrade(give, get);
                    reportBankTrade(ga, give, get);
                }
                else
                {
                    messageToPlayer(c, ga.getName(), "You can't make that trade.");
                    SOCClientData scd = (SOCClientData) c.getAppData();
                    if ((scd != null) && scd.isRobot)
                        D.ebugPrintln("ILLEGAL BANK TRADE: " + c.getData()
                          + ": give " + give + ", get " + get);
                }
            }
            else
            {
                messageToPlayer(c, ga.getName(), "It's not your turn.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "build request" message.
     * If client is current player, they want to buy a {@link SOCPlayingPiece}.
     * Otherwise, if 6-player board, they want to build during the
     * {@link SOCGame#SPECIAL_BUILDING Special Building Phase}.
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleBUILDREQUEST(StringConnection c, SOCBuildRequest mes)
    {
        if (c == null)
            return;
        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        final String gaName = ga.getName();
        ga.takeMonitor();

        try
        {
            final boolean isCurrent = checkTurn(c, ga);
            SOCPlayer player = ga.getPlayer((String) c.getData());
            final int pn = player.getPlayerNumber();
            final int pieceType = mes.getPieceType();
            boolean sendDenyReply = false;  // for robots' benefit

            if (isCurrent)
            {
                if ((ga.getGameState() == SOCGame.PLAY1) || (ga.getGameState() == SOCGame.SPECIAL_BUILDING))
                {
                    switch (pieceType)
                    {
                    case SOCPlayingPiece.ROAD:

                        if (ga.couldBuildRoad(pn))
                        {
                            ga.buyRoad(pn);
                            messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.CLAY, 1));
                            messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                            sendGameState(ga);
                        }
                        else
                        {
                            messageToPlayer(c, gaName, "You can't build a road.");
                            sendDenyReply = true;
                        }

                        break;

                    case SOCPlayingPiece.SETTLEMENT:

                        if (ga.couldBuildSettlement(pn))
                        {
                            ga.buySettlement(pn);
                            gameList.takeMonitorForGame(gaName);
                            messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.CLAY, 1));
                            messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1));
                            messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 1));
                            messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                            gameList.releaseMonitorForGame(gaName);
                            sendGameState(ga);
                        }
                        else
                        {
                            messageToPlayer(c, gaName, "You can't build a settlement.");
                            sendDenyReply = true;
                        }

                        break;

                    case SOCPlayingPiece.CITY:

                        if (ga.couldBuildCity(pn))
                        {
                            ga.buyCity(pn);
                            messageToGame(ga.getName(), new SOCPlayerElement(ga.getName(), pn, SOCPlayerElement.LOSE, SOCPlayerElement.ORE, 3));
                            messageToGame(ga.getName(), new SOCPlayerElement(ga.getName(), pn, SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 2));
                            sendGameState(ga);
                        }
                        else
                        {
                            messageToPlayer(c, gaName, "You can't build a city.");
                            sendDenyReply = true;
                        }

                        break;

                    case SOCPlayingPiece.SHIP:

                        if (ga.couldBuildShip(pn))
                        {
                            ga.buyShip(pn);
                            messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1));
                            messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                            sendGameState(ga);
                        }
                        else
                        {
                            messageToPlayer(c, gaName, "You can't build a ship.");
                            sendDenyReply = true;
                        }

                        break;
                    }
                }
                else if (pieceType == -1)
                {
                    // 6-player board: Special Building Phase
                    // during start of own turn
                    try
                    {
                        ga.askSpecialBuild(pn, true);
                        messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 1));
                        endGameTurn(ga, player);  // triggers start of SBP
                    } catch (IllegalStateException e) {
                        messageToPlayer(c, gaName, "You can't ask to build now.");
                        sendDenyReply = true;
                    }
                }
                else
                {
                    messageToPlayer(c, gaName, "You can't build now.");
                    sendDenyReply = true;
                }
            }
            else
            {
                if (ga.maxPlayers <= 4)
                {
                    messageToPlayer(c, gaName, "It's not your turn.");
                    sendDenyReply = true;
                } else {
                    // 6-player board: Special Building Phase
                    // during other player's turn
                    try
                    {
                        ga.askSpecialBuild(pn, true);  // will validate that they can build now
                        messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 1));
                    } catch (IllegalStateException e) {
                        messageToPlayer(c, gaName, "You can't ask to build now.");
                        sendDenyReply = true;
                    }
                }
            }

            if (sendDenyReply && ga.getPlayer(pn).isRobot())
            {
                messageToPlayer(c, new SOCCancelBuildRequest(gaName, pieceType));
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught at handleBUILDREQUEST");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "cancel build request" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleCANCELBUILDREQUEST(StringConnection c, SOCCancelBuildRequest mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            if (checkTurn(c, ga))
            {
                final SOCPlayer player = ga.getPlayer((String) c.getData());
                final int pn = player.getPlayerNumber();

                switch (mes.getPieceType())
                {
                case SOCPlayingPiece.ROAD:

                    if (ga.getGameState() == SOCGame.PLACING_ROAD)
                    {
                        ga.cancelBuildRoad(pn);
                        messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.CLAY, 1));
                        messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                        sendGameState(ga);
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You didn't buy a road.");
                    }

                    break;

                case SOCPlayingPiece.SETTLEMENT:

                    if (ga.getGameState() == SOCGame.PLACING_SETTLEMENT)
                    {
                        ga.cancelBuildSettlement(pn);
                        gameList.takeMonitorForGame(gaName);
                        messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.CLAY, 1));
                        messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.SHEEP, 1));
                        messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WHEAT, 1));
                        messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                        gameList.releaseMonitorForGame(gaName);
                        sendGameState(ga);
                    }
                    else if ((ga.getGameState() == SOCGame.START1B) || (ga.getGameState() == SOCGame.START2B) || (ga.getGameState() == SOCGame.START3B))
                    {
                        SOCSettlement pp = new SOCSettlement(player, player.getLastSettlementCoord(), null);
                        ga.undoPutInitSettlement(pp);
                        messageToGame(gaName, mes);  // Re-send to all clients to announce it
                            // (Safe since we've validated all message parameters)
                        messageToGame(gaName, player.getName() + " cancelled this settlement placement.");
                        sendGameState(ga);  // This send is redundant, if client reaction changes game state
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You didn't buy a settlement.");
                    }

                    break;

                case SOCPlayingPiece.CITY:

                    if (ga.getGameState() == SOCGame.PLACING_CITY)
                    {
                        ga.cancelBuildCity(pn);
                        messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.ORE, 3));
                        messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WHEAT, 2));
                        sendGameState(ga);
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You didn't buy a city.");
                    }

                    break;

                case SOCPlayingPiece.SHIP:

                    if (ga.getGameState() == SOCGame.PLACING_SHIP)
                    {
                        ga.cancelBuildShip(pn);
                        messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.SHEEP, 1));
                        messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                        sendGameState(ga);
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You didn't buy a ship.");
                    }

                    break;

                default:
                    throw new IllegalArgumentException("Unknown piece type " + mes.getPieceType());
                }
            }
            else
            {
                messageToPlayer(c, gaName, "It's not your turn.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "buy card request" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleBUYCARDREQUEST(StringConnection c, SOCBuyCardRequest mes)
    {
        if (c == null)
            return;
        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            SOCPlayer player = ga.getPlayer((String) c.getData());
            final int pn = player.getPlayerNumber();
            boolean sendDenyReply = false;  // for robots' benefit

            if (checkTurn(c, ga))
            {
                if (((ga.getGameState() == SOCGame.PLAY1) || (ga.getGameState() == SOCGame.SPECIAL_BUILDING))
                    && (ga.couldBuyDevCard(pn)))
                {
                    int card = ga.buyDevCard();
                    gameList.takeMonitorForGame(gaName);
                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.ORE, 1));
                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1));
                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 1));
                    messageToGameWithMon(gaName, new SOCDevCardCount(gaName, ga.getNumDevCards()));
                    gameList.releaseMonitorForGame(gaName);
                    if ((card == SOCDevCardConstants.KNIGHT) && (c.getVersion() < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
                        card = SOCDevCardConstants.KNIGHT_FOR_VERS_1_X;
                    messageToPlayer(c, new SOCDevCard(gaName, pn, SOCDevCard.DRAW, card));

                    if (ga.clientVersionLowest >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES)
                    {
                        messageToGameExcept(gaName, c, new SOCDevCard(gaName, pn, SOCDevCard.DRAW, SOCDevCardConstants.UNKNOWN), true);
                    } else {
                        messageToGameForVersionsExcept
                            (ga, -1, SOCDevCardConstants.VERSION_FOR_NEW_TYPES - 1,
                             c, new SOCDevCard(gaName, pn, SOCDevCard.DRAW, SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X), true);
                        messageToGameForVersionsExcept
                            (ga, SOCDevCardConstants.VERSION_FOR_NEW_TYPES, Integer.MAX_VALUE,
                             c, new SOCDevCard(gaName, pn, SOCDevCard.DRAW, SOCDevCardConstants.UNKNOWN), true);
                    }
                    messageToGame(gaName, (String) c.getData() + " bought a development card.");

                    if (ga.getNumDevCards() > 1)
                    {
                        messageToGame(gaName, "There are " + ga.getNumDevCards() + " cards left.");
                    }
                    else if (ga.getNumDevCards() == 1)
                    {
                        messageToGame(gaName, "There is 1 card left.");
                    }
                    else
                    {
                        messageToGame(gaName, "There are no more Development cards.");
                    }

                    sendGameState(ga);
                }
                else
                {
                    if (ga.getNumDevCards() == 0)
                    {
                        messageToPlayer(c, gaName, "There are no more Development cards.");
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You can't buy a development card now.");
                    }
                    sendDenyReply = true;
                }
            }
            else
            {
                if (ga.maxPlayers <= 4)
                {
                    messageToPlayer(c, gaName, "It's not your turn.");
                } else {
                    // 6-player board: Special Building Phase
                    try
                    {
                        ga.askSpecialBuild(pn, true);
                        messageToGame(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.ASK_SPECIAL_BUILD, 1));
                    } catch (IllegalStateException e) {
                        messageToPlayer(c, gaName, "You can't ask to buy a card now.");
                    }
                }
                sendDenyReply = true;
            }

            if (sendDenyReply && ga.getPlayer(pn).isRobot())
            {
                messageToPlayer(c, new SOCCancelBuildRequest(gaName, -2));  // == SOCPossiblePiece.CARD
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "play development card request" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handlePLAYDEVCARDREQUEST(StringConnection c, SOCPlayDevCardRequest mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            if (checkTurn(c, ga))
            {
                final SOCPlayer player = ga.getPlayer((String) c.getData());
                final int pn = player.getPlayerNumber();

                int ctype = mes.getDevCard();
                if ((ctype == SOCDevCardConstants.KNIGHT_FOR_VERS_1_X)
                    && (c.getVersion() < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
                    ctype = SOCDevCardConstants.KNIGHT;

                switch (ctype)
                {
                case SOCDevCardConstants.KNIGHT:

                    if (ga.canPlayKnight(pn))
                    {
                        ga.playKnight();
                        gameList.takeMonitorForGame(gaName);
                        messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, player.getName() + " played a Soldier card."));
                        if (ga.clientVersionLowest >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES)
                        {
                            messageToGameWithMon(gaName, new SOCDevCard(gaName, pn, SOCDevCard.PLAY, SOCDevCardConstants.KNIGHT));
                        } else {
                            messageToGameForVersions
                                (ga, -1, SOCDevCardConstants.VERSION_FOR_NEW_TYPES - 1,
                                 new SOCDevCard(gaName, pn, SOCDevCard.PLAY, SOCDevCardConstants.KNIGHT_FOR_VERS_1_X), false);
                            messageToGameForVersions
                                (ga, SOCDevCardConstants.VERSION_FOR_NEW_TYPES, Integer.MAX_VALUE,
                                 new SOCDevCard(gaName, pn, SOCDevCard.PLAY, SOCDevCardConstants.KNIGHT), false);
                        }
                        messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, pn, true));
                        messageToGameWithMon(gaName, new SOCPlayerElement(gaName, pn, SOCPlayerElement.GAIN, SOCPlayerElement.NUMKNIGHTS, 1));
                        gameList.releaseMonitorForGame(gaName);
                        broadcastGameStats(ga);
                        sendGameState(ga);
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You can't play a Soldier card now.");
                    }

                    break;

                case SOCDevCardConstants.ROADS:

                    if (ga.canPlayRoadBuilding(pn))
                    {
                        ga.playRoadBuilding();
                        gameList.takeMonitorForGame(gaName);
                        messageToGameWithMon(gaName, new SOCDevCard(gaName, pn, SOCDevCard.PLAY, SOCDevCardConstants.ROADS));
                        messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, pn, true));
                        messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, player.getName() + " played a Road Building card."));
                        gameList.releaseMonitorForGame(gaName);
                        sendGameState(ga);
                        if (ga.getGameState() == SOCGame.PLACING_FREE_ROAD1)
                        {
                            if (ga.hasSeaBoard)
                                messageToPlayer(c, gaName, "You may place 2 roads/ships.");
                            else
                                messageToPlayer(c, gaName, "You may place 2 roads.");
                        }
                        else
                        {
                            if (ga.hasSeaBoard)
                                messageToPlayer(c, gaName, "You may place your 1 remaining road or ship.");
                            else
                                messageToPlayer(c, gaName, "You may place your 1 remaining road.");
                        }
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You can't play a Road Building card now.");
                    }

                    break;

                case SOCDevCardConstants.DISC:

                    if (ga.canPlayDiscovery(pn))
                    {
                        ga.playDiscovery();
                        gameList.takeMonitorForGame(gaName);
                        messageToGameWithMon(gaName, new SOCDevCard(gaName, pn, SOCDevCard.PLAY, SOCDevCardConstants.DISC));
                        messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, pn, true));
                        messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, player.getName() + " played a Year of Plenty card."));
                        gameList.releaseMonitorForGame(gaName);
                        sendGameState(ga);
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You can't play a Year of Plenty card now.");
                    }

                    break;

                case SOCDevCardConstants.MONO:

                    if (ga.canPlayMonopoly(pn))
                    {
                        ga.playMonopoly();
                        gameList.takeMonitorForGame(gaName);
                        messageToGameWithMon(gaName, new SOCDevCard(gaName, pn, SOCDevCard.PLAY, SOCDevCardConstants.MONO));
                        messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, pn, true));
                        messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, player.getName() + " played a Monopoly card."));
                        gameList.releaseMonitorForGame(gaName);
                        sendGameState(ga);
                    }
                    else
                    {
                        messageToPlayer(c, gaName, "You can't play a Monopoly card now.");
                    }

                    break;

                // VP cards are secretly played when bought.
                // (case SOCDevCardConstants.CAP, LIB, UNIV, TEMP, TOW):
                // If player clicks "Play Card" the message is handled at the
                // client, in SOCHandPanel.actionPerformed case CARD.
                //  "You secretly played this VP card when you bought it."
                //  break;

                default:
                    D.ebugPrintln("* SOCServer.handlePLAYDEVCARDREQUEST: asked to play unhandled type " + mes.getDevCard());

                }
            }
            else
            {
                messageToPlayer(c, gaName, "It's not your turn.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "discovery pick" message (while playing Discovery card)
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleDISCOVERYPICK(StringConnection c, SOCDiscoveryPick mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            if (checkTurn(c, ga))
            {
                SOCPlayer player = ga.getPlayer((String) c.getData());

                if (ga.canDoDiscoveryAction(mes.getResources()))
                {
                    ga.doDiscoveryAction(mes.getResources());

                    StringBuffer message = new StringBuffer((String) c.getData());
                    message.append(" received ");
                    reportRsrcGainLoss(gaName, mes.getResources(), false, player.getPlayerNumber(), -1, message, null);
                    message.append(" from the bank.");
                    messageToGame(gaName, message.toString());
                    sendGameState(ga);
                }
                else
                {
                    messageToPlayer(c, gaName, "That is not a legal Year of Plenty pick.");
                }
            }
            else
            {
                messageToPlayer(c, gaName, "It's not your turn.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "monopoly pick" message
     *
     * @param c     the connection that sent the message
     * @param mes   the messsage
     */
    private void handleMONOPOLYPICK(StringConnection c, SOCMonopolyPick mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;


        ga.takeMonitor();

        try
        {
            final String gaName = ga.getName();
            if (checkTurn(c, ga))
            {
                if (ga.canDoMonopolyAction())
                {
                    int[] monoPicks = ga.doMonopolyAction(mes.getResource());

                    final String monoPlayerName = (String) c.getData();
                    final String resName
                        = " " + SOCResourceConstants.resName(mes.getResource()) + ".";
                    String message = monoPlayerName + " monopolized" + resName;

                    gameList.takeMonitorForGame(gaName);
                    messageToGameExcept(gaName, c, new SOCGameTextMsg(gaName, SERVERNAME, message), false);

                    /**
                     * just send all the player's resource counts for the
                     * monopolized resource
                     */
                    for (int i = 0; i < ga.maxPlayers; i++)
                    {
                        /**
                         * Note: This only works if SOCPlayerElement.CLAY == SOCResourceConstants.CLAY
                         */
                        messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, mes.getResource(), ga.getPlayer(i).getResources().getAmount(mes.getResource())));
                    }
                    gameList.releaseMonitorForGame(gaName);

                    /**
                     * now that monitor is released, notify the
                     * victim(s) of resource amounts taken,
                     * and tell the player how many they won.
                     */
                    int monoTotal = 0;
                    for (int i = 0; i < ga.maxPlayers; i++)
                    {
                        int picked = monoPicks[i];
                        if (picked == 0)
                            continue;
                        monoTotal += picked;
                        String viName = ga.getPlayer(i).getName();
                        StringConnection viCon = getConnection(viName);
                        if (viCon != null)
                            messageToPlayer(viCon, gaName,
                                monoPlayerName + "'s Monopoly took your " + picked + resName);
                    }

                    messageToPlayer(c, gaName, "You monopolized " + monoTotal + resName);
                    sendGameState(ga);
                }
                else
                {
                    messageToPlayer(c, gaName, "You can't do a Monopoly pick now.");
                }
            }
            else
            {
                messageToPlayer(c, gaName, "It's not your turn.");
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception caught");
        }

        ga.releaseMonitor();
    }

    /**
     * handle "change face" message
     *
     * @param c  the connection
     * @param mes  the message
     */
    private void handleCHANGEFACE(StringConnection c, SOCChangeFace mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        SOCPlayer player = ga.getPlayer((String) c.getData());
        if (player == null)
            return;

        final String gaName = mes.getGame();
        player.setFaceId(mes.getFaceId());
        messageToGame(gaName, new SOCChangeFace(gaName, player.getPlayerNumber(), mes.getFaceId()));
    }

    /**
     * handle "set seat lock" message
     *
     * @param c  the connection
     * @param mes  the message
     */
    private void handleSETSEATLOCK(StringConnection c, SOCSetSeatLock mes)
    {
        if (c == null)
            return;

        SOCGame ga = gameList.getGameData(mes.getGame());
        if (ga == null)
            return;

        SOCPlayer player = ga.getPlayer((String) c.getData());
        if (player == null)
            return;

        if (mes.getLockState() == true)
        {
            ga.lockSeat(mes.getPlayerNumber());
        }
        else
        {
            ga.unlockSeat(mes.getPlayerNumber());
        }

        messageToGame(mes.getGame(), mes);
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
    private void handleRESETBOARDREQUEST(StringConnection c, SOCResetBoardRequest mes)
    {
        if (c == null)
            return;
        String gaName = mes.getGame();
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
        int numHuman = SOCGameBoardReset.sortPlayerConnections(ga, null, gameList.getMembers(gaName), humanConns, robotConns);

        final int reqPN = reqPlayer.getPlayerNumber();
        if (numHuman < 2)
        {
            // Are there robots? Go ahead and reset if so.
            boolean hadRobot = false;
            for (int i = robotConns.length-1; i>=0; --i)
            {
                if (robotConns[i] != null)
                {
                    hadRobot = true;
                    break;
                }
            }
            if (hadRobot)
            {
                resetBoardAndNotify(gaName, reqPN);
            } else {
                messageToGameUrgent(gaName, "Everyone has left this game. Please start a new game with players or bots.");
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
                    if ((pc != null) && pc.isConnected() && pc.getVersion() >= 1100)
                    {
                         ++votingPlayers;
                    }
                }
            }

            if (votingPlayers == 0)
            {
                // No one else is capable of voting.
                // Reset the game immediately.
                messageToGameWithMon(gaName, new SOCGameTextMsg
                    (gaName, SERVERNAME, ">>> " + (String) c.getData()
                     + " is resetting the game - other connected players are unable to vote (client too old)."));
                gameList.releaseMonitorForGame(gaName);
                resetBoardAndNotify(gaName, reqPN);
            }
            else
            {
                // Put it to a vote
                messageToGameWithMon(gaName, new SOCGameTextMsg
                    (gaName, SERVERNAME, (String) c.getData() + " requests a board reset - other players please vote."));
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
    private void handleRESETBOARDVOTE(StringConnection c, SOCResetBoardVote mes)
    {
        if (c == null)
            return;
        SOCGame ga = gameList.getGameData(mes.getGame());
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
    private void resetBoardVoteNotifyOne(SOCGame ga, final int pn, final String plName, final boolean vyes)
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
     * process the "game option get defaults" message.
     * Responds to client by sending {@link SOCGameOptionGetDefaults GAMEOPTIONGETDEFAULTS}.
     * All of server's known options are sent, except empty string-valued options.
     * Depending on client version, server's response may include option names that
     * the client is too old to use; the client is able to ignore them.
     * @param c  the connection
     * @param mes  the message
     * @since 1.1.07
     */
    private void handleGAMEOPTIONGETDEFAULTS(StringConnection c, SOCGameOptionGetDefaults mes)
    {
        if (c == null)
            return;
        c.put(SOCGameOptionGetDefaults.toCmd
              (SOCGameOption.packKnownOptionsToString(true)));
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
        boolean vecIsOptObjs = false;
        boolean alreadyTrimmedEnums = false;
        Vector<?> okeys = mes.getOptionKeys();

        if (okeys == null)
        {
            // received "-", look for newer options (cli is older than us).
            // okeys will be null if nothing is new.
            okeys = SOCGameOption.optionsNewerThanVersion(cliVers, false, true, null);
            vecIsOptObjs = true;
            alreadyTrimmedEnums = true;
        }

        if (okeys != null)
        {
            for (int i = 0; i < okeys.size(); ++i)
            {
                SOCGameOption opt;
                if (vecIsOptObjs)
                {
                    opt = (SOCGameOption) okeys.elementAt(i);
                    if (opt.minVersion > cliVers)
                        opt = new SOCGameOption(opt.optKey);  // OTYPE_UNKNOWN
                } else {
                    String okey = (String) okeys.elementAt(i);
                    opt = SOCGameOption.getOption(okey);
                    if ((opt == null) || (opt.minVersion > cliVers))  // Don't use opt.getMinVersion() here
                        opt = new SOCGameOption(okey);  // OTYPE_UNKNOWN
                }

                // Enum-type options may have their values restricted by version.
                if ( (! alreadyTrimmedEnums)
                    && (opt.enumVals != null)
                    && (opt.optType != SOCGameOption.OTYPE_UNKNOWN)
                    && (opt.lastModVersion > cliVers))
                {
                    opt = SOCGameOption.trimEnumForVersion(opt, cliVers);
                }

                c.put(new SOCGameOptionInfo(opt).toCmd());
            }
        }

        // mark end of list, even if list was empty
        c.put(SOCGameOptionInfo.OPTINFO_NO_MORE_OPTS.toCmd());  // GAMEOPTIONINFO("-")
    }

    /**
     * process the "new game with options request" message.
     * For messages sent, and other details,
     * see {@link #createOrJoinGameIfUserOK(StringConnection, String, String, String, Hashtable)}.
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
     * Handle the client's debug Free Placement putpiece request.
     * @since 1.1.12
     */
    private final void handleDEBUGFREEPLACE(StringConnection c, SOCDebugFreePlace mes)
    {
        SOCGame ga = gameList.getGameData(mes.getGame());
        if ((ga == null) || ! ga.isDebugFreePlacement())
            return;
        final String gaName = ga.getName();

        final int coord = mes.getCoordinates();
        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        if (player == null)
            return;

        boolean didPut = false;
        final int pieceType = mes.getPieceType();

        final boolean initialDeny
            = ga.isInitialPlacement() && ! player.canBuildInitialPieceType(pieceType);

        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:
            if (player.isPotentialRoad(coord) && ! initialDeny)
            {
                ga.putPiece(new SOCRoad(player, coord, null));
                didPut = true;
            }
            break;

        case SOCPlayingPiece.SETTLEMENT:
            if (player.canPlaceSettlement(coord) && ! initialDeny)
            {
                ga.putPiece(new SOCSettlement(player, coord, null));
                didPut = true;
            }
            break;

        case SOCPlayingPiece.CITY:
            if (player.isPotentialCity(coord) && ! initialDeny)
            {
                ga.putPiece(new SOCCity(player, coord, null));
                didPut = true;
            }
            break;

        case SOCPlayingPiece.SHIP:
            if (ga.canPlaceShip(player, coord) && ! initialDeny)
            {
                ga.putPiece(new SOCShip(player, coord, null));
                didPut = true;
            }
            break;

        default:
            messageToPlayer(c, gaName, "* Unknown piece type: " + pieceType);
        }

        if (didPut)
        {
            messageToGame(gaName, new SOCPutPiece
                          (gaName, mes.getPlayerNumber(), pieceType, coord));

            // Check for initial settlement next to gold hex
            if (pieceType == SOCPlayingPiece.SETTLEMENT)
            {
                final int numGoldRes = player.getNeedToPickGoldHexResources();
                if (numGoldRes > 0)
                    messageToPlayer(c, new SOCPickResourcesRequest(gaName, numGoldRes));
            }

            if (ga.getGameState() >= SOCGame.OVER)
            {
                // exit debug mode, announce end of game
                processDebugCommand_freePlace(c, gaName, "0");
                sendGameState(ga, false);
            }
        } else {
            if (initialDeny)
            {
                final String pieceTypeFirst =
                    ((player.getPieces().size() % 2) == 0)
                    ? "settlement"
                    : "road";
                messageToPlayer(c, gaName, "Place a " + pieceTypeFirst + " before placing that.");
            } else {
                messageToPlayer(c, gaName, "Not a valid location to place that.");
            }
        }
    }

    /**
     * Handle the client's "move piece request" message.
     * @since 2.0.00
     */
    private final void handleMOVEPIECEREQUEST(StringConnection c, SOCMovePieceRequest mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = gameList.getGameData(gaName);
        if (ga == null)
            return;

        boolean denyRequest = false;
        final int pn = mes.getPlayerNumber();
        final int fromEdge = mes.getFromCoord(),
                  toEdge   = mes.getToCoord();
        if ((mes.getPieceType() != SOCPlayingPiece.SHIP)
            || ! checkTurn(c, ga))
        {
            denyRequest = true;
        } else {
            SOCShip moveShip = ga.canMoveShip
                (pn, fromEdge, toEdge);
            if (moveShip == null)
            {
                denyRequest = true;
            } else {
                ga.moveShip(moveShip, toEdge);
                gameList.takeMonitorForGame(gaName);
                messageToGameWithMon(gaName, new SOCGameTextMsg
                    (gaName, SERVERNAME, ga.getPlayer(pn).getName() + " moved a ship."));
                messageToGameWithMon(gaName, new SOCMovePiece
                    (gaName, pn, SOCPlayingPiece.SHIP, fromEdge, toEdge));
                gameList.releaseMonitorForGame(gaName);
                if (ga.getGameState() >= SOCGame.OVER)
                {
                    // announce end of game
                    sendGameState(ga, false);
                }
            }
        }
        if (denyRequest)
        {
            D.ebugPrintln("ILLEGAL MOVEPIECE: 0x" + Integer.toHexString(fromEdge) + " -> 0x" + Integer.toHexString(toEdge)
                + ": player " + pn);
            messageToPlayer(c, gaName, "You can't move that ship right now.");
            messageToPlayer(c, new SOCCancelBuildRequest(gaName, SOCPlayingPiece.SHIP));
        }
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

        //
        // check to see if there is an account with
        // the requested nickname
        //
        String userPassword = null;

        try
        {
            userPassword = SOCDBHelper.getUserPassword(mes.getNickname());
        }
        catch (SQLException sqle)
        {
            // Indicates a db problem: don't continue
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_PROBLEM_WITH_DB, cliVers,
                     "Problem connecting to database, please try again later."));
            return;
        }

        if (userPassword != null)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                     "The nickname '" + mes.getNickname() + "' is already in use."));

            return;
        }

        //
        // create the account
        //
        Date currentTime = new Date();

        boolean success = false;

        try
        {
            success = SOCDBHelper.createAccount(mes.getNickname(), c.host(), mes.getPassword(), mes.getEmail(), currentTime.getTime());
        }
        catch (SQLException sqle)
        {
            System.err.println("Error creating account in db.");
        }

        if (success)
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_ACCT_CREATED_OK, cliVers,
                     "Account created for '" + mes.getNickname() + "'."));
        }
        else
        {
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_ACCT_NOT_CREATED_ERR, cliVers,
                     "Account not created due to error."));
        }
    }

    /**
     * Client has been approved to join game; send the entire state of the game to client.
     * Unless <tt>isTakingOver</tt>, send client join event to other players.
     * Assumes NEWGAME (or NEWGAMEWITHOPTIONS) has already been sent out.
     * First message sent to connecting client is JOINGAMEAUTH, unless isReset.
     *<P>
     * Among other messages, player names are sent via SITDOWN, and pieces on board
     * sent by PUTPIECE.  See comments here for further details.
     * If <tt>isTakingOver</tt>, some details are sent by calling
     * {@link #sitDown_sendPrivateInfo(SOCGame, StringConnection, int, String)}.
     * The group of messages sent here ends with GAMEMEMBERS, SETTURN and GAMESTATE.
     *<P>
     * @param gameData Game to join
     * @param c        The connection of joining client
     * @param isReset  Game is a board-reset of an existing game
     * @param isTakingOver  Client is re-joining; this connection replaces an earlier one which
     *                      is defunct because of a network problem.
     *                      If <tt>isTakingOver</tt>, don't send anything to other players.
     *
     * @see #connectToGame(StringConnection, String, Hashtable)
     * @see #createOrJoinGameIfUserOK(StringConnection, String, String, String, Hashtable)
     */
    private void joinGame(SOCGame gameData, StringConnection c, boolean isReset, boolean isTakingOver)
    {
        String gameName = gameData.getName();
        if (! isReset)
        {
            c.put(SOCJoinGameAuth.toCmd(gameName));
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_OK,
                     "Welcome to Java Settlers of Catan!"));
        }

        //c.put(SOCGameState.toCmd(gameName, gameData.getGameState()));
        for (int i = 0; i < gameData.maxPlayers; i++)
        {
            /**
             * send the already-seated player information;
             * if isReset, don't send, because sitDown will
             * be sent from resetBoardAndNotify.
             */
            if (! isReset)
            {
                SOCPlayer pl = gameData.getPlayer(i);
                String plName = pl.getName();
                if ((plName != null) && (!gameData.isSeatVacant(i)))
                {
                    c.put(SOCSitDown.toCmd(gameName, plName, i, pl.isRobot()));
                }
            }

            /**
             * send the seat lock information
             */
            messageToPlayer(c, new SOCSetSeatLock(gameName, i, gameData.isSeatLocked(i)));
        }

        c.put(getBoardLayoutMessage(gameData).toCmd());
        //    No need to catch IllegalArgumentException:
        //    Since game is already started, getBoardLayoutMessage has previously
        //    been called for the creating player, and the board encoding is OK.

        /**
         * if game hasn't started yet, each player's potentialSettlements are
         * identical, so send that info once for all players.
         */
        if ((gameData.getGameState() == SOCGame.NEW)
            && (c.getVersion() >= SOCPotentialSettlements.VERSION_FOR_PLAYERNUM_ALL))
        {
            final HashSet<Integer> psList = gameData.getPlayer(0).getPotentialSettlements();

            // Some boards may have multiple land areas.
            // See also below, and startGame which has very similar code.
            final HashSet<Integer>[] lan;
            final int pan;
            boolean addedPsList = false;
            if (gameData.hasSeaBoard)
            {
                final SOCBoardLarge bl = (SOCBoardLarge) gameData.getBoard();
                lan = bl.getLandAreasLegalNodes();
                pan = bl.getStartingLandArea();
                if ((lan != null) && ! lan[pan].equals(psList))
                {
                    // If potentials != legals[startingLandArea], send as legals[0]
                    lan[0] = psList;
                    addedPsList = true;
                }
            } else {
                lan = null;
                pan = 0;
            }

            if (lan == null)
                c.put(SOCPotentialSettlements.toCmd(gameName, -1, new Vector<Integer>(psList)));
            else
                c.put(SOCPotentialSettlements.toCmd(gameName, -1, pan, lan));

            if (addedPsList)
                lan[0] = null;  // Undo change to game's copy of landAreasLegalNodes

            if (gameData.isGameOptionSet(SOCGameOption.K_SC_CLVI))
                c.put(SOCPlayerElement.toCmd
                    (gameName, -1, SOCPlayerElement.SET,
                     SOCPlayerElement.SCENARIO_CLOTH_COUNT, ((SOCBoardLarge) (gameData.getBoard())).getCloth()));
        }

        /**
         * Send the current player number.
         * Before v2.0.00, this wasn't sent so early; was sent
         * just before SOCGameState and the "joined the game" text.
         * This earlier send has been tested against 1.1.07 (released 2009-10-31).
         */
        c.put(SOCSetTurn.toCmd(gameName, gameData.getCurrentPlayerNumber()));

        /**
         * send the per-player information
         */
        for (int i = 0; i < gameData.maxPlayers; i++)
        {
            SOCPlayer pl = gameData.getPlayer(i);

            // Send piece info even if player has left the game (pl.getName() == null).
            // This lets them see "their" pieces before sitDown(), if they rejoin at same position.

            Enumeration<SOCPlayingPiece> piecesEnum = pl.getPieces().elements();

            while (piecesEnum.hasMoreElements())
            {
                SOCPlayingPiece piece = piecesEnum.nextElement();

                if (piece.getType() == SOCPlayingPiece.CITY)
                {
                    c.put(SOCPutPiece.toCmd(gameName, i, SOCPlayingPiece.SETTLEMENT, piece.getCoordinates()));
                }

                c.put(SOCPutPiece.toCmd(gameName, i, piece.getType(), piece.getCoordinates()));
            }

            /**
             * send each player's unique potential settlement list,
             * if game has started
             */
            if ((gameData.getGameState() != SOCGame.NEW)
                || (c.getVersion() < SOCPotentialSettlements.VERSION_FOR_PLAYERNUM_ALL))
            {
                final HashSet<Integer> psList = pl.getPotentialSettlements();

                // Some boards may have multiple land areas.
                // Note: Assumes all players have same legal nodes.
                // See also above, and startGame which has very similar code.
                final HashSet<Integer>[] lan;
                final int pan;
                if (gameData.hasSeaBoard && (i == 0))
                {
                    // send this info once, not per-player
                    final SOCBoardLarge bl = (SOCBoardLarge) gameData.getBoard();
                    lan = bl.getLandAreasLegalNodes();
                    pan = bl.getStartingLandArea();
                    if (lan != null)
                        lan[0] = psList;
                } else {
                    lan = null;
                    pan = 0;
                }

                if (lan == null)
                {
                    c.put(SOCPotentialSettlements.toCmd(gameName, i, new Vector<Integer>(psList)));
                } else {
                    c.put(SOCPotentialSettlements.toCmd(gameName, i, pan, lan));
                    lan[0] = null;  // Undo change to game's copy of landAreasLegalNodes
                }
            }

            /**
             * send coords of the last settlement
             */
            c.put(SOCLastSettlement.toCmd(gameName, i, pl.getLastSettlementCoord()));

            /**
             * send number of playing pieces in hand
             */
            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.ROADS, pl.getNumPieces(SOCPlayingPiece.ROAD)));
            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.SETTLEMENTS, pl.getNumPieces(SOCPlayingPiece.SETTLEMENT)));
            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.CITIES, pl.getNumPieces(SOCPlayingPiece.CITY)));
            if (gameData.hasSeaBoard)
                c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.SHIPS, pl.getNumPieces(SOCPlayingPiece.SHIP)));

            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.UNKNOWN, pl.getResources().getTotal()));

            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.NUMKNIGHTS, pl.getNumKnights()));

            final int numDevCards = pl.getDevCards().getTotal();
            final int unknownType;
            if (c.getVersion() >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES)
                unknownType = SOCDevCardConstants.UNKNOWN;
            else
                unknownType = SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X;
            final String cardUnknownCmd = SOCDevCard.toCmd(gameName, i, SOCDevCard.ADDOLD, unknownType);
            for (int j = 0; j < numDevCards; j++)
            {
                c.put(cardUnknownCmd);
            }

            if (i == 0)
            {
                // per-game data, send once
                c.put(SOCFirstPlayer.toCmd(gameName, gameData.getFirstPlayer()));

                c.put(SOCDevCardCount.toCmd(gameName, gameData.getNumDevCards()));
            }

            c.put(SOCChangeFace.toCmd(gameName, i, pl.getFaceId()));

            if (i == 0)
            {
                // per-game data, send once

                c.put(SOCDiceResult.toCmd(gameName, gameData.getCurrentDice()));
            }
        }

        ///
        /// send who has longest road
        ///
        SOCPlayer lrPlayer = gameData.getPlayerWithLongestRoad();
        int lrPlayerNum = -1;

        if (lrPlayer != null)
        {
            lrPlayerNum = lrPlayer.getPlayerNumber();
        }

        c.put(SOCLongestRoad.toCmd(gameName, lrPlayerNum));

        ///
        /// send who has largest army
        ///
        final SOCPlayer laPlayer = gameData.getPlayerWithLargestArmy();
        final int laPlayerNum;
        if (laPlayer != null)
        {
            laPlayerNum = laPlayer.getPlayerNumber();
        }
        else
        {
            laPlayerNum = -1;
        }

        c.put(SOCLargestArmy.toCmd(gameName, laPlayerNum));

        /**
         * If we're rejoining and taking over a seat after a network problem,
         * send our resource and hand information.
         */
        if (isTakingOver)
        {
            SOCPlayer cliPl = gameData.getPlayer((String) c.getData());
            if (cliPl != null)
            {
                int pn = cliPl.getPlayerNumber();
                if ((pn != -1) && ! gameData.isSeatVacant(pn))
                    sitDown_sendPrivateInfo(gameData, c, pn, gameName);
            }
        }

        String membersCommand = null;
        gameList.takeMonitorForGame(gameName);

        /**
         * Almost done; send GAMEMEMBERS as a hint to client that we're almost ready for its input.
         * There's no new data in GAMEMEMBERS, because player names have already been sent by
         * the SITDOWN messages above.
         */
        try
        {
            Vector<StringConnection> gameMembers = gameList.getMembers(gameName);
            membersCommand = SOCGameMembers.toCmd(gameName, gameMembers);
        }
        catch (Exception e)
        {
            D.ebugPrintln("Exception in handleJOINGAME (gameMembers) - " + e);
        }

        gameList.releaseMonitorForGame(gameName);
        c.put(membersCommand);
        // before v2.0.00, current player number (SETTURN) was sent here,
        // between membersCommand and GAMESTATE.
        c.put(SOCGameState.toCmd(gameName, gameData.getGameState()));
        D.ebugPrintln("*** " + c.getData() + " joined the game " + gameName + " from " + c.host());

        //messageToGame(gameName, new SOCGameTextMsg(gameName, SERVERNAME, n+" joined the game"));
        /**
         * Let everyone else know about the change
         */
        if (isTakingOver)
        {
            return;
        }
        messageToGame(gameName, new SOCJoinGame((String)c.getData(), "", "dummyhost", gameName));
    }

    /**
     * This player is sitting down at the game
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
                // If reset, player is already added and knows if robot.
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
                        messageToPlayer(c, gaName, "You cannot sit down here.");
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
                    startGame(ga);
                }

                /**
                 * if the request list is empty, remove the empty list
                 */
                if (requests.isEmpty())
                {
                    robotJoinRequests.remove(gaName);
                }
            }

            broadcastGameStats(ga);

            /**
             * send all the private information
             */
            sitDown_sendPrivateInfo(ga, c, pn, gaName);
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "Exception caught at sitDown");
        }

        ga.releaseMonitor();
    }

    /**
     * When player has just sat down at a seat, send all the private information.
     * Called from {@link #sitDown(SOCGame, StringConnection, int, boolean, boolean)}.
     *<P>
     * <b>Locks:</b> Assumes ga.takeMonitor() is held, and should remain held.
     *
     * @param ga     the game
     * @param c      the connection for the player
     * @param pn     which seat the player is taking
     * @param gaName the game's name (for convenience)
     * @since 1.1.08
     */
    private void sitDown_sendPrivateInfo(SOCGame ga, StringConnection c, int pn, final String gaName)
    {
        final SOCPlayer pl = ga.getPlayer(pn);

        /**
         * send all the private information
         */
        SOCResourceSet resources = pl.getResources();
        // CLAY, ORE, SHEEP, WHEAT, WOOD, UNKNOWN
        for (int res = SOCPlayerElement.CLAY; res <= SOCPlayerElement.UNKNOWN; ++res)
            messageToPlayer(c, new SOCPlayerElement(gaName, pn, SOCPlayerElement.SET, res, resources.getAmount(res)));

        SOCDevCardSet devCards = pl.getDevCards();

        final boolean cliVersionNew = (c.getVersion() >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES);

        /**
         * remove the unknown cards
         */
        final SOCDevCard cardUnknown = (cliVersionNew)
            ? new SOCDevCard(gaName, pn, SOCDevCard.PLAY, SOCDevCardConstants.UNKNOWN)
            : new SOCDevCard(gaName, pn, SOCDevCard.PLAY, SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X);
        for (int i = 0; i < devCards.getTotal(); i++)
        {
            messageToPlayer(c, cardUnknown);
        }

        /**
         * send first all new cards, then all old cards
         */
        for (int dcAge = SOCDevCardSet.NEW; dcAge >= SOCDevCardSet.OLD; --dcAge)
        {
            final int addCmd = (dcAge == SOCDevCardSet.NEW) ? SOCDevCard.ADDNEW : SOCDevCard.ADDOLD;

            /**
             * loop for all known card types
             */
            for (int dcType = SOCDevCardConstants.MIN_KNOWN; dcType < SOCDevCardConstants.MAXPLUSONE; ++dcType)
            {
                int cardAmt = devCards.getAmount(dcAge, dcType);
                if (cardAmt > 0)
                {
                    SOCDevCard addMsg;
                    if (cliVersionNew || (dcType != SOCDevCardConstants.KNIGHT))
                        addMsg = new SOCDevCard(gaName, pn, addCmd, dcType);
                    else
                        addMsg = new SOCDevCard(gaName, pn, addCmd, SOCDevCardConstants.KNIGHT_FOR_VERS_1_X);

                    for ( ; cardAmt > 0; --cardAmt)
                        messageToPlayer(c, addMsg);
                }

            }  // for (dcType)

        }  // for (dcAge)

        /**
         * send scenario info
         */
        int itm = pl.getSpecialVP();
        if (itm != 0)
            messageToPlayer(c, new SOCPlayerElement
                    (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_SVP, itm));

        itm = pl.getScenarioPlayerEvents();
        if (itm != 0)
            messageToPlayer(c, new SOCPlayerElement
                    (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_PLAYEREVENTS_BITMASK, itm));

        itm = pl.getScenarioSVPLandAreas();
        if (itm != 0)
            messageToPlayer(c, new SOCPlayerElement
                (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_SVP_LANDAREAS_BITMASK, itm));

        itm = pl.getCloth();
        if (itm != 0)
            messageToPlayer(c, new SOCPlayerElement
                (gaName, pn, SOCPlayerElement.SET, SOCPlayerElement.SCENARIO_CLOTH_COUNT, itm));

        /**
         * send game state info such as requests for discards
         */
        sendGameState(ga);

        if ((ga.getCurrentDice() == 7) && pl.getNeedToDiscard())
        {
            messageToPlayer(c, new SOCDiscardRequest(gaName, pl.getResources().getTotal() / 2));
        }
        else if (ga.hasSeaBoard)
        {
            final int numGoldRes = pl.getNeedToPickGoldHexResources();
            if (numGoldRes > 0)
                messageToPlayer(c, new SOCPickResourcesRequest(gaName, numGoldRes));
        }

        /**
         * send what face this player is using
         */
        messageToGame(gaName, new SOCChangeFace(gaName, pn, pl.getFaceId()));
    }

    /**
     * The current player is stealing from another player.
     * Send messages saying what was stolen.
     *
     * @param ga  the game
     * @param pe  the perpetrator
     * @param vi  the the victim
     * @param rsrc  type of resource stolen, as in {@link SOCResourceConstants#SHEEP},
     *              or {@link SOCResourceConstants#CLOTH_STOLEN_LOCAL} for cloth
     *              (scenario option {@link SOCGameOption#K_SC_CLVI _SC_CLVI}).
     */
    protected void reportRobbery(SOCGame ga, SOCPlayer pe, SOCPlayer vi, final int rsrc)
    {
        if (ga == null)
            return;

        final String gaName = ga.getName();
        final String peName = pe.getName();
        final String viName = vi.getName();
        final int pePN = pe.getPlayerNumber();
        final int viPN = vi.getPlayerNumber();
        if (rsrc == SOCResourceConstants.CLOTH_STOLEN_LOCAL)
        {
            // Send players' cloth counts and text.
            // Client's game will recalculate players' VP based on
            // the cloth counts, so we don't need to also send VP.

            messageToGame(gaName,
                new SOCPlayerElement(gaName, viPN, SOCPlayerElement.SET,
                    SOCPlayerElement.SCENARIO_CLOTH_COUNT, vi.getCloth()));
            messageToGame(gaName,
                new SOCPlayerElement(gaName, pePN, SOCPlayerElement.SET,
                    SOCPlayerElement.SCENARIO_CLOTH_COUNT, pe.getCloth()));
            messageToGame(gaName, peName + " stole a cloth from " + viName);

            return;  // <--- early return: cloth is announced to entire game ---
        }

        StringBuffer mes = new StringBuffer(" stole ");  // " stole a sheep resource from "
        SOCPlayerElement gainRsrc = null;
        SOCPlayerElement loseRsrc = null;
        SOCPlayerElement gainUnknown;
        SOCPlayerElement loseUnknown;

        final String aResource = SOCResourceConstants.aResName(rsrc);
        mes.append(aResource);  // "a clay"

        // This works because SOCPlayerElement.SHEEP == SOCResourceConstants.SHEEP.
        gainRsrc = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, rsrc, 1);
        loseRsrc = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, rsrc, 1);

        mes.append(" resource from ");

        /**
         * send the game messages
         */
        StringConnection peCon = getConnection(peName);
        StringConnection viCon = getConnection(viName);
        messageToPlayer(peCon, gainRsrc);
        messageToPlayer(peCon, loseRsrc);
        messageToPlayer(viCon, gainRsrc);
        messageToPlayer(viCon, loseRsrc);
        // Don't send generic message to pe or vi
        Vector<StringConnection> exceptions = new Vector<StringConnection>(2);
        exceptions.addElement(peCon);
        exceptions.addElement(viCon);
        gainUnknown = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, SOCPlayerElement.UNKNOWN, 1);
        loseUnknown = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, 1);
        messageToGameExcept(gaName, exceptions, gainUnknown, true);
        messageToGameExcept(gaName, exceptions, loseUnknown, true);

        /**
         * send the text messages:
         * "You stole a sheep resource from viName."
         * "peName stole a sheep resource from you."
         * "peName stole a resource from viName."
         */
        messageToPlayer(peCon, gaName,
            "You" + mes.toString() + viName + '.');
        messageToPlayer(viCon, gaName,
            peName + mes.toString() + "you.");
        messageToGameExcept(gaName, exceptions, new SOCGameTextMsg(gaName, SERVERNAME,
            peName + " stole a resource from " + viName), true);
    }

    /**
     * send the current state of the game with a message.
     * Assumes current player does not change during this state.
     * If we send a text message to prompt the new player to roll,
     * also sends a RollDicePrompt data message.
     * If the client is too old (1.0.6), it will ignore the prompt.
     *<P>
     * State {@link SOCGame#WAITING_FOR_DISCARDS}:
     * If a 7 is rolled, will also say who must discard (in a GAMETEXTMSG).
     *<P>
     * For more details and references, see {@link #sendGameState(SOCGame, boolean)}.
     *
     * @param ga  the game
     */
    protected void sendGameState(SOCGame ga)
    {
        sendGameState(ga, true);
    }
    
    /**
     * send the current state of the game with a message.
     * Note that the current (or new) player number is not sent here.
     * If game is now OVER, send appropriate messages.
     *<P>
     * State {@link SOCGame#WAITING_FOR_DISCARDS}:
     * If a 7 is rolled, will also say who must discard (in a GAMETEXTMSG).
     *<P>
     * State {@link SOCGame#STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}:
     * To announce the player must pick a resource to gain from the gold hex initial placement,
     * please call {@link #sendGameState_sendGoldPickAnnounceText(SOCGame, String, StringConnection)}.
     *<P>
     * State {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE}:
     * If a gold hex is rolled, does not say who
     * must pick resources to gain (because of timing).  Please call
     * {@link #sendGameState_sendGoldPickAnnounceText(SOCGame, String, StringConnection)}
     * after sending the resource gain text ("x gets 1 sheep").
     *
     * @see #sendTurn(SOCGame, boolean)
     * @see #sendGameState(SOCGame)
     * @see #sendGameStateOVER(SOCGame)
     *
     * @param ga  the game
     * @param sendRollPrompt  If true, and if we send a text message to prompt
     *    the player to roll, send a RollDicePrompt data message.
     *    If the client is too old (1.0.6), it will ignore the prompt.
     *
     * @return    did we send a text message to prompt the player to roll?
     *    If so, sendTurn can also send a RollDicePrompt data message.
     */
    protected boolean sendGameState(SOCGame ga, boolean sendRollPrompt)
    {
        if (ga == null)
            return false;

        final String gname = ga.getName();
        boolean promptedRoll = false;
        if (ga.getGameState() == SOCGame.OVER)
        {
            /**
             * Before sending state "OVER", enforce current player number.
             * This helps the client's copy of game recognize winning condition.
             */
            messageToGame(gname, new SOCSetTurn(gname, ga.getCurrentPlayerNumber()));
        }
        messageToGame(gname, new SOCGameState(gname, ga.getGameState()));

        SOCPlayer player = null;

        if (ga.getCurrentPlayerNumber() != -1)
        {
            player = ga.getPlayer(ga.getCurrentPlayerNumber());
        }

        switch (ga.getGameState())
        {
        case SOCGame.START1A:
        case SOCGame.START2A:
        case SOCGame.START3A:
            messageToGame(gname, "It's " + player.getName() + "'s turn to build a settlement.");
            if ((ga.getGameState() >= SOCGame.START2A)
                && ga.isGameOptionSet(SOCGameOption.K_SC_3IP))
            {
                // reminder to player before their 2nd, 3rd settlements
                StringConnection con = getConnection(player.getName());
                if (con != null)
                {
                    con.put(SOCGameTextMsg.toCmd
                        (gname, SERVERNAME, "This game gives you 3 initial settlements and roads."));
                    con.put(SOCGameTextMsg.toCmd
                        (gname, SERVERNAME, "Your free resources will be from the third settlement."));
                }
            }
            break;

        case SOCGame.START1B:
        case SOCGame.START2B:
        case SOCGame.START3B:
            messageToGame(gname, "It's " + player.getName()
                + ((ga.hasSeaBoard) ? "'s turn to build a road or ship.": "'s turn to build a road."));

            break;

        case SOCGame.PLAY:
            messageToGame(gname, "It's " + player.getName() + "'s turn to roll the dice.");
            promptedRoll = true;
            if (sendRollPrompt)
                messageToGame(gname, new SOCRollDicePrompt (gname, player.getPlayerNumber()));
                
            break;

        case SOCGame.WAITING_FOR_DISCARDS:

            int count = 0;
            String[] names = new String[ga.maxPlayers];

            for (int i = 0; i < ga.maxPlayers; i++)
            {
                if (ga.getPlayer(i).getNeedToDiscard())
                {
                    names[count] = ga.getPlayer(i).getName();
                    count++;
                }
            }

            String message = sendGameState_buildPlayerNamesText(count, names, "discard.");
            messageToGame(gname, message);
            break;

        // case SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE and
        // case SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE are now
            // handled in handlePUTPIECE and handleROLLDICE, so it's sent after
            // the resource texts ("x gets 1 sheep") and not before.
            // These methods directly call sendGameState_sendGoldPickAnnounceText.

        case SOCGame.WAITING_FOR_ROBBER_OR_PIRATE:
            messageToGame(gname, player.getName() + " must choose to move the robber or the pirate.");
            break;

        case SOCGame.PLACING_ROBBER:
            messageToGame(gname, player.getName() + " will move the robber.");
            break;

        case SOCGame.PLACING_PIRATE:
            messageToGame(gname, player.getName() + " will move the pirate ship.");
            break;

        case SOCGame.WAITING_FOR_CHOICE:

            /**
             * get the choices from the game
             */
            boolean[] choices = new boolean[ga.maxPlayers];

            for (int i = 0; i < ga.maxPlayers; i++)
            {
                choices[i] = false;
            }

            Enumeration<SOCPlayer> plEnum = ga.getPossibleVictims().elements();

            while (plEnum.hasMoreElements())
            {
                SOCPlayer pl = plEnum.nextElement();
                choices[pl.getPlayerNumber()] = true;
            }

            /**
             * ask the current player to choose a player to steal from
             */
            StringConnection con = getConnection(ga.getPlayer(ga.getCurrentPlayerNumber()).getName());
            if (con != null)
            {
                con.put(SOCChoosePlayerRequest.toCmd(gname, choices));
            }

            break;

        case SOCGame.OVER:

            sendGameStateOVER(ga);
            
            break;
            
        }  // switch ga.getGameState
        
        return promptedRoll;
    }

    /**
     * For discard or gold hex in {@link #sendGameState(SOCGame, boolean), build
     * the list of player names affected.
     * @param count  Number of players to name
     * @param names  Player names
     * @param needToVerb  "discard." or "pick resources."
     * @return message text, such as "x, y and z need to discard."
     * @since 2.0.00
     */
    private final String sendGameState_buildPlayerNamesText
        (final int count, final String[] names, final String needToVerb)
    {
        String message = "error at sendGameState()";  // default contents

        if (count == 1)
        {
            message = names[0] + " needs to " + needToVerb;
        }
        else if (count == 2)
        {
            message = names[0] + " and " + names[1] + " need to " + needToVerb;
        }
        else if (count > 2)
        {
            message = names[0];

            for (int i = 1; i < (count - 1); i++)
            {
                message += (", " + names[i]);
            }

            message += (" and " + names[count - 1] + " need to " + needToVerb);
        }

        return message;
    }

    /**
     * Send a game text message "x, y, and z need to pick resources from the gold hex."
     * and, for each picking player, a {@link SOCPlayerElement}({@link SOCPlayerElement#NUM_PICK_GOLD_HEX_RESOURCES NUM_PICK_GOLD_HEX_RESOURCES}).
     * Used in game state {@link SOCGame#STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}
     * and {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE}.
     *<P>
     * These messages are sent to the entire game. To prompt the specific players to choose a resource,
     * you must send their clients a {@link SOCPickResourcesRequest}.
     * If you know that only 1 player will pick gold, send it here by setting <tt>playerCon</tt>.
     *<P>
     * This is separate from {@link #sendGameState(SOCGame)} because when the dice are rolled,
     * <tt>sendGameState</tt> is called, then resource distribution messages are sent out,
     * then this method is called.
     *
     * @param ga  Game object
     * @param gname  Game name
     * @param playerCon  <tt>null</tt>, or current player's client connection to send
     *                   {@link SOCPickResourcesRequest} if they are the only one to pick gold.
     *                   If more than 1 player has {@link SOCPlayer#getNeedToPickGoldHexResources()},
     *                   no message will be sent to <tt>playerCon</tt>.
     * @since 2.0.00
     */
    private final void sendGameState_sendGoldPickAnnounceText
        (SOCGame ga, final String gname, StringConnection playerCon)
    {
        int count = 0, amount = 0;
        String[] names = new String[ga.maxPlayers];
        int[] num = new int[ga.maxPlayers];

        for (int pl = 0; pl < ga.maxPlayers; ++pl)
        {
            final int numGoldRes = ga.getPlayer(pl).getNeedToPickGoldHexResources();
            if (numGoldRes > 0)
            {
                num[pl] = numGoldRes;
                names[count] = ga.getPlayer(pl).getName();
                count++;
                if (count == 1)
                    amount = numGoldRes;
            }
        }

        messageToGame(gname, sendGameState_buildPlayerNamesText
            (count, names, "pick resources from the gold hex."));
        for (int pl = 0; pl < ga.maxPlayers; ++pl)
            if (num[pl] > 0)
                messageToGame(gname, new SOCPlayerElement
                    (gname, pl, SOCPlayerElement.SET, SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES, num[pl]));

        if ((playerCon != null) && (count == 1))
            messageToPlayer(playerCon, new SOCPickResourcesRequest(gname, amount));
    }

    /**
     *  If game is OVER, send messages reporting winner, final score,
     *  and each player's victory-point cards.
     *  Also give stats on game length, and on each player's connect time.
     *  If player has finished more than 1 game since connecting, send win-loss count.
     *<P>
     *  If db is active, calls {@link #storeGameScores(SOCGame)}
     *  if {@link SOCDBHelper#PROP_JSETTLERS_DB_SAVE_GAMES} setting is active.
     *
     * @param ga This game is over; state should be OVER
     */
    protected void sendGameStateOVER(SOCGame ga)
    {
        final String gname = ga.getName();
        String msg;

        /**
         * Find and announce the winner
         * (the real "found winner" code is in SOCGame.checkForWinner;
         *  that was already called before sendGameStateOVER.)
         */
        SOCPlayer winPl = ga.getPlayer(ga.getCurrentPlayerNumber());

        if ((winPl.getTotalVP() < ga.vp_winner) && ! ga.hasScenarioWinCondition)
        {
            // Should not happen: By rules FAQ, only current player can be winner.
            // This is fallback code.
            for (int i = 0; i < ga.maxPlayers; i++)
            {
                if (winPl.getTotalVP() >= ga.vp_winner)
                {
                    winPl = ga.getPlayer(i);
                    break;
                }
            }
        }
        msg = ">>> " + winPl.getName() + " has won the game with " + winPl.getTotalVP() + " points.";
        messageToGameUrgent(gname, msg);
        
        /// send a message with the revealed final scores
        {
            int[] scores = new int[ga.maxPlayers];
            boolean[] isRobot = new boolean[ga.maxPlayers];
            for (int i = 0; i < ga.maxPlayers; ++i)
            {
                scores[i] = ga.getPlayer(i).getTotalVP();
                isRobot[i] = ga.getPlayer(i).isRobot();
            }
            messageToGame(gname, new SOCGameStats(gname, scores, isRobot));
        }
        
        ///
        /// send a message saying what VP cards each player has
        ///
        for (int i = 0; i < ga.maxPlayers; i++)
        {
            SOCPlayer pl = ga.getPlayer(i);
            SOCDevCardSet devCards = pl.getDevCards();

            if (devCards.getNumVPCards() > 0)
            {
                msg = pl.getName() + " has";
                int vpCardCount = 0;

                for (int devCardType = SOCDevCardConstants.MIN_KNOWN;
                         devCardType < SOCDevCardConstants.MAXPLUSONE;
                         devCardType++)
                {
                    if (! SOCDevCardSet.isVPCard(devCardType))
                        continue;

                    if ((devCards.getAmount(SOCDevCardSet.OLD, devCardType) > 0) || (devCards.getAmount(SOCDevCardSet.NEW, devCardType) > 0))
                    {
                        if (vpCardCount > 0)
                        {
                            if ((devCards.getNumVPCards() - vpCardCount) == 1)
                            {
                                msg += " and";
                            }
                            else if ((devCards.getNumVPCards() - vpCardCount) > 0)
                            {
                                msg += ",";
                            }
                        }

                        vpCardCount++;

                        switch (devCardType)
                        {
                        case SOCDevCardConstants.CAP:
                            msg += " a Gov.House (+1VP)";

                            break;

                        case SOCDevCardConstants.LIB:
                            msg += " a Market (+1VP)";

                            break;

                        case SOCDevCardConstants.UNIV:
                            msg += " a University (+1VP)";

                            break;

                        case SOCDevCardConstants.TEMP:
                            msg += " a Temple (+1VP)";

                            break;

                        case SOCDevCardConstants.TOW:
                            msg += " a Chapel (+1VP)";

                            break;
                        }
                    }
                }  // for each devcard type

                messageToGame(gname, msg);

            }  // if devcards
        }  // for each player

        /**
         * send game-length and connect-length messages, possibly win-loss count.
         */
        {
            Date now = new Date();
            Date gstart = ga.getStartTime();
            final String gLengthMsg;
            if (gstart != null)
            {
                StringBuffer sb = new StringBuffer("This game was ");
                sb.append(ga.getRoundCount());
                sb.append(" rounds, and took ");
                long gameSeconds = ((now.getTime() - gstart.getTime())+500L) / 1000L;
                long gameMinutes = gameSeconds/60L;
                gameSeconds = gameSeconds % 60L;
                sb.append(gameMinutes);
                if (gameSeconds == 0)
                {
                    sb.append(" minutes.");
                } else if (gameSeconds == 1)
                {
                    sb.append(" minutes 1 second.");
                } else {
                    sb.append(" minutes ");
                    sb.append(gameSeconds);
                    sb.append(" seconds.");
                }
                gLengthMsg = sb.toString();
                messageToGame(gname, gLengthMsg);

                // Ignore possible "1 minutes"; that game is too short to worry about.
            } else {
                gLengthMsg = null;
            }

            /**
             * Update each player's win-loss count for this session.
             * Tell each player their resource roll totals.
             * Tell each player how long they've been connected.
             * (Robot players aren't told this, it's not necessary.)
             */
            String connMsg;
            if ((strSocketName != null) && (strSocketName.equals(PRACTICE_STRINGPORT)))
                connMsg = "You have been practicing ";
            else
                connMsg = "You have been connected ";

            for (int i = 0; i < ga.maxPlayers; i++)
            {
                if (ga.isSeatVacant(i))
                    continue;

                SOCPlayer pl = ga.getPlayer(i);
                StringConnection plConn = conns.get(pl.getName());
                SOCClientData cd;
                if (plConn != null)
                {
                    // Update win-loss count, even for robots
                    cd = (SOCClientData) plConn.getAppData();
                    if (pl == winPl)
                        cd.wonGame();
                    else
                        cd.lostGame();
                } else {
                    cd = null;  // To satisfy compiler warning
                }

                if (pl.isRobot())
                    continue;  // <-- Don't bother to send any stats text to robots --

                if (plConn != null)
                {
                    if (plConn.getVersion() >= SOCPlayerStats.VERSION_FOR_RES_ROLL)
                    {
                        // Send total resources rolled
                        messageToPlayer(plConn, new SOCPlayerStats(pl, SOCPlayerStats.STYPE_RES_ROLL));
                    }

                    long connTime = plConn.getConnectTime().getTime();
                    long connMinutes = (((now.getTime() - connTime)) + 30000L) / 60000L;
                    StringBuffer cLengthMsg = new StringBuffer(connMsg);
                    cLengthMsg.append(connMinutes);
                    if (connMinutes == 1)
                        cLengthMsg.append(" minute.");
                    else
                        cLengthMsg.append(" minutes.");
                    messageToPlayer(plConn, gname, cLengthMsg.toString());

                    // Send client's win-loss count for this session,
                    // if more than 1 game has been played
                    {
                        int wins = cd.getWins();
                        int losses = cd.getLosses();
                        if (wins + losses < 2)
                            continue;  // Only 1 game played so far

                        StringBuffer winLossMsg = new StringBuffer("You have ");
                        if (wins > 0)
                        {
                            winLossMsg.append("won ");
                            winLossMsg.append(wins);
                            if (losses == 0)
                            {
                                if (wins != 1)
                                    winLossMsg.append(" games");
                                else
                                    winLossMsg.append(" game");
                            } else {
                                winLossMsg.append(" and ");
                            }
                        }
                        if (losses > 0)
                        {
                            winLossMsg.append("lost ");
                            winLossMsg.append(losses);
                            if (losses != 1)
                                winLossMsg.append(" games");
                            else
                                winLossMsg.append(" game");
                        }
                        winLossMsg.append(" since connecting.");
                        messageToPlayer(plConn, gname, winLossMsg.toString());
                    }
                }
            }  // for each player

        }  // send game timing stats, win-loss stats

        //
        // Save game stats in the database,
        // if that setting is active
        //
        if (init_getBoolProperty
            (props, SOCDBHelper.PROP_JSETTLERS_DB_SAVE_GAMES, false))
        {
            storeGameScores(ga);
        }

    }

    /**
     * report a trade that has taken place between players, using {@link SOCPlayerElement}
     * and {@link SOCGameTextMsg} messages.  Trades are also reported to robots
     * by re-sending the accepting player's {@link SOCAcceptOffer} message.
     *
     * @param ga        the game
     * @param offering  the number of the player making the offer
     * @param accepting the number of the player accepting the offer
     *
     * @see #reportBankTrade(SOCGame, SOCResourceSet, SOCResourceSet)
     */
    protected void reportTrade(SOCGame ga, int offering, int accepting)
    {
        if (ga == null)
            return;

        final String gaName = ga.getName();
        final SOCTradeOffer offer = ga.getPlayer(offering).getCurrentOffer();

        StringBuffer message = new StringBuffer(ga.getPlayer(offering).getName());
        message.append(" traded ");
        reportRsrcGainLoss(gaName, offer.getGiveSet(), true, offering, accepting, message, null);
        message.append(" for ");
        reportRsrcGainLoss(gaName, offer.getGetSet(), false, offering, accepting, message, null);
        message.append(" from ");
        message.append(ga.getPlayer(accepting).getName());
        message.append('.');
        messageToGame(gaName, message.toString());
    }

    /**
     * report that the current player traded with the bank or a port,
     * using {@link SOCPlayerElement} and {@link SOCGameTextMsg} messages.
     *
     * @param ga        the game
     * @param give      the number of the player making the offer
     * @param get       the number of the player accepting the offer
     *
     * @see #reportTrade(SOCGame, int, int)
     */
    protected void reportBankTrade(SOCGame ga, SOCResourceSet give, SOCResourceSet get)
    {
        if (ga == null)
            return;

        final String gaName = ga.getName();
        final int    cpn    = ga.getCurrentPlayerNumber();
        StringBuffer message = new StringBuffer (ga.getPlayer(cpn).getName());
        message.append(" traded ");
        reportRsrcGainLoss(gaName, give, true, cpn, -1, message, null);
        message.append(" for ");
        reportRsrcGainLoss(gaName, get, false, cpn, -1, message, null);

        final int giveTotal = give.getTotal(),
            getTotal = get.getTotal();
        if ((giveTotal / getTotal) == 4)
        {
            message.append(" from the bank.");  // 4:1 trade
        }
        else
        {
            message.append(" from a port.");    // 3:1 or 2:1 trade
        }
        if (giveTotal < getTotal)
            message.append(" (Undo previous trade)");

        messageToGame(gaName, message.toString());
    }

    /**
     * Report the resources gained/lost by a player, and optionally (for trading)
     * lost/gained by a second player.
     * Sends PLAYERELEMENT messages, either to entire game, or to player only.
     * Builds the resource-amount string used to report the trade as text.
     * Takes and releases the gameList monitor for this game.
     *<P>
     * Used to report the resources gained from a roll, discard, or discovery (year-of-plenty) pick.
     * Also used to report the "give" or "get" half of a resource trade.
     *
     * @param gaName  Game name
     * @param rset    Resource set (from a roll, or the "give" or "get" side of a trade).
     *                Resource type {@link SOCResourceConstants#UNKNOWN UNKNOWN} or
     *                {@link SOCResourceConstants#GOLD_LOCAL GOLD_LOCAL} is ignored.
     *                Only positive resource amounts are sent (negative is ignored).
     * @param isLoss  If true, "give" ({@link SOCPlayerElement#LOSE}), otherwise "get" ({@link SOCPlayerElement#GAIN})
     * @param mainPlayer     Player number "giving" if isLose==true, otherwise "getting".
     *                For each nonzero resource involved, PLAYERELEMENT messages will be sent about this player.
     * @param tradingPlayer  Player number on other side of trade, or -1 if no second player is involved.
     *                If not -1, PLAYERELEMENT messages will also be sent about this player.
     * @param message Append resource numbers/types to this stringbuffer,
     *                format like "3 clay,3 wood"; can be null.
     * @param playerConn     Null or mainPlayer's connection; send messages here instead of
     *                sending to all players in game.  Because trades are public, there is no
     *                such parameter for tradingPlayer.
     *
     * @see #reportTrade(SOCGame, int, int)
     * @see #reportBankTrade(SOCGame, SOCResourceSet, SOCResourceSet)
     * @see #handleDISCARD(StringConnection, SOCDiscard)
     * @see #handleDISCOVERYPICK(StringConnection, SOCDiscoveryPick)
     * @see #handleROLLDICE(StringConnection, SOCRollDice)
     */
    private void reportRsrcGainLoss
        (final String gaName, final SOCResourceSet rset, final boolean isLoss, final int mainPlayer, final int tradingPlayer,
         StringBuffer message, StringConnection playerConn)
    {
        final int losegain  = isLoss ? SOCPlayerElement.LOSE : SOCPlayerElement.GAIN;  // for pnA
        final int gainlose  = isLoss ? SOCPlayerElement.GAIN : SOCPlayerElement.LOSE;  // for pnB

        boolean needComma = false;  // Has a resource already been appended to message?

        gameList.takeMonitorForGame(gaName);

        for (int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; ++res)
        {
            // This works because SOCPlayerElement.SHEEP == SOCResourceConstants.SHEEP.

            final int amt = rset.getAmount(res);
            if (amt <= 0)
                continue;
            if (playerConn != null)
                messageToPlayer(playerConn, new SOCPlayerElement(gaName, mainPlayer, losegain, res, amt));
            else
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, mainPlayer, losegain, res, amt));
            if (tradingPlayer != -1)
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, tradingPlayer, gainlose, res, amt));
            if (message != null)
            {
                if (needComma)
                    message.append(", ");
                message.append(amt);
                message.append(" ");
                message.append(SOCResourceConstants.resName(res));
                needComma = true;
            }
        }

        gameList.releaseMonitorForGame(gaName);
    }

    /**
     * Report to game members what a player picked from the gold hex.
     * Sends {@link SOCPlayerElement} for resources and to reset the
     * {@link SOCPlayerElement#NUM_PICK_GOLD_HEX_RESOURCES} counter.
     * Sends text "playername gets ___ from the gold hex.".
     * @param gn      Game name
     * @param player  Player gaining
     * @param pn      <tt>player</tt>{@link SOCPlayer#getPlayerNumber() .getPlayerNumber()}
     * @param rsrcs   Resources picked
     * @since 2.0.00
     */
    private void reportRsrcGainGold
        (final String gn, final SOCPlayer player, final int pn, final SOCResourceSet rsrcs)
    {
        StringBuffer gainsText = new StringBuffer();
        gainsText.append(player.getName());
        gainsText.append(" gets ");
        // Send SOCPlayerElement messages,
        // build resource-text in gainsText.
        reportRsrcGainLoss(gn, rsrcs, false, pn, -1, gainsText, null);
        gainsText.append(" from the gold hex.");
        messageToGame(gn, gainsText.toString());
        messageToGame(gn, new SOCPlayerElement
            (gn, pn, SOCPlayerElement.SET, SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES, 0));
    }

    /**
     * make sure it's the player's turn
     *
     * @param c  the connection for player
     * @param ga the game
     *
     * @return true if it is the player's turn;
     *         false if another player's turn, or if this player isn't in the game
     */
    protected boolean checkTurn(StringConnection c, SOCGame ga)
    {
        if ((c == null) || (ga == null))
            return false;

        try
        {
            return (ga.getCurrentPlayerNumber() == ga.getPlayer((String) c.getData()).getPlayerNumber());
        }
        catch (Throwable th)
        {
            return false;
        }
    }

    /**
     * Do the stuff you need to do to start a game and send its data to the client.
     *<P>
     * If {@link SOCGame#hasSeaBoard}: Once the board is made, send the updated
     * {@link SOCPotentialSettlements potential settlements}.
     *
     * @param ga  the game
     */
    protected void startGame(SOCGame ga)
    {
        if (ga == null)
            return;

        final String gaName = ga.getName();

        numberOfGamesStarted++;
        ga.startGame();
        gameList.takeMonitorForGame(gaName);

        /**
         * send the board layout
         */
        try
        {
            messageToGameWithMon(gaName, getBoardLayoutMessage(ga));
                // For scenario option _SC_CLVI, the board layout message
                // includes villages and the general supply cloth count.
        } catch (IllegalArgumentException e) {
            gameList.releaseMonitorForGame(gaName);
            System.err.println("startGame: Cannot send board for " + gaName + ": " + e.getMessage());
            return;
        }
        if (ga.hasSeaBoard)
        {
            // See also joinGame which has very similar code.

            // Send the updated Potential/Legal Settlement node list
            // Note: Assumes all players have same potential settlements
            //    (sends with playerNumber -1 == all)
            final HashSet<Integer> psList = ga.getPlayer(0).getPotentialSettlements();

            // Some boards may have multiple land areas.
            final HashSet<Integer>[] lan;
            final int pan;
            boolean addedPsList = false;
            if (ga.hasSeaBoard)
            {
                final SOCBoardLarge bl = (SOCBoardLarge) ga.getBoard();
                lan = bl.getLandAreasLegalNodes();
                pan = bl.getStartingLandArea();
                if ((lan != null) && ! lan[pan].equals(psList))
                {
                    // If potentials != legals[startingLandArea], send as legals[0]
                    lan[0] = psList;
                    addedPsList = true;
                }
            } else {
                lan = null;
                pan = 0;
            }

            if (lan == null)
                messageToGameWithMon(gaName, new SOCPotentialSettlements(gaName, -1, new Vector<Integer>(psList)));
            else
                messageToGameWithMon(gaName, new SOCPotentialSettlements(gaName, -1, pan, lan));

            if (addedPsList)
                lan[0] = null;  // Undo change to game's copy of landAreasLegalNodes
        }

        /**
         * send the player info
         */
        for (int i = 0; i < ga.maxPlayers; i++)
        {
            if (! ga.isSeatVacant(i))
            {
                SOCPlayer pl = ga.getPlayer(i);
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.ROADS, pl.getNumPieces(SOCPlayingPiece.ROAD)));
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.SETTLEMENTS, pl.getNumPieces(SOCPlayingPiece.SETTLEMENT)));
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.CITIES, pl.getNumPieces(SOCPlayingPiece.CITY)));
                if (ga.hasSeaBoard)
                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.SHIPS, pl.getNumPieces(SOCPlayingPiece.SHIP)));
                messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, i, false));
            }
        }

        /**
         * send the number of dev cards
         */
        messageToGameWithMon(gaName, new SOCDevCardCount(gaName, ga.getNumDevCards()));

        /**
         * ga.startGame() picks who goes first, but feedback is nice
         */
        messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, "Randomly picking a starting player..."));

        gameList.releaseMonitorForGame(gaName);

        /**
         * send the game state
         */
        sendGameState(ga, false);

        /**
         * start the game
         */
        messageToGame(gaName, new SOCStartGame(gaName));

        /**
         * send whose turn it is
         */
        sendTurn(ga, false);
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
     *    If the game was already over at reset time, different robots will
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
    private void resetBoardAndNotify (final String gaName, final int requestingPlayer)
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
            messageToGameUrgent(gaName, ">>> Internal error, Game " + gaName + " board reset failed");
            return;  // <---- Early return: reset failed ----
        }
        SOCGame reGame = reBoard.newGame;

        // Announce who asked for this reset
        {
            String plName = reGame.getPlayer(requestingPlayer).getName();
            if (plName == null)
                plName = "player who left";
            messageToGameUrgent(gaName, ">>> Game " + gaName + " board reset by "
                + plName);
        }

        // If game was over, we'll shuffle the robots
        final boolean gameWasOverAtReset = (SOCGame.OVER == reBoard.oldGameState);

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
                if (! gameWasOverAtReset)
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
     * @param reBoard
     * @param reGame
     * @since 1.1.07
     */
    private void resetBoardAndNotify_finish(SOCGameBoardReset reBoard, SOCGame reGame)
    {
        final boolean gameWasOverAtReset = (SOCGame.OVER == reBoard.oldGameState);
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
            startGame (reGame);
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
              (reGame, gameWasOverAtReset ? null : reBoard.robotConns);
        }

        // All set.
    }  // resetBoardAndNotify_finish

    /**
     * send {@link SOCTurn whose turn it is}. Optionally also send a prompt to roll.
     * If the client is too old (1.0.6), it will ignore the prompt.
     *<P>
     * sendTurn should be called whenever the current player changes, including
     * during and after initial placement.
     *
     * @param ga  the game
     * @param sendRollPrompt  whether to send a RollDicePrompt message afterwards
     */
    private void sendTurn(SOCGame ga, boolean sendRollPrompt)
    {
        if (ga == null)
            return;

        String gname = ga.getName();
        int pn = ga.getCurrentPlayerNumber();

        messageToGame(gname, new SOCSetPlayedDevCard(gname, pn, false));

        SOCTurn turnMessage = new SOCTurn(gname, pn);
        messageToGame(gname, turnMessage);
        recordGameEvent(gname, turnMessage.toCmd());

        if (sendRollPrompt)
            messageToGame(gname, new SOCRollDicePrompt(gname, pn));
    }

    /**
     * put together the board layout message.
     * Message type will be {@link SOCBoardLayout} or {@link SOCBoardLayout2},
     * depending on {@link SOCBoard#getBoardEncodingFormat() ga.getBoard().getBoardEncodingFormat()}
     * and {@link SOCGame#getClientVersionMinRequired()}.
     *
     * @param  ga   the game
     * @return   a board layout message
     * @throw IllegalArgumentException  if game board's encoding is unrecognized
     */
    private SOCMessage getBoardLayoutMessage(SOCGame ga)
        throws IllegalArgumentException
    {
        final SOCBoard board;
        int[] hexes;
        int[] numbers;
        int robber;

        board = ga.getBoard();
        final int bef = board.getBoardEncodingFormat();
        if (bef <= SOCBoard.BOARD_ENCODING_6PLAYER)
        {
            // v1 or v2
            hexes = board.getHexLayout();
            numbers = board.getNumberLayout();
        } else {
            // v3
            hexes = null;
            numbers = null;
        }
        robber = board.getRobberHex();
        if ((bef == 1) && (ga.getClientVersionMinRequired() < SOCBoardLayout2.VERSION_FOR_BOARDLAYOUT2))
        {
            // SOCBoard.BOARD_ENCODING_ORIGINAL: v1
            return new SOCBoardLayout(ga.getName(), hexes, numbers, robber);
        }
        switch (bef)
        {
        case SOCBoard.BOARD_ENCODING_ORIGINAL: // v1
            // fall through to v2
        case SOCBoard.BOARD_ENCODING_6PLAYER:  // v2
            return new SOCBoardLayout2(ga.getName(), bef, hexes, numbers, board.getPortsLayout(), robber);

        case SOCBoard.BOARD_ENCODING_LARGE:  // v3
            final SOCBoardLarge bl = (SOCBoardLarge) board;
            return new SOCBoardLayout2
                (ga.getName(), bef, bl.getLandHexLayout(), board.getPortsLayout(),
                 robber, bl.getPirateHex(), bl.getPlayerExcludedLandAreas(), bl.getRobberExcludedLandAreas(),
                 bl.getVillageAndClothLayout());

        default:
            throw new IllegalArgumentException("unknown board encoding v" + bef);
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
     * if all the players stayed for the whole game,
     * record the scores in the database.
     * Called only if property <tt>jsettlers.db.save.games</tt>
     * is true. ({@link SOCDBHelper#PROP_JSETTLERS_DB_SAVE_GAMES})
     *
     * @param ga  the game; state should be {@link SOCGame#OVER}
     */
    protected void storeGameScores(SOCGame ga)
    {
        if ((ga == null) || ! SOCDBHelper.isInitialized())
            return;

        //D.ebugPrintln("allOriginalPlayers for "+ga.getName()+" : "+ga.allOriginalPlayers());
        if (! ((ga.getGameState() == SOCGame.OVER) && ga.allOriginalPlayers()))
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
     * this is a debugging command that gives resources to a player.
     * Format: rsrcs: #cl #or #sh #wh #wo playername
     */
    protected void giveResources(StringConnection c, String mes, SOCGame game)
    {
        StringTokenizer st = new StringTokenizer(mes.substring(6));
        int[] resources = new int[SOCResourceConstants.WOOD + 1];
        int resourceType = SOCResourceConstants.CLAY;
        String name = "";
        boolean parseError = false;

        while (st.hasMoreTokens())
        {
            if (resourceType <= SOCResourceConstants.WOOD)
            {
                String token = st.nextToken();
                try
                {
                    resources[resourceType] = Integer.parseInt(token);
                    resourceType++;
                }
                catch (NumberFormatException e)
                {
                    parseError = true;
                    break;
                }
            }
            else
            {
                // get all the of the line, in case there's a space in the player name ("robot 7"),
                //  by choosing an unlikely separator character
                name = st.nextToken(Character.toString( (char) 1 )).trim();
                break;
            }
        }

        final SOCPlayer pl = game.getPlayer(name);
        if ((pl == null) && ! parseError)
        {
            messageToPlayer(c, game.getName(), "### rsrcs: Player name not found: " + name);
            parseError = true;
        }
        if (parseError)
        {
            messageToPlayer(c, game.getName(), "### Usage: " + DEBUG_COMMANDS_HELP_RSRCS);
            return;  // <--- early return ---
        }
        SOCResourceSet rset = pl.getResources();
        int pnum = pl.getPlayerNumber();
        String outMes = "### " + name + " gets";

        for (resourceType = SOCResourceConstants.CLAY;
                resourceType <= SOCResourceConstants.WOOD; resourceType++)
        {
            rset.add(resources[resourceType], resourceType);
            outMes += (" " + resources[resourceType]);

            // SOCResourceConstants.CLAY == SOCPlayerElement.CLAY
            messageToGame(game.getName(), new SOCPlayerElement(game.getName(), pnum, SOCPlayerElement.GAIN, resourceType, resources[resourceType]));
        }

        messageToGame(game.getName(), outMes);
    }

    /**
     * this broadcasts game information to all people connected
     * used to display the scores on the player client
     */
    protected void broadcastGameStats(SOCGame ga)
    {
        /*
           if (ga != null) {
           int scores[] = new int[SOCGame.MAXPLAYERS];
           boolean robots[] = new boolean[SOCGame.MAXPLAYERS];
           for (int i = 0; i < SOCGame.MAXPLAYERS; i++) {
           SOCPlayer player = ga.getPlayer(i);
           if (player != null) {
           if (ga.isSeatVacant(i)) {
           scores[i] = -1;
           robots[i] = false;
           } else {
           scores[i] = player.getPublicVP();
           robots[i] = player.isRobot();
           }
           } else {
           scores[i] = -1;
           }
           }
        
           broadcast(SOCGameStats.toCmd(ga.getName(), scores, robots));
           }
         */
    }

    /**
     * check for games that have expired and destroy them.
     * If games are about to expire, send a warning.
     * As of version 1.1.09, practice games ({@link SOCGame#isPractice} flag set) don't expire.
     * Callback method from {@link SOCGameTimeoutChecker#run()}.
     *
     * @param currentTimeMillis  The time when called, from {@link System#currentTimeMillis()}
     * @see #GAME_EXPIRE_WARN_MINUTES
     * @see #checkForExpiredTurns(long)
     */
    public void checkForExpiredGames(final long currentTimeMillis)
    {
        Vector<String> expired = new Vector<String>();

        gameList.takeMonitor();
        
        // Add 2 minutes because of coarse 5-minute granularity in SOCGameTimeoutChecker.run()
        long warn_ms = (2 + GAME_EXPIRE_WARN_MINUTES) * 60L * 1000L;

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
                    messageToGameUrgent(gameName, ">>> The time limit on this game has expired and will now be destroyed.");
                }
                else if ((gameExpir - warn_ms) <= currentTimeMillis)
                {
                    //
                    //  Give people a few minutes' warning (they may have a few warnings)
                    //
                    long minutes = ((gameExpir - currentTimeMillis) / 60000);
                    if (minutes < 1L)
                        minutes = 1;  // in case of rounding down

                    messageToGameUrgent(gameData.getName(), ">>> Less than "
                            + minutes + " minutes remaining.  Type *ADDTIME* to extend this game another 30 minutes.");
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
        //
        for (Enumeration<String> ex = expired.elements(); ex.hasMoreElements();)
        {
            String ga = ex.nextElement();
            gameList.takeMonitor();

            try
            {
                destroyGame(ga);
            }
            catch (Exception e)
            {
                D.ebugPrintln("Exception in checkForExpired - " + e);
            }

            gameList.releaseMonitor();
            broadcast(SOCDeleteGame.toCmd(ga));
        }
    }

    /**
     * Check for robot turns that have expired, and end them.
     * They may end from inactivity or from an illegal placement.
     * Checks the {@link SOCGame#lastActionTime} field.
     * Callback method from {@link SOCGameTimeoutChecker#run()}.
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
                if (SOCGame.OVER <= ga.getGameState())
                {
                    // bump out that time, so we don't see
                    // it again every few seconds
                    ga.lastActionTime
                        += (1000L * 60L * SOCGameListAtServer.GAME_EXPIRE_MINUTES);
                    continue;
                }
                final int cpn = ga.getCurrentPlayerNumber();
                if (cpn == -1)
                    continue;  // not started yet
                SOCPlayer pl = ga.getPlayer(cpn);
                if (! pl.isRobot())
                    continue;  // not the robot's turn

                final int gameState = ga.getGameState();
                if ((gameState == SOCGame.WAITING_FOR_DISCARDS) || (gameState == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE))
                {
                    // Check if we're just waiting on humans, not on the robot
                    boolean waitHumans = false;
                    for (int i = 0; i < ga.maxPlayers; ++i)
                    {
                        final SOCPlayer pli = ga.getPlayer(i);
                        if (pli.isRobot())
                            continue;
                        if (pli.getNeedToDiscard() || (pli.getNeedToPickGoldHexResources() > 0))
                        {
                            waitHumans = true;
                            break;
                        }
                    }

                    if (waitHumans)
                        continue;  // <-- Waiting on humans, don't end bot's turn --
                }

                if (pl.getCurrentOffer() != null)
                {
                    // Robot is waiting for response to a trade offer;
                    // check against that longer timeout.
                    final long tradeInactiveTime
                        = currentTimeMillis - (1000L * ROBOT_FORCE_ENDTURN_TRADEOFFER_SECONDS);
                    if (lastActionTime > tradeInactiveTime)
                        continue;
                }

                new ForceEndTurnThread(ga, pl).start();
            }
        }
        catch (Exception e)
        {
            D.ebugPrintln("Exception in checkForExpiredTurns - " + e);
        }
    }

    /** this is a debugging command that gives a dev card to a player.
     *  <PRE> dev: cardtype player </PRE>
     *  For card-types numbers, see {@link SOCDevCardConstants}
     */
    protected void giveDevCard(StringConnection c, String mes, SOCGame game)
    {
        StringTokenizer st = new StringTokenizer(mes.substring(5));
        String name = "";
        int cardType = -1;
        boolean parseError = false;

        while (st.hasMoreTokens())
        {
            if (cardType < 0)
            {
                try
                {
                    cardType = Integer.parseInt(st.nextToken());
                    if ((cardType < SOCDevCardConstants.MIN_KNOWN) || (cardType >= SOCDevCardConstants.MAXPLUSONE))
                        parseError = true;  // Can't give unknown dev cards
                }
                catch (NumberFormatException e)
                {
                    parseError = true;
                    break;
                }
            }
            else
            {
                // get all the of the line, in case there's a space in the player name ("robot 7"),
                //  by choosing an unlikely separator character
                name = st.nextToken(Character.toString( (char) 1 )).trim();
                break;
            }
        }

        final SOCPlayer pl = game.getPlayer(name);
        if ((pl == null) && ! parseError)
        {
            messageToPlayer(c, game.getName(), "### dev: Player name not found: " + name);
            parseError = true;
        }
        if (parseError)
        {
            messageToPlayer(c, game.getName(), "### Usage: " + DEBUG_COMMANDS_HELP_DEV);
            return;  // <--- early return ---
        }
        SOCDevCardSet dcSet = pl.getDevCards();
        dcSet.add(1, SOCDevCardSet.NEW, cardType);

        int pnum = pl.getPlayerNumber();
        String outMes = "### " + name + " gets a " + cardType + " card.";
        if ((cardType != SOCDevCardConstants.KNIGHT) || (game.clientVersionLowest >= SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
        {
            messageToGame(game.getName(), new SOCDevCard(game.getName(), pnum, SOCDevCard.DRAW, cardType));
        } else {
            messageToGameForVersions
                (game, -1, SOCDevCardConstants.VERSION_FOR_NEW_TYPES - 1,
                 new SOCDevCard(game.getName(), pnum, SOCDevCard.DRAW, SOCDevCardConstants.KNIGHT_FOR_VERS_1_X), true);
            messageToGameForVersions
                (game, SOCDevCardConstants.VERSION_FOR_NEW_TYPES, Integer.MAX_VALUE,
                 new SOCDevCard(game.getName(), pnum, SOCDevCard.DRAW, SOCDevCardConstants.KNIGHT), true);
        }
        messageToGame(game.getName(), outMes);
    }

    /**
     * Quick-and-dirty command line parsing of game options.
     * Calls {@link SOCGameOption#setKnownOptionCurrentValue(SOCGameOption)}.
     * @param optNameValue Game option name+value, of form expected by
     *                     {@link SOCGameOption#parseOptionNameValue(String, boolean)}
     * @return true if OK, false if bad name or value
     * @since 1.1.07
     */
    public static boolean parseCmdline_GameOption(final String optNameValue)
    {
        SOCGameOption op = SOCGameOption.parseOptionNameValue(optNameValue, true);
        if (op == null)
        {
            System.err.println("Unknown or malformed game option: " + optNameValue);
            return false;
        }
        if (op.optType == SOCGameOption.OTYPE_UNKNOWN)
        {
            System.err.println("Unknown game option: " + op.optKey);
            return false;
        }

        try
        {
            SOCGameOption.setKnownOptionCurrentValue(op);
            return true;
        } catch (Throwable t)
        {
            System.err.println("Bad value, cannot set game option: " + op.optKey);
            return false;
        }
    }

    /**
     * Quick-and-dirty parsing of command-line arguments with dashes.
     *<P>
     * If any game options are set ("-o", "--option"), then
     * {@link #hasSetGameOptions} is set to true, and
     * {@link SOCGameOption#setKnownOptionCurrentValue(SOCGameOption)}
     * is called to set them globally.
     *<P>
     * If <tt>args[]</tt> is empty, it will use defaults for
     * {@link #PROP_JSETTLERS_PORT} and {@link #PROP_JSETTLERS_CONNECTIONS}}.
     *<P>
     * Sets {@link #hasStartupPrintAndExit} if appropriate.
     *
     * @param args args as passed to main
     * @return Properties collection of args, or null for argument error.
     *     Will contain at least {@link #PROP_JSETTLERS_PORT},
     *     {@link #PROP_JSETTLERS_CONNECTIONS},
     *     {@link SOCDBHelper#PROP_JSETTLERS_DB_USER},
     *     {@link SOCDBHelper#PROP_JSETTLERS_DB_PASS}.
     * @since 1.1.07
     */
    public static Properties parseCmdline_DashedArgs(String[] args)
    {
        Properties argp = new Properties();

        int aidx = 0;
        while ((aidx < args.length) && (args[aidx].startsWith("-")))
        {
            String arg = args[aidx];

            if (arg.equals("-V") || arg.equalsIgnoreCase("--version"))
            {
                printVersionText();
                hasStartupPrintAndExit = true;
            }
            else if (arg.equalsIgnoreCase("-h") || arg.equals("?") || arg.equalsIgnoreCase("--help"))
            {
                printUsage(true);
                hasStartupPrintAndExit = true;
            }
            else if (arg.startsWith("-o") || arg.equalsIgnoreCase("--option"))
            {
                hasSetGameOptions = true;
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
                    if (! parseCmdline_GameOption(argValue))
                        argValue = null;
                }
                if (argValue == null)
                {
                    System.err.println("Missing required option name/value after " + arg);
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
                argp.setProperty(name, value);

            } else {
                System.err.println("Unknown argument: " + arg);
            }
            ++aidx;
        }

        // Done parsing flagged parameters.
        // Look for the positional ones.
        if ((args.length - aidx) == 0)
        {
            // No positional parameters: Take defaults.
            argp.setProperty(PROP_JSETTLERS_PORT, Integer.toString(SOC_PORT_DEFAULT));
            argp.setProperty(PROP_JSETTLERS_CONNECTIONS, Integer.toString(SOC_MAXCONN_DEFAULT));
            argp.setProperty(SOCDBHelper.PROP_JSETTLERS_DB_USER, "socuser");
            argp.setProperty(SOCDBHelper.PROP_JSETTLERS_DB_PASS, "socpass");
        } else {
            // Require all 4 parameters
            if ((args.length - aidx) < 4)
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
            argp.setProperty(SOCDBHelper.PROP_JSETTLERS_DB_USER, args[aidx]);  ++aidx;
            argp.setProperty(SOCDBHelper.PROP_JSETTLERS_DB_PASS, args[aidx]);  ++aidx;
        }

        if (aidx < args.length)
        {
            if (! printedUsageAlready)
            {
                if (args[aidx].startsWith("-"))
                {
                    System.err.println("SOCServer: Options must appear before, not after, the port number.");
                } else {
                    System.err.println("SOCServer: Options must appear before the port number, not after dbpass.");
                }
                printUsage(false);
            }
            return null;
        }

        // Done parsing.
        return argp;
    }

    /**
     * Track whether we've already called {@link #printUsage(boolean)}.
     * @since 1.1.07
     */
    public static boolean printedUsageAlready = false;

    /**
     * Print command line parameter information, including options ("--" / "-").
     * @param longFormat short or long?
     * Long format gives details and also calls {@link #printVersionText()} beforehand.
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
            printVersionText();
        }
        System.err.println("usage: java soc.server.SOCServer [option...] port_number max_connections dbUser dbPass");
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
        Hashtable<String, SOCGameOption> allopts = SOCGameOption.getAllKnownOptions();
        System.err.println("-- Current default game options: --");
        for (Enumeration<String> e = allopts.keys(); e.hasMoreElements(); )
        {
            String okey = e.nextElement();
            SOCGameOption opt = allopts.get(okey);
            boolean quotes = (opt.optType == SOCGameOption.OTYPE_STR) || (opt.optType == SOCGameOption.OTYPE_STRHIDE);
            // OTYPE_* - consider any type-specific output in this method.

            StringBuffer sb = new StringBuffer("  ");
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
            sb.append(opt.optDesc);
            System.err.println(sb.toString());
            if (opt.enumVals != null)  // possible values of OTYPE_ENUM
            {
                sb = new StringBuffer("    option choices (1-n): ");
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

        int optsVers = SOCGameOption.optionsMinimumVersion(allopts);
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
     * Starting the server from the command line
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
        Properties argp = parseCmdline_DashedArgs(args);
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
                server.setPriority(5);
                server.start();  // <---- Start the Main SOCServer Thread ----

                // Most threads are started in the SOCServer constructor, via initSocServer.
                // Messages from clients are handled in processCommand's loop.
            }
            catch (SocketException e)
            {
                // network setup problem
                System.err.println(e.getMessage());  // "* Exiting due to network setup problem: ..."
                System.exit (1);
            }
            catch (EOFException e)
            {
                // the sql setup script was ran successfully by initialize;
                // exit server, user will re-run without the setup script param.
                System.err.println("\nDB setup script was successful. Exiting now.\n");
                System.exit(2);
            }
            catch (SQLException e)
            {
                // the sql setup script was ran by initialize, but failed to complete.
                // exception detail was printed in initSocServer.
                System.err.println("\n* DB setup script failed. Exiting now.\n");
                System.exit(1);
            }
        }
        catch (Throwable e)
        {
            printUsage(false);
            return;
        }

    }  // main

    /**
     * Each local robot gets its own thread.
     * Equivalent to main thread in SOCRobotClient in network games.
     *<P>
     * Before 1.1.09, this class was part of SOCPlayerClient.
     * @see SOCServer#setupLocalRobots(int, int, int)
     * @since 1.1.00
     */
    private static class SOCPlayerLocalRobotRunner implements Runnable
    {
        /**
         * All the started {@link SOCRobotClient}s. Key is the bot nickname.
         *<P>
         *<b>Note:</b> If a bot is disconnected from the server, it's not
         * removed from this list, because the same bot will try to reconnect.
         * To see if a bot is connected, check {@link SOCServer#robots} instead.
         * @since 1.1.13
         */
        public static Hashtable<String, SOCRobotClient> robotClients = new Hashtable<String, SOCRobotClient>();

        SOCRobotClient rob;

        protected SOCPlayerLocalRobotRunner(SOCRobotClient rc)
        {
            rob = rc;
        }

        public void run()
        {
            final String rname = rob.getNickname();
            Thread.currentThread().setName("robotrunner-" + rname);
            robotClients.put(rname, rob);
            rob.init();
        }

        /**
         * Create and start a robot client within a {@link SOCPlayerLocalRobotRunner} thread.
         * After creating it, {@link Thread#yield() yield} the current thread and then sleep
         * 75 milliseconds, to give the robot time to start itself up.
         * The SOCPlayerLocalRobotRunner's run() will add the {@link SOCRobotClient} to {@link #robotClients}.
         * @param rname  Name of robot
         * @param strSocketName  Server's stringport socket name, or null
         * @param port    Server's tcp port, if <tt>strSocketName</tt> is null
         * @since 1.1.09
         * @see SOCServer#setupLocalRobots(int, int)
         * @throws ClassNotFoundException  if a robot class, or SOCDisplaylessClient,
         *           can't be loaded. This can happen due to packaging of the server-only JAR.
         * @throws LinkageError  for same reason as ClassNotFoundException
         */
        public static void createAndStartRobotClientThread
            (final String rname, final String strSocketName, final int port)
            throws ClassNotFoundException, LinkageError
        {
            SOCRobotClient rcli;
            if (strSocketName != null)
                rcli = new SOCRobotClient(strSocketName, rname, "pw");
            else
                rcli = new SOCRobotClient("localhost", port, rname, "pw");
            Thread rth = new Thread(new SOCPlayerLocalRobotRunner(rcli));
            rth.setDaemon(true);
            rth.start();  // run() will add to robotClients

            Thread.yield();
            try
            {
                Thread.sleep(75);  // Let that robot go for a bit.
                    // robot runner thread will call its init()
            }
            catch (InterruptedException ie) {}
        }

    }  // nested static class SOCPlayerLocalRobotRunner

    /**
     * Force-end this robot's turn.
     * Done in a separate thread in case of deadlocks.
     * Created from {@link SOCGameTimeoutChecker#run()}.
     * @author Jeremy D Monin
     * @since 1.1.11
     */
    private class ForceEndTurnThread extends Thread
    {
        private SOCGame ga;
        private SOCPlayer pl;

        public ForceEndTurnThread(SOCGame g, SOCPlayer p)
        {
            setDaemon(true);
            ga = g;
            pl = p;
        }

        /** If our targeted robot player is still the current player, force-end their turn. */
        @Override
        public void run()
        {
            final String rname = pl.getName();
            final int plNum = pl.getPlayerNumber();
            if (ga.getCurrentPlayerNumber() != plNum)
                return;

            StringConnection rconn = getConnection(rname);
            System.err.println("For robot " + rname + ": force end turn in game " + ga.getName() + " cpn=" + plNum + " state " + ga.getGameState());
            if (ga.getGameState() == SOCGame.WAITING_FOR_DISCARDS)
                System.err.println("  srv card count = " + pl.getResources().getTotal());
            if (rconn == null)
            {
                System.err.println("L9120: internal error: can't find connection for " + rname);
                return;  // shouldn't happen
            }

            // if it's the built-in type, print brain variables
            SOCClientData scd = (SOCClientData) rconn.getAppData();
            if (scd.isBuiltInRobot)
            {
                SOCRobotClient rcli = SOCPlayerLocalRobotRunner.robotClients.get(rname);
                if (rcli != null)
                    rcli.debugPrintBrainStatus(ga.getName());
                else
                    System.err.println("L9397: internal error: can't find robotClient for " + rname);
            } else {
                System.err.println("  Can't print brain status; robot type is " + scd.robot3rdPartyBrainClass);
            }

            endGameTurnOrForce(ga, plNum, rname, rconn, false);
        }

    }  // inner class ForceEndTurnThread

}  // public class SOCServer
