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
package soctest.i18n;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.TreeSet;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCGameOption;
import soc.game.SOCScenario;
import soc.message.SOCLocalizedStrings;
import soc.message.SOCMessage;
import soc.server.genericServer.StringConnection;
import soc.util.SOCStringManager;

/**
 * Tests for I18N - Consistency of {@link SOCGameOption} and {@link SOCScenario} strings
 * in their Java classes versus the {@code en_US} properties file used by
 * {@link SOCStringManager#getServerManagerForClient(Locale)};
 * unsendable characters; combined length of all strings versus max allowable size
 * for a {@link SOCLocalizedStrings} message ({@link StringConnection#MAX_MESSAGE_SIZE_UTF8}).
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestI18NGameoptScenStrings
{
    /** Shared StringManager for all tests to read from */
    private static SOCStringManager sm;

    /** For all tests to read from, {@link SOCGameOption#getAllKnownOptions()} */
    private static Map<String, SOCGameOption> allOpts;

    /** For all tests to read from, {@link SOCScenario#getAllKnownScenarios()} */
    private static Map<String, SOCScenario> allScens;

    @BeforeClass
    public static void loadStrings()
    {
        sm = SOCStringManager.getServerManagerForClient(new Locale("en_US"));
        allOpts = SOCGameOption.getAllKnownOptions();
        allScens = SOCScenario.getAllKnownScenarios();
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

        final TreeSet<String> mismatchKeys = new TreeSet<String>(),  // use TreeSet for sorted results
                              missingKeys  = new TreeSet<String>();
        for (final SOCGameOption opt : allOpts.values())
        {
            // "Hidden" gameopts starting with "_" don't need to be in sm,
            // but if present there the description strings do need to match.
            try
            {
                final String smDesc = sm.get("gameopt." + opt.key);
                if (! opt.getDesc().equals(smDesc))
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

        final TreeSet<String> mismatchKeys = new TreeSet<String>(),  // use TreeSet for sorted results
                              missingKeys  = new TreeSet<String>();
        for (final SOCScenario sc : allScens.values())
        {
            String strKey = sc.key + ".n";
            try
            {
                final String smDesc = sm.get("gamescen." + strKey);
                if (! sc.getDesc().equals(smDesc))
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
                    final String smDesc = sm.get("gamescen." + strKey);
                    if (! longDesc.equals(smDesc))
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

    /**
     * For {@link #testDescriptionsForNet()}, test one string props file's description strings.
     * @param pfile Full filename to open and test
     * @return True if OK, or prints failed strings and return false
     * @throws Exception if {@code pfile} can't be opened and read
     */
    private boolean testDescriptionsFile(File pfile)
        throws Exception
    {
        final FileInputStream fis = new FileInputStream(pfile);
        final PropertyResourceBundle props = new PropertyResourceBundle(fis);
        try { fis.close(); }
        catch (IOException e) {}

        boolean allOK = true;

        final TreeSet<String> optBadChar  = new TreeSet<String>(), // use TreeSet for sorted results
                              scenBadChar = new TreeSet<String>();
        final ArrayList<String> optsStr = new ArrayList<String>(),
                                scenStr = new ArrayList<String>();

        for (final SOCGameOption opt : allOpts.values())
        {
            // "Hidden" gameopts starting with "_" don't need to be localized,
            // but if present there the description strings do need to be OK.
            try
            {
                final String smDesc = props.getString("gameopt." + opt.key);
                if (smDesc != null)
                {
                    optsStr.add(smDesc);
                    if (! SOCMessage.isSingleLineAndSafe(smDesc))
                        optBadChar.add(opt.key);
                }
            } catch (MissingResourceException e) {}
        }

        for (final SOCScenario sc : allScens.values())
        {
            String strKey = sc.key + ".n";
            try
            {
                final String smDesc = props.getString("gamescen." + strKey);
                if (smDesc != null)
                {
                    scenStr.add(smDesc);
                    if (! SOCMessage.isSingleLineAndSafe(smDesc))
                        scenBadChar.add(strKey);
                }
            } catch (MissingResourceException e) {}

            final String longDesc = sc.getLongDesc();
            if (longDesc != null)
            {
                strKey = sc.key + ".d";
                try
                {
                    final String smDesc = props.getString("gamescen." + strKey);
                    if (smDesc != null)
                    {
                        scenStr.add(smDesc);
                        if (smDesc.contains(SOCMessage.sep) || ! SOCMessage.isSingleLineAndSafe(smDesc, true))
                            scenBadChar.add(strKey);
                    }
                } catch (MissingResourceException e) {}
            }
        }

        if (! optBadChar.isEmpty())
        {
            allOK = false;
            System.out.println
                (pfile.getName()+ ": Game opts with gameopt.* strings failing SOCMessage.isSingleLineAndSafe(..): "
                 + optBadChar);
        }

        if (! scenBadChar.isEmpty())
        {
            allOK = false;
            System.out.println
                (pfile.getName() + ": SOCScenario key strings in gamescen.* failing SOCMessage.isSingleLineAndSafe(..): "
                 + scenBadChar);
        }

        final int MAX = StringConnection.MAX_MESSAGE_SIZE_UTF8;  // alias for brevity

        String msg = SOCLocalizedStrings.toCmd(SOCLocalizedStrings.TYPE_GAMEOPT, Integer.MAX_VALUE, optsStr);
        int L = msg.getBytes("utf-8").length;
        if (L > MAX)
        {
            allOK = false;
            System.out.println
                (pfile.getName() + ": Total length gameopt.* strings too long for SOCLocalizedStrings ("
                 + L + ", max " + MAX + ")");
        }

        msg = SOCLocalizedStrings.toCmd(SOCLocalizedStrings.TYPE_SCENARIO, Integer.MAX_VALUE, scenStr);
        L = msg.getBytes("utf-8").length;
        if (L > MAX)
        {
            allOK = false;
            System.out.println
                (pfile.getName() + ": Total length gamescen.* strings too long for SOCLocalizedStrings ("
                 + L + ", max " + MAX + ")");
        }

        return allOK;
    }

    /**
     * Network compatibility: Test all locales' {@link SOCGameOption} and {@link SOCScenario} description strings for
     * unsendable characters; test combined length of all its strings versus max length of {@link SOCLocalizedStrings}
     * ({@link StringConnection#MAX_MESSAGE_SIZE_UTF8}).
     *<P>
     * If a locale's strings fail versus {@link StringConnection#MAX_MESSAGE_SIZE_UTF8}, the server code should
     * be enhanced to break them into multiple messages when that situation arises.
     *
     * @throws Exception if any locale's props file can't be opened and read
     */
    @Test
    public void testDescriptionsForNet()
        throws Exception
    {
        String pfull = SOCStringManager.PROPS_PATH_SERVER_FOR_CLIENT + ".properties";
        if (pfull.charAt(0) != '/')
            pfull = '/' + pfull;  // we're in a separate package from SOCStringManager
        final URL u = SOCStringManager.class.getResource(pfull);
        assertNotNull("Found " + pfull, u);

        final File uf = new File(u.getPath());
        final File dir = new File(uf.getParent());
        assertTrue("Dir for " + pfull + " exists", dir.isDirectory());
        String pname = uf.getName();
        assertTrue(pname.endsWith(".properties"));
        pname = pname.substring(0, pname.length() - ".properties".length());  // to use as prefix in loop

        boolean allOK = true;
        for (final File f : dir.listFiles())
        {
            if (! f.getName().startsWith(pname))
                continue;
            if (! testDescriptionsFile(f))
                allOK = false;
        }

        assertTrue
            ("All locales' gameopt and scenario descs have no unsendable characters: " + pfull, allOK);
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.i18n.TestI18NGameoptScenStrings");
    }

}
