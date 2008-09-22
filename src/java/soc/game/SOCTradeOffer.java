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

import java.io.Serializable;


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
     * The constructor for a SOCTradeOffer
     *
     * @param  game  the name of the game in which this offer was made
     * @param  from  the number of the player making the offer
     * @param  to    a boolean array where 'true' means that the offer
     *               is being made to the player with the same number as
     *               the index of the 'true'
     * @param  give  the set of resources being given
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
        to = new boolean[SOCGame.MAXPLAYERS];

        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
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
     * @return the boolean array representing to whom this offer was made
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

        for (int i = 1; i < SOCGame.MAXPLAYERS; i++)
        {
            str += ("," + to[i]);
        }

        str += ("|give=" + give + "|get=" + get);

        return str;
    }
}
