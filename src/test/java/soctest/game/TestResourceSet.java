/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
 * Portions of this file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestResourceSet
{
    @Test
    public void total_test()
    {
        SOCResourceSet rs = new SOCResourceSet(1,1,1,1,1,0);
        assertEquals(5, rs.getTotal());
    }

    @Test
    public void removeOneResource_removesOneResource()
    {
        SOCResourceSet rs = new SOCResourceSet(1,1,1,1,1,0);
        rs.subtract(1, SOCResourceConstants.ORE);
        assertEquals(4, rs.getTotal());
    }

    @Test
    public void removeTwoResources_doesNotThrowException()
    {
        SOCResourceSet rs = new SOCResourceSet(1,1,1,1,1,0);
        rs.subtract(2, SOCResourceConstants.CLAY);
        assertEquals(3, rs.getTotal());
        assertEquals(0, rs.getAmount(SOCResourceConstants.CLAY));
        assertEquals(-1, rs.getAmount(SOCResourceConstants.UNKNOWN));
    }
}
