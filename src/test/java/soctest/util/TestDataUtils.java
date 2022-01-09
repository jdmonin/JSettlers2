/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2021-2022 Jeremy D Monin <jeremy@nand.net>
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
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedList;
import java.util.TreeMap;

import soc.game.SOCResourceSet;
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

    /**
     * Test {@link DataUtils#mapIntoStringBuilder(Map, StringBuilder, String, String)}.
     * @since 2.6.00
     */
    @Test
    public void testMapIntoStringBuilder()
    {
        Map<String, String> strMap = new HashMap<>();
        StringBuilder sb = new StringBuilder();

        try
        {
            DataUtils.mapIntoStringBuilder(strMap, null, ": ", "; ");
            fail("null sb should throw NullPointerException");
        } catch (NullPointerException e) {}

        DataUtils.mapIntoStringBuilder(null, sb, null, null);
        assertEquals("(null)", sb.toString());
        sb.delete(0, sb.length());

        DataUtils.mapIntoStringBuilder(strMap, sb, null, null);
        assertEquals("(empty)", sb.toString());
        sb.delete(0, sb.length());

        strMap.put("K1", "v1");

        DataUtils.mapIntoStringBuilder(strMap, sb, null, null);
        assertEquals("K1: v1", sb.toString());
        sb.delete(0, sb.length());

        strMap.put("K1", "v1");
        DataUtils.mapIntoStringBuilder(strMap, sb, " = ", null);
        assertEquals("K1 = v1", sb.toString());
        sb.delete(0, sb.length());

        strMap.put("K2", "v2");
        strMap.put("3K", "3v");

        DataUtils.mapIntoStringBuilder(strMap, sb, null, null);
        assertEquals("3K: 3v, K1: v1, K2: v2", sb.toString());
        sb.delete(0, sb.length());

        DataUtils.mapIntoStringBuilder(strMap, sb, ":", null);
        assertEquals("3K:3v, K1:v1, K2:v2", sb.toString());
        sb.delete(0, sb.length());

        DataUtils.mapIntoStringBuilder(strMap, sb, null, "; ");
        assertEquals("3K: 3v; K1: v1; K2: v2", sb.toString());
        sb.delete(0, sb.length());

        DataUtils.mapIntoStringBuilder(strMap, sb, ":", "; ");
        assertEquals("3K:3v; K1:v1; K2:v2", sb.toString());
        sb.delete(0, sb.length());

        // pre-sorted TreeMap:

        final TreeMap<String, String> tMap = new TreeMap<>();

        DataUtils.mapIntoStringBuilder(tMap, sb, null, null);
        assertEquals("(empty)", sb.toString());
        sb.delete(0, sb.length());

        tMap.put("k1", "v1");
        tMap.put("k0", "V0");
        tMap.put("k22", "v22");
        tMap.put("kThree", "V3");

        DataUtils.mapIntoStringBuilder(tMap, sb, null, null);
        assertEquals("k0: V0, k1: v1, k22: v22, kThree: V3", sb.toString());
        sb.delete(0, sb.length());

        DataUtils.mapIntoStringBuilder(tMap, sb, "=", "; ");
        assertEquals("k0=V0; k1=v1; k22=v22; kThree=V3", sb.toString());
        sb.delete(0, sb.length());

        // other keys/values types:

        final Map<Integer, SOCResourceSet> resMap = new HashMap<>();
        resMap.put(Integer.valueOf(3), new SOCResourceSet(0, 3, 0, 2, 0, 0));
        resMap.put(Integer.valueOf(-1), new SOCResourceSet(4, 0, 0, 0, 0, 0));

        DataUtils.mapIntoStringBuilder(resMap, sb, null, null);
        assertEquals
            ("-1: clay=4|ore=0|sheep=0|wheat=0|wood=0|unknown=0, 3: clay=0|ore=3|sheep=0|wheat=2|wood=0|unknown=0", sb.toString());
        sb.delete(0, sb.length());

        DataUtils.mapIntoStringBuilder(resMap, sb, " == ", "; ");
        assertEquals
            ("-1 == clay=4|ore=0|sheep=0|wheat=0|wood=0|unknown=0; 3 == clay=0|ore=3|sheep=0|wheat=2|wood=0|unknown=0", sb.toString());
        // sb.delete(0, sb.length());
    }

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
