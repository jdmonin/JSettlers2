/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2022-2024 Jeremy D Monin <jeremy@nand.net>
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
 *      If was not allowed because game has option {@code "UBL"} and {@link soc.game.SOCPlayer#getUndosRemaining()} is 0,
 *      that reply is preceded by a {@link SOCPlayerElement}(SET, {@link SOCPlayerElement.PEType#NUM_UNDOS_REMAINING}, 0)
 *      in case player's client had an inaccurate count.
 *</UL>
 *
 *<H3>Announcement from Server</H3>
 *<UL>
 * <LI> Any preceding messages (Un-close ship routes, return pieces, etc; usually {@link SOCPlayerElement})
 * <LI> Server sends {@code SOCUndoPutPiece}(pn, pieceType, coordinates) to all clients in the game
 * <LI> Client UI should update at this point and announce the undo.
 * <LI> Any following messages (Un-close ship routes, return pieces, etc; usually {@link SOCPlayerElement}).
 *      Such messages update client UI on their own if needed.
 *</UL>
 *
 * See also {@link SOCCancelBuildRequest} and {@link SOCRemovePiece}.
 *<P>
 * If server has sent {@link SOCUndoNotAllowedReasonText} to announce that the player's most recent action can't be undone,
 * client shouldn't send {@code SOCUndoPutPiece}.
 *<P>
 * This message is used only with {@link soc.game.SOCGameOption} {@code "UB"}, which has a minimum version of 2.7.00.
 * If a game has that SGO, server doesn't need to check client versions before sending {@code SOCUndoPutPiece}.
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
     * The coordinates of the piece; must be &gt; 0. If undoing a move, the coordinates after the move.
     */
    private final int coordinates;

    /**
     * The former coordinates of a moved piece (before it was moved); must be &gt; 0. Is 0 if not undoing a move.
     */
    private final int movedFromCoordinates;

    /**
     * Create a {@link SOCUndoPutPiece} message to undo a piece build (not a move).
     *
     * @param gn  name of the game
     * @param pt  type of playing piece, such as {@link SOCPlayingPiece#CITY}; must be >= 0
     * @param pn  player number, or -1 for server decline replies.
     *     Sent from server, ignored if sent from client.
     * @param co  coordinates; must be &gt; 0
     * @throws IllegalArgumentException if {@code pt} &lt; 0 or {@code co} &lt;= 0
     * @see #SOCUndoPutPiece(String, int, int, int, int)
     */
    public SOCUndoPutPiece(String gn, int pn, int pt, int co)
        throws IllegalArgumentException
    {
        this(gn, pn, pt, co, 0);
    }

    /**
     * Create a {@link SOCUndoPutPiece} message to undo a piece move (not a build).
     *
     * @param gn  name of the game
     * @param pt  type of playing piece, such as {@link SOCPlayingPiece#CITY}; must be >= 0
     * @param pn  player number, or -1 for server decline replies.
     *     Sent from server, ignored if sent from client.
     * @param co  current coordinates; must be &gt; 0
     * @param fromCo  former coordinates before the move; must be &gt; 0 (otherwise it's undoing a build)
     * @throws IllegalArgumentException if {@code pt} &lt; 0, {@code co} &lt;= 0, or {@code fromCo} &lt; 0
     * @see #SOCUndoPutPiece(String, int, int, int)
     */
    public SOCUndoPutPiece(String gn, int pn, int pt, int co, int fromCo)
        throws IllegalArgumentException
    {
        if (pt < 0)
            throw new IllegalArgumentException("pt: " + pt);
        if (co <= 0)
            throw new IllegalArgumentException("coord <= 0: " + co);
        if (fromCo < 0)
            throw new IllegalArgumentException("fromCo < 0: " + fromCo);

        messageType = UNDOPUTPIECE;
        game = gn;
        pieceType = pt;
        playerNumber = pn;
        coordinates = co;
        movedFromCoordinates = fromCo;
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
     * Get the coordinates of the piece whose placement or move is being undone.
     * @return the coordinates; is &gt; 0. If undoing a move (not a build), the coordinates after the move.
     * @see #getMovedFromCoordinates()
     */
    public int getCoordinates()
    {
        return coordinates;
    }

    /**
     * If undoing a move, get the piece's former coordinates before the move.
     * @return the coordinates (&gt; 0), or 0 if undoing a build instead of a move
     * @see #getCoordinates()
     */
    public int getMovedFromCoordinates()
    {
        return movedFromCoordinates;
    }

    /**
     * Command string:
     *
     * UNDOPUTPIECE sep game sep2 playerNumber sep2 pieceType sep2 coordinates [sep2 movedFromCoordinates]
     *
     * @return the command string
     */
    public String toCmd()
    {
        StringBuilder ret = new StringBuilder
            (UNDOPUTPIECE + sep + game + sep2 + playerNumber + sep2 + pieceType + sep2 + coordinates);
        if (movedFromCoordinates != 0)
            ret.append(sep2).append(movedFromCoordinates);

        return ret.toString();
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
        int fromCo = 0;  // optional moved-from coordinates

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            gn = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            pt = Integer.parseInt(st.nextToken());
            co = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens())
                fromCo = Integer.parseInt(st.nextToken());

            return new SOCUndoPutPiece(gn, pn, pt, co, fromCo);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Minimum version where this message type is used.
     * @return Version number, 2700 for JSettlers 2.7.00.
     */
    @Override
    public int getMinimumVersion() { return 2700; }

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
        if (pieces.length > 4)
            ret.append(sep2_char).append(Integer.parseInt(pieces[4], 16));  // optional movedFromCoord

        return ret.toString();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder ret = new StringBuilder
            ("SOCUndoPutPiece:game=" + game + "|playerNumber=" + playerNumber + "|pieceType=" + pieceType
             + "|coord=" + Integer.toHexString(coordinates));
        if (movedFromCoordinates != 0)
            ret.append("|movedFromCoord=").append(Integer.toHexString(movedFromCoordinates));

        return ret.toString();
    }

}
