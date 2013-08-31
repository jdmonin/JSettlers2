/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2013 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
 *     - UI layer refactoring, GameStatistics, type parameterization, GUI API updates, etc
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

import soc.client.SOCPlayerClient.GameAwtDisplay;
import soc.client.stats.SOCGameStatistics;
import soc.debug.D;  // JM

import soc.game.SOCCity;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCRoad;
import soc.game.SOCScenario;
import soc.game.SOCScenarioEventListener;
import soc.game.SOCScenarioGameEvent;
import soc.game.SOCScenarioPlayerEvent;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCVillage;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import java.io.PrintWriter;  // For chatPrintStackTrace
import java.io.StringWriter;

/**
 * Window with interface for a player in one game of Settlers of Catan.
 * Contains {@link SOCBoardPanel board}, client's and other players' {@link SOCHandPanel hands},
 * chat interface, game message window, and the {@link SOCBuildingPanel building/buying panel}.
 *<P>
 * Players' {@link SOCHandPanel hands} start with player 0 at top-left, and go clockwise;
 * see {@link #doLayout()} for details.
 *<P>
 * When we join a game, the client will update visible game state by calling methods here like
 * {@link #addPlayer(String, int)}; when all this activity is complete, and the interface is
 * ready for interaction, the client calls {@link #began(Vector)}.
 *<P>
 * A separate {@link SOCPlayerClient} window holds the list of current games and channels.
 *
 * @author Robert S. Thomas
 */
