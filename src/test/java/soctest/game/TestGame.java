/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCGame}.
 *
 * @see TestBoard
 * @see TestPlayer
 * @since 2.3.00
 */
public class TestGame
{

    @Test
    @SuppressWarnings("all")  // "Comparing identical expressions"
    public void test_gameState_startsVsRoll()
    {
        assertTrue((SOCGame.ROLL_OR_CARD - 1) == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE);
    }

    /**
     * Client-side tests for {@link SOCGame#hasRolledSeven()}.
     * @since 2.4.50
     */
    @Test
    public void testRolled7_client()
    {
        SOCGame ga = new SOCGame("test");
        assertFalse(ga.hasRolledSeven());

        ga.setCurrentDice(5);
        assertFalse(ga.hasRolledSeven());

        ga.setCurrentDice(7);
        assertTrue(ga.hasRolledSeven());

        ga.setCurrentDice(5);
        assertTrue(ga.hasRolledSeven());
    }

}
