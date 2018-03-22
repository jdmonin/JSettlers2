/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013 Luis A. Ramirez <lartkma@gmail.com>
 * Some parts of this file Copyright (C) 2013,2017-2018 Jeremy D Monin <jeremy@nand.net>
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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import net.nand.util.i18n.mgr.StringManager;

import soc.game.SOCDevCard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCInventoryItem;
import soc.game.SOCResourceSet;
import soc.proto.Data;

/**
 * String Manager for retrieving I18N localized text from {@code .properties} bundle files
 * with special methods for formatting JSettlers objects.
 *<P>
 * See comments at the top of {@code .properties} files for more details on key-value formatting and message parameters.
 * Remember that {@code .properties} bundle files are encoded not in {@code UTF-8} but in {@code ISO-8859-1}:
 *<UL>
 * <LI> <A href="http://docs.oracle.com/javase/1.5.0/docs/api/java/util/Properties.html#encoding"
 *       >java.util.Properties</A> (Java 1.5)
 * <LI> <A href="http://stackoverflow.com/questions/4659929/how-to-use-utf-8-in-resource-properties-with-resourcebundle"
 *       >Stack Overflow: How to use UTF-8 in resource properties with ResourceBundle</A> (asked on 2011-01-11)
 *</UL>
 * Introduced in v2.0.00; network messages sending localized text should check the receiver's version
 * against {@link #VERSION_FOR_I18N}.
 *
 * @author lartkma
 * @see soc.util.I18n
 * @since 2.0.00
 */
public class SOCStringManager extends StringManager
{
    /**
     * Minimum version (2.0.00) of client/server with I18N localization.
     * Network messages sending localized text should check the receiver's version against this constant.
     */
    public static final int VERSION_FOR_I18N = 2000;

