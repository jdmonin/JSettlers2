/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCCity;
import soc.game.SOCGame;
import soc.game.SOCMoveRobberResult;
import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
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
 * Currently tests only a few rules for {@link SOCScenario#K_SC_PIRI SC_PIRI}.
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
        gl = new SOCGameListAtServer();
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

}
