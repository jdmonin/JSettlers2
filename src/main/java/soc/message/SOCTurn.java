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
import soc.game.SOCGame;  // for javadocs only
import soc.proto.GameMessage;
import soc.proto.Message;


/**
 * This message from server to client signals end of the current player's turn.
 * Client should end current turn, clear dice, set current player number, reset votes, etc.
 *<P>
 * In v2.0.00 and newer, this message optionally includes a {@link #getGameState()} field instead of
 * a separate {@link SOCGameState} message, since the state and turn are part of the same transition.
 *<P>
 * Before v2.0.00 this message was always preceded by a {@link SOCGameState} with the new turn's state.
 * There were a few minor messages like {@link SOCSetPlayedDevCard} sent between them.  Client would
 * set current game state based on that GAMESTATE message.  Then, when this TURN message changed the
 * player number, the game would have a known state to inform the new player's options and actions.
 *<P>
 * In v2.0.00 and newer, is also sent to robot players during initial placement when a round ends
 * and the direction of play changes, and bot has just placed a road or ship and should now place
 * the next settlement or roll the dice as first player. (In earlier versions with fewer possible
 * state transitions, the client's SOCGame had enough info to advance the gamestate and player
 * number.)
 *<P>
 * The server won't send a TURN message to human players after the final road or ship is placed
 * at the end of initial placement and start of regular gameplay, only a {@link SOCGameState}
 * message (state START2 -> ROLL_OR_CARD).
 *
 * @author Robert S. Thomas
 * @see SOCSetTurn
 */
public class SOCTurn extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Name of game
     */
    private String game;

    /**
     * The seat number
     */
    private int playerNumber;

    /**
     * The optional {@link SOCGame} State field, or 0.
     * See {@link #getGameState()} for details.
     * @since 2.0.00
     */
    private final int gameState;

    /**
     * Create a Turn message.
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     * @param gs  the new turn's optional Game State such as {@link SOCGame#ROLL_OR_CARD}, or 0.
     *     Values &lt; 0 are out of range and ignored (treated as 0).
     */
    public SOCTurn(final String ga, final int pn, final int gs)
    {
        messageType = TURN;
        game = ga;
        playerNumber = pn;
        gameState = (gs > 0) ? gs : 0;
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
     * Get the the new turn's optional {@link SOCGame} State.
     * Ignored by clients older than v2.0.00 ({@link SOCGameState#VERSION_FOR_GAME_STATE_AS_FIELD}).
     * @return Game State, such as {@link SOCGame#ROLL_OR_CARD}, or 0
     * @since 2.0.00
     */
    public int getGameState()
    {
        return gameState;
    }

    /**
     * TURN sep game sep2 playerNumber [sep2 gameState]
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, gameState);
    }

    /**
     * TURN sep game sep2 playerNumber [sep2 gameState]
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     * @param gs  the new turn's optional Game State such as {@link SOCGame#ROLL_OR_CARD}, or 0 to omit that field
     * @return the command string
     */
    public static String toCmd(final String ga, final int pn, final int gs)
    {
        return TURN + sep + ga + sep2 + pn + ((gs > 0) ? sep2 + gs : "");
    }

    /**
     * Parse the command String into a TURN message.
     *
     * @param s   the String to parse
     * @return    a TURN message, or null if the data is garbled
     */
    public static SOCTurn parseDataStr(String s)
    {
        try
        {
            String ga;   // the game name
            int pn;  // the seat number
            int gs = 0;  // the game state

            StringTokenizer st = new StringTokenizer(s, sep2);

            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens())
                gs = Integer.parseInt(st.nextToken());

            return new SOCTurn(ga, pn, gs);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.Turn.Builder b
            = GameMessage.Turn.newBuilder().setPlayerNumber(playerNumber);
        if (gameState > 0)
            b.setStateValue(gameState);
        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGaName(game).setTurn(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCTurn:game=" + game + "|playerNumber=" + playerNumber
            + ((gameState != 0) ? "|gameState=" + gameState : "");
    }

}
