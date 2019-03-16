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

import java.util.Map;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.server.SOCBoardAtServer;
import soc.server.SOCGameHandler;
import soc.server.SOCGameListAtServer;

import static org.junit.Assert.*;

/**
 * Non-testing class to hold utility methods to help run the actual tests.
 * @since 2.0.00
 */
public abstract class GameTestUtils
{
    /**
     * Create a game with the given player count, with optional scenario and options.
     * Parses options, asserts options != {@code null} and that no problems were found by
     * {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean)}.
     * Calls {@link SOCGameListAtServer#createGame(String, String, String, Map, soc.server.GameHandler)}.
     * Asserts created game != {@code null}.
     *<P>
     * Next steps for caller to use this game:
     *<UL>
     * <LI> Call {@link SOCGame#addPlayer(String, int)} for at least one player seat number
     * <LI> Call {@link SOCGame#startGame()}, which will also call {@link SOCBoardAtServer#makeNewBoard(Map)}
     * <LI> Call {@link SOCBoardAtServer#startGame_scenarioSetup(SOCGame)}
     * <LI> Test as needed.
     * <LI> When done, call {@link SOCGameListAtServer#deleteGame(String) gl.deleteGame(ga.getName())}
     *</UL>
     *
     * @param pl  Number of players (3, 4, 6, etc)
     * @param scName   Scenario name to test, or {@code null} for classic games
     * @param otherOpts  String of any other {@link SOCGameOption} names=values, or {@code null}
     * @param gaName  Game name, or {@code null} to use default "testGame"
     * @param gl  SOCGameList to use for game factory
     * @param sgh  Required SOCGameHandler to pass to game
     * @return  Newly created game, which was also added to {@code gl}
     */
    public static SOCGame createGame
        (final int pl, final String scName, final String otherOpts, String gaName,
        final SOCGameListAtServer gl, final SOCGameHandler sgh)
    {
        if (gaName == null)
            gaName = "testGame";

        final String optsStr =
            ("PL=" + pl + ((scName != null) ? ",SC=" + scName : "")
             + ((otherOpts != null) ? "," + otherOpts : ""));
        final Map<String, SOCGameOption> gaOpts = SOCGameOption.parseOptionsToMap(optsStr);
        assertNotNull("Unexpected problems with scenario option string: " + optsStr, gaOpts);
        assertNull("Unexpected problems with scenario options",
            SOCGameOption.adjustOptionsToKnown(gaOpts, null, true));
                // this same pre-check is done by TestScenarioOpts.testAllScenarios()

        gl.createGame(gaName, "test", "en_US", gaOpts, sgh);
        final SOCGame ga = gl.getGameData(gaName);
        assertNotNull("Game not created: " + gaName, ga);

        return ga;
    }

    /**
     * Does an array contain a given value?
     * @param arr  Unsorted array to search, or {@code null}
     * @param val  Value to find
     * @return  True if {@code val} found in {@code arr}, false otherwise
     */
    public static boolean contains(final int[] arr, final int val)
    {
        if (arr == null)
            return false;

        for (int i : arr)
            if (i == val)
                return true;

        return false;
    }

}
