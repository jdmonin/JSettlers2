/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2022 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.ResourceSet;
import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameEvent;
import soc.game.SOCGameEventListener;
import soc.game.SOCGameOptionSet;
import soc.game.SOCInventory;
import soc.game.SOCInventoryItem;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerEvent;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCScenario;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCSpecialItem;
import soc.game.SOCTradeOffer;
import soc.game.SOCVillage;
import soc.message.SOCMessage;
import soc.message.SOCPlayerElement.PEType;
import soc.message.SOCDeclinePlayerRequest;
import soc.message.SOCDevCardAction;
import soc.message.SOCPickResources;  // for reason code constants
import soc.message.SOCPlayerStats;   // for trade type constants
import soc.message.SOCSimpleAction;  // for action type constants
import soc.message.SOCSimpleRequest;  // for request type constants
import soc.message.SOCTurn;  // for server version check
import soc.util.SOCStringManager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.TextArea;
import java.awt.TextComponent;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
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

import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Window with interface for a player in one game of Settlers of Catan.
 * Contains {@link SOCBoardPanel board}, client's and other players' {@link SOCHandPanel hands},
 * chat interface, game message window, and the {@link SOCBuildingPanel building/buying panel}.
 *<P>
 * Players' {@link SOCHandPanel hands} start with player 0 at top-left, and go clockwise;
 * see {@link PILayoutManager} for details. Component sizes including {@link SOCBoardPanel}
 * are recalculated when the frame is resized.
 *<P>
 * When we join a game, the client will update visible game state by calling methods here like
 * {@link #addPlayer(String, int)}; when all this activity is complete, and the interface is
 * ready for interaction, the client calls {@link #began(List)}.
 *<P>
 * Has keyboard shortcuts for Accept/Reject/Counter trade offers since v2.3.00,
 * and to ask for Special Building since v2.5.00.
 * See {@link PIHotkeyActionListener} for details if adding others.
 *<P>
 * <B>Chat text history:</B>
 * Remembers chat text sent by client player to the game/server, including local debug commands.
 *<UL>
 * <LI> If text is exact copy of previously sent line, don't add another copy to history
 * <LI> Up arrow key browses back in time, Down browses forward (same direction as the chat output window)
 * <LI> While browsing history, can edit currently-displayed line in input field.
 *      Can then hit Enter to send, or continue browsing (changes are discarded).
 * <LI> At start of browsing (first Up arrow), saves current contents of chat input field.
 * <LI> When browsing Down past the most recent history, restores those saved contents.
 * <LI> When board is reset, keeps sent history but discards contents of the scrolling chat pane.
 *      After the reset, server sends a "recap" of recent chat text.
 *</UL>
 *
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
public class SOCPlayerInterface extends JFrame
    implements ActionListener, MouseListener, SOCGameEventListener,
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
     * Optional board layout part Visual Shift ("VS") may increase {@link #boardPanel}'s size
     * and {@code width_base}.
     * @see #height_base
     * @see #widthOrig
     * @since 1.2.00
     */
    private int width_base;

    /**
     * Minimum frame height calculated in constructor from this game's player count and board,
     * based on {@link #HEIGHT_MIN_4PL} and {@link #displayScale}.
     * Optional board layout part Visual Shift ("VS") may increase {@link #boardPanel}'s size
     * and {@code height_base}.
     * @see #width_base
     * @see #heightOrig
     * @since 1.2.00
     */
    private int height_base;

    /**
     * Frame's original width, as calculated in constructor.
     * Used with {@link #wasResized} in {@link #frameResizeDone()}.
     *
     * @see #heightOrig
     * @see #width_base
     * @since 2.0.00
     */
    private int widthOrig;

    /**
     * Frame's original height, as calculated in constructor.
     * Used with {@link #wasResized} in {@link #frameResizeDone()}.
     *
     * @see #widthOrig
     * @see #height_base
     * @since 2.0.00
     */
    private int heightOrig;

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
     * Is this game using the 6-player board?
     * Checks {@link SOCGame#maxPlayers}.
     * @since 1.1.08
     */
    private final boolean is6player;

    /**
     * Is this game using developer Game Option {@link SOCGameOptionSet#K_PLAY_FO PLAY_FO}?
     * If so, all {@link SOCHandPanel}s will show dev cards/inventory details
     * ({@link #isGameObservableVP}) and each resource type amount (brick, ore, ...).
     * @since 2.5.00
     */
    protected final boolean isGameFullyObservable;

    /**
     * Is this game using developer Game Option {@link SOCGameOptionSet#K_PLAY_VPO PLAY_VPO}
     * or {@link #isGameFullyObservable}? If so, all {@link SOCHandPanel}s will show dev cards/inventory details.
     * @since 2.5.00
     */
    protected final boolean isGameObservableVP;

    /**
     * For perf/display-bugs during component layout (OSX firefox),
     * show only background color in {@link #update(Graphics)} when true.
     * @since 1.1.06
     */
    private boolean layoutNotReadyYet;

    /**
     * True only if window size has been changed from {@link #widthOrig} x {@link #heightOrig}.
     * Prevents rewriting/changing size prefs unnecessarily in {@link #frameResizeDone()}.
     * (This flag is needed in case it's changed back to that size afterwards.)
     * @since 2.0.00
     */
    private boolean wasResized;

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
     * Checked in {@link PILayoutManager}.
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
     * The input line where player can type chat text.
     * History is stored in {@link #textInputHistory}.
     * @see #textInputIsInitial
     */
    private JTextField textInput;

    /**
     * History for chat text sent to server from {@link #textInput}.
     * For UI/behavior, see class javadoc.
     *
     *<UL>
     * <LI> Each line must be a non-empty string
     * <LI> Newly sent lines are added to the end (highest index)
     * <LI> Element 0 is reserved for "unsent" contents of {@link #textInput},
     *      to avoid losing that with an accidental up-arrow keypress
     * <LI> During browsing, {@link #textInputHistoryBrowsePos} tracks displayed index.
     *      Sending a line resets that index to 0.
     * <LI> Not thread-safe: Changed only on AWT event thread
     *</UL>
     * @since 2.2.00
     */
    private final List<String> textInputHistory;

    /**
     * Currently displayed position while browsing chat text history;
     * otherwise 0. See {@link #textInputHistory} for details.
     * @since 2.2.00
     */
    private int textInputHistoryBrowsePos;

    /**
     * {@link #textInput} not yet typed into; display prompt message.
     *
     * @see #textInputHasSent
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
     * @since 1.1.00
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
     * @since 1.1.00
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
     * Used for responding to chat textfield changes by setting/clearing prompt message,
     * and up/down arrow keys for history.
     *
     * @see #textInput
     * @since 1.1.00
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
    private volatile long textDisplaysLargerWhen;

    /**
     * In the {@link #is6player 6-player} layout, the text display fields
     * ({@link #textDisplay}, {@link #chatDisplay}) aren't as large.
     * When this flag is set, they've temporarily been made larger.
     * @see SOCPITextDisplaysLargerTask
     * @see #textDisplaysLargerWhen
     * @since 1.1.08
     */
    private volatile boolean textDisplaysLargerTemp;

    /**
     * When set, must return text display field sizes to normal in {@link PILayoutManager}
     * after a previous {@link #textDisplaysLargerTemp} flag set.
     * @since 1.1.08
     */
    private volatile boolean textDisplaysLargerTemp_needsLayout;

    /**
     * Mouse hover flags, for use on 6-player board with {@link #textDisplaysLargerTemp}.
     *<P>
     * Set/cleared in {@link #mouseEntered(MouseEvent)}, {@link #mouseExited(MouseEvent)}.
     * @see SOCPITextDisplaysLargerTask
     * @since 1.1.08
     */
    private volatile boolean textInputHasMouse, textDisplayHasMouse, chatDisplayHasMouse;

    /**
     * In 6-player games, text areas temporarily zoom when the mouse is over them.
     * On windows, the scrollbars aren't considered part of the text areas, so
     * we get a mouseExited when user is trying to scroll the text area.
     * Workaround: Instead of looking for mouseExited, look for mouseEntered on
     * handpanels or boardpanel.
     * @see #textDisplaysLargerTemp
     * @see #sbFixBHasMouse
     */
    private volatile boolean sbFixNeeded;

    /**
     * Mouse hover flags, for use on 6-player board with {@link #textDisplaysLargerTemp}
     * and {@link #sbFixNeeded}. Used only on platforms (windows) where the scrollbar isn't
     * considered part of the textarea and triggers a mouseExited.
     *<P>
     * Set/cleared in {@link #mouseEntered(MouseEvent)}, {@link #mouseExited(MouseEvent)}.
     * @see SOCPITextDisplaysLargerTask
     * @since 1.1.08
     */
    private volatile boolean sbFixLHasMouse, sbFixRHasMouse, sbFixBHasMouse;

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
     * @see #isClientCurrentPlayer()
     * @see #bankTradeWasFromTradePanel
     * @since 1.1.00
     */
    protected SOCHandPanel clientHand;

    /**
     * Player ID of {@link #clientHand}, or -1.
     * Set by {@link SOCHandPanel}'s removePlayer() and addPlayer() methods
     * by calling {@link #setClientHand(SOCHandPanel)}.
     * @see #isClientCurrentPlayer()
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
     * Color to use when painting borders when in high-contrast mode; {@code null} otherwise.
     * Used by {@link #paintBordersHandColumn(Graphics, SOCHandPanel)}.
     * Will be the system foreground color, for contrast against panel backgrounds.
     * When {@code null}, uses {@link Graphics#clearRect(int, int, int, int)} to clear to background color instead.
     * @since 2.0.00
     */
    private final Color highContrastBorderColor;

    /**
     * For high-contrast mode, thick borders between handpanels and other panels; null otherwise.
     * Positioned by {@link PILayoutManager}.
     *<P>
     * Before v2.6.00 converted PI to Swing, those borders were instead drawn by overriding AWT's paint/update methods.
     * @since 2.6.00
     */
    private final ColorSquare[] highContrastBorders;

    /**
     * the client main display that spawned us
     */
    protected final MainDisplay mainDisplay;

    protected final SOCPlayerClient client;

    /**
     * the game associated with this interface. Not null. This reference changes if board is reset.
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
     * @since 1.1.00
     */
    protected boolean gameIsStarting;

    /**
     * Flag to set true if game has been deleted while we're observing it,
     * or was stopped by a server or network error. Is set in {@link #gameDisconnected(boolean, String)}.
     * @since 1.2.01
     */
    protected boolean gameHasErrorOrDeletion;

    /**
     * Optional board layout "visual shift and trim" (Added Layout Part "VS") to use
     * when sizing and laying out the game's {@link SOCBoardPanel}, or {@code null}.
     * @see SOCBoardPanel#getExtraSizeFromBoard(boolean)
     * @since 2.0.00
     */
    private final int[] layoutVS;

    /**
     * this other player has requested a board reset; voting is under way.
     * Null if no board reset vote is under way.
     *
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     * @since 1.1.00
     */
    protected SOCHandPanel boardResetRequester;

    /**
     * Board reset voting: If voting is active and we haven't yet voted,
     * track our dialog; this lets us dispose of it if voting is cancelled.
     * @since 1.1.00
     */
    protected ResetBoardVoteDialog boardResetVoteDia;

    /** Is one or more {@link SOCHandPanel} (of other players) showing a
     *  "Discarding..." or "Picking resource..." message?
     * @since 1.1.00
     */
    private boolean showingPlayerDiscardOrPick;

    /**
     * Synchronize access to {@link #showingPlayerDiscardOrPick}
     * and {@link #showingPlayerDiscardOrPick_task}
     * @since 1.1.00
     */
    private Object showingPlayerDiscardOrPick_lock;

    /**
     * Task reference, in case need to cancel. May be null if not {@link #showingPlayerDiscardOrPick}.
     * @since 1.1.00
     */
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
     * Determined in {@link PILayoutManager}, or null.
     * @since 1.1.11
     */
    private Dimension prevSize;

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

    // Sounds are generated once, then used for all PIs.
    // Theoretically should call Clip.close() on each one when the client exits,
    // but the JVM cleans up enough to cover that.

    /**
     * Sound prompt at start of player's turn (roll or play card).
     * Generated at first call to constructor.
     *<P>
     * Before v2.6.00 this was a {@code byte[]} buffer of raw 16-bit mono PCM data.
     * @since 1.2.00
     */
    private static Clip SOUND_BEGIN_TURN;

    /**
     * Sound made when a piece is placed.
     * Generated at first call to constructor.
     *<P>
     * Before v2.6.00 this was a {@code byte[]} buffer of raw 16-bit mono PCM data.
     * @since 1.2.00
     */
    private static Clip SOUND_PUT_PIECE;

    /**
     * Alert chime for when resources have been stolen or player must choose which ones to discard.
     * Generated at first call to constructor.
     *<P>
     * Before v2.6.00 this was a {@code byte[]} buffer of raw 16-bit mono PCM data.
     * @since 1.2.00
     */
    static Clip SOUND_RSRC_LOST;

    /**
     * Alert chime for when free resources are gained from a gold hex or a scenario-specific event.
     * Generated at first call to constructor.
     *<P>
     * Before v2.6.00 this was a {@code byte[]} buffer of raw 16-bit mono PCM data.
     * @since 2.0.00
     */
    static Clip SOUND_RSRC_GAINED_FREE;

    /**
     * Sound prompt when trade is offered to client player.
     * Generated at first call to constructor.
     *<P>
     * Before v2.6.00 this was a {@code byte[]} buffer of raw 16-bit mono PCM data.
     * @since 1.2.01
     */
    static Clip SOUND_OFFERED_TRADE;

    private final ClientBridge clientListener;

    /**
     * Thread executor to queue and play {@link #playSound(byte[])} using {@link PIPlaySound}s.
     * @since 1.2.00
     */
    private static final ExecutorService soundQueueThreader = Executors.newSingleThreadExecutor();

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
     * Add one hotkey's bindings to an {@link InputMap}.
     * Hotkey shortcuts always respond to Ctrl + letter, and also Cmd on MacOSX or Alt on Windows.
     * On Windows, also calls {@link JButton#setMnemonic(int) btn.setMnemonic(vkChar)}.
     * @param im  InputMap to add to
     * @param vkChar  Unmasked key to use, like {@link KeyEvent#VK_R}
     * @param eventStr  Unique event to pair InputMap to ActionMap, like {@code "hotkey_roll"}
     * @param btn  Button, to call {@link JButton#setMnemonic(int)} for Alt + {@code vkChar};
     *     {@code null} if there's no associated button and on Windows this method should
     *     directly add the mapping with {@link InputEvent#ALT_DOWN_MASK}
     * @see #removeHotkeysInputMap_one(InputMap, int)
     * @since 2.3.00
     */
    /* package */ static void addHotkeysInputMap_one
        (final InputMap im, final int vkChar, final String eventStr, final JButton btn)
    {
        im.put(KeyStroke.getKeyStroke(vkChar, InputEvent.CTRL_DOWN_MASK), eventStr);

        if (SOCPlayerClient.IS_PLATFORM_WINDOWS)
        {
            // also respond to Alt on win32/win64;
            // setMnemonic works only on the Windows L&F; does nothing on MacOSX for Cmd
            if (btn != null)
                btn.setMnemonic(vkChar);
            else
                im.put(KeyStroke.getKeyStroke(vkChar, InputEvent.ALT_DOWN_MASK), eventStr);
        } else if (SOCPlayerClient.IS_PLATFORM_MAC_OSX) {
            // also respond to Cmd on MacOSX
            im.put(KeyStroke.getKeyStroke(vkChar, InputEvent.META_DOWN_MASK), eventStr);
        }
    }

    /**
     * Remove one hotkey's bindings from an {@link InputMap}.
     * Will clear bindings for Ctrl + letter, and also Cmd on MacOSX or Alt on Windows,
     * by setting their action to {@code "none"}.
     * @param im  InputMap to remove from
     * @param vkChar  Unmasked key to use, like {@link KeyEvent#VK_R}
     * @see #addHotkeysInputMap_one(InputMap, int, String, JButton)
     * @since 2.3.00
     */
    /* package */ static void removeHotkeysInputMap_one
        (final InputMap im, final int vkChar)
    {
        KeyStroke[] ksMods = new KeyStroke[2];
        ksMods[0] = KeyStroke.getKeyStroke(vkChar, InputEvent.CTRL_DOWN_MASK);
        if (SOCPlayerClient.IS_PLATFORM_WINDOWS)
            ksMods[1] = KeyStroke.getKeyStroke(vkChar, InputEvent.ALT_DOWN_MASK);
        else if (SOCPlayerClient.IS_PLATFORM_MAC_OSX)
            ksMods[1] = KeyStroke.getKeyStroke(vkChar, InputEvent.META_DOWN_MASK);

        for (final KeyStroke ks : ksMods)
            if (ks != null)
                im.put(ks, "none");
    }

    /**
     * Create and show a new player interface.
     * If the game options have a {@link SOCScenario} description, it will be shown now in a popup
     * by {@link #showScenarioInfoDialog()}.
     *
     * @param title  title for this interface - game name
     * @param md     the client main display that spawned us
     * @param ga     the game associated with this interface; must not be {@code null}
     * @param layoutVS  Optional board layout "visual shift and trim" (Added Layout Part "VS")
     *     to use when sizing and laying out the new game's {@link SOCBoardPanel}, or {@code null}
     * @param localPrefs  optional map of per-game local preferences to use in this {@code SOCPlayerInterface},
     *     or {@code null}. Preference name keys are {@link #PREF_SOUND_MUTE}, etc.
     *     Values for boolean prefs should be {@link Boolean#TRUE} or {@code .FALSE}.
     *     If provided in the Map, value for {@link SOCPlayerClient#PREF_BOT_TRADE_REJECT_SEC}
     *     (positive or negative) is used to call {@link #setBotTradeRejectSec(int)}.
     * @throws IllegalArgumentException if a {@code localPrefs} value isn't the expected type
     *     ({@link Integer} or {@link Boolean}) based on its key's javadoc.
     */
    public SOCPlayerInterface
        (String title, MainDisplay md, SOCGame ga, final int[] layoutVS, final Map<String, Object> localPrefs)
        throws IllegalArgumentException
    {
        super(strings.get("interface.title.game", title)
              + (ga.isPractice ? "" : " [" + md.getClient().getNickname(false) + "]"));
            // "Settlers of Catan Game: {0}"

        layoutNotReadyYet = true;  // will set to false at end of layoutContainer
        setResizable(true);
        setLocationByPlatform(true);  // cascade, not all same hard-coded position as in v1.1.xx

        this.mainDisplay = md;
        // set displayScale from NGOF if pref is there, otherwise from MainDisplay startup
        {
            int ds = 0;
            Object pref = (localPrefs != null) ? (localPrefs.get(SOCPlayerClient.PREF_UI_SCALE_FORCE)) : null;
            if ((pref != null) && (pref instanceof Integer))
                ds = ((Integer) pref).intValue();
            displayScale = ((ds > 0) && (ds <= 3)) ? ds : md.getDisplayScaleFactor();
        }
        client = md.getClient();
        game = ga;
        game.setGameEventListener(this);
        is6player = (game.maxPlayers > 4);
        isGameFullyObservable = game.isGameOptionSet(SOCGameOptionSet.K_PLAY_FO);
        isGameObservableVP = isGameFullyObservable || game.isGameOptionSet(SOCGameOptionSet.K_PLAY_VPO);

        knowsGameState = (game.getGameState() != 0);
        this.layoutVS = layoutVS;
        gameStats = new SOCGameStatistics(game);
        clientListener = createClientListenerBridge();

        gameIsStarting = false;
        clientHand = null;
        clientHandPlayerNum = -1;

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
            highContrastBorderColor = null;
            getContentPane().setBackground(Color.BLACK);
            getContentPane().setForeground(Color.WHITE);
            highContrastBorders = null;
        } else {
            final Color[] sysColors = SwingMainDisplay.getForegroundBackgroundColors(false, true);
            highContrastBorderColor = sysColors[0];
            highContrastBorders = new ColorSquare[game.maxPlayers];
        }
        setFont(new Font("SansSerif", Font.PLAIN, 10 * displayScale));

        /** we're doing our own layout management */
        setLayout(new PILayoutManager());

        initUIElements(true);

        /**
         * more initialization stuff
         */

        textInputHistory = new ArrayList<String>();
        textInputHistory.add("");

        final Dimension boardExtraSize = boardPanel.getExtraSizeFromBoard(false);
            // add to minimum size, to make enough room for board height, width, layoutVS
            // use unscaled board-internal pixels, to simplify assumptions at this early part of init/layout setup

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
                prefWidth *= displayScale;
                prefHeight *= displayScale;

                if ((width_base != (WIDTH_MIN_4PL * displayScale))
                    || (height_base != (HEIGHT_MIN_4PL * displayScale)))
                {
                    // Pref size is based on minimum board size, but this game's board is bigger.
                    // This "scale-up" calc is the reverse of the one in frameResizeDone which scales down for
                    // getExtraSizeFromBoard; if you change it here, change it there too.
                    // (Unlike in frameResizeDone, this calc needs to apply displayScale)

                    piWidth = prefWidth + (boardExtraSize.width * displayScale);
                    piHeight = prefHeight + (boardExtraSize.height * displayScale);
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

        widthOrig = piWidth;
        heightOrig = piHeight;
        wasResized = false;
        setSize(piWidth, piHeight);
        revalidate();
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
                    byte[] buf = Sounds.genChime(Sounds.NOTE_A5_HZ, 160, .38);
                    SOUND_BEGIN_TURN = Sounds.toClip(buf);

                    buf = new byte[Sounds.bufferLen(60)];
                    Sounds.genChime(140, 60, .15, buf, 0, false);
                    Sounds.genChime(160, 50, .15, buf, 0, true);
                    Sounds.genChime(240, 30, .2, buf, 0, true);
                    SOUND_PUT_PIECE = Sounds.toClip(buf);

                    buf = new byte[Sounds.bufferLen(120 + 90)];
                    int i = Sounds.genChime(Sounds.NOTE_E4_HZ, 120, .9, buf, 0, false);
                    Sounds.genChime(Sounds.NOTE_C4_HZ, 90, .9, buf, i, false);
                    SOUND_RSRC_LOST = Sounds.toClip(buf);

                    buf = new byte[Sounds.bufferLen(120 + 90)];
                    i = Sounds.genChime(Sounds.NOTE_C4_HZ, 120, .9, buf, 0, false);
                    Sounds.genChime(Sounds.NOTE_E4_HZ, 90, .9, buf, i, false);
                    SOUND_RSRC_GAINED_FREE = Sounds.toClip(buf);

                    buf = new byte[Sounds.bufferLen(120 + 120)];
                    i = Sounds.genChime(Sounds.NOTE_B5_HZ, 120, .4, buf, 0, false);
                    Sounds.genChime(Sounds.NOTE_B5_HZ, 120, .4, buf, i, false);
                    SOUND_OFFERED_TRADE = Sounds.toClip(buf);
                }
            });

        /**
         * init is almost complete - when window appears and PILayoutManager.layoutContainer(..) is called,
         * it will reset mouse cursor from WAIT_CURSOR to normal (WAIT_CURSOR is
         * set in SOCPlayerClient.startPracticeGame or SwingMainDisplay.guardedActionPerform).
         * Then, if the game has any scenario description, it will be shown once in a popup
         * via showScenarioInfoDialog().
         */
    }

    /**
     * Factory method to create a new {@link ClientBridge}.
     * Third-party clients can use this to extend SOCPlayerInterface.
     * Is called during early part of construction, so most PI fields won't be initialized yet.
     * @return a new {@link ClientBridge} for this PlayerInterface
     * @since 2.5.00
     */
    protected ClientBridge createClientListenerBridge()
    {
        return new ClientBridge(this);
    }

    /**
     * Provide access to the client listener in case this class does not directly implement it.
     */
    public PlayerClientListener getClientListener()
    {
        return clientListener;
    }

    /**
     * Set up the user interface elements. Called for initial setup and board reset.
     * Creates all new components, adds them to layout.
     *<P>
     * Before v2.2.00 this method was {@code initInterfaceElements}.
     *
     * @param firstCall First setup call for this window; do global things
     *   such as windowListeners, not just component-specific things.
     */
    protected void initUIElements(final boolean firstCall)
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
        textComponentAddClipboardContextMenu(textDisplay);

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
        textComponentAddClipboardContextMenu(chatDisplay);

        textInput = new JTextField();
        if (SOCPlayerClient.IS_PLATFORM_MAC_OSX)
        {
            int px = displayScale;  // based on 1-pixel border
            textInput.setBorder(new EmptyBorder(px, px, px, px));  // avoid black background inside overly-thick border
        }
        textInput.setFont(sans10Font);
        textInputListener = new SOCPITextfieldListener(this);
        textInputHasSent = false;
        textInputHistoryBrowsePos = 0;  // reset position, keep contents
        textInputGreyCountdown = textInputGreyCountFrom;
        textInput.addKeyListener(textInputListener);
        textInput.getDocument().addDocumentListener(textInputListener);
        textInput.addFocusListener(textInputListener);

        FontMetrics fm = this.getFontMetrics(textInput.getFont());
        textInput.setSize(SOCBoardPanel.PANELX, fm.getHeight() + 4 * displayScale);
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
        boardPanel = new SOCBoardPanel(this, layoutVS);
        boardPanel.setBackground(new Color(63, 86, 139));  // sea blue; briefly visible at start before water tiles are drawn
        boardPanel.setForeground(Color.black);
        boardPanel.setFont(sans10Font);
        Dimension bpMinSz = boardPanel.getMinimumSize();
        boardPanel.setSize(bpMinSz.width * displayScale, bpMinSz.height * displayScale);
        add(boardPanel);
        if (game.isGameOptionDefined("PL"))
        {
            updatePlayerLimitDisplay(true, false, -1);
                // Player data may not be received yet;
                // game is created empty, then SITDOWN messages are received from server.
                // gameState is at default 0 (NEW) during JOINGAMEAUTH and SITDOWN.
                // initUIElements is also called at board reset.
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

        if (highContrastBorders != null)
        {
            final Dimension dim4x4 = new Dimension(4, 4);
            for (int i = 0; i < game.maxPlayers; ++i)
            {
                ColorSquare sq = new ColorSquare(highContrastBorderColor);
                sq.setBorderColor(highContrastBorderColor);
                sq.setMinimumSize(dim4x4);
                highContrastBorders[i] = sq;
                add(sq);
            }
        }

        if (firstCall)
        {
            // If player requests window close, ask if they're sure, leave game if so
            addWindowListener(new PIWindowAdapter(mainDisplay, this));
        }

    }

    /**
     * Add client player hotkey bindings to PI's InputMap and ActionMap.
     * Because PI itself isn't a Swing component, we use JPanel {@link #buildingPanel}
     * which may one day get its own hotkeys. Also overrides {@link #textInput}'s Ctrl-A
     * to keep its functionality or accept the trade offer if all text is already selected.
     * @since 2.3.00
     */
    private void addHotkeysInputMap()
        throws IllegalStateException
    {
        final PIHotkeyActionListener acceptTrade
            = new PIHotkeyActionListener(PIHotkeyActionListener.ACCEPT);

        final ActionMap am = buildingPanel.getActionMap();
        am.put("hotkey_accept", acceptTrade);
        am.put("hotkey_reject", new PIHotkeyActionListener(PIHotkeyActionListener.REJECT));
        am.put("hotkey_counteroffer", new PIHotkeyActionListener(PIHotkeyActionListener.COUNTER));
        if (game.maxPlayers > 4)
            am.put("hotkey_askspecialbuild", new PIHotkeyActionListener(PIHotkeyActionListener.ASK_SPECIAL_BUILD));

        final InputMap im = buildingPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        addHotkeysInputMap_one(im, KeyEvent.VK_A, "hotkey_accept", null);
        addHotkeysInputMap_one(im, KeyEvent.VK_J, "hotkey_reject", null);
        addHotkeysInputMap_one(im, KeyEvent.VK_C, "hotkey_counteroffer", null);
        if (game.maxPlayers > 4)
            addHotkeysInputMap_one(im, KeyEvent.VK_B, "hotkey_askspecialbuild", null);

        textInput.getActionMap().put("hotkey_selectAllOrTradeAccept", new AbstractAction()
        {
            /** If empty of text, or if all text's already selected, accept trade offer instead of re-selecting all. */
            public void actionPerformed(final ActionEvent e)
            {
                String txt = textInput.getText();
                final int L = (txt != null) ? txt.length() : 0;

                if ((L == 0) || ((textInput.getSelectionStart() == 0) && (textInput.getSelectionEnd() == L)))
                    acceptTrade.actionPerformed(e);
                else
                    textInput.selectAll();
            }
        });
        addHotkeysInputMap_one
            (textInput.getInputMap(JComponent.WHEN_FOCUSED),
             KeyEvent.VK_A, "hotkey_selectAllOrTradeAccept", null);
    }

    /**
     * Add context menu to a TextField/TextArea for Select All and Copy to Clipboard.
     * Assumes {@code tfield} is read-only, not editable, so doesn't include Cut or Paste.
     *<P>
     * This menu is useful because the usual keyboard shortcuts (Ctrl-A, Ctrl-C)
     * were claimed by new Trade Offer keyboard shortcuts in v2.3.00.
     * Not needed for {@link #textInput} because that editable field can claim focus,
     * so the standard shortcuts work there.
     *<P>
     * The standard {@link TextArea} already has a menu for this on Windows, but not on MacOSX.
     * {@code TextArea} on Windows ignores this custom popup menu and keeps using that standard one.
     * Other platforms can use this one, they have no such standard menu.
     *
     * @param tfield Textfield to add to, like {@link #chatDisplay} or {@link #textDisplay}
     * @since 2.3.00
     */
    private void textComponentAddClipboardContextMenu(final TextComponent tfield)
    {
        final PopupMenu menu = new PopupMenu();

        MenuItem mi = new MenuItem(strings.get("menu.copy"));  // "Copy"
        mi.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ae)
            {
                try
                {
                    final StringSelection data = new StringSelection(tfield.getSelectedText());
                    final Clipboard cb = getToolkit().getSystemClipboard();
                    if (cb != null)
                        cb.setContents(data, data);
                } catch (Exception e) {}  // security, or clipboard unavailable
            }
        });
        menu.add(mi);

        mi = new MenuItem(strings.get("menu.select_all"));  // "Select All"
        mi.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ae)
            {
                tfield.selectAll();
                tfield.repaint();
            }
        });
        menu.add(mi);

        tfield.add(menu);
        tfield.addMouseListener(new MouseAdapter()
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
                menu.show(tfield, e.getX(), e.getY());
            }
        });
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
     * Get the game displayed in this PlayerInterface. This reference changes if board is reset.
     * @return the game associated with this interface; not null
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
     * If &gt; 0, {@link TradePanel} will start the countdown when any robot offers a trade.
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
     * @since 1.1.00
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
     * @since 1.1.00
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
     *<P>
     * This method also gets called once after constructor and initial PILayoutManager.layoutContainer,
     * even though the user hasn't manually resized the window.
     *
     * @since 1.2.00
     */
    private void frameResizeDone()
    {
        final Dimension siz = getSize();
        int w = siz.width, h = siz.height;

        if (! wasResized)
        {
            if ((w == widthOrig) && (h == heightOrig))
            {
                return;  // <--- user hasn't changed it yet ---
            }

            wasResized = true;
        }

        // This "scale-down" calc is the reverse of the one in constructor which scales up for
        // getExtraSizeFromBoard; if you change it here, change it there too

        Dimension boardExtraSize = boardPanel.getExtraSizeFromBoard(true);
        w -= boardExtraSize.width;
        h -= boardExtraSize.height;

        if ((w < 100) || (h < 100))
            return;  // sanity check

        UserPreferences.putPref(SOCPlayerClient.PREF_PI__WIDTH, w / displayScale);
        UserPreferences.putPref(SOCPlayerClient.PREF_PI__HEIGHT, h / displayScale);
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
     * @since 1.1.00
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
     * If the player's hand panel is showing a message
     * (not a trade offer), clear and hide that message.
     *<P>
     * Before v2.0.00 this method was {@code clearTradeMsg}.
     *
     * @param pn  Player number, or -1 for all players
     * @see SOCHandPanel#hideMessage()
     * @since 1.1.12
     */
    void hideHandMessage(final int pn)
    {
        if (pn != -1)
            hands[pn].hideMessage();
        else
            for (int i = 0; i < game.maxPlayers; ++i)
                hands[i].hideMessage();
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
            if (clientHand != null)
                clientHand.updateAtOurGameState();
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
     * @see #isClientCurrentPlayer()
     * @see #isClientPlayer(SOCPlayer)
     * @see #getClientPlayer()
     * @see #getClientPlayerNumber()
     * @since 1.1.00
     */
    public SOCHandPanel getClientHand()
    {
        return clientHand;
    }

    /**
     * Update the client player's {@link SOCHandPanel} reference, for joining
     * or leaving a game. Also updates {@link #getClientPlayerNumber()}.
     *<P>
     * Called by {@link SOCHandPanel#removePlayer()} and {@link SOCHandPanel#addPlayer(String)}.
     *
     * @param h  The SOCHandPanel for us, or null if none (leaving).
     *     Will update {@link #getClientPlayerNumber()} from
     *     {@link SOCHandPanel#getPlayer() h.getPlayer()}{@link SOCPlayer#getPlayerNumber() .getPlayerNumber()},
     *     or -1 if null.
     * @see #getClientHand()
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
     *<P>
     * Before v2.5.00 this method was {@code clientIsCurrentPlayer()}.
     *
     * @see #getClientPlayerNumber()
     * @see #isClientPlayer(SOCPlayer)
     * @since 1.1.00
     */
    public final boolean isClientCurrentPlayer()
    {
        if (clientHand == null)
            return false;
        else
            return clientHand.isClientAndCurrentPlayer();
    }

    /**
     * Client player's nickname used on the remote/TCP server or practice server,
     * depending on this game's {@link SOCGame#isPractice} flag.
     * Unlike {@link #getClientHand()} or {@link #getClientPlayer()},
     * this return value doesn't change before/after client player sits down.
     * @return Client player's nickname, from {@link SOCPlayerClient#getNickname(boolean)}
     * @since 2.3.00
     */
    public final String getClientNickname()
    {
        return client.getNickname(game.isPractice);
    }

    /**
     * If client player is seated and active in game, their player object.
     * Set by {@link #setClientHand(SOCHandPanel)}.
     * @return Client's player if active, or {@code null}
     * @see #getClientPlayerNumber()
     * @see #getClientHand()
     * @see #getClientNickname()
     * @since 2.3.00
     */
    public final SOCPlayer getClientPlayer()
    {
        return (clientHandPlayerNum >= 0) ? game.getPlayer(clientHandPlayerNum) : null;
    }

    /**
     * If client player is seated and active in game, their player number.
     * Set by {@link #setClientHand(SOCHandPanel)}.
     *
     * @return client's player ID, or -1 if not seated
     * @see #isClientCurrentPlayer()
     * @see #getClientPlayer()
     * @see #getClientHand()
     * @see #getClientNickname()
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

            if (! checkTextCharactersOrPopup(s, mainDisplay, this))
                return;

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
                    textInput.getDocument().removeDocumentListener(textInputListener);
            }

            /**
             * Clear field and send to game at server, or process as local debug command
             */

            textInput.setText("");

            // add to chat history, unless is repeat of previous item
            {
                final int S = textInputHistory.size();
                if ((S == 1) || ! textInputHistory.get(S - 1).equals(s))
                    textInputHistory.add(s);
                textInputHistoryBrowsePos = 0;
            }

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

            if (! doLocalCommand(s))
                client.getGameMessageSender().sendText(game, s);

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
     * Check characters in {@code txt} to see if they're allowed within a message to the server.
     * If not, show a modal {@link NotifyDialog} to let the user know what can't be sent.
     * Currently disallows only {@code '|'}, the message field delimiter ({@link SOCMessage#sep_char}).
     *
     * @param txt  Text to check; may be "" but not null
     * @param md       Player client's main display. Not null, required by NotifyDialog if shown
     * @param parent   Current game's player interface or channel window,
     *                 or another Frame or Dialog for our parent window,
     *                 or null to look for client's main Frame/Dialog as parent,
     *                 for NotifyDialog if shown
     * @return true if OK to send, false if a dialog was shown
     * @since 2.5.00
     */
    public static boolean checkTextCharactersOrPopup
        (final String txt, final MainDisplay md, final Window parent)
    {
        if (txt.indexOf(SOCMessage.sep_char) != -1)
        {
            NotifyDialog.createAndShow
                (md, parent, strings.get("interface.chat.text.no_pipe_symbol"), null, true);
                    // "Chat text can't contain the '|' symbol."
            return false;
        }

        return true;
    }

    /**
     * Handle local client commands for games.
     *<P>
     * Command names, after \ :
     *<UL>
     * <LI> For chat:
     * <UL>
     *  <LI> {@code ignore} <em>playerName</em>
     *  <LI> {@code unignore} <em>playerName</em>
     * </UL>
     * <LI> To pick a board location to send a bot {@code :consider-target} commands:
     * <UL>
     *  <LI> {@code clt-set} <em>robotName</em>
     *  <LI> {@code clt-road} <em>robotName</em>
     *  <LI> {@code clt-ship} <em>robotName</em>
     *  <LI> {@code clt-city} <em>robotName</em>
     * </UL>
     * <LI> To pick a board location to send a bot {@code :consider-move} commands:
     * <UL>
     *  <LI> {@code clm-set} <em>robotName</em>
     *  <LI> {@code clm-road} <em>robotName</em>
     *  <LI> {@code clm-ship} <em>robotName</em>
     *  <LI> {@code clm-city} <em>robotName</em>
     * </UL>
     *</UL>
     * Before 2.0.00 this method was {@code SOCPlayerClient.doLocalCommand(SOCGame, String)}.
     *
     * @param cmd  Local client command string, which starts with \
     * @return true if a command was handled, false if no command name was recognized
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
            doLocalCommand_botConsiderMode
                (cmd.substring(9), SOCBoardPanel.CONSIDER_LM_SETTLEMENT, "clm-set");
            return true;
        }
        else if (cmd.startsWith("\\clm-road "))
        {
            doLocalCommand_botConsiderMode
                (cmd.substring(10), SOCBoardPanel.CONSIDER_LM_ROAD, "clm-road");
            return true;
        }
        else if (cmd.startsWith("\\clm-ship "))
        {
            doLocalCommand_botConsiderMode
                (cmd.substring(10), SOCBoardPanel.CONSIDER_LM_SHIP, "clm-ship");
            return true;
        }
        else if (cmd.startsWith("\\clm-city "))
        {
            doLocalCommand_botConsiderMode
                (cmd.substring(10), SOCBoardPanel.CONSIDER_LM_CITY, "clm-city");
            return true;
        }
        else if (cmd.startsWith("\\clt-set "))
        {
            doLocalCommand_botConsiderMode
                (cmd.substring(9), SOCBoardPanel.CONSIDER_LT_SETTLEMENT, "clt-set");
            return true;
        }
        else if (cmd.startsWith("\\clt-road "))
        {
            doLocalCommand_botConsiderMode
                (cmd.substring(10), SOCBoardPanel.CONSIDER_LT_ROAD, "clt-road");
            return true;
        }
        else if (cmd.startsWith("\\clt-ship "))
        {
            doLocalCommand_botConsiderMode
                (cmd.substring(10), SOCBoardPanel.CONSIDER_LT_SHIP, "clt-ship");
            return true;
        }
        else if (cmd.startsWith("\\clt-city "))
        {
            doLocalCommand_botConsiderMode
                (cmd.substring(10), SOCBoardPanel.CONSIDER_LT_CITY, "clt-city");
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Handle {@link #doLocalCommand(String)} bot-debugging "consider" modes for {@link SOCBoardPanel}:
     * {@link SOCBoardPanel#CONSIDER_LM_SETTLEMENT}, {@link SOCBoardPanel#CONSIDER_LT_ROAD}, etc.
     * Gives feedback that mode has been set or {@code botPlName} not found in game.
     *
     * @param botPlName  Bot name to look for in game; will be trimmed
     * @param mode   {@link SOCBoardPanel} mode to set
     * @param modeNameKey  Mode name string key suffix to use in feedback: "clm-road", "clt-set", etc.
     *     For string lookup, will be prefixed with "interface.debug.bot.".
     * @since 2.0.00
     */
    private void doLocalCommand_botConsiderMode
        (String botPlName, final int mode, final String modeNameKey)
    {
        botPlName = botPlName.trim();

        SOCPlayer pl = game.getPlayer(botPlName.trim());
        if (pl != null)
        {
            boardPanel.setOtherPlayer(pl);
            boardPanel.setMode(mode);

            String modeName;
            try
            {
                modeName = strings.get("interface.debug.bot." + modeNameKey);
                    // interface.debug.bot.clt-set -> ":consider-target(settlement)"
            } catch(MissingResourceException e) {
                modeName = modeNameKey;
            }
            printKeyed("interface.debug.bot.mode_prompt", modeName, botPlName);
                // "{0} mode for {1}: Click to indicate piece location."
        } else {
            printKeyed("interface.debug.bot.not_found", botPlName);
                // "Can't find a player named {0}"
        }
    }

    /**
     * Leave this game and close this window.
     */
    public void leaveGame()
    {
        mainDisplay.leaveGame(game);
        if (clientHand != null)
            clientHand.removePlayer();  // cleanup, possibly close open non-modal dialogs, etc
        client.getGameMessageSender().leaveGame(game);

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
            client.getGameMessageSender().resetBoardRequest(game);
        else
            textDisplay.append("*** " + strings.get("reset.you.may.ask.once") + "\n");
                // "You may ask only once per turn to reset the board."
    }

    /**
     * Another player has voted on a board reset request.
     * Show the vote.
     * @since 1.1.00
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
     * @since 1.1.00
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
     * @since 1.1.00
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
            D.ebugPrintlnINFO("resetBoardAskVote: Cannot: " + re);
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
     * @since 1.1.00
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
     * Uses {@link PIPlaySound} to call {@link Clip#setFramePosition(int)} and {@link Clip#start()}.
     * No sound is played if preference {@link SOCPlayerClient#PREF_SOUND_ON} is false
     * or if {@link #isSoundMuted()}.
     *<P>
     * Playback uses a queuing thread executor, not the AWT {@link EventQueue}.
     *
     * @param c  Clip for sound to play, or null to do nothing.
     *     Can be generated by methods like {@link Sounds#genChime(int, int, double)}.
     * @since 1.2.00
     */
    public void playSound(final Clip c)
    {
        if (c != null)
            soundQueueThreader.submit(new PIPlaySound(c));
    }

    /**
     * Game was deleted or a server/network error occurred; stop playing.
     *<P>
     * Before v2.4.00 this method was called {@code over(..)}.
     *
     * @param wasDeleted  True if game was deleted, isn't from an error;
     *     this can happen while observing a game
     * @param errorMessage  Error message if any, or {@code null}
     */
    public void gameDisconnected(final boolean wasDeleted, final String errorMessage)
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
     *<P>
     * Should be called from event dispatch thread, using {@link EventQueue#invokeLater(Runnable)} if needed.
     *
     * @param members Game member names from {@link soc.message.SOCGameMembers#getMembers()} (added in 1.1.12)
     */
    public void began(final List<String> members)
    {
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
            if (mname.equals(getClientNickname()))
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
     * A player has sat down to play. Update the display:
     *<UL>
     * <LI> Calls {@link SOCHandPanel#addPlayer(String)} which does additional actions if that
     *     player is the client (not a different human or robot), including a call back up
     *     to {@link #setClientHand(SOCHandPanel)}.
     * <LI> Calls {@link SOCBoardPanel#setPlayer()} and {@link SOCBuildingPanel#setPlayer()}
     *     if being called for client player (based on {@code name}).
     * <LI> Updates {@link SOCHandPanel}'s displayed values.
     *</UL>
     *
     * @param name   the name of the player. Checks if is client player by calling {@link #getClientNickname()}.
     * @param pn  the seat number of the player
     * @see #removePlayer(int)
     */
    public void addPlayer(final String name, final int pn)
    {
        hands[pn].addPlayer(name);  // This will also update all other hands' buttons ("sit here" -> "lock", etc)

        final boolean sitterIsClientPlayer = (name.equals(getClientNickname()));

        if (sitterIsClientPlayer)
        {
            for (int i = 0; i < game.maxPlayers; i++)
                if (game.getPlayer(i).isRobot())
                    hands[i].addSittingRobotLockBut();

            if (is6player)
            {
                // handpanel sizes change when client sits
                // in a 6-player game.
                getContentPane().revalidate();
                repaint(hands[pn].getX(), 0, hands[pn].getWidth(), getHeight());
                    // must repaint entire column's handpanels and wide borders
            }

            addHotkeysInputMap();
        }

        if (game.isGameOptionDefined("PL"))
            updatePlayerLimitDisplay(true, false, -1);

        if (game.isBoardReset())
        {
            // Retain face after reset
            hands[pn].changeFace(hands[pn].getPlayer().getFaceId());
        }

        /**
         * if client sat down, let the board panel & building panel find our player object
         */
        if (sitterIsClientPlayer)
        {
            boardPanel.setPlayer();
            buildingPanel.setPlayer();
        }

        if (is6player)
            buildingPanel.updatePlayerCount();

        /**
         * update the hand panel's displayed values
         */
        final SOCHandPanel hp = getPlayerHandPanel(pn);
        hp.updateValue(PlayerClientListener.UpdateType.Road);
        hp.updateValue(PlayerClientListener.UpdateType.Settlement);
        hp.updateValue(PlayerClientListener.UpdateType.City);
        if (game.hasSeaBoard)
            hp.updateValue(PlayerClientListener.UpdateType.Ship);
        hp.updateValue(PlayerClientListener.UpdateType.Knight);
        hp.updateValue(PlayerClientListener.UpdateType.VictoryPoints);
        hp.updateValue(PlayerClientListener.UpdateType.LongestRoad);
        hp.updateValue(PlayerClientListener.UpdateType.LargestArmy);

        if (sitterIsClientPlayer)
        {
            hp.updateValue(PlayerClientListener.UpdateType.ResourceTotalAndDetails);
            hp.updateDevCards(false);
        } else {
            hp.updateValue(PlayerClientListener.UpdateType.Resources);
            hp.updateValue(PlayerClientListener.UpdateType.DevCards);
        }
    }

    /**
     * remove a player from the game.
     * Updates panes and displays, does not print any message
     * about the player leaving the game.
     *<P>
     * To prevent inconsistencies, call this <em>before</em> calling
     * {@link SOCGame#removePlayer(String, boolean)}.
     *
     * @param pn the number of the player
     * @see #addPlayer(String, int)
     */
    public void removePlayer(int pn)
    {
        hands[pn].removePlayer();  // May also clear clientHand
        if (game.isGameOptionDefined("PL"))
            updatePlayerLimitDisplay(true, false, pn);
        else
            hands[pn].addSitButton(clientHand != null);  // Is the client player already sitting down at this game?

        if (is6player)
        {
            buildingPanel.updatePlayerCount();

            if (clientHand == null)
            {
                // handpanel sizes change when client leaves in a 6-player game
                getContentPane().revalidate();
                repaint();
            }
        }
    }

    // This javadoc also appears in PlayerClientListener; please also update there if it changes.
    /**
     * Is a dialog or popup message currently visible while gameplay continues?
     * See {@link PlayerClientListener} interface javadoc for details and implications.
     * @since 2.0.00
     */
    public boolean isNonBlockingDialogVisible()
    {
        return (nbdForEvent != null) && nbdForEvent.isVisible();
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
     * for each player. Update game data and refresh the display:
     * see {@link PlayerClientListener#gameEnded(Map)}.
     *
     * @param finalScores  Final score for each player position; length should be {@link SOCGame#maxPlayers}
     * @since 1.1.00
     */
    public void updateAtOver(final int[] finalScores)
    {
        if (game.getGameState() != SOCGame.OVER)
        {
            System.err.println("L1264: pi.updateAtOver called at state " + game.getGameState());
            return;
        }

        for (int pn = 0; pn < finalScores.length; ++pn)
            game.getPlayer(pn).forceFinalVP(finalScores[pn]);
        if (null == game.getPlayerWithWin())
        {
            game.checkForWinner();  // Assumes "current player" set to winner already, by SETTURN msg
        }
        for (int pn = 0; pn < finalScores.length; ++pn)
            hands[pn].updateValue(PlayerClientListener.UpdateType.VictoryPoints);  // Also disables buttons, etc.

        // reveal each player's VP cards
        for (int pn = 0; pn < finalScores.length; ++pn)
        {
            final SOCPlayer pl = game.getPlayer(pn);
            final List<SOCInventoryItem> vpCards = pl.getInventory().getByState(SOCInventory.KEPT);
            if (! vpCards.isEmpty())
                printKeyedSpecial("game.end.player.has.vpcards", pl.getName(), vpCards);
                    // "Joe has a Gov.House (+1VP) and a Market (+1VP)"
        }

        setTitle(strings.get("interface.title.game.over", game.getName()) +
                 (game.isPractice ? "" : " [" + getClientNickname() + "]"));
                // "Settlers of Catan Game Over: {0}"

        boardPanel.updateMode();
        repaint();
    }

    /**
     * Game's current player and state has changed: Update displays.
     * Called after game data has been updated.
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

        if ((game.getGameState() == SOCGame.SPECIAL_BUILDING)
            && (client.getServerVersion(game) >= SOCTurn.VERSION_FOR_FLAG_CLEAR_AND_SBP_TEXT))
            printKeyed("action.sbp.turn.to.place.common", game.getPlayer(game.getCurrentPlayerNumber()).getName());
                // "Special building phase: {0}''s turn to place."

        // play Begin Turn sound here, not updateAtRollPrompt() which
        // isn't called for first player during initial placement
        if (isClientCurrentPlayer())
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

        if (isClientCurrentPlayer() && ! clientListener.isNonBlockingDialogVisible())
            getClientHand().autoRollOrPromptPlayer();
    }

    /**
     * Let client player know the server has declined their request or requested action.
     * @param reasonCode  Reason the request was declined:
     *     {@link SOCDeclinePlayerRequest#REASON_NOT_NOW}, {@link SOCDeclinePlayerRequest#REASON_NOT_YOUR_TURN}, etc
     * @param detailValue1  Optional detail, may be used by some {@code reasonCode}s
     * @param detailValue2  Optional detail, may be used by some {@code reasonCode}s
     * @param reasonText  Optional localized reason text, or {@code null} to print text based on {@code reasonCode}
     * @since 2.5.00
     */
    private void showDeclinedPlayerRequest
        (final int reasonCode, final int detailValue1,  final int detailValue2, final String reasonText)
    {
        if (reasonText != null)
        {
            print("* " + reasonText);
            return;
        }

        final String reasonTextKey;
        switch(reasonCode)
        {
        case SOCDeclinePlayerRequest.REASON_NOT_THIS_GAME:
            reasonTextKey = "reply.common.cannot.in_this_game";  // "You can't do that in this game."
            break;

        case SOCDeclinePlayerRequest.REASON_NOT_YOUR_TURN:
            reasonTextKey = "base.reply.not.your.turn";  // "It's not your turn."
            break;

        case SOCDeclinePlayerRequest.REASON_LOCATION:
            reasonTextKey = "reply.common.cannot.at_that_location";  // "You can't do that at that location."
            break;

        default:
            reasonTextKey = "reply.common.cannot.right_now";  // "You can't do that right now."
        }
        printKeyed(reasonTextKey);
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

        buildingPanel.updateButtonStatus();
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
     * Clear contents of the chat input text ("please wait" during setup, etc).
     * @since 2.5.00
     */
    public void clearChatTextInput()
    {
        textInput.setText("");
        textInputIsInitial = false;
    }

    /**
     * Set or clear the chat text input's initial prompt.
     * If {@code setToInitial} true, sets its status, foreground color, and the prompt text
     * unless player already sent chat text ({@link #textInputHasSent}).
     *<P>
     * Should be called from event dispatch thread, using {@link EventQueue#invokeLater(Runnable)} if needed.
     * This avoids an occasional race bug where prompt is appended twice ("Type here to chat.Type here to chat.").
     *<P>
     * Do not call this directly from a Swing {@link DocumentListener},
     * which will throw "IllegalStateException: Attempt to mutate in notification":
     * Instead add to event queue with {@code invokeLater}.
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
     * This player has just discarded some resources. Player data has been updated.
     * Announce the discard and update displays.
     * @param player  Player discarding resources; not {@code null}
     * @param discards  The known or unknown resources discarded; not {@code null}
     * @since 2.5.00
     */
    public void reportDiscard(final SOCPlayer player, final ResourceSet discards)
    {
        final int pn = player.getPlayerNumber();
        hands[pn].updateValue
            (PlayerClientListener.UpdateType.ResourceTotalAndDetails);

        if (isGameFullyObservable || (discards.getAmount(SOCResourceConstants.UNKNOWN) == 0))
            printKeyedSpecial
                ((pn != clientHandPlayerNum) ? "action.discarded.rsrcs" : "action.discarded.rsrcs.you",
                 player.getName(), discards);
                // "{0} discarded {1,rsrcs}."
                // or "You discarded {1,rsrcs}."
        else
            printKeyedSpecial
                ("action.discarded.total.common", player.getName(), discards.getTotal());
                // "{0} discarded {1} resources."
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
     * @see GameMessageSender#choosePlayer(SOCGame, int)
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
        NewGameOptionsFrame.showScenarioInfoDialog
            (game, ((game.isPractice) ? client.practiceServGameOpts : client.tcpServGameOpts).knownOpts,
             getMainDisplay(), this);
    }

    /**
     * A robbery has just occurred; show result details.
     * Is called after game data has been updated.
     *
     * @param perpPN  Perpetrator's player number, or -1 if none
     *     (used by {@code SC_PIRI} scenario, future use by other scenarios/expansions)
     * @param victimPN  Victim's player number, or -1 if none (for future use by scenarios/expansions)
     * @param resType  Resource type being stolen, like {@link SOCResourceConstants#SHEEP}
     *     or {@link SOCResourceConstants#UNKNOWN}. Ignored if {@code resSet != null} or {@code peType != null}.
     * @param resSet  Resource set being stolen, if not using {@code resType} or {@code peType}
     * @param peType  PlayerElement type such as {@link PEType#SCENARIO_CLOTH_COUNT},
     *     or {@code null} if a resource like sheep is being stolen (use {@code resType} or {@code resSet} instead).
     * @param isGainLose  If true, the amount here is a delta Gained/Lost by players, not a total to Set
     * @param amount  Amount being stolen if {@code isGainLose}, otherwise {@code perpPN}'s new total amount
     * @param victimAmount  {@code victimPN}'s new total amount if not {@code isGainLose}, 0 otherwise
     * @param extraValue  Optional information related to the robbery, or 0; for use by scenarios/expansions
     * @since 2.5.00
     */
    public void reportRobberyResult
        (final int perpPN, final int victimPN, final int resType, SOCResourceSet resSet, final PEType peType,
         final boolean isGainLose, final int amount, final int victimAmount, final int extraValue)
    {
        // These texts are also sent from server SOCGameHandler.reportRobbery to clients older than v2.5.00;
        // if you change the logic or text, make sure it's updated in both places

        final String peName = (perpPN >= 0) ? game.getPlayer(perpPN).getName() : null,
            viName = (victimPN >= 0) ? game.getPlayer(victimPN).getName() : null;

        if ((perpPN == -1) && game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
        {
            // Pirate Fleet attack results

            if (victimPN < 0)
                return;  // unlikely

            if ((amount > 0) || (resSet != null))
            {
                if (victimPN == clientHandPlayerNum)
                {
                    if (resSet == null)
                    {
                        resSet = new SOCResourceSet();
                        resSet.add(amount, resType);
                    }
                    printKeyedSpecial
                        ("action.rolled.sc_piri.you.lost.rsrcs.to.fleet", resSet, extraValue);
                        // "You lost {0,rsrcs} to the pirate fleet (strength {1,number})"
                    playSound(SOUND_RSRC_LOST);
                } else {
                    printKeyed
                        ("action.rolled.sc_piri.player.lost.rsrcs.to.fleet",
                         viName, (resSet != null) ? resSet.getTotal() : amount, extraValue);
                        // "Joe lost 1 resource to pirate fleet attack (strength 3)." or
                        // "Joe lost 3 resources to pirate fleet attack (strength 3)."
                }
            }
            else if (amount == 0)
            {
                printKeyed
                    ((resType == SOCResourceConstants.UNKNOWN)
                         ? "action.rolled.sc_piri.player.won.pick.free"
                         : "action.rolled.sc_piri.player.tied",
                     viName, extraValue);
                    // "{0} won against the pirate fleet (strength {1,number}) and will pick a free resource"
                    // or "{0} tied against the pirate fleet (strength {1,number})."
            }

            hands[victimPN].updateValue(PlayerClientListener.UpdateType.ResourceTotalAndDetails);
        }
        else if (peType == null)
        {
            if ((resType == SOCResourceConstants.UNKNOWN) || (clientHandPlayerNum < 0)
                || ((clientHandPlayerNum != perpPN) && (clientHandPlayerNum != victimPN)))
            {
                if (isGameFullyObservable)
                    printKeyedSpecial
                        ("robber.stole.resource.from.play_fo",  // "{0} stole {2,rsrcs} from {1}."
                         peName, viName, (amount != 1) ? amount : -1, resType);
                else if (amount == 1)
                    printKeyed
                        ("robber.common.stole.resource.from", peName, viName);  // "{0} stole a resource from {1}."
                else
                    printKeyed
                        ("robber.common.stole.resource.from.n",  // "{0} stole {2} resources from {1}."
                         peName, viName, amount);
            } else {
                if (perpPN == clientHandPlayerNum)
                    printKeyedSpecial
                        ("robber.common.you.stole.resource.from",  // "You stole {0,rsrcs} from {2}."
                         (amount != 1) ? amount : -1, resType, viName);
                else
                    printKeyedSpecial
                        ("robber.common.stole.resource.from.you",  // "{0} stole {1,rsrcs} from you."
                         peName, (amount != 1) ? amount : -1, resType);
            }

            if (perpPN >= 0)
                hands[perpPN].updateValue(PlayerClientListener.UpdateType.ResourceTotalAndDetails);
            if (victimPN >= 0)
            {
                hands[victimPN].updateValue(PlayerClientListener.UpdateType.ResourceTotalAndDetails);
                if (victimPN == clientHandPlayerNum)
                    playSound(SOUND_RSRC_LOST);
            }
        } else {
            PlayerClientListener.UpdateType utype = null;

            switch (peType)
            {
            case SCENARIO_CLOTH_COUNT:
                printKeyed("robber.common.stole.cloth.from", peName, viName);  // "{0} stole a cloth from {1}."
                utype = PlayerClientListener.UpdateType.Cloth;
                break;

            default:
                // Nothing else recognized yet
                // Other PETypes may be used in future scenarios/expansions
            }

            if (utype != null)
            {
                if (perpPN >= 0)
                    clientListener.playerElementUpdated
                        (game.getPlayer(perpPN), utype, false, false);
                if (victimPN >= 0)
                    clientListener.playerElementUpdated
                        (game.getPlayer(victimPN), utype, false, (victimPN == clientHandPlayerNum));
            }
        }
    }

    /**
     * Update interface after the server sends us a new board layout.
     * Call after setting game data and board data.
     * Calls {@link SOCBoardPanel#flushBoardLayoutAndRepaint()}.
     * Updates display of board-related counters, such as {@link soc.game.SOCBoardLarge#getCloth()}.
     * Not needed if calling {@link #resetBoard(SOCGame, int, int)}.
     * @since 2.0.00
     */
    public void updateAtNewBoard()
    {
        boardPanel.flushBoardLayoutAndRepaint();
        if (game.isGameOptionSet(SOCGameOptionSet.K_SC_CLVI))
            buildingPanel.updateClothCount();
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
     *
     * @param isForDecline If true, server has sent us a {@link SOCDeclinePlayerRequest};
     *     game state might not have changed since last call to {@code updateAtGameState(..)}.
     * @since 1.1.00
     */
    public void updateAtGameState(boolean isForDecline)
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

        // Update our interface at start of first turn;
        // some server versions don't send non-bot clients a TURN message after the
        // final road/ship is placed (state START2 -> ROLL_OR_CARD).
        if (gameIsStarting && (gs >= SOCGame.ROLL_OR_CARD))
        {
            gameIsStarting = false;
            if (clientHand != null)
                clientHand.updateAtTurn();
        }

        buildingPanel.updateButtonStatus();
        boardPanel.updateMode();
        boardPanel.repaint();

        // Check for placement states (board panel popup, build via right-click)
        if ((gs == SOCGame.PLACING_ROAD) || (gs == SOCGame.PLACING_SETTLEMENT)
            || (gs == SOCGame.PLACING_CITY) || (gs == SOCGame.PLACING_SHIP))
        {
            if (boardPanel.popupExpectingBuildRequest())
                boardPanel.popupFireBuildingRequest();
        }

        if ((gs == SOCGame.PLACING_INV_ITEM) && isClientCurrentPlayer()
            && game.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI))
        {
            printKeyed("game.invitem.sc_ftri.prompt");
                // "You have received this trade port as a gift."
                // "You must now place it next to your coastal settlement which is not adjacent to any existing port."
        }

        // React if waiting for players to discard,
        // or if we were waiting, and aren't anymore.
        if (gs == SOCGame.WAITING_FOR_DISCARDS)
        {
            // Set timer.  If still waiting for discards after 2 seconds,
            // show balloons on-screen. (hands[i].setDiscardOrPickMsg)
            if (! showingPlayerDiscardOrPick)
                discardOrPickTimerSet(true);
        }
        else if ((gs == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                 || (gs == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE))
        {
            // Set timer.  If still waiting for resource picks after 2 seconds,
            // show balloons on-screen. (hands[i].setDiscardOrPickMsg)
            if (! showingPlayerDiscardOrPick)
                discardOrPickTimerSet(false);
        }
        else if (showingPlayerDiscardOrPick)
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
     * For example, when scenario {@link SOCGameOptionSet#K_SC_PIRI}
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
     * Update game data and displays after putting a piece on the board,
     * or moving a ship that was already placed.
     * Places or moves the piece within our {@link SOCGame}
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
        boardPanel.repaint();
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
     * @see #playerEvent(SOCGame, SOCPlayer, SOCPlayerEvent, boolean, Object)
     * @since 2.0.00
     */
    public void gameEvent(final SOCGame ga, final SOCGameEvent evt, final Object detail)
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
     * @param flagsChanged  True if this event changed {@link SOCPlayer#getPlayerEvents()},
     *             {@link SOCPlayer#getSpecialVP()}, or another flag documented for <tt>evt</tt> in
     *             {@link SOCPlayerEvent}
     * @param obj  Object related to the event, or null; documented for <tt>evt</tt> in {@link SOCPlayerEvent}.
     *             Example: The {@link SOCVillage} for {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     * @see #gameEvent(SOCGame, SOCGameEvent, Object)
     * @since 2.0.00
     */
    public void playerEvent(final SOCGame ga, final SOCPlayer pl, final SOCPlayerEvent evt,
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
     *<P>
     * Before v2.0.00 this was {@code discardTimerSet}.
     *
     * @param isDiscard  True for discard, false for picking gold-hex resources
     * @since 1.1.00
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
     *<P>
     * Before v2.0.00 this was {@code discardTimerClear}.
     *
     * @since 1.1.00
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
        int oldGameState = game.getOldGameState();
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

        getContentPane().removeAll();  // old sub-components
        initUIElements(false);  // new sub-components

        // Clear from possible "game over" titlebar
        setTitle(strings.get("interface.title.game", game.getName()) +
                 (game.isPractice ? "" : " [" + getClientNickname() + "]"));
                // "Settlers of Catan Game: {0}"
        boardPanel.debugShowPotentials = boardDebugShow;

        getContentPane().revalidate();
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
     * @since 1.1.00
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

    // @since 1.1.00
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

    /**
     * Is this player shown as the current player?
     * Calls handPanels[{@link SOCPlayer#getPlayerNumber()}]
     * {@link SOCHandPanel#isClientPlayer() .isClientPlayer()}.
     * @param p  Player object; uses only its {@code playerNumber}
     * @return True if {@link SOCHandPanel#isClientPlayer()}
     * @since 2.0.00
     */
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
     * Client Bridge to translate PCL interface to SOCPlayerInterface methods.
     * Added to PI during construction by {@link SOCPlayerInterface#createClientListenerBridge()} factory method.
     * For most methods here, {@link PlayerClientListener} will have their javadoc.
     *
     * @author paulbilnoski
     * @since 2.0.00
     */
    protected static class ClientBridge implements PlayerClientListener
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

        public SOCGame getGame()
        {
            return pi.getGame();
        }

        public int getClientPlayerNumber()
        {
            return pi.getClientPlayerNumber();
        }

        public boolean isClientCurrentPlayer()
        {
            return pi.isClientCurrentPlayer();
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
                    (SOCPlayerInterface.strings.getSpecial
                        (ga, "game.playername.gets.resources.common", pl.getName(), rsrc.get(p)));
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

        /**
         * {@inheritDoc}
         *<P>
         * Calls {@link SOCPlayerInterface#addPlayer(String, int)}.
         */
        public void playerSitdown(final int playerNumber, final String sitterNickname)
        {
            pi.addPlayer(sitterNickname, playerNumber);
        }

        /**
         * Game's current player and state has changed.
         * Update displays: Repaint board panel, update buttons' status, etc.
         * (Caller has already called {@link SOCGame#setGameState(int)}, {@link SOCGame#setCurrentPlayerNumber(int)},
         * {@link SOCGame#updateAtTurn()}.)
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

        public void playerPickedResources
            (final SOCPlayer player, final SOCResourceSet resSet, final int reasonCode)
        {
            final String key;
            switch (reasonCode)
            {
            case SOCPickResources.REASON_GENERIC:
                key = "action.picked.rsrcs";  // "{0} has picked {1,rsrcs}."
                break;

            case SOCPickResources.REASON_DISCOVERY:
                key = "action.card.discov.received";  // "{0} received {1,rsrcs} from the bank."
                break;

            case SOCPickResources.REASON_GOLD_HEX:
                key = "action.picked.rsrcs.goldhex";  // "{0} has picked {1,rsrcs} from the gold hex."
                break;

            default:
                return;
            }

            pi.printKeyedSpecial(key, player.getName(), resSet);
            pi.getPlayerHandPanel(player.getPlayerNumber())
                .updateValue(PlayerClientListener.UpdateType.ResourceTotalAndDetails);
        }

        public void playerElementUpdated
            (final SOCPlayer player, final PlayerClientListener.UpdateType utype,
             final boolean isGoodNews, final boolean isBadNews)
        {
            final SOCHandPanel hpan = (player == null) ? null : pi.getPlayerHandPanel(player.getPlayerNumber());
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

            case ResourceTotalAndDetails:
                // avoid default-case warning print; is handled below like Clay, Ore, Sheep, Wheat, Wood
                break;

            case VictoryPoints:
                hpan.updateValue(PlayerClientListener.UpdateType.VictoryPoints);
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
                    pi.buildingPanel.updateClothCount();
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

            final boolean isClientPlayer = hpan.isClientPlayer();

            if ((hpanUpdateRsrcType != 0) || (utype == PlayerClientListener.UpdateType.ResourceTotalAndDetails))
            {
                if (isClientPlayer || pi.isGameFullyObservable)
                {
                    hpan.updateValue(utype);

                    if (isClientPlayer)
                    {
                        // Because client player's available resources have changed,
                        // update Building panel and any trade offers currently showing (show or hide Accept button)
                        pi.updateAtClientPlayerResources();

                        // If good or bad news from unexpectedly gained or lost
                        // resources or pieces, let the player know
                        if (isGoodNews)
                            pi.playSound(SOUND_RSRC_GAINED_FREE);
                        else if (isBadNews)
                            pi.playSound(SOUND_RSRC_LOST);
                    }
                } else {
                    hpan.updateValue
                        ((utype == PlayerClientListener.UpdateType.ResourceTotalAndDetails)
                         ? utype
                         : PlayerClientListener.UpdateType.Resources);
                }
            }

            if (isClientPlayer && (pi.getGame().getGameState() != SOCGame.NEW))
                pi.buildingPanel.updateButtonStatus();
        }

        public void requestedSpecialBuild(SOCPlayer player)
        {
            if (player.hasAskedSpecialBuild())
                pi.printKeyed("game.sbp.wants.to", player.getName());  // * "{0} wants to Special Build."
            if (pi.isClientPlayer(player))
                pi.buildingPanel.updateButtonStatus();
        }

        public void requestedGoldResourceCountUpdated(SOCPlayer player, int countToPick)
        {
            final SOCHandPanel hpan = pi.getPlayerHandPanel(player.getPlayerNumber());
            hpan.updatePickGoldHexResources();
        }

        public void playerDevCardsUpdated(SOCPlayer player, final boolean addedPlayable)
        {
            final SOCHandPanel hpan = pi.getPlayerHandPanel(player.getPlayerNumber());
            if (pi.isGameObservableVP || pi.isClientPlayer(player))
            {
                hpan.updateDevCards(addedPlayable);
                hpan.updateValue(PlayerClientListener.UpdateType.VictoryPoints);
            }
            else if (player != null)
            {
                hpan.updateDevCards(addedPlayable);
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

        public void playerStats(final int statsType, final int[] stats)
        {
            if (statsType != SOCPlayerStats.STYPE_TRADES)
                return;  // unrecognized type

            pi.printKeyed("game.trade.stats.heading");
                // "Your trade stats: Give (clay, ore, sheep, wheat, wood) -> Get (clay, ore, sheep, wheat, wood):"
            final String[] statLabels =
                {
                    "game.port.three",  // "3:1 Port"
                    "game.port.clay", "game.port.ore", "game.port.sheep", // "2:1 Clay port", Ore, Sheep,
                    "game.port.wheat",  "game.port.wood",  // Wheat, "2:1 Wood port"
                    "game.trade.stats.bank",  // "4:1 Bank"
                    "game.trade.stats.with_players"  // "All trades with players"
                };
            final int subLen = stats[1];
            int numTypes = (stats.length - 2) / subLen;
            if (numTypes > statLabels.length)
                numTypes = statLabels.length;  // just in case; shouldn't occur
            int si = 2;  // index just after subLen
            StringBuilder sb = new StringBuilder();
            for (int ttype = 0; ttype < numTypes; ++ttype)
            {
                sb.append("* ").append(strings.get(statLabels[ttype])).append(": (");
                for (int res = 0; res < 5; ++res, ++si)
                {
                    if (res > 0)
                        sb.append(", ");
                    sb.append(stats[si]);
                }
                sb.append(") -> (");
                for (int res = 0; res < 5; ++res, ++si)
                {
                    if (res > 0)
                        sb.append(", ");
                    sb.append(stats[si]);
                }
                sb.append(')');

                pi.print(sb.toString());
                sb.delete(0, sb.length());
            }
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
        public void membersListed(final List<String> names)
        {
            EventQueue.invokeLater(new Runnable()
            {
                public void run()
                { pi.began(names); }
            });
        }

        public void boardLayoutUpdated()
        {
            pi.updateAtNewBoard();
        }

        public void boardUpdated()
        {
            pi.boardPanel.flushBoardLayoutAndRepaint();
        }

        public void pieceValueUpdated(final SOCPlayingPiece piece)
        {
            pi.boardPanel.pieceValueUpdated(piece);
        }

        public void boardPotentialsUpdated()
        {
            pi.boardPanel.flushBoardLayoutAndRepaintIfDebugShowPotentials();
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

        public void gameStarted()
        {
            pi.startGame();
        }

        public void gameStateChanged(int gameState, boolean isForDecline)
        {
            pi.updateAtGameState(isForDecline);
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
            pi.gameDisconnected(wasDeleted, errorMessage);
        }

        public void messageBroadcast(String msg)
        {
            pi.chatPrint("::: " + msg + " :::");
        }

        public void printText(String txt)
        {
            pi.print(txt);
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
                    pi.printKeyed
                        ("game.invitem.port.placed",
                         pi.game.getPlayer(pn).getName(),
                         strings.get(SOCBoard.getPortDescForType(value2, true)));  // "a 3:1 port", "a 2:1 clay port", etc
                        // * "{0} has placed {1}." -> "Joe has placed a 3:1 port."
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
                    if (pi.getClient().getServerVersion(pi.game)
                        >= SOCDevCardAction.VERSION_FOR_BUY_OMITS_GE_DEV_CARD_COUNT)
                        pi.updateDevCardCount();

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
                    pi.printKeyed
                        ("game.invitem.port.picked.up", plName, strings.get(SOCBoard.getPortDescForType(value2, true)));
                        // "{0} has picked up {1} from the board." -> "Joe has picked up a 3:1 port from the board."
                }
                break;

            default:
                // ignore unknown action types
                System.err.println
                    ("PI.simpleAction: Ignored unknown type " + acttype + " in game " + pi.game.getName());

            }
        }

        public void playerRequestDeclined
            (final int reasonCode, final int detailValue1, final int detailValue2, final String reasonText)
        {
            pi.showDeclinedPlayerRequest(reasonCode,  detailValue1, detailValue2, reasonText);
        }

        public void buildRequestCanceled(SOCPlayer player)
        {
            pi.getPlayerHandPanel(player.getPlayerNumber()).updateResourcesVP();
            pi.boardPanel.updateMode();
        }

        public void invItemPlayRejected(final int type, final int reasonCode)
        {
            if ((reasonCode == 4) && pi.getGame().isGameOptionSet(SOCGameOptionSet.K_SC_FTRI))
                pi.printKeyed("game.invitem.sc_ftri.need.coastal");  // * "Requires a coastal settlement not adjacent to an existing port."
            else
                pi.printKeyed("hpan.item.play.cannot");  // * "Cannot play this item right now."
        }

        public void playerPickSpecialItem
            (final String typeKey, final SOCGame ga, final SOCPlayer pl, final int gidx, final int pidx,
             final boolean isPick, final int coord, final int level, final String sv)
        {
            if ((pl == null) && isPick)
                return;  // <--- Early return: So far, every pick implemented is player-specific ---

            if (! typeKey.equals(SOCGameOptionSet.K_SC_WOND))
                return;  // <--- Early return: So far, the only known typeKey is _SC_WOND ---

            if (isPick)
            {
                String iname = null;
                final SOCSpecialItem itm = ga.getSpecialItem(typeKey, gidx);

                if (itm != null)
                    iname = itm.getStringValue();

                if (iname != null)
                    iname = strings.get("game.specitem.sc_wond." + iname); // "w3" -> "Monument", etc
                else
                    iname = "# " + gidx;

                if (level == 1)
                    pi.printKeyed("game.specitem.sc_wond.started", pl.getName(), iname);
                        // "{0} started building a Wonder! ({1})"
                else
                    pi.printKeyed("game.specitem.sc_wond.built", pl.getName(), level, iname);
                        // "{0} has built level # of their Wonder ({2})."

                // TODO any visual effect?
            } else {
                pi.printKeyed("game.specitem.sc_wond.decl");  // "You cannot build that Wonder right now."
            }
        }

        public void playerSetSpecialItem
            (final String typeKey, final SOCGame ga, final SOCPlayer pl,
             final int gidx, final int pidx, final boolean isSet)
        {
            if (pl == null)
                return;  // <--- Early return: So far, everything implemented is player-specific ---

            if (! typeKey.equals(SOCGameOptionSet.K_SC_WOND))
                return;  // <--- Early return: So far, the only known typeKey is _SC_WOND ---

            final SOCHandPanel hp = pi.getPlayerHandPanel(pl.getPlayerNumber());
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

            if ((resultShipsLost == 0) || pi.isClientCurrentPlayer())
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

        public void playerDiscarded(final SOCPlayer player, final ResourceSet discards)
        {
            pi.reportDiscard(player, discards);
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

        public void reportRobberyResult
            (final int perpPN, final int victimPN, final int resType, final SOCResourceSet resSet, final PEType peType,
             final boolean isGainLose, final int amount, final int victimAmount, final int extraValue)
        {
            pi.reportRobberyResult
                (perpPN, victimPN, resType, resSet, peType, isGainLose, amount, victimAmount, extraValue);
        }

        public void playerBankTrade(final SOCPlayer player, final SOCResourceSet give, final SOCResourceSet get)
        {
            requestedTradeClear(player, true);
            playerElementUpdated
                (player, PlayerClientListener.UpdateType.ResourceTotalAndDetails, false, false);
            pi.printTradeResources(player, give, get, false, null);
        }

        public void requestedTrade(final SOCPlayer offerer, final int fromPN)
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

        public void playerTradeAccepted
            (final SOCPlayer offerer, final SOCPlayer acceptor, SOCResourceSet toOffering, SOCResourceSet toAccepting)
        {
            if (toOffering == null)
            {
                final SOCTradeOffer offer = offerer.getCurrentOffer();
                if (offer == null)
                    return;

                toAccepting = offer.getGiveSet();
                toOffering = offer.getGetSet();
            } else {
                // update resource-count displays, since there weren't PlayerElement messages for this trade
                playerElementUpdated
                    (offerer, PlayerClientListener.UpdateType.ResourceTotalAndDetails, false, false);
                playerElementUpdated
                    (acceptor, PlayerClientListener.UpdateType.ResourceTotalAndDetails, false, false);
            }

            if (toOffering != null)
                pi.printTradeResources(offerer, toAccepting, toOffering, false, acceptor);
        }

        public void playerTradeDisallowed(final int offeringPN, final boolean isOffer, final boolean isNotTurn)
        {
            pi.printKeyed
                ((isNotTurn)
                 ? "base.reply.not.your.turn"  // "It's not your turn."
                 : ((isOffer)
                    ? "trade.msg.cant.make.offer"  // "You can't make that offer."
                    : "reply.common.trade.cannot_make"));  // "You can't make that trade."
        }

        public void requestedTradeReset(SOCPlayer playerToReset)
        {
            final int pn = (playerToReset != null) ? playerToReset.getPlayerNumber() : -1;
            pi.hideHandMessage(pn);
        }

        public void clearTradeOffer(SOCPlayer player, boolean updateSendCheckboxes)
        {
            if (player != null)
                pi.hands[player.getPlayerNumber()].clearOffer(updateSendCheckboxes);
            else
                for (SOCHandPanel hp : pi.hands)
                    hp.clearOffer(updateSendCheckboxes);
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
            md.getGameMessageSender().resetBoardVote(pi.getGame(), true);
            pi.resetBoardClearDia();
        }

        /**
         * React to the No button. (call playerClient.resetBoardVote)
         */
        @Override
        public void button2Chosen()
        {
            md.getGameMessageSender().resetBoardVote(pi.getGame(), false);
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
     * When the choice is made, calls {@link GameMessageSender#chooseRobber(SOCGame)}
     * or {@link GameMessageSender#choosePirate(SOCGame)}.
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
         * Call {@link GameMessageSender#choosePlayer(SOCGame, int) pcli.choosePlayer(CHOICE_MOVE_ROBBER)}.
         */
        @Override
        public void button1Chosen()
        {
            md.getGameMessageSender().chooseRobber(game);
        }

        /**
         * React to the Move Pirate button.
         * Call {@link GameMessageSender#choosePlayer(SOCGame, int) pcli.choosePlayer(CHOICE_MOVE_PIRATE)}.
         */
        @Override
        public void button2Chosen()
        {
            md.getGameMessageSender().choosePirate(game);
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
     * When the choice is made, calls {@link GameMessageSender#choosePlayer(SOCGame, int)}.
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
         * Call {@link GameMessageSender#choosePlayer(SOCGame, int) pcli.choosePlayer(-(vpn + 1))}.
         */
        @Override
        public void button1Chosen()
        {
            md.getGameMessageSender().choosePlayer(game, -(vpn + 1));
        }

        /**
         * React to the Steal Resource button.
         * Call {@link GameMessageSender#choosePlayer(SOCGame, int) pcli.choosePlayer(vpn)}.
         */
        @Override
        public void button2Chosen()
        {
            md.getGameMessageSender().choosePlayer(game, vpn);
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
     * This PI's custom layout manager. Sizes and stretches its board panels, hand panels, etc.
     *<P>
     * Before v2.6.00, this class was {@code SOCPlayerInterface.doLayout()}.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.6.00
     */
    private class PILayoutManager implements LayoutManager
    {
        public void addLayoutComponent(String name, Component comp) {}

        public void removeLayoutComponent(Component comp) {}

        /** More or less unused, since PI initialization calls setSize instead of pack */
        public Dimension preferredLayoutSize(final Container pi)
        {
            return new Dimension(widthOrig, heightOrig);
        }

        /** More or less unused, since PI initialization calls setSize instead of pack */
        public Dimension minimumLayoutSize(final Container pi)
        {
            return new Dimension(WIDTH_MIN_4PL, HEIGHT_MIN_4PL);
        }

        /**
         * Arrange the custom layout at creation or frame resize.
         * Stretches {@link SOCBoardPanel}, {@link SOCHandPanel}s, etc to fit.
         *<P>
         * If a player sits down in a 6-player game, will need to
         * {@link Container#revalidate() getContentPane().revalidate()} and call this again,
         * because {@link SOCHandPanel} sizes will change.
         *<P>
         * Also, on first call, resets mouse cursor to normal, in case it was WAIT_CURSOR.
         * On first call, if the game options have a {@link SOCScenario} with any long description,
         * it will be shown in a popup via {@link #showScenarioInfoDialog()}.
         *<P>
         * Before v2.6.00, this method was {@code SOCPlayerInterface.doLayout()}.
         */
        public void layoutContainer(final Container pane)
        {
            final Dimension dim = pane.getSize();
            boolean needRepaintBorders = (prevSize != null)
                && ((dim.width != prevSize.width) || (dim.height != prevSize.height));
            prevSize = dim;

            /**
             * Classic Sizing
             * (board size was fixed, cannot scale)
             *
            int bw = SOCBoardPanel.PANELX;
            int bh = SOCBoardPanel.PANELY;
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
             * Make board as wide as possible without violating minimum handpanel width.
             * Boardpanel will center or scale up board hexes if needed to fill the larger space.
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

            boolean canStretchBoard = (bw >= bMinW) && (bh >= bMinH);

            if (bw > bMinW)
            {
                // Make board wider if possible
                int spareW = dim.width - (hpMinW * 2) - bw - pix16;
                if (spareW > 0)
                    bw += (4 * spareW / 5);  // give 4/5 to boardpanel width, the rest to hw
            }

            int hw = (dim.width - bw - pix16) / 2;  // each handpanel's width; height is hh
            int tah = 0;  // textareas' height (not including tfh): calculated soon

            if (canStretchBoard)
            {
                // Now that we have minimum board height/width,
                // make it taller if possible
                int spareH = (dim.height - buildph - pix16 - (int)(5.5f * tfh)) - bh;
                if (spareH > 0)
                    bh += (2 * spareH / 3);  // give 2/3 to boardpanel height, the rest to tah

                tah = dim.height - bh - buildph - tfh - pix16;

                // Scale it
                try
                {
                    boardPanel.setBounds(hw + pix8, tfh + tah + pix8, bw, bh);
                }
                catch (IllegalArgumentException e)
                {
                    canStretchBoard = false;
                }
            }

            if (! canStretchBoard)
            {
                bh = bMinH;
                tah = dim.height - bh - buildph - tfh - pix16;
                try
                {
                    boardPanel.setBounds(hw + pix8, tfh + tah + pix8, bw, bh);
                }
                catch (IllegalArgumentException ee)
                {
                    // fall back to safe sizes

                    bw = boardPanel.getWidth();
                    bh = boardPanel.getHeight();
                    hw = (dim.width - bw - pix16) / 2;
                    if ((hw < hpMinW) && (bw > bMinW))
                    {
                        // prevent gradually-widening boardpanel.getWidth() from squeezing handpanels too narrow
                        int widthAvail = dim.width - bMinW - (2 * hpMinW) - pix16;
                        if (widthAvail > 0)
                        {
                            int boardAvail = widthAvail / 5;
                            bw = bMinW + boardAvail;
                            hw = hpMinW + (widthAvail - boardAvail);

                            boardPanel.setSize(bw, bh, true);  // won't throw yet another exception

                            // because setSize may have ignored bw or bh:
                            bw = boardPanel.getWidth();
                            bh = boardPanel.getHeight();
                            hw = (dim.width - bw - pix16) / 2;
                        }
                    }

                    tah = dim.height - bh - buildph - tfh - pix16;
                    boardPanel.setLocation(hw + pix8, tfh + tah + pix8);
                }
            }
            if (highContrastBorders != null)
            {
                // [0] left, [1] right of boardPanel
                highContrastBorders[0].setBounds(hw + pix4, 0, pix4, dim.height);
                highContrastBorders[1].setBounds(hw + bw + pix8, 0, pix4, dim.height);
            }

            final int halfplayers = (is6player) ? 3 : 2;
            final int hh = (dim.height - pix12) / halfplayers;  // handpanel height
            final int kw = bw;

            buildingPanel.setBounds(hw + pix8, tah + tfh + bh + pix12, kw, buildph);

            // Hands start at top-left, go clockwise;
            // hp.setBounds also sets its blankStandIn's bounds.
            // Note that any hands[] could be null, due to async adds, calls, invalidations.

            try
            {
                if (! is6player)
                {
                    hands[0].setBounds(pix4, pix4, hw, hh);
                    if (game.maxPlayers > 1)
                    {
                        hands[1].setBounds(hw + bw + pix12, pix4, hw, hh);
                        hands[2].setBounds(hw + bw + pix12, hh + pix8, hw, hh);
                        hands[3].setBounds(pix4, hh + pix8, hw, hh);

                        if (highContrastBorders != null)
                        {
                            // [2] between left hpans, [3] between right
                            highContrastBorders[2].setBounds(0, hh + pix4, hw + pix8, pix4);
                            highContrastBorders[3].setBounds(hw + bw + pix8, hh + pix4, hw + pix8, pix4);
                        }
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
                        hands[0].setBounds(pix4, pix4, hw, hh);
                        hands[4].setBounds(pix4, 2 * hh + pix12, hw, hh);
                        hands[5].setBounds(pix4, hh + pix8, hw, hh);
                        if (highContrastBorders != null)
                        {
                            // [2], [3] between left hpans
                            highContrastBorders[2].setBounds(0, hh + pix4, hw + pix8, pix4);
                            highContrastBorders[3].setBounds(0, 2 * hh + pix8, hw + pix8, pix4);
                        }
                    }
                    if ((clientHandPlayerNum < 1) || (clientHandPlayerNum > 3))
                    {
                        hands[1].setBounds(hw + bw + pix12, pix4, hw, hh);
                        hands[2].setBounds(hw + bw + pix12, hh + pix8, hw, hh);
                        hands[3].setBounds(hw + bw + pix12, 2 * hh + pix12, hw, hh);
                        if (highContrastBorders != null)
                        {
                            // [4], [5] between right hpans
                            highContrastBorders[4].setBounds(hw + bw + pix12, hh + pix4, hw + pix8, pix4);
                            highContrastBorders[5].setBounds(hw + bw + pix12, 2 * hh + pix8, hw + pix8, pix4);
                        }
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
                            hp_x = hw + bw + pix12;
                        } else {
                            final int[] idx_left = {0, 5, 4};
                            hp_idx = idx_left;
                            hp_x = pix4;
                        }
                        for (int ihp = 0, hp_y = pix4; ihp < 3; ++ihp)
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
                            if ((highContrastBorders != null) && (ihp <= 1))
                                highContrastBorders[ihp + (isRight ? 4 : 2)].setBounds
                                    (hp_x - pix4, hp_y - pix4, hw + pix8, pix4);
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
                final int x = hw / 2;
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

                textDisplay.setBounds(x, pix4, w, h);
                if (! game.isPractice)
                    cdh += (20 * displayScale);
                chatDisplay.setBounds(x, pix4 + h, w, cdh);
                h += cdh;
                textInput.setBounds(x, pix4 + h, w, tfh);

                needRepaintBorders = true;

                // focus here for easier chat typing
                textInput.requestFocusInWindow();
            } else {
                // standard size
                final int prevTextY = textInput.getBounds().y;
                textDisplay.setBounds(hw + pix8, pix4, bw, tdh);
                chatDisplay.setBounds(hw + pix8, pix4 + tdh, bw, cdh);
                textInput.setBounds(hw + pix8, pix4 + tah, bw, tfh);

                // scroll to bottom of textDisplay, chatDisplay after resize from expanded
                EventQueue.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        chatDisplay.setCaretPosition(chatDisplay.getText().length());
                        textDisplay.setCaretPosition(textDisplay.getText().length());
                    }
                });

                if (textDisplaysLargerTemp_needsLayout || (prevTextY != textInput.getBounds().y))
                    needRepaintBorders = true;
            }
            textDisplaysLargerTemp_needsLayout = false;
            if (needRepaintBorders)
                repaint();  // clear borders of handpanels near chatDisplay, textDisplay

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
             * (set in SOCPlayerClient.startPracticeGame or SwingMainDisplay.guardedActionPerform).
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
    }

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
            pi.revalidate();  // calls pi.doLayout()
            pi.repaint();
        }

        /**
         * Clean up after the window is closed.
         * Close the GameStatisticsFrame if showing, maybe persist some client prefs, etc.
         * @since 2.0.00
         */
        @Override
        public void windowClosed(WindowEvent e)
        {
            // Close stats frame if showing
            if (pi.buildingPanel != null)
                pi.buildingPanel.gameWindowClosed();

            // Remember last-chosen face icon
            if (UserPreferences.getPref(SOCPlayerClient.PREF_FACE_ICON, 0) > 0)
                UserPreferences.putPref(SOCPlayerClient.PREF_FACE_ICON, pi.client.lastFaceChange);
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
        /** our playerinterface; not null */
        private final SOCPlayerInterface pi;

        public SOCPITextfieldListener(SOCPlayerInterface spi)
            throws IllegalArgumentException
        {
            if (spi == null)
                throw new IllegalArgumentException("spi");

            pi = spi;
        }

        /**
         * Handle key presses.
         *<UL>
         * <LI> If first keypress in initially empty field, clear that prompt message
         * <LI> Use up/down arrows to browse sent-chat history in {@link SOCPlayerInterface#textInputHistory}
         *</UL>
         */
        @Override
        public void keyPressed(KeyEvent e)
        {
            switch (e.getKeyCode())
            {
            case KeyEvent.VK_UP:
                {
                    final int S = pi.textInputHistory.size();
                    if (S > 1)
                    {
                        int i = pi.textInputHistoryBrowsePos;
                        if (i == 0)
                        {
                            pi.textInputHistory.set(0, pi.textInput.getText());  // save unsent text
                            i = S - 1;
                        } else {
                            --i;
                        }

                        if (i > 0)
                        {
                            pi.textInput.setText(pi.textInputHistory.get(i));
                            pi.textInputHistoryBrowsePos = i;
                            e.consume();
                        }
                    }
                }
                break;

            case KeyEvent.VK_DOWN:
                {
                    int i = pi.textInputHistoryBrowsePos;
                    if (i > 0)
                    {
                        ++i;
                        if (i == pi.textInputHistory.size())
                            i = 0;  // restore unsent text

                        pi.textInput.setText(pi.textInputHistory.get(i));
                        pi.textInputHistoryBrowsePos = i;
                        e.consume();
                    }
                }
                break;
            }

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
     * Hotkey listener for client player to request actions or respond to a hand panel's trade offers.
     * If {@link #isForTrade}: When more than one hand panel has {@link TradePanel#isOfferToPlayer()}, does nothing
     * to avoid responding to the wrong offer.
     * Not used for {@link SOCHandPanel}'s Roll and Done hotkeys.
     * Initialized in {@link SOCPlayerInterface#addHotkeysInputMap()}.
     *<P>
     * Before v2.5.00 this class was {@code TradeHotkeyActionListener}.
     * @since 2.3.00
     */
    private class PIHotkeyActionListener extends AbstractAction
    {
        public static final int ACCEPT = 1, REJECT = 2, COUNTER = 3;

        /**
         * Ask to Special Build.
         * @since 2.5.00
         */
        public static final int ASK_SPECIAL_BUILD = 4;

        /**
         * {@link #ACCEPT}, {@link #ASK_SPECIAL_BUILD}, etc.
         * @see #isForTrade
         */
        public final int forButton;

        /**
         * True if should do nothing if more than one hand panel has {@link TradePanel#isOfferToPlayer()}.
         * Set in constructor. Is used with trade buttons {@link #ACCEPT}, {@link #REJECT}, {@link #COUNTER}
         * but not {@link #ASK_SPECIAL_BUILD}.
         * @see #forButton
         * @since 2.5.00
         */
        public final boolean isForTrade;

        /**
         * @param forButton The button to activate: {@link #ACCEPT}, {@link #REJECT}, {@link #COUNTER},
         *     or {@link #ASK_SPECIAL_BUILD}
         */
        public PIHotkeyActionListener(final int forButton)
        {
            this.forButton = forButton;
            this.isForTrade = (forButton != ASK_SPECIAL_BUILD);
        }

        public void actionPerformed(ActionEvent e)
        {
            if (! isForTrade)
            {
                switch (forButton)
                {
                case ASK_SPECIAL_BUILD:
                    buildingPanel.clickBuildingButton(game, SOCBuildingPanel.SBP, false);
                    break;
                }

                return;
            }

            SOCHandPanel hpo = null;
            for (int pn = 0; pn < game.maxPlayers; ++pn)
            {
                SOCHandPanel hp = hands[pn];
                if (! hp.isShowingOfferToClientPlayer())
                    continue;
                if (hpo != null)
                    return;  // multiple offers
                hpo = hp;
            }
            if (hpo == null)
                return;

            switch (forButton)
            {
            case ACCEPT:  hpo.clickOfferAcceptButton();  break;
            case REJECT:  hpo.clickOfferRejectButton();  break;
            case COUNTER: hpo.clickOfferCounterButton(); break;
            }
        }
    }

    /**
     * When timer fires, show discard message or picking-resource message
     * for any other player (not client player) who must discard or pick.
     *<P>
     * Before v2.0.00 this class was {@code SOCPIDiscardMsgTask}.
     *
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
                getContentPane().revalidate();
                repaint();
            }
        }

    }  // SOCPITextDisplaysLargerTask

    /**
     * Runnable class to try to play a queued sound using {@link Clip#setFramePosition(int)} and {@link Clip#start()}.
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
        private final Clip c;

        /**
         * Create a queued sound Runnable.
         * @param c  Clip for buffered sound to play; not null
         * @throws IllegalArgumentException if {@code c} is null
         */
        public PIPlaySound(final Clip c)
            throws IllegalArgumentException
        {
            if (c == null)
                throw new IllegalArgumentException("PIPlaySound");

            this.c = c;
        }

        public void run()
        {
            if (soundMuted || ! UserPreferences.getPref(SOCPlayerClient.PREF_SOUND_ON, true))
                return;

            try
            {
                c.setFramePosition(0);
                c.start();
                Thread.sleep(c.getMicrosecondLength() / 1000);  // since playback uses clip's own thread
            } catch (InterruptedException e) {}
        }
    }

}  // SOCPlayerInterface
