/*
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
 */

package soctest.client;

import org.junit.Test;
import static org.junit.Assert.*;

import soc.client.DataOutputUtils;

/**
 * Tests for {@link DataOutputUtils}.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.6.00
 */
public class TestDataOutputUtils
{
    /**
     * Test {@link DataOutputUtils#newlinesToHTML(String)}.
     */
    @Test
    public void testNewlinesToHTML()
    {
        assertNull(DataOutputUtils.newlinesToHTML(null));

        String unchanged = "";
        assertTrue(unchanged == DataOutputUtils.newlinesToHTML(unchanged));

        unchanged = "no_newlines_in_here";
        assertTrue(unchanged == DataOutputUtils.newlinesToHTML(unchanged));

        String html = DataOutputUtils.newlinesToHTML("\n");
        assertEquals("<HTML><BR></HTML>", html);

        html = DataOutputUtils.newlinesToHTML("\n\n");
        assertEquals("<HTML><BR><BR></HTML>", html);

        html = DataOutputUtils.newlinesToHTML("basic\ntest");
        assertEquals("<HTML>basic<BR>test</HTML>", html);

        html = DataOutputUtils.newlinesToHTML("basic\n3-line\ntest");
        assertEquals("<HTML>basic<BR>3-line<BR>test</HTML>", html);

        html = DataOutputUtils.newlinesToHTML("consecutive\n\ntest");
        assertEquals("<HTML>consecutive<BR><BR>test</HTML>", html);

        html = DataOutputUtils.newlinesToHTML("\nstarting-newline");
        assertEquals("<HTML><BR>starting-newline</HTML>", html);

        html = DataOutputUtils.newlinesToHTML("\n\n2 starting-newline");
        assertEquals("<HTML><BR><BR>2 starting-newline</HTML>", html);

        html = DataOutputUtils.newlinesToHTML("\nstarting-newline\nmore");
        assertEquals("<HTML><BR>starting-newline<BR>more</HTML>", html);

        html = DataOutputUtils.newlinesToHTML("\nstarting-newline\nmore\neven more");
        assertEquals("<HTML><BR>starting-newline<BR>more<BR>even more</HTML>", html);

        html = DataOutputUtils.newlinesToHTML("ending-newline\n");
        assertEquals("<HTML>ending-newline<BR></HTML>", html);

        html = DataOutputUtils.newlinesToHTML("2 ending-newline\n\n");
        assertEquals("<HTML>2 ending-newline<BR><BR></HTML>", html);

        html = DataOutputUtils.newlinesToHTML("more\nending-newline\n");
        assertEquals("<HTML>more<BR>ending-newline<BR></HTML>", html);

        html = DataOutputUtils.newlinesToHTML("more\neven more\nending-newline\n");
        assertEquals("<HTML>more<BR>even more<BR>ending-newline<BR></HTML>", html);

        html = DataOutputUtils.newlinesToHTML("\nstart-end-newlines\n");
        assertEquals("<HTML><BR>start-end-newlines<BR></HTML>", html);

        html = DataOutputUtils.newlinesToHTML("\nstart-end\nmore newlines\n");
        assertEquals("<HTML><BR>start-end<BR>more newlines<BR></HTML>", html);

        html = DataOutputUtils.newlinesToHTML("\nstart-end\nmore\neven more newlines\n");
        assertEquals("<HTML><BR>start-end<BR>more<BR>even more newlines<BR></HTML>", html);

        html = DataOutputUtils.newlinesToHTML("it's a test of\n\"newlines\"\n& html <escapes>\n");
        assertEquals("<HTML>it&#39;s a test of<BR>&quot;newlines&quot;<BR>&amp; html &lt;escapes&gt;<BR></HTML>", html);
    }

    /**
     * Test {@link DataOutputUtils#escapeHTML(String, StringBuilder)}.
     */
    @Test
    public void testEscapeHTML()
    {
        StringBuilder sb = new StringBuilder("contents");

        try
        {
            DataOutputUtils.escapeHTML(null, sb);
            fail("should have thrown NullPointerException");
        } catch (NullPointerException e) {}

        try
        {
            DataOutputUtils.escapeHTML("xyz", null);
            fail("should have thrown NullPointerException");
        } catch (NullPointerException e) {}

        DataOutputUtils.escapeHTML("", sb);
        assertEquals("contents", sb.toString());

        DataOutputUtils.escapeHTML("<<>>", sb);
        assertEquals("contents&lt;&lt;&gt;&gt;", sb.toString());

        sb.delete(0, sb.length());
        DataOutputUtils.escapeHTML("'this' & \"that\" & <more>", sb);
        assertEquals("&#39;this&#39; &amp; &quot;that&quot; &amp; &lt;more&gt;", sb.toString());

        sb.delete(0, sb.length());
        DataOutputUtils.escapeHTML("text <font face=\"bad\" color='#ffffff'>is hidden</font>", sb);
        assertEquals("text &lt;font face=&quot;bad&quot; color=&#39;#ffffff&#39;&gt;is hidden&lt;/font&gt;", sb.toString());
    }

}
