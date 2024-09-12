/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2019-2024 Jeremy D Monin <jeremy@nand.net>
 * Extracted in 2019 from SOCPlayerClient.java, so:
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file copyright (C) 2007-2019 Jeremy D Monin <jeremy@nand.net>
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
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.SystemColor;
import java.awt.Toolkit;
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
import java.util.Set;
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
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCScenario;
import soc.game.SOCVersionedItem;
import soc.message.SOCAuthRequest;
import soc.message.SOCChannelTextMsg;
import soc.message.SOCGameOptionGetDefaults;
import soc.message.SOCGameOptionGetInfos;
import soc.message.SOCGameOptionInfo;
import soc.message.SOCGameStats;
import soc.message.SOCJoinChannel;
import soc.message.SOCJoinGame;
import soc.message.SOCLocalizedStrings;
import soc.message.SOCMessage;
import soc.message.SOCNewGameWithOptions;
import soc.message.SOCNewGameWithOptionsRequest;
import soc.message.SOCScenarioInfo;
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
 * Should be added directly to a {@link JFrame} or other {@link Frame}, not a subcontainer.
 *<P>
 * Also holds some GUI utility methods like {@link #checkDisplayScaleFactor(Component)}
 * and {@link #isOSColorHighContrast()}.
 *<P>
 * Before v2.0.00, most of these fields and methods were part of the main {@link SOCPlayerClient} class.
 * Also converted from AWT to Swing in v2.0.00.
 *
 * @since 2.0.00
 */
@SuppressWarnings("serial")
public class SwingMainDisplay extends JPanel implements MainDisplay
{
    /**
     * Recommended minimum height of the screen (display): 768 pixels.
     * {@link #checkDisplayScaleFactor(Component)} will return a high-DPI scaling factor
     * if screen is at least twice this height.
     * @see #PROP_JSETTLERS_UI_SCALE
     * @since 2.0.00
     */
    public static final int DISPLAY_MIN_UNSCALED_HEIGHT = 768;

    /**
     * System property {@code "jsettlers.uiScale"} for UI scaling override ("high-DPI") if needed
     * for {@link #checkDisplayScaleFactor(Component)}.
     *<P>
     * At startup, overrides optional user preference {@link SOCPlayerClient#PREF_UI_SCALE_FORCE}.
     *<P>
     * Name is based on similar {@code "sun.java2d.uiScale"},
     * but that property might not be available for all java versions.
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_UI_SCALE = "jsettlers.uiScale";

    /**
     * System property {@code "jsettlers.uiContrastMode"} to force high-contrast dark or light mode
     * if needed for accessibility. Recognized values: {@code "light"} or {@code "dark"} background.
     * Name is based on {@link #PROP_JSETTLERS_UI_SCALE}.
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_UI_CONTRAST_MODE = "jsettlers.uiContrastMode";

    /**
     * The classic JSettlers goldenrod dialog background color; pale yellow-orange tint #FFE6A2.
     * Typically used with foreground {@link Color#BLACK}, like in game/chat text areas,
     * {@link TradePanel}, {@link MessagePanel}, and {@link AskDialog}.
     * @see #getForegroundBackgroundColors(boolean)
     * @see #JSETTLERS_BG_GREEN
     * @since 2.0.00
     */
    public static final Color DIALOG_BG_GOLDENROD = new Color(255, 230, 162);

    /**
     * The classic JSettlers green background color; green tone #61AF71.
     * Typically used with foreground color {@link Color#BLACK},
     * like in {@link SwingMainDisplay}'s main panel.
     * Occasionally used with {@link #MISC_LABEL_FG_OFF_WHITE}.
     * @see #getForegroundBackgroundColors(boolean)
     * @see #DIALOG_BG_GOLDENROD
     * @since 2.0.00
     */
    public static final Color JSETTLERS_BG_GREEN = new Color(97, 175, 113);

    /**
     * For miscellaneous labels, off-white foreground color #FCFBF3.
     * Typically used on {@link #JSETTLERS_BG_GREEN}.
     * @see #getForegroundBackgroundColors(boolean)
     * @since 2.0.00
     */
    public static final Color MISC_LABEL_FG_OFF_WHITE = new Color(252, 251, 243);

    /** main panel, in cardlayout */
    private static final String MAIN_PANEL = "main";

    /**
     * Message main panel for showing errors, in cardlayout.
     * @see #showErrorPanel(String, boolean)
     */
    private static final String MESSAGE_PANEL = "message";

    /** Connect-or-practice panel (if jar launch), in cardlayout.
     * Panel field is {@link #connectOrPracticePane}.
     * Shown at startup if {@link #hasConnectOrPractice}.
     * @since 1.1.00
     */
    private static final String CONNECT_OR_PRACTICE_PANEL = "connOrPractice";

    /**
     * For practice games, reminder message for network problems.
     * @since 1.1.00
     */
    public final String NET_UNAVAIL_CAN_PRACTICE_MSG;

    /**
     * Hint message if they try to join a game or channel without entering a nickname.
     *
     * @see #NEED_NICKNAME_BEFORE_JOIN_2
     * @see #NEED_NICKNAME_BEFORE_JOIN_G
     * @since 1.1.00
     */
    public final String NEED_NICKNAME_BEFORE_JOIN;

    /**
     * Stronger hint message if they still try to join a game or channel without entering a nickname.
     *
     * @see #NEED_NICKNAME_BEFORE_JOIN
     * @see #NEED_NICKNAME_BEFORE_JOIN_G2
     * @since 1.1.00
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

    /**
     * Tracking flag to make sure {@link #scaleUIManagerFonts(int)} is done only once.
     * @since 2.0.00
     */
    private static boolean didScaleUIManagerFonts;

    private final SOCPlayerClient client;

    private final ClientNetwork net;

    /**
     * For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @see #checkDisplayScaleFactor(Component)
     * @see #PROP_JSETTLERS_UI_SCALE
     */
    private final int displayScale;

    /**
     * True if {@link #getForegroundBackgroundColors(boolean, boolean)} has been called to
     * determine {@link #isOSColorHighContrast}, {@link #osColorText}, {@link #osColorTextBG}.
     * @since 2.0.00
     */
    private static boolean hasDeterminedOSColors;

    /**
     * Is the OS using high-contrast or reverse-video colors (accessibility mode)?
     * If {@link #hasDeterminedOSColors}, has been set in {@link #getForegroundBackgroundColors(boolean, boolean)}.
     * @since 2.0.00
     */
    private static boolean isOSColorHighContrast;  // TODO later enhancement: + isOSDarkBackground

    /**
     * System theme's default text colors, from {@link SystemColor#textText} and {@link SystemColor#text}.
     * If {@link #hasDeterminedOSColors}, has been set in {@link #getForegroundBackgroundColors(boolean, boolean)}.
     * Lazy: avoids static initializer to avoid problems for console-only client jar usage.
     * @since 2.0.00
     */
    private static Color osColorText, osColorTextBG;

    /**
     * Foreground color for miscellaneous label text; typically {@link #MISC_LABEL_FG_OFF_WHITE}.
     * @since 2.0.00
     */
    private final Color miscLabelFGColor;

    /**
     * The player interfaces for all the {@link SOCPlayerClient#games} we're playing.
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
     * @since 1.1.00
     */
    protected boolean hasJoinedServer;

    /**
     * If true, at startup we'll give the user a choice to
     * connect to a server, start a local server,
     * or a local practice game.
     * Used for when we're started from a jar, or
     * from the command line with no arguments.
     * Uses {@link #connectOrPracticePane}.
     *
     * @see #cardLayout
     * @since 1.1.00
     */
    protected final boolean hasConnectOrPractice;

    /**
     * Is set up in {@link #initVisualElements()}.
     * Key for {@link #cardLayout} is {@link #CONNECT_OR_PRACTICE_PANEL}.
     * @see #hasConnectOrPractice
     * @since 1.1.00
     */
    protected SOCConnectOrPracticePanel connectOrPracticePane;

    /**
     * The currently showing new-game options frame, or null
     * @since 1.1.07
     */
    public NewGameOptionsFrame newGameOptsFrame = null;

    /**
     * Currently showing {@link NewGameOptionsFrame NGOF}s from user clicking "Game Info" button.
     * Uses Hashtable for thread-safety, in case a non-UI thread might want to update info in such a window.
     * @since 2.7.00
     */
    private final Hashtable<String, NewGameOptionsFrame> gameInfoFrames = new Hashtable<>();

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

    /**
     * Nickname (username) to connect to server and use in games.
     * After auth, once a game or channel is successfully joined,
     * client calls {@link JTextComponent#setEditable(boolean) nick.setEditable(false)}.
     *<P>
     * Default value is set in {@link #guardedActionPerform_games(Object)} if still blank,
     * so Practice game can be joined from {@link SOCConnectOrPracticePanel} which doesn't show this field.
     */
    protected JTextField nick;

    /** Password for {@link #nick} while connecting to server, or blank */
    protected JPasswordField pass;

    /**
     * Status from server, or progress/error message updated by client.
     * @see #showStatus(String, boolean, boolean)
     * @see #clearWaitingStatus(boolean)
     * @see #statusOKText
     */
    protected JTextField status;

    /**
     * If most recent {@link #showStatus(String, boolean, boolean)} was OK, its text.
     * Otherwise {@code null}.
     * @see #clearWaitingStatus(boolean)
     * @since 2.0.00
     */
    private String statusOKText;

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
     * @since 1.1.00
     */
    protected JButton pg;

    /**
     * "Game Info" button, shows a game's {@link SOCGameOption}s
     * and (if server is new enough) overall status and duration.
     *<P>
     * Renamed in 2.0.00 to 'gi'; previously 'so' Show Options.
     * @since 1.1.07
     */
    protected JButton gi;

    /**
     * Local Server indicator in main panel: blank, or 'server is running' if
     * {@link ClientNetwork#localTCPServer} has been started.
     * If so, localTCPServer's port number is shown in {@link #versionOrlocalTCPPortLabel}.
     * @since 1.1.00
     */
    private JLabel localTCPServerLabel;

    /**
     * When connected to a remote server, shows its version number.
     * When running {@link ClientNetwork#localTCPServer}, shows that
     * server's port number (see also {@link #localTCPServerLabel}).
     * In either mode, has a tooltip with more info.
     *<P>
     * Before v1.1.06 this field was {@code localTCPPortLabel}.
     *
     * @since 1.1.00
     */
    private JLabel versionOrlocalTCPPortLabel;

    /**
     * Error message display in {@link #MESSAGE_PANEL}.
     * Updated by {@link #showErrorPanel(String, boolean)}.
     */
    protected JLabel messageLabel;

    /**
     * Secondary message at top of {@link #MESSAGE_PANEL}.
     * Updated by {@link #showErrorPanel(String, boolean)}.
     * @since 1.1.00
     */
    protected JLabel messageLabel_top;

    /**
     * Practice Game button in {@link #MESSAGE_PANEL}.
     * @since 1.1.00
     */
    protected JButton pgm;

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
     * @param displayScaleFactor  Display scaling factor to use (1 if not high-DPI); caller should
     *     call {@link #checkDisplayScaleFactor(Component)} with the Frame to which this display will be added
     * @throws IllegalArgumentException if {@code client} is null or {@code displayScaleFactor} &lt; 1
     */
    public SwingMainDisplay
        (boolean hasConnectOrPractice, final SOCPlayerClient client, final int displayScaleFactor)
        throws IllegalArgumentException
    {
        if (client == null)
            throw new IllegalArgumentException("null client");
        if (displayScaleFactor < 1)
            throw new IllegalArgumentException("displayScaleFactor: " + displayScaleFactor);

        this.hasConnectOrPractice = hasConnectOrPractice;
        this.client = client;
        displayScale = displayScaleFactor;
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
        final Color[] colors = SwingMainDisplay.getForegroundBackgroundColors(false, false);
        if (colors != null)
        {
            setBackground(colors[2]);  // JSETTLERS_BG_GREEN
            setForeground(colors[0]);  // Color.BLACK
            miscLabelFGColor = colors[1];
        } else {
            miscLabelFGColor = osColorText;
        }
    }

    /**
     * Get foreground and background colors to use for a new window or panel, unless OS
     * is using high-contrast or reverse-video colors (accessibility).
     *<P>
     * The first time this is called, attempts to determine if the OS is using
     * a high-contrast or reverse-video mode (might detect only on Windows);
     * if so, windows and panels should probably use the OS default colors.
     *<P>
     * To check whether the OS is using such a mode, call {@link #isOSColorHighContrast()}.
     *
     * @param isForLightBG  True for a light background like {@link #DIALOG_BG_GOLDENROD},
     *     false for a dark background like {@link #JSETTLERS_BG_GREEN}
     * @param wantSystemColors  True to return the default system-theme colors (not always accurate)
     *     from {@link SystemColor#textText} and {@link SystemColor#text} instead of JSettlers colors.
     *     If true: Won't return null, and ignores {@code isForLightBG}.
     * @return Array of 3 colors: { Main foreground, misc foreground, background },
     *     or {@code null} if OS is using high-contrast or reverse-video colors.
     *     If background is dark, misc foreground is {@link #MISC_LABEL_FG_OFF_WHITE}
     *     instead of same as main foreground {@link Color#BLACK}.
     * @since 2.0.00
     */
    public static final Color[] getForegroundBackgroundColors
        (final boolean isForLightBG, final boolean wantSystemColors)
    {
        if (! hasDeterminedOSColors)
        {
            String propValue = System.getProperty(PROP_JSETTLERS_UI_CONTRAST_MODE);
            if (propValue != null)
            {
                if (propValue.equalsIgnoreCase("dark"))
                {
                    osColorTextBG = Color.BLACK;
                    osColorText = Color.WHITE;
                }
                else if (propValue.equalsIgnoreCase("light"))
                {
                    osColorTextBG = Color.WHITE;
                    osColorText = Color.BLACK;
                }
                else
                {
                    System.err.println
                        ("* Unrecognized value for " + PROP_JSETTLERS_UI_CONTRAST_MODE + ": " + propValue);
                    propValue = null;
                }

                if (propValue != null)
                {
                    isOSColorHighContrast = true;
                    System.err.println
                        ("High-contrast mode enabled using " + PROP_JSETTLERS_UI_CONTRAST_MODE + '=' + propValue);
                }
            }

            if (propValue == null)
            {
                osColorTextBG = SystemColor.text;
                osColorText = SystemColor.textText;

                Object o = Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on");
                if (o instanceof Boolean)  // false if null
                    isOSColorHighContrast = (Boolean) o;

                if (! isOSColorHighContrast)
                {
                    // check for reverse video
                    final float brightnessBG =
                        (osColorTextBG.getRed() + osColorTextBG.getGreen() + osColorTextBG.getBlue()) / (3 * 255f),
                      brightnessText =
                        (osColorText.getRed() + osColorText.getGreen() + osColorText.getBlue()) / (3 * 255f);

                    isOSColorHighContrast = (brightnessText > brightnessBG) && (brightnessBG <= 0.3f);
                    if (isOSColorHighContrast)
                        System.err.println("High-contrast mode detected (dark background).");
                } else {
                    System.err.println("High-contrast mode detected.");
                }
            }

            hasDeterminedOSColors = true;
        }

        if (wantSystemColors)
            return new Color[]{ osColorText, osColorText, osColorTextBG };

        if (isOSColorHighContrast)
            return null;

        if (isForLightBG)
        {
            return new Color[]{ Color.BLACK, Color.BLACK, DIALOG_BG_GOLDENROD };
        } else {
            return new Color[]{ Color.BLACK, MISC_LABEL_FG_OFF_WHITE, JSETTLERS_BG_GREEN };
        }
    }

    /**
     * Is the OS using high-contrast or reverse-video colors (accessibility mode)? If so, to get those colors call
     * {@link #getForegroundBackgroundColors(boolean, boolean) getForegroundBackgroundColors(false, true)}.
     * @return true if high-contrast or reverse instead of usual JSettlers colors
     * @since 2.0.00
     */
    public static final boolean isOSColorHighContrast()
    {
        if (! hasDeterminedOSColors)
            getForegroundBackgroundColors(false, true);

        return isOSColorHighContrast;
    }

    /**
     * For use on high-DPI displays, determine if the screen resolution is tall enough that our unscaled (1x)
     * component sizes, window sizes, and font sizes would be too small to comfortably use.
     *<P>
     * Returns a high-DPI scaling factor if screen height is at least twice {@link #DISPLAY_MIN_UNSCALED_HEIGHT}.
     * Called at startup, available afterwards from {@link #getDisplayScaleFactor()}.
     *<P>
     * After determining scale here, be sure to call {@link #scaleUIManagerFonts(int)} once.
     *<P>
     * If user preference {@link SOCPlayerClient#PREF_UI_SCALE_FORCE} or system property {@link #PROP_JSETTLERS_UI_SCALE}
     * are set to an integer &gt;= 1, they override this display check and that value will be returned,
     * even if {@code c} is null or hasn't been added to a Container.
     *
     * @param c  Component; not {@code null}
     * @return scaling factor based on screen height divided by {@link #DISPLAY_MIN_UNSCALED_HEIGHT},
     *     or 1 if cannot determine height
     * @throws IllegalStateException  if {@code c} isn't a top-level Container and hasn't yet been added to a Container
     * @throws NullPointerException  if {@code c} is null
     * @since 2.0.00
     */
    public static final int checkDisplayScaleFactor(final Component c)
        throws IllegalStateException, NullPointerException
    {
        try
        {
            final String propValue = System.getProperty(PROP_JSETTLERS_UI_SCALE);
            if ((propValue != null) && (propValue.length() > 0))
            {
                try
                {
                    int uiScale = Integer.parseInt(propValue);
                    if (uiScale > 0)
                    {
                        System.err.println("L678: checkDisplayScaleFactor prop override -> scale=" + uiScale);  // TODO later: remove debug print
                        return uiScale;
                    }
                } catch (NumberFormatException e) {}
            }
        } catch (SecurityException e) {}

        int uiScaleForce = UserPreferences.getPref(SOCPlayerClient.PREF_UI_SCALE_FORCE, 0);
        if ((uiScaleForce > 0) && (uiScaleForce <= 3))
        {
            System.err.println("L688: checkDisplayScaleFactor user-pref override -> scale=" + uiScaleForce);  // TODO later: remove debug print
            return uiScaleForce;
        }

        final GraphicsConfiguration gconf = c.getGraphicsConfiguration();
        if (gconf == null)
            throw new IllegalStateException("needs container");

        int scale = 1;
        try
        {
            final int screenHeight = gconf.getDevice().getDisplayMode().getHeight();
            System.err.print("L549: checkDisplayScaleFactor got screenHeight=" + screenHeight);  // TODO later: remove debug print
            if (screenHeight >= (2 * DISPLAY_MIN_UNSCALED_HEIGHT))
                scale = screenHeight / DISPLAY_MIN_UNSCALED_HEIGHT;
        } catch (NullPointerException e) {}

        System.err.println(" -> scale=" + scale);  // TODO later: remove debug print
        return scale;
    }

    /**
     * If not already done, scale the UI look-and-feel font sizes for use on high-DPI displays.
     * Helps keep buttons, labels, text fields legible.
     * Sets a flag to ensure scaling is done only once, to avoid setting 4x or 8x font sizes.
     * Assumes {@link UIManager#setLookAndFeel(String)} has already been called.
     * @param displayScale  Scale factor to use, from {@link #checkDisplayScaleFactor(Component)};
     *     if 1, makes no changes to font sizes.
     * @since 2.0.00
     */
    public static final void scaleUIManagerFonts(final int displayScale)
    {
        if ((displayScale <= 1) || didScaleUIManagerFonts)
            return;

        // Adapted from MadProgrammer's 2014-11-12 answer to
        // https://stackoverflow.com/questions/26877517/java-swing-on-high-dpi-screen

        final Set<Object> keySet = UIManager.getLookAndFeelDefaults().keySet();
        for (final Object key : keySet.toArray(new Object[keySet.size()]))
        {
            if ((key == null) || ! key.toString().toLowerCase().contains("font"))
                continue;

            Font font = UIManager.getDefaults().getFont(key);
            if (font != null)
                UIManager.put(key, font.deriveFont((float) (font.getSize2D() * displayScale)));
        }

        didScaleUIManagerFonts = true;
    }

    public SOCPlayerClient getClient()
    {
        return client;
    }

    public final GameMessageSender getGameMessageSender()
    {
        return client.getGameMessageSender();
    }

    public final Container getGUIContainer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     *<P>
     * Determined at startup by {@link #checkDisplayScaleFactor(Component)}.
     */
    public final int getDisplayScaleFactor()
    {
        return displayScale;
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

        setFont(new Font("SansSerif", Font.PLAIN, 12 * displayScale));

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

        if (SOCPlayerClient.IS_PLATFORM_WINDOWS && ! isOSColorHighContrast)
        {
            // swing on win32 needs all JButtons to inherit their bgcolor from panel, or they get gray corners
            ng.setBackground(null);
            jc.setBackground(null);
            jg.setBackground(null);
            pg.setBackground(null);
            gi.setBackground(null);
        }

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
        messageLabel.setForeground(miscLabelFGColor);  // MISC_LABEL_FG_OFF_WHITE
        messagePane.add(messageLabel, BorderLayout.CENTER);

        // bottom of message pane: practice-game button
        pgm = new JButton(strings.get("pcli.message.practicebutton"));  // "Practice Game (against robots)"
        pgm.setVisible(false);
        if (SOCPlayerClient.IS_PLATFORM_WINDOWS && ! isOSColorHighContrast)
            pgm.setBackground(null);
        messagePane.add(pgm, BorderLayout.SOUTH);
        pgm.addActionListener(actionListener);

        // all together now...
        cardLayout = new CardLayout();
        setLayout(cardLayout);

        connectOrPracticePane = new SOCConnectOrPracticePanel(this);
        if (hasConnectOrPractice)
            add (connectOrPracticePane, CONNECT_OR_PRACTICE_PANEL);  // shown first
        add(messagePane, MESSAGE_PANEL); // shown first unless cpPane
        if (! hasConnectOrPractice)
            add (connectOrPracticePane, CONNECT_OR_PRACTICE_PANEL);
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
            final int bsize = 4 * displayScale;
            mainPane.setBorder(BorderFactory.createEmptyBorder(0, bsize, bsize, bsize));
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
    public void connect(String chost, int cport, String cpass, String cuser)
    {
        connectOrPracticePane.setServerHostPort(chost, cport);
        nick.setEditable(true);  // in case of reconnect. Will disable after starting or joining a game.
        pass.setEditable(true);
        pass.setText(cpass);
        nick.setText(cuser);
        nick.requestFocusInWindow();
        if ((cuser != null) && (cuser.trim().length() > 0))
            ng.setEnabled(true);

        cardLayout.show(this, MESSAGE_PANEL);
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
     * @return True if OK or no action taken, false if caller needs to show popup "cannot join"
     * @see #guardedActionPerform_games(Object)
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
        } else if (! ch.isEmpty()) {
            String errMsg = checkNameFormat(ch);

            if (errMsg != null)
            {
                status.setText(errMsg);
                channel.requestFocusInWindow();
                ch = "";
            }
        }

        if (ch.isEmpty())
        {
            return true;
        }

        ChannelFrame cf = channels.get(ch);

        if (cf == null)
        {
            if (channels.isEmpty())
            {
                // Prepare to auth.
                // May set hint message if empty, like NEED_NICKNAME_BEFORE_JOIN
                if (! readValidNicknameAndPassword())
                    return true;  // not filled in yet
            }

            status.setText(client.strings.get("pcli.message.talkingtoserv"));  // "Talking to server..."
            net.putNet(SOCJoinChannel.toCmd
                (client.nickname, (client.gotPassword ? "" : client.password), SOCMessage.EMPTYSTR, ch));
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
     * Calls {@link #getValidNickname(boolean) getValidNickname(false)} which may set status bar to
     * a hint message if username/nickname field is empty.
     * @since 1.1.07
     */
    public boolean readValidNicknameAndPassword()
    {
        // May set hint message if empty,
        // like NEED_NICKNAME_BEFORE_JOIN
        if (getValidNickname(false) == null)
            return false;  // nickname field blank or invalid, client.nickname not set yet

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
     * @return True if OK or if feedback was handled here; false if caller needs to show popup "cannot join"
     * @see #guardedActionPerform_channels(Object)
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
            // (v1.x used DEFAULT_PLAYER_NAME const field here)

            if (0 == nick.getText().trim().length())
                nick.setText(client.strings.get("default.name.practice.player"));  // "Player"
        }
        else if (target == ng)  // "New Game" button
        {
            if (null != getValidNickname(true))  // name check, but don't set nick field yet
                gameWithOptionsBeginSetup(false, false);  // Also may set status, WAIT_CURSOR
            else
                nick.requestFocusInWindow();  // Not a valid player nickname

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

            boolean isPractice = false;
            SOCGameOptionSet opts = null;

            if ((net.practiceServer != null) && (net.practiceServer.getGame(gm) != null))
            {
                isPractice = true;
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
            final NewGameOptionsFrame ngof = gameInfoFrames.get(gm);
            if (ngof != null)
                ngof.toFront();
            else
                showGameOptions(gm, opts, isPractice);

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
                // May set hint message if empty,
                // like NEED_NICKNAME_BEFORE_JOIN
                if (getValidNickname(false) == null)
                    return true;  // nickname blank or invalid, client.nickname not set yet

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

                // Check nickname field, unless is read-only because of previous successful auth
                if (nick.isEditable() && (getValidNickname(false) == null))
                {
                    nick.requestFocusInWindow();  // Not a valid player nickname
                    return true;
                }

                // Send JOINGAME right away.
                // (Create New Game is done above; see calls to gameWithOptionsBeginSetup)

                // May take a while for server to start game, so set WAIT_CURSOR.
                // The new-game window will clear this cursor
                // (SOCPlayerInterface constructor)

                status.setText(client.strings.get("pcli.message.talkingtoserv"));  // "Talking to server..."
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                net.putNet(SOCJoinGame.toCmd
                    (client.nickname, (client.gotPassword ? "" : client.password), SOCMessage.EMPTYSTR, gm));
            }
        }
        else
        {
            pi.setVisible(true);
        }

        return true;
    }

    /**
     * Trim, validate, and return the nickname textfield if valid.
     * If successful, can also set {@link SOCPlayerClient} nickname fields.
     * May set status bar to a hint message if textfield is empty,
     * such as {@link #NEED_NICKNAME_BEFORE_JOIN}.
     *<P>
     * Unless {@code precheckOnly}:
     *<UL>
     * <LI> Sets {@code SOCPlayerClient.nickname} field
     * <LI> Sets {@code SOCPlayerClient.practiceNickname} field if that field is null
     *</UL>
     *
     * @param precheckOnly  If true, only validate the name, don't set {@code nickname} client-object fields
     * @return Validated nickname from textfield, or {@code null} if blank or not successfully validated
     * @see #readValidNicknameAndPassword()
     * @since 1.1.07
     */
    protected String getValidNickname(final boolean precheckOnly)
    {
        final String n = nick.getText().trim();

        if (n.isEmpty())
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

        String errMsg = checkNameFormat(n);
        if (errMsg != null)
        {
            status.setText(errMsg);

            return null;
        }

        nick.setText(n);

        if (! precheckOnly)
        {
            client.nickname = n;
            if (client.practiceNickname == null)
                client.practiceNickname = n;
        }

        return n;
    }

    /**
     * Check format of an entered nickname or channel name;
     * if problems, return error text to explain.
     *<UL>
     * <LI> Can't contain {@code '|'} or {@code ','}
     * <LI> Must pass {@link SOCMessage#isSingleLineAndSafe(String)}
     *</UL>
     * Doesn't check a few additional requirements that don't apply to channels.
     *
     * @param n Name to check; should not be null or empty
     * @return {@code null} if name is OK, otherwise localized error text
     * @since 2.3.00
     */
    private String checkNameFormat(final String n)
    {
        String errMsg = null;

        if (-1 != n.indexOf(SOCMessage.sep_char))  // '|'
            errMsg = client.strings.get("netmsg.status.client.newgame_name_rejected_char", SOCMessage.sep_char);
                // Name must not contain "|", please choose a different name.
        else if (-1 != n.indexOf(SOCMessage.sep2_char))  // ','
            errMsg = client.strings.get("netmsg.status.client.newgame_name_rejected_char", SOCMessage.sep2_char);
                // Name must not contain ",", please choose a different name.
        else if (! SOCMessage.isSingleLineAndSafe(n))
            errMsg = client.strings.get("netmsg.status.common.newgame_name_rejected");
                // "This name is not permitted, please choose a different name."

        return errMsg;
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
            net.putNet(new SOCAuthRequest
                (SOCAuthRequest.ROLE_GAME_PLAYER, client.getNickname(forPracticeServer), client.password,
                 SOCAuthRequest.SCHEME_CLIENT_PLAINTEXT, net.getHost()).toCmd());

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
                    opts.knownOpts = SOCServer.localizeKnownOptions(client.cliLocale, true);
                }

                if (! opts.allScenStringsReceived)
                {
                    // Game scenario localized text. As with game options, the practice client and
                    // practice server aren't started yet, so we can't go through them to request localization.
                    client.localizeGameScenarios
                        (SOCServer.localizeGameScenarios(client.cliLocale, null, true, false, null),
                         false, true, true);
                }
            } else {
                opts = client.tcpServGameOpts;
                if ((! opts.allOptionsReceived) && (client.sVersion < SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS))
                {
                    // Server doesn't support them.  Don't ask it.
                    fullSetIsKnown = true;
                    opts.knownOpts = null;
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
            if ((opts.newGameOpts == null) && (opts.knownOpts != null))
                opts.newGameOpts = new SOCGameOptionSet(opts.knownOpts, true);
            newGameOptsFrame = showGameOptions(null, opts.newGameOpts, forPracticeServer);

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
                // Client newer than server: Ask about specific new/changed scenarios which server might not know.

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

            if (cliVers != client.sVersion)
                client.getGameMessageSender().put
                    (new SOCScenarioInfo(changes, true).toCmd(), false);
                        // if cli newer: specific scenario list and MARKER_ANY_CHANGED
                        // if srv newer: empty 'changes' list and MARKER_ANY_CHANGED
            else if (client.wantsI18nStrings(false))
                client.getGameMessageSender().put
                    (new SOCLocalizedStrings
                        (SOCLocalizedStrings.TYPE_SCENARIO, SOCLocalizedStrings.FLAG_REQ_ALL,
                         (List<String>) null).toCmd(),
                     false);
        }

        opts.newGameWaitingForOpts = true;
        opts.askedDefaultsAlready = true;
        opts.askedDefaultsTime = System.currentTimeMillis();
        client.getGameMessageSender().put(new SOCGameOptionGetDefaults(null).toCmd(), forPracticeServer);

        if (gameOptsDefsTask != null)
            gameOptsDefsTask.cancel();
        gameOptsDefsTask = new GameOptionDefaultsTimeoutTask(this, client.tcpServGameOpts, forPracticeServer);
        eventTimer.schedule(gameOptsDefsTask, 5000 /* ms */ );

        // Once options are received, handlers will
        // create and show NewGameOptionsFrame.
    }

    /**
     * Create a blank "New Game" {@link NewGameOptionsFrame} to make a new game, or
     * create a Game Info NGOF for an existing game and add it to {@link #gameInfoFrames}.
     * Calls {@link NewGameOptionsFrame#createAndShow(SOCPlayerInterface, MainDisplay, String, SOCGameOptionSet, boolean, boolean)}.
     * When called for an existing game, asks server for timing info/status by sending {@link SOCGameStats}
     * if we've auth'd and server is new enough.
     *
     * @param gaName  Name of existing game, or {@code null} for a new game
     * @param gameOpts  Game's options, or {@code null} if server too old to support them
     * @param forPracticeServer  True if game is on {@link ClientNetwork#practiceServer}, not a TCP server
     * @return the new NGOF
     * @since 2.7.00
     */
    private NewGameOptionsFrame showGameOptions
        (final String gaName, final SOCGameOptionSet gameOpts, final boolean forPracticeServer)
    {
        final boolean isNew = (gaName == null);
        final NewGameOptionsFrame ngof = NewGameOptionsFrame.createAndShow
            (isNew ? null : playerInterfaces.get(gaName), this, gaName, gameOpts, forPracticeServer, ! isNew);
        if (isNew)
            return ngof; // <--- Early return: No existing game ---

        gameInfoFrames.put(gaName, ngof);

        if ((! forPracticeServer)
            && (client.sVersion >= SOCGameStats.VERSION_FOR_TYPE_TIMING)
            && ! nick.getText().trim().isEmpty())
        {
            if (! client.gotPassword)
            {
                if (! readValidNicknameAndPassword())
                    return ngof;  // <--- Early return: Can't auth, so can't send SOCGameStats ---

                net.putNet(new SOCAuthRequest
                    (SOCAuthRequest.ROLE_GAME_PLAYER, client.getNickname(false), client.password,
                     SOCAuthRequest.SCHEME_CLIENT_PLAINTEXT, net.getHost()).toCmd());

                // ideally we'd wait for auth success reply before sending SOCGameStats,
                // but this is already a corner case
            }

            net.putNet(new SOCGameStats(gaName, SOCGameStats.TYPE_TIMING, null).toCmd());
        }

        return ngof;
    }

    /**
     * {@inheritDoc}
     *<P>
     * Assumes {@link #getValidNickname(boolean) getValidNickname(false)}, {@link #getPassword()},
     * {@link ClientNetwork#connect(String, int)}, and {@link #gotPassword} are already called and valid.
     *
     * @since 1.1.07
     */
    public void askStartGameWithOptions
        (final String gmName, final boolean forPracticeServer,
         final SOCGameOptionSet opts, final Map<String, Object> localPrefs)
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
                    (client.nickname, pw, SOCMessage.EMPTYSTR, gmName, opts.getAll())
                : SOCJoinGame.toCmd
                    (client.nickname, pw, SOCMessage.EMPTYSTR, gmName);
            net.putNet(askMsg);
            System.out.flush();  // for debug print output (temporary)
            status.setText(client.strings.get("pcli.message.talkingtoserv"));  // "Talking to server..."
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
    }

    /**
     * {@inheritDoc}
     *<P>
     * If {@code clearStatus} is true and the most recent Server Status code is "OK",
     * will have status line show the text from that message: Typically something like
     * "Welcome to JSettlers!".
     */
    public void clearWaitingStatus(final boolean clearStatus)
    {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        if (clearStatus)
            status.setText(statusOKText != null ? statusOKText : "");  // clear "Talking to server...", etc
    }

    /**
     * Look for active games that we're playing
     *
     * @param fromPracticeServer  Enumerate games from {@link ClientNetwork#practiceServer},
     *     instead of all games in {@link #playerInterfaces}?
     * @return Any found game of ours which is active (state &lt; {@link SOCGame#OVER}), or null if none.
     * @see #hasAnyActiveGame(boolean)
     * @see ClientNetwork#anyHostedActiveGames()
     * @since 1.1.00
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

    @Override
    public boolean hasAnyActiveGame(final boolean fromPracticeServer)
    {
        return (null != findAnyActiveGame(fromPracticeServer));
    }

    /**
     * After network trouble, show the error panel ({@link #MESSAGE_PANEL})
     * instead of the main user/password/games/channels panel ({@link #MAIN_PANEL}).
     *<P>
     * If {@code canPractice} is true, shows the {@code err} message using the startup panel with buttons to
     * connect to a server or practice, instead of the simpler practice-only message panel.
     *
     * @param err  Error message to show; not {@code null}. Can be multi-line by including {@code \n}.
     * @param canPractice  In current state of client, can we start a practice game?
     * @throws NullPointerException if {@code err} is {@code null}
     * @since 1.1.16
     */
    public void showErrorPanel(String err, final boolean canPractice)
        throws NullPointerException
    {
        // In case was WAIT_CURSOR while connecting
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        if (err.indexOf('\n') != -1)
            err = DataOutputUtils.newlinesToHTML(err);

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

        if (canPractice)
        {
            // prep to show startup panel by un-setting read-only fields we'll need again after connect.
            nick.setEditable(true);
            pass.setText("");
            pass.setEditable(true);

            cardLayout.show(this, CONNECT_OR_PRACTICE_PANEL);
            connectOrPracticePane.lostServerConnection(err);
            revalidate();
        }
        else
        {
            cardLayout.show(this, MESSAGE_PANEL);
            revalidate();
            if (canPractice)
            {
                if (! hasAnyActiveGame(true))
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
            versionOrlocalTCPPortLabel.setForeground(miscLabelFGColor);  // MISC_LABEL_FG_OFF_WHITE
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

    public void showStatus(final String statusText, final boolean statusIsOK, final boolean debugWarn)
    {
        status.setText(statusText);
        statusOKText = (statusIsOK) ? statusText : null;

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

    public void repaintGameAndChannelLists()
    {
        if (chlist.isVisible())
            chlist.repaint();

        if (gmlist.isVisible())
            gmlist.repaint();
    }

    public void channelJoined(String channelName)
    {
        nick.setEditable(false);
        pass.setText("");
        pass.setEditable(false);
        clearWaitingStatus(true);
        if (! hasJoinedServer)
        {
            Container c = getParent();
            if (c instanceof Frame)
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

    public void channelMemberList(String channelName, Collection<String> members)
    {
        ChannelFrame fr = channels.get(channelName);

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
            cf.over(message);

        channels.clear();
        DefaultListModel<JoinableListItem> lm = (DefaultListModel<JoinableListItem>) chlist.getModel();
        lm.clear();
        lm.addElement(JoinableListItem.BLANK);
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

    public void gameTimingStatsReceived
        (final String gameName, final long creationTimeSeconds,
         final boolean isStarted, final int durationFinishedSeconds)
    {
        final NewGameOptionsFrame ngof = gameInfoFrames.get(gameName);
        if (ngof != null)
            ngof.gameTimingStatsReceived(creationTimeSeconds, isStarted, durationFinishedSeconds);
    }

    public void dialogClosed(final NewGameOptionsFrame ngof)
    {
        if (ngof == newGameOptsFrame)
            newGameOptsFrame = null;

        final String gaName = ngof.getExistingGameName();
        if (gaName != null)
            gameInfoFrames.remove(gaName);
    }

    public void leaveGame(SOCGame game)
    {
        playerInterfaces.remove(game.getName());
    }

    public PlayerClientListener gameJoined
        (final SOCGame game, final int[] layoutVS, final Map<String, Object> localPrefs)
    {
        nick.setEditable(false);
        pass.setEditable(false);
        pass.setText("");
        clearWaitingStatus(true);
        if (! hasJoinedServer)
        {
            Container c = getParent();
            if (c instanceof Frame)
            {
                Frame fr = (Frame) c;
                fr.setTitle(/*I*/fr.getTitle() + " [" + nick.getText() + "]"/*18N*/);
            }
            hasJoinedServer = true;
        }

        final SOCPlayerInterface pi = new SOCPlayerInterface
            (game.getName(), SwingMainDisplay.this, game, layoutVS, localPrefs);
        playerInterfaces.put(game.getName(), pi);

        // slight delay before PI window visibility, otherwise
        // MainDisplay might get back in front of it while
        // processing double-click event (seen MacOSX in 2020-01)
        eventTimer.schedule(new TimerTask()
        {
            public void run()
            {
                EventQueue.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        pi.setVisible(true);
                        pi.toFront();
                    }
                });
            }
        }, 80 /* ms */ );

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

        if ((opts.newGameOpts == null) && (opts.knownOpts != null))
            opts.newGameOpts = new SOCGameOptionSet(opts.knownOpts, true);
        newGameOptsFrame = showGameOptions(null, opts.newGameOpts, isPractice);
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
                final SOCGameOptionSet gameOpts = client.serverGames.parseGameOptions(gameInfoWaiting);
                if (! isPractice)
                    client.checkGameoptsForUnknownScenario(gameOpts);
                showGameOptions(gameInfoWaiting, gameOpts, isPractice);
            }
            else if (newGameWaiting)
            {
                synchronized(opts)
                {
                    opts.newGameWaitingForOpts = false;
                    if ((opts.newGameOpts == null) && (opts.knownOpts != null))
                        opts.newGameOpts = new SOCGameOptionSet(opts.knownOpts, true);
                }
                newGameOptsFrame = showGameOptions(null, opts.newGameOpts, isPractice);
            }
        }
    }

    public void addToGameList
        (final boolean cannotJoin, String gameName, String gameOptsStr, final boolean addToSrvList)
    {
        if (addToSrvList)
        {
            if (client.serverGames == null)
                client.serverGames = new SOCGameList(client.tcpServGameOpts.knownOpts);
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
        gmlist.repaint();
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
                    client.serverGames.deleteGame(gameName);  // may not be in there

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
            client.serverGames.deleteGame(gameName);  // may not be in there

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
            net.putNet(new SOCChannelTextMsg(ch, client.nickname, mes).toCmd());
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

    /**
     * Print the current chat ignorelist to a channel window.
     * @since 1.1.00
     */
    protected void printIgnoreList(ChannelFrame fr)
    {
        fr.print("* "+/*I*/"Ignore list:"/*18N*/);

        for (String s : client.ignoreList)
        {
            fr.print("* " + s);
        }
    }

    /**
     * Print the current chat ignorelist to a game window.
     * @since 1.1.00
     */
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
            if (parent instanceof Frame)
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
        net.connect(null, tport);

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


    /**
     * React to windowOpened, windowClosing events for SwingMainDisplay's Frame.
     *<P>
     * Before v2.0.00 this class was {@code SOCPlayerClient.MyWindowAdapter}.
     */
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
                if (c instanceof Frame)
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
                (new SOCGameOptionInfo(new SOCGameOption("-", null), Version.versionNumber(), null), false);
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


