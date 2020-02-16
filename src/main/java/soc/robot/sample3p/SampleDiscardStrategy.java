/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.robot.sample3p;

import java.util.Random;
import java.util.Stack;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.proto.Data;
import soc.robot.DiscardStrategy;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCRobotBrain;

/**
 * Trivial sample discard strategy: Not recommended for actual use!
 * For details see {@link #discard(int, Stack)}.
 * @since 2.2.00
 */
public class SampleDiscardStrategy extends DiscardStrategy
{

    /**
     * Create a SampleDiscardStrategy for a {@link SOCRobotBrain}'s player.
     * @param ga  Our game
     * @param pl  Our player data in {@code ga}
     * @param br  Robot brain for {@code pl}
     * @param rand  Random number generator from {code #br}
     */
    public SampleDiscardStrategy(SOCGame ga, SOCPlayer pl, SOCRobotBrain br, Random rand)
    {
        super(ga, pl, br, rand);
    }

    /**
     * Trivial sample discard strategy: Not recommended for actual use!
     * Discard clay if in hand. Otherwise use standard strategy.
     *
     * @param numDiscards  Required number of discards
     * @param buildingPlan  Brain's current building plan
     * @return  Resources to discard, which should be a subset of
     *     {@code ourPlayerData}.{@link SOCPlayer#getResources() getResources()}
     */
    @Override
    public SOCResourceSet discard
        (final int numDiscards, Stack<SOCPossiblePiece> buildingPlan)
    {
        SOCResourceSet currRes = ourPlayerData.getResources().copy();

        if (currRes.getAmount(Data.ResourceType.CLAY_VALUE) >= numDiscards)
        {
            SOCResourceSet discards = new SOCResourceSet();
            discards.add(numDiscards, Data.ResourceType.CLAY_VALUE);
            return discards;
        } else {
            return super.discard(numDiscards, buildingPlan);
        }
    }

}
