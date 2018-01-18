/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCInventory;
import soc.game.SOCLRPathData;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCShip;

import soc.util.Pair;
import soc.util.Queue;

import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.TreeMap;
import java.util.Vector;


/**
 * This class is used by the SOCRobotBrain to track
 * strategic planning information such as
 * possible building spots for itself and other players.
 * Also used for prediction of other players' possible upcoming moves.
 *<P>
 * Some users of this class are: {@link SOCRobotDM#planStuff(int)},
 * and many callers of {@link #getWinGameETA()}
 *<P>
 *
 * (Dissertation excerpt)
 *<blockquote>
 * "When a player places a road, that player's PlayerTracker will look ahead by
 *  pretending to place new roads attached to that road and then recording new
 *  potential settlements [and their roads]...
 *<p>
 *  The PlayerTracker only needs to be updated when players put pieces on the
 *  board... not only when that player builds a road but when any player builds
 *  a road or settlement. This is because another player's road or settlement
 *  may cut off a path to a future settlement. This update can be done by
 *  keeping track of which pieces support the building of others."
 *</blockquote>
 *<p>
 *  To output a legible overview of the data in a SOCPlayerTracker, use {@link #playerTrackersDebug(HashMap)}.
 *
 * @author Robert S Thomas
 */
public class SOCPlayerTracker
{
    // protected static final DecimalFormat df1 = new DecimalFormat("###0.00");

    /**
     * Road expansion level for {@link #addOurNewRoadOrShip(SOCRoad, HashMap, int)};
     * how far away to look for possible future settlements
     * (level of recursion).
     */
    static protected int EXPAND_LEVEL = 1;

    /**
     * Ship route length expansion level to add to {@link #EXPAND_LEVEL} for
     * {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, HashMap, int)}.
     * @since 2.0.00
     */
    static protected int EXPAND_LEVEL_SHIP_EXTRA = 2;

    /**
     * Road expansion level for {@link #updateLRPotential(SOCPossibleRoad, SOCPlayer, SOCRoad, int, int)};
     * how far away to look for possible future roads
     * (level of recursion).
     */
    static protected int LR_CALC_LEVEL = 2;

    /** The robot brain using this tracker */
    protected final SOCRobotBrain brain;

    /**
     * The game where {@link #player} is being tracked
     * @since 2.0.00
     */
    private final SOCGame game;

    /**
     * The player being tracked
     * @see #game
     */
    private final SOCPlayer player;

    /** Seat number of the player being tracked; {@link #player}{@link SOCPlayer#getPlayerNumber() .getPlayerNumber()} */
    private final int playerNumber;

    /**
     * Possible near-future settlements for this player.
     * Key = {@link Integer} node coordinate, value = {@link SOCPossibleSettlement}.
     * Expanded in {@link #addOurNewRoadOrShip(SOCRoad, HashMap, int)}
     * via {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, HashMap, int)}.
     * Also updated in {@link #addNewSettlement(SOCSettlement, HashMap)},
     * {@link #cancelWrongSettlement(SOCSettlement)}, a few other places.
     */
    protected TreeMap<Integer, SOCPossibleSettlement> possibleSettlements;

    /**
     * Includes both roads and ships.
     * Key = {@link Integer} edge coordinate, value = {@link SOCPossibleRoad} or {@link SOCPossibleShip}
     * Expanded in {@link #addOurNewRoadOrShip(SOCRoad, HashMap, int)}
     * via {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, HashMap, int)}.
     */
    protected TreeMap<Integer, SOCPossibleRoad> possibleRoads;

    /** Key = {@link Integer} node coordinate, value = {@link SOCPossibleCity} */
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
     * @since 1.1.00
     */
    protected SOCSettlement pendingInitSettlement;

    /**
     * For scenario {@code _SC_PIRI}, the player's ship closest to the Fortress (the ship farthest west).
     * {@code null} otherwise.  Updated by {@link #updateScenario_SC_PIRI_closestShipToFortress(SOCShip, boolean)}.
     * @since 2.0.00
     */
    private SOCShip scen_SC_PIRI_closestShipToFortress;

    /**
     * monitor for synchronization
     */
    boolean inUse;

