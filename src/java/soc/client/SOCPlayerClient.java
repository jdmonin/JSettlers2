/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2017 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
 *     - UI layer refactoring, GameStatistics, nested class refactoring, parameterize types
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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.net.ConnectException;
import java.net.Socket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCDevCardConstants;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCInventory;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCScenario;
import soc.game.SOCSettlement;
import soc.game.SOCSpecialItem;
import soc.game.SOCTradeOffer;
import soc.game.SOCVersionedItem;
import soc.game.SOCVillage;

import soc.message.*;

import soc.server.SOCServer;
import soc.server.genericServer.LocalStringConnection;
import soc.server.genericServer.LocalStringServerSocket;
import soc.server.genericServer.StringConnection;

import soc.util.I18n;
import soc.util.SOCGameList;
import soc.util.SOCServerFeatures;
import soc.util.SOCStringManager;
import soc.util.Version;

/**
 * Standalone client for connecting to the SOCServer. (For applet see {@link SOCApplet}.)
 * Nested class {@link GameAwtDisplay} prompts for name and password, then connects and
 * displays the lists of games and channels available.
 * The actual game is played in a separate {@link SOCPlayerInterface} window.
 *<P>
 * If you want another connection port, you have to specify it as the "port"
 * argument in the html source. If you run this as a stand-alone, the port can be
 * specified on the command line or typed into {@code GameAwtDisplay}'s connect dialog.
 *<P>
 * At startup or init, will try to connect to server via {@link SOCPlayerClient.ClientNetwork#connect(String, int)}.
 * See that method for more details.
 *<P>
 * There are three possible servers to which a client can be connected:
 *<UL>
 *  <LI>  A remote server, running on the other end of a TCP connection
 *  <LI>  A local TCP server, for hosting games, launched by this client:
 *        {@link SOCPlayerClient.ClientNetwork#localTCPServer localTCPServer}
 *  <LI>  A "practice game" server, not bound to any TCP port, for practicing
 *        locally against robots: {@link SOCPlayerClient.ClientNetwork#practiceServer practiceServer}
 *</UL>
 * At most, the client is connected to the practice server and one TCP server.
 * Each game's {@link SOCGame#isPractice} flag determines which connection to use.
 *<P>
 * Once connected, messages from the server are processed in {@link MessageTreater#treat(SOCMessage, boolean)}.
 *
 * @author Robert S Thomas
 */
@SuppressWarnings("serial")
public class SOCPlayerClient
{
    /**
     * i18n text strings in our {@link #cliLocale}.
     * @since 2.0.00
     */
    private final soc.util.SOCStringManager strings;

    /** text prefix to show games this client cannot join. "(cannot join) "
     * @since 1.1.06
     */
    //TODO i18n logic should be changed
    protected static final String GAMENAME_PREFIX_CANNOT_JOIN = "(cannot join) ";

    /**
     * For use in password fields, and possibly by other places, detect if we're running on
     * Mac OS X.  To identify osx from within java, see technote TN2110:
     * http://developer.apple.com/technotes/tn2002/tn2110.html
     * @since 1.1.07
     */
    public static final boolean isJavaOnOSX =
        System.getProperty("os.name").toLowerCase().startsWith("mac os x");

    /**
     * Locale for i18n message lookups used for {@link #strings}.  Also sent to server while connecting.
     * Override if needed in the constructor by reading the
     * {@link I18n#PROP_JSETTLERS_LOCALE PROP_JSETTLERS_LOCALE} system property {@code "jsettlers.locale"}.
     * @since 2.0.00
     */
    final Locale cliLocale;

    /**
     * Helper object to deal with network connectivity.
     */
    private ClientNetwork net;

    /**
     * Helper object to receive incoming network traffic from the server.
     */
    private MessageTreater treater;

    /**
     * Helper object to send outgoing network traffic to the server.
     */
    private GameManager gameManager;

    /**
     * Display for all user interface, including and beyond games.
     */
    private GameDisplay gameDisplay;

    /**
     *  Server version number for remote server, sent soon after connect, 0 if no server, or -1 if version unknown.
     *  A local practice server's version is always {@link Version#versionNumber()}, not {@code sVersion},
     *  so always check {@link SOCGame#isPractice} before checking this field.
     */
    protected int sVersion;

    /**
     * Server's active optional features, sent soon after connect, or null if unknown.
     * Not used with a local practice server, so always check {@link SOCGame#isPractice} before checking this field.
     * @see #tcpServGameOpts
     * @since 1.1.19
     */
    protected SOCServerFeatures sFeatures;

    /**
     * Track the game options available at the remote server, at the practice server.
     * Initialized by {@link SOCPlayerClient.GameAwtDisplay#gameWithOptionsBeginSetup(boolean, boolean)}
     * and/or {@link MessageTreater#handleVERSION(boolean, SOCVersion)}.
     * These fields are never null, even if the respective server is not connected or not running.
     *<P>
     * For a summary of the flags and variables involved with game options,
     * and the client/server interaction about their values, see
     * {@link ServerGametypeInfo}'s javadoc.
     *<P>
     * Scenario strings are localized by {@link #localizeGameScenarios(List, boolean, boolean, boolean)}.
     *
     * @see #sFeatures
     * @since 1.1.07
     */
    protected ServerGametypeInfo tcpServGameOpts = new ServerGametypeInfo(),
        practiceServGameOpts = new ServerGametypeInfo();

    /**
     * For practice games, default game name ("Practice").
     * Set in constructor using i18n {@link #strings} lookup.
     */
    public final String DEFAULT_PRACTICE_GAMENAME;

    /**
     * the nickname; null until validated and set by
     * {@link SOCPlayerClient.GameAwtDisplay#getValidNickname(boolean) getValidNickname(true)}
     */
    protected String nickname = null;

    /**
     * the password for {@link #nickname}. If the server's authenticated this password,
     * the {@link #gotPassword} flag is set.
     */
    protected String password = null;

    /**
     * true if we've stored the password and the server's replied that it's correct
     * @see #isNGOFWaitingForAuthStatus
     */
    protected boolean gotPassword;

    /**
     * true if user clicked "new game" and, before showing {@link NewGameOptionsFrame}, we've
     * sent the nickname (username) and password to the server and are waiting for a response.
     *<P>
     * Used only with TCP servers, not with {@link SOCPlayerClient.ClientNetwork#practiceServer practiceServer}.
     * @since 1.1.19
     */
    protected boolean isNGOFWaitingForAuthStatus;

    /**
     * True if contents of incoming and outgoing network message traffic should be debug-printed.
     * Set if optional system property {@code jsettlers.debug.traffic} is set.
     *<P>
     * Versions earlier than 1.1.20 always printed this debug output.
     * @since 2.0.00
     */
    private boolean debugTraffic;

    /**
     * face ID chosen most recently (for use in new games)
     */
    protected int lastFaceChange;

    /**
     * the games we're currently playing
     */
    protected Hashtable<String, SOCGame> games = new Hashtable<String, SOCGame>();

    /**
     * all announced game names on the remote server, including games which we can't
     * join due to limitations of the client.
     * May also contain options for all announced games on the server (not just ones
     * we're in) which we can join (version is not higher than our version).
     *<P>
     * Key is the game name, without the UNJOINABLE prefix.
     * This field is null until {@link SOCPlayerClient.MessageTreater#handleGAMES(SOCGames, boolean) handleGAMES},
     *   {@link SOCPlayerClient.MessageTreater#handleGAMESWITHOPTIONS(SOCGamesWithOptions, boolean) handleGAMESWITHOPTIONS},
     *   {@link SOCPlayerClient.MessageTreater#handleNEWGAME(SOCNewGame, boolean) handleNEWGAME}
     *   or {@link SOCPlayerClient.MessageTreater#handleNEWGAMEWITHOPTIONS(SOCNewGameWithOptions, boolean) handleNEWGAMEWITHOPTIONS}
     *   is called.
     * @since 1.1.07
     */
    protected SOCGameList serverGames = null;

    /**
     * the unjoinable game names from {@link #serverGames} that player has asked to join,
     * and been told they can't.  If they click again, try to connect.
     * (This is a failsafe against bugs in server or client version-recognition.)
     * Both key and value are the game name, without the UNJOINABLE prefix.
     * @since 1.1.06
     */
    protected Map<String,String> gamesUnjoinableOverride = new Hashtable<String,String>();

    /**
     * Map from game-name to the listener for that game.
     */
    private final Map<String, PlayerClientListener> clientListeners = new HashMap<String, PlayerClientListener>();

    /**
     * the ignore list
     */
    protected Vector<String> ignoreList = new Vector<String>();

    /**
     * Number of practice games started; used for naming practice games
     */
    protected int numPracticeGames = 0;


    /**
     * A facade for the SOCPlayerClient to use to invoke actions in the GUI
     * @since 2.0.00
     */
    public interface GameDisplay
    {
        /**
         * Init the visual elements.  Done before connecting to server,
         * so we don't know its version or active {@link SOCServerFeatures}.
         * So, most of the Main Panel elements are initialized here but not
         * laid out or made visible until a later call to
         * {@link #showVersion(int, String, String, SOCServerFeatures)}
         * when the version and features are known.
         */
        void initVisualElements();

        /**
         * Prepare to connect and give feedback by showing a message panel.
         * Stores the given username and password in the user interface.
         *<P>
         * Does not make a network connection.
         * Call {@link SOCPlayerClient.ClientNetwork#connect(String, int)} when ready to make the connection.
         *<P>
         * User login and authentication don't occur until a game or channel join is requested;
         * at that time, the user interface will read the name and password stored here.
         *
         * @param cpass Password text to put into that TextField (obscured)
         * @param cuser User nickname text to put into that TextField
         */
        void connect(String cpass, String cuser);

        /**
         * Act as if the "practice game" button has been clicked.
         * Assumes the dialog panels are all initialized.
         */
        void clickPracticeButton();
        void practiceGameStarting();

        void setMessage(String string);

        /**
         * Show an error dialog which has one button.
         * @param errMessage  Error message to show
         * @param buttonText  Button text, or null for "OK"
         */
        void showErrorDialog(String errMessage, String buttonText);

        /**
         * After network trouble, show a panel with the error message
         * instead of the main user/password/games/channels panel.
         *<P>
         * If we have the startup panel (started as JAR client app, not applet) with buttons to connect
         * to a server or practice, show that instead of the simpler practice-only message panel.
         *
         * @param err  Error message to show
         * @param canPractice  In current state of client, can we start a practice game?
         * @since 1.1.16
         */
        void showErrorPanel(String err, boolean canPractice);

        void enableOptions();

        /**
         * After connecting, display the remote server's version on main panel,
         * and update display based on its active {@link SOCServerFeatures}.
         * Not called for practice server.
         * If we're running a server, display its listening port # instead.
         * @param versionNumber  Version number, like 1119, from server's {@link soc.util.Version#versionNumber()}
         * @param versionString  Version string, like "1.1.19", from server's {@link soc.util.Version#version()}
         * @param buildString  Build number, from server's {@link soc.util.Version#buildnum()}
         * @param feats  Active optional server features; never null. If server is older than v1.1.19, use the
         *            {@link SOCServerFeatures#SOCServerFeatures(boolean) SOCServerFeatures(true)} constructor.
         */
        void showVersion
            (final int versionNumber, final String versionString, final String buildString, final SOCServerFeatures feats);

        /**
         * Show server welcome banner or status text.
         * If status during initial connect includes warning that the server's in Debug Mode, show that.
         * @param statusText  Status message text from server
         * @param debugWarn   True if server has Debug Mode active
         */
        void showStatus(String statusText, boolean debugWarn);

        /** If the password field is currently visible, focus the cursor there for the user to type something. */
        public void focusPassword();

        /** Set the contents of the password field. */
        public void setPassword(final String pw);

        void channelJoined(String channelName);
        void channelJoined(String channelName, String nickname);
        void channelMemberList(String channelName, Collection<String> members);
        void channelCreated(String channelName);
        void channelLeft(String channelName);
        void channelLeft(String channelName, String nickname);

        /**
         * Server has sent its list of chat channels (or an empty list), so
         * populate and show that list along with the rest of the UI.
         * The server sends the list when the client successfully connects.
         * @param channelNames  List of server's chat channels, from server message
         * @param isPractice  True if this is the practice server, not a TCP server
         */
        void channelList(Collection<String> channelNames, boolean isPractice);

        void channelDeleted(String channelName);
        void channelsClosed(String message);

        void messageBroadcast(String message);
        void messageReceived(String channelName, String nickname, String message);

        PlayerClientListener gameJoined(SOCGame game);

        /**
         * Want to start a new game, on a server which supports options.
         * Do we know the valid options already?  If so, bring up the options window.
         * If not, ask the server for them. If a {@link NewGameOptionsFrame} is already
         * showing, give it focus instead of creating a new one.
         *<P>
         * For a summary of the flags and variables involved with game options,
         * and the client/server interaction about their values, see
         * {@link ServerGametypeInfo}.
         *
         * @param forPracticeServer  Ask {@link ClientNetwork#practiceServer}, instead of TCP server?
         * @param didAuth  If true, the server has authenticated our username and password;
         *     set those input fields read-only.
         */
        void gameWithOptionsBeginSetup(final boolean forPracticeServer, final boolean didAuth);

        void optionsRequested();

        /**
         * Server has sent its game option default values for new games.
         * Called when {@link ServerGametypeInfo#newGameWaitingForOpts} flag was set and
         * has just been cleared.  Client should show dialog to create a new game which
         * will have game options.
         *
         * @param opts  Client's game option info, tracking the TCP or local practice server
         * @param isPractice  True if received from {@link ClientNetwork#practiceServer}, instead of TCP server
         */
        void optionsReceived(ServerGametypeInfo opts, boolean isPractice);

        /**
         * Server has sent info about a single game option.  If {@code hasAllNow},
         * client should check {@link ServerGametypeInfo#newGameWaitingForOpts} and
         * {@link ServerGametypeInfo#gameInfoWaitingForOpts}, and if either of these
         * were waiting, show a game info/options dialog for a new game or existing game.
         *
         * @param opts  Client's game option info, tracking the TCP or local practice server
         * @param isPractice  True if received from {@link ClientNetwork#practiceServer}, instead of TCP server
         * @param isDash  True if the game option was {@code "-"}, indicating the end of the list.
         *     If so, no further options will be sent and any running timeout task related to the
         *     game options can be cancelled.
         * @param hasAllNow  If true, all game option info has now been received by the client
         */
        void optionsReceived(ServerGametypeInfo opts, boolean isPractice, boolean isDash, boolean hasAllNow);

        /**
         * Add a new game to the initial window's list of games.
         * If client can't join, makes sure the game is marked as unjoinable
         * in the {@link SOCPlayerClient#serverGames} list.
         *
         * @param cannotJoin Can we not join this game?
         * @param gameName the game name to add to the list;
         *            must not have the prefix {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
         * @param gameOptsStr String of packed {@link SOCGameOption game options}, or null
         * @param addToSrvList Should this game be added to the list of remote-server games?
         *            Practice games should not be added.
         */
        void addToGameList(final boolean cannotJoin, String gameName, String gameOptsStr, final boolean addToSrvList);

        /**
         * Update this game's stats in the game list display.
         *
         * @param gameName Name of game to update
         * @param scores Each player position's score
         * @param robots Is this position a robot?
         *
         * @see soc.message.SOCGameStats
         */
        void updateGameStats(String gameName, int[] scores, boolean[] robots);

        /**
         * Delete a game from the list.
         * If it's on the list, also remove from {@link #serverGames}.
         *
         * @param gameName  the game to remove
         * @param isPractice   Game is practice, not at tcp server?
         * @return true if deleted, false if not found in list
         */
        boolean deleteFromGameList(String gameName, final boolean isPractice);

    }  // public interface GameDisplay


    /**
     * Create a SOCPlayerClient connecting to localhost port {@link ClientNetwork#SOC_PORT_DEFAULT}.
     * Initializes helper objects (except {@link GameDisplay}), locale, {@link SOCStringManager}.
     * The locale will be the current user's default locale, unless overridden by setting the
     * {@link I18n#PROP_JSETTLERS_LOCALE PROP_JSETTLERS_LOCALE} system property {@code "jsettlers.locale"}.
     *<P>
     * Must call {@link SOCApplet#init()}, or {@link #setGameDisplay(GameDisplay)} and then
     * {@link GameDisplay#initVisualElements()}, to start up and do layout.
     *<P>
     * Must then call {@link #connect(String, int, String, String)} or {@link ClientNetwork#connect(String, int)}
     * to join a TCP server, or {@link GameDisplay#clickPracticeButton()}
     * or {@link GameAwtDisplay#startLocalTCPServer(int)} to start a server locally.
     */
    public SOCPlayerClient()
    {
        gotPassword = false;
        lastFaceChange = 1;  // Default human face

        if (null != System.getProperty("jsettlers.debug.traffic"))
            debugTraffic = true;  // set flag if debug prop has any value at all

        String jsLocale = System.getProperty(I18n.PROP_JSETTLERS_LOCALE);
        Locale lo = null;
        if (jsLocale != null)
        {
            try
            {
                lo = I18n.parseLocale(jsLocale.trim());
            } catch (IllegalArgumentException e) {
                System.err.println("Could not parse locale " + jsLocale);
            }
        }
        if (lo != null)
            cliLocale = lo;
        else
            cliLocale = Locale.getDefault();

        strings = soc.util.SOCStringManager.getClientManager(cliLocale);
        DEFAULT_PRACTICE_GAMENAME = strings.get("default.name.practice.game");

        net = new ClientNetwork(this);
        gameManager = new GameManager(this);
        treater = new MessageTreater(this);
    }

    /**
     * Set our game display interface.
     * Before using the client, caller must also call {@link GameDisplay#initVisualElements()}.
     * @since 2.0.00
     */
    public void setGameDisplay(final GameDisplay gd)
    {
        gameDisplay = gd;
    }

    /**
     * Connect and give feedback by showing MESSAGE_PANEL.
     * Calls {@link GameDisplay#connect(String, String)} to set username and password,
     * then {@link ClientNetwork#connect(String, int)} to make the connection.
     *
     * @param chost Hostname to connect to, or null for localhost
     * @param cport Port number to connect to
     * @param cuser User nickname
     * @param cpass User optional password
     */
    public void connect(String chost, int cport, String cuser, String cpass)
    {
        gameDisplay.connect(cpass, cuser);
        net.connect(chost, cport);
    }

    /**
     * @return the nickname of this user
     * @see SOCPlayerClient.GameAwtDisplay#getValidNickname(boolean)
     */
    public String getNickname()
    {
        return nickname;
    }


    /**
     * A {@link GameDisplay} implementation for AWT.
     * Uses {@link CardLayout} to display an appropriate interface:
     *<UL>
     * <LI> Initial "Connect or Practice" panel to connect to a server
     * <LI> Main panel to list the connected server's current games and channels
     *     and create new ones
     * <LI> Message panel to show a server connectivity error
     *</UL>
     * Individual games are shown using {@link SOCPlayerInterface}
     * and channels use {@link ChannelFrame}.
     *<P>
     * Before v2.0.00, most of these fields and methods were part of the main {@link SOCPlayerClient} class.
     * @since 2.0.00
     */
    public static class GameAwtDisplay extends Panel implements GameDisplay
    {
        /** main panel, in cardlayout */
        private static final String MAIN_PANEL = "main";

        /** message panel, in cardlayout */
        private static final String MESSAGE_PANEL = "message";

        /** Connect-or-practice panel (if jar launch), in cardlayout.
          * Panel field is {@link #connectOrPracticePane}.
          * Available if {@link #hasConnectOrPractice}.
          */
        private static final String CONNECT_OR_PRACTICE_PANEL = "connOrPractice";

        /** Game statistics prefix */
        protected static final String STATSPREFEX = "  [";  // TODO I18N: must analyze

        /**
         * For practice games, reminder message for network problems.
         */
        public final String NET_UNAVAIL_CAN_PRACTICE_MSG;

        /**
         * Hint message if they try to join a game or channel without entering a nickname.
         *
         * @see #NEED_NICKNAME_BEFORE_JOIN_2
         * @see #NEED_NICKNAME_BEFORE_JOIN_G
         */
        public final String NEED_NICKNAME_BEFORE_JOIN;

        /**
         * Stronger hint message if they still try to join a game or channel without entering a nickname.
         *
         * @see #NEED_NICKNAME_BEFORE_JOIN
         * @see #NEED_NICKNAME_BEFORE_JOIN_G2
         */
        public final String NEED_NICKNAME_BEFORE_JOIN_2;

        /**
         * Hint message if they try to join a game without entering a nickname,
         * on a server which doesn't support chat channels.
         *
         * @see #NEED_NICKNAME_BEFORE_JOIN_G2
         * @see #NEED_NICKNAME_BEFORE_JOIN
         * @since 1.1.19
         */
        public final String NEED_NICKNAME_BEFORE_JOIN_G;

        /**
         * Stronger hint message if they still try to join a game without entering a nickname,
         * on a server which doesn't support chat channels.
         *
         * @see #NEED_NICKNAME_BEFORE_JOIN_G
         * @see #NEED_NICKNAME_BEFORE_JOIN_2
         * @since 1.1.19
         */
        public final String NEED_NICKNAME_BEFORE_JOIN_G2;

        /**
         * Status text to indicate client cannot join a game.
         * @since 1.1.06
         */
        public final String STATUS_CANNOT_JOIN_THIS_GAME;

        private SOCPlayerClient client;

        /**
         * the player interfaces for the games
         */
        private final Map<String, SOCPlayerInterface> playerInterfaces = new Hashtable<String, SOCPlayerInterface>();

        /**
         * Task for timeout when asking remote server for {@link SOCGameOptionInfo game options info}.
         * Set up when sending {@link SOCGameOptionGetInfos GAMEOPTIONGETINFOS}.
         * In case of slow connection or server bug.
         * @see #gameOptionsSetTimeoutTask()
         * @since 1.1.07
         */
        protected GameOptionsTimeoutTask gameOptsTask = null;

        /**
         * Task for timeout when asking remote server for {@link SOCGameOption game options defaults}.
         * Set up when sending {@link SOCGameOptionGetDefaults GAMEOPTIONGETDEFAULTS}.
         * In case of slow connection or server bug.
         * @see #gameWithOptionsBeginSetup(boolean, boolean)
         * @since 1.1.07
         */
        protected GameOptionDefaultsTimeoutTask gameOptsDefsTask = null;

        /**
         * Once true, disable "nick" textfield, etc.
         * Remains true, even if connected becomes false.
         */
        protected boolean hasJoinedServer;

        /**
         * If true, we'll give the user a choice to
         * connect to a server, start a local server,
         * or a local practice game.
         * Used for when we're started from a jar, or
         * from the command line with no arguments.
         * Uses {@link SOCConnectOrPracticePanel}.
         *
         * @see #cardLayout
         */
        protected final boolean hasConnectOrPractice;

        /**
         * If applicable, is set up in {@link #initVisualElements()}.
         * Key for {@link #cardLayout} is {@link #CONNECT_OR_PRACTICE_PANEL}.
         * @see #hasConnectOrPractice
         */
        protected SOCConnectOrPracticePanel connectOrPracticePane;

        /**
         * The currently showing new-game options frame, or null
         * @since 1.1.07
         */
        public NewGameOptionsFrame newGameOptsFrame = null;

        // MainPanel GUI elements:

        /**
         * MainPanel GUI, initialized in {@link #initVisualElements()}
         * and {@link #initMainPanelLayout(boolean, SOCServerFeatures)}.
         *<P>
         * {@code mainPane}, {@link #mainGBL}, and {@link #mainGBC} are fields not locals so that
         * the layout can be changed after initialization if needed.  Most of the Main Panel
         * elements are initialized in {@link #initVisualElements()} but not laid out or made visible
         * until a later call to {@link #initMainPanelLayout(boolean, SOCServerFeatures)} (from
         * ({@link #showVersion(int, String, String, SOCServerFeatures) showVersion(....)})
         * when the version and features are known.
         * @since 1.1.19
         */
        private Panel mainPane;

        /** Layout for {@link #mainPane} */
        private GridBagLayout mainGBL;

        /** Constraints for {@link #mainGBL} */
        private GridBagConstraints mainGBC;

