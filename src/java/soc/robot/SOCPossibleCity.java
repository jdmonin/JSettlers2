/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file copyright (C) 2009,2012 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCCity;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;

import java.util.Vector;


/**
 * This is a possible city that we can build
 *
 * @author Robert S Thomas
 *
 */
public class SOCPossibleCity extends SOCPossiblePiece
{
    /**
     * Speedup per building type.  Indexed from {@link SOCBuildingSpeedEstimate#MIN}
     * to {@link SOCBuildingSpeedEstimate#MAXPLUSONE}.
     */
    protected int[] speedup = { 0, 0, 0, 0, 0 };

    /**
     * constructor
     *
     * @param pl  the owner
     * @param co  coordinates;
     */
    public SOCPossibleCity(SOCPlayer pl, int co)
    {
        pieceType = SOCPossiblePiece.CITY;
        player = pl;
        coord = co;
        eta = 0;
        threats = new Vector<SOCPossiblePiece>();
        biggestThreats = new Vector<SOCPossiblePiece>();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
        updateSpeedup();
    }

    /**
     * copy constructor
     *
     * Note: This will not copy vectors, only make empty ones
     *
     * @param pc  the possible city to copy
     */
    public SOCPossibleCity(SOCPossibleCity pc)
    {
        //D.ebugPrintln(">>>> Copying possible city: "+pc);
        pieceType = SOCPossiblePiece.CITY;
        player = pc.getPlayer();
        coord = pc.getCoordinates();
        eta = pc.getETA();
        threats = new Vector<SOCPossiblePiece>();
        biggestThreats = new Vector<SOCPossiblePiece>();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;

        int[] pcSpeedup = pc.getSpeedup();

        for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                buildingType++)
        {
            speedup[buildingType] = pcSpeedup[buildingType];
        }
    }

    /**
     * calculate the speedup that this city gives
     * @see #getSpeedup()
     */
    public void updateSpeedup()
    {
        //D.ebugPrintln("****************************** (CITY) updateSpeedup at "+Integer.toHexString(coord));
        SOCBuildingSpeedEstimate bse1 = new SOCBuildingSpeedEstimate(player.getNumbers());
        int[] ourBuildingSpeed = bse1.getEstimatesFromNothingFast(player.getPortFlags());
        SOCPlayerNumbers newNumbers = new SOCPlayerNumbers(player.getNumbers());
        newNumbers.updateNumbers(new SOCCity(player, coord, null), player.getGame().getBoard());

        SOCBuildingSpeedEstimate bse2 = new SOCBuildingSpeedEstimate(newNumbers);
        int[] speed = bse2.getEstimatesFromNothingFast(player.getPortFlags());

        for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                buildingType++)
        {
            //D.ebugPrintln("!@#$% ourBuildingSpeed[buildingType]="+ourBuildingSpeed[buildingType]+" speed[buildingType]="+speed[buildingType]);
            speedup[buildingType] = ourBuildingSpeed[buildingType] - speed[buildingType];
        }
    }

    /**
     * @return the speedup for this city
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
