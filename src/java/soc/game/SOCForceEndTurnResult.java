/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008,2010 Jeremy D Monin <jeremy@nand.net>
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



/**
 * This class holds the results of a call to {@link SOCGame#forceEndTurn()}.
 * Specifically, the resulting action type, and possibly list of discarded
 * or returned resources.
 *<P>
 * <tt>forceEndTurn()</tt> may also set the game state to {@link SOCGame#OVER}.
 * Check for that; it's not reported as part of this object.
 *<P>
 * The result object isn't intended to be conveyed over a network to clients; the server
 * should translate it into standard SOCMessages which change game state.
 */
public class SOCForceEndTurnResult
{
    /** Result type, like {@link #FORCE_ENDTURN_NONE} */
    private int result;

    /**
     * Resources gained (returned to cancel piece placement) or lost (discarded), or null.
     * Lost resources are negative values in this set.
     */
    private SOCResourceSet gainLoss;

    /**
     * If true, player's resources are lost (discarded), not gained (returned).
     */
    private boolean rsrcLoss;

    /**
     * Development card type re-gained, or -1;
     * from constants such as {@link SOCDevCardConstants#DISC}.
     */
    private int devCardType;

    /**
     * If true, game's {@link SOCGame#getFirstPlayer()} was changed.
     * @since 1.1.09
     */
    private boolean updatedFP;

    /**
     * If true, game's lastPlayer was changed.
     * Calling {@link SOCGame#setFirstPlayer(int)} will also calculate
     * its lastPlayer based on vacant seats and firstPlayer.
     * @since 1.1.09
     */
    private boolean updatedLP;

    /**
     * {@link SOCGame#forceEndTurn()} return values. FORCE_ENDTURN_MIN is the lowest valid value.
     */
    public static final int FORCE_ENDTURN_MIN               = 1;  // Lowest possible

    /** Since state is already {@link SOCGame#PLAY1}, already OK to end turn. No action was taken by forceEndTurn. */
    public static final int FORCE_ENDTURN_NONE              = 1;

    /**
     * Skip an initial road or settlement; current player has advanced forward, state changes to {@link SOCGame#START1A}.
     * May have changed game's firstPlayer or lastPlayer; check {@link #didUpdateFP()} and {@link #didUpdateLP()}.
     */
    public static final int FORCE_ENDTURN_SKIP_START_ADV    = 2;

    /**
     * Skip an initial road or settlement; current player has advanced backward, state changes to {@link SOCGame#START2A}.
     * May have changed game's firstPlayer or lastPlayer; check {@link #didUpdateFP()} and {@link #didUpdateLP()}.
     */
    public static final int FORCE_ENDTURN_SKIP_START_ADVBACK = 3;

    /** Skip an initial road or settlement; state changes to {@link SOCGame#PLAY1}, and {@link SOCGame#endTurn()} should be called. */
    public static final int FORCE_ENDTURN_SKIP_START_TURN   = 4;

    /** Sent both for placement of bought pieces, and for "free" pieces from road-building cards */
    public static final int FORCE_ENDTURN_RSRC_RET_UNPLACE  = 5;

    /** Robber movement has been cancelled. */
    public static final int FORCE_ENDTURN_UNPLACE_ROBBER    = 6;

    /** Resources have been randomly discarded. Ready to end turn. */
    public static final int FORCE_ENDTURN_RSRC_DISCARD      = 7;

    /** Resources have been randomly discarded. Cannot end turn yet; other players must discard. {@link SOCGame#isForcingEndTurn()} is set. */
    public static final int FORCE_ENDTURN_RSRC_DISCARD_WAIT = 8;

    /** Choice lost; a development card may be returned to hand, see {@link #getDevCardType()}. */
    public static final int FORCE_ENDTURN_LOST_CHOICE       = 9;

    /** Highest valid FORCE_ENDTURN_ value for {@link SOCGame#forceEndTurn()} */
    public static final int FORCE_ENDTURN_MAX               = 9;  // Highest possible

    /**
     * Creates a new SOCForceEndTurnResult object, no resources gained/lost.
     *
     * @param res Result type, from constants in this class
     *            ({@link #FORCE_ENDTURN_UNPLACE_ROBBER}, etc.)
     * @throws IllegalArgumentException If res is not in the range
     *            {@link #FORCE_ENDTURN_MIN} to {@link #FORCE_ENDTURN_MAX}.
     */
    public SOCForceEndTurnResult(int res)
    {
        this(res, null, false, false, false);
    }

    /**
     * Creates a new SOCForceEndTurnResult object, from start states, possibly changing the game's firstplayer or lastplayer.
     *
     * @param res Result type, from constants in this class
     *            ({@link #FORCE_ENDTURN_UNPLACE_ROBBER}, etc.)
     * @param updateFirstPlayer Was {@link SOCGame#getFirstPlayer()} changed?
     * @param updateLastPlayer  Was game's lastPlayer changed?
     * @throws IllegalArgumentException If res is not in the range
     *            {@link #FORCE_ENDTURN_MIN} to {@link #FORCE_ENDTURN_MAX}.
     * @since 1.1.09
     */
    public SOCForceEndTurnResult(int res, final boolean updateFirstPlayer, final boolean updateLastPlayer)
    {
        this(res, null, false, updateFirstPlayer, updateLastPlayer);
    }

