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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A village playing piece, used on the large sea board ({@link SOCBoardLarge}) with some scenarios.
 * Villages are in a game only if scenario option {@link SOCGameOption#K_SC_CLVI} is set.
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
     * Default starting amount of cloth for a village (5).
     */
    public static final int STARTING_CLOTH = 5;

    /**
     * Village's dice number, for giving cloth to players
     * who've established a trade route to here.
     * To simplify cloth distribution when remaining cloth
     * is low, no other village should share the same dice number.
     */
    public final int diceNum;

    /**
     * How many cloth does this village have?
     */
    private int numCloth;

    /**
     * Players who have established trade with this village.
     */
    private List<SOCPlayer> traders;

    // Temporary with defaults until dice, cloth sent to client
    public SOCVillage(final int node, SOCBoard board)
        throws IllegalArgumentException
    {
        this(node, 0, STARTING_CLOTH, board);
    }

    /**
     * Make a new village, which has a certain amount of cloth.
     *
     * @param node  node coordinate of village
     * @param dice  dice number for giving cloth to players.
     *              To simplify cloth distribution when remaining cloth
     *              is low, no other village should share the same dice number.
     * @param cloth  number of pieces of cloth, such as {@link #STARTING_CLOTH}
     * @param board  board
     * @throws IllegalArgumentException  if board null
     */
    public SOCVillage(final int node, final int dice, final int cloth, SOCBoard board)
        throws IllegalArgumentException
    {
        super(SOCPlayingPiece.VILLAGE, node, board);
        diceNum = dice;
        numCloth = cloth;
    }

    /**
     * Get how many cloth this village currently has.
     * @see #takeCloth(int)
     */
    public int getCloth()
    {
        return numCloth;
    }

    /**
     * Set how many cloth this village currently has.
     * For use at client based on messages from server.
     * @param numCloth  Number of cloth
     */
    public void setCloth(final int numCloth)
    {
        this.numCloth = numCloth;
    }

    /**
     * Take this many cloth, if available, from this village.
     * Cloth should be given to players, and is worth 1 VP for every 2 cloth they have.
     * @param numTake  Number of cloth to try and take
     * @return  Number of cloth actually taken, a number from 0 to <tt>numTake</tt>.
     *          If &gt; 0 but &lt; <tt>numTake</tt>, the rest should be taken from the
     *          board's "general supply" of cloth.
     * @see #getCloth()
     * @see SOCBoardLarge#takeCloth(int)
     */
    public int takeCloth(int numTake)
    {
        if (numTake > numCloth)
        {
            numTake = numCloth;
            numCloth = 0;
        } else {
            numCloth -= numTake;
        }
        return numTake;
    }

    /**
     * Add this player to the list of trading players.
     * If the village has some {@link #getCloth()} remaining,
     * gives <tt>pl</tt> 1 cloth now (at server only).
     * @param pl  Player who's just established trade with this village
     * @return   True if <tt>pl</tt> received 1 cloth
     */
    public boolean addTradingPlayer(SOCPlayer pl)
    {
        if (traders == null)
            traders = new ArrayList<SOCPlayer>();
        else if (traders.contains(pl))
            return false;

        traders.add(pl);
        if ((numCloth > 0) && pl.getGame().isAtServer)
        {
            --numCloth;
            pl.setCloth(1 + pl.getCloth());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Game action: Distribute cloth to players from this village.
     * Each player from {@link #addTradingPlayer(SOCPlayer)} gets at most 1 cloth.
     * If the village has no cloth remaining, does nothing (returns null).
     * Calls {@link #takeCloth(int)}, {@link SOCBoardLarge#takeCloth(int)}, {@link SOCPlayer#setCloth(int)}, etc.
     * @param game  Game with this village
     * @return  null, or results as an array:
     *   [ Cloth amount taken from general supply, Matching village node coordinate,
     *     Cloth amount given to player 0, to player 1, ... to player n ].
     */
    public int[] distributeCloth(SOCGame game)
    {
        if ((numCloth == 0) || (traders == null) || traders.isEmpty())
            return null;

        int[] results = new int[game.maxPlayers + 2];
        results[1] = coord;

        final int n = traders.size();
        final int nFromHere = takeCloth(n);
        final int nFromGeneral;
        if (nFromHere < n)
        {
            nFromGeneral = ((SOCBoardLarge) board).takeCloth(n - nFromHere);
            results[0] = nFromGeneral;
        } else {
            nFromGeneral = 0;
        }

        // Mark the established trading players
        for (final SOCPlayer pl : traders)
            results[2 + pl.getPlayerNumber()] = 1;

        if (nFromHere + nFromGeneral < n)
        {
            // TODO if not enough to distribute, keep only some per-player results:
            // Track amount remaining in "distribute" for-loop, start that loop @ current player. 
        }

        // Distribute
        for (int pn = 0; pn < game.maxPlayers; ++pn)
        {
            if (results[2 + pn] != 0)
            {
                SOCPlayer pl = game.getPlayer(pn);
                pl.setCloth(1 + pl.getCloth());
            }
        }

        return results;
    }
}
