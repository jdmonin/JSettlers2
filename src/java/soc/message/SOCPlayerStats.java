/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2010,2012,2014-2016 Jeremy D Monin <jeremy@nand.net>
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

import java.util.List;

import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;

/**
 * Statistics of one type for one player.
 * Sent at end of game, or by player's request ("*STATS*" command).
 * Design allows multiple types of stats.
 * The first item in this message is the type number.
 *<P>
 * <B>Type 1:</B> Resource roll stats: Introduced in 1.1.09;
 * check client version against {@link #VERSION_FOR_RES_ROLL}
 * before sending this message.
 * For format details see {@link #SOCPlayerStats(String, int[])}.
 *<P>
 * In 2.0.00 and newer, this type optionally includes an additional
 * item for the number of gold hex resource picks, or 0 if omitted.
 * Older clients would ignore the extra item, but wouldn't be compatible
 * anyway with any game scenario that features gold hexes.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't care about their player stats.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.09
 */
public class SOCPlayerStats extends SOCMessageTemplateMi
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /** Lowest-numbered stats type (1) */
    public static final int STYPE_MIN = 1;

    /** Stats type 1: Resource roll stats.
     *  Each resource's count includes resources picked from a rolled <tt>GOLD_HEX</tt>.
     *  For the Fog Scenario, includes resources picked when building
     *  a road or ship revealed gold from a fog hex.
     *<P>
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
        super(PLAYERSTATS, pl.getGame().getName(), new int[len(pl, stype)]);  // len is almost always 6
        if ((stype < STYPE_MIN) || (stype > STYPE_MAX))
            throw new IllegalArgumentException("stype out of range: " + stype);

        pa[0] = stype;
        // Right now, only STYPE_RES_ROLL is defined
        final int[] rstats = pl.getResourceRollStats();  // rstats[0] is unused
        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; ++i)
            pa[i] = rstats[i];
        if (pa.length > SOCResourceConstants.GOLD_LOCAL)
            pa[SOCResourceConstants.GOLD_LOCAL] = rstats[SOCResourceConstants.GOLD_LOCAL];
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
     * pa[5] = wood count
     * pa[6] = gold pick count, or 0 if omitted (v2.0.00+)</pre>
     *  <P>
     * In 2.0.00 and newer, type 1 optionally includes an additional
     * item for the number of gold hex resource picks, or 0 if omitted.
     * Older clients would ignore the extra item, but wouldn't be compatible
     * anyway with any game scenario that features gold hexes.
     */
    protected SOCPlayerStats(final String gameName, int[] pa)
        throws IllegalArgumentException
    {
	super(PLAYERSTATS, gameName, pa);
    }

    /**
     * Given a stat type and specific player, find the array length needed
     * to send that player's stats of that type.
     * @param pl  Player for these stats
     * @param stype  Stats type, such as {@link #STYPE_RES_ROLL}; see class javadoc or constructors
     * @return  Stat array length, including {@code stype} at index 0; always 6 before v2.0.00
     * @throws IllegalArgumentException if {@code stype} &lt; {@link #STYPE_MIN}
     *           or > {@link #STYPE_MAX}
     * @throws NullPointerException if {@code pl} null
     */
    private static final int len(final SOCPlayer pl, final int stype)
        throws IllegalArgumentException, NullPointerException
    {
        switch (stype)
        {
        case STYPE_RES_ROLL:
            {
                final boolean hasGold =
                    (pl.getResourceRollStats()[SOCResourceConstants.GOLD_LOCAL] != 0);

                return 1 + 5 + ((hasGold) ? 1 : 0);
            }

        default:  // (stype < STYPE_MIN) || (stype > STYPE_MAX)
            throw new IllegalArgumentException("stype out of range: " + stype);
        }
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
     * Parse the command String list into a SOCPlayerStats message.
     * Calls {@link #SOCPlayerStats(String, int[])} constructor,
     * see its javadoc for parameter details.
     *
     * @param pa   the parameters; length 2 or more required.
     * @return    a SOCPlayerStats message, or null if parsing errors
     */
    public static SOCPlayerStats parseDataStr(List<String> pa)
    {
        if (pa == null)
            return null;
        final int L = pa.size();
        if (L < 2)
            return null;

        try
        {
            final String gaName = pa.get(0);
            int[] ipa = new int[L - 1];
            for (int i = 0; i < ipa.length; ++i)
                ipa[i] = Integer.parseInt(pa.get(i + 1));

            return new SOCPlayerStats(gaName, ipa);
        } catch (Exception e) {
            return null;
        }
    }

}
