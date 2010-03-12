/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2010 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 **/
package soc.message;

import soc.game.SOCGameOption;
import soc.game.SOCPlayer;

/**
 * Information on one available {@link SOCGameOption game option}.
 * Sent at end of game, or by player's request ("*STATS*" command).
 * Design allows multiple types of stats.
 *<P>
 * Type 1: Resource roll stats: Introduced in 1.1.09;
 * check client version against {@link #VERSION_FOR_PLAYERSTATS_1}
 * before sending this message.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't care about their player stats.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.09
 */
public class SOCPlayerStats extends SOCMessageTemplateMi
{
    /** Constructor for server to tell client about a player stat */
    public SOCPlayerStats(SOCPlayer pl, int stype)
        throws IllegalArgumentException
    {
        super(PLAYERSTATS, pl.getGame().getName(),
            new int[6]);
        // TODO validate stype; need function for length based on type

        pa[0] = stype;
        final int[] rstats = pl.getResourceRollStats();
        for (int i = 1; i <= 5; ++i)
            pa[i] = rstats[i-1];
    }

    /**
     * Constructor for client to parse message from server.
     *
     * @param gameName Game name
     * @param pa Parameters of the option: <pre>
     * pa[0] = type (only type 1 is defined for now)
     * pa[1] = clay count
     * pa[2] = ore
     * pa[3] = sheep
     * pa[4] = wheat
     * pa[5] = wood count</pre>
     *
     * @throws IllegalArgumentException if pa.length != 6, or type is not valid
     */
    protected SOCPlayerStats(final String gameName, int[] pa)
        throws IllegalArgumentException
    {
	super(PLAYERSTATS, gameName, pa);
	if (pa.length != 6)
	    throw new IllegalArgumentException("pa.length");

	// TODO validate stype
    }

    /**
     * Minimum version where this message type is used.
     * PLAYERSTATS introduced in 1.1.09 for game-options feature.
     * @return Version number, 1109 for JSettlers 1.1.09.
     */
    public int getMinimumVersion() { return 1109; }

    /**
     * @return the player stat type
     * @see #getParams()
     */
    public int getStatType()
    {
        return pa[0];
    }

    /**
     * Parse the command String array into a SOCGameOptionInfo message. <pre>
     * pa[0] = type (1)
     * pa[1] = clay count
     * pa[2] = ore
     * pa[3] = sheep
     * pa[4] = wheat
     * pa[5] = wood count</pre>
     *
     * @param pa   the parameters
     * @return    a SOCPlayerStats message, or null if parsing errors
     */
    public static SOCPlayerStats parseDataStr(String[] pa)
    {
        if ((pa == null) || (pa.length != 6))
            return null;
        try
        {
            String ga = pa[0];
            int[] ipa = new int[pa.length - 1];
            for (int i = 0; i < ipa.length; ++i)
                ipa[i] = Integer.parseInt(pa[i+1]);
            return new SOCPlayerStats(ga, ipa);
        } catch (Throwable e)
        {
            return null;
        }
    }

}
