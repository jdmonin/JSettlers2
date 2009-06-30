/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2007 Jeremy D. Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas
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
package soc.client;

import java.awt.Color;


/**
 * This is a larger square box with a background color and
 * possibly a number or X in it.  This box can be
 * interactive, or non-interactive.  The possible
 * colors of the box correspond to resources in SoC.
 *
 * Because ColorSquares and their dimensions are used widely,
 * this subclass limits the changes needed for larger
 * squares for player interaction.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
public class ColorSquareLarger extends ColorSquare
{
    public final static int WIDTH_L = 20;
    public final static int HEIGHT_L = 20;

    /**
     * Creates a new ColorSquareLarger of the specified kind and background
     * color. Possibly interactive. For kind = NUMBER, upper=99, lower=0.
     *
     * @param k Kind: NUMBER, YES_NO, CHECKBOX, BOUNDED_INC, BOUNDED_DEC
     * @param in interactive flag allowing user interaction
     * @param c background color
     * @see ColorSquare#ColorSquare(int, boolean, Color)
     */
    public ColorSquareLarger(int k, boolean in, Color c)
    {
        super(k, in, c);
        setSize(WIDTH_L, HEIGHT_L);
    }

    /**
     * Creates a new ColorSquare of the specified kind and background
     * color. Possibly interactive, with upper and lower bounds specified for
     * NUMBER kinds.
     *
     * @param k Kind: NUMBER, YES_NO, CHECKBOX, BOUNDED_INC, BOUNDED_DEC
     * @param in interactive flag allowing user interaction
     * @param c background color
     * @param upper upper bound if k == NUMBER
     * @param lower lower bound if k == NUMBER
     */
    public ColorSquareLarger(int k, boolean in, Color c, int upper, int lower)
    {
        super(k, in, c, upper, lower);
        setSize(WIDTH_L, HEIGHT_L);
    }
    
}
