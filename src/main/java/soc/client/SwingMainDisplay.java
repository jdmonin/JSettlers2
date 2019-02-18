/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
 * Extracted in 2019 from SOCPlayerClient.java, so:
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCScenario;
import soc.game.SOCVersionedItem;
import soc.message.SOCAuthRequest;
import soc.message.SOCChannelTextMsg;
import soc.message.SOCGameOptionGetDefaults;
import soc.message.SOCGameOptionGetInfos;
import soc.message.SOCGameOptionInfo;
import soc.message.SOCJoinChannel;
import soc.message.SOCJoinGame;
import soc.message.SOCMessage;
import soc.message.SOCNewGameWithOptions;
import soc.message.SOCNewGameWithOptionsRequest;
import soc.message.SOCScenarioInfo;
import soc.message.SOCStatusMessage;
import soc.server.SOCServer;
import soc.util.SOCFeatureSet;
import soc.util.SOCGameList;
import soc.util.SOCStringManager;
import soc.util.Version;


/**
 * A {@link MainDisplay} implementation for Swing.
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
 * Also converted from AWT to Swing in v2.0.00.
 * @since 2.0.00
 */
@SuppressWarnings("serial")
public class SwingMainDisplay extends JPanel implements MainDisplay
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

    private final SOCPlayerClient client;

    private final ClientNetwork net;

    /**
     * The player interfaces for the {@link SOCPlayerClient#games} we're playing.
     * Accessed from GUI thread and network MessageHandler thread.
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
     * and {@link #initMainPanelLayout(boolean, SOCFeatureSet)}.
     *<P>
     * {@code mainPane}, {@link #mainGBL}, and {@link #mainGBC} are fields not locals so that
     * the layout can be changed after initialization if needed.  Most of the Main Panel
     * elements are initialized in {@link #initVisualElements()} but not laid out or made visible
     * until a later call to {@link #initMainPanelLayout(boolean, SOCFeatureSet)} (from
     * ({@link #showVersion(int, String, String, SOCFeatureSet) showVersion(....)})
     * when the version and features are known.
     * @since 1.1.19
     */
    private JPanel mainPane;

    /** Layout for {@link #mainPane} */
    private GridBagLayout mainGBL;

    /** Constraints for {@link #mainGBL} */
    private GridBagConstraints mainGBC;

    /**
     * Flags for tracking {@link #mainPane} layout status, in case
     * {@link #initMainPanelLayout(boolean, SOCFeatureSet)} is
     * called again after losing connection and then connecting to
     * another server or starting a hosted tcp server.
     * @since 1.1.19
     */
    private boolean mainPaneLayoutIsDone, mainPaneLayoutIsDone_hasChannels;

    /** Nickname (username) to connect to server and use in games */
    protected JTextField nick;

    /** Password for {@link #nick} while connecting to server, or blank */
    protected JPasswordField pass;

    /** Status from server, or progress/error message updated by client */
    protected JTextField status;

    /**
     * Chat channel name to create or join with {@link #jc} button.
     * Hidden in v1.1.19+ if server is missing {@link SOCFeatureSet#SERVER_CHANNELS}.
     */
    protected JTextField channel;

    // protected TextField game;  // removed 1.1.07 - NewGameOptionsFrame instead

    /**
     * List of chat channels that can be joined with {@link #jc} button or by double-click.
     * Hidden in v1.1.19+ if server is missing {@link SOCFeatureSet#SERVER_CHANNELS}.
     *<P>
     * When there are no channels, this list contains a single blank item (" ").
     */
    protected JList<JoinableListItem> chlist;

    /**
     * List of games that can be joined with {@link #jg} button or by double-click,
     * or detail info displayed with {@link #gi} button.
     * Contains all games on server if connected, and any Practice Games
     * created with {@link #pg} button.
     *<P>
     * When there are no games, this list contains a single blank item (" ").
     */
    protected JList<JoinableListItem> gmlist;

    /**
     * "New Game..." button, brings up {@link NewGameOptionsFrame} window
     * @since 1.1.07
     */
    protected JButton ng;  // new game

    /**
     * "Join Channel" button, for channel currently highlighted in {@link #chlist},
     * or create new channel named in {@link #channel}. Hidden in v1.1.19+ if server
     * is missing {@link SOCFeatureSet#SERVER_CHANNELS}.
     */
    protected JButton jc;

    /** "Join Game" button */
    protected JButton jg;

    /**
     * Practice Game button: Create game to play against
     * {@link ClientNetwork#practiceServer}, not {@link ClientNetwork#localTCPServer}.
     */
    protected JButton pg;

    /**
     * "Game Info" button, shows a game's {@link SOCGameOption}s.
     *<P>
     * Renamed in 2.0.00 to 'gi'; previously 'so' Show Options.
     * @since 1.1.07
     */
    protected JButton gi;

    /**
     * Local Server indicator in main panel: blank, or 'server is running' if
     * {@link ClientNetwork#localTCPServer} has been started.
     * If so, localTCPServer's port number is shown in {@link #versionOrlocalTCPPortLabel}.
     */
    private JLabel localTCPServerLabel;

    /**
     * When connected to a remote server, shows its version number.
     * When running {@link ClientNetwork#localTCPServer}, shows that
     * server's port number (see also {@link #localTCPServerLabel}).
     * In either mode, has a tooltip with more info.
     */
    private JLabel versionOrlocalTCPPortLabel;

    protected JLabel messageLabel;  // error message for messagepanel
    protected JLabel messageLabel_top;   // secondary message
    protected JButton pgm;  // practice game on messagepanel

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
     * The channels we've joined.
     * Accessed from GUI thread and network MessageHandler thread.
     */
    protected Hashtable<String, ChannelFrame> channels = new Hashtable<String, ChannelFrame>();

    /**
     * Utility for time-driven events in the display.
     * To find users: Search for where-used of this field, {@link MainDisplay#getEventTimer()},
     * and {@link SOCPlayerInterface#getEventTimer()}.
     * @since 1.1.07
     */
    protected Timer eventTimer = new Timer(true);  // use daemon thread

    /**
     * Create a new SwingMainDisplay for this client.
     * Must call {@link #initVisualElements()} after this constructor.
     * @param hasConnectOrPractice  True if should initially display {@link SOCConnectOrPracticePanel}
     *     and ask for a server to connect to, false if the server is known
     *     and should display the main panel (game list, channel list, etc).
     * @param client  Client using this display; {@link SOCPlayerClient#strings client.strings} must not be null
     * @throws IllegalArgumentException if {@code client} is null
     */
    public SwingMainDisplay(boolean hasConnectOrPractice, final SOCPlayerClient client)
        throws IllegalArgumentException
    {
        if (client == null)
            throw new IllegalArgumentException("null client");

        this.hasConnectOrPractice = hasConnectOrPractice;
        this.client = client;
        net = client.getNet();

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

        // Set colors; easier than troubleshooting color-inherit from JFrame or applet tag
        setOpaque(true);
        setBackground(SOCPlayerClient.JSETTLERS_BG_GREEN);
        setForeground(Color.BLACK);
    }

    public SOCPlayerClient getClient()
    {
        return client;
    }

    public GameMessageMaker getGameMessageMaker()
    {
        return client.getGameMessageMaker();
    }

    public final Container getGUIContainer()
    {
        return this;
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
     * Uses {@link NotifyDialog#createAndShow(SwingMainDisplay, Frame, String, String, boolean)}
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

        nick = new JTextField(20);
        pass = new JPasswordField(20);
        status = new JTextField(20);
        status.setEditable(false);
        channel = new JTextField(20);

        DefaultListModel<JoinableListItem> lm = new DefaultListModel<JoinableListItem>();
        chlist = new JList<JoinableListItem>(lm);
        chlist.setVisibleRowCount(10);
        chlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lm.addElement(JoinableListItem.BLANK);

        lm = new DefaultListModel<JoinableListItem>();
        gmlist = new JList<JoinableListItem>(lm);
        gmlist.setVisibleRowCount(10);
        gmlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lm.addElement(JoinableListItem.BLANK);

        ng = new JButton(strings.get("pcli.main.newgame"));       // "New Game..."
        jc = new JButton(strings.get("pcli.main.join.channel"));  // "Join Channel"
        jg = new JButton(strings.get("pcli.main.join.game"));     // "Join Game"
        pg = new JButton(strings.get("pcli.main.practice"));      // "Practice" -- "practice game" text is too wide
        gi = new JButton(strings.get("pcli.main.game.info"));     // "Game Info" -- show game options

        // swing on win32 needs all JButtons to inherit their bgcolor from panel, or they get gray corners
        ng.setBackground(null);
        jc.setBackground(null);
        jg.setBackground(null);
        pg.setBackground(null);
        gi.setBackground(null);

        versionOrlocalTCPPortLabel = new JLabel();
        localTCPServerLabel = new JLabel();

        // Username not entered yet: can't click buttons
        ng.setEnabled(false);
        jc.setEnabled(false);

        // when game is selected in gmlist, these buttons will be enabled:
        jg.setEnabled(false);
        gi.setEnabled(false);

        nick.getDocument().addDocumentListener(new DocumentListener()
        {
            public void removeUpdate(DocumentEvent e)  { textValueChanged(); }
            public void insertUpdate(DocumentEvent e)  { textValueChanged(); }
            public void changedUpdate(DocumentEvent e) { textValueChanged(); }

            /**
             * When nickname contents change, enable/disable buttons as appropriate.
             * @since 1.1.07
             */
            private void textValueChanged()
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
                guardedActionPerform(e.getSource());
            }
        };

        nick.addActionListener(actionListener);  // hit Enter to go to next field
        pass.addActionListener(actionListener);
        channel.addActionListener(actionListener);
        chlist.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() < 2)
                    return;
                e.consume();
                guardedActionPerform(chlist);
            }
        });
        gmlist.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() < 2)
                    return;
                e.consume();
                guardedActionPerform(gmlist);
            }
        });
        gmlist.getSelectionModel().addListSelectionListener(new ListSelectionListener()
        {
            /**
             * When a game is selected/deselected, enable/disable buttons as appropriate. ({@link ListSelectionListener})
             * @param e textevent from {@link #gmlist}
             * @since 1.1.07
             */
            public void valueChanged(ListSelectionEvent e)
            {
                boolean wasSel = ! (((ListSelectionModel) (e.getSource())).isSelectionEmpty());
                if (wasSel != jg.isEnabled())
                {
                    jg.setEnabled(wasSel);
                    gi.setEnabled(wasSel &&
                        ((client.getNet().practiceServer != null)
                         || (client.sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)));
                }
            }
        });
        ng.addActionListener(actionListener);
        jc.addActionListener(actionListener);
        jg.addActionListener(actionListener);
        pg.addActionListener(actionListener);
        gi.addActionListener(actionListener);

        initMainPanelLayout(true, null);  // status line only, until later call to showVersion

        JPanel messagePane = new JPanel(new BorderLayout());
        messagePane.setBackground(null);  // inherit from parent
        messagePane.setForeground(null);

        // secondary message at top of message pane, used with pgm button.
        messageLabel_top = new JLabel("", SwingConstants.CENTER);
        messageLabel_top.setVisible(false);
        messagePane.add(messageLabel_top, BorderLayout.NORTH);

        // message label that takes up the whole pane
        messageLabel = new JLabel("", SwingConstants.CENTER);
        messageLabel.setForeground(SOCPlayerClient.MISC_LABEL_FG_OFF_WHITE);
        messagePane.add(messageLabel, BorderLayout.CENTER);

        // bottom of message pane: practice-game button
        pgm = new JButton(strings.get("pcli.message.practicebutton"));  // "Practice Game (against robots)"
        pgm.setVisible(false);
        pgm.setBackground(null);
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

        messageLabel.setText(strings.get("pcli.message.connecting.serv"));  // "Connecting to server..."
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
     *     Null when {@code isStatusRow == true}.  See {@link #showVersion(int, String, String, SOCFeatureSet)}
     *     javadoc for expected contents when an older server does not report features.
     * @since 1.1.19
     * @throws IllegalArgumentException if {@code feats} is null but {@code isStatusRow} is false
     */
    private void initMainPanelLayout(final boolean isStatusRow, final SOCFeatureSet feats)
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
        {
            mainPane = new JPanel(mainGBL);
            mainPane.setBackground(null);
            mainPane.setForeground(null);
            mainPane.setOpaque(false);
            mainPane.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        }
        else if (mainPane.getLayout() == null)
            mainPane.setLayout(mainGBL);

        final GridBagLayout gbl = mainGBL;
        final GridBagConstraints c = mainGBC;

        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.LINE_START;  // for buttons (don't use fill)

        if (isStatusRow)
        {
            c.weightx = 1;  // fill width, stretch with frame resize
            gbl.setConstraints(status, c);
            mainPane.add(status);
            c.weightx = 0;

            return;  // <---- Early return: Call later to lay out the rest ----
        }

        // Reminder: Everything here and below is the delayed second call.
        // So, any fields must be initialized in initVisualElements(), not here.

        final boolean hasChannels = feats.isActive(SOCFeatureSet.SERVER_CHANNELS);

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

        JLabel l;

        // Layout is 6 columns wide (item, item, middle spacer, item, spacer, item).
        // Text fields in column 2 and 6 are stretched to together fill available width (weightx 0.5).
        // If ! hasChannels, channel-related items won't be laid out; adjust spacing to compensate.

        // Row 1 (spacer)

        l = new JLabel();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 2

        l = new JLabel(strings.get("pcli.main.label.yournickname"));  // "Your Nickname:"
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        c.weightx = .5;
        gbl.setConstraints(nick, c);
        mainPane.add(nick);
        c.weightx = 0;

        l = new JLabel(" ");
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new JLabel(strings.get("pcli.main.label.optionalpw"));  // "Optional Password:"
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new JLabel(" ");
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        c.weightx = .5;
        gbl.setConstraints(pass, c);
        mainPane.add(pass);
        c.weightx = 0;

        l = new JLabel();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new JLabel();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 3 (New Channel label & textfield, Practice btn, New Game btn)

        if (hasChannels)
        {
            l = new JLabel(strings.get("pcli.main.label.newchannel"));  // "New Channel:"
            c.gridwidth = 1;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            c.gridwidth = 1;
            c.weightx = .5;
            gbl.setConstraints(channel, c);
            mainPane.add(channel);
            c.weightx = 0;
        }

        l = new JLabel();
        c.gridwidth = (hasChannels) ? 1 : 3;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;  // this position was "New Game:" label before 1.1.07
        gbl.setConstraints(pg, c);
        mainPane.add(pg);  // "Practice"; stretched to same width as "Game Info"

        l = new JLabel();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        gbl.setConstraints(ng, c);
        mainPane.add(ng);  // "New Game..."
        c.fill = GridBagConstraints.BOTH;

        l = new JLabel();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 4 (spacer/localTCP label)

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(localTCPServerLabel, c);
        mainPane.add(localTCPServerLabel);

        // Row 5 (version/port# label, join channel btn, show-options btn, join game btn)

        c.gridwidth = (hasChannels) ? 1 : 2;
        gbl.setConstraints(versionOrlocalTCPPortLabel, c);
        mainPane.add(versionOrlocalTCPPortLabel);

        if (hasChannels)
        {
            c.fill = GridBagConstraints.NONE;
            c.gridwidth = 1;
            gbl.setConstraints(jc, c);
            mainPane.add(jc);  // "Join Channel"
            c.fill = GridBagConstraints.BOTH;
        }

        l = new JLabel(" ");
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        gbl.setConstraints(gi, c);
        mainPane.add(gi);  // "Game Info"; stretched to same width as "Practice"

        l = new JLabel();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        gbl.setConstraints(jg, c);
        mainPane.add(jg);  // "Join Game"
        c.fill = GridBagConstraints.BOTH;

        l = new JLabel();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 6

        if (hasChannels)
        {
            l = new JLabel(strings.get("pcli.main.label.channels"));  // "Channels"
            c.gridwidth = 2;
            gbl.setConstraints(l, c);
            mainPane.add(l);

            l = new JLabel(" ");
            c.gridwidth = 1;
            gbl.setConstraints(l, c);
            mainPane.add(l);
        }

        l = new JLabel(strings.get("pcli.main.label.games"));  // "Games"
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        // Row 7

        c.weighty = 1;  // Stretch to fill remainder of extra height

        if (hasChannels)
        {
            c.gridwidth = 2;
            JScrollPane sp = new JScrollPane(chlist);
            gbl.setConstraints(sp, c);
            mainPane.add(sp);

            l = new JLabel();
            c.gridwidth = 1;
            gbl.setConstraints(l, c);
            mainPane.add(l);
        }

        c.gridwidth = GridBagConstraints.REMAINDER;
        JScrollPane sp = new JScrollPane(gmlist);
        gbl.setConstraints(sp, c);
        mainPane.add(sp);

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
     * Handle mouse clicks and keyboard: Wrapped version of actionPerformed() for easier encapsulation.
     * If appropriate, calls {@link #guardedActionPerform_games(Object)}
     * or {@link #guardedActionPerform_channels(Object)} and shows the "cannot join" popup if needed.
     *<P>
     * To help debugging, catches any thrown exceptions and prints them to {@link System#err}.
     *
     * @param target Action source, from ActionEvent.getSource(),
     *     such as {@link #jg}, {@link #ng}, {@link #chlist}, or {@link #gmlist}.
     * @since 1.1.00
     */
    private void guardedActionPerform(Object target)
    {
        try
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
                NotifyDialog.createAndShow(this, (JFrame) null,
                    STATUS_CANNOT_JOIN_THIS_GAME,
                    client.strings.get("base.cancel"), true);

                return;
            }

            if (target == nick)
            { // Nickname TextField
                nick.transferFocus();
            }

        } catch (Throwable thr) {
            System.err.println("-- Error caught in AWT event thread: " + thr + " --");
            thr.printStackTrace(); // will print causal chain, no need to manually iterate
            System.err.println("-- Error stack trace end --");
            System.err.println();
        }
    }

    /**
     * GuardedActionPerform when a channels-related button or field is clicked.
     * If target is {@link #chlist} itself, will call {@link JList#getSelectedValue()}
     * to get which channel name was clicked.
     * @param target Target as in actionPerformed
     * @return True if OK, false if caller needs to show popup "cannot join"
     * @since 1.1.06
     */
    private boolean guardedActionPerform_channels(Object target)
    {
        String ch = null;

        if (target == jc) // "Join Channel" Button
        {
            ch = channel.getText().trim();
            if (ch.length() == 0)
                ch = null;
        }
        else if (target == channel)
        {
            ch = channel.getText().trim();
        }

        if (ch == null)
        {
            JoinableListItem itm = chlist.getSelectedValue();
            if (itm == null)
                return true;
            if (itm.isUnjoinable)
                return false;

            ch = itm.name.trim();
        }

        if (ch.length() == 0)
        {
            return true;
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
            net.putNet(SOCJoinChannel.toCmd
                (client.nickname, (client.gotPassword ? "" : client.password), net.getHost(), ch));
        }
        else
        {
            cf.setVisible(true);
        }

        channel.setText("");
        return true;
    }

    /**
     * {@inheritDoc}
     *<P>
     * This method may set status bar to a hint message if username is empty,
     * such as {@link #NEED_NICKNAME_BEFORE_JOIN}.
     * @see #getValidNickname(boolean)
     * @since 1.1.07
     */
    public boolean readValidNicknameAndPassword()
    {
        client.nickname = getValidNickname(true);  // May set hint message if empty,
                                        // like NEED_NICKNAME_BEFORE_JOIN
        if (client.nickname == null)
           return false;  // not filled in yet

        if (! client.gotPassword)
        {
            client.password = getPassword();  // may be 0-length
            if (client.password == null)
                return false;  // invalid or too long
        }

        return true;
    }

    /**
     * GuardedActionPerform when a games-related button or field is clicked.
     * If target is {@link #gmlist} itself, will call {@link JList#getSelectedValue()}
     * to get which game name was clicked.
     * @param target Target as in actionPerformed
     * @return True if OK, false if caller needs to show popup "cannot join"
     * @since 1.1.06
     */
    private boolean guardedActionPerform_games(Object target)
    {
        String gm;  // May also be 0-length string, if pulled from Lists
        boolean isUnjoinable = false;  // if selecting game from gmList, may become true

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
        else  // "Join Game" Button jg, or game list
        {
            JoinableListItem item = gmlist.getSelectedValue();
            if (item == null)
                return true;
            gm = item.name.trim();  // may be length 0
            isUnjoinable = item.isUnjoinable;
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

            if ((net.practiceServer != null) && (-1 != net.practiceServer.getGameState(gm)))
            {
                opts = net.practiceServer.getGameOptions(gm);  // won't ever need to parse from string on practice server
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
            NewGameOptionsFrame.createAndShow
                (playerInterfaces.get(gm), this, gm, opts, false, true);
            return true;
        }

        // Can we not join that game?
        if (isUnjoinable || ((client.serverGames != null) && client.serverGames.isUnjoinableGame(gm)))
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
                && (net.practiceServer != null)
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

                if (! client.gotPassword)
                {
                    client.password = getPassword();  // may be 0-length
                    if (client.password == null)  // invalid or too long
                        return true;
                }
            }

            if (((target == pg) || (target == pgm)) && (null == net.ex_P))
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
                net.putNet(SOCJoinGame.toCmd
                    (client.nickname, (client.gotPassword ? "" : client.password), net.getHost(), gm));
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
                    ((client.sFeatures.isActive(SOCFeatureSet.SERVER_CHANNELS))
                     ? NEED_NICKNAME_BEFORE_JOIN_2
                     : NEED_NICKNAME_BEFORE_JOIN_G2 );
            else
                // Send first hint message (or re-send first if they've seen _2)
                status.setText
                    ((client.sFeatures.isActive(SOCFeatureSet.SERVER_CHANNELS))
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
     * Validate and return the password textfield contents; may be 0-length, or {@code null} if invalid.
     * Also set {@link #password} field to the value returned from this method.
     * If {@link #gotPassword} already, return current password without checking textfield.
     * If text is too long, sets status text and sets focus to password textfield.
     * @return  The trimmed password field text (may be ""), or {@code null} if invalid or too long
     * @see #readValidNicknameAndPassword()
     * @since 1.1.07
     */
    protected String getPassword()
    {
        if (client.gotPassword)
            return client.password;

        @SuppressWarnings("deprecation")
        String p = pass.getText().trim();

        if (p.length() > SOCAuthRequest.PASSWORD_LEN_MAX)
        {
            status.setText
                (client.strings.get("account.common.password_too_long"));  // "That password is too long."
            pass.requestFocus();
            p = null;
        }

        client.password = p;
        return p;
    }

    /**
     * {@inheritDoc}
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
            net.putNet(SOCAuthRequest.toCmd
                (SOCAuthRequest.ROLE_GAME_PLAYER, client.nickname, client.password,
                 SOCAuthRequest.SCHEME_CLIENT_PLAINTEXT, net.getHost()));

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
                (null, this, null, opts.optionSet, forPracticeServer, false);
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
            //   If server is newer: Ask for any scenario changes since our version.
            //   If same version: Ask for i18n localized scenarios strings if available.
            //   In both cases that request is sent as an empty 'changes' list and MARKER_ANY_CHANGED.

            if ((cliVers != client.sVersion) || client.wantsI18nStrings(false))
                client.getGameMessageMaker().put(new SOCScenarioInfo(changes, true).toCmd(), false);
        }

        opts.newGameWaitingForOpts = true;
        opts.askedDefaultsAlready = true;
        opts.askedDefaultsTime = System.currentTimeMillis();
        client.getGameMessageMaker().put(SOCGameOptionGetDefaults.toCmd(null), forPracticeServer);

        if (gameOptsDefsTask != null)
            gameOptsDefsTask.cancel();
        gameOptsDefsTask = new GameOptionDefaultsTimeoutTask(this, client.tcpServGameOpts, forPracticeServer);
        eventTimer.schedule(gameOptsDefsTask, 5000 /* ms */ );

        // Once options are received, handlers will
        // create and show NewGameOptionsFrame.
    }

    /**
     * {@inheritDoc}
     *<P>
     * Assumes {@link #getValidNickname(boolean) getValidNickname(true)}, {@link #getPassword()}, {@link ClientNetwork#host},
     * and {@link #gotPassword} are already called and valid.
     *
     * @since 1.1.07
     */
    public void askStartGameWithOptions
        (final String gmName, final boolean forPracticeServer,
         final Map<String, SOCGameOption> opts, final Map<String, Object> localPrefs)
    {
        client.putGameReqLocalPrefs(gmName, localPrefs);

        if (forPracticeServer)
        {
            client.startPracticeGame(gmName, opts, true);  // Also sets WAIT_CURSOR
        } else {
            final String pw = (client.gotPassword ? "" : client.password);  // after successful auth, don't need to send
            String askMsg =
                (client.sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
                ? SOCNewGameWithOptionsRequest.toCmd
                    (client.nickname, pw, net.getHost(), gmName, opts)
                : SOCJoinGame.toCmd
                    (client.nickname, pw, net.getHost(), gmName);
            System.err.println("L1314 askStartGameWithOptions at " + System.currentTimeMillis());
            System.err.println("      Got all opts,defaults? " + client.tcpServGameOpts.allOptionsReceived
                + " " + client.tcpServGameOpts.defaultsReceived);
            net.putNet(askMsg);
            System.out.flush();  // for debug print output (temporary)
            status.setText(client.strings.get("pcli.message.talkingtoserv"));  // "Talking to server..."
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            System.err.println("L1320 askStartGameWithOptions done at " + System.currentTimeMillis());
            System.err.println("      sent: " + net.lastMessage_N);
        }
    }

    public void clearWaitingStatus(final boolean clearStatus)
    {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        if (clearStatus)
            status.setText("");  // clear "Talking to server...", etc
    }

    /**
     * Look for active games that we're playing
     *
     * @param fromPracticeServer  Enumerate games from {@link ClientNetwork#practiceServer},
     *     instead of {@link #playerInterfaces}?
     * @return Any found game of ours which is active (state not OVER), or null if none.
     * @see ClientNetwork#anyHostedActiveGames()
     */
    protected SOCPlayerInterface findAnyActiveGame(boolean fromPracticeServer)
    {
        SOCPlayerInterface pi = null;
        int gs;  // gamestate

        Collection<String> gameNames;
        if (fromPracticeServer)
        {
            if (net.practiceServer == null)
                return null;  // <---- Early return: no games if no practice server ----
            gameNames = net.practiceServer.getGameNames();
        } else {
            gameNames = playerInterfaces.keySet();
        }

        for (String tryGm : gameNames)
        {
            if (fromPracticeServer)
            {
                gs = net.practiceServer.getGameState(tryGm);
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
     * app, not applet) with buttons to connect to a server or practice, and
     * {@code canPractice} is true, shows that panel instead of the simpler
     * practice-only message panel.
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

        if (hasConnectOrPractice && canPractice)
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
     * {@link #initMainPanelLayout(boolean, SOCFeatureSet) initMainPanelLayout(false, feats)}
     * to complete layout of the Main Panel with the server's version and active features.
     */
    public void showVersion
        (final int vers, final String versionString, final String buildString, final SOCFeatureSet feats)
    {
        if (null == net.localTCPServer)
        {
            versionOrlocalTCPPortLabel.setForeground(SOCPlayerClient.MISC_LABEL_FG_OFF_WHITE);
            versionOrlocalTCPPortLabel.setText(client.strings.get("pcli.main.version", versionString));  // "v {0}"
            versionOrlocalTCPPortLabel.setToolTipText
                (client.strings.get("pcli.main.version.tip", versionString, buildString,
                     Version.version(), Version.buildnum()));
                     // "Server version is {0} build {1}; client is {2} bld {3}"
        }

        initMainPanelLayout(false, feats);  // complete the layout as appropriate for server
        validate();

        if ((net.practiceServer == null) && (vers < SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
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

    public void setNickname(final String nm)
    {
        nick.setText(nm);
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
     * Add a joinable new channel or game.
     *
     * @param thing  the thing to add to the list
     * @param lst    the list
     */
    public void addToList(String thing, JList<JoinableListItem> lst)
    {
        final JoinableListItem item = new JoinableListItem(thing, false);
        final DefaultListModel<JoinableListItem> lm = (DefaultListModel<JoinableListItem>) lst.getModel();

        if (lm.get(0).equals(JoinableListItem.BLANK))
        {
            lm.set(0, item);
            lst.setSelectedIndex(0);
        } else {
            lm.addElement(item);
        }
    }

    /**
     * Delete a list item.
     * Recreate "blank" item if list becomes empty.
     * Otherwise select a remaining item.
     *
     * @param thing   name of the thing to remove
     * @param lst     the list
     */
    public void deleteFromList(String thing, JList<JoinableListItem> lst)
    {
        final DefaultListModel<JoinableListItem> lm = (DefaultListModel<JoinableListItem>) lst.getModel();

        if (lm.size() == 1)
        {
            if (lm.get(0).equals(thing))
            {
                lm.set(0, JoinableListItem.BLANK);  // keep blank item
                lst.clearSelection();
            }

            return;
        }

        lm.removeElement(new JoinableListItem(thing, false));

        if (lst.getSelectedIndex() == -1)
        {
            final int c = lm.size();
            if (c > 0)
                lst.setSelectedIndex(c - 1);
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
            cardLayout.show(SwingMainDisplay.this, MAIN_PANEL);
            validate();

            status.setText
                ((client.sFeatures.isActive(SOCFeatureSet.SERVER_CHANNELS))
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

    public void chatMessageBroadcast(String message)
    {
        for (ChannelFrame fr : channels.values())
        {
            fr.print("::: " + message + " :::");
        }
    }

    public void chatMessageReceived(String channelName, String nickname, String message)
    {
        ChannelFrame fr = channels.get(channelName);

        if (fr != null)
        {
            if (! client.onIgnoreList(nickname))
            {
                fr.print(nickname + ": " + message);
            }
        }
    }

    public void dialogClosed(final NewGameOptionsFrame ngof)
    {
        if (ngof == newGameOptsFrame)
            newGameOptsFrame = null;
    }

    public void leaveGame(SOCGame game)
    {
        playerInterfaces.remove(game.getName());
    }

    public PlayerClientListener gameJoined(SOCGame game, final Map<String, Object> localPrefs)
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

        SOCPlayerInterface pi = new SOCPlayerInterface(game.getName(), SwingMainDisplay.this, game, localPrefs);
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
            (null, SwingMainDisplay.this, (String) null, opts.optionSet, isPractice, false);
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
                    (playerInterfaces.get(gameInfoWaiting), SwingMainDisplay.this,
                     gameInfoWaiting, gameOpts, isPractice, true);
            }
            else if (newGameWaiting)
            {
                synchronized(opts)
                {
                    opts.newGameWaitingForOpts = false;
                }
                newGameOptsFrame = NewGameOptionsFrame.createAndShow
                    (null, SwingMainDisplay.this, (String) null, opts.optionSet, isPractice, false);
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

        final JoinableListItem item = new JoinableListItem(gameName, cannotJoin);
        final DefaultListModel<JoinableListItem> lm = (DefaultListModel<JoinableListItem>) gmlist.getModel();

        if ((! lm.isEmpty()) && (lm.get(0).equals(JoinableListItem.BLANK)))
        {
            lm.set(0, item);
            gmlist.setSelectedIndex(0);
            jg.setEnabled(true);
            gi.setEnabled((net.practiceServer != null)
                || (client.sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS));
        } else {
            lm.addElement(item);
        }
    }

    public boolean deleteFromGameList(String gameName, final boolean isPractice, final boolean withUnjoinablePrefix)
    {
        final DefaultListModel<JoinableListItem> lm = (DefaultListModel<JoinableListItem>) gmlist.getModel();

        if (lm.size() == 1)
        {
            if (lm.get(0).equals(gameName))
            {
                lm.set(0, JoinableListItem.BLANK);  // keep blank item
                gmlist.clearSelection();

                if ((! isPractice) && (client.serverGames != null))
                {
                    client.serverGames.deleteGame(gameName);  // may not be in there
                }

                gi.setEnabled(false);

                return true;
            }

            return false;
        }

        boolean found = lm.removeElement(new JoinableListItem(gameName, withUnjoinablePrefix));

        if (gmlist.getSelectedIndex() == -1)
        {
            final int c = lm.size();
            if (c > 0)
                gmlist.setSelectedIndex(c - 1);  // one of the remaining games, or blank item if none
        }

        if (found && (! isPractice) && (client.serverGames != null))
        {
            client.serverGames.deleteGame(gameName);  // may not be in there
        }

        return found;
    }

    /**
     * {@inheritDoc}
     *<P>
     * Before v2.0.00 this method was {@code chSend}.
     */
    public void sendToChannel(String ch, String mes)
    {
        if (! doLocalCommand(ch, mes))
        {
            net.putNet(SOCChannelTextMsg.toCmd(ch, client.nickname, mes));
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

    public void printIgnoreList(SOCPlayerInterface pi)
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
     * {@inheritDoc}
     *<P>
     * If parent is a Frame, sets titlebar to show "server" and port number.
     * Shows port number in {@link #versionOrlocalTCPPortLabel}.
     */
    public void startLocalTCPServer(final int tport)
        throws IllegalArgumentException, IllegalStateException
    {
        if (net.localTCPServer != null)
        {
            return;  // Already set up
        }
        if (net.isConnected())
        {
            throw new IllegalStateException("Already connected to " + net.getHost());
        }
        if (tport < 1)
        {
            throw new IllegalArgumentException("Port must be positive: " + tport);
        }

        // May take a while to start server.
        // At end of method, we'll clear this cursor.
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        if (! net.initLocalServer(tport))
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
                    (SwingMainDisplay.this,
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
        versionOrlocalTCPPortLabel.setToolTipText
            (strings.get("pcli.localserver.running.tip", tportStr, Version.version(), Version.buildnum()));
                // "You are running a server on TCP port {0}. Version {1} bld {2}"
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
        net.connect("localhost", tport);  // I18N: no need to localize this hostname

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


    /**
     * A game or channel in the displayed {@link SwingMainDisplay#gmlist} or {@link SwingMainDisplay#chlist}.
     * Client may or may not be able to join this game or channel.
     *<P>
     * <B>I18N:</B> {@link #toString()} uses {@link SOCPlayerClient#GAMENAME_PREFIX_CANNOT_JOIN}
     * and assumes it's localized.
     * @since 2.0.00
     */
    private static class JoinableListItem
    {
        /** Blank dummy item; {@link #name} is 1 space " " */
        public static final JoinableListItem BLANK = new JoinableListItem(" ", false);

        public final String name;
        public final boolean isUnjoinable;

        /**
         * Make a JoinableListItem.
         * @throws IllegalArgumentException if {@code name} is {@code null}
         */
        public JoinableListItem(final String name, final boolean isUnjoinable)
            throws IllegalArgumentException
        {
            if (name == null)
                throw new IllegalArgumentException();

            this.name = name;
            this.isUnjoinable = isUnjoinable;
        }

        /**
         * Item {@link #name}. If {@link #isUnjoinable}, will be prefixed with
         * {@link SOCPlayerClient#GAMENAME_PREFIX_CANNOT_JOIN}.
         */
        public String toString()
        {
            return (isUnjoinable)
                ? (SOCPlayerClient.GAMENAME_PREFIX_CANNOT_JOIN + name)
                : name;
        }

        /** Check list item equality to a {@link String} or another item (only {@link #name} field is checked). */
        @Override
        public boolean equals(Object i)
        {
            if (i == null)
                return false;
            if (i == this)
                return true;
            if (i instanceof JoinableListItem)
                return name.equals(((JoinableListItem) i).name);
            else if (i instanceof String)
                return name.equals(i);
            else
                return super.equals(i);
        }
    }   // JoinableListItem


    /** React to windowOpened, windowClosing events for SwingMainDisplay's Frame. */
    private static class ClientWindowAdapter extends WindowAdapter
    {
        private final SwingMainDisplay md;

        public ClientWindowAdapter(SwingMainDisplay md)
        {
            this.md = md;
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
                piActive = md.findAnyActiveGame(false);

            if (piActive != null)
            {
                SOCQuitAllConfirmDialog.createAndShow(piActive.getMainDisplay(), piActive);
                return;
            }
            boolean canAskHostingGames = false;
            boolean isHostingActiveGames = false;

            // Are we running a server?
            ClientNetwork cnet = md.getClient().getNet();
            if (cnet.isRunningLocalServer())
                isHostingActiveGames = cnet.anyHostedActiveGames();

            if (isHostingActiveGames)
            {
                // If we have GUI, ask whether to shut down these games
                Container c = md.getParent();
                if ((c != null) && (c instanceof Frame))
                {
                    canAskHostingGames = true;
                    SOCQuitAllConfirmDialog.createAndShow(md, (Frame) c);
                }
            }

            if (! canAskHostingGames)
            {
                // Just quit.
                md.getClient().getNet().putLeaveAll();
                System.exit(0);
            }
        }

        /**
         * Set focus to Nickname field
         */
        @Override
        public void windowOpened(WindowEvent evt)
        {
            if (! md.hasConnectOrPractice)
                md.nick.requestFocus();
        }

    }  // nested class ClientWindowAdapter


    /**
     * TimerTask used soon after client connect, to prevent waiting forever for
     * {@link SOCGameOptionInfo game options info}
     * (assume slow connection or server bug).
     * Set up when sending {@link SOCGameOptionGetInfos GAMEOPTIONGETINFOS}.
     *<P>
     * When timer fires, assume no more options will be received. Call
     * {@link MessageHandler#handleGAMEOPTIONINFO(SOCGameOptionInfo, boolean) handleGAMEOPTIONINFO("-",false)}
     * to trigger end-of-list behavior at client.
     * @author jdmonin
     * @since 1.1.07
     */
    private static class GameOptionsTimeoutTask extends TimerTask
    {
        public SwingMainDisplay pcli;
        public ServerGametypeInfo srvOpts;

        public GameOptionsTimeoutTask (SwingMainDisplay c, ServerGametypeInfo opts)
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
            pcli.getClient().getMessageHandler().handleGAMEOPTIONINFO
                (new SOCGameOptionInfo(new SOCGameOption("-"), Version.versionNumber(), null), false);
        }

    }  // GameOptionsTimeoutTask


    /**
     * TimerTask used when new game is asked for, to prevent waiting forever for
     * {@link SOCGameOption game option defaults}.
     * (in case of slow connection or server bug).
     * Set up when sending {@link SOCGameOptionGetDefaults GAMEOPTIONGETDEFAULTS}
     * in {@link SwingMainDisplay#gameWithOptionsBeginSetup(boolean, boolean)}.
     *<P>
     * When timer fires, assume no defaults will be received.
     * Display the new-game dialog.
     * @author jdmonin
     * @since 1.1.07
     */
    private static class GameOptionDefaultsTimeoutTask extends TimerTask
    {
        public SwingMainDisplay pcli;
        public ServerGametypeInfo srvOpts;
        public boolean forPracticeServer;

        public GameOptionDefaultsTimeoutTask (SwingMainDisplay c, ServerGametypeInfo opts, boolean forPractice)
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


}


