/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017,2019,2021-2022 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
     * For use in toString: Append collection's contents to a StringBuilder,
     * formatted as "a,b,c,d,e".
     *<P>
     * Before v2.0.00 this method was {@code enumIntoStringBuf}.
     *
     * @param sc  Collection to append. 0 length is allowed, null is not allowed.
     *     Each element's {@link Object#toString()} will be called.
     * @param sb  StringBuilder to which {@code sc} will be appended as "a,b,c,d,e"
     * @throws NullPointerException if {@code sc} or {@code sb} is null
     * @since 1.1.09
     */
    public static final void listIntoStringBuilder(final Collection<?> sc, final StringBuilder sb)
        throws NullPointerException
    {
        if (sc.isEmpty())
            return;

        boolean any = false;
        for (Object s : sc)
        {
            if (any)
                sb.append(',');
            else
                any = true;

            sb.append(s);
        }
    }

    /**
     * Append map's contents to a StringBuilder, formatted by default as {@code "K1: V1; K2: v2"}.
     * Will be sorted here by key unless {@code map} is already a {@link TreeMap}.
     * Separators can be changed. Appends null as {@code "(null)"}, empty map as {@code "(empty)"}.
     *
     * @param map  Map to append.  Can be empty or {@code null}.
     * @param sb  StringBuilder to which {@code map} will be appended; not {@code null}
     * @param kvSeparator Separator to use between each key and its value, or {@code null} to use default {@code ": "}
     * @param entrySeparator Separator to use between items, or {@code null} to use default {@code ", "}
     * @throws NullPointerException if {@code sb} is null
     * @since 2.6.00
     */
    public static final void mapIntoStringBuilder
        (final Map<?, ?> map, final StringBuilder sb, String kvSeparator, String entrySeparator)
        throws NullPointerException
    {
        if (map == null)
        {
            sb.append("(null)");
            return;
        }
        else if (map.isEmpty())
        {
            sb.append("(empty)");
            return;
        }

        if (kvSeparator == null)
            kvSeparator = ": ";
        if (entrySeparator == null)
            entrySeparator = ", ";

        final Set<?> sortedKeys = (map instanceof TreeMap) ? map.keySet() : new TreeSet<>(map.keySet());
        boolean any = false;
        for (Object k : sortedKeys)
        {
            if (any)
                sb.append(entrySeparator);
            else
                any = true;

            sb.append(k).append(kvSeparator).append(map.get(k));
        }
    }

    /**
     * Convert a list of boxed Integers to a primitive int array.
     * @param li  List to convert, or {@code null}
     * @return The list as an array, or {@code null} if {@code li} is {@code null}
     * @since 2.5.00
     */
    public static final int[] intListToPrimitiveArray(final List<Integer> li)
    {
        if (li == null)
            return null;

        int[] arr = new int[li.size()];
        Iterator<Integer> iterator = li.iterator();
        for (int i = 0; i < arr.length; ++i)
            arr[i] = iterator.next().intValue();

        return arr;
    }

}
