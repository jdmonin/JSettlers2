/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012,2014,2017 Jeremy D Monin <jeremy@nand.net>
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

/**
 * This message from server updates the value field(s) of a piece on the board.
 *
 * <H5>Current uses (v2.0.00):</H5>
 *<UL>
 * <LI> Cloth count for a village in the {@code SC_CLVI} cloth trade scenario
 * <LI> Fortress strength in the {@code SC_PIRI} pirate islands scenario
 *</UL>
 * Param 1: Coordinate of the piece to be updated <br>
 * Param 2: New value for the piece <br>
 * Param 3: New secondary value (if piece has 2 value fields), or 0
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCPieceValue extends SOCMessageTemplate3i
{
    private static final long serialVersionUID = 2000L;

    /**
     * Create a SOCPieceValue message.
     *
     * @param ga  the name of the game
     * @param coord  Coordinate of the piece to be updated
     * @param pv1    New value for the piece
     * @param pv2    New secondary value (if piece has 2 value fields), or 0
     */
    public SOCPieceValue(final String ga, final int coord, final int pv1, final int pv2)
    {
        super(PIECEVALUE, ga, coord, pv1, pv2);
    }

    /**
     * PIECEVALUE sep game sep2 coord sep2 pv1 sep2 pv2
     *
     * @param ga  the name of the game
     * @param coord  Coordinate of the piece to be updated
     * @param pv1    New value for the piece
     * @param pv2    New secondary value (if piece has 2 value fields), or 0
     * @return the command string
     */
    public static String toCmd(final String ga, final int coord, final int pv1, final int pv2)
    {
        return SOCMessageTemplate3i.toCmd(PIECEVALUE, ga, coord, pv1, pv2);
    }

    /**
     * Parse the command string into a SOCPieceValue message.
     *
     * @param s   the String to parse; format: game sep2 coord sep2 pv1 sep2 pv2
     * @return    a SOCPieceValue message, or null if parsing errors
     */
    public static SOCPieceValue parseDataStr(final String s)
    {
        String ga; // the game name
        int co;  // the piece coordinate
        int pv1; // value field 1
        int pv2; // value field 2

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            co = Integer.parseInt(st.nextToken());
            pv1 = Integer.parseInt(st.nextToken());
            pv2 = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPieceValue(ga, co, pv1, pv2);
    }

    /**
     * Minimum version where this message type is used.
     * PIECEVALUE introduced in 2.0.00 for the cloth villages scenario.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public int getMinimumVersion() { return 2000; }

}
