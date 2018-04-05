/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2014,2017-2018 Jeremy D Monin <jeremy@nand.net>
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

import java.io.Serializable;
import java.util.List;


/**
 * This class represents a trade offer in Settlers of Catan
 */
@SuppressWarnings("serial")
public class SOCTradeOffer implements Serializable, Cloneable
{
    String game;
    SOCResourceSet give;
    SOCResourceSet get;
    int from;
    boolean[] to;

    /**
     * Make a boolean array of player numbers from a list.
     * @param pnList  List of player numbers; should be in range 0-3 or 0-5 inclusive,
     *     for a 4-player or 6-player game. Element values outside that range are ignored.
     * @return Boolean array whose element indexes from {@code pnList} are {@code true}
     *    and all others are {@code false}, or {@code null} if {@code pnList}
     *    was null, empty, or had no values &gt;= 0
     * @since 3.0.00
     */
    public static final boolean[] makePNArray(final List<Integer> pnList)
    {
        if (pnList == null)
            return null;

        int max = -1;
        for (final int i : pnList)
            if (i > max)
                max = i;
        if (max == -1)
            return null;

        final int L = (max >= 4) ? 6 : 4;
        boolean[] pnArr = new boolean[L];
        for (final int i : pnList)
            if ((i >= 0) && (i < L))
                pnArr[i] = true;

        return pnArr;
    }

    /**
     * The constructor for a SOCTradeOffer.
     * To use a list of player numbers instead of a boolean array, use {@link #makePNArray(List)} to convert it.
     *
     * @param  game  the name of the game in which this offer was made
     * @param  from  the number of the player making the offer
     * @param  to    a boolean array with the set of player numbers this offer is made to;
     *               see {@link #getTo()} for details.
     * @param  give  the set of resources being given (offered) by the {@code from} player
     * @param  get   the set of resources being asked for
     */
    public SOCTradeOffer(String game, int from, boolean[] to, SOCResourceSet give, SOCResourceSet get)
    {
        this.game = game;
        this.from = from;
        this.to = to;
        this.give = give;
        this.get = get;
    }

    /**
     * make a copy of this offer
     *
     * @param offer   the trade offer to copy
     */
    public SOCTradeOffer(SOCTradeOffer offer)
    {
        game = offer.game;
        from = offer.from;
        final int maxPlayers = offer.to.length;
        to = new boolean[maxPlayers];

        for (int i = 0; i < maxPlayers; i++)
        {
            to[i] = offer.to[i];
        }

        give = offer.give.copy();
        get = offer.get.copy();
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the number of the player that made the offer
     */
    public int getFrom()
    {
        return from;
    }

    /**
     * Get the set of player numbers this offer is made to.
     * @return the boolean array representing player numbers to whom this offer was made:
     *    An array with {@link SOCGame#maxPlayers} elements, set true for
     *    the {@link SOCPlayer#getPlayerNumber()} of each player to whom
     *    the offer was made.
     */
    public boolean[] getTo()
    {
        return to;
    }

    /**
     * @return the set of resources offered
     */
    public SOCResourceSet getGiveSet()
    {
        return give;
    }

    /**
     * @return the set of resources wanted in exchange
     */
    public SOCResourceSet getGetSet()
    {
        return get;
    }

    /**
     * @return a human readable string of data
     */
    public String toString()
    {
        String str = "game=" + game + "|from=" + from + "|to=" + to[0];

        for (int i = 1; i < to.length; i++)
        {
            str += ("," + to[i]);
        }

        str += ("|give=" + give + "|get=" + get);

        return str;
    }
}
