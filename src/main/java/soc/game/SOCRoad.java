/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2011-2012,2014,2017-2018 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
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
 * A road playing piece.
 *<P>
 * To simplify some game code, roads and {@link SOCShip}s have a common superclass.
 */
@SuppressWarnings("serial")
public class SOCRoad extends SOCRoutePiece
{
    /**
     * The set of resources a player needs to build a {@link SOCRoad road}.
     *<P>
     * Before v2.0.00, this field was {@code SOCGame.ROAD_SET}.
     * @see SOCPlayingPiece#getResourcesToBuild(int)
     * @since 2.0.00
     */
    public static final SOCResourceSet COST = new SOCResourceSet(1, 0, 0, 0, 1, 0);

    /**
     * Make a new road
     *
     * @param pl  player who owns the road
     * @param edge  road's edge coordinate
     * @param board  board if known; otherwise will extract from {@code pl}
     * @throws IllegalArgumentException  if {@code pl} null, or board null and {@code pl.board} also null
     */
    public SOCRoad(final SOCPlayer pl, final int edge, final SOCBoard board)
        throws IllegalArgumentException
    {
        super(pl, SOCPlayingPiece.ROAD, edge, board);
    }

}
