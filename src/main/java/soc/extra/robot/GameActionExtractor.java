/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2021 Jeremy D Monin <jeremy@nand.net>
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

package soc.extra.robot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import soc.extra.robot.GameActionLog.Action;
import soc.extra.robot.GameActionLog.Action.ActionType;
import soc.extra.server.GameEventLog;
import soc.extra.server.GameEventLog.QueueEntry;
import soc.game.SOCBoardLarge;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.message.*;

/**
 * Extract basic higher-level actions in a game, from its message sequences
 * in a {@link GameEventLog}, to a {@link GameActionLog}.
 *<P>
 * Can be used by bots or any other code which wants to examine a game's logs and actions.
 *<P>
 * For more details and the message analysis on which this is based,
 * see {@code /doc/extra/GameActionExtractor.md}.
 * For sample code which uses it, see {@code soctest.robot.TestGameActionExtractor}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.5.00
 */
public class GameActionExtractor
{
    /**
     * All {@link SOCMessage} types which start a possible sequence in the decision tree top level,
     * as documented in {@code GameActionExtractor.md}.
     */
    protected static final Set<Integer> SEQ_START_MSG_TYPES = new HashSet<>();
    static {
        for (int msgtype : new int[]
            {
                SOCMessage.TURN, SOCMessage.ROLLDICE, SOCMessage.PUTPIECE, SOCMessage.BUILDREQUEST,
                SOCMessage.MOVEPIECE, SOCMessage.BUYDEVCARDREQUEST, SOCMessage.PLAYDEVCARDREQUEST,
                SOCMessage.DISCARD, SOCMessage.PICKRESOURCES, SOCMessage.CHOOSEPLAYER, SOCMessage.MOVEROBBER,
                SOCMessage.CHOOSEPLAYERREQUEST, SOCMessage.REPORTROBBERY,
                SOCMessage.BANKTRADE, SOCMessage.MAKEOFFER, SOCMessage.REJECTOFFER, SOCMessage.ACCEPTOFFER,
                SOCMessage.ENDTURN, SOCMessage.GAMEELEMENTS
            })
            SEQ_START_MSG_TYPES.add(msgtype);
    }

    /**
     * All {@link SOCMessage} types which can be ignored while reading through an event log.
     */
    protected static final Set<Integer> IGNORE_MSG_TYPES = new HashSet<>();
    static {
        for (int msgtype : new int[]
            {
                SOCMessage.GAMETEXTMSG, SOCMessage.GAMESERVERTEXT, SOCMessage.CHANGEFACE
            })
            IGNORE_MSG_TYPES.add(msgtype);
    }

    protected final GameEventLog eventLog;
    protected final GameActionLog actLog;
    protected final ExtractorState state = new ExtractorState();

    /**
     * Current sequence to gather all entries into during {@link #next()}, or {@code null}.
     * @see #resetCurrentSequence()
     */
    protected ArrayList<GameEventLog.QueueEntry> currentSequence;

    /**
     * Index within {@link #eventLog} of start of current sequence:
     * The value of {@link ExtractorState#nextLogIndex} when {@link #currentSequence} was set to an empty List
     * by {@link #resetCurrentSequence()}.
     */
    protected int currentSequenceStartIndex;

    /**
     * Create a new {@link GameActionExtractor} for a {@link GameEventLog}.
     * Checks log's required initial events for consistency,
     * fast-forwards past {@link SOCStartGame} if found, otherwise to end of log.
     * Once ready to parse the rest, call {@link #extract()}.
     *
     * @param eventLog  Log to recognize and extract {@link GameActionLog.Action}s from event sequences
     * @param keepEntriesBeforeInitPlacement  If true, keep the {@link GameEventLog} entries from start of log,
     * up to and including {@link SOCStartGame}, instead of skipping them.
     * Adds them to a {@link GameActionLog.Action} of type
     * {@link GameActionLog.Action.ActionType#LOG_START_TO_STARTGAME LOG_START_TO_STARTGAME}.
     *
     * @throws IllegalArgumentException if {@code eventLog} is null or empty
     * @throws NoSuchElementException if {@code eventLog} doesn't start with {@link SOCVersion}
     *     followed by {@link SOCNewGame} or {@link SOCNewGameWithOptions}
     * @throws IllegalStateException if {@code eventLog} doesn't contain a {@link SOCStartGame}
     */
    public GameActionExtractor
        (final GameEventLog eventLog, final boolean keepEntriesBeforeInitPlacement)
        throws IllegalArgumentException, NoSuchElementException, IllegalStateException
    {
        if ((eventLog == null) || eventLog.entries.isEmpty())
            throw new IllegalArgumentException("eventLog");

        this.eventLog = eventLog;
        actLog = new GameActionLog();

        if (keepEntriesBeforeInitPlacement)
            currentSequence = new ArrayList<>(70);  // approx. message count before startgame for a 2-player game

        GameEventLog.QueueEntry e = next();
        if ((e == null) || ! (e.event instanceof SOCVersion))
            throw new NoSuchElementException("expected SOCVersion");

        e = next();
        if ((e == null)
            || ! ((e.event instanceof SOCNewGame) || (e.event instanceof SOCNewGameWithOptions)))
            throw new NoSuchElementException("expected SOCNewGame or SOCNewGameWithOptions");

        // Skip messages before init placement: Seek forward until see all:SOCStartGame
        //    which should be followed by ignorable text and changeface, then "start of turn" sequence
        while (true)
        {
            e = next();
            if (e == null)
                throw new IllegalStateException("reached end of log, found no SOCStartGame");

            if ((e.event instanceof SOCStartGame) && e.isToAll())
            {
                // found the SOCStartGame we're looking for.
                // Next message should be "start of turn" sequence for initial placement.

                if (keepEntriesBeforeInitPlacement)
                    actLog.add(new Action
                        (ActionType.LOG_START_TO_STARTGAME, state.currentGameState, resetCurrentSequence(), 0));

                break;
            }
        }
    }

    /**
     * Read the next event entry: Starting at current log position {@link ExtractorState#nextLogIndex},
     * look for an entry that has a {@link SOCMessage} as its {@link GameEventLog.QueueEntry#event}
     * whose type isn't in {@link #IGNORE_MSG_TYPES}.
     *<P>
     * If {@link #currentSequence} != null, will add any ignored entries and the returned entry to it.
     *<P>
     * Updates {@link ExtractorState#currentGameState} from {@link SOCGameState}, {@link SOCTurn},
     * and {@link SOCStartGame}.
     *<P>
     * Updates {@link ExtractorState#currentPlayerNumber} from {@link SOCTurn}
     * and {@link SOCGameElements}({@link SOCGameElements.GEType#CURRENT_PLAYER CURRENT_PLAYER}).
     *
     * @return next non-ignored event log message, or {@code null} if reached end of {@link #eventLog}.
     *     If not {@code null}, its {@code event} field won't be null either.
     * @see #nextIfType(int)
     * @see #nextIfGamestateOrOver()
     * @see #backtrackTo(ExtractorState)
     * @see #resetCurrentSequence()
     */
    protected GameEventLog.QueueEntry next()
    {
        final int size = eventLog.entries.size();

        while (state.nextLogIndex < size)
        {
            final GameEventLog.QueueEntry e = eventLog.entries.get(state.nextLogIndex);
            ++state.nextLogIndex;

            if (currentSequence != null)
                currentSequence.add(e);

            if (e.event != null)
            {
                if (e.event instanceof SOCGameState)
                    state.currentGameState = ((SOCGameState) e.event).getState();
                else if (e.event instanceof SOCTurn)
                {
                    state.currentPlayerNumber = ((SOCTurn) e.event).getPlayerNumber();
                    state.currentGameState = ((SOCTurn) e.event).getGameState();
                }
                else if (e.event instanceof SOCGameElements)
                {
                    final SOCGameElements ge = (SOCGameElements) e.event;
                    final int eTypes[] = ge.getElementTypes();
                    for (int i = 0; i < eTypes.length; ++i)
                        if (eTypes[i] == SOCGameElements.GEType.CURRENT_PLAYER.getValue())
                        {
                            state.currentPlayerNumber = ge.getValues()[i];
                            break;
                        }
                }
                else if (e.event instanceof SOCStartGame)
                    state.currentGameState = ((SOCStartGame) e.event).getGameState();

                if (! (IGNORE_MSG_TYPES.contains(Integer.valueOf(e.event.getType()))))
                    return e;
            }
        }

        // reached end of log
        return null;
    }