public class SOCPlayerInterface extends Frame
    implements ActionListener, MouseListener, SOCScenarioEventListener
{
    //strings
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * System property os.name; For use by {@link #SOCPI_isPlatformWindows}.
     * @since 1.1.08
     */
    private final static String SOCPI_osName = System.getProperty("os.name");

    /**
     * Are we running on the Windows platform, according to {@link #SOCPI_osName}?
     * @since 1.1.08
     */
    private final static boolean SOCPI_isPlatformWindows = (SOCPI_osName != null) && (SOCPI_osName.toLowerCase().indexOf("windows") != -1);

    /**
     * the board display
     */
    protected SOCBoardPanel boardPanel;

    /**
     * Is the boardpanel stretched beyond normal size in {@link #doLayout()}?
     * @see SOCBoardPanel#isScaled()
     */
    protected boolean boardIsScaled;

    /**
     * Is this game using the 6-player board?
     * Checks {@link SOCGame#maxPlayers}.
     * @since 1.1.08
     */
    private boolean is6player;

    /**
     * For perf/display-bugs during component layout (OSX firefox),
     * show only background color in {@link #update(Graphics)} when true.
     * @since 1.1.06
     */
    private boolean layoutNotReadyYet;

    /**
     * True if we've already shown {@link #game}'s scenario's
     * descriptive text in a popup window when the client joined,
     * or if the game has no scenario.
     * Checked in {@link #doLayout()}.
     * Shown just once, not shown again at {@link #resetBoard(SOCGame, int, int)}.
     * @since 2.0.00
     */
    private boolean didGameScenarioPopupCheck;

    //========================================================
    /**
     * Text/chat fields begin here
     */
    //========================================================

    /**
     * where the player types in text
     */
    protected TextField textInput;

    /**
     * Not yet typed-in; display prompt message.
     *
     * @see #textInput
     * @see #TEXTINPUT_INITIAL_PROMPT_MSG
     */
    protected boolean textInputIsInitial;

    /**
     * At least one text chat line has been sent by the player.
     * Don't show the initial prompt message if the text field
     * becomes blank again.
     *
     * @see #textInput
     * @see #TEXTINPUT_INITIAL_PROMPT_MSG
     */
    protected boolean textInputHasSent;

    /**
     * Number of change-of-turns during game, after which
     * the initial prompt message fades to light grey.
     *
     * @see #textInput
     * @see #textInputGreyCountFrom
     */
    protected int textInputGreyCountdown;
    
    /**
     * Initial value (20 turns) for textInputGreyCountdown
     *
     * @see #textInputGreyCountdown
     */
    protected static int textInputGreyCountFrom = 20;

    /**
     * Not yet typed-in; display prompt message.
     *
     * @see #textInput
     */
    public static final String TEXTINPUT_INITIAL_PROMPT_MSG
        = /*I*/"Type here to chat."/*18N*/;

    /** Titlebar text for game in progress */
    public static final String TITLEBAR_GAME
        = "Settlers of Catan Game: ";  //i18n not neccesary
 
    /** Titlebar text for game when over */
    public static final String TITLEBAR_GAME_OVER
        = "Settlers of Catan Game Over: "; //i18n not neccesary;

    /**
     * Used for responding to textfield changes by setting/clearing prompt message.
     *
     * @see #textInput
     */
    protected SOCPITextfieldListener textInputListener;

    /**
     * where text is displayed.
     * In the 6-player layout, size expands when hovered over with mouse.
     * @see #textDisplaysLargerTemp
     */
    protected SnippingTextArea textDisplay;

    /**
     * where chat text is displayed.
     * In the 6-player layout, size expands when hovered over with mouse.
     * @see #textDisplaysLargerTemp
     */
    protected SnippingTextArea chatDisplay;

    /**
     * If any text area is clicked in a 6-player game, give
     * focus to the text input box; for use with {@link #mouseClicked(MouseEvent)}.
     * This is for ease-of-use because these boxes move/expand in
     * the 6-player board just as the mouse is near them, so it's
     * more difficult to click on the text input box.
     *<P>
     * If the user actually wants to click in the text box instead,
     * they can click there again 1 second after the text displays
     * are made larger.
     *<P>
     * When the text fields are un-expanded because the mouse moves away from them,
     * that timer is reset to 0, and the next click will again give focus
     * to the input text box.
     * @since 1.1.13
     */
    private long textDisplaysLargerWhen;

    /**
     * In the {@link #is6player 6-player} layout, the text display fields
     * ({@link #textDisplay}, {@link #chatDisplay}) aren't as large.
     * When this flag is set, they've temporarily been made larger.
     * @see SOCPITextDisplaysLargerTask
     * @see #textDisplaysLargerWhen
     * @since 1.1.08
     */
    private boolean textDisplaysLargerTemp;

    /**
     * When set, must return text display field sizes to normal in {@link #doLayout()}
     * after a previous {@link #textDisplaysLargerTemp} flag set.
     * @since 1.1.08
     */
    private boolean textDisplaysLargerTemp_needsLayout;

    /**
     * Mouse hover flags, for use on 6-player board with {@link #textDisplaysLargerTemp}.
     *<P>
     * Set/cleared in {@link #mouseEntered(MouseEvent)}, {@link #mouseExited(MouseEvent)}.
     * @see SOCPITextDisplaysLargerTask
     * @since 1.1.08
     */
    private boolean textInputHasMouse, textDisplayHasMouse, chatDisplayHasMouse;

    /**
     * In 6-player games, text areas temporarily zoom when the mouse is over them.
     * On windows, the scrollbars aren't considered part of the text areas, so
     * we get a mouseExited when user is trying to scroll the text area.
     * Workaround: Instead of looking for mouseExited, look for mouseEntered on
     * handpanels or boardpanel.
     * @see #textDisplaysLargerTemp
     * @see #sbFixBHasMouse
     */
    private boolean sbFixNeeded;

    /**
     * Mouse hover flags, for use on 6-player board with {@link #textDisplaysLargerTemp}
     * and {@link #sbFixNeeded}. Used only on platforms (windows) where the scrollbar isn't
     * considered part of the textarea and triggers a mouseExited.
     *<P>
     * Set/cleared in {@link #mouseEntered(MouseEvent)}, {@link #mouseExited(MouseEvent)}.
     * @see SOCPITextDisplaysLargerTask
     * @since 1.1.08
     */
    private boolean sbFixLHasMouse, sbFixRHasMouse, sbFixBHasMouse;

    //========================================================
    /**
     * End of text/chat fields
     */
    //========================================================

    /**
     * interface for building pieces
     */
    protected SOCBuildingPanel buildingPanel;

    /**
     * the display for the players' hands.
     * Hands start at top-left and go clockwise.
     */
    protected SOCHandPanel[] hands;
    
    /**
     * Tracks our own hand within hands[], if we are
     * active in a game.  Null otherwise.
     * Set by SOCHandPanel's removePlayer() and addPlayer() methods.
     */
    protected SOCHandPanel clientHand;

    /**
     * Player ID of clientHand, or -1.
     * Set by SOCHandPanel's removePlayer() and addPlayer() methods.
     */
    private int clientHandPlayerNum;

    /**
     * the player colors. Indexes from 0 to {@link SOCGame#maxPlayers} - 1.
     * Initialized in constructor.
     * @see #getPlayerColor(int, boolean)
     */
    protected Color[] playerColors, playerColorsGhost;

    /**
     * the display that spawned us
     */
    protected GameAwtDisplay gameDisplay;
    protected SOCPlayerClient client;

    /**
     * the game associated with this interface
     */
    protected SOCGame game;

    /**
     * Flag to ensure interface is updated, when the first actual
     * turn begins (state changes from {@link SOCGame#START2B} or {@link SOCGame#START3B}
     * to {@link SOCGame#PLAY}).
     * Initially set in {@link #startGame()}.
     * Checked/cleared in {@link #updateAtGameState()};
     */
    protected boolean gameIsStarting;

    /**
     * this other player has requested a board reset; voting is under way.
     * Null if no board reset vote is under way.
     *
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     */
    protected SOCHandPanel boardResetRequester;

    /**
     * Board reset voting: If voting is active and we haven't yet voted,
     * track our dialog; this lets us dispose of it if voting is cancelled.
     */
    protected ResetBoardVoteDialog boardResetVoteDia;

    /** Is one or more {@link SOCHandPanel} (of other players) showing a
     *  "Discarding..." or "Picking resource..." message?
     */
    private boolean showingPlayerDiscardOrPick;

    /**
     * Synchronize access to {@link #showingPlayerDiscardOrPick}
     * and {@link #showingPlayerDiscardOrPick_task}
     */
    private Object showingPlayerDiscardOrPick_lock;

    /** May be null if not {@link #showingPlayerDiscardOrPick}. */
    private SOCPIDiscardOrPickMsgTask showingPlayerDiscardOrPick_task;

    /**
     * number of columns in the text output area
     */
    protected int ncols;

    /**
     * width of text output area in pixels
     */
    protected int npix;

    /**
     * Size of our window from {@link #getSize()}, not excluding insets.
     * Determined in {@link #doLayout()}, or null.
     * @since 1.1.11
     */
    private Dimension prevSize;

    /**
     * Have we resized the board, and thus need to repaint the borders
     * between panels?  Determined in {@link #doLayout()}.
     * @since 1.1.11
     */
    private boolean needRepaintBorders;

    /**
     * The dialog for getting what resources the player wants to discard or gain.
     */
    protected SOCDiscardOrGainResDialog discardOrGainDialog;

    /**
     * the dialog for choosing a player from which to steal
     */
    protected SOCChoosePlayerDialog choosePlayerDialog;

    /**
     * the dialog for choosing a resource to monopolize
     */
    protected SOCMonopolyDialog monopolyDialog;

    private SOCGameStatistics gameStats;
    
    private final ClientBridge clientListener;

    /**
     * Create and show a new player interface.
     * If the game options have a {@link SOCScenario} description, it will be shown now in a popup
     * by {@link #showScenarioInfoDialog()}.
     *
     * @param title  title for this interface - game name
     * @param gd     the player display that spawned us
     * @param ga     the game associated with this interface; must not be {@code null}
     */
    public SOCPlayerInterface(String title, GameAwtDisplay gd, SOCGame ga)
    {
        super(/*I*/TITLEBAR_GAME + title + (ga.isPractice ? "" : " [" + gd.getNickname() + "]")/*18N*/);
        
        setResizable(true);
        layoutNotReadyYet = true;  // will set to false at end of doLayout

        this.gameDisplay = gd;
        client = gd.getClient();
        game = ga;
        game.setScenarioEventListener(this);
        clientListener = new ClientBridge(this);
        gameStats = new SOCGameStatistics(game);
        gameIsStarting = false;
        clientHand = null;
        clientHandPlayerNum = -1;
        is6player = (game.maxPlayers > 4);

        showingPlayerDiscardOrPick = false;
        showingPlayerDiscardOrPick_lock = new Object();

        /**
         * initialize the player colors
         */
        playerColors = new Color[game.maxPlayers];
        playerColorsGhost = new Color[game.maxPlayers];
        playerColors[0] = new Color(109, 124, 231); // grey-blue
        playerColors[1] = new Color(231,  35,  35); // red
        playerColors[2] = new Color(244, 238, 206); // off-white
        playerColors[3] = new Color(249, 128,  29); // orange
        if (is6player)
        {
            playerColors[4] = new Color(97, 151, 113); // same green as playerclient bg ("61AF71")
            playerColors[5] = playerColors[3];  // orange
            playerColors[3] = new Color(166, 88, 201);  // violet
        }
        for (int i = 0; i < game.maxPlayers; ++i)
        {
            playerColorsGhost[i] = makeGhostColor(playerColors[i]);
        }

        /**
         * initialize the font and the foreground, and background colors
         */
        setBackground(Color.black);
        setForeground(Color.black);
        setFont(new Font("SansSerif", Font.PLAIN, 10));

        /**
         * we're doing our own layout management
         */
        setLayout(null);

        /**
         * setup interface elements.
         * PERF: hide window while doing so (osx firefox)
         */
        final boolean didHideTemp = isShowing();
        if (didHideTemp)
        {
            setVisible(false);
        }
        initInterfaceElements(true);

        /**
         * more initialization stuff
         */
        int piHeight = 650;
        if ((is6player || game.hasSeaBoard) && SOCPI_isPlatformWindows)
        {
            setLocation(50, 40);
            piHeight += 25;
        } else {
            setLocation(50, 50);
        }
        if (is6player || game.hasSeaBoard)
            setSize((2*SOCHandPanel.WIDTH_MIN) + 16 + boardPanel.getMinimumSize().width, piHeight);
        else
            setSize(830, piHeight);
        validate();

        if (didHideTemp)
        {
            setVisible(true);
        }
        repaint();

        /**
         * init is almost complete - when window appears and doLayout() is called,
         * it will reset mouse cursor from WAIT_CURSOR to normal (WAIT_CURSOR is
         * set in SOCPlayerClient.startPracticeGame or .guardedActionPerform).
         * Then, if the game has any scenario description, it will be shown once in a popup
         * via showScenarioInfoDialog().
         */
    }
    
    /**
     * Provide access to the client listener in case this class does not directly implement it.
     */
    public PlayerClientListener getClientListener()
    {
        return clientListener;
    }

    /**
     * Setup the interface elements
     *
     * @param firstCall First setup call for this window; do global things
     *   such as windowListeners, not just component-specific things.
     */
    protected void initInterfaceElements(boolean firstCall)
    {
        /**
         * initialize the text input and display and add them to the interface.
         * Moved first so they'll be at top of the z-order, for use with textDisplaysLargerTemp.
         * In 6-player games, these text areas' sizes are "zoomed" larger temporarily when
         * the mouse hovers over them, for better visibility.
         */

        textDisplaysLargerTemp = false;
        textDisplaysLargerTemp_needsLayout = false;
        textDisplaysLargerWhen = 0L;
        textInputHasMouse = false;
        textDisplayHasMouse = false;
        chatDisplayHasMouse = false;
        sbFixNeeded = false;
        sbFixLHasMouse = false;
        sbFixRHasMouse = false;
        sbFixBHasMouse = false;
        if (firstCall && is6player)
            addMouseListener(this);  // react when mouse leaves the Frame

        textDisplay = new SnippingTextArea("", 40, 80, TextArea.SCROLLBARS_VERTICAL_ONLY, 80);
        textDisplay.setFont(new Font("SansSerif", Font.PLAIN, 10));
        textDisplay.setBackground(new Color(255, 230, 162));
        textDisplay.setForeground(Color.black);
        textDisplay.setEditable(false);
        add(textDisplay);
        if (is6player)
            textDisplay.addMouseListener(this);

        chatDisplay = new SnippingTextArea("", 40, 80, TextArea.SCROLLBARS_VERTICAL_ONLY, 100);
        chatDisplay.setFont(new Font("SansSerif", Font.PLAIN, 10));
        chatDisplay.setBackground(new Color(255, 230, 162));
        chatDisplay.setForeground(Color.black);
        chatDisplay.setEditable(false);
        if (is6player)
            chatDisplay.addMouseListener(this);
        add(chatDisplay);

        textInput = new TextField();
        textInput.setFont(new Font("SansSerif", Font.PLAIN, 10));
        textInputListener = new SOCPITextfieldListener(this);
        textInputHasSent = false;
        textInputGreyCountdown = textInputGreyCountFrom;
        textInput.addKeyListener(textInputListener);
        textInput.addTextListener(textInputListener);
        textInput.addFocusListener(textInputListener);

        FontMetrics fm = this.getFontMetrics(textInput.getFont());
        textInput.setSize(SOCBoardPanel.PANELX, fm.getHeight() + 4);
        textInput.setBackground(Color.white);  // new Color(255, 230, 162));
        textInput.setForeground(Color.black);
        textInput.setEditable(false);
        textInputIsInitial = false;  // due to "please wait"
        textInput.setText(/*I*/"Please wait..."/*18N*/);
        add(textInput);
        textInput.addActionListener(this);
        if (is6player)
            textInput.addMouseListener(this);

        /**
         * initialize the player hand displays and add them to the interface
         */
        hands = new SOCHandPanel[game.maxPlayers];

        for (int i = 0; i < hands.length; i++)
        {
            SOCHandPanel hp = new SOCHandPanel(this, game.getPlayer(i));
            hands[i] = hp;
            hp.setSize(180, 120);
            add(hp);
            ColorSquare blank = hp.getBlankStandIn();
            blank.setSize(180, 120);
            add(blank);
        }

        /**
         * initialize the building interface and add it to the main interface
         */
        buildingPanel = new SOCBuildingPanel(this);
        buildingPanel.setSize(200, SOCBuildingPanel.MINHEIGHT);
        add(buildingPanel);

        /**
         * initialize the game board display and add it to the interface
         */
        boardPanel = new SOCBoardPanel(this);
        if (! game.hasSeaBoard)
            boardPanel.setBackground(new Color(112, 45, 10));  // brown
        else
            boardPanel.setBackground(new Color(63, 86, 139));  // sea blue
        boardPanel.setForeground(Color.black);
        Dimension bpMinSz = boardPanel.getMinimumSize();
        boardPanel.setSize(bpMinSz.width, bpMinSz.height);
        boardIsScaled = false;
        add(boardPanel);
        if (game.isGameOptionDefined("PL"))
        {
            updatePlayerLimitDisplay(true, false, -1);
                // Player data may not be received yet;
                // game is created empty, then SITDOWN messages are received from server.
                // gameState is at default 0 (NEW) during JOINGAMEAUTH and SITDOWN.
                // initInterfaceElements is also called at board reset.
                // updatePlayerLimitDisplay will check the current gameState.
        }

        /**
         * In 6-player games, text areas temporarily zoom when the mouse is over them.
         * On windows, the scrollbars aren't considered part of the text areas, so
         * we get a mouseExited when user is trying to scroll the text area.
         * Workaround: Instead of looking for mouseExited, look for mouseEntered on
         * handpanels or boardpanel.
         */
        if (is6player)
        {
            if (SOCPI_isPlatformWindows)
            {
                sbFixNeeded = true;
                hands[0].addMouseListener(this);  // upper-left
                hands[1].addMouseListener(this);  // upper-right
                boardPanel.addMouseListener(this);
                // Note not just on firstCall,
                // because hands[] is initialized above.
            }
        }


        /** If player requests window close, ask if they're sure, leave game if so */
        if (firstCall)
        {
            addWindowListener(new PIWindowAdapter(gameDisplay, this));
        }
    }

    /**
     * Overriden so the peer isn't painted, which clears background. Don't call
     * this directly, use {@link #repaint()} instead.
     * For performance and display-bug avoidance, checks {@link #layoutNotReadyYet} flag.
     */
    @Override
    public void update(Graphics g)
    {
        if (! layoutNotReadyYet)
        {
            paint(g);
        } else {
            g.clearRect(0, 0, getWidth(), getHeight());
        }
    }

    /**
     * Paint each component, after (if {@link #needRepaintBorders}) clearing stray pixels
     * from the borders between the components.
     * @since 1.1.11
     */
    @Override
    public void paint(Graphics g)
    {
        if (needRepaintBorders)
            paintBorders(g);
        super.paint(g);
    }

    /**
     * Paint the borders after a resize, and set {@link #needRepaintBorders} false.
     * {@link #prevSize} should be set before calling.
     * @param g  Graphics as passed to <tt>update()</tt>
     * @since 1.1.11
     */
    private void paintBorders(Graphics g)
    {
        if (prevSize == null)
            return;
        if (is6player)
        {
            paintBordersHandColumn(g, hands[5]);
            paintBordersHandColumn(g, hands[2]);
        } else {
            paintBordersHandColumn(g, hands[0]);
            paintBordersHandColumn(g, hands[1]);
        }
        g.clearRect(boardPanel.getX(), boardPanel.getY() - 4, boardPanel.getWidth(), 4);
        needRepaintBorders = false;
    }

    /**
     * Paint the borders of one column of handpanels.
     * {@link #prevSize} must be set before calling.
     * @param g  Graphics as passed to <tt>update()</tt>
     * @param middlePanel  The middle handpanel (6-player) or the bottom (4-player) in this column
     * @since 1.1.11
     */
    private final void paintBordersHandColumn(Graphics g, SOCHandPanel middlePanel)
    {
        if (middlePanel == null)
            return;  // if called during board reset

        final int w = middlePanel.getWidth();

        // left side, entire height
        int x = middlePanel.getX();
        g.clearRect(x - 4, 0, 4, prevSize.height);

        // right side, entire height
        x += w;
        g.clearRect(x, 0, 4, prevSize.height);

        // above middle panel
        x = middlePanel.getX();
        int y = middlePanel.getY();
        g.clearRect(x, y - 4, w, 4);

        // below middle panel
        y += middlePanel.getHeight();
        g.clearRect(x, y, w, 4);
    }

    /**
     * @return the client that spawned us
     */
    public SOCPlayerClient getClient()
    {
        return gameDisplay.getClient();
    }

    /**
     * @return the game display associated with this interface
     * @since 2.0.00
     */
    public GameAwtDisplay getGameDisplay()
    {
        return gameDisplay;
    }

    /**
     * @return the game associated with this interface
     */
    public SOCGame getGame()
    {
        return game;
    }

    public SOCGameStatistics getGameStats()
    {
        return gameStats;
    }

    /**
     * @return the color of a player
     * @param pn  the player number
     */
    public Color getPlayerColor(int pn)
    {
        return getPlayerColor(pn, false);
    }

    /**
     * @return the normal or "ghosted" color of a player
     * @param pn  the player number
     * @param isGhost Do we want the "ghosted" color, not the normal color?
     */
    public Color getPlayerColor(int pn, boolean isGhost)
    {
        if (isGhost)
            return playerColorsGhost[pn];
        else
            return playerColors[pn];
    }
    
    /**
     * @return a player's hand panel, or null if <tt>pn</tt> &lt; 0
     *
     * @param pn  the player's seat number
     * 
     * @see #getClientHand()
     */
    public SOCHandPanel getPlayerHandPanel(final int pn)
    {
        if (pn < 0)
            return null;

        return hands[pn];
    }

    /**
     * @return the board panel
     */
    public SOCBoardPanel getBoardPanel()
    {
        return boardPanel;
    }

    /**
     * @return the timer for time-driven events in the interface
     *
     * @see SOCHandPanel#autoRollSetupTimer()
     * @see SOCBoardPanel#popupSetBuildRequest(int, int)
     */
    public Timer getEventTimer()
    {
        return gameDisplay.getEventTimer();
    }

    /**
     * The game's count of development cards remaining has changed.
     * Update the display.
     */
    public void updateDevCardCount()
    {
       buildingPanel.updateDevCardCount();
    }

    /**
     * The game's longest road or largest army may have changed.
     * Update each player's handpanel (victory points and longest-road/
     * largest-army indicator).  If it changed, print an announcement in
     * the message window.
     *<P>
     * Call this only after updating the SOCGame objects.
     *
     * @param isRoadNotArmy Longest-road, not largest-army, has just changed
     * @param oldp  Previous player with longest/largest, or null if none
     * @param newp  New player with longest/largest, or null if none
     */
    public void updateLongestLargest
        (boolean isRoadNotArmy, SOCPlayer oldp, SOCPlayer newp)
    {
        // Update handpanels
        final PlayerClientListener.UpdateType updateType;
        if (isRoadNotArmy)
            updateType = PlayerClientListener.UpdateType.LongestRoad;
        else
            updateType = PlayerClientListener.UpdateType.LargestArmy;

        for (int i = 0; i < game.maxPlayers; i++)
        {
            hands[i].updateValue(updateType);
            hands[i].updateValue(PlayerClientListener.UpdateType.VictoryPoints);
        }

        // Check for and announce change in largest army, or longest road
        if ((newp != oldp)
            && ((null != oldp) || (null != newp)))
        {
            final String changedObj;  // what was changed?
            if (isRoadNotArmy)
            {
                if (game.hasSeaBoard)
                    changedObj = /*I*/"Longest trade route"/*18N*/;
                else
                    changedObj = /*I*/"Longest road"/*18N*/;
            } else {
                changedObj = /*I*/"Largest army"/*18N*/;
            }

            final String msg;  // full sentence with change and players
            if (newp != null)
            {
                if (oldp != null)
                {
                    msg = MessageFormat.format
                        (/*I*/"* {0} was taken from {1} by {2}."/*18N*/, changedObj, oldp.getName(), newp.getName());
                } else {
                    msg = MessageFormat.format
                        (/*I*/"* {0} was taken by {1}."/*18N*/, changedObj, newp.getName());
                }
            } else {
                msg = MessageFormat.format
                    (/*I*/"* {0} was lost by {1}."/*18N*/, changedObj, oldp.getName());
            }

            print(msg);
        }
    }

    /**
     * Show the maximum and available number of player positions,
     * if game parameter "PL" is less than {@link SOCGame#maxPlayers}.
     * Also, if show, and {@code isGameStart}, check for game-is-full,
     * and hide or show "sit down" buttons if necessary.
     * If the game has already started, and the client is playing in this game,
     * will not show this display (it overlays the board, which is in use).
     * It will still hide/show sit-here buttons if needed.
     * @param show show the text, or clear the display (at game start)?
     * @param isGameStart  True if calling from {@link #startGame()}; will be set true if gameState is {@link SOCGame#NEW}
     * @param playerLeaving The player number if a player is leaving the game, otherwise -1.
     * @since 1.1.07
     */
    private void updatePlayerLimitDisplay(final boolean show, boolean isGameStart, final int playerLeaving)
    {
        final int gstate = game.getGameState();
        final boolean clientSatAlready = (clientHand != null);
        boolean noTextOverlay =  ((! show) ||
            ((gstate >= SOCGame.START1A) && clientSatAlready));
        if (gstate == SOCGame.NEW)
            isGameStart = true;

        if (noTextOverlay)
        {
            boardPanel.setSuperimposedText(null, null);
        }
        final int maxPl = game.getGameOptionIntValue("PL");
        if (maxPl == game.maxPlayers)
            noTextOverlay = true;
        int availPl = game.getAvailableSeatCount();
        if (playerLeaving != -1)
            ++availPl;  // Not yet vacant in game data
        if (! noTextOverlay)
        {
            String availTxt = (availPl == 1) ? /*I*/"1 seat available"/*18N*/ : /*I*/Integer.toString(availPl) + " seats available"/*18N*/;
            boardPanel.setSuperimposedText
                (/*I*/"Maximum players: " + maxPl/*18N*/, availTxt);
        }

        if (isGameStart || ! clientSatAlready)
        {
            if (availPl == 0)
            {
                // No seats remain; remove all "sit here" buttons.
                // (If client has already sat, will leave them
                //  visible as robot "lock" buttons.)
                for (int i = 0; i < game.maxPlayers; i++)
                    hands[i].removeSitBut();
            }
            else if (playerLeaving != -1)
            {
                // Now there's a vacant seat again, re-add button,
                // either as "sit here" or "lock" as appropriate.
                // If availPl==1, there was previously 0 available,
                // so we must re-add at each vacant position.
                // The leaving player isn't vacant yet in the game data.
                hands[playerLeaving].addSitButton(clientSatAlready);
                if (availPl == 1)
                    for (int i = 0; i < game.maxPlayers; i++)
                        if (game.isSeatVacant(i))
                            hands[i].addSitButton(clientSatAlready);
            }
        }
    }

    /**
     * If the player's trade-offer panel is showing a message
     * (not a trade offer), clear and hide it.
     * @param pn  Player number, or -1 for all players
     * @see SOCHandPanel#clearTradeMsg()
     * @since 1.1.12
     */
    void clearTradeMsg(final int pn)
    {
        if (pn != -1)
            hands[pn].clearTradeMsg();
        else
            for (int i = 0; i < game.maxPlayers; ++i)
                hands[i].clearTradeMsg();
    }

    /**
     * Switch the game's {@link SOCGame#debugFreePlacement Debug Paint Piece Mode}
     * on or off, as directed by the server.
     * @param setOn  Should the mode be turned on?
     * @see #setDebugFreePlacementPlayer(int)
     * @since 1.1.12
     */
    void setDebugFreePlacementMode(final boolean setOn)
    {
        try
        {
            game.setDebugFreePlacement(setOn);
            if (! setOn)
                boardPanel.setPlayer(null);
            boardPanel.updateMode();  // will set or clear top text, which triggers a repaint
            buildingPanel.updateButtonStatus();
        } catch (IllegalStateException e) {
            textDisplay.append
              ("*** Can't setDebugFreePlacement(" + setOn+ ") for " + game.getName() + " in state " + game.getGameState() + "\n");
        }
    }

    /**
     * Set the board's 'client player' for the Debug Paint Piece Mode.
     * Returns to the true client player when
     * {@link #setDebugFreePlacementMode(boolean) setDebugPaintPieceMode(false)}
     * is called.
     * @param pn Player number
     * @since 1.1.12
     */
    void setDebugFreePlacementPlayer(final int pn)
    {
        if (! game.isDebugFreePlacement())
            return;

        int prevPn = boardPanel.getPlayerNumber();
        if (pn == prevPn)
            return;

        boardPanel.setPlayer(game.getPlayer(pn));

        // update "current" player hilight
        getPlayerHandPanel(prevPn).updateAtTurn();
        getPlayerHandPanel(pn).updateAtTurn();
    }


    /**
     * @return the building panel
     */
    public SOCBuildingPanel getBuildingPanel()
    {
        return buildingPanel;
    }
    
    /** The client player's SOCHandPanel interface, if active in a game.
     * 
     * @return our player's hand interface, or null if not in a game.
     * @see #clientIsCurrentPlayer()
     */
    public SOCHandPanel getClientHand()
    {
        return clientHand;
    }
    
    /** Update the client player's SOCHandPanel interface, for joining
     *  or leaving a game.
     * 
     *  Set by SOCHandPanel's removePlayer() and addPlayer() methods.
     * 
     * @param h  The SOCHandPanel for us, or null if none (leaving).
     */
    public void setClientHand(SOCHandPanel h)
    {
        clientHand = h;
        if (h != null)
            clientHandPlayerNum = h.getPlayer().getPlayerNumber();
        else
            clientHandPlayerNum = -1;
    }
    
    /**
     * Is the client player active in this game, and the current player?
     * Assertion: If this returns true, {@link #getClientHand()} will return non-null.
     * @see #getClientPlayerNumber()
     */
    public boolean clientIsCurrentPlayer()
    {
        if (clientHand == null)
            return false;
        else
            return clientHand.isClientAndCurrentPlayer();
    }

    /**
     * If client player is active in game, their player number.
     * 
     * @return client's player ID, or -1.
     * @see #clientIsCurrentPlayer()
     * @see #getClientHand()
     */
    public int getClientPlayerNumber()
    {
        return clientHandPlayerNum;
    }

    /**
     * Server has just sent a dice result message.
     * Call this after updating game state with the roll result.
     * Show the dice result in the game text panel and on the board.
     *
     * @param cp   Current player who rolled
     * @param roll The roll result, or 0
     * @since 2.0.00
     */
    public void showDiceResult(final SOCPlayer cp, final int roll)
    {
        if (roll > 0)
            printFormat(/*I*/"* Rolled a {0}."/*18N*/, Integer.toString(roll));

        boardPanel.repaint();

        // only update stats for valid rolls
        if ((roll >= 2) && (roll <= 12) && (cp != null))
        {
            gameStats.diceRolled(new SOCGameStatistics.DiceRollEvent(roll, cp));
        }
    }

    /**
     * send the message that was just typed in
     */
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == textInput)
        {
            if (textInputIsInitial)
            {
                // Player hit enter while chat prompt is showing (TEXTINPUT_INITIAL_PROMPT_MSG).
                // Just clear the prompt so they can type what they want to say.
                textInputSetToInitialPrompt(false);
                textInput.setText(" ");  // Not completely empty, so TextListener won't re-set prompt.
                return;
            }

            String s = textInput.getText().trim();
            String sOverflow = null;

            if (s.length() > 100)
            {
                // wrap long line at a word if possible
                int lastSpace = s.lastIndexOf(' ', 100);
                if (lastSpace == -1)
                    lastSpace = 100;
                sOverflow = s.substring(lastSpace).trim();
                s = s.substring(0, lastSpace).trim();
            }
            else if (s.length() == 0)
            {
                return;
            }

            // Remove listeners for lower overhead on future typing
            if (! textInputHasSent)
            {
                textInputHasSent = true;
                if (textInputListener != null)
                {
                    textInput.removeKeyListener(textInputListener);
                    textInput.removeTextListener(textInputListener);
                    textInputListener = null;
                }
            }

            // Clear and send to game at server
            textInput.setText("");
            if (s.startsWith("=*="))
            {
                String sLower = s.toLowerCase();
                boolean doSet;
                int i = sLower.indexOf("show:");
                if (i > 0)
                {
                    doSet = true;
                } else {
                    i = sLower.indexOf("hide:");
                    doSet = false;
                }
                if (i > 0)
                {
                    s = sLower.substring(i+5).trim();
                    int flagnum;
                    if (s.equalsIgnoreCase("all"))
                    {
                        flagnum = -1;
                    } else{
                        try
                        {
                            flagnum = Integer.parseInt(s);
                        } catch (NumberFormatException e2) {
                            chatPrintDebug
                                ("Usage: =*= show: n  or =*= hide: n   where n is all or a number 0-9"); //i18n?
                            return;
                        }
                    }
                    boardPanel.setDebugShowPotentialsFlag(flagnum, false, doSet);
                    return;
                }
            }

            final String msg = s + '\n';
            if (! doLocalCommand(msg))
                client.getGameManager().sendText(game, msg);

            if (sOverflow != null)
            {
                textInput.setText(sOverflow);  // user can choose to re-send the rest
                textInput.setSelectionStart(0);  // clear highlight, so typing won't erase overflow
                textInput.setSelectionEnd(0);
                textInput.setCaretPosition(sOverflow.length());
            }
        }
    }
    
    /**
     * Handle local client commands for games.
     *<P>
     * Before 2.0.00, this was <tt>SOCPlayerClient.doLocalCommand(SOCGame, String)</tt>.
     *
     * @param cmd  Local client command string, which starts with \
     * @return true if a command was handled
     * @since 2.0.00
     */
    private boolean doLocalCommand(String cmd)
    {
        if (cmd.charAt(0) != '\\')
            return false;

        if (cmd.startsWith("\\ignore "))
        {
            String name = cmd.substring(8);
            client.addToIgnoreList(name);
            print("* Ignoring " + name);
            gameDisplay.printIgnoreList(this);

            return true;
        }
        else if (cmd.startsWith("\\unignore "))
        {
            String name = cmd.substring(10);
            client.removeFromIgnoreList(name);
            print("* Unignoring " + name);
            gameDisplay.printIgnoreList(this);

            return true;
        }
        else if (cmd.startsWith("\\clm-set "))
        {
            String name = cmd.substring(9).trim();
            getBoardPanel().setOtherPlayer(game.getPlayer(name));
            getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LM_SETTLEMENT);

            return true;
        }
        else if (cmd.startsWith("\\clm-road "))
        {
            String name = cmd.substring(10).trim();
            getBoardPanel().setOtherPlayer(game.getPlayer(name));
            getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LM_ROAD);

            return true;
        }
        else if (cmd.startsWith("\\clm-city "))
        {
            String name = cmd.substring(10).trim();
            getBoardPanel().setOtherPlayer(game.getPlayer(name));
            getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LM_CITY);

            return true;
        }
        else if (cmd.startsWith("\\clt-set "))
        {
            String name = cmd.substring(9).trim();
            getBoardPanel().setOtherPlayer(game.getPlayer(name));
            getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LT_SETTLEMENT);

            return true;
        }
        else if (cmd.startsWith("\\clt-road "))
        {
            String name = cmd.substring(10).trim();
            getBoardPanel().setOtherPlayer(game.getPlayer(name));
            getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LT_ROAD);

            return true;
        }
        else if (cmd.startsWith("\\clt-city "))
        {
            String name = cmd.substring(10).trim();
            getBoardPanel().setOtherPlayer(game.getPlayer(name));
            getBoardPanel().setMode(SOCBoardPanel.CONSIDER_LT_CITY);

            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * leave this game
     */
    public void leaveGame()
    {
        gameDisplay.leaveGame(game);
        client.getGameManager().leaveGame(game);
        dispose();
    }

    /**
     * Player wants to request to reset the board (same players, new game, new layout).
     * If acceptable, send request to server. If not, say so in text area.
     * Not acceptable if they've already done so this turn, or if voting
     * is active because another player called for a vote.
     *<P>
     * Board reset was added in version 1.1.00.  Older servers won't support it.
     * If this happens, give user a message.
     *<P>
     * Before resetting a practice game, have the user confirm the reset with a dialog box.
     * This is to prevent surprises if they click the "Restart" button at the end of a practice game,
     * which is the same button as the "Done" button at the end of a turn.
     *
     * @param confirmDialogFirst  If true, a practice game is over and the user should confirm the reset
     *            with a dialog box created and shown here.  If the game is just starting, no need to confirm.
     *            If true, assumes <tt>resetBoardRequest</tt> is being called from the AWT event thread.
     */
    public void resetBoardRequest(final boolean confirmDialogFirst)
    {
        if (confirmDialogFirst)
        {
            new ResetBoardConfirmDialog(gameDisplay, this).run();
            return;
            // ResetBoardConfirmDialog will call resetBoardRequest(false) if its Restart button is clicked
        }

        if (client.getServerVersion(game) < 1100)
        {
            textDisplay.append("*** "+/*I*/"This server does not support board reset, server is too old."/*18N*/+"\n");
            return;
        }
        if (game.getResetVoteActive())
        {
            textDisplay.append("*** "+/*I*/"Voting is already active. Try again when voting completes."/*18N*/+"\n");
            return;
        }
        SOCPlayer pl = game.getPlayer(clientHandPlayerNum);
        if (! pl.hasAskedBoardReset())
            client.getGameManager().resetBoardRequest(game);
        else
            textDisplay.append("*** "+/*I*/"You may ask only once per turn to reset the board."/*18N*/+"\n");
    }

    /**
     * Another player has voted on a board reset request.
     * Show the vote.
     */
    public void resetBoardVoted(int pn, boolean vyes)
    {
        String voteMsg;
        if (vyes)
            voteMsg = /*I*/"Go ahead."/*18N*/;
        else
            voteMsg = /*I*/"No thanks."/*18N*/;
        textDisplay.append("* " + /*I*/game.getPlayer(pn).getName() + " has voted: " + voteMsg/*18N*/+"\n");
        game.resetVoteRegister(pn, vyes);
        try { hands[pn].resetBoardSetMessage(voteMsg); }
        catch (IllegalStateException e) { /* ignore; discard message is showing */ }
    }

    /**
     * Voting complete, board reset was rejected.
     * Display text message and clear the offer.
     */
    public void resetBoardRejected()
    {
        textDisplay.append("** "+/*I*/"The board reset was rejected."/*18N*/+"\n");
        for (int i = 0; i < hands.length; ++i)
        {
            // Clear all displayed votes
            try { hands[i].resetBoardSetMessage(null); }
            catch (IllegalStateException e) { /* ignore; discard message is showing */ }
        }
        boardResetRequester = null;
        if (boardResetVoteDia != null)
        {
            if (boardResetVoteDia.isShowing())
                boardResetVoteDia.disposeQuietly();
            boardResetVoteDia = null;
        }
        // Requester may have already been null, if we're the requester and it was rejected.
    }

    /**
     * Creates and shows a new ResetBoardVoteDialog.
     * If the game is over, the "Reset" button is the default;
     * otherwise, "No" is default.
     * Also announces the vote request (text) and sets boardResetRequester.
     * Dialog is shown in a separate thread, to continue message
     * treating and screen redraws as the other players vote.
     *<P>
     * If we are the requester, we update local game state
     * but don't vote.
     *
     * @param pnRequester Player number of the player requesting the board reset
     */
    public void resetBoardAskVote(int pnRequester)
    {
        boolean gaOver = (game.getGameState() >= SOCGame.OVER);
        try
        {
            game.resetVoteBegin(pnRequester);
        }
        catch (RuntimeException re)
        {
            D.ebugPrintln("resetBoardAskVote: Cannot: " + re);
            return;
        }
        boardResetRequester = hands[pnRequester];
        if (pnRequester != clientHandPlayerNum)
        {
            String pleaseMsg;
            if (gaOver)
                pleaseMsg = /*I*/"Restart Game?"/*18N*/;
            else
                pleaseMsg = /*I*/"Reset Board?"/*18N*/;
            boardResetRequester.resetBoardSetMessage(pleaseMsg);

            String requester = game.getPlayer(pnRequester).getName();
            boardResetVoteDia = new ResetBoardVoteDialog(gameDisplay, this, requester, gaOver);
            boardResetVoteDia.showInNewThread();
               // Separate thread so ours is not tied up; this allows server
               // messages to be received, and screen to refresh, if other
               // players vote before we do, or if voting is cancelled.
        }
    }

    /** Callback from ResetBoardVoteDialog, to clear our reference when
     *  button is clicked and dialog is going away.
     */
    private void resetBoardClearDia()
    {
        boardResetVoteDia = null;
    }

    /**
     * Print formatted text (with placeholders) in the text window, followed by a new line (<tt>'\n'</tt>). Equivalent to
     * {@link #print(String) print}({@link MessageFormat#format(String, Object...) MessageFormat.format}({@code s, args})).
     *
     * @param s  String with placeholders, such as "{0} wants to Special Build.". Single quotes must be doubled.
     * @param args  Arguments to fill into {@code s}'s placeholders
     * @since 2.0.00
     */
    public void printFormat(final String s, final Object ... args)
    {
        print(MessageFormat.format(s, args));
    }

    /**
     * print text in the text window, followed by a new line (<tt>'\n'</tt>).
     *
     * @param s  the text; you don't need to include "\n".
     * @see #chatPrint(String)
     * @see #printFormat(String, Object...)
     */
    public void print(String s)
    {
        StringTokenizer st = new StringTokenizer(s, "\n", false);
        while (st.hasMoreElements())
        {
            String tk = st.nextToken().trim();
            textDisplay.append(tk + "\n");  // TextArea will soft-wrap within the line
        }
    }

    /**
     * print text in the chat window
     *
     * @param s  the text
     * @see #print(String)
     */
    public void chatPrint(String s)
    {
        StringTokenizer st = new StringTokenizer(s, "\n", false);
        while (st.hasMoreElements())
        {
            String tk = st.nextToken().trim();
            chatDisplay.append(tk + "\n");  // TextArea will soft-wrap within the line
        }
    }

    /**
     * an error occurred, stop playing
     *
     * @param s  an error message
     */
    public void over(String s)
    {
        if (textInputIsInitial)
            textInputSetToInitialPrompt(false);  // Clear, set foreground color
        textInput.setEditable(false);
        textInput.setText(s);
        textDisplay.append("* "+/*I*/"Sorry, lost connection to the server."/*18N*/+"\n");
        textDisplay.append("*** "+/*I*/"Game stopped."/*18N*/+" ***\n");
        game.setCurrentPlayerNumber(-1);
        boardPanel.repaint();
    }

    /**
     * start the game interface: set chat input (textInput) to initial prompt.
     * This doesn't mean that game play or placement is starting,
     * only that the window is ready for players to choose where to sit.
     * By now HandPanel has added "sit" buttons, or updatePlayerLimitDisplay
     * has removed them if necessary.
     *<P>
     * If this game has observers, list them in the textDisplay now.
     *
     * @param members Game member names from {@link soc.message.SOCGameMembers#getMembers()} (added in 1.1.12)
     */
    public void began(Vector<String> members)
    {
        textInput.setEditable(true);
        textInput.setText("");
        textInputSetToInitialPrompt(true);
        // Don't request focus for textInput; it should clear
        // the prompt text when user clicks (focuses) it, so
        // wait for user to do that.

        // Look for game observers, list in textDisplay
        if (members == null)
            return;
        int numObservers = 0;
        StringBuffer obs = null;
        for (int i = members.size() - 1; i >= 0; --i)
        {
            String mname = members.elementAt(i);
            if (null != game.getPlayer(mname))
                continue;
            if (mname.equals(client.getNickname()))
                continue;
            if (numObservers == 0)
                obs = new StringBuffer();
            else
                obs.append(", ");
            obs.append(mname);
            ++numObservers;
        }
        if (numObservers > 0)
        {
            textDisplay.append("* "+strings.get((numObservers == 1 ? 
                    "interface.observer.enter.one" : "interface.observer.enter.many"), obs.toString())+"\n");
        }
    }

    /**
     * a player has sat down to play
     *
     * @param n   the name of the player
     * @param pn  the seat number of the player
     */
    public void addPlayer(String n, int pn)
    {
        hands[pn].addPlayer(n);  // This will also update all other hands' buttons ("sit here" -> "lock", etc)

        if (n.equals(client.getNickname()))
        {
            for (int i = 0; i < game.maxPlayers; i++)
            {
                if (game.getPlayer(i).isRobot())
                {
                    hands[i].addSittingRobotLockBut();
                }
            }
            if (is6player)
            {
                // handpanel sizes change when client sits
                // in a 6-player game.
                invalidate();
                doLayout();
            }
        }

        if (game.isGameOptionDefined("PL"))
            updatePlayerLimitDisplay(true, false, -1);

        if (game.isBoardReset())
        {
            // Retain face after reset
            hands[pn].changeFace(hands[pn].getPlayer().getFaceId());
        }
    }

    /**
     * remove a player from the game.
     * To prevent inconsistencies, call this <em>before</em> calling
     * {@link SOCGame#removePlayer(String)}.
     *
     * @param pn the number of the player
     */
    public void removePlayer(int pn)
    {
        hands[pn].removePlayer();  // May also clear clientHand
        if (game.isGameOptionDefined("PL"))
            updatePlayerLimitDisplay(true, false, pn);
        else
            hands[pn].addSitButton(clientHand != null);  // Is the client player already sitting down at this game?

        if (is6player && (clientHand == null))
        {
            // handpanel sizes change when client leaves in a 6-player game.
            invalidate();
            doLayout();
        }
    }

    /**
     * Game play is starting (leaving state {@link SOCGame#NEW}).
     * Remove the start buttons and robot-lockout buttons.
     * Next move is for players to make their starting placements.
     *<P>
     * Call {@link SOCGame#setGameState(int)} before calling this method.
     * Call this method before calling {@link #updateAtGameState()}.
     */
    public void startGame()
    {
        for (int i = 0; i < hands.length; i++)
        {
            hands[i].removeStartBut();
            // This button has two functions (and two labels).
            // If client joined and then started a game, remove it (as robot lockout).
            // If we're joining a game in progress, keep it (as "sit here").
            hands[i].removeSitLockoutBut();
        }
        updatePlayerLimitDisplay(false, true, -1);
        gameIsStarting = true;
    }

    /**
     * Game is over; server has sent us the revealed scores
     * for each player.  Refresh the display.
     *
     * @param finalScores Final score for each player position
     */
    public void updateAtOver(int[] finalScores)
    {
        if (game.getGameState() != SOCGame.OVER)
        {
            System.err.println("L1264: pi.updateAtOver called at state " + game.getGameState());
            return;
        }

        for (int i = 0; i < finalScores.length; ++i)
            game.getPlayer(i).forceFinalVP(finalScores[i]);
        if (null == game.getPlayerWithWin())
        {
            game.checkForWinner();  // Assumes "current player" set to winner already, by SETTURN msg
        }
        for (int i = 0; i < finalScores.length; ++i)
            hands[i].updateValue(PlayerClientListener.UpdateType.VictoryPoints);  // Also disables buttons, etc.
        setTitle(/*I*/TITLEBAR_GAME_OVER + game.getName() +
                 (game.isPractice ? "" : " [" + client.getNickname() + "]")/*18N*/);
        boardPanel.updateMode();
        repaint();
    }

    /**
     * Game's current player has changed.  Update displays.
     *
     * @param pnum New current player number; should match game.getCurrentPlayerNumber()
     */
    public void updateAtTurn(final int pnum)
    {
        if ((pnum >= 0) && (pnum < hands.length))
            getPlayerHandPanel(pnum).updateDevCards();

        for (int i = 0; i < hands.length; i++)
        {
            // hilight current player, update takeover button
            getPlayerHandPanel(i).updateAtTurn();
        }

        boardPanel.updateMode();
        boardPanel.repaint();
        if (textInputGreyCountdown > 0)
        {
            --textInputGreyCountdown;
            if ((textInputGreyCountdown == 0) && textInputIsInitial)
            {
                textInput.setForeground(Color.LIGHT_GRAY);
            }
        }

        buildingPanel.updateButtonStatus();
    }

    /**
     * A player is being asked to roll (or play a card) at the start of their turn.
     * Update displays if needed.
     *<P>
     * If the client is the current player, calls {@link SOCHandPanel#autoRollOrPromptPlayer()}.
     * @since 1.1.11
     */
    public void updateAtRollPrompt()
    {
        if (clientIsCurrentPlayer())
            getClientHand().autoRollOrPromptPlayer();
    }

    /**
     * A player has been awarded Special Victory Points (SVP), so announce those details.
     * Example: "Lily gets 2 Special Victory Points for settling a new island."
     * Only prints text, does not update SOCHandPanel's SVP or call {@link SOCPlayer#addSpecialVPInfo(int, String)}.
     * @param plName  Player name
     * @param svp  Number of SVP awarded
     * @param desc  Description of player's action that led to SVP; example: "settling a new island"
     * @since 2.0.00
     */
    public void updateAtSVPText(final String plName, final int svp, final String desc)
    {
        final String svpKey = (svp == 1)
            ? "game.SVP.get.one"
            : "game.SVP.get.many";

        textDisplay.append("* "+strings.get(svpKey, plName, svp, desc)+"\n");
    }

    /**
     * Set or clear the chat text input's initial prompt.
     * Sets its status, foreground color, and the prompt text if true.
     *
     * @param setToInitial If false, clear initial-prompt status, and
     *    clear contents (if they are the initial-prompt message);
     *    If true, set initial-prompt status, and set the prompt
     *    (if contents are blank when trimmed).
     *
     * @throws IllegalStateException if setInitial true, but player
     *    already sent chat text (textInputHasSent).
     *
     * @see #TEXTINPUT_INITIAL_PROMPT_MSG
     */
    protected void textInputSetToInitialPrompt(boolean setToInitial)
        throws IllegalStateException
    {
        if (setToInitial && textInputHasSent)
            throw new IllegalStateException("Already sent text, can't re-initial");

        // Always change text before changing flag,
        // so TextListener doesn't fight this action.

        if (setToInitial)
        {
            if (textInput.getText().trim().length() == 0)
                textInput.setText(TEXTINPUT_INITIAL_PROMPT_MSG);  // Set text before flag
            textInputIsInitial = true;
            textInputGreyCountdown = textInputGreyCountFrom;  // Reset fade countdown
            textInput.setForeground(Color.DARK_GRAY);
        } else {
            if (textInput.getText().equals(TEXTINPUT_INITIAL_PROMPT_MSG))
                textInput.setText("");  // Clear to make room for text being typed
            textInputIsInitial = false;
            textInput.setForeground(Color.BLACK);
        }
    }

    /**
     * Show the discard dialog or the gain-resources dialog.
     * Used for discards, gold hexes, and the Discovery/Year of Plenty dev card.
     *
     * @param nd  the number of resources to discard or gain
     * @param isDiscard  True for discard (after 7), false for gain (after gold hex)
     */
    public void showDiscardOrGainDialog(final int nd, final boolean isDiscard)
    {
        discardOrGainDialog = new SOCDiscardOrGainResDialog(this, nd, isDiscard);
        discardOrGainDialog.setVisible(true);
    }

    /**
     * show the {@link SOCChoosePlayerDialog} to choose a player for robbery.
     *<P>
     * Before v2.0.00, this was <tt>choosePlayer</tt>.
     *
     * @param count   the number of players to choose from
     * @param pnums   the player ids of those players; length of this
     *                array may be larger than count (may be {@link SOCGame#maxPlayers}).
     *                Only the first <tt>count</tt> elements will be used.
     *                If <tt>allowChooseNone</tt>, pnums.length must be at least <tt>count + 1</tt>
     *                to leave room for "no player".
     * @param allowChooseNone  if true, player can choose to rob no one (game scenario <tt>SC_PIRI</tt>)
     * @see SOCPlayerClient.GameManager#choosePlayer(SOCGame, int)
     * @see #showChooseRobClothOrResourceDialog(int)
     */
    public void showChoosePlayerDialog(final int count, final int[] pnums, final boolean allowChooseNone)
    {
        choosePlayerDialog = new SOCChoosePlayerDialog(this, count, pnums, allowChooseNone);
        choosePlayerDialog.setVisible(true);
    }

    /**
     * Show the {@link ChooseRobClothOrResourceDialog} to choose what to rob from a player.
     * @param vpn  Victim player number
     * @since 2.0.00
     * @see #showChoosePlayerDialog(int, int[])
     */
    public void showChooseRobClothOrResourceDialog(final int vpn)
    {
        new ChooseRobClothOrResourceDialog(vpn).showInNewThread();
    }

    /**
     * show the Monopoly dialog box
     */
    public void showMonopolyDialog()
    {
        monopolyDialog = new SOCMonopolyDialog(this);
        monopolyDialog.setVisible(true);
    }

    /**
     * If this game has a scenario (game option {@code "SC"}), show the scenario
     * description, special rules, and number of victory points to win.
     * Shown automatically when the SOCPlayerInterface is first shown.
     * @since 2.0.00
     */
    public void showScenarioInfoDialog()
    {
        NewGameOptionsFrame.showScenarioInfoDialog
            (game.getGameOptionStringValue("SC"), game.getGameOptions(), game.vp_winner, getGameDisplay(), this);
    }

    /**
     * Update interface after the server sends us a new board layout.
     * Call after setting game data and board data.
     * Calls {@link SOCBoardPanel#flushBoardLayoutAndRepaint()}.
     * Updates display of board-related counters, such as {@link SOCBoardLarge#getCloth()}.
     * Not needed if calling {@link #resetBoard(SOCGame, int, int)}.
     * @since 2.0.00
     */
    public void updateAtNewBoard()
    {
        boardPanel.flushBoardLayoutAndRepaint();
        if (game.isGameOptionSet(SOCGameOption.K_SC_CLVI))
            buildingPanel.updateClothCount();
    }

    /**
     * Update interface after game state has changed.
     * For example, if the client is current player, and state changed from PLAY to PLAY1,
     * (Dice has been rolled, or card played), enable the player's Done and Bank buttons.
     * Or, if the player must discard resources or pick free resources from the gold hex,
     * calls {@link #discardOrPickTimerSet(boolean)}.
     *<P>
     * Please call {@link SOCGame#setGameState(int)} first. <BR>
     * If the game is now starting, please call in this order:
     *<code><pre>
     *   game.setGameState(newState);
     *   playerInterface.{@link #startGame()};
     *   playerInterface.updateAtGameState();
     *</pre></code>
     */
    public void updateAtGameState()
    {
        int gs = game.getGameState();

        getBoardPanel().updateMode();
        getBuildingPanel().updateButtonStatus();
        getBoardPanel().repaint();

        // Check for placement states (board panel popup, build via right-click)
        if ((gs == SOCGame.PLACING_ROAD) || (gs == SOCGame.PLACING_SETTLEMENT)
            || (gs == SOCGame.PLACING_CITY) || (gs == SOCGame.PLACING_SHIP))
        {
            if (getBoardPanel().popupExpectingBuildRequest())
                getBoardPanel().popupFireBuildingRequest();
        }

        // Update our interface at start of first turn;
        // The server won't send a TURN message after the
        // final road is placed (state START2 -> PLAY).
        if (gameIsStarting && (gs >= SOCGame.PLAY))
        {
            gameIsStarting = false;
            if (clientHand != null)
                clientHand.updateAtTurn();
            boardPanel.updateMode();
        }

        // React if waiting for players to discard,
        // or if we were waiting, and aren't anymore.
        if (gs == SOCGame.WAITING_FOR_DISCARDS)
        {
            // Set timer.  If still waiting for discards after 2 seconds,
            // show balloons on-screen. (hands[i].setDiscardOrPickMsg)
            discardOrPickTimerSet(true);
        } else if ((gs == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                   || (gs == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE))
        {
            // Set timer.  If still waiting for resource picks after 2 seconds,
            // show balloons on-screen. (hands[i].setDiscardOrPickMsg)
            discardOrPickTimerSet(false);
        } else if (showingPlayerDiscardOrPick &&
                   ((gs == SOCGame.PLAY1) || (gs == SOCGame.START2B) || (gs == SOCGame.START3B)))
        {
            // If not all players' discard status balloons were cleared by
            // PLAYERELEMENT messages, clean up now.
            discardOrPickTimerClear();
        }

        // React if we are current player
        if ((clientHand == null) || (clientHandPlayerNum != game.getCurrentPlayerNumber()))
        {
            return;  // <--- Early return: not current player ---
        }

        switch (gs)
        {
        case SOCGame.WAITING_FOR_DISCOVERY:
            showDiscardOrGainDialog(2, false);
            break;

        case SOCGame.WAITING_FOR_MONOPOLY:
            showMonopolyDialog();
            break;

        case SOCGame.WAITING_FOR_ROBBER_OR_PIRATE:
            java.awt.EventQueue.invokeLater(new ChooseMoveRobberOrPirateDialog());
            break;

        default:
            clientHand.updateAtOurGameState();
        }
    }

    /**
     * Handle updates after pieces have changed on the board.
     * For example, when scenario {@link SOCGameOption#K_SC_PIRI}
     * converts players' ships to warships, changes the strength
     * of a pirate fortress, etc.
     *<P>
     * Call <b>after</b> updating game state.  This is different than
     * {@link #updateAtPutPiece(int, int, int, boolean, int)} which
     * updates both the {@link SOCGame} and the board.
     *<P>
     * Currently, hand panels aren't updated (piece counts or VP total), only {@link SOCBoardPanel}.
     * @since 2.0.00
     */
    public void updateAtPiecesChanged()
    {
        boardPanel.repaint();
    }

    /**
     * Handle updates after putting a piece on the board,
     * or moving a ship that was already placed.
     * Place or move the piece within our {@link SOCGame}
     * and visually on our {@link SOCBoardPanel}.
     *
     * @param mesPn  The piece's player number
     * @param coord  The piece's coordinate.  If <tt>isMove</tt>, the coordinate to move <em>from</em>.
     * @param pieceType  Piece type, like {@link SOCPlayingPiece#CITY}
     * @param isMove   If true, it's a move, not a new placement; valid only for ships.
     * @param moveToCoord  If <tt>isMove</tt>, the coordinate to move <em>to</em>.  Otherwise ignored.
     *
     * @see #updateAtPiecesChanged()
     * @since 2.0.00
     */
    public void updateAtPutPiece
        (final int mesPn, final int coord, final int pieceType,
         final boolean isMove, final int moveToCoord)
    {
        // TODO consider more effic way for flushBoardLayoutAndRepaint, without the =null

        final SOCPlayer pl = (pieceType != SOCPlayingPiece.VILLAGE) ? game.getPlayer(mesPn) : null;
        final SOCPlayer oldLongestRoadPlayer = game.getPlayerWithLongestRoad();
        final SOCHandPanel mesHp = (pieceType != SOCPlayingPiece.VILLAGE) ? getPlayerHandPanel(mesPn) : null;
        final boolean[] debugShowPotentials = boardPanel.debugShowPotentials;
        final SOCPlayingPiece pp;

        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:
            pp = new SOCRoad(pl, coord, null);
            game.putPiece(pp);
            mesHp.updateValue(PlayerClientListener.UpdateType.Road);
            if (debugShowPotentials[4] || debugShowPotentials[5] || debugShowPotentials[7])
                boardPanel.flushBoardLayoutAndRepaint();

            break;

        case SOCPlayingPiece.SETTLEMENT:
            pp = new SOCSettlement(pl, coord, null);
            game.putPiece(pp);
            mesHp.updateValue(PlayerClientListener.UpdateType.Settlement);

            /**
             * if this is the second initial settlement, then update the resource display
             */
            if (mesHp.isClientPlayer())
            {
                mesHp.updateValue(PlayerClientListener.UpdateType.Clay);
                mesHp.updateValue(PlayerClientListener.UpdateType.Ore);
                mesHp.updateValue(PlayerClientListener.UpdateType.Sheep);
                mesHp.updateValue(PlayerClientListener.UpdateType.Wheat);
                mesHp.updateValue(PlayerClientListener.UpdateType.Wood);
            } else {
                mesHp.updateValue(PlayerClientListener.UpdateType.Resources);
            }

            if (debugShowPotentials[4] || debugShowPotentials[5] || debugShowPotentials[7]
                || debugShowPotentials[6])
                boardPanel.flushBoardLayoutAndRepaint();

            break;

        case SOCPlayingPiece.CITY:
            pp = new SOCCity(pl, coord, null);
            game.putPiece(pp);
            mesHp.updateValue(PlayerClientListener.UpdateType.Settlement);
            mesHp.updateValue(PlayerClientListener.UpdateType.City);

            if (debugShowPotentials[4] || debugShowPotentials[5] || debugShowPotentials[7]
                || debugShowPotentials[6])
                boardPanel.flushBoardLayoutAndRepaint();

            break;

        case SOCPlayingPiece.SHIP:
            pp = new SOCShip(pl, coord, null);
            if (! isMove)
            {
                game.putPiece(pp);
                mesHp.updateValue(PlayerClientListener.UpdateType.Ship);
            } else {
                game.moveShip((SOCShip) pp, moveToCoord);
            }

            if (debugShowPotentials[4] || debugShowPotentials[5] || debugShowPotentials[7])
                boardPanel.flushBoardLayoutAndRepaint();

            break;

        case SOCPlayingPiece.VILLAGE:
            // no need to refresh boardPanel after receiving each village
            pp = new SOCVillage(coord, game.getBoard());
            game.putPiece(pp);

            return; // <--- Early return: Piece is part of board initial layout, not player info ---

        case SOCPlayingPiece.FORTRESS:
            pp = new SOCFortress(pl, coord, game.getBoard());
            game.putPiece(pp);
            return; // <--- Early return: Piece is part of board initial layout, not added during game ---

        default:
            chatPrintDebug("* Unknown piece type " + pieceType + " at coord 0x" + Integer.toHexString(coord));
            return;  // <--- Early return ---
        }

        mesHp.updateValue(PlayerClientListener.UpdateType.VictoryPoints);
        boardPanel.repaint();
        buildingPanel.updateButtonStatus();
        if (game.isDebugFreePlacement() && game.isInitialPlacement())
            boardPanel.updateMode();  // update here, since gamestate doesn't change to trigger update

        /**
         * Check for and announce change in longest road; update all players' victory points.
         */
        SOCPlayer newLongestRoadPlayer = game.getPlayerWithLongestRoad();
        if (newLongestRoadPlayer != oldLongestRoadPlayer)
        {
            updateLongestLargest(true, oldLongestRoadPlayer, newLongestRoadPlayer);
        }
    }

    /**
     * A player's piece has been removed from the board.
     * Updates game state and refreshes the board display by calling {@link #updateAtPiecesChanged()}.
     *<P>
     * Currently, only ships can be removed, in game scenario {@code _SC_PIRI}.
     * Other {@code pieceType}s are ignored.
     *
     * @param player  Player who owns the ship
     * @param pieceCoordinate  Ship's node coordinate
     * @param pieceType  The piece type identifier {@link SOCPlayingPiece#SHIP}
     * @since 2.0.00
     */
    public void updateAtPieceRemoved(SOCPlayer player, int pieceCoordinate, int pieceType)
    {
        switch (pieceType)
        {
        case SOCPlayingPiece.SHIP:
            game.removeShip(new SOCShip(player, pieceCoordinate, null));
            updateAtPiecesChanged();
            break;

        default:
            System.err.println("PI.updateAtPieceRemoved called for un-handled type " + pieceType);
        }
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
     * @param evt Event code
     * @param detail  Game piece, coordinate, or other data about the event, or null, depending on <tt>evt</tt>
     * @see #playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)
     * @since 2.0.00
     */
    public void gameEvent(final SOCGame ga, final SOCScenarioGameEvent evt, final Object detail)
    {
        // stub for now
    }

    /**
     * Listener callback for per-player scenario events on the large sea board.
     * For example, there might be an SVP awarded for settlements.
     * @param ga  Game
     * @param pl  Player
     * @param evt  Event code
     * @param flagsChanged  True if this event changed {@link SOCPlayer#getScenarioPlayerEvents()},
     *             {@link SOCPlayer#getSpecialVP()}, or another flag documented for <tt>evt</tt> in
     *             {@link SOCScenarioPlayerEvent}
     * @param obj  Object related to the event, or null; documented for <tt>evt</tt> in {@link SOCScenarioPlayerEvent}.
     *             Example: The {@link SOCVillage} for {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     * @see #gameEvent(SOCGame, SOCScenarioGameEvent, Object)
     * @since 2.0.00
     */
    public void playerEvent(final SOCGame ga, final SOCPlayer pl, final SOCScenarioPlayerEvent evt,
        final boolean flagsChanged, final Object obj)
    {
        final SOCHandPanel mesHp = getPlayerHandPanel(pl.getPlayerNumber());
        if (mesHp == null)
            return;

        if (flagsChanged && (pl.getSpecialVP() != 0))
        {
            // assumes will never be reduced to 0 again
            //   so won't ever need to hide SVP counter
            mesHp.updateValue(PlayerClientListener.UpdateType.SpecialVictoryPoints);
            mesHp.updateValue(PlayerClientListener.UpdateType.VictoryPoints);  // call after SVP, not before, in case ends the game
            // (This code also appears in SOCPlayerClient.handlePLAYERELEMENT)
        }
    }

    /**
     * Gamestate just became {@link SOCGame#WAITING_FOR_DISCARDS}
     * or {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE}.
     * Set up a timer to wait 1 second before showing "Discarding..."
     * or "Picking Resources..." balloons in players' handpanels.
     * Uses {@link SOCPIDiscardOrPickMsgTask}.
     * @param isDiscard  True for discard, false for picking gold-hex resources
     */
    private void discardOrPickTimerSet(final boolean isDiscard)
    {
        synchronized (showingPlayerDiscardOrPick_lock)
        {
            if (showingPlayerDiscardOrPick_task != null)
            {
                showingPlayerDiscardOrPick_task.cancel();  // cancel any previous
            }
            showingPlayerDiscardOrPick_task = new SOCPIDiscardOrPickMsgTask(this, isDiscard);

            // Run once, after a brief delay in case only robots must discard.
            gameDisplay.getEventTimer().schedule(showingPlayerDiscardOrPick_task, 1000 /* ms */ );
        }
    }

    /**
     * Cancel any "discarding..." or "picking resources..." timer, and clear the message if showing.
     */
    private void discardOrPickTimerClear()
    {
        synchronized (showingPlayerDiscardOrPick_lock)
        {
            if (showingPlayerDiscardOrPick_task != null)
            {
                showingPlayerDiscardOrPick_task.cancel();  // cancel any previous
                showingPlayerDiscardOrPick_task = null;
            }
            if (showingPlayerDiscardOrPick)
            {
                for (int i = hands.length - 1; i >= 0; --i)
                    hands[i].clearDiscardOrPickMsg();
                showingPlayerDiscardOrPick = false;
            }
        }
    }

    /**
     * Handle board reset (new game with same players, same game name).
     * The reset message will be followed with others which will fill in the game state.
     * Most GUI panels are destroyed and re-created.  Player chat text is kept.
     *
     * @param newGame New game object
     * @param rejoinPlayerNumber Sanity check - must be our correct player number in this game
     * @param requesterNumber Player who requested the board reset
     * 
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     */
    public void resetBoard(SOCGame newGame, int rejoinPlayerNumber, int requesterNumber)
    {
        if (clientHand == null)
            return;
        if (clientHandPlayerNum != rejoinPlayerNumber)
            return;
        if (newGame == null)
            throw new IllegalArgumentException("newGame is null");

        // Feedback: "busy" mouse cursor while clearing and re-laying out the components
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Clear out old state (similar to constructor)
        int oldGameState = game.getResetOldGameState();
        game = newGame;
        if (gameStats != null)
            gameStats.dispose();
        gameStats = new SOCGameStatistics(game);
        for (int i = 0; i < hands.length; ++i)
        {
            hands[i].removePlayer();  // will cancel roll countdown timer, right-click menus, etc
            hands[i].setEnabled(false);
            hands[i].destroy();
        }
        final String prevChatText = chatDisplay.getText();
        final boolean[] boardDebugShow = boardPanel.debugShowPotentials.clone();
        clientHand = null;
        clientHandPlayerNum = -1;

        removeAll();  // old sub-components
        initInterfaceElements(false);  // new sub-components

        // Clear from possible TITLEBAR_GAME_OVER
        setTitle(/*I*/TITLEBAR_GAME + game.getName() +
                 (game.isPractice ? "" : " [" + client.getNickname() + "]")/*18N*/);
        boardPanel.debugShowPotentials = boardDebugShow;

        validate();
        repaint();

        chatDisplay.append(prevChatText);
        String requesterName = game.getPlayer(requesterNumber).getName();
        if (requesterName == null)
            //i18n split into two keys?
            requesterName = /*I*/"player who left"/*18N*/;
        String resetMsg;
        if (oldGameState != SOCGame.OVER)
            resetMsg = "** "+/*I*/"The board was reset by " + requesterName + "."/*18N*/+"\n";
        else
            resetMsg = "** "+/*I*/"New game started by " + requesterName + "."/*18N*/+"\n";
        textDisplay.append(resetMsg);
        chatDisplay.append(resetMsg);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // Further messages from server will fill in the rest.
    }

    /**
     * set the face icon for a player
     *
     * @param pn  the number of the player
     * @param id  the id of the face image
     */
    public void changeFace(int pn, int id)
    {
        hands[pn].changeFace(id);
    }

    /**
     * if debug is enabled, print this in the chat display.
     */
    public void chatPrintDebug(String debugMsg)
    {
        if (! D.ebugIsEnabled())
            return;
        chatPrint(debugMsg + "\n");
    }

    /**
     * if debug is enabled, print this exception's stack trace in
     * the chat display.  This eases tracing of exceptions when
     * our code is called in AWT threads (such as EventDispatch).
     */
    public void chatPrintStackTrace(Throwable th)
    {
        chatPrintStackTrace(th, false);
    }
    
    private void chatPrintStackTrace(Throwable th, boolean isNested)
    {
        if (! D.ebugIsEnabled())
            return;
        String excepName = th.getClass().getName();
        if (! isNested)
            chatDisplay.append("** Exception occurred **\n");
        if (th.getMessage() != null)
            chatPrint(excepName + ": " + th.getMessage());
        else
            chatPrint(excepName);
        StringWriter backstack = new StringWriter();
        PrintWriter pw = new PrintWriter(backstack);
        th.printStackTrace(pw);
        pw.flush();
        chatPrint (backstack.getBuffer().toString());
        Throwable cause = th.getCause();
        if ((cause != null) && (cause != th)) // NOTE: getCause is 1.4+
        {
            chatDisplay.append("** --> Nested Cause Exception: **\n");
            chatPrintStackTrace (cause, true);
        }
        if (! isNested)
            chatDisplay.append("-- Exception ends: " + excepName + " --\n\n");
    }

    /**
     * Calculate a color towards gray, for a hilight or the robber ghost.
     * If srcColor is light, ghost color is darker. (average with gray)
     * If it's dark or midtone, ghost should be lighter. (average with white)
     * 
     * @param srcColor The color to ghost from
     * @return Ghost color based on srcColor
     */
    public static Color makeGhostColor(Color srcColor)
    {
        int outR, outG, outB;
        outR = srcColor.getRed();
        outG = srcColor.getGreen();
        outB = srcColor.getBlue();
        if ((outR + outG + outB) > (160 * 3))
        {
            // src is light, we should be dark. (average with gray)
            outR = (outR + 140) / 2;
            outG = (outG + 140) / 2;
            outB = (outB + 140) / 2;
        } else {
            // src is dark or midtone, we should be light. (average with white)
            outR = (outR + 255) / 2;
            outG = (outG + 255) / 2;
            outB = (outB + 255) / 2;
        }
        return new Color (outR, outG, outB);
    }
    
    /**
     * Arrange the custom layout. If a player sits down in a 6-player game, will need to
     * {@link #invalidate()} and call this again, because {@link SOCHandPanel} sizes will change.
     * Also, on first call, resets mouse cursor to normal, in case it was WAIT_CURSOR.
     * On first call, if the game options have a {@link SOCScenario} with any long description,
     * it will be shown in a popup via {@link #showScenarioInfoDialog()}.
     */
    @Override
    public void doLayout()
    {
        Insets i = getInsets();
        Dimension dim = getSize();
        if (prevSize != null)
        {
            needRepaintBorders = (dim.width != prevSize.width)
                || (dim.height != prevSize.height);
        }
        prevSize = dim;
        dim.width -= (i.left + i.right);
        dim.height -= (i.top + i.bottom);

        /**
         * Classic Sizing
         * (board size is fixed, cannot scale)
         *
        int bw = SOCBoardPanel.getPanelX();
        int bh = SOCBoardPanel.getPanelY();
        int hw = (dim.width - bw - 16) / 2;
        int hh = (dim.height - 12) / 2;
        int kw = bw;
        int kh = buildingPanel.getSize().height;
        int tfh = textInput.getSize().height;
        int tah = dim.height - bh - kh - tfh - 16;
         */

        /**
         * "Stretch" Scaleable-board Sizing:
         * If board can be at least 15% larger than minimum board width,
         * without violating minimum handpanel width, scale it larger.
         * Otherwise, use minimum board width (widen handpanels instead).
         * Handpanel height:
         * - If 4-player, 1/2 of window height
         * - If 6-player, 1/3 of window height, until client sits down.
         *   (Column of 3 on left side, 3 on right side, of this frame)
         *   Once sits down, that column's handpanel heights are
         *   1/2 of window height for the player's hand, and 1/4 for others.
         */
        final int bMinW, bMinH;
        {
            Dimension bpMinSz = boardPanel.getMinimumSize();
            bMinW = bpMinSz.width;
            bMinH = bpMinSz.height;
        }
        int bw = (dim.width - 16 - (2*SOCHandPanel.WIDTH_MIN));  // As wide as possible
        int bh = (int) ((bw * (long) bMinH) / bMinW);
        final int buildph = buildingPanel.getHeight();
        int tfh = textInput.getHeight();
        if (bh > (dim.height - buildph - 16 - (int)(5.5f * tfh)))
        {
            // Window is wide: board would become taller than fits in window.
            // Re-calc board max height, then board width.
            bh = dim.height - buildph - 16 - (int)(5.5f * tfh);  // As tall as possible
            bw = (int) ((bh * (long) bMinW) / bMinH);
        }
        int hw = (dim.width - bw - 16) / 2;
        int tah = dim.height - bh - buildph - tfh - 16;

        boolean canScaleBoard = (bw >= (1.15f * bMinW));
        if (canScaleBoard)
        {
            try
            {
                boardPanel.setBounds(i.left + hw + 8, i.top + tfh + tah + 8, bw, bh);
            }
            catch (IllegalArgumentException e)
            {
                canScaleBoard = false;
            }
        }
        if (! canScaleBoard)
        {
            bw = bMinW;
            bh = bMinH;
            hw = (dim.width - bw - 16) / 2;
            tah = dim.height - bh - buildph - tfh - 16;
            try
            {
                boardPanel.setBounds(i.left + hw + 8, i.top + tfh + tah + 8, bw, bh);
            }
            catch (IllegalArgumentException ee)
            {
                bw = boardPanel.getWidth();
                bh = boardPanel.getHeight();
                hw = (dim.width - bw - 16) / 2;
                tah = dim.height - bh - buildph - tfh - 16;
            }
        }
        boardIsScaled = canScaleBoard;  // set field, now that we know if it works
        final int halfplayers = (is6player) ? 3 : 2;
        final int hh = (dim.height - 12) / halfplayers;  // handpanel height
        final int kw = bw;

        buildingPanel.setBounds(i.left + hw + 8, i.top + tah + tfh + bh + 12, kw, buildph);

        // Hands start at top-left, go clockwise;
        // hp.setBounds also sets its blankStandIn's bounds.
        // Note that any hands[] could be null, due to async adds, calls, invalidations.

        try
        {
            if (! is6player)
            {
                hands[0].setBounds(i.left + 4, i.top + 4, hw, hh);
                if (game.maxPlayers > 1)
                {
                    hands[1].setBounds(i.left + hw + bw + 12, i.top + 4, hw, hh);
                    hands[2].setBounds(i.left + hw + bw + 12, i.top + hh + 8, hw, hh);
                    hands[3].setBounds(i.left + 4, i.top + hh + 8, hw, hh);
                }
            }
            else
            {
                // 6-player layout:
                // If client player isn't sitting yet, all handpanels are 1/3 height of window.
                // Otherwise, they're 1/3 height in the column of 3 which doesn't contain the
                // client. and roughly 1/4 or 1/2 height in the client's column.
    
                if ((clientHandPlayerNum == -1) ||
                    ((clientHandPlayerNum >= 1) && (clientHandPlayerNum <= 3)))
                {
                    hands[0].setBounds(i.left + 4, i.top + 4, hw, hh);
                    hands[4].setBounds(i.left + 4, i.top + 2 * hh + 12, hw, hh);
                    hands[5].setBounds(i.left + 4, i.top + hh + 8, hw, hh);
                }
                if ((clientHandPlayerNum < 1) || (clientHandPlayerNum > 3))
                {
                    hands[1].setBounds(i.left + hw + bw + 12, i.top + 4, hw, hh);
                    hands[2].setBounds(i.left + hw + bw + 12, i.top + hh + 8, hw, hh);
                    hands[3].setBounds(i.left + hw + bw + 12, i.top + 2 * hh + 12, hw, hh);
                }
                if (clientHandPlayerNum != -1)
                {
                    // Lay out the column we're sitting in.
                    final boolean isRight;
                    final int[] hp_idx;
                    final int hp_x;
                    isRight = ((clientHandPlayerNum >= 1) && (clientHandPlayerNum <= 3));
                    if (isRight)
                    {
                        final int[] idx_right = {1, 2, 3};
                        hp_idx = idx_right;
                        hp_x = i.left + hw + bw + 12;
                    } else {
                        final int[] idx_left = {0, 5, 4};
                        hp_idx = idx_left;
                        hp_x = i.left + 4;
                    }
                    for (int ihp = 0, hp_y = i.top + 4; ihp < 3; ++ihp)
                    {
                        SOCHandPanel hp = hands[hp_idx[ihp]];
                        int hp_height;
                        if (hp_idx[ihp] == clientHandPlayerNum)
                            hp_height = (dim.height - 12) / 2 - (2 * ColorSquare.HEIGHT);
                        else
                            hp_height = (dim.height - 12) / 4 + ColorSquare.HEIGHT;
                        hp.setBounds(hp_x, hp_y, hw, hp_height);
                        hp.invalidate();
                        hp.doLayout();
                        hp_y += (hp_height + 4);
                    }
                }
            }
        }
        catch (NullPointerException e) {}

        int tdh, cdh;
        if (game.isPractice)
        {
            // Game textarea larger than chat textarea
            cdh = (int) (2.2f * tfh);
            tdh = tah - cdh;
            if (tdh < cdh)
            {
                tdh = cdh;
                cdh = tah - cdh;
            }
        }
        else
        {
            // Equal-sized text, chat textareas
            tdh = tah / 2;
            cdh = tah - tdh;
        }
        if (textDisplaysLargerTemp_needsLayout && textDisplaysLargerTemp)
        {
            // expanded size (temporary)
            final int x = i.left + (hw / 2);
            final int w = dim.width - 2 * (hw - x);
            int h = 5 * tfh;  // initial guess of height
            if (h < boardPanel.getY() - tfh)
                h = boardPanel.getY() - tfh;

            // look for a better height;
            // Use height of shortest handpanel as reference.
            {
                int hf = hands[0].getHeight();
                int h1 = hands[1].getHeight();
                if (h1 < hf)
                    hf = h1;
                hf = hf - cdh - tfh - 15;
                if (hf > h)
                    h = hf;
            }

            textDisplay.setBounds(x, i.top + 4, w, h);
            if (! game.isPractice)
                cdh += 20;
            chatDisplay.setBounds(x, i.top + 4 + h, w, cdh);
            h += cdh;
            textInput.setBounds(x, i.top + 4 + h, w, tfh);

            // focus here for easier chat typing
            textInput.requestFocusInWindow();
        } else {
            // standard size
            textDisplay.setBounds(i.left + hw + 8, i.top + 4, bw, tdh);
            chatDisplay.setBounds(i.left + hw + 8, i.top + 4 + tdh, bw, cdh);
            textInput.setBounds(i.left + hw + 8, i.top + 4 + tah, bw, tfh);

            // scroll to bottom of textDisplay, chatDisplay after resize from expanded
            EventQueue.invokeLater(new Runnable()
            {
                public void run()
                {
                    chatDisplay.setCaretPosition(chatDisplay.getText().length());
                    textDisplay.setCaretPosition(textDisplay.getText().length());
                }
            });
        }
        textDisplaysLargerTemp_needsLayout = false;

        npix = textDisplay.getPreferredSize().width;
        ncols = (int) (((bw) * 100.0f) / (npix)) - 2; // use float division for closer approximation

        //FontMetrics fm = this.getFontMetrics(textDisplay.getFont());
        //int nrows = (tdh / fm.getHeight()) - 1;

        //textDisplay.setMaximumLines(nrows);
        //nrows = (cdh / fm.getHeight()) - 1;

        //chatDisplay.setMaximumLines(nrows);
        boardPanel.doLayout();

        /**
         * Reset mouse cursor from WAIT_CURSOR to normal
         * (set in SOCPlayerClient.startPracticeGame or .guardedActionPerform).
         */
        if (layoutNotReadyYet)
        {
            gameDisplay.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            layoutNotReadyYet = false;
            repaint();
        }

        if (! didGameScenarioPopupCheck)
        {
            showScenarioInfoDialog();
            didGameScenarioPopupCheck = true;
        }
    }

    /**
     * For 6-player board, make the text displays larger/smaller when mouse
     * enters/exits them.
     * Timer will set textDisplaysLargerTemp_needsLayout, invalidate(), doLayout().
     * Wait 200 ms first, to avoid flicker in case of several related
     * {@link #mouseExited(MouseEvent)}/{@link #mouseEntered(MouseEvent)} events
     * (such as moving mouse from {@link #textDisplay} to {@link #chatDisplay}).
     * @since 1.1.08
     */
    private void textDisplaysLargerSetResizeTimer()
    {
        getEventTimer().schedule(new SOCPITextDisplaysLargerTask(), 200 /* ms */ );
    }

    /**
     * Track TextField mouse cursor entry on 6-player board (MouseListener).
     * Listener is not added unless {@link #is6player}.
     * For use by {@link #textDisplay}, {@link #chatDisplay}, {@link #textInput}.
     * Calls {@link #textDisplaysLargerSetResizeTimer()}.
     * @since 1.1.08
     */
    public void mouseEntered(MouseEvent e)
    {
        if (! is6player)
            return;

        final Object src = e.getSource();
        if (src == textDisplay)
            textDisplayHasMouse = true;
        else if (src == chatDisplay)
            chatDisplayHasMouse = true;
        else if (src == textInput)
            textInputHasMouse = true;
        else if (textDisplaysLargerTemp)
        {
            if (src == boardPanel)
                sbFixBHasMouse = true;
            else if (src == hands[0])
                sbFixLHasMouse = true;
            else if (src == hands[1])
                sbFixRHasMouse = true;
        }
        else
        {
            return;  // <-- Unknown source; don't trigger textDisplays resize ---
        }

        // will set textDisplaysLargerTemp_needsLayout, invalidate(), doLayout()
        textDisplaysLargerSetResizeTimer();
    }

    /**
     * Track TextField mouse cursor exit on 6-player board (MouseListener).
     * Same details as {@link #mouseEntered(MouseEvent)}.
     * @since 1.1.08
     */
    public void mouseExited(MouseEvent e)
    {
        if (! is6player)
            return;

        final Object src = e.getSource();
        if (src == textDisplay)
            textDisplayHasMouse = false;
        else if (src == chatDisplay)
            chatDisplayHasMouse = false;
        else if (src == textInput)
            textInputHasMouse = false;
        else if (src == this)
        {
            textDisplayHasMouse = false;
            chatDisplayHasMouse = false;
            textInputHasMouse = false;
        }
        else if (sbFixNeeded)
        {
            if (src == boardPanel)
                sbFixBHasMouse = false;
            else if (src == hands[0])
                sbFixLHasMouse = false;
            else if (src == hands[1])
                sbFixRHasMouse = false;

            return;  // <-- early return: no resize from exiting sbFix areas ---
        }
        else
        {
            return;  // <-- Unknown source; don't trigger textDisplays resize ---
        }

        // will set textDisplaysLargerTemp_needsLayout, invalidate(), doLayout()
        textDisplaysLargerSetResizeTimer();
    }

    /**
     * If any text area is clicked in a 6-player game, give
     * focus to the text input box for ease-of-use.  For details
     * see the {@link #textDisplaysLargerWhen} javadoc.
     * @since 1.1.13
     */
    public void mouseClicked(MouseEvent e)
    {
        if (! is6player)
            return;
        final Object src = e.getSource();
        if ((src != textDisplay) && (src != chatDisplay))
            return;

        final long when = e.getWhen();
        if ((textDisplaysLargerWhen == 0L) || (Math.abs(when - textDisplaysLargerWhen) < 1000L))
            textInput.requestFocusInWindow();
    }

    /**
     * Stub for MouseListener.
     * @since 1.1.08
     */
    public void mousePressed(MouseEvent e) { }

    /**
     * Stub for MouseListener.
     * @since 1.1.08
     */
    public void mouseReleased(MouseEvent e) { }

    protected boolean isClientPlayer(SOCPlayer p)
    {
        if (p == null)
            return false;
        int pn = p.getPlayerNumber();
        return hands[pn].isClientPlayer();
        //return p.getName().equals(client.getNickname());
    }

    //========================================================
    /**
     * Nested classes begin here
     */
    //========================================================

    /**
     * Client Bridge to translate interface to SOCPlayerInterface methods.
     * For most methods here, {@link PlayerClientListener} will have their javadoc.
     * @author paulbilnoski
     * @since 2.0.00
     */
    private static class ClientBridge implements PlayerClientListener
    {
        final SOCPlayerInterface pi;

        /**
         * Create a new ClientBridge for this playerinterface and its {@link SOCGame}.
         * @param pi  A player interface, already linked to a game
         */
        public ClientBridge(SOCPlayerInterface pi)
        {
            this.pi = pi;
        }

        /**
         * Show a dice roll result.
         * Call this after updating game state with the roll result.
         */
        public void diceRolled(SOCPlayer player, int roll)
        {
            pi.showDiceResult(player, roll);
        }

        public void playerJoined(String nickname)
        {
            final String msg = "*** " + /*I*/nickname + " has joined this game.\n"/*18N*/;
            pi.print(msg);
            if ((pi.game != null) && (pi.game.getGameState() >= SOCGame.START1A))
                pi.chatPrint(msg);
        }

        public void playerLeft(String nickname, SOCPlayer player)
        {
            if (player != null)
            {
                //
                //  This user was not a spectator.
                //  Remove first from interface, then from game data.
                //
                pi.removePlayer(player.getPlayerNumber());
            }
            else if (pi.game.getGameState() >= SOCGame.START1A)
            {
                //  Spectator, game in progress.
                //  Server prints it in the game text area,
                //  and we also print in the chat area (less clutter there).
                pi.chatPrint("* " + nickname + " left the game");
            }
        }

        public void playerSitdown(int playerNumber, String sitterNickname)
        {
            pi.addPlayer(sitterNickname, playerNumber);

            String nickname = pi.getClient().getNickname();

            /**
             * let the board panel & building panel find our player object if we sat down
             */
            if (nickname.equals(sitterNickname))
            {
                pi.getBoardPanel().setPlayer();
                pi.getBuildingPanel().setPlayer();
            }

            /**
             * update the hand panel's displayed values
             */
            final SOCHandPanel hp = pi.getPlayerHandPanel(playerNumber);
            hp.updateValue(PlayerClientListener.UpdateType.Road);
            hp.updateValue(PlayerClientListener.UpdateType.Settlement);
            hp.updateValue(PlayerClientListener.UpdateType.City);
            if (pi.game.hasSeaBoard)
                hp.updateValue(PlayerClientListener.UpdateType.Ship);
            hp.updateValue(PlayerClientListener.UpdateType.Knight);
            hp.updateValue(PlayerClientListener.UpdateType.VictoryPoints);
            hp.updateValue(PlayerClientListener.UpdateType.LongestRoad);
            hp.updateValue(PlayerClientListener.UpdateType.LargestArmy);

            if (nickname.equals(sitterNickname))
            {
                hp.updateValue(PlayerClientListener.UpdateType.Clay);
                hp.updateValue(PlayerClientListener.UpdateType.Ore);
                hp.updateValue(PlayerClientListener.UpdateType.Sheep);
                hp.updateValue(PlayerClientListener.UpdateType.Wheat);
                hp.updateValue(PlayerClientListener.UpdateType.Wood);
                hp.updateDevCards();
            }
            else
            {
                hp.updateValue(PlayerClientListener.UpdateType.Resources);
                hp.updateValue(PlayerClientListener.UpdateType.DevCards);
            }
        }

        /**
         * Game's current player has changed. Update displays.
         * Repaint board panel, update buttons' status, etc.
         */
        public void playerTurnSet(int playerNumber)
        {
            pi.updateAtTurn(playerNumber);
        }

        public void playerPiecePlaced(SOCPlayer player, int coordinate, int pieceType)
        {
            pi.updateAtPutPiece(player.getPlayerNumber(), coordinate, pieceType, false, 0);
        }

        public void playerPieceMoved(SOCPlayer player, int sourceCoordinate, int targetCoordinate, int pieceType)
        {
            pi.updateAtPutPiece(player.getPlayerNumber(),
                                sourceCoordinate,
                                pieceType,
                                true,
                                targetCoordinate);
        }

        public void playerPieceRemoved(SOCPlayer player, int pieceCoordinate, int pieceType)
        {
            pi.updateAtPieceRemoved(player, pieceCoordinate, pieceType);
        }

        public void playerSVPAwarded(SOCPlayer player, int numSvp, String awardDescription)
        {
            if (pi.getClientHand() == null)
                return;  // not seated yet (joining game in progress)
            pi.updateAtSVPText(player.getName(), numSvp, awardDescription);
        }

        public void playerResourcesUpdated(SOCPlayer player)
        {
            SOCHandPanel hpan = pi.getPlayerHandPanel(player.getPlayerNumber());
            hpan.updateValue(PlayerClientListener.UpdateType.Resources);
        }

        public void playerElementUpdated(SOCPlayer player, PlayerClientListener.UpdateType utype)
        {
            final SOCHandPanel hpan = player == null ? null : pi.getPlayerHandPanel(player.getPlayerNumber());  // null if no player
            int hpanUpdateRsrcType = 0;  // If not 0, update this type's amount display

            switch (utype)
            {
            case Road:
                hpan.updateValue(utype);
                break;

            case Settlement:
                hpan.updateValue(utype);
                break;

            case City:
                hpan.updateValue(utype);
                break;

            case Ship:
                hpan.updateValue(utype);
                break;

            case Knight:
                // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
                hpan.updateValue(utype);
                break;

            case Clay:
                hpanUpdateRsrcType = SOCResourceConstants.CLAY;
                break;

            case Ore:
                hpanUpdateRsrcType = SOCResourceConstants.ORE;
                break;

            case Sheep:
                hpanUpdateRsrcType = SOCResourceConstants.SHEEP;
                break;

            case Wheat:
                hpanUpdateRsrcType = SOCResourceConstants.WHEAT;
                break;

            case Wood:
                hpanUpdateRsrcType = SOCResourceConstants.WOOD;
                break;

            case Unknown:
                hpan.updateValue(PlayerClientListener.UpdateType.Resources);
                break;

            case SpecialVictoryPoints:
                if (player.getSpecialVP() != 0)
                {
                    // assumes will never be reduced to 0 again
                    hpan.updateValue(PlayerClientListener.UpdateType.SpecialVictoryPoints);
                    hpan.updateValue(PlayerClientListener.UpdateType.VictoryPoints);  // call after SVP, not before, in case ends the game
                    // (This code also appears in SOCPlayerInterface.playerEvent)
                }
                break;

            case Cloth:
                if (player != null)
                {
                    hpan.updateValue(utype);
                    hpan.updateValue(PlayerClientListener.UpdateType.VictoryPoints);  // 2 cloth = 1 VP
                } else {
                    pi.getBuildingPanel().updateClothCount();
                }
                break;

            case Warship:
                pi.updateAtPiecesChanged();
                break;
                
            default:
                System.out.println("Unhandled case in PlayerClientListener["+utype+"]");
                break;
            }

            if (hpan == null)
                return;  // <--- early return: not a per-player element ---

            if (hpanUpdateRsrcType != 0)
            {
                if (hpan.isClientPlayer())
                {
                    hpan.updateValue(utype);
                }
                else
                {
                    hpan.updateValue(PlayerClientListener.UpdateType.Resources);
                }
            }

            if (hpan.isClientPlayer() && (pi.getGame().getGameState() != SOCGame.NEW))
            {
                pi.getBuildingPanel().updateButtonStatus();
            }
        }

        public void requestedSpecialBuild(SOCPlayer player)
        {
            if (player.hasAskedSpecialBuild())
                pi.printFormat(/*I*/"* {0} wants to Special Build."/*18N*/, player.getName());
            if (pi.isClientPlayer(player))
                pi.getBuildingPanel().updateButtonStatus();
        }

        public void requestedGoldResourceCountUpdated(SOCPlayer player, int countToSelect)
        {
            final SOCHandPanel hpan = pi.getPlayerHandPanel(player.getPlayerNumber());
            hpan.updatePickGoldHexResources();
        }

        public void playerDevCardUpdated(SOCPlayer player)
        {
            if (pi.isClientPlayer(player))
            {
                SOCHandPanel hp = pi.getClientHand();
                hp.updateDevCards();
                hp.updateValue(PlayerClientListener.UpdateType.VictoryPoints);
            }
            else
            {
                pi.getPlayerHandPanel(player.getPlayerNumber()).updateValue(PlayerClientListener.UpdateType.DevCards);
            }
        }

        public void playerFaceChanged(SOCPlayer player, int faceId)
        {
            pi.changeFace(player.getPlayerNumber(), faceId);
        }

        public void playerStats(EnumMap<PlayerClientListener.UpdateType, Integer> stats)
        {
            pi.print("* "+/*I*/"Your resource rolls: (Clay, Ore, Sheep, Wheat, Wood)"/*18N*/);
            StringBuffer sb = new StringBuffer("* ");
            int total = 0;

            PlayerClientListener.UpdateType[] types = {
                PlayerClientListener.UpdateType.Clay,
                PlayerClientListener.UpdateType.Ore,
                PlayerClientListener.UpdateType.Sheep,
                PlayerClientListener.UpdateType.Wheat,
                PlayerClientListener.UpdateType.Wood
            };

            for (PlayerClientListener.UpdateType t : types)
            {
                int value = stats.get(t).intValue();
                total += value;
                sb.append(value);
                sb.append(", ");
            }
            // Remove the last comma-space
            sb.delete(sb.length()-2, sb.length());
            sb.append(". Total: ");
            sb.append(total);
            pi.print(sb.toString());
        }

        public void largestArmyRefresh(SOCPlayer old, SOCPlayer potentialNew)
        {
            pi.updateLongestLargest(false, old, potentialNew);
        }

        public void longestRoadRefresh(SOCPlayer old, SOCPlayer potentialNew)
        {
            pi.updateLongestLargest(true, old, potentialNew);
        }

        /**
         * The current game members (players and observers) are listed, and the
         * game is about to start.  Calls {@link SOCPlayerInterface#began(Vector)}.
         * @param names  Game member names; to see if each is a player, call {@link SOCGame#getPlayer(String)}.
         */
        public void membersListed(Collection<String> names)
        {
            Vector<String> v = new Vector<String>(names);
            pi.began(v);
        }

        public void boardLayoutUpdated()
        {
            pi.updateAtNewBoard();
        }

        public void boardUpdated()
        {
            pi.getBoardPanel().flushBoardLayoutAndRepaint();
        }

        public void pieceValueUpdated(final SOCPlayingPiece piece)
        {
            pi.getBoardPanel().pieceValueUpdated(piece);
        }

        public void boardPotentialsUpdated()
        {
            pi.getBoardPanel().flushBoardLayoutAndRepaintIfDebugShowPotentials();
        }

        public void boardReset(SOCGame newGame, int newSeatNumber, int requestingPlayerNumber)
        {
            pi.resetBoard(newGame, newSeatNumber, requestingPlayerNumber);
        }

        public void boardResetVoteRequested(SOCPlayer requestor)
        {
            pi.resetBoardAskVote(requestor.getPlayerNumber());
        }

        public void boardResetVoteCast(SOCPlayer voter, boolean vote)
        {
            pi.resetBoardVoted(voter.getPlayerNumber(), vote);
        }

        public void boardResetVoteRejected()
        {
            pi.resetBoardRejected();
        }

        public void seatLockUpdated()
        {
            for (int i = 0; i < pi.game.maxPlayers; i++)
            {
                pi.getPlayerHandPanel(i).updateSeatLockButton();
                pi.getPlayerHandPanel(i).updateTakeOverButton();
            }
        }

        public void gameStarted()
        {
            pi.startGame();
        }

        public void gameStateChanged(int gameState)
        {
            pi.updateAtGameState();
        }

        public void gameEnded(Map<SOCPlayer, Integer> scores)
        {
            int[] scoresArray = new int[scores.size()];
            for (Map.Entry<SOCPlayer, Integer> e : scores.entrySet())
            {
                scoresArray[e.getKey().getPlayerNumber()] = e.getValue().intValue();
            }

            pi.updateAtOver(scoresArray);
        }

        public void gameDisconnected(String errorMessage)
        {
            pi.over(errorMessage);
        }

        public void messageBroadcast(String msg)
        {
            pi.chatPrint("::: " + msg + " :::");
        }

        public void messageSent(String nickname, String message)
        {
            if (nickname == null)
            {
                String starMesText = "* " + message;
                pi.print(starMesText);
                if (message.startsWith(">>>"))
                    pi.chatPrint(starMesText);
            }
            else
            {
                if (!pi.getClient().onIgnoreList(nickname))
                {
                    pi.chatPrint(nickname + ": " + message);
                }
            }
        }

        public void buildRequestCanceled(SOCPlayer player)
        {
            pi.getPlayerHandPanel(player.getPlayerNumber()).updateResourcesVP();
            pi.getBoardPanel().updateMode();
        }

        public void scen_SC_PIRI_pirateFortressAttackResult
            (final boolean wasRejected, final int defStrength, final int resultShipsLost)
        {
            if (wasRejected)
            {
                pi.print( /*I*/"* You cannot attack the pirate fortress right now."/*18N*/ );
                return;
            }

            final SOCGame ga = pi.getGame();
            final SOCPlayer cpl = ga.getPlayer(ga.getCurrentPlayerNumber());
            final SOCFortress fort = cpl.getFortress();
            final String cplName = cpl.getName();
            pi.printFormat( /*I*/"* {0} has attacked a pirate fortress (defense strength {1})."/*18N*/,
                cplName, Integer.toString(defStrength));

            String resDesc;  // used for game text print and popup window
            switch (resultShipsLost)
            {
            case 0:  resDesc = /*I*/"{0} wins!"/*18N*/;  break;
            case 1:  resDesc = /*I*/"{0} ties, and loses 1 ship."/*18N*/;  break;
            default: resDesc = /*I*/"{0} loses, and loses 2 ships."/*18N*/;  break;
                // case 2 is "default" so resDesc is always set for compiler
            }
            resDesc = MessageFormat.format(resDesc, cplName);  // 'Player 2 wins!'
            pi.print("* " + resDesc);

            final String resDesc2;
            if (resultShipsLost == 0)
            {
                if (fort == null)
                {
                    // defeated and recaptured
                    resDesc2 = MessageFormat.format
                        ( /*I*/"{0} has recaptured the fortress as a settlement."/*18N*/, cplName);
                } else {
                    // still needs to attack
                    resDesc2 = MessageFormat.format
                        ( /*I*/"The pirate fortress will be defeated after {0} more attack(s)."/*18N*/,
                         Integer.toString(fort.getStrength()));
                }
                pi.print("* " + resDesc2);
            } else {
                resDesc2 = null;
            }

            if (pi.clientIsCurrentPlayer() || (fort == null))
            {
                // popup if player is our client, or if recaptured
                StringBuffer sb = new StringBuffer( /*I*/"Pirate Fortress attack results:\n"/*18N*/ );
                sb.append( /*I*/"Defense strength: "/*18N*/ );
                sb.append(defStrength);
                sb.append('\n');
                sb.append(resDesc);
                if (resDesc2 != null)
                {
                    sb.append('\n');
                    sb.append(resDesc2);
                }

                final String s = sb.toString();
                EventQueue.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        NotifyDialog.createAndShow(pi.getGameDisplay(), pi, s, null, true);
                    }
                });

            }
        }

        public void robberMoved()
        {
            pi.getBoardPanel().repaint();
        }

        public void devCardDeckUpdated()
        {
            pi.updateDevCardCount();
        }

        public void requestedDiscard(int countToDiscard)
        {
            pi.showDiscardOrGainDialog(countToDiscard, true);
        }

        public void requestedResourceSelect(int countToDiscard)
        {
            pi.showDiscardOrGainDialog(countToDiscard, false);
        }

        public void requestedChoosePlayer(final List<SOCPlayer> choices, final boolean isNoneAllowed)
        {
            int[] pnums = new int[choices.size() + (isNoneAllowed ? 1 : 0)];
            int i = 0;
            for (SOCPlayer p : choices)
                pnums[i++] = p.getPlayerNumber();
            pi.showChoosePlayerDialog(choices.size(), pnums, isNoneAllowed);
        }

        public void requestedChooseRobResourceType(SOCPlayer player)
        {
            pi.showChooseRobClothOrResourceDialog(player.getPlayerNumber());
        }

        public void requestedTrade(SOCPlayer offerer)
        {
            pi.getPlayerHandPanel(offerer.getPlayerNumber()).updateCurrentOffer();
        }

        public void requestedTradeClear(SOCPlayer offerer)
        {
            if (offerer != null)
            {
                pi.getPlayerHandPanel(offerer.getPlayerNumber()).updateCurrentOffer();
            }
            else
            {
                for (int i = 0; i < pi.game.maxPlayers; ++i)
                {
                    pi.getPlayerHandPanel(i).updateCurrentOffer();
                }
            }
        }

        public void requestedTradeRejection(SOCPlayer rejecter)
        {
            pi.getPlayerHandPanel(rejecter.getPlayerNumber()).rejectOfferShowNonClient();
        }

        public void requestedTradeReset(SOCPlayer playerToReset)
        {
            final int pn = (playerToReset != null) ? playerToReset.getPlayerNumber() : -1;
            pi.clearTradeMsg(pn);
        }

        public void requestedDiceRoll()
        {
            pi.updateAtRollPrompt();
        }

        public void debugFreePlaceModeToggled(boolean isEnabled)
        {
            pi.setDebugFreePlacementMode(isEnabled);
        }
    }

    /**
     * This is the modal dialog to vote on another player's board reset request.
     * If game in progress, buttons are Reset and Continue Playing; default Continue.
     * If game is over, buttons are Restart and No thanks; default Restart.
     * Start a new thread to show, so message treating can continue as other players vote.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.00
     */
    protected static class ResetBoardVoteDialog extends AskDialog implements Runnable
    {
        /** Runs in own thread, to not tie up client's message-treater thread which initially shows the dialog. */
        private Thread rdt;

        /** If true, don't call any methods from callbacks here */
        private boolean askedDisposeQuietly;

        /**
         * Creates a new ResetBoardVoteDialog.
         *
         * @param cli      Player client interface
         * @param gamePI   Current game's player interface
         * @param requester  Name of player requesting the reset
         * @param gameIsOver The game is over - "Reset" button should be default (if not over, "Continue" is default)
         */
        protected ResetBoardVoteDialog(GameAwtDisplay cli, SOCPlayerInterface gamePI, String requester, boolean gameIsOver)
        {
            super(cli, gamePI, /*I*/"Reset board of game "
                    + gamePI.getGame().getName() + "?"/*18N*/,
                (gameIsOver
                    ? (/*I*/requester + " wants to start a new game."/*18N*/)
                    : (/*I*/requester + " wants to reset the game being played."/*18N*/)),
                (gameIsOver
                    ? /*I*/"Restart"/*18N*/
                    : /*I*/"Reset"/*18N*/),
                (gameIsOver
                    ? /*I*/"No thanks"/*18N*/
                    : /*I*/"Continue playing"/*18N*/),
                null,
                (gameIsOver ? 1 : 2));
            rdt = null;
            askedDisposeQuietly = false;
        }

        /**
         * React to the Reset button. (call playerClient.resetBoardVote)
         */
        @Override
        public void button1Chosen()
        {
            pcli.getGameManager().resetBoardVote(pi.getGame(), pi.getClientPlayerNumber(), true);
            pi.resetBoardClearDia();
        }

        /**
         * React to the No button. (call playerClient.resetBoardVote)
         */
        @Override
        public void button2Chosen()
        {
            pcli.getGameManager().resetBoardVote(pi.getGame(), pi.getClientPlayerNumber(), false);
            pi.resetBoardClearDia();
        }

        /**
         * React to the dialog window closed by user. (Vote No)
         */
        @Override
        public void windowCloseChosen()
        {
            if (! askedDisposeQuietly)
                button2Chosen();
        }

        /**
         * Make a new thread and show() in that thread.
         * Keep track of the thread, in case we need to dispose of it.
         */
        public void showInNewThread()
        {
            rdt = new Thread(this);
            rdt.setDaemon(true);
            rdt.setName("resetVoteDialog-" + pcli.getNickname());
            rdt.start();  // run method will show the dialog
        }

        public void disposeQuietly()
        {
            askedDisposeQuietly = true;
            //FIXME: Thread#stop is unsafe, need to tell the thread to internally terminate
            rdt.stop();
            dispose();
        }

        /**
         * In new thread, show ourselves. Do not call
         * directly; call {@link #showInNewThread()}.
         */
        public void run()
        {
            try
            {
                setVisible(true);
            }
            catch (ThreadDeath e) {}
            catch (Throwable e) {
                e.printStackTrace();
            }
        }

    }  // class ResetBoardVoteDialog

    /**
     * Modal dialog to ask whether to move the robber or the pirate ship.
     * Use the AWT event thread to show, so message treating can continue while the dialog is showing.
     * When the choice is made, calls {@link SOCPlayerClient.GameManager#choosePlayer(SOCGame, int)}
     * with {@link SOCChoosePlayer#CHOICE_MOVE_ROBBER CHOICE_MOVE_ROBBER}
     * or {@link SOCChoosePlayer#CHOICE_MOVE_PIRATE CHOICE_MOVE_PIRATE}.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    private class ChooseMoveRobberOrPirateDialog extends AskDialog implements Runnable
    {
        private static final long serialVersionUID = 2000L;

        /**
         * Creates a new ChooseMoveRobberOrPirateDialog.
         * To display the dialog without tying up the client's message-treater thread,
         * call {@link java.awt.EventQueue#invokeLater(Runnable) EventQueue.invokeLater(thisDialog)}.
         */
        protected ChooseMoveRobberOrPirateDialog()
        {
            super(getGameDisplay(), SOCPlayerInterface.this,
                /*I*/"Move robber or pirate?"/*18N*/,
                /*I*/"Do you want to move the robber or the pirate ship?"/*18N*/,
                /*I*/"Move Robber"/*18N*/,
                /*I*/"Move Pirate"/*18N*/,
                null, 1);
        }

        /**
         * React to the Move Robber button.
         * Call {@link SOCPlayerClient.GameManager#choosePlayer(SOCGame, int) pcli.choosePlayer(CHOICE_MOVE_ROBBER)}.
         */
        @Override
        public void button1Chosen()
        {
            pcli.getGameManager().chooseRobber(game);
        }

        /**
         * React to the Move Pirate button.
         * Call {@link SOCPlayerClient.GameManager#choosePlayer(SOCGame, int) pcli.choosePlayer(CHOICE_MOVE_PIRATE)}.
         */
        @Override
        public void button2Chosen()
        {
            pcli.getGameManager().choosePirate(game);
        }

        /**
         * React to the dialog window closed by user. (Default is move the robber)
         */
        @Override
        public void windowCloseChosen() { button1Chosen(); }

        /**
         * In the AWT event thread, show ourselves. Do not call directly;
         * call {@link java.awt.EventQueue#invokeLater(Runnable) EventQueue.invokeLater(thisDialog)}.
         */
        public void run()
        {
            try
            {
                setVisible(true);
            }
            catch (ThreadDeath e) {}
            catch (Throwable e) {
                e.printStackTrace();
            }
        }

    }  // nested class ChooseMoveRobberOrPirateDialog

    /**
     * Modal dialog to ask whether to rob cloth or a resource from the victim.
     * Start a new thread to show, so message treating can continue while the dialog is showing.
     * When the choice is made, calls {@link SOCPlayerClient.GameManager#choosePlayer(SOCGame, int)}.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    private class ChooseRobClothOrResourceDialog extends AskDialog implements Runnable
    {
        private static final long serialVersionUID = 2000L;

        /** Runs in own thread, to not tie up client's message-treater thread. */
        private Thread rdt;

        /** victim player number */
        private final int vpn;

        /**
         * Creates a new ChooseRobClothOrResourceDialog.
         * To display the dialog, call {@link #showInNewThread()}.
         * @param vpn  Victim player number
         */
        protected ChooseRobClothOrResourceDialog(final int vpn)
        {
            super(getGameDisplay(), SOCPlayerInterface.this,
                /*I*/"Rob cloth or resource?"/*18N*/,
                /*I*/"Do you want to steal cloth or a resource from this player?"/*18N*/,
                /*I*/"Steal Cloth"/*18N*/,
                /*I*/"Steal Resource"/*18N*/,
                null, 1);
            rdt = null;
            this.vpn = vpn;
        }

        /**
         * React to the Steal Cloth button.
         * Call {@link SOCPlayerClient.GameManager#choosePlayer(SOCGame, int) pcli.choosePlayer(-(vpn + 1))}.
         */
        @Override
        public void button1Chosen()
        {
            pcli.getGameManager().choosePlayer(game, -(vpn + 1));
        }

        /**
         * React to the Steal Resource button.
         * Call {@link SOCPlayerClient.GameManager#choosePlayer(SOCGame, int) pcli.choosePlayer(vpn)}.
         */
        @Override
        public void button2Chosen()
        {
            pcli.getGameManager().choosePlayer(game, vpn);
        }

        /**
         * React to the dialog window closed by user. (Default is steal resource)
         */
        @Override
        public void windowCloseChosen() { button2Chosen(); }

        /**
         * Make a new thread and show() in that thread.
         * Keep track of the thread, in case we need to dispose of it.
         */
        public void showInNewThread()
        {
            rdt = new Thread(this);
            rdt.setDaemon(true);
            rdt.setName("ChooseRobClothOrResourceDialog");
            rdt.start();  // run method will show the dialog
        }

        @Override
        public void dispose()
        {
            if (rdt != null)
            {
                //FIXME: Thread#stop is unsafe, need to tell the thread to internally terminate
                rdt.stop();
                rdt = null;
            }
            super.dispose();
        }

        /**
         * In new thread, show ourselves. Do not call
         * directly; call {@link #showInNewThread()}.
         */
        public void run()
        {
            try
            {
                setVisible(true);
            }
            catch (ThreadDeath e) {}
            catch (Throwable e) {
                e.printStackTrace();
            }
        }

    }  // nested class ChooseRobClothOrResourceDialog

    /**
     * This is the modal dialog to confirm resetting the board after a practice game.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.18
     */
    protected static class ResetBoardConfirmDialog extends AskDialog implements Runnable
    {
        /**
         * Creates a new ResetBoardConfirmDialog.
         * To display it from the AWT event thread, call {@link #run()}.
         * To display it from another thread, call
         * {@link EventQueue#invokeLater(Runnable) EventQueue.invokeLater(thisDialog)}.
         *
         * @param cli      Player client interface
         * @param gamePI   Current game's player interface
         */
        private ResetBoardConfirmDialog(GameAwtDisplay cli, SOCPlayerInterface gamePI)
        {
            super(cli, gamePI, /*I*/"Restart game?"/*18N*/,
                /*I*/"Reset the board and start a new game?"/*18N*/,
                /*I*/"Restart"/*18N*/,
                /*I*/"Cancel"/*18N*/,
                null,
                2);
        }

        /**
         * React to the Restart button. (call playerInterface.resetBoardRequest)
         */
        @Override
        public void button1Chosen()
        {
            pi.resetBoardRequest(false);
        }

        /**
         * React to the Cancel button. (do nothing)
         */
        @Override
        public void button2Chosen() {}

        /**
         * React to the dialog window closed by user. (do nothing)
         */
        @Override
        public void windowCloseChosen() {}

        /**
         * In AWT event thread, show ourselves. Do not call directly unless on that thread;
         * call {@link EventQueue#invokeLater(Runnable) EventQueue.invokeLater(thisDialog)}.
         */
        public void run()
        {
            setVisible(true);
        }

    }  // nested class ResetBoardConfirmDialog

    /**
     * React to window closing or losing focus (deactivation).
     * @author jdmonin
     * @since 1.1.00
     */
    private static class PIWindowAdapter extends WindowAdapter
    {
        private final GameAwtDisplay gd;
        private final SOCPlayerInterface pi;

        public PIWindowAdapter(GameAwtDisplay gd, SOCPlayerInterface spi)
        {
            this.gd = gd;
            pi = spi;
        }

        /**
         * Ask if player is sure - Leave the game when the window closes.
         * If they're observing, not playing, the window can close immediately.
         */
        @Override
        public void windowClosing(WindowEvent e)
        {
            if (pi.clientHandPlayerNum != -1)
                SOCQuitConfirmDialog.createAndShow(gd, pi);
            else
                pi.leaveGame();
        }

        /**
         * When window loses focus, if 6-player, unexpand the chat window if needed.
         * @since 1.1.12
         */
        @Override
        public void windowDeactivated(WindowEvent e)
        {
            if (! pi.textDisplaysLargerTemp)
                return;

            pi.textDisplayHasMouse = false;
            pi.chatDisplayHasMouse = false;
            pi.textInputHasMouse = false;
            pi.textDisplaysLargerTemp = false;
            pi.textDisplaysLargerTemp_needsLayout = true;
            pi.invalidate();
            pi.validate();  // call pi.doLayout()
        }

        /**
         * Clean up after the window is closed.
         * Close the GameStatisticsFrame if showing, etc.
         * @since 2.0.00
         */
        @Override
        public void windowClosed(WindowEvent e)
        {
            // Close stats frame if showing
            if (pi.buildingPanel != null)
                pi.buildingPanel.gameWindowClosed();
        }

    }  // MyWindowAdapter

    /**
     * Used for chat textfield setting/clearing initial prompt text
     * (TEXTINPUT_INITIAL_PROMPT_MSG).
     * It's expected that after the player sends their first line of chat text,
     * the listeners will be removed so we don't have the overhead of
     * calling these methods.
     * @author jdmonin
     * @since 1.1.00
     */
    private static class SOCPITextfieldListener
        extends KeyAdapter implements TextListener, FocusListener
    {
        private SOCPlayerInterface pi;

        public SOCPITextfieldListener(SOCPlayerInterface spi)
        {
            pi = spi;
        }

        /** If first keypress in initially empty field, clear that prompt message */
        @Override
        public void keyPressed(KeyEvent e)
        {
            if (! pi.textInputIsInitial)
            {
                return;
            }
            pi.textInputSetToInitialPrompt(false);
        }

        /**
         * If input text is cleared, and field is again empty, show the
         * prompt message unless player has already sent a line of chat.
         */
        public void textValueChanged(TextEvent e)
        {
            if (pi.textInputIsInitial || pi.textInputHasSent)
            {
                return;
            }
        }

        /**
         * If input text is cleared, and player leaves the textfield while it's empty,
         * show the prompt message unless they've already sent a line of chat.
         */
        public void focusLost(FocusEvent e)
        {
            if (pi.textInputIsInitial || pi.textInputHasSent)
            {
                return;
            }
            if (pi.textInput.getText().trim().length() == 0)
            {
                // Former contents were erased,
                // show the prompt message.
                // Trim in case it's " " due to
                // player previously hitting "enter" in an
                // initial field (actionPerformed).

                pi.textInputSetToInitialPrompt(true);
            }
        }

        /** Clear the initial prompt message when textfield is entered or clicked on. */
        public void focusGained(FocusEvent e)
        {
            if (! pi.textInputIsInitial)
            {
                return;
            }
            pi.textInputSetToInitialPrompt(false);
        }

    }  // SOCPITextfieldListener


    /**
     * When timer fires, show discard message or picking-resource message
     * for any other player (not client player) who must discard or pick.
     * @see SOCPlayerInterface#discardOrPickTimerSet(boolean)
     * @author jdmonin
     * @since 1.1.00
     */
    private static class SOCPIDiscardOrPickMsgTask extends TimerTask
    {
        private SOCPlayerInterface pi;
        private final boolean isDiscard;

        /**
         * Create a new SOCPIDiscardOrPickMsgTask.
         * After creating, you must schedule it
         * with {@link SOCPlayerClient#getEventTimer()}.{@link Timer#schedule(TimerTask, long) schedule(msgTask,delay)} .
         * @param spi  Our player interface
         * @param forDiscard  True for discard, false for picking gold-hex resources
         */
        public SOCPIDiscardOrPickMsgTask (SOCPlayerInterface spi, final boolean forDiscard)
        {
            pi = spi;
            isDiscard = forDiscard;
        }

        /**
         * Called when timer fires. Examine game state and players.
         * Sets "discarding..." or "picking..." at handpanels of discarding players.
         */
        @Override
        public void run()
        {
            final int needState;
            if (isDiscard)
                needState = SOCGame.WAITING_FOR_DISCARDS;
            else if (pi.game.isInitialPlacement())
                needState = SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE;
            else
                needState = SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE;

            final int clientPN = pi.clientHandPlayerNum;
            boolean anyShowing = false;
            SOCHandPanel hp;
            synchronized (pi.showingPlayerDiscardOrPick_lock)
            {
                if (pi.game.getGameState() != needState)
                {
                    return;  // <--- Early return: No longer relevant ---
                }
                for (int i = pi.hands.length - 1; i >=0; --i)
                {
                    if (i == clientPN)
                        continue;

                    hp = pi.hands[i];
                    if (isDiscard)
                    {
                        if (7 < hp.getPlayer().getResources().getTotal())
                            if (hp.setDiscardOrPickMsg(true))
                                anyShowing = true;
                    } else {
                        if (hp.getPlayer().getNeedToPickGoldHexResources() > 0)
                            if (hp.setDiscardOrPickMsg(false))
                                anyShowing = true;
                    }
                }

                pi.showingPlayerDiscardOrPick = anyShowing;
                pi.showingPlayerDiscardOrPick_task = null;  // No longer needed (fires once)
            }
        }

    }  // SOCPIDiscardOrPickMsgTask

    /**
     * For 6-player board, make the text displays larger/smaller when mouse
     * enters/exits them. Wait 200 ms first, to avoid flicker in case of several related
     * {@link #mouseExited(MouseEvent)}/{@link #mouseEntered(MouseEvent)} events
     * (such as moving mouse from {@link #textDisplay} to {@link #chatDisplay}).
     *<P>
     * Used only when {@link SOCPlayerInterface#is6player} true.
     *<P>
     * Delay was 100 ms in 1.1.08, increased to 200 ms in 1.1.09.
     * @author Jeremy D Monin <jeremy@nand.net>
     * @since 1.1.08
     */
    private class SOCPITextDisplaysLargerTask extends TimerTask
    {
        /**
         * Called when timer fires; see class javadoc for actions taken.
         */
        @Override
        public void run()
        {
            final boolean leftLarger =
                sbFixNeeded
                    && (sbFixLHasMouse || sbFixRHasMouse || sbFixBHasMouse);
            final boolean wantsLarger =
                (textDisplayHasMouse || chatDisplayHasMouse || textInputHasMouse)
                || (sbFixNeeded && textDisplaysLargerTemp && ! leftLarger);

            if (textDisplaysLargerTemp != wantsLarger)
            {
                textDisplaysLargerTemp = wantsLarger;
                textDisplaysLargerTemp_needsLayout = true;
                if (! wantsLarger)
                    textDisplaysLargerWhen = 0L;
                else
                    textDisplaysLargerWhen = System.currentTimeMillis();
                invalidate();
                validate();
            }
        }

    }  // SOCPITextDisplaysLargerTask

}  // SOCPlayerInterface
