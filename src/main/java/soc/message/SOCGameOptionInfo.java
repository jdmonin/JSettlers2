/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2009,2012-2013,2015,2017-2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;  // for javadocs only
import soc.game.SOCScenario;   // for javadocs only
import soc.game.SOCVersionedItem;

/**
 * Information on one available {@link SOCGameOption} game option.
 * Is reply from server to a client's {@link SOCGameOptionGetInfos GAMEOPTIONGETINFOS} message:
 * Provides the option's information, including default value and current value at the
 * server for new games.  In v2.0.00+ the option description text sent may be localized for the client if available;
 * see also {@link SOCLocalizedStrings}({@link SOCLocalizedStrings#TYPE_GAMEOPT TYPE_GAMEOPT}).
 *<P>
 * This message is for clients to find out about options which were
 * introduced in versions newer than the client's version, but which
 * may be applicable to their version or all versions.
 * Client should have a {@link SOCGameOptionSet} to hold its "Known Options",
 * initialized from {@link SOCGameOptionSet#getAllKnownOptions()} and updated by this message.
 *<P>
 * If the server doesn't know this option, the returned option type is
 * {@link SOCGameOption#OTYPE_UNKNOWN}.
 * If the client asks about an option too new for it to use,
 * by sending the option name or GAMEOPTIONGETINFOS("-"),
 * the server will respond with {@link SOCGameOption#OTYPE_UNKNOWN}.
 *<P>
 * Special case: If the client is asking for any new options by sending
 * GAMEOPTIONGETINFOS("-"), but there aren't any new options, server responds with
 * {@link #OPTINFO_NO_MORE_OPTS}, a GAMEOPTIONINFO named "-" with type {@code OTYPE_UNKNOWN}.
 *<P>
 * Introduced in 1.1.07; check client version against {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}
 * before sending this message.
 *<P>
 * In v2.0.00 and newer, {@link SOCScenario} info is also synchronized with similar logic;
 * see {@link SOCScenarioInfo}.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't create or browse games.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.07
 */
public class SOCGameOptionInfo extends SOCMessageTemplateMs
{
    private static final long serialVersionUID = 2000L;

    /**
     * If the client is asking for any new options by sending GAMEOPTIONGETINFOS("-"),
     * server responds with set of GAMEOPTIONINFOs. End of this list is marked with a
     * GAMEOPTIONINFO named "-" with type {@link SOCGameOption#OTYPE_UNKNOWN}.
     */
    public static final SOCGameOptionInfo OPTINFO_NO_MORE_OPTS
        = new SOCGameOptionInfo(new SOCGameOption("-"), 0, null);

    protected SOCGameOption opt = null;

    /**
     * Constructor for server to tell client about a game option.
     * The client's version is checked to make sure the message format can be understood
     * at the client, by omitting fields and flags added after the client's version.
     * @param op  Option to send
     * @param cliVers  Client's version number; 1107 is version 1.1.07
     * @param localDesc  i18n localized option description, or {@code null} to use
     *            {@link soc.game.SOCVersionedItem#getDesc() SOCGameOption.getDesc()}
     */
    public SOCGameOptionInfo(final SOCGameOption op, final int cliVers, final String localDesc)
    {
        super(GAMEOPTIONINFO, new ArrayList<String>());

        // OTYPE_*
        opt = op;
        /* [0] */ pa.add(op.key);
        /* [1] */ pa.add(Integer.toString(op.optType));
        /* [2] */ pa.add(Integer.toString(op.minVersion));
        /* [3] */ pa.add(Integer.toString(op.lastModVersion));
        /* [4] */ pa.add(op.defaultBoolValue ? "t" : "f");
        /* [5] */ pa.add(Integer.toString(op.defaultIntValue));
        /* [6] */ pa.add(Integer.toString(op.minIntValue));
        /* [7] */ pa.add(Integer.toString(op.maxIntValue));
        /* [8] */ pa.add(op.getBoolValue() ? "t" : "f");
        if ((op.optType == SOCGameOption.OTYPE_STR) || (op.optType == SOCGameOption.OTYPE_STRHIDE))
        {
            /* [9] */ pa.add(op.getStringValue());
        } else {
            /* [9] */ pa.add(Integer.toString(op.getIntValue()));
        }
        if (cliVers < 2000)
            /* [10] */ pa.add(op.hasFlag(SOCGameOption.FLAG_DROP_IF_UNUSED) ? "t" : "f");
        else
            /* [10] */ pa.add(Integer.toString(op.optFlags));

        /* [11] */ pa.add((localDesc != null) ? localDesc : op.getDesc());

        // for OTYPE_ENUM, _ENUMBOOL, pa[12+] are the enum choices' string values
        if ((op.optType == SOCGameOption.OTYPE_ENUM) || (op.optType == SOCGameOption.OTYPE_ENUMBOOL))
            for (final String ev : op.enumVals)
                pa.add(ev);
    }

