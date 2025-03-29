/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2022-2025 Jeremy D Monin <jeremy@nand.net>
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

package soc.game;

import java.util.Arrays;
import java.util.List;

import soc.extra.robot.GameActionLog;  // for javadocs only
import soc.extra.server.GameEventLog;  // javadocs only
import soc.message.SOCGameState;  // javadocs only
import soc.message.SOCPlayerElement;  // javadocs only
import soc.message.SOCStartGame;  // javadocs only

/**
 * An action in a game such as placing a piece, trading with the bank, etc.
 * Some actions can be undone, but also check {@link #cannotUndoReason}.
 * Meaning of field values depends on {@link #actType}.
 * See {@link ActionType} for all recognized action types.
 * See {@link #effects} for side effects such as paying resources or changing gameState.
 *<P>
 * To copy a GameAction while changing some fields,
 * use the {@link #GameAction(GameAction, ActionType, int, int, int)} constructor.
 *<P>
 * Before v2.7.00, this was part of {@link GameActionLog.Action}.
 * So, some members may have {@code @since} 2.7.00 or earlier.
 *
 * @see SOCGame#getLastAction()
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.7.00
 */
public class GameAction
{
    public final ActionType actType;

    /** {@link ActionType}-specific int parameter value, or 0. */
    public final int param1, param2, param3;

    /** {@link ActionType}-specific resource set parameter value, or null. */
    public final ResourceSet rset1, rset2;

    /**
     * Side-effects of the action, if any, or {@code null}.
     * Not used when extracting actions from a {@link GameEventLog}.
     * Used only at server, not sent to clients; may contain hidden info about players.
     * @since 2.7.00
     */
    public final List<Effect> effects;
      // TODO add rest of possible construcs

    /**
     * The reason this usually-undoable action can't be undone, or {@code null} when action can be undone.
     * Is an i18n string key at server, localized when sent to client.
     * If can't undo but reason is unknown or unexplained, set to {@code "?"};
     * there is no separate boolean {@code cannotUndo} field.
     * @since 2.7.00
     */
    public volatile String cannotUndoReason;

    // reminder: if you add fields, update equals() and toString()

    /**
     * Create a new GameAction with no parameters.
     * @param aType GameAction type of this action; not null
     * @throws IllegalArgumentException if {@code aType} is null
     * @see #GameAction(ActionType, int, int, int)
     * @see #GameAction(ActionType, ResourceSet, ResourceSet)
     * @see #GameAction(ActionType, int, int, int, ResourceSet, ResourceSet)
     */
    public GameAction
        (ActionType aType)
        throws IllegalArgumentException
    {
        this(aType, 0, 0, 0, null, null);
    }

    /**
     * Create a new GameAction with optional int parameters.
     * @param aType GameAction type of this action; not null
     * @param p1  First action-specific parameter, or 0
     * @param p2  Second action-specific parameter, or 0
     * @param p3  Third action-specific parameter, or 0
     * @throws IllegalArgumentException if {@code aType} is null
     * @see #GameAction(ActionType)
     * @see #GameAction(ActionType, ResourceSet, ResourceSet)
     * @see #GameAction(ActionType, int, int, int, ResourceSet, ResourceSet)
     * @see #GameAction(GameAction, ActionType, int, int, int)
     * @see #GameAction(ActionType, int, int, int, List)
     */
    public GameAction
        (ActionType aType, final int p1, final int p2, final int p3)
        throws IllegalArgumentException
    {
        this(aType, p1, p2, p3, null, null, null);
    }

    /**
     * Create a new GameAction with optional int and {@link Effect} parameters.
     * @param aType GameAction type of this action; not null
     * @param p1  First action-specific parameter, or 0
     * @param p2  Second action-specific parameter, or 0
     * @param p3  Third action-specific parameter, or 0
     * @param effects  Action's effects, or null
     * @throws IllegalArgumentException if {@code aType} is null
     * @see #GameAction(ActionType, int, int, int)
     * @see #GameAction(ActionType, int, int, int, ResourceSet, ResourceSet, List)
     */
    public GameAction
        (ActionType aType, final int p1, final int p2, final int p3, final List<Effect> effects)
        throws IllegalArgumentException
    {
        this(aType, p1, p2, p3, null, null, effects);
    }

