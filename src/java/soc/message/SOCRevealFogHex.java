/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012,2014 Jeremy D Monin <jeremy@nand.net>
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

import java.util.StringTokenizer;

import soc.game.SOCBoard;  // for javadocs only

/**
 * This message from server reveals a hex previously hidden by fog on the large sea board.
 * Hexes are revealed by placing roads or ships that touch a corner of a fog hex.
 * This message is sent out before the {@link SOCPutPiece} for the new road or ship.
 *<P>
 * Param 1: Coordinate of the land hex to reveal <br>
 * Param 2: Revealed hex type, same value as {@link SOCBoard#getHexTypeFromCoord(int)} <br>
 * Param 3: Revealed hex dice number, same value as {@link SOCBoard#getNumberOnHexFromCoord(int)}, or 0
 *<P>
 * Used with game option/scenario {@link soc.game.SOCGameOption#K_SC_FOG SOCGameOption.K_SC_FOG}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCRevealFogHex extends SOCMessageTemplate3i
{
    private static final long serialVersionUID = 2000L;

    /**
     * Create a SOCRevealFogHex message.
     *
     * @param ga  the name of the game
     * @param hexCoord  Coordinate of the land hex to reveal
     * @param hexType   Revealed hex type, same value as {@link SOCBoard#getHexTypeFromCoord(int)}
     * @param diceNum   Revealed hex dice number, same value as {@link SOCBoard#getNumberOnHexFromCoord(int)}, or 0
     */
    public SOCRevealFogHex(final String ga, final int hexCoord, final int hexType, final int diceNum)
    {
        super(REVEALFOGHEX, ga, hexCoord, hexType, diceNum);
    }

    /**
     * REVEALFOGHEX sep game sep2 hexcoord sep2 hextype sep2 dicenum
     *
     * @param ga  the name of the game
     * @param hexCoord  Coordinate of the land hex to reveal
     * @param hexType   Revealed hex type, same value as {@link SOCBoard#getHexTypeFromCoord(int)}
     * @param diceNum   Revealed hex dice number, same value as {@link SOCBoard#getNumberOnHexFromCoord(int)}, or 0
     * @return the command string
     */
    public static String toCmd(final String ga, final int hexCoord, final int hexType, final int diceNum)
    {
        return SOCMessageTemplate3i.toCmd(REVEALFOGHEX, ga, hexCoord, hexType, diceNum);
    }

    /**
     * Parse the command string into a SOCRevealFogHex message.
     *
     * @param s   the String to parse; format: game sep2 hexcoord sep2 hextype sep2 dicenum
     * @return    a SOCRevealFogHex message, or null if parsing errors
     */
    public static SOCRevealFogHex parseDataStr(String s)
    {
        String ga; // the game name
        int hc; // the hex coordinate
        int ht; // hex type
        int dn; // dice number

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            hc = Integer.parseInt(st.nextToken());
            ht = Integer.parseInt(st.nextToken());
            dn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCRevealFogHex(ga, hc, ht, dn);
    }

    /**
     * Minimum version where this message type is used.
     * REVEALFOGHEX introduced in 2.0.00 for fog on the large sea board.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public int getMinimumVersion() { return 2000; }

}
