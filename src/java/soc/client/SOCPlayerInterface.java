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

import soc.game.SOCCity;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.message.SOCPlayerElement;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
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
public class SOCPlayerInterface extends Frame implements ActionListener, MouseListener
{
    /**
     * System property os.name; For use by {@link #isPlatformWindows}.
     * @since 1.1.08
     */
    private final static String SOCPI_osName = System.getProperty("os.name");

    /**
     * Are we running on the Windows platform, according to {@link #osName}?
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
        = "Type here to chat.";

    /** Titlebar text for game in progress */
    public static final String TITLEBAR_GAME
        = "Settlers of Catan Game: ";

    /** Titlebar text for game when over */
    public static final String TITLEBAR_GAME_OVER
        = "Settlers of Catan Game Over: ";

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
     * the client that spawned us
     */
    protected SOCPlayerClient client;

    /**
     * the game associated with this interface
     */
    protected SOCGame game;

    /**
     * Flag to ensure interface is updated, when the first actual
     * turn begins (state changes from {@link SOCGame#START2B}
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
     * To reduce text clutter: server has just sent a dice result message.
     * If the next text message from server is the roll,
     *   replace: * It's Player's turn to roll the dice. \n * Player rolled a 4 and a 5.
     *   with:    * It's Player's turn to roll. Rolled a 9.
     *<P>
     * Set to 0 at most times.
     * Set to the roll result when roll text is expected.
     * Will be cleared to 0 in {@link #print(String)}.
     * Whenever this field is nonzero, textmessages from the server
     * will be scanned for " rolled a ".
     */
    protected int textDisplayRollExpected;
    
    /**
     * The dialog for getting what resources the player wants to discard or gain.
     */
    protected SOCDiscardOrGainResDialog discardOrGainDialog;

    /**
     * the dialog for choosing a player from which to steal
     */
    protected SOCChoosePlayerDialog choosePlayerDialog;

    /**
     * the dialog for choosing 2 resources to discover
     */
    protected SOCDiscoveryDialog discoveryDialog;

    /**
     * the dialog for choosing a resource to monopolize
     */
    protected SOCMonopolyDialog monopolyDialog;

    private SOCGameStatistics gameStats;

    /**
     * create a new player interface
     *
     * @param title  title for this interface - game name
     * @param cl     the player client that spawned us
     * @param ga     the game associated with this interface
     */
    public SOCPlayerInterface(String title, SOCPlayerClient cl, SOCGame ga)
    {
        super(TITLEBAR_GAME + title +
              (ga.isPractice ? "" : " [" + cl.getNickname() + "]"));
        setResizable(true);
        layoutNotReadyYet = true;  // will set to false at end of doLayout

        client = cl;
        game = ga;
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
         * initialize the font and the forground, and background colors
         */
        setBackground(Color.black);
        setForeground(Color.black);
        setFont(new Font("Geneva", Font.PLAIN, 10));

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
         * init is almost complete - when window appears and doLayout is called,
         * it will reset mouse cursor from WAIT_CURSOR to normal (WAIT_CURSOR is
         * set in SOCPlayerClient.startPracticeGame or .guardedActionPerform).
         */
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
        textDisplayRollExpected = 0;

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
        textInput.setText("Please wait...");
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
            updatePlayerLimitDisplay(true, -1);  // Player data may not be received yet;
                // game is created empty, then SITDOWN messages are received from server.
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
            addWindowListener(new MyWindowAdapter(this));
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
        return client;
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
        return playerColors[pn];
    }
    
    /**
     * @return a player's hand panel
     *
     * @param pn  the player's seat number
     * 
     * @see #getClientHand()
     */
    public SOCHandPanel getPlayerHandPanel(int pn)
    {
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
        return client.getEventTimer();
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
        final int updateType;
        if (isRoadNotArmy)
            updateType = SOCHandPanel.LONGESTROAD;
        else
            updateType = SOCHandPanel.LARGESTARMY;

        for (int i = 0; i < game.maxPlayers; i++)
        {
            hands[i].updateValue(updateType);
            hands[i].updateValue(SOCHandPanel.VICTORYPOINTS);
        }

        // Check for and announce change in largest army, or longest road
        if ((newp != oldp)
            && ((null != oldp) || (null != newp)))
        {
            StringBuffer msgbuf;
            if (isRoadNotArmy)
            {
                if (game.hasSeaBoard)
                    msgbuf = new StringBuffer("Longest trade route was ");
                else
                    msgbuf = new StringBuffer("Longest road was ");
            } else {
                msgbuf = new StringBuffer("Largest army was ");
            }

            if (newp != null)
            {
                if (oldp != null)
                {
                    msgbuf.append("taken from ");
                    msgbuf.append(oldp.getName());
                    msgbuf.append(" by ");
                } else {
                    msgbuf.append("taken by ");
                }
                msgbuf.append(newp.getName());
            } else {
                msgbuf.append("lost by ");
                msgbuf.append(oldp.getName());
            }

            msgbuf.append('.');
            print(msgbuf.toString());
        }
    }

