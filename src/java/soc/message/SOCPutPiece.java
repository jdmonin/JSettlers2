/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012-2014,2017 Jeremy D Monin <jeremy@nand.net>
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
 * This message means that a player is asking to place, or has placed, a piece on the board.
 * Also used when joining a new game or a game in progress, to send the game state so far.
 * Some game scenarios use {@link soc.game.SOCVillage villages} which aren't owned by any player;
 * their {@link #getPlayerNumber()} is -1 in this message.
 *<P>
 * The messages similar but opposite to this one are {@link SOCCancelBuildRequest}
 * and the very-limited {@link SOCRemovePiece}.
 *<P>
 * Some scenarios like {@link soc.game.SOCScenario#K_SC_PIRI SC_PIRI} include some pieces
 * as part of the initial board layout while the game is starting. These will all be sent to
 * the clients while game state is &lt; {@link soc.game.SOCGame#START1A START1A} and before
 * sending them {@link SOCStartGame}. Scenario {@link soc.game.SOCScenario#K_SC_CLVI SC_CLVI}
 * sends its neutral villages before {@code START1A} but as part {@code "CV"} of the board layout
 * message, not as {@code SOCPutPiece}s.
 *
 * @author Robert S Thomas
 */
public class SOCPutPiece extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * the name of the game
     */
    private String game;

    /**
     * the type of piece being placed, such as {@link soc.game.SOCPlayingPiece#CITY}
     */
    private int pieceType;

    /**
     * the player number of who played the piece, or -1 for non-player-owned {@link soc.game.SOCPlayingPiece#VILLAGE}
     */
    private int playerNumber;

    /**
     * the coordinates of the piece
     */
    private int coordinates;

    /**
     * create a PutPiece message
     *
     * @param na  name of the game
     * @param pt  type of playing piece, such as {@link soc.game.SOCPlayingPiece#CITY}
     * @param pn  player number, or -1 for non-player-owned {@link soc.game.SOCPlayingPiece#VILLAGE}
     * @param co  coordinates
     */
    public SOCPutPiece(String na, int pn, int pt, int co)
    {
        messageType = PUTPIECE;
        game = na;
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
     * PUTPIECE sep game sep2 playerNumber sep2 pieceType sep2 coordinates
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
     * PUTPIECE sep game sep2 playerNumber sep2 pieceType sep2 coordinates
     *
     * @param ga  the name of the game
     * @param pn  player number, or -1 for non-player-owned {@link soc.game.SOCPlayingPiece#VILLAGE}
     * @param pt  type of playing piece
     * @param co  coordinates
     * @return the command string
     */
    public static String toCmd(String ga, int pn, int pt, int co)
    {
        return PUTPIECE + sep + ga + sep2 + pn + sep2 + pt + sep2 + co;
    }

    /**
     * parse the command string into a PutPiece message
     *
     * @param s   the String to parse
     * @return    a PUTPIECE message, or null if the data is garbled
     */
    public static SOCPutPiece parseDataStr(String s)
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

        return new SOCPutPiece(na, pn, pt, co);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCPutPiece:game=" + game + "|playerNumber=" + playerNumber + "|pieceType=" + pieceType + "|coord=" + Integer.toHexString(coordinates);

        return s;
    }
}
