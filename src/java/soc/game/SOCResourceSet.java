/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2008 Jeremy Monin <jeremy@nand.net>
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

import java.io.Serializable;


/**
 * This represents a collection of
 * clay, ore, sheep, wheat, and wood resources.
 * Unknown resources are also tracked here.
 * Although it's possible to store negative amounts of resources, it's discouraged.
 */
public class SOCResourceSet implements Serializable, Cloneable
{
    /** Resource set with zero of each resource type */
    public static final SOCResourceSet EMPTY_SET = new SOCResourceSet();

    /**
     * the number of resources
     */
    private int[] resources;

    /**
     * Make an empty resource set
     */
    public SOCResourceSet()
    {
        resources = new int[SOCResourceConstants.MAXPLUSONE];
        clear();
    }

    /**
     * Make a resource set with stuff in it
     *
     * @param cl  number of clay resources
     * @param or  number of ore resources
     * @param sh  number of sheep resources
     * @param wh  number of wheat resources
     * @param wo  number of wood resources
     * @param uk  number of unknown resources
     */
    public SOCResourceSet(int cl, int or, int sh, int wh, int wo, int uk)
    {
        resources = new int[SOCResourceConstants.MAXPLUSONE];

        resources[SOCResourceConstants.CLAY]    = cl;
        resources[SOCResourceConstants.ORE]     = or;
        resources[SOCResourceConstants.SHEEP]   = sh;
        resources[SOCResourceConstants.WHEAT]   = wh;
        resources[SOCResourceConstants.WOOD]    = wo;
        resources[SOCResourceConstants.UNKNOWN] = uk;
    }

    /**
     * Make a resource set from an array
     *
     * @param rset resource set, of length 5 or 6 (clay, ore, sheep, wheat, wood, unknown).
     *     If length is 5, unknown == 0.
     * @since 1.1.08
     */
    public SOCResourceSet(int[] rset)
    {
        this(rset[0], rset[1], rset[2], rset[3], rset[4], (rset.length >= 6) ? rset[5] : 0);
    }

    /**
     * set the number of resources to zero
     */
    public void clear()
    {
        for (int i = SOCResourceConstants.MIN;
                i < SOCResourceConstants.MAXPLUSONE; i++)
        {
            resources[i] = 0;
        }
    }

    /**
     * @return the number of a kind of resource
     *
     * @param rtype  the type of resource, like {@link SOCResourceConstants#CLAY}
     */
    public int getAmount(int rtype)
    {
        return resources[rtype];
    }

    /**
     * @return the total number of resources
     */
    public int getTotal()
    {
        int sum = 0;

        for (int i = SOCResourceConstants.MIN;
                i < SOCResourceConstants.MAXPLUSONE; i++)
        {
            sum += resources[i];
        }

        return sum;
    }

    /**
     * set the amount of a resource
     *
     * @param rtype the type of resource, like {@link SOCResourceConstants#CLAY}
     * @param amt   the amount
     */
    public void setAmount(int amt, int rtype)
    {
        resources[rtype] = amt;
    }

    /**
     * add an amount to a resource
     *
     * @param rtype the type of resource, like {@link SOCResourceConstants#CLAY}
     * @param amt   the amount; if below 0 (thus subtracting resources),
     *              the subtraction occurs and no special action is taken.
     *              {@link #subtract(int, int)} takes special action in some cases.
     */
    public void add(int amt, int rtype)
    {
        resources[rtype] += amt;
    }

    /**
     * subtract an amount from a resource.
     * If we're subtracting more from a resource than there are of that resource,
     * set that resource to zero, and then take the difference away from the
     * {@link SOCResourceConstants#UNKNOWN} resources.
     * As a result, UNKNOWN may be less than zero afterwards.
     *
     * @param rtype the type of resource, like {@link SOCResourceConstants#CLAY}
     * @param amt   the amount; unlike in {@link #add(int, int)}, any amount that
     *              takes the resource below 0 is treated specially.
     */
    public void subtract(int amt, int rtype)
    {
        /**
         * if we're subtracting more from a resource than
         * there are of that resource, set that resource
         * to zero, and then take the difference away
         * from the UNKNOWN resources
         */
        if (amt > resources[rtype])
        {
            resources[SOCResourceConstants.UNKNOWN] -= (amt - resources[rtype]);
            resources[rtype] = 0;
        }
        else
        {
            resources[rtype] -= amt;
        }

        if (resources[SOCResourceConstants.UNKNOWN] < 0)
        {
            System.err.println("RESOURCE < 0 : RESOURCE TYPE=" + rtype);
        }
    }

    /**
     * add an entire resource set's amounts into this set.
     *
     * @param rs  the resource set
     */
    public void add(SOCResourceSet rs)
    {
        resources[SOCResourceConstants.CLAY]    += rs.getAmount(SOCResourceConstants.CLAY);
        resources[SOCResourceConstants.ORE]     += rs.getAmount(SOCResourceConstants.ORE);
        resources[SOCResourceConstants.SHEEP]   += rs.getAmount(SOCResourceConstants.SHEEP);
        resources[SOCResourceConstants.WHEAT]   += rs.getAmount(SOCResourceConstants.WHEAT);
        resources[SOCResourceConstants.WOOD]    += rs.getAmount(SOCResourceConstants.WOOD);
        resources[SOCResourceConstants.UNKNOWN] += rs.getAmount(SOCResourceConstants.UNKNOWN);
    }

