/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017-2018,2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.proto.GameMessage;
import soc.proto.Message;


/**
 * This message means that the player is rejecting all offers ("no thanks").
 *<P>
 * Sent from rejecting player's client to server.
 * The server then sends a copy of the message to all players
 * to announce the rejection.
 *<UL>
 * <LI> Message to server is in response to a {@link SOCMakeOffer} sent earlier this turn to client.
 * <LI> Followed by (from server, to all clients) {@link SOCRejectOffer} with the same data.
 *</UL>
 * @author Robert S. Thomas
 * @see SOCAcceptOffer
 */
public class SOCRejectOffer extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * From server, the player number rejecting all offers made to them; see {@link #getPlayerNumber()}.
     */
    private int playerNumber;

    /**
     * Create a RejectOffer message.
     *
     * @param ga  the name of the game
     * @param pn  the player number rejecting all offers made to them.
     *     Sent from server, always ignored when sent from client.
     */
    public SOCRejectOffer(String ga, int pn)
    {
        messageType = REJECTOFFER;
        game = ga;
        playerNumber = pn;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * The player number rejecting all offers made to them, when sent from server.
     * When sent from client, server has always ignored this field; could be any value.
     * @return the player number from server
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * REJECTOFFER sep game sep2 playerNumber
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber);
    }

    /**
     * REJECTOFFER sep game sep2 playerNumber
     *
     * @param ga  the name of the game
     * @param pn  the player number rejecting all offers made to them.
     *     Sent from server, always ignored when sent from client.
     * @return the command string
     */
    public static String toCmd(String ga, int pn)
    {
        return REJECTOFFER + sep + ga + sep2 + pn;
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

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCRejectOffer(ga, pn);
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.TradeRejectOffer.Builder b
            = GameMessage.TradeRejectOffer.newBuilder();
        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGameName(game).setPlayerNumber(playerNumber).setTradeRejectOffer(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCRejectOffer:game=" + game + "|playerNumber=" + playerNumber;
    }

}