    /**
     * Create a new GameAction by copying an existing one, keeping current field values
     * except {@link #actType}, {@link #param1}, {@link #param2}, {@link #param3}.
     * The {@link ResourceSet}s and {@link #effects} are copied by reference, not deep-copied by value.
     * @param copyFrom  GameAction to copy from; not null
     * @param aType  GameAction type of this new action; not null
     * @param p1  First action-specific parameter, or 0
     * @param p2  Second action-specific parameter, or 0
     * @param p3  Third action-specific parameter, or 0
     * @throws IllegalArgumentException if {@code aType} is null
     * @throws NullPointerException if {@code copyFrom} is null
     */
    public GameAction
        (final GameAction copyFrom, ActionType aType, final int p1, final int p2, final int p3)
        throws IllegalArgumentException, NullPointerException
    {
        this(aType, p1, p2, p3, copyFrom.rset1, copyFrom.rset2, copyFrom.effects);
    }

    /**
     * Create a new GameAction with optional resource set parameters.
     * @param aType GameAction type of this action; not null
     * @param rs1  First resource set parameter, or null
     * @param rs2  Second resource set parameter, or null
     * @throws IllegalArgumentException if {@code aType} is null
     * @see #GameAction(ActionType)
     * @see #GameAction(ActionType, int, int, int)
     * @see #GameAction(ActionType, int, int, int, ResourceSet, ResourceSet)
     */
    public GameAction
        (ActionType aType, final ResourceSet rs1, final ResourceSet rs2)
    {
        this(aType, 0, 0, 0, rs1, rs2);
    }

    /**
     * Create a new GameAction with optional int and resource parameters.
     * @param aType GameAction type of this action; not null
     * @param p1  First action-specific parameter, or 0
     * @param p2  Second action-specific parameter, or 0
     * @param p3  Third action-specific parameter, or 0
     * @param rs1  First resource set parameter, or null
     * @param rs2  Second resource set parameter, or null
     * @throws IllegalArgumentException if {@code aType} is null
     * @see #GameAction(ActionType)
     * @see #GameAction(ActionType, int, int, int)
     * @see #GameAction(ActionType, ResourceSet, ResourceSet)
     * @see #GameAction(GameAction, ActionType, int, int, int)
     * @see #GameAction(ActionType, int, int, int, ResourceSet, ResourceSet, List)
     */
    public GameAction
        (ActionType aType, final int p1, final int p2, final int p3, final ResourceSet rs1, final ResourceSet rs2)
        throws IllegalArgumentException
    {
        this(aType, p1, p2, p3, rs1, rs2, null);
    }

    /**
     * Create a new GameAction with optional int, resource, and {@link Effect} parameters.
     * @param aType GameAction type of this action; not null
     * @param p1  First action-specific parameter, or 0
     * @param p2  Second action-specific parameter, or 0
     * @param p3  Third action-specific parameter, or 0
     * @param rs1  First resource set parameter, or null
     * @param rs2  Second resource set parameter, or null
     * @param effects  Action's effects, or null
     * @throws IllegalArgumentException if {@code aType} is null
     * @see #GameAction(ActionType, int, int, int, ResourceSet, ResourceSet)
     */
    public GameAction
        (ActionType aType, final int p1, final int p2, final int p3,
         final ResourceSet rs1, final ResourceSet rs2, final List<Effect> effects)
        throws IllegalArgumentException
    {
        if (aType == null)
            throw new IllegalArgumentException("aType");

        actType = aType;
        param1 = p1;
        param2 = p2;
        param3 = p3;
        rset1 = rs1;
        rset2 = rs2;
        this.effects = effects;
    }

