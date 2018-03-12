/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2016-2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;
import soc.game.SOCGameOption;
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
 * server responds with {@link #CANNOT_PLAY}, optionally with a {@link #reasonCode}
 *<LI> When a player plays an item, server sends {@link #PLAYED} to all clients,
 * including all flags such as {@link #isKept} and {@link #canCancelPlay}.
 * Messages after that will indicate new game state or any other results of playing the item.
 *<LI> If some other game action or event causes an item to need placement on the board,
 * but it was never in the player's inventory, server sends {@link #PLACING_EXTRA}
 * to give the item details such as {@link #itemType} and {@link #canCancelPlay}.
 * Play/placement rules are specific to each kind of inventory action, and the {@code PLACING_EXTRA}
 * message may be sent before a game state change or other messages necessary for placement.
 * See {@link #PLACING_EXTRA}'s javadoc for client handling.
 *</UL>
 * When the server sends a {@code SOCInventoryItemAction}, it doesn't also send a {@link SOCGameServerText} explaining
 * the details; the client must print such text based on the {@code SOCInventoryItemAction} received.
 *<P>
 * Based on {@link SOCDevCardAction}.
 *<P>
 *
 * <H5>Notes for certain special items:</H5>
 *<UL>
 * <LI> {@link SOCGameOption#K_SC_FTRI _SC_FTRI}: Trade ports received as gifts. <BR>
 *   In state {@link SOCGame#PLAY1} or {@link SOCGame#SPECIAL_BUILDING}, current player sends this when they
 *   have a port in their inventory.  {@code itemType} is the negative of the port type to play.
 *  <P>
 *   If they can place now, server broadcasts SOCInventoryItemAction({@link #PLAYED}) to the game to remove
 *   it from player's inventory, then sends {@link SOCGameState}({@link SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM}).
 *   (Player interface shouldn't let them place before the new game state is sent.)
 *   When the requesting client receives this PLAYED message, it will call {@link SOCGame#setPlacingItem(SOCInventoryItem)}
 *   because {@link SOCInventoryItem#isPlayForPlacement(SOCGame, int)} is true for {@code _SC_FTRI}.
 *  <P>
 *   Otherwise, server responds with {@link #CANNOT_PLAY} with one of these {@link #reasonCode}s: <BR>
 *   1 if the requested port type isn't in the player's inventory <BR>
 *   2 if the game options don't permit moving trade ports <BR>
 *   3 if the game state or current player aren't right to request placement now <BR>
 *   4 if player's {@link soc.game.SOCPlayer#getPortMovePotentialLocations(boolean)} is null <BR>
 *  <P>
 *   The port may be placed immediately if the player puts a ship at its original location and has
 *   a coastal settlement with room for a port.  Such a port will never be in the player's inventory.
 *   The server will immediately send that player {@link #PLACING_EXTRA} with the port's details;
 *   see that action's javadoc for client handling of the message.
 *  <P>
 *   When the player chooses their placement location, they should send
 *   {@link SOCSimpleRequest}({@link SOCSimpleRequest#TRADE_PORT_PLACE TRADE_PORT_PLACE}).
 *   See that constant's javadoc for details.
 *</UL>
 *
 * @since 2.0.00
 */
public class SOCInventoryItemAction extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    // Item Actions:
    // If you add or change actions, update toString().

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

    /**
     * item action PLAYED: From server, item was played.
     * Check the {@link #isKept} flag to see if the item should be removed from inventory, or remain with state KEPT.
     * Call {@link SOCInventoryItem#isPlayForPlacement(SOCGame, int)}: If true, playing this item requires placement;
     * client receiving the message should call {@link SOCGame#setPlacingItem(SOCInventoryItem)}.
     */
    public static final int PLAYED = 5;

    /**
     * If some other game action or event causes an item to need placement on the board,
     * but it was never in the player's inventory, server sends {@code PLACING_EXTRA}
     * to give the item details such as {@link #itemType} and {@link #canCancelPlay}.
     * May be sent to entire game or just to the placing current player.
     *<P>
     * Play/placement rules are specific to each kind of inventory action, and the {@code PLACING_EXTRA}
     * message may be sent before a game state change or other messages necessary for placement.  The client
     * handling {@code PLACING_EXTRA} should call {@link SOCGame#setPlacingItem(SOCInventoryItem)} and wait
     * for those other messages. When they arrive, client can call {@link SOCGame#getPlacingItem()} to
     * retrieve the item details.
     */
    public static final int PLACING_EXTRA = 6;

    /** {@link #isKept} flag position for sending over network in a bit field */
    private static final int FLAG_ISKEPT = 0x01;

    /** {@link #isVP} flag position for sending over network in a bit field */
    private static final int FLAG_ISVP   = 0x02;

    /** {@link #canCancelPlay} flag position for sending over network in a bit field */
    private static final int FLAG_CANCPLAY = 0x04;

    /**
     * Name of game; getter required by {@link SOCMessageForGame}
     */
    private final String game;

    /**
     * Player number, or -1 for action {@link #CANNOT_PLAY}
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
     * Optional reason codes for the {@link #CANNOT_PLAY} action, corresponding
     * to {@link SOCGame#canPlayInventoryItem(int, int)} return codes, or 0.
     * Also used within this class to encode {@link #isKept} and {@link #isVP} over the network.
     */
    public final int reasonCode;

    /**
     * If true, this item being added is kept in inventory until end of game.
     * This flag is sent for all actions except {@link #PLAY} and {@link #CANNOT_PLAY}.
     */
    public final boolean isKept;

    /**
     * If true, this item being added is worth victory points.
     * This flag is sent for all actions except {@link #PLAY} and {@link #CANNOT_PLAY}.
     */
    public final boolean isVP;

    /**
     * If true when sent with any {@code ADD} action, this item's later play or placement can be canceled:
     * {@link SOCInventoryItem#canCancelPlay}. This flag is sent for all actions
     * except {@link #PLAY} and {@link #CANNOT_PLAY}.
     */
    public final boolean canCancelPlay;

    /**
     * Create an InventoryItemAction message, skipping the boolean flags.
     *<P>
     * If the action is to add a card with {@link #isKept}, {@link #isVP}, or {@link #canCancelPlay}, use
     * the {@link #SOCInventoryItemAction(String, int, int, int, boolean, boolean, boolean)}
     * constructor instead.
     *<P>
     * If the action is the server replying with {@link #CANNOT_PLAY} with a reason code,
     * use the {@link #SOCInventoryItemAction(String, int, int, int, int)} constructor instead.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for action type {@link #CANNOT_PLAY}
     * @param ac  the type of action, such as {@link #PLAY}
     * @param it  the item type code, from {@link SOCInventoryItem#itype}
     */
    public SOCInventoryItemAction(final String ga, final int pn, final int ac, final int it)
    {
        this(ga, pn, ac, it, 0);
    }

    /**
     * Create an InventoryItemAction message, with any possible flags.
     * {@link #reasonCode} will be 0.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for action type {@link #CANNOT_PLAY}
     * @param ac  the type of action, such as {@link #ADD_PLAYABLE} or {@link #PLAYED}
     * @param it  the item type code, from {@link SOCInventoryItem#itype}
     * @param kept  If true, this is an add or play message with the {@link #isKept} flag set
     * @param vp    If true, this is an add  or play message with the {@link #isVP} flag set
     * @param canCancel  If true, this is an add or play message with the {@link #canCancelPlay} flag set
     */
    public SOCInventoryItemAction
        (final String ga, final int pn, final int ac, final int it,
         final boolean kept, final boolean vp, final boolean canCancel)
    {
        messageType = INVENTORYITEMACTION;
        game = ga;
        playerNumber = pn;
        action = ac;
        itemType = it;
        reasonCode = ((kept) ? FLAG_ISKEPT : 0) | ((vp) ? FLAG_ISVP : 0) | ((canCancel) ? FLAG_CANCPLAY : 0);
        isKept = kept;
        isVP = vp;
        canCancelPlay = canCancel;
    }

    /**
     * Create an InventoryItemAction message, with optional {@link #reasonCode}.
     * The {@link #isKept}, {@link #isVP}, and {@link #canCancelPlay} flags will be false.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for action type {@link #CANNOT_PLAY}
     * @param ac  the type of action, such as {@link #ADD_PLAYABLE}
     * @param it  the item type code, from {@link SOCInventoryItem#itype}
     * @param rc  reason code for {@link #reasonCode}, or 0
     */
    public SOCInventoryItemAction
        (final String ga, final int pn, final int ac, final int it, final int rc)
    {
        messageType = INVENTORYITEMACTION;
        game = ga;
        playerNumber = pn;
        action = ac;
        itemType = it;
        reasonCode = rc;
        isKept = false;
        isVP = false;
        canCancelPlay = false;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * INVENTORYITEMACTION sep game sep2 playerNumber sep2 action sep2 itemType [ sep2 rcode ]
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, action, itemType, reasonCode);
    }

    /**
     * INVENTORYITEMACTION sep game sep2 playerNumber sep2 action sep2 itemType [ sep2 rcode ]
     *
     * @param ga  the game name
     * @param pn  the player number
     * @param ac  the type of action
     * @param it  the item type code
     * @param rc  the reason code if action == CANNOT_PLAY
     * @return    the command string
     */
    public static String toCmd
        (final String ga, final int pn, final int ac, final int it, final int rc)
    {
        String cmd = INVENTORYITEMACTION + sep + ga + sep2 + pn + sep2 + ac + sep2 + it;
        if (rc != 0)
            cmd = cmd + sep2 + rc;
        return cmd;
    }

    /**
     * Parse the command String into an InventoryItemAction message.
     *
     * @param s   the String to parse
     * @return    an InventoryItemAction message, or null if the data is garbled
     */
    public static SOCInventoryItemAction parseDataStr(String s)
    {
        final String ga;
        final int pn, ac, it;
        int rc = 0;
        boolean actionHasFlags = false, kept = false, vp = false, canCancel = false;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            ac = Integer.parseInt(st.nextToken());
            it = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens())
            {
                rc = Integer.parseInt(st.nextToken());
                if ((ac != PLAY) && (ac != CANNOT_PLAY))
                {
                    actionHasFlags = true;
                    kept = ((rc & FLAG_ISKEPT) != 0);
                    vp   = ((rc & FLAG_ISVP)   != 0);
                    canCancel = ((rc & FLAG_CANCPLAY) != 0);
                }
            }
        }
        catch (Exception e)
        {
            return null;
        }

        if (actionHasFlags)
            return new SOCInventoryItemAction(ga, pn, ac, it, kept, vp, canCancel);
        else
            return new SOCInventoryItemAction(ga, pn, ac, it, rc);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        final String ac;
        switch (action)
        {
        case ADD_PLAYABLE:  ac = "ADD_PLAYABLE";  break;
        case ADD_OTHER:     ac = "ADD_OTHER";     break;
        case PLAY:          ac = "PLAY";          break;
        case CANNOT_PLAY:   ac = "CANNOT_PLAY";   break;
        case PLAYED:        ac = "PLAYED";        break;
        case PLACING_EXTRA: ac = "PLACING_EXTRA"; break;
        default:            ac = Integer.toString(action);
        }

        String s = "SOCInventoryItemAction:game=" + game + "|playerNum=" + playerNumber + "|action=" + ac
            + "|itemType=" + itemType;

        if ((action != PLAY) && (action != CANNOT_PLAY))
            s += "|kept=" + isKept + "|isVP=" + isVP + "|canCancel=" + canCancelPlay;
        else
            s += "|rc=" + reasonCode;

        return s;
    }

}
