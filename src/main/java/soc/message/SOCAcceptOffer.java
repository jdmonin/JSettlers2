/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2013-2014,2016-2018 Jeremy D Monin <jeremy@nand.net>
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
 * This message means that the player is accepting an offer.
 *<P>
 * Sent from accepting player's client to server.
 * If the trade is allowed, announced from server to all players.
 *<UL>
 * <LI> Message to server is in response to a {@link SOCMakeOffer} sent earlier this turn to client.
 * <LI> Server's response (announced to game) is {@link SOCPlayerElement}s, {@link SOCGameServerText},
 *      then the {@code AcceptOffer}, then {@link SOCClearOffer}s.
 *</UL>
 *<P>
 * Before v2.0.00 the server announced the {@code SOCClearOffer}s before {@code SOCAcceptOffer}. The old
 * non-robot clients ignored that {@code SOCAcceptOffer}, so changing the order has no effect on them.
 * @author Robert S. Thomas
 * @see SOCRejectOffer
 */
public class SOCAcceptOffer extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * The number of the accepting player.
     * Sent from server, ignored if sent from client.
     */
    private int accepting;

    /**
     * The number of the offering player
     */
    private int offering;

    /**
     * Create a AcceptOffer message.
     *
     * @param ga  the name of the game
     * @param ac  the player number of the accepting player.
     *     Sent from server, ignored if sent from client.
     * @param of  the player number of the offering player
     */
    public SOCAcceptOffer(String ga, int ac, int of)
    {
        messageType = ACCEPTOFFER;
        game = ga;
        accepting = ac;
        offering = of;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * Get the player number accepting the trade offer
     * from {@link #getOfferingNumber()}.
     * @return the number of the accepting player from server,
     *     or any value sent from client (not used by server)
     */
    public int getAcceptingNumber()
    {
        return accepting;
    }

    /**
     * Get the player number offering this trade which is
     * being accepted by {@link #getAcceptingNumber()}.
     * @return the number of the offering player
     */
    public int getOfferingNumber()
    {
        return offering;
    }

    /**
     * ACCEPTOFFER sep game sep2 accepting sep2 offering
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, accepting, offering);
    }

    /**
     * ACCEPTOFFER sep game sep2 accepting sep2 offering
     *
     * @param ga  the name of the game
     * @param ac  the player number of the accepting player.
     *     Sent from server, ignored if sent from client.
     * @param of  the player number of the offering player
     * @return the command string
     */
    public static String toCmd(String ga, int ac, int of)
    {
        return ACCEPTOFFER + sep + ga + sep2 + ac + sep2 + of;
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

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            ac = Integer.parseInt(st.nextToken());
            of = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCAcceptOffer(ga, ac, of);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCAcceptOffer:game=" + game + "|accepting=" + accepting + "|offering=" + offering;
    }
}
