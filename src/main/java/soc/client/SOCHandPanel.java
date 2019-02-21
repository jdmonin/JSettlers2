/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2019 Jeremy D Monin <jeremy@nand.net>
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

import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCDevCard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCInventory;
import soc.game.SOCInventoryItem;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCSpecialItem;
import soc.game.SOCTradeOffer;
import soc.message.SOCCancelBuildRequest;  // for INV_ITEM_PLACE_CANCEL constant
import soc.util.SOCStringManager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.MissingResourceException;
import java.util.Timer;  // For auto-roll
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;


/**
 * This panel displays a player's information.
 * If the player is us, then more information is
 * displayed than in another player's hand panel.
 *<P>
 * To update most of the values shown in the handpanel,
 * call {@link #updateValue(PlayerClientListener.UpdateType)} after receiving
 * a {@link soc.message.SOCPlayerElement} message or after something
 * else changes the game state.
 *<P>
 * Custom layout: see {@link #doLayout()}.
 * To set this panel's position or size, please use {@link #setBounds(int, int, int, int)},
 * because it is overridden to also update {@link #getBlankStandIn()}.
 */
@SuppressWarnings("serial")
/*package*/ class SOCHandPanel extends JPanel
    implements ActionListener, MouseListener
{
    /** Minimum desired width, in pixels */
    public static final int WIDTH_MIN = 218;

    /** Items to update via {@link #updateValue(PlayerClientListener.UpdateType)};
     * for items not appearing in {@link soc.message.SOCPlayerElement}.
     * All these item numbers are negative, so they won't
     * conflict with any SOCPlayerElement element type.
     *<P>
     * </tt>NUMDEVCARDS</tt> won't appear in the client's handpanel, only in other players'.
     */
    public static final int
        NUMRESOURCES = -3,
        NUMDEVCARDS = -4,
        VICTORYPOINTS = -6,
        LONGESTROAD = -7,
        LARGESTARMY = -8,
        SPECIALVICTORYPOINTS = 9;

    /** Auto-roll timer countdown, 5 seconds unless changed at program start. */
    public static int AUTOROLL_TIME = 5;

    /** Array of five zeroes, one per resource type; for {@link #sqPanel}. */
    protected static final int[] zero = { 0, 0, 0, 0, 0 };

    /** i18n text strings.
     *  @since 2.0.00 */
    private static final SOCStringManager strings = SOCStringManager.getClientManager();

    /** Before game starts, use {@link #pname} to show if a seat is no-robots-allowed. */
    protected static final String SITLOCKED = strings.get("hpan.sit.locked.norobot");  // "Locked: No robot"
    protected static final String SIT = strings.get("hpan.sit.here");  // "Sit Here"
    protected static final String START = strings.get("hpan.start.game");  // "Start Game"
    protected static final String ROBOT = strings.get("hpan.sit.robot");
    protected static final String TAKEOVER = strings.get("hpan.sit.takeover");  // "Take Over"
    private static final String SEAT_LOCKED = "* " + strings.get("hpan.seat.locked") + " *";  // "* Seat Locked *"
    protected static final String LOCKSEAT = strings.get("hpan.sit.lock");
    protected static final String UNLOCKSEAT = strings.get("hpan.sit.unlock");
    private static final String LOCKSEATTIP = strings.get("hpan.sit.lock.tip");
        // "Lock to prevent a robot from sitting here."
    private static final String UNLOCKSEATTIP = strings.get("hpan.sit.unlock.tip");
        // "Unlock to have a robot sit here when the game starts."
    protected static final String ROLL = strings.get("hpan.roll");
    protected static final String QUIT = strings.get("hpan.quit");
    protected static final String DONE = strings.get("hpan.done");
    /** Text of Done button at end of game becomes Restart button. If you set this, set {@link #doneButIsRestart}. */
    protected static final String DONE_RESTART = strings.get("base.restart");
    protected static final String CLEAR = strings.get("hpan.trade.clear");
    protected static final String SEND = strings.get("hpan.trade.offer");
    protected static final String BANK = strings.get("hpan.trade.bankport");  // "Bank/Port"
    private static final String BANK_UNDO = strings.get("hpan.trade.undo");  // "Undo Trade"
    /** "  Play Card  " button label text for {@link #playCardBut} */
    private static final String CARD = "  " + strings.get("hpan.devcards.play") + "  ";
    /** "Cancel" button label text, used temporarily in some game states */
    private static final String CANCEL = strings.get("base.cancel");
    protected static final String GIVE = strings.get("hpan.trade.igive");  // No trailing space (room for wider colorsquares)
    protected static final String GET = strings.get("hpan.trade.iget");
    /** Dev card list prefix "*NEW* " - includes trailing space */
    private static final String DEVCARD_NEW = strings.get("hpan.devcards.prefix.new");
    private static final String RESOURCES = strings.get("hpan.rsrc") + " ";  // for other players (! playerIsClient)
    private static final String RESOURCES_TOTAL = strings.get("hpan.rsrc.total") + " ";  // "Total: " for playerIsClient
    protected static final String AUTOROLL_COUNTDOWN = strings.get("hpan.roll.auto_countdown");  // "Auto-Roll in: {0}"
    protected static final String ROLL_OR_PLAY_CARD = strings.get("hpan.roll.rollorplaycard");  // "Roll or Play Card"
    private static final String OFFERBUTTIP_ENA = strings.get("hpan.trade.offer.tip.send");
        // "Send trade offer to other players"
    private static final String OFFERBUTTIP_DIS = strings.get("hpan.trade.offer.tip.first");
        // "To offer a trade, first click resources"
    private static final String ROBOTLOCKBUT_U = strings.get("hpan.sit.unlocked");
    private static final String ROBOTLOCKBUT_L = strings.get("hpan.sit.locked");
    private static final String ROBOTLOCKBUT_M = strings.get("hpan.sit.marked");  // for lockstate Clear on Reset
    private static final String ROBOTLOCKBUTTIP_L = strings.get("hpan.sit.locked.tip");
        // "Click to mark or unlock; is locked to prevent a human from taking over this robot."
    private static final String ROBOTLOCKBUTTIP_U = strings.get("hpan.sit.unlocked.tip");
        // "Click to lock or mark; is unlocked, a human can take over this robot."
    private static final String ROBOTLOCKBUTTIP_M = strings.get("hpan.sit.marked.tip");
        // "Click to unmark; is marked to remove this robot if the game is reset."
    private static final String ROBOTLOCKBUTTIP_L_OLD = strings.get("hpan.sit.locked.tip.nomark");
        // "Click to unlock; is locked to prevent a human from taking over this robot."
    private static final String ROBOTLOCKBUTTIP_U_OLD = strings.get("hpan.sit.unlocked.tip.nomark");
        // "Click to lock; is unlocked, a human can take over this robot."

    /**
     * Show that a non-client player is discarding resources after 7 is rolled.
     * Call {@link #setDiscardOrPickMsg(boolean)} to show.
     * Same methods are used by discard and by {@link #TRADEMSG_PICKING}.
     * @since 1.1.00
     */
    private static final String TRADEMSG_DISCARD = strings.get("hpan.discarding");  // "Discarding..."

    /**
     * Show that a non-client player is picking resources for the gold hex.
     * Uses same variables and methods as {@link #TRADEMSG_DISCARD}:
     * {@link #offerIsDiscardOrPickMessage}, {@link #setDiscardOrPickMsg(boolean)}, etc.
     * @since 2.0.00
     */
    private static final String TRADEMSG_PICKING = strings.get("hpan.picking.rsrcs");  // "Picking\nResources..."

    /** Panel text color, and player name color when not current player */
    protected static final Color COLOR_FOREGROUND = Color.BLACK;

    /**
     * If true, our constructor has set the Swing tooltip default font/foreground/background.
     * Currently SOCHandPanel is the only JSettlers class using Swing tooltips.
     * @since 2.0.00
     */
    private static boolean didSwingTooltipDefaults;

    /** Player name background color when current player (foreground does not change) */
    protected Color pnameActiveBG;

    /**
     * Blank area which is normally hidden, except during addPlayer when handpanel is hidden.
     * This prevents a big black area on the display (which looks like a crash).
     * For perf/display-bugs during component layout (OSX firefox).
     * Added to pi's layout by {@link SOCPlayerInterface#initInterfaceElements(boolean)}.
     * @since 1.1.06
     */
    private ColorSquare blankStandIn;

    /**
     * When player has joined but not sat, the "Sit Here" button.  After
     * they sit down, this button is used for the anti-robot "Lock" button.
     * ({@link #LOCKSEAT} / {@link #UNLOCKSEAT}, when {@link #sitButIsLock} true.)
     * Disappears when the game begins.
     * @see #renameSitButLock()
     * @see #sittingRobotLockBut
     */
    protected JButton sitBut;

    protected JButton robotBut;
    protected JButton startBut;
    protected JButton takeOverBut;

    /** Seat lock/unlock shown in robot handpanels during game play,
     *  to prevent/allow humans to join and take over a robot's seat.
     *  Used during different game states than {@link #sitBut}.
     *<P>
     *  Labels are {@link #ROBOTLOCKBUT_U}, {@link #ROBOTLOCKBUT_L}, {@link #ROBOTLOCKBUT_M}.
     *  Click method is {@link #clickRobotSeatLockButton(soc.game.SOCGame.SeatLockState)}.
     *  @see #sitBut
     */
    protected JButton sittingRobotLockBut;

    /** When true, the game is still forming, player has chosen a seat;
     *  "Sit Here" button is labeled as "Lock" or "Unlock".  Humans can
     *  use this to lock robots out of that seat, to start a game with
     *  fewer players and some vacant seats.
     *<P>
     *  Set by {@link #renameSitButLock()}, cleared elsewhere.
     *  This affects {@link #sitBut} and not {@link #sittingRobotLockBut}.
     *  @see #addPlayer(String)
     *  @see #updateSeatLockButton()
     */
    protected boolean sitButIsLock;

    /**
     * Face icon; can right-click/triple-click for face chooser popup.
     * @since 1.1.00
     */
    protected SOCFaceButton faceImg;

    /** Player name if {@link #inPlay}, otherwise blank or text like "Locked" */
    protected JLabel pname;

    protected JLabel vpLab;
    protected ColorSquare vpSq;

    /** Label for Special Victory Points.  Hidden if {@link SOCPlayer#getSpecialVP()} is 0.
     *  Null unless {@link SOCGame#hasSeaBoard}.
     *  @since 2.0.00
     */
    private JLabel svpLab;

    /** Special Victory Points, if > 0.  Hidden if 0.
     *  Null unless {@link SOCGame#hasSeaBoard}.
     *  @since 2.0.00
     */
    private ColorSquare svpSq;

    /** Largest Army label, usually invisible; placed to left of {@link #lroadLab} */
    protected JLabel larmyLab;
    /** Longest Road label, usually invisible; placed to right of {@link #larmyLab} */
    protected JLabel lroadLab;

    protected ColorSquare claySq;
    protected ColorSquare oreSq;
    protected ColorSquare sheepSq;
    protected ColorSquare wheatSq;
    protected ColorSquare woodSq;
    protected ColorSquare resourceSqDivLine;
    protected JLabel clayLab;
    protected JLabel oreLab;
    protected JLabel sheepLab;
    protected JLabel wheatLab;
    protected JLabel woodLab;

    /**
     * For right-click resource to trade - If playerIsClient, track cost
     * of bank/port trade per resource. Index 0 unused; index 1 is
     * {@link SOCResourceConstants#CLAY}, etc. Highest index is 5.
     * Null, unless playerIsClient and addPlayer has been called.
     */
    protected int[] resourceTradeCost;

    /**
     * For right-click resource to trade - If playerIsClient, popup menus
     * to bank/port trade resources. Index 0 unused; index 1 is
     * {@link SOCResourceConstants#CLAY}, etc. Highest index is 5.
     * Null, unless playerIsClient and addPlayer has been called.
     */
    protected ResourceTradeTypeMenu[] resourceTradeMenu;

    protected ColorSquare settlementSq;
    protected ColorSquare citySq;
    protected ColorSquare roadSq;
    /** shipSq = the number of ships remaining, or null if not {@link SOCGame#hasSeaBoard}. @since 2.0.00 */
    protected ColorSquare shipSq;
    protected JLabel settlementLab;
    protected JLabel cityLab;
    protected JLabel roadLab;
    protected JLabel shipLab;
    /** Resource card count */
    protected ColorSquare resourceSq;
    protected JLabel resourceLab;
    /** Development card count */
    protected ColorSquare developmentSq;
    protected JLabel developmentLab;
    /** Soldier/Knight count */
    protected ColorSquare knightsSq;
    /**
     * Label for {@link #knightsSq}. This and related labels (settlements, cities, ...) are transparent-background
     * JLabels to avoid z-order overlap problems between the labels' padding and the count squares next to them
     * (seen during v2.0.00 development).
     */
    protected JLabel knightsLab;

    /**
     * Player's development card/inventory item names, from {@link #inventoryItems};
     * updated frequently by {@link #updateDevCards(boolean)}. Held within {@link #inventoryScroll}.
     */
    protected JList<String> inventory;

    /**
     * Player's development cards/inventory items, in same order as {@link #inventory};
     * updated frequently by {@link #updateDevCards(boolean)}
     */
    private ArrayList<SOCInventoryItem> inventoryItems;

    /**
     * Scrollpane holding {@link #inventory} on panel, for {@code doLayout()} to size.
     * @since 2.0.00
     */
    private JScrollPane inventoryScroll;

    /**
     * Play Card button for {@link #inventory}.
     *<P>
     * v2.0.00+: In state {@link SOCGame#PLACING_INV_ITEM} only, this button's label
     * becomes {@link #CANCEL}, and {@link #inventory} is disabled, while the player
     * places an item on the board.  They can hit Cancel to return the item to their
     * inventory instead.  In any other state, label text is {@link #CARD}.
     * Updated in {@link #updateRollDoneBankButtons()} which checks {@link #canCancelInvItemPlay}.
     */
    protected JButton playCardBut;

    /**
     * Flag for {@link #playCardBut} in state {@link SOCGame#PLACING_INV_ITEM}.
     * Checked in {@link #updateRollDoneBankButtons()}.
     * For details, see {@link #setCanCancelInvItemPlay(boolean)}.
     */
    private boolean canCancelInvItemPlay;

    /** Trade offer resource squares; visible only for client's own player */
    protected SquaresPanel sqPanel;

    /**
     * Cloth count, for scenario {@link SOCGameOption#K_SC_CLVI _SC_CLVI}; null otherwise.
     * Appears in same area as {@link #wonderLab}.
     * @since 2.0.00
     */
    protected ColorSquare clothSq;
    protected JLabel clothLab;

    /**
     * Wonder Level label, for scenario {@link SOCGameOption#K_SC_WOND _SC_WOND}; null otherwise.
     * Blank ("") if player has no current wonder level.
     * Appears in same areas as {@link #clothLab} and {@link #clothSq}.
     * @since 2.0.00
     */
    private JLabel wonderLab;

    // Trading interface

    /**
     * Game option NT: If true, only bank trading is allowed,
     * trading between players is disabled.
     * @since 1.1.07
     */
    protected boolean playerTradingDisabled;

    protected JLabel giveLab;
    protected JLabel getLab;

    /** "Offer" button for player trading: send offer to server */
    protected JButton offerBut;

    /** Clear the current trade offer at client and server */
    protected JButton clearOfferBut;

    /**
     * Trade resources with the bank or port.
     * @see #bankGive
     * @see #bankGet
     * @see #bankUndoBut
     * @see SOCPlayerInterface#bankTradeWasFromTradePanel
     */
    protected JButton bankBut;

    /**
     * Bank or port trade's give/get resource info;
     * used for Undo.
     * @since 1.1.13
     */
    private SOCResourceSet bankGive, bankGet;

    /**
     * Undo previous trade with the bank or port
     * @see #bankBut
     * @since 1.1.13
     */
    protected JButton bankUndoBut;

    /**
     * Checkboxes to send to the other 3 or 5 players.
     * Enabled/disabled at removeStartBut().
     * Updated at start of each turn in {@link #clearOffer(boolean)}.
     *<P>
     * This is null if {@link #playerTradingDisabled}.
     *
     * @see #playerSendMap
     * @see #playerSendForPrevTrade
     */
    protected ColorSquare[] playerSend;

    /**
     * The {@link #playerSend} checkboxes selected during the
     * previous trade offer; if no previous offer, all are selected.
     *<P>
     * This is null if {@link #playerTradingDisabled}, because {@link #playerSend} is null.
     * @since 1.1.13
     */
    private boolean[] playerSendForPrevTrade;

    // end of most Trading interface fields; a few more below.

    /** displays auto-roll countdown, or prompts to roll/play card.
     * @see #setRollPrompt(String, boolean)
     */
    protected JLabel rollPromptCountdownLab;
    protected boolean rollPromptInUse;
    protected TimerTask autoRollTimerTask;  // Created every turn when countdown needed
    protected JButton rollBut;

    /** "Done" with turn during play; also "Restart" for board reset at end of game */
    protected JButton doneBut;

    /** True when {@link #doneBut}'s label is Restart ({@link #DONE_RESTART}) */
    protected boolean doneButIsRestart;

    protected JButton quitBut;

    protected final SOCPlayerInterface playerInterface;
    protected final SOCPlayerClient client;
    protected final SOCGame game;

    /**
     * Our player if: {@link #inPlay}, not null, and {@link SOCPlayer#getName()} not null.
     * @see #playerNumber
     */
    protected SOCPlayer player;

    /**
     * Our player number.  Set in {@link #creation(SOCPlayerInterface, SOCPlayer, boolean)}
     * to {@link #player}.{@link SOCPlayer#getPlayerNumber() getPlayerNumber()}
     * @since 1.1.16
     */
    private int playerNumber = -1;

    /** Does this panel represent our client's own hand?  If true, implies {@link #interactive}. */
    protected boolean playerIsClient;

    /** Is this panel's player the game's current player?  Used for hilight - set in updateAtTurn() */
    protected boolean playerIsCurrent;

    /**
     * Do we have any seated player? Set by {@link #addPlayer(String)}, cleared by {@link #removePlayer()}.
     * @see #player
     * @see #playerNumber
     */
    protected boolean inPlay;

    // More Trading interface/message balloon fields:

    /** Three player numbers to send trade offers to.
     *  For i from 0 to 2, playerSendMap[i] is playerNumber for checkbox i.
     *  This is null if {@link #playerTradingDisabled}.
     *
     * @see #playerSend
     */
    protected int[] playerSendMap;

    /**
     * Display other players' trade offers and related messages.
     * Does not apply to client's hand panel (<tt>playerIsClient</tt> == true).
     * Both offer and counter-offer display are part of this object.
     * Also used to display board-reset vote messages.
     * When displaying a message, looks like a {@link SpeechBalloon}.
     *<P>
     * If the handpanel is not tall enough, other controls will be obscured by this one.
     * This low height is indicated by {@link #offerHidesControls} and possibly {@link #offerCounterHidesFace}.
     *
     * @see #offerIsResetMessage
     * @see #offerIsDiscardOrPickMessage
     */
    protected TradeOfferPanel offer;

    /**
     * If true, the handpanel isn't tall enough, so when the {@link #offer} message panel
     * is showing something, we must hide other controls.
     * Does not apply to client's hand panel.
     *
     * @see #hideTradeMsgShowOthers(boolean)
     * @see #offerCounterHidingFace
     * @since 1.1.08
     */
    private boolean offerHidesControls, offerCounterHidesFace;

    /**
     * When handpanel isn't tall enough, are we currently in the situation described
     * at {@link #offerHidesControls} or {@link #offerCounterHidesFace}?
     * @see #hideTradeMsgShowOthers(boolean)
     * @since 1.1.08
     */
    private boolean offerHidingControls, offerCounterHidingFace;

    /**
     * Board-reset voting: If true, {@link #offer} is holding a message related to a board-reset vote.
     * @see #offerIsDiscardOrPickMessage
     */
    protected boolean offerIsResetMessage;

    /**
     * Board-reset voting: If true, {@link #offer} is holding a discard message
     * ({@link #TRADEMSG_DISCARD}) or a gold hex pick-resources message
     * ({@link #TRADEMSG_PICKING}).
     * Set by {@link #setDiscardOrPickMsg(boolean)},
     * cleared by {@link #clearDiscardOrPickMsg()}.
     * @see #offerIsResetMessage
     */
    private boolean offerIsDiscardOrPickMessage;

    /**
     * Board-reset voting: If true, {@link #offer} was holding an active trade offer
     * before {@link #offerIsResetMessage} or {@link #offerIsDiscardOrPickMessage} was set.
     */
    protected boolean offerIsMessageWasTrade;

    // End of Trading interface/message balloon fields.

    /**
     * When this flag is true, the panel is interactive.
     * If {@link #playerIsClient} true, implies interactive.
     */
    protected final boolean interactive;

    /**
     * Construct a new hand panel.
     * For details see {@link #SOCHandPanel(SOCPlayerInterface, SOCPlayer, boolean)}.
     *
     * @param pi  the interface that this panel is a part of
     * @param pl  the player associated with this panel; cannot be {@code null}
     */
    public SOCHandPanel(final SOCPlayerInterface pi, final SOCPlayer pl)
    {
        this(pi, pl, true);
    }

    /**
     * Construct a new hand panel.
     * Calls {@link #removePlayer()}.
     *
     * @param pi  the interface that this panel is a part of
     * @param pl  the player associated with this panel; cannot be {@code null}
     * @param isInteractive  true if is or might become interactive, with a seated player
     */
    public SOCHandPanel(final SOCPlayerInterface pi, final SOCPlayer pl, final boolean isInteractive)
    {
        super(null);

        playerInterface = pi;
        client = pi.getClient();
        game = pi.getGame();
        player = pl;
        playerNumber = player.getPlayerNumber();
        playerIsCurrent = false;
        playerIsClient = false;  // confirmed by call to removePlayer() at end of method.
        interactive = isInteractive;

        // Note no layout manager is used - custom layout, see doLayout().

        final String FONT_SKIP_FLAG = DONE;  // client property to not set a label's font in loop at bottom;
            // picked DONE for value because it's a defined arbitrary unique-to-handpanel string reference

        final Color pcolor = playerInterface.getPlayerColor(playerNumber);
        setBackground(pcolor);
        setForeground(COLOR_FOREGROUND);
        setFont(new Font("SansSerif", Font.PLAIN, 10));

        blankStandIn = new ColorSquare(pcolor, strings.get("hpan.one.moment"));  // "One moment..."
        blankStandIn.setVisible(false);
        // playerinterface.initInterfaceElements will add blankStandIn to its layout, and set its size/position.

        faceImg = new SOCFaceButton(playerInterface, playerNumber);
        add(faceImg);

        pname = new JLabel();
        pname.setFont(new Font("SansSerif", Font.PLAIN, 13));
        pname.setVerticalAlignment(JLabel.TOP);
        pname.putClientProperty(FONT_SKIP_FLAG, Boolean.TRUE);
        // pname uses panel's background color, except when current player (updateAtTurn):
        pname.setBackground(null);
        pname.setOpaque(true);
        add(pname);
        pnameActiveBG = null;  // Will be calculated at first turn

        startBut = new JButton(START);
        startBut.addActionListener(this);
        // this button always enabled
        add(startBut);

        vpLab = new JLabel(strings.get("hpan.points") + " ");  // "Points: "
        add(vpLab);
        vpSq = new ColorSquare(ColorSquare.GREY, 0);
        vpSq.setToolTipText(strings.get("hpan.points.total.opponent"));  // "Total victory points for this opponent"
        final String vp_close_to_win = strings.get("hpan.points.closetowin");  // "Close to winning"
        if (game.vp_winner <= 12)
        {
            vpSq.setToolTipHighWarningLevel(vp_close_to_win, game.vp_winner - 2);  // (win checked in SOCGame.checkForWinner)
        } else {
            vpSq.setToolTipHighWarningLevel(vp_close_to_win, game.vp_winner - 3);
        }
        add(vpSq);

        if (game.hasSeaBoard)
        {
            final String svp_tt = strings.get("hpan.svp.tt");  // "Special Victory Points, click for details"

            svpLab = new JLabel(strings.get("hpan.svp") + " ");  // "SVP: "
            svpLab.setVisible(false);
            svpLab.setToolTipText(svp_tt);
            add(svpLab);
            svpLab.addMouseListener(this);
            svpSq = new ColorSquare(ColorSquare.GREY, 0);
            svpSq.setVisible(false);
            svpSq.setToolTipText(svp_tt);
            add(svpSq);
            svpSq.addMouseListener(this);
        } else {
            svpLab = null;
            svpSq = null;
        }

        final Font DIALOG_PLAIN_10 = new Font("Dialog", Font.PLAIN, 10);

        larmyLab = new JLabel("", SwingConstants.CENTER);
        larmyLab.setFont(DIALOG_PLAIN_10);  // was bold 12pt SansSerif before v2.0.00 (i18n: needs room for more chars)
        add(larmyLab);

        lroadLab = new JLabel("", SwingConstants.RIGHT);
        lroadLab.setFont(DIALOG_PLAIN_10);  // was bold 12pt SansSerif before v2.0.00
        add(lroadLab);

        createAndAddResourceColorSquare(ColorSquare.CLAY, "resources.clay");
        clayLab = createColorSqRetLbl;   claySq = createColorSqRetSq;

        createAndAddResourceColorSquare(ColorSquare.ORE, "resources.ore");
        oreLab = createColorSqRetLbl;    oreSq = createColorSqRetSq;

        createAndAddResourceColorSquare(ColorSquare.SHEEP, "resources.sheep");
        sheepLab = createColorSqRetLbl;  sheepSq = createColorSqRetSq;

        createAndAddResourceColorSquare(ColorSquare.WHEAT, "resources.wheat");
        wheatLab = createColorSqRetLbl;  wheatSq = createColorSqRetSq;

        createAndAddResourceColorSquare(ColorSquare.WOOD, "resources.wood");
        woodLab = createColorSqRetLbl;   woodSq = createColorSqRetSq;

        createColorSqRetLbl = null;      createColorSqRetSq = null;  // done, clear refs

        resourceSqDivLine = new ColorSquare(Color.BLACK);
        resourceSqDivLine.setMinimumSize(new Dimension(20, 1));  // for this narrow line, reduce usual minimum height
        add(resourceSqDivLine);

        //cardLab = new Label("Cards:");
        //add(cardLab);
        inventoryItems = new ArrayList<SOCInventoryItem>();
        inventory = new JList<String>(new DefaultListModel<String>());
        inventory.setVisibleRowCount(-1);  // show as many as possible, based on height from doLayout
        inventory.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        inventory.setFont(getFont());
        // support double-click:
        inventory.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() < 2)
                    return;
                e.consume();
                clickPlayCardButton();  // assumes first click has selected an item to play
            }
        });
        inventoryScroll = new JScrollPane(inventory);
        add(inventoryScroll);

        final String pieces_available_to_place = strings.get("hpan.pieces.available");

        roadSq = new ColorSquare(ColorSquare.GREY, 0);
        add(roadSq);
        roadSq.setToolTipText(pieces_available_to_place);
        roadSq.setToolTipLowWarningLevel(strings.get("hpan.roads.almostout"), 2);  // "Almost out of roads to place"
        roadSq.setToolTipZeroText(strings.get("hpan.roads.out"));  // "No more roads available"
        roadLab = new JLabel(strings.get("hpan.roads"));  // "Roads:"
        roadLab.setFont(DIALOG_PLAIN_10);
        add(roadLab);

        settlementSq = new ColorSquare(ColorSquare.GREY, 0);
        add(settlementSq);
        settlementSq.setToolTipText(pieces_available_to_place);
        settlementSq.setToolTipLowWarningLevel(strings.get("hpan.stlmts.almostout"), 1);
        settlementSq.setToolTipZeroText(strings.get("hpan.stlmts.out"));
        settlementLab = new JLabel(strings.get("hpan.stlmts"));  // "Stlmts:"
        settlementLab.setFont(DIALOG_PLAIN_10);
        add(settlementLab);

        citySq = new ColorSquare(ColorSquare.GREY, 0);
        add(citySq);
        citySq.setToolTipText(pieces_available_to_place);
        citySq.setToolTipLowWarningLevel(strings.get("hpan.cities.almostout"), 1);
        citySq.setToolTipZeroText(strings.get("hpan.cities.out"));
        cityLab = new JLabel(strings.get("hpan.cities"));  // "Cities:"
        cityLab.setFont(DIALOG_PLAIN_10);
        add(cityLab);

        if (game.hasSeaBoard)
        {
            shipSq = new ColorSquare(ColorSquare.GREY, 0);
            add(shipSq);
            shipSq.setToolTipText(pieces_available_to_place);
            shipSq.setToolTipLowWarningLevel(strings.get("hpan.ships.almostout"), 2);
            shipSq.setToolTipZeroText(strings.get("hpan.ships.out"));
            shipLab = new JLabel(strings.get("hpan.ships"));  // "Ships:"
            shipLab.setFont(DIALOG_PLAIN_10);
            add(shipLab);
        } else {
            // shipSq, shipLab already null
        }

        if (game.isGameOptionSet(SOCGameOption.K_SC_CLVI))
        {
            clothLab = new JLabel(strings.get("hpan.cloth"));  // No trailing space (room for wider colorsquares at left)
            clothLab.setFont(DIALOG_PLAIN_10);
            add(clothLab);
            clothSq = new ColorSquare(ColorSquare.GREY, 0);
            add(clothSq);
            clothSq.setToolTipText(strings.get("hpan.cloth.amounttraded"));  // "Amount of cloth traded from villages"
        }
        else if (game.isGameOptionSet(SOCGameOption.K_SC_WOND))
        {
            wonderLab = new JLabel("");  // Blank at wonder level 0; other levels' text set by updateValue(WonderLevel)
            wonderLab.setFont(DIALOG_PLAIN_10);  // same font as larmyLab, lroadLab
            add(wonderLab);
            wonderLab.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    pi.buildingPanel.clickWondersButton();
                }
            });
        } else {
            // clothSq, clothLab, wonderLab already null
        }

        knightsLab = new JLabel(strings.get("hpan.soldiers"));  // No trailing space (room for wider colorsquares at left)
        knightsLab.setFont(DIALOG_PLAIN_10);
        add(knightsLab);
        knightsSq = new ColorSquare(ColorSquare.GREY, 0);
        add(knightsSq);
        knightsSq.setToolTipText(strings.get("hpan.soldiers.sizearmy"));  // "Size of this army"

        resourceLab = new JLabel(RESOURCES);
        add(resourceLab);
        resourceSq = new ColorSquare(ColorSquare.GREY, 0);
        add(resourceSq);
        resourceSq.setToolTipText(strings.get("hpan.amounthand"));  // "Amount in hand"
        resourceSq.setToolTipHighWarningLevel(strings.get("hpan.rsrc.roll7discard"), 8); // "If 7 is rolled, would discard half these resources"

        developmentLab = new JLabel(strings.get("hpan.devcards") + " ");  // "Dev. Cards: "
        add(developmentLab);
        developmentSq = new ColorSquare(ColorSquare.GREY, 0);
        add(developmentSq);
        developmentSq.setToolTipText(strings.get("hpan.amounthand"));  // "Amount in hand"

        sittingRobotLockBut = new JButton(ROBOTLOCKBUT_U);  // button text will change soon in updateSeatLockButton()
        sittingRobotLockBut.addActionListener(this);
        sittingRobotLockBut.setEnabled(interactive);
        add(sittingRobotLockBut);

        takeOverBut = new JButton(TAKEOVER);
        takeOverBut.addActionListener(this);
        takeOverBut.setEnabled(interactive);
        add(takeOverBut);

        sitBut = new JButton(SIT);
        sitBut.addActionListener(this);
        sitBut.setEnabled(interactive);
        add(sitBut);
        sitButIsLock = false;

        robotBut = new JButton(ROBOT);
        robotBut.addActionListener(this);
        robotBut.setEnabled(interactive);
        add(robotBut);

        playCardBut = new JButton(CARD);
        playCardBut.addActionListener(this);
        playCardBut.setEnabled(interactive);
        add(playCardBut);

        playerTradingDisabled = game.isGameOptionSet("NT");

        giveLab = new JLabel(GIVE);
        add(giveLab);
        if (interactive)
            giveLab.setToolTipText(strings.get("hpan.trade.igive.tip"));
                // "Resources to give to other players or the bank"

        getLab = new JLabel(GET);
        add(getLab);
        if (interactive)
            getLab.setToolTipText(strings.get("hpan.trade.iget.tip"));
                // "Resources to get from other players or the bank"

        sqPanel = new SquaresPanel(interactive, this);
        add(sqPanel);
        sqPanel.setVisible(false); // will become visible only for seated client player

        if (playerTradingDisabled)
        {
            offerBut = null;
        } else {
            offerBut = new JButton(SEND);
            offerBut.addActionListener(this);
            offerBut.setEnabled(interactive);
            add(offerBut);
            if (interactive)
                offerBut.setToolTipText(OFFERBUTTIP_ENA);
        }

        // clearOfferBut used by bank/port trade, and player trade
        clearOfferBut = new JButton(CLEAR);
        clearOfferBut.addActionListener(this);
        clearOfferBut.setEnabled(interactive);
        add(clearOfferBut);

        bankBut = new JButton(BANK);
        bankBut.addActionListener(this);
        bankBut.setEnabled(interactive);
        add(bankBut);
        if (interactive)
            bankBut.setToolTipText(strings.get("hpan.trade.bankport.tip"));
                // "Trade these resources with the bank or a port"

        bankUndoBut = new JButton(BANK_UNDO);
        bankUndoBut.addActionListener(this);
        bankUndoBut.setEnabled(false);
        add(bankUndoBut);

        if (playerTradingDisabled)
        {
            // playerSend, playerSendMap, playerSendForPrevTrade already null
        } else {
            playerSend = new ColorSquare[game.maxPlayers-1];
            playerSendMap = new int[game.maxPlayers-1];
            playerSendForPrevTrade = new boolean[game.maxPlayers-1];

            // set the trade buttons correctly
            int cnt = 0;
            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                if (pn != playerNumber)
                {
                    Color color = playerInterface.getPlayerColor(pn);
                    playerSendMap[cnt] = pn;
                    playerSendForPrevTrade[cnt] = true;
                    playerSend[cnt] = new ColorSquare(ColorSquare.CHECKBOX, true, color);
                    playerSend[cnt].setColor(playerInterface.getPlayerColor(pn));
                    playerSend[cnt].setBoolValue(true);
                    add(playerSend[cnt]);
                    cnt++;
                }
            }
        }  // if(playerTradingDisabled)

        rollPromptCountdownLab = new JLabel(" ");
        add(rollPromptCountdownLab);
        rollPromptInUse = false;   // Nothing yet (no game in progress)
        autoRollTimerTask = null;  // Nothing yet

        rollBut = new JButton(ROLL);
        rollBut.addActionListener(this);
        rollBut.setEnabled(interactive);
        add(rollBut);

        doneBut = new JButton(DONE);
        doneBut.addActionListener(this);
        doneBut.setEnabled(interactive);
        doneButIsRestart = false;
        add(doneBut);

        quitBut = new JButton(QUIT);
        quitBut.addActionListener(this);
        quitBut.setEnabled(interactive);
        add(quitBut);

        offer = new TradeOfferPanel(this, playerNumber);
        offer.setVisible(false);
        offerIsResetMessage = false;
        add(offer);

        // Set tooltip appearance to look like rest of SOCHandPanel; currently only this panel uses Swing tooltips
        if (! didSwingTooltipDefaults)
        {
            UIManager.put("ToolTip.foreground", COLOR_FOREGROUND);
            UIManager.put("ToolTip.background", Color.WHITE);
            UIManager.put("ToolTip.font", DIALOG_PLAIN_10);

            didSwingTooltipDefaults = true;
        }

        // Make all labels and buttons use panel's font and background color.
        // To not cut off wide button text, remove button margin since we're using custom layout anyway
        final Insets minMargin = new Insets(2, 2, 2, 2);
        final Font panelFont = getFont();
        for (Component co : getComponents())
        {
            if (! ((co instanceof JLabel) || (co instanceof JButton)))
                continue;

            if ((co.getFont() != DIALOG_PLAIN_10) && (null == ((JComponent) co).getClientProperty(FONT_SKIP_FLAG)))
                co.setFont(panelFont);

            if (co instanceof JLabel)
                co.setForeground(null);  // inherit panel's color
            else
                ((JButton) co).setMargin(minMargin);

            co.setBackground(null);  // inherit panel's bg color; required for win32 to avoid gray corners on JButton
        }

        // set the starting state of the panel
        removePlayer();
    }

    /** Color square label created by most recent call to {@link #createAndAddResourceColorSquare(Color, String)}. */
    private JLabel createColorSqRetLbl;

    /** Color square created by most recent call to {@link #createAndAddResourceColorSquare(Color, String)}. */
    private ColorSquare createColorSqRetSq;

    /**
     * Create a ColorSquare and its Label, with the given text key, and add them to the layout.
     * The new colorsquare and label will be "returned" by setting the
     * {@link #createColorSqRetSq} and {@link #createColorSqRetLbl} fields.
     *
     * @param rc      Color for the square, such as {@link ColorSquare#CLAY}
     * @param rtxtkey Text key for i18n, such as {@code "resources.clay"}.
     *                If this key gives the text "Clay", the label will be "Clay:" and the
     *                tooltip will be "Right-click to trade clay".
     * @since 2.0.00
     */
    private final void createAndAddResourceColorSquare(final Color rc, final String rtxtkey)
    {
        final String rtxt = strings.get(rtxtkey);
        createColorSqRetLbl = new JLabel(rtxt + ":");  // "Clay:"
        add(createColorSqRetLbl);
        createColorSqRetSq = new ColorSquare(rc, 0);
        add(createColorSqRetSq);
        createColorSqRetSq.setToolTipText
            (strings.get("hpan.trade.rightclick", rtxt.toLowerCase()));  // "Right-click to trade clay"
    }

    /**
     * @return the player interface
     */
    public SOCPlayerInterface getPlayerInterface()
    {
        return playerInterface;
    }

    /**
     * @return the player
     */
    public SOCPlayer getPlayer()
    {
        return player;
    }

    /**
     * @return the client
     */
    public SOCPlayerClient getClient()
    {
        return client;
    }

    /**
     * @return the game
     */
    public SOCGame getGame()
    {
        return game;
    }

    /**
     * handle interaction
     */
    public void actionPerformed(ActionEvent e)
    {
        try {
        String target = e.getActionCommand();

        SOCPlayerClient client = playerInterface.getClient();
        SOCGame game = playerInterface.getGame();

        if (target == LOCKSEAT)
        {
            // Seat Lock while game forming (gamestate NEW); see below for ROBOTLOCKBUT_L etc
            client.getGameMessageMaker().setSeatLock(game, playerNumber, SOCGame.SeatLockState.LOCKED);
        }
        else if (target == UNLOCKSEAT)
        {
            // Unlock while game forming
            client.getGameMessageMaker().setSeatLock(game, playerNumber, SOCGame.SeatLockState.UNLOCKED);
        }
        else if (target == TAKEOVER)
        {
            client.getGameMessageMaker().sitDown(game, playerNumber);
        }
        else if (target == SIT)
        {
            client.getGameMessageMaker().sitDown(game, playerNumber);
        }
        else if ((target == START) && startBut.isVisible())
        {
            client.getGameMessageMaker().startGame(game);

            // checks isVisible to guard against button action from hitting spacebar
            // when hidden but has focus because startBut is the first button added to panel;
            // this bug seen on OSX 10.9.1 (1.5.0 JVM)
        }
        else if (target == ROBOT)
        {
            // cf.cc.addRobot(cf.cname, playerNum);
        }
        else if (target == ROLL)
        {
            if (autoRollTimerTask != null)
            {
                autoRollTimerTask.cancel();
                autoRollTimerTask = null;
            }
            clickRollButton();
        }
        else if (target == QUIT)
        {
            SOCQuitConfirmDialog.createAndShow(playerInterface.getMainDisplay(), playerInterface);
        }
        else if (target == DONE)
        {
            // sqPanel.setValues(zero, zero);
            client.getGameMessageMaker().endTurn(game);
        }
        else if (target == DONE_RESTART)
        {
            playerInterface.resetBoardRequest(game.isPractice && ! game.isInitialPlacement());
        }
        else if (target == CLEAR)
        {
            clearOffer(true);    // Zero the square panel numbers, unless board-reset vote in progress
            if (game.getGameState() == SOCGame.PLAY1)
            {
                client.getGameMessageMaker().clearOffer(game);
            }
        }
        else if (target == BANK)
        {
            int gstate = game.getGameState();
            if (gstate == SOCGame.PLAY1)
            {
                int[] give = new int[5];
                int[] get = new int[5];
                sqPanel.getValues(give, get);
                createSendBankTradeRequest(game, give, get, true);
            }
            else if (gstate == SOCGame.OVER)
            {
                String msg = game.gameOverMessageToPlayer(player);
                    // msg = "The game is over; you are the winner!";
                    // msg = "The game is over; <someone> won.";
                    // msg = "The game is over; no one won.";
                playerInterface.print("* " + msg);
            }
        }
        else if (target == BANK_UNDO)
        {
            if ((bankGive != null) && (bankGet != null))
            {
                client.getGameMessageMaker().bankTrade(game, bankGet, bankGive);  // undo by reversing previous request
                bankGive = null;
                bankGet = null;
                bankUndoBut.setEnabled(false);
            }
        }
        else if (target == ROBOTLOCKBUT_L)
        {
            // Seat Lock while game in progress; see above for UNLOCKSEAT etc
            clickRobotSeatLockButton(SOCGame.SeatLockState.LOCKED);
        }
        else if (target == ROBOTLOCKBUT_U)
        {
            clickRobotSeatLockButton(SOCGame.SeatLockState.UNLOCKED);
        }
        else if (target == ROBOTLOCKBUT_M)
        {
            clickRobotSeatLockButton(SOCGame.SeatLockState.CLEAR_ON_RESET);
        }
        else if (target == SEND)
        {
            if (playerTradingDisabled)
                return;

            if (game.getGameState() == SOCGame.PLAY1)
            {
                int[] give = new int[5];
                int[] get = new int[5];
                int giveSum = 0;
                int getSum = 0;
                sqPanel.getValues(give, get);

                for (int i = 0; i < 5; i++)
                {
                    giveSum += give[i];
                    getSum += get[i];
                }

                SOCResourceSet giveSet = new SOCResourceSet(give);
                SOCResourceSet getSet = new SOCResourceSet(get);

                if (! player.getResources().contains(giveSet))
                {
                    playerInterface.print("*** " + strings.get("hpan.trade.msg.donthave"));
                        // "You can't offer what you don't have."
                }
                else if ((giveSum == 0) || (getSum == 0))
                {
                    playerInterface.print("*** " + strings.get("hpan.trade.msg.eachplayer"));
                        // "A trade must contain at least one resource from each player."
                }
                else
                {
                    // bool array elements begin as false
                    boolean[] to = new boolean[game.maxPlayers];
                    boolean toAny = false;

                    if (game.getCurrentPlayerNumber() == playerNumber)
                    {
                        for (int i = 0; i < (game.maxPlayers - 1); i++)
                        {
                            if (playerSend[i].getBoolValue() && ! game.isSeatVacant(playerSendMap[i]))
                            {
                                to[playerSendMap[i]] = true;
                                toAny = true;
                                playerSendForPrevTrade[i] = true;
                            } else {
                                playerSendForPrevTrade[i] = false;
                            }
                        }
                    }
                    else
                    {
                        // can only offer to current player
                        to[game.getCurrentPlayerNumber()] = true;
                        toAny = true;
                    }

                    if (! toAny)
                    {
                        playerInterface.print("*** " + strings.get("hpan.trade.msg.chooseoppo"));
                            // "Choose at least one opponent's checkbox."
                    }
                    else
                    {
                        SOCTradeOffer tradeOffer =
                            new SOCTradeOffer(game.getName(),
                                              playerNumber,
                                              to, giveSet, getSet);
                        client.getGameMessageMaker().offerTrade(game, tradeOffer);
                        disableBankUndoButton();
                    }
                }
            } else {
                getPlayerInterface().print("* " + strings.get("hpan.trade.msg.notnow") + "\n");
                    // "You cannot trade at this time."
            }
        }
        else if ((e.getSource() == inventory) || (e.getSource() == playCardBut))
        {
            clickPlayCardButton();
        }
        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }

    /**
     * Handle clicks on {@link #svpSq} or {@link #svpLab} to get more info.
     * @since 2.0.00
     */
    public void mouseClicked(MouseEvent e)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(strings.get("hpan.svp.total", player.getSpecialVP()));  // "Total Special Victory Points: {0}"

        ArrayList<SOCPlayer.SpecialVPInfo> svpis = player.getSpecialVPInfo();
        if ((svpis != null) && (svpis.size() > 0))
        {
            sb.append("\n");

            // null shouldn't happen: server sends svp info when SVPs are awarded,
            //  or when the client joins a game in progress.
            for (SOCPlayer.SpecialVPInfo svpi : svpis)
            {
                sb.append("\n");
                sb.append(svpi.svp + ": " + svpi.desc);  // I18N: Server sends localized desc
            }
        }

        NotifyDialog.createAndShow(playerInterface.getMainDisplay(), playerInterface, sb.toString(), null, true);
    }

    /** required stub for MouseListener */
    public void mousePressed(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mouseReleased(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mouseEntered(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mouseExited(MouseEvent e) {}

    /**
     * Create and send a bank/port trade request.
     * Remember the resources for the "undo" button.
     * If {@code isFromTradePanel} and we're also offering that trade to other players, clear the offer.
     * @param game  Our game
     * @param give  Resources to give, same format as {@link SOCResourceSet#SOCResourceSet(int[])}
     * @param get   Resources to get, same format as {@link SOCResourceSet#SOCResourceSet(int[])}
     * @param isFromTradePanel   If true, this bank/port trade request was sent from handpanel's Trade Offer panel.
     *     Otherwise was from some other UI element like a context menu.
     * @see #enableBankUndoButton()
     * @since 1.1.13
     */
    private void createSendBankTradeRequest
        (SOCGame game, final int[] give, final int[] get, final boolean isFromTradePanel)
    {
        final boolean isOldServer = (client.getServerVersion(game) < SOCStringManager.VERSION_FOR_I18N);
            // old server version won't send SOCBankTrade if successful:
            // must take some actions now instead of when that message is received

        if (isFromTradePanel && (isOldServer || (player.getCurrentOffer() != null)))
            client.getGameMessageMaker().clearOffer(game);

        SOCResourceSet giveSet = new SOCResourceSet(give);
        SOCResourceSet getSet = new SOCResourceSet(get);
        client.getGameMessageMaker().bankTrade(game, giveSet, getSet);

        bankGive = giveSet;
        bankGet = getSet;
        if (isOldServer)
            bankUndoBut.setEnabled(true);

        playerInterface.bankTradeWasFromTradePanel = isFromTradePanel;
    }

    /**
     * Disable the bank/port trade undo button.
     * Call when a non-trade game action is sent by the client.
     * @see #enableBankUndoButton()
     * @since 1.1.13
     */
    public void disableBankUndoButton()
    {
        if (bankGive == null)
            return;

        bankGive = null;
        bankGet = null;
        bankUndoBut.setEnabled(false);
    }

    /**
     * Enable the bank/port trade undo button.
     * Call when server has announced a successful bank/port trade.
     * Will not enable if the give/get resource fields weren't initialized during send
     * ({@link #createSendBankTradeRequest(SOCGame, int[], int[], boolean)} does so):
     * To use the undo button, the give/get resources must be known.
     *
     * @see #disableBankUndoButton()
     * @since 2.0.00
     */
    public void enableBankUndoButton()
    {
        if (bankGive == null)
            return;

        bankUndoBut.setEnabled(true);
    }

    /**
     * During game play, handle a click on a sitting robot's Lock/Unlock/Mark button,
     * ask the server to advance to the next seat lock state.
     * Called from {@link #actionPerformed(ActionEvent)}.
     * @param current  Current lock state/button label
     * @since 2.0.00
     */
    private final void clickRobotSeatLockButton(SOCGame.SeatLockState current)
    {
        final SOCGame.SeatLockState slNext;
        switch (current)
        {
        case UNLOCKED:  slNext = SOCGame.SeatLockState.LOCKED;  break;

        case CLEAR_ON_RESET:  slNext = SOCGame.SeatLockState.UNLOCKED;  break;

        default:  // == case LOCKED:
            if (game.isPractice || (client.sVersion >= 2000))
                slNext = SOCGame.SeatLockState.CLEAR_ON_RESET;
            else
                slNext = SOCGame.SeatLockState.UNLOCKED;  // old servers don't support CLEAR_ON_RESET

            break;
        }

        client.getGameMessageMaker().setSeatLock(game, playerNumber, slNext);
    }


    /**
     * Handle a click on the "play card" button, or double-click
     * on an item in the inventory/list of cards held.
     *<P>
     * Inventory items are almost always {@link SOCDevCard}s.
     * Some scenarios may place other items in the player's inventory,
     * such as a "gift" port being moved in {@link SOCGameOption#K_SC_FTRI _SC_FTRI}.
     * If one of these is chosen, this method calls {@link #clickPlayInventorySpecialItem(SOCInventoryItem)}.
     *<P>
     * Called from actionPerformed()
     */
    public void clickPlayCardButton()
    {
        // Check first for "Cancel"
        if (game.getGameState() == SOCGame.PLACING_INV_ITEM)
        {
            client.getGameMessageMaker().cancelBuildRequest(game, SOCCancelBuildRequest.INV_ITEM_PLACE_CANCEL);
            return;
        }

        String itemText;
        int itemNum;  // Which one to play from list?
        SOCInventoryItem itemObj = null;  // SOCDevCard or special item

        setRollPrompt(null, false);  // Clear prompt if Play Card clicked (instead of Roll clicked)

        final DefaultListModel<String> invModel = (DefaultListModel<String>) inventory.getModel();
        itemNum = inventory.getSelectedIndex();
        itemText = inventory.getSelectedValue();

        if ((itemText == null) || (itemText.length() == 0))
        {
            if (invModel.size() == 1)
            {
                // No card selected, but only one to choose from
                itemText = invModel.get(0);
                itemNum = 0;
                if (itemText.length() == 0)
                    return;
                itemObj = inventoryItems.get(0);
            } else {
                /**
                 * No card selected, multiple are in the list.
                 * See if only one card isn't a "(VP)" card, isn't new.
                 * If more than one, but they're all same type (ex.
                 * unplayed Robbers), pretend there's only one.
                 */
                itemNum = -1;  // Nothing yet
                String itemNumText = null;
                for (int i = invModel.size() - 1; i >= 0; --i)
                {
                    itemText = invModel.get(i);
                    if ((itemText != null) && (itemText.length() > 0))
                    {
                        SOCInventoryItem item = inventoryItems.get(i);
                        if (item.isPlayable())
                        {
                            // Playable (not VP card, not new) item found
                            if (itemObj == null)
                            {
                                itemNum = i;
                                itemNumText = itemText;
                                itemObj = item;
                            }
                            else if (itemObj.itype != item.itype)
                            {
                                itemNum = -1;  // More than one found, and they aren't the same type;
                                break;         // we can't auto-pick among them, so stop looking through the list.
                            }
                        }
                    }
                }

                if ((itemNum == -1) || (itemObj == null))
                {
                    playerInterface.printKeyed("hpan.devcards.clickfirst");  // * "Please click a card first to select it."
                    return;
                }

                itemText = itemNumText;
            }
        } else {
            // get selected item's Card object
            if (itemNum < inventoryItems.size())
                itemObj = inventoryItems.get(itemNum);
        }

        // At this point, itemNum is the index of the card we want,
        // and item is its text string.
        // itemCard is its SOCDevCard object (card type and new/old flag).

        if ((! playerIsCurrent) || (itemObj == null))
        {
            return;  // <--- Early Return: Not current player ---
        }

        if (itemObj.isVPItem())
        {
            playerInterface.print("*** " + strings.get("hpan.devcards.vp.secretlyplayed"));
                // "You secretly played this VP card when you bought it."
            itemNum = inventory.getSelectedIndex();
            if (itemNum >= 0)
                inventory.clearSelection();

            return;  // <--- Early Return: Can't play a VP card ---
        }

        if (itemObj.isNew())
        {
            playerInterface.print("*** " + strings.get("hpan.devcards.wait"));  // "Wait a turn before playing new cards."
            return;  // <--- Early Return: Card is new ---
        }

        if (! (itemObj instanceof SOCDevCard))
        {
            clickPlayInventorySpecialItem(itemObj);
            return;  // <--- Early Return: Special item, not a dev card ---
        }

        if (player.hasPlayedDevCard())
        {
            playerInterface.print("*** " + strings.get("hpan.devcards.oneperturn"));  // "You may play only one card per turn."
            playCardBut.setEnabled(false);
            return;
        }

        int cardTypeToPlay = -1;

        switch (itemObj.itype)
        {
        case SOCDevCardConstants.KNIGHT:
            if (game.canPlayKnight(playerNumber))
            {
                cardTypeToPlay = SOCDevCardConstants.KNIGHT;
            }
            else if (game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
            {
                playerInterface.printKeyed("hpan.devcards.warship.cannotnow");
                    // "You cannot convert a ship to a warship right now."
            }
            break;

        case SOCDevCardConstants.ROADS:
            if (game.canPlayRoadBuilding(playerNumber))
            {
                cardTypeToPlay = SOCDevCardConstants.ROADS;
            }
            else if (player.getNumPieces(SOCPlayingPiece.ROAD) == 0)
            {
                if (game.hasSeaBoard && (player.getNumPieces(SOCPlayingPiece.SHIP) == 0))
                    playerInterface.printKeyed("hpan.devcards.roads_ships.none");
                        // "You have no roads or ships left to place."
                else
                    playerInterface.printKeyed("hpan.devcards.roads.none");
                        // "You have no roads left to place."
            }
            break;

        case SOCDevCardConstants.DISC:
            if (game.canPlayDiscovery(playerNumber))
            {
                cardTypeToPlay = SOCDevCardConstants.DISC;
            }
            break;

        case SOCDevCardConstants.MONO:
            if (game.canPlayMonopoly(playerNumber))
            {
                cardTypeToPlay = SOCDevCardConstants.MONO;
            }
            break;

        default:
            playerInterface.printKeyed("hpan.devcards.interror.ctype", itemObj.itype, itemText);
                // "Internal error: Unknown card type {0,number}: {1}"

        }

        if (cardTypeToPlay != -1)
        {
            client.getGameMessageMaker().playDevCard(game, cardTypeToPlay);
            disableBankUndoButton();
        }
    }

    /**
     * Handle a click on a special inventory item (not a {@link SOCDevCard}).
     * Used only in certain scenarios.
     * @param item  Special item picked by player
     * @since 2.0.00
     */
    private final void clickPlayInventorySpecialItem(final SOCInventoryItem item)
    {
        if (item.isPlayable())
            client.getGameMessageMaker().playInventoryItem(game, item.itype);
        // else isKept, or is new;
        // clickPlayCardButton checks these and prints a message to the user.
    }

    /** Handle a click on the roll button.
     *  Called from actionPerformed() and the auto-roll timer task.
     */
    public void clickRollButton()
    {
        if (rollPromptInUse)
            setRollPrompt(null, false);  // Clear it
        client.getGameMessageMaker().rollDice(game);
        rollBut.setEnabled(false);  // Only one roll per turn
    }

    /**
     * Add the "lock" button for when a robot is currently playing in this position.
     * This is not the large "lock" button seen in empty positions when the
     * game is forming, which prevents a robot from sitting down. That button
     * is actually {@link #sitBut} with a different label.
     *<P>
     * This method was <tt>addSeatLockBut()</tt> before 1.1.07.
     */
    public void addSittingRobotLockBut()
    {
        final String lbl, tipText;
        switch (game.getSeatLock(playerNumber))
        {
        case LOCKED:
            lbl = ROBOTLOCKBUT_L;
            if (game.isPractice || (client.sVersion >= 2000))
                tipText = ROBOTLOCKBUTTIP_L;
            else
                tipText = ROBOTLOCKBUTTIP_L_OLD;
            break;

        case CLEAR_ON_RESET:
            lbl = ROBOTLOCKBUT_M;
            tipText = ROBOTLOCKBUTTIP_M;
            break;

        default:  // == case UNLOCKED:
            lbl = ROBOTLOCKBUT_U;
            if (game.isPractice || (client.sVersion >= 2000))
                tipText = ROBOTLOCKBUTTIP_U;
            else
                tipText = ROBOTLOCKBUTTIP_U_OLD;
            break;
        }

        sittingRobotLockBut.setText(lbl);
        sittingRobotLockBut.setVisible(true);
        sittingRobotLockBut.setToolTipText(tipText);
    }

    /**
     * DOCUMENT ME!
     */
    public void addTakeOverBut()
    {
        takeOverBut.setVisible(true);
    }

    /**
     * Add the "Sit Here" button. If this button has been used as
     * a "lock" button to keep out a robot, revert the label to "Sit Here"
     * unless clientHasSatAlready.
     *<P>
     * If the game's already started (state {@link SOCGame#START2A} or later),
     * the player can't sit there; this method will hide the Sit button
     * if {@code ! clientHasSatAlready}.
     *<P>
     * <b>Note:</b> Does not check if the seat is vacant (in case we're
     * removing a player, and game state is not yet updated);
     * please call {@link SOCGame#isSeatVacant(int)} before calling this.
     *
     * @param clientHasSatAlready Is the client seated in this game?
     *   If so, button label should be "lock"/"unlock" (about robots).
     *   (Added in 1.1.07)
     * @see #renameSitButLock()
     */
    public void addSitButton(boolean clientHasSatAlready)
    {
        if (! clientHasSatAlready)
        {
            if (game.getGameState() >= SOCGame.START2A)
            {
                sitBut.setVisible(false);
                return;  // <--- Early return ---
            }

            if (sitButIsLock)
            {
                sitBut.setText(SIT);
                sitButIsLock = false;
            }
        }
        else if (clientHasSatAlready && ! sitButIsLock)
        {
            renameSitButLock();
        }

        sitBut.setVisible(true);
    }

    /**
     * DOCUMENT ME!
     */
    public void addRobotButton()
    {
        robotBut.setVisible(true);
    }

    /**
     * Change the face image
     *
     * @param id  the id of the image
     */
    public void changeFace(int id)
    {
        faceImg.setFace(id);
    }


    /**
     * remove this player.
     * To prevent inconsistencies, call this <em>before</em> calling
     * {@link SOCGame#removePlayer(String)}.
     * Also called from constructor, before {@link #doLayout()}.
     * @see #addPlayer(String)
     */
    public void removePlayer()
    {
        if (blankStandIn != null)
            blankStandIn.setVisible(true);
        setVisible(false);

        //D.ebugPrintln("REMOVE PLAYER");
        //D.ebugPrintln("NAME = "+player.getName());
        vpLab.setVisible(false);
        vpSq.setVisible(false);
        if (svpSq != null)
        {
            svpLab.setVisible(false);
            svpSq.setVisible(false);
        }
        faceImg.setVisible(false);
        pname.setVisible(false);
        pname.setText("");
        roadSq.setVisible(false);
        roadLab.setVisible(false);
        settlementLab.setVisible(false);
        settlementSq.setVisible(false);
        cityLab.setVisible(false);
        citySq.setVisible(false);
        if (shipSq != null)
        {
            shipSq.setVisible(false);
            shipLab.setVisible(false);
        }
        knightsSq.setVisible(false);
        knightsLab.setVisible(false);
        if (clothSq != null)
        {
            clothLab.setVisible(false);
            clothSq.setVisible(false);
        }
        else if (wonderLab != null)
        {
            wonderLab.setVisible(false);
        }

        offer.setVisible(false);

        larmyLab.setVisible(false);
        lroadLab.setVisible(false);
        resourceLab.setVisible(false);
        resourceSq.setVisible(false);
        resourceSqDivLine.setVisible(false);

        offerHidingControls = false;
        offerCounterHidingFace = false;

        if (playerIsClient)
        {
            // Clean up, since we're leaving the game
            if (playerInterface.getClientHand() == this)
                playerInterface.setClientHand(null);
            playerIsClient = false;
        } else if (game.getGameState() == SOCGame.NEW)
        {
            // Un-hide "Sit Here" or "Lock" button
            boolean clientAlreadySitting = (playerInterface.getClientHand() != null);
            addSitButton(clientAlreadySitting);
        }

        /* Hide items in case this was our hand */
        claySq.setVisible(false);
        clayLab.setVisible(false);
        oreSq.setVisible(false);
        oreLab.setVisible(false);
        sheepSq.setVisible(false);
        sheepLab.setVisible(false);
        wheatSq.setVisible(false);
        wheatLab.setVisible(false);
        woodSq.setVisible(false);
        woodLab.setVisible(false);

        resourceTradeCost = null;
        if (resourceTradeMenu != null)
        {
            for (int i = 0; i < resourceTradeMenu.length; ++i)
            {
                if (resourceTradeMenu[i] != null)
                {
                    resourceTradeMenu[i].destroy();
                    resourceTradeMenu[i] = null;
                }
            }
            resourceTradeMenu = null;
        }

        //cardLab.setVisible(false);
        inventory.setVisible(false);
        playCardBut.setVisible(false);

        giveLab.setVisible(false);
        getLab.setVisible(false);
        sqPanel.setVisible(false);
        clearOfferBut.setVisible(false);
        bankBut.setVisible(false);
        bankUndoBut.setVisible(false);

        if (! playerTradingDisabled)
        {
            offerBut.setVisible(false);
            for (int i = 0; i < (game.maxPlayers - 1); i++)
            {
                playerSend[i].setVisible(false);
            }
        }

        rollBut.setVisible(false);
        doneBut.setVisible(false);
        quitBut.setVisible(false);

        setRollPrompt(null, true);  // Clear it, and cancel autoRollTimerTask if running

        /* other player's hand */
        developmentLab.setVisible(false);
        developmentSq.setVisible(false);
        faceImg.removeFacePopupMenu();  // Also disables left-click to change

        removeTakeOverBut();
        removeSittingRobotLockBut();

        inPlay = false;

        validate();  // doLayout() will lay things out for empty seat
        if (blankStandIn != null)
            blankStandIn.setVisible(false);
        setVisible(true);
        repaint();
    }

    /**
     * Remove elements to clean up this panel.
     * Calls removePlayer() as part of cleanup.
     * @see #gameDisconnected()
     */
    public void destroy()
    {
        removePlayer();
        removeAll();
    }

    /**
     * Game was deleted or a server/network error occurred;
     * disable all buttons to stop playing.
     * @see #destroy()
     * @since 1.2.01
     */
    public void gameDisconnected()
    {
        removeSitBut();
        removeTakeOverBut();

        JButton[] inPlayButtons
            = new JButton[] { playCardBut, offerBut, bankBut, bankUndoBut, rollBut, doneBut, sittingRobotLockBut };
        for (JButton b : inPlayButtons)
            if ((b != null) && b.isVisible() && b.isEnabled())
                b.setEnabled(false);
    }

    /**
     * Add a player (human or robot) at this currently-vacant seat position.
     * Update controls at this handpanel.
     * Also update ALL OTHER handpanels in our {@link #playerInterface} this way:
     * Remove all of the sit and take over buttons.
     * If game still forming, can lock seats (for fewer players/robots).
     *
     * @param name Name of player to add
     * @see #removePlayer()
     */
    public void addPlayer(String name)
    {
        if (blankStandIn != null)
            blankStandIn.setVisible(true);
        setVisible(false);

        /* This is visible for both our hand and opponent hands */
        if (! game.isBoardReset())
        {
            faceImg.setDefaultFace();
        }
        else
        {
            changeFace(player.getFaceId());
        }
        faceImg.setVisible(true);

        pname.setText(name);
        pname.setVisible(true);

        larmyLab.setVisible(true);
        lroadLab.setVisible(true);

        roadSq.setVisible(true);
        roadLab.setVisible(true);
        settlementSq.setVisible(true);
        settlementLab.setVisible(true);
        citySq.setVisible(true);
        cityLab.setVisible(true);
        if (shipSq != null)
        {
            shipSq.setVisible(true);
            shipLab.setVisible(true);
        }
        knightsLab.setVisible(true);
        knightsSq.setVisible(true);
        if (clothSq != null)
        {
            clothLab.setVisible(true);
            clothSq.setVisible(true);
        }
        else if (wonderLab != null)
        {
            wonderLab.setText("");  // clear previous; will be updated soon by updateValue(WonderLevel)
            wonderLab.setToolTipText(null);
            wonderLab.setVisible(true);
            // alignment is set below, after playerIsClient is known
        }

        resourceLab.setVisible(true);
        resourceSq.setVisible(true);

        if (svpSq != null)
        {
            final int newSVP = player.getSpecialVP();
            svpSq.setIntValue(newSVP);
            final boolean vis = (newSVP != 0);
            svpSq.setVisible(vis);
            svpLab.setVisible(vis);
        }

        playerIsCurrent = (game.getCurrentPlayerNumber() == playerNumber);

        if (player.getName().equals(client.getNickname()))
        {
            // this is our hand

            playerIsClient = true;
            playerInterface.setClientHand(this);

            knightsSq.setToolTipText(strings.get("hpan.soldiers.sizeyourarmy"));  // "Size of your army"
            vpSq.setToolTipText(strings.get("hpan.points.total.yours"));  // "Your victory point total"

            // show 'Victory Points' and hide "Start Button" if game in progress
            if (game.getGameState() == SOCGame.NEW)
            {
                startBut.setVisible(true);
            }
            else
            {
                vpLab.setVisible(true);
                vpSq.setVisible(true);
            }

            faceImg.addFacePopupMenu();  // Also enables left-click to change

            claySq.setVisible(true);
            clayLab.setVisible(true);
            oreSq.setVisible(true);
            oreLab.setVisible(true);
            sheepSq.setVisible(true);
            sheepLab.setVisible(true);
            wheatSq.setVisible(true);
            wheatLab.setVisible(true);
            woodSq.setVisible(true);
            woodLab.setVisible(true);
            resourceSqDivLine.setVisible(true);
            resourceSq.setBorderColor(ColorSquare.ORE);  // dark gray
            resourceLab.setText(RESOURCES_TOTAL);

            resourceTradeCost = new int[6];
            if (resourceTradeMenu != null)
            {
                // Must have forgot to call removePlayer;
                //   clean it up now
                for (int i = 0; i < resourceTradeMenu.length; ++i)
                {
                    if (resourceTradeMenu[i] != null)
                    {
                        resourceTradeMenu[i].destroy();
                        resourceTradeMenu[i] = null;
                    }
                }
            }
            else
            {
                resourceTradeMenu = new ResourceTradeTypeMenu[6];
            }
            updateResourceTradeCosts(true);

            //cardLab.setVisible(true);
            canCancelInvItemPlay = false;
            inventory.setEnabled(true);
            inventory.setVisible(true);
            playCardBut.setVisible(true);

            giveLab.setVisible(true);
            getLab.setVisible(true);
            sqPanel.setVisible(true);

            clearOfferBut.setVisible(true);
            bankBut.setVisible(true);
            if (game.isPractice || (client.sVersion >= 1113))  // server version 1.1.13 and up
                bankUndoBut.setVisible(true);

            if (! playerTradingDisabled)
            {
                offerBut.setVisible(true);
                for (int i = 0; i < (game.maxPlayers - 1); i++)
                {
                    playerSend[i].setBoolValue(true);
                    playerSend[i].setEnabled(true);
                    playerSend[i].setVisible(true);
                }
            }
            rollBut.setVisible(true);
            doneButIsRestart = ((game.getGameState() <= SOCGame.START3B)
                 || (game.getGameState() == SOCGame.OVER));
            if (doneButIsRestart)
                doneBut.setText(DONE_RESTART);
            else
                doneBut.setText(DONE);
            doneBut.setVisible(true);
            quitBut.setVisible(true);

            // Remove all of the sit and take over buttons.
            // If game still forming, can lock seats (for fewer players/robots).
            boolean gameForming = (game.getGameState() == SOCGame.NEW);
            for (int i = 0; i < game.maxPlayers; i++)
            {
                playerInterface.getPlayerHandPanel(i).removeTakeOverBut();
                if (gameForming && (i != playerNumber) && game.isSeatVacant(i))
                    playerInterface.getPlayerHandPanel(i).renameSitButLock();
                else
                    playerInterface.getPlayerHandPanel(i).removeSitBut();
            }

            updateButtonsAtAdd();  // Enable,disable the proper buttons
        }
        else
        {
            /* This is another player's hand */

            final boolean isRobot = player.isRobot();
            D.ebugPrintln("**** SOCHandPanel.addPlayer(name) ****");
            D.ebugPrintln("player.getPlayerNumber() = " + playerNumber);
            D.ebugPrintln("player.isRobot() = " + isRobot);
            D.ebugPrintln("player.getSeatLock(" + playerNumber + ") = " + game.getSeatLock(playerNumber));
            D.ebugPrintln("game.getPlayer(client.getNickname()) = " + game.getPlayer(client.getNickname()));

            knightsSq.setToolTipText(strings.get("hpan.soldiers.sizeoppoarmy"));  // "Size of this opponent's army"

            // To see if client already sat down at this game,
            // we can't call playerInterface.getClientHand() yet,
            // because it may not have been set at this point.
            // Use game.getPlayer(client.getNickname()) instead:

            final boolean clientIsASeatedPlayer = (game.getPlayer(client.getNickname()) != null);

            if (isRobot && (! clientIsASeatedPlayer)
                && (game.getSeatLock(playerNumber) != SOCGame.SeatLockState.LOCKED))
            {
                addTakeOverBut();
            }

            if (isRobot && clientIsASeatedPlayer)
            {
                addSittingRobotLockBut();
            }
            else
            {
                removeSittingRobotLockBut();
            }

            offer.addPlayer();

            vpLab.setVisible(true);
            vpSq.setVisible(true);
            resourceSq.setBorderColor(Color.BLACK);
            resourceLab.setText(RESOURCES);

            developmentLab.setVisible(true);
            developmentSq.setVisible(true);

            removeSitBut();
            removeRobotBut();
        }

        if (wonderLab != null)
            wonderLab.setHorizontalAlignment(SwingConstants.RIGHT);

        inPlay = true;

        validate();  // doLayout() will lay things out for our hand or other player's hand
        if (blankStandIn != null)
            blankStandIn.setVisible(false);
        setVisible(true);
        repaint();
    }

    /** Player is client, is current, and has no playable cards,
     *  so begin auto-roll countdown.
     *
     * Called by autoRollOrPromptPlayer when that condition is met.
     * Countdown begins with AUTOROLL_TIME seconds.
     *
     * @see #autoRollOrPromptPlayer()
     * @since 1.1.00
     */
    protected void autoRollSetupTimer()
    {
        Timer piTimer = playerInterface.getEventTimer();
        if (autoRollTimerTask != null)
            autoRollTimerTask.cancel();  // cancel any previous
        if (! game.canRollDice(playerNumber))
            return;

        // Set up to run once per second, it will cancel
        //   itself after AUTOROLL_TIME seconds.
        autoRollTimerTask = new HandPanelAutoRollTask();
        piTimer.scheduleAtFixedRate(autoRollTimerTask, 0, 1000 /* ms */ );
    }

    /**
     * Handpanel interface updates at start of each turn (not just our turn).
     * Calls {@link #updateTakeOverButton()}, and checks if current player (for hilight).
     * Called from client when server sends {@link soc.message.SOCMessage#TURN}.
     * Called also at start of game by {@link SOCPlayerInterface#updateAtGameState()},
     * because the server sends no TURN between the last road (gamestate START2B)
     * and the first player's turn (state ROLL_OR_CARD).
     * @since 1.1.00
     */
    public void updateAtTurn()
    {
        playerIsCurrent = (game.getCurrentPlayerNumber() == playerNumber);
        if (playerIsCurrent)
        {
            if (pnameActiveBG == null)
                pnameCalcColors();

            if (playerIsClient)
                updateRollDoneBankButtons();
        }

        // show current player, or for debugging, current Free Placement player
        {
            boolean showAsCurrent;
            if (! game.isDebugFreePlacement())
            {
                showAsCurrent = playerIsCurrent;
            } else {
                showAsCurrent =
                    (playerNumber == playerInterface.getBoardPanel().getPlayerNumber());
                if (pnameActiveBG == null)
                    pnameCalcColors();
            }

            if (showAsCurrent)
                pname.setBackground(pnameActiveBG);
            else
                pname.setBackground(null);  // use panel's bg color
        }

        updateTakeOverButton();
        if (playerIsClient)
        {
            final int gs = game.getGameState();
            boolean normalTurnStarting = (gs == SOCGame.ROLL_OR_CARD || gs == SOCGame.PLAY1);
            clearOffer(normalTurnStarting);  // Zero the square panel numbers, etc. (TODO) better descr.
                // at any player's turn, not just when playerIsCurrent.
            if (! playerIsCurrent)
            {
                rollBut.setEnabled(false);
                doneBut.setEnabled(false);
            }

            if (doneButIsRestart)
            {
                if (normalTurnStarting)
                {
                    doneBut.setText(DONE);
                    doneButIsRestart = false;
                } else {
                    doneBut.setEnabled(true);  // "Restart" during game-start (label DONE_RESTART)
                }
            }
            normalTurnStarting = normalTurnStarting && playerIsCurrent;
            playCardBut.setEnabled(normalTurnStarting && ! ((DefaultListModel<?>) inventory.getModel()).isEmpty());
        }

        bankGive = null;
        bankGet = null;
        if (bankUndoBut.isEnabled())
            bankUndoBut.setEnabled(false);

        // Although this method is called at the start of our turn,
        // the call to autoRollOrPromptPlayer() is not made here.
        // That call is made when the server says it's our turn to
        // roll, via a SOCRollDicePrompt message.
        // We can then avoid tracking the game's current and
        // previous states in various places in the UI;
        // the server sends such messages at other times (states)
        // besides start-of-turn.
    }

    /**
     * Client is current player; state changed.
     * Enable or disable the Roll, Done and Bank buttons.
     *<P>
     * Should not be called except by client's playerinterface.
     * Call only when if player is client and is current player.
     *<P>
     * Before v1.2.01 this method was {@code updateAtPlay1()}.
     *
     * @since 1.1.00
     */
    void updateAtOurGameState()
    {
        if (! playerIsClient)
            return;

        updateRollDoneBankButtons();
    }

    /** Enable, disable the proper buttons
     * when the client (player) is added to a game.
     * Call only if {@link #playerIsClient}.
     * @since 1.1.00
     */
    private void updateButtonsAtAdd()
    {
        if (playerIsCurrent)
        {
            updateRollDoneBankButtons();
        }
        else
        {
            rollBut.setEnabled(false);
            doneBut.setEnabled(false);
            playCardBut.setEnabled(false);
            bankBut.setEnabled(false);  // enabled by updateAtOurGameState()
        }

        bankUndoBut.setEnabled(false);
        clearOfferBut.setEnabled(false);  // No trade offer has been set yet
        if (! playerTradingDisabled)
        {
            offerBut.setEnabled(false);
            offerBut.setToolTipText(OFFERBUTTIP_DIS);
        }
    }

    /**
     * During this player's first turn, calculate the player name label's
     * background color for current player.
     * @since 1.1.00
     */
    protected void pnameCalcColors()
    {
        if (pnameActiveBG != null)
            return;
        pnameActiveBG = SOCPlayerInterface.makeGhostColor(getBackground());
    }

    /**
     * If trade offer is set/cleared, enable/disable buttons accordingly.
     * @since 1.1.00
     */
    public void sqPanelZerosChange(boolean notAllZero)
    {
        int gs = game.getGameState();
        clearOfferBut.setEnabled(notAllZero);
        if (playerTradingDisabled)
            return;

        final boolean enaOfferBut = notAllZero && ((gs == SOCGame.ROLL_OR_CARD) || (gs == SOCGame.PLAY1));
        offerBut.setEnabled(enaOfferBut);
        offerBut.setToolTipText((enaOfferBut) ? (OFFERBUTTIP_ENA) : OFFERBUTTIP_DIS);
    }

    /**
     * Callback from {@link TradeOfferPanel}.
     * For players who aren't the client:
     * If our {@link TradeOfferPanel} shows/hides the counter offer,
     * may need to rearrange or hide controls under it.
     * This should be called when in {@link TradeOfferPanel#OFFER_MODE},
     * not in {@link TradeOfferPanel#MESSAGE_MODE}.
     *
     * @param counterVisible Is the counter-offer showing?
     * @since 1.1.08
     */
    public void offerCounterOfferVisibleChanged(final boolean counterVisible)
    {
        if (! (offerCounterHidesFace || offerHidingControls))
            return;

        hideTradeMsgShowOthers(false);  // move 'offer' around if needed, hide/show faceImg.
    }

    /**
     * If the player (client) has no playable
     * cards, begin auto-roll countdown,
     * Otherwise, prompt them to roll or pick a card.
     *<P>
     * Call only if panel's player is the client, and the game's current player.
     *<P>
     * Called when server sends a
     * {@link soc.message.SOCRollDicePrompt ROLLDICEPROMPT} message.
     *
     * @see #updateAtTurn()
     * @see #autoRollSetupTimer()
     * @since 1.1.00
     */
    public void autoRollOrPromptPlayer()
    {
        updateAtTurn();  // Game state may have changed
        if (player.hasUnplayedDevCards()
                && ! player.hasPlayedDevCard())
            setRollPrompt(ROLL_OR_PLAY_CARD, false);
        else
            autoRollSetupTimer();
    }

    /**
     * Update the displayed list of player's development cards and other inventory items,
     * and enable or disable the "Play Card" button.
     *<P>
     * Enables the "Play Card" button for {@link SOCInventory#PLAYABLE PLAYABLE} cards,
     * and also for {@link SOCInventory#KEPT KEPT} cards (VP cards) so the user can
     * pick those and get a message that that they've already been played, instead of
     * wondering why they're listed but can't be played.
     *<P>
     * Updates {@link #inventory} and {@link #inventoryItems} to keep them in sync.
     * @param addedPlayable  True if the update added a dev card or item that's playable now
     */
    public void updateDevCards(final boolean addedPlayable)
    {
        SOCInventory items = player.getInventory();

        boolean hasOldCards = false;

        final DefaultListModel<String> invModel = (DefaultListModel<String>) inventory.getModel();
        synchronized (inventory.getTreeLock())
        {
            invModel.clear();
            inventoryItems.clear();

            if (addedPlayable && ! inventory.isEnabled())
                inventory.setEnabled(true);  // can become disabled in game state PLACING_INV_ITEM

            // show all new cards first, then all playable, then all kept (VP cards)
            for (int cState = SOCInventory.NEW; cState <= SOCInventory.KEPT; ++cState)
            {
                final boolean isNew = (cState == SOCInventory.NEW);

                for (final SOCInventoryItem item : items.getByState(cState))  // almost always instanceof SOCDevCard
                {
                    String itemText;
                    if (isNew)
                    {
                        itemText = DEVCARD_NEW + item.getItemName(game, false, strings);
                    } else {
                        itemText = item.getItemName(game, false, strings);
                        hasOldCards = true;
                    }

                    invModel.addElement(itemText);
                    inventoryItems.add(item);
                }
            }
        }

        playCardBut.setEnabled(hasOldCards && playerIsCurrent);
    }

    /**
     * Remove the "lock" button seen when a robot is currently playing in this position.
     *<P>
     * This method was <tt>removeSeatLockBut()</tt> before 1.1.07.
     *
     * @see #addSittingRobotLockBut()
     * @see #removeSitBut()
     * @since 1.1.00
     */
    public void removeSittingRobotLockBut()
    {
        sittingRobotLockBut.setVisible(false);
    }

    /**
     * DOCUMENT ME!
     */
    public void removeTakeOverBut()
    {
        takeOverBut.setVisible(false);
    }

    /**
     * Remove the sit-here / lockout-robot button.
     * If it's currently "lockout", revert button text to "sit-here",
     * and hide the "locked, no robot" text label.
     * @see #renameSitButLock()
     * @see #addSittingRobotLockBut()
     */
    public void removeSitBut()
    {
        if (sitBut.isVisible())
            sitBut.setVisible(false);
        if (sitButIsLock)
        {
            sitBut.setText(SIT);
            sitButIsLock = false;
            if ((player == null) || (player.getName() == null))
                pname.setVisible(false);  // Hide "Locked: No robot" text
        }
    }

    /**
     * Remove the sit-here/lockout-robot button, only if its label
     * is currently "lockout". (sitButIsLock == true).  This button
     * is also used for newly joining players to choose a seat.  If the
     * button label is "sit here", our interface is a newly joining
     * player to a game that's already started; otherwise they arrived
     * while the game was forming, and now it's started, so clean up the window.
     * @since 1.1.00
     */
    public void removeSitLockoutBut()
    {
        if (sitButIsLock)
            removeSitBut();
    }

    /**
     * If game is still forming (state NEW), and player has
     * just chosen a seat, can lock empty seats for a game
     * with fewer players/robots. This uses the same server-interface as
     * the "lock" button shown when robot is playing in the position,
     * but a different button in the client (the sit-here button).
     * @see #addSitButton(boolean)
     * @see #updateSeatLockButton()
     * @since 1.1.00
     */
    public void renameSitButLock()
    {
        if (game.getGameState() != SOCGame.NEW)
            return;  // TODO consider IllegalStateException
        final String buttonText, ttipText;
        if (game.getSeatLock(playerNumber) == SOCGame.SeatLockState.LOCKED)
        {
            buttonText = UNLOCKSEAT;  // actionPerformed target becomes UNLOCKSEAT
            ttipText = UNLOCKSEATTIP;
            pname.setText(SITLOCKED);
            pname.setVisible(true);
        }
        else
        {
            buttonText = LOCKSEAT;
            ttipText = LOCKSEATTIP;
        }
        sitBut.setText(buttonText);
        sitBut.setToolTipText(ttipText);
        sitButIsLock = true;
        validate();  // sitBut minimum width may change with text
        sitBut.repaint();
    }

    /**
     * DOCUMENT ME!
     */
    public void removeRobotBut()
    {
        robotBut.setVisible(false);
    }

    /**
     * Internal mechanism to remove start button (if visible) and add VP label.
     * Also refreshes status of "send-offer" checkboxes vs. vacant seats.
     * @since 1.1.00
     */
    public void removeStartBut()
    {
        // First, hide or show victory-point buttons
        {
            final boolean seatTaken = ! game.isSeatVacant(playerNumber);
            vpLab.setVisible(seatTaken);
            vpSq.setVisible(seatTaken);
        }

        startBut.setVisible(false);

        // Update the player-trading checkboxes
        if (! playerTradingDisabled)
        {
            for (int i = 0; i < (game.maxPlayers - 1); i++)
            {
                boolean seatTaken = ! game.isSeatVacant(playerSendMap[i]);
                playerSend[i].setBoolValue(seatTaken);
                playerSend[i].setEnabled(seatTaken);
                if (seatTaken)
                {
                    String pname = game.getPlayer(playerSendMap[i]).getName();
                    if (pname != null)
                        playerSend[i].setToolTipText(pname);
                }
            }
        }
    }

    /**
     * Display or update this player's trade offer, or hide if none.
     * If a game reset request is in progress, don't show the offer, because
     * they use the same display component ({@link #offer}).  In that case
     * the trade offer will be refreshed after the reset is cancelled.
     *<P>
     * Does not display if playerIsClient.
     *
     * @param isNewOffer  If true this is for a newly made trade offer,
     *    not a refresh based on other game or player info.
     * @param resourcesOnly  If true, instead of updating the entire offer,
     *    only show or hide "Accept" button based on the client player's resources.
     *    Calls {@link TradeOfferPanel#updateOfferButtons()}.
     *    If no offer is currently visible, does nothing.
     */
    public void updateCurrentOffer(final boolean isNewOffer, final boolean resourcesOnly)
    {
        if (inPlay)
        {
            if (resourcesOnly)
            {
                offer.updateOfferButtons();
                return;
            }

            SOCTradeOffer currentOffer = player.getCurrentOffer();

            if (currentOffer != null)
            {
                if (! (offerIsResetMessage || offerIsDiscardOrPickMessage))
                {
                    if (! playerIsClient)
                    {
                        offer.setOffer(currentOffer);
                        offer.setVisible(true);
                        if (offerHidesControls)
                            hideTradeMsgShowOthers(false);
                        offer.repaint();

                        if (isNewOffer && offer.isOfferToClientPlayer())
                            playerInterface.playSound(SOCPlayerInterface.SOUND_OFFERED_TRADE);
                    }
                }
                else
                    offerIsMessageWasTrade = true;  // Will show after voting
            }
            else
            {
                clearOffer(false);
            }
        }
    }

    /**
     * Show that this player (who isn't the client) has rejected another player's offer.
     * For robots, do nothing unless {@link SOCGame#hasTradeOffers()}, because the bots
     * will sometimes reject another player's offer after a bank trade.
     */
    public void rejectOfferShowNonClient()
    {
        if (playerIsClient)
            return;
        if (player.isRobot() && ! game.hasTradeOffers())
            return;

        offer.setMessage(strings.get("base.no.thanks.sentenc"));  // "No thanks."
        if (offerHidesControls)
            hideTradeMsgShowOthers(false);
        offer.setVisible(true);
        //validate();
        repaint();
    }

    /**
     * Client is rejecting the current offer from another player.
     * Send to the server, hide the trade message, trigger a repaint.
     * @since 1.1.08
     */
    public void rejectOfferAtClient()
    {
        client.getGameMessageMaker().rejectOffer(game);
        offer.setMessage(null);
        offer.setVisible(false);
        if (offerHidesControls)
            hideTradeMsgShowOthers(true);
        repaint();
    }

    /**
     * If the trade-offer panel is showing a message
     * (not a trade offer), clear and hide it.
     * Assumes this hand's player is not the client.
     * @see #tradeSetMessage(String)
     */
    public void clearTradeMsg()
    {
        if ((offer.getMode() == TradeOfferPanel.MESSAGE_MODE)
            && ! (offerIsResetMessage || offerIsDiscardOrPickMessage))
        {
            offer.setMessage(null);
            offer.setVisible(false);
            if (offerHidesControls)
                hideTradeMsgShowOthers(true);
            repaint();
        }
    }

    /**
     * If non-client player's handpanel isn't tall enough, when
     * the {@link #offer} message panel is showing, we must hide
     * other controls like {@link #knightsLab} and {@link #resourceSq}.
     *<P>
     * This method does <b>not</b> hide/show the trade offer;
     * other methods do that, and then if {@link #offerHidesControls},
     * call this method to show/hide the controls that would be obscured by it.
     *<P>
     * If {@link #offerCounterHidesFace}, will check {@link TradeOfferPanel#isCounterOfferMode()}
     * and redo layout (to hide/move) if needed.
     *
     * @param hideTradeMsg True if hiding the trade offer message panel and should show other controls;
     *     false if showing trade offer and should hide others
     * @see #tradeSetMessage(String)
     * @see #clearTradeMsg()
     * @see #offerHidesControls
     * @since 1.1.08
     */
    private void hideTradeMsgShowOthers(final boolean hideTradeMsg)
    {
        if ((! offerHidesControls) && resourceSq.isVisible())
            return;

        if (offerHidingControls == hideTradeMsg)
        {
            knightsLab.setVisible(hideTradeMsg);
            knightsSq.setVisible(hideTradeMsg);
            if (clothSq != null)
            {
                clothLab.setVisible(hideTradeMsg);
                clothSq.setVisible(hideTradeMsg);
            }
            else if (wonderLab != null)
            {
                wonderLab.setVisible(hideTradeMsg);
            }
            resourceLab.setVisible(hideTradeMsg);
            resourceSq.setVisible(hideTradeMsg);
            developmentLab.setVisible(hideTradeMsg);
            developmentSq.setVisible(hideTradeMsg);
            roadSq.setVisible(hideTradeMsg);
            roadLab.setVisible(hideTradeMsg);
            settlementSq.setVisible(hideTradeMsg);
            settlementLab.setVisible(hideTradeMsg);
            citySq.setVisible(hideTradeMsg);
            cityLab.setVisible(hideTradeMsg);
            if (shipSq != null)
            {
                shipSq.setVisible(hideTradeMsg);
                shipLab.setVisible(hideTradeMsg);
            }

            if (inPlay && player.isRobot())
            {
                final boolean clientAlreadySat = (null != playerInterface.getClientHand());

                if (clientAlreadySat)
                    sittingRobotLockBut.setVisible(hideTradeMsg);
                else if (game.getSeatLock(playerNumber) != SOCGame.SeatLockState.LOCKED)
                    takeOverBut.setVisible(hideTradeMsg);
            }

            offerHidingControls = ! hideTradeMsg;
        }

        if (! offerCounterHidesFace)
            return;

        final boolean counterIsShowing = offer.isCounterOfferMode();
        if (offerCounterHidingFace != counterIsShowing)
        {
            faceImg.setVisible(! counterIsShowing);
            pname.setVisible(! counterIsShowing);
            vpLab.setVisible(! counterIsShowing);
            vpSq.setVisible(! counterIsShowing);
            larmyLab.setVisible(! counterIsShowing);
            lroadLab.setVisible(! counterIsShowing);
            if (svpSq != null)
            {
                final boolean vis = (! counterIsShowing) && (player.getSpecialVP() > 0);

                svpLab.setVisible(vis);
                svpSq.setVisible(vis);

                // SC_WOND: wonderLab is next to svpSq
                if (wonderLab != null)
                    wonderLab.setVisible(vis);
            }

            offerCounterHidingFace = counterIsShowing;
            invalidate();
            doLayout();  // must move offer panel
            repaint();
        }
    }

    /**
     * Clear the current offer.
     * If player is client, clear the numbers in the resource "offer" squares,
     * and disable the "offer" and "clear" buttons (since no resources are selected).
     * Otherwise just hide the last-displayed offer.
     *
     * @param updateSendCheckboxes If true, and player is client, update the
     *    selection checkboxes for which opponents are sent the offer.
     *    If it's currently our turn, check all boxes where the seat isn't empty.
     *    Otherwise, check only the box for the opponent whose turn it is.
     */
    public void clearOffer(boolean updateSendCheckboxes)
    {
        if (! offerIsResetMessage)
        {
            offer.setVisible(false);
            offer.clearOffer();  // Clear to zero the offer and counter-offer
        }

        if (playerIsClient)
        {
            // clear the squares panel
            sqPanel.setValues(zero, zero);

            // reset the send squares (checkboxes)
            if (updateSendCheckboxes && ! playerTradingDisabled)
            {
                final int pcurr = game.getCurrentPlayerNumber();  // current player number
                final boolean pIsCurr = (pcurr == playerNumber);  // are we current?
                for (int i = 0; i < game.maxPlayers - 1; i++)
                {
                    boolean canSend, wantSend;
                    if (pIsCurr)
                    {
                        // send to any occupied seat
                        canSend = ! game.isSeatVacant(playerSendMap[i]);
                        wantSend = canSend && playerSendForPrevTrade[i];
                    } else {
                        // send only to current player
                        canSend = (pcurr == playerSendMap[i]);
                        wantSend = canSend;
                    }
                    playerSend[i].setBoolValue(wantSend);
                    playerSend[i].setEnabled(canSend);
                }
            }

            clearOfferBut.setEnabled(false);
            if (! playerTradingDisabled)
            {
                offerBut.setEnabled(false);
                offerBut.setToolTipText(OFFERBUTTIP_DIS);
            }
        }
        else if (offerHidesControls)
        {
            hideTradeMsgShowOthers(true);
        }

        validate();
        repaint();
    }

    /**
     * Show or hide a message in the trade-panel.
     * Should not be client player, only other players.
     * Sets offerIsMessageWasTrade, but does not set boolean modes (offerIsResetMessage, offerIsDiscardMessage, etc.)
     * Will clear boolean modes if message null.
     *
     * @param message Message to show, or null to hide (and return tradepanel to previous display, if any)
     */
    private void tradeSetMessage(String message)
    {
        if (playerIsClient)
            return;

        if (message != null)
        {
            offerIsMessageWasTrade = (offer.isVisible() && (offer.getMode() == TradeOfferPanel.OFFER_MODE));
            offer.setMessage(message);
            if (offerHidesControls)
                hideTradeMsgShowOthers(false);
            offer.setVisible(true);
            repaint();
        } else {
            // restore previous state of offer panel
            offerIsDiscardOrPickMessage = false;
            offerIsResetMessage = false;
            if ((! offerIsMessageWasTrade) || (! inPlay))
                clearTradeMsg();
            else
                updateCurrentOffer(false, false);
        }
    }

    /**
     * Show or hide a message related to board-reset voting.
     *
     * @param message Message to show, or null to hide
     * @throws IllegalStateException if offerIsDiscardMessage true when called
     */
    public void resetBoardSetMessage(String message)
        throws IllegalStateException
    {
        if (! inPlay)
            return;
        if (offerIsDiscardOrPickMessage)
            throw new IllegalStateException("Cannot call resetmessage when discard msg");

        tradeSetMessage(message);
        offerIsResetMessage = (message != null);
    }

    /**
     * Show the "discarding..." or "picking resources..." message in the trade panel.
     * Indicates discard on a 7, or picking resources on a gold hex.
     * Assumes player can't be discarding and asking for board-reset at same time.
     * Not called for the client player, only for other players.
     *<P>
     * Normally, this will be cleared by {@link #updateValue(PlayerClientListener.UpdateType)} for NUMRESOURCES,
     * because that's what the server sends all other players on the player's discard or pick.
     *
     * @see #clearDiscardOrPickMsg()
     * @see SOCPlayerInterface#discardOrPickTimerSet(boolean)
     * @param isDiscard  True to show {@link #TRADEMSG_DISCARD}, false for {@link #TRADEMSG_PICKING}.
     * @return true if set, false if not set because was in reset-mode already.
     */
    public boolean setDiscardOrPickMsg(final boolean isDiscard)
    {
        if (! inPlay)
            return false;
        if (offerIsResetMessage)
            return false;

        tradeSetMessage(isDiscard ? TRADEMSG_DISCARD : TRADEMSG_PICKING);
        offerIsDiscardOrPickMessage = true;
        return true;
    }

    /**
     * Clear the "discarding..." or "picking resources..." message in the trade panel.
     * Assumes player can't be discarding and asking for board-reset at same time.
     * If wasn't in discardMessage mode, do nothing.
     * @see #setDiscardOrPickMsg(boolean)
     */
    public void clearDiscardOrPickMsg()
    {
        if (! offerIsDiscardOrPickMessage)
            return;

        tradeSetMessage(null);
        offerIsDiscardOrPickMessage = false;
    }

    /**
     * update the takeover button so that it only
     * allows takover when it's not the robot's turn
     */
    public void updateTakeOverButton()
    {
        if ((game.getSeatLock(playerNumber) != SOCGame.SeatLockState.LOCKED) &&
            (game.getCurrentPlayerNumber() != playerNumber))
        {
            takeOverBut.setText(TAKEOVER);
        } else {
            takeOverBut.setText(SEAT_LOCKED);
        }
    }

    /**
     * Client is current player; enable or disable buttons according to game state:
     * {@link #rollBut}, {@link #doneBut}, {@link #bankBut}.
     * Call only if {@link #playerIsCurrent} and {@link #playerIsClient}.
     *<P>
     * v2.0.00+: In game state {@link SOCGame#PLACING_INV_ITEM}, the Play Card button's label
     * becomes {@link #CANCEL}, and {@link #inventory} is disabled, while the player places
     * an item on the board.  They can hit Cancel to return the item to their inventory instead.
     * (Checks the flag set in {@link #setCanCancelInvItemPlay(boolean)}.)
     * Once that state is over, button and inventory return to normal.
     *<P>
     * Before v1.2.01 this method was {@code updateRollButton()}.
     *
     * @since 1.1.00
     */
    private void updateRollDoneBankButtons()
    {
        final int gs = game.getGameState();
        rollBut.setEnabled(gs == SOCGame.ROLL_OR_CARD);
        doneBut.setEnabled
            ((gs <= SOCGame.START3B) || doneButIsRestart || game.canEndTurn(playerNumber));
        bankBut.setEnabled(gs == SOCGame.PLAY1);

        if (game.hasSeaBoard)
        {
            if (gs == SOCGame.PLACING_INV_ITEM)
            {
                // in this state only, "Play Card" becomes "Cancel"
                SOCInventoryItem placing = game.getPlacingItem();
                if (placing != null)
                    canCancelInvItemPlay = placing.canCancelPlay;
                inventory.setEnabled(false);
                playCardBut.setText(CANCEL);
                playCardBut.setEnabled(canCancelInvItemPlay);
            } else {
                if (! inventory.isEnabled())
                    inventory.setEnabled(true);  // note, may still visually appear disabled; repaint doesn't fix it

                if (playCardBut.getText().equals(CANCEL))
                {
                    playCardBut.setText(CARD);  // " Play Card "
                    playCardBut.setEnabled(! inventoryItems.isEmpty());
                }
            }
        }
    }

    /**
     * update the seat lock button so that it
     * allows a player to lock an unlocked seat
     * and vice versa. Called from client when server
     * sends a SETSEATLOCK message. Updates both
     * buttons: The robot-seat-lock (when robot playing at
     * this position) and the robot-lockout (game forming,
     * seat vacant, no robot here please) buttons.
     * @since 1.1.00
     */
    public void updateSeatLockButton()
    {
        final SOCGame.SeatLockState sl = game.getSeatLock(playerNumber);

        final String lbl, tipText;
        switch (sl)
        {
        case LOCKED:
            lbl = ROBOTLOCKBUT_L;
            if (game.isPractice || (client.sVersion >= 2000))
                tipText = ROBOTLOCKBUTTIP_L;
            else
                tipText = ROBOTLOCKBUTTIP_L_OLD;
            break;

        case CLEAR_ON_RESET:
            lbl = ROBOTLOCKBUT_M;
            tipText = ROBOTLOCKBUTTIP_M;
            break;

        default:  // == case UNLOCKED:
            lbl = ROBOTLOCKBUT_U;
            if (game.isPractice || (client.sVersion >= 2000))
                tipText = ROBOTLOCKBUTTIP_U;
            else
                tipText = ROBOTLOCKBUTTIP_U_OLD;
            break;
        }

        sittingRobotLockBut.setText(lbl);
        sittingRobotLockBut.setToolTipText(tipText);

        if (sitButIsLock)
        {
            // game is still forming, so update the other "lock" button.

            boolean noPlayer = (player == null) || (player.getName() == null);
            final String buttonText, ttipText;
            if (sl == SOCGame.SeatLockState.LOCKED)
            {
                buttonText = UNLOCKSEAT;
                ttipText = UNLOCKSEATTIP;
                if (noPlayer)
                {
                    pname.setText(SITLOCKED);
                    pname.setVisible(true);
                }
            }
            else
            {
                buttonText = LOCKSEAT;
                ttipText = LOCKSEATTIP;
                if (noPlayer)
                {
                    pname.setText(" ");
                    pname.setVisible(false);
                }
            }
            sitBut.setText(buttonText);
            sitBut.setToolTipText(ttipText);

            validate();  // sitBut minimum width may change with text
            repaint();
        }
    }

    /**
     * turn the "largest army" label on or off
     *
     * @param haveIt  true if this player has the largest army
     */
    protected void setLArmy(boolean haveIt)
    {
        larmyLab.setText(haveIt ? strings.get("hpan.L.army") : "");  // "L. Army"
        larmyLab.setToolTipText(haveIt ? strings.get("hpan.L.army.tip") : null);  // "Largest Army"
    }

    /**
     * Turn the "longest road" label on or off.  If the game uses the large sea board,
     * the label shows "L. Route" instead of "L. Road".
     *
     * @param haveIt  true if this player has the longest road
     */
    protected void setLRoad(boolean haveIt)
    {
        lroadLab.setText(haveIt ? (game.hasSeaBoard ? strings.get("hpan.L.route") : strings.get("hpan.L.road")) : "");
            // "L. Route" / "L. Road"
        lroadLab.setToolTipText
            (haveIt ? (game.hasSeaBoard ? strings.get("hpan.L.route.tip") : strings.get("hpan.L.road.tip")) : null);
            // "Longest Trade Route" / "Longest Road"
    }

    /**
     * This player is playing or placing a special {@link SOCInventoryItem}, such as a gift
     * trade port in scenario {@link SOCGameOption#K_SC_FTRI _SC_FTRI}.  Set a flag that
     * indicates if this play or placement can be canceled (returned to player's inventory).
     *<P>
     * Should be called only for our own client player, not other players.
     *<P>
     * Should be called before entering game state {@link SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM}.
     * In that state, {@link #updateRollDoneBankButtons()} checks this flag to see if the
     * "Cancel" button should be enabled.
     *
     * @param canCancel  True if {@link SOCInventoryItem#canCancelPlay}
     */
    void setCanCancelInvItemPlay(final boolean canCancel)
    {
        canCancelInvItemPlay = canCancel;

        if (playerIsClient && playCardBut.getText().equals(CANCEL))  // should not be Cancel yet; check just in case
            playCardBut.setEnabled(canCancel);
    }

    /**
     * update the value of a player element.
     * Call this after updating game data.
     *<P>
     * If {@link #VICTORYPOINTS} is updated, and game state is {@link SOCGame#OVER}, check for winner
     * and update (player name label, victory-points tooltip, disable bank/trade btn)
     *
     * @param utype  the type of value update, such as {@link #VICTORYPOINTS}
     *            or {@link PlayerClientListener.UpdateType#Sheep}.
     */
    public void updateValue(PlayerClientListener.UpdateType utype)
    {
        boolean updateTotalResCount = false;

        /**
         * We say that we're getting the total vp, but
         * for other players this will automatically get
         * the public vp because we will assume their
         * dev card vp total is zero.
         */
        switch (utype)
        {
        case VictoryPoints:
            {
                int newVP = player.getTotalVP();
                vpSq.setIntValue(newVP);
                if (game.getGameState() == SOCGame.OVER)
                {
                    if (game.getPlayerWithWin() == player)
                    {
                        vpSq.setToolTipText(strings.get("hpan.winner.label.ttwithvp", newVP));  // "Winner with 12 victory points"
                        pname.setText(strings.get("hpan.winner.label", player.getName()));  // "X - Winner"
                    }
                    if (interactive)
                    {
                        bankBut.setEnabled(false);
                        bankUndoBut.setEnabled(false);
                        playCardBut.setEnabled(false);
                    }
                    doneBut.setText(DONE_RESTART);
                    doneBut.setEnabled(true);  // In case it's another player's turn
                    doneButIsRestart = true;
                }
            }
            break;

        case SpecialVictoryPoints:
            if (svpSq != null)
            {
                final int newSVP = player.getSpecialVP();
                svpSq.setIntValue(newSVP);
                final boolean vis = (newSVP != 0) && ! offerCounterHidingFace;
                svpSq.setVisible(vis);
                svpLab.setVisible(vis);
            }
            break;

        case LongestRoad:

            setLRoad(player.hasLongestRoad());

            break;

        case LargestArmy:

            setLArmy(player.hasLargestArmy());

            break;

        case Clay:
            claySq.setIntValue(player.getResources().getAmount(SOCResourceConstants.CLAY));
            updateTotalResCount = true;
            break;

        case Ore:
            oreSq.setIntValue(player.getResources().getAmount(SOCResourceConstants.ORE));
            updateTotalResCount = true;
            break;

        case Sheep:
            sheepSq.setIntValue(player.getResources().getAmount(SOCResourceConstants.SHEEP));
            updateTotalResCount = true;
            break;

        case Wheat:
            wheatSq.setIntValue(player.getResources().getAmount(SOCResourceConstants.WHEAT));
            updateTotalResCount = true;
            break;

        case Wood:
            woodSq.setIntValue(player.getResources().getAmount(SOCResourceConstants.WOOD));
            updateTotalResCount = true;
            break;

        case ResourceTotalAndDetails:
            if (playerIsClient)
            {
                // Update the 5 individual ones too, not just the total count
                final SOCResourceSet rsrc = player.getResources();
                claySq.setIntValue (rsrc.getAmount(SOCResourceConstants.CLAY));
                oreSq.setIntValue  (rsrc.getAmount(SOCResourceConstants.ORE));
                sheepSq.setIntValue(rsrc.getAmount(SOCResourceConstants.SHEEP));
                wheatSq.setIntValue(rsrc.getAmount(SOCResourceConstants.WHEAT));
                woodSq.setIntValue (rsrc.getAmount(SOCResourceConstants.WOOD));
            }
            // fall through to case Resources

        case Resources:
            updateTotalResCount = true;
            break;

        case Road:
            roadSq.setIntValue(player.getNumPieces(SOCPlayingPiece.ROAD));
            break;

        case Settlement:
            settlementSq.setIntValue(player.getNumPieces(SOCPlayingPiece.SETTLEMENT));
            if (playerIsClient)
                updateResourceTradeCosts(false);
            break;

        case City:
            citySq.setIntValue(player.getNumPieces(SOCPlayingPiece.CITY));
            break;

        case Ship:
            if (shipSq != null)
                shipSq.setIntValue(player.getNumPieces(SOCPlayingPiece.SHIP));
            break;

        case DevCards:

            developmentSq.setIntValue(player.getInventory().getTotal());

            break;

        case Knight:
            knightsSq.setIntValue(player.getNumKnights());
            break;

        case Cloth:
            if (clothSq != null)
                clothSq.setIntValue(player.getCloth());
            break;

        case WonderLevel:
            if (wonderLab != null)
            {
                SOCSpecialItem pWond = player.getSpecialItem(SOCGameOption.K_SC_WOND, 0);
                final int pLevel = (pWond != null) ? pWond.getLevel() : 0;
                if (pLevel == 0)
                {
                    wonderLab.setText("");
                    wonderLab.setToolTipText(null);
                } else {
                    String ofWonder = null;
                    try {
                        String sv = pWond.getStringValue();  // "w3"
                        if (sv != null)
                            ofWonder = strings.get("game.specitem.sc_wond.of_" + sv);  // "of the Monument"
                    } catch (MissingResourceException e) {
                        try {
                            ofWonder = strings.get("game.specitem.sc_wond.of_fallback");  // "of a Wonder"
                        }
                        catch (MissingResourceException e2) {
                            ofWonder = "of a Wonder";
                        }
                    }
                    wonderLab.setText(strings.get("hpan.wonderlevel", pLevel));  // "Wonder Level: #"
                    wonderLab.setToolTipText
                        (strings.get("hpan.wonderlevel.tip", pLevel, SOCSpecialItem.SC_WOND_WIN_LEVEL, ofWonder));
                        // "Built # of # levels of the Monument"
                }
            }
            break;

        case GoldGains:
        case Warship:
        case Unknown:
            // do nothing (avoid compiler enum warning).
            //    We don't use a default case, so that future enum values will be considered when the warning appears.
        }

        if (updateTotalResCount)
        {
            resourceSq.setIntValue(player.getResources().getTotal());

            if (offerIsDiscardOrPickMessage)
            {
                final int gs = game.getGameState();
                if (gs != SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
                {
                    clearDiscardOrPickMsg();
                } else {
                    // Clear pick-resources message is handled above
                    // by updateValue(NUM_PICK_GOLD_HEX_RESOURCES)
                }
            }
        }
    }

    /**
     * This player must pick this many gold hex resources, or no longer needs to pick them.
     * Called after {@link SOCPlayer#setNeedToPickGoldHexResources(int)}.
     * Informational only: do not display a {@link SOCDiscardOrGainResDialog}.
     *<P>
     * "Clear" is handled here (has picked, numPick == 0, no longer needs to pick some).
     * "Set" (numPick &gt; 0) is handled in {@link SOCPlayerInterface#updateAtGameState()}
     * which will display "Picking resources..." in the handpanel for any non-client
     * players who need to pick.
     */
    public void updatePickGoldHexResources()
    {
        if (offerIsDiscardOrPickMessage && (0 == player.getNeedToPickGoldHexResources()))
        {
            clearDiscardOrPickMsg();
            // Clear is handled here.
            // Set is handled in SOCPlayerInterface.updateAtGameState
            // by setting a timer: SOCPlayerInterface.discardOrPickTimerSet(false)
        }
    }

    /**
     * Re-read player's resource info and victory points, update the
     * display and resource trade costs and resourceTradeMenu text.
     * @since 1.1.00
     */
    public void updateResourcesVP()
    {
        updateValue(PlayerClientListener.UpdateType.ResourceTotalAndDetails);

        if (playerIsClient)
            updateResourceTradeCosts(false);

        updateValue(PlayerClientListener.UpdateType.VictoryPoints);
    }

    /**
     * If playerIsClient, update cost of bank/port trade per resource.
     * Update resourceTradeCost numbers and resourceTradeMenu text.
     *
     * @param doInit If true, fill resourceTradeMenu[] with newly constructed menus.
     */
    public void updateResourceTradeCosts(boolean doInit)
    {
        boolean has3Port = player.getPortFlag(SOCBoard.MISC_PORT);

        for (int i = SOCResourceConstants.CLAY;
                i <= SOCResourceConstants.WOOD; ++i)
        {
            int oldCost = resourceTradeCost[i];
            int newCost;
            if (player.getPortFlag(i))
                newCost = 2;
            else if (has3Port)
                newCost = 3;
            else
                newCost = 4;

            if (doInit || (newCost != oldCost))
            {
                resourceTradeCost[i] = newCost;

                /**
                 * Update menu text
                 */
                if (! doInit)
                {
                    resourceTradeMenu[i].updateCost(newCost);
                }
                else
                {
                    ColorSquare resSq;
                    switch (i)
                    {
                    case SOCResourceConstants.CLAY:
                        resSq = claySq;  break;
                    case SOCResourceConstants.ORE:
                        resSq = oreSq;   break;
                    case SOCResourceConstants.SHEEP:
                        resSq = sheepSq; break;
                    case SOCResourceConstants.WHEAT:
                        resSq = wheatSq; break;
                    case SOCResourceConstants.WOOD:
                        resSq = woodSq;  break;
                    default:
                        // Should not happen
                        resSq = null;
                    }
                    resourceTradeMenu[i] = new
                        ResourceTradeTypeMenu(this, i, resSq, newCost);
                }
            }
        }
    }

    /**
     * Is this panel showing the client's player?
     * @see #isClientAndCurrentPlayer()
     * @see SOCPlayerInterface#getClientHand()
     */
    public boolean isClientPlayer()
    {
        return (playerIsClient);
    }

    /**
     * Is this panel showing the client's player,
     * and is that player the game's current player?
     *<P>
     * Note that because of the order of network messages,
     * after this player's turn, there's a brief time when
     * the state becomes ROLL_OR_CARD again, before the current player
     * is changed to the next player.  So, it appears that
     * this player can roll again, but they cannot.
     * To guard against this, use {@link #isClientAndCurrentlyCanRoll()} instead.
     *
     * @see SOCPlayerInterface#clientIsCurrentPlayer()
     * @since 1.1.00
     */
    public boolean isClientAndCurrentPlayer()
    {
        return (playerIsClient && playerIsCurrent);
    }

    /**
     * Is this panel showing the client's player,
     * and is that player the game's current player,
     * and are they able to roll the dice right now?
     * @since 1.1.09
     */
    public boolean isClientAndCurrentlyCanRoll()
    {
        return playerIsClient && playerIsCurrent
           && (rollBut != null) && rollBut.isEnabled();
    }

    /** Set or clear the roll prompt / auto-roll countdown display.
     *
     * @param prompt The message to display, or null to clear it.
     * @param cancelTimer Cancel {@link #autoRollTimerTask}, for use with null prompt
     * @since 1.1.00
     */
    protected void setRollPrompt(String prompt, final boolean cancelTimer)
    {
        boolean wasUse = rollPromptInUse;
        rollPromptInUse = (prompt != null);
        if (rollPromptInUse)
        {
            rollPromptCountdownLab.setText(prompt);
            rollPromptCountdownLab.repaint();
        }
        else if (wasUse)
        {
            rollPromptCountdownLab.setText(" ");
            rollPromptCountdownLab.repaint();
        }

        if (cancelTimer)
        {
            if (autoRollTimerTask != null)
            {
                autoRollTimerTask.cancel();
                autoRollTimerTask = null;
            }
        }
    }

    /**
     * For {@link SOCPlayerInterface}'s use, to set its size and position
     * @return the stand-in blank colorsquare: not a subcomponent, but shows up when handpanel is hidden
     * @see #setBounds(int, int, int, int)
     * @since 1.1.06
     */
    public ColorSquare getBlankStandIn()
    {
        return blankStandIn;
    }

    /**
     * Overriden to also update {@link #getBlankStandIn()} bounds.
     * @since 1.1.06
     */
    @Override
    public void setBounds(int x, int y, int width, int height)
    {
        super.setBounds(x, y, width, height);
        if (blankStandIn != null)
            blankStandIn.setBounds(x, y, width, height);
    }

    /**
     * Custom layout for player hand panel.
     * Different arrangements for our hand, other player's hand, or empty seat.
     * See comments for arrangement details.
     */
    @Override
    public void doLayout()
    {
        final Dimension dim = getSize();
        final int inset = 3;  // margin from edge of panel; was 8 before 1.1.08
        final int space = 2;  // vertical and horizontal spacing between most items

        final FontMetrics fm = this.getFontMetrics(this.getFont());
        final int lineH = ColorSquare.HEIGHT;  // layout's basic line height; most rows have a ColorSquare

        if (! inPlay)
        {
            /* just show the 'sit' button */
            /* and the 'robot' button     */
            /* and pname label, centered  */

            final int sitW;
            if (fm == null)
                sitW = 70;
            else if (sitButIsLock)
            {
                final int wLock = fm.stringWidth(LOCKSEAT),
                          wUnlock = fm.stringWidth(UNLOCKSEAT);
                sitW = 24 + ((wLock > wUnlock) ? wLock : wUnlock);
            } else {
                sitW = 24 + fm.stringWidth(sitBut.getText());
            }

            sitBut.setBounds((dim.width - sitW) / 2, (dim.height - 82) / 2, sitW, 40);
            pname.setHorizontalAlignment(SwingConstants.CENTER);
            pname.setBounds(inset, inset, dim.width - (2*inset), lineH);
        }
        else
        {
            final int faceW = 40;  // face icon width
            final int pnameW = dim.width - (inset + faceW + inset + inset);  // player name width, to right of face

            // Top of panel: Face icon, player name to right (left-aligned)
            faceImg.setBounds(inset, inset, faceW, faceW);
            pname.setHorizontalAlignment(SwingConstants.LEFT);
            pname.setBounds(inset + faceW + inset, inset, pnameW, lineH);

            // To right of face, below player name:
            // Victory Points count, Largest Army, Longest Road
            final int vpW = fm.stringWidth(vpLab.getText().replace(' ','_'));
                // Bug in stringWidth does not give correct size for ' ' in some versions
            int y = inset + lineH + 2*space;
            vpLab.setBounds(inset + faceW + inset, y, vpW, lineH);
            vpSq.setBounds(inset + faceW + inset + vpW + space, y, ColorSquare.WIDTH, ColorSquare.HEIGHT);

            final int topStuffW = inset + faceW + inset + vpW + space + ColorSquare.WIDTH + space;

            // always position these: though they may not be visible
            larmyLab.setBounds(topStuffW, y, (dim.width - (topStuffW + inset + space)) / 2, lineH);
            lroadLab.setBounds(topStuffW + ((dim.width - (topStuffW + inset + space)) / 2) + space, y,
                (dim.width - (topStuffW + inset + space)) / 2, lineH);

            // SVP goes below Victory Points count; usually invisible
            if (svpSq != null)
            {
                y += (lineH + 1);
                svpLab.setBounds(inset + faceW + inset, y, vpW, lineH);
                svpSq.setBounds(inset + faceW + inset + vpW + space, y, ColorSquare.WIDTH, ColorSquare.HEIGHT);

                if (wonderLab != null)
                {
                    // SC_WOND: Show Wonder Level next to svpSq.
                    // Since SC_WOND requires game.hasSeaBoard, svpSq != null for SC_WOND.
                    final int x = svpSq.getX() + ColorSquare.WIDTH + space;
                    wonderLab.setBounds(x, y, dim.width - x - space, lineH);
                }
            }

            //if (true) {
            if (playerIsClient)
            {
                /* This is our hand */

                // Top has name, then a row with VP count, largest army, longest road
                //   (If game hasn't started yet, "Start Game" button is here instead of that row)
                //   SVP is under VP count, if applicable
                // To left below top area: Trade area
                //   (Give/Get and SquaresPanel; below that, Offer button and checkboxes, then Clear/Bank buttons)
                // To left below trade area: Resource counts
                //   (Clay, Ore, Sheep, Wheat, Wood, Total)
                // To right below top area: Piece counts
                //   (Soldiers, Roads, Settlements, Cities, Ships)
                // To right below piece counts: Dev card list, Play button
                // Bottom of panel: 1 button row: Quit to left; Roll, Restart to right

                final Dimension sqpDim = sqPanel.getSize();  // Trading SquaresPanel (doesn't include Give/Get labels)
                final int labelspc = fm.stringWidth("_") / 3;  // Bug in stringWidth does not give correct size for ' '
                final int sheepW;  // width of longest localized string clay/sheep/ore/wheat/wood
                {
                    int wmax = 0;
                    final JLabel[] rLabs = { clayLab, oreLab, sheepLab, wheatLab, woodLab };
                    for (int i = 0; i < rLabs.length; ++i)
                    {
                        final JLabel rl = rLabs[i];
                        if (rl != null)
                        {
                            final String txt = rl.getText();
                            if (txt != null)
                            {
                                final int w = fm.stringWidth(rl.getText());
                                if (w > wmax)
                                    wmax = w;
                            }
                        }
                    }
                    if (wmax == 0)
                        wmax = fm.stringWidth("Sheep:");  // fallback

                    sheepW = wmax + labelspc;
                }
                final int pcW = fm.stringWidth(CARD.replace(' ','_'));  // Bug in stringWidth
                final int giveW;  // width of trading Give/Get labels
                {
                    final int gv = fm.stringWidth(GIVE), gt = fm.stringWidth(GET);
                    giveW = ((gv > gt) ? gv : gt) + labelspc + 2;
                }
                // int clearW = fm.stringWidth(CLEAR.replace(' ','_'));
                // int bankW = fm.stringWidth(BANK.replace(' ','_'));
                final int resCardsH = 5 * (lineH + space);   // Clay,Ore,Sheep,Wheat,Wood
                final int tradeH = sqpDim.height + space + (2 * (lineH + space));  // sqPanel + 2 rows of buttons
                final int sectionSpace = (dim.height - (inset + faceW + resCardsH + tradeH + lineH + inset)) / 3;
                final int tradeY = inset + faceW + sectionSpace;  // top of trade area
                final int devCardsY = tradeY + tradeH + sectionSpace;  // top of dev card list

                // Always reposition everything
                startBut.setBounds(inset + faceW + inset, inset + lineH + space, dim.width - (inset + faceW + inset + inset), lineH);

                // Below face, player name, VP count, etc:
                // Section spacer, then:
                // Trade area to left; item counts to right (soldiers,roads,settlements,cities,ships)

                // Trading: Give,Get labels to left of SquaresPanel
                giveLab.setBounds(inset, tradeY, giveW, lineH);
                getLab.setBounds(inset, tradeY + ColorSquareLarger.HEIGHT_L, giveW, lineH);
                sqPanel.setLocation(inset + giveW + space, tradeY);

                // Button rows Below SquaresPanel:
                // Offer button, playerSend checkboxes (3 or 5)
                // Clear, Bank/Port
                // Undo button (below Bank/Port, leaving room on left for resource card counts)

                final int tbW = ((giveW + sqpDim.width) / 2);
                final int tbX = inset;
                int tbY = tradeY + sqpDim.height + space;
                if (offerBut != null)
                {
                    if (game.maxPlayers == 4)
                        offerBut.setBounds(tbX, tbY, tbW, lineH);
                    else  // 6-player: leave room for 5 checkboxes
                        offerBut.setBounds(tbX, tbY, (2 * tbW) + space - (5 * (1 + ColorSquare.WIDTH)), lineH);
                }
                clearOfferBut.setBounds(tbX, tbY + lineH + space, tbW, lineH);
                bankBut.setBounds(tbX + tbW + space, tbY + lineH + space, tbW, lineH);
                bankUndoBut.setBounds(tbX + tbW + space, tbY + 2 * (lineH + space), tbW, lineH);

                if (! playerTradingDisabled)
                {
                    // Checkboxes to select players to send trade offers
                    if (game.maxPlayers == 4)
                    {
                        playerSend[0].setBounds(tbX + tbW + space, tbY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                        playerSend[1].setBounds(tbX + tbW + space + ((tbW - ColorSquare.WIDTH) / 2), tbY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                        playerSend[2].setBounds((tbX + tbW + space + tbW) - ColorSquare.WIDTH, tbY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                    } else {
                        // 6-player: 5 checkboxes
                        int px = tbX + (2 * (space + tbW)) - ColorSquare.WIDTH - 1;
                        for (int pi = 4; pi >=0; --pi, px -= (ColorSquare.WIDTH + 1))
                            playerSend[pi].setBounds(px, tbY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                    }
                }

                // Calc knightsW minimum width needed from label texts
                final int knightsW;
                {
                    int wmax = 0;
                    JLabel[] labs = new JLabel[]{clothLab, knightsLab, roadLab, settlementLab, cityLab, shipLab};
                    for (JLabel L : labs)
                    {
                        if (L == null)
                            continue;
                        int w = fm.stringWidth(L.getText());
                        if (w > wmax)
                            wmax = w;
                    }
                    wmax += 2;  // +2 because Label text is inset from column 0

                    // make sure not more than half panel width
                    if (wmax > ((dim.width - ColorSquare.WIDTH - 8) / 2))
                        wmax = (dim.width - ColorSquare.WIDTH - 8) / 2;

                    knightsW = wmax;
                }

                // Various item counts, to the right of give/get/offer/trade area
                if (clothSq != null)
                {
                    clothLab.setBounds(dim.width - inset - knightsW - ColorSquare.WIDTH - space, tradeY - (lineH + space), knightsW, lineH);
                    clothSq.setBounds(dim.width - inset - ColorSquare.WIDTH, tradeY - (lineH + space), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                }
                knightsLab.setBounds(dim.width - inset - knightsW - ColorSquare.WIDTH - space, tradeY, knightsW, lineH);
                knightsSq.setBounds(dim.width - inset - ColorSquare.WIDTH, tradeY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                roadLab.setBounds(dim.width - inset - knightsW - ColorSquare.WIDTH - space, tradeY + lineH + space, knightsW, lineH);
                roadSq.setBounds(dim.width - inset - ColorSquare.WIDTH, tradeY + lineH + space, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                settlementLab.setBounds(dim.width - inset - knightsW - ColorSquare.WIDTH - space, tradeY + (2 * (lineH + space)), knightsW, lineH);
                settlementSq.setBounds(dim.width - inset - ColorSquare.WIDTH, tradeY + (2 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                cityLab.setBounds(dim.width - inset - knightsW - ColorSquare.WIDTH - space, tradeY + (3 * (lineH + space)), knightsW, lineH);
                citySq.setBounds(dim.width - inset - ColorSquare.WIDTH, tradeY + (3 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                if (shipSq != null)
                {
                    shipLab.setBounds(dim.width - inset - knightsW - ColorSquare.WIDTH - space, tradeY + (4 * (lineH + space)), knightsW, lineH);
                    shipSq.setBounds(dim.width - inset - ColorSquare.WIDTH, tradeY + (4 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                }

                // Player's resource counts
                //   center the group vertical between bottom of Clear button, top of Quit button
                tbY = (((dim.height - lineH - inset) + (tbY + (2 * lineH) + space)) / 2)
                  - (3 * (lineH + space));
                clayLab.setBounds(inset, tbY, sheepW, lineH);
                claySq.setBounds(inset + sheepW + space, tbY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                tbY += (lineH + space);
                oreLab.setBounds(inset, tbY, sheepW, lineH);
                oreSq.setBounds(inset + sheepW + space, tbY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                tbY += (lineH + space);
                sheepLab.setBounds(inset, tbY, sheepW, lineH);
                sheepSq.setBounds(inset + sheepW + space, tbY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                tbY += (lineH + space);
                wheatLab.setBounds(inset, tbY, sheepW, lineH);
                wheatSq.setBounds(inset + sheepW + space, tbY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                tbY += (lineH + space);
                woodLab.setBounds(inset, tbY, sheepW, lineH);
                woodSq.setBounds(inset + sheepW + space, tbY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                // Line between woodSq, resourceSq
                tbY += (lineH + space);
                resourceSqDivLine.setBounds(inset + space, tbY - 1, sheepW + ColorSquare.WIDTH, 1);
                // Total Resources
                ++tbY;
                resourceLab.setBounds(inset, tbY, sheepW, lineH);
                resourceSq.setBounds(inset + sheepW + space, tbY, ColorSquare.WIDTH, ColorSquare.HEIGHT);

                // To the right of resource counts:
                // Development Card list, Play button below
                final int clW = dim.width - (inset + sheepW + space + ColorSquare.WIDTH + (4 * space) + inset);
                final int clX = inset + sheepW + space + ColorSquare.WIDTH + (4 * space);
                inventoryScroll.setBounds(clX, devCardsY, clW, (4 * (lineH + space)) - 2);
                playCardBut.setBounds(((clW - pcW) / 2) + clX, devCardsY + (4 * (lineH + space)), pcW, lineH);

                // Bottom of panel:
                // 1 button row: Quit to left; Roll, Restart to right
                final int bbW = 50;
                tbY = dim.height - lineH - inset;
                // Label lines up over Roll button
                rollPromptCountdownLab.setBounds(dim.width - (bbW + space + bbW + inset), tbY - lineH, dim.width - 2*inset, lineH);
                // Bottom row of buttons
                quitBut.setBounds(inset, tbY, bbW, lineH);
                rollBut.setBounds(dim.width - (bbW + space + bbW + inset), tbY, bbW, lineH);
                doneBut.setBounds(dim.width - inset - bbW, tbY, bbW, lineH);

                offerHidesControls = false;  // since it won't ever be showing
                offerCounterHidesFace = false;
            }
            else
            {
                /* This is another player's hand */

                // Top has name, VP count, largest army, longest road; SVP under VP count if applicable
                // Trade offers appear in center when a trade is active
                // Bottom has columns of item counts on left, right, having 3 or 4 rows:
                //   Cloth (if that scenario), Soldiers, Res, Dev Cards to left;
                //   Ships (if sea board), Roads, Settlements, Cities to right
                //   Robot lock button (during play) in bottom center

                int balloonH = dim.height - (inset + (4 * (lineH + space)) + inset);  // offer-message panel
                offer.setAvailableSpace(dim.width - 2 * inset, balloonH);  // recalc offer.getPreferredSize()

                final boolean wasHidesControls = offerHidesControls;  // if changes here, will call hideTradeMsgShowOthers

                boolean hasTakeoverBut = false, hasSittingRobotLockBut = false;
                if (player.isRobot())
                {
                    // "Take Over" button if client is observer (not seated at this game),
                    // otherwise Lock/Unlock button during play (sittingRobotLockBut):
                    // Just above the lower-left, lower-right columns of item counts

                    int lowerY = dim.height - ((4 * (lineH + space)) + inset);
                    int yb = lowerY - 5;
                    if (game.hasSeaBoard)
                        yb -= (lineH + space);

                    if (game.getPlayer(client.getNickname()) == null)
                    {
                        takeOverBut.setBounds(9, yb, dim.width - 18, lineH + space);
                        hasTakeoverBut = true;
                    }
                    else if (sittingRobotLockBut.isVisible())
                    {
                        sittingRobotLockBut.setBounds(9, yb, dim.width - 18, lineH + space);
                        hasSittingRobotLockBut = true;
                    }
                }

                // Are we tall enough for room, after the offer, for other controls?
                // If not, they will be hid when offer is visible.
                final Dimension offerPrefSize = offer.getPreferredSize();
                int offerMinHeight = offer.isCounterOfferMode()
                    ? (TradeOfferPanel.OFFER_HEIGHT + TradeOfferPanel.OFFER_COUNTER_HEIGHT
                       - TradeOfferPanel.OFFER_BUTTONS_ADDED_HEIGHT)
                    : TradeOfferPanel.OFFER_HEIGHT;
                if (offer.offerPanel.wantsRejectCountdown(true))
                    offerMinHeight += TradeOfferPanel.LABEL_LINE_HEIGHT;
                final int numBottomLines = (hasTakeoverBut || hasSittingRobotLockBut) ? 5 : 4;
                offerHidesControls = offerHidingControls
                    || ((dim.height - (inset + faceW + space) - (numBottomLines * (lineH + space))) < offerMinHeight);
                if (offerHidesControls)
                {
                    // This flag is set here based on newly calculated layout,
                    // for use later when changing offerCounterHidingFace
                    offerCounterHidesFace =
                        (dim.height - offerMinHeight) < faceW;

                    final int offerW = Math.min(dim.width - (2 * inset), offerPrefSize.width);

                    // This is a dynamic flag, set by hideTradeMsgShowOthers
                    // when the user clicks button to show/hide the counter-offer.
                    // If true now, hideTradeMsgShowOthers has already hid faceImg,
                    // pname, vpLab and vpSq, to make room for it.
                    if (offerCounterHidingFace)
                    {
                        offer.setBounds
                            (inset, inset, offerW, Math.min(dim.height - (2 * inset), offerPrefSize.height));
                    } else {
                        offer.setBounds
                            (inset, inset + faceW + space, offerW,
                             Math.min(dim.height - (inset + faceW + 2 * space), offerPrefSize.height));
                    }
                    offer.setCounterHidesBalloonPoint(offerCounterHidingFace);
                } else {
                    offer.setBounds(inset, inset + faceW + space, offerPrefSize.width, offerPrefSize.height);
                    offerCounterHidesFace = false;
                }
                offer.validate();

                // Calc stlmtsW, dcardsW minimum widths needed from label texts
                final int stlmtsW;
                {
                    int wmax = 0, w;
                    JLabel[] labs = new JLabel[]{shipLab, roadLab, settlementLab, cityLab};
                    for (JLabel L : labs)
                    {
                        if (L == null)
                            continue;
                        w = fm.stringWidth(L.getText());
                        if (w > wmax)
                            wmax = w;
                    }

                    wmax += 10;  // for inset before and spacing after label

                    // make sure not more than half panel width
                    if (wmax > ((dim.width - ColorSquare.WIDTH - 8) / 2))
                        wmax = (dim.width - ColorSquare.WIDTH - 8) / 2;

                    stlmtsW = wmax;
                }
                final int dcardsW;
                {
                    // developmentLab

                    int wmax = 0, w;
                    JLabel[] labs = new JLabel[]{clothLab, knightsLab};
                    for (JLabel L : labs)
                    {
                        if (L == null)
                            continue;
                        w = fm.stringWidth(L.getText());
                        if (w > wmax)
                            wmax = w;
                    }
                    w = fm.stringWidth(resourceLab.getText());  // is Label, not JLabel like the labs above
                    if (w > wmax)
                        wmax = w;
                    w = fm.stringWidth(developmentLab.getText());
                    if (w > wmax)
                        wmax = w;

                    wmax += 2;  // for insets within label

                    // make sure not more than half panel width
                    if (wmax > ((dim.width - ColorSquare.WIDTH - 8) / 2))
                        wmax = (dim.width - ColorSquare.WIDTH - 8) / 2;

                    dcardsW = wmax;
                }

                final int lowerY = dim.height - ((4 * (lineH + space)) + inset);

                // Lower-left: Column of item counts:
                // Cloth, Soldiers, Resources, Dev Cards
                resourceLab.setBounds(inset, lowerY + (2 * (lineH + space)), dcardsW, lineH);
                resourceSq.setBounds(inset + dcardsW + space, lowerY + (2 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                developmentLab.setBounds(inset, lowerY + (3 * (lineH + space)), dcardsW, lineH);
                developmentSq.setBounds(inset + dcardsW + space, lowerY + (3 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                knightsLab.setBounds(inset, lowerY + (lineH + space), dcardsW, lineH);
                knightsSq.setBounds(inset + dcardsW + space, lowerY + (lineH + space), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                if (clothSq != null)
                {
                    clothLab.setBounds(inset, lowerY, dcardsW, lineH);
                    clothSq.setBounds(inset + dcardsW + space, lowerY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                }

                // Lower-right: Column of piece counts:
                // Ships, Roads, Settlements, Cities
                if (shipSq != null)
                {
                    shipLab.setBounds(dim.width - inset - stlmtsW - ColorSquare.WIDTH - space, lowerY, stlmtsW, lineH);
                    shipSq.setBounds(dim.width - inset - ColorSquare.WIDTH, lowerY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                }
                roadLab.setBounds(dim.width - inset - stlmtsW - ColorSquare.WIDTH - space, lowerY + (lineH + space), stlmtsW, lineH);
                roadSq.setBounds(dim.width - inset - ColorSquare.WIDTH, lowerY + (lineH + space), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                settlementLab.setBounds(dim.width - inset - stlmtsW - ColorSquare.WIDTH - space, lowerY + (2 * (lineH + space)), stlmtsW, lineH);
                settlementSq.setBounds(dim.width - inset - ColorSquare.WIDTH, lowerY + (2 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                cityLab.setBounds(dim.width - inset - stlmtsW - ColorSquare.WIDTH - space, lowerY + (3 * (lineH + space)), stlmtsW, lineH);
                citySq.setBounds(dim.width - inset - ColorSquare.WIDTH, lowerY + (3 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);

                if (wasHidesControls != offerHidesControls)
                    hideTradeMsgShowOthers(false);
            }
        }
    }


    /**
     * Used for countdown before auto-roll of the current player.
     * Updates on-screen countdown, fires auto-roll at 0.
     *
     * @see SOCHandPanel#AUTOROLL_TIME
     * @see SOCHandPanel#autoRollSetupTimer()
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.00
     */
    protected class HandPanelAutoRollTask extends java.util.TimerTask
    {
        int timeRemain;  // seconds displayed, seconds at start of "run" tick

        protected HandPanelAutoRollTask()
        {
            timeRemain = AUTOROLL_TIME;
        }

        @Override
        public void run()
        {
            // for debugging
            if (Thread.currentThread().getName().startsWith("Thread-"))
            {
                try {
                    Thread.currentThread().setName("timertask-autoroll");
                }
                catch (Throwable th) {}
            }

            // autoroll function
            try
            {
                if (timeRemain > 0)
                {
                    setRollPrompt(MessageFormat.format(AUTOROLL_COUNTDOWN, Integer.valueOf(timeRemain)), false);
                } else {
                    clickRollButton();  // Clear prompt, click Roll
                    cancel();  // End of countdown for this timer
                }
            }
            catch (Throwable thr)
            {
                playerInterface.chatPrintStackTrace(thr);
            }
            finally
            {
                --timeRemain;  // for next tick
            }
        }

    }  // inner class HandPanelAutoRollTask

    /**
     * Menu item for right-click on resource square to trade with bank/port.
     *
     * @see soc.client.SOCHandPanel.ResourceTradePopupMenu
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.00
     */
    protected static class ResourceTradeMenuItem extends MenuItem
    {
        private static final Integer INT_1 = Integer.valueOf(1);  // resource quantity for string formats

        private final SOCGame game;  // needed only for SOCStringManager.getSpecial
        private int tradeFrom, tradeTo;
        private int tradeNum;
        private boolean shortTxt;

        /**
         * Create a bank/port trade MenuItem, with text such as "Trade 2 brick for 1 wheat".
         *
         * @param game     Game reference, needed for {@link SOCStringManager#getSpecial(SOCGame, String, Object...)}
         * @param numFrom  Number of resources to trade for 1 resource
         * @param typeFrom Source resource type, as in {@link SOCResourceConstants}.
         * @param typeTo   Target resource type, as in {@link SOCResourceConstants}.
         *                 If typeFrom == typeTo, menuitem will be disabled.
         * @param shortText If true, short ("For 1 wheat") vs full "Trade 2 brick for 1 wheat"
         */
        public ResourceTradeMenuItem(final SOCGame game, int numFrom, int typeFrom, int typeTo, boolean shortText)
        {
            super( (shortText
                    ? strings.getSpecial(game, "board.trade.for.1.rsrc", INT_1, typeTo)     // "For 1 sheep"
                    : strings.getSpecial(game, "board.trade.trade.x.rsrcs.for.1.rsrc", numFrom, typeFrom, INT_1, typeTo)
                    // "Trade 3 wheat for 1 sheep"
                    ) );
            this.game = game;
            tradeNum = numFrom;
            tradeFrom = typeFrom;
            tradeTo = typeTo;
            shortTxt = shortText;
            if (tradeFrom == tradeTo)
                setEnabled(false);
        }

        /**
         * Update menu item text to new cost of trade.
         * @param numFrom Trade this many resources;
         *                if the number is unchanged, the text is not updated.
         */
        public void setCost(int numFrom)
        {
            if (tradeNum == numFrom)
                return;
            tradeNum = numFrom;
            if (shortTxt)
                setLabel(strings.getSpecial(game, "board.trade.for.1.rsrc", INT_1, tradeTo));     // "For 1 sheep"
            else
                setLabel(strings.getSpecial
                    (game, "board.trade.trade.x.rsrcs.for.1.rsrc", numFrom, tradeFrom, INT_1, tradeTo));
                    // "Trade 3 wheat for 1 sheep"
        }

        /**
         * Enable or disable this menu item.
         * If from-to resources are same, always disabled.
         */
        @Override
        public void setEnabled(boolean enable)
        {
            if (tradeFrom == tradeTo)
                enable = false;
            super.setEnabled(enable);
        }

        /**
         * @return the resource type to trade from, shown in this menu item text
         */
        public int getTradeFrom()
        {
            return tradeFrom;
        }

        /**
         * @return the resource type to trade to, shown in this menu item text
         */
        public int getTradeTo()
        {
            return tradeTo;
        }

        /**
         * Create a bank-trade-request, send to the server.
         */
        public void createBankTradeRequest(SOCHandPanel hp)
        {
            // Code like actionPerformed for BANK button
            SOCGame game = hp.getGame();
            if (game.getGameState() != SOCGame.PLAY1)
            {
                hp.getPlayerInterface().print("* " + strings.get("hpan.trade.msg.notnow") + "\n");
                    // "You cannot trade at this time."
                return;
            }

            int[] give = new int[5];
            int[] get = new int[5];
            give[tradeFrom - 1] = tradeNum;
            get[tradeTo - 1] = 1;
            hp.createSendBankTradeRequest(game, give, get, false);
        }

    }  // ResourceTradeMenuItem

    /**
     * Menu for right-click on resource square to trade with bank/port.
     *
     * @see SOCHandPanel.ResourceTradeTypeMenu
     * @see SOCBoardPanel.ResourceTradeAllMenu
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.00
     */
    /* package-access */ static abstract class ResourceTradePopupMenu extends PopupMenu
    {
        protected SOCHandPanel hpan;

        protected ResourceTradePopupMenu(SOCHandPanel hp, String title)
        {
            super(title);
            hpan = hp;
        }

        /**
         * Show menu at this position. Before showing, enable or
         * disable based on gamestate and player's resources.
         *
         * @param x   Mouse x-position relative to colorsquare
         * @param y   Mouse y-position relative to colorsquare
         *
         * @see #setEnabledIfCanTrade(boolean)
         */
        public abstract void show(int x, int y);

        /**
         * Enable or disable based on gamestate and player's resources.
         *
         * @param itemsOnly If true, enable/disable items, instead of the menu itself.
         */
        public abstract void setEnabledIfCanTrade(boolean itemsOnly);

        /** Cleanup, for removing this menu. */
        public abstract void destroy();

    }  /* static nested class ResourceTradePopupMenu */

    /**
     * Menu for right-click on resource square to trade one resource type with bank/port.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.00
     */
    /* package-access */ static class ResourceTradeTypeMenu extends ResourceTradePopupMenu
        implements java.awt.event.MouseListener, java.awt.event.ActionListener
    {
        private ColorSquare resSq;
        private int resTypeFrom;
        private int costFrom;
        boolean isForThree1;
        private ResourceTradeMenuItem[] tradeForItems;

        /** Menu attached to a resource colorsquare in the client player's handpanel */
        public ResourceTradeTypeMenu(SOCHandPanel hp, int typeFrom, ColorSquare sq, int numFrom)
        {
          super(hp, strings.get("board.trade.bank.port.trade"));  // "Bank/Port Trade"
          init(typeFrom, hp.game, sq, numFrom, false);
        }

        /**
         * One-time-use menu for board popup menu.
         *
         * @param hp  Handpanel with player's information (including trade costs)
         * @param typeFrom Resource type from which to trade
         * @param forThree1 Is part of a 3-for-1 port trade menu, with all resource types
         *
         * @throws IllegalStateException If client not current player
         */
        public ResourceTradeTypeMenu(SOCHandPanel hp, int typeFrom, boolean forThree1)
            throws IllegalStateException
        {
            super(hp, strings.get("board.trade.trade.port"));  // "Trade Port"
            if (! hp.getPlayerInterface().clientIsCurrentPlayer())
                throw new IllegalStateException("Not current player");
            init(typeFrom, hp.game, null, hp.resourceTradeCost[typeFrom], forThree1);
        }

        /** Common to both constructors */
        private void init(int typeFrom, final SOCGame ga, ColorSquare sq, int numFrom, boolean forThree1)
        {
          resSq = sq;
          resTypeFrom = typeFrom;
          costFrom = numFrom;
          isForThree1 = forThree1;
          if (forThree1)
              setLabel(strings.getSpecial(ga, "board.trade.trade.x.rsrcs", costFrom, typeFrom));  // "Trade 3 wheat"

          if (resSq != null)
          {
              resSq.add(this);
              resSq.addMouseListener(this);
          }
          tradeForItems = new ResourceTradeMenuItem[5];
          for (int i = 0; i < 5; ++i)
          {
              tradeForItems[i] = new ResourceTradeMenuItem(ga, numFrom, typeFrom, i+1, forThree1);
              add(tradeForItems[i]);
              tradeForItems[i].addActionListener(this);
          }
        }

        /**
         * Show menu at this position. Before showing, enable or
         * disable based on gamestate and player's resources.
         *
         * @param x   Mouse x-position relative to colorsquare
         * @param y   Mouse y-position relative to colorsquare
         */
        @Override
        public void show(int x, int y)
        {
            setEnabledIfCanTrade(true);  // enable/disable each item
            super.show(resSq, x, y);
        }

        /**
         * Enable or disable based on gamestate and player's resources.
         *
         * @param itemsOnly If true, enable/disable items, instead of the menu itself.
         */
        @Override
        public void setEnabledIfCanTrade(boolean itemsOnly)
        {
            final SOCPlayer p = hpan.player;
            boolean canTrade = (hpan.getGame().getGameState() == SOCGame.PLAY1)
                && (hpan.getGame().getCurrentPlayerNumber() == hpan.playerNumber)
                && (costFrom <= p.getResources().getAmount(resTypeFrom));
            if (itemsOnly)
            {
                for (int i = 0; i < 5; ++i)
                    tradeForItems[i].setEnabled(canTrade);
            }
            else
            {
                setEnabled(canTrade);
            }
        }

        /** Update cost of trade; update menu item text. */
        public void updateCost(int newCost)
        {
            if (costFrom == newCost)
                return;
            costFrom = newCost;
            for (int i = 0; i < 5; ++i)
                tradeForItems[i].setCost(newCost);
        }

        /**
         * @return the resource type number from which this menu trades
         */
        public int getResourceType()
        {
            return resTypeFrom;
        }

        /**
         * @return the cost to trade this resource (3-for-1 returns 3, etc)
         */
        public int getResourceCost()
        {
            return costFrom;
        }

        /** Handling the menu item **/
        public void actionPerformed(ActionEvent e)
        {
            try
            {
                Object src = e.getSource();
                ResourceTradeMenuItem mi = null;
                for (int i = 0; i < 5; ++i)
                {
                    if (src == tradeForItems[i])
                    {
                        mi = tradeForItems[i];
                        break;
                    }
                }
                if (mi == null)
                    return;

                mi.createBankTradeRequest(hpan);

            } catch (Throwable th) {
                hpan.getPlayerInterface().chatPrintStackTrace(th);
            }
        }

        /**
         * Handle popup-click on resource colorsquare.
         * mousePressed has xwindows/OS-X popup trigger.
         */
        public void mousePressed(MouseEvent evt)
        {
            mouseClicked(evt);  // same desired code: react to isPopupTrigger
        }

        /**
         * Handle popup-click on resource colorsquare.
         */
        public void mouseClicked(MouseEvent evt)
        {
            try {
                if ((resSq == evt.getSource()) && evt.isPopupTrigger())
                {
                    evt.consume();
                    show(evt.getX(), evt.getY());
                }
            } catch (Throwable th) {
                hpan.getPlayerInterface().chatPrintStackTrace(th);
            }
        }

        /**
         * Handle popup-click on resource colorsquare.
         * mouseReleased has win32 popup trigger.
         */
        public void mouseReleased(MouseEvent evt)
        {
            mouseClicked(evt);  // same desired code: react to isPopupTrigger
        }

        /** Stub required for MouseListener */
        public void mouseEntered(MouseEvent evt) {}

        /** Stub required for MouseListener */
        public void mouseExited(MouseEvent evt) {}

        /** Cleanup, for removing this menu. */
        @Override
        public void destroy()
        {
            for (int i = 0; i < 5; ++i)
            {
                if (tradeForItems[i] != null)
                {
                    ResourceTradeMenuItem mi = tradeForItems[i];
                    tradeForItems[i] = null;
                    mi.removeActionListener(this);
                }
            }
            hpan = null;
            if (resSq != null)
            {
                resSq.remove(this);
                resSq.removeMouseListener(this);
                resSq = null;
            }
            removeAll();
        }

    }  /* static nested class ResourceTradeTypeMenu */

}  // class SOCHandPanel
