/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2011-2012,2014 Jeremy D Monin <jeremy@nand.net>
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
 * A road playing piece, or (on the large sea board) a {@link SOCShip ship} playing piece.
 *<P>
 * To simplify some game code, ships are a subclass of roads.
 * To see if this piece is actually a road, check {@link #isRoadNotShip()}.
 */
@SuppressWarnings("serial")
public class SOCRoad extends SOCPlayingPiece
{
    /**
     * Make a new road
     *
     * @param pl  player who owns the road
     * @param co  coordinates
     * @param board  board if known; otherwise will extract from <tt>pl</tt>
     * @throws IllegalArgumentException  if <tt>pl</tt> null, or board null and <tt>pl.board</tt> also null
     */
    public SOCRoad(SOCPlayer pl, int co, SOCBoard board)
        throws IllegalArgumentException
    {
        super(SOCPlayingPiece.ROAD, pl, co, board);
    }

    /**
     * The 2 nodes touching this road.
     * @return the 2 nodes touching this road, same format as {@link SOCBoard#getAdjacentNodesToEdge_arr(int)}
     */
    public int[] getAdjacentNodes()
    {
        return board.getAdjacentNodesToEdge_arr(coord);
    }

    /**
     * Is this piece really a road on land, and not a ship on water (our subclass)?
     * @return True for roads (pieceType {@link SOCPlayingPiece#ROAD}), false otherwise
     * @since 2.0.00
     */
    public final boolean isRoadNotShip()
    {
        return (pieceType == SOCPlayingPiece.ROAD);
    }

}