    /**
     * subtract an entire resource set. If any type's amount would go below 0, set it to 0.
     *
     * @param rs  the resource set
     */
    public void subtract(SOCResourceSet rs)
    {
        resources[SOCResourceConstants.CLAY] -= rs.getAmount(SOCResourceConstants.CLAY);

        if (resources[SOCResourceConstants.CLAY] < 0)
        {
            resources[SOCResourceConstants.CLAY] = 0;
        }

        resources[SOCResourceConstants.ORE] -= rs.getAmount(SOCResourceConstants.ORE);

        if (resources[SOCResourceConstants.ORE] < 0)
        {
            resources[SOCResourceConstants.ORE] = 0;
        }

        resources[SOCResourceConstants.SHEEP] -= rs.getAmount(SOCResourceConstants.SHEEP);

        if (resources[SOCResourceConstants.SHEEP] < 0)
        {
            resources[SOCResourceConstants.SHEEP] = 0;
        }

        resources[SOCResourceConstants.WHEAT] -= rs.getAmount(SOCResourceConstants.WHEAT);

        if (resources[SOCResourceConstants.WHEAT] < 0)
        {
            resources[SOCResourceConstants.WHEAT] = 0;
        }

        resources[SOCResourceConstants.WOOD] -= rs.getAmount(SOCResourceConstants.WOOD);

        if (resources[SOCResourceConstants.WOOD] < 0)
        {
            resources[SOCResourceConstants.WOOD] = 0;
        }

        resources[SOCResourceConstants.UNKNOWN] -= rs.getAmount(SOCResourceConstants.UNKNOWN);

        if (resources[SOCResourceConstants.UNKNOWN] < 0)
        {
            resources[SOCResourceConstants.UNKNOWN] = 0;
        }
    }

    /**
     * Convert all these resources to type {@link SOCResourceConstants#UNKNOWN}.
     * Information on amount of wood, wheat, etc is no longer available.
     * Equivalent to:
     * <code>
     *    int numTotal = resSet.getTotal();
     *    resSet.clear();
     *    resSet.setAmount (SOCResourceConstants.UNKNOWN, numTotal);
     * </code>
     */
    public void convertToUnknown()
    {
        int numTotal = getTotal();
        clear();
        resources[SOCResourceConstants.UNKNOWN] = numTotal;
    }

    /**
     * @return true if each resource type in set A is >= each resource type in set B
     *
     * @param a   set A
     * @param b   set B
     */
    static public boolean gte(SOCResourceSet a, SOCResourceSet b)
    {
        return (   (a.getAmount(SOCResourceConstants.CLAY)    >= b.getAmount(SOCResourceConstants.CLAY))
                && (a.getAmount(SOCResourceConstants.ORE)     >= b.getAmount(SOCResourceConstants.ORE))
                && (a.getAmount(SOCResourceConstants.SHEEP)   >= b.getAmount(SOCResourceConstants.SHEEP))
                && (a.getAmount(SOCResourceConstants.WHEAT)   >= b.getAmount(SOCResourceConstants.WHEAT))
                && (a.getAmount(SOCResourceConstants.WOOD)    >= b.getAmount(SOCResourceConstants.WOOD))
                && (a.getAmount(SOCResourceConstants.UNKNOWN) >= b.getAmount(SOCResourceConstants.UNKNOWN)));
    }

    /**
     * @return true if each resource type in set A is <= each resource type in set B
     *
     * @param a   set A
     * @param b   set B
     */
    static public boolean lte(SOCResourceSet a, SOCResourceSet b)
    {
        return (   (a.getAmount(SOCResourceConstants.CLAY)    <= b.getAmount(SOCResourceConstants.CLAY))
                && (a.getAmount(SOCResourceConstants.ORE)     <= b.getAmount(SOCResourceConstants.ORE))
                && (a.getAmount(SOCResourceConstants.SHEEP)   <= b.getAmount(SOCResourceConstants.SHEEP))
                && (a.getAmount(SOCResourceConstants.WHEAT)   <= b.getAmount(SOCResourceConstants.WHEAT))
                && (a.getAmount(SOCResourceConstants.WOOD)    <= b.getAmount(SOCResourceConstants.WOOD))
                && (a.getAmount(SOCResourceConstants.UNKNOWN) <= b.getAmount(SOCResourceConstants.UNKNOWN)));
    }

