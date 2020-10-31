/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2014-2020 Jeremy D Monin <jeremy@nand.net>
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

import java.util.List;
import java.util.StringTokenizer;

import soc.game.SOCGame;  // for javadocs only
import soc.game.SOCGameOptionSet;  // for javadocs only
import soc.game.SOCPlayer;  // for javadocs only
import soc.game.SOCSpecialItem;  // for javadocs only

/**
 * This message is to pick, set, or clear a {@link SOCSpecialItem} in the game and/or the Special Item list
 * of the player owning the item. Within the game data, lists track the per-game (unowned or game-wide) and/or
 * per-player Special Item lists. The same Special Item object instance may be in both lists.
 * Special Items' status and details are currently "public" and known to all players, not hidden like VP dev cards.
 *<P>
 * Is sent for the Wonders chosen by players in the {@link SOCGameOptionSet#K_SC_WOND _SC_WOND} scenario.
 *<P>
 * This message conveys which object is affected ({@link #typeKey}, {@link #gameItemIndex}, {@link #playerItemIndex})
 * and the object data fields ({@link #playerNumber}, {@link #coord}, {@link #level}, {@link #sv}).
 * When a Special Item is held in the game's list and also its owning player's list,
 * the message can update both lists at once.
 *<P>
 * A client player can request that the item at an index within a per-player or game-wide Special Item list be picked,
 * set, or cleared. The server can decline that request, or announce a change or pick to all members of the game.
 * The server can also send a {@code SOCSetSpecialItem} message when anything happens in-game that causes a change.
 *<P>
 * In some scenarios, there may be a resource or other cost for picking, setting, or clearing an item.  If so,
 * the server will check whether the requesting player can pay, and if so, the {@code SOCSetSpecialItem} response
 * message(s) from the server will be preceded by {@link SOCPlayerElement} messages reporting the player's losses to
 * pay the cost. As with building a settlement or road, cost paid isn't reported as a text message:
 * If that's important to the client, they already have {@link SOCSpecialItem#getCost()}
 * and can print something when they receive the server's {@code SOCSetSpecialItem}.
 *<P>
 * If the special item change has also caused a change to game state, the server will announce that
 * after sending the special item message(s).
 *<P>
 * If client joins the game after it starts, these messages will be sent only after the {@link SOCBoardLayout2} message.
 * So, {@link SOCGame#updateAtBoardLayout()} has already been called at that client and created Special Item objects
 * before it receives any {@code SOCSetSpecialItem}.
 *<P>
 * For game data details, see the {@link SOCSpecialItem} class javadoc.
 * For message traffic/protocol details see operation {@link #OP_SET}, {@link #OP_CLEAR}, {@link #OP_PICK} and
 * {@link #OP_DECLINE} javadocs; client requests typically use {@link #OP_PICK}. Server can also respond
 * with combination ops {@link #OP_SET_PICK} or {@link #OP_CLEAR_PICK}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCSetSpecialItem extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    // If you add an OP_ constant, also update OPS_STRS[] and the unit test mentioned there.

    /**
     * If sent from client to server, a request to set an item in the game and/or owning player's Special Item list.
     * Server will call {@link SOCSpecialItem#playerSetItem(String, SOCGame, SOCPlayer, int, int, boolean)}.
     *<P>
     * If sent from server to client(s) because of something in game or responding to a client request,
     * this item will be set.
     *<P>
     * If {@link #gameItemIndex} != -1, sets the special item's {@link SOCSpecialItem#getGameIndex()},
     * otherwise will not clear or change that field.
     *<P>
     * If setting for both the game and the owning player ({@link #gameItemIndex} != -1 and
     * {@link #playerItemIndex} != -1), the client will check the game for an existing object before checking the player.
     * That is: If the game and player previously had different objects (not null) at the specified special item indices,
     * now they will both have a reference to the game's object. If the game's list item at this index was null,
     * the player's object will now also be referenced in the game's Special Item list.
     *<P>
     * {@code OP_SET} is the lowest-numbered operation.
     */
    public static final int OP_SET = 1;

    /**
     * If sent from client to server, a request to clear an item in the game and/or owning player's Special Item list.
     * Server will call {@link SOCSpecialItem#playerSetItem(String, SOCGame, SOCPlayer, int, int, boolean)}.
     *<P>
     * If sent from server to client(s) because of something in game or in response to a client request,
     * this item will be cleared.
     */
    public static final int OP_CLEAR = 2;

    /**
     * If sent from client to server, a request to pick or choose an item for some action.
     * Server will call {@link SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)}.
     *<P>
     * If sent from server to client(s), this item has been picked for some action.  The server isn't required to
     * announce the pick to all players, only to the requesting player.  Depending on the situation in which the
     * item is being picked, it may or may not make sense to announce it.  For clarity, any change to the contents
     * of a Special Item list/item fields must be done with {@link #OP_SET} or {@link #OP_CLEAR}
     * (or {@link #OP_SET_PICK} or {@link #OP_CLEAR_PICK}), never implied by sending only {@link #OP_PICK}.
     *<P>
     * Alternately, the server will respond to the requesting player with {@link #OP_DECLINE}.
     *<P>
     * The sequence of messages sent from the server for a player's successful PICK are:
     *<OL>
     * <LI> {@link SOCPlayerElement} message(s) to pay the cost, if any
     * <LI> Either:
     *  <UL>
     *   <LI> {@link #OP_SET} or {@link #OP_CLEAR} message(s) resulting from the pick
     *   <LI> {@link #OP_PICK} itself
     *  </UL>
     * <LI> Or:
     *  <UL>
     *   <LI> {@link #OP_SET_PICK} or {@link #OP_CLEAR_PICK} message resulting from the pick
     *  </UL>
     * <LI> {@link SOCGameState} and related messages, if the state changed or the game is now over
     *</OL>
     *<P>
     * For convenience, the server's PICK message includes the {@link #coord}, {@link #level}, and {@link #sv} field
     * values of the special item being picked.  Different scenarios might change picked objects in different ways,
     * so these fields are filled by:
     *<UL>
     * <LI> If the pick causes the item to become {@code null} and be cleared, the values before the pick
     *      as retrieved by {@link SOCGame#getSpecialItem(String, int, int, int)}
     * <LI> Otherwise, the field values after the pick
     * <LI> If the pick specifies both {@link #gameItemIndex} and {@link #playerItemIndex}, and afterwards
     *      these are two different {@link SOCSpecialItem} objects, then the values are taken from
     *      {@code gameItemIndex}'s item if not {@code null}, otherwise from {@code playerItemIndex}'s item.
     *</UL>
     * When the client receives the PICK, they can get the field values if needed by calling
     * {@link SOCGame#getSpecialItem(String, int)} or {@link SOCPlayer#getSpecialItem(String, int)},
     * because the SET or CLEAR messages are sent out before the PICK.
     *<P>
     * {@code OP_PICK} is currently the highest-numbered operation that a client can send as a request.
     */
    public static final int OP_PICK = 3;

    /**
     * Sent from server as reply to a requesting client's {@link #OP_SET}, {@link #OP_CLEAR}, or {@link #OP_PICK}:
     * The client's request is declined.
     *<P>
     * The reply's data fields ({@link #typeKey}, {@link #gameItemIndex}, etc) will have the same values as the request.
     */
    public static final int OP_DECLINE = 4;

    /**
     * Sent from server as a combined {@link #OP_SET} and {@link #OP_PICK} with same data fields.
     * Client should act as if this message is {@link #OP_SET}, and then as if it was
     * a second message which is {@link #OP_PICK}. See those ops' javadocs for details.
     * Not sent from client.
     */
    public static final int OP_SET_PICK = 5;

    /**
     * Sent from server as a combined {@link #OP_CLEAR} and {@link #OP_PICK} with same data fields.
     * Client should act as if this message is {@link #OP_CLEAR}, and then as if it was
     * a second message which is {@link #OP_PICK}. See those ops' javadocs for details.
     * Not sent from client.
     */
    public static final int OP_CLEAR_PICK = 6;

    /** Name of game. */
    public final String game;

    /** Special item type key; see the {@link SOCSpecialItem} class javadoc for details. */
    public final String typeKey;

    /**
     * The operation code: {@link #OP_SET}, {@link #OP_CLEAR}, {@link #OP_PICK}, {@link #OP_DECLINE},
     * {@link #OP_SET_PICK}, or {@link #OP_CLEAR_PICK}.
     */
    public final int op;

    /** Index in the game's Special Item list, or -1. */
    public final int gameItemIndex;

    /**
     * Index in the owning player's Special Item list, or -1.
     * If used, {@link #playerNumber} must be != -1.
     */
    public final int playerItemIndex;

    /**
     * Owning player number, or -1.
     * The item doesn't need to be in the owner's Special Item list;
     * if it should be, set {@link #playerItemIndex}.
     * A player can only request with their own playerNumber, server ignores this field.
     */
    public final int playerNumber;

    /** Optional coordinates on the board for this item, or -1. An edge or a node, depending on item type. */
    public final int coord;

    /** Optional level of construction or strength, or 0. */
    public final int level;

    /** Optional string value from {@link SOCSpecialItem#getStringValue()}, or {@code null}. Never "". */
    public final String sv;

    /**
     * Create a SOCSetSpecialItem message with data fields from an item object.
     *
     * @param game  game; only its name is used in this message
     * @param op  Operation code: see {@link #op} for values
     * @param typeKey  Special item type key; see the {@link SOCSpecialItem} class javadoc for details
     * @param gi  Game item index, or -1
     * @param pi  Player item index (requires {@link SOCSpecialItem#getPlayer() item.getPlayer()} != null), or -1
     * @param item  Item to copy for optional owning player, {@link #coord}, {@link #level}, and {@link #sv}
     * @throws IllegalArgumentException  if typeKey is null, or pi != -1 but item.getPlayer() is null,
     *            or gi == -1 and pi == -1
     * @throws NullPointerException if game or item is null
     */
    public SOCSetSpecialItem
        (final SOCGame game, final int op, final String typeKey, final int gi, final int pi, final SOCSpecialItem item)
        throws IllegalArgumentException, NullPointerException
    {
        this(game.getName(), op, typeKey, gi, pi, ((item.getPlayer() != null) ? item.getPlayer().getPlayerNumber() : -1),
             item.getCoordinates(), item.getLevel(), item.getStringValue());
    }

    /**
     * Create a SOCSetSpecialItem message, specifying all field values except
     * coordinate (-1), level (0), stringValue ({@code null}).
     * @param ga  Name of the game
     * @param op  Operation code: see {@link #op} for values
     * @param typeKey    Special item type key; see the {@link SOCSpecialItem} class javadoc for details
     * @param gi  Game item index, or -1
     * @param pi  Player item index (requires pn != -1), or -1
     * @param pn  Currently owning player number, or -1
     * @throws IllegalArgumentException
     * @throws NullPointerException
     */
    public SOCSetSpecialItem
        (final String ga, final int op, final String typeKey, final int gi, final int pi, final int pn)
        throws IllegalArgumentException, NullPointerException
    {
        this(ga, op, typeKey, gi, pi, pn, -1, 0, null);
    }

    /**
     * Create a SOCSetSpecialItem message, specifying all field values.
     *
     * @param ga  Name of the game
     * @param op  Operation code: see {@link #op} for values
     * @param typeKey    Special item type key; see the {@link SOCSpecialItem} class javadoc for details
     * @param gi  Game item index, or -1
     * @param pi  Player item index (requires pn != -1), or -1
     * @param pn  Currently owning player number, or -1
     * @param co  Optional coordinate on board, or -1
     * @param lv  Optional built level/strength, or 0
     * @param sv  Optional stringValue from {@link SOCSpecialItem#getStringValue()}, or {@code null}
     * @throws IllegalArgumentException  if ga or typeKey is null, or pn != -1 but pi == -1,
     *            or gi == -1 and pi == -1,
     *            or sv fails {@link SOCMessage#isSingleLineAndSafe(String)}
     */
    public SOCSetSpecialItem
        (final String ga, final int op, final String typeKey, final int gi, final int pi,
         final int pn, final int co, final int lv, final String sv)
        throws IllegalArgumentException
    {
        if ((ga == null) || (typeKey == null) || ((pn != -1) && (pi == -1))
            || ((pi == -1) && (gi == -1))
            || ((sv != null) && ! SOCMessage.isSingleLineAndSafe(sv)))
            throw new IllegalArgumentException();

        messageType = SETSPECIALITEM;
        game = ga;
        this.op = op;
        this.typeKey = typeKey;
        gameItemIndex = gi;
        playerItemIndex = pi;
        playerNumber = pn;
        coord = co;
        level = lv;
        this.sv = ((sv != null) && (sv.length() > 0)) ? sv : null;
    }

    // getGame is required by interface; all message fields are public final, no getters needed

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * Parse the command String into a SOCSetSpecialItem message.
     *
     * @param s   the String to parse, from {@link #toCmd()}
     * @return    a SOCSetSpecialItem message, or {@code null} if the data is garbled
     */
    public static SOCSetSpecialItem parseDataStr(final String s)
    {
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            final String ga; // the game name
            final int op;    // the operation
            final String tk;  // type key
            final int gi;    // game item index, or -1
            final int pi;    // player item index (if pn != -1), or -1
            final int pn;    // owning player number, or -1
            final int co;    // optional coordinate, or -1
            final int lv;    // optional level/strength, or 0
            String sv;  // optional string value, or null

            ga = st.nextToken();
            op = Integer.parseInt(st.nextToken());
            tk = st.nextToken();
            gi = Integer.parseInt(st.nextToken());
            pi = Integer.parseInt(st.nextToken());
            pn = Integer.parseInt(st.nextToken());
            co = Integer.parseInt(st.nextToken());
            lv = Integer.parseInt(st.nextToken());
            sv = st.nextToken();
            if (sv.equals(EMPTYSTR))
                sv = null;

            return new SOCSetSpecialItem(ga, op, tk, gi, pi, pn, co, lv, sv);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Minimum version where this message type is used.
     * SETSPECIALITEM introduced in 2.0.00.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public int getMinimumVersion() { return 2000; }

    /**
     * SETSPECIALITEM sep game sep2 operation sep2 typeKey sep2 gameItemIndex sep2 playerItemIndex
     *   sep2 playerNumber sep2 coord sep2 level sep2 sv
     *<P>
     * If {@link #sv} is {@code null}, it's sent as {@link SOCMessage#EMPTYSTR}.
     *
     * @return the command string
     */
    public String toCmd()
    {
        final String svStr = (sv != null) ? sv : EMPTYSTR;

        return SETSPECIALITEM + sep + game + sep2 + op + sep2 + typeKey + sep2 + gameItemIndex + sep2 + playerItemIndex
            + sep2 + playerNumber + sep2 + coord + sep2 + level + sep2 + svStr;
    }

    /** OP_* constant strings for {@link #toString()} and {@link #stripAttribNames(String)}. */
    public final static String[] OPS_STRS = { null, "SET", "CLEAR", "PICK", "DECLINE", "SET_PICK", "CLEAR_PICK" };
        // if you update these: Watch for backwards compatibility,
        // also update unit test TestStringConstants.testSetSpecialItem()

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters delimited for {@link SOCMessage#parseMsgStr(String)}
     * to pass to {@link #parseDataStr(String)}.
     * Converts to {@link #op} int from {@link #OPS_STRS}[], handles null {@link #sv}.
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.4.50
     */
    public static String stripAttribNames(String messageStrParams)
    {
        boolean svIsNull = false;
        if (messageStrParams.endsWith("|sv null"))
            svIsNull = true;
        else if (-1 == messageStrParams.indexOf("|sv="))
            return null;

        List<String> pieces = SOCMessage.stripAttribsToList(messageStrParams);
        if ((pieces == null) || (pieces.size() != 9))
            return null;

        int op;
        final String opStr = pieces.get(1);
        for (op = 1; op < OPS_STRS.length; ++op)
        {
            if (OPS_STRS[op].equals(opStr))
            {
                pieces.set(1, Integer.toString(op));
                break;
            }
        }
        if (op >= OPS_STRS.length)
            return null;

        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < 8; i++)
            ret.append(pieces.get(i)).append(sep2_char);
        if (svIsNull)
            ret.append(EMPTYSTR);
        else
            ret.append(pieces.get(8));

        return ret.toString();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        final String opStr;
        if ((op > 0) && (op < OPS_STRS.length))
            opStr = OPS_STRS[op];
        else
            opStr = Integer.toString(op);

        return "SOCSetSpecialItem:game=" + game + "|op=" + opStr + "|typeKey=" + typeKey
                + "|gi=" + gameItemIndex + "|pi=" + playerItemIndex + "|pn=" + playerNumber
                + "|co=" + ((coord >= 0) ? Integer.toHexString(coord) : Integer.toString(coord))
                + "|lv=" + level + ((sv != null) ? "|sv=" + sv : "|sv null");
    }

}
