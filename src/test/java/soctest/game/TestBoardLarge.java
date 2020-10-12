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
import soc.game.SOCBoardLarge;
import soc.util.IntPair;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCBoardLarge}.
 *
 * @see TestBoard
 * @since 2.0.00
 */
public class TestBoardLarge
{

    /**
     * Test a few common constants and default {@link SOCBoardLarge#getBoardSize(soc.game.SOCGameOptionSet) getBoardSize(null)}.
     * @since 2.4.00
     */
    @Test
    public void testConstants()
    {
        assertEquals(16, SOCBoardLarge.BOARDHEIGHT_LARGE);
        assertEquals(18, SOCBoardLarge.BOARDWIDTH_LARGE);
        assertEquals(new IntPair(16, 18), SOCBoardLarge.getBoardSize(null));
    }

    /**
     * For a pair of hex coordinates, test {@link SOCBoardLarge#getAdjacentHexToHex(int, int)} in both directions.
     * 
     * @param hexA  Hex to test from
     * @param hexB  Expected adjacent hex from {@code hexA} in {@code facing} direction, or 0 if expected off board
     * @param facing  Facing direction from {@code hexA} to {@code hexB}
     * @param expectFail The exception a failing test is expected to throw, or {@code null} if test should succeed.
     *     Checked only in "forward" direction {@code hexA} to {@code hexB}, not reverse.
     * @since 2.4.00
     */
    private static void doTestPair_getAdjacentHexToHex
        (final SOCBoardLarge board, final int hexA, final int hexB, final int facing,
         final Class<? extends Throwable> expectFail)
    {
        String desc = "getAdjacentHexToHex(0x" + Integer.toHexString(hexA) + ", " + facing + ')';

        try
        {
            final int adjacFromA = board.getAdjacentHexToHex(hexA, facing);
            if (expectFail != null)
                fail(desc + " should have thrown exception");
            assertEquals(desc + " incorrect", hexB, adjacFromA);
        } catch (Throwable th) {
            if ((expectFail == null) || ! expectFail.isInstance(th))
                throw th;
        }

        if (hexB != 0)
        {
            int faceBack = facing + 3;
            if (faceBack > 6)
                faceBack -= 6;

            final int adjacFromB = board.getAdjacentHexToHex(hexB, faceBack);
            desc = "getAdjacentHexToHex(0x" + Integer.toHexString(hexB) + ", " + faceBack + ')';
            assertEquals(desc + " incorrect", hexA, adjacFromB);
        }
    }

    /**
     * Test {@link SOCBoardLarge#getAdjacentHexToHex(int, int)}.
     * @since 2.4.00
     */
    @Test
    public void test_getAdjacentHexToHex()
    {
        final SOCBoardLarge b = new SOCBoardLarge(null, 4, SOCBoardLarge.getBoardSize(null));
        assertEquals(0x10, b.getBoardHeight());  // also BOARDHEIGHT_LARGE
        assertEquals(0x12, b.getBoardWidth());   // also BOARDWIDTH_LARGE

        // this hex is valid in all 6 facing directions
        doTestPair_getAdjacentHexToHex(b, 0x0305, 0x0106, SOCBoard.FACING_NE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0305, 0x0307, SOCBoard.FACING_E,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0305, 0x0506, SOCBoard.FACING_SE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0305, 0x0504, SOCBoard.FACING_SW, null);
        doTestPair_getAdjacentHexToHex(b, 0x0305, 0x0303, SOCBoard.FACING_W,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0305, 0x0104, SOCBoard.FACING_NW, null);

        // bad facing ranges
        doTestPair_getAdjacentHexToHex(b, 0x0305, 0, -1, IllegalArgumentException.class);
        doTestPair_getAdjacentHexToHex(b, 0x0305, 0, 0, IllegalArgumentException.class);
        doTestPair_getAdjacentHexToHex(b, 0x0305, 0, 7, IllegalArgumentException.class);

        // hexA out of bounds (boardHeight, boardWidth)
        doTestPair_getAdjacentHexToHex(b, 0x9305, 0, SOCBoard.FACING_E, IndexOutOfBoundsException.class);
        doTestPair_getAdjacentHexToHex(b, 0x0395, 0, SOCBoard.FACING_E, IndexOutOfBoundsException.class);

        // hexA not in a hex row
        doTestPair_getAdjacentHexToHex(b, 0x0404, 0, SOCBoard.FACING_E, IndexOutOfBoundsException.class);

        // at top border of board
        doTestPair_getAdjacentHexToHex(b, 0x0108, 0, SOCBoard.FACING_NE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0108, 0x010A, SOCBoard.FACING_E,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0108, 0x0309, SOCBoard.FACING_SE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0108, 0x0307, SOCBoard.FACING_SW, null);
        doTestPair_getAdjacentHexToHex(b, 0x0108, 0x0106, SOCBoard.FACING_W,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0108, 0, SOCBoard.FACING_NW, null);

        // at left border
        doTestPair_getAdjacentHexToHex(b, 0x0502, 0x0301, SOCBoard.FACING_NW, null);
        doTestPair_getAdjacentHexToHex(b, 0x0502, 0, SOCBoard.FACING_W,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0502, 0x0701, SOCBoard.FACING_SW, null);
        doTestPair_getAdjacentHexToHex(b, 0x0502, 0x0504, SOCBoard.FACING_E,  null);

        doTestPair_getAdjacentHexToHex(b, 0x0701, 0, SOCBoard.FACING_NW, null);
        doTestPair_getAdjacentHexToHex(b, 0x0701, 0, SOCBoard.FACING_W,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0701, 0, SOCBoard.FACING_SW, null);
        doTestPair_getAdjacentHexToHex(b, 0x0701, 0x0703, SOCBoard.FACING_E,  null);

        // at bottom-left corner
        doTestPair_getAdjacentHexToHex(b, 0x0F01, 0x0D02, SOCBoard.FACING_NE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0F01, 0x0F03, SOCBoard.FACING_E,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0F01, 0, SOCBoard.FACING_SE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0F01, 0, SOCBoard.FACING_SW, null);
        doTestPair_getAdjacentHexToHex(b, 0x0F01, 0, SOCBoard.FACING_W,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0F01, 0, SOCBoard.FACING_NW, null);

        // at right border
        doTestPair_getAdjacentHexToHex(b, 0x0510, 0x0311, SOCBoard.FACING_NE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0510, 0, SOCBoard.FACING_E,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0510, 0x0711, SOCBoard.FACING_SE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0510, 0x050E, SOCBoard.FACING_W,  null);

        doTestPair_getAdjacentHexToHex(b, 0x0711, 0, SOCBoard.FACING_NE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0711, 0, SOCBoard.FACING_E,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0711, 0, SOCBoard.FACING_SE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0711, 0x070F, SOCBoard.FACING_W,  null);

        // at bottom-right corner
        doTestPair_getAdjacentHexToHex(b, 0x0F11, 0, SOCBoard.FACING_NE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0F11, 0, SOCBoard.FACING_E,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0F11, 0, SOCBoard.FACING_SE, null);
        doTestPair_getAdjacentHexToHex(b, 0x0F11, 0, SOCBoard.FACING_SW, null);
        doTestPair_getAdjacentHexToHex(b, 0x0F11, 0x0F0F, SOCBoard.FACING_W,  null);
        doTestPair_getAdjacentHexToHex(b, 0x0F11, 0x0D10, SOCBoard.FACING_NW, null);
    }

