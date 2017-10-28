/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
 * Portions of this file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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

/**
 * Represents an immutable set of resources
 *
 * To construct a mutable set, see {@link SOCResourceSet}.
 * @since 2.0.00
 */
public interface ResourceSet
{
    /**
     * How many resources of this type are contained in the set?
     * @param resourceType  the type of resource, like {@link Data.ResourceType#CLAY_VALUE}
     * @return the number of a kind of resource
     * @see #contains(int)
     * @see #getTotal()
     */
    int getAmount(int resourceType);

    /**
     * Does the set contain any resources of this type?
     * @param resourceType  the type of resource, like {@link Data.ResourceType#CLAY_VALUE}
     * @return true if the set's amount of this resource &gt; 0
     * @see #getAmount(int)
     * @see #contains(ResourceSet)
     */
    boolean contains(int resourceType);

    /**
     * Get the number of known resource types contained in this set:
     * {@link Data.ResourceType#CLAY_VALUE} to {@link Data.ResourceType#WOOD_VALUE},
     * excluding {@link Data.ResourceType#UNKNOWN} or {@link SOCResourceConstants#GOLD_LOCAL}.
     * An empty set returns 0, a set containing only wheat returns 1,
     * that same set after adding wood and sheep returns 3, etc.
     * @return  The number of resource types in this set with nonzero resource counts.
     */
    int getResourceTypeCount();

    /**
     * Get the total number of resources in this set
     * @return the total number of resources
     * @see #getAmount(int)
     */
    int getTotal();

    /**
     * @return true if this contains at least the resources in other
     *
     * @param other  the sub set, can be {@code null} for an empty resource subset
     * @see #contains(int)
     */
    boolean contains(ResourceSet other);
}
