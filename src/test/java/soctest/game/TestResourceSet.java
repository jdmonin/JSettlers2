/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
 * Portions of this file Copyright (C) 2017,2019-2022 Jeremy D Monin <jeremy@nand.net>
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
import soc.server.savegame.SavedGameModel;

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
     * @since 2.5.00
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
        assertEquals(4, rs.getKnownTotal());
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
        assertEquals(0, rs.getKnownTotal());
    }

    @Test
    public void removeAll_yieldsEmptyResourceSet2()
    {
        SOCResourceSet rs1 = onePerType();
        SOCResourceSet rs2 = onePerType();
        rs1.subtract(rs2);
        assertEquals(0, rs1.getTotal());
        assertEquals(0, rs1.getKnownTotal());
    }

    /**
     * Tests for {@link SOCResourceSet#subtract(int)}.
     * @since 2.6.00
     */
    @Test
    public void testSubtractAmount()
    {
        SOCResourceSet rs = onePerType();
        assertEquals(5, rs.getTotal());

        try
        {
            rs.subtract(-1);
            fail("subtract(< 0) should throw exception");
        } catch (IllegalArgumentException e) {}

        try
        {
            rs.subtract(6);
            fail("subtract(too many) should throw exception");
        } catch (IllegalArgumentException e) {}

        SOCResourceSet subbed = rs.subtract(0);
        assertEquals(0, subbed.getTotal());  // also checks subtract(0) != null
        assertEquals(5, rs.getTotal());

        subbed = rs.subtract(5);
        assertEquals(5, subbed.getTotal());
        assertEquals(0, rs.getTotal());
        assertEquals(onePerType(), subbed);

        rs = new SOCResourceSet(0, 3, 0, 3, 0, 2);
        subbed = rs.subtract(1);
        assertEquals(1, subbed.getTotal());
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 1}, subbed.getAmounts(true));
        assertArrayEquals("subtract from unknown first", new int[]{0, 3, 0, 3, 0, 1}, rs.getAmounts(true));

        subbed = rs.subtract(2);
        assertEquals(2, subbed.getTotal());
        assertArrayEquals(new int[]{0, 1, 0, 0, 0, 1}, subbed.getAmounts(true));
        assertArrayEquals("subtract from unknown first, then left to right", new int[]{0, 2, 0, 3, 0, 0}, rs.getAmounts(true));

        subbed = rs.subtract(1);
        assertEquals(1, subbed.getTotal());
        assertArrayEquals(new int[]{0, 1, 0, 0, 0, 0}, subbed.getAmounts(true));
        assertArrayEquals("subtract left to right", new int[]{0, 1, 0, 3, 0, 0}, rs.getAmounts(true));

        subbed = rs.subtract(3);
        assertEquals(3, subbed.getTotal());
        assertArrayEquals(new int[]{0, 1, 0, 2, 0, 0}, subbed.getAmounts(true));
        assertArrayEquals("subtract left to right", new int[]{0, 0, 0, 1, 0, 0}, rs.getAmounts(true));
    }

    /**
     * Test that {@link SOCResourceSet#subtract(int, int, boolean)} converts the entire set to unknown
     * when unknown resources are removed, and {@link SOCResourceSet#getKnownTotal()} ignores unknowns.
     * @see #removeTooMany_ConvertToUnknown()
     * @since 2.5.00
     */
    @Test
    public void removeUnknown_ConvertToUnknown()
    {
        SOCResourceSet rs = new SOCResourceSet(2, 2, 2, 2, 2, 2);

        rs.subtract(1, SOCResourceConstants.CLAY);  // not unknown: behave normally
        assertEquals(11, rs.getTotal());
        assertEquals(9, rs.getKnownTotal());
        assertArrayEquals(new int[]{1, 2, 2, 2, 2, 2}, rs.getAmounts(true));

        rs.subtract(1, SOCResourceConstants.UNKNOWN);  // unknown but param is false: normal
        assertEquals(10, rs.getTotal());
        assertEquals(9, rs.getKnownTotal());
        assertArrayEquals(new int[]{1, 2, 2, 2, 2, 1}, rs.getAmounts(true));

        rs.subtract(1, SOCResourceConstants.UNKNOWN, true);  // unknown and param is true: convert
        assertEquals(9, rs.getTotal());
        assertEquals(0, rs.getKnownTotal());
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 9}, rs.getAmounts(true));
    }

    /**
     * Test that {@link SOCResourceSet#subtract(int, int)} removes from unknown
     * when too many of a resource type is removed.
     * @see #removeUnknown_ConvertToUnknown()
     * @since 2.5.00
     */
    @Test
    public void removeTooMany_ConvertToUnknown()
    {
        SOCResourceSet rs = onePerType();

        rs.subtract(1, SOCResourceConstants.CLAY);  // not too many
        assertEquals(4, rs.getTotal());
        assertEquals(4, rs.getKnownTotal());
        assertArrayEquals(new int[]{0, 1, 1, 1, 1, 0}, rs.getAmounts(true));

        rs.subtract(1, SOCResourceConstants.CLAY);  // now is too many
        assertEquals(3, rs.getTotal());
        assertEquals(4, rs.getKnownTotal());
        assertArrayEquals(new int[]{0, 1, 1, 1, 1, -1}, rs.getAmounts(true));
    }

    /**
     * Test that {@link SOCResourceSet#subtract(soc.game.ResourceSet)} clips to 0
     * when too many of a resource type are removed, instead of having negative amounts.
     * @see #removeSet_ConvertToUnknown()
     * @since 2.5.00
     */
    @Test
    public void removeSet_ClipTo0()
    {
        SOCResourceSet rs = onePerType();

        rs.subtract(new SOCResourceSet(0, 2, 1, 0, 3, 0));
        assertEquals(2, rs.getTotal());
        assertEquals(2, rs.getKnownTotal());
        assertArrayEquals(new int[]   {1, 0, 0, 1, 0, 0}, rs.getAmounts(true));
    }

    /**
     * Test that {@link SOCResourceSet#subtract(soc.game.ResourceSet, boolean)}
     * removes from unknown when too many of a resource type are removed, but not if
     * the player has at least that many.
     * Also tests {@link SOCResourceSet#getKnownTotal()} vs {@code getTotal()}.
     * @see #removeSet_ClipTo0()
     * @since 2.5.00
     */
    @Test
    public void removeSet_ConvertToUnknown()
    {
        SOCResourceSet rs = new SOCResourceSet(3, 3, 3, 0, 0, 4);
        assertEquals(13, rs.getTotal());
        assertEquals(9, rs.getKnownTotal());

        // if not too many removed, no conversion
        rs.subtract(new SOCResourceSet(0, 3, 0, 0, 0, 0), true);
        assertEquals(10, rs.getTotal());
        assertEquals(6, rs.getKnownTotal());
        assertArrayEquals(new int[]   {3, 0, 3, 0, 0, 4}, rs.getAmounts(true));

        rs.subtract(new SOCResourceSet(0, 0, 5, 0, 0, 0), true);
        assertEquals(5, rs.getTotal());
        assertEquals(3, rs.getKnownTotal());
        assertArrayEquals(new int[]   {3, 0, 0, 0, 0, 2}, rs.getAmounts(true));
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
        assertEquals(5, rs.getKnownTotal());
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
        assertEquals(0, rs1.getAmount(SOCResourceConstants.UNKNOWN));
        assertArrayEquals(new int[]{2, 2, 2, 2, 2, 0}, rs1.getAmounts(true));
        assertEquals(10, rs1.getTotal());
        assertEquals(10, rs1.getKnownTotal());
        assertTrue(rs1.contains(SOCResourceConstants.WHEAT));

        rs2.add(2, SOCResourceConstants.UNKNOWN);
        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 2}, rs2.getAmounts(true));
        assertEquals(7, rs2.getTotal());
        assertEquals(5, rs2.getKnownTotal());
        rs1.add(rs2);
        assertEquals(3, rs1.getAmount(SOCResourceConstants.CLAY));
        assertEquals(3, rs1.getAmount(SOCResourceConstants.WHEAT));
        assertEquals(3, rs1.getAmount(SOCResourceConstants.ORE));
        assertEquals(3, rs1.getAmount(SOCResourceConstants.WOOD));
        assertEquals(3, rs1.getAmount(SOCResourceConstants.SHEEP));
        assertEquals(2, rs1.getAmount(SOCResourceConstants.UNKNOWN));
        assertArrayEquals(new int[]{3, 3, 3, 3, 3, 2}, rs1.getAmounts(true));
        assertEquals(17, rs1.getTotal());
        assertEquals(15, rs1.getKnownTotal());
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
     * @since 2.5.00
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
     * Test {@link SOCResourceSet#setAmounts(soc.game.ResourceSet)}.
     * @since 2.6.00
     */
    @Test
    public void testSetAmounts()
    {
        SOCResourceSet rs = new SOCResourceSet(1, 0, 2, 0, 3, 1);
        assertArrayEquals(new int[]{1, 0, 2, 0, 3, 1}, rs.getAmounts(true));

        rs.setAmounts(onePerType());
        assertArrayEquals("from SOCResourceSet", new int[]{1, 1, 1, 1, 1, 0}, rs.getAmounts(true));

        rs.setAmounts(new SavedGameModel.KnownResourceSet(new SOCResourceSet(1, 0, 2, 0, 3, 0)));
        assertArrayEquals("from other ResourceSet impl", new int[]{1, 0, 2, 0, 3, 0}, rs.getAmounts(true));
    }

    /**
     * Test {@link SOCResourceSet#hashCode()}
     * @since 2.5.00
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
