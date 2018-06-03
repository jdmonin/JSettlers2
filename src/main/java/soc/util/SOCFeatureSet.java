/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2014-2018 Jeremy D Monin <jeremy@nand.net>
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

/**
 * Set of optional server features or client features that are currently active.
 * Sent during connect via {@link soc.message.SOCVersion} message fields.
 *<P>
 * Added in v1.1.19 ({@link #VERSION_FOR_SERVERFEATURES}); earlier clients assume the server is using the
 * features defined in 1.1.19. Use the {@link #SOCFeatureSet(boolean, boolean) SOCFeatureSet(true, true)} constructor
 * when connecting to a server older than 1.1.19. See that constructor's javadoc for the list of features
 * always assumed active before 1.1.19.
 *<P>
 * Feature names are kept simple (lowercase alphanumerics, underscore, dash) for encoding into network message fields.
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
    /** Minimum version (1.1.19) of client/server which send and recognize server features */
    public static final int VERSION_FOR_SERVERFEATURES = 1119;

    /**
     * Minimum version (2.0.00) of client/server which send and recognize client features
     * @since 2.0.00
     */
    public static final int VERSION_FOR_CLIENTFEATURES = 2000;

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

    /**
     * Separator character ';' between features in {@link #featureList}.
     * Chosen to avoid the {@code sep_char} and {@code sep2_char} separators defined in {@code SOCMessage}.
     */
    private static char SEP_CHAR = ';';

    /**
     * Active feature list, or null if none.
     * If not null, the list starts and ends with {@link #SEP_CHAR} for ease of search.
     */
    private String featureList = null;

    /**
     * Create a new empty SOCFeatureSet, with none active or defaults active.
     * After construction, use {@link #add(String)} to add active features.
     * @param withOldDefaults  If false, nothing is active. If true, include the default features
     *     which were assumed always active in servers older than v1.1.19 ({@link #VERSION_FOR_SERVERFEATURES}):
     *     {@link #SERVER_ACCOUNTS}, {@link #SERVER_CHANNELS}, {@link #SERVER_OPEN_REG}.
     */
    public SOCFeatureSet(final boolean withOldDefaults)
    {
        if (withOldDefaults)
        {
            featureList = SEP_CHAR + SERVER_ACCOUNTS + SEP_CHAR + SERVER_CHANNELS
                + SEP_CHAR + SERVER_OPEN_REG + SEP_CHAR;
        } else {
            // featureList is already empty (null)
        }
    }

    /**
     * Create a new SOCFeatureSet from an encoded list; useful at client.
     * @param encodedList  List from {@link #getEncodedList()}, or null or "" for none
     * @throws IllegalArgumentException if {@code encodedList} is not empty but
     *     doesn't start and end with the separator character
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
     * @return  True if {@code featureName} is in the features list
     * @throws IllegalArgumentException if {@code featureName} is null or ""
     */
    public boolean isActive(final String featureName)
        throws IllegalArgumentException
    {
        if ((featureName == null) || (featureName.length() == 0))
            throw new IllegalArgumentException("featureName: " + featureName);

        if (featureList == null)
            return false;

        return featureList.contains(SEP_CHAR + featureName + SEP_CHAR);
    }

    /**
     * Add this active feature.
     * @param featureName  A defined feature name, such as {@link #SERVER_ACCOUNTS}
     * @throws IllegalArgumentException if {@code featureName} is null or ""
     */
    public void add(final String featureName)
        throws IllegalArgumentException
    {
        if ((featureName == null) || (featureName.length() == 0))
            throw new IllegalArgumentException("featureName: " + featureName);

        if (featureList == null)
            featureList = SEP_CHAR + featureName + SEP_CHAR;
        else
            featureList = featureList.concat(featureName + SEP_CHAR);
    }

    /**
     * Get the encoded list of all active features, to send to a
     * client or server for {@link #SOCFeatureSet(String)}.
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
