/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.game;

import java.util.Vector;


/**
 * A settlement playing piece
 */
@SuppressWarnings("serial")
public class SOCSettlement extends SOCPlayingPiece
{
    /**
     * Make a new settlement
     *
     * @param pl  player who owns the city
     * @param co  coordinates
     */
    public SOCSettlement(SOCPlayer pl, int co)
    {
        pieceType = SOCPlayingPiece.SETTLEMENT;
        player = pl;
        coord = co;
    }

    /**
     * @return the hexes touching this settlement
     */
    public Vector<Integer> getAdjacentHexes()
    {
        return SOCBoard.getAdjacentHexesToNode(coord);
    }

    /**
     * @return edges touching this settlement
     */
    public Vector<Integer> getAdjacentEdges()
    {
        return SOCBoard.getAdjacentEdgesToNode(coord);
    }
}
