/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010-2011,2013-2014,2017 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCGame;  // for javadoc's use
import soc.proto.GameMessage;
import soc.proto.Message;


/**
 * This message communicates the current state of the game.
 *<P>
 * For some states, such as {@link SOCGame#WAITING_FOR_ROB_CHOOSE_PLAYER},
 * another message (such as {@link SOCChoosePlayerRequest}) will
 * follow to prompt the current player.  For others, such as
 * {@link SOCGame#WAITING_FOR_DISCOVERY} or
 * {@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE}, sending this
 * {@code SOCGameState} message implies that the player must
 * decide and respond.
 *<P>
 * For {@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE}, the player should
 * respond with {@link SOCChoosePlayer}; see that message's javadoc.
 *<P>
 * When a new game is starting (leaving state {@code NEW}), the server
 * sends the new game state and then sends {@link SOCStartGame}.
 *<P>
 * In v2.0.00 and newer, some messages contain an optional Game State field to change state
 * as part of that message's change, instead of sending a separate {@code SOCGameState}:
 * {@link SOCStartGame}, {@link SOCTurn}. Games with clients older than v2.0.00 are
 * sent {@code SOCGameState} instead of using that field. To find uses of such messages,
 * do a where-used search for {@link #VERSION_FOR_GAME_STATE_AS_FIELD}.
 *
 * @author Robert S Thomas &lt;thomas@infolab.northwestern.edu&gt;
 * @see SOCGame#getGameState()
 */
public class SOCGameState extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Minimum client version (v2.0.00) which can be sent message types with an optional Game State field.
     * @since 2.0.00
     */
    public static final int VERSION_FOR_GAME_STATE_AS_FIELD = 2000;

    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * Game state
     */
    private int state;

    /**
     * Create a GameState message.
     *
     * @param ga  name of the game
     * @param gs  game state
     */
    public SOCGameState(String ga, int gs)
    {
        messageType = GAMESTATE;
        game = ga;
        state = gs;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the game state
     */
    public int getState()
    {
        return state;
    }

    /**
     * GAMESTATE sep game sep2 state
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, state);
    }

    /**
     * GAMESTATE sep game sep2 state
     *
     * @param ga  the game name
     * @param gs  the game state
     * @return    the command string
     */
    public static String toCmd(String ga, int gs)
    {
        return GAMESTATE + sep + ga + sep2 + gs;
    }

    /**
     * Parse the command String into a GameState message
     *
     * @param s   the String to parse
     * @return    a GameState message, or null if the data is garbled
     */
    public static SOCGameState parseDataStr(String s)
    {
        String ga;
        int gs;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            gs = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCGameState(ga, gs);
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.State.Builder b
            = GameMessage.State.newBuilder();
        b.setStateValue(state);
        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGaName(game).setGameState(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCGameState:game=" + game + "|state=" + state;

        return s;
    }
}
