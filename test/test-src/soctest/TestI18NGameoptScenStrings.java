/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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
package soctest;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.TreeSet;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCGameOption;
import soc.game.SOCScenario;
import soc.util.SOCStringManager;

/**
 * Tests for I18N - Consistency of {@link SOCGameOption} and {@link SOCScenario} strings
 * in their Java classes versus the {@code en_US} properties file used by
 * {@link SOCStringManager#getServerManagerForClient(Locale)}.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestI18NGameoptScenStrings
{
    /** Shared StringManager for all tests to read from */
    private static SOCStringManager sm;

    @BeforeClass
    public static void loadStrings()
    {
        sm = SOCStringManager.getServerManagerForClient(new Locale("en_US"));
    }

    /**
     * Test {@link SOCGameOption} text strings.
     * @see soc.server.SOCServer#localizeKnownOptions(java.util.Locale, boolean)
     * @see soc.server.SOCServerMessageHandler#handleGAMEOPTIONGETINFOS(soc.server.genericServer.StringConnection, soc.message.SOCGameOptionGetInfos)
     */
    @Test
    public void testGameoptsText()
    {
        boolean allOK = true;

        final Map<String, SOCGameOption> allOpts = SOCGameOption.getAllKnownOptions();
        final TreeSet<String> mismatchKeys = new TreeSet<String>(),  // use TreeSet for sorted results
                              missingKeys  = new TreeSet<String>();
        for (final SOCGameOption opt : allOpts.values())
        {
            // "Hidden" gameopts starting with "_" don't need to be in sm,
            // but if present there the description strings do need to match.
            try
            {
                if (! opt.getDesc().equals(sm.get("gameopt." + opt.key)))
                    mismatchKeys.add(opt.key);
            } catch (MissingResourceException e) {
                if (opt.key.charAt(0) != '_')
                    missingKeys.add(opt.key);
            }
        }

        if (! mismatchKeys.isEmpty())
        {
            allOK = false;
            System.out.println
                ("Game opts which mismatch against toClient.properties gameopt.* strings: " + mismatchKeys);
        }

        if (! missingKeys.isEmpty())
        {
            allOK = false;
            System.out.println
                ("Game opts missing from toClient.properties gameopt.* strings: " + missingKeys);
        }

        assertTrue("SOCGameOption i18n strings", allOK);
    }

    /**
     * Test {@link SOCScenario} text strings: gamescen.*.n, some have gamescen.*.d.
     * @see soc.server.SOCServer#clientHasLocalizedStrs_gameScenarios(soc.server.genericServer.StringConnection)
     */
    @Test
    public void testScenariosText()
    {
        boolean allOK = true;

        final Map<String, SOCScenario> allScens = SOCScenario.getAllKnownScenarios();
        final TreeSet<String> mismatchKeys = new TreeSet<String>(),  // use TreeSet for sorted results
                              missingKeys  = new TreeSet<String>();
        for (final SOCScenario sc : allScens.values())
        {
            String strKey = sc.key + ".n";
            try
            {
                if (! sc.getDesc().equals(sm.get("gamescen." + strKey)))
                    mismatchKeys.add(strKey);
            } catch (MissingResourceException e) {
                missingKeys.add(strKey);
            }

            final String longDesc = sc.getLongDesc();
            if (longDesc != null)
            {
                strKey = sc.key + ".d";
                try
                {
                    if (! longDesc.equals(sm.get("gamescen." + strKey)))
                        mismatchKeys.add(strKey);
                } catch (MissingResourceException e) {
                    missingKeys.add(strKey);
                }
            }
        }

        if (! mismatchKeys.isEmpty())
        {
            allOK = false;
            System.out.println
                ("SOCScenario keys which mismatch against toClient.properties gamescen.* strings: " + mismatchKeys);
        }

        if (! missingKeys.isEmpty())
        {
            allOK = false;
            System.out.println
                ("SOCScenario keys missing from toClient.properties gamescen.* strings: " + missingKeys);
        }

        assertTrue("SOCScenario i18n strings", allOK);
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.TestI18NGameoptScenStrings");
    }

}
