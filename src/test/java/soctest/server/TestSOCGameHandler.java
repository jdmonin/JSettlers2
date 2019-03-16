/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2018-2019 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCScenario;
import soc.server.SOCGameHandler;
import soc.util.SOCFeatureSet;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCGameHandler}.
 *<P>
 * TODO: As of mid-2018, new functionality will come with unit tests but all existing {@code SOCGameHandler}
 * code still needs to have unit tests written.
 *
 * @since 2.0.00
 */
public class TestSOCGameHandler
{
    /** Tests for {@link SOCGameHandler#calcGameClientFeaturesRequired(SOCGame)}. */
    @Test
    public void testCalcGameClientFeaturesRequired()
    {
        /**
         * Game opts and expected resulting client features.
         * When one client feature is expected, will test with String.equals.
         * When multiple client features are expected, will test with featsObj.findMissingAgainst(otherFeatsSet),
         * which unfortunately also returns true if an int-valued feature > its value in otherFeatsSet.
         */
        final String[][] gameoptFeatPairs =
            {
                { null, null },
                { "SBL=t", ';' + SOCFeatureSet.CLIENT_SEA_BOARD + ';' },
                { "PL=4", null },
                { "PL=5", ';' + SOCFeatureSet.CLIENT_6_PLAYERS + ';' },
                { "SC=" + SOCScenario.K_SC_4ISL, ';' + SOCFeatureSet.CLIENT_SCENARIO_VERSION + "=2000;" },
                { "SC=_NONEXISTENT_", ';' + SOCFeatureSet.CLIENT_SCENARIO_VERSION + "=" + Integer.MAX_VALUE + ";" },
                { "SBL=t,PL=5", ';' + SOCFeatureSet.CLIENT_SEA_BOARD + ';' + SOCFeatureSet.CLIENT_6_PLAYERS + ';' },
            };

        final SOCGameHandler sgh = new SOCGameHandler(null);  // null SOCServer is OK for this test, not in general

        for (String[] pair : gameoptFeatPairs)
        {
            final String gameopts = pair[0], featsStr = pair[1];

            final SOCGame ga = new SOCGame("testname", SOCGameOption.parseOptionsToMap(gameopts));
            sgh.calcGameClientFeaturesRequired(ga);
            final SOCFeatureSet cliFeats = ga.getClientFeaturesRequired();
            if (featsStr == null)
            {
                if (cliFeats != null)
                    fail("For gameopts " + gameopts + " expected no cli feats but got " + cliFeats.getEncodedList());
            } else if (cliFeats == null) {
                fail("For gameopts " + gameopts + " expected some cli feats but got null");
            } else {
                final boolean hasMulti = (featsStr.indexOf(';', 2) < (featsStr.length() - 1));
                final boolean passed = (hasMulti)
                    ? (null == cliFeats.findMissingAgainst(new SOCFeatureSet(featsStr), true))
                    : featsStr.equals(cliFeats.getEncodedList());
                if (! passed)
                    fail("For gameopts " + gameopts + " expected cli feats " + featsStr
                         + " but got " + cliFeats.getEncodedList());
            }
        }
    }

}
