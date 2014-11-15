/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008-2009,2012,2014 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;

import java.util.StringTokenizer;

import soc.util.SOCServerFeatures;  // for javadocs only


/**
 * This message sends the server's version and features, or client's version, to the
 * other side of the connection.  VERSION is the first message sent from client to server.
 * The server also sends its version to the client early, not in response to client's VERSION message.
 * Version numbers are read via {@link soc.util.Version}.
 *<P>
 * Before 1.1.19, the server did not send its active optional features; new clients of older servers
 * should use the {@link SOCServerFeatures#SOCServerFeatures(boolean) SOCServerFeatures(true)} constructor
 * to set the default features active.
 *<P>
 * Before 1.1.06, in SOCPlayerClient, this was sent first from server to client, then client responded.
 * Robot clients have always sent first since the introduction in 1.1.00 of client-server versioning (2008-08-07).
 *
 * @version 1.1.19
 * @since 1.1.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class SOCVersion extends SOCMessage
{
    private static final long serialVersionUID = 1119L;  // last structural change v1.1.19

    /**
     * Version display string, as in {@link soc.util.Version#version()}
     */
    private String versStr;

    /**
     * Version number, as in {@link soc.util.Version#versionNumber()}
     */
    private int versNum;

    /**
     * Version build, or null, as in {@link soc.util.Version#buildnum()}
     */
    private String versBuild;

    /**
     * Server's active optional features, or null, as in {@link SOCServerFeatures#getEncodedList()}.
     * Features not sent from servers older than 1.1.19.
     * See class javadoc for handling older servers when this field is null.
     * Null when <tt>SOCVersion</tt> is sent from clients.
     * @since 1.1.19
     */
    public final String feats;

    /**
     * Create a Version message.
     *
     * @param verNum The version number, as in {@link soc.util.Version#versionNumber()}
     * @param verStr The version display string, as in {@link soc.util.Version#version()}
     * @param verBuild The version build, or null, as in {@link soc.util.Version#buildnum()}
     * @param verFeats  The server's active optional features, or null, as in
     *                  {@link SOCServerFeatures#getEncodedList()}; not sent by servers older than 1.1.19.
     *                  Server can send this to a client older than 1.1.19, it is safely ignored.
     *                  Null when <tt>SOCVersion</tt> is sent from clients.
     * @throws IllegalArgumentException if <tt>verBuild</tt> is null and <tt>verFeats</tt> != null;
     *     not supported by message encoding.
     */
    public SOCVersion(final int verNum, final String verStr, final String verBuild, final String verFeats)
        throws IllegalArgumentException
    {
        if ((verBuild == null) && (verFeats != null))
            throw new IllegalArgumentException("null verBuild, non-null verFeats");

        messageType = VERSION;
        versNum = verNum;
        versStr = verStr;
        versBuild = verBuild;
        feats = verFeats;
    }

    /**
     * @return the version number, as in {@link soc.util.Version#versionNumber()}
     */
    public int getVersionNumber()
    {
        return versNum;
    }

    /**
     * @return the version display string, as in {@link soc.util.Version#version()}
     */
    public String getVersionString()
    {
        return versStr;
    }

    /**
     * @return the build, as in {@link soc.util.Version#buildnum()}, or null
     */
    public String getBuild()
    {
        return versBuild;
    }

    /**
     * VERSION SEP vernum SEP2 verstr SEP2 build SEP2 feats; build,feats may be blank
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(versNum, versStr, versBuild, feats);
    }

    /**
     * VERSION SEP vernum SEP2 verstr SEP2 build SEP2 feats; build,feats may be blank
     *
     * @param verNum  the version number, like 1100 for 1.1.00, as in {@link soc.util.Version#versionNumber()}
     * @param verStr  the version as string, like "1.1.00"
     * @param verBuild the version build, or null, from {@link soc.util.Version#buildnum()}
     * @param verFeats  the server's active optional features, or null, as in
     *                  {@link SOCServerFeatures#getEncodedList()}; not sent by servers older than 1.1.19.
     *                  Server can send this to a client older than 1.1.19, it is safely ignored.
     *                  Null when <tt>SOCVersion</tt> is sent from clients.
     * @return    the command string
     * @throws IllegalArgumentException if <tt>verBuild</tt> is null and <tt>verFeats</tt> != null;
     *     not supported by message encoding.
     */
    public static String toCmd(int verNum, String verStr, String verBuild, final String verFeats)
        throws IllegalArgumentException
    {
        if ((verBuild == null) && (verFeats != null))
            throw new IllegalArgumentException("null verBuild, non-null verFeats");

        return VERSION + sep + verNum + sep2 + verStr
            + sep2 + (verBuild != null ? verBuild : "")
            + sep2 + (verFeats != null ? verFeats : "");
    }

    /**
     * Parse the command String into a Version message
     *
     * @param s   the String to parse
     * @return    a Version message
     */
    public static SOCVersion parseDataStr(String s)
    {
        int vn;     // version number
        String vs;  // version string
        String bs;  // build string, or null
        String fe = null;  // server features string, or null

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            vn = Integer.parseInt(st.nextToken());
            vs = st.nextToken();
            if (st.hasMoreTokens())
            {
                bs = st.nextToken();
                if (st.hasMoreTokens())
                {
                    fe = st.nextToken();
                    if (fe.length() == 0)
                        fe = null;
                }
            } else {
                bs = null;
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCVersion(vn, vs, bs, fe);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCVersion:" + versNum + ",str=" + versStr + ",verBuild="
            + (versBuild != null ? versBuild : "(null)")
            + ",feats=" + (feats != null ? feats : "(null)");
    }

    /**
     * Minimum version where this message type is used.
     * VERSION introduced in 1.1.00 for client/server versioning.
     * @return Version number, 1100 for JSettlers 1.1.00.
     */
    public int getMinimumVersion()
    {
        return 1100;
    }

}