    /**
     * Read the next event in our {@link GameEventLog} if it's this {@code msgType}.
     * If not found, backtrack as if {@link #next()} was never called.
     * @param msgType  Message type expected to be found, from {@link SOCMessage#getType()}
     * @return  Event log entry of expected type, or {@code null} if end of log or next entry was another type
     * @see #nextIfGamestateOrOver()
     */
    protected GameEventLog.QueueEntry nextIfType(final int msgType)
    {
        final ExtractorState presentState = new ExtractorState(state);

        GameEventLog.QueueEntry e = next();
        if ((e == null) || (e.event.getType() != msgType))
        {
            backtrackTo(presentState);
            return null;
        } else {
            return e;
        }
    }

    /**
     * Read the next event in our {@link GameEventLog} if it's a {@link SOCGameState}
     * as expected, or if it's the pair of messages indicating game over state:
     * <tt>all:{@link SOCGameElements}({@link SOCGameElements.GEType#CURRENT_PLAYER CURRENT_PLAYER})</tt>
     * followed by <tt>all:{@link SOCGameState}({@link SOCGame#OVER})</tt>.
     *<P>
     * If found, adds all to {@link #currentSequence} as usual and returns the {@link SOCGameState} message;
     * if game is now over, {@link ExtractorState#currentGameState} will be {@link SOCGame#OVER}
     * and {@link ExtractorState#currentPlayerNumber} will be updated to the new value
     * seen in {@link SOCGameElements}.
     *<P>
     * If a game state or game over pair isn't found, will not add anything to {@link #currentSequence};
     * will backtrack and return null.
     *
     * @return {@link SOCGameState} entry or {@code null}
     * @see #nextIfType(int)
     */
    protected GameEventLog.QueueEntry nextIfGamestateOrOver()
    {
        final ExtractorState presentState = new ExtractorState(state);
        boolean sawGE = false;

        // if game is now over:
        // all:SOCGameElements:game=test|e4=3  // CURRENT_PLAYER
        GameEventLog.QueueEntry e = next();
        if (e == null)
        {
            backtrackTo(presentState);
            return null;
        }
        if (e.event instanceof SOCGameElements)
        {
            // look for curr_player only
            SOCGameElements ge = (SOCGameElements) e.event;
            int[] et = ge.getElementTypes();
            if ((et.length != 1) || (et[0] != SOCGameElements.GEType.CURRENT_PLAYER.getValue())
                || ! e.isToAll())
                return null;

            sawGE = true;
            state.currentPlayerNumber = ge.getValues()[0];

            e = next();
        }

        // all:SOCGameState:game=test|state=1000 // OVER
        // or another state if game not over
        if ((e == null) || (! (e.isToAll() && (e.event instanceof SOCGameState)))
            || (sawGE && (state.currentGameState != SOCGame.OVER)))
        {
            backtrackTo(presentState);
            return null;
        }

        return e;
    }

    /**
     * Backtrack to a known previous position in our {@link GameEventLog},
     * probably after finding the {@link #next()} entry wasn't as expected.
     * @param toState  Extractor state to backtrack to,
     *     from copy constructor before a previous call to {@code next()}
     * @throws IllegalArgumentException if {@code toState}'is {@code null}
     *     or is the sequencer's own {@link #state} object instead of a copy
     * @throws IllegalStateException if {@code toState}'s {@code toLogIndex} or {@code toSeqSize} &gt; current value
     */
    protected void backtrackTo(final ExtractorState toState)
        throws IllegalArgumentException, IllegalStateException
    {
        if ((toState == null) || (toState == state))
            throw new IllegalArgumentException("toState");
        if (toState.nextLogIndex > state.nextLogIndex)
            throw new IllegalStateException("toLogIndex=" + toState.nextLogIndex + " > current " + state.nextLogIndex);
        int currSize = currentSequence.size();
        if (toState.currentSequenceSize > currSize)
            throw new IllegalStateException("toSeqSize=" + toState.currentSequenceSize + " > current " + currSize);

        state.nextLogIndex = toState.nextLogIndex;
        final int toSeqSize = toState.currentSequenceSize;
        for (; currSize > toSeqSize; currSize = currentSequence.size())
            currentSequence.remove(currSize - 1);
        state.currentPlayerNumber = toState.currentPlayerNumber;
        state.currentGameState = toState.currentGameState;
    }

    /**
     * Creates a new empty {@link #currentSequence} and updates related fields.
     * Call this after extracting an {@link Action}, to prepare for the next one.
     * Note current value of {@link #currentSequenceStartIndex} before calling this;
     * it will be updated here to {@link ExtractorState#nextLogIndex}.
     * @return previous {@link #currentSequence}, or {@code null} if none
     */
    protected List<QueueEntry> resetCurrentSequence()
    {
        final List<QueueEntry> prev = currentSequence;

        currentSequence = new ArrayList<>();
        currentSequenceStartIndex = state.nextLogIndex;

        return prev;
    }

