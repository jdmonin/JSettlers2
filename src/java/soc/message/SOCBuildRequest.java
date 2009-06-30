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
 * This message says which player the current player wants to
 * steal from.
 *
 * @author Robert S. Thomas
 */
public class SOCBuildRequest extends SOCMessage
{
    /**
     * Name of game
     */
    private String game;

    /**
     * The type of piece to build
     */
    private int pieceType;

    /**
     * Create a BuildRequest message.
     *
     * @param ga  the name of the game
     * @param pt  the type of piece to build
     */
    public SOCBuildRequest(String ga, int pt)
    {
        messageType = BUILDREQUEST;
        game = ga;
        pieceType = pt;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the type of piece to build
     */
    public int getPieceType()
    {
        return pieceType;
    }

    /**
     * BUILDREQUEST sep game sep2 pieceType
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, pieceType);
    }

    /**
     * BUILDREQUEST sep game sep2 pieceType
     *
     * @param ga  the name of the game
     * @param pt  the type of piece to build
     * @return the command string
     */
    public static String toCmd(String ga, int pt)
    {
        return BUILDREQUEST + sep + ga + sep2 + pt;
    }

    /**
     * Parse the command String into a BuildRequest message
     *
     * @param s   the String to parse
     * @return    a BuildRequest message, or null of the data is garbled
     */
    public static SOCBuildRequest parseDataStr(String s)
    {
        String ga; // the game name
        int pt; // the type of piece to build

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pt = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCBuildRequest(ga, pt);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCBuildRequest:game=" + game + "|pieceType=" + pieceType;
    }
}
