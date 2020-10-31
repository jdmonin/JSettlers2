/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2011,2014,2017,2020 Jeremy D Monin <jeremy@nand.net>
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
 * send {@link SOCPutPiece} in reply, or {@link SOCGameServerText}
 * if it declines the request.
 *<P>
 * A few scenario-specific conditions might cause server to decline the request;
 * see {@link soc.game.SOCPlayer#canPlaceShip_debugFreePlace(int)}.
 * Other conditions might cause server to send additional messages when replying
 * to a successful request, like {@link SOCInventoryItemAction} and
 * {@link SOCGameState}({@link soc.game.SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM})
 * for port placement in scenario {@link soc.game.SOCGameOptionSet#K_SC_FTRI SC_FTRI}.
 *<P>
 * If placement leads to Longest Route player changing, server sends that after its SOCPutPiece message:
 * {@link SOCGameElements}({@link SOCGameElements.GEType#LONGEST_ROAD_PLAYER LONGEST_ROAD_PLAYER}).
 *<P>
 * When sent from server to client, the message is a generic message to
 * acknowledge that the "Free Placement" debug-mode has been turned on or off.
 * {@link #getCoordinates()} is 1 for on, 0 for off.
 *<P>
 * Introduced in 1.1.12; check client version against {@link SOCDebugFreePlace#VERSION_FOR_DEBUGFREEPLACE}
 * before sending this message.
 *
 * @author Jeremy D Monin
 * @since 1.1.12
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
     * the coordinates of the piece; is >= 0
     */
    private int coordinates;

    /**
     * create a DEBUGFREEPLACE message from the client.
     *
     * @param na  name of the game
     * @param pt  type of playing piece, such as {@link soc.game.SOCPlayingPiece#CITY}; must be >= 0
     * @param pn  player number
     * @param co  coordinates; must be >= 0
     * @throws IllegalArgumentException if {@code pt} &lt; 0 or {@code co} &lt; 0
     */
    public SOCDebugFreePlace(String na, int pn, int pt, int co)
        throws IllegalArgumentException
    {
        if (pt < 0)
            throw new IllegalArgumentException("pt: " + pt);
        if (co < 0)
            throw new IllegalArgumentException("coord < 0");

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
     * @return the coordinates; is >= 0
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
     * @param pt  type of playing piece, such as {@link soc.game.SOCPlayingPiece#CITY}; must be >= 0
     * @param pn  player number
     * @param co  coordinates; must be >= 0
     * @return the command string
     * @throws IllegalArgumentException if {@code pt} &lt; 0 or {@code co} &lt; 0
     */
    public static String toCmd(String na, int pn, int pt, int co)
        throws IllegalArgumentException
    {
        if (pt < 0)
            throw new IllegalArgumentException("pt: " + pt);
        if (co < 0)
            throw new IllegalArgumentException("coord < 0");

        return DEBUGFREEPLACE + sep + na + sep2 + pn + sep2 + pt + sep2 + co;
    }

    /**
     * parse the command string into a DEBUGFREEPLACE message.
     *
     * @param s   the String to parse
     * @return    a DEBUGFREEPLACE message, or null if the data is garbled
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

            return new SOCDebugFreePlace(na, pn, pt, co);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Minimum version where this message type is used.
     * DEBUGFREEPLACE introduced in 1.1.12 for debugging.
     * @return Version number, 1112 for JSettlers 1.1.12.
     * @see #VERSION_FOR_DEBUGFREEPLACE
     */
    public int getMinimumVersion() { return VERSION_FOR_DEBUGFREEPLACE; /* == 1112 */ }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * Converts piece coordinate to decimal from hexadecimal format.
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.4.50
     */
    public static String stripAttribNames(String messageStrParams)
    {
        String s = SOCMessage.stripAttribNames(messageStrParams);
        if (s == null)
            return null;
        String[] pieces = s.split(SOCMessage.sep2);

        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < 3; i++)
            ret.append(pieces[i]).append(sep2_char);
        ret.append(Integer.parseInt(pieces[3].substring(2), 16));  // skip "0x"

        return ret.toString();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCDebugFreePlace:game=" + game + "|playerNumber=" + playerNumber + "|pieceType=" + pieceType + "|coord=0x" + Integer.toHexString(coordinates);

        return s;
    }

}
