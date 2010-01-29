/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2010 Jeremy D. Monin <jeremy@nand.net>
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

import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
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

import java.util.Timer;  // For auto-roll
import java.util.TimerTask;


/**
 * This panel displays a player's information.
 * If the player is us, then more information is
 * displayed than in another player's hand panel.
 *<P>
 * Custom layout: see {@link #doLayout()}.
 * To set this panel's position or size, please use {@link #setBounds(int, int, int, int)},
 * because it is overridden to also update {@link #getBlankStandIn()}.
 */
public class SOCHandPanel extends Panel implements ActionListener
{
    /** Minimum desired width, in pixels */
    public static final int WIDTH_MIN = 218;

    /** Items to update via {@link #updateValue(int)};
     * similar values to {@link soc.message.SOCPlayerElement}
     */
    public static final int ROADS = 0;
    public static final int SETTLEMENTS = 1;
    public static final int CITIES = 2;
    public static final int NUMRESOURCES = 3;
    public static final int NUMDEVCARDS = 4;
    public static final int NUMKNIGHTS = 5;
    public static final int VICTORYPOINTS = 6;
    public static final int LONGESTROAD = 7;
    public static final int LARGESTARMY = 8;
    public static final int CLAY = 9;
    public static final int ORE = 10;
    public static final int SHEEP = 11;
    public static final int WHEAT = 12;
    public static final int WOOD = 13;

    /**
     * Item flag for asked special build in {@link #updateValue(int)}.
     * @since 1.1.08
     */
    public static final int ASK_SPECIAL_BUILD = 16;  // same as SOCPlayerElement.ASK_SPECIAL_BUILD

    /** Auto-roll timer countdown, 5 seconds unless changed at program start. */
    public static int AUTOROLL_TIME = 5;

    /** Array of five zeroes, one per resource type; for {@link #sqPanel}. */
    protected static final int[] zero = { 0, 0, 0, 0, 0 };
    /** Before game starts, use {@link #pname} to show if a seat is no-robots-allowed. */
    protected static final String SITLOCKED = "Locked: No robot";
    protected static final String SIT = "Sit Here";
    protected static final String START = "Start Game";
    protected static final String ROBOT = "Robot";
    protected static final String TAKEOVER = "Take Over";
    protected static final String LOCKSEAT = "Lock";
    protected static final String UNLOCKSEAT = "Unlock";
    protected static final String ROLL = "Roll";
    protected static final String QUIT = "Quit";
    protected static final String DONE = "Done";
    /** Text of Done button at end of game becomes Restart button */
    protected static final String DONE_RESTART = "Restart";
    protected static final String CLEAR = "Clear";
    protected static final String SEND = "Offer";
    protected static final String BANK = "Bank/Port";
    protected static final String CARD = "  Play Card  ";
    protected static final String GIVE = "I Give:";  // No trailing space (room for wider colorsquares)
    protected static final String GET = "I Get:";
    protected static final String AUTOROLL_COUNTDOWN = "Auto-Roll in: ";
    protected static final String ROLL_OR_PLAY_CARD = "Roll or Play Card";
    protected static final String OFFERBUTTIP_ENA = "Send trade offer to other players";
    protected static final String OFFERBUTTIP_DIS = "To offer a trade, first click resources";
    protected static final String TRADEMSG_DISCARD = "Discarding..."; 

    /** If player has won the game, update pname label */
    protected static final String WINNER_SUFFIX = " - Winner";

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

    protected Button sitBut;
    protected Button robotBut;
    protected Button startBut;
    protected Button takeOverBut;

    /** Seat lock/unlock shown in robot handpanels during game play,
     *  to prevent/allow humans to join and take over a robot's seat.
     *  Used during different game states than {@link #sitBut}.
     */
    protected Button sittingRobotLockBut;

    /** When true, the game is still forming, player has chosen a seat;
     *  "Sit Here" button is labeled as "Lock".  Humans can use this to
     *  lock robots out of that seat, so as to start a game with fewer
     *  players and some vacant seats.
     *  Set by {@link #renameSitButLock()}, cleared elsewhere.
     *  This affects {@link #sitBut} and not {@link #sittingRobotLockBut}.
     *  @see #addPlayer(String)
     *  @see #updateSeatLockButton()
     */
    protected boolean sitButIsLock;
    protected SOCFaceButton faceImg;
    protected Label pname;
    protected Label vpLab;
    protected ColorSquare vpSq;
    protected Label larmyLab;
    protected Label lroadLab;
    protected ColorSquare claySq;
    protected ColorSquare oreSq;
    protected ColorSquare sheepSq;
    protected ColorSquare wheatSq;
    protected ColorSquare woodSq;
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
    protected Label settlementLab;
    protected Label cityLab;
    protected Label roadLab;
    protected ColorSquare resourceSq;
    protected Label resourceLab;
    protected ColorSquare developmentSq;
    protected Label developmentLab;
    protected ColorSquare knightsSq;
    protected Label knightsLab;
    //protected Label cardLab; // no longer used?
    protected List cardList;
    protected Button playCardBut;
    protected SquaresPanel sqPanel;

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

    /** Clear the current trade offer at client and server */
    protected Button clearOfferBut;

    /** Trade resources with the bank or port */
    protected Button bankBut;

    /**
     * Checkboxes to send to the other three players.
     * Enabled/disabled at removeStartBut().
     * This is null if {@link #playerTradingDisabled}.
     *
     * @see #playerSendMap
     */
    protected ColorSquare[] playerSend;

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

