/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
 * Portions of this file Copyright (C) 2017,2019-2020 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Arrays;

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link SOCResourceSet}.
 * @since 2.0.00
 */
public class TestResourceSet
{
    private static SOCResourceSet onePerType()
    {
        return new SOCResourceSet(1,1,1,1,1,0);
    }

    @Test
    public void total_test()
    {
        SOCResourceSet rs = onePerType();
        assertEquals(5, rs.getTotal());
    }

    /**
     * Tests for {@link SOCResourceSet#isEmpty()}.
     * @since 2.4.50
     */
    @Test
    public void isEmpty()
    {
        assertFalse(onePerType().isEmpty());

        SOCResourceSet rs = new SOCResourceSet();
        assertTrue(rs.isEmpty());

        rs.add(1, SOCResourceConstants.SHEEP);
        assertFalse(rs.isEmpty());

        rs.subtract(1, SOCResourceConstants.SHEEP);
        assertTrue(rs.isEmpty());

        // remove a resource, even though set is already empty
        rs.subtract(1, SOCResourceConstants.SHEEP);
        assertEquals(-1, rs.getAmount(SOCResourceConstants.UNKNOWN));
        assertFalse(rs.isEmpty());  // since total UNKNOWN is -1
    }

    @Test
    public void removeOneResource_removesOneResource()
    {
        SOCResourceSet rs = onePerType();
        rs.subtract(1, SOCResourceConstants.ORE);
        assertEquals(4, rs.getTotal());
    }

    @Test
    public void removeTwoResources_doesNotThrowException()
    {
        SOCResourceSet rs = onePerType();
        rs.subtract(2, SOCResourceConstants.CLAY);
        assertEquals(3, rs.getTotal());
        assertEquals(0, rs.getAmount(SOCResourceConstants.CLAY));
        assertEquals(-1, rs.getAmount(SOCResourceConstants.UNKNOWN));
    }

    @Test
    public void removeAll_yieldsEmptyResourceSet()
    {
        SOCResourceSet rs = onePerType();
        rs.subtract(1, SOCResourceConstants.CLAY);
        rs.subtract(1, SOCResourceConstants.WHEAT);
        rs.subtract(1, SOCResourceConstants.WOOD);
        rs.subtract(1, SOCResourceConstants.ORE);
        rs.subtract(1, SOCResourceConstants.SHEEP);
        assertEquals(0, rs.getTotal());
    }

    @Test
    public void removeAll_yieldsEmptyResourceSet2()
    {
        SOCResourceSet rs1 = onePerType();
        SOCResourceSet rs2 = onePerType();
        rs1.subtract(rs2);
        assertEquals(0, rs1.getTotal());
    }

    @Test
    public void clear_yieldsEmptyResourceSet()
    {
        SOCResourceSet rs = onePerType();
        rs.clear();
        assertEquals(0, rs.getTotal());
    }

    @Test
    public void onePerType_hasOnePerType()
    {
        SOCResourceSet rs = onePerType();
        assertTrue(rs.contains(SOCResourceConstants.CLAY));
        assertTrue(rs.contains(SOCResourceConstants.WHEAT));
        assertTrue(rs.contains(SOCResourceConstants.WOOD));
        assertTrue(rs.contains(SOCResourceConstants.ORE));
        assertTrue(rs.contains(SOCResourceConstants.SHEEP));
    }

    @Test
    public void onePerType_hasOnePerType2()
    {
        SOCResourceSet rs = onePerType();
        assertEquals(1, rs.getAmount(SOCResourceConstants.CLAY));
        assertEquals(1, rs.getAmount(SOCResourceConstants.ORE));
        assertEquals(1, rs.getAmount(SOCResourceConstants.SHEEP));
        assertEquals(1, rs.getAmount(SOCResourceConstants.WOOD));
        assertEquals(1, rs.getAmount(SOCResourceConstants.WHEAT));
    }

    @Test
    public void containsArray()
    {
        SOCResourceSet rs = onePerType();
        int[] other = new int[5];

        assertTrue(rs.contains(other));  // all 0s
        other[1] = 1;
        assertTrue(rs.contains(other));  // subset
        Arrays.fill(other, 1);
        assertTrue(rs.contains(other));  // equal
        other[0] = 2;
        assertFalse(rs.contains(other));  // no longer subset

        other = new int[6];
        assertTrue(rs.contains(other));  // all 0s
        other[1] = 1;
        assertTrue(rs.contains(other));  // subset
        Arrays.fill(other, 1);
        assertFalse(rs.contains(other));  // no longer subset, because of unknowns
        other[5] = 0;
        assertTrue(rs.contains(other));  // now equal again: no unknowns
        other[2] = 2;
        assertFalse(rs.contains(other));  // no longer subset

        other = null;
        assertTrue(rs.contains(other));  // null is a subset
    }

    @Test(expected=IllegalArgumentException.class)
    public void containsArrayLengthThrow4()
    {
        SOCResourceSet rs = onePerType();
        rs.contains(new int[4]);  // too short
    }