        /**
         * Flags for tracking {@link #mainPane} layout status, in case
         * {@link #initMainPanelLayout(boolean, SOCServerFeatures)} is
         * called again after losing connection and then connecting to
         * another server or starting a hosted tcp server.
         * @since 1.1.19
         */
        private boolean mainPaneLayoutIsDone, mainPaneLayoutIsDone_hasChannels;

        /** Nickname (username) to connect to server and use in games */
        protected TextField nick;

        /** Password for {@link #nick} while connecting to server, or blank */
        protected TextField pass;

        /** Status from server, or progress/error message updated by client */
        protected TextField status;

        /**
         * Chat channel name to create or join with {@link #jc} button.
         * Hidden in v1.1.19+ if server is missing {@link SOCServerFeatures#FEAT_CHANNELS}.
         */
        protected TextField channel;

        // protected TextField game;  // removed 1.1.07 - NewGameOptionsFrame instead

        /**
         * List of chat channels that can be joined with {@link #jc} button or by double-click.
         * Hidden in v1.1.19+ if server is missing {@link SOCServerFeatures#FEAT_CHANNELS}.
         */
        protected java.awt.List chlist;

        /**
         * List of games that can be joined with {@link #jg} button or by double-click,
         * or detail info displayed with {@link #gi} button.
         * Contains all games on server if connected, and any Practice Games
         * created with {@link #pg} button.
         */
        protected java.awt.List gmlist;

        /**
         * "New Game..." button, brings up {@link NewGameOptionsFrame} window
         * @since 1.1.07
         */
        protected Button ng;  // new game

        /**
         * "Join Channel" button, for channel currently highlighted in {@link #chlist},
         * or create new channel named in {@link #channel}. Hidden in v1.1.19+ if server
         * is missing {@link SOCServerFeatures#FEAT_CHANNELS}.
         */
        protected Button jc;

        /** "Join Game" button */
        protected Button jg;

        /**
         * Practice Game button: Create game to play against
         * {@link SOCPlayerClient.ClientNetwork#practiceServer practiceServer},
         * not {@link SOCPlayerClient.ClientNetwork#localTCPServer localTCPServer}.
         */
        protected Button pg;

        /**
         * "Game Info" button, shows a game's {@link SOCGameOption}s.
         *<P>
         * Renamed in 2.0.00 to 'gi'; previously 'so' Show Options.
         * @since 1.1.07
         */
        protected Button gi;

        /**
         * Local Server indicator in main panel: blank, or 'server is running' if
         * {@link SOCPlayerClient.ClientNetwork#localTCPServer localTCPServer} has been started.
         * If so, localTCPServer's port number is shown in {@link #versionOrlocalTCPPortLabel}.
         */
        private Label localTCPServerLabel;

        /**
         * When connected to a remote server, shows its version number.
         * When running {@link SOCPlayerClient.ClientNetwork#localTCPServer localTCPServer},
         * shows that server's port number (see also {@link #localTCPServerLabel}).
         * In either mode, has a tooltip with more info.
         */
        private Label versionOrlocalTCPPortLabel;

        protected Label messageLabel;  // error message for messagepanel
        protected Label messageLabel_top;   // secondary message
        protected Button pgm;  // practice game on messagepanel

        /**
         * This class displays one of several panels to the user:
         * {@link #MAIN_PANEL}, {@link #MESSAGE_PANEL} or
         * (if launched from jar, or with no command-line arguments)
         * {@link #CONNECT_OR_PRACTICE_PANEL}.
         *
         * @see #hasConnectOrPractice
         */
        protected CardLayout cardLayout;

        /**
         * the channels we've joined
         */
        protected Hashtable<String, ChannelFrame> channels = new Hashtable<String, ChannelFrame>();

        /**
         * Utility for time-driven events in the display.
         * For users, search for where-used of this field
         * and of {@link #getEventTimer()}.
         * @since 1.1.07
         */
        protected Timer eventTimer = new Timer(true);  // use daemon thread

        /**
         * Create a new GameAwtDisplay for this client.
         * Must call {@link #initVisualElements()} after this constructor.
         * @param hasConnectOrPractice  True if should initially display {@link SOCConnectOrPracticePanel}
         *     and ask for a server to connect to, false if the server is known
         *     and should display the main panel (game list, channel list, etc).
         * @param client  Client using this display; {@link SOCPlayerClient#strings client.strings} must not be null
         * @throws IllegalArgumentException if {@code client} is null
         */
        public GameAwtDisplay(boolean hasConnectOrPractice, final SOCPlayerClient client)
            throws IllegalArgumentException
        {
            if (client == null)
                throw new IllegalArgumentException("null client");

            this.hasConnectOrPractice = hasConnectOrPractice;
            this.client = client;

            NET_UNAVAIL_CAN_PRACTICE_MSG = client.strings.get("pcli.error.server.unavailable");
                // "The server is unavailable. You can still play practice games."
            NEED_NICKNAME_BEFORE_JOIN = client.strings.get("pcli.main.join.neednickname");
                // "First enter a nickname, then join a game or channel."
            NEED_NICKNAME_BEFORE_JOIN_G = client.strings.get("pcli.main.join.neednickname.g");
                // "First enter a nickname, then join a game."
            NEED_NICKNAME_BEFORE_JOIN_2 = client.strings.get("pcli.main.join.neednickname.2");
                // "You must enter a nickname before you can join a game or channel."
            NEED_NICKNAME_BEFORE_JOIN_G2 = client.strings.get("pcli.main.join.neednickname.g2");
                // "You must enter a nickname before you can join a game."
            STATUS_CANNOT_JOIN_THIS_GAME = client.strings.get("pcli.main.join.cannot");
                // "Cannot join, this client is incompatible with features of this game."
        }

        public SOCPlayerClient getClient()
        {
            return client;
        }

        public GameManager getGameManager()
        {
            return client.getGameManager();
        }

        public WindowAdapter createWindowAdapter()
        {
            return new ClientWindowAdapter(this);
        }

        public void setMessage(String message)
        {
            messageLabel.setText(message);
        }

        /**
         * {@inheritDoc}
         *<P>
         * Uses {@link NotifyDialog#createAndShow(GameAwtDisplay, Frame, String, String, boolean)}
         * which calls {@link EventQueue#invokeLater(Runnable)} to ensure it displays from the proper thread.
         */
        public void showErrorDialog(final String errMessage, final String buttonText)
        {
            NotifyDialog.createAndShow(this, null, errMessage, buttonText, true);
        }

        public void initVisualElements()
        {
            final SOCStringManager strings = client.strings;

            setFont(new Font("SansSerif", Font.PLAIN, 12));

            nick = new TextField(20);
            pass = new TextField(20);
            if (isJavaOnOSX)
                pass.setEchoChar('\u2022');  // round bullet (option-8)
            else
                pass.setEchoChar('*');
            status = new TextField(20);
            status.setEditable(false);
            channel = new TextField(20);
            chlist = new java.awt.List(10, false);
            chlist.add(" ");
            gmlist = new java.awt.List(10, false);
            gmlist.add(" ");
            ng = new Button(strings.get("pcli.main.newgame"));       // "New Game..."
            jc = new Button(strings.get("pcli.main.join.channel"));  // "Join Channel"
            jg = new Button(strings.get("pcli.main.join.game"));     // "Join Game"
            pg = new Button(strings.get("pcli.main.practice"));      // "Practice" -- "practice game" text is too wide
            gi = new Button(strings.get("pcli.main.game.info"));     // "Game Info" -- show game options
            versionOrlocalTCPPortLabel = new Label();
            localTCPServerLabel = new Label();

            // Username not entered yet: can't click buttons
            ng.setEnabled(false);
            jc.setEnabled(false);

            // when game is selected in gmlist, these buttons will be enabled:
            jg.setEnabled(false);
            gi.setEnabled(false);

            nick.addTextListener(new TextListener()
            {
                /**
                 * When nickname contents change, enable/disable buttons as appropriate. ({@link TextListener})
                 * @param e textevent from {@link #nick}
                 * @since 1.1.07
                 */
                public void textValueChanged(TextEvent e)
                {
                    boolean notEmpty = (nick.getText().trim().length() > 0);
                    if (notEmpty != ng.isEnabled())
                    {
                        ng.setEnabled(notEmpty);
                        jc.setEnabled(notEmpty);
                    }
                }
            });

            ActionListener actionListener = new ActionListener()
            {
                /**
                 * Handle mouse clicks and keyboard
                 */
                public void actionPerformed(ActionEvent e)
                {
                    try
                    {
                        Object target = e.getSource();
                        guardedActionPerform(target);
                    }
                    catch (Throwable thr)
                    {
                        System.err.println("-- Error caught in AWT event thread: " + thr + " --");
                        thr.printStackTrace(); // will print causal chain, no need to manually iterate
                        System.err.println("-- Error stack trace end --");
                        System.err.println();
                    }
                }
            };

            nick.addActionListener(actionListener);  // hit Enter to go to next field
            pass.addActionListener(actionListener);
            channel.addActionListener(actionListener);
            chlist.addActionListener(actionListener);
            gmlist.addActionListener(actionListener);
            gmlist.addItemListener(new ItemListener()
            {
                /**
                 * When a game is selected/deselected, enable/disable buttons as appropriate. ({@link ItemListener})
                 * @param e textevent from {@link #gmlist}
                 * @since 1.1.07
                 */
                public void itemStateChanged(ItemEvent e)
                {
                    boolean wasSel = (e.getStateChange() == ItemEvent.SELECTED);
                    if (wasSel != jg.isEnabled())
                    {
                        jg.setEnabled(wasSel);
                        gi.setEnabled(wasSel &&
                            ((client.net.practiceServer != null) || (client.sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)));
                    }
                }
            });
            ng.addActionListener(actionListener);
            jc.addActionListener(actionListener);
            jg.addActionListener(actionListener);
            pg.addActionListener(actionListener);
            gi.addActionListener(actionListener);

            initMainPanelLayout(true, null);  // status line only, until later call to showVersion

            Panel messagePane = new Panel(new BorderLayout());

            // secondary message at top of message pane, used with pgm button.
            messageLabel_top = new Label("", Label.CENTER);
            messageLabel_top.setVisible(false);
            messagePane.add(messageLabel_top, BorderLayout.NORTH);

            // message label that takes up the whole pane
            messageLabel = new Label("", Label.CENTER);
            messageLabel.setForeground(new Color(252, 251, 243)); // off-white
            messagePane.add(messageLabel, BorderLayout.CENTER);

            // bottom of message pane: practice-game button
            pgm = new Button(strings.get("pcli.message.practicebutton"));  // "Practice Game (against robots)"
            pgm.setVisible(false);
            messagePane.add(pgm, BorderLayout.SOUTH);
            pgm.addActionListener(actionListener);

            // all together now...
            cardLayout = new CardLayout();
            setLayout(cardLayout);

            if (hasConnectOrPractice)
            {
                connectOrPracticePane = new SOCConnectOrPracticePanel(this);
                add (connectOrPracticePane, CONNECT_OR_PRACTICE_PANEL);  // shown first
            }
            add(messagePane, MESSAGE_PANEL); // shown first unless cpPane
            add(mainPane, MAIN_PANEL);

            messageLabel.setText(strings.get("pcli.message.waiting"));  // "Waiting to connect."
            validate();
        }

        /**
         * Lay out the Main Panel: status text, name, password, game/channel names and lists, buttons.
         * This method is called twice: First during client init to lay out the status display only,
         * and then again once the server is connected and its features and version are known.
         *<P>
         * Because {@link #mainPane} is part of a larger layout, this method does not call {@code validate()}.
         * Be sure to call {@link Container#validate() validate()} afterwards once the entire layout is ready.
         *<P>
         * This was part of {@link #initVisualElements()} before v1.1.19.
         * @param isStatusRow  If true, this is an initial call and only the status row should be laid out.
         *     If false, assumes status was already done and adds the rest of the rows.
         * @param feats  Active optional server features, for second call with {@code isStatusRow == false}.
         *     Null when {@code isStatusRow == true}.  See {@link #showVersion(int, String, String, SOCServerFeatures)}
         *     javadoc for expected contents when an older server does not report features.
         * @since 1.1.19
         * @throws IllegalArgumentException if {@code feats} is null but {@code isStatusRow} is false
         */
        private void initMainPanelLayout(final boolean isStatusRow, final SOCServerFeatures feats)
            throws IllegalArgumentException
        {
            if ((feats == null) && ! isStatusRow)
                throw new IllegalArgumentException("feats");

            final SOCStringManager strings = client.strings;

            if (mainGBL == null)
                mainGBL = new GridBagLayout();
            if (mainGBC == null)
                mainGBC = new GridBagConstraints();
            if (mainPane == null)
                mainPane = new Panel(mainGBL);
            else if (mainPane.getLayout() == null)
                mainPane.setLayout(mainGBL);

            final GridBagLayout gbl = mainGBL;
            final GridBagConstraints c = mainGBC;

            c.fill = GridBagConstraints.BOTH;
            c.gridwidth = GridBagConstraints.REMAINDER;

            if (isStatusRow)
            {
                gbl.setConstraints(status, c);
                mainPane.add(status);

                return;  // <---- Early return: Call later to lay out the rest ----
            }

            // Reminder: Everything here and below is the delayed second call.
            // So, any fields must be initialized in initVisualElements(), not here.

            final boolean hasChannels = feats.isActive(SOCServerFeatures.FEAT_CHANNELS);

            if (mainPaneLayoutIsDone)
            {
                // called again after layout was done; probably connected to a different server

                if (hasChannels != mainPaneLayoutIsDone_hasChannels)
                {
                    // hasChannels changed: redo both layout calls
                    mainPane.removeAll();
                    mainGBL = null;
                    mainGBC = null;
                    mainPane.setLayout(null);
                    mainPaneLayoutIsDone = false;

                    initMainPanelLayout(true, null);
                    initMainPanelLayout(false, feats);
                }

                return;  // <---- Early return: Layout done already ----
            }

            // If ! hasChannels, these aren't part of a layout: hide them in case other code checks isVisible()
            channel.setVisible(hasChannels);
            chlist.setVisible(hasChannels);
            jc.setVisible(hasChannels);

            Label l;

            // Layout is 6 columns wide (item, item, middle spacer, item, spacer, item).
            // If ! hasChannels, channel-related items won't be laid out; adjust spacing to compensate.

            // Row 1 (spacer)

            l = new Label();
            c.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            // Row 2

            l = new Label(strings.get("pcli.main.label.yournickname"));  // "Your Nickname:"
            c.gridwidth = 1;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            c.gridwidth = 1;
            gbl.setConstraints(nick, c);
            mainPane.add(nick);

            l = new Label();
            c.gridwidth = 1;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            l = new Label(strings.get("pcli.main.label.optionalpw"));  // "Optional Password:"
            c.gridwidth = 1;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            l = new Label();
            c.gridwidth = 1;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            c.gridwidth = 1;
            gbl.setConstraints(pass, c);
            mainPane.add(pass);

            l = new Label();
            c.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            l = new Label();
            c.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            // Row 3 (New Channel label & textfield, Practice btn, New Game btn)

            if (hasChannels)
            {
                l = new Label(strings.get("pcli.main.label.newchannel"));  // "New Channel:"
                c.gridwidth = 1;
                gbl.setConstraints(l, c);
                mainPane.add(l);

                c.gridwidth = 1;
                gbl.setConstraints(channel, c);
                mainPane.add(channel);
            }

            l = new Label();
            c.gridwidth = (hasChannels) ? 1 : 3;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            c.gridwidth = 1;  // this position was "New Game:" label before 1.1.07
            gbl.setConstraints(pg, c);
            mainPane.add(pg);  // "Practice"

            l = new Label();
            c.gridwidth = 1;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            c.gridwidth = 1;
            gbl.setConstraints(ng, c);
            mainPane.add(ng);  // "New Game..."

            l = new Label();
            c.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            // Row 4 (spacer)

            c.gridwidth = 2;
            gbl.setConstraints(localTCPServerLabel, c);
            mainPane.add(localTCPServerLabel);

            l = new Label();
            c.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            // Row 5 (version/port# label, join channel btn, show-options btn, join game btn)

            c.gridwidth = (hasChannels) ? 1 : 2;
            gbl.setConstraints(versionOrlocalTCPPortLabel, c);
            mainPane.add(versionOrlocalTCPPortLabel);

            if (hasChannels)
            {
                c.gridwidth = 1;
                gbl.setConstraints(jc, c);
                mainPane.add(jc);  // "Join Channel"
            }

            l = new Label();
            c.gridwidth = 1;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            c.gridwidth = 1;
            gbl.setConstraints(gi, c);
            mainPane.add(gi);  // "Game Info"

            l = new Label();
            c.gridwidth = 1;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            c.gridwidth = 1;
            gbl.setConstraints(jg, c);
            mainPane.add(jg);  // "Join Game"

            l = new Label();
            c.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            // Row 6

            if (hasChannels)
            {
                l = new Label(strings.get("pcli.main.label.channels"));  // "Channels"
                c.gridwidth = 2;
                gbl.setConstraints(l, c);
                mainPane.add(l);

                l = new Label();
                c.gridwidth = 1;
                gbl.setConstraints(l, c);
                mainPane.add(l);
            }

            l = new Label(strings.get("pcli.main.label.games"));  // "Games"
            c.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            // Row 7

            if (hasChannels)
            {
                c.gridwidth = 2;
                c.gridheight = GridBagConstraints.REMAINDER;
                gbl.setConstraints(chlist, c);
                mainPane.add(chlist);

                l = new Label();
                c.gridwidth = 1;
                gbl.setConstraints(l, c);
                mainPane.add(l);
            }

            c.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(gmlist, c);
            mainPane.add(gmlist);

            mainPaneLayoutIsDone_hasChannels = hasChannels;
            mainPaneLayoutIsDone = true;
        }

        /**
         * Prepare to connect, give feedback by showing {@link #MESSAGE_PANEL}.
         * {@inheritDoc}
         */
        public void connect(String cpass, String cuser)
        {
            nick.setEditable(true);  // in case of reconnect. Will disable after starting or joining a game.
            pass.setEditable(true);
            pass.setText(cpass);
            nick.setText(cuser);
            nick.requestFocusInWindow();
            if ((cuser != null) && (cuser.trim().length() > 0))
                ng.setEnabled(true);

            cardLayout.show(this, MESSAGE_PANEL);
        }

        public String getNickname()
        {
            return client.getNickname();
        }

        public void clickPracticeButton()
        {
            guardedActionPerform(pgm);
        }

        /**
         * Wrapped version of actionPerformed() for easier encapsulation.
         * @param target Action source, from ActionEvent.getSource()
         * @since 1.1.00
         */
        private void guardedActionPerform(Object target)
        {
            boolean showPopupCannotJoin = false;

            if ((target == jc) || (target == channel) || (target == chlist)) // Join channel stuff
            {
                showPopupCannotJoin = ! guardedActionPerform_channels(target);
            }
            else if ((target == jg) || (target == ng) || (target == gmlist)
                    || (target == pg) || (target == pgm) || (target == gi)) // Join game stuff
            {
                showPopupCannotJoin = ! guardedActionPerform_games(target);
            }

            if (showPopupCannotJoin)
            {
                status.setText(STATUS_CANNOT_JOIN_THIS_GAME);
                // popup
                NotifyDialog.createAndShow(this, (Frame) null,
                    STATUS_CANNOT_JOIN_THIS_GAME,
                    client.strings.get("base.cancel"), true);

                return;
            }

            if (target == nick)
            { // Nickname TextField
                nick.transferFocus();
            }

            return;
        }

        /**
         * GuardedActionPerform when a channels-related button or field is clicked
         * @param target Target as in actionPerformed
         * @return True if OK, false if caller needs to show popup "cannot join"
         * @since 1.1.06
         */
        private boolean guardedActionPerform_channels(Object target)
        {
            String ch;

            if (target == jc) // "Join Channel" Button
            {
                ch = channel.getText().trim();

                if (ch.length() == 0)
                {
                    try
                    {
                        ch = chlist.getSelectedItem().trim();
                    }
                    catch (NullPointerException ex)
                    {
                        return true;
                    }
                }
            }
            else if (target == channel)
            {
                ch = channel.getText().trim();
            }
            else
            {
                try
                {
                    ch = chlist.getSelectedItem().trim();
                }
                catch (NullPointerException ex)
                {
                    return true;
                }
            }

            if (ch.length() == 0)
            {
                return true;
            }

            if (ch.startsWith(GAMENAME_PREFIX_CANNOT_JOIN))
            {
                return false;
            }

            ChannelFrame cf = channels.get(ch);

            if (cf == null)
            {
                if (channels.isEmpty())
                {
                    // May set hint message if empty, like NEED_NICKNAME_BEFORE_JOIN
                    if (! readValidNicknameAndPassword())
                        return true;  // not filled in yet
                }

                status.setText(client.strings.get("pcli.message.talkingtoserv"));  // "Talking to server..."
                client.net.putNet(SOCJoinChannel.toCmd
                    (client.nickname, (client.gotPassword ? "" : client.password), client.net.getHost(), ch));
            }
            else
            {
                cf.setVisible(true);
            }

            channel.setText("");
            return true;
        }

        /**
         * Read and validate username and password GUI fields into client's data fields.
         * This method may set status bar to a hint message if username is empty,
         * such as {@link #NEED_NICKNAME_BEFORE_JOIN}.
         * @return true if OK, false if blank or not ready
         * @see #getValidNickname(boolean)
         * @since 1.1.07
         */
        public boolean readValidNicknameAndPassword()
        {
            client.nickname = getValidNickname(true);  // May set hint message if empty,
                                            // like NEED_NICKNAME_BEFORE_JOIN
            if (client.nickname == null)
               return false;  // not filled in yet

            if (!client.gotPassword)
            {
                client.password = getPassword();  // may be 0-length
            }
            return true;
        }

