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

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCDevCardConstants;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCVersionedItem;
import soc.util.Version;

/**
 * Tests for {@link SOCGameOption} and {@link SOCGameOptionSet}.
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
     * Test that contents of {@link SOCGameOptionSet#getAllKnownOptions()} are consistent internally.
     *<UL>
     * <LI> Key must not start with {@code "3"} or {@code "_3"} unless option has {@link SOCGameOption#FLAG_3RD_PARTY}
     *</UL>
     */
    @Test
    public void testKnownOptionMapKeysConsistent()
    {
        for (SOCGameOption opt : knownOpts)
        {
            final String okey = opt.key;
            boolean named3p = okey.startsWith("3") || okey.startsWith("_3");
            assertEquals
                ("key " + okey + " starts with 3 only if FLAG_3RD_PARTY",
                 named3p, opt.hasFlag(SOCGameOption.FLAG_3RD_PARTY));
        }
    }

    /**
     * Test that when the client sends a new-game request whose opts contains {@code "VP"} with boolean part false,
     * that {@code "VP"} will be removed from the set of options by
     * {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean) newOpts.adjustOptionsToKnown(knownOpts, true)}.
     */
    @Test
    public void testServerRemovesFalseVP()
    {
        final SOCGameOptionSet newOpts = new SOCGameOptionSet(),
            testFewKnownOpts = new SOCGameOptionSet();

        final SOCGameOption optVP = knownOpts.getKnownOption("VP", true);
        optVP.setIntValue(12);
        optVP.setBoolValue(false);

        // should remove VP when false and not present at "server" with a default
        newOpts.put(optVP);
        StringBuilder sb = newOpts.adjustOptionsToKnown(testFewKnownOpts, true);
        assertNull(sb);
        assertFalse(newOpts.containsKey("VP"));

        // should remove VP when false and is present at "server" with a default
        {
            final SOCGameOption srvVP = knownOpts.getKnownOption("VP", true);
            srvVP.setIntValue(13);
            srvVP.setBoolValue(true);
            testFewKnownOpts.put(srvVP);
        }
        newOpts.put(optVP);
        sb = newOpts.adjustOptionsToKnown(testFewKnownOpts, true);
        assertNull(sb);
        assertFalse(newOpts.containsKey("VP"));

        // shouldn't remove if VP is true
        optVP.setBoolValue(true);
        newOpts.put(optVP);
        sb = newOpts.adjustOptionsToKnown(testFewKnownOpts, true);
        assertNull(sb);
        assertTrue(newOpts.containsKey("VP"));
    }

    /**
     * Test adding a new known option and removing it.
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

        // cleanup/remove known opt, by adding unknown opt
        hadNoOld = knowns.addKnownOption(new SOCGameOption("_TESTF"));
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
        newGameOpts.adjustOptionsToKnown(knowns, true);
        assertNull(newGameOpts.get("_TESTF"));

        // cleanup
        knowns.addKnownOption(new SOCGameOption("_TESTF"));
        assertNull(knowns.getKnownOption("_TESTF", false));
    }

    /**
     * Inactive/activated gameopts: Test {@link SOCGameOptionSet#activate(String)},
     * {@link SOCGameOptionSet#optionsWithFlag(int, int)}.
     * Test {@link SOCGameOptionSet#optionsForVersion(int)} and
     * {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean) gameOpts.adjustOptionsToKnown(knownOpts, doServerPreadjust=true)}
     * checks for {@link SOCGameOption#FLAG_INACTIVE_HIDDEN}. Uses game options
     * {@link SOCGameOptionSet#K_PLAY_FO "PLAY_FO"}, {@link SOCGameOptionSet#K_PLAY_VPO "PLAY_VPO"}.
     * @since 2.4.10
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

        SOCGameOptionSet activatedOpts = knowns.optionsWithFlag(SOCGameOption.FLAG_ACTIVATED, 2410);
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
        StringBuilder optProblems = newGameReqOpts.adjustOptionsToKnown(knowns, true);
        assertNotNull(optProblems);
        assertTrue(optProblems.toString().contains("PLAY_VPO: inactive"));

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

        activatedOpts = knowns.optionsWithFlag(SOCGameOption.FLAG_ACTIVATED, 2410);
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
        optProblems = newGameReqOpts.adjustOptionsToKnown(knowns, true);
        assertNull(optProblems);

        // cleanup
        knowns.addKnownOption(new SOCGameOption("_TESTACT"));
        assertNull(knowns.getKnownOption("_TESTACT", false));
    }

    /**
     * Test {@link SOCGameOptionSet#activate(String)} when known option not found.
     * @since 2.4.10
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFlagInactiveActivate_notFound()
    {
        assertNull(knownOpts.getKnownOption("_NONEXISTENT", false));
        knownOpts.activate("_NONEXISTENT");
    }

    /**
     * Test {@link SOCGameOptionSet#activate(String)} when known option not inactive.
     * @since 2.4.10
     */
    @Test(expected=IllegalArgumentException.class)
    public void testFlagInactiveActivate_notInactive()
    {
        knownOpts.activate("PL");
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
     * Test {@link SOCGameOptionSet#optionsNewerThanVersion(int, boolean, boolean)}.
     * Currently client-side functions only: checkValues=false, trimEnums=false.
     * Also tests {@link SOCGameOption#FLAG_3RD_PARTY} and its interaction at client
     * with {@code optionsNewerThanVersion(..)}.
     * @since 2.4.10
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
        assertNull(knowns.getKnownOption("T3P", false));
        knowns.addKnownOption(opt3PKnown);
        assertNotNull(knowns.getKnownOption("T3P", false));

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
                && ! opt.hasFlag(SOCGameOption.FLAG_INACTIVE_HIDDEN))
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

        // client-side test FLAG-3RD_PARTY
        opt3PKnown = new SOCGameOption
            ("T3P", -1, 1107, 0, 0, 0xFFFF, SOCGameOption.FLAG_3RD_PARTY, "For unit test");
        opt3PKnown.setClientFeature("com.example.js.test3p");
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
        knowns.addKnownOption(new SOCGameOption("T3P"));
        assertNull(knowns.getKnownOption("T3P", false));

        // TODO server-side tests too: call w/ (cliVers, false, true, null) etc
    }

    /**
     * Test {@link SOCGameOption#equals(Object)}.
     * @since 2.4.10
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

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.game.TestGameOptions");
    }

}
