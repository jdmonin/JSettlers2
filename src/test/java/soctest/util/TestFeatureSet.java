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

package soctest.util;

import soc.util.SOCFeatureSet;
import org.junit.Test;
import static org.junit.Assert.*;

/** Tests for {@link SOCFeatureSet}. */
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

}
