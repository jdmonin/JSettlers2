/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010 Jeremy D Monin <jeremy@nand.net>
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


/**
 * This message has the total resource count for a player
 *
 * @author Robert S. Thomas
 */
public class SOCResourceCount extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * The seat number
     */
    private int playerNumber;

    /**
     * The resource count
     */
    private int count;

    /**
     * Create a ResourceCount message.
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     * @param rc  the resource count
     */
    public SOCResourceCount(String ga, int pn, int rc)
    {
        messageType = RESOURCECOUNT;
        game = ga;
        playerNumber = pn;
        count = rc;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the seat number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the recource count
     */
    public int getCount()
    {
        return count;
    }

    /**
     * RESOURCECOUNT sep game sep2 playerNumber sep2 count
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, count);
    }

    /**
     * RESOURCECOUNT sep game sep2 playerNumber sep2 count
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     * @param rc  the resource count
     * @return the command string
     */
    public static String toCmd(String ga, int pn, int rc)
    {
        return RESOURCECOUNT + sep + ga + sep2 + pn + sep2 + rc;
    }

    /**
     * Parse the command String into a ResourceCount message
     *
     * @param s   the String to parse
     * @return    a ResourceCount message, or null of the data is garbled
     */
    public static SOCResourceCount parseDataStr(String s)
    {
        String ga; // the game name
        int pn; // the seat number
        int rc; // the resource count

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            rc = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCResourceCount(ga, pn, rc);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCResourceCount:game=" + game + "|playerNumber=" + playerNumber + "|count=" + count;
    }
}
