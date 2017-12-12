/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2015,2017 Jeremy D Monin <jeremy@nand.net>
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
package soc.util;

import java.util.List;
import java.util.Locale;

/**
 * Common helper functions for internationalization and localization (I18N).
 *<P>
 * I18N localization was added in v2.0.00; network messages sending localized text should
 * check the remote receiver's version against {@link SOCStringManager#VERSION_FOR_I18N}.
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @see SOCStringManager
 */
public abstract class I18n
{
    /**
     * Property {@code jsettlers.locale} to specify the locale,
     * overriding the default from {@link java.util.Locale#getDefault()}.toString().
     */
    public static final String PROP_JSETTLERS_LOCALE = "jsettlers.locale";

    /**
     * Parse and construct a Locale for this locale string.
     * @param loc  Locale string, such as "en_US" from {@link Locale#toString()}
     * @return A Locale object, or if 0-length, {@link Locale#getDefault()}.
     * @throws IllegalArgumentException if no locale can be parsed or found
     */
    public static final Locale parseLocale(final String loc)
        throws IllegalArgumentException
    {
        if (loc.length() == 0)
            return Locale.getDefault();
        final String[] lc = loc.split("_");
        if (lc.length == 1)
            return new Locale(lc[0]);
        else if (lc.length == 2)
            return new Locale(lc[0], lc[1]);
        else
            return new Locale(lc[0], lc[1], lc[2]);
    }

    /**
     * Build a string with the contents of this list, such as "x, y, and z".
     *<P>
     * This method and its formatting strings ({@code i18n.listitems.*}) may need
     * refinement as more languages are supported.
     * @param items  Each item's {@link Object#toString() toString()} will be placed in the list
     * @param strings  StringManager to retrieve localized formatting between items
     * @return A string nicely listing the items, with a form such as:
     *   <UL>
     *   <LI> nothing
     *   <LI> x
     *   <LI> x and y
     *   <LI> x, y, and z
     *   <LI> x, y, z, and w
     *   </UL>
     * @throws IllegalArgumentException if {@code items} is null, or {@code strings} is null
     */
    public static final String listItems(List<? extends Object> items, SOCStringManager strings)
        throws IllegalArgumentException
    {
        if ((items == null) || (strings == null))
            throw new IllegalArgumentException("null");

        final int L = items.size();
        switch(L)
        {
        case 0:
            return strings.get("i18n.listitems.nothing");  // "nothing"

        case 1:
            return items.get(0).toString();

        case 2:
            return strings.get("i18n.listitems.2", items.get(0), items.get(1));  // "{0} and {1}"

        default:
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < (L-1); ++i)
                sb.append(strings.get("i18n.listitems.item", items.get(i)));  // "{0}, " -- trailing space noted in properties file comment
            sb.append(strings.get("i18n.listitems.finalitem", items.get(L-1)));  // "and {0}"
            return sb.toString();
        }
    }

}

