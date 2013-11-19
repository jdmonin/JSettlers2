/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007,2009,2012-2013 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Skylar Bolton <iiagrer@gmail.com>
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * This represents a collection of development cards.
 * Players can have 0, 1, or more of any card type.
 * Each card's current state can be New to be played soon; Playable; or Kept in hand
 * until the end of the game (Victory Point cards, which are never New).
 *<P>
 * For use in loops, age constants and card state constants are contiguous:<BR>
 * {@link #OLD} == 0, {@link #NEW} == 1.<BR>
 * {@link #NEW} == 1, {@link #PLAYABLE} == 2, {@link #KEPT} == 3.
 */
public class SOCDevCardSet implements Serializable, Cloneable
{
    /**
     * Age constant: An old card can either be played this turn (state {@link #PLAYABLE})
     * or is kept in hand until the end of the game (state {@link #KEPT}) such as a Victory Point card.
     */
    public static final int OLD = 0;

    /**
     * Age constant, card state constant: Recently bought card, playable next turn.<BR>
     * Other possible age is {@link #OLD}.<BR>
     * Other possible states are {@link #PLAYABLE} and {@link #KEPT}.
     */
    public static final int NEW = 1;

    /**
     * Card state constant: Playable this turn (not {@link #NEW} or {@link #KEPT}).
     * @since 2.0.00
     */
    public static final int PLAYABLE = 2;

    /**
     * Card state constant: Kept in hand until end of game (not {@link #PLAYABLE}, was never {@link #NEW}).
     * @since 2.0.00
     */
    public static final int KEPT = 3;

    /**
     * Current set of cards with 1 of 3 possible states (new and not playable yet; playable; kept until end of game).
     * If a card's type has {@link SOCDevCard#isVPCard(cType)}, it is placed in {@code kept}, never in {@code news}.
     *<P>
     * This implementation assumes players will have only a few cards at a time, so linear searching for a card type
     * is acceptable.
     * @since 2.0.00
     */
    private final List<SOCDevCard> news, playables, kept;

    // Representation before v2.0.00 refactoring:
    // private int[][] devCards;  // [new or old][cType]

    /**
     * Make an empty development card set
     */
    public SOCDevCardSet()
    {
        news = new ArrayList<SOCDevCard>();
        playables = new ArrayList<SOCDevCard>();
        kept = new ArrayList<SOCDevCard>();
    }

    /**
     * Make a copy of a development card set.
     * The copy is deep: new copies are instantiated of all contained {@link SOCDevCard} objects.
     *
     * @param set  the dev card set to copy
     */
    public SOCDevCardSet(SOCDevCardSet set)
    {
        this();
        for (SOCDevCard c : set.news)
            news.add(new SOCDevCard(c));
        for (SOCDevCard c : set.playables)
            playables.add(new SOCDevCard(c));
        for (SOCDevCard c : set.kept)
            kept.add(new SOCDevCard(c));
    }

    /**
     * set the total number of dev cards to zero
     */
    public void clear()
    {
        news.clear();
        playables.clear();
        kept.clear();
    }

    /**
     * Get the cards, if any, having this state.
     * Please treat the returned list as read-only.
     * @param cState  Card state: {@link #NEW}, {@link #PLAYABLE} or {@link #KEPT}
     * @return Cards or an empty list
     * @throws IllegalArgumentException if {@code cstate} isn't one of the 3 card states
     * @since 2.0.00
     * @see #hasPlayable(int)
     */
    public List<SOCDevCard> getByState(final int cState)
        throws IllegalArgumentException
    {
        switch (cState)
        {
        case NEW:      return news;
        case PLAYABLE: return playables;
        case KEPT:     return kept;
        default:       throw new IllegalArgumentException("Unknown cState: " + cState);
        }
    }

    /**
     * Does this set contain 1 or more playable cards of this type?
     * (Playable this turn: Not new, not already played and then kept.)
     * @param ctype  Type of development card from {@link SOCDevCardConstants}
     * @return  True if has at least 1 playable card of this type
     * @since 2.0.00
     * @see #getByState(int)
     */
    public boolean hasPlayable(final int ctype)
    {
        for (SOCDevCard c : playables)
        {
            if (c.ctype == ctype)
                return true;
        }

        return false;
    }

    /**
     * Get the amount of a card type (old and new) in the set.
     * @param ctype  Type of development card as described
     *        in {@link SOCDevCardConstants};
     *        at least {@link SOCDevCardConstants#MIN}
     *        and less than {@link SOCDevCardConstants#MAXPLUSONE}
     * @return  the number of new + of old cards of this type
     * @see #getAmount(int, int)
     * @since 2.0.00
     */
    public int getAmount(final int ctype)
    {
        int amt = 0;

        for (SOCDevCard c : news)
            if (c.ctype == ctype)
                ++amt;
        for (SOCDevCard c : playables)
            if (c.ctype == ctype)
                ++amt;
        for (SOCDevCard c : kept)
            if (c.ctype == ctype)
                ++amt;

        return amt;
    }

    /**
     * @return the number of a kind of development card
     *
     * @param age  either {@link #OLD} or {@link #NEW}
     * @param ctype
     *        the type of development card as described
     *        in {@link SOCDevCardConstants};
     *        at least {@link SOCDevCardConstants#MIN}
     *        and less than {@link SOCDevCardConstants#MAXPLUSONE}
     * @see #getAmount(int)
     * @see #hasPlayable(int)
     * @see #getByState(int)
     */
    public int getAmount(int age, int ctype)
    {
        final List<SOCDevCard> clist;
        if (SOCDevCard.isVPCard(ctype))
            clist = kept;
        else
            clist = (age == NEW) ? news : playables;

        int amt = 0;
        for (SOCDevCard c : clist)
            if (c.ctype == ctype)
                ++amt;

        return amt;
    }

    /**
     * @return the total number of development cards
     * @see #getNumUnplayed()
     * @see #getNumVPCards()
     */
    public int getTotal()
    {
        return news.size() + playables.size() + kept.size();
    }

    /**
     * add an amount to a type of card
     *
     * @param age   either {@link #OLD} or {@link #NEW}
     * @param ctype the type of development card, at least
     *              {@link SOCDevCardConstants#MIN} and less than {@link SOCDevCardConstants#MAXPLUSONE}
     * @param amt   the amount
     */
    public void add(int amt, final int age, final int ctype)
    {
        final boolean isNew;
        final List<SOCDevCard> clist;
        if (SOCDevCard.isVPCard(ctype))
        {
            isNew = false;
            clist = kept;
        } else {
            isNew = (age == NEW);
            clist = (isNew) ? news : playables;
        }

        while (amt > 0)
        {
            clist.add(new SOCDevCard(ctype, isNew));
            --amt;
        }
    }

    /**
     * Subtract one card of a type from the set.
     * If that type isn't available, subtract from {@link SOCDevCardConstants#UNKNOWN} instead.
     *
     * @param age   either {@link #OLD} or {@link #NEW}
     * @param ctype the type of development card, at least
     *              {@link SOCDevCardConstants#MIN} and less than {@link SOCDevCardConstants#MAXPLUSONE}
     */
    public void subtract(final int age, final int ctype)
    {
        final List<SOCDevCard> clist;
        if (SOCDevCard.isVPCard(ctype))
            clist = kept;
        else
            clist = (age == NEW) ? news : playables;

        final Iterator<SOCDevCard> cIter = clist.iterator();
        while (cIter.hasNext())
        {
            SOCDevCard c = cIter.next();
            if (c.ctype == ctype)
            {
                cIter.remove();
                return;  // <--- Early return: found and removed ---
            }
        }

        // not found
        if (ctype != SOCDevCardConstants.UNKNOWN)
            subtract(age, SOCDevCardConstants.UNKNOWN);
    }

    /**
     * Get the number of Victory Point cards in this set:
     * All cards returning true for {@link SOCDevCard#isVPCard()}.
     * @return the number of victory point cards in
     *         this set
     * @see #getNumUnplayed()
     * @see #getTotal()
     * @see #getByState(int)
     * @see SOCDevCard#isVPCard(int)
     */
    public int getNumVPCards()
    {
        int sum = 0;

        // VP cards are never new, don't check the news list

        for (SOCDevCard c : playables)
            if (c.isVPCard())
                ++sum;
        for (SOCDevCard c : kept)
            if (c.isVPCard())
                ++sum;

        return sum;
    }

    /**
     * Some card types stay in your hand after being played.
     * Count only the unplayed ones (old or new); kept VP cards are skipped.
     *
     * @return the number of unplayed cards in this set
     * @see #getNumVPCards()
     * @see #getTotal()
     * @see #getByState(int)
     */
    public int getNumUnplayed()
    {
        return news.size() + playables.size();
    }

    /**
     * Change all the new cards to old ones.
     * Cards' state {@link #NEW} becomes {@link #PLAYABLE}.
     */
    public void newToOld()
    {
        for (SOCDevCard c : news)
        {
            c.newToOld();
            playables.add(c);
        }

        news.clear();
    }

}
