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
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCBoardLarge;
import soc.game.SOCGameOption;
import soc.game.SOCVersionedItem;

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
     * are consistent internally and with each other. Each option's map key must be its option key.
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
        }
    }

    /**
     * Test methods that ensure v2 client is always sent {@code SBL=t} from v3 or higher server:
     * Server calls {@link SOCGameOption#optionsMinimumVersion(Map, boolean)},
     * message classes call {@link SOCGameOption#packOptionsToString(Map, boolean, int)}.
     * @since 3.0.00
     */
    @Test
    public void testCliV2GetsSBL()
    {
        assertEquals(3000, SOCBoardLarge.VERSION_FOR_ALSO_CLASSIC);

        final Map<String, SOCGameOption> opts = new HashMap<String, SOCGameOption>();

        // null map, sent to v3 client: no change
        assertEquals(3000, SOCGameOption.optionsMinimumVersion(null, true));
        String ostr = SOCGameOption.packOptionsToString(null, false, false, 3000);
        assertFalse(ostr.contains("SBL="));

        // null map, sent to v2 client: add, set true
        ostr = SOCGameOption.packOptionsToString(null, false, false, 2999);
        assertTrue(ostr.contains("SBL=t"));

        // empty map, sent to v3 client: no change
        assertEquals(3000, SOCGameOption.optionsMinimumVersion(opts, true));
        ostr = SOCGameOption.packOptionsToString(opts, false, false, 3000);
        assertFalse(ostr.contains("SBL="));

        // empty map, sent to v2 client: add, set true
        ostr = SOCGameOption.packOptionsToString(opts, false, false, 2999);
        assertTrue(ostr.contains("SBL=t"));

        // map without SBL, sent to v3 client: no change
        final SOCGameOption optNT = SOCGameOption.getOption("NT", true);  // any non-SBL opt
        optNT.setBoolValue(true);
        opts.put("NT", optNT);
        assertEquals(3000, SOCGameOption.optionsMinimumVersion(opts, true));
        ostr = SOCGameOption.packOptionsToString(opts, false, false, 3000);
        assertFalse(ostr.contains("SBL="));

        // map without SBL, sent to v2 client: add, set true
        ostr = SOCGameOption.packOptionsToString(opts, false, false, 2999);
        assertTrue(ostr.contains("SBL=t"));

        // map with SBL false, sent to v3 client: no change
        final SOCGameOption optSBL = SOCGameOption.getOption("SBL", true);
        optSBL.setBoolValue(false);
        opts.put("SBL", optSBL);
        assertEquals(3000, SOCGameOption.optionsMinimumVersion(opts, true));
        ostr = SOCGameOption.packOptionsToString(opts, false, false, 3000);
        assertTrue(ostr.contains("SBL=f"));

        // map with SBL false, sent to v2 client: set true
        ostr = SOCGameOption.packOptionsToString(opts, false, false, 2999);
        assertTrue(ostr.contains("SBL=t"));

        // map with SBL true, sent to v3 client: no change
        optSBL.setBoolValue(true);
        assertEquals(SOCBoardLarge.MIN_VERSION, SOCGameOption.optionsMinimumVersion(opts, true));
        ostr = SOCGameOption.packOptionsToString(opts, false, false, 3000);
        assertTrue(ostr.contains("SBL=t"));

        // map with SBL true, sent to v2 client: no change
        ostr = SOCGameOption.packOptionsToString(opts, false, false, 2999);
        assertTrue(ostr.contains("SBL=t"));
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
