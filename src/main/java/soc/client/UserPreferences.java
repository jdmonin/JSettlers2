/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
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
package soc.client;

import java.awt.EventQueue;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Gathered static methods to use persistent user preferences if available, or defaults if not.
 *<P>
 * Before v2.0.00 these methods were in {@code SOCPlayerClient} itself.
 * Method names are simplifed to prevent redundancy:
 * v1.x {@code getUserPreference(..)} -> v2.x {@code UserPreferences.getPref(..)}, etc.
 *<P>
 * Because the user preference storage namespace is based on the {@code soc.client} package
 * and not a class name, preferences are shared among v1.x.xx, v2.0, and higher versions.
 *<P>
 * Is public for possible use by anyone extending JSettlers in a different package.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class UserPreferences
{
    /**
     * Persistent user preferences like {@link SOCPlayerClient#PREF_SOUND_ON}, or {@code null} if none could be loaded.
     * @since 1.2.00
     */
    private static Preferences userPrefs;
    static
    {
        try
        {
            userPrefs = Preferences.userNodeForPackage(SOCPlayerInterface.class);
        }
        catch (Exception e) {}
    }

    /**
     * Get a boolean persistent user preference if available, or the default value.
     *<P>
     * Before v2.0.00 this method was {@code getUserPreference}.
     *
     * @param prefKey  Preference name key, such as {@link SOCPlayerClient#PREF_SOUND_ON}
     * @param dflt  Default value to get if no preference, or if {@code prefKey} is null
     * @return  Preference value or {@code dflt}
     * @see #putPref(String, boolean)
     * @see #getPref(String, int)
     * @since 1.2.00
     */
    public static boolean getPref(final String prefKey, final boolean dflt)
    {
        if (userPrefs == null)
            return dflt;

        try
        {
            return userPrefs.getBoolean(prefKey, dflt);
        } catch (RuntimeException e) {
            return dflt;
        }
    }

    /**
     * Get an int persistent user preference if available, or the default value.
     *<P>
     * Before v2.0.00 this method was {@code getUserPreference}.
     *
     * @param prefKey  Preference name key, such as {@link SOCPlayerClient#PREF_BOT_TRADE_REJECT_SEC}
     * @param dflt  Default value to get if no preference, or if {@code prefKey} is null
     * @return  Preference value or {@code dflt}
     * @see #putPref(String, int)
     * @see #getPref(String, boolean)
     * @since 1.2.00
     */
    public static int getPref(final String prefKey, final int dflt)
    {
        if (userPrefs == null)
            return dflt;

        try
        {
            return userPrefs.getInt(prefKey, dflt);
        } catch (RuntimeException e) {
            return dflt;
        }
    }

    /**
     * Set a boolean persistent user preference, if available.
     * Asynchronously calls {@link Preferences#flush()}.
     *<P>
     * Before v2.0.00 this method was {@code putUserPreference}.
     *
     * @param prefKey  Preference name key, such as {@link SOCPlayerClient#PREF_SOUND_ON}
     * @param val  Value to set
     * @throws NullPointerException if {@code prefKey} is null
     * @throws IllegalArgumentException if {@code prefKey} is longer than {@link Preferences#MAX_KEY_LENGTH}
     * @see #getPref(String, boolean)
     * @see #putPref(String, int)
     * @see #clear(String)
     * @since 1.2.00
     */
    public static void putPref(final String prefKey, final boolean val)
        throws NullPointerException, IllegalArgumentException
    {
        if (userPrefs == null)
            return;

        try
        {
            userPrefs.putBoolean(prefKey, val);
        }
        catch (IllegalStateException e) {}  // unlikely

        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                try
                {
                    userPrefs.flush();
                }
                catch (BackingStoreException e) {}
            }
        });
    }

    /**
     * Set an int persistent user preference, if available.
     * Asynchronously calls {@link Preferences#flush()}.
     *<P>
     * Before v2.0.00 this method was {@code putUserPreference}.
     *
     * @param prefKey  Preference name key, such as {@link SOCPlayerClient#PREF_BOT_TRADE_REJECT_SEC}
     * @param val  Value to set
     * @throws NullPointerException if {@code prefKey} is null
     * @throws IllegalArgumentException if {@code prefKey} is longer than {@link Preferences#MAX_KEY_LENGTH}
     * @see #getPref(String, int)
     * @see #putPref(String, boolean)
     * @see #clear(String)
     * @since 1.2.00
     */
    public static void putPref(final String prefKey, final int val)
        throws NullPointerException, IllegalArgumentException
    {
        if (userPrefs == null)
            return;

        try
        {
            userPrefs.putInt(prefKey, val);
        }
        catch (IllegalStateException e) {}  // unlikely

        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                try
                {
                    userPrefs.flush();
                }
                catch (BackingStoreException e) {}
            }
        });
    }

    /**
     * Clear some user preferences by removing the value stored for their key(s).
     * (Calls {@link Preferences#remove(String)}, not {@link Preferences#clear()}).
     * Calls {@link Preferences#flush()} afterwards. Prints a "Cleared" message
     * to {@link System#err} with {@code prefKeyList}.
     *<P>
     * Before v2.0.00 this method was {@code clearUserPreferences}.
     *
     * @param prefKeyList  Preference name key(s) to clear, same format
     *     as {@link SOCPlayerClient#PROP_JSETTLERS_DEBUG_CLEAR__PREFS}.
     *     Does nothing if {@code null} or "". Keys on this list do not
     *     all have to exist with a value; key name typos will not throw
     *     an exception.
     * @since 1.2.00
     */
    public static final void clear(final String prefKeyList)
    {
        if ((prefKeyList == null) || (prefKeyList.length() == 0) || (userPrefs == null))
            return;

        for (String key : prefKeyList.split(","))
        {
            try
            {
                userPrefs.remove(key);
            } catch (IllegalStateException e) {}
        }

        try
        {
            userPrefs.flush();
        }
        catch (BackingStoreException e) {}

        System.err.println("Cleared user preferences: " + prefKeyList);
    }

}
