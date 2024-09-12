/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2021 Jeremy D Monin <jeremy@nand.net>
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


/**
 * This debug/testing message from the server asks a bot to check part of its game data for accuracy
 * to the server's values. The bot's values could drift because of robot bugs, or message sequence
 * bugs during development. Currently checks 1 data type: {@link #TYPE_RESOURCE_AMOUNTS}.
 *<P>
 * When the built-in robot brain receives this message, it checks its game data.
 * If any discrepancies are found, they're printed along with the message sequence for
 * the previous and current turn. The bot then corrects its data to the known values
 * to continue the game or current test.
 *<P>
 * Activated at server via {@link soc.server.SOCServer#PROP_JSETTLERS_DEBUG_BOTS_DATACHECK_RSRC}.
 *
 *<H4>Third-Party Bots</H4>
 * Robots are free to ignore this message type, but may find it useful.
 * If your bot doesn't override {@link soc.robot.SOCRobotBrain#handleBOTGAMEDATACHECK(int, int[])},
 * your bot handles this message type.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.5.00
 */
public class SOCBotGameDataCheck extends SOCMessageTemplateMi
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2500L;  // v2.5.00

    /**
     * Version number (2.5.00) where this message type was introduced.
     */
    public static final int MIN_VERSION = 2500;

    /**
     * Resource Amounts data type.
     * {@link #getValues()} format is a per-player sequence of amounts known at the server,
     * each non-vacant player followed by the next:
     * playerNumber numClay numOre numSheep numWheat numWood
     */
    public static final int TYPE_RESOURCE_AMOUNTS = 1;

    /**
     * Data element type to check.
     * See {@link #getDataType()} for details.
     */
    private final int dataType;

    /**
     * Data element values to check
     */
    private final int[] values;

    /**
     * Create a BotGameDataCheck message.
     *
     * @param ga  Name of the game
     * @param dtype  Game data type to check, such as {@link #TYPE_RESOURCE_AMOUNTS}.
     *     Bot can ignore a message having any type it doesn't recognize.
     * @param values  Values for each element; format differs by {@code dtype}
     * @throws NullPointerException if {@code values} null or empty
     */
    public SOCBotGameDataCheck(final String ga, final int dtype, final int[] values)
        throws NullPointerException
    {
        super(BOTGAMEDATACHECK, ga, new int[1 + values.length]);
        final int L = values.length;
        if (L == 0)
            throw new NullPointerException("length 0");

        dataType = dtype;
        this.values = values;
        pa[0] = dtype;
        System.arraycopy(values, 0, pa, 1, L);
    }

    /**
     * Minimum version where this message type is used.
     * BOTGAMEDATACHECK was introduced in v2.5.00 ({@link #MIN_VERSION}).
     * @return Version number, 2500 for JSettlers 2.5.00
     */
    @Override
    public final int getMinimumVersion() { return MIN_VERSION; }

    /**
     * Get the data type. These are ints to preserve values from unknown types from different versions.
     * Bot can ignore a message having any type it doesn't recognize.
     * @return the data element type, such as {@link #TYPE_RESOURCE_AMOUNTS}
     * @see #getValues()
     */
    public int getDataType()
    {
        return dataType;
    }

    /**
     * Get the values for each element. Format is specific to each {@link #getDataType()}; see those types' javadocs.
     * @return the element values to check
     */
    public int[] getValues()
    {
        return values;
    }

    /**
     * Parse the command String list into a SOCBotGameDataCheck message.
     *
     * @param pa   the parameters; length 3 or more required.
     *     Built by constructor at server. <pre>
     * pa[0] = gameName
     * pa[1] = dataType
     * pa[2] = value[0]
     * pa[3] = value[1]
     * pa[4] = value[2]
     * ...</pre>
     * @return    a SOCBotGameDataCheck message, or null if parsing errors
     */
    public static SOCBotGameDataCheck parseDataStr(final List<String> pa)
    {
        if (pa == null)
            return null;
        final int L = pa.size();
        if (L < 3)
            return null;

        try
        {
            final String gaName = pa.get(0);
            final int dataType = Integer.parseInt(pa.get(1));

            int[] values = new int[L - 2];
            for (int i = 2; i < L; ++i)
                values[i - 2] = Integer.parseInt(pa.get(i));

            return new SOCBotGameDataCheck(gaName, dataType, values);
        } catch (Exception e) {
            return null;
        }
    }

}
