/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2022 Jeremy D Monin <jeremy@nand.net>
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

package soctest.message;

import soc.message.SOCGameStats;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCGameStats}.
 * @since 2.7.00
 */
public class TestSOCGameStats
{

    /**
     * Test constructors, including stats types.
     */
    @Test
    public void testConstructors()
    {
        final int[] INTS_123 = {1, 2, 3};
        final boolean[] BOOLS_TFT = {true, false, true};

        try
        {
            @SuppressWarnings("unused")
            SOCGameStats msg = new SOCGameStats("ga", SOCGameStats.TYPE_PLAYERS, INTS_123);
            fail("Constructor(TYPE_PLAYERS) should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try
        {
            @SuppressWarnings("unused")
            SOCGameStats msg = new SOCGameStats("ga", 0, INTS_123);
            fail("Constructor(0) should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try
        {
            @SuppressWarnings("unused")
            SOCGameStats msg = new SOCGameStats("ga", -1, INTS_123);
            fail("Constructor(-1) should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try
        {
            @SuppressWarnings("unused")
            SOCGameStats msg = new SOCGameStats("ga", SOCGameStats.TYPE_TIMING, null);
            fail("Constructor(null) should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try
        {
            @SuppressWarnings("unused")
            SOCGameStats msg = new SOCGameStats("ga", SOCGameStats.TYPE_TIMING, new int[]{});
            fail("Constructor(empty[]) should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        SOCGameStats msg = new SOCGameStats("ga", SOCGameStats.TYPE_TIMING, INTS_123);
        assertEquals(SOCGameStats.TYPE_TIMING, msg.getStatType());
        assertEquals("ga", msg.getGame());
        assertArrayEquals(new int[] {1, 2, 3} , msg.getScores());
        assertNull(msg.getRobotSeats());

        msg = new SOCGameStats("ga", INTS_123, BOOLS_TFT);
        assertEquals(SOCGameStats.TYPE_PLAYERS, msg.getStatType());
        assertEquals("ga", msg.getGame());
        assertArrayEquals(new int[]{1, 2, 3} , msg.getScores());
        assertArrayEquals(new boolean[]{true, false, true}, msg.getRobotSeats());
    }

}
