/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2011,2014 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
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
 **/
package soc.message;

import java.util.StringTokenizer;

/**
 * This debug message from client to server means that a player
 * is asking to place a piece on the board, without spending
 * resources or checking the current player.  The server will
 * send {@link SOCPutPiece} in reply.
 *<P>
 * When sent from server to client, the message is a generic message to
 * acknowledge that the "Free Placement" debug-mode has been turned on or off.
 * {@link #getCoordinates()} will return 1 for on, 0 for off.
 *<P>
 * Introduced in 1.1.12; check client version against {@link SOCDebugFreePlace#VERSION_FOR_DEBUGFREEPLACE}
 * before sending this message.
 *
 * @author Jeremy D Monin
 */
public class SOCDebugFreePlace extends SOCMessage
    implements SOCMessageForGame
{
    /** matches version (1.1.12) */
    private static final long serialVersionUID = 1112L;

    /**
     * Minimum version (1.1.12) of client/server which recognize
     * and send DEBUGFREEPLACE.
     */
    public static final int VERSION_FOR_DEBUGFREEPLACE = 1112;

    /**
     * the name of the game
     */
    private String game;

    /**
     * the type of piece being placed, such as {@link soc.game.SOCPlayingPiece#CITY}
     */
    private int pieceType;

    /**
     * the player number of who played the piece
     */
    private int playerNumber;

    /**
     * the coordinates of the piece
     */
    private int coordinates;

    /**
     * create a DEBUGFREEPLACE message from the client.
     *
     * @param na  name of the game
     * @param pt  type of playing piece, such as {@link soc.game.SOCPlayingPiece#CITY}
     * @param pn  player number
     * @param co  coordinates
     */
    public SOCDebugFreePlace(String na, int pn, int pt, int co)
    {
        messageType = DEBUGFREEPLACE;
        game = na;
        pieceType = pt;
        playerNumber = pn;
        coordinates = co;
    }

    /**
     * create a DEBUGFREEPLACE message from the server.
     * pieceType will be 0.
     * coordinate will be 1 for on, 0 for off.
     *
     * @param na  name of the game
     * @param pn  current player number
     * @param onOff  true for on, false for off
     */
    public SOCDebugFreePlace(String na, int pn, boolean onOff)
    {
	this(na, pn, 0, onOff ? 1 : 0);
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the type of playing piece, such as {@link soc.game.SOCPlayingPiece#CITY}
     */
    public int getPieceType()
    {
        return pieceType;
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
     * DEBUGFREEPLACE sep game sep2 playerNumber sep2 pieceType sep2 coordinates
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, pieceType, coordinates);
    }

    /**
     * Command string:
     *
     * DEBUGFREEPLACE sep game sep2 playerNumber sep2 pieceType sep2 coordinates
     *
     * @param na  the name of the game
     * @param pt  type of playing piece
     * @param pn  player number
     * @param co  coordinates
     * @return the command string
     */
    public static String toCmd(String na, int pn, int pt, int co)
    {
        return DEBUGFREEPLACE + sep + na + sep2 + pn + sep2 + pt + sep2 + co;
    }

    /**
     * parse the command string into a PutPiece message
     *
     * @param s   the String to parse
     * @return    a TextMsg message, or null of the data is garbled
     */
    public static SOCDebugFreePlace parseDataStr(String s)
    {
        String na; // name of the game
        int pn; // player number
        int pt; // type of piece
        int co; // coordinates

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            na = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            pt = Integer.parseInt(st.nextToken());
            co = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCDebugFreePlace(na, pn, pt, co);
    }

    /**
     * Minimum version where this message type is used.
     * DEBUGFREEPLACE introduced in 1.1.12 for debugging.
     * @return Version number, 1112 for JSettlers 1.1.12.
     * @see #VERSION_FOR_DEBUGFREEPLACE
     */
    public int getMinimumVersion() { return VERSION_FOR_DEBUGFREEPLACE; /* == 1112 */ }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCDebugFreePlace:game=" + game + "|playerNumber=" + playerNumber + "|pieceType=" + pieceType + "|coord=0x" + Integer.toHexString(coordinates);

        return s;
    }

}
