/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2011 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;

import soc.message.SOCAcceptOffer;
import soc.message.SOCCancelBuildRequest;
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
import soc.message.SOCMoveRobber;
import soc.message.SOCPlayerElement;
import soc.message.SOCPotentialSettlements;
import soc.message.SOCPutPiece;
import soc.message.SOCRejectOffer;
import soc.message.SOCResourceCount;
import soc.message.SOCSetPlayedDevCard;
import soc.message.SOCSetTurn;
import soc.message.SOCTurn;

import soc.server.SOCServer;

import soc.util.CappedQueue;
import soc.util.CutoffExceededException;
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
     */
    protected SOCPlayer ourPlayerData;
    
    /**
     * Dummy player for cancelling bad placements
     */
    protected SOCPlayer dummyCancelPlayerData;

    /**
     * The queue of game messages; contents are {@link SOCMessage}.
     */
    protected CappedQueue gameEventQ;

    /**
     * The game messages received this turn / previous turn; contents are {@link SOCMessage}.
     * @since 1.1.13
     */
    private Vector turnEventsCurrent, turnEventsPrev;

    /**
     * A counter used to measure passage of time
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
     */
    protected Stack buildingPlan;

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
     * this is the resource we want to monopolize
     */
    protected int monopolyChoice;

    /**
     * our player tracker
     */
    protected SOCPlayerTracker ourPlayerTracker;

    /**
     * trackers for all players (one per player, including this robot)
     */
    protected HashMap playerTrackers;

    /**
     * the thing that determines what we want to build next
     */
    protected SOCRobotDM decisionMaker;

    /**
     * the thing that determines how we negotiate
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
     * true when the robber will move because a seven was rolled
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
     * the game state before the current one
     */
    protected int oldGameState;

    /**
     * used to cache resource estimates for the board
     */
    protected int[] resourceEstimates;

    /**
     * used in planning where to put our first and second settlements
     */
    protected int firstSettlement;

    /**
     * used in planning where to put our first and second settlements
     */
    protected int secondSettlement;

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
     *
     * @param rc  the robot client
     * @param params  the robot parameters
     * @param ga  the game we're playing
     * @param mq  the message queue
     */
    public SOCRobotBrain(SOCRobotClient rc, SOCRobotParameters params, SOCGame ga, CappedQueue mq)
    {
        client = rc;
        robotParameters = params.copyIfOptionChanged(ga.getGameOptions());
        game = ga;
        gameIs6Player = (ga.maxPlayers > 4);
        pauseFaster = gameIs6Player;
        gameEventQ = mq;
        turnEventsCurrent = new Vector();
        turnEventsPrev = new Vector();
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

        buildingPlan = new Stack();
        resourceChoices = new SOCResourceSet();
        resourceChoices.add(2, SOCResourceConstants.CLAY);
        monopolyChoice = SOCResourceConstants.SHEEP;
        pinger = new SOCRobotPinger(gameEventQ, game.getName(), client.getNickname() + "-" + game.getName());
        dRecorder = new DebugRecorder[2];
        dRecorder[0] = new DebugRecorder();
        dRecorder[1] = new DebugRecorder();
        currentDRecorder = 0;
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
    public HashMap getPlayerTrackers()
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
     * SITDOWN is received (robotclient calls setOurPlayerData()),
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
     * @return the building plan
     */
    public Stack getBuildingPlan()
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
     * Find our player data using our nickname
     */
    public void setOurPlayerData()
    {
        ourPlayerData = game.getPlayer(client.getNickname());
        ourPlayerTracker = new SOCPlayerTracker(ourPlayerData, this);
        int opn = ourPlayerData.getPlayerNumber();
        playerTrackers = new HashMap();
        playerTrackers.put(new Integer(opn), ourPlayerTracker);

        for (int pn = 0; pn < game.maxPlayers; pn++)
        {
            if ((pn != opn) && ! game.isSeatVacant(pn))
            {
                SOCPlayerTracker tracker = new SOCPlayerTracker(game.getPlayer(pn), this);
                playerTrackers.put(new Integer(pn), tracker);
            }
        }

        decisionMaker = new SOCRobotDM(this);
        negotiator = new SOCRobotNegotiator(this);
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
        System.err.println("Robot internal state: " + client.getNickname() + " in game " + game.getName() + ": gs=" + game.getGameState());
        if (game.getGameState() == SOCGame.WAITING_FOR_DISCARDS)
            System.err.println("  bot card count = " + ourPlayerData.getResources().getTotal());
        final String[] s = {
            "ourTurn", "doneTrading",
            "waitingForGameState", "waitingForOurTurn", "waitingForTradeMsg", "waitingForDevCard", "waitingForTradeResponse",
            "moveRobberOnSeven", "expectSTART1A", "expectSTART1B", "expectSTART2A", "expectSTART2B",
            "expectPLAY", "expectPLAY1", "expectPLACING_ROAD", "expectPLACING_SETTLEMENT", "expectPLACING_CITY",
            "expectPLACING_ROBBER", "expectPLACING_FREE_ROAD1", "expectPLACING_FREE_ROAD2",
            "expectPUTPIECE_FROM_START1A", "expectPUTPIECE_FROM_START1B", "expectPUTPIECE_FROM_START2A", "expectPUTPIECE_FROM_START2B",
            "expectDICERESULT", "expectDISCARD", "expectMOVEROBBER", "expectWAITING_FOR_DISCOVERY", "expectWAITING_FOR_MONOPOLY"
        };
        final boolean[] b = {
            ourTurn, doneTrading,
            waitingForGameState, waitingForOurTurn, waitingForTradeMsg, waitingForDevCard, waitingForTradeResponse,
            moveRobberOnSeven, expectSTART1A, expectSTART1B, expectSTART2A, expectSTART2B,
            expectPLAY, expectPLAY1, expectPLACING_ROAD, expectPLACING_SETTLEMENT, expectPLACING_CITY,
            expectPLACING_ROBBER, expectPLACING_FREE_ROAD1, expectPLACING_FREE_ROAD2,
            expectPUTPIECE_FROM_START1A, expectPUTPIECE_FROM_START1B, expectPUTPIECE_FROM_START2A, expectPUTPIECE_FROM_START2B,
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
            sb.append(b[i]);
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
    private static void debugPrintTurnMessages(Vector msgV, final String msgDesc)
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
     * and deal with each one.
     * Remember that we're sent a {@link SOCTimingPing} once per second.
     */
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
                /** Our player number */
                final int ourPN = ourPlayerData.getPlayerNumber();

                //
                // Along with actual game events, the pinger sends a SOCGameTextMsg
                // once per second, to aid the robot's timekeeping counter.
                //

                while (alive)
                {
                    SOCMessage mes;

                    //if (!gameEventQ.empty()) {
                    mes = (SOCMessage) gameEventQ.get();  // Sleeps until message received

                    //} else {
                    //mes = null;
                    //}
                    final int mesType;

                    if (mes != null)
                    {
                        mesType = mes.getType();
                        if (mesType != SOCMessage.GAMETEXTMSG)
                            turnEventsCurrent.addElement(mes);
                        if (D.ebugOn)
                            D.ebugPrintln("mes - " + mes);

                        // Debug aid: when looking at message contents: avoid pings:
                        // check here for (mesType != SOCMessage.GAMETEXTMSG).
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
                            Vector tmp = turnEventsPrev;
                            turnEventsPrev = turnEventsCurrent;
                            tmp.clear();
                            turnEventsCurrent = tmp;
                        }
                    }

                    if (game.getCurrentPlayerNumber() == ourPN)
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
                            if (pl.getPlayerNumber() != ourPN)
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
                        game.getBoard().setRobberHex(((SOCMoveRobber) mes).getCoordinates(), true);
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
                            if ((ourPN == (((SOCAcceptOffer) mes).getOfferingNumber()))
                                || (ourPN == ((SOCAcceptOffer) mes).getAcceptingNumber()))
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
                        handleDEVCARD((SOCDevCard) mes);
                        break;

                    case SOCMessage.SETPLAYEDDEVCARD:
                        {
                        SOCPlayer player = game.getPlayer(((SOCSetPlayedDevCard) mes).getPlayerNumber());
                        player.setPlayedDevCard(((SOCSetPlayedDevCard) mes).hasPlayedDevCard());
                        }
                        break;

                    case SOCMessage.POTENTIALSETTLEMENTS:
                        {
                        SOCPlayer player = game.getPlayer(((SOCPotentialSettlements) mes).getPlayerNumber());
                        player.setPotentialSettlements(((SOCPotentialSettlements) mes).getPotentialSettlements());
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

                    if ((game.getGameState() == SOCGame.PLACING_ROBBER) && (!waitingForGameState))
                    {
                        expectPLACING_ROBBER = false;

                        if ((!waitingForOurTurn) && (ourTurn))
                        {
                            if (!((expectPLAY || expectPLAY1) && (counter < 4000)))
                            {
                                if (moveRobberOnSeven == true)
                                {
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
                                        expectPLAY = true;
                                    }
                                    else if (oldGameState == SOCGame.PLAY1)
                                    {
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
                                client.monopolyPick(game, monopolyChoice);
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
                        && (!waitingForGameState) && (!waitingForTradeMsg) && (!waitingForTradeResponse) && (!waitingForDevCard)
                        && (!expectPLACING_ROAD) && (!expectPLACING_SETTLEMENT) && (!expectPLACING_CITY) && (!expectPLACING_ROBBER) && (!expectPLACING_FREE_ROAD1) && (!expectPLACING_FREE_ROAD2) && (!expectWAITING_FOR_DISCOVERY) && (!expectWAITING_FOR_MONOPOLY))
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

                            if ((buildingPlan.empty()) && (ourPlayerData.getResources().getTotal() > 1) && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
                            {
                                planBuilding();

                                /*
                                 * planBuilding takes these actions:
                                 *
                                decisionMaker.planStuff(robotParameters.getStrategyType());

                                if (!buildingPlan.empty())
                                {
                                    lastTarget = (SOCPossiblePiece) buildingPlan.peek();
                                    negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), (SOCPossiblePiece) buildingPlan.peek());
                                }
                                 */

                                if ( ! buildingPlan.empty())
                                {
                                    // Do we have the resources right now?
                                    final SOCPossiblePiece targetPiece = (SOCPossiblePiece) buildingPlan.peek();
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

                        if ((!waitingForOurTurn) && (ourTurn))
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

                                    if (((laPlayer != null) && (laPlayer.getPlayerNumber() != ourPN)) || (laPlayer == null))
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

                                        if (((ourPlayerData.getNumKnights() + ourPlayerData.getDevCards().getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT) + ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT)) >= larmySize) && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) > 0))
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
                                if (!expectPLACING_ROBBER && (buildingPlan.empty()) && (ourPlayerData.getResources().getTotal() > 1) && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
                                {
                                    planBuilding();

                                    /*
                                     * planBuilding takes these actions:
                                     *
                                    decisionMaker.planStuff(robotParameters.getStrategyType());

                                    if (!buildingPlan.empty())
                                    {
                                        lastTarget = (SOCPossiblePiece) buildingPlan.peek();
                                        negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), (SOCPossiblePiece) buildingPlan.peek());
                                    }
                                     */
                                }

                                //D.ebugPrintln("DONE PLANNING");
                                if (!expectPLACING_ROBBER && !buildingPlan.empty())
                                {
                                    // Time to build something.

                                    // Either ask to build a piece, or use trading or development
                                    // cards to get resources to build it.  See javadoc for flags set.
                                    buildOrGetResourceByTradeOrCard();
                                }

                                /**
                                 * see if we're done with our turn
                                 */
                                if (!(expectPLACING_SETTLEMENT || expectPLACING_FREE_ROAD1 || expectPLACING_FREE_ROAD2 || expectPLACING_ROAD || expectPLACING_CITY || expectWAITING_FOR_DISCOVERY || expectWAITING_FOR_MONOPOLY || expectPLACING_ROBBER || waitingForTradeMsg || waitingForTradeResponse || waitingForDevCard))
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
                        handlePUTPIECE_updateTrackers((SOCPutPiece) mes);

                        // For initial placement of our own pieces, also checks
                        // and clears expectPUTPIECE_FROM_START1A,
                        // and sets expectSTART1B, etc.  The final initial putpiece
                        // clears expectPUTPIECE_FROM_START2B and sets expectPLAY.

                        break;

                    case SOCMessage.DICERESULT:
                        if (expectDICERESULT)
                        {
                            expectDICERESULT = false;
    
                            if (((SOCDiceResult) mes).getResult() == 7)
                            {
                                moveRobberOnSeven = true;
    
                                if (ourPlayerData.getResources().getTotal() > 7)
                                    expectDISCARD = true;

                                else if (ourTurn)
                                    expectPLACING_ROBBER = true;
                            }
                            else
                            {
                                expectPLAY1 = true;
                            }
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
                            expectPLACING_ROBBER = true;
                        }
                        else
                        {
                            expectPLAY1 = true;
                        }

                        counter = 0;
                        discard(((SOCDiscardRequest) mes).getNumberOfDiscards());

                        //	}
                        break;

                    case SOCMessage.CHOOSEPLAYERREQUEST:
                        chooseRobberVictim(((SOCChoosePlayerRequest) mes).getChoices());
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
                        // We've been waiting too long, commit suicide.
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
     * <LI> {@link SOCGame#START1A} - {@link SOCGame#START2B}
     * <LI> {@link SOCGame#PLACING_SETTLEMENT}
     * <LI> {@link SOCGame#PLACING_ROAD}
     * <LI> {@link SOCGame#PLACING_CITY}
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
                    client.putPiece(game, whatWeWantToBuild);
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
    
                    SOCPossiblePiece posPiece = (SOCPossiblePiece) buildingPlan.pop();
    
                    if (posPiece.getType() == SOCPossiblePiece.ROAD)
                    {
                        // D.ebugPrintln("posPiece = " + posPiece);
                        whatWeWantToBuild = new SOCRoad(ourPlayerData, posPiece.getCoordinates(), null);
                        // D.ebugPrintln("$ POPPED OFF");
                        // D.ebugPrintln("!!! PUTTING PIECE 2 " + whatWeWantToBuild + " !!!");
                        pause(500);
                        client.putPiece(game, whatWeWantToBuild);
                        pause(1000);
                    }
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
                    planInitialSettlements();
                    placeFirstSettlement();
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
                    placeInitRoad();
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
                    planSecondSettlement();
                    placeSecondSettlement();
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
                    placeInitRoad();
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
                    && ! (ourPlayerData.getNumbers().getNumberResourcePairsForHex(game.getBoard().getRobberHex())).isEmpty())
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
     * Either ask to build a piece, or use trading or development cards to get resources to build it.
     * Examines {@link #buildingPlan} for the next piece wanted.
     *<P>
     * Call when these conditions are all true:
     * <UL>
     *<LI> gameState {@link SOCGame#PLAY1} or {@link SOCGame#SPECIAL_BUILDING}
     *<LI> <tt>waitingFor...</tt> flags all false ({@link #waitingForGameState}, etc) except possibly {@link #waitingForSpecialBuild}
     *<LI> <tt>expect...</tt> flags all false ({@link #expectPLACING_ROAD}, etc)
     *<LI> ! {@link #waitingForOurTurn}
     *<LI> {@link #ourTurn}
     *<LI> ! ({@link #expectPLAY} && (counter < 4000))
     *<LI> ! {@link #buildingPlan}.empty()
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
     */
    private void buildOrGetResourceByTradeOrCard()
    {
        /**
         * If we're in SPECIAL_BUILDING (not PLAY1),
         * can't trade or play development cards.
         */
        final boolean gameStatePLAY1 = (game.getGameState() == SOCGame.PLAY1);

        /**
         * check to see if this is a Road Building plan
         */
        boolean roadBuildingPlan = false;

        if (gameStatePLAY1 && (! ourPlayerData.hasPlayedDevCard()) && (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2) && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0))
        {
            //D.ebugPrintln("** Checking for Road Building Plan **");
            SOCPossiblePiece topPiece = (SOCPossiblePiece) buildingPlan.pop();

            //D.ebugPrintln("$ POPPED "+topPiece);
            if ((topPiece != null) && (topPiece.getType() == SOCPossiblePiece.ROAD) && (!buildingPlan.empty()))
            {
                SOCPossiblePiece secondPiece = (SOCPossiblePiece) buildingPlan.peek();

                //D.ebugPrintln("secondPiece="+secondPiece);
                if ((secondPiece != null) && (secondPiece.getType() == SOCPossiblePiece.ROAD))
                {
                    roadBuildingPlan = true;
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
            SOCPossiblePiece targetPiece = (SOCPossiblePiece) buildingPlan.peek();
            SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

            //D.ebugPrintln("^^^ targetPiece = "+targetPiece);
            //D.ebugPrintln("^^^ ourResources = "+ourPlayerData.getResources());

            negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), targetPiece);

            ///
            /// if we have a 2 free resources card and we need
            /// at least 2 resources, play the card
            ///
            if (gameStatePLAY1 && (! ourPlayerData.hasPlayedDevCard()) && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC) > 0))
            {
                SOCResourceSet ourResources = ourPlayerData.getResources();
                int numNeededResources = 0;

                for (int resource = SOCResourceConstants.CLAY;
                        resource <= SOCResourceConstants.WOOD;
                        resource++)
                {
                    int diff = targetResources.getAmount(resource) - ourResources.getAmount(resource);

                    if (diff > 0)
                    {
                        numNeededResources += diff;
                    }
                }

                if (numNeededResources == 2)
                {
                    chooseFreeResources(targetResources);

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
                if (gameStatePLAY1 && (! ourPlayerData.hasPlayedDevCard()) && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO) > 0) && chooseMonopoly())
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
                        // Calls buildingPlan.pop().
                        // Checks against whatWeFailedToBuild to see if server has rejected this already.
                        // Calls client.buyDevCard or client.buildRequest.
                        // Sets waitingForDevCard, or waitingForGameState and expectPLACING_SETTLEMENT (etc).

                        buildRequestPlannedPiece(targetPiece);
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
        final SOCPlayer pl = game.getPlayer(mes.getPlayerNumber());
        final int coord = mes.getCoordinates();

        switch (mes.getPieceType())
        {
        case SOCPlayingPiece.ROAD:

            if ((game.getGameState() == SOCGame.START1B) || (game.getGameState() == SOCGame.START2B))
            {
                //
                // Before processing this road, track the settlement that goes with it.
                // This was deferred until road placement, in case a human player decides
                // to cancel their settlement and place it elsewhere.
                //
                SOCPlayerTracker tr = (SOCPlayerTracker) playerTrackers.get
                    (new Integer(mes.getPlayerNumber()));
                SOCSettlement se = tr.getPendingInitSettlement();
                if (se != null)
                    trackNewSettlement(se, false);
            }
            SOCRoad rd = new SOCRoad(pl, coord, null);
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
            if (ourTurn)
            {
                cancelWrongPiecePlacement(mes);
            }
            break;

        case SOCGame.START1B:
        case SOCGame.START2B:
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
                SOCPlayerTracker tr = (SOCPlayerTracker) playerTrackers.get
                    (new Integer(pnum));
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

        if ((offer.getFrom() == ourPlayerData.getPlayerNumber()))
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
            negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), null);

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
     * @since 1.1.08
     */
    private void handlePUTPIECE_updateTrackers(SOCPutPiece mes)
    {
        final int pn = mes.getPlayerNumber();
        final int coord = mes.getCoordinates();
        final int pieceType = mes.getPieceType();

        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:

            SOCRoad newRoad = new SOCRoad(game.getPlayer(pn), coord, null);
            trackNewRoad(newRoad, false);

            break;

        case SOCPlayingPiece.SETTLEMENT:

            SOCPlayer newSettlementPl = game.getPlayer(pn);
            SOCSettlement newSettlement = new SOCSettlement(newSettlementPl, coord, null);
            if ((game.getGameState() == SOCGame.START1B) || (game.getGameState() == SOCGame.START2B))
            {
                // Track it after the road is placed
                // (in handlePUTPIECE_updateGameData)
                SOCPlayerTracker tr = (SOCPlayerTracker) playerTrackers.get
                    (new Integer(newSettlementPl.getPlayerNumber()));
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
        }

        if (D.ebugOn)
        {
            SOCPlayerTracker.playerTrackersDebug(playerTrackers);
        }

        if (pn != ourPlayerData.getPlayerNumber())
        {
            return;  // <---- Not our piece ----
        }

        /**
         * Update expect-vars during initial placement of our pieces.
         */

        if (expectPUTPIECE_FROM_START1A && (pieceType == SOCPlayingPiece.SETTLEMENT) && (mes.getCoordinates() == ourPlayerData.getLastSettlementCoord()))
        {
            expectPUTPIECE_FROM_START1A = false;
            expectSTART1B = true;
        }

        if (expectPUTPIECE_FROM_START1B && (pieceType == SOCPlayingPiece.ROAD) && (mes.getCoordinates() == ourPlayerData.getLastRoadCoord()))
        {
            expectPUTPIECE_FROM_START1B = false;
            expectSTART2A = true;
        }

        if (expectPUTPIECE_FROM_START2A && (pieceType == SOCPlayingPiece.SETTLEMENT) && (mes.getCoordinates() == ourPlayerData.getLastSettlementCoord()))
        {
            expectPUTPIECE_FROM_START2A = false;
            expectSTART2B = true;
        }

        if (expectPUTPIECE_FROM_START2B && (pieceType == SOCPlayingPiece.ROAD) && (mes.getCoordinates() == ourPlayerData.getLastRoadCoord()))
        {
            expectPUTPIECE_FROM_START2B = false;
            expectPLAY = true;
        }

    }

    /**
     * Have the client ask to build this piece, unless we've already
     * been told by the server to not build it.
     * Calls {@link #buildingPlan}.pop().
     * Checks against {@link #whatWeFailedToBuild} to see if server has rejected this already.
     * Calls <tt>client.buyDevCard()</tt> or <tt>client.buildRequest()</tt>.
     * Sets {@link #waitingForDevCard}, or {@link #waitingForGameState} and
     * {@link #expectPLACING_SETTLEMENT} (etc).
     *
     * @param targetPiece  This should be the top piece of {@link #buildingPlan}.
     * @since 1.1.08
     */
    private void buildRequestPlannedPiece(SOCPossiblePiece targetPiece)
    {
        buildingPlan.pop();
        D.ebugPrintln("$ POPPED " + targetPiece);
        lastMove = targetPiece;
        currentDRecorder = (currentDRecorder + 1) % 2;
        negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), targetPiece);

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
            lastTarget = (SOCPossiblePiece) buildingPlan.peek();
            negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), (SOCPossiblePiece) buildingPlan.peek());
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
        SOCPlayer pl = game.getPlayer(mes.getPlayerNumber());

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
                    game.askSpecialBuild(pl.getPlayerNumber(), false);  // set per-player, per-game flags
                }
                catch (RuntimeException e) {}
            } else {
                pl.setAskedSpecialBuild(false);
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
            final SOCPossiblePiece targetPiece = (SOCPossiblePiece) buildingPlan.peek();
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
        Iterator trackersIter;
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            if (! isCancel)
                tracker.addNewSettlement(newSettlement, playerTrackers);
            else
                tracker.cancelWrongSettlement(newSettlement);
        }

        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            Iterator posRoadsIter = tracker.getPossibleRoads().values().iterator();

            while (posRoadsIter.hasNext())
            {
                ((SOCPossibleRoad) posRoadsIter.next()).clearThreats();
            }

            Iterator posSetsIter = tracker.getPossibleSettlements().values().iterator();

            while (posSetsIter.hasNext())
            {
                ((SOCPossibleSettlement) posSetsIter.next()).clearThreats();
            }
        }

        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
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
        Enumeration adjEdgeEnum = board.getAdjacentEdgesToNode(newSettlement.getCoordinates()).elements();

        while (adjEdgeEnum.hasMoreElements())
        {
            Integer adjEdge = (Integer) adjEdgeEnum.nextElement();
            Enumeration roadEnum = board.getRoads().elements();

            while (roadEnum.hasMoreElements())
            {
                SOCRoad road = (SOCRoad) roadEnum.nextElement();

                if (road.getCoordinates() == adjEdge.intValue())
                {
                    roadCount[road.getPlayer().getPlayerNumber()]++;

                    if (roadCount[road.getPlayer().getPlayerNumber()] == 2)
                    {
                        if (road.getPlayer().getPlayerNumber() != ourPlayerData.getPlayerNumber())
                        {
                            ///
                            /// this settlement bisects another players road
                            ///
                            trackersIter = playerTrackers.values().iterator();

                            while (trackersIter.hasNext())
                            {
                                SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                                if (tracker.getPlayer().getPlayerNumber() == road.getPlayer().getPlayerNumber())
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
        
        int pNum = newSettlement.getPlayer().getPlayerNumber();

        ///
        /// update the speedups from possible settlements
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == pNum)
            {
                Iterator posSetsIter = tracker.getPossibleSettlements().values().iterator();

                while (posSetsIter.hasNext())
                {
                    ((SOCPossibleSettlement) posSetsIter.next()).updateSpeedup();
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
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == pNum)
            {
                Iterator posCitiesIter = tracker.getPossibleCities().values().iterator();

                while (posCitiesIter.hasNext())
                {
                    ((SOCPossibleCity) posCitiesIter.next()).updateSpeedup();
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
    private void trackNewCity(SOCCity newCity, final boolean isCancel)
    {
        Iterator trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == newCity.getPlayer().getPlayerNumber())
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
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == newCity.getPlayer().getPlayerNumber())
            {
                Iterator posSetsIter = tracker.getPossibleSettlements().values().iterator();

                while (posSetsIter.hasNext())
                {
                    ((SOCPossibleSettlement) posSetsIter.next()).updateSpeedup();
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
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == newCity.getPlayer().getPlayerNumber())
            {
                Iterator posCitiesIter = tracker.getPossibleCities().values().iterator();

                while (posCitiesIter.hasNext())
                {
                    ((SOCPossibleCity) posCitiesIter.next()).updateSpeedup();
                }

                break;
            }
        }
    }

    /**
     * Run a newly placed road through the playerTrackers.
     * 
     * @param newRoad  The newly placed road
     * @param isCancel Is this our own robot's road placement, rejected by the server?
     *     If so, this method call will cancel its placement within the game data / robot data. 
     */
    protected void trackNewRoad(SOCRoad newRoad, final boolean isCancel)
    {
        Iterator trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            tracker.takeMonitor();

            try
            {
                if (! isCancel)
                    tracker.addNewRoad(newRoad, playerTrackers);
                else
                    tracker.cancelWrongRoad(newRoad);
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
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            tracker.takeMonitor();

            try
            {
                Iterator posRoadsIter = tracker.getPossibleRoads().values().iterator();

                while (posRoadsIter.hasNext())
                {
                    ((SOCPossibleRoad) posRoadsIter.next()).clearThreats();
                }

                Iterator posSetsIter = tracker.getPossibleSettlements().values().iterator();

                while (posSetsIter.hasNext())
                {
                    ((SOCPossibleSettlement) posSetsIter.next()).clearThreats();
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
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            tracker.updateThreats(playerTrackers);
            tracker.takeMonitor();

            try
            {
                if (tracker.getPlayer().getPlayerNumber() == newRoad.getPlayer().getPlayerNumber())
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
        final boolean cancelBuyDevCard = (mes.getPieceType() == -2);
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
        else if (gameState <= SOCGame.START2B)
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
            case SOCPlayingPiece.ROAD:
                trackNewRoad((SOCRoad) cancelPiece, true);
                ourPlayerData.clearPotentialRoad(coord);
                if (game.getGameState() <= SOCGame.START2B)
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
     * figure out where to place the two settlements
     */
    protected void planInitialSettlements()
    {
        D.ebugPrintln("--- planInitialSettlements");

        int[] rolls;
        Enumeration hexes;
        int speed;
        boolean allTheWay;
        firstSettlement = 0;
        secondSettlement = 0;

        int bestSpeed = 4 * SOCBuildingSpeedEstimate.DEFAULT_ROLL_LIMIT;
        SOCBoard board = game.getBoard();
        SOCResourceSet emptySet = new SOCResourceSet();
        SOCPlayerNumbers playerNumbers = new SOCPlayerNumbers(board.getBoardEncodingFormat());
        int probTotal;
        int bestProbTotal;
        boolean[] ports = new boolean[SOCBoard.WOOD_PORT + 1];
        SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate();
        int[] prob = SOCNumberProbabilities.INT_VALUES;

        bestProbTotal = 0;

        for (int firstNode = board.getMinNode(); firstNode <= SOCBoard.MAXNODE; firstNode++)
        {
            if (ourPlayerData.isPotentialSettlement(firstNode))
            {
                Integer firstNodeInt = new Integer(firstNode);

                //
                // this is just for testing purposes
                //
                D.ebugPrintln("FIRST NODE -----------");
                D.ebugPrintln("firstNode = " + board.nodeCoordToString(firstNode));
                D.ebugPrint("numbers:[");
                playerNumbers.clear();
                probTotal = 0;
                hexes = SOCBoard.getAdjacentHexesToNode(firstNode).elements();

                while (hexes.hasMoreElements())
                {
                    Integer hex = (Integer) hexes.nextElement();
                    int number = board.getNumberOnHexFromCoord(hex.intValue());
                    int resource = board.getHexTypeFromCoord(hex.intValue());
                    playerNumbers.addNumberForResource(number, resource, hex.intValue());
                    probTotal += prob[number];
                    D.ebugPrint(number + " ");
                }

                D.ebugPrintln("]");
                D.ebugPrint("ports: ");

                for (int portType = SOCBoard.MISC_PORT;
                        portType <= SOCBoard.WOOD_PORT; portType++)
                {
                    if (board.getPortCoordinates(portType).contains(firstNodeInt))
                    {
                        ports[portType] = true;
                    }
                    else
                    {
                        ports[portType] = false;
                    }

                    D.ebugPrint(ports[portType] + "  ");
                }

                D.ebugPrintln();
                D.ebugPrintln("probTotal = " + probTotal);
                estimate.recalculateEstimates(playerNumbers);
                speed = 0;
                allTheWay = false;

                try
                {
                    speed += estimate.calculateRollsFast(emptySet, SOCGame.SETTLEMENT_SET, 300, ports).getRolls();
                    speed += estimate.calculateRollsFast(emptySet, SOCGame.CITY_SET, 300, ports).getRolls();
                    speed += estimate.calculateRollsFast(emptySet, SOCGame.CARD_SET, 300, ports).getRolls();
                    speed += estimate.calculateRollsFast(emptySet, SOCGame.ROAD_SET, 300, ports).getRolls();
                }
                catch (CutoffExceededException e) {}

                rolls = estimate.getEstimatesFromNothingFast(ports, 300);
                D.ebugPrint(" road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                D.ebugPrint(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                D.ebugPrint(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                D.ebugPrintln(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                D.ebugPrintln("speed = " + speed);

                //
                // end test
                //
                for (int secondNode = firstNode + 1; secondNode <= SOCBoard.MAXNODE;
                        secondNode++)
                {
                    if ((ourPlayerData.isPotentialSettlement(secondNode)) && (! board.getAdjacentNodesToNode(secondNode).contains(firstNodeInt)))
                    {
                        D.ebugPrintln("firstNode = " + board.nodeCoordToString(firstNode));
                        D.ebugPrintln("secondNode = " + board.nodeCoordToString(secondNode));

                        Integer secondNodeInt = new Integer(secondNode);

                        /**
                         * get the numbers for these settlements
                         */
                        D.ebugPrint("numbers:[");
                        playerNumbers.clear();
                        probTotal = 0;
                        hexes = SOCBoard.getAdjacentHexesToNode(firstNode).elements();

                        while (hexes.hasMoreElements())
                        {
                            Integer hex = (Integer) hexes.nextElement();
                            int number = board.getNumberOnHexFromCoord(hex.intValue());
                            int resource = board.getHexTypeFromCoord(hex.intValue());
                            playerNumbers.addNumberForResource(number, resource, hex.intValue());
                            probTotal += prob[number];
                            D.ebugPrint(number + " ");
                        }

                        D.ebugPrint("] [");
                        hexes = SOCBoard.getAdjacentHexesToNode(secondNode).elements();

                        while (hexes.hasMoreElements())
                        {
                            Integer hex = (Integer) hexes.nextElement();
                            int number = board.getNumberOnHexFromCoord(hex.intValue());
                            int resource = board.getHexTypeFromCoord(hex.intValue());
                            playerNumbers.addNumberForResource(number, resource, hex.intValue());
                            probTotal += prob[number];
                            D.ebugPrint(number + " ");
                        }

                        D.ebugPrintln("]");

                        /**
                         * see if the settlements are on any ports
                         */
                        D.ebugPrint("ports: ");

                        for (int portType = SOCBoard.MISC_PORT;
                                portType <= SOCBoard.WOOD_PORT; portType++)
                        {
                            if ((board.getPortCoordinates(portType).contains(firstNodeInt)) || (board.getPortCoordinates(portType).contains(secondNodeInt)))
                            {
                                ports[portType] = true;
                            }
                            else
                            {
                                ports[portType] = false;
                            }

                            D.ebugPrint(ports[portType] + "  ");
                        }

                        D.ebugPrintln();
                        D.ebugPrintln("probTotal = " + probTotal);

                        /**
                         * estimate the building speed for this pair
                         */
                        estimate.recalculateEstimates(playerNumbers);
                        speed = 0;
                        allTheWay = false;

                        try
                        {
                            speed += estimate.calculateRollsFast(emptySet, SOCGame.SETTLEMENT_SET, bestSpeed, ports).getRolls();

                            if (speed < bestSpeed)
                            {
                                speed += estimate.calculateRollsFast(emptySet, SOCGame.CITY_SET, bestSpeed, ports).getRolls();

                                if (speed < bestSpeed)
                                {
                                    speed += estimate.calculateRollsFast(emptySet, SOCGame.CARD_SET, bestSpeed, ports).getRolls();

                                    if (speed < bestSpeed)
                                    {
                                        speed += estimate.calculateRollsFast(emptySet, SOCGame.ROAD_SET, bestSpeed, ports).getRolls();
                                        allTheWay = true;
                                    }
                                }
                            }
                        }
                        catch (CutoffExceededException e)
                        {
                            speed = bestSpeed;
                        }

                        rolls = estimate.getEstimatesFromNothingFast(ports, bestSpeed);
                        D.ebugPrint(" road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                        D.ebugPrint(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                        D.ebugPrint(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                        D.ebugPrintln(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                        D.ebugPrintln("allTheWay = " + allTheWay);
                        D.ebugPrintln("speed = " + speed);

                        /**
                         * keep the settlements with the best speed
                         */
                        if (speed < bestSpeed)
                        {
                            firstSettlement = firstNode;
                            secondSettlement = secondNode;
                            bestSpeed = speed;
                            bestProbTotal = probTotal;
                            D.ebugPrintln("bestSpeed = " + bestSpeed);
                            D.ebugPrintln("bestProbTotal = " + bestProbTotal);
                        }
                        else if ((speed == bestSpeed) && allTheWay)
                        {
                            if (probTotal > bestProbTotal)
                            {
                                D.ebugPrintln("Equal speed, better prob");
                                firstSettlement = firstNode;
                                secondSettlement = secondNode;
                                bestSpeed = speed;
                                bestProbTotal = probTotal;
                                D.ebugPrintln("firstSettlement = " + Integer.toHexString(firstSettlement));
                                D.ebugPrintln("secondSettlement = " + Integer.toHexString(secondSettlement));
                                D.ebugPrintln("bestSpeed = " + bestSpeed);
                                D.ebugPrintln("bestProbTotal = " + bestProbTotal);
                            }
                        }
                    }
                }
            }
        }

        /**
         * choose which settlement to place first
         */
        playerNumbers.clear();
        hexes = SOCBoard.getAdjacentHexesToNode(firstSettlement).elements();

        while (hexes.hasMoreElements())
        {
            int hex = ((Integer) hexes.nextElement()).intValue();
            int number = board.getNumberOnHexFromCoord(hex);
            int resource = board.getHexTypeFromCoord(hex);
            playerNumbers.addNumberForResource(number, resource, hex);
        }

        Integer firstSettlementInt = new Integer(firstSettlement);

        for (int portType = SOCBoard.MISC_PORT; portType <= SOCBoard.WOOD_PORT;
                portType++)
        {
            if (board.getPortCoordinates(portType).contains(firstSettlementInt))
            {
                ports[portType] = true;
            }
            else
            {
                ports[portType] = false;
            }
        }

        estimate.recalculateEstimates(playerNumbers);

        int firstSpeed = 0;
        int cutoff = 100;

        try
        {
            firstSpeed += estimate.calculateRollsFast(emptySet, SOCGame.SETTLEMENT_SET, cutoff, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            firstSpeed += cutoff;
        }

        try
        {
            firstSpeed += estimate.calculateRollsFast(emptySet, SOCGame.CITY_SET, cutoff, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            firstSpeed += cutoff;
        }

        try
        {
            firstSpeed += estimate.calculateRollsFast(emptySet, SOCGame.CARD_SET, cutoff, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            firstSpeed += cutoff;
        }

        try
        {
            firstSpeed += estimate.calculateRollsFast(emptySet, SOCGame.ROAD_SET, cutoff, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            firstSpeed += cutoff;
        }

        playerNumbers.clear();
        hexes = SOCBoard.getAdjacentHexesToNode(secondSettlement).elements();

        while (hexes.hasMoreElements())
        {
            int hex = ((Integer) hexes.nextElement()).intValue();
            int number = board.getNumberOnHexFromCoord(hex);
            int resource = board.getHexTypeFromCoord(hex);
            playerNumbers.addNumberForResource(number, resource, hex);
        }

        Integer secondSettlementInt = new Integer(secondSettlement);

        for (int portType = SOCBoard.MISC_PORT; portType <= SOCBoard.WOOD_PORT;
                portType++)
        {
            if (board.getPortCoordinates(portType).contains(secondSettlementInt))
            {
                ports[portType] = true;
            }
            else
            {
                ports[portType] = false;
            }
        }

        estimate.recalculateEstimates(playerNumbers);

        int secondSpeed = 0;

        try
        {
            secondSpeed += estimate.calculateRollsFast(emptySet, SOCGame.SETTLEMENT_SET, bestSpeed, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            secondSpeed += cutoff;
        }

        try
        {
            secondSpeed += estimate.calculateRollsFast(emptySet, SOCGame.CITY_SET, bestSpeed, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            secondSpeed += cutoff;
        }

        try
        {
            secondSpeed += estimate.calculateRollsFast(emptySet, SOCGame.CARD_SET, bestSpeed, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            secondSpeed += cutoff;
        }

        try
        {
            secondSpeed += estimate.calculateRollsFast(emptySet, SOCGame.ROAD_SET, bestSpeed, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            secondSpeed += cutoff;
        }

        if (firstSpeed > secondSpeed)
        {
            int tmp = firstSettlement;
            firstSettlement = secondSettlement;
            secondSettlement = tmp;
        }

        D.ebugPrintln(board.nodeCoordToString(firstSettlement) + ":" + firstSpeed + ", " + board.nodeCoordToString(secondSettlement) + ":" + secondSpeed);
    }

    /**
     * figure out where to place the second settlement
     */
    protected void planSecondSettlement()
    {
        D.ebugPrintln("--- planSecondSettlement");

        int bestSpeed = 4 * SOCBuildingSpeedEstimate.DEFAULT_ROLL_LIMIT;
        SOCBoard board = game.getBoard();
        SOCResourceSet emptySet = new SOCResourceSet();
        SOCPlayerNumbers playerNumbers = new SOCPlayerNumbers(board.getBoardEncodingFormat());
        boolean[] ports = new boolean[SOCBoard.WOOD_PORT + 1];
        SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate();
        int probTotal;
        int bestProbTotal;
        final int[] prob = SOCNumberProbabilities.INT_VALUES;
        final int firstNode = firstSettlement;
        final Integer firstNodeInt = new Integer(firstNode);

        bestProbTotal = 0;
        secondSettlement = -1;

        for (int secondNode = board.getMinNode(); secondNode <= SOCBoard.MAXNODE; secondNode++)
        {
            if ((ourPlayerData.isPotentialSettlement(secondNode)) && (! board.getAdjacentNodesToNode(secondNode).contains(firstNodeInt)))
            {
                Integer secondNodeInt = new Integer(secondNode);

                /**
                 * get the numbers for these settlements
                 */
                D.ebugPrint("numbers: ");
                playerNumbers.clear();
                probTotal = 0;

                Enumeration hexes = SOCBoard.getAdjacentHexesToNode(firstNode).elements();

                while (hexes.hasMoreElements())
                {
                    final int hex = ((Integer) hexes.nextElement()).intValue();
                    int number = board.getNumberOnHexFromCoord(hex);
                    int resource = board.getHexTypeFromCoord(hex);
                    playerNumbers.addNumberForResource(number, resource, hex);
                    probTotal += prob[number];
                    D.ebugPrint(number + " ");
                }

                hexes = SOCBoard.getAdjacentHexesToNode(secondNode).elements();

                while (hexes.hasMoreElements())
                {
                    final int hex = ((Integer) hexes.nextElement()).intValue();
                    int number = board.getNumberOnHexFromCoord(hex);
                    int resource = board.getHexTypeFromCoord(hex);
                    playerNumbers.addNumberForResource(number, resource, hex);
                    probTotal += prob[number];
                    D.ebugPrint(number + " ");
                }

                /**
                 * see if the settlements are on any ports
                 */
                D.ebugPrint("ports: ");

                for (int portType = SOCBoard.MISC_PORT;
                        portType <= SOCBoard.WOOD_PORT; portType++)
                {
                    if ((board.getPortCoordinates(portType).contains(firstNodeInt)) || (board.getPortCoordinates(portType).contains(secondNodeInt)))
                    {
                        ports[portType] = true;
                    }
                    else
                    {
                        ports[portType] = false;
                    }

                    D.ebugPrint(ports[portType] + "  ");
                }

                D.ebugPrintln();
                D.ebugPrintln("probTotal = " + probTotal);

                /**
                 * estimate the building speed for this pair
                 */
                estimate.recalculateEstimates(playerNumbers);

                int speed = 0;

                try
                {
                    speed += estimate.calculateRollsFast(emptySet, SOCGame.SETTLEMENT_SET, bestSpeed, ports).getRolls();

                    if (speed < bestSpeed)
                    {
                        speed += estimate.calculateRollsFast(emptySet, SOCGame.CITY_SET, bestSpeed, ports).getRolls();

                        if (speed < bestSpeed)
                        {
                            speed += estimate.calculateRollsFast(emptySet, SOCGame.CARD_SET, bestSpeed, ports).getRolls();

                            if (speed < bestSpeed)
                            {
                                speed += estimate.calculateRollsFast(emptySet, SOCGame.ROAD_SET, bestSpeed, ports).getRolls();
                            }
                        }
                    }
                }
                catch (CutoffExceededException e)
                {
                    speed = bestSpeed;
                }

                D.ebugPrintln(Integer.toHexString(firstNode) + ", " + Integer.toHexString(secondNode) + ":" + speed);

                /**
                 * keep the settlements with the best speed
                 */
                if ((speed < bestSpeed) || (secondSettlement < 0))
                {
                    firstSettlement = firstNode;
                    secondSettlement = secondNode;
                    bestSpeed = speed;
                    bestProbTotal = probTotal;
                    D.ebugPrintln("firstSettlement = " + Integer.toHexString(firstSettlement));
                    D.ebugPrintln("secondSettlement = " + Integer.toHexString(secondSettlement));

                    int[] rolls = estimate.getEstimatesFromNothingFast(ports);
                    D.ebugPrint("road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                    D.ebugPrint(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                    D.ebugPrint(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                    D.ebugPrintln(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                    D.ebugPrintln("bestSpeed = " + bestSpeed);
                }
                else if (speed == bestSpeed)
                {
                    if (probTotal > bestProbTotal)
                    {
                        firstSettlement = firstNode;
                        secondSettlement = secondNode;
                        bestSpeed = speed;
                        bestProbTotal = probTotal;
                        D.ebugPrintln("firstSettlement = " + Integer.toHexString(firstSettlement));
                        D.ebugPrintln("secondSettlement = " + Integer.toHexString(secondSettlement));

                        int[] rolls = estimate.getEstimatesFromNothingFast(ports);
                        D.ebugPrint("road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                        D.ebugPrint(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                        D.ebugPrint(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                        D.ebugPrintln(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                        D.ebugPrintln("bestSpeed = " + bestSpeed);
                    }
                }
            }
        }
    }

    /**
     * place planned first settlement
     */
    protected void placeFirstSettlement()
    {
        //D.ebugPrintln("BUILD REQUEST FOR SETTLEMENT AT "+Integer.toHexString(firstSettlement));
        pause(500);
        lastStartingPieceCoord = firstSettlement;
        client.putPiece(game, new SOCSettlement(ourPlayerData, firstSettlement, null));
        pause(1000);
    }

    /**
     * place planned second settlement
     */
    protected void placeSecondSettlement()
    {
        if (secondSettlement == -1)
        {
            // This could mean that the server (incorrectly) asked us to
            // place another second settlement, after we've cleared the
            // potentialSettlements contents.
            System.err.println("robot assert failed: secondSettlement -1, " + ourPlayerData.getName() + " leaving game " + game.getName());
            failedBuildingAttempts = 2 + (2 * MAX_DENIED_BUILDING_PER_TURN);
            waitingForGameState = false;
            return;
        }

        //D.ebugPrintln("BUILD REQUEST FOR SETTLEMENT AT "+Integer.toHexString(secondSettlement));
        pause(500);
        lastStartingPieceCoord = secondSettlement;
        client.putPiece(game, new SOCSettlement(ourPlayerData, secondSettlement, null));
        pause(1000);
    }

    /**
     * Plan and place a road attached to our most recently placed initial settlement,
     * in game states {@link SOCGame#START1B START1B}, {@link SOCGame#START2B START2B}.
     *<P>
     * Road choice is based on the best nearby potential settlements, and doesn't
     * directly check {@link SOCPlayer#isPotentialRoad(int) ourPlayerData.isPotentialRoad(edgeCoord)}.
     * If the server rejects our road choice, then {@link #cancelWrongPiecePlacementLocal(SOCPlayingPiece)}
     * will need to know which settlement node we were aiming for,
     * and call {@link SOCPlayer#clearPotentialSettlement(int) ourPlayerData.clearPotentialSettlement(nodeCoord)}.
     * The {@link #lastStartingRoadTowardsNode} field holds this coordinate.
     */
    public void placeInitRoad()
    {
        final int settlementNode = ourPlayerData.getLastSettlementCoord();

        /**
         * Score the nearby nodes to build road towards: Key = coord Integer; value = Integer score towards "best" node.
         */
        Hashtable twoAway = new Hashtable();

        D.ebugPrintln("--- placeInitRoad");

        /**
         * look at all of the nodes that are 2 away from the
         * last settlement, and pick the best one
         */
        SOCBoard board = game.getBoard();

        for (int facing = 1; facing <= 6; ++facing)
        {
            // each of 6 directions: NE, E, SE, SW, W, NW
            int tmp = board.getAdjacentNodeToNode2Away(settlementNode, facing);
            if ((tmp != -9) && ourPlayerData.isPotentialSettlement(tmp))
                twoAway.put(new Integer(tmp), new Integer(0));
        }

        scoreNodesForSettlements(twoAway, 3, 5, 10);

        D.ebugPrintln("Init Road for " + client.getNickname());

        /**
         * create a dummy player to calculate possible places to build
         * taking into account where other players will build before
         * we can.
         */
        SOCPlayer dummy = new SOCPlayer(ourPlayerData.getPlayerNumber(), game);

        if (game.getGameState() == SOCGame.START1B)
        {
            /**
             * do a look ahead so we don't build toward a place
             * where someone else will build first.
             */
            int numberOfBuilds = numberOfEnemyBuilds();
            D.ebugPrintln("Other players will build " + numberOfBuilds + " settlements before I get to build again.");

            if (numberOfBuilds > 0)
            {
                /**
                 * rule out where other players are going to build
                 */
                Hashtable allNodes = new Hashtable();
                final int minNode = board.getMinNode();

                for (int i = minNode; i <= SOCBoard.MAXNODE; i++)
                {
                    if (ourPlayerData.isPotentialSettlement(i))
                    {
                        D.ebugPrintln("-- potential settlement at " + Integer.toHexString(i));
                        allNodes.put(new Integer(i), new Integer(0));
                    }
                }

                /**
                 * favor spots with the most high numbers
                 */
                bestSpotForNumbers(allNodes, 100);

                /**
                 * favor spots near good ports
                 */
                /**
                 * check 3:1 ports
                 */
                Vector miscPortNodes = game.getBoard().getPortCoordinates(SOCBoard.MISC_PORT);
                bestSpot2AwayFromANodeSet(allNodes, miscPortNodes, 5);

                /**
                 * check out good 2:1 ports
                 */
                for (int portType = SOCBoard.CLAY_PORT;
                        portType <= SOCBoard.WOOD_PORT; portType++)
                {
                    /**
                     * if the chances of rolling a number on the resource is better than 1/3,
                     * then it's worth looking at the port
                     */
                    if (resourceEstimates[portType] > 33)
                    {
                        Vector portNodes = game.getBoard().getPortCoordinates(portType);
                        int portWeight = (resourceEstimates[portType] * 10) / 56;
                        bestSpot2AwayFromANodeSet(allNodes, portNodes, portWeight);
                    }
                }

                /*
                 * create a list of potential settlements that takes into account
                 * where other players will build
                 */
                Vector psList = new Vector();

                for (int j = minNode; j <= SOCBoard.MAXNODE; j++)
                {
                    if (ourPlayerData.isPotentialSettlement(j))
                    {
                        D.ebugPrintln("- potential settlement at " + Integer.toHexString(j));
                        psList.addElement(new Integer(j));
                    }
                }

                dummy.setPotentialSettlements(psList);

                for (int builds = 0; builds < numberOfBuilds; builds++)
                {
                    BoardNodeScorePair bestNodePair = new BoardNodeScorePair(0, 0);
                    Enumeration nodesEnum = allNodes.keys();

                    while (nodesEnum.hasMoreElements())
                    {
                        Integer nodeCoord = (Integer) nodesEnum.nextElement();
                        final int score = ((Integer) allNodes.get(nodeCoord)).intValue();
                        D.ebugPrintln("NODE = " + Integer.toHexString(nodeCoord.intValue()) + " SCORE = " + score);

                        if (bestNodePair.getScore() < score)
                        {
                            bestNodePair.setScore(score);
                            bestNodePair.setNode(nodeCoord.intValue());
                        }
                    }

                    /**
                     * pretend that someone has built a settlement on the best spot
                     */
                    dummy.updatePotentials(new SOCSettlement(ourPlayerData, bestNodePair.getNode(), null));

                    /**
                     * remove this spot from the list of best spots
                     */
                    allNodes.remove(new Integer(bestNodePair.getNode()));
                }
            }
        }

        /**
         * Find the best scoring node
         */
        BoardNodeScorePair bestNodePair = new BoardNodeScorePair(0, 0);
        Enumeration cenum = twoAway.keys();

        while (cenum.hasMoreElements())
        {
            Integer coord = (Integer) cenum.nextElement();
            final int score = ((Integer) twoAway.get(coord)).intValue();

            D.ebugPrintln("Considering " + Integer.toHexString(coord.intValue()) + " with a score of " + score);

            if (dummy.isPotentialSettlement(coord.intValue()))
            {
                if (bestNodePair.getScore() < score)
                {
                    bestNodePair.setScore(score);
                    bestNodePair.setNode(coord.intValue());
                }
            }
            else
            {
                D.ebugPrintln("Someone is bound to ruin that spot.");
            }
        }

        // Reminder: settlementNode == ourPlayerData.getLastSettlementCoord()
        final int destination = bestNodePair.getNode();  // coordinate of future settlement
                                                         // 2 nodes away from settlementNode
        final int roadEdge   // will be adjacent to settlementNode
            = board.getAdjacentEdgeToNode2Away(settlementNode, destination);

        //D.ebugPrintln("!!! PUTTING INIT ROAD !!!");
        pause(500);

        //D.ebugPrintln("Trying to build a road at "+Integer.toHexString(roadEdge));
        lastStartingPieceCoord = roadEdge;
        lastStartingRoadTowardsNode = destination;
        client.putPiece(game, new SOCRoad(ourPlayerData, roadEdge, null));
        pause(1000);

        dummy.destroyPlayer();
    }

    /**
     * Estimate the rarity of each resource, given this board's resource locations vs dice numbers.
     * Cached after the first call.
     *
     * @return an array of rarity numbers where
     *         estimates[SOCBoard.CLAY_HEX] == the clay rarity,
     *         as an integer percentage 0-100 of dice rolls.
     */
    protected int[] estimateResourceRarity()
    {
        if (resourceEstimates == null)
        {
            SOCBoard board = game.getBoard();
            final int[] numberWeights = SOCNumberProbabilities.INT_VALUES;

            resourceEstimates = new int[SOCResourceConstants.UNKNOWN];  // uses 1 to 5 (CLAY to WOOD)
            resourceEstimates[0] = 0;

            // look at each hex
            final int L = board.getNumberLayout().length;
            for (int i = 0; i < L; i++)
            {
                final int hexNumber = board.getNumberOnHexFromNumber(i);
                if (hexNumber > 0)
                    resourceEstimates[board.getHexTypeFromNumber(i)] += numberWeights[hexNumber];
            }
        }

        //D.ebugPrint("Resource Estimates = ");
        //for (int i = 1; i < 6; i++)
        //{
            //D.ebugPrint(i+":"+resourceEstimates[i]+" ");
        //}

        //D.ebugPrintln();
        return resourceEstimates;
    }

    /**
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  Nodes touching hexes
     * with better numbers get better scores.
     *
     * @param nodes    the table of nodes with scores
     * @param weight   a number that is multiplied by the score
     */
    protected void bestSpotForNumbers(Hashtable nodes, int weight)
    {
        int[] numRating = SOCNumberProbabilities.INT_VALUES;
        SOCBoard board = game.getBoard();
        int oldScore;
        Enumeration nodesEnum = nodes.keys();

        while (nodesEnum.hasMoreElements())
        {
            Integer node = (Integer) nodesEnum.nextElement();

            //D.ebugPrintln("BSN - looking at node "+Integer.toHexString(node.intValue()));
            oldScore = ((Integer) nodes.get(node)).intValue();

            int score = 0;
            Enumeration hexesEnum = SOCBoard.getAdjacentHexesToNode(node.intValue()).elements();

            while (hexesEnum.hasMoreElements())
            {
                int hex = ((Integer) hexesEnum.nextElement()).intValue();
                score += numRating[board.getNumberOnHexFromCoord(hex)];

                //D.ebugPrintln(" -- -- Adding "+numRating[board.getNumberOnHexFromCoord(hex)]);
            }

            /*
             * normalize score and multiply by weight
             * 40 is highest practical score
             * lowest score is 0
             */
            int nScore = ((score * 100) / 40) * weight;
            Integer finalScore = new Integer(nScore + oldScore);
            nodes.put(node, finalScore);

            //D.ebugPrintln("BSN -- put node "+Integer.toHexString(node.intValue())+" with old score "+oldScore+" + new score "+nScore);
        }
    }

    /**
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  Nodes touching hexes
     * with better numbers get better scores.  Also numbers
     * that the player isn't touching yet are better than ones
     * that the player is already touching.
     *
     * @param nodes    the table of nodes with scores. key = Int node, value = Int score, to be modified in this method
     * @param player   the player that we are doing the rating for
     * @param weight   a number that is multiplied by the score
     */
    protected void bestSpotForNumbers(Hashtable nodes, SOCPlayer player, int weight)
    {
        int[] numRating = SOCNumberProbabilities.INT_VALUES;
        SOCBoard board = game.getBoard();
        int oldScore;
        Enumeration nodesEnum = nodes.keys();

        while (nodesEnum.hasMoreElements())
        {
            Integer node = (Integer) nodesEnum.nextElement();

            //D.ebugPrintln("BSN - looking at node "+Integer.toHexString(node.intValue()));
            oldScore = ((Integer) nodes.get(node)).intValue();

            int score = 0;
            Enumeration hexesEnum = SOCBoard.getAdjacentHexesToNode(node.intValue()).elements();

            while (hexesEnum.hasMoreElements())
            {
                final int hex = ((Integer) hexesEnum.nextElement()).intValue();
                final int number = board.getNumberOnHexFromCoord(hex);
                score += numRating[number];

                if ((number != 0) && (!player.getNumbers().hasNumber(number)))
                {
                    /**
                     * add a bonus for numbers that the player doesn't already have
                     */

                    //D.ebugPrintln("ADDING BONUS FOR NOT HAVING "+number);
                    score += numRating[number];
                }

                //D.ebugPrintln(" -- -- Adding "+numRating[board.getNumberOnHexFromCoord(hex)]);
            }

            /*
             * normalize score and multiply by weight
             * 80 is highest practical score
             * lowest score is 0
             */
            int nScore = ((score * 100) / 80) * weight;
            Integer finalScore = new Integer(nScore + oldScore);
            nodes.put(node, finalScore);

            //D.ebugPrintln("BSN -- put node "+Integer.toHexString(node.intValue())+" with old score "+oldScore+" + new score "+nScore);
        }
    }

    /**
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  A vector of nodes that
     * we want to be near is also taken as an argument.
     * Here are the rules for scoring:
     * If a node is two away from a node in the desired set of nodes it gets 100.
     * Otherwise it gets 0.
     *
     * @param nodesIn   the table of nodes to evaluate
     * @param nodeSet   the set of desired nodes
     * @param weight    the score multiplier
     */
    protected void bestSpot2AwayFromANodeSet(Hashtable nodesIn, Vector nodeSet, int weight)
    {
        final SOCBoard board = game.getBoard();
        Enumeration nodesInEnum = nodesIn.keys();

        while (nodesInEnum.hasMoreElements())
        {
            Integer nodeCoord = (Integer) nodesInEnum.nextElement();
            int node = nodeCoord.intValue();
            int score = 0;
            final int oldScore = ((Integer) nodesIn.get(nodeCoord)).intValue();

            Enumeration nodeSetEnum = nodeSet.elements();

            while (nodeSetEnum.hasMoreElements())
            {
                int target = ((Integer) nodeSetEnum.nextElement()).intValue();

                if (node == target)
                {
                    break;
                }
                else if (board.isNode2AwayFromNode(node, target))
                {
                    score = 100;
                }
            }

            /**
             * multiply by weight
             */
            score *= weight;

            nodesIn.put(nodeCoord, new Integer(oldScore + score));

            //D.ebugPrintln("BS2AFANS -- put node "+Integer.toHexString(node)+" with old score "+oldScore+" + new score "+score);
        }
    }

    /**
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  A vector of nodes that
     * we want to be on is also taken as an argument.
     * Here are the rules for scoring:
     * If a node is in the desired set of nodes it gets 100.
     * Otherwise it gets 0.
     *
     * @param nodesIn   the table of nodes to evaluate
     * @param nodeSet   the set of desired nodes
     * @param weight    the score multiplier
     */
    protected void bestSpotInANodeSet(Hashtable nodesIn, Vector nodeSet, int weight)
    {
        Enumeration nodesInEnum = nodesIn.keys();

        while (nodesInEnum.hasMoreElements())
        {
            Integer nodeCoord = (Integer) nodesInEnum.nextElement();
            int node = nodeCoord.intValue();
            int score = 0;
            final int oldScore = ((Integer) nodesIn.get(nodeCoord)).intValue();

            Enumeration nodeSetEnum = nodeSet.elements();

            while (nodeSetEnum.hasMoreElements())
            {
                int target = ((Integer) nodeSetEnum.nextElement()).intValue();

                if (node == target)
                {
                    score = 100;

                    break;
                }
            }

            /**
             * multiply by weight
             */
            score *= weight;

            nodesIn.put(nodeCoord, new Integer(oldScore + score));

            //D.ebugPrintln("BSIANS -- put node "+Integer.toHexString(node)+" with old score "+oldScore+" + new score "+score);
        }
    }

    /**
     * move the robber
     */
    protected void moveRobber()
    {
        D.ebugPrintln("%%% MOVEROBBER");

        final int[] hexes = game.getBoard().getHexLandCoords();

        int robberHex = game.getBoard().getRobberHex();

        /**
         * decide which player we want to thwart
         */
        int[] winGameETAs = new int[game.maxPlayers];
        for (int i = game.maxPlayers - 1; i >= 0; --i)
            winGameETAs[i] = 100;
        Iterator trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            D.ebugPrintln("%%%%%%%%% TRACKER FOR PLAYER " + tracker.getPlayer().getPlayerNumber());

            try
            {
                tracker.recalcWinGameETA();
                winGameETAs[tracker.getPlayer().getPlayerNumber()] = tracker.getWinGameETA();
                D.ebugPrintln("winGameETA = " + tracker.getWinGameETA());
            }
            catch (NullPointerException e)
            {
                D.ebugPrintln("Null Pointer Exception calculating winGameETA");
                winGameETAs[tracker.getPlayer().getPlayerNumber()] = 500;
            }
        }

        int victimNum = -1;

        for (int pnum = 0; pnum < game.maxPlayers; pnum++)
        {
            if (! game.isSeatVacant(pnum))
            {
                if ((victimNum < 0) && (pnum != ourPlayerData.getPlayerNumber()))
                {
                    // The first pick
                    D.ebugPrintln("Picking a robber victim: pnum=" + pnum);
                    victimNum = pnum;
                }
                else if ((pnum != ourPlayerData.getPlayerNumber()) && (winGameETAs[pnum] < winGameETAs[victimNum]))
                {
                    // A better pick
                    D.ebugPrintln("Picking a better robber victim: pnum=" + pnum);
                    victimNum = pnum;
                }
            }
        }
        // Postcondition: victimNum != -1 due to "First pick" in loop.

        /**
         * figure out the best way to thwart that player
         */
        SOCPlayer victim = game.getPlayer(victimNum);
        SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate();
        int bestHex = robberHex;
        int worstSpeed = 0;
        final boolean skipDeserts = game.isGameOptionSet("RD");  // can't move robber to desert
        SOCBoard gboard = (skipDeserts ? game.getBoard() : null);

        for (int i = 0; i < hexes.length; i++)
        {
            /**
             * only check hexes that we're not touching,
             * and not the robber hex, and possibly not desert hexes
             */
            if ((hexes[i] != robberHex)
                    && ourPlayerData.getNumbers().getNumberResourcePairsForHex(hexes[i]).isEmpty()
                    && ! (skipDeserts && (gboard.getHexTypeFromCoord(hexes[i]) == SOCBoard.DESERT_HEX )))
            {
                estimate.recalculateEstimates(victim.getNumbers(), hexes[i]);

                int[] speeds = estimate.getEstimatesFromNothingFast(victim.getPortFlags());
                int totalSpeed = 0;

                for (int j = SOCBuildingSpeedEstimate.MIN;
                        j < SOCBuildingSpeedEstimate.MAXPLUSONE; j++)
                {
                    totalSpeed += speeds[j];
                }

                D.ebugPrintln("total Speed = " + totalSpeed);

                if (totalSpeed > worstSpeed)
                {
                    bestHex = hexes[i];
                    worstSpeed = totalSpeed;
                    D.ebugPrintln("bestHex = " + Integer.toHexString(bestHex));
                    D.ebugPrintln("worstSpeed = " + worstSpeed);
                }
            }
        }

        D.ebugPrintln("%%% bestHex = " + Integer.toHexString(bestHex));

        /**
         * pick a spot at random if we can't decide.
         * Don't pick deserts if the game option is set.
         * Don't pick one of our hexes if at all possible.
         * It's not likely we'll need to pick one of our hexes
         * (we try 30 times to avoid it), so there isn't code here
         * to pick the 'least bad' one.
         * (TODO) consider that: it would be late in the game.
         *       Use similar algorithm as picking for opponent,
         *       but apply it worst vs best.
         */
        if (bestHex == robberHex)
        {
            int numRand = 0;
            while ((bestHex == robberHex)
                    || (skipDeserts
                            && (gboard.getHexTypeFromCoord(bestHex) == SOCBoard.DESERT_HEX ))
                    || ((numRand < 30)
                            && ourPlayerData.getNumbers().getNumberResourcePairsForHex(bestHex).isEmpty()))
            {
                bestHex = hexes[Math.abs(rand.nextInt()) % hexes.length];
                // D.ebugPrintln("%%% random pick = " + Integer.toHexString(bestHex));
                System.err.println("%%% random pick = " + Integer.toHexString(bestHex));
                ++numRand;
            }
        }

        D.ebugPrintln("!!! MOVING ROBBER !!!");
        client.moveRobber(game, ourPlayerData, bestHex);
        pause(2000);
    }

    /**
     * discard some resources
     *
     * @param numDiscards  the number of resources to discard
     */
    protected void discard(int numDiscards)
    {
        //D.ebugPrintln("DISCARDING...");

        /**
         * if we have a plan, then try to keep the resources
         * needed for that plan, otherwise discard at random
         */
        SOCResourceSet discards = new SOCResourceSet();

        /**
         * make a plan if we don't have one
         */
        if (buildingPlan.empty())
        {
            decisionMaker.planStuff(robotParameters.getStrategyType());
        }

        if (!buildingPlan.empty())
        {
            SOCPossiblePiece targetPiece = (SOCPossiblePiece) buildingPlan.peek();
            negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), targetPiece);

            //D.ebugPrintln("targetPiece="+targetPiece);
            SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

            /**
             * figure out what resources are NOT the ones we need
             */
            SOCResourceSet leftOvers = ourPlayerData.getResources().copy();

            for (int rsrc = SOCResourceConstants.CLAY;
                    rsrc <= SOCResourceConstants.WOOD; rsrc++)
            {
                if (leftOvers.getAmount(rsrc) > targetResources.getAmount(rsrc))
                {
                    leftOvers.subtract(targetResources.getAmount(rsrc), rsrc);
                }
                else
                {
                    leftOvers.setAmount(0, rsrc);
                }
            }

            SOCResourceSet neededRsrcs = ourPlayerData.getResources().copy();
            neededRsrcs.subtract(leftOvers);

            /**
             * figure out the order of resources from
             * easiest to get to hardest
             */

            //D.ebugPrintln("our numbers="+ourPlayerData.getNumbers());
            SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());
            int[] rollsPerResource = estimate.getRollsPerResource();
            int[] resourceOrder = 
            {
                SOCResourceConstants.CLAY, SOCResourceConstants.ORE,
                SOCResourceConstants.SHEEP, SOCResourceConstants.WHEAT,
                SOCResourceConstants.WOOD
            };

            for (int j = 4; j >= 0; j--)
            {
                for (int i = 0; i < j; i++)
                {
                    if (rollsPerResource[resourceOrder[i]] < rollsPerResource[resourceOrder[i + 1]])
                    {
                        int tmp = resourceOrder[i];
                        resourceOrder[i] = resourceOrder[i + 1];
                        resourceOrder[i + 1] = tmp;
                    }
                }
            }

            /**
             * pick the discards
             */
            int curRsrc = 0;

            while (discards.getTotal() < numDiscards)
            {
                /**
                 * choose from the left overs
                 */
                while ((discards.getTotal() < numDiscards) && (curRsrc < 5))
                {
                    //D.ebugPrintln("(1) dis.tot="+discards.getTotal()+" curRsrc="+curRsrc);
                    if (leftOvers.getAmount(resourceOrder[curRsrc]) > 0)
                    {
                        discards.add(1, resourceOrder[curRsrc]);
                        leftOvers.subtract(1, resourceOrder[curRsrc]);
                    }
                    else
                    {
                        curRsrc++;
                    }
                }

                curRsrc = 0;

                /**
                 * choose from what we need
                 */
                while ((discards.getTotal() < numDiscards) && (curRsrc < 5))
                {
                    //D.ebugPrintln("(2) dis.tot="+discards.getTotal()+" curRsrc="+curRsrc);
                    if (neededRsrcs.getAmount(resourceOrder[curRsrc]) > 0)
                    {
                        discards.add(1, resourceOrder[curRsrc]);
                        neededRsrcs.subtract(1, resourceOrder[curRsrc]);
                    }
                    else
                    {
                        curRsrc++;
                    }
                }
            }

            if (curRsrc == 5)
            {
                System.err.println("PROBLEM IN DISCARD - curRsrc == 5");
            }
        }
        else
        {
            /**
             *  choose discards at random
             */
            SOCGame.discardPickRandom(ourPlayerData.getResources(), numDiscards, discards, rand);
        }

        //D.ebugPrintln("!!! DISCARDING !!!");
        //D.ebugPrintln("discards="+discards);
        client.discard(game, discards);
    }

    /**
     * choose a robber victim
     *
     * @param choices a boolean array representing which players are possible victims
     */
    protected void chooseRobberVictim(boolean[] choices)
    {
        int choice = -1;

        /**
         * choose the player with the smallest WGETA
         */
        for (int i = 0; i < game.maxPlayers; i++)
        {
            if (! game.isSeatVacant (i))
            {
                if (choices[i])
                {
                    if (choice == -1)
                    {
                        choice = i;
                    }
                    else
                    {
                        SOCPlayerTracker tracker1 = (SOCPlayerTracker) playerTrackers.get(new Integer(i));
                        SOCPlayerTracker tracker2 = (SOCPlayerTracker) playerTrackers.get(new Integer(choice));
    
                        if ((tracker1 != null) && (tracker2 != null) && (tracker1.getWinGameETA() < tracker2.getWinGameETA()))
                        {
                            //D.ebugPrintln("Picking a robber victim: pnum="+i+" VP="+game.getPlayer(i).getPublicVP());
                            choice = i;
                        }
                    }
                }
            }
        }

        /**
         * choose victim at random
         *
           do {
           choice = Math.abs(rand.nextInt() % SOCGame.MAXPLAYERS);
           } while (!choices[choice]);
         */
        client.choosePlayer(game, choice);
    }

    /**
     * calculate the number of builds before the next turn during init placement
     *
     */
    protected int numberOfEnemyBuilds()
    {
        int numberOfBuilds = 0;
        int pNum = game.getCurrentPlayerNumber();

        /**
         * This is the clockwise direction
         */
        if ((game.getGameState() == SOCGame.START1A) || (game.getGameState() == SOCGame.START1B))
        {
            do
            {
                /**
                 * look at the next player
                 */
                pNum++;

                if (pNum >= game.maxPlayers)
                {
                    pNum = 0;
                }

                if ((pNum != game.getFirstPlayer()) && ! game.isSeatVacant (pNum))
                {
                    numberOfBuilds++;
                }
            }
            while (pNum != game.getFirstPlayer());
        }

        /**
         * This is the counter-clockwise direction
         */
        do
        {
            /**
             * look at the next player
             */
            pNum--;

            if (pNum < 0)
            {
                pNum = game.maxPlayers - 1;
            }

            if ((pNum != game.getCurrentPlayerNumber()) && ! game.isSeatVacant (pNum))
            {
                numberOfBuilds++;
            }
        }
        while (pNum != game.getCurrentPlayerNumber());

        return numberOfBuilds;
    }

    /**
     * given a table of nodes/edges with scores, return the
     * best scoring pair
     *
     * @param nodes  the table of nodes/edges
     * @return the best scoring pair
     */
    protected BoardNodeScorePair findBestScoringNode(Hashtable nodes)
    {
        BoardNodeScorePair bestNodePair = new BoardNodeScorePair(0, -1);
        Enumeration nodesEnum = nodes.keys();

        while (nodesEnum.hasMoreElements())
        {
            Integer nodeCoord = (Integer) nodesEnum.nextElement();
            Integer score = (Integer) nodes.get(nodeCoord);

            //D.ebugPrintln("Checking:"+Integer.toHexString(nodeCoord.intValue())+" score:"+score);
            if (bestNodePair.getScore() < score.intValue())
            {
                bestNodePair.setScore(score.intValue());
                bestNodePair.setNode(nodeCoord.intValue());
            }
        }

        return bestNodePair;
    }

    /**
     * this is a function more for convience
     * given a set of nodes, run a bunch of metrics across them
     * to find which one is best for building a
     * settlement
     *
     * @param nodes          a hashtable of nodes, the scores in the table will be modified.
     *                            Key = coord Integer; value = score Integer.
     * @param numberWeight   the weight given to nodes on good numbers
     * @param miscPortWeight the weight given to nodes on 3:1 ports
     * @param portWeight     the weight given to nodes on good 2:1 ports
     */
    protected void scoreNodesForSettlements(Hashtable nodes, final int numberWeight, final int miscPortWeight, final int portWeight)
    {
        /**
         * favor spots with the most high numbers
         */
        bestSpotForNumbers(nodes, ourPlayerData, numberWeight);

        /**
         * favor spots on good ports:
         */
        /**
         * check if this is on a 3:1 ports, only if we don't have one
         */
        if (!ourPlayerData.getPortFlag(SOCBoard.MISC_PORT))
        {
            Vector miscPortNodes = game.getBoard().getPortCoordinates(SOCBoard.MISC_PORT);
            bestSpotInANodeSet(nodes, miscPortNodes, miscPortWeight);
        }

        /**
         * check out good 2:1 ports that we don't have
         */
        int[] resourceEstimates = estimateResourceRarity();

        for (int portType = SOCBoard.CLAY_PORT; portType <= SOCBoard.WOOD_PORT;
                portType++)
        {
            /**
             * if the chances of rolling a number on the resource is better than 1/3,
             * then it's worth looking at the port
             */
            if ((resourceEstimates[portType] > 33) && (!ourPlayerData.getPortFlag(portType)))
            {
                Vector portNodes = game.getBoard().getPortCoordinates(portType);
                int estimatedPortWeight = (resourceEstimates[portType] * portWeight) / 56;
                bestSpotInANodeSet(nodes, portNodes, estimatedPortWeight);
            }
        }
    }

    /**
     * do some trading
     */
    protected void tradeStuff()
    {
        /**
         * make a tree of all the possible trades that we can
         * make with the bank or ports
         */
        SOCTradeTree treeRoot = new SOCTradeTree(ourPlayerData.getResources(), (SOCTradeTree) null);
        Hashtable treeNodes = new Hashtable();
        treeNodes.put(treeRoot.getResourceSet(), treeRoot);

        Queue queue = new Queue();
        queue.put(treeRoot);

        while (!queue.empty())
        {
            SOCTradeTree currentTreeNode = (SOCTradeTree) queue.get();

            //D.ebugPrintln("%%% Expanding "+currentTreeNode.getResourceSet());
            expandTradeTreeNode(currentTreeNode, treeNodes);

            Enumeration childrenEnum = currentTreeNode.getChildren().elements();

            while (childrenEnum.hasMoreElements())
            {
                SOCTradeTree child = (SOCTradeTree) childrenEnum.nextElement();

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
        Enumeration possibleTrades = treeNodes.keys();

        while (possibleTrades.hasMoreElements())
        {
            SOCResourceSet possibleTradeOutcome = (SOCResourceSet) possibleTrades.nextElement();

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
        Stack stack = new Stack();
        SOCTradeTree cursor = (SOCTradeTree) treeNodes.get(bestTradeOutcome);

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
            currTreeNode = (SOCTradeTree) stack.pop();
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
    protected void expandTradeTreeNode(SOCTradeTree currentTreeNode, Hashtable table)
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
                        Enumeration tableEnum = table.keys();

                        while (tableEnum.hasMoreElements())
                        {
                            SOCResourceSet oldTradeResult = (SOCResourceSet) tableEnum.nextElement();

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
     * make trades to get the target resources
     *
     * @param targetResources  the resources that we want
     * @return true if we sent a request to trade
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
        else
        {
            return false;
        }
    }

    /**
     * consider an offer made by another player
     *
     * @param offer  the offer to consider
     * @return a code that represents how we want to respond
     * note: a negative result means we do nothing
     */
    protected int considerOffer(SOCTradeOffer offer)
    {
        int response = -1;

        SOCPlayer offeringPlayer = game.getPlayer(offer.getFrom());

        if ((offeringPlayer.getCurrentOffer() != null) && (offer == offeringPlayer.getCurrentOffer()))
        {
            boolean[] offeredTo = offer.getTo();

            if (offeredTo[ourPlayerData.getPlayerNumber()])
            {
                response = negotiator.considerOffer2(offer, ourPlayerData.getPlayerNumber());
            }
        }

        return response;
    }

    /**
     * make an offer to another player, or decide to make no offer.
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
     * this means that we want to play a discovery development card
     */
    protected void chooseFreeResources(SOCResourceSet targetResources)
    {
        /**
         * clear our resource choices
         */
        resourceChoices.clear();

        /**
         * find the most needed resource by looking at
         * which of the resources we still need takes the
         * longest to aquire
         */
        SOCResourceSet rsCopy = ourPlayerData.getResources().copy();
        SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());
        int[] rollsPerResource = estimate.getRollsPerResource();

        for (int resourceCount = 0; resourceCount < 2; resourceCount++)
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

            resourceChoices.add(1, mostNeededResource);
            rsCopy.add(1, mostNeededResource);
        }
    }

    /**
     * choose a resource to monopolize
     * @return true if playing the card is worth it
     */
    protected boolean chooseMonopoly()
    {
        int bestResourceCount = 0;
        int bestResource = 0;

        for (int resource = SOCResourceConstants.CLAY;
                resource <= SOCResourceConstants.WOOD; resource++)
        {
            //D.ebugPrintln("$$ resource="+resource);
            int freeResourceCount = 0;
            boolean twoForOne = false;
            boolean threeForOne = false;

            if (ourPlayerData.getPortFlag(resource))
            {
                twoForOne = true;
            }
            else if (ourPlayerData.getPortFlag(SOCBoard.MISC_PORT))
            {
                threeForOne = true;
            }

            int resourceTotal = 0;

            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                if (ourPlayerData.getPlayerNumber() != pn)
                {
                    resourceTotal += game.getPlayer(pn).getResources().getAmount(resource);

                    //D.ebugPrintln("$$ resourceTotal="+resourceTotal);
                }
            }

            if (twoForOne)
            {
                freeResourceCount = resourceTotal / 2;
            }
            else if (threeForOne)
            {
                freeResourceCount = resourceTotal / 3;
            }
            else
            {
                freeResourceCount = resourceTotal / 4;
            }

            //D.ebugPrintln("freeResourceCount="+freeResourceCount);
            if (freeResourceCount > bestResourceCount)
            {
                bestResourceCount = freeResourceCount;
                bestResource = resource;
            }
        }

        if (bestResourceCount > 2)
        {
            monopolyChoice = bestResource;

            return true;
        }
        else
        {
            return false;
        }
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
