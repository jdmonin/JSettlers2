/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2019 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
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

import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCInventory;
import soc.game.SOCInventoryItem;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCRoutePiece;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCSpecialItem;
import soc.game.SOCTradeOffer;

import soc.message.SOCAcceptOffer;
import soc.message.SOCBankTrade;
import soc.message.SOCCancelBuildRequest;
import soc.message.SOCChoosePlayer;
import soc.message.SOCChoosePlayerRequest;
import soc.message.SOCClearOffer;
import soc.message.SOCDevCardAction;
import soc.message.SOCDiceResult;
import soc.message.SOCDiscardRequest;
import soc.message.SOCGameState;
import soc.message.SOCInventoryItemAction;
import soc.message.SOCMakeOffer;
import soc.message.SOCMessage;
import soc.message.SOCMovePiece;
import soc.message.SOCMoveRobber;
import soc.message.SOCPlayerElement;
import soc.message.SOCPlayerElements;
import soc.message.SOCPutPiece;
import soc.message.SOCRejectOffer;
import soc.message.SOCResourceCount;
import soc.message.SOCSetSpecialItem;
import soc.message.SOCSimpleAction;
import soc.message.SOCSimpleRequest;
import soc.message.SOCSitDown;  // for javadoc
import soc.message.SOCStartGame;
import soc.message.SOCTimingPing;  // for javadoc
import soc.message.SOCTurn;

