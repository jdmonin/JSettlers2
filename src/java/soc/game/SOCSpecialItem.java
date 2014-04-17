/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2014 Jeremy D Monin <jeremy@nand.net>
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
 * A special item for Settlers scenarios or expansions.
 *<P>
 * Example use: The Wonders chosen by players in the {@code _SC_WOND} scenario.
 *<P>
 * Special Items are per-game and/or per-player.  In {@link SOCGame} and {@link SOCPlayer}
 * they're accessed by an item type key.  For compatibility among scenarios and expansions,
 * this key should be a {@link SOCGameOption} keyname; if an option has more than one
 * special item type, {@code typeKey} should be optionName + "/" + a short alphanumeric key of your choosing.
 * Please document the Special Item type(s) in the SOCGameOption's javadoc, including
 * whether each is per-game, per-player, or both (for more convenient access).
 *<P>
 * <B>Locks:</B> Field values are not synchronized here. If a specific item type or access pattern
 * requires synchronization, do so outside this class and document the details.
 *<P>
 * Special items must be {@link Cloneable} for use in copy constructors, see {@link #clone()} for details.
 *
 * @since 2.0.00
 */
public class SOCSpecialItem
    implements Cloneable
{
    /**
     * The player who owns this item, if any. Will be null for certain items
     * which belong to the game and not to players.
     */
    protected SOCPlayer player;

    /** Optional coordinates on the board for this item, or -1. An edge or a node, depending on item type. */
    protected int coord;

    /** Optional level of construction or strength, or 0. */
    protected int level;

    /**
     * Make a new item, optionally owned by a player.
     * Its optional Level will be 0.
     *
     * @param pl  player who owns the item, or {@code null}
     * @param co  coordinates, or -1
     */
    public SOCSpecialItem(SOCPlayer pl, final int co)
    {
        this(pl, co, 0);
    }

    /**
     * Make a new item, optionally owned by a player, with a level.
     *
     * @param pl  player who owns the item, or {@code null}
     * @param co  coordinates, or -1
     * @param lv  current level of construction or strength, or 0
     */
    public SOCSpecialItem(SOCPlayer pl, final int co, final int lv)
    {
        player = pl;
        coord = co;
        level = lv;
    }

    /**
     * Get the player who owns this item, if any.
     * @return the owner of the item, or {@code null}
     */
    public SOCPlayer getPlayer()
    {
        return player;
    }

    /**
     * Set or clear the player who owns this item.
     * @param pl  the owner of this item, or {@code null}
     */
    public void setPlayer(SOCPlayer pl)
    {
        player = pl;
    }

    /**
     * @return the node or edge coordinate for this item, or -1 if none
     */
    public int getCoordinates()
    {
        return coord;
    }

    /**
     * @param co the node or edge coordinate for this item, or -1 if none
     */
    public void setCoordinates(final int co)
    {
        coord = co;
    }

    /**
     * Get the current construction level or strength of this item.
     * @return  Current level
     */
    public int getLevel()
    {
        return level;
    }

    /**
     * Set the current level of this special item.
     * @param lv  New level
     */
    public void setLevel(final int lv)
    {
        level = lv;
    }

    /**
     * @return a human readable form of this object
     */
    @Override
    public String toString()
    {
        return "SOCSpecialItem:player=" + player + "|coord=" + Integer.toHexString(coord) + "|level=" + level;
    }

    /**
     * Compare this SOCSpecialItem to another SOCSpecialItem, or another object.
     * Comparison method:
     * <UL>
     * <LI> If other is null, false.
     * <LI> If other is not a SOCSpecialItem, use our super.equals to compare.
     * <LI> SOCSpecialItem are equal with the same coordinate, player, and level.
     * </UL>
     *
     * @param other The object to compare with, or null
     */
    @Override
    public boolean equals(Object other)
    {
        if (other == null)
            return false;
        if (! (other instanceof SOCSpecialItem))
            return super.equals(other);

        return ((coord == ((SOCSpecialItem) other).coord)
            &&  (player == ((SOCSpecialItem) other).player)
            &&  (level == ((SOCSpecialItem) other).level));
    }

    /**
     * For use in set copy constructors, create and return a clone of this {@link SOCSpecialItem}.
     * The {@code SOCSpecialItem} implementation just calls {@code super.clone()}.
     * If subclasses have any lists or structures, be sure to deeply copy them.
     * @throws CloneNotSupportedException  Declared from super.clone(), should not occur
     *     since SOCSpecialItem implements Cloneable.
     * @return a clone of this item
     */
    public SOCSpecialItem clone()
        throws CloneNotSupportedException
    {
        return (SOCSpecialItem) super.clone();
    }

}
