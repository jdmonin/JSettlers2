/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2026 Jeremy D Monin <jeremy@nand.net>
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

package soctest.message;

import soc.message.SOCChangeGameOptions;
import soc.message.SOCMessage;
import static soctest.message.TestToCmdToStringParse.MAP_SGO_UB_UBL;
import static soctest.message.TestToCmdToStringParse.sortedMapOf;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A few tests for {@link SOCChangeGameOptions}.
 * @since 2.7.00
 */
public class TestSOCChangeGameOptions
{

    /**
     * Test server constructor and value of {@link SOCChangeGameOptions#OP_REMOVE}.
     */
    @Test
    public void testConstructor()
    {
        // server constructor param checks:

        final SortedMap<String, Integer> clisMap
            = sortedMapOf(new String[]{"oldc", "oldc2"}, new int[]{2600, 2100});
        SOCChangeGameOptions msg;

        msg = new SOCChangeGameOptions
            ("ga", SOCChangeGameOptions.OP_REMOVE,
             MAP_SGO_UB_UBL, clisMap);
        assertNotNull("all-ok test", msg);

        // gaName: not null or empty
        try {
            msg = new SOCChangeGameOptions
                (null, SOCChangeGameOptions.OP_REMOVE,
                 MAP_SGO_UB_UBL, clisMap);
            fail("should disallow null gaName");
        } catch (IllegalArgumentException e) {
            assertEquals("gaName", e.getMessage());
        }
        try {
            msg = new SOCChangeGameOptions
                ("", SOCChangeGameOptions.OP_REMOVE,
                 MAP_SGO_UB_UBL, clisMap);
            fail("should disallow empty gaName");
        } catch (IllegalArgumentException e) {
            assertEquals("gaName", e.getMessage());
        }

        // operation == OP_REMOVE
        assertEquals("const hasn't changed value", 'R', SOCChangeGameOptions.OP_REMOVE);
        try {
            msg = new SOCChangeGameOptions
                ("ga", 'X',  // not "R" for OP_REMOVE
                 MAP_SGO_UB_UBL, clisMap);
            fail("should disallow op != OP_REMOVE");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("unknown op"));
        }

        // optsChanged: not null or empty
        try {
            msg = new SOCChangeGameOptions
                ("ga", SOCChangeGameOptions.OP_REMOVE,
                 null, clisMap);
            fail("should disallow null optsChanged");
        } catch (IllegalArgumentException e) {
            assertEquals("optsChanged", e.getMessage());
        }
        try {
            msg = new SOCChangeGameOptions
                ("ga", SOCChangeGameOptions.OP_REMOVE,
                 new TreeMap<>(), clisMap);
            fail("should disallow empty optsChanged");
        } catch (IllegalArgumentException e) {
            assertEquals("optsChanged", e.getMessage());
        }

        // changeCauseClientNamesVersions: can be null but not empty
        msg = new SOCChangeGameOptions
            ("ga", SOCChangeGameOptions.OP_REMOVE,
             MAP_SGO_UB_UBL, null);
        assertNotNull("null changeCauseClientNamesVersions", msg);
        try
        {
            msg = new SOCChangeGameOptions
                ("ga", SOCChangeGameOptions.OP_REMOVE,
                 MAP_SGO_UB_UBL, new TreeMap<>());
            fail("should disallow empty changeCauseClientNamesVersions");
        } catch (IllegalArgumentException e) {
            assertEquals("changeCauseClientNamesVersions", e.getMessage());
        }
    }

    /**
     * Test {@link SOCChangeGameOptions#parseDataStr(List)}.
     */
    @Test
    public void testParseDataStr()
    {
        SOCChangeGameOptions msg;

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "O 1", "UB")));
        assertNotNull("all-ok test", msg);
        assertEquals("ga", msg.getGame());
        assertEquals(SOCChangeGameOptions.OP_REMOVE, msg.operation);
        List<String> optsChanges = msg.optsChanges;
        assertNotNull(optsChanges);
        assertEquals(1, optsChanges.size());
        assertEquals("UB", optsChanges.get(0));
        assertNull(msg.changeCauseClientNamesVersions);

        // with changeCauseClientNamesVersions sublist too
        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "O 1", "UB", "C 4", "cname", "2600", "c2", "1201")));
        assertNotNull("with optional client list", msg);
        assertEquals("ga", msg.getGame());
        assertEquals(SOCChangeGameOptions.OP_REMOVE, msg.operation);
        optsChanges = msg.optsChanges;
        assertNotNull(optsChanges);
        assertEquals(1, optsChanges.size());
        assertEquals("UB", optsChanges.get(0));
        List<String> cliNamesVers = msg.changeCauseClientNamesVersions;
        assertNotNull(cliNamesVers);
        assertEquals(4, cliNamesVers.size());
        assertEquals(Arrays.asList("cname", "2600", "c2", "1201"), cliNamesVers);

        // sublists in other order is fine too
        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "C 4", "cname", "2600", "c2", "1201", "O 1", "UB")));
        assertNotNull("with optional client list", msg);
        assertEquals("ga", msg.getGame());
        assertEquals(SOCChangeGameOptions.OP_REMOVE, msg.operation);
        optsChanges = msg.optsChanges;
        assertNotNull(optsChanges);
        assertEquals(1, optsChanges.size());
        assertEquals("UB", optsChanges.get(0));
        cliNamesVers = msg.changeCauseClientNamesVersions;
        assertNotNull(cliNamesVers);
        assertEquals(4, cliNamesVers.size());
        assertEquals(Arrays.asList("cname", "2600", "c2", "1201"), cliNamesVers);

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "C 4", "cname", "2600", "c2", "1201")));
        assertNull("optsChanges is required", msg);

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "C 4", "cname", "2600", "c2", "1201", "O 1", "UB", "O 1", "UX")));
        assertNull("can't dupe optsChanges", msg);

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "C 4", "cname", "2600", "c2", "1201", "C 4", "cname3", "2600", "c4", "1201", "O 1", "UB")));
        assertNull("can't dupe changeCauseClientNamesVersion", msg);

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList(SOCMessage.EMPTYSTR, "R", "O 1", "UB")));
        assertNull("empty gaName", msg);

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "X", "O 1", "UB")));
        assertNull("unknown operation", msg);

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "O -1", "UB")));
        assertNull("bad sublist length", msg);

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "O1", "UB")));
        assertNull("bad sublist length fmt", msg);

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "O x", "UB")));
        assertNull("bad sublist length char", msg);

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "O 3", "UB")));
        assertNull("sublist incomplete", msg);

        msg = SOCChangeGameOptions.parseDataStr
            (new ArrayList<>(Arrays.asList("ga", "R", "O 0")));
        assertNull("length < 4", msg);
    }
}
