/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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

import java.util.Stack;
import java.util.Vector;

import soc.game.SOCPlayer;


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
    protected int[] speedup = { 0, 0, 0, 0 };
    protected int numberOfNecessaryRoads;
    protected Stack roadPath;

    /**
     * constructor
     *
     * @param pl  the owner
     * @param co  coordinates;
     * @param nr  necessaryRoads;
     */
    public SOCPossibleSettlement(SOCPlayer pl, int co, Vector<SOCPossibleRoad> nr)
    {
        pieceType = SOCPossiblePiece.SETTLEMENT;
        player = pl;
        coord = co;
        necessaryRoads = nr;
        eta = 0;
        threats = new Vector();
        biggestThreats = new Vector();
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
        //log.debug(">>>> Copying possible settlement: "+ps);
        pieceType = SOCPossiblePiece.SETTLEMENT;
        player = ps.getPlayer();
        coord = ps.getCoordinates();
        necessaryRoads = new Vector<SOCPossibleRoad>(ps.getNecessaryRoads().size());
        eta = ps.getETA();
        threats = new Vector();
        biggestThreats = new Vector();
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
            roadPath = (Stack) ps.getRoadPath().clone();
        }
    }

    /**
     * @return the shortest road path to this settlement
     */
    Stack getRoadPath()
    {
        return roadPath;
    }

    /**
     * @param path  a stack containing the shortest road path to this settlement
     */
    void setRoadPath(Stack path)
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
     * update the speedup that this settlement gives
     */
    public void updateSpeedup()
    {
        /*
           log.debug("****************************** (SETTLEMENT) updateSpeedup at "+Integer.toHexString(coord));
           log.debug("SOCPN:"+player.getNumbers());
           D.ebugPrint("PFLAGS:");
           boolean portFlags[] = player.getPortFlags();
           if (log.isDebugEnabled()) {
             for (int port = SOCBoard.MISC_PORT; port <= SOCBoard.WOOD_PORT; port++) {
               D.ebugPrint(portFlags[port]+",");
             }
           }
           log.debug();
           SOCBuildingSpeedEstimate bse1 = new SOCBuildingSpeedEstimate(player.getNumbers());
           int ourBuildingSpeed[] = bse1.getEstimatesFromNothingFast(player.getPortFlags());
           //
           //  get new numbers
           //
           SOCPlayerNumbers newNumbers = new SOCPlayerNumbers(player.getNumbers());
           newNumbers.updateNumbers(coord, player.getGame().getBoard());
           log.debug("----- new numbers and ports -----");
           log.debug("SOCPN:"+newNumbers);
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
           log.debug();
           SOCBuildingSpeedEstimate bse2 = new SOCBuildingSpeedEstimate(newNumbers);
           int speed[] = bse2.getEstimatesFromNothingFast(newPortFlags);
           for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                buildingType++) {
             log.debug("!@#$% ourBuildingSpeed["+buildingType+"]="+ourBuildingSpeed[buildingType]+" speed["+buildingType+"]="+speed[buildingType]);
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