    /**
     * Path and prefix of {@link #getServerManagerForClient(Locale)} properties files.
     */
    public static final String PROPS_PATH_SERVER_FOR_CLIENT = "resources/strings/server/toClient";

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
     * Set if needed by {@link #getFallbackServerManagerForClient()}.
     */
    private static SOCStringManager serverManagerForClientLocale_fallback;

    /**
     * Create a string manager for the bundles at {@code bundlePath} with the default locale.
     * Remember that bundle files are encoded not in {@code UTF-8} but in {@code ISO-8859-1}, see class javadoc.
     * @param bundlePath  Bundle path, will be retrieved with {@link ResourceBundle#getBundle(String)}
     */
    public SOCStringManager(String bundlePath)
    {
        super(bundlePath);
    }

    /**
     * Create a string manager for the bundles at {@code bundlePath} with a certain Locale.
     * Remember that bundle files are encoded not in {@code UTF-8} but in {@code ISO-8859-1}, see class javadoc.
     * @param bundlePath  Bundle path, will be retrieved with {@link ResourceBundle#getBundle(String, Locale)}
     * @param loc  Locale to use; not {@code null}
     */
    public SOCStringManager(final String bundlePath, final Locale loc)
    {
        super(bundlePath, loc);
    }

    // If you add get methods, for server convenience also add them in Connection and classes implementing that.

    /**
     * Resource type-and-count text keys for {@link #getSpecial(SOCGame, String, Object...)}.
     * Each subarray's indexes are the same values as {@link Data.ResourceType#CLAY_VALUE}
     * to {@link Data.ResourceType#WOOD_VALUE}. The string key at index 0 is used for
     * resources out of range (unknown types).
     * @see #getSOCResourceCount(int, Integer)
     */
    private static final String[][] GETSPECIAL_RSRC_KEYS =
    {
        {     // 1
            "spec.rsrcs.1unknown", "spec.rsrcs.1clay", "spec.rsrcs.1ore", "spec.rsrcs.1sheep", "spec.rsrcs.1wheat", "spec.rsrcs.1wood"
        }, {  // n
            "spec.rsrcs.nunknown", "spec.rsrcs.nclay", "spec.rsrcs.nore", "spec.rsrcs.nsheep", "spec.rsrcs.nwheat", "spec.rsrcs.nwood"
        }, {  // a, an
            "spec.rsrcs.aunknown", "spec.rsrcs.aclay", "spec.rsrcs.aore", "spec.rsrcs.asheep", "spec.rsrcs.awheat", "spec.rsrcs.awood"
        }, {  // resource type names, all of 1 resource ("la madera")
            "spec.rsrcs.unknown", "spec.rsrcs.clay", "spec.rsrcs.ore", "spec.rsrcs.sheep", "spec.rsrcs.wheat", "spec.rsrcs.wood"
        }
    };

    /**
     * Get a resource count, such as "5 sheep"; used by {@link #getSpecial(SOCGame, String, Object...)}.
     * @param rtype  Type of resource, in the range {@link Data.ResourceType#CLAY_VALUE}
     *     to {@link Data.ResourceType#WOOD_VALUE}
     * @param rcountObj  Resource count; uses the Integer object passed into {@code getSpecial}.
     *          As a special case, -1 will localize with the indefinite article, such as "a sheep" or "an ore".
     *          -2 will localize to the plural resource name without a count, as in "clay" or "la lana".
     * @return  A localized string such as "1 wood" or "5 clay" or "a sheep", or if {@code rtype} is out of range,
     *          "3 resources of unknown type 37"
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     */
    public final String getSOCResourceCount(final int rtype, final Integer rcountObj)
        throws MissingResourceException
    {
        final int rcount = rcountObj;

        // arrays of resource type string keys for 1, n, or a/an
        final String[] rkeyArray;
        {
            final int idx;
            switch (rcount)
            {
            case  1:  idx = 0;  break;  // 1 resource
            case -1:  idx = 2;  break;  // an/a resource
            case -2:  idx = 3;  break;  // resource [no count shown]
            default:  idx = 1;          // n resources
            }
            rkeyArray = GETSPECIAL_RSRC_KEYS[idx];
        }

        final String resText;
        if ((rtype >= Data.ResourceType.CLAY_VALUE) && (rtype <= Data.ResourceType.WOOD_VALUE))
        {
            if ((rcount == 1) || (rcount == -1))
                resText = bundle.getString(rkeyArray[rtype]);
            else
                resText = MessageFormat.format(bundle.getString(rkeyArray[rtype]), rcountObj);
        } else {
            // out of range, unknown type
            if ((rcount == 1) || (rcount < 0))
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
     *     resource type Integer in the range {@link Data.ResourceType#CLAY_VALUE} - {@link Data.ResourceType#WOOD_VALUE}.
     *     Special case: A count of -1 will localize with "a/an", such as "a sheep" or "an ore".
     *     A count of -2 will localize to the plural resource name without a number, for uses such as "Joe monopolized clay".
     *<LI> <tt>{0,dcards}</tt> for a Development Card or list of dev cards.
     *     {@code arguments} should contain a single Integer or {@link SOCInventoryItem}, or a {@link List} of them,
     *     in the range {@link SOCDevCardConstants#MIN} - {@link SOCDevCardConstants#KNIGHT}.
     *     <P>
     *     The returned format will include indefinite articles: "a Year of Plenty", "a Market (1 VP)", etc.
     *</UL>
     *<P>
     * To skip key retrieval, call {@link #formatSpecial(SOCGame, String, Object...)} instead of this method.
     *
     * @param game  Game, in case its options influence the strings (such as dev card Knight -> Warship in scenario _SC_PIRI)
     * @param key  Key to use for string retrieval. The retrieved string can contain <tt>{0,rsrcs}</tt> and/or
     *            <tt>{0,dcards}</tt>. You can use <tt>{1</tt>, <tt>{2</tt>, or any other slot number.
     * @param arguments  Objects to go with <tt>{0,list}</tt>, <tt>{0,rsrcs}</tt>, <tt>{0,dcards}</tt>, etc in {@code key};
     *            see above for the expected object types.
     * @return the localized formatted string from the manager's bundle or one of its parents
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @throws IllegalArgumentException if the localized pattern string has a parse error (closing '}' brace without
     *     opening '{' brace, etc)
     * @see #getSOCResourceCount(int, Integer)
     */
    public String getSpecial(final SOCGame game, final String key, Object ... arguments)
        throws MissingResourceException, IllegalArgumentException
    {
        return formatSpecial(game, bundle.getString(key), arguments);
    }

    /**
     * Format an already-localized format string (with special SoC-specific parameters).
     * Called by {@code getSpecial(...)} after it retrieves the string from this manager's bundle.
     *<P>
     * See {@link #getSpecial(SOCGame, String, Object...)} for most javadocs,including parameters and returns.
     * @param game  Game, in case its options influence the strings (such as dev card Knight -> Warship in scenario _SC_PIRI)
     * @param txtfmt  Formatting string, already looked up by {@link ResourceBundle#getString(String)}
     *     or from another source
     * @param arguments Objects to go with {@code txtfmt}; details are in {@code getSpecial(..)} javadoc
     */
    public String formatSpecial(final SOCGame game, String txtfmt, Object ... arguments)
        throws MissingResourceException, IllegalArgumentException
    {
        /** Clone of arguments, with specials replaced with their localized strings */
        Object[] argsLocal = null;

        // look for any "{#,rsrcs}" parameter here, and replace that arg with a String
        int ir = txtfmt.indexOf(",rsrcs}");
        while (ir != -1)
        {
            final int i0 = txtfmt.lastIndexOf('{', ir - 1);
            if (i0 == -1)
                throw new IllegalArgumentException("Missing '{' before ',rsrcs}' in pattern: " + txtfmt);

            if (argsLocal == null)
                argsLocal = (Object[]) (arguments.clone());

            final int pnum = Integer.parseInt(txtfmt.substring(i0 + 1, ir));
            final Object arg = argsLocal[pnum];
            if (arg instanceof Integer)
            {
                // [pnum] is rcount, [pnum+1] is rtype;
                // replace the argument obj with its localized String
                argsLocal[pnum] = getSOCResourceCount
                    (((Integer) arguments[pnum + 1]).intValue(), (Integer) arg);
            }
            else if (arg instanceof SOCResourceSet)
            {
                final SOCResourceSet rset = (SOCResourceSet) (arg);
                ArrayList<String> resList = new ArrayList<String>();
                for (int rtype = Data.ResourceType.CLAY_VALUE; rtype <= Data.ResourceType.WOOD_VALUE; ++rtype)
                {
                    int n = rset.getAmount(rtype);
                    if (n > 0)
                        resList.add(getSOCResourceCount(rtype, Integer.valueOf(n)));
                }

                // replace the argument obj
                if (resList.isEmpty())
                    argsLocal[pnum] = bundle.getString("spec.rsrcs.none");  // "nothing"
                else
                    argsLocal[pnum] = I18n.listItems(resList, this);

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

            if (argsLocal == null)
                argsLocal = (Object[]) (arguments.clone());

            final int pnum = Integer.parseInt(txtfmt.substring(i0 + 1, ir));
            final Object arg = argsLocal[pnum];
            if (arg instanceof List)
            {
                // replace the argument obj with String of its localized items
                argsLocal[pnum] = I18n.listItems((List<?>) arg, this);
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

            if (argsLocal == null)
                argsLocal = (Object[]) (arguments.clone());

            final int pnum = Integer.parseInt(txtfmt.substring(i0 + 1, ir));
            final Object arg = argsLocal[pnum];
            if (arg instanceof Integer)
            {
                // replace the argument obj with its localized String
                argsLocal[pnum] = SOCDevCard.getCardTypeName(((Integer) arg), game, true, this);
            }
            else if (arg instanceof SOCInventoryItem)
            {
                // replace the argument obj with its localized String
                argsLocal[pnum] = ((SOCInventoryItem) arg).getItemName(game, true, this);
            }
            else if (arg instanceof List)
            {
                // replace the argument obj with String of its localized items
                final int L = ((List<?>) arg).size();
                if (L == 0)
                {
                    argsLocal[pnum] = bundle.getString("base.emptylist.nothing");  // "nothing"
                } else {
                    ArrayList<String> resList = new ArrayList<String>(L);
                    for (Object itm : ((List<?>) arg))
                    {
                        if (itm instanceof Integer)
                            resList.add(SOCDevCard.getCardTypeName(((Integer) itm).intValue(), game, true, this));
                        else if (itm instanceof SOCInventoryItem)
                            resList.add(((SOCInventoryItem) itm).getItemName(game, true, this));
                        else
                            resList.add(itm.toString());
                    }

                    argsLocal[pnum] = I18n.listItems(resList, this);
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
        if (argsLocal == null)
            argsLocal = arguments;

        return MessageFormat.format(txtfmt, argsLocal);
    }

    /**
     * Create or retrieve the cached client string manager, with the default Locale.
     * If the client manager already exists, further gets will return that manager
     * with its Locale, ignoring the default locale of the new call.
     * @return  The client manager
     * @see #getClientManager(Locale)
     */
    public static SOCStringManager getClientManager()
    {
        if(clientManager == null)
            clientManager = new SOCStringManager("resources/strings/client/data");

        return clientManager;
    }

    /**
     * Create or retrieve the cached client string manager, with a certain Locale.
     * If the client manager already exists, further gets will return that manager
     * with its Locale, ignoring the Locale of the new call.
     * @param loc  Locale to use; not {@code null}
     * @return  The client manager
     * @see #getClientManager()
     */
    public static SOCStringManager getClientManager(Locale loc)
    {
        if (clientManager == null)
            clientManager = new SOCStringManager("resources/strings/client/data", loc);

        return clientManager;
    }

    /**
     * Create or retrieve the server's string manager to send text to a clients with a certain locale.
     * @param loc  Locale to use, or {@code null} to use {@link Locale#getDefault()}
     * @return  The server manager for that client locale
     */
    public static SOCStringManager getServerManagerForClient(Locale loc)
    {
        if (loc == null)
            loc = Locale.getDefault();

        final String lstr = loc.toString();
        SOCStringManager smc = serverManagerForClientLocale.get(lstr);
        if (smc == null)
        {
            smc = new SOCStringManager(PROPS_PATH_SERVER_FOR_CLIENT, loc);
            serverManagerForClientLocale.put(lstr, smc);
        }

        return smc;
    }

    /**
     * Create or retrieve the server's string manager for fallback to send text to clients with unknown locale.
     * Can be used for messages while a client hasn't yet sent their locale.
     * @return  The server string manager with default locale
     *     from {@link #getServerManagerForClient(Locale) getServerManagerForClient(null)}
     */
    public static SOCStringManager getFallbackServerManagerForClient()
    {
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
