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

import soc.util.SOCStringManager;

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
     * Resource type-and-count text keys for {@link #getCardTypeName(int, SOCGame, boolean, SOCStringManager)}.
     * Each subarray's indexes are the same values as {@link SOCDevCardConstants#UNKNOWN} to {@link SOCDevCardConstants#TOW}.
     * @since 2.0.00
     */
    private static final String[][] GETCARDTYPENAME_KEYS =
    {
        {     // without article
            "spec.dcards.unknown", "spec.dcards.roadbuilding", "spec.dcards.discoveryplenty", "spec.dcards.monopoly",
            "spec.dcards.capgovhouse", "spec.dcards.libmarket", "spec.dcards.university",
            "spec.dcards.temple", "spec.dcards.towerchapel", "spec.dcards.knightsoldier"
        }, {  // with article (a/an)
            "spec.dcards.aunknown", "spec.dcards.aroadbuilding", "spec.dcards.adiscoveryplenty", "spec.dcards.amonopoly",
            "spec.dcards.acapgovhouse", "spec.dcards.alibmarket", "spec.dcards.auniversity",
            "spec.dcards.atemple", "spec.dcards.atowerchapel", "spec.dcards.aknightsoldier"
        }
    };

    /**
     * Get a card type's name.
     * @param ctype  A constant such as {@link SOCDevCardConstants#TOW}
     *               or {@link SOCDevCardConstants#ROADS}
     * @param game  Game data, or {@code null}; some game options might change a card name.
     *               For example, {@link SOCGameOption#K_SC_PIRI _SC_PIRI} renames "Knight" to "Warship".
     * @param withArticle  If true, format is: "a Market (+1VP)"; if false, is "Market (1VP)"
     * @param strings  StringManager to get i18n localized text
     * @return  The card name, formatted per {@code withArticle}; unknown ctypes return "Unknown card type #"
     */
    public static String getCardTypeName
        (final int ctype, final SOCGame game, final boolean withArticle, final SOCStringManager strings)
    {
        // i18n: These names are also currently hardcoded in SOCServer.DEBUG_COMMANDS_HELP and .DEBUG_COMMANDS_HELP_DEV_TYPES

        final String ctname;

        if ((ctype == SOCDevCardConstants.KNIGHT) && (game != null) && game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
        {
            final String key = (withArticle) ? "spec.dcards.knightsoldier.awarship" : "spec.dcards.knightsoldier.warship";
            return strings.get(key);  // <--- Early return: special case ---
        }

        final String[] keyArr = GETCARDTYPENAME_KEYS[(withArticle) ? 1 : 0];
        if ((ctype >= 0) && (ctype < keyArr.length))
        {
            ctname = strings.get(keyArr[ctype]);
        } else {
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
