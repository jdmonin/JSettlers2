/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008 Jeremy Monin <jeremy@nand.net>
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



/**
 * This class holds the results of a call to {@link SOCGame#forceEndTurn()}.
 * Specifically, the resulting action type, and possibly list of discarded
 * or returned resources.
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
     * {@link SOCGame#forceEndTurn()} return values. FORCE_ENDTURN_MIN is the lowest valid value.
     */
    public static final int FORCE_ENDTURN_MIN               = 1;  // Lowest possible

    /** Since state is already {@link SOCGame#PLAY1}, already OK to end turn. No action was taken by forceEndTurn. */
    public static final int FORCE_ENDTURN_NONE              = 1;

    /** Skip an initial road or settlement; current player has advanced forward, state changes to {@link SOCGame#START1A}. */
    public static final int FORCE_ENDTURN_SKIP_START_ADV    = 2;

    /** Skip an initial road or settlement; current player has advanced backward, state changes to {@link SOCGame#START2A}. */
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
        this(res, null, false);
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
     *            {@link SOCDevCardConstants#MIN} to {@link SOCDevCardConstants#MAX_KNOWN}.
     */
    public SOCForceEndTurnResult(int res, int dtype)
    {
        this(res);
        if ( ((dtype < SOCDevCardConstants.MIN) || (dtype > SOCDevCardConstants.MAX_KNOWN))
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
        this(res, gained, false);
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
        if ((res < FORCE_ENDTURN_MIN) || (res > FORCE_ENDTURN_MAX))
            throw new IllegalArgumentException("res out of range: " + res);

        result = res;
        gainLoss = gainedLost;
        rsrcLoss = isLoss;
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