    /**
     * Human-readable form of the set, with format "clay=5|ore=1|sheep=0|wheat=0|wood=3|unknown=0"
     * @return a human readable longer form of the set
     * @see #toShortString()
     * @see #toFriendlyString()
     */
    public String toString()
    {
        String s = "clay=" + resources[SOCResourceConstants.CLAY]
            + "|ore=" + resources[SOCResourceConstants.ORE]
            + "|sheep=" + resources[SOCResourceConstants.SHEEP]
            + "|wheat=" + resources[SOCResourceConstants.WHEAT]
            + "|wood=" + resources[SOCResourceConstants.WOOD]
            + "|unknown=" + resources[SOCResourceConstants.UNKNOWN];

        return s;
    }

    /**
     * Human-readable form of the set, with format "Resources: 5 1 0 0 3 0".
     * Order of types is Clay, ore, sheep, wheat, wood, unknown.
     * @return a human readable short form of the set
     * @see #toFriendlyString()
     */
    public String toShortString()
    {
        String s = "Resources: " + resources[SOCResourceConstants.CLAY] + " "
            + resources[SOCResourceConstants.ORE] + " "
            + resources[SOCResourceConstants.SHEEP] + " "
            + resources[SOCResourceConstants.WHEAT] + " "
            + resources[SOCResourceConstants.WOOD] + " "
            + resources[SOCResourceConstants.UNKNOWN];

        return s;
    }

    /**
     * Human-readable form of the set, with format "5 clay,1 ore,3 wood"
     * @return a human readable longer form of the set;
     *         if the set is empty, return the string "nothing".
     * @see #toShortString()
     */
    public String toFriendlyString()
    {
        StringBuffer sb = new StringBuffer();
        if (toFriendlyString(sb))
            return sb.toString();
        else
            return "nothing";
    }

    /**
     * Human-readable form of the set, with format "5 clay, 1 ore, 3 wood"
     * @param sb Append into this buffer.
     * @return true if anything was appended, false if sb unchanged (this resource set is empty).
     * @see #toFriendlyString()
     */
    public boolean toFriendlyString(StringBuffer sb)
    {
        boolean needComma = false;  // Has a resource already been appended to sb?
        int amt;
        
        amt = resources[SOCResourceConstants.CLAY];
        if (amt > 0)
        {
            sb.append(amt);
            sb.append(" clay");
            needComma = true;
        }

        amt = resources[SOCResourceConstants.ORE];
        if (amt > 0)
        {
            if (needComma)
                sb.append(", ");
            sb.append(amt);
            sb.append(" ore");
            needComma = true;
        }

        amt = resources[SOCResourceConstants.SHEEP];
        if (amt > 0)
        {
            if (needComma)
                sb.append(", ");
            sb.append(amt);
            sb.append(" sheep");
            needComma = true;
        }

        amt = resources[SOCResourceConstants.WHEAT];
        if (amt > 0)
        {
            if (needComma)
                sb.append(", ");
            sb.append(amt);
            sb.append(" wheat");
            needComma = true;
        }

        amt = resources[SOCResourceConstants.WOOD];
        if (amt > 0)
        {
            if (needComma)
                sb.append(", ");
            sb.append(amt);
            sb.append(" wood");
            needComma = true;  // signal for return code
        }

        return needComma;  // Did we append anything?
    }

    /**
     * @return true if sub is in this set
     *
     * @param sub  the sub set
     */
    public boolean contains(SOCResourceSet sub)
    {
        return gte(this, sub);
    }

    /**
     * @return true if the argument is a SOCResourceSet containing the same amounts of each resource, including UNKNOWN
     *
     * @param anObject  the object in question
     */
    public boolean equals(Object anObject)
    {
        if ((anObject instanceof SOCResourceSet)
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.CLAY)    == resources[SOCResourceConstants.CLAY])
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.ORE)     == resources[SOCResourceConstants.ORE])
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.SHEEP)   == resources[SOCResourceConstants.SHEEP])
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.WHEAT)   == resources[SOCResourceConstants.WHEAT])
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.WOOD)    == resources[SOCResourceConstants.WOOD])
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.UNKNOWN) == resources[SOCResourceConstants.UNKNOWN]))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * @return a hashcode for this data
     */
    public int hashCode()
    {
        return this.toString().hashCode();
    }

    /**
     * @return a copy of this resource set
     */
    public SOCResourceSet copy()
    {
        SOCResourceSet copy = new SOCResourceSet();
        copy.add(this);

        return copy;
    }

    /**
     * copy a resource set into this one. This one's current data is lost and overwritten.
     *
     * @param set  the set to copy from
     */
    public void setAmounts(SOCResourceSet set)
    {
        resources[SOCResourceConstants.CLAY]    = set.getAmount(SOCResourceConstants.CLAY);
        resources[SOCResourceConstants.ORE]     = set.getAmount(SOCResourceConstants.ORE);
        resources[SOCResourceConstants.SHEEP]   = set.getAmount(SOCResourceConstants.SHEEP);
        resources[SOCResourceConstants.WHEAT]   = set.getAmount(SOCResourceConstants.WHEAT);
        resources[SOCResourceConstants.WOOD]    = set.getAmount(SOCResourceConstants.WOOD);
        resources[SOCResourceConstants.UNKNOWN] = set.getAmount(SOCResourceConstants.UNKNOWN);
    }

}
