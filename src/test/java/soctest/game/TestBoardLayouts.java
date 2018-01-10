/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017-2018 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Map;
import java.util.TreeSet;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCScenario;
import soc.server.SOCGameHandler;
import soc.server.SOCGameListAtServer;

/**
 * Tests for inconsistent board layouts: Classic 4- and 6-player games and
 * all {@link SOCScenario}s, for 2, 3, 4 and 6 players.
 * Layout problems are found at runtime by methods like
 * {@link soc.server.SOCBoardAtServer#makeNewBoard_placeHexes}
 * checking layout details like port facings versus land hex coordinates.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestBoardLayouts
{
    /** For all tests to read from, {@link SOCScenario#getAllKnownScenarios()} */
    private static Map<String, SOCScenario> allScens;
    private static SOCGameListAtServer gl;
    private static SOCGameHandler sgh;

    @BeforeClass
    public static void setup()
    {
        allScens = SOCScenario.getAllKnownScenarios();
        sgh = new SOCGameHandler(null);
        gl = new SOCGameListAtServer();
    }

    /**
     * Test one scenario's layout for a given number of players.
     * @param sc  Scenario to test, or {@code null} for classic games
     * @param pl  Number of players (3, 4, 6, etc)
     * @return  True if OK, false if construction failed
     */
    public final boolean testSingleLayout(final SOCScenario sc, final int pl)
    {
        final Map<String, SOCGameOption> gaOpts = SOCGameOption.parseOptionsToMap
            ("PL=" + pl + ((sc != null) ? ",SC=" + sc.key : ""));
        if (gaOpts != null)
            assertNull("Unexpected problems with scenario options",
                SOCGameOption.adjustOptionsToKnown(gaOpts, null, true));
                    // this same pre-check is done by TestScenarioOpts.testAllScenarios()

        final String gaName = ((sc != null) ? sc.key : "classic") + ":" + pl;
        gl.createGame(gaName, "test", "en_US", gaOpts, sgh);
        final SOCGame ga = gl.getGameData(gaName);
        assertNotNull("Game not created", ga);

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
            return false;
        }

        return true;
    }

    /**
     * Test board layouts for classic games and all {@link SOCScenario}s for 2, 3, 4 and 6 players.
     * @see soc.server.SOCGameListAtServer#createGame(String, String, String, Map, soc.server.GameHandler)
     * @see soc.server.SOCBoardAtServer#makeNewBoard(Map)
     */
    @Test(timeout=20000)
    public void testLayouts()
    {
        final int[] PL = {2, 3, 4, 6};
        final TreeSet<String> badLayouts = new TreeSet<String>(); // use TreeSet for sorted results

        for (int pl : PL)
            if (! testSingleLayout(null, pl))
                badLayouts.add("classic:" + pl);

        for (final SOCScenario sc : allScens.values())
            for (int pl : PL)
                if (! testSingleLayout(sc, pl))
                    badLayouts.add(sc.key + ":" + pl);

        if (! badLayouts.isEmpty())
            System.out.println
                ("Board layouts: Scenario:player combinations which fail layout: " + badLayouts);

        assertTrue("Classic and scenario board layouts", badLayouts.isEmpty());
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.game.TestBoardLayouts");
    }

}
