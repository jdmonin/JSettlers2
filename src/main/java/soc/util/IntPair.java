/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012,2019-2020 Jeremy D Monin <jeremy@nand.net>
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

import java.io.Serializable;

/**
 * A semi-ordered pair of 2 ints.
 * ({@link #equals(IntPair)} ignores order of A, B.)
 *<P>
 * Not thread-safe: Direct public access to fields.
 *
 * @see IntTriple
 */
public class IntPair implements Serializable
{
    /** no structural changes since v1.0 (1000) or earlier */
    private static final long serialVersionUID = 1000L;

    /** The first int of the ordered pair */
    public int a;

    /** The second int of the ordered pair */
    public int b;

    /**
     * Creates a new IntPair object.
     *
     * @param a  First int
     * @param b  Second int
     */
    public IntPair(int a, int b)
    {
        this.a = a;
        this.b = b;
    }

    /**
     * Are these IntPairs' integers equal (unordered comparison)?
     *
     * @param ip  Another IntPair
     *
     * @return True if pairs' contents are (I,J) and (I,J), or (I,J) and (J,I)
     * @see #equals(Object)
     */
    public boolean equals(IntPair ip)
    {
        return ((ip.a == a) && (ip.b == b))
            || ((ip.a == b) && (ip.b == a));
    }

    /**
     * General object comparison. If {@code obj} is an {@link IntPair}, calls {@link #equals(IntPair)}.
     * @param obj  Object to compare, or {@code null}
     * @since 2.4.00
     */
    @Override
    public boolean equals(Object obj)
    {
        return (obj instanceof IntPair) && equals((IntPair) obj);
    }

    /**
     * Get the first int of this pair.
     *
     * @return Current value of {@link #a}
     */
    public int getA()
    {
        return a;
    }

    /**
     * Get the second int of this pair.
     *
     * @return Current value of {@link #b}
     */
    public int getB()
    {
        return b;
    }

    /**
     * Get a string with the hex contents of this pair.
     *
     * @return A string of format "a:##, b:##", where ## are the values in hexadecimal
     */
    public String toString()
    {
        return "a:" + Integer.toHexString(a) + ", b:" + Integer.toHexString(b);
    }

}
