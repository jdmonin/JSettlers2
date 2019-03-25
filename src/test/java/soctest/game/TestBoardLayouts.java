/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017-2019 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Collections;
import java.util.Map;
import java.util.TreeSet;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCScenario;
import soc.game.SOCVersionedItem;  // for javadoc
import soc.server.SOCBoardAtServer;
import soc.server.SOCGameHandler;
import soc.server.SOCGameListAtServer;

/**
 * Tests for inconsistent board layouts: Classic 4- and 6-player games and
 * all {@link SOCScenario}s, for 2, 3, 4 and 6 players.
 * Layout problems are found at runtime by methods like
 * {@link SOCBoardAtServer#makeNewBoard_placeHexes}
 * checking layout details like port facings versus land hex coordinates.
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
     */
    public static int roundCount = 1;

    /** All known scenarios, from {@link SOCScenario#getAllKnownScenarios()} */
    private static Map<String, SOCScenario> allScens;

    private static SOCGameListAtServer gl;

    private static SOCGameHandler sgh;

    /**
     * Set of layouts with problems, accumulated during {@link #testLayouts()}.
     * Members are "scenarioKey:playerCount" strings from {@link #layoutNameKey(SOCScenario, int)}.
     * Uses TreeSet for sorted results.
     */
    private final TreeSet<String> badLayouts = new TreeSet<String>();

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
        gl = new SOCGameListAtServer();
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
        boolean noFails = true;

        for (int i = roundCount; i >= 1; --i)
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
                    ga.startGame();  // SOCBoard/SOCBoardAtServer.makeNewBoard is called here
                    gl.deleteGame(gaName);
                }
                catch (Exception e)
                {
                    System.err.println("Error at board setup: " + gaName + ", " + pl + " players:");
                    e.printStackTrace();
                    noFails = false;
                }
            }
        }

        return noFails;
    }

    /**
     * Test board layouts for classic games and all {@link SOCScenario}s for 2, 3, 4 and 6 players.
     * Tests each one for {@link #roundCount} rounds, with and without game option {@code "BC=t3"}.
     * @see SOCGameListAtServer#createGame(String, String, String, Map, soc.server.GameHandler)
     * @see SOCBoardAtServer#makeNewBoard(Map)
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

        // TODO if roundCount>1, print all done 100% with stats of #fails per layout etc

        if (! badLayouts.isEmpty())
            System.out.println
                ("Board layouts: Scenario:player combinations which fail layout: " + badLayouts);

        assertTrue("Classic and scenario board layouts", badLayouts.isEmpty());
    }

    /** Callback for {@link SOCBoardAtServer.NewBoardProgressListener} during {@link #testLayouts()} */
    public void hexesProgress
        (final SOCBoardAtServer board, final Map<String, SOCGameOption> opts, final int step, final int[] landPath)
    {
        checkLandHexNumbers(board, step, landPath);
    }

    /** Callback for {@link SOCBoardAtServer.NewBoardProgressListener} during {@link #testLayouts()} */
    public void boardProgress
        (final SOCBoardAtServer board, final Map<String, SOCGameOption> opts, final int step)
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
                    waterInLandSet = new ArrayList<Integer>();
                waterInLandSet.add(hexCoord);
            }
            else if ((htype == SOCBoard.DESERT_HEX) && (0 != board.getNumberOnHexFromCoord(hexCoord)))
            {
                if (desertWithDice == null)
                    desertWithDice = new ArrayList<Integer>();
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

}