    @Test(expected=IllegalArgumentException.class)
    public void containsArrayLengthThrow7()
    {
        SOCResourceSet rs = onePerType();
        rs.contains(new int[7]);  // too long
    }

    @Test
    public void onePerType_typesAreKnown()
    {
        SOCResourceSet rs = onePerType();
        assertEquals(5, rs.getResourceTypeCount());
    }

    @Test
    public void addResourceSet_addsResourceSet()
    {
        SOCResourceSet rs1 = onePerType();
        SOCResourceSet rs2 = onePerType();
        rs1.add(rs2);
        assertEquals(2, rs1.getAmount(SOCResourceConstants.CLAY));
        assertEquals(2, rs1.getAmount(SOCResourceConstants.WHEAT));
        assertEquals(2, rs1.getAmount(SOCResourceConstants.ORE));
        assertEquals(2, rs1.getAmount(SOCResourceConstants.WOOD));
        assertEquals(2, rs1.getAmount(SOCResourceConstants.SHEEP));
        assertTrue(rs1.contains(SOCResourceConstants.WHEAT));
    }
    @Test
    public void clone_isContained()
    {
        SOCResourceSet rs1 = onePerType();
        SOCResourceSet rs2 = onePerType();
        assertTrue(rs1.equals(rs2));
        assertTrue(rs1.contains(rs2));
    }

    @Test
    public void almostClone_isNotContained()
    {
        SOCResourceSet rs1 = onePerType();
        SOCResourceSet rs2 = onePerType();
        assertTrue(rs2.equals(rs1));

        rs2.subtract(1, SOCResourceConstants.CLAY);
        assertTrue(rs1.contains(rs2));
        assertFalse(rs2.contains(rs1));
    }

    @Test
    public void clone_isEqual()
    {
        SOCResourceSet rs1 = onePerType();
        SOCResourceSet rs2 = onePerType();
        assertTrue(rs1.equals(rs2));
        assertTrue(rs2.equals(rs1));
    }

    @Test
    public void copyConstructor_ConstructsACopy()
    {
        SOCResourceSet all = onePerType();
        SOCResourceSet copy = new SOCResourceSet(all);
        assertEquals(all, copy);
        assertTrue(all.equals(copy));
        assertTrue(copy.equals(all));
    }

    /**
     * Test {@link SOCResourceSet#getAmounts(boolean)}.
     * @since 2.4.50
     */
    @Test
    public void testGetAmounts()
    {
        SOCResourceSet rs = new SOCResourceSet();
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, rs.getAmounts(false));
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0}, rs.getAmounts(true));

        rs = onePerType();
        assertArrayEquals(new int[]{1, 1, 1, 1, 1}, rs.getAmounts(false));
        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 0}, rs.getAmounts(true));
        rs.add(2, SOCResourceConstants.UNKNOWN);
        assertArrayEquals(new int[]{1, 1, 1, 1, 1}, rs.getAmounts(false));
        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 2}, rs.getAmounts(true));

        rs = new SOCResourceSet(1, 0, 2, 0, 3, 1);
        assertArrayEquals(new int[]{1, 0, 2, 0, 3}, rs.getAmounts(false));
        assertArrayEquals(new int[]{1, 0, 2, 0, 3, 1}, rs.getAmounts(true));

        assertArrayEquals
            (new int[]{2, 1, 2, 3, 4, 0},
             new SOCResourceSet(new int[]{2, 1, 2, 3, 4}).getAmounts(true));  // constructor: array without unknowns

        int[] counts = new int[]{2, 1, 2, 3, 4, 7};
        rs =  new SOCResourceSet(counts);  // constuctor: array with unknowns
        assertArrayEquals(counts, rs.getAmounts(true));
        assertArrayEquals(new int[]{2, 1, 2, 3, 4}, rs.getAmounts(false));
    }

    /**
     * Test {@link SOCResourceSet#hashCode()}
     * @since 2.4.50
     */
    @Test
    public void testHashCode()
    {
        SOCResourceSet rs1 = new SOCResourceSet(1, 0, 2, 0, 3, 1);
        SOCResourceSet rs2 = new SOCResourceSet(1, 0, 2, 0, 3, 1);
        assertTrue(rs1.equals(rs2));
        assertEquals(rs1.hashCode(), rs2.hashCode());

        rs1.add(1, SOCResourceConstants.SHEEP);
        assertFalse(rs1.equals(rs2));
        assertNotEquals(rs1.hashCode(), rs2.hashCode());
        rs2.add(1, SOCResourceConstants.SHEEP);
        assertTrue(rs1.equals(rs2));
        assertEquals(rs1.hashCode(), rs2.hashCode());

        SOCResourceSet rsCopy = new SOCResourceSet(rs1);
        assertTrue(rs1.equals(rs2));
        assertEquals(rs1.hashCode(), rsCopy.hashCode());
    }

}
