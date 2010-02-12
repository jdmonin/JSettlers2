/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009-2010 Jeremy D Monin <jeremy@nand.net>
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
package soc.game;


/**
 * This is a list of constants for representing
 * types of resources in Settlers of Catan.
 *<P>
 * Warning: Many pieces of code depend on these values and their count.
 *          Clay is first (1), Wood is last (5), Unknown is after wood.
 *          Those are the 5 resource types (count==5 or ==6 (unknown) is also assumed).
 *          Adding a new resource type would require changes in many places.
 *          SOCRobotBrain.estimateResourceRarity is one of many examples.
 * Constants in other places (like {@link soc.message.SOCPlayerElement#CLAY})
 * have the same hardcoded values.
 *<P>
 * Before 1.1.08, this was an interface.  Changing to a class allowed adding
 * methods such as {@link #resName(int)}.
 *
 * @see SOCResourceSet
 */
public class SOCResourceConstants
{
    /**
     * Warning: Don't mess with these constants, other pieces
     *          of code depend on these numbers staying like this.
     *          Clay is first (1), Wood is last (5), Unknown is after wood.
     */
    public static final int CLAY = 1;
    public static final int ORE = 2;
    public static final int SHEEP = 3;
    public static final int WHEAT = 4;
    public static final int WOOD = 5;
    public static final int UNKNOWN = 6;
    public static final int MIN = 1;
    public static final int MAXPLUSONE = 7;

    /**
     * Get the resource type name for this resource type number,
     * such as "clay" or "ore".
     *
     * @param rtype Resource type, such as {@link #CLAY} or {@link #WOOD}.
     * @return Lowercase resource name, or null if rtype is out of range.
     * @since 1.1.08
     */
    public static String resName(int rtype)
    {
        String tname;
        switch (rtype)
        {
        case CLAY:
            tname = "clay";  break;
        case ORE:
            tname = "ore";   break;
        case SHEEP:
            tname = "sheep"; break;
        case WHEAT:
            tname = "wheat"; break;
        case WOOD:
            tname = "wood";  break;
        default:
            // Should not happen
            tname = null;            
        }
        return tname;
    }

    /**
     * Get the indefinite article of the resource type name for this number,
     * such as "a clay" or "an ore".
     *
     * @param rtype Resource type, such as {@link #CLAY} or {@link #SHEEP}.
     * @return Lowercase resource name, or "a null" if rtype is out of range
     *              ({@link #CLAY} - {@link #WOOD})
     * @since 1.1.08
     */
    public static String aResName(final int rtype)
    {
        StringBuffer sb = new StringBuffer();
        if (rtype == ORE)
            sb.append("an ");
        else
            sb.append("a ");
        sb.append(resName(rtype));
        return sb.toString();
    }
}
