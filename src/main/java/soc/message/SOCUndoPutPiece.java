/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2022 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCPlayingPiece;  // for javadocs only

/**
 * Client player is asking to undo, or server is announcing undo of, moving or placing a piece on the board
 * ({@link SOCMovePiece} or {@link SOCPutPiece}).
 *
 *<H3>Request from Client to Server</H3>
 *<UL>
 * <LI> Client sends to server: {@code SOCUndoPutPiece}(pieceType, coordinates)
 * <LI> If undo is allowed, server announces {@code SOCUndoPutPiece} to the game along with any related messages:
 *      See below.
 * <LI> Otherwise server replies with {@code SOCUndoPutPiece}(pn = -1).
 *</UL>
 *
 *<H3>Announcement from Server</H3>
 *<UL>
 * <LI> Any preceding messages? TBD. (Un-close ship routes, return pieces, etc)
 * <LI> Server sends {@code SOCUndoPutPiece}(pn, pieceType, coordinates) to all clients in the game
 * <LI> Any following messages? TBD. (Un-close ship routes, return pieces, etc)
 * <LI> Client UI should update at this point and announce the undo.
 *</UL>
 *
 * See also {@link SOCCancelBuildRequest} and {@link SOCRemovePiece}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.7.00
 */
public class SOCUndoPutPiece extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2700L;  // no structural change yet; created v2.7.00

    /**
     * Name of the game
     */
    private final String game;

    /**
     * Type of piece being placed, such as {@link SOCPlayingPiece#CITY}
     */
    private final int pieceType;

    /**
     * Player number for this undo, or -1 for server decline replies.
     * Sent from server, ignored if sent from client.
     */
    private final int playerNumber;

    /**
     * the coordinates of the piece; must be &gt;= 0
     */
    private final int coordinates;

    /**
     * create a {@link SOCUndoPutPiece} message.
     *
     * @param gn  name of the game
     * @param pt  type of playing piece, such as {@link SOCPlayingPiece#CITY}; must be >= 0
     * @param pn  player number, or -1 for server decline replies.
     *     Sent from server, ignored if sent from client.
     * @param co  coordinates; must be &gt;= 0
     * @throws IllegalArgumentException if {@code pt} &lt; 0 or {@code co} &lt; 0
     */
    public SOCUndoPutPiece(String gn, int pn, int pt, int co)
        throws IllegalArgumentException
    {
        if (pt < 0)
            throw new IllegalArgumentException("pt: " + pt);
        if (co < 0)
            throw new IllegalArgumentException("coord < 0: " + co);

        messageType = UNDOPUTPIECE;
        game = gn;
        pieceType = pt;
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
     * @return the type of playing piece, such as {@link SOCPlayingPiece#CITY}
     */
    public int getPieceType()
    {
        return pieceType;
    }

    /**
     * @return the player number from server, or any value sent from client (not used by server)
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the coordinates; is &gt;= 0
     */
    public int getCoordinates()
    {
        return coordinates;
    }

    /**
     * Command string:
     *
     * UNDOPUTPIECE sep game sep2 playerNumber sep2 pieceType sep2 coordinates
     *
     * @return the command string
     */
    public String toCmd()
    {
        return UNDOPUTPIECE + sep + game + sep2 + playerNumber + sep2 + pieceType + sep2 + coordinates;
    }

    /**
     * Parse the command string into a {@code SOCUndoPutPiece} message.
     *
     * @param s   the String to parse
     * @return    an UNDOPUTPIECE message, or null if the data is garbled
     */
    public static SOCUndoPutPiece parseDataStr(String s)
    {
        String gn; // name of the game
        int pn; // player number
        int pt; // type of piece
        int co; // coordinates

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            gn = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            pt = Integer.parseInt(st.nextToken());
            co = Integer.parseInt(st.nextToken());

            return new SOCUndoPutPiece(gn, pn, pt, co);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * Converts piece coordinate to decimal from hexadecimal format.
     * @param messageStrParams  Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
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
        ret.append(Integer.parseInt(pieces[3], 16));

        return ret.toString();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCUndoPutPiece:game=" + game + "|playerNumber=" + playerNumber + "|pieceType=" + pieceType + "|coord=" + Integer.toHexString(coordinates);
    }

}
