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
     * Get a card type's name.
     * @param ctype  A constant such as {@link SOCDevCardConstants#TOW}
     *               or {@link SOCDevCardConstants#ROADS}
     * @param game  Game data, or {@code null}; some game options might change a card name.
     *               For example, {@link SOCGameOption#K_SC_PIRI _SC_PIRI} renames "Knight" to "Warship".
     * @param withArticle  If true, format is: "a Market (+1VP)"; if false, is "Market (1VP)"
     * @return  The card name, formatted per {@code withArticle}; unknown ctypes return "Unknown card type #"
     */
    public static String getCardTypeName(final int ctype, final SOCGame game, final boolean withArticle)
    {
        final String ctname;

        switch (ctype)
        {
        case SOCDevCardConstants.DISC:
            ctname = (withArticle) ? /*I*/"a Year of Plenty"/*18N*/ : /*I*/"Year of Plenty"/*18N*/;
            break;

        case SOCDevCardConstants.KNIGHT:
            {
                final boolean withWarship = (game != null) && game.isGameOptionSet(SOCGameOption.K_SC_PIRI);
                ctname = (withWarship)
                    ? ((withArticle) ? "a Warship" : "Warship")
                    : ((withArticle) ? "a Soldier" : "Soldier");
            }
            break;

        case SOCDevCardConstants.MONO:
            ctname = (withArticle) ? "a Monopoly" : "Monopoly";
            break;

        case SOCDevCardConstants.ROADS:
            ctname = (withArticle) ? "a Road Building" : "Road Building";
            break;

        case SOCDevCardConstants.CAP:
            ctname = (withArticle) ? "a Gov.House (+1VP)" : "Gov. House (1VP)";
            break;

        case SOCDevCardConstants.LIB:
            ctname = (withArticle) ? "a Market (+1VP)" : "Market (1VP)";
            break;

        case SOCDevCardConstants.UNIV:
            ctname = (withArticle) ? "a University (+1VP)" : "University (1VP)";
            break;

        case SOCDevCardConstants.TEMP:
            ctname = (withArticle) ? "a Temple (+1VP)" : "Temple (1VP)";
            break;

        case SOCDevCardConstants.TOW:
            ctname = (withArticle) ? "a Chapel (+1VP)" : "Chapel (1VP)";
            break;

        default:
            ctname = "Unknown card type " + ctype;  // don't bother I18N, should not occur
        }

        return ctname;
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