    /**
     * Show the maximum and available number of player positions,
     * if game parameter "PL" is less than {@link SOCGame#maxPlayers}.
     * Also, if show, and gamestate is {@link SOCGame#NEW}, check for game-is-full,
     * and hide or show "sit down" buttons if necessary.
     * If the game has already started, and the client is playing in this game,
     * will not show this display (it overlays the board, which is in use).
     * It will still hide/show sit-here buttons if needed.
     * @param show show the text, or clear the display (at game start)?
     * @param playerLeaving The player number if a player is leaving the game, otherwise -1.
     * @since 1.1.07
     */
    private void updatePlayerLimitDisplay(final boolean show, final int playerLeaving)
    {
        final int gstate = game.getGameState();
        final boolean clientSatAlready = (clientHand != null);
        boolean noTextOverlay =  ((! show) ||
            ((gstate >= SOCGame.START1A) && clientSatAlready));
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
            String availTxt = (availPl == 1) ? "1 seat available" : Integer.toString(availPl) + " seats available";
            boardPanel.setSuperimposedText
                ("Maximum players: " + maxPl, availTxt);
        }
        if ((gstate == SOCGame.NEW) || ! clientSatAlready)
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
     * To reduce text clutter: server has just sent a dice result message.
     * If the next text message from server is the roll,
     *   replace: * It's Player's turn to roll the dice. \n * Player rolled a 4 and a 5.
     *   with:    * It's Player's turn to roll. Rolled a 9.
     *<P>
     * Set to 0 at most times.
     * Set to the roll result when roll text is expected.
     * Will be cleared to 0 in {@link #print(String)}.
     *<P>
     * Whenever this field is nonzero, textmessages from the server
     * will be scanned for " rolled a ".
     *
     * @param roll The expected roll result, or 0.
     */
    public void setTextDisplayRollExpected(int roll)
    {
        textDisplayRollExpected = roll;
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

            if (s.length() > 100)
            {
                s = s.substring(0, 100);
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
                                ("Usage: =*= show: n  or =*= hide: n   where n is all or a number 0-9");
                            return;
                        }
                    }
                    boardPanel.setDebugShowPotentialsFlag(flagnum, false, doSet);
                    return;
                }
            }
            client.getGameManager().sendText(game, s + "\n");
        }
    }

    /**
     * leave this game
     */
    public void leaveGame()
    {
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
     */
    public void resetBoardRequest()
    {
        if (client.getServerVersion(game) < 1100)
        {
            textDisplay.append("*** This server does not support board reset, server is too old.\n");
            return;
        }
        if (game.getResetVoteActive())
        {
            textDisplay.append("*** Voting is already active. Try again when voting completes.\n");
            return;
        }
        SOCPlayer pl = game.getPlayer(clientHandPlayerNum);
        if (! pl.hasAskedBoardReset())
            client.getGameManager().resetBoardRequest(game);
        else
            textDisplay.append("*** You may ask only once per turn to reset the board.\n");
    }

    /**
     * Another player has voted on a board reset request.
     * Show the vote.
     */
    public void resetBoardVoted(int pn, boolean vyes)
    {
        String voteMsg;
        if (vyes)
            voteMsg = "Go ahead.";
        else
            voteMsg = "No thanks.";
        textDisplay.append("* " + game.getPlayer(pn).getName() + " has voted: " + voteMsg + "\n");
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
        textDisplay.append("** The board reset was rejected.\n");
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
                pleaseMsg = "Restart Game?";
            else
                pleaseMsg = "Reset Board?";
            boardResetRequester.resetBoardSetMessage(pleaseMsg);

            String requester = game.getPlayer(pnRequester).getName();
            boardResetVoteDia = new ResetBoardVoteDialog(client, this, requester, gaOver);
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
     * print text in the text window, followed by a new line (<tt>'\n'</tt>).
     * For dice-roll message, combine lines to reduce clutter.
     *
     * @param s  the text; you don't need to include "\n".
     * @see #chatPrint(String)
     */
    public void print(String s)
    {
        if (textDisplayRollExpected > 0)
        {
            /*
             * Special case: Roll message.  Reduce clutter.
             * Instead of printing this message verbatim,
             * change the textDisplay contents (if matching):
             *   replace: * It's Player's turn to roll the dice. \n * Player rolled a 4 and a 5.
             *   with:    * It's Player's turn to roll. Rolled a 9.
             * 
             * JM 2009-05-21: Don't edit existing text on Mac OS X 10.5; it can lead to a GUI hang/race condition.
             *   Instead just print the total rolled.
             */

            if (s.startsWith("* ") && (s.indexOf(" rolled a ") > 0))
            {
                String currentText = textDisplay.getText();
                int L = currentText.length();
                int i = currentText.lastIndexOf("'s turn to roll the dice.");
                                                // 25 chars: length of match text
                                                //  9 chars: length of " the dice"
                if ((i > 0) && (30 > (L - i)))
                {
                    if (! SnippingTextArea.isJavaOnOSX105)
                    {
                        String rollText = ". Rolled a " + textDisplayRollExpected;
                        currentText = currentText.substring(0, i+15)
                            + rollText + currentText.substring(i+15+9);
                        textDisplay.setText(currentText);
                        //textDisplay.replaceRange(rollText, i+15, i+15+9);
                        //textDisplay.replaceRange(rollText, i+5, i+5+9);
                        //textDisplay.insert(rollText, 10); // i+5); // +15);
                    } else {
                        String rollText = "* Rolled a " + textDisplayRollExpected + ".\n";
                        textDisplay.append(rollText);
                    }
                    textDisplayRollExpected = 0;

                    return;  // <--- Early return ---
                }
            }

            textDisplayRollExpected = 0;  // Reset for next call
        }

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
        textDisplay.append("* Sorry, lost connection to the server.\n");
        textDisplay.append("*** Game stopped. ***\n");
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
                obs = new StringBuffer("* ");
            else
                obs.append(", ");
            obs.append(mname);
            ++numObservers;
        }
        if (numObservers > 0)
        {
            if (numObservers == 1)
                obs.append(" has");
            else
                obs.append(" have");
            obs.append(" joined as observer.\n");

            textDisplay.append(obs.toString());
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
            updatePlayerLimitDisplay(true, -1);

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
            updatePlayerLimitDisplay(true, pn);
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
        updatePlayerLimitDisplay(false, -1);
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
            hands[i].updateValue(SOCHandPanel.VICTORYPOINTS);  // Also disables buttons, etc.
        setTitle(TITLEBAR_GAME_OVER + game.getName() +
                 (game.isPractice ? "" : " [" + client.getNickname() + "]"));
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
     * show the choose player dialog box
     *
     * @param count   the number of players to choose from
     * @param pnums   the player ids of those players
     */
    public void choosePlayer(int count, int[] pnums)
    {
        choosePlayerDialog = new SOCChoosePlayerDialog(this, count, pnums);
        choosePlayerDialog.setVisible(true);
    }

    /**
     * show the Discovery dialog box
     */
    public void showDiscoveryDialog()
    {
        discoveryDialog = new SOCDiscoveryDialog(this);
        discoveryDialog.setVisible(true);
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
     * Update interface after game state has changed.
     * For example, if the client is current player, and state changed from PLAY to PLAY1,
     * (Dice has been rolled, or card played), enable the player's Done and Bank buttons.
     *<P>
     * Please call after {@link SOCGame#setGameState(int)}.
     * If the game is now starting, please call in this order:
     *<code>
     *   playerInterface.{@link #startGame()};
     *   game.setGameState(newState);
     *   playerInterface.updateAtGameState();
     *</code>
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
                   || (gs == SOCGame.START2A_WAITING_FOR_PICK_GOLD_RESOURCE))
        {
            // Set timer.  If still waiting for resource picks after 2 seconds,
            // show balloons on-screen. (hands[i].setDiscardOrPickMsg)
            discardOrPickTimerSet(false);
        } else if (showingPlayerDiscardOrPick &&
                   ((gs == SOCGame.PLAY1) || (gs == SOCGame.START2B)))
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
            showDiscoveryDialog();
            break;

        case SOCGame.WAITING_FOR_MONOPOLY:
            showMonopolyDialog();
            break;

        case SOCGame.WAITING_FOR_ROBBER_OR_PIRATE:
            new ChooseMoveRobberOrPirateDialog().showInNewThread();
            break;

        default:
            clientHand.updateAtOurGameState();
        }
    }

    /**
     * Handle updates after putting a piece on the board,
     * or moving a ship that was already placed.
     * Place or move the piece within our {@link SOCGame}
     * and visually on our {@link SOCBoardPanel}.
     * @since 2.0.00
     *
     * @param mesPn  The piece's player number
     * @param coord  The piece's coordinate.  If <tt>isMove</tt>, the coordinate to move <em>from</em>.
     * @param pieceType  Piece type, like {@link SOCPlayingPiece#CITY}
     * @param isMove   If true, it's a move, not a new placement; valid only for ships.
     * @param moveToCoord  If <tt>isMove</tt>, the coordinate to move <em>to</em>.  Otherwise ignored.
     */
    public void updateAtPutPiece
        (final int mesPn, final int coord, final int pieceType,
         final boolean isMove, final int moveToCoord)
    {
        // TODO consider more effic way for flushBoardLayoutAndRepaint, without the =null

        final SOCPlayer pl = game.getPlayer(mesPn);
        final SOCPlayer oldLongestRoadPlayer = game.getPlayerWithLongestRoad();
        final SOCHandPanel mesHp = getPlayerHandPanel(mesPn);
        final boolean[] debugShowPotentials = boardPanel.debugShowPotentials;
        final SOCPlayingPiece pp;

        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:
            pp = new SOCRoad(pl, coord, null);
            game.putPiece(pp);
            mesHp.updateValue(SOCPlayerElement.ROADS);
            if (debugShowPotentials[4] || debugShowPotentials[5] || debugShowPotentials[7])
                boardPanel.flushBoardLayoutAndRepaint();

            break;

        case SOCPlayingPiece.SETTLEMENT:
            pp = new SOCSettlement(pl, coord, null);
            game.putPiece(pp);
            mesHp.updateValue(SOCPlayerElement.SETTLEMENTS);

            /**
             * if this is the second initial settlement, then update the resource display
             */
            if (mesHp.isClientPlayer())
            {
                mesHp.updateValue(SOCPlayerElement.CLAY);
                mesHp.updateValue(SOCPlayerElement.ORE);
                mesHp.updateValue(SOCPlayerElement.SHEEP);
                mesHp.updateValue(SOCPlayerElement.WHEAT);
                mesHp.updateValue(SOCPlayerElement.WOOD);
            } else {
                mesHp.updateValue(SOCHandPanel.NUMRESOURCES);
            }

            if (debugShowPotentials[4] || debugShowPotentials[5] || debugShowPotentials[7]
                || debugShowPotentials[6])
                boardPanel.flushBoardLayoutAndRepaint();

            break;

        case SOCPlayingPiece.CITY:
            pp = new SOCCity(pl, coord, null);
            game.putPiece(pp);
            mesHp.updateValue(SOCPlayerElement.SETTLEMENTS);
            mesHp.updateValue(SOCPlayerElement.CITIES);

            if (debugShowPotentials[4] || debugShowPotentials[5] || debugShowPotentials[7]
                || debugShowPotentials[6])
                boardPanel.flushBoardLayoutAndRepaint();

            break;

        case SOCPlayingPiece.SHIP:
            pp = new SOCShip(pl, coord, null);
            if (! isMove)
            {
                game.putPiece(pp);
                mesHp.updateValue(SOCPlayerElement.SHIPS);
            } else {
                game.moveShip((SOCShip) pp, moveToCoord);
            }

            if (debugShowPotentials[4] || debugShowPotentials[5] || debugShowPotentials[7])
                boardPanel.flushBoardLayoutAndRepaint();

            break;

        default:
            chatPrintDebug("* Unknown piece type " + pieceType + " at coord 0x" + coord);

            return;  // <--- Early return ---
        }

        mesHp.updateValue(SOCHandPanel.VICTORYPOINTS);
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
            client.getEventTimer().schedule(showingPlayerDiscardOrPick_task, 1000 /* ms */ );
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
        final boolean[] boardDebugShow = boardPanel.debugShowPotentials.clone();
        clientHand = null;
        clientHandPlayerNum = -1;

        removeAll();  // old sub-components
        initInterfaceElements(false);  // new sub-components

        // Clear from possible TITLEBAR_GAME_OVER
        setTitle(TITLEBAR_GAME + game.getName() +
                 (game.isPractice ? "" : " [" + client.getNickname() + "]"));
        boardPanel.debugShowPotentials = boardDebugShow;
        validate();
        repaint();
        String requesterName = game.getPlayer(requesterNumber).getName();
        if (requesterName == null)
            requesterName = "player who left";
        String resetMsg;
        if (oldGameState != SOCGame.OVER)
            resetMsg = "** The board was reset by " + requesterName + ".\n";
        else
            resetMsg = "** New game started by " + requesterName + ".\n";
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
        } else {
            // standard size
            textDisplay.setBounds(i.left + hw + 8, i.top + 4, bw, tdh);
            chatDisplay.setBounds(i.left + hw + 8, i.top + 4 + tdh, bw, cdh);
            textInput.setBounds(i.left + hw + 8, i.top + 4 + tah, bw, tfh);
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
            client.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            layoutNotReadyYet = false;
            repaint();
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

    //========================================================
    /**
     * Nested classes begin here
     */
    //========================================================

    /**
     * This is the modal dialog to vote on another player's board reset request.
     * If game in progress, buttons are Reset and Continue Playing; default Continue.
     * If game is over, buttons are Restart and No thanks; default Restart.
     * Start a new thread to show, so message treating can continue as other players vote.
     *
     * @author Jeremy D Monin <jeremy@nand.net>
     */
    protected static class ResetBoardVoteDialog extends AskDialog implements Runnable
    {
        /** Runs in own thread, to not tie up client's message-treater thread. */
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
        protected ResetBoardVoteDialog(SOCPlayerClient cli, SOCPlayerInterface gamePI, String requester, boolean gameIsOver)
        {
            super(cli, gamePI, "Reset board of game "
                    + gamePI.getGame().getName() + "?",
                (gameIsOver
                    ? (requester + " wants to start a new game.")
                    : (requester + " wants to reset the game being played.")),
                (gameIsOver
                    ? "Restart"
                    : "Reset"),
                (gameIsOver
                    ? "No thanks"
                    : "Continue playing"),
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
            //rdt.stop();
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
            catch (Exception e) {
                e.printStackTrace();
            }
        }

    }  // class ResetBoardVoteDialog

    /**
     * Modal dialog to ask whether to move the robber or the pirate ship.
     * Start a new thread to show, so message treating can continue while the dialog is showing.
     * When the choice is made, calls {@link SOCPlayerClient#choosePlayer(SOCGame, int)}
     * with -1 or -2.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    private class ChooseMoveRobberOrPirateDialog extends AskDialog implements Runnable
    {
        private static final long serialVersionUID = 2000L;

        /** Runs in own thread, to not tie up client's message-treater thread. */
        private Thread rdt;

        /**
         * Creates a new ChooseMoveRobberOrPirateDialog.
         * To display the dialog, call {@link #showInNewThread()}.
         */
        protected ChooseMoveRobberOrPirateDialog()
        {
            super(getClient(), SOCPlayerInterface.this,
                "Move robber or pirate?",
                "Do you want to move the robber or the pirate ship?",
                "Move Robber",
                "Move Pirate",
                null, 1);
            rdt = null;
        }

        /**
         * React to the Move Robber button.
         * Call {@link SOCPlayerClient#choosePlayer(SOCGame, int) pcli.choosePlayer(-1)}.
         */
        @Override
        public void button1Chosen()
        {
            pcli.getGameManager().choosePlayer(game, -1);
        }

        /**
         * React to the Move Pirate button.
         * Call {@link SOCPlayerClient#choosePlayer(SOCGame, int) pcli.choosePlayer(-2)}.
         */
        @Override
        public void button2Chosen()
        {
            pcli.getGameManager().choosePlayer(game, -2);
        }

        /**
         * React to the dialog window closed by user. (Default is move the robber)
         */
        @Override
        public void windowCloseChosen() { button1Chosen(); }

        /**
         * Make a new thread and show() in that thread.
         * Keep track of the thread, in case we need to dispose of it.
         */
        public void showInNewThread()
        {
            rdt = new Thread(this);
            rdt.setDaemon(true);
            rdt.setName("ChooseMoveRobberOrPirateDialog");
            rdt.start();  // run method will show the dialog
        }

        @Override
        public void dispose()
        {
            if (rdt != null)
            {
                //FIXME: Thread#stop is unsafe, need to tell the thread to internally terminate
                //rdt.stop();
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
            catch (Exception e) {
                e.printStackTrace();
            }
        }

    }  // nested class ChooseMoveRobberOrPirateDialog

    /**
     * React to window closing or losing focus (deactivation).
     * @author jdmonin
     * @since 1.1.00
     */
    private static class MyWindowAdapter extends WindowAdapter
    {
        private SOCPlayerInterface pi;

        public MyWindowAdapter(SOCPlayerInterface spi)
        {
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
                SOCQuitConfirmDialog.createAndShow(pi.getClient(), pi);
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
                needState = SOCGame.START2A_WAITING_FOR_PICK_GOLD_RESOURCE;
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
