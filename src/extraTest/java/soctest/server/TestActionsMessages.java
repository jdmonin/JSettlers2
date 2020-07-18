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
package soctest.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCMoveRobberResult;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCRoutePiece;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.message.SOCChoosePlayer;
import soc.server.SOCServer;
import soc.server.savegame.SavedGameModel;
import soctest.server.RecordingTesterServer.QueueEntry;
import soctest.server.TestRecorder.StartedTestGameObjects;

/**
 * Extra testing to cover core game actions and their messages, as recorded by {@link RecordingTesterServer}.
 * Expands coverage past the basic unit tests done by {@link TestRecorder}.
 * @since 2.4.10
 */
public class TestActionsMessages
{
    private static RecordingTesterServer srv;

    @BeforeClass
    public static void startStringportServer()
    {
        srv = new RecordingTesterServer();
        srv.setPriority(5);  // same as in SOCServer.main
        srv.start();

        // wait for startup
        try
        {
            Thread.sleep(500);
        }
        catch (InterruptedException e) {}
    }

    @AfterClass
    public static void shutdownServer()
    {
        // for clearer sequences in System.out, wait for other threads' prints to complete
        try
        {
            Thread.sleep(250);
        }
        catch (InterruptedException e) {}

        System.out.flush();
        System.out.println();
        srv.stopServer();
    }

    /**
     * Re-run unit test {@link TestRecorder#testBasics_Loadgame(SOCServer)},
     * to rule out test setup problems if other tests fail.
     */
    @Test
    public void testBasics_Loadgame()
        throws IOException
    {
        assertNotNull(srv);
        TestRecorder.testBasics_Loadgame(srv);
    }

    /**
     * Tests building pieces and moving ships.
     * Builds all piece types except road, which is covered by {@link TestRecorder#testLoadAndBasicSequences()}.
     */
    @Test
    public void testBuildAndMove()
        throws IOException
    {
        assertNotNull(srv);
        testOne_BuildAndMove(false, false);
        testOne_BuildAndMove(false, true);
        testOne_BuildAndMove(true, false);
        testOne_BuildAndMove(true, true);
    }

