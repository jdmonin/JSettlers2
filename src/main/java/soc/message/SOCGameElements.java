/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;  // for javadocs only


/**
 * This message from the server sets or updates fields of a game's status,
 * such as the number of rounds played or development cards available.
 *<P>
 * Versions older than v2.0.00 instead used single-purpose message types
 * like {@link SOCLongestRoad} and {@link SOCDevCardCount}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @see SOCPlayerElements
 */
public class SOCGameElements extends SOCMessageTemplateMi
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Version number (2.0.00) where this message type was introduced.
     */
    public static final int MIN_VERSION = 2000;

    // -----------------------------------------------------------
    // Game element type list:

    /** Current round of play, from {@link SOCGame#getRoundCount()}. */
    public static final int ROUND_COUNT = 1;

    /**
     * Number of development cards remaining in the deck to be bought,
     * from {@link SOCGame#getNumDevCards()}.
     *<P>
     * Versions before v2.0.00 sent {@link SOCDevCardCount} instead.
     */
    public static final int DEV_CARD_COUNT = 2;

    /**
     * Player number of first player in this game, from {@link SOCGame#getFirstPlayer()}.
     *<P>
     * Versions before v2.0.00 sent {@link SOCFirstPlayer} instead.
     */
    public static final int FIRST_PLAYER = 3;

    /**
     * Player number of current player, or -1, from {@link SOCGame#getCurrentPlayerNumber()}.
     *<P>
     * Versions before v2.0.00 sent {@link SOCSetTurn} instead.
     */
    public static final int CURRENT_PLAYER = 4;

    /**
     * Player number of player with largest army, or -1, from {@link SOCGame#getPlayerWithLargestArmy()}.
     *<P>
     * Versions before v2.0.00 sent {@link SOCLargestArmy} instead.
     */
    public static final int LARGEST_ARMY_PLAYER = 5;

    /**
     * Player number of player with longest road, or -1, from {@link SOCGame#getPlayerWithLongestRoad()}.
     *<P>
     * Versions before v2.0.00 sent {@link SOCLongestRoad} instead.
     */
    public static final int LONGEST_ROAD_PLAYER = 6;

    // End of element type list.
    // -----------------------------------------------------------

    /**
     * Element types such as {@link #DEV_CARD_COUNT}, each matching up
     * with the same-index item of parallel array {@link #values}.
     */
    private int[] elementTypes;

    /**
     * Element values to set, each matching up with the same-index item
     * of parallel array {@link #elementTypes}.
     */
    private int[] values;

    /**
     * Create a GameElements message about multiple elements.
     *
     * @param ga  name of the game
     * @param etypes  element types to set, such as {@link #DEV_CARD_COUNT}
     * @param values  new values for each element, corresponding to <tt>etypes[]</tt>
     * @see #SOCGameElements(String, int, int)
     * @throws NullPointerException if {@code etypes} null or {@code values} null
     * @throws IllegalArgumentException if {@code etypes.length != values.length}
     */
    public SOCGameElements(final String ga, final int[] etypes, final int[] values)
        throws NullPointerException, IllegalArgumentException
    {
        super(GAMEELEMENTS, ga, new int[2 * etypes.length]);
        if (values == null)
            throw new NullPointerException();
        final int L = etypes.length;
        if (values.length != L)
            throw new IllegalArgumentException("lengths");

        elementTypes = etypes;
        this.values = values;
        for (int pai = 0, eti = 0; eti < L; ++eti)
        {
            pa[pai] = etypes[eti];  ++pai;
            pa[pai] = values[eti];  ++pai;
        }
    }

    /**
     * Create a GameElements message about one element.
     *
     * @param ga  name of the game
     * @param etype  the type of element, such as {@link #DEV_CARD_COUNT}
     * @param value  the value to set the element to
     * @see #SOCGameElements(String, int[], int[])
     */
    public SOCGameElements(final String ga, final int etype, final int value)
    {
        this(ga, new int[]{etype}, new int[]{value});
    }

    /**
     * Minimum version where this message type is used.
     * GAMEELEMENTS was introduced in v2.0.00 ({@link #MIN_VERSION}).
     * @return Version number, 2000 for JSettlers 2.0.00
     */
    @Override
    public final int getMinimumVersion() { return MIN_VERSION; }

    /**
     * @return the element types such as {@link #DEV_CARD_COUNT}, each matching up
     *     with the same-index item of parallel array {@link #getValues()}.
     */
    public int[] getElementTypes()
    {
        return elementTypes;
    }

    /**
     * @return the element values to set, each matching up with the same-index item
     *     of parallel array {@link #getElementTypes()}.
     */
    public int[] getValues()
    {
        return values;
    }

    /**
     * Parse the command String list into a SOCGameElements message.
     *
     * @param pa   the parameters; length 3 or more required.
     *     Built by constructor at server. Length must be odd. <pre>
     * pa[0] = gameName
     * pa[1] = elementType[0]
     * pa[2] = value[0]
     * pa[3] = elementType[1]
     * pa[4] = value[1]
     * ...</pre>
     * @return    a SOCGameElements message, or null if parsing errors
     */
    public static SOCGameElements parseDataStr(final List<String> pa)
    {
        if (pa == null)
            return null;
        final int L = pa.size();
        if ((L < 3) || ((L % 2) == 0))
            return null;

        try
        {
            final String gaName = pa.get(0);

            final int n = (L - 1) / 2;
            int[] elementTypes = new int[n], values = new int[n];
            for (int i = 0, pai = 1; i < n; ++i)
            {
                elementTypes[i] = Integer.parseInt(pa.get(pai));  ++pai;
                values[i] = Integer.parseInt(pa.get(pai));  ++pai;
            }

            return new SOCGameElements(gaName, elementTypes, values);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder
            ("SOCGameElements:game=" + game + '|');
        for (int i = 0; i < pa.length; )
        {
            if (i > 0)
                sb.append(',');
            sb.append('e');
            sb.append(pa[i]);  ++i;
            sb.append('=');
            sb.append(pa[i]);  ++i;
        }

        return sb.toString();
    }

}
