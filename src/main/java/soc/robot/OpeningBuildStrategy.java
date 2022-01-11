/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2008 Eli McGowan <http://sourceforge.net/users/emcgowan>
 * Portions of this file copyright (C) 2003-2004 Robert S. Thomas
 * Portions of this file copyright (C) 2008 Christopher McNeil <http://sourceforge.net/users/cmcneil>
 * Portions of this file copyright (C) 2009-2013,2017-2021 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

// import org.apache.log4j.Logger;

import soc.disableDebug.D;
import soc.game.*;
import soc.util.CutoffExceededException;

/**
 * This class is a temporary class put in place to slowly pull tasks out of SOCRobotBrain
 * and start replacing them with classes that implement strategy interfaces. (Strategy Pattern)
 * @author Eli McGowan
 *
 */
public class OpeningBuildStrategy {

    /** Our game */
    protected final SOCGame game;

    /** Our {@link SOCRobotBrain}'s player */
    protected final SOCPlayer ourPlayerData;

    /**
     * Our {@link SOCBuildingSpeedEstimate} factory, from {@link #ourPlayerData}'s brain passed into constructor.
     * @since 2.5.00
     */
    protected final SOCBuildingSpeedEstimateFactory bseFactory;

    /** debug logging */
    // private transient Logger log = Logger.getLogger(this.getClass().getName());
    protected transient D log = new D();

    /**
     * used in planning where to put our first and second settlements
     */
    protected int firstSettlement;

    /**
     * used in planning where to put our first and second settlements
     */
    protected int secondSettlement;

    /**
     * Coordinate of a future settlement 2 nodes away from settlementNode
     * (from {@link #firstSettlement} or {@link #secondSettlement}).
     * Valid after calling {@link #planInitRoad()}.
     * @since 2.0.00
     */
    protected int plannedRoadDestinationNode;

    /**
     * Cached resource estimates for the board;
     * <tt>resourceEstimates</tt>[{@link SOCBoard#CLAY_HEX}] == the clay rarity,
     * as an integer percentage 0-100 of dice rolls.
     * Initialized in {@link #estimateResourceRarity()}.
     */
    protected int[] resourceEstimates;

    /**
     * Create an OpeningBuildStrategy for a {@link SOCRobotBrain}'s or {@link SOCRobotDM}'s player.
     * @param ga  Our game
     * @param pl  Our player data in {@code ga}
     * @param br  Robot brain for {@code pl} if available, or null
     */
    public OpeningBuildStrategy(SOCGame ga, SOCPlayer pl, SOCRobotBrain br)
    {
        if (pl == null)
            throw new IllegalArgumentException();

        game = ga;
        ourPlayerData = pl;
        bseFactory = (br != null)
            ? br.getEstimatorFactory()
            : new SOCBuildingSpeedEstimateFactory(null);
    }

    /**
     * Callback from {@link SOCRobotBrain#cancelWrongPiecePlacement(soc.message.SOCCancelBuildRequest)}
     * In case this OBS wants to take any other action to prevent re-sending the cancelled piece.
     * Game state will still be the state in which this piece's placement was attempted:
     * {@link SOCGame#START1A}, {@link SOCGame#START2B}, etc.
     * Any overriders should call {@code super.cancelWrongPiecePlacement(..)}.
     *<P>
     * In versions before 2.5.00, if an initial road was cancelled, bot would call
     * {@link SOCPlayer#clearPotentialSettlement(int) ourPlayerData.clearPotentialSettlement(nodeCoord)}
     * on the future-planned settlement node we were aiming for,
     * stored in this OBS's {@code plannedRoadDestinationNode} field.
     * That action might not apply to every third-party bot's OBS.
     *
     * @param cancelPiece  Playing piece type and coordinate which were rejected by server; not null
     * @since 2.5.00
     */
    public void cancelWrongPiecePlacement(final SOCPlayingPiece cancelPiece)
    {
        if (cancelPiece instanceof SOCRoutePiece)
        {
            // needed for planInitRoad() calculations
            if (plannedRoadDestinationNode > 0)
                ourPlayerData.clearPotentialSettlement(plannedRoadDestinationNode);
        }
    }

