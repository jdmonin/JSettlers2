/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2018-2019 Jeremy D Monin <jeremy@nand.net>
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

package soctest.util;

import soc.util.SOCFeatureSet;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link SOCFeatureSet}.
 * @since 2.0.00
 */
public class TestFeatureSet
{
    @Test
    public void testFromEmpty()
    {
        final String[] encodedEmpty = { "", ";", ";;", null };
        for (String fsStr : encodedEmpty)
        {
            SOCFeatureSet fs = new SOCFeatureSet(fsStr);
            if ((fsStr == null) || (fsStr.length() < 1))
                assertNull(fs.getEncodedList());
            assertFalse(fs.isActive("xyz"));
            assertEquals(0, fs.getValue("xyz", 0));

            fs.add("xyz");
            fs.add("xyz2", 1);
            assertTrue(fs.isActive("xyz"));
            assertEquals(1, fs.getValue("xyz2", 0));

            final String encoded = fs.getEncodedList();
            assertTrue(encoded.equals
                (((fsStr == null) || (fsStr.length() < 2)) ? ";xyz;xyz2=1;" : ";;xyz;xyz2=1;"));

            SOCFeatureSet fsCopy = new SOCFeatureSet(fs);
            assertTrue(fsCopy.getEncodedList().equals(encoded));
        }
    }

