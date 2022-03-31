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
import soc.server.savegame.SavedGameModel;

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
        SOCPlayer pl = new SOCGame("test").getPlayer(2);
        pl.addRolledResources(new SOCResourceSet(1, 2, 2, 2, 1, 0));
        assertEquals(8, pl.getResources().getTotal());  // SOCResourceSet is mostly tested elsewhere
        assertEquals("discard should be half", 4, pl.getCountToDiscard());
        pl.addRolledResources(new SOCResourceSet(1, 0, 0, 0, 0, 0));
        assertEquals("discard should round down", 4, pl.getCountToDiscard());
    }

    @Test
    public void testInventoryDevCardsVP()
    {
        SOCPlayer pl = new SOCGame("test").getPlayer(2);
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
     * Test {@link SOCPlayer#updateDevCardsPlayed(int, boolean)}, {@link SOCPlayer#getDevCardsPlayed()},
     * and related stats fields ({@link SOCPlayer#numDISCCards} etc).
     * @since 2.5.00
     */
    @Test
    public void testDevCardStats()
    {
        SOCPlayer pl = new SOCGame("test").getPlayer(2);
        assertEquals(0, pl.numDISCCards);
        assertEquals(0, pl.numMONOCards);
        assertEquals(0, pl.numRBCards);
        assertNull(pl.getDevCardsPlayed());

        pl.updateDevCardsPlayed(SOCDevCardConstants.UNKNOWN, false);
        pl.updateDevCardsPlayed(SOCDevCardConstants.UNIV, false);
        pl.updateDevCardsPlayed(-42, false);  // out of range but should ignore it, not throw any exception
        pl.updateDevCardsPlayed(42, false);
        assertEquals(0, pl.numDISCCards);
        assertEquals(0, pl.numMONOCards);
        assertEquals(0, pl.numRBCards);
        List<Integer> expectedCards = new ArrayList<Integer>
            (Arrays.asList(SOCDevCardConstants.UNKNOWN, SOCDevCardConstants.UNIV, -42, 42));
        assertEquals(expectedCards, pl.getDevCardsPlayed());

        pl.updateDevCardsPlayed(SOCDevCardConstants.DISC, false);
        pl.updateDevCardsPlayed(SOCDevCardConstants.MONO, false);
        pl.updateDevCardsPlayed(SOCDevCardConstants.MONO, false);
        pl.updateDevCardsPlayed(SOCDevCardConstants.DISC, false);
        pl.updateDevCardsPlayed(SOCDevCardConstants.ROADS, false);
        assertEquals(2, pl.numDISCCards);
        assertEquals(2, pl.numMONOCards);
        assertEquals(1, pl.numRBCards);
        expectedCards.addAll(Arrays.asList
            (SOCDevCardConstants.DISC, SOCDevCardConstants.MONO, SOCDevCardConstants.MONO,
             SOCDevCardConstants.DISC, SOCDevCardConstants.ROADS));
        assertEquals(expectedCards, pl.getDevCardsPlayed());

        // test updateDevCardsPlayed with isCancel, including removal from list:

        // - end of list
        pl.updateDevCardsPlayed(SOCDevCardConstants.ROADS, true);
        assertEquals(0, pl.numRBCards);
        int ilast = expectedCards.size() - 1;
        assertEquals(SOCDevCardConstants.ROADS, expectedCards.get(ilast).intValue());
        expectedCards.remove(ilast);
        assertEquals(expectedCards, pl.getDevCardsPlayed());
        assertEquals(ilast, pl.getDevCardsPlayed().size());

        // - type not in list; shouldn't throw exception when looking to remove from list
        pl.updateDevCardsPlayed(47, true);
        assertEquals(ilast, pl.getDevCardsPlayed().size());  // unchanged

        // - just before end of list
        pl.updateDevCardsPlayed(SOCDevCardConstants.MONO, true);
        assertEquals(1, pl.numMONOCards);
        assertEquals(SOCDevCardConstants.DISC, expectedCards.get(ilast - 1).intValue());
        assertEquals(SOCDevCardConstants.MONO, expectedCards.get(ilast - 2).intValue());
        expectedCards.remove(ilast - 2);
        assertEquals(SOCDevCardConstants.DISC, expectedCards.get(ilast - 2).intValue());
        assertEquals(expectedCards, pl.getDevCardsPlayed());
        assertEquals(ilast - 1, pl.getDevCardsPlayed().size());

        SOCPlayer pclone = new SOCPlayer(pl, null);
        assertEquals(2, pclone.numDISCCards);
        assertEquals(1, pclone.numMONOCards);
        assertEquals(0, pclone.numRBCards);
        assertEquals(expectedCards, pclone.getDevCardsPlayed());
    }

    /**
     * Test {@link SOCPlayer#makeTrade(soc.game.ResourceSet, soc.game.ResourceSet)},
     * {@link SOCPlayer#makeBankTrade(soc.game.ResourceSet, soc.game.ResourceSet)},
     * and {@link SOCPlayer#getResourceTradeStats()}.
     * @see #testSetResourceTradeStats()
     * @since 2.6.00
     */
    @Test
    public void testTradeAndStats()
    {
        assertEquals(0, SOCBoard.MISC_PORT);
        assertEquals(1, SOCBoard.CLAY_PORT);
        assertEquals(5, SOCBoard.WOOD_PORT);
        assertEquals(6, SOCPlayer.TRADE_STATS_INDEX_BANK);
        assertEquals(7, SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL);
        assertEquals(8, SOCPlayer.TRADE_STATS_ARRAY_LEN);

        assertEquals(SOCPlayer.TRADE_STATS_ARRAY_LEN, SavedGameModel.TradeTypeStat.TYPE_DESCRIPTIONS.length);

        final SOCGame ga = new SOCGame("test");
        final SOCPlayer pl = ga.getPlayer(2);
        final SOCResourceSet plRes = pl.getResources();

        // initial setup and checks
        int[][][] plExpectedStats = new int[SOCPlayer.TRADE_STATS_ARRAY_LEN][2][5];  // [trType][give/get][resType]
        {
            SOCResourceSet[][] plStats = pl.getResourceTradeStats();  // [give/get][trType]
            assertEquals(2, plStats.length);
            assertEquals(SOCPlayer.TRADE_STATS_ARRAY_LEN, plStats[0].length);
        }
        assertTradeStatsEqual(plExpectedStats, pl);

        final SOCResourceSet ORE_1 = new SOCResourceSet(0, 1, 0, 0, 0, 0);

        // player trades
        {
            final SOCPlayer plTrade = ga.getPlayer(3);
            int[][][] plTradeExpectedStats = new int[SOCPlayer.TRADE_STATS_ARRAY_LEN][2][5];
            assertTradeStatsEqual(plTradeExpectedStats, plTrade);

            final SOCResourceSet SHEEP_WOOD_1 = new SOCResourceSet(0, 0, 1, 0, 1, 0);
            plRes.add(SHEEP_WOOD_1);
            plTrade.getResources().add(ORE_1);
            pl.makeTrade(SHEEP_WOOD_1, ORE_1);
            plTrade.makeTrade(ORE_1, SHEEP_WOOD_1);
            {
                plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][0] = new int[]{0, 0, 1, 0, 1};  // sheep, wood
                plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][1] = new int[]{0, 1, 0, 0, 0};  // ore
                plTradeExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][0] = new int[]{0, 1, 0, 0, 0};
                plTradeExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][1] = new int[]{0, 0, 1, 0, 1};
                assertTradeStatsEqual(plExpectedStats, pl);
                assertTradeStatsEqual(plTradeExpectedStats, plTrade);
            }

            // second trade; stats should include both
            final SOCResourceSet CLAY_1 = new SOCResourceSet(1, 0, 0, 0, 0, 0),
                WHEAT_1 = new SOCResourceSet(0, 0, 0, 1, 0, 0);
            plRes.add(CLAY_1);
            plTrade.getResources().add(WHEAT_1);
            pl.makeTrade(CLAY_1, WHEAT_1);
            plTrade.makeTrade(WHEAT_1, CLAY_1);
            plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][0][0]++;  // CLAY
            plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][1][3]++;  // WHEAT
            plTradeExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][1][0]++;
            plTradeExpectedStats[SOCPlayer.TRADE_STATS_INDEX_PLAYER_ALL][0][3]++;
            assertTradeStatsEqual(plExpectedStats, pl);
            assertTradeStatsEqual(plTradeExpectedStats, plTrade);
        }

        // basic 4:1 bank trade
        final SOCResourceSet SHEEP_4 = new SOCResourceSet(0, 0, 4, 0, 0, 0);
        plRes.add(SHEEP_4);
        pl.makeBankTrade(SHEEP_4, ORE_1);
        plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_BANK][0][2] += 4;  // SHEEP
        plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_BANK][1][1]++;  // ORE
        assertTradeStatsEqual(plExpectedStats, pl);

        // undo bank trade
        pl.makeBankTrade(ORE_1, SHEEP_4);
        plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_BANK][0][2] -= 4;  // SHEEP
        plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_BANK][1][1]--;  // ORE
        assertTradeStatsEqual(plExpectedStats, pl);

        // set and use 3:1
        pl.setPortFlag(SOCBoard.MISC_PORT, true);
        final SOCResourceSet WOOD_3 = new SOCResourceSet(0, 0, 0, 0, 3, 0);
        plRes.add(WOOD_3);
        pl.makeBankTrade(WOOD_3, ORE_1);
        plExpectedStats[SOCBoard.MISC_PORT][0][4] += 3;  // WOOD
        plExpectedStats[SOCBoard.MISC_PORT][1][1]++;  // ORE
        assertTradeStatsEqual(plExpectedStats, pl);

        // undo
        pl.makeBankTrade(ORE_1, WOOD_3);
        plExpectedStats[SOCBoard.MISC_PORT][0][4] -= 3;  // WOOD
        plExpectedStats[SOCBoard.MISC_PORT][1][1]--;  // ORE
        assertTradeStatsEqual(plExpectedStats, pl);

        // set and use 2:1
        pl.setPortFlag(SOCBoard.CLAY_PORT, true);
        final SOCResourceSet CLAY_2 = new SOCResourceSet(2, 0, 0, 0, 0, 0);
        plRes.add(CLAY_2);
        pl.makeBankTrade(CLAY_2, ORE_1);
        plExpectedStats[SOCBoard.CLAY_PORT][0][0] += 2;  // CLAY
        plExpectedStats[SOCBoard.CLAY_PORT][1][1]++;  // ORE
        assertTradeStatsEqual(plExpectedStats, pl);

        // undo
        pl.makeBankTrade(ORE_1, CLAY_2);
        plExpectedStats[SOCBoard.CLAY_PORT][0][0] -= 2;  // CLAY
        plExpectedStats[SOCBoard.CLAY_PORT][1][1]--;  // ORE
        assertTradeStatsEqual(plExpectedStats, pl);

        // multiple port types: clay 2:1 and 3:1
        final SOCResourceSet CLAY_2_WOOD_3 = new SOCResourceSet(2, 0, 0, 0, 3, 0),
            ORE_2 = new SOCResourceSet(0, 2, 0, 0, 0, 0);
        plRes.add(CLAY_2_WOOD_3);
        pl.makeBankTrade(CLAY_2_WOOD_3, ORE_2);
        plExpectedStats[SOCBoard.CLAY_PORT][0][0] += 2;  // CLAY
        plExpectedStats[SOCBoard.CLAY_PORT][1][1]++;  // ORE
        plExpectedStats[SOCBoard.MISC_PORT][0][4] += 3;  // WOOD
        plExpectedStats[SOCBoard.MISC_PORT][1][1]++;  // ORE
        assertTradeStatsEqual(plExpectedStats, pl);

        // undo multiple: clay 2:1 and 3:1
        pl.makeBankTrade(ORE_2, CLAY_2_WOOD_3);
        plExpectedStats[SOCBoard.CLAY_PORT][0][0] -= 2;  // CLAY
        plExpectedStats[SOCBoard.CLAY_PORT][1][1]--;  // ORE
        plExpectedStats[SOCBoard.MISC_PORT][0][4] -= 3;  // WOOD
        plExpectedStats[SOCBoard.MISC_PORT][1][1]--;  // ORE
        assertTradeStatsEqual(plExpectedStats, pl);

        // multiple port types: clay 2:1 but not 3:1
        pl.setPortFlag(SOCBoard.MISC_PORT, false);
        final SOCResourceSet CLAY_2_SHEEP_4 = new SOCResourceSet(2, 0, 4, 0, 0, 0);
        plRes.add(CLAY_2_SHEEP_4);
        pl.makeBankTrade(CLAY_2_SHEEP_4, ORE_2);
        plExpectedStats[SOCBoard.CLAY_PORT][0][0] += 2;  // CLAY
        plExpectedStats[SOCBoard.CLAY_PORT][1][1]++;  // ORE
        plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_BANK][0][2] += 4;  // SHEEP
        plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_BANK][1][1]++;  // ORE
        assertTradeStatsEqual(plExpectedStats, pl);

        // undo more complicated: clay 2:1 but not 3:1
        pl.makeBankTrade(ORE_2, CLAY_2_SHEEP_4);
        plExpectedStats[SOCBoard.CLAY_PORT][0][0] -= 2;  // CLAY
        plExpectedStats[SOCBoard.CLAY_PORT][1][1]--;  // ORE
        plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_BANK][0][2] -= 4;  // SHEEP
        plExpectedStats[SOCPlayer.TRADE_STATS_INDEX_BANK][1][1]--;  // ORE
        assertTradeStatsEqual(plExpectedStats, pl);
    }

    /**
     * Test {@link SOCPlayer#setResourceTradeStats(soc.game.ResourceSet[][])}.
     * @see #testTradeAndStats()
     * @since 2.6.00
     */
    @Test
    public void testSetResourceTradeStats()
    {
        final SOCGame ga = new SOCGame("test");
        final SOCPlayer pl = ga.getPlayer(2);

        // initial setup and checks
        int[][][] plExpectedStats = new int[SOCPlayer.TRADE_STATS_ARRAY_LEN][2][5];  // [trType][give/get][resType]
        assertTradeStatsEqual(plExpectedStats, pl);

        // usual call with expected length; test subelement nulls
        SOCResourceSet[][] statsSet = new SOCResourceSet[][]
            {{
                new SOCResourceSet(3, 0, 0, 3, 0, 0), // 3:1
                null,
                null,
                new SOCResourceSet(0, 0, 0, 4, 2, 0), // 2:1 sheep
                null,
                null,
                new SOCResourceSet(4, 4, 0, 0, 0, 0), // 4:1 bank
                new SOCResourceSet(0, 1, 1, 0, 0, 0), // player trades
            }, {
                new SOCResourceSet(0, 0, 0, 0, 2, 0), // 3:1
                null,
                null,
                new SOCResourceSet(0, 0, 3, 0, 0, 0), // 2:1 sheep
                null,
                null,
                new SOCResourceSet(0, 0, 0, 2, 0, 0), // 4:1 bank
                new SOCResourceSet(1, 0, 0, 0, 1, 0), // player trades
            }};
        assertEquals(SOCPlayer.TRADE_STATS_ARRAY_LEN, statsSet[0].length);
        pl.setResourceTradeStats(statsSet);
        plExpectedStats[0][0] = new int[]{3, 0, 0, 3, 0};
        plExpectedStats[3][0] = new int[]{0, 0, 0, 4, 2};
        plExpectedStats[6][0] = new int[]{4, 4, 0, 0, 0};
        plExpectedStats[7][0] = new int[]{0, 1, 1, 0, 0};
        plExpectedStats[0][1] = new int[]{0, 0, 0, 0, 2};
        plExpectedStats[3][1] = new int[]{0, 0, 3, 0, 0};
        plExpectedStats[6][1] = new int[]{0, 0, 0, 2, 0};
        plExpectedStats[7][1] = new int[]{1, 0, 0, 0, 1};
        assertTradeStatsEqual(plExpectedStats, pl);

        // too many elements: should ignore extra
        statsSet = new SOCResourceSet[][]
            {{
                new SOCResourceSet(3, 0, 0, 3, 0, 0), // 3:1
                null,
                null,
                new SOCResourceSet(0, 0, 0, 4, 2, 0), // 2:1 sheep
                null,
                null,
                new SOCResourceSet(4, 4, 0, 0, 0, 0), // 4:1 bank
                new SOCResourceSet(0, 1, 1, 0, 0, 0), // player trades
                new SOCResourceSet(1, 1, 1, 1, 1, 0), // beyond max expected stats length
                new SOCResourceSet(1, 1, 1, 1, 1, 0)
            }, {
                new SOCResourceSet(0, 0, 0, 0, 2, 0), // 3:1
                null,
                null,
                new SOCResourceSet(0, 0, 3, 0, 0, 0), // 2:1 sheep
                null,
                null,
                new SOCResourceSet(0, 0, 0, 2, 0, 0), // 4:1 bank
                new SOCResourceSet(1, 0, 0, 0, 1, 0), // player trades
                new SOCResourceSet(1, 1, 1, 1, 1, 0), // beyond max expected stats length
                new SOCResourceSet(1, 1, 1, 1, 1, 0)
            }};
        assertTrue(SOCPlayer.TRADE_STATS_ARRAY_LEN < statsSet[0].length);
        pl.setResourceTradeStats(statsSet);
        assertTradeStatsEqual(plExpectedStats, pl);

        // too few elements: should pad with 0s
        statsSet = new SOCResourceSet[][]
            {{
                new SOCResourceSet(3, 0, 0, 3, 0, 0), // 3:1
                null,
                null,
                new SOCResourceSet(0, 0, 0, 4, 2, 0), // 2:1 sheep
                null
            }, {
                new SOCResourceSet(0, 0, 0, 0, 2, 0), // 3:1
                null,
                null,
                new SOCResourceSet(0, 0, 3, 0, 0, 0), // 2:1 sheep
                null
            }};
        assertTrue(SOCPlayer.TRADE_STATS_ARRAY_LEN > statsSet[0].length);
        pl.setResourceTradeStats(statsSet);
        plExpectedStats[6][0] = new int[5];
        plExpectedStats[7][0] = new int[5];
        plExpectedStats[6][1] = new int[5];
        plExpectedStats[7][1] = new int[5];
        assertTradeStatsEqual(plExpectedStats, pl);
    }

    /**
     * Check player's trade stats at a point in time
     * for {@link #testTradeAndStats()} and {@link #testSetResourceTradeStats()}.
     * Assumes {@code plExpectedStats} was created there with correct lengths.
     * @param plExpectedStats  Stats arrays for resource trades: [trType 0..7][give=0/get=1][resType clay=0..wood=4]
     * @param pl  Will call {@link SOCPlayer#getResourceTradeStats() pl.getResourceTradeStats()}
     * @since 2.6.00
     */
    public static void assertTradeStatsEqual(final int[][][] plExpectedStats, final SOCPlayer pl)
    {
        final SOCResourceSet[][] plStats = pl.getResourceTradeStats();
        for (int i = 0; i < plStats[0].length; ++i)
        {
            assertArrayEquals("for give tradeType " + i, plExpectedStats[i][0], plStats[0][i].getAmounts(false));
            assertArrayEquals("for get tradeType " + i, plExpectedStats[i][1], plStats[1][i].getAmounts(false));
        }
    }

}
