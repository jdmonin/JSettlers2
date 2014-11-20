/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2014 Jeremy D Monin <jeremy@nand.net>
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
 * Set of optional server features that are currently active.
 * Sent from server to client during connect via {@link soc.message.SOCVersion} fields.
 *<P>
 * Added in v1.1.19 ({@link #VERSION_FOR_SERVERFEATURES}); earlier clients assume the server is using the
 * features defined in 1.1.19. Use the {@link #SOCServerFeatures(boolean) SOCServerFeatures(true)} constructor
 * when connecting to a server older than 1.1.19. See that constructor's javadoc for the list of features
 * always assumed active before 1.1.19.
 *<P>
 * Feature names are kept simple (lowercase alphanumerics, underscore, dash) for encoding into network message fields.
 *<P>
 * <b>Locks:</b> Not thread-safe.  Caller must guard against potential multi-threaded modifications or access.
 *
 * @since 1.1.19
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class SOCServerFeatures
{
    /** Minimum version (1.1.19) of client/server which send and recognize server features */
    public static final int VERSION_FOR_SERVERFEATURES = 1119;

    /**
     * User accounts defined in a persistent database.
     * If this feature is active, nicknames and passwords are authenticated.
     * Otherwise there are no passwords defined.
     * @see #FEAT_OPEN_REG
     */
    public static final String FEAT_ACCTS = "accts";

    /**
     * Chat channels.
     * If this feature is active, users are allowed to create chat channels.
     * Otherwise no channels are allowed.
     */
    public static final String FEAT_CHANNELS = "ch";

    /**
     * Open registration.
     * If this feature is active, anyone can create their own user accounts.
     * Otherwise only existing users can create new accounts.
     *<P>
     * When a newly installed server requires authentication to create new accounts,
     * but no accounts exist in the database yet, the server tells clients that
     * <tt>FEAT_OPEN_REG</tt> is active so that <tt>SOCAccountClient</tt> won't
     * ask for a username and password.
     * @see #FEAT_ACCTS
     */
    public static final String FEAT_OPEN_REG = "oreg";

    /**
     * Separator character ';' between features in {@link #featureList}.
     * Avoid separators defined in <tt>SOCMessage</tt>.
     */
    private static char SEP_CHAR = ';';

    /**
     * Active feature list, or null if none.
     * If not null, the list starts and ends with {@link #SEP_CHAR} for ease of search.
     */
    private String featureList = null;

    /**
     * Create a new empty SOCServerFeatures, with none active or defaults active.
     * After construction, use {@link #add(String)} to add active features.
     * @param withOldDefaults  If false, nothing is active. If true, include the default features
     *     which were assumed always active in servers older than v1.1.19 ({@link #VERSION_FOR_SERVERFEATURES}):
     *     {@link #FEAT_ACCTS}, {@link #FEAT_CHANNELS}, {@link #FEAT_OPEN_REG}.
     */
    public SOCServerFeatures(final boolean withOldDefaults)
    {
        if (withOldDefaults)
        {
            featureList = SEP_CHAR + FEAT_ACCTS + SEP_CHAR + FEAT_CHANNELS + SEP_CHAR + FEAT_OPEN_REG + SEP_CHAR;
        } else {
            // featureList is already empty (null).
        }
    }

    /**
     * Create a new SOCServerFeatures from an encoded list; useful at client.
     * @param encodedList  List from {@link #getEncodedList()}, or null or "" for none
     * @throws IllegalArgumentException if <tt>encodedList</tt> is not empty but
     *     doesn't start and end with the separator character
     */
    public SOCServerFeatures(String encodedList)
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
     * Create a new SOCServerFeatures by copying another.
     * @param feats  Copy from this to create the new SOCServerFeatures
     * @throws NullPointerException if <tt>feats</tt> == null
     */
    public SOCServerFeatures(SOCServerFeatures feats)
        throws NullPointerException
    {
        super();
        featureList = feats.featureList;
    }

    /**
     * Is this feature active?
     * @param featureName  A defined feature name, such as {@link #FEAT_ACCTS}
     * @return  True if <tt>featureName</tt> is in the features list
     * @throws IllegalArgumentException if <tt>featureName</tt> is null or ""
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
     * @param featureName  A defined feature name, such as {@link #FEAT_ACCTS}
     * @throws IllegalArgumentException if <tt>featureName</tt> is null or ""
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
     * Get the encoded list of all active features, to send to a client for {@link #SOCServerFeatures(String)}.
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
     * <LI> soc.util.SOCServerFeatures@86c347{;accts;ch;}
     * <LI> soc.util.SOCServerFeatures@f7e6a96{(empty)}
     *</UL>
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append('{');
        sb.append((featureList != null) ? featureList : "(empty)");
        sb.append('}');
        return sb.toString();
    }

}
