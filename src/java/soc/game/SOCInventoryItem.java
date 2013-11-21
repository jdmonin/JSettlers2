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
 * An inventory item, such as a {@link SOCDevCard} or a scenario-specific item, held
 * in a player's hand to be played later or kept until scoring at the end of the game.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public interface SOCInventoryItem
{
    /**
     * Get this item's identifying code, which may be used at client and server
     * and sent over the network to specify this particular kind of item in a game.
     *<P>
     * For dev cards, it would be {@link SOCDevCardConstants#KNIGHT}, {@link SOCDevCardConstants#DISC}, etc.
     * The code number for items which aren't dev cards should be unique within the game scenario being played,
     * not just unique within its java class.
     * For a port being moved in scenario {@code _SC_FTRI}, it would be {@link SOCBoard#MISC_PORT},
     * {@link SOCBoard#SHEEP_PORT}, etc.
     *
     * @return a number to identify this item or its type
     */
    public int getItemCode();

    /**
     * Is this item playable this turn (state {@link SOCDevCardSet#PLAYABLE PLAYABLE}),
     * not newly given ({@link SOCDevCardSet#NEW NEW})?
     */
    public boolean isPlayable();

    /**
     * Is this item to be kept in hand until end of game
     * (never state {@link SOCDevCardSet#NEW NEW})?
     *<P>
     * Items with this flag can either be {@link #isPlayable()} before keeping, or not.
     * When the item is added to inventory, {@link #isPlayable()} is checked before {@link #isKept()}
     * to determine the item's initial state.
     * @see #isVPItem()
     */
    public boolean isKept();

    /**
     * Is this item worth Victory Points when kept in inventory?
     * @return  True for VP items, false otherwise
     * @see #isKept()
     */
    public boolean isVPItem();

    /**
     * At the start of the holding player's turn, change state from
     * {@link SOCDevCardSet#NEW NEW} to {@link SOCDevCardSet#PLAYABLE PLAYABLE}.
     */
    public void newToOld();

    /**
     * Get the item's name.
     *<P>
     * Called at server and at client, so any i18n name keys must be in properties files at server and client.
     * @param game  Game data, or {@code null}; some game options might change an item name.
     *               For example, {@link SOCGameOption#K_SC_PIRI _SC_PIRI} renames "Knight" to "Warship".
     * @param withArticle  If true, format is: "a Market (+1VP)"; if false, is "Market (1VP)"
     * @param strings  StringManager to get i18n localized text
     * @return  The item name, formatted per {@code withArticle}
     */
    public String getItemName
        (final SOCGame game, final boolean withArticle, final SOCStringManager strings);

}
