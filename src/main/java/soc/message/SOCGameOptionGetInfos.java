/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009-2010,2013,2017,2019 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;

import soc.game.SOCGameOption;
import soc.util.DataUtils;


/**
 * This message from client sends a list of game options to the server.
 * The server will respond with {@link SOCGameOptionInfo GAMEOPTIONINFO} message(s),
 * one per option keyname listed in this message.
 *<P>
 * If the only 'option' keyname sent is '-', server will send info on all
 * options which are new or changed since the client's version. (this usage
 * assumes client is older than server).
 *<P>
 * This is so clients can find out about options which were
 * introduced in versions newer than the client's version, but which
 * may be applicable to their version or all versions.
 *<P>
 * In v2.0.00 and newer, clients can also request localized descriptions of all options
 * if available, by including {@link #OPTKEY_GET_I18N_DESCS} as the last option keyname
 * in their list sent to the server.  Check server version against
 * {@link soc.util.SOCStringManager#VERSION_FOR_I18N SOCStringManager.VERSION_FOR_I18N}.
 * Server's response sequence may include a
 * {@link SOCLocalizedStrings}({@link SOCLocalizedStrings#TYPE_GAMEOPT TYPE_GAMEOPT})
 * with any unchanged but localized game options.
 *<P>
 * The keyname list sent by the client would be:
 *<UL>
 * <LI> If older than server, or same version: "-", {@link #OPTKEY_GET_I18N_DESCS}
 * <LI> If same version: only {@link #OPTKEY_GET_I18N_DESCS}
 * <LI> If newer than server: Each newer option name, then {@link #OPTKEY_GET_I18N_DESCS}
 *</UL>
 * This message type introduced in v1.1.07; check server version against
 * {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}
 * before sending this message.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't create games.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.07
 */
public class SOCGameOptionGetInfos extends SOCMessage
{
    private static final long serialVersionUID = 2000L;

    /**
     * I18N option-description request token {@code "?I18N"} sent from client when its locale isn't {@code en_US}.
     *<P>
     * If the list of game option keys from the client includes this item, the server should
     * check the client's locale and send localized descriptions for all game options available
     * at this client's version.
     *<P>
     * When present, this will be at the end of the list of option keys sent over the network,
     * but isn't part of the {@link #optionKeys} list. The receiving parser
     * will remove it from the list and set {@link #hasTokenGetI18nDescs} and/or
     * {@link #hasOnlyTokenI18n}.
     *<P>
     * If the server does not have game option names in the client's locale, this token
     * is ignored and only the changed options will be sent by version as described above.
     *<P>
     * Introduced in v2.0.00: Before sending, check the server's version against
     * {@link soc.util.SOCStringManager#VERSION_FOR_I18N SOCStringManager.VERSION_FOR_I18N}.
     *
     * @see #hasTokenGetI18nDescs
     * @since 2.0.00
     */
    public static final String OPTKEY_GET_I18N_DESCS = "?I18N";

    /**
     * List of game option keynames, or {@code null}.  Does not include
     * {@link #OPTKEY_GET_I18N_DESCS}, use {@link #hasTokenGetI18nDescs} instead.
     *<P>
     * If {@code null}, then "-" was sent unless {@link #hasOnlyTokenI18n}.
     *<P>
     * Before v2.0.00 this was private field {@code optkeys}, with public getter {@code getOptionKeys()}.
     */
    public final Vector<String> optionKeys;

    /**
     * True if client is also asking server for localized game option descriptions (v2.0.00 and
     * newer); will send {@link #OPTKEY_GET_I18N_DESCS} along with {@link #optionKeys}.
     * @see #hasOnlyTokenI18n
     * @since 2.0.00
     */
    public final boolean hasTokenGetI18nDescs;

    /**
     * True if client is same version as server, and is asking only for {@link #OPTKEY_GET_I18N_DESCS};
     * will send {@link #OPTKEY_GET_I18N_DESCS} as the sole "option key": {@code "?I18N"}, not {@code "-,?I18N"}.
     * {@link #optionKeys} must be {@code null}.
     * @see #hasTokenGetI18nDescs
     * @since 2.0.00
     */
    public final boolean hasOnlyTokenI18n;

    /**
     * Create a GameOptionGetInfos Message.
     *
     * @param okeys  list of game option keynames (Strings), or {@code null} for "-".
     *   Do not include {@link #OPTKEY_GET_I18N_DESCS} in this list; set {@code withTokenI18nDescs} true instead.
     * @param withTokenI18nDescs  true if client is also asking server for localized game option
     *   descriptions (v2.0.00 and newer); will send {@link #OPTKEY_GET_I18N_DESCS} along with
     *   {@code okeys}. Before sending this token, check the server's version against
     *   {@link soc.util.SOCStringManager#VERSION_FOR_I18N SOCStringManager.VERSION_FOR_I18N}.
     * @param withOnlyTokenI18n  true if client is same version as server and asking only for
     *   localized game option descriptions; will send {@link #OPTKEY_GET_I18N_DESCS} but not "-".
     *   See {@code withTokenI18nDescs} for version requirements.
     * @throws IllegalArgumentException if {@code withOnlyTokenI18n}, but {@code okeys != null}
     */
    public SOCGameOptionGetInfos
        (final Vector<String> okeys, final boolean withTokenI18nDescs, final boolean withOnlyTokenI18n)
        throws IllegalArgumentException
    {
        if (withOnlyTokenI18n && (okeys != null))
            throw new IllegalArgumentException();

        messageType = GAMEOPTIONGETINFOS;
        optionKeys = okeys;
        hasTokenGetI18nDescs = withTokenI18nDescs;
        hasOnlyTokenI18n = withOnlyTokenI18n;
    }

