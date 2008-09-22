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
 * This message says that a player is changing the
 * face icon.
 *
 * @author Robert S. Thomas
 */
@SuppressWarnings("serial")
public class SOCChangeFace extends SOCMessage
{
    /**
     * Name of game
     */
    private String game;

    /**
     * The number player that is changing
     */
    private int playerNumber;

    /**
     * The id of the face image
     */
    private int faceId;

    /**
     * Create a ChangeFace message.
     *
     * @param ga  the name of the game
     * @param pn  the number of the changing player
     * @param id  the id of the face image
     */
    public SOCChangeFace(String ga, int pn, int id)
    {
        messageType = CHANGEFACE;
        game = ga;
        playerNumber = pn;
        faceId = id;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the number of changing player
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the id of the face image
     */
    public int getFaceId()
    {
        return faceId;
    }

    /**
     * CHANGEFACE sep game sep2 playerNumber sep2 faceId
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, faceId);
    }

    /**
     * CHANGEFACE sep game sep2 playerNumber sep2 faceId
     *
     * @param ga  the name of the game
     * @param pn  the number of the changing player
     * @param id  the id of the face image
     * @return the command string
     */
    public static String toCmd(String ga, int pn, int id)
    {
        return CHANGEFACE + sep + ga + sep2 + pn + sep2 + id;
    }

    /**
     * Parse the command String into a ChangeFace message
     *
     * @param s   the String to parse
     * @return    a ChangeFace message, or null of the data is garbled
     */
    public static SOCChangeFace parseDataStr(String s)
    {
        String ga; // the game name
        int pn; // the number of the changing player
        int id; // the id of the face image

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            id = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCChangeFace(ga, pn, id);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCChangeFace:game=" + game + "|playerNumber=" + playerNumber + "|faceId=" + faceId;
    }
}
