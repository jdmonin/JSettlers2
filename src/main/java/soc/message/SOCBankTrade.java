/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010-2011,2013-2014,2017-2018 Jeremy D Monin <jeremy@nand.net>
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
import soc.proto.Data;
import soc.proto.GameMessage;
import soc.proto.Message;
import soc.util.SOCStringManager;  // javadocs only

import java.util.StringTokenizer;


/**
 * This request to server means that a player wants to trade with the bank or a port.
 * Or, an info announcement from server of a successful bank/port trade (version 2.0.00 or higher)
 * sent after the {@link SOCPlayerElement} messages which announce the resource changes.
 * Clients older than v2.0.00 ignore this message from server; use {@link SOCStringManager#VERSION_FOR_I18N}.
 *<P>
 * If the client's trade request is acceptable, server responds to entire game with {@link SOCPlayerElement}s:
 * A {@link SOCPlayerElement#LOSE} for each resource type being traded in,
 * then {@link SOCPlayerElement#GAIN} for those given to the player.
 * Clients v2.0.00 or higher are sent a {@code SOCBankTrade} to announce the trade,
 * older clients are sent a {@link SOCGameTextMsg} instead.
 *<P>
 * The server declines any unacceptable trade by sending the client an explanatory {@link SOCGameServerText}.
 *<P>
 * To undo a bank trade in version 1.1.13 or higher, the player's client can
 * send another BANKTRADE message with the same resources but give/get swapped.
 * For instance, if they gave 3 sheep to get 1 brick, undo by sending a BANKTRADE
 * to give 1 brick to get 3 sheep.
 *
 * @author Robert S. Thomas
 */
public class SOCBankTrade extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Name of game
     */
    private String game;

    /**
     * The set of resources being given to the bank/port
     */
    private SOCResourceSet give;

    /**
     * The set of resources being taken from the bank/port
     */
    private SOCResourceSet get;

    /**
     * From server, player number who made the bank trade.
     * Ignored from client (use -1 to not send this field).
     * @since 2.0.00
     */
    private final int playerNumber;

    /**
     * Create a BankTrade message.
     *
     * @param ga   the name of the game
     * @param give the set of resources being given to the bank/port
     * @param get  the set of resources being taken from the bank/port
     * @param pn   the player number making the trade, or -1 for request from client.
     *     Not sent if &lt; 0. Versions older than 2.0.00 ignore this field.
     */
    public SOCBankTrade(String ga, SOCResourceSet give, SOCResourceSet get, final int pn)
    {
        messageType = BANKTRADE;
        game = ga;
        this.give = give;
        this.get = get;
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
     * @return the set of resources being given to the bank or port
     */
    public SOCResourceSet getGiveSet()
    {
        return give;
    }

    /**
     * @return the set of resources being taken from the bank or port
     */
    public SOCResourceSet getGetSet()
    {
        return get;
    }

    /**
     * @return the player number who made the bank trade (message from server),
     *     or -1 if not set (message from client). Versions older than 2.0.00 ignore this field.
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * Build a command string to send this message. {@code playerNumber} is sent only if &gt;= 0.
     * @return the command string
     */
    public String toCmd()
    {
        StringBuilder cmd = new StringBuilder(BANKTRADE + sep + game);

        for (int i = Data.ResourceType.CLAY_VALUE; i <= Data.ResourceType.WOOD_VALUE;
             i++)
        {
            cmd.append(sep2);
            cmd.append(give.getAmount(i));
        }

        for (int i = Data.ResourceType.CLAY_VALUE; i <= Data.ResourceType.WOOD_VALUE;
                i++)
        {
            cmd.append(sep2);
            cmd.append(get.getAmount(i));
        }

        if (playerNumber >= 0)
        {
            cmd.append(sep2);
            cmd.append(playerNumber);
        }

        return cmd.toString();
    }

    /**
     * Parse the command String into a BankTrade message
     *
     * @param s   the String to parse
     * @return    a BankTrade message, or null if the data is garbled
     */
    public static SOCBankTrade parseDataStr(String s)
    {
        String ga; // the game name
        SOCResourceSet give;  // the set of resources being given to the bank/port
        SOCResourceSet get;   // the set of resources being taken from the bank/port
        int pn = -1;  // player number if sent

        give = new SOCResourceSet();
        get = new SOCResourceSet();

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();

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

            if (st.hasMoreTokens())
                pn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCBankTrade(ga, give, get, pn);
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.TradeWithBank.Builder b
            = GameMessage.TradeWithBank.newBuilder()
                .setGive(ProtoMessageBuildHelper.toResourceSet(give))
                .setGet(ProtoMessageBuildHelper.toResourceSet(get));
        if (playerNumber >= 0)
            b.setPlayerNumber(playerNumber);
        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGaName(game).setTradeWithBank(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCBankTrade:game=" + game + "|give=" + give + "|get=" + get
            + ((playerNumber >= 0) ? ("|pn=" + playerNumber) : "");
    }

}
