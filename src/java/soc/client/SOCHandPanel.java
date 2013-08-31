/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2013 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Label;
import java.awt.List;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import java.util.ArrayList;
import java.util.Timer;  // For auto-roll
import java.util.TimerTask;


/**
 * This panel displays a player's information.
 * If the player is us, then more information is
 * displayed than in another player's hand panel.
 *<P>
 * To update most of the values shown in the handpanel,
 * call {@link #updateValue(int)} after receiving
 * a {@link SOCPlayerElement} message or after something
 * else changes the game state.
 *<P>
 * Custom layout: see {@link #doLayout()}.
 * To set this panel's position or size, please use {@link #setBounds(int, int, int, int)},
 * because it is overridden to also update {@link #getBlankStandIn()}.
 */
public class SOCHandPanel extends Panel
    implements ActionListener, MouseListener
{
    /** Minimum desired width, in pixels */
    public static final int WIDTH_MIN = 218;

    /** Items to update via {@link #updateValue(int)};
     * for items not appearing in {@link SOCPlayerElement}.
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
    /** Before game starts, use {@link #pname} to show if a seat is no-robots-allowed. */
    protected static final String SITLOCKED = /*I*/"Locked: No robot"/*18N*/;
    protected static final String SIT = /*I*/"Sit Here"/*18N*/;
    protected static final String START = /*I*/"Start Game"/*18N*/;
    protected static final String ROBOT = /*I*/"Robot"/*18N*/;
    protected static final String TAKEOVER = /*I*/"Take Over"/*18N*/;
    protected static final String LOCKSEAT = /*I*/"Lock"/*18N*/;
    protected static final String UNLOCKSEAT = /*I*/"Unlock"/*18N*/;
    private static final String LOCKSEATTIP = /*I*/"Lock to prevent a robot from sitting here."/*18N*/;
    private static final String UNLOCKSEATTIP = /*I*/"Unlock to have a robot sit here when the game starts."/*18N*/;
    protected static final String ROLL = /*I*/"Roll"/*18N*/;
    protected static final String QUIT = /*I*/"Quit"/*18N*/;
    protected static final String DONE = /*I*/"Done"/*18N*/;
    /** Text of Done button at end of game becomes Restart button. If you set this, set {@link #doneButIsRestart}. */
    protected static final String DONE_RESTART = /*I*/"Restart"/*18N*/;
    protected static final String CLEAR = /*I*/"Clear"/*18N*/;
    protected static final String SEND = /*I*/"Offer"/*18N*/;
    protected static final String BANK = /*I*/"Bank/Port"/*18N*/;
    private static final String BANK_UNDO = /*I*/"Undo Trade"/*18N*/;
    protected static final String CARD = /*I*/"  Play Card  "/*18N*/;
    protected static final String GIVE = /*I*/"I Give:"/*18N*/;  // No trailing space (room for wider colorsquares)
    protected static final String GET = /*I*/"I Get:"/*18N*/;
    private static final String RESOURCES = /*I*/"Resources: "/*18N*/;  // for other players (! playerIsClient)
    private static final String RESOURCES_TOTAL = /*I*/"Total: "/*18N*/;  // for playerIsClient
    protected static final String AUTOROLL_COUNTDOWN = /*I*/"Auto-Roll in: "/*18N*/;
    protected static final String ROLL_OR_PLAY_CARD = /*I*/"Roll or Play Card"/*18N*/;
    protected static final String OFFERBUTTIP_ENA = /*I*/"Send trade offer to other players"/*18N*/;
    protected static final String OFFERBUTTIP_DIS = /*I*/"To offer a trade, first click resources"/*18N*/;
    private static final String ROBOTLOCKBUT_U = /*I*/"Unlocked"/*18N*/;
    private static final String ROBOTLOCKBUT_L = /*I*/"Locked"/*18N*/;
    private static final String ROBOTLOCKBUT_M = /*I*/"Marked"/*18N*/;  // C is for lockstate Clear on Reset
    private static final String ROBOTLOCKBUTTIP_L
        = /*I*/"Click to mark or unlock; is locked to prevent a human from taking over this robot."/*18N*/;
    private static final String ROBOTLOCKBUTTIP_U
        = /*I*/"Click to lock or mark; is unlocked, a human can take over this robot."/*18N*/;
    private static final String ROBOTLOCKBUTTIP_M
        = /*I*/"Click to unmark; is marked to remove this robot if the game is reset."/*18N*/;
    private static final String ROBOTLOCKBUTTIP_L_OLD
        = /*I*/"Click to unlock; is locked to prevent a human from taking over this robot."/*18N*/;
    private static final String ROBOTLOCKBUTTIP_U_OLD
        = /*I*/"Click to lock; is unlocked, a human can take over this robot."/*18N*/;

    /**
     * Show that a non-client player is discarding resources after 7 is rolled.
     * Call {@link #setDiscardOrPickMsg(boolean)} to show.
     * Same methods are used by discard and by {@link #TRADEMSG_PICKING}.
     * @since 1.1.00
     */
    private static final String TRADEMSG_DISCARD = /*I*/"Discarding..."/*18N*/;

    /**
     * Show that a non-client player is picking resources for the gold hex.
     * Uses same variables and methods as {@link #TRADEMSG_DISCARD}:
     * {@link #offerIsDiscardOrPickMessage}, {@link #setDiscardOrPickMsg(boolean)}, etc.
     * @since 2.0.00
     */
    private static final String TRADEMSG_PICKING = /*I*/"Picking\nResources..."/*18N*/;

    /** Panel text color, and player name color when not current player */
    protected static final Color COLOR_FOREGROUND = Color.BLACK;
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
    protected Button sitBut;

    /**
     * Hint for "Lock/Unlock" button before game starts ({@link #sitBut};
     * non-null only if {@link #sitButIsLock}.
     * @see #LOCKSEATTIP
     * @see #UNLOCKSEATTIP
     * @since 1.1.12
     */
    private AWTToolTip sitButTip;

    protected Button robotBut;
    protected Button startBut;
    protected Button takeOverBut;

    /** Seat lock/unlock shown in robot handpanels during game play,
     *  to prevent/allow humans to join and take over a robot's seat.
     *  Used during different game states than {@link #sitBut}.
     *<P>
     *  Labels are {@link #ROBOTLOCKBUT_U}, {@link #ROBOTLOCKBUT_L}, {@link #ROBOTLOCKBUT_M}.
     *  Tooltip is {@link #robotLockButTip}.
     *  Click method is {@link #clickRobotSeatLockButton(soc.game.SOCGame.SeatLockState)}.
     *  @see #sitBut
     */
    protected Button sittingRobotLockBut;

    /** When true, the game is still forming, player has chosen a seat;
     *  "Sit Here" button is labeled as "Lock".  Humans can use this to
     *  lock robots out of that seat, so as to start a game with fewer
     *  players and some vacant seats.
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

    protected Label pname;
    protected Label vpLab;
    protected ColorSquare vpSq;

    /** Label for Special Victory Points.  Hidden if {@link SOCPlayer#getSpecialVP()} is 0.
     *  Null unless {@link SOCGame#hasSeaBoard}.
     *  @since 2.0.00
     */
    private Label svpLab;

    /** Special Victory Points, if > 0.  Hidden if 0.
     *  Null unless {@link SOCGame#hasSeaBoard}.
     *  @since 2.0.00
     */
    private ColorSquare svpSq;

    protected Label larmyLab;
    protected Label lroadLab;
    protected ColorSquare claySq;
    protected ColorSquare oreSq;
    protected ColorSquare sheepSq;
    protected ColorSquare wheatSq;
    protected ColorSquare woodSq;
    protected ColorSquare resourceSqDivLine;
    protected Label clayLab;
    protected Label oreLab;
    protected Label sheepLab;
    protected Label wheatLab;
    protected Label woodLab;

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
    protected Label settlementLab;
    protected Label cityLab;
    protected Label roadLab;
    protected Label shipLab;
    /** Resource card count */
    protected ColorSquare resourceSq;
    protected Label resourceLab;
    /** Development card count */
    protected ColorSquare developmentSq;
    protected Label developmentLab;
    /** Soldier/Knight count */
    protected ColorSquare knightsSq;
    protected Label knightsLab;
    /** Player's development card names, from {@link #cardListItems}; updated frequently by {@link #updateDevCards()} */
    protected List cardList;
    /** Player's development cards for {@link #cardList}; updated frequently by {@link #updateDevCards()} */
    private ArrayList<SOCDevCard> cardListItems;
        // eventually, cardListItems should be refactored into SOCPlayer.
    protected Button playCardBut;
    /** Trade offer resource squares; visible only for client's own player */
    protected SquaresPanel sqPanel;
    /** Cloth count, for scenario _SC_CLVI; null otherwise. @since 2.0.00 */
    protected ColorSquare clothSq;
    protected Label clothLab;

    // Trading interface

    /**
     * Game option NT: If true, only bank trading is allowed,
     * trading between players is disabled.
     * @since 1.1.07
     */
    protected boolean playerTradingDisabled;

    protected Label giveLab;
    protected Label getLab;
    /** "Offer" button for player trading: send offer to server */
    protected Button offerBut;

    /**
     * Hint for "Offer" button; non-null only if interactive
     *   and if playerTradingDisabled == false.
     * @see #OFFERBUTTIP_DIS
     * @see #OFFERBUTTIP_ENA
     * @see #interactive
     */
    protected AWTToolTip offerButTip;

    /**
     * Hint for "Lock/Unlock" button ({@link #sittingRobotLockBut};
     * non-null only if a robot is sitting there.
     * @see #ROBOTLOCKBUTTIP_L
     * @see #ROBOTLOCKBUTTIP_U
     * @see #ROBOTLOCKBUTTIP_M
     * @since 1.1.12
     */
    protected AWTToolTip robotLockButTip;

    /** Clear the current trade offer at client and server */
    protected Button clearOfferBut;

    /** Trade resources with the bank or port */
    protected Button bankBut;

    /**
     * Bank or port trade's give/get resource info;
     * used for Undo.
     * @since 1.1.13
     */
    private SOCResourceSet bankGive, bankGet;

    /**
     * Undo previous trade with the bank or port
     * @since 1.1.13
     */
    protected Button bankUndoBut;

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
    protected Label rollPromptCountdownLab;
    protected boolean rollPromptInUse;
    protected TimerTask autoRollTimerTask;  // Created every turn when countdown needed
    protected Button rollBut;

    /** "Done" with turn during play; also "Restart" for board reset at end of game */
    protected Button doneBut;

    /** True when {@link #doneBut}'s label is Restart ({@link #DONE_RESTART}) */
    protected boolean doneButIsRestart;

    protected Button quitBut;
    protected SOCPlayerInterface playerInterface;
    protected SOCPlayerClient client;
    protected SOCGame game;
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

    /** Do we have any seated player? Set by {@link #addPlayer(String)}, cleared by {@link #removePlayer()}. */
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
     * @since 1.1.08
     */
    private boolean offerHidesControls, offerCounterHidesFace;
    
    /**
     * When handpanel isn't tall enough, are we currently in the situation described
     * at {@link #offerHidesControls} or {@link #offerCounterHidesFace}?
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
    protected boolean interactive;

    /**
     * make a new hand panel
     *
     * @param pi  the interface that this panel is a part of
     * @param pl  the player associated with this panel
     * @param in  the interactive flag setting
     */
    public SOCHandPanel(SOCPlayerInterface pi, SOCPlayer pl, boolean in)
    {
        super(null);
        creation(pi, pl, in);
    }

    /**
     * make a new hand panel
     *
     * @param pi  the interface that this panel is a part of
     * @param pl  the player associated with this panel
     */
    public SOCHandPanel(SOCPlayerInterface pi, SOCPlayer pl)
    {
        this(pi, pl, true);
    }

    /**
     * Stuff to do when a SOCHandPanel is created.
     *   Calls {@link #removePlayer()} as part of creation.
     *
     * @param pi   player interface
     * @param pl   the player data, cannot be null
     * @param in   the interactive flag setting
     */
    protected void creation(SOCPlayerInterface pi, SOCPlayer pl, boolean in)
    {
        playerInterface = pi;
        client = pi.getClient();
        game = pi.getGame();
        player = pl;
        playerNumber = player.getPlayerNumber();
        playerIsCurrent = false;
        playerIsClient = false;  // confirmed by call to removePlayer() at end of method.
        interactive = in;

        // Note no AWT layout is used - custom layout, see doLayout().

        final Color pcolor = playerInterface.getPlayerColor(playerNumber);
        setBackground(pcolor);
        setForeground(COLOR_FOREGROUND);
        setFont(new Font("SansSerif", Font.PLAIN, 10));

        blankStandIn = new ColorSquare(pcolor, /*I*/"One moment..."/*18N*/);
        blankStandIn.setVisible(false);
        // playerinterface.initInterfaceElements will add blankStandIn to its layout, and set its size/position.

        faceImg = new SOCFaceButton(playerInterface, playerNumber);
        add(faceImg);

        pname = new Label();
        pname.setFont(new Font("Serif", Font.PLAIN, 14));
        add(pname);
        pnameActiveBG = null;  // Will be calculated at first turn

        startBut = new Button(START);
        startBut.addActionListener(this);
        // this button always enabled
        add(startBut);

        vpLab = new Label(/*I*/"Points: "/*18N*/);
        add(vpLab);
        vpSq = new ColorSquare(ColorSquare.GREY, 0);
        vpSq.setTooltipText(/*I*/"Total victory points for this opponent"/*18N*/);
        if (game.vp_winner <= 12)
        {
            vpSq.setTooltipHighWarningLevel(/*I*/"Close to winning"/*18N*/, game.vp_winner - 2);  // (win checked in SOCGame.checkForWinner)
        } else {
            vpSq.setTooltipHighWarningLevel(/*I*/"Close to winning"/*18N*/, game.vp_winner - 3);
        }
        add(vpSq);

        if (game.hasSeaBoard)
        {
            svpLab = new Label(/*I*/"SVP: "/*18N*/);
            svpLab.setVisible(false);
            add(svpLab);
            new AWTToolTip(/*I*/"Special Victory Points for this player"/*18N*/, svpLab);
            svpLab.addMouseListener(this);
            svpSq = new ColorSquare(ColorSquare.GREY, 0);
            svpSq.setVisible(false);
            svpSq.setTooltipText(/*I*/"Special Victory Points, click for details"/*18N*/);
            add(svpSq);
            svpSq.addMouseListener(this);
        } else {
            svpLab = null;
            svpSq = null;
        }

        larmyLab = new Label("", Label.CENTER);
        larmyLab.setForeground(new Color(142, 45, 10));
        larmyLab.setFont(new Font("SansSerif", Font.BOLD, 12));
        add(larmyLab);

        lroadLab = new Label("", Label.CENTER);
        lroadLab.setForeground(new Color(142, 45, 10));
        lroadLab.setFont(new Font("SansSerif", Font.BOLD, 12));
        add(lroadLab);

        clayLab = new Label(/*I*/"Clay:"/*18N*/);
        add(clayLab);
        claySq = new ColorSquare(ColorSquare.CLAY, 0);
        add(claySq);
        claySq.setTooltipText(/*I*/"Right-click to trade clay"/*18N*/);

        oreLab = new Label(/*I*/"Ore:"/*18N*/);
        add(oreLab);
        oreSq = new ColorSquare(ColorSquare.ORE, 0);
        add(oreSq);
        oreSq.setTooltipText(/*I*/"Right-click to trade ore"/*18N*/);

        sheepLab = new Label(/*I*/"Sheep:"/*18N*/);
        add(sheepLab);
        sheepSq = new ColorSquare(ColorSquare.SHEEP, 0);
        add(sheepSq);
        sheepSq.setTooltipText(/*I*/"Right-click to trade sheep"/*18N*/);

        wheatLab = new Label(/*I*/"Wheat:"/*18N*/);
        add(wheatLab);
        wheatSq = new ColorSquare(ColorSquare.WHEAT, 0);
        add(wheatSq);
        wheatSq.setTooltipText(/*I*/"Right-click to trade wheat"/*18N*/);

        woodLab = new Label(/*I*/"Wood:"/*18N*/);
        add(woodLab);
        woodSq = new ColorSquare(ColorSquare.WOOD, 0);
        add(woodSq);
        woodSq.setTooltipText(/*I*/"Right-click to trade wood"/*18N*/);

        resourceSqDivLine = new ColorSquare(Color.BLACK);
        add(resourceSqDivLine);

        //cardLab = new Label("Cards:");
        //add(cardLab);
        cardListItems = new ArrayList<SOCDevCard>();
        cardList = new List(0, false);
        cardList.addActionListener(this);  // double-click support
        add(cardList);

        roadSq = new ColorSquare(ColorSquare.GREY, 0);
        add(roadSq);
        roadSq.setTooltipText(/*I*/"Pieces available to place"/*18N*/);
        roadSq.setTooltipLowWarningLevel(/*I*/"Almost out of roads to place"/*18N*/, 2);
        roadSq.setTooltipZeroText(/*I*/"No more roads available"/*18N*/);
        roadLab = new Label(/*I*/"Roads:"/*18N*/);
        add(roadLab);

        settlementSq = new ColorSquare(ColorSquare.GREY, 0);
        add(settlementSq);
        settlementSq.setTooltipText(/*I*/"Pieces available to place"/*18N*/);
        settlementSq.setTooltipLowWarningLevel(/*I*/"Almost out of settlements to place"/*18N*/, 1);
        settlementSq.setTooltipZeroText(/*I*/"No more settlements available"/*18N*/);
        settlementLab = new Label(/*I*/"Stlmts:"/*18N*/);
        add(settlementLab);

        citySq = new ColorSquare(ColorSquare.GREY, 0);
        add(citySq);
        citySq.setTooltipText(/*I*/"Pieces available to place"/*18N*/);
        citySq.setTooltipLowWarningLevel(/*I*/"Almost out of cities to place"/*18N*/, 1);
        citySq.setTooltipZeroText(/*I*/"No more cities available"/*18N*/);
        cityLab = new Label(/*I*/"Cities:"/*18N*/);
        add(cityLab);

        if (game.hasSeaBoard)
        {
            shipSq = new ColorSquare(ColorSquare.GREY, 0);
            add(shipSq);
            shipSq.setTooltipText(/*I*/"Pieces available to place"/*18N*/);
            shipSq.setTooltipLowWarningLevel(/*I*/"Almost out of ships to place"/*18N*/, 2);
            shipSq.setTooltipZeroText(/*I*/"No more ships available"/*18N*/);
            shipLab = new Label(/*I*/"Ships:"/*18N*/);
            add(shipLab);
        } else {
            // shipSq, shipLab already null
        }

        if (game.isGameOptionSet(SOCGameOption.K_SC_CLVI))
        {
            clothLab = new Label(/*I*/"Cloth:"/*18N*/);  // No trailing space (room for wider colorsquares at left)
            add(clothLab);
            clothSq = new ColorSquare(ColorSquare.GREY, 0);
            add(clothSq);
            clothSq.setTooltipText(/*I*/"Amount of cloth traded from villages"/*18N*/);
        } else {
            // clothSq, clothLab already null
        }

        knightsLab = new Label(/*I*/"Soldiers:"/*18N*/);  // No trailing space (room for wider colorsquares at left)
        add(knightsLab);
        knightsSq = new ColorSquare(ColorSquare.GREY, 0);
        add(knightsSq);
        knightsSq.setTooltipText(/*I*/"Size of this army"/*18N*/);

        resourceLab = new Label(RESOURCES);
        add(resourceLab);
        resourceSq = new ColorSquare(ColorSquare.GREY, 0);
        add(resourceSq);
        resourceSq.setTooltipText(/*I*/"Amount in hand"/*18N*/);
        resourceSq.setTooltipHighWarningLevel(/*I*/"If 7 is rolled, would discard half these resources"/*18N*/, 8);

        developmentLab = new Label(/*I*/"Dev. Cards: "/*18N*/);
        add(developmentLab);
        developmentSq = new ColorSquare(ColorSquare.GREY, 0);
        add(developmentSq);
        developmentSq.setTooltipText(/*I*/"Amount in hand"/*18N*/);

        sittingRobotLockBut = new Button(ROBOTLOCKBUT_U);  // button text will change soon in updateSeatLockButton()
        sittingRobotLockBut.addActionListener(this);
        sittingRobotLockBut.setEnabled(interactive);
        add(sittingRobotLockBut);

        takeOverBut = new Button(TAKEOVER);
        takeOverBut.addActionListener(this);
        takeOverBut.setEnabled(interactive);
        add(takeOverBut);

        sitBut = new Button(SIT);
        sitBut.addActionListener(this);
        sitBut.setEnabled(interactive);
        add(sitBut);
        sitButIsLock = false;

        robotBut = new Button(ROBOT);
        robotBut.addActionListener(this);
        robotBut.setEnabled(interactive);
        add(robotBut);

        playCardBut = new Button(CARD);
        playCardBut.addActionListener(this);
        playCardBut.setEnabled(interactive);
        add(playCardBut);

        playerTradingDisabled = game.isGameOptionSet("NT");

        giveLab = new Label(GIVE);
        add(giveLab);

        getLab = new Label(GET);
        add(getLab);

        sqPanel = new SquaresPanel(interactive, this);
        add(sqPanel);
        sqPanel.setVisible(false); // will become visible only for seated client player

        if (playerTradingDisabled)
        {
            offerBut = null;
            offerButTip = null;
        } else {
            offerBut = new Button(SEND);
            offerBut.addActionListener(this);
            offerBut.setEnabled(interactive);
            add(offerBut);
            if (interactive)
                offerButTip = new AWTToolTip(OFFERBUTTIP_ENA, offerBut);
        }

        // clearOfferBut used by bank/port trade, and player trade
        clearOfferBut = new Button(CLEAR);
        clearOfferBut.addActionListener(this);
        clearOfferBut.setEnabled(interactive);
        add(clearOfferBut);

        bankBut = new Button(BANK);
        bankBut.addActionListener(this);
        bankBut.setEnabled(interactive);
        add(bankBut);

        bankUndoBut = new Button(BANK_UNDO);
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

        rollPromptCountdownLab = new Label(" ");
        add(rollPromptCountdownLab);
        rollPromptInUse = false;   // Nothing yet (no game in progress)
        autoRollTimerTask = null;  // Nothing yet

        rollBut = new Button(ROLL);
        rollBut.addActionListener(this);
        rollBut.setEnabled(interactive);
        add(rollBut);

        doneBut = new Button(DONE);
        doneBut.addActionListener(this);
        doneBut.setEnabled(interactive);
        doneButIsRestart = false;
        add(doneBut);

        quitBut = new Button(QUIT);
        quitBut.addActionListener(this);
        quitBut.setEnabled(interactive);
        add(quitBut);

        offer = new TradeOfferPanel(this, playerNumber);
        offer.setVisible(false);
        offerIsResetMessage = false;
        add(offer);

        // set the starting state of the panel
        removePlayer();
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
            client.getGameManager().setSeatLock(game, playerNumber, SOCGame.SeatLockState.LOCKED);
        }
        else if (target == UNLOCKSEAT)
        {
            // Unlock while game forming
            client.getGameManager().setSeatLock(game, playerNumber, SOCGame.SeatLockState.UNLOCKED);
        }
        else if (target == TAKEOVER)
        {
            client.getGameManager().sitDown(game, playerNumber);
        }
        else if (target == SIT)
        {
            client.getGameManager().sitDown(game, playerNumber);
        }
        else if (target == START)
        {
            client.getGameManager().startGame(game);
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
            SOCQuitConfirmDialog.createAndShow(playerInterface.getGameDisplay(), playerInterface);
        }
        else if (target == DONE)
        {
            // sqPanel.setValues(zero, zero);
            client.getGameManager().endTurn(game);
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
                client.getGameManager().clearOffer(game);
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
                client.getGameManager().clearOffer(game);
                createSendBankTradeRequest(game, give, get);
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
                client.getGameManager().bankTrade(game, bankGet, bankGive);  // reverse the previous order to undo it
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

                if (!player.getResources().contains(giveSet))
                {
                    playerInterface.print("*** "+/*I*/"You can't offer what you don't have."/*18N*/);
                }
                else if ((giveSum == 0) || (getSum == 0))
                {
                    playerInterface.print("*** "+/*I*/"A trade must contain at least one resource card from each player."/*18N*/);
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
                        playerInterface.print("*** "+/*I*/"Please choose at least one opponent's checkbox."/*18N*/);
                    }
                    else
                    {
                        SOCTradeOffer tradeOffer =
                            new SOCTradeOffer(game.getName(),
                                              playerNumber,
                                              to, giveSet, getSet);
                        client.getGameManager().offerTrade(game, tradeOffer);
                        disableBankUndoButton();
                    }
                }
            }
        }
        else if ((e.getSource() == cardList) || (target == CARD))
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
        sb.append(/*I*/"Total Special Victory Points: " + player.getSpecialVP()/*18N*/);

        ArrayList<SOCPlayer.SpecialVPInfo> svpis = player.getSpecialVPInfo();
        if ((svpis != null) && (svpis.size() > 0))
        {
            sb.append("\n");

            // null shouldn't happen: server sends svp info when SVPs are awarded,
            //  or when the client joins a game in progress.
            for (SOCPlayer.SpecialVPInfo svpi : svpis)
            {
                sb.append("\n");
                sb.append(/*I*/svpi.svp+": "+svpi.desc/*18N*/);
            }
        }

        NotifyDialog.createAndShow(playerInterface.getGameDisplay(), playerInterface, sb.toString(), null, true);
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
     * @param game  Our game
     * @param give  Resources to give, same format as {@link SOCResourceSet#SOCResourceSet(int[])
     * @param get   Resources to get, same format as {@link SOCResourceSet#SOCResourceSet(int[])
     * @since 1.1.13
     */
    private void createSendBankTradeRequest
        (SOCGame game, final int[] give, final int[] get)
    {
        SOCResourceSet giveSet = new SOCResourceSet(give);
        SOCResourceSet getSet = new SOCResourceSet(get);
        getClient().getGameManager().bankTrade(game, giveSet, getSet);

        bankGive = giveSet;
        bankGet = getSet;
        bankUndoBut.setEnabled(true);  // TODO what if trade is not allowed
    }

    /**
     * Disable the bank/port trade undo button.
     * Call when a non-trade game action is sent by the client.
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

        client.getGameManager().setSeatLock(game, playerNumber, slNext);
    }


    /**
     * Handle a click on the "play card" button, or double-click
     * on an item in the list of cards held.
     * Called from actionPerformed()
     */
    public void clickPlayCardButton()
    {
        //TODO Logic must be changed to allow i18n
        String item;
        int itemNum;  // Which one to play from list?
        SOCDevCard itemCard = null;

        setRollPrompt(null, false);  // Clear prompt if Play Card clicked (instead of Roll clicked)
        if (playerIsCurrent && player.hasPlayedDevCard())
        {
            playerInterface.print("*** "+/*I*/"You may play only one card per turn."/*18N*/);
            playCardBut.setEnabled(false);
            return;
        }

        item = cardList.getSelectedItem();
        itemNum = cardList.getSelectedIndex();

        if (item == null || item.length() == 0)
        {
            if (cardList.getItemCount() == 1)
            {
                // No card selected, but only one to choose from
                item = cardList.getItem(0);
                itemNum = 0;
                if (item.length() == 0)
                    return;
                itemCard = cardListItems.get(0);
            } else {
                /**
                 * No card selected, multiple are in the list.
                 * See if only one card isn't a "(VP)" card, isn't new.
                 * If more than one, but they're all same type (ex.
                 * unplayed Robbers), pretend there's only one.
                 */
                itemNum = -1;  // Nothing yet
                String itemNumText = null;
                for (int i = cardList.getItemCount() - 1; i >= 0; --i)
                {
                    item = cardList.getItem(i);
                    if ((item != null) && (item.length() > 0))
                    {
                        SOCDevCard dev = cardListItems.get(i);
                        if (! (dev.isVPCard() || dev.isNew()))
                        {
                            // Non-VP non-new card found
                            if (itemCard == null)
                            {
                                itemNum = i;
                                itemNumText = item;
                                itemCard = dev;
                            }
                            else if (itemCard.ctype != dev.ctype)
                            {
                                itemNum = -1;  // More than one found, and they aren't the same type;
                                break;         // we can't auto-pick among them, so stop looking through the list.
                            }
                        }
                    }
                }
                if ((itemNum == -1) || (itemCard == null))
                {
                    playerInterface.print("* "+/*I*/"Please click a card first to select it."/*18N*/);
                    return;
                }
                item = itemNumText;
            }
        } else {
            // get selected item's Card object
            if (itemNum < cardListItems.size())
                itemCard = cardListItems.get(itemNum);
        }

        // At this point, itemNum is the index of the card we want,
        // and item is its text string.
        // itemCard is its SOCDevCard object (card type and new/old flag).

        if ((! playerIsCurrent) || (itemCard == null))
        {
            return;  // <--- Early Return: Not current player ---
        }

        if (itemCard.isVPCard())
        {
            playerInterface.print("*** You secretly played this VP card when you bought it.");
            itemNum = cardList.getSelectedIndex();
            if (itemNum >= 0)
                cardList.deselect(itemNum);

            return;  // <--- Early Return: Can't play a VP card ---
        }

        int cardTypeToPlay = -1;

        switch (itemCard.ctype)
        {
        case SOCDevCardConstants.KNIGHT:
            if (game.canPlayKnight(playerNumber))
            {
                cardTypeToPlay = SOCDevCardConstants.KNIGHT;
            }
            else if (game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
            {
                playerInterface.print("* You cannot convert a ship to a warship right now.");   
            }
            break;

        case SOCDevCardConstants.ROADS:
            if (game.canPlayRoadBuilding(playerNumber))
            {
                cardTypeToPlay = SOCDevCardConstants.ROADS;
            }
            else if (player.getNumPieces(SOCPlayingPiece.ROAD) == 0)
            {
                playerInterface.print("* You have no roads left to place.");
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
            playerInterface.print("L1198 internal error: Unknown card type " + itemCard.ctype + ": " + item);

        }

        if (cardTypeToPlay != -1)
        {
            client.getGameManager().playDevCard(game, cardTypeToPlay);
            disableBankUndoButton();
        }
    }

    /** Handle a click on the roll button.
     *  Called from actionPerformed() and the auto-roll timer task.
     */
    public void clickRollButton()
    {
        if (rollPromptInUse)
            setRollPrompt(null, false);  // Clear it
        client.getGameManager().rollDice(game);
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

        sittingRobotLockBut.setLabel(lbl);
        sittingRobotLockBut.setVisible(true);

        if (robotLockButTip != null)
            robotLockButTip.setTip(tipText);
        else
            robotLockButTip = new AWTToolTip(tipText, sittingRobotLockBut);
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
        if (sitButIsLock && ! clientHasSatAlready)
        {
            sitBut.setLabel(SIT);
            sitButIsLock = false;
            if (sitButTip != null)
            {
                sitButTip.destroy();
                sitButTip = null;
            }
        } else if (clientHasSatAlready && ! sitButIsLock)
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
        cardList.setVisible(false);
        playCardBut.setVisible(false);

        giveLab.setVisible(false);
        getLab.setVisible(false);
        sqPanel.setVisible(false);
        clearOfferBut.setVisible(false);
        bankBut.setVisible(false);
        bankUndoBut.setVisible(false);

        if (! playerTradingDisabled)
        {
            offerBut.setVisible(false);  // also hides offerButTip if created
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
     */
    public void destroy()
    {
        removePlayer();
        removeAll();
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

            knightsSq.setTooltipText(/*I*/"Size of your army"/*18N*/);
            vpSq.setTooltipText(/*I*/"Your victory point total"/*18N*/);

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
            cardList.setVisible(true);
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
                doneBut.setLabel(DONE_RESTART);
            else
                doneBut.setLabel(DONE);
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

            D.ebugPrintln("**** SOCHandPanel.addPlayer(name) ****");
            D.ebugPrintln("player.getPlayerNumber() = " + playerNumber);
            D.ebugPrintln("player.isRobot() = " + player.isRobot());
            D.ebugPrintln("player.getSeatLock(" + playerNumber + ") = " + game.getSeatLock(playerNumber));
            D.ebugPrintln("game.getPlayer(client.getNickname()) = " + game.getPlayer(client.getNickname()));

            knightsSq.setTooltipText(/*I*/"Size of this opponent's army"/*18N*/);

            // To see if client already sat down at this game,
            // we can't call playerInterface.getClientHand() yet,
            // because it may not have been set at this point.
            // Use game.getPlayer(client.getNickname()) instead:

            if (player.isRobot() && (game.getPlayer(client.getNickname()) == null)
                && (game.getSeatLock(playerNumber) != SOCGame.SeatLockState.LOCKED))
            {
                addTakeOverBut();
            }

            if (player.isRobot() && (game.getPlayer(client.getNickname()) != null))
            {
                addSittingRobotLockBut();
            }
            else
            {
                removeSittingRobotLockBut();
            }

            vpLab.setVisible(true);
            vpSq.setVisible(true);
            resourceSq.setBorderColor(Color.BLACK);
            resourceLab.setText(RESOURCES);

            developmentLab.setVisible(true);
            developmentSq.setVisible(true);

            removeSitBut();
            removeRobotBut();
        }

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
     * and the first player's turn (state PLAY).
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
                pname.setBackground(this.getBackground());
        }

        updateTakeOverButton();
        if (playerIsClient)
        {
            final int gs = game.getGameState();
            boolean normalTurnStarting = (gs == SOCGame.PLAY || gs == SOCGame.PLAY1);
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
                    doneBut.setLabel(DONE);
                    doneButIsRestart = false;
                } else {
                    doneBut.setEnabled(true);  // "Restart" during game-start (label DONE_RESTART)
                }
            }
            normalTurnStarting = normalTurnStarting && playerIsCurrent;
            playCardBut.setEnabled(normalTurnStarting && (cardList.getItemCount() > 0));
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
     * Before 2.0.00, this was updateAtPlay1().
     */
    void updateAtOurGameState()
    {
        if (! playerIsClient)
            return;

        updateRollDoneBankButtons();
    }

    /** Enable,disable the proper buttons
     * when the client (player) is added to a game.
     * Call only if {@link #playerIsClient}.
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
            if (offerButTip != null)
                offerButTip.setTip(OFFERBUTTIP_DIS);
        }
    }

    /**
     * During this player's first turn, calculate the player name label's
     * background color for current player.
     */
    protected void pnameCalcColors()
    {
        if (pnameActiveBG != null)
            return;
        pnameActiveBG = SOCPlayerInterface.makeGhostColor(getBackground());
    }

    /** If trade offer is set/cleared, enable/disable buttons accordingly. */
    public void sqPanelZerosChange(boolean notAllZero)
    {
        int gs = game.getGameState();
        clearOfferBut.setEnabled(notAllZero);
        if (playerTradingDisabled)
            return;

        final boolean enaOfferBut = notAllZero && ((gs == SOCGame.PLAY) || (gs == SOCGame.PLAY1));
        offerBut.setEnabled(enaOfferBut);
        if (offerButTip != null)
        {
            if (enaOfferBut)
                offerButTip.setTip(OFFERBUTTIP_ENA);
            else
                offerButTip.setTip(OFFERBUTTIP_DIS);
        }
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
        if (! offerCounterHidesFace)
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
     * Update the displayed list of player's development cards,
     * and enable or disable the "Play Card" button.
     *<P>
     * Updates {@link #cardList} and {@link #cardListItems} to keep them in sync.
     */
    public void updateDevCards()
    {
        //TODO i18n being changed by game logic change
        SOCDevCardSet cards = player.getDevCards();

        int[] cardTypes = { SOCDevCardConstants.DISC,
                            SOCDevCardConstants.KNIGHT,
                            SOCDevCardConstants.MONO,
                            SOCDevCardConstants.ROADS,
                            SOCDevCardConstants.CAP,
                            SOCDevCardConstants.LIB,
                            SOCDevCardConstants.TEMP,
                            SOCDevCardConstants.TOW,
                            SOCDevCardConstants.UNIV };
        boolean hasOldCards = false;

        synchronized (cardList.getTreeLock())
        {
            cardList.removeAll();
            cardListItems.clear();

            // add items to the list for each new and old card, of each type
            for (int i = 0; i < cardTypes.length; i++)
            {
                final int ctype = cardTypes[i];
                int numOld = cards.getAmount(SOCDevCardSet.OLD, ctype);
                int numNew = cards.getAmount(SOCDevCardSet.NEW, ctype);
                if (numOld > 0)
                    hasOldCards = true;

                for (int j = 0; j < numOld; j++)
                {
                    cardList.add(SOCDevCard.getCardTypeName(ctype, game, false));
                    cardListItems.add(new SOCDevCard(ctype, false));
                }
                for (int j = 0; j < numNew; j++)
                {
                    // VP cards are valid immediately, so don't mark them new
                    String prefix = (SOCDevCard.isVPCard(i)) ? "" : /*I*/"*NEW* "/*18N*/;
                    cardList.add(prefix + SOCDevCard.getCardTypeName(ctype, game, false));
                    cardListItems.add(new SOCDevCard(ctype, true));
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
     */
    public void removeSittingRobotLockBut()
    {
        sittingRobotLockBut.setVisible(false);
        if (robotLockButTip != null)
        {
            robotLockButTip.destroy();
            robotLockButTip = null;
        }
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
            sitBut.setLabel(SIT);
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
        sitBut.setLabel(buttonText);
        if (sitButTip == null)
            sitButTip = new AWTToolTip(ttipText, sitBut);
        else
            sitButTip.setTip(ttipText);
        sitButIsLock = true;
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
                        playerSend[i].setTooltipText(pname);
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
     */
    public void updateCurrentOffer()
    {
        if (inPlay)
        {
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
        if (player.isRobot())
        {
            if (! game.hasTradeOffers())
                return;
        }
        offer.setMessage(/*I*/"No thanks."/*18N*/);
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
        client.getGameManager().rejectOffer(game);
        if (offerHidesControls)
            hideTradeMsgShowOthers(false);
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
     * If handpanel isn't tall enough, when the {@link #offer}
     * message panel is showing, we must hide other controls.
     *<P>
     * This method does <b>not</b> hide/show the trade offer;
     * other methods do that, and then call this method to show/hide
     * the controls that would be obscured by it.
     *<P>
     * If {@link #offerCounterHidesFace}, will check {@link TradeOfferPanel#isCounterOfferMode()}
     * and redo layout (to hide/move) if needed.
     *
     * @param hideTradeMsg Are we hiding, or showing, the trade offer message panel?
     * @see #tradeSetMessage(String)
     * @see #clearTradeMsg()
     * @see #offerHidesControls
     * @since 1.1.08
     */
    private void hideTradeMsgShowOthers(final boolean hideTradeMsg)
    {
        if (! offerHidesControls)
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
                offerButTip.setTip(OFFERBUTTIP_DIS);
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
        }
        else
        {
            // restore previous state of offer panel
            offerIsDiscardOrPickMessage = false;
            offerIsResetMessage = false;
            if ((! offerIsMessageWasTrade) || (! inPlay))
                clearTradeMsg();
            else
                updateCurrentOffer();
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
     * Normally, this will be cleared by {@link #updateValue(int)} for NUMRESOURCES,
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
            takeOverBut.setLabel(TAKEOVER);
        }
        else
        {
            takeOverBut.setLabel(/*I*/"* Seat Locked *"/*18N*/);
        }
    }

    /**
     * Client is current player; enable or disable buttons according to game state:
     * {@link #rollBut}, {@link #doneBut}, {@link #bankBut}.
     * Call only if {@link #playerIsCurrent} and {@link #playerIsClient}.
     */
    private void updateRollDoneBankButtons()
    {
        final int gs = game.getGameState();
        rollBut.setEnabled(gs == SOCGame.PLAY);
        doneBut.setEnabled((gs == SOCGame.PLAY1) || (gs == SOCGame.SPECIAL_BUILDING)
            || (gs <= SOCGame.START3B) || doneButIsRestart);
        bankBut.setEnabled(gs == SOCGame.PLAY1);
    }

    /**
     * update the seat lock button so that it
     * allows a player to lock an unlocked seat
     * and vice versa. Called from client when server
     * sends a SETSEATLOCK message. Updates both
     * buttons: The robot-seat-lock (when robot playing at
     * this position) and the robot-lockout (game forming,
     * seat vacant, no robot here please) buttons.
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

        sittingRobotLockBut.setLabel(lbl);
        if (robotLockButTip != null)
        {
            final String prevTip = robotLockButTip.getTip();
            if (prevTip != tipText)  // constant string ref, so don't need equals()
                robotLockButTip.setTip(tipText);
        }

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
            sitBut.setLabel(buttonText);
            if (sitButTip == null)
                sitButTip = new AWTToolTip(ttipText, sitBut);
            else
                sitButTip.setTip(ttipText);

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
        larmyLab.setText(haveIt ? /*I*/"L. Army"/*18N*/ : "");
    }

    /**
     * Turn the "longest road" label on or off.  If the game uses the large sea board,
     * the label shows "L. Route" instead of "L. Road".
     *
     * @param haveIt  true if this player has the longest road
     */
    protected void setLRoad(boolean haveIt)
    {
        lroadLab.setText(haveIt ? (game.hasSeaBoard ? /*I*/"L. Route"/*18N*/ : /*I*/"L. Road"/*18N*/) : "");
    }

    /**
     * update the value of a player element.
     * Call this after updating game data.
     *<P>
     * If {@link #VICTORYPOINTS} is updated, and game state is {@link SOCGame#OVER}, check for winner
     * and update (player name label, victory-points tooltip, disable bank/trade btn)
     *
     * @param vt  the type of value, such as {@link #VICTORYPOINTS}
     *            or {@link SOCPlayerElement#SHEEP}.
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
                        vpSq.setTooltipText(/*I*/"Winner with " + newVP + " victory points"/*18N*/);
                        pname.setText(/*I*/player.getName() + " - Winner"/*18N*/);
                    }
                    if (interactive)
                    {
                        bankBut.setEnabled(false);
                        bankUndoBut.setEnabled(false);
                        playCardBut.setEnabled(false);
                    }
                    doneBut.setLabel(DONE_RESTART);
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

            developmentSq.setIntValue(player.getDevCards().getTotal());

            break;

        case Knight:
            knightsSq.setIntValue(player.getNumKnights());
            break;

        case Cloth:
            if (clothSq != null)
                clothSq.setIntValue(player.getCloth());
            break;

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
     */
    public void updateResourcesVP()
    {
        if (playerIsClient)
        {
            updateValue(PlayerClientListener.UpdateType.Clay);
            updateValue(PlayerClientListener.UpdateType.Ore);
            updateValue(PlayerClientListener.UpdateType.Sheep);
            updateValue(PlayerClientListener.UpdateType.Wheat);
            updateValue(PlayerClientListener.UpdateType.Wood);
            updateResourceTradeCosts(false);
        }
        else
        {
            updateValue(PlayerClientListener.UpdateType.Resources);
        }
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
     * the state becomes PLAY again, before the current player
     * is changed to the next player.  So, it appears that
     * this player can roll again, but they cannot.
     * To guard against this, use {@link #isClientAndCurrentlyCanRoll()} instead.
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
        final int faceW = 40;  // face icon width
        final int pnameW = dim.width - (inset + faceW + inset + inset);  // player name width, to right of face

        if (!inPlay)
        {
            /* just show the 'sit' button */
            /* and the 'robot' button     */
            /* and the pname label        */
            sitBut.setBounds((dim.width - 60) / 2, (dim.height - 82) / 2, 60, 40);
            pname.setBounds(inset + faceW + inset, inset, pnameW, lineH);
        }
        else
        {
            //TODO i18n match with actual label used for display
            final int stlmtsW = fm.stringWidth("Stlmts:_");  // Bug in stringWidth does not give correct size for ' '
            final int knightsW = fm.stringWidth("Soldiers:") + 2;  // +2 because Label text is inset from column 0
            // (for item count labels, either Settlements or Soldiers/Knights is widest text)

            // Top of panel: Face icon, player name to right
            faceImg.setBounds(inset, inset, faceW, faceW);
            pname.setBounds(inset + faceW + inset, inset, pnameW, lineH);

            // To right of face, below player name:
            // Victory Points count, Largest Army, Longest Road
            final int vpW = fm.stringWidth(vpLab.getText().replace(' ','_'));
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
                final int sheepW = fm.stringWidth("Sheep:_");  // Bug in stringWidth does not give correct size for ' '
                final int pcW = fm.stringWidth(CARD.replace(' ','_'));  // Bug in stringWidth
                final int giveW = fm.stringWidth(GIVE.replace(' ','_'));  // Width of trading Give/Get labels
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
                cardList.setBounds(clX, devCardsY, clW, (4 * (lineH + space)) - 2);
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
                // Bottom has columns of item counts on left, right:
                //   Soldiers, Res, Dev Cards to left;
                //   Ships, Roads, Settlements, Cities to right
                //   Robot lock button (during play) in bottom center

                final int balloonH = dim.height - (inset + (4 * (lineH + space)) + inset);  // offer-message panel
                final int dcardsW = fm.stringWidth("Dev._Cards:_");  //Bug in stringWidth does not give correct size for ' '

                if (player.isRobot())
                {
                    if (game.getPlayer(client.getNickname()) == null)
                    {
                        // If client not seated at this game, show "Take Over" button
                        // just above the lower-left, lower-right columns of item counts
                        int yb = (inset + balloonH) - 10;
                        if (game.hasSeaBoard)
                            yb -= (lineH + space);
                        takeOverBut.setBounds(10, yb, dim.width - 20, 20);
                    }
                    else if (sittingRobotLockBut.isVisible())
                    {
                        //seatLockBut.setBounds(10, inset+balloonH-10, dim.width-20, 20);
                        // Lock button during play: Bottom of panel, between the 2 columns of item counts
                        sittingRobotLockBut.setBounds
                            (inset + dcardsW + space + ColorSquare.WIDTH + space, inset + balloonH + (lineH + space) + (lineH / 2),
                             (dim.width - (2 * (inset + ColorSquare.WIDTH + (2 * space))) - stlmtsW - dcardsW), 2 * (lineH + space));
                    }
                }

                // Are we tall enough for room, after the offer, for other controls?
                // If not, they will be hid when offer is visible.
                offerHidesControls = (dim.height - (inset + faceW + space + balloonH))
                    < (3 * (lineH + space));
                if (offerHidesControls)
                {
                    // This field is calculated based on height.
                    offerCounterHidesFace =
                        ((dim.height - TradeOfferPanel.OFFER_HEIGHT - TradeOfferPanel.OFFER_COUNTER_HEIGHT + TradeOfferPanel.OFFER_BUTTONS_HEIGHT)
                        < faceW);

                    // This is a dynamic flag, set by hideTradeMsgShowOthers
                    // when the user clicks button to show/hide the counter-offer.
                    // If true, hideTradeMsgShowOthers has already hid faceImg,
                    // pname, vpLab and vpSq, to make room for it.
                    if (offerCounterHidingFace)
                    {
                        offer.setBounds(inset, inset, dim.width - (2 * inset), dim.height - (2 * inset));
                    } else {
                        offer.setBounds(inset, inset + faceW + space, dim.width - (2 * inset), dim.height - (inset + faceW + 2 * space));
                    }
                    offer.setCounterHidesBalloonPoint(offerCounterHidingFace);
                } else {
                    offer.setBounds(inset, inset + faceW + space, dim.width - (2 * inset), balloonH);
                    offerCounterHidesFace = false;
                }
                offer.doLayout();

                // Lower-left: Column of item counts:
                // Cloth, Soldiers, Resources, Dev Cards
                resourceLab.setBounds(inset, inset + balloonH + (2 * (lineH + space)), dcardsW, lineH);
                resourceSq.setBounds(inset + dcardsW + space, inset + balloonH + (2 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                developmentLab.setBounds(inset, inset + balloonH + (3 * (lineH + space)), dcardsW, lineH);
                developmentSq.setBounds(inset + dcardsW + space, inset + balloonH + (3 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                knightsLab.setBounds(inset, inset + balloonH + (lineH + space), dcardsW, lineH);
                knightsSq.setBounds(inset + dcardsW + space, inset + balloonH + (lineH + space), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                if (clothSq != null)
                {
                    clothLab.setBounds(inset, inset + balloonH, dcardsW, lineH);
                    clothSq.setBounds(inset + dcardsW + space, inset + balloonH, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                }

                // Lower-right: Column of piece counts:
                // Ships, Roads, Settlements, Cities
                if (shipSq != null)
                {
                    shipLab.setBounds(dim.width - inset - stlmtsW - ColorSquare.WIDTH - space, inset + balloonH, stlmtsW, lineH);
                    shipSq.setBounds(dim.width - inset - ColorSquare.WIDTH, inset + balloonH, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                }
                roadLab.setBounds(dim.width - inset - stlmtsW - ColorSquare.WIDTH - space, inset + balloonH + (lineH + space), stlmtsW, lineH);
                roadSq.setBounds(dim.width - inset - ColorSquare.WIDTH, inset + balloonH + (lineH + space), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                settlementLab.setBounds(dim.width - inset - stlmtsW - ColorSquare.WIDTH - space, inset + balloonH + (2 * (lineH + space)), stlmtsW, lineH);
                settlementSq.setBounds(dim.width - inset - ColorSquare.WIDTH, inset + balloonH + (2 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                cityLab.setBounds(dim.width - inset - stlmtsW - ColorSquare.WIDTH - space, inset + balloonH + (3 * (lineH + space)), stlmtsW, lineH);
                citySq.setBounds(dim.width - inset - ColorSquare.WIDTH, inset + balloonH + (3 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
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
     * @author Jeremy D Monin <jeremy@nand.net>
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
                    setRollPrompt(AUTOROLL_COUNTDOWN + Integer.toString(timeRemain), false);
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
     * @author Jeremy D Monin <jeremy@nand.net>
     */
    protected static class ResourceTradeMenuItem extends MenuItem
    {
        private int tradeFrom, tradeTo;
        private int tradeNum;
        private boolean shortTxt;

        /**
         * Create a bank/port trade MenuItem, with text such as "Trade 2 brick for 1 wheat".
         *
         * @param numFrom  Number of resources to trade for 1 resource
         * @param typeFrom Source resource type, as in {@link SOCResourceConstants}.
         * @param typeTo   Target resource type, as in {@link SOCResourceConstants}.
         *                 If typeFrom == typeTo, menuitem will be disabled.
         * @param shortText If true, short ("For 1 wheat") vs full "Trade 2 brick for 1 wheat"
         */
        public ResourceTradeMenuItem(int numFrom, int typeFrom, int typeTo, boolean shortText)
        {
            super( (shortText
                    ? /*I*/"For 1 "/*18N*/
                    : /*I*/("Trade " + numFrom + " " + SOCResourceConstants.resName(typeFrom) + " for 1 ")/*18N*/)
                   + SOCResourceConstants.resName(typeTo));
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
                setLabel(/*I*/"For 1 " + SOCResourceConstants.resName(tradeTo)/*18N*/);
            else
                setLabel(/*I*/"Trade " + numFrom + " " + SOCResourceConstants.resName(tradeFrom)
                        + " for 1 " + SOCResourceConstants.resName(tradeTo)/*18N*/);
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
                hp.getPlayerInterface().print("* "+/*I*/"You cannot trade at this time."/*18N*/+"\n");
                return;
            }

            int[] give = new int[5];
            int[] get = new int[5];
            give[tradeFrom - 1] = tradeNum;
            get[tradeTo - 1] = 1;
            hp.createSendBankTradeRequest(game, give, get);
        }

    }  // ResourceTradeMenuItem

    /**
     * Menu for right-click on resource square to trade with bank/port.
     *
     * @see SOCHandPanel.ResourceTradeTypeMenu
     * @see SOCBoardPanel.ResourceTradeAllMenu
     * @author Jeremy D Monin <jeremy@nand.net>
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
     * @author Jeremy D Monin <jeremy@nand.net>
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
          super(hp, /*I*/"Bank/Port Trade"/*18N*/);
          init(typeFrom, sq, numFrom, false);
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
            super(hp, /*I*/"Trade Port"/*18N*/);
            if (! hp.getPlayerInterface().clientIsCurrentPlayer())
                throw new IllegalStateException("Not current player");
            init(typeFrom, null, hp.resourceTradeCost[typeFrom], forThree1);
        }

        /** Common to both constructors */
        private void init(int typeFrom, ColorSquare sq, int numFrom, boolean forThree1)
        {
          resSq = sq;
          resTypeFrom = typeFrom;
          costFrom = numFrom;
          isForThree1 = forThree1;
          if (forThree1)
              setLabel(/*I*/"Trade " + costFrom + " "
                  + SOCResourceConstants.resName(typeFrom) + " "/*18N*/);

          if (resSq != null)
          {
              resSq.add(this);
              resSq.addMouseListener(this);
          }
          tradeForItems = new ResourceTradeMenuItem[5];
          for (int i = 0; i < 5; ++i)
          {
              tradeForItems[i] = new ResourceTradeMenuItem(numFrom, typeFrom, i+1, forThree1);
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
