/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2019 Jeremy D Monin <jeremy@nand.net>
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

import soc.client.stats.SOCGameStatistics;
import soc.debug.D;  // JM

import soc.game.SOCCity;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCScenario;
import soc.game.SOCScenarioEventListener;
import soc.game.SOCScenarioGameEvent;
import soc.game.SOCScenarioPlayerEvent;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCSpecialItem;
import soc.game.SOCTradeOffer;
import soc.game.SOCVillage;
import soc.message.SOCSimpleAction;  // for action type constants
import soc.message.SOCSimpleRequest;  // for request type constants
import soc.util.SOCStringManager;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.StringTokenizer;
import java.util.Timer;  // SOCPlayerInterface also uses restartable javax.swing.Timer
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.PrintWriter;  // For chatPrintStackTrace
import java.io.StringWriter;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Window with interface for a player in one game of Settlers of Catan.
 * Contains {@link SOCBoardPanel board}, client's and other players' {@link SOCHandPanel hands},
 * chat interface, game message window, and the {@link SOCBuildingPanel building/buying panel}.
 *<P>
 * Players' {@link SOCHandPanel hands} start with player 0 at top-left, and go clockwise;
 * see {@link #doLayout()} for details. Component sizes including {@link SOCBoardPanel}
 * are recalculated by {@link #doLayout()} when the frame is resized.
 *<P>
 * When we join a game, the client will update visible game state by calling methods here like
 * {@link #addPlayer(String, int)}; when all this activity is complete, and the interface is
 * ready for interaction, the client calls {@link #began(List)}.
 *<P>
 * <B>Local preferences:</B>
 * For optional per-game preferences like {@link #PREF_SOUND_MUTE}, see {@code localPrefs} parameter in
 * the {@link #SOCPlayerInterface(String, MainDisplay, SOCGame, Map)} constructor javadoc.
 * The current game's prefs are shown and changed with {@link NewGameOptionsFrame}.
 * Local prefs are not saved persistently like client preferences
 * ({@link SOCPlayerClient#PREF_SOUND_ON} etc) are.
 *<P>
 * A separate {@link SOCPlayerClient} window holds the list of current games and channels.
 *<P>
 * Some requests, actions, and features need different behavior depending on the
 * server version we're connected to: check {@link SOCPlayerClient#getServerVersion(SOCGame)}
 * if needed.
 *
 * @author Robert S. Thomas
 */
@SuppressWarnings("serial")
public class SOCPlayerInterface extends Frame
    implements ActionListener, MouseListener, SOCScenarioEventListener,
    PlayerClientListener.NonBlockingDialogDismissListener
{
    /**
     * Boolean per-game preference to mute all sound effects in this game.
     * For use with constructor's {@code localPrefs} parameter. Default value is {@code false}.
     * @see #isSoundMuted()
     * @see SOCPlayerClient#PREF_SOUND_ON
     * @since 1.2.00
     */
    public static final String PREF_SOUND_MUTE = "soundMute";

    /**
     * Basic minimum frame width for a 4-player game.
     * Used for {@link #width_base} and user preference {@link SOCPlayerClient#PREF_PI__WIDTH}.
     * @since 1.2.00
     */
    private static final int WIDTH_MIN_4PL = 830;

    /**
     * Basic minimum frame height for a 4-player game.
     * Used for {@link #height_base} and user preference {@link SOCPlayerClient#PREF_PI__HEIGHT}.
     * @since 1.2.00
     */
    private static final int HEIGHT_MIN_4PL = 650;

    /** i18n text strings */
    private static final SOCStringManager strings = SOCStringManager.getClientManager();

    /**
     * Minimum frame width calculated in constructor from this game's player count and board,
     * based on {@link #WIDTH_MIN_4PL} and {@link #displayScale}.
     * Updated only in {@link #updateAtNewBoard()} if board layout part
     * Visual Shift ("VS") increases {@link #boardPanel}'s size.
     * @since 1.2.00
     */
    private int width_base;

    /**
     * Minimum frame height calculated in constructor from this game's player count and board,
     * based on {@link #HEIGHT_MIN_4PL} and {@link #displayScale}.
     * Updated only in {@link #updateAtNewBoard()} if board layout part
     * Visual Shift ("VS") increases {@link #boardPanel}'s size.
     * @since 1.2.00
     */
    private int height_base;

    /**
     * For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @since 2.0.00
     */
    /*package*/ final int displayScale;

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
     * To avoid sound-effect spam while receiving board layout info
     * when starting a game or joining a game in progress, track whether
     * {@link #began(List)} has been called.
     *<P>
     * Reminder: When starting a new game, some PUTPIECE messages
     * may be sent after {@code began(..)}, but these will all be sent
     * while game state is &lt; {@link SOCGame#START1A}.
     * @since 1.2.00
     */
    private boolean hasCalledBegan;

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
    private JTextField textInput;

    /**
     * Not yet typed-in; display prompt message.
     *
     * @see #textInput
     * @see #TEXTINPUT_INITIAL_PROMPT_MSG
     * @since 1.1.00
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
     * @since 1.1.00
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
     * @since 1.1.00
     */
    public static final String TEXTINPUT_INITIAL_PROMPT_MSG
        = strings.get("interface.type.here.chat");  // "Type here to chat."

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
     * @see #clientHand
     */
    protected SOCHandPanel[] hands;

    /**
     * Tracks our own hand within {@link #hands hands[]}, if we are
     * active in a game.  Null otherwise.
     * Set by {@link SOCHandPanel}'s removePlayer() and addPlayer() methods
     * by calling {@link #setClientHand(SOCHandPanel)}.
     * @see #clientHandPlayerNum
     * @see #clientIsCurrentPlayer()
     * @see #bankTradeWasFromTradePanel
     * @since 1.1.00
     */
    protected SOCHandPanel clientHand;

    /**
     * Player ID of {@link #clientHand}, or -1.
     * Set by {@link SOCHandPanel}'s removePlayer() and addPlayer() methods
     * by calling {@link #setClientHand(SOCHandPanel)}.
     * @see #clientIsCurrentPlayer()
     * @since 1.1.00
     */
    private int clientHandPlayerNum;  // the field for this in some other packages is called ourPN or ourPlayerNumber

    /**
     * If true, client player's most recent bank/port trade request was sent from their handpanel's
     * Trade Offer panel. So if trade is approved by server, should clear that panel's values to 0.
     * If false, that request was sent from another interface element.
     * @since 2.0.00
     */
    boolean bankTradeWasFromTradePanel;  // package-level access for SOCHandPanel

    /**
     * the player colors. Indexes from 0 to {@link SOCGame#maxPlayers} - 1.
     * Initialized in constructor.
     * @see #getPlayerColor(int, boolean)
     */
    protected Color[] playerColors, playerColorsGhost;

    /**
     * the client main display that spawned us
     */
    protected MainDisplay mainDisplay;

    protected SOCPlayerClient client;

    /**
     * the game associated with this interface
     */
    protected SOCGame game;

    /**
     * If true, {@link #updateAtGameState()} has been called at least once,
     * or the constructor was called with a non-zero {@link SOCGame#getGameState()}.
     * If false, the 'known' game state (from the constructor) is 0.
     * @since 1.2.00
     */
    private boolean knowsGameState;

    /**
     * Flag to ensure interface will be updated later when the first actual turn
     * begins (state changes from {@link SOCGame#START2B} or {@link SOCGame#START3B}
     * to {@link SOCGame#ROLL_OR_CARD}). Initially set in {@link #startGame()} while leaving
     * state {@link SOCGame#NEW}. Checked/cleared in {@link #updateAtGameState()}.
     */
    protected boolean gameIsStarting;

    /**
     * Flag to set true if game has been deleted while we're observing it,
     * or was stopped by a server or network error. Is set in {@link #over(boolean, String)}.
     * @since 1.2.01
     */
    protected boolean gameHasErrorOrDeletion;

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
     * For frame resize, a restarting timer to call {@link #frameResizeDone()}
     * once instead of repeatedly as the user drags the frame edges.
     * Usually {@code null} when not in the middle of resizing.
     *<P>
     * This field is only read and set/cleared from AWT/swing threads,
     * but is volatile just in case.
     * @since 1.2.00
     */
    private volatile javax.swing.Timer frameResizeDoneTimer;

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
     * True if sound effects in this particular game interface are muted.
     * For more details see {@link #isSoundMuted()}.
     * @since 1.2.00
     */
    private boolean soundMuted;

    /**
     * Starting value of the countdown to auto-reject bot trades.
     * For more details see {@link #getBotTradeRejectSec()}.
     *<P>
     * Default value -8 is negative because the countdown is disabled by default.
     * Another value can be specified in our constructor's {@code localPrefs} param.
     * @since 1.2.00
     */
    private int botTradeRejectSec = UserPreferences.getPref
        (SOCPlayerClient.PREF_BOT_TRADE_REJECT_SEC, -8);

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

    /**
     * Sound prompt at start of player's turn (roll or play card).
     * Generated at first call to constructor.
     * @since 1.2.00
     */
    private static byte[] SOUND_BEGIN_TURN;

    /**
     * Sound made when a piece is placed.
     * Generated at first call to constructor.
     * @since 1.2.00
     */
    private static byte[] SOUND_PUT_PIECE;

    /**
     * Alert chime for when resources have been stolen or player must choose which ones to discard.
     * Generated at first call to constructor.
     * @since 1.2.00
     */
    static byte[] SOUND_RSRC_LOST;

    /**
     * Alert chime for when free resources are gained from a gold hex or a scenario-specific event.
     * Generated at first call to constructor.
     * @since 2.0.00
     */
    static byte[] SOUND_RSRC_GAINED_FREE;

    /**
     * Sound prompt when trade is offered to client player.
     * Generated at first call to constructor.
     * @since 1.2.01
     */
    static byte[] SOUND_OFFERED_TRADE;

    private final ClientBridge clientListener;

    /**
     * Thread executor to queue and play {@link #playSound(byte[])} using {@link PIPlaySound}s.
     * @since 1.2.00
     */
    private static final ExecutorService soundQueueThreader = Executors.newSingleThreadExecutor();

    /**
     * Listener for
     * {@link ClientBridge#setNonBlockingDialogDismissListener(soc.client.PlayerClientListener.NonBlockingDialogDismissListener)},
     * or null. Such dialogs are tracked with {@link #nbdForEvent}.
     * @since 2.0.00
     */
    private PlayerClientListener.NonBlockingDialogDismissListener nbddListener;  // TODO doesn't fire yet

    /**
     * Non-blocking dialog created for scenario or game event, for {@link #isNonBlockingDialogVisible()}.
     *<P>
     * Currently assumes only one field is needed to track such dialogs,
     * because no current scenario has more than one type of event which would
     * need concurrent tracking.
     * @since 2.0.00
     */
    volatile Dialog nbdForEvent;

    /**
     * Create and show a new player interface.
     * If the game options have a {@link SOCScenario} description, it will be shown now in a popup
     * by {@link #showScenarioInfoDialog()}.
     *
     * @param title  title for this interface - game name
     * @param md     the client main display that spawned us
     * @param ga     the game associated with this interface; must not be {@code null}
     * @param localPrefs  optional map of per-game local preferences to use in this {@code SOCPlayerInterface},
     *     or {@code null}. Preference name keys are {@link #PREF_SOUND_MUTE}, etc.
     *     Values for boolean prefs should be {@link Boolean#TRUE} or {@code .FALSE}.
     *     If provided in the Map, value for {@link SOCPlayerClient#PREF_BOT_TRADE_REJECT_SEC}
     *     (positive or negative) is used to call {@link #setBotTradeRejectSec(int)}.
     * @throws IllegalArgumentException if a {@code localPrefs} value isn't the expected type
     *     ({@link Integer} or {@link Boolean}) based on its key's javadoc.
     */
    public SOCPlayerInterface(String title, MainDisplay md, SOCGame ga, final Map<String, Object> localPrefs)
        throws IllegalArgumentException
    {
        super(strings.get("interface.title.game", title)
              + (ga.isPractice ? "" : " [" + md.getClient().getNickname() + "]"));
            // "Settlers of Catan Game: {0}"

        layoutNotReadyYet = true;  // will set to false at end of doLayout
        setResizable(true);
        setLocationByPlatform(true);  // cascade, not all same hard-coded position as in v1.1.xx

        this.mainDisplay = md;
        displayScale = md.getDisplayScaleFactor();
        client = md.getClient();
        game = ga;
        game.setScenarioEventListener(this);
        knowsGameState = (game.getGameState() != 0);
        clientListener = new ClientBridge(this);
        gameStats = new SOCGameStatistics(game);
        gameIsStarting = false;
        clientHand = null;
        clientHandPlayerNum = -1;
        is6player = (game.maxPlayers > 4);

        if (localPrefs != null)
        {
            soundMuted = Boolean.TRUE.equals(localPrefs.get(PREF_SOUND_MUTE));

            String k = SOCPlayerClient.PREF_BOT_TRADE_REJECT_SEC;
            Object v = localPrefs.get(k);
            if (v != null)
            {
                if (v instanceof Integer)
                    botTradeRejectSec = ((Integer) v).intValue();
                else
                    throw new IllegalArgumentException("value not Integer: " + k);
            }
        }
        // else, soundMuted = false, botTradeRejectSec = its default (see declaration/initializer)

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
            playerColors[4] = new Color(97, 151, 113); // almost same green as SwingMainDisplay.JSETTLERS_BG_GREEN #61AF71
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
        if (! SwingMainDisplay.isOSColorHighContrast())
        {
            setBackground(Color.BLACK);
            setForeground(Color.WHITE);
        }
        setFont(new Font("SansSerif", Font.PLAIN, 10 * displayScale));

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
        final Dimension boardExtraSize = boardPanel.getExtraSizeFromBoard();

        int piHeight = HEIGHT_MIN_4PL;
        if ((is6player || game.hasSeaBoard) && SOCPlayerClient.IS_PLATFORM_WINDOWS)
            piHeight += 25;
        piHeight = (piHeight + boardExtraSize.height) * displayScale;
        height_base = piHeight;

        int piWidth = WIDTH_MIN_4PL;
        piWidth = (piWidth + boardExtraSize.width) * displayScale;
        width_base = piWidth;

        // check window frame size preference if set
        {
            int prefWidth = UserPreferences.getPref(SOCPlayerClient.PREF_PI__WIDTH, -1);
            int prefHeight = (prefWidth != -1)
                ? UserPreferences.getPref(SOCPlayerClient.PREF_PI__HEIGHT, HEIGHT_MIN_4PL)
                : 0;
            if (prefWidth != -1)
            {
                if ((width_base != (WIDTH_MIN_4PL * displayScale))
                    || (height_base != (HEIGHT_MIN_4PL * displayScale)))
                {
                    // pref size is based on minimum board size, but this game's board is bigger
                    piWidth = (prefWidth * width_base) / (WIDTH_MIN_4PL * displayScale);
                    piHeight = (prefHeight * height_base) / (HEIGHT_MIN_4PL * displayScale);
                } else {
                    piWidth = prefWidth;
                    piHeight = prefHeight;
                }

                // Check vs max size for current screen
                try
                {
                    final DisplayMode mode = getGraphicsConfiguration().getDevice().getDisplayMode();
                    final int scWidth = mode.getWidth(), scHeight = mode.getHeight();
                    if (piWidth > scWidth)
                    {
                        if (piHeight > scHeight)
                        {
                            // Might be from resolution change: Keep ratio
                            final float scrnRatio = scWidth / scHeight, piRatio = piWidth / piHeight;
                            if (scrnRatio < piRatio)
                            {
                                // frame is wide, not tall: maximize width
                                piWidth = scWidth - 20;
                                piHeight = (int) (piWidth / piRatio);
                            } else {
                                // maximize height
                                piHeight = scHeight - 20;
                                piWidth = (int) (piHeight * piRatio);
                            }
                        } else {
                            // height is ok
                            piWidth = scWidth - 20;
                        }
                    }
                    else if (piHeight > scHeight)
                    {
                        // width is ok
                        piHeight = scHeight - 20;
                    }
                } catch (NullPointerException e) {}

            }
        }

        setSize(piWidth, piHeight);
        validate();

        if (didHideTemp)
        {
            setVisible(true);
        }
        repaint();

        addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(final ComponentEvent e)
            {
                if (layoutNotReadyYet || (e.getComponent() != SOCPlayerInterface.this) || ! isVisible())
                    return;

                // use restartable timer in case of rapid fire during resize
                javax.swing.Timer t = frameResizeDoneTimer;
                if (t != null)
                {
                    t.restart();
                } else {
                    t = new javax.swing.Timer(300, new ActionListener()
                    {
                        public void actionPerformed(ActionEvent ae)
                        {
                            frameResizeDoneTimer = null;
                            frameResizeDone();
                        }
                    });
                    t.setRepeats(false);
                    frameResizeDoneTimer = t;
                    t.start();
                }
            }
        });

        if (SOUND_BEGIN_TURN == null)
            soundQueueThreader.submit(new Runnable()
            {
                public void run()
                {
                    SOUND_BEGIN_TURN = Sounds.genChime(Sounds.NOTE_A5_HZ, 160, .38);

                    byte[] buf = new byte[Sounds.bufferLen(60)];
                    Sounds.genChime(140, 60, .15, buf, 0, false);
                    Sounds.genChime(160, 50, .15, buf, 0, true);
                    Sounds.genChime(240, 30, .2, buf, 0, true);
                    SOUND_PUT_PIECE = buf;

                    buf = new byte[Sounds.bufferLen(120 + 90)];
                    int i = Sounds.genChime(Sounds.NOTE_E4_HZ, 120, .9, buf, 0, false);
                    Sounds.genChime(Sounds.NOTE_C4_HZ, 90, .9, buf, i, false);
                    SOUND_RSRC_LOST = buf;

                    buf = new byte[Sounds.bufferLen(120 + 90)];
                    i = Sounds.genChime(Sounds.NOTE_C4_HZ, 120, .9, buf, 0, false);
                    Sounds.genChime(Sounds.NOTE_E4_HZ, 90, .9, buf, i, false);
                    SOUND_RSRC_GAINED_FREE = buf;

                    buf = new byte[Sounds.bufferLen(120 + 120)];
                    i = Sounds.genChime(Sounds.NOTE_B5_HZ, 120, .4, buf, 0, false);
                    Sounds.genChime(Sounds.NOTE_B5_HZ, 120, .4, buf, i, false);
                    SOUND_OFFERED_TRADE = buf;
                }
            });

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
        final boolean isOSHighContrast = SwingMainDisplay.isOSColorHighContrast();

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

        final Font sans10Font = new Font("SansSerif", Font.PLAIN, 10 * displayScale);

        textDisplay = new SnippingTextArea("", 40, 80, TextArea.SCROLLBARS_VERTICAL_ONLY, 80);
        textDisplay.setFont(sans10Font);
        if (! isOSHighContrast)
        {
            textDisplay.setBackground(SwingMainDisplay.DIALOG_BG_GOLDENROD);
            textDisplay.setForeground(Color.BLACK);
        }
        textDisplay.setEditable(false);
        add(textDisplay);
        if (is6player)
            textDisplay.addMouseListener(this);

        chatDisplay = new SnippingTextArea("", 40, 80, TextArea.SCROLLBARS_VERTICAL_ONLY, 100);
        chatDisplay.setFont(sans10Font);
        if (! isOSHighContrast)
        {
            chatDisplay.setBackground(SwingMainDisplay.DIALOG_BG_GOLDENROD);
            chatDisplay.setForeground(Color.BLACK);
        }
        chatDisplay.setEditable(false);
        if (is6player)
            chatDisplay.addMouseListener(this);
        add(chatDisplay);

        textInput = new JTextField();
        if (SOCPlayerClient.IS_PLATFORM_MAC_OSX)
        {
            int px = displayScale;  // based on 1-pixel border
            textInput.setBorder(new EmptyBorder(px, px, px, px));  // avoid black background inside overly-thick border
        }
        textInput.setFont(sans10Font);
        textInputListener = new SOCPITextfieldListener(this);
        textInputHasSent = false;
        textInputGreyCountdown = textInputGreyCountFrom;
        textInput.addKeyListener(textInputListener);
        textInput.getDocument().addDocumentListener(textInputListener);
        textInput.addFocusListener(textInputListener);

        FontMetrics fm = this.getFontMetrics(textInput.getFont());
        textInput.setSize(SOCBoardPanel.PANELX, fm.getHeight() + 4 * displayScale);
        textInput.setEditable(false);
        if (! isOSHighContrast)
        {
            textInput.setBackground(Color.WHITE);  // before v1.1.00 was new Color(255, 230, 162) aka DIALOG_BG_GOLDENROD
            textInput.setForeground(Color.BLACK);
        } else {
            final Color[] sysColors = SwingMainDisplay.getForegroundBackgroundColors(false, true);
            textInput.setBackground(sysColors[2]);
            textInput.setForeground(sysColors[0]);
        }
        textInputIsInitial = false;  // due to "please wait"
        textInput.setText(strings.get("base.please.wait"));  // "Please wait..."
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
            hp.setSize(180 * displayScale, 120 * displayScale);
            add(hp);
            ColorSquare blank = hp.getBlankStandIn();
            blank.setSize(hp.getSize());
            add(blank);
        }

        /**
         * initialize the building interface and add it to the main interface
         */
        buildingPanel = new SOCBuildingPanel(this);
        buildingPanel.setSize(200 * displayScale, SOCBuildingPanel.MINHEIGHT * displayScale);
        add(buildingPanel);

        /**
         * initialize the game board display and add it to the interface
         */
        boardPanel = new SOCBoardPanel(this);
        boardPanel.setBackground(new Color(63, 86, 139));  // sea blue; briefly visible at start before water tiles are drawn
        boardPanel.setForeground(Color.black);
        boardPanel.setFont(sans10Font);
        Dimension bpMinSz = boardPanel.getMinimumSize();
        boardPanel.setSize(bpMinSz.width * displayScale, bpMinSz.height * displayScale);
        boardIsScaled = (displayScale != 1);
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
            if (SOCPlayerClient.IS_PLATFORM_WINDOWS)
            {
                sbFixNeeded = true;
                hands[0].addMouseListener(this);  // upper-left
                hands[1].addMouseListener(this);  // upper-right
                boardPanel.addMouseListener(this);
                // Note not just on firstCall,
                // because hands[] is initialized above.
            }
        }

        if (firstCall)
        {
            // If player requests window close, ask if they're sure, leave game if so
            addWindowListener(new PIWindowAdapter(mainDisplay, this));
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
        int bw = 4 * displayScale;
        g.clearRect(boardPanel.getX(), boardPanel.getY() - bw, boardPanel.getWidth(), bw);

        needRepaintBorders = false;
    }

    /**
     * Paint the borders of one column of handpanels.
     * {@link #prevSize} must be set before calling.
     * @param g  Graphics as passed to <tt>update()</tt>
     * @param middlePanel  The middle (6-player) or the bottom (4-player) handpanel in this column
     * @since 1.1.11
     */
    private final void paintBordersHandColumn(Graphics g, SOCHandPanel middlePanel)
    {
        if (middlePanel == null)
            return;  // if called during board reset

        final int w = middlePanel.getWidth();  // handpanel's width
        final int bw = 4 * displayScale;  // border width

        // left side, entire height
        int x = middlePanel.getX();
        g.clearRect(x - bw, 0, bw, prevSize.height);

        // right side, entire height
        x += w;
        g.clearRect(x, 0, bw, prevSize.height);

        // above middle panel
        x = middlePanel.getX();
        int y = middlePanel.getY();
        g.clearRect(x, y - bw, w, bw);

        // below middle panel
        y += middlePanel.getHeight();
        g.clearRect(x, y, w, bw);
    }

    /**
     * @return the client that spawned us
     */
    public SOCPlayerClient getClient()
    {
        return mainDisplay.getClient();
    }

    /**
     * @return the client's main display associated with this interface
     * @since 2.0.00
     */
    public MainDisplay getMainDisplay()
    {
        return mainDisplay;
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
     * True if sound effects in this particular game interface are muted.
     * Checked by {@link #playSound(byte[])}. Changed with {@link #setSoundMuted(boolean)}.
     * Default value is {@code false}.
     * @return True if muted
     * @see #PREF_SOUND_MUTE
     * @see SOCPlayerClient#PREF_SOUND_ON
     * @since 1.2.00
     */
    public boolean isSoundMuted()
    {
        return soundMuted;
    }

    /**
     * Set or clear the {@link #isSoundMuted()} flag.
     * @param mute  True to set, false to clear
     * @since 1.2.00
     */
    public void setSoundMuted(boolean mute)
    {
        soundMuted = mute;
    }

    /**
     * Get this game interface's current setting for the starting value of the countdown to auto-reject bot trades,
     * from {@link SOCPlayerClient#PREF_BOT_TRADE_REJECT_SEC}.
     * If &gt; 0, {@link TradeOfferPanel} will start the countdown when any robot offers a trade.
     * Negative values or 0 turn off the auto-reject countdown feature, to keep the setting's
     * value for its "Options" dialog without also having a separate enabled/disabled flag.
     * @return This game interface's current setting, positive or negative, in seconds
     * @since 1.2.00
     */
    public int getBotTradeRejectSec()
    {
        return botTradeRejectSec;
    }

    /**
     * Set this game interface's current setting for the starting value of the countdown to auto-reject bot trades,
     * See {@link #getBotTradeRejectSec()} for details.
     * Does not update value of persistent preference {@link SOCPlayerClient#PREF_BOT_TRADE_REJECT_SEC}.
     * @param sec  New value, positive or negative, in seconds
     * @since 1.2.00
     */
    public void setBotTradeRejectSec(final int sec)
    {
        botTradeRejectSec = sec;
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
     * Get the timer for time-driven events in the interface.
     * Same timer as {@link MainDisplay#getEventTimer()}.
     *
     * @see SOCHandPanel#autoRollSetupTimer()
     * @see SOCBoardPanel#popupSetBuildRequest(int, int)
     */
    public Timer getEventTimer()
    {
        return mainDisplay.getEventTimer();
    }

    /**
     * Callback for saving PI size preference when user is done resizing this window.
     * Uses current size, with scaling factor for 6-player and sea board games.
     *<P>
     * Call only if {@link #isVisible()} and ! {@link #layoutNotReadyYet}.
     * @since 1.2.00
     */
    private void frameResizeDone()
    {
        final Dimension siz = getSize();
        int w = siz.width, h = siz.height;

        Dimension boardExtraSize = boardPanel.getExtraSizeFromBoard();
        w -= boardExtraSize.width;
        h -= boardExtraSize.height;

        if ((w < 100) || (h < 100))
            return;  // sanity check

        UserPreferences.putPref(SOCPlayerClient.PREF_PI__WIDTH, w);
        UserPreferences.putPref(SOCPlayerClient.PREF_PI__HEIGHT, h);
    }

    /**
     * The game's count of development cards remaining has changed.
     * Update the display.
     *<P>
     * See also {@link ClientBridge#simpleAction(int, int, int, int)} with {@link SOCSimpleAction#DEVCARD_BOUGHT}.
     * @since 1.1.00
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
            final String changedObj;  // what was changed?  Key prefix will be combined below with .taken/.first/.lost
            if (isRoadNotArmy)
            {
                if (game.hasSeaBoard)
                    changedObj = "game.route.longest";  // "Longest trade route"
                else
                    changedObj = "game.road.longest";  // "Longest road"
            } else {
                changedObj = "game.army.largest";  // "Largest army"
            }

            final String msg;  // full sentence with change and players
            if (newp != null)
            {
                if (oldp != null)
                {
                    msg = strings.get(changedObj + ".taken", oldp.getName(), newp.getName());
                        // "___ was taken from {0} by {1}."
                } else {
                    msg = strings.get(changedObj + ".first", newp.getName());
                        // "___ was taken by {0}."
                }
            } else {
                msg = strings.get(changedObj + ".lost", oldp.getName());
                    // "___ was lost by {0}."
            }

            print("* " + msg);
        }
    }

    /**
     * Show the maximum and available number of player positions,
     * if game parameter "PL" is less than {@link SOCGame#maxPlayers}.
     * Also, if {@code show} and {@code isGameStart}, check for game-is-full
     * and hide or show "sit down" buttons if necessary.
     *<P>
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
        boolean noTextOverlay = ((! show) || isGameStart
            || (clientSatAlready && ((gstate >= SOCGame.READY) || game.isBoardReset())));
        if (gstate == SOCGame.NEW)
            isGameStart = true;  // change this param only after setting noTextOverlay

        final int maxPl = game.getGameOptionIntValue("PL");
        if (maxPl == game.maxPlayers)
            noTextOverlay = true;

        int availPl = game.getAvailableSeatCount();
        if (playerLeaving != -1)
            ++availPl;  // Not yet vacant in game data

        if (noTextOverlay)
            boardPanel.setSuperimposedText(null, null);
        else
            boardPanel.setSuperimposedText
                (strings.get("interface.max.players", maxPl),     // "Maximum players: {0}"
                 strings.get("interface.seats.avail", availPl));  // ChoiceFormat: "1 seat available" / "{0} seats available"

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
     * Switch the game's {@link SOCGame#debugFreePlacement Debug Free Placement Mode}
     * on or off, as directed by the server.
     * @param setOn  Should the mode be turned on?
     * @see #setDebugFreePlacementPlayer(int)
     * @since 1.1.12
     */
    void setDebugFreePlacementMode(final boolean setOn)
    {
        try
        {
            if (! setOn)
                // reset the current player indicator: must call before turning off in game
                setDebugFreePlacementPlayer(clientHandPlayerNum);

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
     * {@link #setDebugFreePlacementMode(boolean) setDebugFreePlacementMode(false)}
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
     * @see #isClientPlayer(SOCPlayer)
     * @since 1.1.00
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
     * @since 1.1.00
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
     * @see #isClientPlayer(SOCPlayer)
     * @since 1.1.00
     */
    public final boolean clientIsCurrentPlayer()
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
     * @since 1.1.00
     */
    public final int getClientPlayerNumber()
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
            print(strings.get("game.roll.rolled.number", roll));  // "* Rolled a {0}."

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
                    textInput.getDocument().removeDocumentListener(textInputListener);
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

                else if (sLower.indexOf("showcoord") == 4)
                {
                    boardPanel.setDebugShowCoordsFlag(true);
                    return;
                }
                else if (sLower.indexOf("hidecoord") == 4)
                {
                    boardPanel.setDebugShowCoordsFlag(false);
                    return;
                }
            }

            final String msg = s + '\n';
            if (! doLocalCommand(msg))
                client.getGameMessageMaker().sendText(game, msg);

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
     * Before 2.0.00 this method was {@code SOCPlayerClient.doLocalCommand(SOCGame, String)}.
     *
     * @param cmd  Local client command string, which starts with \
     * @return true if a command was handled
     * @since 2.0.00
     * @see SwingMainDisplay#doLocalCommand(String, String)
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
            mainDisplay.printIgnoreList(this);

            return true;
        }
        else if (cmd.startsWith("\\unignore "))
        {
            String name = cmd.substring(10);
            client.removeFromIgnoreList(name);
            print("* Unignoring " + name);
            mainDisplay.printIgnoreList(this);

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
        mainDisplay.leaveGame(game);
        client.getGameMessageMaker().leaveGame(game);
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
     * @since 1.1.00
     */
    public void resetBoardRequest(final boolean confirmDialogFirst)
    {
        if (confirmDialogFirst)
        {
            EventQueue.invokeLater(new ResetBoardConfirmDialog(mainDisplay, this));
            return;
            // ResetBoardConfirmDialog will call resetBoardRequest(false) if its Restart button is clicked
        }

        if (client.getServerVersion(game) < 1100)
        {
            textDisplay.append("*** " + strings.get("reset.server.support.too.old") + "\n");
                // "This server does not support board reset, server is too old."
            return;
        }
        if (game.getResetVoteActive())
        {
            textDisplay.append("*** " + strings.get("reset.voting.already.active") + "\n");
                // "Voting is already active. Try again when voting completes."
            return;
        }
        SOCPlayer pl = game.getPlayer(clientHandPlayerNum);
        if (! pl.hasAskedBoardReset())
            client.getGameMessageMaker().resetBoardRequest(game);
        else
            textDisplay.append("*** " + strings.get("reset.you.may.ask.once") + "\n");
                // "You may ask only once per turn to reset the board."
    }

    /**
     * Another player has voted on a board reset request.
     * Show the vote.
     */
    public void resetBoardVoted(int pn, boolean vyes)
    {
        String voteMsg;
        if (vyes)
            voteMsg = strings.get("reset.go.ahead");   // "Go ahead."
        else
            voteMsg = strings.get("base.no.thanks.sentenc");  // "No thanks."
        printKeyed("reset.x.has.voted", game.getPlayer(pn).getName(), voteMsg);  // "* " + "{0} has voted: {1}"
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
        textDisplay.append("*** " + strings.get("reset.was.rejected") + "\n");  // "The board reset was rejected."
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
                pleaseMsg = strings.get("reset.restart.game");  // "Restart Game?"
            else
                pleaseMsg = strings.get("reset.board");   // "Reset Board?"
            boardResetRequester.resetBoardSetMessage(pleaseMsg);

            String requester = game.getPlayer(pnRequester).getName();
            boardResetVoteDia = new ResetBoardVoteDialog(mainDisplay, this, requester, gaOver);
            EventQueue.invokeLater(boardResetVoteDia);
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
     * Print game text to announce either a bank/port trade, a player's new trade offer,
     * or an accepted and completed trade offer between two players.
     *<P>
     * For a bank/port trade, also enables client player's Undo Trade button.
     *
     * @param plFrom  Player making the trade offer or the bank/port/player trade
     * @param give  {@code plFrom} gives these resources
     * @param get   {@code plFrom} gets these resources
     * @param isOffer  True to announce a trade offer, false for a completed bank/port/player trade
     * @param plTo    For a completed trade offer between players, the player accepting the offer.
     *     {@code null} otherwise. Ignored if {@code isOffer}.
     * @see SOCHandPanel#updateCurrentOffer(boolean, boolean)
     * @since 2.0.00
     */
    public void printTradeResources
        (final SOCPlayer plFrom, final SOCResourceSet give, final SOCResourceSet get,
         final boolean isOffer, final SOCPlayer plTo)
    {
        final String plName = plFrom.getName();

        if (isOffer)
        {
            if (client.getServerVersion(game) >= SOCStringManager.VERSION_FOR_I18N)
                printKeyedSpecial("trade.offered.rsrcs.for", plName, give, get);
                    // "{0} offered to give {1,rsrcs} for {2,rsrcs}."
        }
        else if (plTo != null)
        {
            if (client.getServerVersion(game) >= SOCStringManager.VERSION_FOR_I18N)
                printKeyedSpecial("trade.gave.rsrcs.for.from.player", plName, give, get, plTo.getName());
                    // "{0} gave {1,rsrcs} for {2,rsrcs} from {3}."
        }
        else
        {
            // use total rsrc counts to determine bank or port
            final int giveTotal = give.getTotal(),
                      getTotal  = get.getTotal();
            final String msgKey;
            final int tradeFrom;  // 1 = "the bank" -- 4:1 trade; 2 = "a port" -- 3:1 or 2:1 trade
            if (giveTotal > getTotal)
            {
                msgKey = "trade.traded.rsrcs.for.from.bankport";  // "{0} traded {1,rsrcs} for {2,rsrcs} from {3,choice, 1#the bank|2#a port}."
                tradeFrom = ((giveTotal / getTotal) == 4) ? 1 : 2;
            } else {
                msgKey = "trade.traded.rsrcs.for.from.bankport.undoprevious";  // same + " (Undo previous trade)"
                tradeFrom = ((getTotal / giveTotal) == 4) ? 1 : 2;
            }

            printKeyedSpecial(msgKey, plName, give, get, tradeFrom);

            if (clientHand != null)
                clientHand.enableBankUndoButton();
        }
    }

    /**
     * Get and print a localized string (having no parameters) in the text window, followed by a new line (<tt>'\n'</tt>).
     * Equivalent to {@link #print(String) print}("* " + {@link SOCStringManager#get(String) strings.get}({@code key})).
     * @param key  Key to use for string retrieval
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @since 2.0.00
     */
    public void printKeyed(final String key)
        throws MissingResourceException
    {
        textDisplay.append("* " + strings.get(key) + "\n");  // TextArea will soft-wrap within the line
    }

    /**
     * Get and print a localized string (with parameters) in the text window, followed by a new line (<tt>'\n'</tt>).
     * Equivalent to {@link #print(String) print}
     * ("* " + {@link SOCStringManager#get(String, Object...) strings.get}({@code key, params})).
     * @param key  Key to use for string retrieval
     * @param params  Objects to use with <tt>{0}</tt>, <tt>{1}</tt>, etc in the localized string by
     *                calling {@code SOCStringManager.get(key, params...)}. The localized string should not
     *                contain the leading <tt>"* "</tt> or the ending <tt>\n</tt>, those are added here.
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @since 2.0.00
     */
    public void printKeyed(final String key, final Object ... params)
        throws MissingResourceException
    {
        textDisplay.append("* " + strings.get(key, params) + "\n");  // TextArea will soft-wrap within the line
    }

    /**
     * Get and print a localized string (with special SoC-specific parameters) in the text window,
     * followed by a new line (<tt>'\n'</tt>). Equivalent to {@link #print(String) print}("* " +
     * {@link SOCStringManager#getSpecial(String, String, Object...) strings.getSpecial}({@code game, key, params})).
     * @param key  Key to use for string retrieval
     * @param params  Objects to use with <tt>{0}</tt>, <tt>{1}</tt>, etc in the localized string by
     *                calling {@code SOCStringManager.getSpecial(game, key, params...)}. The localized string should not
     *                contain the leading <tt>"* "</tt> or the ending <tt>\n</tt>, those are added here.
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @throws IllegalArgumentException if the localized pattern string has a parse error
     *     (closing '}' brace without opening '{' brace, etc)
     * @since 2.0.00
     */
    public void printKeyedSpecial(final String key, final Object ... params)
        throws MissingResourceException, IllegalArgumentException
    {
        textDisplay.append("* " + strings.getSpecial(game, key, params) + "\n");  // TextArea will soft-wrap within line
    }

    /**
     * print text in the text window, followed by a new line (<tt>'\n'</tt>).
     *
     * @param s  the text; you don't need to include "\n".
     * @see #chatPrint(String)
     * @see #printKeyed(String)
     * @see #printKeyed(String, Object...)
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
     * Queue a sound to play soon but not in this thread.
     * Uses {@link PIPlaySound} to call {@link Sounds#playPCMBytes(byte[])}.
     * No sound is played if preference {@link SOCPlayerClient#PREF_SOUND_ON} is false
     * or if {@link #isSoundMuted()}.
     *<P>
     * Playback uses a queuing thread executor, not the AWT {@link EventQueue}.
     *
     * @param buf  Mono 8-bit PCM sound to play, or null to do nothing.
     *     Can be generated by methods like {@link Sounds#genChime(int, int, double)}.
     * @since 1.2.00
     */
    public void playSound(final byte[] buf)
    {
        if (buf != null)
            soundQueueThreader.submit(new PIPlaySound(buf));
    }

    /**
     * Game was deleted or a server/network error occurred; stop playing.
     * @param wasDeleted  True if game was deleted, isn't from an error;
     *     this can happen while observing a game
     * @param errorMessage  Error message if any, or {@code null}
     */
    public void over(final boolean wasDeleted, final String errorMessage)
    {
        gameHasErrorOrDeletion = true;

        if (textInputIsInitial)
            textInputSetToInitialPrompt(false);  // Clear, set foreground color
        textInput.setEditable(false);
        if (errorMessage != null)
            textInput.setText(errorMessage);
        if (wasDeleted)
        {
            textDisplay.append("*** " + strings.get("interface.error.game.has_been_deleted") + " ***\n");
                // "Game has been deleted."
        } else {
            textDisplay.append("* " + strings.get("interface.error.lost.conn") + "\n");
                // "Lost connection to the server."
            textDisplay.append("*** " + strings.get("interface.error.game.stopped") + " ***\n");
                // "Game stopped."
        }

        game.setCurrentPlayerNumber(-1);
        boardPanel.repaint();
        for (int i = 0; i < game.maxPlayers; i++)
            hands[i].gameDisconnected();
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
    public void began(final List<String> members)
    {
        textInput.setEditable(true);
        textInput.setText("");
        textInputSetToInitialPrompt(true);
        // Don't request focus for textInput; it should clear
        // the prompt text when user clicks (focuses) it, so
        // wait for user to do that.

        hasCalledBegan = true;

        // Look for game observers, list in textDisplay
        if (members == null)
            return;
        List<String> obs = null;
        for (int i = members.size() - 1; i >= 0; --i)
        {
            final String mname = members.get(i);
            if (null != game.getPlayer(mname))
                continue;
            if (mname.equals(client.getNickname()))
                continue;
            if (obs == null)
                obs = new ArrayList<String>();
            obs.add(mname);
        }
        if (obs != null)
        {
            final String obsTxt = (obs.size() == 1)
                ? strings.get("interface.observer.enter.one", obs.get(0))
                : strings.getSpecial(game, "interface.observer.enter.many", obs);
            textDisplay.append("* " + obsTxt + "\n");
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
                repaint(hands[pn].getX(), 0, hands[pn].getWidth(), getHeight());
                    // must repaint entire column's handpanels and wide borders
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
     * Updates panes and displays, does not print any message
     * about the player leaving the game.
     *<P>
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

    // This javadoc also appears in PlayerClientListener; please also update there if it changes.
    /**
     * Is a dialog or popup message currently visible while gameplay continues?
     * See {@link PlayerClientListener} interface javadoc for details and implications.
     *<P>
     * To do things when the dialog is no longer visible, you can register a listener with
     * {@link #setNonBlockingDialogDismissListener(PlayerClientListener.NonBlockingDialogDismissListener)}.
     * @since 2.0.00
     */
    public boolean isNonBlockingDialogVisible()
    {
        return (nbdForEvent != null) && nbdForEvent.isVisible();
    }

    // This javadoc also appears in PlayerClientListener; please also update there if it changes.
    /**
     * Set or clear the {@link PlayerClientListener.NonBlockingDialogDismissListener listener}
     * for when {@link #isNonBlockingDialogVisible()}'s dialog is no longer visible.
     * @param li  Listener, or {@code null} to clear
     *<P>
     * Implements part {@link PlayerClientListener}.
     * @since 2.0.00
     */
    public void setNonBlockingDialogDismissListener
        (PlayerClientListener.NonBlockingDialogDismissListener li)
    {
        nbddListener = li;
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
     * @since 1.1.00
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
        setTitle(strings.get("interface.title.game.over", game.getName()) +
                 (game.isPractice ? "" : " [" + client.getNickname() + "]"));
                // "Settlers of Catan Game Over: {0}"
        boardPanel.updateMode();
        repaint();
    }

    /**
     * Game's current player has changed.  Update displays.
     *
     * @param pnum New current player number; should match game.getCurrentPlayerNumber()
     * @since 1.1.00
     */
    public void updateAtTurn(final int pnum)
    {
        if ((pnum >= 0) && (pnum < hands.length))
            getPlayerHandPanel(pnum).updateDevCards(false);

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
            if ((textInputGreyCountdown == 0) && textInputIsInitial && ! SwingMainDisplay.isOSColorHighContrast())
            {
                textInput.setForeground(Color.LIGHT_GRAY);
            }
        }

        buildingPanel.updateButtonStatus();

        bankTradeWasFromTradePanel = false;

        // play Begin Turn sound here, not updateAtRollPrompt() which
        // isn't called for first player during initial placement
        if (clientIsCurrentPlayer())
            playSound(SOUND_BEGIN_TURN);
    }

    /**
     * A player is being asked to roll (or play a card) at the start of their turn.
     * Update displays if needed. Also may be called during initial placement
     * when it's a player's turn to place.
     *<P>
     * If the client is the current player, calls {@link SOCHandPanel#autoRollOrPromptPlayer()}
     * unless {@link ClientBridge#isNonBlockingDialogVisible()}.
     * @param pn  Player number being prompted
     * @since 1.1.11
     */
    public void updateAtRollPrompt(final int pn)
    {
        if ((client.getServerVersion(game) >= SOCStringManager.VERSION_FOR_I18N)
            && ! game.isInitialPlacement())
        {
            printKeyed("game.prompt.turn_to_roll_dice", game.getPlayer(pn).getName());
                // "It's Joe's turn to roll the dice."
        }
        // else, server has just sent the prompt text and we've printed it

        if (clientIsCurrentPlayer() && ! clientListener.isNonBlockingDialogVisible())
            getClientHand().autoRollOrPromptPlayer();
    }

    /**
     * The client player's available resources have changed. Update displays if needed.
     *<P>
     * If any trade offers are currently showing, show or hide the offer Accept button
     * depending on the updated set of available resources.
     * @since 1.1.20
     */
    public void updateAtClientPlayerResources()
    {
        for (int i = 0; i < hands.length; ++i)
        {
            if (i == clientHandPlayerNum)
                continue;

            hands[i].updateCurrentOffer(false, true);
        }
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

        printKeyed(svpKey, plName, svp, desc);
    }

    /**
     * Set or clear the chat text input's initial prompt.
     * If {@code setToInitial} true, sets its status, foreground color, and the prompt text
     * unless player already sent chat text ({@link #textInputHasSent}).
     *<P>
     * Do not call this directly from a Swing {@link DocumentListener},
     * which will throw "IllegalStateException: Attempt to mutate in notification":
     * Instead add to event queue with invokeLater.
     *
     * @param setToInitial If false, clear initial-prompt status, and
     *    clear contents (if they are the initial-prompt message);
     *    If true, set initial-prompt status, and set the prompt
     *    (if contents are blank when trimmed).
     * @see #TEXTINPUT_INITIAL_PROMPT_MSG
     * @since 1.1.00
     */
    protected void textInputSetToInitialPrompt(boolean setToInitial)
    {
        if (setToInitial && textInputHasSent)
            return;  // Already sent text, won't re-prompt

        final boolean isOSHighContrast = SwingMainDisplay.isOSColorHighContrast();

        // Always change text before changing flag,
        // so DocumentListener doesn't fight this action.

        if (setToInitial)
        {
            if (textInput.getText().trim().length() == 0)
            {
                textInput.setText(TEXTINPUT_INITIAL_PROMPT_MSG);  // Set text before flag
                textInput.setCaretPosition(0);
            }
            textInputIsInitial = true;
            textInputGreyCountdown = textInputGreyCountFrom;  // Reset fade countdown
            if (! isOSHighContrast)
                textInput.setForeground(Color.DARK_GRAY);
        } else {
            if (textInput.getText().equals(TEXTINPUT_INITIAL_PROMPT_MSG))
                textInput.setText("");  // Clear to make room for text being typed
            textInputIsInitial = false;
            if (! isOSHighContrast)
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
        EventQueue.invokeLater(discardOrGainDialog);  // calls setVisible(true)
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
     * @see GameMessageMaker#choosePlayer(SOCGame, int)
     * @see #showChooseRobClothOrResourceDialog(int)
     */
    public void showChoosePlayerDialog(final int count, final int[] pnums, final boolean allowChooseNone)
    {
        choosePlayerDialog = new SOCChoosePlayerDialog(this, count, pnums, allowChooseNone);
        EventQueue.invokeLater(choosePlayerDialog);  // dialog's run() calls pack and setVisible(true)
    }

    /**
     * Show the {@link ChooseRobClothOrResourceDialog} to choose what to rob from a player.
     * @param vpn  Victim player number
     * @since 2.0.00
     * @see #showChoosePlayerDialog(int, int[], boolean)
     */
    public void showChooseRobClothOrResourceDialog(final int vpn)
    {
        EventQueue.invokeLater(new ChooseRobClothOrResourceDialog(vpn));  // dialog's run() calls setVisible(true)
    }

    /**
     * show the Monopoly dialog box
     */
    public void showMonopolyDialog()
    {
        monopolyDialog = new SOCMonopolyDialog(this);
        EventQueue.invokeLater(monopolyDialog);  // dialog's run() calls setVisible(true)
    }

    /**
     * If this game has a scenario (game option {@code "SC"}), show the scenario
     * description, special rules, and number of victory points to win.
     * Shown automatically when the SOCPlayerInterface is first shown.
     * @since 2.0.00
     */
    public void showScenarioInfoDialog()
    {
        NewGameOptionsFrame.showScenarioInfoDialog(game, getMainDisplay(), this);
    }

    /**
     * Update interface after the server sends us a new board layout.
     * Call after setting game data and board data.
     * Calls {@link SOCBoardPanel#flushBoardLayoutAndRepaint()}, which
     * may change its minimum size if board has Layout Part "VS".
     * Updates display of board-related counters, such as {@link soc.game.SOCBoardLarge#getCloth()}.
     * Not needed if calling {@link #resetBoard(SOCGame, int, int)}.
     * @since 2.0.00
     */
    public void updateAtNewBoard()
    {
        Dimension sizeChange = boardPanel.flushBoardLayoutAndRepaint();
        if (game.isGameOptionSet(SOCGameOption.K_SC_CLVI))
            buildingPanel.updateClothCount();
        if (sizeChange != null)
        {
            final int dw = sizeChange.width, dh = sizeChange.height;
            width_base += dw;
            height_base += dh;
            invalidate();
            setSize(getWidth() + dw, getHeight() + dh);
        }
    }

    /**
     * Update interface after game state has changed.
     * For example, if the client is current player, and state changed from ROLL_OR_CARD to PLAY1
     * (Dice have been rolled, or card played), enable the player's Done and Bank buttons.
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
     * @since 1.1.00
     */
    public void updateAtGameState()
    {
        int gs = game.getGameState();

        if (! knowsGameState)
        {
            knowsGameState = true;

            // game state was 0 when PI and handpanels were created:
            // update Sit Here buttons' status now if we haven't already sat down;
            // show buttons only if game is still forming
            if (clientHand == null)
            {
                for (int i = 0; i < game.maxPlayers; i++)
                    if (game.isSeatVacant(i))
                    {
                        if (gs < SOCGame.START2A)
                            hands[i].addSitButton(false);
                        else
                            hands[i].removeSitBut();
                    }
            }
        }

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

        if ((gs == SOCGame.PLACING_INV_ITEM) && clientIsCurrentPlayer()
            && game.isGameOptionSet(SOCGameOption.K_SC_FTRI))
        {
            printKeyed("game.invitem.sc_ftri.prompt");
                // "You have received this trade port as a gift."
                // "You must now place it next to your coastal settlement which is not adjacent to any existing port."
        }

        // Update our interface at start of first turn;
        // server doesn't send non-bot clients a TURN message after the
        // final road/ship is placed (state START2 -> ROLL_OR_CARD).
        if (gameIsStarting && (gs >= SOCGame.ROLL_OR_CARD))
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
            mesHp.updateValue(PlayerClientListener.UpdateType.ResourceTotalAndDetails);

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
                if (mesHp == clientHand)
                    mesHp.disableBankUndoButton();  // just in case; it probably wasn't enabled
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

        if (hasCalledBegan && (game.getGameState() >= SOCGame.START1A))
            playSound(SOUND_PUT_PIECE);

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
     * The robber or pirate has been moved onto a hex. Repaints board.
     * @param newHex  The new robber/pirate hex coordinate, or 0 to take the pirate off the board
     * @param isPirate  True if the pirate, not the robber, was moved
     * @see SOCGame#doesRobberLocationAffectPlayer(int, boolean)
     * @since 1.2.00
     */
    public void updateAtRobberMoved(final int newHex, final boolean isPirate)
    {
        getBoardPanel().repaint();
    }

    /**
     * Listener callback for scenario events on the large sea board which affect the game or board,
     * not a specific player. For example, a hex might be revealed from fog.
     *<P>
     * <em>Threads:</em> The game's treater thread handles incoming client messages and calls
     * game methods that change state. Those same game methods will trigger the scenario events;
     * so, the treater thread will also run this <tt>gameEvent</tt> callback.
     * GUI code should use {@link EventQueue#invokeLater(Runnable)}.
     *
     * @param ga  Game
     * @param evt Event code
     * @param detail  Game piece, coordinate, or other data about the event, or null, depending on <tt>evt</tt>
     * @see #playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)
     * @since 2.0.00
     */
    public void gameEvent(final SOCGame ga, final SOCScenarioGameEvent evt, final Object detail)
    {
        switch (evt)
        {
        case SGE_STARTPLAY_BOARD_SPECIAL_NODES_EMPTIED:
            EventQueue.invokeLater(new Runnable()
            {
                public void run() { boardPanel.flushBoardLayoutAndRepaint(); }
            });
            break;

        default:
            // Most game events are ignored at the client.
            // Default case does nothing, prevents a compiler warning.
        }
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
     * {@inheritDoc}
     *<P>
     * For {@link PlayerClientListener.NonBlockingDialogDismissListener} interface;
     * if {@code srcDialog} matches currently tracked dialog for {@link #isNonBlockingDialogVisible()},
     * clear that tracking field.
     * @since 2.0.00
     */
    public void dialogDismissed(final Object srcDialog, final boolean wasCanceled)
    {
        if (nbdForEvent == srcDialog)
            nbdForEvent = null;
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
            mainDisplay.getEventTimer().schedule(showingPlayerDiscardOrPick_task, 1000 /* ms */ );
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
     * @param rejoinPlayerNumber Sanity check - must be our correct player number in this game,
     *     or -1 if server didn't send a player number
     * @param requesterNumber Player who requested the board reset
     *
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     * @since 1.1.00
     */
    public void resetBoard
        (final SOCGame newGame, final int rejoinPlayerNumber, final int requesterNumber)
    {
        if (clientHand == null)
            return;
        if ((rejoinPlayerNumber != -1) && (rejoinPlayerNumber != clientHandPlayerNum))
            return;
        if (newGame == null)
            throw new IllegalArgumentException("newGame is null");

        // Feedback: "busy" mouse cursor while clearing and re-laying out the components
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Clear out old state (similar to constructor)
        int oldGameState = game.getResetOldGameState();
        game = newGame;
        knowsGameState = (game.getGameState() != 0);
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
        clientHand = null;  // server will soon send SITDOWN messages for all human players,
                            // we'll then set clientHand to a new SOCHandPanel
        clientHandPlayerNum = -1;

        removeAll();  // old sub-components
        initInterfaceElements(false);  // new sub-components

        // Clear from possible "game over" titlebar
        setTitle(strings.get("interface.title.game", game.getName()) +
                 (game.isPractice ? "" : " [" + client.getNickname() + "]"));
                // "Settlers of Catan Game: {0}"
        boardPanel.debugShowPotentials = boardDebugShow;

        validate();
        repaint();

        chatDisplay.append(prevChatText);

        String requesterName = game.getPlayer(requesterNumber).getName();
        if (requesterName == null)
            requesterName = strings.get("reset.player.who.left");  // fall back to "player who left"

        final String resetMsg = "** "
            + strings.get( ((oldGameState != SOCGame.OVER)
              ? "reset.board.was.reset"     // "The board was reset by {0}."
              : "reset.new.game.started"),  // "New game started by {0}.
              requesterName)
            + "\n";
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
     * @since 1.1.00
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
     * @since 1.1.00
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
     * Arrange the custom layout at creation or frame resize.
     * Stretches {@link SOCBoardPanel}, {@link SOCHandPanel}s, etc to fit.
     *<P>
     * If a player sits down in a 6-player game, will need to
     * {@link #invalidate()} and call this again, because {@link SOCHandPanel} sizes will change.
     *<P>
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
         * (board size was fixed, cannot scale)
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
         *
         * If board can be at least 15% larger than minimum board width,
         * without violating minimum handpanel width, scale it larger.
         * Otherwise, use minimum board width (widen handpanels instead).
         *
         * Handpanel height:
         * - If 4-player, 1/2 of window height
         * - If 6-player, 1/3 of window height, until client sits down.
         *   (Column of 3 on left side, 3 on right side, of this frame)
         *   Once sits down, that column's handpanel heights are
         *   1/2 of window height for the player's hand, and 1/4 for others.
         *
         * Since this is the minimum, is not multiplied by displayScale:
         * Don't need displayScale here because we already used it when
         * setting width_base and height_base. Just use overall PI size
         * to drive boardpanel size.
         */
        final int bMinW, bMinH;
        {
            Dimension bpMinSz = boardPanel.getMinimumSize();
            bMinW = bpMinSz.width;
            bMinH = bpMinSz.height;
        }
        final int buildph = buildingPanel.getHeight();
        final int tfh = textInput.getHeight();
        final int pix4 = 4 * displayScale, pix8 = 8 * displayScale,
                  pix12 = 12 * displayScale, pix16 = 16 * displayScale;
        int hpMinW = SOCHandPanel.WIDTH_MIN * displayScale;
        if (hpMinW < (dim.width / 10))
            hpMinW = dim.width / 10;  // at least 10% of window width
        int bw = dim.width - (2 * hpMinW) - pix16;  // As wide as possible
        int bh = (int) ((bw * (long) bMinH) / bMinW);

        if (bh > (dim.height - buildph - pix16 - (int)(5.5f * tfh)))
        {
            // Window is wide: board would become taller than fits in window.
            // Re-calc board max height, then board width.
            bh = dim.height - buildph - pix16 - (int)(5.5f * tfh);  // As tall as possible
            bw = (int) ((bh * (long) bMinW) / bMinH);
        }

        int hw = 0;   // each handpanel's width; height is hh
        int tah = 0;  // textareas' height (not including tfh): calculated soon

        boolean canScaleBoard = (bw >= (1.15f * bMinW));

        if (canScaleBoard)
        {
            // Now that we have minimum board height/width,
            // make taller if possible
            int spare = (dim.height - buildph - pix16 - (int)(5.5f * tfh)) - bh;
            if (spare > 0)
                bh += (2 * spare / 3);  // give 2/3 to boardpanel height, the rest to tah

            // and wider if possible
            spare = dim.width - (hpMinW * 2) - bw - pix16;
            if (spare > 0)
                bw += (4 * spare / 5);  // give 4/5 to boardpanel width, the rest to hw

            tah = dim.height - bh - buildph - tfh - pix16;
            hw = (dim.width - bw - pix16) / 2;

            // Scale it
            try
            {
                boardPanel.setBounds(i.left + hw + pix8, i.top + tfh + tah + pix8, bw, bh);
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
            hw = (dim.width - bw - pix16) / 2;
            tah = dim.height - bh - buildph - tfh - pix16;
            try
            {
                boardPanel.setBounds(i.left + hw + pix8, i.top + tfh + tah + pix8, bw, bh);
            }
            catch (IllegalArgumentException ee)
            {
                bw = boardPanel.getWidth();
                bh = boardPanel.getHeight();
                hw = (dim.width - bw - pix16) / 2;
                tah = dim.height - bh - buildph - tfh - pix16;
                boardPanel.setLocation(i.left + hw + pix8, i.top + tfh + tah + pix8);
            }
        }
        boardIsScaled = canScaleBoard;  // set field, now that we know if it works
        final int halfplayers = (is6player) ? 3 : 2;
        final int hh = (dim.height - pix12) / halfplayers;  // handpanel height
        final int kw = bw;

        buildingPanel.setBounds(i.left + hw + pix8, i.top + tah + tfh + bh + pix12, kw, buildph);

        // Hands start at top-left, go clockwise;
        // hp.setBounds also sets its blankStandIn's bounds.
        // Note that any hands[] could be null, due to async adds, calls, invalidations.

        try
        {
            if (! is6player)
            {
                hands[0].setBounds(i.left + pix4, i.top + pix4, hw, hh);
                if (game.maxPlayers > 1)
                {
                    hands[1].setBounds(i.left + hw + bw + pix12, i.top + pix4, hw, hh);
                    hands[2].setBounds(i.left + hw + bw + pix12, i.top + hh + pix8, hw, hh);
                    hands[3].setBounds(i.left + pix4, i.top + hh + pix8, hw, hh);
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
                    hands[0].setBounds(i.left + pix4, i.top + pix4, hw, hh);
                    hands[4].setBounds(i.left + pix4, i.top + 2 * hh + pix12, hw, hh);
                    hands[5].setBounds(i.left + pix4, i.top + hh + pix8, hw, hh);
                }
                if ((clientHandPlayerNum < 1) || (clientHandPlayerNum > 3))
                {
                    hands[1].setBounds(i.left + hw + bw + pix12, i.top + pix4, hw, hh);
                    hands[2].setBounds(i.left + hw + bw + pix12, i.top + hh + pix8, hw, hh);
                    hands[3].setBounds(i.left + hw + bw + pix12, i.top + 2 * hh + pix12, hw, hh);
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
                        hp_x = i.left + hw + bw + pix12;
                    } else {
                        final int[] idx_left = {0, 5, 4};
                        hp_idx = idx_left;
                        hp_x = i.left + pix4;
                    }
                    for (int ihp = 0, hp_y = i.top + pix4; ihp < 3; ++ihp)
                    {
                        SOCHandPanel hp = hands[hp_idx[ihp]];
                        int hp_height;
                        if (hp_idx[ihp] == clientHandPlayerNum)
                            hp_height = (dim.height - pix12) / 2 - (2 * ColorSquare.HEIGHT * displayScale);
                        else
                            hp_height = (dim.height - pix12) / 4 + (ColorSquare.HEIGHT * displayScale);
                        hp.setBounds(hp_x, hp_y, hw, hp_height);
                        hp.invalidate();
                        hp.doLayout();
                        hp_y += (hp_height + pix4);
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
                hf = hf - cdh - tfh - (15 * displayScale);
                if (hf > h)
                    h = hf;
            }

            textDisplay.setBounds(x, i.top + pix4, w, h);
            if (! game.isPractice)
                cdh += (20 * displayScale);
            chatDisplay.setBounds(x, i.top + pix4 + h, w, cdh);
            h += cdh;
            textInput.setBounds(x, i.top + pix4 + h, w, tfh);

            // focus here for easier chat typing
            textInput.requestFocusInWindow();
        } else {
            // standard size
            textDisplay.setBounds(i.left + hw + pix8, i.top + pix4, bw, tdh);
            chatDisplay.setBounds(i.left + hw + pix8, i.top + pix4 + tdh, bw, cdh);
            textInput.setBounds(i.left + hw + pix8, i.top + pix4 + tah, bw, tfh);

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
            mainDisplay.clearWaitingStatus(false);
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
        public void diceRolled(SOCPlayer player, final int rollSum)
        {
            pi.showDiceResult(player, rollSum);
        }

        public void diceRolledResources(final List<Integer> pnum, final List<SOCResourceSet> rsrc)
        {
            StringBuffer sb = new StringBuffer("* ");
            boolean noPlayersGained = true;

            final int n = pnum.size();
            final SOCGame ga = pi.game;
            for (int p = 0; p < n; ++p)  // current index reading from playerNum and playerRsrc
            {
                final int pn = pnum.get(p);
                final SOCHandPanel hpan = pi.getPlayerHandPanel(pn);
                hpan.updateValue(PlayerClientListener.UpdateType.ResourceTotalAndDetails);

                final SOCPlayer pl = ga.getPlayer(pn);
                if (noPlayersGained)
                    noPlayersGained = false;
                else
                    sb.append(" ");

                sb.append
                    (SOCPlayerInterface.strings.getSpecial(ga, "game.roll.gets.resources", pl.getName(), rsrc.get(p)));
                    // "{0} gets {1,rsrcs}."
            }

            if (sb.length() > 2)
                pi.print(sb.toString());
        }

        public void playerJoined(String nickname)
        {
            final String msg = "*** " + strings.get("interface.member.joined.game", nickname);  // "Joe has joined this game."
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

            if (pi.game.getGameState() >= SOCGame.START1A)
            {
                //  Server sends "left" message to print in the game text area.
                //  If game is in progress, also print in the chat area (less clutter there).
                pi.chatPrint("* " + strings.get("interface.member.left.game", nickname));  // "Joe left the game."
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
                hp.updateValue(PlayerClientListener.UpdateType.ResourceTotalAndDetails);
                hp.updateDevCards(false);
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
            pi.updateAtPutPiece
                (player.getPlayerNumber(), sourceCoordinate, pieceType, true, targetCoordinate);
            if (pieceType == SOCPlayingPiece.SHIP)
                pi.printKeyed("game.pieces.moved.ship", player.getName());  // "Joe moved a ship."
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

        public void playerElementUpdated
            (final SOCPlayer player, final PlayerClientListener.UpdateType utype,
             final boolean isGoodNews, final boolean isBadNews)
        {
            final SOCHandPanel hpan = (player == null) ? null : pi.getPlayerHandPanel(player.getPlayerNumber());  // null if no player
            int hpanUpdateRsrcType = 0;  // If not 0, update this type's amount display

            switch (utype)
            {
            // easy cases fall through:
            case Road:
            case Settlement:
            case City:
            case Ship:
            case Knight:  // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
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

            case WonderLevel:
                hpan.updateValue(PlayerClientListener.UpdateType.WonderLevel);
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

                    // Because client player's available resources have changed,
                    // update any trade offers currently showing (show or hide Accept button)
                    pi.updateAtClientPlayerResources();

                    // If good or bad news from unexpectedly gained or lost
                    // resources or pieces, let the player know
                    if (isGoodNews)
                        pi.playSound(SOUND_RSRC_GAINED_FREE);
                    else if (isBadNews)
                        pi.playSound(SOUND_RSRC_LOST);
                }
                else
                {
                    hpan.updateValue(PlayerClientListener.UpdateType.Resources);
                }
            }

            if (hpan.isClientPlayer() && (pi.getGame().getGameState() != SOCGame.NEW))
                pi.getBuildingPanel().updateButtonStatus();
        }

        public void requestedSpecialBuild(SOCPlayer player)
        {
            if (player.hasAskedSpecialBuild())
                pi.printKeyed("game.sbp.wants.to", player.getName());  // * "{0} wants to Special Build."
            if (pi.isClientPlayer(player))
                pi.getBuildingPanel().updateButtonStatus();
        }

        public void requestedGoldResourceCountUpdated(SOCPlayer player, int countToPick)
        {
            final SOCHandPanel hpan = pi.getPlayerHandPanel(player.getPlayerNumber());
            hpan.updatePickGoldHexResources();
        }

        public void playerDevCardUpdated(SOCPlayer player, final boolean addedPlayable)
        {
            if (pi.isClientPlayer(player))
            {
                SOCHandPanel hp = pi.getClientHand();
                hp.updateDevCards(addedPlayable);
                hp.updateValue(PlayerClientListener.UpdateType.VictoryPoints);
            }
            else if (player != null)
            {
                pi.getPlayerHandPanel(player.getPlayerNumber()).updateValue(PlayerClientListener.UpdateType.DevCards);
            }
        }

        public void playerCanCancelInvItemPlay(SOCPlayer player, final boolean canCancel)
        {
            if (pi.isClientPlayer(player))
                pi.getClientHand().setCanCancelInvItemPlay(canCancel);
        }

        public void playerFaceChanged(SOCPlayer player, int faceId)
        {
            pi.changeFace(player.getPlayerNumber(), faceId);
        }

        public void playerStats(EnumMap<PlayerClientListener.UpdateType, Integer> stats)
        {
            pi.printKeyed("stats.rolls.your");  // "Your resource rolls: (Clay, Ore, Sheep, Wheat, Wood)"
            int total = 0;

            // read resource stats into an array for message format access
            int[] v = new int[5];  // CLAY - WOOD
            final PlayerClientListener.UpdateType[] types =
            {
                PlayerClientListener.UpdateType.Clay,
                PlayerClientListener.UpdateType.Ore,
                PlayerClientListener.UpdateType.Sheep,
                PlayerClientListener.UpdateType.Wheat,
                PlayerClientListener.UpdateType.Wood
            };

            int i = 0;
            for (PlayerClientListener.UpdateType t : types)
            {
                int value = stats.get(t).intValue();
                total += value;
                v[i] = value;  ++i;
            }

            pi.printKeyed("stats.rolls.n.total", v[0], v[1], v[2], v[3], v[4], total);  // "{0}, {1}, {2}, {3}, {4}. Total: {5}"

            Integer gp = stats.get(PlayerClientListener.UpdateType.GoldGains);
            if (gp != null)
                pi.printKeyed("stats.gold_gains", gp);  // "Resources gained from gold hexes: {0}"
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
         * game is about to start.  Calls {@link SOCPlayerInterface#began(List)}.
         * @param names  Game member names; to see if each is a player, call {@link SOCGame#getPlayer(String)}.
         */
        public void membersListed(List<String> names)
        {
            pi.began(names);
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

        public final boolean isNonBlockingDialogVisible()
        {
            return pi.isNonBlockingDialogVisible();
        }

        public final void setNonBlockingDialogDismissListener(NonBlockingDialogDismissListener li)
        {
            pi.setNonBlockingDialogDismissListener(li);
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
                scoresArray[e.getKey().getPlayerNumber()] = e.getValue().intValue();

            pi.updateAtOver(scoresArray);
        }

        public void gameDisconnected(final boolean wasDeleted, final String errorMessage)
        {
            pi.over(wasDeleted, errorMessage);
        }

        public void messageBroadcast(String msg)
        {
            pi.chatPrint("::: " + msg + " :::");
        }

        public void messageReceived(String nickname, String message)
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
                if (! pi.getClient().onIgnoreList(nickname))
                    pi.chatPrint(nickname + ": " + message);
            }
        }

        public final void simpleRequest(final int pn, final int reqtype, final int value1, final int value2)
        {
            switch (reqtype)
            {
            case SOCSimpleRequest.SC_PIRI_FORT_ATTACK:
                // was rejected
                scen_SC_PIRI_pirateFortressAttackResult(true, 0, 0);
                break;

            case SOCSimpleRequest.TRADE_PORT_PLACE:
                if (pn == -1)
                {
                    // rejected
                    if (pi.getGame().getGameState() == SOCGame.PLACING_INV_ITEM)
                        pi.printKeyed("game.invitem.sc_ftri.need.coastal");  // * "Requires a coastal settlement not adjacent to an existing port."
                    else
                        pi.printKeyed("hpan.item.play.cannot");  // * "Cannot play this item right now."
                } else {
                    // placement
                    boardUpdated();
                    if (pi.clientHand != null)
                        pi.clientHand.updateResourceTradeCosts(false);
                    pi.printKeyed("game.invitem.port.placed", pi.game.getPlayer(pn).getName());  // * "{0} has placed a trade port."
                }
                break;

            case SOCSimpleRequest.PROMPT_PICK_RESOURCES:
                promptPickResources(value1);
                break;

            default:
                // ignore unknown request types
                System.err.println
                    ("PI.simpleRequest: Ignored unknown type " + reqtype + " in game " + pi.game.getName());

            }
        }

        public final void simpleAction(final int pn, final int acttype, final int value1, final int value2)
        {
            final String plName = (pn >= 0) ? pi.game.getPlayer(pn).getName() : null;

            switch (acttype)
            {
            case SOCSimpleAction.DEVCARD_BOUGHT:
                {
                    pi.printKeyed("game.devcard.bought", plName);
                    final String remainKey;
                    switch (value1)
                    {
                    case 0:
                        remainKey = "game.devcard.bought.0left";  break;  // "There are no more Development cards."
                    case 1:
                        remainKey = "game.devcard.bought.1left";  break;  // "There is 1 card left."
                    default:
                        remainKey = "game.devcard.bought.xleft";  // "There are 5 cards left."
                    }
                    pi.printKeyed(remainKey, value1);
                }
                break;

            case SOCSimpleAction.BOARD_EDGE_SET_SPECIAL:
                boardUpdated();
                break;

            case SOCSimpleAction.RSRC_TYPE_MONOPOLIZED:
                {
                    if (pn == pi.clientHandPlayerNum)
                        pi.printKeyedSpecial("game.action.mono.you.monopolized", value1, value2);
                            // "You monopolized 5 sheep."
                    else
                        pi.printKeyedSpecial("game.action.mono.monopolized", plName, value1, value2);
                            // "Joe monopolized 5 sheep."
                }
                break;

            case SOCSimpleAction.TRADE_PORT_REMOVED:
                {
                    boardUpdated();
                    pi.printKeyed("game.invitem.port.picked.up", plName);  // "{0} has picked up a trade port from the board."
                }
                break;

            default:
                // ignore unknown action types
                System.err.println
                    ("PI.simpleAction: Ignored unknown type " + acttype + " in game " + pi.game.getName());

            }
        }

        public void buildRequestCanceled(SOCPlayer player)
        {
            pi.getPlayerHandPanel(player.getPlayerNumber()).updateResourcesVP();
            pi.getBoardPanel().updateMode();
        }

        public void invItemPlayRejected(final int type, final int reasonCode)
        {
            if ((reasonCode == 4) && pi.getGame().isGameOptionSet(SOCGameOption.K_SC_FTRI))
                pi.printKeyed("game.invitem.sc_ftri.need.coastal");  // * "Requires a coastal settlement not adjacent to an existing port."
            else
                pi.printKeyed("hpan.item.play.cannot");  // * "Cannot play this item right now."
        }

        public void playerPickSpecialItem
            (final String typeKey, final SOCGame ga, final SOCPlayer pl, final int gi, final int pi,
             final boolean isPick, final int coord, final int level, final String sv)
        {
            if ((pl == null) && isPick)
                return;  // <--- Early return: So far, every pick implemented is player-specific ---

            if (! typeKey.equals(SOCGameOption.K_SC_WOND))
                return;  // <--- Early return: So far, the only known typeKey is _SC_WOND ---

            if (isPick)
            {
                String iname = null;
                final SOCSpecialItem itm = ga.getSpecialItem(typeKey, gi);

                if (itm != null)
                    iname = itm.getStringValue();

                if (iname != null)
                    iname = strings.get("game.specitem.sc_wond." + iname); // "w3" -> "Monument", etc
                else
                    iname = "# " + gi;

                if (level == 1)
                    this.pi.printKeyed("game.specitem.sc_wond.started", pl.getName(), iname);
                        // "{0} started building a Wonder! ({1})"
                else
                    this.pi.printKeyed("game.specitem.sc_wond.built", pl.getName(), level, iname);
                        // "{0} has built level # of their Wonder ({2})."

                // TODO any visual effect?
            } else {
                this.pi.printKeyed("game.specitem.sc_wond.decl");  // "You cannot build that Wonder right now."
            }
        }

        public void playerSetSpecialItem
            (final String typeKey, final SOCGame ga, final SOCPlayer pl, final int gi, final int pi, final boolean isSet)
        {
            if (pl == null)
                return;  // <--- Early return: So far, everything implemented is player-specific ---

            if (! typeKey.equals(SOCGameOption.K_SC_WOND))
                return;  // <--- Early return: So far, the only known typeKey is _SC_WOND ---

            final SOCHandPanel hp = this.pi.getPlayerHandPanel(pl.getPlayerNumber());
            if (hp != null)
                hp.updateValue(PlayerClientListener.UpdateType.WonderLevel);
        }

        public void scen_SC_PIRI_pirateFortressAttackResult
            (final boolean wasRejected, final int defStrength, final int resultShipsLost)
        {
            if (wasRejected)
            {
                pi.printKeyed("game.sc_piri.attfort.cannot");  // * "You cannot attack the fortress right now."
                return;
            }

            final SOCGame ga = pi.getGame();
            final SOCPlayer cpl = ga.getPlayer(ga.getCurrentPlayerNumber());
            final SOCFortress fort = cpl.getFortress();
            final String cplName = cpl.getName();
            pi.printKeyed("game.sc_piri.attfort.attacked", cplName, defStrength);
                // * "{0} has attacked a Pirate Fortress (defense strength {1})."

            String resDesc;  // used for game text print and popup window
            switch (resultShipsLost)
            {
            case 0:  resDesc = "game.sc_piri.attfort.wins";  break;  // "{0} wins!"
            case 1:  resDesc = "game.sc_piri.attfort.ties";  break;  // "{0} ties, and loses 1 ship."
            default: resDesc = "game.sc_piri.attfort.loses"; break;  // "{0} loses, and loses 2 ships."
                // case 2 is "default" so resDesc is always set for compiler
            }
            resDesc = strings.get(resDesc, cplName);  // "Player 2 wins!"
            pi.print("* " + resDesc);

            final String resDesc2;
            if (resultShipsLost == 0)
            {
                if (fort == null)
                {
                    // defeated and recaptured
                    resDesc2 = strings.get("game.sc_piri.attfort.wins.recaptured", cplName);
                        // "{0} has recaptured a Fortress as a settlement."
                } else {
                    // still needs to attack
                    resDesc2 = strings.get("game.sc_piri.attfort.n.more.attacks", fort.getStrength());
                        // "That Fortress will be defeated after {0} more attack(s)."
                }
                pi.print("* " + resDesc2);
            } else {
                resDesc2 = null;
            }

            if ((resultShipsLost == 0) || pi.clientIsCurrentPlayer())
            {
                // alert sound if client player lost ships
                if (resultShipsLost > 0)
                    pi.playSound(SOUND_RSRC_LOST);

                // popup if player is our client, or if won or recaptured

                StringBuffer sb = new StringBuffer(strings.get("game.sc_piri.attfort.results"));  // "Pirate Fortress attack results:"
                sb.append('\n');
                sb.append(strings.get("game.sc_piri.attfort.def.strength", defStrength));  // "Defense strength: {0}"
                sb.append('\n');
                sb.append(resDesc);
                if (resDesc2 != null)
                {
                    sb.append('\n');
                    sb.append(resDesc2);
                }

                final String s = sb.toString();
                NotifyDialog nd = new NotifyDialog(pi.getMainDisplay(), pi, s, null, true);
                nd.setNonBlockingDialogDismissListener(pi);
                pi.nbdForEvent = nd;
                EventQueue.invokeLater(nd);  // calls setVisible(true)
            }
        }

        public void robberMoved(final int newHex, final boolean isPirate)
        {
            pi.updateAtRobberMoved(newHex, isPirate);
        }

        public void devCardDeckUpdated()
        {
            pi.updateDevCardCount();
        }

        public void requestedDiscard(int countToDiscard)
        {
            pi.showDiscardOrGainDialog(countToDiscard, true);
        }

        public void promptPickResources(int countToPick)
        {
            pi.showDiscardOrGainDialog(countToPick, false);
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

        public void playerBankTrade(final SOCPlayer player, final SOCResourceSet give, final SOCResourceSet get)
        {
            requestedTradeClear(player, true);
            pi.printTradeResources(player, give, get, false, null);
        }

        public void requestedTrade(SOCPlayer offerer)
        {
            pi.getPlayerHandPanel(offerer.getPlayerNumber()).updateCurrentOffer(true, false);
            final SOCTradeOffer offer = offerer.getCurrentOffer();
            if (offer != null)
                pi.printTradeResources(offerer, offer.getGiveSet(), offer.getGetSet(), true, null);
        }

        public void requestedTradeClear(final SOCPlayer offerer, final boolean isBankTrade)
        {
            if (isBankTrade && ! pi.bankTradeWasFromTradePanel)
                return;

            if (offerer != null)
                pi.getPlayerHandPanel(offerer.getPlayerNumber()).updateCurrentOffer(false, false);
            else
                for (int i = 0; i < pi.game.maxPlayers; ++i)
                    pi.getPlayerHandPanel(i).updateCurrentOffer(false, false);
        }

        public void requestedTradeRejection(SOCPlayer rejecter)
        {
            pi.getPlayerHandPanel(rejecter.getPlayerNumber()).rejectOfferShowNonClient();
        }

        public void playerTradeAccepted(final SOCPlayer offerer, final SOCPlayer acceptor)
        {
            final SOCTradeOffer offer = offerer.getCurrentOffer();
            if (offer != null)
                pi.printTradeResources(offerer, offer.getGiveSet(), offer.getGetSet(), false, acceptor);
        }

        public void requestedTradeReset(SOCPlayer playerToReset)
        {
            final int pn = (playerToReset != null) ? playerToReset.getPlayerNumber() : -1;
            pi.clearTradeMsg(pn);
        }

        public void requestedDiceRoll(final int pn)
        {
            pi.updateAtRollPrompt(pn);
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
     *<P>
     * Use {@link EventQueue#invokeLater(Runnable)} to show, so message treating can continue as other players vote.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.00
     */
    protected static class ResetBoardVoteDialog extends AskDialog implements Runnable
    {
        /** If true, don't call any methods from callbacks here */
        private boolean askedDisposeQuietly;

        /**
         * Creates a new ResetBoardVoteDialog.
         *
         * @param md       Player client's main display
         * @param gamePI   Current game's player interface
         * @param requester  Name of player requesting the reset
         * @param gameIsOver The game is over - "Reset" button should be default (if not over, "Continue" is default)
         */
        protected ResetBoardVoteDialog
            (MainDisplay md, SOCPlayerInterface gamePI, String requester, boolean gameIsOver)
        {
            super(md, gamePI, strings.get("reset.board.for.game", gamePI.getGame().getName()),  // "Reset board for game {0}?"
                (gameIsOver
                    ? strings.get("reset.x.wants.start.new", requester)  // "{0} wants to start a new game."
                    : strings.get("reset.x.wants.reset", requester)),    // "{0} wants to reset the game being played."
                (gameIsOver
                    ? strings.get("base.restart")    // "Restart"
                    : strings.get("reset.reset")),   // "Reset"
                (gameIsOver
                    ? strings.get("base.no.thanks")                  // "No thanks"
                    : strings.get("dialog.base.continue.playing")),  // "Continue playing"
                null,
                (gameIsOver ? 1 : 2));
            askedDisposeQuietly = false;
        }

        /**
         * React to the Reset button. (call playerClient.resetBoardVote)
         */
        @Override
        public void button1Chosen()
        {
            md.getGameMessageMaker().resetBoardVote(pi.getGame(), pi.getClientPlayerNumber(), true);
            pi.resetBoardClearDia();
        }

        /**
         * React to the No button. (call playerClient.resetBoardVote)
         */
        @Override
        public void button2Chosen()
        {
            md.getGameMessageMaker().resetBoardVote(pi.getGame(), pi.getClientPlayerNumber(), false);
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

        public void disposeQuietly()
        {
            askedDisposeQuietly = true;
            dispose();
        }

    }  // class ResetBoardVoteDialog

    /**
     * Modal dialog to ask whether to move the robber or the pirate ship.
     * Use the AWT event thread to show, so message treating can continue while the dialog is showing.
     * When the choice is made, calls {@link GameMessageMaker#chooseRobber(SOCGame)}
     * or {@link GameMessageMaker#choosePirate(SOCGame)}.
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
        private ChooseMoveRobberOrPirateDialog()
        {
            super(getMainDisplay(), SOCPlayerInterface.this,
                strings.get("dialog.choosemove.robber.or.pirate"), // "Move robber or pirate?"
                strings.get("dialog.choosemove.ask.rob.pirate"),   // "Do you want to move the robber or the pirate ship?"
                strings.get("dialog.base.move.robber"),  // "Move Robber"
                strings.get("dialog.base.move.pirate"),  // "Move Pirate"
                null, 1);
        }

        /**
         * React to the Move Robber button.
         * Call {@link GameMessageMaker#choosePlayer(SOCGame, int) pcli.choosePlayer(CHOICE_MOVE_ROBBER)}.
         */
        @Override
        public void button1Chosen()
        {
            md.getGameMessageMaker().chooseRobber(game);
        }

        /**
         * React to the Move Pirate button.
         * Call {@link GameMessageMaker#choosePlayer(SOCGame, int) pcli.choosePlayer(CHOICE_MOVE_PIRATE)}.
         */
        @Override
        public void button2Chosen()
        {
            md.getGameMessageMaker().choosePirate(game);
        }

        /**
         * React to the dialog window closed by user. (Default is move the robber)
         */
        @Override
        public void windowCloseChosen() { button1Chosen(); }

    }  // nested class ChooseMoveRobberOrPirateDialog

    /**
     * Modal dialog to ask whether to rob cloth or a resource from the victim.
     * Start a new thread to show, so message treating can continue while the dialog is showing.
     * When the choice is made, calls {@link GameMessageMaker#choosePlayer(SOCGame, int)}.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    private class ChooseRobClothOrResourceDialog extends AskDialog implements Runnable
    {
        private static final long serialVersionUID = 2000L;

        /** victim player number */
        private final int vpn;

        /**
         * Creates a new ChooseRobClothOrResourceDialog.
         * To display the dialog, call {@link EventQueue#invokeLater(Runnable)}.
         * @param vpn  Victim player number
         */
        protected ChooseRobClothOrResourceDialog(final int vpn)
        {
            super(getMainDisplay(), SOCPlayerInterface.this,
                strings.get("dialog.rob.sc_clvi.cloth.or.rsrc"),      // "Rob cloth or resource?"
                strings.get("dialog.rob.sc_clvi.ask.cloth.or.rsrc"),  // "Do you want to steal cloth or a resource from this player?"
                strings.get("dialog.rob.sc_clvi.cloth"),  // "Steal Cloth"
                strings.get("dialog.rob.sc_clvi.rsrc"),   // "Steal Resource"
                null, 1);
            this.vpn = vpn;
        }

        /**
         * React to the Steal Cloth button.
         * Call {@link GameMessageMaker#choosePlayer(SOCGame, int) pcli.choosePlayer(-(vpn + 1))}.
         */
        @Override
        public void button1Chosen()
        {
            md.getGameMessageMaker().choosePlayer(game, -(vpn + 1));
        }

        /**
         * React to the Steal Resource button.
         * Call {@link GameMessageMaker#choosePlayer(SOCGame, int) pcli.choosePlayer(vpn)}.
         */
        @Override
        public void button2Chosen()
        {
            md.getGameMessageMaker().choosePlayer(game, vpn);
        }

        /**
         * React to the dialog window closed by user. (Default is steal resource)
         */
        @Override
        public void windowCloseChosen() { button2Chosen(); }

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
         * @param md       Player client's main display
         * @param gamePI   Current game's player interface
         */
        private ResetBoardConfirmDialog(MainDisplay md, SOCPlayerInterface gamePI)
        {
            super(md, gamePI, strings.get("reset.restart.game"),  // "Restart game?"
                strings.get("reset.board.new"),  // "Reset the board and start a new game?"
                strings.get("base.restart"),
                strings.get("base.cancel"),
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

    }  // nested class ResetBoardConfirmDialog

    /**
     * React to window closing or losing focus (deactivation).
     * @author jdmonin
     * @since 1.1.00
     */
    private static class PIWindowAdapter extends WindowAdapter
    {
        private final MainDisplay md;
        private final SOCPlayerInterface pi;

        public PIWindowAdapter(MainDisplay md, SOCPlayerInterface spi)
        {
            this.md = md;
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
                SOCQuitConfirmDialog.createAndShow(md, pi);
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
     * ({@link #TEXTINPUT_INITIAL_PROMPT_MSG}).
     * It's expected that after the player sends their first line of chat text,
     * the listeners will be removed so we don't have the overhead of
     * calling these methods.
     * @author jdmonin
     * @since 1.1.00
     */
    private static class SOCPITextfieldListener
        extends KeyAdapter implements DocumentListener, FocusListener
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

        // JTextField document listener:

        public void insertUpdate(DocumentEvent e)  { }

        public void removeUpdate(DocumentEvent e)  { promptIfEmpty(); }

        public void changedUpdate(DocumentEvent e) { promptIfEmpty(); }

        /**
         * If input text is cleared, and player leaves the textfield while it's empty,
         * show the prompt message unless they've already sent a line of chat.
         */
        public void focusLost(FocusEvent e)
        {
            promptIfEmpty();
        }

        /**
         * If input text field is made empty, show the
         * prompt message unless player has already sent a line of chat.
         * @since 2.0.00
         */
        private void promptIfEmpty()
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

                EventQueue.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        pi.textInputSetToInitialPrompt(true);
                    }
                });
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
         * with {@link MainDisplay#getEventTimer()}.{@link Timer#schedule(TimerTask, long) schedule(msgTask,delay)} .
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

    /**
     * Runnable class to try to play a queued sound using {@link Sounds#playPCMBytes(byte[])}.
     * No sound is played if preference {@link SOCPlayerClient#PREF_SOUND_ON} is false
     * or if {@link SOCPlayerInterface#isSoundMuted() pi.isSoundMuted()}.
     *<P>
     * If playback throws {@link LineUnavailableException}, playback stops but the exception
     * has no further effect on this {@link SOCPlayerInterface}.
     * @see SOCPlayerInterface#playSound(byte[])
     * @since 1.2.00
     */
    private class PIPlaySound implements Runnable
    {
        private final byte[] buf;

        /**
         * Create a queued sound Runnable.
         * @param buf  Buffered sound to play; not null
         * @throws IllegalArgumentException if {@code buf} is null
         */
        public PIPlaySound(final byte[] buf)
            throws IllegalArgumentException
        {
            if (buf == null)
                throw new IllegalArgumentException("PIPlaySound");

            this.buf = buf;
        }

        public void run()
        {
            if (soundMuted || ! UserPreferences.getPref(SOCPlayerClient.PREF_SOUND_ON, true))
                return;

            try
            {
                Sounds.playPCMBytes(buf);
            } catch (LineUnavailableException e) {}
        }
    }

}  // SOCPlayerInterface
