/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2018-2020 Jeremy D Monin <jeremy@nand.net>
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

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCDevCardConstants;
import soc.game.SOCGameOption;
import soc.game.SOCVersionedItem;
import soc.util.Version;

/**
 * Tests for {@link SOCGameOption}.
 *<P>
 * TODO add more basic-functionality tests
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestGameOptions
{
    /**
     * Test that {@link SOCGameOption#setIntValue(int)} works and that, instead of throwing an exception,
     * values outside of min/max range are clipped to that range; uses intbool option {@code "VP"}.
     */
    @Test
    public void testSetIntValueRange()
    {
        final SOCGameOption vp = SOCGameOption.getOption("VP", true);
        assertNotNull(vp);
        vp.setIntValue(12);   // is within range
        assertEquals(12, vp.getIntValue());
        vp.setIntValue(2);  // too low
        assertEquals("should clip to min", vp.minIntValue, vp.getIntValue());
        vp.setIntValue(vp.maxIntValue + 999);  // too high
        assertEquals("should clip to max", vp.maxIntValue, vp.getIntValue());
    }

    /**
     * Test that keys of {@link SOCGameOption#initAllOptions()} and {@link SOCGameOption#getAllKnownOptions()}
     * are consistent internally and with each other.
     *<UL>
     * <LI> Each option's map key must be its option key
     * <LI> Key must not start with {@code "3"} or {@code "_3"} unless option has {@link SOCGameOption#FLAG_3RD_PARTY}
     *</UL>
     */
    @Test
    public void testKnownOptionMapKeysConsistent()
    {
        final Map<String, SOCGameOption> allOpts = SOCGameOption.initAllOptions(),
            knownOpts = SOCGameOption.getAllKnownOptions();  // sanitized copy of initAllOptions()

        for (String okey : allOpts.keySet())
        {
            SOCGameOption opt = allOpts.get(okey);
            assertEquals("getAllKnownOptions: map key != opt.key", okey, opt.key);
            assertTrue
                ("key " + okey + " in initAllOptions() but missing from getAllKnownOptions()",
                 knownOpts.containsKey(okey));

            boolean named3p = okey.startsWith("3") || okey.startsWith("_3");
            assertEquals
                ("key " + okey + " starts with 3 only if FLAG_3RD_PARTY",
                 named3p, opt.hasFlag(SOCGameOption.FLAG_3RD_PARTY));
        }
    }

    /**
     * Test that when the client sends a new-game request whose opts contains {@code "VP"} with boolean part false,
     * that {@code "VP"} will be removed from the set of options by
     * {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean) SGO.adjustOptionsToKnown(newOpts, knownOpts, true)}.
     */
    @Test
    public void testServerRemovesFalseVP()
    {
        final Map<String, SOCGameOption> newOpts = new HashMap<>(),
            knownOpts = new HashMap<>();

        final SOCGameOption optVP = SOCGameOption.getOption("VP", true);
        optVP.setIntValue(12);
        optVP.setBoolValue(false);

        // should remove VP when false and not present at "server" with a default
        newOpts.put("VP", optVP);
        StringBuilder sb = SOCGameOption.adjustOptionsToKnown(newOpts, knownOpts, true);
        assertNull(sb);
        assertFalse(newOpts.containsKey("VP"));

        // should remove VP when false and is present at "server" with a default
        {
            final SOCGameOption srvVP = SOCGameOption.getOption("VP", true);
            srvVP.setIntValue(13);
            srvVP.setBoolValue(true);
            knownOpts.put("VP", srvVP);
        }
        newOpts.put("VP", optVP);
        sb = SOCGameOption.adjustOptionsToKnown(newOpts, knownOpts, true);
        assertNull(sb);
        assertFalse(newOpts.containsKey("VP"));

        // shouldn't remove if VP is true
        optVP.setBoolValue(true);
        newOpts.put("VP", optVP);
        sb = SOCGameOption.adjustOptionsToKnown(newOpts, knownOpts, true);
        assertNull(sb);
        assertTrue(newOpts.containsKey("VP"));
    }

    /**
     * Test adding a new known option and removing it.
     */
    @Test
    public void testAddKnownOption()
    {
        // add known opt
        assertNull(SOCGameOption.getOption("_TESTF", false));
        final SOCGameOption newKnown = new SOCGameOption
            ("_TESTF", 2000, 2000, false, 0, "For unit test");
        boolean hadNoOld = SOCGameOption.addKnownOption(newKnown);
        assertTrue(hadNoOld);

        // getOption without clone should be same object
        SOCGameOption opt = SOCGameOption.getOption("_TESTF", false);
        assertTrue(newKnown == opt);

        // getOption with clone should be different object with same field values
        opt = SOCGameOption.getOption("_TESTF", true);
        assertTrue(newKnown != opt);
        assertNotNull(opt);
        assertEquals("_TESTF", opt.key);
        assertEquals(SOCGameOption.OTYPE_BOOL, opt.optType);
        assertEquals(2000, opt.minVersion);
        assertEquals(2000, opt.lastModVersion);

        // cleanup/remove known opt, by adding unknown opt
        hadNoOld = SOCGameOption.addKnownOption(new SOCGameOption("_TESTF"));
        assertFalse(hadNoOld);
        assertNull(SOCGameOption.getOption("_TESTF", false));
    }

    /**
     * Test server-side behavior of {@link SOCGameOption#FLAG_INTERNAL_GAME_PROPERTY}.
     * Currently can't test client-side because it's part of NewGameOptionsFrame GUI code.
     */
    @Test
    public void testFlagInternalGameProperty()
    {
        // setup
        final SOCGameOption newKnown = new SOCGameOption
            ("_TESTF", 2000, 2000, 0, 0, 0xFFFF, SOCGameOption.FLAG_INTERNAL_GAME_PROPERTY,
             "For unit test");
        assertNull(SOCGameOption.getOption("_TESTF", false));
        SOCGameOption.addKnownOption(newKnown);
        assertNotNull(SOCGameOption.getOption("_TESTF", false));

        // should remove internal option if sent from "client" to "server"
        final Map<String, SOCGameOption> newGameOpts = new HashMap<>();
        final SOCGameOption opt = SOCGameOption.getOption("_TESTF", true);
        opt.setIntValue(0x2211);
        newGameOpts.put("_TESTF", opt);
        SOCGameOption.adjustOptionsToKnown(newGameOpts, null, true);
        assertNull(newGameOpts.get("_TESTF"));

        // cleanup
        SOCGameOption.addKnownOption(new SOCGameOption("_TESTF"));
        assertNull(SOCGameOption.getOption("_TESTF", false));
    }

    /**
     * Inactive/activated gameopts: Test {@link SOCGameOption#activate(String)},
     * {@link SOCGameOption#optionsWithFlag(int, int)}.
     * Test {@link SOCGameOption#optionsForVersion(int, Map) SOCGameOption#optionsForVersion(cvers, null)} and
     * {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean) SGO.adjustOptionsToKnown(gameOpts, null, doServerPreadjust=true)}
     * checks for {@link SOCGameOption#FLAG_INACTIVE_HIDDEN}. Uses game options
     * {@link SOCGameOption#K_PLAY_FO "PLAY_FO"}, {@link SOCGameOption#K_PLAY_VPO "PLAY_VPO"}.
     * @since 2.4.10
     */
    @Test
    public void testFlagInactiveActivate()
    {
        final int TEST_CLI_VERSION = 2100;

        // setup:
        // - an option changed after client version 2100:
        final SOCGameOption optPlayVPO = SOCGameOption.getOption("PLAY_VPO", false);
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
        assertNull(SOCGameOption.getOption("_TESTACT", false));
        SOCGameOption.addKnownOption(newKnown2);
        assertNotNull(SOCGameOption.getOption("_TESTACT", false));
        assertTrue(newKnown2.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertFalse(newKnown2.hasFlag(SOCGameOption.FLAG_ACTIVATED));
        // - make sure PLAY_FO also has proper minVersion
        SOCGameOption optFO = SOCGameOption.getOption("PLAY_FO", false);
        assertNotNull(optFO);
        assertTrue(optFO.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertFalse(optFO.hasFlag(SOCGameOption.FLAG_ACTIVATED));
        assertEquals("minVersion 2000", 2000, optFO.minVersion);

        // optionsWithFlag ignores inactives unless asked for that flag
        {
            Map<String, SOCGameOption> inacts = SOCGameOption.optionsWithFlag
                (SOCGameOption.FLAG_INACTIVE_HIDDEN, 0);
            assertNotNull(inacts);
            assertTrue("inactive gameopts should include _TESTACT", inacts.containsKey("_TESTACT"));
            assertTrue("inactive gameopts should include PLAY_FO", inacts.containsKey("PLAY_FO"));
        }

        Map<String, SOCGameOption> activatedOpts = SOCGameOption.optionsWithFlag(SOCGameOption.FLAG_ACTIVATED, 2410);
        assertNull("not activated yet", activatedOpts);

        // testing the actual activation feature:

        // not in known options when inactive, even if changed after client version
        {
            List<SOCGameOption> opts = SOCGameOption.optionsNewerThanVersion(TEST_CLI_VERSION, false, true, null);
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

            opts = SOCGameOption.optionsForVersion(TEST_CLI_VERSION, null);
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

        HashMap<String, SOCGameOption> newGameReqOpts = new HashMap<>();
        newGameReqOpts.put("PLAY_VPO", optPlayVPO);
        StringBuilder optProblems = SOCGameOption.adjustOptionsToKnown(newGameReqOpts, null, true);
        assertNotNull(optProblems);
        assertTrue(optProblems.toString().contains("PLAY_VPO: inactive"));

        SOCGameOption.activate("PLAY_VPO");
        SOCGameOption.activate("_TESTACT");

        SOCGameOption activated = SOCGameOption.getOption("PLAY_VPO", false);  // non-cloned reference
        assertFalse(activated.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertTrue(activated.hasFlag(SOCGameOption.FLAG_ACTIVATED));

        activated = SOCGameOption.getOption("PLAY_VPO", true);  // clone should copy flags
        assertFalse(activated.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertTrue(activated.hasFlag(SOCGameOption.FLAG_ACTIVATED));

        SOCGameOption activated2 = SOCGameOption.getOption("_TESTACT", true);
        assertFalse(activated2.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertTrue(activated2.hasFlag(SOCGameOption.FLAG_ACTIVATED));

        activatedOpts = SOCGameOption.optionsWithFlag(SOCGameOption.FLAG_ACTIVATED, 2410);
        assertNotNull(activatedOpts);
        assertEquals(2, activatedOpts.size());
        assertEquals(activated, activatedOpts.get("PLAY_VPO"));
        assertEquals(activated2, activatedOpts.get("_TESTACT"));

        activatedOpts = SOCGameOption.optionsWithFlag(SOCGameOption.FLAG_ACTIVATED, 1999);  // older than _TESTACT minVersion
        assertNull(activatedOpts);

        // is in known options when active, even if not changed after client version
        {
            List<SOCGameOption> opts = SOCGameOption.optionsNewerThanVersion(TEST_CLI_VERSION, false, true, null);
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

            opts = SOCGameOption.optionsForVersion(TEST_CLI_VERSION, null);
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

        newGameReqOpts.put("PLAY_VPO", activated);
        optProblems = SOCGameOption.adjustOptionsToKnown(newGameReqOpts, null, true);
        assertNull(optProblems);

        // cleanup
        SOCGameOption.addKnownOption(new SOCGameOption("_TESTACT"));
        assertNull(SOCGameOption.getOption("_TESTACT", false));
    }

    /**
     * Test {@link SOCGameOption#activate(String)} when known option not found.
     * @since 2.4.10
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFlagInactiveActivate_notFound()
    {
        assertNull(SOCGameOption.getOption("_NONEXISTENT", false));
        SOCGameOption.activate("_NONEXISTENT");
    }

    /**
     * Test {@link SOCGameOption#activate(String)} when known option not inactive.
     * @since 2.4.10
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFlagInactiveActivate_notInactive()
    {
        SOCGameOption.activate("PL");
    }

    /**
     * Test that gameopt constructors can't be called with both
     * {@link SOCGameOption#FLAG_ACTIVATED} and {@link SOCGameOption#FLAG_INACTIVE_HIDDEN} set at same time.
     * @since 2.4.10
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFlagInactiveActivate_constructor()
    {
        final SOCGameOption opt = new SOCGameOption
            ("_TESTIAF", 2000, 2410, false, SOCGameOption.FLAG_ACTIVATED | SOCGameOption.FLAG_INACTIVE_HIDDEN,
             "test active and inactive at same time");
        // should throw IllegalArgumentException; next statement is there only to avoid compiler warnings
        assertNotNull(opt);
    }

    /**
     * Test {@link SOCVersionedItem#itemsMinimumVersion(Map)}.
     * @since 2.1.00
     */
    @Test
    public void testItemsMinimumVersion()
    {
        assertEquals(-1, SOCVersionedItem.itemsMinimumVersion(null, false));

        assertEquals(-1, SOCVersionedItem.itemsMinimumVersion(new HashMap<String, SOCGameOption>(), false));

        // TODO expand beyond empty/null tests
    }

    /**
     * Test {@link SOCGameOption#optionsNewerThanVersion(int, boolean, boolean, Map)}.
     * Currently client-side functions only: checkValues=false, trimEnums=false.
     * Also tests {@link SOCGameOption#FLAG_3RD_PARTY} and its interaction at client
     * with {@code optionsNewerThanVersion(..)}.
     * @since 2.4.10
     */
    @Test
    public void testOptionsNewerThanVersion()
    {
        final int currVers = Version.versionNumber();

        // client-side tests: checkValues=false, trimEnums=false

        // Nothing newer than current version
        for (int vers = currVers; vers <= (currVers + 1); ++vers)
        {
            List<SOCGameOption> opts = SOCGameOption.optionsNewerThanVersion(currVers, false, false, null);
            if (opts != null)
            {
                // filter out any activated options (like PLAY_VPO from other unit test)
                // which are added regardless of version

                ListIterator<SOCGameOption> iter = opts.listIterator();
                while (iter.hasNext())
                {
                    SOCGameOption opt = iter.next();
                    if (opt.hasFlag(SOCGameOption.FLAG_ACTIVATED))
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
        assertNull(SOCGameOption.getOption("T3P", false));
        SOCGameOption.addKnownOption(opt3PKnown);
        assertNotNull(SOCGameOption.getOption("T3P", false));

        // also add a 3P Known Option that's inactive, so it should be ignored client-side and server-side
        SOCGameOption new3PInact = new SOCGameOption
            ("T3I", -1, 1107, 0, 0, 0xFFFF,
            SOCGameOption.FLAG_3RD_PARTY | SOCGameOption.FLAG_INACTIVE_HIDDEN,
             "For unit test");
        assertTrue(new3PInact.hasFlag(SOCGameOption.FLAG_3RD_PARTY));
        assertTrue(new3PInact.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN));
        assertNull(SOCGameOption.getOption("T3I", false));
        SOCGameOption.addKnownOption(new3PInact);
        assertNotNull(SOCGameOption.getOption("T3I", false));

        assertNull("no active 3P gameopts yet", SOCGameOption.optionsWithFlag(SOCGameOption.FLAG_3RD_PARTY, 0));
        {
            Map<String, SOCGameOption> inacts3p = SOCGameOption.optionsWithFlag
                (SOCGameOption.FLAG_3RD_PARTY | SOCGameOption.FLAG_INACTIVE_HIDDEN, 0);
            assertNotNull(inacts3p);
            assertTrue("inactive gameopts should include T3I", inacts3p.containsKey("T3I"));
        }

        // build a reasonable expected list
        Map<String, SOCGameOption> builtMap = new HashMap<>();
        for (SOCGameOption opt : SOCGameOption.getAllKnownOptions().values())
        {
            if (((opt.lastModVersion > OLDER_VERSION) || opt.hasFlag(SOCGameOption.FLAG_ACTIVATED))
                && ! opt.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN))
                builtMap.put(opt.key, opt);
        }
        assertTrue("contains SC", builtMap.containsKey("SC"));    // added at v2000
        assertTrue("contains PLP", builtMap.containsKey("PLP"));  // added at v2300
        assertFalse("shouldn't contain T3P yet", builtMap.containsKey("T3P"));  // not recent, not 3rd-party yet

        List<SOCGameOption> newerOpts = SOCGameOption.optionsNewerThanVersion(OLDER_VERSION, false, false, null);
        assertNotNull(newerOpts);
        Map<String, SOCGameOption> testMap = new HashMap<>();
        for (SOCGameOption opt : newerOpts)
            testMap.put(opt.key, opt);
        assertEquals("expected key count", builtMap.size(), testMap.size());
        for (String optKey : builtMap.keySet())
            if (! testMap.containsKey(optKey))
                fail("missing expected key: " + optKey);

        // client-side test FLAG-3RD_PARTY
        opt3PKnown = new SOCGameOption
            ("T3P", -1, 1107, 0, 0, 0xFFFF, SOCGameOption.FLAG_3RD_PARTY, "For unit test");
        opt3PKnown.setClientFeature("com.example.js.test3p");
        assertTrue(opt3PKnown.hasFlag(SOCGameOption.FLAG_3RD_PARTY));
        SOCGameOption.addKnownOption(opt3PKnown);
        SOCGameOption knOpt = SOCGameOption.getOption("T3P", false);
        assertNotNull(knOpt);
        assertTrue(knOpt.hasFlag(SOCGameOption.FLAG_3RD_PARTY));
        Map<String, SOCGameOption> opts3p = SOCGameOption.optionsWithFlag(SOCGameOption.FLAG_3RD_PARTY, 0);
        assertNotNull(opts3p);
        assertEquals(1, opts3p.size());
        assertTrue(opts3p.containsKey("T3P"));

        builtMap.put("T3P", opt3PKnown);

        newerOpts = SOCGameOption.optionsNewerThanVersion(OLDER_VERSION, false, false, null);
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
        SOCGameOption.addKnownOption(new SOCGameOption("T3P"));
        assertNull(SOCGameOption.getOption("T3P", false));

        // TODO server-side tests too: call w/ (cliVers, false, true, null) etc
    }

    /**
     * Test {@link SOCGameOption#equals(Object)}.
     * @since 2.4.10
     */
    @Test
    public void testEquals()
    {
        SOCGameOption opt = SOCGameOption.getOption("PL", true);
        assertFalse(opt.equals(null));
        assertTrue(opt.equals(opt));

        // int value
        SOCGameOption op2 = SOCGameOption.getOption("PL", true);
        assertTrue(opt.equals(op2));
        assertEquals(4, opt.getIntValue());
        op2.setIntValue(5);
        assertFalse(opt.equals(op2));

        // bool value
        SOCGameOption optPLB = SOCGameOption.getOption("PLB", true);
        assertFalse(opt.equals(optPLB));
        op2 = SOCGameOption.getOption("PLB", true);
        assertFalse(op2.getBoolValue());
        assertTrue(optPLB.equals(op2));
        op2.setBoolValue(true);
        assertFalse(optPLB.equals(op2));

        // string value
        SOCGameOption optSC = SOCGameOption.getOption("SC", true);
        assertFalse(opt.equals(optSC));
        op2 = SOCGameOption.getOption("SC", true);
        assertEquals("", optSC.getStringValue());
        assertTrue(optSC.equals(op2));
        op2.setStringValue("xyz");
        assertFalse(optSC.equals(op2));
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.game.TestGameOptions");
    }

}
