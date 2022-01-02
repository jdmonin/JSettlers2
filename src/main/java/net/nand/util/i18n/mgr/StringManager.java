/**
 * nand.net i18n utilities for Java: String Manager.
 * This file Copyright (C) 2013,2018-2020 Jeremy D Monin <jeremy@nand.net>
 * Some parts of this file Copyright (C) 2013 Luis A. Ramirez <lartkma@gmail.com>
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
 * The maintainer of this program can be reached at jeremy@nand.net
 **/
package net.nand.util.i18n.mgr;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * String Manager for retrieving I18N localized text from {@code .properties} bundle files.
 * Written for the JSettlers2 client by Luis A. Ramirez, extended and enhanced by Jeremy Monin.
 *<P>
 * Your app may want to extend this class if it has objects which want special formatting methods,
 * or to provide and cache a single manager instance whose locale is set at startup.
 *<P>
 * Remember that bundle files are encoded not in {@code UTF-8} but in {@code ISO-8859-1}:
 *<UL>
 * <LI> <A href="https://docs.oracle.com/javase/6/docs/api/java/util/Properties.html"
 *       >java.util.Properties</A> (Java 6)
 * <LI> <A href="http://stackoverflow.com/questions/4659929/how-to-use-utf-8-in-resource-properties-with-resourcebundle"
 *       >Stack Overflow: How to use UTF-8 in resource properties with ResourceBundle</A> (asked on 2011-01-11)
 *</UL>
 * You can use the {@code net.nand.util.i18n.gui.PTEMain} editor to compare and translate properties files
 * conveniently side-by-side, without needing to deal with ISO-8859-1 unicode escapes.
 *
 * @author lartkma
 */
public class StringManager
{
    /**
     * This manager's resource bundle with localized strings.
     * @see #get(String)
     * @see ResourceBundle#getString(String)
     */
    final protected ResourceBundle bundle;

    /**
     * Create a string manager for the bundles at {@code bundlePath} with the default locale.
     * Remember that bundle files are encoded not in {@code UTF-8} but in {@code ISO-8859-1}, see class javadoc.
     * @param bundlePath  Bundle path, will be retrieved with {@link ResourceBundle#getBundle(String)}
     */
    public StringManager(String bundlePath)
    {
        bundle = ResourceBundle.getBundle(bundlePath);
    }

    /**
     * Create a string manager for the bundles at {@code bundlePath} with a certain Locale.
     * Remember that bundle files are encoded not in {@code UTF-8} but in {@code ISO-8859-1}, see class javadoc.
     * @param bundlePath  Bundle path, will be retrieved with {@link ResourceBundle#getBundle(String, Locale)}
     * @param loc  Locale to use; not {@code null}
     */
    public StringManager(final String bundlePath, final Locale loc)
    {
        bundle = ResourceBundle.getBundle(bundlePath, loc);
    }

    /**
     * Parse and construct a Locale for this locale string.
     *<P>
     * This method used to be in the JSettlers {@code soc.util.I18n} package;
     * moved here for JSettlers v2.2 and net.nand.util.i18n editor version 1.2.
     *
     * @param loc  Locale string, such as "en_US" from {@link Locale#toString()}
     * @return A Locale object, or {@link Locale#getDefault()} if {@code loc} is "" or null
     * @throws IllegalArgumentException if no locale can be parsed or found
     */
    public static final Locale parseLocale(final String loc)
        throws IllegalArgumentException
    {
        if ((loc == null) || loc.isEmpty())
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
     * Get a localized string (having no parameters) with the given key.
     * @param key  Key to use for string retrieval
     * @return the localized string from the manager's bundle or one of its parents
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @see #get(String, Object...)
     */
    public final String get(final String key)
        throws MissingResourceException
    {
        return bundle.getString(key);
    }

    /**
     * Get and format a localized string (with parameters) with the given key.
     * @param key  Key to use for string retrieval
     * @param arguments  Objects to use with <tt>{0}</tt>, <tt>{1}</tt>, etc in the localized string
     *                   by calling {@link MessageFormat#format(String, Object...)}.
     * @return the localized formatted string from the manager's bundle or one of its parents
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @see #get(String)
     */
    public final String get(final String key, final Object ... arguments)
        throws MissingResourceException
    {
        return MessageFormat.format(bundle.getString(key), arguments);
    }

}