        /**
         * GuardedActionPerform when a games-related button or field is clicked
         * @param target Target as in actionPerformed
         * @return True if OK, false if caller needs to show popup "cannot join"
         * @since 1.1.06
         */
        private boolean guardedActionPerform_games(Object target)
        {
            String gm;  // May also be 0-length string, if pulled from Lists

            if ((target == pg) || (target == pgm)) // "Practice Game" Buttons
            {
                gm = client.DEFAULT_PRACTICE_GAMENAME;  // "Practice"

                // If blank, fill in player name

                if (0 == nick.getText().trim().length())
                {
                    nick.setText(client.strings.get("default.name.practice.player"));  // "Player"
                }
            }
            else if (target == ng)  // "New Game" button
            {
                if (null != getValidNickname(false))  // that method does a name check, but doesn't set nick field yet
                {
                    gameWithOptionsBeginSetup(false, false);  // Also may set status, WAIT_CURSOR
                } else {
                    nick.requestFocusInWindow();  // Not a valid player nickname
                }
                return true;
            }
            else if (target == jg) // "Join Game" Button
            {
                try
                {
                    gm = gmlist.getSelectedItem().trim();  // may be length 0
                }
                catch (NullPointerException ex)
                {
                    return true;
                }
            }
            else
            {
                // game list
                try
                {
                    gm = gmlist.getSelectedItem().trim();
                }
                catch (NullPointerException ex)
                {
                    return true;
                }
            }

            // System.out.println("GM = |"+gm+"|");
            if (gm.length() == 0)
            {
                return true;
            }

            if (target == gi)  // show game info, game options, for an existing game
            {
                // This game is either from the tcp server, or practice server,
                // both servers' games are in the same GUI list.

                Map<String,SOCGameOption> opts = null;

                if ((client.net.practiceServer != null) && (-1 != client.net.practiceServer.getGameState(gm)))
                {
                    opts = client.net.practiceServer.getGameOptions(gm);  // won't ever need to parse from string on practice server
                }
                else if (client.serverGames != null)
                {
                    opts = client.serverGames.getGameOptions(gm);
                    if ((opts == null) && (client.serverGames.getGameOptionsString(gm) != null))
                    {
                        // If necessary, parse game options from string before displaying.
                        // (Parsed options are cached, they won't be re-parsed)
                        // If parsed options include a scenario, and we don't have its
                        // localized strings, ask the server for that but don't wait for
                        // a reply before showing the NewGameOptionsFrame.

                        if (client.tcpServGameOpts.allOptionsReceived)
                        {
                            opts = client.serverGames.parseGameOptions(gm);
                            client.checkGameoptsForUnknownScenario(opts);
                        } else {
                            // not yet received; remember game name.
                            // when all are received, will show it,
                            // and will also clear WAIT_CURSOR.
                            // (see handleGAMEOPTIONINFO)

                            client.tcpServGameOpts.gameInfoWaitingForOpts = gm;
                            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                            return true;  // <---- early return: not yet ready to show ----
                        }
                    }
                }

                // don't overwrite newGameOptsFrame field; this popup is to show an existing game.
                NewGameOptionsFrame.createAndShow(this, gm, opts, false, true);
                return true;
            }

            final boolean unjoinablePrefix = gm.startsWith(GAMENAME_PREFIX_CANNOT_JOIN);
            if (unjoinablePrefix)
            {
                // Game is marked as un-joinable by this client. Remember that,
                // then continue to process the game name, without prefix.

                gm = gm.substring(GAMENAME_PREFIX_CANNOT_JOIN.length());
            }

            // Can we not join that game?
            if (unjoinablePrefix || ((client.serverGames != null) && client.serverGames.isUnjoinableGame(gm)))
            {
                if (! client.gamesUnjoinableOverride.containsKey(gm))
                {
                    client.gamesUnjoinableOverride.put(gm, gm);  // Next click will try override
                    return false;
                }
            }

            // Are we already in a game with that name?
            SOCPlayerInterface pi = playerInterfaces.get(gm);

            if ((pi == null)
                    && ((target == pg) || (target == pgm))
                    && (client.net.practiceServer != null)
                    && (gm.equalsIgnoreCase(client.DEFAULT_PRACTICE_GAMENAME)))
            {
                // Practice game requested, no game named "Practice" already exists.
                // Check for other active practice games. (Could be "Practice 2")
                pi = findAnyActiveGame(true);
            }

            if ((pi != null) && ((target == pg) || (target == pgm)))
            {
                // Practice game requested, already exists.
                //
                // Ask the player if they want to join, or start a new game.
                // If we're from the error panel (pgm), there's no way to
                // enter a game name; make a name up if needed.
                // If we already have a game going, our nickname is not empty.
                // So, it's OK to not check that here or in the dialog.

                // Is the game over yet?
                if (pi.getGame().getGameState() == SOCGame.OVER)
                {
                    // No point joining, just get options to start a new one.
                    gameWithOptionsBeginSetup(true, false);
                }
                else
                {
                    new SOCPracticeAskDialog(this, pi).setVisible(true);
                }

                return true;
            }

            if (pi == null)
            {
                if (client.games.isEmpty())
                {
                    client.nickname = getValidNickname(true);  // May set hint message if empty,
                                               // like NEED_NICKNAME_BEFORE_JOIN
                    if (client.nickname == null)
                        return true;  // not filled in yet

                    if (!client.gotPassword)
                        client.password = getPassword();  // may be 0-length
                }

                int endOfName = gm.indexOf(STATSPREFEX);

                if (endOfName > 0)
                {
                    gm = gm.substring(0, endOfName);
                }

                if (((target == pg) || (target == pgm)) && (null == client.net.ex_P))
                {
                    if (target == pg)
                        status.setText
                            (client.strings.get("pcli.message.startingpractice"));  // "Starting practice game setup..."

                    gameWithOptionsBeginSetup(true, false);  // Also may set WAIT_CURSOR
                }
                else
                {
                    // Join a game on the remote server.
                    // Send JOINGAME right away.
                    // (Create New Game is done above; see calls to gameWithOptionsBeginSetup)

                    // May take a while for server to start game, so set WAIT_CURSOR.
                    // The new-game window will clear this cursor
                    // (SOCPlayerInterface constructor)

                    status.setText(client.strings.get("pcli.message.talkingtoserv"));  // "Talking to server..."
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    client.net.putNet(SOCJoinGame.toCmd
                        (client.nickname, (client.gotPassword ? "" : client.password), client.net.getHost(), gm));
                }
            }
            else
            {
                pi.setVisible(true);
            }

            return true;
        }

        /**
         * Validate and return the nickname textfield, or null if blank or not ready.
         * If successful, also set {@link #nickname} field.
         * @param precheckOnly If true, only validate the name, don't set {@link #nickname}.
         * @see #readValidNicknameAndPassword()
         * @since 1.1.07
         */
        protected String getValidNickname(boolean precheckOnly)
        {
            String n = nick.getText().trim();

            if (n.length() == 0)
            {
                final String stat = status.getText();
                if (stat.equals(NEED_NICKNAME_BEFORE_JOIN) || stat.equals(NEED_NICKNAME_BEFORE_JOIN_G))
                    // Send stronger hint message
                    status.setText
                        ((client.sFeatures.isActive(SOCServerFeatures.FEAT_CHANNELS))
                         ? NEED_NICKNAME_BEFORE_JOIN_2
                         : NEED_NICKNAME_BEFORE_JOIN_G2 );
                else
                    // Send first hint message (or re-send first if they've seen _2)
                    status.setText
                        ((client.sFeatures.isActive(SOCServerFeatures.FEAT_CHANNELS))
                         ? NEED_NICKNAME_BEFORE_JOIN
                         : NEED_NICKNAME_BEFORE_JOIN_G );
                return null;
            }

            if (n.length() > 20)
            {
                n = n.substring(0, 20);
            }
            if (! SOCMessage.isSingleLineAndSafe(n))
            {
                status.setText(SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED);
                return null;
            }
            nick.setText(n);
            if (! precheckOnly)
                client.nickname = n;
            return n;
        }

        /**
         * Validate and return the password textfield contents; may be 0-length.
         * Also set {@link #password} field.
         * If {@link #gotPassword} already, return current password without checking textfield.
         * @since 1.1.07
         */
        protected String getPassword()
        {
            if (client.gotPassword)
                return client.password;

            String p = pass.getText().trim();

            if (p.length() > 20)
            {
                p = p.substring(0, 20);
            }

            client.password = p;
            return p;
        }

        /**
         * Utility for time-driven events in the client.
         * For some users, see where-used of this and of {@link SOCPlayerInterface#getEventTimer()}.
         * @return the timer
         * @since 1.1.07
         */
        public Timer getEventTimer()
        {
            return eventTimer;
        }

        /**
         * {@inheritDoc}
         *<P>
         * Updates tcpServGameOpts, practiceServGameOpts, newGameOptsFrame.
         * @since 1.1.07
         */
        public void gameWithOptionsBeginSetup(final boolean forPracticeServer, final boolean didAuth)
        {
            if (newGameOptsFrame != null)
            {
                newGameOptsFrame.setVisible(true);
                return;
            }

            // Have we authenticated our password?  If not, do so now before creating newGameOptsFrame.
            // Even if the server doesn't support accounts or passwords, this will name our connection
            // and reserve our nickname.
            if ((! (forPracticeServer || client.gotPassword))
                && (client.sVersion >= SOCAuthRequest.VERSION_FOR_AUTHREQUEST))
            {
                if (! readValidNicknameAndPassword())
                    return;

                // handleSTATUSMESSAGE(SV_OK) will check the isNGOFWaitingForAuthStatus flag and
                // call gameWithOptionsBeginSetup again if set.  At that point client.gotPassword
                // will be true, so we'll bypass this section.

                client.isNGOFWaitingForAuthStatus = true;
                status.setText(client.strings.get("pcli.message.talkingtoserv"));  // "Talking to server..."
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));  // NGOF create calls setCursor(DEFAULT_CURSOR)
                client.net.putNet(SOCAuthRequest.toCmd
                    (SOCAuthRequest.ROLE_GAME_PLAYER, client.nickname, client.password,
                     SOCAuthRequest.SCHEME_CLIENT_PLAINTEXT, client.net.getHost()));

                return;
            }

            if (didAuth)
            {
                nick.setEditable(false);
                pass.setText("");
                pass.setEditable(false);
            }

            ServerGametypeInfo opts;

            // What server are we going against? Do we need to ask it for options?
            {
                boolean fullSetIsKnown = false;

                if (forPracticeServer)
                {
                    opts = client.practiceServGameOpts;
                    if (! opts.allOptionsReceived)
                    {
                        // We know what the practice options will be,
                        // because they're in our own JAR file.
                        // Also, the practice server isn't started yet,
                        // so we can't ask it for the options.
                        // The practice server will be started when the player clicks
                        // "Create Game" in the NewGameOptionsFrame, causing the new
                        // game to be requested from askStartGameWithOptions.
                        fullSetIsKnown = true;
                        opts.optionSet = SOCServer.localizeKnownOptions(client.cliLocale, true);
                    }

                    if (! opts.allScenStringsReceived)
                    {
                        // Game scenario localized text. As with game options, the practice client and
                        // practice server aren't started yet, so we can't request localization from there.
                        client.localizeGameScenarios
                            (SOCServer.localizeGameScenarios(client.cliLocale, null, false, null),
                             false, true, true);
                    }
                } else {
                    opts = client.tcpServGameOpts;
                    if ((! opts.allOptionsReceived) && (client.sVersion < SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS))
                    {
                        // Server doesn't support them.  Don't ask it.
                        fullSetIsKnown = true;
                        opts.optionSet = null;
                    }
                }

                if (fullSetIsKnown)
                {
                    opts.allOptionsReceived = true;
                    opts.defaultsReceived = true;
                }
            }

            // Do we already have info on all options?
            boolean askedAlready, optsAllKnown, knowDefaults;
            synchronized (opts)
            {
                askedAlready = opts.askedDefaultsAlready;
                optsAllKnown = opts.allOptionsReceived;
                knowDefaults = opts.defaultsReceived;
            }

            if (askedAlready && ! (optsAllKnown && knowDefaults))
            {
                // If we're only waiting on defaults, how long ago did we ask for them?
                // If > 5 seconds ago, assume we'll never know the unknown ones, and present gui frame.
                if (optsAllKnown && (5000 < Math.abs(System.currentTimeMillis() - opts.askedDefaultsTime)))
                {
                    knowDefaults = true;
                    opts.defaultsReceived = true;
                    if (gameOptsDefsTask != null)
                    {
                        gameOptsDefsTask.cancel();
                        gameOptsDefsTask = null;
                    }
                    // since optsAllKnown, will present frame below.
                } else {
                    return;  // <--- Early return: Already waiting for an answer ----
                }
            }

            if (optsAllKnown && knowDefaults)
            {
                // All done, present the options window frame
                newGameOptsFrame = NewGameOptionsFrame.createAndShow
                    (this, null, opts.optionSet, forPracticeServer, false);
                return;  // <--- Early return: Show options to user ----
            }

            // OK, we need to sync scenario and option info.
            // Ask the server by sending GAMEOPTIONGETDEFAULTS.
            // (This will never happen for practice games, see above.)

            // May take a while for server to send our info.
            // The new-game-options window will clear this cursor
            // (NewGameOptionsFrame constructor)

            status.setText(client.strings.get("pcli.message.talkingtoserv"));  // "Talking to server..."
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            final int cliVers = Version.versionNumber();
            if ((! forPracticeServer) && (! opts.allScenInfoReceived)
                && (client.sVersion >= SOCScenario.VERSION_FOR_SCENARIOS))
            {
                // Before game option defaults, ask for any updated or localized scenario info;
                // that will all be received before game option defaults, so client will have it
                // before NewGameOptionsFrame appears with the scenarios dropdown Choice widget.

                List<String> changes = null;

                if (cliVers > client.sVersion)
                {
                    // Client newer than server: Ask specifically about any scenarios server might not know about.

                    final List<SOCScenario> changeScens =
                        SOCVersionedItem.itemsNewerThanVersion
                            (client.sVersion, false, SOCScenario.getAllKnownScenarios());

                    if (changeScens != null)
                    {
                        changes = new ArrayList<String>();
                        for (SOCScenario sc : changeScens)
                            changes.add(sc.key);
                    }
                }

                // Else, server is newer than our client or same version.
                //   Server is newer: Ask for any scenario changes since our version.
                //   Same version: Ask for i18n localized scenarios strings if available.
                //   In both cases that's requested by sending an empty 'changes' list and MARKER_ANY_CHANGED.

                if ((cliVers != client.sVersion) || client.wantsI18nStrings(false))
                    client.gameManager.put(new SOCScenarioInfo(changes, true).toCmd(), false);
            }

            opts.newGameWaitingForOpts = true;
            opts.askedDefaultsAlready = true;
            opts.askedDefaultsTime = System.currentTimeMillis();
            client.gameManager.put(SOCGameOptionGetDefaults.toCmd(null), forPracticeServer);

            if (gameOptsDefsTask != null)
                gameOptsDefsTask.cancel();
            gameOptsDefsTask = new GameOptionDefaultsTimeoutTask(this, client.tcpServGameOpts, forPracticeServer);
            eventTimer.schedule(gameOptsDefsTask, 5000 /* ms */ );

