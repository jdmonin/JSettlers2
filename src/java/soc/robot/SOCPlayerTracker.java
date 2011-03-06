/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2011 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCLRPathData;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;

import soc.util.CutoffExceededException;
import soc.util.NodeLenVis;
import soc.util.Pair;
import soc.util.Queue;

import java.text.DecimalFormat;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.TreeMap;
import java.util.Vector;


/**
 * This class is used by the SOCRobotBrain to track
 * possible building spots for itself and other players.
 *
 * (Dissertation excerpt)
 *
 * "When a player places a road, that player's PlayerTracker will look ahead by
 *  pretending to place new roads attached to that road and then recording new
 *  potential settlements [and their roads]...
 *<p>
 *  The PlayerTracker only needs to be updated when players put pieces on the
 *  board... not only when that player builds a road but when any player builds
 *  a road or settlement. This is because another player's road or settlement
 *  may cut off a path to a future settlement. This update can be done by
 *  keeping track of which pieces support the building of others."
 *<p>
 *  For a legible overview of the data in a SOCPlayerTracker, use playerTrackersDebug.
 *  @see #playerTrackersDebug(HashMap)
 *
 * @author Robert S Thomas
 */
public class SOCPlayerTracker
{
    protected static final DecimalFormat df1 = new DecimalFormat("###0.00");
    static protected int EXPAND_LEVEL = 1;
    static protected int LR_CALC_LEVEL = 2;
    protected SOCRobotBrain brain;
    protected SOCPlayer player;
    protected TreeMap possibleSettlements;
    protected TreeMap possibleRoads;
    protected TreeMap possibleCities;
    protected int longestRoadETA;
    protected int roadsToGo;
    protected int largestArmyETA;
    protected int winGameETA;
    protected int knightsToBuy;
    protected boolean needLR;
    protected boolean needLA;

    /**
     * Player's settlement during initial placement; delay processing until
     * the road is placed, and thus the settlement placement can't be moved around.
     */
    protected SOCSettlement pendingInitSettlement;

    /**
     * monitor for synchronization
     */
    boolean inUse;

    /**
     * constructor
     *
     * @param pl  the player
     * @param br  the robot brain
     */
    public SOCPlayerTracker(SOCPlayer pl, SOCRobotBrain br)
    {
        inUse = false;
        brain = br;
        player = pl;
        possibleRoads = new TreeMap();
        possibleSettlements = new TreeMap();
        possibleCities = new TreeMap();
        longestRoadETA = 500;
        roadsToGo = 20;
        largestArmyETA = 500;
        knightsToBuy = 0;
        pendingInitSettlement = null;
    }

    /**
     * copy constructor
     *
     * Note: Does NOT copy connections between possible pieces
     *
     * @param pt  the player tracker
     */
    public SOCPlayerTracker(SOCPlayerTracker pt)
    {
        inUse = false;
        brain = pt.getBrain();
        player = pt.getPlayer();
        possibleRoads = new TreeMap();
        possibleSettlements = new TreeMap();
        possibleCities = new TreeMap();
        longestRoadETA = pt.getLongestRoadETA();
        roadsToGo = pt.getRoadsToGo();
        largestArmyETA = pt.getLargestArmyETA();
        knightsToBuy = pt.getKnightsToBuy();
        pendingInitSettlement = pt.getPendingInitSettlement();

        //D.ebugPrintln(">>>>> Copying SOCPlayerTracker for player number "+player.getPlayerNumber());
        //
        // now perform the copy
        //
        // start by just getting all of the possible pieces
        //
        Iterator posRoadsIter = pt.getPossibleRoads().values().iterator();

        while (posRoadsIter.hasNext())
        {
            SOCPossibleRoad posRoad = (SOCPossibleRoad) posRoadsIter.next();
            SOCPossibleRoad posRoadCopy = new SOCPossibleRoad(posRoad);
            possibleRoads.put(new Integer(posRoadCopy.getCoordinates()), posRoadCopy);
        }

        Iterator posSettlementsIter = pt.getPossibleSettlements().values().iterator();

        while (posSettlementsIter.hasNext())
        {
            SOCPossibleSettlement posSettlement = (SOCPossibleSettlement) posSettlementsIter.next();
            SOCPossibleSettlement posSettlementCopy = new SOCPossibleSettlement(posSettlement);
            possibleSettlements.put(new Integer(posSettlementCopy.getCoordinates()), posSettlementCopy);
        }

        Iterator posCitiesIter = pt.getPossibleCities().values().iterator();

        while (posCitiesIter.hasNext())
        {
            SOCPossibleCity posCity = (SOCPossibleCity) posCitiesIter.next();
            SOCPossibleCity posCityCopy = new SOCPossibleCity(posCity);
            possibleCities.put(new Integer(posCityCopy.getCoordinates()), posCityCopy);
        }
    }

    /**
     * make copies of player trackers and then
     * make connections between copied pieces
     *
     * Note: not copying threats
     *
     * param trackers  player trackers for each player
     */
    public static HashMap copyPlayerTrackers(HashMap trackers)
    {
        HashMap trackersCopy = new HashMap(trackers.size());  // == SOCGame.MAXPLAYERS

        //
        // copy the trackers but not the connections between the pieces
        //
        Iterator trackersIter = trackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker pt = (SOCPlayerTracker) trackersIter.next();
            trackersCopy.put(new Integer(pt.getPlayer().getPlayerNumber()), new SOCPlayerTracker(pt));
        }

        //
        // now make the connections between the pieces
        //
        //D.ebugPrintln(">>>>> Making connections between pieces");
        trackersIter = trackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            SOCPlayerTracker trackerCopy = (SOCPlayerTracker) trackersCopy.get(new Integer(tracker.getPlayer().getPlayerNumber()));

            //D.ebugPrintln(">>>> Player num for tracker is "+tracker.getPlayer().getPlayerNumber()); 
            //D.ebugPrintln(">>>> Player num for trackerCopy is "+trackerCopy.getPlayer().getPlayerNumber()); 
            TreeMap possibleRoads = tracker.getPossibleRoads();
            TreeMap possibleRoadsCopy = trackerCopy.getPossibleRoads();
            TreeMap possibleSettlements = tracker.getPossibleSettlements();
            TreeMap possibleSettlementsCopy = trackerCopy.getPossibleSettlements();
            Iterator posRoadsIter = possibleRoads.values().iterator();

            while (posRoadsIter.hasNext())
            {
                SOCPossibleRoad posRoad = (SOCPossibleRoad) posRoadsIter.next();
                SOCPossibleRoad posRoadCopy = (SOCPossibleRoad) possibleRoadsCopy.get(new Integer(posRoad.getCoordinates()));

                //D.ebugPrintln(">>> posRoad     : "+posRoad);
                //D.ebugPrintln(">>> posRoadCopy : "+posRoadCopy);
                Iterator necRoadsIter = posRoad.getNecessaryRoads().iterator();

                while (necRoadsIter.hasNext())
                {
                    SOCPossibleRoad necRoad = (SOCPossibleRoad) necRoadsIter.next();

                    //D.ebugPrintln(">> posRoad.necRoad : "+necRoad);
                    //
                    // now find the copy of this necessary road and 
                    // add it to the pos road copy's nec road list
                    //
                    SOCPossibleRoad necRoadCopy = (SOCPossibleRoad) possibleRoadsCopy.get(new Integer(necRoad.getCoordinates()));

                    if (necRoadCopy != null)
                    {
                        posRoadCopy.addNecessaryRoad(necRoadCopy);
                    }
                    else
                    {
                        D.ebugPrintln("*** ERROR in copyPlayerTrackers : necRoadCopy == null");
                    }
                }

                Iterator newPosIter = posRoad.getNewPossibilities().iterator();

                while (newPosIter.hasNext())
                {
                    SOCPossiblePiece newPos = (SOCPossiblePiece) newPosIter.next();

                    //D.ebugPrintln(">> posRoad.newPos : "+newPos);
                    //
                    // now find the copy of this new possibility and 
                    // add it to the pos road copy's new possibility list
                    //
                    switch (newPos.getType())
                    {
                    case SOCPossiblePiece.ROAD:

                        SOCPossibleRoad newPosRoadCopy = (SOCPossibleRoad) possibleRoadsCopy.get(new Integer(newPos.getCoordinates()));

                        if (newPosRoadCopy != null)
                        {
                            posRoadCopy.addNewPossibility(newPosRoadCopy);
                        }
                        else
                        {
                            D.ebugPrintln("*** ERROR in copyPlayerTrackers : newPosRoadCopy == null");
                        }

                        break;

                    case SOCPossiblePiece.SETTLEMENT:

                        SOCPossibleSettlement newPosSettlementCopy = (SOCPossibleSettlement) possibleSettlementsCopy.get(new Integer(newPos.getCoordinates()));

                        if (newPosSettlementCopy != null)
                        {
                            posRoadCopy.addNewPossibility(newPosSettlementCopy);
                        }
                        else
                        {
                            D.ebugPrintln("*** ERROR in copyPlayerTrackers : newPosSettlementCopy == null");
                        }

                        break;
                    }
                }
            }

            Iterator posSettlementsIter = possibleSettlements.values().iterator();

