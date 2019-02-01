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

package soctest.server;

import java.util.List;

import soc.server.SOCChatRecentBuffer;
import soc.server.SOCChatRecentBuffer.Entry;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Full set of unit tests for {@link SOCChatRecentBuffer}.
 */
public class TestSOCChatRecentBuffer
{
    private void addSeqEntry(final SOCChatRecentBuffer b, final int i)
    {
        final String istr = Integer.toString(i);
        b.add(istr, "t" + istr);
    }

    private Entry newSeqEntry(final int i)
    {
        final String istr = Integer.toString(i);
        return new Entry(istr, "t" + istr);
    }

    /** Test our internal methods */
    @Test
    public void testTestMethods()
    {
        final Entry e5 = newSeqEntry(5);
        assertTrue(e5.nickname.equals(Integer.toString(5)));
        assertTrue(e5.text.equals("t" + Integer.toString(5)));

        final SOCChatRecentBuffer b = new SOCChatRecentBuffer();
        addSeqEntry(b, 3);
        final Entry e3 = b.getAll().get(0);
        assertTrue(e3.nickname.equals(Integer.toString(3)));
        assertTrue(e3.text.equals("t" + Integer.toString(3)));
        assertTrue(e3.equals(newSeqEntry(3)));
    }

    /** Tests for {@link SOCChatRecentBuffer.Entry#equals(Object)} */
    @Test
    public void testEntryEquals()
    {
        Entry e = new Entry("n", "t");
        assertFalse(e.equals(null));
        assertFalse(e.equals(new String("n")));
        assertFalse(e.equals(new Entry("nn", "t")));
        assertFalse(e.equals(new Entry("n", "tt")));
        assertTrue(e.equals(new Entry("n", "t")));
    }

    /**
     * Passing a null nickname to the {@link Entry} constructor should throw
     * {@link IllegalArgumentException}, not {@link NullPointerException}
     */
    @Test(expected=IllegalArgumentException.class)
    public void testEntryNullName()
    {
        Entry e = new Entry(null, "t");  // should throw here
        assertNotNull(e);  // won't reach this code; is here to ensure constructor is called
    }

    /**
     * Passing null text to the {@link Entry} constructor should throw
     * {@link IllegalArgumentException}, not {@link NullPointerException}
     */
    @Test(expected=IllegalArgumentException.class)
    public void testEntryNullText()
    {
        Entry e = new Entry("n", null);  // should throw here
        assertNotNull(e);  // won't reach this code; is here to ensure constructor is called
    }

    /** Tests for initial fill (not wraparound). */
    @Test
    public void testInitial()
    {
        SOCChatRecentBuffer b = new SOCChatRecentBuffer();

        assertTrue(b.isEmpty());
        assertTrue(b.getAll().isEmpty());

        addSeqEntry(b, 1);   // same as: b.add("1", "t1")
        assertFalse(b.isEmpty());
        List<Entry> allEntries = b.getAll();
        assertEquals(allEntries.size(), 1);
        assertTrue(allEntries.get(0).equals(newSeqEntry(1)));

        for (int i = 2; i <= SOCChatRecentBuffer.BUFFER_SIZE; ++i)
        {
            addSeqEntry(b, i);  // b.add("2" "t2") etc
            assertFalse(b.isEmpty());
            allEntries = b.getAll();  // oldest to newest: "1", "2", "3" ... i
            assertEquals(allEntries.size(), i);
            for (int j = 1; j < allEntries.size(); ++j)
                assertTrue(allEntries.get(j - 1).equals(newSeqEntry(j)));
        }

        // Clear the same buffer, give it 2 items

        b.clear();
        assertTrue(b.isEmpty());
        assertTrue(b.getAll().isEmpty());

        addSeqEntry(b, 91);
        assertFalse(b.isEmpty());
        allEntries = b.getAll();
        assertEquals(allEntries.size(), 1);
        assertTrue(allEntries.get(0).equals(newSeqEntry(91)));

        addSeqEntry(b, 92);
        allEntries = b.getAll();
        assertEquals(allEntries.size(), 2);
        assertTrue(allEntries.get(0).equals(newSeqEntry(91)));
        assertTrue(allEntries.get(1).equals(newSeqEntry(92)));
    }

    /** Tests for wraparound and dropping old entries. */
    @Test
    public void testWraparound()
    {
        final int BUFFER_SIZE = SOCChatRecentBuffer.BUFFER_SIZE;
        SOCChatRecentBuffer b = new SOCChatRecentBuffer();

        for (int i = 1; i <= BUFFER_SIZE; ++i)
            addSeqEntry(b, i);  // b.add("2" "t2") etc

        List<Entry> allEntries = b.getAll();  // oldest to newest: "1", "2", "3" ... BUFFER_SIZE
        assertEquals(allEntries.size(), BUFFER_SIZE);
        assertTrue(allEntries.get(0).equals(newSeqEntry(1)));
        assertTrue(allEntries.get(BUFFER_SIZE - 1).equals(newSeqEntry(BUFFER_SIZE)));

        // Now it's full, we have known conditions to test wraparound

        for (int i = BUFFER_SIZE + 1; i <= (4 * BUFFER_SIZE + 5); ++i)
        {
            addSeqEntry(b, i);  // b.add("17" "t17") etc
            allEntries = b.getAll();
            assertEquals(allEntries.size(), BUFFER_SIZE);
            assertTrue(allEntries.get(0).equals(newSeqEntry(i - BUFFER_SIZE + 1)));  // oldest
            assertTrue(allEntries.get(BUFFER_SIZE - 1).equals(newSeqEntry(i)));      // newest
        }
    }

}