    private void testOne_BuildAndMove
        (final boolean clientAsRobot, final boolean othersAsRobot)
        throws IOException
    {
        // unique client nickname, in case tests run in parallel
        final String CLIENT_NAME = "testBuildAndMove_" + (clientAsRobot ? 'r' : 'h') + (othersAsRobot ? "_r" : "_h");

        final StartedTestGameObjects objs =
            TestRecorder.connectLoadJoinResumeGame(srv, CLIENT_NAME, true, clientAsRobot, othersAsRobot);
        final DisplaylessTesterClient tcli = objs.tcli;
        // final SavedGameModel sgm = objs.sgm;
        final SOCGame ga = objs.gameAtServer;
        final SOCBoardLarge board = objs.board;
        final SOCPlayer cliPl = objs.clientPlayer;
        final Vector<QueueEntry> records = objs.records;

        /* build settlement (on main island) */

        records.clear();
        final int SETTLEMENT_NODE = 0x60a;
        assertNull(board.settlementAtNode(SETTLEMENT_NODE));
        assertEquals(3, cliPl.getNumPieces(SOCPlayingPiece.SETTLEMENT));
        assertEquals(2, cliPl.getPublicVP());
        assertArrayEquals(new int[]{3, 3, 3, 4, 4}, cliPl.getResources().getAmounts(false));
        tcli.putPiece(ga, new SOCSettlement(cliPl, SETTLEMENT_NODE, board));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertNotNull("settlement built", board.settlementAtNode(SETTLEMENT_NODE));
        assertEquals(2, cliPl.getNumPieces(SOCPlayingPiece.SETTLEMENT));
        assertEquals(3, cliPl.getPublicVP());
        assertArrayEquals(new int[]{2, 3, 2, 3, 3}, cliPl.getResources().getAmounts(false));

        StringBuilder comparesSettle = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElements:", "|playerNum=3|actionType=LOSE|e1=1,e3=1,e4=1,e5=1"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " built a settlement."},
                {"all:SOCPutPiece:", "|playerNumber=3|pieceType=1|coord=60a"},
                {"all:SOCGameState:", "|state=20"}
            });

        /* upgrade that settlement to city */

        records.clear();
        assertEquals(4, cliPl.getNumPieces(SOCPlayingPiece.CITY));
        assertEquals(2, cliPl.getNumPieces(SOCPlayingPiece.SETTLEMENT));
        tcli.putPiece(ga, new SOCCity(cliPl, SETTLEMENT_NODE, board));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertTrue("city built", board.settlementAtNode(SETTLEMENT_NODE) instanceof SOCCity);
        assertEquals(3, cliPl.getNumPieces(SOCPlayingPiece.CITY));
        assertEquals(3, cliPl.getNumPieces(SOCPlayingPiece.SETTLEMENT));
        assertEquals(4, cliPl.getPublicVP());
        assertArrayEquals(new int[]{2, 0, 2, 1, 3}, cliPl.getResources().getAmounts(false));

        StringBuilder comparesCity = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElements:", "|playerNum=3|actionType=LOSE|e2=3,e4=2"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " built a city."},
                {"all:SOCPutPiece:", "|playerNumber=3|pieceType=2|coord=60a"},
                {"all:SOCGameState:", "|state=20"}
            });

        /* build ship */

        records.clear();
        assertEquals(11, cliPl.getNumPieces(SOCPlayingPiece.SHIP));
        assertTrue(board.roadOrShipAtEdge(0xd05) instanceof SOCShip);
        final int SHIP_EDGE = 0xe05;
        assertNull(board.roadOrShipAtEdge(SHIP_EDGE));
        tcli.putPiece(ga, new SOCShip(cliPl, SHIP_EDGE, board));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertTrue("ship built", board.roadOrShipAtEdge(SHIP_EDGE) instanceof SOCShip);
        assertEquals(10, cliPl.getNumPieces(SOCPlayingPiece.SHIP));
        assertArrayEquals(new int[]{2, 0, 1, 1, 2}, cliPl.getResources().getAmounts(false));

        StringBuilder comparesShipBuild = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElements:", "|playerNum=3|actionType=LOSE|e3=1,e5=1"},
                {"all:SOCGameServerText:", "|text="+ CLIENT_NAME + " built a ship."},
                {"all:SOCPutPiece:", "|playerNumber=3|pieceType=3|coord=e05"},
                {"all:SOCGameState:", "|state=20"}
            });

        /* move a different ship */

        records.clear();
        final int MOVESHIP_EDGE_FROM = 3078,  // 0xc06
            MOVESHIP_EDGE_TO = 3846;  // 0xf06
        assertTrue("moving ship from here", board.roadOrShipAtEdge(MOVESHIP_EDGE_FROM) instanceof SOCShip);
        assertNull("no ship here yet", board.roadOrShipAtEdge(MOVESHIP_EDGE_TO));
        tcli.movePieceRequest(ga, cliPl.getPlayerNumber(), SOCPlayingPiece.SHIP, MOVESHIP_EDGE_FROM, MOVESHIP_EDGE_TO);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertTrue("ship moved", board.roadOrShipAtEdge(MOVESHIP_EDGE_TO) instanceof SOCShip);
        assertNull("ship moved from here", board.roadOrShipAtEdge(MOVESHIP_EDGE_FROM));
        assertEquals(4, cliPl.getPublicVP());  // unchanged by this move

        StringBuilder comparesShipMove = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCMovePiece:", "|param1=3|param2=3|param3=3078|param4=3846"}
            });

        /* build settlement (on small island) */

        records.clear();
        final int SETTLEMENT_ISL_NODE = 0x1006;
        assertNull(board.settlementAtNode(SETTLEMENT_ISL_NODE));
        assertEquals(3, cliPl.getNumPieces(SOCPlayingPiece.SETTLEMENT));
        tcli.putPiece(ga, new SOCSettlement(cliPl, SETTLEMENT_ISL_NODE, board));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertNotNull("settlement built", board.settlementAtNode(SETTLEMENT_ISL_NODE));
        assertEquals(2, cliPl.getNumPieces(SOCPlayingPiece.SETTLEMENT));
        assertEquals(5, cliPl.getPublicVP());
        assertArrayEquals(new int[]{1, 0, 0, 0, 1}, cliPl.getResources().getAmounts(false));

        StringBuilder comparesSettleIsl = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElements:", "|playerNum=3|actionType=LOSE|e1=1,e3=1,e4=1,e5=1"},
                {"all:SOCGameServerText:", "|text="+ CLIENT_NAME + " built a settlement."},
                {"all:SOCPutPiece:", "|playerNumber=3|pieceType=1|coord=1006"},
                {"all:SOCGameState:", "|state=20"}
            });

        /* leave game, consolidate results */

        tcli.destroy();

        StringBuilder compares = new StringBuilder();
        if (comparesSettle != null)
        {
            compares.append("Build settlement: Message mismatch: ");
            compares.append(comparesSettle);
        }
        if (comparesCity != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Build city: Message mismatch: ");
            compares.append(comparesCity);
        }
        if (comparesShipBuild != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Build ship: Message mismatch: ");
            compares.append(comparesShipBuild);
        }
        if (comparesShipMove != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Move ship: Message mismatch: ");
            compares.append(comparesShipMove);
        }
        if (comparesSettleIsl != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Build settlement on island: Message mismatch: ");
            compares.append(comparesSettleIsl);
        }

        if (compares.length() > 0)
        {
            compares.insert(0, "For test " + CLIENT_NAME + ": ");
            System.err.println(compares);
            fail(compares.toString());
        }
    }

    /**
     * Tests playing dev cards.
     * Buying dev cards is covered by {@link TestRecorder#testLoadAndBasicSequences()}.
     */
    @Test
    public void testPlayDevCards()
        throws IOException
    {
        assertNotNull(srv);
        testOne_PlayDevCards(false, false);
        testOne_PlayDevCards(false, true);
        testOne_PlayDevCards(true, false);
        testOne_PlayDevCards(true, true);
    }

    private void testOne_PlayDevCards(final boolean clientAsRobot, final boolean othersAsRobot)
        throws IOException
    {
        // unique client nickname, in case tests run in parallel
        final String CLIENT_NAME = "testPlayDevCards_" + (clientAsRobot ? 'r' : 'h') + (othersAsRobot ? "_r" : "_h");

        final StartedTestGameObjects objs =
            TestRecorder.connectLoadJoinResumeGame(srv, CLIENT_NAME, true, clientAsRobot, othersAsRobot);
        final DisplaylessTesterClient tcli = objs.tcli;
        // final SavedGameModel sgm = objs.sgm;
        final SOCGame ga = objs.gameAtServer;
        final SOCBoardLarge board = objs.board;
        final SOCPlayer cliPl = objs.clientPlayer;
        final Vector<QueueEntry> records = objs.records;

        /* monopoly: Sheep (victims pn=1 and pn=2 both have some sheep) */

        records.clear();
        assertArrayEquals(new int[]{3, 3, 3, 4, 4}, cliPl.getResources().getAmounts(false));
        tcli.playDevCard(ga, SOCDevCardConstants.MONO);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.WAITING_FOR_MONOPOLY, ga.getGameState());
        tcli.pickResourceType(ga, SOCResourceConstants.SHEEP);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.PLAY1, ga.getGameState());
        assertArrayEquals(new int[]{3, 3, 6, 4, 4}, cliPl.getResources().getAmounts(false));

        StringBuilder comparesMono = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCDevCardAction:", "|playerNum=3|actionType=PLAY|cardType=3"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=SET|elementType=19|amount=1"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " played a Monopoly card."},
                {"all:SOCGameState:", "|state=53"},
                {"all:SOCSimpleAction:", "|pn=3|actType=3|v1=3|v2=3"},
                {"all:SOCPlayerElement:", "|playerNum=1|actionType=SET|elementType=3|amount=0|news=Y"},
                {"all:SOCPlayerElement:", "|playerNum=2|actionType=SET|elementType=3|amount=0|news=Y"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=SET|elementType=3|amount=6"},
                (othersAsRobot ? null : new String[]{"p1:SOCGameServerText:", "|text=" + CLIENT_NAME + "'s Monopoly took your 1 sheep."}),
                (othersAsRobot ? null : new String[]{"p2:SOCGameServerText:", "|text=" + CLIENT_NAME + "'s Monopoly took your 2 sheep."}),
                {"all:SOCGameState:", "|state=20"}
            });

        /* discovery/year of plenty */

        records.clear();
        cliPl.setPlayedDevCard(false);  // bend rules to skip waiting for our next turn
        tcli.playDevCard(ga, SOCDevCardConstants.DISC);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.WAITING_FOR_DISCOVERY, ga.getGameState());
        tcli.pickResources(ga, new SOCResourceSet(0, 1, 0, 1, 0, 0));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.PLAY1, ga.getGameState());
        assertArrayEquals(new int[]{3, 4, 6, 5, 4}, cliPl.getResources().getAmounts(false));

        StringBuilder comparesDisc = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCDevCardAction:", "|playerNum=3|actionType=PLAY|cardType=2"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=SET|elementType=19|amount=1"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " played a Year of Plenty card."},
                {"all:SOCGameState:", "|state=52"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=2|amount=1"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=4|amount=1"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " received 1 ore and 1 wheat from the bank."},
                {"all:SOCGameState:", "|state=20"}
            });

        /* road building, gain longest road */

        records.clear();
        assertEquals(null, ga.getPlayerWithLongestRoad());
        assertEquals(2, cliPl.getPublicVP());
        assertTrue(board.roadOrShipAtEdge(0x609) instanceof SOCRoad);
        final int ROAD_EDGE_1 = 0x70a, ROAD_EDGE_2 = 0x809;
        assertNull(board.roadOrShipAtEdge(ROAD_EDGE_1));
        assertNull(board.roadOrShipAtEdge(ROAD_EDGE_2));
        cliPl.setPlayedDevCard(false);
        tcli.playDevCard(ga, SOCDevCardConstants.ROADS);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.PLACING_FREE_ROAD1, ga.getGameState());
        tcli.putPiece(ga, new SOCRoad(cliPl, ROAD_EDGE_1, board));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.PLACING_FREE_ROAD2, ga.getGameState());
        assertTrue(board.roadOrShipAtEdge(ROAD_EDGE_1) instanceof SOCRoad);
        tcli.putPiece(ga, new SOCRoad(cliPl, ROAD_EDGE_2, board));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.PLAY1, ga.getGameState());
        assertTrue(board.roadOrShipAtEdge(ROAD_EDGE_2) instanceof SOCRoad);
        assertEquals(cliPl, ga.getPlayerWithLongestRoad());
        assertEquals(4, cliPl.getPublicVP());

        StringBuilder comparesRoadBuild = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCDevCardAction:", "|playerNum=3|actionType=PLAY|cardType=1"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=SET|elementType=19|amount=1"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " played a Road Building card."},
                {"all:SOCGameState:", "|state=40"},
                {"p3:SOCGameServerText:", "|text=You may place 2 roads/ships."},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " built a road."},
                {"all:SOCPutPiece:", "|playerNumber=3|pieceType=0|coord=70a"},
                {"all:SOCGameState:", "|state=41"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " built a road."},
                {"all:SOCPutPiece:", "|playerNumber=3|pieceType=0|coord=809"},
                {"all:SOCGameElements:", "|e6=3"},
                {"all:SOCGameState:", "|state=20"}
            });

        /* soldier (move pirate ship) */

        records.clear();

        final int PIRATE_HEX = 0xd0a;
        // victim's ship should be sole adjacent piece
        {
            List<SOCPlayer> players = ga.getPlayersShipsOnHex(PIRATE_HEX);
            assertNotNull(players);
            assertEquals(1, players.size());
            assertEquals(1, players.get(0).getPlayerNumber());

            final int EXPECTED_VICTIM_SHIP_EDGE = 0xd09;
            SOCRoutePiece oppoShip = board.roadOrShipAtEdge(EXPECTED_VICTIM_SHIP_EDGE);
            assertTrue(oppoShip instanceof SOCShip);
            assertEquals(1, oppoShip.getPlayerNumber());

            final int[] PIRATE_HEX_OTHER_EDGES = {0xc09, 0xc0a, 0xd0b, 0xe0a, 0xe09};
            for (final int edge : PIRATE_HEX_OTHER_EDGES)
                assertNull(board.roadOrShipAtEdge(edge));
        }

        assertNotEquals("pirate not moved there yet", PIRATE_HEX, board.getPirateHex());
        cliPl.setPlayedDevCard(false);
        tcli.playDevCard(ga, SOCDevCardConstants.KNIGHT);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertTrue(cliPl.hasPlayedDevCard());
        assertEquals(2, cliPl.getNumKnights());
        assertEquals(SOCGame.WAITING_FOR_ROBBER_OR_PIRATE, ga.getGameState());
        tcli.choosePlayer(ga, SOCChoosePlayer.CHOICE_MOVE_PIRATE);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.PLACING_PIRATE, ga.getGameState());
        tcli.moveRobber(ga, cliPl, -PIRATE_HEX);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals("new pirateHex", PIRATE_HEX, board.getPirateHex());
        SOCMoveRobberResult robRes = ga.getRobberyResult();
        assertNotNull(robRes);
        int resType = robRes.getLoot();
        assertTrue(resType > 0);

        StringBuilder comparesMovePirate = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " played a Soldier card."},
                {"all:SOCDevCardAction:", "|playerNum=3|actionType=PLAY|cardType=9"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=SET|elementType=19|amount=1"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=15|amount=1"},
                {"all:SOCGameState:", "|state=54"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " must choose to move the robber or the pirate."},
                {"all:SOCGameState:", "|state=34"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " will move the pirate ship."},
                {"all:SOCMoveRobber:", "|playerNumber=3|coord=-d0a"},
                {"p3:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=" + resType + "|amount=1"},
                {"p3:SOCPlayerElement:", "|playerNum=1|actionType=LOSE|elementType=" + resType + "|amount=1|news=Y"},
                {"p1:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=" + resType + "|amount=1"},
                {"p1:SOCPlayerElement:", "|playerNum=1|actionType=LOSE|elementType=" + resType + "|amount=1|news=Y"},
                {"!p[3, 1]:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=6|amount=1"},
                {"!p[3, 1]:SOCPlayerElement:", "|playerNum=1|actionType=LOSE|elementType=6|amount=1"},
                {"p3:SOCGameServerText:", "|text=You stole a", " from "},  // "a wheat", "an ore"
                {"p1:SOCGameServerText:", "|text=" + CLIENT_NAME + " stole a", " from you."},
                {"!p[3, 1]:SOCGameServerText:", "|text=" + CLIENT_NAME + " stole a resource from "},
                {"all:SOCGameState:", "|state=20"},
            });

        /* soldier (move robber), gain largest army */

        records.clear();
        cliPl.setPlayedDevCard(false);
        assertEquals(null, ga.getPlayerWithLargestArmy());
        assertEquals(4, cliPl.getPublicVP());
        assertEquals(2, cliPl.getNumKnights());
        final int ROBBER_HEX = 0x703;
        assertNotEquals("robber not moved there yet", ROBBER_HEX, board.getRobberHex());
        // victim's settlement should be sole adjacent piece
        {
            final int EXPECTED_VICTIM_SETTLEMENT_NODE = 0x604;

            Set<SOCPlayingPiece> pp = new HashSet<>();
            List<SOCPlayer> players = ga.getPlayersOnHex(ROBBER_HEX, pp);

            assertNotNull(players);
            assertEquals(1, players.size());
            assertEquals(2, players.get(0).getPlayerNumber());

            assertEquals(1, pp.size());
            SOCPlayingPiece vset = (SOCPlayingPiece) (pp.toArray()[0]);
            assertTrue(vset instanceof SOCSettlement);
            assertEquals(2, vset.getPlayerNumber());
            assertEquals(EXPECTED_VICTIM_SETTLEMENT_NODE, vset.getCoordinates());

            assertTrue(board.settlementAtNode(EXPECTED_VICTIM_SETTLEMENT_NODE) instanceof SOCSettlement);

            final int[] ROBBER_HEX_OTHER_NODES = {0x602, 0x603, 0x802, 0x803, 0x804};
            for (final int node : ROBBER_HEX_OTHER_NODES)
                assertNull(board.settlementAtNode(node));
        }

        cliPl.setPlayedDevCard(false);
        tcli.playDevCard(ga, SOCDevCardConstants.KNIGHT);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertTrue(cliPl.hasPlayedDevCard());
        assertEquals(3, cliPl.getNumKnights());
        assertEquals(6, cliPl.getPublicVP());
        assertEquals(cliPl, ga.getPlayerWithLargestArmy());
        assertEquals(SOCGame.WAITING_FOR_ROBBER_OR_PIRATE, ga.getGameState());
        tcli.choosePlayer(ga, SOCChoosePlayer.CHOICE_MOVE_ROBBER);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals(SOCGame.PLACING_ROBBER, ga.getGameState());
        tcli.moveRobber(ga, cliPl, ROBBER_HEX);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertEquals("new robberHex", ROBBER_HEX, board.getRobberHex());
        robRes = ga.getRobberyResult();
        assertNotNull(robRes);
        resType = robRes.getLoot();
        assertTrue(resType > 0);

        StringBuilder comparesMoveRobber = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " played a Soldier card."},
                {"all:SOCDevCardAction:", "|playerNum=3|actionType=PLAY|cardType=9"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=SET|elementType=19|amount=1"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=15|amount=1"},
                {"all:SOCGameElements:", "|e5=3"},
                {"all:SOCGameState:", "|state=54"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " must choose to move the robber or the pirate."},
                {"all:SOCGameState:", "|state=33"},
                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " will move the robber."},
                {"all:SOCMoveRobber:", "|playerNumber=3|coord=703"},
                {"p3:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=" + resType + "|amount=1"},
                {"p3:SOCPlayerElement:", "|playerNum=2|actionType=LOSE|elementType=" + resType + "|amount=1|news=Y"},
                {"p2:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=" + resType + "|amount=1"},
                {"p2:SOCPlayerElement:", "|playerNum=2|actionType=LOSE|elementType=" + resType + "|amount=1|news=Y"},
                {"!p[3, 2]:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=6|amount=1"},
                {"!p[3, 2]:SOCPlayerElement:", "|playerNum=2|actionType=LOSE|elementType=6|amount=1"},
                {"p3:SOCGameServerText:", "|text=You stole a", " from "},
                {"p2:SOCGameServerText:", "|text=" + CLIENT_NAME + " stole a", " from you."},
                {"!p[3, 2]:SOCGameServerText:", "|text=" + CLIENT_NAME + " stole a resource from"},
                {"all:SOCGameState:", "|state=20"}
            });

        /* leave game, consolidate results */

        tcli.destroy();

        StringBuilder compares = new StringBuilder();
        if (comparesMono != null)
        {
            compares.append("Monopoly: Message mismatch: ");
            compares.append(comparesMono);
        }
        if (comparesDisc != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Discovery/Year of Plenty: Message mismatch: ");
            compares.append(comparesDisc);
        }
        if (comparesRoadBuild != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Road Building: Message mismatch: ");
            compares.append(comparesRoadBuild);
        }
        if (comparesMovePirate != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Move Pirate: Message mismatch: ");
            compares.append(comparesMovePirate);
        }
        if (comparesMoveRobber != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Move Robber: Message mismatch: ");
            compares.append(comparesMoveRobber);
        }

        if (compares.length() > 0)
        {
            compares.insert(0, "For test " + CLIENT_NAME + ": ");
            System.err.println(compares);
            fail(compares.toString());
        }
    }

    /**
     * Test rolling dice at start of turn, and all 4 possible sequences in this test game:
     *<UL>
     * <LI> players receive resources
     * <LI> no one receives resources
     * <LI> roll 7, steal from a player (same sequence covers: steal from no one)
     * <LI> roll 7, discard, steal from a player
     *</UL>
     */
    @Test
    public void testRollDiceRsrcsOrMoveRobber()
        throws IOException
    {
        assertNotNull(srv);
        // These messages should have no differences between human and robot clients.
        // We'll test the usual 4 combinations just in case.
        testOne_RollDiceRsrcsOrMoveRobber(false, false);
        testOne_RollDiceRsrcsOrMoveRobber(false, true);
        testOne_RollDiceRsrcsOrMoveRobber(true, false);
        testOne_RollDiceRsrcsOrMoveRobber(true, true);
    }

    private void testOne_RollDiceRsrcsOrMoveRobber
        (final boolean clientAsRobot, final boolean othersAsRobot)
        throws IOException
    {
        final String CLIENT_NAME = "testRollDice_" + (clientAsRobot ? 'r' : 'h') + (othersAsRobot ? "_r" : "_h");
        final int CLIENT_PN = 3;

        final StartedTestGameObjects objs =
            TestRecorder.connectLoadJoinResumeGame(srv, CLIENT_NAME, false, clientAsRobot, othersAsRobot);
        final DisplaylessTesterClient tcli = objs.tcli;
        final SavedGameModel sgm = objs.sgm;
        final SOCGame ga = objs.gameAtServer;
        final SOCBoardLarge board = objs.board;
        final SOCPlayer cliPl = objs.clientPlayer;
        final Vector<QueueEntry> records = objs.records;

        assertTrue(ga.isSeatVacant(0));
        assertEquals(CLIENT_PN, cliPl.getPlayerNumber());

        // clear debug player's resources so no one needs to discard on 7;
        // once that sequence is validated, will change so must discard on 7
        ga.getPlayer(CLIENT_PN).getResources().setAmounts(new SOCResourceSet(0, 3, 1, 2, 0, 0));

        // allow 7s to be rolled
        ga.getGameOptions().remove("N7");

        final SOCResourceSet[] savedRsrcs = new SOCResourceSet[ga.maxPlayers];
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
            savedRsrcs[pn] = new SOCResourceSet(ga.getPlayer(pn).getResources());  // make independent copy

        sgm.gameState = SOCGame.ROLL_OR_CARD;
        TestRecorder.resumeLoadedGame(ga, srv, objs.tcliConn);

        // Validate expected resources gained by each player number for each dice number vs artifact's board layout
        final int[][] RSRC_GAINED_COUNTS =
            {
                null, null,
                /* 2 */ {0, 1, 0, 0}, {0, 0, 1, 1}, {0, 0, 1, 0}, {0, 0, 1, 1}, {0, 1, 2, 0},
                /* 7 */ null,
                /* 8 */ null,
                {0, 1, 0, 2}, {0, 2, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}
            };
        for (int diceNumber = 2; diceNumber <= 12; ++diceNumber)
        {
            final int[] counts = RSRC_GAINED_COUNTS[diceNumber];
            for (int pn = 0; pn < ga.maxPlayers; ++pn)
            {
                SOCResourceSet rs = ga.getResourcesGainedFromRoll(ga.getPlayer(pn), diceNumber);
                if (rs.isEmpty())
                    assertTrue
                        ("no res on board for: dice=" + diceNumber + " pn=" + pn,
                         (counts == null) || (counts[pn] == 0));
                else
                {
                    int n = rs.getTotal();
                    assertTrue
                        ("num res on board = " + n + " for: dice=" + diceNumber + " pn=" + pn,
                         (counts != null) && (counts[pn] == n));
                }
            }
        }

        StringBuilder comparesRsrcs = null, comparesNoRsrcs = null,
            compares7MoveRobber = null, compares7DiscardMove = null;
        boolean testedRsrcs = false, testedNoRsrcs = false, tested7 = false, tested7Discard = false;

        while (! (testedRsrcs && testedNoRsrcs && tested7 && tested7Discard))
        {
            ga.setGameState(SOCGame.ROLL_OR_CARD);
            for (int pn = 0; pn < ga.maxPlayers; ++pn)
                ga.getPlayer(pn).getResources().setAmounts(savedRsrcs[pn]);

            records.clear();
            tcli.rollDice(ga);

            try { Thread.sleep(60); }
            catch(InterruptedException e) {}
            final int diceNumber = ga.getCurrentDice();

            if (diceNumber != 7)
            {
                assertEquals(SOCGame.PLAY1, ga.getGameState());

                int nGainingPlayers = 0;
                final int[] counts = RSRC_GAINED_COUNTS[diceNumber];
                for (int pn = 1; pn < ga.maxPlayers; ++pn)  // pn 0 is vacant
                {
                    SOCPlayer pl = ga.getPlayer(pn);
                    int nGains = pl.getRolledResources().getTotal();
                    assertEquals
                        ((counts != null) ? counts[pn] : 0, nGains);
                    if (nGains > 0)
                        ++nGainingPlayers;
                }

                if (counts != null)
                {
                    if (! testedRsrcs)
                    {
                        // SOCDiceResultResources:game has a very specific format; see its class javadoc.
                        // We'll check that here.

                        StringBuilder diceResRsrc = new StringBuilder();
                        List<Integer> playerNums = new ArrayList<>();  // player numbers gaining resources
                        List<StringBuilder> playerRsrcElems = new ArrayList<>();  // same indexes as pn

                        // build expected SOCDiceResultResources and SOCPlayerElements strings
                        for (int pn = 1; pn < ga.maxPlayers; ++pn)
                        {
                            SOCPlayer pl = ga.getPlayer(pn);
                            SOCResourceSet rsGained = pl.getRolledResources();
                            int nGain = rsGained.getTotal();
                            if (nGain == 0)
                                continue;

                            SOCResourceSet rsPlayer = pl.getResources();
                            playerNums.add(Integer.valueOf(pn));
                            StringBuilder rsStrAdd = new StringBuilder("|playerNum=" + pn + "|actionType=SET|");
                            for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype)
                            {
                                if (rtype > SOCResourceConstants.CLAY)
                                    rsStrAdd.append(',');
                                rsStrAdd.append("e" + rtype + "=" + rsPlayer.getAmount(rtype));
                            }
                            playerRsrcElems.add(rsStrAdd);

                            if (diceResRsrc.length() == 0)
                                diceResRsrc.append
                                    ("game=" + ga.getName() + "|p=" + nGainingPlayers);  // first data fields of message
                            else
                                diceResRsrc.append("|p=0");  // separator from previous player

                            diceResRsrc.append("|p=" + pn);
                            diceResRsrc.append("|p=" + rsPlayer.getTotal());
                            for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype)
                            {
                                int n = rsGained.getAmount(rtype);
                                if (n == 0)
                                    continue;
                                diceResRsrc.append("|p=" + n);
                                diceResRsrc.append("|p=" + rtype);
                            }
                        }

                        /*
                        example:
                        all:SOCDiceResult:game=message-seqs|param=3
                        all:SOCGameState:game=message-seqs|state=20
                        all:SOCDiceResultResources:game=message-seqs|p=2|p=2|p=5|p=1|p=3|p=0|p=3|p=7|p=1|p=4
                        pn=2:SOCPlayerElements:game=message-seqs|playerNum=2|actionType=SET|e1=1,e2=1,e3=3,e4=0,e5=0
                        pn=3:SOCPlayerElements:game=message-seqs|playerNum=3|actionType=SET|e1=0,e2=3,e3=1,e4=3,e5=0
                         */
                        ArrayList<String[]> recordsList = new ArrayList<>();
                        recordsList.add(new String[]{"all:SOCDiceResult:", "|param=" + diceNumber});
                        recordsList.add(new String[]{"all:SOCGameState:", "|state=20"});
                        recordsList.add(new String[]{"all:SOCDiceResultResources:", diceResRsrc.toString()});
                        for (int i = 0; i < playerNums.size(); ++i)
                        {
                            int pn = playerNums.get(i);
                            recordsList.add(new String[]
                                {
                                    "p" + pn + ":SOCPlayerElements:",
                                    playerRsrcElems.get(i).toString()
                                });
                        }

                        comparesRsrcs = TestRecorder.compareRecordsToExpected
                            (records, recordsList.toArray(new String[recordsList.size()][]));

                        testedRsrcs = true;
                    }
                } else {
                    if (! testedNoRsrcs)
                    {
                        comparesNoRsrcs = TestRecorder.compareRecordsToExpected
                            (records, new String[][]
                            {
                                {"all:SOCDiceResult:", "|param=" + diceNumber},
                                {"all:SOCGameState:", "|state=20"},
                                {"all:SOCGameServerText:", "|text=No player gets anything."}
                            });

                        testedNoRsrcs = true;
                    }
                }

            } else {
                /* 7: move robber, steal from 1 player */

                if (! tested7)
                {
                    assertEquals(SOCGame.WAITING_FOR_ROBBER_OR_PIRATE, ga.getGameState());

                    try { Thread.sleep(60); }
                    catch(InterruptedException e) {}
                    compares7MoveRobber = TestRecorder.moveRobberStealSequence
                        (tcli, ga, cliPl, records,
                         new String[][]
                            {
                                {"all:SOCDiceResult:", "|param=7"},
                            });

                    tested7 = true;

                    // adjust player resources so will have to discard at next 7
                    savedRsrcs[CLIENT_PN] = new SOCResourceSet(3, 3, 3, 4, 4, 0);
                }
                else if (! tested7Discard)
                {
                    assertEquals(SOCGame.WAITING_FOR_DISCARDS, ga.getGameState());
                    assertArrayEquals(new int[]{3, 3, 3, 4, 4}, cliPl.getResources().getAmounts(false));

                    try { Thread.sleep(60); }
                    catch(InterruptedException e) {}
                    tcli.discard(ga, new SOCResourceSet(0, 0, 0, 4, 4, 0));

                    try { Thread.sleep(60); }
                    catch(InterruptedException e) {}
                    assertEquals(SOCGame.WAITING_FOR_ROBBER_OR_PIRATE, ga.getGameState());
                    compares7DiscardMove = TestRecorder.moveRobberStealSequence
                        (tcli, ga, cliPl, records,
                         new String[][]
                            {
                                {"all:SOCDiceResult:", "|param=7"},
                                {"all:SOCGameState:", "|state=50"},
                                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " needs to discard."},
                                {"p3:SOCDiscardRequest:", "|numDiscards=8"},
                                {"p3:SOCPlayerElement:", "|playerNum=3|actionType=LOSE|elementType=4|amount=4"},
                                {"p3:SOCPlayerElement:", "|playerNum=3|actionType=LOSE|elementType=5|amount=4"},
                                {"!p3:SOCPlayerElement:", "|playerNum=3|actionType=LOSE|elementType=6|amount=8|news=Y"},
                                {"all:SOCGameServerText:", "|text=" + CLIENT_NAME + " discarded 8 resources."}
                            });

                    tested7Discard = true;
                }

                // reset, for next robber move
                board.setRobberHex(0x90a, false);
            }
        }

        /* leave game, consolidate results */

        tcli.destroy();

        StringBuilder compares = new StringBuilder();
        if (comparesRsrcs != null)
        {
            compares.append("Players get resources: Message mismatch: ");
            compares.append(comparesRsrcs);
        }
        if (comparesNoRsrcs != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("No one gets resources: Message mismatch: ");
            compares.append(comparesNoRsrcs);
        }
        if (compares7MoveRobber != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Roll 7 move robber: Message mismatch: ");
            compares.append(compares7MoveRobber);
        }
        if (compares7DiscardMove != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Roll 7 discard move robber: Message mismatch: ");
            compares.append(compares7DiscardMove);
        }

        if (compares.length() > 0)
        {
            compares.insert(0, "For test " + CLIENT_NAME + ": ");
            System.err.println(compares);
            fail(compares.toString());
        }
    }

    /**
     * Test 4:1 bank trades, 2:1 port trades, undoing those trades.
     */
    @Test
    public void testBankPortTrades()
        throws IOException
    {
        assertNotNull(srv);
        // These messages are all public with no text, and should have no differences between human and robot clients.
        // We'll test the usual 4 combinations just in case.
        testOne_BankPortTrades(false, false);
        testOne_BankPortTrades(false, true);
        testOne_BankPortTrades(true, false);
        testOne_BankPortTrades(true, true);
    }

    private void testOne_BankPortTrades
        (final boolean clientAsRobot, final boolean othersAsRobot)
        throws IOException
    {
        final String CLIENT_NAME = "testBankPortTrad_" + (clientAsRobot ? 'r' : 'h') + (othersAsRobot ? "_r" : "_h");

        final StartedTestGameObjects objs =
            TestRecorder.connectLoadJoinResumeGame(srv, CLIENT_NAME, true, clientAsRobot, othersAsRobot);
        final DisplaylessTesterClient tcli = objs.tcli;
        final SOCGame ga = objs.gameAtServer;
        final SOCBoardLarge board = objs.board;
        final SOCPlayer cliPl = objs.clientPlayer;
        final Vector<QueueEntry> records = objs.records;

        final SOCResourceSet SHEEP_1 = new SOCResourceSet(0, 0, 1, 0, 0, 0),
            WHEAT_4 = new SOCResourceSet(0, 0, 0, 4, 0, 0),
            WHEAT_2 = new SOCResourceSet(0, 0, 0, 2, 0, 0);

        /* 4:1 bank trade */

        records.clear();
        assertArrayEquals(new int[]{3, 3, 3, 4, 4}, cliPl.getResources().getAmounts(false));
        assertTrue(ga.canMakeBankTrade(WHEAT_4, SHEEP_1));
        assertFalse(ga.canMakeBankTrade(WHEAT_2, SHEEP_1));
        tcli.bankTrade(ga, WHEAT_4, SHEEP_1);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertArrayEquals(new int[]{3, 3, 4, 0, 4}, cliPl.getResources().getAmounts(false));

        StringBuilder compares_4_1 = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=LOSE|elementType=4|amount=4"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=3|amount=1"},
                {"all:SOCBankTrade:", "|give=clay=0|ore=0|sheep=0|wheat=4|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0|pn=3"}
            });

        /* undo 4:1 bank trade */

        records.clear();
        assertTrue(ga.canUndoBankTrade(WHEAT_4, SHEEP_1));
        tcli.bankTrade(ga, SHEEP_1, WHEAT_4);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertArrayEquals(new int[]{3, 3, 3, 4, 4}, cliPl.getResources().getAmounts(false));

        StringBuilder compares_undo_4_1 = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=LOSE|elementType=3|amount=1"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=4|amount=4"},
                {"all:SOCBankTrade:", "|give=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=0|wheat=4|wood=0|unknown=0|pn=3"}
            });

        /* build wheat port to enable 2:1 trades */

        final int SETTLEMENT_NODE = 0xc04;
        assertNull(board.settlementAtNode(SETTLEMENT_NODE));
        assertEquals(3, cliPl.getNumPieces(SOCPlayingPiece.SETTLEMENT));
        assertEquals(2, cliPl.getPublicVP());
        assertFalse(ga.canMakeBankTrade(WHEAT_2, SHEEP_1));
        tcli.putPiece(ga, new SOCSettlement(cliPl, SETTLEMENT_NODE, board));

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertNotNull("settlement built", board.settlementAtNode(SETTLEMENT_NODE));
        assertEquals(2, cliPl.getNumPieces(SOCPlayingPiece.SETTLEMENT));
        assertEquals(3, cliPl.getPublicVP());
        assertTrue(ga.canMakeBankTrade(WHEAT_2, SHEEP_1));
        assertArrayEquals(new int[]{2, 3, 2, 3, 3}, cliPl.getResources().getAmounts(false));

        // no need to check message records; another test already checks "build settlement" message sequence

        /* 2:1 port trade */

        records.clear();
        tcli.bankTrade(ga, WHEAT_2, SHEEP_1);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertArrayEquals(new int[]{2, 3, 3, 1, 3}, cliPl.getResources().getAmounts(false));

        StringBuilder compares_2_1 = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=LOSE|elementType=4|amount=2"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=3|amount=1"},
                {"all:SOCBankTrade:", "|give=clay=0|ore=0|sheep=0|wheat=2|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0|pn=3"}
            });

        /* undo 2:1 port trade */

        records.clear();
        assertTrue(ga.canUndoBankTrade(WHEAT_2, SHEEP_1));
        tcli.bankTrade(ga, SHEEP_1, WHEAT_2);

        try { Thread.sleep(60); }
        catch(InterruptedException e) {}
        assertArrayEquals(new int[]{2, 3, 2, 3, 3}, cliPl.getResources().getAmounts(false));

        StringBuilder compares_undo_2_1 = TestRecorder.compareRecordsToExpected
            (records, new String[][]
            {
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=LOSE|elementType=3|amount=1"},
                {"all:SOCPlayerElement:", "|playerNum=3|actionType=GAIN|elementType=4|amount=2"},
                {"all:SOCBankTrade:", "|give=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=0|wheat=2|wood=0|unknown=0|pn=3"}
            });

        /* leave game, consolidate results */

        tcli.destroy();

        StringBuilder compares = new StringBuilder();
        if (compares_4_1 != null)
        {
            compares.append("4:1 bank trade: Message mismatch: ");
            compares.append(compares_4_1);
        }
        if (compares_undo_4_1 != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Undo 4:1 bank trade: Message mismatch: ");
            compares.append(compares_undo_4_1);
        }
        if (compares_2_1 != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("2:1 port trade: Message mismatch: ");
            compares.append(compares_2_1);
        }
        if (compares_undo_2_1 != null)
        {
            if (compares.length() > 0)
                compares.append("   ");
            compares.append("Undo 2:1 port trade: Message mismatch: ");
            compares.append(compares_undo_2_1);
        }

        if (compares.length() > 0)
        {
            compares.insert(0, "For test " + CLIENT_NAME + ": ");
            System.err.println(compares);
            fail(compares.toString());
        }
    }
}