            while (posSettlementsIter.hasNext())
            {
                SOCPossibleSettlement posSet = (SOCPossibleSettlement) posSettlementsIter.next();
                SOCPossibleSettlement posSetCopy = (SOCPossibleSettlement) possibleSettlementsCopy.get(new Integer(posSet.getCoordinates()));

                //D.ebugPrintln(">>> posSet     : "+posSet);
                //D.ebugPrintln(">>> posSetCopy : "+posSetCopy);
                Iterator necRoadsIter = posSet.getNecessaryRoads().iterator();

                while (necRoadsIter.hasNext())
                {
                    SOCPossibleRoad necRoad = (SOCPossibleRoad) necRoadsIter.next();

                    //D.ebugPrintln(">> posSet.necRoad : "+necRoad);
                    //
                    // now find the copy of this necessary road and 
                    // add it to the pos settlement copy's nec road list
                    //
                    SOCPossibleRoad necRoadCopy = (SOCPossibleRoad) possibleRoadsCopy.get(new Integer(necRoad.getCoordinates()));

                    if (necRoadCopy != null)
                    {
                        posSetCopy.addNecessaryRoad(necRoadCopy);
                    }
                    else
                    {
                        D.ebugPrintln("*** ERROR in copyPlayerTrackers : necRoadCopy == null");
                    }
                }

                Iterator conflictsIter = posSet.getConflicts().iterator();

                while (conflictsIter.hasNext())
                {
                    SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictsIter.next();

                    //D.ebugPrintln(">> posSet.conflict : "+conflict);
                    //
                    // now find the copy of this conflict and
                    // add it to the conflict list in the pos settlement copy
                    //
                    SOCPlayerTracker trackerCopy2 = (SOCPlayerTracker) trackersCopy.get(new Integer(conflict.getPlayer().getPlayerNumber()));

                    if (trackerCopy2 == null)
                    {
                        D.ebugPrintln("*** ERROR in copyPlayerTrackers : trackerCopy2 == null");
                    }
                    else
                    {
                        SOCPossibleSettlement conflictCopy = (SOCPossibleSettlement) trackerCopy2.getPossibleSettlements().get(new Integer(conflict.getCoordinates()));

                        if (conflictCopy == null)
                        {
                            D.ebugPrintln("*** ERROR in copyPlayerTrackers : conflictCopy == null");
                        }
                        else
                        {
                            posSetCopy.addConflict(conflictCopy);
                        }
                    }
                }
            }
        }

        return trackersCopy;
    }

    /**
     * take the monitor for this tracker
     */
    public synchronized void takeMonitor()
    {
        /*
           while (inUse) {
           try {
           wait();
           } catch (InterruptedException e) {
           System.out.println("EXCEPTION IN takeMonitor() -- "+e);
           }
           }
           inUse = true;
         */
    }

    /**
     * release the monitor for this tracker
     */
    public synchronized void releaseMonitor()
    {
        /*
           inUse = false;
           notifyAll();
         */
    }

    /**
     * @return the robot brain for this tracker
     */
    public SOCRobotBrain getBrain()
    {
        return brain;
    }

    /**
     * @return the player for this tracker
     */
    public SOCPlayer getPlayer()
    {
        return player;
    }

    /**
     * @return the list of possible roads
     */
    public TreeMap getPossibleRoads()
    {
        return possibleRoads;
    }

    /**
     * @return the list of possible settlements
     */
    public TreeMap getPossibleSettlements()
    {
        return possibleSettlements;
    }

    /**
     * @return the list of possible cities
     */
    public TreeMap getPossibleCities()
    {
        return possibleCities;
    }

    /**
     * @return the longest road eta
     */
    public int getLongestRoadETA()
    {
        return longestRoadETA;
    }

    /**
     * @return how many roads needed to build to take longest road
     */
    public int getRoadsToGo()
    {
        return roadsToGo;
    }

    /**
     * @return largest army eta
     */
    public int getLargestArmyETA()
    {
        return largestArmyETA;
    }

    /**
     * @return the number of knights to buy to get LA
     */
    public int getKnightsToBuy()
    {
        return knightsToBuy;
    }

    /**
     * @return the pending-placement initial settlement
     */
    public SOCSettlement getPendingInitSettlement()
    {
        return pendingInitSettlement;
    }

    /**
     * set this player's pending initial settlement, to be
     * placed/calculated by this tracker after their road.
     *
     * You must call addNewSettlement and then addNewRoad:
     * This is just a place to store the settlement data.
     *
     * @param s Settlement, or null
     */
    public void setPendingInitSettlement(SOCSettlement s)
    {
        pendingInitSettlement = s;
    }

    /**
     * add a road that has just been built
     *
     * @param road       the road
     * @param trackers   player trackers for the players
     */
    public void addNewRoad(SOCRoad road, HashMap trackers)
    {
        if (road.getPlayer().getPlayerNumber() == player.getPlayerNumber())
        {
            addOurNewRoad(road, trackers, EXPAND_LEVEL);
        }
        else
        {
            addTheirNewRoad(road, false);
        }
    }
    
    /**
     * Remove our incorrect road placement, it's been rejected by the server.
     * 
     * @param road Location of our bad road
     * 
     * @see SOCRobotBrain#cancelWrongPiecePlacement(SOCCancelBuildRequest)
     */
    public void cancelWrongRoad(SOCRoad road)
    {
        addTheirNewRoad(road, true);
        
        //
        // Cancel-actions to remove from potential settlements list,
        // (since it was wrongly placed), taken from addOurNewRoad.
        //
        // see if the new road was a possible road
        //
        Iterator prIter = possibleRoads.values().iterator();

        while (prIter.hasNext())
        {
            SOCPossibleRoad pr = (SOCPossibleRoad) prIter.next();
            if (pr.getCoordinates() == road.getCoordinates())
            {
                //
                // if so, remove it
                //
                //D.ebugPrintln("$$$ removing (wrong) "+Integer.toHexString(road.getCoordinates()));
                possibleRoads.remove(new Integer(pr.getCoordinates()));
                removeFromNecessaryRoads(pr);

                break;
            }
        }
    }

    /**
     * Add one of our roads that has just been built
     *
     * @param road         the road
     * @param trackers     player trackers for the players
     * @param expandLevel  how far out we should expand roads
     */
    public void addOurNewRoad(SOCRoad road, HashMap trackers, int expandLevel)
    {
        //D.ebugPrintln("$$$ addOurNewRoad : "+road);
        //
        // see if the new road was a possible road
        //
        Iterator prIter = possibleRoads.values().iterator();

        while (prIter.hasNext())
        {
            SOCPossibleRoad pr = (SOCPossibleRoad) prIter.next();

            //
            // reset all expanded flags for possible roads
            //
            pr.resetExpandedFlag();

            if (pr.getCoordinates() == road.getCoordinates())
            {
                //
                // if so, remove it
                //
                //D.ebugPrintln("$$$ removing "+Integer.toHexString(road.getCoordinates()));
                possibleRoads.remove(new Integer(pr.getCoordinates()));
                removeFromNecessaryRoads(pr);

                break;
            }
        }

        //D.ebugPrintln("$$$ checking for possible settlements");
        //
        // see if this road adds any new possible settlements
        //
        // check adjacent nodes to road for potential settlements
        //
        final SOCBoard board = player.getGame().getBoard();
        Enumeration adjNodeEnum = board.getAdjacentNodesToEdge(road.getCoordinates()).elements();

        while (adjNodeEnum.hasMoreElements())
        {
            Integer adjNode = (Integer) adjNodeEnum.nextElement();

            if (player.isPotentialSettlement(adjNode.intValue()))
            {
                //
                // see if possible settlement is already in the list
                //
                //D.ebugPrintln("$$$ seeing if "+Integer.toHexString(adjNode.intValue())+" is already in the list");
                SOCPossibleSettlement posSet = (SOCPossibleSettlement) possibleSettlements.get(adjNode);

                if (posSet != null)
                {
                    //
                    // if so, clear necessary road list and remove from np lists
                    //
                    //D.ebugPrintln("$$$ found it");
                    removeFromNecessaryRoads(posSet);
                    posSet.getNecessaryRoads().removeAllElements();
                    posSet.setNumberOfNecessaryRoads(0);
                }
                else
                {
                    //
                    // else, add new possible settlement
                    //
                    //D.ebugPrintln("$$$ adding new possible settlement at "+Integer.toHexString(adjNode.intValue()));
                    SOCPossibleSettlement newPosSet = new SOCPossibleSettlement(player, adjNode.intValue(), new Vector());
                    newPosSet.setNumberOfNecessaryRoads(0);
                    possibleSettlements.put(adjNode, newPosSet);
                    updateSettlementConflicts(newPosSet, trackers);
                }
            }
        }

        //D.ebugPrintln("$$$ checking roads adjacent to "+Integer.toHexString(road.getCoordinates()));
        //
        // see if this road adds any new possible roads 
        //
        Vector newPossibleRoads = new Vector();
        Vector roadsToExpand = new Vector();

        //
        // check adjacent edges to road
        //
        Enumeration adjEdgesEnum = board.getAdjacentEdgesToEdge(road.getCoordinates()).elements();

        while (adjEdgesEnum.hasMoreElements())
        {
            Integer adjEdge = (Integer) adjEdgesEnum.nextElement();

            //D.ebugPrintln("$$$ edge "+Integer.toHexString(adjEdge.intValue())+" is legal:"+player.isPotentialRoad(adjEdge.intValue()));
            //
            // see if edge is a potential road
            //
            if (player.isPotentialRoad(adjEdge.intValue()))
            {
                //
                // see if possible road is already in the list
                //
                SOCPossibleRoad pr = (SOCPossibleRoad) possibleRoads.get(adjEdge);

                if (pr != null)
                {
                    //
                    // if so, clear necessary road list and remove from np lists
                    //
                    //D.ebugPrintln("$$$ pr "+Integer.toHexString(pr.getCoordinates())+" already in list");
                    if (!pr.getNecessaryRoads().isEmpty())
                    {
                        //D.ebugPrintln("$$$    clearing nr list");
                        removeFromNecessaryRoads(pr);
                        pr.getNecessaryRoads().removeAllElements();
                        pr.setNumberOfNecessaryRoads(0);
                    }

                    roadsToExpand.addElement(pr);
                    pr.setExpandedFlag();
                }
                else
                {
                    //
                    // else, add new possible road
                    //
                    //D.ebugPrintln("$$$ adding new pr at "+Integer.toHexString(adjEdge.intValue()));
                    SOCPossibleRoad newPR = new SOCPossibleRoad(player, adjEdge.intValue(), new Vector());
                    newPR.setNumberOfNecessaryRoads(0);
                    newPossibleRoads.addElement(newPR);
                    roadsToExpand.addElement(newPR);
                    newPR.setExpandedFlag();
                }
            }
        }

        //
        // add the new roads to our list of possible roads
        //
        Enumeration newPREnum = newPossibleRoads.elements();

        while (newPREnum.hasMoreElements())
        {
            SOCPossibleRoad newPR = (SOCPossibleRoad) newPREnum.nextElement();
            possibleRoads.put(new Integer(newPR.getCoordinates()), newPR);
        }

        //
        // expand possible roads that we've touched or added
        //
        SOCPlayer dummy = new SOCPlayer(player);
        Enumeration expandPREnum = roadsToExpand.elements();

        while (expandPREnum.hasMoreElements())
        {
            SOCPossibleRoad expandPR = (SOCPossibleRoad) expandPREnum.nextElement();
            expandRoad(expandPR, player, dummy, trackers, expandLevel);
        }

        dummy.destroyPlayer();
    }

    /**
     * Expand a possible road to see what this road makes possible
     *
     * @param targetRoad   the possible road
     * @param player    the player who owns the original road
     * @param dummy     the dummy player used to see what's legal
     * @param trackers  player trackers
     * @param level     how many levels to expand
     */
    public void expandRoad(SOCPossibleRoad targetRoad, SOCPlayer player, SOCPlayer dummy, HashMap trackers, int level)
    {
        //D.ebugPrintln("$$$ expandRoad at "+Integer.toHexString(targetRoad.getCoordinates())+" level="+level);
        SOCBoard board = player.getGame().getBoard();
        SOCRoad dummyRoad = new SOCRoad(dummy, targetRoad.getCoordinates(), board);
        dummy.putPiece(dummyRoad);

        //
        // see if this road adds any new possible settlements
        //
        //D.ebugPrintln("$$$ checking for possible settlements");
        //
        // check adjacent nodes to road for potential settlements
        //
        Enumeration adjNodeEnum = board.getAdjacentNodesToEdge(targetRoad.getCoordinates()).elements();

        while (adjNodeEnum.hasMoreElements())
        {
            Integer adjNode = (Integer) adjNodeEnum.nextElement();

            if (dummy.isPotentialSettlement(adjNode.intValue()))
            {
                //
                // see if possible settlement is already in the list
                //
                //D.ebugPrintln("$$$ seeing if "+Integer.toHexString(adjNode.intValue())+" is already in the list");
                SOCPossibleSettlement posSet = (SOCPossibleSettlement) possibleSettlements.get(adjNode);

                if (posSet != null)
                {
                    //
                    // if so and it needs 1 or more roads other than this one, 
                    //
                    if ((!posSet.getNecessaryRoads().isEmpty()) && (!posSet.getNecessaryRoads().contains(targetRoad)))
                    {
                        //
                        // add target road to settlement's nr list and this settlement to the road's np list
                        //
                        //D.ebugPrintln("$$$ adding road "+Integer.toHexString(targetRoad.getCoordinates())+" to the settlement "+Integer.toHexString(posSet.getCoordinates()));
                        posSet.getNecessaryRoads().addElement(targetRoad);
                        targetRoad.addNewPossibility(posSet);

                        //
                        // update it's numberOfNecessaryRoads if this road reduces it
                        //
                        if ((targetRoad.getNumberOfNecessaryRoads() + 1) < posSet.getNumberOfNecessaryRoads())
                        {
                            posSet.setNumberOfNecessaryRoads(targetRoad.getNumberOfNecessaryRoads() + 1);
                        }
                    }
                }
                else
                {
                    //
                    // else, add new possible settlement
                    //
                    //D.ebugPrintln("$$$ adding new possible settlement at "+Integer.toHexString(adjNode.intValue()));
                    Vector nr = new Vector();
                    nr.addElement(targetRoad);

                    SOCPossibleSettlement newPosSet = new SOCPossibleSettlement(player, adjNode.intValue(), nr);
                    newPosSet.setNumberOfNecessaryRoads(targetRoad.getNumberOfNecessaryRoads() + 1);
                    possibleSettlements.put(adjNode, newPosSet);
                    targetRoad.addNewPossibility(newPosSet);
                    updateSettlementConflicts(newPosSet, trackers);
                }
            }
        }

        if (level > 0)
        {
            //
            // check for new possible roads
            //
            Vector newPossibleRoads = new Vector();
            Vector roadsToExpand = new Vector();

            //D.ebugPrintln("$$$ checking roads adjacent to "+Integer.toHexString(targetRoad.getCoordinates()));
            //
            // check adjacent edges to road
            //
            Enumeration adjEdgesEnum = board.getAdjacentEdgesToEdge(targetRoad.getCoordinates()).elements();

            while (adjEdgesEnum.hasMoreElements())
            {
                Integer adjEdge = (Integer) adjEdgesEnum.nextElement();

                //D.ebugPrintln("$$$ edge "+Integer.toHexString(adjEdge.intValue())+" is legal:"+dummy.isPotentialRoad(adjEdge.intValue()));
                //
                // see if edge is a potential road
                //
                if (dummy.isPotentialRoad(adjEdge.intValue()))
                {
                    //
                    // see if possible road is already in the list
                    //
                    SOCPossibleRoad pr = (SOCPossibleRoad) possibleRoads.get(adjEdge);

                    if (pr != null)
                    {
                        //
                        // if so, and it needs 1 or more roads other than this one, 
                        //
                        //D.ebugPrintln("$$$ pr "+Integer.toHexString(pr.getCoordinates())+" already in list");
                        Vector nr = pr.getNecessaryRoads();

                        if (!nr.isEmpty() && (!nr.contains(targetRoad)))
                        {
                            //
                            // add the target road to its nr list and the new road to the target road's np list
                            //
                            //D.ebugPrintln("$$$    adding "+Integer.toHexString(targetRoad.getCoordinates())+" to nr list");
                            nr.addElement(targetRoad);
                            targetRoad.addNewPossibility(pr);

                            //
                            // update this road's numberOfNecessaryRoads if the target road reduces it
                            //
                            if ((targetRoad.getNumberOfNecessaryRoads() + 1) < pr.getNumberOfNecessaryRoads())
                            {
                                pr.setNumberOfNecessaryRoads(targetRoad.getNumberOfNecessaryRoads() + 1);
                            }
                        }

                        if (!pr.hasBeenExpanded())
                        {
                            roadsToExpand.addElement(pr);
                            pr.setExpandedFlag();
                        }
                    }
                    else
                    {
                        //
                        // else, add new possible road
                        //
                        //D.ebugPrintln("$$$ adding new pr at "+Integer.toHexString(adjEdge.intValue()));
                        Vector neededRoads = new Vector();
                        neededRoads.addElement(targetRoad);

                        SOCPossibleRoad newPR = new SOCPossibleRoad(player, adjEdge.intValue(), neededRoads);
                        newPR.setNumberOfNecessaryRoads(targetRoad.getNumberOfNecessaryRoads() + 1);
                        targetRoad.addNewPossibility(newPR);
                        newPossibleRoads.addElement(newPR);
                        roadsToExpand.addElement(newPR);
                        newPR.setExpandedFlag();
                    }
                }
            }

            //
            // add the new roads to our list of possible roads
            //
            Enumeration newPREnum = newPossibleRoads.elements();

            while (newPREnum.hasMoreElements())
            {
                SOCPossibleRoad newPR = (SOCPossibleRoad) newPREnum.nextElement();
                possibleRoads.put(new Integer(newPR.getCoordinates()), newPR);
            }

            //
            // if the level is not zero, expand roads that we've touched or added
            //
            Enumeration expandPREnum = roadsToExpand.elements();

            while (expandPREnum.hasMoreElements())
            {
                SOCPossibleRoad expandPR = (SOCPossibleRoad) expandPREnum.nextElement();
                expandRoad(expandPR, player, dummy, trackers, level - 1);
            }
        }

        //
        // remove the dummy road
        //
        dummy.removePiece(dummyRoad);
    }

    /**
     * add another player's new road, or cancel our own bad road
     * by acting as if another player has placed there.
     * (That way, we won't decide to place there again.)
     *
     * @param road  the new road
     * @param isCancel Is this our own robot's road placement, rejected by the server?
     *     If so, this method call will cancel its placement within the tracker data.
     */
    public void addTheirNewRoad(SOCRoad road, boolean isCancel)
    {
        /**
         * see if another player's road interferes with our possible roads
         * and settlements
         */
        /**
         * if another player's road is on one of our possible
         * roads, then remove it
         */
        D.ebugPrintln("$$$ addTheirNewRoad : " + road);

        Integer roadCoordinates = new Integer(road.getCoordinates());
        SOCPossibleRoad pr = (SOCPossibleRoad) possibleRoads.get(roadCoordinates);

        if (pr != null)
        {
            //D.ebugPrintln("$$$ removing road at "+Integer.toHexString(pr.getCoordinates()));
            possibleRoads.remove(roadCoordinates);
            removeFromNecessaryRoads(pr);
            removeDependents(pr);
        }
    }

    /**
     * update settlement conflicts
     *
     * @param ps        a possible settlement
     * @param trackers  player trackers for all players
     */
    protected void updateSettlementConflicts(SOCPossibleSettlement ps, HashMap trackers)
    {
        //D.ebugPrintln("$$$ updateSettlementConflicts : "+Integer.toHexString(ps.getCoordinates()));

        /**
         * look at all adjacent nodes and update possible settlements on nodes
         */
        Iterator trackersIter = trackers.values().iterator();
        SOCBoard board = player.getGame().getBoard();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            /**
             * first look at the node that the possible settlement is on
             */
            /**
             * if it's not our tracker...
             */
            if (tracker.getPlayer().getPlayerNumber() != ps.getPlayer().getPlayerNumber())
            {
                SOCPossibleSettlement posSet = (SOCPossibleSettlement) tracker.getPossibleSettlements().get(new Integer(ps.getCoordinates()));

                if (posSet != null)
                {
                    //D.ebugPrintln("$$$ add conflict "+Integer.toHexString(posSet.getCoordinates()));
                    ps.addConflict(posSet);
                    posSet.addConflict(ps);
                }
            }

            /**
             * now look at adjacent settlements
             */
            Enumeration adjNodeEnum = board.getAdjacentNodesToNode(ps.getCoordinates()).elements();

            while (adjNodeEnum.hasMoreElements())
            {
                Integer adjNode = (Integer) adjNodeEnum.nextElement();
                SOCPossibleSettlement posSet = (SOCPossibleSettlement) tracker.getPossibleSettlements().get(adjNode);

                if (posSet != null)
                {
                    //D.ebugPrintln("$$$ add conflict "+Integer.toHexString(posSet.getCoordinates()));
                    ps.addConflict(posSet);
                    posSet.addConflict(ps);
                }
            }
        }
    }

    /**
     * add a settlement that has just been built
     *
     * @param settlement       the settlement
     * @param trackers         player trackers for the players
     */
    public synchronized void addNewSettlement(SOCSettlement settlement, HashMap trackers)
    {
        //D.ebugPrintln("%$% settlement owner ="+settlement.getPlayer().getPlayerNumber());
        //D.ebugPrintln("%$% tracker owner ="+player.getPlayerNumber());
        if (settlement.getPlayer().getPlayerNumber() == player.getPlayerNumber())
        {
            addOurNewSettlement(settlement, trackers);
        }
        else
        {
            addTheirNewSettlement(settlement, false);
        }
    }

    /**
     * Remove our incorrect settlement placement, it's been rejected by the server.
     * 
     * @param settlement Location of our bad settlement
     * 
     * @see SOCRobotBrain#cancelWrongPiecePlacement(SOCCancelBuildRequest)
     */
    public void cancelWrongSettlement(SOCSettlement settlement)
    {
        addTheirNewSettlement(settlement, true);
        
        /**
         * Cancel-actions to remove from potential settlements list,
         * (since it was wrongly placed), taken from addOurNewSettlement.
         * 
         * see if the new settlement was a possible settlement in
         * the list.  if so, remove it.
         */
        Integer settlementCoords = new Integer(settlement.getCoordinates());
        SOCPossibleSettlement ps = (SOCPossibleSettlement) possibleSettlements.get(settlementCoords);
        D.ebugPrintln("$$$ removing (wrong) " + Integer.toHexString(settlement.getCoordinates()));
        possibleSettlements.remove(settlementCoords);
        removeFromNecessaryRoads(ps);

    }

    /**
     * add one of our settlements
     *
     * @param settlement  the new settlement
     * @param trackers    player trackers for all of the players
     */
    public synchronized void addOurNewSettlement(SOCSettlement settlement, HashMap trackers)
    {
        //D.ebugPrintln();
        D.ebugPrintln("$$$ addOurNewSettlement : " + settlement);
        SOCBoard board = player.getGame().getBoard();

        Integer settlementCoords = new Integer(settlement.getCoordinates());

        /**
         * add a new possible city
         */
        possibleCities.put(settlementCoords, new SOCPossibleCity(player, settlement.getCoordinates()));

        /**
         * see if the new settlement was a possible settlement in
         * the list.  if so, remove it.
         */
        SOCPossibleSettlement ps = (SOCPossibleSettlement) possibleSettlements.get(settlementCoords);

        if (ps != null)
        {
            D.ebugPrintln("$$$ was a possible settlement");

            /**
             * copy a list of all the conflicting settlements
             */
            Vector conflicts = (Vector) ps.getConflicts().clone();

            /**
             * remove the possible settlement that is now a real settlement
             */
            D.ebugPrintln("$$$ removing " + Integer.toHexString(settlement.getCoordinates()));
            possibleSettlements.remove(settlementCoords);
            removeFromNecessaryRoads(ps);

            /**
             * remove possible settlements that this one cancels out
             */
            Enumeration conflictEnum = conflicts.elements();

            while (conflictEnum.hasMoreElements())
            {
                SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictEnum.nextElement();
                D.ebugPrintln("$$$ checking conflict with " + conflict.getPlayer().getPlayerNumber() + ":" + Integer.toHexString(conflict.getCoordinates()));

                SOCPlayerTracker tracker = (SOCPlayerTracker) trackers.get(new Integer(conflict.getPlayer().getPlayerNumber()));

                if (tracker != null)
                {
                    D.ebugPrintln("$$$ removing " + Integer.toHexString(conflict.getCoordinates()));
                    tracker.getPossibleSettlements().remove(new Integer(conflict.getCoordinates()));
                    removeFromNecessaryRoads(conflict);

                    /**
                     * remove the conflicts that this settlement made
                     */
                    Enumeration otherConflictEnum = conflict.getConflicts().elements();

                    while (otherConflictEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement otherConflict = (SOCPossibleSettlement) otherConflictEnum.nextElement();
                        D.ebugPrintln("$$$ removing conflict " + Integer.toHexString(conflict.getCoordinates()) + " from " + Integer.toHexString(otherConflict.getCoordinates()));
                        otherConflict.removeConflict(conflict);
                    }
                }
            }
        }
        else
        {
            /**
             * if the new settlement wasn't a possible settlement,
             * we still need to cancel out other players possible settlements
             */
            D.ebugPrintln("$$$ wasn't possible settlement");

            Vector trash = new Vector();
            Vector adjNodes = board.getAdjacentNodesToNode(settlement.getCoordinates());
            Iterator trackersIter = trackers.values().iterator();

            while (trackersIter.hasNext())
            {
                SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
                SOCPossibleSettlement posSet = (SOCPossibleSettlement) tracker.getPossibleSettlements().get(settlementCoords);
                D.ebugPrintln("$$$ tracker for player " + tracker.getPlayer().getPlayerNumber());

                /**
                 * check the node that the settlement is on
                 */
                D.ebugPrintln("$$$ checking node " + Integer.toHexString(settlement.getCoordinates()));

                if (posSet != null)
                {
                    D.ebugPrintln("$$$ trashing " + Integer.toHexString(posSet.getCoordinates()));
                    trash.addElement(posSet);

                    /**
                     * remove the conflicts that this settlement made
                     */
                    Enumeration conflictEnum = posSet.getConflicts().elements();

                    while (conflictEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictEnum.nextElement();
                        D.ebugPrintln("$$$ removing conflict " + Integer.toHexString(posSet.getCoordinates()) + " from " + Integer.toHexString(conflict.getCoordinates()));
                        conflict.removeConflict(posSet);
                    }
                }

                /**
                 * check adjacent nodes
                 */
                Enumeration adjNodeEnum = adjNodes.elements();

                while (adjNodeEnum.hasMoreElements())
                {
                    Integer adjNode = (Integer) adjNodeEnum.nextElement();
                    D.ebugPrintln("$$$ checking node " + Integer.toHexString(adjNode.intValue()));
                    posSet = (SOCPossibleSettlement) tracker.getPossibleSettlements().get(adjNode);

                    if (posSet != null)
                    {
                        D.ebugPrintln("$$$ trashing " + Integer.toHexString(posSet.getCoordinates()));
                        trash.addElement(posSet);

                        /**
                         * remove the conflicts that this settlement made
                         */
                        Enumeration conflictEnum = posSet.getConflicts().elements();

                        while (conflictEnum.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictEnum.nextElement();
                            D.ebugPrintln("$$$ removing conflict " + Integer.toHexString(posSet.getCoordinates()) + " from " + Integer.toHexString(conflict.getCoordinates()));
                            conflict.removeConflict(posSet);
                        }
                    }
                }

                /**
                 * take out the trash
                 */
                D.ebugPrintln("$$$ removing trash for " + tracker.getPlayer().getPlayerNumber());

                Enumeration trashEnum = trash.elements();

                while (trashEnum.hasMoreElements())
                {
                    SOCPossibleSettlement pset = (SOCPossibleSettlement) trashEnum.nextElement();
                    D.ebugPrintln("$$$ removing " + Integer.toHexString(pset.getCoordinates()) + " owned by " + pset.getPlayer().getPlayerNumber());
                    tracker.getPossibleSettlements().remove(new Integer(pset.getCoordinates()));
                    removeFromNecessaryRoads(pset);
                }

                trash.removeAllElements();
            }
        }
    }

    /**
     * add another player's new settlement, or cancel our own bad settlement
     * by acting as if another player has placed there.
     * (That way, we won't decide to place there again.)
     *
     * @param settlement  the new settlement
     * @param isCancel Is this our own robot's settlement placement, rejected by the server?
     *     If so, this method call will cancel its placement within the tracker data.
     */
    public void addTheirNewSettlement(SOCSettlement settlement, boolean isCancel)
    {
        /**
         * this doesn't need to remove conflicts between settlements
         * because addOurNewSettlement takes care of that, but we
         * still need to check to see if any of our possible roads
         * have been cut off
         */
        /**
         * look for possible roads adjacent to the new settlement.
         * dependencies that cross the settlement are removed.
         * roads that lose all of their dependencies are removed.
         */

        //D.ebugPrintln();
        D.ebugPrintln("$$$ addTheirNewSettlement : " + settlement);

        Vector prTrash = new Vector();
        Vector nrTrash = new Vector();
        Vector adjEdges = player.getGame().getBoard().getAdjacentEdgesToNode(settlement.getCoordinates());
        Enumeration edge1Enum = adjEdges.elements();

        while (edge1Enum.hasMoreElements())
        {
            prTrash.removeAllElements();

            Integer edge1 = (Integer) edge1Enum.nextElement();
            SOCPossibleRoad pr = (SOCPossibleRoad) possibleRoads.get(edge1);

            if (pr != null)
            {
                if (pr.getNecessaryRoads().isEmpty())
                {
                    ///
                    /// This road has no necessary roads.
                    /// check to see if it is cut off by the new settlement
                    /// by seeing if this road was threatened by the settlement.
                    ///
                    /// If we're cancelling, it would have been our settlement,
                    /// so wouldn't have threatened our road.  Don't worry about
                    /// other players' potential roads, because point of 'cancel'
                    /// is to change our robot's immediate goal, not other players. 
                    ///
                    if (! isCancel)
                    {
                        Enumeration threatEnum = pr.getThreats().elements();
    
                        while (threatEnum.hasMoreElements())
                        {
                            SOCPossiblePiece threat = (SOCPossiblePiece) threatEnum.nextElement();
    
                            if ((threat.getType() == SOCPossiblePiece.SETTLEMENT) && (threat.getCoordinates() == settlement.getCoordinates()) && (threat.getPlayer().getPlayerNumber() == settlement.getPlayer().getPlayerNumber()))
                            {
                                D.ebugPrintln("$$$ new settlement cuts off road at " + Integer.toHexString(pr.getCoordinates()));
                                prTrash.addElement(pr);
    
                                break;
                            }
                        }
                    }
                }
                else
                {
                    nrTrash.removeAllElements();

                    Enumeration nrEnum = pr.getNecessaryRoads().elements();

                    while (nrEnum.hasMoreElements())
                    {
                        SOCPossibleRoad nr = (SOCPossibleRoad) nrEnum.nextElement();
                        Enumeration edge2Enum = adjEdges.elements();

                        while (edge2Enum.hasMoreElements())
                        {
                            Integer edge2 = (Integer) edge2Enum.nextElement();

                            if (nr.getCoordinates() == edge2.intValue())
                            {
                                D.ebugPrintln("$$$ removing dependency " + Integer.toHexString(nr.getCoordinates()) + " from " + Integer.toHexString(pr.getCoordinates()));
                                nrTrash.addElement(nr);

                                break;
                            }
                        }
                    }

                    ///
                    /// take out nr trash
                    ///
                    if (!nrTrash.isEmpty())
                    {
                        Enumeration nrTrashEnum = nrTrash.elements();

                        while (nrTrashEnum.hasMoreElements())
                        {
                            SOCPossibleRoad nrTrashRoad = (SOCPossibleRoad) nrTrashEnum.nextElement();
                            pr.getNecessaryRoads().removeElement(nrTrashRoad);
                            nrTrashRoad.getNewPossibilities().removeElement(pr);
                        }

                        if (pr.getNecessaryRoads().isEmpty())
                        {
                            D.ebugPrintln("$$$ no more dependencies, removing " + Integer.toHexString(pr.getCoordinates()));
                            prTrash.addElement(pr);
                        }
                    }
                }
            }

            ///
            /// take out the pr trash 
            ///
            Enumeration prTrashEnum = prTrash.elements();

            while (prTrashEnum.hasMoreElements())
            {
                SOCPossibleRoad prt = (SOCPossibleRoad) prTrashEnum.nextElement();
                possibleRoads.remove(new Integer(prt.getCoordinates()));
                removeFromNecessaryRoads(prt);
                removeDependents(prt);
            }
        }
    }

    /**
     * remove everything that depends on this road being built
     *
     * @param road  the road
     */
    protected void removeDependents(SOCPossibleRoad road)
    {
        /**
         * look at all of the pieces that this one
         * makes possible and remove them if this
         * is the only road that makes it possible
         */

        //D.ebugPrintln("$$$ removeDependents "+Integer.toHexString(road.getCoordinates()));
        Enumeration newPosEnum = road.getNewPossibilities().elements();

        while (newPosEnum.hasMoreElements())
        {
            SOCPossiblePiece newPos = (SOCPossiblePiece) newPosEnum.nextElement();

            //D.ebugPrintln("$$$ updating "+Integer.toHexString(newPos.getCoordinates()));
            Vector nr;

            switch (newPos.getType())
            {
            case SOCPossiblePiece.ROAD:
                nr = ((SOCPossibleRoad) newPos).getNecessaryRoads();

                if (nr.isEmpty())
                {
                    System.out.println("ERROR in removeDependents - empty nr list for " + newPos);
                }
                else
                {
                    nr.removeElement(road);

                    if (nr.isEmpty())
                    {
                        //D.ebugPrintln("$$$ removing this road");
                        possibleRoads.remove(new Integer(newPos.getCoordinates()));
                        removeFromNecessaryRoads((SOCPossibleRoad) newPos);
                        removeDependents((SOCPossibleRoad) newPos);
                    }
                    else
                    {
                        //
                        // update this road's numberOfNecessaryRoads value
                        //
                        int smallest = 40;
                        Enumeration nrEnum = nr.elements();

                        while (nrEnum.hasMoreElements())
                        {
                            SOCPossibleRoad necRoad = (SOCPossibleRoad) nrEnum.nextElement();

                            if ((necRoad.getNumberOfNecessaryRoads() + 1) < smallest)
                            {
                                smallest = necRoad.getNumberOfNecessaryRoads() + 1;
                            }
                        }

                        ((SOCPossibleRoad) newPos).setNumberOfNecessaryRoads(smallest);
                    }
                }

                break;

            case SOCPossiblePiece.SETTLEMENT:
                nr = ((SOCPossibleSettlement) newPos).getNecessaryRoads();

                if (nr.isEmpty())
                {
                    System.out.println("ERROR in removeDependents - empty nr list for " + newPos);
                }
                else
                {
                    nr.removeElement(road);

                    if (nr.isEmpty())
                    {
                        //D.ebugPrintln("$$$ removing this settlement");
                        possibleSettlements.remove(new Integer(newPos.getCoordinates()));
                        removeFromNecessaryRoads((SOCPossibleSettlement) newPos);

                        /**
                         * remove the conflicts that this settlement made
                         */
                        Enumeration conflictEnum = ((SOCPossibleSettlement) newPos).getConflicts().elements();

                        while (conflictEnum.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictEnum.nextElement();
                            conflict.removeConflict((SOCPossibleSettlement) newPos);
                        }
                    }
                    else
                    {
                        //
                        // update this road's numberOfNecessaryRoads value
                        //
                        int smallest = 40;
                        Enumeration nrEnum = nr.elements();

                        while (nrEnum.hasMoreElements())
                        {
                            SOCPossibleRoad necRoad = (SOCPossibleRoad) nrEnum.nextElement();

                            if ((necRoad.getNumberOfNecessaryRoads() + 1) < smallest)
                            {
                                smallest = necRoad.getNumberOfNecessaryRoads() + 1;
                            }
                        }

                        ((SOCPossibleSettlement) newPos).setNumberOfNecessaryRoads(smallest);
                    }
                }

                break;
            }
        }

        road.getNewPossibilities().removeAllElements();
    }

    /**
     * remove this piece from the pieces that support it
     *
     * @param pr  the possible road
     */
    protected void removeFromNecessaryRoads(SOCPossibleRoad pr)
    {
        //D.ebugPrintln("%%% remove road from necessary roads");
        Enumeration nrEnum = pr.getNecessaryRoads().elements();

        while (nrEnum.hasMoreElements())
        {
            SOCPossibleRoad nr = (SOCPossibleRoad) nrEnum.nextElement();

            //D.ebugPrintln("%%% removing road at "+Integer.toHexString(pr.getCoordinates())+" from road at "+Integer.toHexString(nr.getCoordinates()));
            nr.getNewPossibilities().removeElement(pr);
        }
    }

    /**
     * remove this piece from the pieces that support it
     *
     * @param ps  the possible settlement
     */
    protected void removeFromNecessaryRoads(SOCPossibleSettlement ps)
    {
        if (ps == null)
            return;    // just in case; should not happen

        //D.ebugPrintln("%%% remove settlement from necessary roads");
        Enumeration nrEnum = ps.getNecessaryRoads().elements();

        while (nrEnum.hasMoreElements())
        {
            SOCPossibleRoad nr = (SOCPossibleRoad) nrEnum.nextElement();

            //D.ebugPrintln("%%% removing settlement at "+Integer.toHexString(ps.getCoordinates())+" from road at "+Integer.toHexString(nr.getCoordinates()));
            nr.getNewPossibilities().removeElement(ps);
        }
    }

    /**
     * Remove our incorrect city placement, it's been rejected by the server.
     * Note, there is no addNewCity or addTheirNewCity method.
     * 
     * @param city Location of our bad city
     * 
     * @see SOCRobotBrain#cancelWrongPiecePlacement(SOCCancelBuildRequest)
     */
    public void cancelWrongCity(SOCCity city)
    {
        if (city == null)
            return;      // just in case; should not happen

        /**
         * There is no addTheirNewCity method.
         * Just remove our potential city, since it was wrongly placed.
         * remove the possible city from the list
         */
        possibleCities.remove(new Integer(city.getCoordinates()));
    }

    /**
     * add one of our cities, by removing it from the possible-cities list if it's there.
     * Note, there is no addNewCity or addTheirNewCity method.
     *
     * @param city  the new city
     */
    public void addOurNewCity(SOCCity city)
    {
        /**
         * remove the possible city from the list
         */
        possibleCities.remove(new Integer(city.getCoordinates()));
    }

    /**
     * undo adding one of our cities
     *
     * @param city  the now possible city
     */
    public void undoAddOurNewCity(SOCPossibleCity city)
    {
        /**
         * add the possible city to the list
         */
        possibleCities.put(new Integer(city.getCoordinates()), city);
    }

    /**
     * update threats for pieces that need to be updated
     *
     * @param trackers  all of the player trackers
     */
    public void updateThreats(HashMap trackers)
    {
        //D.ebugPrintln("&&&& updateThreats");

        /**
         * check roads that need updating and don't have necessary roads
         */
        int ourPlayerNumber = player.getPlayerNumber();
        SOCBoard board = player.getGame().getBoard();
        Iterator posRoadsIter = possibleRoads.values().iterator();

        while (posRoadsIter.hasNext())
        {
            SOCPossibleRoad posRoad = (SOCPossibleRoad) posRoadsIter.next();

            if ((!posRoad.isThreatUpdated()) && (posRoad.getNecessaryRoads().isEmpty()))
            {
                //D.ebugPrintln("&&&& examining road at "+Integer.toHexString(posRoad.getCoordinates()));

                /**
                 * look for possible settlements that can block this road
                 */
                final int[] adjNodesToPosRoad = SOCBoard.getAdjacentNodesToEdge_arr(posRoad.getCoordinates());
                Enumeration adjEdgeEnum = board.getAdjacentEdgesToEdge(posRoad.getCoordinates()).elements();

                while (adjEdgeEnum.hasMoreElements())
                {
                    Integer adjEdge = (Integer) adjEdgeEnum.nextElement();
                    Enumeration realRoadEnum = player.getRoads().elements();

                    while (realRoadEnum.hasMoreElements())
                    {
                        SOCRoad realRoad = (SOCRoad) realRoadEnum.nextElement();

                        if (adjEdge.intValue() == realRoad.getCoordinates())
                        {
                            /**
                             * found a supporting road, now find the node between
                             * the supporting road and the possible road
                             */
                            final int[] adjNodesToRealRoad = realRoad.getAdjacentNodes();

                            for (int pi = 0; pi < 2; ++pi)
                            {
                                final int adjNodeToPosRoad = adjNodesToPosRoad[pi];

                                for (int ri = 0; ri < 2; ++ri)
                                {
                                    final int adjNodeToRealRoad = adjNodesToRealRoad[ri];

                                    if (adjNodeToPosRoad == adjNodeToRealRoad)
                                    {
                                        /**
                                         * we found the common node
                                         * now see if there is a possible enemy settlement
                                         */
                                        final Integer adjNodeToPosRoadInt = new Integer(adjNodeToPosRoad);
                                        Iterator trackersIter = trackers.values().iterator();

                                        while (trackersIter.hasNext())
                                        {
                                            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                                            if (tracker.getPlayer().getPlayerNumber() != ourPlayerNumber)
                                            {
                                                SOCPossibleSettlement posEnemySet = (SOCPossibleSettlement) tracker.getPossibleSettlements().get(adjNodeToPosRoadInt);

                                                if (posEnemySet != null)
                                                {
                                                    /**
                                                     * we found a settlement that threatens our possible road
                                                     */

                                                    //D.ebugPrintln("&&&& adding threat from settlement at "+Integer.toHexString(posEnemySet.getCoordinates()));
                                                    posRoad.addThreat(posEnemySet);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                /**
                 * look for enemy roads that can block this road
                 */
                Iterator trackersIter = trackers.values().iterator();

                while (trackersIter.hasNext())
                {
                    SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                    if (tracker.getPlayer().getPlayerNumber() != ourPlayerNumber)
                    {
                        SOCPossibleRoad posEnemyRoad = (SOCPossibleRoad) tracker.getPossibleRoads().get(new Integer(posRoad.getCoordinates()));

                        if (posEnemyRoad != null)
                        {
                            /**
                             * we found a road that threatens our possible road
                             */

                            //D.ebugPrintln("&&&& adding threat from road at "+Integer.toHexString(posEnemyRoad.getCoordinates()));
                            posRoad.addThreat(posEnemyRoad);
                        }
                    }
                }

                /**
                 * look at all of the roads that this possible road supports.
                 * if any of those roads are solely dependent on this
                 * possible road, then all of the possible pieces that
                 * threaten this road, also threaten those pieces
                 */
                Vector threats = posRoad.getThreats();
                Stack stack = new Stack();
                stack.push(posRoad);

                while (!stack.empty())
                {
                    SOCPossiblePiece curPosPiece = (SOCPossiblePiece) stack.pop();

                    if (curPosPiece.getType() == SOCPossiblePiece.ROAD)
                    {
                        Enumeration newPosEnum = ((SOCPossibleRoad) curPosPiece).getNewPossibilities().elements();

                        while (newPosEnum.hasMoreElements())
                        {
                            SOCPossiblePiece newPosPiece = (SOCPossiblePiece) newPosEnum.nextElement();

                            if (newPosPiece.getType() == SOCPossiblePiece.ROAD)
                            {
                                Vector necRoadVec = ((SOCPossibleRoad) newPosPiece).getNecessaryRoads();

                                if ((necRoadVec.size() == 1) && (necRoadVec.firstElement() == curPosPiece))
                                {
                                    /**
                                     * pass on all of the threats to this piece
                                     */

                                    //D.ebugPrintln("&&&& adding threats to road at "+Integer.toHexString(newPosPiece.getCoordinates()));
                                    Enumeration threatEnum = threats.elements();

                                    while (threatEnum.hasMoreElements())
                                    {
                                        ((SOCPossibleRoad) newPosPiece).addThreat((SOCPossiblePiece) threatEnum.nextElement());
                                    }
                                }

                                /**
                                 * put this piece on the stack
                                 */
                                stack.push(newPosPiece);
                            }
                        }
                    }
                }

                //D.ebugPrintln("&&&& done updating road at "+Integer.toHexString(posRoad.getCoordinates()));
                posRoad.threatUpdated();
            }
        }

        /**
         * check roads that need updating and DO have necessary roads
         */
        posRoadsIter = possibleRoads.values().iterator();

        while (posRoadsIter.hasNext())
        {
            SOCPossibleRoad posRoad = (SOCPossibleRoad) posRoadsIter.next();

            if (!posRoad.isThreatUpdated())
            {
                //D.ebugPrintln("&&&& examining road at "+Integer.toHexString(posRoad.getCoordinates()));

                /**
                 * check for enemy roads with
                 * the same coordinates
                 */
                Iterator trackersIter = trackers.values().iterator();

                while (trackersIter.hasNext())
                {
                    SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                    if (tracker.getPlayer().getPlayerNumber() != ourPlayerNumber)
                    {
                        SOCPossibleRoad posEnemyRoad = (SOCPossibleRoad) tracker.getPossibleRoads().get(new Integer(posRoad.getCoordinates()));

                        if (posEnemyRoad != null)
                        {
                            /**
                             * we found a road that threatens our possible road
                             */

                            //D.ebugPrintln("&&&& adding threat from road at "+Integer.toHexString(posEnemyRoad.getCoordinates()));
                            posRoad.addThreat(posEnemyRoad);
                            posRoad.threatUpdated();
                        }
                    }
                }

                /**
                 * look for possible settlements that can block this road
                 */
                /**
                 * if this road has only one supporting road,
                 * find the node between this and the supporting road
                 */
                Vector necRoadVec = posRoad.getNecessaryRoads();

                if (necRoadVec.size() == 1)
                {
                    SOCPossibleRoad necRoad = (SOCPossibleRoad) necRoadVec.firstElement();
                    final int[] adjNodes1 = SOCBoard.getAdjacentNodesToEdge_arr(posRoad.getCoordinates());

                    for (int i1 = 0; i1 < 2; ++i1)
                    {
                        final int adjNode1 = adjNodes1[i1];
                        final int[] adjNodes2 = SOCBoard.getAdjacentNodesToEdge_arr(necRoad.getCoordinates());

                        for (int i2 = 0; i2 < 2; ++i2)
                        {
                            final int adjNode2 = adjNodes2[i2];

                            if (adjNode1 == adjNode2)
                            {
                                /**
                                 * see if there is a possible enemy settlement at
                                 * the node between the two possible roads
                                 */
                                trackersIter = trackers.values().iterator();
                                final Integer adjNodeInt = new Integer(adjNode1);

                                while (trackersIter.hasNext())
                                {
                                    SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                                    if (tracker.getPlayer().getPlayerNumber() != ourPlayerNumber)
                                    {
                                        SOCPossibleSettlement posEnemySet = (SOCPossibleSettlement) tracker.getPossibleSettlements().get(adjNodeInt);

                                        if (posEnemySet != null)
                                        {
                                            /**
                                             * we found a settlement that threatens our possible road
                                             */

                                            //D.ebugPrintln("&&&& adding threat from settlement at "+Integer.toHexString(posEnemySet.getCoordinates()));
                                            posRoad.addThreat(posEnemySet);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                //D.ebugPrintln("&&&& done updating road at "+Integer.toHexString(posRoad.getCoordinates()));
                posRoad.threatUpdated();
            }
        }

        /**
         * check settlements that need updating
         */
        Iterator posSetsIter = possibleSettlements.values().iterator();

        while (posSetsIter.hasNext())
        {
            SOCPossibleSettlement posSet = (SOCPossibleSettlement) posSetsIter.next();

            if (!posSet.isThreatUpdated())
            {
                //D.ebugPrintln("&&&& examining settlement at "+Integer.toHexString(posSet.getCoordinates()));

                /**
                 * see if there are enemy settlements with the same coords
                 */
                Iterator trackersIter = trackers.values().iterator();

                while (trackersIter.hasNext())
                {
                    SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                    if (tracker.getPlayer().getPlayerNumber() != ourPlayerNumber)
                    {
                        SOCPossibleSettlement posEnemySet = (SOCPossibleSettlement) tracker.getPossibleSettlements().get(new Integer(posSet.getCoordinates()));

                        if (posEnemySet != null)
                        {
                            //D.ebugPrintln("&&&& adding threat from settlement at "+Integer.toHexString(posEnemySet.getCoordinates()));
                            posSet.addThreat(posEnemySet);
                        }
                    }
                }

                //
                // if this settlement doesn't rely on anything, then we're done
                //
                Vector necRoadVec = posSet.getNecessaryRoads();

                if (necRoadVec.isEmpty())
                {
                    ;
                }
                else if (necRoadVec.size() == 1)
                {
                    //
                    // if it relies on only one road, then it inherits the road's threats
                    //
                    //D.ebugPrintln("&&&& inheriting threats from road at "+Integer.toHexString(((SOCPossibleRoad)necRoadVec.firstElement()).getCoordinates()));
                    Enumeration threatEnum = ((SOCPossibleRoad) necRoadVec.firstElement()).getThreats().elements();

                    while (threatEnum.hasMoreElements())
                    {
                        posSet.addThreat((SOCPossiblePiece) threatEnum.nextElement());
                    }
                }
                else
                {
                    //
                    // this settlement relies on more than one road.
                    // if all of the roads have the same threat,
                    // then add that threat to this settlement
                    //
                    SOCPossibleRoad nr = (SOCPossibleRoad) necRoadVec.firstElement();
                    Enumeration nrThreatEnum = nr.getThreats().elements();

                    while (nrThreatEnum.hasMoreElements())
                    {
                        SOCPossiblePiece nrThreat = (SOCPossiblePiece) nrThreatEnum.nextElement();
                        boolean allHaveIt = true;
                        Enumeration nr2Enum = necRoadVec.elements();

                        while (nr2Enum.hasMoreElements())
                        {
                            SOCPossibleRoad nr2 = (SOCPossibleRoad) nr2Enum.nextElement();

                            if ((nr2 != nr) && (!nr2.getThreats().contains(nrThreat)))
                            {
                                allHaveIt = false;

                                break;
                            }
                        }

                        if (allHaveIt)
                        {
                            //D.ebugPrintln("&&&& adding threat from "+Integer.toHexString(nrThreat.getCoordinates()));
                            posSet.addThreat(nrThreat);
                        }
                    }
                }

                //D.ebugPrintln("&&&& done updating settlement at "+Integer.toHexString(posSet.getCoordinates()));
                posSet.threatUpdated();
            }
        }
    }

    /**
     * calculate the longest road ETA
     */
    public void recalcLongestRoadETA()
    {
        D.ebugPrintln("===  recalcLongestRoadETA for player " + player.getPlayerNumber());

        int roadETA;
        SOCBuildingSpeedEstimate bse = new SOCBuildingSpeedEstimate(player.getNumbers());

        try
        {
            roadETA = bse.calculateRollsFast(SOCGame.EMPTY_RESOURCES, SOCGame.ROAD_SET, 500, player.getPortFlags()).getRolls();
        }
        catch (CutoffExceededException e)
        {
            roadETA = 500;
        }

        roadsToGo = 500;
        longestRoadETA = 500;

        int longestRoadLength;
        SOCPlayer lrPlayer = player.getGame().getPlayerWithLongestRoad();

        if ((lrPlayer != null) && (lrPlayer.getPlayerNumber() == player.getPlayerNumber()))
        {
            ///
            /// we have longest road
            ///
            //D.ebugPrintln("===  we have longest road");
            longestRoadETA = 0;
            roadsToGo = 0;
        }
        else
        {
            if (lrPlayer == null)
            {
                ///
                /// no one has longest road
                ///
                longestRoadLength = Math.max(4, player.getLongestRoadLength());
            }
            else
            {
                longestRoadLength = lrPlayer.getLongestRoadLength();
            }

            Iterator lrPathsIter = player.getLRPaths().iterator();
            int depth;

            while (lrPathsIter.hasNext())
            {
                SOCLRPathData pathData = (SOCLRPathData) lrPathsIter.next();
                depth = Math.min(((longestRoadLength + 1) - pathData.getLength()), player.getNumPieces(SOCPlayingPiece.ROAD));

                int minRoads = recalcLongestRoadETAAux(pathData.getBeginning(), pathData.getLength(), longestRoadLength, depth);
                roadsToGo = Math.min(minRoads, roadsToGo);
                minRoads = recalcLongestRoadETAAux(pathData.getEnd(), pathData.getLength(), longestRoadLength, depth);
                roadsToGo = Math.min(minRoads, roadsToGo);
            }
        }

        D.ebugPrintln("--- roadsToGo = " + roadsToGo);
        longestRoadETA = roadsToGo * roadETA;
    }

    /**
     * Does a depth first search from the end point of the longest
     * path in a graph of nodes and returns how many roads would
     * need to be built to take longest road.
     *
     * @param startNode     the path endpoint
     * @param pathLength    the length of that path
     * @param lrLength      length of longest road in the game
     * @param searchDepth   how many roads out to search
     *
     * @return the number of roads needed, or 500 if it can't be done
     */
    private int recalcLongestRoadETAAux
        (final int startNode, final int pathLength, final int lrLength, final int searchDepth)
    {
        return ((Integer) SOCRobotDM.recalcLongestRoadETAAux
            (player, false, startNode, pathLength, lrLength, searchDepth)).intValue();
    }

    /**
     * calculate the largest army ETA
     */
    public void recalcLargestArmyETA()
    {
        int laSize = 0;
        SOCPlayer laPlayer = player.getGame().getPlayerWithLargestArmy();

        if (laPlayer == null)
        {
            ///
            /// no one has largest army
            ///
            laSize = 3;
        }
        else if (laPlayer.getPlayerNumber() == player.getPlayerNumber())
        {
            ///
            /// we have largest army
            ///
            largestArmyETA = 0;

            return;
        }
        else
        {
            laSize = laPlayer.getNumKnights() + 1;
        }

        ///
        /// figure out how many knights we need to buy
        ///
        knightsToBuy = 0;

        if ((player.getNumKnights() + player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) + player.getDevCards().getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT)) < laSize)
        {
            knightsToBuy = laSize - (player.getNumKnights() + player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT));
        }

        if (player.getGame().getNumDevCards() >= knightsToBuy)
        {
            ///
            /// figure out how long it takes to buy this many knights
            ///
            SOCBuildingSpeedEstimate bse = new SOCBuildingSpeedEstimate(player.getNumbers());
            int[] ourBuildingSpeed = bse.getEstimatesFromNothingFast(player.getPortFlags());
            int cardETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CARD];
            largestArmyETA = (cardETA + 1) * knightsToBuy;
        }
        else
        {
            ///
            /// not enough dev cards left
            ///
            largestArmyETA = 500;
        }
    }

    /**
     * update the longest road values for all possible roads
     *
     * longest road value is how much this
     * road would increase our longest road
     * if it were built
     *
     * the longest road potential is how much
     * this road would increase our LR value
     * if other roads supported by this one were
     * built
     */
    public void updateLRValues()
    {
        SOCPlayer dummy = new SOCPlayer(player);
        int lrLength = player.getLongestRoadLength();

        //
        // for each possible road with no necessary roads
        //
        Iterator posRoadsIter = possibleRoads.values().iterator();

        while (posRoadsIter.hasNext())
        {
            SOCPossibleRoad posRoad = (SOCPossibleRoad) posRoadsIter.next();

            if (posRoad.getNecessaryRoads().isEmpty())
            {
                //
                // calc longest road value
                //
                SOCRoad dummyRoad = new SOCRoad(dummy, posRoad.getCoordinates(), null);
                dummy.putPiece(dummyRoad);

                int newLRLength = dummy.calcLongestRoad2();

                if (newLRLength <= lrLength)
                {
                    posRoad.setLRValue(0);
                }
                else
                {
                    posRoad.setLRValue(newLRLength - lrLength);
                }

                //D.ebugPrintln("$$ updateLRValue for "+Integer.toHexString(posRoad.getCoordinates())+" is "+posRoad.getLRValue());
                //
                // update potential LR value
                //
                posRoad.setLRPotential(0);
                updateLRPotential(posRoad, dummy, dummyRoad, lrLength, LR_CALC_LEVEL);
                dummy.removePiece(dummyRoad);
            }
            else
            {
                posRoad.setLRValue(0);
                posRoad.setLRPotential(0);
            }
        }

        dummy.destroyPlayer();
    }

    /**
     * update the potential LR value of a possible road
     * by placing dummy roads and calculating LR
     *
     * @param posRoad   the possible road
     * @param dummy     the dummy player
     * @param lrLength  the current lr length
     * @param level     how many levels of recursion
     */
    public void updateLRPotential(SOCPossibleRoad posRoad, SOCPlayer dummy, SOCRoad dummyRoad, int lrLength, int level)
    {
        //D.ebugPrintln("$$$ updateLRPotential for road at "+Integer.toHexString(posRoad.getCoordinates())+" level="+level);
        //
        // if we've reached the bottom level of recursion, 
        // or if there are no more roads to place from this one.
        // then calc potential LR value
        //
        SOCBoard board = player.getGame().getBoard();
        boolean noMoreExpansion;

        if (level <= 0)
        {
            noMoreExpansion = true;
        }
        else
        {
            noMoreExpansion = false;

            Enumeration adjEdgeEnum = board.getAdjacentEdgesToEdge(dummyRoad.getCoordinates()).elements();

            while (adjEdgeEnum.hasMoreElements())
            {
                Integer adjEdge = (Integer) adjEdgeEnum.nextElement();

                if (dummy.isPotentialRoad(adjEdge.intValue()))
                {
                    noMoreExpansion = false;

                    break;
                }
            }
        }

        if (noMoreExpansion)
        {
            //
            // only update the potential LR if it's bigger than the 
            // current value
            //
            int newPotentialLRValue = dummy.calcLongestRoad2() - lrLength;

            //D.ebugPrintln("$$$ newPotentialLRValue = "+newPotentialLRValue);
            if (newPotentialLRValue > posRoad.getLRPotential())
            {
                posRoad.setLRPotential(newPotentialLRValue);
            }
        }
        else
        {
            //
            // we need to add a new road and recurse
            //
            Enumeration adjEdgeEnum = board.getAdjacentEdgesToEdge(dummyRoad.getCoordinates()).elements();

            while (adjEdgeEnum.hasMoreElements())
            {
                Integer adjEdge = (Integer) adjEdgeEnum.nextElement();

                if (dummy.isPotentialRoad(adjEdge.intValue()))
                {
                    SOCRoad newDummyRoad = new SOCRoad(dummy, adjEdge.intValue(), board);
                    dummy.putPiece(newDummyRoad);
                    updateLRPotential(posRoad, dummy, newDummyRoad, lrLength, level - 1);
                    dummy.removePiece(newDummyRoad);
                }
            }
        }
    }

    /**
     * @return the ETA for winning the game
     */
    public int getWinGameETA()
    {
        return winGameETA;
    }

    /**
     * @return true if this player needs LR to win
     */
    public boolean needsLR()
    {
        return needLR;
    }

    /**
     * @return true if this player needs LA to win
     */
    public boolean needsLA()
    {
        return needLA;
    }

    /**
     * recalculate the ETA for winning the game
     */
    public void recalcWinGameETA()
    {
        int oldWGETA = winGameETA;

        try
        {
            needLR = false;
            needLA = false;
            winGameETA = 0;

            SOCPlayerNumbers tempPlayerNumbers = new SOCPlayerNumbers(player.getNumbers());
            boolean[] tempPortFlags = new boolean[SOCBoard.WOOD_PORT + 1];

            for (int portType = SOCBoard.MISC_PORT;
                    portType <= SOCBoard.WOOD_PORT; portType++)
            {
                tempPortFlags[portType] = player.getPortFlag(portType);
            }

            SOCBuildingSpeedEstimate[] tempSetBSE = new SOCBuildingSpeedEstimate[2];
            SOCBuildingSpeedEstimate[] tempCityBSE = new SOCBuildingSpeedEstimate[2];

            tempCityBSE[0] = new SOCBuildingSpeedEstimate();
            tempCityBSE[1] = new SOCBuildingSpeedEstimate();

            tempSetBSE[0] = new SOCBuildingSpeedEstimate();
            tempSetBSE[1] = new SOCBuildingSpeedEstimate();

            int[][] chosenSetBuildingSpeed = new int[2][4];
            int[][] chosenCityBuildingSpeed = new int[2][4];

            SOCBuildingSpeedEstimate tempBSE = new SOCBuildingSpeedEstimate();

            SOCBuildingSpeedEstimate ourBSE = new SOCBuildingSpeedEstimate(player.getNumbers());
            int[] ourBuildingSpeed = ourBSE.getEstimatesFromNothingFast(tempPortFlags);
            int cityETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CITY];
            int settlementETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.SETTLEMENT];
            int roadETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.ROAD];
            int cardETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CARD];

            int settlementPiecesLeft = player.getNumPieces(SOCPlayingPiece.SETTLEMENT);
            int cityPiecesLeft = player.getNumPieces(SOCPlayingPiece.CITY);
            int citySpotsLeft = possibleCities.size();

            boolean haveLA = false;
            boolean haveLR = false;

            int tempLargestArmyETA = largestArmyETA;
            int tempLongestRoadETA = longestRoadETA;

            SOCPlayer laPlayer = player.getGame().getPlayerWithLargestArmy();
            SOCPlayer lrPlayer = player.getGame().getPlayerWithLongestRoad();

            final SOCBoard board = player.getGame().getBoard();

            if (D.ebugOn)
            {
                if (laPlayer != null)
                {
                    D.ebugPrintln("laPlayer # = " + laPlayer.getPlayerNumber());
                }
                else
                {
                    D.ebugPrintln("laPlayer = null");
                }

                if (lrPlayer != null)
                {
                    D.ebugPrintln("lrPlayer # = " + lrPlayer.getPlayerNumber());
                }
                else
                {
                    D.ebugPrintln("lrPlayer = null");
                }
            }

            if ((laPlayer != null) && (player.getPlayerNumber() == laPlayer.getPlayerNumber()))
            {
                haveLA = true;
            }

            if ((lrPlayer != null) && (player.getPlayerNumber() == lrPlayer.getPlayerNumber()))
            {
                haveLR = true;
            }

            TreeMap posSetsCopy = (TreeMap) possibleSettlements.clone();
            TreeMap posCitiesCopy = (TreeMap) possibleCities.clone();

            int points = player.getTotalVP();
            int fastestETA;

            Queue necRoadQueue = new Queue();

            while (points < SOCGame.VP_WINNER)  // TODO: Hardcoded 10 to win
            {
                D.ebugPrintln("WWW points = " + points);
                D.ebugPrintln("WWW settlementPiecesLeft = " + settlementPiecesLeft);
                D.ebugPrintln("WWW cityPiecesLeft = " + cityPiecesLeft);
                D.ebugPrintln("WWW settlementSpotsLeft = " + posSetsCopy.size());
                D.ebugPrintln("WWW citySpotsLeft = " + posCitiesCopy.size());

                if (D.ebugOn)
                {
                    D.ebugPrint("WWW tempPortFlags: ");

                    for (int portType = SOCBoard.MISC_PORT;
                            portType <= SOCBoard.WOOD_PORT; portType++)
                    {
                        D.ebugPrint(tempPortFlags[portType] + " ");
                    }

                    D.ebugPrintln();
                }

                D.ebugPrintln("WWW settlementETA = " + settlementETA);
                D.ebugPrintln("WWW cityETA = " + cityETA);
                D.ebugPrintln("WWW roadETA = " + roadETA);
                D.ebugPrintln("WWW cardETA = " + cardETA);

                if (points == (SOCGame.VP_WINNER - 1))
                {
                    fastestETA = 500;

                    SOCPossibleSettlement chosenSet = null;

                    if ((settlementPiecesLeft > 0) && (!posSetsCopy.isEmpty()))
                    {
                        Iterator posSetsIter = posSetsCopy.values().iterator();

                        while (posSetsIter.hasNext())
                        {
                            SOCPossibleSettlement posSet = (SOCPossibleSettlement) posSetsIter.next();
                            int posSetETA = settlementETA + (posSet.getNumberOfNecessaryRoads() * roadETA);

                            if (posSetETA < fastestETA)
                            {
                                fastestETA = posSetETA;
                                chosenSet = posSet;
                            }
                        }

                        ///
                        ///  estimate setETA using building speed
                        ///  for settlements and roads from nothing
                        /// 
                        ///  as long as this settlement needs roads
                        ///  add a roadETA to the ETA for this settlement
                        ///
                        if (chosenSet != null)
                        {
                            int totalNecRoads = 0;

                            if (!chosenSet.getNecessaryRoads().isEmpty())
                            {
                                necRoadQueue.clear();
                                necRoadQueue.put(new Pair(new Integer(0), chosenSet.getNecessaryRoads()));

                                while (!necRoadQueue.empty())
                                {
                                    Pair necRoadPair = (Pair) necRoadQueue.get();
                                    Integer number = (Integer) necRoadPair.getA();
                                    Vector necRoads = (Vector) necRoadPair.getB();
                                    totalNecRoads = number.intValue();

                                    if (necRoads.isEmpty())
                                    {
                                        necRoadQueue.clear();
                                    }
                                    else
                                    {
                                        Enumeration necRoadEnum = necRoads.elements();

                                        while (necRoadEnum.hasMoreElements())
                                        {
                                            SOCPossibleRoad nr = (SOCPossibleRoad) necRoadEnum.nextElement();
                                            necRoadQueue.put(new Pair(new Integer(totalNecRoads + 1), nr.getNecessaryRoads()));
                                        }
                                    }
                                }
                            }

                            fastestETA = (settlementETA + (totalNecRoads * roadETA));
                            D.ebugPrintln("WWW # necesesary roads = " + totalNecRoads);
                            D.ebugPrintln("WWW this settlement eta = " + (settlementETA + (totalNecRoads * roadETA)));
                            D.ebugPrintln("WWW settlement is " + chosenSet);
                            D.ebugPrintln("WWW settlement eta = " + fastestETA);
                        }
                        else
                        {
                            fastestETA = 500;
                        }
                    }

                    if ((cityPiecesLeft > 0) && (citySpotsLeft > 0) && (cityETA <= fastestETA))
                    {
                        D.ebugPrintln("WWW city eta = " + cityETA);
                        fastestETA = cityETA;
                    }

                    if (!haveLA && !needLA && (tempLargestArmyETA < fastestETA))
                    {
                        D.ebugPrintln("WWW LA eta = " + tempLargestArmyETA);
                        fastestETA = tempLargestArmyETA;
                    }

                    if (!haveLR && !needLR && (tempLongestRoadETA < fastestETA))
                    {
                        D.ebugPrintln("WWW LR eta = " + tempLongestRoadETA);
                        fastestETA = tempLongestRoadETA;
                    }

                    if (!haveLR && !needLR && (fastestETA == tempLongestRoadETA))
                    {
                        needLR = true;

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": Longest Road");
                        }
                    }
                    else if (!haveLA && !needLA && (fastestETA == tempLargestArmyETA))
                    {
                        needLA = true;

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": Largest Army");
                        }
                    }
                    else if ((cityPiecesLeft > 0) && (citySpotsLeft > 0) && (cityETA == fastestETA))
                    {
                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": City");
                        }
                    }
                    else if (chosenSet != null)
                    {
                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": Stlmt at " + board.nodeCoordToString(chosenSet.getCoordinates()));
                        }
                    }

                    D.ebugPrintln("WWW Adding " + fastestETA + " to win eta");
                    winGameETA += fastestETA;
                    points += 2;
                }
                else
                {
                    //
                    // This is for < 9 vp (not about to win with VP_WINNER points)
                    //
                    //System.out.println("Old Player Numbers = "+tempPlayerNumbers);
                    //System.out.print("Old Ports = ");
                    //for (int i = 0; i <= SOCBoard.WOOD_PORT; i++) {
                    //  System.out.print(tempPortFlags[i]+",");
                    //}
                    //System.out.println();
                    fastestETA = 500;

                    SOCPossibleSettlement[] chosenSet = new SOCPossibleSettlement[2];
                    boolean[][] tempPortFlagsSet = new boolean[2][SOCBoard.WOOD_PORT + 1];
                    SOCPossibleCity[] chosenCity = new SOCPossibleCity[2];
                    chosenSet[0] = null;
                    chosenSet[1] = null;
                    chosenCity[0] = null;
                    chosenCity[1] = null;

                    int twoSettlements = 0;
                    int twoCities = 500;
                    int oneOfEach = 0;
                    int cityBeforeSettlement = 500;
                    int settlementBeforeCity = 500;

                    ///
                    /// two cities
                    ///
                    if ((cityPiecesLeft > 1) && (citySpotsLeft > 1))
                    {
                        // 
                        // get a more accurate estimate by taking the
                        // effect on building speed into account
                        //
                        twoCities = 500;

                        Iterator posCities0Iter = posCitiesCopy.values().iterator();

                        while (posCities0Iter.hasNext())
                        {
                            SOCPossibleCity posCity0 = (SOCPossibleCity) posCities0Iter.next();

                            //
                            // update our building speed estimate
                            //
                            tempPlayerNumbers.updateNumbers(posCity0.getCoordinates(), board);
                            tempCityBSE[0].recalculateEstimates(tempPlayerNumbers);
                            chosenCityBuildingSpeed[0] = tempCityBSE[0].getEstimatesFromNothingFast(tempPortFlags);

                            int tempCityETA = chosenCityBuildingSpeed[0][SOCBuildingSpeedEstimate.CITY];

                            //
                            // estimate time to build the second city
                            //
                            if ((cityETA + tempCityETA) < twoCities)
                            {
                                chosenCity[0] = posCity0;
                                twoCities = (cityETA + tempCityETA);
                            }

                            tempPlayerNumbers.undoUpdateNumbers(posCity0.getCoordinates(), board);
                        }

                        if (twoCities <= fastestETA)
                        {
                            D.ebugPrintln("WWW twoCities = " + twoCities);
                            fastestETA = twoCities;
                        }
                    }

                    ///
                    /// two settlements
                    ///
                    boolean canBuild2Settlements = false;

                    if ((settlementPiecesLeft > 1) && (posSetsCopy.size() > 1))
                    {
                        canBuild2Settlements = true;

                        Vector posSetsToPutBack = new Vector();

                        for (int i = 0; i < 2; i++)
                        {
                            int fastestSetETA = 500;
                            int bestSpeedupTotal = 0;

                            if (posSetsCopy.isEmpty())
                            {
                                canBuild2Settlements = false;
                            }
                            else
                            {
                                Iterator posSetsIter = posSetsCopy.values().iterator();

                                while (posSetsIter.hasNext())
                                {
                                    SOCPossibleSettlement posSet = (SOCPossibleSettlement) posSetsIter.next();
                                    int posSetETA = settlementETA + (posSet.getNumberOfNecessaryRoads() * roadETA);

                                    final int posSetCoord = posSet.getCoordinates();
                                    if (posSetETA < fastestSetETA)
                                    {
                                        fastestSetETA = posSetETA;
                                        tempPlayerNumbers.updateNumbers(posSetCoord, board);

                                        for (int portType = SOCBoard.MISC_PORT;
                                                portType <= SOCBoard.WOOD_PORT;
                                                portType++)
                                        {
                                            tempPortFlagsSet[i][portType] = tempPortFlags[portType];
                                        }
                                        int portType = board.getPortTypeFromNodeCoord(posSetCoord);
                                        if (portType != -1)
                                            tempPortFlagsSet[i][portType] = true;

                                        tempSetBSE[i].recalculateEstimates(tempPlayerNumbers);
                                        chosenSetBuildingSpeed[i] = tempSetBSE[i].getEstimatesFromNothingFast(tempPortFlagsSet[i]);

                                        for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                                buildingType++)
                                        {
                                            if ((ourBuildingSpeed[buildingType] - chosenSetBuildingSpeed[i][buildingType]) > 0)
                                            {
                                                bestSpeedupTotal += (ourBuildingSpeed[buildingType] - chosenSetBuildingSpeed[i][buildingType]);
                                            }
                                        }

                                        tempPlayerNumbers.undoUpdateNumbers(posSetCoord, board);
                                        chosenSet[i] = posSet;
                                    }
                                    else if (posSetETA == fastestSetETA)
                                    {
                                        boolean[] veryTempPortFlags = new boolean[SOCBoard.WOOD_PORT + 1];
                                        tempPlayerNumbers.updateNumbers(posSetCoord, board);

                                        for (int portType = SOCBoard.MISC_PORT;
                                                portType <= SOCBoard.WOOD_PORT;
                                                portType++)
                                        {
                                            veryTempPortFlags[portType] = tempPortFlags[portType];
                                        }
                                        int portType = board.getPortTypeFromNodeCoord(posSetCoord);
                                        if (portType != -1)
                                            veryTempPortFlags[portType] = true;

                                        tempBSE.recalculateEstimates(tempPlayerNumbers);

                                        int[] tempBuildingSpeed = tempBSE.getEstimatesFromNothingFast(veryTempPortFlags);
                                        int tempSpeedupTotal = 0;

                                        //	    boolean ok = true;
                                        for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                                buildingType++)
                                        {
                                            if ((ourBuildingSpeed[buildingType] - tempBuildingSpeed[buildingType]) >= 0)
                                            {
                                                tempSpeedupTotal += (ourBuildingSpeed[buildingType] - tempBuildingSpeed[buildingType]);
                                            }
                                            else
                                            {
                                                //		ok = false;
                                            }
                                        }

                                        //	    if (ok) {
                                        //	      good++;
                                        //	    } else {
                                        //	      bad++;
                                        //	      //
                                        //	      // output the player number data 
                                        //	      //
                                        //	      System.out.println("New Player Numbers = "+tempPlayerNumbers);
                                        //	      System.out.print("New Ports = ");
                                        //	      for (int k = 0; k <= SOCBoard.WOOD_PORT; k++) {
                                        //		System.out.print(veryTempPortFlags[k]+",");
                                        //	      }
                                        //	      System.out.println();
                                        //	    }
                                        tempPlayerNumbers.undoUpdateNumbers(posSetCoord, board);

                                        if (tempSpeedupTotal > bestSpeedupTotal)
                                        {
                                            fastestSetETA = posSetETA;
                                            bestSpeedupTotal = tempSpeedupTotal;

                                            for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                                    buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                                    buildingType++)
                                            {
                                                chosenSetBuildingSpeed[i][buildingType] = tempBuildingSpeed[buildingType];
                                            }

                                            for (portType = SOCBoard.MISC_PORT;
                                                    portType <= SOCBoard.WOOD_PORT;
                                                    portType++)
                                            {
                                                tempPortFlagsSet[i][portType] = veryTempPortFlags[portType];
                                            }

                                            chosenSet[i] = posSet;
                                        }
                                    }
                                }

                                ///
                                ///  estimate setETA using building speed
                                ///  for settlements and roads from nothing
                                /// 
                                ///  as long as this settlement needs roads
                                ///  add a roadETA to the ETA for this settlement
                                ///
                                int totalNecRoads = 0;

                                if (!chosenSet[i].getNecessaryRoads().isEmpty())
                                {
                                    necRoadQueue.clear();
                                    necRoadQueue.put(new Pair(new Integer(0), chosenSet[i].getNecessaryRoads()));

                                    while (!necRoadQueue.empty())
                                    {
                                        Pair necRoadPair = (Pair) necRoadQueue.get();
                                        Integer number = (Integer) necRoadPair.getA();
                                        Vector necRoads = (Vector) necRoadPair.getB();
                                        totalNecRoads = number.intValue();

                                        if (necRoads.isEmpty())
                                        {
                                            necRoadQueue.clear();
                                        }
                                        else
                                        {
                                            Enumeration necRoadEnum = necRoads.elements();

                                            while (necRoadEnum.hasMoreElements())
                                            {
                                                SOCPossibleRoad nr = (SOCPossibleRoad) necRoadEnum.nextElement();
                                                necRoadQueue.put(new Pair(new Integer(totalNecRoads + 1), nr.getNecessaryRoads()));
                                            }
                                        }
                                    }
                                }

                                D.ebugPrintln("WWW # necesesary roads = " + totalNecRoads);
                                D.ebugPrintln("WWW this settlement eta = " + (settlementETA + (totalNecRoads * roadETA)));

                                if ((i == 0) && (chosenSet[0] != null))
                                {
                                    posSetsCopy.remove(new Integer(chosenSet[0].getCoordinates()));

                                    Enumeration conflicts = chosenSet[0].getConflicts().elements();

                                    while (conflicts.hasMoreElements())
                                    {
                                        SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflicts.nextElement();
                                        Integer conflictInt = new Integer(conflict.getCoordinates());
                                        SOCPossibleSettlement possibleConflict = (SOCPossibleSettlement) posSetsCopy.get(conflictInt);

                                        if (possibleConflict != null)
                                        {
                                            posSetsToPutBack.add(possibleConflict);
                                            posSetsCopy.remove(conflictInt);
                                        }
                                    }

                                    twoSettlements += (settlementETA + (totalNecRoads * roadETA));
                                }

                                if ((i == 1) && (chosenSet[1] != null))
                                {
                                    // 
                                    // get a more accurate estimate by taking the
                                    // effect on building speed into account
                                    //
                                    int tempSettlementETA = chosenSetBuildingSpeed[0][SOCBuildingSpeedEstimate.SETTLEMENT];
                                    int tempRoadETA = chosenSetBuildingSpeed[0][SOCBuildingSpeedEstimate.ROAD];
                                    twoSettlements += (tempSettlementETA + (totalNecRoads * tempRoadETA));
                                }
                            }
                        }

                        posSetsCopy.put(new Integer(chosenSet[0].getCoordinates()), chosenSet[0]);

                        Iterator posSetsToPutBackIter = posSetsToPutBack.iterator();

                        while (posSetsToPutBackIter.hasNext())
                        {
                            SOCPossibleSettlement tmpPosSet = (SOCPossibleSettlement) posSetsToPutBackIter.next();
                            posSetsCopy.put(new Integer(tmpPosSet.getCoordinates()), tmpPosSet);
                        }

                        if (canBuild2Settlements && (twoSettlements <= fastestETA))
                        {
                            D.ebugPrintln("WWW 2 * settlement = " + twoSettlements);
                            fastestETA = twoSettlements;
                        }
                    }

                    ///
                    /// one of each
                    ///
                    if ((cityPiecesLeft > 0) && (((settlementPiecesLeft > 0) && (citySpotsLeft >= 0)) || ((settlementPiecesLeft >= 0) && (citySpotsLeft > 0))) && !posSetsCopy.isEmpty())
                    {
                        //
                        // choose a city to build
                        //
                        if ((chosenCity[0] == null) && (citySpotsLeft > 0))
                        {
                            int bestCitySpeedupTotal = 0;
                            Iterator posCities0Iter = posCitiesCopy.values().iterator();

                            while (posCities0Iter.hasNext())
                            {
                                SOCPossibleCity posCity0 = (SOCPossibleCity) posCities0Iter.next();
                                tempPlayerNumbers.updateNumbers(posCity0.getCoordinates(), board);
                                tempBSE.recalculateEstimates(tempPlayerNumbers);

                                int[] tempBuildingSpeed = tempBSE.getEstimatesFromNothingFast(tempPortFlags);
                                int tempSpeedupTotal = 0;

                                //		boolean ok = true;
                                for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                        buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                        buildingType++)
                                {
                                    if ((ourBuildingSpeed[buildingType] - tempBuildingSpeed[buildingType]) >= 0)
                                    {
                                        tempSpeedupTotal += (ourBuildingSpeed[buildingType] - tempBuildingSpeed[buildingType]);
                                    }
                                    else
                                    {
                                        //		    ok = false;
                                    }
                                }

                                //		if (ok) {
                                //		  good++;
                                //		} else {
                                //		  bad++;
                                //		  //
                                //		  // output the player number data 
                                //		  //
                                //		  System.out.println("New Player Numbers = "+tempPlayerNumbers);
                                //		  System.out.print("New Ports = ");
                                //		  for (int i = 0; i <= SOCBoard.WOOD_PORT; i++) {
                                //		    System.out.print(tempPortFlags[i]+",");
                                //		  }
                                //		  System.out.println();
                                //		}
                                tempPlayerNumbers.undoUpdateNumbers(posCity0.getCoordinates(), board);

                                if (tempSpeedupTotal >= bestCitySpeedupTotal)
                                {
                                    bestCitySpeedupTotal = tempSpeedupTotal;

                                    for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                            buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                            buildingType++)
                                    {
                                        chosenCityBuildingSpeed[0][buildingType] = tempBuildingSpeed[buildingType];
                                    }

                                    chosenCity[0] = posCity0;
                                }
                            }
                        }

                        //
                        // choose a settlement to build
                        //
                        if (chosenSet[0] == null)
                        {
                            int fastestSetETA = 500;
                            int bestSpeedupTotal = 0;
                            Iterator posSetsIter = posSetsCopy.values().iterator();

                            while (posSetsIter.hasNext())
                            {
                                SOCPossibleSettlement posSet = (SOCPossibleSettlement) posSetsIter.next();
                                int posSetETA = settlementETA + (posSet.getNumberOfNecessaryRoads() * roadETA);

                                if (posSetETA < fastestSetETA)
                                {
                                    fastestSetETA = posSetETA;
                                    tempPlayerNumbers.updateNumbers(posSet.getCoordinates(), board);

                                    for (int portType = SOCBoard.MISC_PORT;
                                            portType <= SOCBoard.WOOD_PORT;
                                            portType++)
                                    {
                                        tempPortFlagsSet[0][portType] = tempPortFlags[portType];
                                    }
                                    int portType = board.getPortTypeFromNodeCoord(posSet.getCoordinates());
                                    if (portType != -1)
                                        tempPortFlagsSet[0][portType] = true;

                                    tempSetBSE[0].recalculateEstimates(tempPlayerNumbers);
                                    chosenSetBuildingSpeed[0] = tempSetBSE[0].getEstimatesFromNothingFast(tempPortFlagsSet[0]);

                                    for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                            buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                            buildingType++)
                                    {
                                        if ((ourBuildingSpeed[buildingType] - chosenSetBuildingSpeed[0][buildingType]) > 0)
                                        {
                                            bestSpeedupTotal += (ourBuildingSpeed[buildingType] - chosenSetBuildingSpeed[0][buildingType]);
                                        }
                                    }

                                    tempPlayerNumbers.undoUpdateNumbers(posSet.getCoordinates(), board);
                                    chosenSet[0] = posSet;
                                }
                                else if (posSetETA == fastestSetETA)
                                {
                                    boolean[] veryTempPortFlags = new boolean[SOCBoard.WOOD_PORT + 1];
                                    tempPlayerNumbers.updateNumbers(posSet.getCoordinates(), board);

                                    for (int portType = SOCBoard.MISC_PORT;
                                            portType <= SOCBoard.WOOD_PORT;
                                            portType++)
                                    {
                                        veryTempPortFlags[portType] = tempPortFlags[portType];
                                    }
                                    int portType = board.getPortTypeFromNodeCoord(posSet.getCoordinates());
                                    if (portType != -1)
                                        veryTempPortFlags[portType] = true;

                                    tempBSE.recalculateEstimates(tempPlayerNumbers);

                                    int[] tempBuildingSpeed = tempBSE.getEstimatesFromNothingFast(veryTempPortFlags);
                                    int tempSpeedupTotal = 0;

                                    //		  boolean ok = true;
                                    for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                            buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                            buildingType++)
                                    {
                                        if ((ourBuildingSpeed[buildingType] - tempBuildingSpeed[buildingType]) >= 0)
                                        {
                                            tempSpeedupTotal += (ourBuildingSpeed[buildingType] - tempBuildingSpeed[buildingType]);
                                        }
                                        else
                                        {
                                            //		      ok = false;
                                        }
                                    }

                                    //		  if (ok) {
                                    //		    good++;
                                    //		  } else {
                                    //		    bad++;
                                    //		    //
                                    //		    // output the player number data 
                                    //		    //
                                    //		    System.out.println("New Player Numbers = "+tempPlayerNumbers);
                                    //		    System.out.print("New Ports = ");
                                    //		    for (int i = 0; i <= SOCBoard.WOOD_PORT; i++) {
                                    //		      System.out.print(tempPortFlags[i]+",");
                                    //		    }
                                    //		    System.out.println();
                                    //		  }
                                    tempPlayerNumbers.undoUpdateNumbers(posSet.getCoordinates(), board);

                                    if (tempSpeedupTotal > bestSpeedupTotal)
                                    {
                                        fastestSetETA = posSetETA;
                                        bestSpeedupTotal = tempSpeedupTotal;

                                        for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                                buildingType++)
                                        {
                                            chosenSetBuildingSpeed[0][buildingType] = tempBuildingSpeed[buildingType];
                                        }

                                        for (portType = SOCBoard.MISC_PORT;
                                                portType <= SOCBoard.WOOD_PORT;
                                                portType++)
                                        {
                                            tempPortFlagsSet[0][portType] = veryTempPortFlags[portType];
                                        }

                                        chosenSet[0] = posSet;
                                    }
                                }
                            }
                        }

                        if (citySpotsLeft == 0)
                        {
                            chosenCity[0] = new SOCPossibleCity(player, chosenSet[0].getCoordinates());
                        }

                        ///
                        ///  estimate setETA using building speed
                        ///  for settlements and roads from nothing
                        /// 
                        ///  as long as this settlement needs roads
                        ///  add a roadETA to the ETA for this settlement
                        ///
                        int totalNecRoads = 0;

                        if (!chosenSet[0].getNecessaryRoads().isEmpty())
                        {
                            necRoadQueue.clear();
                            necRoadQueue.put(new Pair(new Integer(0), chosenSet[0].getNecessaryRoads()));

                            while (!necRoadQueue.empty())
                            {
                                Pair necRoadPair = (Pair) necRoadQueue.get();
                                Integer number = (Integer) necRoadPair.getA();
                                Vector necRoads = (Vector) necRoadPair.getB();
                                totalNecRoads = number.intValue();

                                if (necRoads.isEmpty())
                                {
                                    necRoadQueue.clear();
                                }
                                else
                                {
                                    Enumeration necRoadEnum = necRoads.elements();

                                    while (necRoadEnum.hasMoreElements())
                                    {
                                        SOCPossibleRoad nr = (SOCPossibleRoad) necRoadEnum.nextElement();
                                        necRoadQueue.put(new Pair(new Integer(totalNecRoads + 1), nr.getNecessaryRoads()));
                                    }
                                }
                            }
                        }

                        D.ebugPrintln("WWW # necesesary roads = " + totalNecRoads);
                        D.ebugPrintln("WWW this settlement eta = " + (settlementETA + (totalNecRoads * roadETA)));

                        // 
                        // get a more accurate estimate by taking the
                        // effect on building speed into account
                        //
                        if ((settlementPiecesLeft > 0) && (citySpotsLeft >= 0))
                        {
                            int tempCityETA = chosenSetBuildingSpeed[0][SOCBuildingSpeedEstimate.CITY];
                            settlementBeforeCity = tempCityETA + (settlementETA + (totalNecRoads * roadETA));
                        }

                        if ((settlementPiecesLeft >= 0) && (citySpotsLeft > 0))
                        {
                            int tempSettlementETA = chosenCityBuildingSpeed[0][SOCBuildingSpeedEstimate.SETTLEMENT];
                            int tempRoadETA = chosenCityBuildingSpeed[0][SOCBuildingSpeedEstimate.ROAD];
                            cityBeforeSettlement = cityETA + (tempSettlementETA + (totalNecRoads * tempRoadETA));
                        }

                        if (settlementBeforeCity < cityBeforeSettlement)
                        {
                            oneOfEach = settlementBeforeCity;
                        }
                        else
                        {
                            oneOfEach = cityBeforeSettlement;
                        }

                        if (oneOfEach <= fastestETA)
                        {
                            D.ebugPrintln("WWW one of each = " + oneOfEach);
                            fastestETA = oneOfEach;
                        }
                    }

                    ///
                    /// largest army
                    ///				
                    if (!haveLA && !needLA && (points > 5))
                    {
                        //
                        // recalc LA eta given our new building speed
                        //
                        int laSize = 0;

                        if (laPlayer == null)
                        {
                            ///
                            /// no one has largest army
                            ///
                            laSize = 3;
                        }
                        else if (laPlayer.getPlayerNumber() == player.getPlayerNumber())
                        {
                            ///
                            /// we have largest army
                            ///
                            D.ebugPrintln("WWW ERROR CALCULATING LA ETA");
                        }
                        else
                        {
                            laSize = laPlayer.getNumKnights() + 1;
                        }

                        ///
                        /// figure out how many knights we need to buy
                        ///
                        knightsToBuy = 0;

                        if ((player.getNumKnights() + player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) + player.getDevCards().getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT)) < laSize)
                        {
                            knightsToBuy = laSize - (player.getNumKnights() + player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT));
                        }

                        ///
                        /// figure out how long it takes to buy this many knights
                        ///
                        if (player.getGame().getNumDevCards() >= knightsToBuy)
                        {
                            tempLargestArmyETA = (cardETA + 1) * knightsToBuy;
                        }
                        else
                        {
                            tempLargestArmyETA = 500;
                        }

                        D.ebugPrintln("WWW LA eta = " + tempLargestArmyETA);

                        if (tempLargestArmyETA < fastestETA)
                        {
                            fastestETA = tempLargestArmyETA;
                        }
                    }

                    ///
                    /// longest road
                    ///
                    //
                    // recalc LR eta given our new building speed
                    //
                    if (!haveLR && !needLR && (points > 5))
                    {
                        tempLongestRoadETA = roadETA * roadsToGo;
                        D.ebugPrintln("WWW LR eta = " + tempLongestRoadETA);

                        if (tempLongestRoadETA < fastestETA)
                        {
                            fastestETA = tempLongestRoadETA;
                        }
                    }

                    ///
                    /// implement the fastest scenario
                    ///
                    D.ebugPrintln("WWW Adding " + fastestETA + " to win eta");
                    points += 2;
                    winGameETA += fastestETA;
                    D.ebugPrintln("WWW WGETA SO FAR FOR PLAYER " + player.getPlayerNumber() + " = " + winGameETA);

                    if ((settlementPiecesLeft > 1) && (posSetsCopy.size() > 1) && (canBuild2Settlements) && (fastestETA == twoSettlements))
                    {
                        Integer chosenSet0Int = new Integer(chosenSet[0].getCoordinates());
                        Integer chosenSet1Int = new Integer(chosenSet[1].getCoordinates());
                        posSetsCopy.remove(chosenSet0Int);
                        posSetsCopy.remove(chosenSet1Int);
                        posCitiesCopy.put(chosenSet0Int, new SOCPossibleCity(player, chosenSet[0].getCoordinates()));
                        posCitiesCopy.put(chosenSet1Int, new SOCPossibleCity(player, chosenSet[1].getCoordinates()));

                        //
                        // remove possible settlements that are conflicts
                        //
                        Enumeration conflicts = chosenSet[0].getConflicts().elements();

                        while (conflicts.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflicts.nextElement();
                            Integer conflictInt = new Integer(conflict.getCoordinates());
                            posSetsCopy.remove(conflictInt);
                        }

                        conflicts = chosenSet[1].getConflicts().elements();

                        while (conflicts.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflicts.nextElement();
                            Integer conflictInt = new Integer(conflict.getCoordinates());
                            posSetsCopy.remove(conflictInt);
                        }

                        settlementPiecesLeft -= 2;
                        citySpotsLeft += 2;

                        //
                        // update our building speed estimate
                        //
                        tempPlayerNumbers.updateNumbers(chosenSet[0].getCoordinates(), board);
                        tempPlayerNumbers.updateNumbers(chosenSet[1].getCoordinates(), board);

                        int portType = board.getPortTypeFromNodeCoord(chosenSet[0].getCoordinates());
                        if (portType != -1)
                            tempPortFlags[portType] = true;
                        portType = board.getPortTypeFromNodeCoord(chosenSet[1].getCoordinates());
                        if (portType != -1)
                            tempPortFlags[portType] = true;

                        ourBSE.recalculateEstimates(tempPlayerNumbers);
                        ourBuildingSpeed = ourBSE.getEstimatesFromNothingFast(tempPortFlags);
                        settlementETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.SETTLEMENT];
                        roadETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.ROAD];
                        cityETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CITY];
                        cardETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CARD];
                        D.ebugPrintln("WWW  * build two settlements");
                        D.ebugPrintln("WWW    settlement 1: " + board.nodeCoordToString(chosenSet[0].getCoordinates()));
                        D.ebugPrintln("WWW    settlement 2: " + board.nodeCoordToString(chosenSet[1].getCoordinates()));

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": Stlmt at " + board.nodeCoordToString(chosenSet[0].getCoordinates()) + "; Stlmt at " + board.nodeCoordToString(chosenSet[1].getCoordinates()));
                        }
                    }
                    else if (((cityPiecesLeft > 0) && (((settlementPiecesLeft > 0) && (citySpotsLeft >= 0)) || ((settlementPiecesLeft >= 0) && (citySpotsLeft > 0))) && !posSetsCopy.isEmpty()) && (fastestETA == oneOfEach))
                    {
                        Integer chosenSet0Int = new Integer(chosenSet[0].getCoordinates());
                        posSetsCopy.remove(chosenSet0Int);

                        if (chosenSet[0].getCoordinates() != chosenCity[0].getCoordinates())
                        {
                            posCitiesCopy.put(chosenSet0Int, new SOCPossibleCity(player, chosenSet[0].getCoordinates()));
                        }

                        posCitiesCopy.remove(new Integer(chosenCity[0].getCoordinates()));
                        cityPiecesLeft -= 1;

                        //
                        // remove possible settlements that are conflicts
                        //
                        Enumeration conflicts = chosenSet[0].getConflicts().elements();

                        while (conflicts.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflicts.nextElement();
                            Integer conflictInt = new Integer(conflict.getCoordinates());
                            posSetsCopy.remove(conflictInt);
                        }

                        //
                        // update our building speed estimate
                        //
                        tempPlayerNumbers.updateNumbers(chosenSet[0].getCoordinates(), board);

                        int portType = board.getPortTypeFromNodeCoord(chosenSet[0].getCoordinates());
                        if (portType != -1)
                            tempPortFlags[portType] = true;

                        tempPlayerNumbers.updateNumbers(chosenCity[0].getCoordinates(), board);
                        ourBSE.recalculateEstimates(tempPlayerNumbers);
                        ourBuildingSpeed = ourBSE.getEstimatesFromNothingFast(tempPortFlags);
                        settlementETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.SETTLEMENT];
                        roadETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.ROAD];
                        cityETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CITY];
                        cardETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CARD];
                        D.ebugPrintln("WWW  * build a settlement and a city");
                        D.ebugPrintln("WWW    settlement at " + board.nodeCoordToString(chosenSet[0].getCoordinates()));
                        D.ebugPrintln("WWW    city at " + board.nodeCoordToString(chosenCity[0].getCoordinates()));

                        if (brain.getDRecorder().isOn())
                        {
                            if (fastestETA == settlementBeforeCity)
                            {
                                brain.getDRecorder().record(fastestETA + ": Stlmt at " + board.nodeCoordToString(chosenSet[0].getCoordinates()) + "; City at " + board.nodeCoordToString(chosenCity[0].getCoordinates()));
                            }
                            else
                            {
                                brain.getDRecorder().record(fastestETA + ": City at " + board.nodeCoordToString(chosenCity[0].getCoordinates()) + "; Stlmt at " + board.nodeCoordToString(chosenSet[0].getCoordinates()));
                            }
                        }
                    }
                    else if ((cityPiecesLeft > 1) && (citySpotsLeft > 1) && (fastestETA == twoCities))
                    {
                        posCitiesCopy.remove(new Integer(chosenCity[0].getCoordinates()));

                        //
                        // update our building speed estimate
                        //
                        tempPlayerNumbers.updateNumbers(chosenCity[0].getCoordinates(), board);

                        //
                        // pick the second city to build
                        //
                        int bestCitySpeedupTotal = 0;
                        Iterator posCities1Iter = posCitiesCopy.values().iterator();

                        while (posCities1Iter.hasNext())
                        {
                            SOCPossibleCity posCity1 = (SOCPossibleCity) posCities1Iter.next();
                            tempPlayerNumbers.updateNumbers(posCity1.getCoordinates(), board);
                            D.ebugPrintln("tempPlayerNumbers = " + tempPlayerNumbers);
                            tempBSE.recalculateEstimates(tempPlayerNumbers);

                            int[] tempBuildingSpeed = tempBSE.getEstimatesFromNothingFast(tempPortFlags);
                            int tempSpeedupTotal = 0;

                            //boolean ok = true;
                            for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                    buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                    buildingType++)
                            {
                                D.ebugPrintln("ourBuildingSpeed[" + buildingType + "] = " + ourBuildingSpeed[buildingType]);
                                D.ebugPrintln("tempBuildingSpeed[" + buildingType + "] = " + tempBuildingSpeed[buildingType]);

                                if ((ourBuildingSpeed[buildingType] - tempBuildingSpeed[buildingType]) >= 0)
                                {
                                    tempSpeedupTotal += (ourBuildingSpeed[buildingType] - tempBuildingSpeed[buildingType]);
                                }
                                else
                                {
                                    //ok = false;
                                }
                            }

                            //      if (ok) {
                            //	good++;
                            //      } else {
                            //	bad++;
                            //	//
                            //	// output the player number data 
                            //	//
                            //	System.out.println("New Player Numbers = "+tempPlayerNumbers);
                            //	System.out.print("New Ports = ");
                            //	for (int i = 0; i <= SOCBoard.WOOD_PORT; i++) {
                            //	  System.out.print(tempPortFlags[i]+",");
                            //	}
                            //	System.out.println();
                            //      }
                            tempPlayerNumbers.undoUpdateNumbers(posCity1.getCoordinates(), board);
                            D.ebugPrintln("tempPlayerNumbers = " + tempPlayerNumbers);
                            D.ebugPrintln("WWW City at " + board.nodeCoordToString(posCity1.getCoordinates()) + " has tempSpeedupTotal = " + tempSpeedupTotal);

                            if (tempSpeedupTotal >= bestCitySpeedupTotal)
                            {
                                bestCitySpeedupTotal = tempSpeedupTotal;
                                chosenCity[1] = posCity1;
                            }
                        }

                        if (chosenCity[1] == null)
                        {
                            System.out.println("OOPS!!!");
                        }
                        else
                        {
                            posCitiesCopy.remove(new Integer(chosenCity[1].getCoordinates()));
                        }

                        settlementPiecesLeft += 2;
                        cityPiecesLeft -= 2;
                        citySpotsLeft -= 2;

                        tempPlayerNumbers.updateNumbers(chosenCity[1].getCoordinates(), board);
                        ourBSE.recalculateEstimates(tempPlayerNumbers);
                        ourBuildingSpeed = ourBSE.getEstimatesFromNothingFast(tempPortFlags);
                        settlementETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.SETTLEMENT];
                        roadETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.ROAD];
                        cityETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CITY];
                        cardETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CARD];
                        D.ebugPrintln("WWW  * build 2 cities");
                        D.ebugPrintln("WWW    city 1: " + board.nodeCoordToString(chosenCity[0].getCoordinates()));
                        D.ebugPrintln("WWW    city 2: " + board.nodeCoordToString(chosenCity[1].getCoordinates()));

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": City at " + board.nodeCoordToString(chosenCity[0].getCoordinates()) + "; City at " + board.nodeCoordToString(chosenCity[1].getCoordinates()));
                        }
                    }
                    else if (!haveLR && !needLR && (points > 5) && (fastestETA == tempLongestRoadETA))
                    {
                        needLR = true;
                        D.ebugPrintln("WWW  * take longest road");

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": Longest Road");
                        }
                    }
                    else if (!haveLA && !needLA && (points > 5) && (fastestETA == tempLargestArmyETA))
                    {
                        needLA = true;
                        D.ebugPrintln("WWW  * take largest army");

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": Largest Army");
                        }
                    }
                }
            }

            D.ebugPrintln("WWW TOTAL WGETA FOR PLAYER " + player.getPlayerNumber() + " = " + winGameETA);

            if (brain.getDRecorder().isOn())
            {
                brain.getDRecorder().record("Total WGETA for " + player.getName() + " = " + winGameETA);
                brain.getDRecorder().record("--------------------");
            }
        }
        catch (Exception e)
        {
            winGameETA = oldWGETA;
            System.out.println("Exception in recalcWinGameETA - " + e);
            e.printStackTrace();
        }

        //System.out.println("good = "+good+" bad = "+bad);
        //System.out.println();
    }

    /**
     * See how building a piece impacts the game
     *
     * @param piece      the piece to build
     * @param game       the game
     * @param trackers   the player trackers
     *
     * @return a copy of the player trackers with the new piece in place
     */
    public static HashMap tryPutPiece(SOCPlayingPiece piece, SOCGame game, HashMap trackers)
    {
        HashMap trackersCopy = SOCPlayerTracker.copyPlayerTrackers(trackers);

        if (piece != null)
        {
            game.putTempPiece(piece);

            Iterator trackersCopyIter = trackersCopy.values().iterator();

            while (trackersCopyIter.hasNext())
            {
                SOCPlayerTracker trackerCopy = (SOCPlayerTracker) trackersCopyIter.next();

                switch (piece.getType())
                {
                case SOCPlayingPiece.ROAD:
                    trackerCopy.addNewRoad((SOCRoad) piece, trackersCopy);

                    break;

                case SOCPlayingPiece.SETTLEMENT:
                    trackerCopy.addNewSettlement((SOCSettlement) piece, trackersCopy);

                    break;

                case SOCPlayingPiece.CITY:
                    trackerCopy.addOurNewCity((SOCCity) piece);

                    break;
                }
            }
        }

        return trackersCopy;
    }

    /**
     * same as tryPutPiece, but we don't make a copy of the player trackers
     * instead you supply the copy
     *
     * @param piece      the piece to build
     * @param game       the game
     * @param trackers   the player trackers
     */
    public static void tryPutPieceNoCopy(SOCPlayingPiece piece, SOCGame game, HashMap trackers)
    {
        if (piece != null)
        {
            game.putTempPiece(piece);

            Iterator trackersIter = trackers.values().iterator();

            while (trackersIter.hasNext())
            {
                SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                switch (piece.getType())
                {
                case SOCPlayingPiece.ROAD:
                    tracker.addNewRoad((SOCRoad) piece, trackers);

                    break;

                case SOCPlayingPiece.SETTLEMENT:
                    tracker.addNewSettlement((SOCSettlement) piece, trackers);

                    break;

                case SOCPlayingPiece.CITY:
                    tracker.addOurNewCity((SOCCity) piece);

                    break;
                }
            }
        }
    }

    /**
     * Reset the game back to before we put the temp piece
     *
     * @param piece      the piece to remove
     * @param game       the game
     *
     */
    public static void undoTryPutPiece(SOCPlayingPiece piece, SOCGame game)
    {
        if (piece != null)
        {
            game.undoPutTempPiece(piece);
        }
    }

    /**
     * debug output for player trackers
     *
     * @param playerTrackers  the player trackers
     */
    public static void playerTrackersDebug(HashMap playerTrackers)
    {
        if (D.ebugOn)
        {
            Iterator trackersIter = playerTrackers.values().iterator();

            while (trackersIter.hasNext())
            {
                SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
                D.ebugPrintln("%%%%%%%%% TRACKER FOR PLAYER " + tracker.getPlayer().getPlayerNumber());
                D.ebugPrintln("   LONGEST ROAD ETA = " + tracker.getLongestRoadETA());
                D.ebugPrintln("   LARGEST ARMY ETA = " + tracker.getLargestArmyETA());

                Iterator prIter = tracker.getPossibleRoads().values().iterator();

                while (prIter.hasNext())
                {
                    SOCPossibleRoad pr = (SOCPossibleRoad) prIter.next();
                    D.ebugPrintln("%%% possible road at " + Integer.toHexString(pr.getCoordinates()));
                    D.ebugPrint("   eta:" + pr.getETA());
                    D.ebugPrint("   this road needs:");

                    Enumeration nrEnum = pr.getNecessaryRoads().elements();

                    while (nrEnum.hasMoreElements())
                    {
                        D.ebugPrint(" " + Integer.toHexString(((SOCPossiblePiece) nrEnum.nextElement()).getCoordinates()));
                    }

                    D.ebugPrintln();
                    D.ebugPrint("   this road supports:");

                    Enumeration newPosEnum = pr.getNewPossibilities().elements();

                    while (newPosEnum.hasMoreElements())
                    {
                        D.ebugPrint(" " + Integer.toHexString(((SOCPossiblePiece) newPosEnum.nextElement()).getCoordinates()));
                    }

                    D.ebugPrintln();
                    D.ebugPrint("   threats:");

                    Enumeration threatEnum = pr.getThreats().elements();

                    while (threatEnum.hasMoreElements())
                    {
                        SOCPossiblePiece threat = (SOCPossiblePiece) threatEnum.nextElement();
                        D.ebugPrint(" " + threat.getPlayer().getPlayerNumber() + ":" + threat.getType() + ":" + Integer.toHexString(threat.getCoordinates()));
                    }

                    D.ebugPrintln();
                    D.ebugPrintln("   LR value=" + pr.getLRValue() + " LR Potential=" + pr.getLRPotential());
                }

                Iterator psIter = tracker.getPossibleSettlements().values().iterator();

                while (psIter.hasNext())
                {
                    SOCPossibleSettlement ps = (SOCPossibleSettlement) psIter.next();
                    D.ebugPrintln("%%% possible settlement at " + Integer.toHexString(ps.getCoordinates()));
                    D.ebugPrint("   eta:" + ps.getETA());
                    D.ebugPrint("%%%   conflicts");

                    Enumeration conflictEnum = ps.getConflicts().elements();

                    while (conflictEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictEnum.nextElement();
                        D.ebugPrint(" " + conflict.getPlayer().getPlayerNumber() + ":" + Integer.toHexString(conflict.getCoordinates()));
                    }

                    D.ebugPrintln();
                    D.ebugPrint("%%%   necessary roads");

                    Enumeration nrEnum = ps.getNecessaryRoads().elements();

                    while (nrEnum.hasMoreElements())
                    {
                        SOCPossibleRoad nr = (SOCPossibleRoad) nrEnum.nextElement();
                        D.ebugPrint(" " + Integer.toHexString(nr.getCoordinates()));
                    }

                    D.ebugPrintln();
                    D.ebugPrint("   threats:");

                    Enumeration threatEnum = ps.getThreats().elements();

                    while (threatEnum.hasMoreElements())
                    {
                        SOCPossiblePiece threat = (SOCPossiblePiece) threatEnum.nextElement();
                        D.ebugPrint(" " + threat.getPlayer().getPlayerNumber() + ":" + threat.getType() + ":" + Integer.toHexString(threat.getCoordinates()));
                    }

                    D.ebugPrintln();
                }

                Iterator pcIter = tracker.getPossibleCities().values().iterator();

                while (pcIter.hasNext())
                {
                    SOCPossibleCity pc = (SOCPossibleCity) pcIter.next();
                    D.ebugPrintln("%%% possible city at " + Integer.toHexString(pc.getCoordinates()));
                    D.ebugPrintln("   eta:" + pc.getETA());
                }
            }
        }
    }

    /**
     * update winGameETAs for player trackers
     *
     * @param playerTrackers  the player trackers
     */
    public static void updateWinGameETAs(HashMap playerTrackers)
    {
        Iterator playerTrackersIter = playerTrackers.values().iterator();

        while (playerTrackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) playerTrackersIter.next();

            //D.ebugPrintln("%%%%%%%%% TRACKER FOR PLAYER "+tracker.getPlayer().getPlayerNumber());
            try
            {
                tracker.recalcLongestRoadETA();
                tracker.recalcLargestArmyETA();
                tracker.recalcWinGameETA();

                //D.ebugPrintln("needs LA = "+tracker.needsLA());
                //D.ebugPrintln("largestArmyETA = "+tracker.getLargestArmyETA());
                //D.ebugPrintln("needs LR = "+tracker.needsLR());
                //D.ebugPrintln("longestRoadETA = "+tracker.getLongestRoadETA());
                //D.ebugPrintln("winGameETA = "+tracker.getWinGameETA());
            }
            catch (NullPointerException e)
            {
                System.out.println("Null Pointer Exception calculating winGameETA");
                e.printStackTrace();
            }
        }
    }
}