    @Test
    public void testReads()
    {
        final SOCFeatureSet fs = new SOCFeatureSet(";xyz;abc=-5;");

        assertFalse(fs.isActive("xy"));
        assertTrue(fs.isActive("xyz"));

        assertFalse(fs.isActive("ab"));
        assertTrue(fs.isActive("abc"));
        assertEquals(-5, fs.getValue("abc", 0));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMalformedPrefix()
    {
        @SuppressWarnings("unused")
        final SOCFeatureSet fs = new SOCFeatureSet("xyz;abc=-5;");

        fail("should have thrown exception");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMalformedSuffix()
    {
        @SuppressWarnings("unused")
        final SOCFeatureSet fs = new SOCFeatureSet(";xyz;abc=-5");

        fail("should have thrown exception");
    }

    @Test
    public void testMissingValue()
    {
        SOCFeatureSet fs = new SOCFeatureSet(";abc=;");
        assertTrue(fs.isActive("abc"));
        assertEquals(77, fs.getValue("abc", 77));
    }

    @Test
    public void testMalformedValue()
    {
        SOCFeatureSet fs = new SOCFeatureSet(";abc=not_a.number;");
        assertTrue(fs.isActive("abc"));
        assertEquals(77, fs.getValue("abc", 77));
    }

    @Test
    public void testMalformedName()
    {
        final String[] bads = { null, "", "x=", "x" + SOCFeatureSet.SEP_CHAR, "=x", SOCFeatureSet.SEP_CHAR + "x" };
        final SOCFeatureSet fs = new SOCFeatureSet((String) null);

        for (String s : bads)
        {
            try
            {
                fs.add(s);
                fail("add(s) should have thrown exception for name: " + s);
            }
            catch (IllegalArgumentException e) {}

            try
            {
                fs.add(s, 1);
                fail("add(s,1) should have thrown exception for name: " + s);
            }
            catch (IllegalArgumentException e) {}
        }
    }

    @Test
    public void testClientOldDefaults()
    {
        SOCFeatureSet fs = new SOCFeatureSet(true, false);
        assertTrue(fs.isActive(SOCFeatureSet.CLIENT_6_PLAYERS));
        assertTrue(fs.getEncodedList().equals(';' + SOCFeatureSet.CLIENT_6_PLAYERS + ';'));
    }

    @Test
    public void testServerOldDefaults()
    {
        SOCFeatureSet fs = new SOCFeatureSet(true, true);
        assertTrue(fs.isActive(SOCFeatureSet.SERVER_ACCOUNTS));
        assertTrue(fs.isActive(SOCFeatureSet.SERVER_CHANNELS));
        assertTrue(fs.isActive(SOCFeatureSet.SERVER_OPEN_REG));
        assertTrue(fs.getEncodedList().equals
            (';' + SOCFeatureSet.SERVER_ACCOUNTS + ';' + SOCFeatureSet.SERVER_CHANNELS
             + ';' + SOCFeatureSet.SERVER_OPEN_REG + ';'));
    }

    @Test
    public void testWithoutOldDefaults()
    {
        SOCFeatureSet fs = new SOCFeatureSet(false, false);
        assertNull(fs.getEncodedList());

        fs = new SOCFeatureSet(false, true);  // should ignore 2nd param
        assertNull(fs.getEncodedList());
    }

    @Test
    public void testFindMissingAgainst()
    {
        final String[] emptys = { null, "", ";", ";;" };

        // no features in self and subset
        for (String selfStr : emptys)
        {
            SOCFeatureSet fs = new SOCFeatureSet(selfStr);
            for (String subStr : emptys)
                assertNull(fs.findMissingAgainst(new SOCFeatureSet(subStr), false));
            assertNotNull(fs.findMissingAgainst(new SOCFeatureSet(";xyz;"), false));
        }

        // single feature, test overall parsing
        SOCFeatureSet fs = new SOCFeatureSet(";xyz=2002;");
        assertNull(fs.findMissingAgainst(null, false));
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(""), false));
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";"), false));
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";;"), false));
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";xyz;"), false));  // subset's feature as boolean without int value
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";xyz=;"), false));  // almost malformed: should parse no-value as 0
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";xyz=0;"), false));
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";xyz=-5;"), false));  // negative value
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";xyz=2001;"), false));  // lower value
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";xyz=2002;"), false));  // same value
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";xyz=2003;"), false), "xyz=2003");  // higher value
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";xyz=n;"), false), "xyz");  // value purposefully malformed
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";=7;"), false), "?");  // purposefully malformed

        // more negative-value tests
        fs = new SOCFeatureSet(";abc=-5;");
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";abc=-6;"), false));
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";abc=-5;"), false));
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";abc=-4;"), false), "abc=-4");
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";abc=0;"), false), "abc=0");

        // multiple features
        fs = new SOCFeatureSet(";a;b;cd=11;");
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";a;"), false));
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";a=7;"), false), "a=7");  // subset's feature has int value, but ours is boolean
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";a;b;"), false));
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";b;a;"), false));
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";b;a;f;"), false), "f");  // f not in our set
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";cd;a;b;"), false));  // subset's feature as boolean without int value
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";cd=7;b;a;"), false));  // lower value
        assertNull(fs.findMissingAgainst(new SOCFeatureSet(";cd=11;b;a;"), false));  // same value
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";cd=12;b;a;"), false), "cd=12");  // higher value

        // multiple missing features
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";f;a;cz;"), false), "f;cz");
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";cd=12;f;a;"), false), "cd=12;f");  // value differs

        // stopAtFirstFound, using same test cases as "multiple missing" above
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";f;a;cz;"), true), "f");
        assertEquals(fs.findMissingAgainst(new SOCFeatureSet(";cd=12;f;a;"), true), "cd=12");  // value differs
    }

    /** Constants are sent between servers and clients, so their values shouldn't change between versions. */
    @Test
    public void testConstantsUnchanged()
    {
        assertTrue(SOCFeatureSet.CLIENT_6_PLAYERS.equals("6pl"));
        assertTrue(SOCFeatureSet.CLIENT_SEA_BOARD.equals("sb"));
        assertTrue(SOCFeatureSet.CLIENT_SCENARIO_VERSION.equals("sc"));

        assertTrue(SOCFeatureSet.SERVER_ACCOUNTS.equals("accts"));
        assertTrue(SOCFeatureSet.SERVER_CHANNELS.equals("ch"));
        assertTrue(SOCFeatureSet.SERVER_OPEN_REG.equals("oreg"));
    }

}
