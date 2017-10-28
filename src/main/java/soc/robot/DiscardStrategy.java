/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2008 Christopher McNeil <http://sourceforge.net/users/cmcneil>
 * Portions of this file copyright (C) 2003-2004 Robert S. Thomas
 * Portions of this file copyright (C) 2008 Eli McGowan <http://sourceforge.net/users/emcgowan>
 * Portions of this file copyright (C) 2009,2012-2013,2015 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import java.util.Random;
import java.util.Stack;

// import org.apache.log4j.Logger;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.proto.Data;
import soc.util.SOCRobotParameters;

/**
 * Strategy when we need to discard.
 * For details see javadoc for
 * {@link #discard(int, Stack, Random, SOCPlayer, SOCRobotParameters, SOCRobotDM, SOCRobotNegotiator)}.
 */
public class DiscardStrategy
{

    /** debug logging */
    // private transient Logger log = Logger.getLogger(this.getClass().getName());

    /**
     * When we have to discard, try to keep the resources needed for our building plan.
     * If we don't have a plan, make one by calling {@link SOCRobotDM#planStuff(int)}.
     * Otherwise, discard at random.
     * Calls {@link SOCRobotNegotiator#setTargetPiece(int, SOCPossiblePiece)}
     * to remember the piece we want to build,
     * in case we'll need to trade for its lost resources.
     */
    public static SOCResourceSet discard
        (final int numDiscards, Stack<SOCPossiblePiece> buildingPlan, Random rand,
         SOCPlayer ourPlayerData, SOCRobotParameters robotParameters, SOCRobotDM decisionMaker, SOCRobotNegotiator negotiator)
    {
        //log.debug("DISCARDING...");

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

        if (! buildingPlan.empty())
        {
            SOCPossiblePiece targetPiece = buildingPlan.peek();
            negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), targetPiece);

            //log.debug("targetPiece="+targetPiece);

            final SOCResourceSet targetResources = targetPiece.getResourcesToBuild();
                // may be null

            /**
             * figure out what resources are NOT the ones we need
             */
            SOCResourceSet leftOvers = ourPlayerData.getResources().copy();

            if (targetResources != null)
                for (int rsrc = Data.ResourceType.CLAY_VALUE;
                     rsrc <= Data.ResourceType.WOOD_VALUE; rsrc++)
                    if (leftOvers.getAmount(rsrc) > targetResources.getAmount(rsrc))
                        leftOvers.subtract(targetResources.getAmount(rsrc), rsrc);
                    else
                        leftOvers.setAmount(0, rsrc);

            SOCResourceSet neededRsrcs = ourPlayerData.getResources().copy();
            neededRsrcs.subtract(leftOvers);

            /**
             * figure out the order of resources from
             * easiest to get to hardest
             */

            //log.debug("our numbers="+ourPlayerData.getNumbers());
            final int[] resourceOrder
                = SOCBuildingSpeedEstimate.getRollsForResourcesSorted(ourPlayerData);

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
                    //log.debug("(1) dis.tot="+discards.getTotal()+" curRsrc="+curRsrc);
                    if (leftOvers.contains(resourceOrder[curRsrc]))
                    {
                        discards.add(1, resourceOrder[curRsrc]);
                        leftOvers.subtract(1, resourceOrder[curRsrc]);

                        // keep looping at this resource until finished
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
                    //log.debug("(2) dis.tot="+discards.getTotal()+" curRsrc="+curRsrc);
                    if (neededRsrcs.contains(resourceOrder[curRsrc]))
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
                // log.error("PROBLEM IN DISCARD - curRsrc == 5");
                System.err.println("discardStrategy: PROBLEM IN DISCARD - curRsrc == 5");
            }
        }
        else
        {
            /**
             *  choose discards at random
             */
            SOCGame.discardOrGainPickRandom
                (ourPlayerData.getResources(), numDiscards, true, discards, rand);
        }

        return discards;
    }
}
