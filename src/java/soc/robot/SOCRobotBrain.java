/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2012 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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
package soc.robot;

import soc.client.SOCDisplaylessPlayerClient;
import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCTradeOffer;
import soc.game.SOCVillage;

import soc.message.SOCAcceptOffer;
import soc.message.SOCCancelBuildRequest;
import soc.message.SOCChoosePlayer;
import soc.message.SOCChoosePlayerRequest;
import soc.message.SOCClearOffer;
import soc.message.SOCDevCard;
import soc.message.SOCDevCardCount;
import soc.message.SOCDiceResult;
import soc.message.SOCDiscardRequest;
import soc.message.SOCFirstPlayer;
import soc.message.SOCGameState;
import soc.message.SOCGameTextMsg;
import soc.message.SOCMakeOffer;
import soc.message.SOCMessage;
import soc.message.SOCMovePiece;
import soc.message.SOCMoveRobber;
import soc.message.SOCPickResourcesRequest;
import soc.message.SOCPlayerElement;
import soc.message.SOCPutPiece;
import soc.message.SOCRejectOffer;
import soc.message.SOCResourceCount;
import soc.message.SOCSetPlayedDevCard;
import soc.message.SOCSetTurn;
import soc.message.SOCSitDown;  // for javadoc
import soc.message.SOCTurn;

import soc.server.SOCServer;

import soc.util.CappedQueue;
import soc.util.DebugRecorder;
import soc.util.Queue;
import soc.util.SOCRobotParameters;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Stack;
import java.util.Vector;


/**
 * AI for playing Settlers of Catan.
 * Represents a robot player within 1 game.
 * The bot is a separate thread, so everything happens in {@link #run()} or a method called from there.
 *<P>
 * Some robot behaviors are altered by the {@link SOCRobotParameters} passed into our constructor.
 * Some decision-making code is in the {@link OpeningBuildStrategy},
 * {@link RobberStrategy}, {@link MonopolyStrategy}, etc classes.
 * Data and predictions about the other players in the game is in
 * {@link SOCPlayerTracker}.  If we're trading with other players for
 * resources, some details of that are in {@link SOCRobotNegotiator}.
 * All these, and data on the game and players, are initialized in
 * {@link #setOurPlayerData()}.
 *<P>
 * At the start of each player's turn, {@link #buildingPlan} and most other state fields are cleared
 * (search {@link #run()} for <tt>mesType == SOCMessage.TURN</tt>).
 * The plan for what to build next is decided in {@link SOCRobotDM#planStuff(int)}
 * (called from {@link #planBuilding()} and some other places) which updates {@link #buildingPlan}.
 * That plan is executed in {@link #buildOrGetResourceByTradeOrCard()}.
 *<P>
 * Current status and the next expected action are tracked by the "waitingFor" and "expect" flag fields.
 * If we've sent the server an action and we're waiting for the result, {@link #waitingForGameState} is true
 * along with one other "expect" flag, such as {@link #expectPLACING_ROBBER}.
 * All these fields can be output for inspection by calling {@link #debugPrintBrainStatus()}.
 *
 * @author Robert S Thomas
 */
public class SOCRobotBrain extends Thread
{
    /**
     * The robot parameters
     */
    SOCRobotParameters robotParameters;

    /**
     * Flag for whether or not we're alive
     */
    protected boolean alive;

    /**
     * Flag for whether or not it is our turn
     */
    protected boolean ourTurn;

    /**
     * Timer for turn taking
     */
    protected int turnTime;

    /**
     * {@link #pause(int) Pause} for less time;
     * speeds up response in 6-player games.
     * @since 1.1.09
     */
    private boolean pauseFaster;

    /**
     * Our current state
     */
    protected int curState;

    /**
     * Random number generator
     */
    protected Random rand = new Random();

    /**
     * The client we are hooked up to
     */
    protected SOCRobotClient client;

    /**
     * The game we are playing
     */
    protected SOCGame game;

    /**
     * The {@link #game} we're playing is on the 6-player board.
     * @since 1.1.08
     */
    final private boolean gameIs6Player;

    /**
     * Our player data
     * Set in {@link #setOurPlayerData()}
     */
    protected SOCPlayer ourPlayerData;

    /**
     * Our player number; set in {@link #setOurPlayerData()}.
     * @since 2.0.00
     */
    private int ourPlayerNumber;

    /**
     * Dummy player for cancelling bad placements
     */
    protected SOCPlayer dummyCancelPlayerData;

    /**
     * The queue of game messages; contents are {@link SOCMessage}.
     */
    protected CappedQueue<SOCMessage> gameEventQ;

    /**
     * The game messages received this turn / previous turn, for debugging.
     * @since 1.1.13
     */
    private Vector<SOCMessage> turnEventsCurrent, turnEventsPrev;

    /**
     * A counter used to measure passage of time.
     * Incremented each second, when the server sends {@link SOCMessage#TIMINGPING}.
     * When we decide to take an action, resets to 0.
     * If counter gets too high, we assume a bug and leave the game (<tt>{@link #alive} = false</tt>).
     */
    protected int counter;

    /**
     * During this turn, which is another player's turn,
     * have we yet decided whether to do the Special Building phase
     * (for the 6-player board)?
     * @since 1.1.08
     */
    private boolean decidedIfSpecialBuild;

    /**
     * true when we're waiting for our requested Special Building phase
     * (for the 6-player board).
     * @since 1.1.08
     */
    private boolean waitingForSpecialBuild;

    /**
     * This is what we want to build
     */
    protected SOCPlayingPiece whatWeWantToBuild;

    /**
     * This is our current building plan, a stack of {@link SOCPossiblePiece}.
     *<P>
     * Cleared at the start of each player's turn, and a few other places
     * if certain conditions arise.  Set in {@link #planBuilding()}.
     * When making a {@link #buildingPlan}, be sure to also set
     * {@link #negotiator}'s target piece.
     *<P>
     * {@link SOCRobotDM#buildingPlan} is the same Stack.
     */
    protected Stack<SOCPossiblePiece> buildingPlan;

    /**
     * This is what we tried building this turn,
     * but the server said it was an illegal move
     * (due to a bug in our robot).
     *
     * @see #whatWeWantToBuild
     * @see #failedBuildingAttempts
     */
    protected SOCPlayingPiece whatWeFailedToBuild;

    /**
     * Track how many illegal placement requests we've
     * made this turn.  Avoid infinite turn length, by
     * preventing robot from alternately choosing two
     * wrong things when the server denies a bad build.
     *
     * @see #whatWeFailedToBuild
     * @see #MAX_DENIED_BUILDING_PER_TURN
     */
    protected int failedBuildingAttempts;

    /**
     * If, during a turn, we make this many illegal build
     * requests that the server denies, stop trying.
     *
     * @see #failedBuildingAttempts
     */
    public static int MAX_DENIED_BUILDING_PER_TURN = 3;

    /**
     * these are the two resources that we want
     * when we play a discovery dev card
     */
    protected SOCResourceSet resourceChoices;

    /**
     * our player tracker
     */
    protected SOCPlayerTracker ourPlayerTracker;

    /**
     * trackers for all players (one per player, including this robot)
     */
    protected HashMap<Integer, SOCPlayerTracker> playerTrackers;

    /**
     * the thing that determines what we want to build next
     */
    protected SOCRobotDM decisionMaker;

    /**
     * The data and code that determines how we negotiate.
     * {@link SOCRobotNegotiator#setTargetPiece(int, SOCPossiblePiece)}
     * is set when {@link #buildingPlan} is updated.
     * @see #tradeToTarget2(SOCResourceSet)
     * @see #makeOffer(SOCPossiblePiece)
     * @see #considerOffer(SOCTradeOffer)
     * @see #tradeStopWaitingClearOffer()
     */
    protected SOCRobotNegotiator negotiator;

    // If any new expect or waitingFor fields are added,
    // please update debugPrintBrainStatus().

    /**
     * true if we're expecting the START1A state
     */
    protected boolean expectSTART1A;

    /**
     * true if we're expecting the START1B state
     */
    protected boolean expectSTART1B;

    /**
     * true if we're expecting the START2A state
     */
    protected boolean expectSTART2A;

    /**
     * true if we're expecting the START2B state
     */
    protected boolean expectSTART2B;

    /**
     * true if we're expecting the {@link SOCGame#START3A START3A} state.
     * @since 2.0.00
     */
    protected boolean expectSTART3A;

    /**
     * true if we're expecting the {@link SOCGame#START3B START3B} state.
     * @since 2.0.00
     */
    protected boolean expectSTART3B;

    /**
     * true if we're expecting the PLAY state
     */
    protected boolean expectPLAY;

    /**
     * true if we're expecting the PLAY1 state
     */
    protected boolean expectPLAY1;

    /**
     * true if we're expecting the PLACING_ROAD state
     */
    protected boolean expectPLACING_ROAD;

    /**
     * true if we're expecting the PLACING_SETTLEMENT state
     */
    protected boolean expectPLACING_SETTLEMENT;

    /**
     * true if we're expecting the PLACING_CITY state
     */
    protected boolean expectPLACING_CITY;

    /**
     * true if we're expecting the PLACING_SHIP game state
     * @since 2.0.00
     */
    protected boolean expectPLACING_SHIP;

    /**
     * true if we're expecting the PLACING_ROBBER state
     */
    protected boolean expectPLACING_ROBBER;

    /**
     * true if we're expecting the PLACING_FREE_ROAD1 state
     */
    protected boolean expectPLACING_FREE_ROAD1;

    /**
     * true if we're expecting the PLACING_FREE_ROAD2 state
     */
    protected boolean expectPLACING_FREE_ROAD2;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1A game state
     */
    protected boolean expectPUTPIECE_FROM_START1A;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1B game state
     */
    protected boolean expectPUTPIECE_FROM_START1B;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1A game state
     */
    protected boolean expectPUTPIECE_FROM_START2A;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1A game state
     */
    protected boolean expectPUTPIECE_FROM_START2B;

    /**
     * true if were expecting a PUTPIECE message after
     * a {@link SOCGame#START3A START3A} game state.
     * @since 2.0.00
     */
    protected boolean expectPUTPIECE_FROM_START3A;

    /**
     * true if were expecting a PUTPIECE message after
     * a {@link SOCGame#START3B START3B} game state.
     * @since 2.0.00
     */
    protected boolean expectPUTPIECE_FROM_START3B;

    /**
     * true if we're expecting a DICERESULT message
     */
    protected boolean expectDICERESULT;

    /**
     * true if we're expecting a DISCARDREQUEST message
     */
    protected boolean expectDISCARD;

    /**
     * true if we're expecting to have to move the robber
     */
    protected boolean expectMOVEROBBER;

    /**
     * true if we're expecting to pick two resources
     */
    protected boolean expectWAITING_FOR_DISCOVERY;

    /**
     * true if we're expecting to pick a monopoly
     */
    protected boolean expectWAITING_FOR_MONOPOLY;

    // If any new expect or waitingFor fields are added,
    // please update debugPrintBrainStatus().

    /**
     * true if we're waiting for a GAMESTATE message from the server.
     * This is set after a robot action or requested action is sent to server,
     * or just before ending our turn (which also sets {@link #waitingForOurTurn} == true).
     *<P>
     * For example, when playing a {@link SOCDevCard}, set true and also set
     * an "expect" flag ({@link #expectPLACING_ROBBER}, {@link #expectWAITING_FOR_DISCOVERY}, etc).
     * @see #rejectedPlayDevCardType
     */
    protected boolean waitingForGameState;

    /**
     * true if we're waiting for a {@link SOCTurn TURN} message from the server
     * when it's our turn
     */
    protected boolean waitingForOurTurn;

    /**
     * true when we're waiting for the results of a trade
     */
    protected boolean waitingForTradeMsg;

    /**
     * true when we're waiting to receive a dev card
     */
    protected boolean waitingForDevCard;

    /**
     * True when the robber will move because a seven was rolled.
     * Used to help bot remember why the robber is moving (Knight dev card, or 7).
     * Set true when {@link SOCMessage#DICERESULT} received.
     * Read in gamestate {@link SOCGame#PLACING_ROBBER PLACING_ROBBER}.
     */
    protected boolean moveRobberOnSeven;

    /**
     * true if we're waiting for a response to our trade message
     */
    protected boolean waitingForTradeResponse;

    // If any new expect or waitingFor fields are added,
    // please update debugPrintBrainStatus().

    /**
     * true if we're done trading
     */
    protected boolean doneTrading;

    /**
     * true if the player with that player number has rejected our offer
     */
    protected boolean[] offerRejections;

    /**
     * If set, the server rejected our play of this dev card type this turn
     * (such as {@link SOCDevCardConstants#KNIGHT}) because of a bug in our
     * robot; should not attempt to play the same type again this turn.
     * Otherwise -1.
     * @since 1.1.17
     */
    private int rejectedPlayDevCardType;

    /**
     * the game state before the current one
     */
    protected int oldGameState;

    /**
     * During START states, coordinate of our most recently placed road or settlement.
     * Used to avoid repeats in {@link #cancelWrongPiecePlacement(SOCCancelBuildRequest)}.
     * @since 1.1.09
     */
    private int lastStartingPieceCoord;

    /**
     * During START1B and START2B states, coordinate of the potential settlement node
     * towards which we're building, as calculated by {@link #placeInitRoad()}.
     * Used to avoid repeats in {@link #cancelWrongPiecePlacementLocal(SOCPlayingPiece)}.
     * @since 1.1.09
     */
    private int lastStartingRoadTowardsNode;

    /**
     * Strategy to plan and build initial settlements and roads.
     * Set in {@link #setOurPlayerData()}.
     * @since 2.0.00
     */
    private OpeningBuildStrategy openingBuildStrategy;

    /**
     * Strategy to choose whether to monopolize, and which resource.
     * Set in {@link #setOurPlayerData()}.
     * @since 2.0.00
     */
    private MonopolyStrategy monopolyStrategy;

    // RobberStrategy is used but has no state, its methods are static.

    /**
     * a thread that sends ping messages to this one
     */
    protected SOCRobotPinger pinger;

    /**
     * an object for recording debug information that can
     * be accessed interactively
     */
    protected DebugRecorder[] dRecorder;

    /**
     * keeps track of which dRecorder is current
     */
    protected int currentDRecorder;

    /**
     * keeps track of the last thing we bought for debugging purposes
     */
    protected SOCPossiblePiece lastMove;

    /**
     * keeps track of the last thing we wanted for debugging purposes
     */
    protected SOCPossiblePiece lastTarget;

    /**
     * Create a robot brain to play a game.
     *<P>
     * Depending on {@link SOCGame#getGameOptions() game options},
     * constructor might copy and alter the robot parameters
     * (for example, to clear {@link SOCRobotParameters#getTradeFlag()}).
     *<P>
     * Please call {@link #setOurPlayerData()} before using this brain or starting its thread.
     *
     * @param rc  the robot client
     * @param params  the robot parameters
     * @param ga  the game we're playing
     * @param mq  the message queue
     */
    public SOCRobotBrain(SOCRobotClient rc, SOCRobotParameters params, SOCGame ga, CappedQueue<SOCMessage> mq)
    {
        client = rc;
        robotParameters = params.copyIfOptionChanged(ga.getGameOptions());
        game = ga;
        gameIs6Player = (ga.maxPlayers > 4);
        pauseFaster = gameIs6Player;
        gameEventQ = mq;
        turnEventsCurrent = new Vector<SOCMessage>();
        turnEventsPrev = new Vector<SOCMessage>();
        alive = true;
        counter = 0;
        expectSTART1A = true;
        expectSTART1B = false;
        expectSTART2A = false;
        expectSTART2B = false;
        expectPLAY = false;
        expectPLAY1 = false;
        expectPLACING_ROAD = false;
        expectPLACING_SETTLEMENT = false;
        expectPLACING_CITY = false;
        expectPLACING_SHIP = false;
        expectPLACING_ROBBER = false;
        expectPLACING_FREE_ROAD1 = false;
        expectPLACING_FREE_ROAD2 = false;
        expectPUTPIECE_FROM_START1A = false;
        expectPUTPIECE_FROM_START1B = false;
        expectPUTPIECE_FROM_START2A = false;
        expectPUTPIECE_FROM_START2B = false;
        expectDICERESULT = false;
        expectDISCARD = false;
        expectMOVEROBBER = false;
        expectWAITING_FOR_DISCOVERY = false;
        expectWAITING_FOR_MONOPOLY = false;
        ourTurn = false;
        oldGameState = game.getGameState();
        waitingForGameState = false;
        waitingForOurTurn = false;
        waitingForTradeMsg = false;
        waitingForDevCard = false;
        waitingForSpecialBuild = false;
        decidedIfSpecialBuild = false;
        moveRobberOnSeven = false;
        waitingForTradeResponse = false;
        doneTrading = false;
        offerRejections = new boolean[game.maxPlayers];
        for (int i = 0; i < game.maxPlayers; i++)
        {
            offerRejections[i] = false;
        }

        buildingPlan = new Stack<SOCPossiblePiece>();
        resourceChoices = new SOCResourceSet();
        resourceChoices.add(2, SOCResourceConstants.CLAY);
        pinger = new SOCRobotPinger(gameEventQ, game.getName(), client.getNickname() + "-" + game.getName());
        dRecorder = new DebugRecorder[2];
        dRecorder[0] = new DebugRecorder();
        dRecorder[1] = new DebugRecorder();
        currentDRecorder = 0;

        // Strategy fields will be set in setOurPlayerData();
        // we don't have the data yet.
    }

    /**
     * @return the robot parameters
     */
    public SOCRobotParameters getRobotParameters()
    {
        return robotParameters;
    }

