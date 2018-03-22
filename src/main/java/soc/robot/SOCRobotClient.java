/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2018 Jeremy D Monin <jeremy@nand.net>
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
package soc.robot;

import soc.baseclient.SOCDisplaylessPlayerClient;

import soc.disableDebug.D;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;

import soc.message.*;

import soc.server.genericServer.StringServerSocket;

import soc.util.CappedQueue;
import soc.util.CutoffExceededException;
import soc.util.SOCRobotParameters;
import soc.util.Version;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.net.Socket;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;


/**
 * This is a robot client that can play Settlers of Catan.
 *<P>
 * Once connected, messages from the server are processed in {@link #treat(SOCMessage)}.
 * For each game this robot client plays, there is a {@link SOCRobotBrain}.
 *<P>
 * The built-in robots must be the same version as the server, to simplify things.
 * Third-party bots might be based on this code and be other versions, to simplify their code.
 *<P>
 * The {@link soc.message.SOCImARobot IMAROBOT} connect message gives the bot's class name and
 * a required security cookie, which is passed into the robot client constructor and which must
 * match the server's generated cookie. You can set the server's cookie by setting the
 * server's {@code jsettlers.bots.cookie} parameter, or view it by setting {@code jsettlers.bots.showcookie},
 * when starting the server.
 *<P>
 * Once a bot has connected to the server, it waits to be asked to join games via
 * {@link SOCBotJoinGameRequest BOTJOINREQUEST} messages. When it receives that
 * message type, the bot replies with {@link SOCJoinGame JOINGAME} and the server
 * responds with {@link SOCJoinGameAuth JOINGAMEAUTH}. That message handler creates
 * a {@link SOCRobotBrain} to play the game it is joining.
 *
 *<H4>Debugging</H4>
 * Several bot debug messages are available by sending text messages from other players
 * with certain keywords. See {@link #handleGAMETEXTMSG_debug(SOCGameTextMsg)} for details.
 *
 *<H4>Third-Party Bots</H4>
 * Third-party robot clients can be built from scratch, or extend this class and/or {@link SOCRobotBrain}.
 * If extending this class, please remember to:
 *<UL>
 * <LI> Update {@link #rbclass} value before calling {@link #init()}
 * <LI> Override {@link #createBrain(SOCRobotParameters, SOCGame, CappedQueue)}
 *      to provide your subclass of {@link SOCRobotBrain}
 *</UL>
 * See {@link soc.robot.sample3p.Sample3PClient} for a trivial example subclass.
 *
 *<H4>I18N</H4>
 * The bot ignores the contents of all {@link SOCGameServerText} messages and has no locale.
 * If robot debug commands ({@link SOCGameTextMsg}) are sent to the bot, its responses to the server are in English.
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
     * For server testing, system property {@code "jsettlers.bots.test.quit_at_joinreq"} to
     * randomly disconnect from the server when asked to join a game. If set, value is
     * the disconnect percentage 0-100.
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_BOTS_TEST_QUIT_AT_JOINREQ = "jsettlers.bots.test.quit_at_joinreq";

    /**
     * For server testing, random disconnect percentage from property
     * {@link #PROP_JSETTLERS_BOTS_TEST_QUIT_AT_JOINREQ}. Defaults to 0.
     * @since 2.0.00
     */
    private static int testQuitAtJoinreqPercent = 0;

    /**
     * For debugging/regression testing, randomly pause responding
     * for several seconds, to simulate a "stuck" robot brain.
     *<P>
     *<b>Note:</b> This debugging tool is not scalable to many simultaneous games,
     * because it delays all messages, not just ones for a specific game / brain,
     * and it won't be our turn in each of those games.
     *<P>
     * Because of the limited scope, currently there is no way to enable this
     * debug flag at runtime; the value must be edited here in the source.
     *
     * @see #DEBUGRANDOMPAUSE_FREQ
     * @see #debugRandomPauseActive
     * @since 1.1.11
     */
    private static boolean debugRandomPause = false;  // set true to use this debug type

    /**
     * Is {@link #debugRandomPause} currently in effect for this client?
     * If so, this flag becomes {@code true} while pausing.
     * When true, store messages into {@link #debugRandomPauseQueue} instead of
     * sending them to {@link #robotBrains} immediately.
     * The pause goes on until {@link #debugRandomPauseUntil} arrives
     * and then {@code debugRandomPauseActive} becomes {@code false}
     * until the next random float below {@link #DEBUGRANDOMPAUSE_FREQ}.
     * This is all handled within {@link #treat(SOCMessage)}.
     * @since 1.1.11
     */
    private boolean debugRandomPauseActive = false;

    /**
     * When {@link #debugRandomPauseActive} is true, store incoming messages
     * from the server into this queue until {@link #debugRandomPauseUntil}.
     * Initialized in {@link #treat(SOCMessage)}.
     * @since 1.1.11
     */
    private Vector<SOCMessage> debugRandomPauseQueue = null;

    /**
     * When {@link #debugRandomPauseActive} is true, resume at this time;
     * same format as {@link System#currentTimeMillis()}.
     * @see #DEBUGRANDOMPAUSE_SECONDS
     * @since 1.1.11
     */
    private long debugRandomPauseUntil;

    /**
     * When {@link #debugRandomPause} is true but not {@link #debugRandomPauseActive},
     * frequency of activating it; checked for each non-{@link SOCGameTextMsg}
     * and non-{@link SOCGameServerText} message received during our own turn.
     * @since 1.1.11
     */
    private static final double DEBUGRANDOMPAUSE_FREQ = .04;  // 4%

    /**
     * When {@link #debugRandomPauseActive} is activated, pause this many seconds
     * before continuing.
     * @see #debugRandomPauseUntil
     */
    private static final int DEBUGRANDOMPAUSE_SECONDS = 12;

    /**
     * Robot class, to be reported to the server when connecting and
     * sending our {@link SOCImARobot} message. Defaults to
     * {@link SOCImARobot#RBCLASS_BUILTIN}: Third-party bots
     * should update this field before calling {@link #init()}.
     * @since 2.0.00
     */
    protected String rbclass = SOCImARobot.RBCLASS_BUILTIN;

    /**
     * The security cookie value; required by server v1.1.19 and higher.
     * @since 1.1.19
     */
    private String cookie = null;

    /**
     * the thread that reads incoming messages
     */
    private Thread readerRobot;

    /**
     * the current robot parameters for robot brains
     */
    protected SOCRobotParameters currentRobotParameters;

    /**
     * the robot's "brains", 1 for each game this robot is currently playing.
     * @see SOCDisplaylessPlayerClient#games
     */
    protected Hashtable<String, SOCRobotBrain> robotBrains = new Hashtable<String, SOCRobotBrain>();

    /**
     * the message queues for the different brains
     */
    protected Hashtable<String, CappedQueue<SOCMessage>> brainQs = new Hashtable<String, CappedQueue<SOCMessage>>();

    /**
     * a table of requests from the server to sit at games
     */
    private Hashtable<String, Integer> seatRequests = new Hashtable<String, Integer>();

    /**
     * options for all games on the server we've been asked to join.
     * Some games may have no options, so will have no entry here,
     * although they will have an entry in {@link #games} once joined.
     * Key = game name, Value = map of game's {@link SOCGameOption}s.
     * Entries are added in {@link #handleBOTJOINGAMEREQUEST(SOCBotJoinGameRequest)}.
     * Since the robot and server are the same version, the
     * set of "known options" will always be in sync.
     */
    protected Hashtable<String, Map<String, SOCGameOption>> gameOptions
        = new Hashtable<String, Map<String, SOCGameOption>>();

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
    protected final long startTime;

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
     * @param co  cookie for robot connections to server
     */
    public SOCRobotClient(final String h, final int p, final String nn, final String pw, final String co)
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
        cookie = co;
        strSocketName = null;

        String val = System.getProperty(PROP_JSETTLERS_BOTS_TEST_QUIT_AT_JOINREQ);
        if (val != null)
            try
            {
                testQuitAtJoinreqPercent = Integer.parseInt(val);
            }
            catch (NumberFormatException e) {}
    }

    /**
     * Constructor for connecting to a local game (practice) on a local stringport.
     *
     * @param s    the stringport that the server listens on
     * @param nn   nickname for robot
     * @param pw   password for robot
     * @param co   cookie for robot connections to server
     */
    public SOCRobotClient(final String s, final String nn, final String pw, final String co)
    {
        this(null, 0, nn, pw, co);
        strSocketName = s;
    }

    /**
     * Initialize the robot player; connect to server, send first messages
     * including our version and {@link #rbclass}.
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
                sLocal = StringServerSocket.connectTo(strSocketName);
            }
            connected = true;
            readerRobot = new Thread(this);
            readerRobot.start();

            //resetThread = new SOCRobotResetThread(this);
            //resetThread.start();
            put(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum(), null));
            put(SOCImARobot.toCmd(nickname, cookie, rbclass));
        }
        catch (Exception e)
        {
            ex = e;
            System.err.println("Could not connect to the server: " + ex);
        }
    }

    /**
     * Disconnect and then try to reconnect.
     * If the reconnect fails, will retry a maximum of 3 times.
     * If those attempts all fail, {@link #ex} is set. Otherwise {@code ex} is null when method returns.
     */
    public void disconnectReconnect()
    {
        D.ebugPrintln("(*)(*)(*)(*)(*)(*)(*) disconnectReconnect()");
        ex = null;

        for (int attempt = 3; attempt > 0; --attempt)
        {
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
                    sLocal = StringServerSocket.connectTo(strSocketName);
                }
                connected = true;
                readerRobot = new Thread(this);
                readerRobot.start();

                //resetThread = new SOCRobotResetThread(this);
                //resetThread.start();
                put(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum(), null));
                put(SOCImARobot.toCmd(nickname, cookie, SOCImARobot.RBCLASS_BUILTIN));

                break;  // <--- Exit attempt-loop ---
            }
            catch (Exception e)
            {
                ex = e;
                System.err.println("disconnectReconnect error: " + ex);
                if (attempt > 0)
                    System.err.println("-> Retrying");
            }
        }

        if (! connected)
        {
            // Couldn't reconnect. Shut down active games' brains.
            for (SOCRobotBrain rb : robotBrains.values())
                rb.kill();
        }
    }

    /**
     * Factory method for creating a new {@link SOCRobotBrain}.
     *<P>
     * Third-party clients can override this method to use this client with
     * different robot brain subclasses.
     *
     * @param params  the robot parameters to use
     * @param ga  the game in which the brain will play
     * @param mq  the inbound message queue for this brain from the client
     * @return the newly created brain
     * @since 2.0.00
     */
    public SOCRobotBrain createBrain
        (final SOCRobotParameters params, final SOCGame ga, final CappedQueue<SOCMessage> mq)
    {
        return new SOCRobotBrain(this, params, ga, mq);
    }

    /**
     * Treat the incoming messages.
     * Messages of unknown type are ignored. All {@link SOCGameServerText} are ignored.
     * ({@code mes} will be null from {@link SOCMessage#toMsg(String)}).
     *<P>
     *<B>Note:</B> Currently, does not call {@link SOCDisplaylessPlayerClient#treat(SOCMessage)}.
     * New message types should be added to both methods if both displayless and robot should handle them.
     * The robot treat's switch case can call super.treat before or after any robot-specific handling.
     *
     * @param mes    the message
     */
    @Override
    public void treat(SOCMessage mes)
    {
        if (mes == null)
            return;  // Message syntax error or unknown type

        // Using debugRandomPause?
        if (debugRandomPause && (! robotBrains.isEmpty())
            && (mes instanceof SOCMessageForGame)
            && ! (mes instanceof SOCGameTextMsg)
            && ! (mes instanceof SOCGameServerText)
            && ! (mes instanceof SOCTurn))
        {
            final String ga = ((SOCMessageForGame) mes).getGame();
            if (ga != null)
            {
                SOCRobotBrain brain = robotBrains.get(ga);
                if (brain != null)
                {
                    if (! debugRandomPauseActive)
                    {
                        // random chance of doing so
                        if ((Math.random() < DEBUGRANDOMPAUSE_FREQ)
                            && ((debugRandomPauseQueue == null)
                                || (debugRandomPauseQueue.isEmpty())))
                        {
                            SOCGame gm = games.get(ga);
                            final int cpn = gm.getCurrentPlayerNumber();
                            SOCPlayer rpl = gm.getPlayer(nickname);
                            if ((rpl != null) && (cpn == rpl.getPlayerNumber())
                                && (gm.getGameState() >= SOCGame.ROLL_OR_CARD))
                            {
                                // we're current player, pause us
                                debugRandomPauseActive = true;
                                debugRandomPauseUntil = System.currentTimeMillis() + (1000L * DEBUGRANDOMPAUSE_SECONDS);
                                if (debugRandomPauseQueue == null)
                                    debugRandomPauseQueue = new Vector<SOCMessage>();
                                System.err.println("L379 -> do random pause: " + nickname);
                                sendText(gm,
                                    "debugRandomPauseActive for " + DEBUGRANDOMPAUSE_SECONDS + " seconds");
                            }
                        }
                    }
                }
            }
        }

        if (debugRandomPause && debugRandomPauseActive)
        {
            if ((System.currentTimeMillis() < debugRandomPauseUntil)
                && ! (mes instanceof SOCTurn))
            {
                // time hasn't arrived yet, and still our turn:
                //   Add message to queue (even non-game and SOCGameTextMsg)
                debugRandomPauseQueue.addElement(mes);

                return;  // <--- Early return: debugRandomPauseActive ---
            }

            // time to resume the queue
            debugRandomPauseActive = false;
            while (! debugRandomPauseQueue.isEmpty())
            {
                // calling ourself is safe, because
                //  ! queue.isEmpty; thus won't decide
                //  to set debugRandomPauseActive=true again.
                treat(debugRandomPauseQueue.firstElement());
                debugRandomPauseQueue.removeElementAt(0);
            }

            // Don't return from this method yet,
            // we still need to process mes.
        }

        if ((debugTraffic || D.ebugIsEnabled())
            && ! ((mes instanceof SOCServerPing) && (nextServerPingExpectedAt != 0)
                  && (Math.abs(System.currentTimeMillis() - nextServerPingExpectedAt) <= 66000)))
                          // within 66 seconds of the expected time; see displaylesscli.handleSERVERPING
        {
            soc.debug.D.ebugPrintln("IN - " + nickname + " - " + mes);
        }

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
                handleJOINGAMEAUTH((SOCJoinGameAuth) mes, (sLocal != null));
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
             * game text message (bot debug commands)
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
             * update the state of the game
             */
            case SOCMessage.GAMESTATE:
                handleGAMESTATE((SOCGameState) mes);
                break;

            /**
             * a player built something
             */
            case SOCMessage.PUTPIECE:
                handlePUTPIECE((SOCPutPiece) mes);
                break;

            /**
             * the server is requesting that we join a game
             */
            case SOCMessage.BOTJOINGAMEREQUEST:
                handleBOTJOINGAMEREQUEST((SOCBotJoinGameRequest) mes);
                break;

            /**
             * message that means the server wants us to leave the game
             */
            case SOCMessage.ROBOTDISMISS:
                handleROBOTDISMISS((SOCRobotDismiss) mes);
                break;

            /**
             * handle board reset (new game with same players, same game name, new layout).
             */
            case SOCMessage.RESETBOARDAUTH:
                handleRESETBOARDAUTH((SOCResetBoardAuth) mes);
                break;

            /**
             * generic "simple request" responses or announcements from the server.
             * Message type added 2013-02-17 for v1.1.18,
             * bot ignored these until 2015-10-10 for v2.0.00 SC_PIRI
             * and for PROMPT_PICK_RESOURCES from gold hex.
             */
            case SOCMessage.SIMPLEREQUEST:
                super.handleSIMPLEREQUEST(games, (SOCSimpleRequest) mes);
                handlePutBrainQ((SOCSimpleRequest) mes);
                break;

            /**
             * generic "simple action" announcements from the server.
             * Added 2013-09-04 for v1.1.19.
             */
            case SOCMessage.SIMPLEACTION:
                super.handleSIMPLEACTION(games, (SOCSimpleAction) mes);
                handlePutBrainQ((SOCSimpleAction) mes);
                break;

            /**
             * a special inventory item action: either add or remove,
             * or we cannot play our requested item.
             * Added 2013-11-26 for v2.0.00.
             */
            case SOCMessage.INVENTORYITEMACTION:
                {
                    final boolean isReject = super.handleINVENTORYITEMACTION
                        (games, (SOCInventoryItemAction) mes);
                    if (isReject)
                        handlePutBrainQ((SOCInventoryItemAction) mes);
                }
                break;

            /**
             * Special Item change announcements.
             * Added 2014-04-16 for v2.0.00.
             */
            case SOCMessage.SETSPECIALITEM:
                super.handleSETSPECIALITEM(games, (SOCSetSpecialItem) mes);
                handlePutBrainQ((SOCSetSpecialItem) mes);
                break;

            // These message types are handled entirely by SOCRobotBrain:

            case SOCMessage.ACCEPTOFFER:
            case SOCMessage.CANCELBUILDREQUEST:  // current player has cancelled an initial settlement
            case SOCMessage.CHOOSEPLAYER:  // server wants our player to choose to rob cloth or rob resources from victim
            case SOCMessage.CHOOSEPLAYERREQUEST:
            case SOCMessage.CLEAROFFER:
            case SOCMessage.DEVCARDACTION:  // either draw, play, or add to hand, or cannot play our requested dev card
            case SOCMessage.DICERESULT:
            case SOCMessage.DISCARDREQUEST:
            case SOCMessage.BANKTRADE:
            case SOCMessage.MAKEOFFER:
            case SOCMessage.MOVEPIECE:   // move a previously placed ship; will update game data and player trackers
            case SOCMessage.MOVEROBBER:
            case SOCMessage.PLAYERELEMENT:
            case SOCMessage.PLAYERELEMENTS:  // apply multiple PLAYERELEMENT updates; added 2017-12-10 for v2.0.00
            case SOCMessage.REJECTOFFER:
            case SOCMessage.RESOURCECOUNT:
            case SOCMessage.STARTGAME:  // added 2017-12-18 for v2.0.00 when gameState became a field of this message
            case SOCMessage.TIMINGPING:  // server's 1x/second timing ping
            case SOCMessage.TURN:
                handlePutBrainQ((SOCMessageForGame) mes);
                break;

            // These message types are ignored by the robot client;
            // don't send them to SOCDisplaylessClient.treat:

            case SOCMessage.BCASTTEXTMSG:
            case SOCMessage.CHANGEFACE:
            case SOCMessage.CHANNELMEMBERS:
            case SOCMessage.CHANNELS:        // If bot ever uses CHANNELS, update SOCChannels class javadoc
            case SOCMessage.CHANNELTEXTMSG:
            case SOCMessage.DELETECHANNEL:
            case SOCMessage.GAMES:
            case SOCMessage.GAMESERVERTEXT:  // SOCGameServerText contents are ignored by bots
                                             // (but not SOCGameTextMsg, which is used solely for debug commands)
            case SOCMessage.GAMESTATS:
            case SOCMessage.JOINCHANNEL:
            case SOCMessage.JOINCHANNELAUTH:
            case SOCMessage.LEAVECHANNEL:
            case SOCMessage.NEWCHANNEL:
            case SOCMessage.NEWGAME:
            case SOCMessage.SETSEATLOCK:
                break;  // ignore this message type

            /**
             * Call SOCDisplaylessClient.treat for all other message types.
             * For types relevant to robots, it will update data from the message contents.
             * Other message types will be ignored.
             */
            default:
                super.treat(mes, true);
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
            System.out.println("  For message: " + mes);
        }
    }

    /**
     * handle the admin ping message
     * @param mes  the message
     */
    protected void handleADMINPING(SOCAdminPing mes)
    {
        D.ebugPrintln("*** Admin Ping message = " + mes);

        SOCGame ga = games.get(mes.getGame());

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
        if (D.ebugIsEnabled())
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
    protected void handleBOTJOINGAMEREQUEST(SOCBotJoinGameRequest mes)
    {
        D.ebugPrintln("**** handleBOTJOINGAMEREQUEST ****");

        final String gaName = mes.getGame();

        if ((testQuitAtJoinreqPercent != 0) && (new Random().nextInt(100) < testQuitAtJoinreqPercent))
        {
            System.err.println
                (" -- " + nickname + " leaving at JoinGameRequest('" + gaName + "', " + mes.getPlayerNumber()
                 + "): " + PROP_JSETTLERS_BOTS_TEST_QUIT_AT_JOINREQ);
            put(new SOCLeaveAll().toCmd());

            try { Thread.sleep(200); } catch (InterruptedException e) {}  // wait for send/receive
            disconnect();
            return;  // <--- Disconnected from server ---
        }

        final Map<String,SOCGameOption> gaOpts = mes.getOptions();
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
    @Override
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes)
    {
        int sv = mes.getStatusValue();
        if (sv == SOCStatusMessage.SV_OK_DEBUG_MODE_ON)
            sv = 0;
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
     * @param isPractice Is the server local for practice, or remote?
     */
    @Override
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes, final boolean isPractice)
    {
        gamesPlayed++;

        final String gaName = mes.getGame();

        SOCGame ga = new SOCGame(gaName, true, gameOptions.get(gaName));
        ga.isPractice = isPractice;
        games.put(gaName, ga);

        CappedQueue<SOCMessage> brainQ = new CappedQueue<SOCMessage>();
        brainQs.put(gaName, brainQ);

        SOCRobotBrain rb = createBrain(currentRobotParameters, ga, brainQ);
        robotBrains.put(gaName, rb);
    }

    /**
     * handle the "game members" message, which indicates the entire game state has now been sent.
     * If we have a {@link #seatRequests} for this game, sit down now.
     * @param mes  the message
     */
    @Override
    protected void handleGAMEMEMBERS(SOCGameMembers mes)
    {
        /**
         * sit down to play
         */
        Integer pn = seatRequests.get(mes.getGame());

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
     * handle any per-game message that just needs to go into its game's {@link #brainQs}.
     * This includes all messages that the {@link SOCRobotBrain} needs to react to.
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handlePutBrainQ(SOCMessageForGame mes)
    {
        CappedQueue<SOCMessage> brainQ = brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put((SOCMessage)mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintln("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * Handle the "game text message" message, including
     * debug text messages to the robot which start with
     * the robot's nickname + ":".
     * @param mes  the message
     */
    @Override
    protected void handleGAMETEXTMSG(SOCGameTextMsg mes)
    {
        //D.ebugPrintln(mes.getNickname()+": "+mes.getText());
        if (mes.getText().startsWith(nickname))
        {
            handleGAMETEXTMSG_debug(mes);
        }
    }

    /**
     * Handle debug text messages from players to the robot, which start with
     * the robot's nickname + ":".
     * @since 1.1.12
     */
    private final void handleGAMETEXTMSG_debug(SOCGameTextMsg mes)
    {
        final int nL = nickname.length();
        try
        {
            if (mes.getText().charAt(nL) != ':')
                return;
        } catch (IndexOutOfBoundsException e) {
            return;
        }
        final String dcmd = mes.getText().substring(nL);

        if (dcmd.startsWith(":debug-off"))
        {
            SOCGame ga = games.get(mes.getGame());
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

            if (brain != null)
            {
                brain.turnOffDRecorder();
                sendText(ga, "Debug mode OFF");
            }
        }

        else if (dcmd.startsWith(":debug-on"))
        {
            SOCGame ga = games.get(mes.getGame());
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

            if (brain != null)
            {
                brain.turnOnDRecorder();
                sendText(ga, "Debug mode ON");
            }
        }

        else if (dcmd.startsWith(":current-plans") || dcmd.startsWith(":cp"))
        {
            SOCGame ga = games.get(mes.getGame());
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                sendRecordsText(ga, brain.getDRecorder().getRecord(CURRENT_PLANS));
            }
        }

        else if (dcmd.startsWith(":current-resources") || dcmd.startsWith(":cr"))
        {
            SOCGame ga = games.get(mes.getGame());
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                sendRecordsText(ga, brain.getDRecorder().getRecord(CURRENT_RESOURCES));
            }
        }

        else if (dcmd.startsWith(":last-plans") || dcmd.startsWith(":lp"))
        {
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                Vector<String> record = brain.getOldDRecorder().getRecord(CURRENT_PLANS);

                if (record != null)
                {
                    SOCGame ga = games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        else if (dcmd.startsWith(":last-resources") || dcmd.startsWith(":lr"))
        {
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                Vector<String> record = brain.getOldDRecorder().getRecord(CURRENT_RESOURCES);

                if (record != null)
                {
                    SOCGame ga = games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        else if (dcmd.startsWith(":last-move") || dcmd.startsWith(":lm"))
        {
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

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

                    case SOCPossiblePiece.SHIP:
                        key = "SHIP" + lastMove.getCoordinates();
                        break;
                    }

                    Vector<String> record = brain.getOldDRecorder().getRecord(key);

                    if (record != null)
                    {
                        SOCGame ga = games.get(mes.getGame());
                        sendRecordsText(ga, record);
                    }
                }
            }
        }

        else if (dcmd.startsWith(":consider-move ") || dcmd.startsWith(":cm "))
        {
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

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

                Vector<String> record = brain.getOldDRecorder().getRecord(key);

                if (record != null)
                {
                    SOCGame ga = games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        else if (dcmd.startsWith(":last-target") || dcmd.startsWith(":lt"))
        {
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

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

                    case SOCPossiblePiece.SHIP:
                        key = "SHIP" + lastTarget.getCoordinates();
                        break;
                    }

                    Vector<String> record = brain.getDRecorder().getRecord(key);

                    if (record != null)
                    {
                        SOCGame ga = games.get(mes.getGame());
                        sendRecordsText(ga, record);
                    }
                }
            }
        }

        else if (dcmd.startsWith(":consider-target ") || dcmd.startsWith(":ct "))
        {
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

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

                Vector<String> record = brain.getDRecorder().getRecord(key);

                if (record != null)
                {
                    SOCGame ga = games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        else if (dcmd.startsWith(":print-vars") || dcmd.startsWith(":pv"))
        {
            // "prints" the results as series of SOCGameTextMsg to game
            debugPrintBrainStatus(mes.getGame(), true);
        }

        else if (dcmd.startsWith(":stats"))
        {
            SOCGame ga = games.get(mes.getGame());
            sendText(ga, "Games played:" + gamesPlayed);
            sendText(ga, "Games finished:" + gamesFinished);
            sendText(ga, "Games won:" + gamesWon);
            sendText(ga, "Clean brain kills:" + cleanBrainKills);
            sendText(ga, "Brains running: " + robotBrains.size());

            Runtime rt = Runtime.getRuntime();
            sendText(ga, "Total Memory:" + rt.totalMemory());
            sendText(ga, "Free Memory:" + rt.freeMemory());
        }

        else if (dcmd.startsWith(":gc"))
        {
            SOCGame ga = games.get(mes.getGame());
            Runtime rt = Runtime.getRuntime();
            rt.gc();
            sendText(ga, "Free Memory:" + rt.freeMemory());
        }

    }

    /**
     * handle the "someone is sitting down" message
     * @param mes  the message
     */
    @Override
    protected void handleSITDOWN(SOCSitDown mes)
    {
        /**
         * tell the game that a player is sitting
         */
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            ga.addPlayer(mes.getNickname(), mes.getPlayerNumber());

            /**
             * set the robot flag
             */
            ga.getPlayer(mes.getPlayerNumber()).setRobotFlag(mes.isRobot(), false);

            /**
             * let the robot brain find our player object if we sat down
             */
            if (nickname.equals(mes.getNickname()))
            {
                SOCRobotBrain brain = robotBrains.get(mes.getGame());

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
                SOCRobotBrain brain = robotBrains.get(mes.getGame());

                if (brain != null)
                {
                    brain.addPlayerTracker(mes.getPlayerNumber());
                }
            }
        }
    }

    /**
     * handle the "delete game" message
     * @param mes  the message
     */
    @Override
    protected void handleDELETEGAME(SOCDeleteGame mes)
    {
        SOCRobotBrain brain = robotBrains.get(mes.getGame());

        if (brain != null)
        {
            SOCGame ga = games.get(mes.getGame());

            if (ga != null)
            {
                if (ga.getGameState() == SOCGame.OVER)
                {
                    gamesFinished++;

                    if (ga.getPlayer(nickname).getTotalVP() >= ga.vp_winner)
                    {
                        gamesWon++;
                        // TODO: should check actual winning player number (getCurrentPlayerNumber?)
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
     * Handle the "game state" message; instead of immediately updating state,
     * calls {@link #handlePutBrainQ(SOCMessageForGame)}.
     * @param mes  the message
     */
    @Override
    protected void handleGAMESTATE(SOCGameState mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            handlePutBrainQ(mes);
        }
    }

    /**
     * handle the "put piece" message
     * @param mes  the message
     */
    protected void handlePUTPIECE(SOCPutPiece mes)
    {
        CappedQueue<SOCMessage> brainQ = brainQs.get(mes.getGame());

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

            SOCGame ga = games.get(mes.getGame());

            if (ga != null)
            {
                // SOCPlayer pl = ga.getPlayer(mes.getPlayerNumber());
                // JDM TODO - Was this in stock client?
            }
        }
    }

    /**
     * handle the "dismiss robot" message
     * @param mes  the message
     */
    protected void handleROBOTDISMISS(SOCRobotDismiss mes)
    {
        SOCGame ga = games.get(mes.getGame());
        CappedQueue<SOCMessage> brainQ = brainQs.get(mes.getGame());

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
             * the game here, instead of having the brain leave it
             */
            SOCRobotBrain brain = robotBrains.get(mes.getGame());

            if ((brain == null) || (! brain.isAlive()))
            {
                leaveGame(games.get(mes.getGame()), "brain not alive in handleROBOTDISMISS", true, false);
            }
        }
    }

    /**
     * handle board reset
     * (new game with same players, same game name).
     * Destroy old Game object.
     * Unlike <tt>SOCDisplaylessPlayerClient.handleRESETBOARDAUTH</tt>, don't call {@link SOCGame#resetAsCopy()}.
     *<P>
     * Take robotbrain out of old game, don't yet put it in new game.
     * Let server know we've done so, by sending LEAVEGAME via {@link #leaveGame(SOCGame, String, boolean, boolean)}.
     * Server will soon send a BOTJOINGAMEREQUEST if we should join the new game.
     *
     * @param mes  the message
     *
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     * @see #handleBOTJOINGAMEREQUEST(SOCBotJoinGameRequest)
     */
    @Override
    protected void handleRESETBOARDAUTH(SOCResetBoardAuth mes)
    {
        D.ebugPrintln("**** handleRESETBOARDAUTH ****");

        String gname = mes.getGame();
        SOCGame ga = games.get(gname);
        if (ga == null)
            return;  // Not one of our games

        SOCRobotBrain brain = robotBrains.get(gname);
        if (brain != null)
            brain.kill();
        leaveGame(ga, "resetboardauth", false, false);  // Same as in handleROBOTDISMISS
        ga.destroyGame();
    }

    /**
     * Call sendText on each string element of record.
     * @param ga Game to sendText to
     * @param record Strings to send, or null
     */
    protected void sendRecordsText(SOCGame ga, Vector<String> record)
    {
        if (record != null)
        {
            for (String str : record)
            {
                sendText(ga, str);
            }
        }
    }

    /**
     * Print brain variables and status for this game, to {@link System#err}
     * or as {@link SOCGameTextMsg} sent to the game's members,
     * by calling {@link SOCRobotBrain#debugPrintBrainStatus()}.
     * @param gameName  Game name; if no brain for that game, do nothing.
     * @param sendTextToGame  Send to game as {@link SOCGameTextMsg} if true,
     *     otherwise print to {@link System#err}.
     * @since 1.1.13
     */
    public void debugPrintBrainStatus(String gameName, final boolean sendTextToGame)
    {
        SOCRobotBrain brain = robotBrains.get(gameName);
        if (brain == null)
            return;

        List<String> rbSta = brain.debugPrintBrainStatus();
        if (sendTextToGame)
            for (final String st : rbSta)
                put(SOCGameTextMsg.toCmd(gameName, nickname, st));
        else
            for (final String st : rbSta)
                System.err.println(st);
    }

    /**
     * the user leaves the given game
     *
     * @param ga   the game
     * @param leaveReason reason for leaving
     * @param showReason  If true print bot, game, and {@code leaveReason} even if not {@link D#ebugIsEnabled()}
     * @param showDebugTrace  If true print current thread's stack trace
     */
    public void leaveGame
        (final SOCGame ga, final String leaveReason, final boolean showReason, final boolean showDebugTrace)
    {
        if (ga != null)
        {
            final String gaName = ga.getName();

            robotBrains.remove(gaName);
            brainQs.remove(gaName);
            games.remove(gaName);

            final String r = (showReason || D.ebugIsEnabled())
                ? ("L1833 robot " + nickname + " leaving game " + gaName + " due to " + leaveReason)
                : null;
            if (showReason)
                soc.debug.D.ebugPrintln(r);
            else if (r != null)
                D.ebugPrintln(r);

            if (showDebugTrace)
            {
                soc.debug.D.ebugPrintStackTrace(null, "Leaving game here");
                System.err.flush();
            }

            put(SOCLeaveGame.toCmd(nickname, host, gaName));
        }
    }

    /**
     * add one to the number of clean brain kills
     */
    public void addCleanKill()
    {
        cleanBrainKills++;
    }

    /**
     * Connection to server has raised an error; leave all games, then try to reconnect.
     */
    @Override
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
        if (args.length < 5)
        {
            System.err.println("Java Settlers robotclient " + Version.version() +
                    ", build " + Version.buildnum());
            System.err.println("usage: java soc.robot.SOCRobotClient host port_number userid password cookie");
            return;
        }

        SOCRobotClient ex1 = new SOCRobotClient(args[0], Integer.parseInt(args[1]), args[2], args[3], args[4]);
        ex1.init();
    }

}
