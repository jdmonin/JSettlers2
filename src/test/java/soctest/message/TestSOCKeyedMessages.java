/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2025 Jeremy D Monin <jeremy@nand.net>
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

import org.junit.Test;
import static org.junit.Assert.*;

import soc.message.SOCKeyedMessage;  // for jdocs
import soc.message.SOCMessage;
import soc.message.SOCUndoNotAllowedReasonText;

/**
 * A few tests for some {@link SOCKeyedMessage} classes
 * which aren't part of the main list tested in {@link TestToCmdToStringParse}.
 *
 * @since 2.7.00
 */
public class TestSOCKeyedMessages
{
    /**
     * Tests for {@link SOCUndoNotAllowedReasonText}.
     */
    @Test
    public void testSOCUndoNotAllowedReasonText()
    {
        SOCUndoNotAllowedReasonText msg = new SOCUndoNotAllowedReasonText("ga", false, null);
        assertNull("null reason is ok in this constructor", msg.reason);
        assertNull(msg.getKey());
        assertEquals("ga", msg.game);
        assertFalse(msg.isLocalized);
        assertFalse(msg.isNotAllowed);

        msg = new SOCUndoNotAllowedReasonText("ga", true, "rcode");
        assertTrue(msg.isNotAllowed);
        assertEquals("rcode", msg.reason);
        assertEquals("rcode", msg.getKey());

        try
        {
            @SuppressWarnings("unused")
            SOCUndoNotAllowedReasonText m = new SOCUndoNotAllowedReasonText("ga", false, "");
            fail("constructor should reject empty reasonText");
        } catch (IllegalArgumentException e) {}

        try
        {
            @SuppressWarnings("unused")
            SOCUndoNotAllowedReasonText m = new SOCUndoNotAllowedReasonText("ga", false, "not\nsingle\nline");
            fail("constructor should reject reasonText which fails isSingleLineAndSafe");
        } catch (IllegalArgumentException e) {}

        msg = new SOCUndoNotAllowedReasonText("ga", true, null, true);
        assertEquals("ga", msg.game);
        assertTrue(msg.isLocalized);
        assertTrue(msg.isNotAllowed);
        assertNull("null reason ok in this constructor", msg.reason);
        assertNull("null reason ok in this constructor", msg.getKey());

        msg = new SOCUndoNotAllowedReasonText("ga", false, "rcode", false);
        assertFalse(msg.isLocalized);
        assertFalse(msg.isNotAllowed);
        assertEquals("rcode", msg.reason);
        assertEquals("rcode", msg.getKey());

        SOCMessage msgLocal = msg.localize("rlocal");
        assertTrue(msgLocal instanceof SOCUndoNotAllowedReasonText);
        assertTrue(((SOCUndoNotAllowedReasonText) msgLocal).isLocalized);
        assertEquals("ga", ((SOCUndoNotAllowedReasonText) msgLocal).game);
        assertFalse(msg.isNotAllowed);
        assertEquals("rlocal", ((SOCUndoNotAllowedReasonText) msgLocal).reason);
        assertEquals("rlocal", ((SOCUndoNotAllowedReasonText) msgLocal).getKey());
    }

}
