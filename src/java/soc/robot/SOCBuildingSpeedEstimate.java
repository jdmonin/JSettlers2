/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file copyright (C) 2012-2013,2015 Jeremy D Monin <jeremy@nand.net>
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

import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
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
 * Uses {@link SOCPlayerNumbers} to get resources of currently reached hexes.
 */
public class SOCBuildingSpeedEstimate
{
    public static final int ROAD = 0;
    public static final int SETTLEMENT = 1;
    public static final int CITY = 2;
    public static final int CARD = 3;
    public static final int SHIP = 4;
    public static final int MIN = 0;
    public static final int MAXPLUSONE = 5;
    public static final int DEFAULT_ROLL_LIMIT = 40;
    protected static boolean recalc;
    int[] estimatesFromNothing;
    int[] estimatesFromNow;

    /**
     * Number of rolls to gain each resource type ({@link SOCResourceConstants#CLAY}
     * to {@link SOCResourceConstants#WOOD}).
     * Index 0 is unused.
     *<P>
     * Does not contain {@link soc.game.SOCBoardLarge#GOLD_HEX GOLD_HEX}
     * or {@link SOCResourceConstants#GOLD_LOCAL},
     * because {@link SOCPlayerNumbers} methods translate the gold hexes into
     * each of the normal 5 resource types.
     */
    private int[] rollsPerResource;

    /**
     * Resource sets gained for each dice roll number (2 to 12).
     * Indexes 0 and 1 are unused.
     *<P>
     * Does not contain {@link soc.game.SOCBoardLarge#GOLD_HEX GOLD_HEX}
     * or {@link SOCResourceConstants#GOLD_LOCAL},
     * because {@link SOCPlayerNumbers} methods translate each gold hex number
     * into 1 resource of each of the normal 5 types.
     */
    private SOCResourceSet[] resourcesForRoll;

    /**
     * Create a new SOCBuildingSpeedEstimate, calculating
     * the rollsPerResource and resourcesPerRoll based on
     * the player's dice numbers (settlement/city hexes).
     *
     * @param numbers  the numbers that the player's pieces are touching
     */
    public SOCBuildingSpeedEstimate(SOCPlayerNumbers numbers)
    {
        estimatesFromNothing = new int[MAXPLUSONE];
        estimatesFromNow = new int[MAXPLUSONE];
        rollsPerResource = new int[SOCResourceConstants.WOOD + 1];
        recalculateRollsPerResource(numbers, -1);
        resourcesForRoll = new SOCResourceSet[13];
        recalculateResourcesForRoll(numbers, -1);
    }

    /**
     * Create a new SOCBuildingSpeedEstimate, not yet calculating
     * estimates.  To consider the player's dice numbers (settlement/city hexes),
     * you'll need to call {@link #recalculateEstimates(SOCPlayerNumbers, int)}.
     */
    public SOCBuildingSpeedEstimate()
    {
        estimatesFromNothing = new int[MAXPLUSONE];
        estimatesFromNow = new int[MAXPLUSONE];
        rollsPerResource = new int[SOCResourceConstants.WOOD + 1];
        resourcesForRoll = new SOCResourceSet[13];
    }

