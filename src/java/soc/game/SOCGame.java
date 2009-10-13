/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2009 Jeremy D. Monin <jeremy@nand.net>
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
package soc.game;

import soc.disableDebug.D;

import soc.message.SOCMessage;
import soc.util.IntPair;
import soc.util.SOCGameBoardReset;

import java.io.Serializable;

import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.Stack;
import java.util.Vector;


/**
 * A class for holding and manipulating game data.
 * Most methods are not implicitly thread-safe;
 * call {@link #takeMonitor()} and {@link #releaseMonitor()} around them.
 *<P>
 * The model in this client/server game is: The SOCGame at server contains the game's
 * complete state information, and game logic advances there.
 * Each client's local SOCGame contains only partial state (for instance, other
 * players' resources or devel cards may be of unknown type); and the server directly
 * updates clients' game state by sending messages such as
 * {@link soc.message.SOCGameState} and {@link soc.message.SOCSetPlayedDevCard}.
 *<P>
 * For the board <b>coordinate system and terms</b> (hex, node, edge), see the
 * {@link SOCBoard} class javadoc.
 *<P>
 * {@link #putPiece(SOCPlayingPiece)} and other game-action methods update gameState.
 *
 * @author Robert S. Thomas
 */
public class SOCGame implements Serializable, Cloneable
{
    /**
     * Game states.  NEW is a brand-new game, not yet ready to start playing.
     * Players are choosing where to sit, or have all sat but no one has yet clicked
     * the "start game" button.
     * Next state from NEW is {@link #READY} if robots, or {@link #START1A} if only humans
     * are playing.
     *<P>
     * General assumptions for states and their numeric values:
     * <UL>
     * <LI> Active game states are >= {@link #START1A} and < {@link #OVER}
     * <LI> Initial placement ends after {@link #START2B}, going directly to {@link #PLAY}
     * <LI> A Normal turn's "main phase" is {@link #PLAY1}, after dice-roll/card-play in {@link #PLAY}
     * <LI> When the game is waiting for a player to react to something,
     *      state is > {@link #PLAY1}, < {@link #OVER}; state name starts with
     *      PLACING_ or WAITING_
     * </UL>
     *<P>
     * The code reacts to (switches based on) game state in several places. 
     * The main places to check, if you add a game state:
     * <PRE>
        {@link soc.client.SOCBoardPanel#updateMode()}
        {@link soc.client.SOCBuildingPanel#updateButtonStatus()}
        {@link soc.client.SOCPlayerInterface#updateAtGameState()}
        {@link soc.game.SOCGame#putPiece(SOCPlayingPiece)}
        {@link soc.robot.SOCRobotBrain#run()}
        {@link soc.server.SOCServer#sendGameState(SOCGame)}
     * </PRE>
     * Other places to check, if you add a game state:
     * <PRE>
        SOCBoardPanel.BoardPopupMenu.showBuild, showCancelBuild
        SOCBoardPanel.drawBoard
        SOCHandPanel.addPlayer, began, removePlayer, updateAtTurn, updateValue
        SOCGame.addPlayer
        SOCServer.handleSTARTGAME, leaveGame, sitDown, handleCANCELBUILDREQUEST, handlePUTPIECE
        SOCPlayerClient.handleCANCELBUILDREQUEST, SOCDisplaylessPlayerClient.handleCANCELBUILDREQUEST
     * </PRE>
     */
    public static final int NEW = 0; // Brand new game, players sitting down

    /**
     * Ready to start playing.  All humans have chosen a seat.
     * Wait for requested robots to sit down.
     * Once robots have joined the game (this happens in other threads, possibly in other
     * processes), gameState will become {@link #START1A}.
     * @see #READY_RESET_WAIT_ROBOT_DISMISS
     */
    public static final int READY = 1; // Ready to start playing

    public static final int SETOPTIONS_EXCL = 2; // Future use: Game owner setting options, no one can yet connect
    public static final int SETOPTIONS_INCL = 3; // Future use: Game owner setting options, but anyone can connect
        // These are still unused in 1.1.07, even though we now have options,
        // because they are set before the SOCGame is created.

    /**
     * This game object has just been created by a reset, but the old game contains robot players,
     * so we must wait for them to leave before re-inviting anyone to continue the reset process.
     * Once they have all left, state becomes {@link #READY}.
     * See {@link #boardResetOngoingInfo} and (private) SOCServer.resetBoardAndNotify.
     * @since 1.1.07
     */
    public static final int READY_RESET_WAIT_ROBOT_DISMISS = 4;

    /**
     * Players place first settlement.  Proceed in order for each player; next state
     * is {@link #START1B} to place each player's 1st road.
     */
    public static final int START1A = 5; // Players place 1st stlmt

    /**
     * Players place first road.  Next state is {@link #START1A} to place next
     * player's 1st settlement, or if all have placed settlements,
     * {@link #START2A} to place 2nd settlement.
     */
    public static final int START1B = 6; // Players place 1st road

    /**
     * Players place second settlement.  Proceed in reverse order for each player;
     * next state is {@link #START2B} to place 2nd road.
     */
    public static final int START2A = 10; // Players place 2nd stlmt

    /**
     * Players place second road.  Next state is {@link #START2A} to place previous
     * player's 2nd settlement (player changes in reverse order), or if all have placed
     * settlements, {@link #PLAY} to begin first player's turn.
     */
    public static final int START2B = 11; // Players place 2nd road

    /**
     * Start of a normal turn.  Time to roll or play a card.
     * Next state depends on card or roll, but usually is {@link #PLAY1}.
     */
    public static final int PLAY = 15; // Play continues normally; time to roll or play card

    /**
     * Done rolling (or moving robber on 7).  Time for other turn actions,
     * such as building or buying or trading, or playing a card if not already done.
     * Next state depends on what's done, but usually is the next player's {@link #PLAY}.
     */
    public static final int PLAY1 = 20; // Done rolling

    public static final int PLACING_ROAD = 30;
    public static final int PLACING_SETTLEMENT = 31;
    public static final int PLACING_CITY = 32;
    public static final int PLACING_ROBBER = 33;
    public static final int PLACING_FREE_ROAD1 = 40; // Player is placing first road
    public static final int PLACING_FREE_ROAD2 = 41; // Player is placing second road
    public static final int WAITING_FOR_DISCARDS = 50; // Waiting for players to discard
    public static final int WAITING_FOR_CHOICE = 51; // Waiting for player to choose a player
    public static final int WAITING_FOR_DISCOVERY = 52; // Waiting for player to choose 2 resources
    public static final int WAITING_FOR_MONOPOLY = 53; // Waiting for player to choose a resource

    /**
     * The game is over.  A player has accumulated 10 ({@link #VP_WINNER}) victory points,
     * or all players have left the game.
     */
    public static final int OVER = 1000; // The game is over

    /**
     * This game is an obsolete old copy of a new (reset) game with the same name.
     * To assist logic, numeric constant value is greater than {@link #OVER}.
     * @see #resetAsCopy()
     * @see #getResetOldGameState()
     */
    public static final int RESET_OLD = 1001;

    /**
     * seat states
     */
    public static final int VACANT = 0, OCCUPIED = 1;

    /**
     * seatLock states
     */
    public static final boolean LOCKED = true, UNLOCKED = false;

    /**
     * {@link #boardResetVotes} per-player states: no vote sent; yes; no.
     */
    public static final int VOTE_NONE = 0;
    public static final int VOTE_YES  = 1;
    public static final int VOTE_NO   = 2;

    /**
     * maximum number of players in a game
     */
    public static final int MAXPLAYERS = 4;

    /**
     * minimum number of players in a game (was assumed ==MAXPLAYERS in standard 1.0.6).
     * Use isSeatVacant(i) to determine if a player is present;
     * players[i] will be non-null although no player is there.
     */
    public static final int MINPLAYERS = 2;

    /**
     * Number of victory points (10) needed to win.
     * Set to constant for searching if in future, decide 
     * to make a per-game choice.
     */
    public static final int VP_WINNER = 10;

    /**
     * the set of resources a player needs to build a settlement
     */
    public static final SOCResourceSet EMPTY_RESOURCES = new SOCResourceSet();

    /**
     * the set of resources a player needs to build a settlement
     */
    public static final SOCResourceSet SETTLEMENT_SET = new SOCResourceSet(1, 0, 1, 1, 1, 0);

    /**
     * the set of resources a player needs to build a road
     */
    public static final SOCResourceSet ROAD_SET = new SOCResourceSet(1, 0, 0, 0, 1, 0);

    /**
     * the set of resources a player needs to build a city
     */
    public static final SOCResourceSet CITY_SET = new SOCResourceSet(0, 3, 0, 2, 0, 0);

    /**
     * the set of resources a player needs to buy a development card
     */
    public static final SOCResourceSet CARD_SET = new SOCResourceSet(0, 1, 1, 1, 0, 0);

    /**
     * monitor for synchronization
     */
    boolean inUse;

    /**
     * the name of the game
     */
    private String name;

    /**
     * true if this game is ACTIVE
     */
    private boolean active;

    /**
     * true if the game's network is local for practice.  Used by
     * client to route messages to appropriate connection.
     * NOT CURRENTLY SET AT SERVER.  Instead check if server's strSocketName != null,
     * or if connection instanceof LocalStringConnection.
     */
    public boolean isLocal;

    /**
     * For use at server; are there clients connected which aren't at the latest version?
     */
    public boolean hasOldClients;

    /**
     * For use at server; lowest and highest version of connected clients.
     */
    public int clientVersionLowest, clientVersionHighest;

    /**
     * For use at server; lowest version of client which can connect to
     * this game (based on game options/features added in a given version),
     * or -1 if unknown.
     * Format is the internal integer format, see {@link soc.util.Version#versionNumber()}.
     *<P>
     *<b>Reminder:</b> If you add new game options, please be sure that the
     *   robot is also capable of understanding/using them.
     */
    private int clientVersionMinRequired;

    /**
     * true if the game came from a board reset
     */
    private boolean isFromBoardReset;

    /**
     * For the server's use, if a reset is in progress, this holds the reset data
     * until all robots have left (new game state is {@link #READY_RESET_WAIT_ROBOT_DISMISS}).
     * This field is null except within the newly-created game object during reset.
     * @since 1.1.07
     */
    public transient SOCGameBoardReset boardResetOngoingInfo;

    /**
     * If a board reset vote is active, player number who requested the vote.
     * All human players must vote unanimously, or board reset is rejected.
     * -1 if no vote is active.
     * Synchronize on {@link #boardResetVotes} before reading or writing.
     */
    private int boardResetVoteRequester;

    /**
     * If a board reset vote is active, votes are recorded here.
     * Values: {@link #VOTE_NONE}, {@link #VOTE_YES}, {@link #VOTE_NO}.
     * Indexed 0 to SOCGame.MAXPLAYERS-1.
     * Synchronize on this object before reading or writing.
     */
    private int boardResetVotes[];

    /**
     * If a board reset vote is active, we're waiting to receive this many more votes.
     * All human players vote, except the vote requester. Robots do not vote.
     * Synchronize on {@link #boardResetVotes} before reading or writing.
     * When the vote is complete, or before the first vote has begun, this is 0.
     * Set in resetVoteBegin, resetVoteRegister. Cleared in resetVoteClear.
     */
    private int boardResetVotesWaiting;

    /**
     * the game board
     */
    private SOCBoard board;

    /**
     * the game options ({@link SOCGameOption}), or null
     * @since 1.1.07
     */
    private Hashtable opts;

    /**
     * the players; never contains a null element, use {@link #isSeatVacant(int)}
     * to see if a position is occupied.
     */
    private SOCPlayer[] players;

    /**
     * the states for the player's seats
     */
    private int[] seats;

    /**
     * the states if the locks for the player's seats
     */
    private boolean[] seatLocks;

    /**
     * the number of the current player
     */
    private int currentPlayerNumber;

    /**
     * the first player to place a settlement
     */
    private int firstPlayerNumber;

    /**
     * the last player to place the first settlement
     */
    private int lastPlayerNumber;

    /**
     * the current dice result. -1 at start of game, 0 during player's turn before roll (state {@link #PLAY}).
     */
    private int currentDice;

    /**
     * the current game state
     */
    private int gameState;

    /**
     * the old game state
     */
    private int oldGameState;

    /**
     * If true, and if state is {@link #PLACING_ROBBER},
     * the robber is being moved because a knight card
     * has just been played.  Thus, if {@link #forceEndTurn()}
     * is called, the knight card should then be returned to
     * the player's hand. 
     */
    private boolean placingRobberForKnightCard;

    /**
     * If true, this turn is being ended. Controller of game (server) should call {@link #endTurn()}
     * whenever possible.  Usually set when we have called {@link #forceEndTurn()}, and
     * forced the current player to discard randomly, and are waiting for other players
     * to discard in gamestate {@link #WAITING_FOR_DISCARDS}.  Once all players have
     * discarded, the turn should be ended.
     * @see #forceEndTurn()
     */
    private boolean forcingEndTurn;

    /**
     * the player with the largest army, or -1 if none
     */
    private int playerWithLargestArmy;
    private int oldPlayerWithLargestArmy;

    /**
     * the player with the longest road, or -1 if none
     */
    private int playerWithLongestRoad;

    /**
     * used to restore the LR player
     */
    Stack oldPlayerWithLongestRoad;

    /**
     * the player declared winner, if gamestate == OVER; otherwise -1
     */
    private int playerWithWin;

    /**
     * the number of development cards left
     */
    private int numDevCards;