    /**
     * Constructor for client to parse server's reply about a game option.
     * If opt type number is unknown locally, will change to {@link SOCGameOption#OTYPE_UNKNOWN}.
     *
     * @param pal  The option's parameters:
     *<UL>
     * <LI> pal[0] = key (technical name of the option, from {@link SOCVersionedItem#key})
     * <LI> pal[1] = type
     * <LI> pal[2] = minVersion
     * <LI> pal[3] = lastModVersion
     * <LI> pal[4] = defaultBoolValue ('t' or 'f')
     * <LI> pal[5] = defaultIntValue
     * <LI> pal[6] = minIntValue
     * <LI> pal[7] = maxIntValue
     * <LI> pal[8] = boolValue ('t' or 'f'; current, not default)
     * <LI> pal[9] = intValue (current, not default) or stringvalue; stringvalue of "" is stored as {@code null}
     * <LI> pal[10] = optFlags as integer -- before v2.0.00, only FLAG_DROP_IF_UNUSED ('t' or 'f')
     * <LI> pal[11] = desc (displayed text) if present; required for all types except {@code OTYPE_UNKNOWN}
     * <LI> pal[12] and beyond, if present = each enum choice's text
     *</UL>
     * Any parameter which is {@link SOCMessage#EMPTYSTR} is changed to "" in place in {@code pal}.
     *
     * @throws IllegalArgumentException if pal's length &lt; 11, or type is not a valid {@link SOCGameOption#optType};
     *      if type isn't {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM} or ENUMBOOL,
     *      pal's length must == 12 (or 11 for OTYPE_UNKNOWN).
     * @throws NumberFormatException    if pal integer-field contents are incorrectly formatted.
     */
    protected SOCGameOptionInfo(List<String> pal)
        throws IllegalArgumentException, NumberFormatException
    {
        super(GAMEOPTIONINFO, pal);
        final int L = pal.size();
        if (L < 11)
            throw new IllegalArgumentException("pal.size");

        parseData_FindEmptyStrs(pal);  // EMPTYSTR -> ""
        final String[] params = pal.toArray(new String[L]);

        // OTYPE_*
        int otyp = Integer.parseInt(params[1]);
        if ((otyp < SOCGameOption.OTYPE_MIN) || (otyp > SOCGameOption.OTYPE_MAX))
            otyp = SOCGameOption.OTYPE_UNKNOWN;

        final int oversmin = Integer.parseInt(params[2]);
        final int oversmod = Integer.parseInt(params[3]);
        final boolean bval_def = (params[4].equals("t"));
        final int ival_def = Integer.parseInt(params[5]);
        final int ival_min = Integer.parseInt(params[6]);
        final int ival_max = Integer.parseInt(params[7]);
        final boolean bval_cur = (params[8].equals("t"));
        final int ival_cur;
        String sval_cur;
        if ((otyp == SOCGameOption.OTYPE_STR) || (otyp == SOCGameOption.OTYPE_STRHIDE))
        {
            ival_cur = 0;
            sval_cur = params[9];
            if (sval_cur.length() == 0)
                sval_cur = null;
        } else {
            ival_cur = Integer.parseInt(params[9]);
            sval_cur = null;
        }
        final int opt_flags;
        if (params[10].equals("t"))
            opt_flags = SOCGameOption.FLAG_DROP_IF_UNUSED;
        else if (params[10].equals("f") || (params[10].length() == 0))
            opt_flags = 0;
        else
            opt_flags = Integer.parseInt(params[10]);

        if ((params.length != 11) && (params.length != 12)
              && (otyp != SOCGameOption.OTYPE_ENUM)
              && (otyp != SOCGameOption.OTYPE_ENUMBOOL))
            throw new IllegalArgumentException("params.length");

        switch (otyp)  // OTYPE_*
        {
        case SOCGameOption.OTYPE_UNKNOWN:
            opt = new SOCGameOption(params[0]);
            break;

        case SOCGameOption.OTYPE_BOOL:
            opt = new SOCGameOption
                (params[0], oversmin, oversmod, bval_def, opt_flags, params[11]);
            opt.setBoolValue(bval_cur);
            break;

        case SOCGameOption.OTYPE_INT:
            opt = new SOCGameOption
                (params[0], oversmin, oversmod, ival_def, ival_min, ival_max, opt_flags, params[11]);
            opt.setIntValue(ival_cur);
            break;

        case SOCGameOption.OTYPE_INTBOOL:
            opt = new SOCGameOption
                (params[0], oversmin, oversmod, bval_def, ival_def, ival_min, ival_max, opt_flags, params[11]);
            opt.setBoolValue(bval_cur);
            opt.setIntValue(ival_cur);
            break;

        case SOCGameOption.OTYPE_ENUM:
            {
                String[] choices = new String[ival_max];
                System.arraycopy(params, 12, choices, 0, ival_max);
                opt = new SOCGameOption
                    (params[0], oversmin, oversmod, ival_def, choices, opt_flags, params[11]);
                opt.setIntValue(ival_cur);
            }
            break;

        case SOCGameOption.OTYPE_ENUMBOOL:
            {
                String[] choices = new String[ival_max];
                System.arraycopy(params, 12, choices, 0, ival_max);
                opt = new SOCGameOption
                    (params[0], oversmin, oversmod, bval_def, ival_def, choices, opt_flags, params[11]);
                opt.setBoolValue(bval_cur);
                opt.setIntValue(ival_cur);
            }
            break;

        case SOCGameOption.OTYPE_STR:
        case SOCGameOption.OTYPE_STRHIDE:
            opt = new SOCGameOption
                (params[0], oversmin, oversmod, ival_max, (otyp == SOCGameOption.OTYPE_STRHIDE), opt_flags, params[11]);
            opt.setStringValue(sval_cur);
            break;

        default:
            throw new IllegalArgumentException("optType");

        }  // switch (otyp)
    }

