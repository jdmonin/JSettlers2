/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGameOption;

/**
 * Information on one available {@link SOCGameOption game option}.
 * Reply from server to a client's {@link SOCGameOptionGetInfos GAMEOPTIONGETINFOS} message.
 * Provides the option's information, including
 * default value, and current value at the server for new games.
 * If the server doesn't know this option, the returned option type is
 * {@link SOCGameOption#OTYPE_UNKNOWN}.
 * If the client asks about an option too new for it to use,
 * the server will respond with {@link SOCGameOption#OTYPE_UNKNOWN}.
 *<P>
 * Special case: If the client is asking for any new options, by sending
 * GAMEOPTIONGETINFOS("-"), but there aren't any new options, server responds with
 * {@link #OPTINFO_NO_MORE_OPTS}, a GAMEOPTIONINFO named "-" with type OTYPE_UNKNOWN.
 *<P>
 * This is so clients can find out about options which were
 * introduced in versions newer than the client's version, but which
 * may be applicable to their version or all versions.
 *<P>
 * Introduced in 1.1.07; check client version against {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}
 * before sending this message.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't create or browse games.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.07
 */
public class SOCGameOptionInfo extends SOCMessageTemplateMs
{
    /**
     * If the client is asking for any new options, by sending GAMEOPTIONGETINFOS("-"),
     * server responds with set of GAMEOPTIONINFOs. Mark end of this list with a
     * GAMEOPTIONINFO named "-" with type OTYPE_UNKNOWN.
     */
    public static final SOCGameOptionInfo OPTINFO_NO_MORE_OPTS
        = new SOCGameOptionInfo(new SOCGameOption("-"));

    /**
     * symbol to represent a null or empty string value,
     * because empty pa[] elements can't be parsed
     */
    protected static final String EMPTYSTR  = "\t";

    protected SOCGameOption opt = null;

    /** Constructor for server to tell client about a game option */
    public SOCGameOptionInfo(SOCGameOption op)
    {
        // OTYPE_*
        super(GAMEOPTIONINFO, null,
            new String[12 + ( ((op.optType != SOCGameOption.OTYPE_ENUM)
                               && (op.optType != SOCGameOption.OTYPE_ENUMBOOL))
                    ? 0
                    : op.maxIntValue ) ]);
        opt = op;
        pa[0] = op.optKey;
        pa[1] = Integer.toString(op.optType);
        pa[2] = Integer.toString(op.minVersion);
        pa[3] = Integer.toString(op.lastModVersion);
        pa[4] = (op.defaultBoolValue ? "t" : "f");
        pa[5] = Integer.toString(op.defaultIntValue);
        pa[6] = Integer.toString(op.minIntValue);
        pa[7] = Integer.toString(op.maxIntValue);
        pa[8] = (op.getBoolValue() ? "t" : "f");
        if ((op.optType == SOCGameOption.OTYPE_STR) || (op.optType == SOCGameOption.OTYPE_STRHIDE))
        {
            String sv = op.getStringValue();
            if (sv.length() == 0)
                sv = EMPTYSTR;  // can't parse a null or 0-length pa[9]
            pa[9] = sv;
        } else {
            pa[9] = Integer.toString(op.getIntValue());
        }
        pa[10] = (op.dropIfUnused ? "t" : "f");
        pa[11] = op.optDesc;

        // for OTYPE_ENUM, _ENUMBOOL, pa[12+] are the enum choices' string values
        if ((op.optType == SOCGameOption.OTYPE_ENUM) || (op.optType == SOCGameOption.OTYPE_ENUMBOOL))
            System.arraycopy(op.enumVals, 0, pa, 12, op.enumVals.length);
    }

