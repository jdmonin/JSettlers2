/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2011,2013-2014 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
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

import java.util.Vector;


/**
 * This is a possible ship that we can build.
 * Note that it's a subclass of {@link SOCPossibleRoad}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCPossibleShip extends SOCPossibleRoad
{
    /** True if this is a coastal edge that could possibly be a road or a ship */
    public final boolean isCoastalRoadAndShip;

    /**
     * constructor
     *
     * @param pl  the owner
     * @param co  coordinates
     * @param isCoastal  Is this also a possible coastal road ({@link #isCoastalRoadAndShip})?
     * @param nr  necessaryRoads, or {@code null} to create a new empty Vector here
     */
    public SOCPossibleShip(SOCPlayer pl, int co, final boolean isCoastal, Vector<SOCPossibleRoad> nr)
    {
        super(pl, co, nr);
        pieceType = SOCPossiblePiece.SHIP;
        isCoastalRoadAndShip = isCoastal;
    }

    /**
     * copy constructor
     *
     * Note: This will not copy the vectors, just make empty ones
     *
     * @param pr  the possible road to copy
     */
    public SOCPossibleShip(SOCPossibleShip pr)
    {
        super(pr);
        pieceType = SOCPossiblePiece.SHIP;
        isCoastalRoadAndShip = pr.isCoastalRoadAndShip;
    }

}
