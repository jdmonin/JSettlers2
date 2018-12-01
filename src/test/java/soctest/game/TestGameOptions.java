/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2018 Jeremy D Monin <jeremy@nand.net>
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

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCGameOption;

/**
 * Tests for {@link SOCGameOption}.
 *<P>
 * TODO add more basic-functionality tests
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestGameOptions
{
    /**
     * Test that when the client sends a new-game request whose opts contains {@code "VP"} with boolean part false,
     * that {@code "VP"} will be removed from the set of options by
     * {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean) SGO.adjustOptionsToKnown(newOpts, knownOpts, true)}.
     */
    @Test
    public void testServerRemovesFalseVP()
    {
        final Map<String, SOCGameOption> newOpts = new HashMap<String, SOCGameOption>(),
            knownOpts = new HashMap<String, SOCGameOption>();

        final SOCGameOption optVP = SOCGameOption.getOption("VP", true);
        optVP.setIntValue(12);
        optVP.setBoolValue(false);

        // should remove VP when false and not present at "server" with a default
        newOpts.put("VP", optVP);
        StringBuilder sb = SOCGameOption.adjustOptionsToKnown(newOpts, knownOpts, true);
        assertNull(sb);
        assertFalse(newOpts.containsKey("VP"));

        // should remove VP when false and is present at "server" with a default
        {
            final SOCGameOption srvVP = SOCGameOption.getOption("VP", true);
            srvVP.setIntValue(13);
            srvVP.setBoolValue(true);
            knownOpts.put("VP", srvVP);
        }
        newOpts.put("VP", optVP);
        sb = SOCGameOption.adjustOptionsToKnown(newOpts, knownOpts, true);
        assertNull(sb);
        assertFalse(newOpts.containsKey("VP"));

        // shouldn't remove if VP is true
        optVP.setBoolValue(true);
        newOpts.put("VP", optVP);
        sb = SOCGameOption.adjustOptionsToKnown(newOpts, knownOpts, true);
        assertNull(sb);
        assertTrue(newOpts.containsKey("VP"));
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.game.TestGameOptions");
    }

}
