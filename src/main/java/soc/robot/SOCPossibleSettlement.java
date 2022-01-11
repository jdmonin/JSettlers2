/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file copyright (C) 2009,2012-2015,2018,2020-2021 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;


/**
 * This is a possible settlement that we can build
 *<P>
 * If serializing and deserializing this piece, remember the Player and
 * {@link SOCBuildingSpeedEstimateFactory} fields will be null when deserialized:
 * Call {@link SOCPossiblePiece#setTransientsAtLoad(SOCPlayer, SOCPlayerTracker)} to set them.
 *
 * @author Robert S Thomas
 *
 */
public class SOCPossibleSettlement extends SOCPossiblePiece
{
    /** Last structural change v2.0.00 (2000) */
    private static final long serialVersionUID = 2000L;

    protected List<SOCPossibleRoad> necessaryRoads;
    protected List<SOCPossibleSettlement> conflicts;

    /**
     * Speedup per building type.  Indexed from {@link SOCBuildingSpeedEstimate#MIN}
     * to {@link SOCBuildingSpeedEstimate#MAXPLUSONE}.
     */
    protected int[] speedup = { 0, 0, 0, 0, 0 };

    protected int numberOfNecessaryRoads;
    protected Stack<SOCPossibleRoad> roadPath;

    /**
     * constructor
     *
     * @param pl  the owner
     * @param co  coordinates; not validated
     * @param nr  necessaryRoads list reference to use (not to copy!),
     *     or {@code null} to create a new empty list here
     * @param bseFactory  factory to use for {@link SOCBuildingSpeedEstimate}
     *     in {@link #updateSpeedup()} calls; not null
     */
    public SOCPossibleSettlement
        (SOCPlayer pl, int co, List<SOCPossibleRoad> nr, SOCBuildingSpeedEstimateFactory bseFactory)
    {
        super(SOCPossiblePiece.SETTLEMENT, pl, co, bseFactory);

        if (nr == null)
            nr = new ArrayList<SOCPossibleRoad>();
        necessaryRoads = nr;
        eta = 0;
        conflicts = new ArrayList<SOCPossibleSettlement>();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
        numberOfNecessaryRoads = -1;
        roadPath = null;

        updateSpeedup();
    }

    /**
     * copy constructor.
     *
     * Note: This will not copy lists of threats, necessaryRoads, and conflicts, only make empty ones.
     *
     * @param ps  the possible settlement to copy
     */
    @SuppressWarnings("unchecked")
    public SOCPossibleSettlement(final SOCPossibleSettlement ps)
    {
        //D.ebugPrintln(">>>> Copying possible settlement: "+ps);
        super(SOCPossiblePiece.SETTLEMENT, ps.getPlayer(), ps.getCoordinates(), ps.bseFactory);

        necessaryRoads = new ArrayList<SOCPossibleRoad>(ps.getNecessaryRoads().size());
        eta = ps.getETA();
        conflicts = new ArrayList<SOCPossibleSettlement>(ps.getConflicts().size());
        threatUpdatedFlag = false;
        hasBeenExpanded = false;

        int[] psSpeedup = ps.getSpeedup();
        for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                buildingType++)
        {
            speedup[buildingType] = psSpeedup[buildingType];
        }

        numberOfNecessaryRoads = ps.getNumberOfNecessaryRoads();