    /**
     * Minimum version where this message type is used.
     * GAMEOPTIONINFO introduced in 1.1.07 for game-options feature.
     * @return Version number, 1107 for JSettlers 1.1.07.
     */
    public int getMinimumVersion() { return 1107; }

    /**
     * @return the name (key) of the option, or "-" for the end-of-list marker.
     */
    public String getOptionNameKey()
    {
        return pa.get(0);
    }

    /**
     * @return the parsed option values, or null if this
     *    message is coming from a client asking about a game
     */
    public SOCGameOption getOptionInfo()
    {
        return opt;
    }

    /**
     * Parse the command String array into a SOCGameOptionInfo message.
     *<UL>
     * <LI> pa[0] = key (option's technical name, from {@link SOCVersionedItem#key})
     * <LI> pa[1] = type
     * <LI> pa[2] = minVersion
     * <LI> pa[3] = lastModVersion
     * <LI> pa[4] = defaultBoolValue ('t' or 'f')
     * <LI> pa[5] = defaultIntValue
     * <LI> pa[6] = minIntValue
     * <LI> pa[7] = maxIntValue
     * <LI> pa[8] = boolValue ('t' or 'f'; current, not default)
     * <LI> pa[9] = intValue (current, not default) or stringvalue
     * <LI> pa[10] = dropIfUnused ('t' or 'f')
     * <LI> pa[11] = desc (displayed text) if present; required for all types except {@code OTYPE_UNKNOWN}
     * <LI> pa[12] and beyond, if present = each enum choice's text
     *</UL>
     *
     * @param pa   the String parameters
     * @return    a GameOptionInfo message, or null if parsing errors
     */
    public static SOCGameOptionInfo parseDataStr(List<String> pa)
    {
        if ((pa == null) || (pa.size() < 11))
            return null;

        try
        {
            return new SOCGameOptionInfo(pa);  // calls parseData_FindEmptyStrs
        } catch (Throwable e) {
            return null;
        }
    }

}
