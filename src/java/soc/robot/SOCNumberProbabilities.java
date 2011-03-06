/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010 Jeremy D Monin <jeremy@nand.net>
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


/**
 * This is a list of probabilities for how often
 * each dice number comes up.
 */
interface SOCNumberProbabilities
{
    /**
     * Float probabilities:
     * Index is dice number (1-12; 0 is unused), value ranges 0.0 to 1.0.
     * There's a 5/36 = 14% chance of rolling a 6,
     * so <tt>FLOAT_VALUES[6]</tt> = 0.14f.
     */
    public static final float[] FLOAT_VALUES = 
    {
        0.0f, 0.0f, 0.03f, 0.06f, 0.08f, 0.11f, 0.14f, 0.17f, 0.14f, 0.11f,
        0.08f, 0.06f, 0.03f
    };

    /**
     * Integer percent probabilities:
     * Index is dice number (1-12; 0 is unused), value is 100 * percentage.
     * There's a 5/36 = 14% chance of rolling a 6,
     * so <tt>INT_VALUES[6]</tt> = 14.
     */
    public static final int[] INT_VALUES = 
    {
        0, 0, 3, 6, 8, 11, 14, 17, 14, 11, 8, 6, 3
    };
}