        if (ps.getRoadPath() == null)
            roadPath = null;
        else
            roadPath = (Stack<SOCPossibleRoad>) ps.getRoadPath().clone();
    }

    /**
     * Get the shortest road path to this settlement; some bots don't use this.
     * See {@link #setRoadPath(Stack)} for details.
     * @return the shortest road path to this settlement
     */
    public Stack<SOCPossibleRoad> getRoadPath()
    {
        return roadPath;
    }

    /**
     * Shortest road/ship path to this settlement.
     * Calculated from {@link #getNecessaryRoads()} by
     * {@link SOCRobotDM#scoreSettlementsForDumb(int, SOCBuildingSpeedEstimate)}.
     * The bots with {@link SOCRobotDM#SMART_STRATEGY} won't calculate this;
     * instead, they pick roads/ships with 0 {@link #getNecessaryRoads()},
     * and iteratively simulate building other things after picking such a road.
     * @param path  a stack containing the shortest road path to this settlement
     */
    public void setRoadPath(Stack<SOCPossibleRoad> path)
    {
        roadPath = path;
    }

    /**
     * Get this possible settlement's list of necessary roads, from
     * constructor and/or {@link #addNecessaryRoad(SOCPossibleRoad)}.
     * @return the list of necessary roads
     * @see #getNumberOfNecessaryRoads()
     */
    public List<SOCPossibleRoad> getNecessaryRoads()
    {
        return necessaryRoads;
    }

    /**
     * @return the minimum number of necessary roads,
     *     which may not necessarily be the length of {@link #getNecessaryRoads()}
     */
    public int getNumberOfNecessaryRoads()
    {
        return numberOfNecessaryRoads;
    }

    /**
     * set the minimum number of necessary roads
     *
     * @param num  the minimum number of necessary roads
     */
    public void setNumberOfNecessaryRoads(int num)
    {
        numberOfNecessaryRoads = num;
    }

    /**
     * update the speedup that this settlement gives.
     * This has been a do-nothing method (all code commented out) since March 2004 or earlier.
     */
    public void updateSpeedup()
    {
        /*
           D.ebugPrintlnINFO("****************************** (SETTLEMENT) updateSpeedup at "+Integer.toHexString(coord));
           D.ebugPrintlnINFO("SOCPN:"+player.getNumbers());
           D.ebugPrintINFO("PFLAGS:");
           boolean portFlags[] = player.getPortFlags();
           if (D.ebugOn) {
             for (int port = SOCBoard.MISC_PORT; port <= SOCBoard.WOOD_PORT; port++) {
               D.ebugPrintINFO(portFlags[port]+",");
             }
           }
           D.ebugPrintlnINFO();
           SOCBuildingSpeedEstimate bse1 = bseFactory.getEstimator(player.getNumbers());
           int ourBuildingSpeed[] = bse1.getEstimatesFromNothingFast(player.getPortFlags());
           //
           //  get new numbers
           //
           SOCPlayerNumbers newNumbers = new SOCPlayerNumbers(player.getNumbers());
           newNumbers.updateNumbers(coord, player.getGame().getBoard());
           D.ebugPrintlnINFO("----- new numbers and ports -----");
           D.ebugPrintlnINFO("SOCPN:"+newNumbers);
           D.ebugPrintINFO("PFLAGS:");
           //
           //  get new ports
           //
           Integer coordInteger = Integer.valueOf(this.getCoordinates());
           boolean newPortFlags[] = new boolean[SOCBoard.WOOD_PORT+1];
           for (int port = SOCBoard.MISC_PORT; port <= SOCBoard.WOOD_PORT; port++) {
             newPortFlags[port] = player.getPortFlag(port);
             if (player.getGame().getBoard().getPortCoordinates(port).contains(coordInteger)) {
               newPortFlags[port] = true;
             }
             D.ebugPrintINFO(portFlags[port]+",");
           }
           D.ebugPrintlnINFO();
           SOCBuildingSpeedEstimate bse2 = bseFactory.getEstimator(newNumbers);
           int speed[] = bse2.getEstimatesFromNothingFast(newPortFlags);
           for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                buildingType++) {
             D.ebugPrintlnINFO("!@#$% ourBuildingSpeed["+buildingType+"]="+ourBuildingSpeed[buildingType]+" speed["+buildingType+"]="+speed[buildingType]);
             speedup[buildingType] = ourBuildingSpeed[buildingType] - speed[buildingType];
           }
         */
    }

    /**
     * @return the list of conflicting settlements
     */
    public List<SOCPossibleSettlement> getConflicts()
    {
        return conflicts;
    }

    /**
     * add a possible road to the list of necessary roads
     *
     * @param rd  the road
     */
    public void addNecessaryRoad(SOCPossibleRoad rd)
    {
        necessaryRoads.add(rd);
    }

    /**
     * add a conflicting settlement
     *
     * @param s  the settlement
     */
    public void addConflict(SOCPossibleSettlement s)
    {
        conflicts.add(s);
    }

    /**
     * remove a conflicting settlement
     *
     * @param s  the settlement
     */
    public void removeConflict(SOCPossibleSettlement s)
    {
        conflicts.remove(s);
    }

    /**
     * @return the speedup for this settlement
     */
    public int[] getSpeedup()
    {
        return speedup;
    }

    /**
     * Get the total speedup from this settlement.  Settlement speedup is currently not used, always 0.
     * @return the sum of all of the speedup numbers
     */
    public int getSpeedupTotal()
    {
        int sum = 0;

        for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                buildingType++)
        {
            sum += speedup[buildingType];
        }

        return sum;
    }

}
