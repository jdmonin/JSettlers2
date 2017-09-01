/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013-2017 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCBoardLarge;  // solely for javadocs
import soc.game.SOCGameOption;  // solely for javadocs

/**
 * This generic message from the server to clients handles a simple action or event in a game, usually about
 * a client player. This is a way to add game actions and events without adding new SOCMessage subclasses.
 * It has a player number, an action type code, and two optional detail-value fields.
 * This message comes after, not before, any messages that update the game and player data for the action.
 *<P>
 * To get the optional detail value fields from a {@code SOCSimpleAction}, be sure to use {@link #getValue1()}
 * and {@link #getValue2()}, not {@link #getParam1()} and {@link #getParam2()} which would instead return the
 * player number and action type code.  {@link #getPlayerNumber()} and {@link #getActionType()} are
 * convenience methods with more intuitive names to retrieve the player number and typecode.
 *<P>
 * This message type is useful for functions that don't have a complicated set of
 * details attached, such as telling all players that someone has bought a development card,
 * or telling a bot that it's made a successful bank/port trade, or some event or condition just happened.
 * Some action types may not be about a specific player; this will be mentioned in the typecode's javadoc.
 *<P>
 * Depending on the action type code, this message may be broadcast to the entire game
 * or sent to only the affected player.  Clients should ignore action types they don't
 * know how to handle (maybe the type is newer than the client's version).
 *<P>
 * Action type codes below 1000 are for general types that different kinds of games might be able to use.<BR>
 * Gametype-specific action types start at 1000.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.19
 * @see SOCSimpleRequest
 */
public class SOCSimpleAction extends SOCMessageTemplate4i
{
    private static final long serialVersionUID = 1119L;

    /**
     * First version number (1.1.19) that has this message type.
     * Send older clients {@link SOCGameTextMsg} or other appropriate messages instead.
     */
    public static final int VERSION_FOR_SIMPLEACTION = 1119;

    /**
     * The current player has bought a development card.
     * For i18n in v2.x, this message is sent instead of a text message announcing the buy; bots can ignore it.
     * {@code value1} is the number of cards remaining to be bought, {@code value2} is unused.
     * Follows a {@link SOCDevCardAction} which has the card's info, bots must process that message.
     * @since 1.1.19
     */
    public static final int DEVCARD_BOUGHT = 1;

    /**
     * The requested resource trade with the bank/ports was successful.
     * {@code value1} and {@code value2} are unused.
     * Sent to bots only; human players see a text message sent to the entire game.
     * @since 1.1.19
     */
    public static final int TRADE_SUCCESSFUL = 2;

    /**
     * This edge coordinate on the game board has become a Special Edge, or is no longer a Special Edge.
     * Used in some game scenarios.  Applies only to games using {@link SOCBoardLarge}.
     * Client should call {@link SOCBoardLarge#setSpecialEdge(int, int)}.
     *<P>
     * pn: Unused; -1 <br>
     * Param 1: The edge coordinate <br>
     * Param 2: Its new special edge type, such as {@link SOCBoardLarge#SPECIAL_EDGE_DEV_CARD},
     *     or 0 if no longer special
     * @since 2.0.00
     */
    public static final int BOARD_EDGE_SET_SPECIAL = 3;

    /**
     * This message from server announces the results of the current player's pirate fortress attack attempt:
     * Pirates' random defense strength, number of player's ships lost (win/tie/loss).
     * Sent in response to client's {@link SOCSimpleRequest#SC_PIRI_FORT_ATTACK}
     * in scenario {@link SOCGameOption#K_SC_PIRI _SC_PIRI}.
     *<P>
     * This message is sent out <B>after</B> related messages with game data (see below), so that those
     * can be shown visually before any popup announcing the result.
     *<P>
     * Param 1: The pirates' defense strength (random 1 - 6) <br>
     * Param 2: The number of ships lost by the player: 0 if player wins, 1 if tie, 2 if pirates win
     *<P>
     * These game data update messages are sent from server before {@code SC_PIRI_FORT_ATTACK_RESULT}, in this order:
     *<UL>
     *      Messages sent if player does not win: <br>&nbsp;
     * <LI> {@link SOCRemovePiece} for each removed ship
     * <LI> {@link SOCPlayerElement}({@link SOCPlayerElement#SCENARIO_WARSHIP_COUNT SCENARIO_WARSHIP_COUNT})
     *        if any of the player's warships were removed
     *      <P>&nbsp;<P>
     *      Messages sent if player wins: <br>&nbsp;
     * <LI> {@link SOCMoveRobber} only if all players' fortresses are recaptured,
     *        which removes the pirate fleet from the board (new pirate coordinate = 0)
     * <LI> {@link SOCPieceValue} for the fortress' reduced strength;
     *        if its new strength is 0, it is recaptured by the player
     * <LI> {@link SOCPutPiece}({@code SETTLEMENT}) if the player wins for the last time
     *        and recaptures the fortress
     *</UL>
     *
     * @since 2.0.00
     */
    public static final int SC_PIRI_FORT_ATTACK_RESULT = 1001;

