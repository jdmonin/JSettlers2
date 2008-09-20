/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2008 Jeremy D. Monin <jeremy@nand.net>
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
package soc.robot;

import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.log4j.Logger;

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
import soc.message.SOCCancelBuildRequest;
import soc.util.CutoffExceededException;
import soc.util.NodeLenVis;
import soc.util.Pair;
import soc.util.Queue;


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
    /** debug logging; see {@link #log} */
    private transient static Logger staticLog = Logger.getLogger(SOCPlayerTracker.class.getName());

    protected static final DecimalFormat df1 = new DecimalFormat("###0.00");
    static protected int EXPAND_LEVEL = 1;
    static protected int LR_CALC_LEVEL = 2;
    protected SOCRobotBrain brain;
    protected SOCPlayer player;
    protected TreeMap<Integer, SOCPossibleSettlement> possibleSettlements;
    protected TreeMap<Integer, SOCPossibleRoad> possibleRoads;
    protected TreeMap<Integer, SOCPossibleCity> possibleCities;
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

    /** debug logging; see {@link #staticLog} */
    private transient Logger log = Logger.getLogger(this.getClass().getName());

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
        possibleRoads = new TreeMap<Integer, SOCPossibleRoad>();
        possibleSettlements = new TreeMap<Integer, SOCPossibleSettlement>();
        possibleCities = new TreeMap<Integer, SOCPossibleCity>();
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
        possibleRoads = new TreeMap<Integer, SOCPossibleRoad>();
        possibleSettlements = new TreeMap<Integer, SOCPossibleSettlement>();
        possibleCities = new TreeMap<Integer, SOCPossibleCity>();
        longestRoadETA = pt.getLongestRoadETA();
        roadsToGo = pt.getRoadsToGo();
        largestArmyETA = pt.getLargestArmyETA();
        knightsToBuy = pt.getKnightsToBuy();
        pendingInitSettlement = pt.getPendingInitSettlement();

        //log.debug(">>>>> Copying SOCPlayerTracker for player number "+player.getPlayerNumber());
        //
        // now perform the copy
        //
        // start by just getting all of the possible pieces
        //
        Iterator<SOCPossibleRoad> posRoadsIter = pt.getPossibleRoads().values().iterator();

        while (posRoadsIter.hasNext())
        {
            SOCPossibleRoad posRoad = posRoadsIter.next();
            SOCPossibleRoad posRoadCopy = new SOCPossibleRoad(posRoad);
            possibleRoads.put(new Integer(posRoadCopy.getCoordinates()), posRoadCopy);
        }

        Iterator<SOCPossibleSettlement> posSettlementsIter = pt.getPossibleSettlements().values().iterator();

        while (posSettlementsIter.hasNext())
        {
            SOCPossibleSettlement posSettlement = posSettlementsIter.next();
            SOCPossibleSettlement posSettlementCopy = new SOCPossibleSettlement(posSettlement);
            possibleSettlements.put(new Integer(posSettlementCopy.getCoordinates()), posSettlementCopy);
        }

        Iterator<SOCPossibleCity> posCitiesIter = pt.getPossibleCities().values().iterator();

        while (posCitiesIter.hasNext())
        {
            SOCPossibleCity posCity = posCitiesIter.next();
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
    public static HashMap<Integer, SOCPlayerTracker> copyPlayerTrackers(HashMap trackers)
    {
        HashMap<Integer, SOCPlayerTracker> trackersCopy = new HashMap<Integer, SOCPlayerTracker>(SOCGame.MAXPLAYERS);

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
        //log.debug(">>>>> Making connections between pieces");
        trackersIter = trackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            SOCPlayerTracker trackerCopy = trackersCopy.get(new Integer(tracker.getPlayer().getPlayerNumber()));

            //log.debug(">>>> Player num for tracker is "+tracker.getPlayer().getPlayerNumber()); 
            //log.debug(">>>> Player num for trackerCopy is "+trackerCopy.getPlayer().getPlayerNumber()); 
            TreeMap<Integer, SOCPossibleRoad> possibleRoads = tracker.getPossibleRoads();
            TreeMap<Integer, SOCPossibleRoad> possibleRoadsCopy = trackerCopy.getPossibleRoads();
            TreeMap<Integer, SOCPossibleSettlement> possibleSettlements = tracker.getPossibleSettlements();
            TreeMap<Integer, SOCPossibleSettlement> possibleSettlementsCopy = trackerCopy.getPossibleSettlements();
            Iterator<SOCPossibleRoad> posRoadsIter = possibleRoads.values().iterator();

            while (posRoadsIter.hasNext())
            {
                SOCPossibleRoad posRoad = posRoadsIter.next();
                SOCPossibleRoad posRoadCopy = possibleRoadsCopy.get(new Integer(posRoad.getCoordinates()));

                //log.debug(">>> posRoad     : "+posRoad);
                //log.debug(">>> posRoadCopy : "+posRoadCopy);
                Iterator<SOCPossibleRoad> necRoadsIter = posRoad.getNecessaryRoads().iterator();

                while (necRoadsIter.hasNext())
                {
                    SOCPossibleRoad necRoad = necRoadsIter.next();

                    //log.debug(">> posRoad.necRoad : "+necRoad);
                    //
                    // now find the copy of this necessary road and 
                    // add it to the pos road copy's nec road list
                    //
                    SOCPossibleRoad necRoadCopy = possibleRoadsCopy.get(new Integer(necRoad.getCoordinates()));

                    if (necRoadCopy != null)
                    {
                        posRoadCopy.addNecessaryRoad(necRoadCopy);
                    }
                    else
                    {
                        staticLog.debug("*** ERROR in copyPlayerTrackers : necRoadCopy == null");
                    }
                }

                Iterator newPosIter = posRoad.getNewPossibilities().iterator();

                while (newPosIter.hasNext())
                {
                    SOCPossiblePiece newPos = (SOCPossiblePiece) newPosIter.next();

                    //log.debug(">> posRoad.newPos : "+newPos);
                    //
                    // now find the copy of this new possibility and 
                    // add it to the pos road copy's new possibility list
                    //
                    switch (newPos.getType())
                    {
                    case SOCPossiblePiece.ROAD:

                        SOCPossibleRoad newPosRoadCopy = possibleRoadsCopy.get(new Integer(newPos.getCoordinates()));

                        if (newPosRoadCopy != null)
                        {
                            posRoadCopy.addNewPossibility(newPosRoadCopy);
                        }
                        else
                        {
                            staticLog.debug("*** ERROR in copyPlayerTrackers : newPosRoadCopy == null");
                        }

                        break;

                    case SOCPossiblePiece.SETTLEMENT:

                        SOCPossibleSettlement newPosSettlementCopy = possibleSettlementsCopy.get(new Integer(newPos.getCoordinates()));

                        if (newPosSettlementCopy != null)
                        {
                            posRoadCopy.addNewPossibility(newPosSettlementCopy);
                        }
                        else
                        {
                            staticLog.debug("*** ERROR in copyPlayerTrackers : newPosSettlementCopy == null");
                        }

                        break;
                    }
                }
            }

            Iterator<SOCPossibleSettlement> posSettlementsIter = possibleSettlements.values().iterator();

            while (posSettlementsIter.hasNext())
            {
                SOCPossibleSettlement posSet = posSettlementsIter.next();
                SOCPossibleSettlement posSetCopy = possibleSettlementsCopy.get(new Integer(posSet.getCoordinates()));

                //log.debug(">>> posSet     : "+posSet);
                //log.debug(">>> posSetCopy : "+posSetCopy);
                Iterator<SOCPossibleRoad> necRoadsIter = posSet.getNecessaryRoads().iterator();

                while (necRoadsIter.hasNext())
                {
                    SOCPossibleRoad necRoad = necRoadsIter.next();

                    //log.debug(">> posSet.necRoad : "+necRoad);
                    //
                    // now find the copy of this necessary road and 
                    // add it to the pos settlement copy's nec road list
                    //
                    SOCPossibleRoad necRoadCopy = possibleRoadsCopy.get(new Integer(necRoad.getCoordinates()));

                    if (necRoadCopy != null)
                    {
                        posSetCopy.addNecessaryRoad(necRoadCopy);
                    }
                    else
                    {
                        staticLog.debug("*** ERROR in copyPlayerTrackers : necRoadCopy == null");
                    }
                }

                Iterator conflictsIter = posSet.getConflicts().iterator();

                while (conflictsIter.hasNext())
                {
                    SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictsIter.next();

                    //log.debug(">> posSet.conflict : "+conflict);
                    //
                    // now find the copy of this conflict and
                    // add it to the conflict list in the pos settlement copy
                    //
                    SOCPlayerTracker trackerCopy2 = trackersCopy.get(new Integer(conflict.getPlayer().getPlayerNumber()));

                    if (trackerCopy2 == null)
                    {
                        staticLog.debug("*** ERROR in copyPlayerTrackers : trackerCopy2 == null");
                    }
                    else
                    {
                        SOCPossibleSettlement conflictCopy = trackerCopy2.getPossibleSettlements().get(new Integer(conflict.getCoordinates()));

                        if (conflictCopy == null)
                        {
                            staticLog.debug("*** ERROR in copyPlayerTrackers : conflictCopy == null");
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
    public TreeMap<Integer, SOCPossibleRoad> getPossibleRoads()
    {
        return possibleRoads;
    }

    /**
     * @return the list of possible settlements
     */
    public TreeMap<Integer, SOCPossibleSettlement> getPossibleSettlements()
    {
        return possibleSettlements;
    }

    /**
     * @return the list of possible cities
     */
    public TreeMap<Integer, SOCPossibleCity> getPossibleCities()
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
    public void addNewRoad(SOCRoad road, HashMap<Integer, SOCPlayerTracker> trackers)
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
     * JM TODO javadoc comments
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
        Iterator<SOCPossibleRoad> prIter = possibleRoads.values().iterator();

        while (prIter.hasNext())
        {
            SOCPossibleRoad pr = prIter.next();
            if (pr.getCoordinates() == road.getCoordinates())
            {
                //
                // if so, remove it
                //
                //log.debug("$$$ removing (wrong) "+Integer.toHexString(road.getCoordinates()));
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
    public void addOurNewRoad(SOCRoad road, HashMap<Integer, SOCPlayerTracker> trackers, int expandLevel)
    {
        //log.debug("$$$ addOurNewRoad : "+road);
        //
        // see if the new road was a possible road
        //
        Iterator<SOCPossibleRoad> prIter = possibleRoads.values().iterator();

        while (prIter.hasNext())
        {
            SOCPossibleRoad pr = prIter.next();

            //
            // reset all expanded flags for possible roads
            //
            pr.resetExpandedFlag();

            if (pr.getCoordinates() == road.getCoordinates())
            {
                //
                // if so, remove it
                //
                //log.debug("$$$ removing "+Integer.toHexString(road.getCoordinates()));
                possibleRoads.remove(new Integer(pr.getCoordinates()));
                removeFromNecessaryRoads(pr);

                break;
            }
        }

        //log.debug("$$$ checking for possible settlements");
        //
        // see if this road adds any new possible settlements
        //
        // check adjacent nodes to road for potential settlements
        //
        Enumeration adjNodeEnum = SOCBoard.getAdjacentNodesToEdge(road.getCoordinates()).elements();

        while (adjNodeEnum.hasMoreElements())
        {
            Integer adjNode = (Integer) adjNodeEnum.nextElement();

            if (player.isPotentialSettlement(adjNode.intValue()))
            {
                //
                // see if possible settlement is already in the list
                //
                //log.debug("$$$ seeing if "+Integer.toHexString(adjNode.intValue())+" is already in the list");
                SOCPossibleSettlement posSet = possibleSettlements.get(adjNode);

                if (posSet != null)
                {
                    //
                    // if so, clear necessary road list and remove from np lists
                    //
                    //log.debug("$$$ found it");
                    removeFromNecessaryRoads(posSet);
                    posSet.getNecessaryRoads().removeAllElements();
                    posSet.setNumberOfNecessaryRoads(0);
                }
                else
                {
                    //
                    // else, add new possible settlement
                    //
                    //log.debug("$$$ adding new possible settlement at "+Integer.toHexString(adjNode.intValue()));
                    SOCPossibleSettlement newPosSet = new SOCPossibleSettlement(player, adjNode.intValue(), new Vector<SOCPossibleRoad>());
                    newPosSet.setNumberOfNecessaryRoads(0);
                    possibleSettlements.put(adjNode, newPosSet);
                    updateSettlementConflicts(newPosSet, trackers);
                }
            }
        }

        //log.debug("$$$ checking roads adjacent to "+Integer.toHexString(road.getCoordinates()));
        //
        // see if this road adds any new possible roads 
        //
        Vector<SOCPossibleRoad> newPossibleRoads = new Vector<SOCPossibleRoad>();
        Vector<SOCPossibleRoad> roadsToExpand = new Vector<SOCPossibleRoad>();

        //
        // check adjacent edges to road
        //
        Enumeration adjEdgesEnum = SOCBoard.getAdjacentEdgesToEdge(road.getCoordinates()).elements();

        while (adjEdgesEnum.hasMoreElements())
        {
            Integer adjEdge = (Integer) adjEdgesEnum.nextElement();

            //log.debug("$$$ edge "+Integer.toHexString(adjEdge.intValue())+" is legal:"+player.isPotentialRoad(adjEdge.intValue()));
            //
            // see if edge is a potential road
            //
            if (player.isPotentialRoad(adjEdge.intValue()))
            {
                //
                // see if possible road is already in the list
                //
                SOCPossibleRoad pr = possibleRoads.get(adjEdge);

                if (pr != null)
                {
                    //
                    // if so, clear necessary road list and remove from np lists
                    //
                    //log.debug("$$$ pr "+Integer.toHexString(pr.getCoordinates())+" already in list");
                    if (!pr.getNecessaryRoads().isEmpty())
                    {
                        //log.debug("$$$    clearing nr list");
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
                    //log.debug("$$$ adding new pr at "+Integer.toHexString(adjEdge.intValue()));
                    SOCPossibleRoad newPR = new SOCPossibleRoad(player, adjEdge.intValue(), new Vector<SOCPossibleRoad>());
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
        Enumeration<SOCPossibleRoad> newPREnum = newPossibleRoads.elements();

        while (newPREnum.hasMoreElements())
        {
            SOCPossibleRoad newPR = newPREnum.nextElement();
            possibleRoads.put(new Integer(newPR.getCoordinates()), newPR);
        }

        //
        // expand possible roads that we've touched or added
        //
        SOCPlayer dummy = new SOCPlayer(player);
        Enumeration<SOCPossibleRoad> expandPREnum = roadsToExpand.elements();

        while (expandPREnum.hasMoreElements())
        {
            SOCPossibleRoad expandPR = expandPREnum.nextElement();
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
    public void expandRoad(SOCPossibleRoad targetRoad, SOCPlayer player, SOCPlayer dummy, HashMap<Integer, SOCPlayerTracker> trackers, int level)
    {
        //log.debug("$$$ expandRoad at "+Integer.toHexString(targetRoad.getCoordinates())+" level="+level);
        SOCRoad dummyRoad = new SOCRoad(dummy, targetRoad.getCoordinates());
        dummy.putPiece(dummyRoad);

        //
        // see if this road adds any new possible settlements
        //
        //log.debug("$$$ checking for possible settlements");
        //
        // check adjacent nodes to road for potential settlements
        //
        Enumeration adjNodeEnum = SOCBoard.getAdjacentNodesToEdge(targetRoad.getCoordinates()).elements();

        while (adjNodeEnum.hasMoreElements())
        {
            Integer adjNode = (Integer) adjNodeEnum.nextElement();

            if (dummy.isPotentialSettlement(adjNode.intValue()))
            {
                //
                // see if possible settlement is already in the list
                //
                //log.debug("$$$ seeing if "+Integer.toHexString(adjNode.intValue())+" is already in the list");
                SOCPossibleSettlement posSet = possibleSettlements.get(adjNode);

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
                        //log.debug("$$$ adding road "+Integer.toHexString(targetRoad.getCoordinates())+" to the settlement "+Integer.toHexString(posSet.getCoordinates()));
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
                    //log.debug("$$$ adding new possible settlement at "+Integer.toHexString(adjNode.intValue()));
                    Vector<SOCPossibleRoad> nr = new Vector<SOCPossibleRoad>();
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
            Vector<SOCPossibleRoad> newPossibleRoads = new Vector<SOCPossibleRoad>();
            Vector<SOCPossibleRoad> roadsToExpand = new Vector<SOCPossibleRoad>();

            //log.debug("$$$ checking roads adjacent to "+Integer.toHexString(targetRoad.getCoordinates()));
            //
            // check adjacent edges to road
            //
            Enumeration adjEdgesEnum = SOCBoard.getAdjacentEdgesToEdge(targetRoad.getCoordinates()).elements();

            while (adjEdgesEnum.hasMoreElements())
            {
                Integer adjEdge = (Integer) adjEdgesEnum.nextElement();

                //log.debug("$$$ edge "+Integer.toHexString(adjEdge.intValue())+" is legal:"+dummy.isPotentialRoad(adjEdge.intValue()));
                //
                // see if edge is a potential road
                //
                if (dummy.isPotentialRoad(adjEdge.intValue()))
                {
                    //
                    // see if possible road is already in the list
                    //
                    SOCPossibleRoad pr = possibleRoads.get(adjEdge);

                    if (pr != null)
                    {
                        //
                        // if so, and it needs 1 or more roads other than this one, 
                        //
                        //log.debug("$$$ pr "+Integer.toHexString(pr.getCoordinates())+" already in list");
                        Vector<SOCPossibleRoad> nr = pr.getNecessaryRoads();

                        if (!nr.isEmpty() && (!nr.contains(targetRoad)))
                        {
                            //
                            // add the target road to its nr list and the new road to the target road's np list
                            //
                            //log.debug("$$$    adding "+Integer.toHexString(targetRoad.getCoordinates())+" to nr list");
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
                        //log.debug("$$$ adding new pr at "+Integer.toHexString(adjEdge.intValue()));
                        Vector<SOCPossibleRoad> neededRoads = new Vector<SOCPossibleRoad>();
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
            Enumeration<SOCPossibleRoad> newPREnum = newPossibleRoads.elements();

            while (newPREnum.hasMoreElements())
            {
                SOCPossibleRoad newPR = newPREnum.nextElement();
                possibleRoads.put(new Integer(newPR.getCoordinates()), newPR);
            }

            //
            // if the level is not zero, expand roads that we've touched or added
            //
            Enumeration<SOCPossibleRoad> expandPREnum = roadsToExpand.elements();

            while (expandPREnum.hasMoreElements())
            {
                SOCPossibleRoad expandPR = expandPREnum.nextElement();
                expandRoad(expandPR, player, dummy, trackers, level - 1);
            }
        }

        //
        // remove the dummy road
        //
        dummy.removePiece(dummyRoad);
    }

    /**
     * add another player's new road
     *
     * @param road  the new road
     * @param isCancel  JM TODO
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
        log.debug("$$$ addTheirNewRoad : " + road);

        Integer roadCoordinates = new Integer(road.getCoordinates());
        SOCPossibleRoad pr = possibleRoads.get(roadCoordinates);

        if (pr != null)
        {
            //log.debug("$$$ removing road at "+Integer.toHexString(pr.getCoordinates()));
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
    protected void updateSettlementConflicts(SOCPossibleSettlement ps, HashMap<Integer, SOCPlayerTracker> trackers)
    {
        //log.debug("$$$ updateSettlementConflicts : "+Integer.toHexString(ps.getCoordinates()));

        /**
         * look at all adjacent nodes and update possible settlements on nodes
         */
        Iterator<SOCPlayerTracker> trackersIter = trackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();

            /**
             * first look at the node that the possible settlement is on
             */
            /**
             * if it's not our tracker...
             */
            if (tracker.getPlayer().getPlayerNumber() != ps.getPlayer().getPlayerNumber())
            {
                SOCPossibleSettlement posSet = tracker.getPossibleSettlements().get(new Integer(ps.getCoordinates()));

                if (posSet != null)
                {
                    //log.debug("$$$ add conflict "+Integer.toHexString(posSet.getCoordinates()));
                    ps.addConflict(posSet);
                    posSet.addConflict(ps);
                }
            }

            /**
             * now look at adjacent settlements
             */
            Enumeration adjNodeEnum = SOCBoard.getAdjacentNodesToNode(ps.getCoordinates()).elements();

            while (adjNodeEnum.hasMoreElements())
            {
                Integer adjNode = (Integer) adjNodeEnum.nextElement();
                SOCPossibleSettlement posSet = tracker.getPossibleSettlements().get(adjNode);

                if (posSet != null)
                {
                    //log.debug("$$$ add conflict "+Integer.toHexString(posSet.getCoordinates()));
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
    public synchronized void addNewSettlement(SOCSettlement settlement, HashMap<Integer, SOCPlayerTracker> trackers)
    {
        //log.debug("%$% settlement owner ="+settlement.getPlayer().getPlayerNumber());
        //log.debug("%$% tracker owner ="+player.getPlayerNumber());
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
     * JM TODO javadoc comments
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
        SOCPossibleSettlement ps = possibleSettlements.get(settlementCoords);
        log.debug("$$$ removing (wrong) " + Integer.toHexString(settlement.getCoordinates()));
        possibleSettlements.remove(settlementCoords);
        removeFromNecessaryRoads(ps);

    }

    /**
     * add one of our settlements
     *
     * @param settlement  the new settlement
     * @param trackers    player trackers for all of the players
     */
    public synchronized void addOurNewSettlement(SOCSettlement settlement, HashMap<Integer, SOCPlayerTracker> trackers)
    {
        //log.debug();
        log.debug("$$$ addOurNewSettlement : " + settlement);

        Integer settlementCoords = new Integer(settlement.getCoordinates());

        /**
         * add a new possible city
         */
        possibleCities.put(settlementCoords, new SOCPossibleCity(player, settlement.getCoordinates()));

        /**
         * see if the new settlement was a possible settlement in
         * the list.  if so, remove it.
         */
        SOCPossibleSettlement ps = possibleSettlements.get(settlementCoords);

        if (ps != null)
        {
            log.debug("$$$ was a possible settlement");

            /**
             * copy a list of all the conflicting settlements
             */
            Vector conflicts = (Vector) ps.getConflicts().clone();

            /**
             * remove the possible settlement that is now a real settlement
             */
            log.debug("$$$ removing " + Integer.toHexString(settlement.getCoordinates()));
            possibleSettlements.remove(settlementCoords);
            removeFromNecessaryRoads(ps);

            /**
             * remove possible settlements that this one cancels out
             */
            Enumeration conflictEnum = conflicts.elements();

            while (conflictEnum.hasMoreElements())
            {
                SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictEnum.nextElement();
                log.debug("$$$ checking conflict with " + conflict.getPlayer().getPlayerNumber() + ":" + Integer.toHexString(conflict.getCoordinates()));

                SOCPlayerTracker tracker = trackers.get(new Integer(conflict.getPlayer().getPlayerNumber()));

                if (tracker != null)
                {
                    log.debug("$$$ removing " + Integer.toHexString(conflict.getCoordinates()));
                    tracker.getPossibleSettlements().remove(new Integer(conflict.getCoordinates()));
                    removeFromNecessaryRoads(conflict);

                    /**
                     * remove the conflicts that this settlement made
                     */
                    Enumeration otherConflictEnum = conflict.getConflicts().elements();

                    while (otherConflictEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement otherConflict = (SOCPossibleSettlement) otherConflictEnum.nextElement();
                        log.debug("$$$ removing conflict " + Integer.toHexString(conflict.getCoordinates()) + " from " + Integer.toHexString(otherConflict.getCoordinates()));
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
            log.debug("$$$ wasn't possible settlement");

            Vector<SOCPossibleSettlement> trash = new Vector<SOCPossibleSettlement>();
            Vector adjNodes = SOCBoard.getAdjacentNodesToNode(settlement.getCoordinates());
            Iterator<SOCPlayerTracker> trackersIter = trackers.values().iterator();

            while (trackersIter.hasNext())
            {
                SOCPlayerTracker tracker = trackersIter.next();
                SOCPossibleSettlement posSet = tracker.getPossibleSettlements().get(settlementCoords);
                log.debug("$$$ tracker for player " + tracker.getPlayer().getPlayerNumber());

                /**
                 * check the node that the settlement is on
                 */
                log.debug("$$$ checking node " + Integer.toHexString(settlement.getCoordinates()));

                if (posSet != null)
                {
                    log.debug("$$$ trashing " + Integer.toHexString(posSet.getCoordinates()));
                    trash.addElement(posSet);

                    /**
                     * remove the conflicts that this settlement made
                     */
                    Enumeration conflictEnum = posSet.getConflicts().elements();

                    while (conflictEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictEnum.nextElement();
                        log.debug("$$$ removing conflict " + Integer.toHexString(posSet.getCoordinates()) + " from " + Integer.toHexString(conflict.getCoordinates()));
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
                    log.debug("$$$ checking node " + Integer.toHexString(adjNode.intValue()));
                    posSet = tracker.getPossibleSettlements().get(adjNode);

                    if (posSet != null)
                    {
                        log.debug("$$$ trashing " + Integer.toHexString(posSet.getCoordinates()));
                        trash.addElement(posSet);

                        /**
                         * remove the conflicts that this settlement made
                         */
                        Enumeration conflictEnum = posSet.getConflicts().elements();

                        while (conflictEnum.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictEnum.nextElement();
                            log.debug("$$$ removing conflict " + Integer.toHexString(posSet.getCoordinates()) + " from " + Integer.toHexString(conflict.getCoordinates()));
                            conflict.removeConflict(posSet);
                        }
                    }
                }

                /**
                 * take out the trash
                 */
                log.debug("$$$ removing trash for " + tracker.getPlayer().getPlayerNumber());

                Enumeration<SOCPossibleSettlement> trashEnum = trash.elements();

                while (trashEnum.hasMoreElements())
                {
                    SOCPossibleSettlement pset = trashEnum.nextElement();
                    log.debug("$$$ removing " + Integer.toHexString(pset.getCoordinates()) + " owned by " + pset.getPlayer().getPlayerNumber());
                    tracker.getPossibleSettlements().remove(new Integer(pset.getCoordinates()));
                    removeFromNecessaryRoads(pset);
                }

                trash.removeAllElements();
            }
        }
    }

    /**
     * add  another player's new settlement
     *
     * @param settlement  the new settlement
     * @param isCancel JM TODO
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

        //log.debug();
        log.debug("$$$ addTheirNewSettlement : " + settlement);

        Vector<SOCPossibleRoad> prTrash = new Vector<SOCPossibleRoad>();
        Vector<SOCPossibleRoad> nrTrash = new Vector<SOCPossibleRoad>();
        Vector adjEdges = SOCBoard.getAdjacentEdgesToNode(settlement.getCoordinates());
        Enumeration edge1Enum = adjEdges.elements();

        while (edge1Enum.hasMoreElements())
        {
            prTrash.removeAllElements();

            Integer edge1 = (Integer) edge1Enum.nextElement();
            SOCPossibleRoad pr = possibleRoads.get(edge1);

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
                                log.debug("$$$ new settlement cuts off road at " + Integer.toHexString(pr.getCoordinates()));
                                prTrash.addElement(pr);
    
                                break;
                            }
                        }
                    }
                }
                else
                {
                    nrTrash.removeAllElements();

                    Enumeration<SOCPossibleRoad> nrEnum = pr.getNecessaryRoads().elements();

                    while (nrEnum.hasMoreElements())
                    {
                        SOCPossibleRoad nr = nrEnum.nextElement();
                        Enumeration edge2Enum = adjEdges.elements();

                        while (edge2Enum.hasMoreElements())
                        {
                            Integer edge2 = (Integer) edge2Enum.nextElement();

                            if (nr.getCoordinates() == edge2.intValue())
                            {
                                log.debug("$$$ removing dependency " + Integer.toHexString(nr.getCoordinates()) + " from " + Integer.toHexString(pr.getCoordinates()));
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
                        Enumeration<SOCPossibleRoad> nrTrashEnum = nrTrash.elements();

                        while (nrTrashEnum.hasMoreElements())
                        {
                            SOCPossibleRoad nrTrashRoad = nrTrashEnum.nextElement();
                            pr.getNecessaryRoads().removeElement(nrTrashRoad);
                            nrTrashRoad.getNewPossibilities().removeElement(pr);
                        }

                        if (pr.getNecessaryRoads().isEmpty())
                        {
                            log.debug("$$$ no more dependencies, removing " + Integer.toHexString(pr.getCoordinates()));
                            prTrash.addElement(pr);
                        }
                    }
                }
            }

            ///
            /// take out the pr trash 
            ///
            Enumeration<SOCPossibleRoad> prTrashEnum = prTrash.elements();

            while (prTrashEnum.hasMoreElements())
            {
                SOCPossibleRoad prt = prTrashEnum.nextElement();
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

        //log.debug("$$$ removeDependents "+Integer.toHexString(road.getCoordinates()));
        Enumeration newPosEnum = road.getNewPossibilities().elements();

        while (newPosEnum.hasMoreElements())
        {
            SOCPossiblePiece newPos = (SOCPossiblePiece) newPosEnum.nextElement();

            //log.debug("$$$ updating "+Integer.toHexString(newPos.getCoordinates()));
            Vector<SOCPossibleRoad> nr;

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
                        //log.debug("$$$ removing this road");
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
                        Enumeration<SOCPossibleRoad> nrEnum = nr.elements();

                        while (nrEnum.hasMoreElements())
                        {
                            SOCPossibleRoad necRoad = nrEnum.nextElement();

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
                        //log.debug("$$$ removing this settlement");
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
                        Enumeration<SOCPossibleRoad> nrEnum = nr.elements();

                        while (nrEnum.hasMoreElements())
                        {
                            SOCPossibleRoad necRoad = nrEnum.nextElement();

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
        //log.debug("%%% remove road from necessary roads");
        Enumeration<SOCPossibleRoad> nrEnum = pr.getNecessaryRoads().elements();

        while (nrEnum.hasMoreElements())
        {
            SOCPossibleRoad nr = nrEnum.nextElement();

            //log.debug("%%% removing road at "+Integer.toHexString(pr.getCoordinates())+" from road at "+Integer.toHexString(nr.getCoordinates()));
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
        //log.debug("%%% remove settlement from necessary roads");
        Enumeration<SOCPossibleRoad> nrEnum = ps.getNecessaryRoads().elements();

        while (nrEnum.hasMoreElements())
        {
            SOCPossibleRoad nr = nrEnum.nextElement();

            //log.debug("%%% removing settlement at "+Integer.toHexString(ps.getCoordinates())+" from road at "+Integer.toHexString(nr.getCoordinates()));
            nr.getNewPossibilities().removeElement(ps);
        }
    }

    /**
     * JM TODO javadoc comments
     * 
     * @param city Location of our bad city
     * 
     * @see SOCRobotBrain#cancelWrongPiecePlacement(SOCCancelBuildRequest)
     */
    public void cancelWrongCity(SOCCity city)
    {
        /**
         * There is no addTheirNewCity method.
         * Just remove our potential city, since it was wrongly placed.
         * remove the possible city from the list
         */
        possibleCities.remove(new Integer(city.getCoordinates()));
    }
    
    /**
     * add one of our cities
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
        //log.debug("&&&& updateThreats");

        /**
         * check roads that need updating and don't have necessary roads
         */
        int ourPlayerNumber = player.getPlayerNumber();
        Iterator<SOCPossibleRoad> posRoadsIter = possibleRoads.values().iterator();

        while (posRoadsIter.hasNext())
        {
            SOCPossibleRoad posRoad = posRoadsIter.next();

            if ((!posRoad.isThreatUpdated()) && (posRoad.getNecessaryRoads().isEmpty()))
            {
                //log.debug("&&&& examining road at "+Integer.toHexString(posRoad.getCoordinates()));

                /**
                 * look for possible settlements that can block this road
                 */
                Vector adjNodeVec = SOCBoard.getAdjacentNodesToEdge(posRoad.getCoordinates());
                Enumeration adjEdgeEnum = SOCBoard.getAdjacentEdgesToEdge(posRoad.getCoordinates()).elements();

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
                            Enumeration adjNodeToPosRoadEnum = adjNodeVec.elements();

                            while (adjNodeToPosRoadEnum.hasMoreElements())
                            {
                                Integer adjNodeToPosRoad = (Integer) adjNodeToPosRoadEnum.nextElement();
                                Enumeration adjNodeToRealRoadEnum = realRoad.getAdjacentNodes().elements();

                                while (adjNodeToRealRoadEnum.hasMoreElements())
                                {
                                    Integer adjNodeToRealRoad = (Integer) adjNodeToRealRoadEnum.nextElement();

                                    if (adjNodeToPosRoad.intValue() == adjNodeToRealRoad.intValue())
                                    {
                                        /**
                                         * we found the common node
                                         * now see if there is a possible enemy settlement
                                         */
                                        Iterator trackersIter = trackers.values().iterator();

                                        while (trackersIter.hasNext())
                                        {
                                            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                                            if (tracker.getPlayer().getPlayerNumber() != ourPlayerNumber)
                                            {
                                                SOCPossibleSettlement posEnemySet = tracker.getPossibleSettlements().get(adjNodeToPosRoad);

                                                if (posEnemySet != null)
                                                {
                                                    /**
                                                     * we found a settlement that threatens our possible road
                                                     */

                                                    //log.debug("&&&& adding threat from settlement at "+Integer.toHexString(posEnemySet.getCoordinates()));
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
                        SOCPossibleRoad posEnemyRoad = tracker.getPossibleRoads().get(new Integer(posRoad.getCoordinates()));

                        if (posEnemyRoad != null)
                        {
                            /**
                             * we found a road that threatens our possible road
                             */

                            //log.debug("&&&& adding threat from road at "+Integer.toHexString(posEnemyRoad.getCoordinates()));
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
                Stack<SOCPossiblePiece> stack = new Stack<SOCPossiblePiece>();
                stack.push(posRoad);

                while (!stack.empty())
                {
                    SOCPossiblePiece curPosPiece = stack.pop();

                    if (curPosPiece.getType() == SOCPossiblePiece.ROAD)
                    {
                        Enumeration newPosEnum = ((SOCPossibleRoad) curPosPiece).getNewPossibilities().elements();

                        while (newPosEnum.hasMoreElements())
                        {
                            SOCPossiblePiece newPosPiece = (SOCPossiblePiece) newPosEnum.nextElement();

                            if (newPosPiece.getType() == SOCPossiblePiece.ROAD)
                            {
                                Vector<SOCPossibleRoad> necRoadVec = ((SOCPossibleRoad) newPosPiece).getNecessaryRoads();

                                if ((necRoadVec.size() == 1) && (necRoadVec.firstElement() == curPosPiece))
                                {
                                    /**
                                     * pass on all of the threats to this piece
                                     */

                                    //log.debug("&&&& adding threats to road at "+Integer.toHexString(newPosPiece.getCoordinates()));
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

                //log.debug("&&&& done updating road at "+Integer.toHexString(posRoad.getCoordinates()));
                posRoad.threatUpdated();
            }
        }

        /**
         * check roads that need updating and DO have necessary roads
         */
        posRoadsIter = possibleRoads.values().iterator();

        while (posRoadsIter.hasNext())
        {
            SOCPossibleRoad posRoad = posRoadsIter.next();

            if (!posRoad.isThreatUpdated())
            {
                //log.debug("&&&& examining road at "+Integer.toHexString(posRoad.getCoordinates()));

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
                        SOCPossibleRoad posEnemyRoad = tracker.getPossibleRoads().get(new Integer(posRoad.getCoordinates()));

                        if (posEnemyRoad != null)
                        {
                            /**
                             * we found a road that threatens our possible road
                             */

                            //log.debug("&&&& adding threat from road at "+Integer.toHexString(posEnemyRoad.getCoordinates()));
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
                Vector<SOCPossibleRoad> necRoadVec = posRoad.getNecessaryRoads();

                if (necRoadVec.size() == 1)
                {
                    SOCPossibleRoad necRoad = necRoadVec.firstElement();
                    Enumeration adjNode1Enum = SOCBoard.getAdjacentNodesToEdge(posRoad.getCoordinates()).elements();

                    while (adjNode1Enum.hasMoreElements())
                    {
                        Integer adjNode1 = (Integer) adjNode1Enum.nextElement();
                        Enumeration adjNode2Enum = SOCBoard.getAdjacentNodesToEdge(necRoad.getCoordinates()).elements();

                        while (adjNode2Enum.hasMoreElements())
                        {
                            Integer adjNode2 = (Integer) adjNode2Enum.nextElement();

                            if (adjNode1.intValue() == adjNode2.intValue())
                            {
                                /**
                                 * see if there is a possible enemy settlement at
                                 * the node between the two possible roads
                                 */
                                trackersIter = trackers.values().iterator();

                                while (trackersIter.hasNext())
                                {
                                    SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                                    if (tracker.getPlayer().getPlayerNumber() != ourPlayerNumber)
                                    {
                                        SOCPossibleSettlement posEnemySet = tracker.getPossibleSettlements().get(adjNode1);

                                        if (posEnemySet != null)
                                        {
                                            /**
                                             * we found a settlement that threatens our possible road
                                             */

                                            //log.debug("&&&& adding threat from settlement at "+Integer.toHexString(posEnemySet.getCoordinates()));
                                            posRoad.addThreat(posEnemySet);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                //log.debug("&&&& done updating road at "+Integer.toHexString(posRoad.getCoordinates()));
                posRoad.threatUpdated();
            }
        }

        /**
         * check settlements that need updating
         */
        Iterator<SOCPossibleSettlement> posSetsIter = possibleSettlements.values().iterator();

        while (posSetsIter.hasNext())
        {
            SOCPossibleSettlement posSet = posSetsIter.next();

            if (!posSet.isThreatUpdated())
            {
                //log.debug("&&&& examining settlement at "+Integer.toHexString(posSet.getCoordinates()));

                /**
                 * see if there are enemy settlements with the same coords
                 */
                Iterator trackersIter = trackers.values().iterator();

                while (trackersIter.hasNext())
                {
                    SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                    if (tracker.getPlayer().getPlayerNumber() != ourPlayerNumber)
                    {
                        SOCPossibleSettlement posEnemySet = tracker.getPossibleSettlements().get(new Integer(posSet.getCoordinates()));

                        if (posEnemySet != null)
                        {
                            //log.debug("&&&& adding threat from settlement at "+Integer.toHexString(posEnemySet.getCoordinates()));
                            posSet.addThreat(posEnemySet);
                        }
                    }
                }

                //
                // if this settlement doesn't rely on anything, then we're done
                //
                Vector<SOCPossibleRoad> necRoadVec = posSet.getNecessaryRoads();

                if (necRoadVec.isEmpty())
                {
                    ;
                }
                else if (necRoadVec.size() == 1)
                {
                    //
                    // if it relies on only one road, then it inherits the road's threats
                    //
                    //log.debug("&&&& inheriting threats from road at "+Integer.toHexString(((SOCPossibleRoad)necRoadVec.firstElement()).getCoordinates()));
                    Enumeration threatEnum = necRoadVec.firstElement().getThreats().elements();

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
                    SOCPossibleRoad nr = necRoadVec.firstElement();
                    Enumeration nrThreatEnum = nr.getThreats().elements();

                    while (nrThreatEnum.hasMoreElements())
                    {
                        SOCPossiblePiece nrThreat = (SOCPossiblePiece) nrThreatEnum.nextElement();
                        boolean allHaveIt = true;
                        Enumeration<SOCPossibleRoad> nr2Enum = necRoadVec.elements();

                        while (nr2Enum.hasMoreElements())
                        {
                            SOCPossibleRoad nr2 = nr2Enum.nextElement();

                            if ((nr2 != nr) && (!nr2.getThreats().contains(nrThreat)))
                            {
                                allHaveIt = false;

                                break;
                            }
                        }

                        if (allHaveIt)
                        {
                            //log.debug("&&&& adding threat from "+Integer.toHexString(nrThreat.getCoordinates()));
                            posSet.addThreat(nrThreat);
                        }
                    }
                }

                //log.debug("&&&& done updating settlement at "+Integer.toHexString(posSet.getCoordinates()));
                posSet.threatUpdated();
            }
        }
    }

    /**
     * calculate the longest road ETA
     */
    public void recalcLongestRoadETA()
    {
        log.debug("===  recalcLongestRoadETA for player " + player.getPlayerNumber());

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
            //log.debug("===  we have longest road");
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

        log.debug("--- roadsToGo = " + roadsToGo);
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
    private int recalcLongestRoadETAAux(int startNode, int pathLength, int lrLength, int searchDepth)
    {
        log.debug("=== recalcLongestRoadETAAux(" + Integer.toHexString(startNode) + "," + pathLength + "," + lrLength + "," + searchDepth + ")");

        //
        // we're doing a depth first search of all possible road paths 
        //
        int longest = 0;
        int numRoads = 500;
        Stack<NodeLenVis> pending = new Stack<NodeLenVis>();
        pending.push(new NodeLenVis(startNode, pathLength, new Vector<Integer>()));

        while (!pending.empty())
        {
            NodeLenVis curNode = pending.pop();
            log.debug("curNode = " + curNode);

            int coord = curNode.node;
            int len = curNode.len;
            Vector<Integer> visited = curNode.vis;
            boolean pathEnd = false;

            //
            // check for road blocks 
            //
            Enumeration pEnum = player.getGame().getBoard().getPieces().elements();

            while (pEnum.hasMoreElements())
            {
                SOCPlayingPiece p = (SOCPlayingPiece) pEnum.nextElement();

                if ((len > 0) && (p.getPlayer().getPlayerNumber() != player.getPlayerNumber()) && ((p.getType() == SOCPlayingPiece.SETTLEMENT) || (p.getType() == SOCPlayingPiece.CITY)) && (p.getCoordinates() == coord))
                {
                    pathEnd = true;

                    //log.debug("^^^ path end at "+Integer.toHexString(coord));
                    break;
                }
            }

            if (!pathEnd)
            {
                // 
                // check if we've connected to another road graph
                //
                Iterator lrPathsIter = player.getLRPaths().iterator();

                while (lrPathsIter.hasNext())
                {
                    SOCLRPathData pathData = (SOCLRPathData) lrPathsIter.next();

                    if (((startNode != pathData.getBeginning()) && (startNode != pathData.getEnd())) && ((coord == pathData.getBeginning()) || (coord == pathData.getEnd())))
                    {
                        pathEnd = true;
                        len += pathData.getLength();
                        log.debug("connecting to another path: " + pathData);
                        log.debug("len = " + len);

                        break;
                    }
                }
            }

            if (!pathEnd)
            {
                //
                // (len - pathLength) = how many new roads we've built
                //
                if ((len - pathLength) >= searchDepth)
                {
                    pathEnd = true;
                }
            }

            if (!pathEnd)
            {
                pathEnd = true;

                int j;
                Integer edge;
                boolean match;

                j = coord - 0x11;
                edge = new Integer(j);
                match = false;

                if ((j >= SOCBoard.MINEDGE) && (j <= SOCBoard.MAXEDGE) && (player.isLegalRoad(j)))
                {
                    for (Enumeration<Integer> ev = visited.elements();
                            ev.hasMoreElements();)
                    {
                        Integer vis = ev.nextElement();

                        if (vis.equals(edge))
                        {
                            match = true;

                            break;
                        }
                    }

                    if (!match)
                    {
                        Vector<Integer> newVis = (Vector<Integer>) visited.clone();
                        newVis.addElement(edge);

                        // node coord and edge coord are the same
                        pending.push(new NodeLenVis(j, len + 1, newVis));
                        pathEnd = false;
                    }
                }

                j = coord;
                edge = new Integer(j);
                match = false;

                if ((j >= SOCBoard.MINEDGE) && (j <= SOCBoard.MAXEDGE) && (player.isLegalRoad(j)))
                {
                    for (Enumeration<Integer> ev = visited.elements();
                            ev.hasMoreElements();)
                    {
                        Integer vis = ev.nextElement();

                        if (vis.equals(edge))
                        {
                            match = true;

                            break;
                        }
                    }

                    if (!match)
                    {
                        Vector<Integer> newVis = (Vector<Integer>) visited.clone();
                        newVis.addElement(edge);

                        // coord for node = edge + 0x11
                        j += 0x11;
                        pending.push(new NodeLenVis(j, len + 1, newVis));
                        pathEnd = false;
                    }
                }

                j = coord - 0x01;
                edge = new Integer(j);
                match = false;

                if ((j >= SOCBoard.MINEDGE) && (j <= SOCBoard.MAXEDGE) && (player.isLegalRoad(j)))
                {
                    for (Enumeration<Integer> ev = visited.elements();
                            ev.hasMoreElements();)
                    {
                        Integer vis = ev.nextElement();

                        if (vis.equals(edge))
                        {
                            match = true;

                            break;
                        }
                    }

                    if (!match)
                    {
                        Vector<Integer> newVis = (Vector<Integer>) visited.clone();
                        newVis.addElement(edge);

                        // node coord = edge coord + 0x10
                        j += 0x10;
                        pending.push(new NodeLenVis(j, len + 1, newVis));
                        pathEnd = false;
                    }
                }

                j = coord - 0x10;
                edge = new Integer(j);
                match = false;

                if ((j >= SOCBoard.MINEDGE) && (j <= SOCBoard.MAXEDGE) && (player.isLegalRoad(j)))
                {
                    for (Enumeration<Integer> ev = visited.elements();
                            ev.hasMoreElements();)
                    {
                        Integer vis = ev.nextElement();

                        if (vis.equals(edge))
                        {
                            match = true;

                            break;
                        }
                    }

                    if (!match)
                    {
                        Vector<Integer> newVis = (Vector<Integer>) visited.clone();
                        newVis.addElement(edge);

                        // node coord = edge coord + 0x01
                        j += 0x01;
                        pending.push(new NodeLenVis(j, len + 1, newVis));
                        pathEnd = false;
                    }
                }
            }

            if (pathEnd)
            {
                if (len > longest)
                {
                    longest = len;
                    numRoads = curNode.len - pathLength;
                }
                else if ((len == longest) && (curNode.len < numRoads))
                {
                    numRoads = curNode.len - pathLength;
                }
            }
        }

        if (longest > lrLength)
        {
            return numRoads;
        }
        else
        {
            return 500;
        }
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
        Iterator<SOCPossibleRoad> posRoadsIter = possibleRoads.values().iterator();

        while (posRoadsIter.hasNext())
        {
            SOCPossibleRoad posRoad = posRoadsIter.next();

            if (posRoad.getNecessaryRoads().isEmpty())
            {
                //
                // calc longest road value
                //
                SOCRoad dummyRoad = new SOCRoad(dummy, posRoad.getCoordinates());
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

                //log.debug("$$ updateLRValue for "+Integer.toHexString(posRoad.getCoordinates())+" is "+posRoad.getLRValue());
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
        //log.debug("$$$ updateLRPotential for road at "+Integer.toHexString(posRoad.getCoordinates())+" level="+level);
        //
        // if we've reached the bottom level of recursion, 
        // or if there are no more roads to place from this one.
        // then calc potential LR value
        //
        boolean noMoreExpansion;

        if (level <= 0)
        {
            noMoreExpansion = true;
        }
        else
        {
            noMoreExpansion = false;

            Enumeration adjEdgeEnum = SOCBoard.getAdjacentEdgesToEdge(dummyRoad.getCoordinates()).elements();

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

            //log.debug("$$$ newPotentialLRValue = "+newPotentialLRValue);
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
            Enumeration adjEdgeEnum = SOCBoard.getAdjacentEdgesToEdge(dummyRoad.getCoordinates()).elements();

            while (adjEdgeEnum.hasMoreElements())
            {
                Integer adjEdge = (Integer) adjEdgeEnum.nextElement();

                if (dummy.isPotentialRoad(adjEdge.intValue()))
                {
                    SOCRoad newDummyRoad = new SOCRoad(dummy, adjEdge.intValue());
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

        int good = 0;
        int bad = 0;

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

            SOCBoard board = player.getGame().getBoard();

            if (log.isDebugEnabled())
            {
                if (laPlayer != null)
                {
                    log.debug("laPlayer # = " + laPlayer.getPlayerNumber());
                }
                else
                {
                    log.debug("laPlayer = null");
                }

                if (lrPlayer != null)
                {
                    log.debug("lrPlayer # = " + lrPlayer.getPlayerNumber());
                }
                else
                {
                    log.debug("lrPlayer = null");
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

            TreeMap<Integer, SOCPossibleSettlement> posSetsCopy = (TreeMap<Integer, SOCPossibleSettlement>) possibleSettlements.clone();
            TreeMap<Integer, SOCPossibleCity> posCitiesCopy = (TreeMap<Integer, SOCPossibleCity>) possibleCities.clone();

            int points = player.getTotalVP();
            int fastestETA;

            Queue necRoadQueue = new Queue();

            while (points < SOCGame.VP_WINNER)  // TODO: Hardcoded 10 to win
            {
                if (log.isDebugEnabled())
                {
                    log.debug("WWW points = " + points);
                    log.debug("WWW settlementPiecesLeft = " + settlementPiecesLeft);
                    log.debug("WWW cityPiecesLeft = " + cityPiecesLeft);
                    log.debug("WWW settlementSpotsLeft = " + posSetsCopy.size());
                    log.debug("WWW citySpotsLeft = " + posCitiesCopy.size());

                    StringBuffer sb = new StringBuffer();
                    sb.append("WWW tempPortFlags: ");

                    for (int portType = SOCBoard.MISC_PORT;
                            portType <= SOCBoard.WOOD_PORT; portType++)
                    {
                        sb.append(tempPortFlags[portType] + " ");
                    }

                    log.debug(sb.toString());

                    log.debug("WWW settlementETA = " + settlementETA);
                    log.debug("WWW cityETA = " + cityETA);
                    log.debug("WWW roadETA = " + roadETA);
                    log.debug("WWW cardETA = " + cardETA);
                }

                if (points == (SOCGame.VP_WINNER - 1))
                {
                    fastestETA = 500;

                    SOCPossibleSettlement chosenSet = null;

                    if ((settlementPiecesLeft > 0) && (!posSetsCopy.isEmpty()))
                    {
                        Iterator<SOCPossibleSettlement> posSetsIter = posSetsCopy.values().iterator();

                        while (posSetsIter.hasNext())
                        {
                            SOCPossibleSettlement posSet = posSetsIter.next();
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
                            log.debug("WWW # necesesary roads = " + totalNecRoads);
                            log.debug("WWW this settlement eta = " + (settlementETA + (totalNecRoads * roadETA)));
                            log.debug("WWW settlement is " + chosenSet);
                            log.debug("WWW settlement eta = " + fastestETA);
                        }
                        else
                        {
                            fastestETA = 500;
                        }
                    }

                    if ((cityPiecesLeft > 0) && (citySpotsLeft > 0) && (cityETA <= fastestETA))
                    {
                        log.debug("WWW city eta = " + cityETA);
                        fastestETA = cityETA;
                    }

                    if (!haveLA && !needLA && (tempLargestArmyETA < fastestETA))
                    {
                        log.debug("WWW LA eta = " + tempLargestArmyETA);
                        fastestETA = tempLargestArmyETA;
                    }

                    if (!haveLR && !needLR && (tempLongestRoadETA < fastestETA))
                    {
                        log.debug("WWW LR eta = " + tempLongestRoadETA);
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

                    log.debug("WWW Adding " + fastestETA + " to win eta");
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

                        Iterator<SOCPossibleCity> posCities0Iter = posCitiesCopy.values().iterator();

                        while (posCities0Iter.hasNext())
                        {
                            SOCPossibleCity posCity0 = posCities0Iter.next();

                            //
                            // update our building speed estimate
                            //
                            tempPlayerNumbers.updateNumbers(posCity0.getCoordinates(), player.getGame().getBoard());
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

                            tempPlayerNumbers.undoUpdateNumbers(posCity0.getCoordinates(), player.getGame().getBoard());
                        }

                        if (twoCities <= fastestETA)
                        {
                            log.debug("WWW twoCities = " + twoCities);
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

                        Vector<SOCPossibleSettlement> posSetsToPutBack = new Vector<SOCPossibleSettlement>();

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
                                Iterator<SOCPossibleSettlement> posSetsIter = posSetsCopy.values().iterator();

                                while (posSetsIter.hasNext())
                                {
                                    SOCPossibleSettlement posSet = posSetsIter.next();
                                    int posSetETA = settlementETA + (posSet.getNumberOfNecessaryRoads() * roadETA);

                                    if (posSetETA < fastestSetETA)
                                    {
                                        fastestSetETA = posSetETA;
                                        tempPlayerNumbers.updateNumbers(posSet.getCoordinates(), player.getGame().getBoard());

                                        Integer posSetCoords = new Integer(posSet.getCoordinates());

                                        for (int portType = SOCBoard.MISC_PORT;
                                                portType <= SOCBoard.WOOD_PORT;
                                                portType++)
                                        {
                                            tempPortFlagsSet[i][portType] = tempPortFlags[portType];

                                            if (player.getGame().getBoard().getPortCoordinates(portType).contains(posSetCoords))
                                            {
                                                tempPortFlagsSet[i][portType] = true;
                                            }
                                        }

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

                                        tempPlayerNumbers.undoUpdateNumbers(posSet.getCoordinates(), player.getGame().getBoard());
                                        chosenSet[i] = posSet;
                                    }
                                    else if (posSetETA == fastestSetETA)
                                    {
                                        boolean[] veryTempPortFlags = new boolean[SOCBoard.WOOD_PORT + 1];
                                        tempPlayerNumbers.updateNumbers(posSet.getCoordinates(), player.getGame().getBoard());

                                        Integer posSetCoords = new Integer(posSet.getCoordinates());

                                        for (int portType = SOCBoard.MISC_PORT;
                                                portType <= SOCBoard.WOOD_PORT;
                                                portType++)
                                        {
                                            veryTempPortFlags[portType] = tempPortFlags[portType];

                                            if (player.getGame().getBoard().getPortCoordinates(portType).contains(posSetCoords))
                                            {
                                                veryTempPortFlags[portType] = true;
                                            }
                                        }

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
                                        tempPlayerNumbers.undoUpdateNumbers(posSet.getCoordinates(), player.getGame().getBoard());

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

                                            for (int portType = SOCBoard.MISC_PORT;
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

                                log.debug("WWW # necesesary roads = " + totalNecRoads);
                                log.debug("WWW this settlement eta = " + (settlementETA + (totalNecRoads * roadETA)));

                                if ((i == 0) && (chosenSet[0] != null))
                                {
                                    posSetsCopy.remove(new Integer(chosenSet[0].getCoordinates()));

                                    Enumeration conflicts = chosenSet[0].getConflicts().elements();

                                    while (conflicts.hasMoreElements())
                                    {
                                        SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflicts.nextElement();
                                        Integer conflictInt = new Integer(conflict.getCoordinates());
                                        SOCPossibleSettlement possibleConflict = posSetsCopy.get(conflictInt);

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

                        Iterator<SOCPossibleSettlement> posSetsToPutBackIter = posSetsToPutBack.iterator();

                        while (posSetsToPutBackIter.hasNext())
                        {
                            SOCPossibleSettlement tmpPosSet = posSetsToPutBackIter.next();
                            posSetsCopy.put(new Integer(tmpPosSet.getCoordinates()), tmpPosSet);
                        }

                        if (canBuild2Settlements && (twoSettlements <= fastestETA))
                        {
                            log.debug("WWW 2 * settlement = " + twoSettlements);
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
                            Iterator<SOCPossibleCity> posCities0Iter = posCitiesCopy.values().iterator();

                            while (posCities0Iter.hasNext())
                            {
                                SOCPossibleCity posCity0 = posCities0Iter.next();
                                tempPlayerNumbers.updateNumbers(posCity0.getCoordinates(), player.getGame().getBoard());
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
                                tempPlayerNumbers.undoUpdateNumbers(posCity0.getCoordinates(), player.getGame().getBoard());

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
                            Iterator<SOCPossibleSettlement> posSetsIter = posSetsCopy.values().iterator();

                            while (posSetsIter.hasNext())
                            {
                                SOCPossibleSettlement posSet = posSetsIter.next();
                                int posSetETA = settlementETA + (posSet.getNumberOfNecessaryRoads() * roadETA);

                                if (posSetETA < fastestSetETA)
                                {
                                    fastestSetETA = posSetETA;
                                    tempPlayerNumbers.updateNumbers(posSet.getCoordinates(), player.getGame().getBoard());

                                    Integer posSetCoords = new Integer(posSet.getCoordinates());

                                    for (int portType = SOCBoard.MISC_PORT;
                                            portType <= SOCBoard.WOOD_PORT;
                                            portType++)
                                    {
                                        tempPortFlagsSet[0][portType] = tempPortFlags[portType];

                                        if (player.getGame().getBoard().getPortCoordinates(portType).contains(posSetCoords))
                                        {
                                            tempPortFlagsSet[0][portType] = true;
                                        }
                                    }

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

                                    tempPlayerNumbers.undoUpdateNumbers(posSet.getCoordinates(), player.getGame().getBoard());
                                    chosenSet[0] = posSet;
                                }
                                else if (posSetETA == fastestSetETA)
                                {
                                    boolean[] veryTempPortFlags = new boolean[SOCBoard.WOOD_PORT + 1];
                                    tempPlayerNumbers.updateNumbers(posSet.getCoordinates(), player.getGame().getBoard());

                                    Integer posSetCoords = new Integer(posSet.getCoordinates());

                                    for (int portType = SOCBoard.MISC_PORT;
                                            portType <= SOCBoard.WOOD_PORT;
                                            portType++)
                                    {
                                        veryTempPortFlags[portType] = tempPortFlags[portType];

                                        if (player.getGame().getBoard().getPortCoordinates(portType).contains(posSetCoords))
                                        {
                                            veryTempPortFlags[portType] = true;
                                        }
                                    }

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
                                    tempPlayerNumbers.undoUpdateNumbers(posSet.getCoordinates(), player.getGame().getBoard());

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

                                        for (int portType = SOCBoard.MISC_PORT;
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

                        log.debug("WWW # necesesary roads = " + totalNecRoads);
                        log.debug("WWW this settlement eta = " + (settlementETA + (totalNecRoads * roadETA)));

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
                            log.debug("WWW one of each = " + oneOfEach);
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
                            log.debug("WWW ERROR CALCULATING LA ETA");
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

                        log.debug("WWW LA eta = " + tempLargestArmyETA);

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
                        log.debug("WWW LR eta = " + tempLongestRoadETA);

                        if (tempLongestRoadETA < fastestETA)
                        {
                            fastestETA = tempLongestRoadETA;
                        }
                    }

                    ///
                    /// implement the fastest scenario
                    ///
                    log.debug("WWW Adding " + fastestETA + " to win eta");
                    points += 2;
                    winGameETA += fastestETA;
                    log.debug("WWW WGETA SO FAR FOR PLAYER " + player.getPlayerNumber() + " = " + winGameETA);

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
                        tempPlayerNumbers.updateNumbers(chosenSet[0].getCoordinates(), player.getGame().getBoard());
                        tempPlayerNumbers.updateNumbers(chosenSet[1].getCoordinates(), player.getGame().getBoard());

                        Integer chosenSet0Coords = new Integer(chosenSet[0].getCoordinates());
                        Integer chosenSet1Coords = new Integer(chosenSet[1].getCoordinates());

                        for (int portType = SOCBoard.MISC_PORT;
                                portType <= SOCBoard.WOOD_PORT; portType++)
                        {
                            if (player.getGame().getBoard().getPortCoordinates(portType).contains(chosenSet0Coords))
                            {
                                tempPortFlags[portType] = true;
                            }

                            if (player.getGame().getBoard().getPortCoordinates(portType).contains(chosenSet1Coords))
                            {
                                tempPortFlags[portType] = true;
                            }
                        }

                        ourBSE.recalculateEstimates(tempPlayerNumbers);
                        ourBuildingSpeed = ourBSE.getEstimatesFromNothingFast(tempPortFlags);
                        settlementETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.SETTLEMENT];
                        roadETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.ROAD];
                        cityETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CITY];
                        cardETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CARD];
                        log.debug("WWW  * build two settlements");
                        log.debug("WWW    settlement 1: " + board.nodeCoordToString(chosenSet[0].getCoordinates()));
                        log.debug("WWW    settlement 2: " + board.nodeCoordToString(chosenSet[1].getCoordinates()));

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
                        tempPlayerNumbers.updateNumbers(chosenSet[0].getCoordinates(), player.getGame().getBoard());

                        Integer chosenSet0Coords = new Integer(chosenSet[0].getCoordinates());

                        for (int portType = SOCBoard.MISC_PORT;
                                portType <= SOCBoard.WOOD_PORT; portType++)
                        {
                            if (player.getGame().getBoard().getPortCoordinates(portType).contains(chosenSet0Coords))
                            {
                                tempPortFlags[portType] = true;
                            }
                        }

                        tempPlayerNumbers.updateNumbers(chosenCity[0].getCoordinates(), player.getGame().getBoard());
                        ourBSE.recalculateEstimates(tempPlayerNumbers);
                        ourBuildingSpeed = ourBSE.getEstimatesFromNothingFast(tempPortFlags);
                        settlementETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.SETTLEMENT];
                        roadETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.ROAD];
                        cityETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CITY];
                        cardETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CARD];
                        log.debug("WWW  * build a settlement and a city");
                        log.debug("WWW    settlement at " + board.nodeCoordToString(chosenSet[0].getCoordinates()));
                        log.debug("WWW    city at " + board.nodeCoordToString(chosenCity[0].getCoordinates()));

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
                        tempPlayerNumbers.updateNumbers(chosenCity[0].getCoordinates(), player.getGame().getBoard());

                        //
                        // pick the second city to build
                        //
                        int bestCitySpeedupTotal = 0;
                        Iterator<SOCPossibleCity> posCities1Iter = posCitiesCopy.values().iterator();

                        while (posCities1Iter.hasNext())
                        {
                            SOCPossibleCity posCity1 = posCities1Iter.next();
                            tempPlayerNumbers.updateNumbers(posCity1.getCoordinates(), player.getGame().getBoard());
                            log.debug("tempPlayerNumbers = " + tempPlayerNumbers);
                            tempBSE.recalculateEstimates(tempPlayerNumbers);

                            int[] tempBuildingSpeed = tempBSE.getEstimatesFromNothingFast(tempPortFlags);
                            int tempSpeedupTotal = 0;

                            //boolean ok = true;
                            for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                    buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                    buildingType++)
                            {
                                log.debug("ourBuildingSpeed[" + buildingType + "] = " + ourBuildingSpeed[buildingType]);
                                log.debug("tempBuildingSpeed[" + buildingType + "] = " + tempBuildingSpeed[buildingType]);

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
                            tempPlayerNumbers.undoUpdateNumbers(posCity1.getCoordinates(), player.getGame().getBoard());
                            log.debug("tempPlayerNumbers = " + tempPlayerNumbers);
                            log.debug("WWW City at " + board.nodeCoordToString(posCity1.getCoordinates()) + " has tempSpeedupTotal = " + tempSpeedupTotal);

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

                        tempPlayerNumbers.updateNumbers(chosenCity[1].getCoordinates(), player.getGame().getBoard());
                        ourBSE.recalculateEstimates(tempPlayerNumbers);
                        ourBuildingSpeed = ourBSE.getEstimatesFromNothingFast(tempPortFlags);
                        settlementETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.SETTLEMENT];
                        roadETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.ROAD];
                        cityETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CITY];
                        cardETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CARD];
                        log.debug("WWW  * build 2 cities");
                        log.debug("WWW    city 1: " + board.nodeCoordToString(chosenCity[0].getCoordinates()));
                        log.debug("WWW    city 2: " + board.nodeCoordToString(chosenCity[1].getCoordinates()));

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": City at " + board.nodeCoordToString(chosenCity[0].getCoordinates()) + "; City at " + board.nodeCoordToString(chosenCity[1].getCoordinates()));
                        }
                    }
                    else if (!haveLR && !needLR && (points > 5) && (fastestETA == tempLongestRoadETA))
                    {
                        needLR = true;
                        log.debug("WWW  * take longest road");

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": Longest Road");
                        }
                    }
                    else if (!haveLA && !needLA && (points > 5) && (fastestETA == tempLargestArmyETA))
                    {
                        needLA = true;
                        log.debug("WWW  * take largest army");

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": Largest Army");
                        }
                    }
                }
            }

            log.debug("WWW TOTAL WGETA FOR PLAYER " + player.getPlayerNumber() + " = " + winGameETA);

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
    public static HashMap<Integer, SOCPlayerTracker> tryPutPiece(SOCPlayingPiece piece, SOCGame game, HashMap trackers)
    {
        HashMap<Integer, SOCPlayerTracker> trackersCopy = SOCPlayerTracker.copyPlayerTrackers(trackers);

        if (piece != null)
        {
            game.putTempPiece(piece);

            Iterator<SOCPlayerTracker> trackersCopyIter = trackersCopy.values().iterator();

            while (trackersCopyIter.hasNext())
            {
                SOCPlayerTracker trackerCopy = trackersCopyIter.next();

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
    public static void tryPutPieceNoCopy(SOCPlayingPiece piece, SOCGame game, HashMap<Integer, SOCPlayerTracker> trackers)
    {
        if (piece != null)
        {
            game.putTempPiece(piece);

            Iterator<SOCPlayerTracker> trackersIter = trackers.values().iterator();

            while (trackersIter.hasNext())
            {
                SOCPlayerTracker tracker = trackersIter.next();

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
        if (staticLog.isDebugEnabled())
        {
            Iterator trackersIter = playerTrackers.values().iterator();

            while (trackersIter.hasNext())
            {
                SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
                staticLog.debug("%%%%%%%%% TRACKER FOR PLAYER " + tracker.getPlayer().getPlayerNumber());
                staticLog.debug("   LONGEST ROAD ETA = " + tracker.getLongestRoadETA());
                staticLog.debug("   LARGEST ARMY ETA = " + tracker.getLargestArmyETA());

                Iterator<SOCPossibleRoad> prIter = tracker.getPossibleRoads().values().iterator();

                while (prIter.hasNext())
                {
                    SOCPossibleRoad pr = prIter.next();
                    staticLog.debug("%%% possible road at " + Integer.toHexString(pr.getCoordinates()));
                    
                    StringBuffer sb = new StringBuffer();
                    sb.append("   eta:" + pr.getETA());
                    sb.append("   this road needs:");

                    Enumeration<SOCPossibleRoad> nrEnum = pr.getNecessaryRoads().elements();

                    while (nrEnum.hasMoreElements())
                    {
                        sb.append(" " + Integer.toHexString(nrEnum.nextElement().getCoordinates()));
                    }

                    staticLog.debug(sb.toString());
                    sb = new StringBuffer();
                    sb.append("   this road supports:");

                    Enumeration newPosEnum = pr.getNewPossibilities().elements();

                    while (newPosEnum.hasMoreElements())
                    {
                        sb.append(" " + Integer.toHexString(((SOCPossiblePiece) newPosEnum.nextElement()).getCoordinates()));
                    }

                    staticLog.debug(sb.toString());
                    sb = new StringBuffer();
                    sb.append("   threats:");

                    Enumeration threatEnum = pr.getThreats().elements();

                    while (threatEnum.hasMoreElements())
                    {
                        SOCPossiblePiece threat = (SOCPossiblePiece) threatEnum.nextElement();
                        sb.append(" " + threat.getPlayer().getPlayerNumber() + ":" + threat.getType() + ":" + Integer.toHexString(threat.getCoordinates()));
                    }

                    staticLog.debug(sb.toString());
                    staticLog.debug("   LR value=" + pr.getLRValue() + " LR Potential=" + pr.getLRPotential());
                }

                Iterator<SOCPossibleSettlement> psIter = tracker.getPossibleSettlements().values().iterator();

                while (psIter.hasNext())
                {
                    SOCPossibleSettlement ps = psIter.next();
                    staticLog.debug("%%% possible settlement at " + Integer.toHexString(ps.getCoordinates()));
                    
                    StringBuffer sb = new StringBuffer();
                    sb.append("   eta:" + ps.getETA());
                    sb.append("%%%   conflicts");

                    Enumeration conflictEnum = ps.getConflicts().elements();

                    while (conflictEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictEnum.nextElement();
                        sb.append(" " + conflict.getPlayer().getPlayerNumber() + ":" + Integer.toHexString(conflict.getCoordinates()));
                    }

                    staticLog.debug(sb.toString());
                    sb = new StringBuffer();
                    sb.append("%%%   necessary roads");

                    Enumeration<SOCPossibleRoad> nrEnum = ps.getNecessaryRoads().elements();

                    while (nrEnum.hasMoreElements())
                    {
                        SOCPossibleRoad nr = nrEnum.nextElement();
                        sb.append(" " + Integer.toHexString(nr.getCoordinates()));
                    }

                    staticLog.debug(sb.toString());
                    sb = new StringBuffer();
                    sb.append("   threats:");

                    Enumeration threatEnum = ps.getThreats().elements();

                    while (threatEnum.hasMoreElements())
                    {
                        SOCPossiblePiece threat = (SOCPossiblePiece) threatEnum.nextElement();
                        sb.append(" " + threat.getPlayer().getPlayerNumber() + ":" + threat.getType() + ":" + Integer.toHexString(threat.getCoordinates()));
                    }

                    staticLog.debug(sb.toString());
                }

                Iterator<SOCPossibleCity> pcIter = tracker.getPossibleCities().values().iterator();

                while (pcIter.hasNext())
                {
                    SOCPossibleCity pc = pcIter.next();
                    staticLog.debug("%%% possible city at " + Integer.toHexString(pc.getCoordinates()));
                    staticLog.debug("   eta:" + pc.getETA());
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

            //log.debug("%%%%%%%%% TRACKER FOR PLAYER "+tracker.getPlayer().getPlayerNumber());
            try
            {
                tracker.recalcLongestRoadETA();
                tracker.recalcLargestArmyETA();
                tracker.recalcWinGameETA();

                //log.debug("needs LA = "+tracker.needsLA());
                //log.debug("largestArmyETA = "+tracker.getLargestArmyETA());
                //log.debug("needs LR = "+tracker.needsLR());
                //log.debug("longestRoadETA = "+tracker.getLongestRoadETA());
                //log.debug("winGameETA = "+tracker.getWinGameETA());
            }
            catch (NullPointerException e)
            {
                System.out.println("Null Pointer Exception calculating winGameETA");
                e.printStackTrace();
            }
        }
    }
}