    /**
     * Contents for debugging, formatted like:
     *<pre>
     * GameAction(ROLL_DICE, p1=12)
     * GameAction(BUILD_PIECE, p1=1, p2=3, p3=33, e=[CHANGE_LONGEST_ROAD_PLAYER(1, 3)])
     * GameAction(BUILD_PIECE, p1=1, p2=3, p3=33, e=[CHANGE_LONGEST_ROAD_PLAYER(1, 3)], cannotUndo=example text)
     *</pre>
     *<P>
     * Includes {@code p1 p2 p3} if non-zero, then {@code rs1 rs2} if non-null, then any {@code effects},
     * then any {@link #cannotUndoReason} is last field in case it contains special characters.
     */
    @Override
    public String toString()
    {
        // if you update this format, consider also updating GameActionLog.Action.toString()
        // and SOCSetLastAction.toString()

        StringBuilder sb = new StringBuilder("GameAction(");
        sb.append(actType.toString());
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
        if (effects != null)
            sb.append(", e=").append(effects);  // "e=[A, B, C]"
        if (cannotUndoReason != null)
            sb.append(", cannotUndo=").append(cannotUndoReason);
        sb.append(')');

        return sb.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (! (o instanceof GameAction))
            return false;
        final GameAction oga = (GameAction) o;

        return (actType == oga.actType)
            && (param1 == oga.param1) && (param2 == oga.param2) && (param3 == oga.param3)
            && (rset1 == null ? (oga.rset1 == null) : rset1.equals(oga.rset1))
            && (rset2 == null ? (oga.rset2 == null) : rset2.equals(oga.rset2))
            && ((cannotUndoReason == null)
                ? (oga.cannotUndoReason == null)
                : cannotUndoReason.equals(oga.cannotUndoReason))
            && (effects == null ? (oga.effects == null) : effects.equals(oga.effects));
    }

    /**
     * All recognized {@link GameAction}s.
     * Actions which don't include a player number are done by the current player.
     * Only {@link #TURN_BEGINS} actions change the current player number.
     *<P>
     * To store in a database or send as an int, use {@link #value}.
     * When received or retrieved as int, use {@link #valueOf(int)} to convert to {@link ActionType}.
     *<P>
     * Before v2.7.00, this was part of {@link GameActionLog.Action}.
     * For use there, some {@code ActionType} javadocs refer to log sequences.
     *
     * @since 2.5.00
     */
    public static enum ActionType
    {
        // Declared in same order as in /doc/Message-Sequences-for-Game-Actions.md

        // Original int values are 10 apart, so others can be inserted later if needed.

        /**
         * Enum constant with {@link #ordinal()} 0, declared to prevent 0 being seen as a valid type.
         * @see #UNKNOWN
         */
        UNINITIALIZED(0),

        /**
         * Any sequence that doesn't start with a "sequence start" message type.
         * Also the type to use when converting from int but value is unknown.
         * Note: {@link #valueOf(int)} returns {@code null} and not this value.
         */
        UNKNOWN(1),

        /**
         * Entire contents of log before first turn of initial placement (optional section).
         * Last message in {@link Action#eventSequence} is a {@link SOCStartGame}.
         */
        LOG_START_TO_STARTGAME(10),

        /**
         * Start a player's regular turn or Special Build Phase (SBP), depending on {@link Action#endingGameState}.
         * Current player number has changed.
         *<BR>
         * {@code p1} = new current player number.
         */
        TURN_BEGINS(20),

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
        ROLL_DICE(30),

        /**
         * Build a piece (place a piece).
         *<BR>
         * {@code p1} = piece type like {@link SOCPlayingPiece#SETTLEMENT},
         * {@code p2} = coordinate it was built at,
         * {@code p3} = player number who built the piece; helpful during initial placement.
         * {@code rs1} = free resources gained from revealing any non-gold land hexes from fog; null otherwise
         *<P>
         * When player changes during initial placement,
         * {@link Action#endingGameState} won't change during the {@code BUILD_PIECE},
         * but during the {@link #TURN_BEGINS} action which follows it.
         *<P>
         * At server, {@link GameAction#effects} will include {@link GameAction.EffectType#DEDUCT_COST_FROM_PLAYER}
         * unless the piece was free (road building card, etc).
         *
         * @see #UNDO_BUILD_PIECE
         */
        BUILD_PIECE(40),