    /**
     * Constructor.
     *
     * @param pl  the player being tracked
     * @param br  the robot brain using this tracker
     */
    public SOCPlayerTracker(SOCPlayer pl, SOCRobotBrain br)
    {
        inUse = false;
        brain = br;
        player = pl;
        playerNumber = pl.getPlayerNumber();
        game = pl.getGame();
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
     * Copy constructor.
     *<P>
     * Note: Does NOT copy connections between possible pieces
     *
     * @param pt  the player tracker
     */
    public SOCPlayerTracker(SOCPlayerTracker pt)
    {
        inUse = false;
        brain = pt.getBrain();
        player = pt.getPlayer();
        playerNumber = player.getPlayerNumber();
        game = pt.game;
        possibleRoads = new TreeMap<Integer, SOCPossibleRoad>();
        possibleSettlements = new TreeMap<Integer, SOCPossibleSettlement>();
        possibleCities = new TreeMap<Integer, SOCPossibleCity>();
        longestRoadETA = pt.getLongestRoadETA();
        roadsToGo = pt.getRoadsToGo();
        largestArmyETA = pt.getLargestArmyETA();
        knightsToBuy = pt.getKnightsToBuy();
        pendingInitSettlement = pt.getPendingInitSettlement();
        scen_SC_PIRI_closestShipToFortress = pt.scen_SC_PIRI_closestShipToFortress;

        //D.ebugPrintln(">>>>> Copying SOCPlayerTracker for player number "+player.getPlayerNumber());
        //
        // now perform the copy
        //
        // start by just getting all of the possible pieces
        //
        for (SOCPossibleRoad posRoad : pt.getPossibleRoads().values())
        {
            SOCPossibleRoad posRoadCopy;
            if (posRoad instanceof SOCPossibleShip)
                posRoadCopy = new SOCPossibleShip((SOCPossibleShip) posRoad);
            else
                posRoadCopy = new SOCPossibleRoad(posRoad);
            possibleRoads.put(new Integer(posRoadCopy.getCoordinates()), posRoadCopy);
        }

        for (SOCPossibleSettlement posSettlement : pt.getPossibleSettlements().values())
        {
            SOCPossibleSettlement posSettlementCopy = new SOCPossibleSettlement(posSettlement);
            possibleSettlements.put(new Integer(posSettlementCopy.getCoordinates()), posSettlementCopy);
        }

        for (SOCPossibleCity posCity : pt.getPossibleCities().values())
        {
            SOCPossibleCity posCityCopy = new SOCPossibleCity(posCity);
            possibleCities.put(new Integer(posCityCopy.getCoordinates()), posCityCopy);
        }
    }

    /**
     * make copies of player trackers and then
     * make connections between copied pieces
     *<P>
     * Note: not copying threats
     *
     * param trackers  player trackers for each player
     */
    public static HashMap<Integer, SOCPlayerTracker> copyPlayerTrackers(HashMap<Integer, SOCPlayerTracker> trackers)
    {
        HashMap<Integer, SOCPlayerTracker> trackersCopy = new HashMap<Integer, SOCPlayerTracker>(trackers.size());  // == SOCGame.MAXPLAYERS

        //
        // copy the trackers but not the connections between the pieces
        //
        Iterator<SOCPlayerTracker> trackersIter = trackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker pt = trackersIter.next();
            trackersCopy.put(new Integer(pt.getPlayer().getPlayerNumber()), new SOCPlayerTracker(pt));
        }

        //
        // now make the connections between the pieces
        //
        //D.ebugPrintln(">>>>> Making connections between pieces");
        trackersIter = trackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();
            SOCPlayerTracker trackerCopy = trackersCopy.get(new Integer(tracker.getPlayer().getPlayerNumber()));

            //D.ebugPrintln(">>>> Player num for tracker is "+tracker.getPlayer().getPlayerNumber());
            //D.ebugPrintln(">>>> Player num for trackerCopy is "+trackerCopy.getPlayer().getPlayerNumber());
            TreeMap<Integer, SOCPossibleRoad> possibleRoads = tracker.getPossibleRoads();
            TreeMap<Integer, SOCPossibleRoad> possibleRoadsCopy = trackerCopy.getPossibleRoads();
            TreeMap<Integer, SOCPossibleSettlement> possibleSettlements = tracker.getPossibleSettlements();
            TreeMap<Integer, SOCPossibleSettlement> possibleSettlementsCopy = trackerCopy.getPossibleSettlements();

            for (SOCPossibleRoad posRoad : possibleRoads.values())
            {
                SOCPossibleRoad posRoadCopy = possibleRoadsCopy.get(new Integer(posRoad.getCoordinates()));

                //D.ebugPrintln(">>> posRoad     : "+posRoad);
                //D.ebugPrintln(">>> posRoadCopy : "+posRoadCopy);

                for (SOCPossibleRoad necRoad : posRoad.getNecessaryRoads())
                {
                    //D.ebugPrintln(">> posRoad.necRoad : "+necRoad);
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
                        D.ebugPrintln("*** ERROR in copyPlayerTrackers : necRoadCopy == null");
                    }
                }

                for (SOCPossiblePiece newPos : posRoad.getNewPossibilities())
                {
                    //D.ebugPrintln(">> posRoad.newPos : "+newPos);
                    //
                    // now find the copy of this new possibility and
                    // add it to the pos road copy's new possibility list
                    //
                    switch (newPos.getType())
                    {
                    case SOCPossiblePiece.SHIP:  // fall through to ROAD
                    case SOCPossiblePiece.ROAD:

                        SOCPossibleRoad newPosRoadCopy = possibleRoadsCopy.get(new Integer(newPos.getCoordinates()));

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

                        SOCPossibleSettlement newPosSettlementCopy = possibleSettlementsCopy.get(new Integer(newPos.getCoordinates()));

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


            for (SOCPossibleSettlement posSet : possibleSettlements.values())
            {
                SOCPossibleSettlement posSetCopy = possibleSettlementsCopy.get(new Integer(posSet.getCoordinates()));

                //D.ebugPrintln(">>> posSet     : "+posSet);
                //D.ebugPrintln(">>> posSetCopy : "+posSetCopy);

                for (SOCPossibleRoad necRoad : posSet.getNecessaryRoads())
                {
                    //D.ebugPrintln(">> posSet.necRoad : "+necRoad);
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
                        D.ebugPrintln("*** ERROR in copyPlayerTrackers : necRoadCopy == null");
                    }
                }


                for (SOCPossibleSettlement conflict : posSet.getConflicts())
                {
                    //D.ebugPrintln(">> posSet.conflict : "+conflict);
                    //
                    // now find the copy of this conflict and
                    // add it to the conflict list in the pos settlement copy
                    //
                    SOCPlayerTracker trackerCopy2 = trackersCopy.get(new Integer(conflict.getPlayer().getPlayerNumber()));

                    if (trackerCopy2 == null)
                    {
                        D.ebugPrintln("*** ERROR in copyPlayerTrackers : trackerCopy2 == null");
                    }
                    else
                    {
                        SOCPossibleSettlement conflictCopy = trackerCopy2.getPossibleSettlements().get(new Integer(conflict.getCoordinates()));

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
     * Get the possible roads and ships ({@link SOCPossibleRoad}, {@link SOCPossibleShip}).
     * Treat the structure of the returned map as read-only, don't add or remove anything.
     * @return the Map of coordinates to possible roads and ships
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
     * Get the ETA to take Longest Road.
     * Updated in {@link #updateWinGameETAs(HashMap)} or {@link #recalcLongestRoadETA()}.
     * @return the longest road eta
     * @see #needsLR()
     * @see #getRoadsToGo()
     */
    public int getLongestRoadETA()
    {
        return longestRoadETA;
    }

    /**
     * Get how many roads must be built to take Longest Road.
     * Updated in {@link #updateWinGameETAs(HashMap)} and {@link #recalcLongestRoadETA()}.
     * @return how many roads needed to build to take longest road
     * @see #needsLR()
     * @see #getLongestRoadETA()
     */
    public int getRoadsToGo()
    {
        return roadsToGo;
    }

    /**
     * Get the ETA to take Largest Army.
     * Updated in {@link #updateWinGameETAs(HashMap)} and {@link #recalcLargestArmyETA()}.
     * @return largest army eta
     * @see #needsLA()
     * @see #getKnightsToBuy()
     */
    public int getLargestArmyETA()
    {
        return largestArmyETA;
    }

    /**
     * Get the number of knights needed to take Largest Army.
     * Updated in {@link #updateWinGameETAs(HashMap)} and {@link #recalcLargestArmyETA()}.
     * @return the number of knights to buy to get LA
     * @see #needsLA()
     * @see #getLargestArmyETA()
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
     *<P>
     * You must call addNewSettlement and then addNewRoadOrShip:
     * This is just a place to store the settlement data.
     *
     * @param s Settlement, or null
     */
    public void setPendingInitSettlement(SOCSettlement s)
    {
        pendingInitSettlement = s;
    }

    /**
     * add a road or ship that has just been built
     *
     * @param road       the road or ship
     * @param trackers   player trackers for the players
     */
    public void addNewRoadOrShip(SOCRoad road, HashMap<Integer, SOCPlayerTracker> trackers)
    {
        if (road.getPlayerNumber() == playerNumber)
        {
            addOurNewRoadOrShip(road, trackers, EXPAND_LEVEL);
        }
        else
        {
            addTheirNewRoadOrShip(road, false);
        }
    }

    /**
     * Remove our incorrect road or ship placement, it's been rejected by the server.
     *
     * @param road Location of our bad road or ship
     *
     * @see SOCRobotBrain#cancelWrongPiecePlacement(SOCCancelBuildRequest)
     * @since 1.1.00
     */
    public void cancelWrongRoadOrShip(SOCRoad road)
    {
        addTheirNewRoadOrShip(road, true);

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
                //D.ebugPrintln("$$$ removing (wrong) "+Integer.toHexString(road.getCoordinates()));
                possibleRoads.remove(new Integer(pr.getCoordinates()));
                removeFromNecessaryRoads(pr);

                break;
            }
        }
    }

    /**
     * Add one of our roads or ships that has just been built.
     * Look for new adjacent possible settlements.
     * Calls {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, HashMap, int)}
     * on newly possible adjacent roads or ships.
     *
     * @param road         the road or ship
     * @param trackers     player trackers for the players
     * @param expandLevel  how far out we should expand roads/ships;
     *                     passed to {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, HashMap, int)}
     */
    private void addOurNewRoadOrShip(SOCRoad road, HashMap<Integer, SOCPlayerTracker> trackers, int expandLevel)
    {
        //D.ebugPrintln("$$$ addOurNewRoad : "+road);
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
                //D.ebugPrintln("$$$ removing "+Integer.toHexString(road.getCoordinates()));
                possibleRoads.remove(new Integer(pr.getCoordinates()));
                removeFromNecessaryRoads(pr);

                break;
            }
        }

        //D.ebugPrintln("$$$ checking for possible settlements");
        //
        // see if this road/ship adds any new possible settlements
        //
        // check adjacent nodes to road for potential settlements
        //
        final SOCBoard board = game.getBoard();
        Collection<Integer> adjNodeEnum = board.getAdjacentNodesToEdge(road.getCoordinates());

        for (Integer adjNode : adjNodeEnum)
        {
            if (player.canPlaceSettlement(adjNode.intValue()))
            {
                //
                // see if possible settlement is already in the list
                //
                //D.ebugPrintln("$$$ seeing if "+Integer.toHexString(adjNode.intValue())+" is already in the list");
                SOCPossibleSettlement posSet = possibleSettlements.get(adjNode);

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
                    SOCPossibleSettlement newPosSet = new SOCPossibleSettlement(player, adjNode.intValue(), null);
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
        Vector<SOCPossibleRoad> newPossibleRoads = new Vector<SOCPossibleRoad>();
        Vector<SOCPossibleRoad> roadsToExpand = new Vector<SOCPossibleRoad>();

        //
        // check adjacent edges to road
        //
        Vector<Integer> adjEdgesEnum = board.getAdjacentEdgesToEdge(road.getCoordinates());
        for (Integer adjEdge : adjEdgesEnum)
        {
            final int edge = adjEdge.intValue();

            //D.ebugPrintln("$$$ edge "+Integer.toHexString(adjEdge.intValue())+" is legal:"+player.isPotentialRoad(adjEdge.intValue()));
            //
            // see if edge is a potential road
            // or ship to continue this route
            //
            boolean edgeIsPotentialRoute =
                (road.isRoadNotShip())
                ? player.isPotentialRoad(edge)
                : player.isPotentialShip(edge);

            // If true, this edge transitions
            // between ships <-> roads, at a
            // coastal settlement
            boolean edgeRequiresCoastalSettlement = false;

            if ((! edgeIsPotentialRoute)
                && game.hasSeaBoard)
            {
                // Determine if can transition ship <-> road
                // at a coastal settlement
                final int nodeBetween = ((SOCBoardLarge) board).getNodeBetweenAdjacentEdges(road.getCoordinates(), edge);
                if (player.canPlaceSettlement(nodeBetween))
                {
                    // check opposite type at transition
                    edgeIsPotentialRoute = (road.isRoadNotShip())
                        ? player.isPotentialShip(edge)
                        : player.isPotentialRoad(edge);

                    if (edgeIsPotentialRoute)
                        edgeRequiresCoastalSettlement = true;
                }
            }

            if (edgeIsPotentialRoute)
            {
                //
                // see if possible road is already in the list
                //
                SOCPossibleRoad pr = possibleRoads.get(adjEdge);

                if (pr != null)
                {
                    // if so, it must be the same type for now (TODO).
                    //   For now, can't differ along a coastal route.
                    if (edgeRequiresCoastalSettlement && (pr.isRoadNotShip() != road.isRoadNotShip()))
                    {
                        continue;  // <--- road vs ship mismatch ---
                    }

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
                    SOCPossibleRoad newPR;
                    final int roadsBetween;  // for effort if requires settlement
                    boolean isRoad = road.isRoadNotShip();
                    if (edgeRequiresCoastalSettlement)
                    {
                        isRoad = ! isRoad;
                        roadsBetween = 2;  // roughly account for effort & cost of new settlement
                    } else {
                        roadsBetween = 0;
                    }

                    // use coastal road/ship type (isCoastalRoadAndShip) only if we can
                    // require a coastal settlement to switch from road-only or ship-only
                    final boolean isCoastal = edgeRequiresCoastalSettlement
                        && player.isPotentialRoad(edge) && player.isPotentialShip(edge);

                    if (isRoad && ! isCoastal)
                    {
                        newPR = new SOCPossibleRoad(player, edge, null);
                    } else {
                        newPR = new SOCPossibleShip(player, edge, isCoastal, null);
                        System.err.println
                            ("L793: " + toString() + ": new PossibleShip(" + isCoastal + ") at 0x" + Integer.toHexString(edge));
                    }
                    newPR.setNumberOfNecessaryRoads(roadsBetween);  // 0 unless requires settlement
                    newPossibleRoads.addElement(newPR);
                    roadsToExpand.addElement(newPR);
                    newPR.setExpandedFlag();
                }
            }
        }

        //
        // add the new roads to our list of possible roads
        //
        for (SOCPossibleRoad newPR : newPossibleRoads)
        {
            possibleRoads.put(new Integer(newPR.getCoordinates()), newPR);
        }

        //
        // expand possible roads that we've touched or added
        //
        SOCPlayer dummy = new SOCPlayer(player);
        for (SOCPossibleRoad expandPR : roadsToExpand)
        {
            expandRoadOrShip(expandPR, player, dummy, trackers, expandLevel);
        }

        dummy.destroyPlayer();

        //
        // in scenario _SC_PIRI, update the closest ship to our fortress
        //
        if ((road instanceof SOCShip) && game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
            updateScenario_SC_PIRI_closestShipToFortress((SOCShip) road, true);
    }

    /**
     * Expand a possible road or ship, to see what placements it makes possible.
     *<UL>
     *<LI> Creates {@code dummyRoad}: A copy of {@code targetRoad} owned by {@code dummy}
     *<LI> Calls {@link SOCPlayer#putPiece(SOCPlayingPiece, boolean) dummy.putPiece(dummyRoad, true)}
     *<LI> Adds to or updates {@link #possibleSettlements} at <tt>targetRoad</tt>'s nodes, if potential
     *<LI> If {@code level > 0}: Calls itself recursively to go more levels out from the current pieces,
     *   adding/updating {@link #possibleRoads} and {@link #possibleSettlements}
     *<LI> Calls {@link SOCPlayer#removePiece(SOCPlayingPiece, SOCPlayingPiece) dummy.removePiece(dummyRoad, null)}
     *</UL>
     *<P>
     * <b>Scenario {@code _SC_PIRI}</b>: Ships in this scenario never expand east (never away from the
     * pirate fortress). Scenario rules require the route to be as short as possible. Even if another (human)
     * player might want to do so, they couldn't interfere with the bot's own route, so we don't track
     * that possibility.
     *
     * @param targetRoad   the possible road
     * @param player    the player who owns the original road
     * @param dummy     the dummy player used to see what's legal; created by caller copying {@code player}
     * @param trackers  player trackers
     * @param level     how many levels (additional pieces) to expand;
     *                  0 to only check <tt>targetRoad</tt> for potential settlements
     *                  and not expand past it for new roads, ships, or further settlements.
     *                  If {@code level > 0} but {@code dummy} has no more roads or ships
     *                  (depending on {@link SOCPossibleRoad#isRoadNotShip() targetRoad.isRoadNotShip()}),
     *                  acts as if {@code level == 0}.
     */
    public void expandRoadOrShip
        (final SOCPossibleRoad targetRoad, final SOCPlayer player, final SOCPlayer dummy,
         HashMap<Integer, SOCPlayerTracker> trackers, final int level)
    {
        //D.ebugPrintln("$$$ expandRoad at "+Integer.toHexString(targetRoad.getCoordinates())+" level="+level);

        final SOCBoard board = game.getBoard();
        final int tgtRoadEdge = targetRoad.getCoordinates();
        final boolean isRoadNotShip = targetRoad.isRoadNotShip();
        final SOCRoad dummyRoad;
        if (isRoadNotShip
            || ((targetRoad instanceof SOCPossibleShip) && ((SOCPossibleShip) targetRoad).isCoastalRoadAndShip))
            dummyRoad = new SOCRoad(dummy, tgtRoadEdge, board);
            // TODO better handling for coastal roads/ships
        else
            dummyRoad = new SOCShip(dummy, tgtRoadEdge, board);

        dummy.putPiece(dummyRoad, true);

        //
        // see if this road/ship adds any new possible settlements
        // (check road's adjacent nodes)
        //
        //D.ebugPrintln("$$$ checking for possible settlements");
        //
        Vector<Integer> adjNodeEnum = board.getAdjacentNodesToEdge(tgtRoadEdge);
        for (Integer adjNode : adjNodeEnum)
        {
            if (dummy.canPlaceSettlement(adjNode.intValue()))
            {
                //
                // see if possible settlement is already in the list
                //
                //D.ebugPrintln("$$$ seeing if "+Integer.toHexString(adjNode.intValue())+" is already in the list");
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
                        //D.ebugPrintln("$$$ adding road "+Integer.toHexString(targetRoad.getCoordinates())+" to the settlement "+Integer.toHexString(posSet.getCoordinates()));
                        posSet.getNecessaryRoads().addElement(targetRoad);
                        targetRoad.addNewPossibility(posSet);

                        //
                        // update settlement's numberOfNecessaryRoads if this road reduces it
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

        if ((level > 0) && (0 < dummy.getNumPieces(isRoadNotShip ? SOCPlayingPiece.ROAD : SOCPlayingPiece.SHIP)))
        {
            //
            // check for new possible roads or ships.
            // The above getNumPieces check ignores any possible ship <-> road transition at a coastal settlement.
            //
            Vector<SOCPossibleRoad> newPossibleRoads = new Vector<SOCPossibleRoad>();
            Vector<SOCPossibleRoad> roadsToExpand = new Vector<SOCPossibleRoad>();

            // ships in _SC_PIRI never expand east
            final boolean isShipInSC_PIRI = (! isRoadNotShip) && game.isGameOptionSet(SOCGameOption.K_SC_PIRI);

            //D.ebugPrintln("$$$ checking roads adjacent to "+Integer.toHexString(targetRoad.getCoordinates()));
            //
            // check adjacent edges to road or ship
            //
            Enumeration<Integer> adjEdgesEnum = board.getAdjacentEdgesToEdge(tgtRoadEdge).elements();
            while (adjEdgesEnum.hasMoreElements())
            {
                Integer adjEdge = adjEdgesEnum.nextElement();
                final int edge = adjEdge.intValue();

                if (isShipInSC_PIRI)
                {
                    final int tgtEdgeCol = tgtRoadEdge & 0xFF, adjEdgeCol = edge & 0xFF;
                    if ((adjEdgeCol > tgtEdgeCol)  // adjacent goes north/south from eastern node of diagonal target edge
                        || ((adjEdgeCol == tgtEdgeCol) && ((tgtRoadEdge & 0x100) != 0)))
                            // adjacent goes northeast/southeast from vertical target edge (tgtRoadEdge is on odd row)
                    {
                        continue;  // <--- Ignore this eastern adjacent edge ---
                    }
                }

                //D.ebugPrintln("$$$ edge "+Integer.toHexString(adjEdge.intValue())+" is legal:"+dummy.isPotentialRoad(adjEdge.intValue()));
                //
                // see if edge is a potential road
                // or ship to continue this route
                //
                boolean edgeIsPotentialRoute =
                    (isRoadNotShip)
                    ? dummy.isPotentialRoad(edge)
                    : dummy.isPotentialShip(edge);

                // If true, this edge transitions
                // between ships <-> roads, at a
                // coastal settlement
                boolean edgeRequiresCoastalSettlement = false;

                if ((! edgeIsPotentialRoute)
                    && game.hasSeaBoard)
                {
                    // Determine if can transition ship <-> road
                    // at a coastal settlement
                    final int nodeBetween =
                        ((SOCBoardLarge) board).getNodeBetweenAdjacentEdges(tgtRoadEdge, edge);
                    if (dummy.canPlaceSettlement(nodeBetween))
                    {
                        // check opposite type at transition
                        edgeIsPotentialRoute = (isRoadNotShip)
                            ? dummy.isPotentialShip(edge)
                            : dummy.isPotentialRoad(edge);

                        if (edgeIsPotentialRoute)
                            edgeRequiresCoastalSettlement = true;
                    }
                }

                if (edgeIsPotentialRoute)
                {
                    // Add 1 to road distance, unless
                    // it requires a coastal settlement
                    // (extra effort to build that)
                    final int incrDistance
                        = edgeRequiresCoastalSettlement ? 3 : 1;

                    //
                    // see if possible road is already in the list
                    //
                    SOCPossibleRoad pr = possibleRoads.get(adjEdge);

                    if (pr != null)
                    {
                        // if so, it must be the same type for now (TODO).
                        //   For now, can't differ along a coastal route.
                        if (edgeRequiresCoastalSettlement
                            && (isRoadNotShip != pr.isRoadNotShip()))
                        {
                            continue;  // <--- road vs ship mismatch ---
                        }

                        //
                        // if so, and it needs 1 or more roads other than this one,
                        //
                        //D.ebugPrintln("$$$ pr "+Integer.toHexString(pr.getCoordinates())+" already in list");
                        Vector<SOCPossibleRoad> nr = pr.getNecessaryRoads();

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
                            if ((targetRoad.getNumberOfNecessaryRoads() + incrDistance) < pr.getNumberOfNecessaryRoads())
                            {
                                pr.setNumberOfNecessaryRoads(targetRoad.getNumberOfNecessaryRoads() + incrDistance);
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
                        // else, add new possible road or ship
                        //
                        //D.ebugPrintln("$$$ adding new pr at "+Integer.toHexString(adjEdge.intValue()));
                        Vector<SOCPossibleRoad> neededRoads = new Vector<SOCPossibleRoad>();
                        neededRoads.addElement(targetRoad);

                        SOCPossibleRoad newPR;
                        boolean isRoad = isRoadNotShip;
                        if (edgeRequiresCoastalSettlement)
                            isRoad = ! isRoad;

                        // use coastal road/ship type (isCoastalRoadAndShip) only if the road/ship
                        // being expanded is coastal, or if we can require a coastal settlement to
                        // switch from road-only or ship-only
                        final boolean isCoastal =
                            dummy.isPotentialRoad(edge) && dummy.isPotentialShip(edge)
                            && (edgeRequiresCoastalSettlement
                                || ((targetRoad instanceof SOCPossibleShip)
                                    && ((SOCPossibleShip) targetRoad).isCoastalRoadAndShip));

                        if (isRoad && ! isCoastal)
                        {
                            newPR = new SOCPossibleRoad(player, edge, neededRoads);
                        } else {
                            newPR = new SOCPossibleShip(player, edge, isCoastal, neededRoads);
                            System.err.println
                                ("L1072: " + toString() + ": new PossibleShip(" + isCoastal + ") at 0x" + Integer.toHexString(edge));
                        }
                        newPR.setNumberOfNecessaryRoads(targetRoad.getNumberOfNecessaryRoads() + incrDistance);
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
            // expand roads that we've touched or added
            //
            Enumeration<SOCPossibleRoad> expandPREnum = roadsToExpand.elements();
            while (expandPREnum.hasMoreElements())
            {
                SOCPossibleRoad expandPR = expandPREnum.nextElement();
                expandRoadOrShip(expandPR, player, dummy, trackers, level - 1);
            }
        }

        //
        // remove the dummy road
        //
        dummy.removePiece(dummyRoad, null);
    }

    /**
     * add another player's new road or ship, or cancel our own bad road
     * by acting as if another player has placed there.
     * (That way, we won't decide to place there again.)
     *
     * @param road  the new road or ship
     * @param isCancel Is this our own robot's road placement, rejected by the server?
     *     If so, this method call will cancel its placement within the tracker data.
     */
    private void addTheirNewRoadOrShip(SOCRoad road, boolean isCancel)
    {
        /**
         * see if another player's road interferes with our possible roads
         * and settlements
         */
        /**
         * if another player's road is on one of our possible
         * roads, then remove it
         */
        D.ebugPrintln("$$$ addTheirNewRoadOrShip : " + road);

        Integer roadCoordinates = new Integer(road.getCoordinates());
        SOCPossibleRoad pr = possibleRoads.get(roadCoordinates);

        if (pr != null)
        {
            //D.ebugPrintln("$$$ removing road at "+Integer.toHexString(pr.getCoordinates()));
            possibleRoads.remove(roadCoordinates);
            removeFromNecessaryRoads(pr);
            removeDependents(pr);
        }
    }

    /**
     * For scenario {@code _SC_PIRI}, get the player's ship closest to their Fortress (the ship farthest west).
     * Updated by {@link #updateScenario_SC_PIRI_closestShipToFortress(SOCShip, boolean)}.
     * @return the closest ship in scenario {@code _SC_PIRI}; {@code null} otherwise.
     * @see #getScenario_SC_PIRI_shipDistanceToFortress(SOCShip)
     * @since 2.0.00
     */
    public SOCShip getScenario_SC_PIRI_closestShipToFortress()
    {
        return scen_SC_PIRI_closestShipToFortress;
    }

    /**
     * For scenario {@code _SC_PIRI}, update the player's ship closest to their Fortress.
     * Assumes no ship will ever be west of the fortress (smaller column number).
     * Must be called after adding or removing a ship from our player's {@link SOCPlayer#getRoads()}.
     * @param ship  Ship that was added or removed, or {@code null} to check all ships after removal
     * @param shipAdded  True if {@code ship} was added; false if {@code ship} or any other ship was removed
     *            or if we're updating Closest Ship without adding or removing a ship
     * @throws IllegalArgumentException if {@code shipAdded} is true, but null {@code ship}
     * @since 2.0.00
     */
    void updateScenario_SC_PIRI_closestShipToFortress(final SOCShip ship, final boolean shipAdded)
        throws IllegalArgumentException
    {
        if (shipAdded && (ship == null))
            throw new IllegalArgumentException();

        if ((scen_SC_PIRI_closestShipToFortress == null) && (ship != null))
        {
            if (shipAdded)
                scen_SC_PIRI_closestShipToFortress = ship;  // closest by default

            return;  // <--- Early return: no other ships to compare ---
        }

        if (! shipAdded)
        {
            // A ship has been removed.  If we know what ship, and
            // it's not the closest ship, we don't need to do anything.

            if ((ship != null) && (scen_SC_PIRI_closestShipToFortress != null)
                && (ship.getCoordinates() != scen_SC_PIRI_closestShipToFortress.getCoordinates()))
                return;  // <--- Early return: Not the closest ship ---
        }

        final SOCFortress fort = player.getFortress();  // may be null towards end of game
            // If fort's null, we can still compare columns, just not rows, of ship coordinates.
        final int fortR = (fort != null)
            ? (fort.getCoordinates() >> 8)
            : -1;

        if (shipAdded)
        {
            final int shipEdge = ship.getCoordinates(),
                      prevShipEdge = scen_SC_PIRI_closestShipToFortress.getCoordinates();
            final int shipR = shipEdge >> 8, shipC = shipEdge & 0xFF,
                      prevR = prevShipEdge >> 8, prevC = prevShipEdge & 0xFF;
            if ((shipC < prevC)
                || ((shipC == prevC) && (fortR != -1)
                    && (Math.abs(shipR - fortR) < Math.abs(prevR - fortR))))
            {
                scen_SC_PIRI_closestShipToFortress = ship;
            }
        } else {
            // A ship has been removed.  We don't know which one.
            // So, check all ships for distance from fortress.

            Enumeration<SOCRoad> roadAndShipEnum = player.getRoads().elements();

            SOCShip closest = null;
            int closeR = -1, closeC = -1;
            while (roadAndShipEnum.hasMoreElements())
            {
                final SOCRoad rs = roadAndShipEnum.nextElement();
                if (! (rs instanceof SOCShip))
                    continue;

                final int shipEdge = rs.getCoordinates();
                final int shipR = shipEdge >> 8, shipC = shipEdge & 0xFF;

                if ((closest == null)
                    || (shipC < closeC)
                    || ((shipC == closeC) && (fortR != -1)
                        && (Math.abs(shipR - fortR) < Math.abs(closeR - fortR))))
                {
                    closest = (SOCShip) rs;
                    closeR = shipR;
                    closeC = shipC;
                }
            }

            scen_SC_PIRI_closestShipToFortress = closest;  // null if no ships
        }
    }

    /**
     * For scenario {@code _SC_PIRI}, get the distance of this player's closest ship from their
     * {@code SOCFortress}. Since ships aren't placed diagonally, this is the distance along rows + columns.
     * The edge (r,c) has node (r,c) as its left end, at distance 0.
     * @param ship  Any ship, including {@link #getScenario_SC_PIRI_closestShipToFortress()}
     * @return row distance + column distance based on piece coordinates;
     *     or 0 if no fortress which would mean the fortress was reached
     *     (distance 0) and defeated already.
     * @since 2.0.00
     */
    public int getScenario_SC_PIRI_shipDistanceToFortress(final SOCShip ship)
    {
        final SOCFortress fort = player.getFortress();  // may be null towards end of game
        if (fort == null)
            return 0;

        final int fortNode = fort.getCoordinates(),
                  shipEdge = ship.getCoordinates();
        final int fortR = fortNode >> 8, fortC = fortNode & 0xFF,
                  shipR = shipEdge >> 8, shipC = shipEdge & 0xFF;

        return Math.abs(fortR - shipR) + Math.abs(fortC - shipC);
    }

    /**
     * For scenario {@code _SC_PIRI}, get the player's next potential ship towards their Fortress.
     * If fortress was already defeated, or they have no boats, returns {@code null}.
     *<P>
     * This is calculated every time, not cached, because potential-ships list may change often.
     * Calls {@link #updateScenario_SC_PIRI_closestShipToFortress(SOCShip, boolean)} if closest ship not known.
     *
     * @return Next potential ship, or {@code null}
     * @since 2.0.00
     */
    SOCPossibleShip recalcScenario_SC_PIRI_nextPotentialShip()
    {
        final SOCFortress fort = player.getFortress();  // may be null towards end of game
        if (fort == null)
            return null;  // <--- Early return: already defeated fortress ---
        final int fortR = fort.getCoordinates() >> 8;

        if (scen_SC_PIRI_closestShipToFortress == null)
            updateScenario_SC_PIRI_closestShipToFortress(null, false);

        final SOCShip closest = scen_SC_PIRI_closestShipToFortress;
        if (closest == null)
            return null;  // <--- Early return: no ships ---
        final Vector<Integer> closestAdjacs =
            ((SOCBoardLarge) game.getBoard()).getAdjacentEdgesToEdge(closest.getCoordinates());

        SOCPossibleShip nextShip = null;
        int nextR = -1, nextC = -1;
        for (Integer edge : closestAdjacs)
        {
            final SOCPossibleRoad rs = possibleRoads.get(edge);
            if ((rs == null) || ! (rs instanceof SOCPossibleShip))
                continue;

            final int shipEdge = rs.getCoordinates();
            final int shipR = shipEdge >> 8, shipC = shipEdge & 0xFF;

            if ((nextShip == null)
                || (shipC < nextC)
                || ((shipC == nextC)
                    && (Math.abs(shipR - fortR) < Math.abs(nextR - fortR))))
            {
                nextShip = (SOCPossibleShip) rs;
                nextR = shipR;
                nextC = shipC;
            }
        }

        return nextShip;
    }

    /**
     * update settlement conflicts
     *
     * @param ps        a possible settlement
     * @param trackers  player trackers for all players
     */
    protected void updateSettlementConflicts(SOCPossibleSettlement ps, HashMap<Integer, SOCPlayerTracker> trackers)
    {
        //D.ebugPrintln("$$$ updateSettlementConflicts : "+Integer.toHexString(ps.getCoordinates()));

        /**
         * look at all adjacent nodes and update possible settlements on nodes
         */
        Iterator<SOCPlayerTracker> trackersIter = trackers.values().iterator();
        SOCBoard board = game.getBoard();

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
                    //D.ebugPrintln("$$$ add conflict "+Integer.toHexString(posSet.getCoordinates()));
                    ps.addConflict(posSet);
                    posSet.addConflict(ps);
                }
            }

            /**
             * now look at adjacent settlements
             */
            Enumeration<Integer> adjNodeEnum = board.getAdjacentNodesToNode(ps.getCoordinates()).elements();

            while (adjNodeEnum.hasMoreElements())
            {
                Integer adjNode = adjNodeEnum.nextElement();
                SOCPossibleSettlement posSet = tracker.getPossibleSettlements().get(adjNode);

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
     * Add a settlement that has just been built.
     * Called only after {@link SOCGame#putPiece(SOCPlayingPiece)}
     * or {@link SOCGame#putTempPiece(SOCPlayingPiece)}.
     *
     * @param settlement       the settlement
     * @param trackers         player trackers for the players
     */
    public synchronized void addNewSettlement(SOCSettlement settlement, HashMap<Integer, SOCPlayerTracker> trackers)
    {
        //D.ebugPrintln("%$% settlement owner ="+settlement.getPlayer().getPlayerNumber());
        //D.ebugPrintln("%$% tracker owner ="+player.getPlayerNumber());
        if (settlement.getPlayerNumber() == playerNumber)
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
     * @since 1.1.00
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
        D.ebugPrintln("$$$ removing (wrong) " + Integer.toHexString(settlement.getCoordinates()));
        possibleSettlements.remove(settlementCoords);
        removeFromNecessaryRoads(ps);

    }

    /**
     * Add one of our settlements, and newly possible pieces from it.
     * Adds a new possible city; removes conflicting possible settlements (ours or other players).
     * On the large Sea board, if this is a coastal settlement adds newly possible ships, and if
     * we've just settled a new island, newly possible roads, because the coastal settlement is
     * a roads {@literal <->} ships transition.
     *<P>
     * Newly possible roads or ships next to the settlement are expanded by calling
     * {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, HashMap, int)}.
     * {@link #EXPAND_LEVEL} is the basic expansion length, and ships add
     * {@link #EXPAND_LEVEL_SHIP_EXTRA} to that for crossing the sea to nearby islands.
     *<P>
     * Called in 2 different conditions:
     *<UL>
     * <LI> To track an actual (not possible) settlement that's just been placed
     * <LI> To see the effects of trying to placing a possible settlement, in a copy of the PlayerTracker
     *      ({@link #tryPutPiece(SOCPlayingPiece, SOCGame, HashMap)})
     *</UL>
     *
     * @param settlement  the new settlement
     * @param trackers    player trackers for all of the players
     */
    public synchronized void addOurNewSettlement(SOCSettlement settlement, HashMap<Integer, SOCPlayerTracker> trackers)
    {
        //D.ebugPrintln();
        D.ebugPrintln("$$$ addOurNewSettlement : " + settlement);
        SOCBoard board = game.getBoard();

        final Integer settlementCoords = new Integer(settlement.getCoordinates());

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
            D.ebugPrintln("$$$ was a possible settlement");

            /**
             * copy a list of all the conflicting settlements
             */
            Vector<SOCPossibleSettlement> conflicts = new Vector<SOCPossibleSettlement>(ps.getConflicts());

            /**
             * remove the possible settlement that is now a real settlement
             */
            D.ebugPrintln("$$$ removing " + Integer.toHexString(settlement.getCoordinates()));
            possibleSettlements.remove(settlementCoords);
            removeFromNecessaryRoads(ps);

            /**
             * remove possible settlements that this one cancels out
             */
            Enumeration<SOCPossibleSettlement> conflictEnum = conflicts.elements();

            while (conflictEnum.hasMoreElements())
            {
                SOCPossibleSettlement conflict = conflictEnum.nextElement();
                D.ebugPrintln("$$$ checking conflict with " + conflict.getPlayer().getPlayerNumber() + ":" + Integer.toHexString(conflict.getCoordinates()));

                SOCPlayerTracker tracker = trackers.get(new Integer(conflict.getPlayer().getPlayerNumber()));

                if (tracker != null)
                {
                    D.ebugPrintln("$$$ removing " + Integer.toHexString(conflict.getCoordinates()));
                    tracker.getPossibleSettlements().remove(new Integer(conflict.getCoordinates()));
                    removeFromNecessaryRoads(conflict);

                    /**
                     * remove the conflicts that this settlement made
                     */
                    Enumeration<SOCPossibleSettlement> otherConflictEnum = conflict.getConflicts().elements();

                    while (otherConflictEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement otherConflict = otherConflictEnum.nextElement();
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

            Vector<SOCPossibleSettlement> trash = new Vector<SOCPossibleSettlement>();
            Vector<Integer> adjNodes = board.getAdjacentNodesToNode(settlement.getCoordinates());
            Iterator<SOCPlayerTracker> trackersIter = trackers.values().iterator();

            while (trackersIter.hasNext())
            {
                SOCPlayerTracker tracker = trackersIter.next();
                SOCPossibleSettlement posSet = tracker.getPossibleSettlements().get(settlementCoords);
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
                    Enumeration<SOCPossibleSettlement> conflictEnum = posSet.getConflicts().elements();

                    while (conflictEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement conflict = conflictEnum.nextElement();
                        D.ebugPrintln("$$$ removing conflict " + Integer.toHexString(posSet.getCoordinates()) + " from " + Integer.toHexString(conflict.getCoordinates()));
                        conflict.removeConflict(posSet);
                    }
                }

                /**
                 * check adjacent nodes
                 */
                Enumeration<Integer> adjNodeEnum = adjNodes.elements();

                while (adjNodeEnum.hasMoreElements())
                {
                    Integer adjNode = adjNodeEnum.nextElement();
                    D.ebugPrintln("$$$ checking node " + Integer.toHexString(adjNode.intValue()));
                    posSet = tracker.getPossibleSettlements().get(adjNode);

                    if (posSet != null)
                    {
                        D.ebugPrintln("$$$ trashing " + Integer.toHexString(posSet.getCoordinates()));
                        trash.addElement(posSet);

                        /**
                         * remove the conflicts that this settlement made
                         */
                        Enumeration<SOCPossibleSettlement> conflictEnum = posSet.getConflicts().elements();

                        while (conflictEnum.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = conflictEnum.nextElement();
                            D.ebugPrintln("$$$ removing conflict " + Integer.toHexString(posSet.getCoordinates()) + " from " + Integer.toHexString(conflict.getCoordinates()));
                            conflict.removeConflict(posSet);
                        }
                    }
                }

                /**
                 * take out the trash
                 * (no-longer-possible settlements, roads that support it)
                 */
                D.ebugPrintln("$$$ removing trash for " + tracker.getPlayer().getPlayerNumber());

                Enumeration<SOCPossibleSettlement> trashEnum = trash.elements();

                while (trashEnum.hasMoreElements())
                {
                    SOCPossibleSettlement pset = trashEnum.nextElement();
                    D.ebugPrintln("$$$ removing " + Integer.toHexString(pset.getCoordinates()) + " owned by " + pset.getPlayer().getPlayerNumber());
                    tracker.getPossibleSettlements().remove(new Integer(pset.getCoordinates()));
                    removeFromNecessaryRoads(pset);
                }

                trash.removeAllElements();
            }
        }

        /**
         * Add possible road-ship transitions made possible by the new settlement.
         * Normally a new settlement placement doesn't need to add possible roads or ships,
         * because each road/ship placement adds possibles past the new far end of the route
         * in addOurNewRoadOrShip.
         */
        if (board instanceof SOCBoardLarge)
        {
            Vector<SOCPossibleRoad> roadsToExpand = null;

            /**
             * Only add new possible roads if we're on a new island
             * (that is, the newly placed settlement has no adjacent roads already).
             * Coastal ships/roads may still be added even if settleAlreadyHasRoad.
             */
            boolean settleAlreadyHasRoad = false;
            Vector<SOCPossibleRoad> possibleNewIslandRoads = null;

            final Vector<Integer> adjacEdges = board.getAdjacentEdgesToNode(settlementCoords);

            // First, loop to check for settleAlreadyHasRoad
            for (Integer edge : adjacEdges)
            {
                if (possibleRoads.get(edge) != null)
                    continue;  // already a possible road or ship here

                SOCRoad road = board.roadAtEdge(edge);
                if ((road != null) && road.isRoadNotShip())
                {
                    settleAlreadyHasRoad = true;
                    break;
                }
            }

            // Now, possibly add new roads/ships/coastals
            for (Integer edge : adjacEdges)
            {
                // TODO remove these debug prints soon
                //System.err.println("L1348: examine edge 0x"
                //    + Integer.toHexString(edge) + " for placed settle 0x"
                //    + Integer.toHexString(settlementCoords));

                SOCPossibleRoad pRoad = possibleRoads.get(edge);
                if (pRoad != null)
                {
                    //if (pRoad.isRoadNotShip())
                    //    System.err.println("  -> already possible road");
                    //else
                    //    System.err.println("  -> already possible ship");
                    continue;  // already a possible road or ship
                }

                if (board.roadAtEdge(edge) != null)
                {
                    continue;  // not new, something's already there
                }

                if (player.isPotentialRoad(edge))
                {
                    // Add newly possible roads from settlement placement.
                    // Probably won't need to happen (usually added in addOurNewRoadOrShip, see newPossibleRoads)
                    // but could on a new island's first settlement

                    final boolean isCoastline = player.isPotentialShip(edge);
                    if (settleAlreadyHasRoad && ! isCoastline)
                        continue;

                    if (possibleNewIslandRoads == null)
                        possibleNewIslandRoads = new Vector<SOCPossibleRoad>();
                    possibleNewIslandRoads.add( (isCoastline)
                        ? new SOCPossibleShip(player, edge, true, null)
                        : new SOCPossibleRoad(player, edge, null));
                    if (isCoastline)
                        System.err.println
                            ("L1675: " + toString() + ": new PossibleShip(true) at 0x" + Integer.toHexString(edge));
                }
                else if (player.isPotentialShip(edge))
                {
                    // A way out to a new island
                    SOCPossibleShip newPS = new SOCPossibleShip(player, edge, false, null);
                    possibleRoads.put(edge, newPS);
                    System.err.println("L1685: " + toString() + ": new PossibleShip(false) at 0x" + Integer.toHexString(edge)
                        + " from coastal settle 0x" + Integer.toHexString(settlementCoords));
                    if (roadsToExpand == null)
                        roadsToExpand = new Vector<SOCPossibleRoad>();
                    roadsToExpand.addElement(newPS);
                    newPS.setExpandedFlag();
                }
            }

            if ((possibleNewIslandRoads != null)
                && ! game.isInitialPlacement())
            {
                // only add new possible roads if we're on a new island
                // (that is, the newly placed settlement has no adjacent roads already).
                // (Make sure this isn't initial placement, where nothing has adjacent roads)
                for (SOCPossibleRoad pr : possibleNewIslandRoads)
                {
                    possibleRoads.put(Integer.valueOf(pr.getCoordinates()), pr);
                    System.err.println("L1396: new possible road at edge 0x"
                        + Integer.toHexString(pr.getCoordinates()) + " from coastal settle 0x"
                        + Integer.toHexString(settlementCoords));
                    if (roadsToExpand == null)
                        roadsToExpand = new Vector<SOCPossibleRoad>();
                    roadsToExpand.addElement(pr);
                    pr.setExpandedFlag();
                }
            }

            if (roadsToExpand != null)
            {
                //
                // expand possible ships/roads that we've added
                //
                SOCPlayer dummy = new SOCPlayer(player);
                for (SOCPossibleRoad expandPR : roadsToExpand)
                {
                    final int expand = EXPAND_LEVEL + (expandPR.isRoadNotShip() ? 0 : EXPAND_LEVEL_SHIP_EXTRA);
                    expandRoadOrShip(expandPR, player, dummy, trackers, expand);
                }
                dummy.destroyPlayer();
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

        Vector<SOCPossibleRoad> prTrash = new Vector<SOCPossibleRoad>();
        Vector<SOCPossibleRoad> nrTrash = new Vector<SOCPossibleRoad>();
        Vector<Integer> adjEdges = game.getBoard().getAdjacentEdgesToNode(settlement.getCoordinates());
        Enumeration<Integer> edge1Enum = adjEdges.elements();

        while (edge1Enum.hasMoreElements())
        {
            prTrash.removeAllElements();

            Integer edge1 = edge1Enum.nextElement();
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
                        final int settleCoord = settlement.getCoordinates(),
                                  settlePN    = settlement.getPlayerNumber();
                        Enumeration<SOCPossiblePiece> threatEnum = pr.getThreats().elements();

                        while (threatEnum.hasMoreElements())
                        {
                            SOCPossiblePiece threat = threatEnum.nextElement();

                            if ((threat.getType() == SOCPossiblePiece.SETTLEMENT) && (threat.getCoordinates() == settleCoord) && (threat.getPlayer().getPlayerNumber() == settlePN))
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

                    Enumeration<SOCPossibleRoad> nrEnum = pr.getNecessaryRoads().elements();

                    while (nrEnum.hasMoreElements())
                    {
                        SOCPossibleRoad nr = nrEnum.nextElement();
                        final int nrEdge = nr.getCoordinates();
                        Enumeration<Integer> edge2Enum = adjEdges.elements();

                        while (edge2Enum.hasMoreElements())
                        {
                            Integer edge2 = edge2Enum.nextElement();

                            if (nrEdge == edge2.intValue())
                            {
                                D.ebugPrintln("$$$ removing dependency " + Integer.toHexString(nrEdge) + " from " + Integer.toHexString(pr.getCoordinates()));
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
                            D.ebugPrintln("$$$ no more dependencies, removing " + Integer.toHexString(pr.getCoordinates()));
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

        //D.ebugPrintln("$$$ removeDependents "+Integer.toHexString(road.getCoordinates()));
        Enumeration<SOCPossiblePiece> newPosEnum = road.getNewPossibilities().elements();

        while (newPosEnum.hasMoreElements())
        {
            SOCPossiblePiece newPos = newPosEnum.nextElement();

            //D.ebugPrintln("$$$ updating "+Integer.toHexString(newPos.getCoordinates()));
            Vector<SOCPossibleRoad> nr;

            switch (newPos.getType())
            {
            case SOCPossiblePiece.SHIP:  // fall through to ROAD
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
                        //D.ebugPrintln("$$$ removing this settlement");
                        possibleSettlements.remove(new Integer(newPos.getCoordinates()));
                        removeFromNecessaryRoads((SOCPossibleSettlement) newPos);

                        /**
                         * remove the conflicts that this settlement made
                         */
                        Enumeration<SOCPossibleSettlement> conflictEnum = ((SOCPossibleSettlement) newPos).getConflicts().elements();

                        while (conflictEnum.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = conflictEnum.nextElement();
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
        //D.ebugPrintln("%%% remove road from necessary roads");
        Enumeration<SOCPossibleRoad> nrEnum = pr.getNecessaryRoads().elements();

        while (nrEnum.hasMoreElements())
        {
            SOCPossibleRoad nr = nrEnum.nextElement();

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
        Enumeration<SOCPossibleRoad> nrEnum = ps.getNecessaryRoads().elements();

        while (nrEnum.hasMoreElements())
        {
            SOCPossibleRoad nr = nrEnum.nextElement();

            //D.ebugPrintln("%%% removing settlement at "+Integer.toHexString(ps.getCoordinates())+" from road at "+Integer.toHexString(nr.getCoordinates()));
            nr.getNewPossibilities().removeElement(ps);
        }
    }

    /**
     * Remove our incorrect city placement, it's been rejected by the server.
     *<P>
     * Note, there is no addNewCity or addTheirNewCity method.
     *
     * @param city Location of our bad city
     *
     * @see SOCRobotBrain#cancelWrongPiecePlacement(SOCCancelBuildRequest)
     * @since 1.1.00
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
    public void updateThreats(HashMap<Integer, SOCPlayerTracker> trackers)
    {
        //D.ebugPrintln("&&&& updateThreats");

        /**
         * check roads that need updating and don't have necessary roads
         */
        SOCBoard board = game.getBoard();
        Iterator<SOCPossibleRoad> posRoadsIter = possibleRoads.values().iterator();

        while (posRoadsIter.hasNext())
        {
            SOCPossibleRoad posRoad = posRoadsIter.next();

            if ((!posRoad.isThreatUpdated()) && (posRoad.getNecessaryRoads().isEmpty()))
            {
                //D.ebugPrintln("&&&& examining road at "+Integer.toHexString(posRoad.getCoordinates()));

                /**
                 * look for possible settlements that can block this road
                 */
                final int[] adjNodesToPosRoad = board.getAdjacentNodesToEdge_arr(posRoad.getCoordinates());
                Enumeration<Integer> adjEdgeEnum = board.getAdjacentEdgesToEdge(posRoad.getCoordinates()).elements();

                while (adjEdgeEnum.hasMoreElements())
                {
                    final int adjEdge = adjEdgeEnum.nextElement().intValue();
                    Enumeration<SOCRoad> realRoadEnum = player.getRoads().elements();

                    while (realRoadEnum.hasMoreElements())
                    {
                        SOCRoad realRoad = realRoadEnum.nextElement();

                        if (adjEdge == realRoad.getCoordinates())
                        {
                            /**
                             * found an adjacent supporting road, now find the node between
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
                                        Iterator<SOCPlayerTracker> trackersIter = trackers.values().iterator();

                                        while (trackersIter.hasNext())
                                        {
                                            SOCPlayerTracker tracker = trackersIter.next();

                                            if (tracker.getPlayer().getPlayerNumber() != playerNumber)
                                            {
                                                SOCPossibleSettlement posEnemySet = tracker.getPossibleSettlements().get(adjNodeToPosRoadInt);

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
                Iterator<SOCPlayerTracker> trackersIter = trackers.values().iterator();

                while (trackersIter.hasNext())
                {
                    SOCPlayerTracker tracker = trackersIter.next();

                    if (tracker.getPlayer().getPlayerNumber() != playerNumber)
                    {
                        SOCPossibleRoad posEnemyRoad = tracker.getPossibleRoads().get(new Integer(posRoad.getCoordinates()));

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
                Vector<SOCPossiblePiece> threats = posRoad.getThreats();
                Stack<SOCPossiblePiece> stack = new Stack<SOCPossiblePiece>();
                stack.push(posRoad);

                while (!stack.empty())
                {
                    SOCPossiblePiece curPosPiece = stack.pop();

                    // TODO roads only; need to also decide how ships are threatened

                    if ((curPosPiece.getType() == SOCPossiblePiece.ROAD)
                        || ((curPosPiece instanceof SOCPossibleShip)
                             && ((SOCPossibleShip) curPosPiece).isCoastalRoadAndShip))
                    {
                        Enumeration<SOCPossiblePiece> newPosEnum = ((SOCPossibleRoad) curPosPiece).getNewPossibilities().elements();

                        while (newPosEnum.hasMoreElements())
                        {
                            SOCPossiblePiece newPosPiece = newPosEnum.nextElement();

                            if ((newPosPiece.getType() == SOCPossiblePiece.ROAD)
                                || ((newPosPiece instanceof SOCPossibleShip)
                                    && ((SOCPossibleShip) newPosPiece).isCoastalRoadAndShip))
                            {
                                Vector<SOCPossibleRoad> necRoadVec = ((SOCPossibleRoad) newPosPiece).getNecessaryRoads();

                                if ((necRoadVec.size() == 1) && (necRoadVec.firstElement() == curPosPiece))
                                {
                                    /**
                                     * pass on all of the threats to this piece
                                     */

                                    //D.ebugPrintln("&&&& adding threats to road at "+Integer.toHexString(newPosPiece.getCoordinates()));
                                    Enumeration<SOCPossiblePiece> threatEnum = threats.elements();

                                    while (threatEnum.hasMoreElements())
                                    {
                                        ((SOCPossibleRoad) newPosPiece).addThreat(threatEnum.nextElement());
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
            SOCPossibleRoad posRoad = posRoadsIter.next();

            if (!posRoad.isThreatUpdated())
            {
                //D.ebugPrintln("&&&& examining road at "+Integer.toHexString(posRoad.getCoordinates()));

                /**
                 * check for enemy roads with
                 * the same coordinates
                 */
                Iterator<SOCPlayerTracker> trackersIter = trackers.values().iterator();

                while (trackersIter.hasNext())
                {
                    SOCPlayerTracker tracker = trackersIter.next();

                    if (tracker.getPlayer().getPlayerNumber() != playerNumber)
                    {
                        SOCPossibleRoad posEnemyRoad = tracker.getPossibleRoads().get(Integer.valueOf(posRoad.getCoordinates()));

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
                Vector<SOCPossibleRoad> necRoadVec = posRoad.getNecessaryRoads();

                if (necRoadVec.size() == 1)
                {
                    SOCPossibleRoad necRoad = necRoadVec.firstElement();
                    final int[] adjNodes1 = board.getAdjacentNodesToEdge_arr(posRoad.getCoordinates());

                    for (int i1 = 0; i1 < 2; ++i1)
                    {
                        final int adjNode1 = adjNodes1[i1];
                        final int[] adjNodes2 = board.getAdjacentNodesToEdge_arr(necRoad.getCoordinates());

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
                                    SOCPlayerTracker tracker = trackersIter.next();

                                    if (tracker.getPlayer().getPlayerNumber() != playerNumber)
                                    {
                                        SOCPossibleSettlement posEnemySet = tracker.getPossibleSettlements().get(adjNodeInt);

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
        Iterator<SOCPossibleSettlement> posSetsIter = possibleSettlements.values().iterator();

        while (posSetsIter.hasNext())
        {
            SOCPossibleSettlement posSet = posSetsIter.next();

            if (!posSet.isThreatUpdated())
            {
                //D.ebugPrintln("&&&& examining settlement at "+Integer.toHexString(posSet.getCoordinates()));

                /**
                 * see if there are enemy settlements with the same coords
                 */
                Iterator<SOCPlayerTracker> trackersIter = trackers.values().iterator();

                while (trackersIter.hasNext())
                {
                    SOCPlayerTracker tracker = trackersIter.next();

                    if (tracker.getPlayer().getPlayerNumber() != playerNumber)
                    {
                        SOCPossibleSettlement posEnemySet = tracker.getPossibleSettlements().get(new Integer(posSet.getCoordinates()));

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
                    //D.ebugPrintln("&&&& inheriting threats from road at "+Integer.toHexString(((SOCPossibleRoad)necRoadVec.firstElement()).getCoordinates()));
                    Enumeration<SOCPossiblePiece> threatEnum = necRoadVec.firstElement().getThreats().elements();

                    while (threatEnum.hasMoreElements())
                    {
                        posSet.addThreat(threatEnum.nextElement());
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
                    Enumeration<SOCPossiblePiece> nrThreatEnum = nr.getThreats().elements();

                    while (nrThreatEnum.hasMoreElements())
                    {
                        SOCPossiblePiece nrThreat = nrThreatEnum.nextElement();
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
     * Calculate the longest road ETA.
     * Always 500 or more if {@link SOCGameOption#K_SC_0RVP} is set.
     * Updates fields for {@link #getLongestRoadETA()} and {@link #getRoadsToGo()}.
     */
    public void recalcLongestRoadETA()
    {
        // TODO handle ships here (different resources, etc)

        D.ebugPrintln("===  recalcLongestRoadETA for player " + playerNumber);

        final int roadETA;
        SOCBuildingSpeedEstimate bse = new SOCBuildingSpeedEstimate(player.getNumbers());
        roadETA = bse.calculateRollsFast(SOCGame.EMPTY_RESOURCES, SOCRoad.COST, 500, player.getPortFlags());

        roadsToGo = 500;
        longestRoadETA = 500;

        int longestRoadLength;
        SOCPlayer lrPlayer = game.getPlayerWithLongestRoad();

        if ((lrPlayer != null) && (lrPlayer.getPlayerNumber() == playerNumber))
        {
            ///
            /// we have longest road
            ///
            //D.ebugPrintln("===  we have longest road");
            longestRoadETA = 0;
            roadsToGo = 0;
        }
        else if (! game.isGameOptionSet(SOCGameOption.K_SC_0RVP))
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

            Iterator<SOCLRPathData> lrPathsIter = player.getLRPaths().iterator();
            int depth;

            while (lrPathsIter.hasNext())
            {
                SOCLRPathData pathData = lrPathsIter.next();
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
     *<P>
     * Do not call if {@link SOCGameOption#K_SC_0RVP} is set.
     *
     * @param startNode     the path endpoint, such as from
     *            {@link SOCPlayer#getLRPaths()}.(i){@link SOCLRPathData#getBeginning() .getBeginning()}
     *            or {@link SOCLRPathData#getEnd() .getEnd()}
     * @param pathLength    the length of that path
     * @param lrLength      length of longest road in the game
     * @param searchDepth   how many roads out to search
     *
     * @return the number of roads needed, or 500 if it can't be done
     */
    private int recalcLongestRoadETAAux
        (final int startNode, final int pathLength, final int lrLength, final int searchDepth)
    {
        // TODO handle ships here
        return ((Integer) SOCRobotDM.recalcLongestRoadETAAux
            (player, false, startNode, pathLength, lrLength, searchDepth)).intValue();
    }

    /**
     * calculate the largest army ETA
     */
    public void recalcLargestArmyETA()
    {
        int laSize = 0;
        SOCPlayer laPlayer = game.getPlayerWithLargestArmy();

        if (laPlayer == null)
        {
            ///
            /// no one has largest army
            ///
            laSize = 3;
        }
        else if (laPlayer.getPlayerNumber() == playerNumber)
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

        if ((player.getNumKnights() + player.getInventory().getAmount(SOCDevCardConstants.KNIGHT)) < laSize)  // OLD + NEW knights
        {
            knightsToBuy = laSize - (player.getNumKnights() + player.getInventory().getAmount(SOCInventory.OLD, SOCDevCardConstants.KNIGHT));
        }

        if (game.getNumDevCards() >= knightsToBuy)
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
     * update the longest road values for all possible roads/ships.
     *<P>
     * longest road value is how much this
     * road/ship would increase our longest road
     * if it were built.
     *<P>
     * the longest road potential is how much
     * this road/ship would increase our LR value
     * if other roads supported by this one were
     * built.
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
                SOCRoad dummyRoad;
                if (posRoad.isRoadNotShip()
                    || ((posRoad instanceof SOCPossibleShip) && ((SOCPossibleShip) posRoad).isCoastalRoadAndShip))
                    dummyRoad = new SOCRoad(dummy, posRoad.getCoordinates(), null);  // TODO better coastal handling
                else
                    dummyRoad = new SOCShip(dummy, posRoad.getCoordinates(), null);
                dummy.putPiece(dummyRoad, true);

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
                dummy.removePiece(dummyRoad, null);
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
     * update the potential LR value of a possible road or ship
     * by placing dummy roads/ships and calculating LR (longest road).
     * If <tt>level</tt> &gt; 0, add the new roads or ships adjacent
     * to <tt>dummy</tt> and recurse.
     *
     * @param posRoad   the possible road or ship
     * @param dummy     the dummy player
     * @param lrLength  the current LR length
     * @param level     how many levels of recursion, or 0 to not recurse
     */
    public void updateLRPotential
        (SOCPossibleRoad posRoad, SOCPlayer dummy, SOCRoad dummyRoad, final int lrLength, final int level)
    {
        //D.ebugPrintln("$$$ updateLRPotential for road at "+Integer.toHexString(posRoad.getCoordinates())+" level="+level);
        //
        // if we've reached the bottom level of recursion,
        // or if there are no more roads to place from this one.
        // then calc potential LR value
        //
        SOCBoard board = game.getBoard();
        boolean noMoreExpansion;

        if (level <= 0)
        {
            noMoreExpansion = true;
        }
        else
        {
            noMoreExpansion = false;

            Enumeration<Integer> adjEdgeEnum = board.getAdjacentEdgesToEdge(dummyRoad.getCoordinates()).elements();

            while (adjEdgeEnum.hasMoreElements())
            {
                final int adjEdge = adjEdgeEnum.nextElement().intValue();

                if ( (dummyRoad.isRoadNotShip() && dummy.isPotentialRoad(adjEdge))
                     || ((! dummyRoad.isRoadNotShip()) && dummy.isPotentialShip(adjEdge)) )
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
            // we need to add new roads/ships adjacent to dummyRoad, and recurse
            //
            Enumeration<Integer> adjEdgeEnum = board.getAdjacentEdgesToEdge(dummyRoad.getCoordinates()).elements();
            while (adjEdgeEnum.hasMoreElements())
            {
                final int adjEdge = adjEdgeEnum.nextElement().intValue();

                if ( (dummyRoad.isRoadNotShip() && dummy.isPotentialRoad(adjEdge))
                     || ((! dummyRoad.isRoadNotShip()) && dummy.isPotentialShip(adjEdge)) )
                {
                    SOCRoad newDummyRoad;
                    if (dummyRoad.isRoadNotShip())
                        newDummyRoad = new SOCRoad(dummy, adjEdge, board);
                    else
                        newDummyRoad = new SOCShip(dummy, adjEdge, board);
                    dummy.putPiece(newDummyRoad, true);
                    updateLRPotential(posRoad, dummy, newDummyRoad, lrLength, level - 1);
                    dummy.removePiece(newDummyRoad, null);
                }
            }
        }
    }

    /**
     * Get the calculated Winning the Game ETA (WGETA), based on
     * the most recent call to {@link #recalcWinGameETA()}.
     * @return the ETA for winning the game
     */
    public int getWinGameETA()
    {
        return winGameETA;
    }

    /**
     * Does this player need Longest Road to win?
     * Updated by {@link #recalcWinGameETA()}.
     * @return true if this player needs LR to win
     * @see #getLongestRoadETA()
     * @see #getRoadsToGo()
     */
    public boolean needsLR()
    {
        return needLR;
    }

    /**
     * Does this player need Largest Army to win?
     * Updated by {@link #recalcWinGameETA()}.
     * @return true if this player needs LA to win
     * @see #getLargestArmyETA()
     * @see #getKnightsToBuy()
     */
    public boolean needsLA()
    {
        return needLA;
    }

    /**
     * Recalculate the tracked player's ETA for winning the game (WGETA) by making and simulating with a copy
     * of our current potential settlement/city locations, building speed estimates (BSEs), and dice numbers,
     * looping from player's current {@link SOCPlayer#getTotalVP()} to {@link SOCGame#vp_winner}.
     *<P>
     * Calculates the fields for {@link #getWinGameETA()}, {@link #needsLA()}, {@link #needsLR()}.
     *<P>
     * Each time through the loop, given potential locations and available pieces, pick the fastest ETA
     * among each of these 2-VP combinations:
     *<UL>
     * <LI> 2 settlements (including necessary roads' ETA)
     * <LI> 2 cities
     * <LI> 1 city, 1 settlement (+ roads)
     * <LI> 1 settlement (+ roads), 1 city
     * <LI> Buy enough cards for Largest Army
     * <LI> Build enough roads for Longest Road
     *</UL>
     * The temporary potential sets, port trade flags, BSEs and dice numbers are updated with the picked pieces.
     * The loop body doesn't add new potential roads/ships or potential settlements to its copy of those sets,
     * or call {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, HashMap, int)}, so it may run out
     * of potential locations before {@code vp_winner} is reached.  If the loop doesn't have the locations or
     * pieces to do anything, 500 ETA and 2 VP are added to the totals to keep things moving.
     *<P>
     * If the loop reaches {@link SOCGame#vp_winner} - 1, it calculates ETAs for 1 city or settlement (+ roads)
     * instead of 2, and Largest Army and Longest Road, to make its choice.
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

            int[][] chosenSetBuildingSpeed = new int[2][SOCBuildingSpeedEstimate.MAXPLUSONE];
            int[][] chosenCityBuildingSpeed = new int[2][SOCBuildingSpeedEstimate.MAXPLUSONE];

            SOCBuildingSpeedEstimate tempBSE = new SOCBuildingSpeedEstimate();

            SOCBuildingSpeedEstimate ourBSE = new SOCBuildingSpeedEstimate(player.getNumbers());
            int[] ourBuildingSpeed = ourBSE.getEstimatesFromNothingFast(tempPortFlags);
            int cityETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CITY];
            int settlementETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.SETTLEMENT];
            int roadETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.ROAD];
            int cardETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CARD];
            // TODO shipETA, when ready

            int settlementPiecesLeft = player.getNumPieces(SOCPlayingPiece.SETTLEMENT);
            int cityPiecesLeft = player.getNumPieces(SOCPlayingPiece.CITY);
            int citySpotsLeft = possibleCities.size();

            boolean haveLA = false;
            boolean haveLR = false;

            int tempLargestArmyETA = largestArmyETA;
            int tempLongestRoadETA = longestRoadETA;

            SOCPlayer laPlayer = game.getPlayerWithLargestArmy();
            SOCPlayer lrPlayer = game.getPlayerWithLongestRoad();

            final SOCBoard board = game.getBoard();

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

            if ((laPlayer != null) && (playerNumber == laPlayer.getPlayerNumber()))
            {
                haveLA = true;
            }

            if ((lrPlayer != null) && (playerNumber == lrPlayer.getPlayerNumber()))
            {
                haveLR = true;
            }

            TreeMap<Integer, SOCPossibleSettlement> posSetsCopy =
                new TreeMap<Integer, SOCPossibleSettlement>(possibleSettlements);
            TreeMap<Integer, SOCPossibleCity> posCitiesCopy =
                new TreeMap<Integer, SOCPossibleCity>(possibleCities);

            int points = player.getTotalVP();
            int fastestETA;

            Queue<Pair<Integer, Vector<SOCPossibleRoad>>> necRoadQueue = new Queue<Pair<Integer, Vector<SOCPossibleRoad>>>();

            final int vp_winner = game.vp_winner;
            while (points < vp_winner)
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

                if (points == (vp_winner - 1))
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
                                necRoadQueue.put(new Pair<Integer, Vector<SOCPossibleRoad>>
                                    (Integer.valueOf(0), chosenSet.getNecessaryRoads()));

                                while (!necRoadQueue.empty())
                                {
                                    Pair<Integer, Vector<SOCPossibleRoad>> necRoadPair = necRoadQueue.get();
                                    Integer number = necRoadPair.getA();
                                    Vector<SOCPossibleRoad> necRoads = necRoadPair.getB();
                                    totalNecRoads = number.intValue();

                                    if (necRoads.isEmpty())
                                    {
                                        necRoadQueue.clear();
                                    }
                                    else
                                    {
                                        Enumeration<SOCPossibleRoad> necRoadEnum = necRoads.elements();

                                        while (necRoadEnum.hasMoreElements())
                                        {
                                            SOCPossibleRoad nr = necRoadEnum.nextElement();
                                            necRoadQueue.put(new Pair<Integer, Vector<SOCPossibleRoad>>
                                                (Integer.valueOf(totalNecRoads + 1), nr.getNecessaryRoads()));
                                        }

                                        if (necRoadQueue.size() > 100)
                                        {
                                            // Too many necessary, or dupes led to loop. Bug in necessary road construction?
                                            System.err.println
                                                ("PT.recalcWinGameETA L2997: Necessary Road Path too long for settle at 0x"
                                                 + Integer.toHexString(chosenSet.getCoordinates()));
                                            totalNecRoads = 100;
                                            break;
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
                            brain.getDRecorder().record(fastestETA + ": Stlmt at "
                                + board.nodeCoordToString(chosenSet.getCoordinates()));
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

                        Iterator<SOCPossibleCity> posCities0Iter = posCitiesCopy.values().iterator();

                        while (posCities0Iter.hasNext())
                        {
                            SOCPossibleCity posCity0 = posCities0Iter.next();

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
                                        chosenSetBuildingSpeed[i] =
                                            tempSetBSE[i].getEstimatesFromNothingFast(tempPortFlagsSet[i]);

                                        for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                                buildingType++)
                                        {
                                            if ((ourBuildingSpeed[buildingType] - chosenSetBuildingSpeed[i][buildingType]) > 0)
                                            {
                                                bestSpeedupTotal +=
                                                    (ourBuildingSpeed[buildingType] - chosenSetBuildingSpeed[i][buildingType]);
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
                                                tempSpeedupTotal +=
                                                    (ourBuildingSpeed[buildingType] - tempBuildingSpeed[buildingType]);
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
                                    necRoadQueue.put(new Pair<Integer, Vector<SOCPossibleRoad>>
                                        (Integer.valueOf(0), chosenSet[i].getNecessaryRoads()));

                                    while (!necRoadQueue.empty())
                                    {
                                        Pair<Integer, Vector<SOCPossibleRoad>> necRoadPair = necRoadQueue.get();
                                        Integer number = necRoadPair.getA();
                                        Vector<SOCPossibleRoad> necRoads = necRoadPair.getB();
                                        totalNecRoads = number.intValue();

                                        if (necRoads.isEmpty())
                                        {
                                            necRoadQueue.clear();
                                        }
                                        else
                                        {
                                            Enumeration<SOCPossibleRoad> necRoadEnum = necRoads.elements();

                                            while (necRoadEnum.hasMoreElements())
                                            {
                                                SOCPossibleRoad nr = necRoadEnum.nextElement();
                                                necRoadQueue.put(new Pair<Integer, Vector<SOCPossibleRoad>>
                                                    (Integer.valueOf(totalNecRoads + 1), nr.getNecessaryRoads()));
                                            }

                                            if (necRoadQueue.size() > 100)
                                            {
                                                // Too many necessary, or dupes led to loop. Bug in necessary road construction?
                                                System.err.println
                                                    ("PT.recalcWinGameETA L3326: Necessary Road Path too long for settle at 0x"
                                                     + Integer.toHexString(chosenSet[i].getCoordinates()));
                                                totalNecRoads = 100;
                                                break;
                                            }
                                        }
                                    }
                                }

                                D.ebugPrintln("WWW # necesesary roads = " + totalNecRoads);
                                D.ebugPrintln("WWW this settlement eta = " + (settlementETA + (totalNecRoads * roadETA)));

                                if ((i == 0) && (chosenSet[0] != null))
                                {
                                    posSetsCopy.remove(new Integer(chosenSet[0].getCoordinates()));

                                    Enumeration<SOCPossibleSettlement> conflicts = chosenSet[0].getConflicts().elements();

                                    while (conflicts.hasMoreElements())
                                    {
                                        SOCPossibleSettlement conflict = conflicts.nextElement();
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
                            D.ebugPrintln("WWW 2 * settlement = " + twoSettlements);
                            fastestETA = twoSettlements;
                        }
                    }

                    ///
                    /// one of each
                    ///
                    if ((cityPiecesLeft > 0)
                        && (   ((settlementPiecesLeft > 0) && (citySpotsLeft >= 0))
                            || ((settlementPiecesLeft >= 0) && (citySpotsLeft > 0))  )
                        && ! posSetsCopy.isEmpty())
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
                            Iterator<SOCPossibleSettlement> posSetsIter = posSetsCopy.values().iterator();

                            while (posSetsIter.hasNext())
                            {
                                SOCPossibleSettlement posSet = posSetsIter.next();
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
                                            bestSpeedupTotal +=
                                                (ourBuildingSpeed[buildingType] - chosenSetBuildingSpeed[0][buildingType]);
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
                                            tempSpeedupTotal +=
                                                (ourBuildingSpeed[buildingType] - tempBuildingSpeed[buildingType]);
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
                            necRoadQueue.put(new Pair<Integer, Vector<SOCPossibleRoad>>
                                (Integer.valueOf(0), chosenSet[0].getNecessaryRoads()));

                            while (!necRoadQueue.empty())
                            {
                                Pair<Integer, Vector<SOCPossibleRoad>> necRoadPair = necRoadQueue.get();
                                Integer number = necRoadPair.getA();
                                Vector<SOCPossibleRoad> necRoads = necRoadPair.getB();
                                totalNecRoads = number.intValue();

                                if (necRoads.isEmpty())
                                {
                                    necRoadQueue.clear();
                                }
                                else
                                {
                                    Enumeration<SOCPossibleRoad> necRoadEnum = necRoads.elements();

                                    while (necRoadEnum.hasMoreElements())
                                    {
                                        SOCPossibleRoad nr = necRoadEnum.nextElement();
                                        necRoadQueue.put(new Pair<Integer, Vector<SOCPossibleRoad>>
                                            (Integer.valueOf(totalNecRoads + 1), nr.getNecessaryRoads()));
                                    }

                                    if (necRoadQueue.size() > 100)
                                    {
                                        // Too many necessary. Bug in necessary road construction?
                                        System.err.println
                                            ("PT.recalcWinGameETA L3631: Necessary Road Path too long for settle at 0x"
                                             + Integer.toHexString(chosenSet[0].getCoordinates()));
                                        totalNecRoads = 100;
                                        break;
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
                        else if (laPlayer.getPlayerNumber() == playerNumber)
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

                        if ((player.getNumKnights()
                            + player.getInventory().getAmount(SOCDevCardConstants.KNIGHT)) < laSize)  // OLD + NEW knights
                        {
                            knightsToBuy = laSize -
                                (player.getNumKnights() + player.getInventory().getAmount(SOCInventory.OLD, SOCDevCardConstants.KNIGHT));
                        }

                        ///
                        /// figure out how long it takes to buy this many knights
                        ///
                        if (game.getNumDevCards() >= knightsToBuy)
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
                    D.ebugPrintln("WWW WGETA SO FAR FOR PLAYER " + playerNumber + " = " + winGameETA);

                    if ((settlementPiecesLeft > 1) && (posSetsCopy.size() > 1)
                        && canBuild2Settlements && (fastestETA == twoSettlements))
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
                        Enumeration<SOCPossibleSettlement> conflicts = chosenSet[0].getConflicts().elements();

                        while (conflicts.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = conflicts.nextElement();
                            Integer conflictInt = new Integer(conflict.getCoordinates());
                            posSetsCopy.remove(conflictInt);
                        }

                        conflicts = chosenSet[1].getConflicts().elements();

                        while (conflicts.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = conflicts.nextElement();
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
                            brain.getDRecorder().record
                                (fastestETA + ": Stlmt at " + board.nodeCoordToString(chosenSet[0].getCoordinates())
                                 + "; Stlmt at " + board.nodeCoordToString(chosenSet[1].getCoordinates()));
                        }
                    }
                    else if ((  (cityPiecesLeft > 0)
                               && (   ((settlementPiecesLeft > 0) && (citySpotsLeft >= 0))
                                   || ((settlementPiecesLeft >= 0) && (citySpotsLeft > 0))  )
                               && ! posSetsCopy.isEmpty()  )
                             && (fastestETA == oneOfEach))
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
                        Enumeration<SOCPossibleSettlement> conflicts = chosenSet[0].getConflicts().elements();

                        while (conflicts.hasMoreElements())
                        {
                            SOCPossibleSettlement conflict = conflicts.nextElement();
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
                                brain.getDRecorder().record
                                    (fastestETA + ": Stlmt at " + board.nodeCoordToString(chosenSet[0].getCoordinates())
                                     + "; City at " + board.nodeCoordToString(chosenCity[0].getCoordinates()));
                            }
                            else
                            {
                                brain.getDRecorder().record
                                    (fastestETA + ": City at " + board.nodeCoordToString(chosenCity[0].getCoordinates())
                                     + "; Stlmt at " + board.nodeCoordToString(chosenSet[0].getCoordinates()));
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
                        Iterator<SOCPossibleCity> posCities1Iter = posCitiesCopy.values().iterator();

                        while (posCities1Iter.hasNext())
                        {
                            SOCPossibleCity posCity1 = posCities1Iter.next();
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
                            D.ebugPrintln("WWW City at " + board.nodeCoordToString(posCity1.getCoordinates())
                                + " has tempSpeedupTotal = " + tempSpeedupTotal);

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
                            brain.getDRecorder().record
                                (fastestETA + ": City at " + board.nodeCoordToString(chosenCity[0].getCoordinates())
                                 + "; City at " + board.nodeCoordToString(chosenCity[1].getCoordinates()));
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

            D.ebugPrintln("WWW TOTAL WGETA FOR PLAYER " + playerNumber + " = " + winGameETA);

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
     * See how building a piece impacts the game.
     * Calls {@link SOCGame#putTempPiece(SOCPlayingPiece)} and {@link SOCPlayerTracker#copyPlayerTrackers(HashMap)},
     * then adds <tt>piece</tt> to the tracker copies.
     *
     * @param piece      the piece to build
     * @param game       the game
     * @param trackers   the player trackers
     *
     * @return a copy of the player trackers with the new piece in place
     * @see #tryPutPieceNoCopy(SOCPlayingPiece, SOCGame, HashMap)
     */
    public static HashMap<Integer, SOCPlayerTracker> tryPutPiece
        (SOCPlayingPiece piece, SOCGame game, HashMap<Integer, SOCPlayerTracker> trackers)
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
                case SOCPlayingPiece.SHIP:  // fall through to ROAD
                case SOCPlayingPiece.ROAD:
                    trackerCopy.addNewRoadOrShip((SOCRoad) piece, trackersCopy);

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
     * Same as {@link #tryPutPiece(SOCPlayingPiece, SOCGame, HashMap) tryPutPiece},
     * but we don't make a copy of the player trackers. Instead caller supplies the copy.
     *
     * @param piece      the piece to build
     * @param game       the game
     * @param trackers   the already-copied player trackers
     */
    public static void tryPutPieceNoCopy
        (SOCPlayingPiece piece, SOCGame game, HashMap<Integer, SOCPlayerTracker> trackers)
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
                case SOCPlayingPiece.SHIP:  // fall through to ROAD
                case SOCPlayingPiece.ROAD:
                    tracker.addNewRoadOrShip((SOCRoad) piece, trackers);

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
     * Print debug output for a set of player trackers.
     *<P>
     * Calls <tt>D.ebugPrintln</tt>; no output will appear if this class
     * imports <tt>soc.disableDebug.D</tt> instead of <tt>soc.debug.D</tt>.
     *
     * @param playerTrackers  the player trackers
     */
    public static void playerTrackersDebug(HashMap<Integer, SOCPlayerTracker> playerTrackers)
    {
        if (D.ebugOn)
        {
            Iterator<SOCPlayerTracker> trackersIter = playerTrackers.values().iterator();

            while (trackersIter.hasNext())
            {
                SOCPlayerTracker tracker = trackersIter.next();
                D.ebugPrintln("%%%%%%%%% TRACKER FOR PLAYER " + tracker.getPlayer().getPlayerNumber());
                D.ebugPrintln("   LONGEST ROAD ETA = " + tracker.getLongestRoadETA());
                D.ebugPrintln("   LARGEST ARMY ETA = " + tracker.getLargestArmyETA());

                Iterator<SOCPossibleRoad> prIter = tracker.getPossibleRoads().values().iterator();

                while (prIter.hasNext())
                {
                    SOCPossibleRoad pr = prIter.next();
                    if (pr.isRoadNotShip())
                        D.ebugPrint("%%% possible road at ");
                    else
                        D.ebugPrint("%%% possible ship at ");
                    D.ebugPrintln(Integer.toHexString(pr.getCoordinates()));
                    D.ebugPrint("   eta:" + pr.getETA());
                    D.ebugPrint("   this road/ship needs:");

                    Enumeration<SOCPossibleRoad> nrEnum = pr.getNecessaryRoads().elements();

                    while (nrEnum.hasMoreElements())
                    {
                        D.ebugPrint(" " + Integer.toHexString(nrEnum.nextElement().getCoordinates()));
                    }

                    D.ebugPrintln();
                    D.ebugPrint("   this road/ship supports:");

                    Enumeration<SOCPossiblePiece> newPosEnum = pr.getNewPossibilities().elements();

                    while (newPosEnum.hasMoreElements())
                    {
                        D.ebugPrint(" " + Integer.toHexString(newPosEnum.nextElement().getCoordinates()));
                    }

                    D.ebugPrintln();
                    D.ebugPrint("   threats:");

                    Enumeration<SOCPossiblePiece> threatEnum = pr.getThreats().elements();

                    while (threatEnum.hasMoreElements())
                    {
                        SOCPossiblePiece threat = threatEnum.nextElement();
                        D.ebugPrint(" " + threat.getPlayer().getPlayerNumber() + ":" + threat.getType() + ":" + Integer.toHexString(threat.getCoordinates()));
                    }

                    D.ebugPrintln();
                    D.ebugPrintln("   LR value=" + pr.getLRValue() + " LR Potential=" + pr.getLRPotential());
                }

                Iterator<SOCPossibleSettlement> psIter = tracker.getPossibleSettlements().values().iterator();

                while (psIter.hasNext())
                {
                    SOCPossibleSettlement ps = psIter.next();
                    D.ebugPrintln("%%% possible settlement at " + Integer.toHexString(ps.getCoordinates()));
                    D.ebugPrint("   eta:" + ps.getETA());
                    D.ebugPrint("%%%   conflicts");

                    Enumeration<SOCPossibleSettlement> conflictEnum = ps.getConflicts().elements();

                    while (conflictEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement conflict = conflictEnum.nextElement();
                        D.ebugPrint(" " + conflict.getPlayer().getPlayerNumber() + ":" + Integer.toHexString(conflict.getCoordinates()));
                    }

                    D.ebugPrintln();
                    D.ebugPrint("%%%   necessary roads/ships");

                    Enumeration<SOCPossibleRoad> nrEnum = ps.getNecessaryRoads().elements();

                    while (nrEnum.hasMoreElements())
                    {
                        SOCPossibleRoad nr = nrEnum.nextElement();
                        D.ebugPrint(" " + Integer.toHexString(nr.getCoordinates()));
                    }

                    D.ebugPrintln();
                    D.ebugPrint("   threats:");

                    Enumeration<SOCPossiblePiece> threatEnum = ps.getThreats().elements();

                    while (threatEnum.hasMoreElements())
                    {
                        SOCPossiblePiece threat = threatEnum.nextElement();
                        D.ebugPrint(" " + threat.getPlayer().getPlayerNumber() + ":" + threat.getType() + ":" + Integer.toHexString(threat.getCoordinates()));
                    }

                    D.ebugPrintln();
                }

                Iterator<SOCPossibleCity> pcIter = tracker.getPossibleCities().values().iterator();

                while (pcIter.hasNext())
                {
                    SOCPossibleCity pc = pcIter.next();
                    D.ebugPrintln("%%% possible city at " + Integer.toHexString(pc.getCoordinates()));
                    D.ebugPrintln("   eta:" + pc.getETA());
                }
            }
        }
    }

    /**
     * Update winGameETAs for player trackers.
     * For each tracker, call {@link #recalcLongestRoadETA()},
     * {@link #recalcLargestArmyETA()}, {@link #recalcWinGameETA()}.
     *
     * @param playerTrackers  the player trackers
     */
    public static void updateWinGameETAs(HashMap<Integer, SOCPlayerTracker> playerTrackers)
    {
        Iterator<SOCPlayerTracker> playerTrackersIter = playerTrackers.values().iterator();

        while (playerTrackersIter.hasNext())
        {
            SOCPlayerTracker tracker = playerTrackersIter.next();

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

    /**
     * SOCPlayerTracker key fields (brain player name, tracked player name) to aid debugging.
     * Since PTs are copied a lot and we need a way to tell the copies apart, also includes
     * hex {@code super.}{@link Object#hashCode() hashCode()}.
     * @return This SOCPlayerTracker's fields, in the format:
     *     <tt>SOCPlayerTracker@<em>hashCode</em>[<em>brainPlayerName</em>, pl=<em>trackedPlayerName</em>]</tt>
     * @since 1.1.20
     */
    @Override
    public String toString()
    {
        return "SOCPlayerTracker@" + Integer.toHexString(super.hashCode())
            + "[" + brain.getOurPlayerData().getName() + ", pl=" + player.getName() + "]";
    }

}
