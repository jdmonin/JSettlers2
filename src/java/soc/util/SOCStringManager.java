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
import java.util.Locale;
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
    
    public String get(String key){
        return bundle.getString(key);
    }

    public String get(String key, Object ... arguments){
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

}
