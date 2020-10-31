/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012,2014,2017-2020 Jeremy D Monin <jeremy@nand.net>
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
 *<H5>Current uses (v2.0.00):</H5>
 *<UL>
 * <LI> Cloth count for a village in the {@code SC_CLVI} Cloth Villages scenario
 * <LI> Fortress strength in the {@code SC_PIRI} Pirate Islands scenario
 *</UL>
 *
 *<H5>Parameters:</H5>
 *<OL>
 * <LI> Type of the piece to be updated, such as {@link soc.game.SOCPlayingPiece#FORTRESS}. <br>
 *      Client can ignore this field unless the game's scenario uses values on multiple piece types.
 * <LI> Coordinate of the piece
 * <LI> New value for the piece
 * <LI> New secondary value (if piece has 2 value fields), or 0
 *</OL>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCPieceValue extends SOCMessageTemplate4i
{
    private static final long serialVersionUID = 2000L;

    /**
     * Create a SOCPieceValue message.
     *
     * @param ga  the name of the game
     * @param pt  Type of the piece to be updated, such as {@link soc.game.SOCPlayingPiece#FORTRESS}
     * @param coord  Coordinate of the piece
     * @param pv1    New value for the piece
     * @param pv2    New secondary value if piece has 2 value fields, or 0
     */
    public SOCPieceValue(final String ga, final int pt, final int coord, final int pv1, final int pv2)
    {
        super(PIECEVALUE, ga, pt, coord, pv1, pv2);
    }

    /**
     * Parse the command string into a SOCPieceValue message.
     *
     * @param s   the String to parse; format: game sep2 piecetype sep2 coord sep2 pv1 sep2 pv2
     * @return    a SOCPieceValue message, or null if parsing errors
     */
    public static SOCPieceValue parseDataStr(final String s)
    {
        String ga; // the game name
        int pt;  // piece type
        int co;  // the piece coordinate
        int pv1; // value field 1
        int pv2; // value field 2, or 0

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pt = Integer.parseInt(st.nextToken());
            co = Integer.parseInt(st.nextToken());
            pv1 = Integer.parseInt(st.nextToken());
            pv2 = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPieceValue(ga, pt, co, pv1, pv2);
    }

    /**
     * Minimum version where this message type is used.
     * PIECEVALUE introduced in 2.0.00 for the Cloth Villages and Pirate Islands scenarios.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public int getMinimumVersion() { return 2000; }

    /**
     * Build a human-readable form of the message, with this class's field names
     * instead of generic names from {@link SOCMessageTemplate4i}.
     * @return a human readable form of the message
     * @since 2.4.50
     */
    @Override
    public String toString()
    {
        return "SOCPieceValue:game=" + game
            + "|pieceType=" + p1 + "|coord=" + p2
            + "|pv1=" + p3 + "|pv2=" + p4;
    }

}
