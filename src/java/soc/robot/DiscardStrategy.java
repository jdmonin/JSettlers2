package soc.robot;

import java.util.Random;
import java.util.Stack;

// import org.apache.log4j.Logger;

import soc.disableDebug.D;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.util.SOCRobotParameters;

public class DiscardStrategy {

	/** debug logging */
    // private transient Logger log = Logger.getLogger(this.getClass().getName());

	public SOCResourceSet discard(int numDiscards, Stack buildingPlan, SOCPlayer ourPlayerData, SOCRobotParameters robotParameters, SOCRobotDM decisionMaker, SOCRobotNegotiator negotiator){
		Random rand = new Random();
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

        if (!buildingPlan.empty())
        {
            SOCPossiblePiece targetPiece = (SOCPossiblePiece) buildingPlan.peek();
            negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), targetPiece);

            //log.debug("targetPiece="+targetPiece);
            SOCResourceSet targetResources = null;

            switch (targetPiece.getType())
            {
            case SOCPossiblePiece.CARD:
                targetResources = SOCGame.CARD_SET;

                break;

            case SOCPlayingPiece.ROAD:
                targetResources = SOCGame.ROAD_SET;

                break;

            case SOCPlayingPiece.SETTLEMENT:
                targetResources = SOCGame.SETTLEMENT_SET;

                break;

            case SOCPlayingPiece.CITY:
                targetResources = SOCGame.CITY_SET;

                break;
            }

            /**
             * figure out what resources are NOT the ones we need
             */
            SOCResourceSet leftOvers = ourPlayerData.getResources().copy();

            for (int rsrc = SOCResourceConstants.CLAY;
                    rsrc <= SOCResourceConstants.WOOD; rsrc++)
            {
                if (leftOvers.getAmount(rsrc) > targetResources.getAmount(rsrc))
                {
                    leftOvers.subtract(targetResources.getAmount(rsrc), rsrc);
                }
                else
                {
                    leftOvers.setAmount(0, rsrc);
                }
            }

            SOCResourceSet neededRsrcs = ourPlayerData.getResources().copy();
            neededRsrcs.subtract(leftOvers);

            /**
             * figure out the order of resources from
             * easiest to get to hardest
             */

            //log.debug("our numbers="+ourPlayerData.getNumbers());
            SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());
            int[] rollsPerResource = estimate.getRollsPerResource();
            int[] resourceOrder = 
            {
                SOCResourceConstants.CLAY, SOCResourceConstants.ORE,
                SOCResourceConstants.SHEEP, SOCResourceConstants.WHEAT,
                SOCResourceConstants.WOOD
            };

            for (int j = 4; j >= 0; j--)
            {
                for (int i = 0; i < j; i++)
                {
                    if (rollsPerResource[resourceOrder[i]] < rollsPerResource[resourceOrder[i + 1]])
                    {
                        int tmp = resourceOrder[i];
                        resourceOrder[i] = resourceOrder[i + 1];
                        resourceOrder[i + 1] = tmp;
                    }
                }
            }

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
                    if (leftOvers.getAmount(resourceOrder[curRsrc]) > 0)
                    {
                        discards.add(1, resourceOrder[curRsrc]);
                        leftOvers.subtract(1, resourceOrder[curRsrc]);
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
                    if (neededRsrcs.getAmount(resourceOrder[curRsrc]) > 0)
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
                D.ebugPrintln("discardStrategy: PROBLEM IN DISCARD - curRsrc == 5");
            }
        }
        else
        {
            /**
             *  choose discards at random
             */
            SOCGame.discardPickRandom(ourPlayerData.getResources(), numDiscards, discards, rand);
        }
        
        return discards;
	}
}