    /**
     * Constructor for client to parse server's reply about a game option.
     * If opt type number is unknown locally, will change to {@link SOCGameOption#OTYPE_UNKNOWN}.
     *
     * @param pa Parameters of the option: <pre>
     * pa[0] = key (name of the option)
     * pa[1] = type
     * pa[2] = minVersion
     * pa[3] = lastModVersion
     * pa[4] = defaultBoolValue ('t' or 'f')
     * pa[5] = defaultIntValue
     * pa[6] = minIntValue
     * pa[7] = maxIntValue
     * pa[8] = boolValue ('t' or 'f'; current, not default)
     * pa[9] = intValue (current, not default) or stringvalue
     * pa[10] = dropIfUnused ('t' or 'f')
     * pa[11] = optDesc (displayed text) if present; required for all but OTYPE_UNKNOWN
     * pa[12] and beyond, if present = each enum choice's text </pre>
     *
     * @throws IllegalArgumentException if pa.length < 11, or type is not a valid {@link SOCGameOption#optType};
     *      if type isn't {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM} or ENUMBOOL, pa.length must == 12 (or 11 for OTYPE_UNKNOWN).
     * @throws NumberFormatException    if pa integer-field contents are incorrectly formatted.
     */
    protected SOCGameOptionInfo(String[] pa)
        throws IllegalArgumentException, NumberFormatException
    {
	super(GAMEOPTIONINFO, null, pa);
	if (pa.length < 11)
	    throw new IllegalArgumentException("pa.length");

	// OTYPE_*
	int otyp = Integer.parseInt(pa[1]);
        if ((otyp < SOCGameOption.OTYPE_MIN) || (otyp > SOCGameOption.OTYPE_MAX))
            otyp = SOCGameOption.OTYPE_UNKNOWN;

        final int oversmin = Integer.parseInt(pa[2]);
	final int oversmod = Integer.parseInt(pa[3]);
	final boolean bval_def = (pa[4].equals("t"));
	final int ival_def = Integer.parseInt(pa[5]);
	final int ival_min = Integer.parseInt(pa[6]);
	final int ival_max = Integer.parseInt(pa[7]);
	final boolean bval_cur = (pa[8].equals("t"));
	final int ival_cur;
	String sval_cur;
	if ((otyp == SOCGameOption.OTYPE_STR) || (otyp == SOCGameOption.OTYPE_STRHIDE))
	{
	    ival_cur = 0;
	    sval_cur = pa[9];
	    if (sval_cur.equals(EMPTYSTR))
	        sval_cur = null;
	} else {
	    ival_cur = Integer.parseInt(pa[9]);
	    sval_cur = null;
	}
        final boolean skip_def = (pa[10].equals("t"));

        if ((pa.length != 11) && (pa.length != 12)
              && (otyp != SOCGameOption.OTYPE_ENUM)
              && (otyp != SOCGameOption.OTYPE_ENUMBOOL))
	    throw new IllegalArgumentException("pa.length");

	switch (otyp)  // OTYPE_*
	{
	case SOCGameOption.OTYPE_UNKNOWN:
	    opt = new SOCGameOption(pa[0]);
	    break;

	case SOCGameOption.OTYPE_BOOL:
	    opt = new SOCGameOption(pa[0], oversmin, oversmod, bval_def, skip_def, pa[11]);
	    opt.setBoolValue(bval_cur);
	    break;

	case SOCGameOption.OTYPE_INT:
	    opt = new SOCGameOption(pa[0], oversmin, oversmod, ival_def, ival_min, ival_max, skip_def, pa[11]);
	    opt.setIntValue(ival_cur);
	    break;

	case SOCGameOption.OTYPE_INTBOOL:
	    opt = new SOCGameOption(pa[0], oversmin, oversmod, bval_def, ival_def, ival_min, ival_max, skip_def, pa[11]);
	    opt.setBoolValue(bval_cur);
	    opt.setIntValue(ival_cur);
	    break;
	    
	case SOCGameOption.OTYPE_ENUM:
	    {
		String[] choices = new String[ival_max];
		System.arraycopy(pa, 12, choices, 0, ival_max);
	        opt = new SOCGameOption(pa[0], oversmin, oversmod, ival_def, skip_def, choices, pa[11]);
	        opt.setIntValue(ival_cur);
            }
	    break;

        case SOCGameOption.OTYPE_ENUMBOOL:
            {
                String[] choices = new String[ival_max];
                System.arraycopy(pa, 12, choices, 0, ival_max);
                opt = new SOCGameOption(pa[0], oversmin, oversmod, bval_def, ival_def, choices, skip_def, pa[11]);
                opt.setBoolValue(bval_cur);
                opt.setIntValue(ival_cur);
            }
            break;

        case SOCGameOption.OTYPE_STR:
	case SOCGameOption.OTYPE_STRHIDE:
	    opt = new SOCGameOption
		(pa[0], oversmin, oversmod, ival_max, (otyp == SOCGameOption.OTYPE_STRHIDE), skip_def, pa[11]);
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
        return pa[0];
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
     * Parse the command String array into a SOCGameOptionInfo message. <pre>
     * pa[0] = key (name of the {@link SOCGameOption option})
     * pa[1] = type
     * pa[2] = minVersion
     * pa[3] = lastModVersion
     * pa[4] = defaultBoolValue ('t' or 'f')
     * pa[5] = defaultIntValue
     * pa[6] = minIntValue
     * pa[7] = maxIntValue
     * pa[8] = boolValue ('t' or 'f'; current, not default)
     * pa[9] = intValue (current, not default) or stringvalue
     * pa[10] = dropIfUnused ('t' or 'f')
     * pa[11] = optDesc (displayed text) if present; required for all but OTYPE_UNKNOWN
     * pa[12] and beyond, if present = each enum choice's text </pre>
     *
     * @param pa   the String parameters
     * @return    a GameOptionInfo message, or null if parsing errors
     */
    public static SOCGameOptionInfo parseDataStr(String[] pa)
    {
        if ((pa == null) || (pa.length < 11))
            return null;
        try
        {
            return new SOCGameOptionInfo(pa);
        } catch (Throwable e)
        {
            return null;
        }
    }

}
