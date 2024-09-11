/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2009-2015,2017-2024 Jeremy D Monin <jeremy@nand.net>
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
 **/
package soc.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCPlayer;
import soc.game.SOCScenario;
import soc.game.SOCVersionedItem;
import soc.message.SOCGameStats;
import soc.message.SOCMessage;
import soc.util.DataUtils;
import soc.util.SOCGameList;
import soc.util.SOCStringManager;
import soc.util.Version;

/**
 * This is the dialog for a game's name and options to set, along with the client's
 * user preferences such as {@link SOCPlayerClient#PREF_SOUND_ON}
 * and per-game preferences such as {@link SOCPlayerInterface#PREF_SOUND_MUTE}.
 * When "Create" button is clicked, validates fields and calls
 * {@link MainDisplay#askStartGameWithOptions(String, boolean, SOCGameOptionSet, Map)}.
 *<P>
 * Also used for showing a game's options (read-only) during game play.
 *<P>
 * Changes to the {@code PREF_SOUND_ON} or {@code PREF_SOUND_MUTE} checkboxes
 * take effect immediately so the user can mute sound effects with minimal frustration.
 * Other user preferences take effect only when Create/OK button is pressed,
 * and not if Cancel or Escape key is pressed.
 *<P>
 * If this window already exists and you'd like to make it topmost,
 * call {@link #setVisible(boolean)} instead of {@link #requestFocus()}.
 *<P>
 * Game option "SC" (Scenarios) gets special rendering. Internally it's {@link SOCGameOption#OTYPE_STR},
 * but it's presented as a checkbox and {@link JComboBox}. When a scenario is picked in the JComboBox,
 * related options are updated by "SC"'s {@link SOCGameOption.ChangeListener}.
 *<P>
 * This class also contains the "Scenario Info" popup window, called from
 * this dialog's Scenario Info button, and from {@link SOCPlayerInterface}
 * when first joining a game with a scenario:
 * See {@link #showScenarioInfoDialog(SOCScenario, SOCGameOptionSet, SOCGameOptionSet, int, MainDisplay, Window)}.
 *<P>
 * Although this class was changed in v2.0 from a Frame to a JDialog, it's still named
 * "NewGameOptionsFrame": Many commit messages and local vars refer to "NGOF".
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.07
 */