    /**
     * Creates a new SOCForceEndTurnResult object, with a development card regained.
     *
     * @param res Result type, from constants in this class
     *            ({@link #FORCE_ENDTURN_UNPLACE_ROBBER}, etc.)
     * @param dtype Development card type, like {@link SOCDevCardConstants#DISC}, or -1 for none.
     * @throws IllegalArgumentException If res is not in the range
     *            {@link #FORCE_ENDTURN_MIN} to {@link #FORCE_ENDTURN_MAX},
     *            or if dtype is not -1 and not in the range
     *            {@link SOCDevCardConstants#MIN_KNOWN} to {@link SOCDevCardConstants#MAXPLUSONE}.
     */
    public SOCForceEndTurnResult(int res, int dtype)
    {
        this(res);
        if ( ((dtype < SOCDevCardConstants.MIN_KNOWN) || (dtype >= SOCDevCardConstants.MAXPLUSONE))
            && (dtype != -1) )
            throw new IllegalArgumentException("dtype out of range: " + dtype);
        devCardType = dtype;
    }

    /**
     * Creates a new SOCForceEndTurnResult object, with resources gained.
     *
     * @param res Result type, from constants in this class
     *            ({@link #FORCE_ENDTURN_UNPLACE_ROBBER}, etc.)
     * @param gained Resources gained (returned to cancel piece
     *            placement), or null.
     * @throws IllegalArgumentException If res is not in the range
     *            {@link #FORCE_ENDTURN_MIN} to {@link #FORCE_ENDTURN_MAX}.
     */
    public SOCForceEndTurnResult(int res, SOCResourceSet gained)
    {
        this(res, gained, false, false, false);
    }

    /**
     * Creates a new SOCForceEndTurnResult object, with resources gained/lost.
     *
     * @param res Result type, from constants in this class
     *            ({@link #FORCE_ENDTURN_UNPLACE_ROBBER}, etc.)
     * @param gainedLost Resources gained (returned to cancel piece
     *            placement) or lost (discarded), or null.
     * @param isLoss     Resources are lost (discarded), not gained (returned to player).
     * @throws IllegalArgumentException If res is not in the range
     *            {@link #FORCE_ENDTURN_MIN} to {@link #FORCE_ENDTURN_MAX}.
     */
    public SOCForceEndTurnResult(int res, SOCResourceSet gainedLost, boolean isLoss)
    {
        this(res, gainedLost, isLoss, false, false);
    }

    /**
     * Creates a new SOCForceEndTurnResult object.
     *
     * @param res Result type, from constants in this class
     *            ({@link #FORCE_ENDTURN_UNPLACE_ROBBER}, etc.)
     * @param gainedLost Resources gained (returned to cancel piece
     *            placement) or lost (discarded), or null.
     * @param isLoss     Resources are lost (discarded), not gained (returned to player).
     * @param updateFirstPlayer Was {@link SOCGame#getFirstPlayer()} changed?
     * @param updateLastPlayer  Was game's lastPlayer changed?
     * @throws IllegalArgumentException If res is not in the range
     *            {@link #FORCE_ENDTURN_MIN} to {@link #FORCE_ENDTURN_MAX}.
     */
    private SOCForceEndTurnResult(int res, SOCResourceSet gainedLost, boolean isLoss,
        final boolean updateFirstPlayer, final boolean updateLastPlayer)
    {
        if ((res < FORCE_ENDTURN_MIN) || (res > FORCE_ENDTURN_MAX))
            throw new IllegalArgumentException("res out of range: " + res);

        result = res;
        gainLoss = gainedLost;
        rsrcLoss = isLoss;
        updatedFP = updateFirstPlayer;
        updatedLP = updateLastPlayer;
        devCardType = -1;
    }

    /**
     * Get the force result type.
     * @return Result type, from constants in this class
     *            ({@link #FORCE_ENDTURN_UNPLACE_ROBBER}, etc.)
     */
    public int getResult()
    {
        return result;
    }

    /**
     * Get the resources gained (returned to cancel piece
     * placement) or lost (discarded), if any.
     * Lost resources are signaled by {@link #isLoss()}.
     *
     * @return gained or lost resources, or null
     */
    public SOCResourceSet getResourcesGainedLost()
    {
        return gainLoss;
    }

    /**
     * Is player losing, or gaining, the resources of
     * {@link #getResourcesGainedLost()}?
     *
     * @return true if resource loss, false if gain, for the player
     */
    public boolean isLoss()
    {
        return rsrcLoss; 
    }

    /**
     * Did the game's {@link SOCGame#getFirstPlayer()} change?
     * @since 1.1.09
     */
    public boolean didUpdateFP()
    {
        return updatedFP;
    }

    /**
     * Did the game's lastPlayer change?
     * Calling {@link SOCGame#setFirstPlayer(int)} will also calculate
     * its lastPlayer based on vacant seats and firstPlayer.
     * @since 1.1.09
     */
    public boolean didUpdateLP()
    {
        return updatedLP;
    }

    /**
     * Is a development card being returned to the player's hand?
     *
     * @return Development card to return, or -1; type constants
     *         like {@link SOCDevCardConstants#DISC}.
     */
    public int getDevCardType()
    {
        return devCardType;
    }
}
