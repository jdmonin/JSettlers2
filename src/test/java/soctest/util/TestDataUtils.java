/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2021 Jeremy D Monin <jeremy@nand.net>
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

package soctest.util;

import java.util.ArrayList;
import java.util.LinkedList;

import soc.util.DataUtils;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link DataUtils}.
 * @since 2.5.00
 */
public class TestDataUtils
{
    // TODO tests for arrayIntoStringBuf, listIntoStringBuilder

    @Test
    public void testintListToPrimitiveArray()
    {
        assertNull(DataUtils.intListToPrimitiveArray(null));

        int[] arr = DataUtils.intListToPrimitiveArray(new LinkedList<Integer>());
        assertNotNull(arr);
        assertEquals(0, arr.length);

        ArrayList<Integer> al = new ArrayList<>();
        al.add(3);
        arr = DataUtils.intListToPrimitiveArray(al);
        assertNotNull(arr);
        assertEquals(1, arr.length);
        assertEquals(3, arr[0]);

        al.add(42);
        al.add(-7);
        arr = DataUtils.intListToPrimitiveArray(al);
        assertNotNull(arr);
        assertEquals(3, arr.length);
        assertArrayEquals(new int[]{3, 42, -7}, arr);

        LinkedList<Integer> li = new LinkedList<>();
        li.add(77);
        li.add(0);
        li.add(-5);
        li.add(2);
        arr = DataUtils.intListToPrimitiveArray(li);
        assertNotNull(arr);
        assertEquals(4, arr.length);
        assertArrayEquals(new int[]{77, 0, -5, 2}, arr);
    }

}
