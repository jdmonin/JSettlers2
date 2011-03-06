/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.robot;

import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;

import soc.util.CutoffExceededException;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;


/**
 * This class calculates approximately how
 * long it would take a player to build something.
 */
public class SOCBuildingSpeedEstimate
{
    public static final int ROAD = 0;
    public static final int SETTLEMENT = 1;
    public static final int CITY = 2;
    public static final int CARD = 3;
    public static final int MIN = 0;
    public static final int MAXPLUSONE = 4;
    public static final int DEFAULT_ROLL_LIMIT = 40;
    protected static boolean recalc;
    int[] estimatesFromNothing;
    int[] estimatesFromNow;
    int[] rollsPerResource;
    SOCResourceSet[] resourcesForRoll;

    /**
     * this is a constructor
     *
     * @param numbers  the numbers that the player's pieces are touching
     */
    public SOCBuildingSpeedEstimate(SOCPlayerNumbers numbers)
    {
        estimatesFromNothing = new int[MAXPLUSONE];
        estimatesFromNow = new int[MAXPLUSONE];
        rollsPerResource = new int[SOCResourceConstants.WOOD + 1];
        recalculateRollsPerResource(numbers);
        resourcesForRoll = new SOCResourceSet[13];
        recalculateResourcesForRoll(numbers);
    }

    /**
     * this is a constructor
     */
    public SOCBuildingSpeedEstimate()
    {
        estimatesFromNothing = new int[MAXPLUSONE];
        estimatesFromNow = new int[MAXPLUSONE];
        rollsPerResource = new int[SOCResourceConstants.WOOD + 1];
        resourcesForRoll = new SOCResourceSet[13];
    }

