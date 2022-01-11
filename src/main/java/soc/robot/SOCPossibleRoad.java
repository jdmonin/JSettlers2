/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2011-2015,2018,2020-2021 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.game.SOCShip;

import java.util.ArrayList;
import java.util.List;


/**
 * This is a possible road that we can build.
 * Note that {@link SOCPossibleShip} is a subclass,
 * unlike {@link SOCRoad}/{@link SOCShip} which are siblings.
 *
 * @author Robert S Thomas
 */
public class SOCPossibleRoad extends SOCPossiblePiece
{
    /** Last structural change v2.0.00 (2000) */
    private static final long serialVersionUID = 2000L;

    protected final List<SOCPossibleRoad> necessaryRoads;
    protected final List<SOCPossiblePiece> newPossibilities;
    protected int longestRoadValue;
    protected int longestRoadPotential;
    protected int numberOfNecessaryRoads;

    /**
     * constructor
     *
     * @param pl  the owner
     * @param co  coordinates; not validated
     * @param nr  necessaryRoads list reference to use (not to copy!), or {@code null} to create a new empty list here
     */
    public SOCPossibleRoad(SOCPlayer pl, int co, List<SOCPossibleRoad> nr)
    {
        super(SOCPossiblePiece.ROAD, pl, co);

        if (nr == null)
            nr = new ArrayList<SOCPossibleRoad>();
        necessaryRoads = nr;
        eta = 0;
        newPossibilities = new ArrayList<SOCPossiblePiece>();
        longestRoadValue = 0;
        longestRoadPotential = 0;
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
        numberOfNecessaryRoads = -1;
    }

    /**
     * copy constructor.
     *
     * Note: This will not copy {@code pr}'s lists, only make empty ones.
     *
     * @param pr  the possible road to copy
     */
    public SOCPossibleRoad(SOCPossibleRoad pr)
    {
        //D.ebugPrintln(">>>> Copying possible road: "+pr);
        super(SOCPossiblePiece.ROAD, pr.getPlayer(), pr.getCoordinates());

        necessaryRoads = new ArrayList<SOCPossibleRoad>(pr.getNecessaryRoads().size());
        eta = pr.getETA();
        newPossibilities = new ArrayList<SOCPossiblePiece>(pr.getNewPossibilities().size());
        longestRoadValue = pr.getLRValue();
        longestRoadPotential = pr.getLRPotential();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
        numberOfNecessaryRoads = pr.getNumberOfNecessaryRoads();
    }

    /**
     * Get this possible road/ship's list of necessary roads, from
     * constructor and/or {@link #addNecessaryRoad(SOCPossibleRoad)}.
     * @return the list of necessary roads or ships
     */
    public List<SOCPossibleRoad> getNecessaryRoads()
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
     * Get the list of any possibilities added by {@link #addNewPossibility(SOCPossiblePiece)}.
     * @return the list of new possibilities
     */
    public List<SOCPossiblePiece> getNewPossibilities()
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
        necessaryRoads.add(rd);
    }

    /**
     * add a new possibility to the list
     *
     * @param piece  the new possible piece
     */
    public void addNewPossibility(SOCPossiblePiece piece)
    {
        newPossibilities.add(piece);
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
     * Is this piece really a road on land, and not a ship on water (subclass {@link SOCPossibleShip})?
     * @return True for roads (pieceType {@link SOCPlayingPiece#ROAD}), false otherwise
     * @since 2.0.00
     */
    public final boolean isRoadNotShip()
    {
        return (pieceType == SOCPlayingPiece.ROAD);
    }

}
