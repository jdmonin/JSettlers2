/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009-2010,2013,2017,2019-2020 Jeremy D Monin <jeremy@nand.net>
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

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import soc.game.SOCGameOption;
import soc.util.DataUtils;


/**
 * This message from client asks the server about a list of game options,
 * or is a general request about all changes to game options.
 * Used during {@link SOCGameOption} synchronization when client wants localization
 * or is a different version than the server.
 * The server will respond with {@link SOCGameOptionInfo GAMEOPTIONINFO} message(s),
 * one per option keyname listed in this message.
 *<P>
 * If the only "option" keyname sent is '-', server will send info on all
 * options which are new or changed since the client's version. (this usage
 * assumes client is older than server.)
 * This is so clients can find out about options which were
 * introduced in versions newer than the client's version, but which
 * may be applicable to their version or all versions.
 *<P>
 * A client looking for all such changes but also info about specific options
 * can send {@link #OPTKEY_GET_ANY_CHANGES} as part of its {@link #optionKeys} list.
 *<P>
 * In v2.0.00 and newer, clients can also request localized descriptions of all options
 * if available, by including {@link #OPTKEY_GET_I18N_DESCS} as the last option keyname
 * in their list sent to the server.  Check server version against
 * {@link soc.util.SOCStringManager#VERSION_FOR_I18N SOCStringManager.VERSION_FOR_I18N}.
 * Server's response sequence may include a
 * {@link SOCLocalizedStrings}({@link SOCLocalizedStrings#TYPE_GAMEOPT TYPE_GAMEOPT})
 * with any unchanged but localized game options.
 *<P>
 * The keyname list sent by a client asking for localization would be:
 *<UL>
 * <LI> If older than server: "-", {@link #OPTKEY_GET_I18N_DESCS}
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
    implements SOCMessageFromUnauthClient
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
     * @see #OPTKEY_GET_ANY_CHANGES
     * @since 2.0.00
     */
    public static final String OPTKEY_GET_I18N_DESCS = "?I18N";

    /**
     * Request token sent from client to a newer server to get info on any new or changed options
     * when client also needs to ask status of some specific options.
     *<P>
     * v2.4.50 was the first version with that requirement,
     * because that version adds {@link SOCGameOption#FLAG_3RD_PARTY}.
     * Earlier client versions would ask older servers about specific options,
     * or ask newer servers about any new/changed options in general,
     * never needing both of those sets of info at once.
     *
     * @see #OPTKEY_GET_I18N_DESCS
     * @since 2.4.50
     */
    public static final String OPTKEY_GET_ANY_CHANGES = "?CHANGES";

    /**
     * List of specific game option keynames to ask about, or {@code null}. Does not include
     * {@link #OPTKEY_GET_I18N_DESCS}, use {@link #hasTokenGetI18nDescs} instead.
     * At sending client, does include {@link #OPTKEY_GET_ANY_CHANGES} if needed;
     * receiving server uses {@link #hasTokenGetAnyChanges} instead.
     *<P>
     * If {@code null}, then "-" was sent unless {@link #hasOnlyTokenI18n}.
     *<P>
     * Before v2.0.00 this was private field {@code optkeys}, with public getter {@code getOptionKeys()}.
     */
    public final List<String> optionKeys;

    /**
     * True if client is also asking server for localized game option descriptions (v2.0.00 and
     * newer); will send {@link #OPTKEY_GET_I18N_DESCS} along with {@link #optionKeys}.
     * @see #hasOnlyTokenI18n
     * @see #hasTokenGetAnyChanges
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
     * True if client sent {@link #OPTKEY_GET_ANY_CHANGES} as part of {@link #optionKeys}.
     * During parsing at server, this flag was set and that token was removed from {@code optionKeys}.
     *<P>
     * Because it's seldom used, unlike {@link #hasTokenGetI18nDescs}, this flag isn't {@code final}
     * or a constructor parameter.
     *
     * @since 2.4.50
     */
    public boolean hasTokenGetAnyChanges;

    /**
     * Create a GameOptionGetInfos Message, optionally with a list of game option keyname Strings to ask about.
     *
     * @param okeys  list of game option keynames (Strings), or {@code null} for "-".
     *   Do not include {@link #OPTKEY_GET_I18N_DESCS} in this list; set {@code withTokenI18nDescs} true instead.
     *   Do include {@link #OPTKEY_GET_ANY_CHANGES} if needed.
     * @param withTokenI18nDescs  true if client is also asking server for localized game option
     *   descriptions (v2.0.00 and newer); will send {@link #OPTKEY_GET_I18N_DESCS} along with
     *   {@code okeys}. Before sending this token, check the server's version against
     *   {@link soc.util.SOCStringManager#VERSION_FOR_I18N}.
     * @param withOnlyTokenI18n  true if client is same version as server and asking only for
     *   localized game option descriptions; will send {@link #OPTKEY_GET_I18N_DESCS} but not "-".
     *   See {@code withTokenI18nDescs} for version requirements.
     * @throws IllegalArgumentException if {@code withOnlyTokenI18n}, but {@code okeys != null}
     * @see #SOCGameOptionGetInfos(List, boolean)
     */
    public SOCGameOptionGetInfos
        (final List<String> okeys, final boolean withTokenI18nDescs, final boolean withOnlyTokenI18n)
        throws IllegalArgumentException
    {
        if (withOnlyTokenI18n && (okeys != null))
            throw new IllegalArgumentException(okeys.toString());

        messageType = GAMEOPTIONGETINFOS;

        optionKeys = okeys;
        hasTokenGetI18nDescs = withTokenI18nDescs;
        hasOnlyTokenI18n = withOnlyTokenI18n;
    }

    /**
     * Create a GameOptionGetInfos Message, optionally with a list of {@link SOCGameOption}s to ask about.
     *<P>
     * If client and server are same version, consider using the other constructor
     * {@link #SOCGameOptionGetInfos(List, boolean, boolean)} because
     * this one doesn't set the {@link #hasOnlyTokenI18n} flag.
     *
     * @param opts  list of game options, or {@code null} for "-". Will build message using these options' keynames.
     *   Can include {@link #OPTKEY_GET_ANY_CHANGES} if needed.
     * @param withTokenI18nDescs  true if client is also asking server for localized game option
     *   descriptions (v2.0.00 and newer); will send {@link #OPTKEY_GET_I18N_DESCS} along with
     *   {@code opts}' keynames. Before sending this token, check the server's version against
     *   {@link soc.util.SOCStringManager#VERSION_FOR_I18N}.
     * @since 2.4.50
     */
    public SOCGameOptionGetInfos
        (final List<SOCGameOption> opts, final boolean withTokenI18nDescs)
    {
        messageType = GAMEOPTIONGETINFOS;

        if (opts != null)
        {
            List<String> okeys = new ArrayList<>(opts.size());
            for (SOCGameOption opt : opts)
                okeys.add(opt.key);
            optionKeys = okeys;
        } else {
            optionKeys = null;
        }
        hasTokenGetI18nDescs = withTokenI18nDescs;
        hasOnlyTokenI18n = false;
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
     * @see #hasOnlyTokenI18n
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
        StringBuilder cmd = new StringBuilder();
        cmd.append(GAMEOPTIONGETINFOS).append(sep);

        if ((optionKeys == null) || optionKeys.isEmpty())
        {
            if (! hasOnlyTokenI18n)
                cmd.append("-");
        } else {
            boolean hadAny = false;

            for (final String key : optionKeys)
            {
                if (hadAny)
                    cmd.append(sep2);
                else
                    hadAny = true;

                cmd.append(key);
            }
        }

        if (hasTokenGetI18nDescs)
        {
            if (! hasOnlyTokenI18n)
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
        List<String> okey = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(s, sep2);
        boolean hasDash = false, hasTokenI18n = false, hasTokenAllChanges = false;

        try
        {
            while (st.hasMoreTokens())
            {
                String ntok = st.nextToken();

                if (ntok.equals("-"))
                {
                    hasDash = true;
                    continue;
                }
                else if (ntok.equals(OPTKEY_GET_I18N_DESCS))
                {
                    hasTokenI18n = true;
                    continue;  // not an optkey, don't add it to list
                }
                else if (ntok.equals(OPTKEY_GET_ANY_CHANGES))
                {
                    hasTokenAllChanges = true;
                    continue;
                }

                okey.add(ntok);
            }
        }
        catch (Exception e)
        {
            System.err.println("SOCGameOptionGetInfos parseDataStr ERROR - " + e);

            return null;
        }

        if (okey.isEmpty())
            okey = null;
        if (hasDash && (okey != null))
            return null;  // parse error: more than "-" in list which contains "-"

        SOCGameOptionGetInfos ret = new SOCGameOptionGetInfos
            (okey, hasTokenI18n, hasTokenI18n && (okey == null) && ! hasDash);
        if (hasTokenAllChanges)
            ret.hasTokenGetAnyChanges = true;

        return ret;
    }

    /**
     * @return a human readable form of the message
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SOCGameOptionGetInfos:options=");

        if (optionKeys == null)
        {
            if (! hasOnlyTokenI18n)
                sb.append('-');
        } else {
            DataUtils.listIntoStringBuilder(optionKeys, sb);
            if (hasTokenGetAnyChanges && ! optionKeys.contains(OPTKEY_GET_ANY_CHANGES))
                sb.append(',').append(OPTKEY_GET_ANY_CHANGES);
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
