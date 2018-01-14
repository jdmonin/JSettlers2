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
import soc.message.SOCMessage;

/**
 * Tests for any SOCScenarios with inconsistent game options.
 *<P>
 * For each known scenario:
 *<UL>
 * <LI> Each of its game options must be defined
 * <LI> Each option must parse OK and be in valid range (for int or enum types)
 * <LI> Each option's minimum version must be &gt;= scenario's minimum version
 *</UL>
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestScenarioOpts
{
    /**
     * Ensure {@link SOCScenario} constructor rejects bad game option strings as expected.
     */
    @Test
    public void testScenarioConstructorGameopts()
    {
        @SuppressWarnings("unused")
        SOCScenario sc;

        try
        {
            sc = new SOCScenario("SC_TEST", 2000, -1, "desc", null, "-");
        }
        catch (IllegalArgumentException e) {
            fail("SOCScenario constructor accepts '-' gameopt");
        }

        try
        {
            sc = new SOCScenario("SC_TEST", 2000, -1, "desc", null, null);
            fail("SOCScenario constructor rejects null opt");
        }
        catch (IllegalArgumentException e) {}  // is expected

        try
        {
            sc = new SOCScenario("SC_TEST", 2000, -1, "desc", null, "");
            fail("SOCScenario constructor rejects empty opt");
        }
        catch (IllegalArgumentException e) {}  // is expected

    }

    /**
     * Test all {@link SOCScenario}s' game option consistency,
     * as described in {@link TestScenarioOpts class javadoc}.
     */
    @Test
    public void testAllScenarios()
    {
        final TreeSet<String> badScens = new TreeSet<String>(); // use TreeSet for sorted results

        for (final SOCScenario sc : SOCScenario.getAllKnownScenarios().values())
        {
            if (sc.scOpts.equals("-"))
                continue;

            try
            {
                final Map<String, SOCGameOption> parsedOpts = SOCGameOption.parseOptionsToMap(sc.scOpts);
                    // will be null if any opts failed parsing

                StringBuilder sb = null;
                // Look for unknown opts; may clip value of out-of-range opts.
                // This same pre-check is done by TestBoardLayouts.testSingleLayout(..)
                if (parsedOpts != null)
                {
                    sb = SOCGameOption.adjustOptionsToKnown(parsedOpts, null, true);
                    if (null != sb)
                    {
                        badScens.add(sc.key + ": Bad game options found by SGO.adjustOptionsToKnown: " + sb);
                        continue;
                    }
                }

                sb = new StringBuilder();

                // Check for bad or changed values from sc.scOpts name-value pairs (nvpairs)
                for (String nvpair : sc.scOpts.split(SOCMessage.sep2))
                {
                    if (nvpair.length() == 0)
                    {
                        if (sb.length() > 0)
                            sb.append(", ");
                        sb.append("Adjacent commas in sc.scOpts");
                        continue;
                    }

                    int i = nvpair.indexOf("=");
                    if ((i <= 0) || (i == nvpair.length() - 1))
                    {
                        if (sb.length() > 0)
                            sb.append(", ");
                        sb.append("Malformed name=value in sc.scOpts: \"" + nvpair + '"');

                        continue;
                    }

                    if (parsedOpts == null)
                    {
                        if (null == SOCGameOption.parseOptionNameValue(nvpair, false))
                        {
                            if (sb.length() > 0)
                                sb.append(", ");
                            sb.append("Cannot parse option: check type and syntax: \"" + nvpair + '"');
                        }
                    } else {
                        final String origKey = nvpair.substring(0, i);
                        if (! parsedOpts.containsKey(origKey))
                        {
                            if (sb.length() > 0)
                                sb.append(", ");
                            sb.append("sc.scOpts key not found in parsed opts: \"" + nvpair + '"');
                            continue;
                        }

                        final String repacked = parsedOpts.get(origKey).toString();
                        if (! repacked.equals(nvpair))
                        {
                            if (sb.length() > 0)
                                sb.append(", ");
                            sb.append("sc.scOpts value changed by SGO.adjustOptionsToKnown: \""
                                + nvpair + "\" -> \"" + repacked + '"');
                        }
                    }
                }

                if (sb.length() > 0)
                {
                    badScens.add(sc.key + ": " + sb);
                    sb.delete(0, sb.length());
                }

                // Check opt versions
                if (parsedOpts != null)
                {
                    int optsVers = -1;
                    for (SOCGameOption opt : parsedOpts.values())
                    {
                        final int vers = opt.minVersion;
                        if (vers > optsVers)
                            optsVers = vers;
                        if (vers > sc.minVersion)
                        {
                            if (sb.length() > 0)
                                sb.append(", ");
                            sb.append(opt.key);
                        }
                    }

                    if (sb.length() > 0)
                        badScens.add
                            (sc.key + ": Game options minVersion (" + optsVers + ") too new for scenario minVersion ("
                             + sc.minVersion + "): " + sb);
                }

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
