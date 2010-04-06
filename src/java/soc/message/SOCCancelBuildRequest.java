/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Some documentation paragraphs in this file Copyright (C) 2007,2010 Jeremy D Monin <jeremy@nand.net>
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
 *  This message type has three meanings, depending on game state and direction of send. 
 *<P>
 * - When sent from client to server, CANCELBUILDREQUEST means the player has changed
 *   their mind about spending resources to build a piece.  Only allowed during normal
 *   game play (PLACING_ROAD, PLACING_SETTLEMENT, or PLACING_CITY).
 *<P>
 *  When sent from server to client:
 *<P>
 * - During game startup (START1B or START2B): <BR>
 *       Sent from server, CANCELBUILDREQUEST means the current player
 *       wants to undo the placement of their initial settlement.  
 *<P>
 * - During piece placement (PLACING_ROAD, PLACING_CITY, PLACING_SETTLEMENT,
 *                           PLACING_FREE_ROAD1 or PLACING_FREE_ROAD2): <BR>
 *      Sent from server, CANCELBUILDREQUEST means the player has sent
 *      an illegal PUTPIECE (bad building location). Humans can probably
 *      decide a better place to put their road, but robots must cancel
 *      the build request and decide on a new plan. <BR>
 *<P>
 *  This can also be the reply if the client sends an illegal BUILDREQUEST
 *      (no resources, not the right game state, etc.).
 *      In that case it's sent only to robot clients, not to humans.
 *      (Humans get a textual error message, and can understand that instead.)
 *
 * @author Robert S. Thomas
 */
public class SOCCancelBuildRequest extends SOCMessage
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
     * Create a CancelBuildRequest message.
     *
     * @param ga  the name of the game
     * @param pt  the type of piece to build
     */
    public SOCCancelBuildRequest(String ga, int pt)
    {
        messageType = CANCELBUILDREQUEST;
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
     * CANCELBUILDREQUEST sep game sep2 pieceType
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, pieceType);
    }

    /**
     * CANCELBUILDREQUEST sep game sep2 pieceType
     *
     * @param ga  the name of the game
     * @param pt  the type of piece to build
     * @return the command string
     */
    public static String toCmd(String ga, int pt)
    {
        return CANCELBUILDREQUEST + sep + ga + sep2 + pt;
    }

    /**
     * Parse the command String into a CancelBuildRequest message
     *
     * @param s   the String to parse
     * @return    a CancelBuildRequest message, or null of the data is garbled
     */
    public static SOCCancelBuildRequest parseDataStr(String s)
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

        return new SOCCancelBuildRequest(ga, pt);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCCancelBuildRequest:game=" + game + "|pieceType=" + pieceType;
    }
}
