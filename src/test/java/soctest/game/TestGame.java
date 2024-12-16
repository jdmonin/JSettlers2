/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020-2024 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Set;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soctest.server.savegame.TestLoadgame;  // for javadocs only

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCGame}.
 *<P>
 * Some SOCGame methods are tested elsewhere, like {@link TestPlayer#testTradeAndStats()}
 * and {@link TestLoadgame#testLoadSeaBoard()}.
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

    /**
     * Test {@link SOCGame#saveLargestArmyState()} and {@link SOCGame#restoreLargestArmyState()}.
     * @since 2.7.00
     */
    @Test
    public void testSaveRestoreLargestArmyState()
    {
        final SOCGame ga = new SOCGame("testSaveRestoreLargestArmyState");
        assertEquals(SOCGame.NEW, ga.getGameState());

        ga.addPlayer("tplayer", 2);
        final SOCPlayer pl2 = ga.getPlayer(2);
        ga.setGameState(SOCGame.PLAY1);
        ga.setCurrentPlayerNumber(2);

        assertEquals(null, ga.getPlayerWithLargestArmy());
        ga.restoreLargestArmyState();
        assertEquals("was nothing to restore yet", null, ga.getPlayerWithLargestArmy());

        ga.saveLargestArmyState();
        pl2.setNumKnights(3);
        ga.updateLargestArmy();
        assertEquals(pl2, ga.getPlayerWithLargestArmy());

        pl2.setNumKnights(2);
        ga.restoreLargestArmyState();
        assertEquals(null, ga.getPlayerWithLargestArmy());

        pl2.setNumKnights(3);
        ga.updateLargestArmy();
        assertEquals(pl2, ga.getPlayerWithLargestArmy());

        ga.addPlayer("player3", 3);
        final SOCPlayer pl3 = ga.getPlayer(3);

        ga.saveLargestArmyState();
        pl3.setNumKnights(4);
        ga.updateLargestArmy();
        assertEquals(pl3, ga.getPlayerWithLargestArmy());

        pl3.setNumKnights(0);
        ga.restoreLargestArmyState();
        assertEquals(pl2, ga.getPlayerWithLargestArmy());
    }

    /**
     * Test {@link SOCGame#getDurationSeconds()}, {@link SOCGame#setTimeSinceCreated(int)},
     * and {@link SOCGame#setDurationSecondsFinished(int)}.
     * @see soctest.server.savegame.TestLoadgame#checkReloaded_ClassicBotturn(soc.server.savegame.SavedGameModel)
     * @since 2.7.00
     */
    @Test
    public void testTimeDurations()
    {
        SOCGame ga = new SOCGame("test");
        assertEquals(SOCGame.NEW, ga.getGameState());

        int duration = ga.getDurationSeconds();
        assertTrue("getDurationSeconds() < 2", (duration < 2));

        try
        {
            ga.setTimeSinceCreated(-1);
            fail("setTimeSinceCreated(-1) should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        ga.setTimeSinceCreated(7);
        assertEquals(7, ga.getDurationSeconds());

        // can setDurationSecondsFinished in gameState OVER or 0, but not earlier states:

        assertEquals(SOCGame.NEW, ga.getGameState());
        try
        {
            ga.setDurationSecondsFinished(-1);
            fail("setDurationSecondsFinished(..) should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try
        {
            ga.setDurationSecondsFinished(0);
            fail("setDurationSecondsFinished(..) should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        ga.setDurationSecondsFinished(42);
        assertEquals(7, ga.getDurationSeconds());
        ga.setGameState(SOCGame.OVER);
        assertEquals(42, ga.getDurationSeconds());

        ga = new SOCGame("test2");
        ga.setGameState(SOCGame.PLAY1);

        ga.setTimeSinceCreated(7);
        assertEquals(7, ga.getDurationSeconds());

        try
        {
            ga.setDurationSecondsFinished(42);
            fail("setDurationSecondsFinished(..) should have thrown IllegalStateException");
        } catch (IllegalStateException e) {}

        ga.setGameState(SOCGame.OVER);

        ga.setTimeSinceCreated(11);
        assertEquals("can still call setTimeSinceCreated at OVER", 11, ga.getDurationSeconds());

        ga.setDurationSecondsFinished(42);
        assertEquals(42, ga.getDurationSeconds());

        assertTrue(SOCGame.RESET_OLD > SOCGame.OVER);
        ga.setGameState(SOCGame.RESET_OLD);
        ga.setDurationSecondsFinished(55);
        assertEquals(55, ga.getDurationSeconds());
    }

    /**
     * Test {@link SOCGame#isMemberChatAllowed(String)}, {@link SOCGame#setMemberChatAllowed(String, boolean)},
     * and {@link SOCGame#getMemberChatAllowList()}
     * for the game's Chat Allow List.
     * @since 2.7.00
     */
    @Test
    public void testChatAllowList()
    {
        SOCGame ga = new SOCGame("testChat");
        ga.addPlayer("p2", 2);
        ga.addPlayer("p3", 3);

        assertFalse("isMemberChatAllowed always false before initAtServer called", ga.isMemberChatAllowed("p2"));
        assertFalse("isMemberChatAllowed always false before initAtServer called", ga.isMemberChatAllowed("anotherName"));
        try
        {
            ga.setMemberChatAllowed("anotherName", true);
            fail("setMemberChatAllowed before initAtServer should throw exception");
        } catch (IllegalStateException e) {
            assertFalse("isMemberChatAllowed always false before initAtServer called", ga.isMemberChatAllowed("anotherName"));
        }
        assertNull("getMemberChatAllowList should be null before initAtServer", ga.getMemberChatAllowList());

        // set up game fields as if at server, but don't create a board that won't be used
        ga.initAtServer();
        assertEquals("game not started yet", SOCGame.NEW, ga.getGameState());

        assertTrue("isMemberChatAllowed true for player 2 after initAtServer called", ga.isMemberChatAllowed("p2"));
        assertTrue("isMemberChatAllowed true for player 3 after initAtServer called", ga.isMemberChatAllowed("p3"));
        assertFalse("isMemberChatAllowed false for others before initAtServer called", ga.isMemberChatAllowed("anotherName"));

        // returned view is accurate and read-only
        {
            Set<String> s = ga.getMemberChatAllowList();
            assertNotNull(s);
            assertEquals(2, s.size());
            assertTrue(s.contains("p2"));
            assertTrue(s.contains("p3"));
            try
            {
                s.remove("p2");
                fail("getMemberChatAllowList list should be read-only");
            } catch (UnsupportedOperationException e) {}
            try
            {
                s.add("another");
                fail("getMemberChatAllowList list should be read-only");
            } catch (UnsupportedOperationException e) {}
        }

        ga.setMemberChatAllowed("anotherName", true);
        assertTrue("isMemberChatAllowed true after adding them", ga.isMemberChatAllowed("anotherName"));
        {
            Set<String> s = ga.getMemberChatAllowList();
            assertNotNull(s);
            assertEquals(3, s.size());
            assertTrue(s.contains("anotherName"));
        }
        ga.setMemberChatAllowed("anotherName", false);
        assertFalse("isMemberChatAllowed false after removing them", ga.isMemberChatAllowed("anotherName"));

        ga.setMemberChatAllowed(null, true);  // doesn't crash if null
        assertFalse("null is OK", ga.isMemberChatAllowed(null));

        ga.setMemberChatAllowed("p2", false);
        assertFalse("isMemberChatAllowed false after removing player 2", ga.isMemberChatAllowed("p2"));
        assertTrue("isMemberChatAllowed still true for player 3 after removing p2", ga.isMemberChatAllowed("p3"));
    }

}
