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

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCSpecialItem;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCSpecialItem}.
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

}
