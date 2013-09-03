/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013 Luis A. Ramirez <lartkma@gmail.com>
 * Some parts of this file Copyright (C) 2013 Jeremy D Monin <jeremy@nand.net>
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
 * The maintainer of this program can be reached at lartkma@gmail.com 
 **/
package soc.util;

import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * TODO Write JavaDoc.
 * @author lartkma
 * @see soc.util.I18n
 */
public class SOCStringManager {

    /**
     * Manager for all client strings. Static is okay because the client is seen by 1 person with 1 locale.
     */
    private static SOCStringManager clientManager = null;

    /**
     * Manager at server for strings sent to the client.
     *<P>
     * Key = locale.toString, value = {@link SOCStringManager} for server strings to clients in that locale. 
     * Uses Hashtable to gain synchronization.
     */
    private static Hashtable<String, SOCStringManager> serverManagerForClientLocale
        = new Hashtable<String, SOCStringManager>();

    /**
     * Fallback for {@link #serverManagerForClientLocale} using server's default locale.
     */
    private static SOCStringManager serverManagerForClientLocale_fallback;

    private ResourceBundle bundle;

    /**
     * Create a string manager for the bundles at {@code bundlePath} with the default locale. 
     * @param bundlePath  Bundle path, will be retrieved with {@link ResourceBundle#getBundle(String)}
     */
    public SOCStringManager(String bundlePath){
        bundle = ResourceBundle.getBundle(bundlePath);
    }

    /**
     * Create a string manager for the bundles at {@code bundlePath} with a certain Locale.
     * @param bundlePath  Bundle path, will be retrieved with {@link ResourceBundle#getBundle(String, Locale)}
     * @param loc  Locale to use; not {@code null}
     */
    public SOCStringManager(final String bundlePath, final Locale loc) {
        bundle = ResourceBundle.getBundle(bundlePath, loc);
    }

    // If you add get methods, for server convenience also add them in StringConnection and classes that implement that.

    /**
     * Get a localized string (having no parameters) with the given key.
     * @param key  Key to use for string retrieval
     * @return the localized string from the manager's bundle or one of its parents
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     */
    public String get(String key)
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
     */
    public String get(String key, Object ... arguments)
        throws MissingResourceException
    {
        return MessageFormat.format(bundle.getString(key), arguments);
    }

    /**
     * Create or retrieve the cached client string manager, with the default Locale.
     * If the client manager already exists, further gets will return that manager
     * with its Locale, ignoring the default locale of the new call.
     * @return  The client manager
     */
    public static SOCStringManager getClientManager(){
        if(clientManager == null)
            clientManager = new SOCStringManager("soc/client/strings/data");
        
        return clientManager;
    }

    /**
     * Create or retrieve the cached client string manager, with a certain Locale.
     * If the client manager already exists, further gets will return that manager
     * with its Locale, ignoring the Locale of the new call.
     * @param loc  Locale to use; not {@code null}
     * @return  The client manager
     */
    public static SOCStringManager getClientManager(Locale loc) {
        if (clientManager == null)
            clientManager = new SOCStringManager("soc/client/strings/data", loc);

        return clientManager;
    }

    /**
     * Create or retrieve the server's string manager to send text to a clients with a certain locale.
     * @param loc  Locale to use, or {@code null} for the {@link Locale#getDefault()} 
     * @return  The server manager for that client locale
     */
    public static SOCStringManager getServerManagerForClient(Locale loc) {
        if (loc == null)
            loc = Locale.getDefault();

        final String lstr = loc.toString();
        SOCStringManager smc = serverManagerForClientLocale.get(lstr);
        if (smc == null)
        {
            smc = new SOCStringManager("soc/server/strings/toClient", loc);
            serverManagerForClientLocale.put(lstr, smc);
        }

        return smc;
    }

    /**
     * Create or retrieve the server's string manager for fallback to send text to clients with unknown locale.
     * Can be used for messages while a client hasn't yet sent their locale.
     * @return  The server string manager with default locale
     */
    public static SOCStringManager getFallbackServerManagerForClient() {
        SOCStringManager sm = serverManagerForClientLocale_fallback;
        if (sm == null)
        {
            sm = getServerManagerForClient(null);
            serverManagerForClientLocale_fallback = sm;
            // multithreading: If multiple threads race to initialize this field,
            // any of the created objects will have the same contents and function
        }

        return sm;
    }
}