import soc.util.CappedQueue;
import soc.util.DebugRecorder;
import soc.util.Queue;
import soc.util.SOCRobotParameters;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
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
 *<P>
 * See {@link #run()} for more details of how the bot waits for and reacts to incoming messages.
 * Some reactions are chosen in methods like {@link #considerOffer(SOCTradeOffer)} called from {@code run()}.
 * Some robot actions wait for other players or other timeouts; the brain counts {@link SOCTimingPing} messages
 * (1 per second) for timing.  For robustness testing, the {@code SOCRobotClient.debugRandomPause} flag can
 * be used to inject random delays in incoming messages.
 *<P>
 * To keep the game moving, the server may force an inactive bot to end its turn;
 * see {@link soc.server.SOCForceEndTurnThread}.
 *
 *<H3>AI/Robot development:</H3>
 *
 * The bot can be sent debug commands to examine its state; see
 * {@link SOCRobotClient#handleGAMETEXTMSG(soc.message.SOCGameTextMsg)}.
 * Extending this class is one way to begin developing a custom JSettlers bot.
 * For a trivial example see {@link soc.robot.sample3p.Sample3PBrain}.
 * See {@code README.developer} for more about bot development.
 *
 * @author Robert S Thomas
 */
public class SOCRobotBrain extends Thread
{
    /**
     * Bot pause speed-up factor when {@link SOCGame#isBotsOnly} in {@link #pause(int)}.
     * Default 0.25 (25% of normal pause time: 4x speed-up).
     * Use .01 for a shorter delay (1% of normal pauses).
     * @since 2.0.00
     */
    public static float BOTS_ONLY_FAST_PAUSE_FACTOR = .25f;

    /**
     * If, during a turn, we make this many illegal build
     * requests that the server denies, stop trying.
     *
     * @see #failedBuildingAttempts
     */
    public static int MAX_DENIED_BUILDING_PER_TURN = 3;

    /**
     * The robot parameters
     */
    SOCRobotParameters robotParameters;

    /**
     * Flag for whether or not we're alive.
     * From other threads, set false by calling {@link #kill()}.
     */
    protected volatile boolean alive;

    /**
     * Flag for whether or not it is our turn.
     * Updated near top of per-message loop in {@code run()}
     * based on {@link SOCGame#getCurrentPlayerNumber()}.
     */
    protected boolean ourTurn;

    /**
     * Timer for turn taking
     */
    protected int turnTime;

    /**
     * {@link #pause(int) Pause} for less time;
     * speeds up response in 6-player games.
     * Ignored if {@link SOCGame#isBotsOnly}, which pauses for even less time.
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
     * The game we are playing. Set in constructor, unlike {@link #ourPlayerData}.
     * @see #gameIs6Player
     */
    protected SOCGame game;

    /**
     * If true, the {@link #game} we're playing is on the 6-player board.
     * @since 1.1.08
     */
    final private boolean gameIs6Player;

    /**
     * Our player data.
     * Set in {@link #setOurPlayerData()}
     * @see #ourPlayerNumber
     * @see #ourPlayerName
     * @see #game
     */
    protected SOCPlayer ourPlayerData;

    /**
     * Our player number; set in {@link #setOurPlayerData()}.
     * @since 2.0.00
     */
    private int ourPlayerNumber;

    /**
     * Our player nickname. Convenience field, set from
     * {@link SOCDisplaylessPlayerClient#getNickname() client.getNickname()}.
     * @since 2.0.00
     */
    protected final String ourPlayerName;

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
     * Number of exceptions caught this turn, if any.
     * Resets at each player's turn during {@link SOCMessage#TURN TURN} message.
     * @since 1.1.20
     */
    private int turnExceptionCount;

    /**
     * A counter used to measure passage of time.
     * Incremented each second, when the server sends {@link SOCTimingPing}.
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
     * This is the piece we want to build now.
     * Set in {@link #buildOrGetResourceByTradeOrCard()} from {@link #buildingPlan},
     * used in {@link #placeIfExpectPlacing()}.
     * @see #whatWeFailedToBuild
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
     * @see #whatWeWantToBuild
     */
    protected final Stack<SOCPossiblePiece> buildingPlan;

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
    // please update debugPrintBrainStatus() and the
    // run() loop at "if (mesType == SOCMessage.TURN)".

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
     * true if we're expecting the {@link SOCGame#ROLL_OR_CARD ROLL_OR_CARD} state.
     *<P>
     * Before v2.0.00 this field was {@code expectPLAY} because that state was named {@code PLAY}.
     */
    protected boolean expectROLL_OR_CARD;

    /**
     * true if we're expecting the {@link SOCGame#PLAY1 PLAY1} state.
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
     * True if we're expecting the PLACING_ROBBER state.
     * {@link #playKnightCard()} sets this field and {@link #waitingForGameState}.
     *<P>
     * In scenario {@link SOCGameOption#K_SC_PIRI SC_PIRI}, this flag is also used when we've just played
     * a "Convert to Warship" card (Knight/Soldier card) and we're waiting for the
     * server response.  The response won't be a GAMESTATE(PLACING_SOLDIER) message,
     * it will either be PLAYERLEMENT(GAIN, SCENARIO_WARSHIP_COUNT) or DEVCARDACTION(CANNOT_PLAY).
     * Since this situation is otherwise the same as playing a Knight/Soldier, we use
     * this same waiting flags.
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
     * true if we're expecting the {@link SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM} state.
     * @since 2.0.00
     */
    protected boolean expectPLACING_INV_ITEM;

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
     * True if we're expecting to pick a monopoly.
     * When game state {@link SOCGame#WAITING_FOR_MONOPOLY} arrives,
     * will send a resource type and set {@link #expectPLAY1}.
     */
    protected boolean expectWAITING_FOR_MONOPOLY;

    // If any new expect or waitingFor fields are added,
    // please update debugPrintBrainStatus() and maybe also
    // the section of run() at (mesType == SOCMessage.TURN).

    /**
     * true if we're waiting for a GAMESTATE message from the server.
     * This is set after a robot action or requested action is sent to server,
     * or just before ending our turn (which also sets {@link #waitingForOurTurn} == true).
     *<P>
     * For example, when playing a {@link SOCDevCardAction}, set true and also set
     * an "expect" flag ({@link #expectPLACING_ROBBER}, {@link #expectWAITING_FOR_DISCOVERY}, etc).
     *<P>
     * <b>Special case:</b><br>
     * In scenario {@link SOCGameOption#K_SC_PIRI SC_PIRI}, this flag is also set when we've just played
     * a "Convert to Warship" card (Knight/Soldier card), although the server won't
     * respond with a GAMESTATE message; see {@link #expectPLACING_ROBBER} javadoc.
     *
     * @see #rejectedPlayDevCardType
     * @see #rejectedPlayInvItem
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

    /**
     * Non-{@code null} if we're waiting for server response to picking
     * a {@link SOCSpecialItem}, for certain scenarios; contains the {@code typeKey}
     * of the special item we're waiting on.
     */
    protected String waitingForPickSpecialItem;

    /**
     * True if we're in a {@link SOCGameOption#K_SC_PIRI _SC_PIRI} game
     * and waiting for server response to a {@link SOCSimpleRequest}
     * to attack a pirate fortress.
     */
    protected boolean waitingForSC_PIRI_FortressRequest;

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
     * If not {@code null}, the server rejected our play of this {@link SOCInventoryItem}
     * this turn, probably because of a bug in our robot. Should not attempt to
     * play an item of the same {@link SOCInventoryItem#itype itype} again this turn.
     * @since 2.0.00
     */
    private SOCInventoryItem rejectedPlayInvItem;
        // TODO refine later: must build/play something else first, have that clear this field. After building/playing
        // something else, the previously rejected inv item type might be okay to play again this turn.
        // Don't need to also add a count of play inv item rejections this turn (to avoid loop forever
        // asking & being rejected between building other things) because would run out of other things.
        // To find places which build/play something else, look for counter = 0.

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
     * towards which we're building, as calculated by {@link OpeningBuildStrategy#planInitRoad()}.
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
     * keeps track of the last thing we bought, for debugging purposes
     */
    protected SOCPossiblePiece lastMove;

    /**
     * keeps track of the last thing we wanted, for debugging purposes
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
        ourPlayerName = rc.getNickname();
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
        expectROLL_OR_CARD = false;
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
        if (null == playerTrackers.get(Integer.valueOf(pn)))
        {
            SOCPlayerTracker tracker = new SOCPlayerTracker(game.getPlayer(pn), this);
            playerTrackers.put(Integer.valueOf(pn), tracker);
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
     * @see #getOurPlayerNumber()
     */
    public SOCPlayer getOurPlayerData()
    {
        return ourPlayerData;
    }

    /**
     * Get our player number, as set in {@link #setOurPlayerData()}.
     * @return Our {@link #getOurPlayerData()}'s player number
     * @since 2.0.00
     */
    public final int getOurPlayerNumber()
    {
        return ourPlayerNumber;
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
     * Initializes our game and {@link #ourPlayerData},
     * {@link SOCRobotDM}, {@link SOCRobotNegotiator},
     * strategy fields, {@link SOCPlayerTracker}s, etc.
     */
    public void setOurPlayerData()
    {
        ourPlayerData = game.getPlayer(client.getNickname());
        ourPlayerTracker = new SOCPlayerTracker(ourPlayerData, this);
        ourPlayerNumber = ourPlayerData.getPlayerNumber();
        playerTrackers = new HashMap<Integer, SOCPlayerTracker>();
        playerTrackers.put(Integer.valueOf(ourPlayerNumber), ourPlayerTracker);

        for (int pn = 0; pn < game.maxPlayers; pn++)
        {
            if ((pn != ourPlayerNumber) && ! game.isSeatVacant(pn))
            {
                SOCPlayerTracker tracker = new SOCPlayerTracker(game.getPlayer(pn), this);
                playerTrackers.put(Integer.valueOf(pn), tracker);
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
     * Print brain variables and status for this game to a list of {@link String}s.
     * Includes all of the expect and waitingFor fields (<tt>expectROLL_OR_CARD</tt>,
     * <tt>waitingForGameState</tt>, etc.)
     * Also prints the game state, and the messages received by this brain
     * during the previous and current turns.
     *<P>
     * Before v1.1.20, this printed to {@link System#err} instead of returning the status as Strings.
     * @since 1.1.13
     */
    public List<String> debugPrintBrainStatus()
    {
        ArrayList<String> rbSta = new ArrayList<String>();

        if ((ourPlayerData == null) || (game == null))
        {
            rbSta.add("Robot internal state: Cannot print: null game or player");
            return rbSta;
        }

        rbSta.add("Robot internal state: "
                + ((client != null) ? client.getNickname() : ourPlayerData.getName())
                + " in game " + game.getName()
                + ": gs=" + game.getGameState());
        if (waitingForPickSpecialItem != null)
            rbSta.add("  waitingForPickSpecialItem = " + waitingForPickSpecialItem);
        if (game.getGameState() == SOCGame.WAITING_FOR_DISCARDS)
            rbSta.add("  bot card count = " + ourPlayerData.getResources().getTotal());
        if (rejectedPlayDevCardType != -1)
            rbSta.add("  rejectedPlayDevCardType = " + rejectedPlayDevCardType);
        if (rejectedPlayInvItem != null)
            rbSta.add("  rejectedPlayInvItem = " + rejectedPlayInvItem);

        // Reminder: Add new state fields to both s[] and b[]

        final String[] s = {
            "ourTurn", "doneTrading",
            "waitingForGameState", "waitingForOurTurn", "waitingForTradeMsg", "waitingForDevCard",
            "waitingForTradeResponse", "waitingForSC_PIRI_FortressRequest",
            "moveRobberOnSeven", "expectSTART1A", "expectSTART1B", "expectSTART2A", "expectSTART2B", "expectSTART3A", "expectSTART3B",
            "expectROLL_OR_CARD", "expectPLAY1", "expectPLACING_ROAD", "expectPLACING_SETTLEMENT", "expectPLACING_CITY", "expectPLACING_SHIP",
            "expectPLACING_ROBBER", "expectPLACING_FREE_ROAD1", "expectPLACING_FREE_ROAD2", "expectPLACING_INV_ITEM",
            "expectPUTPIECE_FROM_START1A", "expectPUTPIECE_FROM_START1B", "expectPUTPIECE_FROM_START2A", "expectPUTPIECE_FROM_START2B",
            "expectPUTPIECE_FROM_START3A", "expectPUTPIECE_FROM_START3B",
            "expectDICERESULT", "expectDISCARD", "expectMOVEROBBER", "expectWAITING_FOR_DISCOVERY", "expectWAITING_FOR_MONOPOLY"
        };
        final boolean[] b = {
            ourTurn, doneTrading,
            waitingForGameState, waitingForOurTurn, waitingForTradeMsg, waitingForDevCard,
            waitingForTradeResponse, waitingForSC_PIRI_FortressRequest,
            moveRobberOnSeven, expectSTART1A, expectSTART1B, expectSTART2A, expectSTART2B, expectSTART3A, expectSTART3B,
            expectROLL_OR_CARD, expectPLAY1, expectPLACING_ROAD, expectPLACING_SETTLEMENT, expectPLACING_CITY, expectPLACING_SHIP,
            expectPLACING_ROBBER, expectPLACING_FREE_ROAD1, expectPLACING_FREE_ROAD2, expectPLACING_INV_ITEM,
            expectPUTPIECE_FROM_START1A, expectPUTPIECE_FROM_START1B, expectPUTPIECE_FROM_START2A, expectPUTPIECE_FROM_START2B,
            expectPUTPIECE_FROM_START3A, expectPUTPIECE_FROM_START3B,
            expectDICERESULT, expectDISCARD, expectMOVEROBBER, expectWAITING_FOR_DISCOVERY, expectWAITING_FOR_MONOPOLY
        };
        if (s.length != b.length)
        {
            rbSta.add("L745: Internal error: array length");
            return rbSta;
        }
        int slen = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length; ++i)
        {
            if ((slen + s[i].length() + 8) > 79)
            {
                rbSta.add(sb.toString());
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
            rbSta.add(sb.toString());

        debugPrintTurnMessages(turnEventsPrev, "previous", rbSta);
        debugPrintTurnMessages(turnEventsCurrent, "current", rbSta);

        return rbSta;
    }

    /**
     * Add the contents of this Vector as Strings to the provided list.
     * One element per line, indented by <tt>\t</tt>.
     * Headed by a line formatted as one of:
     *<BR>  Current turn: No messages received.
     *<BR>  Current turn: 5 messages received:
     * @param msgV  Vector of {@link SOCMessage}s from server
     * @param msgDesc  Short description of the vector, like 'previous' or 'current'
     * @param toList  Add to this list
     * @since 1.1.13
     */
    private static void debugPrintTurnMessages(Vector<?> msgV, final String msgDesc, List<String> toList)
    {
        final int n = msgV.size();
        if (n == 0)
        {
            toList.add("  " + msgDesc + " turn: No messages received.");
        } else {
            toList.add("  " + msgDesc + " turn: " + n + " messages received:");
            for (int i = 0; i < n; ++i)
            {
                toList.add("\t" + msgV.elementAt(i));
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
     *<P>
     * The brain thread will run until {@link #kill()} has been called or its pinger stops,
     * or it receives a {@link SOCMessage#ROBOTDISMISS} request to exit the game.
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

            //
            // Along with actual game events, the pinger sends a TIMINGPING message
            // once per second, to aid the robot's timekeeping counter.
            //

            while (alive)
            {
                try
                {
                    final SOCMessage mes = gameEventQ.get();  // Sleeps until message received

                    final int mesType;
                    if (mes != null)
                    {
                        // Debug aid: When looking at message contents or setting a per-message breakpoint,
                        // skip the pings; note (mesType != SOCMessage.TIMINGPING) here.

                        mesType = mes.getType();
                        if (mesType != SOCMessage.TIMINGPING)
                            turnEventsCurrent.addElement(mes);
                        if (D.ebugOn)
                            D.ebugPrintln("mes - " + mes);
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
                        handleGAMESTATE(((SOCGameState) mes).getState());
                            // clears waitingForGameState, updates oldGameState, calls ga.setGameState
                    }

                    else if (mesType == SOCMessage.STARTGAME)
                    {
                        handleGAMESTATE(((SOCStartGame) mes).getGameState());
                            // clears waitingForGameState, updates oldGameState, calls ga.setGameState
                    }

                    else if (mesType == SOCMessage.TURN)
                    {
                        // Start of a new player's turn.
                        // Update game and reset most of our state fields.
                        // See also below: if ((mesType == SOCMessage.TURN) && ourTurn).

                        handleGAMESTATE(((SOCTurn) mes).getGameState());
                            // clears waitingForGameState, updates oldGameState, calls ga.setGameState

                        game.setCurrentPlayerNumber(((SOCTurn) mes).getPlayerNumber());
                        game.updateAtTurn();

                        //
                        // remove any expected states
                        //
                        expectROLL_OR_CARD = false;
                        expectPLAY1 = false;
                        expectPLACING_ROAD = false;
                        expectPLACING_SETTLEMENT = false;
                        expectPLACING_CITY = false;
                        expectPLACING_SHIP = false;
                        expectPLACING_ROBBER = false;
                        expectPLACING_FREE_ROAD1 = false;
                        expectPLACING_FREE_ROAD2 = false;
                        expectPLACING_INV_ITEM = false;
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

                        waitingForPickSpecialItem = null;
                        waitingForSC_PIRI_FortressRequest = false;

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
                            Vector<SOCMessage> oldPrev = turnEventsPrev;
                            turnEventsPrev = turnEventsCurrent;
                            oldPrev.clear();
                            turnEventsCurrent = oldPrev;
                        }

                        turnExceptionCount = 0;
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

                    if ((mesType == SOCMessage.TURN) && ourTurn)
                    {
                        waitingForOurTurn = false;

                        // Clear some per-turn variables.
                        // For others, see above: if (mesType == SOCMessage.TURN)
                        whatWeFailedToBuild = null;
                        failedBuildingAttempts = 0;
                        rejectedPlayDevCardType = -1;
                        rejectedPlayInvItem = null;
                    }

                    /**
                     * Handle some message types early.
                     *
                     * When reading the main flow of this method, skip past here;
                     * search for "it's time to decide to build or take other normal actions".
                     */
                    switch (mesType)
                    {
                    case SOCMessage.PLAYERELEMENT:
                        // If this during the ROLL_OR_CARD state, also updates the
                        // negotiator's is-selling flags.
                        // If our player is losing a resource needed for the buildingPlan,
                        // clear the plan if this is for the Special Building Phase (on the 6-player board).
                        // In normal game play, we clear the building plan at the start of each turn.

                        handlePLAYERELEMENT((SOCPlayerElement) mes);
                        break;

                    case SOCMessage.PLAYERELEMENTS:
                        // Multiple PLAYERELEMENT updates;
                        // see comment above for actions taken.

                        handlePLAYERELEMENTS((SOCPlayerElements) mes);
                        break;

                    case SOCMessage.RESOURCECOUNT:
                        handlePLAYERELEMENT
                            (null, ((SOCResourceCount) mes).getPlayerNumber(), SOCPlayerElement.SET,
                             SOCPlayerElement.RESOURCE_COUNT, ((SOCResourceCount) mes).getCount());
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

                    case SOCMessage.DEVCARDACTION:
                        {
                            SOCDevCardAction dcMes = (SOCDevCardAction) mes;
                            if (dcMes.getAction() != SOCDevCardAction.CANNOT_PLAY)
                            {
                                handleDEVCARDACTION(dcMes);
                            } else {
                                // rejected by server, can't play our requested card
                                rejectedPlayDevCardType = dcMes.getCardType();
                                waitingForGameState = false;
                                expectPLACING_FREE_ROAD1 = false;
                                expectWAITING_FOR_DISCOVERY = false;
                                expectWAITING_FOR_MONOPOLY = false;
                                expectPLACING_ROBBER = false;
                            }
                        }
                        break;

                    case SOCMessage.SIMPLEREQUEST:
                        // These messages can almost always be ignored by bots,
                        // unless we've just sent a request to attack a pirate fortress.
                        // Some request types are handled at the bottom of the loop body;
                        // search for SOCMessage.SIMPLEREQUEST

                        if (ourTurn && waitingForSC_PIRI_FortressRequest)
                        {
                            final SOCSimpleRequest rqMes = (SOCSimpleRequest) mes;

                            if ((rqMes.getRequestType() == SOCSimpleRequest.SC_PIRI_FORT_ATTACK)
                                && (rqMes.getPlayerNumber() == -1))
                            {
                                // Attack request was denied: End our turn now.
                                // Reset method sets waitingForGameState, which will bypass
                                // any further actions in the run() loop body.

                                waitingForSC_PIRI_FortressRequest = false;
                                resetFieldsAtEndTurn();
                                client.endTurn(game);
                            }
                            // else, from another player; we can ignore it
                        }
                        break;

                    case SOCMessage.SIMPLEACTION:
                        // Most action types are handled later in the loop body;
                        // search for SOCMessage.SIMPLEACTION

                        switch(((SOCSimpleAction) mes).getActionType())
                        {
                        case SOCSimpleAction.SC_PIRI_FORT_ATTACK_RESULT:
                            if (ourTurn && waitingForSC_PIRI_FortressRequest)
                            {
                                // Our player has won or lost an attack on a pirate fortress.
                                // When we receive this message, other messages have already
                                // been sent to update related game state. End our turn now.
                                // Reset method sets waitingForGameState, which will bypass
                                // any further actions in the run() loop body.

                                waitingForSC_PIRI_FortressRequest = false;
                                resetFieldsAtEndTurn();
                                // client.endTurn not needed; making the attack implies sending endTurn
                            }
                            // else, from another player; we can ignore it

                            break;
                        }
                        break;

                    case SOCMessage.INVENTORYITEMACTION:
                        if (((SOCInventoryItemAction) mes).action == SOCInventoryItemAction.CANNOT_PLAY)
                        {
                            final List<SOCInventoryItem> itms = ourPlayerData.getInventory().getByStateAndType
                                (SOCInventory.PLAYABLE, ((SOCInventoryItemAction) mes).itemType);
                            if (itms != null)
                                rejectedPlayInvItem = itms.get(0);  // any item of same type# is similar enough here

                            waitingForGameState = false;
                            expectPLACING_INV_ITEM = false;  // in case was rejected placement (SC_FTRI gift port, etc)
                        }
                        break;

                    }  // switch(mesType)

                    debugInfo();

                    if ((game.getGameState() == SOCGame.ROLL_OR_CARD) && ! waitingForGameState)
                    {
                        rollOrPlayKnightOrExpectDice();

                        // On our turn, ask client to roll dice or play a knight;
                        // on other turns, update flags to expect dice result.
                        // Clears expectROLL_OR_CARD to false.
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

                    else if ((game.getGameState() == SOCGame.PLACING_ROBBER) && ! waitingForGameState)
                    {
                        expectPLACING_ROBBER = false;

                        if ((! waitingForOurTurn) && ourTurn)
                        {
                            if (! ((expectROLL_OR_CARD || expectPLAY1) && (counter < 4000)))
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

                                    if (oldGameState == SOCGame.ROLL_OR_CARD)
                                    {
                                        // robber moved from playing knight card before dice roll
                                        expectROLL_OR_CARD = true;
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

                    if ((game.getGameState() == SOCGame.WAITING_FOR_DISCOVERY) && ! waitingForGameState)
                    {
                        expectWAITING_FOR_DISCOVERY = false;

                        if ((! waitingForOurTurn) && ourTurn)
                        {
                            if (! (expectPLAY1) && (counter < 4000))
                            {
                                waitingForGameState = true;
                                expectPLAY1 = true;
                                counter = 0;
                                client.pickResources(game, resourceChoices);
                                pause(1500);
                            }
                        }
                    }

                    if ((game.getGameState() == SOCGame.WAITING_FOR_MONOPOLY) && ! waitingForGameState)
                    {
                        expectWAITING_FOR_MONOPOLY = false;

                        if ((! waitingForOurTurn) && ourTurn)
                        {
                            if (!(expectPLAY1) && (counter < 4000))
                            {
                                waitingForGameState = true;
                                expectPLAY1 = true;
                                counter = 0;
                                client.pickResourceType(game, monopolyStrategy.getMonopolyChoice());
                                pause(1500);
                            }
                        }
                    }

                    if (ourTurn && (! waitingForOurTurn)
                        && (game.getGameState() == SOCGame.PLACING_INV_ITEM) && (! waitingForGameState))
                    {
                        planAndPlaceInvItem();  // choose and send a placement location
                    }

                    if (waitingForTradeMsg && (mesType == SOCMessage.BANKTRADE)
                        && (((SOCBankTrade) mes).getPlayerNumber() == ourPlayerNumber))
                    {
                        //
                        // This is the bank/port trade confirmation announcement we've been waiting for
                        //
                        waitingForTradeMsg = false;
                    }

                    if (waitingForDevCard && (mesType == SOCMessage.SIMPLEACTION)
                        && (((SOCSimpleAction) mes).getPlayerNumber() == ourPlayerNumber)
                        && (((SOCSimpleAction) mes).getActionType() == SOCSimpleAction.DEVCARD_BOUGHT))
                    {
                        //
                        // This is the "dev card bought" message we've been waiting for
                        //
                        waitingForDevCard = false;
                    }

                    /**
                     * Planning: If our turn and not waiting for something,
                     * it's time to decide to build or take other normal actions.
                     */
                    if (((game.getGameState() == SOCGame.PLAY1) || (game.getGameState() == SOCGame.SPECIAL_BUILDING))
                        && ! (waitingForGameState || waitingForTradeMsg || waitingForTradeResponse || waitingForDevCard
                              || expectPLACING_ROAD || expectPLACING_SETTLEMENT || expectPLACING_CITY
                              || expectPLACING_SHIP || expectPLACING_FREE_ROAD1 || expectPLACING_FREE_ROAD2
                              || expectPLACING_ROBBER || expectWAITING_FOR_DISCOVERY || expectWAITING_FOR_MONOPOLY
                              || waitingForSC_PIRI_FortressRequest || (waitingForPickSpecialItem != null)))
                    {
                        expectPLAY1 = false;

                        // 6-player: check Special Building Phase
                        // during other players' turns.
                        if ((! ourTurn) && waitingForOurTurn && gameIs6Player
                             && (! decidedIfSpecialBuild) && (! expectPLACING_ROBBER))
                        {
                            decidedIfSpecialBuild = true;

                            /**
                             * It's not our turn.  We're not doing anything else right now.
                             * Gamestate has passed ROLL_OR_CARD, so we know what resources to expect.
                             * Do we want to Special Build?  Check the same conditions as during our turn.
                             * Make a plan if we don't have one,
                             * and if we haven't given up building attempts this turn.
                             */

                            if (buildingPlan.empty() && (ourPlayerData.getResources().getTotal() > 1)
                                && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
                            {
                                planBuilding();

                                    /*
                                     * planBuilding takes these actions, sets buildingPlan and other fields
                                     * (see its javadoc):
                                     *
                                    decisionMaker.planStuff(robotParameters.getStrategyType());

                                    if (! buildingPlan.empty())
                                    {
                                        lastTarget = (SOCPossiblePiece) buildingPlan.peek();
                                        negotiator.setTargetPiece(ourPlayerNumber, buildingPlan.peek());
                                    }
                                     */

                                if ( ! buildingPlan.empty())
                                {
                                    // If we have the resources right now, ask to Special Build

                                    final SOCPossiblePiece targetPiece = buildingPlan.peek();
                                    final SOCResourceSet targetResources = targetPiece.getResourcesToBuild();
                                        // may be null

                                    if ((ourPlayerData.getResources().contains(targetResources)))
                                    {
                                        // Ask server for the Special Building Phase.
                                        // (TODO) if FAST_STRATEGY: Maybe randomly don't ask, to lower opponent difficulty?
                                        waitingForSpecialBuild = true;
                                        client.buildRequest(game, -1);
                                        pause(100);
                                    }
                                }
                            }
                        }

                        if ((! waitingForOurTurn) && ourTurn)
                        {
                            if (! (expectROLL_OR_CARD && (counter < 4000)))
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
                                 *
                                 * In scenario _SC_PIRI (which has no robber and
                                 * no largest army), play one whenever we have
                                 * it, someone else has resources, and we can
                                 * convert a ship to a warship.
                                 */
                                if ((game.getGameState() == SOCGame.PLAY1) && ! ourPlayerData.hasPlayedDevCard())
                                {
                                    playKnightCardIfShould();  // might set expectPLACING_ROBBER and waitingForGameState
                                }

                                /**
                                 * make a plan if we don't have one,
                                 * and if we haven't given up building
                                 * attempts this turn.
                                 */
                                if ( (! expectPLACING_ROBBER) && buildingPlan.empty()
                                     && (ourPlayerData.getResources().getTotal() > 1)
                                     && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
                                {
                                    planBuilding();

                                        /*
                                         * planBuilding takes these actions, sets buildingPlan and other fields
                                         * (see its javadoc):
                                         *
                                        decisionMaker.planStuff(robotParameters.getStrategyType());

                                        if (! buildingPlan.empty())
                                        {
                                            lastTarget = (SOCPossiblePiece) buildingPlan.peek();
                                            negotiator.setTargetPiece(ourPlayerNumber, buildingPlan.peek());
                                        }
                                         */
                                }

                                //D.ebugPrintln("DONE PLANNING");
                                if ( (! expectPLACING_ROBBER) && (! buildingPlan.empty()))
                                {
                                    // Time to build something.

                                    // Either ask to build a piece, or use trading or development
                                    // cards to get resources to build it.  See javadoc for flags set
                                    // (expectPLACING_ROAD, etc).  In a future iteration of the run loop
                                    // with the expected PLACING_ state, we'll build whatWeWantToBuild
                                    // in placeIfExpectPlacing().

                                    buildOrGetResourceByTradeOrCard();
                                }

                                /**
                                 * see if we're done with our turn
                                 */
                                if (! (expectPLACING_SETTLEMENT || expectPLACING_FREE_ROAD1 || expectPLACING_FREE_ROAD2
                                       || expectPLACING_ROAD || expectPLACING_CITY || expectPLACING_SHIP
                                       || expectWAITING_FOR_DISCOVERY || expectWAITING_FOR_MONOPOLY
                                       || expectPLACING_ROBBER || waitingForTradeMsg || waitingForTradeResponse
                                       || waitingForDevCard
                                       || waitingForGameState
                                       || (waitingForPickSpecialItem != null)))
                                {
                                    // Any last things for turn from game's scenario?
                                    boolean scenActionTaken = false;
                                    if (game.isGameOptionSet(SOCGameOption.K_SC_FTRI)
                                        || game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
                                    {
                                        // possibly attack pirate fortress
                                        // or place a gift port for better bank trades
                                        scenActionTaken = considerScenarioTurnFinalActions();
                                    }

                                    if (! scenActionTaken)
                                    {
                                        resetFieldsAtEndTurn();
                                            /*
                                             * These state fields are reset:
                                             *
                                            waitingForGameState = true;
                                            counter = 0;
                                            expectROLL_OR_CARD = true;
                                            waitingForOurTurn = true;

                                            doneTrading = (robotParameters.getTradeFlag() != 1);

                                            //D.ebugPrintln("!!! ENDING TURN !!!");
                                            negotiator.resetIsSelling();
                                            negotiator.resetOffersMade();
                                            buildingPlan.clear();
                                            negotiator.resetTargetPieces();
                                             */

                                        pause(1500);
                                        client.endTurn(game);
                                    }
                                }
                            }
                        }
                    }

                    /**
                     * Placement: Make various putPiece calls; server has told us it's OK to buy them.
                     * Call client.putPiece.
                     * Works when it's our turn and we have an expect flag set
                     * (such as expectPLACING_SETTLEMENT, in these game states:
                     * START1A - START2B or - START3B
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
                    case SOCMessage.PUTPIECE:
                        /**
                         * this is for player tracking
                         *
                         * For initial placement of our own pieces, also checks
                         * and clears expectPUTPIECE_FROM_START1A,
                         * and sets expectSTART1B, etc.  The final initial putpiece
                         * clears expectPUTPIECE_FROM_START2B and sets expectROLL_OR_CARD.
                         */
                        {
                            final SOCPutPiece mpp = (SOCPutPiece) mes;
                            final int pn = mpp.getPlayerNumber();
                            final int coord = mpp.getCoordinates();
                            final int pieceType = mpp.getPieceType();
                            handlePUTPIECE_updateTrackers(pn, coord, pieceType);
                        }

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

                    case SOCMessage.SIMPLEREQUEST:
                        // These messages can almost always be ignored by bots.
                        // Some request types are handled at the top of the loop body;
                        // search for SOCMessage.SIMPLEREQUEST
                        {
                            final SOCSimpleRequest rqMes = (SOCSimpleRequest) mes;
                            switch (rqMes.getRequestType())
                            {
                            case SOCSimpleRequest.PROMPT_PICK_RESOURCES:
                                // gold hex
                                counter = 0;
                                pickFreeResources(rqMes.getValue1());
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
                            }
                        }
                        break;

                    case SOCMessage.DISCARDREQUEST:
                        expectDISCARD = false;

                        /**
                         * If we haven't recently discarded...
                         */

                        //  if (! ((expectPLACING_ROBBER || expectPLAY1) && (counter < 4000)))
                        //  {
                        if ((game.getCurrentDice() == 7) && ourTurn)
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

                        //  }
                        break;

                    case SOCMessage.CHOOSEPLAYERREQUEST:
                        {
                            final SOCChoosePlayerRequest msg = (SOCChoosePlayerRequest) mes;
                            final int choicePl = RobberStrategy.chooseRobberVictim
                                (msg.getChoices(), msg.canChooseNone(), game, playerTrackers);
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

                    case SOCMessage.SETSPECIALITEM:
                        if (waitingForPickSpecialItem != null)
                        {
                            final SOCSetSpecialItem siMes = (SOCSetSpecialItem) mes;
                            if (siMes.typeKey.equals(waitingForPickSpecialItem))
                            {
                                // This could be the "pick special item" message we've been waiting for,
                                // or a related SET/CLEAR message that precedes it

                                switch (siMes.op)
                                {
                                case SOCSetSpecialItem.OP_PICK:
                                    waitingForPickSpecialItem = null;

                                    // Now that this is received, can continue our turn.
                                    // Any specific action needed? Not for SC_WOND.
                                    break;

                                case SOCSetSpecialItem.OP_DECLINE:
                                    waitingForPickSpecialItem = null;

                                    // TODO how to prevent asking again? (similar to whatWeFailedtoBuild)
                                    break;

                                // ignore SET or CLEAR that precedes the PICK message
                                }
                            }
                        }
                        break;

                    case SOCMessage.ROBOTDISMISS:
                        if ((! expectDISCARD) && (! expectPLACING_ROBBER))
                        {
                            client.leaveGame(game, "dismiss msg", false, false);
                            alive = false;
                        }
                        break;

                    case SOCMessage.TIMINGPING:
                        // Once-per-second message from the pinger thread
                        counter++;
                        break;

                    }  // switch (mesType) - for some types, at bottom of loop body

                    if (ourTurn && (counter > 15000))
                    {
                        // We've been waiting too long, must be a bug: Leave the game.
                        // This is a fallback, server has SOCForceEndTurnThread which
                        // should have already taken action.
                        // Before v1.1.20, would leave game even during other (human) players' turns.
                        client.leaveGame(game, "counter 15000", true, false);
                        alive = false;
                    }

                    if ((failedBuildingAttempts > (2 * MAX_DENIED_BUILDING_PER_TURN))
                        && game.isInitialPlacement())
                    {
                        // Apparently can't decide where we can initially place:
                        // Leave the game.
                        client.leaveGame(game, "failedBuildingAttempts at start", true, false);
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
                catch (Exception e)
                {
                    // Print exception; ignore errors due to game reset in another thread
                    if (alive && ((game == null) || (game.getGameState() != SOCGame.RESET_OLD)))
                    {
                        ++turnExceptionCount;  // TODO end our turn if too many

                        String eMsg = (turnExceptionCount == 1)
                            ? "*** Robot caught an exception - " + e
                            : "*** Robot caught an exception (" + turnExceptionCount + " this turn) - " + e;
                        D.ebugPrintln(eMsg);
                        System.out.println(eMsg);
                        e.printStackTrace();
                    }
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
        rejectedPlayInvItem = null;
        resourceChoices = null;
        ourPlayerTracker = null;
        playerTrackers = null;

        pinger.stopPinger();
        pinger = null;
    }

    /**
     * Handle a game state change from {@link SOCGameState} or another message
     * which has a Game State field. Clears {@link #waitingForGameState},
     * updates {@link #oldGameState} if state value is actually changing, then calls
     * {@link SOCDisplaylessPlayerClient#handleGAMESTATE(SOCGame, int)}.
     * @param gs  New game state; if 0, does nothing
     * @since 2.0.00
     */
    private void handleGAMESTATE(final int gs)
    {
        if (gs == 0)
            return;

        waitingForGameState = false;
        int currGS = game.getGameState();
        if (currGS != gs)
            oldGameState = currGS;  // if no actual change, don't overwrite previously known oldGameState
        SOCDisplaylessPlayerClient.handleGAMESTATE(game, gs);
    }

    /**
     * Bot is ending its turn; reset state control fields to act during other players' turns.
     *<UL>
     * <LI> {@link #waitingForGameState} = true
     * <LI> {@link #expectROLL_OR_CARD} = true
     * <LI> {@link #waitingForOurTurn} = true
     * <LI> {@link #doneTrading} = false only if {@link #robotParameters} allow trade
     * <LI> {@link #counter} = 0
     * <LI> clear {@link #buildingPlan}
     * <LI> {@link SOCRobotNegotiator#resetIsSelling() negotiator.resetIsSelling()},
     *      {@link SOCRobotNegotiator#resetOffersMade() .resetOffersMade()},
     *      {@link SOCRobotNegotiator#resetTargetPieces() .resetTargetPieces()}
     *</UL>
     *<P>
     * Does not call {@link SOCRobotClient#endTurn(SOCGame)}.
     * @since 2.0.00
     */
    private final void resetFieldsAtEndTurn()
    {
        waitingForGameState = true;
        counter = 0;
        expectROLL_OR_CARD = true;
        waitingForOurTurn = true;

        doneTrading = (robotParameters.getTradeFlag() != 1);

        //D.ebugPrintln("!!! ENDING TURN !!!");
        negotiator.resetIsSelling();
        negotiator.resetOffersMade();
        buildingPlan.clear();
        negotiator.resetTargetPieces();
    }

    /**
     * Look for and take any scenario-specific final actions before ending the turn.
     *<P>
     * For example, {@link SOCGameOption#K_SC_PIRI _SC_PIRI} will check if we've reached the fortress
     * and have 5 or more warships, and if so will attack the fortress.  Doing so ends the turn, so
     * we don't try to attack before end of turn.
     *<P>
     * {@link SOCGameOption#K_SC_FTRI _SC_FTRI} can play a gift port from our inventory to place for
     * better bank trades.
     *<P>
     * <B>NOTE:</B> For now this method assumes it's called only in the {@code SC_FTRI} or {@code SC_PIRI} scenario.
     * Caller must check the game for any relevant scenario SOCGameOptions before calling.
     * Also assumes not {@link #waitingForGameState} or any other pending action.
     *
     * @return true if an action was taken <B>and</B> turn shouldn't be ended yet, false otherwise
     * @since 2.0.00
     */
    protected boolean considerScenarioTurnFinalActions()
    {
        // NOTE: for now this method assumes it's called only in the SC_FTRI or SC_PIRI scenario

        if (game.isGameOptionSet(SOCGameOption.K_SC_FTRI))
        {
            // SC_FTRI

            // check inventory for gift ports
            SOCInventoryItem itm = null;
            for (SOCInventoryItem i : ourPlayerData.getInventory().getByState(SOCInventory.PLAYABLE))
            {
                if (i.itype > 0)
                    continue;  // not a port; most likely a SOCDevCard
                if ((rejectedPlayInvItem != null) && (i.itype == rejectedPlayInvItem.itype))
                    continue;

                itm = i;
                break;  // unlikely to have more than one in inventory
            }

            if (itm != null)
            {
                // Do we have somewhere to place one?
                if (ourPlayerData.getPortMovePotentialLocations(false) == null)
                    return false;

                // Set fields, make the request
                return planAndPlaceInvItemPlacement_SC_FTRI(itm);
            }
        } else {
            // SC_PIRI

            // require 5+ warships; game.canAttackPirateFortress checks that we've reached the fortress with adjacent ship
            if ((ourPlayerData.getNumWarships() < 5) || (null == game.canAttackPirateFortress()))
                return false;

            waitingForSC_PIRI_FortressRequest = true;
            client.simpleRequest(game, ourPlayerNumber, SOCSimpleRequest.SC_PIRI_FORT_ATTACK, 0, 0);

            return true;
        }

        return false;
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
                if (getSet.contains(rsrcType))
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
     * If we haven't played a dev card yet this turn, and we have a knight, and we can get
     * largest army, play the knight. Must be our turn and gameState {@code PLAY1}.
     * {@link SOCPlayer#hasPlayedDevCard() ourPlayerData.hasPlayedDevCard()} must be false.
     *<P>
     * In scenario {@code _SC_PIRI} (which has no robber and no largest army), play one
     * whenever we have it, someone else has resources, and we can convert a ship to a warship.
     *<P>
     * If we call {@link #playKnightCard()}, it sets the flags
     * {@code expectPLACING_ROBBER} and {@code waitingForGameState}.
     *
     * @see #rollOrPlayKnightOrExpectDice()
     * @since 2.0.00
     */
    private void playKnightCardIfShould()
    {
        final boolean canGrowArmy;

        if (game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
        {
            // Convert ship to warship:
            // Play whenever we have one and someone else has resources

            boolean anyOpponentHasRsrcs = false;
            for (int pn = 0; pn < game.maxPlayers; ++pn)
            {
                if ((pn == ourPlayerNumber) || game.isSeatVacant(pn))
                    continue;

                if (game.getPlayer(pn).getResources().getTotal() > 0)
                {
                    anyOpponentHasRsrcs = true;
                    break;
                }
            }

            canGrowArmy = anyOpponentHasRsrcs;

        } else {

            final SOCPlayer laPlayer = game.getPlayerWithLargestArmy();

            if ((laPlayer == null) || (laPlayer.getPlayerNumber() != ourPlayerNumber))
            {
                final int larmySize;

                if (laPlayer == null)
                    larmySize = 3;
                else
                    larmySize = laPlayer.getNumKnights() + 1;

                canGrowArmy =
                    ((ourPlayerData.getNumKnights()
                      + ourPlayerData.getInventory().getAmount(SOCDevCardConstants.KNIGHT))
                      >= larmySize);

            } else {
                canGrowArmy = false;  // we already have largest army

                // TODO Should we defend it if another player is close to taking it from us?
            }
        }

        if (canGrowArmy
            && game.canPlayKnight(ourPlayerNumber)  // has an old KNIGHT devcard, etc;
                  // for _SC_PIRI, also checks if # of warships ships less than # of ships
            && (rejectedPlayDevCardType != SOCDevCardConstants.KNIGHT))
        {
            /**
             * play a knight card
             * (or, in scenario _SC_PIRI, a Convert to Warship card)
             */
            playKnightCard();  // sets expectPLACING_ROBBER, waitingForGameState
        }
    }

    /**
     * If it's our turn and we have an expect flag set
     * (such as {@link #expectPLACING_SETTLEMENT}), then
     * call {@link SOCRobotClient#putPiece(SOCGame, SOCPlayingPiece) client.putPiece}
     * ({@code game}, {@link #whatWeWantToBuild}).
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
     * Does nothing if {@link #waitingForGameState}, {@link #waitingForOurTurn}, <tt>! {@link #ourTurn}</tt>,
     * or if state's corresponding <tt>expectPLACING_&lt;piecetype&gt;</tt> flag isn't set.
     *<P>
     * If all goes well, server will reply with a PutPiece message
     * to be handled in {@link #handlePUTPIECE_updateTrackers(int, int, int)}.
     *
     * @see #buildRequestPlannedPiece()
     * @since 1.1.09
     */
    private void placeIfExpectPlacing()
    {
        if (waitingForGameState)
            return;

        switch (game.getGameState())
        {
        case SOCGame.PLACING_SETTLEMENT:
            if (ourTurn && (! waitingForOurTurn) && (expectPLACING_SETTLEMENT))
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
            break;

        case SOCGame.PLACING_ROAD:
            if (ourTurn && (! waitingForOurTurn) && (expectPLACING_ROAD))
            {
                expectPLACING_ROAD = false;
                waitingForGameState = true;
                counter = 0;
                expectPLAY1 = true;

                pause(500);
                client.putPiece(game, whatWeWantToBuild);
                pause(1000);
            }
            break;

        case SOCGame.PLACING_CITY:
            if (ourTurn && (! waitingForOurTurn) && (expectPLACING_CITY))
            {
                expectPLACING_CITY = false;
                waitingForGameState = true;
                counter = 0;
                expectPLAY1 = true;

                pause(500);
                client.putPiece(game, whatWeWantToBuild);
                pause(1000);
            }
            break;

        case SOCGame.PLACING_SHIP:
            if (ourTurn && (! waitingForOurTurn) && (expectPLACING_SHIP))
            {
                expectPLACING_SHIP = false;
                waitingForGameState = true;
                counter = 0;
                expectPLAY1 = true;

                pause(500);
                client.putPiece(game, whatWeWantToBuild);
                pause(1000);
            }
            break;

        case SOCGame.PLACING_FREE_ROAD1:
            if (ourTurn && (! waitingForOurTurn) && (expectPLACING_FREE_ROAD1))
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
            break;

        case SOCGame.PLACING_FREE_ROAD2:
            if (ourTurn && (! waitingForOurTurn) && (expectPLACING_FREE_ROAD2))
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
            break;

        case SOCGame.START1A:
            {
                expectSTART1A = false;

                if ((! waitingForOurTurn) && ourTurn && (! (expectPUTPIECE_FROM_START1A && (counter < 4000))))
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

                if ((! waitingForOurTurn) && ourTurn && (! (expectPUTPIECE_FROM_START1B && (counter < 4000))))
                {
                    expectPUTPIECE_FROM_START1B = true;
                    counter = 0;
                    waitingForGameState = true;
                    waitingForOurTurn = true;  // ignore next player's GameState(START1A) message seen before Turn(nextPN)
                    pause(1500);
                    planAndPlaceInitRoad();
                }
            }
            break;

        case SOCGame.START2A:
            {
                expectSTART2A = false;

                if ((! waitingForOurTurn) && ourTurn && (! (expectPUTPIECE_FROM_START2A && (counter < 4000))))
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

                if ((! waitingForOurTurn) && ourTurn && (! (expectPUTPIECE_FROM_START2B && (counter < 4000))))
                {
                    expectPUTPIECE_FROM_START2B = true;
                    counter = 0;
                    waitingForGameState = true;
                    waitingForOurTurn = true;  // ignore next player's GameState(START2A) message seen before Turn(nextPN)
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
                    waitingForOurTurn = true;  // ignore next player's GameState(START3A) message seen before Turn(nextPN)
                    pause(1500);
                    planAndPlaceInitRoad();
                }
            }
            break;

        }
    }

    /**
     * Play a Knight card.
     * In scenario {@link SOCGameOption#K_SC_PIRI _SC_PIRI}, play a "Convert to Warship" card.
     * Sets {@link #expectPLACING_ROBBER}, {@link #waitingForGameState}.
     * Calls {@link SOCRobotClient#playDevCard(SOCGame, int) client.playDevCard}({@link SOCDevCardConstants#KNIGHT KNIGHT}).
     *<P>
     * In scenario {@code _SC_PIRI}, the server response messages are different, but we
     * still use those two flag fields; see {@link #expectPLACING_ROBBER} javadoc.
     *
     * @see #playKnightCardIfShould()
     * @since 2.0.00
     */
    private void playKnightCard()
    {
        expectPLACING_ROBBER = true;
        waitingForGameState = true;
        counter = 0;
        client.playDevCard(game, SOCDevCardConstants.KNIGHT);
        pause(1500);
    }

    /**
     * On our turn, ask client to roll dice or play a knight;
     * on other turns, update flags to expect dice result.
     *<P>
     * Call when gameState {@link SOCGame#ROLL_OR_CARD} && ! {@link #waitingForGameState}.
     *<P>
     * Clears {@link #expectROLL_OR_CARD} to false.
     * Sets either {@link #expectDICERESULT}, or {@link #expectPLACING_ROBBER} and {@link #waitingForGameState}.
     *<P>
     * In scenario {@code _SC_PIRI}, don't play a Knight card before dice roll, because the scenario has
     * no robber: Playing before the roll won't un-block any of our resource hexes, and it might put us
     * over 7 resources.
     *
     * @see #playKnightCardIfShould()
     * @since 1.1.08
     */
    private void rollOrPlayKnightOrExpectDice()
    {
        expectROLL_OR_CARD = false;

        if ((! waitingForOurTurn) && ourTurn)
        {
            if (!expectPLAY1 && !expectDISCARD && !expectPLACING_ROBBER && ! (expectDICERESULT && (counter < 4000)))
            {
                /**
                 * if we have a knight card and the robber
                 * is on one of our numbers, play the knight card
                 */
                if (ourPlayerData.getInventory().hasPlayable(SOCDevCardConstants.KNIGHT)
                    && (rejectedPlayDevCardType != SOCDevCardConstants.KNIGHT)
                    && (! game.isGameOptionSet(SOCGameOption.K_SC_PIRI))  // scenario has no robber; wait until after roll
                    && ! ourPlayerData.getNumbers().hasNoResourcesForHex(game.getBoard().getRobberHex()))
                {
                    playKnightCard();  // sets expectPLACING_ROBBER, waitingForGameState
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
     * Sets {@link #whatWeWantToBuild} by calling {@link #buildRequestPlannedPiece()}
     * or using a Road Building dev card.
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
     *<LI> <tt>waitingFor...</tt> flags all false ({@link #waitingForGameState}, etc)
     *     except possibly {@link #waitingForSpecialBuild}
     *<LI> <tt>expect...</tt> flags all false ({@link #expectPLACING_ROAD}, etc)
     *<LI> ! {@link #waitingForOurTurn}
     *<LI> ! ({@link #expectROLL_OR_CARD} && (counter < 4000))
     *</UL>
     *<P>
     * May set any of these flags:
     * <UL>
     *<LI> {@link #waitingForGameState}, and {@link #expectWAITING_FOR_DISCOVERY} or {@link #expectWAITING_FOR_MONOPOLY}
     *<LI> {@link #waitingForTradeMsg} or {@link #waitingForTradeResponse} or {@link #doneTrading}
     *<LI> {@link #waitingForDevCard}, or {@link #waitingForGameState} and {@link #expectPLACING_SETTLEMENT} (etc).
     *<LI> {@link #waitingForPickSpecialItem}
     *<LI> Scenario actions such as {@link #waitingForSC_PIRI_FortressRequest}
     *</UL>
     *<P>
     * In a future iteration of the run() loop with the expected {@code PLACING_} state, the
     * bot will build {@link #whatWeWantToBuild} by calling {@link #placeIfExpectPlacing()}.
     *
     * @since 1.1.08
     * @throws IllegalStateException  if {@link #buildingPlan}{@link Stack#isEmpty() .isEmpty()}
     */
    private void buildOrGetResourceByTradeOrCard()
        throws IllegalStateException
    {
        if (buildingPlan.isEmpty())
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
            && ourPlayerData.getInventory().hasPlayable(SOCDevCardConstants.ROADS)
            && (rejectedPlayDevCardType != SOCDevCardConstants.ROADS))
        {
            //D.ebugPrintln("** Checking for Road Building Plan **");
            SOCPossiblePiece topPiece = buildingPlan.pop();

            //D.ebugPrintln("$ POPPED "+topPiece);
            if ((topPiece != null) && (topPiece instanceof SOCPossibleRoad))
            {
                SOCPossiblePiece secondPiece = (buildingPlan.isEmpty()) ? null : buildingPlan.peek();

                //D.ebugPrintln("secondPiece="+secondPiece);
                if ((secondPiece != null) && (secondPiece instanceof SOCPossibleRoad))
                {
                    roadBuildingPlan = true;

                    // TODO for now, 2 coastal roads/ships are always built as roads, not ships;
                    // builds ships only if the 2 possible pieces are non-coastal ships
                    if ((topPiece instanceof SOCPossibleShip)
                        && (! ((SOCPossibleShip) topPiece).isCoastalRoadAndShip )
                        && (secondPiece instanceof SOCPossibleShip)
                        && (! ((SOCPossibleShip) secondPiece).isCoastalRoadAndShip ))
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

        if (roadBuildingPlan)
        {
            return;  // <---- Early return: Road Building dev card ----
        }

        ///
        /// figure out what resources we need
        ///
        SOCPossiblePiece targetPiece = buildingPlan.peek();
        SOCResourceSet targetResources = targetPiece.getResourcesToBuild();  // may be null

        //D.ebugPrintln("^^^ targetPiece = "+targetPiece);
        //D.ebugPrintln("^^^ ourResources = "+ourPlayerData.getResources());

        negotiator.setTargetPiece(ourPlayerNumber, targetPiece);

        ///
        /// if we have a 2 free resources card and we need
        /// at least 2 resources, play the card
        ///
        if (gameStatePLAY1
            && (! ourPlayerData.hasPlayedDevCard())
            && ourPlayerData.getInventory().hasPlayable(SOCDevCardConstants.DISC)
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

        if (! expectWAITING_FOR_DISCOVERY)
        {
            ///
            /// if we have a monopoly card, play it
            /// and take what there is most of
            ///
            if (gameStatePLAY1
                && (! ourPlayerData.hasPlayedDevCard())
                && ourPlayerData.getInventory().hasPlayable(SOCDevCardConstants.MONO)
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

            if (! expectWAITING_FOR_MONOPOLY)
            {
                if (gameStatePLAY1 && (! doneTrading) && (! ourPlayerData.getResources().contains(targetResources)))
                {
                    waitingForTradeResponse = false;

                    if (robotParameters.getTradeFlag() == 1)
                    {
                        makeOffer(targetPiece);
                        // makeOffer will set waitingForTradeResponse or doneTrading.
                    }
                }

                if (gameStatePLAY1 && ! waitingForTradeResponse)
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
                if ((! (waitingForTradeMsg || waitingForTradeResponse))
                    && ourPlayerData.getResources().contains(targetResources))
                {
                    // Remember that targetPiece == buildingPlan.peek().
                    // Calls buildingPlan.pop().
                    // Checks against whatWeFailedToBuild to see if server has rejected this already.
                    // Calls client.buyDevCard or client.buildRequest.
                    // Sets waitingForDevCard, or waitingForGameState and expectPLACING_SETTLEMENT (etc).
                    // Sets waitingForPickSpecialItem if target piece is SOCPossiblePickSpecialItem.

                    buildRequestPlannedPiece();
                }
            }
        }
    }

    /**
     * On our turn, server is expecting us to choose a placement location for a {@link SOCInventoryItem}.
     * Try to plan a location and send placement request command(s).
     *<P>
     * Call only when these conditions are all true:
     *<UL>
     * <LI> {@link #ourTurn} &amp;&amp; ! {@link #waitingForOurTurn}
     * <LI> game state {@link SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM}
     * <LI> ! {@link #waitingForGameState}
     *</UL>
     * If the piece can be planned and placed, will set {@link #waitingForGameState}
     * and either {@link #expectPLAY1} or another expect flag, and send placement commands.
     * If nothing could be planned and placed, does not set {@link #waitingForGameState}.
     *
     * @throws IllegalStateException if called with {@link #waitingForGameState} true
     * @since 2.0.00
     */
    protected void planAndPlaceInvItem()
        throws IllegalStateException
    {
        if (waitingForGameState)
            throw new IllegalStateException();

        SOCInventoryItem itm = game.getPlacingItem();
        if (itm == null)
            return;  // in case of bugs; shouldn't happen in a consistent game

        if (game.isGameOptionSet(SOCGameOption.K_SC_FTRI))
        {
            planAndPlaceInvItemPlacement_SC_FTRI(itm);
        } else {
            System.err.println
                ("L2720: Game " + game.getName() + " bot " + client.getNickname()
                 + ": No PLACING_INV_ITEM handler for scenario " + game.getGameOptionStringValue("SC"));

            // End turn? Probably cleaner to let server force-end it. So do nothing here.
            // TODO revisit that: Per PLACING_INV_ITEM javadoc:
            // "For some kinds of item, placement can be canceled by calling ga.cancelPlaceInventoryItem"
        }
    }

    /**
     * For scenario {@link SOCGameOption#K_SC_FTRI _SC_FTRI}, try to plan a location and
     * send placement request command(s) for a "gift" trade port on the player's turn.
     *<P>
     * Calls {@link SOCPlayer#getPortMovePotentialLocations(boolean)}; this method is safe to call
     * when the player has nowhere to place.
     *<P>
     * Called from {@link #planAndPlaceInvItem()}, see that method for required conditions to call
     * and described brain-state results after calling.  Assumes caller has checked those conditions
     * and:
     *<UL>
     * <LI> {@link #ourTurn}
     * <LI> game state {@link SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM} or {@link SOCGame#PLAY1 PLAY1}
     * <LI> ! {@link #waitingForGameState}
     *</UL>
     *
     * @param itm The gift port which must be placed; not {@code null}.
     *     Can be as prompted by server, or from player's {@link SOCInventoryItem}s.
     * @return true if {@code itm} was planned and/or placed
     * @since 2.0.00
     */
    private boolean planAndPlaceInvItemPlacement_SC_FTRI(final SOCInventoryItem itm)
    {
        if (itm == null)
            return false;
        if ((rejectedPlayInvItem != null) && (itm.itype == rejectedPlayInvItem.itype))
            return false;  // rbrain must plan something else instead

        List<Integer> edges = ourPlayerData.getPortMovePotentialLocations(true);
        if (edges.isEmpty())
        {
            // TODO any action to keep it moving?
            rejectedPlayInvItem = itm;  // don't re-plan same thing for next move this turn

            return false;  // <--- Early return: No choices despite gamestate ---
        }

        final int ptype = itm.itype;  // reminder: will be negative or 0
        waitingForGameState = true;
        counter = 0;

        if (game.getGameState() == SOCGame.PLACING_INV_ITEM)
        {
            // This is a first draft for overall functionality, not for the smartest placement strategy.
            // TODO smarter planning; consider settlement & dice number locations / hex types against
            // the port type from itm if not null; if null iterate inv items
            // but reject if chooses rejectedPlayInvItem again
            final int edge = edges.get(0).intValue();

            // Expected response from server: GAMESTATE(PLAY1) and
            // then SIMPLEREQUEST confirming placement location,
            // or SIMPLEREQUEST rejecting it; TODO don't plan further building
            // until that's seen (new flag field?) because will need to recalc
            // building speed estimates (BSEs) with the new port.

            expectPLAY1 = true;
            client.simpleRequest(game, ourPlayerNumber, SOCSimpleRequest.TRADE_PORT_PLACE, edge, 0);
        } else {
            // State PLAY1; assume inv is from inventory

            // Expected response from server: GAMESTATE(PLACING_INV_ITEM).
            // If client's request is rejected because nowhere to place right now,
            // will respond with SOCInventoryItemAction(CANNOT_PLAY)
            // and rbrain will clear expectPLACING_INV_ITEM.

            expectPLACING_INV_ITEM = true;
            client.playInventoryItem(game, ourPlayerNumber, ptype);
        }

        pause(1000);
        return true;
    }

    /**
     * Handle a PUTPIECE for this game, by updating game data.
     * For initial roads, also track their initial settlement in SOCPlayerTracker.
     * In general, most tracking is done a bit later in {@link #handlePUTPIECE_updateTrackers(int, int, int)}.
     * @since 1.1.08
     */
    private void handlePUTPIECE_updateGameData(SOCPutPiece mes)
    {
        switch (mes.getPieceType())
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
            // fall through to default

        default:
            SOCDisplaylessPlayerClient.handlePUTPIECE(mes, game);
            break;
        }
    }

    /**
     * Handle a CANCELBUILDREQUEST for this game.
     *<P>
     *<b> During game startup</b> (START1B, START2B, or START3B): <BR>
     *    When sent from server to client, CANCELBUILDREQUEST means the current
     *    player wants to undo the placement of their initial settlement.
     *    This handler method calls {@link SOCGame#undoPutInitSettlement(SOCPlayingPiece)}
     *    and {@link SOCPlayerTracker#setPendingInitSettlement(SOCSettlement) tracker.setPendingInitSettlement(null)}.
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
                // Human player placed, then cancelled placement
                // (assume mes.getPieceType() == SOCPlayingPiece.SETTLEMENT).
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
        case SOCGame.PLACING_SHIP:
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
                System.err.println
                    ("L2521 SOCRobotBrain: " + client.getNickname() + ": Unhandled CANCELBUILDREQUEST at state " + gstate);
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
            if (giveSet.contains(rsrcType))
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
            if (getSet.contains(rsrcType))
            {
                D.ebugPrintln("%%% player " + offer.getFrom() + " wants to buy " + rsrcType
                    + " and therefore does not want to sell it");
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

                    if (offeredTo[i] && ! offerRejections[i])
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
            return;  // <--- Early return: SOCRobotNegotiator.IGNORE_OFFER ---

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

            if (! waitingForTradeResponse)
                client.rejectOffer(game);

            break;

        case SOCRobotNegotiator.COUNTER_OFFER:

            if (! makeCounterOffer(offer))
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
                if (getSet.contains(rsrcType) && ! negotiator.wantsAnotherOffer(rejector, rsrcType))
                    negotiator.markAsNotSelling(rejector, rsrcType);
            }

            offerRejections[mes.getPlayerNumber()] = true;

            boolean everyoneRejected = true;
            D.ebugPrintln("ourPlayerData.getCurrentOffer() = " + ourPlayerData.getCurrentOffer());

            boolean[] offeredTo = ourPlayerData.getCurrentOffer().getTo();

            for (int i = 0; i < game.maxPlayers; i++)
            {
                D.ebugPrintln("offerRejections[" + i + "]=" + offerRejections[i]);

                if (offeredTo[i] && ! offerRejections[i])
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
                            if (getSet.contains(rsrcType) && ! negotiator.wantsAnotherOffer(rejector, rsrcType))
                                negotiator.markAsNotSelling(rejector, rsrcType);
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle a DEVCARDACTION for this game.
     * No brain-specific action.
     * @since 1.1.08
     */
    private void handleDEVCARDACTION(SOCDevCardAction mes)
    {
        SOCInventory cardsInv = game.getPlayer(mes.getPlayerNumber()).getInventory();
        final int cardType = mes.getCardType();

        switch (mes.getAction())
        {
        case SOCDevCardAction.DRAW:
            cardsInv.addDevCard(1, SOCInventory.NEW, cardType);
            break;

        case SOCDevCardAction.PLAY:
            cardsInv.removeDevCard(SOCInventory.OLD, cardType);
            break;

        case SOCDevCardAction.ADD_OLD:
            cardsInv.addDevCard(1, SOCInventory.OLD, cardType);
            break;

        case SOCDevCardAction.ADD_NEW:
            cardsInv.addDevCard(1, SOCInventory.NEW, cardType);
            break;
        }
    }

    /**
     * Handle a PUTPIECE for this game, by updating {@link SOCPlayerTracker}s.
     * Also handles the "move piece to here" part of MOVEPIECE.
     *<P>
     * For initial placement of our own pieces, this method also checks
     * and clears expectPUTPIECE_FROM_START1A, and sets expectSTART1B, etc.
     * The final initial putpiece clears expectPUTPIECE_FROM_START2B and sets expectROLL_OR_CARD.
     * As part of the PUTPIECE request, brain set those expectPUTPIECE flags in {@link #placeIfExpectPlacing()}.
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

        if (expectPUTPIECE_FROM_START1A && (pieceType == SOCPlayingPiece.SETTLEMENT)
            && (coord == ourPlayerData.getLastSettlementCoord()))
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

        if (expectPUTPIECE_FROM_START2A && (pieceType == SOCPlayingPiece.SETTLEMENT)
            && (coord == ourPlayerData.getLastSettlementCoord()))
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
                expectROLL_OR_CARD = true;    // wait for regular game play to start; other players might still place first
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
            expectROLL_OR_CARD = true;
        }

    }

    /**
     * Have the client ask to build our top planned piece
     * (calls {@link #buildingPlan}{@link Stack#pop() .pop()}),
     * unless we've already been told by the server to not build it.
     * Sets {@link #whatWeWantToBuild}, {@link #waitingForDevCard},
     * or {@link #waitingForPickSpecialItem}.
     * Called from {@link #buildOrGetResourceByTradeOrCard()}.
     *<P>
     * Checks against {@link #whatWeFailedToBuild} to see if server has rejected this already.
     * Calls <tt>client.buyDevCard()</tt> or <tt>client.buildRequest()</tt>.
     * Sets {@link #waitingForDevCard} or {@link #waitingForPickSpecialItem},
     * or sets {@link #waitingForGameState} and a flag like {@link #expectPLACING_SETTLEMENT} (etc).
     *<P>
     * Preconditions: Call only when:
     *<UL>
     * <LI> Gamestate is {@link SOCGame#PLAY1} or {@link SOCGame#SPECIAL_BUILDING}
     * <LI> <tt>! ({@link #waitingForTradeMsg} || {@link #waitingForTradeResponse})</tt>
     * <LI> ourPlayerData.getResources().{@link SOCResourceSet#contains(soc.game.ResourceSet) contains}(targetPieceResources)
     *</UL>
     *
     * @see #placeIfExpectPlacing()
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
                System.err.println("L2733: " + ourPlayerData.getName() + ": !!! BUILD REQUEST FOR A SHIP AT "
                    + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                D.ebugPrintln("!!! BUILD REQUEST FOR A SHIP AT " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                client.buildRequest(game, SOCPlayingPiece.SHIP);
            } else {
                // We already tried to build this.
                cancelWrongPiecePlacementLocal(whatWeWantToBuild);
                // cancel sets whatWeWantToBuild = null;
            }

            break;

        case SOCPossiblePiece.PICK_SPECIAL:
            {
                final SOCPossiblePickSpecialItem psi = (SOCPossiblePickSpecialItem) targetPiece;
                waitingForPickSpecialItem = psi.typeKey;
                whatWeWantToBuild = null;  // targetPiece isn't a SOCPlayingPiece
                counter = 0;

                client.pickSpecialItem(game, psi.typeKey, psi.gi, psi.pi);
            }
            break;

        default:
            // shouldn't occur: print for debugging
            System.err.println
                (ourPlayerData.getName() + ": buildRequestPlannedPiece: Unknown piece type " + targetPiece.getType());
        }
    }

    /**
     * Plan the next building plan and target.
     * Should be called from {@link #run()} under these conditions: <BR>
     * ( !expectPLACING_ROBBER && buildingPlan.empty() && (ourPlayerData.getResources().getTotal() > 1)
     * && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
     *<P>
     * Sets these fields and makes these calls:
     *<UL>
     * <LI> {@link SOCRobotDM#planStuff(int) SOCRobotDM.planStuff}
     *      ({@link SOCRobotDM#FAST_STRATEGY FAST_STRATEGY} or {@link SOCRobotDM#SMART_STRATEGY SMART_STRATEGY})
     * <LI> {@link #buildingPlan}
     * <LI> {@link #lastTarget}
     * <LI> {@link SOCRobotNegotiator#setTargetPiece(int, SOCPossiblePiece)}
     *</UL>
     *
     * @since 1.1.08
     */
    private final void planBuilding()
    {
        decisionMaker.planStuff(robotParameters.getStrategyType());

        if (! buildingPlan.empty())
        {
            lastTarget = buildingPlan.peek();
            negotiator.setTargetPiece(ourPlayerNumber, lastTarget);
        }
    }

    /**
     * Handle a PLAYERELEMENTS for this game.
     * See {@link #handlePLAYERELEMENT(SOCPlayer, int, int, int, int)} for actions taken.
     * @since 2.0.00
     */
    private void handlePLAYERELEMENTS(SOCPlayerElements mes)
    {
        final int pn = mes.getPlayerNumber();
        final SOCPlayer pl = (pn != -1) ? game.getPlayer(pn) : null;
        final int action = mes.getAction();
        final int[] etypes = mes.getElementTypes(), amounts = mes.getAmounts();

        for (int i = 0; i < etypes.length; ++i)
            handlePLAYERELEMENT(pl, pn, action, etypes[i], amounts[i]);
    }

    /**
     * Handle a PLAYERELEMENT for this game.
     * See {@link #handlePLAYERELEMENT(SOCPlayer, int, int, int, int)} for actions taken.
     * @since 1.1.08
     */
    private void handlePLAYERELEMENT(SOCPlayerElement mes)
    {
        final int pn = mes.getPlayerNumber();
        final int action = mes.getAction(), amount = mes.getAmount();
        final int etype = mes.getElementType();

        handlePLAYERELEMENT(null, pn, action, etype, amount);
    }

    /**
     * Handle a player information update from a {@link SOCPlayerElement} or {@link SOCPlayerElements} message:
     * Update a player's amount of a resource or a building type.
     *<P>
     * If this during the {@link SOCGame#ROLL_OR_CARD} state, then update the
     * {@link SOCRobotNegotiator}'s is-selling flags.
     *<P>
     * If our player is losing a resource needed for the {@link #buildingPlan},
     * clear the plan if this is for the Special Building Phase (on the 6-player board).
     * In normal game play, we clear the building plan at the start of each turn.
     *<P>
     * Otherwise, only the game data is updated, nothing brain-specific.
     *
     * @param pl   Player to update; some elements take null. If null and {@code pn != -1}, will find {@code pl}
     *     using {@link SOCGame#getPlayer(int) game.getPlayer(pn)}.
     * @param pn   Player number from message (sometimes -1 for none or all)
     * @param action   {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN GAIN},
     *     or {@link SOCPlayerElement#LOSE LOSE}
     * @param etype  Element type, such as {@link SOCPlayerElement#SETTLEMENTS} or {@link SOCPlayerElement#NUMKNIGHTS}
     * @param amount  The new value to set, or the delta to gain/lose
     * @since 2.0.00
     */
    private void handlePLAYERELEMENT
        (SOCPlayer pl, final int pn, final int action, final int etype, final int amount)
    {
        if ((pl == null) && (pn != -1))
            pl = game.getPlayer(pn);

        switch (etype)
        {
        case SOCPlayerElement.ROADS:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.ROAD, amount);
            break;

        case SOCPlayerElement.SETTLEMENTS:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.SETTLEMENT, amount);
            break;

        case SOCPlayerElement.CITIES:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.CITY, amount);
            break;

        case SOCPlayerElement.SHIPS:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.SHIP, amount);
            break;

        case SOCPlayerElement.NUMKNIGHTS:
            // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numKnights
                (game, pl, action, amount);
            break;

        case SOCPlayerElement.CLAY:
            handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.CLAY, "CLAY", amount);
            break;

        case SOCPlayerElement.ORE:
            handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.ORE, "ORE", amount);
            break;

        case SOCPlayerElement.SHEEP:
            handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.SHEEP, "SHEEP", amount);
            break;

        case SOCPlayerElement.WHEAT:
            handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.WHEAT, "WHEAT", amount);
            break;

        case SOCPlayerElement.WOOD:
            handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.WOOD, "WOOD", amount);
            break;

        case SOCPlayerElement.UNKNOWN:
            /**
             * Note: if losing unknown resources, we first
             * convert player's known resources to unknown resources,
             * then remove mes's unknown resources from player.
             */
            handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.UNKNOWN, "UNKNOWN", amount);
            break;

        case SOCPlayerElement.RESOURCE_COUNT:
            if (amount != pl.getResources().getTotal())
            {
                SOCResourceSet rsrcs = pl.getResources();

                if (D.ebugOn)
                {
                    client.sendText(game, ">>> RESOURCE COUNT ERROR FOR PLAYER " + pl.getPlayerNumber()
                        + ": " + amount + " != " + rsrcs.getTotal());
                }

                //
                //  fix it
                //
                if (pl.getPlayerNumber() != ourPlayerNumber)
                {
                    rsrcs.clear();
                    rsrcs.setAmount(amount, SOCResourceConstants.UNKNOWN);
                }
            }
            break;

        case SOCPlayerElement.SCENARIO_WARSHIP_COUNT:
            if (expectPLACING_ROBBER && (action == SOCPlayerElement.GAIN))
            {
                // warship card successfully played; clear the flag fields
                expectPLACING_ROBBER = false;
                waitingForGameState = false;
            }
            // fall through to default, so handlePLAYERELEMENT_simple will update game data

        default:
            // handle ASK_SPECIAL_BUILD, NUM_PICK_GOLD_HEX_RESOURCES, SCENARIO_CLOTH_COUNT, etc;
            // those are all self-contained informational fields that don't need any reaction from a bot.

            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (game, pl, pn, action, etype, amount, ourPlayerName);
            break;

        }

        ///
        /// if this during the ROLL_OR_CARD state, then update the is selling flags
        ///
        if (game.getGameState() == SOCGame.ROLL_OR_CARD)
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
     * Before v2.0.00 this method directly took a {@link SOCPlayerElement} instead of that message's
     * {@code action} and {@code amount} fields.
     *
     * @param pl       Player to update
     * @param action   {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN GAIN},
     *     or {@link SOCPlayerElement#LOSE LOSE}
     * @param rtype    Type of resource, like {@link SOCResourceConstants#CLAY}
     * @param rtypeStr Resource type name, for debugging
     * @param amount   The new value to set, or the delta to gain/lose
     */
    @SuppressWarnings("unused")  // unnecessary dead-code warning "if (D.ebugOn)"
    protected void handlePLAYERELEMENT_numRsrc
        (SOCPlayer pl, final int action, int rtype, String rtypeStr, final int amount)
    {
        /**
         * for SET, check the amount of unknown resources against
         * what we think we know about our player.
         */
        if (D.ebugOn && (pl == ourPlayerData) && (action == SOCPlayerElement.SET))
        {
            if (amount != ourPlayerData.getResources().getAmount(rtype))
            {
                client.sendText(game, ">>> RSRC ERROR FOR " + rtypeStr
                    + ": " + amount + " != " + ourPlayerData.getResources().getAmount(rtype));
            }
        }

        /**
         * Update game data.
         */
        SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
            (pl, action, rtype, amount);

        /**
         * Clear building plan, if we just lost a resource we need.
         * Only necessary for Special Building Phase (6-player board),
         * because in normal game play, we clear the building plan
         * at the start of each turn.
         */
        if (waitingForSpecialBuild && (pl == ourPlayerData)
            && (action != SOCPlayerElement.GAIN)
            && ! buildingPlan.isEmpty())
        {
            final SOCPossiblePiece targetPiece = buildingPlan.peek();
            final SOCResourceSet targetResources = targetPiece.getResourcesToBuild();  // may be null

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
     * Called only after {@link SOCGame#putPiece(SOCPlayingPiece)}
     * or {@link SOCGame#putTempPiece(SOCPlayingPiece)}.
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
            Enumeration<SOCRoutePiece> roadEnum = board.getRoadsAndShips().elements();

            while (roadEnum.hasMoreElements())
            {
                final SOCRoutePiece rs = roadEnum.nextElement();

                if (rs.getCoordinates() == adjEdge)
                {
                    final int roadPN = rs.getPlayerNumber();

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
     * @param newPiece  The newly placed road or ship
     * @param isCancel Is this our own robot's placement, rejected by the server?
     *     If so, this method call will cancel its placement within the game data / robot data.
     */
    protected void trackNewRoadOrShip(final SOCRoutePiece newPiece, final boolean isCancel)
    {
        final int newRoadPN = newPiece.getPlayerNumber();

        Iterator<SOCPlayerTracker> trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();
            tracker.takeMonitor();

            try
            {
                if (! isCancel)
                    tracker.addNewRoadOrShip(newPiece, playerTrackers);
                else
                    tracker.cancelWrongRoadOrShip(newPiece);
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
     *  Must update game data by calling {@link SOCGame#setGameState(int)} before calling this method.
     *<P>
     *  This method increments {@link #failedBuildingAttempts},
     *  but won't leave the game if we've failed too many times.
     *  The brain's run loop should make that decision.
     *<UL>
     * <LI> If {@link SOCGame#getGameState()} is {@link SOCGame#PLAY1},
     *   server likely denied us due to resources, not due to building plan
     *   being interrupted by another player's building before our special building phase.
     *   (Could also be due to a bug in the chosen building plan.)
     *   Will clear our building plan so we'll make a new one.
     * <LI> In other gamestates, assumes requested piece placement location was illegal.
     *   Will call {@link #cancelWrongPiecePlacementLocal(SOCPlayingPiece)}
     *   so we don't try again to build there.
     * <LI> Either way, sends a {@code CancelBuildRequest} message to the server.
     *</UL>
     *
     * @param mes  Cancel message from server, including piece type
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
        waitingForGameState = false;

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
            if (! cancelBuyDevCard)
            {
                // special building, currently in state PLACING_* ;
                // get our resources back, get state PLAY1 or SPECIALBUILD
                waitingForGameState = true;
                expectPLAY1 = true;
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
     * build there. Since we treat it like another player's new placement, we
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
                trackNewRoadOrShip((SOCRoutePiece) cancelPiece, true);
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
     * Kill this brain's thread: clears its "alive" flag, stops pinger,
     * puts a null message into the event queue.
     */
    public void kill()
    {
        final SOCRobotPinger p = pinger;

        alive = false;

        try
        {
            if (p != null)
                p.stopPinger();
            gameEventQ.put(null);
        }
        catch (Exception exc) {}
    }

    /**
     * pause for a bit.
     *<P>
     * When {@link SOCGame#isBotsOnly}, pause only 25% as long, to quicken the simulation
     * but not make it too fast to allow a person to observe.
     * Can change {@link #BOTS_ONLY_FAST_PAUSE_FACTOR} to adjust that percentage.
     *<P>
     * In a 6-player game, pause only 75% as long, to shorten the overall game delay,
     * except if {@link #waitingForTradeResponse}.
     * This is indicated by the {@link #pauseFaster} flag.
     *
     * @param msec  number of milliseconds to pause
     */
    public void pause(int msec)
    {
        if (game.isBotsOnly)
        {
            msec = (int) (msec * BOTS_ONLY_FAST_PAUSE_FACTOR);
            if (msec == 0)
                return;  // will still yield within run() loop
        } else if (pauseFaster && ! waitingForTradeResponse) {
            msec = (msec / 2) + (msec / 4);
        }

        try
        {
            yield();
            if (msec > 2)  // skip very short sleeps from small BOTS_ONLY_FAST_PAUSE_FACTOR
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
            System.err.println("robot assert failed: initSettlement -1, "
                + ourPlayerData.getName() + " leaving game " + game.getName());
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
     * pick what's rare from {@link OpeningBuildStrategy#estimateResourceRarity()}.
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
            final SOCPossiblePiece targetPiece = buildingPlan.peek();
            targetResources = targetPiece.getResourcesToBuild();  // may be null
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

        client.pickResources(game, resourceChoices);
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

        while (! queue.empty())
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

        while (! stack.empty())
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
            while (tempTO.contains(SOCSettlement.COST))
            {
                score += 2;
                tempTO.subtract(SOCSettlement.COST);
            }
        }

        if ((ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 1) && (ourPlayerData.hasPotentialRoad()))
        {
            while (tempTO.contains(SOCRoad.COST))
            {
                score += 1;
                tempTO.subtract(SOCRoad.COST);
            }
        }

        if ((ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) >= 1) && (ourPlayerData.hasPotentialCity()))
        {
            while (tempTO.contains(SOCCity.COST))
            {
                score += 2;
                tempTO.subtract(SOCCity.COST);
            }
        }

        //D.ebugPrintln("Score for "+tradeOutcome+" : "+score);
        return score;
    }

    /**
     * Make bank trades or port trades to get the target resources, if possible.
     *
     * @param targetResources  the resources that we want, can be {@code null} for an empty set (method returns false)
     * @return true if we sent a request to trade, false if
     *     we already have the resources or if we don't have
     *     enough to trade in for <tt>targetResources</tt>.
     */
    protected boolean tradeToTarget2(SOCResourceSet targetResources)
    {
        if ((targetResources == null) || ourPlayerData.getResources().contains(targetResources))
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
     * If offered to our player, calls {@link SOCRobotNegotiator#considerOffer2(SOCTradeOffer, int)}.
     *
     * @param offer  the offer to consider
     * @return a code from {@link SOCRobotNegotiator} that represents how we want to respond:
     *     {@link SOCRobotNegotiator#IGNORE_OFFER IGNORE_OFFER},
     *     {@link SOCRobotNegotiator#ACCEPT_OFFER ACCEPT_OFFER},
     *     {@link SOCRobotNegotiator#REJECT_OFFER REJECT_OFFER},
     *     or {@link SOCRobotNegotiator#COUNTER_OFFER COUNTER_OFFER}.
     *     Note: any negative value returned means we do nothing
     *     ({@link SOCRobotNegotiator#IGNORE_OFFER IGNORE_OFFER}).
     * @see #makeCounterOffer(SOCTradeOffer)
     */
    protected int considerOffer(SOCTradeOffer offer)
    {
        int response = SOCRobotNegotiator.IGNORE_OFFER;

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
     * Plan a counter offer to another player, and make it from our client.
     * Calls {@link SOCRobotNegotiator#makeCounterOffer(SOCTradeOffer)},
     * then {@link #ourPlayerData}{@link SOCPlayer#setCurrentOffer(SOCTradeOffer) .setCurrentOffer(..)}
     * with the result or {@code null}. Updates {@link #waitingForTradeResponse} flag.
     * If no counteroffer is made here, sets {@link #doneTrading}.
     *
     * @param offer  the other player's offer
     * @return true if we made and sent an offer
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
     *             from {@link SOCPossiblePiece#getResourcesToBuild()}
     *             for {@link #buildingPlan}.peek()
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
     * Choose the resources we need most; used when we want to play a discovery development card
     * or when a Gold Hex number is rolled.
     * If returns true, has called {@link #chooseFreeResources(SOCResourceSet, int, boolean)}
     * and has set {@link #resourceChoices}.
     *
     * @param targetResources  Resources needed to build our next planned piece,
     *             from {@link SOCPossiblePiece#getResourcesToBuild()}
     *             for {@link #buildingPlan}.
     *             If {@code null}, returns false (no more resources required).
     * @param numChoose  Number of resources to choose
     * @param chooseIfNotNeeded  Even if we find we don't need them, choose anyway;
     *             set true for Gold Hex choice, false for Discovery card pick.
     * @return  true if we need <tt>numChoose</tt> resources
     * @since 2.0.00
     */
    private boolean chooseFreeResourcesIfNeeded
        (SOCResourceSet targetResources, final int numChoose, final boolean chooseIfNotNeeded)
    {
        if (targetResources == null)
            return false;

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
            if (targetResources == null)  // can be null from SOCPossiblePickSpecialItem.cost
                break;

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

                    SOCPossiblePiece targetPiece = buildingPlan.elementAt(i);
                    targetResources = targetPiece.getResourcesToBuild();  // may be null

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
           if (expectROLL_OR_CARD)
           //D.ebugPrintln("expectROLL_OR_CARD");
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
