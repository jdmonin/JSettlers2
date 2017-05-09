/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017 Jeremy D Monin <jeremy@nand.net>
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
 * This message says which player has longest road.
 * Sent from server during joinGame.
 * During normal gameplay, "longest road" indicator at client is updated
 * by examining game state, not by messages from server.
 *
 * @author Robert S. Thomas
 */
public class SOCLongestRoad extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * The number of the player with longest road
     */
    private int playerNumber;

    /**
     * Create a LongestRoad message.
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     */
    public SOCLongestRoad(String ga, int pn)
    {
        messageType = LONGESTROAD;
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
     * @return the number of the player with longest road
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * LONGESTROAD sep game sep2 playerNumber
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber);
    }

    /**
     * LONGESTROAD sep game sep2 playerNumber
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     * @return the command string
     */
    public static String toCmd(String ga, int pn)
    {
        return LONGESTROAD + sep + ga + sep2 + pn;
    }

    /**
     * Parse the command String into a LONGESTROAD message.
     *
     * @param s   the String to parse
     * @return    a LONGESTROAD message, or null if the data is garbled
     */
    public static SOCLongestRoad parseDataStr(String s)
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

        return new SOCLongestRoad(ga, pn);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCLongestRoad:game=" + game + "|playerNumber=" + playerNumber;
    }
}
