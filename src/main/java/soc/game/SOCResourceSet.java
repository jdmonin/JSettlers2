/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2008-2009,2012-2015,2017 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
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
package soc.game;

import soc.proto.Data;

import java.io.Serializable;
import java.util.Arrays;

/**
 * This represents a collection of
 * clay, ore, sheep, wheat, and wood resources.
 * Unknown resources are also tracked here.
 * Although it's possible to store negative amounts of resources, it's discouraged.
 *
 * @see SOCResourceConstants
 * @see SOCPlayingPiece#getResourcesToBuild(int)
 */
@SuppressWarnings("serial")
public class SOCResourceSet implements ResourceSet, Serializable, Cloneable
{
    /** Resource set with zero of each resource type */
    public static final SOCResourceSet EMPTY_SET = new SOCResourceSet();

    /**
     * the number of each resource type.
     * Indexes 1 to n are used:
     * 1 == {@link Data.ResourceType#CLAY_VALUE},
     * 2 == {@link Data.ResourceType#ORE_VALUE},
     * ...
     * 5 = {@link Data.ResourceType#WHEAT_VALUE},
     * 6 = {@link Data.ResourceType#UNKNOWN_VALUE}.
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

        resources[Data.ResourceType.CLAY_VALUE]    = cl;
        resources[Data.ResourceType.ORE_VALUE]     = or;
        resources[Data.ResourceType.SHEEP_VALUE]   = sh;
        resources[Data.ResourceType.WHEAT_VALUE]   = wh;
        resources[Data.ResourceType.WOOD_VALUE]    = wo;
        resources[Data.ResourceType.UNKNOWN_VALUE] = uk;
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
        // Note that rset[]'s indexes are different from resources[]'s indexes.

        this(rset[0], rset[1], rset[2], rset[3], rset[4], (rset.length >= 6) ? rset[5] : 0);
    }

    /**
     * Construct a new resource set from an immutable resource set (copy constructor)
     * @param other instance to copy contents from
     *
     * @implNote This constructor does not support {@link SOCResourceConstants#UNKNOWN}
     */
    public SOCResourceSet(ResourceSet other)
    {
        this();
        resources[Data.ResourceType.CLAY_VALUE] = other.getAmount(Data.ResourceType.CLAY_VALUE);
        resources[Data.ResourceType.ORE_VALUE] = other.getAmount(Data.ResourceType.ORE_VALUE);
        resources[Data.ResourceType.SHEEP_VALUE] = other.getAmount(Data.ResourceType.SHEEP_VALUE);
        resources[Data.ResourceType.WHEAT_VALUE] = other.getAmount(Data.ResourceType.WHEAT_VALUE);
        resources[Data.ResourceType.WOOD_VALUE] = other.getAmount(Data.ResourceType.WOOD_VALUE);
        resources[Data.ResourceType.UNKNOWN_VALUE] = other.getAmount(Data.ResourceType.UNKNOWN_VALUE);
    }

    /**
     * set the number of resources to zero
     */
    public void clear()
    {
        Arrays.fill(resources, 0);
    }

    /**
     * Does the set contain any resources of this type?
     * @param resourceType  the type of resource, like {@link Data.ResourceType#CLAY_VALUE}
     * @return true if the set's amount of this resource &gt; 0
     * @since 2.0.00
     * @see #getAmount(int)
     * @see #contains(ResourceSet)
     */
    public boolean contains(final int resourceType)
    {
        if (resourceType >= resources.length)
            return false;
        return (resources[resourceType] > 0);
    }

    /**
     * How many resources of this type are contained in the set?
     * @param resourceType  the type of resource, like {@link Data.ResourceType#CLAY_VALUE}
     * @return the number of a kind of resource
     * @see #contains(int)
     * @see #getTotal()
     */
    public int getAmount(int resourceType)
    {
        return resources[resourceType];
    }

