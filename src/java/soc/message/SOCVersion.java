/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008-2009,2012-2014 Jeremy D Monin <jeremy@nand.net>
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


/**
 * This message sends the server's version, or client's version and locale, to the other side of the
 * connection.  VERSION is the first message sent from client to server.
 * The server also sends its version to the client early, not in response to client's VERSION message.
 * Version numbers are read via {@link soc.util.Version}.
 *<P>
 * Before 2.0.00, the client did not send locale; new servers should probably assume <tt>en_US</tt>
 * since older versions had all messages in english.
 *<P>
 * Before 1.1.06, in SOCPlayerClient, was sent first from server to client, then client responds.
 * Robot clients always sent first (since introduction in 1.1.00 of client-server versioning (2008-08-07)).
 *
 * @version 2.0.00
 * @since 1.1.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class SOCVersion extends SOCMessage
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

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
     * Client's JVM locale, or null, as in {@link java.util.Locale#toString()}.
     * Not sent from server or from jsettlers clients older than 2.0.00;
     * if null, should probably assume <tt>en_US</tt>
     * since older versions had all messages in english.
     * @since 2.0.00
     */
    public final String locale;

    /**
     * Create a Version message.
     *
     * @param verNum The version number, as in {@link soc.util.Version#versionNumber()}
     * @param verStr The version display string, as in {@link soc.util.Version#version()}
     * @param verBuild The version build, or null, as in {@link soc.util.Version#buildnum()}
     * @param verLocale The client's JVM locale, or null, as in {@link java.util.Locale#toString()};
     *                  not sent by jsettlers clients older than 2.0.00.
     * @throws IllegalArgumentException if {@code verBuild} is null and {@code verLocale} != null;
     *     not supported by message encoding.
     */
    public SOCVersion(final int verNum, final String verStr, final String verBuild, final String verLocale)
        throws IllegalArgumentException
    {
        if ((verBuild == null) && (verLocale != null))
            throw new IllegalArgumentException("null verBuild, non-null verLocale");

        messageType = VERSION;
        versNum = verNum;
        versStr = verStr;
        versBuild = verBuild;
        locale = verLocale;
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
     * VERSION SEP vernum SEP2 verstr SEP2 build SEP2 locale; build,locale may be blank
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(versNum, versStr, versBuild, locale);
    }

    /**
     * VERSION SEP vernum SEP2 verstr SEP2 build; build may be blank
     *
     * @param verNum  the version number, like 1100 for 1.1.00, as in {@link soc.util.Version#versionNumber()}
     * @param verStr  the version as string, like "1.1.00"
     * @param verBuild the version build, or null, from {@link soc.util.Version#buildnum()}
     * @param verLocale The client's JVM locale, or null, as in {@link java.util.Locale#toString()};
     *                  not sent by jsettlers clients older than 2.0.00.
     * @return    the command string
     * @throws IllegalArgumentException if {@code verBuild} is null and {@code verLocale} != null;
     *     not supported by message encoding.
     */
    public static String toCmd(final int verNum, final String verStr, final String verBuild, final String verLocale)
        throws IllegalArgumentException
    {
        if ((verBuild == null) && (verLocale != null))
            throw new IllegalArgumentException("null verBuild, non-null verLocale");

        return VERSION + sep + verNum + sep2 + verStr
            + sep2 + (verBuild != null ? verBuild : "")
            + sep2 + (verLocale != null ? verLocale : "");
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
        String lo = null;  // locale string, or null 

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
                    lo = st.nextToken();
                    if (lo.length() == 0)
                        lo = null;
                }
            } else {
                bs = null;
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCVersion(vn, vs, bs, lo);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCVersion:" + versNum + ",str=" + versStr + ",verBuild="
            + (versBuild != null ? versBuild : "(null)")
            + ",locale=" + (locale != null ? locale : "(null)");
    }

    /**
     * Minimum version where this message type is used.
     * VERSION introduced in 1.1.00 for client/server versioning.
     * @return Version number, 1100 for JSettlers 1.1.00.
     */
    public int getMinimumVersion() { return 1100; }

}