            // Once options are received, handlers will
            // create and show NewGameOptionsFrame.
        }

        /**
         * Ask server to start a game with options.
         * If it's practice, will call {@link #startPracticeGame(String, Map, boolean)}.
         * Otherwise, ask tcp server, and also set WAIT_CURSOR and status line ("Talking to server...").
         *<P>
         * Assumes {@link #getValidNickname(boolean) getValidNickname(true)}, {@link #getPassword()}, {@link ClientNetwork#host},
         * and {@link #gotPassword} are already called and valid.
         *
         * @param gmName Game name; for practice, null is allowed
         * @param forPracticeServer Is this for a new game on the practice (not tcp) server?
         * @param opts Set of {@link SOCGameOption game options} to use, or null
         * @since 1.1.07
         * @see #readValidNicknameAndPassword()
         */
        public void askStartGameWithOptions
            (final String gmName, final boolean forPracticeServer, final Map<String, SOCGameOption> opts)
        {
            if (forPracticeServer)
            {
                client.startPracticeGame(gmName, opts, true);  // Also sets WAIT_CURSOR
            } else {
                final String pw = (client.gotPassword ? "" : client.password);  // after successful auth, don't need to send
                String askMsg =
                    (client.sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
                    ? SOCNewGameWithOptionsRequest.toCmd
                        (client.nickname, pw, client.net.getHost(), gmName, opts)
                    : SOCJoinGame.toCmd
                        (client.nickname, pw, client.net.getHost(), gmName);
                System.err.println("L1314 askStartGameWithOptions at " + System.currentTimeMillis());
                System.err.println("      Got all opts,defaults? " + client.tcpServGameOpts.allOptionsReceived
                    + " " + client.tcpServGameOpts.defaultsReceived);
                client.net.putNet(askMsg);
                System.out.flush();  // for debug print output (temporary)
                status.setText(client.strings.get("pcli.message.talkingtoserv"));  // "Talking to server..."
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                System.err.println("L1320 askStartGameWithOptions done at " + System.currentTimeMillis());
                System.err.println("      sent: " + client.net.lastMessage_N);
            }
        }

        /**
         * Look for active games that we're playing
         *
         * @param fromPracticeServer  Enumerate games from {@link ClientNetwork#practiceServer},
         *     instead of {@link #playerInterfaces}?
         * @return Any found game of ours which is active (state not OVER), or null if none.
         * @see SOCPlayerClient.ClientNetwork#anyHostedActiveGames()
         */
        protected SOCPlayerInterface findAnyActiveGame(boolean fromPracticeServer)
        {
            SOCPlayerInterface pi = null;
            int gs;  // gamestate

            Collection<String> gameNames;
            if (fromPracticeServer)
            {
                if (client.net.practiceServer == null)
                    return null;  // <---- Early return: no games if no practice server ----
                gameNames = client.net.practiceServer.getGameNames();
            } else {
                gameNames = playerInterfaces.keySet();
            }

            for (String tryGm : gameNames)
            {
                if (fromPracticeServer)
                {
                    gs = client.net.practiceServer.getGameState(tryGm);
                    if (gs < SOCGame.OVER)
                    {
                        pi = playerInterfaces.get(tryGm);
                        if (pi != null)
                            break;  // Active and we have a window with it
                    }
                } else {
                    pi = playerInterfaces.get(tryGm);
                    if (pi != null)
                    {
                        // we have a window with it
                        gs = pi.getGame().getGameState();
                        if (gs < SOCGame.OVER)
                            break;      // Active

                        pi = null;  // Avoid false positive
                    }
                }
            }

            return pi;  // Active game, or null
        }

        /**
         * After network trouble, show the error panel ({@link #MESSAGE_PANEL})
         * instead of the main user/password/games/channels panel ({@link #MAIN_PANEL}).
         *<P>
         * If {@link #hasConnectOrPractice we have the startup panel} (started as JAR client
         * app, not applet) with buttons to connect to a server or practice, we'll show that
         * instead of the simpler practice-only message panel.
         *
         * @param err  Error message to show
         * @param canPractice  In current state of client, can we start a practice game?
         * @since 1.1.16
         */
        public void showErrorPanel(final String err, final boolean canPractice)
        {
            // In case was WAIT_CURSOR while connecting
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            if (canPractice)
            {
                messageLabel_top.setText(err);
                messageLabel_top.setVisible(true);
                messageLabel.setText(NET_UNAVAIL_CAN_PRACTICE_MSG);
                pgm.setVisible(true);
            }
            else
            {
                messageLabel_top.setVisible(false);
                messageLabel.setText(err);
                pgm.setVisible(false);
            }

            if (hasConnectOrPractice)
            {
                // If we have the startup panel with buttons to connect to a server or practice,
                // prep to show that by un-setting read-only fields we'll need again after connect.
                nick.setEditable(true);
                pass.setText("");
                pass.setEditable(true);

                cardLayout.show(this, CONNECT_OR_PRACTICE_PANEL);
                validate();
                connectOrPracticePane.clickConnCancel();
                connectOrPracticePane.setTopText(err);
                connectOrPracticePane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
            else
            {
                cardLayout.show(this, MESSAGE_PANEL);
                validate();
                if (canPractice)
                {
                    if (null == findAnyActiveGame(true))
                        pgm.requestFocus();  // No practice games: put this msg as topmost window
                    else
                        pgm.requestFocusInWindow();  // Practice game is active; don't interrupt to show this
                }
            }
        }

        public void enableOptions()
        {
            if (gi != null)
                gi.setEnabled(true);
        }

        /**
         * {@inheritDoc}
         *<P>
         * {@code showVersion} calls
         * {@link #initMainPanelLayout(boolean, SOCServerFeatures) initMainPanelLayout(false, feats)}
         * to complete layout of the Main Panel with the server's version and active features.
         */
        public void showVersion
            (final int vers, final String versionString, final String buildString, final SOCServerFeatures feats)
        {
            if (null == client.net.localTCPServer)
            {
                versionOrlocalTCPPortLabel.setForeground(new Color(252, 251, 243)); // off-white
                versionOrlocalTCPPortLabel.setText(client.strings.get("pcli.main.version", versionString));  // "v {0}"
                new AWTToolTip
                    (client.strings.get("pcli.main.version.tip", versionString, buildString,
                         Version.version(), Version.buildnum()),
                         // "Server version is {0} build {1}; client is {2} bld {3}"
                     versionOrlocalTCPPortLabel);
            }

            initMainPanelLayout(false, feats);  // complete the layout as appropriate for server
            validate();

            if ((client.net.practiceServer == null) && (vers < SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
                && (gi != null))
                gi.setEnabled(false);  // server too old for options, so don't use that button
        }

        public void showStatus(final String statusText, final boolean debugWarn)
        {
            status.setText(statusText);

            // If warning about debug during initial connect, show that.
            // That status message would be sent after VERSION.
            if (debugWarn)
                versionOrlocalTCPPortLabel.setText
                    (versionOrlocalTCPPortLabel.getText()
                     + client.strings.get("pcli.message.append.debugon"));  // ", debug is on"

            // If was trying to join a game, reset cursor from WAIT_CURSOR.
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }

        public void focusPassword()
        {
            pass.requestFocusInWindow();
        }

        public void setPassword(final String pw)
        {
            pass.setText(pw);
        }

        public void channelJoined(String channelName)
        {
            nick.setEditable(false);
            pass.setText("");
            pass.setEditable(false);
            if (! hasJoinedServer)
            {
                Container c = getParent();
                if ((c != null) && (c instanceof Frame))
                {
                    Frame fr = (Frame) c;
                    fr.setTitle(/*I*/fr.getTitle() + " [" + nick.getText() + "]"/*18N*/);
                }
                hasJoinedServer = true;
            }

            ChannelFrame cf = new ChannelFrame(channelName, this);
            cf.setVisible(true);
            channels.put(channelName, cf);
        }

        public void channelJoined(String channelName, String nickname)
        {
            ChannelFrame fr = channels.get(channelName);
            fr.print("*** " + client.strings.get("channel.joined", nickname) + "\n");  // "{0} has joined this channel."
            fr.addMember(nickname);
        }

        public void channelLeft(String channelName)
        {
            channels.remove(channelName);
        }

        public void channelLeft(String channelName, String nickname)
        {
            ChannelFrame fr = channels.get(channelName);
            fr.print("*** " + client.strings.get("channel.left", nickname) + "\n");  // "{0} left."
            fr.deleteMember(nickname);
        }

        public void channelMemberList(String channel, Collection<String> members)
        {
            ChannelFrame fr = channels.get(channel);

            for (String member : members)
                fr.addMember(member);

            fr.began();
        }

        public void channelDeleted(String channelName)
        {
            deleteFromList(channelName, chlist);
        }

        public void channelsClosed(String message)
        {
            for (ChannelFrame cf : channels.values())
            {
                cf.over(message);
            }
        }

        /**
         * add a new channel or game, put it in the list in alphabetical order
         *
         * @param thing  the thing to add to the list
         * @param lst    the list
         */
        public void addToList(String thing, java.awt.List lst)
        {
            if (lst.getItem(0).equals(" "))
            {
                lst.replaceItem(thing, 0);
                lst.select(0);
            }
            else
            {
                lst.add(thing, 0);

                /*
                   int i;
                   for(i=lst.getItemCount()-1;i>=0;i--)
                   if(lst.getItem(i).compareTo(thing)<0)
                   break;
                   lst.add(thing, i+1);
                   if(lst.getSelectedIndex()==-1)
                   lst.select(0);
                 */
            }
        }

        /**
         * delete a group
         *
         * @param thing   the thing to remove
         * @param lst     the list
         */
        public void deleteFromList(String thing, java.awt.List lst)
        {
            if (lst.getItemCount() == 1)
            {
                if (lst.getItem(0).equals(thing))
                {
                    lst.replaceItem(" ", 0);
                    lst.deselect(0);
                }

                return;
            }

            for (int i = lst.getItemCount() - 1; i >= 0; i--)
            {
                if (lst.getItem(i).equals(thing))
                {
                    lst.remove(i);
                }
            }

            if (lst.getSelectedIndex() == -1)
            {
                lst.select(lst.getItemCount() - 1);
            }
        }

        public void channelCreated(String channelName)
        {
            addToList(channelName, chlist);
        }

        public void channelList(Collection<String> channelNames, boolean isPractice)
        {
            if (! isPractice)
            {
                cardLayout.show(GameAwtDisplay.this, MAIN_PANEL);
                validate();

                status.setText
                    ((client.sFeatures.isActive(SOCServerFeatures.FEAT_CHANNELS))
                     ? NEED_NICKNAME_BEFORE_JOIN    // "First enter a nickname, then join a game or channel."
                     : NEED_NICKNAME_BEFORE_JOIN_G  // "First enter a nickname, then join a game."
                     );
            }

            for (String ch : channelNames)
            {
                addToList(ch, chlist);
            }

            if (! isPractice)
                nick.requestFocus();
        }

        public void messageBroadcast(String message)
        {
            for (ChannelFrame fr : channels.values())
            {
                fr.print("::: " + message + " :::");
            }
        }

        public void messageReceived(String channelName, String nickname, String message)
        {
            ChannelFrame fr = channels.get(channelName);

            if (fr != null)
            {
                if (!client.onIgnoreList(nickname))
                {
                    fr.print(nickname + ": " + message);
                }
            }
        }

        public void leaveGame(SOCGame game)
        {
            playerInterfaces.remove(game.getName());
        }

        public PlayerClientListener gameJoined(SOCGame game)
        {
            nick.setEditable(false);
            pass.setEditable(false);
            pass.setText("");
            if (! hasJoinedServer)
            {
                Container c = getParent();
                if ((c != null) && (c instanceof Frame))
                {
                    Frame fr = (Frame) c;
                    fr.setTitle(/*I*/fr.getTitle() + " [" + nick.getText() + "]"/*18N*/);
                }
                hasJoinedServer = true;
            }

            SOCPlayerInterface pi = new SOCPlayerInterface(game.getName(), GameAwtDisplay.this, game);
            System.err.println("L2325 new pi at " + System.currentTimeMillis());
            pi.setVisible(true);
            System.err.println("L2328 visible at " + System.currentTimeMillis());
            playerInterfaces.put(game.getName(), pi);

            return pi.getClientListener();
        }

        /**
         * Start the game-options info timeout
         * ({@link GameOptionsTimeoutTask}) at 5 seconds.
         * @see #gameOptionsCancelTimeoutTask()
         * @since 1.1.07
         */
        private void gameOptionsSetTimeoutTask()
        {
            if (gameOptsTask != null)
                gameOptsTask.cancel();
            gameOptsTask = new GameOptionsTimeoutTask(this, client.tcpServGameOpts);
            eventTimer.schedule(gameOptsTask, 5000 /* ms */ );
        }

        /**
         * Cancel the game-options info timeout.
         * @see #gameOptionsSetTimeoutTask()
         * @since 1.1.07
         */
        private void gameOptionsCancelTimeoutTask()
        {
            if (gameOptsTask != null)
            {
                gameOptsTask.cancel();
                gameOptsTask = null;
            }
        }

        public void optionsRequested()
        {
            gameOptionsSetTimeoutTask();
        }

        public void optionsReceived(ServerGametypeInfo opts, boolean isPractice)
        {
            gameOptionsCancelTimeoutTask();
            newGameOptsFrame = NewGameOptionsFrame.createAndShow
                (GameAwtDisplay.this, (String) null, opts.optionSet, isPractice, false);
        }

        public void optionsReceived(ServerGametypeInfo opts, boolean isPractice, boolean isDash, boolean hasAllNow)
        {
            final boolean newGameWaiting;
            final String gameInfoWaiting;
            synchronized(opts)
            {
                newGameWaiting = opts.newGameWaitingForOpts;
                gameInfoWaiting = opts.gameInfoWaitingForOpts;
            }

            if ((! isPractice) && isDash)
                gameOptionsCancelTimeoutTask();

            if (hasAllNow)
            {
                if (gameInfoWaiting != null)
                {
                    synchronized(opts)
                    {
                        opts.gameInfoWaitingForOpts = null;
                    }
                    final Map<String,SOCGameOption> gameOpts = client.serverGames.parseGameOptions(gameInfoWaiting);
                    if (! isPractice)
                        client.checkGameoptsForUnknownScenario(gameOpts);
                    newGameOptsFrame = NewGameOptionsFrame.createAndShow
                        (GameAwtDisplay.this, gameInfoWaiting, gameOpts, isPractice, true);
                }
                else if (newGameWaiting)
                {
                    synchronized(opts)
                    {
                        opts.newGameWaitingForOpts = false;
                    }
                    newGameOptsFrame = NewGameOptionsFrame.createAndShow
                        (GameAwtDisplay.this, (String) null, opts.optionSet, isPractice, false);
                }
            }
        }

        public void addToGameList
            (final boolean cannotJoin, String gameName, String gameOptsStr, final boolean addToSrvList)
        {
            if (addToSrvList)
            {
                if (client.serverGames == null)
                    client.serverGames = new SOCGameList();
                client.serverGames.addGame(gameName, gameOptsStr, cannotJoin);
            }

            if (cannotJoin)
            {
                // for display:
                // "(cannot join) "     TODO color would be nice
                gameName = GAMENAME_PREFIX_CANNOT_JOIN + gameName;
            }

            // String gameName = thing + STATSPREFEX + "-- -- -- --]";

            if ((gmlist.getItemCount() > 0) && (gmlist.getItem(0).equals(" ")))
            {
                gmlist.replaceItem(gameName, 0);
                gmlist.select(0);
                jg.setEnabled(true);
                gi.setEnabled((client.net.practiceServer != null)
                    || (client.sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS));
            }
            else
            {
                gmlist.add(gameName, 0);
            }
        }

        public void updateGameStats(String gameName, int[] scores, boolean[] robots)
        {
            //D.ebugPrintln("UPDATE GAME STATS FOR "+gameName);
            //TODO i18n how to?
            String testString = gameName + STATSPREFEX;

            for (int i = 0; i < gmlist.getItemCount(); i++)
            {
                if (gmlist.getItem(i).startsWith(testString))
                {
                    String updatedString = gameName + STATSPREFEX;

                    for (int pn = 0; pn < (scores.length - 1); pn++)
                    {
                        if (scores[pn] != -1)
                        {
                            if (robots[pn])
                            {
                                updatedString += "#";
                            }
                            else
                            {
                                updatedString += "o";
                            }

                            updatedString += (scores[pn] + " ");
                        }
                        else
                        {
                            updatedString += "-- ";
                        }
                    }

                    if (scores[scores.length - 1] != -1)
                    {
                        if (robots[scores.length - 1])
                        {
                            updatedString += "#";
                        }
                        else
                        {
                            updatedString += "o";
                        }

                        updatedString += (scores[scores.length - 1] + "]");
                    }
                    else
                    {
                        updatedString += "--]";
                    }

                    gmlist.replaceItem(updatedString, i);

                    break;
                }
            }
        }

        public boolean deleteFromGameList(String gameName, final boolean isPractice)
        {
            //String testString = gameName + STATSPREFEX;
            String testString = gameName;

            if (gmlist.getItemCount() == 1)
            {
                if (gmlist.getItem(0).startsWith(testString))
                {
                    gmlist.replaceItem(" ", 0);
                    gmlist.deselect(0);

                    if ((! isPractice) && (client.serverGames != null))
                    {
                        client.serverGames.deleteGame(gameName);  // may not be in there
                    }

                    gi.setEnabled(false);

                    return true;
                }

                return false;
            }

            boolean found = false;

            for (int i = gmlist.getItemCount() - 1; i >= 0; i--)
            {
                if (gmlist.getItem(i).startsWith(testString))
                {
                    gmlist.remove(i);
                    found = true;
                }
            }

            if (gmlist.getSelectedIndex() == -1)
            {
                gmlist.select(gmlist.getItemCount() - 1);
            }

            if (found && (! isPractice) && (client.serverGames != null))
            {
                client.serverGames.deleteGame(gameName);  // may not be in there
            }

            return found;
        }

        /**
         * send a text message to a channel
         *
         * @param ch   the name of the channel
         * @param mes  the message
         * @see SOCPlayerClient.GameManager#sendText(SOCGame, String)
         */
        public void chSend(String ch, String mes)
        {
            if (!doLocalCommand(ch, mes))
            {
                client.net.putNet(SOCTextMsg.toCmd(ch, client.nickname, mes));
            }
        }

        /**
         * Handle local client commands for channels.
         *
         * @param cmd  Local client command string, such as \ignore or \&shy;unignore
         * @return true if a command was handled
         * @see SOCPlayerInterface#doLocalCommand(String)
         */
        public boolean doLocalCommand(String ch, String cmd)
        {
            ChannelFrame fr = channels.get(ch);

            if (cmd.startsWith("\\ignore "))
            {
                String name = cmd.substring(8);
                client.addToIgnoreList(name);
                fr.print("* "+/*I*/"Ignoring " + name/*18N*/);
                printIgnoreList(fr);

                return true;
            }
            else if (cmd.startsWith("\\unignore "))
            {
                String name = cmd.substring(10);
                client.removeFromIgnoreList(name);
                fr.print("* "+/*I*/"Unignoring " + name/*18N*/);
                printIgnoreList(fr);

                return true;
            }
            else
            {
                return false;
            }
        }

        /** Print the current chat ignorelist in a channel. */
        protected void printIgnoreList(ChannelFrame fr)
        {
            fr.print("* "+/*I*/"Ignore list:"/*18N*/);

            for (String s : client.ignoreList)
            {
                fr.print("* " + s);
            }
        }

        /** Print the current chat ignorelist in a playerinterface. */
        protected void printIgnoreList(SOCPlayerInterface pi)
        {
            pi.print("* "+/*I*/"Ignore list:"/*18N*/);

            for (String s : client.ignoreList)
            {
                pi.print("* " + s);
            }
        }

        /**
         * When a practice game is starting, it may take a while to start server & game.
         * Set {@link Cursor#WAIT_CURSOR}.
         * The new-game window will clear this cursor back to default.
         */
        public void practiceGameStarting()
        {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        /**
         * Setup for locally hosting a TCP server.
         * If needed, a {@link ClientNetwork#localTCPServer local server} and robots are started, and client connects to it.
         * If parent is a Frame, set titlebar to show "server" and port#.
         * Show port number in {@link #versionOrlocalTCPPortLabel}.
         * If the {@link ClientNetwork#localTCPServer} is already created, does nothing.
         * If {@link ClientNetwork#connected connected} already, does nothing.
         *
         * @param tport Port number to host on; must be greater than zero.
         * @throws IllegalArgumentException If port is 0 or negative
         * @throws IllegalStateException  if already connected to a server
         */
        public void startLocalTCPServer(final int tport)
            throws IllegalArgumentException, IllegalStateException
        {
            if (client.net.localTCPServer != null)
            {
                return;  // Already set up
            }
            if (client.net.isConnected())
            {
                throw new IllegalStateException("Already connected to " + client.net.getHost());
            }
            if (tport < 1)
            {
                throw new IllegalArgumentException("Port must be positive: " + tport);
            }

            // May take a while to start server.
            // At end of method, we'll clear this cursor.
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            if (! client.net.initLocalServer(tport))
            {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                return;  // Unable to start local server, or bind to port
            }

            /**
             * StringManager.  Note that for TCP port#s we avoid {0,number} and use
             * Integer.toString() so the port number won't be formatted as "8,880".
             */
            final SOCStringManager strings = client.strings;
            final String tportStr = Integer.toString(tport);

            MouseAdapter mouseListener = new MouseAdapter()
            {
                /**
                 * When the local-server info label is clicked,
                 * show a popup with more info.
                 * @since 1.1.12
                 */
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    NotifyDialog.createAndShow
                        (GameAwtDisplay.this,
                         null,
                         strings.get("pcli.localserver.dialog", tportStr),
                         /*      "Other players connecting to your server\n" +
                                 "need only your IP address and port number.\n" +
                                 "No other server software install is needed.\n" +
                                 "Make sure your firewall allows inbound traffic on " +
                                 "port {0}."
                         */
                         strings.get("base.ok"),
                         true);
                }

                /**
                 * Set the hand cursor when entering the local-server info label.
                 * @since 1.1.12
                 */
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    if (e.getSource() == localTCPServerLabel)
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                /**
                 * Clear the cursor when exiting the local-server info label.
                 * @since 1.1.12
                 */
                @Override
                public void mouseExited(MouseEvent e)
                {
                    if (e.getSource() == localTCPServerLabel)
                        setCursor(Cursor.getDefaultCursor());
                }
            };

            // Set label
            localTCPServerLabel.setText(strings.get("pcli.localserver.running"));  // "Server is Running. (Click for info)"
            localTCPServerLabel.setFont(getFont().deriveFont(Font.BOLD));
            localTCPServerLabel.addMouseListener(mouseListener);
            versionOrlocalTCPPortLabel.setText(strings.get("pcli.localserver.port", tportStr));  // "Port: {0}"
            new AWTToolTip
                (strings.get("pcli.localserver.running.tip", tportStr, Version.version(), Version.buildnum()),
                    // "You are running a server on TCP port {0}. Version {1} bld {2}"
                versionOrlocalTCPPortLabel);
            versionOrlocalTCPPortLabel.addMouseListener(mouseListener);

            // Set titlebar, if present
            {
                Container parent = this.getParent();
                if ((parent != null) && (parent instanceof Frame))
                {
                    try
                    {
                        ((Frame) parent).setTitle
                            (strings.get("pcli.main.title.localserver", Version.version(), tportStr));
                            // "JSettlers server {0} - port {1}"
                    } catch (Throwable t) {
                        // no titlebar change is fine
                    }
                }
            }

            cardLayout.show(this, MESSAGE_PANEL);

            // Connect to it
            client.net.connect("localhost", tport);  // I18N: no need to localize this hostname

            // Ensure we can't "connect" to another, too
            if (connectOrPracticePane != null)
            {
                connectOrPracticePane.startedLocalServer();
            }

            // Ensure we can type a nickname, or click "New Game" if one is already entered.
            // This lets player create a game after starting a practice game (which sets nickname)
            // and then starting a server.
            if (nick.getText().trim().length() > 0)
                ng.setEnabled(true);
            else
                nick.setEditable(true);

            // Reset the cursor
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }


    /**
     * Nested class for processing incoming messages (treating).
     *<P>
     * Before v2.0.00, most of these fields and methods were part of the main {@link SOCPlayerClient} class.
     * @author paulbilnoski
     * @since 2.0.00
     */
    private class MessageTreater
    {
        private final SOCPlayerClient client;
        private final GameManager gmgr;

        public MessageTreater(SOCPlayerClient client)
        {
            if (client == null)
                throw new IllegalArgumentException("client is null");
            this.client = client;
            gmgr = client.getGameManager();

            if (gmgr == null)
                throw new IllegalArgumentException("client game manager is null");
        }

    /**
     * Treat the incoming messages.
     * Messages of unknown type are ignored
     * ({@code mes} will be null from {@link SOCMessage#toMsg(String)}).
     *
     * @param mes    the message
     * @param isPractice  Server is {@link ClientNetwork#practiceServer}, not tcp network
     */
    public void treat(SOCMessage mes, final boolean isPractice)
    {
        if (mes == null)
            return;  // Parsing error

        if (debugTraffic || D.ebugIsEnabled())
            soc.debug.D.ebugPrintln(mes.toString());

        try
        {
            switch (mes.getType())
            {

            /**
             * echo the server ping, to ensure we're still connected.
             * (ignored before version 1.1.08)
             */
            case SOCMessage.SERVERPING:
                handleSERVERPING((SOCServerPing) mes, isPractice);
                break;

            /**
             * server's version message
             */
            case SOCMessage.VERSION:
                handleVERSION(isPractice, (SOCVersion) mes);

                break;

            /**
             * status message
             */
            case SOCMessage.STATUSMESSAGE:
                handleSTATUSMESSAGE((SOCStatusMessage) mes, isPractice);

                break;

            /**
             * join channel authorization
             */
            case SOCMessage.JOINCHANNELAUTH:
                handleJOINCHANNELAUTH((SOCJoinChannelAuth) mes);

                break;

            /**
             * someone joined a channel
             */
            case SOCMessage.JOINCHANNEL:
                handleJOINCHANNEL((SOCJoinChannel) mes);

                break;

            /**
             * list of members for a chat channel
             */
            case SOCMessage.CHANNELMEMBERS:
                handleCHANNELMEMBERS((SOCChannelMembers) mes);

                break;

            /**
             * a new chat channel has been created
             */
            case SOCMessage.NEWCHANNEL:
                handleNEWCHANNEL((SOCNewChannel) mes);

                break;

            /**
             * List of chat channels on the server: Server connection is complete.
             * (sent at connect after VERSION, even if no channels)
             * Show main panel if not already showing; see handleCHANNELS javadoc.
             */
            case SOCMessage.CHANNELS:
                handleCHANNELS((SOCChannels) mes, isPractice);

                break;

            /**
             * text message to a chat channel
             */
            case SOCMessage.TEXTMSG:
                handleTEXTMSG((SOCTextMsg) mes);

                break;

            /**
             * someone left the chat channel
             */
            case SOCMessage.LEAVECHANNEL:
                handleLEAVECHANNEL((SOCLeaveChannel) mes);

                break;

            /**
             * delete a chat channel
             */
            case SOCMessage.DELETECHANNEL:
                handleDELETECHANNEL((SOCDeleteChannel) mes);

                break;

            /**
             * list of games on the server
             */
            case SOCMessage.GAMES:
                handleGAMES((SOCGames) mes, isPractice);

                break;

            /**
             * join game authorization
             */
            case SOCMessage.JOINGAMEAUTH:
                handleJOINGAMEAUTH((SOCJoinGameAuth) mes, isPractice);

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
                handleNEWGAME((SOCNewGame) mes, isPractice);

                break;

            /**
             * game has been destroyed
             */
            case SOCMessage.DELETEGAME:
                handleDELETEGAME((SOCDeleteGame) mes, isPractice);

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
             * The server wants this player to choose to rob cloth or rob resources.
             * Added 2012-11-17 for v2.0.00.
             */
            case SOCMessage.CHOOSEPLAYER:
                handleCHOOSEPLAYER((SOCChoosePlayer) mes);
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
            case SOCMessage.DEVCARDACTION:
                handleDEVCARDACTION(isPractice, (SOCDevCardAction) mes);

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
                handlePOTENTIALSETTLEMENTS((SOCPotentialSettlements) mes);

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
             * handle the roll dice prompt message
             * (it is now x's turn to roll the dice)
             */
            case SOCMessage.ROLLDICEPROMPT:
                handleROLLDICEPROMPT((SOCRollDicePrompt) mes);

                break;

            /**
             * handle board reset (new game with same players, same game name, new layout).
             */
            case SOCMessage.RESETBOARDAUTH:
                handleRESETBOARDAUTH((SOCResetBoardAuth) mes);

                break;

            /**
             * another player is requesting a board reset: we must vote
             */
            case SOCMessage.RESETBOARDVOTEREQUEST:
                handleRESETBOARDVOTEREQUEST((SOCResetBoardVoteRequest) mes);

                break;

            /**
             * another player has voted on a board reset request
             */
            case SOCMessage.RESETBOARDVOTE:
                handleRESETBOARDVOTE((SOCResetBoardVote) mes);

                break;

            /**
             * voting complete, board reset request rejected
             */
            case SOCMessage.RESETBOARDREJECT:
                handleRESETBOARDREJECT((SOCResetBoardReject) mes);

                break;

            /**
             * for game options (1.1.07)
             */
            case SOCMessage.GAMEOPTIONGETDEFAULTS:
                handleGAMEOPTIONGETDEFAULTS((SOCGameOptionGetDefaults) mes, isPractice);
                break;

            case SOCMessage.GAMEOPTIONINFO:
                handleGAMEOPTIONINFO((SOCGameOptionInfo) mes, isPractice);
                break;

            case SOCMessage.NEWGAMEWITHOPTIONS:
                handleNEWGAMEWITHOPTIONS((SOCNewGameWithOptions) mes, isPractice);
                break;

            case SOCMessage.GAMESWITHOPTIONS:
                handleGAMESWITHOPTIONS((SOCGamesWithOptions) mes, isPractice);
                break;

            /**
             * player stats (as of 20100312 (v 1.1.09))
             */
            case SOCMessage.PLAYERSTATS:
                handlePLAYERSTATS((SOCPlayerStats) mes);
                break;

            /**
             * debug piece Free Placement (as of 20110104 (v 1.1.12))
             */
            case SOCMessage.DEBUGFREEPLACE:
                handleDEBUGFREEPLACE((SOCDebugFreePlace) mes);
                break;

            /**
             * generic 'simple request' response from the server.
             * Added 2013-02-19 for v1.1.18.
             */
            case SOCMessage.SIMPLEREQUEST:
                handleSIMPLEREQUEST((SOCSimpleRequest) mes);
                break;

            /**
             * generic "simple action" announcements from the server.
             * Added 2013-09-04 for v1.1.19.
             */
            case SOCMessage.SIMPLEACTION:
                handleSIMPLEACTION((SOCSimpleAction) mes);
                break;

            /**
             * game server text and announcements.
             * Added 2013-09-05 for v2.0.00.
             */
            case SOCMessage.GAMESERVERTEXT:
                handleGAMESERVERTEXT((SOCGameServerText) mes);
                break;

            /**
             * All players' dice roll result resources.
             * Added 2013-09-20 for v2.0.00.
             */
            case SOCMessage.DICERESULTRESOURCES:
                handleDICERESULTRESOURCES((SOCDiceResultResources) mes);
                break;

            /**
             * move a previous piece (a ship) somewhere else on the board.
             * Added 2011-12-05 for v2.0.00.
             */
            case SOCMessage.MOVEPIECE:
                handleMOVEPIECE((SOCMovePiece) mes);
                break;

            /**
             * remove a piece (a ship) from the board in certain scenarios.
             * Added 2013-02-19 for v2.0.00.
             */
            case SOCMessage.REMOVEPIECE:
                handleREMOVEPIECE((SOCRemovePiece) mes);
                break;

            /**
             * pick resources to gain from the gold hex.
             * Added 2012-01-12 for v2.0.00.
             */
            case SOCMessage.PICKRESOURCESREQUEST:
                handlePICKRESOURCESREQUEST((SOCPickResourcesRequest) mes);
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

            /**
             * Text that a player has been awarded Special Victory Point(s).
             * Added 2012-12-21 for v2.0.00.
             */
            case SOCMessage.SVPTEXTMSG:
                handleSVPTEXTMSG((SOCSVPTextMessage) mes);
                break;

            /**
             * Set or clear a special edge on the board.
             * Added 2013-11-07 for v2.0.00.
             */
            case SOCMessage.BOARDSPECIALEDGE:
                handleBOARDSPECIALEDGE((SOCBoardSpecialEdge) mes);
                break;

            /**
             * a special inventory item action: either add or remove,
             * or we cannot play our requested item.
             * Added 2013-11-26 for v2.0.00.
             */
            case SOCMessage.INVENTORYITEMACTION:
                handleINVENTORYITEMACTION((SOCInventoryItemAction) mes);
                break;

            /**
             * Special Item change announcements.
             * Added 2014-04-16 for v2.0.00.
             */
            case SOCMessage.SETSPECIALITEM:
                handleSETSPECIALITEM(games, (SOCSetSpecialItem) mes);
                break;

            /**
             * Localized i18n strings for game items.
             * Added 2015-01-11 for v2.0.00.
             */
            case SOCMessage.LOCALIZEDSTRINGS:
                handleLOCALIZEDSTRINGS((SOCLocalizedStrings) mes, isPractice);
                break;

            /**
             * Updated scenario info.
             * Added 2015-09-21 for v2.0.00.
             */
            case SOCMessage.SCENARIOINFO:
                handleSCENARIOINFO((SOCScenarioInfo) mes, isPractice);
                break;

            }  // switch (mes.getType())
        }
        catch (Exception e)
        {
            System.out.println("SOCPlayerClient treat ERROR - " + e.getMessage());
            e.printStackTrace();
            System.out.println("  For message: " + mes);
        }

    }  // treat

    /**
     * Handle the "version" message, server's version report.
     * Ask server for game-option info if client's version differs.
     * If remote, store the server's version for {@link #getServerVersion(SOCGame)}
     * and display the version on the main panel.
     * (Local server's version is always {@link Version#versionNumber()}.)
     *
     * @param isPractice Is the server {@link ClientNetwork#practiceServer}, not remote?  Client can be connected
     *                only to one at a time.
     * @param mes  the message
     */
    private void handleVERSION(final boolean isPractice, SOCVersion mes)
    {
        D.ebugPrintln("handleVERSION: " + mes);
        int vers = mes.getVersionNumber();

        if (! isPractice)
        {
            sVersion = vers;
            sFeatures = (vers >= SOCServerFeatures.VERSION_FOR_SERVERFEATURES)
                ? new SOCServerFeatures(mes.localeOrFeats)
                : new SOCServerFeatures(true);

            gameDisplay.showVersion(vers, mes.getVersionString(), mes.getBuild(), sFeatures);
        }

        // If we ever require a minimum server version, would check that here.

        // Pre-1.1.06 versions would reply here with our client version.
        // That's been sent to server already in connect() in 1.1.06 and later.

        // Check known game options vs server's version. (added in 1.1.07)
        // Server's responses will add, remove or change our "known options".
        // In v2.0.00 and later, also checks for game option localized descriptions.
        final int cliVersion = Version.versionNumber();
        final boolean withTokenI18n =
            (cliLocale != null) && (isPractice || (sVersion >= SOCStringManager.VERSION_FOR_I18N))
            && ! ("en".equals(cliLocale.getLanguage()) && "US".equals(cliLocale.getCountry()));

        if ( ((! isPractice) && (sVersion > cliVersion))
            || (withTokenI18n && (isPractice || (sVersion == cliVersion))))
        {
            // Newer server: Ask it to list any options we don't know about yet.
            // Same version: Ask for all options with localized descs if available.
            if (! isPractice)
                gameDisplay.optionsRequested();
            gmgr.put(SOCGameOptionGetInfos.toCmd(null, withTokenI18n), isPractice);  // sends "-"
        }
        else if ((sVersion < cliVersion) && ! isPractice)
        {
            if (sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
            {
                // Older server: Look for options created or changed since server's version.
                // Ask it what it knows about them.
                List<SOCGameOption> tooNewOpts = SOCGameOption.optionsNewerThanVersion(sVersion, false, false, null);
                if ((tooNewOpts != null) && (sVersion < SOCGameOption.VERSION_FOR_LONGER_OPTNAMES) && ! isPractice)
                {
                    // Server is older than 2.0.00; we can't send it any long option names.
                    // Remove them from our set of options for games at this server.
                    if (tcpServGameOpts.optionSet == null)
                        tcpServGameOpts.optionSet = SOCGameOption.getAllKnownOptions();

                    Iterator<SOCGameOption> opi = tooNewOpts.iterator();
                    while (opi.hasNext())
                    {
                        final SOCGameOption op = opi.next();
                        //TODO i18n how to?
                        if ((op.key.length() > 3) || op.key.contains("_"))
                        {
                            tcpServGameOpts.optionSet.remove(op.key);
                            opi.remove();
                        }
                    }
                    if (tooNewOpts.isEmpty())
                        tooNewOpts = null;
                }

                if (tooNewOpts != null)
                {
                    if (! isPractice)
                        gameDisplay.optionsRequested();
                    gmgr.put(SOCGameOptionGetInfos.toCmd(tooNewOpts, withTokenI18n), isPractice);
                }
                else if (withTokenI18n && ! isPractice)
                {
                    // server is older than client but understands i18n: request gameopt localized strings

                    gmgr.put(SOCGameOptionGetInfos.toCmd(null, true), false);  // sends opt list "-,?I18N"
                }
            } else {
                // server is too old to understand options. Can't happen with local practice srv,
                // because that's our version (it runs from our own JAR file).

                if (! isPractice)
                {
                    tcpServGameOpts.noMoreOptions(true);
                    tcpServGameOpts.optionSet = null;
                }
            }
        } else {
            // sVersion == cliVersion, so we have same code as server for getAllKnownOptions.
            // For practice games, optionSet may already be initialized, so check vs null.
            ServerGametypeInfo opts = (isPractice ? practiceServGameOpts : tcpServGameOpts);
            if (opts.optionSet == null)
                opts.optionSet = SOCGameOption.getAllKnownOptions();
            opts.noMoreOptions(isPractice);  // defaults not known unless it's practice
        }
    }

    /**
     * handle the {@link SOCStatusMessage "status"} message.
     * Used for server events, also used if player tries to join a game
     * but their nickname is not OK.
     *<P>
     * Also used (v1.1.19 and newer) as a reply to {@link SOCAuthRequest} sent
     * before showing {@link NewGameOptionsFrame}, so check whether the
     * {@link SOCPlayerClient#isNGOFWaitingForAuthStatus isNGOFWaitingForAuthStatus client.isNGOFWaitingForAuthStatus}
     * flag is set.
     *
     * @param mes  the message
     * @param isPractice from practice server, not remote server?
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes, final boolean isPractice)
    {
        System.err.println("L2045 statusmsg at " + System.currentTimeMillis());
        final int sv = mes.getStatusValue();
        final String statusText = mes.getStatus();

        final boolean srvDebugMode;
        if (isPractice || (sVersion >= 2000))
            srvDebugMode = (sv == SOCStatusMessage.SV_OK_DEBUG_MODE_ON);
        else
            srvDebugMode = statusText.toLowerCase().contains("debug");

        gameDisplay.showStatus(statusText, srvDebugMode);

        // Are we waiting for auth response in order to show NGOF?
        if ((! isPractice) && client.isNGOFWaitingForAuthStatus)
        {
            client.isNGOFWaitingForAuthStatus = false;

            if (sv == SOCStatusMessage.SV_OK)
            {
                client.gotPassword = true;

                EventQueue.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        gameDisplay.gameWithOptionsBeginSetup(false, true);
                    }
                });
            }
        }

        switch (sv)
        {
        case SOCStatusMessage.SV_PW_WRONG:
            gameDisplay.focusPassword();
            break;

        case SOCStatusMessage.SV_NEWGAME_OPTION_VALUE_TOONEW:
            {
                // Extract game name and failing game-opt keynames,
                // and pop up an error message window.
                String errMsg;
                StringTokenizer st = new StringTokenizer(statusText, SOCMessage.sep2);
                try
                {
                    String gameName = null;
                    Vector<String> optNames = new Vector<String>();
                    errMsg = st.nextToken();
                    gameName = st.nextToken();
                    while (st.hasMoreTokens())
                        optNames.addElement(st.nextToken());
                    StringBuffer opts = new StringBuffer();
                    final Map<String, SOCGameOption> knowns =
                        isPractice ? practiceServGameOpts.optionSet : tcpServGameOpts.optionSet;
                    for (int i = 0; i < optNames.size(); ++i)
                    {
                        opts.append('\n');
                        String oname = optNames.elementAt(i);
                        SOCGameOption oinfo = null;
                        if (knowns != null)
                            oinfo = knowns.get(oname);
                        if (oinfo != null)
                            oname = oinfo.getDesc();
                        opts.append(strings.get("options.error.valuesproblem.which", oname));
                    }
                    errMsg = strings.get("options.error.valuesproblem", gameName, errMsg, opts.toString());
                }
                catch (Throwable t)
                {
                    errMsg = statusText;  // fallback, not expected to happen
                }

                gameDisplay.showErrorDialog(errMsg, strings.get("base.cancel"));
            }
            break;
        }
    }

    /**
     * handle the "join channel authorization" message
     * @param mes  the message
     */
    protected void handleJOINCHANNELAUTH(SOCJoinChannelAuth mes)
    {
        gotPassword = true;
        gameDisplay.channelJoined(mes.getChannel());
    }

    /**
     * handle the "join channel" message
     * @param mes  the message
     */
    protected void handleJOINCHANNEL(SOCJoinChannel mes)
    {
        gameDisplay.channelJoined(mes.getChannel(), mes.getNickname());
    }

    /**
     * handle the "channel members" message
     * @param mes  the message
     */
    protected void handleCHANNELMEMBERS(SOCChannelMembers mes)
    {
        gameDisplay.channelMemberList(mes.getChannel(), mes.getMembers());
    }

    /**
     * handle the "new channel" message
     * @param mes  the message
     */
    protected void handleNEWCHANNEL(SOCNewChannel mes)
    {
        gameDisplay.channelCreated(mes.getChannel());
    }

    /**
     * handle the "list of channels" message; this message indicates that
     * we're newly connected to the server, and is sent even if the server
     * isn't using {@link SOCServerFeatures#FEAT_CHANNELS}: Server connection is complete.
     * Unless {@code isPractice}, show {@link #MAIN_PANEL}.
     * @param mes  the message
     * @param isPractice is the server actually {@link ClientNetwork#practiceServer} (practice game)?
     */
    protected void handleCHANNELS(SOCChannels mes, final boolean isPractice)
    {
        gameDisplay.channelList(mes.getChannels(), isPractice);
    }

    /**
     * handle a broadcast text message
     * @param mes  the message
     */
    protected void handleBCASTTEXTMSG(SOCBCastTextMsg mes)
    {
        gameDisplay.messageBroadcast(mes.getText());

        for (PlayerClientListener pcl : clientListeners.values())
        {
            pcl.messageBroadcast(mes.getText());
        }
    }

    /**
     * handle a text message received in a channel
     * @param mes  the message
     * @see #handleGAMETEXTMSG(SOCGameTextMsg)
     */
    protected void handleTEXTMSG(SOCTextMsg mes)
    {
        gameDisplay.messageReceived(mes.getChannel(), mes.getNickname(), mes.getText());
    }

    /**
     * handle the "leave channel" message
     * @param mes  the message
     */
    protected void handleLEAVECHANNEL(SOCLeaveChannel mes)
    {
        gameDisplay.channelLeft(mes.getChannel(), mes.getNickname());
    }

    /**
     * handle the "delete channel" message
     * @param mes  the message
     */
    protected void handleDELETECHANNEL(SOCDeleteChannel mes)
    {
        gameDisplay.channelDeleted(mes.getChannel());
    }

    /**
     * handle the "list of games" message
     * @param mes  the message
     */
    protected void handleGAMES(SOCGames mes, final boolean isPractice)
    {
        // Any game's name in this msg may start with the "unjoinable" prefix
        // SOCGames.MARKER_THIS_GAME_UNJOINABLE.
        // We'll recognize and remove it in methods called from here.

        Collection<String> gameNamesEnum = mes.getGames();

        if (! isPractice)  // practiceServer's gameoption data is set up in handleVERSION
        {
            if (serverGames == null)
                serverGames = new SOCGameList();
            serverGames.addGames(gameNamesEnum, Version.versionNumber());

            // No more game-option info will be received,
            // because that's always sent before game names are sent.
            // We may still ask for GAMEOPTIONGETDEFAULTS if asking to create a game,
            // but that will happen when user clicks that button, not yet.
            tcpServGameOpts.noMoreOptions(false);

            // Reset enum for addToGameList call; serverGames.addGames has consumed it.
            gameNamesEnum = mes.getGames();
        }

        for (String gn : gameNamesEnum)
        {
            addToGameList(gn, null, false);
        }
    }

    /**
     * handle the "join game authorization" message: create new {@link SOCGame} and
     * {@link SOCPlayerInterface} so user can join the game
     * @param mes  the message
     * @param isPractice server is practiceServer (not normal tcp network)
     */
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes, final boolean isPractice)
    {
        System.err.println("L2299 joingameauth at " + System.currentTimeMillis());
        gotPassword = true;

        final String gaName = mes.getGame();
        Map<String,SOCGameOption> gameOpts;
        if (isPractice)
        {
            gameOpts = net.practiceServer.getGameOptions(gaName);
            if (gameOpts != null)
                gameOpts = new HashMap<String,SOCGameOption>(gameOpts);  // changes here shouldn't change practiceServ's copy
        } else {
            if (serverGames != null)
                gameOpts = serverGames.parseGameOptions(gaName);
            else
                gameOpts = null;
        }
        System.err.println("L2318 past opts at " + System.currentTimeMillis());

        SOCGame ga = new SOCGame(gaName, gameOpts);
        if (ga != null)
        {
            ga.isPractice = isPractice;
            PlayerClientListener clientListener = gameDisplay.gameJoined(ga);
            clientListeners.put(gaName, clientListener);
            games.put(gaName, ga);
        }
        System.err.println("L2332 handlejoin done at " + System.currentTimeMillis());
    }

    /**
     * handle the "join game" message
     * @param mes  the message
     */
    protected void handleJOINGAME(SOCJoinGame mes)
    {
        final String gn = mes.getGame();
        final String name = mes.getNickname();
        if (name == null)
            return;

        PlayerClientListener pcl = clientListeners.get(gn);
        pcl.playerJoined(name);
    }

    /**
     * handle the "leave game" message
     * @param mes  the message
     */
    protected void handleLEAVEGAME(SOCLeaveGame mes)
    {
        String gn = mes.getGame();
        SOCGame ga = games.get(gn);

        if (ga != null)
        {
            final String name = mes.getNickname();
            final SOCPlayer player = ga.getPlayer(name);

            // Give the listener a chance to clean up while the player is still in the game
            PlayerClientListener pcl = clientListeners.get(gn);
            pcl.playerLeft(name, player);

            if (player != null)
            {
                //
                //  This user was not a spectator.
                //  Remove first from listener, then from game data.
                //
                ga.removePlayer(name);
            }
        }
    }

    /**
     * handle the "new game" message
     * @param mes  the message
     */
    protected void handleNEWGAME(SOCNewGame mes, final boolean isPractice)
    {
        addToGameList(mes.getGame(), null, ! isPractice);
    }

    /**
     * handle the "delete game" message
     * @param mes  the message
     */
    protected void handleDELETEGAME(SOCDeleteGame mes, final boolean isPractice)
    {
        if (! gameDisplay.deleteFromGameList(mes.getGame(), isPractice))
            gameDisplay.deleteFromGameList(GAMENAME_PREFIX_CANNOT_JOIN + mes.getGame(), isPractice);
    }

    /**
     * handle the "game members" message, the server's hint that it's almost
     * done sending us the complete game state in response to JOINGAME.
     * @param mes  the message
     */
    protected void handleGAMEMEMBERS(final SOCGameMembers mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.membersListed(mes.getMembers());
    }

    /**
     * handle the "game stats" message
     */
    protected void handleGAMESTATS(SOCGameStats mes)
    {
        String ga = mes.getGame();
        int[] scores = mes.getScores();

        // Update game list (initial window)
        gameDisplay.updateGameStats(ga, scores, mes.getRobotSeats());

        // If we're playing in a game, update the scores. (SOCPlayerInterface)
        // This is used to show the true scores, including hidden
        // victory-point cards, at the game's end.
        updateGameEndStats(ga, scores);
    }

    /**
     * handle the "game text message" message.
     * Messages not from Server go to the chat area.
     * Messages from Server go to the game text window.
     * Urgent messages from Server (starting with ">>>") also go to the chat area,
     * which has less activity, so they are harder to miss.
     *
     * @param mes  the message
     * @see #handleGAMESERVERTEXT(SOCGameServerText)
     * @see #handleTEXTMSG(SOCTextMsg)
     */
    protected void handleGAMETEXTMSG(SOCGameTextMsg mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;

        String fromNickname = mes.getNickname();
        if (fromNickname.equals(SOCGameTextMsg.SERVERNAME))  // for pre-2.0.00 servers not using SOCGameServerText
            fromNickname = null;
        pcl.messageSent(fromNickname, mes.getText());
    }

    /**
     * handle the "player sitting down" message
     * @param mes  the message
     */
    protected void handleSITDOWN(final SOCSitDown mes)
    {
        /**
         * tell the game that a player is sitting
         */
        final SOCGame ga = games.get(mes.getGame());
        if (ga != null)
        {
            final int mesPN = mes.getPlayerNumber();

            ga.takeMonitor();
            SOCPlayer player = null;
            try
            {
                ga.addPlayer(mes.getNickname(), mesPN);

                player = ga.getPlayer(mesPN);
                /**
                 * set the robot flag
                 */
                player.setRobotFlag(mes.isRobot(), false);
            }
            catch (Exception e)
            {
                System.out.println("Exception caught - " + e);
                e.printStackTrace();

                return;
            }
            finally
            {
                ga.releaseMonitor();
            }

            /**
             * tell the GUI that a player is sitting
             */
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.playerSitdown(mesPN, mes.getNickname());

            /**
             * let the board panel & building panel find our player object if we sat down
             */
            if (nickname.equals(mes.getNickname()))
            {
                /**
                 * change the face (this is so that old faces don't 'stick')
                 */
                if (! ga.isBoardReset() && (ga.getGameState() < SOCGame.START1A))
                {
                    ga.getPlayer(mesPN).setFaceId(lastFaceChange);
                    gmgr.changeFace(ga, lastFaceChange);
                }
            }
        }
    }

    /**
     * Handle the old "board layout" message (original 4-player board, no options).
     * Most game boards will call {@link #handleBOARDLAYOUT2(SOCBoardLayout2)} instead.
     * @param mes  the message
     */
    protected void handleBOARDLAYOUT(SOCBoardLayout mes)
    {
        System.err.println("L2561 boardlayout at " + System.currentTimeMillis());
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            // BOARDLAYOUT is always the v1 board encoding (oldest format)
            SOCBoard bd = ga.getBoard();
            bd.setHexLayout(mes.getHexLayout());
            bd.setNumberLayout(mes.getNumberLayout());
            bd.setRobberHex(mes.getRobberHex(), false);
            ga.updateAtBoardLayout();

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.boardLayoutUpdated();
        }
    }

    /**
     * echo the server ping, to ensure we're still connected.
     * (ignored before version 1.1.08)
     * @since 1.1.08
     */
    private void handleSERVERPING(SOCServerPing mes, final boolean isPractice)
    {
        int timeval = mes.getSleepTime();
        if (timeval != -1)
        {
            gmgr.put(mes.toCmd(), isPractice);
        } else {
            net.ex = new RuntimeException(strings.get("pcli.error.kicked.samename"));  // "Kicked by player with same name."
            client.dispose();
        }
    }

    /**
     * Handle the "board layout" message, in its usual format.
     * (Some simple games can use the old {@link #handleBOARDLAYOUT(SOCBoardLayout)} instead.)
     * @param mes  the message
     * @since 1.1.08
     */
    protected void handleBOARDLAYOUT2(SOCBoardLayout2 mes)
    {
        System.err.println("L2602 boardlayout2 at " + System.currentTimeMillis());
        if (SOCDisplaylessPlayerClient.handleBOARDLAYOUT2(games, mes))
        {
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.boardLayoutUpdated();
        }
    }

    /**
     * handle the "start game" message
     * @param mes  the message
     */
    protected void handleSTARTGAME(SOCStartGame mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        final SOCGame ga = games.get(mes.getGame());
        if ((pcl == null) || (ga == null))
            return;

        if (ga.getGameState() == SOCGame.NEW)
            // skip this call if handleGAMESTATE already called it
            pcl.gameStarted();
    }

    /**
     * handle the "game state" message
     * @param mes  the message
     */
    protected void handleGAMESTATE(SOCGameState mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            final boolean gameStarted = (ga.getGameState() == SOCGame.NEW && mes.getState() != SOCGame.NEW);
            final int newState = mes.getState();

            ga.setGameState(newState);
            if (gameStarted)
            {
                // call here, not just in handleSTARTGAME, in case we joined a game in progress
                pcl.gameStarted();
            }
            pcl.gameStateChanged(newState);
        }
    }

    /**
     * handle the "set turn" message
     * @param mes  the message
     */
    protected void handleSETTURN(SOCSetTurn mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // <--- Early return: not playing in that one ----

        final int pn = mes.getPlayerNumber();
        ga.setCurrentPlayerNumber(pn);

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.playerTurnSet(pn);
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
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);

        if (ga != null)
        {
            final int pnum = mes.getPlayerNumber();
            ga.setCurrentPlayerNumber(pnum);
            ga.updateAtTurn();
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.playerTurnSet(pnum);
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
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            PlayerClientListener.UpdateType utype = null;  // If not null, update this type's amount display
            final int etype = mes.getElementType();

            switch (etype)
            {
            case SOCPlayerElement.ROADS:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.ROAD);
                utype = PlayerClientListener.UpdateType.Road;
                break;

            case SOCPlayerElement.SETTLEMENTS:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.SETTLEMENT);
                utype = PlayerClientListener.UpdateType.Settlement;
                break;

            case SOCPlayerElement.CITIES:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.CITY);
                utype = PlayerClientListener.UpdateType.City;
                break;

            case SOCPlayerElement.SHIPS:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.SHIP);
                utype = PlayerClientListener.UpdateType.Ship;
                break;

            case SOCPlayerElement.NUMKNIGHTS:
                // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
                {
                    final SOCPlayer oldLargestArmyPlayer = ga.getPlayerWithLargestArmy();
                    SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numKnights
                        (mes, pl, ga);
                    utype = PlayerClientListener.UpdateType.Knight;

                    // Check for change in largest-army player; update handpanels'
                    // LARGESTARMY and VICTORYPOINTS counters if so, and
                    // announce with text message.
                    pcl.largestArmyRefresh(oldLargestArmyPlayer, ga.getPlayerWithLargestArmy());
                }

                break;

            case SOCPlayerElement.CLAY:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.CLAY);
                utype = PlayerClientListener.UpdateType.Clay;
                break;

            case SOCPlayerElement.ORE:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.ORE);
                utype = PlayerClientListener.UpdateType.Ore;
                break;

            case SOCPlayerElement.SHEEP:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.SHEEP);
                utype = PlayerClientListener.UpdateType.Sheep;
                break;

            case SOCPlayerElement.WHEAT:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.WHEAT);
                utype = PlayerClientListener.UpdateType.Wheat;
                break;

            case SOCPlayerElement.WOOD:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.WOOD);
                utype = PlayerClientListener.UpdateType.Wood;
                break;

            case SOCPlayerElement.UNKNOWN:
                /**
                 * Note: if losing unknown resources, we first
                 * convert player's known resources to unknown resources,
                 * then remove mes's unknown resources from player.
                 */
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.UNKNOWN);
                utype = PlayerClientListener.UpdateType.Unknown;
                break;

            case SOCPlayerElement.ASK_SPECIAL_BUILD:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple(mes, ga, pl, pn);
                // This case is not really an element update, so route as a 'request'
                pcl.requestedSpecialBuild(pl);
                break;

            case SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple(mes, ga, pl, pn);
                pcl.requestedGoldResourceCountUpdated(pl, 0);
                break;

            case SOCPlayerElement.SCENARIO_SVP:
                pl.setSpecialVP(mes.getValue());
                utype = PlayerClientListener.UpdateType.SpecialVictoryPoints;
                break;

            case SOCPlayerElement.SCENARIO_PLAYEREVENTS_BITMASK:
            case SOCPlayerElement.SCENARIO_SVP_LANDAREAS_BITMASK:
            case SOCPlayerElement.STARTING_LANDAREAS:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple(mes, ga, pl, pn);
                break;

            case SOCPlayerElement.SCENARIO_CLOTH_COUNT:
                if (pn != -1)
                {
                    pl.setCloth(mes.getValue());
                } else {
                    ((SOCBoardLarge) (ga.getBoard())).setCloth(mes.getValue());
                }
                utype = PlayerClientListener.UpdateType.Cloth;
                break;

            case SOCPlayerElement.SCENARIO_WARSHIP_COUNT:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple(mes, ga, pl, pn);
                utype = PlayerClientListener.UpdateType.Warship;
                break;

            }

            if (pcl != null)
            {
                if (utype != null)
                    pcl.playerElementUpdated(pl, utype);
            }
        }
    }

    /**
     * handle "resource count" message
     * @param mes  the message
     */
    protected void handleRESOURCECOUNT(SOCResourceCount mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayer pl = ga.getPlayer(mes.getPlayerNumber());
            PlayerClientListener pcl = clientListeners.get(mes.getGame());

            if (mes.getCount() != pl.getResources().getTotal())
            {
                SOCResourceSet rsrcs = pl.getResources();

                if (D.ebugOn)
                {
                    //pi.print(">>> RESOURCE COUNT ERROR: "+mes.getCount()+ " != "+rsrcs.getTotal());
                }

                boolean isClientPlayer = pl.getName().equals(client.getNickname());

                //
                //  fix it
                //

                if (! isClientPlayer)
                {
                    rsrcs.clear();
                    rsrcs.setAmount(mes.getCount(), SOCResourceConstants.UNKNOWN);
                    pcl.playerResourcesUpdated(pl);
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
        final String gameName = mes.getGame();
        SOCGame ga = games.get(gameName);
        if (ga == null)
            throw new IllegalStateException("No game found for name '"+gameName+"'");

        final int cpn = ga.getCurrentPlayerNumber();
        SOCPlayer p = null;
        if (cpn >= 0)
            p = ga.getPlayer(cpn);

        final int roll = mes.getResult();
        final SOCPlayer player = p;

        // update game state
        ga.setCurrentDice(roll);

        // notify listener
        PlayerClientListener listener = clientListeners.get(gameName);
        listener.diceRolled(player, roll);
    }

    /**
     * handle the "put piece" message
     * @param mes  the message
     */
    protected void handlePUTPIECE(SOCPutPiece mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        final int coord = mes.getCoordinates();
        final int ptype = mes.getPieceType();

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;
        pcl.playerPiecePlaced(player, coord, ptype);
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
     * @since 1.1.00
     */
    protected void handleCANCELBUILDREQUEST(SOCCancelBuildRequest mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final int ptype = mes.getPieceType();
        final SOCPlayer pl;
        if (ptype >= SOCPlayingPiece.SETTLEMENT)
        {
            final int sta = ga.getGameState();
            if ((sta != SOCGame.START1B) && (sta != SOCGame.START2B) && (sta != SOCGame.START3B))
            {
                // The human player gets a text message from the server informing
                // about the bad piece placement.  So, we can ignore this message type.
                return;
            }
            if (ptype != SOCPlayingPiece.SETTLEMENT)
                return;

            pl = ga.getPlayer(ga.getCurrentPlayerNumber());
            SOCSettlement pp = new SOCSettlement(pl, pl.getLastSettlementCoord(), null);
            ga.undoPutInitSettlement(pp);
        } else {
            // ptype is -3 (SOCCancelBuildRequest.INV_ITEM_PLACE_CANCEL)
            pl = ga.getPlayer(ga.getCurrentPlayerNumber());
        }

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.buildRequestCanceled(pl);
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
            if (newHex > 0)
                ga.getBoard().setRobberHex(newHex, true);
            else
                ((SOCBoardLarge) ga.getBoard()).setPirateHex(-newHex, true);

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.robberMoved();
        }
    }

    /**
     * handle the "discard request" message
     * @param mes  the message
     */
    protected void handleDISCARDREQUEST(SOCDiscardRequest mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedDiscard(mes.getNumberOfDiscards());
    }

    /**
     * handle the "pick resources request" message
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handlePICKRESOURCESREQUEST(SOCPickResourcesRequest mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedResourceSelect(mes.getParam());
    }

    /**
     * handle the "choose player request" message
     * @param mes  the message
     */
    protected void handleCHOOSEPLAYERREQUEST(SOCChoosePlayerRequest mes)
    {
        SOCGame game = games.get(mes.getGame());
        final int maxPl = game.maxPlayers;
        final boolean[] ch = mes.getChoices();
        final boolean allowChooseNone = ((ch.length > maxPl) && ch[maxPl]);  // for scenario SC_PIRI

        List<SOCPlayer> choices = new ArrayList<SOCPlayer>();
        for (int i = 0; i < maxPl; i++)
        {
            if (ch[i])
            {
                SOCPlayer p = game.getPlayer(i);
                choices.add(p);
            }
        }

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedChoosePlayer(choices, allowChooseNone);
    }

    /**
     * The server wants this player to choose to rob cloth or rob resources,
     * after moving the pirate ship.  Added 2012-11-17 for v2.0.00.
     */
    protected void handleCHOOSEPLAYER(SOCChoosePlayer mes)
    {
        SOCGame ga = games.get(mes.getGame());
        int victimPlayerNumber = mes.getChoice();
        SOCPlayer player = ga.getPlayer(victimPlayerNumber);

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedChooseRobResourceType(player);
    }

    /**
     * handle the "make offer" message
     * @param mes  the message
     */
    protected void handleMAKEOFFER(final SOCMakeOffer mes)
    {
        final SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            SOCTradeOffer offer = mes.getOffer();
            SOCPlayer from = ga.getPlayer(offer.getFrom());
            from.setCurrentOffer(offer);

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.requestedTrade(from);
        }
    }

    /**
     * handle the "clear offer" message
     * @param mes  the message
     */
    protected void handleCLEAROFFER(final SOCClearOffer mes)
    {
        final SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            final int pn = mes.getPlayerNumber();
            SOCPlayer player = null;
            if (pn != -1)
                player = ga.getPlayer(pn);

            if (pn != -1)
            {
                ga.getPlayer(pn).setCurrentOffer(null);
            } else {
                for (int i = 0; i < ga.maxPlayers; ++i)
                {
                    ga.getPlayer(i).setCurrentOffer(null);
                }
            }

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.requestedTradeClear(player);
        }
    }

    /**
     * handle the "reject offer" message
     * @param mes  the message
     */
    protected void handleREJECTOFFER(SOCRejectOffer mes)
    {
        SOCGame ga = games.get(mes.getGame());
        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedTradeRejection(player);
    }

    /**
     * handle the "clear trade message" message
     * @param mes  the message
     */
    protected void handleCLEARTRADEMSG(SOCClearTradeMsg mes)
    {
        SOCGame ga = games.get(mes.getGame());
        int pn = mes.getPlayerNumber();
        SOCPlayer player = null;
        if (pn != -1)
            player = ga.getPlayer(pn);

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedTradeReset(player);
    }

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
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            if (pcl != null)
                pcl.devCardDeckUpdated();
        }
    }

    /**
     * handle the "development card action" message
     * @param mes  the message
     */
    protected void handleDEVCARDACTION(final boolean isPractice, final SOCDevCardAction mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            final int mesPN = mes.getPlayerNumber();
            SOCPlayer player = ga.getPlayer(mesPN);

            int ctype = mes.getCardType();
            if ((! isPractice) && (sVersion < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
            {
                if (ctype == SOCDevCardConstants.KNIGHT_FOR_VERS_1_X)
                    ctype = SOCDevCardConstants.KNIGHT;
                else if (ctype == SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X)
                    ctype = SOCDevCardConstants.UNKNOWN;
            }

            final int act = mes.getAction();
            switch (act)
            {
            case SOCDevCardAction.DRAW:
                player.getInventory().addDevCard(1, SOCInventory.NEW, ctype);

                break;

            case SOCDevCardAction.PLAY:
                player.getInventory().removeDevCard(SOCInventory.OLD, ctype);
                // JM temp debug:
                if (ctype != mes.getCardType())
                    System.out.println("L3947: play dev card type " + ctype + "; srv has " + mes.getCardType());

                break;

            case SOCDevCardAction.ADDOLD:
                player.getInventory().addDevCard(1, SOCInventory.OLD, ctype);

                break;

            case SOCDevCardAction.ADDNEW:
                player.getInventory().addDevCard(1, SOCInventory.NEW, ctype);

                break;
            }

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.playerDevCardUpdated(player, (act == SOCDevCardAction.ADDOLD));
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
     * @throws IllegalStateException if the board has
     *     {@link SOCBoardLarge#getAddedLayoutPart(String) SOCBoardLarge.getAddedLayoutPart("AL")} != {@code null} but
     *     badly formed (node list number 0, or a node list number not followed by a land area number).
     *     This Added Layout Part is rarely used, and this would be discovered quickly while testing
     *     the board layout that contained it.
     */
    protected void handlePOTENTIALSETTLEMENTS(SOCPotentialSettlements mes)
        throws IllegalStateException
    {
        System.err.println("L3292 potentialsettles at " + System.currentTimeMillis());
        SOCDisplaylessPlayerClient.handlePOTENTIALSETTLEMENTS(mes, games);

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl != null)
            pcl.boardPotentialsUpdated();
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
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            player.setFaceId(mes.getFaceId());
            pcl.playerFaceChanged(player, mes.getFaceId());
        }
    }

    /**
     * handle the "reject connection" message
     * @param mes  the message
     */
    protected void handleREJECTCONNECTION(SOCRejectConnection mes)
    {
        net.disconnect();

        gameDisplay.showErrorPanel(mes.getText(), (net.ex_P == null));
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
            SOCPlayer oldLongestRoadPlayer = ga.getPlayerWithLongestRoad();
            SOCPlayer newLongestRoadPlayer;
            if (mes.getPlayerNumber() == -1)
            {
                newLongestRoadPlayer = null;
            }
            else
            {
                newLongestRoadPlayer = ga.getPlayer(mes.getPlayerNumber());
            }
            ga.setPlayerWithLongestRoad(newLongestRoadPlayer);

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            // Update player victory points; check for and announce change in longest road
            pcl.longestRoadRefresh(oldLongestRoadPlayer, newLongestRoadPlayer);
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
            SOCPlayer oldLargestArmyPlayer = ga.getPlayerWithLargestArmy();
            SOCPlayer newLargestArmyPlayer;
            if (mes.getPlayerNumber() == -1)
            {
                newLargestArmyPlayer = null;
            }
            else
            {
                newLargestArmyPlayer = ga.getPlayer(mes.getPlayerNumber());
            }
            ga.setPlayerWithLargestArmy(newLargestArmyPlayer);

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            // Update player victory points; check for and announce change in largest army
            pcl.largestArmyRefresh(oldLargestArmyPlayer, newLargestArmyPlayer);
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
            ga.setSeatLock(mes.getPlayerNumber(), mes.getLockState());
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.seatLockUpdated();
        }
    }

    /**
     * handle the "roll dice prompt" message;
     *   if we're in a game and we're the dice roller,
     *   either set the auto-roll timer, or prompt to roll or choose card.
     *
     * @param mes  the message
     */
    protected void handleROLLDICEPROMPT(SOCRollDicePrompt mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games
        pcl.requestedDiceRoll();
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
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCGame greset = ga.resetAsCopy();
        greset.isPractice = ga.isPractice;
        games.put(gname, greset);
        pcl.boardReset(greset, mes.getRejoinPlayerNumber(), mes.getRequestingPlayerNumber());
        ga.destroyGame();
    }

    /**
     * a player is requesting a board reset: we must update
     * local game state, and vote unless we are the requester.
     *
     * @param mes  the message
     */
    protected void handleRESETBOARDVOTEREQUEST(SOCResetBoardVoteRequest mes)
    {
        String gname = mes.getGame();
        SOCGame ga = games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCPlayer player = ga.getPlayer(mes.getRequestingPlayer());
        pcl.boardResetVoteRequested(player);
    }

    /**
     * another player has voted on a board reset request: display the vote.
     *
     * @param mes  the message
     */
    protected void handleRESETBOARDVOTE(SOCResetBoardVote mes)
    {
        String gname = mes.getGame();
        SOCGame ga = games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        pcl.boardResetVoteCast(player, mes.getPlayerVote());
    }

    /**
     * voting complete, board reset request rejected
     *
     * @param mes  the message
     */
    protected void handleRESETBOARDREJECT(SOCResetBoardReject mes)
    {
        String gname = mes.getGame();
        SOCGame ga = games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        pcl.boardResetVoteRejected();
    }

    /**
     * process the "game option get defaults" message.
     * If any default option's keyname is unknown, ask the server.
     * @see ServerGametypeInfo
     * @since 1.1.07
     */
    private void handleGAMEOPTIONGETDEFAULTS(SOCGameOptionGetDefaults mes, final boolean isPractice)
    {
        ServerGametypeInfo opts;
        if (isPractice)
            opts = practiceServGameOpts;
        else
            opts = tcpServGameOpts;

        final List<String> unknowns;
        synchronized(opts)
        {
            // receiveDefaults sets opts.defaultsReceived, may set opts.allOptionsReceived
            unknowns = opts.receiveDefaults
                (SOCGameOption.parseOptionsToMap((mes.getOpts())));
        }

        if (unknowns != null)
        {
            if (! isPractice)
                gameDisplay.optionsRequested();

            gmgr.put(SOCGameOptionGetInfos.toCmd(unknowns, wantsI18nStrings(isPractice)), isPractice);
        } else {
            opts.newGameWaitingForOpts = false;
            gameDisplay.optionsReceived(opts, isPractice);
        }
    }

    /**
     * process the "game option info" message
     * by calling {@link ServerGametypeInfo#receiveInfo(SOCGameOptionInfo)}.
     * If all are now received, possibly show game info/options window for new game or existing game.
     *<P>
     * For a summary of the flags and variables involved with game options,
     * and the client/server interaction about their values, see
     * {@link ServerGametypeInfo}.
     *
     * @since 1.1.07
     */
    private void handleGAMEOPTIONINFO(SOCGameOptionInfo mes, final boolean isPractice)
    {
        ServerGametypeInfo opts;
        if (isPractice)
            opts = practiceServGameOpts;
        else
            opts = tcpServGameOpts;

        boolean hasAllNow;
        synchronized(opts)
        {
            hasAllNow = opts.receiveInfo(mes);
        }

        boolean isDash = mes.getOptionNameKey().equals("-");  // I18N: do not localize "-" keyname
        gameDisplay.optionsReceived(opts, isPractice, isDash, hasAllNow);
    }

    /**
     * process the "new game with options" message
     * @since 1.1.07
     */
    private void handleNEWGAMEWITHOPTIONS(SOCNewGameWithOptions mes, final boolean isPractice)
    {
        System.err.println("L3609 newgamewithopts at " + System.currentTimeMillis());
        String gname = mes.getGame();
        String opts = mes.getOptionsString();
        boolean canJoin = (mes.getMinVersion() <= Version.versionNumber());
        if (gname.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE)
        {
            gname = gname.substring(1);
            canJoin = false;
        }
        gameDisplay.addToGameList(! canJoin, gname, opts, ! isPractice);
    }

    /**
     * handle the "list of games with options" message
     * @since 1.1.07
     */
    private void handleGAMESWITHOPTIONS(SOCGamesWithOptions mes, final boolean isPractice)
    {
        // Any game's name in this msg may start with the "unjoinable" prefix
        // SOCGames.MARKER_THIS_GAME_UNJOINABLE.
        // This is recognized and removed in mes.getGameList.

        SOCGameList msgGames = mes.getGameList();
        if (msgGames == null)
            return;
        if (! isPractice)  // practice gameoption data is set up in handleVERSION;
        {                  // practice srv's gamelist is reached through practiceServer obj.
            if (serverGames == null)
                serverGames = msgGames;
            else
                serverGames.addGames(msgGames, Version.versionNumber());

            // No more game-option info will be received,
            // because that's always sent before game names are sent.
            // We may still ask for GAMEOPTIONGETDEFAULTS if asking to create a game,
            // but that will happen when user clicks that button, not yet.
            tcpServGameOpts.noMoreOptions(false);
        }

        for (String gaName : msgGames.getGameNames())
        {
            gameDisplay.addToGameList(msgGames.isUnjoinableGame(gaName), gaName, msgGames.getGameOptionsString(gaName), false);
        }
    }

    /**
     * Localized i18n strings for game items.
     * Added 2015-01-11 for v2.0.00.
     * @param isPractice  Is the server {@link ClientNetwork#practiceServer}, not remote?
     */
    private void handleLOCALIZEDSTRINGS(final SOCLocalizedStrings mes, final boolean isPractice)
    {
        final List<String> str = mes.getParams();
        final String type = str.get(0);

        if (type.equals(SOCLocalizedStrings.TYPE_GAMEOPT))
        {
            final int L = str.size();
            for (int i = 1; i < L; i += 2)
            {
                SOCGameOption opt = SOCGameOption.getOption(str.get(i), false);
                if (opt != null)
                {
                    final String desc = str.get(i + 1);
                    if (! desc.equals(SOCLocalizedStrings.EMPTY))
                        opt.setDesc(desc);
                }
            }

        }
        else if (type.equals(SOCLocalizedStrings.TYPE_SCENARIO))
        {
            localizeGameScenarios
                (str, true, mes.isFlagSet(SOCLocalizedStrings.FLAG_SENT_ALL), isPractice);
        }
        else
        {
            System.err.println("L4916: Unknown localized string type " + type);
        }
    }

    /**
     * Updated scenario info.
     * Added 2015-09-21 for v2.0.00.
     * @param isPractice  Is the server {@link ClientNetwork#practiceServer}, not remote?
     */
    private void handleSCENARIOINFO(final SOCScenarioInfo mes, final boolean isPractice)
    {
        ServerGametypeInfo opts;
        if (isPractice)
            opts = practiceServGameOpts;
        else
            opts = tcpServGameOpts;

        if (mes.noMoreScens)
        {
            synchronized (opts)
            {
                opts.allScenStringsReceived = true;
                opts.allScenInfoReceived = true;
            }
        } else {
            final String scKey = mes.getScenarioKey();

            if (mes.isKeyUnknown)
                SOCScenario.removeUnknownScenario(scKey);
            else
                SOCScenario.addKnownScenario(mes.getScenario());

            synchronized (opts)
            {
                opts.scenKeys.add(scKey);  // OK if was already present from received localized strings
            }
        }
    }

    /**
     * handle the "player stats" message
     * @since 1.1.09
     */
    private void handlePLAYERSTATS(SOCPlayerStats mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        final int stype = mes.getStatType();
        if (stype != SOCPlayerStats.STYPE_RES_ROLL)
            return;  // not recognized in this version

        final int[] rstat = mes.getParams();

        EnumMap<PlayerClientListener.UpdateType, Integer> stats
            = new EnumMap<PlayerClientListener.UpdateType, Integer>(PlayerClientListener.UpdateType.class);
        stats.put(PlayerClientListener.UpdateType.Clay, Integer.valueOf(rstat[SOCResourceConstants.CLAY]));
        stats.put(PlayerClientListener.UpdateType.Ore, Integer.valueOf(rstat[SOCResourceConstants.ORE]));
        stats.put(PlayerClientListener.UpdateType.Sheep, Integer.valueOf(rstat[SOCResourceConstants.SHEEP]));
        stats.put(PlayerClientListener.UpdateType.Wheat, Integer.valueOf(rstat[SOCResourceConstants.WHEAT]));
        stats.put(PlayerClientListener.UpdateType.Wood, Integer.valueOf(rstat[SOCResourceConstants.WOOD]));
        if (rstat.length > SOCResourceConstants.GOLD_LOCAL)
        {
            final int n = rstat[SOCResourceConstants.GOLD_LOCAL];
            if (n != 0)
                stats.put(PlayerClientListener.UpdateType.GoldGains, Integer.valueOf(n));
        }
        pcl.playerStats(stats);
    }

    /**
     * Handle the server's debug piece placement on/off message.
     * @since 1.1.12
     */
    private final void handleDEBUGFREEPLACE(SOCDebugFreePlace mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        pcl.debugFreePlaceModeToggled(mes.getCoordinates() == 1);
    }

    /**
     * Handle server responses from the "simple request" handler.
     * @since 1.1.18
     */
    private final void handleSIMPLEREQUEST(SOCSimpleRequest mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCDisplaylessPlayerClient.handleSIMPLEREQUEST(games, mes);  // update any game state
        pcl.simpleRequest(mes.getPlayerNumber(), mes.getRequestType(), mes.getValue1(), mes.getValue2());
    }

    /**
     * Handle "simple action" announcements from the server.
     * @since 1.1.19
     */
    private final void handleSIMPLEACTION(final SOCSimpleAction mes)
    {
        final String gaName = mes.getGame();
        PlayerClientListener pcl = clientListeners.get(gaName);
        if (pcl == null)
            return;  // Not one of our games

        final int atype = mes.getActionType();
        switch (atype)
        {
        case SOCSimpleAction.SC_PIRI_FORT_ATTACK_RESULT:
            // present the server's response to a Pirate Fortress Attack request
            pcl.scen_SC_PIRI_pirateFortressAttackResult(false, mes.getValue1(), mes.getValue2());
            break;

        case SOCSimpleAction.TRADE_PORT_REMOVED:
            SOCDisplaylessPlayerClient.handleSIMPLEACTION(games, mes);  // calls ga.removePort(..)
            // fall through so pcl.simpleAction updates displayed board

        case SOCSimpleAction.DEVCARD_BOUGHT:
            pcl.simpleAction(mes.getPlayerNumber(), atype, mes.getValue1(), mes.getValue2());
            break;

        default:
            // ignore unknown types
            System.err.println
                ("handleSIMPLEACTION: Unknown type ignored: " + atype + " in game " + gaName);
        }
    }

    /**
     * Handle game server text and announcements.
     * @see #handleGAMETEXTMSG(SOCGameTextMsg)
     * @since 2.0.00
     */
    protected void handleGAMESERVERTEXT(SOCGameServerText mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;

        pcl.messageSent(null, mes.getText());
    }

    /**
     * Handle all players' dice roll result resources.  Looks up the game,
     * players gain resources, and announces results.
     * @since 2.0.00
     */
    protected void handleDICERESULTRESOURCES(final SOCDiceResultResources mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;

        SOCDisplaylessPlayerClient.handleDICERESULTRESOURCES(mes, ga);
        pcl.diceRolledResources(mes.playerNum, mes.playerRsrc);
    }

    /**
     * Handle moving a piece (a ship) around on the board.
     * @since 2.0.00
     */
    private final void handleMOVEPIECE(SOCMovePiece mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;
        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        pcl.playerPieceMoved(player, mes.getFromCoord(), mes.getToCoord(), mes.getPieceType());
    }

    /**
     * Handle removing a piece (a ship) from the board in certain scenarios.
     * @since 2.0.00
     */
    private final void handleREMOVEPIECE(SOCRemovePiece mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;
        SOCPlayer player = ga.getPlayer(mes.getParam1());
        pcl.playerPieceRemoved(player, mes.getParam3(), mes.getParam2());
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

        PlayerClientListener pcl = clientListeners.get(gaName);
        if (pcl == null)
            return;  // Not one of our games
        pcl.boardUpdated();
    }

    /**
     * Update a village piece's value on the board (cloth remaining) in _SC_CLVI,
     * or a pirate fortress's strength in _SC_PIRI.
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

        final int coord = mes.getParam1();
        final int pv = mes.getParam2();
        SOCPlayingPiece updatePiece = null;  // if not null, call pcl.pieceValueUpdated

        if (ga.isGameOptionSet(SOCGameOption.K_SC_CLVI))
        {
            SOCVillage vi = ((SOCBoardLarge) (ga.getBoard())).getVillageAtNode(coord);
            if (vi != null)
            {
                vi.setCloth(pv);
                updatePiece = vi;
            }
        }
        else if (ga.isGameOptionSet(SOCGameOption.K_SC_PIRI))
        {
            SOCFortress fort = ga.getFortress(coord);
            if (fort != null)
            {
                fort.setStrength(pv);
                updatePiece = fort;
            }
        }

        if (updatePiece != null)
        {
            PlayerClientListener pcl = clientListeners.get(gaName);
            if (pcl != null)
                pcl.pieceValueUpdated(updatePiece);
        }
    }

    /**
     * Text that a player has been awarded Special Victory Point(s).
     * The server will also send a {@link SOCPlayerElement} with the SVP total.
     * Also sent for each player's SVPs when client is joining a game in progress.
     * @since 2.0.00
     */
    protected void handleSVPTEXTMSG(final SOCSVPTextMessage mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games
        final SOCPlayer pl = ga.getPlayer(mes.pn);
        if (pl == null)
            return;
        pl.addSpecialVPInfo(mes.svp, mes.desc);
        PlayerClientListener pcl = clientListeners.get(gaName);
        if (pcl == null)
            return;
        pcl.playerSVPAwarded(pl, mes.svp, mes.desc);
    }

    /**
     * Set or clear a special edge on the board.
     * @since 2.0.00
     */
    private void handleBOARDSPECIALEDGE(final SOCBoardSpecialEdge mes)
    {
        if (SOCDisplaylessPlayerClient.handleBOARDSPECIALEDGE(games, mes))
        {
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            if (pcl != null)
                pcl.boardLayoutUpdated();
        }
    }

    /**
     * Update player inventory. Refresh our display. If it's a reject message, give feedback to the user.
     * @since 2.0.00
     */
    private void handleINVENTORYITEMACTION(final SOCInventoryItemAction mes)
    {
        final boolean isReject = SOCDisplaylessPlayerClient.handleINVENTORYITEMACTION
            (games, (SOCInventoryItemAction) mes);

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;

        if (isReject)
        {
            pcl.invItemPlayRejected(mes.itemType, mes.reasonCode);
        } else {
            SOCGame ga = games.get(mes.getGame());
            if (ga != null)
            {
                final SOCPlayer pl = ga.getPlayer(mes.playerNumber);
                pcl.playerDevCardUpdated
                    (pl, (mes.action == SOCInventoryItemAction.ADD_PLAYABLE));
                if (mes.action == SOCInventoryItemAction.PLAYED)
                    pcl.playerCanCancelInvItemPlay(pl, mes.canCancelPlay);
            }
        }
    }

    /**
     * Handle the "set special item" message.
     * Calls {@link SOCDisplaylessPlayerClient#handleSETSPECIALITEM(Map, SOCSetSpecialItem)},
     * then calls {@link PlayerClientListener} to update the game display.
     *
     * @param games  Games the client is playing
     * @param mes  the message
     * @since 2.0.00
     */
    private void handleSETSPECIALITEM(final Map<String, SOCGame> games, SOCSetSpecialItem mes)
    {
        SOCDisplaylessPlayerClient.handleSETSPECIALITEM(games, (SOCSetSpecialItem) mes);

        final PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;

        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final String typeKey = mes.typeKey;
        final int gi = mes.gameItemIndex, pi = mes.playerItemIndex, pn = mes.playerNumber;
        final SOCPlayer pl = ((pn != -1) && (pi != -1)) ? ga.getPlayer(pn) : null;

        switch (mes.op)
        {
        case SOCSetSpecialItem.OP_SET:
            // fall through
        case SOCSetSpecialItem.OP_CLEAR:
            pcl.playerSetSpecialItem(typeKey, ga, pl, gi, pi, (mes.op == SOCSetSpecialItem.OP_SET));
            break;

        case SOCSetSpecialItem.OP_PICK:
            // fall through
        case SOCSetSpecialItem.OP_DECLINE:
            pcl.playerPickSpecialItem(typeKey, ga, pl, gi, pi, (mes.op == SOCSetSpecialItem.OP_PICK),
                mes.coord, mes.level, mes.sv);
            break;
        }
    }

    }  // nested class MessageTreater


    /**
     * Should this client request localized strings (I18N) from the server if available?
     * Checks server version, checks whether client locale differs from the fallback {@code "en_US"}.
     * @param isPractice  True if checking for local practice server, not a remote server
     * @return  True if client should request localized strings
     * @since 2.0.00
     */
    final boolean wantsI18nStrings(final boolean isPractice)
    {
        return (isPractice || (sVersion >= SOCStringManager.VERSION_FOR_I18N))
            && (cliLocale != null)
            && ! ("en".equals(cliLocale.getLanguage()) && "US".equals(cliLocale.getCountry()));
    }

    /**
     * Check these game options to see if they contain a scenario we don't yet have full info about.
     * If the options include a scenario and we don't have that scenario's info or localized strings,
     * ask the server for that but don't wait here for a reply.
     *<P>
     * <B>Do not call for practice games:</B> Assumes this is the TCP server, because for practice games
     * we already have full info about scenarios and their strings.
     *
     * @param opts  Game options to check for {@code "SC"}, or {@code null}
     * @since 2.0.00
     */
    protected void checkGameoptsForUnknownScenario(final Map<String,SOCGameOption> opts)
    {
        if ((opts == null) || tcpServGameOpts.allScenInfoReceived || ! opts.containsKey("SC"))
            return;

        final String scKey = opts.get("SC").getStringValue();
        if ((scKey.length() == 0) || tcpServGameOpts.scenKeys.contains(scKey))
            return;

        net.putNet(new SOCScenarioInfo(scKey, false).toCmd());
    }

    /**
     * Localize {@link SOCScenario} names and descriptions with strings from the server.
     * Updates scenario data in {@link #practiceServGameOpts} or {@link #tcpServGameOpts}.
     *
     * @param scStrs  Scenario localized strings, same format as {@link SOCLocalizedStrings} params.
     * @param skipFirst  If true skip the first element of {@code scStrs}, it isn't a scenario keyname.
     * @param sentAll  True if no further strings will be received; is {@link SOCLocalizedStrings#FLAG_SENT_ALL} set?
     *     If true, sets {@link ServerGametypeInfo#allScenStringsReceived}.
     * @param isPractice  Is the server {@link ClientNetwork#practiceServer}, not remote?
     * @since 2.0.00
     */
    protected void localizeGameScenarios
        (final List<String> scStrs, final boolean skipFirst, final boolean sentAll, final boolean isPractice)
    {
        ServerGametypeInfo opts = (isPractice ? practiceServGameOpts : tcpServGameOpts);

        final int L = scStrs.size();
        int i = (skipFirst) ? 1 : 0;
        while (i < L)
        {
            final String scKey = scStrs.get(i);
            ++i;
            opts.scenKeys.add(scKey);

            final String nm = scStrs.get(i);
            ++i;

            if (nm.equals(SOCLocalizedStrings.MARKER_KEY_UNKNOWN))
                continue;

            String desc = scStrs.get(i);
            ++i;

            SOCScenario sc = SOCScenario.getScenario(scKey);
            if ((sc != null) && ! nm.equals(SOCLocalizedStrings.EMPTY))
            {
                if ((desc != null) && desc.equals(SOCLocalizedStrings.EMPTY))
                    desc = null;

                sc.setDesc(nm, desc);
            }
        }

        if (sentAll)
            opts.allScenStringsReceived = true;
    }

    /**
     * Add a new game to the initial window's list of games, and possibly
     * to the {@link #serverGames server games list}.
     *
     * @param gameName the game name to add to the list;
     *            may have the prefix {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}
     * @param gameOptsStr String of packed {@link SOCGameOption game options}, or null
     * @param addToSrvList Should this game be added to the list of remote-server games?
     *            Practice games should not be added.
     *            The {@link #serverGames} list also has a flag for cannotJoin.
     * @see GameDisplay#addToGameList(boolean, String, String, boolean)
     */
    public void addToGameList(String gameName, String gameOptsStr, final boolean addToSrvList)
    {
        boolean hasUnjoinMarker = (gameName.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE);
        if (hasUnjoinMarker)
        {
            gameName = gameName.substring(1);
        }
        gameDisplay.addToGameList(hasUnjoinMarker, gameName, gameOptsStr, addToSrvList);
    }

    /** If we're playing in a game that's just finished, update the scores.
     *  This is used to show the true scores, including hidden
     *  victory-point cards, at the game's end.
     *  @since 1.1.00
     */
    public void updateGameEndStats(String game, final int[] scores)
    {
        SOCGame ga = games.get(game);
        if (ga == null)
            return;  // Not playing in that game
        if (ga.getGameState() != SOCGame.OVER)
        {
            System.err.println("L4044: pcli.updateGameEndStats called at state " + ga.getGameState());
            return;  // Should not have been sent; game is not yet over.
        }

        PlayerClientListener pcl = clientListeners.get(game);
        if (pcl == null)
            return;
        Map<SOCPlayer, Integer> scoresMap = new HashMap<SOCPlayer, Integer>();
        for (int i=0; i<scores.length; ++i)
        {
            scoresMap.put(ga.getPlayer(i), Integer.valueOf(scores[i]));
        }
        pcl.gameEnded(scoresMap);
    }

    /**
     * the user leaves the given chat channel
     *
     * @param ch  the name of the channel
     */
    public void leaveChannel(String ch)
    {
        gameDisplay.channelLeft(ch);
        net.putNet(SOCLeaveChannel.toCmd(nickname, net.getHost(), ch));
    }

    public GameManager getGameManager()
    {
        return gameManager;
    }


    /**
     * Nested class for processing outgoing messages (putting).
     *<P>
     * Before v2.0.00, most of these fields and methods were part of the main {@link SOCPlayerClient} class.
     * @author paulbilnoski
     * @since 2.0.00
     */
    public static class GameManager
    {
        private final SOCPlayerClient client;
        private final ClientNetwork net;

        GameManager(SOCPlayerClient client)
        {
            this.client = client;
            if (client == null)
                throw new IllegalArgumentException("client is null");
            net = client.getNet();
            if (net == null)
                throw new IllegalArgumentException("client network is null");
        }

        /**
         * Write a message to the net or practice server.
         * Because the player can be in both network games and practice games,
         * we must route to the appropriate client-server connection.
         *
         * @param s  the message
         * @param isPractice  Put to the practice server, not tcp network?
         *                {@link ClientNetwork#localTCPServer} is considered "network" here.
         *                Use <tt>isPractice</tt> only with {@link ClientNetwork#practiceServer}.
         * @return true if the message was sent, false if not
         * @throws IllegalArgumentException if {@code s} is {@code null}
         */
        private synchronized boolean put(String s, final boolean isPractice)
            throws IllegalArgumentException
        {
            if (s == null)
                throw new IllegalArgumentException("null");

            if (isPractice)
                return net.putPractice(s);
            return net.putNet(s);
        }

    /**
     * request to buy a development card
     *
     * @param ga     the game
     */
    public void buyDevCard(SOCGame ga)
    {
        put(SOCBuyCardRequest.toCmd(ga.getName()), ga.isPractice);
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
        put(SOCBuildRequest.toCmd(ga.getName(), piece), ga.isPractice);
    }

    /**
     * request to cancel building something
     *
     * @param ga     the game
     * @param piece  the type of piece, from SOCPlayingPiece constants
     */
    public void cancelBuildRequest(SOCGame ga, int piece)
    {
        put(SOCCancelBuildRequest.toCmd(ga.getName(), piece), ga.isPractice);
    }

    /**
     * put a piece on the board, using the {@link SOCPutPiece} message.
     * If the game is in {@link SOCGame#debugFreePlacement} mode,
     * send the {@link SOCDebugFreePlace} message instead.
     *
     * @param ga  the game where the action is taking place
     * @param pp  the piece being placed
     */
    public void putPiece(SOCGame ga, SOCPlayingPiece pp)
    {
        String ppm;
        if (ga.isDebugFreePlacement())
            ppm = SOCDebugFreePlace.toCmd(ga.getName(), pp.getPlayerNumber(), pp.getType(), pp.getCoordinates());
        else
            ppm = SOCPutPiece.toCmd(ga.getName(), pp.getPlayerNumber(), pp.getType(), pp.getCoordinates());

        /**
         * send the command
         */
        put(ppm, ga.isPractice);
    }

    /**
     * Ask the server to move this piece to a different coordinate.
     * @param ga  the game where the action is taking place
     * @param pn  The piece's player number
     * @param ptype    The piece type, such as {@link SOCPlayingPiece#SHIP}
     * @param fromCoord  Move the piece from here
     * @param toCoord    Move the piece to here
     * @since 2.0.00
     */
    public void movePieceRequest
        (final SOCGame ga, final int pn, final int ptype, final int fromCoord, final int toCoord)
    {
        put(SOCMovePieceRequest.toCmd(ga.getName(), pn, ptype, fromCoord, toCoord), ga.isPractice);
    }

    /**
     * the player wants to move the robber or the pirate ship.
     *
     * @param ga  the game
     * @param pl  the player
     * @param coord  edge where the player wants the robber, or negative edge for the pirate ship
     */
    public void moveRobber(SOCGame ga, SOCPlayer pl, int coord)
    {
        put(SOCMoveRobber.toCmd(ga.getName(), pl.getPlayerNumber(), coord), ga.isPractice);
    }

    /**
     * The player wants to send a simple request to the server, such as
     * {@link SOCSimpleRequest#SC_PIRI_FORT_ATTACK} to attack their
     * pirate fortress in scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI}.
     *<P>
     * Using network message request types within the client breaks abstraction,
     * but prevents having a lot of very similar methods for simple requests.
     *
     * @param pl  the requesting player
     * @param reqtype  the request type as defined in {@link SOCSimpleRequest}
     * @since 2.0.00
     * @see #sendSimpleRequest(SOCPlayer, int, int, int)
     */
    public void sendSimpleRequest(final SOCPlayer pl, final int reqtype)
    {
        sendSimpleRequest(pl, reqtype, 0, 0);
    }

    /**
     * The player wants to send a simple request to the server, such as
     * {@link SOCSimpleRequest#SC_PIRI_FORT_ATTACK} to attack their
     * pirate fortress in scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * with optional {@code value1} and {@code value2} parameters.
     *<P>
     * Using network message request types within the client breaks abstraction,
     * but prevents having a lot of very similar methods for simple requests.
     *
     * @param pl  the requesting player
     * @param reqtype  the request type as defined in {@link SOCSimpleRequest}
     * @param value1  First optional detail value, or 0
     * @param value2  Second optional detail value, or 0
     * @since 2.0.00
     * @see #sendSimpleRequest(SOCPlayer, int)
     */
    public void sendSimpleRequest(final SOCPlayer pl, final int reqtype, final int value1, final int value2)
    {
        final SOCGame ga = pl.getGame();
        put(SOCSimpleRequest.toCmd(ga.getName(), pl.getPlayerNumber(), reqtype, value1, value2),
            ga.isPractice);
    }

    /**
     * send a text message to the people in the game
     *
     * @param ga   the game
     * @param me   the message
     * @see SOCPlayerClient.GameAwtDisplay#chSend(String, String)
     */
    public void sendText(SOCGame ga, String me)
    {
        put(SOCGameTextMsg.toCmd(ga.getName(), client.nickname, me), ga.isPractice);
    }

    /**
     * the user leaves the given game
     *
     * @param ga   the game
     */
    public void leaveGame(SOCGame ga)
    {
        client.clientListeners.remove(ga.getName());
        client.games.remove(ga.getName());
        put(SOCLeaveGame.toCmd(client.nickname, net.getHost(), ga.getName()), ga.isPractice);
    }

    /**
     * the user sits down to play
     *
     * @param ga   the game
     * @param pn   the number of the seat where the user wants to sit
     */
    public void sitDown(SOCGame ga, int pn)
    {
        put(SOCSitDown.toCmd(ga.getName(), "dummy", pn, false), ga.isPractice);
    }

    /**
     * the user is starting the game
     *
     * @param ga  the game
     */
    public void startGame(SOCGame ga)
    {
        put(SOCStartGame.toCmd(ga.getName()), ga.isPractice);
    }

    /**
     * the user rolls the dice
     *
     * @param ga  the game
     */
    public void rollDice(SOCGame ga)
    {
        put(SOCRollDice.toCmd(ga.getName()), ga.isPractice);
    }

    /**
     * the user is done with the turn
     *
     * @param ga  the game
     */
    public void endTurn(SOCGame ga)
    {
        put(SOCEndTurn.toCmd(ga.getName()), ga.isPractice);
    }

    /**
     * the user wants to discard
     *
     * @param ga  the game
     */
    public void discard(SOCGame ga, SOCResourceSet rs)
    {
        put(SOCDiscard.toCmd(ga.getName(), rs), ga.isPractice);
    }

    /**
     * The user has picked these resources to gain from the gold hex.
     * Or, in game state {@link SOCGame#WAITING_FOR_DISCOVERY}, has picked these
     * 2 free resources from a Discovery/Year of Plenty card.
     *
     * @param ga  the game
     * @param rs  The resources to pick
     * @since 2.0.00
     */
    public void pickResources(SOCGame ga, SOCResourceSet rs)
    {
        if (ga.getGameState() != SOCGame.WAITING_FOR_DISCOVERY)
            put(SOCPickResources.toCmd(ga.getName(), rs), ga.isPractice);
        else
            put(SOCDiscoveryPick.toCmd(ga.getName(), rs), ga.isPractice);
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
        put(SOCChoosePlayer.toCmd(ga.getName(), ch), ga.isPractice);
    }

    /**
     * The user is reacting to the move robber request.
     *
     * @param ga  the game
     */
    public void chooseRobber(SOCGame ga)
    {
        choosePlayer(ga, SOCChoosePlayer.CHOICE_MOVE_ROBBER);
    }

    /**
     * The user is reacting to the move pirate request.
     *
     * @param ga  the game
     */
    public void choosePirate(SOCGame ga)
    {
        choosePlayer(ga, SOCChoosePlayer.CHOICE_MOVE_PIRATE);
    }

    /**
     * the user is rejecting the current offers
     *
     * @param ga  the game
     */
    public void rejectOffer(SOCGame ga)
    {
        put(SOCRejectOffer.toCmd(ga.getName(), ga.getPlayer(client.nickname).getPlayerNumber()), ga.isPractice);
    }

    /**
     * the user is accepting an offer
     *
     * @param ga  the game
     * @param from the number of the player that is making the offer
     */
    public void acceptOffer(SOCGame ga, int from)
    {
        put(SOCAcceptOffer.toCmd(ga.getName(), ga.getPlayer(client.nickname).getPlayerNumber(), from), ga.isPractice);
    }

    /**
     * the user is clearing an offer
     *
     * @param ga  the game
     */
    public void clearOffer(SOCGame ga)
    {
        put(SOCClearOffer.toCmd(ga.getName(), ga.getPlayer(client.nickname).getPlayerNumber()), ga.isPractice);
    }

    /**
     * the user wants to trade with the bank or a port.
     *
     * @param ga    the game
     * @param give  what is being offered
     * @param get   what the player wants
     */
    public void bankTrade(SOCGame ga, SOCResourceSet give, SOCResourceSet get)
    {
        put(SOCBankTrade.toCmd(ga.getName(), give, get), ga.isPractice);
    }

    /**
     * the user is making an offer to trade with another player.
     *
     * @param ga    the game
     * @param offer the trade offer
     */
    public void offerTrade(SOCGame ga, SOCTradeOffer offer)
    {
        put(SOCMakeOffer.toCmd(ga.getName(), offer), ga.isPractice);
    }

    /**
     * the user wants to play a development card
     *
     * @param ga  the game
     * @param dc  the type of development card
     */
    public void playDevCard(SOCGame ga, int dc)
    {
        if ((! ga.isPractice) && (client.sVersion < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
        {
            if (dc == SOCDevCardConstants.KNIGHT)
                dc = SOCDevCardConstants.KNIGHT_FOR_VERS_1_X;
            else if (dc == SOCDevCardConstants.UNKNOWN)
                dc = SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X;
        }
        put(SOCPlayDevCardRequest.toCmd(ga.getName(), dc), ga.isPractice);
    }

    /**
     * The current user wants to play a special {@link soc.game.SOCInventoryItem SOCInventoryItem}.
     * Send the server a {@link SOCInventoryItemAction}{@code (currentPlayerNumber, PLAY, itype, rc=0)} message.
     * @param ga     the game
     * @param itype  the special inventory item type picked by player,
     *     from {@link soc.game.SOCInventoryItem#itype SOCInventoryItem.itype}
     */
    public void playInventoryItem(SOCGame ga, final int itype)
    {
        put(SOCInventoryItemAction.toCmd
            (ga.getName(), ga.getCurrentPlayerNumber(), SOCInventoryItemAction.PLAY, itype, 0), ga.isPractice);
    }

    /**
     * The current user wants to pick a {@link SOCSpecialItem Special Item}.
     * Send the server a {@link SOCSetSpecialItem}{@code (PICK, typeKey, gi, pi, owner=-1, coord=-1, level=0)} message.
     * @param ga  Game
     * @param typeKey  Special item type.  Typically a {@link SOCGameOption} keyname; see the {@link SOCSpecialItem}
     *     class javadoc for details.
     * @param gi  Game Item Index, as in {@link SOCGame#getSpecialItem(String, int)} or
     *     {@link SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)}, or -1
     * @param pi  Player Item Index, as in {@link SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)},
     *     or -1
     */
    public void pickSpecialItem(SOCGame ga, final String typeKey, final int gi, final int pi)
    {
        put(new SOCSetSpecialItem
            (ga.getName(), SOCSetSpecialItem.OP_PICK, typeKey, gi, pi, -1).toCmd(), ga.isPractice);
    }

    /**
     * the user picked 2 resources to discover
     *
     * @param ga    the game
     * @param rscs  the resources
     */
    public void discoveryPick(SOCGame ga, SOCResourceSet rscs)
    {
        put(SOCDiscoveryPick.toCmd(ga.getName(), rscs), ga.isPractice);
    }

    /**
     * the user picked a resource to monopolize
     *
     * @param ga   the game
     * @param res  the resource
     */
    public void monopolyPick(SOCGame ga, int res)
    {
        put(SOCMonopolyPick.toCmd(ga.getName(), res), ga.isPractice);
    }

    /**
     * the user is changing the face image
     *
     * @param ga  the game
     * @param id  the image id
     */
    public void changeFace(SOCGame ga, int id)
    {
        client.lastFaceChange = id;
        put(SOCChangeFace.toCmd(ga.getName(), ga.getPlayer(client.nickname).getPlayerNumber(), id), ga.isPractice);
    }

    /**
     * The user is locking or unlocking a seat.
     *
     * @param ga  the game
     * @param pn  the seat number
     * @param sl  new seat lock state; remember that servers older than v2.0.00 won't recognize {@code CLEAR_ON_RESET}
     * @since 2.0.00
     */
    public void setSeatLock(SOCGame ga, int pn, SOCGame.SeatLockState sl)
    {
        put(SOCSetSeatLock.toCmd(ga.getName(), pn, sl), ga.isPractice);
    }

    /**
     * Player wants to request to reset the board (same players, new game, new layout).
     * Send {@link soc.message.SOCResetBoardRequest} to server;
     * it will either respond with a
     * {@link soc.message.SOCResetBoardAuth} message,
     * or will tell other players to vote yes/no on the request.
     * Before calling, check player.hasAskedBoardReset()
     * and game.getResetVoteActive().
     */
    public void resetBoardRequest(SOCGame ga)
    {
        put(SOCResetBoardRequest.toCmd(SOCMessage.RESETBOARDREQUEST, ga.getName()), ga.isPractice);
    }

    /**
     * Player is responding to a board-reset vote from another player.
     * Send {@link soc.message.SOCResetBoardRequest} to server;
     * it will either respond with a
     * {@link soc.message.SOCResetBoardAuth} message,
     * or will tell other players to vote yes/no on the request.
     *
     * @param ga Game to vote on
     * @param pn Player number of our player who is voting
     * @param voteYes If true, this player votes yes; if false, no
     */
    public void resetBoardVote(SOCGame ga, int pn, boolean voteYes)
    {
        put(SOCResetBoardVote.toCmd(ga.getName(), pn, voteYes), ga.isPractice);
    }

        /**
         * send a command to the server with a message
         * asking a robot to show the debug info for
         * a possible move after a move has been made
         *
         * @param ga  the game
         * @param pname  the robot name
         * @param piece  the piece to consider
         */
         //TODO i18n this is a command, isn't it?
        public void considerMove(SOCGame ga, String pname, SOCPlayingPiece piece)
        {
            String msg = pname + ":consider-move ";

            switch (piece.getType())
            {
            case SOCPlayingPiece.SETTLEMENT:
                msg += "settlement";

                break;

            case SOCPlayingPiece.ROAD:
                msg += "road";

                break;

            case SOCPlayingPiece.CITY:
                msg += "city";

                break;
            }

            msg += (" " + piece.getCoordinates());
            put(SOCGameTextMsg.toCmd(ga.getName(), client.nickname, msg), ga.isPractice);
        }

        /**
         * send a command to the server with a message
         * asking a robot to show the debug info for
         * a possible move before a move has been made
         *
         * @param ga  the game
         * @param pname  the robot name
         * @param piece  the piece to consider
         */
        public void considerTarget(SOCGame ga, String pname, SOCPlayingPiece piece)
        {
            String msg = pname + ":consider-target ";

            switch (piece.getType())
            {
            case SOCPlayingPiece.SETTLEMENT:
                msg += "settlement";

                break;

            case SOCPlayingPiece.ROAD:
                msg += "road";

                break;

            case SOCPlayingPiece.CITY:
                msg += "city";

                break;
            }

            msg += (" " + piece.getCoordinates());
            put(SOCGameTextMsg.toCmd(ga.getName(), client.nickname, msg), ga.isPractice);
        }

    }  // nested class GameManager


    /**
     * @return true if name is on the ignore list
     */
    protected boolean onIgnoreList(String name)
    {
        boolean result = false;

        for (String s : ignoreList)
        {
            if (s.equals(name))
            {
                result = true;

                break;
            }
        }

        return result;
    }

    /**
     * add this name to the ignore list
     *
     * @param name the name to add
     */
    protected void addToIgnoreList(String name)
    {
        name = name.trim();

        if (!onIgnoreList(name))
        {
            ignoreList.addElement(name);
        }
    }

    /**
     * remove this name from the ignore list
     *
     * @param name  the name to remove
     */
    protected void removeFromIgnoreList(String name)
    {
        name = name.trim();
        ignoreList.removeElement(name);
    }

    /**
     * Create a game name, and start a practice game.
     * Assumes {@link GameAwtDisplay#MAIN_PANEL} is initialized.
     * @since 1.1.00
     */
    public void startPracticeGame()
    {
        startPracticeGame(null, null, true);
    }

    /**
     * Setup for practice game (on the non-tcp server).
     * If needed, a (stringport, not tcp) {@link ClientNetwork#practiceServer}, client, and robots are started.
     *
     * @param practiceGameName Unique name to give practice game; if name unknown, call
     *         {@link #startPracticeGame()} instead
     * @param gameOpts Set of {@link SOCGameOption game options} to use, or null
     * @param mainPanelIsActive Is the SOCPlayerClient main panel active?
     *         False if we're being called from elsewhere, such as
     *         {@link SOCConnectOrPracticePanel}.
     * @since 1.1.00
     */
    public void startPracticeGame
        (String practiceGameName, final Map<String, SOCGameOption> gameOpts, final boolean mainPanelIsActive)
    {
        ++numPracticeGames;

        if (practiceGameName == null)
            practiceGameName = DEFAULT_PRACTICE_GAMENAME + " " + (numPracticeGames);  // "Practice 3"

        // May take a while to start server & game.
        // The new-game window will clear this cursor.
        gameDisplay.practiceGameStarting();

        net.startPracticeGame(practiceGameName, gameOpts);
    }

    /**
     * Server version, for checking feature availability.
     * Returns -1 if unknown.
     * @param  game  Game being played on a practice or tcp server.
     * @return Server version, format like {@link soc.util.Version#versionNumber()},
     *         or 0 or -1.
     */
    public int getServerVersion(SOCGame game)
    {
        if (game.isPractice)
            return Version.versionNumber();
        else
            return sVersion;
    }

    /**
     * network trouble; if possible, ask if they want to play locally (practiceServer vs. robots).
     * Otherwise, go ahead and shut down.
     */
    public void dispose()
    {
        final boolean canPractice = net.putLeaveAll(); // Can we still start a practice game?

        String err;
        if (canPractice)
        {
            err = strings.get("pcli.error.networktrouble");  // "Sorry, network trouble has occurred."
        } else {
            err = strings.get("pcli.error.clientshutdown");  // "Sorry, the client has been shut down."
        }
        err = err + " " + ((net.ex == null) ? strings.get("pcli.error.loadpageagain") : net.ex.toString());
            // "Load the page again."

        gameDisplay.channelsClosed(err);

        // Stop network games; Practice games can continue.
        for (Map.Entry<String, PlayerClientListener> e : clientListeners.entrySet())
        {
            String gameName = e.getKey();
            SOCGame game = games.get(gameName);
            boolean isPractice = false;
            if (game != null)
                isPractice = game.isPractice;
            if (!(canPractice && isPractice))
                e.getValue().gameDisconnected(err);
        }

        net.dispose();

        gameDisplay.showErrorPanel(err, canPractice);
    }

    /**
     * for stand-alones
     */
    public static void usage()
    {
        System.err.println("usage: java soc.client.SOCPlayerClient [<host> <port>]");
    }

    /**
     * for stand-alones
     */
    public static void main(String[] args)
    {
        GameAwtDisplay gameDisplay = null;
        SOCPlayerClient client = null;

        String host = null;  // from args, if not empty
        int port = -1;

        Version.printVersionText(System.out, "Java Settlers Client ");

        if (args.length != 0)
        {
            if (args.length != 2)
            {
                usage();
                System.exit(1);
            }

            try {
                host = args[0];
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException x) {
                usage();
                System.err.println("Invalid port: " + args[1]);
                System.exit(1);
            }
        }

        client = new SOCPlayerClient();
        gameDisplay = new GameAwtDisplay((args.length == 0), client);
        client.setGameDisplay(gameDisplay);

        Frame frame = new Frame(client.strings.get("pcli.main.title", Version.version()));  // "JSettlers client {0}"
        frame.setBackground(new Color(Integer.parseInt("61AF71",16)));
        frame.setForeground(Color.black);
        // Add a listener for the close event
        frame.addWindowListener(gameDisplay.createWindowAdapter());

        gameDisplay.initVisualElements(); // after the background is set

        frame.add(gameDisplay, BorderLayout.CENTER);
        frame.setSize(620, 400);
        frame.setVisible(true);

        if ((host != null) && (port != -1))
            client.net.connect(host, port);
    }

    public ClientNetwork getNet()
    {
        return net;
    }


    /**
     * Helper object to encapsulate and deal with network connectivity.
     *<P>
     * Local practice server (if any) is started in {@link #startPracticeGame(String, Map)}.
     *<br>
     * Local tcp server (if any) is started in {@link #initLocalServer(int)}.
     *<br>
     * Network shutdown is {@link #disconnect()} or {@link #dispose()}.
     *<P>
     * Before v2.0.00, most of these fields and methods were part of the main {@link SOCPlayerClient} class.
     *
     * @author Paul Bilnoski &lt;paul@bilnoski.net&gt;
     * @since 2.0.00
     * @see SOCPlayerClient#getNet()
     */
    public static class ClientNetwork
    {
        /**
         * Default tcp port number 8880 to listen, and to connect to remote server.
         * Should match SOCServer.SOC_PORT_DEFAULT.
         *<P>
         * 8880 is the default SOCPlayerClient port since jsettlers 1.0.4, per cvs history.
         * @since 1.1.00
         */
        public static final int SOC_PORT_DEFAULT = 8880;

        final SOCPlayerClient client;

        /**
         * Hostname we're connected to, or null
         */
        private String host;

        /**
         * TCP port we're connected to; default is {@link #SOC_PORT_DEFAULT}.
         */
        private int port = SOC_PORT_DEFAULT;

        /**
         * Client-hosted TCP server. If client is running this server, it's also connected
         * as a client, instead of being client of a remote server.
         * Started via {@link SOCPlayerClient.GameAwtDisplay#startLocalTCPServer(int)}.
         * {@link #practiceServer} may still be activated at the user's request.
         * Note that {@link SOCGame#isPractice} is false for localTCPServer's games.
         */
        private SOCServer localTCPServer = null;

        Socket s;
        DataInputStream in;
        DataOutputStream out;
        Thread reader = null;

        /**
         * Any network error (TCP communication) received while connecting
         * or sending messages in {@link #putNet(String)}, or null.
         * If {@code ex != null}, putNet will refuse to send.
         *<P>
         * The exception's {@link Throwable#toString() toString()} including its
         * {@link Throwable#getMessage() getMessage()} may be displayed to the user
         * by {@link SOCPlayerClient#dispose()}; if throwing an error that the user
         * should see, be sure to set the detail message.
         */
        Exception ex = null;

        /** Practice-server error (stringport pipes), or null */
        Exception ex_P = null;

        /**
         * Are we connected to a TCP server (remote or {@link #localTCPServer})?
         * {@link #practiceServer} is not a TCP server.
         * @see #ex
         */
        boolean connected = false;

        /** For debug, our last messages sent, over the net or practice server (pipes) */
        protected String lastMessage_N, lastMessage_P;

        /**
         * Server for practice games via {@link #prCli}; not connected to the network,
         * not suited for hosting multi-player games. Use {@link #localTCPServer}
         * for those.
         * SOCMessages of games where {@link SOCGame#isPractice} is true are sent
         * to practiceServer.
         *<P>
         * Null before it's started in {@link SOCPlayerClient#startPracticeGame()}.
         */
        protected SOCServer practiceServer = null;

        /**
         * Client connection to {@link #practiceServer practice server}.
         * Null before it's started in {@link #startPracticeGame()}.
         *<P>
         * Last message is in {@link #lastMessage_P}; any error is in {@link #ex_P}.
         */
        protected StringConnection prCli = null;

        public ClientNetwork(SOCPlayerClient c)
        {
            client = c;
            if (client == null)
                throw new IllegalArgumentException("client is null");
        }

        /** Shut down the local TCP server (if any) and disconnect from the network. */
        public void dispose()
        {
            shutdownLocalServer();
            disconnect();
        }

        /**
         * Start a practice game.  If needed, create and start {@link #practiceServer}.
         * @param practiceGameName  Game name
         * @param gameOpts  Game options
         */
        public void startPracticeGame(final String practiceGameName, final Map<String, SOCGameOption> gameOpts)
        {
            if (practiceServer == null)
            {
                try
                {
                    practiceServer = new SOCServer(SOCServer.PRACTICE_STRINGPORT, SOCServer.SOC_MAXCONN_DEFAULT, null, null);
                    practiceServer.setPriority(5);  // same as in SOCServer.main
                    practiceServer.start();

                    // We need some opponents.
                    // Let the server randomize whether we get smart or fast ones.
                    practiceServer.setupLocalRobots(5, 2);
                }
                catch (Throwable th)
                {
                    client.gameDisplay.showErrorDialog
                        (client.strings.get("pcli.error.startingpractice") + "\n" + th,  // "Problem starting practice server:"
                         client.strings.get("base.cancel"));
                }
            }

            if (prCli == null)
            {
                try
                {
                    prCli = LocalStringServerSocket.connectTo(SOCServer.PRACTICE_STRINGPORT);
                    new SOCPlayerLocalStringReader((LocalStringConnection) prCli);
                    // Reader will start its own thread.
                    // Send VERSION right away (1.1.06 and later)
                    putPractice(SOCVersion.toCmd
                        (Version.versionNumber(), Version.version(), Version.buildnum(), client.cliLocale.toString()));

                    // practice server will support per-game options
                    client.gameDisplay.enableOptions();
                }
                catch (ConnectException e)
                {
                    ex_P = e;
                    return;
                }
            }

            // Ask internal practice server to create the game
            if (gameOpts == null)
                putPractice(SOCJoinGame.toCmd(client.nickname, "", getHost(), practiceGameName));
            else
                putPractice(SOCNewGameWithOptionsRequest.toCmd
                    (client.nickname, "", getHost(), practiceGameName, gameOpts));
        }

        /**
         * Get the tcp port number of the local server.
         * @see #isRunningLocalServer()
         */
        public int getLocalServerPort()
        {
            if (localTCPServer == null)
                return 0;
            return localTCPServer.getPort();
        }

        /** Shut down the local TCP server. */
        public void shutdownLocalServer()
        {
            if ((localTCPServer != null) && (localTCPServer.isUp()))
            {
                localTCPServer.stopServer();
                localTCPServer = null;
            }
        }

        /**
         * Create and start the local TCP server on a given port.
         * If startup fails, show a {@link NotifyDialog} with the error message.
         * @return True if started, false if not
         */
        public boolean initLocalServer(int tport)
        {
            try
            {
                localTCPServer = new SOCServer(tport, SOCServer.SOC_MAXCONN_DEFAULT, null, null);
                localTCPServer.setPriority(5);  // same as in SOCServer.main
                localTCPServer.start();

                // We need some opponents.
                // Let the server randomize whether we get smart or fast ones.
                localTCPServer.setupLocalRobots(5, 2);
            }
            catch (Throwable th)
            {
                client.gameDisplay.showErrorDialog
                    (client.strings.get("pcli.error.startingserv") + "\n" + th,  // "Problem starting server:"
                     client.strings.get("base.cancel"));
                return false;
            }

            return true;
        }

        /** Port number of the tcp server we're a client of; default is {@link #SOC_PORT_DEFAULT}. */
        public int getPort()
        {
            return port;
        }

        /** Hostname of the tcp server we're a client of */
        public String getHost()
        {
            return host;
        }

        /** Are we connected to a tcp server? */
        public synchronized boolean isConnected()
        {
            return connected;
        }

        /**
         * Attempts to connect to the server. See {@link #isConnected()} for success or
         * failure. Once connected, starts a {@link #reader} thread.
         * The first message over the connection is our version,
         * and the second is the server's response:
         * Either {@link SOCRejectConnection}, or the lists of
         * channels and games ({@link SOCChannels}, {@link SOCGames}).
         *<P>
         * Since user login and authentication don't occur until a game or channel join is requested,
         * no username or password is needed here.
         *<P>
         * Before 1.1.06, the server's response was first,
         * and version was sent in reply to server's version.
         *
         * @param chost  Server host to connect to, or {@code null} for localhost
         * @param sPort  Server TCP port to connect to; the default server port is {@link ClientNetwork#SOC_PORT_DEFAULT}.
         * @throws IllegalStateException if already connected
         * @see soc.server.SOCServer#newConnection1(StringConnection)
         */
        public synchronized void connect(String chost, int sPort)
            throws IllegalStateException
        {
            if (connected)
            {
                throw new IllegalStateException
                    ("Already connected to " + (host != null ? host : "localhost") + ":" + port);
            }

            ex = null;
            host = chost;
            port = sPort;

            String hostString = (chost != null ? chost : "localhost") + ":" + sPort;
            System.out.println(/*I*/"Connecting to " + hostString/*18N*/);  // I18N: Not localizing console output yet
            client.gameDisplay.setMessage
                (client.strings.get("pcli.message.connecting.serv"));  // "Connecting to server..."

            try
            {
                if (client.gotPassword)
                {
                    client.gameDisplay.setPassword(client.password);
                        // when ! gotPassword, GameAwtDisplay.getPassword() will read pw from there
                    client.gotPassword = false;
                }
                s = new Socket(host, port);
                in = new DataInputStream(s.getInputStream());
                out = new DataOutputStream(s.getOutputStream());
                connected = true;
                (reader = new Thread(new NetReadTask(client, this))).start();
                // send VERSION right away (1.1.06 and later)
                // Version msg includes locale in 2.0.00 and later clients; older 1.1.xx servers will ignore that token.
                putNet(SOCVersion.toCmd
                    (Version.versionNumber(), Version.version(), Version.buildnum(), client.cliLocale.toString()));
            }
            catch (Exception e)
            {
                ex = e;
                String msg = client.strings.get("pcli.error.couldnotconnect", ex);  // "Could not connect to the server: " + ex
                System.err.println(msg);
                client.gameDisplay.showErrorPanel(msg, (ex_P == null));
                if (connected)
                {
                    disconnect();
                    connected = false;
                }
                host = null;
                port = 0;
                if (in != null)
                {
                    try { in.close(); } catch (Throwable th) {}
                    in = null;
                }
                if (out != null)
                {
                    try { out.close(); } catch (Throwable th) {}
                    out = null;
                }
                s = null;
            }
        }

        /**
         * Disconnect from the net (client of remote server).
         * If a problem occurs, sets {@link #ex}.
         * @see #dispose()
         */
        protected synchronized void disconnect()
        {
            connected = false;

            // reader will die once 'connected' is false, and socket is closed

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
         * Are we running a local tcp server?
         * @see #getLocalServerPort()
         * @see #anyHostedActiveGames()
         */
        public boolean isRunningLocalServer()
        {
            return localTCPServer != null;
        }

        /**
         * Look for active games that we're hosting (state >= START1A, not yet OVER).
         *
         * @return If any hosted games of ours are active
         * @see SOCPlayerClient.GameAwtDisplay#findAnyActiveGame(boolean)
         */
        public boolean anyHostedActiveGames()
        {
            if (localTCPServer == null)
                return false;

            Collection<String> gameNames = localTCPServer.getGameNames();

            for (String tryGm : gameNames)
            {
                int gs = localTCPServer.getGameState(tryGm);
                if ((gs < SOCGame.OVER) && (gs >= SOCGame.START1A))
                {
                    return true;  // Active
                }
            }

            return false;  // No active games found
        }

        /**
         * write a message to the net: either to a remote server,
         * or to {@link #localTCPServer} for games we're hosting.
         *<P>
         * If {@link #ex} != null, or ! {@link #connected}, {@code putNet}
         * returns false without attempting to send the message.
         *<P>
         * This message is copied to {@link #lastMessage_N}; any error sets {@link #ex}
         * and calls {@link SOCPlayerClient#dispose()} to show the error message.
         *
         * @param s  the message
         * @return true if the message was sent, false if not
         * @see SOCPlayerClient.GameManager#put(String, boolean)
         */
        public synchronized boolean putNet(String s)
        {
            lastMessage_N = s;

            if ((ex != null) || !isConnected())
            {
                return false;
            }

            if (client.debugTraffic || D.ebugIsEnabled())
                soc.debug.D.ebugPrintln("OUT - " + SOCMessage.toMsg(s));

            try
            {
                out.writeUTF(s);
                out.flush();
            }
            catch (IOException e)
            {
                ex = e;
                System.err.println("could not write to the net: " + ex);  // I18N: Not localizing console output yet
                client.dispose();

                return false;
            }

            return true;
        }

        /**
         * write a message to the practice server. {@link #localTCPServer} is not
         * the same as the practice server; use {@link #putNet(String)} to send
         * a message to the local TCP server.
         * Use <tt>putPractice</tt> only with {@link #practiceServer}.
         *<P>
         * Before version 1.1.14, this was <tt>putLocal</tt>.
         *
         * @param s  the message
         * @return true if the message was sent, false if not
         * @see SOCPlayerClient.GameManager#put(String, boolean)
         * @throws IllegalArgumentException if {@code s} is {@code null}
         * @since 1.1.00
         */
        public synchronized boolean putPractice(String s)
            throws IllegalArgumentException
        {
            if (s == null)
                throw new IllegalArgumentException("null");

            lastMessage_P = s;

            if ((ex_P != null) || !prCli.isConnected())
            {
                return false;
            }

            if (client.debugTraffic || D.ebugIsEnabled())
                soc.debug.D.ebugPrintln("OUT L- " + SOCMessage.toMsg(s));

            prCli.put(s);

            return true;
        }

        /**
         * resend the last message (to the network)
         */
        public void resendNet()
        {
            if (lastMessage_N != null)
                putNet(lastMessage_N);
        }

        /**
         * resend the last message (to the practice server)
         * @since 1.1.00
         */
        public void resendPractice()
        {
            if (lastMessage_P != null)
                putPractice(lastMessage_P);
        }

        /**
         * For shutdown - Tell the server we're leaving all games.
         * If we've started a practice server, also tell that server.
         * If we've started a TCP server, tell all players on that server, and shut it down.
         *<P><em>
         * Since no other state variables are set, call this only right before
         * discarding this object or calling System.exit.
         *</em>
         * @return Can we still start practice games? (No local exception yet in {@link #ex_P})
         */
        public boolean putLeaveAll()
        {
            final boolean canPractice = (ex_P == null);  // Can we still start a practice game?

            SOCLeaveAll leaveAllMes = new SOCLeaveAll();
            putNet(leaveAllMes.toCmd());
            if ((prCli != null) && ! canPractice)
                putPractice(leaveAllMes.toCmd());

            shutdownLocalServer();

            return canPractice;
        }


        /**
         * A task to continuously read from the server socket.
         * Not used for talking to the practice server.
         */
        static class NetReadTask implements Runnable
        {
            final ClientNetwork net;
            final SOCPlayerClient client;

            public NetReadTask(SOCPlayerClient client, ClientNetwork net)
            {
                this.client = client;
                this.net = net;
            }

            /**
             * continuously read from the net in a separate thread;
             * not used for talking to the practice server.
             */
            public void run()
            {
                Thread.currentThread().setName("cli-netread");  // Thread name for debug
                try
                {
                    while (net.isConnected())
                    {
                        String s = net.in.readUTF();
                        client.treater.treat(SOCMessage.toMsg(s), false);
                    }
                }
                catch (IOException e)
                {
                    // purposefully closing the socket brings us here too
                    if (net.isConnected())
                    {
                        net.ex = e;
                        System.out.println("could not read from the net: " + net.ex);  // I18N: Not localizing console output yet
                        client.dispose();
                    }
                }
            }

        }  // nested class NetReadTask


        /**
         * For practice games, reader thread to get messages from the
         * practice server to be treated and reacted to.
         * @author jdmonin
         * @since 1.1.00
         */
        class SOCPlayerLocalStringReader implements Runnable
        {
            LocalStringConnection locl;

            /**
             * Start a new thread and listen to practice server.
             *
             * @param prConn Active connection to practice server
             */
            protected SOCPlayerLocalStringReader (LocalStringConnection prConn)
            {
                locl = prConn;

                Thread thr = new Thread(this);
                thr.setDaemon(true);
                thr.start();
            }

            /**
             * Continuously read from the practice string server in a separate thread.
             */
            public void run()
            {
                Thread.currentThread().setName("cli-stringread");  // Thread name for debug
                try
                {
                    while (locl.isConnected())
                    {
                        String s = locl.readNext();
                        SOCMessage msg = SOCMessage.toMsg(s);

                        client.treater.treat(msg, true);
                    }
                }
                catch (IOException e)
                {
                    // purposefully closing the socket brings us here too
                    if (locl.isConnected())
                    {
                        ex_P = e;
                        System.out.println("could not read from practice server: " + ex_P);  // I18N: Not localizing console output yet
                        client.dispose();
                    }
                }
            }

        }  // nested class SOCPlayerLocalStringReader

    }  // nested class ClientNetwork


    /** React to windowOpened, windowClosing events for GameAwtDisplay's Frame. */
    private static class ClientWindowAdapter extends WindowAdapter
    {
        private final GameAwtDisplay cli;

        public ClientWindowAdapter(GameAwtDisplay c)
        {
            cli = c;
        }

        /**
         * User has clicked window Close button.
         * Check for active games, before exiting.
         * If we are playing in a game, or running a local tcp server hosting active games,
         * ask the user to confirm if possible.
         */
        @Override
        public void windowClosing(WindowEvent evt)
        {
            SOCPlayerInterface piActive = null;

            // Are we a client to any active games?
            if (piActive == null)
                piActive = cli.findAnyActiveGame(false);

            if (piActive != null)
            {
                SOCQuitAllConfirmDialog.createAndShow(piActive.getGameDisplay(), piActive);
                return;
            }
            boolean canAskHostingGames = false;
            boolean isHostingActiveGames = false;

            // Are we running a server?
            ClientNetwork cnet = cli.getClient().getNet();
            if (cnet.isRunningLocalServer())
                isHostingActiveGames = cnet.anyHostedActiveGames();

            if (isHostingActiveGames)
            {
                // If we have GUI, ask whether to shut down these games
                Container c = cli.getParent();
                if ((c != null) && (c instanceof Frame))
                {
                    canAskHostingGames = true;
                    SOCQuitAllConfirmDialog.createAndShow(cli, (Frame) c);
                }
            }

            if (! canAskHostingGames)
            {
                // Just quit.
                cli.getClient().getNet().putLeaveAll();
                System.exit(0);
            }
        }

        /**
         * Set focus to Nickname field
         */
        @Override
        public void windowOpened(WindowEvent evt)
        {
            if (! cli.hasConnectOrPractice)
                cli.nick.requestFocus();
        }

    }  // nested class ClientWindowAdapter


    /**
     * TimerTask used soon after client connect, to prevent waiting forever for
     * {@link SOCGameOptionInfo game options info}
     * (assume slow connection or server bug).
     * Set up when sending {@link SOCGameOptionGetInfos GAMEOPTIONGETINFOS}.
     *<P>
     * When timer fires, assume no more options will be received. Call
     * {@link SOCPlayerClient.MessageTreater#handleGAMEOPTIONINFO(SOCGameOptionInfo, boolean) handleGAMEOPTIONINFO("-",false)}
     * to trigger end-of-list behavior at client.
     * @author jdmonin
     * @since 1.1.07
     */
    private static class GameOptionsTimeoutTask extends TimerTask
    {
        public GameAwtDisplay pcli;
        public ServerGametypeInfo srvOpts;

        public GameOptionsTimeoutTask (GameAwtDisplay c, ServerGametypeInfo opts)
        {
            pcli = c;
            srvOpts = opts;
        }

        /**
         * Called when timer fires. See class description for action taken.
         */
        @Override
        public void run()
        {
            pcli.gameOptsTask = null;  // Clear reference to this soon-to-expire obj
            srvOpts.noMoreOptions(false);
            pcli.getClient().treater.handleGAMEOPTIONINFO
                (new SOCGameOptionInfo(new SOCGameOption("-"), Version.versionNumber(), null), false);
        }

    }  // GameOptionsTimeoutTask


    /**
     * TimerTask used when new game is asked for, to prevent waiting forever for
     * {@link SOCGameOption game option defaults}.
     * (in case of slow connection or server bug).
     * Set up when sending {@link SOCGameOptionGetDefaults GAMEOPTIONGETDEFAULTS}
     * in {@link SOCPlayerClient.GameAwtDisplay#gameWithOptionsBeginSetup(boolean, boolean)}.
     *<P>
     * When timer fires, assume no defaults will be received.
     * Display the new-game dialog.
     * @author jdmonin
     * @since 1.1.07
     */
    private static class GameOptionDefaultsTimeoutTask extends TimerTask
    {
        public GameAwtDisplay pcli;
        public ServerGametypeInfo srvOpts;
        public boolean forPracticeServer;

        public GameOptionDefaultsTimeoutTask (GameAwtDisplay c, ServerGametypeInfo opts, boolean forPractice)
        {
            pcli = c;
            srvOpts = opts;
            forPracticeServer = forPractice;
        }

        /**
         * Called when timer fires. See class description for action taken.
         */
        @Override
        public void run()
        {
            pcli.gameOptsDefsTask = null;  // Clear reference to this soon-to-expire obj
            srvOpts.noMoreOptions(true);
            if (srvOpts.newGameWaitingForOpts)
                pcli.gameWithOptionsBeginSetup(forPracticeServer, false);
        }

    }  // GameOptionDefaultsTimeoutTask


}  // public class SOCPlayerClient
