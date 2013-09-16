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
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import soc.game.SOCDevCard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;

/**
 * TODO Write JavaDoc.
 *<P>
 * See comments at the top of {@code .properties} files for more details on key-value formatting and message parameters.
 * Remember that {@code .properties} bundle files are encoded not in {@code UTF-8} but in {@code ISO-8859-1}:
 *<UL>
 * <LI> <A href="http://docs.oracle.com/javase/1.5.0/docs/api/java/util/Properties.html#encoding"
 *       >java.util.Properties</A> (Java 1.5)
 * <LI> <A href="http://stackoverflow.com/questions/4659929/how-to-use-utf-8-in-resource-properties-with-resourcebundle"
 *       >Stack Overflow: How to use UTF-8 in resource properties with ResourceBundle</A> (asked on 2011-01-11)
 *</UL>
 *
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
     * Remember that bundle files are encoded not in {@code UTF-8} but in {@code ISO-8859-1}, see class javadoc.
     * @param bundlePath  Bundle path, will be retrieved with {@link ResourceBundle#getBundle(String)}
     */
    public SOCStringManager(String bundlePath){
        bundle = ResourceBundle.getBundle(bundlePath);
    }

    /**
     * Create a string manager for the bundles at {@code bundlePath} with a certain Locale.
     * Remember that bundle files are encoded not in {@code UTF-8} but in {@code ISO-8859-1}, see class javadoc.
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
     */
    public final String get(final String key, final Object ... arguments)
        throws MissingResourceException
    {
        return MessageFormat.format(bundle.getString(key), arguments);
    }

    /**
     * Resource type-and-count text keys for {@link #getSpecial(SOCGame, String, Object...)}.
     * Each subarray's indexes are the same values as {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}.
     * The string key at index 0 is used for resources out of range (unknown types).
     */
    private static final String[][] GETSPECIAL_RSRC_KEYS =
    {
        {     // 1
            "spec.rsrcs.1unknown", "spec.rsrcs.1clay", "spec.rsrcs.1ore", "spec.rsrcs.1sheep", "spec.rsrcs.1wheat", "spec.rsrcs.1wood"
        }, {  // n
            "spec.rsrcs.nunknown", "spec.rsrcs.nclay", "spec.rsrcs.nore", "spec.rsrcs.nsheep", "spec.rsrcs.nwheat", "spec.rsrcs.nwood"
        }, {  // a, an
            "spec.rsrcs.aunknown", "spec.rsrcs.aclay", "spec.rsrcs.aore", "spec.rsrcs.asheep", "spec.rsrcs.awheat", "spec.rsrcs.awood"
        }
    };

    /**
     * Get a resource count, such as "5 sheep"; used by {@link #getSpecial(SOCGame, String, Object...)}. 
     * @param rtype  Type of resource, in the range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     * @param rcountObj  Resource count; uses the Integer object passed into {@code getSpecial}.
     *          As a special case, -1 will localize with the indefinite article, such as "a sheep" or "an ore"
     * @return  A localized string such as "1 wood" or "5 clay" or "a sheep", or if {@code rtype} is out of range,
     *          "3 resources of unknown type 37"
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     */
    public final String getSOCResourceCount(final int rtype, final Integer rcountObj)
        throws MissingResourceException
    {
        final int rcount = rcountObj;

        // keys for 1, n, or a/an
        final String[] rkeyArray = GETSPECIAL_RSRC_KEYS[(rcount == 1) ? 0 : ((rcount != -1) ? 1 : 2)];

        final String resText;
        if ((rtype >= SOCResourceConstants.CLAY) && (rtype <= SOCResourceConstants.WOOD))
        {
            if ((rcount == 1) || (rcount == -1))
                resText = bundle.getString(rkeyArray[rtype]);
            else
                resText = MessageFormat.format(bundle.getString(rkeyArray[rtype]), rcountObj);
        } else {
            // out of range, unknown type
            if ((rcount == 1) || (rcount == -1))
                resText = MessageFormat.format(bundle.getString(rkeyArray[0]), rtype);
            else
                resText = MessageFormat.format(bundle.getString(rkeyArray[0]), rcountObj, rtype);
        }

        return resText;
    }

    /**
     * Get and format a localized string (with special SoC-specific parameters) with the given key.
     *<UL>
     *<LI> <tt>{0,list}</tt> for a list of items, which will be formatted as "x, y, and z"
     *     by {@link I18n#listItems(List, SOCStringManager)}.  Use a {@link List} in {@code arguments}.
     *<LI> <tt>{0,rsrcs}</tt> for a resource name or resource set.
     *     A resource set is passed as a {@link SOCResourceSet} in {@code arguments}.
     *     Resource names ("5 sheep") take 2 argument slots: an Integer for the count, and a
     *     resource type Integer in the range {@link SOCResourceConstants#CLAY} - {@link SOCResourceConstants#WOOD}.
     *     Special case: A count of -1 will localize with "a/an", such as "a sheep" or "an ore".
     *<LI> <tt>{0,dcards}</tt> for a Development Card or list of dev cards.
     *     {@code arguments} should contain a single Integer, or a {@link List} of them,
     *     in the range {@link SOCDevCardConstants#MIN} - {@link SOCDevCardConstants#TOW}.
     *     <P>
     *     The returned format will include indefinite articles: "a Year of Plenty", "a Market (1 VP)", etc.
     *</UL>
     *
     * @param game  Game, in case its options influence the strings (such as dev card Knight -> Warship in scenario _SC_PIRI)
     * @param key  Key to use for string retrieval. Can contain <tt>{0,rsrcs}</tt> and or <tt>{0,dcards}</tt>.
     *            You can use <tt>{1</tt>, <tt>{2</tt>, or any other slot number.
     * @param arguments  Objects to go with <tt>{0,list}</tt>, <tt>{0,rsrcs}</tt>, <tt>{0,dcards}</tt>, etc in {@code key};
     *            see above for the expected object types.
     * @return the localized formatted string from the manager's bundle or one of its parents
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @throws IllegalArgumentException if the localized pattern string has a parse error (closing '}' brace without opening '{' brace, etc)
     * @see #getSOCResourceCount(int, Integer)
     */
    public String getSpecial(final SOCGame game, final String key, Object ... arguments)
        throws MissingResourceException, IllegalArgumentException
    {
        String txtfmt = bundle.getString(key);

        // look for any "{#,rsrcs}" parameter here, and replace that arg with a String
        int ir = txtfmt.indexOf(",rsrcs}");
        while (ir != -1)
        {
            final int i0 = txtfmt.lastIndexOf('{', ir - 1);
            if (i0 == -1)
                throw new IllegalArgumentException("Missing '{' before ',rsrcs}' in pattern: " + txtfmt);

            final int pnum = Integer.parseInt(txtfmt.substring(i0 + 1, ir));
            if (arguments[pnum] instanceof Integer)
            {
                // [pnum] is rcount, [pnum+1] is rtype;
                // replace the argument obj with its localized String 
                arguments[pnum] = getSOCResourceCount
                    (((Integer) arguments[pnum + 1]).intValue(), (Integer) arguments[pnum]);
            }
            else if (arguments[pnum] instanceof SOCResourceSet)
            {
                final SOCResourceSet rset = (SOCResourceSet) (arguments[pnum]); 
                ArrayList<String> resList = new ArrayList<String>();
                for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype)
                {
                    int n = rset.getAmount(rtype);
                    if (n > 0)
                        resList.add(getSOCResourceCount(rtype, Integer.valueOf(n)));
                }

                // replace the argument obj
                if (resList.isEmpty())
                    arguments[pnum] = bundle.getString("spec.rsrcs.none");  // "nothing"
                else
                    arguments[pnum] = I18n.listItems(resList, this);

            } else {
                // keep obj as whatever it is; MessageFormat.format will call its toString()
            }

            // splice the format string: "{#,rsrcs}" -> "{#}"
            txtfmt = txtfmt.substring(0, ir) + txtfmt.substring(ir + 6);

            // look for any others (at top of loop)
            ir = txtfmt.indexOf(",rsrcs}");
        }

        // look for any "{#,list}" parameter here, and replace that arg with a String
        ir = txtfmt.indexOf(",list}");
        while (ir != -1)
        {
            final int i0 = txtfmt.lastIndexOf('{', ir - 1);
            if (i0 == -1)
                throw new IllegalArgumentException("Missing '{' before ',list}' in pattern: " + txtfmt);

            final int pnum = Integer.parseInt(txtfmt.substring(i0 + 1, ir));
            if (arguments[pnum] instanceof List)
            {
                // replace the argument obj with String of its localized items
                arguments[pnum] = I18n.listItems((List<?>) arguments[pnum], this);
            } else {
                // keep obj as whatever it is; MessageFormat.format will call its toString()
            }

            // splice the format string: "{#,list}" -> "{#}"
            txtfmt = txtfmt.substring(0, ir) + txtfmt.substring(ir + 5);

            // look for any others (at top of loop)
            ir = txtfmt.indexOf(",list}");
        }

        // look for any "{#,dcards}" parameter here, and replace that arg with a String
        ir = txtfmt.indexOf(",dcards}");
        while (ir != -1)
        {
            final int i0 = txtfmt.lastIndexOf('{', ir - 1);
            if (i0 == -1)
                throw new IllegalArgumentException("Missing '{' before ',dcards}' in pattern: " + txtfmt);

            final int pnum = Integer.parseInt(txtfmt.substring(i0 + 1, ir));
            if (arguments[pnum] instanceof Integer)
            {
                // replace the argument obj with its localized String 
                arguments[pnum] = SOCDevCard.getCardTypeName(((Integer) arguments[pnum]), game, true, this);
            }
            else if (arguments[pnum] instanceof List)
            {
                // replace the argument obj with String of its localized items 
                final int L = ((List<?>) arguments[pnum]).size();
                if (L == 0)
                {
                    arguments[pnum] = bundle.getString("base.emptylist.nothing");  // "nothing"
                } else {
                    ArrayList<String> resList = new ArrayList<String>(L);
                    for (Object itm : ((List<?>) arguments[pnum]))
                    {
                        if (itm instanceof Integer)
                            resList.add(SOCDevCard.getCardTypeName(((Integer) itm).intValue(), game, true, this));
                        else
                            resList.add(itm.toString());
                    }

                    arguments[pnum] = I18n.listItems(resList, this);
                }
            } else {
                // keep obj as whatever it is; MessageFormat.format will call its toString()
            }

            // splice the format string: "{#,dcards}" -> "{#}"
            txtfmt = txtfmt.substring(0, ir) + txtfmt.substring(ir + 7);

            // look for any others (at top of loop)
            ir = txtfmt.indexOf(",dcards}");
        }

        // now format the rest of the message:
        return MessageFormat.format(txtfmt, arguments);
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
