/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2022 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Skylar Bolton <iiagrer@gmail.com>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
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
package soc.game;

import soc.disableDebug.D;

import soc.message.SOCMessage;  // For static calls only; SOCGame does not interact with network messages
import soc.server.SOCBoardAtServer;  // For calling server-only methods like distributeClothFromRoll
import soc.util.DataUtils;
import soc.util.IntPair;
import soc.util.SOCFeatureSet;
import soc.util.SOCGameBoardReset;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;


/**
 * A class for holding and manipulating game data.
 * Most methods are not implicitly thread-safe;
 * call {@link #takeMonitor()} and {@link #releaseMonitor()} around them.
 *<P>
 * The model in this client/server game is: The SOCGame at server contains the game's
 * complete state information, and game logic advances there.
 * Each client's local SOCGame contains only partial state (for instance, other
 * players' resources or devel cards may be of unknown type); and the server directly
 * updates clients' game state by sending messages such as
 * {@link soc.message.SOCGameState} and {@link soc.message.SOCSetPlayedDevCard}.
 * Within this package, you can check {@link #isAtServer}.
 *<P>
 * Many methods assume you've already checked whether the move is valid,
 * and won't check it a second time.  For example, {@link #canPlayKnight(int)}
 * should be called to check before calling {@link #playKnight()}.
 *<P>
 * For the board <b>coordinate system and terms</b> (hex, node, edge), see the
 * {@link SOCBoard} class javadoc.
 *<P>
 * Games are created at the server in {@link soc.server.SOCGameListAtServer} and given an
 * expiration time 120 minutes away
 * ({@link soc.server.SOCGameListAtServer#GAME_TIME_EXPIRE_MINUTES SOCGameListAtServer.GAME_TIME_EXPIRE_MINUTES}).
 * Players then choose their seats, optionally locking empty seats against joining robots,
 * and any player can click the Start Game button.
 *<P>
 * Game play begins with the server calling {@link #startGame()}, then sending messages to clients
 * with the starting game state and player data and a board layout.
 * After initial placement, normal play begins with the first player's turn, in state {@link #ROLL_OR_CARD};
 * {@link #updateAtGameFirstTurn()} is called for any work needed.
 *<P>
 * During game play, {@link #putPiece(SOCPlayingPiece)} and other game-action methods update {@code gameState}.
 * {@link #updateAtTurn()}, <tt>putPiece</tt> and some other game-action methods update {@link #lastActionTime}.
 *<P>
 * The game's current plays and actions are tracked through game states, such as
 * {@value #START1A} or {@link #WAITING_FOR_DISCARDS}.  A normal turn starts at {@link #ROLL_OR_CARD};
 * after dice are rolled, the turn will spend most of its time in {@link #PLAY1}.  If you need to
 * add a state, please see the instructions at {@link #NEW}.
 *<P>
 * The winner is the player who has {@link #vp_winner} or more victory points (typically 10)
 * on their own turn.  Some optional game scenarios have special win conditions, see {@link #checkForWinner()}.
 *<P>
 * The {@link SOCGame#hasSeaBoard large sea board} features scenario events.
 * To listen for these, call {@link #setGameEventListener(SOCGameEventListener)}.
 * If the game has a scenario, its {@link SOCGameOption}s will include {@code "SC"}, possibly along with
 * other options whose key names start with {@code "_SC_"}.
 *<P>
 * To persist a game's contents or save/load, use {@link soc.server.savegame.SavedGameModel}.
 *
 * @author Robert S. Thomas
 */
public class SOCGame implements Serializable, Cloneable
{
    /**
     * The main game class has a serialVersionUID; pieces and players don't.
     * To persist a game between versions, use {@link soc.server.savegame.SavedGameModel}.
     */
    private static final long serialVersionUID = 2500L;  // last structural change v2.5.00

    /**
     * Game states.  {@link #NEW} is a brand-new game, not yet ready to start playing.
     * Players are choosing where to sit, or have all sat but no one has yet clicked
     * the "start game" button. The board is empty sea, with no land hexes yet.
     * Next state from NEW is {@link #READY} if robots, or {@link #START1A} if only humans
     * are playing.
     *<P>
     * General assumptions for states and their numeric values:
     * <UL>
     * <LI> Any scenario-specific initial pieces, such as those in
     *      {@link SOCScenario#K_SC_PIRI SC_PIRI}, are sent while
     *      game state is still &lt; {@link #START1A}.
     * <LI> Active game states are >= {@link #START1A} and &lt; {@link #OVER}
     * <LI> Initial placement ends after {@link #START2B} or {@link #START3B}, going directly to {@link #ROLL_OR_CARD}
     * <LI> A Normal turn's "main phase" is {@link #PLAY1}, after dice-roll/card-play in {@link #ROLL_OR_CARD}
     * <LI> When the game is waiting for a player to react to something,
     *      state is > {@link #PLAY1}, &lt; {@link #OVER}; state name starts with
     *      PLACING_ or WAITING_
     * <LI> While reloading and resuming a saved game, state is {@link #LOADING}, barely &lt; {@link #OVER}.
     *      Some code may want to check state &lt; {@code LOADING} instead of &lt; {@code OVER}.
     * </UL>
     *<P>
     * The code reacts to (switches based on) game state in several places.
     * The main places to check, if you add a game state:
     *<UL>
     * <LI> {@link soc.client.SOCBoardPanel#updateMode()}
     * <LI> {@link soc.client.SOCBuildingPanel#updateButtonStatus()}
     * <LI> {@link soc.client.SOCPlayerInterface#updateAtGameState()}
     * <LI> {@link #putPiece(SOCPlayingPiece)}
     * <LI> {@link #advanceTurnStateAfterPutPiece()}
     * <LI> {@link #forceEndTurn()}
     * <LI> {@link soc.robot.SOCRobotBrain#run()}
     * <LI> {@link soc.server.SOCGameHandler#sendGameState(SOCGame)}
     * <LI> {@link soc.message.SOCGameState} javadoc list of states with related messages and client responses
     *</UL>
     * Also, if your state is similar to an existing state, do a where-used search
     * for that state, and decide where both states should be reacted to.
     *<P>
     * If your new state might be waiting for several players (not just the current player) to
     * respond with a choice (such as picking resources to discard or gain), also update
     * {@link soc.server.GameHandler#endTurnIfInactive(SOCGame, long)}.  Otherwise the robot will be
     * forced to lose its turn while waiting for human players.
     *<P>
     * Other places to check, if you add a game state:
     *<UL>
     * <LI> SOCBoardPanel.BoardPopupMenu.showBuild, showCancelBuild
     * <LI> SOCBoardPanel.drawBoard
     * <LI> SOCHandPanel.addPlayer, began, removePlayer, updateAtTurn, updateValue
     * <LI> SOCGame.addPlayer
     * <LI> SOCServerMessageHandler.handleSTARTGAME
     * <LI> Your game type's GameHandler.leaveGame, sitDown, GameMessageHandler.handleCANCELBUILDREQUEST, handlePUTPIECE
     * <LI> SOCPlayerClient.handleCANCELBUILDREQUEST, SOCDisplaylessPlayerClient.handleCANCELBUILDREQUEST
     *</UL>
     */
    public static final int NEW = 0; // Brand new game, players sitting down

    /**
     * Ready to start playing.  All humans have chosen a seat.
     * Wait for requested robots to sit down.
     * Once robots have joined the game (this happens in other threads, possibly in other
     * processes), gameState will become {@link #START1A}.
     * @see #READY_RESET_WAIT_ROBOT_DISMISS
     * @see #LOADING_RESUMING
     */
    public static final int READY = 1; // Ready to start playing

    /**
     * This game object has just been created by a reset, but the old game contains robot players,
     * so we must wait for them to leave before re-inviting anyone to continue the reset process.
     * Once they have all left, state becomes {@link #READY}.
     * See {@link #boardResetOngoingInfo} and (private) SOCServer.resetBoardAndNotify.
     * @since 1.1.07
     */
    public static final int READY_RESET_WAIT_ROBOT_DISMISS = 4;

    /**
     * Players place first settlement.  Proceed in order for each player; next state
     * is {@link #START1B} to place each player's 1st road.
     */
    public static final int START1A = 5; // Players place 1st stlmt

    /**
     * Players place first road.  Next state is {@link #START1A} to place next
     * player's 1st settlement, or if all have placed settlements,
     * {@link #START2A} to place 2nd settlement.
     */
    public static final int START1B = 6; // Players place 1st road

    /**
     * Players place second settlement.  Proceed in reverse order for each player;
     * next state is {@link #START2B} to place 2nd road.
     * If the settlement is placed on a Gold Hex, the next state
     * is {@link #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}.
     *<P>
     * If game scenario option {@link SOCGameOptionSet#K_SC_3IP _SC_3IP} is set, then instead of
     * this second settlement giving resources, a third round of placement will do that;
     * next game state after START2A remains {@link #START2B}.
     */
    public static final int START2A = 10; // Players place 2nd stlmt

    /**
     * Just placed an initial piece, waiting for current
     * player to choose which Gold Hex resources to receive.
     * This can happen after the second or third initial settlement,
     * or (with the fog scenario {@link SOCGameOptionSet#K_SC_FOG _SC_FOG})
     * when any initial road, settlement, or ship reveals a gold hex.
     *<P>
     * The next game state will be based on <tt>oldGameState</tt>,
     * which is the state whose placement led to {@link #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}.
     * For settlements not revealed from fog:
     * Next game state is {@link #START2B} to place 2nd road.
     * If game scenario option {@link SOCGameOptionSet#K_SC_3IP _SC_3IP} is set,
     * next game state can be {@link #START3B}.
     *<P>
     * Valid only when {@link #hasSeaBoard}, settlement adjacent to {@link SOCBoardLarge#GOLD_HEX},
     * or gold revealed from {@link SOCBoardLarge#FOG_HEX} by a placed road, ship, or settlement.
     *<P>
     * This is the highest-numbered possible starting state; value is {@link #ROLL_OR_CARD} - 1.
     *
     * @see #WAITING_FOR_PICK_GOLD_RESOURCE
     * @see #pickGoldHexResources(int, SOCResourceSet)
     * @since 2.0.00
     */
    public static final int STARTS_WAITING_FOR_PICK_GOLD_RESOURCE = 14;  // value must be 1 less than ROLL_OR_CARD

    /**
     * Players place second road.  Next state is {@link #START2A} to place previous
     * player's 2nd settlement (player changes in reverse order), or if all have placed
     * settlements, {@link #ROLL_OR_CARD} to begin first player's turn.
     *<P>
     * If game scenario option {@link SOCGameOptionSet#K_SC_3IP _SC_3IP} is set, then instead of
     * starting normal play, a third settlement and road are placed by each player,
     * with game state {@link #START3A}.
     */
    public static final int START2B = 11; // Players place 2nd road

    /**
     * (Game scenarios) Players place third settlement.  Proceed in normal order
     * for each player; next state is {@link #START3B} to place 3rd road.
     * If the settlement is placed on a Gold Hex, the next state
     * is {@link #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}.
     *<P>
     * Valid only when game scenario option {@link SOCGameOptionSet#K_SC_3IP _SC_3IP} is set.
     */
    public static final int START3A = 12;  // Players place 3rd settlement

    /**
     * Players place third road.  Next state is {@link #START3A} to place previous
     * player's 3rd settlement (player changes in normal order), or if all have placed
     * settlements, {@link #ROLL_OR_CARD} to begin first player's turn.
     *<P>
     * Valid only when game scenario option {@link SOCGameOptionSet#K_SC_3IP _SC_3IP} is set.
     */
    public static final int START3B = 13;

    /**
     * Start of a normal turn: Time to roll or play a card.
     * Next state depends on card or roll, but usually is {@link #PLAY1}.
     *<P>
     * If 7 is rolled, might be {@link #WAITING_FOR_DISCARDS} or {@link #WAITING_FOR_ROBBER_OR_PIRATE}
     *   or {@link #PLACING_ROBBER} or {@link #PLACING_PIRATE}.
     *<P>
     * If 7 is rolled with scenario option <tt>_SC_PIRI</tt>, there is no robber to move, but
     * the player will choose their robbery victim ({@link #WAITING_FOR_ROB_CHOOSE_PLAYER}) after any discards.
     *<P>
     * If the number rolled is on a gold hex, next state might be
     *   {@link #WAITING_FOR_PICK_GOLD_RESOURCE}.
     *<P>
     * <b>More special notes for scenario <tt>_SC_PIRI</tt>:</b> When the dice is rolled, the pirate fleet moves
     * along a path, and attacks the sole player with an adjacent settlement to the pirate hex, if any.
     * This is resolved before any of the normal dice-rolling actions (distributing resources, handling a 7, etc.)
     * If the player ties or loses (pirate fleet is stronger than player's fleet of warships), the roll is
     * handled as normal, as described above.  If the player wins, they get to pick a random resource.
     * Unless the roll is 7, this can be dealt with along with other gained resources (gold hexes).
     * So: <b>If the player wins and the roll is 7,</b> the player must pick their resource before any normal 7 discarding.
     * In that case only, the next state is {@link #WAITING_FOR_PICK_GOLD_RESOURCE}, which will be
     * followed by {@link #WAITING_FOR_DISCARDS} or {@link #WAITING_FOR_ROB_CHOOSE_PLAYER}.
     *<P>
     * Before v2.0.00 this state was named {@code PLAY}.
     */
    public static final int ROLL_OR_CARD = 15; // Play continues normally; time to roll or play card

    /**
     * Done rolling (or moving robber on 7).  Time for other turn actions,
     * such as building or buying or trading, or playing a card if not already done.
     * Next state depends on what's done, but usually is the next player's {@link #ROLL_OR_CARD}.
     */
    public static final int PLAY1 = 20; // Done rolling

    public static final int PLACING_ROAD = 30;
    public static final int PLACING_SETTLEMENT = 31;
    public static final int PLACING_CITY = 32;

    /**
     * Player is placing the robber on a new land hex.
     * May follow state {@link #WAITING_FOR_ROBBER_OR_PIRATE} if the game {@link #hasSeaBoard}.
     *<P>
     * Possible next game states:
     *<UL>
     * <LI> {@link #PLAY1}, after robbing no one or the single possible victim; from {@code oldGameState}
     * <LI> {@link #WAITING_FOR_ROB_CHOOSE_PLAYER} if multiple possible victims
     * <LI> In scenario {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI}, {@link #WAITING_FOR_ROB_CLOTH_OR_RESOURCE}
     *   if the victim has cloth and has resources
     * <LI> {@link #OVER}, if current player just won by gaining Largest Army
     *   (when there aren't multiple possible victims or another robber-related choice to make)
     *</UL>
     * @see #PLACING_PIRATE
     * @see #canMoveRobber(int, int)
     * @see #moveRobber(int, int)
     */
    public static final int PLACING_ROBBER = 33;

    /**
     * Player is placing the pirate ship on a new water hex,
     * in a game which {@link #hasSeaBoard}.
     * May follow state {@link #WAITING_FOR_ROBBER_OR_PIRATE}.
     * Has the same possible next game states as {@link #PLACING_ROBBER}.
     * @see #canMovePirate(int, int)
     * @see #movePirate(int, int)
     * @since 2.0.00
     */
    public static final int PLACING_PIRATE = 34;

    /**
     * This game {@link #hasSeaBoard}, and a player has bought and is placing a ship.
     * @since 2.0.00
     */
    public static final int PLACING_SHIP = 35;

    /**
     * Player is placing their first free road/ship.
     * If {@link #getCurrentDice()} == 0, the Road Building card was
     * played before rolling the dice.
     */
    public static final int PLACING_FREE_ROAD1 = 40;

    /**
     * Player is placing their second free road/ship.
     * If {@link #getCurrentDice()} == 0, the Road Building card was
     * played before rolling the dice.
     */
    public static final int PLACING_FREE_ROAD2 = 41;

    /**
     * Player is placing the special {@link SOCInventoryItem} held in {@link #getPlacingItem()}. For some kinds
     * of item, placement can sometimes be canceled by calling {@link #cancelPlaceInventoryItem(boolean)}.
     *<P>
     * The placement method depends on the scenario and item type; for example,
     * {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI} has trading port items and would
     * call {@link #placePort(int)}.
     *<P>
     * Placement requires its own game state (not {@code PLAY1}) because sometimes
     * it's triggered by the game after another action, not initiated by player request.
     *<P>
     * When setting this gameState: In case placement is canceled, set
     * {@code oldGameState} to {@link #PLAY1} or {@link #SPECIAL_BUILDING}.
     * @since 2.0.00
     */
    public static final int PLACING_INV_ITEM = 42;

    /**
     * Waiting for player(s) to discard, after 7 is rolled in {@link #rollDice()}.
     * Next game state is {@link #WAITING_FOR_DISCARDS}
     * (if other players still need to discard),
     * {@link #WAITING_FOR_ROBBER_OR_PIRATE},
     * or {@link #PLACING_ROBBER}.
     *<P>
     * In scenario option <tt>_SC_PIRI</tt>, there is no robber
     * to move, but the player will choose their robbery victim
     * ({@link #WAITING_FOR_ROB_CHOOSE_PLAYER}) after any discards.
     * If there are no possible victims, next state is {@link #PLAY1}.
     *
     * @see #discard(int, ResourceSet)
     */
    public static final int WAITING_FOR_DISCARDS = 50;

    /**
     * Waiting for player to choose a player to rob,
     * with the robber or pirate ship, after rolling 7 or
     * playing a Knight/Soldier card.
     * Next game state is {@link #PLAY1}, {@link #WAITING_FOR_ROB_CLOTH_OR_RESOURCE},
     * or {@link #OVER} if player just won by gaining Largest Army.
     *<P>
     * To see whether we're moving the robber or the pirate, use {@link #getRobberyPirateFlag()}.
     * To choose the player, call {@link #choosePlayerForRobbery(int)}.
     *<P>
     * In scenario option <tt>_SC_PIRI</tt>, there is no robber
     * to move, but the player will choose their robbery victim.
     * <tt>{@link #currentRoll}.sc_clvi_robPossibleVictims</tt>
     * holds the list of possible victims.  In that scenario,
     * the player also doesn't control the pirate ships, and
     * never has Knight cards to move the robber and steal.
     *<P>
     * So in that scenario, the only time the game state is {@code WAITING_FOR_ROB_CHOOSE_PLAYER}
     * is when the player must choose to steal from a possible victim, or choose to steal
     * from no one, after a 7 is rolled.  To choose the victim, call {@link #choosePlayerForRobbery(int)}.
     * To choose no one, call {@link #choosePlayerForRobbery(int) choosePlayerForRobbery(-1)}.
     *<P>
     * Before v2.0.00, this game state was called {@code WAITING_FOR_CHOICE}.
     *
     * @see #playKnight()
     * @see #canChoosePlayer(int)
     * @see #canChooseRobClothOrResource(int)
     * @see #stealFromPlayer(int, boolean)
     */
    public static final int WAITING_FOR_ROB_CHOOSE_PLAYER = 51;

    /**
     * Waiting for player to choose 2 resources (Discovery/Year of Plenty card)
     * Next game state is {@link #PLAY1}.
     */
    public static final int WAITING_FOR_DISCOVERY = 52;

    /**
     * Waiting for player to choose a resource (Monopoly card)
     * Next game state is {@link #PLAY1}.
     */
    public static final int WAITING_FOR_MONOPOLY = 53;

    /**
     * Waiting for player to choose the robber or the pirate ship,
     * after {@link #rollDice()} or {@link #playKnight()}.
     * Next game state is {@link #PLACING_ROBBER} or {@link #PLACING_PIRATE}.
     *<P>
     * Moving from {@code WAITING_FOR_ROBBER_OR_PIRATE} to those states preserves {@code oldGameState}.
     *
     * @see #canChooseMovePirate()
     * @see #chooseMovePirate(boolean)
     * @see #WAITING_FOR_DISCARDS
     * @since 2.0.00
     */
    public static final int WAITING_FOR_ROBBER_OR_PIRATE = 54;

    /**
     * Waiting for player to choose whether to rob cloth or rob a resource.
     * Previous game state is {@link #PLACING_PIRATE} or {@link #WAITING_FOR_ROB_CHOOSE_PLAYER}.
     * Next step: Call {@link #stealFromPlayer(int, boolean)} with result of that player's choice.
     *<P>
     * Next game state is {@link #PLAY1}, or {@link #OVER} if player just won by gaining Largest Army.
     *<P>
     * Used with scenario option {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI}.
     *
     * @see #movePirate(int, int)
     * @see #canChooseRobClothOrResource(int)
     * @since 2.0.00
     */
    public static final int WAITING_FOR_ROB_CLOTH_OR_RESOURCE = 55;

    /**
     * Waiting for player(s) to choose which Gold Hex resources to receive.
     * Next game state is usually {@link #PLAY1}, sometimes
     * {@link #PLACING_FREE_ROAD2} or {@link #SPECIAL_BUILDING}.
     * ({@link #oldGameState} holds the <b>next</b> state after this WAITING state.)
     *<P>
     * Valid only when {@link #hasSeaBoard}, settlements or cities
     * adjacent to {@link SOCBoardLarge#GOLD_HEX}.
     *<P>
     * When receiving this state from server, a client shouldn't immediately check their user player's
     * {@link SOCPlayer#getNeedToPickGoldHexResources()} or prompt the user to pick resources; those
     * players' clients will be sent a {@link soc.message.SOCSimpleRequest#PROMPT_PICK_RESOURCES} message shortly.
     *<P>
     * If scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI} is active,
     * this state is also used when a 7 is rolled and the player has won against a
     * pirate fleet attack.  They must choose a free resource.  {@link #oldGameState} is {@link #ROLL_OR_CARD}.
     * Then, the 7 is resolved as normal.  See {@link #ROLL_OR_CARD} javadoc for details.
     * That's the only time free resources are picked on rolling 7.
     *
     * @see #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE
     * @see #pickGoldHexResources(int, SOCResourceSet)
     * @since 2.0.00
     */
    public static final int WAITING_FOR_PICK_GOLD_RESOURCE = 56;

    /**
     * The 6-player board's Special Building Phase.
     * Takes place at the end of any player's normal turn (roll, place, etc).
     * The Special Building Phase changes {@link #currentPlayerNumber}.
     * So, it begins by calling {@link #advanceTurn()} to
     * the next player, and continues clockwise until
     * {@link #currentPlayerNumber} == {@link #specialBuildPhase_afterPlayerNumber}.
     * At that point, the Special Building Phase is over,
     * and it's the next player's turn as usual.
     * @since 1.1.08
     */
    public static final int SPECIAL_BUILDING = 100;  // see advanceTurnToSpecialBuilding()

    /**
     * A saved game is being loaded. Its actual state is saved in field {@code oldGameState}.
     * Bots have joined to sit in seats which were bots in the saved game, and human seats
     * are currently unclaimed except for the requesting user if they were named as a player.
     * Before resuming play, server or user may need to satisfy conditions or constraints
     * (have a certain type of bot sit down at a given player number, etc).
     * If there are unclaimed non-vacant seats where bots will need to join,
     * next state is {@link #LOADING_RESUMING}, otherwise will resume at {@code oldGameState}.
     *<P>
     * This game state is higher-numbered than actively-playing states, slightly lower than {@link #OVER}
     * or {@link #LOADING_RESUMING}.
     *
     * @since 2.3.00
     */
    public static final int LOADING = 990;

    /**
     * A saved game was loaded and is about to resume, now waiting for some bots to rejoin.
     * Game's actual state is saved in field {@code oldGameState}.
     * Before we can resume play, server has requested some bots to fill unclaimed non-vacant seats,
     * which had humans when the game was saved. Server is waiting for the bots to join and sit down
     * (like state {@link #READY}). Once all bots have joined, gameState can resume at {@code oldGameState}.
     *<P>
     * This game state is higher-numbered than actively-playing states, slightly lower than {@link #OVER}
     * but higher than {@link #LOADING}.
     *
     * @since 2.3.00
     */
    public static final int LOADING_RESUMING = 992;

    /**
     * The game is over.  A player has accumulated enough ({@link #vp_winner}) victory points,
     * or all players have left the game.
     * The winning player, if any, is {@link #getPlayerWithWin()}.
     * @see #checkForWinner()
     */
    public static final int OVER = 1000; // The game is over

    /**
     * This game is an obsolete old copy of a new (reset) game with the same name.
     * To assist logic, numeric constant value is greater than {@link #OVER}.
     * @see #resetAsCopy()
     * @see #getOldGameState()
     * @since 1.1.00
     */
    public static final int RESET_OLD = 1001;

    /**
     * seat states
     */
    private static final int VACANT = 0, OCCUPIED = 1;

    /**
     * Seat state {@link #VACANT}, but {@link #removePlayer(String, boolean)} was called
     * with flag that another player will be seated here momentarily; used at server only.
     * See that method for threading assumption/race prevention.
     * @since 2.1.00
     */
    private static final int VACANT_PENDING_REPLACE = 2;

    // for seatLock states, see SeatLockState enum javadoc.

    /**
     * {@link #boardResetVotes} per-player states: no vote sent; yes; no.
     * @since 1.1.00
     */
    public static final int VOTE_NONE = 0;
    public static final int VOTE_YES  = 1;
    public static final int VOTE_NO   = 2;

    /**
     * Maximum number of players in a game, in this version.
     * Was 4 before 1.1.08, now is 6.
     * @see #maxPlayers
     * @see #MAXPLAYERS_STANDARD
     */
    public static final int MAXPLAYERS = 6;

    /**
     * maximum number of players in a standard game
     * without the 6-player extension.
     * @see #MAXPLAYERS
     * @see #maxPlayers
     * @since 1.1.08
     */
    public static final int MAXPLAYERS_STANDARD = 4;

    /**
     * minimum number of players in a game (was assumed =={@link #MAXPLAYERS} in standard 1.0.6).
     * Use {@link #isSeatVacant(int)} to determine if a player is present;
     * <tt>players[i]</tt> will be non-null although no player is there.
     * @since 1.1.00
     */
    public static final int MINPLAYERS = 2;

    /**
     * Default number of victory points (10) needed to win.
     * Per-game copy is {@link #vp_winner}, can be changed from 10 in
     * constructor with the <tt>"VP"</tt> {@link SOCGameOption}.
     *<P>
     * Before v1.1.14 this was {@code VP_WINNER}.
     * @since 1.1.00
     */
    public static final int VP_WINNER_STANDARD = 10;

    /**
     * Number of development cards (5) which are Victory Point cards.
     * Not used in scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
     * (If 4 or more players in that scenario, they become {@link SOCDevCardConstants#KNIGHT KNIGHT} cards.)
     * @see #NUM_DEVCARDS_STANDARD
     * @since 2.0.00
     */
    private static final int NUM_DEVCARDS_VP = 5;

    /**
     * Number of development cards (25) in the standard rules,
     * without the 6-player extension.
     * @see #NUM_DEVCARDS_6PLAYER
     * @since 1.1.08
     */
    private static final int NUM_DEVCARDS_STANDARD = 25;

    /**
     * Number of development cards (34) in the 6-player rules.
     * @see #NUM_DEVCARDS_STANDARD
     * @since 1.1.08
     */
    private static final int NUM_DEVCARDS_6PLAYER = 9 + NUM_DEVCARDS_STANDARD;

    /**
     * Minimum version (2.5.00) that supports canceling the second free road or ship placement
     * without ending player's turn.
     * @see #cancelBuildRoad(int)
     * @see #cancelBuildShip(int)
     * @see #VERSION_FOR_CANCEL_FREE_ROAD2
     * @since 2.5.00
     */
    public static final int VERSION_FOR_CANCEL_FREE_ROAD1 = 2500;

    /**
     * Minimum version (1.1.17) that supports canceling the second free road or ship placement.
     * @see #cancelBuildRoad(int)
     * @see #cancelBuildShip(int)
     * @see #VERSION_FOR_CANCEL_FREE_ROAD1
     * @since 1.1.17
     */
    public static final int VERSION_FOR_CANCEL_FREE_ROAD2 = 1117;

    /**
     * Minimum version (2.4.00) where {@link #getPlayerWithLongestRoad()} and {@link #getPlayerWithLargestArmy()}
     * are calculated only at the server, instead of also at the client.
     * Client should calculate them if {@link #serverVersion} &lt; this version.
     * @since 2.4.00
     */
    public static final int VERSION_FOR_LONGEST_LARGEST_FROM_SERVER = 2400;

    /**
     * an empty set of resources.
     * @see SOCSettlement#COST
     */
    public static final ResourceSet EMPTY_RESOURCES = new SOCResourceSet();

    /**
     * The {@link SOCBoard.BoardFactory} for creating new boards in the SOCGame constructors.
     * Differs at client and at server.
     * If null, SOCGame constructor sets to {@link SOCBoard.DefaultBoardFactory}.
     * @see soc.server.SOCBoardAtServer.BoardFactoryAtServer
     * @since 2.0.00
     */
    public static SOCBoard.BoardFactory boardFactory;

    /**
     * An empty int array for use in method calls.
     * @since 2.0.00
     */
    private static final int[] EMPTY_INT_ARRAY = { };

    /**
     * monitor for synchronization
     */
    boolean inUse;

    /**
     * the name of the game
     */
    private String name;

    /**
     * Is this the server's complete copy of the game, not the client's (with some details unknown)?
     * Is set during {@link #startGame()}. Treat as read-only.
     * @see #serverVersion
     * @see #hasDoneGameOverTasks
     * @since 1.1.17
     */
    public boolean isAtServer;

    /**
     * For use at client, version of the server hosting this game;
     * same format as {@link soc.util.Version#versionNumber()}.
     * If {@link #isPractice}, holds same version as client.
     *<P>
     * Unused if {@link #isAtServer}.
     *<P>
     * Needed because some versions have a different balance of which game-data tasks are done at the server,
     * and which are at the client or at both. See {@link #VERSION_FOR_LONGEST_LARGEST_FROM_SERVER} for an example.
     *<P>
     * Is set when client receives {@code SOCJoinGameAuth} message which tells client to create the {@link SOCGame}.
     * Treat as read-only.
     * @since 2.4.00
     */
    public transient int serverVersion;

    /**
     * For games at server, a convenient queue to hold any outbound SOCMessages during game actions.
     * Public access for use by SOCServer.  The server will handle a game action as usual
     * (for example, {@link #putPiece(SOCPlayingPiece)}), create pending PLAYERELEMENT messages from
     * {@link SOCGameEventListener#playerEvent(SOCGame, SOCPlayer, SOCPlayerEvent, boolean, Object) SOCGameEventListener.playerEvent(...)},
     * send the usual messages related to that action, then check this list and send out
     * the pending PLAYERELEMENT message so that the game's clients will update that player's
     * {@link SOCPlayer#setPlayerEvents(int)} or other related fields, before the GAMESTATE message.
     *<P>
     * For pending messages to send only to one player's client, see {@link SOCPlayer#pendingMessagesOut}.
     *<P>
     * <B>Contents:</B> When sending out to game members, the server handles queue elements by class:
     *<UL>
     * <LI> {@code SOCKeyedMessage}: Localize and send
     * <LI> Other {@code SOCMessage} types: Send
     * <LI> {@code UnlocalizedString}: Localize and send with
     *      {@code SOCServer.messageToGameKeyed(SOCGame, boolean, String, Object...)}
     *      or {@code SOCServer.messageToGameKeyedSpecial(..)}
     * <LI> Anything else: Ignore or print error message at server console
     *</UL>
     *<P>
     * <B>Note:</B> Only a few of the server message-handling methods check this queue, because
     * only those few can potentially lead to special victory points or other game/scenario events.
     * If you add code where other player actions can lead to {@code pendingMessagesOut} adds, be sure
     * the server's SOCGameHandler/SOCGameMessageHandler for those actions checks this list afterwards,
     * to send before GAMESTATE via {@code SOCGameHandler.sendGamePendingMessages(..)}.
     *<P>
     * Because this queue is server-only, it's null until {@link #startGame()}.
     * To send and clear contents, the server should call
     * {@code SOCGameHandler.sendGamePendingMessages(SOCGame, boolean)}.
     *<P>
     * <B>Locking:</B> Not thread-safe, because all of a game's message handling
     * is done within a single thread.
     *
     * @since 2.0.00
     */
    public transient List<Object> pendingMessagesOut;

    /**
     * For a game at server which was loaded from disk and hasn't yet been resumed,
     * its {@link soc.server.savegame.SavedGameModel}. Otherwise {@code null}.
     * After the game is resumed by {@link soc.server.savegame.SavedGameModel#resumePlay(boolean)},
     * there's no field or flag that remembers it was previously loaded.
     *<P>
     * Declared as Object here to avoid needing server class at client.
     * @since 2.3.00
     */
    public transient Object savedGameModel;

    /**
     * For games at the server, the owner (creator) of the game.
     * Will be the name of a player / server connection.
     * Currently, if the game is reset, {@link #resetAsCopy()} copies ownerName,
     * even if they aren't still connected to the game.
     * NOT CURRENTLY SET AT CLIENT.
     * @since 1.1.10
     */
    private String ownerName;

    /**
     * Locale of the client connection that created this game.
     * Used to determine whether to set {@link #hasMultiLocales} when adding a member to the game.
     * Currently, if the game is reset, {@link #resetAsCopy()} copies {@code ownerLocale},
     * even if they aren't still connected to the game.
     *<P>
     * Server only, this field is not set at client.
     * @since 2.0.00
     */
    private String ownerLocale;

    /**
     * True if this game at server has already done the tasks which happen once when game ends:
     * Update server's game stats, write results to optional database, etc.
     *<P>
     * Server only, this field is not used at client.
     * @since 2.3.00
     */
    public boolean hasDoneGameOverTasks;

    /**
     * true if this game is ACTIVE
     */
    private boolean active;

    /**
     * Number of victory points needed to win this game (default {@link #VP_WINNER_STANDARD} == 10).
     * After game events such as playing a piece or moving the robber, check if current player's
     * VP &gt;= {@link #vp_winner} and call {@link #checkForWinner()} if so.
     * @see #hasScenarioWinCondition
     * @since 1.1.14
     */
    public final int vp_winner;

    /**
     * Does this game's scenario have a special win condition besides {@link #vp_winner}?
     * For example, scenario {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI} will end the game if
     * less than half the {@link SOCVillage}s have cloth remaining.  See {@link #checkForWinner()}
     * for a full list and more details.
     *<P>
     * When set, methods that check current player's VP &gt;= {@link #vp_winner} will
     * also call {@link #checkForWinner()}.
     * @since 2.0.00
     */
    public final boolean hasScenarioWinCondition;

    /**
     * true if the game's network is local for practice.  Used by
     * client to route messages to appropriate connection.
     * NOT CURRENTLY SET AT SERVER.  Instead check if server's strSocketName != null,
     * or if connection instanceof {@link StringConnection}.
     *<P>
     * Since 1.1.09: This flag is set at the server, true only if the server is a local practice
     * server whose stringport name equals {@code SOCServer.PRACTICE_STRINGPORT}.
     *<P>
     * Before 1.1.13 this field was called {@code isLocal}, but that was misleading;
     * the full client can launch a locally hosted tcp LAN server.
     *
     * @see #serverVersion
     * @since 1.1.00
     */
    public boolean isPractice;

    /**
     * true if the game's only players are bots, no humans.  Useful for bot AI experiments.
     * Not sent over the network: Must be set at server, or set at bot client at start of game.
     *<P>
     * This flag should be set by the server when creating the game.  If a human observer exits
     * a game with this flag, the game should continue play unless its state is {@link #OVER}.
     * @since 2.0.00
     * @see soc.robot.SOCRobotBrain#BOTS_ONLY_FAST_PAUSE_FACTOR
     * @see soc.server.SOCGameHandler#DESTROY_BOT_ONLY_GAMES_WHEN_OVER
     */
    public boolean isBotsOnly;

    /**
     * True once any player has built a city.
     * Used with house-rule game option {@code "N7C"}.
     * @since 1.1.19
     */
    private boolean hasBuiltCity;

    /**
     * Listener for scenario events on the {@link #hasSeaBoard large sea board}, or null.
     * Package access for read-only use by {@link SOCPlayer}.
     * @since 2.0.00
     */
    SOCGameEventListener gameEventListener;

    /**
     * For use at server; are there clients connected which aren't at the latest version?
     * @since 1.1.00
     */
    public boolean hasOldClients;

    /**
     * For use at server; lowest and highest version of connected clients.
     * @since 1.1.00
     */
    public int clientVersionLowest, clientVersionHighest;

    /**
     * For use at server; lowest version of client which can connect to
     * this game (based on game options/features added in a given version),
     * or -1 if unknown.
     *<P>
     * Calculated by {@link SOCVersionedItem#itemsMinimumVersion(Map)}.
     * Format is the internal integer format, see {@link soc.util.Version#versionNumber()}.
     * Value may sometimes be too low at client, see {@link #getClientVersionMinRequired()} for details.
     * @see #clientFeaturesRequired
     * @since 1.1.06
     */
    private int clientVersionMinRequired;

    /**
     * For use at server; optional client features needed to connect to this game,
     * or {@code null} if none.
     * @see #clientVersionMinRequired
     * @since 2.0.00
     */
    private SOCFeatureSet clientFeaturesRequired;

    /**
     * For use at server for i18n; does this game have any members (players or observers)
     * with a locale different than {@link #getOwnerLocale()}?
     * Initially false, set true in {@code SOCGameListAtServer.addMember} if needed.
     * @since 2.0.00
     */
    public boolean hasMultiLocales;

    /**
     * Are we in the 'free placement' debug mode?
     * See {@link #isDebugFreePlacement()} for more details.
     * @since 1.1.12
     */
    private boolean debugFreePlacement;

    /**
     * Have we placed pieces in {@link #debugFreePlacement}
     * during initial placement?  Set in {@link #putPiece(SOCPlayingPiece)}.
     * @since 1.1.12
     */
    private boolean debugFreePlacementStartPlaced;

    /**
     * true if the game came from a board reset
     * @since 1.1.00
     */
    private boolean isFromBoardReset;

    /**
     * For the server's use, if a reset is in progress, this holds the reset data
     * until all robots have left (new game state is {@link #READY_RESET_WAIT_ROBOT_DISMISS}).
     * This field is null except within the newly-created game object during reset.
     * @since 1.1.07
     */
    public transient SOCGameBoardReset boardResetOngoingInfo;

    /**
     * If a board reset vote is active, player number who requested the vote.
     * All human players must vote unanimously, or board reset is rejected.
     * -1 if no vote is active.
     * Synchronize on {@link #boardResetVotes} before reading or writing.
     * @since 1.1.00
     */
    private int boardResetVoteRequester;

    /**
     * If a board reset vote is active, votes are recorded here.
     * Values: {@link #VOTE_NONE}, {@link #VOTE_YES}, {@link #VOTE_NO}.
     * Indexed 0 to SOCGame.MAXPLAYERS-1.
     * Synchronize on this object before reading or writing.
     * @since 1.1.00
     */
    private int boardResetVotes[];

    /**
     * If a board reset vote is active, we're waiting to receive this many more votes.
     * All human players vote, except the vote requester. Robots do not vote.
     * Synchronize on {@link #boardResetVotes} before reading or writing.
     * When the vote is complete, or before the first vote has begun, this is 0.
     * Set in resetVoteBegin, resetVoteRegister. Cleared in resetVoteClear.
     * @since 1.1.00
     */
    private int boardResetVotesWaiting;

    /**
     * The game board.
     *<P>
     * If {@link #hasSeaBoard}, can always be cast to {@link SOCBoardLarge}.
     */
    private SOCBoard board;

    /**
     * the game options ({@link SOCGameOption}), or null
     * @see #knownOpts
     * @since 1.1.07
     */
    private final SOCGameOptionSet opts;

    /**
     * All Known Options, for {@link #opts} validation and adding options from scenario if present.
     * Not null unless {@link #opts} is null.
     *<P>
     * At client, practice games use a different set of Known Options than games being played on a server
     * (whose options may vary by version and server config).
     *
     * @since 2.5.00
     */
    private final SOCGameOptionSet knownOpts;

    /**
     * the players; never contains a null element during play, use {@link #isSeatVacant(int)}
     * to see if a position is occupied.  Length is {@link #maxPlayers}.
     *<P>
     * If the game is reset or restarted by {@link #resetAsCopy()},
     * the new game gets new player objects, not the ones in this array.
     *<P>
     * Contains nulls after {@link #destroyGame()} is called.
     *
     * @see #currentPlayerNumber
     */
    private final SOCPlayer[] players;

    /**
     * State of each player number's seat: {@link #OCCUPIED}, {@link #VACANT}, etc.
     * Length is {@link #maxPlayers}.
     * @see #seatLocks
     */
    private int[] seats;

    /**
     * The lock state for each player number's seat. Length is {@link #maxPlayers}.
     * @see #seats
     */
    private SeatLockState[] seatLocks;

    /**
     * The seat number of the current player within {@link #players}[],
     * or -1 if the game isn't started yet.
     * @see #specialBuildPhase_afterPlayerNumber
     */
    private int currentPlayerNumber;

    /**
     * the first player to place a settlement
     */
    private int firstPlayerNumber;

    /**
     * the last player to place the first settlement
     */
    private int lastPlayerNumber;

    /**
     * The standard {@code maxPlayers} is 4 for the original classic game,
     * or 6 if this game is on the 6-player board, with corresponding rules.
     * The value of game option {@code "PL"} may be 2, 3, 4, 5, or 6, but
     * the {@code maxPlayers} field will be 4 or 6.
     *<P>
     * The 6-player extensions are orthogonal to other board type/expansion flags such as {@link #hasSeaBoard};
     * one doesn't imply or exclude the other.
     *
     * @see #getAvailableSeatCount()
     * @see #getPlayerCount()
     * @since 1.1.08
     */
    public final int maxPlayers;

    /**
     * Is this game played on the {@link SOCBoardLarge} large board / sea board?
     * If true, our board's {@link SOCBoard#getBoardEncodingFormat()}
     * must be {@link SOCBoard#BOARD_ENCODING_LARGE}.
     * When {@code hasSeaBoard}, {@link #getBoard()} can always be cast to {@link SOCBoardLarge}.
     *<P>
     * The 6-player extensions ({@link #maxPlayers} == 6) are orthogonal to {@code hasSeaBoard}
     * or other board types/expansions; one doesn't imply or exclude the other.
     *<P>
     * In most scenarios the sea board has a pirate ship that can be moved instead of
     * the robber.  See game states {@link #WAITING_FOR_ROBBER_OR_PIRATE} and {@link #PLACING_PIRATE}.
     * @since 2.0.00
     */
    public final boolean hasSeaBoard;

    /**
     * the current dice result. -1 at start of game, 0 during player's turn before roll (state {@link #ROLL_OR_CARD}).
     * @see #getCurrentDice()
     * @see #currentRoll
     * @see #hasRolledSeven
     */
    private int currentDice;

    /**
     * The current dice result, including any scenario items such as
     * {@link SOCVillage#distributeCloth(SOCGame, RollResult)} results.
     * This is the object returned from {@link #rollDice()} each turn.
     * @since 2.0.00
     * @see #currentDice
     */
    private RollResult currentRoll;

    /**
     * Has a 7 been rolled yet in this game?
     * See {@link #hasRolledSeven()} for details.
     * @see #currentDice
     * @since 2.5.00
     */
    private boolean hasRolledSeven;

    /**
     * The most recent {@link #moveRobber(int, int)} or {@link #movePirate(int, int)} result.
     * Used at server only.
     * @since 2.0.00
     */
    private SOCMoveRobberResult robberResult;

    /**
     * the current game state
     *<P>
     * Instead of directly setting <tt>gameState = {@link #OVER}</tt>,
     * code should call {@link #setGameStateOVER()}
     * to also set {@link #finalDurationSeconds}.
     */
    private int gameState;

    /**
     * The saved game state; used in only a few places, where a state can happen from different start states.
     * Not set every time the game state changes.
     *<P>
     * oldGameState is read in these states:
     *<UL>
     *<LI> {@link #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}:
     *        After the resource is picked, oldGameState will be the starting state for
     *        {@link #advanceTurnStateAfterPutPiece()}.
     *<LI> {@link #PLACING_ROAD}, {@link #PLACING_SETTLEMENT}, {@link #PLACING_CITY}, {@link #PLACING_SHIP}:
     *        Unless <tt>oldGameState</tt> is {@link #SPECIAL_BUILDING},
     *        {@link #advanceTurnStateAfterPutPiece()} will set the state to {@link #PLAY1}.
     *        So will {@link #cancelBuildRoad(int)}, {@link #cancelBuildSettlement(int)}, etc.
     *<LI> {@link #PLACING_INV_ITEM}:
     *        {@code oldGameState} == {@link #PLAY1} or {@link #SPECIAL_BUILDING},
     *        same advance/cancel logic as other {@code PLACING_} states mentioned above.
     *<LI> {@link #PLACING_ROBBER}, {@link #WAITING_FOR_ROBBER_OR_PIRATE}:
     *        <tt>oldGameState</tt> = {@link #PLAY1} or {@link #OVER}
     *<LI> {@link #WAITING_FOR_ROB_CHOOSE_PLAYER}, {@link #PLACING_PIRATE}, {@link #WAITING_FOR_ROB_CLOTH_OR_RESOURCE}
     *<LI> {@link #WAITING_FOR_DISCOVERY} in {@link #playDiscovery()}, {@link #doDiscoveryAction(SOCResourceSet)}
     *<LI> {@link #WAITING_FOR_MONOPOLY} in {@link #playMonopoly()}, {@link #doMonopolyAction(int)}
     *<LI> {@link #WAITING_FOR_PICK_GOLD_RESOURCE}:
     *        oldGameState holds the <B>next</B> state to go to when all players are done picking resources.
     *        After picking gold from a dice roll, this will usually be {@link #PLAY1}.
     *        Sometimes will be {@link #PLACING_FREE_ROAD2} or {@link #SPECIAL_BUILDING}.
     *        Can be {@link #ROLL_OR_CARD} in scenario {@link SOCGameOptionSet#K_SC_PIRI SC_PIRI}:
     *        See {@link #pickGoldHexResources(int, SOCResourceSet)} and {@link #rollDice_update7gameState()}.
     *<LI> {@link #LOADING}, {@link #LOADING_RESUMING}:
     *        Holds the actual game state, to be resumed once optional constraints are met and all bots have joined.
     *</UL>
     * Also used if the game board was reset: Holds the state before the reset.
     */
    private int oldGameState;

    /**
     * If true, and if state is {@link #PLACING_ROBBER}, {@link #PLACING_PIRATE},
     * or {@link #WAITING_FOR_ROBBER_OR_PIRATE},
     * the robber or pirate is being moved because a knight card
     * has just been played: {@link #playKnight()}.
     * If {@link #forceEndTurn()} is called, the knight card
     * should be returned to the player's inventory.
     *
     * @see #robberyWithPirateNotRobber
     * @see #playingRoadBuildingCardForLastRoad
     * @since 1.1.00
     */
    private boolean placingRobberForKnightCard;

    /**
     * If true, and state is {@link #PLACING_FREE_ROAD2}, the Road Building dev card was played when
     * player had only 1 road or ship remaining: {@link #playRoadBuilding()}.
     * Checked by {@link #doesCancelRoadBuildingReturnCard()}.
     * If {@link #cancelBuildRoad(int)}, {@link #cancelBuildShip(int)}, {@link #endTurn()} or
     * {@link #forceEndTurn()} is called, the road building card should be returned to the player's inventory.
     *
     * @see #placingRobberForKnightCard
     * @since 2.5.00
     */
    private boolean playingRoadBuildingCardForLastRoad;

    /**
     * If true, this turn is being ended. Controller of game (server) should call {@link #endTurn()}
     * whenever possible.  Usually set when we have called {@link #forceEndTurn()}, and
     * forced the current player to discard randomly, and are waiting for other players
     * to discard in gamestate {@link #WAITING_FOR_DISCARDS}.  Once all players have
     * discarded, the turn should be ended.
     * @see #forceEndTurn()
     * @since 1.1.00
     */
    private boolean forcingEndTurn;

    /**
     * If true, it's a 6-player board and at least one player has requested to build
     * during the Special Building Phase that occurs between turns.
     * @see #specialBuildPhase_afterPlayerNumber
     * @since 1.1.08
     */
    private boolean askedSpecialBuildPhase;

    /**
     * During the 6-player board's Special Building Phase, the player number whose
     * normal turn (roll, place, etc) has just ended.
     * Game state is {@link #SPECIAL_BUILDING}.
     * See {@link #getSpecialBuildingPlayerNumberAfter()} for gameplay details.
     * @see #askedSpecialBuildPhase
     * @since 1.1.08
     */
    private int specialBuildPhase_afterPlayerNumber;

    /**
     * the player with the largest army, or -1 if none
     */
    private int playerWithLargestArmy;

    /**
     * To remember last {@link #playerWithLargestArmy} during
     * {@link #saveLargestArmyState()} / {@link #restoreLargestArmyState()}.
     */
    private int oldPlayerWithLargestArmy;

    /**
     * the player with the longest road or trade route, or -1 if none
     */
    private int playerWithLongestRoad;

    /**
     * used to restore the LR player
     */
    Stack<SOCOldLRStats> oldPlayerWithLongestRoad;

    /**
     * the player declared winner, if gamestate == OVER; otherwise -1
     * @since 1.1.00
     */
    private int playerWithWin;

    /**
     * The number of development cards remaining in {@link #devCardDeck};
     * {@code numDevCards - 1} is index of the next card to buy for {@link #buyDevCard()}.
     * @see #getNumDevCards()
     */
    private int numDevCards;

    /**
     * The development card deck's remaining and already-drawn cards.
     * Each element is a dev card type from {@link SOCDevCardConstants}.
     * {@link #numDevCards} counts down to track the next card to buy for {@link #buyDevCard()}.
     */
    private int[] devCardDeck;

    /**
     * Game's {@link SOCSpecialItem}s, if any, by type.
     * This is not the union of each player's {@code spItems}, but a separately maintained Map of item lists.
     * See getter/setter javadocs for details on type keys and rationale for lack of synchronization.
     * ArrayList is used to guarantee we can store null items.
     *<P>
     * Initialized at server and client in {@link #updateAtBoardLayout()} using
     * {@link SOCSpecialItem#makeKnownItem(String, int)}.
     *
     * @since 2.0.00
     */
    private HashMap<String, ArrayList<SOCSpecialItem>> spItems;

    /**
     * used to generate random numbers
     */
    private Random rand = new Random();

    /**
     * used to track if there were any player subs
     */
    boolean allOriginalPlayers;

    /**
     * Time when this game was created, or null if not {@link #active} when created.
     * Used by {@link #getStartTime()} and {@link #getDurationSeconds()}.
     * @see #lastActionTime
     * @see #expiration
     * @see #finalDurationSeconds
     */
    Date startTime;

    /**
     * If game is {@link SOCGame#OVER}, its {@link #getDurationSeconds()} when state became {@code OVER}.
     * Otherwise 0.
     * @since 2.3.00
     */
    private int finalDurationSeconds;

    /**
     * Expiration time for this game in milliseconds
     * (system clock time, not a duration from {@link #startTime});
     * Same format as {@link System#currentTimeMillis()}.
     * @see #startTime
     * @see #hasWarnedExpir
     */
    long expiration;

    /**
     * Has the server warned game's member clients that this game will expire soon?
     * See {@link #hasWarnedExpiration()} for details.
     * @see #expiration
     * @since 1.2.01
     */
    private boolean hasWarnedExpir;

    /**
     * The last time a game action happened; can be used to check for game inactivity.
     * Updated in {@link #updateAtTurn()}, {@link #putPiece(SOCPlayingPiece)},
     * and a few other game action methods.
     *<P>
     * Same format as {@link System#currentTimeMillis()}.
     * The server can set this field to 0 to tell itself to end a turn soon, but
     * otherwise the value should be a recent time.
     *<P>
     * At the end of a game, the server may increase this value by
     * {@link soc.server.SOCGameListAtServer#GAME_TIME_EXPIRE_MINUTES}
     * in order to remove it from the {@link soc.server.SOCGameTimeoutChecker}
     * run loop.
     *
     * @see #getStartTime()
     * @see #getExpiration()
     * @since 1.1.11
     */
    public long lastActionTime;

    /**
     * Used at server; was the most recent player action a bank trade?
     * If true, allow the player to undo that trade.
     * Updated whenever {@link #lastActionTime} is updated.
     *<P>
     * TODO: Consider lastActionType instead, it's more general.
     * @since 1.1.13
     */
    private boolean lastActionWasBankTrade;

    /**
     * Is the current robbery using the pirate ship, not the robber?
     * If true, victims will be based on adjacent ships, not settlements/cities.
     * Set in {@link #chooseMovePirate(boolean)}, {@link #movePirate(int, int)}, {@link #moveRobber(int, int)},
     * and other places that set gameState to {@link #PLACING_ROBBER} or {@link #PLACING_PIRATE}.
     *
     * @see #getRobberyPirateFlag()
     * @see #placingRobberForKnightCard
     * @since 2.0.00
     */
    private boolean robberyWithPirateNotRobber;
        // TODO: Consider refactor to create lastActionType instead, it's more general

    /**
     * Has the current player moved a ship already this turn?
     * Valid only when {@link #hasSeaBoard}.
     * @since 2.0.00
     */
    private boolean movedShipThisTurn;

    /**
     * List of ship edge coordinates placed this turn.
     * A ship cannot be placed and moved on the same turn.
     * Null when not {@link #hasSeaBoard}.
     * @see #canMoveShip(int, int)
     * @since 2.0.00
     */
    private Vector<Integer> shipsPlacedThisTurn;

    /**
     * The special inventory item currently being placed in state {@link #PLACING_INV_ITEM}, or null.
     * Can be set with {@link #setPlacingItem(SOCInventoryItem)} before or during that state.
     * @since 2.0.00
     */
    private SOCInventoryItem placingItem;

    /**
     * The number of normal turns; see {@link #getTurnCount()} for details.
     * @since 1.1.07
     */
    private int turnCount;

    /**
     * The number of normal rounds (each player has 1 turn per round, after initial placements), including this round.
     *  for gameoption N7: Roll no 7s during first # rounds.
     *  This is 0 during initial piece placement, and 1 when the first player is about to
     *  roll dice for the first time.  It becomes 2 when that first player's turn begins again.
     *  updated in {@link #updateAtTurn()}.
     * @since 1.1.07
     */
    private int roundCount;

    /**
     * create a new, active game
     *
     * @param gameName  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @see #SOCGame(String, SOCGameOptionSet, SOCGameOptionSet)
     */
    public SOCGame(final String gameName)
    {
        this(gameName, true, null, null);
    }

    /**
     * create a new, active game with options and optionally a scenario (game option {@code "SC"}).
     *
     * @param gameName  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param op game's {@link SOCGameOption}s if any; otherwise null.
     *           Will validate options and include optional scenario's {@link SOCScenario#scOpts} by calling
     *           {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)}
     *           with <tt>doServerPreadjust</tt> false,
     *           and set game's minimum version by calling
     *           {@link SOCVersionedItem#itemsMinimumVersion(Map)}.
     *           <P>
     *           When creating a game at the server, {@code op} must already be validated by calling
     *           {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)}
     *           with <tt>doServerPreadjust</tt> true.
     *           That call is also needed to add any game options from scenario ({@code "SC"}) if present.
     * @param knownOpts  All Known Options, for {@code op} validation and adding options from scenario if present;
     *           not null unless {@code op} is null
     * @throws IllegalArgumentException if op contains unknown options,
     *           or {@code knownOpts} is null but {@code op} is non-null
     * @since 1.1.07
     */
    public SOCGame(final String gameName, final SOCGameOptionSet op, final SOCGameOptionSet knownOpts)
        throws IllegalArgumentException
    {
        this(gameName, true, op, knownOpts);
    }

    /**
     * create a new game that can be ACTIVE or INACTIVE
     *
     * @param gameName  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param isActive  true if this is an active game, false for inactive
     * @throws IllegalArgumentException if game name fails
     *           {@link SOCMessage#isSingleLineAndSafe(String)}. This check was added in 1.1.07.
     */
    public SOCGame(final String gameName, final boolean isActive)
        throws IllegalArgumentException
    {
        this(gameName, isActive, null, null);
    }

    /**
     * create a new game that can be ACTIVE or INACTIVE, and have options
     * and optionally a scenario (game option {@code "SC"}).
     *
     * @param gameName  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param isActive  true if this is an active game, false for inactive
     * @param op if game has options, its set of {@link SOCGameOption}s; otherwise null.
     *           Will validate options and include optional scenario's {@link SOCScenario#scOpts} by calling
     *           {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)}
     *           with <tt>doServerPreadjust</tt> false,
     *           and set game's minimum version by calling
     *           {@link SOCVersionedItem#itemsMinimumVersion(Map)}.
     *           <P>
     *           New game will use {@code op}, not make a copy of {@code op}.
     *           <P>
     *           When creating a game at the server, {@code op} must already be validated by calling
     *           {@link SOCGameOptionSet#adjustOptionsToKnown(SOCGameOptionSet, boolean, SOCFeatureSet)}
     *           with <tt>doServerPreadjust</tt> true.
     *           That call is also needed to add any game options from scenario ({@code "SC"}) if present.
     * @param knownOpts  All Known Options, for {@code op} validation and adding options from scenario if present;
     *           not null unless {@code op} is null
     * @throws IllegalArgumentException if op contains unknown options, or if game name
     *             fails {@link SOCMessage#isSingleLineAndSafe(String)},
     *             or {@code knownOpts} is null but {@code op} is non-null
     * @since 1.1.07
     */
    public SOCGame
        (final String gameName, final boolean isActive, final SOCGameOptionSet op, final SOCGameOptionSet knownOpts)
        throws IllegalArgumentException
    {
        // For places to initialize fields, see also resetAsCopy().

        if (! SOCMessage.isSingleLineAndSafe(gameName))
            throw new IllegalArgumentException("gameName");

        active = isActive;
        inUse = false;
        name = gameName;
        if (op != null)
        {
            if (knownOpts == null)
                throw new IllegalArgumentException("knownOpts");

            // apply options from scenario, if any:
            {
                final Map<String, String> optProblems = op.adjustOptionsToKnown(knownOpts, false, null);
                if (optProblems != null)
                {
                    StringBuilder sb = new StringBuilder("op: unknown option(s): ");
                    DataUtils.mapIntoStringBuilder(optProblems, sb, null, "; ");
                    throw new IllegalArgumentException(sb.toString());
                }
            }

            hasSeaBoard = op.isOptionSet("SBL");
            final boolean wants6board = op.isOptionSet("PLB");
            final int maxpl = op.getOptionIntValue("PL", 4, false);
            if (wants6board || (maxpl > 4))
                maxPlayers = MAXPLAYERS;  // == 6
            else
                maxPlayers = 4;
            vp_winner = op.getOptionIntValue("VP", VP_WINNER_STANDARD, true);
            hasScenarioWinCondition = op.isOptionSet(SOCGameOptionSet.K_SC_CLVI)
                || op.isOptionSet(SOCGameOptionSet.K_SC_PIRI)
                || op.isOptionSet(SOCGameOptionSet.K_SC_WOND);
            clientVersionMinRequired = SOCVersionedItem.itemsMinimumVersion(op.getAll());
        } else {
            maxPlayers = 4;
            hasSeaBoard = false;
            vp_winner = VP_WINNER_STANDARD;
            hasScenarioWinCondition = false;
            clientVersionMinRequired = -1;
        }
        this.knownOpts = knownOpts;

        if (boardFactory == null)
            boardFactory = new SOCBoard.DefaultBoardFactory();
        board = boardFactory.createBoard(op, hasSeaBoard, maxPlayers);
        opts = op;

        players = new SOCPlayer[maxPlayers];
        seats = new int[maxPlayers];
        seatLocks = new SeatLockState[maxPlayers];
        boardResetVotes = new int[maxPlayers];
        spItems = new HashMap<String, ArrayList<SOCSpecialItem>>();

        for (int i = 0; i < maxPlayers; i++)
        {
            players[i] = new SOCPlayer(i, this);
            seats[i] = VACANT;
            seatLocks[i] = SeatLockState.UNLOCKED;
        }

        currentPlayerNumber = -1;
        firstPlayerNumber = -1;
        currentDice = -1;
        currentRoll = new RollResult();
        playerWithLargestArmy = -1;
        playerWithLongestRoad = -1;
        boardResetVoteRequester = -1;
        playerWithWin = -1;
        gameState = NEW;
        turnCount = 0;
        roundCount = 0;
        forcingEndTurn = false;
        askedSpecialBuildPhase = false;
        placingRobberForKnightCard = false;
        oldPlayerWithLongestRoad = new Stack<SOCOldLRStats>();
        lastActionWasBankTrade = false;
        movedShipThisTurn = false;
        if (hasSeaBoard)
            shipsPlacedThisTurn = new Vector<Integer>();

        if (maxPlayers > 4)
            numDevCards = NUM_DEVCARDS_6PLAYER;
        else
            numDevCards = NUM_DEVCARDS_STANDARD;

        if (active)
            startTime = new Date();
        lastActionTime = System.currentTimeMillis();
    }

    /**
     * Take the synchronization monitor for this game.
     * When done, release it with {@link #releaseMonitor()}.
     */
    public synchronized void takeMonitor()
    {
        //D.ebugPrintln("TAKE MONITOR");
        while (inUse)
        {
            try
            {
                wait(1000);  // timeout to help avoid deadlock
            }
            catch (InterruptedException e)
            {
                System.err.println("EXCEPTION IN takeMonitor() -- " + e);
            }
        }

        inUse = true;
    }

    /**
     * release the monitor for this game
     * @see #takeMonitor()
     */
    public synchronized void releaseMonitor()
    {
        //D.ebugPrintln("RELEASE MONITOR");
        inUse = false;
        this.notify();
    }

    /**
     * For saving a game snapshot, get the values of several private boolean fields:
     *<UL>
     * <LI> placingRobberForKnightCard
     * <LI> robberyWithPirateNotRobber (also available from {@link #getRobberyPirateFlag()})
     * <LI> askedSpecialBuildPhase
     * <LI> movedShipThisTurn
     * <LI> playingRoadBuildingCardForLastRoad (added in v2.5.00;
     *      related to {@link #doesCancelRoadBuildingReturnCard()})
     *</UL>
     * For some other fields to save, see
     * {@link #setFieldsForLoad(List, int, List, boolean, boolean, boolean, boolean, boolean)}.
     *
     * @return an array with the current values of those fields, in the order listed here
     * @since 2.3.00
     */
    public final boolean[] getFlagFieldsForSave()
    {
        return new boolean[]
        {
            placingRobberForKnightCard, robberyWithPirateNotRobber,
            askedSpecialBuildPhase, movedShipThisTurn,
            playingRoadBuildingCardForLastRoad
        };
    }

    /**
     * To help load a saved game snapshot, set the values of the private fields
     * previously returned from {@link #getFlagFieldsForSave()}, along with
     * values saved from some other getters where mentioned in parameter javadocs.
     *
     * @param cards  Deck, same format as {@link #getDevCardDeck()} but as {@link List} instead of {@code int[]}.
     *     Contents will be copied. Can be empty, but not null.
     * @param oldGameState  State from {@link #getOldGameState()}
     * @param shipsPlacedThisTurn  Ships from {@link #getShipsPlacedThisTurn()}. May be null or empty.
     * @param placingRobberForKnightCard
     * @param robberyWithPirateNotRobber
     * @param askedSpecialBuildPhase
     * @param movedShipThisTurn
     * @throws IllegalArgumentException if {@code cards} is null
     * @since 2.3.00
     */
    public void setFieldsForLoad
        (final List<Integer> cards, final int oldGameState, final List<Integer> shipsPlacedThisTurn,
         final boolean placingRobberForKnightCard, final boolean robberyWithPirateNotRobber,
         final boolean askedSpecialBuildPhase, final boolean movedShipThisTurn,
         final boolean playingRoadBuildingCardForLastRoad)
        throws IllegalArgumentException
    {
        if (cards == null)
            throw new IllegalArgumentException("cards");

        final int L = cards.size();
        if ((devCardDeck == null) || (L > devCardDeck.length))
            devCardDeck = new int[L];
        numDevCards = L;
        for (int i = 0; i < L; ++i)
            devCardDeck[i] = cards.get(i);

        this.oldGameState = oldGameState;

        if (shipsPlacedThisTurn == null)
        {
            if (this.shipsPlacedThisTurn != null)
                this.shipsPlacedThisTurn.clear();
        } else {
            if (this.shipsPlacedThisTurn == null)
                this.shipsPlacedThisTurn = new Vector<>(shipsPlacedThisTurn);
            else
                synchronized (this.shipsPlacedThisTurn)
                {
                    this.shipsPlacedThisTurn.clear();
                    this.shipsPlacedThisTurn.addAll(shipsPlacedThisTurn);
                }
        }

        this.placingRobberForKnightCard = placingRobberForKnightCard;
        this.robberyWithPirateNotRobber = robberyWithPirateNotRobber;
        this.askedSpecialBuildPhase = askedSpecialBuildPhase;
        this.movedShipThisTurn = movedShipThisTurn;
        this.playingRoadBuildingCardForLastRoad = playingRoadBuildingCardForLastRoad;
    }

    /**
     * @return allOriginalPlayers
     * @see #hasHumanPlayers()
     */
    public boolean allOriginalPlayers()
    {
        return allOriginalPlayers;
    }

    /**
     * Does this game contain any human players?
     * @return  True if at least one non-vacant seat has
     *          a human player (! {@link SOCPlayer#isRobot()}).
     * @see #allOriginalPlayers()
     * @since 1.1.18
     */
    public boolean hasHumanPlayers()
    {
        for (int i = 0; i < maxPlayers; ++i)
        {
            if (isSeatVacant(i))
                continue;
            if (! players[i].isRobot())
                return true;
        }

        return false;
    }

    /**
     * Get the time at which this game was created.
     * Used only at server.
     *<P>
     * To overwrite this time, call {@link #setTimeSinceCreated(int)}.
     *
     * @return the start time for this game, or null if not active when created
     * @see #getExpiration()
     * @see #getDurationSeconds()
     */
    public Date getStartTime()
    {
        return startTime;
    }

    /**
     * Get the time since this game was created, its age in seconds:
     * If game isn't over, the difference between current time and {@link #getStartTime()}.
     * Otherwise, its duration at game-over time.
     * @return  Game duration, rounded to the nearest second, or 0 if {@link #getStartTime()} is {@code null}
     * @see #setTimeSinceCreated(int)
     * @since 2.3.00
     */
    public int getDurationSeconds()
    {
        if ((gameState >= SOCGame.OVER) && (finalDurationSeconds != 0))
            return finalDurationSeconds;

        return (startTime != null)
            ? (int) (((System.currentTimeMillis() - startTime.getTime()) + 500) / 1000L)
            : 0;
    }

    /**
     * Set how long this game has existed.
     * Overwrites and replaces the times returned by {@link #getStartTime()}
     * and {@link #getDurationSeconds()}.
     * @param ageSeconds Game's new age in seconds; can be 0 but not negative
     * @throws IllegalArgumentException if {@code ageSeconds} &lt; 0
     * @since 2.3.00
     */
    public void setTimeSinceCreated(final int ageSeconds)
        throws IllegalArgumentException
    {
        if (ageSeconds < 0)
            throw new IllegalArgumentException("ageSeconds");

        final long t = System.currentTimeMillis() - (1000L * ageSeconds);
        if (startTime == null)
            startTime = new Date(t);
        else
            startTime.setTime(t);

        finalDurationSeconds = (gameState >= SOCGame.OVER) ? ageSeconds : 0;
    }

    /**
     * Get the expiration time at which this game will be destroyed.
     * Used only at server.
     * @return the expiration time in milliseconds
     *         (system clock time, not a duration from {@link #startTime});
     *         same epoch as {@link java.util.Date#getTime()}.
     *         Not used at client, returns 0.
     * @see #getStartTime()
     * @see #setExpiration(long)
     * @see #hasWarnedExpiration()
     */
    public long getExpiration()
    {
        return expiration;
    }

    /**
     * Has the server warned game's member clients that this game will expire soon?
     * All games get at least 1 warning before expiring. Otherwise a local-server game
     * might immediately expire when a sleeping laptop wakes.
     *<P>
     * Used only at server, which calls {@link #setWarnedExpiration()}.
     * @return true if this warning flag is set.
     *     Not used at client, returns false.
     * @see #getExpiration()
     * @since 1.2.01
     */
    public boolean hasWarnedExpiration()
    {
        return hasWarnedExpir;
    }

    /**
     * Set the {@link #hasWarnedExpiration()} flag.
     * To clear this flag, call {@link #setExpiration(long)} to change the expiration time.
     * @since 1.2.01
     */
    public void setWarnedExpiration()
    {
        hasWarnedExpir = true;
    }

    /**
     * Set or clear the scenario event listener.
     * Used with {@link #hasSeaBoard large sea board} scenario events.
     *<P>
     * Only one listener is allowed.  If you are setting the listener, it must currently be null.
     * @param sel  Listener, or null for none
     * @throws IllegalStateException  If listener already not null, <tt>sel</tt> is not null, and listener is not <tt>sel</tt>
     * @since 2.0.00
     */
    public void setGameEventListener(SOCGameEventListener sel)
        throws IllegalStateException
    {
        if ((sel != null) && (gameEventListener != null) && (gameEventListener != sel))
            throw new IllegalStateException("Listener already " + gameEventListener + ", wants " + sel);

        gameEventListener = sel;
    }

    /**
     * Set the expiration time at which this game will be destroyed.
     * Also clears the {@link #hasWarnedExpiration()} flag, for use when extending the game,
     * so server can warn again near the new expiration time.
     *<P>
     * Called at server, not client.
     *
     * @param ex  the absolute expiration time in milliseconds
     *            (system clock time, not a duration from {@link #startTime});
     *            same epoch as {@link java.util.Date#getTime()}
     * @see #getExpiration()
     */
    public void setExpiration(final long ex)
    {
        expiration = ex;
        hasWarnedExpir = false;
    }

    /**
     * For games at the server, the owner (creator) of the game.
     * Will be the name of a player / server connection.
     * Even if the owner leaves the game, their name may be retained here.
     * @return the owner's player name, or null if {@link #setOwner(String, String)} was never called
     * @since 1.1.10
     * @see #getOwnerLocale()
     */
    public String getOwner()
    {
        return ownerName;
    }

    /**
     * For games at the server, set the game owner (creator).
     * Will be the name of a player / server connection.
     * @param gameOwnerName The game owner's player name, or null to clear
     * @param gameOwnerLocale  The game owner's locale, or {@code null} to clear
     * @since 1.1.10
     * @throws IllegalStateException if <tt>gameOwnerName</tt> not null, but the game's owner is already set
     */
    public void setOwner(final String gameOwnerName, final String gameOwnerLocale)
        throws IllegalStateException
    {
        if ((ownerName != null) && (gameOwnerName != null))
            throw new IllegalStateException("owner already set");
        ownerName = gameOwnerName;
        ownerLocale = gameOwnerLocale;
    }

    /**
     * For games at the server, get the game owner (creator)'s locale, or {@code null}.
     * Used by {@code SOCGameListAtServer.addMember} to determine whether to set {@link #hasMultiLocales}.
     * @since 2.0.00
     */
    public final String getOwnerLocale()
    {
        return ownerLocale;
    }

    /**
     * Add a new player sitting at a certain seat, or sit them again in their same seat (rejoining after a disconnect).
     * Called at server and at client.
     *<P>
     * If the game just started, players are placing their first settlement and road
     * (gamestate &lt; {@link #START2A}), and the new player sits at a vacant seat,
     * check and update the first player number or last player number if necessary.
     * If the new player's {@code pn} is less than {@link #getCurrentPlayerNumber()},
     * they already missed their first settlement and road placement but will get their second one.
     *<P>
     * <B>Note:</B> Once the game has started and everyone already has placed their
     * first settlement and road (gamestate &gt;= {@link #START2A}), no one new
     * should sit down at a vacant seat, they won't have initial placements to receive
     * resources.  This method doesn't know if the seat has always been vacant, or if
     * a robot has just left the game to vacate the seat. So this restriction must be
     * enforced earlier, when the player requests sitting down at a vacant seat or at
     * a robot's position.
     *
     * @param plName  the player's name; must pass {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param pn    the player's requested player number; the seat number at which they would sit
     * @throws IllegalStateException if player is already sitting in
     *              another seat in this game, or if there are no open seats
     *              (based on seats[pn] == OCCUPIED, and game option "PL" or {@link #maxPlayers})
     *               via {@link #getAvailableSeatCount()}
     * @throws IllegalArgumentException if name fails {@link SOCMessage#isSingleLineAndSafe(String)}.
     *           This exception was added in 1.1.07.
     * @see #isSeatVacant(int)
     * @see #removePlayer(String, boolean)
     */
    public void addPlayer(final String plName, final int pn)
        throws IllegalStateException, IllegalArgumentException
    {
        if (! SOCMessage.isSingleLineAndSafe(plName))
            throw new IllegalArgumentException("plName");

        final boolean wasVacant = (seats[pn] == VACANT);
            // but not VACANT_PENDING_REPLACE, so firstPlayer/lastPlayer won't be recalc'd while temporarily vacant
        if (wasVacant)
        {
            if (0 == getAvailableSeatCount())
                throw new IllegalStateException("Game is full");
        }
        SOCPlayer already = getPlayer(plName);
        if ((already != null) && (pn != already.getPlayerNumber()))
        {
            throw new IllegalStateException("Already sitting in this game");
        }

        players[pn].setName(plName);
        seats[pn] = OCCUPIED;

        if ((gameState > NEW) && (gameState < OVER))
        {
            if (wasVacant && (gameState < START2A))
            {
                // Still placing first initial settlement + road; check first/last player number
                if (pn > lastPlayerNumber)
                    setFirstPlayer(firstPlayerNumber);  // recalc lastPlayerNumber
                else if (pn < firstPlayerNumber)
                    setFirstPlayer(pn);  // too late for first settlement, but can place their 2nd
            }

            allOriginalPlayers = false;
        }
    }

    /**
     * remove a player from their seat.
     * Player's name becomes {@code null}.  {@link #isSeatVacant(int) isSeatVacant(playerNum)} becomes true.
     *<P>
     * <b>If they are the current player,</b>
     * call this and then call {@link #canEndTurn(int)}.
     * You'll need to then call {@link #endTurn()} or {@link #forceEndTurn()}.
     *
     * @param plName  the player's name
     * @param hasReplacement  if true (server only), the leaving connection is a bot, and there's a
     *     waiting client who will be told to sit down in this bot's seat, so that isn't really becoming vacant.
     *     To prevent a race condition where a different client could take this seat as bot leaves,
     *     assumes single-threaded processing of incoming messages to this game.
     * @throws IllegalArgumentException if name isn't in this game.
     *           This exception was added in 1.1.07.
     * @see #isSeatVacant(int)
     * @see #addPlayer(String, int)
     */
    public void removePlayer(final String plName, final boolean hasReplacement)
        throws IllegalArgumentException
    {
        SOCPlayer pl = getPlayer(plName);
        if (pl == null)
            throw new IllegalArgumentException("plName");
        pl.setName(null);
        seats[pl.getPlayerNumber()] = (hasReplacement) ? VACANT_PENDING_REPLACE : VACANT;

        //D.ebugPrintln("seats["+pl.getPlayerNumber()+"] = VACANT");
    }

    /**
     * @return true if the seat is VACANT
     *
     * @param pn the number of the seat
     * @see #getAvailableSeatCount()
     * @see #addPlayer(String, int)
     * @see #removePlayer(String, boolean)
     */
    public boolean isSeatVacant(final int pn)
    {
        return (seats[pn] != OCCUPIED);
    }

    /**
     * How many seats are vacant and available for players?
     * Based on {@link #isSeatVacant(int)} and game
     * option "PL" (maximum players) or {@link #maxPlayers}.
     *<P>
     * <B>Note:</B> Once the game has started and everyone already has placed their
     * first settlement and road (gamestate &gt;= {@link #START2A}), no one new
     * should sit down at a vacant seat; see {@link #addPlayer(String, int)}.
     *
     * @return number of available vacant seats
     * @see #isSeatVacant(int)
     * @see #getPlayerCount()
     * @since 1.1.07
     */
    public int getAvailableSeatCount()
    {
        int availSeats;
        if (isGameOptionDefined("PL"))
            availSeats = getGameOptionIntValue("PL");
        else
            availSeats = maxPlayers;

        for (int i = 0; i < maxPlayers; ++i)
            if (seats[i] == OCCUPIED)
                --availSeats;

        return availSeats;
    }

    /**
     * Get a seat's lock state.
     * @param pn the number of the seat
     * @see #getSeatLocks()
     * @since 2.0.00
     */
    public SeatLockState getSeatLock(final int pn)
    {
        return seatLocks[pn];
    }

    /**
     * Get all seats' lock states.
     * @return all seats' lock states; an array indexed by player number, containing all seats in this game
     * @see #getSeatLock(int)
     * @since 2.0.00
     */
    public SeatLockState[] getSeatLocks()
    {
        return seatLocks;
    }

    /**
     * Lock or unlock a seat, or mark a bot's seat to be cleared on reset.
     * The meaning of "locked" is different when the game is still forming
     * (gameState {@link #NEW}) versus when the game is active.
     * For details, see the javadocs for {@link SeatLockState#LOCKED},
     * {@link SeatLockState#UNLOCKED UNLOCKED} and {@link SeatLockState#CLEAR_ON_RESET CLEAR_ON_RESET}.
     *<P>
     * For player consistency, seat locks can't be changed while {@link #getResetVoteActive()}
     * in server version 1.1.19 and higher.
     *<P>
     * Before v2.0.00, this was {@code lockSeat(pn)} and {@code unlockSeat(pn)}.
     *
     * @param pn the number of the seat
     * @param sl  the new lock state for this seat
     * @throws IllegalStateException if the game is still forming
     *     but {@code sl} is {@link SeatLockState#CLEAR_ON_RESET},
     *     or if {@link #getResetVoteActive()}
     * @throws IllegalArgumentException if {@code sl} is null
     * @see #setSeatLocks(SeatLockState[])
     * @since 2.0.00
     */
    public void setSeatLock(final int pn, final SeatLockState sl)
        throws IllegalStateException, IllegalArgumentException
    {
        if (sl == null)
            throw new IllegalArgumentException("sl");
        if (isAtServer
            && (getResetVoteActive()
                || ((sl == SeatLockState.CLEAR_ON_RESET) && (gameState == NEW))))
            throw new IllegalStateException();

        seatLocks[pn] = sl;
    }

    /**
     * At client, set all seats' locked or unlocked status. Sent from server while joining a game.
     *<P>
     * Since this is client-only, does not perform the server-side state check that
     * {@link #setSeatLock(int, SeatLockState)} does.
     *
     * @param sls  All seats' lock states; an array indexed by player number, containing all seats in this game
     * @throws IllegalArgumentException if {@code sls.length} is not {@link #maxPlayers}
     * @see {@link #setSeatLock(int, SeatLockState)}
     * @since 2.0.00
     */
    public void setSeatLocks(final SeatLockState[] sls)
        throws IllegalArgumentException
    {
        if (sls.length != maxPlayers)
            throw new IllegalArgumentException("length");

        for (int pn = 0; pn < sls.length; ++pn)
            seatLocks[pn] = sls[pn];
    }

    /**
     * How many players are currently sitting/playing in the game?
     * That is, how many seats are currently occupied by players?
     * Useful for house rule game option {@code "PLP"} on 6-player board.
     *
     * @return  Number of seats where {@link #isSeatVacant(int)} is false;
     *     minimum 0, maximum {@link #maxPlayers}.
     * @see #getAvailableSeatCount()
     * @since 2.3.00
     */
    public int getPlayerCount()
    {
        int n = 0;
        for (int pn = 0; pn < seats.length; ++pn)
            if (seats[pn] == OCCUPIED)
                ++n;

        return n;
    }

    /**
     * Get the player object sitting at this position.
     * <B>Does not check for vacant seats:</B>
     * Even if a seat is vacant, its unused {@link SOCPlayer} will be returned.
     * Call {@link #isSeatVacant(int)} to check for vacant seats.
     *
     * @param pn  the player number, in range 0 to {@link #maxPlayers}-1
     * @return the player object for a player id; never null if {@code pn} is in range.
     *     If a player leaves the game and another human or bot sits down at the
     *     now-vacant {@code pn}, the same {@code SOCPlayer} object is reused for the new player.
     * @throws ArrayIndexOutOfBoundsException if {@code pn} is out of range
     * @see #getPlayer(String)
     * @see #getPlayers()
     */
    public SOCPlayer getPlayer(final int pn)
        throws ArrayIndexOutOfBoundsException
    {
        return players[pn];
    }

    /**
     * Get the player sitting in this game with this name.
     * @return the player object for a player nickname.
     *   if there is no match, return null
     *
     * @param nn  the nickname
     * @see #getPlayer(int)
     */
    public SOCPlayer getPlayer(final String nn)
    {
        if (nn != null)
        {
            for (int i = 0; i < maxPlayers; i++)
            {
                if (! isSeatVacant(i))
                {
                    final SOCPlayer pl = players[i];  // may be null during end-of-game cleanup or reset
                    if ((pl != null) && nn.equals(pl.getName()))
                        return pl;
                }
            }
        }

        return null;
    }

    /**
     * Is the current player a robot which has been slow or buggy enough ("stubborn")
     * that their turn has been forced to end several times?
     * @return true if there's a current player and their {@link SOCPlayer#isStubbornRobot()} is true
     * @since 2.0.00
     */
    public boolean isCurrentPlayerStubbornRobot()
    {
        return (currentPlayerNumber >= 0) && players[currentPlayerNumber].isStubbornRobot();
    }

    /**
     * @return the name of the game
     * @see #setName(String)
     */
    public String getName()
    {
        return name;
    }

    /**
     * Rename this game. Useful for reloading a saved game snapshot.
     * Should do so only before adding to server's game list.
     * @param newName  New name for this game
     * @throws IllegalStateException if {@link #getGameState()} != {@link #LOADING}
     * @throws IllegalArgumentException if {@code newName} fails {@link SOCMessage#isSingleLineAndSafe(String)}
     * @since 2.3.00
     */
    public void setName(final String newName)
        throws IllegalStateException, IllegalArgumentException
    {
        if ((gameState != SOCGame.NEW) && (gameState != SOCGame.LOADING))
            throw new IllegalStateException("gameState");
        if (! SOCMessage.isSingleLineAndSafe(newName))
            throw new IllegalArgumentException("newName");

        name = newName;
    }

    /**
     * Get this game's {@link SOCGameOption}s, if any.
     *<P>
     * Before v2.5.00 this method returned a <tt>Map&lt;String, SOCGameOption&gt;</tt>.
     *
     * @return this game's options, or null
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     * @see #getGameOptionIntValue(String)
     * @see SOCGameOption#packOptionsToString(Map, boolean, boolean)
     */
    public SOCGameOptionSet getGameOptions()
    {
        return opts;
    }

    /**
     * Is this game option contained in the current game's options?
     * @param optKey Name of a {@link SOCGameOption}
     * @return True if option is defined in ths game's options, false otherwise
     * @since 1.1.07
     * @see #isGameOptionSet(String)
     * @see #getGameOptionIntValue(String)
     */
    public boolean isGameOptionDefined(final String optKey)
    {
        return (opts != null) ? opts.containsKey(optKey) : false;
    }

    /**
     * Is this boolean-valued game option currently set to true?
     * @param optKey Name of a {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_BOOL OTYPE_BOOL},
     *               {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL}
     *               or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     * @return True if option's boolean value is set, false if not set or not defined in this game's options
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #getGameOptionIntValue(String)
     * @see #getGameOptionStringValue(String)
     */
    public boolean isGameOptionSet(final String optKey)
    {
        // OTYPE_* - if a new type is added and it uses a boolean field, update this method's javadoc.

        return (opts != null) ? opts.isOptionSet(optKey) : false;
    }

    /**
     * What is this integer game option's current value?
     *<P>
     * Does not reference {@link SOCGameOption#getBoolValue()}, only the int value,
     * so this will return a value even if the bool value is false.
     * @param optKey A {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_INT OTYPE_INT},
     *               {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL},
     *               {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM}
     *               or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     * @return Option's current {@link SOCGameOption#getIntValue() intValue},
     *         or 0 if not defined in this game's options;
     *         OTYPE_ENUM's choices give an intVal in range 1 to n.
     * @since 1.1.07
     * @see #getGameOptionIntValue(String, int, boolean)
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     * @see #getGameOptionStringValue(String)
     */
    public int getGameOptionIntValue(final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return (opts != null) ? opts.getOptionIntValue(optKey) : 0;
    }

    /**
     * What is this integer game option's current value?
     *<P>
     * Can optionally reference {@link SOCGameOption#getBoolValue()}, not only the int value.
     * @param optKey A {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_INT OTYPE_INT},
     *               {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL},
     *               {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM}
     *               or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     * @param defValue  Default value to use if <tt>optKey</tt> not defined
     * @param onlyIfBoolSet  Check the option's {@link SOCGameOption#getBoolValue()} too;
     *               if false, return <tt>defValue</tt>.
     *               Do not set this parameter if the type doesn't use a boolean component.
     * @return Option's current {@link SOCGameOption#getIntValue() intValue},
     *         or <tt>defValue</tt> if not defined in the set of options;
     *         OTYPE_ENUM's and _ENUMBOOL's choices give an intVal in range 1 to n.
     * @since 2.5.00
     * @see #isOptionSet(String)
     * @see #getOptionIntValue(String)
     * @see #getOptionStringValue(String)
     */
    public int getGameOptionIntValue
        (final String optKey, final int defValue, final boolean onlyIfBoolSet)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return (opts != null) ? opts.getOptionIntValue(optKey, defValue, onlyIfBoolSet) : defValue;
    }

    /**
     * What is this string game option's current value?
     * @param optKey A {@link SOCGameOption} of type
     *               {@link SOCGameOption#OTYPE_STR OTYPE_STR}
     *               or {@link SOCGameOption#OTYPE_STRHIDE OTYPE_STRHIDE}
     * @return Option's current {@link SOCGameOption#getStringValue() getStringValue}
     *         or null if not defined in this game's options
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     * @see #getGameOptionIntValue(String)
     */
    public String getGameOptionStringValue(final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return (opts != null) ? opts.getOptionStringValue(optKey) : null;
    }

    /**
     * For use at server; lowest version of client which can connect to
     * this game (based on game options/features added in a given version),
     * or -1 if unknown or if this game has no options.
     * Calculated by {@link SOCVersionedItem#itemsMinimumVersion(Map)}.
     *<P>
     * For options where the minimum version changes with its current value, some
     * option version data is hardcoded in {@link SOCGameOption#getMinVersion(Map)},
     * executed on the server with a newer version than an older client.  So, the
     * version returned may be too low when called at that client.  The server
     * will let the client know if it's too old to join or create a game due
     * to options.
     *
     * @return game version, in same format as {@link soc.util.Version#versionNumber()}.
     * @see #getClientFeaturesRequired()
     * @since 1.1.06
     */
    public int getClientVersionMinRequired()
    {
        return clientVersionMinRequired;
    }

    /**
     * At server, get the optional client features needed to connect to this game, if any.
     * Those features are optional for clients to implement, but required for them to join this game.
     * @return this game's required client features, or {@code null} if none
     * @see #getClientVersionMinRequired()
     * @see #setClientFeaturesRequired(SOCFeatureSet)
     * @since 2.0.00
     */
    public SOCFeatureSet getClientFeaturesRequired()
    {
        return clientFeaturesRequired;
    }

    /**
     * At server, set the required client features returned by {@link #getClientFeaturesRequired()}, if any.
     * @param feats  New feature set, or {@code null} if no features are required;
     *     do not pass in an empty {@link SOCFeatureSet}
     * @since 2.0.00
     */
    public void setClientFeaturesRequired(SOCFeatureSet feats)
    {
        clientFeaturesRequired = feats;
    }

    /**
     * Given its features, can a client join this game? Assumes client's
     * {@link soc.server.SOCClientData#hasLimitedFeats} flag is true, otherwise they could join any game.
     * Calls {@link #checkClientFeatures(SOCFeatureSet, boolean) checkClientFeatures(cliFeats, true)}.
     * @param cliFeats  Client's limited subset of optional features,
     *     from {@link soc.server.SOCClientData#feats}, or {@code null} or empty set if no features
     * @return  True if client can join, false otherwise.
     *     Always true if {@link #getClientFeaturesRequired()} is {@code null}.
     * @since 2.0.00
     */
    public boolean canClientJoin(final SOCFeatureSet cliFeats)
    {
        return (null == checkClientFeatures(cliFeats, true));
    }

    /**
     * Check whether a client can join this game, given its feature set.
     * Assumes client's {@link soc.server.SOCClientData#hasLimitedFeats} flag is true,
     * otherwise they could join any game. If this game requires any client features, calls
     * {@link SOCFeatureSet#findMissingAgainst(SOCFeatureSet, boolean) cliFeats.findMissingAgainst(gameFeats, stopAtFirstFound)}.
     * @param cliFeats  Client's limited subset of optional features,
     *     from {@link soc.server.SOCClientData#feats}, or {@code null} or empty set if no features
     * @param stopAtFirstFound  True if caller needs to know only if any features are missing,
     *     but doesn't need the full list. If game's features and {@code cliFeats} are both not null,
     *     will stop checking after finding any missing feature.
     * @return  Null if client can join, otherwise the list of features which
     *     this game requires but are not included in {@code cliFeats}.
     *     Always null if {@link #getClientFeaturesRequired()} is {@code null}.
     *     If {@code stopAtFirstFound}, might return one missing feature instead of the full list.
     * @see #canClientJoin(SOCFeatureSet)
     * @since 2.0.00
     */
    public String checkClientFeatures(final SOCFeatureSet cliFeats, final boolean stopAtFirstFound)
    {
        if (clientFeaturesRequired == null)
            return null;  // anyone can join

        if (cliFeats == null)
            return clientFeaturesRequired.getEncodedList();  // cli has no features, this game requires some

        return cliFeats.findMissingAgainst(clientFeaturesRequired, stopAtFirstFound);
    }

    /**
     * @return whether this game was created by board reset of an earlier game
     * @see #resetAsCopy()
     * @since 1.1.00
     */
    public boolean isBoardReset()
    {
        return isFromBoardReset;
    }

    /**
     * Get the game board.
     * When {@link #hasSeaBoard}, {@code getBoard()} can always be cast to {@link SOCBoardLarge}.
     * @return the game board
     */
    public SOCBoard getBoard()
    {
        return board;
    }

    /**
     * @return the list of players
     * @see #getPlayer(int)
     */
    public SOCPlayer[] getPlayers()
    {
        return players;
    }

    /**
     * set the data for a player
     *
     * @param pn  the number of the player
     * @param pl  the player data
     * @throws IllegalArgumentException if pl is null
     */
    protected void setPlayer(final int pn, SOCPlayer pl)
    {
        if (pl != null)
            players[pn] = pl;
        else
            throw new IllegalArgumentException("null pl");
    }

    /**
     * @return the number of the current player, or -1 if the game isn't started yet
     */
    public int getCurrentPlayerNumber()
    {
        return currentPlayerNumber;
    }

    /**
     * Set the number of the current player, and check for winner.
     * If you want to update other game status, call {@link #updateAtTurn()} afterwards.
     * Called only at client: Server instead calls {@link #endTurn()}
     * or {@link #advanceTurn()}.
     * Check for gamestate {@link #OVER} after calling setCurrentPlayerNumber.
     * This is needed because a player can win only during their own turn;
     * if they reach winning points ({@link #vp_winner} or more) during another
     * player's turn, they don't win immediately.  When it later becomes their turn,
     * and setCurrentPlayerNumber is called, gamestate may become {@link #OVER}.
     *
     * @param pn  the player number; -1 is permitted at client in state {@link #OVER}
     *     or if game deleted or connection to server lost
     * @see #endTurn()
     * @see #checkForWinner()
     */
    public void setCurrentPlayerNumber(final int pn)
    {
        //D.ebugPrintln("SETTING CURRENT PLAYER NUMBER TO "+pn);
        if ((pn >= -1) && (pn < players.length))
        {
            currentPlayerNumber = pn;
            if ((pn >= 0) && ((players[pn].getTotalVP() >= vp_winner) || hasScenarioWinCondition))
                checkForWinner();
        }
    }

    /**
     * During the 6-player board's Special Building Phase, the player number whose
     * normal turn (roll, place, etc) has just ended.
     *<P>
     * The Special Building Phase changes {@link #getCurrentPlayerNumber()}.
     * So, it begins by calling {@link #advanceTurn()} to
     * the next player, and continues clockwise until
     * {@code getCurrentPlayerNumber()} == {@code getSpecialBuildingPlayerNumberAfter()}.
     * At that point, the Special Building Phase is over
     * and it's the next player's turn as usual.
     *
     * @return that player number if game state is {@link #SPECIAL_BUILDING}, -1 otherwise
     * @since 2.3.00
     */
    public int getSpecialBuildingPlayerNumberAfter()
    {
        return (gameState == SPECIAL_BUILDING) ? specialBuildPhase_afterPlayerNumber : -1;
    }

    /**
     * For use while reloading a saved game, sets {@link #getSpecialBuildingPlayerNumberAfter()}.
     * This field is ignored except during gameState {@link #SPECIAL_BUILDING}.
     * @param pn  Player number, or -1 to do nothing
     * @throws IllegalArgumentException if {@code pn} &lt; -1 or >= {@link #maxPlayers}
     * @since 2.3.00
     */
    public void setSpecialBuildingPlayerNumberAfter(final int pn)
    {
        if (pn == -1)
            return;
        if ((pn < -1) || (pn >= maxPlayers))
            throw new IllegalArgumentException("pn");

        specialBuildPhase_afterPlayerNumber = pn;
    }

    /**
     * The number of normal rounds (each player has 1 turn per round, after initial placements), including this round.
     *  This is 0 during initial piece placement, and 1 when the first player is about to
     *  roll dice for the first time.  It becomes 2 when that first player's turn begins again.
     *  @since 1.1.07
     *  @see #setRoundCount(int)
     *  @see #getTurnCount()
     */
    public int getRoundCount()
    {
        return roundCount;
    }

    /**
     * Set the number of rounds played, including this one.
     * See {@link #getRoundCount()} for details.
     * Used at client when joining a game in progress.
     * @param count  Round count
     * @since 2.0.00
     */
    public void setRoundCount(final int count)
    {
        roundCount = count;
    }

    /**
     * Has any player built a city?
     * Used with house-rule {@link SOCGameOption game option} {@code "N7C"}.
     *<P>
     * No setter is needed: During normal game play, {@link #putPiece(SOCPlayingPiece) putPiece(SOCCity)} will
     * update this flag. If a client joins a game after it's started, the server will send PUTPIECE messages
     * for any cities already on the board.
     * @return  True if {@link #putPiece}({@link SOCCity}) has been called for a non-temporary piece
     * @since 1.1.19
     */
    public boolean hasBuiltCity()
    {
        return hasBuiltCity;
    }

    /**
     * Get the current dice total from the most recent roll.
     *  -1 at start of game, 0 during player's turn before roll (state {@link #ROLL_OR_CARD}).
     * @return the current dice result
     * @see #rollDice()
     * @see SOCPlayer#getRolledResources()
     * @see #hasRolledSeven()
     */
    public int getCurrentDice()
    {
        return currentDice;
    }

    /**
     * set the current dice result
     *
     * @param dr  the dice result
     */
    public void setCurrentDice(final int dr)
    {
        currentDice = dr;

        if (dr == 7)
            hasRolledSeven = true;
    }

    /**
     * Has a 7 been rolled yet in this game?
     *<P>
     * Not sent over the network: Accurate at server, or client joining at start of game,
     * but not for a client who joined after game play started.
     *
     * @return true if {@link #rollDice()} at server, or {@link #setCurrentDice(int)} at client,
     *     has encountered a 7
     * @see #getCurrentDice()
     * @since 2.5.00
     */
    public boolean hasRolledSeven()
    {
        return hasRolledSeven;
    }

    /**
     * Current game state.  For general information about
     * what states are expected when, please see the javadoc for {@link #NEW}.
     *<P>
     * At the client, a newly joined game has state 0 until the server sends a GAMESTATE message.
     * Keep this in mind when initializing the game's user interface.
     *
     * @return the current game state
     * @see #isInitialPlacement()
     * @see #isSpecialBuilding()
     * @see #getOldGameState()
     */
    public int getGameState()
    {
        return gameState;
    }

    /**
     * set the current game state.
     * For general information about what states are expected when,
     * please see the javadoc for {@link #NEW}.
     *<P>
     * If the new state is {@link #OVER} and no playerWithWin yet determined, calls {@link #checkForWinner()};
     * caller should check {@link #getPlayerWithWin()} afterwards.
     *<P>
     * This method is generally called at the client, due to messages from the server
     * based on the server's complete game data.
     *<P>
     * At the client if this is the first time {@code gs} == {@link #ROLL_OR_CARD} during this game,
     * and state is currently in initial placement ({@link #START1A} to {@link #START3B}),
     * calls {@link #updateAtGameFirstTurn()}.
     *
     * @param gs  the game state
     * @see #updateAtTurn()
     */
    public void setGameState(final int gs)
    {
        final boolean cliFirstRegularTurn =
            (gs == ROLL_OR_CARD) && (! isAtServer) && (gameState >= START1A) && (gameState <= START3B);
                // not if gameState == 0, to prevent updateAtGameFirstTurn() when joining an already-started game

        if ((gs >= SOCGame.OVER) && (finalDurationSeconds == 0))
            finalDurationSeconds = getDurationSeconds();

        if ((gs == ROLL_OR_CARD) && (gameState == SPECIAL_BUILDING))
            oldGameState = PLAY1;  // Needed for isSpecialBuilding() to work at client
        else
            oldGameState = gameState;

        gameState = gs;

        if ((gameState == OVER) && (playerWithWin == -1))
            checkForWinner();

        if (cliFirstRegularTurn)
            updateAtGameFirstTurn();
    }

    /**
     * Set game state to {@link #OVER} and set {@link #finalDurationSeconds} if 0,
     * but don't call {@link #checkForWinner()} like {@link #setGameState(int)} does.
     * This should be called instead of setting <tt>{@link #gameState} = OVER</tt>.
     * @since 2.3.00
     */
    private void setGameStateOVER()
    {
        gameState = OVER;

        if (finalDurationSeconds == 0)
            finalDurationSeconds = getDurationSeconds();
    }

    /**
     * If the game board was reset, get the old game state.
     *
     * @deprecated Use {@link #getOldGameState()} to get the old game state without usage restrictions
     * @return the old game state
     * @throws IllegalStateException Game state must be RESET_OLD
     *    when called; during normal game play, oldGameState is private.
     * @since 1.1.00
     */
    public int getResetOldGameState() throws IllegalStateException
    {
        if (gameState != RESET_OLD)
            throw new IllegalStateException
                ("Current state is not RESET_OLD: " + gameState);

        return oldGameState;
    }

    /**
     * Get the old game state, for saving the game while state is temporarily changed
     * or reference at old copy of game (having current state {@link #RESET_OLD}) after a game reset.
     * @return the old {@link #getGameState()}
     * @since 2.3.00
     */
    public int getOldGameState()
    {
        return oldGameState;
    }

    /**
     * Are we in the Initial Placement part of the game?
     * Includes game states {@link #START1A} - {@link #START3B}
     * and {@link #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}.
     *<P>
     * Returns false if game hasn't started yet (state &lt; {@link #START1A}),
     * or is in normal gameplay or over (state &gt;= {@link #ROLL_OR_CARD}).
     *
     * @return true if in Initial Placement
     * @since 1.1.12
     * @see #isInitialPlacementRoundDone(int)
     */
    public final boolean isInitialPlacement()
    {
        return (gameState >= START1A) && (gameState < ROLL_OR_CARD);
    }

    /**
     * During {@link #isInitialPlacement()}, has a round of initial placement finished?
     * First round goes clockwise to place each player's first settlement and road/ship.
     * Second round reverses the direction of play to go counterclockwise for the second
     * settlements and roads/ships. (In some scenarios there's a third round going clockwise again.)
     * Then, the first turn of normal play starts with state {@link #ROLL_OR_CARD}.
     *<P>
     * Useful for prompting a robot player to place its next settlement
     * or roll its first normal turn.
     *<P>
     * True at state change from {@link #START1B} to {@link #START2A},
     * {@link #START2B} to {@link #START3A} or to {@link #ROLL_OR_CARD},
     * {@link #START3B} to {@link #ROLL_OR_CARD}.
     * @param prevState Previous game state, such as {@link #START1B}
     * @return  True if a round has finished
     * @since 2.0.00
     */
    public final boolean isInitialPlacementRoundDone(final int prevState)
    {
        return ((prevState == START1B) && (gameState == START2A))
            || ((prevState == START2B) && (gameState == START3A))
            || (((prevState == START2B) || (prevState == START3B))
                && (gameState == ROLL_OR_CARD));
    }

    /**
     * If true, this turn is being ended. Controller of game should call {@link #endTurn()}
     * whenever possible.  Usually set if we have called {@link #forceEndTurn()}, and
     * forced the current player to discard randomly, and are waiting for other players
     * to discard in gamestate {@link #WAITING_FOR_DISCARDS}.  Once all players have
     * discarded, the turn should be ended.
     * @see #forceEndTurn()
     * @since 1.1.00
     */
    public boolean isForcingEndTurn()
    {
        return forcingEndTurn;
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}, if true and
     * {@link #canPickGoldHexResources(int, ResourceSet)} in state {@link #WAITING_FOR_PICK_GOLD_RESOURCE},
     * this player's "gold hex" free resources include victory over a pirate fleet attack at a dice roll.
     * @param pn  Player number
     * @since 2.0.00
     */
    public final boolean isPickResourceIncludingPirateFleet(final int pn)
    {
        return (gameState == WAITING_FOR_PICK_GOLD_RESOURCE)
            && (players[pn] == currentRoll.sc_piri_fleetAttackVictim)
            && (currentRoll.sc_piri_fleetAttackRsrcs != null)
            && (currentRoll.sc_piri_fleetAttackRsrcs.contains(SOCResourceConstants.GOLD_LOCAL));
    }

    /**
     * Get the number of development cards remaining to be bought.
     * @return the number of dev cards in the deck
     * @see #setNumDevCards(int)
     * @see #buyDevCard()
     */
    public int getNumDevCards()
    {
        return numDevCards;
    }

    /**
     * At client, set the number of dev cards remaining in the deck.
     *
     * @param  nd  the number of dev cards in the deck
     * @see #getNumDevCards()
     */
    public void setNumDevCards(final int nd)
    {
        numDevCards = nd;
    }

    /**
     * At server, get the dev cards remaining in the unplayed deck.
     * Useful for saving and loading game snapshots.
     * @return Unplayed dev card deck: a copied array of dev card types from {@link SOCDevCardConstants}.
     *     May be empty, but never null.
     * @see #buyDevCard()
     * @see #getNumDevCards()
     * @since 2.3.00
     */
    public int[] getDevCardDeck()
    {
        int[] cards = new int[numDevCards];
        System.arraycopy(devCardDeck, 0, cards, 0, cards.length);
        return cards;
    }

    /**
     * Get all types of this game's {@link SOCSpecialItem}s, if any.
     * Only some scenarios and expansions use Special Items.
     * See {@link #getSpecialItems(String)} for Special Item details and locking.
     * @return  Special item type keys, or {@code null} if none
     * @since 2.0.00
     */
    public Set<String> getSpecialItemTypes()
    {
        return (spItems.isEmpty()) ? null : spItems.keySet();
    }

    /**
     * Get a list of all special items of a given type in this game.
     * This is not the union of each player's {@code spItems}, but a separately maintained list of items.
     * Only some scenarios and expansions use Special Items.
     *<P>
     * Item list is empty if game not started or if {@link #updateAtBoardLayout()} hasn't yet been called.
     *<P>
     * <B>Locks:</B> This getter is not synchronized: It's assumed that the structure of Special Item lists
     * is set up at game creation time, and not often changed.  If a specific item type or access pattern
     * requires synchronization, do so outside this class and document the details.
     *
     * @param typeKey  Special item type.  Typically a {@link SOCGameOption} keyname; see the {@link SOCSpecialItem}
     *     class javadoc for details.
     * @return  List of all special items of that type, or {@code null} if none; will never return an empty list.
     *     Some list items may be {@code null} depending on the list structure created by the scenario or expansion.
     * @since 2.0.00
     * @see SOCPlayer#getSpecialItems(String)
     * @see #getSpecialItemTypes()
     */
    public ArrayList<SOCSpecialItem> getSpecialItems(final String typeKey)
    {
        final ArrayList<SOCSpecialItem> ret = spItems.get(typeKey);
        if ((ret == null) || ret.isEmpty())
            return null;

        return ret;
    }

    /**
     * Get a special item of a given type, by index within the list of all items of that type in this game.
     * This is not the union of each player's {@code spItems}, but a separately maintained list of items.
     * Only some scenarios and expansions use Special Items.
     *<P>
     * Item list is empty if game not started or if {@link #updateAtBoardLayout()} hasn't yet been called.
     *<P>
     * <B>Locks:</B> This getter is not synchronized: It's assumed that the structure of Special Item lists
     * is set up at game creation time, and not often changed.  If a specific item type or access pattern
     * requires synchronization, do so outside this class and document the details.
     *
     * @param typeKey  Special item type.  Typically a {@link SOCGameOption} keyname; see the {@link SOCSpecialItem}
     *     class javadoc for details.
     * @param idx  Index within the list of special items of that type; must be within the list's current size
     * @return  The special item, or {@code null} if none of that type, or if that index is {@code null} within the list
     *     or is beyond the size of the list
     * @since 2.0.00
     * @see #getSpecialItem(String, int, int, int)
     * @see SOCPlayer#getSpecialItem(String, int)
     * @see SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)
     * @see SOCSpecialItem#playerSetItem(String, SOCGame, SOCPlayer, int, int, boolean)
     */
    public SOCSpecialItem getSpecialItem(final String typeKey, final int idx)
    {
        final ArrayList<SOCSpecialItem> li = spItems.get(typeKey);
        if (li == null)
            return null;

        try
        {
            return li.get(idx);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Get a special item of a given type, by index within the game's or player's list of all items of that type.
     * When both {@code gi} and {@code pi} are used, checks game first for an existing object, and if none (null),
     * checks the player. Only some scenarios and expansions use Special Items.
     *<P>
     * Item list is empty if game not started or if {@link #updateAtBoardLayout()} hasn't yet been called.
     *<P>
     * <B>Locks:</B> Call {@link #takeMonitor()} before calling this method.
     *
     * @param typeKey  Special item type.  Typically a {@link SOCGameOption} keyname; see the {@link SOCSpecialItem}
     *     class javadoc for details.
     * @param gi  Index within the game's list of special items of that type, or -1; must be within the list's current size
     * @param pi  Player item index (requires {@code pn} != -1), or -1
     * @param pn  Owning player number, or -1
     * @return  The special item, or {@code null} if none of that type or if that index is {@code null} within the list
     *     or is beyond the size of the list
     * @since 2.0.00
     * @see #getSpecialItem(String, int)
     * @see SOCPlayer#getSpecialItem(String, int)
     * @see SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)
     * @see SOCSpecialItem#playerSetItem(String, SOCGame, SOCPlayer, int, int, boolean)
     */
    public SOCSpecialItem getSpecialItem(final String typeKey, final int gi, final int pi, final int pn)
    {
        SOCSpecialItem item = null;

        if (gi != -1)
            item = getSpecialItem(typeKey, gi);

        if ((item == null) && (pn != -1) && (pi != -1))
            item = players[pn].getSpecialItem(typeKey, pi);

        return item;
    }

    /**
     * Add or replace a special item in the game's list of items of that type.
     * This is not the union of each player's {@code spItems}, but a separately maintained list of items.
     * Only some scenarios and expansions use Special Items.
     * @param typeKey  Special item type.  Typically a {@link SOCGameOption} keyname; see the {@link SOCSpecialItem}
     *     class javadoc for details.  If no list with this key exists, it will be created here.
     * @param idx  Index within the list of special items of that type; if this is past the list's current size,
     *     {@code null} elements will be inserted as needed until {@code idx} is a valid index
     *     If {@code idx} is within the list, the current element at that index will be replaced.
     * @param itm  Item object to set within the list
     * @return  The item previously at this index, or {@code null} if none
     * @throws IndexOutOfBoundsException  if {@code idx} &lt; 0
     * @since 2.0.00
     * @see SOCPlayer#setSpecialItem(String, int, SOCSpecialItem)
     */
    public SOCSpecialItem setSpecialItem(final String typeKey, final int idx, SOCSpecialItem itm)
        throws IndexOutOfBoundsException
    {
        ArrayList<SOCSpecialItem> li = spItems.get(typeKey);
        if (li == null)
        {
            li = new ArrayList<SOCSpecialItem>();
            spItems.put(typeKey, li);
        }

        final int L = li.size();
        if (idx < L)
        {
            return li.set(idx, itm);
        } else {
            for (int n = idx - L; n > 0; --n)  // if idx == L, n is 0, no nulls are needed
                li.add(null);

            li.add(itm);
            return null;
        }
    }

    /**
     * Get the special Inventory Item to be placed by the current player in state {@link #PLACING_INV_ITEM},
     * if any, from the most recent call to {@link #setPlacingItem(SOCInventoryItem)}.
     * See that method for lifecycle details.
     * @return  The item being placed, or {@code null}
     * @since 2.0.00
     */
    public SOCInventoryItem getPlacingItem()
    {
        return placingItem;
    }

    /**
     * Set or clear the special Inventory Item to be placed by the current player in state {@link #PLACING_INV_ITEM}.
     * Can be set before or during that state, either for convenience or because the server must send multiple messages.
     *<P>
     * The item can't be held between turns, and is lost if not placed on the current player's turn.
     * Inventory Item placement methods such as {@link #placePort(SOCPlayer, int, int)} will clear the item
     * to {@code null} when called in {@link #PLACING_INV_ITEM}.
     *
     * @param item  The item being placed, or {@code null} to clear
     * @since 2.0.00
     * @see #getPlacingItem()
     */
    public void setPlacingItem(SOCInventoryItem item)
    {
        placingItem = item;
    }

    /**
     * @return the player with the largest army
     */
    public SOCPlayer getPlayerWithLargestArmy()
    {
        if (playerWithLargestArmy != -1)
            return players[playerWithLargestArmy];
        else
            return null;
    }

    /**
     * set the player with the largest army
     *
     * @param pl  the player
     */
    public void setPlayerWithLargestArmy(SOCPlayer pl)
    {
        if (pl == null)
        {
            playerWithLargestArmy = -1;
        }
        else
        {
            playerWithLargestArmy = pl.getPlayerNumber();
        }
    }

    /**
     * @return the player with the longest road or trade route, or null if none
     */
    public SOCPlayer getPlayerWithLongestRoad()
    {
        if (playerWithLongestRoad != -1)
            return players[playerWithLongestRoad];
        else
            return null;
    }

    /**
     * set the player with the longest road or trade route
     *
     * @param pl  the player, or null to clear
     */
    public void setPlayerWithLongestRoad(SOCPlayer pl)
    {
        if (pl == null)
        {
            playerWithLongestRoad = -1;
        }
        else
        {
            playerWithLongestRoad = pl.getPlayerNumber();
        }
    }

    /**
     * Find the player who was declared winner at end of game.
     * This is determined in {@link #checkForWinner()}; there is no corresponding setter.
     *
     * @return the winning player, or null if none, or if game is not yet over.
     * @since 1.1.00
     */
    public SOCPlayer getPlayerWithWin()
    {
        if (playerWithWin != -1)
            return players[playerWithWin];
        else
            return null;
    }

    /**
     * For each player, call
     * {@link SOCPlayerNumbers#setLandHexCoordinates(int[]) pl.setLandHexCoordinates}
     * ({@link SOCBoardLarge#getLandHexCoords()}).
     * If the landhex coords are <tt>null</tt>, do nothing.
     *<P>
     * To be used with {@link #hasSeaBoard} (v3 board encoding) after creating (at server)
     * or receiving (at client) a new board layout.  So, call from
     * {@link #startGame()} or after {@link SOCBoardLarge#setLandHexLayout(int[])}.
     *<P>
     * For the v1 and v2 board encodings, the land hex coordinates never change, so
     * {@link SOCPlayerNumbers} knows them already.
     *
     * @since 2.0.00
     * @throws IllegalStateException if the board doesn't have the v3 encoding
     */
    public void setPlayersLandHexCoordinates()
        throws IllegalStateException
    {
        final int bef = board.getBoardEncodingFormat();
        if (bef != SOCBoard.BOARD_ENCODING_LARGE)
            throw new IllegalStateException("board encoding: " + bef);

        final int[] landHex = board.getLandHexCoords();
        if (landHex == null)
            return;
        for (int i = 0; i < maxPlayers; ++i)
            players[i].getNumbers().setLandHexCoordinates(landHex);
    }

    /**
     * If game is over, formulate a message to tell a player.
     * @param pl Player to tell (may be the winner)
     * @return A message of one of these forms:
     *       "The game is over; you are the winner!"
     *       "The game is over; <someone> won."
     *       "The game is over; no one won."
     * @throws IllegalStateException If the game state is not OVER
     * @since 1.1.00
     */
    public String gameOverMessageToPlayer(SOCPlayer pl)
        throws IllegalStateException
    {
        if (gameState != OVER)
            throw new IllegalStateException("This game is not over yet");

        String msg;
        SOCPlayer wn = getPlayerWithWin();

        if ((pl != null) && (pl == wn))
        {
            msg = "The game is over; you are the winner!";
        }
        else if (wn != null)
        {
            msg = "The game is over; " + wn.getName() + " won.";
        }
        else
        {
            // Just in case; don't think this can happen
            msg = "The game is over; no one won.";
        }

        return msg;
    }

    /**
     * advance the turn to the previous player,
     * used during initial placement. Does not change any other game state,
     * unless all players have left the game.
     * Clears the {@link #forcingEndTurn} flag.
     * @return true if the turn advances, false if all players have left and
     *          the gamestate has been changed here to {@link #OVER}.
     * @see #advanceTurn()
     */
    protected boolean advanceTurnBackwards()
    {
        final int prevCPN = currentPlayerNumber;

        //D.ebugPrintln("ADVANCE TURN BACKWARDS");
        forcingEndTurn = false;
        currentPlayerNumber--;

        if (currentPlayerNumber < 0)
            currentPlayerNumber = maxPlayers - 1;

        while (isSeatVacant (currentPlayerNumber))
        {
            --currentPlayerNumber;

            if (currentPlayerNumber < 0)
                currentPlayerNumber = maxPlayers - 1;

            if (currentPlayerNumber == prevCPN)
            {
                setGameStateOVER();  // Looped around, no one is here

                return false;
            }
        }

        return true;
    }

    /**
     * advance the turn to the next player. Does not change any other game state,
     * unless all players have left the game.
     * Clears the {@link #forcingEndTurn} flag.
     * @return true if the turn advances, false if all players have left and
     *          the gamestate has been changed here to {@link #OVER}.
     * @see #advanceTurnBackwards()
     */
    protected boolean advanceTurn()
    {
        final int prevCPN = currentPlayerNumber;

        currentPlayerNumber++;
        forcingEndTurn = false;

        if (currentPlayerNumber == maxPlayers)
            currentPlayerNumber = 0;

        while (isSeatVacant (currentPlayerNumber))
        {
            ++currentPlayerNumber;

            if (currentPlayerNumber == maxPlayers)
                currentPlayerNumber = 0;

            if (currentPlayerNumber == prevCPN)
            {
                setGameStateOVER();  // Looped around, no one is here

                return false;
            }
        }

        return true;
    }

    /**
     * For the 6-player board, check whether we should either start
     * or continue the {@link #SPECIAL_BUILDING Special Building Phase}.
     * This method does 1 of 4 possible things:
     *<UL>
     *<LI> A: If this isn't a 6-player board, or no player has asked
     *     to Special Build this turn, do nothing.
     *<LI> B: If we haven't started Special Building yet (gameState not {@link #SPECIAL_BUILDING})
     *     but it's asked for: Set gameState to {@link #SPECIAL_BUILDING}.
     *     Set {@link #specialBuildPhase_afterPlayerNumber} to current player number,
     *     because their turn is ending.
     *     (Special case: if current player wants to special build at start of
     *     their own turn, set _afterPlayerNumber to PREVIOUS player.)
     *     Then, set current player to the first player who wants to Special Build
     *     (it may be unchanged).
     *<LI> C: If we already did some players' Special Build this turn,
     *     and some remain, set current player to the next player
     *     who wants to Special Build.  gameState remains {@link #SPECIAL_BUILDING}.
     *<LI> D: If we already did some players' Special Build this turn,
     *     and no more players are left to special build, prepare to
     *     end the turn normally: Set gameState to {@link #PLAY1}.
     *     Set current player to the player whose turn is ending
     *     ({@link #specialBuildPhase_afterPlayerNumber}).
     *</UL>
     *<P>
     *<b>In 1.1.09 and later:</b>
     *<UL>
     *<LI> Player is allowed to Special Build at start of their own
     *     turn, only if they haven't yet rolled or played a dev card.
     *
     *<LI> During Special Building Phase, a player can ask to Special Build after
     *     the phase has begun, even if this means we temporarily go backwards
     *     in turn order.  (Normal turn order resumes at the end of the SBP.)
     *     The board game does not allow this out-of-order building.
     *</UL>
     *
     * @return true if gamestate is now {@link #SPECIAL_BUILDING}
     * @since 1.1.08
     */
    private boolean advanceTurnToSpecialBuilding()
    {
        if (! askedSpecialBuildPhase)
            return false;  // case "A" part 1: not 6-player or not asked

        final boolean alreadyInPhase = (gameState == SPECIAL_BUILDING);

        // See if anyone can place.
        // Set currentPlayerNumber if it's possible.
        // Unlike the board game, check every player, even if we'd go backwards
        // in turn order temporarily during the SBP.

        final int prevPlayer = currentPlayerNumber;
        boolean anyPlayerWantsSB = false;
        do
        {
            if (! advanceTurn())
                return false;  // All players have left

            anyPlayerWantsSB = players[currentPlayerNumber].hasAskedSpecialBuild();
        } while ((! anyPlayerWantsSB)
                  && (currentPlayerNumber != prevPlayer));

        // Postcondition: If anyPlayerWantsSB false,
        // then currentPlayerNumber is unchanged.

        if (! anyPlayerWantsSB)
        {
            // No one is left to special build.
            // Case "A" or "D".
            if (alreadyInPhase)
            {
                currentPlayerNumber = specialBuildPhase_afterPlayerNumber;
                gameState = PLAY1;  // case "D"
            }
            return false;
        }

        // Case "B" or "C".
        if (! alreadyInPhase)
        {
            // case "B":

            // usually the current player can't call for SBP.
            // unless, that is, it's the very start of their turn
            // and they haven't rolled or played a card yet.

            if (players[prevPlayer].hasAskedSpecialBuild()
                && (gameState == ROLL_OR_CARD)
                && ! players[prevPlayer].hasPlayedDevCard())
            {
                // remember previous player, re-set current player:

                gameState = SPECIAL_BUILDING;
                currentPlayerNumber = prevPlayer;
                if (! advanceTurnBackwards())
                    return false;  // all players have left
                specialBuildPhase_afterPlayerNumber = currentPlayerNumber;
                currentPlayerNumber = prevPlayer;
            } else {
                // usual case: ending current player's turn.

                gameState = SPECIAL_BUILDING;
                specialBuildPhase_afterPlayerNumber = prevPlayer;
            }
        }
        // currentPlayerNumber is set already by advanceTurn.

        return true;
    }

    /**
     * Reveal one land or water hex hidden by {@link SOCBoardLarge#FOG_HEX fog}.
     * Called at server and clients. Updates board.
     * If a {@link SOCBoard#WATER_HEX} is revealed, updates players' legal road and ship edges.
     * (Unrevealed fog hexes are treated as land when setting up legal and potential sets.)
     *
     * @param hexCoord  Coordinate of the hex to reveal
     * @param hexType   Revealed hex type, same value as {@link SOCBoard#getHexTypeFromCoord(int)},
     *                    from {@link SOCBoardLarge#revealFogHiddenHexPrep(int)}
     * @param diceNum   Revealed hex dice number, same value as {@link SOCBoard#getNumberOnHexFromCoord(int)}, or 0
     * @throws IllegalArgumentException if <tt>hexCoord</tt> isn't currently a {@link SOCBoardLarge#FOG_HEX FOG_HEX}
     * @throws IllegalStateException if <tt>! game.{@link #hasSeaBoard}</tt>
     * @since 2.0.00
     */
    public void revealFogHiddenHex(final int hexCoord, final int hexType, int diceNum)
        throws IllegalArgumentException, IllegalStateException
    {
        if (! hasSeaBoard)
            throw new IllegalStateException();

        final boolean wasWaterRemovedLegals = ((SOCBoardLarge) board).revealFogHiddenHex(hexCoord, hexType, diceNum);
            // throws IllegalArgumentException if any problem noted above

        if ((hexType == SOCBoard.WATER_HEX) || ((SOCBoardLarge) board).isHexAtBoardMargin(hexCoord))
        {
            // Previously not a legal ship edge, because
            // we didn't know if the fog hid land or water
            for (SOCPlayer pl : players)
            {
                pl.updateLegalShipsAddHex(hexCoord);
                if (wasWaterRemovedLegals)
                    pl.updatePotentialsAndLegalsAroundRevealedHex(hexCoord);
            }
        }
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI},
     * can a "gift" port be removed from this edge? <BR>
     * All these conditions must be met:
     *<UL>
     * <LI> {@link #hasSeaBoard} is true
     * <LI> Player is current player
     * <LI> Game state is {@link #PLACING_SHIP}, {@link #PLACING_FREE_ROAD1} or {@link #PLACING_FREE_ROAD2}
     * <LI> {@code edge} has a port
     * <LI> Port must be in no land area (LA == 0), or in {@link SOCBoardLarge#getPlayerExcludedLandAreas()}
     *</UL>
     * Does not check whether {@link SOCGameOptionSet#K_SC_FTRI} is set.
     *
     * @param pl  Player who would remove the port
     * @param edge  Port's edge coordinate
     * @return  True if that edge has a port which can be removed now by {@code pl}
     * @throws NullPointerException if {@code pl} is null
     * @since 2.0.00
     * @see SOCBoardLarge#canRemovePort(int)
     * @see #removePort(SOCPlayer, int)
     * @see #canPlacePort(SOCPlayer, int)
     */
    public boolean canRemovePort(final SOCPlayer pl, final int edge)
        throws NullPointerException
    {
        if (! hasSeaBoard)
            return false;
        if (pl.getPlayerNumber() != currentPlayerNumber)
            return false;
        if ((gameState != PLACING_SHIP) && (gameState != PLACING_FREE_ROAD1) && (gameState != PLACING_FREE_ROAD2))
            return false;

        return ((SOCBoardLarge) board).canRemovePort(edge);
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}, remove a "gift" port
     * at this edge to be placed elsewhere. Assumes {@link #canRemovePort(SOCPlayer, int)} has already
     * been called to validate player, edge, and game state.
     *<P>
     * This method will remove the port from the board.  At server it will also either add it to the
     * player's inventory or set the game's placingItem field; change the game state if placing
     * it immediately; and then fire {@link SOCPlayerEvent#REMOVED_TRADE_PORT}.
     *<P>
     * <b>At the server:</b> Not called directly; called only from other game/player methods.
     * Ports are currently removed only by a player's adjacent ship placement, so
     * {@link SOCPlayer#putPiece(SOCPlayingPiece, boolean)} would eventually call this
     * method if {@code canRemovePort(..)}.  The server calls
     * {@link SOCPlayer#getPortMovePotentialLocations(boolean) pl.getPortMovePotentialLocations(false)}
     * to determine if immediate placement is possible and thus required.
     *<P>
     * In the PlayerEvent handler or after this method returns, check
     * {@link #getGameState()} == {@link #PLACING_INV_ITEM} to see whether the port must immediately
     * be placed, or was instead added to the player's inventory.
     *<P>
     * <b>At the client:</b> Call this method to update the board data and player port flags.
     * The server will send other messages about game state and player inventory.  If those set
     * {@link #getGameState()} to {@link #PLACING_INV_ITEM}, the current player must choose a location for
     * port placement: Call
     * {@link SOCPlayer#getPortMovePotentialLocations(boolean) SOCPlayer.getPortMovePotentialLocations(true)}
     * to present options to the user.  The user will choose an edge and send a request to the server. The server
     * will validate and if valid call {@link #placePort(int)} and send updated status to the game's clients.
     *
     * @param pl  Player who is removing the port: Must be current player. Ignored at client, {@code null} is okay there.
     * @param edge  Port's edge coordinate
     * @throws UnsupportedOperationException if ! {@link #hasSeaBoard}
     * @throws NullPointerException if {@code pl} is null at server
     * @return  The port removed, with its {@link SOCInventoryItem#itype} in range
     *      -{@link SOCBoard#WOOD_PORT WOOD_PORT} to -{@link SOCBoard#MISC_PORT MISC_PORT}
     */
    public SOCInventoryItem removePort(SOCPlayer pl, final int edge)
        throws UnsupportedOperationException, NullPointerException
    {
        if (! hasSeaBoard)
            throw new UnsupportedOperationException();

        final int ptype = ((SOCBoardLarge) board).removePort(edge);
        for (int pn = 0; pn < maxPlayers; ++pn)
            players[pn].updatePortFlagsAfterRemove(ptype, true);

        if ((pl == null) || ! isAtServer)
            pl = players[currentPlayerNumber];

        // note: if this logic changes, also update SOCGameHandler.processDebugCommand_scenario

        final boolean placeNow = (pl.getPortMovePotentialLocations(false) != null);
        final SOCInventoryItem port = SOCInventoryItem.createForScenario(this, -ptype, true, false, false, ! placeNow);

        if (isAtServer)
        {
            if (! placeNow)
            {
                pl.getInventory().addItem(port);
            } else {
                placingItem = port;
                if (oldGameState != SPECIAL_BUILDING)
                    oldGameState = (gameState == SPECIAL_BUILDING) ? SPECIAL_BUILDING : PLAY1;
                gameState = PLACING_INV_ITEM;
            }

            // Fire the scenario player event, with the removed port's edge coord and type
            if (gameEventListener != null)
                gameEventListener.playerEvent
                    (this, pl, SOCPlayerEvent.REMOVED_TRADE_PORT, false, new IntPair(edge, ptype));
        }

        return port;
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI},
     * can a "gift" port be placed at this edge? <BR>
     * All these conditions must be met:
     *<UL>
     * <LI> {@link #hasSeaBoard} is true
     * <LI> Must be a coastal edge: {@link SOCBoardLarge#isEdgeCoastline(int)}
     * <LI> No port already at this edge or an adjacent edge
     * <LI> Player must have a settlement or city at one node (one end) of the edge
     * <LI> Player is current player
     *</UL>
     * Does not check whether {@link SOCGameOptionSet#K_SC_FTRI} is set.  Does not check {@link #getGameState()}.
     *
     * @param pl  Player who would place
     * @param edge  Edge where a port is wanted; coordinate not checked for validity.
     *             {@link SOCPlayer#getPortMovePotentialLocations(boolean)} can calculate edges that
     *             meet all conditions for {@code canPlacePort}.
     * @return  True if a port can be placed at this edge
     * @throws NullPointerException if {@code pl} is null
     * @see #canRemovePort(SOCPlayer, int)
     * @see #placePort(SOCPlayer, int, int)
     * @since 2.0.00
     */
    public boolean canPlacePort(final SOCPlayer pl, final int edge)
        throws NullPointerException
    {
        if (! hasSeaBoard)
            return false;
        if (pl.getPlayerNumber() != currentPlayerNumber)
            return false;
        if (! ((SOCBoardLarge) board).isEdgeCoastline(edge))
            return false;

        boolean plHasSettleOrCity = false;
        final int[] portNodes = board.getAdjacentNodesToEdge_arr(edge);
        for (int i = 0; i <= 1; ++i)
        {
            if (board.getPortTypeFromNodeCoord(portNodes[i]) != -1)
                return false;  // Already a port at edge or adjacent

            final SOCPlayingPiece ppiece = board.settlementAtNode(portNodes[i]);
            if ((ppiece != null) && (ppiece.getPlayerNumber() == currentPlayerNumber))
                plHasSettleOrCity = true;  // don't return yet, need to check both nodes for ports
        }

        return plHasSettleOrCity;
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI} in game state {@link #PLACING_INV_ITEM}, place
     * the "gift" port at this edge.  The port in {@link #getPlacingItem()} will be placed.  State becomes previous state
     * ({@link #PLAY1} or {@link #SPECIAL_BUILDING}).  Calls {@link #setPlacingItem(SOCInventoryItem) setPlacingItem(null)}.
     *<P>
     * Assumes {@link #canPlacePort(SOCPlayer, int)} has already been called to validate.
     *
     * @param edge  An available coastal edge adjacent to {@code pl}'s settlement or city,
     *          which should be checked with {@link #canPlacePort(SOCPlayer, int)}
     * @return ptype  The type of port placed (in range {@link SOCBoard#MISC_PORT MISC_PORT}
     *          to {@link SOCBoard#WOOD_PORT WOOD_PORT})
     * @throws IllegalStateException if not state {@link #PLACING_INV_ITEM}, or (internal error) {@link #placingItem} is null
     * @throws IllegalArgumentException if {@code ptype} is out of range, or
     *           if {@code edge} is not coastal (is between 2 land hexes or 2 water hexes)
     * @throws UnsupportedOperationException if ! {@link #hasSeaBoard}
     * @since 2.0.00
     * @see #placePort(SOCPlayer, int, int)
     * @see #removePort(SOCPlayer, int)
     */
    public int placePort(final int edge)
        throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException
    {
        if ((gameState != PLACING_INV_ITEM) || (placingItem == null))
            throw new IllegalStateException("state " + gameState + ", placingItem " + placingItem);

        final int ptype = -placingItem.itype;
        placePort(players[currentPlayerNumber], edge, ptype);  // clears placingItem
        gameState = oldGameState;

        return ptype;
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}, place a "gift" port at this edge.
     * Assumes {@link #canPlacePort(SOCPlayer, int)} has already been called to validate.
     * Through that validation, assumes {@code edge} is a coastal edge and {@code pl} has
     * an adjacent settlement or city.
     *<P>
     * Any port placement in state {@link #PLACING_INV_ITEM} calls
     * {@link #setPlacingItem(SOCInventoryItem) setPlacingItem(null)}.
     *<P>
     * Solely for debugging, a port can be placed at coordinates "off the side of the board"
     * with the intention of removing it soon to give to a player with the proper internal
     * state changes. To do so, call with {@code pl} {@code null}.
     *
     * @param pl  Player who is placing. Will call
     *          {@link SOCPlayer#setPortFlag(int, boolean) pl.setPortFlag}({@code ptype, true})
     *          because {@code pl}'s adjacent coastal settlement or city will become a port.
     *          Can be {@code null} if port is being placed for debugging.
     * @param edge  An available coastal edge adjacent to {@code pl}'s settlement or city,
     *          which should have been checked with {@link #canPlacePort(SOCPlayer, int)} already
     * @param ptype  The type of port (in range {@link SOCBoard#MISC_PORT MISC_PORT}
     *          to {@link SOCBoard#WOOD_PORT WOOD_PORT})
     * @throws IllegalArgumentException if {@code ptype} is out of range, or
     *           if {@code edge} is not coastal (is between 2 land hexes or 2 water hexes)
     *           when {@code pl != null}
     * @throws UnsupportedOperationException if ! {@link #hasSeaBoard}
     * @since 2.0.00
     * @see #placePort(int)
     * @see #removePort(SOCPlayer, int)
     */
    public void placePort(final SOCPlayer pl, final int edge, final int ptype)
        throws IllegalArgumentException, UnsupportedOperationException
    {
        if ((ptype < SOCBoard.MISC_PORT) || (ptype > SOCBoard.WOOD_PORT))
            throw new IllegalArgumentException("ptype: " + ptype);
        if (! hasSeaBoard)
            throw new UnsupportedOperationException();

        if (gameState == PLACING_INV_ITEM)
            placingItem = null;

        if (pl != null)
        {
            ((SOCBoardLarge) board).placePort(edge, ptype);  // validates coastal edge to calculate facing
            pl.setPortFlag(ptype, true);  // might already be set, that's fine
        } else {
            // assume off-board temp placement for debug: blindly calc facing from edge
            ((SOCBoardLarge) board).placePort
                (edge, ((SOCBoardLarge) board).getPortFacingFromEdge(edge, true), ptype);
        }
    }

    /**
     * Can this player place a ship on this edge?
     * The edge must be {@link SOCPlayer#isPotentialShip(int)}
     * and must not be adjacent to {@link SOCBoardLarge#getPirateHex()}.
     * Does not check game state, resources, or pieces remaining.
     *<P>
     * If {@link #isDebugFreePlacement()}, caller might also want to call
     * {@link SOCPlayer#canPlaceShip_debugFreePlace(int)}.
     *
     * @param pl  Player
     * @param shipEdge  Edge to place a ship
     * @return true if this player's ship could be placed there
     * @since 2.0.00
     * @see #canMoveShip(int, int, int)
     * @see SOCPlayer#getNumPieces(int)
     */
    public boolean canPlaceShip(final SOCPlayer pl, final int shipEdge)
    {
        if (! pl.isPotentialShip(shipEdge))
            return false;

        // check shipEdge vs. pirate hex
        {
            final SOCBoardLarge bL = (SOCBoardLarge) board;
            final int ph = bL.getPirateHex();
            if ((ph != 0) && bL.isEdgeAdjacentToHex(shipEdge, ph))
                return false;
        }

        return true;
    }

    /**
     * Get the list of ships placed this turn by the current player, if {@link #hasSeaBoard}.
     * @return copy of the list of ships placed this turn, if any.
     *     May be empty; won't be {@code null} unless ! {@link #hasSeaBoard}
     * @since 2.3.00
     */
    public List<Integer> getShipsPlacedThisTurn()
    {
        return (shipsPlacedThisTurn != null)
            ? new ArrayList<>(shipsPlacedThisTurn)
            : null;
    }

    /**
     * Put this piece on the board and update all related game state.
     * May change current player (at server) and gamestate.
     * Calls {@link #checkForWinner()}; gamestate may become {@link #OVER}.
     *<P>
     * For example, if game state when called is {@link #START2A} (or {@link #START3A} in
     * some scenarios), this is their final initial settlement, so it gives the player
     * some resources.
     *<P>
     * If {@link #hasSeaBoard} and {@link SOCGameOptionSet#K_SC_FOG _SC_FOG},
     * you should check for gamestate {@link #WAITING_FOR_PICK_GOLD_RESOURCE}
     * after calling, to see if they placed next to a gold hex revealed from fog
     * (see paragraph below).
     *
     *<H3>Actions taken:</H3>
     * Calls {@link SOCBoard#putPiece(SOCPlayingPiece)} and each player's
     * {@link SOCPlayer#putPiece(SOCPlayingPiece, boolean) SOCPlayer.putPiece(pp, false)}.
     * Updates longest road if necessary.
     * Calls {@link #advanceTurnStateAfterPutPiece()}.
     * (player.putPiece may also score Special Victory Point(s), see below.)
     *<P>
     * If the piece is a city, putPiece removes the settlement there.
     *<P>
     * If the piece is a settlement, and its owning player has their {@link SOCFortress} there
     * (scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}), putPiece removes the defeated fortress.
     *<P>
     * If placing this piece reveals any {@link SOCBoardLarge#FOG_HEX fog hex}, that happens first of all.
     * Hex is revealed (at server only) via {@link #putPieceCommon_checkFogHexes(int[], boolean)}.
     * Current player gets a resource from each revealed hex, and a scenario player event is fired.
     * See that method's javadoc for details.
     * putPiece's caller should check {@link SOCPlayer#getNeedToPickGoldHexResources()} != 0.
     * Revealing a gold hex from fog will set that player field and also
     * sets gamestate to {@link #WAITING_FOR_PICK_GOLD_RESOURCE}.
     *<P>
     * Calls {@link #checkForWinner()} and otherwise advances turn or state,
     * unless gamestate is {@link #LOADING}.
     * At the client, {@link #advanceTurnStateAfterPutPiece()} won't change the current player;
     * the server will send a message when the current player changes.
     *<P>
     * After the final initial road or ship placement, clears all players' potential settlements by
     * calling {@link #advanceTurnStateAfterPutPiece()} which at the server calls {@link #updateAtGameFirstTurn()}.
     *
     *<H3>Valid placements:</H3>
     * Because <tt>pp</tt> is not checked for validity, please call methods such as
     * {@link SOCPlayer#isPotentialRoad(int)} and {@link SOCPlayer#getNumPieces(int)}
     * to verify <tt>pp</tt> before calling this method, and also check
     * {@link #getGameState()} to ensure that piece type can be placed now.<BR>
     * For settlements, call {@link SOCPlayer#canPlaceSettlement(int)} to check potentials and other game conditions.<BR>
     * For ships, call {@link #canPlaceShip(SOCPlayer, int)} to check
     * the potentials and pirate ship location.
     *
     *<H3>Other things to note:</H3>
     * For some scenarios on the {@link SOCGame#hasSeaBoard large sea board}, placing
     * a settlement in a new Land Area may award the player a Special Victory Point (SVP).
     * This method will increment {@link SOCPlayer#getSpecialVP()}
     * and set the player's {@link SOCPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA} flag.
     *<P>
     * Some scenarios use extra initial pieces in fixed locations, placed in
     * {@link SOCBoardAtServer#startGame_putInitPieces(SOCGame)}.  To prevent the state or current player from
     * advancing, temporarily set game state {@link #READY} before calling putPiece for these.
     *<P>
     * During {@link #isDebugFreePlacement()}, the gamestate is not changed,
     * unless the current player gains enough points to win.
     *
     * @param pp the piece to put on the board; coordinates are not checked for validity, see "valid placements" note
     */
    public void putPiece(SOCPlayingPiece pp)
    {
        putPieceCommon(pp, false);
    }

    /**
     * Put a piece or temporary piece on the board, and update all related game state.
     * Update player potentials, longest road, reveal fog hexes at server, etc.
     * Common to {@link #putPiece(SOCPlayingPiece)} and {@link #putTempPiece(SOCPlayingPiece)}.
     * See {@link #putPiece(SOCPlayingPiece)} javadoc for more information on what putPieceCommon does.
     *
     * @param pp  The piece to put on the board; coordinates are not checked for validity
     * @param isTempPiece  Is this a temporary piece?  If so, do not change current
     *            player or gamestate, or call our {@link SOCGameEventListener}.
     * @since 1.1.14
     */
    private void putPieceCommon(SOCPlayingPiece pp, final boolean isTempPiece)
    {
        final int coord = pp.getCoordinates();

        /**
         * FOG_HEX: On large board, look for fog and reveal its hex if we're
         * placing a road or ship touching the fog hex's corner.
         * During initial placement, a settlement could reveal up to 3 hexes.
         * Current player gets a resource from each revealed hex.
         */
        if (hasSeaBoard && isAtServer && ! (pp instanceof SOCVillage))
        {
            if (pp instanceof SOCRoutePiece)
            {
                // roads, ships
                final int[] endHexes = ((SOCBoardLarge) board).getAdjacentHexesToEdgeEnds(coord);
                putPieceCommon_checkFogHexes(endHexes, false);
            }
            else if ((pp instanceof SOCSettlement) && isInitialPlacement())
            {
                // settlements
                final List<Integer> adjacHexes = board.getAdjacentHexesToNode(coord);
                final int L = adjacHexes.size();
                int[] seHexes = new int[L];
                for (int i = 0; i < L; ++i)
                    seHexes[i] = adjacHexes.get(i);
                putPieceCommon_checkFogHexes(seHexes, true);

                // Any settlement might reveal 1-3 fog hexes.
                // So, the player's revealed getNeedToPickGoldHexResources might be 0 to 3.
                // For the final initial settlement, this is recalculated below
                // to also include adjacent gold hexes that weren't hidden by fog.
            }
        }

        /**
         * call putPiece() on every player so that each
         * player's updatePotentials() function gets called
         */
        if (! (pp instanceof SOCVillage))
        {
            for (int i = 0; i < maxPlayers; i++)
                players[i].putPiece(pp, isTempPiece);
        }

        board.putPiece(pp);

        if ((pp instanceof SOCFortress) || (pp instanceof SOCVillage))
        {
            return;  // <--- Early return: Piece is part of initial layout ---
        }

        if ((! isTempPiece) && debugFreePlacement && (gameState <= START3B))
            debugFreePlacementStartPlaced = true;

        /**
         * if the piece is a city, remove the settlement there
         */
        final int pieceType = pp.getType();
        final SOCPlayer ppPlayer = pp.getPlayer();
        if (pieceType == SOCPlayingPiece.CITY)
        {
            if (! (isTempPiece || hasBuiltCity))
            {
                hasBuiltCity = true;  // for house-rule game option "N7C"
            }

            SOCSettlement se = new SOCSettlement(ppPlayer, coord, board);

            for (int i = 0; i < maxPlayers; i++)
                players[i].removePiece(se, pp, true);

            board.removePiece(se);
        }

        if (gameState == LOADING)
        {
            return;  // <---- Early return: Loading game, so skip any side effects ----
        }

        /**
         * the rare situation "if the piece is a settlement, remove the fortress there" is
         * handled in player.putPiece instead of here, because the SOCPlayer knows about the
         * fortress and settlement, and that single call can correlate the removal and placement.
         */

        /**
         * if this their final initial settlement, give the player some resources.
         * (skip for temporary pieces)
         */
        if ((! isTempPiece)
            && (pieceType == SOCPlayingPiece.SETTLEMENT)
            && ((gameState == START2A) || (gameState == START3A)))
        {
            final boolean init3 = isGameOptionDefined(SOCGameOptionSet.K_SC_3IP);
            final int lastInitSettle = init3 ? START3A : START2A;
            if ( (gameState == lastInitSettle)
                 || (debugFreePlacementStartPlaced
                     && (ppPlayer.getPieces().size() == (init3 ? 5 : 3))) )
            {
                SOCResourceSet resources = new SOCResourceSet();
                int goldHexAdjacent = 0;

                for (final int hexCoord : board.getAdjacentHexesToNode(coord))
                {
                    switch (board.getHexTypeFromCoord(hexCoord))
                    {
                    case SOCBoard.CLAY_HEX:
                        resources.add(1, SOCResourceConstants.CLAY);
                        break;

                    case SOCBoard.ORE_HEX:
                        resources.add(1, SOCResourceConstants.ORE);
                        break;

                    case SOCBoard.SHEEP_HEX:
                        resources.add(1, SOCResourceConstants.SHEEP);
                        break;

                    case SOCBoard.WHEAT_HEX:
                        resources.add(1, SOCResourceConstants.WHEAT);
                        break;

                    case SOCBoard.WOOD_HEX:
                        resources.add(1, SOCResourceConstants.WOOD);
                        break;

                    case SOCBoardLarge.GOLD_HEX:
                        if (hasSeaBoard)
                            ++goldHexAdjacent;
                    }
                }

                ppPlayer.getResources().add(resources);
                if (goldHexAdjacent > 0)
                    ppPlayer.setNeedToPickGoldHexResources(goldHexAdjacent);
            }
        }

        /**
         * update which player has longest road or trade route
         */
        if (pieceType != SOCPlayingPiece.CITY)
        {
            final int placingPN = ppPlayer.getPlayerNumber();

            if (pp instanceof SOCRoutePiece)
            {
                /**
                 * the affected player is the one who build the road or ship
                 */
                updateLongestRoad(placingPN);
            }
            else if (pieceType == SOCPlayingPiece.SETTLEMENT)
            {
                /**
                 * this is a settlement, check if it cut anyone else's road or trade route
                 * or (on sea boards) if this connects a player's own roads to their ships
                 */
                int[] roads = new int[maxPlayers];
                boolean ownRoad = false, ownShip = false;

                for (final int adjEdge : board.getAdjacentEdgesToNode(coord))
                {
                    /**
                     * check all roads and ships adjacent to this node
                     */
                    for (SOCRoutePiece road : board.getRoadsAndShips())
                    {
                        if (adjEdge != road.getCoordinates())
                            continue;

                        final int roadPN = road.getPlayerNumber();
                        roads[roadPN]++;

                        if (roadPN == placingPN)
                        {
                            if (road.isRoadNotShip())
                                ownRoad = true;
                            else
                                ownShip = true;
                        }
                    }
                }

                /**
                 * if a player other than the one who put the settlement
                 * down has 2 roads or ships adjacent to it, then we need to recalculate
                 * their longest road / trade route
                 */
                for (int i = 0; i < maxPlayers; i++)
                {
                    if ((i != placingPN) && (roads[i] == 2))
                    {
                        updateLongestRoad(i);

                        /**
                         * check to see if this created a tie
                         */
                        break;
                    }
                }

                /**
                 * if placing player has connected their own roads and ships
                 * at this new coastal settlement, recalculate their longest route
                 */
                if (ownRoad && ownShip)
                    updateLongestRoad(placingPN);
            }
        }

        /**
         * If temporary piece, don't update gamestate-related info.
         */
        if (isTempPiece)
        {
            return;   // <--- Early return: Temporary piece ---
        }

        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;

        /**
         * Remember ships placed this turn
         */
        if ((pp.getType() == SOCPlayingPiece.SHIP) && (gameState != LOADING))
            shipsPlacedThisTurn.add(Integer.valueOf(coord));

        /**
         * check if the game is over
         */
        if ((gameState > READY) && (oldGameState != SPECIAL_BUILDING))
            checkForWinner();

        /**
         * update the state of the game, and possibly current player
         */
        if (active)
            advanceTurnStateAfterPutPiece();
    }

    /**
     * On the large sea board, look for and reveal any adjacent fog hex,
     * if we're placing a road or ship touching the fog hex's corner node.
     * Reveal it before placing the new piece, so it's easier for
     * players and bots to updatePotentials (their data about the
     * board reachable through their roads/ships).
     * Each revealed fog hex triggers {@link SOCGameEvent#SGE_FOG_HEX_REVEALED}
     * and gives the current player that resource (if not desert or water or gold).
     * The server should send the clients messages to reveal the hex
     * and give the resource to that player.
     * If gold is revealed, calls
     * {@link SOCPlayer#setNeedToPickGoldHexResources(int) currentPlayer.setNeedToPickGoldHexResources(numGoldHexes)}.
     *<P>
     * Called only at server, only when {@link #hasSeaBoard}.
     * During initial placement, placing a settlement could reveal up to 3 hexes.
     *
     * @param hexCoords  Hex coordinates to check type for {@link SOCBoardLarge#FOG_HEX}
     * @param initialSettlement  Are we checking for initial settlement placement?
     *     If so, keep checking after finding a fog hex.
     * @since 2.0.00
     */
    private final void putPieceCommon_checkFogHexes(final int[] hexCoords, final boolean initialSettlement)
    {
        int goldHexes = 0;

        for (int i = 0; i < hexCoords.length; ++i)
        {
            final int hexCoord = hexCoords[i];
            if ((hexCoord != 0) && (board.getHexTypeFromCoord(hexCoord) == SOCBoardLarge.FOG_HEX))
            {
                final int encodedHexInfo =
                    ((SOCBoardLarge) board).revealFogHiddenHexPrep(hexCoord);
                final int hexType = encodedHexInfo >> 8;
                int diceNum = encodedHexInfo & 0xFF;
                if (diceNum == 0xFF)
                    diceNum = 0;

                revealFogHiddenHex(hexCoord, hexType, diceNum);
                if (currentPlayerNumber != -1)
                {
                    if ((hexType >= SOCResourceConstants.CLAY) && (hexType <= SOCResourceConstants.WOOD))
                        players[currentPlayerNumber].getResources().add(1, hexType);
                    else if (hexType == SOCBoardLarge.GOLD_HEX)
                        ++goldHexes;
                }

                if (gameEventListener != null)
                    gameEventListener.gameEvent
                        (this, SOCGameEvent.SGE_FOG_HEX_REVEALED, Integer.valueOf(hexCoord));

                if (! initialSettlement)
                    break;
                    // No need to keep looking, because only one end of the road or ship's
                    // edge is new; player was already at the other end, so it can't be fog.
            }
        }

        if ((goldHexes > 0) && (currentPlayerNumber != -1))
        {
            // ask player to pick a resource from the revealed gold hex
            // in advanceTurnStateAfterPutPiece()
            players[currentPlayerNumber].setNeedToPickGoldHexResources(goldHexes);
        }
    }

    /**
     * After placing a piece on the board, update the state of
     * the game, and possibly current player at server, for play to continue.
     *<P>
     * Called at server and at each client by {@link #putPieceCommon(SOCPlayingPiece, boolean)}.
     * At clients, the PUTPIECE message that triggers that call will soon be
     * followed by a GAMESTATE message to confirm the new state.
     *<P>
     * During {@link #isInitialPlacement()}, only the server advances game state; when client's game calls
     * this method it returns immediately. In {@link #START2B} or {@link #START3B} after the last initial
     * road/ship placement, at server this method calls {@link #updateAtGameFirstTurn()}
     * which includes {@link #updateAtTurn()}.
     *<P>
     * If {@link #debugFreePlacement}, does nothing unless current player's
     * {@link SOCPlayer#getNeedToPickGoldHexResources()} &gt; 0 and so the state should
     * change to one where they're prompted to pick their gains.
     *<P>
     * Also used in {@link #forceEndTurn()} to continue the game
     * after a cancelled piece placement in {@link #START1A}..{@link #START3B} .
     * If the current player number changes here, {@link #isForcingEndTurn()} is cleared.
     *<P>
     * This method is not called after placing a {@link SOCInventoryItem} on the board, which
     * happens only with some scenario options such as {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}.
     *
     * @return true if game is still active, false if all players have left and
     *          the gamestate has been changed here to {@link #OVER}.
     * @since 1.1.00
     */
    private boolean advanceTurnStateAfterPutPiece()
    {
        if (currentPlayerNumber < 0)
            return true;  // Game hasn't started yet

        if ((gameState < ROLL_OR_CARD) && ! isAtServer)
            return true;  // Only server advances state during initial placement

        final boolean needToPickFromGold
            = hasSeaBoard && (players[currentPlayerNumber].getNeedToPickGoldHexResources() != 0);

        if (debugFreePlacement && ! needToPickFromGold)
            return true;  // Free placement doesn't change state or player

        //D.ebugPrintln("CHANGING GAME STATE FROM "+gameState);

        switch (gameState)
        {
        case START1A:
            if (needToPickFromGold)
            {
                oldGameState = START1A;
                gameState = STARTS_WAITING_FOR_PICK_GOLD_RESOURCE;
            } else {
                gameState = START1B;
            }
            break;

        case START1B:
            if (needToPickFromGold)
            {
                oldGameState = START1B;
                gameState = STARTS_WAITING_FOR_PICK_GOLD_RESOURCE;
            } else {
                int tmpCPN = currentPlayerNumber + 1;
                if (tmpCPN >= maxPlayers)
                    tmpCPN = 0;

                while (isSeatVacant (tmpCPN))
                {
                    ++tmpCPN;

                    if (tmpCPN >= maxPlayers)
                        tmpCPN = 0;

                    if (tmpCPN == currentPlayerNumber)
                    {
                        setGameStateOVER();  // Looped around, no one is here

                        return false;
                    }
                }

                if (tmpCPN == firstPlayerNumber)
                {
                    // All have placed their first settlement/road.
                    // Begin second placement.
                    gameState = START2A;
                }
                else
                {
                    if (advanceTurn())
                        gameState = START1A;
                }
            }
            break;

        case START2A:
            if (needToPickFromGold)
            {
                oldGameState = START2A;
                gameState = STARTS_WAITING_FOR_PICK_GOLD_RESOURCE;
            } else {
                gameState = START2B;
            }
            break;

        case START2B:
            if (needToPickFromGold)
            {
                oldGameState = START2B;
                gameState = STARTS_WAITING_FOR_PICK_GOLD_RESOURCE;
            } else {
                int tmpCPN = currentPlayerNumber - 1;

                // who places next? same algorithm as advanceTurnBackwards.
                if (tmpCPN < 0)
                    tmpCPN = maxPlayers - 1;

                while (isSeatVacant (tmpCPN))
                {
                    --tmpCPN;

                    if (tmpCPN < 0)
                        tmpCPN = maxPlayers - 1;

                    if (tmpCPN == currentPlayerNumber)
                    {
                        setGameStateOVER();  // Looped around, no one is here

                        return false;
                    }
                }

                if (tmpCPN == lastPlayerNumber)
                {
                    // All have placed their second settlement/road.
                    if (! isGameOptionSet(SOCGameOptionSet.K_SC_3IP))
                    {
                        // Begin play.
                        // Player number is unchanged; "virtual" endTurn here.
                        // Don't clear forcingEndTurn flag, if it's set.
                        //
                        // Not done if at client; instead, server will send a TURN message
                        // from which client will call updateAtTurn and setGameState
                        // in order to call updateAtGameFirstTurn.
                        if (isAtServer)
                        {
                            gameState = ROLL_OR_CARD;
                            updateAtGameFirstTurn();
                        }
                    } else {
                        // Begin third placement.
                        gameState = START3A;
                    }
                }
                else
                {
                    if (advanceTurnBackwards())
                        gameState = START2A;
                }
            }
            break;

        case START3A:
            if (needToPickFromGold)
            {
                oldGameState = START3A;
                gameState = STARTS_WAITING_FOR_PICK_GOLD_RESOURCE;
            } else {
                gameState = START3B;
            }
            break;

        case START3B:
            if (needToPickFromGold)
            {
                oldGameState = START3B;
                gameState = STARTS_WAITING_FOR_PICK_GOLD_RESOURCE;
            } else {
                // who places next? same algorithm as advanceTurn.
                int tmpCPN = currentPlayerNumber + 1;
                if (tmpCPN >= maxPlayers)
                    tmpCPN = 0;

                while (isSeatVacant (tmpCPN))
                {
                    ++tmpCPN;

                    if (tmpCPN >= maxPlayers)
                        tmpCPN = 0;

                    if (tmpCPN == currentPlayerNumber)
                    {
                        setGameStateOVER();  // Looped around, no one is here

                        return false;
                    }
                }

                if (tmpCPN == firstPlayerNumber)
                {
                    // All have placed their third settlement/road.
                    // Begin play.  The first player to roll is firstPlayerNumber.
                    // "virtual" endTurn here.
                    // Don't clear forcingEndTurn flag, if it's set.
                    //
                    // Not done if at client; instead, server will send a TURN message
                    // from which client will call updateAtTurn and setGameState
                    // in order to call updateAtGameFirstTurn.
                    if (isAtServer)
                    {
                        currentPlayerNumber = firstPlayerNumber;
                        gameState = ROLL_OR_CARD;
                        updateAtGameFirstTurn();
                    }
                }
                else
                {
                    if (advanceTurn())
                        gameState = START3A;
                }
            }
            break;

        case PLAY1:
            // PLAY1 is the gamestate when moveShip calls putPiece.
            // In scenario _SC_FOG, moving a ship might reveal a gold hex.
            // In scenario _SC_PIRI, PLAY1 is the gamestate when a SOCFortress is conquered
            //   and the replacement SOCSettlement is placed by attackPirateFortress.
            //   No special action is needed here.
            if (needToPickFromGold)
            {
                oldGameState = PLAY1;
                gameState = WAITING_FOR_PICK_GOLD_RESOURCE;
            }
            break;

        case PLACING_ROAD:
            // fall through
        case PLACING_SETTLEMENT:
            // fall through
        case PLACING_CITY:
            // fall through
        case PLACING_SHIP:
            if (needToPickFromGold)
            {
                if (oldGameState != SPECIAL_BUILDING)
                    oldGameState = PLAY1;
                gameState = WAITING_FOR_PICK_GOLD_RESOURCE;
            }
            else if (oldGameState != SPECIAL_BUILDING)
            {
                gameState = PLAY1;
            }
            else
            {
                gameState = SPECIAL_BUILDING;
            }
            break;

        case PLACING_FREE_ROAD1:
            if (needToPickFromGold)
            {
                oldGameState = PLACING_FREE_ROAD2;
                gameState = WAITING_FOR_PICK_GOLD_RESOURCE;
            } else {
                gameState = PLACING_FREE_ROAD2;
            }
            break;

        case PLACING_FREE_ROAD2:
            {
                final int nextState;
                if (currentDice != 0)
                    nextState = PLAY1;
                else
                    nextState = ROLL_OR_CARD;  // played dev card before roll

                if (needToPickFromGold)
                {
                    oldGameState = nextState;
                    gameState = WAITING_FOR_PICK_GOLD_RESOURCE;
                } else {
                    gameState = nextState;
                }

                playingRoadBuildingCardForLastRoad = false;
            }
            break;

        // case PLACING_INV_ITEM:
            //    No advance needed if that's the current state; in _SC_FTRI
            //    we're here because the player placed a ship on a special edge
            //    with a port, and that changed the state, we're still waiting
            //    for the player to place their port.  State mentioned here
            //    only for completeness.

        }

        //D.ebugPrintln("  TO "+gameState);
        return true;
    }

    /**
     * A temporary piece has been put on the board; update all related game state.
     * Update player potentials, longest road, etc.
     * Does not advance turn or update gamestate-related fields.
     *
     * @param pp the piece to put on the board
     *
     * @see #undoPutTempPiece(SOCPlayingPiece)
     * @see #saveLargestArmyState()
     */
    public void putTempPiece(SOCPlayingPiece pp)
    {
        //D.ebugPrintln("@@@ putTempPiece "+pp);

        /**
         * save who the last lr player was
         */
        oldPlayerWithLongestRoad.push(new SOCOldLRStats(this));

        putPieceCommon(pp, true);
    }

    /**
     * Can this player currently move this ship, based on game state and
     * their trade routes and settlements/cities?
     * Must be current player.  Game state must be {@link #PLAY1}.
     *<P>
     * Use this method to check a specific move-from location.
     * Use {@link #canMoveShip(int, int, int)} to also check a specific move-to location.
     *<P>
     * Only the ship at the newer end of an open trade route can be moved.
     * So, to move a ship, one of its end nodes must be clear: No
     * settlement or city, and no other adjacent ship on the other
     * side of the node.
     *<P>
     * You cannot place a ship, and then move the same ship, during the same turn.
     * You cannot move a ship from an edge of the pirate ship's hex.
     *<P>
     * Trade routes can branch, so it may be that more than one ship
     * could be moved.  The game limits players to one move per turn.
     *
     * @param pn   Player number
     * @param fromEdge  Edge coordinate to move the ship from; must contain this player's ship
     * @return  The ship at {@code fromEdge} if player can move that ship now; {@code null} otherwise
     * @see #canMoveShip(int, int, int)
     * @since 2.0.00
     */
    public SOCShip canMoveShip(final int pn, final int fromEdge)
    {
        if (movedShipThisTurn || ! (hasSeaBoard && (currentPlayerNumber == pn) && (gameState == PLAY1)))
            return null;
        if (shipsPlacedThisTurn.contains(Integer.valueOf(fromEdge)))
            return null;

        // check fromEdge vs. pirate hex
        {
            SOCBoardLarge bL = (SOCBoardLarge) board;
            final int ph = bL.getPirateHex();
            if ((ph != 0) && bL.isEdgeAdjacentToHex(fromEdge, ph))
                return null;
        }

        final SOCPlayer pl = players[pn];
        final SOCRoutePiece pieceAtFrom = pl.getRoadOrShip(fromEdge);
        if ((pieceAtFrom == null) || pieceAtFrom.isRoadNotShip())
            return null;
        SOCShip canShip = (SOCShip) pieceAtFrom;
        if (! pl.canMoveShip(canShip))
            return null;

        return canShip;
    }

    /**
     * Can this player currently move this ship to this new coordinate,
     * based on game state and their trade routes and settlements/cities?
     * Must be current player.  Game state must be {@link #PLAY1}.
     *<P>
     * Use this method to check a specific move-from and move-to location pair.
     * Use {@link #canMoveShip(int, int)} to check only the move-from.
     *<P>
     * Only the ship at the newer end of an open trade route can be moved.
     * So, to move a ship, one of <tt>fromEdge</tt>'s end nodes must be
     * clear: No settlement or city, and no other adjacent ship on the other
     * side of the node.
     *<P>
     * The new location <tt>toEdge</tt> must also be a potential ship location,
     * even if <tt>fromEdge</tt> was unoccupied; calls
     * {@link SOCPlayer#isPotentialShipMoveTo(int, int) pn.isPotentialShipMoveTo(toEdge, fromEdge)}
     * to check that.
     *<P>
     * You cannot move a ship to or from an edge of the pirate ship's hex.
     *<P>
     * Trade routes can branch, so it may be that more than one ship
     * could be moved.  The game limits players to one move per turn.
     *<P>
     * <B>Scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}:</B><br>
     * Ship movement options are limited, because the route can't branch
     * and only a few sea edges are legal for placement.
     *
     * @param pn   Player number
     * @param fromEdge  Edge coordinate to move the ship from; must contain this player's ship.
     * @param toEdge    Edge coordinate to move to; must be different than <tt>fromEdge</tt>.
     *            Checks {@link SOCPlayer#isPotentialShip(int) players[pn].isPotentialShip(toEdge)}.
     * @return  The ship at {@code fromEdge} if player can move that ship now; {@code null} otherwise
     * @see #canMoveShip(int, int)
     * @see #moveShip(SOCShip, int)
     * @since 2.0.00
     */
    public SOCShip canMoveShip(final int pn, final int fromEdge, final int toEdge)
    {
        if (fromEdge == toEdge)
            return null;
        final SOCPlayer pl = players[pn];
        if (! pl.isPotentialShipMoveTo(toEdge, fromEdge))
            return null;

        // check toEdge vs. pirate hex
        {
            SOCBoardLarge bL = (SOCBoardLarge) board;
            final int ph = bL.getPirateHex();
            if ((ph != 0) && bL.isEdgeAdjacentToHex(toEdge, ph))
                return null;
        }

        return canMoveShip(pn, fromEdge);  // <-- checks most other conditions
    }

    /**
     * Move this ship on the board and update all related game state.
     * Calls {@link #checkForWinner()}; gamestate may become {@link #OVER}
     * if a player gets the longest trade route.
     *<P>
     * Calls {@link #undoPutPieceCommon(SOCPlayingPiece, boolean, boolean) undoPutPieceCommon(sh, false, true)}
     * and {@link #putPiece(SOCPlayingPiece)}.
     * Updates longest trade route.
     * Not for use with temporary pieces.
     *<P>
     *<b>Note:</b> Because <tt>sh</tt> and <tt>toEdge</tt>
     * are not checked for validity, please call
     * {@link #canMoveShip(int, int, int)} before calling this method.
     *<P>
     * The call to putPiece incorrectly adds the moved ship's
     * new location to {@link #getShipsPlacedThisTurn()}, but since
     * we can only move 1 ship per turn, the add is harmless.
     *<P>
     * During {@link #isDebugFreePlacement()}, the gamestate is not changed,
     * unless the current player gains enough points to win.
     *<P>
     * <B>Scenario option {@link SOCGameOptionSet#K_SC_FOG _SC_FOG}:</B><br>
     * moveShip's caller should check {@link SOCPlayer#getNeedToPickGoldHexResources()} != 0.
     * Revealing a gold hex from fog will set that player field and also
     * sets gamestate to {@link #WAITING_FOR_PICK_GOLD_RESOURCE}.
     *
     * @param sh the ship to move on the board; its coordinate must be
     *           the edge to move from. Must not be a temporary ship.
     * @param toEdge    Edge coordinate to move to
     * @since 2.0.00
     */
    public void moveShip(SOCShip sh, final int toEdge)
    {
        undoPutPieceCommon(sh, false, true);
        putPiece(new SOCShip(sh.getPlayer(), toEdge, board));  // calls checkForWinner, etc
        movedShipThisTurn = true;
    }

    /**
     * Remove this ship from the board and update all related game state.
     * Used in scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}
     * by {@link #attackPirateFortress(SOCShip)} and at the client.
     *<P>
     * Calls {@link #undoPutPieceCommon(SOCPlayingPiece, boolean, boolean) undoPutPieceCommon(sh, false, false)}.
     * Not for use with temporary pieces or ship moves.
     *
     * @param sh  the ship to remove
     * @since 2.0.00
     */
    public void removeShip(SOCShip sh)
    {
        undoPutPieceCommon(sh, false, false);
    }

    /**
     * undo the putting of a temporary or initial piece
     * or a ship being moved.
     * If state is START2B or START3B and resources were given, they will be returned.
     *<P>
     * If a ship is removed in scenario {@code _SC_PIRI}, makes sure its player's
     * {@link SOCPlayer#getNumWarships()} is never more than the number of their ships on the board.
     *
     * @param pp  the piece to remove from the board
     * @param isTempPiece  Is this a temporary piece?  If so, do not call the
     *            game's {@link SOCGameEventListener}.
     * @param isMoveOrReplacement  Is the piece really being moved to a new location, or replaced with another?
     *            If so, don't remove its {@link SOCPlayingPiece#specialVP} from player.
     * @since 1.1.00
     */
    protected void undoPutPieceCommon
        (SOCPlayingPiece pp, final boolean isTempPiece, final boolean isMoveOrReplacement)
    {
        //D.ebugPrintln("@@@ undoPutTempPiece "+pp);
        board.removePiece(pp);

        //
        // call undoPutPiece() on every player so that
        // they can update their potentials
        //
        for (int i = 0; i < maxPlayers; i++)
        {
            players[i].undoPutPiece(pp, isMoveOrReplacement);   // If state START2B or START3B, will also zero resources
        }

        //
        // if the piece is a city, put the settlement back
        //
        if (pp.getType() == SOCPlayingPiece.CITY)
        {
            SOCSettlement se = new SOCSettlement(pp.getPlayer(), pp.getCoordinates(), board);

            for (int i = 0; i < maxPlayers; i++)
            {
                players[i].putPiece(se, isTempPiece);
            }

            board.putPiece(se);
        }
    }

    /**
     * undo the putting of a temporary piece
     *
     * @param pp  the piece to remove from the board
     *
     * @see #undoPutInitSettlement(SOCPlayingPiece)
     * @see #restoreLargestArmyState()
     */
    public void undoPutTempPiece(SOCPlayingPiece pp)
    {
        undoPutPieceCommon(pp, true, false);

        //
        // update which player has longest road
        //
        SOCOldLRStats oldLRStats = oldPlayerWithLongestRoad.pop();
        oldLRStats.restoreOldStats(this);
    }

    /**
     * undo the putting of an initial settlement.
     * If state is START2B or START3B and resources were given, they will be returned.
     * Player is unchanged; state will become START1A or START2A or START3A.
     * Not for use with temporary pieces (use {@link #undoPutTempPiece(SOCPlayingPiece)} instead).
     *
     * @param pp the piece to remove from the board
     * @see #canCancelBuildPiece(int)
     * @since 1.1.00
     */
    public void undoPutInitSettlement(SOCPlayingPiece pp)
    {
        if ((gameState != START1B) && (gameState != START2B) && (gameState != START3B))
            throw new IllegalStateException("Cannot remove at this game state: " + gameState);
        if (pp.getType() != SOCPlayingPiece.SETTLEMENT)
            throw new IllegalArgumentException("Not a settlement: type " + pp.getType());
        if (pp.getCoordinates() != pp.getPlayer().getLastSettlementCoord())
            throw new IllegalArgumentException("Not coordinate of last settlement");

        undoPutPieceCommon(pp, false, false);  // Will also zero resources via player.undoPutPiece

        if (gameState == START1B)
            gameState = START1A;
        else if (gameState == START2B)
            gameState = START2A;
        else // gameState == START3B
            gameState = START3A;
    }

    /**
     * Initialize server-only game fields.
     * Called from {@link #startGame()} and saved-game loader.
     * Sets {@link #isAtServer} and {@link #allOriginalPlayers()} flags.
     * Updates {@link #lastActionTime}.
     * Initializes misc fields like each player's {@link SOCPlayer#pendingMessagesOut}.
     * @since 2.3.00
     */
    public void initAtServer()
    {
        isAtServer = true;

        pendingMessagesOut = new ArrayList<Object>();
        for (int i = 0; i < maxPlayers; ++i)
            players[i].pendingMessagesOut = new ArrayList<Object>();

        // make sure game doesn't look idle, in case first player is a robot
        lastActionTime = System.currentTimeMillis();

        allOriginalPlayers = true;
    }

    /**
     * Do the things involved in starting a game at server:
     * Call {@link #initAtServer()},
     * shuffle the tiles and cards,
     * make a board by calling {@link SOCBoard#makeNewBoard(SOCGameOptionSet)},
     * set players' legal and potential piece locations,
     * choose first player.
     * gameState becomes {@link #START1A}.
     * Updates {@link #lastActionTime}.
     *<P>
     * <B>Note:</B> This method requires at least 1 seated player, or it will loop forever.
     *<P>
     * Called only at server, not client.  For a method called during game start
     * at server and clients, see {@link #updateAtBoardLayout()}.
     *<P>
     * Some scenarios require other methods to finish setting up the game;
     * call them in this order before any other board or game methods:
     *<UL>
     * <LI> This method {@code startGame()}
     * <LI> If appropriate, {@link soc.server.SOCBoardAtServer#startGame_scenarioSetup(SOCGame)}
     *</UL>
     */
    public void startGame()
    {
        initAtServer();

        startGame_setupDevCards();

        board.makeNewBoard(opts);
        if (hasSeaBoard)
        {
            /**
             * Set each player's legal and potential settlements and roads
             * to reflect the new board layout.
             *
             * Only necessary when hasSeaBoard (v3 board encoding):
             * In the v1 and v2 board encodings, the legal coordinates never change, so
             * SOCPlayer knows them already.
             */
            setPlayersLandHexCoordinates();
            HashSet<Integer> psList = ((SOCBoardLarge) board).getLegalSettlements();
            final HashSet<Integer>[] las = ((SOCBoardLarge) board).getLandAreasLegalNodes();
            for (int i = 0; i < maxPlayers; ++i)
                players[i].setPotentialAndLegalSettlements(psList, true, las);
        }
        updateAtBoardLayout();

        gameState = START1A;

        /**
         * choose who goes first
         */
        do
        {
            currentPlayerNumber = Math.abs(rand.nextInt() % maxPlayers);
        } while (isSeatVacant(currentPlayerNumber));

        setFirstPlayer(currentPlayerNumber);
    }

    /**
     * For {@link #startGame()}, fill and shuffle the development card deck.
     * {@link #devCardDeck} contents are based on game options and number of players.
     * @since 2.0.00
     */
    private final void startGame_setupDevCards()
    {
        /**
         * set up devCardDeck.  numDevCards is already set in constructor based on maxPlayers.
         */
        final boolean sc_piri_devcards = isGameOptionSet(SOCGameOptionSet.K_SC_PIRI);
        if (maxPlayers > 4)
        {
            // 6-player set
            devCardDeck = new int[NUM_DEVCARDS_6PLAYER];
        } else if (sc_piri_devcards && (getGameOptionIntValue("PL", 4, false) < 4)) {
            // _SC_PIRI with 2 or 3 players omits Victory Point cards
            devCardDeck = new int[NUM_DEVCARDS_STANDARD - NUM_DEVCARDS_VP];
            numDevCards = devCardDeck.length;
        } else {
            // 4-player set
            devCardDeck = new int[NUM_DEVCARDS_STANDARD];
        }

        int i;
        int j;

        // Standard set of knights
        for (i = 0; i < 14; i++)
        {
            devCardDeck[i] = SOCDevCardConstants.KNIGHT;
        }

        for (i = 14; i < 16; i++)
        {
            devCardDeck[i] = SOCDevCardConstants.ROADS;
        }

        for (i = 16; i < 18; i++)
        {
            devCardDeck[i] = SOCDevCardConstants.MONO;
        }

        for (i = 18; i < 20; i++)
        {
            devCardDeck[i] = SOCDevCardConstants.DISC;
        }

        // VP cards are set up after the 4-player non-VP cards.

        if (! sc_piri_devcards)
        {
            devCardDeck[20] = SOCDevCardConstants.CAP;
            devCardDeck[21] = SOCDevCardConstants.MARKET;
            devCardDeck[22] = SOCDevCardConstants.UNIV;
            devCardDeck[23] = SOCDevCardConstants.TEMPLE;
            devCardDeck[24] = SOCDevCardConstants.CHAPEL;
        } else {
            // _SC_PIRI: VP cards become Knight cards, or omit if < 4 players
            if (devCardDeck.length > 24)
                for (i = 20; i <= 24; ++i)
                    devCardDeck[i] = SOCDevCardConstants.KNIGHT;
        }

        if (maxPlayers > 4)
        {
            // 6-player extension

            for (i = 25; i < 31; i++)
            {
                devCardDeck[i] = SOCDevCardConstants.KNIGHT;
            }
            devCardDeck[31] = SOCDevCardConstants.ROADS;
            devCardDeck[32] = SOCDevCardConstants.MONO;
            devCardDeck[33] = SOCDevCardConstants.DISC;
        }

        /**
         * shuffle.
         */
        for (j = 0; j < 10; j++)
        {
            for (i = 1; i < devCardDeck.length; i++) // don't swap 0 with 0!
            {
                // Swap a random card below the ith card with the ith card
                int idx = Math.abs(rand.nextInt() % (devCardDeck.length - 1));
                int tmp = devCardDeck[idx];
                devCardDeck[idx] = devCardDeck[i];
                devCardDeck[i] = tmp;
            }
        }
    }

    /**
     * Sets who the first player is.
     * Based on <code>pn</code> and on vacant seats, also recalculates lastPlayer.
     *
     * @param pn  the seat number of the first player, or -1 if not set yet
     * @see #getFirstPlayer()
     */
    public void setFirstPlayer(final int pn)
    {
        firstPlayerNumber = pn;
        if (pn < 0)  // -1 == not set yet; use <0 to be defensive in while-loop
        {
            lastPlayerNumber = -1;
            return;
        }

        lastPlayerNumber = pn - 1;  // start before firstPlayerNumber, and we'll loop backwards
        if (lastPlayerNumber < 0)
            lastPlayerNumber = maxPlayers - 1;

        while (isSeatVacant (lastPlayerNumber))
        {
            --lastPlayerNumber;
            if (lastPlayerNumber < 0)
            {
                lastPlayerNumber = maxPlayers - 1;
            }
            if (lastPlayerNumber == firstPlayerNumber)
            {
                // Should not happen: All seats blank
                D.ebugPrintlnINFO("** setFirstPlayer: Should not happen: All seats blank");
                lastPlayerNumber = -1;
                break;
            }
        }
    }

    /**
     * Get the first player number, who went first during initial placement.
     * Not needed after initial placement.
     * @return the seat number of the first player
     */
    public int getFirstPlayer()
    {
        return firstPlayerNumber;
    }

    /**
     * Can this player end the current turn without any forced actions?
     *<P>
     * In some states, the current player can't end their turn yet
     * (such as needing to move the robber, or choose resources for a
     *  year-of-plenty card, or discard if a 7 is rolled).
     *<P>
     * v1.2.01 and newer allow player to end their turn during
     * {@link #PLACING_FREE_ROAD1} and {@link #PLACING_FREE_ROAD2}
     * if they rolled the dice before playing the Road Building card:
     * In some situations or scenarios, player may not want to or be able to
     * place both free roads. They also can call {@link #cancelBuildRoad(int)}
     * to continue their turn without placing the second free road.
     *
     * @param pn  player number of the player who wants to end the turn;
     *    returns false if out of range (-1, etc)
     * @return true if okay for this player to end the turn
     *    (They are current player; game state is {@link #PLAY1} or {@link #SPECIAL_BUILDING};
     *    or is {@link #PLACING_FREE_ROAD1} or {@link #PLACING_FREE_ROAD2} and
     *    {@link #getCurrentDice()} != 0).
     *
     * @see #endTurn()
     * @see #forceEndTurn()
     */
    public boolean canEndTurn(final int pn)
    {
        if (currentPlayerNumber != pn)
        {
            return false;
        }

        switch (gameState)
        {
        case PLAY1:
            // fall through
        case SPECIAL_BUILDING:
            return true;

        case PLACING_FREE_ROAD1:
            // fall through
        case PLACING_FREE_ROAD2:
            return (currentDice != 0);

        default:
            return false;
        }
    }

    /**
     * At server, end the turn for the current player, and check for winner.
     * If no winner yet, set up for the next player's turn (see below).
     * Check for gamestate >= {@link #OVER} after calling endTurn.
     *<P>
     * endTurn() is called <b>only at server</b> - client instead calls
     * {@link #setCurrentPlayerNumber(int)}, then client calls {@link #updateAtTurn()}.
     * endTurn() also calls {@link #updateAtTurn()}.
     *<P>
     * endTurn() is not called before the first dice roll.
     * endTurn() will call {@link #updateAtTurn()}.
     * In the 6-player game, calling endTurn() may begin or
     * continue the {@link #SPECIAL_BUILDING Special Building Phase}.
     * Does not clear any player's {@link SOCPlayer#hasAskedSpecialBuild()} flag.
     *<P>
     * The winner check is needed because a player can win only
     * during their own turn; if they reach winning points ({@link #vp_winner}
     * or more) during another player's turn, they must wait.
     * So, this method calls {@link #checkForWinner()} if the new current player has
     * {@link #vp_winner} or if the game's {@link #hasScenarioWinCondition} is true.
     *<P>
     * In 1.1.09 and later, player is allowed to Special Build at start of their
     * own turn, only if they haven't yet rolled or played a dev card.
     * To do so, call {@link #askSpecialBuild(int, boolean)} and then {@link #endTurn()}.
     *<P>
     * In 2.5.00 and later, if ending turn during {@link #PLACING_FREE_ROAD1}
     * or if {@link #doesCancelRoadBuildingReturnCard()},
     * this method calls {@link #cancelBuildRoad(int)} to return the dev card
     * to current player's inventory.
     *
     * @see #forceEndTurn()
     * @see #isForcingEndTurn()
     */
    public void endTurn()
    {
        if ((gameState == SOCGame.PLACING_FREE_ROAD1)
            || (playingRoadBuildingCardForLastRoad && (gameState == SOCGame.PLACING_FREE_ROAD2)))
            cancelBuildRoad(currentPlayerNumber);

        if (! advanceTurnToSpecialBuilding())
        {
            // "Normal" end-turn:

            gameState = ROLL_OR_CARD;
            if (! advanceTurn())
                return;
        }

        updateAtTurn();

        if ((players[currentPlayerNumber].getTotalVP() >= vp_winner) || hasScenarioWinCondition)
            checkForWinner();  // Will do nothing during Special Building Phase
    }

    /**
     * Update any miscellaneous game info as needed after the board layout is set, before the game starts.
     *<P>
     * Called at server and clients during game startup, immediately after all board-layout methods
     * and related game methods like {@link #setPlayersLandHexCoordinates()} have been called.
     *<P>
     * When called at the client, the board layout is set, but neither the gameState nor the players'
     * potential settlements have been sent from the server yet.  Even if the client joined after game start,
     * this method will still be called at that client.
     *<P>
     * Currently used only for Special Item placement by the {@link SOCGameOptionSet#K_SC_WOND _SC_WOND} scenario,
     * where (1 + {@link #maxPlayers}) wonders are available for the players to choose from, held in game
     * Special Item indexes 1 - n.  Because of this limited and non-dynamic use, it's easier to set them up in code
     * here than to create, send, and parse messages with all details of the game's Special Items.  This method
     * sets up the Special Items as they are during game start.  If the game has started before a client joins,
     * other messages sent during the join will then update Special Item info in case they've changed since
     * game start.
     *
     * @since 2.0.00
     */
    public void updateAtBoardLayout()
    {
        if (! isGameOptionSet(SOCGameOptionSet.K_SC_WOND))
            return;

        final int numWonders = 1 + maxPlayers;
        for (int i = 1; i <= numWonders; ++i)
            setSpecialItem(SOCGameOptionSet.K_SC_WOND, i, SOCSpecialItem.makeKnownItem(SOCGameOptionSet.K_SC_WOND, i));
    }

    /**
     * Update game state as needed after initial placement before the first turn of normal play:
     *<UL>
     *<LI> Calls each player's {@link SOCPlayer#clearPotentialSettlements()}
     *
     *<LI> At server, if {@link SOCBoardAtServer#getBonusExcludeLandArea()} != 0,
     *     use that to set each player's second starting land area as a client compatibility workaround
     *     for bonus calculations: Calls {@link SOCPlayer#setStartingLandAreasEncoded(int)}.
     *
     *<LI> If {@link #hasSeaBoard}, check board for Added Layout Part {@code "AL"} for node lists that
     *     become legal locations for settlements after initial placement, and make them legal now.
     *     (This Added Layout Part is rarely used, currently is in scenario {@link SOCScenario#K_SC_WOND SC_WOND}.)
     *     If any node has an adjacent settlement or city, that node won't be made legal.
     *    <P>
     *     Calls {@link SOCBoardLarge#addLegalNodes(int[], int)} for each referenced node list.
     *     Does not adjust players' potential settlement locations, because at the start of a game,
     *     players won't have roads to any node 2 away from their settlements, so they will have no
     *     new potential settlements yet.  Does call each player's
     *     {@link SOCPlayer#addLegalSettlement(int, boolean) pl.addLegalSettlement(coord, true)}.
     *
     *<LI> At server, calls {@link #updateAtTurn()}. Client will instead receive a {@link soc.message.SOCTurn}
     *     message which will call that (always in v2.5+, sometimes in v2.0+).
     *</UL>
     *<P>
     * Called after gameState is set to {@link #ROLL_OR_CARD}.
     * Called at server by {@link #advanceTurnStateAfterPutPiece()}, and at client by first
     * {@link #setGameState(int) setGameState}({@link #ROLL_OR_CARD}).
     *<P>
     * Before v2.0.00, this was in various places.  The players' potential settlements were cleared by
     * {@code putPiece} after placing their last initial road.  If a bot's initial placement turn was
     * forced to end, their potentials might not have been cleared.
     *
     * @since 2.0.00
     */
    private void updateAtGameFirstTurn()
    {
        for (int pn = 0; pn < maxPlayers; ++pn)
            players[pn].clearPotentialSettlements();

        if (board instanceof SOCBoardAtServer)
        {
            final int bxLAEnc = ((SOCBoardAtServer) board).getBonusExcludeLandArea() << 8;
                // Shift left for "encoded" form of players' 2nd starting land area.
                // If bxLAEnc != 0, board must have 1 starting land area, so can assume players' 2nd starting LA is 0.
            if (bxLAEnc != 0)
                for (final SOCPlayer pl: players)
                    pl.setStartingLandAreasEncoded(pl.getStartingLandAreasEncoded() | bxLAEnc);
        }

        final int[] partAL =
            (board instanceof SOCBoardLarge) ? ((SOCBoardLarge) board).getAddedLayoutPart("AL") : null;
        if (partAL != null)
        {
            // Look through board's Added Layout Part "AL" for node list numbers:
            // Part "AL" was already strictly parsed in SOCBoardLarge.initLegalRoadsFromLandNodes(),
            // so there shouldn't be any problems in it. Ignore problems here instead of throwing exceptions.
            // If you update the "AL" parser here, update the similar one there too.

            boolean emptiedAnyNodeSet = false;
            for (int i = 0; i < partAL.length; ++i)
            {
                final int elem = partAL[i];
                if (elem <= 0)
                    continue;  // ignore unless it's a node list number

                ++i;
                int lan = partAL[i];  // land area number follows elem
                final boolean doEmptyNodeSet;
                if (lan < 0)
                {
                    doEmptyNodeSet = true;
                    lan = -lan;
                } else {
                    doEmptyNodeSet = false;
                }

                final String nodeListKey = "N" + elem;
                final int[] nodeList = ((SOCBoardLarge) board).getAddedLayoutPart(nodeListKey);
                if (nodeList == null)
                    continue;

                ((SOCBoardLarge) board).addLegalNodes(nodeList, lan);

                for (int j = 0; j < nodeList.length; ++j)
                    for (int pn = maxPlayers - 1; pn >= 0; --pn)
                        players[pn].addLegalSettlement(nodeList[j], true);

                if (doEmptyNodeSet)
                {
                    emptiedAnyNodeSet = true;
                    ((SOCBoardLarge) board).setAddedLayoutPart(nodeListKey, EMPTY_INT_ARRAY);
                }
            }

            if (emptiedAnyNodeSet && (gameEventListener != null))
                gameEventListener.gameEvent
                    (this, SOCGameEvent.SGE_STARTPLAY_BOARD_SPECIAL_NODES_EMPTIED, null);
        }

        // Begin play.
        // Player number is unchanged; "virtual" endTurn here if at server.
        // This code formerly in advanceTurnStateAfterPutPiece() when last initial piece has been placed.

        if (isAtServer)
            updateAtTurn();
    }

    /**
     * Update game state as needed when a player begins their turn (before dice are rolled).
     *<P>
     * May be called during initial placement.
     * (Game methods don't call during this time, but the server sends each client
     *  a message to call <tt>updateAtTurn</tt> whenever the current player changes.)
     * Is called at the end of initial placement, before the first player's first roll.
     * On the 6-player board, is called at the start of
     * each player's {@link #SPECIAL_BUILDING Special Building Phase}.
     *<UL>
     *<LI> Set first player and last player, if they're currently -1
     *<LI> Set current dice to 0
     *<LI> Call each player's {@link SOCPlayer#updateAtTurn()}
     *<LI> Call the new current player's {@link SOCPlayer#updateAtOurTurn()},
     *     to mark their new dev cards as old and clear other flags
     *<LI> Clear any "x happened this turn" flags/lists
     *<LI> Clear any votes to reset the board
     *<LI> If game state is {@link #ROLL_OR_CARD}, increment {@link #getTurnCount()}
     *     (and {@link #getRoundCount()} if necessary).
     *     These include the current turn; they both are 1 during the first player's first turn.
     *</UL>
     * Called by server and client.
     * At client, call this after {@link #setGameState(int)} and {@link #setCurrentPlayerNumber(int)}.
     * At server, this is called from within {@link #endTurn()}.
     * @since 1.1.07
     */
    public void updateAtTurn()
    {
        if (firstPlayerNumber == -1)
            setFirstPlayer(currentPlayerNumber);  // also sets lastPlayerNumber

        currentDice = 0;
        for (int pl = 0; pl < maxPlayers; ++pl)
            players[pl].updateAtTurn();
        SOCPlayer currPlayer = players[currentPlayerNumber];
        currPlayer.updateAtOurTurn();
        resetVoteClear();
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
        if (hasSeaBoard)
        {
            movedShipThisTurn = false;
            shipsPlacedThisTurn.clear();
        }
        placingItem = null;

        if (gameState == ROLL_OR_CARD)
        {
            ++turnCount;
            if (currentPlayerNumber == firstPlayerNumber)
                ++roundCount;

            if (askedSpecialBuildPhase)
            {
                // clear did-SBP flags for all players
                askedSpecialBuildPhase = false;
                for (int pl = 0; pl < maxPlayers; ++pl)
                    players[pl].setSpecialBuilt(false);
            }
        } else if (gameState == SPECIAL_BUILDING)
        {
            // Set player's flag: active in this Special Building Phase
            currPlayer.setSpecialBuilt(true);
        }
    }

    /**
     * In an active game, force current turn to be able to be ended.
     * This method updates {@link #getGameState()} and takes other actions,
     * but does not end the turn.
     *<P>
     * May be used if player loses connection, or robot does not respond.
     * Takes whatever action needed to force current player to end their turn,
     * and if possible, sets state to {@link #PLAY1}, but does not call {@link #endTurn()}.
     * If player was placing for {@link #SPECIAL_BUILDING}, will cancel that
     * placement and set state to {@link #SPECIAL_BUILDING}.
     *<P>
     * Called by controller of game (server).  The results are then reported to
     * the other players as if the player had manually taken actions to
     * end their turn.  (Resources are shown as returned to player's hand
     * from Cancel Build Road, etc.)
     *<P>
     * Since only the server calls {@link #endTurn()}, this method does not do so.
     * This method also does not check if a board-reset vote is in progress,
     * because endTurn will unconditionally cancel such a vote.
     * Does not clear any player's {@link SOCPlayer#hasAskedSpecialBuild()} flag;
     * do that at the server, and report it out to other players.
     *<P>
     * After calling forceEndTurn, usually the gameState will be {@link #PLAY1},
     * and the caller should call {@link #endTurn()}.  The {@link #isForcingEndTurn()}
     * flag is also set.  The return value in this case is
     * {@link SOCForceEndTurnResult#FORCE_ENDTURN_NONE FORCE_ENDTURN_NONE}.
     * The state in this case could also be {@link #SPECIAL_BUILDING}.
     *<P>
     * Exceptions (where caller should not call endTurn) are these return types:
     * <UL>
     * <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD_WAIT}
     *       - Have forced current player to discard or gain resources randomly,
     *         must now wait for other players to pick their discards or gains.
     *         gameState is {@link #WAITING_FOR_DISCARDS}, current player
     *         as yet unchanged.
     * <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADV}
     *       - During initial placement, have skipped placement of
     *         a player's first or third settlement or road.  gameState is
     *         {@link #START1A} or {@link #START3A}, current player has changed.
     *         Game's first or last player may have changed.
     * <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADVBACK}
     *       - During initial placement, have skipped placement of
     *         a player's second settlement or road. (Or, final player's
     *         first _and_ second settlement or road.)
     *         gameState is {@link #START2A}, current player has changed.
     *         Game's first or last player may have changed.
     *       <P>
     *       Note that for the very last initial road placed, during normal
     *       gameplay, that player continues by rolling the first turn's dice.
     *       To force skipping such a placement, the caller should call endTurn()
     *       to change the current player.  This is indicated by
     *       {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_TURN}.
     * </UL>
     *<P>
     * See also <tt>SOCGameHandler.forceEndGameTurn, SOCGameHandler.endGameTurnOrForce</tt>.
     *
     * @return Type of action performed, one of these values:
     *     <UL>
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_NONE}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADV}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADVBACK}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_TURN}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_RET_UNPLACE}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_UNPLACE_ROBBER}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD_WAIT}
     *     <LI> {@link SOCForceEndTurnResult#FORCE_ENDTURN_LOST_CHOICE}
     *     </UL>
     * @throws IllegalStateException if game is not active
     *     (gamestate < {@link #START1A} or >= {@link #OVER})
     * @see #canEndTurn(int)
     * @see #endTurn()
     * @since 1.1.00
     */
    public SOCForceEndTurnResult forceEndTurn()
        throws IllegalStateException
    {
        if ((gameState < START1A) || (gameState >= OVER))
            throw new IllegalStateException("Game not active: state " + gameState);

        forcingEndTurn = true;
        final SOCPlayer currPlayer = players[currentPlayerNumber];
        SOCInventoryItem itemCard = null;  // card/inventory item being returned to player, if any

        if (gameState == WAITING_FOR_ROBBER_OR_PIRATE)
            chooseMovePirate(false);  // gameState becomes PLACING_ROBBER, which is in the switch

        switch (gameState)
        {
        case START1A:
        case START1B:
        case START3A:
        case START3B:
            return forceEndTurnStartState(true);
                // FORCE_ENDTURN_SKIP_START_ADV,
                // FORCE_ENDTURN_SKIP_START_ADVBACK,
                // or FORCE_ENDTURN_SKIP_START_TURN

        case START2A:
        case START2B:
            return forceEndTurnStartState(false);
                // same types as above

        case STARTS_WAITING_FOR_PICK_GOLD_RESOURCE:
            return forceEndTurnStartState((oldGameState != START2A) && (oldGameState != START2B));
                // sets gameState, picks randomly;
                // FORCE_ENDTURN_SKIP_START_ADV,
                // FORCE_ENDTURN_SKIP_START_ADVBACK,
                // or FORCE_ENDTURN_SKIP_START_TURN

        case ROLL_OR_CARD:
            gameState = PLAY1;
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_NONE);

        case PLAY1:
            // already can end it; fall through to SPECIAL_BUILDING

        case SPECIAL_BUILDING:
            // already can end it
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_NONE);

        case PLACING_ROAD:
            {
                final boolean rets = cancelBuildRoad(currentPlayerNumber);
                return new SOCForceEndTurnResult
                    (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE, rets ? SOCRoad.COST : null);
            }

        case PLACING_SETTLEMENT:
            cancelBuildSettlement(currentPlayerNumber);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE, SOCSettlement.COST);

        case PLACING_CITY:
            cancelBuildCity(currentPlayerNumber);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE, SOCCity.COST);

        case PLACING_SHIP:
            {
                final boolean rets = cancelBuildShip(currentPlayerNumber);
                return new SOCForceEndTurnResult
                    (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE, rets ? SOCShip.COST : null);
            }

        case PLACING_INV_ITEM:
            itemCard = cancelPlaceInventoryItem(true);
            if (itemCard != null)
                return new SOCForceEndTurnResult
                    (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE, itemCard);
            else
                return new SOCForceEndTurnResult
                    (SOCForceEndTurnResult.FORCE_ENDTURN_NONE);

        case PLACING_ROBBER:
        case PLACING_PIRATE:
            {
                boolean isFromDevCard = placingRobberForKnightCard;
                gameState = PLAY1;
                if (isFromDevCard)
                {
                    placingRobberForKnightCard = false;
                    itemCard = new SOCDevCard(SOCDevCardConstants.KNIGHT, false);
                    currPlayer.getInventory().addItem(itemCard);

                    final int newNumKnights = currPlayer.getNumKnights() - 1;
                    if (newNumKnights >= 0)
                        currPlayer.setNumKnights(newNumKnights);

                    if (currentPlayerNumber == playerWithLargestArmy)
                    {
                        if (newNumKnights < 3)
                            playerWithLargestArmy = -1;

                        updateLargestArmy();
                            // TODO: Not perfect; if there had been a tie before the Knight was played,
                            // the current player would've taken Largest Army by playing it,
                            // now it's returned so there's a tie again, but they keep Largest Army.
                    }
                }
                return new SOCForceEndTurnResult
                    (SOCForceEndTurnResult.FORCE_ENDTURN_UNPLACE_ROBBER, itemCard);
            }

        case PLACING_FREE_ROAD1:
        case PLACING_FREE_ROAD2:
            {
                final boolean retDevCard =
                    (gameState == PLACING_FREE_ROAD1)
                    || (playingRoadBuildingCardForLastRoad && (gameState == PLACING_FREE_ROAD2));
                gameState = PLAY1;
                if (retDevCard)
                {
                    itemCard = new SOCDevCard(SOCDevCardConstants.ROADS, false);
                    currPlayer.getInventory().addItem(itemCard);
                    return new SOCForceEndTurnResult
                        (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE, itemCard);
                } else {
                    return new SOCForceEndTurnResult
                        (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE);
                }
            }

        case WAITING_FOR_DISCARDS:
            return forceEndTurnChkDiscardOrGain(currentPlayerNumber, true);  // sets gameState, discards randomly

        case WAITING_FOR_ROB_CHOOSE_PLAYER:
            gameState = PLAY1;
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE);

        case WAITING_FOR_DISCOVERY:
            gameState = PLAY1;
            itemCard = new SOCDevCard(SOCDevCardConstants.DISC, false);
            currPlayer.getInventory().addItem(itemCard);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE, itemCard);

        case WAITING_FOR_MONOPOLY:
            gameState = PLAY1;
            itemCard = new SOCDevCard(SOCDevCardConstants.MONO, false);
            currPlayer.getInventory().addItem(itemCard);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE, itemCard);

        case WAITING_FOR_ROB_CLOTH_OR_RESOURCE:
            gameState = PLAY1;
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE);

        case WAITING_FOR_PICK_GOLD_RESOURCE:
            return forceEndTurnChkDiscardOrGain(currentPlayerNumber, false);  // sets gameState, picks randomly

        default:
            throw new IllegalStateException("Internal error in force, un-handled gamestate: "
                    + gameState);
        }

        // Always returns within switch
    }

    /**
     * Special forceEndTurn() treatment for start-game states.
     * Changes gameState and usually {@link #currentPlayerNumber}.
     * Handles {@link #START1A} - {@link #START3B} and {@link #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}.
     * See {@link #forceEndTurn()} for description.
     *<P>
     * Check for gamestate >= {@link #OVER} after calling this method,
     * in case all players have left the game.
     *
     * @param advTurnForward Should the next player be normal (placing first settlement),
     *                       or backwards (placing second settlement)?
     * @return A forceEndTurn result of type
     *         {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADV},
     *         {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_ADVBACK},
     *         or {@link SOCForceEndTurnResult#FORCE_ENDTURN_SKIP_START_TURN}.
     * @since 1.1.00
     */
    private SOCForceEndTurnResult forceEndTurnStartState(final boolean advTurnForward)
    {
        final int cpn = currentPlayerNumber;
        int cancelResType;  // Turn result type
        final SOCResourceSet goldPicks;  // null unless STARTS_WAITING_FOR_PICK_GOLD_RESOURCE
        boolean updateFirstPlayer, updateLastPlayer;  // are we forcing the very first player's (or last player's) turn?
        updateFirstPlayer = (cpn == firstPlayerNumber);
        updateLastPlayer = (cpn == lastPlayerNumber);

        if (gameState == STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
        {
            goldPicks = new SOCResourceSet();

            // From this state, pickGoldHexResources will call advanceTurnStateAfterPutPiece.
            // It knows about oldGameState, but doesn't know we're force-ending the turn.
            // Before calling, make sure oldGameState is one that will advance the turn:
            if (advTurnForward)
            {
                if (oldGameState >= START3A)
                    oldGameState = START3B;  // third init placement
                else
                    oldGameState = START1B;  // first init placement
            } else {
                oldGameState = START2B;
            }

            // Choose random resource(s) and pick:
            discardOrGainPickRandom
                (players[cpn].getResources(), players[cpn].getNeedToPickGoldHexResources(), false, goldPicks, rand);
            pickGoldHexResources(cpn, goldPicks);  // sets gameState based on oldGameState + advance
            if (gameState == ROLL_OR_CARD)
                gameState = PLAY1;

            if (gameState == PLAY1)
                cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_TURN;
            else if (advTurnForward)
                cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADV;
            else
                cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADVBACK;

        } else {
            // Normal start states (not STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)

            goldPicks = null;

            /**
             * Set the state we're advancing "from";
             * this is needed because {@link #START1A}, {@link #START2A}, {@link #START3A}
             * don't change player number after placing their piece.
             */
            if (advTurnForward)
            {
                if (gameState >= START3A)
                    gameState = START3B;  // third init placement
                else
                    gameState = START1B;  // first init placement
            } else {
                gameState = START2B;
            }

            final boolean stillActive = advanceTurnStateAfterPutPiece();  // Changes state, may change current player

            if ((cpn == currentPlayerNumber) && stillActive)
            {
                // Player didn't change.  This happens when the last player places
                // their first or second road.  But we're trying to end this player's
                // turn, and give another player a chance.
                if (advTurnForward)
                {
                    if (gameState == START1B)
                    {
                        // Was first placement; allow other players to begin second placement.
                        // This player won't get a second placement either.
                        gameState = START2A;
                        advanceTurnBackwards();
                        cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADVBACK;
                    } else {
                        // Was third placement.  Begin normal gameplay.
                        // Set resType to tell caller to call endTurn().
                        gameState = PLAY1;
                        cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_TURN;
                    }
                } else {
                    // Was second placement; begin normal gameplay?
                    if (! isGameOptionSet(SOCGameOptionSet.K_SC_3IP))
                    {
                        // Set resType to tell caller to call endTurn().
                        gameState = PLAY1;
                        cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_TURN;
                    } else {
                        // Begin third settlement.  This player won't get one.
                        gameState = START3A;
                        advanceTurn();
                        cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADV;
                    }
                }
            } else {
                // OK, player has changed.  This means advanceTurnStateAfterPutPiece()
                // has also cleared the forcingEndTurn flag.
                if (advTurnForward)
                    cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADV;
                else
                    cancelResType = SOCForceEndTurnResult.FORCE_ENDTURN_SKIP_START_ADVBACK;
            }
        }

        // update these so the game knows when to stop initial placement
        if (updateFirstPlayer)
            firstPlayerNumber = currentPlayerNumber;
        if (updateLastPlayer)
            lastPlayerNumber = currentPlayerNumber;

        return new SOCForceEndTurnResult(cancelResType, updateFirstPlayer, updateLastPlayer, goldPicks);
    }

    /**
     * Randomly discards from this player's hand or pick random gains,
     * and updates {@link #gameState} so play can continue.
     * Does not end turn or change {@link #currentPlayerNumber}.
     * Assumes {@link #isForcingEndTurn()} flag is already set.
     *<P>
     * If {@code isDiscard}, picks random resources from the player's hand
     * and calls {@link #discard(int, ResourceSet)},
     * Otherwise gains random resources by calling {@link #pickGoldHexResources(int, SOCResourceSet)}.
     * Then, looks at other players' hand size. If no one else must discard or pick,
     * ready to end turn, sets state {@link #PLAY1}.
     * Otherwise must wait for them; sets game state to wait
     * ({@link #WAITING_FOR_DISCARDS} or {@link #WAITING_FOR_PICK_GOLD_RESOURCE}).
     *<P>
     * Not called for {@link #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE},
     * which has different result types and doesn't need to check other players.
     *<P>
     * In version 1.x.xx this method was {@code forceEndTurnChkDiscards}.
     *
     * @param pn Player number to force to randomly discard or gain
     * @param isDiscard  True to discard resources, false to gain
     * @return The force result, including any discarded or gained resources.
     *         Type will be {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD}
     *         or {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD_WAIT}.
     * @see #playerDiscardOrGainRandom(int, boolean)
     * @since 1.1.00
     */
    private SOCForceEndTurnResult forceEndTurnChkDiscardOrGain(final int pn, final boolean isDiscard)
    {
        // select random cards, and discard or gain
        SOCResourceSet picks = new SOCResourceSet();
        SOCResourceSet hand = players[pn].getResources();
        if (isDiscard)
        {
            discardOrGainPickRandom(hand, players[pn].getCountToDiscard(), true, picks, rand);
            discard(pn, picks);  // Checks for other discarders, sets gameState
        } else {
            discardOrGainPickRandom(hand, players[pn].getNeedToPickGoldHexResources(), false, picks, rand);
            pickGoldHexResources(pn, picks);  // Checks for other players, sets gameState
        }

        if ((gameState == WAITING_FOR_DISCARDS) || (gameState == WAITING_FOR_PICK_GOLD_RESOURCE))
        {
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_DISCARD_WAIT, picks, isDiscard);
        } else {
            // gameState == PLAY1 - was set in discard()
            // or is START2B/START3B from pickGoldHexResources() if STARTS_WAITING_FOR_PICK_GOLD_RESOURCE
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_DISCARD, picks, isDiscard);
        }
    }

    /**
     * Choose discards or picks at random; does not actually discard or gain anything or change game state.
     * For discards, randomly choose from contents of <tt>fromHand</tt>.
     * For gains, randomly choose resource types least plentiful in <tt>fromHand</tt>.
     *<P>
     * Before v2.0.00 this method was {@code discardPickRandom}.
     *
     * @param fromHand     Discard from this set, or gain to add to this set
     * @param numToPick    This many must be discarded or gained
     * @param isDiscard    True to discard resources, false to gain
     * @param picks        Add the picked resources to this set (typically new and empty when called)
     * @param rand         Source of random
     * @throws IllegalArgumentException if <tt>isDiscard</tt> and
     *     <tt>numDiscards</tt> &gt; {@link SOCResourceSet#getKnownTotal() fromHand.getKnownTotal()}
     * @since 1.1.00
     */
    public static void discardOrGainPickRandom
        (SOCResourceSet fromHand, int numToPick, final boolean isDiscard, SOCResourceSet picks, Random rand)
        throws IllegalArgumentException
    {
        // resources, to be shuffled and chosen from;
        // discards from fromHand, or possible new picks.
        ArrayList<Integer> tempHand = new ArrayList<>();

        if (isDiscard)
        {
            // First, check the total
            final int totalHand = fromHand.getKnownTotal();
            if (numToPick > totalHand)
                throw new IllegalArgumentException("Has " + totalHand + ", discard " + numToPick);

            // Add everything in fromHand.
            // System.err.println("resources="+ourPlayerData.getResources());
            for (int rsrcType = SOCResourceConstants.CLAY;
                    rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
            {
                for (int i = fromHand.getAmount(rsrcType);
                        i != 0; i--)
                {
                    tempHand.add(Integer.valueOf(rsrcType));
                    // System.err.println("rsrcType="+rsrcType);
                }
            }
        } else {

            // First, determine the res type(s) with lowest amount in hand
            int lowestNum = fromHand.getAmount(SOCResourceConstants.CLAY);
            for (int rsrcType = SOCResourceConstants.ORE;
                     rsrcType <= SOCResourceConstants.WOOD; ++rsrcType)
            {
                final int num = fromHand.getAmount(rsrcType);
                if (num < lowestNum)
                    lowestNum = num;
            }

            // Next, add resources with that amount, and then increase
            // lowestNum until we've found at least numToPick resources.
            int toAdd = numToPick;
            ArrayList<Integer> alreadyPicked = new ArrayList<>();
            do
            {
                for (int rsrcType = SOCResourceConstants.CLAY;
                         rsrcType <= SOCResourceConstants.WOOD; ++rsrcType)
                {
                    final int num = fromHand.getAmount(rsrcType);
                    if (num == lowestNum)
                    {
                        tempHand.add(Integer.valueOf(rsrcType));
                        --toAdd;  // might go below 0, that's okay: we'll shuffle.
                    }
                    else if (num < lowestNum)
                    {
                        // Already added in previous iterations.
                        // Add more of this type only if we need more.
                        alreadyPicked.add(Integer.valueOf(rsrcType));
                    }
                }

                if (toAdd > 0)
                {
                    ++lowestNum;

                    if (! alreadyPicked.isEmpty())
                    {
                        toAdd -= alreadyPicked.size();
                        tempHand.addAll(alreadyPicked);
                        alreadyPicked.clear();
                    }
                }
            } while (toAdd > 0);
        }

        /**
         * randomly pick the resources
         * (same as 'pick cards' when shuffling development cards)
         * and move from tempHand to picks.
         */
        for (; numToPick > 0; numToPick--)
        {
            // System.err.println("numDiscards="+numDiscards+"|hand.size="+hand.size());
            int idx = Math.abs(rand.nextInt() % tempHand.size());

            // System.err.println("idx="+idx);
            picks.add(1, tempHand.get(idx).intValue());
            tempHand.remove(idx);
        }
    }

    /**
     * Force this non-current player to discard or gain resources randomly.
     * Used at server when a player must discard or pick free resources
     * and player loses connection while the game is waiting for them,
     * or a bot is unresponsive. Calls {@link #discard(int, ResourceSet)}
     * or {@link #pickGoldHexResources(int, SOCResourceSet)}.
     *<P>
     * On return, gameState will be:
     *<UL>
     * <LI> {@link #WAITING_FOR_DISCARDS} if other players still must discard
     * <LI> {@link #WAITING_FOR_PICK_GOLD_RESOURCE} if other players still must pick their resources
     * <LI> {@link #PLAY1} if everyone has picked (gained) resources
     * <LI> {@link #PLAY1} if everyone has discarded, and {@link #isForcingEndTurn()} is set
     * <LI> {@link #PLACING_ROBBER} or {@link #WAITING_FOR_ROBBER_OR_PIRATE},
     *      if everyone has discarded and {@link #isForcingEndTurn()} is not set
     *</UL>
     * Before v2.0.00 this method was {@code playerDiscardRandom}.
     *
     * @param pn Player number to discard; player must must need to discard,
     *           must not be current player (use {@link #forceEndTurn()} for that)
     * @param isDiscard  True to discard resources, false to gain (pick from gold hex)
     * @return   Set of resource cards which were discarded or gained
     * @throws IllegalStateException If the gameState isn't {@link #WAITING_FOR_DISCARDS}
     *                               or {@link #WAITING_FOR_PICK_GOLD_RESOURCE},
     *                               or if pn's {@link SOCPlayer#getNeedToDiscard()} is false
     *                                  and their {@link SOCPlayer#getNeedToPickGoldHexResources()} == 0,
     *                               or if pn == currentPlayer.
     * @since 1.1.00
     */
    public SOCResourceSet playerDiscardOrGainRandom(final int pn, final boolean isDiscard)
        throws IllegalStateException
    {
        if (pn == currentPlayerNumber)
            throw new IllegalStateException("Cannot call for current player, use forceEndTurn instead");
        if ((gameState != WAITING_FOR_DISCARDS) && (gameState != WAITING_FOR_PICK_GOLD_RESOURCE))
            throw new IllegalStateException("gameState not WAITING_FOR_DISCARDS: " + gameState);
        if ((players[pn].getNeedToPickGoldHexResources() == 0) && ! (players[pn].getNeedToDiscard()))
            throw new IllegalStateException("Player " + pn + " does not need to discard or pick");

        // Since doesn't change current player number, this is safe to call
        SOCForceEndTurnResult rs = forceEndTurnChkDiscardOrGain(pn, isDiscard);
        return rs.getResourcesGainedLost();
    }

    /**
     * @return true if it's ok for this player to roll the dice
     *
     * @param pn  player number of the player who wants to roll
     */
    public boolean canRollDice(int pn)
    {
        if (currentPlayerNumber != pn)
        {
            return false;
        }
        else if (gameState != ROLL_OR_CARD)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    /**
     * roll the dice.  Distribute resources, or (for 7) set gamestate to
     * move robber or to wait for players to discard.
     * {@link #getGameState()} will usually become {@link #PLAY1}.
     * If the board contains gold hexes, it may become {@link #WAITING_FOR_PICK_GOLD_RESOURCE}.
     * For 7, gameState becomes either {@link #WAITING_FOR_DISCARDS},
     * {@link #WAITING_FOR_ROBBER_OR_PIRATE}, or {@link #PLACING_ROBBER}.
     *<P>
     * For dice roll total, see returned {@link RollResult} or call {@link #getCurrentDice()} afterwards.
     * You can call each player's {@link SOCPlayer#getRolledResources()} for their resources gained, if any,
     * not including gold or scenario-specific items from {@link RollResult}.
     *<P>
     * Checks game option N7: Roll no 7s during first # rounds
     * and N7C: Roll no 7s until a city is built.
     *<P>
     * For scenario option {@link SOCGameOptionSet#K_SC_CLVI}, calls
     * {@link SOCBoardAtServer#distributeClothFromRoll(SOCGame, RollResult, int)}.
     * Cloth are worth VP, so check for game state {@link #OVER} if results include {@link RollResult#cloth}.
     *<P>
     * For scenario option {@link SOCGameOptionSet#K_SC_PIRI}, calls {@link SOCBoardLarge#movePirateHexAlongPath(int)}
     * and then {@link #movePirate(int, int, int)}:
     * Check {@link RollResult#sc_piri_fleetAttackVictim} and {@link RollResult#sc_piri_fleetAttackRsrcs}.
     * Note that if player's warships are stronger than the pirate fleet, {@link SOCMoveRobberResult#sc_piri_loot}
     * will contain {@link SOCResourceConstants#GOLD_LOCAL}, that player's {@link SOCPlayer#setNeedToPickGoldHexResources(int)}
     * will be set to include the free pick, and game state becomes {@link #WAITING_FOR_PICK_GOLD_RESOURCE}.
     * If a 7 was rolled, the free pick happens before any discards.
     *<P>
     * Called at server only.
     *
     * @return The roll results: Dice numbers, and any scenario-specific results
     *         such as {@link RollResult#cloth}.  The game reuses the same instance
     *         each turn, so its field contents will change when <tt>rollDice()</tt>
     *         is called again.
     * @see #getResourcesGainedFromRoll(SOCPlayer, int)
     */
    public RollResult rollDice()
    {
        // N7C: Roll no 7s until a city is built.
        // N7: Roll no 7s during first # rounds.
        //     Use > not >= because roundCount includes current round
        final boolean okToRoll7
            = ((isGameOptionSet("N7C")) ? hasBuiltCity : true)
              && (( ! isGameOptionSet("N7")) || (roundCount > getGameOptionIntValue("N7")));

        int die1, die2;
        do
        {
//            if (rand.nextBoolean())  // JM TEMP - try trigger bot discard-no-move-robber bug
//            {
//                die1 = 0; die2 = 7;
//            } else {
            die1 = Math.abs(rand.nextInt() % 6) + 1;
            die2 = Math.abs(rand.nextInt() % 6) + 1;
//            }

            currentDice = die1 + die2;
        } while ((currentDice == 7) && ! okToRoll7);

        currentRoll.update(die1, die2);  // also clears currentRoll.cloth (SC_CLVI)

        boolean sc_piri_plGainsGold = false;  // Has a player won against pirate fleet attack? (SC_PIRI)
        if (isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
        {
            /**
             * Move the pirate fleet along their path.
             * Copy pirate fleet attack results to currentRoll.
             * If the pirate fleet is already defeated, do nothing.
             */
            final int numSteps = (die1 < die2) ? die1 : die2;
            final int newPirateHex = ((SOCBoardLarge) board).movePirateHexAlongPath(numSteps);
            oldGameState = gameState;
            if (newPirateHex != 0)
                movePirate(currentPlayerNumber, newPirateHex, numSteps);
            else
                robberResult.victims = null;

            final List<SOCPlayer> victims = robberResult.victims;
            if ((victims != null) && (victims.size() == 1))
            {
                currentRoll.sc_piri_fleetAttackVictim = victims.get(0);

                currentRoll.sc_piri_fleetAttackRsrcs = robberResult.sc_piri_loot;
                if (currentRoll.sc_piri_fleetAttackRsrcs.contains(SOCResourceConstants.GOLD_LOCAL))
                {
                    final SOCPlayer plGold = currentRoll.sc_piri_fleetAttackVictim;  // won't be null
                    plGold.setNeedToPickGoldHexResources(1 + plGold.getNeedToPickGoldHexResources());

                    if (currentDice == 7)
                    {
                        hasRolledSeven = true;

                        // Need to set this state only on 7, to pick _before_ discards.  On any other
                        // dice roll, the free pick here will be combined with the usual roll-result gold picks.
                        oldGameState = ROLL_OR_CARD;
                        gameState = WAITING_FOR_PICK_GOLD_RESOURCE;

                        return currentRoll;  // <--- Early return: Wait to pick, then come back & discard ---

                    } else {
                        sc_piri_plGainsGold = true;
                    }
                }
            } else {
                currentRoll.sc_piri_fleetAttackVictim = null;
                currentRoll.sc_piri_fleetAttackRsrcs = null;
            }
        }

        /**
         * handle the seven case
         */
        if (currentDice == 7)
        {
            rollDice_update7gameState();
        }
        else
        {
            boolean anyGoldHex = false;

            /**
             * distribute resources
             */
            for (int i = 0; i < maxPlayers; i++)
            {
                if (! isSeatVacant(i))
                {
                    SOCPlayer pl = players[i];
                    pl.addRolledResources(getResourcesGainedFromRoll(pl, currentDice));
                    if (hasSeaBoard && pl.getNeedToPickGoldHexResources() > 0)
                        anyGoldHex = true;
                }
            }

            if (sc_piri_plGainsGold)
            {
                anyGoldHex = true;
                // this 1 gold was already added to that player's getNeedToPickGoldHexResources
            }

            /**
             * distribute cloth from villages
             */
            if (hasSeaBoard && isGameOptionSet(SOCGameOptionSet.K_SC_CLVI))
            {
                // distribute will usually return false; most rolls don't hit dice#s which distribute cloth
                if (((SOCBoardAtServer) board).distributeClothFromRoll(this, currentRoll, currentDice))
                    checkForWinner();
            }

            /**
             * done, next game state
             */
            if (gameState != OVER)
            {
                if (! anyGoldHex)
                {
                    gameState = PLAY1;
                } else {
                    oldGameState = PLAY1;
                    gameState = WAITING_FOR_PICK_GOLD_RESOURCE;
                }
            }
        }

        return currentRoll;
    }

    /**
     * When a 7 is rolled, update the {@link #gameState}:
     * Always {@link #WAITING_FOR_DISCARDS} if any {@link SOCPlayer#getResources()} total &gt; 7.
     * Otherwise {@link #PLACING_ROBBER}, {@link #WAITING_FOR_ROBBER_OR_PIRATE}, or (for
     * scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI})
     * {@link #WAITING_FOR_ROB_CHOOSE_PLAYER} or {@link #PLAY1}.
     *<P>
     * For state {@link #WAITING_FOR_DISCARDS}, also sets {@link SOCPlayer#setNeedToDiscard(boolean)}.
     * For state {@link #PLACING_ROBBER}, also clears {@link #robberyWithPirateNotRobber}.
     * For <tt>_SC_PIRI</tt>, sets <tt>currentRoll.sc_robPossibleVictims</tt>.
     *<P>
     * This is a separate method from {@link #rollDice()} because for <tt>_SC_PIRI</tt>, if a player wins against
     * the pirate fleet, this "7 update" happens only after they pick and gain their free resource.
     * @since 2.0.00
     */
    private final void rollDice_update7gameState()
    {
        hasRolledSeven = true;

        /**
         * if there are players with too many cards, wait for
         * them to discard
         */
        for (int i = 0; i < maxPlayers; i++)
        {
            if (players[i].getResources().getTotal() > 7)
            {
                players[i].setNeedToDiscard(true);
                gameState = WAITING_FOR_DISCARDS;
            }
        }

        /**
         * if no one needs to discard, then wait for
         * the robber to move
         */
        if (gameState != WAITING_FOR_DISCARDS)
        {
            // next-state logic is similar to playKnight and discard;
            // if you update this method, check those ones

            placingRobberForKnightCard = false;
            oldGameState = PLAY1;
            if (isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
            {
                robberyWithPirateNotRobber = false;
                currentRoll.sc_robPossibleVictims = getPossibleVictims();
                if (currentRoll.sc_robPossibleVictims.isEmpty())
                    gameState = PLAY1;  // no victims
                else
                    gameState = WAITING_FOR_ROB_CHOOSE_PLAYER;  // 1 or more victims; could choose to not steal anything
            }
            else if (canChooseMovePirate())
            {
                gameState = WAITING_FOR_ROBBER_OR_PIRATE;
            } else {
                robberyWithPirateNotRobber = false;
                gameState = PLACING_ROBBER;
            }
        }
    }

    /**
     * For {@link #rollDice()}, figure out what resources a player gets on a given roll,
     * based on the hexes adjacent to the player's settlements and cities
     * and based on the robber's position.
     *<P>
     * If {@link #hasSeaBoard}, and the player's adjacent to a
     * {@link SOCBoardLarge#GOLD_HEX}, the gold-hex resources they must pick
     * are returned as {@link SOCResourceConstants#GOLD_LOCAL}.
     *
     * @param player   the player
     * @param roll     the total number rolled on the dice
     *
     * @return the resource set
     */
    public SOCResourceSet getResourcesGainedFromRoll(SOCPlayer player, final int roll)
    {
        SOCResourceSet resources = new SOCResourceSet();
        final int robberHex = board.getRobberHex();

        /**
         * check the hexes touching settlements
         */
        getResourcesGainedFromRollPieces(roll, resources, robberHex, player.getSettlements(), 1);

        /**
         * check the hexes touching cities
         */
        getResourcesGainedFromRollPieces(roll, resources, robberHex, player.getCities(), 2);

        return resources;
    }

    /**
     * Figure out what resources these piece positions would get on a given roll,
     * based on the hexes adjacent to the pieces' node coordinates.
     * Used in {@link #getResourcesGainedFromRoll(SOCPlayer, int)}.
     *<P>
     * If {@link #hasSeaBoard}, and the player's adjacent to a
     * {@link SOCBoardLarge#GOLD_HEX}, the gold-hex resources they must pick
     * are returned as {@link SOCResourceConstants#GOLD_LOCAL}.
     *
     * @param roll     the total number rolled on the dice
     * @param resources  Add new resources to this set
     * @param robberHex  Robber's position, from {@link SOCBoard#getRobberHex()}
     * @param pieces  Collection of a type of the player's {@link SOCPlayingPiece}s at nodes on the board;
     *             should be either {@link SOCSettlement}s or {@link SOCCity}s
     * @param incr   Add this many resources (1 or 2) per playing piece
     * @since 1.1.17
     */
    private final void getResourcesGainedFromRollPieces
        (final int roll, SOCResourceSet resources,
         final int robberHex, Collection<? extends SOCPlayingPiece> pieces, final int incr)
    {
        for (final SOCPlayingPiece p : pieces)
        {
            for (final int hexCoord : board.getAdjacentHexesToNode(p.getCoordinates()))
            {
                if ((hexCoord == robberHex) || (board.getNumberOnHexFromCoord(hexCoord) != roll))
                    continue;

                switch (board.getHexTypeFromCoord(hexCoord))
                {
                case SOCBoard.CLAY_HEX:
                    resources.add(incr, SOCResourceConstants.CLAY);
                    break;

                case SOCBoard.ORE_HEX:
                    resources.add(incr, SOCResourceConstants.ORE);
                    break;

                case SOCBoard.SHEEP_HEX:
                    resources.add(incr, SOCResourceConstants.SHEEP);
                    break;

                case SOCBoard.WHEAT_HEX:
                    resources.add(incr, SOCResourceConstants.WHEAT);
                    break;

                case SOCBoard.WOOD_HEX:
                    resources.add(incr, SOCResourceConstants.WOOD);
                    break;

                case SOCBoardLarge.GOLD_HEX:
                    if (hasSeaBoard)
                        resources.add(incr, SOCResourceConstants.GOLD_LOCAL);
                        // if not hasSeaBoard, GOLD_HEX == SOCBoard.MISC_PORT_HEX
                    break;
                }
            }
        }
    }

    /**
     * @return true if the player can discard these resources
     * @see #discard(int, ResourceSet)
     *
     * @param pn  the number of the player that is discarding
     * @param rs  the resources that the player is discarding
     */
    public boolean canDiscard(final int pn, ResourceSet rs)
    {
        if (gameState != WAITING_FOR_DISCARDS)
        {
            return false;
        }

        SOCResourceSet resources = players[pn].getResources();

        if (! players[pn].getNeedToDiscard())
        {
            return false;
        }

        if (rs.getTotal() != players[pn].getCountToDiscard())
        {
            return false;
        }

        if (!resources.contains(rs))
        {
            return false;
        }

        return true;
    }

    /**
     * A player is discarding resources. Discard, check if other players
     * must still discard, and set gameState to {@link #WAITING_FOR_DISCARDS}
     * or {@link #WAITING_FOR_ROBBER_OR_PIRATE}
     * or {@link #PLACING_ROBBER} accordingly.
     *<P>
     * Assumes {@link #canDiscard(int, ResourceSet)} already called to validate.
     *<P>
     * In scenario option <tt>_SC_PIRI</tt>, there is no robber
     * to move, but the player will choose their robbery victim
     * (state {@link #WAITING_FOR_ROB_CHOOSE_PLAYER}) after any discards.
     * If there are no possible victims, state becomes {@link #PLAY1}.
     * Check for those game states after calling this method.
     *<P>
     * Special case:
     * If {@link #isForcingEndTurn()}, and no one else needs to discard,
     * gameState becomes {@link #PLAY1} but the caller must call
     * {@link #endTurn()} as soon as possible.
     *<P>
     * Called only at server.
     *
     * @param pn   the number of the player
     * @param rs   the resources that are being discarded
     */
    public void discard(final int pn, ResourceSet rs)
    {
        players[pn].getResources().subtract(rs);
        players[pn].setNeedToDiscard(false);

        /**
         * check if we're still waiting for players to discard
         */
        gameState = -1;  // temp value; oldGameState is set below

        for (int i = 0; i < maxPlayers; i++)
        {
            if (players[i].getNeedToDiscard())
            {
                gameState = WAITING_FOR_DISCARDS;

                break;
            }
        }

        /**
         * if no one needs to discard, and not forcing end of turn,
         * then wait for the robber to move
         */
        if (gameState == -1)
        {
            oldGameState = PLAY1;
            placingRobberForKnightCard = false;  // known because knight card doesn't trigger discard

            if (! forcingEndTurn)
            {
                // next-state logic is similar to playKnight and rollDice_update7gameState;
                // if you update this method, check those ones

                if (isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
                {
                    robberyWithPirateNotRobber = false;
                    currentRoll.sc_robPossibleVictims = getPossibleVictims();
                    if (currentRoll.sc_robPossibleVictims.isEmpty())
                        gameState = PLAY1;  // no victims
                    else
                        gameState = WAITING_FOR_ROB_CHOOSE_PLAYER;  // 1 or more victims; could choose to not steal anything
                }
                else if (canChooseMovePirate())
                {
                    gameState = WAITING_FOR_ROBBER_OR_PIRATE;
                } else {
                    robberyWithPirateNotRobber = false;
                    gameState = PLACING_ROBBER;
                }
            } else {
                gameState = PLAY1;
            }
        }

        lastActionTime = System.currentTimeMillis();
    }

    /**
     * Can the player pick these resources from the gold hex?
     * <tt>rs.</tt>{@link SOCResourceSet#getTotal() getTotal()}
     * must == {@link SOCPlayer#getNeedToPickGoldHexResources()}.
     * Game state must be {@link #WAITING_FOR_PICK_GOLD_RESOURCE}
     * or {@link #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}.
     *
     * @param pn  the number of the player that is picking
     * @param rs  the resources that the player is picking
     * @return true if the player can pick these resources
     * @see #pickGoldHexResources(int, SOCResourceSet)
     * @since 2.0.00
     */
    public boolean canPickGoldHexResources(final int pn, final ResourceSet rs)
    {
        if ((gameState != WAITING_FOR_PICK_GOLD_RESOURCE)
            && (gameState != STARTS_WAITING_FOR_PICK_GOLD_RESOURCE))
        {
            return false;
        }

        return (rs.getTotal() == players[pn].getNeedToPickGoldHexResources());
    }

    /**
     * A player is picking which resources to gain from the gold hex.
     * Gain them, check if other players must still pick, and set
     * gameState to {@link #WAITING_FOR_PICK_GOLD_RESOURCE} if so,
     * oldGameState otherwise (usually {@link #PLAY1}).
     * (Or, during initial placement, usually {@link #START2B} or {@link #START3B} after initial settlement at gold,
     * or {@link #START1A} or {@link #START2A} after placing a ship to a fog hex reveals gold.)
     * During normal play, the oldGameState might sometimes be {@link #PLACING_FREE_ROAD2} or {@link #SPECIAL_BUILDING}.
     *<P>
     * Assumes {@link #canPickGoldHexResources(int, ResourceSet)} was already called to validate.
     *<P>
     * During initial placement from {@link #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE},
     * calls {@link #advanceTurnStateAfterPutPiece()}.
     * The current player won't change if the gold pick was for the player's final initial settlement.
     * If the gold pick was for placing a road or ship that revealed a gold hex from {@link SOCBoardLarge#FOG_HEX fog},
     * the player will probably change here, since the player changes after most inital roads or ships.
     *<P>
     * Also used in scenario {@link SOCGameOptionSet#K_SC_PIRI SC_PIRI} after winning a pirate fleet battle at dice roll.
     *<P>
     * Called at server only; clients will instead get <tt>SOCPlayerElement</tt> messages
     * for the resources picked and the "need to pick" flag, and will call
     * {@link SOCPlayer#getResources()}<tt>.add</tt> and {@link SOCPlayer#setNeedToPickGoldHexResources(int)}.
     * Server will send out messages for new game state and any change of current player
     * from {@link #advanceTurnStateAfterPutPiece()}.
     *
     * @param pn   the number of the player
     * @param rs   the resources that are being picked
     * @return  {@link #getGameState()} from before the gold-revealing placement if called during initial placement
     *     ({@link #START1A}, START2A, etc; can be {@link #START1B} or START2B if ship was placed to a fog hex),
     *     otherwise {@link #PLAY1}
     * @since 2.0.00
     */
    public int pickGoldHexResources(final int pn, final SOCResourceSet rs)
    {
        players[pn].getResources().add(rs);
        players[pn].setNeedToPickGoldHexResources(0);
        lastActionTime = System.currentTimeMillis();

        // initial placement?
        if (gameState == STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
        {
            final int prevGS = oldGameState;
            gameState = prevGS;  // usually START2A or START3A, for initial settlement at gold without fog
            advanceTurnStateAfterPutPiece();  // player may change if was START1B, START2B, START3B
            return prevGS;
        }

        if ((oldGameState == PLAY1) || (oldGameState == SPECIAL_BUILDING) || (oldGameState == PLACING_FREE_ROAD2))
        {
            // Update player's Rolled Resource stats
            //     Note: PLAY1 is also the case for building a road/ship
            //     that revealed gold from a fog hex
            int[] resourceStats = players[pn].getResourceRollStats();
            for (int rtype = SOCResourceConstants.CLAY; rtype < resourceStats.length; ++rtype)
                resourceStats[rtype] += rs.getAmount(rtype);
        }

        /**
         * check if we're still waiting for players to pick
         */
        gameState = oldGameState;  // nearly always PLAY1, after a roll

        if ((gameState == ROLL_OR_CARD) && (currentDice == 7))
        {
            rollDice_update7gameState();  // from win vs pirate fleet at dice roll (SC_PIRI)
                // -- may set gameState to WAITING_FOR_DISCARDS, etc; see javadoc.
        } else {
            for (int i = 0; i < maxPlayers; i++)
            {
                if (players[i].getNeedToPickGoldHexResources() > 0)
                {
                    gameState = WAITING_FOR_PICK_GOLD_RESOURCE;
                    break;
                }
            }
        }

        return PLAY1;
    }

    /**
     * Based on game options, can the pirate ship be moved instead of the robber?
     * True only if {@link #hasSeaBoard}.
     *<UL>
     *<LI> For scenario option {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI}, the player
     * must have {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     *<LI> Scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI} has a pirate fleet
     * and no robber; this scenario is checked in {@link #rollDice()} and does not call
     * {@code canChooseMovePirate()}.
     *<LI> Scenario option {@link SOCGameOptionSet#K_SC_WOND _SC_WOND} does not use the pirate ship.
     *</UL>
     * @return  true if the pirate ship can be moved
     * @see #WAITING_FOR_ROBBER_OR_PIRATE
     * @see #chooseMovePirate(boolean)
     * @since 2.0.00
     */
    public boolean canChooseMovePirate()
    {
        if (! hasSeaBoard)
            return false;

        if (isGameOptionSet(SOCGameOptionSet.K_SC_WOND))
            return false;

        if (isGameOptionSet(SOCGameOptionSet.K_SC_CLVI)
            && ! players[currentPlayerNumber].hasPlayerEvent
                 (SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE))
            return false;

        return true;
    }

    /**
     * Choose to move the pirate or the robber, from game state
     * {@link #WAITING_FOR_ROBBER_OR_PIRATE}.
     * Game state becomes {@link #PLACING_ROBBER} or {@link #PLACING_PIRATE}.
     * {@link #getRobberyPirateFlag()} is set or cleared accordingly.
     *<P>
     * Called from server's game handler and also from {@link #forceEndTurn()} if necessary.
     *
     * @param pirateNotRobber  True to move pirate, false to move robber
     * @throws IllegalStateException if gameState != {@link #WAITING_FOR_ROBBER_OR_PIRATE}
     * @since 2.0.00
     */
    public void chooseMovePirate(final boolean pirateNotRobber)
        throws IllegalStateException
    {
        if (gameState != WAITING_FOR_ROBBER_OR_PIRATE)
            throw new IllegalStateException();

        robberyWithPirateNotRobber = pirateNotRobber;
        if (pirateNotRobber)
            gameState = PLACING_PIRATE;
        else
            gameState = PLACING_ROBBER;
    }

    /**
     * Can this player currently move the robber to these coordinates?
     * Must be different from current robber coordinates.
     * Must not be a desert if {@link SOCGameOption game option} RD is set to true
     * ("Robber can't return to the desert").
     * Must not be a fog hex ({@link SOCBoardLarge#FOG_HEX} can sometimes be hidden water, not land).
     * Must be current player.  Game state must be {@link #PLACING_ROBBER}.
     *
     * @return true if the player can move the robber to the coordinates
     *
     * @param pn  the number of the player that is moving the robber
     * @param co  the new robber hex coordinates; not validated
     * @see #moveRobber(int, int)
     * @see #canMovePirate(int, int)
     */
    public boolean canMoveRobber(final int pn, final int co)
    {
        if (gameState != PLACING_ROBBER)
        {
            return false;
        }

        if (currentPlayerNumber != pn)
        {
            return false;
        }

        if (board.getRobberHex() == co)
        {
            return false;
        }

        if (board instanceof SOCBoardLarge)
        {
            if (((SOCBoardLarge) board).isHexInLandAreas
                (co, ((SOCBoardLarge) board).getRobberExcludedLandAreas()))
                return false;
        }

        switch (board.getHexTypeFromCoord(co))
        {
        case SOCBoard.DESERT_HEX:
            return ! isGameOptionSet("RD");  // Only if it can return to the desert

        case SOCBoard.CLAY_HEX:
        case SOCBoard.ORE_HEX:
        case SOCBoard.SHEEP_HEX:
        case SOCBoard.WHEAT_HEX:
        case SOCBoard.WOOD_HEX:
            return true;

        case SOCBoardLarge.GOLD_HEX:
            // Must check these because the original board has port types (water hexes)
            // with the same numeric values as GOLD_HEX and FOG_HEX.
            return (board instanceof SOCBoardLarge);

        // case SOCBoardLarge.FOG_HEX:
            // Fall through to default, can't place on fog. Might be water.

        default:
            return false;  // Land hexes only (Could check board.max_robber_hex, if we didn't special-case desert,gold,fog)
        }
    }

    /**
     * move the robber.
     *<P>
     * Called only at server.  Client gets messages with results of the move, and
     * calls {@link SOCBoard#setRobberHex(int, boolean)}.
     *
     *<UL>
     * <LI> If no victims (players to possibly steal from): State becomes oldGameState,
     *      or {@link #OVER} if they just won with Largest Army.
     * <LI> If just one victim: calls stealFromPlayer, during which State becomes oldGameState,
     *      or {@link #OVER} if won with Largest Army
     * <LI> If multiple possible victims: Player must choose a victim: State becomes {@link #WAITING_FOR_ROB_CHOOSE_PLAYER}.
     *</UL>
     *<P>
     * Assumes {@link #canMoveRobber(int, int)} has been called already to validate the move.
     * Assumes gameState {@link #PLACING_ROBBER}.
     * Also updates {@link #getRobberyResult()}.
     *
     * @param pn  the number of the player that is moving the robber
     * @param rh  the robber's new hex coordinate; must be &gt; 0, not validated beyond that
     *
     * @return returns a result that says if a resource was stolen, or
     *         if the player needs to make a choice.  It also returns
     *         what was stolen and who was the victim.
     *         The private <tt>robberResult</tt> field is updated to this return value.
     * @throws IllegalArgumentException if <tt>rh</tt> &lt;= 0
     * @see #movePirate(int, int)
     */
    public SOCMoveRobberResult moveRobber(final int pn, final int rh)
        throws IllegalArgumentException
    {
        if (robberResult == null)
            robberResult = new SOCMoveRobberResult();
        else
            robberResult.clear();

        board.setRobberHex(rh, true);  // if rh coord invalid, throws IllegalArgumentException
        robberyWithPirateNotRobber = false;
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;

        /**
         * do the robbing thing
         */
        final List<SOCPlayer> victims = getPossibleVictims();

        if (victims.isEmpty())
        {
            gameState = oldGameState;
        }
        else if (victims.size() == 1)
        {
            final SOCPlayer victim = victims.get(0);
            final int loot = stealFromPlayer(victim.getPlayerNumber(), false);
            robberResult.setLoot(loot);
        }
        else
        {
            /**
             * the current player needs to make a choice
             */
            gameState = WAITING_FOR_ROB_CHOOSE_PLAYER;
        }

        robberResult.setVictims(victims);

        return robberResult;
    }

    /**
     * Can this player currently move the pirate ship to these coordinates?
     * Must be a water hex, per {@link SOCBoardLarge#isHexOnWater(int)}.
     * Must be different from current pirate coordinates.
     * Must not be a fog hex ({@link SOCBoardLarge#FOG_HEX} can sometimes be hidden land, not water).
     * Game must have {@link #hasSeaBoard}.
     * Must be current player.  Game state must be {@link #PLACING_PIRATE}.
     * For scenario option {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI}, the player
     * must have {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     *
     * @return true if this player can move the pirate ship to this hex coordinate
     *
     * @param pn  the number of the player that is moving the pirate
     * @param hco  the new pirate hex coordinates; will check for a water hex
     * @see #movePirate(int, int)
     * @see #canMoveRobber(int, int)
     * @since 2.0.00
     */
    public boolean canMovePirate(final int pn, final int hco)
    {
        if (! hasSeaBoard)
            return false;
        if (gameState != PLACING_PIRATE)
            return false;
        if (currentPlayerNumber != pn)
            return false;
        if (((SOCBoardLarge) board).getPirateHex() == hco)
            return false;
        if (isGameOptionSet(SOCGameOptionSet.K_SC_CLVI)
            && ! players[pn].hasPlayerEvent(SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE))
            return false;

        return (board.isHexOnWater(hco));
    }

    /**
     * Move the pirate ship.
     *<P>
     * Called only at server.  Client gets messages with results of the move, and
     * calls {@link SOCBoardLarge#setPirateHex(int, boolean)}.
     *
     *<H5>Normal operation:</H5>
     *
     *<UL>
     * <LI> If no victims (players to possibly steal from): State becomes oldGameState,
     *      or {@link #OVER} if they just won with Largest Army.
     * <LI> If multiple possible victims: Player must choose a victim:
     *      State becomes {@link #WAITING_FOR_ROB_CHOOSE_PLAYER}.
     *      Once chosen, call {@link #choosePlayerForRobbery(int)} to choose a victim.
     * <LI> If just one victim: calls stealFromPlayer, State becomes oldGameState.
     *      If Largest Army or cloth robbery gives player enough VP to win, sets gameState to {@link #OVER}.
     *      <P>
     *      Or if just one victim but {@link #canChooseRobClothOrResource(int)},
     *      state becomes {@link #WAITING_FOR_ROB_CLOTH_OR_RESOURCE}.
     *      Once chosen, call {@link #stealFromPlayer(int, boolean)}.
     *</UL>
     *
     * Assumes {@link #canMovePirate(int, int)} has been called already to validate the move.
     * Assumes gameState {@link #PLACING_PIRATE}.
     * Also updates {@link #getRobberyResult()}.
     *
     *<H5>Game scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}:</H5>
     *
     * The pirate is moved not by the player,
     * but by the game at every dice roll.  See {@link #movePirate(int, int, int)} instead of this method.
     *
     * @param pn  the number of the player that is moving the pirate ship
     * @param ph  the pirate's new hex coordinate; should be a water hex
     *
     * @return returns a result that says if a resource was stolen, or
     *         if the player needs to make a choice.  It also returns
     *         what was stolen and who was the victim.
     *         <P>
     *         In scenario <tt>_SC_PIRI</tt> only, might contain {@link SOCResourceConstants#GOLD_LOCAL}
     *         if the player wins; see {@link #movePirate(int, int, int)} for details.
     * @throws IllegalArgumentException if <tt>ph</tt> &lt;= 0
     * @see #moveRobber(int, int)
     * @since 2.0.00
     */
    public SOCMoveRobberResult movePirate(final int pn, final int ph)
        throws IllegalArgumentException
    {
        return movePirate(pn, ph, -1);
    }

    /**
     * Move the pirate, optionally with a pirate fleet strength, and do a robbery/fleet battle if needed.
     * Called only at server, by {@link #rollDice()} in scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
     *<P>
     * See {@link #movePirate(int, int)} for method javadocs in "normal" operation (not {@code _SC_PIRI}).
     *<P>
     * In <b>game scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI},</b> the pirate is moved not by the player,
     * but by the game at every dice roll.  See {@link SOCBoardLarge#movePirateHexAlongPath(int)},
     * {@link #getPossibleVictims()}, and {@link #stealFromPlayerPirateFleet(int, int)} for details.
     *
     *<H5>Results:</H5>
     *<UL>
     * <LI> {@link SOCMoveRobberResult#victims} will be the player(s) having a settlement/city adjacent to
     *      the new pirate hex, or {@code null} or an empty list.<BR>
     *      <B>SC_PIRI:</B> See {@link #getPossibleVictims()}: Is empty if multiple players
     *      or {@link SOCPlayer#getAddedLegalSettlement()} locations are adjacent to the pirate.
     * <LI> If there is 1 victim, {@link SOCMoveRobberResult#sc_piri_loot} will be set to the robbed resource(s) if any,
     *      or empty if nothing to rob
     * <LI> If player's warship strength equals the pirate fleet's, nothing is stolen or gained
     * <LI> If player's warships are stronger than the pirate fleet:
     *    <UL>
     *     <LI> {@code sc_piri_loot} will contain {@link SOCResourceConstants#GOLD_LOCAL}
     *     <LI> Does not set {@link SOCPlayer#setNeedToPickGoldHexResources(int)}
     *     <LI> Does not set game state
     *     <LI> Caller {@link #rollDice()} will set the game state and that player flag
     *    </UL>
     *</UL>
     *
     * @param pn  the number of the player that is moving the pirate ship
     * @param ph  the pirate's new hex coordinate; should be a water hex
     * @param pirFleetStrength  Pirate fleet strength, or -1 if not scenario _SC_PIRI
     * @return  see above and {@link #movePirate(int, int)} return value;
     *     also sets {@link #robberResult} to the reported results
     * @throws IllegalArgumentException if {@code ph} &lt; 0
     * @since 2.0.00
     */
    public SOCMoveRobberResult movePirate(final int pn, final int ph, final int pirFleetStrength)
        throws IllegalArgumentException
    {
        if (robberResult == null)
            robberResult = new SOCMoveRobberResult();
        else
            robberResult.clear();

        ((SOCBoardLarge) board).setPirateHex(ph, true);  // if ph invalid, throws IllegalArgumentException
        robberyWithPirateNotRobber = true;
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;

        /**
         * do the robbing thing
         */
        final List<SOCPlayer> victims = getPossibleVictims();

        if (victims.isEmpty())
        {
            gameState = oldGameState;
        }
        else if (victims.size() == 1)
        {
            final SOCPlayer victim = victims.get(0);
            final int vpn = victim.getPlayerNumber();

            if (isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
            {
                // Call is from rollDice():
                // If player has warships, might tie or be stronger (win gold), otherwise pirate steals multiple items
                // Set sc_piri_loot; don't change gameState
                stealFromPlayerPirateFleet(vpn, pirFleetStrength);
            }
            else if (! canChooseRobClothOrResource(vpn))
            {
                // steal item, also sets gameState
                final int loot = stealFromPlayer(vpn, false);
                robberResult.setLoot(loot);
            } else {
                /**
                 * the current player needs to make a choice
                 * of whether to steal cloth or a resource
                 */
                gameState = WAITING_FOR_ROB_CLOTH_OR_RESOURCE;
            }
        }
        else if (! isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
        {
            /**
             * the current player needs to make a choice
             * of which player to steal from
             * (no pirate robbery in _SC_PIRI if multiple victims)
             */
            gameState = WAITING_FOR_ROB_CHOOSE_PLAYER;
        }

        robberResult.setVictims(victims);

        return robberResult;
    }

    /**
     * When moving the robber or pirate, can this player be chosen to be robbed?
     * Game state must be {@link #WAITING_FOR_ROB_CHOOSE_PLAYER} or {@link #WAITING_FOR_PICK_GOLD_RESOURCE}.
     * To choose the player and rob, call {@link #choosePlayerForRobbery(int)}.
     *
     * @return true if the current player can choose this player to rob
     * @param pn  the number of the player to rob, or -1 to rob no one
     *           in game scenario <tt>_SC_PIRI</tt> as described in
     *           {@link #WAITING_FOR_ROB_CHOOSE_PLAYER}.
     *
     * @see #getRobberyPirateFlag()
     * @see #getPossibleVictims()
     * @see #canChooseRobClothOrResource(int)
     * @see #stealFromPlayer(int, boolean)
     */
    public boolean canChoosePlayer(final int pn)
    {
        if ((gameState != WAITING_FOR_ROB_CHOOSE_PLAYER) && (gameState != WAITING_FOR_ROB_CLOTH_OR_RESOURCE))
        {
            return false;
        }

        if (pn == -1)
        {
            if (gameState != WAITING_FOR_ROB_CHOOSE_PLAYER)
                return false;

            return isGameOptionSet(SOCGameOptionSet.K_SC_PIRI);
        }

        for (SOCPlayer pl : getPossibleVictims())
        {
            if (pl.getPlayerNumber() == pn)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The current player has chosen a victim to rob.
     * Do that, unless they must choose whether to rob cloth or a resource.
     * Calls {@link #canChooseRobClothOrResource(int)} to check that.
     *<P>
     * Calls {@link #stealFromPlayer(int, boolean)} to perform the robbery and set gameState back to oldGameState.
     * If cloth robbery gives player enough VP to win, sets gameState to {@link #OVER}.
     *<P>
     * If they must choose what to steal, instead sets gameState to {@link #WAITING_FOR_ROB_CLOTH_OR_RESOURCE}.
     * Once chosen, call {@link #stealFromPlayer(int, boolean)}.
     *<P>
     * Does not validate <tt>pn</tt>; assumes {@link #canChoosePlayer(int)} has been called already.
     *<P>
     * In scenario option <tt>_SC_PIRI</tt>, the player can
     * choose to not steal from anyone after rolling a 7.
     * In that case, call with <tt>pn == -1</tt>.
     * State becomes {@link #PLAY1}.
     *
     * @param pn  the number of the player being robbed
     * @return the type of resource that was stolen, as in {@link SOCResourceConstants}, or 0
     *     if must choose first (state {@link #WAITING_FOR_ROB_CLOTH_OR_RESOURCE}),
     *     or {@link SOCResourceConstants#CLOTH_STOLEN_LOCAL} for cloth.
     * @since 2.0.00
     */
    public int choosePlayerForRobbery(final int pn)
    {
        if ((pn == -1) && (gameState == WAITING_FOR_ROB_CHOOSE_PLAYER))
        {
            gameState = PLAY1;
            return 0;
        }

        if (! canChooseRobClothOrResource(pn))
        {
            return stealFromPlayer(pn, false);
        } else {
            gameState = WAITING_FOR_ROB_CLOTH_OR_RESOURCE;
            return 0;
        }
    }

    /**
     * Can this player be robbed of either cloth or resources?
     * True only if {@link #hasSeaBoard} and when robbing with the pirate
     * ({@link #getRobberyPirateFlag()}).  True only if the player has
     * both cloth and resources.
     *<P>
     * Assumes {@link #canChoosePlayer(int)} has been called already.
     *<P>
     * Used with scenario option {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI}.
     *
     * @param pn  Player number to check
     * @return true  only if current player can choose to rob either cloth or resources from <tt>pn</tt>.
     * @since 2.0.00
     */
    public boolean canChooseRobClothOrResource(final int pn)
    {
        if (! (hasSeaBoard && robberyWithPirateNotRobber))
            return false;
        final SOCPlayer pl = players[pn];
        return (pl.getCloth() > 0) && (pl.getResources().getTotal() > 0);
    }

    /**
     * Can the current player attack their pirate fortress, and try to conquer and recapture it?
     * This method is validation before calling {@link #attackPirateFortress(SOCShip)}.
     *<P>
     * To attack, game state must be {@link #PLAY1}.
     * The current player must have a {@link SOCPlayer#getFortress()} != {@code null},
     * and a ship on an edge adjacent to that {@link SOCFortress}'s node.
     *<P>
     * Used with scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
     *
     * @return Current player's ship adjacent to their {@link SOCFortress}, or {@code null} if they can't attack
     * @see #canAttackPirateFortress(SOCPlayer, boolean)
     * @since 2.0.00
     */
    public SOCShip canAttackPirateFortress()
    {
        return canAttackPirateFortress(null, false);
    }

    /**
     * Can this player attack their pirate fortress, and try to conquer and recapture it?
     * Same logic as {@link #canAttackPirateFortress()}, but can optionally call for
     * other players and/or ignore current game state. See that method's javadoc for details.
     * Useful for client-side tooltips or menus.
     *
     * @param pl  Player to check, or {@code null} for current player
     * @param checkPiecesOnly  True if should ignore current game state and current player,
     *     only check whether the pieces are in position to do so
     * @return  Player's ship adjacent to their {@link SOCFortress}, or {@code null} if they can't attack
     * @since 2.0.00
     */
    public SOCShip canAttackPirateFortress(SOCPlayer pl, final boolean checkPiecesOnly)
    {
        if (! (checkPiecesOnly || (gameState == PLAY1)))
            return null;

        if (pl == null)
            pl = players[currentPlayerNumber];
        else if (! (checkPiecesOnly || (pl.getPlayerNumber() == currentPlayerNumber)))
            return null;

        SOCFortress fort = pl.getFortress();
        if (fort == null)   // will be null unless _SC_PIRI; will be null if already recaptured by player
            return null;

        // Look for player's ship at edge adjacent to pirate fortress;
        // start with most recently placed ship
        final int[] edges = board.getAdjacentEdgesToNode_arr(fort.getCoordinates());
        Vector<SOCRoutePiece> roadsAndShips = pl.getRoadsAndShips();
        for (int i = roadsAndShips.size() - 1; i >= 0; --i)
        {
            SOCRoutePiece rs = roadsAndShips.get(i);
            if (! (rs instanceof SOCShip))
                continue;

            final int rsCoord = rs.getCoordinates();
            for (int j = 0; j < edges.length; ++j)
                if (rsCoord == edges[j])
                    return (SOCShip) rs;  // <--- found adjacent ship ---
        }

        return null;
    }

    /**
     * The current player has built ships to their pirate fortress, and attacks now to try to conquer and recapture it.
     * This can happen at most once per turn: Attacking the fortress always ends the player's turn.
     * Assumes {@link #canAttackPirateFortress()} called first, to validate and get the {@code adjacent} ship.
     *<P>
     * Before calling, call {@link SOCPlayer#getFortress()} so that you can get its new strength afterwards,
     * and call {@link SOCPlayer#getNumWarships()} in case the player doesn't win and the ship they lose is a warship.
     *<P>
     * The player's fleet strength ({@link SOCPlayer#getNumWarships()}) will be compared to a pirate defense
     * strength of 1 to 6 (random).  Players lose 1 ship on a tie, 2 ships if defeated by the pirates.
     *<P>
     * If the player wins, the {@link SOCFortress} strength is reduced by 1.  After several wins, its strength is 0 and
     * the fortress is recaptured, becoming a {@link SOCSettlement} for the player.  This method will fire a
     * {@link SOCPlayerEvent#PIRI_FORTRESS_RECAPTURED} to announce this to any listener.
     *<P>
     * Used with scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
     *
     * @param adjacent  The current player's ship adjacent to their {@link SOCFortress},
     *     from {@link #canAttackPirateFortress()}; unless player wins, this ship will be lost to the pirates.
     * @return  Results array, whose length depends on the number of ships lost by the player to the pirates' defense.<BR>
     *     results[0] is the pirate defense strength rolled here.<BR>
     *     <UL>
     *     <LI> If the player wins, they lose no ships.
     *          Array length is 1.
     *     <LI> If the player ties the pirates, they lose their 1 adjacent ship.
     *          Array length is 2; results[1] is {@code adjacent}'s coordinates.
     *     <LI> If the player loses to the pirates, they lose their 2 ships closest to the pirate fortress.
     *          Array length is 3; results[1] is {@code adjacent}'s coordinates, results[2] is the other lost ship's coord.
     *     </UL>
     * @since 2.0.00
     */
    public int[] attackPirateFortress(final SOCShip adjacent)
    {
        SOCPlayer currPlayer = players[currentPlayerNumber];
        final int nWarships = currPlayer.getNumWarships();
        SOCFortress fort = currPlayer.getFortress();  // not null if caller validated with canAttackPirateFortress

        final int pirStrength = 1 + rand.nextInt(6);

        final int nShipsLost;
        if (nWarships < pirStrength)
            nShipsLost = 2;
        else if (nWarships == pirStrength)
            nShipsLost = 1;
        else
        {
            // player won, reduce fortress strength
            nShipsLost = 0;
            final int newFortStrength = fort.getStrength() - 1;
            fort.setStrength(newFortStrength);

            if (newFortStrength == 0)
            {
                // Fortress defeated: Convert to a settlement
                final SOCSettlement recaptSettle = new SOCSettlement(currPlayer, fort.getCoordinates(), board);
                putPiece(recaptSettle);
                //  game.putPiece will call currPlayer.putPiece, which will set player's fortress field = null.
                //  game.putPiece will also call checkForWinner, and may set gamestate to OVER.

                // Fire the scenario player event, with the resulting SOCSettlement
                if (gameEventListener != null)
                    gameEventListener.playerEvent
                        (this, currPlayer, SOCPlayerEvent.PIRI_FORTRESS_RECAPTURED, true, recaptSettle);

                // Have all other players' fortresses also been conquered?
                boolean stillHasFortress = false;
                for (int pn = 0; pn < maxPlayers; ++pn)
                {
                    if ((pn == currentPlayerNumber) || isSeatVacant(pn))
                        continue;

                    final SOCFortress pfort = players[pn].getFortress();
                    if ((pfort != null) && (pfort.getStrength() > 0))
                    {
                        stillHasFortress = true;
                        break;
                    }
                }

                if (! stillHasFortress)
                {
                    // All fortresses defeated. pirate fleet goes away; trigger a further scenario game event for that.
                    ((SOCBoardLarge) board).setPirateHex(0, true);
                    if (gameEventListener != null)
                        gameEventListener.gameEvent
                            (this, SOCGameEvent.SGE_PIRI_LAST_FORTRESS_FLEET_DEFEATED, null);
                }
            }
        }

        // Remove player's lost ships, if any,
        // and build our results array
        int[] retval = new int[1 + nShipsLost];
        retval[0] = pirStrength;
        if (nShipsLost > 0)
        {
            final int shipEdge = adjacent.getCoordinates();
            retval[1] = shipEdge;
            removeShip(adjacent);

            if (nShipsLost > 1)
            {
                // find player's newest-placed ship adjacent to shipEdge;
                // it will also be lost
                final List<Integer> adjacEdges = board.getAdjacentEdgesToEdge(shipEdge);
                List<SOCRoutePiece> roadsAndShips = currPlayer.getRoadsAndShips();
                for (int i = roadsAndShips.size() - 1; i >= 0; --i)
                {
                    SOCRoutePiece rs = roadsAndShips.get(i);
                    if (! (rs instanceof SOCShip))
                        continue;

                    final int rsCoord = rs.getCoordinates();
                    if (! adjacEdges.contains(Integer.valueOf(rsCoord)))
                        continue;

                    retval[2] = rsCoord;
                    removeShip((SOCShip) rs);
                    break;
                }
            }
        }

        // Attacking the pirate fortress ends the player's turn.
        if (gameState < OVER)
            endTurn();

        return retval;
    }

    /**
     * Get the players who have settlements or cities on this hex.
     * @return a list of players touching a hex
     *   with their settlements/cities, or an empty list if none.
     *   Any player with multiple settlements/cities on the hex
     *   will be in the list just once, not once per piece.
     *
     * @param hex  the coordinates of the hex; not checked for validity
     * @param collectAdjacentPieces  optional set to use to return all players' settlements and cities
     *     adjacent to {@code hex}, or {@code null}
     * @see #getPlayersShipsOnHex(int)
     */
    public List<SOCPlayer> getPlayersOnHex(final int hex, final Set<SOCPlayingPiece> collectAdjacentPieces)
    {
        final List<SOCPlayer> playerList = new ArrayList<SOCPlayer>(3);

        final int[] nodes = board.getAdjacentNodesToHex_arr(hex);

        for (int i = 0; i < maxPlayers; i++)
        {
            if (isSeatVacant(i))
                continue;

            boolean touching = false;

            for (SOCSettlement ss : players[i].getSettlements())
            {
                final int seCoord = ss.getCoordinates();
                for (int d = 0; d < 6; ++d)
                {
                    if (seCoord == nodes[d])
                    {
                        touching = true;
                        if (collectAdjacentPieces != null)
                            collectAdjacentPieces.add(ss);
                        break;
                    }
                }

                if (touching && (collectAdjacentPieces == null))
                    break;
            }

            if ((! touching) || (collectAdjacentPieces != null))
            {
                for (SOCCity ci : players[i].getCities())
                {
                    final int ciCoord = ci.getCoordinates();
                    for (int d = 0; d < 6; ++d)
                    {
                        if (ciCoord == nodes[d])
                        {
                            touching = true;
                            if (collectAdjacentPieces != null)
                                collectAdjacentPieces.add(ci);
                            break;
                        }
                    }

                    if (touching && (collectAdjacentPieces == null))
                        break;
                }
            }

            if (touching)
                playerList.add(players[i]);
        }

        return playerList;
    }

    /**
     * Get the list of players who have ships on the edges of this hex.
     *
     * @param hex  the coordinates of the hex; not checked for validity
     * @return a list of {@link SOCPlayer}s touching a hex
     *   with ships, or an empty list if none
     * @see #getPlayersOnHex(int, Set)
     * @since 2.0.00
     */
    public List<SOCPlayer> getPlayersShipsOnHex(final int hex)
    {
        ArrayList<SOCPlayer> playerList = new ArrayList<>(3);

        final int[] edges = ((SOCBoardLarge) board).getAdjacentEdgesToHex_arr(hex);

        for (int i = 0; i < maxPlayers; i++)
        {
            if (isSeatVacant(i))
                continue;

            Vector<SOCRoutePiece> roads_ships = players[i].getRoadsAndShips();
            boolean touching = false;
            for (SOCRoutePiece rs : roads_ships)
            {
                if (rs.isRoadNotShip())
                    continue;

                final int shCoord = rs.getCoordinates();
                for (int d = 0; d < 6; ++d)
                {
                    if (shCoord == edges[d])
                    {
                        touching = true;
                        break;
                    }
                }
            }

            if (touching)
                playerList.add(players[i]);
        }

        return playerList;
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}, get the Pirate Fortress
     * at this node location, if any.  A player must defeat 'their' fortress to win.
     * @param  node  Coordinate to check for fortress
     * @return  Fortress at that location, or null if none or if <tt>_SC_PIRI</tt> not active.
     *          If the player has already defeated their fortress, this will return null, like
     *          {@link SOCPlayer#getFortress()}; use {@link SOCBoard#settlementAtNode(int)} to
     *          get the settlement that it's converted into after defeat.
     * @since 2.0.00
     */
    public SOCFortress getFortress(final int node)
    {
        if (! isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
            return null;

        for (int i = 0; i < maxPlayers; ++i)
        {
            final SOCFortress pf = players[i].getFortress();
            if ((pf != null) && (node == pf.getCoordinates()))
                return pf;
        }

        return null;
    }

    /**
     * Get the results of the most recent {@link #moveRobber(int, int)} or {@link #movePirate(int, int)}.
     * Is set at server only.
     * @return Robbery results at server, or {@code null} if none so far or at client
     * @see #getRobberyPirateFlag()
     * @since 2.5.00
     */
    public SOCMoveRobberResult getRobberyResult()
    {
        return robberResult;
    }

    /**
     * Does the current or most recent robbery use the pirate ship, not the robber?
     * If true, victims will be based on adjacent ships, not settlements/cities.
     * @return true for pirate ship, false for robber
     * @see #getRobberyResult()
     * @since 2.0.00
     */
    public boolean getRobberyPirateFlag()
    {
        return robberyWithPirateNotRobber;
    }

    /**
     * Given the robber or pirate's current position on the board,
     * and {@link #getRobberyPirateFlag()},
     * the list of victims with adjacent settlements/cities or ships (if any).
     * Victims are players with resources; for scenario option
     * {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI}, also players with cloth
     * when robbing with the pirate.
     *<P>
     * Calls {@link #getPlayersOnHex(int, Set)} or {@link #getPlayersShipsOnHex(int)}.
     *
     *<H3>For scenario option {@link SOCGameOptionSet#K_SC_PIRI}:</H3>
     * This is called after a 7 is rolled, or after the game moves the pirate ship (fleet).
     * When a 7 is rolled, the current player may rob from any player with resources.
     * When the pirate ship is moved (at every dice roll), the player with a
     * port settlement/city adjacent to the pirate ship's hex is attacked.
     * Internal flag {@code robberyWithPirateNotRobber} tracks whether this robbery
     * comes from moving the robber or the pirate.
     *<P>
     * If there are multiple adjacent players, there is no battle and no robbery victim:
     * returns an empty list. This also applies to the hex in the middle of the 6-player board
     * that's adjacent to 2 players' {@link SOCPlayer#getAddedLegalSettlement()} locations,
     * even if they haven't both built there.
     *
     * @return a list of possible players to rob, or an empty list
     * @see #canChoosePlayer(int)
     * @see #choosePlayerForRobbery(int)
     */
    public List<SOCPlayer> getPossibleVictims()
    {
        if ((currentRoll.sc_robPossibleVictims != null)
            && ((gameState == WAITING_FOR_ROB_CHOOSE_PLAYER) || (gameState == WAITING_FOR_ROB_CLOTH_OR_RESOURCE)))
        {
            // already computed this turn
            return currentRoll.sc_robPossibleVictims;
        }

        // victims wil be a subset of candidates:
        // has resources, ! isSeatVacant, ! currentPlayer.

        List<SOCPlayer> candidates;

        if (isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
        {
            if (robberyWithPirateNotRobber)
            {
                // Pirate moved: Any player with adjacent port settlement/city.
                // If more than 1 player, no one is attacked by the pirates.
                // Resource counts don't matter.

                final int ph = ((SOCBoardLarge) board).getPirateHex();
                if (ph != 0)
                {
                    Set<SOCPlayingPiece> adjacPieces = new HashSet<SOCPlayingPiece>();
                    candidates = getPlayersOnHex(ph, adjacPieces);
                    final int nCandidates = candidates.size();
                    if (nCandidates > 1)
                    {
                        candidates.clear();
                    } else if (nCandidates == 1) {
                        // Check adjacPieces for victim's getAddedLegalSettlement
                        // and if found, check for other players'.
                        final SOCPlayer victim = candidates.get(0);
                        final int victimSettleNode = victim.getAddedLegalSettlement();
                        boolean atAdded = false;
                        for (SOCPlayingPiece pp : adjacPieces)
                        {
                            if (victimSettleNode == pp.getCoordinates())
                            {
                                atAdded = true;
                                break;
                            }
                        }
                        if (atAdded)
                        {
                            // See if any other player's getAddedLegalSettlement is also adjacent to this hex.
                            // If so, should not rob at this hex per SC_PIRI scenairo rules.
                            final int[] pirateAdjacNodes = board.getAdjacentNodesToHex_arr(ph);
                            boolean hasOtherPlayer = false;
                            outerLoop:
                            for (int pn = 0; pn < maxPlayers; ++pn)
                            {
                                if (isSeatVacant(pn))
                                    continue;
                                final SOCPlayer pl = players[pn];
                                if (pl == victim)
                                    continue;
                                final int plSettleNode = pl.getAddedLegalSettlement();
                                for (final int node : pirateAdjacNodes)
                                {
                                    if (node == plSettleNode)
                                    {
                                        hasOtherPlayer = true;
                                        break outerLoop;
                                    }
                                }
                            }

                            if (hasOtherPlayer)
                                candidates.clear();
                        }
                    }
                } else {
                    candidates = new ArrayList<SOCPlayer>();
                }

                return candidates;  // <--- Early return: Special for scenario ---

            } else {
                // Robber (7 rolled): all non-current players with resources.
                // For-loop below will check candidate resources.
                candidates = new ArrayList<SOCPlayer>();
                for (int pn = 0; pn < maxPlayers; ++pn)
                    if ((pn != currentPlayerNumber) && ! isSeatVacant(pn))
                        candidates.add(players[pn]);
            }
        }
        else if (robberyWithPirateNotRobber)
        {
            candidates = getPlayersShipsOnHex(((SOCBoardLarge) board).getPirateHex());
        } else {
            candidates = getPlayersOnHex(board.getRobberHex(), null);
        }

        List<SOCPlayer> victims = new ArrayList<SOCPlayer>();
        for (SOCPlayer pl : candidates)
        {
            final int pn = pl.getPlayerNumber();

            if ((pn != currentPlayerNumber)
                && ( (pl.getResources().getTotal() > 0) || (robberyWithPirateNotRobber && (pl.getCloth() > 0)) ))
            {
                victims.add(pl);
            }
        }

        return victims;
    }

    /**
     * the current player has choosen a victim to rob.
     * perform the robbery.  Set gameState back to {@code oldGameState}.
     * The current player gets the stolen item.
     *<P>
     * For the cloth game scenario, can steal cloth, and can gain victory points from having
     * cloth. If cloth robbery gives player enough VP to win, sets gameState to {@link #OVER}.
     *<P>
     * Does not validate <tt>pn</tt>; assumes {@link #canChoosePlayer(int)}
     * and {@link #choosePlayerForRobbery(int)} have been called already.
     * Assumes current game state is {@link #PLACING_ROBBER} or
     * {@link #WAITING_FOR_ROB_CHOOSE_PLAYER}.
     *
     * @param pn  the number of the player being robbed
     * @param choseCloth  player has both resources and {@link SOCPlayer#getCloth() cloth},
     *                    the robbing player chose to steal cloth.
     *                    Even if false (no choice made), will steal cloth
     *                    if player has 0 {@link SOCPlayer#getResources() resources}.
     * @return the type of resource that was stolen, as in {@link SOCResourceConstants},
     *         or {@link SOCResourceConstants#CLOTH_STOLEN_LOCAL} for cloth.
     * @see #stealFromPlayerPirateFleet(int, int)
     */
    public int stealFromPlayer(final int pn, boolean choseCloth)
    {
        SOCPlayer victim = players[pn];
        final int nRsrcs = victim.getResources().getTotal();
        final int rpick;  // resource picked to steal

        if ((nRsrcs == 0) || choseCloth)
        {
            /**
             * steal 1 cloth
             */
            rpick = SOCResourceConstants.CLOTH_STOLEN_LOCAL;
            victim.setCloth(victim.getCloth() - 1);
            players[currentPlayerNumber].setCloth(players[currentPlayerNumber].getCloth() + 1);
            checkForWinner();  // cloth are worth VP
        }
        else
        {
            /**
             * pick a resource card at random
             */
            int[] rsrcs = new int[nRsrcs];  // 1 element per resource card held by victim
            int cnt = 0;

            for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                    i++)
            {
                for (int j = 0; j < victim.getResources().getAmount(i); j++)
                {
                    rsrcs[cnt] = i;
                    cnt++;
                }
            }

            int pick = Math.abs(rand.nextInt() % cnt);
            rpick = rsrcs[pick];

            /**
             * and transfer it to the current player
             */
            victim.getResources().subtract(1, rpick);
            players[currentPlayerNumber].getResources().add(1, rpick);
        }

        /**
         * restore the game state to what it was before the robber or pirate moved
         * unless player just won from cloth VP
         */
        if (gameState != OVER)
            gameState = oldGameState;

        return rpick;
    }

    /**
     * In game scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}, after the pirate fleet is moved,
     * have the pirate fleet attack the victim player with an adjacent settlement or city.
     * If pirate fleet is stronger than player's fleet ({@link SOCPlayer#getNumWarships()}),
     * performs the robbery.  Number of resources stolen are 1 + victim's number of cities.
     * The stolen resources are discarded, no player gets them.
     * If player's fleet is stronger, "victim" player gets to pick a free resource.
     *<P>
     * Does not change gameState.
     *<P>
     * Results are reported back through {@link #robberResult}:
     *<UL>
     * <LI> {@link SOCMoveRobberResult#sc_piri_loot} are the resource(s) stolen, if any, or an empty set
     * <LI> If player had no resources, {@code sc_piri_loot.getTotal()} is 0
     * <LI> If player's strength is tied with {@code pirFleetStrength}, nothing is gained or lost
     * <LI> If player is stronger than the pirate fleet, they get to pick a free resource:
     *      {@code sc_piri_loot} contains 1 {@link SOCResourceConstants#GOLD_LOCAL}.
     *      Caller must set {@link SOCPlayer#setNeedToPickGoldHexResources(int)}, isn't set here.
     *</UL>
     * Assumes proper game state and scenario, does not validate those or {@code pn}.
     *
     * @param pn  The player number being robbed; not validated
     * @param pirFleetStrength  Pirate fleet strength, from {@link #rollDice()} dice roll
     * @see #stealFromPlayer(int, boolean)
     * @since 2.0.00
     */
    private void stealFromPlayerPirateFleet(final int pn, final int pirFleetStrength)
    {
        if (robberResult == null)
            robberResult = new SOCMoveRobberResult();

        robberResult.loot = -1;
        if (robberResult.sc_piri_loot == null)
            robberResult.sc_piri_loot = new SOCResourceSet();
        SOCResourceSet loot = robberResult.sc_piri_loot;
        loot.clear();

        SOCPlayer victim = players[pn];
        final int vicStrength = victim.getNumWarships();

        if (vicStrength > pirFleetStrength)
            // player will pick a free resource
            loot.add(1, SOCResourceConstants.GOLD_LOCAL);

        if (vicStrength >= pirFleetStrength)
        {
            return;  // <--- Early return: Tie or player wins ---
        }

        final int nRsrcs = victim.getResources().getTotal();
        int nSteal = 1 + victim.getCities().size();
        if (nSteal > nRsrcs)
            nSteal = nRsrcs;

        int[] rsrcs = new int[nRsrcs];  // 1 element per resource card held by victim
        int cnt = 0;
        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; ++i)
        {
            for (int j = victim.getResources().getAmount(i); j > 0; --j)
            {
                rsrcs[cnt] = i;
                cnt++;
            }
        }

        for (int k = nSteal; k > 0; --k, --cnt)
        {
            final int rpick;  // resource type picked to steal

            /**
             * pick a resource card at random
             */
            final int pick = Math.abs(rand.nextInt() % cnt);
            rpick = rsrcs[pick];

            /**
             * and discard it from the current player
             */
            victim.getResources().subtract(1, rpick);
            loot.add(1, rpick);

            /**
             * update rsrcs for next steal, if any
             */
            if ((k > 1) && (pick < (cnt-1)))
                rsrcs[pick] = rsrcs[cnt - 1];
        }
    }

    /**
     * Are there currently any trade offers?
     * Calls each player's {@link SOCPlayer#getCurrentOffer()}.
     * @return true if any, false if not
     * @see #makeTrade(int, int)
     * @since 1.1.12
     */
    public boolean hasTradeOffers()
    {
        for (int i = 0; i < maxPlayers; ++i)
        {
            if ((seats[i] == OCCUPIED) && (players[i].getCurrentOffer() != null))
                return true;
        }

        return false;
    }

    /**
     * This player is declining or rejecting all trade offers sent to them.
     * For all active trade offers, calls
     * {@link SOCTradeOffer#clearWaitingReplyFrom(int) offer.clearWaitingReplyFrom(rejectingPN)}.
     * @param rejectingPN  Player number who is rejecting trade offers
     * @throws IllegalArgumentException if <tt>rejectingPN &lt; 0</tt> or <tt>&gt;= {@link SOCGame#MAXPLAYERS}</tt>
     * @since 2.0.00
     */
    public void rejectTradeOffersTo(final int rejectingPN)
        throws IllegalArgumentException
    {
        for (int pn = 0; pn < maxPlayers; ++pn)
        {
            final SOCTradeOffer offer = players[pn].getCurrentOffer();
            if (offer != null)
                offer.clearWaitingReplyFrom(rejectingPN);
        }
    }

    /**
     * Can these two players currently trade?
     * If game option "NT" is set, players can trade only
     * with the bank/ports, not with other players.
     *
     * @return true if the two players can make the trade
     *         described in the offering players current offer
     *
     * @param offering  the number of the player making the offer
     * @param accepting the number of the player accepting the offer
     * @see #canMakeBankTrade(ResourceSet, ResourceSet)
     */
    public boolean canMakeTrade(final int offering, final int accepting)
    {
        D.ebugPrintlnINFO("*** canMakeTrade ***");
        D.ebugPrintlnINFO("*** offering = " + offering);
        D.ebugPrintlnINFO("*** accepting = " + accepting);

        if (gameState != PLAY1)
        {
            return false;
        }

        if (isGameOptionSet("NT"))
            return false;

        if (players[offering].getCurrentOffer() == null)
        {
            return false;
        }

        if ((currentPlayerNumber != offering) && (currentPlayerNumber != accepting))
        {
            return false;
        }

        SOCPlayer offeringPlayer = players[offering];
        SOCPlayer acceptingPlayer = players[accepting];
        SOCTradeOffer offer = offeringPlayer.getCurrentOffer();

        D.ebugPrintlnINFO("*** offer = " + offer);

        if ((offer.getGiveSet().getTotal() == 0) || (offer.getGetSet().getTotal() == 0))
        {
            return false;
        }

        D.ebugPrintlnINFO("*** offeringPlayer.getResources() = " + offeringPlayer.getResources());

        if (!(offeringPlayer.getResources().contains(offer.getGiveSet())))
        {
            return false;
        }

        D.ebugPrintlnINFO("*** acceptingPlayer.getResources() = " + acceptingPlayer.getResources());

        if (!(acceptingPlayer.getResources().contains(offer.getGetSet())))
        {
            return false;
        }

        return true;
    }

    /**
     * perform a trade between two players.
     * the trade performed is described in the offering player's
     * {@link SOCPlayer#getCurrentOffer()}.
     * Calls the two players' {@link SOCPlayer#makeTrade(ResourceSet, ResourceSet)}.
     *<P>
     * Assumes {@link #canMakeTrade(int, int)} already was called.
     * If game option "NT" is set, players can trade only
     * with the bank, not with other players.
     *
     * @param offering  the number of the player making the offer
     * @param accepting the number of the player accepting the offer
     * @see #makeBankTrade(SOCResourceSet, SOCResourceSet)
     * @see #rejectTradeOffersTo(int)
     */
    public void makeTrade(final int offering, final int accepting)
    {
        if (isGameOptionSet("NT"))
            return;

        SOCTradeOffer offer = players[offering].getCurrentOffer();
        final ResourceSet offeredToGive = offer.getGiveSet(), offeredToGet = offer.getGetSet();

        players[offering].makeTrade(offeredToGive, offeredToGet);
        players[accepting].makeTrade(offeredToGet, offeredToGive);

        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
    }

    /**
     * Can we undo this bank trade?
     * True only if the last action this turn was a bank trade with the same resources.
     *<P>
     * To undo the bank trade, call {@link #canMakeBankTrade(ResourceSet, ResourceSet)}
     * with give/get swapped from the original call, then call
     * {@link #makeBankTrade(SOCResourceSet, SOCResourceSet)} the same way.
     *
     * @param undo_gave  Undo giving these resources (get these back from the bank)
     * @param undo_got   Undo getting these resources (give these back to the bank)
     * @return  true if the current player can undo a bank trade of these resources
     * @since 1.1.13
     */
    public boolean canUndoBankTrade(ResourceSet undo_gave, ResourceSet undo_got)
    {
        if (! lastActionWasBankTrade)
            return false;

        final SOCPlayer currPlayer = players[currentPlayerNumber];
        return ((currPlayer.lastActionBankTrade_get != null)
                && currPlayer.lastActionBankTrade_get.equals(undo_got)
                && currPlayer.lastActionBankTrade_give.equals(undo_gave));
    }

    /**
     * Can current player make this bank/port trade?
     * Checks {@link SOCPlayer#getPortFlags()}.
     * Can trade multiple resource types at once,
     * as long as player has the appropriate ports if trying 3:1 or 2:1.
     *
     * @return true if the current player can make a
     *         particular bank/port trade
     *
     * @param  give  what the player will give to the bank
     * @param  get   what the player wants from the bank
     * @see #canUndoBankTrade(ResourceSet, ResourceSet)
     */
    public boolean canMakeBankTrade(ResourceSet give, ResourceSet get)
    {
        if (gameState != PLAY1)
            return false;

        if (lastActionWasBankTrade && canUndoBankTrade(get, give))
            return true;

        final SOCPlayer currPlayer = players[currentPlayerNumber];

        if ((give.getTotal() < 2) || (get.getTotal() == 0))
        {
            return false;
        }

        if (! currPlayer.getResources().contains(give))
        {
            return false;
        }

        int groupCount = 0;
        int ratio = give.getTotal() / get.getTotal();

        switch (ratio)
        {
        /**
         * bank trade
         */
        case 4:

            /**
             * check for groups of 4
             */
            for (int i = SOCResourceConstants.CLAY;
                    i <= SOCResourceConstants.WOOD; i++)
            {
                if ((give.getAmount(i) % 4) == 0)
                {
                    groupCount += (give.getAmount(i) / 4);
                }
                else
                {
                    return false;
                }
            }

            break;

        /**
         * 3:1 port trade
         */
        case 3:

            /**
             * check for groups of 3
             */
            for (int i = SOCResourceConstants.CLAY;
                    i <= SOCResourceConstants.WOOD; i++)
            {
                if ((give.getAmount(i) % 3) == 0)
                {
                    groupCount += (give.getAmount(i) / 3);

                    /**
                     * check if this player has a 3:1 port
                     */
                    if (! currPlayer.getPortFlag(SOCBoard.MISC_PORT))
                    {
                        return false;
                    }
                }
                else
                {
                    return false;
                }
            }

            break;

        /**
         * 2:1 port trade
         */
        case 2:

            /**
             * check for groups of 2
             */
            /**
             * Note: this only works if SOCResourceConstants.CLAY == 1
             */
            for (int i = SOCResourceConstants.CLAY;
                    i <= SOCResourceConstants.WOOD; i++)
            {
                final int giveAmt = give.getAmount(i);
                if (giveAmt > 0)
                {
                    if (((giveAmt % 2) == 0) && currPlayer.getPortFlag(i))
                    {
                        groupCount += (giveAmt / 2);
                    }
                    else
                    {
                        return false;
                    }
                }
            }

            break;
        }

        if (groupCount != get.getTotal())
        {
            return false;
        }

        return true;
    }

    /**
     * perform a bank trade, or undo the last bank trade.
     *<P>
     * This method does not validate against game rules, so call
     * {@link #canMakeBankTrade(ResourceSet, ResourceSet)} first.
     *<P>
     * Called only at server. Client instead updates {@link SOCPlayer#getResources()} contents.
     *<P>
     * Undo was added in version 1.1.13; if the player's previous action
     * this turn was a bank trade, it can be undone by calling
     * {@link #canUndoBankTrade(ResourceSet, ResourceSet)},
     * then calling {@link #makeBankTrade(SOCResourceSet, SOCResourceSet)} with
     * the give/get swapped.  This is the only time the resource count
     * of <tt>give</tt> is less than <tt>get</tt> here (for example,
     * give back 1 brick to get back 3 sheep).
     *
     * @param  give  what the player will give to the bank
     * @param  get   what the player wants from the bank
     */
    public void makeBankTrade(SOCResourceSet give, SOCResourceSet get)
    {
        final SOCPlayer currPlayer = players[currentPlayerNumber];

        if (lastActionWasBankTrade
            && (currPlayer.lastActionBankTrade_get != null)
            && currPlayer.lastActionBankTrade_get.equals(give)
            && currPlayer.lastActionBankTrade_give.equals(get))
        {
            // trade is undo
            lastActionWasBankTrade = false;
            currPlayer.lastActionBankTrade_give = null;
            currPlayer.lastActionBankTrade_get = null;
        } else {
            lastActionWasBankTrade = true;
            currPlayer.lastActionBankTrade_give = give;
            currPlayer.lastActionBankTrade_get = get;
        }

        currPlayer.makeBankTrade(give, get);
        lastActionTime = System.currentTimeMillis();
    }

    /**
     * @return true if the player has the resources, pieces, and
     *         room to build a road
     *
     * @param pn  the number of the player
     */
    public boolean couldBuildRoad(final int pn)
    {
        SOCResourceSet resources = players[pn].getResources();

        return ((resources.getAmount(SOCResourceConstants.CLAY) >= 1) && (resources.getAmount(SOCResourceConstants.WOOD) >= 1) && (players[pn].getNumPieces(SOCPlayingPiece.ROAD) >= 1) && (players[pn].hasPotentialRoad()));
    }

    /**
     * @return true if the player has the resources, pieces, and
     *         room to build a settlement
     *
     * @param pn  the number of the player
     * @see SOCPlayer#canPlaceSettlement(int)
     */
    public boolean couldBuildSettlement(final int pn)
    {
        SOCResourceSet resources = players[pn].getResources();

        return ((resources.getAmount(SOCResourceConstants.CLAY) >= 1) && (resources.getAmount(SOCResourceConstants.SHEEP) >= 1) && (resources.getAmount(SOCResourceConstants.WHEAT) >= 1) && (resources.getAmount(SOCResourceConstants.WOOD) >= 1) && (players[pn].getNumPieces(SOCPlayingPiece.SETTLEMENT) >= 1) && (players[pn].hasPotentialSettlement()));
    }

    /**
     * @return true if the player has the resources, pieces, and
     *         room to build a city
     *
     * @param pn  the number of the player
     */
    public boolean couldBuildCity(final int pn)
    {
        SOCResourceSet resources = players[pn].getResources();

        return ((resources.getAmount(SOCResourceConstants.ORE) >= 3) && (resources.getAmount(SOCResourceConstants.WHEAT) >= 2) && (players[pn].getNumPieces(SOCPlayingPiece.CITY) >= 1) && (players[pn].hasPotentialCity()));
    }

    /**
     * @return true if the player has the resources
     *         to buy a dev card, and if there are dev cards
     *         left to buy: {@link #getNumDevCards()} &gt; 0.
     *
     * @param pn  the number of the player
     * @see #buyDevCard()
     */
    public boolean couldBuyDevCard(final int pn)
    {
        SOCResourceSet resources = players[pn].getResources();

        return ((resources.getAmount(SOCResourceConstants.SHEEP) >= 1) && (resources.getAmount(SOCResourceConstants.ORE) >= 1) && (resources.getAmount(SOCResourceConstants.WHEAT) >= 1) && (numDevCards > 0));
    }

    /**
     * @return true if the player has the resources, pieces, and
     *         room to build a ship.  Always false if not {@link #hasSeaBoard}
     *         because players would have 0 ship pieces.
     *
     * @param pn  the number of the player
     * @since 2.0.00
     * @see #canPlaceShip(SOCPlayer, int)
     */
    public boolean couldBuildShip(final int pn)
    {
        SOCResourceSet resources = players[pn].getResources();

        return ((resources.getAmount(SOCResourceConstants.SHEEP) >= 1) && (resources.getAmount(SOCResourceConstants.WOOD) >= 1) && (players[pn].getNumPieces(SOCPlayingPiece.SHIP) >= 1) && (players[pn].hasPotentialShip()));
    }

    /**
     * a player is buying a road.
     * Assumes {@link #couldBuildRoad(int)} is true, does not check it here.
     *
     * @param pn  the number of the player
     * @see #putPiece(SOCPlayingPiece)
     * @see #cancelBuildRoad(int)
     */
    public void buyRoad(final int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.subtract(1, SOCResourceConstants.CLAY);
        resources.subtract(1, SOCResourceConstants.WOOD);
        oldGameState = gameState;  // PLAY1 or SPECIAL_BUILDING
        gameState = PLACING_ROAD;
    }

    /**
     * a player is buying a settlement.
     * Assumes {@link #couldBuildSettlement(int)} is true, does not check it here.
     *
     * @param pn  the number of the player
     * @see #putPiece(SOCPlayingPiece)
     * @see #cancelBuildSettlement(int)
     */
    public void buySettlement(final int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.subtract(1, SOCResourceConstants.CLAY);
        resources.subtract(1, SOCResourceConstants.SHEEP);
        resources.subtract(1, SOCResourceConstants.WHEAT);
        resources.subtract(1, SOCResourceConstants.WOOD);
        oldGameState = gameState;  // PLAY1 or SPECIAL_BUILDING
        gameState = PLACING_SETTLEMENT;
    }

    /**
     * a player is buying a city.
     * Assumes {@link #couldBuildCity(int)} is true, does not check it here.
     *
     * @param pn  the number of the player
     * @see #putPiece(SOCPlayingPiece)
     * @see #cancelBuildCity(int)
     */
    public void buyCity(final int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.subtract(3, SOCResourceConstants.ORE);
        resources.subtract(2, SOCResourceConstants.WHEAT);
        oldGameState = gameState;  // PLAY1 or SPECIAL_BUILDING
        gameState = PLACING_CITY;
    }

    /**
     * a player is buying a city.
     * Assumes {@link #couldBuildShip(int)} is true, does not check it here.
     *
     * @param pn  the number of the player
     * @see #putPiece(SOCPlayingPiece)
     * @see #cancelBuildShip(int)
     * @since 2.0.00
     */
    public void buyShip(final int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.subtract(1, SOCResourceConstants.SHEEP);
        resources.subtract(1, SOCResourceConstants.WOOD);
        oldGameState = gameState;  // PLAY1 or SPECIAL_BUILDING
        gameState = PLACING_SHIP;
    }

    /**
     * Can the current player cancel building a piece in this game state?
     * True for each piece's normal placing state ({@link #PLACING_ROAD}, etc),
     * and for initial settlement placement.
     * In v2.5.00+, also true in {@link #PLACING_FREE_ROAD1} to cancel playing Road Building dev card:
     * See {@link #doesCancelRoadBuildingReturnCard()}.
     * In v1.1.17+, also true in {@link #PLACING_FREE_ROAD2} to skip the second placement.
     *
     * @param buildType  Piece type ({@link SOCPlayingPiece#ROAD}, {@link SOCPlayingPiece#CITY CITY}, etc)
     * @return  true if current game state allows it
     * @see #cancelBuildRoad(int)
     * @see #cancelBuildShip(int)
     * @since 1.1.17
     */
    public boolean canCancelBuildPiece(final int buildType)
    {
        switch (buildType)
        {
        case SOCPlayingPiece.SETTLEMENT:
            return (gameState == PLACING_SETTLEMENT) || (gameState == START1B)
                || (gameState == START2B) || (gameState == START3B);

        case SOCPlayingPiece.ROAD:
            return (gameState == PLACING_ROAD)
                || (gameState == PLACING_FREE_ROAD1) || (gameState == PLACING_FREE_ROAD2);

        case SOCPlayingPiece.CITY:
            return (gameState == PLACING_CITY);

        case SOCPlayingPiece.SHIP:
            return (gameState == PLACING_SHIP)
                || (gameState == PLACING_FREE_ROAD1) || (gameState == PLACING_FREE_ROAD2);

        default:
            return false;
        }
    }

    /**
     * In game state {@link #PLACING_FREE_ROAD1} canceling the Road Building card
     * ({@link #cancelBuildRoad(int)} or {@link #cancelBuildShip(int)})
     * returns it to the player's inventory because they haven't built anything from it.
     * Also happens in {@link #PLACING_FREE_ROAD2} if card was played with 1 road or ship left.
     *
     * @return true if canceling the Road Building card will return it to the player's inventory
     * @since 2.5.00
     */
    public boolean doesCancelRoadBuildingReturnCard()
    {
        return ((gameState == PLACING_FREE_ROAD1)
            || ((gameState == PLACING_FREE_ROAD2) && playingRoadBuildingCardForLastRoad));
    }

    /**
     * a player is UNbuying a road; return resources, set gameState PLAY1
     * (or SPECIAL_BUILDING)
     *<P>
     * Assumes {@link #canCancelBuildPiece(int)} has been called already.
     *<P>
     * In version 1.1.17 and newer ({@link #VERSION_FOR_CANCEL_FREE_ROAD2}),
     * can also use to skip placing the second free road in {@link #PLACING_FREE_ROAD2};
     * sets gameState to ROLL_OR_CARD or PLAY1 as if the free road was placed.<BR>
     * In v1.2.01 and newer, player can also end their turn from state
     * {@link #PLACING_FREE_ROAD1} or {@link #PLACING_FREE_ROAD2}.<BR>
     * In v2.0.00 and newer, can similarly call {@link #cancelBuildShip(int)} in those states.
     *<P>
     * In v2.5.00 and newer ({@link #VERSION_FOR_CANCEL_FREE_ROAD1}),
     * in {@link #PLACING_FREE_ROAD1} the Road Building card is returned to player's inventory
     * because they haven't built anything from it.
     * Also happens in {@link #PLACING_FREE_ROAD2} if card was played with 1 road or ship left:
     * See {@link #doesCancelRoadBuildingReturnCard()}.
     *
     * @param pn  the number of the player
     * @return  true if resources were returned (false if {@link #PLACING_FREE_ROAD2})
     */
    public boolean cancelBuildRoad(final int pn)
    {
        final SOCPlayer player = players[currentPlayerNumber];

        if ((gameState == PLACING_FREE_ROAD1) || (gameState == PLACING_FREE_ROAD2))
        {
            if ((gameState == PLACING_FREE_ROAD1) || playingRoadBuildingCardForLastRoad)
            {
                player.getInventory().addDevCard(1, SOCInventory.OLD, SOCDevCardConstants.ROADS);
                player.setPlayedDevCard(false);
                player.updateDevCardsPlayed(SOCDevCardConstants.ROADS, true);
                gameState = PLACING_FREE_ROAD2;
            }

            advanceTurnStateAfterPutPiece();
            return false;  // <--- Special case: Not returning resources ---
        }

        player.getResources().add(SOCRoad.COST);
        if (oldGameState != SPECIAL_BUILDING)
            gameState = PLAY1;
        else
            gameState = SPECIAL_BUILDING;

        return true;
    }

    /**
     * a player is UNbuying a settlement; return resources, set gameState PLAY1
     * (or SPECIAL_BUILDING)
     *<P>
     * To cancel an initial settlement, call {@link #undoPutInitSettlement(SOCPlayingPiece)} instead.
     *
     * @param pn  the number of the player
     */
    public void cancelBuildSettlement(int pn)
    {
        players[pn].getResources().add(SOCSettlement.COST);
        if (oldGameState != SPECIAL_BUILDING)
            gameState = PLAY1;
        else
            gameState = SPECIAL_BUILDING;
    }

    /**
     * a player is UNbuying a city; return resources, set gameState PLAY1
     * (or SPECIAL_BUILDING)
     *
     * @param pn  the number of the player
     */
    public void cancelBuildCity(final int pn)
    {
        players[pn].getResources().add(SOCCity.COST);
        if (oldGameState != SPECIAL_BUILDING)
            gameState = PLAY1;
        else
            gameState = SPECIAL_BUILDING;
    }

    /**
     * a player is UNbuying a ship; return resources, set gameState PLAY1
     * (or SPECIAL_BUILDING)
     *<P>
     * Assumes {@link #canCancelBuildPiece(int)} has been called already.
     *<P>
     * Can also use to skip placing the second free ship in {@link #PLACING_FREE_ROAD2},
     * or cancel playing the Road Building card in {@link #PLACING_FREE_ROAD1};
     * sets gameState to ROLL_OR_CARD or PLAY1 as if the free ship was placed.
     * Can similarly call {@link #cancelBuildRoad(int)} in those states.
     *<P>
     * In v2.5.00 and newer ({@link #VERSION_FOR_CANCEL_FREE_ROAD1}),
     * in {@link #PLACING_FREE_ROAD1} the Road Building card is returned to player's inventory
     * because they haven't built anything from it.
     * Also happens in {@link #PLACING_FREE_ROAD2} if card was played with 1 road or ship left:
     * see {@link #doesCancelRoadBuildingReturnCard()}.
     *
     * @param pn  the number of the player
     * @return  true if resources were returned (false if {@link #PLACING_FREE_ROAD2})
     * @since 2.0.00
     */
    public boolean cancelBuildShip(final int pn)
    {
        final SOCPlayer player = players[currentPlayerNumber];

        if ((gameState == PLACING_FREE_ROAD1) || (gameState == PLACING_FREE_ROAD2))
        {
            if ((gameState == PLACING_FREE_ROAD1) || playingRoadBuildingCardForLastRoad)
            {
                player.getInventory().addDevCard(1, SOCInventory.OLD, SOCDevCardConstants.ROADS);
                player.setPlayedDevCard(false);
                player.updateDevCardsPlayed(SOCDevCardConstants.ROADS, true);
                gameState = PLACING_FREE_ROAD2;
            }

            advanceTurnStateAfterPutPiece();
            return false;  // <--- Special case: Not returning resources ---
        }

        player.getResources().add(SOCShip.COST);
        if (oldGameState != SPECIAL_BUILDING)
            gameState = PLAY1;
        else
            gameState = SPECIAL_BUILDING;
        return true;
    }

    /**
     * In state {@link #PLACING_INV_ITEM}, the current player is canceling special {@link SOCInventoryItem} placement.
     * Return it to their hand, and set gameState back to {@link #PLAY1} or {@link #SPECIAL_BUILDING}
     * based on oldGameState.
     *<P>
     * If ! {@link SOCInventoryItem#canCancelPlay}, does nothing unless {@code forceEndTurn}.
     * If {@code placingItem} is {@code null}, sets game state to {@code oldGameState} and returns {@code null}.
     *
     * @param forceEndTurn  If true, player's turn is being ended.  Return item to inventory even if ! {@code item.canCancelPlay}.
     * @return  The item that was being placed, or {@code null} if none or if placement can't be canceled for this type
     * @since 2.0.00
     */
    public SOCInventoryItem cancelPlaceInventoryItem(final boolean forceEndTurn)
    {
        if ((placingItem != null) && ! (forceEndTurn || placingItem.canCancelPlay))
            return null;  // not cancelable

        gameState = oldGameState;

        if (placingItem != null)
        {
            final SOCInventoryItem itemCard = placingItem;
            placingItem = null;
            players[currentPlayerNumber].getInventory().addItem(itemCard);
            return itemCard;
        } else {
            return null;
        }
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}, is this ship upgraded to a warship?
     * Counts down {@code sh}'s player's {@link SOCPlayer#getNumWarships()}
     * while it looks for {@code sh} among the ships from {@link SOCPlayer#getRoadsAndShips()},
     * which is in the same chronological order as warship conversions.
     * (Those are the ships heading out to sea starting at the player's coastal settlement.)
     *
     * @param sh  A ship whose player is in this game
     * @return  True if {@link SOCPlayer#getRoadsAndShips()} contains, among its
     *          first {@link SOCPlayer#getNumWarships()} ships, a ship
     *          located at {@link SOCShip#getCoordinates() sh.getCoordinates()}.
     * @see #playKnight()
     * @since 2.0.00
     */
    public boolean isShipWarship(final SOCShip sh)
    {
        final int node = sh.getCoordinates();
        final SOCPlayer pl = sh.getPlayer();

        // Count down the player's warships to see if sh is among them.
        // This works since warships in getRoads() begin with player's 1st-placed ship.

        int numWarships = pl.getNumWarships();
        if (0 == numWarships)
            return false;  // <--- early return: no warships ---

        for (SOCRoutePiece rship : pl.getRoadsAndShips())
        {
            if (! (rship instanceof SOCShip))
                continue;

            final boolean isWarship = (numWarships > 0);
            if (node == rship.getCoordinates())
            {
                return isWarship;  // <--- early return: coordinates match ---
            }

            if (isWarship)
            {
                --numWarships;

                if (0 == numWarships)
                    return false;  // <--- early return: no more warships ---
            }
        }

        return false;
    }

    /**
     * For debugging at server, set card type of the next development card for {@link #buyDevCard()}
     * To maintain the ratio of card types, will try to find a card of this type within the deck
     * and swap it with the next-to-play card. If none found, will change the type of the next card
     * in the deck.
     *
     * @param cardType  Dev card type from {@link SOCDevCardConstants}; not validated.
     * @throws IllegalStateException if deck is empty or game hasn't started yet;
     *     check {@link #getNumDevCards()} before calling.
     * @since 2.5.00
     */
    public void setNextDevCard(final int cardType)
        throws IllegalStateException
    {
        final int nextCardIdx = numDevCards - 1;
        if ((nextCardIdx < 0) || (devCardDeck == null))
            throw new IllegalStateException("empty");

        if (cardType == devCardDeck[nextCardIdx])
            return;  // already is the next type

        for (int i = nextCardIdx - 1; i >= 0; --i)
        {
            if (cardType == devCardDeck[i])
            {
                final int otherType = devCardDeck[nextCardIdx];
                devCardDeck[nextCardIdx] = cardType;
                devCardDeck[i] = otherType;
                return;
            }
        }

        devCardDeck[nextCardIdx] = cardType;
    }

    /**
     * the current player is buying a dev card.
     *<P>
     *<b>Note:</b> Not checked for validity; please call {@link #couldBuyDevCard(int)} first.
     *<P>
     * Called at server only.
     *<P>
     * If called while the game is starting, when {@link #getCurrentPlayerNumber()} == -1,
     * removes and returns a dev card from the deck without giving it to any player.
     *
     * @return the card that was drawn; a dev card type from {@link SOCDevCardConstants}.
     * @see #playDiscovery()
     * @see #playKnight()
     * @see #playMonopoly()
     * @see #playRoadBuilding()
     * @see #getNumDevCards()
     * @see #setNextDevCard(int)
     */
    public int buyDevCard()
    {
        numDevCards--;
        final int card = devCardDeck[numDevCards];

        if (currentPlayerNumber != -1)
        {
            SOCResourceSet resources = players[currentPlayerNumber].getResources();
            resources.subtract(1, SOCResourceConstants.ORE);
            resources.subtract(1, SOCResourceConstants.SHEEP);
            resources.subtract(1, SOCResourceConstants.WHEAT);
            players[currentPlayerNumber].getInventory().addDevCard(1, SOCInventory.NEW, card);
            lastActionTime = System.currentTimeMillis();
            lastActionWasBankTrade = false;

            checkForWinner();
        }

        return (card);
    }

    /**
     * Can this player currently play a knight card?
     * gameState must be {@link #ROLL_OR_CARD} or {@link #PLAY1}.
     * Must have a {@link SOCDevCardConstants#KNIGHT} and must
     * not have already played a dev card this turn.
     *<P>
     * In <b>game scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}</b>,
     * can this player currently play a Warship (Knight) card to
     * convert one of their ships into a warship?
     * Conditions in first paragraph must be true, and player's
     * {@link SOCPlayer#getRoadsAndShips()} must contain more ships
     * than {@link SOCPlayer#getNumWarships()}. That scenario has
     * no robber on the board, only the pirate fleet.
     *
     * @param pn  the number of the player
     * @return true if the player can play a knight card
     */
    public boolean canPlayKnight(final int pn)
    {
        if (! ((gameState == ROLL_OR_CARD) || (gameState == PLAY1)))
        {
            return false;
        }

        if (players[pn].hasPlayedDevCard())
        {
            return false;
        }

        if (! players[pn].getInventory().hasPlayable(SOCDevCardConstants.KNIGHT))
        {
            return false;
        }

        if (! isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
            return true;

        // Check if the player has any ship to convert to a warship
        final Vector<SOCRoutePiece> roadsShips = players[pn].getRoadsAndShips();
        int numShip = 0;
        for (SOCRoutePiece rs : roadsShips)
            if (rs instanceof SOCShip)
                ++numShip;

        return (numShip - players[pn].getNumWarships()) > 0;
    }

    /**
     * Can the current player play a Road Building card?
     *<P>
     * This card directs the player to place 2 roads.
     * Checks of game rules online show they "MAY" or "CAN", not "MUST", place 2.
     * If they have 2 or more roads, place 2.
     * If they have just 1 road, place 1.
     * If they have 0 roads, cannot play the card.
     *
     * @return true if the player can play a Road Building card.
     *
     * @param pn  the number of the player
     * @see #playRoadBuilding()
     */
    public boolean canPlayRoadBuilding(final int pn)
    {
        if (! ((gameState == ROLL_OR_CARD) || (gameState == PLAY1)))
        {
            return false;
        }

        final SOCPlayer player = players[pn];

        if (player.hasPlayedDevCard())
        {
            return false;
        }

        if (! player.getInventory().hasPlayable(SOCDevCardConstants.ROADS))
        {
            return false;
        }

        if ((player.getNumPieces(SOCPlayingPiece.ROAD) < 1)
             && (player.getNumPieces(SOCPlayingPiece.SHIP) < 1))
        {
            return false;
        }

        return true;
    }

    /**
     * return true if the player can play a Discovery card
     *
     * @param pn  the number of the player
     */
    public boolean canPlayDiscovery(final int pn)
    {
        if (! ((gameState == ROLL_OR_CARD) || (gameState == PLAY1)))
        {
            return false;
        }

        if (players[pn].hasPlayedDevCard())
        {
            return false;
        }

        if (! players[pn].getInventory().hasPlayable(SOCDevCardConstants.DISC))
        {
            return false;
        }

        return true;
    }

    /**
     * return true if the player can play a Monopoly card
     *
     * @param pn  the number of the player
     */
    public boolean canPlayMonopoly(final int pn)
    {
        if (! ((gameState == ROLL_OR_CARD) || (gameState == PLAY1)))
        {
            return false;
        }

        if (players[pn].hasPlayedDevCard())
        {
            return false;
        }

        if (! players[pn].getInventory().hasPlayable(SOCDevCardConstants.MONO))
        {
            return false;
        }

        return true;
    }

    /**
     * Can this player play this special {@link SOCInventoryItem} now? Checks the game state, scenario options, and
     * player's inventory. Returns 0 if OK to play, or the reason why they can't play it right now.
     *<P>
     * Assumes server or client player is calling, and thus player has no unknown dev cards (itype == 0).
     * If calling at client for another player, and the scenario allows special items of type 0, these
     * currently can't be distinguished from {@link SOCDevCardConstants#UNKNOWN} dev cards.
     *
     * <H5>Recognized scenario game options with special items:</H5>
     *<UL>
     * <LI> {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}: Trade ports received as gifts.
     *   Playable in state {@link #PLAY1} or {@link #SPECIAL_BUILDING} and only when player has a
     *   coastal settlement/city with no adjacent existing port.  Returns 4 if
     *   {@link SOCPlayer#getPortMovePotentialLocations(boolean)} == {@code null}.
     *   (See "returns" section below for the standard other return codes.)
     *   Because the rules require building a coastal settlement/city before placing
     *   the new port, which is done during {@link #PLAY1} or {@link #SPECIAL_BUILDING}, the port can't be placed
     *   before the dice roll or in other game states.
     *</UL>
     *
     * @param pn  Player number
     * @param itype  Inventory item type, from {@link SOCInventoryItem#itype}
     * @return  the results of the check:
     *   <UL>
     *     <LI> 0 if can be played now
     *     <LI> 1 if no playable {@code itype} in player's {@link SOCInventory}
     *     <LI> 2 if game doesn't have a scenario option that permits special items to be played
     *     <LI> 3 if cannot be played right now (game state, current player, etc)
     *     <LI> or any other nonzero scenario-specific detail code for why the item can't be played now
     *   </UL>
     * @since 2.0.00
     * @see #playInventoryItem(int)
     */
    public int canPlayInventoryItem(final int pn, final int itype)
    {
        if (! players[pn].getInventory().hasPlayable(itype))
            return 1;

        if (isGameOptionSet(SOCGameOptionSet.K_SC_FTRI))
        {
            if ((pn != currentPlayerNumber) || ((gameState != PLAY1) && (gameState != SPECIAL_BUILDING)))
                return 3;

            if (null == players[pn].getPortMovePotentialLocations(false))
                return 4;

            return 0;
        }

        // Fallback:
        return 2;
    }

    /**
     * Play a special {@link SOCInventoryItem} for the current player.  The current game must have a
     * scenario {@link SOCGameOption} that uses the inventory item.  Assumes {@link #canPlayInventoryItem(int, int)}
     * has been called to check game options and state, player inventory, etc.
     *
     * <H5>Recognized scenario game options with special items:</H5>
     *<UL>
     * <LI> {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}: Trade ports received as gifts.
     *   Game state becomes {@link #PLACING_INV_ITEM}.  Player must now choose and validate a location
     *   and then call {@link #placePort(int)}.
     *</UL>
     *
     * @param  itype  Special item type, from {@link SOCInventoryItem#itype}
     * @return  The item played, or {@code null} if not found playable in current player's inventory
     *     or not recognized in current game. {@link #canPlayInventoryItem(int, int)} checks those things,
     *     so if you've called that, you shouldn't get {@code null} returned from here.
     */
    public SOCInventoryItem playInventoryItem(final int itype)
    {
        SOCInventoryItem item = players[currentPlayerNumber].getInventory().removeItem(SOCInventory.PLAYABLE, itype);
        if (item == null)
            return null;

        if (isGameOptionSet(SOCGameOptionSet.K_SC_FTRI))
        {
            // Player can place a trade port somewhere on the board
            placingItem = item;
            oldGameState = (gameState == SPECIAL_BUILDING) ? SPECIAL_BUILDING : PLAY1;
            gameState = PLACING_INV_ITEM;
            return item;
        }

        // Fallback:
        return null;
    }

    /**
     * The current player plays a Knight card.
     * Assumes {@link #canPlayKnight(int)} already called, and the play is allowed.
     * gameState becomes either {@link #PLACING_ROBBER}
     * or {@link #WAITING_FOR_ROBBER_OR_PIRATE}.
     *<P>
     * See note at {@link #moveRobber(int, int)} or {@link #movePirate(int, int)}
     * about when Largest Army can win the game.
     *<P>
     * <b>In scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI},</b> instead the player
     * converts a normal ship to a warship.  There is no robber piece in this scenario.
     * Call {@link SOCPlayer#getNumWarships()} afterwards to get the current player's new count.
     * Ships are converted in the chronological order they're placed, out to sea from the
     * player's coastal settlement; see {@link #isShipWarship(SOCShip)}.
     *
     * @see SOCPlayer#getDevCardsPlayed()
     */
    public void playKnight()
    {
        final boolean isWarshipConvert = isGameOptionSet(SOCGameOptionSet.K_SC_PIRI);
        final SOCPlayer pl = players[currentPlayerNumber];

        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
        pl.setPlayedDevCard(true);
        pl.updateDevCardsPlayed(SOCDevCardConstants.KNIGHT, false);
        pl.getInventory().removeDevCard(SOCInventory.OLD, SOCDevCardConstants.KNIGHT);

        if (! isWarshipConvert)
        {
            pl.incrementNumKnights();
            updateLargestArmy();
            checkForWinner();

            // next-state logic is similar to discard and rollDice_update7gameState;
            // if you update this method, check those ones

            placingRobberForKnightCard = true;
            oldGameState = gameState;
            if (canChooseMovePirate())
            {
                gameState = WAITING_FOR_ROBBER_OR_PIRATE;
            } else {
                robberyWithPirateNotRobber = false;
                gameState = PLACING_ROBBER;
            }
        } else {
            pl.setNumWarships(1 + pl.getNumWarships());
        }
    }

    /**
     * The current player plays a Road Building card.
     * This card directs the player to place 2 roads or ships,
     * or 1 road and 1 ship.
     * If they have 2 or more roads or ships, may place 2; gameState becomes PLACING_FREE_ROAD1.
     * If they have just 1 road/ship, may place that; gameState becomes PLACING_FREE_ROAD2.
     * (Checks of game rules online show "MAY" or "CAN", not "MUST" place 2.)
     * If they have 0 roads, cannot play the card.
     *<P>
     * Game state becomes {@link #PLACING_FREE_ROAD1}, or {@link #PLACING_FREE_ROAD2} if player has only 1 road piece.
     *<P>
     * Assumes {@link #canPlayRoadBuilding(int)} has already been called, and move is valid.
     * The card can be played before or after rolling the dice.
     * Doesn't set <tt>oldGameState</tt>, because after placing the road, we might need that field.
     *<P>
     * Called only at server; client is instead sent messages with effects of playing the card.
     *
     * @see SOCPlayer#getDevCardsPlayed()
     */
    public void playRoadBuilding()
    {
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
        final SOCPlayer player = players[currentPlayerNumber];
        player.setPlayedDevCard(true);
        player.getInventory().removeDevCard(SOCInventory.OLD, SOCDevCardConstants.ROADS);
        player.updateDevCardsPlayed(SOCDevCardConstants.ROADS, false);

        final int roadShipCount = player.getNumPieces(SOCPlayingPiece.ROAD)
            + player.getNumPieces(SOCPlayingPiece.SHIP);
        playingRoadBuildingCardForLastRoad = (roadShipCount <= 1);
        if (! playingRoadBuildingCardForLastRoad)
        {
            gameState = PLACING_FREE_ROAD1;  // First of 2 free roads / ships
        } else {
            gameState = PLACING_FREE_ROAD2;  // "Second", just 1 free road or ship
        }
    }

    /**
     * the current player plays a Discovery card.
     * Assumes {@link #canPlayDiscovery(int)} has already been called, and move is valid.
     *<P>
     * Game state becomes {@link #WAITING_FOR_DISCOVERY}.
     *<P>
     * Called only at server; client is instead sent messages with effects of playing the card.
     *
     * @see SOCPlayer#getDevCardsPlayed()
     */
    public void playDiscovery()
    {
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
        final SOCPlayer pl = players[currentPlayerNumber];
        pl.setPlayedDevCard(true);
        pl.getInventory().removeDevCard(SOCInventory.OLD, SOCDevCardConstants.DISC);
        pl.updateDevCardsPlayed(SOCDevCardConstants.DISC, false);
        oldGameState = gameState;
        gameState = WAITING_FOR_DISCOVERY;
    }

    /**
     * the current player plays a Monopoly card.
     * Assumes {@link #canPlayMonopoly(int)} has already been called, and move is valid.
     *<P>
     * Game state becomes {@link #WAITING_FOR_MONOPOLY}.
     *<P>
     * Called only at server; client is instead sent messages with effects of playing the card.
     *
     * @see SOCPlayer#getDevCardsPlayed()
     */
    public void playMonopoly()
    {
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
        final SOCPlayer pl = players[currentPlayerNumber];
        pl.setPlayedDevCard(true);
        pl.getInventory().removeDevCard(SOCInventory.OLD, SOCDevCardConstants.MONO);
        pl.updateDevCardsPlayed(SOCDevCardConstants.MONO, false);
        oldGameState = gameState;
        gameState = WAITING_FOR_MONOPOLY;
    }

    /**
     * @return true if the current player can
     *         do the discovery card action
     *         (state is {@link #WAITING_FOR_DISCOVERY})
     *         and the pick contains exactly 2 resources
     *
     * @param pick  the resources that the player wants
     */
    public boolean canDoDiscoveryAction(ResourceSet pick)
    {
        if (gameState != WAITING_FOR_DISCOVERY)
        {
            return false;
        }

        if (pick.getTotal() != 2)
        {
            return false;
        }

        return true;
    }

    /**
     * @return true if the current player can do
     *         the Monopoly card action
     */
    public boolean canDoMonopolyAction()
    {
        if (gameState != WAITING_FOR_MONOPOLY)
            return false;
        else
            return true;
    }

    /**
     * perform the Discovery card action
     *
     * @param pick  what the player picked
     */
    public void doDiscoveryAction(SOCResourceSet pick)
    {
        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            players[currentPlayerNumber].getResources().add(pick.getAmount(i), i);
        }

        gameState = oldGameState;
    }

    /**
     * perform the Monopoly card action.
     * Resources are taken from players that have it.
     * Game state becomes oldGameState (returns to state before monopoly pick).
     *
     * @param rtype  the type of resource to monopolize
     * @return array (1 elem per player) of resource count taken from
     *        each player. 0 for players with nothing taken.
     *        0 for the current player (playing the monopoly card).
     */
    public int[] doMonopolyAction(final int rtype)
    {
        int sum = 0;
        int[] monoResult = new int[maxPlayers];

        for (int i = 0; i < maxPlayers; i++)
        {
            if ((i != currentPlayerNumber) && ! isSeatVacant(i))
            {
                int playerHas = players[i].getResources().getAmount(rtype);
                if (playerHas > 0)
                {
                    sum += playerHas;
                    players[i].getResources().setAmount(0, rtype);
                }
                monoResult[i] = playerHas;
            } else {
                monoResult[i] = 0;
            }
        }

        players[currentPlayerNumber].getResources().add(sum, rtype);
        gameState = oldGameState;
        return monoResult;
    }

    /**
     * update which player has the largest army
     * larger than 2
     *<P>
     * For consistency, recalculates this field only if {@link #isAtServer}; does nothing at client.
     * Before version 2.4.00 ({@link #VERSION_FOR_LONGEST_LARGEST_FROM_SERVER}),
     * Largest Army was calculated at both client and server.
     */
    public void updateLargestArmy()
    {
        if (! (isAtServer || (serverVersion < VERSION_FOR_LONGEST_LARGEST_FROM_SERVER)))
            return;  // <--- for consistency, client shouldn't calc this independently of server ---

        int size =
            (playerWithLargestArmy == -1)
            ? 2
            : players[playerWithLargestArmy].getNumKnights();

        for (int i = 0; i < maxPlayers; i++)
        {
            if (players[i].getNumKnights() > size)
                playerWithLargestArmy = i;
        }
    }

    /**
     * Save the state of who has largest army.
     * This is a field, not a stack, so do not call twice
     * unless you call {@link #restoreLargestArmyState()} between them.
     */
    public void saveLargestArmyState()
    {
        oldPlayerWithLargestArmy = playerWithLargestArmy;
    }

    /**
     * Restore the state of who had largest army.
     * This is a field, not a stack, so do not call twice
     * unless you call {@link #saveLargestArmyState()} between them.
     */
    public void restoreLargestArmyState()
    {
        playerWithLargestArmy = oldPlayerWithLargestArmy;
    }

    /**
     * update which player has longest road longer
     * than 4.
     *<P>
     * this version recalculates the longest road only for
     * the player who is affected by the most recently
     * placed piece, by calling their {@link SOCPlayer#calcLongestRoad2()}.
     * Assumes all other players' longest road has been updated already.
     * All players' {@link SOCPlayer#getLongestRoadLength()} is called here.
     *<P>
     * For consistency, recalculates {@link #getPlayerWithLongestRoad()} only if {@link #isAtServer};
     * client only calls each player's {@code calcLongestRoad2}.
     * Before version 2.4.00 ({@link #VERSION_FOR_LONGEST_LARGEST_FROM_SERVER}),
     * Longest Road was fully calculated at both client and server.
     *<P>
     * if there is a tie, the last player to have LR keeps it.
     * if two or more players are tied for LR and none of them
     * used to have LR, then no one has LR.
     *<P>
     * If {@link SOCGameOptionSet#K_SC_0RVP} is set, does nothing.
     *
     * @param pn  the number of the player who is affected
     */
    public void updateLongestRoad(final int pn)
    {
        if (isGameOptionSet(SOCGameOptionSet.K_SC_0RVP))
            return;  // <--- No longest road ---

        //D.ebugPrintln("## updateLongestRoad("+pn+")");
        int longestLength;
        int playerLength;
        int tmpPlayerWithLR = -1;

        players[pn].calcLongestRoad2();

        if (! (isAtServer || (serverVersion < VERSION_FOR_LONGEST_LARGEST_FROM_SERVER)))
            return;  // <--- for consistency, client shouldn't calc this independently of server ---

        longestLength = 0;

        for (int i = 0; i < maxPlayers; i++)
        {
            playerLength = players[i].getLongestRoadLength();

            //D.ebugPrintln("----- LR length for player "+i+" is "+playerLength);
            if (playerLength > longestLength)
            {
                longestLength = playerLength;
                tmpPlayerWithLR = i;
            }
        }

        if (longestLength < 5)  // Minimum length is 5 for the bonus
        {
            playerWithLongestRoad = -1;
        }
        else
        {
            ///
            /// if there is a tie, the last player to have LR keeps it.
            /// if two or more players are tied for LR and none of them
            /// of them used to have LR, then no one has LR.
            ///
            int playersWithLR = 0;

            for (int i = 0; i < maxPlayers; i++)
                if (players[i].getLongestRoadLength() == longestLength)
                    playersWithLR++;

            if (playersWithLR == 1)
            {
                playerWithLongestRoad = tmpPlayerWithLR;
            }
            else if ((playerWithLongestRoad == -1) || (players[playerWithLongestRoad].getLongestRoadLength() != longestLength))
            {
                playerWithLongestRoad = -1;
            }
        }

        //D.ebugPrintln("----- player "+playerWithLongestRoad+" has LR");
    }

    /**
     * check current player's vp total to see if the
     * game is over.  If so, set game state to {@link #OVER},
     * set player with win.
     *<P>
     * This method is called from other game methods which may award VPs or may cause a win,
     * such as {@link #buyDevCard()} and {@link #putPiece(SOCPlayingPiece)}, and at the start
     * of each turn by {@link #endTurn()} under some conditions (see that method).
     *<P>
     * Per rules FAQ, a player can win only during their own turn.
     * If a player reaches winning points ({@link #vp_winner} or more) but it's
     * not their turn, there is not yet a winner. This could happen if,
     * for example, the longest road is broken by a new settlement, and
     * the next-longest road is not the current player's road.
     *<P>
     * The win is determined not by who has the highest point total, but
     * solely by reaching enough victory points ({@link #vp_winner}) during your own turn.
     * Does nothing if gameState is {@link #SPECIAL_BUILDING} or {@link #LOADING}.
     *<P>
     * Some game scenarios have other special win conditions ({@link #hasScenarioWinCondition}):
     *<UL>
     *<LI> Scenario {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI} will end the game if
     *     fewer than 4 of the {@link SOCVillage}s have cloth remaining.  The player
     *     with the most VP wins; if tied, the tied player with the most cloth wins.
     *     Winner is not necessarily the current player.
     *     <BR>
     *     See {@link SOCGameEvent#SGE_CLVI_WIN_VILLAGE_CLOTH_EMPTY}.
     *<LI> Scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI} requires the player to
     *     defeat and recapture 'their' pirate fortress to win.
     *     To win, they also must earn {@link SOCGame#vp_winner}.
     *     <BR>
     *     See {@link SOCPlayerEvent#PIRI_FORTRESS_RECAPTURED} and
     *     {@link SOCGameEvent#SGE_PIRI_LAST_FORTRESS_FLEET_DEFEATED}.
     *<LI> Scenario {@link SOCGameOptionSet#K_SC_WOND _SC_WOND} requires the player to
     *     build a Wonder to a higher level than any other player's Wonder; the player
     *     can win even without reaching {@link #vp_winner} VP.
     *     <BR>
     *     Reaching this condition does not fire a {@link SOCGameEvent} or {@link SOCPlayerEvent}.
     *</UL>
     *
     * @see #getGameState()
     * @see #getPlayerWithWin()
     */
    public void checkForWinner()
    {
        if ((gameState == SPECIAL_BUILDING) || (gameState == LOADING))
            return;  // Can't win in this state, it's not really anyone's turn

        final int pn = currentPlayerNumber;
        if ((pn < 0) || (pn >= maxPlayers))
            return;

        if ((players[pn].getTotalVP() >= vp_winner))
        {
            if (hasScenarioWinCondition)
            {
                if (isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
                    if (null != players[pn].getFortress())
                        return;  // <--- can't win without defeating pirate fortress ---

                if (isGameOptionSet(SOCGameOptionSet.K_SC_WOND))
                {
                    final SOCSpecialItem plWond = players[pn].getSpecialItem(SOCGameOptionSet.K_SC_WOND, 0);
                    if (plWond == null)
                        return;  // <--- can't win without starting to build a Wonder ---

                    // Check other players' levels
                    for (int p = 0; p < maxPlayers; ++p)
                    {
                        if (p == pn)
                            continue;

                        final SOCSpecialItem pWond = players[p].getSpecialItem(SOCGameOptionSet.K_SC_WOND, 0);
                        if ((pWond != null) && (pWond.getLevel() >= plWond.getLevel()))
                        {
                            return;  // <--- another player has same or higher Wonder level ---
                        }
                    }
                }
            }

            setGameStateOVER();
            playerWithWin = pn;

            return;
        }

        if (! hasScenarioWinCondition)
            return;

        // _SC_CLVI: Check if less than half the villages have cloth remaining
        if (isGameOptionSet(SOCGameOptionSet.K_SC_CLVI))
        {
            if (checkForWinner_SC_CLVI())
            {
                // gameState is OVER
                currentPlayerNumber = playerWithWin;  // don't call setCurrentPlayerNumber, would recurse here
                if (gameEventListener != null)
                    gameEventListener.gameEvent
                        (this, SOCGameEvent.SGE_CLVI_WIN_VILLAGE_CLOTH_EMPTY, players[playerWithWin]);
            }
        }

        // _SC_WOND: Check if the current player's built all 4 levels of their Wonder
        if (isGameOptionSet(SOCGameOptionSet.K_SC_WOND))
        {
            final SOCSpecialItem plWond = players[pn].getSpecialItem(SOCGameOptionSet.K_SC_WOND, 0);
            if ((plWond != null) && (plWond.getLevel() >= SOCSpecialItem.SC_WOND_WIN_LEVEL))
            {
                setGameStateOVER();
                playerWithWin = pn;
            }
        }

    }

    /**
     * Check how many villages have cloth remaining, in scenario {@link SOCScenario#K_SC_CLVI SC_CLVI}.
     * Called by {@link #checkForWinner()}. Game ends immediately if fewer than 4 villages still
     * have cloth ({@link SOCScenario#SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN}).
     * Otherwise, returns false without changing game state.
     *<P>
     * Sets state to {@link #OVER}. Returns true.
     * Caller should fire {@link SOCGameEvent#SGE_CLVI_WIN_VILLAGE_CLOTH_EMPTY}.
     *
     * @return true if game has ended with a winner under this special condition
     * @since 2.0.00
     */
    private final boolean checkForWinner_SC_CLVI()
    {
        int nv = 0;
        final HashMap<Integer, SOCVillage> allv = ((SOCBoardLarge) board).getVillages();
        for (SOCVillage v : allv.values())
        {
            if (v.getCloth() > 0)
            {
                ++nv;
                if (nv >= SOCScenario.SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN)
                    return false;
            }
        }

        setGameStateOVER();

        // find player with most VP, or most cloth if tied
        int p = -1;
        int numWithMax = 0;
        int maxVP = 0;
        for (int pn = 0; pn < maxPlayers; ++pn)
        {
            if (isSeatVacant(pn))
                continue;
            final int vp = players[pn].getTotalVP();
            if (vp > maxVP)
            {
                maxVP = vp;
                numWithMax = 1;
                p = pn;
            }
            else if (vp == maxVP)
            {
                ++numWithMax;  // tie
            }
        }

        if (numWithMax == 1)
        {
            playerWithWin = p;
            return true;
        }

        // VP tied: Check cloth
        p = -1;
        numWithMax = 0;
        int maxCl = 0;
        for (int pn = 0; pn < maxPlayers; ++pn)
        {
            if (isSeatVacant(pn) || (players[pn].getTotalVP() < maxVP))
                continue;
            final int cl = players[pn].getCloth();
            if (cl > maxCl)
            {
                maxCl = cl;
                numWithMax = 1;
                p = pn;
            }
            else if (cl == maxCl)
            {
                ++numWithMax;
            }
        }

        if (numWithMax == 1)
        {
            playerWithWin = p;
            return true;
        }

        // Cloth amount also tied:
        // Give current player the win if possible.
        // Otherwise, find the next tied player in order.
        do
        {
            if ((players[currentPlayerNumber].getTotalVP() == maxVP)
                && (players[currentPlayerNumber].getCloth() == maxCl))
            {
                playerWithWin = p;
                break;
            }
        } while (advanceTurn());
        return true;
    }

    /**
     * set vars to null so gc can clean up
     */
    public void destroyGame()
    {
        for (int i = 0; i < maxPlayers; i++)
        {
            if (players[i] != null)
            {
                players[i].destroyPlayer();
                players[i] = null;
            }
        }

        Arrays.fill(players, null);
        board = null;
        rand = null;
        pendingMessagesOut = null;
    }

    /**
     * Create a new game with same players and name, new board;
     * like calling constructor otherwise.
     * State of current game can be any state. State of copy is {@link #NEW},
     * or if there are robots, it will be set to {@link #READY_RESET_WAIT_ROBOT_DISMISS}
     * by the {@link SOCGameBoardReset} constructor.
     * Deep copy: Player names, faceIDs, and robot-flag are copied from
     * old game, but all other fields set as new Player and Board objects.
     * Robot players are NOT carried over, and must be asked to re-join.
     * (This simplifies the robot client.)
     * Any vacant seats will be locked, so a robot won't sit there.
     *<P>
     * Old game's state becomes {@link #RESET_OLD}.
     * Old game's previous state is saved to {@link #getOldGameState()}.
     * Please call destroyGame() on old game when done examining its state.
     *<P>
     * Assumes that if the game had more than one human player,
     * they've already voted interactively to reset the board.
     * @see #resetVoteBegin(int)
     * @since 1.1.00
     */
    public SOCGame resetAsCopy()
    {
        SOCGame cp = new SOCGame
            (name, active, (opts != null) ? new SOCGameOptionSet(opts, true) : null, knownOpts);
            // the constructor will set most fields, based on game options

        cp.isFromBoardReset = true;
        oldGameState = gameState;  // for reference if needed
        active = false;
        gameState = RESET_OLD;

        // Most fields are NOT copied since this is a "reset", not an identical-state game.
        cp.isAtServer = isAtServer;
        cp.serverVersion = serverVersion;
        cp.isPractice = isPractice;
        cp.gameEventListener = gameEventListener;
        cp.ownerName = ownerName;
        cp.ownerLocale = ownerLocale;
        cp.hasMultiLocales = hasMultiLocales;
        cp.clientVersionLowest = clientVersionLowest;
        cp.clientVersionHighest = clientVersionHighest;
        cp.clientVersionMinRequired = clientVersionMinRequired;

        // Per-player state
        for (int i = 0; i < maxPlayers; i++)
        {
            boolean wasRobot = false;
            if ((seats[i] == OCCUPIED) && (players[i] != null) && (players[i].getName() != null))
            {
                wasRobot = players[i].isRobot();
                if (! wasRobot)
                {
                    cp.addPlayer(players[i].getName(), i);
                    cp.players[i].setRobotFlag(false, false);
                    cp.players[i].setFaceId(players[i].getFaceId());
                }
            }
            cp.seatLocks[i] = seatLocks[i];
            if (wasRobot)
                cp.seats[i] = VACANT;
            else
            {
                cp.seats[i] = seats[i];  // reset in case addPlayer cleared VACANT for non-in-use player position
                if (cp.seats[i] != OCCUPIED)
                    cp.seatLocks[i] = SeatLockState.CLEAR_ON_RESET;
            }
        }

        return cp;
    }

    /**
     * Begin a board-reset vote.
     * The requester is marked as voting yes, and we mark other players as "no vote yet".
     * Wait for other human players to vote.
     *
     * @param reqPN Player number requesting the vote
     * @throws IllegalArgumentException If this player number has already
     *     requested a reset this turn
     * @throws IllegalStateException If there is already a vote in progress
     *
     * @see #getResetVoteRequester()
     * @see #resetVoteRegister(int, boolean)
     * @see #getResetVoteResult()
     * @since 1.1.00
     */
    public void resetVoteBegin(final int reqPN) throws IllegalArgumentException, IllegalStateException
    {
        if (players[reqPN].hasAskedBoardReset())
            throw new IllegalArgumentException("Player has already asked to reset this turn");

        int numVoters = 0;
        synchronized (boardResetVotes)
        {
             if (boardResetVoteRequester != -1)
                 throw new IllegalStateException("Already voting");
             boardResetVoteRequester = reqPN;
             for (int i = 0; i < maxPlayers; ++i)
             {
                 if (i != reqPN)
                 {
                     boardResetVotes[i] = VOTE_NONE;
                     if (! (isSeatVacant(i) || players[i].isRobot()))
                         ++numVoters;
                 }
                 else
                 {
                     // Requester doesn't count as a voter we're waiting for,
                     // but is easier for other code if assume they voted yes.
                     boardResetVotes[i] = VOTE_YES;
                 }
             }
             boardResetVotesWaiting = numVoters;
        }

        if (gameState >= ROLL_OR_CARD)
        {
            players[reqPN].setAskedBoardReset(true);
            // During game setup (START1A..START2B), normal end-of-turn flags aren't
            // cleared.  Easiest to not set this one during those states.
        }
    }

    /**
     * If a board reset vote is active, player number who requested the vote.
     * All human players must vote unanimously, or board reset is rejected.
     * -1 if no vote is active.
     * After the vote completes, this is set to -1 if the vote was rejected,
     * but retains the requester number if the vote succeeded and the board
     * will soon be reset.
     *
     * @return player number who requested the vote.
     *
     * @see #resetVoteBegin(int)
     * @since 1.1.00
     */
    public int getResetVoteRequester()
    {
        synchronized (boardResetVotes)
        {
            return boardResetVoteRequester;
        }
    }

    /**
     * @return if a board-reset vote is active (waiting for votes).
     * @since 1.1.00
     */
    public boolean getResetVoteActive()
    {
        synchronized (boardResetVotes)
        {
            return (boardResetVotesWaiting > 0);
        }
    }

    /**
     * Register this player's vote in a board reset request.
     *
     * @param pn  Player number
     * @param votingYes Are they voting yes, or no?
     * @return True if voting is now complete, false if still waiting for other votes
     * @throws IllegalArgumentException If pn already voted, or can't vote (vacant or robot).
     * @throws IllegalStateException    If voting is not currently active.
     * @see #getResetPlayerVote(int)
     * @since 1.1.00
     */
    public boolean resetVoteRegister(final int pn, final boolean votingYes)
        throws IllegalArgumentException, IllegalStateException
    {
        boolean vcomplete;
        synchronized (boardResetVotes)
        {
            if (boardResetVotes[pn] != VOTE_NONE)
                throw new IllegalArgumentException("Already voted: " + pn);
            if (isSeatVacant(pn) || players[pn].isRobot())
                throw new IllegalArgumentException("Seat cannot vote: " + pn);
            if ((0 == boardResetVotesWaiting) || (-1 == boardResetVoteRequester))
                throw new IllegalStateException("Voting is not active");
            if (votingYes)
                boardResetVotes[pn] = VOTE_YES;
            else
                boardResetVotes[pn] = VOTE_NO;
            --boardResetVotesWaiting;
            vcomplete = (0 == boardResetVotesWaiting);
            if (vcomplete)
            {
                if (! getResetVoteResult())
                    boardResetVoteRequester = -1;  // Board Reset rejected; clear requester.
            }
        }
        return vcomplete;
    }

    /**
     * Get this player's vote on a board reset request.
     *
     * @param pn  Player number
     * @return Vote value for player: {@link #VOTE_YES}, {@link #VOTE_NO},
     *    or if player hasn't yet voted, {@link #VOTE_NONE}.
     * @see #resetVoteRegister(int, boolean)
     * @see #getResetVoteResult()
     * @since 1.1.00
     */
    public int getResetPlayerVote(final int pn)
    {
        synchronized (boardResetVotes)
        {
            return boardResetVotes[pn];
        }
    }

    /**
     * At end of turn, clear flags for board reset voting:
     * requester, players' setAskedBoardReset.
     * This is outside of {@link #endTurn()} because
     * endTurn is called only at the server, not at clients.
     * Do not call this to cancel a vote during normal gameplay, because
     * it would allow players to ask for a reset more than once per turn.
     * @since 1.1.00
     */
    public void resetVoteClear()
    {
        if (boardResetVoteRequester != -1)
            boardResetVoteRequester = -1;
        synchronized (boardResetVotes)
        {
            boardResetVotesWaiting = 0;
            for (int i = 0; i < maxPlayers; ++i)
                players[i].setAskedBoardReset(false);
        }
    }

    /**
     * If a board-reset vote is complete, give its result.
     * All human players must vote unanimously, or board reset is rejected.
     *
     * @return True if accepted, false if rejected.
     * @throws IllegalStateException if voting is still active. See {@link #getResetVoteActive()}.
     * @see #getResetPlayerVote(int)
     * @since 1.1.00
     */
    public boolean getResetVoteResult() throws IllegalStateException
    {
        boolean vyes;
        synchronized (boardResetVotes)
        {
            if (boardResetVotesWaiting > 0)
                throw new IllegalStateException("Voting is still active");

            vyes = true;  // Assume no "no" votes
            for (int i = 0; i < maxPlayers; ++i)
                if (boardResetVotes[i] == VOTE_NO)
                {
                    vyes = false;
                    break;
                }
        }
        return vyes;
    }

    // ---------------------------------------
    // Special Building Phase
    // ---------------------------------------

    /**
     * Can the player either buy and place a piece (or development card) now,
     * or can they ask now for the Special Building Phase (in a 6-player game)?
     * Based on game state and current player number, not on resources available.
     *<P>
     * In 1.1.09 and later, player is allowed to Special Build at start of their
     * own turn, only if they haven't yet rolled or played a dev card.
     *
     * @param pn  Player number
     * @see #canAskSpecialBuild(int, boolean)
     * @since 1.1.08
     */
    public boolean canBuyOrAskSpecialBuild(final int pn)
    {
        return
          ((pn == currentPlayerNumber)
            && ((gameState == SOCGame.PLAY1) || (gameState == SOCGame.SPECIAL_BUILDING)))
          || canAskSpecialBuild(pn, false);
    }

    /**
     * During game play, are we in the {@link #SPECIAL_BUILDING Special Building Phase}?
     * Includes game state {@link #SPECIAL_BUILDING}, and placement game states during this phase.
     *
     * @return true if in Special Building Phase
     * @since 1.1.08
     */
    public boolean isSpecialBuilding()
    {
        return (gameState == SPECIAL_BUILDING) ||
            ((oldGameState == SPECIAL_BUILDING) && (gameState < OVER));
    }

    /**
     * For 6-player mode's {@link #SPECIAL_BUILDING Special Building Phase},
     * can the player currently request to special build?
     * See 'throws' for the conditions checked.
     *<P>
     * In 1.1.09 and later, player is allowed to Special Build at start of their
     * own turn, only if they haven't yet rolled or played a dev card.
     * To do so, call {@link #askSpecialBuild(int, boolean)} and then {@link #endTurn()}.
     *
     * @param pn  The asking player number
     * @param throwExceptions  Behavior when player can't currently special build:
     *     If true, throw one of the exceptions listed here to explain why.
     *     If false, just return false.
     * @throws IllegalStateException  if game is not 6-player, or pn is current player,
     *            or {@link SOCPlayer#hasAskedSpecialBuild() pn.hasAskedSpecialBuild()}
     *            or {@link SOCPlayer#hasSpecialBuilt() pn.hasSpecialBuilt()} is true,
     *            or if gamestate is earlier than {@link #ROLL_OR_CARD}, or >= {@link #OVER},
     *            or if the first player is asking before completing their first turn.
     * @throws NoSuchElementException  if house rule game option {@code "PLP"} is active
     *            and {@link #getPlayerCount()} returns fewer then 5 sitting players.
     * @throws IllegalArgumentException  if pn is not a valid player (vacant seat, etc).
     * @see #canBuyOrAskSpecialBuild(int)
     * @since 1.1.08
     */
    public boolean canAskSpecialBuild(final int pn, final boolean throwExceptions)
        throws IllegalStateException, NoSuchElementException, IllegalArgumentException
    {
        if (maxPlayers <= 4)
        {
            if (throwExceptions)
                throw new IllegalStateException("not 6-player");
            else
                return false;
        }
        if ((pn < 0) || (pn >= maxPlayers))
        {
            if (throwExceptions)
                throw new IllegalArgumentException("pn range");
            else
                return false;
        }

        SOCPlayer pl = players[pn];
        if ((pl == null) || isSeatVacant(pn))
        {
            if (throwExceptions)
                throw new IllegalArgumentException("pn not valid");
            else
                return false;
        }

        if (isGameOptionSet("PLP") && (getPlayerCount() < 5))
        {
            if (throwExceptions)
                throw new NoSuchElementException("house rule PLP, not enough players");
            else
                return false;
        }

        if ((gameState < ROLL_OR_CARD) || (gameState >= OVER)
              || pl.hasSpecialBuilt()
              || pl.hasAskedSpecialBuild())
        {
            if (throwExceptions)
                throw new IllegalStateException("cannot ask at this time");
            else
                return false;
        }

        if ((pn == currentPlayerNumber)
            && ((gameState != ROLL_OR_CARD)
                || (turnCount == 1)       // since SBP occurs @ end of each turn, not @ start
                || pl.hasPlayedDevCard()))
        {
            if (throwExceptions)
                throw new IllegalStateException("current player");
            else
                return false;
        }

        return true;
    }

    /**
     * For 6-player mode's Special Building Phase, check state and
     * set the flag for this player asking to build.
     *<P>
     * In 1.1.09 and later, player is allowed to Special Build at start of their
     * own turn, only if they haven't yet rolled or played a dev card.
     * To do so, call {@link #askSpecialBuild(int, boolean)} and then {@link #endTurn()}.
     *<P>
     * Also sets game's <tt>askedSpecialBuildPhase</tt> flag in 1.1.09 and later.
     *<P>
     * Note that this method only sets the flags, and cannot clear them.
     * You can clear the player's flag via {@link SOCPlayer#setAskedSpecialBuild(boolean)},
     * but cannot directly clear <tt>askedSpecialBuildPhase</tt>.
     * Normal game flow does not need a way to do so.
     *
     * @param pn  The player's number
     * @param onlyIfCan  Check if player can do so, before setting player and game flags.
     *            Should always be <tt>true</tt> for server calls.
     * @throws IllegalStateException  if game is not 6-player, or is currently this player's turn,
     *            or if gamestate is earlier than {@link #ROLL_OR_CARD}, or >= {@link #OVER}.
     * @throws NoSuchElementException  if {@code onlyIfCan}, and house rule game option {@code "PLP"} is active,
     *     and {@link #getPlayerCount()} returns fewer then 5 sitting players.
     * @throws IllegalArgumentException  if pn is not a valid player (vacant seat, etc).
     * @since 1.1.08
     */
    public void askSpecialBuild(final int pn, final boolean onlyIfCan)
        throws IllegalStateException, NoSuchElementException, IllegalArgumentException
    {
        if ((! onlyIfCan) || canAskSpecialBuild(pn, true))
        {
            players[pn].setAskedSpecialBuild(true);
            askedSpecialBuildPhase = true;
        }
    }

    /**
     * Are we in the 'free placement' debug mode?
     * If so, can assume a debug client player is in this game
     * and is the current player. For purpose and more info
     * see SOCGameHandler.processDebugCommand_freePlace,
     * SOCPlayerInterface.setDebugFreePlacementMode.
     * For more details on required conditions to activate this mode
     * see {@link #setDebugFreePlacement(boolean)}.
     * @see #putPiece(SOCPlayingPiece)
     * @since 1.1.12
     */
    public boolean isDebugFreePlacement()
    {
        return debugFreePlacement;
    }

    /**
     * Turn the "free placement" debug mode on or off, if possible.
     *<P>
     * Should only be turned on during the debugging human player's turn,
     * in game state {@link #PLAY1} or during {@link #isInitialPlacement()} states.
     *<P>
     * When turning it off during initial placement, all players must have
     * either 1 settlement and road, or at least 2 settlements and roads.
     * This allows the game setup routines to work properly during this debug
     * mode.  The current player will be set to the first player (if 2+
     * settlements/roads placed) or the last player (1 settlement placed).
     * Check {@link #getCurrentPlayerNumber()} and {@link #getGameState()}
     * after calling {@link #setDebugFreePlacement(boolean) setDebugFreePlacement(false)}
     * during initial placement.
     *<P>
     * For more details, see {@link #isDebugFreePlacement()} javadoc.
     *
     * @param debugOn  Should the mode be on?
     * @throws IllegalStateException  if turning on when gameState is not {@link #PLAY1}
     *    or an initial placement state ({@link #START1A} - {@link #START3B}),
     *    or turning off during initial placement with an unequal number of pieces placed.
     * @since 1.1.12
     */
    public void setDebugFreePlacement(final boolean debugOn)
        throws IllegalStateException
    {
        if ((gameState != SOCGame.PLAY1)
            && (debugOn != (gameState < SOCGame.OVER))
            && ! isInitialPlacement())
            throw new IllegalStateException("state=" + gameState);
        if (debugOn == debugFreePlacement)
            return;

        if (debugFreePlacementStartPlaced
            && (gameState < SOCGame.OVER)
            && ! debugOn)
        {
            // Special handling: When exiting this mode during
            // initial placement, all players must have the same
            // number of settlements and roads.
            final boolean has3rdInitPlace = isGameOptionSet(SOCGameOptionSet.K_SC_3IP);
            final int npieceMax = has3rdInitPlace ? 6 : 4;
            int npiece = -1;
            boolean ok = true;
            for (int i = 0; i < maxPlayers; ++i)
            {
                if (isSeatVacant(i))
                    continue;
                int n = players[i].getPieces().size();
                if (n > npieceMax)
                    n = npieceMax;
                else if ((n == 1) || (n == 3) || (n == 5))
                    ok = false;
                if (npiece == -1)
                    npiece = n;
                else if (npiece != n)
                    ok = false;
            }
            if (! ok)
                throw new IllegalStateException("initial piece count");
            if (npiece == 2)
            {
                currentPlayerNumber = lastPlayerNumber;
                gameState = START2A;
            }
            else if (npiece == 4)
            {
                currentPlayerNumber = firstPlayerNumber;
                if (! has3rdInitPlace)
                {
                    if (isAtServer)
                    {
                        gameState = ROLL_OR_CARD;
                        updateAtGameFirstTurn();  // "virtual" endTurn here,
                          // just like advanceTurnStateAfterPutPiece().
                    }
                } else {
                    gameState = START3A;
                }
            }
            else if (npiece == 6)
            {
                if (isAtServer)
                {
                    currentPlayerNumber = firstPlayerNumber;
                    gameState = ROLL_OR_CARD;
                    updateAtGameFirstTurn();  // "virtual" endTurn here,
                      // just like advanceTurnStateAfterPutPiece().
                }
            }
        }

        debugFreePlacement = debugOn;
        debugFreePlacementStartPlaced = false;
    }

    /**
     * The number of normal turns (not rounds, not initial placements), including this turn.
     * This is 0 during initial piece placement, and 1 when the first player is about to
     * roll dice for the first time. Updated in {@link #updateAtTurn()}.
     *<P>
     * Not sent over the network: Accurate at server, or client joining at start of game,
     * but not for a client who joined after game play started.
     *
     * @return the turn number
     * @see #getRoundCount()
     * @since 2.5.00
     */
    public int getTurnCount()
    {
        return turnCount;
    }

    /**
     * toString contains the game name.
     * @return "SOCGame{" + gameName + "}"
     * @since 1.1.12
     */
    @Override
    public String toString()
    {
        return "SOCGame{" + name + "}";
    }

    /**
     * Seat lock states for lock/unlock.
     * Note different meanings while game is forming
     * (gameState {@link SOCGame#NEW NEW}) versus already active:
     * See enum value javadocs for details.
     *<UL>
     * <LI> {@link #UNLOCKED}
     * <LI> {@link #LOCKED}
     * <LI> {@link #CLEAR_ON_RESET}
     *</UL>
     * @see SOCGame#getSeatLock(int)
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    public static enum SeatLockState
    {
        /** Seat not locked.
         *  If game is forming, if this seat is empty when the game starts, a bot can sit here.
         *  If game is active, a newly-joining player can take over a bot in this seat.
         */
        UNLOCKED,

        /** Seat is locked.
         *  If game is forming, a bot will not sit here when the game starts.
         *  If game is active, a newly-joining player can't take over a bot in this seat.
         *  @see #CLEAR_ON_RESET
         */
        LOCKED,

        /** If this active game is reset, a robot will not take this seat, it will be left vacant.
         *  During game play, behaves like {@link #UNLOCKED}.
         *  Useful for resetting a game to play again with fewer robots, if a robot is currently sitting here.
         *  Not a valid seat lock state if game is still forming.
         *<P>
         *  That feature was added in v2.0.00; before that version, the seat lock state was
         *  boolean ({@link #UNLOCKED} or {@link #LOCKED}).  Game resets included all robots unless their seat
         *  was LOCKED at the time of reset.
         */
        CLEAR_ON_RESET
    }

    /**
     * Dice roll result, for reporting from {@link SOCGame#rollDice()}.
     * Each game has 1 instance of this object, which is updated each turn.
     * @see SOCPlayer#getRolledResources()
     * @see SOCMoveRobberResult
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    public static class RollResult
    {
        /**
         * The dice numbers rolled, each 1 to 6.
         */
        public int diceA, diceB;

        /**
         * Null, or distributed cloth (for game scenario SC_CLVI) in this format:
         *   [ Cloth amount taken from general supply,
         *     Cloth amount given to player 0, to player 1, ..., to player n ].
         * Any {@link SOCVillage}s with matching dice numbers which took part are in {@link #clothVillages}.
         * @see SOCBoardAtServer#distributeClothFromRoll(SOCGame, RollResult, int)
         */
        public int[] cloth;

        /**
         * Null, empty, or any village(s) with matching dice numbers which had cloth remaining,
         * and may have distributed some of the cloth in {@link #cloth}
         * and changed their {@link SOCVillage#getCloth()} amounts.
         *<P>
         * The 4-player board won't put more than 1 village in this list,
         * but the 6-player layout has dice numbers with multiple villages.
         */
        public List<SOCVillage> clothVillages;

        /**
         * Robber/pirate fleet victims in some scenarios, otherwise null.
         *<P>
         * When a 7 is rolled in game scenario {@link SOCScenario#K_SC_PIRI SC_PIRI},
         * there is no robber piece to move; the current player immediately picks another
         * player with resources to steal from.  In that situation, this field holds
         * the list of possible victims, and gameState is {@link #WAITING_FOR_ROB_CHOOSE_PLAYER}.
         *<P>
         * Moving the pirate fleet might also have a different victim:
         * See {@link #sc_piri_fleetAttackVictim}, {@link #sc_piri_fleetAttackRsrcs},
         * and {@link SOCMoveRobberResult#sc_piri_loot}.
         *
         * @see SOCGame#getPossibleVictims()
         */
        public List<SOCPlayer> sc_robPossibleVictims;

        /**
         * When the pirate fleet moves in game scenario {@link SOCScenario#K_SC_PIRI SC_PIRI},
         * they may attack the player with an adjacent settlement or city.
         * If no adjacent, or more than 1, nothing happens, and this field is null.
         * Otherwise see {@link #sc_piri_fleetAttackRsrcs} for the result;
         * The "victim" may win the battle and gain resource picks.
         * See also {@link SOCMoveRobberResult#sc_piri_loot}.
         *<P>
         * Each time the dice is rolled, the fleet is moved and this field is updated; may be null.
         * For robbery behavior when 7 is rolled, see {@link #sc_robPossibleVictims}.
         */
        public SOCPlayer sc_piri_fleetAttackVictim;

        /**
         * When the pirate fleet moves in game scenario {@link SOCScenario#K_SC_PIRI SC_PIRI},
         * resources lost when they attack the player with an adjacent settlement or city
         * ({@link #sc_piri_fleetAttackVictim}).
         *<P>
         * Each time the dice is rolled, the fleet is moved and this field is updated; may be null.
         *<P>
         * If the victim wins against the attack, they must soon be prompted to gain a resource of their choice.
         * This resource set contains {@link SOCResourceConstants#GOLD_LOCAL} to indicate the win.
         * State became {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE} and the caller of {@link SOCGame#rollDice()}
         * must prompt the player to choose their free resource.
         */
        public SOCResourceSet sc_piri_fleetAttackRsrcs;

        /**
         * Convenience: Set diceA and diceB; null out {@link #cloth} and {@link #sc_robPossibleVictims};
         * empty {@link #clothVillages} if not null.
         */
        public void update(final int dA, final int dB)
        {
            diceA = dA;
            diceB = dB;
            cloth = null;
            if (clothVillages != null)
                clothVillages.clear();
            sc_robPossibleVictims = null;
        }

    }  // nested class RollResult

}