        /**
         * Undo previous build/placement of a piece ({@link #BUILD_PIECE}).
         *<BR>
         * {@code p1} = piece type like {@link SOCPlayingPiece#SETTLEMENT},
         * {@code p2} = coordinate it was built at.
         * {@code rs1} = free resources to return, if any; null otherwise
         *<P>
         * Includes any {@link GameAction#effects} of the build action.
         *<P>
         * Undo can happen only during gameState {@link SOCGame#PLAY1 PLAY1}, not initial placement.
         * @since 2.7.00
         */
        UNDO_BUILD_PIECE(45),

        /**
         * Cancel current player's just-built piece (like during initial settlement placement).
         *<BR>
         * {@code p1} = piece type like {@link SOCPlayingPiece#SETTLEMENT},
         * {@code p2} = 0; reserved for coordinate it was built at, in case future extraction can add that,
         * {@code p3} = player number who built and canceled the piece; helpful during initial placement.
         */
        CANCEL_BUILT_PIECE(50),

        /**
         * Move a piece (move a ship).
         *<BR>
         * {@code p1} = piece type like {@link SOCPlayingPiece#SHIP},
         * {@code p2} = coordinate to move from, {@code p3} = coordinate to move to.
         * {@code rs1} = free resources gained from revealing any non-gold land hex from fog; null otherwise
         * @see #UNDO_MOVE_PIECE
         */
        MOVE_PIECE(60),

        /**
         * Undo previous move of a piece ({@link #MOVE_PIECE}).
         *<BR>
         * {@code p1} = piece type like {@link SOCPlayingPiece#SHIP},
         * {@code p2} = coordinate the piece was previously moved to,
         * {@code p3} = coordinate the piece was previously moved from, which is where the undo returns it.
         * {@code rs1} = free resources to return, gained from revealing any non-gold land hex from fog; null otherwise
         *<P>
         * Includes any {@link GameAction#effects} of the move action.
         * @since 2.7.00
         */
        UNDO_MOVE_PIECE(65),

        /**
         * Buy a development card.
         *<BR>
         * {@code p1} = dev card type bought, like {@link SOCDevCardConstants#ROADS}, or 0 if type was hidden.
         * {@code p2} = number of cards remaining in deck after purchase.
         * @see #PLAY_DEV_CARD
         */
        BUY_DEV_CARD(70),

        /**
         * Play a development card.
         *<BR>
         * {@code p1} = dev card type played, like {@link SOCDevCardConstants#ROADS}.
         * {@code p2} = 1st free road's edge coordinate for {@link SOCDevCardConstants#ROADS},
         *     negative if ship, {@link Integer#MAX_VALUE} if canceled; 0 otherwise.
         * {@code p3} = 2nd free road's edge coordinate for {@link SOCDevCardConstants#ROADS},
         *     negative if ship, {@link Integer#MAX_VALUE} if canceled or if player had 1 remaining road/ship
         *     when card played; 0 otherwise.
         * {@code rs1} = resources gained by Year of Plenty/Discovery or Monopoly (if none: null, not empty).
         *<P>
         * For knight/soldier ({@link SOCDevCardConstants#KNIGHT}),
         * see note at {@link #MOVE_ROBBER_OR_PIRATE} about when Largest Army can win the game.
         *<P>
         * If Road Building ({@link SOCDevCardConstants#ROADS}) is cancelled by ending turn,
         * gameState will still be {@link SOCGame#PLACING_FREE_ROAD1} or {@link SOCGame#PLACING_FREE_ROAD2}
         * at end of action; next action will be {@link #END_TURN}.
         *
         * @see #BUY_DEV_CARD
         */
        PLAY_DEV_CARD(80),

        /**
         * Discard resources.
         *<BR>
         * {@code p1} = player number discarding.
         * {@code rs1} = the resources discarded (details if known, otherwise {@link SOCResourceConstants#UNKNOWN}).
         */
        DISCARD(90),

