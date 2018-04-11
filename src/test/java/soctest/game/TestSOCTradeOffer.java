/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2018 Jeremy D Monin <jeremy@nand.net>
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

package soctest.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCTradeOffer;

/**
 * Unit testing for {@link SOCTradeOffer#makePNArray(List)}.
 *<P>
 * If other {@link SOCTradeOffer} tests are added here later, consider backporting to v2.x.xx.
 * @since 3.0.00
 */
public class TestSOCTradeOffer
{
    /**
     * Test whether {@code makePNArray} gets expected results when called with a given list (array) of integers.
     * @param expected  Expected results from {@code makePNArray} call
     * @param fromArray  Array of integers to feed as a list into {@code makePNArray}
     */
    private static void callAndCheckMakePNArray(final boolean[] expected, final int[] fromArray)
    {
        final String arrayStr = Arrays.toString(fromArray);
        List<Integer> li = new ArrayList<Integer>();
        for (int elem : fromArray)
            li.add(elem);

        boolean[] arr = SOCTradeOffer.makePNArray(li);
        assertNotNull("expected not null from " + arrayStr, arr);
        assertTrue("expected length " + expected.length + " from " + arrayStr, (arr.length == expected.length));
        if (! Arrays.equals(expected, arr))
            fail("expected contents " + Arrays.toString(expected)
                 + ", got " + Arrays.toString(arr) + " from " + arrayStr);
    }

    @Test
    public void testMakePNArrayBasics()
    {
        // null
        boolean[] arr = SOCTradeOffer.makePNArray(null);
        assertNull("expected null from null", arr);

        // { }
        List<Integer> li = new ArrayList<Integer>();
        arr = SOCTradeOffer.makePNArray(li);
        assertNull("expected null from empty", arr);

        // { -1 }
        li.add(-1);
        arr = SOCTradeOffer.makePNArray(li);
        assertNull("expected null from {-1}", arr);
    }

    @Test
    public void testMakePNArrayContents()
    {
        callAndCheckMakePNArray(new boolean[]{true, false, false, false}, new int[]{ 0 });

        callAndCheckMakePNArray(new boolean[]{false, false, false, true}, new int[]{ 3 });

        callAndCheckMakePNArray(new boolean[]{false, false, false, false, true, false}, new int[]{ 4 });

        callAndCheckMakePNArray(new boolean[]{false, false, false, false, false, true}, new int[]{ 5 });

        callAndCheckMakePNArray(new boolean[]{false, false, false, false, false, false}, new int[]{ 6 });

        callAndCheckMakePNArray(new boolean[]{false, true, false, true}, new int[]{ 1, 3 });

        callAndCheckMakePNArray(new boolean[]{false, false, false, true}, new int[]{ -1, 3 });

        callAndCheckMakePNArray(new boolean[]{true, false, false, false, true, false}, new int[]{ 0, 4 });

        callAndCheckMakePNArray(new boolean[]{false, false, false, false, true, false}, new int[]{ -1, 4 });

        callAndCheckMakePNArray(new boolean[]{false, false, false, false, false, false}, new int[]{ 99 });

        callAndCheckMakePNArray(new boolean[]{false, false, true, false, false, false}, new int[]{ 2, 99 });

        callAndCheckMakePNArray(new boolean[]{false, false, false, false, false, false}, new int[]{ -1, 99 });
    }

}
