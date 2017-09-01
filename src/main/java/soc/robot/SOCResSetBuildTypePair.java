/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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

import soc.game.SOCResourceSet;


/**
 * this class holds a SOCResourceSet and a building type
 */
public class SOCResSetBuildTypePair
{
    /**
     * the resource set
     */
    SOCResourceSet resources;

    /**
     * the building type
     */
    int building;

    /**
     * the constructor
     *
     * @param rs  the resource set
     * @param bt  the building type
     */
    public SOCResSetBuildTypePair(SOCResourceSet rs, int bt)
    {
        resources = rs;
        building = bt;
    }

    /**
     * @return the resource set
     */
    public SOCResourceSet getResources()
    {
        return resources;
    }

    /**
     * @return the building type
     */
    public int getBuildingType()
    {
        return building;
    }

    /**
     * @return a hashcode for this pair
     */
    public int hashCode()
    {
        String tmp = resources.toString() + building;

        return tmp.hashCode();
    }

    /**
     * @return true if the argument contains the same data
     *
     * @param anObject  the object in question
     */
    public boolean equals(Object anObject)
    {
        if ((anObject instanceof SOCResSetBuildTypePair) && (((SOCResSetBuildTypePair) anObject).getBuildingType() == building) && (((SOCResSetBuildTypePair) anObject).getResources().equals(resources)))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * @return a human readable form of this object
     */
    public String toString()
    {
        String str = "ResType:res=" + resources + "|type=" + building;

        return str;
    }
}