        /**
         * Choose free resources from Gold Hexes, during a dice roll or when revealed in the Fog Hexes scenario.
         *<BR>
         * {@code rs1} = the resources gained.
         */
        CHOOSE_FREE_RESOURCES(100),

        /**
         * Choose whether to move robber or pirate.
         * gameState before action should be {@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE}.
         *<BR>
         * {@code p1} = 1 for robber or 2 for pirate.
         *<P>
         * If extracted from a {@link GameEventLog} with {@link GameEventLog#isAtClient} flag set,
         * choice may be hidden; {@code p1} = 0 if so.
         */
        CHOOSE_MOVE_ROBBER_OR_PIRATE(110),

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
        MOVE_ROBBER_OR_PIRATE(120),

        /**
         * Choose a victim for robbery.
         *<BR>
         * {@code p1} = player number chosen, or -2 if scenario lets the player decline to rob:
         * See {@link SOCGame#WAITING_FOR_ROB_CHOOSE_PLAYER} for info on when that is allowed.
         * Can be -1 if chosen victim isn't visible in sequence.
         */
        CHOOSE_ROBBERY_VICTIM(130),

        /**
         * Choose whether to steal resources or cloth from robbery victim.
         *<BR>
         * {@code p1} = 1 for resources as usual, or 2 for cloth in the Cloth Trade scenario.
         *<P>
         * If extracted from a {@link GameEventLog} with {@link GameEventLog#isAtClient} flag set,
         * choice may be hidden; {@code p1} = 0 if so.
         * Because this action is between server and 1 player client, it may be completely hidden
         * in the {@code isAtClient} log to a different player/observer.
         */
        CHOOSE_ROB_CLOTH_OR_RESOURCE(140),

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
        ROB_PLAYER(150),

        /**
         * Trade with the bank, or undo the previous bank trade.
         *<BR>
         * {@code rs1} = resources given to the bank.
         * {@code rs2} = resources received.
         */
        TRADE_BANK(160),

        /**
         * Make a trade offer to some or all other players.
         *<BR>
         * {@code p1} = player number making the offer.
         * {@code rs1} = resources the offering player would give in the trade.
         * {@code rs2} = resources they would receive from accepting player.
         */
        TRADE_MAKE_OFFER(170),

        /**
         * Clear/cancel player's own trade offer.
         *<BR>
         * {@code p1} = player number canceling their offer.
         */
        TRADE_CLEAR_OFFER(180),

        /**
         * Reject a player's trade offer.
         *<BR>
         * {@code p1} = player number rejecting all offers made to them.
         */
        TRADE_REJECT_OFFER(190),

        /**
         * Accept a player's trade offer.
         *<BR>
         * {@code p1} = player number making the offer.
         * {@code p2} = player number accepting the offer.
         * {@code rs1} = "offered" resources traded to the accepting player from offering player.
         * {@code rs2} = "received" resources traded to the offering player from accepting player.
         */
        TRADE_ACCEPT_OFFER(200),

        /**
         * Ask Special Building during another player's turn.
         *<BR>
         * {@code p1} = player number asking to Special Build.
         */
        ASK_SPECIAL_BUILDING(210),

        /**
         * End player's current turn.
         */
        END_TURN(220),

        /**
         * End of Game, a player has won.
         *<BR>
         * {@code p1} = winning player number.
         */
        GAME_OVER(230);

        /**
         * This enum member's unique int value ({@link #BUILD_PIECE} == 40, etc).
         * @see #valueOf(int)
         */
        public final int value;

        private ActionType(final int v)
        {
            value = v;
        }

        /**
         * Get an ActionType from its int {@link #value}, if type is known.
         * @param ti  Type int value ({@link #BUILD_PIECE} == 40, etc).
         * @return  ActionType for that value, or {@code null} if unknown
         */
        public static ActionType valueOf(final int ti)
        {
            for (ActionType t : values())
                if (t.value == ti)
                    return t;

            return null;
        }
    }

    /**
     * All currently known {@link Effect} types.
     * Except for {@link #CHANGE_GAMESTATE}, none change the game's {@link SOCGame#getGameState()}.
     * @since 2.7.00
     */
    public static enum EffectType
    {
        /**
         * Enum constant with {@link #ordinal()} 0, declared to prevent 0 being seen as a valid type.
         * @see #UNKNOWN
         */
        UNINITIALIZED(0),

