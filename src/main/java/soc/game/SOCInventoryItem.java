/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2016,2019-2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.util.SOCStringManager;

/**
 * An inventory item, either a {@link SOCDevCard} or a scenario-specific item, held
 * in a player's hand ({@link SOCInventory}) to be played later or kept until scoring at the end of the game.
 * Except for {@code SOCDevCard}s, these items aren't subject to the rule of playing at most
 * 1 Development Card per turn. The player can play a Dev Card on the same turn as an item.
 *<P>
 * To see if a player can currently play an inventory item, use {@link SOCGame#canPlayInventoryItem(int, int)}.
 * Inventory items' lifecycle and play rules differ by scenario. In {@link SOCGameOptionSet#K_SC_FTRI SC_FTRI}
 * for example, the items are "gift" trade ports which can be played immediately (cannot cancel during placement)
 * or if nowhere to place, saved for placement later (that placement can be canceled).
 *<P>
 * Inventory items are unrelated to {@link SOCSpecialItem}s.
 *
 *<H3>The scenario-specific inventory items:</H3>
 *<UL>
 * <LI> {@link SOCGameOptionSet#K_SC_FTRI SC_FTRI}: Trade ports received as gifts,
 *      to be played and placed immediately if possible, or saved for later.
 *</UL>
 * For details on how a specific item is used, see its javadocs at {@link SOCGame#canPlayInventoryItem(int, int)}
 * and {@link SOCGame#playInventoryItem(int)}. For its network messages see {@link soc.message.SOCInventoryItemAction}.
 *
 *<H3>When adding a new kind of inventory item:</H3>
 *<UL>
 * <LI> Decide how and when the new kind of item will be played
 * <LI> Decide which scenario {@link SOCGameOption} will use the new kind of item
 *      (like {@link SOCGameOptionSet#K_SC_FTRI}): All code and javadoc updates will check for
 *      or mention that game option. Update its javadoc to mention {@link SOCInventoryItem}
 * <LI> For user-visible item names, make and localize i18n key(s) and pass them into your constructor.
 *      Possibly update (or for a new subclass, override) {@link #getItemName(SOCGame, boolean, SOCStringManager)};
 *      see that method for details
 * <LI> Update {@link SOCGame#canPlayInventoryItem(int, int)} and add the new item to the list in its javadoc
 * <LI> Update {@link SOCGame#playInventoryItem(int)} and add the new item to the list in its javadoc
 * <LI> Note: Inventory items are typically created by game methods called at the server, which use game rules
 *      to set the right values for fields like {@link #isPlayable()}, {@link #itype}, and {@link #canCancelPlay}.
 *      Items are created through either the constructor, or the {@code createForScenario} factory method
 *      (details below). The values of all fields are then sent to the client; the client does not decide its
 *      own values for fields like {@link #canCancelPlay}.
 * <LI> Decide whether to update {@link #createForScenario(SOCGame, int, boolean, boolean, boolean, boolean)}
 *      because of nonstandard field values or method calls during construction. Remember that server-side code
 *      can be easier to update later than client code, which needs the user to download the new version
 * <LI> Decide if the server will communicate item-related actions using {@code SOCSimpleRequest}, {@code SOCSimpleAction}
 *      or {@link soc.message.SOCInventoryItemAction SOCInventoryItemAction} messages, or more specific message types.
 *      Update those message handlers at clients and at server's SOCGameHandler; search where-used for each of the
 *      message classes that will be used. Document those message flows in the list of inventory items
 *      at {@link soc.message.SOCInventoryItemAction}.
 * <LI> Not all items are placed on the board, and not all of those allow placement to be canceled: check and update
 *      {@link #isPlayForPlacement(SOCGame, int)}, {@link SOCGame#cancelPlaceInventoryItem(boolean)}, SOCGame's javadocs
 *      for {@code gameState}, {@code oldGameState}, and {@link SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM}.
 *      For cancelable items, set the {@link #canCancelPlay} flag when calling the constructor.
 * <LI> If the item is playable, update {@link SOCGame#forceEndTurn()} and
 *      {@link soc.server.SOCGameHandler#forceEndGameTurn(SOCGame, String)} to return it to the player's inventory if
 *      their turn must be ended; search where-used for {@link SOCForceEndTurnResult#getReturnedInvItem()}.
 * <LI> If the item is playable or placeable and robots might do so, update
 *      {@link soc.robot.SOCRobotBrain#considerScenarioTurnFinalActions()}
 *      and/or {@link soc.robot.SOCRobotBrain#planAndPlaceInvItem()}
 * <LI> Consider adding debug commands about this kind of item to
 *      {@link soc.server.SOCGameHandler#processDebugCommand_scenario(soc.server.genericServer.Connection, String, String)}
 * <LI> If there's already a similar kind of item, search where-used for its SOCGameOption or related constants,
 *      and decide if your new kind should be checked at the same places in the code
 *</UL>
 * Inventory items must be {@link Cloneable} for use in set copy constructors,
 * see {@link #clone()} for details.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCInventoryItem
    implements Cloneable, Serializable
{
    /** Latest structural change: v2.0.00 */
    private static final long serialVersionUID = 2000L;

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

    /**
     * While this item is being played or placed on the board, can placement be canceled?
     * (Not all items can be played or placed.)  The canceled item is returned to the
     * player's inventory to be played later.
     */
    public final boolean canCancelPlay;

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
     * <LI> {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}: Trade port:
     *      {@link SOCBoard#getPortDescForType(int, boolean) SOCBoard.getPortDescForType(-type, withArticle)}
     *</UL>
     *<P>
     * Callable at server and client.  If client version is older than the scenario, this
     * method will fall back to generic "unknown item" string keys.
     *
     * @param ga  Game, to check scenario options and determine kind of item being created
     * @param type  Item or card type code, to be stored in {@link #itype}
     * @param isPlayable  Is the item playable this turn?
     * @param isKept  Is this item to be kept in hand until end of game?  See {@link #isKept()}.
     * @param isVP  Is this item worth Victory Points when kept in inventory?
     * @param canCancel  Can this item's play or placement be canceled?  See {@link #canCancelPlay}.
     * @return  An inventory item named from this scenario's item types,
     *       or with generic name keys if {@code ga} doesn't have a scenario option recognized here
     */
    public final static SOCInventoryItem createForScenario
        (final SOCGame ga, final int type, final boolean isPlayable, final boolean isKept,
         final boolean isVP, final boolean canCancel)
    {
        if (ga.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI))
        {
            // items in this scenario are always trade ports
            return new SOCInventoryItem
                (type, isPlayable, isKept, isVP, canCancel,
                 SOCBoard.getPortDescForType(-type, false), SOCBoard.getPortDescForType(-type, true));
        }

        // Fallback:
        return new SOCInventoryItem
            (type, isPlayable, isKept, isVP, canCancel, "game.invitem.unknown", "game.aninvitem.unknown");
    }

    /**
     * Does this type of item require placement on the board (state {@link SOCGame#PLACING_INV_ITEM}) when played?
     * If so, when the item is played, caller should call {@link SOCGame#setPlacingItem(SOCInventoryItem)}.
     * @param ga  Game, to check scenario options and determine kind of item being played
     * @param type  Item or card type code, from {@link #itype}
     * @return  True if this item must be placed when played; false if not, or if no known scenario game option is active.
     */
    public final static boolean isPlayForPlacement(final SOCGame ga, final int type)
    {
        if (ga.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI))
            return true;

        // Fallback:
        return false;
    }

    /**
     * Create a new generic inventory item.
     *<P>
     * See also the factory method for specific scenarios' items:
     * {@link #createForScenario(SOCGame, int, boolean, boolean, boolean, boolean)}
     *
     * @param type  Item or card type code, to be stored in {@link #itype}
     * @param isPlayable  Is this item playable this turn (state {@link SOCInventory#PLAYABLE PLAYABLE}),
     *            not newly given ({@link SOCInventory#NEW NEW})?
     * @param isKept  Is this item to be kept in hand until end of game?  See {@link #isKept()}.
     * @param isVP  Is this item worth Victory Points when kept in inventory?
     * @param canCancel  Can this item's play or placement be canceled?  See {@link #canCancelPlay}.
     * @param strKey   i18n string key for this type of item, to be resolved by {@link SOCStringManager} to something like "Market (1VP)"
     * @param aStrKey  i18m string key for an item of this type, to be resolved by {@link SOCStringManager} to something like "a Market (+1VP)"
     */
    public SOCInventoryItem
        (final int type, final boolean isPlayable, final boolean isKept, final boolean isVP, final boolean canCancel,
         final String strKey, final String aStrKey)
    {
        this.itype = type;
        playable = isPlayable;
        kept = isKept;
        vpItem = isVP;
        canCancelPlay = canCancel;
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
     * string keys passed to the constructor: {@link #strKey}, {@link #aStrKey}.
     * If you need something more dynamic, override this in your subclass.
     *
     * @param game  Game data, or {@code null}; some game options might change an item name.
     *               For example, {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI} renames "Knight" to "Warship".
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
     * For use in set copy constructors, create and return a deep copy of this {@link SOCInventoryItem}.
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

    /**
     * For debugging, a human-readable representation of this item's contents, of the form:
     * "SOCInventoryItem{itype=" + {@link #itype} + ", playable?=" + {@link #isPlayable()}
     *  + ", kept?=" + {@link #isKept()}
     *  + ", strKey=" + {@code strKey} + "}@" + Integer.toHexString({@link Object#hashCode()})
     */
    @Override
    public String toString()
    {
        return "SOCInventoryItem{itype=" + itype + ", playable?=" + playable + ", kept?=" + kept
            + ", strKey=" + strKey + "}@" + Integer.toHexString(hashCode());
    }

}
