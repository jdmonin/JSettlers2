/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2018 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
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
package soc.game;


/**
 * A playing piece that connects settlements and cities and can be part of Longest Route:
 * A {@link SOCRoad} or (on the large sea board) {@link SOCShip}.
 * @since 2.0.00
 */
public abstract class SOCRoutePiece extends SOCPlayingPiece
{
    private static final long serialVersionUID = 2000L;

    /**
     * Make a new route piece.
     *
     * @param pl  player who owns the piece
     * @param ptype  piece type, like {@link SOCPlayingPiece#ROAD} or {@link SOCPlayingPiece#SHIP}
     * @param edge  piece's edge coordinate
     * @param board  board if known; otherwise will extract from {@code pl}
     * @throws IllegalArgumentException  if {@code pl} null, or board null and {@code pl.board} also null
     */
    protected SOCRoutePiece(final SOCPlayer pl, final int ptype, final int edge, final SOCBoard board)
        throws IllegalArgumentException
    {
        super(ptype, pl, edge, board);
    }

    /**
     * The 2 nodes touching this road or ship.
     * @return the 2 nodes touching this piece, same format as {@link SOCBoard#getAdjacentNodesToEdge_arr(int)}
     */
    public int[] getAdjacentNodes()
    {
        return board.getAdjacentNodesToEdge_arr(coord);
    }

    /**
     * Is this piece a road on land, and not a ship on water?
     * Convenience method for readability in boolean tests.
     * @return True for roads (pieceType {@link SOCPlayingPiece#ROAD}), false otherwise
     */
    public final boolean isRoadNotShip()
    {
        return (pieceType == SOCPlayingPiece.ROAD);
    }

}
