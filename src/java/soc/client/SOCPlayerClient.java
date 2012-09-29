/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2012 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net> - GameStatistics
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

import soc.client.stats.SOCGameStatistics;
import soc.debug.D;  // JM

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;

import soc.message.*;

import soc.server.SOCServer;
import soc.server.genericServer.LocalStringConnection;
import soc.server.genericServer.LocalStringServerSocket;
import soc.server.genericServer.StringConnection;

import soc.util.SOCGameList;
import soc.util.Version;

import java.applet.Applet;
import java.applet.AppletContext;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.net.ConnectException;
import java.net.Socket;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;


/**
 * Applet/Standalone client for connecting to the SOCServer.
 * Prompts for name and password, displays list of games and channels available.
 * The actual game is played in a separate {@link SOCPlayerInterface} window.
 *<P>
 * If you want another connection port, you have to specify it as the "port"
 * argument in the html source. If you run this as a stand-alone, you have to
 * specify the port.
 *<P>
 * At startup or init, will try to connect to server via {@link #connect()}.
 * See that method for more details.
 *<P>
 * There are three possible servers to which a client can be connected:
 *<UL>
 *  <LI>  A remote server, running on the other end of a TCP connection
 *  <LI>  A local TCP server, for hosting games, launched by this client: {@link #localTCPServer}
 *  <LI>  A "practice game" server, not bound to any TCP port, for practicing
 *        locally against robots: {@link #practiceServer}
 *</UL>
 * At most, the client is connected to the practice server and one TCP server.
 * Each game's {@link SOCGame#isPractice} flag determines which connection to use.
 *<P>
 * Once connected, messages from the server are processed in {@link #treat(SOCMessage, boolean)}.
 *
 * @author Robert S Thomas
 */
