/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2007,2016,2018-2019 Jeremy D. Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
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
package soc.client;

import java.awt.Color;


/**
 * This is a larger square box with a background color and
 * possibly a number or X in it.  This box can be
 * interactive, or non-interactive.  The possible
 * colors of the box correspond to resources in SoC.
 *<P>
 * Because {@link ColorSquare}s and their dimensions are used widely,
 * having this subclass limits the changes needed for larger
 * squares for player interaction.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
@SuppressWarnings("serial")
public class ColorSquareLarger extends ColorSquare
{
    public final static int WIDTH_L = 20;
    public final static int HEIGHT_L = 20;

    /**
     * Creates a new ColorSquareLarger with specified background color and without a visible value.
     * Non-interactive. Calls {@link ColorSquare#ColorSquare(Color)}.
     *<P>
     * A tooltip with the resource name is created if {@code c} is one of the
     * resource colors defined in ColorSquare ({@link ColorSquare#CLAY CLAY}, {@link ColorSquare#WHEAT WHEAT}, etc,
     * or an element of {@link ColorSquare#RESOURCE_COLORS RESOURCE_COLORS}).
     *
     * @param c background color; creates resource-name tooltip if is a resource color
     * @since 2.0.00
     */
    public ColorSquareLarger(Color c)
    {
        super(c, WIDTH_L, HEIGHT_L);
    }

    /**
     * Creates a new ColorSquareLarger of the specified kind and background
     * color. Possibly interactive. For kind = NUMBER, upper=99, lower=0.
     * Calls {@link ColorSquare#ColorSquare(int, boolean, Color)}.
     *<P>
     * A tooltip with the resource name is created if {@code c} is one of the
     * resource colors defined in ColorSquare ({@link ColorSquare#CLAY CLAY}, {@link ColorSquare#WHEAT WHEAT}, etc,
     * or an element of {@link ColorSquare#RESOURCE_COLORS RESOURCE_COLORS}).
     *
     * @param k Kind: NUMBER, YES_NO, CHECKBOX, BOUNDED_INC, BOUNDED_DEC
     * @param in interactive flag allowing user interaction
     * @param c background color; creates resource-name tooltip if is a resource color
     */
    public ColorSquareLarger(int k, boolean in, Color c)
    {
        super(k, in, c);
        setSizesAndFont(WIDTH_L, HEIGHT_L);
    }

    /**
     * Creates a new ColorSquare of the specified kind and background
     * color. Possibly interactive, with upper and lower bounds specified for
     * NUMBER kinds.
     *<P>
     * A tooltip with the resource name is created if {@code c} is one of the
     * resource colors defined in ColorSquare ({@link ColorSquare#CLAY CLAY}, {@link ColorSquare#WHEAT WHEAT}, etc,
     * or an element of {@link ColorSquare#RESOURCE_COLORS RESOURCE_COLORS}).
     *
     * @param k Kind: NUMBER, YES_NO, CHECKBOX, BOUNDED_INC, BOUNDED_DEC
     * @param in interactive flag allowing user interaction
     * @param c background color; creates resource-name tooltip if is a resource color
     * @param upper upper bound if k == NUMBER
     * @param lower lower bound if k == NUMBER
     */
    public ColorSquareLarger(int k, boolean in, Color c, int upper, int lower)
    {
        super(k, in, c, upper, lower);
        setSizesAndFont(WIDTH_L, HEIGHT_L);
    }

}
