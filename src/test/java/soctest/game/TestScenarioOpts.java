/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2018 Jeremy D Monin <jeremy@nand.net>
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

import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCGameOption;
import soc.game.SOCScenario;

/**
 * Tests for any SOCScenarios with inconsistent game options.
 *<P>
 * For each known scenario:
 *<UL>
 * <LI> Each of its game options must be defined
 * <LI> Each option's minimum version must be &gt;= scenario's minimum version
 *</UL>
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestScenarioOpts
{
    /**
     * Test all {@link SOCScenario}s' game option consistency.
     */
    @Test
    public void testAllScenarios()
    {
        final TreeSet<String> badScens = new TreeSet<String>(); // use TreeSet for sorted results

        for (final SOCScenario sc : SOCScenario.getAllKnownScenarios().values())
        {
            try
            {
                final Map<String, SOCGameOption> scOpts = SOCGameOption.parseOptionsToMap(sc.scOpts);
                if (scOpts == null)
                    continue;

                if (null != SOCGameOption.adjustOptionsToKnown(scOpts, null, true))
                {
                    // this same pre-check is done by TestBoardLayouts.testSingleLayout(..)

                    badScens.add(sc.key + ": Unexpected problems with scenario options");
                    continue;
                }

                StringBuilder sb = null;

                int optsVers = -1;
                for (SOCGameOption opt : scOpts.values())
                {
                    final int vers = opt.minVersion;
                    if (vers > optsVers)
                        optsVers = vers;
                    if (vers > sc.minVersion)
                    {
                        if (sb == null)
                        {
                            sb = new StringBuilder(opt.key);
                        } else {
                            sb.append(',');
                            sb.append(opt.key);
                        }
                    }
                }

                if (sb != null)
                    badScens.add
                        (sc.key + ": Game options minVersion (" + optsVers + ") too new for scenario minVersion ("
                         + sc.minVersion + "): " + sb);

            } catch (IllegalArgumentException e) {
                badScens.add(sc.key + ": bad option name: " + e.getMessage());
            }
        }

        if (! badScens.isEmpty())
            System.out.println
                ("SOCScenarios with inconsistent game options: " + badScens);

        assertTrue("SOCScenario game option consistency", badScens.isEmpty());
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.game.TestScenarioOpts");
    }

}
