/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012 Jeremy D Monin <jeremy@nand.net>
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
 * An ordered pair of 2 ints.
 * @see IntTriple
 */
public class IntPair
{
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
     */
    public boolean equals(IntPair ip)
    {
        if (((ip.a == a) && (ip.b == b)) || ((ip.a == b) && (ip.b == a)))
        {
            return true;
        }
        else
        {
            return false;
        }
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
     * Set the first int of this pair.
     *
     * @param val New value for {@link #a}
     */
    public void setA(int val)
    {
        a = val;
    }

    /**
     * Set the second int of this pair.
     *
     * @param val New value for {@link #b}
     */
    public void setB(int val)
    {
        b = val;
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
