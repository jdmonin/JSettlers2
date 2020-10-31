/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017,2019-2020 Jeremy D Monin <jeremy@nand.net>
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
 * @since 2.0.00
 */
public class SOCGameElements extends SOCMessageTemplateMi
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Version number (2.0.00) where this message type was introduced.
     */
    public static final int MIN_VERSION = 2000;

    /**
     * Game element type list.
     * To send over the network as an int, use {@link #getValue()}.
     * When received from network as int, use {@link #valueOf(int)} to convert to {@link GEType}.
     *<P>
     * Converted from int constants to enum in v2.3.00 for cleaner design and human-readable serialization
     * for {@link soc.server.savegame.SavedGameModel}.
     * @since 2.3.00
     */
    public enum GEType
    {
        /**
         * Type to use when converting from int but value is unknown.
         * Note: {@link #valueOf(int)} returns {@code null} and not this value.
         * @since 2.3.00
         */
        UNKNOWN_TYPE(0),

        /** Current round of play, from {@link SOCGame#getRoundCount()}. */
        ROUND_COUNT(1),

        /**
         * Number of development cards remaining in the deck to be bought,
         * from {@link SOCGame#getNumDevCards()}.
         *<P>
         * Sent to clients during game join/start. When a dev card is bought,
         * is sent to clients as part of game data before action announcement/display:
         * See {@link SOCBuyDevCardRequest} javadoc.
         *<P>
         * Versions before v2.0.00 sent {@link SOCDevCardCount} instead.
         */
        DEV_CARD_COUNT(2),

        /**
         * Player number of first player in this game, from {@link SOCGame#getFirstPlayer()}.
         *<P>
         * Versions before v2.0.00 sent {@link SOCFirstPlayer} instead.
         */
        FIRST_PLAYER(3),

        /**
         * Player number of current player, or -1, from {@link SOCGame#getCurrentPlayerNumber()}.
         *<P>
         * Versions before v2.0.00 sent {@link SOCSetTurn} instead.
         */
        CURRENT_PLAYER(4),

        /**
         * Player number of player with Largest Army, or -1, from {@link SOCGame#getPlayerWithLargestArmy()}.
         * Sent when client joins a game, and when changes occur during normal gameplay
         * in response to a player's <tt>{@link SOCPlayDevCardRequest}(KNIGHT)</tt>.
         *<P>
         * In versions before v2.4.00, was not sent by server during game play when Largest Army player changed:
         * Client updated that display by examining game state;
         * see {@link SOCPlayerElement.PEType#NUMKNIGHTS} javadoc.
         * Such clients can be sent this element during gameplay, they'll process it just like newer clients do.
         *<P>
         * Versions before v2.0.00 sent {@link SOCLargestArmy} instead of this element.
         */
        LARGEST_ARMY_PLAYER(5),

        /**
         * Player number of player with Longest Road/Route, or -1, from {@link SOCGame#getPlayerWithLongestRoad()}.
         * Sent when client joins a game, and when changes occur during normal gameplay
         * in response to a player's {@link SOCPutPiece}, {@link SOCMovePiece}, or {@link SOCDebugFreePlace}.
         *<P>
         * In versions before v2.4.00, was not sent by server during game play when Longest Route player changed:
         * Client updated that display by examining game state;
         * see {@link SOCPutPiece} javadoc section on {@link soc.game.SOCPlayingPiece#ROAD}.
         * Such clients can be sent this element during gameplay, they'll process it just like newer clients do.
         *<P>
         * Versions before v2.0.00 sent {@link SOCLongestRoad} instead of this element.
         */
        LONGEST_ROAD_PLAYER(6),

        /**
         * During 6-player game's Special Building Phase,
         * the value of {@link SOCGame#getSpecialBuildingPlayerNumberAfter()}.
         *<P>
         * Not sent to clients over network; used only by {@link soc.server.savegame.SavedGameModel}
         * when gameState is {@link SOCGame#SPECIAL_BUILDING}.
         * 
         * @since 2.3.00
         */
        SPECIAL_BUILDING_AFTER_PLAYER(7);

        private int value;

        private GEType(final int v)
        {
            value = v;
        }

        /**
         * Get a type's integer value ({@link #DEV_CARD_COUNT} == 2, etc).
         * @see #valueOf(int)
         */
        public int getValue()
        {
            return value;
        }

        /**
         * Get a GEType from its int {@link #getValue()}, if type is known.
         * @param ti  Type int value ({@link #DEV_CARD_COUNT} == 2, etc).
         * @return  GEType for that value, or {@code null} if unknown
         */
        public static GEType valueOf(final int ti)
        {
            for (GEType et : values())
                if (et.value == ti)
                    return et;

            return null;
        }

        /**
         * Get a type array's integer values. Calls {@link #getValue()} for each element.
         * @param pe Element array, or {@code null}
         * @return  Int value array of same size as {@code ge}, or {@code null}
         * @throws NullPointerException if {@code ge} contains null values
         */
        public static int[] getValues(GEType[] ge)
            throws NullPointerException
        {
            if (ge == null)
                return null;

            final int L = ge.length;
            final int[] iv = new int[L];
            for (int i = 0; i < L; ++i)
                iv[i] = ge[i].value;

            return iv;
        }
    }

    // End of element type list.
    // -----------------------------------------------------------

    /**
     * Element types such as {@link GEType#DEV_CARD_COUNT} as ints, each matching up
     * with the same-index item of parallel array {@link #values}.
     * See {@link #getElementTypes()} for details.
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
     * @param etypes  element types to set, such as {@link GEType#DEV_CARD_COUNT}
     * @param values  new values for each element, corresponding to <tt>etypes[]</tt>
     * @see #SOCGameElements(String, GEType, int)
     * @throws NullPointerException if {@code etypes} null or {@code values} null
     *     or {@code etypes} contains {@code null}
     * @throws IllegalArgumentException if {@code etypes.length != values.length}
     */
    public SOCGameElements(final String ga, final GEType[] etypes, final int[] values)
        throws NullPointerException, IllegalArgumentException
    {
        this(ga, GEType.getValues(etypes), values);
    }

    private SOCGameElements(final String ga, final int[] etypes, final int[] values)
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
     * @param etype  the type of element, such as {@link GEType#DEV_CARD_COUNT}
     * @param value  the value to set the element to
     * @throws NullPointerException if {@code etype} is null
     * @see #SOCGameElements(String, GEType[], int[])
     */
    public SOCGameElements(final String ga, final GEType etype, final int value)
        throws NullPointerException
    {
        this(ga, new int[]{etype.value}, new int[]{value});
    }

    /**
     * Minimum version where this message type is used.
     * GAMEELEMENTS was introduced in v2.0.00 ({@link #MIN_VERSION}).
     * @return Version number, 2000 for JSettlers 2.0.00
     */
    @Override
    public final int getMinimumVersion() { return MIN_VERSION; }

    /**
     * Get the element types. These are ints to preserve values from unknown types from different versions.
     * Converted at sending side with {@link GEType#getValue()}.
     * To convert at receiving side to a {@link GEType}, use {@link GEType#valueOf(int)}.
     * @return the element types such as {@link GEType#DEV_CARD_COUNT} as ints, each matching up
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
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a list for {@link #parseDataStr(List)}.
     * Handles elemNum=value pairs, undoes mapping of action constant integers -> strings ({@code "GAIN"} etc).
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters to finish parsing into a SOCMessage, or {@code null} if malformed
     * @since 2.4.50
     */
    public static List<String> stripAttribsToList(String messageStrParams)
    {
        // don't call SOCMessage.stripAttribsToList, we need the e# names in elemNum=value pairs

        String[] pieces = messageStrParams.split(sepRE);
        // [0] game=...
        // [1] e#=#,e#=#,...

        if (pieces.length != 2)
            return null;

        List<String> ret = new ArrayList<>();

        if (pieces[0].startsWith("game="))
            ret.add(pieces[0].substring(5));
        else
            return null;

        // "e5=9,e7=12" -> "5", "9, "7", "12"
        pieces = pieces[1].split(",");
        for (int i = 0; i < pieces.length; ++i)
        {
            String piece = pieces[i];  // "e5=9"
            if (piece.charAt(0) != 'e')
                return null;

            int j = piece.indexOf('=');
            if (j < 2)
                return null;

            ret.add(piece.substring(1, j));
            ret.add(piece.substring(j + 1));
        }

        return ret;
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