    /**
     * figure out where to place the two settlements
     * @return {@link #firstSettlement}, or 0 if no potential settlements for our player
     */
    public int planInitialSettlements()
    {
        log.debug("--- planInitialSettlements");

        int speed;
        boolean allTheWay;
        firstSettlement = 0;
        secondSettlement = 0;

        int bestSpeed = 4 * SOCBuildingSpeedEstimate.DEFAULT_ROLL_LIMIT;
        SOCBoard board = game.getBoard();
        SOCPlayerNumbers playerNumbers = new SOCPlayerNumbers(board);
        int probTotal;
        int bestProbTotal;
        boolean[] ports = new boolean[SOCBoard.WOOD_PORT + 1];
        SOCBuildingSpeedEstimate estimate = bseFactory.getEstimator();
        final int[] prob = SOCNumberProbabilities.INT_VALUES;

        bestProbTotal = 0;

        final int[] ourPotentialSettlements = ourPlayerData.getPotentialSettlements_arr();
        if (ourPotentialSettlements == null)
            return 0;  // Should not occur

        for (int i = 0; i < ourPotentialSettlements.length; ++i)
        {
            final int firstNode = ourPotentialSettlements[i];
            // assert: ourPlayerData.isPotentialSettlement(firstNode)

            final Integer firstNodeInt = Integer.valueOf(firstNode);

            //
            // this is just for testing purposes
            //
            log.debug("FIRST NODE -----------");
            log.debug("firstNode = " + board.nodeCoordToString(firstNode));

            StringBuffer sb = new StringBuffer();
            sb.append("numbers:[");

            playerNumbers.clear();
            probTotal = playerNumbers.updateNumbersAndProbability
                (firstNode, board, prob, sb);

            sb.append("]");
            log.debug(sb.toString());
            sb = new StringBuffer();
            sb.append("ports: ");

            for (int portType = SOCBoard.MISC_PORT;
                     portType <= SOCBoard.WOOD_PORT; portType++)
            {
                ports[portType] = (board.getPortCoordinates(portType).contains(firstNodeInt));

                sb.append(ports[portType] + "  ");
            }

            log.debug(sb.toString());
            log.debug("probTotal = " + probTotal);
            estimate.recalculateEstimates(playerNumbers);
            speed = 0;
            allTheWay = false;

            try
            {
                speed += estimate.calculateRollsAndRsrcFast(SOCResourceSet.EMPTY_SET, SOCSettlement.COST, 300, ports).getRolls();
                speed += estimate.calculateRollsAndRsrcFast(SOCResourceSet.EMPTY_SET, SOCCity.COST, 300, ports).getRolls();
                speed += estimate.calculateRollsAndRsrcFast(SOCResourceSet.EMPTY_SET, SOCDevCard.COST, 300, ports).getRolls();
                speed += estimate.calculateRollsAndRsrcFast(SOCResourceSet.EMPTY_SET, SOCRoad.COST, 300, ports).getRolls();
            }
            catch (CutoffExceededException e) {}

            if (D.ebugOn)
            {
                final int[] rolls = estimate.getEstimatesFromNothingFast(ports, 300);
                sb = new StringBuffer();
                sb.append(" road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                sb.append(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                sb.append(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                sb.append(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                log.debug(sb.toString());
                log.debug("speed = " + speed);
            }

            //
            // end test
            //

            //
            // calculate pairs of first and second settlement together
            //

            for (int j = 1 + i; j < ourPotentialSettlements.length; ++j)
            {
                final int secondNode = ourPotentialSettlements[j];
                // assert: ourPlayerData.isPotentialSettlement(secondNode)

                if (board.isNodeAdjacentToNode(secondNode, firstNode))
                    continue;  // <-- too close to firstNode to build --

                log.debug("firstNode = " + board.nodeCoordToString(firstNode));
                log.debug("secondNode = " + board.nodeCoordToString(secondNode));

                /**
                 * get the numbers for these settlements
                 */
                sb = new StringBuffer();
                sb.append("numbers:[");

                playerNumbers.clear();
                probTotal = playerNumbers.updateNumbersAndProbability
                    (firstNode, board, prob, sb);

                sb.append("] [");

                probTotal += playerNumbers.updateNumbersAndProbability
                    (secondNode, board, prob, sb);

                sb.append("]");
                log.debug(sb.toString());

                /**
                 * see if the settlements are on any ports
                 */
                //sb = new StringBuffer();
                //sb.append("ports: ");

                Arrays.fill(ports, false);
                int portType = board.getPortTypeFromNodeCoord(firstNode);
                if (portType != -1)
                    ports[portType] = true;
                portType = board.getPortTypeFromNodeCoord(secondNode);
                if (portType != -1)
                    ports[portType] = true;

                //log.debug(sb.toString());
                log.debug("probTotal = " + probTotal);

                /**
                 * estimate the building speed for this pair
                 */
                estimate.recalculateEstimates(playerNumbers);
                speed = 0;
                allTheWay = false;

                try
                {
                    speed += estimate.calculateRollsAndRsrcFast
                        (SOCResourceSet.EMPTY_SET, SOCSettlement.COST, bestSpeed, ports).getRolls();

                    if (speed < bestSpeed)
                    {
                        speed += estimate.calculateRollsAndRsrcFast
                            (SOCResourceSet.EMPTY_SET, SOCCity.COST, bestSpeed, ports).getRolls();

                        if (speed < bestSpeed)
                        {
                            speed += estimate.calculateRollsAndRsrcFast
                                (SOCResourceSet.EMPTY_SET, SOCDevCard.COST, bestSpeed, ports).getRolls();

                            if (speed < bestSpeed)
                            {
                                speed += estimate.calculateRollsAndRsrcFast
                                    (SOCResourceSet.EMPTY_SET, SOCRoad.COST, bestSpeed, ports).getRolls();
                                allTheWay = true;
                            }
                        }
                    }

                    // because of addition, speed might be as much as (bestSpeed - 1) + bestSpeed
                }
                catch (CutoffExceededException e)
                {
                    speed = bestSpeed;
                }

                if (D.ebugOn)
                {
                    final int[] rolls = estimate.getEstimatesFromNothingFast(ports, bestSpeed);
                    sb = new StringBuffer();
                    sb.append(" road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                    sb.append(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                    sb.append(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                    sb.append(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                    log.debug(sb.toString());
                    log.debug("allTheWay = " + allTheWay);
                    log.debug("speed = " + speed);
                }

                /**
                 * keep the settlements with the best speed
                 */
                if (speed < bestSpeed)
                {
                    firstSettlement = firstNode;
                    secondSettlement = secondNode;
                    bestSpeed = speed;
                    bestProbTotal = probTotal;
                    log.debug("bestSpeed = " + bestSpeed);
                    log.debug("bestProbTotal = " + bestProbTotal);
                }
                else if ((speed == bestSpeed) && allTheWay)
                {
                    if (probTotal > bestProbTotal)
                    {
                        log.debug("Equal speed, better prob");
                        firstSettlement = firstNode;
                        secondSettlement = secondNode;
                        bestSpeed = speed;
                        bestProbTotal = probTotal;
                        log.debug("firstSettlement = " + Integer.toHexString(firstSettlement));
                        log.debug("secondSettlement = " + Integer.toHexString(secondSettlement));
                        log.debug("bestSpeed = " + bestSpeed);
                        log.debug("bestProbTotal = " + bestProbTotal);
                    }
                }

            }  // for (j past i in ourPotentialSettlements[])

        }  // for (i in ourPotentialSettlements[])

        /**
         * choose which settlement to place first
         */
        playerNumbers.clear();
        playerNumbers.updateNumbers(firstSettlement, board);

        final Integer firstSettlementInt = Integer.valueOf(firstSettlement);

        for (int portType = SOCBoard.MISC_PORT; portType <= SOCBoard.WOOD_PORT;
                 portType++)
        {
            ports[portType] = (board.getPortCoordinates(portType).contains(firstSettlementInt));
        }

        estimate.recalculateEstimates(playerNumbers);

        int firstSpeed = 0;
        final int cutoff = 100;

        firstSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCSettlement.COST, cutoff, ports);
        firstSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCCity.COST, cutoff, ports);
        firstSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCDevCard.COST, cutoff, ports);
        firstSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCRoad.COST, cutoff, ports);

        playerNumbers.clear();
        playerNumbers.updateNumbers(secondSettlement, board);

        final Integer secondSettlementInt = Integer.valueOf(secondSettlement);

        for (int portType = SOCBoard.MISC_PORT; portType <= SOCBoard.WOOD_PORT;
                 portType++)
        {
            ports[portType] = (board.getPortCoordinates(portType).contains(secondSettlementInt));
        }

        estimate.recalculateEstimates(playerNumbers);

        int secondSpeed = 0;

        secondSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCSettlement.COST, bestSpeed, ports);
        secondSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCCity.COST, bestSpeed, ports);
        secondSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCDevCard.COST, bestSpeed, ports);
        secondSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCRoad.COST, bestSpeed, ports);

        if (firstSpeed > secondSpeed)
        {
            int tmp = firstSettlement;
            firstSettlement = secondSettlement;
            secondSettlement = tmp;
        }

        log.debug
            (board.nodeCoordToString(firstSettlement) + ":" + firstSpeed + ", "
             + board.nodeCoordToString(secondSettlement) + ":" + secondSpeed);

        return firstSettlement;
    }

    /**
     * figure out where to place the second settlement
     * @return {@link #secondSettlement}, or -1 if none
     */
    public int planSecondSettlement()
    {
        log.debug("--- planSecondSettlement");

        int bestSpeed = 4 * SOCBuildingSpeedEstimate.DEFAULT_ROLL_LIMIT;
        final SOCBoard board = game.getBoard();
        SOCPlayerNumbers playerNumbers = new SOCPlayerNumbers(board);
        boolean[] ports = new boolean[SOCBoard.WOOD_PORT + 1];
        SOCBuildingSpeedEstimate estimate = bseFactory.getEstimator();
        int probTotal;
        int bestProbTotal;
        final int[] prob = SOCNumberProbabilities.INT_VALUES;
        final int firstNode = firstSettlement;

        bestProbTotal = 0;
        secondSettlement = -1;

        final int[] ourPotentialSettlements = ourPlayerData.getPotentialSettlements_arr();
        if (ourPotentialSettlements == null)
            return -1;  // Should not occur

        for (int i = 0; i < ourPotentialSettlements.length; ++i)
        {
            final int secondNode = ourPotentialSettlements[i];
            // assert: ourPlayerData.isPotentialSettlement(secondNode)

            if (board.isNodeAdjacentToNode(secondNode, firstNode))
                continue;  // <-- too close to firstNode to build --

            /**
             * get the numbers for these settlements
             */
            StringBuffer sb = new StringBuffer();
            sb.append("numbers: ");
            playerNumbers.clear();
            probTotal = playerNumbers.updateNumbersAndProbability
                (firstNode, board, prob, sb);
            probTotal += playerNumbers.updateNumbersAndProbability
                (secondNode, board, prob, sb);

            /**
             * see if the settlements are on any ports
             */
            //sb.append("ports: ");

            Arrays.fill(ports, false);
            int portType = board.getPortTypeFromNodeCoord(firstNode);
            if (portType != -1)
                ports[portType] = true;
            portType = board.getPortTypeFromNodeCoord(secondNode);
            if (portType != -1)
                ports[portType] = true;

            //log.debug(sb.toString());
            log.debug("probTotal = " + probTotal);

            /**
             * estimate the building speed for this pair
             */
            estimate.recalculateEstimates(playerNumbers);

            int speed = 0;

            try
            {
                speed += estimate.calculateRollsAndRsrcFast
                    (SOCResourceSet.EMPTY_SET, SOCSettlement.COST, bestSpeed, ports).getRolls();

                if (speed < bestSpeed)
                {
                    speed += estimate.calculateRollsAndRsrcFast
                        (SOCResourceSet.EMPTY_SET, SOCCity.COST, bestSpeed, ports).getRolls();

                    if (speed < bestSpeed)
                    {
                        speed += estimate.calculateRollsAndRsrcFast
                            (SOCResourceSet.EMPTY_SET, SOCDevCard.COST, bestSpeed, ports).getRolls();

                        if (speed < bestSpeed)
                        {
                            speed += estimate.calculateRollsAndRsrcFast
                                (SOCResourceSet.EMPTY_SET, SOCRoad.COST, bestSpeed, ports).getRolls();
                        }
                    }
                }

                // because of addition, speed might be as much as (bestSpeed - 1) + bestSpeed
            }
            catch (CutoffExceededException e)
            {
                speed = bestSpeed;
            }

            log.debug(Integer.toHexString(firstNode) + ", " + Integer.toHexString(secondNode) + ":" + speed);

            /**
             * keep the settlements with the best speed
             */
            if ((speed < bestSpeed) || (secondSettlement < 0))
            {
                firstSettlement = firstNode;
                secondSettlement = secondNode;
                bestSpeed = speed;
                bestProbTotal = probTotal;
                log.debug("firstSettlement = " + Integer.toHexString(firstSettlement));
                log.debug("secondSettlement = " + Integer.toHexString(secondSettlement));

                int[] rolls = estimate.getEstimatesFromNothingFast(ports);
                sb = new StringBuffer();
                sb.append("road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                sb.append(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                sb.append(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                sb.append(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                log.debug(sb.toString());
                log.debug("bestSpeed = " + bestSpeed);
            }
            else if (speed == bestSpeed)
            {
                if (probTotal > bestProbTotal)
                {
                    firstSettlement = firstNode;
                    secondSettlement = secondNode;
                    bestSpeed = speed;
                    bestProbTotal = probTotal;
                    log.debug("firstSettlement = " + Integer.toHexString(firstSettlement));
                    log.debug("secondSettlement = " + Integer.toHexString(secondSettlement));

                    int[] rolls = estimate.getEstimatesFromNothingFast(ports);
                    sb = new StringBuffer();
                    sb.append("road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                    sb.append(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                    sb.append(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                    sb.append(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                    log.debug(sb.toString());
                    log.debug("bestSpeed = " + bestSpeed);
                }
            }

        }  // for (i in ourPotentialSettlements[])

        return secondSettlement;
    }

    /**
     * Plan and place a road attached to our most recently placed initial settlement
     * {@link SOCPlayer#getLastSettlementCoord()},
     * in game states {@link SOCGame#START1B START1B}, {@link SOCGame#START2B START2B}, {@link SOCGame#START3B START3B}.
     *<P>
     * Road choice is based on the best nearby potential settlements, and doesn't
     * directly check {@link SOCPlayer#isPotentialRoad(int) ourPlayerData.isPotentialRoad(edgeCoord)}.
     *<P>
     * If server rejects our road choice, bot will call {@link #cancelWrongPiecePlacement(SOCPlayingPiece)}
     * in case the OBS wants to take action to prevent re-choosing the same wrong choice again.
     *
     * @return road edge adjacent to initial settlement node {@link SOCPlayer#getLastSettlementCoord()}
     */
    public int planInitRoad()
    {
        // TODO handle ships here

        final int settlementNode = ourPlayerData.getLastSettlementCoord();

        /**
         * Score the nearby nodes to build road towards: Key = coord Integer; value = Integer score towards "best" node.
         */
        Hashtable<Integer,Integer> twoAway = new Hashtable<Integer,Integer>();

        log.debug("--- placeInitRoad");

        /**
         * look at all of the nodes that are 2 away from the
         * last settlement, and pick the best one
         */
        final SOCBoard board = game.getBoard();

        for (int facing = 1; facing <= 6; ++facing)
        {
            // each of 6 directions: NE, E, SE, SW, W, NW
            int tmp = board.getAdjacentNodeToNode2Away(settlementNode, facing);
            if ((tmp != -9) && ourPlayerData.canPlaceSettlement(tmp))
                twoAway.put(Integer.valueOf(tmp), Integer.valueOf(0));
        }

        scoreNodesForSettlements(twoAway, 3, 5, 10);

        log.debug("Init Road for " + ourPlayerData.getName());

        /**
         * create a dummy player to calculate possible places to build
         * taking into account where other players will build before
         * we can.
         */
        SOCPlayer dummy = new SOCPlayer(ourPlayerData.getPlayerNumber(), game);

        if ((game.getGameState() == SOCGame.START1B)
            || (game.isGameOptionSet(SOCGameOptionSet.K_SC_3IP) && (game.getGameState() == SOCGame.START2B)))
        {
            /**
             * do a look ahead so we don't build toward a place
             * where someone else will build first.
             */
            final int numberOfBuilds = numberOfEnemyBuilds();
            log.debug("Other players will build " + numberOfBuilds + " settlements before I get to build again.");

            if (numberOfBuilds > 0)
            {
                /**
                 * rule out where other players are going to build
                 */
                Hashtable<Integer,Integer> allNodes = new Hashtable<Integer,Integer>();

                {
                    Iterator<Integer> psi = ourPlayerData.getPotentialSettlements().iterator();
                    while (psi.hasNext())
                        allNodes.put(psi.next(), Integer.valueOf(0));
                    // log.debug("-- potential settlement at " + Integer.toHexString(next));
                }

                /**
                 * favor spots with the most high numbers
                 */
                bestSpotForNumbers(allNodes, null, 100);

                /**
                 * favor spots near good ports
                 */
                /**
                 * check 3:1 ports
                 */
                List<Integer> miscPortNodes = board.getPortCoordinates(SOCBoard.MISC_PORT);
                bestSpot2AwayFromANodeSet(allNodes, miscPortNodes, 5);

                /**
                 * check out good 2:1 ports
                 */
                final int[] resourceEstis = estimateResourceRarity();
                for (int portType = SOCBoard.CLAY_PORT;
                         portType <= SOCBoard.WOOD_PORT; portType++)
                {
                    /**
                     * if the chances of rolling a number on the resource is better than 1/3,
                     * then it's worth looking at the port
                     */
                    if (resourceEstis[portType] > 33)
                    {
                        List<Integer> portNodes = board.getPortCoordinates(portType);
                        final int portWeight = (resourceEstis[portType] * 10) / 56;
                        bestSpot2AwayFromANodeSet(allNodes, portNodes, portWeight);
                    }
                }

                /*
                 * create a list of potential settlements that takes into account
                 * where other players will build
                 */
                List<Integer> psList = new ArrayList<Integer>();

                psList.addAll(ourPlayerData.getPotentialSettlements());
                // log.debug("- potential settlement at " + Integer.toHexString(j));

                dummy.setPotentialAndLegalSettlements(psList, false, null);

                for (int builds = 0; builds < numberOfBuilds; builds++)
                {
                    BoardNodeScorePair bestNodePair = new BoardNodeScorePair(0, 0);
                    Enumeration<Integer> nodesEnum = allNodes.keys();

                    while (nodesEnum.hasMoreElements())
                    {
                        final Integer nodeCoord = nodesEnum.nextElement();
                        final int score = allNodes.get(nodeCoord).intValue();
                        log.debug("NODE = " + Integer.toHexString(nodeCoord.intValue()) + " SCORE = " + score);

                        if (bestNodePair.getScore() < score)
                        {
                            bestNodePair.setScore(score);
                            bestNodePair.setNode(nodeCoord.intValue());
                        }
                    }

                    /**
                     * pretend that someone has built a settlement on the best spot
                     */
                    dummy.updatePotentials(new SOCSettlement(ourPlayerData, bestNodePair.getNode(), null));

                    /**
                     * remove this spot from the list of best spots
                     */
                    allNodes.remove(Integer.valueOf(bestNodePair.getNode()));
                }
            }
        }

        /**
         * Find the best scoring node
         */
        BoardNodeScorePair bestNodePair = new BoardNodeScorePair(0, 0);
        Enumeration<Integer> cenum = twoAway.keys();

        while (cenum.hasMoreElements())
        {
            final Integer coordInt = cenum.nextElement();
            final int coord = coordInt.intValue();
            final int score = twoAway.get(coordInt).intValue();

            log.debug("Considering " + Integer.toHexString(coord) + " with a score of " + score);

            if (dummy.canPlaceSettlement(coord))
            {
                if (bestNodePair.getScore() < score)
                {
                    bestNodePair.setScore(score);
                    bestNodePair.setNode(coord);
                }
            }
            else
            {
                log.debug("Someone is bound to ruin that spot.");
            }
        }

        // Reminder: settlementNode == ourPlayerData.getLastSettlementCoord()
        plannedRoadDestinationNode = bestNodePair.getNode();  // coordinate of future settlement
                                                         // 2 nodes away from settlementNode
        final int roadEdge   // will be adjacent to settlementNode
            = board.getAdjacentEdgeToNode2Away
              (settlementNode, plannedRoadDestinationNode);

        dummy.destroyPlayer();

        return roadEdge;
    }

    /**
     * Given a set of nodes, run a bunch of metrics across them
     * to find which one is best for building a
     * settlement.
     *
     * @param nodes          a hashtable of nodes; the scores in the table will be modified.
     *                            Key = coord Integer; value = score Integer.
     * @param numberWeight   the weight given to nodes on good numbers
     * @param miscPortWeight the weight given to nodes on 3:1 ports
     * @param portWeight     the weight given to nodes on good 2:1 ports
     */
    protected void scoreNodesForSettlements
        (final Hashtable<Integer,Integer> nodes, final int numberWeight, final int miscPortWeight, final int portWeight)
    {
        /**
         * favor spots with the most high numbers
         */
        bestSpotForNumbers(nodes, ourPlayerData, numberWeight);

        /**
         * Favor spots on good ports:
         */
        /**
         * check if this is on a 3:1 ports, only if we don't have one
         */
        if (!ourPlayerData.getPortFlag(SOCBoard.MISC_PORT))
        {
            List<Integer> miscPortNodes = game.getBoard().getPortCoordinates(SOCBoard.MISC_PORT);
            bestSpotInANodeSet(nodes, miscPortNodes, miscPortWeight);
        }

        /**
         * check out good 2:1 ports that we don't have
         */
        final int[] resourceEstis = estimateResourceRarity();

        for (int portType = SOCBoard.CLAY_PORT; portType <= SOCBoard.WOOD_PORT;
                portType++)
        {
            /**
             * if the chances of rolling a number on the resource is better than 1/3,
             * then it's worth looking at the port
             */
            if ((resourceEstis[portType] > 33) && (!ourPlayerData.getPortFlag(portType)))
            {
                List<Integer> portNodes = game.getBoard().getPortCoordinates(portType);
                int estimatedPortWeight = (resourceEstis[portType] * portWeight) / 56;
                bestSpotInANodeSet(nodes, portNodes, estimatedPortWeight);
            }
        }
    }

    /**
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  A list of nodes that
     * we want to be on is also taken as an argument.
     * Here are the rules for scoring:
     * If a node is in the desired set of nodes it gets 100.
     * Otherwise it gets 0.
     *
     * @param nodesIn   the table of nodes to evaluate: Hashtable&lt;Integer,Integer&gt .
     *                    Contents will be modified by the scoring.
     * @param goodNodes the set of desired nodes
     * @param weight    the score multiplier
     */
    protected void bestSpotInANodeSet
        (final Hashtable<Integer,Integer> nodesIn, final List<Integer> goodNodes, final int weight)
    {
        Enumeration<Integer> nodesInEnum = nodesIn.keys();

        while (nodesInEnum.hasMoreElements())
        {
            final Integer nodeCoord = nodesInEnum.nextElement();
            final int node = nodeCoord.intValue();
            int score = 0;
            final int oldScore = nodesIn.get(nodeCoord).intValue();

            for (final int goodNode : goodNodes)
            {
                if (node == goodNode)
                {
                    score = 100;
                    break;
                }
            }

            /**
             * multiply by weight
             */
            score *= weight;

            nodesIn.put(nodeCoord, Integer.valueOf(oldScore + score));

            //log.debug("BSIANS -- put node "+Integer.toHexString(node)+" with old score "+oldScore+" + new score "+score);
        }
    }

    /**
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  A list of nodes that
     * we want to be near is also taken as an argument.
     * Here are the rules for scoring:
     * If a node is two away from a node in the desired set of nodes it gets 100.
     * Otherwise it gets 0.
     *
     * @param nodesIn   the table of nodes to evaluate: Hashtable&lt;Integer,Integer&gt; .
     *                     Contents will be modified by the scoring.
     * @param goodNodes the set of desired nodes
     * @param weight    the score multiplier
     */
    protected void bestSpot2AwayFromANodeSet
        (final Hashtable<Integer,Integer> nodesIn, final List<Integer> goodNodes, final int weight)
    {
        final SOCBoard board = game.getBoard();

        Enumeration<Integer> nodesInEnum = nodesIn.keys();

        while (nodesInEnum.hasMoreElements())
        {
            final Integer nodeCoord = nodesInEnum.nextElement();
            final int node = nodeCoord.intValue();
            int score = 0;
            final int oldScore = nodesIn.get(nodeCoord).intValue();

            for (final int goodNodeTarget : goodNodes)
            {
                if (node == goodNodeTarget)
                {
                    break;
                }
                else if (board.isNode2AwayFromNode(node, goodNodeTarget))
                {
                    score = 100;
                }
            }

            /**
             * multiply by weight
             */
            score *= weight;

            nodesIn.put(nodeCoord, Integer.valueOf(oldScore + score));

            //log.debug("BS2AFANS -- put node "+Integer.toHexString(node)+" with old score "+oldScore+" + new score "+score);
        }
    }

    /**
     * Estimate the rarity of each resource, given this board's resource locations vs dice numbers.
     * Useful for initial settlement placement and free-resource choice (when no other info available).
     * This is based on the board and doesn't change when pieces are placed.
     * Cached after the first call, as {@link #resourceEstimates}.
     *<P>
     * Calls each hex's {@link SOCBoard#getHexTypeFromCoord(int)}, ignores all hex types besides
     * the usual {@link SOCBoard#CLAY_HEX} through {@link SOCBoard#WOOD_HEX} and {@link SOCBoardLarge#GOLD_HEX}.
     *
     * @return an array of rarity numbers, where
     *         estimates[SOCBoard.CLAY_HEX] == the clay rarity,
     *         as an integer percentage 0-100 of dice rolls.
     */
    public int[] estimateResourceRarity()
    {
        if (resourceEstimates == null)
        {
            final SOCBoard board = game.getBoard();
            final int[] numberWeights = SOCNumberProbabilities.INT_VALUES;

            resourceEstimates = new int[SOCResourceConstants.UNKNOWN];  // uses 1 to 5 (CLAY to WOOD)
            resourceEstimates[0] = 0;

            // look at each hex
            final int bef = board.getBoardEncodingFormat();
            if (bef == SOCBoard.BOARD_ENCODING_6PLAYER ||
                bef == SOCBoard.BOARD_ENCODING_ORIGINAL)
            {
                // v1 or v2 encoding
                final int L = board.getNumberLayout().length;
                for (int i = 0; i < L; i++)
                {
                    final int hexNumber = board.getNumberOnHexFromNumber(i);
                    if (hexNumber > 0)
                        resourceEstimates[board.getHexTypeFromNumber(i)] += numberWeights[hexNumber];
                }
            } else {
                // v3 encoding
                final int[] hcoord = board.getLandHexCoords();
                if (hcoord != null)
                {
                    final int L = hcoord.length;
                    for (int i = 0; i < L; i++)
                    {
                        final int hexNumber = board.getNumberOnHexFromCoord(hcoord[i]);
                        if (hexNumber == 0)
                            continue;

                        final int htype = board.getHexTypeFromCoord(hcoord[i]);
                        if (htype == SOCBoardLarge.GOLD_HEX)
                        {
                            // Count gold as all resource types
                            for (int ht = SOCBoard.CLAY_HEX; ht <= SOCBoard.WOOD_HEX; ++ht)
                                resourceEstimates[ht] += numberWeights[hexNumber];
                        }
                        else if ((htype >= 0) && (htype <= SOCBoard.WOOD_HEX))
                        {
                            resourceEstimates[htype] += numberWeights[hexNumber];
                        }
                    }
                }
            }
        }

        //D.ebugPrint("Resource Estimates = ");
        //for (int i = 1; i < 6; i++)
        //{
            //D.ebugPrint(i+":"+resourceEstimates[i]+" ");
        //}

        //log.debug();

        return resourceEstimates;
    }

    /**
     * Calculate the number of builds before our next turn during init placement.
     *
     */
    protected int numberOfEnemyBuilds()
    {
        int numberOfBuilds = 0;
        int pNum = game.getCurrentPlayerNumber();

        /**
         * This is the clockwise direction
         */
        if ((game.getGameState() == SOCGame.START1A) || (game.getGameState() == SOCGame.START1B))
        {
            do
            {
                /**
                 * look at the next player
                 */
                pNum++;

                if (pNum >= game.maxPlayers)
                {
                    pNum = 0;
                }

                if ((pNum != game.getFirstPlayer()) && ! game.isSeatVacant (pNum))
                {
                    numberOfBuilds++;
                }
            }
            while (pNum != game.getFirstPlayer());
        }

        /**
         * This is the counter-clockwise direction
         */
        do
        {
            /**
             * look at the next player
             */
            pNum--;

            if (pNum < 0)
            {
                pNum = game.maxPlayers - 1;
            }

            if ((pNum != game.getCurrentPlayerNumber()) && ! game.isSeatVacant (pNum))
            {
                numberOfBuilds++;
            }
        }
        while (pNum != game.getCurrentPlayerNumber());

        return numberOfBuilds;
    }

    /**
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  Nodes touching hexes
     * with better numbers get better scores.  Also numbers
     * that the player isn't touching yet are better than ones
     * that the player is already touching.
     *
     * @param nodes    the "table" of nodes to add scores to: key = a node on land; value = its Integer score.
     *                   Score values will be updated here.
     * @param player   the player that we are doing the rating for, or <tt>null</tt>;
     *                   will give a bonus to numbers the player isn't already touching
     * @param weight   a number that is multiplied by the score
     */
    protected void bestSpotForNumbers(Hashtable<Integer,Integer> nodes, SOCPlayer player, int weight)
    {
        final int[] numRating = SOCNumberProbabilities.INT_VALUES;
        final SOCPlayerNumbers playerNumbers = (player != null) ? player.getNumbers() : null;
        final SOCBoard board = game.getBoard();

        // 80 is highest practical score (40 if player == null)
        final int maxScore = (player != null) ? 80 : 40;

        int oldScore;
        Enumeration<Integer> nodesEnum = nodes.keys();

        while (nodesEnum.hasMoreElements())
        {
            final Integer nodeInt = nodesEnum.nextElement();

            //log.debug("BSN - looking at node "+Integer.toHexString(node.intValue()));
            oldScore = nodes.get(nodeInt).intValue();

            int score = 0;

            for (final int hex : board.getAdjacentHexesToNode(nodeInt.intValue()))
            {
                final int number = board.getNumberOnHexFromCoord(hex);
                score += numRating[number];

                if ((number != 0) && (playerNumbers != null) && ! playerNumbers.hasNumber(number))
                {
                    /**
                     * add a bonus for numbers that the player doesn't already have
                     */

                    //log.debug("ADDING BONUS FOR NOT HAVING "+number);
                    score += numRating[number];
                }

                //log.debug(" -- -- Adding "+numRating[board.getNumberOnHexFromCoord(hex)]);
            }

            /*
             * normalize score and multiply by weight
             * 80 is highest practical score (40 if player == null)
             * lowest score is 0
             */
            final int nScore = ((score * 100) / maxScore) * weight;
            final Integer finalScore = Integer.valueOf(nScore + oldScore);
            nodes.put(nodeInt, finalScore);

            //log.debug("BSN -- put node "+Integer.toHexString(node.intValue())+" with old score "+oldScore+" + new score "+nScore);
        }
    }

}
