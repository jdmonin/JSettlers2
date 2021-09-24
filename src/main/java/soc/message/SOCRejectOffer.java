/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017-2018,2020-2021 Jeremy D Monin <jeremy@nand.net>
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
 * Typically this message means that the player is rejecting all offers ("no thanks").
 * Server can also send it with a "reason code" when rejecting a client's trade-related request
 * (player trade or bank trade).
 *
 *<H3>Typical usage: (without a reason code)</H3>
 *
 * Sent from rejecting player's client to server.
 * The server then sends a copy of the message to all players
 * to announce the rejection.
 *<UL>
 * <LI> Message to server is client player's response to a {@link SOCMakeOffer} sent earlier this turn.
 * <LI> Followed by (from server, to all clients) {@link SOCRejectOffer} with the same data.
 *</UL>
 *
 *<H3>With a reason code:</H3>
 *
 * Sent from server to a player's client in response to their bank trade, player trade offer,
 * or request to accept a trade, with the specific reason it was disallowed ({@link #REASON_NOT_YOUR_TURN},
 * {@link #REASON_CANNOT_MAKE_OFFER}, or generic {@link #REASON_CANNOT_MAKE_TRADE}).
 * See those constants for info about when they're used,
 * or {@link #getReasonCode()} for general info.
 *<P>
 * In a future version or fork, reason codes could also be sent from a rejecting player's client
 * to give a specific reason for the rejection. This would be mentioned in that reason code constant's
 * javadoc. Server would then announce the rejection to all players.
 *<P>
 * Reason code field is ignored by clients older than v2.5.00 ({@link #VERSION_FOR_REPLY_REASONS}).
 * Older clients are sent {@link SOCGameServerText} instead.
 *
 * @author Robert S. Thomas
 * @see SOCAcceptOffer
 */
public class SOCRejectOffer extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2500L;  // last structural change v2.5.00

    /**
     * Minimum server and client version number which uses reply/disallow reason codes
     * ({@link #REASON_CANNOT_MAKE_TRADE}, etc), which are ignored by older clients: 2500 for v2.5.00.
     * Older clients are sent {@link SOCGameServerText} instead.
     * @since 2.5.00
     */
    public static final int VERSION_FOR_REPLY_REASONS = 2500;

    /**
     * Server's generic reason code when the requesting client can't offer or accept this trade now
     * for whatever reason, or make this bank trade. Usually because they don't have the right resources to give.
     * See {@link #getReasonCode()} for more info.
     *<P>
     * Sent in response to {@link SOCBankTrade} and {@link SOCAcceptOffer}.
     * For a more specific reason, see {@link #REASON_NOT_YOUR_TURN}.
     *<P>
     * {@link #getPlayerNumber()} is -1 if rejecting a player's port/bank trade request,
     * otherwise is the client's player number.
     *<P>
     * Requires minimum version {@link #VERSION_FOR_REPLY_REASONS}.
     *
     * @see #REASON_CANNOT_MAKE_OFFER
     * @since 2.5.00
     */
    public static final int REASON_CANNOT_MAKE_TRADE = 1;

    /**
     * Server's reply reason code when the requesting client can't make this trade now
     * because it isn't their turn.
     *<P>
     * Sent in response to {@link SOCBankTrade}.
     *<P>
     * {@link #getPlayerNumber()} will be -1.
     *<P>
     * Requires minimum version {@link #VERSION_FOR_REPLY_REASONS}.
     *
     * @see #REASON_CANNOT_MAKE_TRADE
     * @since 2.5.00
     */
    public static final int REASON_NOT_YOUR_TURN = 2;

    /**
     * Server's reason code when the requesting client can't make this trade offer now.
     *<P>
     * Sent in response to {@link SOCMakeOffer}, but not {@link SOCBankTrade}.
     *<P>
     * Requires minimum version {@link #VERSION_FOR_REPLY_REASONS}.
     *
     * @see #REASON_CANNOT_MAKE_TRADE
     * @since 2.5.00
     */
    public static final int REASON_CANNOT_MAKE_OFFER = 3;

    /**
     * Name of game
     */
    private String game;

    /**
     * From server, the player number rejecting all offers made to them, or -1; see {@link #getPlayerNumber()}.
     */
    private int playerNumber;

    /**
     * Optional reason code for why an offer was rejected by server or declined by a player, or 0;
     * see {@link #getReasonCode()}.
     * @since 2.5.00
     */
    private int reasonCode;

    /**
     * Create a typical RejectOffer message without a reason code.
     *
     * @param ga  the name of the game
     * @param pn  the player number rejecting all offers made to them.
     *     Sent from server, always ignored when sent from client.
     * @see #SOCRejectOffer(String, int, int)
     */
    public SOCRejectOffer(String ga, int pn)
    {
        this(ga, pn, 0);
    }

    /**
     * Create a SOCRejectOffer message which may have a reason code.
     * Clients older than v2.5.00 will ignore that field.
     *
     * @param ga  the name of the game
     * @param pn  the player number rejecting offer(s), or -1 with some reason codes.
     *     Sent from server, always ignored when sent from client.
     *     Will be the client's own player number, unless otherwise noted in
     *     the {@code reasonCode} constant's javadoc
     * @param reasonCode  Reason code constant, or 0 for none; see {@link #getReasonCode()}
     * @see #SOCRejectOffer(String, int)
     * @since 2.5.00
     */
    public SOCRejectOffer(String ga, int pn, final int reasonCode)
    {
        messageType = REJECTOFFER;
        game = ga;
        playerNumber = pn;
        this.reasonCode = reasonCode;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * The player number rejecting all offers made to them, when sent from server;
     * can be -1 if {@link #getReasonCode()} != 0, for example when rejecting client's bank trade request.
     * When sent from client, server has always ignored this field; could be any value.
     * @return the player number from server
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * Optional reason code for why an offer or bank trade was rejected by server or declined by a player:
     * {@link #REASON_CANNOT_MAKE_TRADE}, {@link #REASON_CANNOT_MAKE_OFFER}, etc.
     *<P>
     * The standard reason code values are &gt; 0.
     * Values &lt; 0 can be used by third-party bots or forks.
     *<P>
     * Ignored by clients older than v2.5.00 ({@link #VERSION_FOR_REPLY_REASONS}).
     *
     * @return this message's Reason Code, or 0 if none
     * @since 2.5.00
     */
    public int getReasonCode()
    {
        return reasonCode;
    }

    /**
     * REJECTOFFER sep game sep2 playerNumber [sep2 reasonCode]
     *
     * @return the command string
     */
    public String toCmd()
    {
        return REJECTOFFER + sep + game + sep2 + playerNumber
           + ((reasonCode != 0) ? sep2 + reasonCode : "");
    }

    /**
     * Parse the command String into a REJECTOFFER message.
     *
     * @param s   the String to parse
     * @return    a REJECTOFFER message, or null if the data is garbled
     */
    public static SOCRejectOffer parseDataStr(String s)
    {
        String ga; // the game name
        int pn; // the seat number
        int rc = 0;  // the optional reason code

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens())
                rc = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCRejectOffer(ga, pn, rc);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCRejectOffer:game=" + game + "|playerNumber=" + playerNumber
            + ((reasonCode != 0) ? "|reasonCode=" + reasonCode : "");
    }

}
