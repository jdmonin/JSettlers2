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
 *<P>
 * Inventory items must be {@link Cloneable} for use in set copy constructors,
 * see {@link #clone()} for details.
 *<P>
 * This class provides the methods needed for game logic.  For user-visible item names, you must
 * provide i18n keys and possibly override {@link #getItemName(SOCGame, boolean, SOCStringManager)};
 * see that method for details.
 *<P>
 * When adding a new kind of inventory item, update {@link SOCGame#forceEndTurn()} and
 * {@link soc.server.SOCGameHandler#forceEndGameTurn(SOCGame, String)} if the item is
 * playable and is returned to the player's inventory if their turn must be ended;
 * search where-used for {@link SOCForceEndTurnResult#getReturnedInvItem()}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCInventoryItem
    implements Cloneable
{

    /**
     * This inventory item's identifying type code or Dev Card type, which may be used at client and
     * server and sent over the network to specify this particular kind of item in a game.
     *<P>
     * For dev cards, it would be {@link SOCDevCardConstants#KNIGHT}, {@link SOCDevCardConstants#DISC}, etc.
     * The type code for items which aren't dev cards should be unique within the game scenario being played,
     * not just unique within its java class, and not overlap with the dev card constants.
     * For a port being moved in scenario {@code _SC_FTRI}, it would be negative: -{@link SOCBoard#MISC_PORT},
     * -{@link SOCBoard#SHEEP_PORT}, etc.
     */
    public final int itype;

    /**
     * Is this item playable this turn (state {@link SOCInventory#PLAYABLE PLAYABLE}),
     * not newly given ({@link SOCInventory#NEW NEW})?
     */
    private boolean playable;

    /**
     * Is this item to be kept in hand until end of game
     * (never state {@link SOCInventory#NEW NEW})?
     *<P>
     * Items with this flag can either be {@link #isPlayable()} before keeping, or not.
     * When the item is added to inventory, {@link #isPlayable()} is checked before {@link #isKept()}
     * to determine the item's initial state.
     * @see #isVPItem()
     */
    private final boolean kept;

    /** Is this item worth Victory Points when kept in inventory? */
    private final boolean vpItem;

    /**  i18n string key for this type of item, to be resolved by {@link SOCStringManager} to something like "Market (1VP)" */
    protected final String strKey;

    /**  i18n string key for an item of this type, to be resolved by {@link SOCStringManager} to something like "a Market (+1VP)" */
    protected final String aStrKey;

    /**
     * Factory method to create a specific scenario's special items, including item name i18n string keys
     * appropriate for {@code type} among the scenario's item types.
     *<P>
     * Currently recognizes and calls:
     *<UL>
     * <LI> {@link SOCGameOption#K_SC_FTRI _SC_FTRI}: Trade port:
     *      {@link SOCBoard#getPortDescForType(int, boolean) SOCBoard.getPortDescForType(-type, withArticle)}
     *</UL>
     *<P>
     * Callable at server and client.  If client version is older than the scenario, this
     * method will fall back to generic "unknown item" string keys.
     *
     * @param ga  Game, to check scenario options
     * @param type  Item or card type code, to be stored in {@link #itype}
     * @param isPlayable  Is the item playable this turn?
     * @param isKept  Is this item to be kept in hand until end of game?  See {@link #isKept()}.
     * @param isVP  Is this item worth Victory Points when kept in inventory?
     * @return  An inventory item named from this scenario's item types,
     *       or with generic name keys if {@code ga} doesn't have a scenario option recognized here
     */
    public final static SOCInventoryItem createForScenario
        (final SOCGame ga, final int type, final boolean isPlayable, final boolean isKept, final boolean isVP)
    {
        if (ga.isGameOptionSet(SOCGameOption.K_SC_FTRI))
        {
            // items in this scenario are always trade ports
            return new SOCInventoryItem
                (type, isPlayable, isKept, isVP,
                 SOCBoard.getPortDescForType(-type, false), SOCBoard.getPortDescForType(-type, true));
        }

        // Fallback:
        return new SOCInventoryItem
            (type, isPlayable, isKept, isVP, "game.invitem.unknown", "game.aninvitem.unknown");
    }

    /**
     * Create a new generic inventory item.
     *<P>
     * See also the factory method for specific scenarios' items:
     * {@link #createForScenario(SOCGame, int, boolean, boolean, boolean)}
     *
     * @param type  Item or card type code, to be stored in {@link #itype}
     * @param isPlayable  Is this item playable this turn (state {@link SOCInventory#PLAYABLE PLAYABLE}),
     *            not newly given ({@link SOCInventory#NEW NEW})?
     * @param isKept  Is this item to be kept in hand until end of game?  See {@link #isKept()}.
     * @param isVP  Is this item worth Victory Points when kept in inventory?
     * @param strKey   i18n string key for this type of item, to be resolved by {@link SOCStringManager} to something like "Market (1VP)"
     * @param aStrKey  i18m string key for an item of this type, to be resolved by {@link SOCStringManager} to something like "a Market (+1VP)"
     */
    public SOCInventoryItem
        (final int type, final boolean isPlayable, final boolean isKept, final boolean isVP,
         final String strKey, final String aStrKey)
    {
        this.itype = type;
        playable = isPlayable;
        kept = isKept;
        vpItem = isVP;
        this.strKey = strKey;
        this.aStrKey = aStrKey;
    }

    /**
     * Is this item newly given to a player (state {@link SOCInventory#NEW NEW}),
     * not {@link #isPlayable()} or {@link #isKept()}?
     */
    public boolean isNew()
    {
        return ! (playable || kept);
    }

    /**
     * Is this item playable this turn (state {@link SOCInventory#PLAYABLE PLAYABLE}),
     * not newly given ({@link SOCInventory#NEW NEW})?
     * @see #isNew()
     * @see #isKept()
     */
    public boolean isPlayable()
    {
        return playable;
    }

    /**
     * Is this item to be kept in hand until end of game
     * (never state {@link SOCInventory#NEW NEW})?
     *<P>
     * Items with this flag can either be {@link #isPlayable()} before keeping, or not.
     * When the item is added to a {@link SOCInventory}, {@link #isPlayable()} is checked
     * before {@link #isKept()} to determine the item's initial state.
     *<P>
     * This flag's value never changes during the item's lifetime.
     * @see #isVPItem()
     */
    public boolean isKept()
    {
        return kept;
    }

    /**
     * Is this item worth Victory Points when kept in inventory?
     *<P>
     * This flag's value never changes during the item's lifetime.
     * @return  True for VP items, false otherwise
     * @see #isKept()
     */
    public boolean isVPItem()
    {
        return vpItem;
    }

    /**
     * At the start of the holding player's turn, change state from
     * {@link SOCInventory#NEW NEW} to {@link SOCInventory#PLAYABLE PLAYABLE}.
     */
    public void newToOld()
    {
        playable = true;
    }

    /**
     * Get the item's name.
     *<P>
     * Called at server and at client, so any i18n name keys used must be in properties files at server and client.
     *<P>
     * SOCInventoryItem's implementation just calls {@link SOCStringManager#get(String) strings.get(key)} with the
     * string keys passed to the constructor.  If you need something more dynamic, override this in your subclass.
     *
     * @param game  Game data, or {@code null}; some game options might change an item name.
     *               For example, {@link SOCGameOption#K_SC_PIRI _SC_PIRI} renames "Knight" to "Warship".
     * @param withArticle  If true, format is: "a Market (+1VP)"; if false, is "Market (1VP)"
     * @param strings  StringManager to get i18n localized text
     * @return  The localized item name, formatted per {@code withArticle}
     */
    public String getItemName
        (final SOCGame game, final boolean withArticle, final SOCStringManager strings)
    {
        return strings.get((withArticle) ? aStrKey : strKey);
    }

    /**
     * For use in set copy constructors, create and return a clone of this {@link SOCInventoryItem}.
     * The {@code SOCInventoryItem} implementation just calls {@code super.clone()}.
     * @throws CloneNotSupportedException  Declared from super.clone(), should not occur
     *     since SOCInventoryItem implements Cloneable.
     * @return super.clone(), with any object fields deep-copied
     */
    public SOCInventoryItem clone()
        throws CloneNotSupportedException
    {
        return (SOCInventoryItem) super.clone();
    }

}
