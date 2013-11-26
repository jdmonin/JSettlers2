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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * This represents a collection of development cards, and occasional scenario-specific items.
 * Players can have 0, 1, or more of any card type or item type.
 * Each item's current state can be New to be played soon; Playable; or Kept in hand
 * until the end of the game (Victory Point cards, which are never New).
 *<P>
 * For use in loops, age constants and inventory-item state constant ranges are each contiguous:<BR>
 * {@link #OLD} == 0, {@link #NEW} == 1.<BR>
 * {@link #NEW} == 1, {@link #PLAYABLE} == 2, {@link #KEPT} == 3.
 *<P>
 * Before v2.0.00, this class was named {@code SOCDevCardSet}.
 */
public class SOCInventory
{
    /**
     * Age constant: An old item can either be played this turn (state {@link #PLAYABLE})
     * or is kept in hand until the end of the game (state {@link #KEPT}) such as a Victory Point card.
     */
    public static final int OLD = 0;

    /**
     * Age constant, item state constant: Recently bought card, playable next turn.<BR>
     * Other possible age is {@link #OLD}.<BR>
     * Other possible states are {@link #PLAYABLE} and {@link #KEPT}.
     */
    public static final int NEW = 1;

    /**
     * Item state constant: Playable this turn (not {@link #NEW} or {@link #KEPT}).
     * @since 2.0.00
     */
    public static final int PLAYABLE = 2;

    /**
     * Item state constant: Kept in hand until end of game (not {@link #PLAYABLE}, was never {@link #NEW}).
     * @since 2.0.00
     */
    public static final int KEPT = 3;

    /**
     * Current set of the items having 1 of 3 possible states (New and not playable yet; Playable; Kept until end of game).
     * If an item's type has {@link SOCDevCard#isVPCard(cType)}, it is placed in {@code kept}, never in {@code news}.
     *<P>
     * This implementation assumes players will have only a few cards or items at a time, so linear searching for a
     * {@link SOCInventoryItem#itype} type is acceptable.
     * @since 2.0.00
     */
    private final List<SOCInventoryItem> news, playables, kept;

    // Representation before v2.0.00 refactoring:
    // private int[][] devCards;  // [new or old][cType]

    /**
     * Make an empty dev card and inventory item set.
     */
    public SOCInventory()
    {
        news = new ArrayList<SOCInventoryItem>();
        playables = new ArrayList<SOCInventoryItem>();
        kept = new ArrayList<SOCInventoryItem>();
    }

    /**
     * Make a copy of a dev card and inventory item set.
     * The copy is deep: all contained {@link SOCDevCard}/{@link SOCInventoryItem} objects are cloned.
     *
     * @param set  the inventory set to copy
     * @throws CloneNotSupportedException  Should not occur; {@link SOCInventoryItem}s should be Cloneable
     */
    public SOCInventory(SOCInventory set)
        throws CloneNotSupportedException
    {
        this();
        for (SOCInventoryItem c : set.news)
            news.add(c.clone());
        for (SOCInventoryItem c : set.playables)
            playables.add(c.clone());
        for (SOCInventoryItem c : set.kept)
            kept.add(c.clone());
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
     * Get the cards and items, if any, having this state.
     * Please treat the returned list as read-only.
     * @param cState  Card/item state: {@link #NEW}, {@link #PLAYABLE} or {@link #KEPT}
     * @return Cards and items, or an empty list
     * @throws IllegalArgumentException if {@code cState} isn't one of the 3 item states
     * @since 2.0.00
     * @see #hasPlayable(int)
     */
    public List<SOCInventoryItem> getByState(final int cState)
        throws IllegalArgumentException
    {
        switch (cState)
        {
        case NEW:      return news;
        case PLAYABLE: return playables;
        case KEPT:     return kept;
        default:       throw new IllegalArgumentException("Unknown state: " + cState);
        }
    }

    /**
     * Does this set contain 1 or more playable cards or items of this type?
     * (Playable this turn: Not new, not already played and then kept.)
     * @param ctype  Type of development card from {@link SOCDevCardConstants}, or item type
     *            from {@link SOCInventoryItem#itype}
     * @return  True if has at least 1 playable card of this type
     * @since 2.0.00
     * @see #getByState(int)
     */
    public boolean hasPlayable(final int ctype)
    {
        for (SOCInventoryItem c : playables)
        {
            if (c.itype == ctype)
                return true;
        }

        return false;
    }

    /**
     * Get the amount of a dev card type or special item in the set.
     * @param ctype  Type of development card or item as described
     *        in {@link SOCDevCardConstants} and {@link SOCInventoryItem#itype}
     * @return  the number of new + of old cards/items of this type
     * @see #getAmount(int, int)
     * @see #getSpecialItemAmount(int, int)
     * @since 2.0.00
     */
    public int getAmount(final int ctype)
    {
        int amt = 0;

        for (SOCInventoryItem c : news)
            if (c.itype == ctype)
                ++amt;
        for (SOCInventoryItem c : playables)
            if (c.itype == ctype)
                ++amt;
        for (SOCInventoryItem c : kept)
            if (c.itype == ctype)
                ++amt;

        return amt;
    }

    /**
     * Get the amount of a dev card type of certain age in the set.
     * Does not count other types of inventory item, only {@link SOCDevCard}.
     *
     * @param age  either {@link #OLD} or {@link #NEW}
     * @param ctype
     *        the type of development card as described in {@link SOCDevCardConstants}
     * @return the amount of a kind of development card
     * @see #getAmount(int)
     * @see #getAmountByState(int, int)
     * @see #hasPlayable(int)
     * @see #getByState(int)
     */
    public int getAmount(int age, int ctype)
    {
        final List<SOCInventoryItem> clist;
        if (SOCDevCard.isVPCard(ctype))
            clist = kept;
        else
            clist = (age == NEW) ? news : playables;

        int amt = 0;
        for (SOCInventoryItem c : clist)
            if ((c instanceof SOCDevCard) && (c.itype == ctype))
                ++amt;

        return amt;
    }

    /**
     * Get the amount of dev cards or special items by state and type.
     * @param state  {@link #NEW}, {@link #PLAYABLE}, or {@link #KEPT}
     * @param itype  Item type code, from {@link SOCInventoryItem#itype} or {@link SOCDevCardConstants}
     * @return  the number of special items or dev cards of this state and type
     * @throws IllegalArgumentException if {@code state} isn't one of the 3 item states
     * @see #getAmount(int, int)
     * @since 2.0.00
     */
    public int getAmountByState(final int state, final int itype)
        throws IllegalArgumentException
    {
        final List<SOCInventoryItem> ilist;
        switch (state)
        {
        case NEW:      ilist = news;      break;
        case PLAYABLE: ilist = playables; break;
        case KEPT:     ilist = kept;      break;
        default:       throw new IllegalArgumentException("Unknown state: " + state);
        }

        int amt = 0;

        for (SOCInventoryItem c : ilist)
            if (c.itype == itype)
                ++amt;

        return amt;
    }

    /**
     * @return the total number of development cards and special items
     * @see #getNumUnplayed()
     * @see #getNumVPItems()
     */
    public int getTotal()
    {
        return news.size() + playables.size() + kept.size();
    }

    /**
     * Add a special item or dev card to this set.
     *<P>
     * The item's {@link SOCInventoryItem#isPlayable()} or/and {@link SOCInventoryItem#isKept()}
     * will be called to determine its initial state:
     *<UL>
     * <LI> {@link SOCInventoryItem#isPlayable() item.isPlayable()} -> {@link #PLAYABLE}
     * <LI> Not playable, {@link SOCInventoryItem#isKept() item.isKept()} -> {@link #KEPT}
     * <LI> Not playable, not kept -> {@link #NEW}
     *</UL>
     * @param item  The special item or dev card being added
     * @since 2.0.00
     * @see #addDevCard(int, int, int)
     * @see #removeItem(int, int)
     * @see #keepPlayedItem(int)
     */
    public void addItem(final SOCInventoryItem item)
    {
        if (item.isPlayable())
            playables.add(item);
        else if (item.isKept())
            kept.add(item);
        else
            news.add(item);
    }

    /**
     * Add an amount to a type of dev card.
     * VP cards will be added with state {@link #KEPT}.  Otherwise, cards with {@code age} == {@link #OLD}
     * will have state {@link #PLAYABLE}, new cards will have {@link #NEW}.
     *<P>
     * Before v2.0.00, this method was {@code add(amt, age, ctype)}.
     *
     * @param age   either {@link #OLD} or {@link #NEW}
     * @param ctype the type of development card, at least
     *              {@link SOCDevCardConstants#MIN} and less than {@link SOCDevCardConstants#MAXPLUSONE}
     * @param amt   the amount
     * @see #addItem(SOCInventoryItem)
     * @see #removeDevCard(int, int)
     */
    public void addDevCard(int amt, final int age, final int ctype)
    {
        final boolean isNew;
        final List<SOCInventoryItem> clist;
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
     * Keep a played item: Change its state from {@link #PLAYABLE} to {@link #KEPT}.
     * @param itype  Item type code from {@link SOCInventoryItem#itype}
     * @return  true if kept, false if not found in Playable state
     * @since 2.0.00
     */
    public boolean keepPlayedItem(final int itype)
    {
        final Iterator<SOCInventoryItem> iIter = playables.iterator();
        while (iIter.hasNext())
        {
            SOCInventoryItem c = iIter.next();
            if (c.itype == itype)
            {
                iIter.remove();
                kept.add(c);
                return true;  // <--- Early return: found and kept ---
            }
        }

        return false;
    }

    /**
     * Remove a special item or card with a certain state from this set.  If its type isn't found,
     * try to remove from {@link SOCDevCardConstants#UNKNOWN} instead.
     *
     * @param state  Item state: {@link #NEW}, {@link #PLAYABLE} or {@link #KEPT}
     * @param itype  Item type code from {@link SOCInventoryItem#itype},
     *            or card type from {@link SOCDevCardConstants}
     * @return  true if removed, false if not found
     * @throws IllegalArgumentException if {@code state} isn't one of the 3 item states
     * @since 2.0.00
     * @see #removeDevCard(int, int)
     * @see #keepPlayedItem(int)
     */
    public boolean removeItem(final int state, final int itype)
        throws IllegalArgumentException
    {
        final List<SOCInventoryItem> ilist;
        switch (state)
        {
        case NEW:      ilist = news;      break;
        case PLAYABLE: ilist = playables; break;
        case KEPT:     ilist = kept;      break;
        default:       throw new IllegalArgumentException("Unknown state: " + state);
        }

        final Iterator<SOCInventoryItem> iIter = ilist.iterator();
        while (iIter.hasNext())
        {
            SOCInventoryItem c = iIter.next();
            if (c.itype == itype)
            {
                iIter.remove();
                return true;  // <--- Early return: found and removed ---
            }
        }

        // not found
        if (itype != SOCDevCardConstants.UNKNOWN)
            return removeItem(state, SOCDevCardConstants.UNKNOWN);
        else
            return false;
    }

    /**
     * Remove one dev card of a type from the set.
     * If that type isn't available, remove from {@link SOCDevCardConstants#UNKNOWN} instead.
     *<P>
     * Before v2.0.00, this method was {@code subtract(amt, age, ctype)}.
     *
     * @param age   either {@link #OLD} or {@link #NEW}
     * @param ctype the type of development card, at least
     *              {@link SOCDevCardConstants#MIN} and less than {@link SOCDevCardConstants#MAXPLUSONE}
     * @see #removeItem(int, int)
     */
    public void removeDevCard(final int age, final int ctype)
    {
        final List<SOCInventoryItem> clist;
        if (SOCDevCard.isVPCard(ctype))
            clist = kept;
        else
            clist = (age == NEW) ? news : playables;

        final Iterator<SOCInventoryItem> cIter = clist.iterator();
        while (cIter.hasNext())
        {
            SOCInventoryItem c = cIter.next();
            if ((c instanceof SOCDevCard) && (c.itype == ctype))
            {
                cIter.remove();
                return;  // <--- Early return: found and removed ---
            }
        }

        // not found
        if (ctype != SOCDevCardConstants.UNKNOWN)
            removeDevCard(age, SOCDevCardConstants.UNKNOWN);
    }

    /**
     * Get the number of Victory Point cards and VP items in this set:
     * All cards and items returning true for {@link SOCInventoryItem#isVPItem()}.
     *<P>
     * Before v2.0.00, this was {@code getNumVPCards()}.
     *
     * @return the number of victory point cards in
     *         this set
     * @see #getNumUnplayed()
     * @see #getTotal()
     * @see #getByState(int)
     * @see SOCDevCard#isVPCard(int)
     */
    public int getNumVPItems()
    {
        int sum = 0;

        // VP cards are never new, don't check the news list

        for (SOCInventoryItem c : playables)
            if (c.isVPItem())
                ++sum;
        for (SOCInventoryItem c : kept)
            if (c.isVPItem())
                ++sum;

        return sum;
    }

    /**
     * Some card types stay in your hand after being played.
     * Count only the unplayed ones (old or new); kept VP cards are skipped.
     *
     * @return the number of unplayed cards in this set
     * @see #getNumVPItems()
     * @see #getTotal()
     * @see #getByState(int)
     */
    public int getNumUnplayed()
    {
        return news.size() + playables.size();
    }

    /**
     * Change all the new cards and items to old ones.
     * Each one's state {@link #NEW} becomes {@link #PLAYABLE}.
     */
    public void newToOld()
    {
        for (SOCInventoryItem c : news)
        {
            c.newToOld();
            playables.add(c);
        }

        news.clear();
    }

}
