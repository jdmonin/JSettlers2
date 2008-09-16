/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008 Jeremy D. Monin <jeremy@nand.net>
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
 */
package soc.client;

/**
 * Listen for changes to the value of the color square,
 * when a user clicks or a setter is called.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
public interface ColorSquareListener
{
    /**
     * Called by {@link ColorSquare} when clicked and value changes.
     *
     * @param sq The square being changed
     * @param oldValue The previous value before clicking
     * @param newValue The new value after clicking; for boolean squares,
     *                 unchecked/no is 0 and checked/yes is 1.
     */
    public void squareChanged(ColorSquare sq, int oldValue, int newValue);
}
