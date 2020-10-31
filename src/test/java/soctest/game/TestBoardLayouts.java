/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017-2020 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCGame;
import soc.game.SOCGameOptionSet;
import soc.game.SOCPlayerEvent;
import soc.game.SOCScenario;
import soc.game.SOCVersionedItem;  // for javadoc
import soc.server.SOCBoardAtServer;
import soc.server.SOCGameHandler;
import soc.server.SOCGameListAtServer;

/**
 * Tests for inconsistent board layouts: Classic 4- and 6-player games and
 * all {@link SOCScenario}s, for 2, 3, 4 and 6 players.
 * Most layout problems can be found at runtime by methods like
 * {@link SOCBoardAtServer#makeNewBoard_placeHexes}
 * checking layout details like port facings versus land hex coordinates.
 *<P>
 * In v2.3.00 and newer, {@link #testLayout_lan(SOCGame)} does some basic Land Area consistency checks. <BR>
 * In v2.4.00 and newer, {@link #testLayout_movePirateCoastal(SOCGame, SOCScenario)} checks pirate ship placement. <BR>
 *<P>
 * Used for unit testing and extra testing; see {@link #roundCount} javadoc.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestBoardLayouts
    implements SOCBoardAtServer.NewBoardProgressListener
{
    /**
     * How many rounds to test each single layout.
     * If more than 1, assumes not in "unit-testing" mode and performs extra layout checks
     * using {@link SOCBoardAtServer.NewBoardProgressListener}.
     * Will try to perform this many rounds, but will stop early if needed to avoid test failure from
     * the 30-second single-round timeout, completing at least half of the requested roundCount;
     * if so, the number of completed and requested rounds is printed to {@link System#out}.
     */
    public static int roundCount = 1;

    /**
     * If {@link #roundCount} is this large, add an outer loop to get through the
     * whole list of layout types before possible timeout.
     * @see #ROUNDCOUNT_OUTER_LOOP_COUNT
     * @since 2.4.50
     */
    private static final int ROUNDCOUNT_OUTER_LOOP_THRESHOLD = 500;

    /**
     * If {@link #roundCount} is this large enough to use an outer loop
     * ({@link #ROUNDCOUNT_OUTER_LOOP_THRESHOLD}), run this many outer loops
     * and within each one, divide {@code roundCount} by this many
     * for the same overall total.
     * @since 2.4.50
     */
    private static final int ROUNDCOUNT_OUTER_LOOP_COUNT = 10;

    /** All known scenarios, from {@link SOCScenario#getAllKnownScenarios()} */
    private static Map<String, SOCScenario> allScens;

    private static SOCGameListAtServer gl;

    private static SOCGameHandler sgh;

    /**
     * Set of layouts with problems, accumulated during {@link #testLayouts()}.
     * Members are "scenarioKey:playerCount" strings from {@link #layoutNameKey(SOCScenario, int)}.
     * Uses TreeSet for sorted results.
     */
    private final TreeSet<String> badLayouts = new TreeSet<>();

    /** Current scenario, or null, being tested in {@link #testSingleLayout(SOCScenario, int)} */
    private SOCScenario currentScen;

    /** Current player count, 2-6, being tested in {@link #testSingleLayout(SOCScenario, int)} */
    private int currentNumPlayers;

    /** True if the current round already failed, within {@link #testSingleLayout(SOCScenario, int)}'s loop. */
    private boolean currentRoundFailed;

    @BeforeClass
    public static void setup()
    {
        allScens = SOCScenario.getAllKnownScenarios();
        sgh = new SOCGameHandler(null);
        gl = new SOCGameListAtServer(new Random(), SOCGameOptionSet.getAllKnownOptions());
    }

    /**
     * Make a canonical game name for a player count and scenario.
     * @param sc  Scenario being tested, or {@code null} for classic game
     * @param pl  Number of players
     * @return  Game name of the form "{@link SOCVersionedItem#key scenario.key}:playerCount" or "classic:playerCount"
     */
    private static String layoutNameKey(final SOCScenario sc, final int pl)
    {
        return ((sc != null) ? sc.key : "classic") + ":" + pl;
    }

    /**
     * Test one scenario's layout for a given number of players,
     * after updating {@link #currentScen} and {@link #currentNumPlayers}.
     * If {@link #roundCount} > 1, test that many times.
     * Each round tests once with player count and scenario,
     * then again adding {@code "BC=t3"} to test breakClumps results.
     *
     * @param sc  Scenario to test, or {@code null} for classic games
     * @param pl  Number of players (3, 4, 6, etc)
     * @return  True if OK, false if construction failed
     */
    public final boolean testSingleLayout(final SOCScenario sc, final int pl)
    {
        currentScen = sc;
        currentNumPlayers = pl;

        final String gaName = layoutNameKey(sc, pl);
        final boolean checkSC_CLVI = ((sc != null) && sc.key.equals(SOCScenario.K_SC_CLVI));
        boolean noFails = true;

        final int roundsHere = (roundCount < ROUNDCOUNT_OUTER_LOOP_THRESHOLD)
            ? roundCount
            : roundCount / ROUNDCOUNT_OUTER_LOOP_COUNT;
        for (int i = roundsHere; i >= 1; --i)
        {
            for (int j = 0; j < 2; ++j)
            {
                currentRoundFailed = false;

                final SOCGame ga = GameTestUtils.createGame
                    (pl, ((sc != null) ? sc.key : null), ((j == 1) ? "BC=t3" : null), gaName, gl, sgh);

                // Create the board. Adapted from SOCGameHandler.startGame,
                // which has a reminder comment to keep sync'd with this test method
                try
                {
                    ga.addPlayer("player", 1);
                    ga.startGame();  // SOCBoardAtServer.makeNewBoard is called here (SOCBoard if classic/no scenario).
                        // If board has Added Layout Part "AL" (SC_WOND), it's parsed and consistency-checked
                        // during makeNewBoard, which calls SOCBoardLarge.initLegalRoadsFromLandNodes()

                    if (ga.hasSeaBoard)
                        assertTrue("ga.hasSeaBoard should be SOCBoardLarge", ga.getBoard() instanceof SOCBoardLarge);

                    testLayout_lan(ga);

                    if (checkSC_CLVI)
                        testLayout_SC_CLVI(ga);

                    if (i == roundsHere)
                        // structural test that doesn't need repeating every round
                        testLayout_movePirateCoastal(ga, sc);
                }
                catch (BLException e)
                {
                    System.err.println
                        ("Inconsistency in board layout: " + gaName + ", " + pl + " players: " + e.getMessage());
                    noFails = false;
                }
                catch (Exception e)
                {
                    System.err.println("Error at board setup: " + gaName + ", " + pl + " players:");
                    e.printStackTrace();
                    noFails = false;
                }
                finally
                {
                    gl.deleteGame(gaName);
                }
            }
        }

        return noFails;
    }

    /**
     * Partial consistency check for game's {@link SOCBoardLarge#getLandAreasLegalNodes()}, if not null:
     *<UL>
     * <LI> lan[0] == null
     * <LI> No nodes in multiple Land Areas
     *</UL>
     * @param ga  Game with board to test
     * @throws BLException  if lan[0] != null or node(s) are in multiple LAs.
     *     {@link Throwable#getMessage()} will have details, including land area numbers and node coordinates.
     * @since 2.3.00
     */
    private void testLayout_lan(final SOCGame ga)
        throws BLException
    {
        if (! ga.hasSeaBoard)
            return;
        final SOCBoardLarge board = (SOCBoardLarge) ga.getBoard();
        final HashSet<Integer>[] lan = board.getLandAreasLegalNodes();
        if (lan == null)
            return;

        if (lan[0] != null)
            throw new BLException("lan[0] != null");

        final HashMap<Integer, Integer> nodeLA = new HashMap<>();
        final TreeMap<Integer, StringBuilder> laProblems = new TreeMap<>();  // use TreeMap for sorted output

        for (int laNum = 1; laNum < lan.length; ++laNum)
        {
            final Integer laInt = Integer.valueOf(laNum);
            final Integer[] nodes = lan[laNum].toArray(new Integer[0]);
            Arrays.sort(nodes);  // for stable, easy-to-browse output
            for (Integer nodeInt : nodes)
            {
                Integer alreadyLA = nodeLA.get(nodeInt);
                if (alreadyLA == null)
                {
                    nodeLA.put(nodeInt, laInt);
                } else {
                    StringBuilder sb = laProblems.get(laInt);
                    if (sb != null)
                    {
                        sb.append(", 0x");
                    } else {
                        sb = new StringBuilder("0x");
                        laProblems.put(laInt, sb);
                    }
                    sb.append(Integer.toHexString(nodeInt).toUpperCase(Locale.US));
                    sb.append(" also in ");
                    sb.append(alreadyLA);
                }
            }
        }

        if (! laProblems.isEmpty())
            throw new BLException("LAs with duplicate nodes: " + laProblems);
    }

    /**
     * For a layout of scenario {@link SOCScenario#K_SC_CLVI SC_CLVI}:
     *<UL>
     * <LI> Checks that Added Layout Part {@code "CV"} has the same contents as
     *   {@link SOCBoardLarge#getVillageAndClothLayout()}.
     *   In the unlikely event of a mismatch, will fail various Assertions
     * <LI> Check village count from {@code "CV"} against
     *   {@link SOCScenario#SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN}:
     *   Should be at least 3 more
     *</UL>
     *
     * @param ga  Game with scenario SC_CLVI
     * @throws BLException  if "CV" mismatch or not enough villages
     */
    private void testLayout_SC_CLVI(final SOCGame ga)
        throws BLException
    {
        final int[] cvPart = ((SOCBoardLarge) ga.getBoard()).getAddedLayoutPart("CV");
        if (cvPart == null)
            throw new BLException("null CV");
        final int[] cvLayout = ((SOCBoardLarge) ga.getBoard()).getVillageAndClothLayout();
        if (cvLayout == null)
            throw new BLException("null getVillageAndClothLayout()");
        assertEquals("CV length", cvPart.length, cvLayout.length);

        final int nVillages = (cvPart.length / 2) - 1;  // per getVillageAndClothLayout() javadoc
        if (nVillages < (3 + SOCScenario.SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN))
            throw new BLException
                ("Only " + nVillages + " villages; should have at least "
                 + (3 + SOCScenario.SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN)
                 + " for SOCScenario.SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN");

        // to compare CV contents, must sort them by village location
        assertEquals("CV[0]", cvPart[0], cvLayout[0]);
        assertEquals("CV[1]", cvPart[1], cvLayout[1]);
        TreeMap<Integer, Integer> partMap = new TreeMap<>(),
            layoutMap = new TreeMap<>();
        for (int i = 2; i < cvPart.length; i += 2)  // arrays have equal length
        {
            partMap.put(cvPart[i], cvPart[i+1]);
            layoutMap.put(cvLayout[i], cvLayout[i+1]);
        }

        // equal length: compare by iterating both of them
        Iterator<Integer> partI = partMap.keySet().iterator(),
            layoutI = layoutMap.keySet().iterator();
        while (partI.hasNext() && layoutI.hasNext())
        {
            Integer partKey = partI.next(), layoutKey = layoutI.next();
            if (! partKey.equals(layoutKey))
                throw new BLException
                    ("village coord mismatch: CV 0x" + Integer.toHexString(partKey)
                     + ", layout 0x" + Integer.toHexString(layoutKey));
            int partValue = partMap.get(partKey), layoutValue = layoutMap.get(layoutKey);
            if (partValue != layoutValue)
                throw new BLException
                    ("village[0x" + Integer.toHexString(partKey) + "] diceNum mismatch: CV "
                     + partValue + ", layout " + layoutValue);
        }
        assertFalse(partI.hasNext());
        assertFalse(layoutI.hasNext());
    }

    /**
     * If this game {@link SOCGame#hasSeaBoard}, check that pirate ship can be placed at any coastal water hex.
     * Checks each land hex for adjacent water (and not land right next to the board's border),
     * checks {@link SOCGame#canMovePirate(int, int)} for each such adjacent water hex.
     *<P>
     * Does nothing if {@code ! SOCGame.hasSeaBoard} or if {@code sc} is a scenario where players don't move/place
     * the pirate ship (SC_PIRI, SC_WOND).
     * @param ga  Game to check layout
     * @param sc  Game's scenario, or {@ocde null} if has none
     * @throws BLException  if pirate can't be moved to one or more coastal water hexes.
     *     {@link Throwable#getMessage()} will have details, including water hex coordinate(s).
     * @since 2.4.00
     */
    private void testLayout_movePirateCoastal(final SOCGame ga, final SOCScenario sc)
        throws BLException
    {
        if (! ga.hasSeaBoard)
            return;

        final int cpn = ga.getCurrentPlayerNumber();

        if (sc != null)
        {
            final String scKey = sc.key;
            if (SOCScenario.K_SC_PIRI.equals(scKey) || SOCScenario.K_SC_WOND.equals(scKey))
                return;  // <--- skip this scenario ---

            if (SOCScenario.K_SC_CLVI.equals(scKey))
                // ga.canMoveRobber requires this player flag
                ga.getPlayer(cpn).setPlayerEvents(SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE.flagValue);
        }

        final SOCBoardLarge board = (SOCBoardLarge) ga.getBoard();
        final int H = board.getBoardHeight(), W = board.getBoardWidth();

        // Ensure known starting conditions:
        board.setPirateHex(0, false);
        ga.setGameState(SOCGame.PLACING_PIRATE);

        final ArrayList<String> noMove = new ArrayList<>();

        // Part 1: Build set of all coastal water hexes
        // If a land hex is at edge of board without adjacent water, flag as a problem

        final HashSet<Integer> coastalWaterHexes = new HashSet<>();
        for (int r = 1; r < H; r += 2)
        {
            nextCol:
            for (int c = ((r % 4) == 1) ? 2 : 1; c < W; c += 2)
            {
                final int hexCoord = (r << 8) + c;

                if (board.getHexTypeFromCoord(hexCoord) == SOCBoard.WATER_HEX)
                    continue;

                // hexCoord is a land hex

                for (int facing = 1; facing <= 6; ++facing)
                {
                    final int adjacHex = board.getAdjacentHexToHex(hexCoord, facing);
                    if (adjacHex == 0)
                    {
                        noMove.add("0x" + Integer.toHexString(hexCoord));
                        continue nextCol;
                    }

                    if (board.getHexTypeFromCoord(adjacHex) == SOCBoard.WATER_HEX)
                        coastalWaterHexes.add(Integer.valueOf(adjacHex));
                }
            }
        }

        if (! noMove.isEmpty())
            throw new BLException
                ("land hex at board border, should only be water there: " + noMove.toString());

        //  Part 2: Check canMovePirate for each water hex in that set

        for (final int hexCoord : coastalWaterHexes)
            if (! ga.canMovePirate(cpn, hexCoord))
                noMove.add("0x" + Integer.toHexString(hexCoord));

        if (! noMove.isEmpty())
            throw new BLException
                ("canMovePirate should be true for coastal water hexes: " + noMove.toString());
    }

    /**
     * Test board layouts for classic games and all {@link SOCScenario}s for 2, 3, 4 and 6 players.
     * Tests each one for {@link #roundCount} rounds, with and without game option {@code "BC=t3"},
     * by calling {@link #testSingleLayout(SOCScenario, int)}.
     * If {@link #roundCount} &gt; 1, does multiple rounds of all that.
     * @see SOCGameListAtServer#createGame(String, String, String, SOCGameOptionSet, soc.server.GameHandler)
     * @see SOCBoardAtServer#makeNewBoard(SOCGameOptionSet)
     */
    @Test(timeout=30000)
    public void testLayouts()
    {
        final int[] PL = {2, 3, 4, 6};
        badLayouts.clear();

        if (roundCount > 1)
        {
            SOCBoardAtServer.setNewBoardProgressListener(this);
            // TODO init a total count/% display
        }

        // if roundCount is large, add an outer loop to get through the
        // whole list of layout types before possible timeout
        final int outerLoopCount = (roundCount >= ROUNDCOUNT_OUTER_LOOP_THRESHOLD)
            ? ROUNDCOUNT_OUTER_LOOP_COUNT
            : 1;
        final long startAt = System.currentTimeMillis();
        final long stopAt = startAt + 29000;  // avoid timeout when roundCount is large
        long oneOuterDurationMillis = 0;

        for (int i = 0; i < outerLoopCount; ++i)
        {
            if ((outerLoopCount > 1) && (oneOuterDurationMillis > 0)
                && (stopAt <= System.currentTimeMillis() + (1.7 * oneOuterDurationMillis))
                && (i >= (outerLoopCount / 2)))  // should get through at least half of requested rounds before timeout
            {
                System.out.println
                    ("TestBoardLayouts.testLayouts: Stopping after "
                     + (roundCount * i / ROUNDCOUNT_OUTER_LOOP_COUNT)
                     + " of " + roundCount + " rounds to stay under single-round timeout");
                break;
            }

            for (int pl : PL)
                if (! testSingleLayout(null, pl))
                    badLayouts.add(layoutNameKey(null, pl));

            for (final SOCScenario sc : allScens.values())
            {
                // TODO chk total count/% display
                for (int pl : PL)
                    if (! testSingleLayout(sc, pl))
                        badLayouts.add(layoutNameKey(sc, pl));
            }

            if ((outerLoopCount > 1) && (oneOuterDurationMillis == 0))
                oneOuterDurationMillis = System.currentTimeMillis() - startAt;
        }

        // TODO if roundCount>1, print all done 100% with stats of #fails per layout etc

        if (! badLayouts.isEmpty())
            System.out.println
                ("Board layouts: Scenario:player combinations which fail layout: " + badLayouts);

        assertTrue("Classic and scenario board layouts; see test's System.out and System.err", badLayouts.isEmpty());
    }

    /** Callback for {@link SOCBoardAtServer.NewBoardProgressListener} during {@link #testLayouts()} */
    public void hexesProgress
        (final SOCBoardAtServer board, final SOCGameOptionSet opts, final int step, final int[] landPath)
    {
        checkLandHexNumbers(board, step, landPath);
    }

    /** Callback for {@link SOCBoardAtServer.NewBoardProgressListener} during {@link #testLayouts()} */
    public void boardProgress
        (final SOCBoardAtServer board, final SOCGameOptionSet opts, final int step)
    {
        checkLandHexNumbers(board, step, null);
    }

    /**
     * Print failure details for {@link #checkLandHexNumbers(SOCBoardAtServer, int, int[])}
     * to {@link System#out}.
     * @param step  Current step; see {@link SOCBoardAtServer.NewBoardProgressListener} for list
     * @param failHexCoords  List of failed hex coordinates to print after {@code desc}; will be sorted in place
     * @param desc  Description to print before hexCoords list
     */
    private void printFailedStep(final int step, final ArrayList<Integer> failHexCoords, final String desc)
    {
        Collections.sort(failHexCoords);

        final String stepName;
        switch (step)
        {
        case SOCBoardAtServer.NewBoardProgressListener.HEXES_PLACE:
            stepName = "Hex placement"; break;
        case SOCBoardAtServer.NewBoardProgressListener.HEXES_CHECK_CLUMPS:
            stepName = "Hex check clumps"; break;
        case SOCBoardAtServer.NewBoardProgressListener.HEXES_MOVE_FREQ_NUMS:
            stepName = "Hex move freq nums"; break;
        case SOCBoardAtServer.NewBoardProgressListener.ALL_HEXES_PLACED:
            stepName = "All hexes placed"; break;
        case SOCBoardAtServer.NewBoardProgressListener.FOG_HIDE_HEXES:
            stepName = "Fog hide hexes"; break;
        case SOCBoardAtServer.NewBoardProgressListener.DONE_PORTS_PLACED:
            stepName = "Done; ports placed"; break;
        default:
            stepName = "# " + step;
        }

        System.out.print
            ("Board layout failed: " + ((currentScen != null) ? currentScen.key : "") + ' '
             + currentNumPlayers + "pl: step " + stepName + ": " + desc + ": ");
        boolean hadFirst = false;
        for (final int hc : failHexCoords)
        {
            if (hadFirst)
                System.out.print(", ");
            else
                hadFirst = true;
            System.out.print("0x" + Integer.toHexString(hc));
        }
        System.out.println();
    }

    /**
     * Do extra checks of the board layout consistency at the current step.
     * Called from listener callbacks during {@link #testLayouts()}.
     * Does nothing if {@link #currentRoundFailed} already.
     * @param board  Board layout being tested
     * @param step   Current step; see {@link SOCBoardAtServer.NewBoardProgressListener} for list
     * @param landPath  Hex coordinates to check, or {@code null} to check entire board
     */
    private void checkLandHexNumbers(final SOCBoardAtServer board, final int step, final int[] landPath)
    {
        if (currentRoundFailed)
            return;

        // SC_FOG scenario has water hexes in some land areas, so don't look for that as a problem
        final boolean checkWater = (currentScen == null) || ! SOCScenario.K_SC_FOG.equals(currentScen.key);

        ArrayList<Integer> desertWithDice = null;
        ArrayList<Integer> waterInLandSet = null;
        for (final int hexCoord : (landPath != null) ? landPath : board.getLandHexCoords())
        {
            final int htype = board.getHexTypeFromCoord(hexCoord);
            if ((htype == SOCBoard.WATER_HEX) && checkWater)
            {
                if (waterInLandSet == null)
                    waterInLandSet = new ArrayList<>();
                waterInLandSet.add(hexCoord);
            }
            else if ((htype == SOCBoard.DESERT_HEX) && (0 != board.getNumberOnHexFromCoord(hexCoord)))
            {
                if (desertWithDice == null)
                    desertWithDice = new ArrayList<>();
                desertWithDice.add(hexCoord);
            }
        }

        boolean allOK = true;

        if (desertWithDice != null)
        {
            printFailedStep(step, desertWithDice, "Desert hexes having dice numbers");
            allOK = false;
        }

        if (waterInLandSet != null)
        {
            printFailedStep(step, waterInLandSet, "Water hexes in land set");
            allOK = false;
        }

        if (! allOK)
        {
            currentRoundFailed = true;
            badLayouts.add(layoutNameKey(currentScen, currentNumPlayers));  // OK if already present
        }
    }

    /**
     * Run tests; 1 round of testing by default. 1 optional arg: {@link #roundCount}.
     * @param args Arguments: empty or 1 argument: round count as integer string.
     */
    public static void main(String[] args)
    {
        boolean parseOK = true;
        if (args.length > 1)
        {
            System.err.println("Unknown argument: Only number of rounds is permitted");
            parseOK = false;
        } else if (args.length == 1) {
            try {
                roundCount = Integer.parseInt(args[0]);
                if (roundCount <= 0)
                {
                    System.err.println("Round count must be >= 1");
                    parseOK = false;
                }
            } catch (NumberFormatException e) {
                System.err.println("Could not parse as round count");
                parseOK = false;
            }
        }

        if (! parseOK)
            System.exit(2);

        org.junit.runner.JUnitCore.main("soctest.game.TestBoardLayouts");
    }

    /**
     * Specific exception for inconsistency details, so
     * {@link TestBoardLayouts#testSingleLayout(SOCScenario, int)} knows
     * it can skip printing stack trace.
     * @since 2.3.00
     */
    @SuppressWarnings("serial")
    private static class BLException extends IllegalStateException
    {
        public BLException(final String message)
        {
            super(message);
        }
    }

}
