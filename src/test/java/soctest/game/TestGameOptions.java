/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2018-2025 Jeremy D Monin <jeremy@nand.net>
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.client.ServerGametypeInfo;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCScenario;
import soc.game.SOCVersionedItem;
import soc.util.SOCFeatureSet;
import soc.util.Version;

/**
 * Tests for {@link SOCGameOption}, {@link SOCGameOptionSet}, and {@link ServerGametypeInfo}.
 *<P>
 * TODO add more basic-functionality tests
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestGameOptions
{
    /**
     * Unchanging set of Known Options, from {@link SOCGameOptionSet#getAllKnownOptions()}.
     * Not to be used for unit tests which add/remove/change Known Options.
     */
    private static SOCGameOptionSet knownOpts;

    /** Set up this test class's Known Options. */
    @BeforeClass
    public static void setup()
    {
        knownOpts = SOCGameOptionSet.getAllKnownOptions();
    }

    /**
     * Test that {@link SOCGameOption#setIntValue(int)} works and that, instead of throwing an exception,
     * values outside of min/max range are clipped to that range; uses intbool option {@code "VP"}.
     */
    @Test
    public void testSetIntValueRange()
    {
        final SOCGameOption vp = knownOpts.getKnownOption("VP", true);
        assertNotNull(vp);
        vp.setIntValue(12);   // is within range
        assertEquals(12, vp.getIntValue());
        vp.setIntValue(2);  // too low
        assertEquals("should clip to min", vp.minIntValue, vp.getIntValue());
        vp.setIntValue(vp.maxIntValue + 999);  // too high
        assertEquals("should clip to max", vp.maxIntValue, vp.getIntValue());
    }

    /**
     * Test that contents of {@link SOCGameOptionSet#getAllKnownOptions()} are consistent internally
     * and follow naming conventions.
     *<UL>
     * <LI> Key must not have {@code '3'} as second character unless option has {@link SOCGameOption#FLAG_3RD_PARTY};
     *      if option has that flag, it has {@code '3'} there.
     *</UL>
     */
    @Test
    public void testKnownOptionMapKeysConsistent()
    {
        for (SOCGameOption opt : knownOpts)
        {
            final String okey = opt.key;
            boolean named3p = (okey.length() >= 2) && (okey.charAt(1) == '3');
            assertEquals
                ("key " + okey + " second char is '3' only if FLAG_3RD_PARTY",
                 named3p, opt.hasFlag(SOCGameOption.FLAG_3RD_PARTY));
        }
    }

    /**
     * Test {@link SOCGameOptionSet#getKnownOption(String, boolean)}.
     * @since 2.7.00
     */
    @Test
    public void testGetKnownOption()
    {
        assertNull(knownOpts.get("ZZ"));
        assertNull(knownOpts.getKnownOption("ZZ", false));

        final SOCGameOption knownOptVP = knownOpts.get("VP");
        assertNotNull(knownOptVP);
        SOCGameOption opt = knownOpts.getKnownOption("VP", false);
        assertNotNull(opt);
        assertTrue("same reference", opt == knownOptVP);

        // check type, range, default
        assertEquals(SOCGameOption.OTYPE_INTBOOL, opt.optType);
        assertEquals(10, opt.minIntValue);
        assertEquals(10, opt.defaultIntValue);
        assertEquals(20, opt.maxIntValue);
        assertEquals(10, opt.getIntValue());
        assertFalse(opt.defaultBoolValue);
        assertFalse(opt.getBoolValue());

        // copy, should still have same range
        opt = knownOpts.getKnownOption("VP", true);
        assertNotNull(opt);
        assertTrue("new reference", opt != knownOptVP);
        assertEquals(SOCGameOption.OTYPE_INTBOOL, opt.optType);
        assertEquals(10, opt.minIntValue);
        assertEquals(10, opt.defaultIntValue);
        assertEquals(20, opt.maxIntValue);
        assertEquals(10, opt.getIntValue());
        assertFalse(opt.defaultBoolValue);
        assertFalse(opt.getBoolValue());

        // get from parsed set, should still have same range
        Map<String, SOCGameOption> opts = SOCGameOption.parseOptionsToMap("VP=t11", knownOpts);
        assertNotNull(opts);
        assertEquals(1, opts.size());
        opt = opts.get("VP");
        assertNotNull(opt);
        assertTrue("new reference", opt != knownOptVP);
        assertEquals(SOCGameOption.OTYPE_INTBOOL, opt.optType);
        assertEquals(10, opt.minIntValue);
        assertEquals(10, opt.defaultIntValue);
        assertEquals(20, opt.maxIntValue);
        assertEquals(11, opt.getIntValue());
        assertFalse(opt.defaultBoolValue);
        assertTrue(opt.getBoolValue());

        // get all option types from larger parsed set; should still have same range as known opt
        opts = SOCGameOption.parseOptionsToMap("RD=t,VP=t11,PL=3,SC=xyz,BLL=t", knownOpts);
        assertNotNull(opts);
        assertEquals(5, opts.size());
        opt = opts.get("VP");
        assertNotNull(opt);
        assertTrue("new reference", opt != knownOptVP);
        assertEquals(SOCGameOption.OTYPE_INTBOOL, opt.optType);
        assertEquals(10, opt.minIntValue);
        assertEquals(10, opt.defaultIntValue);
        assertEquals(20, opt.maxIntValue);
        assertEquals(11, opt.getIntValue());
        assertFalse(opt.defaultBoolValue);
        assertTrue(opt.getBoolValue());
        opt = opts.get("RD");
        assertNotNull(opt);
        assertTrue("new reference", opt != knownOpts.getKnownOption("RD", false));
        assertEquals(SOCGameOption.OTYPE_BOOL, opt.optType);
        assertFalse(opt.defaultBoolValue);
        assertTrue(opt.getBoolValue());
        opt = opts.get("PL");
        assertNotNull(opt);
        assertEquals(SOCGameOption.OTYPE_INT, opt.optType);
        assertEquals(2, opt.minIntValue);
        assertEquals(4, opt.defaultIntValue);
        assertEquals(6, opt.maxIntValue);
        assertEquals(3, opt.getIntValue());
        opt = opts.get("SC");
        assertNotNull(opt);
        assertEquals(SOCGameOption.OTYPE_STR, opt.optType);
        assertEquals("xyz", opt.getStringValue());
        // OTYPE_*: check new type from that set
    }

    /**
     * Test {@link SOCGameOption#copyDefaults(SOCGameOption)}.
     * @since 2.5.00
     */
    @Test
    public void testCopyDefaults()
    {
        assertNull(SOCGameOption.copyDefaults(null));

        final SOCGameOption optStr = new SOCGameOption("ZZ", 1000, 2500, 99, false, 0, "ZZ");
        assertTrue("optStr unchanged", optStr == SOCGameOption.copyDefaults(optStr));  // no int/bool fields

        final SOCGameOption opt = new SOCGameOption("ZZ", 1000, 2500, true, 7, 0, 9, 0, "ZZ");  // is OTYPE_INTBOOL
        assertTrue(opt.defaultBoolValue);
        assertTrue(opt.getBoolValue());
        assertEquals(7, opt.defaultIntValue);
        assertEquals(7, opt.getIntValue());
        assertTrue("opt unchanged", opt == SOCGameOption.copyDefaults(opt));  // returns same reference

        // change bool and/or int value
        for (int i = 1; i < 4; ++i)
        {
            final boolean newBool = (0 == (i & 0x01));
            final int newInt = (0 != (i & 0x02)) ? 3 : 7;
            opt.setBoolValue(newBool);
            opt.setIntValue(newInt);

            final SOCGameOption optUpdate = SOCGameOption.copyDefaults(opt);
            assertFalse("opt copied for i=" + i, optUpdate == opt);
            assertEquals(optUpdate.defaultBoolValue, newBool);
            assertEquals(optUpdate.getBoolValue(), newBool);
            assertEquals(optUpdate.defaultIntValue, newInt);
            assertEquals(optUpdate.getIntValue(), newInt);
            assertEquals(optUpdate.lastModVersion, opt.lastModVersion);
        }

        opt.setBoolValue(true);
        opt.setIntValue(7);
        assertTrue("opt again unchanged", opt == SOCGameOption.copyDefaults(opt));
    }

    /**
     * Test {@link SOCGameOption#packOptionsToString(Map, boolean, boolean)}
     * and {@link SOCGameOption#packOptionsToString(Map, boolean, boolean, int)}.
     * @since 2.7.00
     */
    @Test
    public void testPackOptionsToString()
    {
        assertEquals("-", SOCGameOption.packOptionsToString(null, false, false));
        assertEquals("-", SOCGameOption.packOptionsToString(null, false, false, 2000));
        Map<String, SOCGameOption> opts = new HashMap<>();
        assertEquals("-", SOCGameOption.packOptionsToString(opts, false, false));
        assertEquals("-", SOCGameOption.packOptionsToString(opts, false, false, 2000));

        /*
         * only 1 game option in map
         */
        final SOCGameOption optPL = knownOpts.getKnownOption("PL", true);
        optPL.setIntValue(3);
        opts.put("PL", optPL);
        assertEquals("PL=3", SOCGameOption.packOptionsToString(opts, false, false));
        assertEquals("PL=3", SOCGameOption.packOptionsToString(opts, false, false, 2000));

        /*
         * hideEmptyStringOpts: set of opts with one string that isn't special-case scenario ("SC")
         */
        SOCGameOption op;
        op = knownOpts.getKnownOption("RD", true);
        op.setBoolValue(true);
        opts.put("RD", op);
        op = knownOpts.getKnownOption("N7C", true);
        op.setBoolValue(true);
        opts.put("N7C", op);
        op = knownOpts.getKnownOption(SOCGameOptionSet.K__EXT_GAM, true);
        opts.put(SOCGameOptionSet.K__EXT_GAM, op);

        op.setStringValue("");
        assertEquals("N7C=t,PL=3,RD=t,_EXT_GAM=", SOCGameOption.packOptionsToString(opts, false, true));
        assertEquals("N7C=t,PL=3,RD=t", SOCGameOption.packOptionsToString(opts, true, true));

        op.setStringValue("x");
        assertEquals("N7C=t,PL=3,RD=t,_EXT_GAM=x", SOCGameOption.packOptionsToString(opts, false, true));
        assertEquals("N7C=t,PL=3,RD=t,_EXT_GAM=x", SOCGameOption.packOptionsToString(opts, true, true));
        assertEquals("N7C=t,PL=3,RD=t,_EXT_GAM=x", SOCGameOption.packOptionsToString(opts, false, true, 2700));
        assertEquals("N7C=t,PL=3,RD=t,_EXT_GAM=x", SOCGameOption.packOptionsToString(opts, false, true, -2));
        assertEquals("N7C=t,PL=3,RD=t,_EXT_GAM=x", SOCGameOption.packOptionsToString(opts, true, true, 2700));
        assertEquals("N7C=t,PL=3,RD=t,_EXT_GAM=x", SOCGameOption.packOptionsToString(opts, true, true, -2));

        /*
         * VERSION_FOR_LONGER_OPTNAMES (cliVers -3)
         */
        op = new SOCGameOption("_X", 2700, 2700, false, 0, "A short name with an underscore");
        op.setBoolValue(true);
        opts.put("_X", op);
        assertEquals("N7C=t,PL=3,RD=t,_EXT_GAM=x,_X=t", SOCGameOption.packOptionsToString(opts, true, true, 2700));
        assertEquals("N7C=t,PL=3,RD=t,_EXT_GAM=x,_X=t", SOCGameOption.packOptionsToString(opts, true, true, -2));
        assertEquals("N7C=t,PL=3,RD=t", SOCGameOption.packOptionsToString(opts, true, true, -3));

        opts.remove(SOCGameOptionSet.K__EXT_GAM);
        opts.remove("_X");
        opts.remove("N7C");

        /*
         * omit FLAG_INACTIVE_HIDDEN
         */
        op = new SOCGameOption
            ("FO", 2000, 2500, false,
             SOCGameOption.FLAG_INACTIVE_HIDDEN | SOCGameOption.FLAG_DROP_IF_UNUSED, "test option PLAY_FO");
        op.setBoolValue(true);
        opts.put("FO", op);
        assertEquals("PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, -3));
        assertEquals("PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, -2));
        assertEquals("PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, 2700));
        // simulate SGOSet.activate
        op = new SOCGameOption
            ("FO", 2000, 2500, false,
             SOCGameOption.FLAG_ACTIVATED | SOCGameOption.FLAG_DROP_IF_UNUSED, "test option PLAY_FO");
        op.setBoolValue(true);
        opts.put("FO", op);
        assertEquals("FO=t,PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, -3));
        assertEquals("FO=t,PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, -2));
        assertEquals("FO=t,PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, 2700));
        opts.remove("FO");

        /*
         * adjustment (cliVers -2 or < 1.1.13): PL=3 PLB=t -> PL=5
         */
        assertEquals("PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, -3));
        assertEquals("PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, -2));
        assertEquals("PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, 2700));
        assertEquals("PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, 1113));
        assertEquals("PL=3,RD=t", SOCGameOption.packOptionsToString(opts, false, true, 1112));  // no adjustment without PLB

        op = knownOpts.getKnownOption("PLB", true);
        op.setBoolValue(true);
        opts.put("PLB", op);
        assertEquals(3, optPL.getIntValue());
        assertEquals("PL=3,PLB=t,RD=t", SOCGameOption.packOptionsToString(opts, false, true, -3));
        assertEquals("PL=3,PLB=t,RD=t", SOCGameOption.packOptionsToString(opts, false, true, -2));
        assertEquals("PL=3,PLB=t,RD=t", SOCGameOption.packOptionsToString(opts, false, true, 2700));
        assertEquals("PL=3,PLB=t,RD=t", SOCGameOption.packOptionsToString(opts, false, true, 1113));
        assertEquals("PL=5,PLB=t,RD=t", SOCGameOption.packOptionsToString(opts, false, true, 1112));  // adjustment because PLB

        /*
         * unknowns (cliVers vs 2.7.00 VERSION_FOR_UNKNOWN_WITH_DESCRIPTION)
         */
        opts.remove("RD");
        op = knownOpts.getKnownOption("SBL", true);
        op.setBoolValue(true);
        assertEquals(2000, op.getMinVersion(null));
        opts.put("SBL", op);
        op = knownOpts.getKnownOption("UB", true);
        op.setBoolValue(true);
        assertEquals(2700, op.getMinVersion(null));
        opts.put("UB", op);
        assertEquals(2700, SOCGameOption.VERSION_FOR_UNKNOWN_WITH_DESCRIPTION);

        // doesn't check cliVers vs op.minVers, only OTYPE_UNKNOWN
        assertEquals("PL=3,PLB=t,SBL=t,UB=t", SOCGameOption.packOptionsToString(opts, false, true, -3));
        assertEquals("PL=3,PLB=t,SBL=t,UB=t", SOCGameOption.packOptionsToString(opts, false, true, -2));
        assertEquals("PL=3,PLB=t,SBL=t,UB=t", SOCGameOption.packOptionsToString(opts, false, true, 2700));
        assertEquals("PL=3,PLB=t,SBL=t,UB=t", SOCGameOption.packOptionsToString(opts, false, true, 2699));
        assertEquals("PL=3,PLB=t,SBL=t,UB=t", SOCGameOption.packOptionsToString(opts, false, true, 2000));
        assertEquals("PL=3,PLB=t,SBL=t,UB=t", SOCGameOption.packOptionsToString(opts, false, true, 1999));

        // test with unknowns
        opts.put("UB", new SOCGameOption("UB", "unknown UB"));
        assertEquals("PL=3,PLB=t,SBL=t", SOCGameOption.packOptionsToString(opts, false, true, -3));
        assertEquals("PL=3,PLB=t,SBL=t", SOCGameOption.packOptionsToString(opts, false, true, -2));
        assertEquals("PL=3,PLB=t,SBL=t,UB=?", SOCGameOption.packOptionsToString(opts, false, true, 2700));
        assertEquals("PL=3,PLB=t,SBL=t", SOCGameOption.packOptionsToString(opts, false, true, 2699));
        assertEquals("PL=3,PLB=t,SBL=t", SOCGameOption.packOptionsToString(opts, false, true, 2000));
        assertEquals("PL=3,PLB=t,SBL=t", SOCGameOption.packOptionsToString(opts, false, true, 1999));
        opts.put("SBL", new SOCGameOption("SBL", "unknown SBL"));
        assertEquals("PL=3,PLB=t", SOCGameOption.packOptionsToString(opts, false, true, -3));
        assertEquals("PL=3,PLB=t", SOCGameOption.packOptionsToString(opts, false, true, -2));
        assertEquals("PL=3,PLB=t,SBL=?,UB=?", SOCGameOption.packOptionsToString(opts, false, true, 2700));
        assertEquals("PL=3,PLB=t", SOCGameOption.packOptionsToString(opts, false, true, 2699));
        assertEquals("PL=3,PLB=t", SOCGameOption.packOptionsToString(opts, false, true, 2000));
        assertEquals("PL=3,PLB=t", SOCGameOption.packOptionsToString(opts, false, true, 1999));
    }

    /**
     * Test {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet) adjustOptionsToKnown(knownOpts, doServerPreadjust=true, null)}
     * with all 3 * 3 possible combinations of {@code "VP"} at server's known opts and client:
     * True ({@code t13}), false ({@code f13}), not in set.
     * Also tests with various scenarios having same, lower, or higher VP to win than the default or client-requested VP.
     * Tests without and with {@link SOCGameOption} {@code "_VP_ALL"}.
     *<P>
     * Before v2.5.00 this test was {@code testServerRemovesFalseVP()}.
     */
    @Test
    public void testServerAdjustNewGameOptsVP()
    {
        testOne_ServerAdjustNewGameOptsVP(12, 13, null, 0, false, null);
        testOne_ServerAdjustNewGameOptsVP(14, 13, null, 0, false, null);
        testOne_ServerAdjustNewGameOptsVP(12, 13, null, 0, true, null);
        testOne_ServerAdjustNewGameOptsVP(14, 13, null, 0, true, null);

        final SOCGameOptionSet srvKnownOpts = SOCGameOptionSet.getAllKnownOptions();
        assertNotNull(srvKnownOpts);
        assertTrue(srvKnownOpts.containsKey("VP"));
        assertTrue(srvKnownOpts.containsKey("_VP_ALL"));
        assertTrue(srvKnownOpts.containsKey("SC"));

        // meta-test: not a known scenario
        try {
            testOne_ServerAdjustNewGameOptsVP(12, 13, "XYZ", 0, false, srvKnownOpts);
            fail("testServerAdjustNewGameOptsVP: should have thrown; scenario XYZ unknown");
        }
        catch(NoSuchElementException e) {}

        // Test vs scenarios:
        // Each test's comment lists VP amounts ordered from highest to lowest
        for (int b = 0; b <= 1; ++b)
        {
            final boolean with_VP_ALL = (b != 0);

            testOne_ServerAdjustNewGameOptsVP
                (12, 13, "SC_WOND", 10, with_VP_ALL, srvKnownOpts);  // [default, client, scen == 10]
            testOne_ServerAdjustNewGameOptsVP
                (13, 14, "SC_FOG", 12, with_VP_ALL, srvKnownOpts);   // [default, client, scen > 10]
            testOne_ServerAdjustNewGameOptsVP
                (12, 13, "SC_FOG", 12, with_VP_ALL, srvKnownOpts);   // [default, client == scen]
            testOne_ServerAdjustNewGameOptsVP
                (11, 13, "SC_FOG", 12, with_VP_ALL, srvKnownOpts);   // [default, scen, client]
            testOne_ServerAdjustNewGameOptsVP
                (13, 13, "SC_FTRI", 13, with_VP_ALL, srvKnownOpts);  // [default == client == scen]
            testOne_ServerAdjustNewGameOptsVP
                (12, 13, "SC_CLVI", 14, with_VP_ALL, srvKnownOpts);  // [scen, default, client]
            testOne_ServerAdjustNewGameOptsVP
                (13, 12, "SC_CLVI", 14, with_VP_ALL, srvKnownOpts);  // [scen, client, default]
            testOne_ServerAdjustNewGameOptsVP
                (14, 13, "SC_FOG", 12, with_VP_ALL, srvKnownOpts);   // [client, default, scen]
            testOne_ServerAdjustNewGameOptsVP
                (14, 11, "SC_FOG", 12, with_VP_ALL, srvKnownOpts);   // [client, scen, default]
        }
    }

    /**
     * Test one combination of client-server game opt parameters for {@link #testServerAdjustNewGameOptsVP()}.
     * If {@code srvKnownOpts} is {@code null}, tests include gameopt {@code "VP"} unknown at server.
     * @param clientVP  Requested VP from client, like 12
     * @param srvDefaultVP  Server's default VP, like 13
     * @param clientScen  Client's requested scenario, or {@code null}; must supply {@code srvKnownOpts} if used
     * @param clientScenExpectedVP  Expected VP as defined in scenario, as an extra check, or 0 if {@code clientScen} is {@code null}
     * @param with_VP_ALL  If true, set gameopt {@code "_VP_ALL"} true at server;
     *     when server default VP is set, should override any scenario's VP amount
     * @param srvKnownOpts  Known options for testing, or {@code null} to make a new set here containing only {@code "VP"}
     * @throws IllegalArgumentException if {@code srvKnownOpts} is {@code null}, but {@code clientScen != null}
     * @throws NoSuchElementException if {@code clientScen} not found by {@link SOCScenario#getScenario(String)}
     */
    private void testOne_ServerAdjustNewGameOptsVP
        (final int clientVP, final int srvDefaultVP, final String clientScen, final int clientScenExpectedVP,
         final boolean with_VP_ALL, SOCGameOptionSet srvKnownOpts)
        throws IllegalArgumentException, NoSuchElementException
    {
        final boolean testFewKnownOpts = (srvKnownOpts == null);
        final SOCGameOption cliOptSC;
        int clientScenVP = 0;
        if (testFewKnownOpts)
        {
            srvKnownOpts = new SOCGameOptionSet();
            if (clientScen != null)
                throw new IllegalArgumentException("for clientScen, must supply srvKnownOpts");
            cliOptSC = null;
            if (with_VP_ALL)
            {
                SOCGameOption srv_VP_ALL = knownOpts.getKnownOption("_VP_ALL", true);
                srv_VP_ALL.setBoolValue(true);
                srvKnownOpts.add(srv_VP_ALL);
            }
        } else {
            srvKnownOpts.get("_VP_ALL").setBoolValue(with_VP_ALL);

            final SOCGameOption srvSC = srvKnownOpts.get("SC");
            assertNotNull(srvSC);
            if (clientScen != null)
            {
                SOCScenario sc = SOCScenario.getScenario(clientScen);
                if (sc == null)
                    throw new NoSuchElementException("clientScen " + clientScen);
                final Map<String, SOCGameOption> scOpts = SOCGameOption.parseOptionsToMap(sc.scOpts, knownOpts);
                final SOCGameOption scOptVP = scOpts.get("VP");
                if ((scOptVP != null) && scOptVP.getBoolValue())
                    clientScenVP = scOptVP.getIntValue();
                assertEquals("scen " + clientScen + " VP", clientScenExpectedVP, clientScenVP);

                cliOptSC = knownOpts.getKnownOption("SC", true);
                cliOptSC.setStringValue(clientScen);
            } else {
                cliOptSC = null;
            }
        }
        final String testDesc = "testServerAdjustNewGameOptsVP(cliVP=" + clientVP + ", srvVP=" + srvDefaultVP
            + (testFewKnownOpts ? ", testFewKnownOpts=true" : ", cliSC=" + clientScen)
            + ", with_VP_ALL=" + with_VP_ALL + ")";

        final SOCGameOptionSet cliNewGameOpts = new SOCGameOptionSet();
        final SOCGameOption cliOptVP = knownOpts.getKnownOption("VP", true);
        cliOptVP.setIntValue(clientVP);
        cliOptVP.setBoolValue(true);
        // cliOptVP will be added and removed from cliNewGameOpts during test cases below.
        if (cliOptSC != null)
            cliNewGameOpts.add(cliOptSC);

        Map<String, String> optProblems;

        // 3 x 3 test matrix: VP [missing, false, true] for "client" opt, for "server" known opt.
        // 2 x 3 unless testFewKnownOpts: Otherwise, don't test VP unknown at server.

        // - VP=t12 from "client": shouldn't change or remove

        // when server default false
        {
            final SOCGameOption srvVP;
            if (testFewKnownOpts)
            {
                srvVP = knownOpts.getKnownOption("VP", true);
                srvKnownOpts.put(srvVP);
            } else {
                srvVP = srvKnownOpts.get("VP");
                assertNotNull(srvVP);
            }
            srvVP.setIntValue(srvDefaultVP);
            srvVP.setBoolValue(false);

            cliNewGameOpts.put(cliOptVP);
            optProblems = cliNewGameOpts.adjustOptionsToKnown(srvKnownOpts, true, null);
            assertNull(testDesc, optProblems);
            SOCGameOption opt = cliNewGameOpts.get("VP");
            assertNotNull(testDesc, opt);
            assertTrue(testDesc, opt == cliOptVP);
            assertTrue(testDesc, opt.getBoolValue());
            assertEquals(testDesc, clientVP, opt.getIntValue());
            if (clientScen != null)
            {
                opt = cliNewGameOpts.get("SC");
                assertNotNull(testDesc, opt);
                assertEquals(testDesc, clientScen, opt.getStringValue());
            }
            assertFalse(testDesc + ": no _VP_ALL when client requests VP=t##", cliNewGameOpts.containsKey("_VP_ALL"));
        }

        // when server default true
        {
            final SOCGameOption srvVP = srvKnownOpts.get("VP");
            assertNotNull(srvVP);
            srvVP.setBoolValue(true);
            assertEquals(srvDefaultVP, srvVP.getIntValue());

            assertTrue(cliNewGameOpts.get("VP") == cliOptVP);
            optProblems = cliNewGameOpts.adjustOptionsToKnown(srvKnownOpts, true, null);
            assertNull(testDesc, optProblems);
            SOCGameOption opt = cliNewGameOpts.get("VP");
            assertNotNull(testDesc, opt);
            assertTrue(testDesc, opt == cliOptVP);
            assertTrue(testDesc, opt.getBoolValue());
            assertEquals(testDesc, clientVP, opt.getIntValue());
            if (clientScen != null)
            {
                opt = cliNewGameOpts.get("SC");
                assertNotNull(testDesc, opt);
                assertEquals(testDesc, clientScen, opt.getStringValue());
            }
            assertFalse(testDesc + ": no _VP_ALL when client requests VP=t##", cliNewGameOpts.containsKey("_VP_ALL"));
        }

        // should reject if VP not known at "server"; cli would know not to send "VP"
        if (testFewKnownOpts)
        {
            srvKnownOpts.remove("VP");
            assertTrue(cliNewGameOpts.get("VP") == cliOptVP);

            optProblems = cliNewGameOpts.adjustOptionsToKnown(srvKnownOpts, true, null);
            assertNotNull(testDesc, optProblems);
            assertEquals(testDesc, 1, optProblems.size());
            assertTrue(testDesc, optProblems.containsKey("VP"));
            assertTrue(testDesc, optProblems.get("VP").equals("unknown"));
        }

        // - VP=f12 from "client":

        cliOptVP.setBoolValue(false);
        cliOptVP.setIntValue(clientVP);

        // should update VP when false and is present at "server" with a true default, unless scenario has VP
        {
            final int expectedVP = ((clientScenVP > srvDefaultVP) && ! with_VP_ALL) ? clientScenVP : srvDefaultVP;
            final SOCGameOption srvVP;
            if (testFewKnownOpts)
            {
                srvVP = knownOpts.getKnownOption("VP", true);
                srvKnownOpts.put(srvVP);
            } else {
                srvVP = srvKnownOpts.get("VP");
                assertNotNull(srvVP);
            }
            srvVP.setIntValue(srvDefaultVP);
            srvVP.setBoolValue(true);

            cliNewGameOpts.put(cliOptVP);
            optProblems = cliNewGameOpts.adjustOptionsToKnown(srvKnownOpts, true, null);
            assertNull(testDesc, optProblems);
            SOCGameOption opt = cliNewGameOpts.get("VP");
            assertNotNull(testDesc, opt);
            assertTrue(testDesc, opt.getBoolValue());
            assertEquals(testDesc, expectedVP, opt.getIntValue());
            if (clientScenVP == 0)
            {
                assertTrue(testDesc + ": object fields updated, not cloned", cliOptVP == opt);
            } else {
                opt = cliNewGameOpts.get("SC");
                assertNotNull(testDesc, opt);
                assertEquals(testDesc, clientScen, opt.getStringValue());
            }
            assertEquals(testDesc, with_VP_ALL && clientScen != null, cliNewGameOpts.containsKey("_VP_ALL"));
        }

        // should remove VP when false and server's default is false, unless scenario VP
        {
            final SOCGameOption srvVP = srvKnownOpts.get("VP");
            assertNotNull(srvVP);
            assertEquals(srvDefaultVP, srvVP.getIntValue());
            srvVP.setBoolValue(false);

            if(cliOptVP != cliNewGameOpts.get("VP"))
                cliNewGameOpts.put(cliOptVP);
            cliOptVP.setBoolValue(false);
            cliOptVP.setIntValue(clientVP);
            srvKnownOpts.get("VP").setBoolValue(false);
            optProblems = cliNewGameOpts.adjustOptionsToKnown(srvKnownOpts, true, null);
            assertNull(testDesc, optProblems);
            if (clientScenVP == 0)
            {
                assertFalse(testDesc + ": cli opts shouldn't have VP", cliNewGameOpts.containsKey("VP"));
            } else {
                SOCGameOption opt = cliNewGameOpts.get("VP");
                assertNotNull(testDesc + ": cli opts should have VP", opt);
                assertTrue(testDesc, opt.getBoolValue());
                assertEquals(testDesc, clientScenVP, opt.getIntValue());
                opt = cliNewGameOpts.get("SC");
                assertNotNull(testDesc, opt);
                assertEquals(testDesc, clientScen, opt.getStringValue());
            }
            assertFalse(testDesc, cliNewGameOpts.containsKey("_VP_ALL"));
        }

        // should reject if VP not known at "server"
        if (testFewKnownOpts)
        {
            cliNewGameOpts.put(cliOptVP);
            srvKnownOpts.remove("VP");
            optProblems = cliNewGameOpts.adjustOptionsToKnown(srvKnownOpts, true, null);
            assertNotNull(testDesc, optProblems);
            assertEquals(testDesc, 1, optProblems.size());
            assertTrue(testDesc, optProblems.containsKey("VP"));
            assertTrue(testDesc, optProblems.get("VP").equals("unknown"));
        }

        // - VP not sent from "client":

        // when VP not sent in opts, but true in knownOpts or scenario, include a clone of knownOpts' VP
        {
            final int expectedVP = ((clientScenVP > srvDefaultVP) && ! with_VP_ALL) ? clientScenVP : srvDefaultVP;
            final SOCGameOption srvVP;
            if (testFewKnownOpts)
            {
                srvVP = knownOpts.getKnownOption("VP", true);
                srvKnownOpts.put(srvVP);
            } else {
                srvVP = srvKnownOpts.get("VP");
                assertNotNull(srvVP);
            }
            srvVP.setIntValue(srvDefaultVP);
            srvVP.setBoolValue(true);

            cliNewGameOpts.remove("VP");
            optProblems = cliNewGameOpts.adjustOptionsToKnown(srvKnownOpts, true, null);
            assertNull(testDesc, optProblems);
            SOCGameOption opt = cliNewGameOpts.get("VP");
            assertNotNull(testDesc, opt);
            assertTrue(testDesc, opt.getBoolValue());
            assertEquals(testDesc, expectedVP, opt.getIntValue());
            assertTrue(testDesc + ": cloned, not same reference as srvVP", opt != srvVP);
            if (clientScen != null)
            {
                opt = cliNewGameOpts.get("SC");
                assertNotNull(testDesc, opt);
                assertEquals(testDesc, clientScen, opt.getStringValue());
                assertEquals(testDesc, with_VP_ALL, cliNewGameOpts.containsKey("_VP_ALL"));
            } else {
                assertFalse(testDesc, cliNewGameOpts.containsKey("_VP_ALL"));
            }
        }

        // when VP not sent in opts, and false in knownOpts, don't add it unless in scenario
        {
            final SOCGameOption srvVP = srvKnownOpts.get("VP");
            assertNotNull(srvVP);
            srvVP.setBoolValue(false);
            assertEquals(srvDefaultVP, srvVP.getIntValue());

            cliNewGameOpts.remove("VP");
            optProblems = cliNewGameOpts.adjustOptionsToKnown(srvKnownOpts, true, null);
            if (clientScenVP == 0)
            {
                assertNull(testDesc, optProblems);
                assertFalse(testDesc, cliNewGameOpts.containsKey("VP"));
            } else {
                SOCGameOption opt = cliNewGameOpts.get("VP");
                assertNotNull(testDesc, opt);
                assertTrue(testDesc, opt.getBoolValue());
                assertEquals(testDesc, clientScenVP, opt.getIntValue());
                opt = cliNewGameOpts.get("SC");
                assertNotNull(testDesc, opt);
                assertEquals(testDesc, clientScen, opt.getStringValue());
            }
            assertFalse(testDesc, cliNewGameOpts.containsKey("_VP_ALL"));
        }

        // when VP not sent in opts, and not in knownOpts, don't add it unless in scenario
        if (testFewKnownOpts)
        {
            srvKnownOpts.remove("VP");
            cliNewGameOpts.remove("VP");
            optProblems = cliNewGameOpts.adjustOptionsToKnown(srvKnownOpts, true, null);
            if (clientScenVP == 0)
            {
                assertNull(testDesc, optProblems);
                assertFalse(testDesc, cliNewGameOpts.containsKey("VP"));
            } else {
                SOCGameOption opt = cliNewGameOpts.get("VP");
                assertNotNull(testDesc, opt);
                assertTrue(testDesc, opt.getBoolValue());
                assertEquals(testDesc, clientScenVP, opt.getIntValue());
                opt = cliNewGameOpts.get("SC");
                assertNotNull(testDesc, opt);
                assertEquals(testDesc, clientScen, opt.getStringValue());
            }
            assertFalse(testDesc, cliNewGameOpts.containsKey("_VP_ALL"));
        }
    }

    /**
     * Test adding a new known option, updating it, removing it:
     * {@link SOCGameOptionSet#addKnownOption(SOCGameOption)}.
     */
    @Test
    public void testAddKnownOption()
    {
        final SOCGameOptionSet knowns = SOCGameOptionSet.getAllKnownOptions();

        // add known opt
        assertNull(knowns.getKnownOption("_TESTF", false));
        final SOCGameOption newKnown = new SOCGameOption
            ("_TESTF", 2000, 2000, false, 0, "For unit test");
        boolean hadNoOld = knowns.addKnownOption(newKnown);
        assertTrue(hadNoOld);

        // getOption without clone should be same object
        SOCGameOption opt = knowns.getKnownOption("_TESTF", false);
        assertTrue(newKnown == opt);

        // getOption with clone should be different object with same field values
        opt = knowns.getKnownOption("_TESTF", true);
        assertTrue(newKnown != opt);
        assertNotNull(opt);
        assertEquals("_TESTF", opt.key);
        assertEquals(SOCGameOption.OTYPE_BOOL, opt.optType);
        assertEquals(2000, opt.minVersion);
        assertEquals(2000, opt.lastModVersion);

        // any ChangeListener ref should be copied by addKnownOption
        final SOCGameOption.ChangeListener cl = new SOCGameOption.ChangeListener()
        {
            public void valueChanged
                (SOCGameOption op, Object oldValue, Object newValue,
                 SOCGameOptionSet curr, SOCGameOptionSet known)
            {}
        };
        newKnown.addChangeListener(cl);
        assertTrue(newKnown.getChangeListener() == cl);

        final SOCGameOption newKnown2 = new SOCGameOption
            ("_TESTF", 2000, 2000, false, 0, "Changed for unit test");
        hadNoOld = knowns.addKnownOption(newKnown2);
        assertFalse(hadNoOld);

        opt = knowns.getKnownOption("_TESTF", false);
        assertTrue(newKnown2 == opt);
        assertTrue("SGOSet.addKnownOption should copy ChangeListener ref", newKnown2.getChangeListener() == cl);

        // cleanup/remove known opt, by adding unknown opt
        hadNoOld = knowns.addKnownOption(new SOCGameOption("_TESTF", null));
        assertFalse(hadNoOld);
        assertNull(knowns.getKnownOption("_TESTF", false));
    }

    /**
     * Test server-side behavior of {@link SOCGameOption#FLAG_INTERNAL_GAME_PROPERTY}.
     * Currently can't test client-side because it's part of NewGameOptionsFrame GUI code.
     */
    @Test
    public void testFlagInternalGameProperty()
    {
        final SOCGameOptionSet knowns = SOCGameOptionSet.getAllKnownOptions();

        // setup
        final SOCGameOption newKnown = new SOCGameOption
            ("_TESTF", 2000, 2000, 0, 0, 0xFFFF, SOCGameOption.FLAG_INTERNAL_GAME_PROPERTY,
             "For unit test");
        assertNull(knowns.getKnownOption("_TESTF", false));
        knowns.addKnownOption(newKnown);
        assertNotNull(knowns.getKnownOption("_TESTF", false));

        // should remove internal option if sent from "client" to "server"
        final SOCGameOptionSet newGameOpts = new SOCGameOptionSet();
        final SOCGameOption opt = knowns.getKnownOption("_TESTF", true);
        opt.setIntValue(0x2211);
        newGameOpts.put(opt);
        newGameOpts.adjustOptionsToKnown(knowns, true, null);
        assertNull(newGameOpts.get("_TESTF"));

        // cleanup
        knowns.addKnownOption(new SOCGameOption("_TESTF", null));
        assertNull(knowns.getKnownOption("_TESTF", false));
    }

    /**
     * Inactive/activated gameopts: Test {@link SOCGameOptionSet#activate(String)},
     * {@link SOCGameOptionSet#optionsWithFlag(int, int)}.
     * Test {@link SOCGameOptionSet#optionsForVersion(int)} and
     * {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet) gameOpts.adjustOptionsToKnown(knownOpts, doServerPreadjust=true, limitedCliFeats)}
     * checks for {@link SOCGameOption#FLAG_INACTIVE_HIDDEN}. Uses game options
     * {@link SOCGameOptionSet#K_PLAY_FO "PLAY_FO"}, {@link SOCGameOptionSet#K_PLAY_VPO "PLAY_VPO"}.
     * @since 2.5.00
     */
    @Test
    public void testFlagInactiveActivate()
    {
        final int TEST_CLI_VERSION = 2100;
        final SOCGameOptionSet knowns = SOCGameOptionSet.getAllKnownOptions();

        // setup:
        // - an option changed after client version 2100:
        final SOCGameOption optPlayVPO = knowns.getKnownOption("PLAY_VPO", false);
        assertNotNull(optPlayVPO);
        assertTrue(optPlayVPO.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertFalse(optPlayVPO.hasFlag(SOCGameOption.FLAG_ACTIVATED));
        assertEquals("minVersion 2000", 2000, optPlayVPO.minVersion);
        assertEquals
            ("netcode version assumptions OK", optPlayVPO.minVersion, SOCDevCardConstants.VERSION_FOR_RENUMBERED_TYPES);
        assertTrue("changed after client version 2100", optPlayVPO.lastModVersion > TEST_CLI_VERSION);
        // - an option unchanged at client version 2100:
        final SOCGameOption newKnown2 = new SOCGameOption
            ("_TESTACT", 2000, 2000, 0, 0, 0xFFFF, SOCGameOption.FLAG_INACTIVE_HIDDEN,
             "For unit test");
        assertNull(knowns.getKnownOption("_TESTACT", false));
        knowns.addKnownOption(newKnown2);
        assertNotNull(knowns.getKnownOption("_TESTACT", false));
        assertTrue(newKnown2.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertFalse(newKnown2.hasFlag(SOCGameOption.FLAG_ACTIVATED));
        // - make sure PLAY_FO also has proper minVersion
        SOCGameOption optFO = knowns.getKnownOption("PLAY_FO", false);
        assertNotNull(optFO);
        assertTrue(optFO.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertFalse(optFO.hasFlag(SOCGameOption.FLAG_ACTIVATED));
        assertEquals("minVersion 2000", 2000, optFO.minVersion);

        // optionsWithFlag ignores inactives unless asked for that flag
        {
            SOCGameOptionSet inacts = knowns.optionsWithFlag
                (SOCGameOption.FLAG_INACTIVE_HIDDEN, 0);
            assertNotNull(inacts);
            assertTrue("inactive gameopts should include _TESTACT", inacts.containsKey("_TESTACT"));
            assertTrue("inactive gameopts should include PLAY_FO", inacts.containsKey("PLAY_FO"));
        }

        SOCGameOptionSet activatedOpts = knowns.optionsWithFlag(SOCGameOption.FLAG_ACTIVATED, 2500);
        assertNull("not activated yet", activatedOpts);

        // testing the actual activation feature:

        // not in known options when inactive, even if changed after client version
        {
            List<SOCGameOption> opts = knowns.optionsNewerThanVersion(TEST_CLI_VERSION, false, true);
            boolean found = false, found2 = false;
            if (opts != null)
            {
                for (SOCGameOption opt : opts)
                {
                    String okey = opt.key;
                    if (okey.equals("PLAY_VPO"))
                        found = true;
                    else if (okey.equals("_TESTACT"))
                        found2 = true;
                }
            }
            assertFalse("PLAY_VPO not in optionsNewerThanVersion when inactive", found);
            assertFalse("_TESTACT not in optionsNewerThanVersion when inactive", found2);

            opts = knowns.optionsForVersion(TEST_CLI_VERSION);
            found = false;
            found2 = false;
            assertNotNull(opts);
            for (SOCGameOption opt : opts)
            {
                String okey = opt.key;
                if (okey.equals("PLAY_VPO"))
                    found = true;
                else if (okey.equals("_TESTACT"))
                    found2 = true;
            }
            assertFalse("PLAY_VPO not in optionsForVersion when inactive", found);
            assertFalse("_TESTACT not in optionsForVersion when inactive", found2);
        }

        SOCGameOptionSet newGameReqOpts = new SOCGameOptionSet();
        newGameReqOpts.put(optPlayVPO);
        Map<String, String> optProblems = newGameReqOpts.adjustOptionsToKnown(knowns, true, null);
        assertNotNull(optProblems);
        assertTrue(optProblems.containsKey("PLAY_VPO"));
        assertTrue(optProblems.get("PLAY_VPO").contains("inactive"));

        knowns.activate("PLAY_VPO");
        knowns.activate("_TESTACT");

        SOCGameOption activated = knowns.getKnownOption("PLAY_VPO", false);  // non-cloned reference
        assertFalse(activated.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertTrue(activated.hasFlag(SOCGameOption.FLAG_ACTIVATED));

        activated = knowns.getKnownOption("PLAY_VPO", true);  // clone should copy flags
        assertFalse(activated.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertTrue(activated.hasFlag(SOCGameOption.FLAG_ACTIVATED));

        SOCGameOption activated2 = knowns.getKnownOption("_TESTACT", true);
        assertFalse(activated2.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertTrue(activated2.hasFlag(SOCGameOption.FLAG_ACTIVATED));

        activatedOpts = knowns.optionsWithFlag(SOCGameOption.FLAG_ACTIVATED, 2500);
        assertNotNull(activatedOpts);
        assertEquals(2, activatedOpts.size());
        assertEquals(activated, activatedOpts.get("PLAY_VPO"));
        assertEquals(activated2, activatedOpts.get("_TESTACT"));

        activatedOpts = knowns.optionsWithFlag(SOCGameOption.FLAG_ACTIVATED, 1999);  // older than _TESTACT minVersion
        assertNull(activatedOpts);

        // is in known options when active, even if not changed after client version
        {
            List<SOCGameOption> opts = knowns.optionsNewerThanVersion(TEST_CLI_VERSION, false, true);
            boolean found = false, found2 = false;
            if (opts != null)
            {
                for (SOCGameOption opt : opts)
                {
                    String okey = opt.key;
                    if (okey.equals("PLAY_VPO"))
                    {
                        if (found)
                            fail("optionsNewerThanVersion: found twice in list: PLAY_VPO");
                        found = true;
                    }
                    else if (okey.equals("_TESTACT"))
                    {
                        if (found2)
                            fail("optionsNewerThanVersion: found twice in list: _TESTACT");
                        found2 = true;
                    }
                }
            }
            assertTrue("PLAY_VPO in optionsNewerThanVersion after activated", found);
            assertTrue("_TESTACT in optionsNewerThanVersion after activated", found2);

            opts = knowns.optionsForVersion(TEST_CLI_VERSION);
            assertNotNull(opts);
            found = false;
            found2 = false;
            for (SOCGameOption opt : opts)
            {
                String okey = opt.key;
                if (okey.equals("PLAY_VPO"))
                {
                    if (found)
                        fail("optionsForVersion: found twice in list: PLAY_VPO");
                    found = true;
                }
                else if (okey.equals("_TESTACT"))
                {
                    if (found2)
                        fail("optionsForVersion: found twice in list: _TESTACT");
                    found2 = true;
                }
            }
            assertTrue("PLAY_VPO in optionsForVersion after activated", found);
            assertTrue("_TESTACT in optionsForVersion after activated", found2);
        }

        newGameReqOpts.put(activated);
        optProblems = newGameReqOpts.adjustOptionsToKnown(knowns, true, null);
        assertNull(optProblems);

        // cleanup
        knowns.addKnownOption(new SOCGameOption("_TESTACT", null));
        assertNull(knowns.getKnownOption("_TESTACT", false));
    }

    /**
     * Test {@link SOCGameOptionSet#activate(String)} when known option not found.
     * @since 2.5.00
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFlagInactiveActivate_notFound()
    {
        assertNull(knownOpts.getKnownOption("_NONEXISTENT", false));
        knownOpts.activate("_NONEXISTENT");
    }

    /**
     * Test {@link SOCGameOptionSet#activate(String)} when known option not inactive.
     * @since 2.5.00
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFlagInactiveActivate_notInactive()
    {
        knownOpts.activate("PL");
    }

    /**
     * Test that gameopt constructors can't be called with both
     * {@link SOCGameOption#FLAG_ACTIVATED} and {@link SOCGameOption#FLAG_INACTIVE_HIDDEN} set at same time.
     * @since 2.5.00
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFlagInactiveActivate_constructor()
    {
        final SOCGameOption opt = new SOCGameOption
            ("_TESTIAF", 2000, 2500, false, SOCGameOption.FLAG_ACTIVATED | SOCGameOption.FLAG_INACTIVE_HIDDEN,
             "test active and inactive at same time");
        // should throw IllegalArgumentException; next statement is there only to avoid compiler warnings
        assertNotNull(opt);
    }

    /**
     * Test {@link SOCVersionedItem#itemsMinimumVersion(Map, boolean, Map)}.
     * @since 2.1.00
     */
    @Test
    public void testItemsMinimumVersion()
    {
        final SOCGameOptionSet knowns = SOCGameOptionSet.getAllKnownOptions();

        assertEquals(-1, SOCVersionedItem.itemsMinimumVersion(null, false, null));

        assertEquals(-1, SOCVersionedItem.itemsMinimumVersion(new HashMap<String, SOCGameOption>(), false, null));

        // Min vers is 2700 when gameopt UB is set:

        {
            Map<String, SOCGameOption> optsUB = SOCGameOption.parseOptionsToMap("UB=t", knowns);
            assertEquals(2700, SOCVersionedItem.itemsMinimumVersion(optsUB, false, null));

            Map<String, Integer> optsMins = new HashMap<>();
            assertEquals(2700, SOCVersionedItem.itemsMinimumVersion(optsUB, false, optsMins));
            assertEquals(1, optsMins.size());
            Integer minUB = optsMins.get("UB");
            assertNotNull(minUB);
            assertEquals(2700, minUB.intValue());

            // now optsMins isn't empty, but itemsMinimumVersion requires its incoming itemsMins to be empty
            try
            {
                SOCVersionedItem.itemsMinimumVersion(optsUB, false, optsMins);
                fail("should reject non-empty itemsMins arg");
            } catch (IllegalArgumentException e) {}
        }

        // Min vers is 2700 when gameopt UBL and UB are both set,
        // but not when only UBL is set (it has FLAG_DROP_IF_PARENT_UNUSED):

        {
            Map<String, SOCGameOption> optsUBL = SOCGameOption.parseOptionsToMap("UBL=t3", knowns);
            assertEquals(-1, SOCVersionedItem.itemsMinimumVersion(optsUBL, false, null));

            Map<String, Integer> optsMins = new HashMap<>();
            assertEquals(-1, SOCVersionedItem.itemsMinimumVersion(optsUBL, false, optsMins));
            assertTrue(optsMins.isEmpty());

            // now add UB=f to opts
            SOCGameOption optUB = knowns.getKnownOption("UB", true);
            optsUBL.put("UB", optUB);
            optUB.setBoolValue(false);
            assertEquals(-1, SOCVersionedItem.itemsMinimumVersion(optsUBL, false, optsMins));
            assertTrue(optsMins.isEmpty());

            // now set UB=t
            optUB.setBoolValue(true);
            assertEquals(2700, SOCVersionedItem.itemsMinimumVersion(optsUBL, false, optsMins));
            assertEquals(2, optsMins.size());
            Integer minVers = optsMins.get("UB");
            assertNotNull(minVers);
            assertEquals(2700, minVers.intValue());
            minVers = optsMins.get("UBL");
            assertNotNull(minVers);
            assertEquals(2700, minVers.intValue());
        }

        // TODO expand beyond those simple tests
    }

    /**
     * Test {@link SOCGameOptionSet#optionsNewerThanVersion(int, boolean, boolean)}.
     * Currently client-side functions only: checkValues=false, trimEnums=false.
     * Also tests {@link SOCGameOption#FLAG_3RD_PARTY} and its interaction at client
     * with {@code optionsNewerThanVersion(..)}.
     * @since 2.5.00
     */
    @Test
    public void testOptionsNewerThanVersion()
    {
        final int currVers = Version.versionNumber();
        final SOCGameOptionSet knowns = SOCGameOptionSet.getAllKnownOptions();

        // client-side tests: checkValues=false, trimEnums=false

        // Nothing newer than current version
        for (int vers = currVers; vers <= (currVers + 1); ++vers)
        {
            List<SOCGameOption> opts = knowns.optionsNewerThanVersion(currVers, false, false);
            if (opts != null)
            {
                // filter out any activated options (FLAG_3RD_PARTY if present, PLAY_VPO from other unit test)
                // which are added regardless of version

                ListIterator<SOCGameOption> iter = opts.listIterator();
                while (iter.hasNext())
                {
                    SOCGameOption opt = iter.next();
                    if (opt.hasFlag(SOCGameOption.FLAG_ACTIVATED) || opt.hasFlag(SOCGameOption.FLAG_3RD_PARTY))
                        iter.remove();
                }
                if (opts.isEmpty())
                    opts = null;
            }

            assertNull(opts);
        }

        // checks for an older server version:

        final int OLDER_VERSION = 1201;  // 1.2.01, to return a decently-sized list

        // add a Known Option unchanged at server version 1201:
        // we'll soon change it to be 3rd-party and look for it.
        SOCGameOption opt3PKnown = new SOCGameOption
            ("T3P", -1, 1107, 0, 0, 0xFFFF, 0, "For unit test");
        assertFalse(opt3PKnown.hasFlag(SOCGameOption.FLAG_3RD_PARTY));
        assertNull(knowns.getKnownOption("T3P", false));
        knowns.addKnownOption(opt3PKnown);
        assertNotNull(knowns.getKnownOption("T3P", false));

        // for purposes of this test, if this copy of JSettlers has been modified to add 3rd-party gameopts,
        // remove those gameopts
        Iterator<SOCGameOption> opti = knowns.iterator();
        while (opti.hasNext())
            if (opti.next().hasFlag(SOCGameOption.FLAG_3RD_PARTY))
                opti.remove();

        // also add a 3P Known Option that's inactive, so it should be ignored client-side and server-side
        SOCGameOption new3PInact = new SOCGameOption
            ("T3I", -1, 1107, 0, 0, 0xFFFF,
            SOCGameOption.FLAG_3RD_PARTY | SOCGameOption.FLAG_INACTIVE_HIDDEN,
             "For unit test");
        assertTrue(new3PInact.hasFlag(SOCGameOption.FLAG_3RD_PARTY));
        assertTrue(new3PInact.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertNull(knowns.getKnownOption("T3I", false));
        knowns.addKnownOption(new3PInact);
        assertNotNull(knowns.getKnownOption("T3I", false));

        assertNull("no active 3P gameopts yet", knowns.optionsWithFlag(SOCGameOption.FLAG_3RD_PARTY, 0));
        {
            SOCGameOptionSet inacts3p = knowns.optionsWithFlag
                (SOCGameOption.FLAG_3RD_PARTY | SOCGameOption.FLAG_INACTIVE_HIDDEN, 0);
            assertNotNull(inacts3p);
            assertTrue("inactive gameopts should include T3I", inacts3p.containsKey("T3I"));
        }

        // build a reasonable expected list
        Map<String, SOCGameOption> builtMap = new HashMap<>();
        for (SOCGameOption opt : SOCGameOptionSet.getAllKnownOptions())
        {
            if (((opt.lastModVersion > OLDER_VERSION) || opt.hasFlag(SOCGameOption.FLAG_ACTIVATED))
                && ! (opt.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN) || opt.hasFlag(SOCGameOption.FLAG_3RD_PARTY)))
                builtMap.put(opt.key, opt);
        }
        assertTrue("contains SC", builtMap.containsKey("SC"));    // added at v2000
        assertTrue("contains PLP", builtMap.containsKey("PLP"));  // added at v2300
        assertFalse("shouldn't contain T3P yet", builtMap.containsKey("T3P"));  // not recent, not 3rd-party yet

        List<SOCGameOption> newerOpts = knowns.optionsNewerThanVersion(OLDER_VERSION, false, false);
        assertNotNull(newerOpts);
        Map<String, SOCGameOption> testMap = new HashMap<>();
        for (SOCGameOption opt : newerOpts)
            testMap.put(opt.key, opt);
        assertEquals("expected key count", builtMap.size(), testMap.size());
        for (String optKey : builtMap.keySet())
            if (! testMap.containsKey(optKey))
                fail("missing expected key: " + optKey);

        // client-side test FLAG_3RD_PARTY
        opt3PKnown = new SOCGameOption
            ("T3P", -1, 1107, 0, 0, 0xFFFF, SOCGameOption.FLAG_3RD_PARTY, "For unit test");
        opt3PKnown.setClientFeature("com.example.js.feat.test3p");
        assertTrue(opt3PKnown.hasFlag(SOCGameOption.FLAG_3RD_PARTY));
        knowns.addKnownOption(opt3PKnown);
        SOCGameOption knOpt = knowns.getKnownOption("T3P", false);
        assertNotNull(knOpt);
        assertTrue(knOpt.hasFlag(SOCGameOption.FLAG_3RD_PARTY));
        SOCGameOptionSet opts3p = knowns.optionsWithFlag(SOCGameOption.FLAG_3RD_PARTY, 0);
        assertNotNull(opts3p);
        assertEquals(1, opts3p.size());
        assertTrue(opts3p.containsKey("T3P"));

        builtMap.put("T3P", opt3PKnown);

        newerOpts = knowns.optionsNewerThanVersion(OLDER_VERSION, false, false);
        assertNotNull(newerOpts);
        testMap = new HashMap<>();
        for (SOCGameOption opt : newerOpts)
            testMap.put(opt.key, opt);
        assertTrue("testMap should contain third-party T3P", testMap.containsKey("T3P"));
        assertEquals("expected key count", builtMap.size(), testMap.size());
        for (String optKey : builtMap.keySet())
            if (! testMap.containsKey(optKey))
                fail("missing expected key: " + optKey);

        // cleanup
        knowns.addKnownOption(new SOCGameOption("T3P", null));
        assertNull(knowns.getKnownOption("T3P", false));

        // TODO server-side tests too: call w/ (cliVers, false, true, null) etc
    }

    /**
     * Test {@link SOCGameOptionSet#optionsNotSupported(soc.util.SOCFeatureSet)}.
     * @see #testOptionsTrimmedForSupport()
     * @since 2.5.00
     */
    @Test
    public void testOptionsNotSupported()
    {
        // build a set of game opts which don't require any client features
        SOCGameOptionSet opts = new SOCGameOptionSet();
        for (String okey : new String[]{"RD", "N7C", "NT"})
        {
            SOCGameOption opt = knownOpts.getKnownOption(okey, true);
            assertNotNull(opt);
            assertNull(opt.getClientFeature());
            assertEquals(SOCGameOption.OTYPE_BOOL, opt.optType);
            opt.setBoolValue(true);
            opts.add(opt);
        }
        // that set's fine even with no cli feats
        assertNull(opts.optionsNotSupported(null));
        assertNull(opts.optionsNotSupported(new SOCFeatureSet(";;")));
        // and fine with standard feats
        assertNull(opts.optionsNotSupported(new SOCFeatureSet(true, false)));

        // build a set of game opts for Sea Board
        opts = new SOCGameOptionSet();
        SOCGameOption opt = knownOpts.getKnownOption("SBL", true);
        assertNotNull(opt);
        opt.setBoolValue(true);
        opts.add(opt);
        opt = knownOpts.getKnownOption("PL", true);
        assertNotNull(opt);
        opt.setIntValue(3);
        opts.add(opt);

        // mostly-standard client feats
        assertNull(opts.optionsNotSupported(new SOCFeatureSet(";6pl;sb;")));

        // empty feat set
        Map<String, SOCGameOption> nsOpts = opts.optionsNotSupported(null);
        assertNotNull(nsOpts);
        assertEquals(nsOpts.size(), 1);
        assertTrue(nsOpts.containsKey("SBL"));

        // empty feat set
        nsOpts = opts.optionsNotSupported(new SOCFeatureSet(""));
        assertNotNull(nsOpts);
        assertEquals(nsOpts.size(), 1);
        assertTrue(nsOpts.containsKey("SBL"));

        // sea board but not 6pl
        assertNull(opts.optionsNotSupported(new SOCFeatureSet(";sb;")));

        // 6pl but not sea board
        nsOpts = opts.optionsNotSupported(new SOCFeatureSet(";6pl;"));
        assertNotNull(nsOpts);
        assertEquals(nsOpts.size(), 1);
        assertTrue(nsOpts.containsKey("SBL"));

        // build a set of opts for 6-player game
        opts.get("PL").setIntValue(5);
        opts.remove("SBL");
        opt = knownOpts.getKnownOption("PLB", true);
        opt.setBoolValue(true);
        opts.add(opt);

        // standard v1.x.xx client feats
        assertNull(opts.optionsNotSupported(new SOCFeatureSet(true, false)));
        // 6-player board
        assertNull(opts.optionsNotSupported(new SOCFeatureSet(";6pl;")));
        // sea board
        nsOpts = opts.optionsNotSupported(new SOCFeatureSet(";sb;"));
        assertNotNull(nsOpts);
        assertEquals(nsOpts.size(), 1);
        assertTrue(nsOpts.containsKey("PLB"));

        // set of opts for sea 6-player game
        opt = knownOpts.getKnownOption("SBL", true);
        assertNotNull(opt);
        assertEquals("sb", opt.getClientFeature());
        opt.setBoolValue(true);
        opts.add(opt);
        opt = knownOpts.getKnownOption(SOCGameOptionSet.K_SC_CLVI, true);
        assertNotNull(opt);
        assertEquals(SOCFeatureSet.CLIENT_SCENARIO_VERSION, opt.getClientFeature());
        opt.setBoolValue(true);
        opts.add(opt);

        // is fine with standard v2.x.xx client feats
        assertNull(opts.optionsNotSupported(new SOCFeatureSet(";6pl;sb;sc=2200;")));
        // sea board but not scenarios
        nsOpts = opts.optionsNotSupported(new SOCFeatureSet(";6pl;sb;"));
        assertNotNull(nsOpts);
        assertEquals(nsOpts.size(), 1);
        assertTrue(nsOpts.containsKey(SOCGameOptionSet.K_SC_CLVI));
        // only 6pl
        nsOpts = opts.optionsNotSupported(new SOCFeatureSet(";6pl;"));
        assertNotNull(nsOpts);
        assertEquals(nsOpts.size(), 2);
        assertTrue(nsOpts.containsKey("SBL"));
        assertTrue(nsOpts.containsKey(SOCGameOptionSet.K_SC_CLVI));
        // neither sea nor 6pl
        nsOpts = opts.optionsNotSupported(new SOCFeatureSet(""));
        assertNotNull(nsOpts);
        assertEquals(nsOpts.size(), 3);
        assertTrue(nsOpts.containsKey("PLB"));
        assertTrue(nsOpts.containsKey("SBL"));
        assertTrue(nsOpts.containsKey(SOCGameOptionSet.K_SC_CLVI));
    }

    /**
     * Test {@link SOCGameOptionSet#optionsTrimmedForSupport(soc.util.SOCFeatureSet)}.
     * @see #testOptionsNotSupported()
     * @since 2.5.00
     */
    @Test
    public void testOptionsTrimmedForSupport()
    {
        // empty option set is fine, even with empty feature set
        SOCGameOptionSet opts = new SOCGameOptionSet();
        assertNull(opts.optionsTrimmedForSupport(null));
        assertNull(opts.optionsTrimmedForSupport(new SOCFeatureSet(";;")));
        assertNull(opts.optionsTrimmedForSupport(new SOCFeatureSet(";;")));

        // build a set of game opts which don't require any client features
        for (String okey : new String[]{"RD", "N7C", "NT"})
        {
            SOCGameOption opt = knownOpts.getKnownOption(okey, true);
            assertNotNull(opt);
            assertNull(opt.getClientFeature());
            assertEquals(SOCGameOption.OTYPE_BOOL, opt.optType);
            opt.setBoolValue(true);
            opts.add(opt);
        }
        // that set's fine even with no cli feats
        assertNull(opts.optionsTrimmedForSupport(null));
        assertNull(opts.optionsTrimmedForSupport(new SOCFeatureSet(";;")));
        // and fine with standard feats
        assertNull(opts.optionsTrimmedForSupport(new SOCFeatureSet(true, false)));

        // build a set of opts for 6-player game
        opts = new SOCGameOptionSet();
        SOCGameOption optPL = knownOpts.getKnownOption("PL", true);
        assertNotNull(optPL);
        optPL.setIntValue(5);
        opts.add(optPL);

        // is fine with standard v2.x.xx client feats
        assertNull(opts.optionsTrimmedForSupport(new SOCFeatureSet(";6pl;sb;sc=2200;")));
        // standard v1.x.xx client feats
        assertNull(opts.optionsTrimmedForSupport(new SOCFeatureSet(true, false)));

        // client feats without 6-player support: PL is trimmed to 4, not rejected
        assertEquals(5, opts.get("PL").getIntValue());
        Map<String, SOCGameOption> tsOpts = opts.optionsTrimmedForSupport(new SOCFeatureSet(";sb;"));
        assertNotNull(tsOpts);
        assertEquals(tsOpts.size(), 1);
        optPL = tsOpts.get("PL");
        assertNotNull(optPL);
        assertEquals(4, optPL.getIntValue());
        // empty client feats
        optPL.setIntValue(5);
        opts.put(optPL);
        tsOpts = opts.optionsTrimmedForSupport(new SOCFeatureSet(""));
        assertNotNull(tsOpts);
        assertEquals(tsOpts.size(), 1);
        optPL = tsOpts.get("PL");
        assertNotNull(optPL);
        assertEquals(4, optPL.getIntValue());
        // null client feats
        optPL.setIntValue(5);
        opts.put(optPL);
        tsOpts = opts.optionsTrimmedForSupport(null);
        assertNotNull(tsOpts);
        assertEquals(tsOpts.size(), 1);
        optPL = tsOpts.get("PL");
        assertNotNull(optPL);
        assertEquals(4, optPL.getIntValue());
    }

    /**
     * Test a few things for {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)},
     * which is also partially tested in {@link #testAdjustOptionsToKnown_doServerPreadjust()} and others.
     * @since 2.7.00
     */
    @Test
    public void testAdjustOptionsToKnown()
    {
        final SOCGameOptionSet knownOptsPlus = SOCGameOptionSet.getAllKnownOptions();
        knownOptsPlus.addKnownOption(new SOCGameOption("ZZ", 1000, 2500, 7, 0, 9, SOCGameOption.FLAG_DROP_IF_UNUSED, "ZZ"));
            // none of the known options with FLAG_DROP_IF_UNUSED is OTYPE_INT, 0 so make one

        // FLAG_DROP_IF_UNUSED:
        // will test as non-default, default.

        Map<String, SOCGameOption> optsMap = SOCGameOption.parseOptionsToMap("VP=t11,PLB=t,SC=xyz,ZZ=3", knownOptsPlus);
        assertNotNull(optsMap);
        SOCGameOptionSet opts = new SOCGameOptionSet(optsMap);
        assertEquals(4, opts.size());
        // - OTYPE_INTBOOL, OTYPE_ENUMBOOL: VP
        SOCGameOption optVP = opts.get("VP");
        assertNotNull(optVP);
        assertTrue(optVP.hasFlag(SOCGameOption.FLAG_DROP_IF_UNUSED));
        assertEquals(SOCGameOption.OTYPE_INTBOOL, optVP.optType);
        assertEquals(11, optVP.getIntValue());
        assertTrue(optVP.getBoolValue());
        // - OTYPE_BOOL: PLB
        SOCGameOption optPLB = opts.get("PLB");
        assertNotNull(optPLB);
        assertTrue(optPLB.hasFlag(SOCGameOption.FLAG_DROP_IF_UNUSED));
        assertEquals(SOCGameOption.OTYPE_BOOL, optPLB.optType);
        assertTrue(optPLB.getBoolValue());
        // - OTYPE_STR: SC
        SOCGameOption optSC = opts.get("SC");
        assertNotNull(optSC);
        assertTrue(optSC.hasFlag(SOCGameOption.FLAG_DROP_IF_UNUSED));
        assertEquals(SOCGameOption.OTYPE_STR, optSC.optType);
        assertEquals("xyz", optSC.getStringValue());
        // - OTYPE_INT, OTYPE_ENUM: ignore boolValue since not also boolean-type (OTYPE_INTBOOL nor OTYPE_ENUMBOOL)
        SOCGameOption optZZ = opts.get("ZZ");
        assertNotNull(optZZ);
        assertTrue(optZZ.hasFlag(SOCGameOption.FLAG_DROP_IF_UNUSED));
        assertEquals(SOCGameOption.OTYPE_INT, optZZ.optType);
        assertEquals(3, optZZ.getIntValue());
        // - OTYPE_*: Test the new type's FLAG_DROP_IF_UNUSED behavior

        final SOCFeatureSet emptyFeats = new SOCFeatureSet("");
        Map<String, String> optProblems = opts.adjustOptionsToKnown(knownOptsPlus, false, emptyFeats);
        assertNull(optProblems);
        assertEquals(4, opts.size());  // since none are default values

        // now test whether drops at default:
        assertEquals(10, optVP.defaultIntValue);
        assertFalse(optVP.defaultBoolValue);
        optVP.setIntValue(optVP.defaultIntValue);  // int to default; keep its bool value non-default for now
        assertFalse(optPLB.defaultBoolValue);
        optPLB.setBoolValue(false);
        optSC.setStringValue("");
        assertEquals(7, optZZ.defaultIntValue);
        optZZ.setIntValue(optZZ.defaultIntValue);
        optProblems = opts.adjustOptionsToKnown(knownOptsPlus, false, emptyFeats);
        assertNull(optProblems);
        assertEquals(1, opts.size());  // since most are default values
        assertNotNull(opts.get("VP"));

        // VP: default bool value, non-default int
        optVP.setIntValue(11);
        optVP.setBoolValue(false);
        optProblems = opts.adjustOptionsToKnown(knownOptsPlus, false, emptyFeats);
        assertNull(optProblems);
        assertEquals(1, opts.size());
        assertNotNull(opts.get("VP"));

        // finally, set both fields to default; should drop VP
        optVP.setIntValue(10);
        optVP.setBoolValue(false);
        optProblems = opts.adjustOptionsToKnown(knownOptsPlus, false, emptyFeats);
        assertNull(optProblems);
        assertEquals(0, opts.size());

        // - TODO what else?

    }

    /**
     * Test a few things for {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)}
     * with doServerPreadjust=true.
     * @since 2.7.00
     */
    @Test
    public void testAdjustOptionsToKnown_doServerPreadjust()
    {
        // FLAG_DROP_IF_PARENT_UNUSED:

        SOCGameOptionSet opts = new SOCGameOptionSet();
        final SOCFeatureSet emptyFeats = new SOCFeatureSet("");

        // - Parent is bool w/ FLAG_DROP_IF_UNUSED:
        {
            final SOCGameOption optUB = knownOpts.getKnownOption("UB", true);
            assertNotNull(optUB);
            assertEquals(SOCGameOption.OTYPE_BOOL, optUB.optType);
            assertTrue(optUB.hasFlag(SOCGameOption.FLAG_DROP_IF_UNUSED));
            opts.add(optUB);

            final SOCGameOption optUBL = knownOpts.getKnownOption("UBL", true);
            assertNotNull(optUBL);
            opts.add(optUBL);
            optUBL.setBoolValue(true);
            assertTrue(optUBL.hasFlag(SOCGameOption.FLAG_DROP_IF_PARENT_UNUSED));
            assertEquals("UB", SOCGameOption.getGroupParentKey(optUBL.key));

            optUB.setBoolValue(true);
            assertNull(opts.adjustOptionsToKnown(knownOpts, true, emptyFeats));
            assertNotNull(opts.get("UBL"));

            optUB.setBoolValue(false);
            assertNull(opts.adjustOptionsToKnown(knownOpts, true, emptyFeats));
            assertNull(opts.get("UBL"));
            assertNull(opts.get("UB"));

            opts.add(optUBL);
            optUB.setBoolValue(true);
            opts.add(optUB);
            assertNull(opts.adjustOptionsToKnown(knownOpts, true, emptyFeats));
            assertNotNull(opts.get("UBL"));
            opts.remove("UB");
            assertNull(opts.adjustOptionsToKnown(knownOpts, true, emptyFeats));
            assertNull(opts.get("UBL"));
        }

        // - Parent is bool par which doesn't have FLAG_DROP_IF_UNUSED:

        final SOCGameOptionSet knownOptsPlus = SOCGameOptionSet.getAllKnownOptions();
        opts = new SOCGameOptionSet();
        {
            final SOCGameOption optRD = knownOpts.getKnownOption("RD", true);
            assertNotNull(optRD);
            assertEquals(SOCGameOption.OTYPE_BOOL, optRD.optType);
            assertFalse(optRD.hasFlag(SOCGameOption.FLAG_DROP_IF_UNUSED));
            assertFalse(optRD.defaultBoolValue);
            optRD.setBoolValue(true);
            opts.add(optRD);

            final SOCGameOption optRDZ = new SOCGameOption
                ("RDZ", 1000, 2500, 7, 0, 9, SOCGameOption.FLAG_DROP_IF_PARENT_UNUSED, "RDZ");
            knownOptsPlus.addKnownOption(optRDZ);
            optRDZ.setIntValue(3);
            opts.add(optRDZ);

            // drop only if parent not set
            assertNull(opts.adjustOptionsToKnown(knownOptsPlus, true, emptyFeats));
            assertNotNull(opts.get("RDZ"));
            optRD.setBoolValue(false);
            assertNull(opts.adjustOptionsToKnown(knownOptsPlus, true, emptyFeats));
            assertNull(opts.get("RDZ"));
        }

        // - Parent is a intbool which doesn't have FLAG_DROP_IF_UNUSED:

        opts = new SOCGameOptionSet();
        {
            final SOCGameOption optN7 = knownOptsPlus.get("N7");
            assertNotNull(optN7);
            assertEquals(SOCGameOption.OTYPE_INTBOOL, optN7.optType);
            assertFalse(optN7.hasFlag(SOCGameOption.FLAG_DROP_IF_UNUSED));
            assertEquals(7, optN7.defaultIntValue);
            assertFalse(optN7.defaultBoolValue);
            optN7.setIntValue(8);
            opts.add(optN7);

            final SOCGameOption optN7Z = new SOCGameOption
                ("N7Z", 1000, 2500, 7, 0, 9, SOCGameOption.FLAG_DROP_IF_PARENT_UNUSED, "N7Z");
            knownOptsPlus.addKnownOption(optN7Z);
            optN7Z.setIntValue(3);
            opts.add(optN7Z);

            // drop only if parent not set
            assertNull(opts.adjustOptionsToKnown(knownOptsPlus, true, emptyFeats));
            assertNotNull(opts.get("N7Z"));
            optN7.setBoolValue(true);
            optN7.setIntValue(optN7.defaultIntValue);
            assertNull(opts.adjustOptionsToKnown(knownOptsPlus, true, emptyFeats));
            assertNotNull(opts.get("N7Z"));
            optN7.setBoolValue(false);
            optN7.setIntValue(optN7.defaultIntValue);
            assertNull(opts.adjustOptionsToKnown(knownOptsPlus, true, emptyFeats));
            assertNull(opts.get("N7Z"));
        }

        // - Parent is str:

        opts = new SOCGameOptionSet();
        {
            final SOCGameOption optSC = knownOptsPlus.get("SC");
            assertNotNull(optSC);
            assertEquals(SOCGameOption.OTYPE_STR, optSC.optType);
            assertTrue(optSC.hasFlag(SOCGameOption.FLAG_DROP_IF_UNUSED));
            optSC.setStringValue(SOCScenario.K_SC_NSHO);  // avoid "unknown scenario" from adjustOptionsToKnown
            opts.add(optSC);

            final SOCGameOption optSCZ = new SOCGameOption
                ("SCZ", 1000, 2500, 7, 0, 9, SOCGameOption.FLAG_DROP_IF_PARENT_UNUSED, "SCZ");
            knownOptsPlus.addKnownOption(optSCZ);
            optSCZ.setIntValue(3);
            opts.add(optSCZ);

            // drop only if parent not set
            final SOCFeatureSet cliSeaFeats = new SOCFeatureSet(";sb;sc=2500;");
            assertNull(opts.adjustOptionsToKnown(knownOptsPlus, true, cliSeaFeats));
            assertNotNull(opts.get("SCZ"));
            optSC.setStringValue("");
            assertNull(opts.adjustOptionsToKnown(knownOptsPlus, true, cliSeaFeats));
            assertNull(opts.get("SCZ"));
        }

        // end of FLAG_DROP_IF_PARENT_UNUSED testing.

        // TODO test other work done when doServerPreadjust=true
        // (some is tested in other methods already)
    }

    /**
     * Test {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)}
     * with doServerPreadjust=true and limited client {@link SOCFeatureSet}.
     *<P>
     * Relies on related tests covered in {@link #testOptionsNotSupported()} and
     * {@link #testOptionsTrimmedForSupport()}, since
     * {@code SOCGameOptionSet.adjustOptionsToKnown(..)} calls the methods tested there.
     * @since 2.5.00
     */
    @Test
    public void testAdjustOptionsToKnown_doServerPreadjust_limitedCliFeats()
    {
        // game opts for a 6-player game
        SOCGameOptionSet opts = new SOCGameOptionSet();
        SOCGameOption opt = knownOpts.getKnownOption("PLB", true);
        assertNotNull(opt);
        assertEquals(SOCFeatureSet.CLIENT_6_PLAYERS, opt.getClientFeature());
        opt.setBoolValue(true);
        opts.add(opt);
        SOCGameOption optPL = knownOpts.getKnownOption("PL", true);
        assertNotNull(optPL);
        assertNull(optPL.getClientFeature());
        optPL.setIntValue(5);
        opts.add(optPL);

        // client has no features
        Map<String, String> optProblems = opts.adjustOptionsToKnown(knownOpts, true, new SOCFeatureSet(""));
        assertNotNull(optProblems);
        assertTrue(optProblems.containsKey("PLB"));
        assertTrue(optProblems.get("PLB").contains("requires missing feature"));

        // client has some features, but not 6-player
        optProblems = opts.adjustOptionsToKnown(knownOpts, true, new SOCFeatureSet(";sb;sc=2500;"));
        assertNotNull(optProblems);
        assertTrue(optProblems.containsKey("PLB"));
        assertTrue(optProblems.get("PLB").contains("requires missing feature"));

        // client has that feature
        optProblems = opts.adjustOptionsToKnown(knownOpts, true, new SOCFeatureSet(";6pl;"));
        assertNull(optProblems);
    }

    /**
     * Test {@link SOCGameOption#getGroupParentKey(String)}.
     * @since 2.7.00
     */
    @Test
    public void testGetGroupParentKey()
    {
        String groupKey;

        try {
            groupKey = SOCGameOption.getGroupParentKey(null);
            fail("getGroupParentKey(null): should have thrown; returned " + groupKey);
        }
        catch(IllegalArgumentException e) {}
        try {
            groupKey = SOCGameOption.getGroupParentKey("");
            fail("getGroupParentKey(\"\"): should have thrown; returned " + groupKey);
        }
        catch(IllegalArgumentException e) {}

        assertNull(SOCGameOption.getGroupParentKey("X"));  // length 1
        assertNull(SOCGameOption.getGroupParentKey("XY"));  // length 2
        assertNull(SOCGameOption.getGroupParentKey("_XYZ"));  // length > 3, starts with '_'
        assertNull(SOCGameOption.getGroupParentKey("XYZW"));  // length > 3, has no '_'

        assertEquals("XY", SOCGameOption.getGroupParentKey("XYZ"));  // length 3, no special chars
        assertEquals("X3", SOCGameOption.getGroupParentKey("X3Z"));
        assertEquals("XY", SOCGameOption.getGroupParentKey("XY_"));  // length 3 & ends with '_'
        assertEquals("_Y", SOCGameOption.getGroupParentKey("_YZ"));  // length 3 & starts with '_'
        assertEquals("ABC", SOCGameOption.getGroupParentKey("ABC_"));  // length > 3 & ends with '_'
        assertEquals("ABC", SOCGameOption.getGroupParentKey("ABC_DEF"));
    }

    /**
     * Test {@link SOCGameOption#isSet()} and {@link SOCGameOption#hasValue()}.
     * @since 2.7.00
     */
    @Test
    public void testIsSetHasValue()
    {
        // OTYPE_BOOL
        SOCGameOption opt = knownOpts.getKnownOption("RD", true);
        assertNotNull(opt);
        assertEquals(SOCGameOption.OTYPE_BOOL, opt.optType);
        assertFalse(opt.getBoolValue());
        assertEquals(0, opt.getIntValue());
        assertFalse(opt.isSet());
        assertFalse(opt.hasValue());
        opt.setBoolValue(true);
        assertTrue(opt.isSet());
        assertTrue(opt.hasValue());

        // OTYPE_INTBOOL, OTYPE_ENUMBOOL
        opt = knownOpts.getKnownOption("VP", true);
        assertNotNull(opt);
        assertEquals(SOCGameOption.OTYPE_INTBOOL, opt.optType);
        // int default, bool false
        assertEquals(10, opt.defaultIntValue);
        assertEquals(10, opt.getIntValue());
        assertFalse(opt.getBoolValue());
        assertFalse(opt.isSet());
        assertTrue("default OTYPE_INTBOOL value", opt.hasValue());
        // int default, bool true
        opt.setBoolValue(true);
        assertTrue(opt.isSet());
        assertTrue(opt.hasValue());
        // int != default, bool false
        opt.setIntValue(13);
        opt.setBoolValue(false);
        assertTrue(opt.isSet());
        assertTrue(opt.hasValue());
        // int 0, bool true, but none of the INTBOOL known options has min value 0 so make one
        opt = new SOCGameOption("ZZ", 1000, 2500, false, 0, 0, 9, 0, "ZZ");
        assertEquals(SOCGameOption.OTYPE_INTBOOL, opt.optType);
        assertFalse(opt.defaultBoolValue);
        assertFalse(opt.getBoolValue());
        assertEquals(0, opt.defaultIntValue);
        assertEquals(0, opt.getIntValue());
        assertFalse(opt.isSet());
        assertFalse(opt.hasValue());
        opt.setBoolValue(true);
        assertTrue(opt.isSet());
        assertTrue(opt.hasValue());
        opt.setBoolValue(false);
        assertFalse(opt.isSet());
        assertFalse(opt.hasValue());
        // unrealistic test to delineate the 2 methods; no reason to set an intbool's string value
        opt.setStringValue("x");
        assertFalse(opt.isSet());
        assertTrue(opt.hasValue());

        // OTYPE_INT, OTYPE_ENUM
        opt = knownOpts.getKnownOption("PL", true);
        assertNotNull(opt);
        assertEquals(SOCGameOption.OTYPE_INT, opt.optType);
        assertEquals(2, opt.minIntValue);
        assertEquals(4, opt.defaultIntValue);
        assertEquals(6, opt.maxIntValue);
        assertEquals(4, opt.getIntValue());
        assertTrue(opt.hasValue());
        assertFalse("default OTYPE_INT value", opt.isSet());
        opt.setIntValue(3);
        assertTrue(opt.hasValue());
        assertTrue(opt.isSet());
        opt.setIntValue(4);
        assertTrue(opt.hasValue());
        assertFalse("back to default", opt.isSet());
        assertFalse(opt.getBoolValue());
        opt.setBoolValue(true);  // unrealistic; no reason to set an int's bool value
        assertTrue(opt.hasValue());
        assertFalse("ignore OTYPE_INT's bool value", opt.isSet());

        // OTYPE_STR, OTYPE_STRHIDE
        opt = knownOpts.getKnownOption("SC", true);
        assertNotNull(opt);
        assertEquals(SOCGameOption.OTYPE_STR, opt.optType);
        assertEquals("", opt.getStringValue());
        assertFalse(opt.isSet());
        assertFalse(opt.hasValue());
        opt.setBoolValue(true);  // unrealistic; no reason to set a string's bool value
        assertTrue(opt.hasValue());
        assertFalse("ignore OTYPE_STR bool value", opt.isSet());
        opt.setBoolValue(false);
        assertFalse(opt.isSet());
        assertFalse(opt.hasValue());
        opt.setStringValue("x");
        assertTrue(opt.isSet());
        assertTrue(opt.hasValue());
        opt.setStringValue(null);
        assertEquals("stores null as empty string", "", opt.getStringValue());
        assertFalse(opt.isSet());
        assertFalse(opt.hasValue());
        assertEquals(0, opt.getIntValue());
        opt.setIntValue(2);  // unrealistic; no reason to set a string's int value
        assertEquals(2, opt.getIntValue());
        assertTrue(opt.hasValue());
        assertFalse("ignore OTYPE_STR int value", opt.isSet());

        // OTYPE_*: Test the new type's fields
    }

    /**
     * Test {@link SOCGameOption#equals(Object)}.
     * @since 2.5.00
     */
    @Test
    public void testEquals()
    {
        SOCGameOption opt = knownOpts.getKnownOption("PL", true);
        assertFalse(opt.equals(null));
        assertTrue(opt.equals(opt));

        // int value
        SOCGameOption op2 = knownOpts.getKnownOption("PL", true);
        assertTrue(opt.equals(op2));
        assertEquals(4, opt.getIntValue());
        op2.setIntValue(5);
        assertFalse(opt.equals(op2));

        // bool value
        SOCGameOption optPLB = knownOpts.getKnownOption("PLB", true);
        assertFalse(opt.equals(optPLB));
        op2 = knownOpts.getKnownOption("PLB", true);
        assertFalse(op2.getBoolValue());
        assertTrue(optPLB.equals(op2));
        op2.setBoolValue(true);
        assertFalse(optPLB.equals(op2));

        // string value
        SOCGameOption optSC = knownOpts.getKnownOption("SC", true);
        assertFalse(opt.equals(optSC));
        op2 = knownOpts.getKnownOption("SC", true);
        assertEquals("", optSC.getStringValue());
        assertTrue(optSC.equals(op2));
        op2.setStringValue("xyz");
        assertFalse(optSC.equals(op2));
    }

    /**
     * Test {@link ServerGametypeInfo#knownOpts} and {@link ServerGametypeInfo#receiveDefaults(Map, int)}.
     * @since 2.7.00
     */
    @Test
    public void testClientReceiveDefaults()
    {
        final SOCGameOption knownOptUB = knownOpts.getKnownOption("UB", true);
        assertNotNull(knownOptUB);
        assertEquals(SOCGameOption.OTYPE_BOOL, knownOptUB.optType);
        assertTrue(knownOptUB.hasFlag(SOCGameOption.FLAG_SET_AT_CLIENT_ONCE));
        assertEquals(2700, knownOptUB.minVersion);
        assertFalse(knownOptUB.getBoolValue());

        for (int loopForPrac = 0; loopForPrac <= 1; ++loopForPrac)
            for (int loopSrvOlderThanUB = 0; loopSrvOlderThanUB <= 1; ++loopSrvOlderThanUB)
            {
                String testDesc = ("forPractice=" + (loopForPrac == 1) + ", srvOlderThanUB=" + (loopSrvOlderThanUB == 1));

                ServerGametypeInfo servOpts = new ServerGametypeInfo(loopForPrac == 1);
                assertNotNull(testDesc, servOpts.knownOpts);
                // knownOpts should have same contents as SGOSet.getAllKnownOptions:
                {
                    List<SOCGameOption> allKnowns = new ArrayList<>();
                    for (SOCGameOption opt : SOCGameOptionSet.getAllKnownOptions())
                        allKnowns.add(opt);
                    List<SOCGameOption> servKnowns = new ArrayList<>();
                    for (SOCGameOption opt : servOpts.knownOpts)
                        servKnowns.add(opt);
                    assertEquals(testDesc, allKnowns, servKnowns);
                }

                SOCGameOption opt = servOpts.knownOpts.get("UB");
                assertNotNull(testDesc, opt);
                assertEquals(testDesc, SOCGameOption.OTYPE_BOOL, opt.optType);
                assertTrue(testDesc, opt.hasFlag(SOCGameOption.FLAG_SET_AT_CLIENT_ONCE));
                assertEquals(2700, opt.minVersion);
                assertFalse(testDesc, opt.defaultBoolValue);
                assertFalse(testDesc, opt.getBoolValue());
                assertEquals(testDesc, -1, opt.getMinVersion(null));  // because boolValue == false

                final SOCGameOption servOptsOptUB = servOpts.getNewGameOpts().get("UB");
                assertNotNull(testDesc, servOptsOptUB);
                assertEquals(testDesc, knownOptUB, servOptsOptUB);
                assertEquals(testDesc, 2700, servOptsOptUB.minVersion);
                assertFalse(testDesc, servOptsOptUB.defaultBoolValue);
                assertFalse(testDesc, servOptsOptUB.getBoolValue());

                Map<String, SOCGameOption> servOptsMap = null;
                if (loopForPrac == 0)
                {
                    // give a few changed values, like client might see from actual server:
                    servOptsMap = new HashMap<>();

                    opt = servOpts.knownOpts.get("N7");
                    assertFalse(testDesc, opt.defaultBoolValue);
                    assertEquals(testDesc, 7, opt.defaultIntValue);
                    opt.setBoolValue(true);
                    opt.setIntValue(5);
                    servOptsMap.put("N7", opt);

                    opt = servOpts.knownOpts.get("NT");
                    assertFalse(testDesc, opt.defaultBoolValue);
                    opt.setBoolValue(true);
                    servOptsMap.put("NT", opt);

                    // Even with "UB" false in server default values,
                    // client should set it true for first use there because has FLAG_SET_AT_CLIENT_ONCE
                    opt = servOpts.knownOpts.get("UB");
                    assertFalse(testDesc, opt.getBoolValue());
                    servOptsMap.put("UB", opt);
                }

                servOpts.receiveDefaults(servOptsMap, ((loopSrvOlderThanUB == 1) ? 2699 : Version.versionNumber()));

                if (loopForPrac == 0)
                {
                    opt = servOpts.knownOpts.get("N7");
                    assertTrue(testDesc, opt.defaultBoolValue);
                    assertEquals(testDesc, 5, opt.defaultIntValue);

                    opt = servOpts.knownOpts.get("NT");
                    assertTrue(testDesc, opt.defaultBoolValue);

                    opt = servOpts.knownOpts.get("UB");
                    assertFalse(testDesc, opt.defaultBoolValue);  // FLAG_SET_AT_CLIENT_ONCE shouldn't change default
                }

                opt = servOpts.knownOpts.get("UB");
                assertNotNull(testDesc, opt);
                assertEquals(testDesc, SOCGameOption.OTYPE_BOOL, opt.optType);
                assertTrue(testDesc, opt.hasFlag(SOCGameOption.FLAG_SET_AT_CLIENT_ONCE));
                assertEquals(testDesc, (loopSrvOlderThanUB == 0), opt.getBoolValue());
                    // when server is older, shouldn't set true by default
                    // (but typically, actual client would set type to OTYPE_UNKNOWN because server is older than option's minVersion)

                final SOCGameOption servOptsOptUB2 = servOpts.getNewGameOpts().get("UB");
                assertNotNull(testDesc, servOptsOptUB2);
                assertEquals(testDesc, SOCGameOption.OTYPE_BOOL, servOptsOptUB2.optType);
                assertTrue(testDesc, servOptsOptUB2.hasFlag(SOCGameOption.FLAG_SET_AT_CLIENT_ONCE));
                assertEquals(testDesc, "UB", servOptsOptUB2.key);
                assertFalse(testDesc, servOptsOptUB2.defaultBoolValue);
                assertEquals(testDesc, (loopSrvOlderThanUB == 0), servOptsOptUB2.getBoolValue());
            }
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.game.TestGameOptions");
    }

}
