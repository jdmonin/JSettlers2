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

package soctest.util;

import soc.util.Version;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link Version}.
 * @since 2.7.00
 */
public class TestVersion
{
    /**
     * Test that {@link Version#versionNumberMaximumNoWarn()} has a reasonable recent value:
     * &gt;= 2000, &lt; current {@link Version#versionNumber()}.
     */
    @Test
    public void testVersionNumberMaximumNoWarn()
    {
        assertNotNull(Version.VERSNUM_NOWARN_MAXIMUM);
        assertFalse(Version.VERSNUM_NOWARN_MAXIMUM.isEmpty());

        final int recentVers = Version.versionNumberMaximumNoWarn();
        assertTrue("versionNumberMaximumNoWarn is recent >= 2000", (recentVers >= 2000));
        assertTrue("versionNumberMaximumNoWarn < current", (recentVers < Version.versionNumber()));
    }

    /**
     * Test that {@link Version#websiteURLMain()} and {@link Version#websiteURLSrc()} aren't null
     * and have their expected values.
     */
    @Test
    public void testProjectWebsiteURLs()
    {
        assertNotNull(Version.WEBSITE_URL_MAIN);
        assertFalse(Version.WEBSITE_URL_MAIN.isEmpty());

        String url = Version.websiteURLMain();
        assertNotNull("version.info missing " + Version.WEBSITE_URL_MAIN, url);
        assertEquals("https://nand.net/jsettlers/", url);  // if project site moves, update this test

        assertNotNull(Version.WEBSITE_URL_SRC);
        assertFalse(Version.WEBSITE_URL_SRC.isEmpty());

        url = Version.websiteURLSrc();
        assertNotNull("version.info missing " + Version.WEBSITE_URL_SRC, url);
        assertEquals("https://github.com/jdmonin/JSettlers2/", url);  // if project source repo moves, update this test
    }

}
