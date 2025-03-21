/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2021-2022,2025 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.GameAction;
import soc.game.ResourceSet;
import soc.game.SOCGame;    // javadocs only

/**
 * Log of a game's actions, extracted from {@link GameEventLog}.
 * Log entries are {@link Action}.
 * See {@link GameAction.ActionType} for all recognized action types.
 *<P>
 * Current player number changes during {@link GameAction.ActionType#TURN_BEGINS} actions only.
 *<P>
 * Used by {@link GameActionSequenceRecognizer}.
 *<P>
 * In v2.7.00, {@code enum ActionType} was moved from here to {@link GameAction.ActionType}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.5.00
 */
public class GameActionLog
    extends ArrayList<GameActionLog.Action>
{
    private static final long serialVersionUID = 2500;  // No change since v2.5.00

    /**
     * True if this action log is from a {@link GameEventLog} which includes only messages from the server
     * to one client, not also from all clients to server: See {@link GameEventLog#isAtClient}.
     * @see #atClientPN
     */
    public final boolean isAtClient;

    /**
     * If not -1, this action log is from a {@link GameEventLog} which includes
     * only messages from server to this one client player number, as if log was recorded at that client.
     * @see GameEventLog#atClientPN
     * @see #isAtClient
     */
    public final int atClientPN;

    /**
     * Create a new empty {@link GameActionLog}.
     * @param isAtClient  Value for {@link #isAtClient} field
     * @param atClientPN  Value for {@link #atClientPN} field
     */
    public GameActionLog(final boolean isAtClient, final int atClientPN)
    {
        super();
        this.isAtClient = isAtClient;
        this.atClientPN = atClientPN;
    }

    /**
     * One action in a {@link GameActionLog}.
     * Has action type {@link #actType}, its {@link #endingGameState},
     * the {@link #eventSequence} it's extracted from,
     * and optional int ({@link #param1}, {@link #param2}, {@link #param3})
     * and resource set ({@link #rset1}, {@link #rset2}) parameters.
     */
    public static class Action extends GameAction
    {
        /**
         * Presumed {@link SOCGame#getGameState()} at end of this action,
         * based on event messages with gameState field.
         */
        public final int endingGameState;

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
         * @see #Action(ActionType, int, List, int, ResourceSet, ResourceSet)
         * @see #Action(ActionType, int, List, int, int, int, int, ResourceSet, ResourceSet)
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
         * @see #Action(ActionType, int, List, int, ResourceSet, ResourceSet)
         * @see #Action(ActionType, int, List, int, int, int, int, ResourceSet, ResourceSet)
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
         * @see #Action(ActionType, int, List, int, int, int, int, ResourceSet, ResourceSet)
         */
        public Action
            (ActionType aType, final int endGState, List<GameEventLog.EventEntry> seq, final int startIndex,
             final ResourceSet rs1, final ResourceSet rs2)
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
         * @see #Action(ActionType, int, List, int, ResourceSet, ResourceSet)
         */
        public Action
            (ActionType aType, final int endGState, List<GameEventLog.EventEntry> seq, final int startIndex,
             final int p1, final int p2, final int p3, final ResourceSet rs1, final ResourceSet rs2)
            throws IllegalArgumentException
        {
            super(aType, p1, p2, p3, rs1, rs2);
            if ((seq == null) || seq.isEmpty())
                throw new IllegalArgumentException("seq");

            endingGameState = endGState;
            eventSequence = seq;
            startingLogIndex = startIndex;
        }

        /**
         * Contents for debugging, formatted like:
         *<BR>
         * Action(ROLL_DICE, endGState=20, p1=12, seq=[f3:SOCRollDice:game=test, all:SOCDiceResult:game=test|param=12, all:SOCGameServerText:game=test|text=No player gets anything., all:SOCGameState:game=test|state=20])
         *<BR>
         * Action(BUILD_PIECE, endGState=20, ...p1=12, seq=[f3:SOPutPiece:game=test|...], cannotUndo=given a Gift Port from the Forgotten Tribe)
         *<P>
         * Includes {@code p1 p2 p3} if non-zero,
         * {@code rs1 rs2} {@link GameAction#cannotUndoReason cannotUndoReason} if non-null.
         */
        @Override
        public String toString()
        {
            // if you update this format, consider also updating soc.game.GameAction.toString()

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
                        sb.append(", p3=").append(param3);
                }
            }
            if ((rset1 != null) || (rset2 != null))
            {
                sb.append(", rs1=").append(rset1);
                if (rset2 != null)
                    sb.append(", rs2=").append(rset2);
            }
            sb.append(", seq=").append(eventSequence);
            if (cannotUndoReason != null)
                sb.append(", cannotUndo=").append(cannotUndoReason);
            sb.append(')');

            return sb.toString();
        }

    }

}
