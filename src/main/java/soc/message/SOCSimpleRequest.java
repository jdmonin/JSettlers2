/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2015-2018 Jeremy D Monin <jeremy@nand.net>
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
import soc.proto.GameMessage;
import soc.proto.Message;

/**
 * This generic message handles a simple request from a client player in a game,
 * or a prompt from the server to a client player who needs to make a decision or take action.
 * This is a way to add game actions without adding new SOCMessage subclasses.
 * It has a player number, a request type code, and two optional detail-value fields.
 * The type code constants' javadocs detail usage of that type code.
 * Request types which are prompts from the server are declared with prefix {@code PROMPT_}.
 *<P>
 * To get the optional detail value fields from a {@code SOCSimpleRequest}, be sure to use {@link #getValue1()}
 * and {@link #getValue2()}, not {@link #getParam1()} and {@link #getParam2()} which would instead return the
 * player number and request type code.  {@link #getPlayerNumber()} and {@link #getRequestType()} are
 * convenience methods with more intuitive names to retrieve the player number and typecode.
 *<P>
 * This message type would be useful for new functions that don't have a complicated
 * set of details attached.  If {@link SOCRollDicePrompt} or the Ask-Special-Build message types
 * were implemented today, they would add request types to this message type.
 *
 *<H3>Request from Client to Server</H3>
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
 *
 *<H3>Prompt from Server to Client</H3>
 *<UL>
 * <LI> Server sends to client: (pn, typecode, value, value2). Some request type codes may send
 *      to all players in the game. Their javadocs will say so; otherwise, assume only a single client player
 *      receives the prompt message. If a client player receives such a "broadcast" {@code SOCSimpleRequest}
 *      having {@code pn} != their player number, no action is needed and the message is only to tell the game
 *      why everyone is waiting on {@code pn}.
 * <LI> Unless client is a bot, client UI indicates to the user they need to make a decision or take action
 * <LI> User makes their decision, responds to prompt seen in UI
 * <LI> Client sends to server the appropriate message(s) specified in {@code typecode} constant's javadoc
 *</UL>
 *
 *<H3>Request Type Code Number Ranges</H3>
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
     * This prompt from the server informs the client's player that
     * they must pick which resources they want from a gold hex.
     * {@code value1} = number of resources the client must pick.
     * Client should respond with {@link SOCPickResources}.
     *<P>
     * Also used in scenario {@link SOCGameOption#K_SC_PIRI SC_PIRI}
     * when player wins a free resource for defeating the
     * pirate fleet attack during a dice roll.
     *<P>
     * Same prompt/response pattern as {@link SOCDiscardRequest} / {@link SOCDiscard}.
     *<P>
     * v3 protobuf sends this prompt as {@link GameMessage.GainResources}.
     *
     * @since 2.0.00
     */
    public static final int PROMPT_PICK_RESOURCES = 1;

    /**
     * The current player wants to attack their pirate fortress (scenario {@link SOCGameOption#K_SC_PIRI _SC_PIRI}).
     * Value1 and value2 are unused.  If client can attack, server responds with
     * {@link SOCSimpleAction#SC_PIRI_FORT_ATTACK_RESULT} and related messages (see that type's javadoc).
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
     * {@code value1} = the placed port's edge, {@code value2} = port type.  All clients should call
     * {@link SOCGame#placePort(soc.game.SOCPlayer, int, int)}.
     *<P>
     * Otherwise, server responds with a SOCSimpleRequest declining the placement (pn = -1).
     * @since 2.0.00
     */
    public static final int TRADE_PORT_PLACE = 1001;

    // Reminder: If you add a request type, check client and server code to determine if the new type
    // should be added to methods such as:
    // - SOCGameMessageHandler.handleSIMPLEREQUEST
    // - SOCPlayerClient.handleSIMPLEREQUEST
    // - SOCDisplaylessPlayerClient.handleSIMPLEREQUEST
    // - SOCRobotBrain.run case SOCMessage.SIMPLEREQUEST (in 2 switches)

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
     * @param value1  Optional detail value, or 0.  Use {@link #getValue1()}, not {@link #getParam1()}, to get
     *     this value from a {@code SOCSimpleRequest} message.
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
     * @param value1  First optional detail value, or 0.  Use {@link #getValue1()}, not {@link #getParam1()}, to get
     *     this value from a {@code SOCSimpleRequest} message.
     * @param value2  Second optional detail value, or 0. Use {@link #getValue2()}, not {@link #getParam2()}, to get
     *     this value from a {@code SOCSimpleRequest} message.
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
     * @return the request's optional {@code value1} detail field
     */
    public int getValue1()
    {
        return p3;
    }

    /**
     * @return the request's optional {@code value2} detail field
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

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        switch (p2)
        {
        case PROMPT_PICK_RESOURCES:
            {
                GameMessage.GainResources.Builder b
                    = GameMessage.GainResources.newBuilder().setAmount(p3);
                GameMessage.GameMessageFromServer.Builder gb
                    = GameMessage.GameMessageFromServer.newBuilder();
                gb.setGameName(game).setGainResourcesPrompt(b);
                return Message.FromServer.newBuilder().setGameMessage(gb).build();
            }

        default:
            return null;
        }
    }

    /**
     * Minimum version where this message type is used.
     * SIMPLEREQUEST introduced in 1.1.18.
     * @return Version number, 1118 for JSettlers 1.1.18.
     */
    public int getMinimumVersion() { return 1118; }

    /**
     * Build a human-readable form of the message, with this class's field names
     * instead of generic SOCMessageTemplate4i names.
     * @return a human readable form of the message
     * @since 2.0.00
     */
    @Override
    public String toString()
    {
        return "SOCSimpleRequest:game=" + game
            + "|pn=" + p1 + "|rtype=" + p2
            + "|v1=" + p3 + "|v2=" + p4;
    }

}
