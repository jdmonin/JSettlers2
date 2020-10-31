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

package soctest.server;

import soc.server.SOCServer;
import soc.util.SOCFeatureSet;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for miscellaneous {@link SOCServer} methods.
 * @since 2.4.50
 */
public class TestSOCServerMisc
{

    /**
     * Test {@link SOCServer#checkLimitClientFeaturesForServerDisallows(SOCFeatureSet)}.
     */
    @Test
    public void testCheckLimitClientFeaturesForServerDisallows()
    {
        @SuppressWarnings("serial")
        class SOCServerWithCheck extends SOCServer
        {
            public SOCServerWithCheck()
            {
                super("test", null);
            }

            public SOCFeatureSet checkDisallows
                (SOCFeatureSet cliFeats, boolean disallow6pl, boolean disallowSeaBoard)
            {
                if (disallow6pl)
                    props.put(PROP_JSETTLERS_GAME_DISALLOW_6PLAYER, "Y");
                else
                    props.remove(PROP_JSETTLERS_GAME_DISALLOW_6PLAYER);

                if (disallowSeaBoard)
                    props.put(PROP_JSETTLERS_GAME_DISALLOW_SEA__BOARD, "Y");
                else
                    props.remove(PROP_JSETTLERS_GAME_DISALLOW_SEA__BOARD);

                return checkLimitClientFeaturesForServerDisallows(cliFeats);
            }
        };

        final SOCServerWithCheck srv = new SOCServerWithCheck();

        final SOCFeatureSet standardCliFeats = new SOCFeatureSet(";6pl;sb;sc=2450;");
        assertTrue(standardCliFeats.isActive(SOCFeatureSet.CLIENT_6_PLAYERS));
        assertTrue(standardCliFeats.isActive(SOCFeatureSet.CLIENT_SEA_BOARD));
        assertEquals(2450, standardCliFeats.getValue(SOCFeatureSet.CLIENT_SCENARIO_VERSION, 0));

        // basics;
        assertNull(srv.checkDisallows(standardCliFeats, false, false));
        assertNull(srv.checkDisallows(null, false, false));

        final SOCFeatureSet extraFeats = new SOCFeatureSet(";xyz=5;sbmisc;6pl;sb;sc=2450;");
        for (SOCFeatureSet cliFeats : new SOCFeatureSet[]{ standardCliFeats, null, extraFeats })
        {
            // disallow only 6pl:
            SOCFeatureSet feats = srv.checkDisallows(cliFeats, true, false);
            assertNotNull(feats);
            assertFalse(feats.isActive(SOCFeatureSet.CLIENT_6_PLAYERS));
            if (cliFeats != null)
            {
                assertTrue(feats.isActive(SOCFeatureSet.CLIENT_SEA_BOARD));
                assertEquals(2450, feats.getValue(SOCFeatureSet.CLIENT_SCENARIO_VERSION, 0));
                if (cliFeats == extraFeats)
                {
                    assertTrue(feats.isActive("sbmisc"));
                    assertEquals(5, feats.getValue("xyz", 0));
                }
            } else {
                assertNull(feats.getEncodedList());
            }

            // disallow only sea:
            feats = srv.checkDisallows(cliFeats, false, true);
            assertNotNull(feats);
            if (cliFeats != null)
            {
                assertTrue(feats.isActive(SOCFeatureSet.CLIENT_6_PLAYERS));
                if (cliFeats == extraFeats)
                {
                    assertTrue(feats.isActive("sbmisc"));
                    assertEquals(5, feats.getValue("xyz", 0));
                }
            } else {
                assertNull(feats.getEncodedList());
            }
            assertFalse(feats.isActive(SOCFeatureSet.CLIENT_SEA_BOARD));
            assertFalse(feats.isActive(SOCFeatureSet.CLIENT_SCENARIO_VERSION));

            // disallow both:
            feats = srv.checkDisallows(cliFeats, true, true);
            assertNotNull(feats);
            if (cliFeats == null)
            {
                assertNull(feats.getEncodedList());
            }
            else if (cliFeats == extraFeats)
            {
                assertTrue(feats.isActive("sbmisc"));
                assertEquals(5, feats.getValue("xyz", 0));
            }
            assertFalse(feats.isActive(SOCFeatureSet.CLIENT_6_PLAYERS));
            assertFalse(feats.isActive(SOCFeatureSet.CLIENT_SEA_BOARD));
            assertFalse(feats.isActive(SOCFeatureSet.CLIENT_SCENARIO_VERSION));
        }
    }

}
