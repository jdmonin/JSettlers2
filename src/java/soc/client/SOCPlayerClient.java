/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2008 Jeremy D. Monin <jeremy@nand.net>
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
package soc.client;

import soc.debug.D;  // JM

import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;

import soc.message.*;

import soc.robot.SOCRobotClient;

import soc.server.SOCServer;
import soc.server.genericServer.LocalStringConnection;
import soc.server.genericServer.LocalStringServerSocket;
import soc.server.genericServer.StringConnection;

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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.net.ConnectException;
import java.net.Socket;

import java.util.Enumeration;
import java.util.Hashtable;
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
 * Each game's {@link SOCGame#isLocal} flag determines which connection to use.
 *
 * @author Robert S Thomas
 */
public class SOCPlayerClient extends Applet implements Runnable, ActionListener
{
    /** main panel, in cardlayout */
    protected static final String MAIN_PANEL = "main";

    /** message panel, in cardlayout */
    protected static final String MESSAGE_PANEL = "message";

    /** connect-or-practice panel (if jar launch), in cardlayout */
    protected static final String CONNECT_OR_PRACTICE_PANEL = "connOrPractice";

    /** Default tcp port number 8880 to listen, and to connect to remote server */
    public static final int SOC_PORT_DEFAULT = 8880;

    protected static String STATSPREFEX = "  [";
    protected TextField nick;
    protected TextField pass;
    protected TextField status;
    protected TextField channel;
    protected TextField game;
    protected java.awt.List chlist;
    protected java.awt.List gmlist;
    protected Button jc;  // join channel
    protected Button jg;  // join game
    protected Button pg;  // practice game (local)
    protected Label messageLabel;  // error message for messagepanel
    protected Label messageLabel_top;   // secondary message
    protected Label localTCPPortLabel;   // shows port number in mainpanel, if running localTCPServer
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
     * the nickname
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
     * the channels
     */
    protected Hashtable channels = new Hashtable();

    /**
     * the games
     */
    protected Hashtable games = new Hashtable();

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
     * SOCMessages of games where {@link SOCGame#isLocal} is true are sent
     * to practiceServer.
     */
    protected SOCServer practiceServer = null;

    /**
     * for connection to local-practice server {@link #practiceServer}
     */
    protected StringConnection prCli = null;
    protected int numPracticeGames = 0;  // Used for naming practice games

    /**
     * Client-hosted TCP server. If client is running this server, it's also connected
     * as a client, instead of being client of a remote server.
     * Started via {@link #startLocalTCPServer(int)}.
     * {@link #practiceServer} may still be activated at the user's request.
     * Note that {@link SOCGame#isLocal} is false for localTCPServer's games.
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
        pass.setEchoChar('*');
        status = new TextField(20);
        status.setEditable(false);
        channel = new TextField(20);
        game = new TextField(20);
        chlist = new java.awt.List(10, false);
        chlist.add(" ");
        gmlist = new java.awt.List(10, false);
        gmlist.add(" ");
        jc = new Button("Join Channel");
        jg = new Button("Join Game");
        pg = new Button("Practice");  // "practice game" text is too wide

        nick.addActionListener(this);
        pass.addActionListener(this);
        channel.addActionListener(this);
        game.addActionListener(this);
        chlist.addActionListener(this);
        gmlist.addActionListener(this);
        jc.addActionListener(this);
        jg.addActionListener(this);
        pg.addActionListener(this);        
        
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

        // Row 3

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

        l = new Label("New Game:");
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        gbl.setConstraints(game, c);
        mainPane.add(game);

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 4 (spacer)

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 5

        localTCPPortLabel = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(localTCPPortLabel, c);
        mainPane.add(localTCPPortLabel);

        c.gridwidth = 1;
        gbl.setConstraints(jc, c);
        mainPane.add(jc);

        l = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        gbl.setConstraints(pg, c);
        mainPane.add(pg);

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
     * The first message over the connection is the server's response:
     * Either {@link SOCRejectConnection}, or the lists of
     * channels and games ({@link SOCChannels}, {@link SOCGames}).
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
     */
    public String getNickname()
    {
        return nickname;
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
        if ((target == jc) || (target == channel) || (target == chlist)) // Join channel stuff
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
                        return;
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
                    return;
                }
            }

            if (ch.length() == 0)
            {
                return;
            }

            ChannelFrame cf = (ChannelFrame) channels.get(ch);

            if (cf == null)
            {
                if (channels.isEmpty())
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
                        return;
                    }

