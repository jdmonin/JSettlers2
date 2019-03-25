/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
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

import java.util.List;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCSpecialItem;
import soc.game.SOCSpecialItem.Requirement;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCSpecialItem} and {@link SOCSpecialItem.Requirement}.
 */
public class TestSpecialItem
{
    /** Tests for {@link SOCSpecialItem#makeKnownItem(String, int)} */
    @Test
    public void testMakeKnownItem()
    {
        SOCSpecialItem itm;

        // SC_WOND:

        for (int i = 1; i <= SOCGame.MAXPLAYERS + 1; ++i)
        {
            itm = SOCSpecialItem.makeKnownItem(SOCGameOption.K_SC_WOND, i);
            assertNotNull(itm);
            assertNotNull(itm.getCost());
            assertNotNull(itm.req);
        }
        // past end of defined item indexes:
        itm = SOCSpecialItem.makeKnownItem(SOCGameOption.K_SC_WOND, SOCGame.MAXPLAYERS + 2);
        assertNotNull(itm);
        assertNull(itm.getCost());
        assertNull(itm.req);

        // unknown scenario/typeKey:

        itm = SOCSpecialItem.makeKnownItem("???", 1);
        assertNotNull(itm);
        assertNull(itm.getCost());
        assertNull(itm.req);
    }

    /**
     * Test {@link Requirement#Requirement(char, int, boolean, String)} constructor
     * rejects {@code atPort} and {@code atCoordList} if both are supplied.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testRequirementNotPortAndLoc()
    {
        Requirement r = new Requirement('C', 1, true, "N1");
        assertNotNull(r);  // prevent compiler optimize away; actually expect to throw, not to get here
    }

    /**
     * Test {@link Requirement#Requirement(char, int, boolean, String)} constructor
     * currently rejects {@code atPort} for type 'S' (Settlement).
     */
    @Test(expected=IllegalArgumentException.class)
    public void testRequirementNotSAtPort()
    {
        Requirement r = new Requirement('S', 1, true, null);
        assertNotNull(r);  // prevent compiler optimize away; actually expect to throw, not to get here
    }

    /**
     * Test that {@link SOCSpecialItem.Requirement#parse(String)} throws exception on badly formatted item.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testRequirementParseThrowsExcep()
    {
        List<Requirement> rlist = Requirement.parse(",");
        assertNotNull(rlist);  // prevent compiler optimize away; expect to throw
    }

    /**
     * Test all known kinds of bad-spec parsing by {@link SOCSpecialItem.Requirement#parse(String)}.
     */
    @Test
    public void testRequirementParseBad()
    {
        final String[] tests =
        {
            ",",   // just comma
            "_",   // not an item-type letter
            "2_",  // not an item-type letter
            "2",   // missing item type
            "2C@",  // missing loc afterwards
            "2C,",  // missing another req afterwards
            "2CP",  // not @
            "2C=",  // not @
            "C@N",  // missing #
            "C@N,",
            "C@Nx",  // missing #
        };

        for (final String reqSpec : tests)
        {
            boolean gotExcep = false;
            try
            {
                List<Requirement> rlist = Requirement.parse(reqSpec);
                assertNotNull(rlist);  // prevent compiler optimize away; expect to throw
            }
            catch (IllegalArgumentException e) {
                gotExcep = true;
            }

            assertTrue("Expected Requirement.parse exception for \"" + reqSpec + "\"", gotExcep);
        }
    }

    /**
     * Test good parsing, including {@code SC_WOND}'s known items, by {@link SOCSpecialItem.Requirement#parse(String)}.
     */
    @Test
    public void testRequirementParseGood()
    {
        assertNull(Requirement.parse(""));

        final Object[] tests =
        {
            "2C",     new Requirement('C', 2, false, null),
            "C@P,5L", new Requirement[]{ new Requirement('C', 1, true, null), new Requirement('L', 5, false, null) },
            "S@N1",   new Requirement('S', 1, false, "N1"),
            "S@N2",   new Requirement('S', 1, false, "N2"),
            "C,6V",   new Requirement[]{ new Requirement('C', 1, false, null), new Requirement('V', 6, false, null) },
        };
        // these test cases include all contents of SOCSpecialItem.REQ_SC_WOND

        for (int i = 0; i < tests.length; )
        {
            final String reqSpec = (String) tests[i];
            ++i;
            List<Requirement> rlist = Requirement.parse(reqSpec);
            assertNotNull("Expected not-null: " + reqSpec, rlist);
            assertTrue("Expected non-empty: " + reqSpec, ! rlist.isEmpty());

            int L = rlist.size();
            if (tests[i] instanceof Requirement)
            {
                assertEquals("Expected 1 requirement from: " + reqSpec, 1, L);
                assertEquals((Requirement) tests[i], rlist.get(0));
            }
            else if (tests[i] instanceof Requirement[])
            {
                final Requirement[] expected = (Requirement[]) tests[i];
                assertEquals("Wrong # requirements from: " + reqSpec, expected.length, rlist.size());
                for (int j = 0; j < expected.length; ++j)
                    assertEquals(expected[j], rlist.get(j));
            }
            else
            {
                fail("Internal error: bad test content: " + tests[i]);
            }
            ++i;
        }
    }

    /** Tests for {@link Requirement#equals(Object)} */
    @Test
    public void testRequirementEquals()
    {
        final Requirement r1S = new Requirement('S', 1, false, null);
        assertFalse(r1S.equals(null));
        assertFalse(r1S.equals("String"));
        assertEquals(r1S, new Requirement('S', 1, false, null));
        assertFalse(r1S.equals(new Requirement('C', 1, false, null)));
        assertFalse(r1S.equals(new Requirement('S', 2, false, null)));
        // (can't currently construct 'S' with atPort; see testRequirementNotSAtPort)
        assertFalse(r1S.equals(new Requirement('S', 1, false, "N2")));

        final Requirement r2CP = new Requirement('C', 2, true, null);
        assertFalse(r1S.equals(r2CP));
        assertEquals(r2CP, new Requirement('C', 2, true, null));
        assertFalse(r2CP.equals(new Requirement('C', 2, false, null)));

        final Requirement r2SLoc = new Requirement('S', 2, false, "N1");
        assertFalse(r2CP.equals(r2SLoc));
        assertEquals(r2SLoc, new Requirement('S', 2, false, "N1"));
        assertFalse(r2SLoc.equals(new Requirement('S', 2, false, "N2")));
        assertFalse(r2SLoc.equals(new Requirement('S', 2, false, null)));
    }

}
