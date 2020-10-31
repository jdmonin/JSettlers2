/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.message.SOCDevCardAction;
import soc.message.SOCPlayerElement;
import soc.message.SOCSetSpecialItem;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for string constants seen in message classes like {@link SOCDevCardAction}.
 * @since 2.4.50
 */
public class TestStringConstants
{
    /** Test {@link SOCDevCardAction#ACTION_STRINGS} and corresponding int constants. */
    @Test
    public void testDevCardAction()
    {
        final String[] ACTION_STRINGS = SOCDevCardAction.ACTION_STRINGS;
        assertEquals(5, ACTION_STRINGS.length);
        assertEquals("DRAW", ACTION_STRINGS[SOCDevCardAction.DRAW]);
        assertEquals("PLAY", ACTION_STRINGS[SOCDevCardAction.PLAY]);
        assertEquals("ADD_NEW", ACTION_STRINGS[SOCDevCardAction.ADD_NEW]);
        assertEquals("ADD_OLD", ACTION_STRINGS[SOCDevCardAction.ADD_OLD]);
        assertEquals("CANNOT_PLAY", ACTION_STRINGS[SOCDevCardAction.CANNOT_PLAY]);
    }

    /** Test {@link SOCPlayerElement#ACTION_STRINGS} and corresponding int constants. */
    @Test
    public void testPlayerElement()
    {
        final String[] ACTION_STRINGS = SOCPlayerElement.ACTION_STRINGS;
        assertEquals(3, ACTION_STRINGS.length);
        assertEquals("SET", ACTION_STRINGS[SOCPlayerElement.SET - 100]);
        assertEquals("GAIN", ACTION_STRINGS[SOCPlayerElement.GAIN - 100]);
        assertEquals("LOSE", ACTION_STRINGS[SOCPlayerElement.LOSE - 100]);
    }

    /** Test {@link SOCSetSpecialItem#OPS_STRS} and corresponding int constants. */
    @Test
    public void testSetSpecialItem()
    {
        final String[] OPS_STRS = SOCSetSpecialItem.OPS_STRS;

        assertEquals(1 + SOCSetSpecialItem.OP_CLEAR_PICK, OPS_STRS.length);
        assertNull(OPS_STRS[0]);
        assertEquals("SET", OPS_STRS[SOCSetSpecialItem.OP_SET]);
        assertEquals("CLEAR", OPS_STRS[SOCSetSpecialItem.OP_CLEAR]);
        assertEquals("PICK", OPS_STRS[SOCSetSpecialItem.OP_PICK]);
        assertEquals("DECLINE", OPS_STRS[SOCSetSpecialItem.OP_DECLINE]);
        assertEquals("SET_PICK", OPS_STRS[SOCSetSpecialItem.OP_SET_PICK]);
        assertEquals("CLEAR_PICK", OPS_STRS[SOCSetSpecialItem.OP_CLEAR_PICK]);
    }

}
