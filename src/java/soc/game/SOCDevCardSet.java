/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007 Jeremy D. Monin <jeremy@nand.net>
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
 * This represents a collection of development cards
 */
public class SOCDevCardSet implements Serializable, Cloneable
{
    /**
     * age constants
     */
    public static final int OLD = 0;
    public static final int NEW = 1;

    /**
     * the number of development cards
     * [OLD] are the old cards
     * [NEW] are recently bought cards
     */
    private int[][] devCards;

    /**
     * Make an empty development card set
     */
    public SOCDevCardSet()
    {
        devCards = new int[2][SOCDevCardConstants.MAXPLUSONE];
        clear();
    }

    /**
     * Make a copy of a dev card set
     *
     * @param set  the dev card set to copy
     */
    public SOCDevCardSet(SOCDevCardSet set)
    {
        devCards = new int[2][SOCDevCardConstants.MAXPLUSONE];

        for (int i = SOCDevCardConstants.MIN;
                i < SOCDevCardConstants.MAXPLUSONE; i++)
        {
            devCards[OLD][i] = set.devCards[OLD][i];
            devCards[NEW][i] = set.devCards[NEW][i];
        }
    }

    /**
     * set the number of old and new dev cards to zero
     */
    public void clear()
    {
        for (int i = SOCDevCardConstants.MIN;
                i < SOCDevCardConstants.MAXPLUSONE; i++)
        {
            devCards[OLD][i] = 0;
            devCards[NEW][i] = 0;
        }
    }

    /**
     * @return the number of a kind of development card
     *
     * @param age  either OLD or NEW
     * @param ctype
     *        the type of development card as described
     *        in SOCDevCardConstants
     */
    public int getAmount(int age, int ctype)
    {
        return devCards[age][ctype];
    }

    /**
     * @return the total number of development cards
     */
    public int getTotal()
    {
        int sum = 0;

        for (int i = SOCDevCardConstants.MIN;
                i < SOCDevCardConstants.MAXPLUSONE; i++)
        {
            sum += (devCards[OLD][i] + devCards[NEW][i]);
        }

        return sum;
    }

    /**
     * set the amount of a type of card
     *
     * @param age   either OLD or NEW
     * @param ctype the type of development card
     * @param amt   the amount
     */
    public void setAmount(int amt, int age, int ctype)
    {
        devCards[age][ctype] = amt;
    }

    /**
     * add an amount to a type of card
     *
     * @param age   either OLD or NEW
     * @param ctype the type of development card
     * @param amt   the amount
     */
    public void add(int amt, int age, int ctype)
    {
        devCards[age][ctype] += amt;
    }

    /**
     * subtract an amount from a type of card
     *
     * @param age   either OLD or NEW
     * @param ctype the type of development card
     * @param amt   the amount
     */
    public void subtract(int amt, int age, int ctype)
    {
        if (amt <= devCards[age][ctype])
        {
            devCards[age][ctype] -= amt;
        }
        else
        {
            devCards[age][ctype] = 0;
            devCards[age][SOCDevCardConstants.UNKNOWN] -= amt;
        }
    }

    /**
     * @return the number of victory point cards in
     *         this set
     */
    public int getNumVPCards()
    {
        int sum = 0;

        sum += devCards[OLD][SOCDevCardConstants.CAP];
        sum += devCards[OLD][SOCDevCardConstants.LIB];
        sum += devCards[OLD][SOCDevCardConstants.UNIV];
        sum += devCards[OLD][SOCDevCardConstants.TEMP];
        sum += devCards[OLD][SOCDevCardConstants.TOW];
        sum += devCards[NEW][SOCDevCardConstants.CAP];
        sum += devCards[NEW][SOCDevCardConstants.LIB];
        sum += devCards[NEW][SOCDevCardConstants.UNIV];
        sum += devCards[NEW][SOCDevCardConstants.TEMP];
        sum += devCards[NEW][SOCDevCardConstants.TOW];

        return sum;
    }
    
    /**
     * Some card types stay in your hand after being played.
     * Count only the unplayed ones (old or new). 
     * 
     * @return the number of unplayed cards in this set
     */
    public int getNumUnplayed()
    {
        int sum = 0;

        sum += devCards[OLD][SOCDevCardConstants.KNIGHT];
        sum += devCards[OLD][SOCDevCardConstants.ROADS];
        sum += devCards[OLD][SOCDevCardConstants.DISC];
        sum += devCards[OLD][SOCDevCardConstants.MONO];
        sum += devCards[OLD][SOCDevCardConstants.UNKNOWN];
        sum += devCards[NEW][SOCDevCardConstants.KNIGHT];
        sum += devCards[NEW][SOCDevCardConstants.ROADS];
        sum += devCards[NEW][SOCDevCardConstants.DISC];
        sum += devCards[NEW][SOCDevCardConstants.MONO];
        sum += devCards[NEW][SOCDevCardConstants.UNKNOWN];
        
        return sum;
    }

    /**
     * change all the new cards to old ones
     */
    public void newToOld()
    {
        for (int i = SOCDevCardConstants.MIN;
                i < SOCDevCardConstants.MAXPLUSONE; i++)
        {
            devCards[OLD][i] += devCards[NEW][i];
            devCards[NEW][i] = 0;
        }
    }
}