    /**
     * Minimum version where this message type is used.
     * GAMEOPTIONGETINFOS introduced in 1.1.07 for game-options feature.
     * @return Version number, 1107 for JSettlers 1.1.07.
     */
    @Override
    public int getMinimumVersion() { return 1107; }

    /**
     * @return True if client is also asking server for localized game option descriptions (v2.0.00 and
     *     newer); message includes {@link #OPTKEY_GET_I18N_DESCS} along with {@link #optionKeys}.
     * @since 2.0.00
     */
    public boolean hasTokenGetI18nDescs()
    {
        return hasTokenGetI18nDescs;
    }

    /**
     * GAMEOPTIONGETINFOS sep optionKeys
     *
     * @return the command string
     */
    @Override
    public String toCmd()
    {
        return toCmd(optionKeys, hasTokenGetI18nDescs, hasOnlyTokenI18n);
    }

    /**
     * GAMEOPTIONGETINFOS sep optionKeys
     *
     * @param opts  the list of option keynames, as a list of Strings or SOCGameOptions,
     *            or null to use "-" as 'optionKeys'
     * @param withTokenI18nDescs  true if client is also asking server for localized game option descriptions
     *            (v2.0.00 and newer); will send {@link #OPTKEY_GET_I18N_DESCS} along with {@code opts}.
     *            Before sending this token, check the server's version against
     *            {@link soc.util.SOCStringManager#VERSION_FOR_I18N SOCStringManager.VERSION_FOR_I18N}.
     * @param withOnlyTokenI18n  true if client is same version as server and asking only for
     *            localized game option descriptions; will send {@link #OPTKEY_GET_I18N_DESCS} but not "-".
     *            See {@code withTokenI18nDescs} for version requirements.
     * @return  the command string
     */
    public static String toCmd
        (final List<?> opts, final boolean withTokenI18nDescs, final boolean withOnlyTokenI18n)
    {
        StringBuffer cmd = new StringBuffer(Integer.toString(GAMEOPTIONGETINFOS));
        cmd.append(sep);

        if ((opts == null) || opts.isEmpty())
        {
            if (! withOnlyTokenI18n)
                cmd.append("-");
        } else {
            try
            {
                boolean hadAny = false;

                for (final Object o : opts)
                {
                    if (hadAny)
                        cmd.append(sep2);
                    else
                        hadAny = true;

                    if (o instanceof SOCGameOption)
                        cmd.append(((SOCGameOption) o).key);
                    else
                        cmd.append((String) o);
                }
            }
            catch (Exception e) { }
        }

        if (withTokenI18nDescs)
        {
            if (! withOnlyTokenI18n)
                cmd.append(sep2);
            cmd.append(OPTKEY_GET_I18N_DESCS);
        }

        return cmd.toString();
    }

    /**
     * Parse the command String into a GameOptionGetInfos message
     *
     * @param s   the String to parse
     * @return    a GetInfos message, or null if the data is garbled
     */
    public static SOCGameOptionGetInfos parseDataStr(String s)
    {
        Vector<String> okey = new Vector<String>();
        StringTokenizer st = new StringTokenizer(s, sep2);
        boolean hasDash = false, hasTokenI18n = false;

        try
        {
            while (st.hasMoreTokens())
            {
                String ntok = st.nextToken();

                if (ntok.equals(OPTKEY_GET_I18N_DESCS))
                {
                    hasTokenI18n = true;
                    continue;  // not an optkey, don't add it to list
                }

                okey.addElement(ntok);
                if (ntok.equals("-"))
                    hasDash = true;  // should be sole element of list
            }
        }
        catch (Exception e)
        {
            System.err.println("SOCGameOptionGetInfos parseDataStr ERROR - " + e);

            return null;
        }

        if (hasDash)
        {
            if (okey.size() == 1)
                okey = null;  // empty list for "-"
            else
                return null;  // parse error: more than "-" in list which contains "-"
        } else {
            if (okey.isEmpty())
                okey = null;
        }

        return new SOCGameOptionGetInfos(okey, hasTokenI18n, hasTokenI18n && (okey == null));
    }

    /**
     * @return a human readable form of the message
     */
    @Override
    public String toString()
    {
        StringBuffer sb = new StringBuffer("SOCGameOptionGetInfos:options=");

        if (optionKeys == null)
        {
            if (! hasOnlyTokenI18n)
                sb.append('-');
        } else {
            DataUtils.enumIntoStringBuf(optionKeys.elements(), sb);
        }

        if (hasTokenGetI18nDescs)
        {
            if (! hasOnlyTokenI18n)
                sb.append(',');
            sb.append(OPTKEY_GET_I18N_DESCS);
        }

        return sb.toString();
    }

}
