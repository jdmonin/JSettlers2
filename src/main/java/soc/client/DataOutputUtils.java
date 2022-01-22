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

package soc.client;

/**
 * A few methods for displaying and outputting data.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.6.00
 */
public abstract class DataOutputUtils
{
    /**
     * For use in multi-line {@code JLabel}s, convert text with {@code '\n'}s to HTML with {@code <BR>}s.
     * Calls {@link #escapeHTML(String, StringBuilder)} to sanitize any html special characters.
     * @param str String to inspect and convert; can be null
     * @return {@code str} converted to HTML with {@code <BR>}s
     *     (including leading {@code <HTML>}, trailing {@code </HTML>}),
     *     or {@code str} if unchanged because no {@code \n} found
     * @since 2.6.00
     */
    public static String newlinesToHTML(String str)
    {
        if (str == null)
            return null;
        int i = str.indexOf('\n');
        if (i == -1)
            return str;

        StringBuilder sb = new StringBuilder("<HTML>");
        int iprev = 0;
        while (i != -1)
        {
            if (i > 0)
                escapeHTML(str.substring(iprev, i), sb);
            sb.append("<BR>");
            iprev = i + 1;
            i = str.indexOf('\n', iprev);
        }
        final int L = str.length();
        if (iprev < L)
            escapeHTML(str.substring(iprev, L), sb);
        sb.append("</HTML>");

        return sb.toString();
    }

    /**
     * For use in toString and {@code JLabel}s, escape html special characters in a string.
     * @param htmlText  Text to escape; not null
     * @param sb  StringBuilder to append escaped {@code htmlText} onto
     * @throws NullPointerException if {@code htmlText} or {@code sb} is null
     * @see #newlinesToHTML(String)
     * @since 2.6.00
     */
    public static final void escapeHTML(String htmlText, final StringBuilder sb)
        throws NullPointerException
    {
        if (htmlText.isEmpty())
            return;
        sb.append(htmlText.replaceAll("&", "&amp;").replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;").replaceAll("\"", "&quot;").replaceAll("'", "&#39;"));
    }

}
