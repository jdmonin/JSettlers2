/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2011-2012 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;

import java.util.Vector;


/**
 * This is a possible road that we can build.
 * Note that {@link SOCPossibleShip} is a subclass.
 *
 * @author Robert S Thomas
 */
public class SOCPossibleRoad extends SOCPossiblePiece
{
    protected Vector necessaryRoads;
    protected Vector newPossibilities;
    protected int longestRoadValue;
    protected int longestRoadPotential;
    protected int numberOfNecessaryRoads;

    /**
     * constructor
     *
     * @param pl  the owner
     * @param co  coordinates
     * @param nr  necessaryRoads
     */
    public SOCPossibleRoad(SOCPlayer pl, int co, Vector nr)
    {
        pieceType = SOCPossiblePiece.ROAD;
        player = pl;
        coord = co;
        necessaryRoads = nr;
        eta = 0;
        threats = new Vector();
        biggestThreats = new Vector();
        newPossibilities = new Vector();
        longestRoadValue = 0;
        longestRoadPotential = 0;
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
        numberOfNecessaryRoads = -1;
    }

    /**
     * copy constructor
     *
     * Note: This will not copy the vectors, just make empty ones
     *
     * @param pr  the possible road to copy
     */
    public SOCPossibleRoad(SOCPossibleRoad pr)
    {
        //D.ebugPrintln(">>>> Copying possible road: "+pr);
        pieceType = SOCPossiblePiece.ROAD;
        player = pr.getPlayer();
        coord = pr.getCoordinates();
        necessaryRoads = new Vector(pr.getNecessaryRoads().size());
        eta = pr.getETA();
        threats = new Vector();
        biggestThreats = new Vector();
        newPossibilities = new Vector(pr.getNewPossibilities().size());
        longestRoadValue = pr.getLRValue();
        longestRoadPotential = pr.getLRPotential();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
        numberOfNecessaryRoads = pr.getNumberOfNecessaryRoads();
    }

    /**
     * @return the list of necessary roads or ships
     */
    public Vector getNecessaryRoads()
    {
        return necessaryRoads;
    }

    /**
     * Get the minimum number of necessary roads and/or ships.
     * Note that for routes with both roads and ships,
     * this will be +2 for every coastal-settlement
     * transition between roads and ships, for the
     * effort of building the settlement.
     *
     * @return the minimum number of necessary roads or ships
     */
    public int getNumberOfNecessaryRoads()
    {
        return numberOfNecessaryRoads;
    }

    /**
     * Set the minimum number of necessary roads and/or ships.
     * Note that for routes with both roads and ships,
     * this will be +2 for every coastal-settlement
     * transition between roads and ships, for the
     * effort of building the settlement.
     *
     * @param num  the minimum number of necessary roads
     */
    public void setNumberOfNecessaryRoads(int num)
    {
        numberOfNecessaryRoads = num;
    }

    /**
     * @return the list of new possibilities
     */
    public Vector getNewPossibilities()
    {
        return newPossibilities;
    }

    /**
     * @return the longestRoadValue
     */
    public int getLRValue()
    {
        return longestRoadValue;
    }

    /**
     * @return the longestRoadPotential
     */
    public int getLRPotential()
    {
        return longestRoadPotential;
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
     * add a new possibility to the list
     *
     * @param piece  the new possible piece
     */
    public void addNewPossibility(SOCPossiblePiece piece)
    {
        newPossibilities.addElement(piece);
    }

    /**
     * set the longest road value
     *
     * @param value
     */
    public void setLRValue(int value)
    {
        longestRoadValue = value;
    }

    /**
     * set the longest road potential
     *
     * @param value
     */
    public void setLRPotential(int value)
    {
        longestRoadPotential = value;
    }

    /**
     * Is this piece really a road on land, and not a ship on water (our subclass)?
     * @return True for roads (pieceType {@link SOCPlayingPiece#ROAD}), false otherwise
     * @since 2.0.00
     */
    public final boolean isRoadNotShip()
    {
        return (pieceType == SOCPlayingPiece.ROAD);
    }

}
