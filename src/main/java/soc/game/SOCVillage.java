/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012,2019-2020 Jeremy D Monin <jeremy@nand.net>
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
import java.util.List;

/**
 * A village playing piece, used on the large sea board ({@link SOCBoardLarge}) with some scenarios.
 * Villages are in a game only if scenario option {@link SOCGameOptionSet#K_SC_CLVI} is set.
 *<P>
 * Villages belong to the game board, not to any player, and new villages cannot be built after the game starts.
 * Trying to call {@link SOCPlayingPiece#getPlayer() village.getPlayer()} will throw an exception.
 *
 * @see SOCScenario#SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCVillage extends SOCPlayingPiece
{
    private static final long serialVersionUID = 2000L;

    /**
     * Default starting amount of cloth for the board general supply (10).
     * @see #STARTING_CLOTH
     */
    public static final int STARTING_GENERAL_CLOTH = 10;

    /**
     * Default starting amount of cloth for a village (5).
     * @see #STARTING_GENERAL_CLOTH
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
     * Game action: Distribute cloth to players from this village and
     * (if needed) from the board general supply.
     * Each player from {@link #addTradingPlayer(SOCPlayer)} gets at most 1 cloth.
     * If the village has no cloth remaining, does nothing (returns null).
     * Calls {@link #takeCloth(int)}, {@link SOCBoardLarge#takeCloth(int)}, {@link SOCPlayer#setCloth(int)}, etc.
     *<P>
     * If the village has some cloth, but not enough to distribute to all trading players
     * (including from the general supply), then start with the current player if they're
     * trading with this village. Then, the players with first pick are those who
     * established trade first.
     *
     * @param game  Game with this village
     * @param rollRes  {@code game}'s roll results, to add cloth distribution into:
     *     Updates {@link SOCGame.RollResult#cloth}, {@link SOCGame.RollResult#clothVillages} fields
     * @return  True if this village had cloth and trading partners to distribute cloth to
     *     (from the village or general supply)
     */
    public boolean distributeCloth(final SOCGame game, final SOCGame.RollResult rollRes)
    {
        if ((numCloth == 0) || (traders == null) || traders.isEmpty())
            return false;

        if (rollRes.clothVillages == null)
            rollRes.clothVillages = new ArrayList<SOCVillage>();
        rollRes.clothVillages.add(this);

        final int[] results;
        if (rollRes.cloth != null)
        {
            results = rollRes.cloth;
        } else {
            results = new int[1 + game.maxPlayers];
            rollRes.cloth = results;
        }

        final int n = traders.size();
        final int nFromHere = takeCloth(n);  // will be > 0 because numCloth != 0
        final int nFromGeneral;
        if (nFromHere < n)
        {
            nFromGeneral = ((SOCBoardLarge) board).takeCloth(n - nFromHere);
            results[0] += nFromGeneral;
        } else {
            nFromGeneral = 0;
        }

        // In case not enough to distribute, track amount remaining and prioritize:
        // First, the current player, if trading with this village.
        // After current, go through traders list (chronological order).

        int remain = nFromHere + nFromGeneral;

        final int cpn = game.getCurrentPlayerNumber();
        if (cpn != -1)
        {
            SOCPlayer currPl = game.getPlayer(cpn);
            if (traders.contains(currPl))
            {
                currPl.setCloth(1 + currPl.getCloth());
                ++results[1 + cpn];
                --remain;
            }
        }

        for (int i = 0; (i < n) && (remain > 0); ++i)
        {
            SOCPlayer pl = traders.get(i);
            final int pn = pl.getPlayerNumber();
            if (pn == cpn)
                continue;  // already gave

            pl.setCloth(1 + pl.getCloth());
            ++results[1 + pn];
            --remain;
        }

        return true;
    }

}