    /**
     * the development card deck
     */
    private int[] devCardDeck;

    /**
     * used to generate random numbers
     */
    private Random rand = new Random();

    /**
     * used to track if there were any player subs
     */
    boolean allOriginalPlayers;

    /**
     * when this game was created
     */
    Date startTime;

    /**
     * expiration time for this game in milliseconds
     */
    long expiration;

    /**
     * The number of normal turns (not rounds, not initial placements), including this turn.
     *  This is 0 during initial piece placement, and 1 when the first player is about to
     *  roll dice for the first time.
     *  updated in {@link #updateAtTurn()}.
     * @since 1.1.07
     */
    private int turnCount;

    /**
     * The number of normal rounds (each player has 1 turn per round, after initial placements), including this round.
     *  for gameoption N7: Roll no 7s during first # rounds.
     *  This is 0 during initial piece placement, and 1 when the first player is about to
     *  roll dice for the first time.  It becomes 2 when that first player's turn begins again.
     *  updated in {@link #updateAtTurn()}.
     * @since 1.1.07
     */
    private int roundCount;

    /**
     * create a new, active game
     *
     * @param n  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     */
    public SOCGame(String n)
    {
        this(n, true, null);
    }

    /**
     * create a new, active game with options
     *
     * @param n  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param op if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     *           Will validate options by calling
     *           {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable)},
     *           and set game's minimum version by calling
     *           {@link SOCGameOption#optionsMinimumVersion(Hashtable)}.
     * @throws IllegalArgumentException if op contains unknown options, or any
     *             object class besides {@link SOCGameOption}
     * @since 1.1.07
     */
    public SOCGame(String n, Hashtable op)
	throws IllegalArgumentException
    {
        this(n, true, op);
    }

    /**
     * create a new game that can be ACTIVE or INACTIVE
     *
     * @param n  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param a  true if this is an active game, false for inactive
     * @throws IllegalArgumentException if game name fails
     *           {@link SOCMessage#isSingleLineAndSafe(String)}. This check was added in 1.1.07.
     */
    public SOCGame(String n, boolean a)
        throws IllegalArgumentException
    {
	this(n, a, null);
    }

    /**
     * create a new game that can be ACTIVE or INACTIVE, and have options
     *
     * @param n  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param a  true if this is an active game, false for inactive
     * @param op if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     *           Will validate options by calling
     *           {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable)},
     *           and set game's minimum version by calling
     *           {@link SOCGameOption#optionsMinimumVersion(Hashtable)}.
     * @throws IllegalArgumentException if op contains unknown options, or any
     *             object class besides {@link SOCGameOption}, or if game name
     *             fails {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @since 1.1.07
     */
    public SOCGame(String n, boolean a, Hashtable op)
	throws IllegalArgumentException
    {
        // For places to initialize fields, see also resetAsCopy().

        if (! SOCMessage.isSingleLineAndSafe(n))
            throw new IllegalArgumentException("n");

        active = a;
        inUse = false;
        name = n;
        board = new SOCBoard();
        players = new SOCPlayer[MAXPLAYERS];
        seats = new int[MAXPLAYERS];
        seatLocks = new boolean[MAXPLAYERS];
        boardResetVotes = new int[MAXPLAYERS];

        for (int i = 0; i < MAXPLAYERS; i++)
        {
            players[i] = new SOCPlayer(i, this);
            seats[i] = VACANT;
            seatLocks[i] = UNLOCKED;
        }

        currentPlayerNumber = -1;
        firstPlayerNumber = -1;
        currentDice = -1;
        playerWithLargestArmy = -1;
        playerWithLongestRoad = -1;
        boardResetVoteRequester = -1;
        playerWithWin = -1;
        numDevCards = 25;
        gameState = NEW;
        turnCount = 0;
        roundCount = 0;
        forcingEndTurn = false;
        placingRobberForKnightCard = false;
        oldPlayerWithLongestRoad = new Stack();

	opts = op;
	if (op == null)
	{
	    clientVersionMinRequired = -1;
	} else {
	    if (! SOCGameOption.adjustOptionsToKnown(op, null))
		throw new IllegalArgumentException("op: unknown option");

	    // the adjust method will also throw IllegalArg if a non-SOCGameOption
	    // object is found within opts.

	    clientVersionMinRequired = SOCGameOption.optionsMinimumVersion(op);

	    // ** Reminder:** If you add new game options/features, please be sure that the
	    //    robot is also capable of understanding/using them.
	}

        if (active)
            startTime = new Date();
    }

    /**
     * take the monitor for this game
     */
    public synchronized void takeMonitor()
    {
        //D.ebugPrintln("TAKE MONITOR");
        while (inUse)
        {
            try
            {
                wait(1000);
            }
            catch (InterruptedException e)
            {
                System.out.println("EXCEPTION IN takeMonitor() -- " + e);
            }
        }

        inUse = true;
    }

    /**
     * release the monitor for this game
     */
    public synchronized void releaseMonitor()
    {
        //D.ebugPrintln("RELEASE MONITOR");
        inUse = false;
        this.notify();
    }

    /**
     * @return allOriginalPlayers
     */
    public boolean allOriginalPlayers()
    {
        return allOriginalPlayers;
    }

    /**
     * @return the start time for this game, or null if inactive
     */
    public Date getStartTime()
    {
        return startTime;
    }

    /**
     * @return the expiration time in milliseconds,
     *            same epoch as {@link java.util.Date#getTime()}
     */
    public long getExpiration()
    {
        return expiration;
    }

    /**
     * set the expiration time
     *
     * @param ex  the expiration time in milliseconds,
     *            same epoch as {@link java.util.Date#getTime()}
     */
    public void setExpiration(long ex)
    {
        expiration = ex;
    }

    /**
     * add a new player
     *
     * @param name  the player's name; must pass {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param pn    the player's number
     * @throws IllegalStateException if player is already sitting in
     *              another seat in this game, or if there are no open seats
     *              (based on seats[] == OCCUPIED, and game option "PL" or MAXPLAYERS)
     *               via {@link #getAvailableSeatCount()}
     * @throws IllegalArgumentException if name fails {@link SOCMessage#isSingleLineAndSafe(String)}.
     *           This exception was added in 1.1.07.
     */
    public void addPlayer(final String name, final int pn)
        throws IllegalStateException, IllegalArgumentException
    {
        if (! SOCMessage.isSingleLineAndSafe(name))
            throw new IllegalArgumentException("name");
        if (seats[pn] == VACANT)
        {
	    if (0 == getAvailableSeatCount())
		throw new IllegalStateException("Game is full");
        }
        SOCPlayer already = getPlayer(name);
        if ((already != null) && (pn != already.getPlayerNumber()))
        {
            throw new IllegalStateException("Already sitting in this game");
        }

        players[pn].setName(name);
        seats[pn] = OCCUPIED;

        if ((gameState > NEW) && (gameState < OVER))
        {
            allOriginalPlayers = false;
        }
    }

    /**
     * remove a player
     *
     * @param name  the player's name
     * @throws IllegalArgumentException if name isn't in this game.
     *           This exception was added in 1.1.07.
     */
    public void removePlayer(String name)
        throws IllegalArgumentException
    {
        SOCPlayer pl = getPlayer(name);
        if (pl == null)
            throw new IllegalArgumentException("name");
        pl.setName(null);
        seats[pl.getPlayerNumber()] = VACANT;

        //D.ebugPrintln("seats["+pl.getPlayerNumber()+"] = VACANT");
    }

    /**
     * @return true if the seat is VACANT
     *
     * @param pn the number of the seat
     * @see #getAvailableSeatCount()
     */
    public boolean isSeatVacant(int pn)
    {
        return (seats[pn] == VACANT);
    }

    /**
     * How many seats are vacant and available for players?
     * Based on {@link #isSeatVacant(int)}, and game
     * option "PL" (maximum players) or {@link #MAXPLAYERS}.
     *
     * @return number of available vacant seats
     * @see #isSeatVacant(int)
     * @since 1.1.07
     */
    public int getAvailableSeatCount()
    {
	int availSeats;
	if (isGameOptionDefined("PL"))
	    availSeats = getGameOptionIntValue("PL");
	else
	    availSeats = MAXPLAYERS;

	for (int i = 0; i < MAXPLAYERS; ++i)
	    if (seats[i] == OCCUPIED)
		--availSeats;

	return availSeats;
    }

    /**
     * locks a seat, so no one can take it
     *
     * @param pn the number of the seat
     */
    public void lockSeat(int pn)
    {
        seatLocks[pn] = LOCKED;
    }

    /**
     * unlocks a seat
     *
     * @param pn the number of the seat
     */
    public void unlockSeat(int pn)
    {
        seatLocks[pn] = UNLOCKED;
    }

    /**
     * @return true if this seat is locked
     *
     * @param pn the number of the seat
     */
    public boolean isSeatLocked(int pn)
    {
        return (seatLocks[pn] == LOCKED);
    }

    /**
     * @return the player object for a player id; never null if pn is in range
     *
     * @param pn  the player number, in range 0 to {@link #MAXPLAYERS}-1
     */
    public SOCPlayer getPlayer(int pn)
    {
        return players[pn];
    }

    /**
     * @return the player object for a player nickname
     * if there is no match, return null
     *
     * @param nn  the nickname
     */
    public SOCPlayer getPlayer(String nn)
    {
        if (nn != null)
        {
            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                if ((nn.equals(players[i].getName())) && ! isSeatVacant(i))
                {
                    return players[i];
                }
            }
        }

