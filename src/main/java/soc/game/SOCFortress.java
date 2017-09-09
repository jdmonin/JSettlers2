/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012-2013 Jeremy D Monin <jeremy@nand.net>
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
 * A fortress playing piece, used on the large sea board ({@link SOCBoardLarge}) with some scenarios.
 * Fortresses are in a game only if scenario game option {@link SOCGameOption#K_SC_PIRI _SC_PIRI} is set.
 *<P>
 * A player "owns" one fortress, but doesn't control it until after they have conquered the pirates
 * there and its {@link #getStrength()} is 0.  At that time it becomes a {@link SOCSettlement}.
 * New fortresses cannot be built after the game starts.
 * So, {@link SOCGame#putPiece(SOCPlayingPiece) game.putPiece(SOCFortress)} assumes initial placement.
 *<P>
 * For details of fortress recapture / conquest, see {@link SOCGame#attackPirateFortress(SOCShip)}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCFortress extends SOCPlayingPiece
{
    private static final long serialVersionUID = 2000L;

    /**
     * Default starting strength for a fortress (3).
     */
    public static final int STARTING_STRENGTH = 3;

    /**
     * What is this fortress' strength?
     * Begins at {@link #STARTING_STRENGTH}.
     */
    private int strength;

    /**
     * Make a new fortress, with strength {@link #STARTING_STRENGTH}.
     *
     * @param player  Player who will own the fortress
     * @param node  node coordinate of fortress
     * @param board  board
     * @throws IllegalArgumentException  if board null
     */
    public SOCFortress(SOCPlayer player, final int node, SOCBoard board)
        throws IllegalArgumentException
    {
        super(SOCPlayingPiece.FORTRESS, player, node, board);
        strength = STARTING_STRENGTH;
    }

    /**
     * Get the current strength of this fortress.
     */
    public int getStrength()
    {
        return strength;
    }

    /**
     * Set the current strength of this fortress.
     * For use at client based on messages from server.
     * @param strength  New strength
     */
    public void setStrength(final int strength)
    {
        this.strength = strength;
    }

}
