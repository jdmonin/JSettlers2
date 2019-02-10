/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010-2011,2013-2014,2017-2019 Jeremy D Monin <jeremy@nand.net>
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
 * decide and respond. See the state list below for details.
 *<P>
 * When a new game is starting (leaving state {@code NEW}), the server
 * sends the new game state and then sends {@link SOCStartGame}.
 *<P>
 * In v2.0.00 and newer, some messages contain an optional Game State field to change state
 * as part of that message's change, instead of sending a separate {@code SOCGameState}:
 * {@link SOCStartGame}, {@link SOCTurn}. Games with clients older than v2.0.00 are
 * sent {@code SOCGameState} instead of using that field. To find uses of such messages,
 * do a where-used search for {@link #VERSION_FOR_GAME_STATE_AS_FIELD}.
 *<P>
 * States sent by this message, and messages sent afterwards/client response expected:
 *<UL>
 * <LI>{@link SOCGame#NEW NEW}: -
 * <LI>{@link SOCGame#READY READY}: -
 * <LI>{@link SOCGame#READY_RESET_WAIT_ROBOT_DISMISS READY_RESET_WAIT_ROBOT_DISMISS}: -
 * <LI>{@link SOCGame#START1A START1A}: Current player: Place a settlement
 *     (all placement requests from clients are sent using {@link SOCPutPiece})
 * <LI>{@link SOCGame#START1B START1B}: Current player: Place a road
 * <LI>{@link SOCGame#START2A START2A}: Current player: Place a settlement
 * <LI>{@link SOCGame#START2B START2B}: Current player: Place a road
 * <LI>{@link SOCGame#START3A START3A}: Current player: Place a settlement
 * <LI>{@link SOCGame#START3B START3B}: Current player: Place a road
 * <LI>{@link SOCGame#STARTS_WAITING_FOR_PICK_GOLD_RESOURCE STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}:
 *     Server sends game a "x, y, and z need to pick resources from the gold hex" prompt text.
 *     Sends the game
 *     {@link SOCPlayerElement}({@link SOCPlayerElement#NUM_PICK_GOLD_HEX_RESOURCES NUM_PICK_GOLD_HEX_RESOURCES}).
 *     Sends specific player(s) {@link SOCSimpleRequest}({@link SOCSimpleRequest#PROMPT_PICK_RESOURCES PROMPT_PICK_RESOURCES}).
 * <LI>{@link SOCGame#ROLL_OR_CARD ROLL_OR_CARD}: Server sends game {@link SOCRollDicePrompt} with current player number.
 *     Current player: Send {@link SOCRollDice} or {@link SOCPlayDevCardRequest}
 * <LI>{@link SOCGame#PLAY1 PLAY1}: Current player: Build, play and buy cards, trade, etc.
 *     When done with turn, send {@link SOCEndTurn}
 * <LI>{@link SOCGame#PLACING_ROAD PLACING_ROAD}: Current player: Place a road
 * <LI>{@link SOCGame#PLACING_SETTLEMENT PLACING_SETTLEMENT}: Current player: Place a settlement
 * <LI>{@link SOCGame#PLACING_CITY PLACING_CITY}: Current player: Place a city
 * <LI>{@link SOCGame#PLACING_ROBBER PLACING_ROBBER}: Current player: Choose a new robber hex and send {@link SOCMoveRobber}
 * <LI>{@link SOCGame#PLACING_PIRATE PLACING_PIRATE}: Current player: Choose a new pirate hex and send {@link SOCMoveRobber}
 * <LI>{@link SOCGame#PLACING_SHIP PLACING_SHIP}: Current player: Place a ship
 * <LI>{@link SOCGame#PLACING_FREE_ROAD1 PLACING_FREE_ROAD1}: Current player: Place a road
 * <LI>{@link SOCGame#PLACING_FREE_ROAD2 PLACING_FREE_ROAD2}: Current player: Place a road
 * <LI>{@link SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM}: Current player: Place the previously-designated
 *     {@link soc.game.SOCInventoryItem}. Their placement message to server depends on the scenario and item type,
 *     documented at {@link SOCInventoryItemAction}. For example, in scenario SC_FTRI the player sends a
 *     {@link SOCSimpleRequest}({@link SOCSimpleRequest#TRADE_PORT_PLACE TRADE_PORT_PLACE})
 *     with the requested edge coordinate; the messages responding to that are documented
 *     at {@link SOCSimpleRequest#TRADE_PORT_PLACE}.
 *     <BR>
 *     Placement of some item types can sometimes be cancelled by sending
 *     {@link SOCCancelBuildRequest}({@link SOCCancelBuildRequest#INV_ITEM_PLACE_CANCEL INV_ITEM_PLACE_CANCEL}) instead.
 * <LI>{@link SOCGame#WAITING_FOR_DISCARDS WAITING_FOR_DISCARDS}: Server sends game a "x, y, and z need to discard"
 *     prompt text. Players who must discard are sent {@link SOCDiscardRequest} and must
 *     respond with {@link SOCDiscard}. After each client response, if still waiting for other players to discard,
 *     server sends game another prompt text. Otherwise sends game its new {@link SOCGameState}
 * <LI>{@link SOCGame#WAITING_FOR_ROB_CHOOSE_PLAYER WAITING_FOR_ROB_CHOOSE_PLAYER}:
 *     Server sends current player {@link SOCChoosePlayerRequest} listing possible victims.
 *     Current player: Choose a victim to rob, send {@link SOCChoosePlayer}
 * <LI>{@link SOCGame#WAITING_FOR_DISCOVERY WAITING_FOR_DISCOVERY}:
 *     Current player: Choose 2 resources and send {@link SOCPickResources}
 * <LI>{@link SOCGame#WAITING_FOR_MONOPOLY WAITING_FOR_MONOPOLY}:
 *     Current player: Choose a resource type and send {@link SOCPickResourceType}
 * <LI>{@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE WAITING_FOR_ROBBER_OR_PIRATE}:
 *     Current player: Choose whether to move the Robber or the Pirate,
 *     send {@link SOCChoosePlayer}({@link SOCChoosePlayer#CHOICE_MOVE_ROBBER CHOICE_MOVE_ROBBER} or
 *     {@link SOCChoosePlayer#CHOICE_MOVE_PIRATE CHOICE_MOVE_PIRATE})
 * <LI>{@link SOCGame#WAITING_FOR_ROB_CLOTH_OR_RESOURCE WAITING_FOR_ROB_CLOTH_OR_RESOURCE}:
 *     Current player: Choose a victim to rob and whether to take a resource or cloth, send {@link SOCChoosePlayer}.
 *     See that message's javadoc for how to encode both choices
 * <LI>{@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE WAITING_FOR_PICK_GOLD_RESOURCE}:
 *     Same message flow as {@code WAITING_FOR_DISCARDS}: Server sends game a
 *     "x, y, and z need to pick resources from the gold hex" prompt text. For each player who must pick, the game is sent
 *     {@link SOCPlayerElement}({@link SOCPlayerElement#NUM_PICK_GOLD_HEX_RESOURCES NUM_PICK_GOLD_HEX_RESOURCES})
 *     and the player is sent
 *     {@link SOCSimpleRequest}({@link SOCSimpleRequest#PROMPT_PICK_RESOURCES PROMPT_PICK_RESOURCES}).
 *     They must choose resource(s) and send {@link SOCPickResources}.
 *     After each client response, server sends game its {@link SOCGameState}; if multiple players had to pick,
 *     that state is still {@code WAITING_FOR_PICK_GOLD_RESOURCE} and another "need to pick" prompt text is also sent.
 * <LI>{@link SOCGame#SPECIAL_BUILDING SPECIAL_BUILDING}: Current player: Build, buy cards, etc. When done, send {@link SOCEndTurn}
 * <LI>{@link SOCGame#OVER OVER}: Server announces the winner with
 *     {@link SOCGameElements}({@link SOCGameElements#CURRENT_PLAYER CURRENT_PLAYER}), and sends text messages reporting
 *     winner's name, final score, each player's victory-point cards, game length, and a {@link SOCGameStats}.
 *     Each player is sent text with their resource roll totals. win-loss count for this session, and
 *     how long they've been connected.
 *</UL>
 * This list doesn't mention some informational/cosmetic text messages, such as the {@code START1A}
 * prompt "It's Joe's turn to build a settlement" or {@code PLACING_ROBBER}'s "Lily will move the robber".
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
        gb.setGameName(game).setGameState(b);
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
