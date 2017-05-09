/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010-2011,2013-2014,2017 Jeremy D Monin <jeremy@nand.net>
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

import java.util.StringTokenizer;


/**
 * This message means that a player wants to trade with the bank or a port.
 *<P>
 * To undo a bank trade in version 1.1.13 or higher, the player's client can
 * send another BANKTRADE message with the same resources but give/get swapped.
 * For instance, if they gave 3 sheep to get 1 brick, send a BANKTRADE
 * to give 1 brick to get 3 sheep.
 *
 * @author Robert S. Thomas
 */
public class SOCBankTrade extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1113L;  // last structural change v1.1.13

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
     * Create a BankTrade message.
     *
     * @param ga   the name of the game
     * @param give the set of resources being given to the bank/port
     * @param get  the set of resources being taken from the bank/port
     */
    public SOCBankTrade(String ga, SOCResourceSet give, SOCResourceSet get)
    {
        messageType = BANKTRADE;
        game = ga;
        this.give = give;
        this.get = get;
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
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, give, get);
    }

    /**
     * @return the command string
     *
     * @param ga  the name of the game
     * @param give the set of resources being given to the bank/port
     * @param get  the set of resources being taken from the bank/port
     */
    public static String toCmd(String ga, SOCResourceSet give, SOCResourceSet get)
    {
        String cmd = BANKTRADE + sep + ga;

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            cmd += (sep2 + give.getAmount(i));
        }

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            cmd += (sep2 + get.getAmount(i));
        }

        return cmd;
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
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCBankTrade(ga, give, get);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCBankTrade:game=" + game + "|give=" + give + "|get=" + get;
    }
}
