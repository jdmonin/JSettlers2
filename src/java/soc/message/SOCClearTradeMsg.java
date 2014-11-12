/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010-2011,2014 Jeremy D Monin <jeremy@nand.net>
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
 * This message means that the server wants the trade message cleared.
 *<P>
 * Version 1.1.12: If <tt>playerNumber</tt> is -1, all players are clearing trade messages.
 *
 * @author Robert S. Thomas
 */
public class SOCClearTradeMsg extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1112L;  // last structural change v1.1.12

    /**
     * Minimum version (1.1.12) which supports playerNumber -1 for clear all.
     * @since 1.1.12
     */
    public static final int VERSION_FOR_CLEAR_ALL = 1112;

    /**
     * Name of game
     */
    private String game;

    /**
     * The seat number, or -1 to clear all seats
     */
    private int playerNumber;

    /**
     * Create a ClearTradeMsg message.
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     */
    public SOCClearTradeMsg(String ga, int pn)
    {
        messageType = CLEARTRADEMSG;
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
     * @return the seat number, or -1 to clear all seats
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * CLEARTRADEMSG sep game sep2 playerNumber
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber);
    }

    /**
     * CLEARTRADEMSG sep game sep2 playerNumber
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     * @return the command string
     */
    public static String toCmd(String ga, int pn)
    {
        return CLEARTRADEMSG + sep + ga + sep2 + pn;
    }

    /**
     * Parse the command String into a StartGame message
     *
     * @param s   the String to parse
     * @return    a StartGame message, or null of the data is garbled
     */
    public static SOCClearTradeMsg parseDataStr(String s)
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

        return new SOCClearTradeMsg(ga, pn);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCClearTradeMsg:game=" + game + "|playerNumber=" + playerNumber;
    }
}
