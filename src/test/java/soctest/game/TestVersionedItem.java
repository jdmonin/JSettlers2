/*
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2022,2026 Jeremy D Monin <jeremy@nand.net>
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
 */

package soctest.game;

import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCVersionedItem;

/**
 * A few tests for {@link SOCVersionedItem}.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.6.00
 */
public class TestVersionedItem
{
    /**
     * Test various arguments passed into constructors.
     * @see TestGameOptions#testConstructorArgs()
     * @since 2.7.00
     */
    @Test
    public void testConstructorArgs()
    {
        // TODO test more situations in this method, such as:
        // - desc vs SOCMessage.isSingleLineAndSafe
        // - See constructor for more

        SOCGameOption opt;  // since SOCVersionedItem is abstract

        /**
         * minVersion
         */
        try
        {
            opt = new SOCGameOption
                ("ZZ", 0, 1107, false, 7, 1, 999, 0, "desc");
            fail("should have rejected minVers: " + opt);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("minVers 0 for key ZZ"));
        }
        try
        {
            opt = new SOCGameOption
                ("ZZ", 999, 1107, false, 7, 1, 999, 0, "desc");
            fail("should have rejected minVers: " + opt);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("minVers 999 for key ZZ"));
        }
        opt = new SOCGameOption
            ("ZZ", -1, 1107, false, 7, 1, 999, 0, "desc");
        assertNotNull("minVers -1 is OK", opt);
        assertEquals(-1, opt.minVersion);
        assertEquals(1107, opt.lastModVersion);
        opt = new SOCGameOption
            ("ZZ", 1000, 1107, false, 7, 1, 999, 0, "desc");
        assertNotNull("minVers 1000 is OK", opt);
        assertEquals(1000, opt.minVersion);
        assertEquals(1107, opt.lastModVersion);

        /**
         * lastModVersion
         */
        try
        {
            opt = new SOCGameOption
                ("ZZ", -1, 0, false, 7, 1, 999, 0, "desc");
            fail("should have rejected lastModVersion: " + opt);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("lastModVers 0 for key ZZ"));
        }
        try
        {
            opt = new SOCGameOption
                ("ZZ", -1, 999, false, 7, 1, 999, 0, "desc");
            fail("should have rejected lastModVersion: " + opt);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("lastModVers 999 for key ZZ"));
        }
        opt = new SOCGameOption
            ("ZZ", 1107, -1, false, 7, 1, 999, 0, "desc");
        assertNotNull("lastModVers -1 is OK", opt);
        assertEquals(1107, opt.minVersion);
        assertEquals(-1, opt.lastModVersion);
        opt = new SOCGameOption
            ("ZZ", 1107, 1000, false, 7, 1, 999, 0, "desc");
        assertNotNull("lastModVers 1000 is OK", opt);
        assertEquals(1107, opt.minVersion);
        assertEquals(1000, opt.lastModVersion);
    }

    /**
     * Test {@link SOCVersionedItem#setDesc(String)}.
     */
    @Test
    public void testSetDesc()
    {
        SOCGameOption opt = SOCGameOptionSet.getAllKnownOptions().get("VP");
        assertNotNull(opt);
        assertEquals(Integer.MAX_VALUE, opt.getSortRank());

        // too short for prefix
        opt.setDesc("x");
        assertEquals("x", opt.getDesc());
        assertEquals(Integer.MAX_VALUE, opt.getSortRank());

        // digit but no space before dash: not prefix
        opt.setDesc("6-player game:...");
        assertEquals("6-player game:...", opt.getDesc());
        assertEquals(Integer.MAX_VALUE, opt.getSortRank());

        // brackets but no digit: not prefix
        opt.setDesc("[] longer desc here");
        assertEquals("[] longer desc here", opt.getDesc());
        assertEquals(Integer.MAX_VALUE, opt.getSortRank());

        // an actual prefix
        opt.setDesc("[6] desc here");
        assertEquals("desc here", opt.getDesc());
        assertEquals(6, opt.getSortRank());

        // not a prefix: set rank back to MAX_VALUE
        opt.setDesc("6-player game:...");
        assertEquals("6-player game:...", opt.getDesc());
        assertEquals(Integer.MAX_VALUE, opt.getSortRank());

        // more formats for prefixes:

        opt.setDesc("[03] desc here");
        assertEquals("desc here", opt.getDesc());
        assertEquals(3, opt.getSortRank());

        opt.setDesc("[033] desc here");
        assertEquals("desc here", opt.getDesc());
        assertEquals(33, opt.getSortRank());

        opt.setDesc("09 - desc");
        assertEquals("desc", opt.getDesc());
        assertEquals(9, opt.getSortRank());

        opt.setDesc("3 - desc");
        assertEquals("desc", opt.getDesc());
        assertEquals(3, opt.getSortRank());

        opt.setDesc("044 - desc");
        assertEquals("desc", opt.getDesc());
        assertEquals(44, opt.getSortRank());

        opt.setDesc("\u0e52\u0e53 - desc");  // thai digit two, thai digit three
        assertEquals("desc", opt.getDesc());
        assertEquals(23, opt.getSortRank());

        opt.setDesc("[\u0e53\u0e52] desc");
        assertEquals("desc", opt.getDesc());
        assertEquals(32, opt.getSortRank());

        // badly formatted:

        final String[] badFmts =
            {
                "[3]",
                "[3 ] extra space after digit",
                "[3x] non-digit before bracket",
                "[33]no space after bracket",
                "1 -x", "1 -",
                "1 -missing space after dash"
            };
        for (final String bad : badFmts)
            try
            {
                opt.setDesc(bad);
                fail("should throw for \"" + bad + "\"");
            } catch (IllegalArgumentException e) {}
    }
}

