/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Common helper functions for data and conversions.
 *<P>
 * Some fields and methods were moved here from other classes, so you may see "@since 1.1.09" or other
 * versions older than this class.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public abstract class DataUtils
{
    /**
     * For use in toString: Append int array contents to stringbuffer,
     * formatted as "{ 1 2 3 4 5 }".
     * @param ia  int array to append. 0 length is allowed, null is not.
     * @param sb  StringBuffer to which <tt>ia</tt> will be appended, as "{ 1 2 3 4 5 }"
     * @param useHex  If true, append <tt>ia</tt> as hexidecimal strings.
     *            Uses {@link Integer#toHexString(int)} after checking the sign bit.
     *            (Added in 2.0.00)
     * @throws NullPointerException if <tt>ia</tt> or <tt>sb</tt> is null
     * @since 1.1.09
     */
    public static final void arrayIntoStringBuf(final int[] ia, StringBuffer sb, final boolean useHex)
        throws NullPointerException
    {
        sb.append("{");
        for (int i = 0; i < ia.length; ++i)
        {
            sb.append(' ');
            if (! useHex)
            {
                sb.append(ia[i]);
            } else {
                final int iai = ia[i];
                if (iai >= 0)
                {
                    sb.append(Integer.toHexString(iai));
                } else {
                    sb.append('-');
                    sb.append(Integer.toHexString(-iai));
                }
            }
        }
        sb.append(" }");
    }

    /**
     * For use in toString: Append string enum contents to stringbuffer,
     * formatted as "a,b,c,d,e".
     * @param se  Enum of String to append. 0 length is allowed, null is not allowed.
     * @param sb  StringBuffer to which <tt>se</tt> will be appended, as "a,b,c,d,e"
     * @throws ClassCastException if <tt>se.nextElement()</tt> returns non-String
     * @throws NullPointerException if <tt>se</tt> or <tt>sb</tt> is null
     * @since 1.1.09
     */
    public static final void enumIntoStringBuf(final Enumeration<String> se, StringBuffer sb)
        throws ClassCastException, NullPointerException
    {
        if (! se.hasMoreElements())
            return;

        try
        {
            sb.append (se.nextElement());

            while (se.hasMoreElements())
            {
                sb.append(',');
                sb.append(se.nextElement());
            }
        }
        catch (ClassCastException cce) { throw cce; }
        catch (Exception e) {}
    }

    /**
     * Make a {@link List} of boxed {@link Integer}s from a primitive int array.
     * @param arr  Array to make into list, or {@code null}; length can be 0
     * @return  List of {@link Integer}s from {@code arr}, or {@code null} if {@code arr} was {@code null};
     *     length will be 0 if {@code arr} was empty
     * @since 3.0.00
     */
    public static final List<Integer> toList(final int[] arr)
    {
        if (arr == null)
            return null;

        final int L = arr.length;
        ArrayList<Integer> ilist = new ArrayList<Integer>(L);
        for (int i = 0; i < L; ++i)
            ilist.add(Integer.valueOf(arr[i]));

        return ilist;
    }

}
