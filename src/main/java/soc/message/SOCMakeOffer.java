/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2010,2014,2017-2022 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
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

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;

import java.util.StringTokenizer;


/**
 * This message means that a player wants to trade with other players.
 *<P>
 * From client: A request to make or update a trade offer to 1 or more other players. <BR>
 * From server: A validated offer announced to the game. Will be followed immediately by
 * a {@link SOCClearTradeMsg} to clear responses from any previous offer.
 *<P>
 * If this trade offer is disallowed, server replies with a {@link SOCRejectOffer}
 * with reason {@link SOCRejectOffer#REASON_CANNOT_MAKE_OFFER}.
 * Clients and servers older than v2.5.00 ({@link SOCRejectOffer#VERSION_FOR_REPLY_REASONS})
 * use a {@link SOCGameServerText} to reject such offers.
 *
 * @author Robert S. Thomas
 */
public class SOCMakeOffer extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * The offer being made
     */
    private SOCTradeOffer offer;

    /**
     * Create a MakeOffer message.
     *
     * @param ga   the name of the game
     * @param of   the offer being made.
     *    From server, this offer's {@link SOCTradeOffer#getFrom()} is the player number
     *    making the offer: See {@link #getOffer()}.
     *    From client, value of {@code of.getFrom()} is ignored at server.
     *    Any unknown resources in {@code of} aren't sent over network, will always be 0 when received.
     */
    public SOCMakeOffer(String ga, SOCTradeOffer of)
    {
        messageType = MAKEOFFER;
        game = ga;
        offer = of;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * Get the offer being made.
     * From server, this offer's {@link SOCTradeOffer#getFrom()} is the player number
     * making the offer. From client, value of {@code getFrom()} is ignored at server.
     * @return the offer being made
     */
    public SOCTradeOffer getOffer()
    {
        return offer;
    }

    /**
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, offer);
    }

    /**
     * @return the command string
     *
     * @param ga  the name of the game
     * @param of   the offer being made.
     *    From server, this offer's {@link SOCTradeOffer#getFrom()} is the player number
     *    making the offer. From client, value of {@code of.getFrom()} is ignored at server.
     */
    public static String toCmd(String ga, SOCTradeOffer of)
    {
        String cmd = MAKEOFFER + sep + ga;
        cmd += (sep2 + of.getFrom());

        boolean[] to = of.getTo();

        for (int i = 0; i < to.length; i++)  // length should be == game.maxPlayers
        {
            cmd += (sep2 + to[i]);
        }

        SOCResourceSet give = of.getGiveSet();

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            cmd += (sep2 + give.getAmount(i));
        }

        SOCResourceSet get = of.getGetSet();

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            cmd += (sep2 + get.getAmount(i));
        }

        return cmd;
    }

    /**
     * Parse the command String into a MakeOffer message
     *
     * @param s   the String to parse
     * @return    a MakeOffer message, or null if the data is garbled
     */
    public static SOCMakeOffer parseDataStr(String s)
    {
        String ga; // the game name
        int from; // the number of the offering player
        boolean[] to; // the players to which this trade is offered
        SOCResourceSet give; // the set of resources being asked for
        SOCResourceSet get; // the set of resources that the offerer wants in exchange

        give = new SOCResourceSet();
        get = new SOCResourceSet();

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            from = Integer.parseInt(st.nextToken());
            final int numPlayerTokens = st.countTokens() - (2 * 5);  // Should be == game.maxPlayers
            to = new boolean[numPlayerTokens];

            for (int i = 0; i < numPlayerTokens; i++)
            {
                to[i] = (Boolean.valueOf(st.nextToken())).booleanValue();
            }

            for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; i++)
            {
                give.setAmount(Integer.parseInt(st.nextToken()), i);
            }

            for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; i++)
            {
                get.setAmount(Integer.parseInt(st.nextToken()), i);
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCMakeOffer(ga, new SOCTradeOffer(ga, from, to, give, get));
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
        final int offerFieldIdx = message.indexOf("|offer=game=");
        if (offerFieldIdx != -1)
        {
            // Format is from before v2.5; remove redundant offer= field
            final int nextIdx = message.indexOf('|', offerFieldIdx + 11);
            if (nextIdx == -1)
                return null;

            message = message.substring(0, offerFieldIdx) + message.substring(nextIdx);
        }

        // Strip give=, get=, unknown= from the message
        message = message.replace("give=", "");
        message = message.replace("get=", "");
        // strip with leading delim (hardcode here for now)
        message = message.replaceAll("\\|unknown=0", "");

        return SOCMessage.stripAttribNames(message);
    }

    /**
     * Make a human-readable form of the message:<BR>
     * <tt>"SOCMakeOffer:game=ga|from=3|to=false,false,true,false|give=clay=0|ore=1|sheep=0|wheat=1|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0"</tt><BR>
     *<P>
     * Before v2.5.00, this called {@link SOCTradeOffer#toString()} which included redundant "offer" field:<BR>
     * <tt>"SOCMakeOffer:game=ga|offer=game=ga|from=3|to=false,false,true,false|give=..."</tt>
     *
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCMakeOffer:game=" + game + '|' + offer.toString(true);
    }

}
