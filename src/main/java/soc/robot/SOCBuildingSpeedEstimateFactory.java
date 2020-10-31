/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCResourceConstants;


/**
 * Factory to build {@link SOCBuildingSpeedEstimate}s for use by
 * {@link SOCRobotBrain}, {@link SOCRobotDM}, etc.
 * Also has {@link #getRollsForResourcesSorted(SOCPlayer)}
 * so that static {@code SOCBuildingSpeedEstimate} method can be called or overridden.
 *<P>
 * This basic factory always constructs a basic {@link SOCBuildingSpeedEstimate}.
 * Third-party bots can override as needed, along with {@link SOCRobotBrain#createEstimatorFactory()}.
 *
 * @since 2.4.50
 */
public class SOCBuildingSpeedEstimateFactory
{

    /**
     * Construct a basic {@link SOCBuildingSpeedEstimateFactory}, optionally for use by {@code brain}.
     * @param brain  Brain which will use this factory, or {@code null}.
     *     Default implementation ignores {@code brain} parameter; it's provided in case a subclass needs it.
     */
    public SOCBuildingSpeedEstimateFactory(final SOCRobotBrain brain)
    {
    }

    /**
     * Factory method for when a player's dice numbers are known.
     * @param numbers the current resources in hand of the player we are estimating for,
     *     in same format passed into {@link SOCBuildingSpeedEstimate#SOCBuildingSpeedEstimate(SOCPlayerNumbers)}
     * @return an estimate of time to build something, based on {@code numbers}
     * @see #getEstimator()
     */
    public SOCBuildingSpeedEstimate getEstimator(final SOCPlayerNumbers numbers)
    {
        return new SOCBuildingSpeedEstimate(numbers);
    }

    /**
     * Factory method for when a player's dice numbers are unknown or don't matter yet.
     * @return an estimate of time to build something, which doesn't consider player's dice numbers yet;
     *     to do so, will need to call {@link SOCBuildingSpeedEstimate#recalculateEstimates(SOCPlayerNumbers, int)}
     * @see #getEstimator(SOCPlayerNumbers)
     */
    public SOCBuildingSpeedEstimate getEstimator()
    {
        return new SOCBuildingSpeedEstimate();
    }

    /**
     * Estimate the rolls for this player to obtain each resource.
     * Default implementation calls {@link SOCBuildingSpeedEstimate#getRollsForResourcesSorted(SOCPlayer)}
     * which will construct and use a {@link SOCBuildingSpeedEstimate}
     * from {@link SOCPlayer#getNumbers() pl.getNumbers()}.
     * @param pl  Player to check numbers
     * @return  Resource order, sorted by rolls per resource descending;
     *        a 5-element array containing
     *        {@link SOCResourceConstants#CLAY},
     *        {@link SOCResourceConstants#WHEAT}, etc,
     *        where the resource type constant in [0] has the highest rolls per resource.
     */
    public final int[] getRollsForResourcesSorted(final SOCPlayer pl)
    {
        return SOCBuildingSpeedEstimate.getRollsForResourcesSorted(pl);
    }

}
