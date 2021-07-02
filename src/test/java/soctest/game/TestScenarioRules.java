/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2019-2021 Jeremy D Monin <jeremy@nand.net>
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

import java.util.List;
import java.util.Random;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCGame;
import soc.game.SOCGameOptionSet;
import soc.game.SOCMoveRobberResult;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCRoad;
import soc.game.SOCScenario;
import soc.game.SOCSettlement;
import soc.server.SOCBoardAtServer;
import soc.server.SOCGameHandler;
import soc.server.SOCGameListAtServer;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for per-{@link SOCScenario} rules in {@link SOCGame}.
 * Nowhere near comprehensive at this point:
 * Currently tests only:
 *<UL>
 * <LI> {@link SOCScenario#K_SC_PIRI SC_PIRI}: A few rules for pirate fleet robbery.
 * <LI> {@link SOCScenario#K_SC_TTD SC_TTD}: 2 SVP for placing past the desert, but no SVP for placing in desert
 *</UL>
 * See also {@link TestBoardLayouts#testLayout_SC_CLVI(SOCGame)}.
 *
 * @since 2.0.00
 */
public class TestScenarioRules
{
    private static SOCGameListAtServer gl;
    private static SOCGameHandler sgh;

    @BeforeClass
    public static void setup()
    {
        sgh = new SOCGameHandler(null);
        gl = new SOCGameListAtServer(new Random(), SOCGameOptionSet.getAllKnownOptions());
    }

    /**
     * {@link SOCScenario#K_SC_PIRI SC_PIRI}: Tests for pirate fleet robbery.
     * TODO: Maybe break this up into several methods, as it tests several conditions.
     */
    @Test
    public void test_SC_PIRI_fleetRobbery()
    {
        // The pirate fleet should rob from the sole player with an adjacent settlement or city
        // and a fleet smaller than the pirate fleet's strength.
        // If multiple players are adjacent, don't steal.

        final String gaName = SOCScenario.K_SC_PIRI + ":6";
        final SOCGame ga = GameTestUtils.createGame
            (6, SOCScenario.K_SC_PIRI, null, gaName, gl, sgh);

        // Create the board. Adapted from SOCGameHandler.startGame
        for (int pn = 0; pn < 5; ++pn)
        {
            ga.addPlayer("player" + pn, pn);
            ga.getPlayer(pn).getResources().add(5, SOCResourceConstants.CLAY);  // something to steal
        }
        ga.startGame();  // SOCBoard/SOCBoardAtServer.makeNewBoard is called here
        SOCBoardAtServer.startGame_scenarioSetup(ga);

        final SOCBoardAtServer board = (SOCBoardAtServer) ga.getBoard();
        final SOCPlayer pl0 = ga.getPlayer(0), pl2 = ga.getPlayer(2), pl3 = ga.getPlayer(3);

        // Assert test assumptions on 6-player board:

        // - player #0 should have this addedLegalSettlement (which is adjacent to a pirate hex)
        final int player0Settle = pl0.getAddedLegalSettlement();
        assertEquals(0xC08, player0Settle);
        final int pirHexAdjacP0 = 0xB09;  // geometrically adjacent to 0xC08, no need to assert

        // - pn#2 and pn#3 should both have their addedLegalSettlement adjacent to this sea hex
        final int pirHexShared = 0x0707;
        {
            final int[] pirateAdjacNodes = board.getAdjacentNodesToHex_arr(pirHexShared);
            final int[] playerNodes = new int[2];
            playerNodes[0] = pl2.getAddedLegalSettlement();
            playerNodes[1] = pl3.getAddedLegalSettlement();
            for (final int plAddedNode : playerNodes)
            {
                assertTrue(plAddedNode > 0);
                boolean found = GameTestUtils.contains(pirateAdjacNodes, plAddedNode);
                assertTrue("expected player's legal settlement adjacent to hex 0x" + Integer.toHexString(pirHexShared)
                    + " but it was node 0x" + Integer.toHexString(plAddedNode),
                    found);
            }
        }

        // - pirate path should contain both
        final int[] pirateFleetHexPath = board.getAddedLayoutPart("PP");
        assertTrue(GameTestUtils.contains(pirateFleetHexPath, pirHexAdjacP0));
        assertTrue(GameTestUtils.contains(pirateFleetHexPath, pirHexShared));

        // Set up pirate fleet and test things:

        // Adjacent to a solitary player's location (player 0), should rob only if they have a settlement there

        // - No settlement: no battle or robbery
        ga.setCurrentPlayerNumber(0);
        ga.setGameState(SOCGame.ROLL_OR_CARD);
        SOCMoveRobberResult robResult = ga.movePirate(0, pirHexAdjacP0, 6);  // 6 guarantees a strong fleet
        assertTrue((robResult.sc_piri_loot == null) || (robResult.sc_piri_loot.getTotal() == 0));
        assertTrue(robResult.getVictims().isEmpty());

        // - Has settlement there: player loses battle
        ga.setGameState(SOCGame.PLACING_SETTLEMENT);
        ga.putPiece(new SOCSettlement(pl0, player0Settle, board));
        ga.setGameState(SOCGame.ROLL_OR_CARD);
        robResult = ga.movePirate(0, pirHexAdjacP0, 6);
        assertEquals(1, robResult.sc_piri_loot.getTotal());
        List<SOCPlayer> v = robResult.getVictims();
        assertEquals(1, v.size());
        assertEquals(pl0, v.get(0));
        // cleanup:
        robResult.sc_piri_loot.clear();
        robResult.getVictims().clear();

        // - Has city there: player still loses battle; with 1 city they lose 2 resources
        ga.setGameState(SOCGame.PLACING_CITY);
        ga.putPiece(new SOCCity(pl0, player0Settle, board));
        ga.setGameState(SOCGame.ROLL_OR_CARD);
        robResult = ga.movePirate(0, pirHexAdjacP0, 6);
        assertEquals(2, robResult.sc_piri_loot.getTotal());
        v = robResult.getVictims();
        assertEquals(1, v.size());
        assertEquals(pl0, v.get(0));
        // cleanup:
        robResult.sc_piri_loot.clear();
        robResult.getVictims().clear();

        // - Two players on nodes adjacent to same hex: No battle or robbery
        ga.setCurrentPlayerNumber(3);
        ga.setGameState(SOCGame.PLACING_SETTLEMENT);
        assertEquals(1, pl3.getSettlements().size());  // before tmpSett placement
        SOCSettlement tmpSett = new SOCSettlement(pl3, player0Settle + 0x0002, board);
            // player 3 can't really place there, but legal-coord checker is skipped in this test
        ga.putPiece(tmpSett);
        assertEquals(2, pl3.getSettlements().size());
        ga.setCurrentPlayerNumber(0);
        ga.setGameState(SOCGame.ROLL_OR_CARD);
        robResult = ga.movePirate(0, pirHexAdjacP0, 6);
        assertTrue((robResult.sc_piri_loot == null) || (robResult.sc_piri_loot.getTotal() == 0));
        assertTrue(robResult.getVictims().isEmpty());
        // cleanup:
        board.removePiece(tmpSett);
        pl3.removePiece(tmpSett, null);
        assertEquals(1, pl3.getSettlements().size());  // after tmpSett cleanup
        robResult.sc_piri_loot.clear();
        robResult.getVictims().clear();

        // - If player's fleet is stronger, player gets a gold hex pick
        pl0.setNumWarships(7);
        ga.setGameState(SOCGame.ROLL_OR_CARD);
        robResult = ga.movePirate(0, pirHexAdjacP0, 6);
        assertEquals(1, robResult.sc_piri_loot.getTotal());
        assertEquals(1, robResult.sc_piri_loot.getAmount(SOCResourceConstants.GOLD_LOCAL));
        v = robResult.getVictims();
        assertEquals(1, v.size());
        assertEquals(pl0, v.get(0));
        // cleanup:
        robResult.sc_piri_loot.clear();
        robResult.getVictims().clear();

        // Steal nothing if adjacent to pl2 and pl3's location, even if only 1 of them has an actual settlement
        ga.setCurrentPlayerNumber(2);
        ga.setGameState(SOCGame.PLACING_SETTLEMENT);
        ga.putPiece(new SOCSettlement(pl2, pl2.getAddedLegalSettlement(), board));
        ga.setGameState(SOCGame.ROLL_OR_CARD);
        robResult = ga.movePirate(2, pirHexShared, 6);
        assertTrue((robResult.sc_piri_loot == null) || (robResult.sc_piri_loot.getTotal() == 0));
        assertTrue(robResult.getVictims().isEmpty());

        // Cleanup
        gl.deleteGame(gaName);
    }

    /**
     * {@link SOCScenario#K_SC_TTD SC_TTD}: 2 SVP for placing past the desert, but no SVP for placing in desert.
     * @since 2.5.00
     */
    @Test
    public void test_SC_TTD_place_desert_SVP()
    {
        final String gaName = SOCScenario.K_SC_TTD + ":6";  // use scenario's 6-player layout
        final SOCGame ga = GameTestUtils.createGame
            (6, SOCScenario.K_SC_TTD, null, gaName, gl, sgh);

        // Create the board and 2 players. Adapted from SOCGameHandler.startGame
        for (int pn = 0; pn < 2; ++pn)
            ga.addPlayer("player" + pn, pn);
        ga.startGame();  // SOCBoard/SOCBoardAtServer.makeNewBoard is called here
        SOCBoardAtServer.startGame_scenarioSetup(ga);

        final SOCBoardAtServer board = (SOCBoardAtServer) ga.getBoard();
        final SOCPlayer pl0 = ga.getPlayer(0), pl1 = ga.getPlayer(1);

        // Assert test assumptions on 6-player board:
        // - water
        for (final int HEXCOORD : new int[]{0x512, 0x711})
            assertEquals("expected water at hex 0x" + Integer.toHexString(HEXCOORD),
                SOCBoard.WATER_HEX, board.getHexTypeFromCoord(HEXCOORD));
        // - main island's main part and past-desert strip
        for (final int[] HEX_LA : new int[][]{{0x70f, 1}, {0x910, 1}, {0x30f, 2}, {0x311, 2}})
        {
            final int HEXCOORD = HEX_LA[0], LA = HEX_LA[1];
            final int htype = board.getHexTypeFromCoord(HEXCOORD);
            assertTrue("expected non-desert land type, got " + htype + ", at hex 0x" + Integer.toHexString(HEXCOORD),
                ((htype >= SOCBoard.CLAY_HEX) && (htype <= SOCBoard.WOOD_HEX)) || (htype == SOCBoardLarge.GOLD_HEX));
            for (final int node : board.getAdjacentNodesToHex_arr(HEXCOORD))
            {
                final int nodeLA = board.getNodeLandArea(node);
                assertEquals
                    ("expected LA# == " +LA + " at node 0x" + Integer.toHexString(node) + " for hex 0x" + Integer.toHexString(HEXCOORD) + ", got " + nodeLA,
                     LA, nodeLA);
            }
        }
        // - desert strip
        for (int hc = 0x508; hc <= 0x510; hc += 2)
            assertEquals("expected desert at hex 0x" + Integer.toHexString(hc),
                SOCBoard.DESERT_HEX, board.getHexTypeFromCoord(hc));

        // set first player instead of the normal random start
        ga.setGameState(SOCGame.START1A);
        assertEquals(SOCGame.START1A, ga.getGameState());
        ga.setCurrentPlayerNumber(0);
        assertEquals(0, ga.getCurrentPlayerNumber());
        ga.setFirstPlayer(0);
        assertEquals(0, ga.getFirstPlayer());

        // do initial placement the usual way, more or less, so it will set
        // "starting" LAs like an actual played game and transition to normal gameplay

        SOCPlayingPiece p = new SOCSettlement(pl0, 0x810, board);
        ga.putPiece(p);
        assertNotNull(board.settlementAtNode(0x810));

        p = new SOCRoad(pl0, 0x710, board);
        ga.putPiece(p);
        assertNotNull(board.roadOrShipAtEdge(0x710));
        assertEquals(1, pl0.getTotalVP());

        assertEquals(SOCGame.START1A, ga.getGameState());
        assertEquals(1, ga.getCurrentPlayerNumber());

        p = new SOCSettlement(pl1, 0x80d, board);
        ga.putPiece(p);
        assertNotNull(board.settlementAtNode(0x80d));

        p = new SOCRoad(pl1, 0x80c, board);
        ga.putPiece(p);
        assertNotNull(board.roadOrShipAtEdge(0x80c));
        assertEquals(1, pl1.getTotalVP());

        assertEquals(SOCGame.START2A, ga.getGameState());
        assertEquals(1, ga.getCurrentPlayerNumber());

        p = new SOCSettlement(pl1, 0x80b, board);
        ga.putPiece(p);
        assertNotNull(board.settlementAtNode(0x80b));

        p = new SOCRoad(pl1, 0x80a, board);
        ga.putPiece(p);
        assertNotNull(board.roadOrShipAtEdge(0x80a));
        assertEquals(2, pl1.getTotalVP());

        assertEquals(SOCGame.START2A, ga.getGameState());
        assertEquals(0, ga.getCurrentPlayerNumber());

        p = new SOCSettlement(pl0, 0xc0e, board);
        ga.putPiece(p);
        assertNotNull(board.settlementAtNode(0xc0e));

        p = new SOCRoad(pl0, 0xb0e, board);
        ga.putPiece(p);
        assertNotNull(board.roadOrShipAtEdge(0xb0e));
        assertEquals(2, pl0.getTotalVP());

        // initial placement should have auto-transitioned to first turn of normal gameplay
        assertEquals("should be done with initial placement", SOCGame.ROLL_OR_CARD, ga.getGameState());
        for (int pn = 0; pn < 2; ++pn)
        {
            final SOCPlayer pl = ga.getPlayer(pn);
            assertEquals("pn " + pn + " no SVP", 0,  pl.getSpecialVP());
            assertEquals("pn " + pn + " starting LAs 1, 3", 0x301, pl.getStartingLandAreasEncoded());
                // main part of main island, + main island past desert
        }
        assertEquals(0, ga.getCurrentPlayerNumber());

        ga.setGameState(SOCGame.PLAY1);
        assertEquals(SOCGame.PLAY1, ga.getGameState());

        // continuing north from pl0's first road

        p = new SOCRoad(pl0, 0x610, board);
        ga.putPiece(p);
        assertNotNull(board.roadOrShipAtEdge(0x610));

        assertEquals(0, pl0.getSpecialVP());
        assertEquals(2, pl0.getTotalVP());

        // coastal settlement in middle of desert

        p = new SOCSettlement(pl0, 0x611, board);
        ga.putPiece(p);
        assertNotNull(board.settlementAtNode(0x611));

        assertEquals(0, pl0.getSpecialVP());
        assertEquals(3, pl0.getTotalVP());

        // roads and settlement past desert

        p = new SOCRoad(pl0, 0x511, board);
        ga.putPiece(p);
        assertNotNull(board.roadOrShipAtEdge(0x511));

        p = new SOCRoad(pl0, 0x410, board);
        ga.putPiece(p);
        assertNotNull(board.roadOrShipAtEdge(0x410));

        p = new SOCSettlement(pl0, 0x410, board);
        ga.putPiece(p);
        assertNotNull(board.settlementAtNode(0x410));

        assertEquals(2, pl0.getSpecialVP());
        assertEquals(6, pl0.getTotalVP());

        // Cleanup
        gl.deleteGame(gaName);
    }

}