    /**
     * The current player has removed a trade port from the board.
     * {@code value1} is the former port's edge coordinate, {@code value2} is the port type.
     * Sent to entire game.  If the player must place the port immediately, server will soon send
     * {@link SOCGameState}({@link soc.game.SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM}) among other messages.
     *<P>
     * When the player wants to place the removed port, they will send {@link SOCSimpleRequest#TRADE_PORT_PLACE}
     * with their chosen location.  If the placement is allowed, the server will broadcast a similar
     * {@link SOCSimpleRequest#TRADE_PORT_PLACE} to the game; see that javadoc for details.
     *<P>
     * Used with scenario option {@link SOCGameOption#K_SC_FTRI _SC_FTRI}.
     * @since 2.0.00
     */
    public static final int TRADE_PORT_REMOVED = 1002;

    // Reminder: If you add an action type, check client and server code to determine if the new type
    // should be added to methods such as:
    // - SOCGameHandler.handleSIMPLEACTION
    // - SOCPlayerClient.handleSIMPLEACTION
    // - SOCDisplaylessPlayerClient.handleSIMPLEACTION
    // - SOCRobotBrain.run case SOCMessage.SIMPLEACTION

    /**
     * Create a SOCSimpleAction message.
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on, or -1 if this action isn't about a specific player
     * @param acttype  the action type; below 1000 is general, 1000+ is specific to one kind of game
     */
    public SOCSimpleAction(final String ga, final int pn, final int acttype)
    {
        this(ga, pn, acttype, 0, 0);
    }

    /**
     * Create a SOCSimpleAction message with a detail value.
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on, or -1 if this action isn't about a specific player
     * @param acttype  the action type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  Optional detail value, or 0.  Use {@link #getValue1()}, not {@link #getParam1()}, to get
     *     this value from a {@code SOCSimpleAction} message.
     */
    public SOCSimpleAction(final String ga, final int pn, final int acttype, final int value1)
    {
        this(ga, pn, acttype, value1, 0);
    }

    /**
     * Create a SOCSimpleAction message with 2 detail values.
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on, or -1 if this action isn't about a specific player
     * @param acttype  the action type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  First optional detail value, or 0.  Use {@link #getValue1()}, not {@link #getParam1()}, to get
     *     this value from a {@code SOCSimpleAction} message.
     * @param value2  Second optional detail value, or 0.  Use {@link #getValue2()}, not {@link #getParam2()}, to get
     *     this value from a {@code SOCSimpleAction} message.
     */
    public SOCSimpleAction(final String ga, final int pn, final int acttype, final int value1, final int value2)
    {
        super(SIMPLEACTION, ga, pn, acttype, value1, value2);
    }

    /**
     * @return the player number acting or acted on, or -1 if this action isn't about a specific player
     */
    public final int getPlayerNumber()
    {
        return p1;
    }

    /**
     * @return the action type
     */
    public final int getActionType()
    {
        return p2;
    }

    /**
     * @return the action's optional {@code value1} detail field
     */
    public final int getValue1()
    {
        return p3;
    }

    /**
     * @return the action's optional {@code value2} detail field
     */
    public final int getValue2()
    {
        return p4;
    }

    /**
     * {@link SOCMessage#SIMPLEACTION SIMPLEACTION} sep game sep2 playernumber sep2 acttype sep2 value1 sep2 value2
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on, or -1 if this action isn't about a specific player
     * @param acttype  the action type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  First optional detail value, or 0
     * @param value2  Second optional detail value, or 0
     * @return the command string
     */
    public static String toCmd(final String ga, final int pn, final int acttype, final int value1, final int value2)
    {
        return SOCMessageTemplate4i.toCmd(SIMPLEACTION, ga, pn, acttype, value1, value2);
    }

    /**
     * Parse the command String into a SOCSimpleAction message
     *
     * @param s   the String to parse: {@link SOCMessage#SIMPLEACTION SIMPLEACTION}
     *            sep game sep2 playernumber sep2 acttype sep2 value1 sep2 value2
     * @return    a SOCSimpleAction message, or {@code null} if the data is garbled
     */
    public static SOCSimpleAction parseDataStr(final String s)
    {
        final String ga; // the game name
        final int pn;    // the player number or -1
        final int at;    // action type code
        final int v1;    // optional value1
        final int v2;    // optional value2

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            at = Integer.parseInt(st.nextToken());
            v1 = Integer.parseInt(st.nextToken());
            v2 = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCSimpleAction(ga, pn, at, v1, v2);
    }

    /**
     * Minimum version where this message type is used.
     * SIMPLEACTION introduced in 1.1.19.
     * @return Version number, 1119 for JSettlers 1.1.19.
     */
    public final int getMinimumVersion() { return VERSION_FOR_SIMPLEACTION; /* == 1119 */ }

    /**
     * Build a human-readable form of the message, with this class's field names
     * instead of generic SOCMessageTemplate4i names.
     * @return a human readable form of the message
     * @since 2.0.00
     */
    @Override
    public String toString()
    {
        return "SOCSimpleAction:game=" + game
            + "|pn=" + p1 + "|atype=" + p2
            + "|v1=" + p3 + "|v2=" + p4;
    }

}