    /**
     * Main loop of this class: Finds starts of each sequence, tries to recognize and extract them.
     * Goes until end of our {@link GameEventLog}.
     *<P>
     * Assumes constructor has checked contents of start of log
     * and skipped to just past {@link SOCStartGame}.
     *
     * @return the fully extracted {@link GameActionLog}; not null
     */
    public GameActionLog extract()
    {
        for (;;)
        {
            // Invariant: Just finished extracting the previous message sequence.
            // nextLogIndex points at what's hopefully the start of the next message sequence.
            // currentSequence is empty, currentSequenceStartIndex == nextLogIndex.

            GameEventLog.QueueEntry e = next();
            if (e == null)
                break;

            Action extractedAct = null;  // will set non-null if anything recognized

            SOCMessage event = e.event;
            int eventType = event.getType();
            if (SEQ_START_MSG_TYPES.contains(eventType))
            {
                // Events here are mostly in same order as in Message-Sequences-for-Game-Actions.md

                switch (eventType)
                {
                case SOCMessage.TURN:
                    extractedAct = extract_TURN_BEGINS(e);
                    break;

                case SOCMessage.ROLLDICE:
                    extractedAct = extract_ROLL_DICE(e);
                    break;

                case SOCMessage.PUTPIECE:
                    if (e.isFromClient && (e.pn == state.currentPlayerNumber))
                        extractedAct = extract_BUILD_PIECE(e, false);
                    break;

                case SOCMessage.BUILDREQUEST:
                    if (e.isFromClient)
                    {
                        if (e.pn == state.currentPlayerNumber)
                            extractedAct = extract_BUILD_PIECE(e, true);
                        else if (e.pn >= 0)
                            extractedAct = extract_ASK_SPECIAL_BUILDING(e);
                    }
                    break;

                case SOCMessage.MOVEPIECE:
                    extractedAct = extract_MOVE_PIECE(e);
                    break;

                case SOCMessage.BUYDEVCARDREQUEST:
                    extractedAct = extract_BUY_DEV_CARD(e);
                    break;

                case SOCMessage.PLAYDEVCARDREQUEST:
                    extractedAct = extract_PLAY_DEV_CARD(e);
                    break;

                case SOCMessage.DISCARD:
                    extractedAct = extract_DISCARD(e);
                    break;

                case SOCMessage.PICKRESOURCES:
                    extractedAct = extract_CHOOSE_FREE_RESOURCES(e);
                    break;

                case SOCMessage.CHOOSEPLAYER:
                    if (state.currentGameState == SOCGame.WAITING_FOR_ROBBER_OR_PIRATE)
                        extractedAct = extract_CHOOSE_MOVE_ROBBER_PIRATE(e);
                    else if (state.currentGameState == SOCGame.WAITING_FOR_ROB_CLOTH_OR_RESOURCE)
                        extractedAct = extract_CHOOSE_ROB_CLOTH_OR_RESOURCE(e);
                    break;

                case SOCMessage.MOVEROBBER:
                    extractedAct = extract_MOVE_ROBBER_OR_PIRATE(e);
                    break;

                case SOCMessage.CHOOSEPLAYERREQUEST:
                    if (state.currentGameState == SOCGame.WAITING_FOR_ROB_CHOOSE_PLAYER)
                        extractedAct = extract_CHOOSE_ROBBERY_VICTIM(e);
                    break;

                case SOCMessage.REPORTROBBERY:
                    extractedAct = extract_ROB_PLAYER(e);
                    break;

                case SOCMessage.BANKTRADE:
                    extractedAct = extract_TRADE_BANK(e);
                    break;

                case SOCMessage.MAKEOFFER:
                    extractedAct = extract_TRADE_MAKE_OFFER(e);
                    break;

                case SOCMessage.REJECTOFFER:
                    extractedAct = extract_TRADE_REJECT_OFFER(e);
                    break;

                case SOCMessage.ACCEPTOFFER:
                    extractedAct = extract_TRADE_ACCEPT_OFFER(e);
                    break;

                case SOCMessage.ENDTURN:
                    extractedAct = extract_END_TURN(e);
                    break;

                case SOCMessage.GAMEELEMENTS:
                    extractedAct = extract_GAME_OVER(e);
                    break;

                default:
                    System.err.println
                        ("Internal error: message type " + eventType + " not handled, but is in SEQ_START_MSG_TYPES");
                }
            }

            if (extractedAct != null)
            {
                actLog.add(extractedAct);
            } else {
                // keep looking until we find a sequence-starting message or end of log;
                // gather events from here to then into an UNKNOWN "action"

                ExtractorState presentState = new ExtractorState(state);
                for (;;)
                {
                    e = next();
                    if (e == null)
                        break;

                    event = e.event;
                    if (SEQ_START_MSG_TYPES.contains(event.getType()))
                    {
                        // don't include this sequence-starting message in the UNKNOWN "sequence"
                        backtrackTo(presentState);
                        break;
                    } else {
                        presentState.snapshotFrom(state);
                    }
                }

                int prevStart = currentSequenceStartIndex;
                actLog.add(new Action
                    (ActionType.UNKNOWN, state.currentGameState, resetCurrentSequence(), prevStart));

                if (e == null)
                    break;
            }
        }

        return actLog;
    }

    // Extraction methods, in roughly same order as in /doc/Message-Sequences-for-Game-Actions.md:

