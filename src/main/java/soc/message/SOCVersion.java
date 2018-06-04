/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008-2009,2012-2015,2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.util.SOCFeatureSet;  // for javadocs only


/**
 * This message sends the server's version and features, or client's version and locale,
 * to the other side of the connection.  VERSION is the first message sent from client to server.
 * The server also sends its version to the client early, not in response to client's VERSION message.
 * Version numbers are read via {@link soc.util.Version}.
 *<P>
 * Before 2.0.00, the client did not send its locale or optional features. If locale not sent by client,
 * server should probably assume {@code en_US} since older versions had all messages in english.
 * For robots the locale field is ignored at server, because bots don't parse server text messages;
 * sending a {@link SOCImARobot} message will clear the robot client's locale to {@code null} at the server.
 *<P>
 * Before 1.1.19, the server did not send its active optional features; new clients of older servers
 * should use the {@link SOCFeatureSet#SOCFeatureSet(boolean) SOCFeatureSet(true)} constructor
 * to set the default features active.
 *<P>
 * Before 1.1.06, in SOCPlayerClient, this was sent first from server to client, then client responded.
 * Robot clients have always sent first since the introduction in 1.1.00 of client-server versioning (2008-08-07).
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
     * List of active optional features, or null, as in {@link SOCFeatureSet#getEncodedList()}.
     * Features aren't sent from servers older than 1.1.19 or clients older than 2.0.00.
     * See class javadoc for handling older servers or clients when this field is null.
     * @since 1.1.19
     */
    public final String feats;

    /**
     * Client's JVM locale, or null, as in {@link java.util.Locale#toString()};
     * Not sent from server or from clients older than 2.0.00.
     * See class javadoc for handling older clients when this field is null.
     * @since 2.0.00
     */
    public final String cliLocale;

    /**
     * Create a Version message.
     *
     * @param verNum The version number, as in {@link soc.util.Version#versionNumber()}
     * @param verStr The version display string, as in {@link soc.util.Version#version()}
     * @param verBuild The version build, or null, as in {@link soc.util.Version#buildnum()}
     * @param feats  The server's active optional features, or null, as in
     *     {@link SOCServerFeatures#getEncodedList()};
     *     not sent by servers older than 1.1.19 or clients older than 2.0.00.
     *     Server can send this to a client older than 1.1.19, it is safely ignored.
     * @param cliLocale The client's JVM locale, or null, as in {@link java.util.Locale#toString()};
     *     not sent by servers or by clients older than 2.0.00.
     * @throws IllegalArgumentException if {@code verBuild} is null and {@code feats} != null;
     *     not supported by message encoding to clients older than 2.0.00.
     */
    public SOCVersion
        (final int verNum, final String verStr, final String verBuild, final String feats, final String cliLocale)
        throws IllegalArgumentException
    {
        if ((verBuild == null) && (feats != null))
            throw new IllegalArgumentException("null verBuild, non-null feats");

        messageType = VERSION;
        versNum = verNum;
        versStr = verStr;
        versBuild = verBuild;
        this.feats = feats;
        this.cliLocale = cliLocale;
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
     * VERSION SEP vernum SEP2 verstr [SEP2 build [SEP2 feats [SEP2 cliLocale]]].
     * Build, feats, and cliLocale are optional and may be blank ({@link SOCMessage#EMPTYSTR}).
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(versNum, versStr, versBuild, feats, cliLocale);
    }

    /**
     * VERSION SEP vernum SEP2 verstr [SEP2 build [SEP2 feats [SEP2 cliLocale]]].
     * Build, feats, and cliLocale are optional and may be blank ({@link SOCMessage#EMPTYSTR}).
     *<P>
     * Empty strings should not be given as parameters; use {@code null} instead for optional fields.
     *
     * @param verNum  the version number, like 1100 for 1.1.00, as in {@link soc.util.Version#versionNumber()}; not null
     * @param verStr  the version as string, like "1.1.00"; not null
     * @param verBuild the version build, or null, from {@link soc.util.Version#buildnum()}
     * @param feats  The server or client's active optional features, or null, as in
     *     {@link SOCServerFeatures#getEncodedList()}.
     *     Not sent by servers older than 1.1.19 or clients older than 2.0.00.
     *     Server can send this to a client older than 1.1.19, it is safely ignored.
     * @param cliLocale The client's JVM locale, or null, as in {@link java.util.Locale#toString()}.
     *     Not sent by servers or by clients older than 2.0.00.
     * @return    the command string
     * @throws IllegalArgumentException if {@code verBuild} is null and {@code feats} != null;
     *     not supported by message encoding to clients older than 2.0.00.
     */
    public static String toCmd
        (final int verNum, final String verStr, final String verBuild, final String feats, final String cliLocale)
        throws IllegalArgumentException
    {
        if ((verBuild == null) && (feats != null))
            throw new IllegalArgumentException("null verBuild, non-null feats");
        // don't need to check for null build && non-null cliLocale: that's 2.0.00+ only

        return VERSION + sep + verNum + sep2 + verStr
            + sep2 + (verBuild != null ? verBuild : EMPTYSTR)
            + sep2 + (feats != null ? feats : EMPTYSTR)
            + (cliLocale != null ? (sep2 + cliLocale) : "");
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
        String fs = null;  // feats string, or null
        String clo = null;  // cliLocale string, or null

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            vn = Integer.parseInt(st.nextToken());
            vs = st.nextToken();
            if (st.hasMoreTokens())
            {
                bs = st.nextToken();
                if ((bs.length() == 0) || EMPTYSTR.equals(bs))
                    bs = null;

                if (st.hasMoreTokens())
                {
                    fs = st.nextToken();
                    if ((fs.length() == 0) || EMPTYSTR.equals(fs))
                        fs = null;

                    if (st.hasMoreTokens())
                    {
                        clo = st.nextToken();
                        if ((clo.length() == 0) || EMPTYSTR.equals(clo))
                            clo = null;
                    }
                }
            } else {
                bs = null;
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCVersion(vn, vs, bs, fs, clo);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCVersion:" + versNum + ",str=" + versStr + ",verBuild="
            + (versBuild != null ? versBuild : "(null)")
            + ",feats=" + (feats != null ? feats : "(null)")
            + ",cliLocale=" + (cliLocale != null ? cliLocale : "(null)");
    }

    /**
     * Minimum version where this message type is used.
     * VERSION introduced in 1.1.00 for client/server versioning.
     * @return Version number, 1100 for JSettlers 1.1.00.
     */
    public int getMinimumVersion() { return 1100; }

}
