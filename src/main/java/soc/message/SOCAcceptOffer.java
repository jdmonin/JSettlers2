/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2013-2014,2016-2021 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;


/**
 * This message means that the player is accepting an offer.
 *<P>
 * Sent from accepting player's client to server.
 * If the trade is allowed, announced from server to all players.
 *<UL>
 * <LI> Client message to server is in response to a {@link SOCMakeOffer} announced earlier this turn
 *      with client as an offered-to player.
 * <LI> Server's response (announced to game) is {@link SOCPlayerElement}s to clients older than v2.5,
 *      then {@code SOCAcceptOffer}, {@link SOCGameTextMsg} to v1.x clients, then {@link SOCClearOffer}s.
 *</UL>
 *<P>
 * The server disallows any unacceptable trade by sending that "accepting" client a
 * {@code SOCRejectOffer} with reason code {@link SOCRejectOffer#REASON_CANNOT_MAKE_TRADE}.
 * Servers before v2.5.00 ({@link SOCRejectOffer#VERSION_FOR_REPLY_REASONS})
 * sent an explanatory {@link SOCGameServerText} instead.
 *<P>
 * In v2.5 and newer ({@link #VERSION_FOR_OMIT_PLAYERELEMENTS}),
 * the server's {@code SOCAcceptOffer} contains the resources being traded.
 *<P>
 * Only v1.x clients are sent the {@code SOCGameTextMsg}, which conveys the same info as this {@code SOCAcceptOffer}.
 * Before v2.5.00 the server announced {@code SOCGameTextMsg} before {@code SOCAcceptOffer}, instead of after.
 *<P>
 * Before v2.0.00 the server announced the {@code SOCClearOffer}s before {@code SOCAcceptOffer}. The old
 * non-robot clients ignored that {@code SOCAcceptOffer}, so changing the order has no effect on them.
 *
 * @author Robert S. Thomas
 * @see SOCRejectOffer
 */