public class SOCPlayerClient extends Applet
    implements Runnable, ActionListener, TextListener, ItemListener, MouseListener
{
    /** main panel, in cardlayout */
    protected static final String MAIN_PANEL = "main";

    /** message panel, in cardlayout */
    protected static final String MESSAGE_PANEL = "message";

    /** connect-or-practice panel (if jar launch), in cardlayout */
    protected static final String CONNECT_OR_PRACTICE_PANEL = "connOrPractice";

    /** text prefix to show games this client cannot join. "(cannot join) "
     * @since 1.1.06
     */
    protected static final String GAMENAME_PREFIX_CANNOT_JOIN = "(cannot join) ";

    /**
     * Default tcp port number 8880 to listen, and to connect to remote server.
     * Should match SOCServer.SOC_PORT_DEFAULT.
     *<P>
     * 8880 is the default SOCPlayerClient port since jsettlers 1.0.4, per cvs history.
     * @since 1.1.00
     */
    public static final int SOC_PORT_DEFAULT = 8880;

    protected static final String STATSPREFEX = "  [";

    /**
     * For use in password fields, and possibly by other places, detect if we're running on
     * Mac OS X.  To identify osx from within java, see technote TN2110:
     * http://developer.apple.com/technotes/tn2002/tn2110.html
     * @since 1.1.07
     */
    public static final boolean isJavaOnOSX =
        System.getProperty("os.name").toLowerCase().startsWith("mac os x");

    protected TextField nick;
    protected TextField pass;
    protected TextField status;
    protected TextField channel;
    // protected TextField game;  // removed 1.1.07 - NewGameOptionsFrame instead
    protected java.awt.List chlist;
    protected java.awt.List gmlist;

    /**
     * "New Game..." button, brings up {@link NewGameOptionsFrame} window
     * @since 1.1.07
     */
    protected Button ng;  // new game

    protected Button jc;  // join channel
    protected Button jg;  // join game
    protected Button pg;  // practice game (local)

    /**
     * "Show Options" button, shows a game's {@link SOCGameOption}s
     * @since 1.1.07
     */
    protected Button so;

    protected Label messageLabel;  // error message for messagepanel
    protected Label messageLabel_top;   // secondary message
    private Label localTCPServerLabel;  // blank, or 'server is running'
    private Label versionOrlocalTCPPortLabel;   // shows port number in mainpanel, if running localTCPServer;
                                         // shows remote version# when connected to a remote server
    protected Button pgm;  // practice game on messagepanel
    protected AppletContext ac;

    /** For debug, our last messages sent, over the net and locally (pipes) */
    protected String lastMessage_N, lastMessage_L;

    /**
     * SOCPlayerClient displays one of several panels to the user:
     * {@link #MAIN_PANEL}, {@link #MESSAGE_PANEL} or
     * (if launched from jar, or with no command-line arguments)
     * {@link #CONNECT_OR_PRACTICE_PANEL}.
     *
     * @see #hasConnectOrPractice
     */
    protected CardLayout cardLayout;

    /**
     * Hostname we're connected to, or null; set in constructor or {@link #init()}
     */
    protected String host;
    protected int port;
    protected Socket s;
    protected DataInputStream in;
    protected DataOutputStream out;
    protected Thread reader = null;
    protected Exception ex = null;    // Network errors (TCP communication)
    protected Exception ex_L = null;  // Local errors (stringport pipes)
    protected boolean connected = false;

    /**
     *  Server version number for remote server, sent soon after connect, or -1 if unknown.
     *  A local server's version is always {@link Version#versionNumber()}.
     */
    protected int sVersion;

    /**
     * Track the game options available at the remote server, at the practice server.
     * Initialized by {@link #gameWithOptionsBeginSetup(boolean)}
     * and/or {@link #handleVERSION(boolean, SOCVersion)}.
     * These fields are never null, even if the respective server is not connected or not running.
     *<P>
     * For a summary of the flags and variables involved with game options,
     * and the client/server interaction about their values, see
     * {@link GameOptionServerSet}'s javadoc.
     *
     * @since 1.1.07
     */
    protected GameOptionServerSet tcpServGameOpts = new GameOptionServerSet(),
        practiceServGameOpts = new GameOptionServerSet();

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
     * @see #gameWithOptionsBeginSetup(boolean)
     * @since 1.1.07
     */
    protected GameOptionDefaultsTimeoutTask gameOptsDefsTask = null;

    /**
     * Utility for time-driven events in the client.
     * For users, search for where-used of this field
     * and of {@link #getEventTimer()}.
     * @since 1.1.07
     */
    protected Timer eventTimer = new Timer(true);  // use daemon thread

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
    protected boolean hasConnectOrPractice;

    /**
     * If applicable, is set up in {@link #initVisualElements()}.
     * @see #hasConnectOrPractice
     */
    protected SOCConnectOrPracticePanel connectOrPracticePane;

    /**
     * The currently showing new-game options frame, or null
     * @since 1.1.07
     */
    public NewGameOptionsFrame newGameOptsFrame = null;

    /**
     * For local practice games, default player name.
     */
    public static String DEFAULT_PLAYER_NAME = "Player";

    /**
     * For local practice games, default game name.
     */
    public static String DEFAULT_PRACTICE_GAMENAME = "Practice";

    /**
     * For local practice games, reminder message for network problems.
     */
    public static String NET_UNAVAIL_CAN_PRACTICE_MSG = "The server is unavailable. You can still play practice games.";

    /**
     * Hint message if they try to join game without entering a nickname.
     *
     * @see #NEED_NICKNAME_BEFORE_JOIN_2
     */
    public static String NEED_NICKNAME_BEFORE_JOIN = "First enter a nickname, then join a channel or game.";
    
    /**
     * Stronger hint message if they still try to join game without entering a nickname.
     *
     * @see #NEED_NICKNAME_BEFORE_JOIN
     */
    public static String NEED_NICKNAME_BEFORE_JOIN_2 = "You must enter a nickname before you can join a channel or game.";

    /**
     * Status text to indicate client cannot join a game.
     * @since 1.1.06
     */
    public static String STATUS_CANNOT_JOIN_THIS_GAME = "Cannot join, this client is incompatible with features of this game.";

    /**
     * the nickname; null until validated and set by
     * {@link #getValidNickname(boolean) getValidNickname(true)}
     */
    protected String nickname = null;

    /**
     * the password
     */
    protected String password = null;

    /**
     * true if we've stored the password
     */
    protected boolean gotPassword;

    /**
     * face ID chosen most recently (for use in new games)
     */
    protected int lastFaceChange;

    /**
     * the channels we've joined
     */
    protected Hashtable channels = new Hashtable();

    /**
     * the games we're currently playing
     */
    protected Hashtable games = new Hashtable();

    /**
     * all announced game names on the remote server, including games which we can't
     * join due to limitations of the client.
     * May also contain options for all announced games on the server (not just ones
     * we're in) which we can join (version is not higher than our version).
     *<P>
     * Key is the game name, without the UNJOINABLE prefix.
     * This field is null until {@link #handleGAMES(SOCGames, boolean) handleGAMES},
     *   {@link #handleGAMESWITHOPTIONS(SOCGamesWithOptions, boolean) handleGAMESWITHOPTIONS},
     *   {@link #handleNEWGAME(SOCNewGame, boolean) handleNEWGAME}
     *   or {@link #handleNEWGAMEWITHOPTIONS(SOCNewGameWithOptions, boolean) handleNEWGAMEWITHOPTIONS}
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
    protected Hashtable gamesUnjoinableOverride = new Hashtable();

    /**
     * the player interfaces for the games
     */
    protected Hashtable playerInterfaces = new Hashtable();

    /**
     * the ignore list
     */
    protected Vector ignoreList = new Vector();

    /**
     * for local-practice game via {@link #prCli}; not connected to
     * the network, not suited for multi-player games. Use {@link #localTCPServer}
     * for those.
     * SOCMessages of games where {@link SOCGame#isPractice} is true are sent
     * to practiceServer.
     *<P>
     * Null before it's started in {@link #startPracticeGame()}.
     */
    protected SOCServer practiceServer = null;

    /**
     * for connection to local-practice server {@link #practiceServer}.
     * Null before it's started in {@link #startPracticeGame()}.
     */
    protected StringConnection prCli = null;

    /**
     * Number of practice games started; used for naming practice games
     */
    protected int numPracticeGames = 0;

    /**
     * Client-hosted TCP server. If client is running this server, it's also connected
     * as a client, instead of being client of a remote server.
     * Started via {@link #startLocalTCPServer(int)}.
     * {@link #practiceServer} may still be activated at the user's request.
     * Note that {@link SOCGame#isPractice} is false for localTCPServer's games.
     */
    protected SOCServer localTCPServer = null;

    /**
     * Create a SOCPlayerClient connecting to localhost port {@link #SOC_PORT_DEFAULT}
     */
    public SOCPlayerClient()
    {
        this(null, SOC_PORT_DEFAULT, false);
    }

    /**
     * Create a SOCPlayerClient either connecting to localhost port {@link #SOC_PORT_DEFAULT},
     *   or initially showing 'Connect or Practice' panel.
     *
     * @param cp  If true, start by showing 'Connect or Practice' panel,
     *       instead of connecting to localhost port.
     */
    public SOCPlayerClient(boolean cp)
    {
        this(null, SOC_PORT_DEFAULT, cp);
    }

    /**
     * Constructor for connecting to the specified host, on the specified
     * port.  Must call 'init' or 'initVisualElements' to start up and do layout.
     *
     * @param h  host, or null for localhost
     * @param p  port
     */
    public SOCPlayerClient(String h, int p)
    {
        this (h, p, false);
    }

    /**
     * Constructor for connecting to the specified host, on the specified
     * port.  Must call 'init' or 'initVisualElements' to start up and do layout.
     *
     * @param h  host, or null for localhost
     * @param p  port
     * @param cp  If true, start by showing 'Connect or Practice' panel,
     *       instead of connecting to host and port.
     */
    public SOCPlayerClient(String h, int p, boolean cp)
    {
        gotPassword = false;
        host = h;
        port = p;
        hasConnectOrPractice = cp;
        lastFaceChange = 1;  // Default human face
    }

    /**
     * init the visual elements
     */
    protected void initVisualElements()
    {
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
        ng = new Button("New Game...");
        jc = new Button("Join Channel");
        jg = new Button("Join Game");
        pg = new Button("Practice");  // "practice game" text is too wide
        so = new Button("Show Options");  // show game options

        // Username not entered yet: can't click buttons
        ng.setEnabled(false);
        jc.setEnabled(false);

        // when game is selected in gmlist, these buttons will be enabled:
        jg.setEnabled(false);
        so.setEnabled(false);

        nick.addTextListener(this);    // Will enable buttons when field is not empty
        nick.addActionListener(this);  // hit Enter to go to next field
        pass.addActionListener(this);
        channel.addActionListener(this);
        chlist.addActionListener(this);
        gmlist.addActionListener(this);
        gmlist.addItemListener(this);
        ng.addActionListener(this);
        jc.addActionListener(this);
        jg.addActionListener(this);
        pg.addActionListener(this);        
        so.addActionListener(this);        

        ac = null;

        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        Panel mainPane = new Panel(gbl);

        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(status, c);
        mainPane.add(status);

        Label l;

        // Row 1

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 2

        l = new Label("Your Nickname:");
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

        l = new Label("Optional Password:");
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

        l = new Label("New Channel:");
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        gbl.setConstraints(channel, c);
        mainPane.add(channel);

        l = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;  // this position was "New Game:" label before 1.1.07
        gbl.setConstraints(pg, c);
        mainPane.add(pg);

        l = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        gbl.setConstraints(ng, c);
        mainPane.add(ng);

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 4 (spacer)

        localTCPServerLabel = new Label();
        c.gridwidth = 2;
        gbl.setConstraints(localTCPServerLabel, c);
        mainPane.add(localTCPServerLabel);

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 5 (version/port# label, join channel btn, show-options btn, join game btn)

        versionOrlocalTCPPortLabel = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(versionOrlocalTCPPortLabel, c);
        mainPane.add(versionOrlocalTCPPortLabel);

        c.gridwidth = 1;
        gbl.setConstraints(jc, c);
        mainPane.add(jc);

        l = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        gbl.setConstraints(so, c);
        mainPane.add(so);

        l = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        gbl.setConstraints(jg, c);
        mainPane.add(jg);

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 6

        l = new Label("Channels");
        c.gridwidth = 2;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new Label("Games");
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 7

        c.gridwidth = 2;
        c.gridheight = GridBagConstraints.REMAINDER;
        gbl.setConstraints(chlist, c);
        mainPane.add(chlist);

        l = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(gmlist, c);
        mainPane.add(gmlist);

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
        pgm = new Button("Practice Game (against robots)");
        pgm.setVisible(false);
        messagePane.add(pgm, BorderLayout.SOUTH);
        pgm.addActionListener(this);

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

        messageLabel.setText("Waiting to connect.");
        validate();
    }

    /**
     * Retrieve a parameter and translate to a hex value.
     *
     * @param name a parameter name. null is ignored
     * @return the parameter parsed as a hex value or -1 on error
     */
    public int getHexParameter(String name)
    {
        String value = null;
        int iValue = -1;
        try
        {
            value = getParameter(name);
            if (value != null)
            {
                iValue = Integer.parseInt(value, 16);
            }
        }
        catch (Exception e)
        {
            System.err.println("Invalid " + name + ": " + value);
        }
        return iValue;
    }

    /**
     * Called when the applet should start it's work.
     */
    public void start()
    {
        if (! hasConnectOrPractice)
            nick.requestFocus();
    }
    
    /**
     * Initialize the applet
     */
    public synchronized void init()
    {
        System.out.println("Java Settlers Client " + Version.version() +
                           ", build " + Version.buildnum() + ", " + Version.copyright());
        System.out.println("Network layer based on code by Cristian Bogdan; local network by Jeremy Monin.");

        String param = null;
        int intValue;
            
        intValue = getHexParameter("background"); 
        if (intValue != -1)
                setBackground(new Color(intValue));

        intValue = getHexParameter("foreground");
        if (intValue != -1)
            setForeground(new Color(intValue));

        initVisualElements(); // after the background is set

        param = getParameter("suggestion");
        if (param != null)
            channel.setText(param); // after visuals initialized

        param = getParameter("nickname");  // for use with dynamically-generated html
        if (param != null)
            nick.setText(param);

        System.out.println("Getting host...");
        host = getCodeBase().getHost();
        if (host.equals(""))
            host = null;  // localhost

        try {
            param = getParameter("PORT");
            if (param != null)
                port = Integer.parseInt(param);
        }
        catch (Exception e) {
            System.err.println("Invalid port: " + param);
        }

        connect();
    }

    /**
     * Connect and give feedback by showing MESSAGE_PANEL.
     * For more details, see {@link #connect()}.
     * @param chost Hostname to connect to, or null for localhost
     * @param cport Port number to connect to
     * @param cuser User nickname
     * @param cpass User optional password
     */
    public void connect(String chost, int cport, String cuser, String cpass)
    {
        host = chost;
        port = cport;
        nick.setText(cuser);
        pass.setText(cpass);
        cardLayout.show(this, MESSAGE_PANEL);
        connect();
    }

    /**
     * Attempts to connect to the server. See {@link #connected} for success or
     * failure. Once connected, starts a {@link #reader} thread.
     * The first message over the connection is our version,
     * and the second is the server's response:
     * Either {@link SOCRejectConnection}, or the lists of
     * channels and games ({@link SOCChannels}, {@link SOCGames}).
     *<P>
     * Before 1.1.06, the server's response was first,
     * and version was sent in reply to server's version.
     *
     * @throws IllegalStateException if already connected
     * @see soc.server.SOCServer#newConnection1(StringConnection)
     */
    public synchronized void connect()
    {
        String hostString = (host != null ? host : "localhost") + ":" + port;
        if (connected)
        {
            throw new IllegalStateException("Already connected to " +
                                            hostString);
        }
                
        System.out.println("Connecting to " + hostString);
        messageLabel.setText("Connecting to server...");
        
        try
        {
            s = new Socket(host, port);
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
            connected = true;
            (reader = new Thread(this)).start();
            // send VERSION right away (1.1.06 and later)
            putNet(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum()));
        }
        catch (Exception e)
        {
            ex = e;
            String msg = "Could not connect to the server: " + ex;
            System.err.println(msg);
            if (ex_L == null)
            {
                pgm.setVisible(true);
                messageLabel_top.setText(msg);                
                messageLabel_top.setVisible(true);
                messageLabel.setText(NET_UNAVAIL_CAN_PRACTICE_MSG);
                validate();
                pgm.requestFocus();
            }
            else
            {
                messageLabel.setText(msg);
            }
        }
    }

    /**
     * @return the nickname of this user
     * @see #getValidNickname(boolean)
     */
    public String getNickname()
    {
        return nickname;
    }

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
            so.setEnabled(wasSel && ((practiceServer != null)
                || (sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)));
        }
    }

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
        catch(Throwable thr)
        {
            System.err.println("-- Error caught in AWT event thread: " + thr + " --");
            thr.printStackTrace();
            while (thr.getCause() != null)
            {
                thr = thr.getCause();
                System.err.println(" --> Cause: " + thr + " --");
                thr.printStackTrace();
            }
            System.err.println("-- Error stack trace end --");
            System.err.println();
        }
    }

    /**
     * Act as if the "practice game" button has been clicked.
     * Assumes the dialog panels are all initialized.
     */
    public void clickPracticeButton()
    {
        guardedActionPerform(pgm);
    }

    /**
     * Wrapped version of actionPerformed() for easier encapsulation.
     * @param target Action source, from ActionEvent.getSource()
     */
    private void guardedActionPerform(Object target)
    {
        boolean showPopupCannotJoin = false;

        if ((target == jc) || (target == channel) || (target == chlist)) // Join channel stuff
        {
            showPopupCannotJoin = ! guardedActionPerform_channels(target);
        }
        else if ((target == jg) || (target == ng) || (target == gmlist)
                || (target == pg) || (target == pgm) || (target == so)) // Join game stuff
        {
            showPopupCannotJoin = ! guardedActionPerform_games(target);
        }

        if (showPopupCannotJoin)
        {
            status.setText(STATUS_CANNOT_JOIN_THIS_GAME);
            // popup
            NotifyDialog.createAndShow(this, (Frame) null,
                STATUS_CANNOT_JOIN_THIS_GAME,
                "Cancel", true);

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

        ChannelFrame cf = (ChannelFrame) channels.get(ch);

        if (cf == null)
        {
            if (channels.isEmpty())
            {
                // May set hint message if empty, like NEED_NICKNAME_BEFORE_JOIN
                if (! readValidNicknameAndPassword())
                    return true;  // not filled in yet
            }

            status.setText("Talking to server...");
            putNet(SOCJoin.toCmd(nickname, password, host, ch));
        }
        else
        {
            cf.show();
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
        nickname = getValidNickname(true);  // May set hint message if empty,
                                        // like NEED_NICKNAME_BEFORE_JOIN
        if (nickname == null)
           return false;  // not filled in yet

        if (!gotPassword)
        {
            password = getPassword();  // may be 0-length
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
            gm = DEFAULT_PRACTICE_GAMENAME;

            // If blank, fill in player name

            if (0 == nick.getText().trim().length())
            {
                nick.setText(DEFAULT_PLAYER_NAME);
            }
        }
        else if (target == ng)  // "New Game" button
        {
            if (null != getValidNickname(false))  // name check, but don't set nick field yet
            {
                gameWithOptionsBeginSetup(false);  // Also may set status, WAIT_CURSOR
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

        if (target == so)  // show game options
        {
            // This game is either from remote server, or local practice server,
            // both servers' games are in the same GUI list.
            Hashtable opts = null;
            if ((practiceServer != null) && (-1 != practiceServer.getGameState(gm)))
                opts = practiceServer.getGameOptions(gm);  // won't ever need to parse from string on practice server
            else if (serverGames != null)
            {
                opts = serverGames.getGameOptions(gm);
                if ((opts == null) && (serverGames.getGameOptionsString(gm) != null))
                {
                    // If necessary, parse game options from string before displaying.
                    // (Parsed options are cached, they won't be re-parsed)
    
                    if (tcpServGameOpts.allOptionsReceived)
                    {
                        opts = serverGames.parseGameOptions(gm);
                    } else {
                        // not yet received; remember game name.
                        // when all are received, will show it,
                        // and will also clear WAIT_CURSOR.
                        // (see handleGAMEOPTIONINFO)
    
                        tcpServGameOpts.gameInfoWaitingForOpts = gm;
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
        if (unjoinablePrefix || ((serverGames != null) && serverGames.isUnjoinableGame(gm)))
        {
            if (! gamesUnjoinableOverride.containsKey(gm))
            {
                gamesUnjoinableOverride.put(gm, gm);  // Next click will try override
                return false;
            }
        }

        // Are we already in a game with that name?
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gm);

        if ((pi == null)
                && ((target == pg) || (target == pgm))
                && (practiceServer != null)
                && (gm.equalsIgnoreCase(DEFAULT_PRACTICE_GAMENAME)))
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
                gameWithOptionsBeginSetup(true);
            }
            else
            {
                new SOCPracticeAskDialog(this, pi).show();
            }

            return true;
        }

        if (pi == null)
        {
            if (games.isEmpty())
            {
                nickname = getValidNickname(true);  // May set hint message if empty,
                                           // like NEED_NICKNAME_BEFORE_JOIN
                if (nickname == null)
                    return true;  // not filled in yet

                if (!gotPassword)
                    password = getPassword();  // may be 0-length
            }

            int endOfName = gm.indexOf(STATSPREFEX);

            if (endOfName > 0)
            {
                gm = gm.substring(0, endOfName);
            }

            if (((target == pg) || (target == pgm)) && (null == ex_L))
            {
                if (target == pg)
                {
                    status.setText("Starting practice game setup...");
                }
                gameWithOptionsBeginSetup(true);  // Also may set WAIT_CURSOR
            }
            else
            {
                // Join a game on the remote server.
                // Send JOINGAME right away.
                // (Create New Game is done above; see calls to gameWithOptionsBeginSetup)

                // May take a while for server to start game, so set WAIT_CURSOR.
                // The new-game window will clear this cursor
                // (SOCPlayerInterface constructor)

                status.setText("Talking to server...");
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                putNet(SOCJoinGame.toCmd(nickname, password, host, gm));
            }
        }
        else
        {
            pi.show();
        }

        return true;
    }

    /**
     * Validate and return the nickname textfield, or null if blank or not ready.
     * If successful, also set {@link #nickname} field.
     * @param precheckOnly If true, only validate the name, don't set {@link #nickname}.
     * @since 1.1.07
     */
    protected String getValidNickname(boolean precheckOnly)
    {
        String n = nick.getText().trim();

        if (n.length() == 0)
        {
            if (status.getText().equals(NEED_NICKNAME_BEFORE_JOIN))
                // Send stronger hint message
                status.setText(NEED_NICKNAME_BEFORE_JOIN_2);
            else
                // Send first hint message (or re-send first if they've seen _2)
                status.setText(NEED_NICKNAME_BEFORE_JOIN);
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
            nickname = n;
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
        if (gotPassword)
            return password;

        String p = pass.getText().trim();

        if (p.length() > 20)
        {
            p = p.substring(0, 20);
        }

        password = p;
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
     * Want to start a new game, on a server which supports options.
     * Do we know the valid options already?  If so, bring up the options window.
     * If not, ask the server for them.
     * Updates tcpServGameOpts, practiceServGameOpts, newGameOptsFrame.
     * If a {@link NewGameOptionsFrame} is already showing, give it focus
     * instead of creating a new one.
     *<P>
     * For a summary of the flags and variables involved with game options,
     * and the client/server interaction about their values, see
     * {@link GameOptionServerSet}.
     *
     * @param forPracticeServer  Ask {@link #practiceServer}, instead of remote tcp server?
     * @since 1.1.07
     */
    protected void gameWithOptionsBeginSetup(final boolean forPracticeServer)
    {
        if (newGameOptsFrame != null)
        {
            newGameOptsFrame.show();
            return;
        }

        GameOptionServerSet opts;

        // What server are we going against? Do we need to ask it for options?
        {
            boolean setKnown = false;
            if (forPracticeServer)
            {
                opts = practiceServGameOpts;
                if (! opts.allOptionsReceived)
                {
                    // We know what the practice options will be,
                    // because they're in our own JAR file.
                    // Also, the practice server isn't started yet,
                    // so we can't ask it for the options.
                    // The practice server will be started when the player clicks
                    // "Create Game" in the NewGameOptionsFrame, causing the new
                    // game to be requested from askStartGameWithOptions.
                    setKnown = true;
                    opts.optionSet = SOCGameOption.getAllKnownOptions();
                }
            } else {
                opts = tcpServGameOpts;
                if ((! opts.allOptionsReceived) && (sVersion < SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS))
                {
                    // Server doesn't support them.  Don't ask it.
                    setKnown = true;
                    opts.optionSet = null;
                }
            }

            if (setKnown)
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

        // OK, we need the options.
        // Ask the server by sending GAMEOPTIONGETDEFAULTS.
        // (This will never happen for local practice games, see above.)

        // May take a while for server to send our info.
        // The new-game-options window will clear this cursor
        // (NewGameOptionsFrame constructor)

        status.setText("Talking to server...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        opts.newGameWaitingForOpts = true;
        opts.askedDefaultsAlready = true;
        opts.askedDefaultsTime = System.currentTimeMillis();
        put(SOCGameOptionGetDefaults.toCmd(null), forPracticeServer);

        if (gameOptsDefsTask != null)
            gameOptsDefsTask.cancel();
        gameOptsDefsTask = new GameOptionDefaultsTimeoutTask(this, tcpServGameOpts, forPracticeServer);
        eventTimer.schedule(gameOptsDefsTask, 5000 /* ms */ );

        // Once options are received, handlers will
        // create and show NewGameOptionsFrame.
    }

    /**
     * Ask server to start a game with options.
     * If is local(practice), will call {@link #startPracticeGame(String, Hashtable, boolean)}.
     * Otherwise, ask remote server, and also set WAIT_CURSOR and status line ("Talking to server...").
     *<P>
     * Assumes {@link #getValidNickname(boolean) getValidNickname(true)}, {@link #getPassword()}, {@link #host},
     * and {@link #gotPassword} are already called and valid.
     *
     * @param gmName Game name; for practice, null is allowed
     * @param forPracticeServer Is this for a new game on the local-practice (not remote) server?
     * @param opts Set of {@link SOCGameOption game options} to use, or null
     * @since 1.1.07
     * @see #readValidNicknameAndPassword()
     */
    public void askStartGameWithOptions
        (final String gmName, final boolean forPracticeServer, Hashtable opts)
    {
        if (forPracticeServer)
        {
            startPracticeGame(gmName, opts, true);  // Also sets WAIT_CURSOR
        } else {
            String askMsg =
                (sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
                ? SOCNewGameWithOptionsRequest.toCmd
                        (nickname, password, host, gmName, opts)
                : SOCJoinGame.toCmd(nickname, password, host, gmName);
            putNet(askMsg);
            status.setText("Talking to server...");
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
    }

    /**
     * Look for active games that we're playing
     *
     * @param fromPracticeServer  Enumerate games from {@link #practiceServer},
     *     instead of {@link #playerInterfaces}?
     * @return Any found game of ours which is active (state not OVER), or null if none.
     * @see #anyHostedActiveGames()
     */
    protected SOCPlayerInterface findAnyActiveGame (boolean fromPracticeServer)
    {
        SOCPlayerInterface pi = null;
        int gs;  // gamestate

        Enumeration gameNames;
        if (fromPracticeServer)
        {
            if (practiceServer == null)
                return null;  // <---- Early return: no games if no practice server ----
            gameNames = practiceServer.getGameNames();
        } else {
            gameNames = playerInterfaces.keys();
        }

        while (gameNames.hasMoreElements())
        {
            String tryGm = (String) gameNames.nextElement();

            if (fromPracticeServer)
            {
                gs = practiceServer.getGameState(tryGm);
                if (gs < SOCGame.OVER)
                {
                    pi = (SOCPlayerInterface) playerInterfaces.get(tryGm);
                    if (pi != null)
                        break;  // Active and we have a window with it
                }
            } else {
                pi = (SOCPlayerInterface) playerInterfaces.get(tryGm);
                if (pi != null)
                {
                    // we have a window with it
                    gs = pi.getGame().getGameState();
                    if (gs < SOCGame.OVER)
                    {
                        break;      // Active
                    } else {
                        pi = null;  // Avoid false positive
                    }
                }
            }
        }

        return pi;  // Active game, or null
    }

    /**
     * Look for active games that we're hosting (state >= START1A, not yet OVER).
     *
     * @return If any hosted games of ours are active
     * @see #findAnyActiveGame(boolean)
     */
    protected boolean anyHostedActiveGames ()
    {
        if (localTCPServer == null)
            return false;

        Enumeration gameNames = localTCPServer.getGameNames();

        while (gameNames.hasMoreElements())
        {
            String tryGm = (String) gameNames.nextElement();
            int gs = localTCPServer.getGameState(tryGm);
            if ((gs < SOCGame.OVER) && (gs >= SOCGame.START1A))
            {
                return true;  // Active
            }
        }

        return false;  // No active games found
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
            while (connected)
            {
                String s = in.readUTF();
                treat((SOCMessage) SOCMessage.toMsg(s), false);
            }
        }
        catch (IOException e)
        {
            // purposefully closing the socket brings us here too
            if (connected)
            {
                ex = e;
                System.out.println("could not read from the net: " + ex);
                destroy();
            }
        }
    }

    /**
     * resend the last message (to the network)
     */
    public void resendNet()
    {
        putNet(lastMessage_N);
    }

    /**
     * resend the last message (to the local practice server)
     */
    public void resendPractice()
    {
        putPractice(lastMessage_L);
    }

    /**
     * write a message to the net: either to a remote server,
     * or to {@link #localTCPServer} for games we're hosting.
     *
     * @param s  the message
     * @return true if the message was sent, false if not
     * @see #put(String, boolean)
     */
    public synchronized boolean putNet(String s)
    {
        lastMessage_N = s;

        if ((ex != null) || !connected)
        {
            return false;
        }

        if (D.ebugIsEnabled())
            D.ebugPrintln("OUT - " + SOCMessage.toMsg(s));

        try
        {
            out.writeUTF(s);
        }
        catch (IOException e)
        {
            ex = e;
            System.err.println("could not write to the net: " + ex);
            destroy();

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
     * @see #put(String, boolean)
     */
    public synchronized boolean putPractice(String s)
    {
        lastMessage_L = s;

        if ((ex_L != null) || !prCli.isConnected())
        {
            return false;
        }

        if (D.ebugIsEnabled())
            D.ebugPrintln("OUT L- " + SOCMessage.toMsg(s));

        prCli.put(s);

        return true;
    }

    /**
     * Write a message to the net or the local practice server.
     * Because the player can be in both network games and local games,
     * we must route to the appropriate client-server connection.
     * 
     * @param s  the message
     * @param isPractice Is the server local (practice game), not network?
     *                {@link #localTCPServer} is considered "network" here.
     *                Use <tt>isPractice</tt> only with {@link #practiceServer}.
     * @return true if the message was sent, false if not
     */
    public synchronized boolean put(String s, final boolean isPractice)
    {
        if (isPractice)
            return putPractice(s);
        else
            return putNet(s);
    }

    /**
     * Treat the incoming messages.
     * Messages of unknown type are ignored (mes will be null from {@link SOCMessage#toMsg(String)}).
     *
     * @param mes    the message
     * @param isPractice  Server is local (practice game, not network)
     */
    public void treat(SOCMessage mes, final boolean isPractice)
    {
        if (mes == null)
            return;  // Parsing error

        D.ebugPrintln(mes.toString());

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
            case SOCMessage.JOINAUTH:
                handleJOINAUTH((SOCJoinAuth) mes);

                break;

            /**
             * someone joined a channel
             */
            case SOCMessage.JOIN:
                handleJOIN((SOCJoin) mes);

                break;

            /**
             * list of members for a channel
             */
            case SOCMessage.MEMBERS:
                handleMEMBERS((SOCMembers) mes);

                break;

            /**
             * a new channel has been created
             */
            case SOCMessage.NEWCHANNEL:
                handleNEWCHANNEL((SOCNewChannel) mes);

                break;

            /**
             * list of channels on the server
             */
            case SOCMessage.CHANNELS:
                handleCHANNELS((SOCChannels) mes, isPractice);

                break;

            /**
             * text message
             */
            case SOCMessage.TEXTMSG:
                handleTEXTMSG((SOCTextMsg) mes);

                break;

            /**
             * someone left the channel
             */
            case SOCMessage.LEAVE:
                handleLEAVE((SOCLeave) mes);

                break;

            /**
             * delete a channel
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
            case SOCMessage.DEVCARD:
                handleDEVCARD(isPractice, (SOCDevCard) mes);

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
             * a player (or us) is requesting a board reset: we must vote
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
             * move a previous piece (a ship) somewhere else on the board.
             * Added 2011-12-05 for v2.0.00.
             */
            case SOCMessage.MOVEPIECE:
                handleMOVEPIECE((SOCMovePiece) mes);
                break;

            /**
             * pick resources to gain from the gold hex.
             * Added 2012-01-12 for v2.0.00.
             */
            case SOCMessage.PICKRESOURCESREQUEST:
                handlePICKRESOURCESREQUEST((SOCPickResourcesRequest) mes);
                break;

            }  // switch (mes.getType())            
        }
        catch (Exception e)
        {
            System.out.println("SOCPlayerClient treat ERROR - " + e.getMessage());
            e.printStackTrace();
        }

    }  // treat

    /**
     * Handle the "version" message, server's version report.
     * Ask server for game-option info if client's version differs.
     * If remote, store the server's version for {@link #getServerVersion(SOCGame)}
     * and display the version on the main panel.
     * (Local server's version is always {@link Version#versionNumber()}.)
     *
     * @param isPractice Is the server {@link #practiceServer}, not remote?  Client can be connected
     *                only to one at a time.
     * @param mes  the messsage
     */
    private void handleVERSION(final boolean isPractice, SOCVersion mes)
    {
        D.ebugPrintln("handleVERSION: " + mes);
        int vers = mes.getVersionNumber();
        if (! isPractice)
        {
            sVersion = vers;

            // Display the version on main panel, unless we're running a server.
            // (If so, want to display its listening port# instead)
            if (null == localTCPServer)
            {
                versionOrlocalTCPPortLabel.setForeground(new Color(252, 251, 243)); // off-white
                versionOrlocalTCPPortLabel.setText("v " + mes.getVersionString());
                new AWTToolTip ("Server version is " + mes.getVersionString()
                                + " build " + mes.getBuild()
                                + "; client is " + Version.version()
                                + " bld " + Version.buildnum(),
                                versionOrlocalTCPPortLabel);
            }

            if ((practiceServer == null) && (sVersion < SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
                    && (so != null))
                so.setEnabled(false);  // server too old for options, so don't use that button
        }

        // If we ever require a minimum server version, would check that here.

        // Reply with our client version.
        // (This was sent already in connect(), in 1.1.06 and later)

        // Check known game options vs server's version. (added in 1.1.07)
        // Server's responses will add, remove or change our "known options".
        final int cliVersion = Version.versionNumber();
        if (sVersion > cliVersion)
        {
            // Newer server: Ask it to list any options we don't know about yet.
            if (! isPractice)
                gameOptionsSetTimeoutTask();
            put(SOCGameOptionGetInfos.toCmd(null), isPractice);  // sends "-"
        } else if (sVersion < cliVersion)
        {
            if (sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
            {
                // Older server: Look for options created or changed since server's version.
                // Ask it what it knows about them.
                Vector tooNewOpts = SOCGameOption.optionsNewerThanVersion(sVersion, false, false, null);
                if (tooNewOpts != null)
                {
                    if (! isPractice)
                        gameOptionsSetTimeoutTask();
                    put(SOCGameOptionGetInfos.toCmd(tooNewOpts.elements()), isPractice);
                }
            } else {
                // server is too old to understand options. Can't happen with local practice srv,
                // because that's our version (it runs from our own JAR file).
                if (! isPractice)
                    tcpServGameOpts.noMoreOptions(true);
            }
        } else {
            // sVersion == cliVersion, so we have same code as server for getAllKnownOptions.
            // For local practice games, optionSet may already be initialized, so check vs null.
            GameOptionServerSet opts = (isPractice ? practiceServGameOpts : tcpServGameOpts);
            if (opts.optionSet == null)
                opts.optionSet = SOCGameOption.getAllKnownOptions();
            opts.noMoreOptions(isPractice);  // defaults not known unless it's local practice
        }
    }

    /**
     * handle the {@link SOCStatusMessage "status"} message.
     * Used for server events, also used if player tries to join a game
     * but their nickname is not OK.
     * @param mes  the message
     * @param isPractice from practice server, not remote server?
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes, final boolean isPractice)
    {
        status.setText(mes.getStatus());
        // If was trying to join a game, reset cursor from WAIT_CURSOR.
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        if (mes.getStatusValue() == SOCStatusMessage.SV_NEWGAME_OPTION_VALUE_TOONEW)
        {
            // Extract game name and failing game-opt keynames,
            // and pop up an error message window.
            String errMsg;
            StringTokenizer st = new StringTokenizer(mes.getStatus(), SOCMessage.sep2);
            try
            {
                String gameName = null;
                Vector optNames = new Vector();
                errMsg = st.nextToken();
                gameName = st.nextToken();
                while (st.hasMoreTokens())
                    optNames.addElement(st.nextToken());
                StringBuffer err = new StringBuffer("Cannot create game ");
                err.append(gameName);
                err.append("\nThere is a problem with the option values chosen.\n");
                err.append(errMsg);
                Hashtable knowns = isPractice ? practiceServGameOpts.optionSet : tcpServGameOpts.optionSet;
                for (int i = 0; i < optNames.size(); ++i)
                {
                    err.append("\nThis option must be changed: ");
                    String oname = (String) optNames.elementAt(i);
                    SOCGameOption oinfo = null;
                    if (knowns != null)
                        oinfo = (SOCGameOption) knowns.get(oname);
                    if (oinfo != null)
                        oname = oinfo.optDesc;
                    err.append(oname);
                }
                errMsg = err.toString();
            }
            catch (Throwable t)
            {
                errMsg = mes.getStatus();  // fallback, not expected to happen
            }
            NotifyDialog.createAndShow(this, (Frame) null,
                errMsg, "Cancel", false);
        }
    }

    /**
     * handle the "join authorization" message
     * @param mes  the message
     */
    protected void handleJOINAUTH(SOCJoinAuth mes)
    {
        nick.setEditable(false);
        pass.setText("");
        pass.setEditable(false);
        gotPassword = true;
        if (! hasJoinedServer)
        {
            Container c = getParent();
            if ((c != null) && (c instanceof Frame))
            {
                Frame fr = (Frame) c;
                fr.setTitle(fr.getTitle() + " [" + nick.getText() + "]");
            }
            hasJoinedServer = true;
        }

        ChannelFrame cf = new ChannelFrame(mes.getChannel(), this);
        cf.setVisible(true);
        channels.put(mes.getChannel(), cf);
    }

    /**
     * handle the "join channel" message
     * @param mes  the message
     */
    protected void handleJOIN(SOCJoin mes)
    {
        ChannelFrame fr;
        fr = (ChannelFrame) channels.get(mes.getChannel());
        fr.print("*** " + mes.getNickname() + " has joined this channel.\n");
        fr.addMember(mes.getNickname());
    }

    /**
     * handle the "members" message
     * @param mes  the message
     */
    protected void handleMEMBERS(SOCMembers mes)
    {
        ChannelFrame fr;
        fr = (ChannelFrame) channels.get(mes.getChannel());

        Enumeration membersEnum = (mes.getMembers()).elements();

        while (membersEnum.hasMoreElements())
        {
            fr.addMember((String) membersEnum.nextElement());
        }

        fr.began();
    }

    /**
     * handle the "new channel" message
     * @param mes  the message
     */
    protected void handleNEWCHANNEL(SOCNewChannel mes)
    {
        addToList(mes.getChannel(), chlist);
    }

    /**
     * handle the "list of channels" message; this message indicates that
     * we're newly connected to the server.
     * @param mes  the message
     * @param isPractice is the server actually local (practice game)?
     */
    protected void handleCHANNELS(SOCChannels mes, final boolean isPractice)
    {
        //
        // this message indicates that we're connected to the server
        //
        if (! isPractice)
        {
            cardLayout.show(this, MAIN_PANEL);
            validate();

            nick.requestFocus();
            status.setText("Login by entering nickname and then joining a channel or game.");
        }

        Enumeration channelsEnum = (mes.getChannels()).elements();

        while (channelsEnum.hasMoreElements())
        {
            addToList((String) channelsEnum.nextElement(), chlist);
        }
    }

    /**
     * handle a broadcast text message
     * @param mes  the message
     */
    protected void handleBCASTTEXTMSG(SOCBCastTextMsg mes)
    {
        ChannelFrame fr;
        Enumeration channelKeysEnum = channels.keys();

        while (channelKeysEnum.hasMoreElements())
        {
            fr = (ChannelFrame) channels.get(channelKeysEnum.nextElement());
            fr.print("::: " + mes.getText() + " :::");
        }

        SOCPlayerInterface pi;
        Enumeration playerInterfaceKeysEnum = playerInterfaces.keys();

        while (playerInterfaceKeysEnum.hasMoreElements())
        {
            pi = (SOCPlayerInterface) playerInterfaces.get(playerInterfaceKeysEnum.nextElement());
            pi.chatPrint("::: " + mes.getText() + " :::");
        }
    }

    /**
     * handle a text message
     * @param mes  the message
     */
    protected void handleTEXTMSG(SOCTextMsg mes)
    {
        ChannelFrame fr;
        fr = (ChannelFrame) channels.get(mes.getChannel());

        if (fr != null)
        {
            if (!onIgnoreList(mes.getNickname()))
            {
                fr.print(mes.getNickname() + ": " + mes.getText());
            }
        }
    }

    /**
     * handle the "leave channel" message
     * @param mes  the message
     */
    protected void handleLEAVE(SOCLeave mes)
    {
        ChannelFrame fr;
        fr = (ChannelFrame) channels.get(mes.getChannel());
        fr.print("*** " + mes.getNickname() + " left.\n");
        fr.deleteMember(mes.getNickname());
    }

    /**
     * handle the "delete channel" message
     * @param mes  the message
     */
    protected void handleDELETECHANNEL(SOCDeleteChannel mes)
    {
        deleteFromList(mes.getChannel(), chlist);
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

        Enumeration gameNamesEnum = mes.getGames().elements();

        if (! isPractice)  // local's gameoption data is set up in handleVERSION
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
            gameNamesEnum = mes.getGames().elements();
        }

        while (gameNamesEnum.hasMoreElements())
        {
            addToGameList((String) gameNamesEnum.nextElement(), null, false);
        }
    }

    /**
     * handle the "join game authorization" message: create new {@link SOCGame} and
     * {@link SOCPlayerInterface} so user can join the game
     * @param mes  the message
     * @param isPractice server is local for practice (vs. normal network)
     */
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes, final boolean isPractice)
    {
        nick.setEditable(false);
        pass.setEditable(false);
        pass.setText("");
        gotPassword = true;
        if (! hasJoinedServer)
        {
            Container c = getParent();
            if ((c != null) && (c instanceof Frame))
            {
                Frame fr = (Frame) c;
                fr.setTitle(fr.getTitle() + " [" + nick.getText() + "]");
            }
            hasJoinedServer = true;
        }

        final String gaName = mes.getGame();
        Hashtable gameOpts;
        if (isPractice)
        {
            gameOpts = practiceServGameOpts.optionSet;  // holds most recent settings by user
            if (gameOpts != null)
                gameOpts = (Hashtable) gameOpts.clone();  // changes here shouldn't change practiceServ's copy
        } else {
            if (serverGames != null)
                gameOpts = serverGames.parseGameOptions(gaName);
            else
                gameOpts = null;
        }

        SOCGame ga = new SOCGame(gaName, gameOpts);
        if (ga != null)
        {
            ga.isPractice = isPractice;
            SOCPlayerInterface pi = new SOCPlayerInterface(gaName, this, ga);
            pi.setVisible(true);
            playerInterfaces.put(gaName, pi);
            games.put(gaName, ga);
        }
    }

    /**
     * handle the "join game" message
     * @param mes  the message
     */
    protected void handleJOINGAME(SOCJoinGame mes)
    {
        final String gn = mes.getGame();
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gn);
        final String msg = "*** " + mes.getNickname() + " has joined this game.\n";
        pi.print(msg);
        SOCGame ga = (SOCGame) games.get(gn);
        if ((ga != null) && (ga.getGameState() >= SOCGame.START1A))
            pi.chatPrint(msg);
    }

    /**
     * handle the "leave game" message
     * @param mes  the message
     */
    protected void handleLEAVEGAME(SOCLeaveGame mes)
    {
        String gn = mes.getGame();
        SOCGame ga = (SOCGame) games.get(gn);

        final String name = mes.getNickname();
        if (ga != null)
        {
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gn);
            SOCPlayer player = ga.getPlayer(name);

            if (player != null)
            {
                //
                //  This user was not a spectator.
                //  Remove first from interface, then from game data.
                //
                pi.removePlayer(player.getPlayerNumber());
                ga.removePlayer(name);
            }
            else if (ga.getGameState() >= SOCGame.START1A)
            {
                //  Spectator, game in progress.
                //  Server prints it in the game text area,
                //  and we also print in the chat area (less clutter there).
                pi.chatPrint("* " + name + " left the game");
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
        if (! deleteFromGameList(mes.getGame(), isPractice))
            deleteFromGameList(GAMENAME_PREFIX_CANNOT_JOIN + mes.getGame(), isPractice);
    }

    /**
     * handle the "game members" message, the server's hint that it's almost
     * done sending us the complete game state in response to JOINGAME.
     * @param mes  the message
     */
    protected void handleGAMEMEMBERS(SOCGameMembers mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.began(mes.getMembers());
    }

    /**
     * handle the "game stats" message
     */
    protected void handleGAMESTATS(SOCGameStats mes)
    {
        String ga = mes.getGame();
        int[] scores = mes.getScores();
        
        // Update game list (initial window)
        updateGameStats(ga, scores, mes.getRobotSeats());
        
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
     */
    protected void handleGAMETEXTMSG(SOCGameTextMsg mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());

        if (pi != null)
        {
            if (mes.getNickname().equals("Server"))
            {
                String mesText = mes.getText();
                String starMesText = "* " + mesText;
                pi.print(starMesText);
                if (mesText.startsWith(">>>"))
                    pi.chatPrint(starMesText);
            }
            else
            {
                if (!onIgnoreList(mes.getNickname()))
                {
                    pi.chatPrint(mes.getNickname() + ": " + mes.getText());
                }
            }
        }
    }

    /**
     * handle the "player sitting down" message
     * @param mes  the message
     */
    protected void handleSITDOWN(SOCSitDown mes)
    {
        /**
         * tell the game that a player is sitting
         */
        final SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            final int mesPN = mes.getPlayerNumber();
            ga.takeMonitor();

            try
            {
                ga.addPlayer(mes.getNickname(), mesPN);

                /**
                 * set the robot flag
                 */
                ga.getPlayer(mesPN).setRobotFlag(mes.isRobot(), false);
            }
            catch (Exception e)
            {
                ga.releaseMonitor();
                System.out.println("Exception caught - " + e);
                e.printStackTrace();
            }

            ga.releaseMonitor();

            /**
             * tell the GUI that a player is sitting
             */
            final SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            pi.addPlayer(mes.getNickname(), mesPN);

            /**
             * let the board panel & building panel find our player object if we sat down
             */
            if (nickname.equals(mes.getNickname()))
            {
                pi.getBoardPanel().setPlayer();
                pi.getBuildingPanel().setPlayer();

                /**
                 * change the face (this is so that old faces don't 'stick')
                 */
                if (! ga.isBoardReset() && (ga.getGameState() < SOCGame.START1A))
                {
                    ga.getPlayer(mesPN).setFaceId(lastFaceChange);
                    changeFace(ga, lastFaceChange);
                }
            }

            /**
             * update the hand panel's displayed values
             */
            final SOCHandPanel hp = pi.getPlayerHandPanel(mesPN);
            hp.updateValue(SOCPlayerElement.ROADS);
            hp.updateValue(SOCPlayerElement.SETTLEMENTS);
            hp.updateValue(SOCPlayerElement.CITIES);
            if (ga.hasSeaBoard)
                hp.updateValue(SOCPlayerElement.SHIPS);
            hp.updateValue(SOCPlayerElement.NUMKNIGHTS);
            hp.updateValue(SOCHandPanel.VICTORYPOINTS);
            hp.updateValue(SOCHandPanel.LONGESTROAD);
            hp.updateValue(SOCHandPanel.LARGESTARMY);

            if (nickname.equals(mes.getNickname()))
            {
                hp.updateValue(SOCPlayerElement.CLAY);
                hp.updateValue(SOCPlayerElement.ORE);
                hp.updateValue(SOCPlayerElement.SHEEP);
                hp.updateValue(SOCPlayerElement.WHEAT);
                hp.updateValue(SOCPlayerElement.WOOD);
                hp.updateDevCards();
            }
            else
            {
                hp.updateValue(SOCHandPanel.NUMRESOURCES);
                hp.updateValue(SOCHandPanel.NUMDEVCARDS);
            }
        }
    }

    /**
     * handle the "board layout" message
     * @param mes  the message
     */
    protected void handleBOARDLAYOUT(SOCBoardLayout mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            // BOARDLAYOUT is always the v1 board encoding (oldest format)
            SOCBoard bd = ga.getBoard();
            bd.setHexLayout(mes.getHexLayout());
            bd.setNumberLayout(mes.getNumberLayout());
            bd.setRobberHex(mes.getRobberHex(), false);

            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            pi.getBoardPanel().flushBoardLayoutAndRepaint();
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
            put(mes.toCmd(), isPractice);
        } else {
            ex = new RuntimeException("Kicked by player with same name.");
            destroy();
        }
    }

    /**
     * handle the "board layout" message, new format
     * @param mes  the message
     * @since 1.1.08
     */
    protected void handleBOARDLAYOUT2(SOCBoardLayout2 mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());
        if (ga == null)
            return;

        SOCBoard bd = ga.getBoard();
        final int bef = mes.getBoardEncodingFormat();
        bd.setBoardEncodingFormat(bef);
        if (bef == SOCBoard.BOARD_ENCODING_LARGE)
        {
            // v3
            ((SOCBoardLarge) bd).setLandHexLayout(mes.getIntArrayPart("LH"));
            ga.setPlayersLandHexCoordinates();
            int hex = mes.getIntPart("RH");
            if (hex != 0)
                bd.setRobberHex(hex, false);
            hex = mes.getIntPart("PH");
            if (hex != 0)
                ((SOCBoardLarge) bd).setPirateHex(hex, false);
            int[] portLayout = mes.getIntArrayPart("PL");
            if (portLayout != null)
                bd.setPortsLayout(portLayout);
        }
        else if (bef <= SOCBoard.BOARD_ENCODING_6PLAYER)
        {
            // v1 or v2
            bd.setHexLayout(mes.getIntArrayPart("HL"));
            bd.setNumberLayout(mes.getIntArrayPart("NL"));
            bd.setRobberHex(mes.getIntPart("RH"), false);
            int[] portLayout = mes.getIntArrayPart("PL");
            if (portLayout != null)
                bd.setPortsLayout(portLayout);
        } else {
            // Should not occur: Server has sent an unrecognized format
            System.err.println
                ("Cannot recognize game encoding v" + bef + " for game " + ga.getName());
            return;
        }
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.getBoardPanel().flushBoardLayoutAndRepaint();
    }

    /**
     * handle the "start game" message
     * @param mes  the message
     */
    protected void handleSTARTGAME(SOCStartGame mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.startGame();
    }

    /**
     * handle the "game state" message
     * @param mes  the message
     */
    protected void handleGAMESTATE(SOCGameState mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            if (ga.getGameState() == SOCGame.NEW && mes.getState() != SOCGame.NEW)
            {
                pi.startGame();
            }

            ga.setGameState(mes.getState());
            pi.updateAtGameState();
        }
    }

    /**
     * handle the "set turn" message
     * @param mes  the message
     */
    protected void handleSETTURN(SOCSetTurn mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = (SOCGame) games.get(gaName);
        if (ga == null)
            return;  // <--- Early return: not playing in that one ----

        final int pn = mes.getPlayerNumber();
        ga.setCurrentPlayerNumber(pn);

        // repaint board panel, update buttons' status, etc:
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gaName);
        pi.updateAtTurn(pn);
    }

    /**
     * handle the "first player" message
     * @param mes  the message
     */
    protected void handleFIRSTPLAYER(SOCFirstPlayer mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

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
        SOCGame ga = (SOCGame) games.get(gaName);

        if (ga != null)
        {
            final int pnum = mes.getPlayerNumber();
            ga.setCurrentPlayerNumber(pnum);
            ga.updateAtTurn();
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gaName);
            pi.updateAtTurn(pnum);
        }
    }

    /**
     * handle the "player information" message
     * @param mes  the message
     */
    protected void handlePLAYERELEMENT(SOCPlayerElement mes)
    {
        final SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            final int pn = mes.getPlayerNumber();
            final SOCPlayer pl = ga.getPlayer(pn);
            final SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            final SOCHandPanel hpan = pi.getPlayerHandPanel(pn);
            final int etype = mes.getElementType();
            int hpanUpdateRsrcType = 0;  // If not 0, update this type's amount display

            switch (etype)
            {
            case SOCPlayerElement.ROADS:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.ROAD);
                hpan.updateValue(etype);
                break;

            case SOCPlayerElement.SETTLEMENTS:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.SETTLEMENT);
                hpan.updateValue(etype);
                break;

            case SOCPlayerElement.CITIES:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.CITY);
                hpan.updateValue(etype);
                break;

            case SOCPlayerElement.SHIPS:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.SHIP);
                hpan.updateValue(etype);
                break;

            case SOCPlayerElement.NUMKNIGHTS:
                // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
                {
                    final SOCPlayer oldLargestArmyPlayer = ga.getPlayerWithLargestArmy();
                    SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numKnights
                        (mes, pl, ga);
                    hpan.updateValue(etype);

                    // Check for change in largest-army player; update handpanels'
                    // LARGESTARMY and VICTORYPOINTS counters if so, and
                    // announce with text message.
                    pi.updateLongestLargest(false, oldLargestArmyPlayer, ga.getPlayerWithLargestArmy());
                }

                break;

            case SOCPlayerElement.CLAY:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.CLAY);
                hpanUpdateRsrcType = etype;
                break;

            case SOCPlayerElement.ORE:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.ORE);
                hpanUpdateRsrcType = etype;
                break;

            case SOCPlayerElement.SHEEP:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.SHEEP);
                hpanUpdateRsrcType = etype;
                break;

            case SOCPlayerElement.WHEAT:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.WHEAT);
                hpanUpdateRsrcType = etype;
                break;

            case SOCPlayerElement.WOOD:
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.WOOD);
                hpanUpdateRsrcType = etype;
                break;

            case SOCPlayerElement.UNKNOWN:
                /**
                 * Note: if losing unknown resources, we first
                 * convert player's known resources to unknown resources,
                 * then remove mes's unknown resources from player.
                 */
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.UNKNOWN);
                hpan.updateValue(SOCHandPanel.NUMRESOURCES);
                break;

            case SOCPlayerElement.ASK_SPECIAL_BUILD:
                if (0 != mes.getValue())
                {
                    try {
                        ga.askSpecialBuild(pn, false);  // set per-player, per-game flags
                    }
                    catch (RuntimeException e) {}
                } else {
                    pl.setAskedSpecialBuild(false);
                }
                hpan.updateValue(etype);
                // for client player, hpan also refreshes BuildingPanel with this value.
                break;

            case SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES:
                pl.setNeedToPickGoldHexResources(mes.getValue());
                hpan.updateValue(etype);
                break;

            }

            if (hpanUpdateRsrcType != 0)
            {
                if (hpan.isClientPlayer())
                {
                    hpan.updateValue(hpanUpdateRsrcType);
                }
                else
                {
                    hpan.updateValue(SOCHandPanel.NUMRESOURCES);
                }                
            }

            if (hpan.isClientPlayer() && (ga.getGameState() != SOCGame.NEW))
            {
                pi.getBuildingPanel().updateButtonStatus();
            }
        }
    }

    /**
     * handle "resource count" message
     * @param mes  the message
     */
    protected void handleRESOURCECOUNT(SOCResourceCount mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayer pl = ga.getPlayer(mes.getPlayerNumber());
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());

            if (mes.getCount() != pl.getResources().getTotal())
            {
                SOCResourceSet rsrcs = pl.getResources();

                if (D.ebugOn)
                {
                    //pi.print(">>> RESOURCE COUNT ERROR: "+mes.getCount()+ " != "+rsrcs.getTotal());
                }

                //
                //  fix it
                //
                SOCHandPanel hpan = pi.getPlayerHandPanel(mes.getPlayerNumber());
                if (! hpan.isClientPlayer())
                {                     
                    rsrcs.clear();
                    rsrcs.setAmount(mes.getCount(), SOCResourceConstants.UNKNOWN);
                    hpan.updateValue(SOCHandPanel.NUMRESOURCES);
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
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            int roll = mes.getResult();
            ga.setCurrentDice(roll);
            pi.setTextDisplayRollExpected(roll);
            pi.getBoardPanel().repaint();

            // only notify about valid rolls
            if (roll >= 2 && roll <= 12)
                pi.getGameStats().diceRolled(new SOCGameStatistics.DiceRollEvent(roll, ga.getPlayer(ga.getCurrentPlayerNumber())));
        }
    }

    /**
     * handle the "put piece" message
     * @param mes  the message
     */
    protected void handlePUTPIECE(SOCPutPiece mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());
        if (ga == null)
            return;

        final SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        if (pi == null)
            return;

        pi.updateAtPutPiece
            (mes.getPlayerNumber(), mes.getCoordinates(), mes.getPieceType(), false, 0);
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
     */
    protected void handleCANCELBUILDREQUEST(SOCCancelBuildRequest mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());
        if (ga == null)
            return;

        int sta = ga.getGameState();
        if ((sta != SOCGame.START1B) && (sta != SOCGame.START2B))
        {
            // The human player gets a text message from the server informing
            // about the bad piece placement.  So, we can ignore this message type.
            return;
        }
        if (mes.getPieceType() != SOCPlayingPiece.SETTLEMENT)
            return;

        SOCPlayer pl = ga.getPlayer(ga.getCurrentPlayerNumber());
        SOCSettlement pp = new SOCSettlement(pl, pl.getLastSettlementCoord(), null);
        ga.undoPutInitSettlement(pp);

        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.getPlayerHandPanel(pl.getPlayerNumber()).updateResourcesVP();
        pi.getBoardPanel().updateMode();
    }

    /**
     * handle the "robber moved" or "pirate moved" message.
     * @param mes  the message
     */
    protected void handleMOVEROBBER(SOCMoveRobber mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());

            /**
             * Note: Don't call ga.moveRobber() because that will call the
             * functions to do the stealing.  We just want to say where
             * the robber moved without seeing if something was stolen.
             */
            final int newHex = mes.getCoordinates();
            if (newHex >= 0)
                ga.getBoard().setRobberHex(newHex, true);
            else
                ((SOCBoardLarge) ga.getBoard()).setPirateHex(-newHex, true);
            pi.getBoardPanel().repaint();
        }
    }

    /**
     * handle the "discard request" message
     * @param mes  the message
     */
    protected void handleDISCARDREQUEST(SOCDiscardRequest mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.showDiscardOrGainDialog(mes.getNumberOfDiscards(), true);
    }

    /**
     * handle the "pick resources request" message
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handlePICKRESOURCESREQUEST(SOCPickResourcesRequest mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.showDiscardOrGainDialog(mes.getParam(), false);
    }

    /**
     * handle the "choose player request" message
     * @param mes  the message
     */
    protected void handleCHOOSEPLAYERREQUEST(SOCChoosePlayerRequest mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        boolean[] ch = mes.getChoices();
        int[] choices = new int[ch.length];  // == SOCGame.maxPlayers
        int count = 0;

        for (int i = 0; i < ch.length; i++)
        {
            if (ch[i])
            {
                choices[count] = i;
                count++;
            }
        }

        pi.choosePlayer(count, choices);
    }

    /**
     * handle the "make offer" message
     * @param mes  the message
     */
    protected void handleMAKEOFFER(SOCMakeOffer mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            SOCTradeOffer offer = mes.getOffer();
            ga.getPlayer(offer.getFrom()).setCurrentOffer(offer);
            pi.getPlayerHandPanel(offer.getFrom()).updateCurrentOffer();
        }
    }

    /**
     * handle the "clear offer" message
     * @param mes  the message
     */
    protected void handleCLEAROFFER(SOCClearOffer mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            final int pn = mes.getPlayerNumber();
            if (pn != -1)
            {
                ga.getPlayer(pn).setCurrentOffer(null);
                pi.getPlayerHandPanel(pn).updateCurrentOffer();
            } else {
                for (int i = 0; i < ga.maxPlayers; ++i)
                {
                    ga.getPlayer(i).setCurrentOffer(null);
                    pi.getPlayerHandPanel(i).updateCurrentOffer();
                }
            }
        }
    }

    /**
     * handle the "reject offer" message
     * @param mes  the message
     */
    protected void handleREJECTOFFER(SOCRejectOffer mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.getPlayerHandPanel(mes.getPlayerNumber()).rejectOfferShowNonClient();
    }

    /**
     * handle the "clear trade message" message
     * @param mes  the message
     */
    protected void handleCLEARTRADEMSG(SOCClearTradeMsg mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.clearTradeMsg(mes.getPlayerNumber());
    }

    /**
     * handle the "number of development cards" message
     * @param mes  the message
     */
    protected void handleDEVCARDCOUNT(SOCDevCardCount mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            ga.setNumDevCards(mes.getNumDevCards());
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            if (pi != null)
                pi.updateDevCardCount();
        }
    }

    /**
     * handle the "development card action" message
     * @param mes  the message
     */
    protected void handleDEVCARD(final boolean isPractice, SOCDevCard mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            final int mesPN = mes.getPlayerNumber();
            SOCPlayer player = ga.getPlayer(mesPN);
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());

            int ctype = mes.getCardType();
            if ((! isPractice) && (sVersion < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
            {
                if (ctype == SOCDevCardConstants.KNIGHT_FOR_VERS_1_X)
                    ctype = SOCDevCardConstants.KNIGHT;
                else if (ctype == SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X)
                    ctype = SOCDevCardConstants.UNKNOWN;
            }

            switch (mes.getAction())
            {
            case SOCDevCard.DRAW:
                player.getDevCards().add(1, SOCDevCardSet.NEW, ctype);

                break;

            case SOCDevCard.PLAY:
                player.getDevCards().subtract(1, SOCDevCardSet.OLD, ctype);

                break;

            case SOCDevCard.ADDOLD:
                player.getDevCards().add(1, SOCDevCardSet.OLD, ctype);

                break;

            case SOCDevCard.ADDNEW:
                player.getDevCards().add(1, SOCDevCardSet.NEW, ctype);

                break;
            }

            SOCPlayer ourPlayerData = ga.getPlayer(nickname);
            if ((ourPlayerData != null) && (mesPN == ourPlayerData.getPlayerNumber()))
            {
                SOCHandPanel hp = pi.getClientHand();
                hp.updateDevCards();
                hp.updateValue(SOCHandPanel.VICTORYPOINTS);
            }
            else
            {
                pi.getPlayerHandPanel(mesPN).updateValue(SOCHandPanel.NUMDEVCARDS);
            }
        }
    }

    /**
     * handle the "set played dev card flag" message
     * @param mes  the message
     */
    protected void handleSETPLAYEDDEVCARD(SOCSetPlayedDevCard mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
            player.setPlayedDevCard(mes.hasPlayedDevCard());
        }
    }

    /**
     * handle the "list of potential settlements" message
     * @param mes  the message
     */
    protected void handlePOTENTIALSETTLEMENTS(SOCPotentialSettlements mes)
    {
        SOCDisplaylessPlayerClient.handlePOTENTIALSETTLEMENTS(mes, games);

        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        if (pi == null)
            return;
        pi.getBoardPanel().flushBoardLayoutAndRepaintIfDebugShowPotentials();
    }

    /**
     * handle the "change face" message
     * @param mes  the message
     */
    protected void handleCHANGEFACE(SOCChangeFace mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            player.setFaceId(mes.getFaceId());
            pi.changeFace(mes.getPlayerNumber(), mes.getFaceId());
        }
    }

    /**
     * handle the "reject connection" message
     * @param mes  the message
     */
    protected void handleREJECTCONNECTION(SOCRejectConnection mes)
    {
        disconnect();

        // In case was WAIT_CURSOR while connecting
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        
        if (ex_L == null)
        {
            messageLabel_top.setText(mes.getText());                
            messageLabel_top.setVisible(true);
            messageLabel.setText(NET_UNAVAIL_CAN_PRACTICE_MSG);
            pgm.setVisible(true);
        }
        else
        {
            messageLabel_top.setVisible(false);
            messageLabel.setText(mes.getText());
            pgm.setVisible(false);
        }
        cardLayout.show(this, MESSAGE_PANEL);
        validate();
        if (ex_L == null)
            pgm.requestFocus();
    }

    /**
     * handle the "longest road" message
     * @param mes  the message
     */
    protected void handleLONGESTROAD(SOCLongestRoad mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

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

            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());

            // Update player victory points; check for and announce change in longest road
            pi.updateLongestLargest(true, oldLongestRoadPlayer, newLongestRoadPlayer);
        }
    }

    /**
     * handle the "largest army" message
     * @param mes  the message
     */
    protected void handleLARGESTARMY(SOCLargestArmy mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

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

            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());

            // Update player victory points; check for and announce change in largest army
            pi.updateLongestLargest(false, oldLargestArmyPlayer, newLargestArmyPlayer);
        }
    }

    /**
     * handle the "set seat lock" message
     * @param mes  the message
     */
    protected void handleSETSEATLOCK(SOCSetSeatLock mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            if (mes.getLockState() == true)
            {
                ga.lockSeat(mes.getPlayerNumber());
            }
            else
            {
                ga.unlockSeat(mes.getPlayerNumber());
            }

            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());

            for (int i = 0; i < ga.maxPlayers; i++)
            {
                pi.getPlayerHandPanel(i).updateSeatLockButton();
                pi.getPlayerHandPanel(i).updateTakeOverButton();
            }
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
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        if (pi == null)
            return;  // Not one of our games        
        pi.updateAtRollPrompt();
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
        SOCGame ga = (SOCGame) games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gname);
        if (pi == null)
            return;  // Not one of our games

        SOCGame greset = ga.resetAsCopy();
        greset.isPractice = ga.isPractice;
        games.put(gname, greset);
        pi.resetBoard(greset, mes.getRejoinPlayerNumber(), mes.getRequestingPlayerNumber());
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
        SOCGame ga = (SOCGame) games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gname);
        if (pi == null)
            return;  // Not one of our games

        pi.resetBoardAskVote(mes.getRequestingPlayer());
    }

    /**
     * another player has voted on a board reset request: display the vote.
     *
     * @param mes  the message
     */
    protected void handleRESETBOARDVOTE(SOCResetBoardVote mes)
    {
        String gname = mes.getGame();
        SOCGame ga = (SOCGame) games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gname);
        if (pi == null)
            return;  // Not one of our games

        pi.resetBoardVoted(mes.getPlayerNumber(), mes.getPlayerVote());
    }

    /**
     * voting complete, board reset request rejected
     *
     * @param mes  the message
     */
    protected void handleRESETBOARDREJECT(SOCResetBoardReject mes)
    {
        String gname = mes.getGame();
        SOCGame ga = (SOCGame) games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gname);
        if (pi == null)
            return;  // Not one of our games

        pi.resetBoardRejected();
    }

    /**
     * process the "game option get defaults" message.
     * If any default option's keyname is unknown, ask the server.
     * @see GameOptionServerSet
     * @since 1.1.07
     */
    private void handleGAMEOPTIONGETDEFAULTS(SOCGameOptionGetDefaults mes, final boolean isPractice)
    {
        GameOptionServerSet opts;
        if (isPractice)
            opts = practiceServGameOpts;
        else
            opts = tcpServGameOpts;

        Vector unknowns;
        synchronized(opts)
        {
            // receiveDefaults sets opts.defaultsReceived, may set opts.allOptionsReceived
            unknowns = opts.receiveDefaults
                (SOCGameOption.parseOptionsToHash((mes.getOpts())));
        }

        if (unknowns != null)
        {
            if (! isPractice)
                gameOptionsSetTimeoutTask();
            put(SOCGameOptionGetInfos.toCmd(unknowns.elements()), isPractice);
        } else {
            opts.newGameWaitingForOpts = false;
            if (gameOptsDefsTask != null)
            {
                gameOptsDefsTask.cancel();
                gameOptsDefsTask = null;
            }
            newGameOptsFrame = NewGameOptionsFrame.createAndShow
                (this, (String) null, opts.optionSet, isPractice, false);
        }
    }

    /**
     * process the "game option info" message
     * by calling {@link GameOptionServerSet#receiveInfo(SOCGameOptionInfo)}.
     * If all are now received, possibly show options window for new game or existing game.
     *<P>
     * For a summary of the flags and variables involved with game options,
     * and the client/server interaction about their values, see
     * {@link GameOptionServerSet}.
     *
     * @since 1.1.07
     */
    private void handleGAMEOPTIONINFO(SOCGameOptionInfo mes, final boolean isPractice)
    {
        GameOptionServerSet opts;
        if (isPractice)
            opts = practiceServGameOpts;
        else
            opts = tcpServGameOpts;

        boolean hasAllNow, newGameWaiting;
        String gameInfoWaiting;
        synchronized(opts)
        {
            hasAllNow = opts.receiveInfo(mes);
            newGameWaiting = opts.newGameWaitingForOpts;
            gameInfoWaiting = opts.gameInfoWaitingForOpts;
        }

        if ((! isPractice) && mes.getOptionNameKey().equals("-"))
            gameOptionsCancelTimeoutTask();

        if (hasAllNow)
        {
            if (gameInfoWaiting != null)
            {
                Hashtable gameOpts = serverGames.parseGameOptions(gameInfoWaiting);
                newGameOptsFrame = NewGameOptionsFrame.createAndShow
                    (this, gameInfoWaiting, gameOpts, isPractice, true);
            } else if (newGameWaiting)
            {
                newGameOptsFrame = NewGameOptionsFrame.createAndShow
                    (this, (String) null, opts.optionSet, isPractice, false);
            }
        }
    }

    /**
     * process the "new game with options" message
     * @since 1.1.07
     */
    private void handleNEWGAMEWITHOPTIONS(SOCNewGameWithOptions mes, final boolean isPractice)
    {
        String gname = mes.getGame();
        String opts = mes.getOptionsString();
        boolean canJoin = (mes.getMinVersion() <= Version.versionNumber());
        if (gname.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE)
        {
            gname = gname.substring(1);
            canJoin = false;
        }
        addToGameList(! canJoin, gname, opts, ! isPractice);
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

        Enumeration gamesEnum = msgGames.getGames();
        while (gamesEnum.hasMoreElements())
        {
            String gaName = (String) gamesEnum.nextElement();
            addToGameList
                (msgGames.isUnjoinableGame(gaName), gaName,
                 msgGames.getGameOptionsString(gaName), false);
        }
    }

    /**
     * handle the "player stats" message
     * @since 1.1.09
     */
    private void handlePLAYERSTATS(SOCPlayerStats mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        if (pi == null)
            return;  // Not one of our games

        final int stype = mes.getStatType();
        if (stype != SOCPlayerStats.STYPE_RES_ROLL)
            return;  // not recognized in this version

        final int[] rstat = mes.getParams();

        pi.print("* Your resource rolls: (Clay, Ore, Sheep, Wheat, Wood)");
        StringBuffer sb = new StringBuffer("* ");
        int total = 0;
        for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype)
        {
            total += rstat[rtype];
            if (rtype > 1)
                sb.append(", ");
            sb.append(rstat[rtype]);
        }
        sb.append(". Total: ");
        sb.append(total);
        pi.print(sb.toString());
    }

    /**
     * Handle the server's debug piece placement on/off message.
     * @since 1.1.12
     */
    private final void handleDEBUGFREEPLACE(SOCDebugFreePlace mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        if (pi == null)
            return;  // Not one of our games

        pi.setDebugFreePlacementMode(mes.getCoordinates() == 1);
    }

    /**
     * Handle moving a piece (a ship) around on the board.
     * @since 2.0.00
     */
    private final void handleMOVEPIECE(SOCMovePiece mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = (SOCGame) games.get(gaName);
        if (ga == null)
            return;  // Not one of our games

        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gaName);
        if (pi == null)
            return;  // Not one of our games

        pi.updateAtPutPiece
            (mes.getPlayerNumber(), mes.getFromCoord(), mes.getPieceType(),
             true, mes.getToCoord());
    }

    /**
     * add a new game to the initial window's list of games, and possibly
     * to the {@link #serverGames server games list}.
     *
     * @param gameName the game name to add to the list;
     *                 may have the prefix {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}
     * @param gameOptsStr String of packed {@link SOCGameOption game options}, or null
     * @param addToSrvList Should this game be added to the list of remote-server games?
     *                 Local practice games should not be added.
     *                 The {@link #serverGames} list also has a flag for cannotJoin.
     */
    public void addToGameList(String gameName, String gameOptsStr, final boolean addToSrvList)
    {
        boolean hasUnjoinMarker = (gameName.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE);
        if (hasUnjoinMarker)
        {
            gameName = gameName.substring(1);
        }
        addToGameList(hasUnjoinMarker, gameName, gameOptsStr, addToSrvList);
    }

    /**
     * add a new game to the initial window's list of games.
     * If client can't join, also add to {@link #serverGames} as an unjoinable game.
     *
     * @param cannotJoin Can we not join this game?
     * @param gameName the game name to add to the list;
     *                 must not have the prefix {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @param gameOptsStr String of packed {@link SOCGameOption game options}, or null
     * @param addToSrvList Should this game be added to the list of remote-server games?
     *                 Local practice games should not be added.
     */
    public void addToGameList(final boolean cannotJoin, String gameName, String gameOptsStr, final boolean addToSrvList)
    {
        if (addToSrvList)
        {
            if (serverGames == null)
                serverGames = new SOCGameList();
            serverGames.addGame(gameName, gameOptsStr, cannotJoin);
        }

        if (cannotJoin)
        {
            // for display:
            // "(cannot join) "     TODO color would be nice
            gameName = GAMENAME_PREFIX_CANNOT_JOIN + gameName;
        }

        // String gameName = thing + STATSPREFEX + "-- -- -- --]";

        if ((gmlist.countItems() > 0) && (gmlist.getItem(0).equals(" ")))
        {
            gmlist.replaceItem(gameName, 0);
            gmlist.select(0);
            jg.setEnabled(true);
            so.setEnabled((practiceServer != null)
                || (sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS));
        }
        else
        {
            gmlist.add(gameName, 0);
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
     * Update this game's stats in the game list display.
     *
     * @param gameName Name of game to update
     * @param scores Each player position's score
     * @param robots Is this position a robot?
     * 
     * @see soc.message.SOCGameStats
     */
    public void updateGameStats(String gameName, int[] scores, boolean[] robots)
    {
        //D.ebugPrintln("UPDATE GAME STATS FOR "+gameName);
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
    
    /** If we're playing in a game that's just finished, update the scores.
     *  This is used to show the true scores, including hidden
     *  victory-point cards, at the game's end.
     */
    public void updateGameEndStats(String game, int[] scores)
    {
        SOCGame ga = (SOCGame) games.get(game);
        if (ga == null)
            return;  // Not playing in that game
        if (ga.getGameState() != SOCGame.OVER)
        {
            System.err.println("L4044: pcli.updateGameEndStats called at state " + ga.getGameState());
            return;  // Should not have been sent; game is not yet over.
        }

        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(game);
        pi.updateAtOver(scores);
    }

    /**
     * delete a game from the list.
     * If it's on the list, also remove from {@link #serverGames}.
     *
     * @param gameName  the game to remove
     * @param isPractice   local practice, not at remote server?
     * @return true if deleted, false if not found in list
     */
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

                if ((! isPractice) && (serverGames != null)) 
                {
                    serverGames.deleteGame(gameName);  // may not be in there
                }
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

        if (found && (! isPractice) && (serverGames != null))
        {
            serverGames.deleteGame(gameName);  // may not be in there
        }

        return found;
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

    /**
     * send a text message to a channel
     *
     * @param ch   the name of the channel
     * @param mes  the message
     */
    public void chSend(String ch, String mes)
    {
        if (!doLocalCommand(ch, mes))
        {
            putNet(SOCTextMsg.toCmd(ch, nickname, mes));
        }
    }

    /**
     * the user leaves the given channel
     *
     * @param ch  the name of the channel
     */
    public void leaveChannel(String ch)
    {
        channels.remove(ch);
        putNet(SOCLeave.toCmd(nickname, host, ch));
    }

    /**
     * disconnect from the net
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
     * send a text message to the people in the game
     *
     * @param ga   the game
     * @param me   the message
     */
    public void sendText(SOCGame ga, String me)
    {
        if (!doLocalCommand(ga, me))
        {
            put(SOCGameTextMsg.toCmd(ga.getName(), nickname, me), ga.isPractice);
        }
    }

    /**
     * the user leaves the given game
     *
     * @param ga   the game
     */
    public void leaveGame(SOCGame ga)
    {
        playerInterfaces.remove(ga.getName());
        games.remove(ga.getName());
        put(SOCLeaveGame.toCmd(nickname, host, ga.getName()), ga.isPractice);
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
     *
     * @param ga  the game
     * @param rs  The resources to pick
     * @since 2.0.00
     */
    public void pickResources(SOCGame ga, SOCResourceSet rs)
    {
        put(SOCPickResources.toCmd(ga.getName(), rs), ga.isPractice);
    }

    /**
     * The user chose a player to steal from,
     * or (game state {@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE})
     * chose whether to move the robber or the pirate.
     *
     * @param ga  the game
     * @param pn  the player id,
     *   or -1 to move the robber
     *   or -2 to move the pirate ship
     */
    public void choosePlayer(SOCGame ga, int pn)
    {
        put(SOCChoosePlayer.toCmd(ga.getName(), pn), ga.isPractice);
    }

    /**
     * the user is rejecting the current offers
     *
     * @param ga  the game
     */
    public void rejectOffer(SOCGame ga)
    {
        put(SOCRejectOffer.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber()), ga.isPractice);
    }

    /**
     * the user is accepting an offer
     *
     * @param ga  the game
     * @param from the number of the player that is making the offer
     */
    public void acceptOffer(SOCGame ga, int from)
    {
        put(SOCAcceptOffer.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber(), from), ga.isPractice);
    }

    /**
     * the user is clearing an offer
     *
     * @param ga  the game
     */
    public void clearOffer(SOCGame ga)
    {
        put(SOCClearOffer.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber()), ga.isPractice);
    }

    /**
     * the user wants to trade with the bank
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
     * the user is making an offer to trade
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
        if ((! ga.isPractice) && (sVersion < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
        {
            if (dc == SOCDevCardConstants.KNIGHT)
                dc = SOCDevCardConstants.KNIGHT_FOR_VERS_1_X;
            else if (dc == SOCDevCardConstants.UNKNOWN)
                dc = SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X;
        }
        put(SOCPlayDevCardRequest.toCmd(ga.getName(), dc), ga.isPractice);
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
        lastFaceChange = id;
        put(SOCChangeFace.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber(), id), ga.isPractice);
    }

    /**
     * the user is locking a seat
     *
     * @param ga  the game
     * @param pn  the seat number
     * @param lock  Lock the seat, or unlock?
     */
    public void lockSeat(SOCGame ga, int pn, final boolean lock)
    {
        put(SOCSetSeatLock.toCmd(ga.getName(), pn, lock), ga.isPractice);
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
     * Handle local client commands for channels.
     *
     * @param cmd  Local client command string, such as \ignore or \&shy;unignore
     * @return true if a command was handled
     */
    public boolean doLocalCommand(String ch, String cmd)
    {
        ChannelFrame fr = (ChannelFrame) channels.get(ch);

        if (cmd.startsWith("\\ignore "))
        {
            String name = cmd.substring(8);
            addToIgnoreList(name);
            fr.print("* Ignoring " + name);
            printIgnoreList(fr);

            return true;
        }
        else if (cmd.startsWith("\\unignore "))
        {
            String name = cmd.substring(10);
            removeFromIgnoreList(name);
            fr.print("* Unignoring " + name);
            printIgnoreList(fr);

            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Handle local client commands for games.
     *
     * @param cmd  Local client command string, which starts with \
     * @return true if a command was handled
     */
    public boolean doLocalCommand(SOCGame ga, String cmd)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(ga.getName());

        if (cmd.startsWith("\\ignore "))
        {
            String name = cmd.substring(8);
            addToIgnoreList(name);
            pi.print("* Ignoring " + name);
            printIgnoreList(pi);

            return true;
        }
        else if (cmd.startsWith("\\unignore "))
        {
            String name = cmd.substring(10);
            removeFromIgnoreList(name);
            pi.print("* Unignoring " + name);
            printIgnoreList(pi);

            return true;
        }
        else if (cmd.startsWith("\\clm-set "))
        {
            String name = cmd.substring(9).trim();
            pi.getBoardPanel().setOtherPlayer(ga.getPlayer(name));
            pi.getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LM_SETTLEMENT);

            return true;
        }
        else if (cmd.startsWith("\\clm-road "))
        {
            String name = cmd.substring(10).trim();
            pi.getBoardPanel().setOtherPlayer(ga.getPlayer(name));
            pi.getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LM_ROAD);

            return true;
        }
        else if (cmd.startsWith("\\clm-city "))
        {
            String name = cmd.substring(10).trim();
            pi.getBoardPanel().setOtherPlayer(ga.getPlayer(name));
            pi.getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LM_CITY);

            return true;
        }
        else if (cmd.startsWith("\\clt-set "))
        {
            String name = cmd.substring(9).trim();
            pi.getBoardPanel().setOtherPlayer(ga.getPlayer(name));
            pi.getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LT_SETTLEMENT);

            return true;
        }
        else if (cmd.startsWith("\\clt-road "))
        {
            String name = cmd.substring(10).trim();
            pi.getBoardPanel().setOtherPlayer(ga.getPlayer(name));
            pi.getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LT_ROAD);

            return true;
        }
        else if (cmd.startsWith("\\clt-city "))
        {
            String name = cmd.substring(10).trim();
            pi.getBoardPanel().setOtherPlayer(ga.getPlayer(name));
            pi.getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LT_CITY);

            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * @return true if name is on the ignore list
     */
    protected boolean onIgnoreList(String name)
    {
        boolean result = false;
        Enumeration ienum = ignoreList.elements();

        while (ienum.hasMoreElements())
        {
            String s = (String) ienum.nextElement();
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

    /** Print the current chat ignorelist in a channel. */
    protected void printIgnoreList(ChannelFrame fr)
    {
        fr.print("* Ignore list:");

        Enumeration ienum = ignoreList.elements();

        while (ienum.hasMoreElements())
        {
            String s = (String) ienum.nextElement();
            fr.print("* " + s);
        }
    }

    /** Print the current chat ignorelist in a playerinterface. */
    protected void printIgnoreList(SOCPlayerInterface pi)
    {
        pi.print("* Ignore list:");

        Enumeration ienum = ignoreList.elements();

        while (ienum.hasMoreElements())
        {
            String s = (String) ienum.nextElement();
            pi.print("* " + s);
        }        
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
        put(SOCGameTextMsg.toCmd(ga.getName(), nickname, msg), ga.isPractice);
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
        put(SOCGameTextMsg.toCmd(ga.getName(), nickname, msg), ga.isPractice);
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
        gameOptsTask = new GameOptionsTimeoutTask(this, tcpServGameOpts);
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

    /**
     * Create a game name, and start a practice game.
     * Assumes {@link #MAIN_PANEL} is initialized.
     */
    public void startPracticeGame()
    {
        startPracticeGame(null, null, true);
    }

    /**
     * Setup for local practice game (local non-tcp server).
     * If needed, a (stringport, not tcp) server, client, and robots are started.
     *
     * @param practiceGameName Unique name to give practice game; if name unknown, call
     *         {@link #startPracticeGame()} instead
     * @param gameOpts Set of {@link SOCGameOption game options} to use, or null
     * @param mainPanelIsActive Is the SOCPlayerClient main panel active?
     *         False if we're being called from elsewhere, such as
     *         {@link SOCConnectOrPracticePanel}.
     */
    public void startPracticeGame(String practiceGameName, Hashtable gameOpts, boolean mainPanelIsActive)
    {
        ++numPracticeGames;

        if (practiceGameName == null)
            practiceGameName = DEFAULT_PRACTICE_GAMENAME + " " + (numPracticeGames);

        // May take a while to start server & game.
        // The new-game window will clear this cursor.
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        if (practiceServer == null)
        {
            practiceServer = new SOCServer(SOCServer.PRACTICE_STRINGPORT, 30, null, null);
            practiceServer.setPriority(5);  // same as in SOCServer.main
            practiceServer.start();

            // We need some opponents.
            // Let the server randomize whether we get smart or fast ones.
            practiceServer.setupLocalRobots(5, 2);
        }
        if (prCli == null)
        {
            try
            {
                prCli = LocalStringServerSocket.connectTo(SOCServer.PRACTICE_STRINGPORT);
                new SOCPlayerLocalStringReader((LocalStringConnection) prCli);
                // Reader will start its own thread.
                // Send VERSION right away (1.1.06 and later)
                putPractice(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum()));

                // local server will support per-game options
                if (so != null)
                    so.setEnabled(true);
            }
            catch (ConnectException e)
            {
                ex_L = e;
                return;
            }
        }

        // Ask local "server" to create the game
        if (gameOpts == null)
            putPractice(SOCJoinGame.toCmd(nickname, password, host, practiceGameName));
        else
            putPractice(SOCNewGameWithOptionsRequest.toCmd(nickname, password, host, practiceGameName, gameOpts));
    }

    /**
     * Setup for locally hosting a TCP server.
     * If needed, a local server and robots are started, and client connects to it.
     * If parent is a Frame, set titlebar to show "server" and port#.
     * Show port number in {@link #versionOrlocalTCPPortLabel}. 
     * If the {@link #localTCPServer} is already created, does nothing.
     * If {@link #connected} already, does nothing.
     *
     * @param tport Port number to host on; must be greater than zero.
     * @throws IllegalArgumentException If port is 0 or negative
     */
    public void startLocalTCPServer(int tport)
        throws IllegalArgumentException
    {
        if (localTCPServer != null)
        {
            return;  // Already set up
        }
        if (connected)
        {
            return;  // Already connected somewhere
        }
        if (tport < 1)
        {
            throw new IllegalArgumentException("Port must be positive: " + tport);
        }

        // May take a while to start server.
        // At end of method, we'll clear this cursor.
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        localTCPServer = new SOCServer(tport, 30, null, null);
        localTCPServer.setPriority(5);  // same as in SOCServer.main
        localTCPServer.start();

        // We need some opponents.
        // Let the server randomize whether we get smart or fast ones.
        localTCPServer.setupLocalRobots(5, 2);

        // Set label
        localTCPServerLabel.setText("Server is Running. (Click for info)");
        localTCPServerLabel.setFont(getFont().deriveFont(Font.BOLD));
        localTCPServerLabel.addMouseListener(this);
        versionOrlocalTCPPortLabel.setText("Port: " + tport);
        new AWTToolTip ("You are running a server on TCP port " + tport
            + ". Version " + Version.version()
            + " bld " + Version.buildnum(),
            versionOrlocalTCPPortLabel);
        versionOrlocalTCPPortLabel.addMouseListener(this);

        // Set titlebar, if present
        {
            Container parent = this.getParent();
            if ((parent != null) && (parent instanceof Frame))
            {
                try
                {
                    ((Frame) parent).setTitle("JSettlers server " + Version.version()
                        + " - port " + tport);
                } catch (Throwable t)
                {}
            }
        }
        
        // Connect to it
        host = "localhost";
        port = tport;
        cardLayout.show(this, MESSAGE_PANEL);
        connect();

        // Ensure we can't "connect" to another, too
        if (connectOrPracticePane != null)
        {
            connectOrPracticePane.startedLocalServer();
        }

        // Reset the cursor
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    /**
     * Server version, for checking feature availability.
     * Returns -1 if unknown.
     * @param  game  Game being played on a local (practice) or remote server.
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
     * applet info, of the form similar to that seen at server startup:
     * SOCPlayerClient (Java Settlers Client) 1.1.07, build JM20091027, 2001-2004 Robb Thomas, portions 2007-2009 Jeremy D Monin.
     * Version and copyright info is from the {@link Version} utility class.
     */
    public String getAppletInfo()
    {
        return "SOCPlayerClient (Java Settlers Client) " + Version.version() +
        ", build " + Version.buildnum() + ", " + Version.copyright();
    }

    /**
     * network trouble; if possible, ask if they want to play locally (robots).
     * Otherwise, go ahead and destroy the applet.
     */
    public void destroy()
    {
        boolean canLocal;  // Can we still start a local game?
        canLocal = putLeaveAll();

        String err;
        if (canLocal)
        {
            err = "Sorry, network trouble has occurred. ";
        } else {
            err = "Sorry, the applet has been destroyed. ";
        }
        err = err + ((ex == null) ? "Load the page again." : ex.toString());

        for (Enumeration e = channels.elements(); e.hasMoreElements();)
        {
            ((ChannelFrame) e.nextElement()).over(err);
        }

        for (Enumeration e = playerInterfaces.elements(); e.hasMoreElements();)
        {
            // Stop network games.
            // Local practice games can continue.

            SOCPlayerInterface pi = ((SOCPlayerInterface) e.nextElement());
            if (! (canLocal && pi.getGame().isPractice))
            {
                pi.over(err);
            }
        }
        
        disconnect();

        // In case was WAIT_CURSOR while connecting
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        if (canLocal)
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
        cardLayout.show(this, MESSAGE_PANEL);
        validate();
        if (canLocal)
        {
            if (null == findAnyActiveGame(true))
                pgm.requestFocus();  // No practice games: put this msg as topmost window
            else
                pgm.requestFocusInWindow();  // Practice game is active; don't interrupt to show this
        }
    }

    /**
     * For shutdown - Tell the server we're leaving all games.
     * If we've started a local practice server, also tell that server.
     * If we've started a TCP server, tell all players on that server, and shut it down.
     *<P><em>
     * Since no other state variables are set, call this only right before
     * discarding this object or calling System.exit.
     *</em>
     * @return Can we still start local games? (No local exception yet in {@link #ex_L})
     */
    public boolean putLeaveAll()
    {
        boolean canLocal = (ex_L == null);  // Can we still start a local game? 

        SOCLeaveAll leaveAllMes = new SOCLeaveAll();
        putNet(leaveAllMes.toCmd());
        if ((prCli != null) && ! canLocal)
            putPractice(leaveAllMes.toCmd());
        if ((localTCPServer != null) && (localTCPServer.isUp()))
        {
            localTCPServer.stopServer();
            localTCPServer = null;
        }

        return canLocal;
    }

    /**
     * for stand-alones
     */
    public static void usage()
    {
        System.err.println("usage: java soc.client.SOCPlayerClient <host> <port>");
    }

    /**
     * for stand-alones
     */
    public static void main(String[] args)
    {
        SOCPlayerClient client;
        boolean withConnectOrPractice;

        if (args.length == 0)
        {
            withConnectOrPractice = true;
            client = new SOCPlayerClient(withConnectOrPractice);
        }
        else
        {
            if (args.length != 2)
            {
                usage();
                System.exit(1);
            }

            withConnectOrPractice = false;
            client = new SOCPlayerClient(withConnectOrPractice);

            try {
                client.host = args[0];
                client.port = Integer.parseInt(args[1]);
            } catch (NumberFormatException x) {
                usage();
                System.err.println("Invalid port: " + args[1]);
                System.exit(1);
            }
        }

        System.out.println("Java Settlers Client " + Version.version() +
                ", build " + Version.buildnum() + ", " + Version.copyright());
        System.out.println("Network layer based on code by Cristian Bogdan; local network by Jeremy Monin.");

        Frame frame = new Frame("JSettlers client " + Version.version());
        frame.setBackground(new Color(Integer.parseInt("61AF71",16)));
        frame.setForeground(Color.black);
        // Add a listener for the close event
        frame.addWindowListener(client.createWindowAdapter());
        
        client.initVisualElements(); // after the background is set
        
        frame.add(client, BorderLayout.CENTER);
        frame.setSize(620, 400);
        frame.setVisible(true);

        if (! withConnectOrPractice)
            client.connect();
    }

    /**
     * When the local-server info label is clicked,
     * show a popup with more info.
     * @since 1.1.12
     */
    public void mouseClicked(MouseEvent e)
    {
        NotifyDialog.createAndShow
            (this, null,
             "For other players to connect to your server,\nthey need only your IP address and port number.\nNo other server software install is needed.\nMake sure your firewall allows inbound traffic on port " + localTCPServer.getPort() + ".",
             "OK", true);
    }

    /**
     * Set the hand cursor when entering the local-server info label.
     * @since 1.1.12
     */
    public void mouseEntered(MouseEvent e)
    {
        if (e.getSource() == localTCPServerLabel)
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Clear the cursor when exiting the local-server info label.
     * @since 1.1.12
     */
    public void mouseExited(MouseEvent e)
    {
        if (e.getSource() == localTCPServerLabel)
            setCursor(Cursor.getDefaultCursor());
    }

    /** required stub for {@link MouseListener} */
    public void mousePressed(MouseEvent e) {}

    /** required stub for {@link MouseListener} */
    public void mouseReleased(MouseEvent e) {}

    private WindowAdapter createWindowAdapter()
    {
        return new MyWindowAdapter(this);
    }

    /** React to windowOpened, windowClosing events for SOCPlayerClient's Frame. */
    private static class MyWindowAdapter extends WindowAdapter
    {
        private final SOCPlayerClient cli;

        public MyWindowAdapter(SOCPlayerClient c)
        {
            cli = c;
        }

        /**
         * User has clicked window Close button.
         * Check for active games, before exiting.
         * If we are playing in a game, or running a local server hosting active games,
         * ask the user to confirm if possible.
         */
        public void windowClosing(WindowEvent evt)
        {
            SOCPlayerInterface piActive = null;

            // Are we a client to any active games?
            if (piActive == null)
                piActive = cli.findAnyActiveGame(false);

            if (piActive != null)
                SOCQuitAllConfirmDialog.createAndShow(piActive.getClient(), piActive);
            else
            {
                boolean canAskHostingGames = false;
                boolean isHostingActiveGames = false;

                // Are we running a server?
                if (cli.localTCPServer != null)
                    isHostingActiveGames = cli.anyHostedActiveGames();

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
                    cli.putLeaveAll();
                    System.exit(0);
                }
            }
        }

        /**
         * Set focus to Nickname field
         */
        public void windowOpened(WindowEvent evt)
        {
            if (! cli.hasConnectOrPractice)
                cli.nick.requestFocus();
        }
    }

    /**
     * For local practice games, reader thread to get messages from the
     * local server to be treated and reacted to.
     */
    protected class SOCPlayerLocalStringReader implements Runnable
    {
        LocalStringConnection locl;

        /** 
         * Start a new thread and listen to local server.
         *
         * @param localConn Active connection to local server
         */
        protected SOCPlayerLocalStringReader (LocalStringConnection localConn)
        {
            locl = localConn;

            Thread thr = new Thread(this);
            thr.setDaemon(true);
            thr.start();
        }

        /**
         * continuously read from the local string server in a separate thread
         */
        public void run()
        {
            Thread.currentThread().setName("cli-stringread");  // Thread name for debug
            try
            {
                while (locl.isConnected())
                {
                    String s = locl.readNext();
                    treat((SOCMessage) SOCMessage.toMsg(s), true);
                }
            }
            catch (IOException e)
            {
                // purposefully closing the socket brings us here too
                if (locl.isConnected())
                {
                    ex_L = e;
                    System.out.println("could not read from string localnet: " + ex_L);
                    destroy();
                }
            }
        }
    }



    /**
     * TimerTask used soon after client connect, to prevent waiting forever for
     * {@link SOCGameOptionInfo game options info}
     * (assume slow connection or server bug).
     * Set up when sending {@link SOCGameOptionGetInfos GAMEOPTIONGETINFOS}.
     *<P>
     * When timer fires, assume no more options will be received.
     * Call {@link SOCPlayerClient#handleGAMEOPTIONINFO(SOCGameOptionInfo, boolean) handleGAMEOPTIONINFO("-",false)}
     * to trigger end-of-list behavior at client.
     * @since 1.1.07
     */
    private static class GameOptionsTimeoutTask extends TimerTask
    {
        public SOCPlayerClient pcli;
        public GameOptionServerSet srvOpts;

        public GameOptionsTimeoutTask (SOCPlayerClient c, GameOptionServerSet opts)
        {
            pcli = c;
            srvOpts = opts;
        }

        /**
         * Called when timer fires. See class description for action taken.
         */
        public void run()
        {
            pcli.gameOptsTask = null;  // Clear reference to this soon-to-expire obj
            srvOpts.noMoreOptions(false);
            pcli.handleGAMEOPTIONINFO(new SOCGameOptionInfo(new SOCGameOption("-")), false);
        }

    }  // GameOptionsTimeoutTask


    /**
     * TimerTask used when new game is asked for, to prevent waiting forever for
     * {@link SOCGameOption game option defaults}.
     * (in case of slow connection or server bug).
     * Set up when sending {@link SOCGameOptionGetDefaults GAMEOPTIONGETDEFAULTS}
     * in {@link SOCPlayerClient#gameWithOptionsBeginSetup(boolean)}.
     *<P>
     * When timer fires, assume no defaults will be received.
     * Display the new-game dialog.
     * @since 1.1.07
     */
    private static class GameOptionDefaultsTimeoutTask extends TimerTask
    {
        public SOCPlayerClient pcli;
        public GameOptionServerSet srvOpts;
        public boolean forPracticeServer;

        public GameOptionDefaultsTimeoutTask (SOCPlayerClient c, GameOptionServerSet opts, boolean forPractice)
        {
            pcli = c;
            srvOpts = opts;
            forPracticeServer = forPractice;
        }

        /**
         * Called when timer fires. See class description for action taken.
         */
        public void run()
        {
            pcli.gameOptsDefsTask = null;  // Clear reference to this soon-to-expire obj
            srvOpts.noMoreOptions(true);
            if (srvOpts.newGameWaitingForOpts)
                pcli.gameWithOptionsBeginSetup(forPracticeServer);
        }

    }  // GameOptionDefaultsTimeoutTask


    /**
     * Track the server's valid game option set.
     * One instance for remote tcp server, one for practice server.
     * Not doing getters/setters - Synchronize on the object to set/read its fields.
     *<P>
     * Interaction with client-server messages at connect:
     *<OL>
     *<LI> First, this object is created; <tt>allOptionsReceived</tt> false,
     *     <tt>newGameWaitingForOpts</tt> false.
     *     <tt>optionSet</tt> is set at client from {@link SOCGameOption#getAllKnownOptions()}.
     *<LI> At server connect, ask and receive info about options, if our version and the
     *     server's version differ.  Once this is done, <tt>allOptionsReceived</tt> == true.
     *<LI> When user wants to create a new game, <tt>askedDefaultsAlready</tt> is false;
     *     ask server for its defaults (current option values for any new game).
     *     Also set <tt>newGameWaitingForOpts</tt> = true.
     *<LI> Server will respond with its current option values.  This sets
     *     <tt>defaultsReceived</tt> and updates <tt>optionSet</tt>.
     *     It's possible that the server's defaults contain option names that are
     *     unknown at our version.  If so, <tt>allOptionsReceived</tt> is cleared, and we ask the
     *     server about those specific options.
     *     Otherwise, clear <tt>newGameWaitingForOpts</tt>.
     *<LI> If waiting on option info from defaults above, the server replies with option info.
     *     (They may remain as type {@link SOCGameOption#OTYPE_UNKNOWN}.)
     *     Once these are all received, set <tt>allOptionsReceived</tt> = true,
     *     clear <tt>newGameWaitingForOpts</tt>.
     *<LI> Once  <tt>newGameWaitingForOpts</tt> == false, show the {@link NewGameOptionsFrame}.
     *</OL>
     *
     * @since 1.1.07
     */
    public static class GameOptionServerSet
    {
        /**
         * If true, we know all options on this server,
         * or the server is too old to support options.
         */
        public boolean   allOptionsReceived = false;

        /**
         * If true, we've asked the server about defaults or options because
         * we're about to create a new game.  When all are received,
         * we should create and show a NewGameOptionsFrame.
         */
        public boolean   newGameWaitingForOpts = false;

        /**
         * If non-null, we're waiting to hear about options because
         * user has clicked 'show options' on a game.  When all are
         * received, we should create and show a NewGameOptionsFrame
         * with that game's options.
         */
        public String    gameInfoWaitingForOpts = null;

        /**
         * Options will be null if {@link SOCPlayerClient#sVersion}
         * is less than {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
         * Otherwise, set from {@link SOCGameOption#getAllKnownOptions()}
         * and update from server as needed.
         */
        public Hashtable optionSet = null;

        /** Have we asked the server for default values? */
        public boolean   askedDefaultsAlready = false;

        /** Has the server told us defaults? */
        public boolean   defaultsReceived = false;

        /**
         * If {@link #askedDefaultsAlready}, the time it was asked,
         * as returned by {@link System#currentTimeMillis()}.
         */
        public long askedDefaultsTime;

        public GameOptionServerSet()
        {
            optionSet = SOCGameOption.getAllKnownOptions();
        }

        /**
         * The server doesn't have any more options to send (or none at all, from its version).
         * Set fields as if we've already received the complete set of options, and aren't waiting
         * for any more.
         * @param askedDefaults Should we also set the askedDefaultsAlready flag? It not, leave it unchanged.
         */
        public void noMoreOptions(boolean askedDefaults)
        {
            allOptionsReceived = true;
            if (askedDefaults)
            {
                defaultsReceived = true;
                askedDefaultsAlready = true;
                askedDefaultsTime = System.currentTimeMillis();
            }
        }

        /**
         * Set of default options has been received from the server, examine them.
         * Sets allOptionsReceived, defaultsReceived, optionSet.  If we already have non-null optionSet,
         * merge (update the values) instead of replacing the entire set with servOpts.
         *
         * @param servOpts The allowable {@link SOCGameOption} received from the server.
         *                 Assumes has been parsed already against the locally known opts,
         *                 so ones that we don't know are {@link SOCGameOption#OTYPE_UNKNOWN}.
         * @return null if all are known, or a Vector of key names for unknown options.
         */
        public Vector receiveDefaults(Hashtable servOpts)
        {
            // Although javadoc says "update the values", replacing the option objects does the
            // same thing; we already have parsed servOpts for all obj fields, including current value.
            // Option objects are always accessed by key name, so replacement is OK.

            if ((optionSet == null) || optionSet.isEmpty())
            {
                optionSet = servOpts;
            } else {
                for (Enumeration e = servOpts.keys(); e.hasMoreElements(); )
                {
                    final String oKey = (String) e.nextElement();
                    SOCGameOption op = (SOCGameOption) servOpts.get(oKey);
                    SOCGameOption oldcopy = (SOCGameOption) optionSet.get(oKey);
                    if (oldcopy != null)
                        optionSet.remove(oKey);
                    optionSet.put(oKey, op);  // Even OTYPE_UNKNOWN are added
                }
            }
            Vector unknowns = SOCGameOption.findUnknowns(servOpts);
            allOptionsReceived = (unknowns == null);
            defaultsReceived = true;
            return unknowns;
        }

        /**
         * After calling receiveDefaults, call this as each GAMEOPTIONGETINFO is received.
         * Updates allOptionsReceived.
         *
         * @param gi  Message from server with info on one parameter
         * @return true if all are known, false if more are unknown after this one
         */
        public boolean receiveInfo(SOCGameOptionInfo gi)
        {
            String oKey = gi.getOptionNameKey();
            SOCGameOption oinfo = gi.getOptionInfo();
            SOCGameOption oldcopy = (SOCGameOption) optionSet.get(oKey);

            if ((oinfo.optKey.equals("-")) && (oinfo.optType == SOCGameOption.OTYPE_UNKNOWN))
            {
                // end-of-list marker: no more options from server.
                // That is end of srv's response to cli sending GAMEOPTIONGETINFOS("-").
                noMoreOptions(false);
                return true;
            } else {
                // remove old, replace with new from server (if any)
                SOCGameOption.addKnownOption(oinfo);
                if (oldcopy != null)
                    optionSet.remove(oKey);
                if (oinfo.optType != SOCGameOption.OTYPE_UNKNOWN)
                    optionSet.put(oKey, oinfo);
                return false;
            }
        }

    }  // class GameOptionServerSet

}  // public class SOCPlayerClient
