/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2010 Jeremy D Monin <jeremy@nand.net>
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
 **/
package soc.message;

import soc.game.SOCPlayer;

/**
 * Statistics of one type for one player.
 * Sent at end of game, or by player's request ("*STATS*" command).
 * Design allows multiple types of stats.
 *<P>
 * Type 1: Resource roll stats: Introduced in 1.1.09;
 * check client version against {@link #VERSION_FOR_RES_ROLL}
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
    /** Lowest-numbered stats type (1) */
    public static final int STYPE_MIN = 1;

    /** Stats type 1: Resource roll stats.
     *  Check client version against {@link #VERSION_FOR_RES_ROLL}
     *  before sending this message.
     */
    public static final int STYPE_RES_ROLL = 1;

    /** Highest-numbered stat stype in this version (1) */
    public static final int STYPE_MAX = 1;

    /** Minimum client version for stats type 1 (resource roll stats). */
    public static final int VERSION_FOR_RES_ROLL = 1109;

    /**
     * Constructor for server to tell client about a player stat.
     * 
     * @param pl  Player for these stats
     * @param stype  Stats type.  Newer servers and clients may support
     *           more types.  For each type (such as {@link #STYPE_RES_ROLL}),
     *           check the corresponding VERSION_FOR_ field before sending.
     * @throws IllegalArgumentException if <tt>stype</tt> < {@link #STYPE_MIN}
     *           or > {@link #STYPE_MAX}
     * @throws NullPointerException if <tt>pl</tt> null
     */
    public SOCPlayerStats(SOCPlayer pl, int stype)
        throws IllegalArgumentException, NullPointerException
    {
        super(PLAYERSTATS, pl.getGame().getName(),
            new int[6]);
        if ((stype < STYPE_MIN) || (stype > STYPE_MAX))
            throw new IllegalArgumentException("stype out of range: " + stype);

        pa[0] = stype;        
        final int[] rstats = pl.getResourceRollStats();  // rstats[0] is unused
        for (int i = 1; i <= 5; ++i)
            pa[i] = rstats[i];
    }

    /**
     * Constructor for client to parse message from server.
     * Note that neither type, nor pa length, are validated,
     * so that future stat types can be passed along.
     *
     * @param gameName Game name
     * @param pa Parameters of the option: <pre>
     * pa[0] = type (only type 1 is defined for now):
     *         Use {@link #STYPE_RES_ROLL} for this parameter.
     * pa[1] = clay count
     * pa[2] = ore
     * pa[3] = sheep
     * pa[4] = wheat
     * pa[5] = wood count</pre>
     */
    protected SOCPlayerStats(final String gameName, int[] pa)
        throws IllegalArgumentException
    {
	super(PLAYERSTATS, gameName, pa);
    }

    /**
     * Minimum version where this message type is used.
     * PLAYERSTATS introduced in 1.1.09 for game-options feature.
     * @return Version number, 1109 for JSettlers 1.1.09.
     */
    public int getMinimumVersion() { return 1109; }

    /**
     * Get the stat type.<P>
     * For the actual statistic data. call {@link #getParams()} but
     * remember that stats[i] will be in params[i+1],
     * because params[0] is the stat type.
     *
     * @return the player stat type, such as {@link #STYPE_RES_ROLL}
     */
    public int getStatType()
    {
        return pa[0];
    }

    /**
     * Parse the command String array into a SOCPlayerStats message.
     * Calls {@link #SOCPlayerStats(String, int[])} constructor,
     * see its javadoc for parameter details.
     *
     * @param pa   the parameters; length 2 or more required.
     * @return    a SOCPlayerStats message, or null if parsing errors
     */
    public static SOCPlayerStats parseDataStr(String[] pa)
    {
        if ((pa == null) || (pa.length < 2))
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
