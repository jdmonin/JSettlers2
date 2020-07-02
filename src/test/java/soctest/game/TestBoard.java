/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2019-2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCBoard;
import soc.game.SOCBoard4p;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCBoard}.
 *
 * @see TestBoardLarge
 * @since 2.0.00
 */
public class TestBoard
{
    /**
     * Test the "facing" constants' values: {@link SOCBoard#FACING_NE} etc.
     * @since 2.4.00
     */
    @Test
    public void testFacingConstants()
    {
        assertEquals(1, SOCBoard.FACING_NE);
        assertEquals(2, SOCBoard.FACING_E);
        assertEquals(3, SOCBoard.FACING_SE);
        assertEquals(4, SOCBoard.FACING_SW);
        assertEquals(5, SOCBoard.FACING_W);
        assertEquals(6, SOCBoard.FACING_NW);
    }

    /**
     * For a pair of edge coordinates, test
     * {@link SOCBoard#getNodeBetweenAdjacentEdges(int, int)} with parameters in both orders,
     * and {@link SOCBoard#getAdjacentNodesToEdge_arr(int)}, to see if they return {@code nodeBetween}.
     * @param expectFail  True if {@code nodeBetween} isn't between the two edges
     *     and {@code getNodeBetweenAdjacentEdges} should throw {@link IllegalArgumentException}
     * @see TestBoardLarge#doTestPair_getNodeBetweenAdjacentEdges(soc.game.SOCBoardLarge, int, int, int, boolean)
     */
    private static void doTestPair_getNodeBetweenAdjacentEdges
        (final SOCBoard board, final int edgeA, final int edgeB, final int nodeBetween, final boolean expectFail)
    {
        String desc = "getNodeBetweenAdjacentEdges(0x" + Integer.toHexString(edgeA)
            + ", 0x" + Integer.toHexString(edgeB) + ")";
        try
        {
            final int n = board.getNodeBetweenAdjacentEdges(edgeA, edgeB);
            if (expectFail)
                fail(desc + " should have thrown exception");
            assertEquals(desc + " incorrect", nodeBetween, n);
        }
        catch (IllegalArgumentException e) {
            if (! expectFail)
                throw e;
        }

        desc = "getNodeBetweenAdjacentEdges(0x" + Integer.toHexString(edgeB)
            + ", 0x" + Integer.toHexString(edgeA) + ")";
        try
        {
            final int n = board.getNodeBetweenAdjacentEdges(edgeB, edgeA);
            if (expectFail)
                fail(desc + " should have thrown exception");
            assertEquals(desc + " incorrect", nodeBetween, n);
        }
        catch (IllegalArgumentException e) {
            if (! expectFail)
                throw e;
        }

        if (expectFail)
            return;

        int[] nodes = board.getAdjacentNodesToEdge_arr(edgeA);
        assertTrue("Expected 0x" + Integer.toHexString(nodeBetween) + " in getAdjacentNodesToEdge_arr(0x"
            + Integer.toHexString(edgeA) + "), was " + Arrays.toString(nodes),
            (nodes[0] == nodeBetween) || (nodes[1] == nodeBetween));

        nodes = board.getAdjacentNodesToEdge_arr(edgeB);
        assertTrue("Expected 0x" + Integer.toHexString(nodeBetween) + " in getAdjacentNodesToEdge_arr(0x"
            + Integer.toHexString(edgeB) + "), was " + Arrays.toString(nodes),
            (nodes[0] == nodeBetween) || (nodes[1] == nodeBetween));
    }

    @Test
    public void test_getNodeBetweenAdjacentEdges()
    {
        SOCBoard b = new SOCBoard4p(null);  // SOCBoard4p and SOCBoard6p use same geometry methods in base SOCBoard

        // See RST Dissertation figure A.3: Edge Coordinates and A.2: Node Coordinates

        // adjacent to vertical [Even,Even] edge: Figure A.13
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x26, 0x16, 0x27, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x26, 0x27, 0x27, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x26, 0x25, 0x36, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x26, 0x36, 0x36, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x26, 0x26, 0, true);  // not adjacent: same edge twice
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x26, 0x04, 0, true);  // same row 1 hex away
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x26, 0x48, 0, true);  // same row
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x26, 0x06, 0, true);  // 2 edges away

        // adjacent to northeast-diagonal [Even, Odd] edge: Fig A. 11
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x47, 0x36, 0x47, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x47, 0x46, 0x47, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x47, 0x48, 0x58, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x47, 0x48, 0x58, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x47, 0x47, 0, true);  // not adjacent: same edge twice
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x47, 0x67, 0, true);  // same axis 1 hex away
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x47, 0x27, 0, true);  // same axis
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x47, 0x49, 0, true);  // 2 edges away

        // adjacent to southeast-diagonal [Odd, Even] edge: Fig A. 12
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x52, 0x41, 0x52, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x52, 0x42, 0x52, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x52, 0x62, 0x63, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x52, 0x63, 0x63, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x52, 0x52, 0, true);  // not adjacent: same edge twice
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x52, 0x54, 0, true);  // same axis 1 hex away
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x52, 0x50, 0, true);  // same axis
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x52, 0x43, 0, true);  // 2 edges away
    }

}
