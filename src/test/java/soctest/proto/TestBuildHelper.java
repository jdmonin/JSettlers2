/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2022 Jeremy D Monin <jeremy@nand.net>
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

package soctest.proto;

import soc.game.SOCResourceSet;
import soc.message.ProtoMessageBuildHelper;
import soc.proto.Data;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link ProtoMessageBuildHelper}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 3.0.00
 */
public class TestBuildHelper
{

    /**
     * Test {@link soc.message.ProtoMessageBuildHelper#fromResourceSet(Data.ResourceSet, boolean)}
     * vs {@link Data.ResourceSet}s with unknown resources.
     */
    @Test
    public void testBuildHelperFromResourceSetVsUnknown()
    {
        final SOCResourceSet SHEEP_3 = new SOCResourceSet(0, 0, 3, 0, 0, 0),
            WOOD_1_UNKNOWN_1 = new SOCResourceSet(0, 0, 0, 0, 1, 1);
        final Data.ResourceSet protoSheep = ProtoMessageBuildHelper.toResourceSet(SHEEP_3).build(),
            protoWoodUnknown = ProtoMessageBuildHelper.toResourceSet(WOOD_1_UNKNOWN_1).build();

        SOCResourceSet rsSheep1 = ProtoMessageBuildHelper.fromResourceSet(protoSheep, false),
            rsSheep2 = ProtoMessageBuildHelper.fromResourceSet(protoSheep, true);
        assertNotNull(rsSheep1);
        assertNotNull(rsSheep2);
        assertArrayEquals(new int[]{0, 0, 3, 0, 0, 0}, rsSheep1.getAmounts(true));
        assertEquals(rsSheep1, rsSheep2);

        SOCResourceSet rsWoodUnknown = ProtoMessageBuildHelper.fromResourceSet(protoWoodUnknown, false);
        assertNull(rsWoodUnknown);

        rsWoodUnknown = ProtoMessageBuildHelper.fromResourceSet(protoWoodUnknown, true);
        assertNotNull(rsWoodUnknown);
        assertArrayEquals(new int[]{0, 0, 0, 0, 1, 1}, rsWoodUnknown.getAmounts(true));
    }

}
