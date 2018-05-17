/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010-2011,2013-2014,2017-2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.proto.GameMessage;
import soc.proto.Message;
import soc.game.SOCGame;  // for javadocs only


/**
 * This message (from client to server) means that a client player wants to
 * move the robber or pirate ship, or (from server to all players) a player
 * has moved the robber or pirate ship.
 *<P>
 * From current player's client, is sent in response to server's
 * {@link SOCGameState}({@link SOCGame#PLACING_ROBBER PLACING_ROBBER}
 * or {@link SOCGame#PLACING_PIRATE PLACING_PIRATE}).
 *<P>
 * From the server, the message will be followed by other messages
 * about gaining/losing resources.  So for this message, the client
 * should only call {@link soc.game.SOCBoard#setRobberHex(int, boolean)}
 * and not {@link soc.game.SOCGame#moveRobber(int, int)}.
 *<P>
 * Once the robber is placed on the board, it cannot be taken off the board.
 * The pirate can be taken off by sending {@code coordinate = 0}.
 *<P>
 * This message uses positive coordinates when moving the robber, and negative
 * when moving the pirate.  Moving the pirate to hex 0x0104 is done with a
 * SOCMoveRobber(-0x0104) message, which would cause the client to call
 * {@link soc.game.SOCBoardLarge#setPirateHex(int, boolean) board.setPirateHex(0x0104, boolean)}.
 *
 * @author Robert S Thomas
 * @see SOCMovePiece
 */
public class SOCMoveRobber extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * the name of the game
     */
    private String game;

    /**
     * the number of the player moving the robber
     */
    private int playerNumber;

    /**
     * the hex coordinates of the piece (positive for robber, negative for pirate)
     */
    private int coordinates;

    /**
     * create a MoveRobber message
     *
     * @param na  name of the game
     * @param pn  player number
     * @param co  hex coordinates: positive for robber, negative or 0 for pirate
     */
    public SOCMoveRobber(String na, int pn, int co)
    {
        messageType = MOVEROBBER;
        game = na;
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
     * @return the player number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * Get the robber or pirate's new location.
     * @return the hex coordinates: positive for robber, negative or 0 for pirate
     */
    public int getCoordinates()
    {
        return coordinates;
    }

    /**
     * Command string:
     *
     * MOVEROBBER sep game sep2 playerNumber sep2 coordinates
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, coordinates);
    }

    /**
     * Command string:
     *
     * MOVEROBBER sep game sep2 playerNumber sep2 coordinates
     *
     * @param na  the name of the game
     * @param pn  player number
     * @param co  hex coordinates: positive for robber, negative or 0 for pirate
     * @return the command string
     */
    public static String toCmd(String na, int pn, int co)
    {
        return MOVEROBBER + sep + na + sep2 + pn + sep2 + co;
    }

    /**
     * parse the command string into a MoveRobber message
     *
     * @param s   the String to parse
     * @return    a SOCMoveRobber message, or null if the data is garbled
     */
    public static SOCMoveRobber parseDataStr(String s)
    {
        String na; // name of the game
        int pn; // player number
        int co; // coordinates

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            na = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            co = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCMoveRobber(na, pn, co);
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.MoveRobber.Builder b
            = GameMessage.MoveRobber.newBuilder();
        int hexCoord = coordinates;
        if (hexCoord >= 0)
        {
            b.setIsRobber(true);
        } else {
            b.setIsPirate(true);
            hexCoord = - hexCoord;
        }
        b.setMoveTo(ProtoMessageBuildHelper.toHexCoord(hexCoord));
        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGameName(game).setMoveRobber(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCMoveRobber:game=" + game + "|playerNumber=" + playerNumber + "|coord="
            + ((coordinates >= 0)
              ? Integer.toHexString(coordinates)
              : ("-" + Integer.toHexString(- coordinates)));
        return s;
    }
}