    /**
     * Get the total number of resources in this set, including unknown types.
     * @return the total number of resources
     * @see #getKnownTotal()
     * @see #getAmount(int)
     * @see #getResourceTypeCount()
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
     * Get the number of known resource types contained in this set:
     * {@link Data.ResourceType#CLAY_VALUE} to {@link Data.ResourceType#WOOD_VALUE},
     * excluding {@link Data.ResourceType#UNKNOWN} or {@link SOCResourceConstants#GOLD_LOCAL}.
     * An empty set returns 0, a set containing only wheat returns 1,
     * that same set after adding wood and sheep returns 3, etc.
     * @return  The number of resource types in this set with nonzero resource counts.
     * @since 2.0.00
     */
    public int getResourceTypeCount()
    {
        int typ = 0;

        for (int i = SOCResourceConstants.MIN;
                 i <= Data.ResourceType.WOOD_VALUE; ++i)
        {
            if (resources[i] != 0)
                ++typ;
        }

        return typ;
    }

    /**
     * Get the total amount of resources of known types:
     * {@link Data.ResourceType#CLAY_VALUE} to {@link Data.ResourceType#WOOD_VALUE},
     * excluding {@link Data.ResourceType#UNKNOWN} or {@link SOCResourceConstants#GOLD_LOCAL}.
     * @return the total number of known-type resources
     * @since 1.1.14
     */
    public int getKnownTotal()
    {
        int sum = 0;

        for (int i = SOCResourceConstants.MIN;
                 i <= Data.ResourceType.WOOD_VALUE; i++)
        {
            sum += resources[i];
        }

        return sum;
    }

    /**
     * Set the amount of a resource.
     * To set all resources from another set, use {@link #add(SOCResourceSet)},
     * {@link #subtract(ResourceSet)} or {@link #setAmounts(SOCResourceSet)}.
     *
     * @param rtype the type of resource, like {@link Data.ResourceType#CLAY_VALUE}
     * @param amt   the amount
     */
    public void setAmount(int amt, int rtype)
    {
        resources[rtype] = amt;
    }

    /**
     * add an amount to a resource
     *
     * @param rtype the type of resource, like {@link Data.ResourceType#CLAY_VALUE}
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
     * @param rtype the type of resource, like {@link Data.ResourceType#CLAY_VALUE}
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
     * @param toAdd  the resource set
     */
    public void add(SOCResourceSet toAdd)
    {
        resources[Data.ResourceType.CLAY_VALUE]    += toAdd.getAmount(Data.ResourceType.CLAY_VALUE);
        resources[Data.ResourceType.ORE_VALUE]     += toAdd.getAmount(Data.ResourceType.ORE_VALUE);
        resources[Data.ResourceType.SHEEP_VALUE]   += toAdd.getAmount(Data.ResourceType.SHEEP_VALUE);
        resources[Data.ResourceType.WHEAT_VALUE]   += toAdd.getAmount(Data.ResourceType.WHEAT_VALUE);
        resources[Data.ResourceType.WOOD_VALUE]    += toAdd.getAmount(Data.ResourceType.WOOD_VALUE);
        resources[Data.ResourceType.UNKNOWN_VALUE] += toAdd.getAmount(Data.ResourceType.UNKNOWN_VALUE);
    }

