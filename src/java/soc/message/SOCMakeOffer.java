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

import soc.game.SOCGame;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;


/**
 * This message means that a player wants to trade with other players
 *
 * @author Robert S. Thomas
 */
public class SOCMakeOffer extends SOCMessage
{
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
     * @param of   the offer being made
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
     * @param of   the offer being made
     */
    public static String toCmd(String ga, SOCTradeOffer of)
    {
        String cmd = MAKEOFFER + sep + ga;
        cmd += (sep2 + of.getFrom());

        boolean[] to = of.getTo();

        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
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
     * @return    a MakeOffer message, or null of the data is garbled
     */
    public static SOCMakeOffer parseDataStr(String s)
    {
        String ga; // the game name
        int from; // the number of the offering player
        boolean[] to; // the players to which this trade is offered
        SOCResourceSet give; // the set of resources being asked for 
        SOCResourceSet get; // the set of resources that the offerer wants in exchange

        to = new boolean[SOCGame.MAXPLAYERS];
        give = new SOCResourceSet();
        get = new SOCResourceSet();

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            from = Integer.parseInt(st.nextToken());

            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                to[i] = (Boolean.valueOf(st.nextToken())).booleanValue();
            }

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

        return new SOCMakeOffer(ga, new SOCTradeOffer(ga, from, to, give, get));
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCMakeOffer:game=" + game + "|offer=" + offer;
    }
}
