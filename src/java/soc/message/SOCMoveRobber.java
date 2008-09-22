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


/**
 * This message means that a player wants to move the robber
 *
 * @author Robert S Thomas
 */
@SuppressWarnings("serial")
public class SOCMoveRobber extends SOCMessage
{
    /**
     * the name of the game
     */
    private String game;

    /**
     * the number of the player moving the robber
     */
    private int playerNumber;

    /**
     * the coordinates of the piece
     */
    private int coordinates;

    /**
     * create a MoveRobber message
     *
     * @param na  name of the game
     * @param pn  player number
     * @param co  coordinates
     */
    public SOCMoveRobber(String na, int pn, int co)
    {
        messageType = MOVEROBBER;
        game = na;
        playerNumber = pn;
        coordinates = co;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the player number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the coordinates
     */
    public int getCoordinates()
    {
        return coordinates;
    }

    /**
     * Command string:
     *
     * MOVEROBBER sep game sep2 playerNumber sep2 coordinates
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, coordinates);
    }

    /**
     * Command string:
     *
     * MOVEROBBER sep game sep2 playerNumber sep2 coordinates
     *
     * @param na  the name of the game
     * @param pn  player number
     * @param co  coordinates
     * @return the command string
     */
    public static String toCmd(String na, int pn, int co)
    {
        return MOVEROBBER + sep + na + sep2 + pn + sep2 + co;
    }

    /**
     * parse the command string into a MoveRobber message
     *
     * @param s   the String to parse
     * @return    a TextMsg message, or null of the data is garbled
     */
    public static SOCMoveRobber parseDataStr(String s)
    {
        String na; // name of the game
        int pn; // player number
        int co; // coordinates

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            na = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            co = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCMoveRobber(na, pn, co);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCMoveRobber:game=" + game + "|playerNumber=" + playerNumber + "|coord=" + Integer.toHexString(coordinates);

        return s;
    }
}