    /**
     * subtract an entire resource set. If any type's amount would go below 0, set it to 0.
     * @param toReduce  the resource set to subtract
     */
    public void subtract(ResourceSet toReduce)
    {
        resources[Data.ResourceType.CLAY_VALUE] -= toReduce.getAmount(Data.ResourceType.CLAY_VALUE);

        if (resources[Data.ResourceType.CLAY_VALUE] < 0)
        {
            resources[Data.ResourceType.CLAY_VALUE] = 0;
        }

        resources[Data.ResourceType.ORE_VALUE] -= toReduce.getAmount(Data.ResourceType.ORE_VALUE);

        if (resources[Data.ResourceType.ORE_VALUE] < 0)
        {
            resources[Data.ResourceType.ORE_VALUE] = 0;
        }

        resources[Data.ResourceType.SHEEP_VALUE] -= toReduce.getAmount(Data.ResourceType.SHEEP_VALUE);

        if (resources[Data.ResourceType.SHEEP_VALUE] < 0)
        {
            resources[Data.ResourceType.SHEEP_VALUE] = 0;
        }

        resources[Data.ResourceType.WHEAT_VALUE] -= toReduce.getAmount(Data.ResourceType.WHEAT_VALUE);

        if (resources[Data.ResourceType.WHEAT_VALUE] < 0)
        {
            resources[Data.ResourceType.WHEAT_VALUE] = 0;
        }

        resources[Data.ResourceType.WOOD_VALUE] -= toReduce.getAmount(Data.ResourceType.WOOD_VALUE);

        if (resources[Data.ResourceType.WOOD_VALUE] < 0)
        {
            resources[Data.ResourceType.WOOD_VALUE] = 0;
        }

        resources[Data.ResourceType.UNKNOWN_VALUE] -= toReduce.getAmount(Data.ResourceType.UNKNOWN_VALUE);

        if (resources[Data.ResourceType.UNKNOWN_VALUE] < 0)
        {
            resources[Data.ResourceType.UNKNOWN_VALUE] = 0;
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
     * Are set A's resources each greater than or equal to set B's?
     * @return true if each resource type in set A is >= each resource type in set B
     *
     * @param a   set A, cannot be {@code null}
     * @param b   set B, can be {@code null} for an empty resource set
     */
    static public boolean gte(ResourceSet a, ResourceSet b)
    {
        if (b == null)
            return true;

        return (   (a.getAmount(Data.ResourceType.CLAY_VALUE)    >= b.getAmount(Data.ResourceType.CLAY_VALUE)
                && (a.getAmount(Data.ResourceType.ORE_VALUE)     >= b.getAmount(Data.ResourceType.ORE_VALUE))
                && (a.getAmount(Data.ResourceType.SHEEP_VALUE)   >= b.getAmount(Data.ResourceType.SHEEP_VALUE))
                && (a.getAmount(Data.ResourceType.WHEAT_VALUE)   >= b.getAmount(Data.ResourceType.WHEAT_VALUE))
                && (a.getAmount(Data.ResourceType.WOOD_VALUE)    >= b.getAmount(Data.ResourceType.WOOD_VALUE))
                && (a.getAmount(Data.ResourceType.UNKNOWN_VALUE) >= b.getAmount(Data.ResourceType.UNKNOWN_VALUE))));
    }

    /**
     * Are set A's resources each less than or equal to set B's?
     * @return true if each resource type in set A is &lt;= each resource type in set B
     *
     * @param a   set A, cannot be {@code null}
     * @param b   set B, cannot be {@code null}
     */
    static public boolean lte(ResourceSet a, ResourceSet b)
    {
        return (   (a.getAmount(Data.ResourceType.CLAY_VALUE)    <= b.getAmount(Data.ResourceType.CLAY_VALUE))
                && (a.getAmount(Data.ResourceType.ORE_VALUE)     <= b.getAmount(Data.ResourceType.ORE_VALUE))
                && (a.getAmount(Data.ResourceType.SHEEP_VALUE)   <= b.getAmount(Data.ResourceType.SHEEP_VALUE))
                && (a.getAmount(Data.ResourceType.WHEAT_VALUE)   <= b.getAmount(Data.ResourceType.WHEAT_VALUE))
                && (a.getAmount(Data.ResourceType.WOOD_VALUE)    <= b.getAmount(Data.ResourceType.WOOD_VALUE))
                && (a.getAmount(Data.ResourceType.UNKNOWN_VALUE) <= b.getAmount(Data.ResourceType.UNKNOWN_VALUE)));
    }

    /**
     * Human-readable form of the set, with format "clay=5|ore=1|sheep=0|wheat=0|wood=3|unknown=0"
     * @return a human readable longer form of the set
     * @see #toShortString()
     * @see #toFriendlyString()
     */
    public String toString()
    {
        String s = "clay=" + resources[Data.ResourceType.CLAY_VALUE]
            + "|ore=" + resources[Data.ResourceType.ORE_VALUE]
            + "|sheep=" + resources[Data.ResourceType.SHEEP_VALUE]
            + "|wheat=" + resources[Data.ResourceType.WHEAT_VALUE]
            + "|wood=" + resources[Data.ResourceType.WOOD_VALUE]
            + "|unknown=" + resources[Data.ResourceType.UNKNOWN_VALUE];

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
        String s = "Resources: " + resources[Data.ResourceType.CLAY_VALUE] + " "
            + resources[Data.ResourceType.ORE_VALUE] + " "
            + resources[Data.ResourceType.SHEEP_VALUE] + " "
            + resources[Data.ResourceType.WHEAT_VALUE] + " "
            + resources[Data.ResourceType.WOOD_VALUE] + " "
            + resources[Data.ResourceType.UNKNOWN_VALUE];

        return s;
    }

    /**
     * Human-readable form of the set, with format "5 clay,1 ore,3 wood".
     * Unknown resources aren't mentioned.
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
     * Human-readable form of the set, with format "5 clay, 1 ore, 3 wood".
     * Unknown resources aren't mentioned.
     * @param sb Append into this buffer.
     * @return true if anything was appended, false if sb unchanged (this resource set is empty).
     * @see #toFriendlyString()
     */
    public boolean toFriendlyString(StringBuffer sb)
    {
        boolean needComma = false;  // Has a resource already been appended to sb?
        int amt;

        for (int res = Data.ResourceType.CLAY_VALUE; res <= Data.ResourceType.WOOD_VALUE; ++res)
        {
            amt = resources[res];
            if (amt == 0)
                continue;

            if (needComma)
                sb.append(", ");
            sb.append(amt);
            sb.append(" ");
            sb.append(SOCResourceConstants.resName(res));
            needComma = true;
        }

        return needComma;  // Did we append anything?
    }

    /** {@inheritDoc} */
    public boolean contains(ResourceSet other)
    {
        return gte(this, other);
    }

    /**
     * @return true if the argument is a SOCResourceSet containing the same amounts of each resource, including UNKNOWN
     *
     * @param anObject  the object in question
     */
    public boolean equals(Object anObject)
    {
        if ((anObject instanceof SOCResourceSet)
                && (((SOCResourceSet) anObject).getAmount(Data.ResourceType.CLAY_VALUE)    == resources[Data.ResourceType.CLAY_VALUE])
                && (((SOCResourceSet) anObject).getAmount(Data.ResourceType.ORE_VALUE)     == resources[Data.ResourceType.ORE_VALUE])
                && (((SOCResourceSet) anObject).getAmount(Data.ResourceType.SHEEP_VALUE)   == resources[Data.ResourceType.SHEEP_VALUE])
                && (((SOCResourceSet) anObject).getAmount(Data.ResourceType.WHEAT_VALUE)   == resources[Data.ResourceType.WHEAT_VALUE])
                && (((SOCResourceSet) anObject).getAmount(Data.ResourceType.WOOD_VALUE)    == resources[Data.ResourceType.WOOD_VALUE])
                && (((SOCResourceSet) anObject).getAmount(Data.ResourceType.UNKNOWN_VALUE) == resources[Data.ResourceType.UNKNOWN_VALUE]))
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
     * Make a copy of this resource set.
     * To instead copy another set into this one, use {@link #setAmounts(SOCResourceSet)}.
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
        System.arraycopy(set.resources, 0, resources, 0, resources.length);
    }

}
