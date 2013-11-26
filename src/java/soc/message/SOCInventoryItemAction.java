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
package soc.message;

import java.util.StringTokenizer;
import soc.game.SOCInventoryItem;     // for javadoc's use


/**
 * This message is a client player request, or server response or announcement,
 * about {@link SOCInventoryItem}s in a player's inventory.
 *<P>
 * Inventory items are held by a player like {@code SOCDevCard}s.
 * These special items are used by certain scenarios, and the meaning of
 * each one's {@link #itemType} is specific to the scenario.
 * An item's current state can be NEW and not yet playable; PLAYABLE now; or KEPT until end of game.
 *<P>
 * Possible message traffic, by {@link #action}:
 *<UL>
 *<LI> From Server to one client or all clients:
 * Add an item to a player's inventory with a certain state: {@link #ADD_PLAYABLE}, {@link #ADD_OTHER}
 *<LI> From Client: Player is requesting to {@link #PLAY} a playable item
 *<LI> If the client can't currently play that type,
 * server responds with {@link #CANNOT_PLAY}
 *<LI> When a player plays an item, server sends
 * {@link #PLAY_REMOVE} or {@link #PLAY_KEPT} to the client or to all clients
 *</UL>
 * Based on {@link SOCDevCardAction}.
 *
 * @since 2.0.00
 */
public class SOCInventoryItemAction extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    /** item action ADD_PLAYABLE: From server, add as Playable to player's inventory */
    public static final int ADD_PLAYABLE = 1;

    /** item action ADD_OTHER: From server, add as New or Kept to player's inventory,
     *  depending on whether this item's type is kept until end of game */
    public static final int ADD_OTHER = 2;

    /** item action PLAY: Client request to play a PLAYABLE item */
    public static final int PLAY = 3;

    /**
     * item action CANNOT_PLAY: From server, the player or bot can't play the requested item at this time.
     * This is sent only to the requesting player, so playerNumber is always -1 in this message.
     */
    public static final int CANNOT_PLAY = 4;

    /** item action PLAY_REMOVE: From server, item was played and should be removed from inventory */
    public static final int PLAY_REMOVE = 5;

    /** item action PLAY_KEPT: From server, item was played and stays in inventory with state KEPT */
    public static final int PLAY_KEPT = 6;

    /**
     * Name of game; getter required by {@link SOCMessageForGame}
     */
    private final String game;

    /**
     * Player number
     */
    public final int playerNumber;

    /**
     * The item type code, from {@link SOCInventoryItem#itype}
     */
    public final int itemType;

    /**
     * Action, such as {@link #ADD_PLAYABLE} or {@link #PLAY}
     */
    public final int action;

    /**
     * If true, this item being added is kept in inventory until end of game.
     * This flag is never set except for actions {@link #ADD_PLAYABLE} and {@link #ADD_OTHER}.
     */
    public final boolean isKept;

    /**
     * If true, this item being added is worth victory points.
     * This flag is never set except for actions {@link #ADD_PLAYABLE} and {@link #ADD_OTHER}.
     */
    public final boolean isVP;

    /**
     * Create an InventoryItemAction message, skipping some flags.
     *<P>
     * If the action is to add a card with {@link #isKept} or {@link #isVP}, use
     * the {@link #SOCInventoryItemAction(String, int, int, int, boolean, boolean)}
     * constructor instead.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for action type {@link #CANNOT_PLAY}
     * @param ac  the type of action, such as {@link #PLAY}
     * @param it  the item type code, from {@link SOCInventoryItem#itype}
     */
    public SOCInventoryItemAction(final String ga, final int pn, final int ac, final int it)
    {
        this(ga, pn, ac, it, false, false);
    }

    /**
     * Create an InventoryItemAction message, with any possible flags.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for action type {@link #CANNOT_PLAY}
     * @param ac  the type of action, such as {@link #ADD_PLAYABLE}
     * @param it  the item type code, from {@link SOCInventoryItem#itype}
     * @param kept  If true, this is an add message with the {@link #isKept} flag set
     * @param vp    If true, this is an add message with the {@link #isVP} flag set
     */
    public SOCInventoryItemAction
        (final String ga, final int pn, final int ac, final int it, final boolean kept, final boolean vp)
    {
        messageType = INVENTORYITEMACTION;
        game = ga;
        playerNumber = pn;
        action = ac;
        itemType = it;
        isKept = kept;
        isVP = vp;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * INVENTORYITEMACTION sep game sep2 playerNumber sep2 action sep2 itemType [ sep2 isKept sep2 isVP ]
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, action, itemType, isKept, isVP);
    }

    /**
     * INVENTORYITEMACTION sep game sep2 playerNumber sep2 action sep2 itemType [ sep2 isKept sep2 isVP ]
     *
     * @param ga  the game name
     * @param pn  the player number
     * @param ac  the type of action
     * @param it  the item type code
     * @param kept  If true, this is an add message with the {@link #isKept} flag set
     * @param vp    If true, this is an add message with the {@link #isVP} flag set
     * @return    the command string
     */
    public static String toCmd
        (final String ga, final int pn, final int ac, final int it, final boolean kept, final boolean vp)
    {
        String cmd = INVENTORYITEMACTION + sep + ga + sep2 + pn + sep2 + ac + sep2 + it;
        if (kept || vp)
            cmd = cmd + sep2 + (kept ? '1' : '0') + sep2 + (vp ? '1' : '0');
        return cmd;
    }

    /**
     * Parse the command String into an InventoryItemAction message.
     *
     * @param s   the String to parse
     * @return    an InventoryItemAction message, or null of the data is garbled
     */
    public static SOCInventoryItemAction parseDataStr(String s)
    {
        final String ga;
        final int pn, ac, it;
        boolean kept = false, vp = false;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            ac = Integer.parseInt(st.nextToken());
            it = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens())
                kept = st.nextToken().equals("1");
            if (st.hasMoreTokens())
                vp = st.nextToken().equals("1");
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCInventoryItemAction(ga, pn, ac, it, kept, vp);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCInventoryItemAction:game=" + game + "|playerNum=" + playerNumber + "|action=" + action
            + "|itemType=" + itemType + "|kept=" + isKept + "|isVP=" + isVP;

        return s;
    }

}
