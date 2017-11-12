/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017 Jeremy D Monin <jeremy@nand.net>
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
 * This message from server to client signals end of the current player's turn.
 * Client should end current turn, clear dice, set current player number, reset votes, etc.
 *<P>
 * This message is always preceded by a {@link SOCGameState} with the new turn's state.  There may
 * be a few minor messages (such as {@link SOCSetPlayedDevCard}) sent between them.  Client should
 * set current game state based on that GAMESTATE message.  Then, when this TURN message changes the
 * player number, the game will have a known state to inform the new player's options and actions.
 *<P>
 * The server won't send a TURN message after the final road or ship is placed at the
 * end of initial placement and start of regular gameplay, only a {@link SOCGameState}
 * message (state START2 -> ROLL_OR_CARD).
 *
 * @author Robert S. Thomas
 * @see SOCSetTurn
 */
public class SOCTurn extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * The seat number
     */
    private int playerNumber;

    /**
     * Create a Turn message.
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     */
    public SOCTurn(String ga, int pn)
    {
        messageType = TURN;
        game = ga;
        playerNumber = pn;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the seat number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * TURN sep game sep2 playerNumber
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber);
    }

    /**
     * TURN sep game sep2 playerNumber
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     * @return the command string
     */
    public static String toCmd(String ga, int pn)
    {
        return TURN + sep + ga + sep2 + pn;
    }

    /**
     * Parse the command String into a TURN message.
     *
     * @param s   the String to parse
     * @return    a TURN message, or null if the data is garbled
     */
    public static SOCTurn parseDataStr(String s)
    {
        String ga; // the game name
        int pn; // the seat number

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCTurn(ga, pn);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCTurn:game=" + game + "|playerNumber=" + playerNumber;
    }
}
