/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2023 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
 * Portions of this file Copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
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
import soc.game.SOCGameOptionSet;
import soc.game.SOCInventory;
import soc.game.SOCLRPathData;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.game.SOCRoutePiece;
import soc.game.SOCSettlement;
import soc.game.SOCShip;

import soc.util.Pair;
import soc.util.Queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.TreeMap;


/**
 * This class is used by the SOCRobotBrain to track
 * per-player strategic planning information such as
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
     * Road expansion level for {@link #addOurNewRoadOrShip(SOCRoutePiece, SOCPlayerTracker[], int)};
     * how far away to look for possible future settlements
     * (level of recursion).
     */
    static protected int EXPAND_LEVEL = 1;

    /**
     * Ship route length expansion level to add to {@link #EXPAND_LEVEL} for
     * {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, SOCPlayerTracker[], int)}.
     * @since 2.0.00
     */
    static protected int EXPAND_LEVEL_SHIP_EXTRA = 2;

    /**
     * Road expansion level for {@link #updateLRPotential(SOCPossibleRoad, SOCPlayer, SOCRoutePiece, int, int)};
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
     * Expanded in {@link #addOurNewRoadOrShip(SOCRoutePiece, SOCPlayerTracker[], int)}
     * via {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, SOCPlayerTracker[], int)}.
     * Also updated in {@link #addNewSettlement(SOCSettlement, SOCPlayerTracker[])},
     * {@link #cancelWrongSettlement(SOCSettlement)}, a few other places.
     */
    protected TreeMap<Integer, SOCPossibleSettlement> possibleSettlements;

    /**
     * Includes both roads and ships.
     * Key = {@link Integer} edge coordinate, value = {@link SOCPossibleRoad} or {@link SOCPossibleShip}
     * Expanded in {@link #addOurNewRoadOrShip(SOCRoutePiece, SOCPlayerTracker[], int)}
     * via {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, SOCPlayerTracker[], int)}.
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
     * @param pl  the player being tracked; not null
     * @param br  the robot brain using this tracker; not null
     */
    public SOCPlayerTracker(SOCPlayer pl, SOCRobotBrain br)
        throws IllegalArgumentException
    {
        if ((pl == null) || (br == null))
            throw new IllegalArgumentException("null pl or br");

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
            possibleRoads.put(Integer.valueOf(posRoadCopy.getCoordinates()), posRoadCopy);
        }

        for (SOCPossibleSettlement posSettlement : pt.getPossibleSettlements().values())
        {
            SOCPossibleSettlement posSettlementCopy = new SOCPossibleSettlement(posSettlement);
            possibleSettlements.put(Integer.valueOf(posSettlementCopy.getCoordinates()), posSettlementCopy);
        }

        for (SOCPossibleCity posCity : pt.getPossibleCities().values())
        {
            SOCPossibleCity posCityCopy = new SOCPossibleCity(posCity);
            possibleCities.put(Integer.valueOf(posCityCopy.getCoordinates()), posCityCopy);
        }
    }

    /**
     * Recalculate all ETAs: Calls {@link #recalcLargestArmyETA()},
     * {@link #recalcLongestRoadETA()}, {@link #recalcWinGameETA()}.
     * @since 2.5.00
     */
    public void recalculateAllETAs()
    {
        recalcLargestArmyETA();
        recalcLongestRoadETA();
        recalcWinGameETA();
    }

    /**
     * make copies of player trackers and then
     * make connections between copied pieces
     *<P>
     * Note: not copying threats
     *
     * param trackers  player trackers for each player
     */
    public static SOCPlayerTracker[] copyPlayerTrackers(final SOCPlayerTracker[] trackers)
    {
        final SOCPlayerTracker[] trackersCopy
            = new SOCPlayerTracker[trackers.length];  // length == SOCGame.maxPlayers

        //
        // copy the trackers but not the connections between the pieces
        //
        for (SOCPlayerTracker pt : trackers)
        {
            if (pt != null)
                trackersCopy[pt.getPlayer().getPlayerNumber()] = new SOCPlayerTracker(pt);
        }

        //
        // now make the connections between the pieces
        //
        //D.ebugPrintln(">>>>> Making connections between pieces");

        for (int tpn = 0; tpn < trackers.length; ++tpn)
        {
            final SOCPlayerTracker tracker = trackers[tpn];
            if (tracker == null)
                continue;
            final SOCPlayerTracker trackerCopy = trackersCopy[tracker.getPlayer().getPlayerNumber()];

            //D.ebugPrintln(">>>> Player num for tracker is "+tracker.getPlayer().getPlayerNumber());
            //D.ebugPrintln(">>>> Player num for trackerCopy is "+trackerCopy.getPlayer().getPlayerNumber());
            TreeMap<Integer, SOCPossibleRoad> possibleRoads = tracker.getPossibleRoads();
            TreeMap<Integer, SOCPossibleRoad> possibleRoadsCopy = trackerCopy.getPossibleRoads();
            TreeMap<Integer, SOCPossibleSettlement> possibleSettlements = tracker.getPossibleSettlements();
            TreeMap<Integer, SOCPossibleSettlement> possibleSettlementsCopy = trackerCopy.getPossibleSettlements();

            for (SOCPossibleRoad posRoad : possibleRoads.values())
            {
                SOCPossibleRoad posRoadCopy = possibleRoadsCopy.get(Integer.valueOf(posRoad.getCoordinates()));

                //D.ebugPrintln(">>> posRoad     : "+posRoad);
                //D.ebugPrintln(">>> posRoadCopy : "+posRoadCopy);

                for (SOCPossibleRoad necRoad : posRoad.getNecessaryRoads())
                {
                    //D.ebugPrintln(">> posRoad.necRoad : "+necRoad);
                    //
                    // now find the copy of this necessary road and
                    // add it to the pos road copy's nec road list
                    //
                    SOCPossibleRoad necRoadCopy = possibleRoadsCopy.get(Integer.valueOf(necRoad.getCoordinates()));

                    if (necRoadCopy != null)
                    {
                        posRoadCopy.addNecessaryRoad(necRoadCopy);
                    }
                    else
                    {
                        D.ebugPrintlnINFO("*** ERROR in copyPlayerTrackers : necRoadCopy == null");
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

                        SOCPossibleRoad newPosRoadCopy = possibleRoadsCopy.get(Integer.valueOf(newPos.getCoordinates()));

                        if (newPosRoadCopy != null)
                        {
                            posRoadCopy.addNewPossibility(newPosRoadCopy);
                        }
                        else
                        {
                            D.ebugPrintlnINFO("*** ERROR in copyPlayerTrackers : newPosRoadCopy == null");
                        }

                        break;

                    case SOCPossiblePiece.SETTLEMENT:

                        SOCPossibleSettlement newPosSettlementCopy = possibleSettlementsCopy.get
                            (Integer.valueOf(newPos.getCoordinates()));

                        if (newPosSettlementCopy != null)
                        {
                            posRoadCopy.addNewPossibility(newPosSettlementCopy);
                        }
                        else
                        {
                            D.ebugPrintlnINFO("*** ERROR in copyPlayerTrackers : newPosSettlementCopy == null");
                        }

                        break;
                    }
                }
            }


            for (SOCPossibleSettlement posSet : possibleSettlements.values())
            {
                SOCPossibleSettlement posSetCopy
                    = possibleSettlementsCopy.get(Integer.valueOf(posSet.getCoordinates()));

                //D.ebugPrintln(">>> posSet     : "+posSet);
                //D.ebugPrintln(">>> posSetCopy : "+posSetCopy);

                for (SOCPossibleRoad necRoad : posSet.getNecessaryRoads())
                {
                    //D.ebugPrintln(">> posSet.necRoad : "+necRoad);
                    //
                    // now find the copy of this necessary road and
                    // add it to the pos settlement copy's nec road list
                    //
                    SOCPossibleRoad necRoadCopy
                        = possibleRoadsCopy.get(Integer.valueOf(necRoad.getCoordinates()));

                    if (necRoadCopy != null)
                    {
                        posSetCopy.addNecessaryRoad(necRoadCopy);
                    }
                    else
                    {
                        D.ebugPrintlnINFO("*** ERROR in copyPlayerTrackers : necRoadCopy == null");
                    }
                }


                for (SOCPossibleSettlement conflict : posSet.getConflicts())
                {
                    //D.ebugPrintln(">> posSet.conflict : "+conflict);
                    //
                    // now find the copy of this conflict and
                    // add it to the conflict list in the pos settlement copy
                    //
                    SOCPlayerTracker trackerCopy2 = trackersCopy[conflict.getPlayer().getPlayerNumber()];

                    if (trackerCopy2 == null)
                    {
                        D.ebugPrintlnINFO("*** ERROR in copyPlayerTrackers : trackerCopy2 == null");
                    }
                    else
                    {
                        SOCPossibleSettlement conflictCopy = trackerCopy2.getPossibleSettlements().get
                            (Integer.valueOf(conflict.getCoordinates()));

                        if (conflictCopy == null)
                        {
                            D.ebugPrintlnINFO("*** ERROR in copyPlayerTrackers : conflictCopy == null");
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
     * Updated in {@link #updateWinGameETAs(SOCPlayerTracker[])} or {@link #recalcLongestRoadETA()}.
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
     * Updated in {@link #updateWinGameETAs(SOCPlayerTracker[])} and {@link #recalcLongestRoadETA()}.
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
     * Updated in {@link #updateWinGameETAs(SOCPlayerTracker[])} and {@link #recalcLargestArmyETA()}.
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
     * Updated in {@link #updateWinGameETAs(SOCPlayerTracker[])} and {@link #recalcLargestArmyETA()}.
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
     * @since 1.1.00
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
     * @since 1.1.00
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
    public void addNewRoadOrShip(SOCRoutePiece road, SOCPlayerTracker[] trackers)
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
     *<P>
     * Before v2.0.00 this method was {@code cancelWrongRoad}.
     *
     * @param rs  Location of our bad road or ship
     *
     * @see SOCRobotBrain#cancelWrongPiecePlacement(soc.message.SOCCancelBuildRequest)
     * @since 1.1.00
     */
    public void cancelWrongRoadOrShip(SOCRoutePiece rs)
    {
        addTheirNewRoadOrShip(rs, true);

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
            if (pr.getCoordinates() == rs.getCoordinates())
            {
                //
                // if so, remove it
                //
                //D.ebugPrintln("$$$ removing (wrong) "+Integer.toHexString(road.getCoordinates()));
                possibleRoads.remove(Integer.valueOf(pr.getCoordinates()));
                removeFromNecessaryRoads(pr);

                break;
            }
        }
    }

    /**
     * Add one of our roads or ships that has just been built.
     * Look for new adjacent possible settlements.
     * Calls {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, SOCPlayerTracker[], int)}
     * on newly possible adjacent roads or ships.
     *<P>
     * Before v2.0.00 this method was {@code addOurNewRoad}.
     *
     * @param rs           the road or ship
     * @param trackers     player trackers for the players
     * @param expandLevel  how far out we should expand roads/ships;
     *            passed to {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, SOCPlayerTracker[], int)}
     */
    private void addOurNewRoadOrShip
        (final SOCRoutePiece rs, final SOCPlayerTracker[] trackers, final int expandLevel)
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

            if (pr.getCoordinates() == rs.getCoordinates())
            {
                //
                // if so, remove it
                //
                //D.ebugPrintln("$$$ removing "+Integer.toHexString(road.getCoordinates()));
                possibleRoads.remove(Integer.valueOf(pr.getCoordinates()));
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
        Collection<Integer> adjNodeEnum = board.getAdjacentNodesToEdge(rs.getCoordinates());
        final SOCBuildingSpeedEstimateFactory bsef = brain.getEstimatorFactory();

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
                    posSet.getNecessaryRoads().clear();
                    posSet.setNumberOfNecessaryRoads(0);
                }
                else
                {
                    //
                    // else, add new possible settlement
                    //
                    //D.ebugPrintln("$$$ adding new possible settlement at "+Integer.toHexString(adjNode.intValue()));
                    SOCPossibleSettlement newPosSet = new SOCPossibleSettlement(player, adjNode.intValue(), null, bsef);
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
        ArrayList<SOCPossibleRoad> newPossibleRoads = new ArrayList<SOCPossibleRoad>();
        ArrayList<SOCPossibleRoad> roadsToExpand = new ArrayList<SOCPossibleRoad>();

        //
        // check adjacent edges to road
        //
        for (Integer adjEdge : board.getAdjacentEdgesToEdge(rs.getCoordinates()))
        {
            final int edge = adjEdge.intValue();

            //D.ebugPrintln("$$$ edge "+Integer.toHexString(adjEdge.intValue())+" is legal:"+player.isPotentialRoad(adjEdge.intValue()));
            //
            // see if edge is a potential road
            // or ship to continue this route
            //
            boolean edgeIsPotentialRoute =
                (rs.isRoadNotShip())
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
                final int nodeBetween = ((SOCBoardLarge) board).getNodeBetweenAdjacentEdges(rs.getCoordinates(), edge);
                if (player.canPlaceSettlement(nodeBetween))
                {
                    // check opposite type at transition
                    edgeIsPotentialRoute = (rs.isRoadNotShip())
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
                    if (edgeRequiresCoastalSettlement && (pr.isRoadNotShip() != rs.isRoadNotShip()))
                    {
                        continue;  // <--- road vs ship mismatch ---
                    }

                    //
                    // if so, clear necessary road list and remove from np lists
                    //
                    //D.ebugPrintln("$$$ pr "+Integer.toHexString(pr.getCoordinates())+" already in list");
                    if (! pr.getNecessaryRoads().isEmpty())
                    {
                        //D.ebugPrintln("$$$    clearing nr list");
                        removeFromNecessaryRoads(pr);
                        pr.getNecessaryRoads().clear();
                        pr.setNumberOfNecessaryRoads(0);
                    }

                    roadsToExpand.add(pr);
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
                    boolean isRoad = rs.isRoadNotShip();
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
                        // System.err.println
                        //     ("L793: " + toString() + ": new PossibleShip(" + isCoastal + ") at 0x" + Integer.toHexString(edge));
                    }
                    newPR.setNumberOfNecessaryRoads(roadsBetween);  // 0 unless requires settlement
                    newPossibleRoads.add(newPR);
                    roadsToExpand.add(newPR);
                    newPR.setExpandedFlag();
                }
            }
        }

        //
        // add the new roads to our list of possible roads
        //
        for (SOCPossibleRoad newPR : newPossibleRoads)
        {
            possibleRoads.put(Integer.valueOf(newPR.getCoordinates()), newPR);
        }

        //
        // expand possible roads that we've touched or added
        //
        SOCPlayer dummy = new SOCPlayer(player, "dummy");
        for (SOCPossibleRoad expandPR : roadsToExpand)
        {
            expandRoadOrShip(expandPR, player, dummy, trackers, expandLevel);
        }

        dummy.destroyPlayer();

        //
        // in scenario _SC_PIRI, update the closest ship to our fortress
        //
        if ((rs instanceof SOCShip) && game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
            updateScenario_SC_PIRI_closestShipToFortress((SOCShip) rs, true);
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
     *<P>
     * Before v2.0.00 this method was {@code expandRoad}.
     *
     * @param targetRoad   the possible road
     * @param pl        the player who owns the original road
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
        (final SOCPossibleRoad targetRoad, final SOCPlayer pl, final SOCPlayer dummy,
         final SOCPlayerTracker[] trackers, final int level)
    {
        //D.ebugPrintln("$$$ expandRoad at "+Integer.toHexString(targetRoad.getCoordinates())+" level="+level);

        final SOCBoard board = game.getBoard();
        final int tgtRoadEdge = targetRoad.getCoordinates();
        final boolean isRoadNotShip = targetRoad.isRoadNotShip();
        final SOCRoutePiece dummyRS;
        if (isRoadNotShip
            || ((targetRoad instanceof SOCPossibleShip) && ((SOCPossibleShip) targetRoad).isCoastalRoadAndShip))
            dummyRS = new SOCRoad(dummy, tgtRoadEdge, board);
            // TODO better handling for coastal roads/ships
        else
            dummyRS = new SOCShip(dummy, tgtRoadEdge, board);

        dummy.putPiece(dummyRS, true);

        //
        // see if this road/ship adds any new possible settlements
        // (check road's adjacent nodes)
        //
        //D.ebugPrintln("$$$ checking for possible settlements");
        //
        final SOCBuildingSpeedEstimateFactory bsef = brain.getEstimatorFactory();
        for (Integer adjNode : board.getAdjacentNodesToEdge(tgtRoadEdge))
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
                    if (! (posSet.getNecessaryRoads().isEmpty() || posSet.getNecessaryRoads().contains(targetRoad)))
                    {
                        //
                        // add target road to settlement's nr list and this settlement to the road's np list
                        //
                        //D.ebugPrintln("$$$ adding road "+Integer.toHexString(targetRoad.getCoordinates())+" to the settlement "+Integer.toHexString(posSet.getCoordinates()));
                        posSet.addNecessaryRoad(targetRoad);
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
                    List<SOCPossibleRoad> nr = new ArrayList<SOCPossibleRoad>();
                    nr.add(targetRoad);

                    SOCPossibleSettlement newPosSet = new SOCPossibleSettlement(pl, adjNode.intValue(), nr, bsef);
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
            ArrayList<SOCPossibleRoad> newPossibleRoads = new ArrayList<SOCPossibleRoad>();
            ArrayList<SOCPossibleRoad> roadsToExpand = new ArrayList<SOCPossibleRoad>();

            // ships in _SC_PIRI never expand east
            final boolean isShipInSC_PIRI = (! isRoadNotShip) && game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI);

            //D.ebugPrintln("$$$ checking roads adjacent to "+Integer.toHexString(targetRoad.getCoordinates()));
            //
            // check adjacent edges to road or ship
            //
            for (final Integer adjEdgeInt : board.getAdjacentEdgesToEdge(tgtRoadEdge))
            {
                final int edge = adjEdgeInt.intValue();

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
                    SOCPossibleRoad pr = possibleRoads.get(adjEdgeInt);

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
                        List<SOCPossibleRoad> nr = pr.getNecessaryRoads();

                        if (! (nr.isEmpty() || nr.contains(targetRoad)))
                        {
                            //
                            // add the target road to its nr list and the new road to the target road's np list
                            //
                            //D.ebugPrintln("$$$    adding "+Integer.toHexString(targetRoad.getCoordinates())+" to nr list");
                            nr.add(targetRoad);
                            targetRoad.addNewPossibility(pr);

                            //
                            // update this road's numberOfNecessaryRoads if the target road reduces it
                            //
                            if ((targetRoad.getNumberOfNecessaryRoads() + incrDistance) < pr.getNumberOfNecessaryRoads())
                            {
                                pr.setNumberOfNecessaryRoads(targetRoad.getNumberOfNecessaryRoads() + incrDistance);
                            }
                        }

                        if (! pr.hasBeenExpanded())
                        {
                            roadsToExpand.add(pr);
                            pr.setExpandedFlag();
                        }
                    }
                    else
                    {
                        //
                        // else, add new possible road or ship
                        //
                        //D.ebugPrintln("$$$ adding new pr at "+Integer.toHexString(adjEdge.intValue()));
                        ArrayList<SOCPossibleRoad> neededRoads = new ArrayList<SOCPossibleRoad>();
                        neededRoads.add(targetRoad);

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
                            newPR = new SOCPossibleRoad(pl, edge, neededRoads);
                        } else {
                            newPR = new SOCPossibleShip(pl, edge, isCoastal, neededRoads);
                            // System.err.println
                            //     ("L1072: " + toString() + ": new PossibleShip(" + isCoastal + ") at 0x" + Integer.toHexString(edge));
                        }
                        newPR.setNumberOfNecessaryRoads(targetRoad.getNumberOfNecessaryRoads() + incrDistance);
                        targetRoad.addNewPossibility(newPR);
                        newPossibleRoads.add(newPR);
                        roadsToExpand.add(newPR);
                        newPR.setExpandedFlag();
                    }
                }
            }

            //
            // add the new roads to our list of possible roads
            //
            for (SOCPossibleRoad newPR : newPossibleRoads)
            {
                possibleRoads.put(Integer.valueOf(newPR.getCoordinates()), newPR);
            }

            //
            // expand roads that we've touched or added
            //
            for (SOCPossibleRoad expandPR : roadsToExpand)
            {
                expandRoadOrShip(expandPR, pl, dummy, trackers, level - 1);
            }
        }

        //
        // remove the dummy road
        //
        dummy.removePiece(dummyRS, null);
    }

    /**
     * add another player's new road or ship, or cancel our own bad road
     * by acting as if another player has placed there.
     * (That way, we won't decide to place there again.)
     *<P>
     * Before v2.0.00 this method was {@code addTheirNewRoad}.
     *
     * @param rs  the new road or ship
     * @param isCancel Is this our own robot's road placement, rejected by the server?
     *     If so, this method call will cancel its placement within the tracker data.
     */
    private void addTheirNewRoadOrShip(SOCRoutePiece rs, boolean isCancel)
    {
        /**
         * see if another player's road interferes with our possible roads
         * and settlements
         */
        /**
         * if another player's road is on one of our possible
         * roads, then remove it
         */
        D.ebugPrintlnINFO("$$$ addTheirNewRoadOrShip : " + rs);

        Integer edge = Integer.valueOf(rs.getCoordinates());
        SOCPossibleRoad pr = possibleRoads.get(edge);

        if (pr != null)
        {
            //D.ebugPrintln("$$$ removing road at "+Integer.toHexString(pr.getCoordinates()));
            possibleRoads.remove(edge);
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
     * Must be called after adding or removing a ship from our player's {@link SOCPlayer#getRoadsAndShips()}.
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

            Enumeration<SOCRoutePiece> roadAndShipEnum = player.getRoadsAndShips().elements();

            SOCShip closest = null;
            int closeR = -1, closeC = -1;
            while (roadAndShipEnum.hasMoreElements())
            {
                final SOCRoutePiece rs = roadAndShipEnum.nextElement();
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
        final List<Integer> closestAdjacs =
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
    protected void updateSettlementConflicts(SOCPossibleSettlement ps, final SOCPlayerTracker[] trackers)
    {
        //D.ebugPrintln("$$$ updateSettlementConflicts : "+Integer.toHexString(ps.getCoordinates()));

        /**
         * look at all adjacent nodes and update possible settlements on nodes
         */
        SOCBoard board = game.getBoard();

        for (final SOCPlayerTracker tracker : trackers)
        {
            if (tracker == null)
                continue;

            /**
             * first look at the node that the possible settlement is on
             */

            /**
             * if it's not our tracker...
             */
            if (tracker.getPlayer().getPlayerNumber() != ps.getPlayer().getPlayerNumber())
            {
                SOCPossibleSettlement posSet
                    = tracker.getPossibleSettlements().get(Integer.valueOf(ps.getCoordinates()));

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
            for (Integer adjNode : board.getAdjacentNodesToNode(ps.getCoordinates()))
            {
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
    public synchronized void addNewSettlement(final SOCSettlement settlement, final SOCPlayerTracker[] trackers)
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
     * @see SOCRobotBrain#cancelWrongPiecePlacement(soc.message.SOCCancelBuildRequest)
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
        Integer settlementCoords = Integer.valueOf(settlement.getCoordinates());
        SOCPossibleSettlement ps = possibleSettlements.get(settlementCoords);
        D.ebugPrintlnINFO("$$$ removing (wrong) " + Integer.toHexString(settlement.getCoordinates()));
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
     *      ({@link #tryPutPiece(SOCPlayingPiece, SOCGame, SOCPlayerTracker[])})
     *</UL>
     *
     * @param settlement  the new settlement
     * @param trackers    player trackers for all of the players
     */
    public synchronized void addOurNewSettlement(final SOCSettlement settlement, final SOCPlayerTracker[] trackers)
    {
        //D.ebugPrintln();
        D.ebugPrintlnINFO("$$$ addOurNewSettlement : " + settlement);
        SOCBoard board = game.getBoard();

        final Integer settlementCoords = Integer.valueOf(settlement.getCoordinates());

        /**
         * add a new possible city
         */
        possibleCities.put
            (settlementCoords, new SOCPossibleCity(player, settlement.getCoordinates(), brain.getEstimatorFactory()));

        /**
         * see if the new settlement was a possible settlement in
         * the list.  if so, remove it.
         */
        SOCPossibleSettlement ps = possibleSettlements.get(settlementCoords);

        if (ps != null)
        {
            D.ebugPrintlnINFO("$$$ was a possible settlement");

            /**
             * copy a list of all the conflicting settlements
             */
            List<SOCPossibleSettlement> conflicts = new ArrayList<SOCPossibleSettlement>(ps.getConflicts());

            /**
             * remove the possible settlement that is now a real settlement
             */
            D.ebugPrintlnINFO("$$$ removing " + Integer.toHexString(settlement.getCoordinates()));
            possibleSettlements.remove(settlementCoords);
            removeFromNecessaryRoads(ps);

            /**
             * remove possible settlements that this one cancels out
             */
            for (SOCPossibleSettlement conflict : conflicts)
            {
                D.ebugPrintlnINFO("$$$ checking conflict with " + conflict.getPlayer().getPlayerNumber() + ":" + Integer.toHexString(conflict.getCoordinates()));

                SOCPlayerTracker tracker = trackers[conflict.getPlayer().getPlayerNumber()];
                if (tracker != null)
                {
                    D.ebugPrintlnINFO("$$$ removing " + Integer.toHexString(conflict.getCoordinates()));
                    tracker.getPossibleSettlements().remove(Integer.valueOf(conflict.getCoordinates()));
                    removeFromNecessaryRoads(conflict);

                    /**
                     * remove the conflicts that this settlement made
                     */
                    for (SOCPossibleSettlement otherConflict : conflict.getConflicts())
                    {
                        D.ebugPrintlnINFO("$$$ removing conflict " + Integer.toHexString(conflict.getCoordinates()) + " from " + Integer.toHexString(otherConflict.getCoordinates()));
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
            D.ebugPrintlnINFO("$$$ wasn't possible settlement");

            ArrayList<SOCPossibleSettlement> trash = new ArrayList<SOCPossibleSettlement>();
            List<Integer> adjNodes = board.getAdjacentNodesToNode(settlement.getCoordinates());

            for (final SOCPlayerTracker tracker : trackers)
            {
                if (tracker == null)
                    continue;

                SOCPossibleSettlement posSet = tracker.getPossibleSettlements().get(settlementCoords);
                D.ebugPrintlnINFO("$$$ tracker for player " + tracker.getPlayer().getPlayerNumber());

                /**
                 * check the node that the settlement is on
                 */
                D.ebugPrintlnINFO("$$$ checking node " + Integer.toHexString(settlement.getCoordinates()));

                if (posSet != null)
                {
                    D.ebugPrintlnINFO("$$$ trashing " + Integer.toHexString(posSet.getCoordinates()));
                    trash.add(posSet);

                    /**
                     * remove the conflicts that this settlement made
                     */
                    for (SOCPossibleSettlement conflict : posSet.getConflicts())
                    {
                        D.ebugPrintlnINFO("$$$ removing conflict " + Integer.toHexString(posSet.getCoordinates()) + " from " + Integer.toHexString(conflict.getCoordinates()));
                        conflict.removeConflict(posSet);
                    }
                }

                /**
                 * check adjacent nodes
                 */
                for (Integer adjNode : adjNodes)
                {
                    D.ebugPrintlnINFO("$$$ checking node " + Integer.toHexString(adjNode.intValue()));
                    posSet = tracker.getPossibleSettlements().get(adjNode);

                    if (posSet != null)
                    {
                        D.ebugPrintlnINFO("$$$ trashing " + Integer.toHexString(posSet.getCoordinates()));
                        trash.add(posSet);

                        /**
                         * remove the conflicts that this settlement made
                         */
                        for (SOCPossibleSettlement conflict : posSet.getConflicts())
                        {
                            D.ebugPrintlnINFO("$$$ removing conflict " + Integer.toHexString(posSet.getCoordinates()) + " from " + Integer.toHexString(conflict.getCoordinates()));
                            conflict.removeConflict(posSet);
                        }
                    }
                }

                /**
                 * take out the trash
                 * (no-longer-possible settlements, roads that support it)
                 */
                D.ebugPrintlnINFO("$$$ removing trash for " + tracker.getPlayer().getPlayerNumber());

                for (SOCPossibleSettlement pset : trash)
                {
                    D.ebugPrintlnINFO("$$$ removing " + Integer.toHexString(pset.getCoordinates()) + " owned by " + pset.getPlayer().getPlayerNumber());
                    tracker.getPossibleSettlements().remove(Integer.valueOf(pset.getCoordinates()));
                    removeFromNecessaryRoads(pset);
                }

                trash.clear();
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
            ArrayList<SOCPossibleRoad> roadsToExpand = null;

            /**
             * Only add new possible roads if we're on a new island
             * (that is, the newly placed settlement has no adjacent roads already).
             * Coastal ships/roads may still be added even if settleAlreadyHasRoad.
             */
            boolean settleAlreadyHasRoad = false;
            ArrayList<SOCPossibleRoad> possibleNewIslandRoads = null;

            final List<Integer> adjacEdges = board.getAdjacentEdgesToNode(settlementCoords);

            // First, loop to check for settleAlreadyHasRoad
            for (final Integer edge : adjacEdges)
            {
                if (possibleRoads.get(edge) != null)
                    continue;  // already a possible road or ship here

                SOCRoutePiece rs = board.roadOrShipAtEdge(edge);
                if ((rs != null) && rs.isRoadNotShip())
                {
                    settleAlreadyHasRoad = true;
                    break;
                }
            }

            // Now, possibly add new roads/ships/coastals
            for (final Integer edge : adjacEdges)
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

                if (board.roadOrShipAtEdge(edge) != null)
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
                        possibleNewIslandRoads = new ArrayList<SOCPossibleRoad>();
                    possibleNewIslandRoads.add( (isCoastline)
                        ? new SOCPossibleShip(player, edge, true, null)
                        : new SOCPossibleRoad(player, edge, null));
                    /*
                    if (isCoastline)
                        System.err.println
                            ("L1675: " + toString() + ": new PossibleShip(true) at 0x" + Integer.toHexString(edge));
                     */
                }
                else if (player.isPotentialShip(edge))
                {
                    // A way out to a new island

                    SOCPossibleShip newPS = new SOCPossibleShip(player, edge, false, null);
                    possibleRoads.put(edge, newPS);
                    // System.err.println("L1685: " + toString() + ": new PossibleShip(false) at 0x" + Integer.toHexString(edge)
                    //     + " from coastal settle 0x" + Integer.toHexString(settlementCoords));

                    if (roadsToExpand == null)
                        roadsToExpand = new ArrayList<SOCPossibleRoad>();
                    roadsToExpand.add(newPS);
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
                    /*
                    System.err.println("L1396: new possible road at edge 0x"
                        + Integer.toHexString(pr.getCoordinates()) + " from coastal settle 0x"
                        + Integer.toHexString(settlementCoords));
                     */
                    if (roadsToExpand == null)
                        roadsToExpand = new ArrayList<SOCPossibleRoad>();
                    roadsToExpand.add(pr);
                    pr.setExpandedFlag();
                }
            }

            if (roadsToExpand != null)
            {
                //
                // expand possible ships/roads that we've added
                //
                SOCPlayer dummy = new SOCPlayer(player, "dummy");
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
        D.ebugPrintlnINFO("$$$ addTheirNewSettlement : " + settlement);

        ArrayList<SOCPossibleRoad> prTrash = new ArrayList<SOCPossibleRoad>();
        ArrayList<SOCPossibleRoad> nrTrash = new ArrayList<SOCPossibleRoad>();
        final List<Integer> adjEdges = game.getBoard().getAdjacentEdgesToNode(settlement.getCoordinates());

        for (Integer edge1 : adjEdges)
        {
            prTrash.clear();

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

                        for (SOCPossiblePiece threat : pr.getThreats())
                        {
                            if ((threat.getType() == SOCPossiblePiece.SETTLEMENT)
                                && (threat.getCoordinates() == settleCoord)
                                && (threat.getPlayer().getPlayerNumber() == settlePN))
                            {
                                D.ebugPrintlnINFO("$$$ new settlement cuts off road at " + Integer.toHexString(pr.getCoordinates()));
                                prTrash.add(pr);

                                break;
                            }
                        }
                    }
                }
                else
                {
                    nrTrash.clear();

                    for (SOCPossibleRoad nr : pr.getNecessaryRoads())
                    {
                        final int nrEdge = nr.getCoordinates();

                        for (Integer edge2 : adjEdges)
                        {
                            if (nrEdge == edge2.intValue())
                            {
                                D.ebugPrintlnINFO("$$$ removing dependency " + Integer.toHexString(nrEdge)
                                    + " from " + Integer.toHexString(pr.getCoordinates()));
                                nrTrash.add(nr);

                                break;
                            }
                        }
                    }

                    ///
                    /// take out nr trash
                    ///
                    if (! nrTrash.isEmpty())
                    {
                        for (SOCPossibleRoad nrTrashRoad : nrTrash)
                        {
                            pr.getNecessaryRoads().remove(nrTrashRoad);
                            nrTrashRoad.getNewPossibilities().remove(pr);
                        }

                        if (pr.getNecessaryRoads().isEmpty())
                        {
                            D.ebugPrintlnINFO("$$$ no more dependencies, removing " + Integer.toHexString(pr.getCoordinates()));
                            prTrash.add(pr);
                        }
                    }
                }
            }

            ///
            /// take out the pr trash
            ///
            for (SOCPossibleRoad prt : prTrash)
            {
                possibleRoads.remove(Integer.valueOf(prt.getCoordinates()));
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

        for (SOCPossiblePiece newPos : road.getNewPossibilities())
        {
            //D.ebugPrintln("$$$ updating "+Integer.toHexString(newPos.getCoordinates()));
            final List<SOCPossibleRoad> nr;

            switch (newPos.getType())
            {
            case SOCPossiblePiece.SHIP:  // fall through to ROAD
            case SOCPossiblePiece.ROAD:
                nr = ((SOCPossibleRoad) newPos).getNecessaryRoads();

                if (nr.isEmpty())
                {
                    System.err.println("ERROR in removeDependents - empty nr list for " + newPos);
                }
                else
                {
                    nr.remove(road);

                    if (nr.isEmpty())
                    {
                        //D.ebugPrintln("$$$ removing this road");
                        possibleRoads.remove(Integer.valueOf(newPos.getCoordinates()));
                        removeFromNecessaryRoads((SOCPossibleRoad) newPos);
                        removeDependents((SOCPossibleRoad) newPos);
                    }
                    else
                    {
                        //
                        // update this road's numberOfNecessaryRoads value
                        //
                        int smallest = 40;
                        for (SOCPossibleRoad necRoad : nr)
                            if ((necRoad.getNumberOfNecessaryRoads() + 1) < smallest)
                                smallest = necRoad.getNumberOfNecessaryRoads() + 1;

                        ((SOCPossibleRoad) newPos).setNumberOfNecessaryRoads(smallest);
                    }
                }

                break;

            case SOCPossiblePiece.SETTLEMENT:
                nr = ((SOCPossibleSettlement) newPos).getNecessaryRoads();

                if (nr.isEmpty())
                {
                    System.err.println("ERROR in removeDependents - empty nr list for " + newPos);
                }
                else
                {
                    nr.remove(road);

                    if (nr.isEmpty())
                    {
                        //D.ebugPrintln("$$$ removing this settlement");
                        possibleSettlements.remove(Integer.valueOf(newPos.getCoordinates()));
                        removeFromNecessaryRoads((SOCPossibleSettlement) newPos);

                        /**
                         * remove the conflicts that this settlement made
                         */
                        for (SOCPossibleSettlement conflict : ((SOCPossibleSettlement) newPos).getConflicts())
                            conflict.removeConflict((SOCPossibleSettlement) newPos);
                    }
                    else
                    {
                        //
                        // update this road's numberOfNecessaryRoads value
                        //
                        int smallest = 40;
                        for (SOCPossibleRoad necRoad : nr)
                            if ((necRoad.getNumberOfNecessaryRoads() + 1) < smallest)
                                smallest = necRoad.getNumberOfNecessaryRoads() + 1;

                        ((SOCPossibleSettlement) newPos).setNumberOfNecessaryRoads(smallest);
                    }
                }

                break;
            }
        }

        road.getNewPossibilities().clear();
    }

    /**
     * remove this piece from the pieces that support it
     *
     * @param pr  the possible road
     */
    protected void removeFromNecessaryRoads(SOCPossibleRoad pr)
    {
        //D.ebugPrintln("%%% remove road from necessary roads");

        for (SOCPossibleRoad nr : pr.getNecessaryRoads())
            //D.ebugPrintln("%%% removing road at "+Integer.toHexString(pr.getCoordinates())+" from road at "+Integer.toHexString(nr.getCoordinates()));
            nr.getNewPossibilities().remove(pr);
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

        for (SOCPossibleRoad nr : ps.getNecessaryRoads())
            //D.ebugPrintln("%%% removing settlement at "+Integer.toHexString(ps.getCoordinates())+" from road at "+Integer.toHexString(nr.getCoordinates()));
            nr.getNewPossibilities().remove(ps);
    }

    /**
     * Remove our incorrect city placement, it's been rejected by the server.
     *<P>
     * Note, there is no addNewCity or addTheirNewCity method.
     *
     * @param city Location of our bad city
     *
     * @see SOCRobotBrain#cancelWrongPiecePlacement(soc.message.SOCCancelBuildRequest)
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
        possibleCities.remove(Integer.valueOf(city.getCoordinates()));
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
        possibleCities.remove(Integer.valueOf(city.getCoordinates()));
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
        possibleCities.put(Integer.valueOf(city.getCoordinates()), city);
    }

    /**
     * update threats for pieces that need to be updated
     *
     * @param trackers  all of the player trackers
     */
    public void updateThreats(final SOCPlayerTracker[] trackers)
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

            if ((! posRoad.isThreatUpdated()) && posRoad.getNecessaryRoads().isEmpty())
            {
                //D.ebugPrintln("&&&& examining road at "+Integer.toHexString(posRoad.getCoordinates()));

                /**
                 * look for possible settlements that can block this road
                 */
                final int[] adjNodesToPosRoad = board.getAdjacentNodesToEdge_arr(posRoad.getCoordinates());

                for (final int adjEdge : board.getAdjacentEdgesToEdge(posRoad.getCoordinates()))
                {
                    final SOCRoutePiece realRoad = player.getRoadOrShip(adjEdge);

                    if (realRoad != null)
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
                                    final Integer adjNodeToPosRoadInt = Integer.valueOf(adjNodeToPosRoad);
                                    for (int tpn = 0; tpn < trackers.length; ++tpn)
                                    {
                                        if (tpn == playerNumber)
                                            continue;

                                        final SOCPlayerTracker tracker = trackers[tpn];
                                        if (tracker != null)
                                        {
                                            SOCPossibleSettlement posEnemySet
                                                = tracker.getPossibleSettlements().get(adjNodeToPosRoadInt);

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

                /**
                 * look for enemy roads that can block this road
                 */
                for (final SOCPlayerTracker tracker : trackers)
                {
                    if (tracker == null)
                        continue;

                    if (tracker.getPlayer().getPlayerNumber() != playerNumber)
                    {
                        SOCPossibleRoad posEnemyRoad
                            = tracker.getPossibleRoads().get(Integer.valueOf(posRoad.getCoordinates()));

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
                final List<SOCPossiblePiece> threats = posRoad.getThreats();
                final Stack<SOCPossiblePiece> stack = new Stack<SOCPossiblePiece>();
                stack.push(posRoad);

                while (! stack.empty())
                {
                    SOCPossiblePiece curPosPiece = stack.pop();

                    // TODO: is roads only; need to also decide how ships are threatened

                    if ((curPosPiece.getType() == SOCPossiblePiece.ROAD)
                        || ((curPosPiece instanceof SOCPossibleShip)
                             && ((SOCPossibleShip) curPosPiece).isCoastalRoadAndShip))
                    {
                        for (SOCPossiblePiece newPosPiece : ((SOCPossibleRoad) curPosPiece).getNewPossibilities())
                        {
                            if ((newPosPiece.getType() == SOCPossiblePiece.ROAD)
                                || ((newPosPiece instanceof SOCPossibleShip)
                                    && ((SOCPossibleShip) newPosPiece).isCoastalRoadAndShip))
                            {
                                final List<SOCPossibleRoad> necRoadList = ((SOCPossibleRoad) newPosPiece).getNecessaryRoads();

                                if ((necRoadList.size() == 1) && (necRoadList.get(0) == curPosPiece))
                                {
                                    /**
                                     * pass on all of the threats to this piece
                                     */

                                    //D.ebugPrintln("&&&& adding threats to road at "+Integer.toHexString(newPosPiece.getCoordinates()));
                                    for (SOCPossiblePiece threat : threats)
                                        ((SOCPossibleRoad) newPosPiece).addThreat(threat);
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

            if (! posRoad.isThreatUpdated())
            {
                //D.ebugPrintln("&&&& examining road at "+Integer.toHexString(posRoad.getCoordinates()));

                /**
                 * check for enemy roads with
                 * the same coordinates
                 */
                for (final SOCPlayerTracker tracker : trackers)
                {
                    if (tracker == null)
                        continue;

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
                final List<SOCPossibleRoad> necRoadList = posRoad.getNecessaryRoads();

                if (necRoadList.size() == 1)
                {
                    final SOCPossibleRoad necRoad = necRoadList.get(0);
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
                                final Integer adjNodeInt = Integer.valueOf(adjNode1);

                                for (final SOCPlayerTracker tracker : trackers)
                                {
                                    if (tracker == null)
                                        continue;

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

            if (! posSet.isThreatUpdated())
            {
                //D.ebugPrintln("&&&& examining settlement at "+Integer.toHexString(posSet.getCoordinates()));

                /**
                 * see if there are enemy settlements with the same coords
                 */
                for (final SOCPlayerTracker tracker : trackers)
                {
                    if (tracker == null)
                        continue;

                    if (tracker.getPlayer().getPlayerNumber() != playerNumber)
                    {
                        SOCPossibleSettlement posEnemySet
                            = tracker.getPossibleSettlements().get(Integer.valueOf(posSet.getCoordinates()));

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
                final List<SOCPossibleRoad> necRoadList = posSet.getNecessaryRoads();

                if (necRoadList.isEmpty())
                {
                    ;
                }
                else if (necRoadList.size() == 1)
                {
                    //
                    // if it relies on only one road, then it inherits the road's threats
                    //
                    //D.ebugPrintln("&&&& inheriting threats from road at "+Integer.toHexString(((SOCPossibleRoad)necRoadVec.firstElement()).getCoordinates()));

                    for (SOCPossiblePiece nrThreat: necRoadList.get(0).getThreats())
                        posSet.addThreat(nrThreat);
                }
                else
                {
                    //
                    // this settlement relies on more than one road.
                    // if all of the roads have the same threat,
                    // then add that threat to this settlement
                    //
                    final SOCPossibleRoad nr = necRoadList.get(0);

                    for (SOCPossiblePiece nrThreat : nr.getThreats())
                    {
                        boolean allHaveIt = true;

                        for (SOCPossibleRoad nr2 : necRoadList)
                        {
                            if ((nr2 != nr) && ! nr2.getThreats().contains(nrThreat))
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
     * Always 500 or more if {@link SOCGameOptionSet#K_SC_0RVP} is set.
     * Updates fields for {@link #getLongestRoadETA()} and {@link #getRoadsToGo()}.
     * @see #recalculateAllETAs()
     */
    public void recalcLongestRoadETA()
    {
        // TODO handle ships here (different resources, etc)

        D.ebugPrintlnINFO("===  recalcLongestRoadETA for player " + playerNumber);

        final int roadETA;
        SOCBuildingSpeedEstimate bse = brain.getEstimator(player.getNumbers());
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
        else if (! game.isGameOptionSet(SOCGameOptionSet.K_SC_0RVP))
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

        D.ebugPrintlnINFO("--- roadsToGo = " + roadsToGo);
        longestRoadETA = roadsToGo * roadETA;
    }

    /**
     * Does a depth first search from the end point of the longest
     * path in a graph of nodes and returns how many roads would
     * need to be built to take longest road.
     *<P>
     * Do not call if {@link SOCGameOptionSet#K_SC_0RVP} is set.
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
     * @see #recalculateAllETAs()
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
            SOCBuildingSpeedEstimate bse = brain.getEstimator(player.getNumbers());
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
        SOCPlayer dummy = new SOCPlayer(player, "dummy");
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
                // calc longest route value
                //
                final SOCRoutePiece dummyRS;
                if (posRoad.isRoadNotShip()
                    || ((posRoad instanceof SOCPossibleShip) && ((SOCPossibleShip) posRoad).isCoastalRoadAndShip))
                    dummyRS = new SOCRoad(dummy, posRoad.getCoordinates(), null);  // TODO better coastal handling
                else
                    dummyRS = new SOCShip(dummy, posRoad.getCoordinates(), null);
                dummy.putPiece(dummyRS, true);

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
                updateLRPotential(posRoad, dummy, dummyRS, lrLength, LR_CALC_LEVEL);
                dummy.removePiece(dummyRS, null);
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
     * @param dummyRS   the dummy road or ship
     * @param lrLength  the current LR length
     * @param level     how many levels of recursion, or 0 to not recurse
     */
    public void updateLRPotential
        (SOCPossibleRoad posRoad, SOCPlayer dummy, SOCRoutePiece dummyRS, final int lrLength, final int level)
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

            for (final int adjEdge : board.getAdjacentEdgesToEdge(dummyRS.getCoordinates()))
            {
                if ( (dummyRS.isRoadNotShip() && dummy.isPotentialRoad(adjEdge))
                     || ((! dummyRS.isRoadNotShip()) && dummy.isPotentialShip(adjEdge)) )
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
            for (final int adjEdge : board.getAdjacentEdgesToEdge(dummyRS.getCoordinates()))
            {
                if ( (dummyRS.isRoadNotShip() && dummy.isPotentialRoad(adjEdge))
                     || ((! dummyRS.isRoadNotShip()) && dummy.isPotentialShip(adjEdge)) )
                {
                    final SOCRoutePiece newDummyRS;
                    if (dummyRS.isRoadNotShip())
                        newDummyRS = new SOCRoad(dummy, adjEdge, board);
                    else
                        newDummyRS = new SOCShip(dummy, adjEdge, board);
                    dummy.putPiece(newDummyRS, true);
                    updateLRPotential(posRoad, dummy, newDummyRS, lrLength, level - 1);
                    dummy.removePiece(newDummyRS, null);
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
     * or call {@link #expandRoadOrShip(SOCPossibleRoad, SOCPlayer, SOCPlayer, SOCPlayerTracker[], int)}, so it
     * may run out of potential locations before {@code vp_winner} is reached. If the loop doesn't have the locations or
     * pieces to do anything, 500 ETA and 2 VP are added to the totals to keep things moving.
     *<P>
     * If the loop reaches {@link SOCGame#vp_winner} - 1, it calculates ETAs for 1 city or settlement (+ roads)
     * instead of 2, and Largest Army and Longest Road, to make its choice.
     *
     * @see #recalculateAllETAs()
     */
    public void recalcWinGameETA()
    {
        int oldWGETA = winGameETA;

        try
        {
            needLR = false;
            needLA = false;
            winGameETA = 0;
            HashSet<Integer> printedWarnSettleCoords = new HashSet<>();

            SOCPlayerNumbers tempPlayerNumbers = new SOCPlayerNumbers(player.getNumbers());
            boolean[] tempPortFlags = new boolean[SOCBoard.WOOD_PORT + 1];

            for (int portType = SOCBoard.MISC_PORT;
                    portType <= SOCBoard.WOOD_PORT; portType++)
            {
                tempPortFlags[portType] = player.getPortFlag(portType);
            }

            SOCBuildingSpeedEstimate[] tempSetBSE = new SOCBuildingSpeedEstimate[2];
            SOCBuildingSpeedEstimate[] tempCityBSE = new SOCBuildingSpeedEstimate[2];

            tempCityBSE[0] = brain.getEstimator();
            tempCityBSE[1] = brain.getEstimator();

            tempSetBSE[0] = brain.getEstimator();
            tempSetBSE[1] = brain.getEstimator();

            int[][] chosenSetBuildingSpeed = new int[2][SOCBuildingSpeedEstimate.MAXPLUSONE];
            int[][] chosenCityBuildingSpeed = new int[2][SOCBuildingSpeedEstimate.MAXPLUSONE];

            SOCBuildingSpeedEstimate tempBSE = brain.getEstimator();

            SOCBuildingSpeedEstimate ourBSE = brain.getEstimator(player.getNumbers());
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
            final SOCBuildingSpeedEstimateFactory bsef = brain.getEstimatorFactory();

            if (D.ebugOn)
            {
                if (laPlayer != null)
                {
                    D.ebugPrintlnINFO("laPlayer # = " + laPlayer.getPlayerNumber());
                }
                else
                {
                    D.ebugPrintlnINFO("laPlayer = null");
                }

                if (lrPlayer != null)
                {
                    D.ebugPrintlnINFO("lrPlayer # = " + lrPlayer.getPlayerNumber());
                }
                else
                {
                    D.ebugPrintlnINFO("lrPlayer = null");
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

            final int vp_winner = game.vp_winner;
            vpLoop:
            while (points < vp_winner)
            {
                D.ebugPrintlnINFO("WWW points = " + points);
                D.ebugPrintlnINFO("WWW settlementPiecesLeft = " + settlementPiecesLeft);
                D.ebugPrintlnINFO("WWW cityPiecesLeft = " + cityPiecesLeft);
                D.ebugPrintlnINFO("WWW settlementSpotsLeft = " + posSetsCopy.size());
                D.ebugPrintlnINFO("WWW citySpotsLeft = " + posCitiesCopy.size());

                if (D.ebugOn)
                {
                    D.ebugPrintINFO("WWW tempPortFlags: ");

                    for (int portType = SOCBoard.MISC_PORT;
                            portType <= SOCBoard.WOOD_PORT; portType++)
                    {
                        D.ebugPrintINFO(tempPortFlags[portType] + " ");
                    }

                    D.ebugPrintlnINFO();
                }

                D.ebugPrintlnINFO("WWW settlementETA = " + settlementETA);
                D.ebugPrintlnINFO("WWW cityETA = " + cityETA);
                D.ebugPrintlnINFO("WWW roadETA = " + roadETA);
                D.ebugPrintlnINFO("WWW cardETA = " + cardETA);

                if (points == (vp_winner - 1))
                {
                    fastestETA = 500;

                    SOCPossibleSettlement chosenSet = null;

                    if ((settlementPiecesLeft > 0) && (! posSetsCopy.isEmpty()))
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
                            final int totalNecRoads = calcTotalNecessaryRoads(chosenSet, printedWarnSettleCoords);

                            fastestETA = (settlementETA + (totalNecRoads * roadETA));
                            D.ebugPrintlnINFO("WWW # necesesary roads = " + totalNecRoads);
                            D.ebugPrintlnINFO("WWW this settlement eta = " + (settlementETA + (totalNecRoads * roadETA)));
                            D.ebugPrintlnINFO("WWW settlement is " + chosenSet);
                            D.ebugPrintlnINFO("WWW settlement eta = " + fastestETA);
                        }
                        else
                        {
                            fastestETA = 500;
                        }
                    }

                    if ((cityPiecesLeft > 0) && (citySpotsLeft > 0) && (cityETA <= fastestETA))
                    {
                        D.ebugPrintlnINFO("WWW city eta = " + cityETA);
                        fastestETA = cityETA;
                    }

                    if (!haveLA && !needLA && (tempLargestArmyETA < fastestETA))
                    {
                        D.ebugPrintlnINFO("WWW LA eta = " + tempLargestArmyETA);
                        fastestETA = tempLargestArmyETA;
                    }

                    if (!haveLR && !needLR && (tempLongestRoadETA < fastestETA))
                    {
                        D.ebugPrintlnINFO("WWW LR eta = " + tempLongestRoadETA);
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

                    D.ebugPrintlnINFO("WWW Adding " + fastestETA + " to win eta");
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
                            D.ebugPrintlnINFO("WWW twoCities = " + twoCities);
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

                        ArrayList<SOCPossibleSettlement> posSetsToPutBack = new ArrayList<SOCPossibleSettlement>();

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
                                int totalNecRoads = calcTotalNecessaryRoads(chosenSet[i], printedWarnSettleCoords);

                                D.ebugPrintlnINFO("WWW # necesesary roads = " + totalNecRoads);
                                D.ebugPrintlnINFO("WWW this settlement eta = " + (settlementETA + (totalNecRoads * roadETA)));

                                if ((i == 0) && (chosenSet[0] != null))
                                {
                                    posSetsCopy.remove(Integer.valueOf(chosenSet[0].getCoordinates()));

                                    for (SOCPossibleSettlement conflict : chosenSet[0].getConflicts())
                                    {
                                        Integer conflictInt = Integer.valueOf(conflict.getCoordinates());
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

                        posSetsCopy.put(Integer.valueOf(chosenSet[0].getCoordinates()), chosenSet[0]);

                        for (SOCPossibleSettlement tmpPosSet : posSetsToPutBack)
                        {
                            posSetsCopy.put(Integer.valueOf(tmpPosSet.getCoordinates()), tmpPosSet);
                        }

                        if (canBuild2Settlements && (twoSettlements <= fastestETA))
                        {
                            D.ebugPrintlnINFO("WWW 2 * settlement = " + twoSettlements);
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
                            chosenCity[0] = new SOCPossibleCity(player, chosenSet[0].getCoordinates(), bsef);
                        }

                        ///
                        ///  estimate setETA using building speed
                        ///  for settlements and roads from nothing
                        ///
                        ///  as long as this settlement needs roads
                        ///  add a roadETA to the ETA for this settlement
                        ///
                        int totalNecRoads = calcTotalNecessaryRoads(chosenSet[0], printedWarnSettleCoords);

                        D.ebugPrintlnINFO("WWW # necesesary roads = " + totalNecRoads);
                        D.ebugPrintlnINFO("WWW this settlement eta = " + (settlementETA + (totalNecRoads * roadETA)));

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
                            D.ebugPrintlnINFO("WWW one of each = " + oneOfEach);
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
                            D.ebugPrintlnINFO("WWW ERROR CALCULATING LA ETA");
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

                        D.ebugPrintlnINFO("WWW LA eta = " + tempLargestArmyETA);

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
                        D.ebugPrintlnINFO("WWW LR eta = " + tempLongestRoadETA);

                        if (tempLongestRoadETA < fastestETA)
                        {
                            fastestETA = tempLongestRoadETA;
                        }
                    }

                    ///
                    /// implement the fastest scenario
                    ///
                    D.ebugPrintlnINFO("WWW Adding " + fastestETA + " to win eta");
                    points += 2;
                    winGameETA += fastestETA;
                    D.ebugPrintlnINFO("WWW WGETA SO FAR FOR PLAYER " + playerNumber + " = " + winGameETA);

                    if ((settlementPiecesLeft > 1) && (posSetsCopy.size() > 1)
                        && canBuild2Settlements && (fastestETA == twoSettlements))
                    {
                        Integer chosenSet0Int = Integer.valueOf(chosenSet[0].getCoordinates());
                        Integer chosenSet1Int = Integer.valueOf(chosenSet[1].getCoordinates());
                        posSetsCopy.remove(chosenSet0Int);
                        posSetsCopy.remove(chosenSet1Int);
                        posCitiesCopy.put
                            (chosenSet0Int, new SOCPossibleCity(player, chosenSet[0].getCoordinates(), bsef));
                        posCitiesCopy.put
                            (chosenSet1Int, new SOCPossibleCity(player, chosenSet[1].getCoordinates(), bsef));

                        //
                        // remove possible settlements that are conflicts
                        //
                        for (SOCPossibleSettlement conflict : chosenSet[0].getConflicts())
                        {
                            Integer conflictInt = Integer.valueOf(conflict.getCoordinates());
                            posSetsCopy.remove(conflictInt);
                        }

                        for (SOCPossibleSettlement conflict : chosenSet[1].getConflicts())
                        {
                            Integer conflictInt = Integer.valueOf(conflict.getCoordinates());
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
                        D.ebugPrintlnINFO("WWW  * build two settlements");
                        D.ebugPrintlnINFO("WWW    settlement 1: " + board.nodeCoordToString(chosenSet[0].getCoordinates()));
                        D.ebugPrintlnINFO("WWW    settlement 2: " + board.nodeCoordToString(chosenSet[1].getCoordinates()));

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
                        if (chosenCity[0] == null)
                            break vpLoop;  // <--- rarely occurs; avoid possible endless loop ---

                        Integer chosenSet0Int = Integer.valueOf(chosenSet[0].getCoordinates());
                        posSetsCopy.remove(chosenSet0Int);

                        if (chosenSet[0].getCoordinates() != chosenCity[0].getCoordinates())
                        {
                            posCitiesCopy.put
                                (chosenSet0Int, new SOCPossibleCity(player, chosenSet[0].getCoordinates(), bsef));
                        }

                        posCitiesCopy.remove(Integer.valueOf(chosenCity[0].getCoordinates()));
                        cityPiecesLeft -= 1;

                        //
                        // remove possible settlements that are conflicts
                        //
                        for (SOCPossibleSettlement conflict : chosenSet[0].getConflicts())
                        {
                            Integer conflictInt = Integer.valueOf(conflict.getCoordinates());
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
                        D.ebugPrintlnINFO("WWW  * build a settlement and a city");
                        D.ebugPrintlnINFO("WWW    settlement at " + board.nodeCoordToString(chosenSet[0].getCoordinates()));
                        D.ebugPrintlnINFO("WWW    city at " + board.nodeCoordToString(chosenCity[0].getCoordinates()));

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
                        posCitiesCopy.remove(Integer.valueOf(chosenCity[0].getCoordinates()));

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
                            D.ebugPrintlnINFO("tempPlayerNumbers = " + tempPlayerNumbers);
                            tempBSE.recalculateEstimates(tempPlayerNumbers);

                            int[] tempBuildingSpeed = tempBSE.getEstimatesFromNothingFast(tempPortFlags);
                            int tempSpeedupTotal = 0;

                            //boolean ok = true;
                            for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                                    buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                                    buildingType++)
                            {
                                D.ebugPrintlnINFO("ourBuildingSpeed[" + buildingType + "] = " + ourBuildingSpeed[buildingType]);
                                D.ebugPrintlnINFO("tempBuildingSpeed[" + buildingType + "] = " + tempBuildingSpeed[buildingType]);

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
                            D.ebugPrintlnINFO("tempPlayerNumbers = " + tempPlayerNumbers);
                            D.ebugPrintlnINFO("WWW City at " + board.nodeCoordToString(posCity1.getCoordinates())
                                + " has tempSpeedupTotal = " + tempSpeedupTotal);

                            if (tempSpeedupTotal >= bestCitySpeedupTotal)
                            {
                                bestCitySpeedupTotal = tempSpeedupTotal;
                                chosenCity[1] = posCity1;
                            }
                        }

                        // Note: occasionally chosenCity[1] == null despite citySpotsLeft > 1
                        // If so, plan won't be perfectly accurate, but it'd build 1 city which is progress.

                        if (chosenCity[1] != null)
                        {
                            posCitiesCopy.remove(Integer.valueOf(chosenCity[1].getCoordinates()));
                            tempPlayerNumbers.updateNumbers(chosenCity[1].getCoordinates(), board);

                            settlementPiecesLeft += 2;
                            cityPiecesLeft -= 2;
                            citySpotsLeft -= 2;
                        } else {
                            --points;  // counteract += 2 which assumed can build 2

                            settlementPiecesLeft++;
                            cityPiecesLeft--;
                            citySpotsLeft--;
                        }

                        ourBSE.recalculateEstimates(tempPlayerNumbers);
                        ourBuildingSpeed = ourBSE.getEstimatesFromNothingFast(tempPortFlags);
                        settlementETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.SETTLEMENT];
                        roadETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.ROAD];
                        cityETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CITY];
                        cardETA = ourBuildingSpeed[SOCBuildingSpeedEstimate.CARD];
                        D.ebugPrintlnINFO("WWW  * build 2 cities");
                        D.ebugPrintlnINFO("WWW    city 1: " + board.nodeCoordToString(chosenCity[0].getCoordinates()));
                        if (chosenCity[1] != null)
                            D.ebugPrintlnINFO("WWW    city 2: " + board.nodeCoordToString(chosenCity[1].getCoordinates()));

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
                        D.ebugPrintlnINFO("WWW  * take longest road");

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": Longest Road");
                        }
                    }
                    else if (!haveLA && !needLA && (points > 5) && (fastestETA == tempLargestArmyETA))
                    {
                        needLA = true;
                        D.ebugPrintlnINFO("WWW  * take largest army");

                        if (brain.getDRecorder().isOn())
                        {
                            brain.getDRecorder().record(fastestETA + ": Largest Army");
                        }
                    }
                }
            }

            D.ebugPrintlnINFO("WWW TOTAL WGETA FOR PLAYER " + playerNumber + " = " + winGameETA);

            if (brain.getDRecorder().isOn())
            {
                brain.getDRecorder().record("Total WGETA for " + player.getName() + " = " + winGameETA);
                brain.getDRecorder().record("--------------------");
            }
        }
        catch (Exception e)
        {
            winGameETA = oldWGETA;
            System.err.println("Exception in recalcWinGameETA - " + e);
            e.printStackTrace();
        }

        //System.out.println("good = "+good+" bad = "+bad);
        //System.out.println();
    }

    /**
     * Calculate the total number of necessary roads before this {@link SOCPossibleSettlement} can be built:
     * Do a BFS of its chain of {@link SOCPossibleSettlement#getNecessaryRoads() getNecessaryRoads()}.
     * Iterates through a queue made from {@code ps}'s necessary roads, tracking each one's "distance"
     * (in needed roads) from {@code ps} and adding that road's own {@link SOCPossibleRoad#getNecessaryRoads()} to
     * the end of the queue, until a road is found which has no necessary roads. That road's "distance" is returned.
     *
     * @param ps  The settlement to calculate this for
     * @param printedWarnSettleCoords  The set tracking which potential settlement locations for which
     *     we've already printed "Necessary Road Path too long" or "Necessary Road Path length unresolved";
     *     if print that here for {@code ps}, will add its node coord to this set
     * @return  0 if {@code ps.getNecessaryRoads()} is empty; <BR>
     *     40 if there were too many necessary roads or they somehow formed a loop; <BR>
     *     otherwise the total number of roads needed before {@code ps} can be built
     * @since 2.0.00
     */
    private static int calcTotalNecessaryRoads
        (final SOCPossibleSettlement ps, final HashSet<Integer> printedWarnSettleCoords)
    {
        if (ps.getNecessaryRoads().isEmpty())
        {
            return 0;  // <--- Early return: No roads needed ---
        }

        int totalNecRoads = 0;
        int psNodeAddWarn = -1;  // if >= 0, will add to printedWarnSettleCoords

        /** Queue to track each unvisited possible road's "distance" from ps and its own necessary roads */
        Queue<Pair<Integer, List<SOCPossibleRoad>>> necRoadQueue = new Queue<Pair<Integer, List<SOCPossibleRoad>>>();

        necRoadQueue.clear();
        necRoadQueue.put(new Pair<Integer, List<SOCPossibleRoad>>
            (Integer.valueOf(0), ps.getNecessaryRoads()));

        for (int maxIter = 50; maxIter > 0 && ! necRoadQueue.empty(); --maxIter)
        {
            Pair<Integer, List<SOCPossibleRoad>> necRoadPair = necRoadQueue.get();
            totalNecRoads = necRoadPair.getA();
            List<SOCPossibleRoad> necRoadsToCurrent = necRoadPair.getB();

            if (necRoadsToCurrent.isEmpty())
            {
                necRoadQueue.clear();
            } else {
                if (necRoadQueue.size() + necRoadsToCurrent.size() > 40)
                {
                    // Too many necessary, or dupes led to loop. Bug in necessary road construction?
                    final int psNode = ps.getCoordinates();
                    if (! printedWarnSettleCoords.contains(psNode))
                    {
                        System.err.println
                            ("PT.calcTotalNecessaryRoads L3889: Necessary Road Path too long for settle at 0x"
                             + Integer.toHexString(psNode));
                        psNodeAddWarn = psNode;
                    }
                    totalNecRoads = 40;
                    necRoadQueue.clear();
                    break;
                }

                for (SOCPossibleRoad nr : necRoadsToCurrent)
                    necRoadQueue.put(new Pair<Integer, List<SOCPossibleRoad>>
                        (Integer.valueOf(totalNecRoads + 1), nr.getNecessaryRoads()));
            }
        }

        if (! necRoadQueue.empty())
        {
            // Dupes in various dependent roads? Bug in necessary road construction?
            final int psNode = ps.getCoordinates();
            if (! printedWarnSettleCoords.contains(psNode))
            {
                System.err.println
                    ("PT.calcTotalNecessaryRoads L3906: Necessary Road Path length unresolved for settle at 0x"
                     + Integer.toHexString(psNode));
                psNodeAddWarn = psNode;
            }
            totalNecRoads = 40;
        }

        if (psNodeAddWarn >= 0)
            printedWarnSettleCoords.add(psNodeAddWarn);

        return totalNecRoads;
    }

    /**
     * See how building a piece impacts the game.
     * Calls {@link SOCGame#putTempPiece(SOCPlayingPiece)} and {@link #copyPlayerTrackers(SOCPlayerTracker[])},
     * then adds <tt>piece</tt> to the tracker copies.
     *
     * @param piece      the piece to build
     * @param game       the game
     * @param trackers   the player trackers
     *
     * @return a copy of the player trackers with the new piece in place
     * @see #tryPutPieceNoCopy(SOCPlayingPiece, SOCGame, SOCPlayerTracker[])
     */
    public static SOCPlayerTracker[] tryPutPiece
        (final SOCPlayingPiece piece, final SOCGame game, final SOCPlayerTracker[] trackers)
    {
        final SOCPlayerTracker[] trackersCopy = SOCPlayerTracker.copyPlayerTrackers(trackers);

        if (piece != null)
        {
            game.putTempPiece(piece);

            for (final SOCPlayerTracker trackerCopy : trackersCopy)
            {
                if (trackerCopy == null)
                    continue;

                switch (piece.getType())
                {
                case SOCPlayingPiece.SHIP:  // fall through to ROAD
                case SOCPlayingPiece.ROAD:
                    trackerCopy.addNewRoadOrShip((SOCRoutePiece) piece, trackersCopy);

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
     * Same as {@link #tryPutPiece(SOCPlayingPiece, SOCGame, SOCPlayerTracker[]) tryPutPiece},
     * but we don't make a copy of the player trackers. Instead caller supplies the copy.
     *
     * @param piece      the piece to build
     * @param game       the game
     * @param trackers   the already-copied player trackers
     */
    public static void tryPutPieceNoCopy
        (final SOCPlayingPiece piece, final SOCGame game, final SOCPlayerTracker[] trackers)
    {
        if (piece != null)
        {
            game.putTempPiece(piece);

            for (final SOCPlayerTracker tracker : trackers)
            {
                if (tracker == null)
                    continue;

                switch (piece.getType())
                {
                case SOCPlayingPiece.SHIP:  // fall through to ROAD
                case SOCPlayingPiece.ROAD:
                    tracker.addNewRoadOrShip((SOCRoutePiece) piece, trackers);

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
     * Calls <tt>D.ebugPrintlnINFO</tt>; no output will appear if this class
     * imports <tt>soc.disableDebug.D</tt> instead of <tt>soc.debug.D</tt>.
     *
     * @param playerTrackers  the player trackers
     */
    public static void playerTrackersDebug(final SOCPlayerTracker[] playerTrackers)
    {
        if (! D.ebugOn)
        {
            return;
        }

        for (final SOCPlayerTracker tracker : playerTrackers)
        {
            if (tracker == null)
                continue;

            D.ebugPrintlnINFO("%%%%%%%%% TRACKER FOR PLAYER " + tracker.getPlayer().getPlayerNumber());
            D.ebugPrintlnINFO("   LONGEST ROAD ETA = " + tracker.getLongestRoadETA());
            D.ebugPrintlnINFO("   LARGEST ARMY ETA = " + tracker.getLargestArmyETA());

            Iterator<SOCPossibleRoad> prIter = tracker.getPossibleRoads().values().iterator();

            while (prIter.hasNext())
            {
                SOCPossibleRoad pr = prIter.next();
                if (pr.isRoadNotShip())
                    D.ebugPrintINFO("%%% possible road at ");
                else
                    D.ebugPrintINFO("%%% possible ship at ");
                D.ebugPrintlnINFO(Integer.toHexString(pr.getCoordinates()));
                D.ebugPrintINFO("   eta:" + pr.getETA());
                D.ebugPrintINFO("   this road/ship needs:");

                for (SOCPossibleRoad nr : pr.getNecessaryRoads())
                    D.ebugPrintINFO(" " + Integer.toHexString(nr.getCoordinates()));

                D.ebugPrintlnINFO();
                D.ebugPrintINFO("   this road/ship supports:");

                for (SOCPossiblePiece pp : pr.getNewPossibilities())
                    D.ebugPrintINFO(" " + Integer.toHexString(pp.getCoordinates()));

                D.ebugPrintlnINFO();
                D.ebugPrintINFO("   threats:");

                for (SOCPossiblePiece threat : pr.getThreats())
                    D.ebugPrintINFO(" " + threat.getPlayer().getPlayerNumber() + ":" + threat.getType() + ":"
                        + Integer.toHexString(threat.getCoordinates()));

                D.ebugPrintlnINFO();
                D.ebugPrintlnINFO("   LR value=" + pr.getLRValue() + " LR Potential=" + pr.getLRPotential());
            }

            Iterator<SOCPossibleSettlement> psIter = tracker.getPossibleSettlements().values().iterator();

            while (psIter.hasNext())
            {
                SOCPossibleSettlement ps = psIter.next();
                D.ebugPrintlnINFO("%%% possible settlement at " + Integer.toHexString(ps.getCoordinates()));
                D.ebugPrintINFO("   eta:" + ps.getETA());
                D.ebugPrintINFO("%%%   conflicts");
                for (SOCPossibleSettlement conflict : ps.getConflicts())
                    D.ebugPrintINFO(" " + conflict.getPlayer().getPlayerNumber() + ":"
                        + Integer.toHexString(conflict.getCoordinates()));

                D.ebugPrintlnINFO();
                D.ebugPrintINFO("%%%   necessary roads/ships");
                for (SOCPossibleRoad nr : ps.getNecessaryRoads())
                    D.ebugPrintINFO(" " + Integer.toHexString(nr.getCoordinates()));

                D.ebugPrintlnINFO();
                D.ebugPrintINFO("   threats:");
                for (SOCPossiblePiece threat : ps.getThreats())
                    D.ebugPrintINFO(" " + threat.getPlayer().getPlayerNumber() + ":" + threat.getType() + ":"
                        + Integer.toHexString(threat.getCoordinates()));

                D.ebugPrintlnINFO();
            }

            Iterator<SOCPossibleCity> pcIter = tracker.getPossibleCities().values().iterator();
            while (pcIter.hasNext())
            {
                SOCPossibleCity pc = pcIter.next();
                D.ebugPrintlnINFO("%%% possible city at " + Integer.toHexString(pc.getCoordinates()));
                D.ebugPrintlnINFO("   eta:" + pc.getETA());
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
    public static void updateWinGameETAs(final SOCPlayerTracker[] playerTrackers)
    {
        for (final SOCPlayerTracker tracker : playerTrackers)
        {
            if (tracker == null)
                continue;

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
                System.err.println("Null Pointer Exception calculating winGameETA");
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
