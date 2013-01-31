/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file copyright (C) 2009,2012-2013 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Stack;
import java.util.Vector;


/**
 * This is a possible settlement that we can build
 *
 * @author Robert S Thomas
 *
 */
public class SOCPossibleSettlement extends SOCPossiblePiece
{
    protected Vector<SOCPossibleRoad> necessaryRoads;
    protected Vector<SOCPossibleSettlement> conflicts;

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
     * @param co  coordinates;
     * @param nr  necessaryRoads;
     */
    public SOCPossibleSettlement(SOCPlayer pl, int co, Vector<SOCPossibleRoad> nr)
    {
        if (nr == null)
            throw new IllegalArgumentException("nr null");
        pieceType = SOCPossiblePiece.SETTLEMENT;
        player = pl;
        coord = co;
        necessaryRoads = nr;
        eta = 0;
        threats = new Vector<SOCPossiblePiece>();
        biggestThreats = new Vector<SOCPossiblePiece>();
        conflicts = new Vector<SOCPossibleSettlement>();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
        numberOfNecessaryRoads = -1;
        roadPath = null;

        updateSpeedup();
    }

    /**
     * copy constructor
     *
     * Note: This will not copy vectors, only make empty ones
     *
     * @param ps  the possible settlement to copy
     */
    public SOCPossibleSettlement(SOCPossibleSettlement ps)
    {
        //D.ebugPrintln(">>>> Copying possible settlement: "+ps);
        pieceType = SOCPossiblePiece.SETTLEMENT;
        player = ps.getPlayer();
        coord = ps.getCoordinates();
        necessaryRoads = new Vector<SOCPossibleRoad>(ps.getNecessaryRoads().size());
        eta = ps.getETA();
        threats = new Vector<SOCPossiblePiece>();
        biggestThreats = new Vector<SOCPossiblePiece>();
        conflicts = new Vector<SOCPossibleSettlement>(ps.getConflicts().size());
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
        {
            roadPath = null;
        }
        else
        {
            roadPath = (Stack<SOCPossibleRoad>) ps.getRoadPath().clone();
        }
    }

    /**
     * Get the shortest road path to this settlement; some bots don't use this.
     * See {@link #setRoadPath(Stack)} for details.
     * @return the shortest road path to this settlement
     */
    Stack<SOCPossibleRoad> getRoadPath()
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
    void setRoadPath(Stack<SOCPossibleRoad> path)
    {
        roadPath = path;
    }

    /**
     * @return the list of necessary roads
     */
    public Vector<SOCPossibleRoad> getNecessaryRoads()
    {
        return necessaryRoads;
    }

    /**
     * @return the minimum number of necessary roads
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
           D.ebugPrintln("****************************** (SETTLEMENT) updateSpeedup at "+Integer.toHexString(coord));
           D.ebugPrintln("SOCPN:"+player.getNumbers());
           D.ebugPrint("PFLAGS:");
           boolean portFlags[] = player.getPortFlags();
           if (D.ebugOn) {
             for (int port = SOCBoard.MISC_PORT; port <= SOCBoard.WOOD_PORT; port++) {
               D.ebugPrint(portFlags[port]+",");
             }
           }
           D.ebugPrintln();
           SOCBuildingSpeedEstimate bse1 = new SOCBuildingSpeedEstimate(player.getNumbers());
           int ourBuildingSpeed[] = bse1.getEstimatesFromNothingFast(player.getPortFlags());
           //
           //  get new numbers
           //
           SOCPlayerNumbers newNumbers = new SOCPlayerNumbers(player.getNumbers());
           newNumbers.updateNumbers(coord, player.getGame().getBoard());
           D.ebugPrintln("----- new numbers and ports -----");
           D.ebugPrintln("SOCPN:"+newNumbers);
           D.ebugPrint("PFLAGS:");
           //
           //  get new ports
           //
           Integer coordInteger = new Integer(this.getCoordinates());
           boolean newPortFlags[] = new boolean[SOCBoard.WOOD_PORT+1];
           for (int port = SOCBoard.MISC_PORT; port <= SOCBoard.WOOD_PORT; port++) {
             newPortFlags[port] = player.getPortFlag(port);
             if (player.getGame().getBoard().getPortCoordinates(port).contains(coordInteger)) {
               newPortFlags[port] = true;
             }
             D.ebugPrint(portFlags[port]+",");
           }
           D.ebugPrintln();
           SOCBuildingSpeedEstimate bse2 = new SOCBuildingSpeedEstimate(newNumbers);
           int speed[] = bse2.getEstimatesFromNothingFast(newPortFlags);
           for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                buildingType++) {
             D.ebugPrintln("!@#$% ourBuildingSpeed["+buildingType+"]="+ourBuildingSpeed[buildingType]+" speed["+buildingType+"]="+speed[buildingType]);
             speedup[buildingType] = ourBuildingSpeed[buildingType] - speed[buildingType];
           }
         */
    }

    /**
     * @return the list of conflicting settlements
     */
    public Vector<SOCPossibleSettlement> getConflicts()
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
        necessaryRoads.addElement(rd);
    }

    /**
     * add a conflicting settlement
     *
     * @param s  the settlement
     */
    public void addConflict(SOCPossibleSettlement s)
    {
        conflicts.addElement(s);
    }

    /**
     * remove a conflicting settlement
     *
     * @param s  the settlement
     */
    public void removeConflict(SOCPossibleSettlement s)
    {
        conflicts.removeElement(s);
    }

    /**
     * @return the speedup for this settlement
     */
    public int[] getSpeedup()
    {
        return speedup;
    }

    /**
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