    /**
     * Estimate the rolls for this player to obtain each resource.
     * Will construct a <tt>SOCBuildingSpeedEstimate</tt>
     * from {@link SOCPlayer#getNumbers() pl.getNumbers()},
     * and call {@link #getRollsPerResource()}.
     * @param pl  Player to check numbers
     * @return  Resource order, sorted by rolls per resource descending;
     *        a 5-element array containing
     *        {@link SOCResourceConstants#CLAY},
     *        {@link SOCResourceConstants#WHEAT}, etc,
     *        where the resource in [0] has the highest rolls per resource.
     * @since 2.0.00
     */
    public static final int[] getRollsForResourcesSorted(final SOCPlayer pl)
    {
        SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(pl.getNumbers());
        final int[] rollsPerResource = estimate.getRollsPerResource();
        int[] resourceOrder =
        {
            SOCResourceConstants.CLAY, SOCResourceConstants.ORE,
            SOCResourceConstants.SHEEP, SOCResourceConstants.WHEAT,
            SOCResourceConstants.WOOD
        };

        // Sort descending; resourceOrder[0] will have the highest rollsPerResource.
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

        return resourceOrder;
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
            estimatesFromNothing[SHIP] = DEFAULT_ROLL_LIMIT;

            SOCResourceSet emptySet = new SOCResourceSet();

            try
            {
                estimatesFromNothing[ROAD] = calculateRollsAccurate(emptySet, SOCGame.ROAD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[SETTLEMENT] = calculateRollsAccurate(emptySet, SOCGame.SETTLEMENT_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[CITY] = calculateRollsAccurate(emptySet, SOCGame.CITY_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[CARD] = calculateRollsAccurate(emptySet, SOCGame.CARD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[SHIP] = calculateRollsAccurate(emptySet, SOCGame.SHIP_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
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
            estimatesFromNothing[SHIP] = DEFAULT_ROLL_LIMIT;

            SOCResourceSet emptySet = new SOCResourceSet();

            try
            {
                estimatesFromNothing[ROAD] = calculateRollsAndRsrcFast(emptySet, SOCGame.ROAD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[SETTLEMENT] = calculateRollsAndRsrcFast(emptySet, SOCGame.SETTLEMENT_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[CITY] = calculateRollsAndRsrcFast(emptySet, SOCGame.CITY_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[CARD] = calculateRollsAndRsrcFast(emptySet, SOCGame.CARD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[SHIP] = calculateRollsAndRsrcFast(emptySet, SOCGame.SHIP_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
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
            estimatesFromNothing[SHIP] = limit;

            SOCResourceSet emptySet = new SOCResourceSet();

            try
            {
                estimatesFromNothing[ROAD] = calculateRollsAndRsrcFast(emptySet, SOCGame.ROAD_SET, limit, ports).getRolls();
                estimatesFromNothing[SETTLEMENT] = calculateRollsAndRsrcFast(emptySet, SOCGame.SETTLEMENT_SET, limit, ports).getRolls();
                estimatesFromNothing[CITY] = calculateRollsAndRsrcFast(emptySet, SOCGame.CITY_SET, limit, ports).getRolls();
                estimatesFromNothing[CARD] = calculateRollsAndRsrcFast(emptySet, SOCGame.CARD_SET, limit, ports).getRolls();
                estimatesFromNothing[SHIP] = calculateRollsAndRsrcFast(emptySet, SOCGame.SHIP_SET, limit, ports).getRolls();
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
        estimatesFromNow[SHIP] = DEFAULT_ROLL_LIMIT;

        try
        {
            estimatesFromNow[ROAD] = calculateRollsAccurate(resources, SOCGame.ROAD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[SETTLEMENT] = calculateRollsAccurate(resources, SOCGame.SETTLEMENT_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[CITY] = calculateRollsAccurate(resources, SOCGame.CITY_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[CARD] = calculateRollsAccurate(resources, SOCGame.CARD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[SHIP] = calculateRollsAccurate(resources, SOCGame.SHIP_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
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
        estimatesFromNow[SHIP] = DEFAULT_ROLL_LIMIT;

        try
        {
            estimatesFromNow[ROAD] = calculateRollsAndRsrcFast(resources, SOCGame.ROAD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[SETTLEMENT] = calculateRollsAndRsrcFast(resources, SOCGame.SETTLEMENT_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[CITY] = calculateRollsAndRsrcFast(resources, SOCGame.CITY_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[CARD] = calculateRollsAndRsrcFast(resources, SOCGame.CARD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[SHIP] = calculateRollsAndRsrcFast(resources, SOCGame.SHIP_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            ;
        }

        return estimatesFromNow;
    }

    /**
     * recalculate both rollsPerResource and resourcesPerRoll
     * @param numbers    the numbers that the player is touching
     * @see #recalculateEstimates(SOCPlayerNumbers, int)
     */
    public void recalculateEstimates(SOCPlayerNumbers numbers)
    {
        recalculateRollsPerResource(numbers, -1);
        recalculateResourcesForRoll(numbers, -1);
    }

    /**
     * Recalculate both rollsPerResource and resourcesPerRoll,
     * optionally considering the robber's location.
     * @param numbers    the numbers that the player is touching
     * @param robberHex  Robber location from {@link SOCBoard#getRobberHex()},
     *                     or -1 to ignore the robber
     * @see #recalculateEstimates(SOCPlayerNumbers)
     */
    public void recalculateEstimates(SOCPlayerNumbers numbers, int robberHex)
    {
        recalculateRollsPerResource(numbers, robberHex);
        recalculateResourcesForRoll(numbers, robberHex);
    }

    /**
     * Calculate the rollsPerResource estimates,
     * optionally considering the robber's location.
     *
     * @param numbers    the numbers that the player is touching
     * @param robberHex  Robber location from {@link SOCBoard#getRobberHex()},
     *                     or -1 to ignore the robber
     */
    public void recalculateRollsPerResource(SOCPlayerNumbers numbers, final int robberHex)
    {
        //D.ebugPrintln("@@@@@@@@ recalculateRollsPerResource");
        //D.ebugPrintln("@@@@@@@@ numbers = " + numbers);
        //D.ebugPrintln("@@@@@@@@ robberHex = " + Integer.toHexString(robberHex));
        recalc = true;

        /**
         * figure out how many resources we get per roll
         */
        for (int resource = SOCResourceConstants.CLAY;
                resource <= SOCResourceConstants.WOOD; resource++)
        {
            //D.ebugPrintln("resource: " + resource);

            float totalProbability = 0.0f;

            Enumeration<Integer> numbersEnum =
                ((robberHex != -1)
                   ? numbers.getNumbersForResource(resource, robberHex)
                   : numbers.getNumbersForResource(resource)
                 ).elements();

            while (numbersEnum.hasMoreElements())
            {
                Integer number = numbersEnum.nextElement();
                totalProbability += SOCNumberProbabilities.FLOAT_VALUES[number.intValue()];
            }

            //D.ebugPrintln("totalProbability: " + totalProbability);

            if (totalProbability != 0.0f)
            {
                rollsPerResource[resource] = Math.round(1.0f / totalProbability);
            }
            else
            {
                rollsPerResource[resource] = 55555;
            }

            //D.ebugPrintln("rollsPerResource: " + rollsPerResource[resource]);
        }
    }

    /**
     * Calculate what resources this player will get on each
     * die roll, optionally taking the robber into account.
     *
     * @param numbers  the numbers that the player is touching
     * @param robberHex  Robber location from {@link SOCBoard#getRobberHex()},
     *                     or -1 to ignore the robber
     */
    public void recalculateResourcesForRoll(SOCPlayerNumbers numbers, final int robberHex)
    {
        //D.ebugPrintln("@@@@@@@@ recalculateResourcesForRoll");
        //D.ebugPrintln("@@@@@@@@ numbers = "+numbers);
        //D.ebugPrintln("@@@@@@@@ robberHex = "+Integer.toHexString(robberHex));
        recalc = true;

        for (int diceResult = 2; diceResult <= 12; diceResult++)
        {
            Vector<Integer> resources = (robberHex != -1)
                ? numbers.getResourcesForNumber(diceResult, robberHex)
                : numbers.getResourcesForNumber(diceResult);

            if (resources != null)
            {
                SOCResourceSet resourceSet = resourcesForRoll[diceResult];

                if (resourceSet == null)
                {
                    resourceSet = new SOCResourceSet();
                    resourcesForRoll[diceResult] = resourceSet;
                }
                else
                {
                    resourceSet.clear();
                }

                Enumeration<Integer> resourcesEnum = resources.elements();

                while (resourcesEnum.hasMoreElements())
                {
                    Integer resourceInt = resourcesEnum.nextElement();
                    resourceSet.add(1, resourceInt.intValue());
                }

                //D.ebugPrintln("### resources for "+diceResult+" = "+resourceSet);
            }
        }
    }

    /**
     * Get the number of rolls to gain each resource type ({@link SOCResourceConstants#CLAY}
     * to {@link SOCResourceConstants#WOOD}).
     *<P>
     * Does not contain {@link soc.game.SOCBoardLarge#GOLD_HEX GOLD_HEX}
     * or {@link SOCResourceConstants#GOLD_LOCAL},
     * because {@link SOCPlayerNumbers} methods translate the gold hexes into
     * each of the normal 5 resource types.
     *
     * @return the rolls per resource results; index 0 is unused.
     */
    public int[] getRollsPerResource()
    {
        return rollsPerResource;
    }

    /**
     * Figures out how many rolls it would take this
     * player to get the target set of resources, given
     * a starting set.
     *<P>
     * This method does the same calculation as
     * {@link #calculateRollsAndRsrcFast(SOCResourceSet, SOCResourceSet, int, boolean[])}
     * with a simpler return type and no thrown exception.
     *
     * @param startingResources   the starting resources
     * @param targetResources     the target resources
     * @param cutoff              maximum number of rolls
     * @param ports               a list of port flags
     *
     * @return  the number of rolls, or {@code cutoff} if that maximum is reached.
     *     If {@link SOCResourceSet#contains(SOCResourceSet) startingResources.contains(targetResources)},
     *     returns 0.
     * @since 2.0.00
     */
    protected final int calculateRollsFast
        (final SOCResourceSet startingResources, final SOCResourceSet targetResources, final int cutoff, final boolean[] ports)
    {
        try
        {
            SOCResSetBuildTimePair pair = calculateRollsAndRsrcFast(startingResources, targetResources, cutoff, ports);
            return pair.getRolls();
        }
        catch (CutoffExceededException e)
        {
            return cutoff;
        }
    }

    /**
     * this figures out how many rolls it would take this
     * player to get the target set of resources given
     * a starting set
     *<P>
     * Before v2.0.00, this was {@code calculateRollsFast}.
     *
     * @param startingResources   the starting resources
     * @param targetResources     the target resources
     * @param cutoff              throw an exception if the total speed is greater than this
     * @param ports               a list of port flags
     *
     * @return the number of rolls, and startingResources after any trading.
     *     If {@link SOCResourceSet#contains(SOCResourceSet) startingResources.contains(targetResources)},
     *     returns 0 rolls and a copy of {@code startingResources} with identical amounts.
     * @throws CutoffExceededException  if total number of rolls &gt; {@code cutoff}
     * @see #calculateRollsFast(SOCResourceSet, SOCResourceSet, int, boolean[])
     */
    protected SOCResSetBuildTimePair calculateRollsAndRsrcFast
        (final SOCResourceSet startingResources, final SOCResourceSet targetResources, final int cutoff, final boolean[] ports)
        throws CutoffExceededException
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
     * @return the number of rolls and our resources when the target is reached.
     *    If {@link SOCResourceSet#contains(SOCResourceSet) startingResources.contains(targetResources)},
     *    returns 0 rolls and a {@code null} resource set.
     * @throws CutoffExceededException if estimate more than {@code cutoff} turns to obtain {@code targetResources}
     */
    protected SOCResSetBuildTimePair calculateRollsAccurate
        (SOCResourceSet startingResources, SOCResourceSet targetResources, int cutoff, boolean[] ports)
        throws CutoffExceededException
    {
        D.ebugPrintln("calculateRollsAccurate");
        D.ebugPrintln("  start: " + startingResources);
        D.ebugPrintln("  target: " + targetResources);

        SOCResourceSet ourResources = startingResources.copy();
        int rolls = 0;

        @SuppressWarnings("unchecked")
        Hashtable<SOCResourceSet, Float>[] resourcesOnRoll = new Hashtable[2];
        resourcesOnRoll[0] = new Hashtable<SOCResourceSet, Float>();
        resourcesOnRoll[1] = new Hashtable<SOCResourceSet, Float>();

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

                Enumeration<SOCResourceSet> roltEnum = resourcesOnRoll[lastRoll].keys();

                while (roltEnum.hasMoreElements())
                {
                    SOCResourceSet rs = roltEnum.nextElement();
                    Float prob = resourcesOnRoll[lastRoll].get(rs);
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
                Enumeration<SOCResourceSet> lastResourcesEnum = resourcesOnRoll[lastRoll].keys();

                while (lastResourcesEnum.hasMoreElements())
                {
                    SOCResourceSet lastResources = lastResourcesEnum.nextElement();
                    Float lastProb = resourcesOnRoll[lastRoll].get(lastResources);
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
                    Float probFloat = resourcesOnRoll[thisRoll].get(newResources);
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

            Enumeration<SOCResourceSet> roltEnum = resourcesOnRoll[lastRoll].keys();

            while (roltEnum.hasMoreElements())
            {
                SOCResourceSet rs = roltEnum.nextElement();
                Float prob = resourcesOnRoll[lastRoll].get(rs);
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