    /**
     * Extract {@link ActionType#TURN_BEGINS} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_TURN_BEGINS(GameEventLog.QueueEntry e)
    {
        // all:SOCTurn:game=test|playerNumber=2|gameState=15  // or 100 (SPECIAL_BUILDING)
        if (! e.isToAll())
            return null;
        // next() has set state.currentPlayerNumber, currentGameState from SOCTurn's fields

        // Optional, if not SBP: all:SOCRollDicePrompt:game=test|playerNumber=2
        if (state.currentGameState != SOCGame.SPECIAL_BUILDING)
            nextIfType(SOCMessage.ROLLDICEPROMPT);

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.TURN_BEGINS, state.currentGameState, resetCurrentSequence(), prevStart,
             state.currentPlayerNumber, 0, 0);
    }

    /**
     * Extract {@link ActionType#ROLL_DICE} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_ROLL_DICE(GameEventLog.QueueEntry e)
    {
        // f3:SOCRollDice:game=test
        if (! (e.isFromClient && (e.pn == state.currentPlayerNumber)))
            return null;

        // all:SOCDiceResult:game=test|param=9
        e = next();
        if (! (e.isToAll() && (e.event instanceof SOCDiceResult)))
            return null;
        final int diceTotal = ((SOCDiceResult) e.event).getResult();

        // Sometimes other messages: SOCDiceResultResources, SOCPlayerElements, etc
        do
        {
            e = next();
            if (e == null)
                return null;
        } while (! (e.event instanceof SOCGameState));

        // all:SOCGameState:game=test|state=20  // or another state
        if (! e.isToAll())
            return null;

        // More messages occasionally follow, so gather until another sequence-starting message is seen
        ExtractorState presentState = new ExtractorState(state);
        for (;;)
        {
            e = next();
            if (e == null)
                break;

            if (SEQ_START_MSG_TYPES.contains(e.event.getType()))
            {
                // don't include this sequence-starting message in the ROLL_DICE sequence
                backtrackTo(presentState);
                break;
            } else {
                presentState.snapshotFrom(state);
            }
        }

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.ROLL_DICE, state.currentGameState, resetCurrentSequence(), prevStart,
             diceTotal, 0, 0);
    }

    /**
     * Extract {@link ActionType#BUILD_PIECE} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @param startsWithBuildReq  True if first entry is {@link SOCBuildRequest} instead of {@link SOCPutPiece}
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_BUILD_PIECE(GameEventLog.QueueEntry e, final boolean startsWithBuildReq)
    {
        // Either
        // f3:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=804
        // or
        // f3:SOCBuildRequest:game=test|pieceType=1
        if (! (e.isFromClient && (e.pn == state.currentPlayerNumber)))
            return null;

        final boolean isInitPlacement = (state.currentGameState < SOCGame.ROLL_OR_CARD);

        // Except during initial placement:
        // all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e1=1,e3=1,e4=1,e5=1 (elems vary by type)
        if (! isInitPlacement)
        {
            e = next();
            if ((e == null)
                || ! (e.isToAll() && (e.event instanceof SOCPlayerElements)
                      && (((SOCPlayerElements) e.event).getAction() == SOCPlayerElement.LOSE)))
                return null;
        }

        if (startsWithBuildReq)
        {
            // all:SOCGameState:game=test|state=31
            e = next();
            if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCGameState)))
                return null;
            switch (state.currentGameState)
            {
            case SOCGame.PLACING_ROAD:
            case SOCGame.PLACING_SETTLEMENT:
            case SOCGame.PLACING_CITY:
            case SOCGame.PLACING_SHIP:
                break;  // placement states OK

            default:
                return null;
            }

            // f3:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=67
            e = next();
            if (! (e.isFromClient && (e.pn == state.currentPlayerNumber)
                   && (e.event instanceof SOCPutPiece)))
                return null;
        }

        // Optional: Occasionally extra messages here, depending on game options/scenario
        // (SOCSVPTextMessage, SOCPlayerElement, etc)
        boolean hasFog = false, fogHasGold = false, fogHasNonGold = false;
            // reminder: Placing initial settlement can reveal more than 1 fog hex
        do
        {
            e = next();
            if (e == null)
                return null;
            if (e.event instanceof SOCRevealFogHex)
            {
                hasFog = true;
                if (((SOCRevealFogHex) e.event).getParam2() == SOCBoardLarge.GOLD_HEX)
                    fogHasGold = true;
                else
                    fogHasNonGold = true;
            }
        } while (! (e.event instanceof SOCPutPiece));

        // all:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=60a
        if (! e.isToAll())
            return null;
        final int pType = ((SOCPutPiece) e.event).getPieceType(),
            buildCoord = ((SOCPutPiece) e.event).getCoordinates(),
            playerNumber = ((SOCPutPiece) e.event).getPlayerNumber();

        final ExtractorState eState = new ExtractorState(state);

        // Optional, if Longest Route player changes: all:SOCGameElements:game=test|e6=3  // LONGEST_ROAD_PLAYER
        e = next();
        if (e.isToAll() && (e.event instanceof SOCGameElements))
        {
            SOCGameElements ge = (SOCGameElements) e.event;
            final int[] et = ge.getElementTypes();
            if ((et.length != 1) || (et[0] != SOCGameElements.GEType.LONGEST_ROAD_PLAYER.getValue()))
                return null;

            eState.snapshotFrom(state);
            e = next();
        }

        // If revealing any fog hexes as non-gold:
        if (hasFog && fogHasNonGold)
            // all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=1|amount=1|news=Y
            // (can be multiple if initial settlement was placed)
            do
            {
                if (! (e.isToAll() && (e.event instanceof SOCPlayerElement)
                       && ((SOCPlayerElement) e.event).isNews()
                       && (((SOCPlayerElement) e.event).getAction() == SOCPlayerElement.GAIN)))
                    return null;

                eState.snapshotFrom(state);
                e = next();
            }
            while ((e.event instanceof SOCPlayerElement) && (pType == SOCPlayingPiece.SETTLEMENT));

        // all:SOCGameState:game=test|state=20  // or 100 SPECIAL_BUILDING
        // Or during initial placement, can be all:SOCTurn which begins next sequence
        if (! e.isToAll())
            return null;
        if (isInitPlacement && (e.event instanceof SOCTurn))
        {
            backtrackTo(eState);

            int prevStart = currentSequenceStartIndex;
            return new Action
                (ActionType.BUILD_PIECE, state.currentGameState, resetCurrentSequence(), prevStart,
                 pType, buildCoord, playerNumber);
        }
        if (! (e.event instanceof SOCGameState))
            return null;

        // If revealing a fog hex as gold:
        if (hasFog && fogHasGold)
        {
            // all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1
            e = next();
            if (! (e.isToAll() && (e.event instanceof SOCPlayerElement)
                   && (((SOCPlayerElement) e.event).getAction() == SOCPlayerElement.SET)
                   && (((SOCPlayerElement) e.event).getElementType()
                       == SOCPlayerElement.PEType.NUM_PICK_GOLD_HEX_RESOURCES.getValue())))
                return null;

            // p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0
            e = next();
            if (e.isFromClient
                || ! ((e.pn == state.currentPlayerNumber) && (e.event instanceof SOCSimpleRequest)
                      && (((SOCSimpleRequest) e.event).getRequestType() == SOCSimpleRequest.PROMPT_PICK_RESOURCES)))
                return null;
        }

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.BUILD_PIECE, state.currentGameState, resetCurrentSequence(), prevStart,
             pType, buildCoord, playerNumber);
    }

    /**
     * Extract {@link ActionType#MOVE_PIECE} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_MOVE_PIECE(GameEventLog.QueueEntry e)
    {
        // f3:SOCMovePiece:game=test|pn=3|pieceType=3|fromCoord=3078|toCoord=3846
        if (! e.isFromClient)
            return null;
        SOCMovePiece mp = (SOCMovePiece) e.event;
        final int pType = mp.getPieceType(), fromCoord = mp.getFromCoord(), toCoord = mp.getToCoord();

        // all:SOCMovePiece:game=test|pn=3|pieceType=3|fromCoord=3078|toCoord=3846
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCMovePiece)))
            return null;
        mp = (SOCMovePiece) e.event;
        if ((pType != mp.getPieceType()) || (fromCoord != mp.getFromCoord()) || (toCoord != mp.getToCoord()))
            return null;

        // optional: SOCGameElements(LONGEST_ROAD_PLAYER)
        final ExtractorState presentState = new ExtractorState(state);
        e = nextIfType(SOCMessage.GAMEELEMENTS);
        if (e != null)
        {
            final SOCGameElements ge = (SOCGameElements) e.event;
            final int[] et = ge.getElementTypes();
            if ((et.length != 1) || (et[0] != SOCGameElements.GEType.LONGEST_ROAD_PLAYER.getValue()))
                backtrackTo(presentState);
        }

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.MOVE_PIECE, state.currentGameState, resetCurrentSequence(), prevStart,
             pType, fromCoord, toCoord);
    }

    /**
     * Extract {@link ActionType#BUY_DEV_CARD} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_BUY_DEV_CARD(GameEventLog.QueueEntry e)
    {
        // f3:SOCBuyDevCardRequest:game=test
        if (! e.isFromClient)
            return null;

        // all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e2=1,e3=1,e4=1
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCPlayerElements)))
            return null;

        // all:SOCGameElements:game=test|e2=22  // DEV_CARD_COUNT
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCGameElements)))
            return null;
        final int count;
        {
            final SOCGameElements ge = (SOCGameElements) e.event;
            final int[] et = ge.getElementTypes();
            if ((et.length != 1) || (et[0] != SOCGameElements.GEType.DEV_CARD_COUNT.getValue()))
                return null;
            count = ge.getValues()[0];
        }

        // p3:SOCDevCardAction:game=test|playerNum=3|actionType=DRAW|cardType=5
        e = next();
        if ((e == null) || e.isFromClient || (e.pn < 0) || ! (e.event instanceof SOCDevCardAction))
            return null;
        final int cardType;
        {
            final SOCDevCardAction dca = (SOCDevCardAction) e.event;
            if (dca.getAction() != SOCDevCardAction.DRAW)
                return null;
            cardType = dca.getCardType();
        }

        // !p3:SOCDevCardAction:game=test|playerNum=3|actionType=DRAW|cardType=0
        e = next();
        if ((e == null) || e.isFromClient || (e.excludedPN == null) || ! (e.event instanceof SOCDevCardAction))
            return null;

        // all:SOCSimpleAction:game=test|pn=3|actType=1|v1=22|v2=0
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCSimpleAction)))
            return null;

        // all:SOCGameState:game=test|state=20  // or others
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCGameState)))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.BUY_DEV_CARD, state.currentGameState, resetCurrentSequence(), prevStart,
             cardType, count, 0);
    }

    /**
     * Extract {@link ActionType#PLAY_DEV_CARD} from the current message sequence.
     * Calls {@link #extract_PLAY_DEV_CARD_ROADS()}, {@link #extract_PLAY_DEV_CARD_MONOPOLY()}, etc.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_PLAY_DEV_CARD(GameEventLog.QueueEntry e)
    {
        // f3:SOCPlayDevCardRequest:game=test|devCard=2
        if (! (e.isFromClient && (e.pn == state.currentPlayerNumber)))
            return null;

        // all:SOCDevCardAction:game=test|playerNum=3|actionType=PLAY|cardType=2
        e = next();
        if (! (e.isToAll() && (e.event instanceof SOCDevCardAction)
               && (((SOCDevCardAction) e.event).getAction() == SOCDevCardAction.PLAY)))
            return null;
        final int cardType = ((SOCDevCardAction) e.event).getCardType();

        // all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=19|amount=1  // PLAYED_DEV_CARD_FLAG
        e = next();
        if (! (e.isToAll() && (e.event instanceof SOCPlayerElement)
               && ((SOCPlayerElement) e.event).getElementType()
                   == SOCPlayerElement.PEType.PLAYED_DEV_CARD_FLAG.getValue()))
            return null;

        // Rest of sequence varies by dev card type; they all end with SOCGameState
        final Action seq;
        switch (cardType)
        {
        case SOCDevCardConstants.ROADS:
            seq = extract_PLAY_DEV_CARD_ROADS();
            break;

        case SOCDevCardConstants.DISC:
            seq = extract_PLAY_DEV_CARD_DISCOVERY();
            break;

        case SOCDevCardConstants.MONO:
            seq = extract_PLAY_DEV_CARD_MONOPOLY();
            break;

        case SOCDevCardConstants.KNIGHT:
            seq = extract_PLAY_DEV_CARD_KNIGHT();
            break;

        default:
            return null;
        }

        // If was played before dice roll, now in gamestate ROLL_OR_CARD, that may be followed by:
        // all:SOCRollDicePrompt:game=test|playerNumber=3
        if ((seq != null) && (state.currentGameState == SOCGame.ROLL_OR_CARD))
        {
            e = nextIfType(SOCMessage.ROLLDICEPROMPT);
            if (e != null)
            {
                seq.eventSequence.add(e);
                resetCurrentSequence();
            }
        }

        return seq;
    }

    /**
     * Extract the {@link ActionType#PLAY_DEV_CARD}({@link SOCDevCardConstants#ROADS}) action
     * from the rest of the current sequence.
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_PLAY_DEV_CARD_ROADS()
    {
        GameEventLog.QueueEntry e = next();
        if (! (e.isToAll() && (e.event instanceof SOCGameState)))
            return null;

        if (state.currentGameState == SOCGame.PLACING_FREE_ROAD1)
        {
            // If player has only 1 remaining road/ship, skips this section:
            // all:SOCGameState:game=test|state=40
            //     (is validated above)

            // f3:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=704  // or pieceType=3 for ship
            e = next();
            if (! (e.isFromClient && (e.pn == state.currentPlayerNumber)
                   && (e.event instanceof SOCPutPiece)))
                return null;

            // all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=704
            e = next();
            if (! (e.isToAll() && (e.event instanceof SOCPutPiece)))
                return null;

            // If gains Longest Route after 1st placement: all:SOCGameElements:game=test|e6=3
            e = next();
            if (e.event instanceof SOCGameElements)
            {
                if (! e.isToAll())
                    return null;
                e = next();
            }

            if (! (e.isToAll() && (e.event instanceof SOCGameState)))
                return null;
        }

        // all:SOCGameState:game=test|state=41
        if (state.currentGameState != SOCGame.PLACING_FREE_ROAD2)
            return null;

        // f3:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=804
        e = next();
        if (! (e.isFromClient && (e.pn == state.currentPlayerNumber) && (e.event instanceof SOCPutPiece)))
            return null;

        // all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=804
        e = next();
        if (! (e.isToAll() && (e.event instanceof SOCPutPiece)))
            return null;

        // If gains Longest Route after 2nd placement: all:SOCGameElements:game=test|e6=3
        e = next();
        if (e.event instanceof SOCGameElements)
        {
            if (! e.isToAll())
                return null;
            e = next();
        }

        // all:SOCGameState:game=test|state=20  // or 15
        if (! (e.isToAll() && (e.event instanceof SOCGameState)))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.PLAY_DEV_CARD, state.currentGameState, resetCurrentSequence(), prevStart,
             SOCDevCardConstants.ROADS, 0, 0);
    }

    /**
     * Extract the {@link ActionType#PLAY_DEV_CARD}({@link SOCDevCardConstants#DISC}) action
     * from the rest of the current sequence.
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_PLAY_DEV_CARD_DISCOVERY()
    {
        // all:SOCGameState:game=test|state=52
        GameEventLog.QueueEntry e = next();
        if (! (e.isToAll() && (e.event instanceof SOCGameState)))
            return null;
        if (state.currentGameState != SOCGame.WAITING_FOR_DISCOVERY)
            return null;

        // f3:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=1|wood=0|unknown=0
        e = next();
        if (! (e.isFromClient && (e.pn == state.currentPlayerNumber) && (e.event instanceof SOCPickResources)))
            return null;

        // all:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=1|wood=0|unknown=0|pn=3|reason=2
        e = next();
        if (! (e.isToAll() && (e.event instanceof SOCPickResources)))
            return null;
        SOCResourceSet picked = ((SOCPickResources) e.event).getResources();

        // all:SOCGameState:game=test|state=20  // or 15 (ROLL_OR_CARD)
        e = next();
        if (! (e.isToAll() && (e.event instanceof SOCGameState)))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.PLAY_DEV_CARD, state.currentGameState, resetCurrentSequence(), prevStart,
             SOCDevCardConstants.DISC, 0, 0, picked, null);
    }

    /**
     * Extract the {@link ActionType#PLAY_DEV_CARD}({@link SOCDevCardConstants#MONO}) action
     * from the rest of the current sequence.
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_PLAY_DEV_CARD_MONOPOLY()
    {
        // all:SOCGameState:game=test|state=53
        GameEventLog.QueueEntry e = next();
        if (! (e.isToAll() && (e.event instanceof SOCGameState)))
            return null;
        if (state.currentGameState != SOCGame.WAITING_FOR_MONOPOLY)
            return null;

        // f3:SOCPickResourceType:game=test|resType=3
        e = next();
        if (! (e.isFromClient && (e.pn == state.currentPlayerNumber) && (e.event instanceof SOCPickResourceType)))
            return null;

        SOCPlayerElement pe;

        // From the victim players, if any:
        for (;;)
        {
            e = next();
            if (! (e.isToAll() && (e.event instanceof SOCPlayerElement)))
                return null;
            pe = (SOCPlayerElement) e.event;

            // all:SOCPlayerElement:game=test|playerNum=1|actionType=SET|elementType=3|amount=0|news=Y
            if (pe.isNews() && (pe.getAction() == SOCPlayerElement.SET))
            {
                // all:SOCResourceCount:game=test|playerNum=1|count=7
                e = next();
                if (! (e.isToAll() && (e.event instanceof SOCResourceCount)))
                    return null;
            }
            else if (pe.getAction() == SOCPlayerElement.GAIN)
                break;
            else
                return null;
        }
        // postcondition: pe is current all:SOCPlayerElement(GAIN) message

        // To the current player:
        // all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=3|amount=6  // or amount=0 if none gained
        if (pe.getAction() != SOCPlayerElement.GAIN)
            return null;

        // all:SOCSimpleAction:game=test|pn=3|actType=3|v1=6|v2=3
        e = next();
        if (! (e.isToAll() && (e.event instanceof SOCSimpleAction)))
            return null;
        SOCSimpleAction sa = (SOCSimpleAction) e.event;
        if (sa.getActionType() != SOCSimpleAction.RSRC_TYPE_MONOPOLIZED)
            return null;
        final int resAmount = sa.getValue1(), resType = sa.getValue2();
        final SOCResourceSet gained;
        if (resAmount == 0)
            gained = null;
        else {
            gained = new SOCResourceSet();
            gained.add(resAmount, resType);
        }

        // all:SOCGameState:game=test|state=20  // or 15 (ROLL_OR_CARD)
        e = next();
        if (! (e.isToAll() && (e.event instanceof SOCGameState)))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.PLAY_DEV_CARD, state.currentGameState, resetCurrentSequence(), prevStart,
             SOCDevCardConstants.MONO, 0, 0, gained, null);
    }

    /**
     * Extract the {@link ActionType#PLAY_DEV_CARD}({@link SOCDevCardConstants#KNIGHT}) action
     * from the rest of the current sequence.
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_PLAY_DEV_CARD_KNIGHT()
    {
        // all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=15|amount=1  // NUMKNIGHTS
        GameEventLog.QueueEntry e = next();
        if (! (e.isToAll() && (e.event instanceof SOCPlayerElement)
               && (((SOCPlayerElement) e.event).getElementType() == SOCPlayerElement.PEType.NUMKNIGHTS.getValue())))
            return null;

        // If Largest Army player changing: all:SOCGameElements:game=test|e5=3  // LARGEST_ARMY_PLAYER
        e = next();
        if (! e.isToAll())
            return null;
        if (e.event instanceof SOCGameElements)
        {
            SOCGameElements ge = (SOCGameElements) e.event;
            final int[] et = ge.getElementTypes();
            if ((et.length != 1) || (et[0] != SOCGameElements.GEType.LARGEST_ARMY_PLAYER.getValue()))
                return null;

            e = next();
            if (! e.isToAll())
                return null;
        }

        // all:SOCGameState:game=test|state=33  // or another state
        if (! (e.event instanceof SOCGameState))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.PLAY_DEV_CARD, state.currentGameState, resetCurrentSequence(), prevStart,
             SOCDevCardConstants.KNIGHT, 0, 0);
    }

    /**
     * Extract {@link ActionType#DISCARD} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_DISCARD(GameEventLog.QueueEntry e)
    {
        // f2:SOCDiscard:game=test|resources=clay=0|ore=0|sheep=2|wheat=0|wood=3|unknown=0
        if (! e.isFromClient)
            return null;
        final int discardPN = e.pn;
        final SOCResourceSet discards = ((SOCDiscard) e.event).getResources();

        // p2:SOCPlayerElement:game=test|playerNum=2|actionType=LOSE|elementType=3|amount=2
        e = next();
        if ((e == null) || e.isFromClient || (e.pn != discardPN)
            || (! (e.event instanceof SOCPlayerElement))
            || (((SOCPlayerElement) e.event).getPlayerNumber() != discardPN))
            return null;

        // Might be more of: p2:SOCPlayerElement:game=test|playerNum=2|actionType=LOSE|elementType=5|amount=3
        do
        {
            e = next();
            if ((e == null) || e.isFromClient || (! (e.event instanceof SOCPlayerElement))
                || (((SOCPlayerElement) e.event).getPlayerNumber() != discardPN))
                return null;
        } while (e.pn == discardPN);
        // Postcondition: e is a SOCPlayerElement from server with playerNum=discardPN, but not sent to "p2:"
        // So check if its audience is exclusive like:
        // !p2:SOCPlayerElement:game=test|playerNum=2|actionType=LOSE|elementType=6|amount=5|news=Y
        if (e.excludedPN == null)
            return null;

        // (an ignored) all:SOCGameServerText:game=test|text=p2 discarded 5 resources.
        // If no other players need to discard:
        //     all:SOCGameState:game=test|state=33  // or other: choose robber or pirate, etc
        // If other players still need to discard:
        //     an ignored all:SOCGameServerText:game=test|text=p2 needs to discard.
        //     and no final SOCGameState message, since state is still 50 (WAITING_FOR_DISCARDS)
        // So, include and move past an optional SOCGameState:
        nextIfType(SOCMessage.GAMESTATE);

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.DISCARD, state.currentGameState, resetCurrentSequence(), prevStart,
             discardPN, 0, 0, discards, null);
    }

    /**
     * Extract {@link ActionType#CHOOSE_FREE_RESOURCES} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_CHOOSE_FREE_RESOURCES(GameEventLog.QueueEntry e)
    {
        // f3:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0
        if (! e.isFromClient)
            return null;
        final SOCResourceSet picks = ((SOCPickResources) e.event).getResources();

        // all:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0|pn=3|reason=3
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCPickResources)))
            return null;

        // all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=0  // NUM_PICK_GOLD_HEX_RESOURCES
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCPlayerElement)))
            return null;
        SOCPlayerElement pe = (SOCPlayerElement) e.event;
        if ((pe.getAction() != SOCPlayerElement.SET) || (pe.getAmount() != 0)
            || (pe.getElementType() != SOCPlayerElement.PEType.NUM_PICK_GOLD_HEX_RESOURCES.getValue()))
            return null;

        // all:SOCGameState:game=test|state=20  // or another state
        // Or during initial placement, can be all:SOCTurn which begins next sequence
        ExtractorState eState = new ExtractorState(state);
        e = next();
        if ((e == null) || ! e.isToAll())
            return null;
        if ((e.event instanceof SOCTurn) && (state.currentGameState < SOCGame.ROLL_OR_CARD))
            backtrackTo(eState);
        else if (! (e.event instanceof SOCGameState))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.CHOOSE_FREE_RESOURCES, state.currentGameState, resetCurrentSequence(), prevStart,
             picks, null);
    }

    /**
     * Extract {@link ActionType#CHOOSE_MOVE_ROBBER_PIRATE} from the current message sequence.
     * gameState should be {@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE} when called.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_CHOOSE_MOVE_ROBBER_PIRATE(GameEventLog.QueueEntry e)
    {
        // f3:SOCChoosePlayer:game=test|choice=-2  // or -3
        if (! e.isFromClient)
            return null;
        final int choice = (((SOCChoosePlayer) e.event).getChoice() == SOCChoosePlayer.CHOICE_MOVE_ROBBER)
            ? 1
            : 2;

        // all:SOCGameState:game=test|state=33  // or another state
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCGameState)))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.CHOOSE_MOVE_ROBBER_PIRATE, state.currentGameState, resetCurrentSequence(), prevStart,
             choice, 0, 0);
    }

    /**
     * Extract {@link ActionType#MOVE_ROBBER_OR_PIRATE} from the current message sequence.
     * gameState should be {@link SOCGame#PLACING_ROBBER} or {@link SOCGame#PLACING_PIRATE} when called.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     *     or if {@link ExtractorState#currentGameState} when called isn't one of those two {@code PLACING_} states.
     */
    private Action extract_MOVE_ROBBER_OR_PIRATE(GameEventLog.QueueEntry e)
    {
        boolean isPirate = (state.currentGameState == SOCGame.PLACING_PIRATE);
        if (! (isPirate || (state.currentGameState == SOCGame.PLACING_ROBBER)))
            return null;

        // f3:SOCMoveRobber:game=test|playerNumber=3|coord=504
        if (! (e.isFromClient && (e.pn == state.currentPlayerNumber)))
            return null;

        // all:SOCMoveRobber:game=test|playerNumber=3|coord=504
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCMoveRobber)))
            return null;
        int hexCoord = ((SOCMoveRobber) e.event).getCoordinates();
        if (isPirate && (hexCoord < 0))
            hexCoord = -hexCoord;  // network sends pirate as negative coord; Action abstracts away from that

        // If any choices to be made:
        // all:SOCGameState:game=test|state=20  // or another state
        // Otherwise next message is SOCReportRobbery from server, which isn't part of this sequence
        ExtractorState presentState = new ExtractorState(state);

        e = next();
        if ((e == null) || e.isFromClient)
            return null;

        if (e.event instanceof SOCReportRobbery)
            backtrackTo(presentState);
        else if (! (e.isToAll() && (e.event instanceof SOCGameState)))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.MOVE_ROBBER_OR_PIRATE, state.currentGameState, resetCurrentSequence(), prevStart,
             (isPirate) ? 2 : 1, hexCoord, 0);
    }

    /**
     * Extract {@link ActionType#CHOOSE_ROBBERY_VICTIM} from the current message sequence.
     * gameState should be {@link SOCGame#WAITING_FOR_ROB_CHOOSE_PLAYER} when called.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_CHOOSE_ROBBERY_VICTIM(GameEventLog.QueueEntry e)
    {
        // p3:SOCChoosePlayerRequest:game=test|choices=[true, false, true, false]
        if (e.isFromClient || (e.pn != state.currentPlayerNumber))
            return null;

        // f3:SOCChoosePlayer:game=test|choice=2
        e = next();
        if ((e == null) || (! e.isFromClient) || (e.pn != state.currentPlayerNumber))
            return null;
        int chosenPN = ((SOCChoosePlayer) e.event).getChoice();

        if (chosenPN == SOCChoosePlayer.CHOICE_NO_PLAYER)
            chosenPN = -2;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.CHOOSE_ROBBERY_VICTIM, state.currentGameState, resetCurrentSequence(), prevStart,
             chosenPN, 0, 0);
    }

    /**
     * Extract {@link ActionType#CHOOSE_ROB_CLOTH_OR_RESOURCE} from the current message sequence.
     * gameState should be {@link SOCGame#WAITING_FOR_ROB_CLOTH_OR_RESOURCE} when called.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_CHOOSE_ROB_CLOTH_OR_RESOURCE(GameEventLog.QueueEntry e)
    {
        // p3:SOCChoosePlayer:game=test|choice=2  // 2 = victim pn
        if (e.isFromClient || (e.pn != state.currentPlayerNumber))
            return null;

        // f3:SOCChoosePlayer:game=test|choice=-3  // negative pn -> rob cloth, not resource
        e = next();
        if (! (e.isFromClient && (e.pn == state.currentPlayerNumber)
               && (e.event instanceof SOCChoosePlayer)))
            return null;
        boolean choseCloth = (((SOCChoosePlayer) e.event).getChoice() < 0);

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.CHOOSE_ROB_CLOTH_OR_RESOURCE, state.currentGameState, resetCurrentSequence(), prevStart,
             (choseCloth ? 2 : 1), 0, 0);
    }

    /**
     * Extract {@link ActionType#ROB_PLAYER} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_ROB_PLAYER(GameEventLog.QueueEntry e)
    {
        final SOCReportRobbery rr = (SOCReportRobbery) e.event;
        SOCResourceSet stolenRes = null;
        SOCPlayerElement.PEType stolenPE = null;

        if (e.isToAll())
        {
            // Stealing cloth:
            // all:SOCReportRobbery:game=test|perp=3|victim=2|peType=SCENARIO_CLOTH_COUNT|amount=4|isGainLose=false|victimAmount=3

            if (rr.peType != null)
                stolenPE = rr.peType;
            else
                return null;
        } else {
            // Stealing a resource:
            // p3:SOCReportRobbery:game=test|perp=3|victim=2|resType=5|amount=1|isGainLose=true
            if (e.isFromClient || (e.pn < 0) || (rr.peType != null))
                return null;
            if (rr.resSet != null)
            {
                stolenRes = rr.resSet;
            } else {
                stolenRes = new SOCResourceSet();
                stolenRes.add(rr.amount, rr.resType);
            }

            // p2:SOCReportRobbery:game=test|perp=3|victim=2|resType=5|amount=1|isGainLose=true
            e = next();
            if (e.isFromClient || (e.pn < 0) || ! (e.event instanceof SOCReportRobbery))
                return null;

            // !p[3, 2]:SOCReportRobbery:game=test|perp=3|victim=2|resType=6|amount=1|isGainLose=true
            e = next();
            if ((e.excludedPN == null) || ! (e.event instanceof SOCReportRobbery))
                return null;
        }

        // Common to both:
        // all:SOCGameState:game=test|state=20  // or 15 if hasn't rolled yet
        e = next();
        if (! (e.isToAll() && (e.event instanceof SOCGameState)))
            return null;

        int prevStart = currentSequenceStartIndex;
        if (stolenRes != null)
            return new Action
                (ActionType.ROB_PLAYER, state.currentGameState, resetCurrentSequence(), prevStart,
                 rr.victimPN, 0, 0, stolenRes, null);
        else
            return new Action
                (ActionType.ROB_PLAYER, state.currentGameState, resetCurrentSequence(), prevStart,
                 rr.victimPN, stolenPE.getValue(), rr.amount);
    }

    /**
     * Extract {@link ActionType#TRADE_BANK} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_TRADE_BANK(GameEventLog.QueueEntry e)
    {
        // f3:SOCBankTrade:game=test|give=clay=0|ore=3|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0
        if (! (e.isFromClient && (e.pn == state.currentPlayerNumber)))
            return null;

        // all:SOCBankTrade:game=test|give=clay=0|ore=3|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0|pn=3
        e = next();
        if ((e == null) || ! e.isToAll())
            return null;
        SOCBankTrade bt = ((SOCBankTrade) e.event);

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.TRADE_BANK, state.currentGameState, resetCurrentSequence(), prevStart,
             bt.getGiveSet(), bt.getGetSet());
    }

    /**
     * Extract {@link ActionType#TRADE_MAKE_OFFER} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_TRADE_MAKE_OFFER(GameEventLog.QueueEntry e)
    {
        // f2:SOCMakeOffer:game=test|offer=game=test|from=2|to=false,false,false,true|give=clay=0|ore=0|sheep=0|wheat=1|wood=0|unknown=0|get=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0
        if (! (e.isFromClient && (e.pn != -1)))
            return null;

        // all:SOCMakeOffer:game=test|offer=game=test|from=2|to=false,false,false,true|give=clay=0|ore=0|sheep=0|wheat=1|wood=0|unknown=0|get=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCMakeOffer)))
            return null;
        SOCTradeOffer offer = ((SOCMakeOffer) e.event).getOffer();
        final int offerFromPN = offer.getFrom();

        // all:SOCClearTradeMsg:game=test|playerNumber=-1
        e = next();
        if ((e == null)
            || ! (e.isToAll() && (e.event instanceof SOCClearTradeMsg)
                  && (-1 == ((SOCClearTradeMsg) e.event).getPlayerNumber())))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.TRADE_MAKE_OFFER, state.currentGameState, resetCurrentSequence(), prevStart,
             offerFromPN, 0, 0, offer.getGiveSet(), offer.getGetSet());
    }

    /**
     * Extract {@link ActionType#TRADE_REJECT_OFFER} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_TRADE_REJECT_OFFER(GameEventLog.QueueEntry e)
    {
        // f3:SOCRejectOffer:game=test|playerNumber=0
        if (! (e.isFromClient && (e.pn != -1)))
            return null;

        // all:SOCRejectOffer:game=test|playerNumber=3
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCRejectOffer)))
            return null;
        final int rejectingPN = ((SOCRejectOffer) e.event).getPlayerNumber();

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.TRADE_REJECT_OFFER, state.currentGameState, resetCurrentSequence(), prevStart,
             rejectingPN, 0, 0);
    }

    /**
     * Extract {@link ActionType#TRADE_ACCEPT_OFFER} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_TRADE_ACCEPT_OFFER(GameEventLog.QueueEntry e)
    {
        // f2:SOCAcceptOffer:game=test|accepting=0|offering=3
        if (! (e.isFromClient && (e.pn != -1)))
            return null;

        // all:SOCAcceptOffer:game=test|accepting=2|offering=3|toAccepting=clay=1|ore=0|sheep=0|wheat=0|wood=0|unknown=0|toOffering=clay=0|ore=0|sheep=0|wheat=0|wood=1|unknown=0
        e = next();
        if ((e == null) || ! (e.isToAll() && (e.event instanceof SOCAcceptOffer)))
            return null;
        SOCAcceptOffer ao = (SOCAcceptOffer) e.event;

        // all:SOCClearOffer:game=test|playerNumber=-1
        e = next();
        if ((e == null)
            || ! (e.isToAll() && (e.event instanceof SOCClearOffer)
                  && (-1 == ((SOCClearOffer) e.event).getPlayerNumber())))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.TRADE_ACCEPT_OFFER, state.currentGameState, resetCurrentSequence(), prevStart,
             ao.getOfferingNumber(), ao.getAcceptingNumber(), 0,
             ao.getResToAcceptingPlayer(), ao.getResToOfferingPlayer());
    }

    /**
     * Extract {@link ActionType#ASK_SPECIAL_BUILDING} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_ASK_SPECIAL_BUILDING(GameEventLog.QueueEntry e)
    {
        // f3:SOCBuildRequest:game=test|pieceType=-1  // or a defined piece type
        if (! e.isFromClient)
            return null;
        final int requestingPN = e.pn;

        // all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=16|amount=1  // ASK_SPECIAL_BUILD
        e = next();
        if ((e == null)
            || ! (e.isToAll() && (e.event instanceof SOCPlayerElement)))
            return null;
        SOCPlayerElement pe = (SOCPlayerElement) e.event;
        if ((pe.getAction() != SOCPlayerElement.SET) || (pe.getPlayerNumber() != requestingPN) || (pe.getAmount() != 1)
            || (pe.getElementType() != SOCPlayerElement.PEType.ASK_SPECIAL_BUILD.getValue()))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.ASK_SPECIAL_BUILDING, state.currentGameState, resetCurrentSequence(), prevStart,
             requestingPN, 0, 0);
    }

    /**
     * Extract {@link ActionType#END_TURN} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     */
    private Action extract_END_TURN(GameEventLog.QueueEntry e)
    {
        // f3:SOCEndTurn:game=test
        if (! e.isFromClient)
            return null;

        // If from special building: all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=16|amount=0  // ASK_SPECIAL_BUILD
        e = next();
        if (state.currentGameState == SOCGame.SPECIAL_BUILDING)
        {
            if (! (e.isToAll() && (e.event instanceof SOCPlayerElement)
                && (((SOCPlayerElement) e.event).getElementType()
                    == SOCPlayerElement.PEType.ASK_SPECIAL_BUILD.getValue())))
                return null;

            e = next();
        }

        // all:SOCClearOffer:game=test|playerNumber=-1
        if (! (e.isToAll() && (e.event instanceof SOCClearOffer)))
            return null;

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.END_TURN, state.currentGameState, resetCurrentSequence(), prevStart);
    }

    /**
     * Extract {@link ActionType#GAME_OVER} from the current message sequence.
     * @param e  First entry of current sequence, already validated and added to {@link #currentSequence}; not null
     * @return extracted action, or {@code null} if sequence incomplete
     *     or if {@link ExtractorState#currentGameState} when called isn't one of those two {@code PLACING_} states.
     */
    private Action extract_GAME_OVER(GameEventLog.QueueEntry e)
    {
        // all:SOCGameElements:game=test|e4=3
        SOCGameElements ge = (SOCGameElements) e.event;
        int[] et = ge.getElementTypes();
        if ((et.length != 1) || (et[0] != SOCGameElements.GEType.CURRENT_PLAYER.getValue())
            || ! e.isToAll())
            return null;
        final int newCurrPlayer = ge.getValues()[0];

        // all:SOCGameState:game=test|state=1000
        final ExtractorState presentState = new ExtractorState(state);
        e = nextIfType(SOCMessage.GAMESTATE);
        if (e == null)
            return null;
        if (SOCGame.OVER != ((SOCGameState) e.event).getState())
        {
            backtrackTo(presentState);
            return null;
        }

        // players' revealed dev cards, if any:
        // all:SOCDevCardAction:game=test|playerNum=2|actionType=ADD_OLD|cardType=6
        // all:SOCDevCardAction:game=test|playerNum=3|actionType=ADD_OLD|cardTypes=[5, 4]
        e = next();
        if (e == null)
            return null;
        if (e.event instanceof SOCDevCardAction)
        {
            do
            {
                e = next();
                if (e == null)
                    return null;
            }
            while (e.event instanceof SOCDevCardAction);
        }

        // all:SOCGameStats:game=test|0|0|3|10|false|false|false|false
        if (! (e.event instanceof SOCGameStats))
            return null;

        // stats to players, if clients connected:
        // p3:SOCPlayerStats:game=test|p=1|p=2|p=6|p=0|p=5|p=1
        while (null != nextIfType(SOCMessage.PLAYERSTATS)) {}

        int prevStart = currentSequenceStartIndex;
        return new Action
            (ActionType.GAME_OVER, state.currentGameState, resetCurrentSequence(), prevStart,
             newCurrPlayer, 0, 0);
    }

    /** State fields, encapsulated for calling {@link GameActionExtractor#backtrackTo(ExtractorState)}. */
    protected class ExtractorState
    {
        /**
         * Index within {@link GameActionExtractor#eventLog} of the next message
         * to be considered for extracting from a sequence.
         * @see #currentSequenceStartIndex
         */
        public int nextLogIndex;

        /**
         * "Current" size of sequencer's {@link GameActionExtractor#currentSequence}.
         * Not continually updated during extraction, is -1 in extractor's {@link GameActionExtractor#state}:
         * Used only by copy constructor, {@link #snapshotFrom(ExtractorState)},
         * and {@link GameActionExtractor#backtrackTo(ExtractorState)}, otherwise -1.
         */
        public int currentSequenceSize = -1;

        /**
         * Current player number, or -1 if not yet known.
         * Updated in {@link GameActionExtractor#next()}.
         */
        public int currentPlayerNumber = -1;

        /**
         * Current game state, from most recent {@link SOCGameState} or other state-bearing message
         * seen by {@link #next()}. 0 at start of log.
         */
        public int currentGameState;

        public ExtractorState() {}

        /**
         * Copy constructor, to snapshot current state
         * before a call to {@link GameActionExtractor#next()}.
         * @param snapshotFrom  State to snapshot from; not null.
         *    If its {@link #currentSequenceSize} is -1, will call
         *    {@link GameActionExtractor#currentSequence}{@link List#size() .size()}.
         * @see #snapshotFrom(ExtractorState)
         */
        public ExtractorState(ExtractorState snapshotFrom)
        {
            snapshotFrom(snapshotFrom);
        }

        /**
         * Snapshot current state before a call to {@link GameActionExtractor#next()}.
         * Overwrites values of all fields.
         * @param from  State to snapshot from; not null.
         *    If its {@link #currentSequenceSize} is -1, will call
         *    {@link GameActionExtractor#currentSequence}{@link List#size() .size()}.
         * @see #ExtractorState(ExtractorState)
         */
        public void snapshotFrom(ExtractorState from)
        {
            nextLogIndex = from.nextLogIndex;
            currentSequenceSize = (from.currentSequenceSize != -1)
                ? from.currentSequenceSize
                : currentSequence.size();
            currentPlayerNumber = from.currentPlayerNumber;
            currentGameState = from.currentGameState;
        }

        /**
         * Basic ExtractorState equality test.
         * Compares all ExtractorState fields except enclosing instance reference.
         * {@link #currentSequenceSize} is a field that may not always be equal, see its javadoc.
         * @return True if {@code o} is an {@code ExtractorState} and all compared fields are equal
         */
        public boolean equals(Object o)
        {
            if (! (o instanceof ExtractorState))
                return false;

            final ExtractorState es = (ExtractorState) o;
            return (nextLogIndex == es.nextLogIndex) && (currentSequenceSize == es.currentSequenceSize)
                && (currentPlayerNumber == es.currentPlayerNumber) && (currentGameState == es.currentGameState);
        }

        /**
         * Render this object's fields in human-readable form.
         * @return Object fields' current value, in the form
         *     {@code "ExtractorState{nextLogIndex=7, currentSeqSize=2, currentPN=3, currentGState=15"}
         */
        public String toString()
        {
            StringBuilder sb = new StringBuilder("ExtractorState{nextLogIndex=");

            sb.append(nextLogIndex)
                .append(", currentSeqSize=").append(currentSequenceSize)
                .append(", currentPN=").append(currentPlayerNumber)
                .append(", currentGState=").append(currentGameState)
                .append('}');

            return sb.toString();
        }

    }

}