                    if (n.length() > 20)
                    {
                        nickname = n.substring(1, 20);
                    }
                    else
                    {
                        nickname = n;
                    }

                    if (!gotPassword)
                    {
                        String p = pass.getText().trim();

                        if (p.length() > 20)
                        {
                            password = p.substring(1, 20);
                        }
                        else
                        {
                            password = p;
                        }
                    }
                }

                status.setText("Talking to server...");
                putNet(SOCJoin.toCmd(nickname, password, host, ch));
            }
            else
            {
                cf.show();
            }

            channel.setText("");

            return;
        }

        if ((target == jg) || (target == game) || (target == gmlist) || (target == pg) || (target == pgm)) // Join game stuff
        {
            String gm;

            if ((target == pg) || (target == pgm)) // "Practice Game" Buttons
            {
                // If blank, fill in game and player names

                gm = game.getText().trim();
                if (gm.length() == 0)
                {
                    gm = DEFAULT_PRACTICE_GAMENAME;
                    game.setText(gm);
                }

                if (0 == nick.getText().trim().length())
                {
                    nick.setText(DEFAULT_PLAYER_NAME);
                }
            }
            else if (target == jg) // "Join Game" Button
            {
                gm = game.getText().trim();

                if (gm.length() == 0)
                {
                    try
                    {
                        gm = gmlist.getSelectedItem().trim();
                    }
                    catch (NullPointerException ex)
                    {
                        return;
                    }
                }
            }
            else if (target == game)
            {
                gm = game.getText().trim();
            }
            else
            {
                try
                {
                    gm = gmlist.getSelectedItem().trim();
                }
                catch (NullPointerException ex)
                {
                    return;
                }
            }

            // System.out.println("GM = |"+gm+"|");
            if (gm.length() == 0)
            {
                return;
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
                    // No point joining, just start a new one.
                    startPracticeGame();
                }
                else
                {
                    new SOCPracticeAskDialog(this, pi).show();
                }

                return;
            }

            if (pi == null)
            {
                if (games.isEmpty())
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
                        return;
                    }

                    if (n.length() > 20)
                    {
                        nickname = n.substring(1, 20);
                    }
                    else
                    {
                        nickname = n;
                    }

                    if (!gotPassword)
                    {
                        String p = pass.getText().trim();

                        if (p.length() > 20)
                        {
                            password = p.substring(1, 20);
                        }
                        else
                        {
                            password = p;
                        }
                    }
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
                    startPracticeGame(gm, true);  // Also sets WAIT_CURSOR
                }
                else
                {
                    status.setText("Talking to server...");
                    putNet(SOCJoinGame.toCmd(nickname, password, host, gm));

                    // May take a while for server to start game.
                    // The new-game window will clear this cursor
                    // (SOCPlayerInterface constructor)
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                }
            }
            else
            {
                pi.show();
            }

            game.setText("");

            return;
        }

        if (target == nick)
        { // Nickname TextField
            nick.transferFocus();
        }

        return;
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
            gameNames = practiceServer.getGameNames();
        else
            gameNames = playerInterfaces.keys();

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
    public void resendLocal()
    {
        putLocal(lastMessage_L);
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
     *
     * @param s  the message
     * @return true if the message was sent, false if not
     * @see #put(String, boolean)
     */
    public synchronized boolean putLocal(String s)
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
     * Write a message to the net or local server.
     * Because the player can be in both network games and local games,
     * we must route to the appropriate client-server connection.
     * 
     * @param s  the message
     * @param isLocal Is the server local (practice game), or network?
     *                {@link #localTCPServer} is considered "network" here.
     * @return true if the message was sent, false if not
     */
    public synchronized boolean put(String s, boolean isLocal)
    {
        if (isLocal)
            return putLocal(s);
        else
            return putNet(s);
    }

    /**
     * Treat the incoming messages.
     * Messages of unknown type are ignored (mes will be null from {@link SOCMessage#toMsg(String)}).
     *
     * @param mes    the message
     * @param isLocal Server is local (practice game, not network)
     */
    public void treat(SOCMessage mes, boolean isLocal)
    {
        if (mes == null)
            return;  // Parsing error

        D.ebugPrintln(mes.toString());

        try
        {
            switch (mes.getType())
            {
            /**
             * server's version message
             */
            case SOCMessage.VERSION:
                handleVERSION(isLocal, (SOCVersion) mes);

                break;

            /**
             * status message
             */
            case SOCMessage.STATUSMESSAGE:
                handleSTATUSMESSAGE((SOCStatusMessage) mes);

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
                handleCHANNELS((SOCChannels) mes, isLocal);

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
                handleGAMES((SOCGames) mes);

                break;

            /**
             * join game authorization
             */
            case SOCMessage.JOINGAMEAUTH:
                handleJOINGAMEAUTH((SOCJoinGameAuth) mes, isLocal);

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
                handleNEWGAME((SOCNewGame) mes);

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
             * the robber moved
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
                handleDEVCARD((SOCDevCard) mes);

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

            }  // switch (mes.getType())               
        }
        catch (Exception e)
        {
            System.out.println("SOCPlayerClient treat ERROR - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle the "version" message, server's version report.
     * Reply with client's version.
     * If remote, store the server's version for {@link #getServerVersion(SOCGame)}.
     * (Local server's version is always {@link Version#versionNumber()}.)
     *
     * @param isLocal Is the server local, or remote?  Client can be connected
     *                only to local, or remote.
     * @param mes  the messsage
     */
    private void handleVERSION(boolean isLocal, SOCVersion mes)
    {
        D.ebugPrintln("handleVERSION: " + mes);
        int vers = mes.getVersionNumber();
        if (! isLocal)
            sVersion = vers;

        // If we ever require a minimum server version, would check that here.

        // Reply with our client version.
        put(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum()),
            isLocal);
    }

    /**
     * handle the "status message" message.
     * Used for server events, also used if player tries to join a game
     * but their nickname is not OK.
     * @param mes  the message
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes)
    {
        status.setText(mes.getStatus());
        // If was trying to join a game, reset cursor from WAIT_CURSOR.
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
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
     * @param isLocal is the server actually local (practice game)?
     */
    protected void handleCHANNELS(SOCChannels mes, boolean isLocal)
    {
        //
        // this message indicates that we're connected to the server
        //
        if (! isLocal)
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
    protected void handleGAMES(SOCGames mes)
    {
        Enumeration gamesEnum = (mes.getGames()).elements();

        while (gamesEnum.hasMoreElements())
        {
            addToGameList((String) gamesEnum.nextElement());
        }
    }

    /**
     * handle the "join game authorization" message
     * @param mes  the message
     * @param isLocal server is local for practice (vs. normal network)
     */
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes, boolean isLocal)
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

        SOCGame ga = new SOCGame(mes.getGame());

        if (ga != null)
        {
            ga.isLocal = isLocal;
            SOCPlayerInterface pi = new SOCPlayerInterface(mes.getGame(), this, ga);
            pi.setVisible(true);
            playerInterfaces.put(mes.getGame(), pi);
            games.put(mes.getGame(), ga);
        }
    }

    /**
     * handle the "join game" message
     * @param mes  the message
     */
    protected void handleJOINGAME(SOCJoinGame mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.print("*** " + mes.getNickname() + " has joined this game.\n");
    }

    /**
     * handle the "leave game" message
     * @param mes  the message
     */
    protected void handleLEAVEGAME(SOCLeaveGame mes)
    {
        String gn = mes.getGame();
        SOCGame ga = (SOCGame) games.get(gn);

        if (ga != null)
        {
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(gn);
            SOCPlayer player = ga.getPlayer(mes.getNickname());

            if (player != null)
            {
                //
                //  This user was not a spectator
                //
                pi.removePlayer(player.getPlayerNumber());
                ga.removePlayer(mes.getNickname());
            }
        }
    }

    /**
     * handle the "new game" message
     * @param mes  the message
     */
    protected void handleNEWGAME(SOCNewGame mes)
    {
        addToGameList(mes.getGame());
    }

    /**
     * handle the "delete game" message
     * @param mes  the message
     */
    protected void handleDELETEGAME(SOCDeleteGame mes)
    {
        deleteFromGameList(mes.getGame());
    }

    /**
     * handle the "game members" message
     * @param mes  the message
     */
    protected void handleGAMEMEMBERS(SOCGameMembers mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        System.err.println("got GAMEMEMBERS"); // TODO tracing
        pi.began();
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
                ga.getPlayer(mesPN).setRobotFlag(mes.isRobot());
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
             * update the hand panel
             */
            final SOCHandPanel hp = pi.getPlayerHandPanel(mesPN);
            hp.updateValue(SOCHandPanel.ROADS);
            hp.updateValue(SOCHandPanel.SETTLEMENTS);
            hp.updateValue(SOCHandPanel.CITIES);
            hp.updateValue(SOCHandPanel.NUMKNIGHTS);
            hp.updateValue(SOCHandPanel.VICTORYPOINTS);
            hp.updateValue(SOCHandPanel.LONGESTROAD);
            hp.updateValue(SOCHandPanel.LARGESTARMY);

            if (nickname.equals(mes.getNickname()))
            {
                hp.updateValue(SOCHandPanel.CLAY);
                hp.updateValue(SOCHandPanel.ORE);
                hp.updateValue(SOCHandPanel.SHEEP);
                hp.updateValue(SOCHandPanel.WHEAT);
                hp.updateValue(SOCHandPanel.WOOD);
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
            SOCBoard bd = ga.getBoard();
            bd.setHexLayout(mes.getHexLayout());
            bd.setNumberLayout(mes.getNumberLayout());
            bd.setRobberHex(mes.getRobberHex());

            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            pi.getBoardPanel().repaint();
        }
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
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            ga.setCurrentPlayerNumber(mes.getPlayerNumber());

            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            pi.getBoardPanel().repaint();

            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                pi.getPlayerHandPanel(i).updateTakeOverButton();
            }
        }
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
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            int pnum = mes.getPlayerNumber();

            /**
             * check if this is the first player
             */
            if (ga.getFirstPlayer() == -1)
            {
                ga.setFirstPlayer(pnum);
            }

            ga.setCurrentDice(0);
            ga.setCurrentPlayerNumber(pnum);
            ga.getPlayer(pnum).getDevCards().newToOld();
            ga.resetVoteClear();

            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
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
            int hpanUpdateRsrcType = -1;  // If not -1, update this type's amount display

            switch (mes.getElementType())
            {
            case SOCPlayerElement.ROADS:

                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.ROAD);
                hpan.updateValue(SOCHandPanel.ROADS);
                break;

            case SOCPlayerElement.SETTLEMENTS:

                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.SETTLEMENT);
                hpan.updateValue(SOCHandPanel.SETTLEMENTS);
                break;

            case SOCPlayerElement.CITIES:

                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                    (mes, pl, SOCPlayingPiece.CITY);
                hpan.updateValue(SOCHandPanel.CITIES);
                break;

            case SOCPlayerElement.NUMKNIGHTS:

                // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
                {
                    final SOCPlayer oldLargestArmyPlayer = ga.getPlayerWithLargestArmy();
                    SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numKnights
                        (mes, pl, ga);
                    hpan.updateValue(SOCHandPanel.NUMKNIGHTS);

                    // Check for change in largest-army player; update handpanels'
                    // LARGESTARMY and VICTORYPOINTS counters if so, and
                    // announce with text message.
                    pi.updateLongestLargest(false, oldLargestArmyPlayer, ga.getPlayerWithLargestArmy());
                }

                break;

            case SOCPlayerElement.CLAY:

                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.CLAY);
                hpanUpdateRsrcType = SOCHandPanel.CLAY;
                break;

            case SOCPlayerElement.ORE:

                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.ORE);
                hpanUpdateRsrcType = SOCHandPanel.ORE;
                break;

            case SOCPlayerElement.SHEEP:

                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.SHEEP);
                hpanUpdateRsrcType = SOCHandPanel.SHEEP;
                break;

            case SOCPlayerElement.WHEAT:

                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.WHEAT);
                hpanUpdateRsrcType = SOCHandPanel.WHEAT;
                break;

            case SOCPlayerElement.WOOD:

                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                    (mes, pl, SOCResourceConstants.WOOD);
                hpanUpdateRsrcType = SOCHandPanel.WOOD;
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
            }

            if (hpanUpdateRsrcType != -1)
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
        }
    }

    /**
     * handle the "put piece" message
     * @param mes  the message
     */
    protected void handlePUTPIECE(SOCPutPiece mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            final int mesPn = mes.getPlayerNumber();
            final SOCPlayer pl = ga.getPlayer(mesPn);
            final SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
            final SOCHandPanel mesHp = pi.getPlayerHandPanel(mesPn);
            final SOCPlayer oldLongestRoadPlayer = ga.getPlayerWithLongestRoad();


            switch (mes.getPieceType())
            {
            case SOCPlayingPiece.ROAD:

                SOCRoad rd = new SOCRoad(pl, mes.getCoordinates());
                ga.putPiece(rd);
                mesHp.updateValue(SOCHandPanel.ROADS);

                break;

            case SOCPlayingPiece.SETTLEMENT:

                SOCSettlement se = new SOCSettlement(pl, mes.getCoordinates());
                ga.putPiece(se);
                mesHp.updateValue(SOCHandPanel.SETTLEMENTS);

                /**
                 * if this is the second initial settlement, then update the resource display
                 */
                if (mesHp.isClientPlayer())
                {
                    mesHp.updateValue(SOCHandPanel.CLAY);
                    mesHp.updateValue(SOCHandPanel.ORE);
                    mesHp.updateValue(SOCHandPanel.SHEEP);
                    mesHp.updateValue(SOCHandPanel.WHEAT);
                    mesHp.updateValue(SOCHandPanel.WOOD);
                }
                else
                {
                    mesHp.updateValue(SOCHandPanel.NUMRESOURCES);
                }

                break;

            case SOCPlayingPiece.CITY:

                SOCCity ci = new SOCCity(pl, mes.getCoordinates());
                ga.putPiece(ci);
                mesHp.updateValue(SOCHandPanel.SETTLEMENTS);
                mesHp.updateValue(SOCHandPanel.CITIES);

                break;
            }

            mesHp.updateValue(SOCHandPanel.VICTORYPOINTS);
            pi.getBoardPanel().repaint();
            pi.getBuildingPanel().updateButtonStatus();

            /**
             * Check for and announce change in longest road; update all players' victory points.
             */
            SOCPlayer newLongestRoadPlayer = ga.getPlayerWithLongestRoad();
            if (newLongestRoadPlayer != oldLongestRoadPlayer)
            {
                pi.updateLongestLargest(true, oldLongestRoadPlayer, newLongestRoadPlayer);
            }
        }
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
        SOCSettlement pp = new SOCSettlement(pl, pl.getLastSettlementCoord());
        ga.undoPutInitSettlement(pp);

        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.getPlayerHandPanel(pl.getPlayerNumber()).updateResourcesVP();
        pi.getBoardPanel().updateMode();
    }

    /**
     * handle the "robber moved" message
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
            ga.getBoard().setRobberHex(mes.getCoordinates());
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
        pi.showDiscardDialog(mes.getNumberOfDiscards());
    }

    /**
     * handle the "choose player request" message
     * @param mes  the message
     */
    protected void handleCHOOSEPLAYERREQUEST(SOCChoosePlayerRequest mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        int[] choices = new int[SOCGame.MAXPLAYERS];
        boolean[] ch = mes.getChoices();
        int count = 0;

        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
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
            ga.getPlayer(mes.getPlayerNumber()).setCurrentOffer(null);
            pi.getPlayerHandPanel(mes.getPlayerNumber()).updateCurrentOffer();
        }
    }

    /**
     * handle the "reject offer" message
     * @param mes  the message
     */
    protected void handleREJECTOFFER(SOCRejectOffer mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.getPlayerHandPanel(mes.getPlayerNumber()).rejectOffer();
    }

    /**
     * handle the "clear trade message" message
     * @param mes  the message
     */
    protected void handleCLEARTRADEMSG(SOCClearTradeMsg mes)
    {
        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
        pi.getPlayerHandPanel(mes.getPlayerNumber()).clearTradeMsg();
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
    protected void handleDEVCARD(SOCDevCard mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            final int mesPN = mes.getPlayerNumber();
            SOCPlayer player = ga.getPlayer(mesPN);
            SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());

            switch (mes.getAction())
            {
            case SOCDevCard.DRAW:
                player.getDevCards().add(1, SOCDevCardSet.NEW, mes.getCardType());

                break;

            case SOCDevCard.PLAY:
                player.getDevCards().subtract(1, SOCDevCardSet.OLD, mes.getCardType());

                break;

            case SOCDevCard.ADDOLD:
                player.getDevCards().add(1, SOCDevCardSet.OLD, mes.getCardType());

                break;

            case SOCDevCard.ADDNEW:
                player.getDevCards().add(1, SOCDevCardSet.NEW, mes.getCardType());

                break;
            }

            SOCPlayer ourPlayerData = ga.getPlayer(nickname);

            if (ourPlayerData != null)
            {
                //if (true) {
                if (mesPN == ourPlayerData.getPlayerNumber())
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
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
            player.setPotentialSettlements(mes.getPotentialSettlements());
        }
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

            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
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
        if (pi.clientIsCurrentPlayer())
            pi.getClientHand().autoRollOrPromptPlayer();
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
        greset.isLocal = ga.isLocal;
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
     * add a new game to the initial window's list of games
     *
     * @param gameName  the game name to add to the list
     */
    public void addToGameList(String gameName)
    {
        // String gameName = thing + STATSPREFEX + "-- -- -- --]";

        if ((gmlist.countItems() > 0) && (gmlist.getItem(0).equals(" ")))
        {
            gmlist.replaceItem(gameName, 0);
            gmlist.select(0);
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

                for (int pn = 0; pn < (SOCGame.MAXPLAYERS - 1); pn++)
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

                if (scores[SOCGame.MAXPLAYERS - 1] != -1)
                {
                    if (robots[SOCGame.MAXPLAYERS - 1])
                    {
                        updatedString += "#";
                    }
                    else
                    {
                        updatedString += "o";
                    }

                    updatedString += (scores[SOCGame.MAXPLAYERS - 1] + "]");
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
            return;  // Should not have been sent; game is not yet over.

        SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(game);
        pi.updateAtOver(scores);
    }

    /**
     * delete a game from the list
     *
     * @param gameName   the game to remove
     */
    public void deleteFromGameList(String gameName)
    {
        //String testString = gameName + STATSPREFEX;
        String testString = gameName;

        if (gmlist.getItemCount() == 1)
        {
            if (gmlist.getItem(0).startsWith(testString))
            {
                gmlist.replaceItem(" ", 0);
                gmlist.deselect(0);
            }

            return;
        }

        for (int i = gmlist.getItemCount() - 1; i >= 0; i--)
        {
            if (gmlist.getItem(i).startsWith(testString))
            {
                gmlist.remove(i);
            }
        }

        if (gmlist.getSelectedIndex() == -1)
        {
            gmlist.select(gmlist.getItemCount() - 1);
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
        put(SOCBuyCardRequest.toCmd(ga.getName()), ga.isLocal);
    }

    /**
     * request to build something
     *
     * @param ga     the game
     * @param piece  the type of piece from SOCPlayingPiece
     */
    public void buildRequest(SOCGame ga, int piece)
    {
        put(SOCBuildRequest.toCmd(ga.getName(), piece), ga.isLocal);
    }

    /**
     * request to cancel building something
     *
     * @param ga     the game
     * @param piece  the type of piece from SOCPlayingPiece
     */
    public void cancelBuildRequest(SOCGame ga, int piece)
    {
        put(SOCCancelBuildRequest.toCmd(ga.getName(), piece), ga.isLocal);
    }

    /**
     * put a piece on the board
     *
     * @param ga  the game where the action is taking place
     * @param pp  the piece being placed
     */
    public void putPiece(SOCGame ga, SOCPlayingPiece pp)
    {
        /**
         * send the command
         */
        put(SOCPutPiece.toCmd(ga.getName(), pp.getPlayer().getPlayerNumber(), pp.getType(), pp.getCoordinates()), ga.isLocal);
    }

    /**
     * the player wants to move the robber
     *
     * @param ga  the game
     * @param pl  the player
     * @param coord  where the player wants the robber
     */
    public void moveRobber(SOCGame ga, SOCPlayer pl, int coord)
    {
        put(SOCMoveRobber.toCmd(ga.getName(), pl.getPlayerNumber(), coord), ga.isLocal);
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
            put(SOCGameTextMsg.toCmd(ga.getName(), nickname, me), ga.isLocal);
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
        put(SOCLeaveGame.toCmd(nickname, host, ga.getName()), ga.isLocal);
    }

    /**
     * the user sits down to play
     *
     * @param ga   the game
     * @param pn   the number of the seat where the user wants to sit
     */
    public void sitDown(SOCGame ga, int pn)
    {
        put(SOCSitDown.toCmd(ga.getName(), "dummy", pn, false), ga.isLocal);
    }

    /**
     * the user is starting the game
     *
     * @param ga  the game
     */
    public void startGame(SOCGame ga)
    {
        put(SOCStartGame.toCmd(ga.getName()), ga.isLocal);
    }

    /**
     * the user rolls the dice
     *
     * @param ga  the game
     */
    public void rollDice(SOCGame ga)
    {
        put(SOCRollDice.toCmd(ga.getName()), ga.isLocal);
    }

    /**
     * the user is done with the turn
     *
     * @param ga  the game
     */
    public void endTurn(SOCGame ga)
    {
        put(SOCEndTurn.toCmd(ga.getName()), ga.isLocal);
    }

    /**
     * the user wants to discard
     *
     * @param ga  the game
     */
    public void discard(SOCGame ga, SOCResourceSet rs)
    {
        put(SOCDiscard.toCmd(ga.getName(), rs), ga.isLocal);
    }

    /**
     * the user chose a player to steal from
     *
     * @param ga  the game
     * @param pn  the player id
     */
    public void choosePlayer(SOCGame ga, int pn)
    {
        put(SOCChoosePlayer.toCmd(ga.getName(), pn), ga.isLocal);
    }

    /**
     * the user is rejecting the current offers
     *
     * @param ga  the game
     */
    public void rejectOffer(SOCGame ga)
    {
        put(SOCRejectOffer.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber()), ga.isLocal);
    }

    /**
     * the user is accepting an offer
     *
     * @param ga  the game
     * @param from the number of the player that is making the offer
     */
    public void acceptOffer(SOCGame ga, int from)
    {
        put(SOCAcceptOffer.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber(), from), ga.isLocal);
    }

    /**
     * the user is clearing an offer
     *
     * @param ga  the game
     */
    public void clearOffer(SOCGame ga)
    {
        put(SOCClearOffer.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber()), ga.isLocal);
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
        put(SOCBankTrade.toCmd(ga.getName(), give, get), ga.isLocal);
    }

    /**
     * the user is making an offer to trade
     *
     * @param ga    the game
     * @param offer the trade offer
     */
    public void offerTrade(SOCGame ga, SOCTradeOffer offer)
    {
        put(SOCMakeOffer.toCmd(ga.getName(), offer), ga.isLocal);
    }

    /**
     * the user wants to play a development card
     *
     * @param ga  the game
     * @param dc  the type of development card
     */
    public void playDevCard(SOCGame ga, int dc)
    {
        put(SOCPlayDevCardRequest.toCmd(ga.getName(), dc), ga.isLocal);
    }

    /**
     * the user picked 2 resources to discover
     *
     * @param ga    the game
     * @param rscs  the resources
     */
    public void discoveryPick(SOCGame ga, SOCResourceSet rscs)
    {
        put(SOCDiscoveryPick.toCmd(ga.getName(), rscs), ga.isLocal);
    }

    /**
     * the user picked a resource to monopolize
     *
     * @param ga   the game
     * @param res  the resource
     */
    public void monopolyPick(SOCGame ga, int res)
    {
        put(SOCMonopolyPick.toCmd(ga.getName(), res), ga.isLocal);
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
        put(SOCChangeFace.toCmd(ga.getName(), ga.getPlayer(nickname).getPlayerNumber(), id), ga.isLocal);
    }

    /**
     * the user is locking a seat
     *
     * @param ga  the game
     * @param pn  the seat number
     */
    public void lockSeat(SOCGame ga, int pn)
    {
        put(SOCSetSeatLock.toCmd(ga.getName(), pn, true), ga.isLocal);
    }

    /**
     * the user is unlocking a seat
     *
     * @param ga  the game
     * @param pn  the seat number
     */
    public void unlockSeat(SOCGame ga, int pn)
    {
        put(SOCSetSeatLock.toCmd(ga.getName(), pn, false), ga.isLocal);
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
        put(SOCResetBoardRequest.toCmd(SOCMessage.RESETBOARDREQUEST, ga.getName()), ga.isLocal);
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
        put(SOCResetBoardVote.toCmd(ga.getName(), pn, voteYes), ga.isLocal);
    }

    /**
     * handle local client commands for channels
     *
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
     * handle local client commands for games
     *
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
        D.ebugPrintln("onIgnoreList |" + name + "|");

        boolean result = false;
        Enumeration ienum = ignoreList.elements();

        while (ienum.hasMoreElements())
        {
            String s = (String) ienum.nextElement();
            if (D.ebugIsEnabled())
            {
                D.ebugPrintln("comparing |" + s + "| to |" + name + "|");
            }

            if (s.equals(name))
            {
                result = true;
                D.ebugPrintln("match");

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
        put(SOCGameTextMsg.toCmd(ga.getName(), nickname, msg), ga.isLocal);
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
        put(SOCGameTextMsg.toCmd(ga.getName(), nickname, msg), ga.isLocal);
    }

    /**
     * Create a game name, and start a practice game.
     * Assumes {@link #MAIN_PANEL} is initialized.
     */
    public void startPracticeGame()
    {
        startPracticeGame(null, true);
    }

    /**
     * Setup for local practice game (local server).
     * If needed, a local server, client, and robots are started.
     *
     * @param practiceGameName Unique name to give practice game; if name unknown, call
     *         {@link #startPracticeGame()} instead
     * @param mainPanelIsActive Is the SOCPlayerClient main panel active?
     *         False if we're being called from elsewhere, such as
     *         {@link SOCConnectOrPracticePanel}.
     */
    public void startPracticeGame(String practiceGameName, boolean mainPanelIsActive)
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
            setupLocalRobots(0);
        }
        if (prCli == null)
        {
            try
            {
                prCli = LocalStringServerSocket.connectTo(SOCServer.PRACTICE_STRINGPORT);
                new SOCPlayerLocalStringReader((LocalStringConnection)prCli);
                // Reader will start its own thread
            }
            catch (ConnectException e)
            {
                ex_L = e;
                return;
            }
        }

        // Ask local "server" to create the game
        putLocal(SOCJoinGame.toCmd(nickname, password, host, practiceGameName));

        // Clear the textfield for next game name
        if (game != null)
            game.setText("");
    }

    /**
     * Setup for locally hosting a TCP server.
     * If needed, a local server and robots are started, and client connects to it.
     * If parent is a Frame, set titlebar to show "server" and port#.
     * Show port number in {@link #localTCPPortLabel}. 
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
        setupLocalRobots(tport);

        // Set label
        localTCPPortLabel.setText("Port: " + tport);
        new AWTToolTip ("You are running a server on TCP port " + tport + ".", localTCPPortLabel);

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
     * Set up some robot opponents for a locally running server (tcp or stringport).
     * This lets the server randomize whether we play against smart or fast ones.
     * (Some will be SOCRobotDM.FAST_STRATEGY, some SMART_STRATEGY).
     * If the local server is stringport, it must be running as
     * {@link SOCServer#PRACTICE_STRINGPORT}.
     *
     * @param port Port number for tcp, or 0 for stringport
     * @see #startPracticeGame()
     * @see #startLocalTCPServer(int)
     */
    public void setupLocalRobots(int port)
    {
        SOCRobotClient[] robo_fast = new SOCRobotClient[5];
        SOCRobotClient[] robo_smrt = new SOCRobotClient[2];

        // ASSUMPTION: Server ROBOT_PARAMS_DEFAULT uses SOCRobotDM.FAST_STRATEGY.

        // Make some faster ones first.
        for (int i = 0; i < 5; ++i)
        {
            String rname = "droid " + (i+1);
            if (port == 0)
                robo_fast[i] = new SOCRobotClient (SOCServer.PRACTICE_STRINGPORT, rname, "pw");
            else
                robo_fast[i] = new SOCRobotClient ("localhost", port, rname, "pw");
            new Thread(new SOCPlayerLocalRobotRunner(robo_fast[i])).start();
            Thread.yield();
            try
            {
                Thread.sleep(75);  // Let that robot go for a bit.
                    // robot runner thread will call its init()
            }
            catch (InterruptedException ie) {}
        }

        try
        {
            Thread.sleep(150);
                // Wait for these robots' accept and UPDATEROBOTPARAMS,
                // before we change the default params.
        }
        catch (InterruptedException ie) {}

        // Make a few smarter ones now.
        // Switch params to SMARTER for future new robots.
        // This works because server is in the same JVM as client.

        SOCServer.ROBOT_PARAMS_DEFAULT = SOCServer.ROBOT_PARAMS_SMARTER;   // SOCRobotDM.SMART_STRATEGY

        for (int i = 0; i < 2; ++i)
        {
            String rname = "robot " + (i+1+robo_fast.length);
            if (port == 0)
                robo_smrt[i] = new SOCRobotClient (SOCServer.PRACTICE_STRINGPORT, rname, "pw");
            else
                robo_smrt[i] = new SOCRobotClient ("localhost", port, rname, "pw");
            new Thread(new SOCPlayerLocalRobotRunner(robo_smrt[i])).start();
            Thread.yield();
            try
            {
                Thread.sleep(75);  // Let that robot go for a bit.
            }
            catch (InterruptedException ie) {}
        }
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
        if (game.isLocal)
            return Version.versionNumber();
        else
            return sVersion;
    }

    /**
     * applet info
     */
    public String getAppletInfo()
    {
        return "SOCPlayerClient 0.9 by Robert S. Thomas.";
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
            if (! (canLocal && pi.getGame().isLocal))
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
            pgm.requestFocus();
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
            putLocal(leaveAllMes.toCmd());
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
     * For local practice games, each robot gets its own thread.
     * Equivalent to main thread in SOCRobotClient in network games.
     */
    protected class SOCPlayerLocalRobotRunner implements Runnable
    {
        SOCRobotClient rob;

        protected SOCPlayerLocalRobotRunner (SOCRobotClient rc)
        {
            rob = rc;
        }

        public void run()
        {
            Thread.currentThread().setName("robotrunner-" + rob.getNickname());
            rob.init();
        }
    }

}