    /**
     * @return the estimates from nothing
     *
     * @param ports  the port flags for the player
     */
    public int[] getEstimatesFromNothingAccurate(boolean[] ports)
    {
        if (recalc)
        {
            estimatesFromNothing[ROAD] = DEFAULT_ROLL_LIMIT;
            estimatesFromNothing[SETTLEMENT] = DEFAULT_ROLL_LIMIT;
            estimatesFromNothing[CITY] = DEFAULT_ROLL_LIMIT;
            estimatesFromNothing[CARD] = DEFAULT_ROLL_LIMIT;

            SOCResourceSet emptySet = new SOCResourceSet();

            try
            {
                estimatesFromNothing[ROAD] = calculateRollsAccurate(emptySet, SOCGame.ROAD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[SETTLEMENT] = calculateRollsAccurate(emptySet, SOCGame.SETTLEMENT_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[CITY] = calculateRollsAccurate(emptySet, SOCGame.CITY_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[CARD] = calculateRollsAccurate(emptySet, SOCGame.CARD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            }
            catch (CutoffExceededException e)
            {
                ;
            }
        }

        return estimatesFromNothing;
    }

    /**
     * @return the estimates from nothing
     *
     * @param ports  the port flags for the player
     */
    public int[] getEstimatesFromNothingFast(boolean[] ports)
    {
        if (recalc)
        {
            estimatesFromNothing[ROAD] = DEFAULT_ROLL_LIMIT;
            estimatesFromNothing[SETTLEMENT] = DEFAULT_ROLL_LIMIT;
            estimatesFromNothing[CITY] = DEFAULT_ROLL_LIMIT;
            estimatesFromNothing[CARD] = DEFAULT_ROLL_LIMIT;

            SOCResourceSet emptySet = new SOCResourceSet();

            try
            {
                estimatesFromNothing[ROAD] = calculateRollsFast(emptySet, SOCGame.ROAD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[SETTLEMENT] = calculateRollsFast(emptySet, SOCGame.SETTLEMENT_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[CITY] = calculateRollsFast(emptySet, SOCGame.CITY_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[CARD] = calculateRollsFast(emptySet, SOCGame.CARD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            }
            catch (CutoffExceededException e)
            {
                ;
            }
        }

        return estimatesFromNothing;
    }

    /**
     * @return the estimates from nothing
     *
     * @param ports  the port flags for the player
     */
    public int[] getEstimatesFromNothingFast(boolean[] ports, int limit)
    {
        if (recalc)
        {
            estimatesFromNothing[ROAD] = limit;
            estimatesFromNothing[SETTLEMENT] = limit;
            estimatesFromNothing[CITY] = limit;
            estimatesFromNothing[CARD] = limit;

            SOCResourceSet emptySet = new SOCResourceSet();

            try
            {
                estimatesFromNothing[ROAD] = calculateRollsFast(emptySet, SOCGame.ROAD_SET, limit, ports).getRolls();
                estimatesFromNothing[SETTLEMENT] = calculateRollsFast(emptySet, SOCGame.SETTLEMENT_SET, limit, ports).getRolls();
                estimatesFromNothing[CITY] = calculateRollsFast(emptySet, SOCGame.CITY_SET, limit, ports).getRolls();
                estimatesFromNothing[CARD] = calculateRollsFast(emptySet, SOCGame.CARD_SET, limit, ports).getRolls();
            }
            catch (CutoffExceededException e)
            {
                ;
            }
        }

        return estimatesFromNothing;
    }

    /**
     * @return the estimates from now
     *
     * @param resources  the player's current resources
     * @param ports      the player's port flags
     */
    public int[] getEstimatesFromNowAccurate(SOCResourceSet resources, boolean[] ports)
    {
        estimatesFromNow[ROAD] = DEFAULT_ROLL_LIMIT;
        estimatesFromNow[SETTLEMENT] = DEFAULT_ROLL_LIMIT;
        estimatesFromNow[CITY] = DEFAULT_ROLL_LIMIT;
        estimatesFromNow[CARD] = DEFAULT_ROLL_LIMIT;

        try
        {
            estimatesFromNow[ROAD] = calculateRollsAccurate(resources, SOCGame.ROAD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[SETTLEMENT] = calculateRollsAccurate(resources, SOCGame.SETTLEMENT_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[CITY] = calculateRollsAccurate(resources, SOCGame.CITY_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[CARD] = calculateRollsAccurate(resources, SOCGame.CARD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            ;
        }

        return estimatesFromNow;
    }

    /**
     * @return the estimates from now
     *
     * @param resources  the player's current resources
     * @param ports      the player's port flags
     */
    public int[] getEstimatesFromNowFast(SOCResourceSet resources, boolean[] ports)
    {
        estimatesFromNow[ROAD] = DEFAULT_ROLL_LIMIT;
        estimatesFromNow[SETTLEMENT] = DEFAULT_ROLL_LIMIT;
        estimatesFromNow[CITY] = DEFAULT_ROLL_LIMIT;
        estimatesFromNow[CARD] = DEFAULT_ROLL_LIMIT;

        try
        {
            estimatesFromNow[ROAD] = calculateRollsFast(resources, SOCGame.ROAD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[SETTLEMENT] = calculateRollsFast(resources, SOCGame.SETTLEMENT_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[CITY] = calculateRollsFast(resources, SOCGame.CITY_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[CARD] = calculateRollsFast(resources, SOCGame.CARD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            ;
        }

        return estimatesFromNow;
    }

    /**
     * recalculate both rollsPerResource and resourcesPerRoll
     */
    public void recalculateEstimates(SOCPlayerNumbers numbers)
    {
        recalculateRollsPerResource(numbers);
        recalculateResourcesForRoll(numbers);
    }

    /**
     * recalculate both rollsPerResource and resourcesPerRoll
     * using the robber information
     */
    public void recalculateEstimates(SOCPlayerNumbers numbers, int robberHex)
    {
        recalculateRollsPerResource(numbers, robberHex);
        recalculateResourcesForRoll(numbers, robberHex);
    }

    /**
     * calculate the estimates
     *
     * @param numbers  the numbers that the player is touching
     */
    public void recalculateRollsPerResource(SOCPlayerNumbers numbers)
    {
        //D.ebugPrintln("@@@@@@@@ recalculateRollsPerResource");
        //D.ebugPrintln("@@@@@@@@ numbers = "+numbers);
        recalc = true;

        /**
         * figure out how many resources we get per roll
         */
        for (int resource = SOCResourceConstants.CLAY;
                resource <= SOCResourceConstants.WOOD; resource++)
        {
            //D.ebugPrintln("resource: "+resource);
            float totalProbability = 0.0f;

            Enumeration numbersEnum = numbers.getNumbersForResource(resource).elements();

            while (numbersEnum.hasMoreElements())
            {
                Integer number = (Integer) numbersEnum.nextElement();
                totalProbability += SOCNumberProbabilities.FLOAT_VALUES[number.intValue()];
            }

            //D.ebugPrintln("totalProbability: "+totalProbability);
            if (totalProbability != 0.0f)
            {
                rollsPerResource[resource] = Math.round(1.0f / totalProbability);
            }
            else
            {
                rollsPerResource[resource] = 55555;
            }

            //D.ebugPrintln("rollsPerResource: "+rollsPerResource[resource]);
        }
    }

    /**
     * calculate the estimates assuming that the robber is working
     *
     * @param numbers    the numbers that the player is touching
     * @param robberHex  where the robber is
     */
    public void recalculateRollsPerResource(SOCPlayerNumbers numbers, int robberHex)
    {
        D.ebugPrintln("@@@@@@@@ recalculateRollsPerResource");
        D.ebugPrintln("@@@@@@@@ numbers = " + numbers);
        D.ebugPrintln("@@@@@@@@ robberHex = " + Integer.toHexString(robberHex));
        recalc = true;

        /**
         * figure out how many resources we get per roll
         */
        for (int resource = SOCResourceConstants.CLAY;
                resource <= SOCResourceConstants.WOOD; resource++)
        {
            D.ebugPrintln("resource: " + resource);

            float totalProbability = 0.0f;

            Enumeration numbersEnum = numbers.getNumbersForResource(resource, robberHex).elements();

            while (numbersEnum.hasMoreElements())
            {
                Integer number = (Integer) numbersEnum.nextElement();
                totalProbability += SOCNumberProbabilities.FLOAT_VALUES[number.intValue()];
            }

            D.ebugPrintln("totalProbability: " + totalProbability);

            if (totalProbability != 0.0f)
            {
                rollsPerResource[resource] = Math.round(1.0f / totalProbability);
            }
            else
            {
                rollsPerResource[resource] = 55555;
            }

            D.ebugPrintln("rollsPerResource: " + rollsPerResource[resource]);
        }
    }

    /**
     * calculate what resources this player will get on each
     * die roll
     *
     * @param numbers  the numbers that the player is touching
     */
    public void recalculateResourcesForRoll(SOCPlayerNumbers numbers)
    {
        //D.ebugPrintln("@@@@@@@@ recalculateResourcesForRoll");
        //D.ebugPrintln("@@@@@@@@ numbers = "+numbers);
        recalc = true;

        for (int diceResult = 2; diceResult <= 12; diceResult++)
        {
            Vector resources = numbers.getResourcesForNumber(diceResult);

            if (resources != null)
            {
                SOCResourceSet resourceSet;

                if (resourcesForRoll[diceResult] == null)
                {
                    resourceSet = new SOCResourceSet();
                    resourcesForRoll[diceResult] = resourceSet;
                }
                else
                {
                    resourceSet = resourcesForRoll[diceResult];
                    resourceSet.clear();
                }

                Enumeration resourcesEnum = resources.elements();

                while (resourcesEnum.hasMoreElements())
                {
                    Integer resourceInt = (Integer) resourcesEnum.nextElement();
                    resourceSet.add(1, resourceInt.intValue());
                }

                //D.ebugPrintln("### resources for "+diceResult+" = "+resourceSet);
            }
        }
    }

    /**
     * calculate what resources this player will get on each
     * die roll taking the robber into account
     *
     * @param numbers  the numbers that the player is touching
     */
    public void recalculateResourcesForRoll(SOCPlayerNumbers numbers, int robberHex)
    {
        //D.ebugPrintln("@@@@@@@@ recalculateResourcesForRoll");
        //D.ebugPrintln("@@@@@@@@ numbers = "+numbers);
        //D.ebugPrintln("@@@@@@@@ robberHex = "+Integer.toHexString(robberHex));
        recalc = true;

        for (int diceResult = 2; diceResult <= 12; diceResult++)
        {
            Vector resources = numbers.getResourcesForNumber(diceResult, robberHex);

            if (resources != null)
            {
                SOCResourceSet resourceSet;

                if (resourcesForRoll[diceResult] == null)
                {
                    resourceSet = new SOCResourceSet();
                    resourcesForRoll[diceResult] = resourceSet;
                }
                else
                {
                    resourceSet = resourcesForRoll[diceResult];
                    resourceSet.clear();
                }

                Enumeration resourcesEnum = resources.elements();

                while (resourcesEnum.hasMoreElements())
                {
                    Integer resourceInt = (Integer) resourcesEnum.nextElement();
                    resourceSet.add(1, resourceInt.intValue());
                }

                //D.ebugPrintln("### resources for "+diceResult+" = "+resourceSet);
            }
        }
    }

    /**
     * @return the rolls per resource results
     */
    public int[] getRollsPerResource()
    {
        return rollsPerResource;
    }

    /**
     * this figures out how many rolls it would take this
     * player to get the target set of resources given
     * a starting set
     *
     * @param startingResources   the starting resources
     * @param targetResources     the target resources
     * @param cutoff              throw an exception if the total speed is greater than this
     * @param ports               a list of port flags
     *
     * @return the number of rolls
     */
    protected SOCResSetBuildTimePair calculateRollsFast(SOCResourceSet startingResources, SOCResourceSet targetResources, int cutoff, boolean[] ports) throws CutoffExceededException
    {
        //D.ebugPrintln("calculateRolls");
        //D.ebugPrintln("  start: "+startingResources);
        //D.ebugPrintln("  target: "+targetResources);
        SOCResourceSet ourResources = startingResources.copy();
        int rolls = 0;

        if (!ourResources.contains(targetResources))
        {
            /**
             * do any possible trading with the bank/ports
             */
            for (int giveResource = SOCResourceConstants.CLAY;
                    giveResource <= SOCResourceConstants.WOOD;
                    giveResource++)
            {
                /**
                 * find the ratio at which we can trade
                 */
                int tradeRatio;

                if (ports[giveResource])
                {
                    tradeRatio = 2;
                }
                else if (ports[SOCBoard.MISC_PORT])
                {
                    tradeRatio = 3;
                }
                else
                {
                    tradeRatio = 4;
                }

                /**
                 * get the target resources
                 */
                int numTrades = (ourResources.getAmount(giveResource) - targetResources.getAmount(giveResource)) / tradeRatio;

                //D.ebugPrintln("))) ***");
                //D.ebugPrintln("))) giveResource="+giveResource);
                //D.ebugPrintln("))) tradeRatio="+tradeRatio);
                //D.ebugPrintln("))) ourResources="+ourResources);
                //D.ebugPrintln("))) targetResources="+targetResources);
                //D.ebugPrintln("))) numTrades="+numTrades);
                for (int trades = 0; trades < numTrades; trades++)
                {
                    /**
                     * find the most needed resource by looking at
                     * which of the resources we still need takes the
                     * longest to aquire
                     */
                    int mostNeededResource = -1;

                    for (int resource = SOCResourceConstants.CLAY;
                            resource <= SOCResourceConstants.WOOD;
                            resource++)
                    {
                        if (ourResources.getAmount(resource) < targetResources.getAmount(resource))
                        {
                            if (mostNeededResource < 0)
                            {
                                mostNeededResource = resource;
                            }
                            else
                            {
                                if (rollsPerResource[resource] > rollsPerResource[mostNeededResource])
                                {
                                    mostNeededResource = resource;
                                }
                            }
                        }
                    }

                    /**
                     * make the trade
                     */

                    //D.ebugPrintln("))) want to trade "+tradeRatio+" "+giveResource+" for a "+mostNeededResource);
                    if ((mostNeededResource != -1) && (ourResources.getAmount(giveResource) >= tradeRatio))
                    {
                        //D.ebugPrintln("))) trading...");
                        ourResources.add(1, mostNeededResource);

                        if (ourResources.getAmount(giveResource) < tradeRatio)
                        {
                            System.err.println("@@@ rsrcs=" + ourResources);
                            System.err.println("@@@ tradeRatio=" + tradeRatio);
                            System.err.println("@@@ giveResource=" + giveResource);
                            System.err.println("@@@ target=" + targetResources);
                        }

                        ourResources.subtract(tradeRatio, giveResource);

                        //D.ebugPrintln("))) ourResources="+ourResources);
                    }

                    if (ourResources.contains(targetResources))
                    {
                        break;
                    }
                }

                if (ourResources.contains(targetResources))
                {
                    break;
                }
            }
        }

        while (!ourResources.contains(targetResources))
        {
            //D.ebugPrintln("roll: "+rolls);
            //D.ebugPrintln("resources: "+ourResources);
            rolls++;

            if (rolls > cutoff)
            {
                //D.ebugPrintln("startingResources="+startingResources+"\ntargetResources="+targetResources+"\ncutoff="+cutoff+"\nourResources="+ourResources);
                throw new CutoffExceededException();
            }

            for (int resource = SOCResourceConstants.CLAY;
                    resource <= SOCResourceConstants.WOOD; resource++)
            {
                //D.ebugPrintln("resource: "+resource);
                //D.ebugPrintln("rollsPerResource: "+rollsPerResource[resource]);

                /**
                 * get our resources for the roll
                 */
                if ((rollsPerResource[resource] == 0) || ((rolls % rollsPerResource[resource]) == 0))
                {
                    ourResources.add(1, resource);
                }
            }

            if (!ourResources.contains(targetResources))
            {
                /**
                 * do any possible trading with the bank/ports
                 */
                for (int giveResource = SOCResourceConstants.CLAY;
                        giveResource <= SOCResourceConstants.WOOD;
                        giveResource++)
                {
                    /**
                     * find the ratio at which we can trade
                     */
                    int tradeRatio;

                    if (ports[giveResource])
                    {
                        tradeRatio = 2;
                    }
                    else if (ports[SOCBoard.MISC_PORT])
                    {
                        tradeRatio = 3;
                    }
                    else
                    {
                        tradeRatio = 4;
                    }

                    /**
                     * get the target resources
                     */
                    int numTrades = (ourResources.getAmount(giveResource) - targetResources.getAmount(giveResource)) / tradeRatio;

                    //D.ebugPrintln("))) ***");
                    //D.ebugPrintln("))) giveResource="+giveResource);
                    //D.ebugPrintln("))) tradeRatio="+tradeRatio);
                    //D.ebugPrintln("))) ourResources="+ourResources);
                    //D.ebugPrintln("))) targetResources="+targetResources);
                    //D.ebugPrintln("))) numTrades="+numTrades);
                    for (int trades = 0; trades < numTrades; trades++)
                    {
                        /**
                         * find the most needed resource by looking at
                         * which of the resources we still need takes the
                         * longest to aquire
                         */
                        int mostNeededResource = -1;

                        for (int resource = SOCResourceConstants.CLAY;
                                resource <= SOCResourceConstants.WOOD;
                                resource++)
                        {
                            if (ourResources.getAmount(resource) < targetResources.getAmount(resource))
                            {
                                if (mostNeededResource < 0)
                                {
                                    mostNeededResource = resource;
                                }
                                else
                                {
                                    if (rollsPerResource[resource] > rollsPerResource[mostNeededResource])
                                    {
                                        mostNeededResource = resource;
                                    }
                                }
                            }
                        }

                        /**
                         * make the trade
                         */

                        //D.ebugPrintln("))) want to trade "+tradeRatio+" "+giveResource+" for a "+mostNeededResource);
                        if ((mostNeededResource != -1) && (ourResources.getAmount(giveResource) >= tradeRatio))
                        {
                            //D.ebugPrintln("))) trading...");
                            ourResources.add(1, mostNeededResource);

                            if (ourResources.getAmount(giveResource) < tradeRatio)
                            {
                                System.err.println("@@@ rsrcs=" + ourResources);
                                System.err.println("@@@ tradeRatio=" + tradeRatio);
                                System.err.println("@@@ giveResource=" + giveResource);
                                System.err.println("@@@ target=" + targetResources);
                            }

                            ourResources.subtract(tradeRatio, giveResource);

                            //D.ebugPrintln("))) ourResources="+ourResources);
                        }

                        if (ourResources.contains(targetResources))
                        {
                            break;
                        }
                    }

                    if (ourResources.contains(targetResources))
                    {
                        break;
                    }
                }
            }
        }

        return (new SOCResSetBuildTimePair(ourResources, rolls));
    }

    /**
     * this figures out how many rolls it would take this
     * player to get the target set of resources given
     * a starting set
     *
     * @param startingResources   the starting resources
     * @param targetResources     the target resources
     * @param cutoff              throw an exception if the total speed is greater than this
     * @param ports               a list of port flags
     *
     * @return the number of rolls
     */
    protected SOCResSetBuildTimePair calculateRollsAccurate(SOCResourceSet startingResources, SOCResourceSet targetResources, int cutoff, boolean[] ports) throws CutoffExceededException
    {
        D.ebugPrintln("calculateRollsAccurate");
        D.ebugPrintln("  start: " + startingResources);
        D.ebugPrintln("  target: " + targetResources);

        SOCResourceSet ourResources = startingResources.copy();
        int rolls = 0;
        Hashtable[] resourcesOnRoll = new Hashtable[2];
        resourcesOnRoll[0] = new Hashtable();
        resourcesOnRoll[1] = new Hashtable();

        int lastRoll = 0;
        int thisRoll = 1;

        resourcesOnRoll[lastRoll].put(ourResources, new Float(1.0));

        boolean targetReached = ourResources.contains(targetResources);
        SOCResourceSet targetReachedResources = null;
        float targetReachedProb = (float) 0.0;

        while (!targetReached)
        {
            if (D.ebugOn)
            {
                D.ebugPrintln("roll: " + rolls);
                D.ebugPrintln("resourcesOnRoll[lastRoll]:");

                Enumeration roltEnum = resourcesOnRoll[lastRoll].keys();

                while (roltEnum.hasMoreElements())
                {
                    SOCResourceSet rs = (SOCResourceSet) roltEnum.nextElement();
                    Float prob = (Float) resourcesOnRoll[lastRoll].get(rs);
                    D.ebugPrintln("---- prob:" + prob);
                    D.ebugPrintln("---- rsrcs:" + rs);
                    D.ebugPrintln();
                }

                D.ebugPrintln("targetReachedProb: " + targetReachedProb);
                D.ebugPrintln("===================================");
            }

            rolls++;

            if (rolls > cutoff)
            {
                D.ebugPrintln("startingResources=" + startingResources + "\ntargetResources=" + targetResources + "\ncutoff=" + cutoff + "\nourResources=" + ourResources);
                throw new CutoffExceededException();
            }

            //
            //  get our resources for the roll
            //
            for (int diceResult = 2; diceResult <= 12; diceResult++)
            {
                SOCResourceSet gainedResources = resourcesForRoll[diceResult];
                float diceProb = SOCNumberProbabilities.FLOAT_VALUES[diceResult];

                //
                //  add the resources that we get on this roll to 
                //  each set of resources that we got on the last
                //  roll and multiply the probabilities
                //
                Enumeration lastResourcesEnum = resourcesOnRoll[lastRoll].keys();

                while (lastResourcesEnum.hasMoreElements())
                {
                    SOCResourceSet lastResources = (SOCResourceSet) lastResourcesEnum.nextElement();
                    Float lastProb = (Float) resourcesOnRoll[lastRoll].get(lastResources);
                    SOCResourceSet newResources = lastResources.copy();
                    newResources.add(gainedResources);

                    float newProb = lastProb.floatValue() * diceProb;

                    if (!newResources.contains(targetResources))
                    {
                        //
                        // do any possible trading with the bank/ports
                        //
                        for (int giveResource = SOCResourceConstants.CLAY;
                                giveResource <= SOCResourceConstants.WOOD;
                                giveResource++)
                        {
                            if ((newResources.getAmount(giveResource) - targetResources.getAmount(giveResource)) > 1)
                            {
                                //
                                // find the ratio at which we can trade
                                //
                                int tradeRatio;

                                if (ports[giveResource])
                                {
                                    tradeRatio = 2;
                                }
                                else if (ports[SOCBoard.MISC_PORT])
                                {
                                    tradeRatio = 3;
                                }
                                else
                                {
                                    tradeRatio = 4;
                                }

                                //
                                // get the target resources
                                //
                                int numTrades = (newResources.getAmount(giveResource) - targetResources.getAmount(giveResource)) / tradeRatio;

                                //D.ebugPrintln("))) ***");
                                //D.ebugPrintln("))) giveResource="+giveResource);
                                //D.ebugPrintln("))) tradeRatio="+tradeRatio);
                                //D.ebugPrintln("))) newResources="+newResources);
                                //D.ebugPrintln("))) targetResources="+targetResources);
                                //D.ebugPrintln("))) numTrades="+numTrades);
                                for (int trades = 0; trades < numTrades;
                                        trades++)
                                {
                                    // 
                                    // find the most needed resource by looking at 
                                    // which of the resources we still need takes the
                                    // longest to aquire
                                    //
                                    int mostNeededResource = -1;

                                    for (int resource = SOCResourceConstants.CLAY;
                                            resource <= SOCResourceConstants.WOOD;
                                            resource++)
                                    {
                                        if (newResources.getAmount(resource) < targetResources.getAmount(resource))
                                        {
                                            if (mostNeededResource < 0)
                                            {
                                                mostNeededResource = resource;
                                            }
                                            else
                                            {
                                                if (rollsPerResource[resource] > rollsPerResource[mostNeededResource])
                                                {
                                                    mostNeededResource = resource;
                                                }
                                            }
                                        }
                                    }

                                    //
                                    // make the trade
                                    //
                                    //D.ebugPrintln("))) want to trade "+tradeRatio+" "+giveResource+" for a "+mostNeededResource);
                                    if ((mostNeededResource != -1) && (newResources.getAmount(giveResource) >= tradeRatio))
                                    {
                                        //D.ebugPrintln("))) trading...");
                                        newResources.add(1, mostNeededResource);

                                        if (newResources.getAmount(giveResource) < tradeRatio)
                                        {
                                            System.err.println("@@@ rsrcs=" + newResources);
                                            System.err.println("@@@ tradeRatio=" + tradeRatio);
                                            System.err.println("@@@ giveResource=" + giveResource);
                                            System.err.println("@@@ target=" + targetResources);
                                        }

                                        newResources.subtract(tradeRatio, giveResource);

                                        //D.ebugPrintln("))) newResources="+newResources);
                                    }

                                    if (newResources.contains(targetResources))
                                    {
                                        break;
                                    }
                                }

                                if (newResources.contains(targetResources))
                                {
                                    break;
                                }
                            }
                        }
                    }

                    //
                    //  if this set of resources is already in the list
                    //  of possible outcomes, add this probability to
                    //  that one, else just add this to the list
                    //
                    Float probFloat = (Float) resourcesOnRoll[thisRoll].get(newResources);
                    float newProb2 = newProb;

                    if (probFloat != null)
                    {
                        newProb2 = probFloat.floatValue() + newProb;
                    }

                    //
                    //  check to see if we reached our target
                    //
                    if (newResources.contains(targetResources))
                    {
                        D.ebugPrintln("-----> TARGET HIT *");
                        D.ebugPrintln("newResources: " + newResources);
                        D.ebugPrintln("newProb: " + newProb);
                        targetReachedProb += newProb;

                        if (targetReachedResources == null)
                        {
                            targetReachedResources = newResources;
                        }

                        if (targetReachedProb >= 0.5)
                        {
                            targetReached = true;
                        }
                    }
                    else
                    {
                        resourcesOnRoll[thisRoll].put(newResources, new Float(newProb2));
                    }
                }
            }

            //
            //  copy the resourcesOnRoll[thisRoll] table to the
            //  resourcesOnRoll[lastRoll] table and clear the
            //  resourcesOnRoll[thisRoll] table
            //
            int tmp = lastRoll;
            lastRoll = thisRoll;
            thisRoll = tmp;
            resourcesOnRoll[thisRoll].clear();
        }

        if (D.ebugOn)
        {
            float probSum = (float) 0.0;
            D.ebugPrintln("**************** TARGET REACHED ************");
            D.ebugPrintln("targetReachedResources: " + targetReachedResources);
            D.ebugPrintln("targetReachedProb: " + targetReachedProb);
            D.ebugPrintln("roll: " + rolls);
            D.ebugPrintln("resourcesOnRoll[lastRoll]:");

            Enumeration roltEnum = resourcesOnRoll[lastRoll].keys();

            while (roltEnum.hasMoreElements())
            {
                SOCResourceSet rs = (SOCResourceSet) roltEnum.nextElement();
                Float prob = (Float) resourcesOnRoll[lastRoll].get(rs);
                probSum += prob.floatValue();
                D.ebugPrintln("---- prob:" + prob);
                D.ebugPrintln("---- rsrcs:" + rs);
                D.ebugPrintln();
            }

            D.ebugPrintln("probSum = " + probSum);
            D.ebugPrintln("===================================");
        }

        return (new SOCResSetBuildTimePair(targetReachedResources, rolls));
    }
}
