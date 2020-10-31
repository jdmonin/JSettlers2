/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010-2011,2013-2014,2017-2018,2020 Jeremy D Monin <jeremy@nand.net>
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
 * The server disallows any unacceptable trade by sending the client a
 * {@code SOCBankTrade} with a reason code &lt; 0 like {@link #PN_REPLY_NOT_YOUR_TURN}.
 * Servers before v2.4.50 ({@link #VERSION_FOR_REPLY_REASONS}) disallowed by
 * sending an explanatory {@link SOCGameServerText}.
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

    /**
     * Minimum server and client version number which uses reply/disallow reason codes
     * ({@link #PN_REPLY_CANNOT_MAKE_TRADE}, etc), which are always &lt; 0: 2450 for v2.4.50.
     * @since 2.4.50
     */
    public static final int VERSION_FOR_REPLY_REASONS = 2450;

    /**
     * Server's reply reason code that the requesting client can't make this trade now
     * for whatever reason. Usually because they don't have the right resources to give.
     * Also used by {@link SOCMakeOffer} and {@link SOCAcceptOffer}.
     * For a more specific reason, see {@link #PN_REPLY_NOT_YOUR_TURN}.
     * Requires minimum version {@link #VERSION_FOR_REPLY_REASONS}.
     * @since 2.4.50
     */
    public static final int PN_REPLY_CANNOT_MAKE_TRADE = -2;

    /**
     * Server's reply reason code that the requesting client can't make this trade now
     * because it isn't their turn.
     * Requires minimum version {@link #VERSION_FOR_REPLY_REASONS}.
     * @see #PN_REPLY_CANNOT_MAKE_TRADE
     * @since 2.4.50
     */
    public static final int PN_REPLY_NOT_YOUR_TURN = -3;

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
     * From server, player number who made the bank trade,
     * or the disallowing reason the trade could not occur: See {@link #getPlayerNumber()}.
     * Ignored from client (use -1 to not send this field).
     * @since 2.0.00
     */
    private final int playerNumber;

    /**
     * Create a BankTrade message.
     *
     * @param ga   the name of the game
     * @param give the set of resources being given to the bank/port: see {@link #getGiveSet()
     * @param get  the set of resources being taken from the bank/port: see {@link #getGetSet()}
     * @param pn   the player number making the trade,
     *     or server's reason the trade could not occur (see {@link #getPlayerNumber()}),
     *     or -1 to send a request from client.
     *     Not sent if -1. Versions older than 2.0.00 ignore this field.
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
     * Get the set of resources being given by the player to the bank or port;
     * unused/{@link SOCResourceSet#EMPTY_SET} if this is a server reply with a
     * reason code like {@link #PN_REPLY_CANNOT_MAKE_TRADE}.
     * @return the set of resources being given
     */
    public SOCResourceSet getGiveSet()
    {
        return give;
    }

    /**
     * Get the set of resources being received by the player from the bank or port;
     * unused/{@link SOCResourceSet#EMPTY_SET} if this is a server reply with a
     * reason code like {@link #PN_REPLY_CANNOT_MAKE_TRADE}.
     * @return the set of resources being taken
     */
    public SOCResourceSet getGetSet()
    {
        return get;
    }

    /**
     * In a message from server, get the player number who made the bank trade,
     * or server's disallowing reason the trade could not occur: a value &lt; 0 such as
     * {@link #PN_REPLY_CANNOT_MAKE_TRADE} or {@link #PN_REPLY_NOT_YOUR_TURN}.
     * In a message from client, get -1 because this field isn't set.
     * Versions older than 2.0.00 ignore this field.
     * Versions older than 2.4.50 don't recognize reply reason codes (&lt; -1).
     * @return the player number or negative reason code (message from server),
     *     or -1 if not set (message from client).
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * Build a command string to send this message. {@code playerNumber} is not sent if -1.
     * @return the command string
     */
    public String toCmd()
    {
        StringBuilder cmd = new StringBuilder(BANKTRADE + sep + game);

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            cmd.append(sep2);
            cmd.append(give.getAmount(i));
        }

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            cmd.append(sep2);
            cmd.append(get.getAmount(i));
        }

        if (playerNumber != -1)
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
            for (int i = 1; i <= SOCResourceConstants.WOOD; i++)
            {
                give.setAmount(Integer.parseInt(st.nextToken()), i);
            }

            for (int i = 1; i <= SOCResourceConstants.WOOD; i++)
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

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * @param message Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.4.50
     */
    public static String stripAttribNames(String message)
    {
        // Strip the give= and get= from the message, then do the normal strip
        message = message.replace("give=", "");
        message = message.replace("get=", "");
        // strip with leading delim (hardcode here for now)
        message = message.replaceAll("\\|unknown=0", "");

        return SOCMessage.stripAttribNames(message);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCBankTrade:game=" + game + "|give=" + give + "|get=" + get
            + ((playerNumber != -1) ? ("|pn=" + playerNumber) : "");
    }

}