    /**
     * @return the player client
     */
    public SOCRobotClient getClient()
    {
        return client;
    }

    /**
     * @return the player trackers (one per player, including this robot)
     */
    public HashMap<Integer, SOCPlayerTracker> getPlayerTrackers()
    {
        return playerTrackers;
    }

    /**
     * @return our player tracker
     */
    public SOCPlayerTracker getOurPlayerTracker()
    {
        return ourPlayerTracker;
    }

    /**
     * A player has sat down and been added to the game,
     * during game formation. Create a PlayerTracker for them.
     *<p>
     * Called when SITDOWN received from server; one SITDOWN is
     * sent for every player, and our robot player might not be the
     * first or last SITDOWN.
     *<p>
     * Since our playerTrackers are initialized when our robot's
     * SITDOWN is received (robotclient calls {@link #setOurPlayerData()}),
     * and seats may be vacant at that time (because SITDOWN not yet
     * received for those seats), we must add a PlayerTracker for
     * each SITDOWN received after our player's.
     *
     * @param pn Player number
     */
    public void addPlayerTracker(int pn)
    {
        if (null == playerTrackers)
        {
            // SITDOWN hasn't been sent for our own player yet.
            // When it is, playerTrackers will be initialized for
            // each non-vacant player, including pn.

            return;
        }
        if (null == playerTrackers.get(new Integer(pn)))
        {
            SOCPlayerTracker tracker = new SOCPlayerTracker(game.getPlayer(pn), this);
            playerTrackers.put(new Integer(pn), tracker);
        }
    }

    /**
     * @return the game data
     */
    public SOCGame getGame()
    {
        return game;
    }

    /**
     * @return our player data
     */
    public SOCPlayer getOurPlayerData()
    {
        return ourPlayerData;
    }

    /**
     * @return the building plan, a stack of {@link SOCPossiblePiece}
     */
    public Stack<SOCPossiblePiece> getBuildingPlan()
    {
        return buildingPlan;
    }

    /**
     * @return the decision maker
     */
    public SOCRobotDM getDecisionMaker()
    {
        return decisionMaker;
    }

    /**
     * turns the debug recorders on
     */
    public void turnOnDRecorder()
    {
        dRecorder[0].turnOn();
        dRecorder[1].turnOn();
    }

    /**
     * turns the debug recorders off
     */
    public void turnOffDRecorder()
    {
        dRecorder[0].turnOff();
        dRecorder[1].turnOff();
    }

    /**
     * @return the debug recorder
     */
    public DebugRecorder getDRecorder()
    {
        return dRecorder[currentDRecorder];
    }

    /**
     * @return the old debug recorder
     */
    public DebugRecorder getOldDRecorder()
    {
        return dRecorder[(currentDRecorder + 1) % 2];
    }

    /**
     * @return the last move we made
     */
    public SOCPossiblePiece getLastMove()
    {
        return lastMove;
    }

    /**
     * @return our last target piece
     */
    public SOCPossiblePiece getLastTarget()
    {
        return lastTarget;
    }

    /**
     * When we join a game and sit down to begin play,
     * find our player data using our nickname.
     * Called from {@link SOCRobotClient} when the
     * server sends a {@link SOCSitDown} message.
     * Initializes our game and player data,
     * {@link SOCRobotDM}, {@link SOCRobotNegotiator},
     * strategy fields, {@link SOCPlayerTracker}s, etc.
     */
    public void setOurPlayerData()
    {
        ourPlayerData = game.getPlayer(client.getNickname());
        ourPlayerTracker = new SOCPlayerTracker(ourPlayerData, this);
        ourPlayerNumber = ourPlayerData.getPlayerNumber();
        playerTrackers = new HashMap<Integer, SOCPlayerTracker>();
        playerTrackers.put(new Integer(ourPlayerNumber), ourPlayerTracker);

        for (int pn = 0; pn < game.maxPlayers; pn++)
        {
            if ((pn != ourPlayerNumber) && ! game.isSeatVacant(pn))
            {
                SOCPlayerTracker tracker = new SOCPlayerTracker(game.getPlayer(pn), this);
                playerTrackers.put(new Integer(pn), tracker);
            }
        }

        decisionMaker = new SOCRobotDM(this);
        negotiator = new SOCRobotNegotiator(this);
        openingBuildStrategy = new OpeningBuildStrategy(game, ourPlayerData);
        monopolyStrategy = new MonopolyStrategy(game, ourPlayerData);

        dummyCancelPlayerData = new SOCPlayer(-2, game);

        // Verify expected face (fast or smart robot)
        int faceId;
        switch (getRobotParameters().getStrategyType())
        {
        case SOCRobotDM.SMART_STRATEGY:
            faceId = -1;  // smarter robot face
            break;

        default:
            faceId = 0;   // default robot face
        }
        if (ourPlayerData.getFaceId() != faceId)
        {
            ourPlayerData.setFaceId(faceId);
            // robotclient will handle sending it to server
        }
    }

    /**
     * Print brain variables and status for this game to {@link System#err}.
     * Includes all of the expect and waitingFor fields (<tt>expectPLAY</tt>,
     * <tt>waitingForGameState</tt>, etc.)
     * Also prints the game state, and the messages received by this brain
     * during the previous and current turns.
     * @since 1.1.13
     */
    public void debugPrintBrainStatus()
    {
        if ((ourPlayerData == null) || (game == null))
        {
            System.err.println("Robot internal state: Cannot print: null game or player");
            return;
        }

        System.err.println("Robot internal state: "
                + ((client != null) ? client.getNickname() : ourPlayerData.getName())
                + " in game " + game.getName()
                + ": gs=" + game.getGameState());
        if (game.getGameState() == SOCGame.WAITING_FOR_DISCARDS)
            System.err.println("  bot card count = " + ourPlayerData.getResources().getTotal());
        if (rejectedPlayDevCardType != -1)
            System.err.println("  rejectedPlayDevCardType = " + rejectedPlayDevCardType);
        final String[] s = {
            "ourTurn", "doneTrading",
            "waitingForGameState", "waitingForOurTurn", "waitingForTradeMsg", "waitingForDevCard", "waitingForTradeResponse",
            "moveRobberOnSeven", "expectSTART1A", "expectSTART1B", "expectSTART2A", "expectSTART2B", "expectSTART3A", "expectSTART3B",
            "expectPLAY", "expectPLAY1", "expectPLACING_ROAD", "expectPLACING_SETTLEMENT", "expectPLACING_CITY", "expectPLACING_SHIP",
            "expectPLACING_ROBBER", "expectPLACING_FREE_ROAD1", "expectPLACING_FREE_ROAD2",
            "expectPUTPIECE_FROM_START1A", "expectPUTPIECE_FROM_START1B", "expectPUTPIECE_FROM_START2A", "expectPUTPIECE_FROM_START2B",
            "expectPUTPIECE_FROM_START3A", "expectPUTPIECE_FROM_START3B",
            "expectDICERESULT", "expectDISCARD", "expectMOVEROBBER", "expectWAITING_FOR_DISCOVERY", "expectWAITING_FOR_MONOPOLY"
        };
        final boolean[] b = {
            ourTurn, doneTrading,
            waitingForGameState, waitingForOurTurn, waitingForTradeMsg, waitingForDevCard, waitingForTradeResponse,
            moveRobberOnSeven, expectSTART1A, expectSTART1B, expectSTART2A, expectSTART2B, expectSTART3A, expectSTART3B,
            expectPLAY, expectPLAY1, expectPLACING_ROAD, expectPLACING_SETTLEMENT, expectPLACING_CITY, expectPLACING_SHIP,
            expectPLACING_ROBBER, expectPLACING_FREE_ROAD1, expectPLACING_FREE_ROAD2,
            expectPUTPIECE_FROM_START1A, expectPUTPIECE_FROM_START1B, expectPUTPIECE_FROM_START2A, expectPUTPIECE_FROM_START2B,
            expectPUTPIECE_FROM_START3A, expectPUTPIECE_FROM_START3B,
            expectDICERESULT, expectDISCARD, expectMOVEROBBER, expectWAITING_FOR_DISCOVERY, expectWAITING_FOR_MONOPOLY
        };
        if (s.length != b.length)
        {
            System.err.println("L745: Internal error: array length");
            return;
        }
        int slen = 0;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length; ++i)
        {
            if ((slen + s[i].length() + 8) > 79)
            {
                System.err.println(sb.toString());
                slen = 0;
                sb.delete(0, sb.length());
            }
            sb.append("  ");
            sb.append(s[i]);
            sb.append(": ");
            if (b[i])
                sb.append("TRUE");
            else
                sb.append("false");
            slen = sb.length();
        }
        if (slen > 0)
            System.err.println(sb.toString());

