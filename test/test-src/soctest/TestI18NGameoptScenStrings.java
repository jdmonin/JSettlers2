/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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
package soctest;

import org.junit.Test;
import static org.junit.Assert.*;

import soc.game.SOCGameOption;
import soc.game.SOCScenario;

/**
 * Tests for I18N - Consistency of {@link SOCGameOption} and {@link SOCScenario} strings
 * in their Java classes versus properties files.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestI18NGameoptScenStrings
{
    @Test
    public void testGameoptsText()
    {
        // TODO the actual test
        assertTrue(true);
    }

    @Test
    public void testScenariosText()
    {
        // TODO the actual test
        assertTrue(true);
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.TestI18NGameoptScenStrings");
    }

}
