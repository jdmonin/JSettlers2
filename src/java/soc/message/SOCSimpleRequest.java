/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2015 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;  // for javadocs only
import soc.game.SOCGameOption;  // for javadocs only

/**
 * This generic message handles a simple request from a client player in a game.
 * This is a way to add game actions without adding new SOCMessage subclasses.
 * It has a player number, a request type code, and two optional detail-value fields.
 *<P>
 * This message type would be useful for new functions that don't have a complicated
 * set of details attached.  If {@link SOCRollDiceRequest} or the Ask-Special-Build message types
 * were implemented today, they would add request types to this message type.
 *<UL>
 * <LI> Client sends to server: (pn, typecode, value1, value2). Unless the request type code's javadoc says otherwise,
 *      {@code pn} must be their own player number so that the server could announce the request using the same message
 *      type and parameters instead of defining another type.
 * <LI> If client can't do this request now, server responds to client only with: (pn = -1, typecode, value1b, value2b).
 *      The meaning of the response's optional {@code value1b} and {@code value2b} are typecode-specific and
 *      might not be the same as {@code value1} or {@code value2}.
 *      If the server is too old to understand this request type, it will respond with (pn = -1, typecode, 0, 0).
 * <LI> If client is permitted to do the request, server's response depends on the request type:
 *      Announce the client's request to all players in game,
 *      or take some game action and announce the results using other message types.
 *</UL>
 * Request type codes below 1000 are for general types that different kinds of games might be able to use. <BR>
 * Gametype-specific request types start at 1000.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.18
 * @see SOCSimpleAction
 */
public class SOCSimpleRequest extends SOCMessageTemplate4i
{
    private static final long serialVersionUID = 1118L;

    /**
     * The current player wants to attack their pirate fortress (scenario {@link SOCGameOption#K_SC_PIRI _SC_PIRI}).
     * Value1 and value2 are unused.  If client can attack, server responds with
     * {@link SOCPirateFortressAttackResult} and related messages (see that class' javadoc).
     * Otherwise, server responds with the standard SOCSimpleRequest denial:
     * (pn = -1, typecode {@code SC_PIRI_FORT_ATTACK}, 0, 0).
     *<P>
     * This request type is used in v2.0.00.  It's unused and unrecognized in 1.1.18,
     * but this field is declared there as an example and to reserve its number.
     * @since 1.1.18
     */
    public static final int SC_PIRI_FORT_ATTACK = 1000;

    /**
     * The current player wants to place a trade port they've been given.
     * This typically happens at some point after {@link SOCSimpleAction#TRADE_PORT_REMOVED}
     * in scenario {@link SOCGameOption#K_SC_FTRI _SC_FTRI}.
     *<P>
     * In state {@link SOCGame#PLACING_INV_ITEM}, player sends this with an edge coordinate where
     * they want to place the port. {@code value1} = the edge coordinate, value2 is unused.
     *<P>
     * If they can place there now, server will do so and broadcast the resulting game state ({@link SOCGame#PLAY1} or
     * {@link SOCGame#SPECIAL_BUILDING}), then broadcast a SOCSimpleRequest to the game with
     * {@code value1} = the placed port type, {@code value2} = edge.  All clients should call
     * {@link SOCGame#placePort(soc.game.SOCPlayer, int, int)}.
     *<P>
     * Otherwise, server responds with a SOCSimpleRequest declining the placement (pn = -1).
     * @since 2.0.00
     */
    public static final int TRADE_PORT_PLACE = 1001;

    // Reminder: If you add a request type, check client and server code to determine if the new type
    // should be added to methods such as:
    // - SOCGameHandler.handleSIMPLEREQUEST
    // - SOCPlayerClient.handleSIMPLEREQUEST
    // - SOCDisplaylessPlayerClient.handleSIMPLEREQUEST
    // - SOCRobotBrain.run case SOCMessage.SIMPLEREQUEST

    /**
     * Create a SOCSimpleRequest message.
     *
     * @param ga  the name of the game
     * @param pn  the requester's player number
     * @param reqtype  the request type; below 1000 is general, 1000+ is specific to one kind of game
     */
    public SOCSimpleRequest(final String ga, final int pn, final int reqtype)
    {
        this(ga, pn, reqtype, 0, 0);
    }

    /**
     * Create a SOCSimpleRequest message with a detail value.
     *
     * @param ga  the name of the game
     * @param pn  the requester's player number
     * @param reqtype  the request type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  Optional detail value, or 0
     */
    public SOCSimpleRequest(final String ga, final int pn, final int reqtype, final int value1)
    {
        this(ga, pn, reqtype, value1, 0);
    }

    /**
     * Create a SOCSimpleRequest message with 2 detail values.
     *
     * @param ga  the name of the game
     * @param pn  the requester's player number
     * @param reqtype  the request type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  First optional detail value, or 0
     * @param value2  Second optional detail value, or 0
     */
    public SOCSimpleRequest(final String ga, final int pn, final int reqtype, final int value1, final int value2)
    {
        super(SIMPLEREQUEST, ga, pn, reqtype, value1, value2);
    }

    /**
     * @return the player number
     */
    public int getPlayerNumber()
    {
        return p1;
    }

    /**
     * @return the request type
     */
    public int getRequestType()
    {
        return p2;
    }

    /**
     * @return the optional {@code value1} detail field
     */
    public int getValue1()
    {
        return p3;
    }

    /**
     * @return the optional {@code value2} detail field
     */
    public int getValue2()
    {
        return p4;
    }

    /**
     * {@link SOCMessage#SIMPLEREQUEST SIMPLEREQUEST} sep game sep2 playernumber sep2 reqtype sep2 value1 sep2 value2
     *
     * @param ga  the name of the game
     * @param pn  the requester's player number
     * @param reqtype  the request type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  First optional detail value, or 0
     * @param value2  Second optional detail value, or 0
     * @return the command string
     */
    public static String toCmd(final String ga, final int pn, final int reqtype, final int value1, final int value2)
    {
        return SOCMessageTemplate4i.toCmd(SIMPLEREQUEST, ga, pn, reqtype, value1, value2);
    }

    /**
     * Parse the command String into a SOCSimpleRequest message
     *
     * @param s   the String to parse: {@link SOCMessage#SIMPLEREQUEST SIMPLEREQUEST}
     *            sep game sep2 playernumber sep2 reqtype sep2 value1 sep2 value2
     * @return    a SOCSimpleRequest message, or {@code null} if the data is garbled
     */
    public static SOCSimpleRequest parseDataStr(final String s)
    {
        final String ga; // the game name
        final int pn;    // the player number
        final int rt;    // request type code
        final int v1;    // optional value1
        final int v2;    // optional value2

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            rt = Integer.parseInt(st.nextToken());
            v1 = Integer.parseInt(st.nextToken());
            v2 = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCSimpleRequest(ga, pn, rt, v1, v2);
    }

    /**
     * Minimum version where this message type is used.
     * SIMPLEREQUEST introduced in 1.1.18.
     * @return Version number, 1118 for JSettlers 1.1.18.
     */
    public int getMinimumVersion() { return 1118; }

}
