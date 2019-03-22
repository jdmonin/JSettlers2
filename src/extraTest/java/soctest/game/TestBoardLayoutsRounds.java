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

import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import static org.junit.Assert.*;

/**
 * Extra testing for {@link TestBoardLayouts}: Run 2000 rounds.
 * @since 2.0.00
 */
public class TestBoardLayoutsRounds
{
    /**
     * Run 2000 rounds of each layout's board generation
     */
    @Test
    public void testGameLayouts()
    {
        TestBoardLayouts.roundCount = 2000;
        final Result rslt = JUnitCore.runClasses(TestBoardLayouts.class);
        if (! rslt.wasSuccessful())
        {
            boolean isTimeoutOnly = true;
            for (Failure f : rslt.getFailures())
            {
                System.out.println("sub-test failure: " + f);
                    // "testLayouts(soctest.game.TestBoardLayouts): test timed out after 300 milliseconds"
                // Was it a timeout?
                // Test exception name, not class object, to avoid brittle dependency on junit package structure
                Throwable tex = f.getException();
                if ((tex == null) || ! "TestTimedOutException".equals(tex.getClass().getSimpleName()))
                    isTimeoutOnly = false;
            }
            System.out.flush();
            if (isTimeoutOnly)
                fail("Layout tests timed out");  // instead of generic failure message
        }
        assertTrue("Layout tests failed; see test's stdout for details", rslt.wasSuccessful());
    }

}
