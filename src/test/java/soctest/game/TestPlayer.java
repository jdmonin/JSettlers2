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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCInventory;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.game.SOCSettlement;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCPlayer}.
 *
 * @see TestGame
 * @since 2.3.00
 */
public class TestPlayer
{

    /**
     * Test {@link SOCPlayer#getSettlementOrCityAtNode(int)}.
     * @since 2.4.00
     */
    @Test
    public void testGetSettlementOrCityAtNode()
    {
        final int node = 0x1122;

        SOCGame ga = new SOCGame("test");
        ga.addPlayer("tplayer", 2);
        SOCPlayer pl = ga.getPlayer(2);
        SOCBoard board = ga.getBoard();

        final int unoccupiedNode = 0x2233;

        assertNull(pl.getSettlementOrCityAtNode(unoccupiedNode));
        assertFalse(pl.hasSettlementOrCityAtNode(unoccupiedNode));
        assertFalse(pl.hasSettlementAtNode(unoccupiedNode));
        assertFalse(pl.hasCityAtNode(unoccupiedNode));
        assertNull(board.settlementAtNode(unoccupiedNode));

        ga.putPiece(new SOCSettlement(pl, node, null));

        SOCPlayingPiece piece = pl.getSettlementOrCityAtNode(node);
        assertNotNull(piece);
        assertTrue(pl.hasSettlementOrCityAtNode(node));
        assertTrue(pl.hasSettlementAtNode(node));
        assertFalse(pl.hasCityAtNode(node));
        assertEquals(SOCPlayingPiece.SETTLEMENT, piece.getType());
        piece = board.settlementAtNode(node);
        assertNotNull(piece);
        assertEquals(SOCPlayingPiece.SETTLEMENT, piece.getType());
        assertEquals(2, piece.getPlayerNumber());

        ga.putPiece(new SOCCity(pl, node, board));  // replaces settlement

        assertNull(pl.getSettlementOrCityAtNode(unoccupiedNode));  // cities list no longer empty; still unoccupied
        assertFalse(pl.hasSettlementOrCityAtNode(unoccupiedNode));
        assertFalse(pl.hasSettlementAtNode(unoccupiedNode));
        assertFalse(pl.hasCityAtNode(unoccupiedNode));
        assertNull(board.settlementAtNode(unoccupiedNode));

        piece = pl.getSettlementOrCityAtNode(node);
        assertNotNull(piece);
        assertTrue(pl.hasSettlementOrCityAtNode(node));
        assertTrue(pl.hasCityAtNode(node));
        assertFalse(pl.hasSettlementAtNode(node));
        assertEquals(SOCPlayingPiece.CITY, piece.getType());
        piece = board.settlementAtNode(node);
        assertNotNull(piece);
        assertEquals(SOCPlayingPiece.CITY, piece.getType());
        assertEquals(2, piece.getPlayerNumber());
    }

    @Test
    public void testDiscardRoundDown()
    {
        SOCPlayer pl = new SOCPlayer(2, new SOCGame("test"));
        pl.addRolledResources(new SOCResourceSet(1, 2, 2, 2, 1, 0));
        assertEquals(8, pl.getResources().getTotal());  // SOCResourceSet is mostly tested elsewhere
        assertEquals("discard should be half", 4, pl.getCountToDiscard());
        pl.addRolledResources(new SOCResourceSet(1, 0, 0, 0, 0, 0));
        assertEquals("discard should round down", 4, pl.getCountToDiscard());
    }

    @Test
    public void testInventoryDevCardsVP()
    {
        SOCPlayer pl = new SOCPlayer(2, new SOCGame("test"));
        assertEquals(0, pl.getTotalVP());
        SOCInventory i = pl.getInventory();
        assertFalse(pl.hasUnplayedDevCards());

        i.addDevCard(1, SOCInventory.NEW, SOCDevCardConstants.ROADS);
        assertTrue(pl.hasUnplayedDevCards());
        assertEquals(0, pl.getPublicVP());
        assertEquals(0, pl.getTotalVP());

        i.addDevCard(1, SOCInventory.NEW, SOCDevCardConstants.UNIV);
        assertEquals("VP cards should be hidden from public VP", 0, pl.getPublicVP());
        assertEquals("VP cards should count towards total VP", 1, pl.getTotalVP());
    }

    /**
     * Test {@link SOCPlayer#updateDevCardsPlayed(int)}, {@link SOCPlayer#getDevCardsPlayed()},
     * and related stats fields ({@link SOCPlayer#numDISCCards} etc).
     * @since 2.4.50
     */
    @Test
    public void testDevCardStats()
    {
        SOCPlayer pl = new SOCPlayer(2, new SOCGame("test"));
        assertEquals(0, pl.numDISCCards);
        assertEquals(0, pl.numMONOCards);
        assertEquals(0, pl.numRBCards);
        assertNull(pl.getDevCardsPlayed());

        pl.updateDevCardsPlayed(SOCDevCardConstants.UNKNOWN);
        pl.updateDevCardsPlayed(SOCDevCardConstants.UNIV);
        pl.updateDevCardsPlayed(-42);  // out of range but should ignore it, not throw any exception
        pl.updateDevCardsPlayed(42);
        assertEquals(0, pl.numDISCCards);
        assertEquals(0, pl.numMONOCards);
        assertEquals(0, pl.numRBCards);
        List<Integer> expectedCards = new ArrayList<Integer>
            (Arrays.asList(SOCDevCardConstants.UNKNOWN, SOCDevCardConstants.UNIV, -42, 42));
        assertEquals(expectedCards, pl.getDevCardsPlayed());

        pl.updateDevCardsPlayed(SOCDevCardConstants.DISC);
        pl.updateDevCardsPlayed(SOCDevCardConstants.MONO);
        pl.updateDevCardsPlayed(SOCDevCardConstants.ROADS);
        pl.updateDevCardsPlayed(SOCDevCardConstants.DISC);
        assertEquals(2, pl.numDISCCards);
        assertEquals(1, pl.numMONOCards);
        assertEquals(1, pl.numRBCards);
        expectedCards.addAll(Arrays.asList
            (SOCDevCardConstants.DISC, SOCDevCardConstants.MONO, SOCDevCardConstants.ROADS, SOCDevCardConstants.DISC));
        assertEquals(expectedCards, pl.getDevCardsPlayed());

        SOCPlayer pclone = new SOCPlayer(pl, null);
        assertEquals(2, pclone.numDISCCards);
        assertEquals(1, pclone.numMONOCards);
        assertEquals(1, pclone.numRBCards);
        assertEquals(expectedCards, pclone.getDevCardsPlayed());
    }

}
