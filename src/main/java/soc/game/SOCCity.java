/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2011,2014,2017 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import java.util.Vector;


/**
 * A city playing piece
 */
@SuppressWarnings("serial")
public class SOCCity extends SOCPlayingPiece
{
    /**
     * The set of resources a player needs to build a {@link SOCCity city}.
     *<P>
     * Before v2.0.00, this field was {@code SOCGame.CITY_SET}.
     * @see SOCPlayingPiece#getResourcesToBuild(int)
     * @since 2.0.00
     */
    public static final SOCResourceSet COST = new SOCResourceSet(0, 3, 0, 2, 0, 0);

    /**
     * Make a new city
     *
     * @param player  player who owns the city
     * @param co  coordinates
     * @param board  board if known; otherwise will extract from <tt>pl</tt>
     * @throws IllegalArgumentException  if <tt>pl</tt> null, or board null and <tt>pl.board</tt> also null
     */
    public SOCCity(SOCPlayer player, int co, SOCBoard board)
        throws IllegalArgumentException
    {
        super(SOCPlayingPiece.CITY, player, co, board);
    }

    /**
     * @return the hexes touching this city, same format as {@link SOCBoard#getAdjacentHexesToNode(int)}
     */
    public Vector<Integer> getAdjacentHexes()
    {
        return board.getAdjacentHexesToNode(coord);
    }

}
