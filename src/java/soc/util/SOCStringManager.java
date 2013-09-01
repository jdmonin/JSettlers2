/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013 Luis A. Ramirez <lartkma@gmail.com>
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
import java.util.ResourceBundle;

/**
 * TODO Write JavaDoc.
 * @author lartkma
 */
public class SOCStringManager {

    private static SOCStringManager clientManager = null;
    
    private ResourceBundle bundle;
    
    public SOCStringManager(String bundlePath){
        bundle = ResourceBundle.getBundle(bundlePath);
    }
    
    public String get(String key){
        return bundle.getString(key);
    }
    public String get(String key, Object ... arguments){
        return MessageFormat.format(bundle.getString(key), arguments);
    }
    
    public static SOCStringManager getClientManager(){
        if(clientManager == null)
            clientManager = new SOCStringManager("soc/client/strings/data");
        
        return clientManager;
    }

}
