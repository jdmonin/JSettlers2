/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2014,2017,2019-2021 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
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
import java.util.Arrays;


/**
 * This class represents a trade offer in Settlers of Catan
 * and/or is part of a network message about trades.
 */
@SuppressWarnings("serial")
public class SOCTradeOffer implements Serializable, Cloneable
{
    final String game;
    final SOCResourceSet give;
    final SOCResourceSet get;

    /**
     * Player number making this offer; see {@link #getFrom()}.
     */
    final int from;

    /**
     * Player numbers this offer is made to; see {@link #getTo()} for details.
     * Replies are tracked in {@link #waitingReply}.
     */
    final boolean[] to;

    /**
     * Player numbers this offer is made to, who haven't replied yet (reject or accept);
     * see {@link #getWaitingReply()} for details.
     * @since 2.0.00
     */
    final boolean[] waitingReply;

    /**
     * The constructor for a SOCTradeOffer
     *
     * @param  game  the name of the game in which this offer was made
     * @param  from  the number of the player making the offer
     * @param  to    a boolean array with the set of player numbers this offer is made to;
     *               see {@link #getTo()} for details. Not null.
     * @param  give  the set of resources being given (offered) by the {@code from} player; not null
     * @param  get   the set of resources being asked for; not null
     */
    public SOCTradeOffer(String game, int from, boolean[] to, SOCResourceSet give, SOCResourceSet get)
    {
        this.game = game;
        this.from = from;
        this.to = to;
        waitingReply = Arrays.copyOf(to, to.length);
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
        to = Arrays.copyOf(offer.to, maxPlayers);
        waitingReply = Arrays.copyOf(offer.waitingReply, maxPlayers);
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
     * Player number making this offer.
     * @return the number of the player that made the offer
     */
    public int getFrom()
    {
        return from;
    }

    /**
     * Get the set of player numbers this offer is made to:
     * An array with {@link SOCGame#maxPlayers} elements, where
     * {@code to[pn]} is true for each player number to whom the offer was made.
     * For reply status, see {@link #getWaitingReply()} or {@link #isWaitingReplyFrom(int)}.
     * @return the array showing to which player numbers this offer was made
     */
    public boolean[] getTo()
    {
        return to;
    }

    /**
     * Get the set of player numbers this offer is made to,
     * who haven't replied yet (reject or accept). A subset of {@link #getTo()}.
     *<P>
     * For individual players, see {@link #isWaitingReplyFrom(int)}.
     *
     * @return the boolean array representing player numbers to whom this offer was made:
     *    An array with {@link SOCGame#maxPlayers} elements, set true for
     *    the {@link SOCPlayer#getPlayerNumber()} of each player to whom
     *    the offer was made but no reject/accept reply has been received.
     * @since 2.0.00
     */
    public boolean[] getWaitingReply()
    {
        return waitingReply;
    }

    /**
     * Is offer still waiting for a reply from this player?
     *<P>
     * For all players, see {@link #getWaitingReply()}.
     *<P>
     *<B>Threads:</B> Not synchronized; caller must synchronize if needed.
     *
     * @param pn  Player number to check
     * @return  True if waiting for a reply from {@code pn}
     * @since 2.0.00
     */
    public boolean isWaitingReplyFrom(final int pn)
    {
        return (pn >= 0) && (pn < waitingReply.length) && waitingReply[pn];
    }

    /**
     * Clear this player's "waiting for reply" flag within {@link #getWaitingReply()}.
     *<P>
     *<B>Threads:</B> Not synchronized; caller must synchronize if needed.
     *
     * @param pn  Player number to clear flag
     * @throws IllegalArgumentException if <tt>pn &lt; 0</tt> or <tt>&gt;= {@link SOCGame#MAXPLAYERS}</tt>
     * @since 2.0.00
     */
    public void clearWaitingReplyFrom(final int pn)
        throws IllegalArgumentException
    {
        if ((pn < 0) || (pn >= SOCGame.MAXPLAYERS))
            throw new IllegalArgumentException("pn: " + pn);

        if (pn < waitingReply.length)
            waitingReply[pn] = false;
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
     * Get a readable representation of this data for debugging;
     * omits {@link #getWaitingReply()} for brevity.
     * @return a human readable string of data, of the form:
     *     <tt>game=gname|from=pn|to=true,false,true,false|give={SOCResourceSet.toString}|get={SOCResourceSet.toString}</tt>
     * @see #toString(boolean)
     */
    @Override
    public String toString()
    {
        return toString(false);
    }

    /**
     * Get a readable representation of this data for debugging, optionally without game field;
     * omits {@link #getWaitingReply()} for brevity.
     * @param omitGame  If true, omit <tt>"game="</tt> field
     * @return a human readable string of data, of the form:
     *     [<tt>game=gname|</tt>]<tt>from=pn|to=true,false,true,false|give={SOCResourceSet.toString}|get={SOCResourceSet.toString}</tt>
     * @see #toString()
     * @since 2.5.00
     */
    public String toString(final boolean omitGame)
    {
        StringBuilder str = new StringBuilder();
        if (! omitGame)
            str.append("game=" + game + '|');
        str.append("from=" + from + "|to=" + to[0]);
        for (int pn = 1; pn < to.length; ++pn)
        {
            str.append(',');
            str.append(to[pn]);
        }
        str.append("|give=" + give + "|get=" + get);

        return str.toString();
    }

    /**
     * Compare this trade offer to another object or trade offer.
     * @return true if {@code o} is a {@link SOCTradeOffer}
     *     with the same To, From, Give and Get field contents.
     *     Ignores {@link #getGame()} field.
     * @since 2.5.00
     */
    @Override
    public boolean equals(Object o)
    {
        if (o instanceof SOCTradeOffer)
        {
            SOCTradeOffer offer = (SOCTradeOffer) o;
            for (int i = 0; i < to.length; i++)
                if (to[i] != offer.to[i])
                    return false;

            return (from == offer.from
                && give.equals(offer.give)
                && get.equals(offer.get));
        } else {
            return false;
        }
    }

    /**
     * @return a hashCode for this trade offer based on field contents,
     *     ignoring {@link #getGame()} because {@link #equals(Object)} does
     * @since 2.5.00
     */
    @Override
    public int hashCode()
    {
        return give.hashCode() ^ get.hashCode()
            ^ from ^ Arrays.hashCode(to) ^ Arrays.hashCode(waitingReply);
    }

}