    /**
     * For a pair of edge coordinates, test
     * {@link SOCBoardLarge#getNodeBetweenAdjacentEdges(int, int)} with parameters in both orders,
     * and {@link SOCBoardLarge#getAdjacentNodesToEdge_arr(int)}, to see if they return {@code nodeBetween}.
     * @param expectFail  True if {@code nodeBetween} isn't between the two edges
     *     and {@code getNodeBetweenAdjacentEdges} should throw {@link IllegalArgumentException}
     * @see TestBoard#doTestPair_getNodeBetweenAdjacentEdges(soc.game.SOCBoard, int, int, int, boolean)
     */
    private static void doTestPair_getNodeBetweenAdjacentEdges
        (final SOCBoardLarge board, final int edgeA, final int edgeB, final int nodeBetween, final boolean expectFail)
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

    /**
     * Test {@link SOCBoardLarge#getNodeBetweenAdjacentEdges(int, int)}
     * and {@link SOCBoardLarge#getAdjacentNodesToEdge_arr(int)}.
     */
    @Test
    public void test_getNodeBetweenAdjacentEdges()
    {
        SOCBoardLarge b = new SOCBoardLarge(null, 4, SOCBoardLarge.getBoardSize(null));

        // adjacent to vertical "|" edge
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x505, 0x404, 0x405, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x505, 0x405, 0x405, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x505, 0x604, 0x605, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x505, 0x605, 0x605, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x505, 0x505, 0, true);  // not adjacent: same edge twice
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x505, 0x503, 0, true);  // same row 1 hex away
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x505, 0x507, 0, true);  // same row
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x505, 0x403, 0, true);  // 2 edges away

        // adjacent to northeast-diagonal "/" edge
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x407, 0x406, 0x407, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x407, 0x507, 0x407, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x407, 0x308, 0x408, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x407, 0x408, 0x408, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x407, 0x407, 0, true);  // not adjacent: same edge twice
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x407, 0x206, 0, true);  // same axis 1 hex away
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x407, 0x608, 0, true);  // same axis
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x407, 0x405, 0, true);  // 2 edges away

        // adjacent to southeast-diagonal "\" edge
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x406, 0x306, 0x406, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x406, 0x405, 0x406, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x406, 0x407, 0x407, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x406, 0x507, 0x407, false);
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x406, 0x406, 0, true);  // not adjacent: same edge twice
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x406, 0x207, 0, true);  // same axis 1 hex away
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x406, 0x605, 0, true);  // same axis
        doTestPair_getNodeBetweenAdjacentEdges(b, 0x406, 0x505, 0, true);  // 2 edges away
    }

}
