/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2019-2021,2025 Jeremy D Monin <jeremy@nand.net>
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

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import soc.game.GameAction;
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
import soc.game.SOCShip;
import soc.game.SOCVillage;
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
 * <LI> {@link SOCScenario#K_SC_CLVI SC_CLVI}: Placing ships to a village gives 1 cloth, closes route; 2 cloth gives a VP
 * <LI> {@link SOCScenario#K_SC_PIRI SC_PIRI}: A few rules for pirate fleet robbery.
 * <LI> {@link SOCScenario#K_SC_TTD SC_TTD}: 2 SVP for placing past the desert, but no SVP for placing in desert
 *</UL>
 * See also {@link TestBoardLayouts#testLayout_SC_CLVI(SOCGame)}.
 *
 * @since 2.0.00
 */
public class TestScenarioRules
{
    private final static Random rand = new Random();
    private static SOCGameListAtServer gl;
    private static SOCGameHandler sgh;

    @BeforeClass
    public static void setup()
    {
        sgh = new SOCGameHandler(null);
        gl = new SOCGameListAtServer(rand, SOCGameOptionSet.getAllKnownOptions());
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

    /**
     * {@link SOCScenario#K_SC_CLVI SC_CLVI}: Placing ships to a village gives 1 cloth, closes route; 2 cloth gives a VP.
     * @since 2.7.00
     */
    @Test
    public void test_SC_CLVI_ships_to_village()
    {
        final String gaName = SOCScenario.K_SC_CLVI + ":2";  // use scenario's 2-player layout
        final SOCGame ga = GameTestUtils.createGame
            (2, SOCScenario.K_SC_CLVI, "NT=t,N7=t7,UB=t,UBL=t7", gaName, gl, sgh);

        // Create the board and 2 players. Adapted from SOCGameHandler.startGame
        for (int pn = 0; pn < 2; ++pn)
            ga.addPlayer("player" + pn, pn);
        ga.startGame();  // SOCBoard/SOCBoardAtServer.makeNewBoard is called here
        SOCBoardAtServer.startGame_scenarioSetup(ga);

        final SOCBoardAtServer board = (SOCBoardAtServer) ga.getBoard();
        final SOCPlayer pl0 = ga.getPlayer(0), pl1 = ga.getPlayer(1);

        /**
         * Initial placement and subsequent ships for 2 players:
         * Each subarray has a coastal settle node, 2 sea edges,
         * and a village node next to 2nd sea edge (partial set of board's villages)
         */
        final int[][] COASTAL_SETTLE_2_SHIPS_VILLAGE = new int[][]
            {
                {0x607, 0x607, 0x608, 0x609},
                {0x60b, 0x60a, 0x609, 0x609},
                {0x804, 0x804, 0x805, 0x806},
                {0xc07, 0xc07, 0xc08, 0xc09},
                {0xc0b, 0xc0a, 0xc09, 0xc09},
                {0xa0e, 0xa0d, 0xa0c, 0xa0c},
            };

        // Assert test assumptions on 2-player board:
        // - water for ships to villages
        for (final int HEXCOORD : new int[]{0x508, 0x50a, 0x707, 0x70b, 0x705, 0x904, 0xd08, 0xd0a, 0xb07, 0xb0b, 0xb0d, 0x90e})
            assertEquals("expected water at hex 0x" + Integer.toHexString(HEXCOORD),
                SOCBoard.WATER_HEX, board.getHexTypeFromCoord(HEXCOORD));
        // - land for init placements
        for (final int HEXCOORD : new int[]{0x506, 0x50c, 0x703, 0xd06, 0xd0c, 0xb0f})
            assertNotEquals("expected land at hex 0x" + Integer.toHexString(HEXCOORD),
                SOCBoard.WATER_HEX, board.getHexTypeFromCoord(HEXCOORD));
        // - the villages we'll build to
        for (final int[] CS_SH_VI : COASTAL_SETTLE_2_SHIPS_VILLAGE)
        {
            final int nodeCoord = CS_SH_VI[3];
            SOCVillage vi = ((SOCBoardLarge) board).getVillageAtNode(nodeCoord);
            assertNotNull("expected village at 0x" + Integer.toHexString(nodeCoord), vi);
            assertEquals("cloth count at village at 0x" + Integer.toHexString(nodeCoord), SOCVillage.STARTING_CLOTH, vi.getCloth());
        }

        // set first player instead of the normal random start
        ga.setGameState(SOCGame.START1A);
        assertEquals(SOCGame.START1A, ga.getGameState());
        ga.setCurrentPlayerNumber(0);
        assertEquals(0, ga.getCurrentPlayerNumber());
        ga.setFirstPlayer(0);
        assertEquals(0, ga.getFirstPlayer());

        // do initial placement the usual way, more or less, so it will
        // behave like an actual played game and transition to normal gameplay

        /** Subarrays are each player number's 3 indexes into COASTAL_SETTLE_2_SHIPS_VILLAGE */
        final int[][] plPlacementIndexes = new int[2][3];
        {
            int[] indexes = new int[COASTAL_SETTLE_2_SHIPS_VILLAGE.length];
            for (int i = 0; i < indexes.length; ++i)
                indexes[i] = i;
            // standard Durstenfeld shuffle: Knuth AOCP Algorithm P (Shuffling)
            for (int i = indexes.length - 1; i > 0; i--)
            {
                int idx = rand.nextInt(i + 1);
                int swap = indexes[idx];
                indexes[idx] = indexes[i];
                indexes[i] = swap;
            }

            int iOverall = 0;
            for (int i = 0; i < 3; ++i)
                plPlacementIndexes[0][i] = indexes[iOverall++];
            for (int i = 0; i < 3; ++i)
                plPlacementIndexes[1][i] = indexes[iOverall++];
        }

        // 3 init placements per player
        final int[][] INITPLACE_PN_STARTSTATE = new int[][]
            {
                {0, SOCGame.START1A}, {1, SOCGame.START1A},
                {1, SOCGame.START2A}, {0, SOCGame.START2A},
                {0, SOCGame.START3A}, {1, SOCGame.START3A},
            };
        for (int ipIdx = 0; ipIdx < INITPLACE_PN_STARTSTATE.length; ++ipIdx)
        {
            final int pn = INITPLACE_PN_STARTSTATE[ipIdx][0];
            final SOCPlayer pl = (pn == 0) ? pl0 : pl1;

            assertEquals(INITPLACE_PN_STARTSTATE[ipIdx][1], ga.getGameState());
            assertEquals(pn, ga.getCurrentPlayerNumber());

            int playerNextIdx = pl.getPublicVP();  // since each placed settlement is 1 VP
            final int[] INIT_SETTLE_SHIPS = COASTAL_SETTLE_2_SHIPS_VILLAGE[plPlacementIndexes[pn][playerNextIdx]];

            int coord = INIT_SETTLE_SHIPS[0];
            assertNull("expect no settlement yet at 0x" + Integer.toHexString(coord), board.settlementAtNode(coord));
            SOCPlayingPiece p = new SOCSettlement(pl, coord, board);
            ga.putPiece(p);
            assertNotNull(board.settlementAtNode(coord));

            coord = INIT_SETTLE_SHIPS[1];
            assertNull("expect no ship yet at 0x" + Integer.toHexString(coord), board.roadOrShipAtEdge(coord));
            assertEquals(1 + INITPLACE_PN_STARTSTATE[ipIdx][1], ga.getGameState());
            p = new SOCShip(pl,coord, board);
            ga.putPiece(p);
            assertNotNull(board.roadOrShipAtEdge(coord));
        }

        // initial placement should have auto-transitioned to first turn of normal gameplay
        assertEquals("should be done with initial placement", SOCGame.ROLL_OR_CARD, ga.getGameState());
        for (int pn = 0; pn < 2; ++pn)
        {
            final SOCPlayer pl = ga.getPlayer(pn);
            assertEquals("pn " + pn + " total 3 VP", 3, pl.getTotalVP());
            assertEquals("pn " + pn + " no SVP", 0,  pl.getSpecialVP());
            assertEquals("pn " + pn + " no cloth yet", 0, pl.getCloth());
        }

        HashSet<Integer> villagesBuiltTo = new HashSet<>();
        int ship1Edge, ship2Edge;
        final int villageNode;
        {
            int csIdx = plPlacementIndexes[0][0];  // player 0's first placement
            ship1Edge = COASTAL_SETTLE_2_SHIPS_VILLAGE[csIdx][1];
            ship2Edge = COASTAL_SETTLE_2_SHIPS_VILLAGE[csIdx][2];
            villageNode = COASTAL_SETTLE_2_SHIPS_VILLAGE[csIdx][3];
        }
        test_SC_CLVI_ships_to_village_placeShip(ga, board, 0, 3, 3, 0, ship1Edge, ship2Edge, villageNode, true, villagesBuiltTo, 0);

        int village2Node, clothVPIncr;
        {
            int csIdx = plPlacementIndexes[0][1];  // player 0's second placement
            ship1Edge = COASTAL_SETTLE_2_SHIPS_VILLAGE[csIdx][1];
            ship2Edge = COASTAL_SETTLE_2_SHIPS_VILLAGE[csIdx][2];
            village2Node = COASTAL_SETTLE_2_SHIPS_VILLAGE[csIdx][3];
            clothVPIncr = (village2Node != villageNode) ? 1 : 0;
        }
        test_SC_CLVI_ships_to_village_placeShip(ga, board, 0, 3, 3 + clothVPIncr, 1, ship1Edge, ship2Edge, village2Node, false, villagesBuiltTo, 0);

        // For third, make sure undo/redo move also has the expected effects.
        // Move player 0's third placed ship to placement 2's ship2Edge (since that was undone)
        int moveShipFromEdge;
        {
            int csIdx = plPlacementIndexes[0][2];  // player 0's third placement
            moveShipFromEdge = COASTAL_SETTLE_2_SHIPS_VILLAGE[csIdx][1];
        }
        test_SC_CLVI_ships_to_village_placeShip(ga, board, 0, 3, 3 + clothVPIncr, 1, ship1Edge, ship2Edge, village2Node, true, villagesBuiltTo, moveShipFromEdge);

        // Cleanup
        gl.deleteGame(gaName);
    }

    /**
     * {@link SOCScenario#K_SC_CLVI SC_CLVI}: Place (build or move) a ship to a village and undo that for {@link #test_SC_CLVI_ships_to_village()}.
     * @param ga
     * @param board
     * @param pn  Player number to place for; should be current player
     * @param playerVP  Player's expected {@link SOCPlayer#getTotalVP()} before placing the second ship
     * @param playerVPAfter  Player's expected {@link SOCPlayer#getTotalVP()} after placing the second ship
     * @param playerCloth  Player's expected {@link SOCPlayer#getCloth()} before placing the second ship.
     *     This is also used to indicate whether it's the player's first village (if 0) or subsequent.
     * @param ship1Edge  Edge coord of already-placed first ship
     * @param ship2Edge  Edge coord of ship to be placed here, then undone
     * @param villageNode  Node coord of village to be placed to
     * @param redoPlacement  If true, redo ship placement after undo
     * @param villagesBuiltTo  Since 2 ships can build to same village, track the nodes built-to and redone so we know how much cloth to expect there
     * @param moveShipFromEdge  If nonzero, move ship from here to {@code ship2Edge} instead of building a ship
     * @since 2.7.00
     */
    private void test_SC_CLVI_ships_to_village_placeShip
        (final SOCGame ga, final SOCBoardAtServer board,
         final int pn, final int playerVP, final int playerVPAfter, final int playerCloth,
         final int ship1Edge, final int ship2Edge, final int villageNode, final boolean redoPlacement,
         final HashSet<Integer> villagesBuiltTo, final int moveShipFromEdge)
    {
        assertEquals("current player number", pn, ga.getCurrentPlayerNumber());

        /* check conditions before building the ship to village: */

        final SOCPlayer pl = ga.getPlayer(pn);
        assertEquals("pn " + pn + " cloth count before village", playerCloth, pl.getCloth());
        assertEquals("pn " + pn + " total VP before village", playerVP, pl.getTotalVP());

        SOCShip sh = (SOCShip) board.roadOrShipAtEdge(ship1Edge);
        assertNotNull(sh);
        assertEquals(pn, sh.getPlayerNumber());
        assertFalse(sh.isClosed());

        sh = (SOCShip) board.roadOrShipAtEdge(ship2Edge);
        assertNull("no ship yet at 0x" + Integer.toHexString(ship2Edge), sh);

        final SOCVillage vi = ((SOCBoardLarge) board).getVillageAtNode(villageNode);
        final boolean villageAlreadyBuiltTo = (villagesBuiltTo.contains(villageNode));
        final int villageCloth = SOCVillage.STARTING_CLOTH - (villageAlreadyBuiltTo ? 1 : 0);
        assertNotNull("expected village at 0x" + Integer.toHexString(villageNode), vi);
        assertEquals("cloth count before build at village at 0x" + Integer.toHexString(villageNode), villageCloth, vi.getCloth());

        /* place ship adjacent to village: */

        ga.setGameState(SOCGame.PLAY1);
        assertEquals(SOCGame.PLAY1, ga.getGameState());

        if (moveShipFromEdge == 0)
        {
            sh = new SOCShip(pl, ship2Edge, board);
            ga.putPiece(sh);
        } else {
            sh = ga.canMoveShip(pn, moveShipFromEdge, ship2Edge);
            assertNotNull("can move ship from 0x" + Integer.toHexString(moveShipFromEdge) + " to 0x" + Integer.toHexString(ship2Edge), sh);
            ga.moveShip(sh, ship2Edge);
        }

        sh = (SOCShip) board.roadOrShipAtEdge(ship1Edge);
        assertTrue("ship 1 route now closed: 0x" + Integer.toHexString(ship1Edge), sh.isClosed());

        sh = (SOCShip) (board.roadOrShipAtEdge(ship2Edge));
        assertNotNull(sh);
        assertTrue("ship 2 route now closed: 0x" + Integer.toHexString(ship2Edge), sh.isClosed());

        assertEquals("pn " + pn + " cloth count after village", playerCloth + (villageAlreadyBuiltTo ? 0 : 1), pl.getCloth());
        assertEquals("pn " + pn + " total VP after village", playerVPAfter, pl.getTotalVP());
        assertEquals("cloth count after build at village at 0x" + Integer.toHexString(villageNode), villageCloth - (villageAlreadyBuiltTo ? 0 : 1), vi.getCloth());

        GameAction act = ga.getLastAction();
        assertNotNull(act);
        assertEquals((moveShipFromEdge != 0) ? GameAction.ActionType.MOVE_PIECE : GameAction.ActionType.BUILD_PIECE, act.actType);
        assertEquals(SOCPlayingPiece.SHIP, act.param1);
        assertEquals((moveShipFromEdge != 0) ? moveShipFromEdge : ship2Edge, act.param2);
        assertEquals((moveShipFromEdge != 0) ? ship2Edge : pn, act.param3);
        {
            List<GameAction.Effect> effects = act.effects;
            assertNotNull(effects);
            assertEquals(1 + (villageAlreadyBuiltTo ? 0 : 1) + ((playerCloth == 0) ? 1 : 0), effects.size());

            int i = 0;
            GameAction.Effect e;
            if (playerCloth == 0)
            {
                e = effects.get(i++);
                assertEquals(GameAction.EffectType.PLAYER_SET_EVENT_FLAGS, e.eType);
                assertArrayEquals(new int[]{4, 1}, e.params);
            }

            if (! villageAlreadyBuiltTo)
            {
                e = effects.get(i++);
                assertEquals(GameAction.EffectType.PLAYER_SCEN_CLVI_RECEIVE_CLOTH, e.eType);
                assertArrayEquals(new int[]{1, villageNode, 1}, e.params);
            }

            e = effects.get(i++);
            assertEquals(GameAction.EffectType.CLOSE_SHIP_ROUTE, e.eType);
            assertArrayEquals(new int[]{ship2Edge, ship1Edge}, e.params);
        }

        /* undo place ship adjacent to village: */

        assertEquals(SOCGame.PLAY1, ga.getGameState());
        if (moveShipFromEdge != 0)
        {
            assertTrue(ga.canUndoMoveShip(pn, sh));
            final GameAction undoMove = ga.undoMoveShip(sh);
            assertNotNull(undoMove);
        } else {
            assertTrue(ga.canUndoPutPiece(pn, sh));
            final GameAction undoBuild = ga.undoPutPiece(sh);
            assertNotNull(undoBuild);
        }

        act = ga.getLastAction();
        assertNotNull(act);
        assertEquals((moveShipFromEdge != 0) ? GameAction.ActionType.UNDO_MOVE_PIECE : GameAction.ActionType.UNDO_BUILD_PIECE, act.actType);
        assertEquals(SOCPlayingPiece.SHIP, act.param1);
        assertEquals(ship2Edge, act.param2);
        assertEquals((moveShipFromEdge != 0) ? moveShipFromEdge : pn, act.param3);
        assertNull(act.rset1);
        {
            List<GameAction.Effect> effects = act.effects;
            assertNotNull(effects);
            assertEquals(1 + (villageAlreadyBuiltTo ? 0 : 1) + ((playerCloth == 0) ? 1 : 0), effects.size());

            int i = 0;
            GameAction.Effect e;
            if (playerCloth == 0)
            {
                e = effects.get(i++);
                assertEquals(GameAction.EffectType.PLAYER_SET_EVENT_FLAGS, e.eType);
                assertArrayEquals(new int[]{4, 1}, e.params);
            }

            if (! villageAlreadyBuiltTo)
            {
                e = effects.get(i++);
                assertEquals(GameAction.EffectType.PLAYER_SCEN_CLVI_RECEIVE_CLOTH, e.eType);
                assertArrayEquals(new int[]{1, villageNode, 1}, e.params);
            }

            e = effects.get(i++);
            assertEquals(GameAction.EffectType.CLOSE_SHIP_ROUTE, e.eType);
            assertArrayEquals(new int[]{ship2Edge, ship1Edge}, e.params);
        }

        assertNull(board.roadOrShipAtEdge(ship2Edge));

        assertEquals("cloth count after undo at village at 0x" + Integer.toHexString(villageNode), villageCloth, vi.getCloth());

        sh = (SOCShip) board.roadOrShipAtEdge(ship1Edge);
        assertNotNull(sh);
        assertFalse("ship 1 route reopened: 0x" + Integer.toHexString(ship1Edge), sh.isClosed());

        assertEquals("pn " + pn + " cloth count after undo village", playerCloth, pl.getCloth());
        assertEquals("pn " + pn + " total VP after undo village", playerVP, pl.getTotalVP());

        if (redoPlacement)
        {
            assertEquals(SOCGame.PLAY1, ga.getGameState());

            sh = new SOCShip(pl, ship2Edge, board);
            ga.putPiece(sh);

            sh = (SOCShip) board.roadOrShipAtEdge(ship1Edge);
            assertTrue("ship 1 route now closed: 0x" + Integer.toHexString(ship1Edge), sh.isClosed());

            sh = (SOCShip) (board.roadOrShipAtEdge(ship2Edge));
            assertNotNull(sh);
            assertTrue("ship 2 route now closed: 0x" + Integer.toHexString(ship2Edge), sh.isClosed());

            assertEquals("pn " + pn + " cloth count after redo village", playerCloth + (villageAlreadyBuiltTo ? 0 : 1), pl.getCloth());
            assertEquals("pn " + pn + " total VP after redo village", playerVPAfter, pl.getTotalVP());
            assertEquals("cloth count after redo at village at 0x" + Integer.toHexString(villageNode), villageCloth - (villageAlreadyBuiltTo ? 0 : 1), vi.getCloth());

            act = ga.getLastAction();
            assertNotNull(act);
            {
                List<GameAction.Effect> effects = act.effects;
                assertNotNull(effects);
                assertEquals(1 + (villageAlreadyBuiltTo ? 0 : 1) + ((playerCloth == 0) ? 1 : 0), effects.size());

                int i = 0;
                GameAction.Effect e;
                if (playerCloth == 0)
                {
                    e = effects.get(i++);
                    assertEquals(GameAction.EffectType.PLAYER_SET_EVENT_FLAGS, e.eType);
                    assertArrayEquals(new int[]{4, 1}, e.params);
                }

                if (! villageAlreadyBuiltTo)
                {
                    e = effects.get(i++);
                    assertEquals(GameAction.EffectType.PLAYER_SCEN_CLVI_RECEIVE_CLOTH, e.eType);
                    assertArrayEquals(new int[]{1, villageNode, 1}, e.params);
                }

                e = effects.get(i++);
                assertEquals(GameAction.EffectType.CLOSE_SHIP_ROUTE, e.eType);
                assertArrayEquals(new int[]{ship2Edge, ship1Edge}, e.params);
            }

            villagesBuiltTo.add(villageNode);
        }
    }

}
