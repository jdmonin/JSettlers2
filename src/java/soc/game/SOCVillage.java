/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012 Jeremy D Monin <jeremy@nand.net>
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
 * A village playing piece, used on the large sea board ({@link SOCBoardLarge}) with some scenarios.
 *<P>
 * Villages belong to the game board, not to any player, and new villages cannot be built after the game starts.
 * Trying to call {@link SOCPlayingPiece#getPlayer() village.getPlayer()} will throw an exception.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCVillage extends SOCPlayingPiece
{
    private static final long serialVersionUID = 2000L;

    /**
     * Make a new village.
     *
     * @param node  node coordinate of village
     * @param board  board
     * @throws IllegalArgumentException  if board null
     */
    public SOCVillage(final int node, SOCBoard board)
        throws IllegalArgumentException
    {
        super(SOCPlayingPiece.VILLAGE, node, board);
    }

}
