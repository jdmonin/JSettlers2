/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2014-2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGameOption;  // for javadocs only
import soc.game.SOCGameOptionSet;  // for javadocs only

/**
 * Set of optional server features or client features that are currently active.
 * Sent during connect via {@link soc.message.SOCVersion} message fields.
 * Each feature can be a flag (active or not) or, in v2.0.00 and newer, integer-valued.
 *<P>
 * Server features were added in v1.1.19 ({@link #VERSION_FOR_SERVERFEATURES})
 * to allow features (accounts, chat rooms, etc) to be turned off and to let clients know they're off.
 * Earlier clients assume the server is
 * using the features defined in 1.1.19. Use the {@link #SOCFeatureSet(boolean, boolean) SOCFeatureSet(true, true)}
 * constructor when connecting to a server older than 1.1.19. See that constructor's javadoc for the list of
 * server features always assumed active before 1.1.19.
 *<P>
 * Client features were added in v2.0.00 ({@link #VERSION_FOR_CLIENTFEATURES})
 * to simplify development of third-party bots and clients, which might not want to implement all features.
 * Earlier servers assume the client is using the features that standard JSettlers implements. Use the
 * {@link #SOCFeatureSet(boolean, boolean) SOCFeatureSet(true, false)} constructor when connecting to a client older
 * than 2.0.00. See that constructor's javadoc for the list of client features always assumed active before 2.0.00.
 *<P>
 * Feature name constants defined here are kept simple (lowercase alphanumerics, underscore, dash)
 * for encoding into network message fields.
 *<P>
 * Check active features with {@link #isActive(String)} and/or {@link #getValue(String, int)}.
 * Add a feature with {@link #add(String)} or {@link #add(String, int)}.
 *<P>
 * <b>Locks:</b> Not thread-safe.  Caller must guard against potential multi-threaded modifications or access.
 *<P>
 * Before v2.0.00 this class was {@code SOCServerFeatures}.
 *
 * @since 1.1.19
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class SOCFeatureSet
{
    // When updating this class, also update unit tests if needed: soctest.util.TestFeatureSet

    /** Minimum version (1.1.19) of client/server which send and recognize server features */
    public static final int VERSION_FOR_SERVERFEATURES = 1119;

    /**
     * Minimum version (2.0.00) of client/server which send and recognize client features
     * @since 2.0.00
     */
    public static final int VERSION_FOR_CLIENTFEATURES = 2000;

    // When adding a feature, also add it to TestFeatureSet.testConstantsUnchanged()

    // Server Features

    /**
     * Server feature User accounts defined in a persistent database.
     * If this feature is active, nicknames and passwords are authenticated.
     * Otherwise there are no passwords defined ({@link #SERVER_OPEN_REG}).
     *<P>
     * When this feature is active but the db is empty (new install),
     * the server will tell clients that {@link #SERVER_OPEN_REG} is active;
     * see that feature's javadoc for details.
     *<P>
     * The server can optionally be configured to require accounts,
     * see {@link soc.server.SOCServer#PROP_JSETTLERS_ACCOUNTS_REQUIRED}.
     *<P>
     * Before v2.0.00 this constant was {@code FEAT_ACCTS}.
     */
    public static final String SERVER_ACCOUNTS = "accts";

    /**
     * Server feature Chat channels.
     * If this feature is active, users are allowed to create chat channels.
     * Otherwise no channels are allowed.
     *<P>
     * Before v2.0.00 this constant was {@code FEAT_CHANNELS}.
     */
    public static final String SERVER_CHANNELS = "ch";

    /**
     * Server feature Open registration.
     * If this feature is active, anyone can create their own user accounts.
     * Otherwise only existing users can create new accounts ({@link #SERVER_ACCOUNTS}).
     *
     *<H5>Special case during {@code SERVER_ACCOUNTS} server install:</H5>
     * When a newly installed server requires authentication to create new accounts,
     * but no accounts exist in the database yet, the server tells clients that
     * {@code SERVER_OPEN_REG} is active so that {@code SOCAccountClient} won't
     * ask for a username and password.
     *<P>
     * Before v2.0.00 this constant was {@code FEAT_OPEN_REG}.
     */
    public static final String SERVER_OPEN_REG = "oreg";

    // Client Features

    /**
     * Client feature flag for 6-player games.
     * If not set, client can only play 4-player games,
     * not those where game option {@code PL > 4}.
     * @since 2.0.00
     */
    public static final String CLIENT_6_PLAYERS = "6pl";

    /**
     * Client feature flag for sea board layouts.
     * If not set, client can only play the "classic" boards,
     * not games where game option {@code SBL} is set.
     * @since 2.0.00
     */
    public static final String CLIENT_SEA_BOARD = "sb";

    /**
     * Client scenario version (numeric feature).
     * If not set, client doesn't support scenarios or game option {@code SC}.
     * Clients supporting scenarios should set this feature's value to the JSettlers version
     * having all scenarios they support: For example if the client supports all scenarios
     * included in JSettlers 2.0.01, the value would be 2001.
     *<P>
     * If client doesn't have this feature, server also assumes it doesn't support
     * any scenario-related {@link SOCGameOption}s (the options starting with {@code _SC_*},
     * like {@link SOCGameOptionSet#K_SC_3IP _SC_3IP}).
     *<P>
     * Should not be newer (larger) than the client's reported version.
     * If this value is newer than the client version, it will be reduced
     * to match that.
     * @since 2.0.00
     */
    public static final String CLIENT_SCENARIO_VERSION = "sc";

    /**
     * Separator character ';' between features in {@link #featureList}.
     * Chosen to avoid the {@code sep_char} and {@code sep2_char} separators defined in {@code SOCMessage}.
     */
    public static final char SEP_CHAR = ';';

    /**
     * Active feature list, or null if none.
     * If not null, the list starts and ends with {@link #SEP_CHAR} for ease of search.
     */
    private String featureList = null;

    /**
     * Create a new empty SOCFeatureSet, with none active or defaults active.
     * After construction, use {@link #add(String)} to add active features.
     * @param withOldDefaults  If false, nothing is active. If true, include the default features
     *     assumed active in servers or clients older than {@link SOCFeatureSet} support;
     *     see {@code withOldDefaultsForServer}
     * @param withOldDefaultsForServer  Include features for an old server, not for old client:
     *     <UL>
     *     <LI> If true, include features which were assumed always active in servers older than v1.1.19
     *         ({@link #VERSION_FOR_SERVERFEATURES}): {@link #SERVER_ACCOUNTS}, {@link #SERVER_CHANNELS},
     *         {@link #SERVER_OPEN_REG}.
     *     <LI> If false, include feature which was assumed always active in clients older than v2.0.00
     *         ({@link #VERSION_FOR_CLIENTFEATURES}): {@link #CLIENT_6_PLAYERS}.
     *     <LI> Ignored unless {@code withOldDefaults} is set.
     *     </UL>
     */
    public SOCFeatureSet(final boolean withOldDefaults, final boolean withOldDefaultsForServer)
    {
        if (withOldDefaults)
        {
            if (withOldDefaultsForServer)
                featureList = SEP_CHAR + SERVER_ACCOUNTS + SEP_CHAR + SERVER_CHANNELS
                    + SEP_CHAR + SERVER_OPEN_REG + SEP_CHAR;
            else
                featureList = SEP_CHAR + CLIENT_6_PLAYERS + SEP_CHAR;
        } else {
            // featureList is already empty (null)
        }
    }

    /**
     * Create a new SOCFeatureSet from an encoded list; useful at client.
     * @param encodedList  List from {@link #getEncodedList()}, or null or "" for none (not ";;")
     * @throws IllegalArgumentException if {@code encodedList} is not empty but
     *     doesn't start and end with {@link #SEP_CHAR}
     */
    public SOCFeatureSet(String encodedList)
        throws IllegalArgumentException
    {
        if (encodedList != null)
        {
            final int L = encodedList.length();
            if (L == 0)
                encodedList = null;
            else if ((encodedList.charAt(0) != SEP_CHAR) || (encodedList.charAt(L - 1) != SEP_CHAR))
                throw new IllegalArgumentException("Bad encoding: " + encodedList);
        }

        featureList = encodedList;
    }

    /**
     * Create a new SOCFeatureSet by copying another.
     * @param feats  Copy from this to create the new SOCFeatureSet
     * @throws NullPointerException if {@code feats} == null
     */
    public SOCFeatureSet(SOCFeatureSet feats)
        throws NullPointerException
    {
        super();
        featureList = feats.featureList;
    }

    /**
     * Is this feature active?
     * @param featureName  A defined feature name, such as {@link #SERVER_ACCOUNTS}
     * @return  True if {@code featureName} is in the features list, as either a flag or an int-valued feature
     * @throws IllegalArgumentException if {@code featureName} is null or ""
     * @see #getValue(String, int)
     */
    public boolean isActive(final String featureName)
        throws IllegalArgumentException
    {
        if ((featureName == null) || (featureName.length() == 0))
            throw new IllegalArgumentException("featureName: " + featureName);

        if (featureList == null)
            return false;

        return featureList.contains(SEP_CHAR + featureName + SEP_CHAR)
            || featureList.contains(SEP_CHAR + featureName + '=');
    }

    /**
     * Get the int value of this feature, if active.
     * @param featureName  A defined int-valued feature name
     * @param dflt  Default value if feature isn't found or is a boolean flag.
     *     If value can't be parsed as an integer, returns {@code dflt}.
     * @return  Feature's int value or {@code dflt}
     * @see #isActive(String)
     */
    public int getValue(final String featureName, final int dflt)
    {
        if ((featureName == null) || (featureName.length() == 0))
            throw new IllegalArgumentException("featureName: " + featureName);

        if (featureList == null)
            return dflt;

        int iStart = featureList.indexOf(SEP_CHAR + featureName + '=');
        if (iStart == -1)
            return dflt;
        iStart += featureName.length() + 2;  // move past SEP_CHAR + featureName + '='

        int iEnd = featureList.indexOf(SEP_CHAR, iStart);
        if (iEnd == -1)
            iEnd = featureList.length();  // just in case; shouldn't occur if well-formed

        try
        {
            return Integer.parseInt(featureList.substring(iStart, iEnd));
        } catch (RuntimeException e) {
            // IndexOutOfBoundsException, NumberFormatException; shouldn't occur if well-formed
            return dflt;
        }
    }

    /**
     * Add this active feature flag.
     * Must not already be in the set: Does not check for duplicates.
     * @param featureName  A defined feature flag name, such as {@link #SERVER_ACCOUNTS}
     *     or {@link #CLIENT_SEA_BOARD}
     * @throws IllegalArgumentException if {@code featureName} is null or ""
     *     or contains '=' or ';' ({@link #SEP_CHAR})
     * @see #add(String, int)
     * @see #remove(String)
     */
    public void add(final String featureName)
        throws IllegalArgumentException
    {
        if ((featureName == null) || (featureName.length() == 0)
            || (-1 != featureName.indexOf(SEP_CHAR)) || (-1 != featureName.indexOf('=')))
            throw new IllegalArgumentException("featureName: " + featureName);

        if (featureList == null)
            featureList = SEP_CHAR + featureName + SEP_CHAR;
        else
            featureList = featureList.concat(featureName + SEP_CHAR);
    }

    /**
     * Add this int-valued active feature.
     * Must not already be in the set: Does not check for duplicates.
     * @param featureName  A defined int-valued feature name, such as {@link #CLIENT_SCENARIO_VERSION}
     * @param val  Feature's value; any integer
     * @throws IllegalArgumentException if {@code featureName} is null or ""
     *     or contains '=' or ';' ({@link #SEP_CHAR})
     * @see #add(String)
     * @see #remove(String)
     * @since 2.0.00
     */
    public void add(final String featureName, final int val)
        throws IllegalArgumentException
    {
        if ((featureName == null) || (featureName.length() == 0)
            || (-1 != featureName.indexOf(SEP_CHAR)) || (-1 != featureName.indexOf('=')))
            throw new IllegalArgumentException("featureName: " + featureName);

        if (featureList == null)
            featureList = SEP_CHAR + featureName + "=" + val + SEP_CHAR;
        else
            featureList = featureList.concat(featureName + "=" + val + SEP_CHAR);
    }

    /**
     * Remove this feature if set contains it. Does nothing otherwise.
     * @param featureName  A defined feature name, such as {@link #SERVER_ACCOUNTS}
     *     or {@link #CLIENT_SCENARIO_VERSION}
     * @throws IllegalArgumentException if {@code featureName} is null or ""
     * @since 2.4.50
     */
    public void remove(final String featureName)
        throws IllegalArgumentException
    {
        if ((featureName == null) || (featureName.length() == 0))
            throw new IllegalArgumentException("featureName: " + featureName);

        if (featureList == null)
            return;

        final int L = featureName.length(), LTotal = featureList.length();
        int i = 0;
        while ((i > -1) && (i < LTotal))
        {
            i = featureList.indexOf(SEP_CHAR + featureName, i);
            if (i == -1)
                return;

            int iNext = i + L + 1;  // index of char after featureName
            if (iNext < LTotal)
            {
                int ch = featureList.charAt(iNext);
                if (ch == '=')
                {
                    // name matched; also remove =value
                    iNext = featureList.indexOf(SEP_CHAR, iNext + 1);
                }
                else if (ch != SEP_CHAR)
                {
                    // matched a different feature whose name starts with featureName
                    i = iNext;
                    continue;
                }

                ++iNext;  // just after SEP_CHAR

                if ((i == 0) && (iNext == LTotal))
                    featureList = null;
                else
                    featureList = featureList.substring(0, i + 1)
                        + (((iNext > 0) && (iNext < LTotal)) ? featureList.substring(iNext) : "");

                return;
            } else {
                // featureList malformed: missing final SEP_CHAR
                featureList = featureList.substring(0, i);

                return;
            }
        }
    }

    /**
     * Compare this set against another; does it contain all the features of the other?
     * For features with an int value, value in {@code this} set must be &gt;=
     * the feature's value in {@code otherSet}. If somehow the feature is boolean in
     * one set and int-valued in the other, 0 is used as the boolean feature's int value.
     * @param otherSet  Feature set to compare against, or {@code null}
     * @param stopAtFirstFound  True if caller needs to know only if any features are missing,
     *     but doesn't need the full list. Will stop checking after finding any missing feature.
     * @return {@code null} if this set is a superset containing all features in {@code otherSet},
     *     or if {@code otherSet} is {@code null}.<BR>
     *     Otherwise, the list of "missing" features which are in {@code otherSet} but not this set,
     *     separated by {@link #SEP_CHAR} but not preceded/followed by it like {@link #getEncodedList()} does.
     *     Within the returned list, items will be in the same order as in
     *     {@link SOCFeatureSet#getEncodedList() otherSet.getEncodedList()}.
     *     If {@code stopAtFirstFound}, might return one missing feature instead of the full list.
     * @since 2.0.00
     */
    public String findMissingAgainst(SOCFeatureSet otherSet, final boolean stopAtFirstFound)
    {
        if ((otherSet == null) || (otherSet.featureList == null))
            return null;

        final String fList = otherSet.featureList;
        final int L = fList.length();
        if (L < 2)
            return null;

        // loop through fList, checking our own list
        StringBuilder missingList = null;
        for (int i = fList.indexOf(SEP_CHAR, 1), iprev = 0;  // assumes fList is well-formed and starts with ';'
             ;
             iprev = i, i = fList.indexOf(SEP_CHAR, i+1))
        {
            if (i == -1)
                i = L;

            if (i - iprev > 1)
            {
                String missingItem = null;
                String f = fList.substring(iprev + 1, i);
                int ieq = f.indexOf('=');
                if (ieq == -1)
                {
                    if (! isActive(f))
                    {
                        missingItem = f;
                    }
                } else if (ieq == 0) {
                    missingItem = "?";  // malformed
                } else {
                    String name = f.substring(0, ieq), val = f.substring(ieq + 1);
                    int ival = 0;
                    if (val.length() > 0)
                        try {
                            ival = Integer.parseInt(val);
                            int ourval = getValue(name, ival - 1);
                            if (ourval < ival)
                                missingItem = f;  // name + "=" + val
                        } catch (NumberFormatException e) {
                            missingItem = name;
                        }
                }

                if (missingItem != null)
                {
                    if (stopAtFirstFound)
                        return missingItem;  // <--- Early return ---

                    if (missingList == null)
                        missingList = new StringBuilder();
                    else
                        missingList.append(SOCFeatureSet.SEP_CHAR);

                    missingList.append(missingItem);
                }
            }

            if (i >= (L-1))
                break;
        }

        return (missingList != null) ? missingList.toString() : null;
    }

    /**
     * Get the encoded list of all active features, to send to a
     * client or server for {@link #SOCFeatureSet(String)}.
     *<P>
     * Features are separated by {@link #SEP_CHAR}. Integer-valued features are encoded as <tt>featurename=value</tt>.
     * If not null or empty, the list starts and ends with {@link #SEP_CHAR} for ease of search.
     *<P>
     * Example: <tt>";6pl;sb;sc=2000;"</tt>
     * @return The active features list, or null if none
     */
    public String getEncodedList()
    {
        return featureList;
    }

    /**
     * Human-readable representation of active features.
     * Based on super.toString + featureList. Possible Formats:
     *<UL>
     * <LI> soc.util.SOCFeatureSet@86c347{;accts;ch;}
     * <LI> soc.util.SOCFeatureSet@f7e6a96{(empty)}
     *</UL>
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append('{');
        sb.append((featureList != null) ? featureList : "(empty)");
        sb.append('}');
        return sb.toString();
    }

}