        /**
         * The type to use when converting from int but value is unknown.
         * Note: {@link #valueOf(int)} returns {@code null} and not this value.
         */
        UNKNOWN(1),

        /**
         * Player paid resources for this action.
         * Example: Buying and building a road, as opposed to placing a free road from a dev card.
         * If this is for a typical action where the cost is known and constant,
         * like buying and building a standard piece type, don't record the cost: Null params.
         * Otherwise params[] are the cost resource amounts in same format as
         * {@link SOCResourceSet#getAmounts(boolean) SOCResourceSet.getAmounts(false)}.
         */
        DEDUCT_COST_FROM_PLAYER(10),

        /**
         * GameState changed in a notable way.
         * Example: Action was {@link ActionType#BUILD_PIECE} for a free road instead of the usual built road,
         * so the pre-place state was {@link SOCGame#PLACING_FREE_ROAD1} or {@link SOCGame#PLACING_FREE_ROAD2}.
         * (Typical state changes, like for buying and placing a piece, aren't an Effect.)
         *<P>
         * If the action is undone and server sends side-effect data messages to clients,
         * {@code CHANGE_GAMESTATE} is sent last regardless of its position within {@link GameAction#effects}.
         *<P>
         * Params: Old and new {@link SOCGame#getGameState()}.
         */
        CHANGE_GAMESTATE(20),

        /**
         * Player with longest road has changed.
         * Params: old LR playerNumber, new LR playerNumber.
         * Either can be -1 for none.
         */
        CHANGE_LONGEST_ROAD_PLAYER(30),

        /**
         * Player with largest army has changed.
         * Params: old LA playerNumber, new LA playerNumber.
         * Old can be -1 for none.
         */
        CHANGE_LARGEST_ARMY_PLAYER(40),

        /**
         * Player has gained SVP with this action, usually from a {@link SOCPlayerEvent}.
         * Params: Old {@link SOCPlayer#getSpecialVP()}, amount gained, and if from a player event,
         * old {@link SOCPlayer#getPlayerEvents()} and {@link SOCPlayerEvent#flagValue}.
         * <tt>{@link Effect#params}.length</tt> is 2 when not from a player event.
         */
        PLAYER_GAIN_SVP(50),

        /**
         * Player has gained 2 SVP by settling a new landarea (Game option {@link SOCGameOptionSet#K_SC_SEAC _SC_SEAC}):
         * {@link SOCPlayerEvent#SVP_SETTLED_EACH_NEW_LANDAREA}.
         * Params: Old {@link SOCPlayer#getSpecialVP()}, old and new {@link SOCPlayer#getScenarioSVPLandAreas()}.
         */
        PLAYER_GAIN_SETTLED_LANDAREA(60),

        /** The {@link SOCGame#hasBuiltCity()} flag was set by building a piece. Undoing the build should clear it. */
        GAME_SET_HAS_BUILT_CITY_N7C(70),

        /**
         * Building or moving caused a ship route to be closed. Params are {@link SOCShip} edge coords
         * which became closed because of this action: {@link SOCShip#isClosed()}.
         * When undoing this effect, reopen the ship route before moving or un-building the piece.
         */
        CLOSE_SHIP_ROUTE(80),

        /** Building or moving revealed a fog hex. */
        REVEAL_FOG_HEX(90),

        /**
         * Set or clear one or more flags in {@link SOCPlayer#getPlayerEvents()}.
         * If the action changes multiple flags, combine them all into a single {@code PLAYER_SET_EVENT_FLAGS} effect;
         * don't have this effect multiple times within the action.
         *<P>
         * Params:
         *<UL>
         *<LI>[0]: Flag bit value(s) to set or clear, like
         *     <tt>{@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.flagValue</tt>.<BR>
         *     If the event's {@link SOCPlayerEvent#flagValue} is 0, you must use or create a different Effect to record it;
         *     such an event can't be part of {@link SOCPlayer#getPlayerEvents()}
         *     <BR>
         *<LI>[1]: 1 if the action sets them, 0 if clears
         *</UL>
         */
        PLAYER_SET_EVENT_FLAGS(100),