public class SOCAcceptOffer extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2500;  // last structural change v2.5.00

    /**
     * Minimum version (2.5.00) where client uses this message's fields to update the players' resources,
     * and server doesn't accompany this message with {@link SOCPlayerElement}s.
     * @since 2.5.00
     */
    public static final int VERSION_FOR_OMIT_PLAYERELEMENTS = 2500;

    /**
     * Name of game
     */
    private final String game;

    /**
     * The accepting player number from server:
     * see {@link #getAcceptingNumber()}.
     * @see #resToAccepting
     */
    private int accepting;

    /**
     * The offering player number; see {@link #getOfferingNumber()}.
     * @see #resToOffering
     */
    private int offering;

    /**
     * The set of resources being given to {@link #accepting} player, if sent from server;
     * see {@link #getResToAcceptingPlayer()}.
     * @see #resToOffering
     * @since 2.5.00
     */
    private final SOCResourceSet resToAccepting;

    /**
     * The set of resources being given to {@link #offering} player, if sent from server;
     * see {@link #getResToOfferingPlayer()}.
     * @see #resToAccepting
     * @since 2.5.00
     */
    private final SOCResourceSet resToOffering;

    /**
     * Create an AcceptOffer message which doesn't include the resources traded.
     * Sent from client, or from server to clients older than v2.5.00 ({@link #VERSION_FOR_OMIT_PLAYERELEMENTS}).
     *
     * @param ga  the name of the game
     * @param ac  the player number of the accepting player;
     *     always ignored if sent from client.
     *     See {@link #getAcceptingNumber()}.
     * @param of  the player number of the offering player
     * @see #SOCAcceptOffer(String, int, int, SOCResourceSet, SOCResourceSet)
     */
    public SOCAcceptOffer(String ga, int ac, int of)
    {
        this(ga, ac, of, null, null);
    }

    /**
     * Create an AcceptOffer message from server which can include the resources traded.
     * Clients older than v2.5.00 ({@link #VERSION_FOR_OMIT_PLAYERELEMENTS}) ignore the resource fields when parsing.
     *
     * @param ga  the name of the game
     * @param ac  the player number of the accepting player;
     *     always ignored if sent from client.
     *     See {@link #getAcceptingNumber()}.
     * @param of  the player number of the offering player
     * @param toAc  Resources given to accepting player from offering player.
     *     Not sent if {@code null}. Clients older than v2.5.00 ignore this field.
     * @param toOf Resources given to offering player from accepting player.
     *     Not sent if {@code null}. Clients older than v2.5.00 ignore this field.
     * @throws IllegalArgumentException if one of {@code toAc} or {@code toOf} is null, but the other isn't
     * @see #SOCAcceptOffer(String, int, int)
     * @since 2.5.00
     */
    public SOCAcceptOffer(String ga, int ac, int of, SOCResourceSet toAc, SOCResourceSet toOf)
        throws IllegalArgumentException
    {
        if ((toAc == null) != (toOf == null))
            throw new IllegalArgumentException("toAc, toOf: inconsistent nulls");

        messageType = ACCEPTOFFER;
        game = ga;
        accepting = ac;
        offering = of;
        resToAccepting = toAc;
        resToOffering = toOf;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * When sent from server, get the player number accepting the trade offered by {@link #getOfferingNumber()}.
     * From client, server has always ignored this field; could be any value.
     * @return the number of the accepting player from server,
     *     or any value sent from client (server has always ignored this field)
     * @see #getResToAcceptingPlayer()
     */
    public int getAcceptingNumber()
    {
        return accepting;
    }

    /**
     * Get the player number offering this trade which is
     * being accepted by {@link #getAcceptingNumber()}.
     * @return the number of the offering player
     * @see #getResToOfferingPlayer()
     */
    public int getOfferingNumber()
    {
        return offering;
    }

    /**
     * In announcement from server, resources to be given to the {@link #getAcceptingNumber()} player
     * from the {@link #getOfferingNumber()} player. {@code null} from client or from servers
     * older than v2.5 ({@link #VERSION_FOR_OMIT_PLAYERELEMENTS}).
     *<P>
     * Earlier versions got the resource details by calling {@link soc.game.SOCPlayer#getCurrentOffer()} instead.
     *
     * @return  Resources to be given to accepting player, or {@code null} if not included in message
     * @see #getResToOfferingPlayer()
     * @since 2.5.00
     */
    public SOCResourceSet getResToAcceptingPlayer()
    {
        return resToAccepting;
    }

    /**
     * In announcement from server, resources to be given to the {@link #getOfferingNumber()} player
     * from the {@link #getAcceptingNumber()} player. {@code null} from client or from servers
     * older than v2.5 ({@link #VERSION_FOR_OMIT_PLAYERELEMENTS}).
     *<P>
     * Earlier versions got the resource details by calling {@link soc.game.SOCPlayer#getCurrentOffer()} instead.
     *
     * @return  Resources to be given to offering player, or {@code null} if not included in message
     * @see #getResToAcceptingPlayer()
     * @since 2.5.00
     */
    public SOCResourceSet getResToOfferingPlayer()
    {
        return resToOffering;
    }

    /**
     * ACCEPTOFFER sep game sep2 accepting sep2 offering
     *   [sep2 acceptClay sep2 ore sep2 sheep sep2 wheat sep2 acceptWood
     *    sep2 offerClay sep2 ore sep2 sheep sep2 wheat sep2 offerWood]
     *
     * @return the command string
     */
    public String toCmd()
    {
        StringBuffer cmd = new StringBuffer(ACCEPTOFFER + sep + game + sep2 + accepting + sep2 + offering);

        if (resToAccepting != null)
        {
            for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; ++i)
                cmd.append(sep2).append(resToAccepting.getAmount(i));

            for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; ++i)
                cmd.append(sep2).append(resToOffering.getAmount(i));
        }

        return cmd.toString();
    }

    /**
     * Parse the command String into an ACCEPTOFFER message.
     *
     * @param s   the String to parse
     * @return    an ACCEPTOFFER message, or null if the data is garbled
     */
    public static SOCAcceptOffer parseDataStr(String s)
    {
        String ga; // the game name
        int ac; // the number of the accepting player
        int of; //the number of the offering player
        SOCResourceSet toAc = null, toOf = null;  // optional: resources traded

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            ac = Integer.parseInt(st.nextToken());
            of = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens())
            {
                toAc = new SOCResourceSet();
                toOf = new SOCResourceSet();

                for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; i++)
                    toAc.setAmount(Integer.parseInt(st.nextToken()), i);
                for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; i++)
                    toOf.setAmount(Integer.parseInt(st.nextToken()), i);
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCAcceptOffer(ga, ac, of, toAc, toOf);
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * @param message Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.5.00
     */
    public static String stripAttribNames(String message)
    {
        // Strip any resource set labels and unknown=0 from the message, then do the normal strip

        message = message.replace("toAccepting=", "").replace("toOffering=", "").replaceAll("\\|unknown=0", "");
        return SOCMessage.stripAttribNames(message);
    }

    /**
     * Make a human-readable form of the message; omits resource-set fields if null.
     * Examples:
     *<UL>
     * <LI> {@code "SOCAcceptOffer:game=ga|accepting=2|offering=3"}
     * <LI> {@code "SOCAcceptOffer:game=ga|accepting=2|offering=3|toAccepting=clay=0|ore=0|sheep=2|wheat=0|wood=0|unknown=0|toOffering=clay=1|ore=0|sheep=0|wheat=0|wood=4|unknown=0"}
     *</UL>
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer
            ("SOCAcceptOffer:game=" + game + "|accepting=" + accepting + "|offering=" + offering);
        if (resToAccepting != null)
            sb.append("|toAccepting=").append(resToAccepting).append("|toOffering=").append(resToOffering);

        return sb.toString();
    }

}