    /** Does this panel represent our client's own hand?  If true, implies {@link #interactive}. */
    protected boolean playerIsClient;

    /** Is this panel's player the game's current player?  Used for hilight - set in updateAtTurn() */
    protected boolean playerIsCurrent;

    /** Do we have any seated player? Set by {@link #addPlayer(String)}, cleared by {@link #removePlayer()}. */
    protected boolean inPlay;

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
     * @see #offerIsDiscardMessage
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
     */
    protected boolean offerIsResetMessage;

    /**
     * Board-reset voting: If true, {@link #offer} is holding a discard message.
     */
    protected boolean offerIsDiscardMessage;

    /**
     * Board-reset voting: If true, {@link #offer} was holding an active trade offer
     * before {@link #offerIsResetMessage} or {@link #offerIsDiscardMessage} was set.
     */
    protected boolean offerIsMessageWasTrade;

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
     * @param pl   the player data
     * @param in   the interactive flag setting
     */
    protected void creation(SOCPlayerInterface pi, SOCPlayer pl, boolean in)
    {
        playerInterface = pi;
        client = pi.getClient();
        game = pi.getGame();
        player = pl;
        playerIsCurrent = false;
        playerIsClient = false;  // confirmed by call to removePlayer() at end of method.
        interactive = in;

        // Note no AWT layout is used - custom layout, see doLayout().

        final Color pcolor = playerInterface.getPlayerColor(player.getPlayerNumber());
        setBackground(pcolor);
        setForeground(COLOR_FOREGROUND);
        setFont(new Font("Helvetica", Font.PLAIN, 10));

        blankStandIn = new ColorSquare(pcolor, "One moment...");
        blankStandIn.setVisible(false);
        // playerinterface.initInterfaceElements will add blankStandIn to its layout, and set its size/position.

        faceImg = new SOCFaceButton(playerInterface, player.getPlayerNumber());
        add(faceImg);

        pname = new Label();
        pname.setFont(new Font("Serif", Font.PLAIN, 14));
        add(pname);
        pnameActiveBG = null;  // Will be calculated at first turn

        startBut = new Button(START);
        startBut.addActionListener(this);
        // this button always enabled
        add(startBut);

        vpLab = new Label("Points: ");
        add(vpLab);
        vpSq = new ColorSquare(ColorSquare.GREY, 0);
        vpSq.setTooltipText("Total victory points for this opponent");
        if (SOCGame.VP_WINNER <= 12)
        {
            vpSq.setTooltipHighWarningLevel("Close to winning", SOCGame.VP_WINNER - 2);  // (win checked in SOCGame.checkForWinner)
        } else {
            vpSq.setTooltipHighWarningLevel("Close to winning", SOCGame.VP_WINNER - 3);
        }
        add(vpSq);

        larmyLab = new Label("", Label.CENTER);
        larmyLab.setForeground(new Color(142, 45, 10));
        larmyLab.setFont(new Font("SansSerif", Font.BOLD, 12));
        add(larmyLab);

        lroadLab = new Label("", Label.CENTER);
        lroadLab.setForeground(new Color(142, 45, 10));
        lroadLab.setFont(new Font("SansSerif", Font.BOLD, 12));
        add(lroadLab);

        clayLab = new Label("Clay:");
        add(clayLab);
        claySq = new ColorSquare(ColorSquare.CLAY, 0);
        add(claySq);
        claySq.setTooltipText("Right-click to trade clay");

        oreLab = new Label("Ore:");
        add(oreLab);
        oreSq = new ColorSquare(ColorSquare.ORE, 0);
        add(oreSq);
        oreSq.setTooltipText("Right-click to trade ore");

        sheepLab = new Label("Sheep:");
        add(sheepLab);
        sheepSq = new ColorSquare(ColorSquare.SHEEP, 0);
        add(sheepSq);
        sheepSq.setTooltipText("Right-click to trade sheep");

        wheatLab = new Label("Wheat:");
        add(wheatLab);
        wheatSq = new ColorSquare(ColorSquare.WHEAT, 0);
        add(wheatSq);
        wheatSq.setTooltipText("Right-click to trade wheat");

        woodLab = new Label("Wood:");
        add(woodLab);
        woodSq = new ColorSquare(ColorSquare.WOOD, 0);
        add(woodSq);
        woodSq.setTooltipText("Right-click to trade wood");

        //cardLab = new Label("Cards:");
        //add(cardLab);
        cardList = new List(0, false);
        cardList.addActionListener(this);  // double-click support
        add(cardList);

        roadSq = new ColorSquare(ColorSquare.GREY, 0);
        add(roadSq);
        roadSq.setTooltipText("Pieces available to place");
        roadSq.setTooltipLowWarningLevel("Almost out of roads to place", 2);
        roadSq.setTooltipZeroText("No more roads available");
        roadLab = new Label("Roads:");
        add(roadLab);

        settlementSq = new ColorSquare(ColorSquare.GREY, 0);
        add(settlementSq);
        settlementSq.setTooltipText("Pieces available to place");
        settlementSq.setTooltipLowWarningLevel("Almost out of settlements to place", 1);
        settlementSq.setTooltipZeroText("No more settlements available");
        settlementLab = new Label("Stlmts:");
        add(settlementLab);

        citySq = new ColorSquare(ColorSquare.GREY, 0);
        add(citySq);
        citySq.setTooltipText("Pieces available to place");
        citySq.setTooltipLowWarningLevel("Almost out of cities to place", 1);
        citySq.setTooltipZeroText("No more cities available");
        cityLab = new Label("Cities:");
        add(cityLab);

        knightsLab = new Label("Soldiers:");  // No trailing space (room for wider colorsquares at left)
        add(knightsLab);
        knightsSq = new ColorSquare(ColorSquare.GREY, 0);
        add(knightsSq);
        knightsSq.setTooltipText("Size of this army");

        resourceLab = new Label("Resources: ");
        add(resourceLab);
        resourceSq = new ColorSquare(ColorSquare.GREY, 0);
        add(resourceSq);
        resourceSq.setTooltipText("Amount in hand");
        resourceSq.setTooltipHighWarningLevel("If 7 is rolled, would discard half these resources", 8);

        developmentLab = new Label("Dev. Cards: ");
        add(developmentLab);
        developmentSq = new ColorSquare(ColorSquare.GREY, 0);
        add(developmentSq);
        developmentSq.setTooltipText("Amount in hand");

        sittingRobotLockBut = new Button(UNLOCKSEAT);
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
        sqPanel.setVisible(false); // else it's visible in all (dunno why?)

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

        if (playerTradingDisabled)
        {
            playerSend = null;
            playerSendMap = null;
        } else {
            playerSend = new ColorSquare[game.maxPlayers-1];
            playerSendMap = new int[game.maxPlayers-1];

            // set the trade buttons correctly
            int cnt = 0;
            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                if (pn != player.getPlayerNumber())
                {
                    Color color = playerInterface.getPlayerColor(pn);
                    playerSendMap[cnt] = pn;
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

        offer = new TradeOfferPanel(this, player.getPlayerNumber());
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
            client.lockSeat(game, player.getPlayerNumber());
        }
        else if (target == UNLOCKSEAT)
        {
            client.unlockSeat(game, player.getPlayerNumber());
        }
        else if (target == TAKEOVER)
        {
            client.sitDown(game, player.getPlayerNumber());
        }
        else if (target == SIT)
        {
            client.sitDown(game, player.getPlayerNumber());
        }
        else if (target == START)
        {
            client.startGame(game);
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
            SOCQuitConfirmDialog.createAndShow(client, playerInterface);
        }
        else if (target == DONE)
        {
            // sqPanel.setValues(zero, zero);
            client.endTurn(game);
        }
        else if (target == DONE_RESTART)
        {
            playerInterface.resetBoardRequest();
        }
        else if (target == CLEAR)
        {
            clearOffer(true);    // Zero the square panel numbers, unless board-reset vote in progress
            if (game.getGameState() == SOCGame.PLAY1)
            {
                client.clearOffer(game);
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
                client.clearOffer(game);

                SOCResourceSet giveSet = new SOCResourceSet(give);
                SOCResourceSet getSet = new SOCResourceSet(get);
                client.bankTrade(game, giveSet, getSet);
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
                    playerInterface.print("*** You can't offer what you don't have.");
                }
                else if ((giveSum == 0) || (getSum == 0))
                {
                    playerInterface.print("*** A trade must contain at least one resource card from each player.");
                }
                else
                {
                    // bool array elements begin as false
                    boolean[] to = new boolean[game.maxPlayers];
                    boolean toAny = false;

                    if (game.getCurrentPlayerNumber() == player.getPlayerNumber())
                    {
                        for (int i = 0; i < (game.maxPlayers - 1); i++)
                        {
                            if (playerSend[i].getBoolValue() && ! game.isSeatVacant(playerSendMap[i]))
                            {
                                to[playerSendMap[i]] = true;
                                toAny = true;
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
                        playerInterface.print("*** Please choose at least one opponent's checkbox.");
                    }
                    else
                    {
                        SOCTradeOffer tradeOffer =
                            new SOCTradeOffer(game.getName(),
                                              player.getPlayerNumber(),
                                              to, giveSet, getSet);
                        client.offerTrade(game, tradeOffer);
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
     * Handle a click on the "play card" button, or double-click
     * on an item in the list of cards held.
     * Called from actionPerformed()
     */
    public void clickPlayCardButton()
    {
        String item;
        int itemNum;  // Which one to play from list?

        setRollPrompt(null, false);  // Clear prompt if Play Card clicked (instead of Roll clicked)
        if (playerIsCurrent && player.hasPlayedDevCard())
        {
            playerInterface.print("*** You may play only one card per turn.");
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
                    if ((item != null) && (item.length() > 0)
                        && (item.indexOf("VP)") <= 0)
                        && ! item.startsWith("*NEW*"))
                    {
                        // Non-VP non-new card found
                        if (itemNum == -1)
                        {
                            itemNum = i;
                            itemNumText = item;
                        } else if (! item.equals(itemNumText))
                        {
                            itemNum = -1;  // More than one found,
                            break;         // stop looking.
                        }
                    }
                }
                if (itemNum == -1)
                {
                    playerInterface.print("* Please click a card first to select it.");
                    return;
                } else {
                    item = itemNumText;
                }
            }
        }

        // At this point, itemNum is the index of the card we want,
        // and item is its text string.

        if (! playerIsCurrent)
        {
            return;  // <--- Early Return: Not current player ---
        }

        int cardTypeToPlay = -1;
        if (item.equals("Soldier"))
        {
            if (game.canPlayKnight(player.getPlayerNumber()))
            {
                cardTypeToPlay = SOCDevCardConstants.KNIGHT;
            }
        }
        else if (item.equals("Road Building"))
        {
            if (game.canPlayRoadBuilding(player.getPlayerNumber()))
            {
                cardTypeToPlay = SOCDevCardConstants.ROADS;
            }
        }
        else if (item.equals("Year of Plenty"))
        {
            if (game.canPlayDiscovery(player.getPlayerNumber()))
            {
                cardTypeToPlay = SOCDevCardConstants.DISC;
            }
        }
        else if (item.equals("Monopoly"))
        {
            if (game.canPlayMonopoly(player.getPlayerNumber()))
            {
                cardTypeToPlay = SOCDevCardConstants.MONO;
            }
        }
        else if (item.indexOf("VP)") > 0)
        {
            playerInterface.print("*** You secretly played this VP card when you bought it.");
            itemNum = cardList.getSelectedIndex();
            if (itemNum >= 0)
                cardList.deselect(itemNum);
        }

        if (cardTypeToPlay != -1)
        {
            client.playDevCard(game, cardTypeToPlay);
        }
    }

    /** Handle a click on the roll button.
     *  Called from actionPerformed() and the auto-roll timer task.
     */
    public void clickRollButton()
    {
        if (rollPromptInUse)
            setRollPrompt(null, false);  // Clear it
        client.rollDice(game);
        rollBut.setEnabled(false);  // Only one roll per turn
    }

    /**
     * Add the "lock" button for when a robot is currently playing in this position.
     * This is not the large "lock" button seen in empty positions when the
     * game is forming, which prevents a robot from sitting down. That button
     * is actually sitBut with a different label.
     *<P>
     * This method was <tt>addSeatLockBut()</tt> before 1.1.07.
     */
    public void addSittingRobotLockBut()
    {
        D.ebugPrintln("*** addSeatLockBut() ***");
        D.ebugPrintln("seatLockBut = " + sittingRobotLockBut);

            if (game.isSeatLocked(player.getPlayerNumber()))
            {
                sittingRobotLockBut.setLabel(UNLOCKSEAT);
            }
            else
            {
                sittingRobotLockBut.setLabel(UNLOCKSEAT);
            }

            sittingRobotLockBut.setVisible(true);

            //seatLockBut.repaint();
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
     */
    public void addSitButton(boolean clientHasSatAlready)
    {
        if (sitButIsLock && ! clientHasSatAlready)
        {
            sitBut.setLabel(SIT);
            sitButIsLock = false;
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
        faceImg.setVisible(false);
        pname.setVisible(false);
        roadSq.setVisible(false);
        roadLab.setVisible(false);
        settlementLab.setVisible(false);
        settlementSq.setVisible(false);
        cityLab.setVisible(false);
        citySq.setVisible(false);
        knightsSq.setVisible(false);
        knightsLab.setVisible(false);

        offer.setVisible(false);

        larmyLab.setVisible(false);
        lroadLab.setVisible(false);

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
        resourceLab.setVisible(false);
        resourceSq.setVisible(false);
        developmentLab.setVisible(false);
        developmentSq.setVisible(false);
        faceImg.removeFacePopupMenu();  // Also disables left-click to change

        removeTakeOverBut();
        removeSittingRobotLockBut();

        inPlay = false;

        validate();
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
        knightsLab.setVisible(true);
        knightsSq.setVisible(true);

        playerIsCurrent = (game.getCurrentPlayerNumber() == player.getPlayerNumber());

        if (player.getName().equals(client.getNickname()))
        {
            D.ebugPrintln("SOCHandPanel.addPlayer: This is our hand");

            playerIsClient = true;
            playerInterface.setClientHand(this);

            knightsSq.setTooltipText("Size of your army");
            vpSq.setTooltipText("Your victory point total");

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
            doneButIsRestart = ((game.getGameState() <= SOCGame.START2B)
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
            int pnum = player.getPlayerNumber();
            for (int i = 0; i < game.maxPlayers; i++)
            {
                playerInterface.getPlayerHandPanel(i).removeTakeOverBut();
                if (gameForming && (i != pnum) && game.isSeatVacant(i))
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
            D.ebugPrintln("player.getPlayerNumber() = " + player.getPlayerNumber());
            D.ebugPrintln("player.isRobot() = " + player.isRobot());
            D.ebugPrintln("game.isSeatLocked(" + player.getPlayerNumber() + ") = " + game.isSeatLocked(player.getPlayerNumber()));
            D.ebugPrintln("game.getPlayer(client.getNickname()) = " + game.getPlayer(client.getNickname()));

            knightsSq.setTooltipText("Size of this opponent's army");

            // To see if client already sat down at this game,
            // we can't call playerInterface.getClientHand() yet,
            // because it may not have been set at this point.
            // Use game.getPlayer(client.getNickname()) instead:

            if (player.isRobot() && (game.getPlayer(client.getNickname()) == null) && (!game.isSeatLocked(player.getPlayerNumber())))
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

            resourceLab.setVisible(true);
            resourceSq.setVisible(true);
            developmentLab.setVisible(true);
            developmentSq.setVisible(true);

            removeSitBut();
            removeRobotBut();
        }

        inPlay = true;

        validate();
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
        if (! game.canRollDice(player.getPlayerNumber()))
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
        playerIsCurrent = (game.getCurrentPlayerNumber() == player.getPlayerNumber());
        if (playerIsCurrent)
        {
            if (pnameActiveBG == null)
                pnameCalcColors();
            pname.setBackground(pnameActiveBG);
            updateRollButton();
        }
        else
        {
            pname.setBackground(this.getBackground());
        }

        updateTakeOverButton();
        if (playerIsClient)
        {
            int gs = game.getGameState();
            boolean normalTurnStarting = (gs == SOCGame.PLAY || gs == SOCGame.PLAY1);
            clearOffer(normalTurnStarting);  // Zero the square panel numbers, etc. (TODO) better descr.
                // at any player's turn, not just when playerIsCurrent.
            if (doneButIsRestart && normalTurnStarting)
            {
                doneBut.setLabel(DONE);
                doneButIsRestart = false;
            }
            normalTurnStarting = normalTurnStarting && playerIsCurrent;
            doneBut.setEnabled(normalTurnStarting || (gs <= SOCGame.START2B)
                    || (playerIsCurrent && (gs == SOCGame.SPECIAL_BUILDING)));
                // "Done" at Normal turn,
                // or "Restart" during game-start (label DONE_RESTART),
                // or "Done" during 6-player Special Building Phase.
            playCardBut.setEnabled(normalTurnStarting && (cardList.getItemCount() > 0));
            bankBut.disable();  // enabled by updateAtPlay1()
        }

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
     * Client is current player; state changed from PLAY to PLAY1.
     * (Dice has been rolled, or card played.)
     * Update interface accordingly.
     * Should not be called except by client's playerinterface.
     */
    public void updateAtPlay1()
    {
       if (! playerIsClient)
           return;

       bankBut.enable();
    }

    /** Enable,disable the proper buttons
     * when the client (player) is added to a game.
     */
    public void updateButtonsAtAdd()
    {
        if (playerIsCurrent)
        {
            updateRollButton();
            bankBut.setEnabled(game.getGameState() == SOCGame.PLAY1);
        }
        else
        {
            rollBut.disable();
            doneBut.disable();
            playCardBut.disable();
            bankBut.disable();  // enabled by updateAtPlay1()
        }

        clearOfferBut.disable();  // No trade offer has been set yet
        if (! playerTradingDisabled)
        {
            offerBut.disable();
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
     *
     * Call only if panel's player is the client, and the game's current player.
     *
     * Called when server sends a SOCRollDicePrompt message.
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
     */
    public void updateDevCards()
    {
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
        String[] cardNames = {"Year of Plenty",
                              "Soldier",
                              "Monopoly",
                              "Road Building",
                              "Gov. House (1VP)",
                              "Market (1VP)",
                              "Temple (1VP)",
                              "Chapel (1VP)",
                              "University (1VP)"};
        boolean hasOldCards = false;

        synchronized (cardList.getTreeLock())
        {
            cardList.removeAll();

            // add items to the list for each new and old card, of each type
            for (int i = 0; i < cardTypes.length; i++)
            {
                int numOld = cards.getAmount(SOCDevCardSet.OLD, cardTypes[i]);
                int numNew = cards.getAmount(SOCDevCardSet.NEW, cardTypes[i]);
                if (numOld > 0)
                    hasOldCards = true;

                for (int j = 0; j < numOld; j++)
                {
                    cardList.add(cardNames[i]);
                }
                for (int j = 0; j < numNew; j++)
                {
                    // VP cards (starting at 4) are valid immidiately
                    String prefix = (i < 4) ? "*NEW* " : "";
                    cardList.add(prefix + cardNames[i]);
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
     * @see #updateSeatLockButton()
     */
    public void renameSitButLock()
    {
        if (game.getGameState() != SOCGame.NEW)
            return;  // TODO consider IllegalStateException
        if (game.isSeatLocked(player.getPlayerNumber()))
        {
            sitBut.setLabel(UNLOCKSEAT);  // actionPerformed target becomes UNLOCKSEAT
            pname.setText(SITLOCKED);
            pname.setVisible(true);
        }
        else
        {
            sitBut.setLabel(LOCKSEAT);
        }
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
            boolean seatTaken = ! game.isSeatVacant(getPlayer().getPlayerNumber());
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
                if (! (offerIsResetMessage || offerIsDiscardMessage))
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
     */
    public void rejectOfferShowNonClient()
    {
        if (playerIsClient)
            return;
        offer.setMessage("No thanks.");
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
        client.rejectOffer(game);
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
            && ! (offerIsResetMessage || offerIsDiscardMessage))
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
    
            if (inPlay && player.isRobot())
            {
                final boolean clientAlreadySat = (null != playerInterface.getClientHand());
    
                if (clientAlreadySat)
                    sittingRobotLockBut.setVisible(hideTradeMsg);
                else if (! game.isSeatLocked(player.getPlayerNumber()))
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
                int pcurr = game.getCurrentPlayerNumber();  // current player number
                boolean pIsCurr = (pcurr == player.getPlayerNumber());  // are we current? 
                for (int i = 0; i < game.maxPlayers - 1; i++)
                {
                    boolean canSend;
                    if (pIsCurr)
                        // send to any occupied seat
                        canSend = ! game.isSeatVacant(playerSendMap[i]);
                    else
                        // send only to current player
                        canSend = (pcurr == playerSendMap[i]);
                    playerSend[i].setBoolValue(canSend);
                    playerSend[i].setEnabled(canSend);
                }
            }

            clearOfferBut.disable();
            if (! playerTradingDisabled)
            {
                offerBut.disable();
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
            offerIsDiscardMessage = false;
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
        if (offerIsDiscardMessage)
            throw new IllegalStateException("Cannot call resetmessage when discard msg");
        tradeSetMessage(message);
        offerIsResetMessage = (message != null);
    }

    /**
     * Show the "discarding..." message in the trade panel.
     * Assumes player can't be discarding and asking for board-reset at same time.
     * Normally, this will be cleared by {@link #updateValue(int)} for NUMRESOURCES,
     * because that's what the server sends all other players on discard.
     * @see #clearDiscardMsg()
     * @see #TRADEMSG_DISCARD
     * @return true if set, false if not set because was in reset-mode already.
     */
    public boolean setDiscardMsg()
    {
        if (! inPlay)
            return false;
        if (offerIsResetMessage)
            return false;
        tradeSetMessage(TRADEMSG_DISCARD);
        offerIsDiscardMessage = true;
        return true;
    }

    /**
     * Clear the "discarding..." message in the trade panel.
     * Assumes player can't be discarding and asking for board-reset at same time.
     * If wasn't in discardMessage mode, do nothing.
     * @see #setDiscardMsg()
     */
    public void clearDiscardMsg()
    {
        if (! offerIsDiscardMessage)
            return;
        tradeSetMessage(null);
        offerIsDiscardMessage = false;
    }

    /**
     * update the takeover button so that it only
     * allows takover when it's not the robot's turn
     */
    public void updateTakeOverButton()
    {
        if ((!game.isSeatLocked(player.getPlayerNumber())) &&
            (game.getCurrentPlayerNumber() != player.getPlayerNumber()))
        {
            takeOverBut.setLabel(TAKEOVER);
        }
        else
        {
            takeOverBut.setLabel("* Seat Locked *");
        }
    }

    /** Client is current player, turn has just begun.
     * Enable any previously disabled buttons.
     */
    public void updateRollButton()
    {
        rollBut.setEnabled(game.getGameState() == SOCGame.PLAY);
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
        boolean isLocked = game.isSeatLocked(player.getPlayerNumber());
        if (isLocked)
        {
            sittingRobotLockBut.setLabel(UNLOCKSEAT);
        }
        else
        {
            sittingRobotLockBut.setLabel(LOCKSEAT);
        }
        if (sitButIsLock)
        {
            boolean noPlayer = (player == null) || (player.getName() == null);
            if (isLocked)
            {
                sitBut.setLabel(UNLOCKSEAT);
                if (noPlayer)
                {
                    pname.setText(SITLOCKED);
                    pname.setVisible(true);
                }
            }
            else
            {
                sitBut.setLabel(LOCKSEAT);
                if (noPlayer)
                {
                    pname.setText(" ");
                    pname.setVisible(false);
                }
            }
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
        larmyLab.setText(haveIt ? "L. Army" : "");
    }

    /**
     * turn the "longest road" label on or off
     *
     * @param haveIt  true if this player has the longest road
     */
    protected void setLRoad(boolean haveIt)
    {
        lroadLab.setText(haveIt ? "L. Road" : "");
    }

    /**
     * update the value of a player element.
     * Call this after updating game data.
     *<P>
     * If VICTORYPOINTS is updated, and game state is over, check for winner
     * and update (player name label, victory-points tooltip, disable bank/trade btn)
     *
     * @param vt  the type of value
     */
    public void updateValue(int vt)
    {
        /**
         * We say that we're getting the total vp, but
         * for other players this will automatically get
         * the public vp because we will assume their
         * dev card vp total is zero.
         */
        switch (vt)
        {
        case VICTORYPOINTS:

            {
                int newVP = player.getTotalVP();
                vpSq.setIntValue(newVP);
                if (game.getGameState() == SOCGame.OVER)
                {
                    if (game.getPlayerWithWin() == player)
                    {
                        vpSq.setTooltipText("Winner with " + newVP + " victory points");
                        pname.setText(player.getName() + WINNER_SUFFIX);
                    }
                    if (interactive)
                        bankBut.setEnabled(false);
                    if (interactive)
                        playCardBut.setEnabled(false);
                    doneBut.setLabel(DONE_RESTART);
                    doneBut.setEnabled(true);  // In case it's another player's turn
                    doneButIsRestart = true;
                }
            }
            break;

        case LONGESTROAD:

            setLRoad(player.hasLongestRoad());

            break;

        case LARGESTARMY:

            setLArmy(player.hasLargestArmy());

            break;

        case CLAY:

            claySq.setIntValue(player.getResources().getAmount(SOCResourceConstants.CLAY));

            break;

        case ORE:

            oreSq.setIntValue(player.getResources().getAmount(SOCResourceConstants.ORE));

            break;

        case SHEEP:

            sheepSq.setIntValue(player.getResources().getAmount(SOCResourceConstants.SHEEP));

            break;

        case WHEAT:

            wheatSq.setIntValue(player.getResources().getAmount(SOCResourceConstants.WHEAT));

            break;

        case WOOD:

            woodSq.setIntValue(player.getResources().getAmount(SOCResourceConstants.WOOD));

            break;

        case NUMRESOURCES:

            resourceSq.setIntValue(player.getResources().getTotal());
            if (offerIsDiscardMessage)
                clearDiscardMsg();
            break;

        case ROADS:

            roadSq.setIntValue(player.getNumPieces(SOCPlayingPiece.ROAD));

            break;

        case SETTLEMENTS:

            settlementSq.setIntValue(player.getNumPieces(SOCPlayingPiece.SETTLEMENT));
            if (playerIsClient)
                updateResourceTradeCosts(false);

            break;

        case CITIES:

            citySq.setIntValue(player.getNumPieces(SOCPlayingPiece.CITY));

            break;

        case NUMDEVCARDS:

            developmentSq.setIntValue(player.getDevCards().getTotal());

            break;

        case NUMKNIGHTS:

            knightsSq.setIntValue(player.getNumKnights());

            break;

        case ASK_SPECIAL_BUILD:
            if (player.hasAskedSpecialBuild())
                playerInterface.print("* " + player.getName() + " wants to Special Build.");
            if (playerIsClient)
                playerInterface.getBuildingPanel().updateButtonStatus();
            break;

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
            updateValue(CLAY);
            updateValue(ORE);
            updateValue(SHEEP);
            updateValue(WHEAT);
            updateValue(WOOD);
            updateResourceTradeCosts(false);
        }
        else
        {
            updateValue(NUMRESOURCES);
        }
        updateValue(VICTORYPOINTS);
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
    public void setBounds(int x, int y, int width, int height)
    {
        super.setBounds(x, y, width, height);
        if (blankStandIn != null)
            blankStandIn.setBounds(x, y, width, height);
    }

    /**
     * Custom layout for player hand panel.
     */
    public void doLayout()
    {
        Dimension dim = getSize();
        int inset = 3;  // was 8 before 1.1.08
        int space = 2;

        FontMetrics fm = this.getFontMetrics(this.getFont());
        int lineH = ColorSquare.HEIGHT;
        int faceW = 40;
        int pnameW = dim.width - (inset + faceW + inset + inset);

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
            int stlmtsW = fm.stringWidth("Stlmts:_");     //Bug in stringWidth does not give correct size for ' '
            int knightsW = fm.stringWidth("Soldiers:") + 2;  // +2 because Label text does not start at pixel column 0

            faceImg.setBounds(inset, inset, faceW, faceW);
            pname.setBounds(inset + faceW + inset, inset, pnameW, lineH);

            //if (true) {
            if (playerIsClient)
            {
                /* This is our hand */
                //sqPanel.doLayout();

                Dimension sqpDim = sqPanel.getSize();
                int sheepW = fm.stringWidth("Sheep:_");           //Bug in stringWidth does not give correct size for ' '
                int pcW = fm.stringWidth(CARD.replace(' ','_'));  //Bug in stringWidth
                int giveW = fm.stringWidth(GIVE.replace(' ','_'));
                // int clearW = fm.stringWidth(CLEAR.replace(' ','_'));
                // int bankW = fm.stringWidth(BANK.replace(' ','_'));
                int cardsH = 5 * (lineH + space);
                int tradeH = sqpDim.height + space + (2 * (lineH + space));
                int sectionSpace = (dim.height - (inset + faceW + cardsH + tradeH + lineH + inset)) / 3;
                int tradeY = inset + faceW + sectionSpace;
                int cardsY = tradeY + tradeH + sectionSpace;

                // Always reposition everything
                startBut.setBounds(inset + faceW + inset, inset + lineH + space, dim.width - (inset + faceW + inset + inset), lineH);

                int vpW = fm.stringWidth(vpLab.getText().replace(' ','_'));  //Bug in stringWidth
                vpLab.setBounds(inset + faceW + inset, (inset + faceW) - lineH, vpW, lineH);
                vpSq.setBounds(inset + faceW + inset + vpW + space, (inset + faceW) - lineH, ColorSquare.WIDTH, ColorSquare.WIDTH);

                int topStuffW = inset + faceW + inset + vpW + space + ColorSquare.WIDTH + space;

                // always position these: though they may not be visible
                larmyLab.setBounds(topStuffW, (inset + faceW) - lineH, (dim.width - (topStuffW + inset + space)) / 2, lineH);
                lroadLab.setBounds(topStuffW + ((dim.width - (topStuffW + inset + space)) / 2) + space, (inset + faceW) - lineH, (dim.width - (topStuffW + inset + space)) / 2, lineH);

                giveLab.setBounds(inset, tradeY, giveW, lineH);
                getLab.setBounds(inset, tradeY + ColorSquareLarger.HEIGHT_L, giveW, lineH);
                sqPanel.setLocation(inset + giveW + space, tradeY);

                int tbW = ((giveW + sqpDim.width) / 2);
                int tbX = inset;
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

                if (! playerTradingDisabled)
                {
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

                knightsLab.setBounds(dim.width - inset - knightsW - ColorSquare.WIDTH - space, tradeY, knightsW, lineH);
                knightsSq.setBounds(dim.width - inset - ColorSquare.WIDTH, tradeY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                roadLab.setBounds(dim.width - inset - knightsW - ColorSquare.WIDTH - space, tradeY + lineH + space, knightsW, lineH);
                roadSq.setBounds(dim.width - inset - ColorSquare.WIDTH, tradeY + lineH + space, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                settlementLab.setBounds(dim.width - inset - knightsW - ColorSquare.WIDTH - space, tradeY + (2 * (lineH + space)), knightsW, lineH);
                settlementSq.setBounds(dim.width - inset - ColorSquare.WIDTH, tradeY + (2 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                cityLab.setBounds(dim.width - inset - knightsW - ColorSquare.WIDTH - space, tradeY + (3 * (lineH + space)), knightsW, lineH);
                citySq.setBounds(dim.width - inset - ColorSquare.WIDTH, tradeY + (3 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);

                clayLab.setBounds(inset, cardsY, sheepW, lineH);
                claySq.setBounds(inset + sheepW + space, cardsY, ColorSquare.WIDTH, ColorSquare.HEIGHT);
                oreLab.setBounds(inset, cardsY + (lineH + space), sheepW, lineH);
                oreSq.setBounds(inset + sheepW + space, cardsY + (lineH + space), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                sheepLab.setBounds(inset, cardsY + (2 * (lineH + space)), sheepW, lineH);
                sheepSq.setBounds(inset + sheepW + space, cardsY + (2 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                wheatLab.setBounds(inset, cardsY + (3 * (lineH + space)), sheepW, lineH);
                wheatSq.setBounds(inset + sheepW + space, cardsY + (3 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                woodLab.setBounds(inset, cardsY + (4 * (lineH + space)), sheepW, lineH);
                woodSq.setBounds(inset + sheepW + space, cardsY + (4 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);

                int clW = dim.width - (inset + sheepW + space + ColorSquare.WIDTH + (4 * space) + inset);
                int clX = inset + sheepW + space + ColorSquare.WIDTH + (4 * space);
                cardList.setBounds(clX, cardsY, clW, (4 * (lineH + space)) - 2);
                playCardBut.setBounds(((clW - pcW) / 2) + clX, cardsY + (4 * (lineH + space)), pcW, lineH);

                int bbW = 50;
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
                int balloonH = dim.height - (inset + (4 * (lineH + space)) + inset);  // offer-message panel
                int dcardsW = fm.stringWidth("Dev._Cards:_");                //Bug in stringWidth does not give correct size for ' '
                int vpW = fm.stringWidth(vpLab.getText().replace(' ','_'));  //Bug in stringWidth

                if (player.isRobot())
                {
                    if (game.getPlayer(client.getNickname()) == null)
                    {
                        takeOverBut.setBounds(10, (inset + balloonH) - 10, dim.width - 20, 20);
                    }
                    else if (sittingRobotLockBut.isVisible())
                    {
                        //seatLockBut.setBounds(10, inset+balloonH-10, dim.width-20, 20);
                        sittingRobotLockBut.setBounds(inset + dcardsW + space + ColorSquare.WIDTH + space, inset + balloonH + (lineH + space) + (lineH / 2), (dim.width - (2 * (inset + ColorSquare.WIDTH + (2 * space))) - stlmtsW - dcardsW), 2 * (lineH + space));
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

                vpLab.setBounds(inset + faceW + inset, (inset + faceW) - lineH, vpW, lineH);
                vpSq.setBounds(inset + faceW + inset + vpW + space, (inset + faceW) - lineH, ColorSquare.WIDTH, ColorSquare.HEIGHT);

                int topStuffW = inset + faceW + inset + vpW + space + ColorSquare.WIDTH + space;

                // always position these: though they may not be visible
                larmyLab.setBounds(topStuffW, (inset + faceW) - lineH, (dim.width - (topStuffW + inset + space)) / 2, lineH);
                lroadLab.setBounds(topStuffW + ((dim.width - (topStuffW + inset + space)) / 2) + space, (inset + faceW) - lineH, (dim.width - (topStuffW + inset + space)) / 2, lineH);

                resourceLab.setBounds(inset, inset + balloonH + (2 * (lineH + space)), dcardsW, lineH);
                resourceSq.setBounds(inset + dcardsW + space, inset + balloonH + (2 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                developmentLab.setBounds(inset, inset + balloonH + (3 * (lineH + space)), dcardsW, lineH);
                developmentSq.setBounds(inset + dcardsW + space, inset + balloonH + (3 * (lineH + space)), ColorSquare.WIDTH, ColorSquare.HEIGHT);
                knightsLab.setBounds(inset, inset + balloonH + (lineH + space), dcardsW, lineH);
                knightsSq.setBounds(inset + dcardsW + space, inset + balloonH + (lineH + space), ColorSquare.WIDTH, ColorSquare.HEIGHT);

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
                    ? "For 1 "
                    : ("Trade " + numFrom + " " + SOCResourceConstants.resName(typeFrom) + " for 1 "))
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
                setLabel("For 1 " + SOCResourceConstants.resName(tradeTo));
            else
                setLabel("Trade " + numFrom + " " + SOCResourceConstants.resName(tradeFrom)
                        + " for 1 " + SOCResourceConstants.resName(tradeTo));
        }

        /**
         * Enable or disable this menu item.
         * If from-to resources are same, always disabled.
         */
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
                hp.getPlayerInterface().print("* You cannot trade at this time.\n");
                return;
            }

            int[] give = new int[5];
            int[] get = new int[5];
            give[tradeFrom - 1] = tradeNum;
            get[tradeTo - 1] = 1;

            SOCResourceSet giveSet = new SOCResourceSet(give);
            SOCResourceSet getSet = new SOCResourceSet(get);
            hp.getClient().bankTrade(game, giveSet, getSet);
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
          super(hp, "Bank/Port Trade");
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
            super(hp, "Trade Port");
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
              setLabel("Trade " + costFrom + " "
                  + SOCResourceConstants.resName(typeFrom) + " ");

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
        public void setEnabledIfCanTrade(boolean itemsOnly)
        {
            SOCPlayer p = hpan.getPlayer();
            boolean canTrade = (hpan.getGame().getGameState() == SOCGame.PLAY1)
                && (hpan.getGame().getCurrentPlayerNumber() == p.getPlayerNumber())
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
