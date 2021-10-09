/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2021 Jeremy D Monin <jeremy@nand.net>
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

import soc.extra.server.GameEventLog;
import soc.message.SOCBuildRequest;
import soc.message.SOCMessageForGame;
import soc.message.SOCVersion;
import soc.server.SOCServer;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link GameEventLog}.
 *
 * @see TestRecorder#testNewGameFirstLogEntries()
 * @since 2.5.00
 */
public class TestGameEventLog
{

    /** Comprehensive tests for {@link GameEventLog.QueueEntry}: Constructors, toString */
    @Test
    public void testQueueEntry()
    {
        final SOCBuildRequest event = new SOCBuildRequest("testgame", 2);

        GameEventLog.QueueEntry qe = new GameEventLog.QueueEntry(event, -1, false);
        assertFalse(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("all:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new GameEventLog.QueueEntry(event, new int[]{3});
        assertFalse(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertEquals(event, qe.event);
        assertArrayEquals(new int[]{3}, qe.excludedPN);
        assertEquals("!p3:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new GameEventLog.QueueEntry(event, new int[]{2,3,4});
        assertFalse(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertEquals(event, qe.event);
        assertArrayEquals(new int[]{2,3,4}, qe.excludedPN);
        assertEquals("!p[2, 3, 4]:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new GameEventLog.QueueEntry(event, SOCServer.PN_OBSERVER, false);
        assertFalse(qe.isFromClient);
        assertEquals(SOCServer.PN_OBSERVER, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("ob:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new GameEventLog.QueueEntry(event, SOCServer.PN_REPLY_TO_UNDETERMINED, false);
        assertFalse(qe.isFromClient);
        assertEquals(SOCServer.PN_REPLY_TO_UNDETERMINED, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("un:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new GameEventLog.QueueEntry(event, 3, true);
        assertTrue(qe.isFromClient);
        assertEquals(3, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("f3:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new GameEventLog.QueueEntry(event, SOCServer.PN_OBSERVER, true);
        assertTrue(qe.isFromClient);
        assertEquals(SOCServer.PN_OBSERVER, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("fo:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        // Should use PN_OBSERVER not -1 for observer client, so make sure -1 doesn't result in "fo:" or "all:"
        qe = new GameEventLog.QueueEntry(event, -1, true);
        assertTrue(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("f-1:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());

        qe = new GameEventLog.QueueEntry(null, -1, false);
        assertFalse(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertNull(qe.event);
        assertNull(qe.excludedPN);
        assertEquals("all:null", qe.toString());

        qe = new GameEventLog.QueueEntry(null, 3, false);
        assertFalse(qe.isFromClient);
        assertEquals(3, qe.pn);
        assertNull(qe.event);
        assertNull(qe.excludedPN);
        assertEquals("p3:null", qe.toString());

        try
        {
            qe = new GameEventLog.QueueEntry(null, 3, true);
            fail("Should throw IllegalArgumentException if isFromClient and event=null");
        } catch (IllegalArgumentException e) {}

        try
        {
            final SOCVersion vmsg = new SOCVersion(1100, "1.1.00", "", null, null);
            assertFalse(vmsg instanceof SOCMessageForGame);
            qe = new GameEventLog.QueueEntry(vmsg, 3, true);
            fail("Should throw IllegalArgumentException if isFromClient and event not SOCMessageForGame");
        } catch (IllegalArgumentException e) {}
    }

}
