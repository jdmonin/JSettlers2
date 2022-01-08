/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2013-2014,2017,2020 Jeremy D Monin <jeremy@nand.net>
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
 * This message says that a player is changing their face icon.
 *<P>
 * Although this is a game-specific message, it's handled by {@code SOCServer} instead of a {@code GameHandler}.
 *
 * @author Robert S. Thomas
 */
public class SOCChangeFace extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * The player number changing their face, from server;
     * see {@link #getPlayerNumber()}.
     */
    private int playerNumber;

    /**
     * The id of the face image; see {@link #getFaceId()} for details
     */
    private int faceId;

    /**
     * Create a ChangeFace message.
     *
     * @param ga  the name of the game
     * @param pn  the player number changing their face; sent from server, always ignored when sent from client
     * @param id  the id of the face image;
     *            1 and higher are human face images, 0 is the default robot, -1 is the smarter robot.
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
     * The player number that is changing their face, when sent from server.
     * When sent from client, server has always ignored this field; could be any value.
     * @return the changing player number from server
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the id of the face image;
     * 1 and higher are human face images, 0 is the default robot, -1 is the smarter robot.
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
        return CHANGEFACE + sep + game + sep2 + playerNumber + sep2 + faceId;
    }

    /**
     * Parse the command String into a ChangeFace message
     *
     * @param s   the String to parse
     * @return    a ChangeFace message, or null if the data is garbled
     */
    public static SOCChangeFace parseDataStr(String s)
    {
        String ga; // the game name
        int pn; // the changing player number
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
