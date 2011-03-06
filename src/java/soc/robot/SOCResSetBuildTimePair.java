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
public class SOCResSetBuildTimePair
{
    /**
     * the resource set
     */
    SOCResourceSet resources;

    /**
     * number of rolls
     */
    int rolls;

    /**
     * the constructor
     *
     * @param rs  the resource set
     * @param bt  the building time
     */
    public SOCResSetBuildTimePair(SOCResourceSet rs, int bt)
    {
        resources = rs;
        rolls = bt;
    }

    /**
     * @return the resource set
     */
    public SOCResourceSet getResources()
    {
        return resources;
    }

    /**
     * @return the building time
     */
    public int getRolls()
    {
        return rolls;
    }

    /**
     * @return a hashcode for this pair
     */
    public int hashCode()
    {
        String tmp = resources.toString() + rolls;

        return tmp.hashCode();
    }

    /**
     * @return true if the argument contains the same data
     *
     * @param anObject  the object in question
     */
    public boolean equals(Object anObject)
    {
        if ((anObject instanceof SOCResSetBuildTimePair) && (((SOCResSetBuildTimePair) anObject).getRolls() == rolls) && (((SOCResSetBuildTimePair) anObject).getResources().equals(resources)))
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
        String str = "ResTime:res=" + resources + "|rolls=" + rolls;

        return str;
    }
}
