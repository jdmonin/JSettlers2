/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017,2019-2020,2022,2026 Jeremy D Monin <jeremy@nand.net>
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
import java.util.regex.Matcher;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCScenario;
import soc.game.SOCVersionedItem;
import soc.message.SOCLocalizedStrings;
import soc.message.SOCMessage;
import soc.server.genericServer.Connection;
import soc.util.SOCStringManager;

/**
 * Tests for I18N - Consistency of {@link SOCGameOption} and {@link SOCScenario} strings
 * in their Java classes versus the {@code en_US} properties file used by
 * {@link SOCStringManager#getServerManagerForClient(Locale)};
 * unsendable characters; combined length of all strings versus max allowable size
 * for a {@link SOCLocalizedStrings} message ({@link Connection#MAX_MESSAGE_SIZE_UTF8}).
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestI18NGameoptScenStrings
{
    /** Shared StringManager for all tests to read from */
    private static SOCStringManager sm;

    /** For all tests to read from, {@link SOCGameOptionSet#getAllKnownOptions()} */
    private static SOCGameOptionSet allOpts;

    /** For all tests to read from, {@link SOCScenario#getAllKnownScenarios()} */
    private static Map<String, SOCScenario> allScens;

    @BeforeClass
    public static void loadStrings()
    {
        sm = SOCStringManager.getServerManagerForClient(Locale.US);
        allOpts = SOCGameOptionSet.getAllKnownOptions();
        allScens = SOCScenario.getAllKnownScenarios();
    }

    /**
     * Test {@link SOCGameOption} text strings.
     * @see soc.server.SOCServer#localizeKnownOptions(java.util.Locale, boolean)
     * @see soc.server.SOCServerMessageHandler#handleGAMEOPTIONGETINFOS(Connection, soc.message.SOCGameOptionGetInfos)
     */
    @Test
    public void testGameoptsText()
    {
        boolean allOK = true;

        final TreeSet<String> mismatchKeys = new TreeSet<>(),  // use TreeSet for sorted results
                              missingKeys  = new TreeSet<>();
        for (final SOCGameOption opt : allOpts)
        {
            // "Hidden" gameopts starting with "_" don't need to be in sm, nor do third-party or
            // temporary DEBUG options, but if present there the description strings do need to match.
            try
            {
                final String smDesc = sm.get("gameopt." + opt.key);
                if (! opt.getDesc().equals(smDesc))
                    mismatchKeys.add(opt.key);
            } catch (MissingResourceException e) {
                if ((opt.key.charAt(0) != '_')
                    && ! (opt.key.startsWith("DEBUG") || opt.hasFlag(SOCGameOption.FLAG_3RD_PARTY)))
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

        assertTrue("SOCGameOption i18n strings; see standard output for details", allOK);

        // other misc gameopt-related strings to test for:
        String smText = sm.get("gameopt.desc.cannot_create");
        assertEquals("(Cannot create)", smText);  // If wording changes, update here; mostly testing that the key is defined
    }

    /**
     * Test {@link SOCScenario} text strings: gamescen.*.n, some have gamescen.*.d.
     * Also test that {@link SOCScenario#getScenario(String) SOCScenario.getScenario("SC_WOND")} has a description.
     *
     * @see soc.server.SOCClientData#localeHasGameScenarios(Connection)
     */
    @Test
    public void testScenariosText()
    {
        // pre-test: Hardcoded string needed by SCD.localeHasGameScenarios
        {
            final SOCScenario scWond = SOCScenario.getScenario(SOCScenario.K_SC_WOND);
            assertNotNull("missing required SOCScenario object SC_WOND", scWond);
            final String desc = scWond.getDesc();
            final int L = (desc != null) ? desc.length() : 0;
            assertNotEquals("SOCScenario SC_WOND missing desc", 0, L);
        }

        boolean allOK = true;

        final TreeSet<String> mismatchKeys = new TreeSet<>(),  // use TreeSet for sorted results
                              missingKeys  = new TreeSet<>();
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

        assertTrue("SOCScenario i18n strings; see standard output for details", allOK);
    }

    /**
     * For {@link #testDescriptionsFile(File)}, test a gameopt or scenario's desc
     * vs {@link SOCVersionedItem#getSortRank()}. Uses {@link SOCVersionedItem#REGEX_SORT_RANK_PREFIX}.
     * @param item  item being tested; will call {@link SOCVersionedItem#setDesc(String) item.setDesc(smDesc)}
     * @param smDesc  description from props file
     * @since 2.6.00
     */
    private void checkDescForSortRank(SOCVersionedItem item, String smDesc)
    {
        final String test = item.getClass().getSimpleName() + " " + item.key + ": \"" + smDesc + "\"",
            origDesc = item.getDesc();

        Matcher m = SOCVersionedItem.REGEX_SORT_RANK_PREFIX.matcher(smDesc);
        boolean expectRankValue = m.find();
        try {
            item.setDesc(smDesc);
            if (expectRankValue)
                assertNotEquals
                    ("setDesc expected getSortRank != MAX_VALUE: " + test, Integer.MAX_VALUE, item.getSortRank());
            else
                assertEquals
                    ("setDesc expected getSortRank == MAX_VALUE: " + test, Integer.MAX_VALUE, item.getSortRank());
        } catch (IllegalArgumentException e) {
            fail("setDesc failed: " + test);
        }

        item.setDesc(origDesc);
    }

    /**
     * For {@link #testDescriptionsForNet()}, test one pair of string props files' description strings.
     * @param pfile  Server strings file to open and test
     * @param pfileCli  Client strings file to open and test
     * @param localeSuffix  Filename suffix for locale being tested, like {@code "_es.properties"}
     * @return True if OK, or prints failed strings and return false
     * @throws Exception if {@code pfile} or {@code pfileCli} can't be opened and read
     */
    private boolean testDescriptionsFiles
        (File pfile, File pfileCli, final String localeSuffix)
        throws Exception
    {
        FileInputStream fis = new FileInputStream(pfile);
        final PropertyResourceBundle propsSrv = new PropertyResourceBundle(fis);
        try { fis.close(); }
        catch (IOException e) {}

        fis = new FileInputStream(pfileCli);
        final PropertyResourceBundle propsCli = new PropertyResourceBundle(fis);
        try { fis.close(); }
        catch (IOException e) {}

        boolean allOK = true;

        final TreeSet<String> optBadChar  = new TreeSet<>(), // use TreeSet for sorted results
                              scenBadChar = new TreeSet<>(),
                              optDescMissingScenPrefix = new TreeSet<>();
        final ArrayList<String> optsStr = new ArrayList<>(),
                                scenStr = new ArrayList<>(),
                                keyNotSameAtSrvCli = new ArrayList<>();

        // Client NGOF hides a redundant "Scenarios: " consistent prefix in scenario game option descriptions,
        // but the gameopt text is stored at server, so we'll compare server opt strings against that prefix from client strings.
        String gameoptScenDescPrefix = null;
        final boolean gameoptScenDescMissingOK = (localeSuffix.startsWith("_pl."));
        try
        {
            gameoptScenDescPrefix = propsCli.getString("game.options.scenario.optprefix");
            assertNotNull(gameoptScenDescPrefix);
            assertFalse(gameoptScenDescPrefix.trim().isEmpty());
        } catch (MissingResourceException e) {
            // unlikely to be missing, except in deliberately-empty _en file
            if (! localeSuffix.startsWith("_en."))
            {
                fail(pfileCli.getName() + ": Missing required key game.options.scenario.optprefix");
                return false;  // to satisfy compiler; fail will end execution
            }
        }

        for (final SOCGameOption opt : allOpts)
        {
            // "Hidden" gameopts starting with "_" don't need to be localized,
            // but if present there the description strings do need to be OK.
            try
            {
                final String key = "gameopt." + opt.key;
                final String smDesc = propsSrv.getString(key);
                if (smDesc != null)
                {
                    optsStr.add(smDesc);
                    if (SOCMessage.isSingleLineAndSafe(smDesc))
                        checkDescForSortRank(opt, smDesc);
                    else
                        optBadChar.add(opt.key);

                    if (opt.key.startsWith("_SC_") && (! gameoptScenDescMissingOK)
                        && (! smDesc.startsWith(gameoptScenDescPrefix)))
                        optDescMissingScenPrefix.add(opt.key);
                }
            } catch (MissingResourceException e) {}
        }

        for (final SOCScenario sc : allScens.values())
        {
            String strKey = sc.key + ".n";
            try
            {
                final String smDesc = propsSrv.getString("gamescen." + strKey);
                if (smDesc != null)
                {
                    scenStr.add(smDesc);
                    if (SOCMessage.isSingleLineAndSafe(smDesc))
                        checkDescForSortRank(sc, smDesc);
                    else
                        scenBadChar.add(strKey);
                }
            } catch (MissingResourceException e) {}

            final String longDesc = sc.getLongDesc();
            if (longDesc != null)
            {
                strKey = sc.key + ".d";
                try
                {
                    final String smDesc = propsSrv.getString("gamescen." + strKey);
                    if (smDesc != null)
                    {
                        scenStr.add(smDesc);
                        if (smDesc.contains(SOCMessage.sep) || ! SOCMessage.isSingleLineAndSafe(smDesc, true))
                            scenBadChar.add(strKey);
                    }
                } catch (MissingResourceException e) {}
            }
        }

        // Check for a few strings which should be the same at client and server
        for (String key : new String[]{
            "action.rolled.sc_piri.player.lost.rsrcs.to.fleet", "action.rolled.sc_piri.player.tied",
            "action.rolled.sc_piri.player.won.pick.free", "action.rolled.sc_piri.you.lost.rsrcs.to.fleet"
        })
        {
            String srvVal = null, cliVal = null;
            try
            {
                srvVal = propsSrv.getString(key);
            } catch (MissingResourceException e) {}
            try
            {
                cliVal = propsCli.getString(key);
            } catch (MissingResourceException e) {}

            if (srvVal == null)
            {
                if (cliVal != null)
                    keyNotSameAtSrvCli.add(key + ": missing in " + pfile.getName());
            } else {
                if (cliVal == null)
                    keyNotSameAtSrvCli.add(key + ": missing in " + pfileCli.getName());
                else if (! srvVal.equals(cliVal))
                    keyNotSameAtSrvCli.add(key + ": text differs in " + pfile.getName() + " vs " + pfileCli.getName());
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

        if (! optDescMissingScenPrefix.isEmpty())
        {
            allOK = false;
            System.out.println
                (pfile.getName() + ": Scenario game opt strings in gameopt.* missing prefix text from client key game.options.scenario.optprefix: "
                 + optDescMissingScenPrefix);
        }

        if (! keyNotSameAtSrvCli.isEmpty())
        {
            allOK = false;
            System.out.println
                ("these keys should be same at server, client: " + keyNotSameAtSrvCli);
        }

        final int MAX = Connection.MAX_MESSAGE_SIZE_UTF8;  // alias for brevity

        String msg = new SOCLocalizedStrings(SOCLocalizedStrings.TYPE_GAMEOPT, Integer.MAX_VALUE, optsStr).toCmd();
        int L = msg.getBytes("utf-8").length;
        if (L > MAX)
        {
            allOK = false;
            System.out.println
                (pfile.getName() + ": Total length gameopt.* strings too long for SOCLocalizedStrings ("
                 + L + ", max " + MAX + ")");
        }

        msg = new SOCLocalizedStrings(SOCLocalizedStrings.TYPE_SCENARIO, Integer.MAX_VALUE, scenStr).toCmd();
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
     * ({@link Connection#MAX_MESSAGE_SIZE_UTF8}).
     *<P>
     * If a locale's strings fail versus {@link Connection#MAX_MESSAGE_SIZE_UTF8}, the server code should
     * be enhanced to break them into multiple messages when that situation arises.
     *<P>
     * Also checks:
     *<UL>
     * <LI> Each server locale file has a corresponding client locale file
     * <LI> {@code action.rolled.sc_piri.*} text is the same at server and client
     * <LI> Server's scenario-related game option descriptions begin with text of client key {@code "game.options.scenario.optprefix"}
     *</UL>
     *
     * @throws Exception if any locale's props file can't be opened and read
     */
    @Test
    public void testDescriptionsForNet()
        throws Exception
    {
        String pfullSrv = SOCStringManager.PROPS_PATH_SERVER_FOR_CLIENT + ".properties";
        if (pfullSrv.charAt(0) != '/')
            pfullSrv = '/' + pfullSrv;  // we're in a separate package from SOCStringManager
        final URL uSrv = SOCStringManager.class.getResource(pfullSrv);
        assertNotNull("Couldn't find " + pfullSrv, uSrv);

        String pfullCli = SOCStringManager.PROPS_PATH_AT_CLIENT + ".properties";
        if (pfullCli.charAt(0) != '/')
            pfullCli = '/' + pfullCli;  // we're in a separate package from SOCStringManager
        final URL uCli = SOCStringManager.class.getResource(pfullCli);
        assertNotNull("Couldn't find " + pfullCli, uSrv);

        final File ufSrv = new File(uSrv.getPath());
        final File dirSrv = new File(ufSrv.getParent());
        assertTrue("Dir for server strings " + pfullSrv + " should exist", dirSrv.isDirectory());

        final File ufCli = new File(uCli.getPath());
        final File dirCli = new File(ufCli.getParent());
        assertTrue("Dir for client strings " + pfullCli + " should exist", dirCli.isDirectory());

        String pnameSrv = ufSrv.getName();
        assertTrue(pnameSrv.endsWith(".properties"));
        pnameSrv = pnameSrv.substring(0, pnameSrv.length() - ".properties".length());  // to use as prefix in loop
        final int pnameSrvLen = pnameSrv.length();

        String pnameCli = ufCli.getName();
        assertTrue(pnameCli.endsWith(".properties"));
        pnameCli = pnameCli.substring(0, pnameCli.length() - ".properties".length());  // to get client file based on server filename

        boolean allOK = true;
        for (final File fSrv : dirSrv.listFiles())
        {
            final String fnameSrv = fSrv.getName();
            if (! fnameSrv.startsWith(pnameSrv))
                continue;

            String localeSuffix = fnameSrv.substring(pnameSrvLen);
            String fnameCli = pnameCli + localeSuffix;
            File fCli = new File(dirCli, fnameCli);
            assertTrue("Client file exists: " + fnameCli + " for server " + fnameSrv, fCli.exists());

            if (! testDescriptionsFiles(fSrv, fCli, localeSuffix))
                allOK = false;
        }

        assertTrue
            ("Localized gameopt and scenario strings; see test output for details: " + pfullSrv, allOK);
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.i18n.TestI18NGameoptScenStrings");
    }

}
