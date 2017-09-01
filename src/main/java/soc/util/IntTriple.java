/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003 Robert S. Thomas <thomas@infolab.northwestern.edu>
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
package soc.util;

/**
 * An ordered triple of 3 ints.
 * @see IntPair
 */
public class IntTriple
{
    /** The first int of the ordered triple */
    public int a;

    /** The second int of the ordered triple */
    public int b;

    /** The third int of the ordered triple */
    public int c;

    /**
     * Creates a new IntTriple object.
     *
     * @param a  First int
     * @param b  Second int
     * @param c  Third int
     */
    public IntTriple(int a, int b, int c)
    {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    /**
     * Are these IntTriples' integers equal (ordered comparison)?
     * Unlike {@link IntPair#equals(IntPair)}, the order matters.
     *
     * @param it  Another IntTriple
     *
     * @return True if triples' contents are (I,J,K) and (I,J,K)
     */
    public boolean equals(IntTriple it)
    {
        return (it.a == a) && (it.b == b) && (it.c == c);
    }

    /**
     * Get a string with the hex contents of this triple.
     *
     * @return A string of format "a:##, b:##, c:##", where ## are the values in hexadecimal
     */
    public String toString()
    {
        return "a:" + Integer.toHexString(a) + ", b:" + Integer.toHexString(b) + ", c:" + Integer.toHexString(c);
    }

}