        return null;
    }

    /**
     * @return the name of the game
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return this game's options ({@link SOCGameOption}), or null
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     * @see #getGameOptionIntValue(String)
     */
    public Hashtable getGameOptions()
    {
	return opts;
    }

    /**
     * Is this game option contained in the current game's options?
     * @param optKey Name of a {@link SOCGameOption}
     * @return True if option is defined in ths game's options, false otherwise
     * @since 1.1.07
     * @see #isGameOptionSet(String)
     * @see #getGameOptionIntValue(String)
     */
    public boolean isGameOptionDefined(final String optKey)
    {
        if (opts == null)
            return false;
        else
            return opts.containsKey(optKey);
    }

    /**
     * Is this boolean game option currently set to true?
     * @param optKey Name of a {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_BOOL OTYPE_BOOL}
     *               or {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL}
     * @return True if option is set, false if not set or not defined in this game's options
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #getGameOptionIntValue(String)
     * @see #getGameOptionStringValue(String)
     */
    public boolean isGameOptionSet(final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return isGameOptionSet(opts, optKey);
    }

    /**
     * Is this boolean game option currently set to true?
     * @param opts A hashtable of {@link SOCGameOption}, or null
     * @param optKey Name of a {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_BOOL OTYPE_BOOL}
     *               or {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL}
     * @return True if option is set, false if not set or not defined in this game's options
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #getGameOptionIntValue(String)
     * @see #getGameOptionStringValue(String)
     */
    public static boolean isGameOptionSet(Hashtable opts, final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        if (opts == null)
            return false;
        SOCGameOption op = (SOCGameOption) opts.get(optKey);
        if (op == null)
            return false;
        return op.getBoolValue();        
    }

    /**
     * What is this integer game option's current value?
     * @param optKey A {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_INT OTYPE_INT},
     *               {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL},
     *               or {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM}
     * @return Option's current {@link SOCGameOption#getIntValue() intValue},
     *         or 0 if not defined in this game's options;
     *         OTYPE_ENUM's choices give an intVal in range 1 to n.
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     */
    public int getGameOptionIntValue(final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return getGameOptionIntValue(opts, optKey);
    }

    /**
     * What is this integer game option's current value?
     * @param opts A hashtable of {@link SOCGameOption}, or null
     * @param optKey A {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_INT OTYPE_INT},
     *               {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL},
     *               or {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM}
     * @return Option's current {@link SOCGameOption#getIntValue() intValue},
     *         or 0 if not defined in this game's options;
     *         OTYPE_ENUM's choices give an intVal in range 1 to n.
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     */
    public static int getGameOptionIntValue(Hashtable opts, final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        if (opts == null)
            return 0;
        SOCGameOption op = (SOCGameOption) opts.get(optKey);
        if (op == null)
            return 0;
        return op.getIntValue();
    }

    /**
     * What is this string game option's current value?
     * @param optKey A {@link SOCGameOption} of type
     *               {@link SOCGameOption#OTYPE_STR OTYPE_STR}
     *               or {@link SOCGameOption#OTYPE_STRHIDE OTYPE_STRHIDE}
     * @return Option's current {@link SOCGameOption#getStringValue() getStringValue}
     *         or null if not defined in this game's options
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     */
    public String getGameOptionStringValue(final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return getGameOptionStringValue(opts, optKey);
    }

    /**
     * What is this string game option's current value?
     * @param opts A hashtable of {@link SOCGameOption}, or null
     * @param optKey A {@link SOCGameOption} of type
     *               {@link SOCGameOption#OTYPE_STR OTYPE_STR}
     *               or {@link SOCGameOption#OTYPE_STRHIDE OTYPE_STRHIDE}
     * @return Option's current {@link SOCGameOption#getStringValue() getStringValue}
     *         or null if not defined in this game's options
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     */
    public static String getGameOptionStringValue(Hashtable opts, final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        if (opts == null)
            return null;
        SOCGameOption op = (SOCGameOption) opts.get(optKey);
        if (op == null)
            return null;
        return op.getStringValue();
    }

    /**
     * For use at server; lowest version of client which can connect to
     * this game (based on game options/features added in a given version),
     * or -1 if unknown.
     *<P>
     *<b>Reminder:</b> If you add new game options, please be sure that the
     *   robot is also capable of understanding them.
     *
     * @return game version, in same integer format as {@link soc.util.Version#versionNumber()}.
     * @since 1.1.06
     */
    public int getClientVersionMinRequired()
    {
        return clientVersionMinRequired;
    }

    /**
     * @return whether this game was created by board reset of an earlier game
     */
    public boolean isBoardReset()
    {
        return isFromBoardReset;
    }

    /**
     * @return the game board
     */
    public SOCBoard getBoard()
    {
        return board;
    }

    /**
     * set the game board
     *
     * @param gb  the game board
     */
    protected void setBoard(SOCBoard gb)
    {
        board = gb;
    }

    /**
     * @return the list of players
     */
    public SOCPlayer[] getPlayers()
    {
        return players;
    }

    /**
     * set the data for a player
     *
     * @param pn  the number of the player
     * @param pl  the player data
     */
    protected void setPlayer(int pn, SOCPlayer pl)
    {
        players[pn] = pl;
    }

    /**
     * @return the number of the current player
     */
    public int getCurrentPlayerNumber()
    {
        return currentPlayerNumber;
    }

    /**
     * Set the number of the current player, and check for winner.
     * If you want to update other game status, call {@link #updateAtTurn()} afterwards.
     * Called only at client - server instead calls {@link #endTurn()}
     * or {@link #advanceTurn()}.
     * Check for gamestate {@link #OVER} after calling setCurrentPlayerNumber.
     * This is needed because a player can win only during their own turn;
     * if they reach winning points ({@link #VP_WINNER} or more) during another
     * player's turn, they don't win immediately.  When it later becomes their turn,
     * and setCurrentPlayerNumber is called, gamestate may become {@link #OVER}.
     *
     * @param pn  the player number, or -1 permitted in state {@link #OVER}
     * @see #endTurn()
     * @see #checkForWinner()
     */
    public void setCurrentPlayerNumber(int pn)
    {
        //D.ebugPrintln("SETTING CURRENT PLAYER NUMBER TO "+pn);
        if ((pn >= -1) && (pn < players.length))
        {
            currentPlayerNumber = pn;
            if ((pn >= 0) && (players[pn].getTotalVP() >= VP_WINNER))
                checkForWinner();
        }
    }

    /**
     * The number of normal rounds (each player has 1 turn per round, after initial placements), including this round.
     *  This is 0 during initial piece placement, and 1 when the first player is about to
     *  roll dice for the first time.  It becomes 2 when that first player's turn begins again.
     *  @since 1.1.07
     */
    public int getRoundCount()
    {
        return roundCount;
    }

    /**
     * @return the current dice result
     */
    public int getCurrentDice()
    {
        return currentDice;
    }

    /**
     * set the current dice result
     *
     * @param dr  the dice result
     */
    public void setCurrentDice(int dr)
    {
        currentDice = dr;
    }

    /**
     * Current game state.  For general information about
     * what states are expected when, please see the javadoc for {@link #NEW}.
     *
     * @return the current game state
     */
    public int getGameState()
    {
        return gameState;
    }

    /**
     * set the current game state.
     * If the new state is {@link #OVER}, and no playerWithWin yet determined, call checkForWinner.
     * For general information about what states are expected when,
     * please see the javadoc for {@link #NEW}.
     *
     * @param gs  the game state
     * @see #checkForWinner()
     */
    public void setGameState(int gs)
    {
        gameState = gs;
        if ((gameState == OVER) && (playerWithWin == -1))
            checkForWinner();
    }
   
    /**
     * If the game board was reset, get the old game state.
     *
     * @return the old game state
     * @throws IllegalStateException Game state must be RESET_OLD
     *    when called; during normal game play, oldGameState is private.
     */
    public int getResetOldGameState() throws IllegalStateException
    {
        if (gameState != RESET_OLD)
            throw new IllegalStateException
                ("Current state is not RESET_OLD: " + gameState);

        return oldGameState;
    }

    /**
     * If true, this turn is being ended. Controller of game should call {@link #endTurn()}
     * whenever possible.  Usually set if we have called {@link #forceEndTurn()}, and
     * forced the current player to discard randomly, and are waiting for other players
     * to discard in gamestate {@link #WAITING_FOR_DISCARDS}.  Once all players have
     * discarded, the turn should be ended.
     * @see #forceEndTurn()
     */
    public boolean isForcingEndTurn()
    {
        return forcingEndTurn;
    }

    /**
     * @return the number of dev cards in the deck
     */
    public int getNumDevCards()
    {
        return numDevCards;
    }

    /**
     * set the number of dev cards in the deck
     *
     * @param  nd  the number of dev cards in the deck
     */
    public void setNumDevCards(int nd)
    {
        numDevCards = nd;
    }

    /**
     * @return the player with the largest army
     */
    public SOCPlayer getPlayerWithLargestArmy()
    {
        if (playerWithLargestArmy != -1)
        {
            return players[playerWithLargestArmy];
        }
        else
        {
            return null;
        }
    }

    /**
     * set the player with the largest army
     *
     * @param pl  the player
     */
    public void setPlayerWithLargestArmy(SOCPlayer pl)
    {
        if (pl == null)
        {
            playerWithLargestArmy = -1;
        }
        else
        {
            playerWithLargestArmy = pl.getPlayerNumber();
        }
    }

    /**
     * @return the player with the longest road, or null if none
     */
    public SOCPlayer getPlayerWithLongestRoad()
    {
        if (playerWithLongestRoad != -1)
        {
            return players[playerWithLongestRoad];
        }
        else
        {
            return null;
        }
    }

    /**
     * set the player with the longest road
     *
     * @param pl  the player, or null to clear
     */
    public void setPlayerWithLongestRoad(SOCPlayer pl)
    {
        if (pl == null)
        {
            playerWithLongestRoad = -1;
        }
        else
        {
            playerWithLongestRoad = pl.getPlayerNumber();
        }
    }

    /**
     * Find the player who was declared winner at end of game.
     * This is determined in {@link #checkForWinner()}; there is no corresponding setter.
     *
     * @return the winning player, or null if none, or if game is not yet over.
     */
    public SOCPlayer getPlayerWithWin()
    {
        if (playerWithWin != -1)
        {
            return players[playerWithWin];
        }
        else
        {
            return null;
        }
    }

    /**
     * If game is over, formulate a message to tell a player.
     * @param pl Player to tell (may be the winner)
     * @return A message of one of these forms:
     *       "The game is over; you are the winner!"
     *       "The game is over; <someone> won."
     *       "The game is over; no one won."
     * @throws IllegalStateException If the game state is not OVER
     */
    public String gameOverMessageToPlayer(SOCPlayer pl)
        throws IllegalStateException
    {
        if (gameState != OVER)
            throw new IllegalStateException("This game is not over yet");
        String msg;
        SOCPlayer wn = getPlayerWithWin();

        if ((pl != null) && (pl == wn))
        {
            msg = "The game is over; you are the winner!";
        }
        else if (wn != null)
        {
            msg = "The game is over; " + wn.getName() + " won.";
        }
        else
        {
            // Just in case; don't think this can happen
            msg = "The game is over; no one won.";
        }

        return msg;
    }

    /**
     * advance the turn to the previous player,
     * used during initial placement. Does not change any other game state,
     * unless all players have left the game.
     * @return true if the turn advances, false if all players have left and
     *          the gamestate has been changed here to {@link #OVER}.
     */
    protected boolean advanceTurnBackwards()
    {
        final int prevCPN = currentPlayerNumber;

        //D.ebugPrintln("ADVANCE TURN BACKWARDS");
        forcingEndTurn = false;
        currentPlayerNumber--;

        if (currentPlayerNumber < 0)
        {
            currentPlayerNumber = MAXPLAYERS - 1;
        }
        while (isSeatVacant (currentPlayerNumber))
        {
            --currentPlayerNumber;
            if (currentPlayerNumber < 0)
            {
                currentPlayerNumber = MAXPLAYERS - 1;
            }
            if (currentPlayerNumber == prevCPN)
            {
                gameState = OVER;
                return false;
            }
        }

        return true;
    }

    /**
     * advance the turn to the next player. Does not change any other game state,
     * unless all players have left the game.
     * @return true if the turn advances, false if all players have left and
     *          the gamestate has been changed here to {@link #OVER}.
     */
    protected boolean advanceTurn()
    {
        final int prevCPN = currentPlayerNumber;

        //D.ebugPrintln("ADVANCE TURN FORWARDS");
        forcingEndTurn = false;
        currentPlayerNumber++;

        if (currentPlayerNumber == MAXPLAYERS)
        {
            currentPlayerNumber = 0;
        }
        while (isSeatVacant (currentPlayerNumber))
        {
            ++currentPlayerNumber;
            if (currentPlayerNumber == MAXPLAYERS)
            {
                currentPlayerNumber = 0;
            }
            if (currentPlayerNumber == prevCPN)
            {
                gameState = OVER;
                return false;
            }
        }

        return true;
    }

    /**
     * Put this piece on the board and update all related game state.
     * May change current player.
     * If the piece is a city, putPiece removes the settlement there.
     *
     * @param pp the piece to put on the board
     */
    public void putPiece(SOCPlayingPiece pp)
    {
        /**
         * call putPiece() on every player so that each
         * player's updatePotentials() function gets called
         */
        for (int i = 0; i < MAXPLAYERS; i++)
        {
            players[i].putPiece(pp);
        }

        board.putPiece(pp);

        /**
         * if the piece is a city, remove the settlement there
         */
        if (pp.getType() == SOCPlayingPiece.CITY)
        {
            SOCSettlement se = new SOCSettlement(pp.getPlayer(), pp.getCoordinates());

            for (int i = 0; i < MAXPLAYERS; i++)
            {
                players[i].removePiece(se);
            }

            board.removePiece(se);
        }

        /**
         * if this the second initial settlement, give
         * the player some resources
         */
        if ((gameState == START2A) && (pp.getType() == SOCPlayingPiece.SETTLEMENT))
        {
            SOCResourceSet resources = new SOCResourceSet();
            Enumeration hexes = SOCBoard.getAdjacentHexesToNode(pp.getCoordinates()).elements();

            while (hexes.hasMoreElements())
            {
                Integer hex = (Integer) hexes.nextElement();

                switch (board.getHexTypeFromCoord(hex.intValue()))
                {
                case SOCBoard.CLAY_HEX:
                    resources.add(1, SOCResourceConstants.CLAY);

                    break;

                case SOCBoard.ORE_HEX:
                    resources.add(1, SOCResourceConstants.ORE);

                    break;

                case SOCBoard.SHEEP_HEX:
                    resources.add(1, SOCResourceConstants.SHEEP);

                    break;

                case SOCBoard.WHEAT_HEX:
                    resources.add(1, SOCResourceConstants.WHEAT);

                    break;

                case SOCBoard.WOOD_HEX:
                    resources.add(1, SOCResourceConstants.WOOD);

                    break;
                }
            }

            pp.getPlayer().getResources().add(resources);
        }

        if ((gameState == START2B) && (pp.getType() == SOCPlayingPiece.ROAD))
        {
            pp.getPlayer().clearPotentialSettlements();
        }

        /**
         * update which player has longest road
         */
        if (pp.getType() != SOCPlayingPiece.CITY)
        {
            if (pp.getType() == SOCPlayingPiece.ROAD)
            {
                /**
                 * the affected player is the one who build the road
                 */
                updateLongestRoad(pp.getPlayer().getPlayerNumber());
            }
            else
            {
                /**
                 * this is a settlement, check if it cut anyone elses road
                 */
                int[] roads = new int[MAXPLAYERS];

                for (int i = 0; i < MAXPLAYERS; i++)
                {
                    roads[i] = 0;
                }

                Enumeration adjEdgeEnum = SOCBoard.getAdjacentEdgesToNode(pp.getCoordinates()).elements();

                while (adjEdgeEnum.hasMoreElements())
                {
                    Integer adjEdge = (Integer) adjEdgeEnum.nextElement();

                    /**
                     * look for other player's roads adjacent to this node
                     */
                    Enumeration allRoadsEnum = board.getRoads().elements();

                    while (allRoadsEnum.hasMoreElements())
                    {
                        SOCRoad road = (SOCRoad) allRoadsEnum.nextElement();

                        if (adjEdge.intValue() == road.getCoordinates())
                        {
                            roads[road.getPlayer().getPlayerNumber()]++;
                        }
                    }
                }

                /**
                 * if a player other than the one who put the settlement
                 * down has 2 roads adjacent to it, then we need to recalculate
                 * their longest road
                 */
                for (int i = 0; i < MAXPLAYERS; i++)
                {
                    if ((i != pp.getPlayer().getPlayerNumber()) && (roads[i] == 2))
                    {
                        updateLongestRoad(i);

                        /**
                         * check to see if this created a tie
                         */
                        break;
                    }
                }
            }
        }

        /**
         * check if the game is over
         */
        checkForWinner();

        /**
         * update the state of the game, and possibly current player
         */
        if (active)
        {
            advanceTurnStateAfterPutPiece();
        }
    }

    /**
     * After placing a piece on the board, update the state of
     * the game, and possibly current player, for play to continue.
     *<P>
     * Also used in {@link #forceEndTurn()} to continue the game
     * after a cancelled piece placement in {@link #START1A}..{@link #START2B} .
     * If the current player number changes here, {@link #isForcingEndTurn()} is cleared. 
     */
    private void advanceTurnStateAfterPutPiece()
    {
        //D.ebugPrintln("CHANGING GAME STATE FROM "+gameState);
        switch (gameState)
        {
        case START1A:
            gameState = START1B;

            break;

        case START1B:
            {
                int tmpCPN = currentPlayerNumber + 1;
    
                if (tmpCPN >= MAXPLAYERS)
                {
                    tmpCPN = 0;
                }
                while (isSeatVacant (tmpCPN))
                {
                    ++tmpCPN;
                    if (tmpCPN >= MAXPLAYERS)
                    {
                        tmpCPN = 0;
                    }
                }
    
                if (tmpCPN == firstPlayerNumber)
                {
                    // All have placed their first settlement/road.
                    // Begin second placement.
                    gameState = START2A;
                }
                else
                {
                    if (advanceTurn())
                        gameState = START1A;
                }
            }
    
            break;

        case START2A:
            gameState = START2B;

            break;

        case START2B:
            {
                int tmpCPN = currentPlayerNumber - 1;

                // who places next? same algorithm as advanceTurnBackwards.
                if (tmpCPN < 0)
                {
                    tmpCPN = MAXPLAYERS - 1;
                }
                while (isSeatVacant (tmpCPN))
                {
                    --tmpCPN;
                    if (tmpCPN < 0)
                    {
                        tmpCPN = MAXPLAYERS - 1;
                    }
                }
    
                if (tmpCPN == lastPlayerNumber)
                {
                    // All have placed their second settlement/road.
                    // Begin play.
                    // Player number is unchanged; "virtual" endTurn here.
                    // Don't clear forcingEndTurn flag, if it's set.
                    gameState = PLAY;
                    updateAtTurn();
                }
                else
                {
                    if (advanceTurnBackwards())
                        gameState = START2A;
                }
            }

            break;

        case PLACING_ROAD:
        case PLACING_SETTLEMENT:
        case PLACING_CITY:
            gameState = PLAY1;

            break;

        case PLACING_FREE_ROAD1:
            gameState = PLACING_FREE_ROAD2;

            break;

        case PLACING_FREE_ROAD2:
            gameState = oldGameState;

            break;
        }

        //D.ebugPrintln("  TO "+gameState);
    }

    /**
     * a temporary piece has been put on the board
     *
     * @param pp the piece to put on the board
     */
    public void putTempPiece(SOCPlayingPiece pp)
    {
        //D.ebugPrintln("@@@ putTempPiece "+pp);

        /**
         * save who the last lr player was
         */
        oldPlayerWithLongestRoad.push(new SOCOldLRStats(this));

        /**
         * call putPiece() on every player so that each
         * player's updatePotentials() function gets called
         */
        for (int i = 0; i < MAXPLAYERS; i++)
        {
            players[i].putPiece(pp);
        }

        board.putPiece(pp);

        /**
         * if the piece is a city, remove the settlement there
         */
        if (pp.getType() == SOCPlayingPiece.CITY)
        {
            SOCSettlement se = new SOCSettlement(pp.getPlayer(), pp.getCoordinates());

            for (int i = 0; i < MAXPLAYERS; i++)
            {
                players[i].removePiece(se);
            }

            board.removePiece(se);
        }

        /**
         * update which player has longest road
         */
        if (pp.getType() != SOCPlayingPiece.CITY)
        {
            if (pp.getType() == SOCPlayingPiece.ROAD)
            {
                /**
                 * the affected player is the one who build the road
                 */
                updateLongestRoad(pp.getPlayer().getPlayerNumber());
            }
            else
            {
                /**
                 * this is a settlement, check if it cut anyone elses road
                 */
                int[] roads = new int[MAXPLAYERS];

                for (int i = 0; i < MAXPLAYERS; i++)
                {
                    roads[i] = 0;
                }

                Enumeration adjEdgeEnum = SOCBoard.getAdjacentEdgesToNode(pp.getCoordinates()).elements();

                while (adjEdgeEnum.hasMoreElements())
                {
                    Integer adjEdge = (Integer) adjEdgeEnum.nextElement();

                    /**
                     * look for other player's roads adjacent to this node
                     */
                    Enumeration allRoadsEnum = board.getRoads().elements();

                    while (allRoadsEnum.hasMoreElements())
                    {
                        SOCRoad road = (SOCRoad) allRoadsEnum.nextElement();

                        if (adjEdge.intValue() == road.getCoordinates())
                        {
                            roads[road.getPlayer().getPlayerNumber()]++;
                        }
                    }
                }

                /**
                 * if a player other than the one who put the settlement
                 * down has 2 roads adjacent to it, then we need to recalculate
                 * their longest road
                 */
                for (int i = 0; i < MAXPLAYERS; i++)
                {
                    if ((i != pp.getPlayer().getPlayerNumber()) && (roads[i] == 2))
                    {
                        updateLongestRoad(i);

                        /**
                         * check to see if this created a tie
                         */
                        break;
                    }
                }
            }
        }
    }

    /**
     * undo the putting of a temporary or initial piece
     *
     * @param pp the piece to put on the board
     */
    protected void undoPutPieceCommon(SOCPlayingPiece pp)
    {
        //D.ebugPrintln("@@@ undoPutTempPiece "+pp);
        board.removePiece(pp);

        //
        // call undoPutPiece() on every player so that 
        // they can update their potentials
        //
        for (int i = 0; i < MAXPLAYERS; i++)
        {
            players[i].undoPutPiece(pp);   // If state START2B, will also zero resources
        }

        //
        // if the piece is a city, put the settlement back
        //
        if (pp.getType() == SOCPlayingPiece.CITY)
        {
            SOCSettlement se = new SOCSettlement(pp.getPlayer(), pp.getCoordinates());

            for (int i = 0; i < MAXPLAYERS; i++)
            {
                players[i].putPiece(se);
            }

            board.putPiece(se);
        }
    }

    /**
     * undo the putting of a temporary piece
     *
     * @param pp the piece to put on the board
     *
     * @see #undoPutInitSettlement(SOCPlayingPiece)
     */
    public void undoPutTempPiece(SOCPlayingPiece pp)
    {
        undoPutPieceCommon(pp);

        //
        // update which player has longest road
        //
        SOCOldLRStats oldLRStats = (SOCOldLRStats) oldPlayerWithLongestRoad.pop();
        oldLRStats.restoreOldStats(this);
    }

    /**
     * undo the putting of an initial settlement.
     * If state is STATE2B, resources will be returned.
     * Player is unchanged; state will become STATE1A or STATE2A.
     *
     * @param pp the piece to remove from the board
     */
    public void undoPutInitSettlement(SOCPlayingPiece pp)
    {
        if ((gameState != START1B) && (gameState != START2B))
            throw new IllegalStateException("Cannot remove at this game state: " + gameState);
        if (pp.getType() != SOCPlayingPiece.SETTLEMENT)
            throw new IllegalArgumentException("Not a settlement: type " + pp.getType());
        if (pp.getCoordinates() != pp.getPlayer().getLastSettlementCoord())
            throw new IllegalArgumentException("Not coordinate of last settlement");

        undoPutPieceCommon(pp);  // Will also zero resources via player.undoPutPiece

        if (gameState == START1B)
            gameState = START1A;
        else // gameState == START2B
            gameState = START2A;
    }

    /**
     * do the things involved in starting a game:
     * shuffle the tiles and cards,
     * make a board,
     * choose first player.
     * gameState becomes {@link #START1A}.
     *<P>
     * Called only at server, not client.
     */
    public void startGame()
    {
        board.makeNewBoard(opts);

        /**
         * shuffle the development cards
         */
        devCardDeck = new int[25];

        int i;
        int j;

        for (i = 0; i < 14; i++)
        {
            devCardDeck[i] = SOCDevCardConstants.KNIGHT;
        }

        for (i = 14; i < 16; i++)
        {
            devCardDeck[i] = SOCDevCardConstants.ROADS;
        }

        for (i = 16; i < 18; i++)
        {
            devCardDeck[i] = SOCDevCardConstants.MONO;
        }

        for (i = 18; i < 20; i++)
        {
            devCardDeck[i] = SOCDevCardConstants.DISC;
        }

        devCardDeck[20] = SOCDevCardConstants.CAP;
        devCardDeck[21] = SOCDevCardConstants.LIB;
        devCardDeck[22] = SOCDevCardConstants.UNIV;
        devCardDeck[23] = SOCDevCardConstants.TEMP;
        devCardDeck[24] = SOCDevCardConstants.TOW;

        for (j = 0; j < 10; j++)
        {
            for (i = 1; i < devCardDeck.length; i++) // don't swap 0 with 0!
            {
                // Swap a random card below the ith card with the ith card
                int idx = Math.abs(rand.nextInt() % (devCardDeck.length - 1));
                int tmp = devCardDeck[idx];
                devCardDeck[idx] = devCardDeck[i];
                devCardDeck[i] = tmp;
            }
        }

        allOriginalPlayers = true;
        gameState = START1A;

        /**
         * choose who goes first
         */
        do
        {
            currentPlayerNumber = Math.abs(rand.nextInt() % MAXPLAYERS);
        } while (isSeatVacant(currentPlayerNumber));
        setFirstPlayer(currentPlayerNumber);
    }

    /**
     * sets who the first player is
     *
     * @param pn  the seat number of the first player
     */
    public void setFirstPlayer(int pn)
    {
        firstPlayerNumber = pn;
        if (pn < 0)  // -1 == not set yet; use <0 to be defensive in while-loop
        {
            lastPlayerNumber = -1;
            return;
        }
        lastPlayerNumber = pn - 1;

        if (lastPlayerNumber < 0)
        {
            lastPlayerNumber = MAXPLAYERS - 1;
        }
        while (isSeatVacant (lastPlayerNumber))
        {
            --lastPlayerNumber;
            if (lastPlayerNumber < 0)
            {
                lastPlayerNumber = MAXPLAYERS - 1;
            }
            if (lastPlayerNumber == firstPlayerNumber)
            {
                // Should not happen: All seats blank                    
                D.ebugPrintln("** setFirstPlayer: Should not happen: All seats blank");
                lastPlayerNumber = -1;
                break;
            }
        }
    }

    /**
     * @return the seat number of the first player
     */
    public int getFirstPlayer()
    {
        return firstPlayerNumber;
    }

    /**
     * Can this player end the current turn?
     *<P>
     * In some states, the current player can't end their turn yet
     * (such as needing to move the robber, or choose resources for a
     *  year-of-plenty card, or discard if a 7 is rolled).
     * 
     * @param pn  player number of the player who wants to end the turn
     * @return true if ok for this player to end the turn
     *    (They are current player, game state is {@link #PLAY1})
     *
     * @see #endTurn()
     * @see #forceEndTurn()
     */
    public boolean canEndTurn(int pn)
    {
        if (currentPlayerNumber != pn)
        {
            return false;
        }
        else if (gameState != PLAY1)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    /**
     * end the turn for the current player, and check for winner.
     * Check for gamestate >= {@link #OVER} after calling endTurn.
     * endTurn() is called only at server - client instead calls
     * {@link #setCurrentPlayerNumber(int)}.
     * endTurn() is not called before the first dice roll.
     * endTurn() will call {@link #updateAtTurn()}.
     *<P>
     * The winner check is needed because a player can win only
     * during their own turn; if they reach winning points ({@link #VP_WINNER}
     * or more) during another player's turn, they must wait.
     *
     * @see #checkForWinner()
     * @see #forceEndTurn()
     * @see #isForcingEndTurn()
     */
    public void endTurn()
    {
        gameState = PLAY;
        if (advanceTurn())
        {
            updateAtTurn();
            players[currentPlayerNumber].setPlayedDevCard(false);  // client calls this in handleSETPLAYEDDEVCARD
            if (players[currentPlayerNumber].getTotalVP() >= VP_WINNER)
                checkForWinner();
        }
    }

    /**
     * Update game state as needed when a player begins their turn (before dice are rolled).
     * Call this after {@link #setCurrentPlayerNumber(int)}.
     * May be called during initial placement.
     *<UL>
     *<LI> Set first player and last player, if they're currently -1
     *<LI> Set current dice to 0
     *<LI> Mark current player's new dev cards as old
     *<LI> Clear any votes to reset the board
     *<LI> If game state is {@link #PLAY}, increment turnCount (and roundCount if necessary).
     *</UL>
     * Called by server and client.
     * @since 1.1.07
     */
    public void updateAtTurn()
    {
        if (firstPlayerNumber == -1)
            setFirstPlayer(currentPlayerNumber);  // also sets lastPlayerNumber

        currentDice = 0;
        players[currentPlayerNumber].getDevCards().newToOld();
        resetVoteClear();
        if (gameState == PLAY)
        {
            ++turnCount;
            if (currentPlayerNumber == firstPlayerNumber)
                ++roundCount;
        }
    }

    /**
     * In an active game, force current turn to be able to be ended.
     * Takes whatever action needed to force current player to end their turn,
     * and if possible, sets state to {@link #PLAY1}, but does not call {@link #endTurn()}.
     * May be used if player loses connection, or robot does not respond.
     *<P>
     * Since only the server calls {@link #endTurn()}, this method does not do so.
     * This method also does not check if a board-reset vote is in progress,
     * because endTurn will unconditionally cancel such a vote.
     *<P>
     * After calling forceEndTurn, usually the gameState will be {@link #PLAY1},  
     * and the caller should call {@link #endTurn()}.  The {@link #isForcingEndTurn()}
     * flag is also set.
     * Exceptions (caller should not call endTurn) are these return types:
     * <UL>
     * <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD_WAIT}
     *       - Have forced current player to discard randomly, must now
     *         wait for other players to discard.
     *         gameState is {@link #WAITING_FOR_DISCARDS}, current player
     *         as yet unchanged.
     * <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADV}
     *       - During initial placement, have skipped placement of
     *         a player's first settlement or road.
     *         gameState is {@link #START1A}, current player has changed.
     * <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADVBACK}
     *       - During initial placement, have skipped placement of
     *         a player's second settlement or road. (Or, final player's
     *         first _and_ second settlement or road.)
     *         gameState is {@link #START2A}, current player has changed.
     *       <P>
     *       Note that for the very last initial road placed, during normal
     *       gameplay, that player continues by rolling the first turn's dice.
     *       To force skipping such a placement, the caller should call endTurn()
     *       to change the current player.  This is indicated by
     *       {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_TURN}.
     * </UL>
     *
     * @return Type of action performed, one of these values:
     *     <UL>
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_NONE}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADV}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADVBACK}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_TURN}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_RET_UNPLACE}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_UNPLACE_ROBBER}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD_WAIT}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_LOST_CHOICE}
     *     </UL>
     * @throws IllegalStateException if game is not active
     *     (gamestate < {@link #START1A} or >= {@link #OVER})
     * @see #canEndTurn(int)
     * @see #endTurn()
     */
    public SOCForceEndTurnResult forceEndTurn()
        throws IllegalStateException
    {
        if ((gameState < START1A) || (gameState >= OVER))
            throw new IllegalStateException("Game not active: state " + gameState);

        forcingEndTurn = true;

        switch (gameState)
        {
        case START1A:
        case START1B:
            return forceEndTurnStartState(true);
                // FORCE_ENDTURN_UNPLACE_START_ADV
                // or FORCE_ENDTURN_UNPLACE_START_ADVBACK

        case START2A:
        case START2B:
            return forceEndTurnStartState(false);
                // FORCE_ENDTURN_UNPLACE_START_ADVBACK
                // or FORCE_ENDTURN_UNPLACE_START_TURN

        case PLAY:
            gameState = PLAY1;
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_NONE);

        case PLAY1:
            // already can end it
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_NONE);

        case PLACING_ROAD:
            cancelBuildRoad(currentPlayerNumber);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE, ROAD_SET);

        case PLACING_SETTLEMENT:
            cancelBuildSettlement(currentPlayerNumber);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE, SETTLEMENT_SET);

        case PLACING_CITY:
            cancelBuildCity(currentPlayerNumber);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE, CITY_SET);

        case PLACING_ROBBER:
            {
                boolean isFromDevCard = placingRobberForKnightCard;
                gameState = PLAY1;
                if (isFromDevCard)
                {
                    placingRobberForKnightCard = false;
                    players[currentPlayerNumber].getDevCards().add(1, SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT);
                }
                return new SOCForceEndTurnResult
                    (SOCForceEndTurnResult.FORCE_ENDTURN_UNPLACE_ROBBER,
                     isFromDevCard ? SOCDevCardConstants.KNIGHT : -1);
            }

        case PLACING_FREE_ROAD1:
        case PLACING_FREE_ROAD2:
            gameState = PLAY1;
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE);

        case WAITING_FOR_DISCARDS:
            return forceEndTurnChkDiscards(currentPlayerNumber);  // sets gameState, discards randomly

        case WAITING_FOR_CHOICE:
            gameState = PLAY1;
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE);

        case WAITING_FOR_DISCOVERY:
            gameState = PLAY1;
            players[currentPlayerNumber].getDevCards().add(1, SOCDevCardSet.OLD, SOCDevCardConstants.DISC);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE,
                 SOCDevCardConstants.DISC);

        case WAITING_FOR_MONOPOLY:
            gameState = PLAY1;
            players[currentPlayerNumber].getDevCards().add(1, SOCDevCardSet.OLD, SOCDevCardConstants.MONO);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE,
                 SOCDevCardConstants.MONO);

        default:
            throw new IllegalStateException("Internal error in force, un-handled gamestate: "
                    + gameState);
        }

        // Always returns within switch
    }

    /**
     * Special forceEndTurn() treatment for start-game states.
     * See {@link #forceEndTurn()} for description.
     *
     * @param advTurnForward Should the next player be normal (placing first settlement),
     *                       or backwards (placing second settlement)?
     * @return A forceEndTurn result of type
     *         {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADV},
     *         {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADVBACK},
     *         or {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_TURN}.
     */
    private SOCForceEndTurnResult forceEndTurnStartState(boolean advTurnForward)
    {
        final int cpn = currentPlayerNumber;
        int cancelResType;  // Turn result type

        /**
         * Set the state we're advancing "from";
         * this is needed because {@link #START1A}, {@link #START2A}
         * don't change player number after placing their piece.
         */
        if (advTurnForward)
            gameState = START1B;
        else
            gameState = START2B;

        advanceTurnStateAfterPutPiece();  // Changes state, may change current player

        if (cpn == currentPlayerNumber)
        {
            // Player didn't change.  This happens when the last player places
            // their first or second road.  But we're trying to end this player's
            // turn, and give another player a chance.
            if (advTurnForward)
            {
                // Was first placement; allow other players to begin second placement. 
                // This player won't get a second placement either.
                gameState = START2A;
                advanceTurnBackwards();
                cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADVBACK;
            } else {
                // Was second placement; begin normal gameplay.
                // Set resType to tell caller to call endTurn().
                gameState = PLAY1;
                cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_TURN;
            }
        } else {
            // OK, player has changed.  This means advanceTurnStateAfterPutPiece()
            // has also cleared the forcingEndTurn flag.
            if (advTurnForward)
                cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADV;
            else
                cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADVBACK;
        }

        return new SOCForceEndTurnResult(cancelResType);
    }

    /**
     * Randomly discard from this player's hand, by calling {@link #discard(int, SOCResourceSet)}.
     * Then look at other players' hand size. If no one else must discard,
     * ready to end turn, set state {@link #PLAY1}.
     * Otherwise, must wait for them; if so,
     * set game state to {@link #WAITING_FOR_DISCARDS}.
     * When called, assumes {@link #isForcingEndTurn()} flag is already set.
     *
     * @param pn Player number to force to randomly discard
     * @return The force result, including any discarded resources.
     *         Type will be {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD}
     *         or {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD_WAIT}.
     */
    private SOCForceEndTurnResult forceEndTurnChkDiscards(int pn)
    {
        // select random cards, and discard
        SOCResourceSet discards = new SOCResourceSet();
        {
            SOCResourceSet hand = players[pn].getResources(); 
            discardPickRandom(hand, hand.getTotal() / 2, discards, rand);
            discard(pn, discards);  // Checks for other discarders, sets gameState
        }

        if (gameState == WAITING_FOR_DISCARDS)
        {
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_DISCARD_WAIT, discards, true);
        } else {
            // gameState == PLAY1 - was set in discard()
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_DISCARD, discards, true);
        }
    }

    /**
     * Choose discards at random; does not actually discard anything.
     *
     * @param fromHand     Discard from this set
     * @param numDiscards  This many must be discarded
     * @param discards     Add discards to this set (typically new,empty, when called)
     * @param rand         Source of random
     */
    public static void discardPickRandom(SOCResourceSet fromHand, int numDiscards, SOCResourceSet discards, Random rand)
    {
        Vector tempHand = new Vector(16);

        // System.err.println("resources="+ourPlayerData.getResources());
        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            for (int i = fromHand.getAmount(rsrcType);
                    i != 0; i--)
            {
                tempHand.addElement(new Integer(rsrcType));

                // System.err.println("rsrcType="+rsrcType);
            }
        }

        /**
         * pick cards
         */
        for (; numDiscards > 0; numDiscards--)
        {
            // System.err.println("numDiscards="+numDiscards+"|hand.size="+hand.size());
            int idx = Math.abs(rand.nextInt() % tempHand.size());

            // System.err.println("idx="+idx);
            discards.add(1, ((Integer) tempHand.elementAt(idx)).intValue());
            tempHand.removeElementAt(idx);
        }
    }

    /**
     * Force this non-current player to discard randomly.  Used at server when a
     * player must discard and they lose connection while the game is waiting for them.
     *<P>
     * On return, gameState will be:
     *<UL>
     * <LI> {@link #WAITING_FOR_DISCARDS} if other players still must discard
     * <LI> {@link #PLAY1} if everyone has discarded, and {@link #isForcingEndTurn()} is set
     * <LI> {@link #PLACING_ROBBER} if everyone has discarded, and {@link #isForcingEndTurn()} is not set
     *</UL>
     *
     * @param pn Player number to discard; player must must need to discard,
     *           must not be current player (use {@link #forceEndTurn()} for that)
     * @return   Set of resource cards which were discarded
     * @throws IllegalStateException If the gameState isn't {@link #WAITING_FOR_DISCARDS},
     *                               or if pn's {@link SOCPlayer#getNeedToDiscard()} is false,
     *                               or if pn == currentPlayer.
     */
    public SOCResourceSet playerDiscardRandom(int pn)
        throws IllegalStateException
    {
        if (pn == currentPlayerNumber)
            throw new IllegalStateException("Cannot call for current player, use forceEndTurn instead");
        if (gameState != WAITING_FOR_DISCARDS)
            throw new IllegalStateException("gameState not WAITING_FOR_DISCARDS: " + gameState);
        if (! (players[pn].getNeedToDiscard()))
            throw new IllegalStateException("Player " + pn + " does not need to discard");

        // Since doesn't change current player number, this is safe to call
        SOCForceEndTurnResult rs = forceEndTurnChkDiscards(pn);
        return rs.getResourcesGainedLost();
    }

    /**
     * @return true if it's ok for this player to roll the dice
     *
     * @param pn  player number of the player who wants to roll
     */
    public boolean canRollDice(int pn)
    {
        if (currentPlayerNumber != pn)
        {
            return false;
        }
        else if (gameState != PLAY)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    /**
     * roll the dice.
     * Checks game option N7: Roll no 7s during first # rounds
     */
    public IntPair rollDice()
    {
        // N7: Roll no 7s during first # rounds.
        //     Use > not >= because roundCount includes current round
        final boolean okToRoll7 =
            (! isGameOptionSet("N7")) || (roundCount > getGameOptionIntValue("N7"));

        int die1, die2;
        do
        {
            die1 = Math.abs(rand.nextInt() % 6) + 1;
            die2 = Math.abs(rand.nextInt() % 6) + 1;

            currentDice = die1 + die2;
        } while ((currentDice == 7) && ! okToRoll7);

        /**
         * handle the seven case
         */
        if (currentDice == 7)
        {
            /**
             * if there are players with too many cards, wait for
             * them to discard
             */
            for (int i = 0; i < MAXPLAYERS; i++)
            {
                if (players[i].getResources().getTotal() > 7)
                {
                    players[i].setNeedToDiscard(true);
                    gameState = WAITING_FOR_DISCARDS;
                }
            }

            /**
             * if no one needs to discard, then wait for
             * the robber to move
             */
            if (gameState != WAITING_FOR_DISCARDS)
            {
                placingRobberForKnightCard = false;
                oldGameState = PLAY1;
                gameState = PLACING_ROBBER;
            }
        }
        else
        {
            /**
             * distribute resources
             */
            for (int i = 0; i < MAXPLAYERS; i++)
            {
                if (! isSeatVacant(i))
                {
                    SOCResourceSet newResources = getResourcesGainedFromRoll(players[i], currentDice);
                    players[i].getResources().add(newResources);
                }
            }

            gameState = PLAY1;
        }

        return new IntPair(die1, die2);
    }

    /**
     * figure out what resources a player would get on a given roll
     *
     * @param player   the player
     * @param roll     the roll
     *
     * @return the resource set
     */
    public SOCResourceSet getResourcesGainedFromRoll(SOCPlayer player, int roll)
    {
        SOCResourceSet resources = new SOCResourceSet();

        /**
         * check the hexes touching settlements
         */
        Enumeration sEnum = player.getSettlements().elements();

        while (sEnum.hasMoreElements())
        {
            SOCSettlement se = (SOCSettlement) sEnum.nextElement();
            Enumeration hexes = SOCBoard.getAdjacentHexesToNode(se.getCoordinates()).elements();

            while (hexes.hasMoreElements())
            {
                Integer hex = (Integer) hexes.nextElement();

                if ((board.getNumberOnHexFromCoord(hex.intValue()) == roll) && (hex.intValue() != board.getRobberHex()))
                {
                    switch (board.getHexTypeFromCoord(hex.intValue()))
                    {
                    case SOCBoard.CLAY_HEX:
                        resources.add(1, SOCResourceConstants.CLAY);

                        break;

                    case SOCBoard.ORE_HEX:
                        resources.add(1, SOCResourceConstants.ORE);

                        break;

                    case SOCBoard.SHEEP_HEX:
                        resources.add(1, SOCResourceConstants.SHEEP);

                        break;

                    case SOCBoard.WHEAT_HEX:
                        resources.add(1, SOCResourceConstants.WHEAT);

                        break;

                    case SOCBoard.WOOD_HEX:
                        resources.add(1, SOCResourceConstants.WOOD);

                        break;
                    }
                }
            }
        }

        /**
         * check the settlements touching cities
         */
        Enumeration cEnum = player.getCities().elements();

        while (cEnum.hasMoreElements())
        {
            SOCCity ci = (SOCCity) cEnum.nextElement();
            Enumeration hexes = SOCBoard.getAdjacentHexesToNode(ci.getCoordinates()).elements();

            while (hexes.hasMoreElements())
            {
                Integer hex = (Integer) hexes.nextElement();

                if ((board.getNumberOnHexFromCoord(hex.intValue()) == roll) && (hex.intValue() != board.getRobberHex()))
                {
                    switch (board.getHexTypeFromCoord(hex.intValue()))
                    {
                    case SOCBoard.CLAY_HEX:
                        resources.add(2, SOCResourceConstants.CLAY);

                        break;

                    case SOCBoard.ORE_HEX:
                        resources.add(2, SOCResourceConstants.ORE);

                        break;

                    case SOCBoard.SHEEP_HEX:
                        resources.add(2, SOCResourceConstants.SHEEP);

                        break;

                    case SOCBoard.WHEAT_HEX:
                        resources.add(2, SOCResourceConstants.WHEAT);

                        break;

                    case SOCBoard.WOOD_HEX:
                        resources.add(2, SOCResourceConstants.WOOD);

                        break;
                    }
                }
            }
        }

        return resources;
    }

    /**
     * @return true if the player can discard these resources
     *
     * @param pn  the number of the player that is discarding
     * @param rs  the resources that the player is discarding
     */
    public boolean canDiscard(int pn, SOCResourceSet rs)
    {
        if (gameState != WAITING_FOR_DISCARDS)
        {
            return false;
        }

        SOCResourceSet resources = players[pn].getResources();

        if (!players[pn].getNeedToDiscard())
        {
            return false;
        }

        if (rs.getTotal() != (resources.getTotal() / 2))
        {
            return false;
        }

        if (!resources.contains(rs))
        {
            return false;
        }

        return true;
    }

    /**
     * A player is discarding resources. Discard, check if other players
     * must still discard, and set gameState to {@link #WAITING_FOR_DISCARDS}
     * or {@link #PLACING_ROBBER} accordingly.
     *<P>
     * Special case:
     * If {@link #isForcingEndTurn()}, and no one else needs to discard,
     * gameState becomes {@link #PLAY1} but the caller must call
     * {@link #endTurn()} as soon as possible.
     *
     * @param pn   the number of the player
     * @param rs   the resources that are being discarded
     */
    public void discard(int pn, SOCResourceSet rs)
    {
        players[pn].getResources().subtract(rs);
        players[pn].setNeedToDiscard(false);

        /**
         * check if we're still waiting for players to discard
         */
        gameState = PLACING_ROBBER;  // assumes oldGameState set already
        placingRobberForKnightCard = false;  // known because robber doesn't trigger discard

        for (int i = 0; i < MAXPLAYERS; i++)
        {
            if (players[i].getNeedToDiscard())
            {
                gameState = WAITING_FOR_DISCARDS;

                break;
            }
        }

        /**
         * if no one needs to discard, and not forcing end of turn,
         * then wait for the robber to move
         */
        if (gameState != WAITING_FOR_DISCARDS)
        {
            oldGameState = PLAY1;
            if (! forcingEndTurn)
                gameState = PLACING_ROBBER;
            else
                gameState = PLAY1;
        }
    }

    /**
     * Can this player currently move the robber to these coordinates?
     * Must be different from current robber coordinates.
     * Must not be a desert if {@link SOCGameOption game option} RD is set to true
     * ("Robber can't return to the desert").
     * Must be current player.
     * 
     * @return true if the player can move the robber to the coordinates
     *
     * @param pn  the number of the player that is moving the robber
     * @param co  the coordinates
     */
    public boolean canMoveRobber(int pn, int co)
    {
        if (gameState != PLACING_ROBBER)
        {
            return false;
        }

        if (currentPlayerNumber != pn)
        {
            return false;
        }

        if (board.getRobberHex() == co)
        {
            return false;
        }

        int hexType = board.getHexTypeFromCoord(co);

        if ((hexType == SOCBoard.DESERT_HEX) && isGameOptionSet("RD"))
            return false;

        if ((hexType != SOCBoard.CLAY_HEX) && (hexType != SOCBoard.ORE_HEX) && (hexType != SOCBoard.SHEEP_HEX) && (hexType != SOCBoard.WHEAT_HEX) && (hexType != SOCBoard.WOOD_HEX) && (hexType != SOCBoard.DESERT_HEX))
        {
            return false;
        }

        return true;
    }

    /**
     * move the robber.
     *
     * If no victims (players to possibly steal from): State becomes oldGameState.
     * If just one victim: call stealFromPlayer, State becomes oldGameState.
     * If multiple possible victims: Player must choose a victim; State becomes WAITING_FOR_CHOICE.
     *
     * @param pn  the number of the player that is moving the robber
     * @param co  the coordinates
     *
     * @return returns a result that says if a resource was stolen, or
     *         if the player needs to make a choice.  It also returns
     *         what was stolen and who was the victim.
     */
    public SOCMoveRobberResult moveRobber(int pn, int co)
    {
        SOCMoveRobberResult result = new SOCMoveRobberResult();

        board.setRobberHex(co);

        /**
         * do the robbing thing
         */
        Vector victims = getPossibleVictims();

        if (victims.isEmpty())
        {
            gameState = oldGameState;
        }
        else if (victims.size() == 1)
        {
            SOCPlayer victim = (SOCPlayer) victims.firstElement();
            int loot = stealFromPlayer(victim.getPlayerNumber());
            result.setLoot(loot);
        }
        else
        {
            /**
             * the current player needs to make a choice
             */
            gameState = WAITING_FOR_CHOICE;
        }

        result.setVictims(victims);

        return result;
    }

    /**
     * @return true if the current player can choose a player to rob
     *
     * @param pn  the number of the player to rob
     */
    public boolean canChoosePlayer(int pn)
    {
        if (gameState != WAITING_FOR_CHOICE)
        {
            return false;
        }

        Enumeration plEnum = getPossibleVictims().elements();

        while (plEnum.hasMoreElements())
        {
            SOCPlayer pl = (SOCPlayer) plEnum.nextElement();

            if (pl.getPlayerNumber() == pn)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * @return a list of players touching a hex
     *
     * @param hex  the coordinates of the hex
     */
    public Vector getPlayersOnHex(int hex)
    {
        Vector playerList = new Vector(MAXPLAYERS);

        for (int i = 0; i < MAXPLAYERS; i++)
        {
            Vector settlements = players[i].getSettlements();
            Vector cities = players[i].getCities();
            Enumeration seEnum;
            Enumeration ciEnum;
            int node;
            boolean touching = false;

            node = hex + 0x01;
            seEnum = settlements.elements();

            while (seEnum.hasMoreElements())
            {
                SOCSettlement se = (SOCSettlement) seEnum.nextElement();

                if (se.getCoordinates() == node)
                {
                    touching = true;

                    break;
                }
            }

            if (!touching)
            {
                ciEnum = cities.elements();

                while (ciEnum.hasMoreElements())
                {
                    SOCCity ci = (SOCCity) ciEnum.nextElement();

                    if (ci.getCoordinates() == node)
                    {
                        touching = true;

                        break;
                    }
                }

                if (!touching)
                {
                    node = hex + 0x12;
                    seEnum = settlements.elements();

                    while (seEnum.hasMoreElements())
                    {
                        SOCSettlement se = (SOCSettlement) seEnum.nextElement();

                        if (se.getCoordinates() == node)
                        {
                            touching = true;

                            break;
                        }
                    }

                    if (!touching)
                    {
                        ciEnum = cities.elements();

                        while (ciEnum.hasMoreElements())
                        {
                            SOCCity ci = (SOCCity) ciEnum.nextElement();

                            if (ci.getCoordinates() == node)
                            {
                                touching = true;

                                break;
                            }
                        }

                        if (!touching)
                        {
                            node = hex + 0x21;
                            seEnum = settlements.elements();

                            while (seEnum.hasMoreElements())
                            {
                                SOCSettlement se = (SOCSettlement) seEnum.nextElement();

                                if (se.getCoordinates() == node)
                                {
                                    touching = true;

                                    break;
                                }
                            }

                            if (!touching)
                            {
                                ciEnum = cities.elements();

                                while (ciEnum.hasMoreElements())
                                {
                                    SOCCity ci = (SOCCity) ciEnum.nextElement();

                                    if (ci.getCoordinates() == node)
                                    {
                                        touching = true;

                                        break;
                                    }
                                }

                                node = hex + 0x10;
                                seEnum = settlements.elements();

                                while (seEnum.hasMoreElements())
                                {
                                    SOCSettlement se = (SOCSettlement) seEnum.nextElement();

                                    if (se.getCoordinates() == node)
                                    {
                                        touching = true;

                                        break;
                                    }
                                }

                                if (!touching)
                                {
                                    ciEnum = cities.elements();

                                    while (ciEnum.hasMoreElements())
                                    {
                                        SOCCity ci = (SOCCity) ciEnum.nextElement();

                                        if (ci.getCoordinates() == node)
                                        {
                                            touching = true;

                                            break;
                                        }
                                    }

                                    node = hex - 0x01;
                                    seEnum = settlements.elements();

                                    while (seEnum.hasMoreElements())
                                    {
                                        SOCSettlement se = (SOCSettlement) seEnum.nextElement();

                                        if (se.getCoordinates() == node)
                                        {
                                            touching = true;

                                            break;
                                        }
                                    }

                                    if (!touching)
                                    {
                                        ciEnum = cities.elements();

                                        while (ciEnum.hasMoreElements())
                                        {
                                            SOCCity ci = (SOCCity) ciEnum.nextElement();

                                            if (ci.getCoordinates() == node)
                                            {
                                                touching = true;

                                                break;
                                            }
                                        }

                                        node = hex - 0x10;
                                        seEnum = settlements.elements();

                                        while (seEnum.hasMoreElements())
                                        {
                                            SOCSettlement se = (SOCSettlement) seEnum.nextElement();

                                            if (se.getCoordinates() == node)
                                            {
                                                touching = true;

                                                break;
                                            }
                                        }

                                        if (!touching)
                                        {
                                            ciEnum = cities.elements();

                                            while (ciEnum.hasMoreElements())
                                            {
                                                SOCCity ci = (SOCCity) ciEnum.nextElement();

                                                if (ci.getCoordinates() == node)
                                                {
                                                    touching = true;

                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (touching)
            {
                playerList.addElement(players[i]);
            }
        }

        return playerList;
    }

    /**
     * @return a list of possible players to rob
     */
    public Vector getPossibleVictims()
    {
        Vector victims = new Vector();
        Enumeration plEnum = getPlayersOnHex(getBoard().getRobberHex()).elements();

        while (plEnum.hasMoreElements())
        {
            SOCPlayer pl = (SOCPlayer) plEnum.nextElement();
            int pn = pl.getPlayerNumber(); 

            if ((pn != currentPlayerNumber) && (! isSeatVacant(pn)) && (pl.getResources().getTotal() > 0))
            {
                victims.addElement(pl);
            }
        }

        return victims;
    }

    /**
     * the current player has choosen a victim to rob.
     * perform the robbery.  Set gameState back to oldGameState.
     *
     * @param pn  the number of the player being robbed
     * @return the type of resource that was stolen, as in {@link SOCResourceConstants}
     */
    public int stealFromPlayer(int pn)
    {
        /**
         * pick a resource card at random
         */
        SOCPlayer victim = players[pn];
        int[] rsrcs = new int[victim.getResources().getTotal()];
        int cnt = 0;

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            for (int j = 0; j < victim.getResources().getAmount(i); j++)
            {
                rsrcs[cnt] = i;
                cnt++;
            }
        }

        int pick = Math.abs(rand.nextInt() % cnt);

        /**
         * and transfer it to the current player
         */
        victim.getResources().subtract(1, rsrcs[pick]);
        players[currentPlayerNumber].getResources().add(1, rsrcs[pick]);

        /**
         * restore the game state to what it was before the robber moved
         */
        gameState = oldGameState;

        return rsrcs[pick];
    }

    /**
     * Can these two players currently trade?
     * If game option "NT" is set, players can trade only
     * with the bank, not with other players.
     *
     * @return true if the two players can make the trade
     *         described in the offering players current offer
     *
     * @param offering  the number of the player making the offer
     * @param accepting the number of the player accepting the offer
     * @see #canMakeBankTrade(SOCResourceSet, SOCResourceSet)
     */
    public boolean canMakeTrade(int offering, int accepting)
    {
        D.ebugPrintln("*** canMakeTrade ***");
        D.ebugPrintln("*** offering = " + offering);
        D.ebugPrintln("*** accepting = " + accepting);

        if (gameState != PLAY1)
        {
            return false;
        }

        if (isGameOptionSet("NT"))
            return false;

        if (players[offering].getCurrentOffer() == null)
        {
            return false;
        }

        if ((currentPlayerNumber != offering) && (currentPlayerNumber != accepting))
        {
            return false;
        }

        SOCPlayer offeringPlayer = players[offering];
        SOCPlayer acceptingPlayer = players[accepting];
        SOCTradeOffer offer = offeringPlayer.getCurrentOffer();

        D.ebugPrintln("*** offer = " + offer);

        if ((offer.getGiveSet().getTotal() == 0) || (offer.getGetSet().getTotal() == 0))
        {
            return false;
        }

        D.ebugPrintln("*** offeringPlayer.getResources() = " + offeringPlayer.getResources());

        if (!(offeringPlayer.getResources().contains(offer.getGiveSet())))
        {
            return false;
        }

        D.ebugPrintln("*** acceptingPlayer.getResources() = " + acceptingPlayer.getResources());

        if (!(acceptingPlayer.getResources().contains(offer.getGetSet())))
        {
            return false;
        }

        return true;
    }

    /**
     * perform a trade between two players.
     * the trade performed is described in the offering player's
     * current offer.
     *<P>
     * Assumes {@link #canMakeTrade(int, int)} already was called.
     * If game option "NT" is set, players can trade only
     * with the bank, not with other players.
     *
     * @param offering  the number of the player making the offer
     * @param accepting the number of the player accepting the offer
     * @see #makeBankTrade(SOCResourceSet, SOCResourceSet)
     */
    public void makeTrade(int offering, int accepting)
    {
        if (isGameOptionSet("NT"))
            return;

        SOCResourceSet offeringPlayerResources = players[offering].getResources();
        SOCResourceSet acceptingPlayerResources = players[accepting].getResources();
        SOCTradeOffer offer = players[offering].getCurrentOffer();

        offeringPlayerResources.subtract(offer.getGiveSet());
        acceptingPlayerResources.subtract(offer.getGetSet());
        offeringPlayerResources.add(offer.getGetSet());
        acceptingPlayerResources.add(offer.getGiveSet());
    }

    /**
     * @return true if the current player can make a
     *         particular bank/port trade
     *
     * @param  give  what the player will give to the bank
     * @param  get   what the player wants from the bank
     */
    public boolean canMakeBankTrade(SOCResourceSet give, SOCResourceSet get)
    {
        if (gameState != PLAY1)
        {
            return false;
        }

        if ((give.getTotal() < 2) || (get.getTotal() == 0))
        {
            return false;
        }

        if (!(players[currentPlayerNumber].getResources().contains(give)))
        {
            return false;
        }

        int groupCount = 0;
        int ratio = give.getTotal() / get.getTotal();

        switch (ratio)
        {
        /**
         * bank trade
         */
        case 4:

            /**
             * check for groups of 4
             */
            for (int i = SOCResourceConstants.CLAY;
                    i <= SOCResourceConstants.WOOD; i++)
            {
                if ((give.getAmount(i) % 4) == 0)
                {
                    groupCount += (give.getAmount(i) / 4);
                }
                else
                {
                    return false;
                }
            }

            break;

        /**
         * 3:1 port trade
         */
        case 3:

            /**
             * check for groups of 3
             */
            for (int i = SOCResourceConstants.CLAY;
                    i <= SOCResourceConstants.WOOD; i++)
            {
                if ((give.getAmount(i) % 3) == 0)
                {
                    groupCount += (give.getAmount(i) / 3);

                    /**
                     * check if this player has a 3:1 port
                     */
                    if (!(players[currentPlayerNumber].getPortFlag(SOCBoard.MISC_PORT)))
                    {
                        return false;
                    }
                }
                else
                {
                    return false;
                }
            }

            break;

        /**
         * 2:1 port trade
         */
        case 2:

            /**
             * check for groups of 2
             */
            /**
             * Note: this only works if SOCResourceConstants.CLAY == 1
             */
            for (int i = SOCResourceConstants.CLAY;
                    i <= SOCResourceConstants.WOOD; i++)
            {
                if (give.getAmount(i) > 0)
                {
                    if (((give.getAmount(i) % 2) == 0) && (players[currentPlayerNumber].getPortFlag(i)))
                    {
                        groupCount += (give.getAmount(i) / 2);
                    }
                    else
                    {
                        return false;
                    }
                }
            }

            break;
        }

        if (groupCount != get.getTotal())
        {
            return false;
        }

        return true;
    }

    /**
     * perform a bank trade
     *
     * @param give  the number of the player making the offer
     * @param get the number of the player accepting the offer
     */
    public void makeBankTrade(SOCResourceSet give, SOCResourceSet get)
    {
        SOCResourceSet playerResources = players[currentPlayerNumber].getResources();

        playerResources.subtract(give);
        playerResources.add(get);
    }

    /**
     * @return true if the player has the resources, pieces, and
     *         room to build a road
     *
     * @param pn  the number of the player
     */
    public boolean couldBuildRoad(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();

        return ((resources.getAmount(SOCResourceConstants.CLAY) >= 1) && (resources.getAmount(SOCResourceConstants.WOOD) >= 1) && (players[pn].getNumPieces(SOCPlayingPiece.ROAD) >= 1) && (players[pn].hasPotentialRoad()));
    }

    /**
     * @return true if the player has the resources, pieces, and
     *         room to build a settlement
     *
     * @param pn  the number of the player
     */
    public boolean couldBuildSettlement(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();

        return ((resources.getAmount(SOCResourceConstants.CLAY) >= 1) && (resources.getAmount(SOCResourceConstants.SHEEP) >= 1) && (resources.getAmount(SOCResourceConstants.WHEAT) >= 1) && (resources.getAmount(SOCResourceConstants.WOOD) >= 1) && (players[pn].getNumPieces(SOCPlayingPiece.SETTLEMENT) >= 1) && (players[pn].hasPotentialSettlement()));
    }

    /**
     * @return true if the player has the resources, pieces, and
     *         room to build a city
     *
     * @param pn  the number of the player
     */
    public boolean couldBuildCity(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();

        return ((resources.getAmount(SOCResourceConstants.ORE) >= 3) && (resources.getAmount(SOCResourceConstants.WHEAT) >= 2) && (players[pn].getNumPieces(SOCPlayingPiece.CITY) >= 1) && (players[pn].hasPotentialCity()));
    }

    /**
     * @return true if the player has the resources
     *         to buy a dev card, and if there are dev cards
     *         left to buy
     *
     * @param pn  the number of the player
     */
    public boolean couldBuyDevCard(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();

        return ((resources.getAmount(SOCResourceConstants.SHEEP) >= 1) && (resources.getAmount(SOCResourceConstants.ORE) >= 1) && (resources.getAmount(SOCResourceConstants.WHEAT) >= 1) && (numDevCards > 0));
    }

    /**
     * a player is buying a road
     *
     * @param pn  the number of the player
     */
    public void buyRoad(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.subtract(1, SOCResourceConstants.CLAY);
        resources.subtract(1, SOCResourceConstants.WOOD);
        gameState = PLACING_ROAD;
    }

    /**
     * a player is buying a settlement
     *
     * @param pn  the number of the player
     */
    public void buySettlement(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.subtract(1, SOCResourceConstants.CLAY);
        resources.subtract(1, SOCResourceConstants.SHEEP);
        resources.subtract(1, SOCResourceConstants.WHEAT);
        resources.subtract(1, SOCResourceConstants.WOOD);
        gameState = PLACING_SETTLEMENT;
    }

    /**
     * a player is buying a city
     *
     * @param pn  the number of the player
     */
    public void buyCity(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.subtract(3, SOCResourceConstants.ORE);
        resources.subtract(2, SOCResourceConstants.WHEAT);
        gameState = PLACING_CITY;
    }

    /**
     * a player is UNbuying a road; return resources, set gameState PLAY1
     *
     * @param pn  the number of the player
     */
    public void cancelBuildRoad(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.add(1, SOCResourceConstants.CLAY);
        resources.add(1, SOCResourceConstants.WOOD);
        gameState = PLAY1;
    }

    /**
     * a player is UNbuying a settlement; return resources, set gameState PLAY1
     *
     * @param pn  the number of the player
     */
    public void cancelBuildSettlement(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.add(1, SOCResourceConstants.CLAY);
        resources.add(1, SOCResourceConstants.SHEEP);
        resources.add(1, SOCResourceConstants.WHEAT);
        resources.add(1, SOCResourceConstants.WOOD);
        gameState = PLAY1;
    }

    /**
     * a player is UNbuying a city; return resources, set gameState PLAY1
     *
     * @param pn  the number of the player
     */
    public void cancelBuildCity(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.add(3, SOCResourceConstants.ORE);
        resources.add(2, SOCResourceConstants.WHEAT);
        gameState = PLAY1;
    }

    /**
     * the current player is buying a dev card
     *
     * @return the card that was drawn
     */
    public int buyDevCard()
    {
        int card =  devCardDeck[numDevCards - 1];
        numDevCards--;

        SOCResourceSet resources = players[currentPlayerNumber].getResources();
        resources.subtract(1, SOCResourceConstants.ORE);
        resources.subtract(1, SOCResourceConstants.SHEEP);
        resources.subtract(1, SOCResourceConstants.WHEAT);
        players[currentPlayerNumber].getDevCards().add(1, SOCDevCardSet.NEW, card);
        checkForWinner();

        return (card);
    }

    /**
     * @return true if the player can play a knight card
     *
     * @param pn  the number of the player
     */
    public boolean canPlayKnight(int pn)
    {
        if (!((gameState == PLAY) || (gameState == PLAY1)))
        {
            return false;
        }

        if (players[pn].hasPlayedDevCard())
        {
            return false;
        }

        if (players[pn].getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) == 0)
        {
            return false;
        }

        return true;
    }

    /**
     * Can the current player play a Road Building card?
     *<P>
     * This card directs the player to place 2 roads.
     * Checks of game rules online show they "MAY" or "CAN", not "MUST", place 2.
     * If they have 2 or more roads, place 2.
     * If they have just 1 road, place 1.
     * If they have 0 roads, cannot play the card.
     *
     * @return true if the player can play a Road Building card.
     *
     * @param pn  the number of the player
     */
    public boolean canPlayRoadBuilding(int pn)
    {
        if (!((gameState == PLAY) || (gameState == PLAY1)))
        {
            return false;
        }

        if (players[pn].hasPlayedDevCard())
        {
            return false;
        }

        if (players[pn].getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) == 0)
        {
            return false;
        }

        if (players[pn].getNumPieces(SOCPlayingPiece.ROAD) < 1)
        {
            return false;
        }

        return true;
    }

    /**
     * return true if the player can play a Discovery card
     *
     * @param pn  the number of the player
     */
    public boolean canPlayDiscovery(int pn)
    {
        if (!((gameState == PLAY) || (gameState == PLAY1)))
        {
            return false;
        }

        if (players[pn].hasPlayedDevCard())
        {
            return false;
        }

        if (players[pn].getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC) == 0)
        {
            return false;
        }

        return true;
    }

    /**
     * return true if the player can play a Monopoly card
     *
     * @param pn  the number of the player
     */
    public boolean canPlayMonopoly(int pn)
    {
        if (!((gameState == PLAY) || (gameState == PLAY1)))
        {
            return false;
        }

        if (players[pn].hasPlayedDevCard())
        {
            return false;
        }

        if (players[pn].getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO) == 0)
        {
            return false;
        }

        return true;
    }

    /**
     * the current player plays a Knight card
     */
    public void playKnight()
    {
        players[currentPlayerNumber].setPlayedDevCard(true);
        players[currentPlayerNumber].getDevCards().subtract(1, SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT);
        players[currentPlayerNumber].incrementNumKnights();
        updateLargestArmy();
        checkForWinner();
        placingRobberForKnightCard = true;
        oldGameState = gameState;
        gameState = PLACING_ROBBER;
    }

    /**
     * The current player plays a Road Building card.
     * This card directs the player to place 2 roads.
     * Checks of game rules online show "MAY" or "CAN", not "MUST" place 2.
     * If they have 2 or more roads, may place 2; gameState becomes PLACING_FREE_ROAD1.
     * If they have just 1 road, may place that; gameState becomes PLACING_FREE_ROAD2.
     * If they have 0 roads, cannot play the card.
     * Assumes {@link #canPlayRoadBuilding(int)} has already been called, and move is valid.
     */
    public void playRoadBuilding()
    {
        players[currentPlayerNumber].setPlayedDevCard(true);
        players[currentPlayerNumber].getDevCards().subtract(1, SOCDevCardSet.OLD, SOCDevCardConstants.ROADS);
        oldGameState = gameState;
        if (players[currentPlayerNumber].getNumPieces(SOCPlayingPiece.ROAD) > 1)
        {
            gameState = PLACING_FREE_ROAD1;  // First of 2 free roads
        } else {
            gameState = PLACING_FREE_ROAD2;  // "Second", just 1 free road
        }
    }

    /**
     * the current player plays a Discovery card
     */
    public void playDiscovery()
    {
        players[currentPlayerNumber].setPlayedDevCard(true);
        players[currentPlayerNumber].getDevCards().subtract(1, SOCDevCardSet.OLD, SOCDevCardConstants.DISC);
        oldGameState = gameState;
        gameState = WAITING_FOR_DISCOVERY;
    }

    /**
     * the current player plays a monopoly card
     */
    public void playMonopoly()
    {
        players[currentPlayerNumber].setPlayedDevCard(true);
        players[currentPlayerNumber].getDevCards().subtract(1, SOCDevCardSet.OLD, SOCDevCardConstants.MONO);
        oldGameState = gameState;
        gameState = WAITING_FOR_MONOPOLY;
    }

    /**
     * @return true if the current player can
     *         do the discovery card action and the
     *         pick contains exactly 2 resources
     *
     * @param pick  the resources that the player wants
     */
    public boolean canDoDiscoveryAction(SOCResourceSet pick)
    {
        if (gameState != WAITING_FOR_DISCOVERY)
        {
            return false;
        }

        if (pick.getTotal() != 2)
        {
            return false;
        }

        return true;
    }

    /**
     * @return true if the current player can do
     *         the Monopoly card action
     */
    public boolean canDoMonopolyAction()
    {
        if (gameState != WAITING_FOR_MONOPOLY)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    /**
     * perform the Discovery card action
     *
     * @param pick  what the player picked
     */
    public void doDiscoveryAction(SOCResourceSet pick)
    {
        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            players[currentPlayerNumber].getResources().add(pick.getAmount(i), i);
        }

        gameState = oldGameState;
    }

    /**
     * perform the Monopoly card action.
     * Resources are taken from players that have it.
     * Game state becomes oldGameState (returns to state before monopoly pick).
     *
     * @param rtype  the type of resource to monopolize
     * @return array (1 elem per player) of resource count taken from
     *        each player. 0 for players with nothing taken.
     *        0 for the current player (playing the monopoly card).
     */
    public int[] doMonopolyAction(final int rtype)
    {
        int sum = 0;
        int[] monoResult = new int[MAXPLAYERS];

        for (int i = 0; i < MAXPLAYERS; i++)
        {
            if ((i != currentPlayerNumber) && ! isSeatVacant(i))
            {
                int playerHas = players[i].getResources().getAmount(rtype);
                if (playerHas > 0)
                {
                    sum += playerHas;
                    players[i].getResources().setAmount(0, rtype);
                }
                monoResult[i] = playerHas;
            } else {
                monoResult[i] = 0;
            }
        }

        players[currentPlayerNumber].getResources().add(sum, rtype);
        gameState = oldGameState;
        return monoResult;
    }

    /**
     * update which player has the largest army
     * larger than 2
     */
    public void updateLargestArmy()
    {
        int size;

        if (playerWithLargestArmy == -1)
        {
            size = 2;
        }
        else
        {
            size = players[playerWithLargestArmy].getNumKnights();
        }

        for (int i = 0; i < MAXPLAYERS; i++)
        {
            if (players[i].getNumKnights() > size)
            {
                playerWithLargestArmy = i;
            }
        }
    }

    /**
     * save the state of who has largest army
     */
    public void saveLargestArmyState()
    {
        oldPlayerWithLargestArmy = playerWithLargestArmy;
    }

    /**
     * restore the state of who had largest army
     */
    public void restoreLargestArmyState()
    {
        playerWithLargestArmy = oldPlayerWithLargestArmy;
    }

    /**
     * update which player has longest road longer
     * than 4.
     *
     * this version recalculates the longest road only for
     * the player who is affected by the most recently
     * placed piece, by calling their {@link SOCPlayer#calcLongestRoad2()}.
     * Assumes all other players' longest road has been updated already.
     * All players' {@link SOCPlayer#getLongestRoadLength()} is called here.
     *<P>
     * if there is a tie, the last player to have LR keeps it.
     * if two or more players are tied for LR and none of them
     * used to have LR, then no one has LR.
     *
     * @param pn  the number of the player who is affected
     */
    public void updateLongestRoad(int pn)
    {
        //D.ebugPrintln("## updateLongestRoad("+pn+")");
        int longestLength;
        int playerLength;
        int tmpPlayerWithLR = -1;

        players[pn].calcLongestRoad2();
        longestLength = 0;

        for (int i = 0; i < MAXPLAYERS; i++)
        {
            playerLength = players[i].getLongestRoadLength();

            //D.ebugPrintln("----- LR length for player "+i+" is "+playerLength);
            if (playerLength > longestLength)
            {
                longestLength = playerLength;
                tmpPlayerWithLR = i;
            }
        }

        if (longestLength < 5)  // Minimum length is 5 for the bonus
        {
            playerWithLongestRoad = -1;
        }
        else
        {
            ///
            /// if there is a tie, the last player to have LR keeps it.
            /// if two or more players are tied for LR and none of them
            /// of them used to have LR, then no one has LR.
            ///
            int playersWithLR = 0;

            for (int i = 0; i < MAXPLAYERS; i++)
            {
                if (players[i].getLongestRoadLength() == longestLength)
                {
                    playersWithLR++;
                }
            }

            if (playersWithLR == 1)
            {
                playerWithLongestRoad = tmpPlayerWithLR;
            }
            else if ((playerWithLongestRoad == -1) || (players[playerWithLongestRoad].getLongestRoadLength() != longestLength))
            {
                playerWithLongestRoad = -1;
            }
        }

        //D.ebugPrintln("----- player "+playerWithLongestRoad+" has LR");
    }

    /**
     * check current player's vp total to see if the
     * game is over.  Set game state to OVER,
     * set player with win.
     *<P>
     * Per rules FAQ, a player can win only during their own turn.
     * If a player reaches winning points (VP_WINNER or more) but it's
     * not their turn, there is not yet a winner. This could happen if,
     * for example, the longest road is broken by a new settlement, and
     * the next-longest road is not the current player's road.
     *<P>
     * The win is determined not by who has the highest point total, but
     * solely by reaching 10 victory points (VP_WINNER) during your own turn.
     *
     * @see #getGameState()
     * @see #getPlayerWithWin()
     */
    public void checkForWinner()
    {
        int pn = currentPlayerNumber;
        if ((pn >= 0) && (pn < MAXPLAYERS)
            && (players[pn].getTotalVP() >= VP_WINNER))
        {
            gameState = OVER;
            playerWithWin = pn;
            System.err.println("DEBUG: Set playerWithWin = " + pn
                + " -- in thread: " + Thread.currentThread().getName() + " --");

            // JM temp; will turn that debug-print off later.
            //    Also displayed a locator stacktrace, from 2008-06-15 until 1.1.06 (removed 2009-05-29).
        }
    }

    /**
     * set vars to null so gc can clean up
     */
    public void destroyGame()
    {
        for (int i = 0; i < MAXPLAYERS; i++)
        {
            if (players[i] != null)
            {
                players[i].destroyPlayer();
                players[i] = null;
            }
        }

        players = null;
        board = null;
        rand = null;
    }

    /**
     * Create a new game with same players and name, new board;
     * like calling constructor otherwise.
     * State of current game can be any state. State of copy is {@link #NEW},
     * although if there are robots, it will be set to {@link #READY_RESET_WAIT_ROBOT_DISMISS}
     * by the {@link SOCGameBoardReset} constructor.
     * Deep copy: Player names, faceIDs, and robot-flag are copied from
     * old game, but all other fields set as new Player and Board objects.
     * Robot players are NOT carried over, and must be asked to re-join.
     * (This simplifies the robot client.)
     * Any vacant seats will be locked, so a robot won't sit there.
     *<P>
     * Old game's state becomes {@link #RESET_OLD}.
     * Old game's previous state is saved to {@link #getResetOldGameState()}.
     * Please call destroyGame() on old game when done examining its state.
     *<P>
     * Assumes that if the game had more than one human player,
     * they've already voted interactively to reset the board.
     * @see #resetVoteBegin(int)
     */
    public SOCGame resetAsCopy()
    {
        SOCGame cp = new SOCGame(name, active);

        cp.isFromBoardReset = true;
        oldGameState = gameState;  // for getResetOldGameState()
        active = false;
        gameState = RESET_OLD;

        // Most fields are NOT copied since this is a "reset", not an identical-state game.

        // Game options
        cp.opts = SOCGameOption.cloneOptions(opts);
        cp.clientVersionMinRequired = clientVersionMinRequired;

        // Per-player state
        for (int i = 0; i < MAXPLAYERS; i++)
        {
            boolean wasRobot = false;
            if (players[i] != null)
            {
                wasRobot = players[i].isRobot();
                if (! wasRobot)
                {
                    cp.addPlayer(players[i].getName(), i);
                    cp.players[i].setRobotFlag (false);
                    cp.players[i].setFaceId(players[i].getFaceId());
                }
            }
            cp.seatLocks[i] = seatLocks[i];
            if (wasRobot)
                cp.seats[i] = VACANT;
            else
            {
                cp.seats[i] = seats[i];  // reset in case addPlayer cleared VACANT for non-in-use player position
                if (cp.seats[i] == VACANT)
                    cp.seatLocks[i] = true;
            }
        }

        return cp;
    }

    /**
     * Begin a board-reset vote.
     * The requester is marked as voting yes, and we mark other players as "no vote yet".
     * Wait for other human players to vote.
     *
     * @param reqPN Player number requesting the vote
     * @throws IllegalArgumentException If this player number has already
     *     requested a reset this turn
     * @throws IllegalStateException If there is alread a vote in progress 
     *
     * @see #getResetVoteRequester()
     * @see #resetVoteRegister(int, boolean)
     * @see #getResetVoteResult()
     */
    public void resetVoteBegin(int reqPN) throws IllegalArgumentException, IllegalStateException
    {
        if (players[reqPN].hasAskedBoardReset())
            throw new IllegalArgumentException("Player has already asked to reset this turn");

        int numVoters = 0;
        synchronized (boardResetVotes)
        {
             if (boardResetVoteRequester != -1)
                 throw new IllegalStateException("Already voting");
             boardResetVoteRequester = reqPN;
             for (int i = 0; i < MAXPLAYERS; ++i)
             {
                 if (i != reqPN)
                 {
                     boardResetVotes[i] = VOTE_NONE;
                     if (! (isSeatVacant(i) || players[i].isRobot()))
                         ++numVoters;
                 }
                 else
                 {
                     // Requester doesn't count as a voter we're waiting for,
                     // but is easier for other code if assume they voted yes.
                     boardResetVotes[i] = VOTE_YES;
                 }
             }
             boardResetVotesWaiting = numVoters;
        }

        if (gameState >= PLAY)
        {
            players[reqPN].setAskedBoardReset(true);
            // During game setup (START1A..START2B), normal end-of-turn flags aren't
            // cleared.  Easiest to not set this one during those states.
        }
    }

    /**
     * If a board reset vote is active, player number who requested the vote.
     * All human players must vote unanimously, or board reset is rejected.
     * -1 if no vote is active.
     * After the vote completes, this is set to -1 if the vote was rejected,
     * but retains the requester number if the vote succeeded and the board
     * will soon be reset.
     *
     * @return player number who requested the vote.
     *
     * @see #resetVoteBegin(int)
     */
    public int getResetVoteRequester()
    {
        synchronized (boardResetVotes)
        {
            return boardResetVoteRequester;
        }
    }

    /**
     * @return if a board-reset vote is active (waiting for votes).
     */
    public boolean getResetVoteActive()
    {
        synchronized (boardResetVotes)
        {
            return (boardResetVotesWaiting > 0);
        }
    }

    /**
     * Register this player's vote in a board reset request.
     *
     * @param pn  Player number
     * @param votingYes Are they voting yes, or no?
     * @return True if voting is now complete, false if still waiting for other votes
     * @throws IllegalArgumentException If pn already voted, or can't vote (vacant or robot).
     * @throws IllegalStateException    If voting is not currently active.
     * @see #getResetPlayerVote(int)
     */
    public boolean resetVoteRegister(int pn, boolean votingYes)
        throws IllegalArgumentException, IllegalStateException
    {
        boolean vcomplete;
        synchronized (boardResetVotes)
        {
            if (boardResetVotes[pn] != VOTE_NONE)
                throw new IllegalArgumentException("Already voted: " + pn);
            if (isSeatVacant(pn) || players[pn].isRobot())
                throw new IllegalArgumentException("Seat cannot vote: " + pn);
            if ((0 == boardResetVotesWaiting) || (-1 == boardResetVoteRequester))
                throw new IllegalStateException("Voting is not active");
            if (votingYes)
                boardResetVotes[pn] = VOTE_YES;
            else
                boardResetVotes[pn] = VOTE_NO;
            --boardResetVotesWaiting;
            vcomplete = (0 == boardResetVotesWaiting);
            if (vcomplete)
            {
                if (! getResetVoteResult())
                    boardResetVoteRequester = -1;  // Board Reset rejected; clear requester.
            }
        }
        return vcomplete;
    }

    /**
     * Get this player's vote on a board reset request.
     *
     * @param pn  Player number
     * @return Vote value for player: {@link #VOTE_YES}, {@link #VOTE_NO},
     *    or if player hasn't yet voted, {@link #VOTE_NONE}.
     * @see #resetVoteRegister(int, boolean)
     * @see #getResetVoteResult()
     */
    public int getResetPlayerVote(int pn)
    {
        synchronized (boardResetVotes)
        {
            return boardResetVotes[pn];
        }
    }

    /**
     * At end of turn, clear flags for board reset voting:
     * requester, players' setAskedBoardReset.
     * This is outside of {@link #endTurn()} because
     * endTurn is called only at the server, not at clients.
     * Do not call this to cancel a vote during normal gameplay, because
     * it would allow players to ask for a reset more than once per turn.
     */
    public void resetVoteClear()
    {
        if (boardResetVoteRequester != -1)
            boardResetVoteRequester = -1;
        synchronized (boardResetVotes)
        {
            boardResetVotesWaiting = 0;
            for (int i = 0; i < MAXPLAYERS; ++i)
                players[i].setAskedBoardReset(false);
        }
    }

    /**
     * If a board-reset vote is complete, give its result.
     * All human players must vote unanimously, or board reset is rejected.
     *
     * @return True if accepted, false if rejected.
     * @throws IllegalStateException if voting is still active. See {@link #getResetVoteActive()}.
     * @see #getResetPlayerVote(int)
     */
    public boolean getResetVoteResult() throws IllegalStateException
    {
        boolean vyes;
        synchronized (boardResetVotes)
        {
            if (boardResetVotesWaiting > 0)
                throw new IllegalStateException("Voting is still active");

            vyes = true;  // Assume no "no" votes
            for (int i = 0; i < MAXPLAYERS; ++i)
                if (boardResetVotes[i] == VOTE_NO)
                {
                    vyes = false;
                    break;
                }
        }
        return vyes;  
    }

}
