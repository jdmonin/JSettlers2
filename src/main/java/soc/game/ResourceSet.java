/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
 * Portions of this file Copyright (C) 2017,2019-2021,2023 Jeremy D Monin <jeremy@nand.net>
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

/**
 * Represents an immutable set of resources
 *<P>
 * To construct a mutable set, see {@link SOCResourceSet}.
 * @since 2.0.00
 */
public interface ResourceSet
{
    /**
     * Is this set empty, containing zero resources?
     * @return true if set is completely empty, including its amount of unknown resources
     * @see #getTotal()
     * @see #getAmount(int)
     * @since 2.5.00
     */
    public boolean isEmpty();

    /**
     * How many resources of this type are contained in the set?
     * @param resourceType  the type of resource, like {@link SOCResourceConstants#CLAY}
     * @return the number of a kind of resource
     * @see #contains(int)
     * @see #getTotal()
     * @see #isEmpty()
     */
    int getAmount(int resourceType);

    /**
     * Does the set contain any resources of this type?
     * @param resourceType  the type of resource, like {@link SOCResourceConstants#CLAY}
     * @return true if the set's amount of this resource &gt; 0
     * @see #getAmount(int)
     * @see #contains(ResourceSet)
     * @see #isEmpty()
     */
    boolean contains(int resourceType);

    /**
     * Get the number of known resource types contained in this set:
     * {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD},
     * excluding {@link SOCResourceConstants#UNKNOWN} or {@link SOCResourceConstants#GOLD_LOCAL}.
     * An empty set returns 0, a set containing only wheat returns 1,
     * that same set after adding wood and sheep returns 3, etc.
     * @return  The number of resource types in this set with nonzero resource counts.
     * @see #isEmpty()
     */
    int getResourceTypeCount();

    /**
     * Get the total number of resources in this set
     * @return the total number of resources
     * @see #getAmount(int)
     * @see #isEmpty()
     */
    int getTotal();

    /**
     * Does this set contain all resources of another set?
     * @param other  the subset to test against; can be {@code null} for an empty resource subset
     * @return true if this contains at least the resource amounts in {@code other}
     *     for each known resource type and {@link SOCResourceConstants#UNKNOWN}.
     *     True if {@code other} is null or empty.
     * @see #contains(int)
     * @see #getAmount(int)
     * @see #isEmpty()
     */
    boolean contains(ResourceSet other);

}
