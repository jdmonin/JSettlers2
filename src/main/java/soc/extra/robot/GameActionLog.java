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
import java.util.List;

import soc.extra.server.GameEventLog;
import soc.game.SOCDevCardConstants;  // for javadocs only
import soc.game.SOCGame;    // javadocs only
import soc.game.SOCPlayingPiece;      // javadocs only
import soc.game.SOCResourceConstants; // javadocs only
import soc.game.SOCResourceSet;
import soc.message.SOCGameState;      // javadocs only
import soc.message.SOCPlayerElement;  // javadocs only

/**
 * Log of a game's actions, extracted from {@link GameEventLog}.
 * Log entries are {@link Action}.
 * See {@link Action.ActionType} for all recognized action types.
 *<P>
 * Current player number changes during {@link Action.ActionType#TURN_BEGINS} actions only.
 *<P>
 * Used by {@link GameActionSequenceRecognizer}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.5.00
 */
public class GameActionLog
    extends ArrayList<GameActionLog.Action>
{
    private static final long serialVersionUID = 2500;  // No change since v2.5.00

    // No constructor needed yet; parameterized superclass ArrayList handles it

    /**
     * One action in a {@link GameActionLog}.
     * Has action type {@link #actType}, its {@link #endingGameState},
     * the {@link #eventSequence} it's extracted from,
     * and optional int ({@link #param1}, {@link #param2}, {@link #param3})
     * and resource set ({@link #rset1}, {@link #rset2}) parameters.
     */
    public static class Action
    {
        public final ActionType actType;

        /**
         * Presumed {@link SOCGame#getGameState()} at end of this action,
         * based on event messages with gameState field.
         */
        public final int endingGameState;

        /** {@link ActionType}-specific int parameter value, or 0. */
        public final int param1, param2, param3;

        /** {@link ActionType}-specific resource set parameter value, or null. */
        public final SOCResourceSet rset1, rset2;

        /** Event sequence from which this Action is extracted. */
        public final List<GameEventLog.EventEntry> eventSequence;

        /** Index of beginning of this event sequence within its {@link GameEventLog}, or 0. */
        public final int startingLogIndex;

        /**
         * Create a new Action within the log, with no parameters.
         * @param aType Action type of this action; not null
         * @param endGState  Presumed {@link SOCGame#getGameState()} at end of this action
         * @param seq  Event sequence from which this action is extracted; not null or empty
         * @param startIndex  Index of first event within its {@link GameEventLog}
         * @throws IllegalArgumentException if {@code aType} is null, or {@code seq} is null or empty
         * @see #Action(ActionType, int, List, int, int, int, int)
         * @see #Action(ActionType, int, List, int, SOCResourceSet, SOCResourceSet)
         * @see #Action(ActionType, int, List, int, int, int, int, SOCResourceSet, SOCResourceSet)
         */
        public Action
            (ActionType aType, final int endGState, List<GameEventLog.EventEntry> seq, final int startIndex)
            throws IllegalArgumentException
        {
            this(aType, endGState, seq, startIndex, 0, 0, 0, null, null);
        }

        /**
         * Create a new Action within the log, with optional int parameters.
         * @param aType Action type of this action; not null
         * @param endGState  Presumed {@link SOCGame#getGameState()} at end of this action
         * @param seq  Event sequence from which this action is extracted; not null or empty
         * @param startIndex  Index of first event within its {@link GameEventLog}
         * @param p1  First action-specific parameter, or 0
         * @param p2  Second action-specific parameter, or 0
         * @param p3  Third action-specific parameter, or 0
         * @throws IllegalArgumentException if {@code aType} is null, or {@code seq} is null or empty
         * @see #Action(ActionType, int, List, int)
         * @see #Action(ActionType, int, List, int, SOCResourceSet, SOCResourceSet)
         * @see #Action(ActionType, int, List, int, int, int, int, SOCResourceSet, SOCResourceSet)
         */
        public Action
            (ActionType aType, final int endGState, List<GameEventLog.EventEntry> seq, final int startIndex,
             final int p1, final int p2, final int p3)
            throws IllegalArgumentException
        {
            this(aType, endGState, seq, startIndex, p1, p2, p3, null, null);
        }

        /**
         * Create a new Action within the log, with optional resource set parameters.
         * @param aType Action type of this action; not null
         * @param endGState  Presumed {@link SOCGame#getGameState()} at end of this action
         * @param seq  Event sequence from which this action is extracted; not null or empty
         * @param startIndex  Index of first event within its {@link GameEventLog}
         * @param rs1  First resource set parameter, or null
         * @param rs2  Second resource set parameter, or null
         * @throws IllegalArgumentException if {@code aType} is null, or {@code seq} is null or empty
         * @see #Action(ActionType, int, List, int)
         * @see #Action(ActionType, int, List, int, int, int, int)
         * @see #Action(ActionType, int, List, int, int, int, int, SOCResourceSet, SOCResourceSet)
         */
        public Action
            (ActionType aType, final int endGState, List<GameEventLog.EventEntry> seq, final int startIndex,
             final SOCResourceSet rs1, final SOCResourceSet rs2)
        {
            this(aType, endGState, seq, startIndex, 0, 0, 0, rs1, rs2);
        }

        /**
         * Create a new Action within the log, with optional int and resource parameters.
         * @param aType Action type of this action; not null
         * @param endGState  Presumed {@link SOCGame#getGameState()} at end of this action
         * @param seq  Event sequence from which this action is extracted; not null or empty
         * @param startIndex  Index of first event within its {@link GameEventLog}
         * @param p1  First action-specific parameter, or 0
         * @param p2  Second action-specific parameter, or 0
         * @param p3  Third action-specific parameter, or 0
         * @param rs1  First resource set parameter, or null
         * @param rs2  Second resource set parameter, or null
         * @throws IllegalArgumentException if {@code aType} is null, or {@code seq} is null or empty
         * @see #Action(ActionType, int, List, int)
         * @see #Action(ActionType, int, List, int, int, int, int)
         * @see #Action(ActionType, int, List, int, SOCResourceSet, SOCResourceSet)
         */
        public Action
            (ActionType aType, final int endGState, List<GameEventLog.EventEntry> seq, final int startIndex,
             final int p1, final int p2, final int p3, final SOCResourceSet rs1, final SOCResourceSet rs2)
            throws IllegalArgumentException
        {
            if (aType == null)
                throw new IllegalArgumentException("aType");
            if ((seq == null) || seq.isEmpty())
                throw new IllegalArgumentException("seq");

            actType = aType;
            endingGameState = endGState;
            param1 = p1;
            param2 = p2;
            param3 = p3;
            rset1 = rs1;
            rset2 = rs2;
            eventSequence = seq;
            startingLogIndex = startIndex;
        }

        /**
         * Contents for debugging, formatted like:
         *<BR>
         * Action(ROLL_DICE, endGState=20, p1=12, seq=[f3:SOCRollDice:game=test, all:SOCDiceResult:game=test|param=12, all:SOCGameServerText:game=test|text=No player gets anything., all:SOCGameState:game=test|state=20])
         *<P>
         * Includes {@code p1 p2 p3} if non-zero, {@code rs1 rs2} if non-null.
         */
        public String toString()
        {
            StringBuilder sb = new StringBuilder("Action(");
            sb.append(actType.toString()).append(", endGState=");
            sb.append(endingGameState);
            if ((param1 != 0) || (param2 != 0) || (param3 != 0))
            {
                sb.append(", p1=").append(param1);
                if ((param2 != 0) || (param3 != 0))
                {
                    sb.append(", p2=").append(param2);
                    if (param3 != 0)
                        sb.append(", p3=").append(param2);
                }
            }
            if ((rset1 != null) || (rset2 != null))
            {
                sb.append(", rs1=").append(rset1);
                if (rset2 != null)
                    sb.append(", rs2=").append(rset1);
            }
            sb.append(", seq=").append(eventSequence).append(')');

            return sb.toString();
        }

        /**
         * All recognized {@link Action}s.
         * Actions which don't include a player number are done by the current player.
         * Only {@link #TURN_BEGINS} actions change the current player number.
         */
        public static enum ActionType
        {
            // Declared in same order as in /doc/Message-Sequences-for-Game-Actions.md

            /** Enum constant with {@link #ordinal()} 0, declared to prevent 0 being seen as a valid type */
            UNINITIALIZED,

            /** Any sequence that doesn't start with a "sequence start" message type */
            UNKNOWN,

            /**
             * Entire contents of log before first turn of initial placement (optional section).
             * Last message in {@link Action#eventSequence} is a {@link SOCStartGame}.
             */
            LOG_START_TO_STARTGAME,

            /**
             * Start a player's regular turn or Special Build Phase (SBP), depending on {@link Action#endingGameState}.
             * Current player number has changed.
             *<BR>
             * {@code p1} = new current player number.
             */
            TURN_BEGINS,

            /**
             * Roll Dice. This action can have varying results for all players;
             * see contents of its {@link Action#eventSequence} for details.
             *<BR>
             * {@code p1} = total of the 2 rolled dice numbers.
             *<P>
             * Because roll-related messages occasionally follow the ending <tt>all:{@link SOCGameState}</tt>,
             * {@code eventSequence} can include any messages in the log after {@code all:SOCGameState}
             * before the next "start of sequence" message.
             */
            ROLL_DICE,

            /**
             * Build a piece (place a piece).
             *<BR>
             * {@code p1} = piece type like {@link SOCPlayingPiece#SETTLEMENT},
             * {@code p2} = coordinate to build at.
             * {@code p3} = player number who built the piece; helpful during initial placement.
             *<P>
             * When player changes during initial placement,
             * {@link Action#endingGameState} won't change during the {@code BUILD_PIECE},
             * but during the {@link #TURN_BEGINS} action which follows it.
             */
            BUILD_PIECE,

            /**
             * Move a piece (move a ship).
             *<BR>
             * {@code p1} = piece type like {@link SOCPlayingPiece#SHIP},
             * {@code p2} = coordinate to move from, {@code p3} = coordinate to move to.
             */
            MOVE_PIECE,

            /**
             * Buy a development card.
             *<BR>
             * {@code p1} = dev card type bought, like {@link SOCDevCardConstants#ROADS}.
             * {@code p2} = number of cards remaining in deck after purchase.
             * @see #PLAY_DEV_CARD
             */
            BUY_DEV_CARD,

            /**
             * Play a development card.
             *<BR>
             * {@code p1} = dev card type played, like {@link SOCDevCardConstants#ROADS}.
             * {@code rs1} = resources gained by Year of Plenty/Discovery or Monopoly (if none: null, not empty).
             *<P>
             * For knight/soldier ({@link SOCDevCardConstants#KNIGHT}),
             * see note at {@link #MOVE_ROBBER_OR_PIRATE} about when Largest Army can win the game.
             *
             * @see #BUY_DEV_CARD
             */
            PLAY_DEV_CARD,

            /**
             * Discard resources.
             *<BR>
             * {@code p1} = player number discarding.
             * {@code rs1} = the resources discarded (details if known, otherwise {@link SOCResourceConstants#UNKNOWN}).
             */
            DISCARD,

            /**
             * Choose free resources from Gold Hexes, during a dice roll or when revealed in the Fog Hexes scenario.
             *<BR>
             * {@code rs1} = the resources gained.
             */
            CHOOSE_FREE_RESOURCES,

            /**
             * Choose whether to move robber or pirate.
             * gameState before action should be {@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE}.
             *<BR>
             * {@code p1} = 1 for robber or 2 for pirate.
             */
            CHOOSE_MOVE_ROBBER_OR_PIRATE,

            /**
             * Move the robber or the pirate.
             * gameState before action should be {@link SOCGame#PLACING_ROBBER} or {@link SOCGame#PLACING_PIRATE}.
             *<BR>
             * {@code p1} = 1 for robber or 2 for pirate.
             * {@code p2} = new hex coordinate.
             *<P>
             * If player has just made the game-winning move by gaining Largest Army:
             *<UL>
             * <LI> If no possible victims, game state will change to {@link SOCGame#OVER}
             *      at end of this action's sequence
             * <LI> If there's victim(s) to rob from, game won't be {@link SOCGame#OVER}
             *      until after the robbery is completed
             *</UL>
             */
            MOVE_ROBBER_OR_PIRATE,

            /**
             * Choose a victim for robbery.
             *<BR>
             * {@code p1} = player number chosen, or -2 if scenario lets the player decline to rob:
             * See {@link SOCGame#WAITING_FOR_ROB_CHOOSE_PLAYER} for info on when that is allowed.
             * Can be -1 if chosen victim isn't visible in sequence.
             */
            CHOOSE_ROBBERY_VICTIM,

            /**
             * Choose whether to steal resources or cloth from robbery victim.
             *<BR>
             * {@code p1} = 1 for resources as usual, or 2 for cloth in the Cloth Trade scenario.
             */
            CHOOSE_ROB_CLOTH_OR_RESOURCE,

            /**
             * Rob from another player.
             *<BR>
             * {@code p1} = player number robbed.
             * {@code rs1} = the resource(s) robbed (details if known, otherwise {@link SOCResourceConstants#UNKNOWN}).
             *<BR>
             * Or if cloth was robbed (Cloth Trade scenario):
             *<BR>
             * {@code p1} = player number robbed.
             * {@code p2} = numeric value of player element robbed:
             *     {@link SOCPlayerElement.PEType#SCENARIO_CLOTH_COUNT}.{@link SOCPlayerElement.PEType#getValue() getValue()}.
             * {@code p3} = amount robbed.
             * {@code rs1} = null.
             *<P>
             * If player has just made winning move by gaining Largest Army,
             * game state won't be {@link SOCGame#OVER} until end of this {@code ROB_PLAYER} action.
             */
            ROB_PLAYER,

            /**
             * Trade with the bank, or undo the previous bank trade.
             *<BR>
             * {@code rs1} = resources given to the bank.
             * {@code rs2} = resources received.
             */
            TRADE_BANK,

            /**
             * Make a trade offer to some or all other players.
             *<BR>
             * {@code p1} = player number making the offer.
             * {@code rs1} = resources the offering player would give in the trade.
             * {@code rs2} = resources they would receive from accepting player.
             */
            TRADE_MAKE_OFFER,

            /**
             * Clear/cancel player's own trade offer.
             *<BR>
             * {@code p1} = player number canceling their offer.
             */
            TRADE_CLEAR_OFFER,

            /**
             * Reject a player's trade offer.
             *<BR>
             * {@code p1} = player number rejecting all offers made to them.
             */
            TRADE_REJECT_OFFER,

            /**
             * Accept a player's trade offer.
             *<BR>
             * {@code p1} = player number making the offer.
             * {@code p2} = player number accepting the offer.
             * {@code rs1} = "offered" resources traded to the accepting player from offering player.
             * {@code rs2} = "received" resources traded to the offering player from accepting player.
             */
            TRADE_ACCEPT_OFFER,

            /**
             * Ask Special Building during another player's turn.
             *<BR>
             * {@code p1} = player number asking to Special Build.
             */
            ASK_SPECIAL_BUILDING,

            /**
             * End player's current turn.
             */
            END_TURN,

            /**
             * End of Game, a player has won.
             *<BR>
             * {@code p1} = winning player number.
             */
            GAME_OVER
        }

    }

}
