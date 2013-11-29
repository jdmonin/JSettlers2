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

/**
 * This generic message from the server to clients handles a simple action or event for a client player in a game.
 * This is a way to add game actions and events without adding new SOCMessage subclasses.
 * It has a player number, an action type code, and two optional detail-value fields.
 * This message comes after, not before, any messages that update the game and player data for the action.
 *<P>
 * This message type is useful for functions that don't have a complicated set of
 * details attached, such as telling all players that someone has bought a development card,
 * or telling a bot that it's made a successful bank/port trade, or some event or condition just happened.
 *<P>
 * It can also be used to prompt the player that they may or must take some action at this time.
 * Action types that do this have {@code MAY_} or {@code MUST_} in their name.
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
     * Send older clients {@link SOCGameServerText} or other appropriate messages instead.
     */
    public static final int VERSION_FOR_SIMPLEACTION = 1119;

    /**
     * The current player has bought a development card.
     * {@code value1} is the number of cards remaining to be bought, value2 is unused.
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
     * The current player has removed a trade port from the board.
     * {@code value1} is the former port's edge coordinate, {@code value2} is the port type.
     * Sent to entire game.  If the player must place the port immediately, server will soon send
     * {@link SOCGameState}({@link soc.game.SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM}) among other messages.
     *<P>
     * When the player wants to place the removed port, they will send {@link SOCSimpleRequest#TRADE_PORT_PLACE}
     * with their chosen location.  If the placement is allowed, the server will broadcast a similar
     * {@link SOCSimpleRequest#TRADE_PORT_PLACE} to the game; see that javadoc for details.
     * @since 2.0.00
     */
    public static final int TRADE_PORT_REMOVED = 1001;

    /**
     * Create a SOCSimpleAction message.
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on
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
     * @param pn  the player acting or acted on
     * @param acttype  the action type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  Optional detail value, or 0
     */
    public SOCSimpleAction(final String ga, final int pn, final int acttype, final int value1)
    {
        this(ga, pn, acttype, value1, 0);
    }

    /**
     * Create a SOCSimpleAction message with 2 detail values.
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on
     * @param acttype  the action type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  First optional detail value, or 0
     * @param value2  Second optional detail value, or 0
     */
    public SOCSimpleAction(final String ga, final int pn, final int acttype, final int value1, final int value2)
    {
        super(SIMPLEACTION, ga, pn, acttype, value1, value2);
    }

    /**
     * @return the player number acting or acted on
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
     * @return the optional {@code value1} detail field
     */
    public final int getValue1()
    {
        return p3;
    }

    /**
     * @return the optional {@code value2} detail field
     */
    public final int getValue2()
    {
        return p4;
    }

    /**
     * {@link SOCMessage#SIMPLEACTION SIMPLEACTION} sep game sep2 playernumber sep2 acttype sep2 value1 sep2 value2
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on
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
        final int pn;    // the player number
        final int at;    // actionj type code
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
    public final int getMinimumVersion()
    {
        return VERSION_FOR_SIMPLEACTION;
    }

}
