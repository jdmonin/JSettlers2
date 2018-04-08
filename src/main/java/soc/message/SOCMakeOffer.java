/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2010,2014,2017-2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.proto.Data;
import soc.proto.GameMessage;
import soc.proto.Message;

import java.util.StringTokenizer;


/**
 * This message means that a player wants to trade with other players
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
     *    making the offer. From client, value of {@code of.getFrom()} is ignored at server.
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

        for (int i = Data.ResourceType.CLAY_VALUE; i <= Data.ResourceType.WOOD_VALUE;
                i++)
        {
            cmd += (sep2 + give.getAmount(i));
        }

        SOCResourceSet get = of.getGetSet();

        for (int i = Data.ResourceType.CLAY_VALUE; i <= Data.ResourceType.WOOD_VALUE;
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

            /**
             * Note: this only works if SOCResourceConstants.CLAY == 1
             */
            for (int i = 1; i <= Data.ResourceType.WOOD_VALUE; i++)
            {
                give.setAmount(Integer.parseInt(st.nextToken()), i);
            }

            for (int i = 1; i <= Data.ResourceType.WOOD_VALUE; i++)
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

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        final int fromPN = offer.getFrom();
        final boolean[] toPN = offer.getTo();
        boolean toAll = true;
        for (int pn = 0; pn < toPN.length; ++pn)
        {
            if (pn == fromPN)
                continue;
            if (! toPN[pn])
            {
                toAll = false;
                break;
            }
        }

        GameMessage.TradeMakeOffer.Builder b
            = GameMessage.TradeMakeOffer.newBuilder()
                .setGive(ProtoMessageBuildHelper.toResourceSet(offer.getGiveSet()))
                .setGet(ProtoMessageBuildHelper.toResourceSet(offer.getGetSet()))
                .setFromPlayerNumber(fromPN);
        if (! toAll)
        {
            Data._IntArray.Builder iab = Data._IntArray.newBuilder();
            for (int pn = 0; pn < toPN.length; ++pn)
                if (toPN[pn])
                    iab.addArr(pn);
            b.setToPlayers(iab);
        }
        // TODO once server supports offerSerial field, set that if present
        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGaName(game).setTradeMakeOffer(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCMakeOffer:game=" + game + "|offer=" + offer;
    }

}
