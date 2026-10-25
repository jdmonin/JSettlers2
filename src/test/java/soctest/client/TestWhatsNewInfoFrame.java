/*
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
 */

package soctest.client;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soc.client.WhatsNewInfoFrame;
import soc.client.WhatsNewInfoFrame.ReleaseNotesFromDirectory;
import soc.util.Version;

/**
 * A few tests for the release notes read and shown by {@link WhatsNewInfoFrame}.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.7.00
 */
public class TestWhatsNewInfoFrame
{
    /**
     * Test {@link WhatsNewInfoFrame.ReleaseNotesFromDirectory}
     * and basic structure of files it reads.
     */
    @Test
    public void testReleaseNotesFromDirectory()
        throws IOException
    {
        assertNotNull(WhatsNewInfoFrame.RELNOTES_RSRC_DIR_PATH);
        assertTrue(WhatsNewInfoFrame.RELNOTES_RSRC_DIR_PATH.startsWith("/resources"));

        final ReleaseNotesFromDirectory relnotes
            = new WhatsNewInfoFrame.ReleaseNotesFromDirectory(WhatsNewInfoFrame.RELNOTES_RSRC_DIR_PATH);
        final TreeMap<String, String> notesHTML = relnotes.notesHTML;
        assertNotNull(notesHTML);
        assertFalse(notesHTML.isEmpty());

        assertTrue(notesHTML.containsKey("footer.html"));
        final String currVersFname = "v" + Version.versionNumber() + ".html";
        assertTrue("Contains current version notes " + currVersFname, notesHTML.containsKey(currVersFname));

        // if v2500.html is removed later, this assert will fail;
        // but if nothing is longer than that buffer, the test isn't needed.
        // So, please comment it out until it's needed again by a later version's notes
        assertTrue("v2500 contents are longer than read buffer, to ensure read loop handles that",
            WhatsNewInfoFrame.ReleaseNotesFromDirectory.BUFFER_SIZE < notesHTML.get("v2500.html").length());

        final String versFnamePatternRegex = "^v(\\d{4})\\.html$";
        final Pattern versFnamePattern = Pattern.compile(versFnamePatternRegex);

        for (String fname : notesHTML.keySet())
        {
            assertTrue(fname + ": name ends with .html", fname.endsWith(".html"));
            final String html = notesHTML.get(fname);
            assertNotNull(fname + " not null", html);
            if (fname.equals("footer.html"))
            {
                assertTrue(fname + " starts with <P>", html.startsWith("<P>"));
            }
            else if (fname.startsWith("v"))
            {
                Matcher match = versFnamePattern.matcher(fname);
                assertTrue(fname + ": name should be v####.html to match regex " + versFnamePatternRegex,
                    match.find());
                final int versNum = Integer.parseInt(match.group(1));
                assertTrue(fname + ": version in filename should be >= 2000, not "+ versNum,
                    versNum >= 2000);
                final String expectHtmlStart = "<H4>" + Version.version(versNum);
                final int L = expectHtmlStart.length();
                assertTrue(fname + ": should start with " + expectHtmlStart,
                    L <= html.length());
                assertEquals(fname + ": should start with " + expectHtmlStart,
                    expectHtmlStart, html.substring(0, L));
            } else {
                fail("Stray file, name doesn't match pattern, under " + WhatsNewInfoFrame.RELNOTES_RSRC_DIR_PATH
                    + ": " + fname);
            }
        }
    }

}
