/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020-2022 Jeremy D Monin <jeremy@nand.net>
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

    /**
     * Compare relative values/positions of various game states.
     *<P>
     * Before v2.6.00 this was {@code test_gameState_startsVsRoll}.
     */
    @Test
    @SuppressWarnings("all")  // "Comparing identical expressions"
    public void test_gameStates_relativeValues()
    {
        assertTrue((SOCGame.ROLL_OR_CARD - 1) == SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE);
        assertTrue((SOCGame.OVER - 10) == SOCGame.LOADING);
        assertTrue(SOCGame.LOADING < SOCGame.LOADING_RESUMING);
        assertTrue(SOCGame.LOADING_RESUMING < SOCGame.OVER);
    }

    /**
     * Client-side tests for {@link SOCGame#hasRolledSeven()}.
     * @since 2.5.00
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

    /**
     * Test {@link SOCGame#setNextDevCard(int)}, lightly test {@link SOCGame#buyDevCard()}.
     * @since 2.5.00
     */
    @Test
    public void testSetNextDevCard()
    {
        final int[] ORIG_CARDS = {5, 2, 2, 1};

        SOCGame ga = new SOCGame("test");

        // set up dev cards as if at server, but don't create a board that won't be used
        ArrayList<Integer> cardList = new ArrayList<>();
        for (int ctype : ORIG_CARDS)
            cardList.add(ctype);
        ga.initAtServer();
        ga.setFieldsForLoad(cardList, SOCGame.ROLL_OR_CARD, null, false, false, false, false, false);

        // verify cardList before any moves
        assertArrayEquals(ORIG_CARDS, ga.getDevCardDeck());

        // no change needed
        ga.setNextDevCard(1);
        assertArrayEquals(ORIG_CARDS, ga.getDevCardDeck());

        // swap with first found
        ga.setNextDevCard(2);
        assertArrayEquals(new int[]{5, 2, 1, 2}, ga.getDevCardDeck());

        int ctype = ga.buyDevCard();
        assertEquals(2, ctype);
        assertArrayEquals(new int[]{5, 2, 1}, ga.getDevCardDeck());

        // swap finds at far end of array
        ga.setNextDevCard(5);
        assertArrayEquals(new int[]{1, 2, 5}, ga.getDevCardDeck());

        // replaces if type not found
        ga.setNextDevCard(4);
        assertArrayEquals(new int[]{1, 2, 4}, ga.getDevCardDeck());

        ctype = ga.buyDevCard();
        assertEquals(4, ctype);
        assertArrayEquals(new int[]{1, 2}, ga.getDevCardDeck());

        // works at length 2
        ga.setNextDevCard(2);
        assertArrayEquals(new int[]{1, 2}, ga.getDevCardDeck());
        ga.setNextDevCard(1);
        assertArrayEquals(new int[]{2, 1}, ga.getDevCardDeck());
        ga.setNextDevCard(5);
        assertArrayEquals(new int[]{2, 5}, ga.getDevCardDeck());

        ctype = ga.buyDevCard();
        assertEquals(5, ctype);
        assertArrayEquals(new int[]{2}, ga.getDevCardDeck());

        // works at length 1
        ga.setNextDevCard(2);
        assertArrayEquals(new int[]{2}, ga.getDevCardDeck());
        ga.setNextDevCard(4);
        assertArrayEquals(new int[]{4}, ga.getDevCardDeck());

        ctype = ga.buyDevCard();
        assertEquals(4, ctype);
        assertArrayEquals(new int[]{}, ga.getDevCardDeck());

        // throws ISE at length 0
        boolean threwISE = false;
        try
        {
            ga.setNextDevCard(2);
        } catch (IllegalStateException e) {
            threwISE = true;
        }
        if (! threwISE)
            fail("should have thrown IllegalStateException");
    }

}