        debugPrintTurnMessages(turnEventsPrev, "previous");
        debugPrintTurnMessages(turnEventsCurrent, "current");
    }

    /**
     * Print the contents of this Vector to <tt>System.err</tt>.
     * One element per line, indented by <tt>\t</tt>.
     * Headed by a line formatted as one of:
     *<BR>  Current turn: No messages received.
     *<BR>  Current turn: 5 messages received:
     * @param msgV  Vector of {@link SOCMessage}s from server
     * @param msgDesc  Short description of the vector, like 'previous' or 'current'
     * @since 1.1.13
     */
    private static void debugPrintTurnMessages(Vector<?> msgV, final String msgDesc)
    {
        System.err.print("  " + msgDesc);
        final int n = msgV.size();
        if (n == 0)
        {
            System.err.println(" turn: No messages received.");
        } else {
            System.err.println(" turn: " + n + " messages received:");
            for (int i = 0; i < n; ++i)
            {
                System.err.print("\t");
                System.err.println(msgV.elementAt(i));
            }
        }
    }

    /**
     * Here is the run method.  Just keep receiving game events
     * through {@link #gameEventQ} and deal with each one.
     * Remember that we're sent a {@link SOCTimingPing} event once per second,
     * incrementing {@link #counter}.  That allows the bot to wait a certain
     * time for other players before it decides whether to do something.
     *<P>
     * Nearly all bot actions start in this method; the overview of bot structures
     * is in the {@link SOCRobotBrain class javadoc} for prominence.
     * See comments within <tt>run()</tt> for minor details.
     */
    @Override
    public void run()
    {
        // Thread name for debug
        try
        {
            Thread.currentThread().setName("robotBrain-" + client.getNickname() + "-" + game.getName());
        }
        catch (Throwable th) {}

        if (pinger != null)
        {
            pinger.start();

            try
            {
                //
                // Along with actual game events, the pinger sends a TIMINGPING message
                // once per second, to aid the robot's timekeeping counter.
                //

                while (alive)
                {
                    SOCMessage mes;

                    //if (!gameEventQ.empty()) {
                    mes = gameEventQ.get();  // Sleeps until message received

                    //} else {
                    //mes = null;
                    //}
                    final int mesType;

                    if (mes != null)
                    {
                        mesType = mes.getType();
                        if ((mesType != SOCMessage.TIMINGPING) && (mesType != SOCMessage.GAMETEXTMSG))
                            turnEventsCurrent.addElement(mes);
                        if (D.ebugOn)
                            D.ebugPrintln("mes - " + mes);

                        // Debug aid: when looking at message contents: avoid pings:
                        // check here for (mesType != SOCMessage.TIMINGPING).
                    }
                    else
                    {
                        mesType = -1;
                    }

                    if (waitingForTradeMsg && (counter > 10))
                    {
                        waitingForTradeMsg = false;
                        counter = 0;
                    }

                    if (waitingForTradeResponse && (counter > 100))
                    {
                        // Remember other players' responses, call client.clearOffer,
                        // clear waitingForTradeResponse and counter.
                        tradeStopWaitingClearOffer();
                    }

                    if (waitingForGameState && (counter > 10000))
                    {
                        //D.ebugPrintln("counter = "+counter);
                        //D.ebugPrintln("RESEND");
                        counter = 0;
                        client.resend();
                    }

                    if (mesType == SOCMessage.GAMESTATE)
                    {
                        waitingForGameState = false;
                        oldGameState = game.getGameState();
                        game.setGameState(((SOCGameState) mes).getState());
                    }

                    else if (mesType == SOCMessage.FIRSTPLAYER)
                    {
                        game.setFirstPlayer(((SOCFirstPlayer) mes).getPlayerNumber());
                    }

                    else if (mesType == SOCMessage.SETTURN)
                    {
                        game.setCurrentPlayerNumber(((SOCSetTurn) mes).getPlayerNumber());
                    }

                    else if (mesType == SOCMessage.TURN)
                    {
                        // Start of a new player's turn.
                        // Update game and reset most of our state fields.

                        game.setCurrentPlayerNumber(((SOCTurn) mes).getPlayerNumber());
                        game.updateAtTurn();

                        //
                        // remove any expected states
                        //
                        expectPLAY = false;
                        expectPLAY1 = false;
                        expectPLACING_ROAD = false;
                        expectPLACING_SETTLEMENT = false;
                        expectPLACING_CITY = false;
                        expectPLACING_SHIP = false;
                        expectPLACING_ROBBER = false;
                        expectPLACING_FREE_ROAD1 = false;
                        expectPLACING_FREE_ROAD2 = false;
                        expectDICERESULT = false;
                        expectDISCARD = false;
                        expectMOVEROBBER = false;
                        expectWAITING_FOR_DISCOVERY = false;
                        expectWAITING_FOR_MONOPOLY = false;

                        //
                        // reset the selling flags and offers history
                        //
                        if (robotParameters.getTradeFlag() == 1)
                        {
                            doneTrading = false;
                        }
                        else
                        {
                            doneTrading = true;
                        }

                        waitingForTradeMsg = false;
                        waitingForTradeResponse = false;
                        negotiator.resetIsSelling();
                        negotiator.resetOffersMade();

                        //
                        // check or reset any special-building-phase decisions
                        //
                        decidedIfSpecialBuild = false;
                        if (game.getGameState() == SOCGame.SPECIAL_BUILDING)
                        {
                            if (waitingForSpecialBuild && ! buildingPlan.isEmpty())
                            {
                                // Keep the building plan.
                                // Will ask during loop body to build.
                            } else {
                                // We have no plan, but will call planBuilding()
                                // during the loop body.  If buildingPlan still empty,
                                // bottom of loop will end our Special Building turn,
                                // just as it would in gamestate PLAY1.  Otherwise,
                                // will ask to build after planBuilding.
                            }
                        } else {
                            //
                            // reset any plans we had
                            //
                            buildingPlan.clear();
                        }
                        negotiator.resetTargetPieces();

                        //
                        // swap the message-history queues
                        //
                        {
                            Vector<SOCMessage> tmp = turnEventsPrev;
                            turnEventsPrev = turnEventsCurrent;
                            tmp.clear();
                            turnEventsCurrent = tmp;
                        }
                    }

                    if (game.getCurrentPlayerNumber() == ourPlayerNumber)
                    {
                        ourTurn = true;
                        waitingForSpecialBuild = false;
                    }
                    else
                    {
                        ourTurn = false;
                    }

                    if ((mesType == SOCMessage.TURN) && (ourTurn))
                    {
                        waitingForOurTurn = false;

                        // Clear some per-turn variables.
                        // For others, find the code which calls game.updateAtTurn().
                        whatWeFailedToBuild = null;
                        failedBuildingAttempts = 0;
                        rejectedPlayDevCardType = -1;
                    }

                    /**
                     * Handle some message types early.
                     */
                    switch (mesType)
                    {
                    case SOCMessage.PLAYERELEMENT:
                        {
                        handlePLAYERELEMENT((SOCPlayerElement) mes);

                        // If this during the PLAY state, also updates the
                        // negotiator's is-selling flags.

                        // If our player is losing a resource needed for the buildingPlan,
                        // clear the plan if this is for the Special Building Phase (on the 6-player board).
                        // In normal game play, we clear the building plan at the start of each turn.
                        }
                        break;

                    case SOCMessage.RESOURCECOUNT:
                        {
                        SOCPlayer pl = game.getPlayer(((SOCResourceCount) mes).getPlayerNumber());

                        if (((SOCResourceCount) mes).getCount() != pl.getResources().getTotal())
                        {
                            SOCResourceSet rsrcs = pl.getResources();

                            if (D.ebugOn)
                            {
                                client.sendText(game, ">>> RESOURCE COUNT ERROR FOR PLAYER " + pl.getPlayerNumber() + ": " + ((SOCResourceCount) mes).getCount() + " != " + rsrcs.getTotal());
                            }

                            //
                            //  fix it
                            //
                            if (pl.getPlayerNumber() != ourPlayerNumber)
                            {
                                rsrcs.clear();
                                rsrcs.setAmount(((SOCResourceCount) mes).getCount(), SOCResourceConstants.UNKNOWN);
                            }
                        }
                        }
                        break;

                    case SOCMessage.DICERESULT:
                        game.setCurrentDice(((SOCDiceResult) mes).getResult());
                        break;

                    case SOCMessage.PUTPIECE:
                        handlePUTPIECE_updateGameData((SOCPutPiece) mes);
                        // For initial roads, also tracks their initial settlement in SOCPlayerTracker.
                        break;

                    case SOCMessage.MOVEPIECE:
                        {
                            SOCMovePiece mpm = (SOCMovePiece) mes;
                            SOCShip sh = new SOCShip
                                (game.getPlayer(mpm.getPlayerNumber()), mpm.getFromCoord(), null);
                            game.moveShip(sh, mpm.getToCoord());
                        }
                        break;

                    case SOCMessage.CANCELBUILDREQUEST:
                        handleCANCELBUILDREQUEST((SOCCancelBuildRequest) mes);
                        break;

                    case SOCMessage.MOVEROBBER:
                        {
                        //
                        // Note: Don't call ga.moveRobber() because that will call the
                        // functions to do the stealing.  We just want to set where
                        // the robber moved, without seeing if something was stolen.
                        // MOVEROBBER will be followed by PLAYERELEMENT messages to
                        // report the gain/loss of resources.
                        //
                        moveRobberOnSeven = false;
                        final int newHex = ((SOCMoveRobber) mes).getCoordinates();
                        if (newHex >= 0)
                            game.getBoard().setRobberHex(newHex, true);
                        else
                            ((SOCBoardLarge) game.getBoard()).setPirateHex(-newHex, true);
                        }
                        break;

                    case SOCMessage.MAKEOFFER:
                        if (robotParameters.getTradeFlag() == 1)
                            handleMAKEOFFER((SOCMakeOffer) mes);
                        break;

                    case SOCMessage.CLEAROFFER:
                        if (robotParameters.getTradeFlag() == 1)
                        {
                            final int pn = ((SOCClearOffer) mes).getPlayerNumber();
                            if (pn != -1)
                            {
                                game.getPlayer(pn).setCurrentOffer(null);
                            } else {
                                for (int i = 0; i < game.maxPlayers; ++i)
                                    game.getPlayer(i).setCurrentOffer(null);
                            }
                        }
                        break;

                    case SOCMessage.ACCEPTOFFER:
                        if (waitingForTradeResponse && (robotParameters.getTradeFlag() == 1))
                        {
                            if ((ourPlayerNumber == (((SOCAcceptOffer) mes).getOfferingNumber()))
                                || (ourPlayerNumber == ((SOCAcceptOffer) mes).getAcceptingNumber()))
                            {
                                waitingForTradeResponse = false;
                            }
                        }
                        break;

                    case SOCMessage.REJECTOFFER:
                        if (robotParameters.getTradeFlag() == 1)
                            handleREJECTOFFER((SOCRejectOffer) mes);
                        break;

                    case SOCMessage.DEVCARDCOUNT:
                        game.setNumDevCards(((SOCDevCardCount) mes).getNumDevCards());
                        break;

                    case SOCMessage.DEVCARD:
                        {
                            SOCDevCard dcMes = (SOCDevCard) mes;
                            if (dcMes.getAction() != SOCDevCard.CANNOT_PLAY)
                            {
                                handleDEVCARD(dcMes);
                            } else {
                                // rejected by server, can't play our requested card
                                rejectedPlayDevCardType = mes.getType();
                                waitingForGameState = false;
                                expectPLACING_FREE_ROAD1 = false;
                                expectWAITING_FOR_DISCOVERY = false;
                                expectWAITING_FOR_MONOPOLY = false;
                                expectPLACING_ROBBER = false;
                            }
                        }
                        break;

                    case SOCMessage.SETPLAYEDDEVCARD:
                        {
                        SOCPlayer player = game.getPlayer(((SOCSetPlayedDevCard) mes).getPlayerNumber());
                        player.setPlayedDevCard(((SOCSetPlayedDevCard) mes).hasPlayedDevCard());
                        }
                        break;

                    }  // switch(mesType)

                    debugInfo();

                    if ((game.getGameState() == SOCGame.PLAY) && (!waitingForGameState))
                    {
                        rollOrPlayKnightOrExpectDice();

                        // On our turn, ask client to roll dice or play a knight;
                        // on other turns, update flags to expect dice result.
                        // Clears expectPLAY to false.
                        // Sets either expectDICERESULT, or expectPLACING_ROBBER and waitingForGameState.
                    }

                    if (ourTurn && (game.getGameState() == SOCGame.WAITING_FOR_ROBBER_OR_PIRATE) && ! waitingForGameState)
                    {
                        // TODO handle moving the pirate too
                        // For now, always decide to move the robber.
                        // Once we move the robber, will also need to deal with state WAITING_FOR_ROB_CLOTH_OR_RESOURCE.
                        expectPLACING_ROBBER = true;
                        waitingForGameState = true;
                        counter = 0;
                        client.choosePlayer(game, SOCChoosePlayer.CHOICE_MOVE_ROBBER);
                        pause(200);
                    }

                    else if ((game.getGameState() == SOCGame.PLACING_ROBBER) && (!waitingForGameState))
                    {
                        expectPLACING_ROBBER = false;

                        if ((!waitingForOurTurn) && (ourTurn))
                        {
                            if (!((expectPLAY || expectPLAY1) && (counter < 4000)))
                            {
                                if (moveRobberOnSeven)
                                {
                                    // robber moved because 7 rolled on dice
                                    moveRobberOnSeven = false;
                                    waitingForGameState = true;
                                    counter = 0;
                                    expectPLAY1 = true;
                                }
                                else
                                {
                                    waitingForGameState = true;
                                    counter = 0;

                                    if (oldGameState == SOCGame.PLAY)
                                    {
                                        // robber moved from playing knight card before dice roll
                                        expectPLAY = true;
                                    }
                                    else if (oldGameState == SOCGame.PLAY1)
                                    {
                                        // robber moved from playing knight card after dice roll
                                        expectPLAY1 = true;
                                    }
                                }

                                counter = 0;
                                moveRobber();
                            }
                        }
                    }

                    if ((game.getGameState() == SOCGame.WAITING_FOR_DISCOVERY) && (!waitingForGameState))
                    {
                        expectWAITING_FOR_DISCOVERY = false;

                        if ((!waitingForOurTurn) && (ourTurn))
                        {
                            if (!(expectPLAY1) && (counter < 4000))
                            {
                                waitingForGameState = true;
                                expectPLAY1 = true;
                                counter = 0;
                                client.discoveryPick(game, resourceChoices);
                                pause(1500);
                            }
                        }
                    }

                    if ((game.getGameState() == SOCGame.WAITING_FOR_MONOPOLY) && (!waitingForGameState))
                    {
                        expectWAITING_FOR_MONOPOLY = false;

                        if ((!waitingForOurTurn) && (ourTurn))
                        {
                            if (!(expectPLAY1) && (counter < 4000))
                            {
                                waitingForGameState = true;
                                expectPLAY1 = true;
                                counter = 0;
                                client.monopolyPick(game, monopolyStrategy.getMonopolyChoice());
                                pause(1500);
                            }
                        }
                    }

                    if (waitingForTradeMsg && (mesType == SOCMessage.GAMETEXTMSG) && (((SOCGameTextMsg) mes).getNickname().equals(SOCServer.SERVERNAME)))
                    {
                        //
                        // This might be the trade message we've been waiting for
                        //
                        if (((SOCGameTextMsg) mes).getText().startsWith(client.getNickname() + " traded"))
                        {
                            waitingForTradeMsg = false;
                        }
                    }

                    if (waitingForDevCard && (mesType == SOCMessage.GAMETEXTMSG) && (((SOCGameTextMsg) mes).getNickname().equals(SOCServer.SERVERNAME)))
                    {
                        //
                        // This might be the dev card message we've been waiting for
                        //
                        if (((SOCGameTextMsg) mes).getText().equals(client.getNickname() + " bought a development card."))
                        {
                            waitingForDevCard = false;
                        }
                    }

                    if (((game.getGameState() == SOCGame.PLAY1) || (game.getGameState() == SOCGame.SPECIAL_BUILDING))
                        && ! (waitingForGameState || waitingForTradeMsg || waitingForTradeResponse || waitingForDevCard
                              || expectPLACING_ROAD || expectPLACING_SETTLEMENT || expectPLACING_CITY || expectPLACING_SHIP
                              || expectPLACING_ROBBER || expectPLACING_FREE_ROAD1 || expectPLACING_FREE_ROAD2 || expectWAITING_FOR_DISCOVERY || expectWAITING_FOR_MONOPOLY))
                    {
                        // Time to decide to build, or take other normal actions.

                        expectPLAY1 = false;

                        // 6-player: check Special Building Phase
                        // during other players' turns.
                        if ((! ourTurn) && waitingForOurTurn && gameIs6Player
                             && (! decidedIfSpecialBuild) && (!expectPLACING_ROBBER))
                        {
                            decidedIfSpecialBuild = true;

                            /**
                             * It's not our turn.  We're not doing anything else right now.
                             * Gamestate has passed PLAY, so we know what resources to expect.
                             * Do we want to Special Build?  Check the same conditions as during our turn.
                             * Make a plan if we don't have one,
                             * and if we haven't given up building
                             * attempts this turn.
                             */

                            if (buildingPlan.empty() && (ourPlayerData.getResources().getTotal() > 1) && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
                            {
                                planBuilding();

                                /*
                                 * planBuilding takes these actions:
                                 *
                                decisionMaker.planStuff(robotParameters.getStrategyType());

                                if (!buildingPlan.empty())
                                {
                                    lastTarget = (SOCPossiblePiece) buildingPlan.peek();
                                    negotiator.setTargetPiece(ourPlayerNumber, (SOCPossiblePiece) buildingPlan.peek());
                                }
                                 */

                                if ( ! buildingPlan.empty())
                                {
                                    // Do we have the resources right now?
                                    final SOCPossiblePiece targetPiece = buildingPlan.peek();
                                    final SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

                                    if ((ourPlayerData.getResources().contains(targetResources)))
                                    {
                                        // Ask server for the Special Building Phase.
                                        // (TODO) if FAST_STRATEGY: Maybe randomly don't ask?
                                        waitingForSpecialBuild = true;
                                        client.buildRequest(game, -1);
                                        pause(100);
                                    }
                                }
                            }
                        }

                        if ((! waitingForOurTurn) && ourTurn)
                        {
                            if (!(expectPLAY && (counter < 4000)))
                            {
                                counter = 0;

                                //D.ebugPrintln("DOING PLAY1");
                                if (D.ebugOn)
                                {
                                    client.sendText(game, "================================");

                                    // for each player in game:
                                    //    sendText and debug-prn game.getPlayer(i).getResources()
                                    printResources();
                                }

                                /**
                                 * if we haven't played a dev card yet,
                                 * and we have a knight, and we can get
                                 * largest army, play the knight.
                                 * If we're in SPECIAL_BUILDING (not PLAY1),
                                 * can't trade or play development cards.
                                 */
                                if ((game.getGameState() == SOCGame.PLAY1) && ! ourPlayerData.hasPlayedDevCard())
                                {
                                    SOCPlayer laPlayer = game.getPlayerWithLargestArmy();

                                    if (((laPlayer != null) && (laPlayer.getPlayerNumber() != ourPlayerNumber)) || (laPlayer == null))
                                    {
                                        int larmySize;

                                        if (laPlayer == null)
                                        {
                                            larmySize = 3;
                                        }
                                        else
                                        {
                                            larmySize = laPlayer.getNumKnights() + 1;
                                        }

                                        if ( ((ourPlayerData.getNumKnights()
                                              + ourPlayerData.getDevCards().getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT)
                                              + ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT))
                                              >= larmySize)
                                            && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) > 0)
                                            && (rejectedPlayDevCardType != SOCDevCardConstants.KNIGHT))
                                        {
                                            /**
                                             * play a knight card
                                             */
                                            expectPLACING_ROBBER = true;
                                            waitingForGameState = true;
                                            counter = 0;
                                            client.playDevCard(game, SOCDevCardConstants.KNIGHT);
                                            pause(1500);
                                        }
                                    }
                                }

                                /**
                                 * make a plan if we don't have one,
                                 * and if we haven't given up building
                                 * attempts this turn.
                                 */
                                if ( (! expectPLACING_ROBBER) && buildingPlan.empty() && (ourPlayerData.getResources().getTotal() > 1) && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
                                {
                                    planBuilding();

                                    /*
                                     * planBuilding takes these actions:
                                     *
                                    decisionMaker.planStuff(robotParameters.getStrategyType());

                                    if (!buildingPlan.empty())
                                    {
                                        lastTarget = (SOCPossiblePiece) buildingPlan.peek();
                                        negotiator.setTargetPiece(ourPlayerNumber, (SOCPossiblePiece) buildingPlan.peek());
                                    }
                                     */
                                }

                                //D.ebugPrintln("DONE PLANNING");
                                if ( (! expectPLACING_ROBBER) && (! buildingPlan.empty()))
                                {
                                    // Time to build something.

                                    // Either ask to build a piece, or use trading or development
                                    // cards to get resources to build it.  See javadoc for flags set.
                                    buildOrGetResourceByTradeOrCard();
                                }

                                /**
                                 * see if we're done with our turn
                                 */
                                if (! (expectPLACING_SETTLEMENT || expectPLACING_FREE_ROAD1 || expectPLACING_FREE_ROAD2 || expectPLACING_ROAD || expectPLACING_CITY || expectPLACING_SHIP
                                       || expectWAITING_FOR_DISCOVERY || expectWAITING_FOR_MONOPOLY || expectPLACING_ROBBER || waitingForTradeMsg || waitingForTradeResponse || waitingForDevCard))
                                {
                                    waitingForGameState = true;
                                    counter = 0;
                                    expectPLAY = true;
                                    waitingForOurTurn = true;

                                    if (robotParameters.getTradeFlag() == 1)
                                    {
                                        doneTrading = false;
                                    }
                                    else
                                    {
                                        doneTrading = true;
                                    }

                                    //D.ebugPrintln("!!! ENDING TURN !!!");
                                    negotiator.resetIsSelling();
                                    negotiator.resetOffersMade();
                                    buildingPlan.clear();
                                    negotiator.resetTargetPieces();
                                    pause(1500);
                                    client.endTurn(game);
                                }
                            }
                        }
                    }

                    /**
                     * Placement: Make various putPiece calls; server has told us it's OK to buy them.
                     * Call client.putPiece.
                     * Works when it's our turn and we have an expect flag set
                     * (such as expectPLACING_SETTLEMENT, in these game states:
                     * START1A - START2B
                     * PLACING_SETTLEMENT, PLACING_ROAD, PLACING_CITY
                     * PLACING_FREE_ROAD1, PLACING_FREE_ROAD2
                     */
                    if (! waitingForGameState)
                    {
                        placeIfExpectPlacing();
                    }

                    /**
                     * End of various putPiece placement calls.
                     */

                    /*
                       if (game.getGameState() == SOCGame.OVER) {
                       client.leaveGame(game);
                       alive = false;
                       }
                     */

                    /**
                     * Handle various message types here at bottom of loop.
                     */
                    switch (mesType)
                    {
                    case SOCMessage.SETTURN:
                        game.setCurrentPlayerNumber(((SOCSetTurn) mes).getPlayerNumber());
                        break;

                    case SOCMessage.PUTPIECE:
                        /**
                         * this is for player tracking
                         */
                        {
                            final SOCPutPiece mpp = (SOCPutPiece) mes;
                            final int pn = mpp.getPlayerNumber();
                            final int coord = mpp.getCoordinates();
                            final int pieceType = mpp.getPieceType();
                            handlePUTPIECE_updateTrackers(pn, coord, pieceType);
                        }

                        // For initial placement of our own pieces, also checks
                        // and clears expectPUTPIECE_FROM_START1A,
                        // and sets expectSTART1B, etc.  The final initial putpiece
                        // clears expectPUTPIECE_FROM_START2B and sets expectPLAY.

                        break;

                    case SOCMessage.MOVEPIECE:
                        /**
                         * this is for player tracking of moved ships
                         */
                        {
                            final SOCMovePiece mpp = (SOCMovePiece) mes;
                            final int pn = mpp.getPlayerNumber();
                            final int coord = mpp.getToCoord();
                            final int pieceType = mpp.getPieceType();
                            // TODO what about getFromCoord()?
                            handlePUTPIECE_updateTrackers(pn, coord, pieceType);
                        }
                        break;

                    case SOCMessage.DICERESULT:
                        if (expectDICERESULT)
                        {
                            expectDICERESULT = false;

                            if (((SOCDiceResult) mes).getResult() == 7)
                            {
                                final boolean robWithoutRobber = game.isGameOptionSet(SOCGameOption.K_SC_PIRI);

                                if (! robWithoutRobber)
                                    moveRobberOnSeven = true;

                                if (ourPlayerData.getResources().getTotal() > 7)
                                    expectDISCARD = true;

                                else if (ourTurn)
                                {
                                    if (! robWithoutRobber)
                                        expectPLACING_ROBBER = true;
                                    else
                                        expectPLAY1 = true;
                                }
                            }
                            else
                            {
                                expectPLAY1 = true;
                            }
                        }
                        break;

                    case SOCMessage.PICKRESOURCESREQUEST:
                        // gold hex
                        counter = 0;
                        pickFreeResources( ((SOCPickResourcesRequest) mes).getParam() );
                        waitingForGameState = true;
                        if (game.isInitialPlacement())
                        {
                            if (game.isGameOptionSet(SOCGameOption.K_SC_3IP))
                                expectSTART3B = true;
                            else
                                expectSTART2B = true;
                        } else {
                            expectPLAY1 = true;
                        }
                        break;

                    case SOCMessage.DISCARDREQUEST:
                        expectDISCARD = false;

                        /**
                         * If we haven't recently discarded...
                         */

                        //	if (!((expectPLACING_ROBBER || expectPLAY1) &&
                        //	      (counter < 4000))) {
                        if ((game.getCurrentDice() == 7) && (ourTurn))
                        {
                            if (! game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
                                expectPLACING_ROBBER = true;
                            else
                                expectPLAY1 = true;
                        }
                        else
                        {
                            expectPLAY1 = true;
                        }

                        counter = 0;
                        client.discard(game, DiscardStrategy.discard
                            (((SOCDiscardRequest) mes).getNumberOfDiscards(), buildingPlan, rand,
                              ourPlayerData, robotParameters, decisionMaker, negotiator));

                        //	}
                        break;

                    case SOCMessage.CHOOSEPLAYERREQUEST:
                        {
                            final int choicePl = RobberStrategy.chooseRobberVictim
                                (((SOCChoosePlayerRequest) mes).getChoices(), game, playerTrackers);
                            counter = 0;
                            client.choosePlayer(game, choicePl);
                        }
                        break;

                    case SOCMessage.CHOOSEPLAYER:
                        {
                            final int vpn = ((SOCChoosePlayer) mes).getChoice();
                            // Cloth is more valuable.
                            // TODO decide when we should choose resources instead
                            client.choosePlayer(game, -(vpn + 1));
                        }
                        break;

                    case SOCMessage.ROBOTDISMISS:
                        if ((!expectDISCARD) && (!expectPLACING_ROBBER))
                        {
                            client.leaveGame(game, "dismiss msg", false);
                            alive = false;
                        }
                        break;

                    case SOCMessage.TIMINGPING:
                        // Once-per-second message from the pinger thread
                        counter++;
                        break;

                    }  // switch (mesType) - for some types, at bottom of loop body

                    if (counter > 15000)
                    {
                        // We've been waiting too long, must be a bug: Leave the game.
                        client.leaveGame(game, "counter 15000", false);
                        alive = false;
                    }

                    if ((failedBuildingAttempts > (2 * MAX_DENIED_BUILDING_PER_TURN))
                        && game.isInitialPlacement())
                    {
                        // Apparently can't decide where we can initially place:
                        // Leave the game.
                        client.leaveGame(game, "failedBuildingAttempts at start", false);
                        alive = false;
                    }

                    /*
                       if (D.ebugOn) {
                       if (mes != null) {
                       debugInfo();
                       D.ebugPrintln("~~~~~~~~~~~~~~~~");
                       }
                       }
                     */
                    yield();
                }
            }
            catch (Throwable e)
            {
                // Ignore errors due to game reset in another thread
                if (alive && ((game == null) || (game.getGameState() != SOCGame.RESET_OLD)))
                {
                    D.ebugPrintln("*** Robot caught an exception - " + e);
                    System.out.println("*** Robot caught an exception - " + e);
                    e.printStackTrace();
                }
            }
        }
        else
        {
            System.out.println("AGG! NO PINGER!");
        }

        //D.ebugPrintln("STOPPING AND DEALLOCATING");
        gameEventQ = null;
        client.addCleanKill();
        client = null;
        game = null;
        ourPlayerData = null;
        dummyCancelPlayerData = null;
        whatWeWantToBuild = null;
        whatWeFailedToBuild = null;
        resourceChoices = null;
        ourPlayerTracker = null;
        playerTrackers = null;
        pinger.stopPinger();
        pinger = null;
    }

    /**
     * Stop waiting for responses to a trade offer.
     * Remember other players' responses,
     * Call {@link SOCRobotClient#clearOffer(SOCGame) client.clearOffer},
     * clear {@link #waitingForTradeResponse} and {@link #counter}.
     * @since 1.1.09
     */
    private void tradeStopWaitingClearOffer()
    {
        ///
        /// record which players said no by not saying anything
        ///
        SOCTradeOffer ourCurrentOffer = ourPlayerData.getCurrentOffer();

        if (ourCurrentOffer != null)
        {
            boolean[] offeredTo = ourCurrentOffer.getTo();
            SOCResourceSet getSet = ourCurrentOffer.getGetSet();

            for (int rsrcType = SOCResourceConstants.CLAY;
                    rsrcType <= SOCResourceConstants.WOOD;
                    rsrcType++)
            {
                if (getSet.getAmount(rsrcType) > 0)
                {
                    for (int pn = 0; pn < game.maxPlayers; pn++)
                    {
                        if (offeredTo[pn])
                        {
                            negotiator.markAsNotSelling(pn, rsrcType);
                            negotiator.markAsNotWantingAnotherOffer(pn, rsrcType);
                        }
                    }
                }
            }

            pause(1500);
            client.clearOffer(game);
            pause(500);
        }

        counter = 0;
        waitingForTradeResponse = false;
    }

    /**
     * If it's our turn and we have an expect flag set
     * (such as {@link #expectPLACING_SETTLEMENT}), then
     * call {@link SOCRobotClient#putPiece(SOCGame, SOCPlayingPiece) client.putPiece}.
     *<P>
     * Looks for one of these game states:
     *<UL>
     * <LI> {@link SOCGame#START1A} - {@link SOCGame#START3B}
     * <LI> {@link SOCGame#PLACING_SETTLEMENT}
     * <LI> {@link SOCGame#PLACING_ROAD}
     * <LI> {@link SOCGame#PLACING_CITY}
     * <LI> {@link SOCGame#PLACING_SHIP}
     * <LI> {@link SOCGame#PLACING_FREE_ROAD1}
     * <LI> {@link SOCGame#PLACING_FREE_ROAD2}
     *</UL>
     * @since 1.1.09
     */
    private void placeIfExpectPlacing()
    {
        if (waitingForGameState)
            return;

        switch (game.getGameState())
        {
            case SOCGame.PLACING_SETTLEMENT:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_SETTLEMENT))
                {
                    expectPLACING_SETTLEMENT = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;

                    //D.ebugPrintln("!!! PUTTING PIECE "+whatWeWantToBuild+" !!!");
                    pause(500);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(1000);
                }
            }
            break;

            case SOCGame.PLACING_ROAD:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_ROAD))
                {
                    expectPLACING_ROAD = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;

                    pause(500);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(1000);
                }
            }
            break;

            case SOCGame.PLACING_CITY:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_CITY))
                {
                    expectPLACING_CITY = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;

                    pause(500);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(1000);
                }
            }
            break;

            case SOCGame.PLACING_SHIP:
                {
                    if ((ourTurn) && (! waitingForOurTurn) && (expectPLACING_SHIP))
                    {
                        expectPLACING_SHIP = false;
                        waitingForGameState = true;
                        counter = 0;
                        expectPLAY1 = true;

                        pause(500);
                        client.putPiece(game, whatWeWantToBuild);
                        pause(1000);
                    }
                }
                break;

            case SOCGame.PLACING_FREE_ROAD1:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_FREE_ROAD1))
                {
                    expectPLACING_FREE_ROAD1 = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLACING_FREE_ROAD2 = true;
                    // D.ebugPrintln("!!! PUTTING PIECE 1 " + whatWeWantToBuild + " !!!");
                    pause(500);
                    client.putPiece(game, whatWeWantToBuild);  // either ROAD or SHIP
                    pause(1000);
                }
            }
            break;

            case SOCGame.PLACING_FREE_ROAD2:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_FREE_ROAD2))
                {
                    expectPLACING_FREE_ROAD2 = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;

                    SOCPossiblePiece posPiece = buildingPlan.pop();

                    if (posPiece.getType() == SOCPossiblePiece.ROAD)
                        whatWeWantToBuild = new SOCRoad(ourPlayerData, posPiece.getCoordinates(), null);
                    else
                        whatWeWantToBuild = new SOCShip(ourPlayerData, posPiece.getCoordinates(), null);

                    // D.ebugPrintln("posPiece = " + posPiece);
                    // D.ebugPrintln("$ POPPED OFF");
                    // D.ebugPrintln("!!! PUTTING PIECE 2 " + whatWeWantToBuild + " !!!");
                    pause(500);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(1000);
                }
            }
            break;

            case SOCGame.START1A:
            {
                expectSTART1A = false;

                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START1A && (counter < 4000))))
                {
                    expectPUTPIECE_FROM_START1A = true;
                    counter = 0;
                    waitingForGameState = true;
                    final int firstSettleNode = openingBuildStrategy.planInitialSettlements();
                    placeFirstSettlement(firstSettleNode);
                }
            }
            break;

            case SOCGame.START1B:
            {
                expectSTART1B = false;

                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START1B && (counter < 4000))))
                {
                    expectPUTPIECE_FROM_START1B = true;
                    counter = 0;
                    waitingForGameState = true;
                    pause(1500);
                    planAndPlaceInitRoad();
                }
            }
            break;

            case SOCGame.START2A:
            {
                expectSTART2A = false;

                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START2A && (counter < 4000))))
                {
                    expectPUTPIECE_FROM_START2A = true;
                    counter = 0;
                    waitingForGameState = true;
                    final int secondSettleNode = openingBuildStrategy.planSecondSettlement();
                    placeInitSettlement(secondSettleNode);
                }
            }
            break;

            case SOCGame.START2B:
            {
                expectSTART2B = false;

                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START2B && (counter < 4000))))
                {
                    expectPUTPIECE_FROM_START2B = true;
                    counter = 0;
                    waitingForGameState = true;
                    pause(1500);
                    planAndPlaceInitRoad();
                }
            }
            break;

            case SOCGame.START3A:
            {
                expectSTART3A = false;

                if ((! waitingForOurTurn) && ourTurn && (! (expectPUTPIECE_FROM_START3A && (counter < 4000))))
                {
                    expectPUTPIECE_FROM_START3A = true;
                    counter = 0;
                    waitingForGameState = true;
                    final int secondSettleNode = openingBuildStrategy.planSecondSettlement();  // TODO planThirdSettlement
                    placeInitSettlement(secondSettleNode);
                }
            }
            break;

            case SOCGame.START3B:
            {
                expectSTART3B = false;

                if ((! waitingForOurTurn) && ourTurn && (! (expectPUTPIECE_FROM_START3B && (counter < 4000))))
                {
                    expectPUTPIECE_FROM_START3B = true;
                    counter = 0;
                    waitingForGameState = true;
                    pause(1500);
                    planAndPlaceInitRoad();
                }
            }
            break;

        }
    }

    /**
     * On our turn, ask client to roll dice or play a knight;
     * on other turns, update flags to expect dice result.
     *<P>
     * Call when gameState {@link SOCGame#PLAY} && ! {@link #waitingForGameState}.
     *<P>
     * Clears {@link #expectPLAY} to false.
     * Sets either {@link #expectDICERESULT}, or {@link #expectPLACING_ROBBER} and {@link #waitingForGameState}.
     *
     * @since 1.1.08
     */
    private void rollOrPlayKnightOrExpectDice()
    {
        expectPLAY = false;

        if ((!waitingForOurTurn) && (ourTurn))
        {
            if (!expectPLAY1 && !expectDISCARD && !expectPLACING_ROBBER && !(expectDICERESULT && (counter < 4000)))
            {
                /**
                 * if we have a knight card and the robber
                 * is on one of our numbers, play the knight card
                 */
                if ((ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) > 0)
                    && (rejectedPlayDevCardType != SOCDevCardConstants.KNIGHT)
                    && ! ourPlayerData.getNumbers().hasNoResourcesForHex(game.getBoard().getRobberHex()))
                {
                    expectPLACING_ROBBER = true;
                    waitingForGameState = true;
                    counter = 0;
                    client.playDevCard(game, SOCDevCardConstants.KNIGHT);
                    pause(1500);
                }
                else
                {
                    expectDICERESULT = true;
                    counter = 0;

                    //D.ebugPrintln("!!! ROLLING DICE !!!");
                    client.rollDice(game);
                }
            }
        }
        else
        {
            /**
             * not our turn
             */
            expectDICERESULT = true;
        }
    }

    /**
     * Either ask to build a planned piece, or use trading or development cards to get resources to build it.
     * Examines {@link #buildingPlan} for the next piece wanted.
     *<P>
     * If we need resources and we can't get them through the robber,
     * the {@link SOCDevCardConstants#ROADS Road Building} or
     * {@link SOCDevCardConstants#MONO Monopoly} or
     * {@link SOCDevCardConstants#DISC Discovery} development cards,
     * then trades with the bank ({@link #tradeToTarget2(SOCResourceSet)})
     * or with other players ({@link #makeOffer(SOCPossiblePiece)}).
     *<P>
     * Call when these conditions are all true:
     * <UL>
     *<LI> {@link #ourTurn}
     *<LI> {@link #planBuilding()} already called
     *<LI> ! {@link #buildingPlan}.empty()
     *<LI> gameState {@link SOCGame#PLAY1} or {@link SOCGame#SPECIAL_BUILDING}
     *<LI> <tt>waitingFor...</tt> flags all false ({@link #waitingForGameState}, etc) except possibly {@link #waitingForSpecialBuild}
     *<LI> <tt>expect...</tt> flags all false ({@link #expectPLACING_ROAD}, etc)
     *<LI> ! {@link #waitingForOurTurn}
     *<LI> ! ({@link #expectPLAY} && (counter < 4000))
     *</UL>
     *<P>
     * May set any of these flags:
     * <UL>
     *<LI> {@link #waitingForGameState}, and {@link #expectWAITING_FOR_DISCOVERY} or {@link #expectWAITING_FOR_MONOPOLY}
     *<LI> {@link #waitingForTradeMsg} or {@link #waitingForTradeResponse} or {@link #doneTrading}
     *<LI> {@link #waitingForDevCard}, or {@link #waitingForGameState} and {@link #expectPLACING_SETTLEMENT} (etc).
     *</UL>
     *
     * @since 1.1.08
     * @throw IllegalStateException  if {@link #buildingPlan}{@link Stack#empty() .empty()}
     */
    private void buildOrGetResourceByTradeOrCard()
        throws IllegalStateException
    {
        if (buildingPlan.empty())
            throw new IllegalStateException("buildingPlan empty when called");

        /**
         * If we're in SPECIAL_BUILDING (not PLAY1),
         * can't trade or play development cards.
         */
        final boolean gameStatePLAY1 = (game.getGameState() == SOCGame.PLAY1);

        /**
         * check to see if this is a Road Building plan
         */
        boolean roadBuildingPlan = false;
        // TODO handle ships here

        if (gameStatePLAY1
            && (! ourPlayerData.hasPlayedDevCard())
            && (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2)
            && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0)
            && (rejectedPlayDevCardType != SOCDevCardConstants.ROADS))
        {
            //D.ebugPrintln("** Checking for Road Building Plan **");
            SOCPossiblePiece topPiece = buildingPlan.pop();

            //D.ebugPrintln("$ POPPED "+topPiece);
            if ((topPiece != null) && (topPiece instanceof SOCPossibleRoad))
            {
                SOCPossiblePiece secondPiece = buildingPlan.peek();

                //D.ebugPrintln("secondPiece="+secondPiece);
                if ((secondPiece != null) && (secondPiece instanceof SOCPossibleRoad))
                {
                    roadBuildingPlan = true;

                    if (topPiece instanceof SOCPossibleShip)
                        whatWeWantToBuild = new SOCShip(ourPlayerData, topPiece.getCoordinates(), null);
                    else
                        whatWeWantToBuild = new SOCRoad(ourPlayerData, topPiece.getCoordinates(), null);

                    if (! whatWeWantToBuild.equals(whatWeFailedToBuild))
                    {
                        waitingForGameState = true;
                        counter = 0;
                        expectPLACING_FREE_ROAD1 = true;

                        //D.ebugPrintln("!! PLAYING ROAD BUILDING CARD");
                        client.playDevCard(game, SOCDevCardConstants.ROADS);
                    } else {
                        // We already tried to build this.
                        roadBuildingPlan = false;
                        cancelWrongPiecePlacementLocal(whatWeWantToBuild);
                        // cancel sets whatWeWantToBuild = null;
                    }
                }
                else
                {
                    //D.ebugPrintln("$ PUSHING "+topPiece);
                    buildingPlan.push(topPiece);
                }
            }
            else
            {
                //D.ebugPrintln("$ PUSHING "+topPiece);
                buildingPlan.push(topPiece);
            }
        }

        if (! roadBuildingPlan)
        {
            ///
            /// figure out what resources we need
            ///
            SOCPossiblePiece targetPiece = buildingPlan.peek();
            SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

            //D.ebugPrintln("^^^ targetPiece = "+targetPiece);
            //D.ebugPrintln("^^^ ourResources = "+ourPlayerData.getResources());

            negotiator.setTargetPiece(ourPlayerNumber, targetPiece);

            ///
            /// if we have a 2 free resources card and we need
            /// at least 2 resources, play the card
            ///
            if (gameStatePLAY1
                && (! ourPlayerData.hasPlayedDevCard())
                && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC) > 0)
                && (rejectedPlayDevCardType != SOCDevCardConstants.DISC))
            {
                if (chooseFreeResourcesIfNeeded(targetResources, 2, false))
                {
                    ///
                    /// play the card
                    ///
                    expectWAITING_FOR_DISCOVERY = true;
                    waitingForGameState = true;
                    counter = 0;
                    client.playDevCard(game, SOCDevCardConstants.DISC);
                    pause(1500);
                }
            }

            if (!expectWAITING_FOR_DISCOVERY)
            {
                ///
                /// if we have a monopoly card, play it
                /// and take what there is most of
                ///
                if (gameStatePLAY1
                    && (! ourPlayerData.hasPlayedDevCard())
                    && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO) > 0)
                    && (rejectedPlayDevCardType != SOCDevCardConstants.MONO)
                    && monopolyStrategy.decidePlayMonopoly())
                {
                    ///
                    /// play the card
                    ///
                    expectWAITING_FOR_MONOPOLY = true;
                    waitingForGameState = true;
                    counter = 0;
                    client.playDevCard(game, SOCDevCardConstants.MONO);
                    pause(1500);
                }

                if (!expectWAITING_FOR_MONOPOLY)
                {
                    if (gameStatePLAY1 && (!doneTrading) && (!ourPlayerData.getResources().contains(targetResources)))
                    {
                        waitingForTradeResponse = false;

                        if (robotParameters.getTradeFlag() == 1)
                        {
                            makeOffer(targetPiece);
                            // makeOffer will set waitingForTradeResponse or doneTrading.
                        }
                    }

                    if (gameStatePLAY1 && !waitingForTradeResponse)
                    {
                        /**
                         * trade with the bank/ports
                         */
                        if (tradeToTarget2(targetResources))
                        {
                            counter = 0;
                            waitingForTradeMsg = true;
                            pause(1500);
                        }
                    }

                    ///
                    /// build if we can
                    ///
                    if (!waitingForTradeMsg && !waitingForTradeResponse && ourPlayerData.getResources().contains(targetResources))
                    {
                        // Remember that targetPiece == buildingPlan.peek().
                        // Calls buildingPlan.pop().
                        // Checks against whatWeFailedToBuild to see if server has rejected this already.
                        // Calls client.buyDevCard or client.buildRequest.
                        // Sets waitingForDevCard, or waitingForGameState and expectPLACING_SETTLEMENT (etc).

                        buildRequestPlannedPiece();
                    }
                }
            }
        }
    }

    /**
     * Handle a PUTPIECE for this game, by updating game data.
     * For initial roads, also track their initial settlement in SOCPlayerTracker.
     * In general, most tracking is done a bit later in {@link #handlePUTPIECE_updateTrackers(SOCPutPiece)}.
     * @since 1.1.08
     */
    private void handlePUTPIECE_updateGameData(SOCPutPiece mes)
    {
        final int coord = mes.getCoordinates();
        final int pieceType = mes.getPieceType();
        final SOCPlayer pl = (pieceType != SOCPlayingPiece.VILLAGE)
            ? game.getPlayer(mes.getPlayerNumber())
            : null;

        switch (pieceType)
        {
        case SOCPlayingPiece.SHIP:  // fall through to ROAD
        case SOCPlayingPiece.ROAD:

            if (game.isInitialPlacement())  // START1B, START2B, START3B
            {
                //
                // Before processing this road/ship, track the settlement that goes with it.
                // This was deferred until road placement, in case a human player decides
                // to cancel their settlement and place it elsewhere.
                //
                SOCPlayerTracker tr = playerTrackers.get(Integer.valueOf(mes.getPlayerNumber()));
                SOCSettlement se = tr.getPendingInitSettlement();
                if (se != null)
                    trackNewSettlement(se, false);
            }
            SOCRoad rd;
            if (pieceType == SOCPlayingPiece.ROAD)
                rd = new SOCRoad(pl, coord, null);
            else
                rd = new SOCShip(pl, coord, null);
            game.putPiece(rd);
            break;

        case SOCPlayingPiece.SETTLEMENT:

            SOCSettlement se = new SOCSettlement(pl, coord, null);
            game.putPiece(se);
            break;

        case SOCPlayingPiece.CITY:

            SOCCity ci = new SOCCity(pl, coord, null);
            game.putPiece(ci);
            break;

        case SOCPlayingPiece.VILLAGE:

            SOCVillage vi = new SOCVillage(coord, game.getBoard());
            game.putPiece(vi);
            break;
        }
    }

    /**
     * Handle a CANCELBUILDREQUEST for this game.
     *<P>
     *<b> During game startup</b> (START1B or START2B): <BR>
     *    When sent from server to client, CANCELBUILDREQUEST means the current
     *    player wants to undo the placement of their initial settlement.
     *<P>
     *<b> During piece placement</b> (PLACING_ROAD, PLACING_CITY, PLACING_SETTLEMENT,
     *                         PLACING_FREE_ROAD1, or PLACING_FREE_ROAD2): <BR>
     *    When sent from server to client, CANCELBUILDREQUEST means the player
     *    has sent an illegal PUTPIECE (bad building location).
     *    Humans can probably decide a better place to put their road,
     *    but robots must cancel the build request and decide on a new plan.
     *
     * @since 1.1.08
     */
    private void handleCANCELBUILDREQUEST(SOCCancelBuildRequest mes)
    {
        final int gstate = game.getGameState();
        switch (gstate)
        {
        case SOCGame.START1A:
        case SOCGame.START2A:
        case SOCGame.START3A:
            if (ourTurn)
            {
                cancelWrongPiecePlacement(mes);
            }
            break;

        case SOCGame.START1B:
        case SOCGame.START2B:
        case SOCGame.START3B:
            if (ourTurn)
            {
                cancelWrongPiecePlacement(mes);
            }
            else
            {
                //
                // human player placed, then cancelled placement.
                // Our robot wouldn't do that, and if it's ourTurn,
                // the cancel happens only if we try an illegal placement.
                //
                final int pnum = game.getCurrentPlayerNumber();
                SOCPlayer pl = game.getPlayer(pnum);
                SOCSettlement pp = new SOCSettlement(pl, pl.getLastSettlementCoord(), null);
                game.undoPutInitSettlement(pp);
                //
                // "forget" to track this cancelled initial settlement.
                // Wait for human player to place a new one.
                //
                SOCPlayerTracker tr = playerTrackers.get(Integer.valueOf(pnum));
                tr.setPendingInitSettlement(null);
            }
            break;

        case SOCGame.PLAY1:  // asked to build, hasn't given location yet -> resources
        case SOCGame.PLACING_ROAD:        // has given location -> is bad location
        case SOCGame.PLACING_SETTLEMENT:
        case SOCGame.PLACING_CITY:
        case SOCGame.PLACING_FREE_ROAD1:  // JM TODO how to break out?
        case SOCGame.PLACING_FREE_ROAD2:  // JM TODO how to break out?
        case SOCGame.SPECIAL_BUILDING:
            //
            // We've asked for an illegal piece placement.
            // (Must be a bug.) Cancel and invalidate this
            // planned piece, make a new plan.
            //
            // Can also happen in special building, if another
            // player has placed since we requested special building.
            // If our PUTPIECE request is denied, server sends us
            // CANCELBUILDREQUEST.  We need to ask to cancel the
            // placement, and also set variables to end our SBP turn.
            //
            cancelWrongPiecePlacement(mes);
            break;

        default:
            if (game.isSpecialBuilding())
            {
                cancelWrongPiecePlacement(mes);
            } else {
                // Should not occur
                D.ebugPrintln("Unexpected CANCELBUILDREQUEST at state " + gstate);
            }

        }  // switch (gameState)
    }

    /**
     * Handle a MAKEOFFER for this game.
     * if another player makes an offer, that's the
     * same as a rejection, but still wants to deal.
     * Call {@link #considerOffer(SOCTradeOffer)}, and if
     * we accept, clear our {@link #buildingPlan} so we'll replan it.
     * Ignore our own MAKEOFFERs echoed from server.
     * @since 1.1.08
     */
    private void handleMAKEOFFER(SOCMakeOffer mes)
    {
        SOCTradeOffer offer = mes.getOffer();
        game.getPlayer(offer.getFrom()).setCurrentOffer(offer);

        if ((offer.getFrom() == ourPlayerNumber))
        {
            return;  // <---- Ignore our own offers ----
        }

        ///
        /// record that this player wants to sell me the stuff
        ///
        SOCResourceSet giveSet = offer.getGiveSet();

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD;
                rsrcType++)
        {
            if (giveSet.getAmount(rsrcType) > 0)
            {
                D.ebugPrintln("%%% player " + offer.getFrom() + " wants to sell " + rsrcType);
                negotiator.markAsWantsAnotherOffer(offer.getFrom(), rsrcType);
            }
        }

        ///
        /// record that this player is not selling the resources
        /// he is asking for
        ///
        SOCResourceSet getSet = offer.getGetSet();

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD;
                rsrcType++)
        {
            if (getSet.getAmount(rsrcType) > 0)
            {
                D.ebugPrintln("%%% player " + offer.getFrom() + " wants to buy " + rsrcType + " and therefore does not want to sell it");
                negotiator.markAsNotSelling(offer.getFrom(), rsrcType);
            }
        }

        if (waitingForTradeResponse)
        {
            offerRejections[offer.getFrom()] = true;

            boolean everyoneRejected = true;
            D.ebugPrintln("ourPlayerData.getCurrentOffer() = " + ourPlayerData.getCurrentOffer());

            if (ourPlayerData.getCurrentOffer() != null)
            {
                boolean[] offeredTo = ourPlayerData.getCurrentOffer().getTo();

                for (int i = 0; i < game.maxPlayers; i++)
                {
                    D.ebugPrintln("offerRejections[" + i + "]=" + offerRejections[i]);

                    if (offeredTo[i] && !offerRejections[i])
                        everyoneRejected = false;
                }
            }

            D.ebugPrintln("everyoneRejected=" + everyoneRejected);

            if (everyoneRejected)
            {
                negotiator.addToOffersMade(ourPlayerData.getCurrentOffer());
                client.clearOffer(game);
                waitingForTradeResponse = false;
            }
        }

        ///
        /// consider the offer
        ///
        int ourResponseToOffer = considerOffer(offer);

        D.ebugPrintln("%%% ourResponseToOffer = " + ourResponseToOffer);

        if (ourResponseToOffer < 0)
            return;

        int delayLength = Math.abs(rand.nextInt() % 500) + 3500;
        if (gameIs6Player && ! waitingForTradeResponse)
        {
            delayLength *= 2;  // usually, pause is half-length in 6-player
        }
        pause(delayLength);

        switch (ourResponseToOffer)
        {
        case SOCRobotNegotiator.ACCEPT_OFFER:
            client.acceptOffer(game, offer.getFrom());

            ///
            /// clear our building plan, so that we replan
            ///
            buildingPlan.clear();
            negotiator.setTargetPiece(ourPlayerNumber, null);

            break;

        case SOCRobotNegotiator.REJECT_OFFER:

            if (!waitingForTradeResponse)
                client.rejectOffer(game);

            break;

        case SOCRobotNegotiator.COUNTER_OFFER:

            if (!makeCounterOffer(offer))
                client.rejectOffer(game);

            break;
        }
    }

    /**
     * Handle a REJECTOFFER for this game.
     * watch rejections of other players' offers, and of our offers.
     * @since 1.1.08
     */
    private void handleREJECTOFFER(SOCRejectOffer mes)
    {
        ///
        /// see if everyone has rejected our offer
        ///
        int rejector = mes.getPlayerNumber();

        if ((ourPlayerData.getCurrentOffer() != null) && (waitingForTradeResponse))
        {
            D.ebugPrintln("%%%%%%%%% REJECT OFFER %%%%%%%%%%%%%");

            ///
            /// record which player said no
            ///
            SOCResourceSet getSet = ourPlayerData.getCurrentOffer().getGetSet();

            for (int rsrcType = SOCResourceConstants.CLAY;
                    rsrcType <= SOCResourceConstants.WOOD;
                    rsrcType++)
            {
                if ((getSet.getAmount(rsrcType) > 0) && (!negotiator.wantsAnotherOffer(rejector, rsrcType)))
                    negotiator.markAsNotSelling(rejector, rsrcType);
            }

            offerRejections[mes.getPlayerNumber()] = true;

            boolean everyoneRejected = true;
            D.ebugPrintln("ourPlayerData.getCurrentOffer() = " + ourPlayerData.getCurrentOffer());

            boolean[] offeredTo = ourPlayerData.getCurrentOffer().getTo();

            for (int i = 0; i < game.maxPlayers; i++)
            {
                D.ebugPrintln("offerRejections[" + i + "]=" + offerRejections[i]);

                if (offeredTo[i] && !offerRejections[i])
                    everyoneRejected = false;
            }

            D.ebugPrintln("everyoneRejected=" + everyoneRejected);

            if (everyoneRejected)
            {
                negotiator.addToOffersMade(ourPlayerData.getCurrentOffer());
                client.clearOffer(game);
                waitingForTradeResponse = false;
            }
        }
        else
        {
            ///
            /// we also want to watch rejections of other players' offers
            ///
            D.ebugPrintln("%%%% ALT REJECT OFFER %%%%");

            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                SOCTradeOffer offer = game.getPlayer(pn).getCurrentOffer();

                if (offer != null)
                {
                    boolean[] offeredTo = offer.getTo();

                    if (offeredTo[rejector])
                    {
                        //
                        // I think they were rejecting this offer
                        // mark them as not selling what was asked for
                        //
                        SOCResourceSet getSet = offer.getGetSet();

                        for (int rsrcType = SOCResourceConstants.CLAY;
                                rsrcType <= SOCResourceConstants.WOOD;
                                rsrcType++)
                        {
                            if ((getSet.getAmount(rsrcType) > 0) && (!negotiator.wantsAnotherOffer(rejector, rsrcType)))
                                negotiator.markAsNotSelling(rejector, rsrcType);
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle a DEVCARD for this game.
     * No brain-specific action.
     * @since 1.1.08
     */
    private void handleDEVCARD(SOCDevCard mes)
    {
        SOCDevCardSet plCards = game.getPlayer(mes.getPlayerNumber()).getDevCards();
        final int cardType = mes.getCardType();

        switch (mes.getAction())
        {
        case SOCDevCard.DRAW:
            plCards.add(1, SOCDevCardSet.NEW, cardType);
            break;

        case SOCDevCard.PLAY:
            plCards.subtract(1, SOCDevCardSet.OLD, cardType);
            break;

        case SOCDevCard.ADDOLD:
            plCards.add(1, SOCDevCardSet.OLD, cardType);
            break;

        case SOCDevCard.ADDNEW:
            plCards.add(1, SOCDevCardSet.NEW, cardType);
            break;
        }
    }

    /**
     * Handle a PUTPIECE for this game, by updating {@link SOCPlayerTracker}s.
     * Also handles the "move piece to here" part of MOVEPIECE.
     *<P>
     * For initial placement of our own pieces, this method also checks
     * and clears expectPUTPIECE_FROM_START1A, and sets expectSTART1B, etc.
     * The final initial putpiece clears expectPUTPIECE_FROM_START2B and sets expectPLAY.
     *<P>
     * For initial settlements, won't track here:
     * Delay tracking until the corresponding road is placed,
     * in {@link #handlePUTPIECE_updateGameData(SOCPutPiece)}.
     * This prevents the need for tracker "undo" work if a human
     * player changes their mind on where to place the settlement.
     *
     * @param pn  Piece's player number
     * @param coord  Piece coordinate
     * @param pieceType  Piece type, as in {@link SOCPlayingPiece#SETTLEMENT}
     * @since 1.1.08
     */
    private void handlePUTPIECE_updateTrackers(final int pn, final int coord, final int pieceType)
    {
        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:

            SOCRoad newRoad = new SOCRoad(game.getPlayer(pn), coord, null);
            trackNewRoadOrShip(newRoad, false);
            break;

        case SOCPlayingPiece.SETTLEMENT:

            SOCPlayer newSettlementPl = game.getPlayer(pn);
            SOCSettlement newSettlement = new SOCSettlement(newSettlementPl, coord, null);
            if ((game.getGameState() == SOCGame.START1B) || (game.getGameState() == SOCGame.START2B)
                || (game.getGameState() == SOCGame.START3B))
            {
                // Track it soon, after the road is placed
                // (in handlePUTPIECE_updateGameData)
                // but not yet, in case player cancels placement.
                SOCPlayerTracker tr = playerTrackers.get(Integer.valueOf(newSettlementPl.getPlayerNumber()));
                tr.setPendingInitSettlement(newSettlement);
            }
            else
            {
                // Track it now
                trackNewSettlement(newSettlement, false);
            }
            break;

        case SOCPlayingPiece.CITY:

            SOCCity newCity = new SOCCity(game.getPlayer(pn), coord, null);
            trackNewCity(newCity, false);
            break;

        case SOCPlayingPiece.SHIP:

            SOCShip newShip = new SOCShip(game.getPlayer(pn), coord, null);
            trackNewRoadOrShip(newShip, false);
            break;

        case SOCPlayingPiece.VILLAGE:
            return;  // <--- Early return: Piece is part of board initial layout, not tracked player info ---

        }

        if (D.ebugOn)
        {
            SOCPlayerTracker.playerTrackersDebug(playerTrackers);
        }

        if (pn != ourPlayerNumber)
        {
            return;  // <---- Not our piece ----
        }

        /**
         * Update expect-vars during initial placement of our pieces.
         */

        if (expectPUTPIECE_FROM_START1A && (pieceType == SOCPlayingPiece.SETTLEMENT) && (coord == ourPlayerData.getLastSettlementCoord()))
        {
            expectPUTPIECE_FROM_START1A = false;
            expectSTART1B = true;
        }

        if (expectPUTPIECE_FROM_START1B
            && ((pieceType == SOCPlayingPiece.ROAD) || (pieceType == SOCPlayingPiece.SHIP))
            && (coord == ourPlayerData.getLastRoadCoord()))
        {
            expectPUTPIECE_FROM_START1B = false;
            expectSTART2A = true;
        }

        if (expectPUTPIECE_FROM_START2A && (pieceType == SOCPlayingPiece.SETTLEMENT) && (coord == ourPlayerData.getLastSettlementCoord()))
        {
            expectPUTPIECE_FROM_START2A = false;
            expectSTART2B = true;
        }

        if (expectPUTPIECE_FROM_START2B
            && ((pieceType == SOCPlayingPiece.ROAD) || (pieceType == SOCPlayingPiece.SHIP))
            && (coord == ourPlayerData.getLastRoadCoord()))
        {
            expectPUTPIECE_FROM_START2B = false;
            if (! game.isGameOptionSet(SOCGameOption.K_SC_3IP))
                expectPLAY = true;    // wait for regular game play to start; other players might still place first
            else
                expectSTART3A = true;
        }

        if (expectPUTPIECE_FROM_START3A
            && (pieceType == SOCPlayingPiece.SETTLEMENT)
            && (coord == ourPlayerData.getLastSettlementCoord()))
        {
            expectPUTPIECE_FROM_START3A = false;
            expectSTART3B = true;
        }

        if (expectPUTPIECE_FROM_START3B
            && ((pieceType == SOCPlayingPiece.ROAD) || (pieceType == SOCPlayingPiece.SHIP))
            && (coord == ourPlayerData.getLastRoadCoord()))
        {
            expectPUTPIECE_FROM_START3B = false;
            expectPLAY = true;
        }

    }

    /**
     * Have the client ask to build our top planned piece
     * {@link #buildingPlan}{@link Stack#pop() .pop()},
     * unless we've already been told by the server to not build it.
     * Called from {@link #buildOrGetResourceByTradeOrCard()}.
     *<P>
     * Checks against {@link #whatWeFailedToBuild} to see if server has rejected this already.
     * Calls <tt>client.buyDevCard()</tt> or <tt>client.buildRequest()</tt>.
     * Sets {@link #waitingForDevCard}, or {@link #waitingForGameState} and
     * {@link #expectPLACING_SETTLEMENT} (etc).
     *
     * @since 1.1.08
     */
    private void buildRequestPlannedPiece()
    {
        final SOCPossiblePiece targetPiece = buildingPlan.pop();
        D.ebugPrintln("$ POPPED " + targetPiece);
        lastMove = targetPiece;
        currentDRecorder = (currentDRecorder + 1) % 2;
        negotiator.setTargetPiece(ourPlayerNumber, targetPiece);

        switch (targetPiece.getType())
        {
        case SOCPossiblePiece.CARD:
            client.buyDevCard(game);
            waitingForDevCard = true;

            break;

        case SOCPossiblePiece.ROAD:
            waitingForGameState = true;
            counter = 0;
            expectPLACING_ROAD = true;
            whatWeWantToBuild = new SOCRoad(ourPlayerData, targetPiece.getCoordinates(), null);
            if (! whatWeWantToBuild.equals(whatWeFailedToBuild))
            {
                D.ebugPrintln("!!! BUILD REQUEST FOR A ROAD AT " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                client.buildRequest(game, SOCPlayingPiece.ROAD);
            } else {
                // We already tried to build this.
                cancelWrongPiecePlacementLocal(whatWeWantToBuild);
                // cancel sets whatWeWantToBuild = null;
            }

            break;

        case SOCPlayingPiece.SETTLEMENT:
            waitingForGameState = true;
            counter = 0;
            expectPLACING_SETTLEMENT = true;
            whatWeWantToBuild = new SOCSettlement(ourPlayerData, targetPiece.getCoordinates(), null);
            if (! whatWeWantToBuild.equals(whatWeFailedToBuild))
            {
                D.ebugPrintln("!!! BUILD REQUEST FOR A SETTLEMENT " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                client.buildRequest(game, SOCPlayingPiece.SETTLEMENT);
            } else {
                // We already tried to build this.
                cancelWrongPiecePlacementLocal(whatWeWantToBuild);
                // cancel sets whatWeWantToBuild = null;
            }

            break;

        case SOCPlayingPiece.CITY:
            waitingForGameState = true;
            counter = 0;
            expectPLACING_CITY = true;
            whatWeWantToBuild = new SOCCity(ourPlayerData, targetPiece.getCoordinates(), null);
            if (! whatWeWantToBuild.equals(whatWeFailedToBuild))
            {
                D.ebugPrintln("!!! BUILD REQUEST FOR A CITY " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                client.buildRequest(game, SOCPlayingPiece.CITY);
            } else {
                // We already tried to build this.
                cancelWrongPiecePlacementLocal(whatWeWantToBuild);
                // cancel sets whatWeWantToBuild = null;
            }

            break;

        case SOCPlayingPiece.SHIP:
            waitingForGameState = true;
            counter = 0;
            expectPLACING_SHIP = true;
            whatWeWantToBuild = new SOCShip(ourPlayerData, targetPiece.getCoordinates(), null);
            if (! whatWeWantToBuild.equals(whatWeFailedToBuild))
            {
                System.err.println("L2733: " + ourPlayerData.getName() + ": !!! BUILD REQUEST FOR A SHIP AT " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                D.ebugPrintln("!!! BUILD REQUEST FOR A SHIP AT " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                client.buildRequest(game, SOCPlayingPiece.SHIP);
            } else {
                // We already tried to build this.
                cancelWrongPiecePlacementLocal(whatWeWantToBuild);
                // cancel sets whatWeWantToBuild = null;
            }

            break;

        }
    }

    /**
     * Plan the next building plan and target.
     * Should be called from {@link #run()} under these conditions: <BR>
     * (!expectPLACING_ROBBER && (buildingPlan.empty()) && (ourPlayerData.getResources().getTotal() > 1) && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
     *<P>
     * Sets these fields/actions: <BR>
     *  {@link SOCRobotDM#planStuff(int)} <BR>
     *  {@link #buildingPlan} <BR>
     *  {@link #lastTarget} <BR>
     *  {@link SOCRobotNegotiator#setTargetPiece(int, SOCPossiblePiece)}
     *
     * @since 1.1.08
     */
    private final void planBuilding()
    {
        decisionMaker.planStuff(robotParameters.getStrategyType());

        if (!buildingPlan.empty())
        {
            lastTarget = buildingPlan.peek();
            negotiator.setTargetPiece(ourPlayerNumber, buildingPlan.peek());
        }
    }

    /**
     * Handle a PLAYERELEMENT for this game.
     * Update a player's amount of a resource or a building type.
     *<P>
     * If this during the {@link SOCGame#PLAY} state, then update the
     * {@link SOCRobotNegotiator}'s is-selling flags.
     *<P>
     * If our player is losing a resource needed for the {@link #buildingPlan},
     * clear the plan if this is for the Special Building Phase (on the 6-player board).
     * In normal game play, we clear the building plan at the start of each turn.
     *<P>
     * Otherwise, only the game data is updated, nothing brain-specific.
     *
     * @since 1.1.08
     */
    private void handlePLAYERELEMENT(SOCPlayerElement mes)
    {
        final int pn = mes.getPlayerNumber();
        SOCPlayer pl = (pn != -1) ? game.getPlayer(pn) : null;

        switch (mes.getElementType())
        {
        case SOCPlayerElement.ROADS:

            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (mes, pl, SOCPlayingPiece.ROAD);
            break;

        case SOCPlayerElement.SETTLEMENTS:

            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (mes, pl, SOCPlayingPiece.SETTLEMENT);
            break;

        case SOCPlayerElement.CITIES:

            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (mes, pl, SOCPlayingPiece.CITY);
            break;

        case SOCPlayerElement.SHIPS:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (mes, pl, SOCPlayingPiece.SHIP);
            break;

        case SOCPlayerElement.NUMKNIGHTS:

            // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numKnights
                (mes, pl, game);
            break;

        case SOCPlayerElement.CLAY:

            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.CLAY, "CLAY");
            break;

        case SOCPlayerElement.ORE:

            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.ORE, "ORE");
            break;

        case SOCPlayerElement.SHEEP:

            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.SHEEP, "SHEEP");
            break;

        case SOCPlayerElement.WHEAT:

            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.WHEAT, "WHEAT");
            break;

        case SOCPlayerElement.WOOD:

            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.WOOD, "WOOD");
            break;

        case SOCPlayerElement.UNKNOWN:

            /**
             * Note: if losing unknown resources, we first
             * convert player's known resources to unknown resources,
             * then remove mes's unknown resources from player.
             */
            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.UNKNOWN, "UNKNOWN");
            break;

        case SOCPlayerElement.ASK_SPECIAL_BUILD:
            if (0 != mes.getValue())
            {
                try {
                    game.askSpecialBuild(pn, false);  // set per-player, per-game flags
                }
                catch (RuntimeException e) {}
            } else {
                pl.setAskedSpecialBuild(false);
            }
            break;

        case SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES:
            pl.setNeedToPickGoldHexResources(mes.getValue());
            break;

        case SOCPlayerElement.SCENARIO_SVP:
            pl.setSpecialVP(mes.getValue());
            break;

        case SOCPlayerElement.SCENARIO_PLAYEREVENTS_BITMASK:
            pl.setScenarioPlayerEvents(mes.getValue());
            break;

        case SOCPlayerElement.SCENARIO_SVP_LANDAREAS_BITMASK:
            pl.setScenarioSVPLandAreas(mes.getValue());
            break;

        case SOCPlayerElement.STARTING_LANDAREAS:
            pl.setStartingLandAreasEncoded(mes.getValue());
            break;

        case SOCPlayerElement.SCENARIO_CLOTH_COUNT:
            if (pn != -1)
                pl.setCloth(mes.getValue());
            else
                ((SOCBoardLarge) (game.getBoard())).setCloth(mes.getValue());
            break;

        case SOCPlayerElement.SCENARIO_WARSHIP_COUNT:
            switch (mes.getAction())
            {
            case SOCPlayerElement.SET:
                pl.setNumWarships(mes.getValue());
                break;

            case SOCPlayerElement.GAIN:
                pl.setNumWarships(pl.getNumWarships() + mes.getValue());
                break;
            }
            break;

        }

        ///
        /// if this during the PLAY state, then update the is selling flags
        ///
        if (game.getGameState() == SOCGame.PLAY)
        {
            negotiator.resetIsSelling();
        }
    }

    /**
     * Update a player's amount of a resource.
     *<ul>
     *<LI> If this is a {@link SOCPlayerElement#LOSE} action, and the player does not have enough of that type,
     *     the rest are taken from the player's UNKNOWN amount.
     *<LI> If we are losing from type UNKNOWN,
     *     first convert player's known resources to unknown resources
     *     (individual amount information will be lost),
     *     then remove mes's unknown resources from player.
     *<LI> If this is a SET action, and it's for our own robot player,
     *     check the amount against {@link #ourPlayerData}, and debug print
     *     if they don't match already.
     *</ul>
     *<P>
     * If our player is losing a resource needed for the {@link #buildingPlan},
     * clear the plan if this is for the Special Building Phase (on the 6-player board).
     * In normal game play, we clear the building plan at the start of each turn.
     *<P>
     *
     * @param mes      Message with amount and action (SET/GAIN/LOSE)
     * @param pl       Player to update
     * @param rtype    Type of resource, like {@link SOCResourceConstants#CLAY}
     * @param rtypeStr Resource type name, for debugging
     */
    protected void handlePLAYERELEMENT_numRsrc
        (SOCPlayerElement mes, SOCPlayer pl, int rtype, String rtypeStr)
    {
        /**
         * for SET, check the amount of unknown resources against
         * what we think we know about our player.
         */
        if (D.ebugOn && (pl == ourPlayerData) && (mes.getAction() == SOCPlayerElement.SET))
        {
            if (mes.getValue() != ourPlayerData.getResources().getAmount(rtype))
            {
                client.sendText(game, ">>> RSRC ERROR FOR " + rtypeStr
                    + ": " + mes.getValue() + " != " + ourPlayerData.getResources().getAmount(rtype));
            }
        }

        /**
         * Update game data.
         */
        SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
            (mes, pl, rtype);

        /**
         * Clear building plan, if we just lost a resource we need.
         * Only necessary for Special Building Phase (6-player board),
         * because in normal game play, we clear the building plan
         * at the start of each turn.
         */
        if (waitingForSpecialBuild && (pl == ourPlayerData)
            && (mes.getAction() != SOCPlayerElement.GAIN)
            && ! buildingPlan.isEmpty())
        {
            final SOCPossiblePiece targetPiece = buildingPlan.peek();
            final SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

            if (! ourPlayerData.getResources().contains(targetResources))
            {
                buildingPlan.clear();

                // The buildingPlan is clear, so we'll calculate
                // a new plan when our Special Building turn begins.
                // Don't clear decidedIfSpecialBuild flag, to prevent
                // needless plan calculation before our turn begins,
                // especially from multiple PLAYERELEMENT(LOSE),
                // as may happen for a discard.
            }
        }

    }

    /**
     * Run a newly placed settlement through the playerTrackers.
     *<P>
     * During initial board setup, settlements aren't tracked when placed.
     * They are deferred until their corresponding road placement, in case
     * a human player decides to cancel their settlement and place it elsewhere.
     *
     * During normal play, the settlements are tracked immediately when placed.
     *
     * (Code previously in body of the run method.)
     * Placing the code in its own method allows tracking that settlement when the
     * road's putPiece message arrives.
     *
     * @param newSettlement The newly placed settlement for the playerTrackers
     * @param isCancel Is this our own robot's settlement placement, rejected by the server?
     *     If so, this method call will cancel its placement within the game data / robot data.
     */
    protected void trackNewSettlement(SOCSettlement newSettlement, final boolean isCancel)
    {
        Iterator<SOCPlayerTracker> trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();
            if (! isCancel)
                tracker.addNewSettlement(newSettlement, playerTrackers);
            else
                tracker.cancelWrongSettlement(newSettlement);
        }

        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();
            Iterator<SOCPossibleRoad> posRoadsIter = tracker.getPossibleRoads().values().iterator();

            while (posRoadsIter.hasNext())
            {
                posRoadsIter.next().clearThreats();
            }

            Iterator<SOCPossibleSettlement> posSetsIter = tracker.getPossibleSettlements().values().iterator();

            while (posSetsIter.hasNext())
            {
                posSetsIter.next().clearThreats();
            }
        }

        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();
            tracker.updateThreats(playerTrackers);
        }

        if (isCancel)
        {
            return;  // <--- Early return, nothing else to do ---
        }

        ///
        /// see if this settlement bisected someone else's road
        ///
        int[] roadCount = { 0, 0, 0, 0, 0, 0 };  // Length should be SOCGame.MAXPLAYERS
        SOCBoard board = game.getBoard();
        Enumeration<Integer> adjEdgeEnum = board.getAdjacentEdgesToNode(newSettlement.getCoordinates()).elements();

        while (adjEdgeEnum.hasMoreElements())
        {
            final int adjEdge = adjEdgeEnum.nextElement().intValue();
            Enumeration<SOCRoad> roadEnum = board.getRoads().elements();

            while (roadEnum.hasMoreElements())
            {
                SOCRoad road = roadEnum.nextElement();

                if (road.getCoordinates() == adjEdge)
                {
                    final int roadPN = road.getPlayerNumber();

                    roadCount[roadPN]++;

                    if (roadCount[roadPN] == 2)
                    {
                        if (roadPN != ourPlayerNumber)
                        {
                            ///
                            /// this settlement bisects another players road
                            ///
                            trackersIter = playerTrackers.values().iterator();

                            while (trackersIter.hasNext())
                            {
                                SOCPlayerTracker tracker = trackersIter.next();

                                if (tracker.getPlayer().getPlayerNumber() == roadPN)
                                {
                                    //D.ebugPrintln("$$ updating LR Value for player "+tracker.getPlayer().getPlayerNumber());
                                    //tracker.updateLRValues();
                                }

                                //tracker.recalcLongestRoadETA();
                            }
                        }

                        break;
                    }
                }
            }
        }

        final int pNum = newSettlement.getPlayerNumber();

        ///
        /// update the speedups from possible settlements
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == pNum)
            {
                Iterator<SOCPossibleSettlement> posSetsIter = tracker.getPossibleSettlements().values().iterator();

                while (posSetsIter.hasNext())
                {
                    posSetsIter.next().updateSpeedup();
                }

                break;
            }
        }

        ///
        /// update the speedups from possible cities
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == pNum)
            {
                Iterator<SOCPossibleCity> posCitiesIter = tracker.getPossibleCities().values().iterator();

                while (posCitiesIter.hasNext())
                {
                    posCitiesIter.next().updateSpeedup();
                }

                break;
            }
        }
    }

    /**
     * Run a newly placed city through the PlayerTrackers.
     * @param newCity  The newly placed city
     * @param isCancel Is this our own robot's city placement, rejected by the server?
     *     If so, this method call will cancel its placement within the game data / robot data.
     */
    private void trackNewCity(final SOCCity newCity, final boolean isCancel)
    {
        final int newCityPN = newCity.getPlayerNumber();

        Iterator<SOCPlayerTracker> trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == newCityPN)
            {
                if (! isCancel)
                    tracker.addOurNewCity(newCity);
                else
                    tracker.cancelWrongCity(newCity);

                break;
            }
        }

        if (isCancel)
        {
            return;  // <--- Early return, nothing else to do ---
        }

        ///
        /// update the speedups from possible settlements
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == newCityPN)
            {
                Iterator<SOCPossibleSettlement> posSetsIter = tracker.getPossibleSettlements().values().iterator();

                while (posSetsIter.hasNext())
                {
                    posSetsIter.next().updateSpeedup();
                }

                break;
            }
        }

        ///
        /// update the speedups from possible cities
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == newCityPN)
            {
                Iterator<SOCPossibleCity> posCitiesIter = tracker.getPossibleCities().values().iterator();

                while (posCitiesIter.hasNext())
                {
                    posCitiesIter.next().updateSpeedup();
                }

                break;
            }
        }
    }

    /**
     * Run a newly placed road or ship through the playerTrackers.
     *
     * @param newRoad  The newly placed road or ship
     * @param isCancel Is this our own robot's placement, rejected by the server?
     *     If so, this method call will cancel its placement within the game data / robot data.
     */
    protected void trackNewRoadOrShip(final SOCRoad newRoad, final boolean isCancel)
    {
        final int newRoadPN = newRoad.getPlayerNumber();

        Iterator<SOCPlayerTracker> trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();
            tracker.takeMonitor();

            try
            {
                if (! isCancel)
                    tracker.addNewRoadOrShip(newRoad, playerTrackers);
                else
                    tracker.cancelWrongRoadOrShip(newRoad);
            }
            catch (Exception e)
            {
                tracker.releaseMonitor();
                if (alive)
                {
                    System.out.println("Exception caught - " + e);
                    e.printStackTrace();
                }
            }

            tracker.releaseMonitor();
        }

        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();
            tracker.takeMonitor();

            try
            {
                Iterator<SOCPossibleRoad> posRoadsIter = tracker.getPossibleRoads().values().iterator();

                while (posRoadsIter.hasNext())
                {
                    posRoadsIter.next().clearThreats();
                }

                Iterator<SOCPossibleSettlement> posSetsIter = tracker.getPossibleSettlements().values().iterator();

                while (posSetsIter.hasNext())
                {
                    posSetsIter.next().clearThreats();
                }
            }
            catch (Exception e)
            {
                tracker.releaseMonitor();
                if (alive)
                {
                    System.out.println("Exception caught - " + e);
                    e.printStackTrace();
                }
            }

            tracker.releaseMonitor();
        }

        ///
        /// update LR values and ETA
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();
            tracker.updateThreats(playerTrackers);
            tracker.takeMonitor();

            try
            {
                if (tracker.getPlayer().getPlayerNumber() == newRoadPN)
                {
                    //D.ebugPrintln("$$ updating LR Value for player "+tracker.getPlayer().getPlayerNumber());
                    //tracker.updateLRValues();
                }

                //tracker.recalcLongestRoadETA();
            }
            catch (Exception e)
            {
                tracker.releaseMonitor();
                if (alive)
                {
                    System.out.println("Exception caught - " + e);
                    e.printStackTrace();
                }
            }

            tracker.releaseMonitor();
        }
    }

    /**
     *  We've asked for an illegal piece placement.
     *  Cancel and invalidate this planned piece, make a new plan.
     *  If {@link SOCGame#isSpecialBuilding()}, will set variables to
     *  force the end of our special building turn.
     *  Also handles illegal requests to buy development cards
     *  (piece type -2 in {@link SOCCancelBuildRequest}).
     *<P>
     *  This method increments {@link #failedBuildingAttempts},
     *  but won't leave the game if we've failed too many times.
     *  The brain's run loop should make that decision.
     *
     * @param mes Cancelmessage from server, including piece type
     */
    protected void cancelWrongPiecePlacement(SOCCancelBuildRequest mes)
    {
        final boolean cancelBuyDevCard = (mes.getPieceType() == SOCPossiblePiece.CARD);  // == -2
        if (cancelBuyDevCard)
        {
            waitingForDevCard = false;
        } else {
            whatWeFailedToBuild = whatWeWantToBuild;
            ++failedBuildingAttempts;
        }

        final int gameState = game.getGameState();

        /**
         * if true, server likely denied us due to resources, not due to building plan
         * being interrupted by another player's building before our special building phase.
         * (Could also be due to a bug in the chosen building plan.)
         */
        final boolean gameStateIsPLAY1 = (gameState == SOCGame.PLAY1);

        if (! (gameStateIsPLAY1 || cancelBuyDevCard))
        {
            int coord = -1;
            switch (gameState)
            {
            case SOCGame.START1A:
            case SOCGame.START1B:
            case SOCGame.START2A:
            case SOCGame.START2B:
            case SOCGame.START3A:
            case SOCGame.START3B:
                coord = lastStartingPieceCoord;
                break;

            default:
                if (whatWeWantToBuild != null)
                    coord = whatWeWantToBuild.getCoordinates();
            }
            if (coord != -1)
            {
                SOCPlayingPiece cancelPiece;

                /**
                 * First, invalidate that piece in trackers, so we don't try again to
                 * build it. If we treat it like another player's new placement, we
                 * can remove any of our planned pieces depending on this one.
                 */
                switch (mes.getPieceType())
                {
                case SOCPlayingPiece.ROAD:
                    cancelPiece = new SOCRoad(dummyCancelPlayerData, coord, null);
                    break;

                case SOCPlayingPiece.SETTLEMENT:
                    cancelPiece = new SOCSettlement(dummyCancelPlayerData, coord, null);
                    break;

                case SOCPlayingPiece.CITY:
                    cancelPiece = new SOCCity(dummyCancelPlayerData, coord, null);
                    break;

                case SOCPlayingPiece.SHIP:
                    cancelPiece = new SOCShip(dummyCancelPlayerData, coord, null);
                    break;

                default:
                    cancelPiece = null;  // To satisfy javac
                }

                cancelWrongPiecePlacementLocal(cancelPiece);
            }
        } else {
            /**
             *  stop trying to build it now, but don't prevent
             *  us from trying later to build it.
             */
            whatWeWantToBuild = null;
            buildingPlan.clear();
            waitingForGameState = false;
        }

        /**
         * we've invalidated that piece in trackers.
         * - clear whatWeWantToBuild, buildingPlan
         * - set expectPLAY1, waitingForGameState
         * - reset counter = 0
         * - send CANCEL _to_ server, so all players get PLAYERELEMENT & GAMESTATE(PLAY1) messages.
         * - wait for the play1 message, then can re-plan another piece.
         * - update javadoc of this method (TODO)
         */

        if (gameStateIsPLAY1 || game.isSpecialBuilding())
        {
            // Shouldn't have asked to build this piece at this time.
            // End our confusion by ending our current turn. Can re-plan on next turn.
            failedBuildingAttempts = MAX_DENIED_BUILDING_PER_TURN;
            expectPLACING_ROAD = false;
            expectPLACING_SETTLEMENT = false;
            expectPLACING_CITY = false;
            expectPLACING_SHIP = false;
            decidedIfSpecialBuild = true;
            if (cancelBuyDevCard)
            {
                waitingForGameState = false;  // don't wait for PLACING_ after buy dev card
            } else {
                // special building, currently in state PLACING_* ;
                // get our resources back, get state PLAY1 or SPECIALBUILD
                waitingForGameState = true;
                client.cancelBuildRequest(game, mes.getPieceType());
            }
        }
        else if (gameState <= SOCGame.START3B)
        {
            switch (gameState)
            {
            case SOCGame.START1A:
                expectPUTPIECE_FROM_START1A = false;
                expectSTART1A = true;
                break;

            case SOCGame.START1B:
                expectPUTPIECE_FROM_START1B = false;
                expectSTART1B = true;
                break;

            case SOCGame.START2A:
                expectPUTPIECE_FROM_START2A = false;
                expectSTART2A = true;
                break;

            case SOCGame.START2B:
                expectPUTPIECE_FROM_START2B = false;
                expectSTART2B = true;
                break;

            case SOCGame.START3A:
                expectPUTPIECE_FROM_START3A = false;
                expectSTART3A = true;
                break;

            case SOCGame.START3B:
                expectPUTPIECE_FROM_START3B = false;
                expectSTART3B = true;
                break;
            }
            waitingForGameState = false;
            // The run loop will check if failedBuildingAttempts > (2 * MAX_DENIED_BUILDING_PER_TURN).
            // This bot will leave the game there if it can't recover.
        } else {
            expectPLAY1 = true;
            waitingForGameState = true;
            counter = 0;
            client.cancelBuildRequest(game, mes.getPieceType());
            // Now wait for the play1 message, then can re-plan another piece.
        }
    }

    /**
     * Remove our incorrect piece placement, it's been rejected by the server.
     * Take this piece out of trackers, without sending any response back to the server.
     *<P>
     * This method invalidates that piece in trackers, so we don't try again to
     * build it. Since we treat it like another player's new placement, we
     * can remove any of our planned pieces depending on this one.
     *<P>
     * Also calls {@link SOCPlayer#clearPotentialSettlement(int)},
     * clearPotentialRoad, or clearPotentialCity.
     *
     * @param cancelPiece Type and coordinates of the piece to cancel; null is allowed but not very useful.
     */
    protected void cancelWrongPiecePlacementLocal(SOCPlayingPiece cancelPiece)
    {
        if (cancelPiece != null)
        {
            final int coord = cancelPiece.getCoordinates();

            switch (cancelPiece.getType())
            {
            case SOCPlayingPiece.SHIP:  // fall through to ROAD
            case SOCPlayingPiece.ROAD:
                trackNewRoadOrShip((SOCRoad) cancelPiece, true);
                if (cancelPiece.getType() == SOCPlayingPiece.ROAD)
                    ourPlayerData.clearPotentialRoad(coord);
                else
                    ourPlayerData.clearPotentialShip(coord);
                if (game.getGameState() <= SOCGame.START3B)
                {
                    // needed for placeInitRoad() calculations
                    ourPlayerData.clearPotentialSettlement(lastStartingRoadTowardsNode);
                }
                break;

            case SOCPlayingPiece.SETTLEMENT:
                trackNewSettlement((SOCSettlement) cancelPiece, true);
                ourPlayerData.clearPotentialSettlement(coord);
                break;

            case SOCPlayingPiece.CITY:
                trackNewCity((SOCCity) cancelPiece, true);
                ourPlayerData.clearPotentialCity(coord);
                break;
            }
        }

        whatWeWantToBuild = null;
        buildingPlan.clear();
    }

    /**
     * kill this brain
     */
    public void kill()
    {
        alive = false;

        try
        {
            gameEventQ.put(null);
        }
        catch (Exception exc) {}
    }

    /**
     * pause for a bit.
     *<P>
     * In a 6-player game, pause only 75% as long, to shorten the overall game delay,
     * except if {@link #waitingForTradeResponse}.
     * This is indicated by the {@link #pauseFaster} flag.
     *
     * @param msec  number of milliseconds to pause
     */
    public void pause(int msec)
    {
        if (pauseFaster && ! waitingForTradeResponse)
            msec = (msec / 2) + (msec / 4);

        try
        {
            yield();
            sleep(msec);
        }
        catch (InterruptedException exc) {}
    }

    /**
     * place planned first settlement
     * @param firstSettlement  First settlement's node coordinate
     * @see #placeInitSettlement(int)
     */
    protected void placeFirstSettlement(final int firstSettlement)
    {
        //D.ebugPrintln("BUILD REQUEST FOR SETTLEMENT AT "+Integer.toHexString(firstSettlement));
        pause(500);
        lastStartingPieceCoord = firstSettlement;
        client.putPiece(game, new SOCSettlement(ourPlayerData, firstSettlement, null));
        pause(1000);
    }

    /**
     * Place planned initial settlement after first one.
     * @param initSettlement  Second or third settlement's node coordinate,
     *   from {@link OpeningBuildStrategy#planSecondSettlement()} or
     *   from {@link OpeningBuildStrategy#planThirdSettlement()};
     *   should not be -1
     * @see #placeFirstSettlement(int)
     */
    protected void placeInitSettlement(final int initSettlement)
    {
        if (initSettlement == -1)
        {
            // This could mean that the server (incorrectly) asked us to
            // place another second settlement, after we've cleared the
            // potentialSettlements contents.
            System.err.println("robot assert failed: initSettlement -1, " + ourPlayerData.getName() + " leaving game " + game.getName());
            failedBuildingAttempts = 2 + (2 * MAX_DENIED_BUILDING_PER_TURN);
            waitingForGameState = false;
            return;
        }

        //D.ebugPrintln("BUILD REQUEST FOR SETTLEMENT AT "+Integer.toHexString(secondSettlement));
        pause(500);
        lastStartingPieceCoord = initSettlement;
        client.putPiece(game, new SOCSettlement(ourPlayerData, initSettlement, null));
        pause(1000);
    }

    /**
     * Plan and place a road attached to our most recently placed initial settlement,
     * in game states {@link SOCGame#START1B START1B}, {@link SOCGame#START2B START2B}, {@link SOCGame#START3B START3B}.
     * Calls {@link OpeningBuildStrategy#planInitRoad()}.
     *<P>
     * Road choice is based on the best nearby potential settlements, and doesn't
     * directly check {@link SOCPlayer#isPotentialRoad(int) ourPlayerData.isPotentialRoad(edgeCoord)}.
     * If the server rejects our road choice, then {@link #cancelWrongPiecePlacementLocal(SOCPlayingPiece)}
     * will need to know which settlement node we were aiming for,
     * and call {@link SOCPlayer#clearPotentialSettlement(int) ourPlayerData.clearPotentialSettlement(nodeCoord)}.
     * The {@link #lastStartingRoadTowardsNode} field holds this coordinate.
     */
    protected void planAndPlaceInitRoad()
    {
        // TODO handle ships here

        final int roadEdge = openingBuildStrategy.planInitRoad();

        //D.ebugPrintln("!!! PUTTING INIT ROAD !!!");
        pause(500);

        //D.ebugPrintln("Trying to build a road at "+Integer.toHexString(roadEdge));
        lastStartingPieceCoord = roadEdge;
        lastStartingRoadTowardsNode = openingBuildStrategy.getPlannedInitRoadDestinationNode();
        client.putPiece(game, new SOCRoad(ourPlayerData, roadEdge, null));
        pause(1000);
    }

    /**
     * move the robber
     */
    protected void moveRobber()
    {
        final int bestHex = RobberStrategy.getBestRobberHex(game, ourPlayerData, playerTrackers, rand);
        D.ebugPrintln("!!! MOVING ROBBER !!!");
        client.moveRobber(game, ourPlayerData, bestHex);
        pause(2000);
    }

    /**
     * Respond to server's request to pick resources to gain from the Gold Hex.
     * Use {@link #buildingPlan} or, if that's empty (initial placement),
     * pick what's rare in {@link #resourceEstimates}.
     * @param numChoose  Number of resources to pick
     * @since 2.0.00
     */
    protected void pickFreeResources(int numChoose)
    {
        SOCResourceSet targetResources;

        // try to make a plan if we don't have one
        if (buildingPlan.isEmpty())
        {
            planBuilding();
        }

        if (! buildingPlan.isEmpty())
        {
            targetResources = SOCPlayingPiece.getResourcesToBuild(buildingPlan.peek().getType());
            chooseFreeResourcesIfNeeded(targetResources, numChoose, true);
        } else {
            // Pick based on board dice-roll rarities.
            // TODO: After initial placement, consider based on our
            // number probabilities based on settlements/cities placed.
            //  (BSE.getRollsForResourcesSorted)

            resourceChoices.clear();
            final int[] resourceEstimates = openingBuildStrategy.estimateResourceRarity();
            int numEach = 0;  // in case we pick 5, keep going for 6-10
            while (numChoose > 0)
            {
                int res = -1, pct = Integer.MAX_VALUE;
                for (int i = SOCBoard.CLAY_HEX; i <= SOCBoard.WOOD_HEX; ++i)
                {
                    if ((resourceEstimates[i] < pct) && (resourceChoices.getAmount(i) < numEach))
                    {
                        res = i;
                        pct = resourceEstimates[i];
                    }
                }
                if (res != -1)
                {
                    resourceChoices.add(1, res);
                    --numChoose;
                } else {
                    ++numEach;  // has chosen all 5 by now
                }
            }
        }

        client.pickFreeResources(game, resourceChoices);
    }

    /**
     * do some trading -- this method is obsolete and not called.
     * Instead see {@link #makeOffer(SOCPossiblePiece)}, {@link #considerOffer(SOCTradeOffer)},
     * etc, and the javadoc for {@link #negotiator}.
     */
    protected void tradeStuff()
    {
        /**
         * make a tree of all the possible trades that we can
         * make with the bank or ports
         */
        SOCTradeTree treeRoot = new SOCTradeTree(ourPlayerData.getResources(), (SOCTradeTree) null);
        Hashtable<SOCResourceSet, SOCTradeTree> treeNodes = new Hashtable<SOCResourceSet, SOCTradeTree>();
        treeNodes.put(treeRoot.getResourceSet(), treeRoot);

        Queue<SOCTradeTree> queue = new Queue<SOCTradeTree>();
        queue.put(treeRoot);

        while (!queue.empty())
        {
            SOCTradeTree currentTreeNode = queue.get();

            //D.ebugPrintln("%%% Expanding "+currentTreeNode.getResourceSet());
            expandTradeTreeNode(currentTreeNode, treeNodes);

            Enumeration<SOCTradeTree> childrenEnum = currentTreeNode.getChildren().elements();

            while (childrenEnum.hasMoreElements())
            {
                SOCTradeTree child = childrenEnum.nextElement();

                //D.ebugPrintln("%%% Child "+child.getResourceSet());
                if (child.needsToBeExpanded())
                {
                    /**
                     * make a new table entry
                     */
                    treeNodes.put(child.getResourceSet(), child);
                    queue.put(child);
                }
            }
        }

        /**
         * find the best trade result and then perform the trades
         */
        SOCResourceSet bestTradeOutcome = null;
        int bestTradeScore = -1;
        Enumeration<SOCResourceSet> possibleTrades = treeNodes.keys();

        while (possibleTrades.hasMoreElements())
        {
            SOCResourceSet possibleTradeOutcome = possibleTrades.nextElement();

            //D.ebugPrintln("%%% "+possibleTradeOutcome);
            int score = scoreTradeOutcome(possibleTradeOutcome);

            if (score > bestTradeScore)
            {
                bestTradeOutcome = possibleTradeOutcome;
                bestTradeScore = score;
            }
        }

        /**
         * find the trade outcome in the tree, then follow
         * the chain of parents until you get to the root
         * all the while pushing the outcomes onto a stack.
         * then pop outcomes off of the stack and perfoem
         * the trade to get each outcome
         */
        Stack<SOCTradeTree> stack = new Stack<SOCTradeTree>();
        SOCTradeTree cursor = treeNodes.get(bestTradeOutcome);

        while (cursor != treeRoot)
        {
            stack.push(cursor);
            cursor = cursor.getParent();
        }

        SOCResourceSet give = new SOCResourceSet();
        SOCResourceSet get = new SOCResourceSet();
        SOCTradeTree currTreeNode;
        SOCTradeTree prevTreeNode;
        prevTreeNode = treeRoot;

        while (!stack.empty())
        {
            currTreeNode = stack.pop();
            give.setAmounts(prevTreeNode.getResourceSet());
            give.subtract(currTreeNode.getResourceSet());
            get.setAmounts(currTreeNode.getResourceSet());
            get.subtract(prevTreeNode.getResourceSet());

            /**
             * get rid of the negative numbers
             */
            for (int rt = SOCResourceConstants.CLAY;
                    rt <= SOCResourceConstants.WOOD; rt++)
            {
                if (give.getAmount(rt) < 0)
                {
                    give.setAmount(0, rt);
                }

                if (get.getAmount(rt) < 0)
                {
                    get.setAmount(0, rt);
                }
            }

            //D.ebugPrintln("Making bank trade:");
            //D.ebugPrintln("give: "+give);
            //D.ebugPrintln("get: "+get);
            client.bankTrade(game, give, get);
            pause(2000);
            prevTreeNode = currTreeNode;
        }
    }

    /**
     * expand a trade tree node
     *
     * @param currentTreeNode   the tree node that we're expanding
     * @param table  the table of all of the nodes in the tree except this one
     */
    protected void expandTradeTreeNode(SOCTradeTree currentTreeNode, Hashtable<SOCResourceSet,SOCTradeTree> table)
    {
        /**
         * the resources that we have to work with
         */
        SOCResourceSet rSet = currentTreeNode.getResourceSet();

        /**
         * go through the resources one by one, and generate all possible
         * resource sets that result from trading that type of resource
         */
        for (int giveResource = SOCResourceConstants.CLAY;
                giveResource <= SOCResourceConstants.WOOD; giveResource++)
        {
            /**
             * find the ratio at which we can trade
             */
            int tradeRatio;

            if (ourPlayerData.getPortFlag(giveResource))
            {
                tradeRatio = 2;
            }
            else if (ourPlayerData.getPortFlag(SOCBoard.MISC_PORT))
            {
                tradeRatio = 3;
            }
            else
            {
                tradeRatio = 4;
            }

            /**
             * make sure we have enough resources to trade
             */
            if (rSet.getAmount(giveResource) >= tradeRatio)
            {
                /**
                 * trade the resource that we're looking at for one
                 * of every other resource
                 */
                for (int getResource = SOCResourceConstants.CLAY;
                        getResource <= SOCResourceConstants.WOOD;
                        getResource++)
                {
                    if (getResource != giveResource)
                    {
                        SOCResourceSet newTradeResult = rSet.copy();
                        newTradeResult.subtract(tradeRatio, giveResource);
                        newTradeResult.add(1, getResource);

                        SOCTradeTree newTree = new SOCTradeTree(newTradeResult, currentTreeNode);

                        /**
                         * if the trade results in a set of resources that is
                         * equal to or worse than a trade we've already seen,
                         * then we don't want to expand this tree node
                         */
                        Enumeration<SOCResourceSet> tableEnum = table.keys();

                        while (tableEnum.hasMoreElements())
                        {
                            SOCResourceSet oldTradeResult = tableEnum.nextElement();

                            /*
                               //D.ebugPrintln("%%%     "+newTradeResult);
                               //D.ebugPrintln("%%%  <= "+oldTradeResult+" : "+
                               SOCResourceSet.lte(newTradeResult, oldTradeResult));
                             */
                            if (SOCResourceSet.lte(newTradeResult, oldTradeResult))
                            {
                                newTree.setNeedsToBeExpanded(false);

                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * evaluate a trade outcome by calculating how much you could build with it
     *
     * @param tradeOutcome  a set of resources that would be the result of trading
     */
    protected int scoreTradeOutcome(SOCResourceSet tradeOutcome)
    {
        int score = 0;
        SOCResourceSet tempTO = tradeOutcome.copy();

        if ((ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) >= 1) && (ourPlayerData.hasPotentialSettlement()))
        {
            while (tempTO.contains(SOCGame.SETTLEMENT_SET))
            {
                score += 2;
                tempTO.subtract(SOCGame.SETTLEMENT_SET);
            }
        }

        if ((ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 1) && (ourPlayerData.hasPotentialRoad()))
        {
            while (tempTO.contains(SOCGame.ROAD_SET))
            {
                score += 1;
                tempTO.subtract(SOCGame.ROAD_SET);
            }
        }

        if ((ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) >= 1) && (ourPlayerData.hasPotentialCity()))
        {
            while (tempTO.contains(SOCGame.CITY_SET))
            {
                score += 2;
                tempTO.subtract(SOCGame.CITY_SET);
            }
        }

        //D.ebugPrintln("Score for "+tradeOutcome+" : "+score);
        return score;
    }

    /**
     * Make bank trades or port trades to get the target resources, if possible.
     *
     * @param targetResources  the resources that we want
     * @return true if we sent a request to trade, false if
     *     we already have the resources or if we don't have
     *     enough to trade in for <tt>targetResources</tt>.
     */
    protected boolean tradeToTarget2(SOCResourceSet targetResources)
    {
        if (ourPlayerData.getResources().contains(targetResources))
        {
            return false;
        }

        SOCTradeOffer bankTrade = negotiator.getOfferToBank(targetResources, ourPlayerData.getResources());

        if ((bankTrade != null) && (ourPlayerData.getResources().contains(bankTrade.getGiveSet())))
        {
            client.bankTrade(game, bankTrade.getGiveSet(), bankTrade.getGetSet());
            pause(2000);

            return true;
        }

        return false;
    }

    /**
     * Consider a trade offer made by another player.
     *
     * @param offer  the offer to consider
     * @return a code that represents how we want to respond.
     *      Note: a negative result means we do nothing
     * @see #makeCounterOffer(SOCTradeOffer)
     */
    protected int considerOffer(SOCTradeOffer offer)
    {
        int response = -1;

        SOCPlayer offeringPlayer = game.getPlayer(offer.getFrom());

        if ((offeringPlayer.getCurrentOffer() != null) && (offer == offeringPlayer.getCurrentOffer()))
        {
            boolean[] offeredTo = offer.getTo();

            if (offeredTo[ourPlayerNumber])
            {
                response = negotiator.considerOffer2(offer, ourPlayerNumber);
            }
        }

        return response;
    }

    /**
     * Make a trade offer to another player, or decide to make no offer.
     * Calls {@link SOCRobotNegotiator#makeOffer(SOCPossiblePiece)}.
     * Will set either {@link #waitingForTradeResponse} or {@link #doneTrading},
     * and update {@link #ourPlayerData}.{@link SOCPlayer#setCurrentOffer(SOCTradeOffer) setCurrentOffer()},
     *
     * @param target  the resources that we want
     * @return true if we made an offer
     */
    protected boolean makeOffer(SOCPossiblePiece target)
    {
        boolean result = false;
        SOCTradeOffer offer = negotiator.makeOffer(target);
        ourPlayerData.setCurrentOffer(offer);
        negotiator.resetWantsAnotherOffer();

        if (offer != null)
        {
            ///
            ///  reset the offerRejections flag
            ///
            for (int i = 0; i < game.maxPlayers; i++)
            {
                offerRejections[i] = false;
            }

            waitingForTradeResponse = true;
            counter = 0;
            client.offerTrade(game, offer);
            result = true;
        }
        else
        {
            doneTrading = true;
            waitingForTradeResponse = false;
        }

        return result;
    }

    /**
     * make a counter offer to another player
     *
     * @param offer their offer
     * @return true if we made an offer
     */
    protected boolean makeCounterOffer(SOCTradeOffer offer)
    {
        boolean result = false;
        SOCTradeOffer counterOffer = negotiator.makeCounterOffer(offer);
        ourPlayerData.setCurrentOffer(counterOffer);

        if (counterOffer != null)
        {
            ///
            ///  reset the offerRejections flag
            ///
            offerRejections[offer.getFrom()] = false;
            waitingForTradeResponse = true;
            counter = 0;
            client.offerTrade(game, counterOffer);
            result = true;
        }
        else
        {
            doneTrading = true;
            waitingForTradeResponse = false;
        }

        return result;
    }

    /**
     * Choose the resources we need most, for playing a Discovery development card
     * or when a Gold Hex number is rolled.
     * Find the most needed resource by looking at
     * which of the resources we still need takes the
     * longest to acquire, then add to {@link #resourceChoices}.
     * Looks at our player's current resources.
     * @param targetResources  Resources needed to build our next planned piece,
     *             from {@link SOCPlayingPiece#getResourcesToBuild(int)}
     * @param numChoose  Number of resources to choose
     * @param clearResChoices  If true, clear {@link #resourceChoices} before choosing what to add to it;
     *             set false if calling several times to iteratively build up a big choice.
     * @return  True if we could choose <tt>numChoose</tt> resources towards <tt>targetResources</tt>,
     *             false if we could fully satisfy <tt>targetResources</tt>
     *             from our current resources + less than <tt>numChoose</tt> more.
     *             Examine {@link #resourceChoices}{@link SOCResourceSet#getTotal() .getTotal()}
     *             to see how many were chosen.
     */
    protected boolean chooseFreeResources
        (final SOCResourceSet targetResources, final int numChoose, final boolean clearResChoices)
    {
        /**
         * clear our resource choices
         */
        if (clearResChoices)
            resourceChoices.clear();

        /**
         * find the most needed resource by looking at
         * which of the resources we still need takes the
         * longest to acquire
         */
        SOCResourceSet rsCopy = ourPlayerData.getResources().copy();
        SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());
        int[] rollsPerResource = estimate.getRollsPerResource();

        for (int resourceCount = 0; resourceCount < numChoose; resourceCount++)
        {
            int mostNeededResource = -1;

            for (int resource = SOCResourceConstants.CLAY;
                    resource <= SOCResourceConstants.WOOD; resource++)
            {
                if (rsCopy.getAmount(resource) < targetResources.getAmount(resource))
                {
                    if (mostNeededResource < 0)
                    {
                        mostNeededResource = resource;
                    }
                    else
                    {
                        if (rollsPerResource[resource] > rollsPerResource[mostNeededResource])
                        {
                            mostNeededResource = resource;
                        }
                    }
                }
            }

            if (mostNeededResource == -1)
                return false;  // <--- Early return: couldn't choose enough ---

            resourceChoices.add(1, mostNeededResource);
            rsCopy.add(1, mostNeededResource);
        }

        return true;
    }

    /**
     * Do we need to acquire at least <tt>numChoose</tt> resources to build our next piece?
     * Choose the resources we need most, for we want to play a discovery development card
     * or when a Gold Hex number is rolled.
     * If returns true, has called {@link #chooseFreeResources(SOCResourceSet, int, boolean)}
     * and has set {@link #resourceChoices}.
     *
     * @param targetResources  Resources needed to build our next planned piece,
     *             from {@link SOCPlayingPiece#getResourcesToBuild(int)}
     *             for {@link #buildingPlan}.peek().
     * @param numChoose  Number of resources to choose
     * @param chooseIfNotNeeded  Even if we find we don't need them, choose anyway;
     *             set true for Gold Hex choice, false for Discovery card pick.
     * @return  true if we need <tt>numChoose</tt> resources
     * @since 2.0.00
     */
    private boolean chooseFreeResourcesIfNeeded
        (SOCResourceSet targetResources, final int numChoose, final boolean chooseIfNotNeeded)
    {
        if (chooseIfNotNeeded)
            resourceChoices.clear();

        final SOCResourceSet ourResources = ourPlayerData.getResources();
        int numMore = numChoose;

        // Used only if chooseIfNotNeeded:
        int buildingItem = 0;  // for ourBuildingPlan.peek
        boolean stackTopIs0 = false;

        /**
         * If ! chooseIfNotNeeded, this loop
         * body will only execute once.
         */
        do
        {
            int numNeededResources = 0;

            for (int resource = SOCResourceConstants.CLAY;
                    resource <= SOCResourceConstants.WOOD;
                    resource++)
            {
                final int diff = targetResources.getAmount(resource) - ourResources.getAmount(resource);
                if (diff > 0)
                    numNeededResources += diff;
            }

            if ((numNeededResources == numMore)  // TODO >= numMore ? (could change details of current bot behavior)
                || (chooseIfNotNeeded && (numNeededResources > numMore)))
            {
                chooseFreeResources(targetResources, numMore, ! chooseIfNotNeeded);
                return true;
            }

            if (! chooseIfNotNeeded)
                return false;

            // Assert: numNeededResources < numMore.
            // Pick the first numNeeded, then loop to pick additional ones.
            chooseFreeResources(targetResources, numMore, false);
            numMore = numChoose - resourceChoices.getTotal();

            if (numMore > 0)
            {
                // Pick a new target from building plan, if we can.
                // Otherwise, choose our least-frequently-rolled resources.

                ++buildingItem;
                final int bpSize = buildingPlan.size();
                if (bpSize > buildingItem)
                {
                    if (buildingItem == 1)
                    {
                        // validate direction of stack growth for buildingPlan
                        stackTopIs0 = (0 == buildingPlan.indexOf(buildingPlan.peek()));
                    }

                    int i = (stackTopIs0) ? buildingItem : (bpSize - buildingItem) - 1;
                    targetResources = SOCPlayingPiece.getResourcesToBuild(buildingPlan.elementAt(i).getType());

                    // Will continue at top of loop to add
                    // targetResources to resourceChoices.

                } else {

                    // This will be the last iteration.
                    // Choose based on our least-frequent dice rolls.

                    final int[] resourceOrder =
                        SOCBuildingSpeedEstimate.getRollsForResourcesSorted(ourPlayerData);

                    int curRsrc = 0;
                    while (numMore > 0)
                    {
                        resourceChoices.add(1, resourceOrder[curRsrc]);
                        --numMore;
                        ++curRsrc;
                        if (curRsrc == resourceOrder.length)
                            curRsrc = 0;
                    }

                    // now, numMore == 0, so do-while loop will exit at bottom.
                }
            }

        } while (numMore > 0);

        return true;
    }

    /**
     * this is for debugging
     */
    private void debugInfo()
    {
        /*
           if (D.ebugOn) {
           //D.ebugPrintln("$===============");
           //D.ebugPrintln("gamestate = "+game.getGameState());
           //D.ebugPrintln("counter = "+counter);
           //D.ebugPrintln("resources = "+ourPlayerData.getResources().getTotal());
           if (expectSTART1A)
           //D.ebugPrintln("expectSTART1A");
           if (expectSTART1B)
           //D.ebugPrintln("expectSTART1B");
           if (expectSTART2A)
           //D.ebugPrintln("expectSTART2A");
           if (expectSTART2B)
           //D.ebugPrintln("expectSTART2B");
           if (expectPLAY)
           //D.ebugPrintln("expectPLAY");
           if (expectPLAY1)
           //D.ebugPrintln("expectPLAY1");
           if (expectPLACING_ROAD)
           //D.ebugPrintln("expectPLACING_ROAD");
           if (expectPLACING_SETTLEMENT)
           //D.ebugPrintln("expectPLACING_SETTLEMENT");
           if (expectPLACING_CITY)
           //D.ebugPrintln("expectPLACING_CITY");
           if (expectPLACING_ROBBER)
           //D.ebugPrintln("expectPLACING_ROBBER");
           if (expectPLACING_FREE_ROAD1)
           //D.ebugPrintln("expectPLACING_FREE_ROAD1");
           if (expectPLACING_FREE_ROAD2)
           //D.ebugPrintln("expectPLACING_FREE_ROAD2");
           if (expectPUTPIECE_FROM_START1A)
           //D.ebugPrintln("expectPUTPIECE_FROM_START1A");
           if (expectPUTPIECE_FROM_START1B)
           //D.ebugPrintln("expectPUTPIECE_FROM_START1B");
           if (expectPUTPIECE_FROM_START2A)
           //D.ebugPrintln("expectPUTPIECE_FROM_START2A");
           if (expectPUTPIECE_FROM_START2B)
           //D.ebugPrintln("expectPUTPIECE_FROM_START2B");
           if (expectDICERESULT)
           //D.ebugPrintln("expectDICERESULT");
           if (expectDISCARD)
           //D.ebugPrintln("expectDISCARD");
           if (expectMOVEROBBER)
           //D.ebugPrintln("expectMOVEROBBER");
           if (expectWAITING_FOR_DISCOVERY)
           //D.ebugPrintln("expectWAITING_FOR_DISCOVERY");
           if (waitingForGameState)
           //D.ebugPrintln("waitingForGameState");
           if (waitingForOurTurn)
           //D.ebugPrintln("waitingForOurTurn");
           if (waitingForTradeMsg)
           //D.ebugPrintln("waitingForTradeMsg");
           if (waitingForDevCard)
           //D.ebugPrintln("waitingForDevCard");
           if (moveRobberOnSeven)
           //D.ebugPrintln("moveRobberOnSeven");
           if (waitingForTradeResponse)
           //D.ebugPrintln("waitingForTradeResponse");
           if (doneTrading)
           //D.ebugPrintln("doneTrading");
           if (ourTurn)
           //D.ebugPrintln("ourTurn");
           //D.ebugPrintln("whatWeWantToBuild = "+whatWeWantToBuild);
           //D.ebugPrintln("#===============");
           }
         */
    }

    /**
     * For each player in game:
     * client.sendText, and debug-print to console, game.getPlayer(i).getResources()
     */
    private void printResources()
    {
        if (D.ebugOn)
        {
            for (int i = 0; i < game.maxPlayers; i++)
            {
                SOCResourceSet rsrcs = game.getPlayer(i).getResources();
                String resourceMessage = "PLAYER " + i + " RESOURCES: ";
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.CLAY) + " ");
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.ORE) + " ");
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.SHEEP) + " ");
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.WHEAT) + " ");
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.WOOD) + " ");
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.UNKNOWN) + " ");
                client.sendText(game, resourceMessage);
                D.ebugPrintln(resourceMessage);
            }
        }
    }
}