        /**
         * Player has gained a scenario-specific {@link SOCInventoryItem} into their hand/inventory (not a Dev Card),
         * such as a Gift Port in scenario {@link SOCScenario#K_SC_FTRI SC_FTRI}.
         *<P>
         * Params:
         *<LI>[0]: Item type, from {@link SOCInventoryItem#itype}
         *<LI>[1]: 1 if {@link SOCInventoryItem#isPlayable()} currently, 0 otherwise (is either Kept or New)
         *<LI>[2]: 1 if {@link SOCInventoryItem#isKept()}, 0 otherwise
         *<LI>[3]: 1 if {@link SOCInventoryItem#isVPItem()}, 0 otherwise
         *<LI>[4]: 1 if {@link SOCInventoryItem#canCancelPlay}, 0 otherwise
         *</UL>
         */
        PLAYER_GAIN_INVENTORY_ITEM(110),

        /**
         * Player has received cloth for an action such as establishing a Cloth Trade route with
         * a neutral {@link SOCVillage village} in the {@link SOCScenario#K_SC_CLVI Cloth Trade} scenario.
         *<P>
         * Params:
         *<UL>
         *<LI>[0] Amount of cloth received, added to their {@link SOCPlayer#getCloth()}
         *<LI>[1] Node coordinate of village, or 0 if received from board's {@link SOCBoardLarge#getCloth()} General Supply
         *<LI>[2] 1 if was received for establishing Cloth trade, and {@link SOCVillage#addTradingPlayer(SOCPlayer)}
         *        was called; undo should call {@link SOCVillage#removeTradingPlayer(SOCPlayer)}. 0 otherwise
         *</UL>
         * Establishing trade with a village also adds the {@link #CLOSE_SHIP_ROUTE} effect.
         * If this village is player's first time establishing Cloth Trade this game, also sets their
         * {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE} event flag
         * which is noted with {@link #PLAYER_SET_EVENT_FLAGS}.
         *<P>
         * Although this effect could be added when establishing trade with an "empty" village (getCloth == 0), there's
         * no reason to do so: If undoing the action there's no need to call {@link SOCVillage#removeTradingPlayer(SOCPlayer)}
         * because the empty village won't send them cloth in the future. And if that village was the
         * first Cloth Trade established by the player, {@link #PLAYER_SET_EVENT_FLAGS} notes that fact.
         *<P>
         * Villages are in a game only if scenario option {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI} is set.
         */
        PLAYER_SCEN_CLVI_RECEIVE_CLOTH(120),

        /**
         * Player has reached a Special Edge on the board and received a reward,
         * such as {@link SOCBoardLarge#SPECIAL_EDGE_DEV_CARD}.
         *<P>
         * Params:
         *<UL>
         *<LI>[0] Edge coordinate
         *<LI>[1] Special Edge type, from {@link SOCBoardLarge#getSpecialEdgeType(int)}.<BR>
         *    If edge type is {@link SOCBoardLarge#SPECIAL_EDGE_SVP}, they receive 1 SVP
         *    and there won't also be a redundant {@link #PLAYER_GAIN_SVP} effect
         *<LI>[2] When Edge Type is {@link SOCBoardLarge#SPECIAL_EDGE_DEV_CARD}, the card type
         *    from {@link SOCDevCardConstants}
         *</UL>
         * If undoing this action for {@link SOCBoardLarge#SPECIAL_EDGE_DEV_CARD},
         * call player's {@link SOCInventory#removeDevCard(int, int)} and then
         * {@link SOCBoardLarge#putItemInStackRandomly(Integer)} to place the dev card
         * back into the board's item stack.
         *<P>
         * Currently in game only during scenario {@link SOCScenario#K_SC_FTRI SC_FTRI}.
         */
        PLAYER_SCEN_FTRI_REACHED_SPECIAL_EDGE(130),

