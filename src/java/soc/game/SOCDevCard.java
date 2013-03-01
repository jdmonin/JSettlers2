/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013 Jeremy D Monin <jeremy@nand.net>
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
 * A single Dev Card, probably within a {@link SOCDevCardSet}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCDevCard implements SOCDevCardConstants
{
    /** Card type, such as {@link SOCDevCardConstants#ROADS} */
    public final int ctype;

    /** Is this card newly given to a player, or old from a previous turn? */
    private boolean cnew;

    /**
     * Is this card type a Victory Point card?
     * @param ctype  A constant such as {@link SOCDevCardConstants#TOW}
     *               or {@link SOCDevCardConstants#ROADS}
     * @return  True for VP types, false otherwise
     * @see #isVPCard()
     */
    public static boolean isVPCard(final int ctype)
    {
        return (ctype >= SOCDevCardConstants.CAP) && (ctype <= SOCDevCardConstants.TOW);
    }

    /**
     * Create a new card.
     * @param type   Card type, such as {@link SOCDevCardConstants#ROADS}
     * @param isNew  Is this card newly given to a player, or old from a previous turn?
     */
    public SOCDevCard(final int type, final boolean isNew)
    {
        ctype = type;
        cnew = isNew;
    }

    public boolean isNew()
    {
        return cnew;
    }

    public void newToOld()
    {
        cnew = false;
    }

    /**
     * Is this card type a Victory Point card?
     * @see #isVPCard(int)
     */
    public boolean isVPCard()
    {
        return isVPCard(ctype);
    }

}