@SuppressWarnings("serial")
/*package*/ class NewGameOptionsFrame extends JDialog
    implements ActionListener, DocumentListener, KeyListener, ItemListener, MouseListener
{
    // See initInterfaceElements() for most of the UI setup.
    // See clickCreate() for method which handles game setup after options have been chosen.

    /**
     * Maximum range (min-max value) for integer-type options
     * to be rendered using a value popup, instead of a textfield.
     * @see #initOption_int(SOCGameOption)
     */
    public static final int INTFIELD_POPUP_MAXRANGE = 21;

    /**
     * Game's interface if known, or {@code null} for a new game.
     * Used for updating settings like {@link SOCPlayerInterface#isSoundMuted()}.
     * Can use {@link SOCPlayerInterface#getGame()} to get info about the game.
     *<P>
     * For an alternate source of some limited game info when {@code pi == null},
     * see {@link #existingGameName} and {@link #gameCreationTimeSeconds}.
     *
     * @see #localPrefs
     * @see #forNewGame
     * @since 1.2.00
     */
    private final SOCPlayerInterface pi;

    /**
     * UI's main display, with list of games and channels and status bar.
     * Never null.
     */
    private final MainDisplay mainDisplay;

    /** should this be sent to the remote tcp server, or local practice server? */
    private final boolean forPractice;

    /**
     * The game's effective server version:
     * Our own version for practice games, otherwise {@link SOCPlayerClient#sVersion}.
     * @since 2.7.00
     */
    private final int gameVersion;

    /**
     * Map of local client preferences for a new or current game.
     * Same keys and values as {@link SOCPlayerInterface} constructor's
     * {@code localPrefs} parameter.
     * @since 1.2.00
     */
    private final HashMap<String, Object> localPrefs;

    /**
     * Is this NGOF used to set options for a new game, not to show them for an existing one?
     * If true, {@link #pi} == {@code null} and {@link #existingGameName} == {@code null}.
     * @since 2.0.00
     */
    private final boolean forNewGame;

    /**
     * Name of existing game, or {@code null} if {@link #forNewGame}.
     * @see #opts
     * @since 2.7.00
     */
    private final String existingGameName;

    /**
     * For showing Game Info in {@link SwingMainDisplay} about a game we may not be a member of,
     * its creation time (same format as {@link System#currentTimeMillis()} / 1000).
     * 0 otherwise.
     *<P>
     * Related timing fields: {@link #gameIsStarted}, {@link #gameDurationFinishedSeconds}.
     * @see #gameTimingStatsReceived(long, boolean, int)
     * @since 2.7.00
     */
    private long gameCreationTimeSeconds;

    /**
     * For showing Game Info when {@link #gameCreationTimeSeconds} != 0, true if that game has started.
     * False/unused otherwise.
     * @see #gameDurationFinishedSeconds
     * @since 2.7.00
     */
    private boolean gameIsStarted;

    /**
     * For showing Game Info when {@link #gameCreationTimeSeconds} != 0, and that game has finished,
     * its duration in seconds. 0 otherwise.
     * @since 2.7.00
     */
    private int gameDurationFinishedSeconds;

    /**
     * Is this for display only (shown for an existing game)? If false, dialog is to create a new game.
     * @see #pi
     * @see #forNewGame
     */
    private final boolean readOnly;

    /**
     * Contains this game's {@link SOCGameOption}s, or null if none.
     * (This can occur if server is very old, or uses a third-party option which the client does not have.)
     * Unknowns (OTYPE_UNKNOWN) and inactives (SGO.FLAG_INACTIVE_HIDDEN) are removed in
     * {@link #initInterface_Options(JPanel, GridBagLayout, GridBagConstraints) initInterface_Options(..)}.
     *<P>
     * The opts' values are updated from UI components when the user hits the Create Game button,
     * and sent to the server to create the game.  If there are {@link SOCGameOption.ChangeListener}s,
     * they are updated as soon as the user changes them in the components, then re-updated when
     * Create is hit.
     *
     * @see #readOptsValuesFromControls(boolean)
     * @see #knownOpts
     * @see #existingGameName
     */
    private final SOCGameOptionSet opts;

    /**
     * This game's server's Known Options, from {@link ServerGametypeInfo#knownOpts}, or null if server is very old.
     * When NGOF is being shown to create the first new game / practice game, {@code knownOpts}
     * is copied to {@link ServerGametypeInfo#newGameOpts} to be {@link #opts} here.
     * @since 2.5.00
     */
    private final SOCGameOptionSet knownOpts;

    /** Key = Swing control; value = {@link SOCGameOption} within {@link #opts}. Empty if opts is null.  */
    private Map<Component, SOCGameOption> controlsOpts;

    /**
     * Swing component for each gameopt, for handling {@link SOCGameOption#refreshDisplay()}
     * if called by {@link SOCGameOption.ChangeListener}s.
     * Key = option key; value = UI component.
     * Null if {@link #readOnly}.
     * For game options with 2 input controls (OTYPE_INTBOOL, OTYPE_ENUMBOOL),
     * the JTextField/JComboBox is found here, and the boolean JCheckBox is found in {@link #boolOptCheckboxes}.
     * The scenario dropdown (option {@code "SC"}) uses a {@code JComboBox} control holding
     * {@link SOCScenario} objects and the string "(none)".
     * @since 1.1.13
     * @see #fireOptionChangeListener(soc.game.SOCGameOption.ChangeListener, SOCGameOption, Object, Object)
     */
    private Map<String, Component> optsControls;

    /** Key = {@link SOCVersionedItem#key SOCGameOption.key}; value = {@link JCheckbox} if bool/intbool option.
      * Empty if none, null if readOnly.
      * Used to quickly find an option's associated checkbox.
      */
    private Map<String, JCheckBox> boolOptCheckboxes;

    /**
     * Scenario info for {@link #scenDropdown}, if {@link #opts} contains the {@code "SC"} game option, or null.
     * Initialized from {@link SOCScenario#getAllKnownScenarios()} during
     * {@link #initInterface_Options(JPanel, GridBagLayout, GridBagConstraints)},
     * which is called after any server negotiations.
     * @since 2.0.00
     */
    private Map<String, SOCScenario> allSc;

    /**
     * Scenario choice dropdown if {@link #opts} contains the {@code "SC"} game option, or null.
     * When an item is selected, {@link #actionPerformed(ActionEvent)} reacts specially for this control
     * to update {@code "SC"} within {@link #opts} and enable/disable {@link #scenInfo}.
     * @since 2.0.00
     */
    private JComboBox<?> scenDropdown;

    /**
     * Scenario Info button, for info window about {@link #scenDropdown}'s selected scenario, or null.
     * @see #clickScenarioInfo()
     * @since 2.0.00
     */
    private JButton scenInfo;

    /** Create Game button; null if {@link #readOnly} */
    private JButton create;
    /** Cancel button; text is "OK" if {@link #readOnly} */
    private JButton cancel;
    private JTextField gameName;

    /**
     * Game info if {@link #pi} != {@code null}, or {@code null} otherwise (including when {@link #readOnly}).
     * Call {@link #updateGameInfo()} to update contents.
     * @see #gameInfoUpdateTimer
     * @see #msgText
     * @since 2.7.00
     */
    private JLabel gameInfo;

    /**
     * Once per minute, calls {@link #updateGameInfo()}.
     * Null if {@link #gameInfo} null or if this NGOF was {@link #dispose()}d.
     * @since 2.7.00
     */
    private TimerTask gameInfoUpdateTimer;

    /**
     * For new games, message/prompt text at top of dialog.
     * msgText is null if {@link #readOnly}.
     * @see #gameInfo
     */
    private JTextField msgText;

    // // TODO refactor; these are from connectorprac panel
    private static final Color HEADER_LABEL_BG = new Color(220,255,220);
    private static final Color HEADER_LABEL_FG = Color.BLACK;

    /**
     * i18n text strings; will use same locale as SOCPlayerClient's string manager.
     * Localized option names are requested from the server when client locale isn't en_US.
     * @since 2.0.00
     */
    private static final SOCStringManager strings = SOCStringManager.getClientManager();

    /**
     * Creates a new NewGameOptionsFrame.
     * Once created, resets the mouse cursor from hourglass to normal, and clears main panel's status text.
     *<P>
     * See also convenience method
     * {@link #createAndShow(SOCPlayerInterface, MainDisplay, String, SOCGameOptionSet, boolean, boolean)}.
     *<P>
     * If showing info for a current game but {@code pi == null},
     * may call {@link MainDisplay#readValidNicknameAndPassword()}
     * because client must auth before sending {@link SOCGameStats} request.
     *
     * @param pi  Interface of existing game, or {@code null} for a new game.
     *     Used for updating settings like {@link SOCPlayerInterface#isSoundMuted()}.
     * @param md  Client's main display interface
     * @param gaName   Name of existing game,
     *                 or null for new game; will be blank or (forPractice)
     *                 to use {@link SOCPlayerClient#DEFAULT_PRACTICE_GAMENAME}.
     * @param opts     Set of {@link SOCGameOption}s; its values will be changed when "New Game" button
     *                 is pressed, so the next OptionsFrame will default to the values the user has chosen.
     *                 To preserve them, copy the set beforehand.
     *                 Null if server doesn't support game options.
     *                 Unknown options ({@link SOCGameOption#OTYPE_UNKNOWN}) will be removed unless <tt>readOnly</tt>.
     *                 If not <tt>readOnly</tt>, each option's {@link SOCGameOption#userChanged userChanged}
     *                 flag will be cleared, to reset status from any previously shown NewGameOptionsFrame.
     * @param forPractice For making a new game: Will the game be on local practice server, vs remote tcp server?
     * @param readOnly    Is this display-only (for use during a game), or can it be changed (making a new game)?
     * @throws IllegalArgumentException if a non-null {@code opts} is the client's knownOpts
     *     from {@link ServerGametypeInfo#knownOpts}, which should be copied before use for a new game's options
     */
    public NewGameOptionsFrame
        (final SOCPlayerInterface pi, final MainDisplay md, String gaName,
         final SOCGameOptionSet opts, final boolean forPractice, final boolean readOnly)
        throws IllegalArgumentException
    {
        super( pi, readOnly
                ? (strings.get("game.options.title", gaName))
                : (forPractice
                    ? strings.get("game.options.title.newpractice")
                    : strings.get("game.options.title.new")));

        // Uses default BorderLayout, for simple stretching when window is resized

        this.pi = pi;
        this.mainDisplay = md;
        SOCPlayerClient cli = md.getClient();
        forNewGame = (gaName == null);
        existingGameName = gaName;
        this.opts = opts;
        knownOpts = ((forPractice) ? cli.practiceServGameOpts : cli.tcpServGameOpts).knownOpts;
        localPrefs = new HashMap<String, Object>();
        this.forPractice = forPractice;
        this.readOnly = readOnly;
        gameVersion = (forPractice) ? Version.versionNumber() : cli.sVersion;

        if ((opts != null) && (opts == knownOpts))
            throw new IllegalArgumentException("opts == knownOpts");

        controlsOpts = new HashMap<Component, SOCGameOption>();
        if (! readOnly)
        {
            optsControls = new HashMap<String, Component>();
            boolOptCheckboxes = new HashMap<String, JCheckBox>();
        }
        if ((gaName == null) && forPractice)
        {
            if (cli.numPracticeGames == 0)
                gaName = cli.DEFAULT_PRACTICE_GAMENAME;
            else
                gaName = cli.DEFAULT_PRACTICE_GAMENAME + " " + (1 + cli.numPracticeGames);
        }

        // same Frame/Window setup as in SOCPlayerClient.main
        if (! SwingMainDisplay.isOSColorHighContrast())
        {
            setBackground(SwingMainDisplay.JSETTLERS_BG_GREEN);
            setForeground(Color.black);
            getRootPane().setBackground(null);  // inherit from overall window
            getContentPane().setBackground(null);
        }
        setLocationByPlatform(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        addKeyListener(this);

        initInterfaceElements(gaName);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { clickCancel(false); }
            });

        /**
         * setup is complete; reset mouse cursor from hourglass to normal
         * (was set to hourglass before calling this constructor)
         */
        md.clearWaitingStatus(true);
    }

    /**
     * Creates and shows a new NewGameOptionsFrame.
     * Once created, resets the mouse cursor from hourglass to normal, and clears main panel's status text.
     * See {@link #NewGameOptionsFrame(SOCPlayerInterface, MainDisplay, String, SOCGameOptionSet, boolean, boolean) constructor}
     * for notes about <tt>opts</tt> and other parameters.
     *<P>
     * If showing info for a current game but {@code pi == null},
     * may call {@link MainDisplay#readValidNicknameAndPassword()}
     * because client must auth before sending {@link SOCGameStats} request.
     *
     * @param pi  Interface of existing game, or {@code null} for a new game; see constructor
     * @param gaName  Name of existing game, or {@code null} to show options for a new game;
     *     see constructor for details
     * @return the new frame
     * @throws IllegalArgumentException if a non-null {@code opts} is the client's knownOpts
     *     from {@link ServerGametypeInfo#knownOpts}, which should be copied before use for a new game's options
     */
    public static NewGameOptionsFrame createAndShow
        (SOCPlayerInterface pi, MainDisplay md, String gaName,
         SOCGameOptionSet opts, boolean forPractice, boolean readOnly)
        throws IllegalArgumentException
    {
        final NewGameOptionsFrame ngof =
            new NewGameOptionsFrame(pi, md, gaName, opts, forPractice, readOnly);
        ngof.pack();
        ngof.setVisible(true);

        return ngof;
    }

    /**
     * Interface setup for constructor. Assumes dialog is using BorderLayout.
     * Most elements are part of a sub-panel occupying most of this dialog, and using GridBagLayout.
     * Fills {@link #localPrefs}.
     */
    private void initInterfaceElements(final String gaName)
    {
        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        final int displayScale = mainDisplay.getDisplayScaleFactor();
        final boolean isOSHighContrast = SwingMainDisplay.isOSColorHighContrast();
        final boolean shouldClearButtonBGs = (! isOSHighContrast) && SOCPlayerClient.IS_PLATFORM_WINDOWS;

        final JPanel bp = new JPanel(gbl);  // Actual button panel
        int n = 4 * displayScale;
        bp.setBorder(new EmptyBorder(n, n, n, n));  // need padding around edges, because panel fills the window
        if (! isOSHighContrast)
        {
            bp.setForeground(getForeground());
            bp.setBackground(SwingMainDisplay.JSETTLERS_BG_GREEN);  // If this is omitted, firefox 3.5+ applet uses themed bg-color (seen OS X)
        }

        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1;  // stretch with dialog resize

        if ((! readOnly) && (opts != null))
        {
            msgText = new JTextField(strings.get("game.options.prompt"));  // "Choose options for the new game."
            msgText.setEditable(false);
            if (! isOSHighContrast)
            {
                msgText.setForeground(SwingMainDisplay.MISC_LABEL_FG_OFF_WHITE);
                msgText.setBackground(getBackground());
            }
            add(msgText, BorderLayout.NORTH);
        }

        /**
         * Interface setup: Game name
         */
        JLabel L;

        L = new JLabel(strings.get("game.options.name"), SwingConstants.LEFT);  // "Game name"
        if (! isOSHighContrast)
        {
            L.setBackground(HEADER_LABEL_BG);
            L.setForeground(HEADER_LABEL_FG);
            L.setOpaque(true);
        }
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.ipadx = 2 * displayScale;
        gbl.setConstraints(L, gbc);
        gbc.ipadx = 0;
        bp.add(L);

        gameName = new JTextField(20);
        if (gaName != null)
            gameName.setText(gaName);
        if (readOnly)
        {
            gameName.setEditable(false);  // not setEnabled(false), so still allows highlight in order to copy name
        } else {
            gameName.addKeyListener(this);     // for ESC/ENTER
            Document tfDoc = gameName.getDocument();
            tfDoc.putProperty("owner", gameName);
            tfDoc.addDocumentListener(this);    // Will enable buttons when field is not empty
        }
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1;
        gbl.setConstraints(gameName, gbc);
        bp.add(gameName);

        /**
         * Game Info row for current game, if any
         */
        if (! forNewGame)
        {
            final SOCPlayerClient pcli = mainDisplay.getClient();
            final boolean hasGameInfoAlready = (pi != null);
            if (hasGameInfoAlready || (pcli.sVersion >= SOCGameStats.VERSION_FOR_TYPE_TIMING))
            {
                final int px2 = 2 * displayScale;
                gbc.ipadx = px2;

                L = new JLabel(strings.get("pcli.main.game.info") + ":", SwingConstants.LEFT);  // "Game Info:"
                gbc.gridwidth = 2;
                gbc.weightx = 0;
                gbl.setConstraints(L, gbc);
                bp.add(L);

                final Insets prevIns = gbc.insets;
                gameInfo = new JLabel();
                if (hasGameInfoAlready)
                    updateGameInfo();
                else if (! (pcli.gotPassword || mainDisplay.readValidNicknameAndPassword()))
                    gameInfo.setText(strings.get("game.options.must_enter_nickname_to_check"));  // "Must enter a nickname to check status"
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbc.weightx = 1;
                gbc.insets = new Insets(px2, px2, px2, px2);
                gbl.setConstraints(gameInfo, gbc);
                bp.add(gameInfo);

                gbc.ipadx = 0;
                gbc.insets = prevIns;

                // thin <HR>-type spacer between info and game options
                JSeparator spacer = new JSeparator();
                if (! SwingMainDisplay.isOSColorHighContrast())
                    spacer.setBackground(HEADER_LABEL_BG);
                gbl.setConstraints(spacer, gbc);
                bp.add(spacer);

                if (hasGameInfoAlready)
                    initGameInfoUpdateTimer(true);
                // else
                    // SwingMainDisplay has asked server for game info on our behalf,
                    // and will soon call gameTimingStatsReceived(..)
            }
        }

        /**
         * Interface setup: Game Options, user's client preferences, per-game local preferences
         */
        initInterface_Options(bp, gbl, gbc);

        /**
         * Interface setup: Buttons
         * Bottom row, centered in middle
         * This sub-panel was added in 1.2.00, so the button row's background color
         * was green in all 1.1.xx, default-gray in 1.2.xx, back to green for all 2.x.xx
         */
        JPanel btnPan = new JPanel();
        if (! isOSHighContrast)
        {
            btnPan.setBackground(null);
            btnPan.setForeground(null);
        }
        btnPan.setBorder(new EmptyBorder(4 * displayScale, 2 * displayScale, 0, 2 * displayScale));
            // padding between option rows, buttons

        if (readOnly)
        {
            cancel = new JButton(strings.get("base.ok"));
            cancel.setEnabled(true);
        } else {
            cancel = new JButton(strings.get("base.cancel"));
            cancel.addKeyListener(this);  // for win32 keyboard-focus
        }
        cancel.addActionListener(this);
        if (shouldClearButtonBGs)
            cancel.setBackground(null);  // needed on win32 to avoid gray corners
        btnPan.add(cancel);

        if (! readOnly)
        {
            create = new JButton(strings.get("game.options.oknew"));  // "Create Game"
            if (shouldClearButtonBGs)
                create.setBackground(null);
            create.addActionListener(this);
            create.addKeyListener(this);
            create.setEnabled(! readOnly);
            if ((gaName == null) || (gaName.length() == 0))
                create.setEnabled(false);  // Will enable when gameName not empty
            btnPan.add(create);
        }

        getRootPane().setDefaultButton(readOnly ? cancel : create);

        add(btnPan, BorderLayout.SOUTH);

        // Keyboard shortcut setup
        {
            final JRootPane rp = getRootPane();
            final InputMap im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            final ActionMap am = rp.getActionMap();

            // ESC to cancel/close dialog, even if nothing has keyboard focus (as seen on MacOSX)
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
            am.put("cancel", new AbstractAction()
            {
                public void actionPerformed(ActionEvent ae) { clickCancel(false); }
            });
        }

        // Final assembly setup
        bp.validate();
        add(bp, BorderLayout.CENTER);

        // Now that bp's been added to hierarchy, add copy menu if game name is read-only
        if (readOnly && (gaName != null))
            EventQueue.invokeLater(new Runnable()
            {
                public void run()
                {
                    final PopupMenu menu = new PopupMenu();

                    MenuItem mi = new MenuItem(strings.get("menu.copy"));  // "Copy"
                    mi.addActionListener(new ActionListener()
                    {
                        public void actionPerformed(ActionEvent ae)
                        {
                            try
                            {
                                String selData = gameName.getSelectedText();
                                if ((selData == null) || selData.isEmpty())
                                    selData = gameName.getText();
                                final StringSelection data = new StringSelection(selData);
                                final Clipboard cb = gameName.getToolkit().getSystemClipboard();
                                if (cb != null)
                                    cb.setContents(data, data);
                            } catch (Exception e) {}  // security, or clipboard unavailable
                        }
                    });
                    menu.add(mi);

                    gameName.add(menu);
                    gameName.addMouseListener(new MouseAdapter()
                    {
                        // different platforms have different popupTriggers for their context menus,
                        // so check several types of mouse event:
                        public void mouseReleased(MouseEvent e) { mouseClicked(e); }
                        public void mousePressed(MouseEvent e)  { mouseClicked(e); }
                        public void mouseClicked(MouseEvent e)
                        {
                            if (! e.isPopupTrigger())
                                return;

                            e.consume();
                            menu.show(gameName, e.getX(), e.getY());
                        }
                    });
                }
            });
    }

    /**
     * Start a timer to show the game's increasing age once per minute.
     * If that timer's already started, does nothing.
     * @param initialDelayMinute  If true, wait 1 minute before the first call to {@link #updateGameInfo()}.
     * @since 2.7.00
     */
    private void initGameInfoUpdateTimer(final boolean initialDelayMinute)
    {
        if (gameInfoUpdateTimer != null)
            return;

        gameInfoUpdateTimer = new TimerTask()
        {
            public void run()
            {
                EventQueue.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        updateGameInfo();
                    }
                });
            }
        };

        mainDisplay.getEventTimer().scheduleAtFixedRate(gameInfoUpdateTimer, initialDelayMinute ? 60100 : 0, 60100);
            // just over 60 seconds, to avoid being wrong for 59 of 60 seconds if timing is slightly off
    }

    /**
     * Interface setup: {@link SOCGameOption}s, user's client preferences, per-game local preferences.
     * Boolean checkboxes go on the left edge; text and int/enum values are to right of checkboxes.
     * One row per option; 3-letter options are grouped under their matching 2-letter ones,
     * longer options whose keys have a {@code '_'} under the option (if any) whose key
     * is the prefix before {@code '_'} (see {@link SOCGameOption#getGroupParentKey(String)}).
     * Non-grouped options are sorted by case-insensitive description
     * by calling {@link SOCGameOption#compareTo(Object)}.
     *<P>
     * When showing options to create a new game, option keys starting with '_' are hidden.
     * This prevents unwanted changes to those options, which are set at the server during game creation.
     * When the dialog is shown read-only during a game, these options are shown.
     *<P>
     * Options which have {@link SOCGameOption#FLAG_INTERNAL_GAME_PROPERTY} are always hidden.
     * If not {@link #readOnly}, they're removed from opts. Unknown opts are removed unless read-only.
     *<P>
     * This is called from constructor, so this is a new NGOF being shown.
     * If not read-only, clear {@link SOCGameOption#userChanged} flag for
     * each option in {@link #opts}.
     *<P>
     * If options are null, put a label with "This game does not use options" (localized).
     *<P>
     * Sets up local preferences for the client by calling
     * {@link #initInterface_UserPrefs(JPanel, GridBagLayout, GridBagConstraints)}.
     */
    private void initInterface_Options(JPanel bp, GridBagLayout gbl, GridBagConstraints gbc)
    {
        final boolean isOSHighContrast = SwingMainDisplay.isOSColorHighContrast();
        final boolean hideUnderscoreOpts = ! readOnly;

        JLabel L;

        if (opts == null)
        {
            L = new JLabel(strings.get
                    ((knownOpts != null)
                     ? "game.options.none"     // "This game does not use options."
                     : "game.options.not" ));  // "This server version does not support game options."
            if (! isOSHighContrast)
                L.setForeground(SwingMainDisplay.MISC_LABEL_FG_OFF_WHITE);
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(L, gbc);
            bp.add(L);

            initInterface_UserPrefs(bp, gbl, gbc);

            return;  // <---- Early return: no options ----
        }
        else if (! readOnly)
        {
            for (SOCGameOption opt : opts)
                opt.userChanged = false;  // clear flag from any previously shown NGOF
        }

        if (opts.containsKey("SC"))
            allSc = SOCScenario.getAllKnownScenarios();

        gbc.anchor = GridBagConstraints.WEST;

        // Look for options that should be grouped together and indented
        // under another option (based on key length and common prefix)
        // instead of aligned to the start of a line.
        HashMap<String,String> sameGroupOpts = new HashMap<>();  // key=in-same-group opt, value=opt which heads that group
        for (final SOCGameOption opt : opts)
        {
            final String okey = opt.key;
            final int kL = okey.length();
            if ((kL <= 2) || ((opt.optType == SOCGameOption.OTYPE_UNKNOWN) && ! readOnly)
                || opt.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN))
                continue;

            final String kf2 = SOCGameOption.getGroupParentKey(okey);
            if (kf2 == null)
                continue;
            SOCGameOption op2 = opts.get(kf2);
            if ((op2 != null) && ((op2.optType != SOCGameOption.OTYPE_UNKNOWN) || readOnly))
                sameGroupOpts.put(okey, kf2);
        }

        // Sort and lay out options; remove unknowns and internal-onlys from opts.

        // TreeSet sorts game options by description, using gameopt.compareTo.
        // The array lets us remove from opts without disrupting an iterator.
        SOCGameOption[] optArr = new TreeSet<SOCGameOption>(opts.values()).toArray(new SOCGameOption[0]);

        // Some game options from sameGroupOpts, sorted by key.
        // Declared up here for occasional reuse within the loop.
        TreeMap<String, SOCGameOption> optGroup = new TreeMap<>();

        for (int i = 0; i < optArr.length; ++i)
        {
            final SOCGameOption op = optArr[i];

            if ((op.optType == SOCGameOption.OTYPE_UNKNOWN) && ! readOnly)
            {
                opts.remove(op.key);
                continue;  // <-- Removed, Go to next entry --
            }

            if (op.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN))
            {
                if (! readOnly)
                    opts.remove(op.key);  // don't send inactive options when requesting new game from client
                continue;  // <-- Don't show inactive options --
            }

            if (op.hasFlag(SOCGameOption.FLAG_INTERNAL_GAME_PROPERTY))
            {
                if (! readOnly)
                    opts.remove(op.key);  // ignore internal-property options when requesting new game from client
                continue;  // <-- Don't show internal-property options --
            }

            if (op.key.charAt(0) == '_')
            {
                if (hideUnderscoreOpts)
                    continue;  // <-- Don't show options starting with '_'

                if ((allSc != null) && allSc.containsKey(op.key.substring(1)))
                    continue;  // <-- Don't show options which are scenario names (use SC dropdown to pick at most one)
            }

            if (sameGroupOpts.containsKey(op.key))
                continue;  // <-- Part of a group: We'll init this opt soon with rest of that group --

            final boolean sharesGroup = sameGroupOpts.containsValue(op.key);

            initInterface_OptLine(op, bp, gbl, gbc);
            if (sharesGroup)
            {
                // Group them under this one.
                // Sort by each opt's key, for stability across localizations.

                optGroup.clear();

                for (final String kf3 : sameGroupOpts.keySet())
                {
                    final String kf2 = sameGroupOpts.get(kf3);
                    if ((kf2 == null) || ! kf2.equals(op.key))
                        continue;  // <-- Goes with a a different option --

                    SOCGameOption groupHeadOpt = opts.get(kf3);
                    if (groupHeadOpt == null)
                        continue;  // apparently was removed after initializing sameGroupOpts (internal-use opt?)
                    optGroup.put(kf3, groupHeadOpt);
                }

                for (final SOCGameOption op3 : optGroup.values())
                    initInterface_OptLine(op3, bp, gbl, gbc);
            }

        }  // for(opts)

        initInterface_UserPrefs(bp, gbl, gbc);

        // Check if there's a default/current scenario; if so, set other options' values from it (VP, etc)
        if (! readOnly)
        {
            final SOCGameOption optSC = opts.get("SC");
            if ((optSC != null) && ! optSC.getStringValue().isEmpty())
                fireUserChangedOptListeners(optSC, scenDropdown, true, false);
        }
    }

    /**
     * Set up one game option in one line of the panel.
     * Based on the option type, create the appropriate Swing component and call
     * {@link #initInterface_Opt1(SOCGameOption, Component, boolean, boolean, JPanel, GridBagLayout, GridBagConstraints)}.
     *<P>
     * Special handling: Scenario (option {@code "SC"}) gets a checkbox, label, dropdown, and a second line with
     * an Info button. (Sets {@link #scenDropdown}, {@link #scenInfo}).
     *
     * @param op  Option data
     * @param bp  Add to this panel
     * @param gbl Use this layout
     * @param gbc Use these constraints
     */
    private void initInterface_OptLine
        (SOCGameOption op, JPanel bp, GridBagLayout gbl, GridBagConstraints gbc)
    {
        if (op.key.equals("SC"))
        {
            // special handling: Scenario
            if ((allSc == null) || allSc.isEmpty())
                return;

            int i = 0, sel = 0;

            JComboBox<Object> jcb = new JComboBox<Object>();  // for scenDropdown: holds SOCScenarios and a String
            jcb.addItem(strings.get("base.none.parens"));  // "(none)" is item 0 in dropdown

            Collection<SOCScenario> scens = allSc.values();
            if (! readOnly)
            {
                // Sort by rank and description.
                // Don't sort if readOnly and thus dropdown not enabled, probably not browsable.

                ArrayList<SOCScenario> sl = new ArrayList<SOCScenario>(scens);
                Collections.sort(sl, new Comparator<SOCScenario>() {
                    // This method isn't part of SOCScenario because that class already has
                    // equals and compareTo methods comparing keys, not descriptions

                    public int compare(SOCScenario a, SOCScenario b)
                    {
                        final int rankA = a.getSortRank(), rankB = b.getSortRank();
                        if (rankA < rankB)
                            return -1;
                        else if (rankA > rankB)
                            return 1;

                        return a.getDesc().compareTo(b.getDesc());
                    }
                });
                scens = sl;
            }

            final String currScen = op.getStringValue();  // or "" if none
            for (final SOCScenario sc : scens)
            {
                ++i;
                jcb.addItem(sc);  // sc.toString() == sc.desc
                if (sc.key.equals(currScen))
                    sel = i;
            }
            if (sel != 0)
            {
                jcb.setSelectedIndex(sel);
                op.setBoolValue(true);
            }

            scenDropdown = jcb;
            initInterface_Opt1(op, jcb, true, true, false, bp, gbl, gbc);
                // adds jcb, and a checkbox which will toggle this OTYPE_STR's op.boolValue
            jcb.addActionListener(this);  // when item selected, enable/disable Scenario Info button

            if ((! readOnly) || opts.containsKey("SC"))
            {
                // 2nd line: right-justified "Scenario Info..." button

                JLabel blank = new JLabel();
                gbc.gridwidth = 1;
                gbc.weightx = 0;
                gbl.setConstraints(blank, gbc);
                bp.add(blank);
                scenInfo = new JButton(strings.get("game.options.scenario.info_btn"));  // "Scenario Info..."
                if (SOCPlayerClient.IS_PLATFORM_WINDOWS && ! SwingMainDisplay.isOSColorHighContrast())
                    scenInfo.setBackground(null);  // inherit from parent; needed on win32 to avoid gray corners
                scenInfo.addActionListener(this);
                scenInfo.addKeyListener(this);
                scenInfo.setEnabled(sel != 0);  // disable if "(none)" is selected scenario option

                gbc.gridwidth = GridBagConstraints.REMAINDER;
                final int oldAnchor = gbc.anchor, oldFill = gbc.fill;
                gbc.fill = GridBagConstraints.NONE;
                gbc.anchor = GridBagConstraints.EAST;
                gbl.setConstraints(scenInfo, gbc);
                bp.add(scenInfo);
                gbc.fill = oldFill;
                gbc.anchor = oldAnchor;
            }

            return;
        }

        switch (op.optType)  // OTYPE_*
        {
        case SOCGameOption.OTYPE_BOOL:
        case SOCGameOption.OTYPE_UNKNOWN:
            {
                JCheckBox cb = new JCheckBox();
                initInterface_Opt1(op, cb, true, false, false, bp, gbl, gbc);
                cb.addItemListener(this);
            }
            break;

        case SOCGameOption.OTYPE_INT:
        case SOCGameOption.OTYPE_INTBOOL:
            {
                final boolean hasCheckbox = (op.optType == SOCGameOption.OTYPE_INTBOOL);
                initInterface_Opt1(op, initOption_int(op), hasCheckbox, true, true, bp, gbl, gbc);
            }
            break;

        case SOCGameOption.OTYPE_ENUM:
        case SOCGameOption.OTYPE_ENUMBOOL:
            // JComboBox (popup menu)
            {
                final boolean hasCheckbox = (op.optType == SOCGameOption.OTYPE_ENUMBOOL);
                initInterface_Opt1(op, initOption_enum(op), hasCheckbox, true, true, bp, gbl, gbc);
            }
            break;

        case SOCGameOption.OTYPE_STR:
        case SOCGameOption.OTYPE_STRHIDE:
            {
                int txtwid = op.maxIntValue;  // used as max length
                if (txtwid > 20)
                    txtwid = 20;
                final boolean doHide = (op.optType == SOCGameOption.OTYPE_STRHIDE);
                JTextField txtc = (doHide)
                    ? new JPasswordField(txtwid)
                    : new JTextField(op.getStringValue(), txtwid);
                if (! readOnly)
                {
                    txtc.addKeyListener(this);  // for ESC/ENTER
                    Document tfDoc = txtc.getDocument();
                    tfDoc.putProperty("owner", txtc);
                    tfDoc.addDocumentListener(this);  // for gameopt.ChangeListener and userChanged
                }
                initInterface_Opt1(op, txtc, false, false, false, bp, gbl, gbc);
            }
            break;

            // default: unknown, ignore; see above
        }
    }

    /**
     * Add one GridBagLayout row with this game option (component and label(s)).
     * The option's descriptive text may have "#" as a placeholder for where
     * int/enum value is specified (IntTextField or JComboBox dropdown).
     * @param op  Option data
     * @param oc  Component with option choices (popup menu, textfield, etc).
     *            If oc is a {@link JTextField} or {@link JComboBox}, and hasCB,
     *            changing the component's value will set the checkbox.
     *            <tt>oc</tt> will be added to {@link #optsControls} and {@link #controlsOpts}.
     * @param hasCB  Add a checkbox?  If oc is {@link JCheckbox}, set this true;
     *            it won't add a second checkbox.
     *            The checkbox will be added to {@link #boolOptCheckboxes} and {@link #controlsOpts}.
     * @param ocHasListener  If true, {@code oc} already has its ItemListener, KeyListener, DocumentListener, etc
     *            (probably added by {@link #initOption_int(SOCGameOption)} or {@link #initOption_enum(SOCGameOption)})
     *            and this method shouldn't add another one
     * @param allowPH  Allow the "#" placeholder within option desc?
     * @param bp  Add to this panel
     * @param gbl Use this layout
     * @param gbc Use these constraints; gridwidth will be set to 1 and then REMAINDER
     */
    private void initInterface_Opt1(SOCGameOption op, Component oc,
            final boolean hasCB, final boolean allowPH, final boolean ocHasListener,
            JPanel bp, GridBagLayout gbl, GridBagConstraints gbc)
    {
        final boolean isOSHighContrast = SwingMainDisplay.isOSColorHighContrast();
        JLabel L;

        // reminder: same gbc widths/weights are used in initInterface_UserPrefs/initInterface_Pref1

        gbc.gridwidth = 1;
        gbc.weightx = 0;
        if (hasCB)
        {
            JCheckBox cb;
            if (oc instanceof JCheckBox)
                cb = (JCheckBox) oc;
            else
                cb = new JCheckBox();
            controlsOpts.put(cb, op);
            cb.setSelected
                ((op.optType != SOCGameOption.OTYPE_UNKNOWN) ? op.getBoolValue() : true);
            cb.setEnabled(! readOnly);
            if (! isOSHighContrast)
            {
                cb.setBackground(null);  // needed on win32 to avoid gray border
                cb.setForeground(null);
            }
            gbl.setConstraints(cb, gbc);
            bp.add(cb);
            if (! readOnly)
            {
                boolOptCheckboxes.put(op.key, cb);
                cb.addItemListener(this);  // for op's ChangeListener and userChanged
            }
        } else {
            L = new JLabel();  // to fill checkbox's column
            gbl.setConstraints(L, gbc);
            bp.add(L);
        }

        final String opDesc = op.getDesc();
        final int placeholderIdx = allowPH ? opDesc.indexOf('#') : -1;
        JPanel optp = new JPanel();  // with FlowLayout
        if (! isOSHighContrast)
        {
            optp.setBackground(null);  // inherit from parent
            optp.setForeground(null);
        }
        try
        {
            FlowLayout fl = (FlowLayout) (optp.getLayout());
            fl.setAlignment(FlowLayout.LEFT);
            fl.setVgap(0);
            fl.setHgap(0);
        }
        catch (Throwable fle) {}

        // Any text to the left of placeholder in op.desc?
        if (placeholderIdx > 0)
        {
            L = new JLabel(opDesc.substring(0, placeholderIdx));
            if (! isOSHighContrast)
                L.setForeground(SwingMainDisplay.MISC_LABEL_FG_OFF_WHITE);
            optp.add(L);
            if (hasCB && ! readOnly)
            {
                controlsOpts.put(L, op);
                L.addMouseListener(this);  // Click label to toggle checkbox
            }
        }

        // JTextField or JComboBox at placeholder position
        if (! (oc instanceof JCheckBox))
        {
            controlsOpts.put(oc, op);
            oc.setEnabled(! readOnly);
            optp.add(oc);

            // add listeners, unless initOption_int or initOption_enum already did so
            if (hasCB && ! (readOnly || ocHasListener))
            {
                if (oc instanceof JTextField)
                {
                    ((JTextField) oc).addKeyListener(this);   // for ESC/ENTER
                    Document tfDoc = ((JTextField) oc).getDocument();
                    tfDoc.putProperty("owner", oc);
                    tfDoc.addDocumentListener(this);  // for enable/disable
                }
                else if (oc instanceof JComboBox)
                {
                    ((JComboBox<?>) oc).addItemListener(this);  // for related cb, and op.ChangeListener and userChanged
                }
            }
        }
        if (! readOnly)
            optsControls.put(op.key, oc);

        // Any text to the right of placeholder?  Also creates
        // the text label if there is no placeholder (placeholderIdx == -1).
        if (placeholderIdx + 1 < opDesc.length())
        {
            L = new JLabel(opDesc.substring(placeholderIdx + 1));
            if (! isOSHighContrast)
                L.setForeground(SwingMainDisplay.MISC_LABEL_FG_OFF_WHITE);
            optp.add(L);
            if (hasCB && ! readOnly)
            {
                controlsOpts.put(L, op);
                L.addMouseListener(this);  // Click label to toggle checkbox
            }
        }

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1;
        gbl.setConstraints(optp, gbc);
        bp.add(optp);
    }

    /**
     * Based on this game option's type, present its intvalue either as
     * a numeric textfield, or a popup menu if min/max are near each other.
     * The maximum min/max distance which creates a popup is {@link #INTFIELD_POPUP_MAXRANGE}.
     * @param op A SOCGameOption with an integer value, that is,
     *           of type {@link SOCGameOption#OTYPE_INT OTYPE_INT}
     *           or {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL}
     * @return an {@link IntTextField} or {@link JComboBox} popup menu
     */
    private Component initOption_int(SOCGameOption op)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        int optrange = op.maxIntValue - op.minIntValue;
        Component c;
        if ((optrange > INTFIELD_POPUP_MAXRANGE) || (optrange < 0))
        {
            // IntTextField with width based on number of digits in min/max .
            int amaxv = Math.abs(op.maxIntValue);
            int aminv = Math.abs(op.minIntValue);
            final int magn;
            if (amaxv > aminv)
                magn = amaxv;
            else
                magn = aminv;
            int twidth = 1 + (int) Math.ceil(Math.log10(magn));
            if (twidth < 3)
                twidth = 3;

            c = new IntTextField(op.getIntValue(), twidth);
            c.addKeyListener(this);   // for ESC/ENTER

            Document tfDoc = ((IntTextField) c).getDocument();
            tfDoc.putProperty("owner", c);
            tfDoc.addDocumentListener(this);  // for op.ChangeListener and userChanged
        } else {
            JComboBox<String> combo = new JComboBox<String>();
            for (int i = op.minIntValue; i <= op.maxIntValue; ++i)
                combo.addItem(Integer.toString(i));

            int defaultIdx = op.getIntValue() - op.minIntValue;
            if (defaultIdx > 0)
                combo.setSelectedIndex(defaultIdx);
            combo.addItemListener(this);  // for op.ChangeListener and userChanged
            c = combo;
        }

        return c;
    }

    /**
     * Create a popup menu for the choices of this enum.
     * @param op Game option, of type {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM}
     *           or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     */
    private JComboBox<String> initOption_enum(SOCGameOption op)
    {
        JComboBox<String> ch = new JComboBox<String>();
        final String[] chs = op.enumVals;
        for (int i = 0; i < chs.length; ++i)
            ch.addItem(chs[i]);

        int defaultIdx = op.getIntValue() - 1;  // enum numbering is 1-based
        if (defaultIdx > 0)
            ch.setSelectedIndex(defaultIdx);
        ch.addItemListener(this);  // for op.ChangeListener and userChanged

        return ch;
    }

    /**
     * Build UI for user preferences such as {@link SOCPlayerClient#PREF_SOUND_ON}
     * and {@link SOCPlayerInterface#PREF_SOUND_MUTE}. Fills {@link #localPrefs}.
     *<P>
     * Called from {@link #initInterface_Options(JPanel, GridBagLayout, GridBagConstraints)}.
     * @param bp  Add to this panel
     * @param gbl Use this layout
     * @param gbc Use these constraints
     * @since 1.2.00
     */
    private void initInterface_UserPrefs
        (final JPanel bp, final GridBagLayout gbl, final GridBagConstraints gbc)
    {
        // For current games we aren't playing in, don't show some prefs
        final boolean withPerGamePrefs = forNewGame || (pi != null);

        // thin <HR>-type spacer above prefs section

        JSeparator spacer = new JSeparator();
        if (! SwingMainDisplay.isOSColorHighContrast())
            spacer.setBackground(HEADER_LABEL_BG);
        gbl.setConstraints(spacer, gbc);
        bp.add(spacer);

        // reminder: same gbc widths/weights are used in initInterface_Opt1

        // PREF_HEX_GRAPHICS_SET is an integer for future expansion,
        // but right now there's only 2 options, so use checkbox for simpler UI
        boolean bval = (1 == UserPreferences.getPref(SOCPlayerClient.PREF_HEX_GRAPHICS_SET, 0));
        localPrefs.put(SOCPlayerClient.PREF_HEX_GRAPHICS_SET, bval );
        initInterface_Pref1( bp, gbl, gbc, SOCPlayerClient.PREF_HEX_GRAPHICS_SET,
                strings.get( "game.options.hex.classic.all" ),  // "Hex graphics: Use Classic theme (All games)"
                true, false, bval, 0,
                new PrefCheckboxListener()
                {
                    @Override
                    public void stateChanged( boolean check )
                    {
                        // flip the current state of the hex graphic preference checkbox.
                        localPrefs.put( SOCPlayerClient.PREF_HEX_GRAPHICS_SET,
                                ! (Boolean) localPrefs.get( SOCPlayerClient.PREF_HEX_GRAPHICS_SET ));
                    }
                } );

        initInterface_Pref1
            (bp, gbl, gbc, null,
             strings.get("game.options.sound.all"),  // "Sound effects (All games)"
             true, false,
             UserPreferences.getPref(SOCPlayerClient.PREF_SOUND_ON, true), 0,
             new PrefCheckboxListener()
             {
                 public void stateChanged(boolean check)
                 {
                     UserPreferences.putPref
                         (SOCPlayerClient.PREF_SOUND_ON, check);
                 }
             });

        // Per-PI prefs:
        if (withPerGamePrefs)
        {
            bval = (pi != null) ? pi.isSoundMuted() : false;
            localPrefs.put(SOCPlayerInterface.PREF_SOUND_MUTE, Boolean.valueOf(bval));
            initInterface_Pref1
                (bp, gbl, gbc, null,
                 strings.get("game.options.sound.mute_this"),  // "Sound: Mute this game"
                 true, false, bval, 0,
                 new PrefCheckboxListener()
                 {
                     public void stateChanged(boolean check)
                     {
                         if (pi != null)
                             pi.setSoundMuted(check);
                         else
                             localPrefs.put(SOCPlayerInterface.PREF_SOUND_MUTE, Boolean.valueOf(check));
                     }
                 });

            int ival = (pi != null)
                ? pi.getBotTradeRejectSec()
                : UserPreferences.getPref(SOCPlayerClient.PREF_BOT_TRADE_REJECT_SEC, -8);
            localPrefs.put(SOCPlayerClient.PREF_BOT_TRADE_REJECT_SEC, Integer.valueOf(ival));
            bval = (ival > 0);
            if (! bval)
                ival = -ival;
            initInterface_Pref1
                (bp, gbl, gbc, SOCPlayerClient.PREF_BOT_TRADE_REJECT_SEC,
                 strings.get("game.options.bot.auto_reject"),  // "Auto-reject bot trades after # seconds"
                 true, true, bval, ival, null);
        }

        bval = (0 < UserPreferences.getPref(SOCPlayerClient.PREF_FACE_ICON, SOCPlayer.FIRST_HUMAN_FACE_ID));
        localPrefs.put(SOCPlayerClient.PREF_FACE_ICON, Boolean.valueOf(bval));
        initInterface_Pref1
            (bp, gbl, gbc, SOCPlayerClient.PREF_FACE_ICON,
             strings.get("game.options.ui.remember_face_icon"),  // "Remember face icon"
             true, false,
             bval, 0, null);

        int ival = UserPreferences.getPref(SOCPlayerClient.PREF_UI_SCALE_FORCE, 0);
        localPrefs.put(SOCPlayerClient.PREF_UI_SCALE_FORCE, Integer.valueOf(ival));
        bval = (ival > 0);
        if (! bval)
            ival = (ival < 0) ? (-ival) : 1;
        initInterface_Pref1
            (bp, gbl, gbc, SOCPlayerClient.PREF_UI_SCALE_FORCE,
             strings.get("game.options.ui.scale.force"),  // "Force UI scale to # (requires restart)"
             true, true, bval, ival, null);
    }

    /**
     * Set up one preference row (desc label, checkbox and/or input box)
     * for {@link #initInterface_UserPrefs(JPanel, GridBagLayout, GridBagConstraints)}.
     * @param bp  Add to this panel
     * @param gbl Use this layout
     * @param gbc Use these constraints
     * @param key Pref key name to update in {@link #localPrefs} when changed,
     *     such as {@link SOCPlayerClient#PREF_BOT_TRADE_REJECT_SEC}, or {@code null}.
     *     If {@code hasBool} but not {@code hasInt}, will store {@link Boolean#TRUE} or {@code .FALSE} as key's value.
     *     If {@code hasInt} and can't parse text field contents, stores {@link Integer} 0 as key's value.
     *     If both bool and int, will store an {@code Integer} which is negative if checkbox is unchecked.
     * @param desc  Preference description text to show. If {@code hasInt}, must contain {@code "#"} placeholder.
     * @param hasBool  True if preference has a boolean value
     * @param hasInt   True if preference has an integer value
     * @param initBoolVal  Pref's initial boolean value, for checkbox; ignored unless {@code hasBool}
     * @param initIntVal   Pref's initial integer value, for input box; ignored unless {@code hasInt}
     * @param pcl  Callback when checkbox is checked/unchecked by clicking the box or its label, or {@code null}
     * @throws IllegalArgumentException if {@code hasInt} but {@code desc} doesn't contain {@code "#"},
     *     or if both {@code key} and {@code pcl} are {@code null}
     * @since 1.2.00
     */
    private void initInterface_Pref1
        (final JPanel bp, final GridBagLayout gbl, final GridBagConstraints gbc,
         final String key, final String desc, final boolean hasBool, final boolean hasInt,
         final boolean initBoolVal, final int initIntVal, final PrefCheckboxListener pcl)
        throws IllegalArgumentException
    {
        if ((key == null) && (pcl == null))
            throw new IllegalArgumentException("null key & pcl");

        final boolean isOSHighContrast = SwingMainDisplay.isOSColorHighContrast();

        // reminder: same gbc widths/weights are used in initInterface_Opt1

        final JCheckBox cb;
        final IntTextField itf = (hasInt) ? new IntTextField(initIntVal, 3) : null;
        final MouseListener ml;
        if (hasBool)
        {
            cb = new JCheckBox();
            cb.setSelected(initBoolVal);
            gbc.gridwidth = 1;
            gbc.weightx = 0;
            gbl.setConstraints(cb, gbc);
            if (! isOSHighContrast)
            {
                cb.setBackground(null);  // needed on win32 to avoid gray border
                cb.setForeground(null);
            }
            bp.add(cb);

            ml = new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    final boolean makeChecked = ! cb.isSelected();
                    cb.setSelected(makeChecked);
                    if (pcl != null)
                        pcl.stateChanged(makeChecked);
                    if (key != null)
                    {
                        if (hasInt)
                        {
                            int iv = 0;
                            try
                            {
                                iv = Integer.parseInt(itf.getText().trim());
                                if (! makeChecked)
                                    iv = -iv;
                            } catch (NumberFormatException nfe) {}

                            localPrefs.put(key, Integer.valueOf(iv));
                        } else {
                            localPrefs.put(key, (makeChecked) ? Boolean.TRUE : Boolean.FALSE);
                        }
                    }
                    e.consume();
                }
            };
        } else {
            cb = null;
            ml = null;
        }

        final int placeholderIdx;
        final JPanel prefp;  // null or holds label with start of desc, int input field, label with rest of desc
        if (hasInt)
        {
            placeholderIdx = desc.indexOf('#');
            if (placeholderIdx == -1)
                throw new IllegalArgumentException("missing '#'");

            prefp = new JPanel();  // with FlowLayout
            if (! isOSHighContrast)
            {
                prefp.setBackground(null);  // inherit from parent
                prefp.setForeground(null);
            }
            try
            {
                FlowLayout fl = (FlowLayout) (prefp.getLayout());
                fl.setAlignment(FlowLayout.LEFT);
                fl.setVgap(0);
                fl.setHgap(0);
            }
            catch (Exception fle) {}

        } else {
            placeholderIdx = -1;
            prefp = null;
        }

        // Any text to the left of placeholder in desc?
        if (placeholderIdx > 0)
        {
            JLabel L = new JLabel(desc.substring(0, placeholderIdx));
            if (! isOSHighContrast)
                L.setForeground(SwingMainDisplay.MISC_LABEL_FG_OFF_WHITE);
            prefp.add(L);
            L.addMouseListener(ml);
        }

        if (hasInt)
        {
            prefp.add(itf);

            itf.addKeyListener(this);   // for ESC/ENTER

            if ((cb != null) || (key != null))
            {
                Document tfDoc = itf.getDocument();
                tfDoc.putProperty("owner", itf);
                tfDoc.addDocumentListener(new DocumentListener()  // for value store or enable/disable
                {
                    public void removeUpdate(DocumentEvent e)  { textChanged(); }
                    public void insertUpdate(DocumentEvent e)  { textChanged(); }
                    public void changedUpdate(DocumentEvent e) { textChanged(); }

                    public void textChanged()
                    {
                        final String newText = itf.getText().trim();
                        final boolean notEmpty = (newText.length() > 0);

                        if (cb != null)
                        {
                            if (notEmpty != cb.isSelected())
                            {
                                cb.setSelected(notEmpty);
                                if (pcl != null)
                                    pcl.stateChanged(notEmpty);
                            }
                        }

                        if (key != null)
                        {
                            int iv = 0;
                            try
                            {
                                iv = Integer.parseInt(newText);
                                if ((cb != null) && ! cb.isSelected())
                                    iv = -iv;
                            } catch (NumberFormatException nfe) {}

                            localPrefs.put(key, Integer.valueOf(iv));
                        }
                    }
                });
            }
        }

        // Any text to the right of placeholder?  Also creates
        // the text label if there is no placeholder.
        if (placeholderIdx + 1 < desc.length())
        {
            JLabel L = new JLabel(desc.substring(placeholderIdx + 1));
            if (! isOSHighContrast)
                L.setForeground(SwingMainDisplay.MISC_LABEL_FG_OFF_WHITE);
            if (prefp != null)
            {
                prefp.add(L);
            } else {
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbc.weightx = 1;
                gbl.setConstraints(L, gbc);
                bp.add(L);
            }
            L.addMouseListener(ml);
        }

        if (prefp != null)
        {
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.weightx = 1;
            gbl.setConstraints(prefp, gbc);
            bp.add(prefp);
        }

        if ((cb != null) && (pcl != null))
            cb.addItemListener(new ItemListener()
            {
                public void itemStateChanged(ItemEvent ie)
                {
                    pcl.stateChanged(ie.getStateChange() == ItemEvent.SELECTED);
                }
            });
    }

    /**
     * When the window is shown, request focus on game name textfield.
     * To make this window topmost, call {@code setVisible(true)} instead of {@link #requestFocus()}.
     */
    @Override
    public void setVisible(boolean b)
    {
        super.setVisible(b);
        if (b)
        {
            EventQueue.invokeLater(new Runnable()
            {
                public void run()
                {
                    toFront();  // needed on win32 at least
                    gameName.requestFocus();
                }
            });
        }
    }

    /**
     * React to button clicks.
     *<P>
     * Even in read-only mode for a current game, the "OK" button saves (persists)
     * this dialog's local preferences for use in future games.
     */
    public void actionPerformed(ActionEvent ae)
    {
        try
        {
            Object src = ae.getSource();
            if (src == create)
            {
                // Check options, ask client to set up and start a practice game
                clickCreate(true);
            }
            else if (src == cancel)
            {
                clickCancel(true);
            }
            else if (src == scenInfo)
            {
                clickScenarioInfo();
            }
            else if (src == scenDropdown)
            {
                if (opts == null)
                    return;
                SOCGameOption optSC = opts.get("SC");
                if (optSC == null)
                    return;

                Object scObj = scenDropdown.getSelectedItem();
                boolean wantsSet = (scObj instanceof SOCScenario);  // item 0 is "(none)" string, not a scenario
                optSC.setBoolValue(wantsSet);
                if (wantsSet)
                    optSC.setStringValue(((SOCScenario) scObj).key);
                else
                    optSC.setStringValue("");

                if (scenInfo != null)
                    scenInfo.setEnabled(wantsSet);

                boolean choiceSetCB = false;
                JCheckBox cb = boolOptCheckboxes.get("SC");
                if ((cb != null) && (wantsSet != cb.isSelected()))
                {
                    cb.setSelected(wantsSet);
                    choiceSetCB = true;
                }

                fireUserChangedOptListeners(optSC, scenDropdown, wantsSet, choiceSetCB);
            }
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
     * The "Create" button was clicked; check fields, etc.
     * If creating new game, also calls {@link #persistLocalPrefs()}.
     */
    private void clickCreate(final boolean checkOptionsMinVers)
    {
        String gmName = gameName.getText().trim();
        final int L = gmName.length();
        if (L == 0)
        {
            return;  // Should not happen (button disabled by TextListener)
        }

        String errMsg = null;
        if (L > SOCGameList.GAME_NAME_MAX_LENGTH)
            errMsg = strings.get("netmsg.status.common.name_too_long", SOCGameList.GAME_NAME_MAX_LENGTH);
                // "Please choose a shorter name; maximum length: {0}"
        else if (-1 != gmName.indexOf(SOCMessage.sep_char))  // '|'
            errMsg = strings.get("netmsg.status.client.newgame_name_rejected_char", SOCMessage.sep_char);
                // Name must not contain "|", please choose a different name.
        else if (-1 != gmName.indexOf(SOCMessage.sep2_char))  // ','
            errMsg = strings.get("netmsg.status.client.newgame_name_rejected_char", SOCMessage.sep2_char);
                // Name must not contain ",", please choose a different name.
        else if ((gmName.charAt(0) == '?') || ! SOCMessage.isSingleLineAndSafe(gmName))
            errMsg = strings.get("netmsg.status.common.newgame_name_rejected");
                // "This name is not permitted, please choose a different name."
        else if (SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher(gmName).matches())
            errMsg = strings.get("netmsg.status.common.newgame_name_rejected_digits_or_punct");
                // "A name with only digits or punctuation is not permitted, please add a letter."

        if (errMsg != null)
        {
            msgText.setText(errMsg);
            gameName.requestFocusInWindow();
            return;  // Not a valid game name
        }

        SOCPlayerClient cl = mainDisplay.getClient();

        /**
         * Is this game name already used?
         * Always check remote server for the requested game name.
         * Check practice game names only if creating another practice game.
         */
        if (cl.doesGameExist(gmName, forPractice))
        {
            NotifyDialog.createAndShow
                (mainDisplay, this, strings.get("netmsg.status.common.newgame_already_exists"), null, true);
                    // "A game with this name already exists, please choose a different name."
            return;
        }

        if (mainDisplay.readValidNicknameAndPassword())
        {
            if (readOptsValuesFromControls(checkOptionsMinVers))
            {
                // All fields OK, ready to create a new game.
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));  // Immediate feedback in this window
                persistLocalPrefs();
                final SOCGameOptionSet optsForAskStartgame;
                if (opts != null)
                {
                    // One last check of options for problems,
                    // and don't send default-valued gameopts which have FLAG_DROP_IF_UNUSED flag
                    optsForAskStartgame = new SOCGameOptionSet(opts, true);
                    final Map<String, String> optProblems
                        = optsForAskStartgame.adjustOptionsToKnown(knownOpts, false, null);
                    if (optProblems != null)
                    {
                        StringBuilder errSB = new StringBuilder("Option problems: ");
                            // I18N OK: Is fallback only, previous checks should have caught problems
                        DataUtils.mapIntoStringBuilder(optProblems, errSB, null, "; ");
                        msgText.setText(errSB.toString());
                        gameName.requestFocusInWindow();  // draw attention to top of window
                        setCursor(Cursor.getDefaultCursor());
                        return;
                    }
                } else {
                    optsForAskStartgame = null;
                }
                mainDisplay.askStartGameWithOptions
                    (gmName, forPractice, optsForAskStartgame, localPrefs);  // sets WAIT_CURSOR in main client frame
            } else {
                return;  // readOptsValues will put the err msg in dia's status line
            }
        } else {
            // Nickname field is also checked before this dialog is displayed,
            // so the user must have gone back and changed it.
            // Can't correct the problem from within this dialog, since the
            // nickname field (and hint message) is in SOCPlayerClient's panel.
            NotifyDialog.createAndShow(mainDisplay, this, strings.get("game.options.nickerror"), null, true);
            return;
        }

        dispose();
    }

    /**
     * The "Cancel" button or window's close button was clicked, or ESC was pressed; dismiss the dialog.
     * Note: Button text is "OK" in read-only mode ({@link #readOnly}) for a current game.
     * @param savePrefsIfCurrent  If true, and is {@link #readOnly} for a current game ({@link #pi} != null),
     *     remember local-prefs changes by calling {@link #persistLocalPrefs()}.
     */
    private void clickCancel(final boolean savePrefsIfCurrent)
    {
        if (savePrefsIfCurrent && readOnly && (pi != null))
            persistLocalPrefs();

        if ((! readOnly) && (opts != null))
        {
            // If scenario checkbox was manually cleared, clear scenario-name dropdown selection
            // for next time this dialog is shown to create a new game

            final SOCGameOption optSC = opts.get("SC");
            if ((optSC != null) && ! optSC.getBoolValue())
                optSC.setStringValue("");
        }

        dispose();
    }

    /**
     * The "Scenario Info" button was clicked.
     * Reads the current scenario, if any, from {@link #scenDropdown}.
     * Calls {@link #showScenarioInfoDialog(SOCScenario, SOCGameOptionSet, SOCGameOptionSet, int, MainDisplay, Window)}.
     * @since 2.0.00
     */
    private void clickScenarioInfo()
    {
        if (scenDropdown == null)
            return;  // should not happen, scenDropdown is created before scenInfo

        final Object scObj = scenDropdown.getSelectedItem();
        if ((scObj == null) || ! (scObj instanceof SOCScenario))
            return;  // "(none)" item is a String, not a scenario

        final SOCScenario scen = (SOCScenario) scObj;

        // find game's vp_winner
        int vpWinner = SOCGame.VP_WINNER_STANDARD;
        boolean vpKnown = false;
        if (opts != null)
        {
            SOCGameOption vp = opts.get("VP");
            if (vp.getBoolValue())
            {
                vpWinner = vp.getIntValue();
                vpKnown = true;
            }
        }
        if (forNewGame && (! vpKnown) && scen.scOpts.contains("VP="))
        {
            final Map<String, SOCGameOption> scenOpts = SOCGameOption.parseOptionsToMap(scen.scOpts, knownOpts);
            final SOCGameOption scOptVP = (scenOpts != null) ? scenOpts.get("VP") : null;
            if (scOptVP != null)
                vpWinner = scOptVP.getIntValue();
        }

        showScenarioInfoDialog(scen, null, knownOpts, vpWinner, mainDisplay, this);
    }

    /**
     * Dismiss this dialog, and if client's {@link MainDisplay} has a reference to it,
     * clear it to null there.
     */
    @Override
    public void dispose()
    {
        mainDisplay.dialogClosed(this);

        if (gameInfoUpdateTimer != null)
            try
            {
                gameInfoUpdateTimer.cancel();
                gameInfoUpdateTimer = null;
            }
            catch (Throwable th) {}

        super.dispose();
    }

    /**
     * When window is closing, store any updated persistent local preferences
     * like {@link SOCPlayerClient#PREF_BOT_TRADE_REJECT_SEC}.
     * If {@link #pi} != null, update its settings too.
     *<P>
     * Prefs which update immediately when clicked, like {@link SOCPlayerClient#PREF_SOUND_ON},
     * aren't updated here.
     * @since 1.2.00
     */
    private void persistLocalPrefs()
    {
        String k = SOCPlayerClient.PREF_BOT_TRADE_REJECT_SEC;
        Object v = localPrefs.get(k);
        if ((v != null) && (v instanceof Integer))
        {
            int iv = ((Integer) v).intValue();
            if (pi != null)
                pi.setBotTradeRejectSec(iv);
            if (iv != 0)
                UserPreferences.putPref(k, iv);
        }

        k = SOCPlayerClient.PREF_UI_SCALE_FORCE;
        v = localPrefs.get(k);
        if ((v != null) && (v instanceof Integer))
        {
            int iv = ((Integer) v).intValue();
            if (iv > 3)
                iv = 3;
            UserPreferences.putPref(k, iv);
        }

        k = SOCPlayerClient.PREF_HEX_GRAPHICS_SET;
        v = localPrefs.get(k);
        int setIdx = (Boolean.TRUE.equals(v)) ? 1 : 0;
        if (setIdx != UserPreferences.getPref(SOCPlayerClient.PREF_HEX_GRAPHICS_SET, 0))
        {
            UserPreferences.putPref(SOCPlayerClient.PREF_HEX_GRAPHICS_SET, setIdx);
            mainDisplay.getClient().reloadBoardGraphics();  // refresh all current PIs
        }

        k = SOCPlayerClient.PREF_FACE_ICON;
        boolean wantsSet = ((Boolean) localPrefs.get(k)).booleanValue();
        setIdx = UserPreferences.getPref(SOCPlayerClient.PREF_FACE_ICON, 0);
        if (wantsSet != (0 < setIdx))
        {
            final SOCPlayerClient cli = mainDisplay.getClient();
            final boolean newAndNoActives = forNewGame && ! mainDisplay.hasAnyActiveGame(false);

            if (newAndNoActives && wantsSet && (cli.lastFaceChange == SOCPlayer.FIRST_HUMAN_FACE_ID) && (setIdx != 0))
            {
                // No active PI showing, wants to remember face icons.
                // Use saved pref's non-default face now, if available

                if (setIdx < 0)
                    setIdx = -setIdx;

                cli.lastFaceChange = setIdx;
            }

            if (newAndNoActives && ! wantsSet)
            {
                // No active PI showing, so reset PI's icon to default
                // but don't lose previously-saved value in prefs

                cli.lastFaceChange = SOCPlayer.FIRST_HUMAN_FACE_ID;
                UserPreferences.putPref(SOCPlayerClient.PREF_FACE_ICON, -setIdx);
            } else {
                UserPreferences.putPref(SOCPlayerClient.PREF_FACE_ICON,
                    (wantsSet) ? cli.lastFaceChange : -(cli.lastFaceChange));
            }

        }
    }

    /**
     * Read option values from controls, as prep to request the new game.
     * If there is a problem (out of range, bad character in integer field, etc),
     * set {@link #msgText} and set focus on the field.
     * @param checkOptionsMinVers Warn the user if the options will require a
     *           minimum client version?  Won't do so if {@link #forPractice} is set,
     *           because this isn't a problem for local practice games.
     *           The warning is skipped if that minimum is an old version
     *           &lt;= {@link Version#versionNumberMaximumNoWarn()}.
     * @return true if all were read OK, false if a problem (such as NumberFormatException)
     */
    private boolean readOptsValuesFromControls(final boolean checkOptionsMinVers)
    {
        if (readOnly)
            return false;  // shouldn't be called in that case

        boolean allOK = true;
        for (Component ctrl : controlsOpts.keySet())
        {
            if (ctrl instanceof JLabel)
                continue;
            SOCGameOption op = controlsOpts.get(ctrl);

            if (op.key.equals("SC"))
            {
                // Special case: event listeners have already set its value from controls
                if (! op.getBoolValue())
                    op.setStringValue("");
                continue;
            }

            // OTYPE_* - new option types may have new Swing component objects, or
            //           may use the same components with different contents as these.

            if (ctrl instanceof JCheckBox)
            {
                op.setBoolValue(((JCheckBox)ctrl).isSelected());
            }
            else if (ctrl instanceof JTextField)
            {
                String txt = ((JTextField) ctrl).getText().trim();
                if ((op.optType == SOCGameOption.OTYPE_STR)
                    || (op.optType == SOCGameOption.OTYPE_STRHIDE))
                {
                    try
                    {
                        op.setStringValue(txt);
                    } catch (IllegalArgumentException ex)
                    {
                        allOK = false;
                        msgText.setText(strings.get("game.options.singleline"));  // only a single line of text allowed
                        ctrl.requestFocusInWindow();
                    }
                } else {
                    // OTYPE_INT, OTYPE_INTBOOL; defer setting until after all checkboxes have been read
                }
            }
            else if (ctrl instanceof JComboBox)
            {
                // this works with OTYPE_INT, OTYPE_INTBOOL, OTYPE_ENUM, OTYPE_ENUMBOOL
                int chIdx = ((JComboBox<?>) ctrl).getSelectedIndex();  // 0 to n-1
                if (chIdx != -1)
                    op.setIntValue(chIdx + op.minIntValue);
                else
                    allOK = false;
            }

        }  // for(opts)

        // OTYPE_INT, OTYPE_INTBOOL: now that all checkboxes have been read,
        //   set int values and see if in range; ignore where bool is not set (checkbox not checked).
        //   Use 0 if blank (still checks if in range).
        for (Component ctrl : controlsOpts.keySet())
        {
            if (! (ctrl instanceof JTextField))
                continue;

            SOCGameOption op = controlsOpts.get(ctrl);
            if (op.optType == SOCGameOption.OTYPE_INTBOOL)
            {
                if (! op.getBoolValue())
                    continue;
            }
            else if (op.optType != SOCGameOption.OTYPE_INT)
            {
                continue;
            }

            String txt = ((JTextField) ctrl).getText().trim();
            try
            {
                int iv;
                if (txt.length() > 0)
                    iv = Integer.parseInt(txt);
                else
                    iv = 0;

                op.setIntValue(iv);
                if (iv != op.getIntValue())
                {
                    allOK = false;
                    msgText.setText
                        (strings.get("game.options.outofrange", op.minIntValue, op.maxIntValue));  // "out of range"
                    ctrl.requestFocusInWindow();
                }
            } catch (NumberFormatException ex)
            {
                allOK = false;
                msgText.setText(strings.get("game.options.onlydigits"));  // "please use only digits here"
                ctrl.requestFocusInWindow();
            }

        }  // for(opts)

        if (allOK && checkOptionsMinVers && ! forPractice)
        {
            Map<String, Integer> optsMins = new HashMap<>();
            int optsVers = SOCVersionedItem.itemsMinimumVersion(opts.getAll(), false, optsMins);
            if ((optsVers > -1) && (optsVers > Version.versionNumberMaximumNoWarn()))
            {
                allOK = false;
                new VersionConfirmDialog(this, optsVers, optsMins).setVisible(true);
            }
        }

        return allOK;
    }

    /** Handle Enter or Esc key (KeyListener) */
    public void keyPressed(KeyEvent e)
    {
        if (e.isConsumed())
            return;

        try
        {
            switch (e.getKeyCode())
            {
            case KeyEvent.VK_ENTER:
                e.consume();
                if (readOnly)
                    clickCancel(true);
                else
                    clickCreate(true);
                break;

            case KeyEvent.VK_CANCEL:
            case KeyEvent.VK_ESCAPE:
                e.consume();
                clickCancel(false);
                break;
            }  // switch(e)
        }  // try
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

    /** Stub required by KeyListener */
    public void keyReleased(KeyEvent arg0) { }

    /** Stub required by KeyListener */
    public void keyTyped(KeyEvent arg0) { }

    /** Listen for Game name contents change (DocumentListener) and call {@link #textChanged(DocumentEvent)}. */
        public void removeUpdate(DocumentEvent e)  { textChanged(e); }
        public void insertUpdate(DocumentEvent e)  { textChanged(e); }
        public void changedUpdate(DocumentEvent e) { textChanged(e); }

    /**
     * When gamename contents change, enable/disable buttons as appropriate. (DocumentListener)
     * Also handles {@link SOCGameOption#OTYPE_INTBOOL} textfield/checkbox combos.
     * Also sets {@link SOCGameOption#userChanged}.
     *<P>
     * Before v2.0.00 this was textValueChanged (AWT TextListener).
     *
     * @param e event from {@link #gameName}, or from a JTextField in {@link #controlsOpts}
     */
    public void textChanged(DocumentEvent e)
    {
        if (readOnly)
            return;
        Object srcObj = e.getDocument().getProperty("owner");
        if ((srcObj == null) || ! (srcObj instanceof JTextField))
            return;
        final String newText = ((JTextField) srcObj).getText().trim();
        final boolean notEmpty = (newText.length() > 0);
        if (srcObj == gameName)
        {
            if (notEmpty != create.isEnabled())
                create.setEnabled(notEmpty);  // enable "create" btn only if game name filled in
        }
        else
        {
            // Check for a ChangeListener for OTYPE_STR and OTYPE_STRHIDE,
            // OTYPE_INT and OTYPE_INTBOOL.
            // if source is OTYPE_INTBOOL, check its checkbox vs notEmpty.
            SOCGameOption opt = controlsOpts.get(srcObj);
            if (opt == null)
                return;

            final String oldText = opt.getStringValue();
            boolean validChange = false;
            boolean otypeIsInt;
            int oldIntValue = 0;

            if ((opt.optType == SOCGameOption.OTYPE_STR)
                 || (opt.optType == SOCGameOption.OTYPE_STRHIDE))
            {
                otypeIsInt = false;
                try
                {
                    opt.setStringValue(newText);
                    validChange = true;
                }
                catch (IllegalArgumentException ex) {}
            } else {
                otypeIsInt = true;
                try   // OTYPE_INT, OTYPE_INTBOOL
                {
                    final int iv = Integer.parseInt(newText);
                    oldIntValue = opt.getIntValue();
                    opt.setIntValue(iv);  // ignored if outside min,max range
                    if (iv == opt.getIntValue())
                        validChange = true;
                }
                catch (NumberFormatException ex) {}
            }

            if (validChange && ! opt.userChanged)
                opt.userChanged = true;

            // If this string or int option also has a bool checkbox,
            // set or clear that based on string/int not empty.
            boolean cbSet = false;
            JCheckBox cb = boolOptCheckboxes.get(opt.key);
            if ((cb != null) && (notEmpty != cb.isSelected()))
            {
                cb.setSelected(notEmpty);
                opt.setBoolValue(notEmpty);
                cbSet = true;
            }

            SOCGameOption.ChangeListener cl = opt.getChangeListener();
            if (cl == null)
                return;

            // If both bool and int fields are changed, update both before
            // calling fireOptionChangeListener.  Boolean is called before int.
            if (cbSet)
            {
                // ChangeListener for checkbox
                final Boolean newValue = (notEmpty) ? Boolean.TRUE : Boolean.FALSE;
                final Boolean oldValue = (notEmpty) ? Boolean.FALSE : Boolean.TRUE;
                fireOptionChangeListener(cl, opt, oldValue, newValue);
            }
            // ChangeListener for text field
            if (validChange)
            {
                if (otypeIsInt)
                    fireOptionChangeListener(cl, opt, Integer.valueOf(oldIntValue), Integer.valueOf(opt.getIntValue()));
                else
                    fireOptionChangeListener(cl, opt, oldText, newText);
            }
        }
    }

    /**
     * Called when a JComboBox or JCheckbox value changes (ItemListener).
     * Used for these things:
     *<UL>
     * <LI>
     * Set {@link SOCGameOption#userChanged}
     * <LI>
     * Check JComboBoxes or JCheckboxes to see if their game option has a {@link SOCGameOption.ChangeListener ChangeListener}.
     * <LI>
     * Set the checkbox when the popup-menu JComboBox value is changed for a
     * {@link SOCGameOption#OTYPE_INTBOOL} or {@link SOCGameOption#OTYPE_ENUMBOOL}.
     * <LI>
     * Update game option {@code "SC"} and the {@link #scenInfo} button when a scenario is picked
     * from {@link #scenDropdown}. Other scenario-related updates are handled by this method calling
     * {@link SOCGameOption.ChangeListener#valueChanged(SOCGameOption, Object, Object, SOCGameOptionSet, SOCGameOptionSet)}.
     *</UL>
     * @param e itemevent from a JComboBox or JCheckbox in {@link #controlsOpts}
     */
    public void itemStateChanged(ItemEvent e)
    {
        final Object ctrl = e.getSource();
        SOCGameOption opt = controlsOpts.get(ctrl);
        if (opt == null)
            return;

        boolean wasCBEvent = false, choiceSetCB = false;

        JCheckBox cb = boolOptCheckboxes.get(opt.key);
        if ((cb != null) && (cb != ctrl))
        {
            // If the user picked a choice, also set the checkbox
            boolean wantsSet = true;  // any item sets it

            if (wantsSet != cb.isSelected())
            {
                cb.setSelected(wantsSet);
                choiceSetCB = true;
            }
        }
        else if (ctrl instanceof JCheckBox)
        {
            wasCBEvent = true;
            choiceSetCB = (e.getStateChange() == ItemEvent.SELECTED);
        }

        fireUserChangedOptListeners(opt, ctrl, choiceSetCB, wasCBEvent);
    }

    /**
     * A game option's value widget was changed by the user.  If this game option has a
     * {@link SOCGameOption.ChangeListener}, call it with the appropriate old and new values.
     * Call to update {@code opt}'s value fields:
     *<UL>
     * <LI> If {@code changeBoolValue}, calls {@link SOCGameOption#setBoolValue(boolean) opt.setBoolValue(newBoolValue)}
     * <LI> If {@code ctrl} is a {@link JComboBox} or {@link JComboBox}, calls
     *      {@link SOCGameOption#setIntValue(int) opt.setIntValue}
     *      ({@link JComboBox#getSelectedIndex() ctrl.getSelectedIndex()})
     *</UL>
     * Calls {@link #fireOptionChangeListener(soc.game.SOCGameOption.ChangeListener, SOCGameOption, Object, Object)}
     * for the Option's boolean and/or int values.
     *
     * @param opt  Game option changed
     * @param ctrl  The {@link JCheckbox} or {@link JComboBox} dropdown changed by the user
     * @param newBoolValue  New value to set for {@link SOCGameOption#getBoolValue() opt.getBoolValue()}
     * @param changeBoolValue True if the user changed the opt's boolean value, false if
     *     the opt's int or string value dropdown was changed but boolean wasn't.
     * @since 2.0.00
     */
    final private void fireUserChangedOptListeners
        (final SOCGameOption opt, final Object ctrl, final boolean newBoolValue, final boolean changeBoolValue)
    {
        if (! opt.userChanged)
            opt.userChanged = true;

        SOCGameOption.ChangeListener cl = opt.getChangeListener();
        if (cl == null)
            return;

        // If both bool and int fields are changed, update both before
        // calling fireOptionChangeListener.  Boolean is called before int.
        final boolean fireBooleanListener;
        final Object boolOldValue, boolNewValue;

        if (newBoolValue || changeBoolValue)
        {
            fireBooleanListener = true;
            boolNewValue = (newBoolValue) ? Boolean.TRUE : Boolean.FALSE;
            boolOldValue = (newBoolValue) ? Boolean.FALSE : Boolean.TRUE;
            opt.setBoolValue(newBoolValue);
        } else {
            fireBooleanListener = false;
            boolNewValue = null;
            boolOldValue = null;
        }

        if (ctrl instanceof JComboBox)
        {
            int chIdx = ((JComboBox<?>) ctrl).getSelectedIndex();

            if (chIdx != -1)
            {
                final int nv = chIdx + opt.minIntValue;
                Integer newValue = Integer.valueOf(nv);
                Integer oldValue = Integer.valueOf(opt.getIntValue());
                opt.setIntValue(nv);
                if (fireBooleanListener)
                    fireOptionChangeListener(cl, opt, boolOldValue, boolNewValue);
                fireOptionChangeListener(cl, opt, oldValue, newValue);
            }
        }
        else if (fireBooleanListener)
            fireOptionChangeListener(cl, opt, boolOldValue, boolNewValue);
    }

    /**
     * Handle firing a game option's ChangeListener, and refreshing related
     * gameopts' values on-screen if needed.
     * If <tt>oldValue</tt>.equals(<tt>newValue</tt>), nothing happens and
     * the ChangeListener is not called.
     *<P>
     * To avoid redundant calls and avoid setting {@link SOCGameOption#userChanged} flag
     * from a programmatic value change, removes and re-adds {@code this} NGOF
     * from gameopts' Swing widget listeners.
     *
     * @param cl  The ChangeListener; must not be null
     * @param opt  The game option
     * @param oldValue  Old value, string or boxed primitive
     * @param newValue  New value, string or boxed primitive
     * @since 1.1.13
     */
    private void fireOptionChangeListener
        (SOCGameOption.ChangeListener cl, SOCGameOption opt, final Object oldValue, final Object newValue)
    {
        if (oldValue.equals(newValue))
            return;  // <--- Early return: Value didn't change ---

        try
        {
            cl.valueChanged(opt, oldValue, newValue, opts, knownOpts);
        } catch (Throwable thr) {
            System.err.println("-- Error caught in ChangeListener: " + thr.toString() + " --");
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

        List<SOCGameOption> refresh = SOCGameOption.getAndClearRefreshList();
        if (refresh == null)
            return;  // <--- Early return: Nothing else changed ---

        // Refresh each one now, depending on type:
        if (optsControls == null)
            return;  // <--- Early return; should be null only if readOnly, and thus no changes to values anyway ---

        for (int i = refresh.size() - 1; i >= 0; --i)
        {
            final SOCGameOption op = refresh.get(i);
            final Component opComp = optsControls.get(op.key);

            // reminder: Swing widget listeners are added in initInterface_OptLine, initInterface_Opt1, initOption_int,
            // initOption_enum; see those methods to confirm which widget types get which listeners

            switch (op.optType)  // OTYPE_*
            {
            case SOCGameOption.OTYPE_BOOL:
                {
                    final JCheckBox cb = (JCheckBox) opComp;
                    cb.removeItemListener(this);
                    cb.setSelected(op.getBoolValue());
                    cb.addItemListener(this);
                }
                break;

            case SOCGameOption.OTYPE_INT:
            case SOCGameOption.OTYPE_INTBOOL:
                {
                    if (opComp instanceof JTextField)
                    {
                        final JTextField tf = (JTextField) opComp;
                        final Document doc = tf.getDocument();
                        doc.removeDocumentListener(this);
                        tf.setText(Integer.toString(op.getIntValue()));
                        doc.addDocumentListener(this);
                    } else {
                        final JComboBox<?> combo = (JComboBox<?>) opComp;
                        combo.removeItemListener(this);
                        combo.setSelectedIndex(op.getIntValue() - op.minIntValue);
                        combo.addItemListener(this);
                    }

                    final boolean hasCheckbox = (op.optType == SOCGameOption.OTYPE_INTBOOL);
                    if (hasCheckbox)
                    {
                        JCheckBox cb = boolOptCheckboxes.get(op.key);
                        if (cb != null)
                        {
                            cb.removeItemListener(this);
                            cb.setSelected(op.getBoolValue());
                            cb.addItemListener(this);
                        }
                    }
                }
                break;

            case SOCGameOption.OTYPE_ENUM:
            case SOCGameOption.OTYPE_ENUMBOOL:
                {
                    final JComboBox<?> combo = (JComboBox<?>) opComp;
                    combo.removeItemListener(this);
                    combo.setSelectedIndex(op.getIntValue() - op.minIntValue);
                    combo.addItemListener(this);

                    final boolean hasCheckbox = (op.optType == SOCGameOption.OTYPE_ENUMBOOL);
                    if (hasCheckbox)
                    {
                        JCheckBox cb = boolOptCheckboxes.get(op.key);
                        if (cb != null)
                        {
                            cb.removeItemListener(this);
                            cb.setSelected(op.getBoolValue());
                            cb.addItemListener(this);
                        }
                    }
                }
                break;

            case SOCGameOption.OTYPE_STR:
            case SOCGameOption.OTYPE_STRHIDE:
                {
                    final JTextField tf = (JTextField) opComp;
                    final Document doc = tf.getDocument();
                    doc.removeDocumentListener(this);
                    tf.setText(op.getStringValue());
                    doc.addDocumentListener(this);
                }
                break;

                // default: unknown, see above
            }
        }
    }

    /** when an option with a boolValue's label is clicked, toggle its checkbox */
    public void mouseClicked(MouseEvent e)
    {
        SOCGameOption opt = controlsOpts.get(e.getSource());
        if (opt == null)
            return;
        JCheckBox cb = boolOptCheckboxes.get(opt.key);
        if (cb == null)
            return;

        final boolean becameChecked = ! cb.isSelected();
        cb.setSelected(becameChecked);
        opt.setBoolValue(becameChecked);
        if (! opt.userChanged)
            opt.userChanged = true;

        SOCGameOption.ChangeListener cl = opt.getChangeListener();
        if (cl == null)
            return;

        final Boolean newValue = (becameChecked) ? Boolean.TRUE : Boolean.FALSE;
        final Boolean oldValue = (becameChecked) ? Boolean.FALSE : Boolean.TRUE;
        fireOptionChangeListener(cl, opt, oldValue, newValue);
    }

    /** required stub for MouseListener */
    public void mouseEntered(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mouseExited(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mousePressed(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mouseReleased(MouseEvent e) {}

    /**
     * Show a popup window with this game's scenario's description, special rules, and number of victory points to win.
     * Calls {@link EventQueue#invokeLater(Runnable)}.
     * @param ga  Game to display scenario info for; if game option {@code "SC"} missing or blank, does nothing.
     * @param knownOpts  Server's Known Options, from {@link ServerGametypeInfo#knownOpts}
     * @param md    Player client's main display, for {@link NotifyDialog} call
     * @param parent  Current game's player interface, or another Frame or Dialog for our parent window,
     *                or null to look for {@code cli}'s Frame/Dialog as parent
     * @since 2.0.00
     */
    public static void showScenarioInfoDialog
        (final SOCGame ga, final SOCGameOptionSet knownOpts, final MainDisplay md, final Window parent)
    {
        final String scKey = ga.getGameOptionStringValue("SC");
        if (scKey == null)
            return;

        SOCScenario sc = SOCScenario.getScenario(scKey);
        if (sc == null)
            return;

        showScenarioInfoDialog(sc, ga.getGameOptions(), knownOpts, ga.vp_winner, md, parent);
    }

    /**
     * Show a popup window with this scenario's description, special rules, and number of victory points to win.
     * Calls {@link EventQueue#invokeLater(Runnable)}.
     * @param sc  A {@link SOCScenario}, or {@code null} to do nothing
     * @param gameOpts  All game options if current game, or null to extract from {@code sc}'s {@link SOCScenario#scOpts}
     * @param knownOpts Server's Known Options, from {@link ServerGametypeInfo#knownOpts}
     * @param vpWinner  Number of victory points to win, or {@link SOCGame#VP_WINNER_STANDARD}.
     * @param md     Player client's main display, required for {@link AskDialog} constructor
     * @param parent  Current game's player interface, or another Frame or Dialog for our parent window,
     *                or null to look for {@code cli}'s Frame/Dialog as parent
     * @since 2.0.00
     */
    public static void showScenarioInfoDialog
        (final SOCScenario sc, SOCGameOptionSet gameOpts, SOCGameOptionSet knownOpts, final int vpWinner,
         final MainDisplay md, final Window parent)
    {
        if (sc == null)
            return;

        StringBuilder sb = new StringBuilder();
        sb.append(strings.get("game.options.scenario.label"));  // "Game Scenario:"
        sb.append(' ');
        sb.append(sc.getDesc());
        sb.append('\n');

        final String scLongDesc = sc.getLongDesc();
        if (scLongDesc != null)
        {
            sb.append('\n');
            sb.append(scLongDesc);
            sb.append('\n');
        }

        // Check game for any other _SC_ game opts in effect:

        final String scenOptName = "_" + sc.key;  // "_SC_CLVI"
        final String optDescScenPrefix = strings.get("game.options.scenario.optprefix");  // "Scenarios:"
        //      I18N note: showScenarioInfoDialog() assumes scenario game options
        //      all start with the text "Scenarios:". When localizing, be sure to
        //      keep a consistent prefix that showScenarioInfoDialog() knows to look for.

        if ((gameOpts == null) && (sc.scOpts != null))
            gameOpts = SOCGameOption.parseOptionsToSet(sc.scOpts, knownOpts);

        if (gameOpts != null)
        {
            for (SOCGameOption sgo : gameOpts)
            {
                if (sgo.key.equals(scenOptName))
                    continue;  // scenario's dedicated game option; we already showed its name from scDesc
                if (! sgo.key.startsWith("_SC_"))
                    continue;

                String optDesc = sgo.getDesc();
                if (optDesc.startsWith(optDescScenPrefix))
                    optDesc = optDesc.substring(optDescScenPrefix.length()).trim();
                sb.append("\n\u2022 ");  // bullet point before option text
                sb.append(optDesc);
            }
        }

        if (vpWinner != SOCGame.VP_WINNER_STANDARD)
        {
            sb.append("\n\u2022 ");
            sb.append(strings.get("game.options.scenario.vp"));  // "Victory Points to win:"
            sb.append(' ');
            sb.append(vpWinner);
        }

        final String scenStr = sb.toString();
        NotifyDialog.createAndShow(md, parent, scenStr, null, true);
    }

    /**
     * Get the name of this NGOF's existing game, if purpose isn't to create a new game.
     * @return existing game's name, or {@code null} if new game
     * @since 2.7.00
     */
    public String getExistingGameName()
    {
        return existingGameName;
    }

    /**
     * Callback for when a game's timing stats are received from the server.
     * Shows that info and, if {@code isStarted} but game not over, starts a timer to
     * show the game's increasing age once per minute.
     *
     * @param creationTimeSeconds  Time game was created,
     *     in same format as {@link System#currentTimeMillis()} / 1000; not 0
     * @param isStarted  True if gameplay has began
     * @param durationFinishedSeconds  If game is over, duration in seconds from creation to end of game;
     *     otherwise 0
     * @since 2.7.00
     */
    public void gameTimingStatsReceived
        (final long creationTimeSeconds, final boolean isStarted, final int durationFinishedSeconds)
    {
        gameCreationTimeSeconds = creationTimeSeconds;
        gameIsStarted = isStarted;
        gameDurationFinishedSeconds = durationFinishedSeconds;

        if (isStarted && (durationFinishedSeconds == 0))
            initGameInfoUpdateTimer(false);
        else
            updateGameInfo();
    }

    /**
     * Use current game data to update the info shown in the "Game Info" row, if shown.
     * Does nothing if new game or if game data unavailable.
     * @since 2.7.00
     */
    public void updateGameInfo()
    {
        if ((gameInfo == null) || ((pi == null) && (gameCreationTimeSeconds == 0)))
            return;

        String txt;
        final SOCGame ga = (gameCreationTimeSeconds == 0) ? pi.getGame() : null;
        final int gaState = (ga != null) ? ga.getGameState() : 0;
        if ((gaState < SOCGame.START1A) && ! gameIsStarted)
        {
            txt = strings.get("game.options.not_started_yet");  // "Not started yet"
        } else {
            final boolean serverSendsTiming = (gameVersion >= SOCGameStats.VERSION_FOR_TYPE_TIMING);
            final boolean isGameOver = (ga != null)
                ? (gaState >= SOCGame.OVER)
                : (gameDurationFinishedSeconds != 0);
            if (isGameOver)
            {
                final int durSeconds = (ga != null) ? ga.getDurationSeconds() : gameDurationFinishedSeconds,
                    durMinutes = (durSeconds + 30) / 60;
                txt = (serverSendsTiming)
                    ? strings.get("game.options.finished_minutes", durMinutes)  // "Finished after playing {0} minutes"
                    : strings.get("game.options.finished");  // "Finished"
            } else {
                final int durSeconds = (ga != null)
                    ? ga.getDurationSeconds()
                    : (int) ((System.currentTimeMillis() / 1000) - gameCreationTimeSeconds),
                durMinutes = (durSeconds + 30) / 60;
                txt = strings.get
                    ((serverSendsTiming)
                        ? "game.options.in_progress_created_minutes"  // "In progress; created {0} minutes ago"
                        : "game.options.in_progress_joined_minutes"   // "In progress; joined {0} minutes ago"
                     , durMinutes);
            }
        }

        gameInfo.setText(txt);
    }

    /**
     * Builds a multi-line list of game option localized descriptions and minimum versions.
     * Each line's format is "version: opt desc": {@code "2.7.00: Allow undo piece builds and moves"}
     * followed by a newline character {@code '\n'}.
     *
     * @param optsMins  Map of gameopt keys -> minVers,
     *     from {@link SOCVersionedItem#itemsMinimumVersion(Map, boolean, Map)};
     *     can be null or empty
     * @param versIgnoreMax  Ignores {@code optsMins} entries whose version is this or lower,
     *     like {@link Version#versionNumberMaximumNoWarn()}, or -1 to use all entries
     * @return {@code optsMins} entries' version numbers and localized {@link SOCGameOption#getDesc()}s,
     *     one line per entry, or {@code ""} if none
     * @since 2.7.00
     */
    private StringBuilder buildOptionVersionList
        (final Map<String, Integer> optsMins, final int versIgnoreMax)
    {
        StringBuilder sb = new StringBuilder();

        if (optsMins != null)
        {
            ArrayList<String> okeys = new ArrayList<>(optsMins.keySet());
            Collections.sort(okeys);
            for (String okey : okeys)
            {
                final int vers = optsMins.get(okey).intValue();
                if ((versIgnoreMax > -1) && (vers <= versIgnoreMax))
                    continue;

                sb.append(Version.version(vers)).append(": ");
                sb.append(knownOpts.get(okey).getDesc());
                sb.append('\n');
            }
        }

        return sb;
    }


    /**
     * A textfield that accepts only nonnegative-integer characters.
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     */
    public class IntTextField extends JTextField implements KeyListener
    {
        IntTextField(int initVal, int width)
        {
            super(Integer.toString(initVal), width);
            addKeyListener(this);
        }

        /**
         * Parse the value of this textfield
         * @return value, or 0 if can't parse it
         */
        public int getIntValue()
        {
            String txt = getText().trim();
            if (txt.length() == 0)
                return 0;
            try
            {
                return Integer.parseInt(txt);
            }
            catch (NumberFormatException e)
            {
                return 0;
            }
        }

        /** stub for KeyListener */
        public void keyPressed(KeyEvent e) {}

        /** stub for KeyListener */
        public void keyReleased(KeyEvent e) {}

        /** reject entered characters which aren't digits */
        public void keyTyped(KeyEvent e)
        {
            if (e.isConsumed())
                return;

            // TODO this is not always rejecting non-digits

            switch (e.getKeyCode())
            {
            case KeyEvent.VK_ENTER:
                e.consume();
                if (readOnly)
                    clickCancel(true);
                else
                    clickCreate(true);
                break;

            case KeyEvent.VK_CANCEL:
            case KeyEvent.VK_ESCAPE:
                e.consume();
                clickCancel(false);
                break;

            default:
                {
                    final char c = e.getKeyChar();
                    switch (c)
                    {
                    case KeyEvent.CHAR_UNDEFINED:  // ctrl characters, arrows, etc
                    case (char) 8:    // backspace
                    case (char) 127:  // delete
                        return;  // don't consume

                    default:
                        if (! Character.isDigit(c))
                            e.consume();  // ignore non-digits
                    }
                }
            }  // switch(e)
        }

    }  // public inner class IntTextField


    /**
     * Callback for when a user preference checkbox is checked/unchecked by clicking that box or its label.
     * @see NewGameOptionsFrame#initInterface_Pref1(JPanel, GridBagLayout, GridBagConstraints, String, String, boolean, boolean, boolean, int, PrefCheckboxListener)
     * @since 1.2.00
     */
    private static interface PrefCheckboxListener
    {
        /**
         * Callback for when checkbox becomes checked or unchecked.
         * Also called when checkbox's label is clicked.
         * @param check New value of checkbox: True if becoming checked
         */
        public void stateChanged(final boolean check);
    }


    /**
     * This is the modal dialog to ask user if these options' required
     * minimum client version is OK.
     *
     * @author Jeremy D Monin <jeremy@nand.net>
     */
    private class VersionConfirmDialog extends AskDialog
    {
        /**
         * Creates a new VersionConfirmDialog.
         *
         * @param ngof  Parent options-frame, which contains these options
         * @param minVers  Minimum required version for these options
         * @param optsMins  Map of options which require a minimum version,
         *     from {@link SOCVersionedItem#itemsMinimumVersion(Map, boolean, Map)}, or null or empty if none
         */
        public VersionConfirmDialog(NewGameOptionsFrame ngof, int minVers, Map<String, Integer> optsMins)
        {
            super(mainDisplay, ngof, strings.get("game.options.verconfirm.title"),
                strings.get
                    ("game.options.verconfirm.prompt", Version.version(minVers),
                     buildOptionVersionList(optsMins, Version.versionNumberMaximumNoWarn())),
                strings.get("game.options.verconfirm.create"),
                strings.get("game.options.verconfirm.change"), true, false);
        }

        /**
         * React to the Create button.
         */
        @Override
        public void button1Chosen()
        {
            clickCreate(false);
        }

        /**
         * React to the Change button.
         */
        @Override
        public void button2Chosen()
        {
            dispose();
        }

        /**
         * React to the dialog window closed by user, or Esc pressed. (same as Change button)
         */
        @Override
        public void windowCloseChosen()
        {
            button2Chosen();
        }

    }  // private inner class VersionConfirmDialog


}  // public class NewGameOptionsFrame