        /**
         * For scenario {@link SOCScenario#K_SC_FTRI SC_FTRI},
         * current player has removed a "gift" port from a Special Edge.
         * See {@link SOCGame#removePort(SOCPlayer, int)} for details.
         *<P>
         * Params:
         *<UL>
         * <LI> [0] Removed port's edge coordinate
         * <LI> [1] Port type; same value range as {@link SOCBoard#getPortTypeFromNodeCoord(int)}
         *          and {@link SOCGame#placePort(SOCPlayer, int, int)}
         *</UL>
         *
         * If the player must immediately place their gift port,
         * after doing so add the {@link #GAME_SCEN_FTRI_PORT_PLACED} Effect
         * to the same {@link GameAction} as this {@code GAME_SCEN_FTRI_PORT_REMOVED}.
         */
        GAME_SCEN_FTRI_PORT_REMOVED(140),

        /**
         * For scenario {@link SOCScenario#K_SC_FTRI SC_FTRI},
         * current player has placed a "gift" port at a coastal edge.
         * See {@link SOCGame#placePort(SOCPlayer, int, int)} for details.
         *<P>
         * Params:
         *<UL>
         * <LI> [0] Coastal edge coordinate to place at
         * <LI> [1] Port type; same value range as {@link SOCGame#placePort(SOCPlayer, int, int)}
         *</UL>
         *
         * Often follows {@link #GAME_SCEN_FTRI_PORT_REMOVED} within the same {@link GameAction}.
         */
        GAME_SCEN_FTRI_PORT_PLACED(150);

        /**
         * This enum member's unique int value ({@link #CHANGE_LONGEST_ROAD_PLAYER} == 10, etc).
         * @see #valueOf(int)
         */
        public final int value;

        private EffectType(final int v)
        {
            value = v;
        }

        /**
         * Get an EffectType from its int {@link #value}, if type is known.
         * @param ti  Type int value ({@link #CHANGE_LONGEST_ROAD_PLAYER} == 10, etc).
         * @return  EffectType for that value, or {@code null} if unknown
         */
        public static EffectType valueOf(final int ti)
        {
            for (EffectType t : values())
                if (t.value == ti)
                    return t;

            return null;
        }
    }

    /**
     * One side-effect of a specific {@link GameAction}.
     * An action may have multiple {@link GameAction#effects effects}, or none.
     * @see EffectType
     * @since 2.7.00
     */
    public static class Effect
    {
        public final EffectType eType;

        /** {@link EffectType}-specific parameter value(s), or null if none. Never 0-length. */
        public final int[] params;

        /**
         * Construct an Effect with no parameters.
         * @param et  This Effect's EffectType; not null
         * @throws IllegalArgumentException if {@code et} is null
         */
        public Effect(EffectType et)
            throws IllegalArgumentException
        {
            this(et, null);
        }

        /**
         * Construct an Effect, optionally with parameters.
         * @param et  This Effect's EffectType; not null
         * @param p  Any params used with {@code et}, or null; not empty.
         *     Copies the reference to {@code p[]}, not its current contents,
         *     in case the caller doesn't yet have all necessary info while constructing the Effect.
         * @throws IllegalArgumentException if {@code et} is null, or {@code p} is empty but not null
         */
        public Effect(EffectType et, int[] p)
            throws IllegalArgumentException
        {
            if (et == null)
                throw new IllegalArgumentException("eType");
            if ((p != null) && (p.length == 0))
                throw new IllegalArgumentException("params");

            eType = et;
            params = p;
        }

        /**
         * @return a human readable form of this object: {@link #eType}
         *     followed by "(param1, param2, ...)" if any.
         */
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder(eType.toString());
            if (params != null)
            {
                sb.append('(');
                for (int i = 0; i < params.length; ++i)
                {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(params[i]);
                }
                sb.append(')');
            }

            return sb.toString();
        }

        @Override
        public boolean equals(Object o)
        {
            if (! (o instanceof Effect))
                return false;
            final Effect oe = (Effect) o;

            return (eType == oe.eType)
                && Arrays.equals(params, oe.params);
        }
    }

}
