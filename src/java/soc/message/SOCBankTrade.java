/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.message;

import java.util.StringTokenizer;

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;


/**
 * This message means that a player wants to trade with the bank
 *
 * @author Robert S. Thomas
 */
@SuppressWarnings("serial")
public class SOCBankTrade extends SOCMessage
{
    /**
     * Name of game
     */
    private String game;

    /**
     * The set of resources being given to the bank
     */
    private SOCResourceSet give;

    /**
     * The set of resources being taken from the bank
     */
    private SOCResourceSet get;

    /**
     * Create a BankTrade message.
     *
     * @param ga   the name of the game
     * @param give the set of resources being given to the bank
     * @param get  the set of resources being taken from the bank
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
     * @return the set of resources being given to the bank
     */
    public SOCResourceSet getGiveSet()
    {
        return give;
    }

    /**
     * @return the set of resources being taken from the bank
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
     * @param give the set of resources being given to the bank
     * @param get  the set of resources being taken from the bank
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
     * @return    a BankTrade message, or null of the data is garbled
     */
    public static SOCBankTrade parseDataStr(String s)
    {
        String ga; // the game name
        SOCResourceSet give; // the set of resources being given to the bank
        SOCResourceSet get; // the set of resources being taken from the bank

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
