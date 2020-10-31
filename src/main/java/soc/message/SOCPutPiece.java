/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012-2014,2017-2020 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
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

import soc.game.SOCGame;  // for javadocs only
import soc.game.SOCPlayingPiece;  // for javadocs only

/**
 * Client player is asking to place, or server is announcing placement of, a piece on the board.
 * Also used when joining a new game or a game in progress, to send the game state so far.
 *<P>
 * If message is from server for a {@link SOCPlayingPiece#ROAD} or {@link SOCPlayingPiece#SHIP}:
 * After updating game data with the new piece, client should call {@link SOCGame#getPlayerWithLongestRoad()}
 * and update displays if needed.
 *<P>
 * If message is from server for a {@link SOCPlayingPiece#CITY} while client is joining a game, must precede by sending
 * that client a {@code SOCPutPiece} message with the {@link SOCPlayingPiece#SETTLEMENT} at the same coordinate
 * which was upgraded to that city.
 *<P>
 * If this is a placement request from a client player: If successful, server announces {@link SOCPutPiece}
 * to the game along with the new {@link SOCGameState}. Otherwise server responds with an explanatory
 * {@link SOCGameServerText} and, if the gamestate allowed placement but resources or requested coordinates
 * disallowed it, the current {@link SOCGameState} and then a {@link SOCCancelBuildRequest}.
 *<BR>
 * If PutPiece leads to Longest Route player changing, server sends that
 * after {@code SOCPlayerElement}s before {@code SOCGameState}:
 * {@link SOCGameElements}({@link SOCGameElements.GEType#LONGEST_ROAD_PLAYER LONGEST_ROAD_PLAYER}).
 *<P>
 * Some game scenarios use {@link soc.game.SOCVillage villages} which aren't owned by any player;
 * their {@link #getPlayerNumber()} is -1 in this message.
 *<P>
 * See also {@link SOCMovePiece} and {@link SOCDebugFreePlace}. Messages similar but opposite to this one
 * are {@link SOCCancelBuildRequest} and the very-limited {@link SOCRemovePiece}.
 *<P>
 * Some scenarios like {@link soc.game.SOCScenario#K_SC_PIRI SC_PIRI} include some pieces
 * as part of the initial board layout while the game is starting. These will all be sent to
 * the clients while game state is &lt; {@link SOCGame#START1A START1A} and before
 * sending them {@link SOCStartGame}. Scenario {@link soc.game.SOCScenario#K_SC_CLVI SC_CLVI}
 * sends its neutral villages before {@code START1A} but as part {@code "CV"} of the board layout
 * message, not as {@code SOCPutPiece}s.
 *<P>
 * In v2.0.00 and newer: On their own turn, player clients can optionally request PutPiece in gamestate
 * {@link SOCGame#PLAY1 PLAY1} or {@link SOCGame#SPECIAL_BUILDING SPECIAL_BUILDING}
 * which implies a {@link SOCBuildRequest} for that piece type, without needing to first send that
 * {@code SOCBuildRequest} and wait for a gamestate response. If request is allowed, the server
 * announces {@link SOCPlayerElement} messages for the resources spent, and then its usual
 * response to a successful {@code SOCPutPiece}. Otherwise the rejection response is sent as
 * described above, and after rejection the gamestate may be a placement state such as
 * {@link SOCGame#PLACING_ROAD PLACING_ROAD}.
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
     * the type of piece being placed, such as {@link SOCPlayingPiece#CITY}
     */
    private int pieceType;

    /**
     * the player number who played the piece, or -1 for non-player-owned {@link SOCPlayingPiece#VILLAGE}.
     * Sent from server, ignored if sent from client.
     */
    private int playerNumber;

    /**
     * the coordinates of the piece; must be >= 0
     */
    private int coordinates;

    /**
     * create a PutPiece message
     *
     * @param na  name of the game
     * @param pt  type of playing piece, such as {@link SOCPlayingPiece#CITY}; must be >= 0
     * @param pn  player number, or -1 for non-player-owned {@link SOCPlayingPiece#VILLAGE}.
     *     Sent from server, ignored if sent from client.
     * @param co  coordinates; must be >= 0
     * @throws IllegalArgumentException if {@code pt} &lt; 0 or {@code co} &lt; 0
     */
    public SOCPutPiece(String na, int pn, int pt, int co)
        throws IllegalArgumentException
    {
        if (pt < 0)
            throw new IllegalArgumentException("pt: " + pt);
        if (co < 0)
            throw new IllegalArgumentException("coord < 0");

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
     * @return the coordinates; is >= 0
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
     * @param pn  player number, or -1 for non-player-owned {@link SOCPlayingPiece#VILLAGE}
     * @param pt  type of playing piece, such as {@link SOCPlayingPiece#CITY}; must be >= 0
     * @param co  coordinates; must be >= 0
     * @return the command string
     * @throws IllegalArgumentException if {@code pt} &lt; 0 or {@code co} &lt; 0
     */
    public static String toCmd(String ga, int pn, int pt, int co)
        throws IllegalArgumentException
    {
        if (pt < 0)
            throw new IllegalArgumentException("pt: " + pt);
        if (co < 0)
            throw new IllegalArgumentException("coord < 0");

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

            return new SOCPutPiece(na, pn, pt, co);
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
        ret.append(Integer.parseInt(pieces[3], 16));

        return ret.toString();
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
