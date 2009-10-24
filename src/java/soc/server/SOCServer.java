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
package soc.server;

import soc.debug.D;  // JM

import soc.game.*;
import soc.message.*;

import soc.server.database.SOCDBHelper;

import soc.server.genericServer.LocalStringConnection;
import soc.server.genericServer.Server;
import soc.server.genericServer.StringConnection;

import soc.util.IntPair;
import soc.util.SOCGameBoardReset;
import soc.util.SOCRobotParameters;
import soc.util.Version;

import java.sql.SQLException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
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
 *<b>Network traffic:</b>
 * The first message over the connection is the client's version
 * and the second is the server's response:
 * Either {@link SOCRejectConnection}, or the lists of
 * channels and games ({@link SOCChannels}, {@link SOCGames}).
 * See {@link SOCMessage} for details of the client/server protocol.
 * See {@link Server} for details of the server threading and processing.
 *<P>
 * The server supports several <b>debug commands</b> when enabled, and
 * when sent as chat messages by a user named "debug".
 * (Or by the user in a practice game.)
 * See {@link #processDebugCommand(StringConnection, String, String)}
 * for details.
 *<P>
 * The version check timer is set in {@link SOCClientData#setVersionTimer(SOCServer, StringConnection)}.
 * Before 1.1.06, the server's response was first message,
 * and client version was then sent in reply to server's version.
 */
public class SOCServer extends Server
{
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
     * Must be at least twice the sleep-time in SOCGameTimeoutChecker.run().
     * The game expiry time is set at game creation in SOCGameListAtServer.CreateGame.
     *
     * @see #checkForExpiredGames()
     * @see SOCGameTimeoutChecker#run()
     * @see SOCGameListAtServer#createGame(String)
     */
    public static int GAME_EXPIRE_WARN_MINUTES = 10;

    /**
     * Maximum permitted game name length, default 20 characters.
     *
     * @see #createOrJoinGameIfUserOK(StringConnection, String, String, String, Hashtable)
     * @since 1.1.07
     */
    public static int GAME_NAME_MAX_LENGTH = 20;

    /**
     * Maximum permitted player name length, default 20 characters.
     * The client already truncates to 20 characters in SOCPlayerClient.getValidNickname.
     *
     * @see #createOrJoinGameIfUserOK(StringConnection, String, String, String, Hashtable)
     * @since 1.1.07
     */
    public static int PLAYER_NAME_MAX_LENGTH = 20;

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
     * The TCP port we listen on.
     */
    public int port;

    /**
     * Maximum number of connections allowed
     */
    protected int maxConnections;

    /**
     * A list of robots connected to this server
     */
    protected Vector robots = new Vector();

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
     * Did the command line include --option / -o to set {@link SOCGameOption game option} values?
     * Checked in constructors for possible stderr option-values printout.
     * @since 1.1.07
     */
    public static boolean hasSetGameOptions = false;

    /** Status Message to send, nickname already logged into the system */
    public static final String MSG_NICKNAME_ALREADY_IN_USE
        = "Someone with that nickname is already logged into the system.";

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
    protected Hashtable robotJoinRequests = new Hashtable();

    /**
     * table of requestst for robots to leave games
     */
    protected Hashtable robotDismissRequests = new Hashtable();

    /**
     * table of game data files
     */
    protected Hashtable gameDataFiles = new Hashtable();

    /**
     * the current game event record
     */

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
     * Create a Settlers of Catan server listening on port p.
     * You must start its thread yourself.
     *<P>
     * In 1.1.07 and later, will also print game options to stderr if
     * any option defaults require a minimum client version, or if 
     * {@link #hasSetGameOptions} is set.
     *
     * @param p    the port that the server listens on
     * @param mc   the maximum number of connections allowed
     * @param databaseUserName  the user name for accessing the database
     * @param databasePassword  the password for the user
     */
    public SOCServer(int p, int mc, String databaseUserName, String databasePassword)
    {
        super(p);
        port = p;
        maxConnections = mc;
        initSocServer(databaseUserName, databasePassword);
    }

    /**
     * Create a Settlers of Catan server listening on local stringport s.
     * You must start its thread yourself.
     *<P>
     * In 1.1.07 and later, will also print game options to stderr if
     * any option defaults require a minimum client version, or if 
     * {@link #hasSetGameOptions} is set.
     *
     * @param s    the stringport that the server listens on
     * @param mc   the maximum number of connections allowed
     * @param databaseUserName  the user name for accessing the database
     * @param databasePassword  the password for the user
     */
    public SOCServer(String s, int mc, String databaseUserName, String databasePassword)
    {
        super(s);
        maxConnections = mc;
        initSocServer(databaseUserName, databasePassword);
    }    

    /**
     * Common init for both constructors.
     *
     * @param databaseUserName Used for DB connect - not retained
     * @param databasePassword Used for DB connect - not retained
     */
    private void initSocServer(String databaseUserName, String databasePassword)
    {
        printVersionText();
        
        /* Check for problems during super setup (such as port already in use) */
        if (error != null)
        {
            System.err.println("* Exiting due to network setup problem: " + error.toString());
            System.exit (1);
        }

        try
        {
            SOCDBHelper.initialize(databaseUserName, databasePassword);
            System.err.println("User database initialized.");
        }
        catch (SQLException x) // just a warning
        {
            System.err.println("No user database available: " +
                               x.getMessage());
            System.err.println("Users will not be authenticated.");
        }

        startTime = System.currentTimeMillis();
        numberOfGamesStarted = 0;
        numberOfGamesFinished = 0;
        numberOfUsers = 0;
        serverRobotPinger = new SOCServerRobotPinger(robots);
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
        if (c != null)
        {
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
        D.ebugPrintln("leaveChannel: " + c.getData() + " " + ch + " " + channelListLock);

        boolean result = false;

        if (c != null)
        {
            if (channelList.isMember(c, ch))
            {
                channelList.removeMember(c, ch);

                SOCLeave leaveMessage = new SOCLeave((String) c.getData(), c.host(), ch);
                messageToChannelWithMon(ch, leaveMessage);
                D.ebugPrintln("*** " + (String) c.getData() + " left the channel " + ch);
            }

            if (channelList.isChannelEmpty(ch))
            {
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

                result = true;
            }
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
     *                {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable)}.
     *
     * @return     true if c was not a member of ch before,
     *             false if c was already in this game
     *
     * @throws SOCGameOptionVersionException if asking to create a game (gaOpts != null),
     *           but client's version is too low to join because of a
     *           requested game option's minimum version in gaOpts. 
     *           Calculated via {@link SOCGameOption#optionsNewerThanVersion(int, boolean, Hashtable)}.
     *           (this exception was added in 1.1.07)
     * @throws IllegalArgumentException if client's version is too low to join for any
     *           other reason. (this exception was added in 1.1.06)
     * @see #handleSTARTGAME(StringConnection, SOCStartGame)
     */
    public boolean connectToGame(StringConnection c, final String gaName, Hashtable gaOpts)
        throws SOCGameOptionVersionException, IllegalArgumentException
    {
        boolean result = false;

        if (c != null)
        {
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
                        Vector optsValuesTooNew =
                            SOCGameOption.optionsNewerThanVersion(cliVers, true, gaOpts);
                        throw new SOCGameOptionVersionException(gVers, cliVers, optsValuesTooNew);

                        // <---- Exception: Early return ----
                    }
                }

                gameList.takeMonitor();
                boolean monitorReleased = false;

                try
                {
                    // Create new game, expiring in SOCGameListAtServer.GAME_EXPIRE_MINUTES .
                    gameList.createGame(gaName, gaOpts); 

                    // Add this (creating) player to the game
                    gameList.addMember(c, gaName);

                    // must release monitor before we broadcast
                    gameList.releaseMonitor();
                    monitorReleased = true;
                    result = true;

                    // check version before we broadcast
                    final int cversMin = getMinConnectedCliVersion();

                    if ((gVers <= cversMin) && (gaOpts == null))
                    {
			// All clients can join it, and no game options: use simplest message
                        broadcast(SOCNewGame.toCmd(gaName));

                    } else {
                        // Send messages, based on clients' version
			// and whether there are game options.

			if (cversMin >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
			{
			    // All cli can understand msg with version/options included
			    broadcast
				(SOCNewGameWithOptions.toCmd(gaName, gaOpts, gVers));
			} else {
			    // Only some can understand msg with version/options included;
			    // send at most 1 to each connected client, split by client version.

			    final int cversMax = getMaxConnectedCliVersion();
			    int newgameMaxCliVers;
			    if ((gaOpts != null) && (cversMax >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS))
			    {
				broadcastToVers
				    (SOCNewGameWithOptions.toCmd(gaName, gaOpts, gVers),
				     SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS, Integer.MAX_VALUE);
				newgameMaxCliVers = SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS - 1;
			    } else {
				newgameMaxCliVers = Integer.MAX_VALUE;
			    }

			    // To older clients who can join, announce game without its options/version
			    broadcastToVers(SOCNewGame.toCmd(gaName), gVers, newgameMaxCliVers);

			    // To older clients who can't join, announce game with cant-join prefix
			    StringBuffer sb = new StringBuffer();
			    sb.append(SOCGames.MARKER_THIS_GAME_UNJOINABLE);
			    sb.append(gaName);
			    broadcastToVers
				(SOCNewGame.toCmd(sb.toString()), SOCGames.VERSION_FOR_UNJOINABLE, gVers-1);
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
        else
        {
            return false;
        }
    }

    /**
     * the connection c leaves the game gm.  Clean up; if needed, call {@link #forceEndGameTurn(SOCGame, String)}.
     *<P>
     * WARNING: MUST HAVE THE gameList.takeMonitorForGame(gm) before
     * calling this method
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
        boolean gameDestroyed = false;

        if (c != null)
        {
            gameList.removeMember(c, gm);

            boolean isPlayer = false;
            int playerNumber = 0;    // removing this player number
            SOCGame cg = gameList.getGameData(gm);

            boolean gameHasHumanPlayer = false;
            boolean gameHasObserver = false;
            boolean gameVotingActiveDuringStart = false;

            if (cg != null)
            {
                final String plName = (String) c.getData();  // Retain name, since will become null within game obj.

                for (playerNumber = 0; playerNumber < SOCGame.MAXPLAYERS;
                        playerNumber++)
                {
                    SOCPlayer player = cg.getPlayer(playerNumber);

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
                        if (cg.getResetVoteActive())
                        {
                            if (cg.getGameState() <= SOCGame.START2B)
                                gameVotingActiveDuringStart = true;

                            if (cg.getResetPlayerVote(playerNumber) == SOCGame.VOTE_NONE)
                            {
                                gameList.releaseMonitorForGame(gm);
                                cg.takeMonitor();
                                resetBoardVoteNotifyOne(cg, playerNumber, plName, false);                
                                cg.releaseMonitor();
                                gameList.takeMonitorForGame(gm);
                            }
                        }

                        /** 
                         * Remove the player.
                         */
                        cg.removePlayer(plName);  // player obj name becomes null

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
                for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++)
                {
                    if (cg != null)
                    {
                        SOCPlayer player = cg.getPlayer(pn);

                        if ((player != null) && (player.getName() != null) && (!cg.isSeatVacant(pn)) && (!player.isRobot()))
                        {
                            gameHasHumanPlayer = true;

                            break;
                        }
                    }
                }

                //D.ebugPrintln("*** gameHasHumanPlayer = "+gameHasHumanPlayer+" for "+gm);

                /**
                 * check if there is at least one person watching the game
                 */
                if ((cg != null) && !gameHasHumanPlayer && !gameList.isGameEmpty(gm))
                {
                    Enumeration membersEnum = gameList.getMembers(gm).elements();

                    while (membersEnum.hasMoreElements())
                    {
                        StringConnection member = (StringConnection) membersEnum.nextElement();

                        //D.ebugPrintln("*** "+member.data+" is a member of "+gm);
                        boolean nameMatch = false;

                        for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++)
                        {
                            SOCPlayer player = cg.getPlayer(pn);

                            if ((player != null) && (player.getName() != null) && (player.getName().equals((String) member.getData())))
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
                 * it wasn't a robot, and the game isn't over, then...
                 */
                if (isPlayer && (gameHasHumanPlayer || gameHasObserver) && (cg != null) && (!cg.getPlayer(playerNumber).isRobot()) && (cg.getGameState() < SOCGame.OVER) && !(cg.getGameState() < SOCGame.START1A))
                {
                    /**
                     * get a robot to replace this player;
                     * just in case, check game-version vs robots-version,
                     * like at new-game (readyGameAskRobotsJoin).
                     */
                    boolean foundNoRobots = false;

                    messageToGameWithMon(gm, new SOCGameTextMsg(gm, SERVERNAME, "Fetching a robot player..."));

                    if (robots.isEmpty())
                    {
                        messageToGameWithMon(gm, new SOCGameTextMsg(gm, SERVERNAME, "Sorry, no robots on this server."));
                        foundNoRobots = true;
                    }
                    else if (cg.getClientVersionMinRequired() > Version.versionNumber())
                    {
                        messageToGameWithMon(gm, new SOCGameTextMsg
                                (gm, SERVERNAME,
                                 "Sorry, the robots can't join this game; its version is somehow newer than server and robots, it's "
                                 + cg.getClientVersionMinRequired()));
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

                        Vector requests = (Vector) robotJoinRequests.get(gm);

                        for (int idx = 0; idx < robots.size(); idx++)
                        {
                            robotConn = (StringConnection) robots.get(robotIndexes[idx]);
                            nameMatch = false;

                            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                            {
                                if (cg != null)
                                {
                                    SOCPlayer pl = cg.getPlayer(i);

                                    if (pl != null)
                                    {
                                        String pname = pl.getName();

                                        // D.ebugPrintln("CHECKING " + (String) robotConn.getData() + " == " + pname);

                                        if ((pname != null) && (pname.equals((String) robotConn.getData())))
                                        {
                                            nameMatch = true;

                                            break;
                                        }
                                    }
                                }
                            }

                            if ((!nameMatch) && (requests != null))
                            {
                                Enumeration requestsEnum = requests.elements();

                                while (requestsEnum.hasMoreElements())
                                {
                                    StringConnection tempCon = (StringConnection) requestsEnum.nextElement();

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

                        if (!nameMatch && (cg != null))
                        {
                            /**
                             * make the request
                             */
                            D.ebugPrintln("@@@ JOIN GAME REQUEST for " + (String) robotConn.getData());

                            robotConn.put(SOCJoinGameRequest.toCmd(gm, playerNumber, cg.getGameOptions()));

                            /**
                             * record the request
                             */
                            if (requests == null)
                            {
                                requests = new Vector();
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

                    /**
                     * What to do if no robot was found to fill their spot?
                     */
                    if (foundNoRobots)
                    {
                        final int cpn = cg.getCurrentPlayerNumber();

                        if (playerNumber == cpn)
                        {
                            /**
                             * Rare condition:
                             * No robot was found, but it was this player's turn.
                             * End their turn just to keep the game limping along.
                             * To prevent deadlock, we must release gamelist's monitor for
                             * this game before calling endGameTurn.
                             */
                            if (cg.canEndTurn(playerNumber))
                            {
                                gameList.releaseMonitorForGame(gm);
                                cg.takeMonitor();
                                endGameTurn(cg);
                                cg.releaseMonitor();
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
                                cg.takeMonitor();
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
                                    cg.resetVoteClear();
                                }

                                /**
                                 * Force turn to end
                                 */
                                forceEndGameTurn(cg, plName);
                                cg.releaseMonitor();
                                gameList.takeMonitorForGame(gm);
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
                            if ((cg.getGameState() == SOCGame.WAITING_FOR_DISCARDS)
                                 && (cg.getPlayer(playerNumber).getNeedToDiscard()))
                            {
                                /**
                                 * For discard, tell the discarding player's client that they discarded the resources,
                                 * tell everyone else that the player discarded unknown resources.
                                 */
                                gameList.releaseMonitorForGame(gm);
                                cg.takeMonitor();
                                forceGamePlayerDiscard(cg, cpn, c, plName, playerNumber);
                                sendGameState(cg, false);  // WAITING_FOR_DISCARDS or MOVING_ROBBER
                                cg.releaseMonitor();
                                gameList.takeMonitorForGame(gm);
                            }
                        }  // current player?
                    }
                }
            }

            /**
             * if the game has no players, or if they're all
             * robots, then end the game and write the data
             * to disk.
             */
            boolean emptyGame = false;
            emptyGame = gameList.isGameEmpty(gm);

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
        }

        //D.ebugPrintln("*** gameDestroyed = "+gameDestroyed+" for "+gm);
        return gameDestroyed;
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
     * Force this player (not current player) to discard, and report resources to all players.
     * Does not send gameState, which may have changed; see {@link SOCGame#discardPickRandom(SOCResourceSet, int, SOCResourceSet, Random)}.
     *<P>
     * Assumes, as {@link #endGameTurn(SOCGame)} does:
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
        SOCResourceSet discard = cg.playerDiscardRandom(pn);
        final String gaName = cg.getName();
        if ((c != null) && c.isConnected())
            reportRsrcGainLoss(gaName, discard, true, cpn, -1, null, c);
        int totalRes = discard.getTotal();
        messageToGameExcept(gaName, c, new SOCPlayerElement(gaName, cpn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, totalRes), true);
        messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, plName + " discarded " + totalRes + " resources."));
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

        if (cg != null)
        {
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

            //storeGameScores(cg);
            ///
            /// tell all robots to leave
            ///
            Vector members = null;
            members = gameList.getMembers(gm);

            if (members != null)
            {
                Enumeration conEnum = members.elements();

                while (conEnum.hasMoreElements())
                {
                    StringConnection con = (StringConnection) conEnum.nextElement();
                    con.put(SOCRobotDismiss.toCmd(gm));
                }
            }

            gameList.deleteGame(gm);
        }
    }

    /**
     * Used when SOCPlayerClient is also hosting games.
     * @return The names (Strings) of games on this server
     */
    public Enumeration getGameNames()
    {
        return gameList.getGames();
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
     * @return the game options (hashtable of {@link SOCGameOption}), or null if none or if game doesn't exist
     * @since 1.1.07
     */
    public Hashtable getGameOptions(String gm)
    {
        return gameList.getGameOptions(gm);
    }

    /**
     * the connection c leaves all channels it was in
     *
     * @param c  the connection
     * @return   the channels it was in
     */
    public Vector leaveAllChannels(StringConnection c)
    {
        if (c != null)
        {
            Vector ret = new Vector();
            Vector destroyed = new Vector();

            channelList.takeMonitor();

            try
            {
                for (Enumeration k = channelList.getChannels();
                        k.hasMoreElements();)
                {
                    String ch = (String) k.nextElement();

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
            for (Enumeration de = destroyed.elements(); de.hasMoreElements();)
            {
                String ga = (String) de.nextElement();
                broadcast(SOCDeleteChannel.toCmd(ga));
            }

            return ret;
        }
        else
        {
            return null;
        }
    }

    /**
     * the connection c leaves all games it was in
     *
     * @param c  the connection
     * @return   the games it was in
     */
    public Vector leaveAllGames(StringConnection c)
    {
        if (c != null)
        {
            Vector ret = new Vector();
            Vector destroyed = new Vector();

            gameList.takeMonitor();

            try
            {
                for (Enumeration k = gameList.getGames(); k.hasMoreElements();)
                {
                    String ga = (String) k.nextElement();
                    Vector v = (Vector) gameList.getMembers(ga);

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
            for (Enumeration de = destroyed.elements(); de.hasMoreElements();)
            {
                String ga = (String) de.nextElement();
                D.ebugPrintln("** Broadcasting SOCDeleteGame " + ga);
                broadcast(SOCDeleteGame.toCmd(ga));
            }

            return ret;
        }
        else
        {
            return null;
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
        channelList.takeMonitorForChannel(ch);

        try
        {
            Vector v = channelList.getMembers(ch);

            if (v != null)
            {
                Enumeration menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection c = (StringConnection) menum.nextElement();

                    if (c != null)
                    {
                        c.put(mes.toCmd());
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
        Vector v = channelList.getMembers(ch);

        if (v != null)
        {
            Enumeration menum = v.elements();

            while (menum.hasMoreElements())
            {
                StringConnection c = (StringConnection) menum.nextElement();

                if (c != null)
                {
                    c.put(mes.toCmd());
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
        if ((c != null) && (mes != null))
        {
            //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", c.getData()));
            c.put(mes.toCmd());
        }
    }

    /**
     * Send a message to the given game
     *
     * @param ga  the name of the game
     * @param mes the message to send. If mes is a SOCGameTextMsg whose
     *            text begins with ">>>", the client should consider this
     *            an urgent message, and draw the user's attention in some way.
     *            (See {@link #messageToGameUrgent(String, String)})
     */
    public void messageToGame(String ga, SOCMessage mes)
    {
        gameList.takeMonitorForGame(ga);

        try
        {
            Vector v = gameList.getMembers(ga);

            if (v != null)
            {
                //D.ebugPrintln("M2G - "+mes);
                Enumeration menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection c = (StringConnection) menum.nextElement();

                    if (c != null)
                    {
                        //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", c.getData()));
                        c.put(mes.toCmd());
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
     * Send a message to the given game
     *
     * WARNING: MUST HAVE THE gameList.takeMonitorForGame(ga) before
     * calling this method
     *
     * @param ga  the name of the game
     * @param mes the message to send
     */
    public void messageToGameWithMon(String ga, SOCMessage mes)
    {
        Vector v = gameList.getMembers(ga);

        if (v != null)
        {
            //D.ebugPrintln("M2G - "+mes);
            Enumeration menum = v.elements();

            while (menum.hasMoreElements())
            {
                StringConnection c = (StringConnection) menum.nextElement();

                if (c != null)
                {
                    //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", c.getData()));
                    c.put(mes.toCmd());
                }
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
     */
    public void messageToGameExcept(String gn, Vector ex, SOCMessage mes, boolean takeMon)
    {
        if (takeMon)
            gameList.takeMonitorForGame(gn);

        try
        {
            Vector v = gameList.getMembers(gn);

            if (v != null)
            {
                //D.ebugPrintln("M2GE - "+mes);
                Enumeration menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection con = (StringConnection) menum.nextElement();

                    if ((con != null) && (!ex.contains(con)))
                    {
                        //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", con.getData()));
                        con.put(mes.toCmd());
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
     */
    public void messageToGameExcept(String gn, StringConnection ex, SOCMessage mes, boolean takeMon)
    {
        if (takeMon)
            gameList.takeMonitorForGame(gn);

        try
        {
            Vector v = gameList.getMembers(gn);

            if (v != null)
            {
                //D.ebugPrintln("M2GE - "+mes);
                Enumeration menum = v.elements();

                while (menum.hasMoreElements())
                {
                    StringConnection con = (StringConnection) menum.nextElement();
                    if ((con != null) && (con != ex))
                    {
                        //currentGameEventRecord.addMessageOut(new SOCMessageRecord(mes, "SERVER", con.getData()));
                        con.put(mes.toCmd());
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
     * Send an urgent SOCGameTextMsg to the given game.
     * An "urgent" message is a SOCGameTextMsg whose text
     * begins with ">>>"; the client should draw the user's
     * attention in some way.
     *<P>
     * Like messageToGame, will take and release the game's monitor.
     *
     * @param ga  the name of the game
     * @param mes the message to send. If mes does not begin with ">>>",
     *            will prepend ">>> " before sending mes.
     */
    public void messageToGameUrgent(String ga, String mes)
    {
        if (! mes.startsWith(">>>"))
            mes = ">>> " + mes;
        messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, mes));
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
    public void leaveConnection(StringConnection c)
    {
        if (c != null)
        {
            leaveAllChannels(c);
            leaveAllGames(c);

            /**
             * if it is a robot, remove it from the list
             */
            robots.removeElement(c);
        }
    }

    /**
     * Things to do when a new connection comes.
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
     * @see #nameConnection(StringConnection)
     */
    public boolean newConnection1(StringConnection c)
    {
        if (c != null)
        {
            /**
             * see if we are under the connection limit
             */
            try
            {
                if (this.connectionCount() >= maxConnections)
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
    protected void newConnection2(StringConnection c)
    {
        SOCClientData cdata = new SOCClientData();
        c.setAppData(cdata);

        // VERSION of server
        c.put(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum()));

        // CHANNELS
        Vector cl = new Vector();
        channelList.takeMonitor();

        try
        {
            Enumeration clEnum = channelList.getChannels();

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
     * Send the entire list of games to this client; this is sent once per connecting client.
     * Or, send the set of changed games, if the client's guessed version was wrong.
     * Depending on client's version, the message sent will be either
     * {@link SOCGames GAMES} or {@link SOCGamesWithOptions GAMESWITHOPTIONS}.
     * The set of changed games is sent as matching pairs of {@link SOCDeleteGame DELETEGAME}
     * and either {@link SOCNewGame NEWGAME} or {@link SOCNewGameWithOptions NEWGAMEWITHOPTIONS}.
     *<P>
     * Two possible scenarios for when this method is called:
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
     * 1.1.06 ({@link SOCGames#VERSION_FOR_UNJOINABLE}).
     *<P>
     * <b>Locks:</b> Will call {@link SOCGameListAtServer#takeMonitor()} / releaseMonitor
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

        Vector gl = new Vector();  // contains Strings and/or SOCGames;
                                   // strings are names of unjoinable games,
                                   // with the UNJOINABLE prefix.
        gameList.takeMonitor();
        final boolean alreadySent =
            ((SOCClientData) c.getAppData()).hasSentGameList();  // Check while gamelist monitor is held
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
        Enumeration gaEnum = gameList.getGamesData();
        gameList.releaseMonitor();

        if (cliVersionChange && cliCouldKnow)
        {
            // If they already have the names of games they can't join,
            // no need to re-send those names.
            cliCanKnow = false;
        }

        try
        {
            SOCGame g;

            // Build the list of game names.  This loop is used for the
            // initial list, or for sending just the delta after the version fix.

            while (gaEnum.hasMoreElements())
            {
                g = (SOCGame) gaEnum.nextElement();
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
                    c.put(SOCGamesWithOptions.toCmd(gl));
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
                        c.put(SOCNewGameWithOptions.toCmd((SOCGame) ob));
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
     * check if a name is ok
     * a name is ok if it hasn't been used yet, isn't {@link #SERVERNAME the server's name},
     * and (since 1.1.07) passes {@link SOCMessage#isSingleLineAndSafe(String)}.
     *
     * @param n  the name
     * @return   true if the name is ok
     */
    private boolean checkNickname(String n)
    {
        if (getConnection(n) != null)  // check conns hashtable
        {
            return false;
        }

        if (n.equals(SERVERNAME))
        {
            return false;
        }

        if (! SOCMessage.isSingleLineAndSafe(n))
        {
            return false;
        }
        return true;
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
    public void processCommand(String s, StringConnection c)
    {
        try
        {
            SOCMessage mes = (SOCMessage) SOCMessage.toMsg(s);

            // D.ebugPrintln(c.getData()+" - "+mes);
            if (mes != null)
            {
                switch (mes.getType())
                {

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

                    if (c.getData().equals("debug"))
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
                 * a player is moving the robber
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

		case SOCMessage.GAMEOPTIONGETDEFAULTS:
		    handleGAMEOPTIONGETDEFAULTS(c, (SOCGameOptionGetDefaults) mes);
		    break;

		case SOCMessage.GAMEOPTIONGETINFOS:
		    handleGAMEOPTIONGETINFOS(c, (SOCGameOptionGetInfos) mes);
		    break;

		case SOCMessage.NEWGAMEWITHOPTIONSREQUEST:
		    handleNEWGAMEWITHOPTIONSREQUEST(c, (SOCNewGameWithOptionsRequest) mes);
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
        "*RESETBOT* botname  End a bot's connection",
        "*STATS*   server stats and current-game stats",
        "*STOP*  kill the server",
        "--- Debug Resources ---",
        "rsrcs: #cl #or #sh #wh #wo playername",
        "Example  rsrcs: 0 3 0 2 0 Myname",
        "dev: #typ playername",
        "Example  dev: 2 Myname",
        "Development card types are:",  // see SOCDevCardConstants
        "0 robber",
        "1 road-building",
        "2 year of plenty",
        "3 monopoly",
        "4 governors house",
        "5 market",
        "6 university",
        "7 temple",
        "8 chapel"
        };

    /**
     * Process a debug command, sent by the "debug" client/player.
     * See {@link #DEBUG_COMMANDS_HELP} for list of commands.
     */
    public void processDebugCommand(StringConnection debugCli, String ga, String dcmd)
    {
        if (dcmd.startsWith("*HELP*") || dcmd.startsWith("*help"))
        {
            for (int i = 0; i < DEBUG_COMMANDS_HELP.length; ++i)
                messageToPlayer(debugCli, new SOCGameTextMsg(ga, SERVERNAME, DEBUG_COMMANDS_HELP[i]));
            return;
        }

        if (dcmd.startsWith("*KILLGAME*"))
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
        else if (dcmd.startsWith("*STATS*"))
        {
            long diff = System.currentTimeMillis() - startTime;
            long hours = diff / (60 * 60 * 1000);
            long minutes = (diff - (hours * 60 * 60 * 1000)) / (60 * 1000);
            long seconds = (diff - (hours * 60 * 60 * 1000) - (minutes * 60 * 1000)) / 1000;
            Runtime rt = Runtime.getRuntime();
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Uptime: " + hours + ":" + minutes + ":" + seconds));
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Total connections: " + numberOfConnections));
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Current connections: " + connectionCount()));
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Total Users: " + numberOfUsers));
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Games started: " + numberOfGamesStarted));
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Games finished: " + numberOfGamesFinished));
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Total Memory: " + rt.totalMemory()));
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Free Memory: " + rt.freeMemory()));
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Version: "
                + Version.versionNumber() + " (" + Version.version() + ") build " + Version.buildnum()));

            processDebugCommand_checktime(debugCli, ga, gameList.getGameData(ga));
        }
        else if (dcmd.startsWith("*GC*"))
        {
            Runtime rt = Runtime.getRuntime();
            rt.gc();
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> GARBAGE COLLECTING DONE"));
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Free Memory: " + rt.freeMemory()));
        }
        else if (dcmd.startsWith("*STOP*"))
        {
            String stopMsg = ">>> ********** " + (String) debugCli.getData() + " KILLED THE SERVER!!! ********** <<<";
            stopServer(stopMsg);
            System.exit(0);
        }
        else if (dcmd.startsWith("*BCAST* "))
        {
            ///
            /// broadcast to all chat channels and games
            ///
            broadcast(SOCBCastTextMsg.toCmd(dcmd.substring(8)));
        }
        else if (dcmd.startsWith("*BOTLIST*"))
        {
            Enumeration robotsEnum = robots.elements();

            while (robotsEnum.hasMoreElements())
            {
                StringConnection robotConn = (StringConnection) robotsEnum.nextElement();
                messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> Robot: " + robotConn.getData()));
                robotConn.put(SOCAdminPing.toCmd((ga)));
            }
        }
        else if (dcmd.startsWith("*RESETBOT* "))
        {
            String botName = dcmd.substring(11).trim();
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> botName = '" + botName + "'"));

            Enumeration robotsEnum = robots.elements();

            boolean botFound = false;
            while (robotsEnum.hasMoreElements())
            {
                StringConnection robotConn = (StringConnection) robotsEnum.nextElement();
                if (botName.equals((String) robotConn.getData()))
                {
                    botFound = true;
                    messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> SENDING RESET COMMAND TO " + botName));

                    SOCAdminReset resetCmd = new SOCAdminReset();
                    robotConn.put(resetCmd.toCmd());

                    break;
                }
            }
            if (! botFound)
                D.ebugPrintln("L2590 Bot not found to reset: " + botName);
        }
        else if (dcmd.startsWith("*KILLBOT* "))
        {
            String botName = dcmd.substring(10).trim();
            messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> botName = '" + botName + "'"));

            Enumeration robotsEnum = robots.elements();

            boolean botFound = false;
            while (robotsEnum.hasMoreElements())
            {
                StringConnection robotConn = (StringConnection) robotsEnum.nextElement();

                if (botName.equals((String) robotConn.getData()))
                {
                    botFound = true;
                    messageToGame(ga, new SOCGameTextMsg(ga, SERVERNAME, "> DISCONNECTING " + botName));
                    removeConnection(robotConn);
                    removeConnectionCleanup(robotConn);

                    break;
                }
            }
            if (! botFound)
                D.ebugPrintln("L2614 Bot not found to disconnect: " + botName);
        }
    }

    /**
     * The server is being cleanly stopped.
     * Shut down with a final message "The game server is shutting down".
     */
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
            SOCDBHelper.cleanup();
        }
        catch (SQLException x) { }
        
        super.stopServer();
    }

    /**
     * authenticate the user
     * see if the user is in the db, if so then check the password
     * if they're not in the db, but they supplied a password
     * then send a message
     * if they're not in the db, and no password, then ok
     *
     * @param c         the user's connection
     * @param userName  the user's nickname
     * @param password  the user's password
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
     * Because game options aren't sent before client version is known, we won't ever need to
     * send a "delta" of game options based on client version.
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
     *                Can only set the client's known version once; a second "known" call with
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
        if (c != null)
        {
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
            if ((c.getData() == null) && (!checkNickname(mes.getNickname())))
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NAME_IN_USE, cliVers,
                         MSG_NICKNAME_ALREADY_IN_USE));

                return;
            }

            if ((c.getData() == null) && (!authenticateUser(c, mes.getNickname(), mes.getPassword())))
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
            if (c.getData() == null)
            {
                c.setData(mes.getNickname());
                nameConnection(c);
                numberOfUsers++;
            }

            /**
             * Tell the client that everything is good to go
             */
            c.put(SOCJoinAuth.toCmd(mes.getNickname(), mes.getChannel()));
            c.put(SOCStatusMessage.toCmd
                    (SOCStatusMessage.SV_OK, "Welcome to Java Settlers of Catan!"));

            /**
             * Add the StringConnection to the channel
             */
            String ch = mes.getChannel();

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
                    channelList.createChannel(ch);
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
            messageToChannel(ch, new SOCJoin(mes.getNickname(), "", "dummyhost", ch));
        }
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

        if (c != null)
        {
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
    }

    /**
     * Handle the "I'm a robot" message.
     * Robots send their {@link SOCVersion} before sending this message.
     * Their version is checked here, must equal server's version.
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleIMAROBOT(StringConnection c, SOCImARobot mes)
    {
        if (c != null)
        {
            /**
             * Check the reported version; if none, assume 1000 (1.0.00)
             */
            int srvVers = Version.versionNumber();
            int cliVers = c.getVersion(); 
            if (cliVers != srvVers)
            {
                String rejectMsg = "Sorry, robot client version does not match, version number "
                    + Integer.toString(srvVers) + " is required.";
                c.put(new SOCRejectConnection(rejectMsg).toCmd());
                c.disconnectSoft();
                System.out.println("Rejected robot " + mes.getNickname() + ": Version " + cliVers + " does not match server version");
                return;  // <--- Early return: Robot client too old ---
            }

            /**
             * Check that the nickname is ok
             */
            if ((c.getData() == null) && (!checkNickname(mes.getNickname())))
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
                    = (ConnExcepDelayedPrintTask) cliConnDisconPrintsPending.get(mes.getNickname());
                if (depart != null)
                {
                    depart.cancel();
                    cliConnDisconPrintsPending.remove(mes.getNickname());
                    ConnExcepDelayedPrintTask arrive
                        = (ConnExcepDelayedPrintTask) cliConnDisconPrintsPending.get(c);
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
            ((SOCClientData) c.getAppData()).isRobot = true;
            nameConnection(c);
        }
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
        if ((cmdText.startsWith("*ADDTIME*")) || (cmdText.startsWith("*addtime*")) || (cmdText.startsWith("ADDTIME")) || (cmdText.startsWith("addtime")))
        {
            // add 30 min. to the expiration time.  If this
            // changes to another timespan, please update the
            // warning text sent in checkForExpiredGames().
            // Use ">>>" in messageToGame to mark as urgent.
            ga.setExpiration(ga.getExpiration() + (30 * 60 * 1000));
            messageToGameUrgent(gaName, ">>> This game will expire in " + ((ga.getExpiration() - System.currentTimeMillis()) / 60000) + " minutes.");
        }

        ///
        /// Check the time remaining for this game
        ///
        if (cmdText.startsWith("*CHECKTIME*"))
        {
            processDebugCommand_checktime(c, gaName, ga);
        }
        else if (cmdText.startsWith("*VERSION*"))
        {
            messageToPlayer(c, new SOCGameTextMsg(gaName, SERVERNAME,
                    "Java Settlers Server " +Version.versionNumber() + " (" + Version.version() + ") build " + Version.buildnum()));
        }
        else if (cmdText.startsWith("*WHO*"))
        {
            Vector gameMembers = null;
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
                Enumeration membersEnum = gameMembers.elements();

                while (membersEnum.hasMoreElements())
                {
                    StringConnection conn = (StringConnection) membersEnum.nextElement();
                    messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, "> " + conn.getData()));
                }
            }
        }

        //
        // useful for debugging
        //
        // 1.1.07: all practice games are debug mode, for ease of debugging;
        //         not much use for a chat window in a practice game anyway.
        //
        if (c.getData().equals("debug") || (c instanceof LocalStringConnection))
        {
            final String msgText = cmdText;
            if (cmdText.startsWith("rsrcs:"))
            {
                giveResources(cmdText, ga);
            }
            else if (cmdText.startsWith("dev:"))
            {
                giveDevCard(cmdText, ga);
            }
            else if (cmdText.charAt(0) == '*')
            {
                processDebugCommand(c, ga.getName(), msgText);
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
     * @param c  Client requesting the stats
     * @param gameData  Game to print stats
     * @since 1.1.07
     */
    private void processDebugCommand_checktime(StringConnection c, final String gaName, SOCGame gameData)
    {
        if (gameData == null)
            return;
        messageToPlayer(c, new SOCGameTextMsg(gaName, SERVERNAME, "-- Game statistics: --"));
        messageToPlayer(c, new SOCGameTextMsg(gaName, SERVERNAME, "Rounds played: " + gameData.getRoundCount()));
        // time
        Date gstart = gameData.getStartTime();
        if (gstart != null)
        {                
            long gameSeconds = ((new Date().getTime() - gstart.getTime())+500L) / 1000L;
            long gameMinutes = (gameSeconds+29L)/60L;
            String gLengthMsg = "This game started " + gameMinutes + " minutes ago.";
            messageToPlayer(c, new SOCGameTextMsg(gaName, SERVERNAME, gLengthMsg));
            // Ignore possible "1 minutes"; that game is too short to worry about.
        }
        String expireMsg = ">>> This game will expire in " + ((gameData.getExpiration() - System.currentTimeMillis()) / 60000) + " minutes.";
        messageToPlayer(c, new SOCGameTextMsg(gaName, SERVERNAME, expireMsg));
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
        if (c != null)
        {
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
		(c, mes.getNickname(), mes.getPassword(), mes.getGame(), null);

	}
    }

    /**
     * Check username/password and create new game, or join game.
     * Called by handleJOINGAME and handleNEWGAMEWITHOPTIONSREQUEST.
     * JOINGAME or NEWGAMEWITHOPTIONSREQUEST may be the first message with the
     * client's username and password, so c.getData() may be null.
     * Assumes client's version is already received or guessed.
     *<P>
     * Game name and player name have a maximum length and some disallowed characters; see parameters.
     *<P>
     *<b>Process if gameOpts != null:</b>
     *<UL>
     *  <LI> if game with this name already exists, respond with
     *      STATUSMESSAGE({@link SOCStatusMessage#SV_NEWGAME_ALREADY_EXISTS SV_NEWGAME_ALREADY_EXISTS})
     *  <LI> compare cli's param name-value pairs, with srv's known values. <br>
     *	    - if any are above/below max/min, clip to the max/min value <br>
     *	    - if any are unknown, resp with
     *        STATUSMESSAGE({@link SOCStatusMessage#SV_NEWGAME_OPTION_UNKNOWN SV_NEWGAME_OPTION_UNKNOWN}) <br>
     *      - if any are too new for client's version, resp with
     *        STATUSMESSAGE({@link SOCStatusMessage#SV_NEWGAME_OPTION_VALUE_TOONEW SV_NEWGAME_OPTION_VALUE_TOONEW}) <br>
     *      Comparison is done by {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable)}.
     *  <LI> if ok: create new game with params;
     *      socgame will calc game's minCliVersion,
     *      and this method will check that against cli's version.
     *  <LI> announce to all players using NEWGAMEWITHOPTIONS;
     *       older clients get NEWGAME, won't see the options
     *  <LI> send JOINGAMEAUTH to requesting client, via {@link #joinGame(SOCGame, StringConnection, boolean)}
     *  <LI> send game status details to requesting client, via {@link #joinGame(SOCGame, StringConnection, boolean)}
     *</UL>
     *
     * @param c connection requesting the game, must not be null
     * @param msgUser username of client in message. Must pass {@link SOCMessage#isSingleLineAndSafe(String)}
     *                  and be at most {@link #PLAYER_NAME_MAX_LENGTH} characters.
     * @param msgPass password of client in message
     * @param gameName  name of game to create/join. Must pass {@link SOCMessage#isSingleLineAndSafe(String)}
     *                  and be at most {@link #GAME_NAME_MAX_LENGTH} characters.
     * @param gameOpts  if game has options, contains {@link SOCGameOption} to create new game; if not null, will not join an existing game.
     *                  Will validate by calling
     *                  {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable)}.
     *
     * @since 1.1.07
     */
    private void createOrJoinGameIfUserOK
        (StringConnection c, final String msgUser, final String msgPass, final String gameName, Hashtable gameOpts)
    {
            /**
             * Check that the nickname is ok
             */
            if (c.getData() == null)
            {
                if (msgUser.length() > PLAYER_NAME_MAX_LENGTH)
                {
                    c.put(SOCStatusMessage.toCmd
                            (SOCStatusMessage.SV_NEWGAME_NAME_TOO_LONG, c.getVersion(),
                             SOCStatusMessage.MSG_SV_NEWGAME_NAME_TOO_LONG + Integer.toString(PLAYER_NAME_MAX_LENGTH)));    
                    return;
                }
                if (!checkNickname(msgUser))
                {
                    c.put(SOCStatusMessage.toCmd
                            (SOCStatusMessage.SV_NAME_IN_USE, c.getVersion(),
                             MSG_NICKNAME_ALREADY_IN_USE));    
                    return;
                }
            }

            if ((c.getData() == null) && (!authenticateUser(c, msgUser, msgPass)))
            {
                return;
            }

            /**
             * Check that the game name is ok
             */
            if (! SOCMessage.isSingleLineAndSafe(gameName))
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NEWGAME_NAME_REJECTED, c.getVersion(),
                         SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED));
                  // "This game name is not permitted, please choose a different name."

                  return;  // <---- Early return ----
            }
            if (gameName.length() > GAME_NAME_MAX_LENGTH)
            {
                c.put(SOCStatusMessage.toCmd
                        (SOCStatusMessage.SV_NEWGAME_NAME_TOO_LONG, c.getVersion(),
                         SOCStatusMessage.MSG_SV_NEWGAME_NAME_TOO_LONG + Integer.toString(GAME_NAME_MAX_LENGTH)));
                // Please choose a shorter name; maximum length: 20

                return;  // <---- Early return ----
            }

            /**
             * Now that everything's validated, name this connection/user/player
             */
            if (c != null)
            {
                if (c.getData() == null)
                {
                    c.setData(msgUser);
                    nameConnection(c);
                    numberOfUsers++;
                }
            }

            /**
             * If we have game options, we're being asked to create a new game.
             * Validate them and ensure the game doesn't already exist.
             */
            if (gameOpts != null)
            {
		if (gameList.isGame(gameName))
		{
		    c.put(SOCStatusMessage.toCmd
			  (SOCStatusMessage.SV_NEWGAME_ALREADY_EXISTS, c.getVersion(),
			   SOCStatusMessage.MSG_SV_NEWGAME_ALREADY_EXISTS));
		    // "A game with this name already exists, please choose a different name."

		    return;  // <---- Early return ----
		}

                if (! SOCGameOption.adjustOptionsToKnown(gameOpts, null))
                {
                    c.put(SOCStatusMessage.toCmd
                          (SOCStatusMessage.SV_NEWGAME_OPTION_UNKNOWN, c.getVersion(),
                           "Unknown game option(s) were requested, cannot create this game."));

		    return;  // <---- Early return ----
                }
            }

            /**
             * Try to add player to game, and tell the client that everything is ready;
             * if game doesn't yet exist, it's created in connectToGame, and announced
             * there to all clients.
             *<P>
             * If client's version is too low (based on game options, etc),
             * connectToGame will throw an exception; tell the client if that happens.
             */
            try
            {
                if (connectToGame(c, gameName, gameOpts))
                {
                    /**
                     * send JOINGAMEAUTH to client,
                     * send the entire state of the game to client,
                     * send client join event to other players of game
                     */
                    SOCGame gameData = gameList.getGameData(gameName);

                    if (gameData != null)
                    {
                        joinGame(gameData, c, false);
                    }
                }
            } catch (SOCGameOptionVersionException e)
            {
                // Let them know they can't join; include the game's version.
                // This cli asked to created it, otherwise gameOpts would be null.
                c.put(SOCStatusMessage.toCmd
                  (SOCStatusMessage.SV_NEWGAME_OPTION_VALUE_TOONEW, c.getVersion(),
                    "Cannot create game with these options; requires version "
                    + Integer.toString(e.gameOptsVersion)
                    + SOCMessage.sep2_char + gameName
                    + SOCMessage.sep2_char + e.problemOptionsList()));
            } catch (IllegalArgumentException e)
            {
                // Let them know they can't join; include the game's version.

                c.put(SOCStatusMessage.toCmd
                  (SOCStatusMessage.SV_CANT_JOIN_GAME_VERSION, c.getVersion(),
                    "Cannot join game; requires version "
                    + Integer.toString(gameList.getGameData(gameName).getClientVersionMinRequired())
                    + ": " + gameName));
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
        if (c != null)
        {
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
        Vector requests = (Vector) robotDismissRequests.get(gaName);

        if (requests != null)
        {
            Enumeration reqEnum = requests.elements();
            SOCReplaceRequest req = null;

            while (reqEnum.hasMoreElements())
            {
                SOCReplaceRequest tempReq = (SOCReplaceRequest) reqEnum.nextElement();

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
                 * let the person replacing the robot sit down
                 */
                SOCGame ga = gameList.getGameData(gaName);
                sitDown(ga, req.getArriving(), req.getSitDownMessage().getPlayerNumber(), req.getSitDownMessage().isRobot(), false);
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
        if (c != null)
        {
            final String gaName = mes.getGame();
            SOCGame ga = gameList.getGameData(gaName);

            if (ga != null)
            {
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
                            Vector disRequests = (Vector) robotDismissRequests.get(gaName);
                            SOCReplaceRequest req = new SOCReplaceRequest(c, robotCon, mes);

                            if (disRequests == null)
                            {
                                disRequests = new Vector();
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
                    D.ebugPrintStackTrace(e, "Exception in handleSITDOWN");
                }

                ga.releaseMonitor();

                /**
                 * if this is a robot, remove it from the request list
                 */
                Vector joinRequests = (Vector) robotJoinRequests.get(gaName);

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
                        c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "This game is full, you cannot sit down."));
                    }
                }
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
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                ga.takeMonitor();

                try
                {
                    SOCPlayer player = ga.getPlayer((String) c.getData());

                    /**
                     * make sure the player can do it
                     */
                    final String gaName = ga.getName();
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
                        switch (mes.getPieceType())
                        {
                        case SOCPlayingPiece.ROAD:

                            SOCRoad rd = new SOCRoad(player, mes.getCoordinates());

                            if ((ga.getGameState() == SOCGame.START1B) || (ga.getGameState() == SOCGame.START2B) || (ga.getGameState() == SOCGame.PLACING_ROAD) || (ga.getGameState() == SOCGame.PLACING_FREE_ROAD1) || (ga.getGameState() == SOCGame.PLACING_FREE_ROAD2))
                            {
                                if (player.isPotentialRoad(mes.getCoordinates()))
                                {
                                    ga.putPiece(rd);  // Changes state and sometimes player

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
                                    messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, (String) c.getData() + " built a road."));
                                    messageToGameWithMon(gaName, new SOCPutPiece(mes.getGame(), player.getPlayerNumber(), SOCPlayingPiece.ROAD, mes.getCoordinates()));
                                    gameList.releaseMonitorForGame(gaName);
                                    boolean toldRoll = sendGameState(ga, false);
                                    broadcastGameStats(ga);

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
                                        messageToGame(gaName, new SOCRollDicePrompt (gaName, player.getPlayerNumber()));
                                    }
                                }
                                else
                                {
                                    D.ebugPrintln("ILLEGAL ROAD");
                                    c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't build a road there."));
                                    sendDenyReply = true;                                   
                                }
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't build a road right now."));
                            }

                            break;

                        case SOCPlayingPiece.SETTLEMENT:

                            SOCSettlement se = new SOCSettlement(player, mes.getCoordinates());

                            if ((ga.getGameState() == SOCGame.START1A) || (ga.getGameState() == SOCGame.START2A) || (ga.getGameState() == SOCGame.PLACING_SETTLEMENT))
                            {
                                if (player.isPotentialSettlement(mes.getCoordinates()))
                                {
                                    ga.putPiece(se);   // Changes game state and (if game start) player
                                    gameList.takeMonitorForGame(gaName);
                                    messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, (String) c.getData() + " built a settlement."));
                                    messageToGameWithMon(gaName, new SOCPutPiece(mes.getGame(), player.getPlayerNumber(), SOCPlayingPiece.SETTLEMENT, mes.getCoordinates()));
                                    gameList.releaseMonitorForGame(gaName);
                                    broadcastGameStats(ga);
                                    sendGameState(ga);

                                    if (!checkTurn(c, ga))
                                    {
                                        sendTurn(ga, false);  // Announce new current player.
                                    }
                                }
                                else
                                {
                                    D.ebugPrintln("ILLEGAL SETTLEMENT");
                                    c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't build a settlement there."));
                                    sendDenyReply = true;
                                }
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't build a settlement right now."));
                            }

                            break;

                        case SOCPlayingPiece.CITY:

                            SOCCity ci = new SOCCity(player, mes.getCoordinates());

                            if (ga.getGameState() == SOCGame.PLACING_CITY)
                            {
                                if (player.isPotentialCity(mes.getCoordinates()))
                                {
                                    ga.putPiece(ci);  // changes game state and maybe player
                                    gameList.takeMonitorForGame(gaName);
                                    messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, (String) c.getData() + " built a city."));
                                    messageToGameWithMon(gaName, new SOCPutPiece(mes.getGame(), player.getPlayerNumber(), SOCPlayingPiece.CITY, mes.getCoordinates()));
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
                                    D.ebugPrintln("ILLEGAL CITY");
                                    c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't build a city there."));
                                    sendDenyReply = true;
                                }
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't build a city right now."));
                            }

                            break;
                        
                        }  // switch (mes.getPieceType())
                        
                        if (sendDenyReply)
                        {
                            messageToPlayer(c, new SOCCancelBuildRequest(gaName, mes.getPieceType()));
                        }                       
                    }
                    else
                    {
                        c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "It's not your turn."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintStackTrace(e, "Exception caught in handlePUTPIECE");
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "move robber" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleMOVEROBBER(StringConnection c, SOCMoveRobber mes)
    {
        if (c != null)
        {
            String gn = mes.getGame();
            SOCGame ga = gameList.getGameData(gn);

            if (ga != null)
            {
                ga.takeMonitor();

                try
                {
                    SOCPlayer player = ga.getPlayer((String) c.getData());

                    /**
                     * make sure the player can do it
                     */
                    final String gaName = ga.getName();
                    if (ga.canMoveRobber(player.getPlayerNumber(), mes.getCoordinates()))
                    {
                        SOCMoveRobberResult result = ga.moveRobber(player.getPlayerNumber(), mes.getCoordinates());
                        messageToGame(gaName, new SOCMoveRobber(gaName, player.getPlayerNumber(), mes.getCoordinates()));

                        Vector victims = result.getVictims();

                        /** only one possible victim */
                        if (victims.size() == 1)
                        {
                            /**
                             * report what was stolen
                             */
                            SOCPlayer victim = (SOCPlayer) victims.firstElement();
                            reportRobbery(ga, player, victim, result.getLoot());
                        }
                        /** no victim */
                        else if (victims.size() == 0)
                        {
                            /**
                             * just say it was moved; nothing is stolen
                             */
                            messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, (String) c.getData() + " moved the robber."));
                        }
                        else
                        {
                            /**
                             * else, the player needs to choose a victim
                             */
                            messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, (String) c.getData() + " moved the robber, must choose a victim."));                            
                        }

                        sendGameState(ga);
                    }
                    else
                    {
                        c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't move the robber."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintStackTrace(e, "Exception caught");
                }

                ga.releaseMonitor();
            }
        }
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
        if (c != null)
        {
            String gn = mes.getGame();
            SOCGame ga = gameList.getGameData(gn);

            if (ga != null)
            {
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
                        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
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
                        numEmpty = Math.min(numEmpty, ga.getAvailableSeatCount());
                        
                        if (seatsFull && (numPlayers < 2))
                        {
                            seatsFull = false;
                            numEmpty = 3;
                            String m = "Sorry, the only player cannot lock all seats.";
                            messageToGame(gn, new SOCGameTextMsg(gn, SERVERNAME, m));
                        }
                        else if (!seatsFull)
                        {
                            if (robots.isEmpty()) 
                            {                                
                                if (numPlayers < SOCGame.MINPLAYERS)
                                {
                                    messageToGame(gn, new SOCGameTextMsg
                                        (gn, SERVERNAME, "No robots on this server, please fill at least "
                                         + SOCGame.MINPLAYERS + " seats before starting." ));
                                }
                                else
                                {
                                    seatsFull = true;  // Enough players to start game.
                                }
                            }
                            else
                            {
                                //
                                // make sure there are enough robots connected
                                //
                                if (numEmpty > robots.size())
                                {
                                    String m;
                                    if (anyLocked)
                                        m = "Sorry, not enough robots to fill all the seats.  Only " + robots.size() + " robots are available.";
                                    else
                                        m = "Sorry, not enough robots to fill all the seats.  Lock some seats.  Only " + robots.size() + " robots are available.";
                                    messageToGame(gn, new SOCGameTextMsg(gn, SERVERNAME, m));
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
                                        messageToGame(gn, new SOCGameTextMsg(gn, SERVERNAME, m));
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
        }
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

        Vector robotRequests = null;

        int[] robotIndexes = null;
        if (robotSeats == null)
        {
            // shuffle the indexes to distribute load
            robotIndexes = robotShuffleForJoin();
        }
        else
        {
            // robotSeats not null: check length
            if (robotSeats.length != SOCGame.MAXPLAYERS)
                throw new IllegalArgumentException("robotSeats Length must be MAXPLAYERS");
        }

        final String gname = ga.getName();
	final Hashtable gopts = ga.getGameOptions();
	int seatsOpen = ga.getAvailableSeatCount();
        int idx = 0;
        StringConnection[] robotSeatsConns = new StringConnection[SOCGame.MAXPLAYERS];

        for (int i = 0; (i < SOCGame.MAXPLAYERS) && (seatsOpen > 0);
                i++)
        {
            if (ga.isSeatVacant(i) && ! ga.isSeatLocked(i))
            {
                /**
                 * fetch a robot player
                 */
                if (idx < robots.size())
                {
                    messageToGame(gname, new SOCGameTextMsg(gname, SERVERNAME, "Fetching a robot player..."));

                    StringConnection robotConn;
                    if (robotSeats != null)
                    {
                        robotConn = robotSeats[i];
                        if (robotConn == null)
                            throw new IllegalArgumentException("robotSeats[" + i + "] was needed but null");
                    }
                    else
                    {
                        robotConn = (StringConnection) robots.get(robotIndexes[idx]);
                    }
                    idx++;
                    --seatsOpen;
                    robotSeatsConns[i] = robotConn;

                    /**
                     * record the request
                     */
                    D.ebugPrintln("@@@ JOIN GAME REQUEST for " + (String) robotConn.getData());
                    if (robotRequests == null)
                        robotRequests = new Vector();
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
            for (int i = 0; i < SOCGame.MAXPLAYERS; ++i)
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
        if (c != null)
        {
            final String gn = mes.getGame();
            SOCGame ga = gameList.getGameData(gn);

            if (ga != null)
            {
                ga.takeMonitor();

                try
                {
                    final String plName = (String) c.getData();
                    final SOCPlayer pl = ga.getPlayer(plName);
                    if ((pl != null) && ga.canRollDice(pl.getPlayerNumber()))
                    {
                        IntPair dice = ga.rollDice();
                        /**
                         * Send roll results and then text to client.
                         * Client expects to see DiceResult first, then text message;
                         * to reduce visual clutter, SOCPlayerInterface.print
                         * expects text message to follow a certain format.
                         * If a 7 is rolled, sendGameState will also say who must discard
                         * (in a GAMETEXTMSG).
                         */
                        messageToGame(gn, new SOCDiceResult(gn, ga.getCurrentDice()));
                        messageToGame(gn, new SOCGameTextMsg(gn, SERVERNAME, plName + " rolled a " + dice.getA() + " and a " + dice.getB() + "."));
                        sendGameState(ga);  // For 7, give visual feedback before sending discard request

                        /**
                         * if the roll is not 7, tell players what they got
                         */
                        if (ga.getCurrentDice() != 7)
                        {
                            boolean noPlayersGained = true;
                            StringBuffer gainsText = new StringBuffer();

                            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                            {
                                if (! ga.isSeatVacant(i))
                                {
                                    SOCResourceSet rsrcs = ga.getResourcesGainedFromRoll(ga.getPlayer(i), ga.getCurrentDice());
    
                                    if (rsrcs.getTotal() != 0)
                                    {
                                        if (noPlayersGained)
                                        {
                                            noPlayersGained = false;
                                        }
                                        else
                                        {
                                            gainsText.append(" ");
                                        }

                                        gainsText.append(ga.getPlayer(i).getName());
                                        gainsText.append(" gets ");
                                        // Send SOCPlayerElement messages,
                                        // build resource-text in gainsText.
                                        reportRsrcGainLoss(gn, rsrcs, false, i, -1, gainsText, null);
                                        gainsText.append(".");
                                    }

                                    //
                                    //  send all resource info for accuracy
                                    //
                                    StringConnection playerCon = getConnection(ga.getPlayer(i).getName());
                                    if (playerCon != null)
                                    {
                                        SOCResourceSet resources = ga.getPlayer(i).getResources();
                                        messageToPlayer(playerCon, new SOCPlayerElement(ga.getName(), i, SOCPlayerElement.SET, SOCPlayerElement.CLAY, resources.getAmount(SOCPlayerElement.CLAY)));
                                        messageToPlayer(playerCon, new SOCPlayerElement(ga.getName(), i, SOCPlayerElement.SET, SOCPlayerElement.ORE, resources.getAmount(SOCPlayerElement.ORE)));
                                        messageToPlayer(playerCon, new SOCPlayerElement(ga.getName(), i, SOCPlayerElement.SET, SOCPlayerElement.SHEEP, resources.getAmount(SOCPlayerElement.SHEEP)));
                                        messageToPlayer(playerCon, new SOCPlayerElement(ga.getName(), i, SOCPlayerElement.SET, SOCPlayerElement.WHEAT, resources.getAmount(SOCPlayerElement.WHEAT)));
                                        messageToPlayer(playerCon, new SOCPlayerElement(ga.getName(), i, SOCPlayerElement.SET, SOCPlayerElement.WOOD, resources.getAmount(SOCPlayerElement.WOOD)));
                                        messageToGame(ga.getName(), new SOCResourceCount(ga.getName(), i, resources.getTotal()));
                                    }
                                }  // if (! ga.isSeatVacant(i))
                            }  // for (i)

                            String message;
                            if (noPlayersGained)
                            {
                                message = "No player gets anything.";
                            }
                            else
                            {
                                message = gainsText.toString();
                            }
                            messageToGame(gn, new SOCGameTextMsg(gn, SERVERNAME, message));

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
                             */
                            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
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
                        c.put(SOCGameTextMsg.toCmd(gn, SERVERNAME, "You can't roll right now."));
                    }
                }
                catch (Exception e)
                {
                    if (D.ebugIsEnabled())
                    {
                        D.ebugPrintln("Exception in handleROLLDICE - " + e);
                        e.printStackTrace(System.out);
                    }
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "discard" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleDISCARD(StringConnection c, SOCDiscard mes)
    {
        if (c != null)
        {
            final String gn = mes.getGame();
            SOCGame ga = gameList.getGameData(gn);

            if (ga != null)
            {
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
                        messageToGame(gn, new SOCGameTextMsg(gn, SERVERNAME, (String) c.getData() + " discarded " + mes.getResources().getTotal() + " resources."));

                        /**
                         * send the new state, or end turn if was marked earlier as forced
                         */
                        if ((ga.getGameState() != SOCGame.PLAY1) || ! ga.isForcingEndTurn())
                        {
                            sendGameState(ga);
                        } else {
                            endGameTurn(ga);  // already did ga.takeMonitor()
                        }
                    }
                    else
                    {
                        /**
                         * there could be a better feedback message here
                         */
                        c.put(SOCGameTextMsg.toCmd(gn, SERVERNAME, "You can't discard that many cards."));
                    }
                }
                catch (Throwable e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "end turn" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleENDTURN(StringConnection c, SOCEndTurn mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                final String gname = ga.getName();               
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
                            c.put(SOCGameTextMsg.toCmd(gname, SERVERNAME, msg));
                        }
                    }
                    else if (checkTurn(c, ga))
                    {
                        SOCPlayer pl = ga.getPlayer(plName);
                        if ((pl != null) && ga.canEndTurn(pl.getPlayerNumber()))
                        {
                            endGameTurn(ga);
                        }
                        else
                        {
                            c.put(SOCGameTextMsg.toCmd(gname, SERVERNAME, "You can't end your turn yet."));
                        }
                    }
                    else
                    {
                        c.put(SOCGameTextMsg.toCmd(gname, SERVERNAME, "It's not your turn."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintStackTrace(e, "Exception caught");
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * Pre-checking already done, end the current player's turn in this game.
     * Alter game state and send messages to players.
     *<P>
     * Assumes:
     * <UL>
     * <LI> ga.canEndTurn already called, to validate player
     * <LI> ga.takeMonitor already called (not the same as {@link SOCGameList#takeMonitorForGame(String)})
     * <LI> gamelist.takeMonitorForGame is NOT called, we do NOT have that monitor
     * </UL> 
     * @param ga Game to end turn
     */
    private void endGameTurn(SOCGame ga)
    {
        final String gname = ga.getName();

        boolean hadBoardResetRequest = (-1 != ga.getResetVoteRequester());
        ga.endTurn();  // May set state to OVER, if new player has enough points to win
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
        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
        {
            messageToGameWithMon(gname, new SOCClearOffer(gname, i));
        }
        gameList.releaseMonitorForGame(gname);

        /**
         * send whose turn it is
         */
        sendTurn(ga, wantsRollPrompt);
    }

    /**
     * Try to force-end the current player's turn in this game.
     * Alter game state and send messages to players.
     * Will call {@link #endGameTurn(SOCGame)} if appropriate.
     * Will send gameState and current player (turn) to clients.
     *<P>
     * Assumes, as {@link #endGameTurn(SOCGame)} does:
     * <UL>
     * <LI> ga.canEndTurn already called, returned false
     * <LI> ga.takeMonitor already called (not the same as {@link SOCGameList#takeMonitorForGame(String)})
     * <LI> gamelist.takeMonitorForGame is NOT called, we do NOT have that monitor
     * </UL>
     * @param ga Game to force end turn
     * @param plName Current player's name. Needed because if they have been disconnected by
     *               {@link #leaveGame(StringConnection, String, boolean)},
     *               their name within game object is already null.
     *
     * @see SOCGame#forceEndTurn()
     */
    private void forceEndGameTurn(SOCGame ga, final String plName)
    {
        final String gaName = ga.getName();        
        final SOCForceEndTurnResult res = ga.forceEndTurn();  // State now hopefully PLAY1
        final int cpn = ga.getCurrentPlayerNumber();

        /**
         * report any resources lost, gained
         */
        SOCResourceSet resGainLoss = res.getResourcesGainedLost();
        if (resGainLoss != null)
        {
            /**
             * If returning resources to player (not discarding), report actual types/amounts.
             * For discard, tell the discarding player's client that they discarded the resources,
             * tell everyone else that the player discarded unknown resources.
             */
            if (! res.isLoss())
                reportRsrcGainLoss(gaName, resGainLoss, false, cpn, -1, null, null);
            else
            {
                StringConnection c = getConnection(plName);
                if ((c != null) && c.isConnected())
                    reportRsrcGainLoss(gaName, resGainLoss, true, cpn, -1, null, c);
                int totalRes = resGainLoss.getTotal();
                messageToGameExcept(gaName, c, new SOCPlayerElement(gaName, cpn, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, totalRes), true);
                messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, plName + " discarded " + totalRes + " resources."));
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
                messageToPlayer(c, new SOCDevCard(gaName, cpn, SOCDevCard.ADDOLD, card));
            messageToGameExcept(gaName, c, new SOCDevCard(gaName, cpn, SOCDevCard.ADDOLD, SOCDevCardConstants.UNKNOWN), true);                       
            messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, plName + "'s just-played development card was returned."));            
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
            sendGameState(ga, false);
            sendTurn(ga, false);
            return;  // <--- Early return ---
        }

        /**
         * If the turn can now end, proceed as if player requested it.
         * Otherwise, send current gamestate.  We'll all wait for other
         * players to send discard messages, and afterwards this turn can end.
         */
        if (ga.canEndTurn(cpn))
            endGameTurn(ga);
        else
            sendGameState(ga, false); 
    }

    /**
     * handle "choose player" message during robbery
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleCHOOSEPLAYER(StringConnection c, SOCChoosePlayer mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                ga.takeMonitor();

                try
                {
                    if (checkTurn(c, ga))
                    {
                        if (ga.canChoosePlayer(mes.getChoice()))
                        {
                            int rsrc = ga.stealFromPlayer(mes.getChoice());
                            reportRobbery(ga, ga.getPlayer((String) c.getData()), ga.getPlayer(mes.getChoice()), rsrc);
                            sendGameState(ga);
                        }
                        else
                        {
                            c.put(SOCGameTextMsg.toCmd(ga.getName(), SERVERNAME, "You can't steal from that player."));
                        }
                    }
                    else
                    {
                        c.put(SOCGameTextMsg.toCmd(ga.getName(), SERVERNAME, "It's not your turn."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "make offer" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleMAKEOFFER(StringConnection c, SOCMakeOffer mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                final String gaName = ga.getName();
            	if (ga.isGameOptionSet("NT"))
            	{
            		messageToPlayer(c, new SOCGameTextMsg(gaName, SERVERNAME, "Trading is not allowed in this game."));
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
                            messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, offMsgText.toString() ));
                        }

                        SOCMakeOffer makeOfferMessage = new SOCMakeOffer(gaName, remadeOffer);
                        messageToGame(gaName, makeOfferMessage);

                        recordGameEvent(gaName, makeOfferMessage.toCmd());

                        /**
                         * clear all the trade messages because a new offer has been made
                         */
                        gameList.takeMonitorForGame(gaName);
                        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                        {
                            messageToGameWithMon(gaName, new SOCClearTradeMsg(gaName, i));
                        }
                        gameList.releaseMonitorForGame(gaName);
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "clear offer" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleCLEAROFFER(StringConnection c, SOCClearOffer mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
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
                    for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                    {
                        messageToGame(gaName, new SOCClearTradeMsg(gaName, i));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "reject offer" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleREJECTOFFER(StringConnection c, SOCRejectOffer mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                SOCPlayer player = ga.getPlayer((String) c.getData());

                if (player != null)
                {
                    final String gaName = ga.getName();
                    SOCRejectOffer rejectMessage = new SOCRejectOffer(gaName, player.getPlayerNumber());
                    messageToGame(gaName, rejectMessage);

                    recordGameEvent(gaName, rejectMessage.toCmd());
                }
            }
        }
    }

    /**
     * handle "accept offer" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleACCEPTOFFER(StringConnection c, SOCAcceptOffer mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                ga.takeMonitor();

                try
                {
                    SOCPlayer player = ga.getPlayer((String) c.getData());

                    if (player != null)
                    {
                        int acceptingNumber = player.getPlayerNumber();

                        if (ga.canMakeTrade(mes.getOfferingNumber(), acceptingNumber))
                        {
                            ga.makeTrade(mes.getOfferingNumber(), acceptingNumber);
                            reportTrade(ga, mes.getOfferingNumber(), acceptingNumber);

                            recordGameEvent(mes.getGame(), mes.toCmd());

                            /**
                             * clear all offers
                             */
                            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                            {
                                ga.getPlayer(i).setCurrentOffer(null);
                                messageToGame(ga.getName(), new SOCClearOffer(ga.getName(), i));
                            }

                            /**
                             * send a message to the bots that the offer was accepted
                             */
                            messageToGame(ga.getName(), mes);
                        }
                        else
                        {
                            c.put(SOCGameTextMsg.toCmd(ga.getName(), SERVERNAME, "You can't make that trade."));
                        }
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "bank trade" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleBANKTRADE(StringConnection c, SOCBankTrade mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                ga.takeMonitor();

                try
                {
                    if (checkTurn(c, ga))
                    {
                        if (ga.canMakeBankTrade(mes.getGiveSet(), mes.getGetSet()))
                        {
                            ga.makeBankTrade(mes.getGiveSet(), mes.getGetSet());
                            reportBankTrade(ga, mes.getGiveSet(), mes.getGetSet());
                        }
                        else
                        {
                            c.put(SOCGameTextMsg.toCmd(ga.getName(), SERVERNAME, "You can't make that trade."));
                        }
                    }
                    else
                    {
                        c.put(SOCGameTextMsg.toCmd(ga.getName(), SERVERNAME, "It's not your turn."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "build request" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleBUILDREQUEST(StringConnection c, SOCBuildRequest mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                final String gaName = ga.getName();
                ga.takeMonitor();

                try
                {
                    if (checkTurn(c, ga))
                    {
                        if (ga.getGameState() == SOCGame.PLAY1)
                        {
                            SOCPlayer player = ga.getPlayer((String) c.getData());

                            switch (mes.getPieceType())
                            {
                            case SOCPlayingPiece.ROAD:

                                if (ga.couldBuildRoad(player.getPlayerNumber()))
                                {
                                    ga.buyRoad(player.getPlayerNumber());
                                    messageToGame(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.CLAY, 1));
                                    messageToGame(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                                    sendGameState(ga);
                                }
                                else
                                {
                                    c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't build a road."));
                                }

                                break;

                            case SOCPlayingPiece.SETTLEMENT:

                                if (ga.couldBuildSettlement(player.getPlayerNumber()))
                                {
                                    ga.buySettlement(player.getPlayerNumber());
                                    gameList.takeMonitorForGame(gaName);
                                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.CLAY, 1));
                                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1));
                                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 1));
                                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1));
                                    gameList.releaseMonitorForGame(gaName);
                                    sendGameState(ga);
                                }
                                else
                                {
                                    c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't build a settlement."));
                                }

                                break;

                            case SOCPlayingPiece.CITY:

                                if (ga.couldBuildCity(player.getPlayerNumber()))
                                {
                                    ga.buyCity(player.getPlayerNumber());
                                    messageToGame(ga.getName(), new SOCPlayerElement(ga.getName(), player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.ORE, 3));
                                    messageToGame(ga.getName(), new SOCPlayerElement(ga.getName(), player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 2));
                                    sendGameState(ga);
                                }
                                else
                                {
                                    c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't build a city."));
                                }

                                break;
                            }
                        }
                        else
                        {
                            c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't build now."));
                        }
                    }
                    else
                    {
                        c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "It's not your turn."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "cancel build request" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleCANCELBUILDREQUEST(StringConnection c, SOCCancelBuildRequest mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                ga.takeMonitor();

                try
                {
                    final String gaName = ga.getName();
                    if (checkTurn(c, ga))
                    {
                        SOCPlayer player = ga.getPlayer((String) c.getData());

                        switch (mes.getPieceType())
                        {
                        case SOCPlayingPiece.ROAD:

                            if (ga.getGameState() == SOCGame.PLACING_ROAD)
                            {
                                ga.cancelBuildRoad(player.getPlayerNumber());
                                messageToGame(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.GAIN, SOCPlayerElement.CLAY, 1));
                                messageToGame(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                                sendGameState(ga);
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You didn't buy a road."));
                            }

                            break;

                        case SOCPlayingPiece.SETTLEMENT:

                            if (ga.getGameState() == SOCGame.PLACING_SETTLEMENT)
                            {
                                ga.cancelBuildSettlement(player.getPlayerNumber());
                                gameList.takeMonitorForGame(gaName);
                                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.GAIN, SOCPlayerElement.CLAY, 1));
                                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.GAIN, SOCPlayerElement.SHEEP, 1));
                                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.GAIN, SOCPlayerElement.WHEAT, 1));
                                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1));
                                gameList.releaseMonitorForGame(gaName);
                                sendGameState(ga);
                            }
                            else if ((ga.getGameState() == SOCGame.START1B) || (ga.getGameState() == SOCGame.START2B))
                            {
                                SOCSettlement pp = new SOCSettlement(player, player.getLastSettlementCoord());
                                ga.undoPutInitSettlement(pp);
                                messageToGame(gaName, mes);  // Re-send to all clients to announce it
                                    // (Safe since we've validated all message parameters)
                                messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, player.getName() + " cancelled their settlement placement."));
                                sendGameState(ga);  // This send is redundant, if client reaction changes game state
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You didn't buy a settlement."));
                            }

                            break;

                        case SOCPlayingPiece.CITY:

                            if (ga.getGameState() == SOCGame.PLACING_CITY)
                            {
                                ga.cancelBuildCity(player.getPlayerNumber());
                                messageToGame(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.GAIN, SOCPlayerElement.ORE, 3));
                                messageToGame(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.GAIN, SOCPlayerElement.WHEAT, 2));
                                sendGameState(ga);
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You didn't buy a city."));
                            }

                            break;
                        }
                    }
                    else
                    {
                        c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "It's not your turn."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "buy card request" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleBUYCARDREQUEST(StringConnection c, SOCBuyCardRequest mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                ga.takeMonitor();

                try
                {
                    final String gaName = ga.getName();
                    if (checkTurn(c, ga))
                    {
                        SOCPlayer player = ga.getPlayer((String) c.getData());

                        if ((ga.getGameState() == SOCGame.PLAY1) && (ga.couldBuyDevCard(player.getPlayerNumber())))
                        {
                            int card = ga.buyDevCard();
                            gameList.takeMonitorForGame(gaName);
                            messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.ORE, 1));
                            messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1));
                            messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 1));
                            messageToGameWithMon(gaName, new SOCDevCardCount(gaName, ga.getNumDevCards()));
                            gameList.releaseMonitorForGame(gaName);
                            messageToPlayer(c, new SOCDevCard(gaName, player.getPlayerNumber(), SOCDevCard.DRAW, card));

                            messageToGameExcept(gaName, c, new SOCDevCard(gaName, player.getPlayerNumber(), SOCDevCard.DRAW, SOCDevCardConstants.UNKNOWN), true);
                            messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, (String) c.getData() + " bought a development card."));

                            if (ga.getNumDevCards() > 1)
                            {
                                messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, "There are " + ga.getNumDevCards() + " cards left."));
                            }
                            else if (ga.getNumDevCards() == 1)
                            {
                                messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, "There is 1 card left."));
                            }
                            else
                            {
                                messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, "There are no more Development cards."));
                            }

                            sendGameState(ga);
                        }
                        else
                        {
                            if (ga.getNumDevCards() == 0)
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "There are no more Development cards."));
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't buy a development card now."));
                            }
                        }
                    }
                    else
                    {
                        c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "It's not your turn."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "play development card request" message
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handlePLAYDEVCARDREQUEST(StringConnection c, SOCPlayDevCardRequest mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                ga.takeMonitor();

                try
                {
                    final String gaName = ga.getName();
                    if (checkTurn(c, ga))
                    {
                        SOCPlayer player = ga.getPlayer((String) c.getData());

                        switch (mes.getDevCard())
                        {
                        case SOCDevCardConstants.KNIGHT:

                            if (ga.canPlayKnight(player.getPlayerNumber()))
                            {
                                ga.playKnight();
                                gameList.takeMonitorForGame(gaName);
                                messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, player.getName() + " played a Soldier card."));
                                messageToGameWithMon(gaName, new SOCDevCard(gaName, player.getPlayerNumber(), SOCDevCard.PLAY, SOCDevCardConstants.KNIGHT));
                                messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, player.getPlayerNumber(), true));
                                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, player.getPlayerNumber(), SOCPlayerElement.GAIN, SOCPlayerElement.NUMKNIGHTS, 1));
                                gameList.releaseMonitorForGame(gaName);
                                broadcastGameStats(ga);
                                sendGameState(ga);
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't play a Soldier card now."));
                            }

                            break;

                        case SOCDevCardConstants.ROADS:

                            if (ga.canPlayRoadBuilding(player.getPlayerNumber()))
                            {
                                ga.playRoadBuilding();
                                gameList.takeMonitorForGame(gaName);
                                messageToGameWithMon(gaName, new SOCDevCard(gaName, player.getPlayerNumber(), SOCDevCard.PLAY, SOCDevCardConstants.ROADS));
                                messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, player.getPlayerNumber(), true));
                                messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, player.getName() + " played a Road Building card."));
                                gameList.releaseMonitorForGame(gaName);
                                sendGameState(ga);
                                if (ga.getGameState() == SOCGame.PLACING_FREE_ROAD1)
                                {
                                    c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You may place 2 roads."));
                                }
                                else
                                {
                                    c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You may place your 1 remaining road."));
                                }
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't play a Road Building card now."));
                            }

                            break;

                        case SOCDevCardConstants.DISC:

                            if (ga.canPlayDiscovery(player.getPlayerNumber()))
                            {
                                ga.playDiscovery();
                                gameList.takeMonitorForGame(gaName);
                                messageToGameWithMon(gaName, new SOCDevCard(gaName, player.getPlayerNumber(), SOCDevCard.PLAY, SOCDevCardConstants.DISC));
                                messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, player.getPlayerNumber(), true));
                                messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, player.getName() + " played a Year of Plenty card."));
                                gameList.releaseMonitorForGame(gaName);
                                sendGameState(ga);
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't play a Year of Plenty card now."));
                            }

                            break;

                        case SOCDevCardConstants.MONO:

                            if (ga.canPlayMonopoly(player.getPlayerNumber()))
                            {
                                ga.playMonopoly();
                                gameList.takeMonitorForGame(gaName);
                                messageToGameWithMon(gaName, new SOCDevCard(gaName, player.getPlayerNumber(), SOCDevCard.PLAY, SOCDevCardConstants.MONO));
                                messageToGameWithMon(gaName, new SOCSetPlayedDevCard(gaName, player.getPlayerNumber(), true));
                                messageToGameWithMon(gaName, new SOCGameTextMsg(gaName, SERVERNAME, player.getName() + " played a Monopoly card."));
                                gameList.releaseMonitorForGame(gaName);
                                sendGameState(ga);
                            }
                            else
                            {
                                c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't play a Monopoly card now."));
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
                        c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "It's not your turn."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "discovery pick" message (while playing Discovery card)
     *
     * @param c  the connection that sent the message
     * @param mes  the messsage
     */
    private void handleDISCOVERYPICK(StringConnection c, SOCDiscoveryPick mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
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
                            messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, message.toString()));
                            sendGameState(ga);
                        }
                        else
                        {
                            c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "That is not a legal Year of Plenty pick."));
                        }
                    }
                    else
                    {
                        c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "It's not your turn."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "monopoly pick" message
     *
     * @param c     the connection that sent the message
     * @param mes   the messsage
     */
    private void handleMONOPOLYPICK(StringConnection c, SOCMonopolyPick mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
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

                            String message = monoPlayerName + " monopolized";
                            String resName = null;  // will incl leading ' ', trailing '.'

                            switch (mes.getResource())
                            {
                            case SOCResourceConstants.CLAY:
                                resName = " clay.";

                                break;

                            case SOCResourceConstants.ORE:
                                resName = " ore.";

                                break;

                            case SOCResourceConstants.SHEEP:
                                resName = " sheep.";

                                break;

                            case SOCResourceConstants.WHEAT:
                                resName = " wheat.";

                                break;

                            case SOCResourceConstants.WOOD:
                                resName = " wood.";

                                break;
                            }
                            message += resName;

                            gameList.takeMonitorForGame(gaName);
                            messageToGameExcept(gaName, c, new SOCGameTextMsg(gaName, SERVERNAME, message), false);

                            /**
                             * just send all the player's resource counts for the
                             * monopolized resource
                             */
                            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
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
                            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                            {
                                int picked = monoPicks[i];
                                if (picked == 0)
                                    continue;
                                monoTotal += picked;
                                String viName = ga.getPlayer(i).getName();
                                StringConnection viCon = getConnection(viName);
                                if (viCon != null)
                                    viCon.put(SOCGameTextMsg.toCmd
                                        (gaName, SERVERNAME,
                                         monoPlayerName + "'s Monopoly took your " + picked + resName));
                            }

                            c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You monopolized " + monoTotal + resName));
                            sendGameState(ga);
                        }
                        else
                        {
                            c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "You can't do a Monopoly pick now."));
                        }
                    }
                    else
                    {
                        c.put(SOCGameTextMsg.toCmd(gaName, SERVERNAME, "It's not your turn."));
                    }
                }
                catch (Exception e)
                {
                    D.ebugPrintln("Exception caught - " + e);
                    e.printStackTrace();
                }

                ga.releaseMonitor();
            }
        }
    }

    /**
     * handle "change face" message
     *
     * @param c  the connection
     * @param mes  the message
     */
    private void handleCHANGEFACE(StringConnection c, SOCChangeFace mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                SOCPlayer player = ga.getPlayer((String) c.getData());

                if (player != null)
                {
                    final String gaName = mes.getGame();
                    player.setFaceId(mes.getFaceId());
                    messageToGame(gaName, new SOCChangeFace(gaName, player.getPlayerNumber(), mes.getFaceId()));
                }
            }
        }
    }

    /**
     * handle "set seat lock" message
     *
     * @param c  the connection
     * @param mes  the message
     */
    private void handleSETSEATLOCK(StringConnection c, SOCSetSeatLock mes)
    {
        if (c != null)
        {
            SOCGame ga = gameList.getGameData(mes.getGame());

            if (ga != null)
            {
                SOCPlayer player = ga.getPlayer((String) c.getData());

                if (player != null)
                {
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
            }
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
        StringConnection[] humanConns = new StringConnection[SOCGame.MAXPLAYERS];
        StringConnection[] robotConns = new StringConnection[SOCGame.MAXPLAYERS];
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
            for (int i = SOCGame.MAXPLAYERS-1; i>=0; --i)
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
                for (int i = 0; i < SOCGame.MAXPLAYERS; ++i)
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
     * Mark the end of the option list with {@link SOCGameOptionGetInfo GAMEOPTIONGETINFO}("-").
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
        Vector okeys = mes.getOptionKeys();

        if (okeys == null)
        {
            // received "-", look for newer options (cli is older than us).
            // okeys will be null if nothing is new.
            okeys = SOCGameOption.optionsNewerThanVersion(cliVers, false, null);
            vecIsOptObjs = true;
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
     * Client has been approved to join game; send the entire state of the game to client,
     * send client join event to other players.
     * Assumes NEWGAME (or NEWGAMEWITHOPTIONS) has already been sent out.
     * First message sent to connecting client is JOINGAMEAUTH, unless isReset.
     *<P>
     * Among other messages, player names are sent via SITDOWN, and pieces on board
     * sent by PUTPIECE.  See comments here for further details.
     * The group of messages sent here ends with GAMEMEMBERS, SETTURN and GAMESTATE.
     *<P>
     * @param gameData Game to join
     * @param c        The connection of joining client
     * @param isReset  Game is a board-reset of an existing game
     */
    private void joinGame(SOCGame gameData, StringConnection c, boolean isReset)
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
        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
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

        SOCBoardLayout bl = getBoardLayoutMessage(gameData);
        c.put(bl.toCmd());

        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
        {
            SOCPlayer pl = gameData.getPlayer(i);

            // Send piece info even if player has left the game (pl.getName() == null).
            // This lets them see "their" pieces before sitDown(), if they rejoin at same position.

            Enumeration piecesEnum = pl.getPieces().elements();

            while (piecesEnum.hasMoreElements())
            {
                SOCPlayingPiece piece = (SOCPlayingPiece) piecesEnum.nextElement();

                if (piece.getType() == SOCPlayingPiece.CITY)
                {
                    c.put(SOCPutPiece.toCmd(gameName, i, SOCPlayingPiece.SETTLEMENT, piece.getCoordinates()));
                }

                c.put(SOCPutPiece.toCmd(gameName, i, piece.getType(), piece.getCoordinates()));
            }

            /**
             * send potential settlement list
             */
            Vector psList = new Vector();

            for (int j = 0x23; j <= 0xDC; j++)
            {
                if (pl.isPotentialSettlement(j))
                {
                    psList.addElement(new Integer(j));
                }
            }

            c.put(SOCPotentialSettlements.toCmd(gameName, i, psList));

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

            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.UNKNOWN, pl.getResources().getTotal()));

            c.put(SOCPlayerElement.toCmd(gameName, i, SOCPlayerElement.SET, SOCPlayerElement.NUMKNIGHTS, pl.getNumKnights()));

            int numDevCards = pl.getDevCards().getTotal();

            for (int j = 0; j < numDevCards; j++)
            {
                c.put(SOCDevCard.toCmd(gameName, i, SOCDevCard.ADDOLD, SOCDevCardConstants.UNKNOWN));
            }

            c.put(SOCFirstPlayer.toCmd(gameName, gameData.getFirstPlayer()));

            c.put(SOCDevCardCount.toCmd(gameName, gameData.getNumDevCards()));

            c.put(SOCChangeFace.toCmd(gameName, i, pl.getFaceId()));

            c.put(SOCDiceResult.toCmd(gameName, gameData.getCurrentDice()));
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

        String membersCommand = null;
        gameList.takeMonitorForGame(gameName);

        /**
         * Almost done; send GAMEMEMBERS as a hint to client that we're almost ready for its input.
         * There's no new data in GAMEMEMBERS, because player names have already been sent by
         * the SITDOWN messages above.
         */
        try
        {
            Vector gameMembers = gameList.getMembers(gameName);
            membersCommand = SOCGameMembers.toCmd(gameName, gameMembers);
        }
        catch (Exception e)
        {
            D.ebugPrintln("Exception in handleJOINGAME (gameMembers) - " + e);
        }

        gameList.releaseMonitorForGame(gameName);
        c.put(membersCommand);
        c.put(SOCSetTurn.toCmd(gameName, gameData.getCurrentPlayerNumber()));
        c.put(SOCGameState.toCmd(gameName, gameData.getGameState()));
        D.ebugPrintln("*** " + c.getData() + " joined the game " + gameName
                + " from " + c.host());

        //messageToGame(gameName, new SOCGameTextMsg(gameName, SERVERNAME, n+" joined the game"));
        /**
         * Let everyone else know about the change
         */
        messageToGame(gameName, new SOCJoinGame
            ((String)c.getData(), "", "dummyhost", gameName));
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
        if ((c != null) && (ga != null))
        {
            ga.takeMonitor();

            try
            {
                if (! isReset)
                {
                    // If reset, player is already added and knows if robot.
                    try
                    {
                        ga.addPlayer((String) c.getData(), pn);
                        ga.getPlayer(pn).setRobotFlag(robot);
                    }
                    catch (IllegalStateException e)
                    {
                        // Maybe already seated? (network lag)
                        if (! robot)
                            c.put(SOCGameTextMsg.toCmd(ga.getName(), SERVERNAME, "You cannot sit down here."));
                        ga.releaseMonitor();
                        return;  // <---- Early return: cannot sit down ----
                    }
                }

                /**
                 * if the player can sit, then tell the other clients in the game
                 */
                SOCSitDown sitMessage = new SOCSitDown(ga.getName(), (String) c.getData(), pn, robot);
                messageToGame(ga.getName(), sitMessage);

                D.ebugPrintln("*** sent SOCSitDown message to game ***");

                recordGameEvent(ga.getName(), sitMessage.toCmd());

                Vector requests;
                if (! isReset)
                {
                    requests = (Vector) robotJoinRequests.get(ga.getName());
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
                        robotJoinRequests.remove(ga.getName());
                    }
                }

                broadcastGameStats(ga);

                /**
                 * send all the private information
                 */
                SOCResourceSet resources = ga.getPlayer(pn).getResources();
                messageToPlayer(c, new SOCPlayerElement(ga.getName(), pn, SOCPlayerElement.SET, SOCPlayerElement.CLAY, resources.getAmount(SOCPlayerElement.CLAY)));
                messageToPlayer(c, new SOCPlayerElement(ga.getName(), pn, SOCPlayerElement.SET, SOCPlayerElement.ORE, resources.getAmount(SOCPlayerElement.ORE)));
                messageToPlayer(c, new SOCPlayerElement(ga.getName(), pn, SOCPlayerElement.SET, SOCPlayerElement.SHEEP, resources.getAmount(SOCPlayerElement.SHEEP)));
                messageToPlayer(c, new SOCPlayerElement(ga.getName(), pn, SOCPlayerElement.SET, SOCPlayerElement.WHEAT, resources.getAmount(SOCPlayerElement.WHEAT)));
                messageToPlayer(c, new SOCPlayerElement(ga.getName(), pn, SOCPlayerElement.SET, SOCPlayerElement.WOOD, resources.getAmount(SOCPlayerElement.WOOD)));
                messageToPlayer(c, new SOCPlayerElement(ga.getName(), pn, SOCPlayerElement.SET, SOCPlayerElement.UNKNOWN, resources.getAmount(SOCPlayerElement.UNKNOWN)));

                SOCDevCardSet devCards = ga.getPlayer(pn).getDevCards();

                /**
                 * remove the unknown cards
                 */
                int i;

                for (i = 0; i < devCards.getTotal(); i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.PLAY, SOCDevCardConstants.UNKNOWN));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDNEW, SOCDevCardConstants.KNIGHT));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.ROADS);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDNEW, SOCDevCardConstants.ROADS));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.DISC);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDNEW, SOCDevCardConstants.DISC));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.MONO);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDNEW, SOCDevCardConstants.MONO));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.CAP);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDNEW, SOCDevCardConstants.CAP));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.LIB);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDNEW, SOCDevCardConstants.LIB));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.UNIV);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDNEW, SOCDevCardConstants.UNIV));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.TEMP);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDNEW, SOCDevCardConstants.TEMP));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.TOW);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDNEW, SOCDevCardConstants.TOW));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDOLD, SOCDevCardConstants.KNIGHT));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDOLD, SOCDevCardConstants.ROADS));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDOLD, SOCDevCardConstants.DISC));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDOLD, SOCDevCardConstants.MONO));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.CAP);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDOLD, SOCDevCardConstants.CAP));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.LIB);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDOLD, SOCDevCardConstants.LIB));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.UNIV);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDOLD, SOCDevCardConstants.UNIV));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.TEMP);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDOLD, SOCDevCardConstants.TEMP));
                }

                for (i = 0;
                        i < devCards.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.TOW);
                        i++)
                {
                    messageToPlayer(c, new SOCDevCard(ga.getName(), pn, SOCDevCard.ADDOLD, SOCDevCardConstants.TOW));
                }

                /**
                 * send game state info such as requests for discards
                 */
                sendGameState(ga);

                if ((ga.getCurrentDice() == 7) && ga.getPlayer(pn).getNeedToDiscard())
                {
                    messageToPlayer(c, new SOCDiscardRequest(ga.getName(), ga.getPlayer(pn).getResources().getTotal() / 2));
                }

                /**
                 * send what face this player is using
                 */
                messageToGame(ga.getName(), new SOCChangeFace(ga.getName(), pn, ga.getPlayer(pn).getFaceId()));
            }
            catch (Throwable e)
            {
                D.ebugPrintln("Exception caught - " + e);
                e.printStackTrace();
            }

            ga.releaseMonitor();
        }
    }

    /**
     * The current player is stealing from another player.
     * Send messages saying what was stolen.
     *
     * @param ga  the game
     * @param pe  the perpetrator
     * @param vi  the the victim
     * @param rsrc  type of resource stolen, as in SOCResourceConstants
     */
    protected void reportRobbery(SOCGame ga, SOCPlayer pe, SOCPlayer vi, int rsrc)
    {
        if (ga != null)
        {
            final String gaName = ga.getName();
            final String peName = pe.getName();
            final String viName = vi.getName();
            final int pePN = pe.getPlayerNumber();
            final int viPN = vi.getPlayerNumber();
            StringBuffer mes1 = new StringBuffer("You stole ");
            StringBuffer mes2 = new StringBuffer(peName);  mes2.append(" stole ");
            SOCPlayerElement gainRsrc = null;
            SOCPlayerElement loseRsrc = null;
            SOCPlayerElement gainUnknown;
            SOCPlayerElement loseUnknown;

            switch (rsrc)
            {
            case SOCResourceConstants.CLAY:
                mes1.append("a clay ");
                mes2.append("a clay ");
                gainRsrc = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, SOCPlayerElement.CLAY, 1);
                loseRsrc = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, SOCPlayerElement.CLAY, 1);

                break;

            case SOCResourceConstants.ORE:
                mes1.append("an ore ");
                mes2.append("an ore ");
                gainRsrc = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, SOCPlayerElement.ORE, 1);
                loseRsrc = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, SOCPlayerElement.ORE, 1);

                break;

            case SOCResourceConstants.SHEEP:
                mes1.append("a sheep ");
                mes2.append("a sheep ");
                gainRsrc = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, SOCPlayerElement.SHEEP, 1);
                loseRsrc = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, SOCPlayerElement.SHEEP, 1);

                break;

            case SOCResourceConstants.WHEAT:
                mes1.append("a wheat ");
                mes2.append("a wheat ");
                gainRsrc = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, SOCPlayerElement.WHEAT, 1);
                loseRsrc = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, SOCPlayerElement.WHEAT, 1);

                break;

            case SOCResourceConstants.WOOD:
                mes1.append("a wood ");
                mes2.append("a wood ");
                gainRsrc = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, 1);
                loseRsrc = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, SOCPlayerElement.WOOD, 1);

                break;
            }

            mes1.append("resource from ");  mes1.append(viName);  mes1.append('.');
            mes2.append("resource from you.");

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
            Vector exceptions = new Vector(2);
            exceptions.addElement(peCon);
            exceptions.addElement(viCon);
            gainUnknown = new SOCPlayerElement(gaName, pePN, SOCPlayerElement.GAIN, SOCPlayerElement.UNKNOWN, 1);
            loseUnknown = new SOCPlayerElement(gaName, viPN, SOCPlayerElement.LOSE, SOCPlayerElement.UNKNOWN, 1);
            messageToGameExcept(gaName, exceptions, gainUnknown, true);
            messageToGameExcept(gaName, exceptions, loseUnknown, true);

            /**
             * send the text messages
             */
            messageToPlayer(peCon, new SOCGameTextMsg(gaName, SERVERNAME, mes1.toString()));
            messageToPlayer(viCon, new SOCGameTextMsg(gaName, SERVERNAME, mes2.toString()));
            messageToGameExcept(gaName, exceptions, new SOCGameTextMsg(gaName, SERVERNAME, peName + " stole a resource from " + viName), true);
        }
    }

    /**
     * send the current state of the game with a message.
     * Assumes current player does not change during this state.
     * If we send a text message to prompt the new player to roll,
     * also sends a RollDicePrompt data message.
     * If the client is too old (1.0.6), it will ignore the prompt.
     *
     * @param ga  the game
     * 
     * @see #sendGameState(SOCGame, boolean)
     */
    protected void sendGameState(SOCGame ga)
    {
        sendGameState(ga, true);
    }
    
    /**
     * send the current state of the game with a message.
     * Note that the current (or new) player number is not sent here.
     * If game is now OVER, send appropriate messages.
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
            messageToGame(gname, new SOCGameTextMsg(gname, SERVERNAME, "It's " + player.getName() + "'s turn to build a settlement."));

            break;

        case SOCGame.START1B:
        case SOCGame.START2B:
            messageToGame(gname, new SOCGameTextMsg(gname, SERVERNAME, "It's " + player.getName() + "'s turn to build a road."));

            break;

        case SOCGame.PLAY:
            messageToGame(gname, new SOCGameTextMsg(gname, SERVERNAME, "It's " + player.getName() + "'s turn to roll the dice."));
            promptedRoll = true;
            if (sendRollPrompt)
                messageToGame(gname, new SOCRollDicePrompt (gname, player.getPlayerNumber()));
                
            break;

        case SOCGame.WAITING_FOR_DISCARDS:

            int count = 0;
            String message = "error at sendGameState()";
            String[] names = new String[SOCGame.MAXPLAYERS];

            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                if (ga.getPlayer(i).getNeedToDiscard())
                {
                    names[count] = ga.getPlayer(i).getName();
                    count++;
                }
            }

            if (count == 1)
            {
                message = names[0] + " needs to discard.";
            }
            else if (count == 2)
            {
                message = names[0] + " and " + names[1] + " need to discard.";
            }
            else if (count > 2)
            {
                message = names[0];

                for (int i = 1; i < (count - 1); i++)
                {
                    message += (", " + names[i]);
                }

                message += (" and " + names[count - 1] + " need to discard.");
            }

            messageToGame(gname, new SOCGameTextMsg(gname, SERVERNAME, message));

            break;

        case SOCGame.PLACING_ROBBER:
            messageToGame(gname, new SOCGameTextMsg(gname, SERVERNAME, player.getName() + " will move the robber."));

            break;

        case SOCGame.WAITING_FOR_CHOICE:

            /**
             * get the choices from the game
             */
            boolean[] choices = new boolean[SOCGame.MAXPLAYERS];

            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                choices[i] = false;
            }

            Enumeration plEnum = ga.getPossibleVictims().elements();

            while (plEnum.hasMoreElements())
            {
                SOCPlayer pl = (SOCPlayer) plEnum.nextElement();
                choices[pl.getPlayerNumber()] = true;
            }

            /**
             * ask the current player to choose a player to steal from
             */
            StringConnection con = getConnection
                (ga.getPlayer(ga.getCurrentPlayerNumber()).getName());
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
     *  If game is OVER, send messages reporting winner, final score,
     *  and each player's victory-point cards.
     *  Also give stats on game length, and on each player's connect time.
     *  If player has finished more than 1 game since connecting, send win-loss count.
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

        if (winPl.getTotalVP() < SOCGame.VP_WINNER)
        {
            // Should not happen: By rules FAQ, only current player can be winner.
            // This is fallback code.
            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                winPl = ga.getPlayer(i);        
                if (winPl.getTotalVP() >= SOCGame.VP_WINNER)
                {
                    break;
                }
            }
        }
        msg = ">>> " + winPl.getName() + " has won the game with " + winPl.getTotalVP() + " points.";
        messageToGameUrgent(gname, msg);
        
        /// send a message with the revealed final scores
        {
            int[] scores = new int[SOCGame.MAXPLAYERS];
            boolean[] isRobot = new boolean[SOCGame.MAXPLAYERS];
            for (int i = 0; i < SOCGame.MAXPLAYERS; ++i)
            {
                scores[i] = ga.getPlayer(i).getTotalVP();
                isRobot[i] = ga.getPlayer(i).isRobot();
            }
            messageToGame(gname, new SOCGameStats(gname, scores, isRobot));
        }
        
        ///
        /// send a message saying what VP cards each player has
        ///
        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
        {
            SOCPlayer pl = ga.getPlayer(i);
            SOCDevCardSet devCards = pl.getDevCards();

            if (devCards.getNumVPCards() > 0)
            {
                msg = pl.getName() + " has";
                int vpCardCount = 0;

                for (int devCardType = SOCDevCardConstants.CAP;
                        devCardType < SOCDevCardConstants.UNKNOWN;
                        devCardType++)
                {
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

                messageToGame(gname, new SOCGameTextMsg(gname, SERVERNAME, msg));

            }  // if devcards
        }  // for each player

        /**
         * send game-length and connect-length messages, possibly win-loss count.
         */
        {
            Date now = new Date();
            Date gstart = ga.getStartTime();
            String gLengthMsg;
            if (gstart != null)
            {                
                long gameSeconds = ((now.getTime() - gstart.getTime())+500L) / 1000L;
                long gameMinutes = gameSeconds/60L;
                gameSeconds = gameSeconds % 60L;
                if (gameSeconds == 0)
                    gLengthMsg = "This game took " + gameMinutes + " minutes.";
                else
                    gLengthMsg = "This game took " + gameMinutes + " minutes "
                        + gameSeconds + " seconds.";
                messageToGame(gname, new SOCGameTextMsg(gname, SERVERNAME, gLengthMsg));

                // Ignore possible "1 minutes"; that game is too short to worry about.
            } else {
                gLengthMsg = null;
            }

            /**
             * Update each player's win-loss count for this session.
             * Tell each player how long they've been connected.
             */
            String connMsg;
            if ((strSocketName != null) && (strSocketName.equals(PRACTICE_STRINGPORT)))
                connMsg = "You have been practicing ";
            else
                connMsg = "You have been connected ";

            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                if (ga.isSeatVacant(i))
                    continue;

                SOCPlayer pl = ga.getPlayer(i);
                StringConnection plConn = (StringConnection) conns.get(pl.getName());
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
                    continue;  // Don't bother to send win-loss or timing stats to robots

                if (plConn != null)
                {
                    long connTime = plConn.getConnectTime().getTime();
                    long connMinutes = (((now.getTime() - connTime)) + 30000L) / 60000L;                    
                    StringBuffer cLengthMsg = new StringBuffer(connMsg);
                    cLengthMsg.append(connMinutes);
                    if (connMinutes == 1)
                        cLengthMsg.append(" minute.");
                    else
                        cLengthMsg.append(" minutes.");
                    messageToPlayer(plConn, new SOCGameTextMsg(gname, SERVERNAME, cLengthMsg.toString()));

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
                        messageToPlayer(plConn, new SOCGameTextMsg(gname, SERVERNAME, winLossMsg.toString()));
                    }
                }
            }  // for each player

        }  // send game timing stats, win-loss stats
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
        if (ga != null)
        {
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
            messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, message.toString()));
        }
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
        if (ga != null)
        {
            final String gaName = ga.getName();
            final int    cpn    = ga.getCurrentPlayerNumber();
            StringBuffer message = new StringBuffer (ga.getPlayer(cpn).getName());
            message.append(" traded ");
            reportRsrcGainLoss(gaName, give, true, cpn, -1, message, null);
            message.append(" for ");
            reportRsrcGainLoss(gaName, get, false, cpn, -1, message, null);

            if ((give.getTotal() / get.getTotal()) == 4)
            {
                message.append(" from the bank.");  // 4:1 trade
            }
            else
            {
                message.append(" from a port.");    // 3:1 or 2:1 trade
            }

            messageToGame(gaName, new SOCGameTextMsg(gaName, SERVERNAME, message.toString()));
        }
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
     *                Resource type {@link SOCResourceConstants#UNKNOWN} is ignored.
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

        final int cl = rset.getAmount(SOCResourceConstants.CLAY);
        final int or = rset.getAmount(SOCResourceConstants.ORE);
        final int sh = rset.getAmount(SOCResourceConstants.SHEEP);
        final int wh = rset.getAmount(SOCResourceConstants.WHEAT);
        final int wo = rset.getAmount(SOCResourceConstants.WOOD);

        boolean needComma = false;  // Has a resource already been appended to message?

        gameList.takeMonitorForGame(gaName);

        if (cl > 0)
        {
            if (playerConn != null)
                messageToPlayer(playerConn, new SOCPlayerElement(gaName, mainPlayer, losegain, SOCPlayerElement.CLAY, cl));
            else
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, mainPlayer, losegain, SOCPlayerElement.CLAY, cl));
            if (tradingPlayer != -1)
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, tradingPlayer, gainlose, SOCPlayerElement.CLAY, cl));
            if (message != null)
            {
                message.append(cl);
                message.append(" clay");
                needComma = true;
            }
        }

        if (or > 0)
        {
            if (playerConn != null)
                messageToPlayer(playerConn, new SOCPlayerElement(gaName, mainPlayer, losegain, SOCPlayerElement.ORE, or));
            else
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, mainPlayer, losegain, SOCPlayerElement.ORE, or));
            if (tradingPlayer != -1)
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, tradingPlayer, gainlose, SOCPlayerElement.ORE, or));
            if (message != null)
            {
                if (needComma)
                    message.append(", ");
                message.append(or);
                message.append(" ore");
                needComma = true;
            }
        }

        if (sh > 0)
        {
            if (playerConn != null)
                messageToPlayer(playerConn, new SOCPlayerElement(gaName, mainPlayer, losegain, SOCPlayerElement.SHEEP, sh));
            else
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, mainPlayer, losegain, SOCPlayerElement.SHEEP, sh));
            if (tradingPlayer != -1)
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, tradingPlayer, gainlose, SOCPlayerElement.SHEEP, sh));
            if (message != null)
            {
                if (needComma)
                    message.append(", ");
                message.append(sh);
                message.append(" sheep");
                needComma = true;
            }
        }

        if (wh > 0)
        {
            if (playerConn != null)
                messageToPlayer(playerConn, new SOCPlayerElement(gaName, mainPlayer, losegain, SOCPlayerElement.WHEAT, wh));
            else
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, mainPlayer, losegain, SOCPlayerElement.WHEAT, wh));
            if (tradingPlayer != -1)
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, tradingPlayer, gainlose, SOCPlayerElement.WHEAT, wh));
            if (message != null)
            {
                if (needComma)
                    message.append(", ");
                message.append(wh);
                message.append(" wheat");
                needComma = true;
            }
        }

        if (wo > 0)
        {
            if (playerConn != null)
                messageToPlayer(playerConn, new SOCPlayerElement(gaName, mainPlayer, losegain, SOCPlayerElement.WOOD, wo));
            else
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, mainPlayer, losegain, SOCPlayerElement.WOOD, wo));
            if (tradingPlayer != -1)
                messageToGameWithMon(gaName, new SOCPlayerElement(gaName, tradingPlayer, gainlose, SOCPlayerElement.WOOD, wo));
            if (message != null)
            {
                if (needComma)
                    message.append(", ");
                message.append(wo);
                message.append(" wood");
            }
        }

        gameList.releaseMonitorForGame(gaName);
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
        if ((c != null) && (ga != null))
        {
            try
            {
                if (ga.getCurrentPlayerNumber() != ga.getPlayer((String) c.getData()).getPlayerNumber())
                {
                    return false;
                }
                else
                {
                    return true;
                }
            }
            catch (Exception e)
            {
                return false;
            }
        }
        else
        {
            return false;
        }
    }

    /**
     * do the stuff you need to do to start a game
     *
     * @param ga  the game
     */
    protected void startGame(SOCGame ga)
    {
        if (ga != null)
        {
            final String gaName = ga.getName();

            numberOfGamesStarted++;
            ga.startGame();
            gameList.takeMonitorForGame(gaName);

            /**
             * send the board layout
             */
            SOCBoardLayout bl = getBoardLayoutMessage(ga);
            messageToGameWithMon(gaName, bl);

            /**
             * send the player info
             */            
            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                if (! ga.isSeatVacant(i))
                {
                    SOCPlayer pl = ga.getPlayer(i);
                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.ROADS, pl.getNumPieces(SOCPlayingPiece.ROAD)));
                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.SETTLEMENTS, pl.getNumPieces(SOCPlayingPiece.SETTLEMENT)));
                    messageToGameWithMon(gaName, new SOCPlayerElement(gaName, i, SOCPlayerElement.SET, SOCPlayerElement.CITIES, pl.getNumPieces(SOCPlayingPiece.CITY)));
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
    }

    /**
     * Reset the board, to a copy with same players but new layout.
     * Here's the general outline: Step 1 and 2 are done immediately here,
     * steps 3-n are done (after robots are dismissed) within
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
        for (int pn = 0; pn < SOCGame.MAXPLAYERS; ++pn)
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
     * Complete steps 2-n of the board-reset process
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
         * 2. Send messages as if each human player has clicked "join" (except JoinGameAuth)
         */
        for (int pn = 0; pn < SOCGame.MAXPLAYERS; ++pn)
        {
            if (huConns[pn] != null)
                joinGame(reGame, huConns[pn], true);
        }

        /**
         * 3. Send as if each human player has clicked "sit here"
         */
        for (int pn = 0; pn < SOCGame.MAXPLAYERS; ++pn)
        {
            if (huConns[pn] != null)
                sitDown(reGame, huConns[pn], pn, false /* isRobot*/, true /*isReset */ );
        }

        /**
         * 4a. If no robots, send to game as if someone else has
         *     clicked "start game", and set up state to begin game play.
         */
        if (! reBoard.hadRobots)
        {
            startGame (reGame);

        /**
         * 4b. If there are robots, set up wait-request queue
         *     (robotJoinRequests) and ask robots to re-join.
         *     Game will wait for robots to send JOINGAME and SITDOWN,
         *     as they do when joining a newly created game.
         *     Once all robots have re-joined, the game will begin.
         */
        }
        else
        {
            reGame.setGameState(SOCGame.READY);
            readyGameAskRobotsJoin
              (reGame, gameWasOverAtReset ? null : reBoard.robotConns);
        }

        // All set.
    }  // resetBoardAndNotify_finish

    /**
     * send whose turn it is. Optionally also send a prompt to roll.
     * If the client is too old (1.0.6), it will ignore the prompt.
     *
     * @param ga  the game
     * @param sendRollPrompt  whether to send a RollDicePrompt message afterwards
     */
    private void sendTurn(SOCGame ga, boolean sendRollPrompt)
    {
        if (ga != null)
        {
            String gname = ga.getName();
            int pn = ga.getCurrentPlayerNumber();   

            messageToGame(gname, new SOCSetPlayedDevCard(gname, pn, false));

            SOCTurn turnMessage = new SOCTurn(gname, pn);
            messageToGame(gname, turnMessage);
            recordGameEvent(gname, turnMessage.toCmd());

            if (sendRollPrompt)
                messageToGame(gname, new SOCRollDicePrompt(gname, pn));
        }
    }

    /**
     * put together the SOCBoardLayout message
     *
     * @param  ga   the game
     * @return      a board layout message
     */
    private SOCBoardLayout getBoardLayoutMessage(SOCGame ga)
    {
        SOCBoard board;
        int[] hexes;
        int[] numbers;
        int robber;

        board = ga.getBoard();
        hexes = board.getHexLayout();
        numbers = board.getNumberLayout();
        robber = board.getRobberHex();

        return (new SOCBoardLayout(ga.getName(), hexes, numbers, robber));
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
     * record the scores in the database
     *
     * @param ga  the game
     */
    protected void storeGameScores(SOCGame ga)
    {
        if (ga != null)
        {
            //D.ebugPrintln("allOriginalPlayers for "+ga.getName()+" : "+ga.allOriginalPlayers());
            if ((ga.getGameState() == SOCGame.OVER) && (ga.allOriginalPlayers()))
            {
                //if (ga.allOriginalPlayers()) {
                try
                {
                    SOCDBHelper.saveGameScores(ga.getName(), ga.getPlayer(0).getName(), ga.getPlayer(1).getName(), ga.getPlayer(2).getName(), ga.getPlayer(3).getName(), (short) ga.getPlayer(0).getTotalVP(), (short) ga.getPlayer(1).getTotalVP(), (short) ga.getPlayer(2).getTotalVP(), (short) ga.getPlayer(3).getTotalVP(), ga.getStartTime());
                }
                catch (SQLException sqle)
                {
                    System.err.println("Error saving game scores in db.");
                }
            }
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
    protected void giveResources(String mes, SOCGame game)
    {
        StringTokenizer st = new StringTokenizer(mes.substring(6));
        int[] resources = new int[SOCResourceConstants.WOOD + 1];
        int resourceType = SOCResourceConstants.CLAY;
        String name = "";

        while (st.hasMoreTokens())
        {
            String token = st.nextToken();

            if (resourceType <= SOCResourceConstants.WOOD)
            {
                resources[resourceType] = Integer.parseInt(token);
                resourceType++;
            }
            else
            {
                name = token;

                break;
            }
        }

        SOCResourceSet rset = game.getPlayer(name).getResources();
        int pnum = game.getPlayer(name).getPlayerNumber();
        String outMes = "### " + name + " gets";

        for (resourceType = SOCResourceConstants.CLAY;
                resourceType <= SOCResourceConstants.WOOD; resourceType++)
        {
            rset.add(resources[resourceType], resourceType);
            outMes += (" " + resources[resourceType]);

            switch (resourceType)
            {
            case SOCResourceConstants.CLAY:
                messageToGame(game.getName(), new SOCPlayerElement(game.getName(), pnum, SOCPlayerElement.GAIN, SOCPlayerElement.CLAY, resources[resourceType]));

                break;

            case SOCResourceConstants.ORE:
                messageToGame(game.getName(), new SOCPlayerElement(game.getName(), pnum, SOCPlayerElement.GAIN, SOCPlayerElement.ORE, resources[resourceType]));

                break;

            case SOCResourceConstants.SHEEP:
                messageToGame(game.getName(), new SOCPlayerElement(game.getName(), pnum, SOCPlayerElement.GAIN, SOCPlayerElement.SHEEP, resources[resourceType]));

                break;

            case SOCResourceConstants.WHEAT:
                messageToGame(game.getName(), new SOCPlayerElement(game.getName(), pnum, SOCPlayerElement.GAIN, SOCPlayerElement.WHEAT, resources[resourceType]));

                break;

            case SOCResourceConstants.WOOD:
                messageToGame(game.getName(), new SOCPlayerElement(game.getName(), pnum, SOCPlayerElement.GAIN, SOCPlayerElement.WOOD, resources[resourceType]));

                break;
            }
        }

        messageToGame(game.getName(), new SOCGameTextMsg(game.getName(), SERVERNAME, outMes));
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
     *
     * @see #GAME_EXPIRE_WARN_MINUTES
     * @see SOCGameTimeoutChecker#run()
     */
    public void checkForExpiredGames()
    {
        Vector expired = new Vector();

        gameList.takeMonitor();
        
        // Add 2 minutes because of coarse 5-minute granularity in SOCGameTimeoutChecker.run()
        long warn_ms = (2 + GAME_EXPIRE_WARN_MINUTES) * 60L * 1000L; 

        try
        {
            final long currentTimeMillis = System.currentTimeMillis();
            for (Enumeration k = gameList.getGamesData(); k.hasMoreElements();)
            {
                SOCGame gameData = (SOCGame) k.nextElement();
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
        for (Enumeration ex = expired.elements(); ex.hasMoreElements();)
        {
            String ga = (String) ex.nextElement();
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

    /** this is a debugging command that gives a dev card to a player.
     *  <PRE> dev: cardtype player </PRE>
     *  For card-types numbers, see {@link SOCDevCardConstants}
     */
    protected void giveDevCard(String mes, SOCGame game)
    {
        StringTokenizer st = new StringTokenizer(mes.substring(5));
        String name = "";
        int cardType = -1;

        while (st.hasMoreTokens())
        {
            String token = st.nextToken();

            if (cardType < 0)
            {
                cardType = Integer.parseInt(token);
            }
            else
            {
                name = token;

                break;
            }
        }

        SOCDevCardSet dcSet = game.getPlayer(name).getDevCards();
        dcSet.add(1, SOCDevCardSet.NEW, cardType);

        int pnum = game.getPlayer(name).getPlayerNumber();
        String outMes = "### " + name + " gets a " + cardType + " card.";
        messageToGame(game.getName(), new SOCDevCard(game.getName(), pnum, SOCDevCard.DRAW, cardType));
        messageToGame(game.getName(), new SOCGameTextMsg(game.getName(), SERVERNAME, outMes));
    }

    /**
     * Quick-and-dirty command line parsing of game options
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
     * @param args args as passed to main
     * @return args with any dashed arguments removed, or null for argument error.
     * @since 1.1.07
     */
    public static String[] parseCmdline_DashedArgs(String[] args)
    {
        int aidx = 0;
        while ((aidx < args.length) && (args[aidx].startsWith("-")))
        {
            String arg = args[aidx];

            if (arg.equals("-V") || arg.equalsIgnoreCase("--version"))
            {
                printVersionText();
            }
            else if (arg.equalsIgnoreCase("-h") || arg.equals("?") || arg.equalsIgnoreCase("--help"))
            {
                printUsage(true);
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
            } else {
                System.err.println("Unknown argument: " + arg);
            }
            ++aidx;
        }

        // Done parsing.  Return the remaining args.
        if (aidx == 0)
        {
            return args;
        } else {
            final int numargs = args.length - aidx;
            String[] newargs = new String[numargs];
            System.arraycopy(args, aidx, newargs, 0, numargs);
            return newargs;
        }
    }

    /**
     * Print command line parameter information, including options ("--" / "-").
     * Long format gives details and also calls {@link #printVersionText()} beforehand.
     * @since 1.1.07
     */
    public static void printUsage(final boolean longFormat)
    {
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
        Hashtable allopts = SOCGameOption.getAllKnownOptions();
        System.err.println("-- Current default game options: --");
        for (Enumeration e = allopts.keys(); e.hasMoreElements(); )
        {
            String okey = (String) e.nextElement();
            SOCGameOption opt = (SOCGameOption) allopts.get(okey);
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
            if ((opt.optType == SOCGameOption.OTYPE_ENUM) || (opt.optType == SOCGameOption.OTYPE_ENUMBOOL))
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
                ("*** Note: Client version " + optsVers
                 + " or higher is required for these game options. ***");
            System.err.println
                ("          Games created with different options may not have this restriction.");
        }
    }

    /**
     * Starting the server from the command line
     *
     * @param args  arguments: port number, etc
     */
    static public void main(String[] args)
    {
        int port;
        int mc;

        if ((args.length >=1) && (args[0].startsWith("-")))
        {
            args = parseCmdline_DashedArgs(args);
            if (args == null)
            {
                printUsage(false);
                return;
            }
        }

        if (args.length < 4)
        {
            printUsage(false);
            return;
        }

        try
        {
            port = Integer.parseInt(args[0]);
            mc = Integer.parseInt(args[1]);
        }
        catch (Exception e)
        {
            printUsage(false);
            return;
        }

        SOCServer server = new SOCServer(port, mc, args[2], args[3]);
        server.setPriority(5);
        server.start();

        // SOCServer constructor will also print game options if we've set them on
        // commandline, or if any option defaults require a minimum client version.
    }
}
