/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017,2020-2021 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCPlayer;  // for javadocs only


/**
 * This message from server to client signals start of a new player's turn.
 * Client should end current turn, set current player number and game state,
 * then clear dice, reset votes, etc by calling {@link SOCGame#updateAtTurn()}.
 *<P>
 * In v2.5.00 and newer ({@link #VERSION_FOR_FLAG_CLEAR_AND_SBP_TEXT}), when client receives this message
 * {@link SOCGame#updateAtTurn()} will clear the new player's {@link SOCPlayer#hasPlayedDevCard()} flag.
 * (Previous server versions sent {@link SOCSetPlayedDevCard} or
 * {@link SOCPlayerElement}({@link SOCPlayerElement.PEType#PLAYED_DEV_CARD_FLAG PLAYED_DEV_CARD_FLAG})
 * before {@code SOCTurn}. Server v2.5.00 and newer still send that playerelement message
 * to clients older than 2.5.00.)
 *<P>
 * Also in v2.5.00 and newer, during Special Building (SBP) server doesn't follow this message
 * with {@link SOCGameServerText}("Special building phase: Lily's turn to place"); client should print
 * a prompt like that when it receives {@code SOCTurn}({@link SOCGame#SPECIAL_BUILDING}).
 *<P>
 * Also in v2.5.00 and newer ({@link #VERSION_FOR_SEND_BEGIN_FIRST_TURN}),
 * is also sent to game during initial placement when a round ends,
 * since the direction of play changes, and player has just placed a road or ship and should now place
 * the next settlement or roll the dice to start the game's first turn of regular play.
 * (In v2.0.00 - 2.4.00, that SOCTurn was sent only when a robot was current player.
 * v1.x versions didn't send this message during init placement; there were fewer possible state transitions,
 * and the client's SOCGame had enough info to advance the gamestate and player number.)
 *<P>
 * In v2.0.00 and newer, this message optionally includes a {@link #getGameState()} field instead of
 * a separate {@link SOCGameState} message, since the state and turn are part of the same transition.
 *<P>
 * Before v2.0.00 this message was always preceded by a {@link SOCGameState} with the new turn's state.
 * There were a few minor messages like {@link SOCSetPlayedDevCard} sent between them.  Client would
 * set current game state based on that GAMESTATE message.  Then, when this TURN message changed the
 * player number, the game would have a known state to inform the new player's options and actions.
 *<P>
 * Before v2.0.00 the server didn't send a TURN message to human players after the final road or ship is placed
 * at the end of initial placement and start of regular gameplay, only a {@link SOCGameState}
 * message (state START2 -> PLAY/ROLL_OR_CARD).
 *<P>
 * In v1.1.20 and 1.2.00 only, at start of game this {@code SOCTurn} was followed by a {@link SOCGameState}.
 *
 * @author Robert S. Thomas
 * @see SOCSetTurn
 */
public class SOCTurn extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * First version (2.5.00) where {@code SOCTurn} from server
     * also tells the client to clear the new player's "dev card played" flag
     * as if <tt>{@link SOCSetPlayedDevCard}(pn, false)</tt> was sent,
     * and where during Special Building Phase (SBP) server doesn't follow
     * this message with {@link SOCGameServerText}("Lily's turn to place").
     * @since 2.5.00
     */
    public static final int VERSION_FOR_FLAG_CLEAR_AND_SBP_TEXT = 2500;

    /**
     * First version (2.5.00) where {@code SOCTurn} from server
     * is always sent at the end of initial placement / start of the first normal turn.
     * See {@link SOCTurn} class javadoc for message sequence in earlier 1.x and 2.x versions.
     * @since 2.5.00
     */
    public static final int VERSION_FOR_SEND_BEGIN_FIRST_TURN = 2500;

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
     *     This field is ignored by clients older than v2.0.00 ({@link SOCGameState#VERSION_FOR_GAME_STATE_AS_FIELD}).
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
        return TURN + sep + game + sep2 + playerNumber + ((gameState > 0) ? sep2 + gameState : "");
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
            int gs = 0;  // the game state; not sent from v1.x servers

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

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCTurn:game=" + game + "|playerNumber=" + playerNumber
            + ((gameState != 0) ? "|gameState=" + gameState : "");
    }

}
