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

import soc.message.SOCUndoPutPiece;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for message constructors and their parameters.
 * @since 2.7.00
 */
public class TestConstructorParams
{

    /**
     * Test parameters to both constructors of {@link SOCUndoPutPiece}.
     */
    @Test
    public void testSOCUndoPutPiece()
    {
        SOCUndoPutPiece msg;

        msg = new SOCUndoPutPiece("ga", 3, 1, 0x11);
        assertNotNull("all-ok undo-put constructor", msg);
        msg = new SOCUndoPutPiece("ga", 3, 1, 0x11, 0x22);
        assertNotNull("all-ok undo-move constructor", msg);

        // gaName:

        try {
            msg = new SOCUndoPutPiece(null, 3, 1, 0x11);
            fail("should disallow null gaName");
        } catch (IllegalArgumentException e) {
            assertEquals("gaName", e.getMessage());
        }
        try {
            msg = new SOCUndoPutPiece(null, 3, 1, 0x11, 0x22);
            fail("should disallow null gaName");
        } catch (IllegalArgumentException e) {
            assertEquals("gaName", e.getMessage());
        }
        try {
            msg = new SOCUndoPutPiece("", 3, 1, 0x11);
            fail("should disallow empty gaName");
        } catch (IllegalArgumentException e) {
            assertEquals("gaName", e.getMessage());
        }
        try {
            msg = new SOCUndoPutPiece("", 3, 1, 0x11, 0x22);
            fail("should disallow empty gaName");
        } catch (IllegalArgumentException e) {
            assertEquals("gaName", e.getMessage());
        }

        // pieceType:

        msg = new SOCUndoPutPiece("ga", 3, 0, 0x11);
        assertNotNull("pieceType 0 OK", msg);
        msg = new SOCUndoPutPiece("ga", 3, 0, 0x11, 0x22);
        assertNotNull("pieceType 0 OK", msg);
        try {
            msg = new SOCUndoPutPiece("ga", 3, -1, 0x11);
            fail("should disallow pieceType < 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("pt: "));
        }
        try {
            msg = new SOCUndoPutPiece("ga", 3, -1, 0x11, 0x22);
            fail("should disallow pieceType < 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("pt: "));
        }

        // playerNumber:

        msg = new SOCUndoPutPiece("ga", -1, 1, 0x11);
        assertNotNull("pn < 0 OK", msg);
        msg = new SOCUndoPutPiece("ga", -1, 1, 0x11, 0x22);
        assertNotNull("pn < 0 OK", msg);

        // coordinates:

        msg = new SOCUndoPutPiece("ga", 3, 1, 0x00);
        assertNotNull("undo-put coord 0 is OK", msg);
        try {
            msg = new SOCUndoPutPiece("ga", 3, 1, -1);
            fail("should disallow put coord < 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("coord < 0: "));
        }
        try {
            msg = new SOCUndoPutPiece("ga", 3, 1, -1, 0x22);
            fail("should disallow move coord < 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("coord < 0: "));
        }
        try {
            msg = new SOCUndoPutPiece("ga", 3, 1, 0, 0x22);
            fail("should disallow move coord == 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("move coord <= 0: "));
        }

        // movedFromCoordinates:

        msg = new SOCUndoPutPiece("ga", 3, 1, 0x11, 0);
        assertNotNull("movedFrom 0 OK, means msg is undo-put", msg);
        try {
            msg = new SOCUndoPutPiece("ga", 3, 1, 0x11, -2);
            fail("should disallow movedFrom coord < 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("fromCo < 0: "));
        }
    }

}
