/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2012 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Skylar Bolton <iiagrer@gmail.com>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import soc.message.SOCMessage;
import soc.util.SOCGameBoardReset;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
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
 * The game begins with the server calling {@link #startGame()}.  During game play,
 * {@link #putPiece(SOCPlayingPiece)} and other game-action methods update <tt>gameState</tt>.
 * {@link #updateAtTurn()}, <tt>putPiece</tt> and some other game-action methods update {@link #lastActionTime}.
 *<P>
 * The winner is the player who has {@link #vp_winner} or more victory points (typically 10)
 * on their own turn.  Some optional game scenarios have special win conditions, see {@link #checkForWinner()}.
 *<P>
 * The {@link SOCGame#hasSeaBoard large sea board} features scenario events.
 * To listen for these, call {@link #setScenarioEventListener(SOCScenarioEventListener)}.
 *
 * @author Robert S. Thomas
 */
public class SOCGame implements Serializable, Cloneable
{
    /**
     * Game states.  NEW is a brand-new game, not yet ready to start playing.
     * Players are choosing where to sit, or have all sat but no one has yet clicked
     * the "start game" button.
     * Next state from NEW is {@link #READY} if robots, or {@link #START1A} if only humans
     * are playing.
     *<P>
     * General assumptions for states and their numeric values:
     * <UL>
     * <LI> Active game states are >= {@link #START1A} and < {@link #OVER}
     * <LI> Initial placement ends after {@link #START2B} or {@link #START3B}, going directly to {@link #PLAY}
     * <LI> A Normal turn's "main phase" is {@link #PLAY1}, after dice-roll/card-play in {@link #PLAY}
     * <LI> When the game is waiting for a player to react to something,
     *      state is > {@link #PLAY1}, < {@link #OVER}; state name starts with
     *      PLACING_ or WAITING_
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
     * <LI> {@link soc.server.SOCServer#sendGameState(SOCGame)}
     *</UL>
     * Also, if your state is similar to an existing state, do a where-used search
     * for that state, and decide where both states should be reacted to.
     *<P>
     * Other places to check, if you add a game state:
     *<UL>
     * <LI> SOCBoardPanel.BoardPopupMenu.showBuild, showCancelBuild
     * <LI> SOCBoardPanel.drawBoard
     * <LI> SOCHandPanel.addPlayer, began, removePlayer, updateAtTurn, updateValue
     * <LI> SOCGame.addPlayer
     * <LI> SOCServer.handleSTARTGAME, leaveGame, sitDown, handleCANCELBUILDREQUEST, handlePUTPIECE
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
     */
    public static final int READY = 1; // Ready to start playing

    public static final int SETOPTIONS_EXCL = 2; // Future use: Game owner setting options, no one can yet connect
    public static final int SETOPTIONS_INCL = 3; // Future use: Game owner setting options, but anyone can connect
        // These are still unused in 1.1.07, even though we now have game options,
        // because the options are set before the SOCGame is created.

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
     * If game scenario option {@link SOCGameOption#K_SC_3IP _SC_3IP} is set, then instead of
     * this second settlement giving resources, a third round of placement will do that;
     * next game state after START2A remains {@link #START2B}.
     */
    public static final int START2A = 10; // Players place 2nd stlmt

    /**
     * Just placed the second or third settlement, waiting for current
     * player to choose which Gold Hex resources to receive.
     * Next game state is {@link #START2B} to place 2nd road.
     * If game scenario option {@link SOCGameOption#K_SC_3IP _SC_3IP} is set,
     * next game state is {@link #START3B}.
     *<P>
     * Valid only when {@link #hasSeaBoard}, settlement adjacent to {@link SOCBoardLarge#GOLD_HEX}.
     * @see #WAITING_FOR_PICK_GOLD_RESOURCE
     * @since 2.0.00
     */
    public static final int STARTS_WAITING_FOR_PICK_GOLD_RESOURCE = 14;

    /**
     * Players place second road.  Next state is {@link #START2A} to place previous
     * player's 2nd settlement (player changes in reverse order), or if all have placed
     * settlements, {@link #PLAY} to begin first player's turn.
     *<P>
     * If game scenario option {@link SOCGameOption#K_SC_3IP _SC_3IP} is set, then instead of
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
     * Valid only when game scenario option {@link SOCGameOption#K_SC_3IP _SC_3IP} is set.
     */
    public static final int START3A = 12;

    /**
     * Players place third road.  Next state is {@link #START3A} to place previous
     * player's 3rd settlement (player changes in normal order), or if all have placed
     * settlements, {@link #PLAY} to begin first player's turn.
     *<P>
     * Valid only when game scenario option {@link SOCGameOption#K_SC_3IP _SC_3IP} is set.
     */
    public static final int START3B = 13;

    /**
     * Start of a normal turn.  Time to roll or play a card.
     * Next state depends on card or roll, but usually is {@link #PLAY1}.
     *<P>
     * If 7 is rolled, might be {@link #WAITING_FOR_DISCARDS} or {@link #WAITING_FOR_ROBBER_OR_PIRATE}
     *   or {@link #PLACING_ROBBER} or {@link #PLACING_PIRATE}.
     *<P>
     * If the number rolled is on a gold hex, next state might be
     *   {@link #WAITING_FOR_PICK_GOLD_RESOURCE}.
     */
    public static final int PLAY = 15; // Play continues normally; time to roll or play card

    /**
     * Done rolling (or moving robber on 7).  Time for other turn actions,
     * such as building or buying or trading, or playing a card if not already done.
     * Next state depends on what's done, but usually is the next player's {@link #PLAY}.
     */
    public static final int PLAY1 = 20; // Done rolling

    public static final int PLACING_ROAD = 30;
    public static final int PLACING_SETTLEMENT = 31;
    public static final int PLACING_CITY = 32;

    /**
     * Player is placing the robber on a new land hex.
     * May follow state {@link #WAITING_FOR_ROBBER_OR_PIRATE} if the game {@link #hasSeaBoard}.
     * @see #PLACING_PIRATE
     * @see #canMoveRobber(int, int)
     * @see #moveRobber(int, int)
     */
    public static final int PLACING_ROBBER = 33;

    /**
     * Player is placing the pirate ship on a new water hex,
     * in a game which {@link #hasSeaBoard}.
     * May follow state {@link #WAITING_FOR_ROBBER_OR_PIRATE}.
     * Next game state may be {@link #WAITING_FOR_ROB_CLOTH_OR_RESOURCE}.
     * @see #PLACING_ROBBER
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
     * Player is placing first free road/ship
     */
    public static final int PLACING_FREE_ROAD1 = 40;

    /**
     * Player is placing second free road/ship
     */
    public static final int PLACING_FREE_ROAD2 = 41;

    /**
     * Waiting for player(s) to discard, after 7 is rolled.
     * Next game state is {@link #WAITING_FOR_DISCARDS}
     * (if other players still need to discard),
     * {@link #WAITING_FOR_ROBBER_OR_PIRATE},
     * or {@link #PLACING_ROBBER}.
     * @see #discard(int, SOCResourceSet)
     */
    public static final int WAITING_FOR_DISCARDS = 50;

    /**
     * Waiting for player to choose a player to rob,
     * with the robber or pirate ship, after rolling 7 or
     * playing a Knight/Soldier card.
     * Next game state is {@link #PLAY1} or {@link #WAITING_FOR_ROB_CLOTH_OR_RESOURCE}.
     * To see whether we're moving the robber or the pirate, use {@link #getRobberyPirateFlag()}.
     * To choose the player, call {@link #choosePlayerForRobbery(int)}.
     * @see #playKnight()
     * @see #canChoosePlayer(int)
     * @see #canChooseRobClothOrResource(int)
     * @see #stealFromPlayer(int, boolean)
     */
    public static final int WAITING_FOR_CHOICE = 51;

    /**
     * Waiting for player to choose 2 resources (Discovery card)
     * Next game state is {@link #PLAY1}.
     */
    public static final int WAITING_FOR_DISCOVERY = 52;

    /**
     * Waiting for player to choose a resource (Monopoly card)
     * Next game state is {@link #PLAY1}.
     */
    public static final int WAITING_FOR_MONOPOLY = 53;

    /**
     * Waiting for player to choose the robber or the pirate ship.
     * Next game state is {@link #PLACING_ROBBER} or {@link #PLACING_PIRATE}.
     * @see #canChooseMovePirate()
     * @see #chooseMovePirate(boolean)
     * @since 2.0.00
     */
    public static final int WAITING_FOR_ROBBER_OR_PIRATE = 54;

    /**
     * Waiting for player to choose whether to rob cloth or rob a resource.
     * Previous game state is {@link #PLACING_PIRATE}.
     * Used with scenario option {@link SOCGameOption#K_SC_CLVI _SC_CLVI}.
     * @see #movePirate(int, int)
     * @see #canChooseRobClothOrResource(int)
     * @since 2.0.00
     */
    public static final int WAITING_FOR_ROB_CLOTH_OR_RESOURCE = 55;

    /**
     * Waiting for player(s) to choose which Gold Hex resources to receive.
     * Next game state is {@link #PLAY1}.
     *<P>
     * Valid only when {@link #hasSeaBoard}, settlements or cities
     * adjacent to {@link SOCBoardLarge#GOLD_HEX}.
     * @see #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE
     * @since 2.0.00
     */
    public static final int WAITING_FOR_PICK_GOLD_RESOURCE = 56;

    /**
     * Waiting for player to choose a settlement to destroy, or
     * a city to downgrade (Dev card {@link SOCDevCardConstants#DESTROY}).
     * Used with game option <tt>"DH"</tt>.
     * @since 2.0.00
     */
    public static final int WAITING_FOR_DESTROY = 57;

    /**
     * Waiting for player to choose a settlement or city to swap
     * with another player (Dev card {@link SOCDevCardConstants#SWAP}).
     * Used with game option <tt>"DH"</tt>.
     * @since 2.0.00
     */
    public static final int WAITING_FOR_SWAP = 58;

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
     * The game is over.  A player has accumulated enough ({@link #vp_winner}) victory points,
     * or all players have left the game.
     */
    public static final int OVER = 1000; // The game is over

    /**
     * This game is an obsolete old copy of a new (reset) game with the same name.
     * To assist logic, numeric constant value is greater than {@link #OVER}.
     * @see #resetAsCopy()
     * @see #getResetOldGameState()
     */
    public static final int RESET_OLD = 1001;

    /**
     * seat states
     */
    public static final int VACANT = 0, OCCUPIED = 1;

    /**
     * seatLock states
     */
    public static final boolean LOCKED = true, UNLOCKED = false;

    /**
     * {@link #boardResetVotes} per-player states: no vote sent; yes; no.
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
     * @see #MAXPLAYERS
     * @see #maxPlayers
     * @since 1.1.08
     */
    public static final int MAXPLAYERS_STANDARD = 4;

    /**
     * minimum number of players in a game (was assumed =={@link #MAXPLAYERS} in standard 1.0.6).
     * Use {@link #isSeatVacant(int)} to determine if a player is present;
     * <tt>players[i]</tt> will be non-null although no player is there.
     */
    public static final int MINPLAYERS = 2;

    /**
     * Default number of victory points (10) needed to win.
     * Per-game copy is {@link #vp_winner}, can be changed from 10 in
     * constructor with the <tt>"VP"</tt> {@link SOCGameOption}.
     *<P>
     * Before v1.1.14, this was public static final int <tt>VP_WINNER</tt>.
     * @since 1.1.14
     */
    public static final int VP_WINNER_STANDARD = 10;

    /**
     * Number of development cards (25) in the standard rules.
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
     * an empty set of resources.
     * @see #SETTLEMENT_SET
     */
    public static final SOCResourceSet EMPTY_RESOURCES = new SOCResourceSet();

    /**
     * the set of resources a player needs to build a {@link SOCSettlement settlement}
     * @see SOCPlayingPiece#getResourcesToBuild(int)
     */
    public static final SOCResourceSet SETTLEMENT_SET = new SOCResourceSet(1, 0, 1, 1, 1, 0);

    /**
     * the set of resources a player needs to build a {@link SOCRoad road}
     * @see SOCPlayingPiece#getResourcesToBuild(int)
     */
    public static final SOCResourceSet ROAD_SET = new SOCResourceSet(1, 0, 0, 0, 1, 0);

    /**
     * the set of resources a player needs to build a {@link SOCCity city}
     * @see SOCPlayingPiece#getResourcesToBuild(int)
     */
    public static final SOCResourceSet CITY_SET = new SOCResourceSet(0, 3, 0, 2, 0, 0);

    /**
     * the set of resources a player needs to build a {@link SOCShip ship}
     * @see SOCPlayingPiece#getResourcesToBuild(int)
     * @since 2.0.00
     */
    public static final SOCResourceSet SHIP_SET = new SOCResourceSet(0, 0, 1, 0, 1, 0);

    /**
     * the set of resources a player needs to buy a development card
     * @see SOCPlayingPiece#getResourcesToBuild(int)
     * @see SOCDevCardSet
     */
    public static final SOCResourceSet CARD_SET = new SOCResourceSet(0, 1, 1, 1, 0, 0);

    /**
     * monitor for synchronization
     */
    boolean inUse;

    /**
     * the name of the game
     */
    private String name;

    /**
     * Is this the server's complete copy of the game, not the client's?
     * Set during {@link #startGame()}.
     * @since 1.1.17
     */
    boolean isAtServer;

    /**
     * For games at server, a convenient place to hold outbound messages during game actions.
     * Public access for use by SOCServer.  The server will handle a game action as usual
     * (for example, {@link #putPiece(SOCPlayingPiece)}), create pending PLAYERELEMENT messages from
     * {@link SOCScenarioEventListener#playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object) SOCScenarioEventListener.playerEvent(...)},
     * send the usual messages related to that action, then check this list and send out
     * the pending PLAYERELEMENT message so that the game's clients will update that player's
     * {@link SOCPlayer#setScenarioPlayerEvents(int)} or other related fields.
     *<P>
     * Because this is server-only, it's null until {@link #startGame()}.
     * @since 2.0.00
     */
    public transient List<Object> pendingMessagesOut;

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
     * For example, scenario {@link SOCGameOption#K_SC_CLVI _SC_CLVI} will end the game if
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
     * or if connection instanceof LocalStringConnection.
     *<P>
     * Since 1.1.09: This flag is set at the server, only if the server is a local practice
     * server whose stringport name is <tt>SOCServer.PRACTICE_STRINGPORT</tt>.
     *<P>
     * Before 1.1.13, this field was called <tt>isLocal</tt>, but that was misleading;
     * the full client can launched a tcp LAN server.
     */
    public boolean isPractice;

    /**
     * Listener for scenario events on the {@link #hasSeaBoard large sea board}, or null.
     * Package access for read-only use by {@link SOCPlayer}.
     * @since 2.0.00
     */
    SOCScenarioEventListener scenarioEventListener;

    /**
     * For use at server; are there clients connected which aren't at the latest version?
     */
    public boolean hasOldClients;

    /**
     * For use at server; lowest and highest version of connected clients.
     */
    public int clientVersionLowest, clientVersionHighest;

    /**
     * For use at server; lowest version of client which can connect to
     * this game (based on game options/features added in a given version),
     * or -1 if unknown.
     * Calculated by {@link SOCGameOption#optionsMinimumVersion(Hashtable)}.
     * Format is the internal integer format, see {@link soc.util.Version#versionNumber()}.
     * Value may sometimes be too low at client, see {@link #getClientVersionMinRequired()} for details.
     */
    private int clientVersionMinRequired;

    /**
     * Are we in the 'free placement' debug mode?
     * See server.processDebugCommand_freePlace,
     * SOCPlayerInterface.setDebugPaintPieceMode.
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
     */
    private int boardResetVoteRequester;

    /**
     * If a board reset vote is active, votes are recorded here.
     * Values: {@link #VOTE_NONE}, {@link #VOTE_YES}, {@link #VOTE_NO}.
     * Indexed 0 to SOCGame.MAXPLAYERS-1.
     * Synchronize on this object before reading or writing.
     */
    private int boardResetVotes[];

    /**
     * If a board reset vote is active, we're waiting to receive this many more votes.
     * All human players vote, except the vote requester. Robots do not vote.
     * Synchronize on {@link #boardResetVotes} before reading or writing.
     * When the vote is complete, or before the first vote has begun, this is 0.
     * Set in resetVoteBegin, resetVoteRegister. Cleared in resetVoteClear.
     */
    private int boardResetVotesWaiting;

    /**
     * the game board
     */
    private SOCBoard board;

    /**
     * the game options ({@link SOCGameOption}), or null
     * @since 1.1.07
     */
    private Hashtable<String, SOCGameOption> opts;

    /**
     * the players; never contains a null element, use {@link #isSeatVacant(int)}
     * to see if a position is occupied.  Length is {@link #maxPlayers}.
     */
    private SOCPlayer[] players;

    /**
     * the states for the player's seats
     */
    private int[] seats;

    /**
     * the states if the locks for the player's seats
     */
    private boolean[] seatLocks;

    /**
     * the number of the current player
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
     * maxPlayers is 4 for the standard game,
     * or 6 if this game is on the 6-player board, with corresponding rules.
     *<P>
     * The 6-player extensions are orthogonal to other board type/expansion flags such as {@link #hasSeaBoard};
     * one doesn't imply or exclude the other.
     * @since 1.1.08
     */
    public final int maxPlayers;

    /**
     * Is this game played on the {@link SOCBoardLarge} large board / sea board?
     * If true, our board's {@link SOCBoard#getBoardEncodingFormat()}
     * must be {@link SOCBoard#BOARD_ENCODING_LARGE}.
     * When <tt>hasSeaBoard</tt>, {@link #getBoard()} can be cast to {@link SOCBoardLarge}.
     *<P>
     * The 6-player extensions ({@link #maxPlayers} == 6) are orthogonal to <tt>hasSeaBoard</tt>
     * or other board types/expansions; one doesn't imply or exclude the other.
     * @since 2.0.00
     */
    public final boolean hasSeaBoard;

    /**
     * the current dice result. -1 at start of game, 0 during player's turn before roll (state {@link #PLAY}).
     * @see #currentRoll
     */
    private int currentDice;

    /**
     * The current dice result, including any scenario items such as
     * {@link SOCVillage#distributeCloth(SOCGame)} results.
     * This is the object returned from {@link #rollDice()} each turn.
     * @since 2.0.00
     * @see #currentDice
     */
    private RollResult currentRoll;

    /**
     * the current game state
     */
    private int gameState;

    /**
     * the old game state
     */
    private int oldGameState;

    /**
     * If true, and if state is {@link #PLACING_ROBBER},
     * the robber is being moved because a knight card
     * has just been played.  Thus, if {@link #forceEndTurn()}
     * is called, the knight card should then be returned to
     * the player's hand.
     */
    private boolean placingRobberForKnightCard;

    /**
     * If true, this turn is being ended. Controller of game (server) should call {@link #endTurn()}
     * whenever possible.  Usually set when we have called {@link #forceEndTurn()}, and
     * forced the current player to discard randomly, and are waiting for other players
     * to discard in gamestate {@link #WAITING_FOR_DISCARDS}.  Once all players have
     * discarded, the turn should be ended.
     * @see #forceEndTurn()
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
     * For the 6-player board's Special Building Phase, the player number whose
     * normal turn (roll, place, etc) has just ended.
     * Game state is {@link #SPECIAL_BUILDING}.
     * The Special Building Phase changes {@link #currentPlayerNumber}.
     * So, it begins by calling {@link #advanceTurn()} to
     * the next player, and continues clockwise until
     * {@link #currentPlayerNumber} == {@link #specialBuildPhase_afterPlayerNumber}.
     * At that point, the Special Building Phase is over,
     * and it's the next player's turn as usual.
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
     */
    private int playerWithWin;

    /**
     * the number of development cards left
     */
    private int numDevCards;

    /**
     * the development card deck.
     * Each element is a dev card type from {@link SOCDevCardConstants}.
     */
    private int[] devCardDeck;

    /**
     * used to generate random numbers
     */
    private Random rand = new Random();

    /**
     * used to track if there were any player subs
     */
    boolean allOriginalPlayers;

    /**
     * when this game was created
     */
    Date startTime;

    /**
     * expiration time for this game in milliseconds.
     * Same format as {@link System#currentTimeMillis()}.
     */
    long expiration;

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
     * 90 minutes ({@link soc.server.SOCGameListAtServer#GAME_EXPIRE_MINUTES})
     * in order to remove it from the {@link soc.server.SOCGameTimeoutChecker}
     * run loop.
     *
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
     * @see #getRobberyPirateFlag()
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
     * @since 2.0.00
     */
    private Vector<Integer> placedShipsThisTurn;

    /**
     * The number of normal turns (not rounds, not initial placements), including this turn.
     *  This is 0 during initial piece placement, and 1 when the first player is about to
     *  roll dice for the first time.
     *  updated in {@link #updateAtTurn()}.
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
     * @param n  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     */
    public SOCGame(String n)
    {
        this(n, true, null);
    }

    /**
     * create a new, active game with options
     *
     * @param n  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param op if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     *           Will validate options by calling
     *           {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable, boolean)}
     *           with <tt>doServerPreadjust</tt> false,
     *           and set game's minimum version by calling
     *           {@link SOCGameOption#optionsMinimumVersion(Hashtable)}.
     * @throws IllegalArgumentException if op contains unknown options, or any
     *             object class besides {@link SOCGameOption}
     * @since 1.1.07
     */
    public SOCGame(String n, Hashtable<String, SOCGameOption> op)
        throws IllegalArgumentException
    {
        this(n, true, op);
    }

    /**
     * create a new game that can be ACTIVE or INACTIVE
     *
     * @param n  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param a  true if this is an active game, false for inactive
     * @throws IllegalArgumentException if game name fails
     *           {@link SOCMessage#isSingleLineAndSafe(String)}. This check was added in 1.1.07.
     */
    public SOCGame(String n, boolean a)
        throws IllegalArgumentException
    {
        this(n, a, null);
    }

    /**
     * create a new game that can be ACTIVE or INACTIVE, and have options
     *
     * @param n  the name of the game.  For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param a  true if this is an active game, false for inactive
     * @param op if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     *           Will validate options by calling
     *           {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable, boolean)}
     *           with <tt>doServerPreadjust</tt> false,
     *           and set game's minimum version by calling
     *           {@link SOCGameOption#optionsMinimumVersion(Hashtable)}.
     * @throws IllegalArgumentException if op contains unknown options, or any
     *             object class besides {@link SOCGameOption}, or if game name
     *             fails {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @since 1.1.07
     */
    public SOCGame(String n, boolean a, Hashtable<String, SOCGameOption> op)
        throws IllegalArgumentException
    {
        // For places to initialize fields, see also resetAsCopy().

        if (! SOCMessage.isSingleLineAndSafe(n))
            throw new IllegalArgumentException("n");

        active = a;
        inUse = false;
        name = n;
        if (op != null)
        {
            hasSeaBoard = isGameOptionSet(op, "PLL");
            final boolean wants6board = isGameOptionSet(op, "PLB");
            final int maxpl = getGameOptionIntValue(op, "PL", 4, false);
            if (wants6board || (maxpl > 4))
                maxPlayers = MAXPLAYERS;  // == 6
            else
                maxPlayers = 4;
            vp_winner = getGameOptionIntValue(op, "VP", VP_WINNER_STANDARD, true);
            hasScenarioWinCondition = isGameOptionSet(op, SOCGameOption.K_SC_CLVI);
        } else {
            maxPlayers = 4;
            hasSeaBoard = false;
            vp_winner = VP_WINNER_STANDARD;
            hasScenarioWinCondition = false;
        }
        board = SOCBoard.createBoard(op, hasSeaBoard, maxPlayers);
        players = new SOCPlayer[maxPlayers];
        seats = new int[maxPlayers];
        seatLocks = new boolean[maxPlayers];
        boardResetVotes = new int[maxPlayers];

        for (int i = 0; i < maxPlayers; i++)
        {
            players[i] = new SOCPlayer(i, this);
            seats[i] = VACANT;
            seatLocks[i] = UNLOCKED;
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
            placedShipsThisTurn = new Vector<Integer>();

        opts = op;
        if (op == null)
        {
            clientVersionMinRequired = -1;
        } else {
            final StringBuffer optProblems = SOCGameOption.adjustOptionsToKnown(op, null, false);
            if (optProblems != null)
                throw new IllegalArgumentException("op: unknown option(s): " + optProblems);

            // the adjust method will also throw IllegalArg if a non-SOCGameOption
            // object is found within opts.

            clientVersionMinRequired = SOCGameOption.optionsMinimumVersion(op);
        }

        if (maxPlayers > 4)
            numDevCards = NUM_DEVCARDS_6PLAYER;
        else
            numDevCards = NUM_DEVCARDS_STANDARD;

        if (active)
            startTime = new Date();
        lastActionTime = System.currentTimeMillis();
    }

    /**
     * take the monitor for this game
     */
    public synchronized void takeMonitor()
    {
        //D.ebugPrintln("TAKE MONITOR");
        while (inUse)
        {
            try
            {
                wait(1000);
            }
            catch (InterruptedException e)
            {
                System.out.println("EXCEPTION IN takeMonitor() -- " + e);
            }
        }

        inUse = true;
    }

    /**
     * release the monitor for this game
     */
    public synchronized void releaseMonitor()
    {
        //D.ebugPrintln("RELEASE MONITOR");
        inUse = false;
        this.notify();
    }

    /**
     * @return allOriginalPlayers
     */
    public boolean allOriginalPlayers()
    {
        return allOriginalPlayers;
    }

    /**
     * @return the start time for this game, or null if inactive
     */
    public Date getStartTime()
    {
        return startTime;
    }

    /**
     * @return the expiration time in milliseconds,
     *            same epoch as {@link java.util.Date#getTime()}
     */
    public long getExpiration()
    {
        return expiration;
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
    public void setScenarioEventListener(SOCScenarioEventListener sel)
        throws IllegalStateException
    {
        if ((sel != null) && (scenarioEventListener != null) && (scenarioEventListener != sel))
            throw new IllegalStateException("Listener already " + scenarioEventListener + ", wants " + sel);

        scenarioEventListener = sel;
    }

    /**
     * set the expiration time
     *
     * @param ex  the expiration time in milliseconds,
     *            same epoch as {@link java.util.Date#getTime()}
     */
    public void setExpiration(long ex)
    {
        expiration = ex;
    }

    /**
     * For games at the server, the owner (creator) of the game.
     * Will be the name of a player / server connection.
     * Even if the owner leaves the game, their name may be retained here.
     * @return the owner's player name, or null if {@link #setOwner(String)} was never called
     * @since 1.1.10
     */
    public String getOwner()
    {
        return ownerName;
    }

    /**
     * For games at the server, set the game owner (creator).
     * Will be the name of a player / server connection.
     * @param gameOwnerName The game owner's player name, or null to clear
     * @since 1.1.10
     * @throws IllegalStateException if <tt>gameOwnerName</tt> not null, but the game's owner is already set
     */
    public void setOwner(String gameOwnerName)
        throws IllegalStateException
    {
        if ((ownerName != null) && (gameOwnerName != null))
            throw new IllegalStateException("owner already set");
        ownerName = gameOwnerName;
    }

    /**
     * add a new player
     *
     * @param name  the player's name; must pass {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param pn    the player's number
     * @throws IllegalStateException if player is already sitting in
     *              another seat in this game, or if there are no open seats
     *              (based on seats[] == OCCUPIED, and game option "PL" or MAXPLAYERS)
     *               via {@link #getAvailableSeatCount()}
     * @throws IllegalArgumentException if name fails {@link SOCMessage#isSingleLineAndSafe(String)}.
     *           This exception was added in 1.1.07.
     */
    public void addPlayer(final String name, final int pn)
        throws IllegalStateException, IllegalArgumentException
    {
        if (! SOCMessage.isSingleLineAndSafe(name))
            throw new IllegalArgumentException("name");
        if (seats[pn] == VACANT)
        {
            if (0 == getAvailableSeatCount())
                throw new IllegalStateException("Game is full");
        }
        SOCPlayer already = getPlayer(name);
        if ((already != null) && (pn != already.getPlayerNumber()))
        {
            throw new IllegalStateException("Already sitting in this game");
        }

        players[pn].setName(name);
        seats[pn] = OCCUPIED;

        if ((gameState > NEW) && (gameState < OVER))
        {
            allOriginalPlayers = false;
        }
    }

    /**
     * remove a player from their seat.
     * Player's name becomes null.  {@link #isSeatVacant(int) isSeatVacant(playerNum)} becomes true.
     *<P>
     * <b>If they are the current player,</b>
     * call this and then call {@link #canEndTurn(int)}.
     * You'll need to then call {@link #endTurn()} or {@link #forceEndTurn()}.
     *
     * @param name  the player's name
     * @throws IllegalArgumentException if name isn't in this game.
     *           This exception was added in 1.1.07.
     */
    public void removePlayer(String name)
        throws IllegalArgumentException
    {
        SOCPlayer pl = getPlayer(name);
        if (pl == null)
            throw new IllegalArgumentException("name");
        pl.setName(null);
        seats[pl.getPlayerNumber()] = VACANT;

        //D.ebugPrintln("seats["+pl.getPlayerNumber()+"] = VACANT");
    }

    /**
     * @return true if the seat is VACANT
     *
     * @param pn the number of the seat
     * @see #getAvailableSeatCount()
     */
    public boolean isSeatVacant(int pn)
    {
        return (seats[pn] == VACANT);
    }

    /**
     * How many seats are vacant and available for players?
     * Based on {@link #isSeatVacant(int)}, and game
     * option "PL" (maximum players) or {@link #maxPlayers}.
     *
     * @return number of available vacant seats
     * @see #isSeatVacant(int)
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
     * locks a seat, so no one can take it
     *
     * @param pn the number of the seat
     */
    public void lockSeat(int pn)
    {
        seatLocks[pn] = LOCKED;
    }

    /**
     * unlocks a seat
     *
     * @param pn the number of the seat
     */
    public void unlockSeat(int pn)
    {
        seatLocks[pn] = UNLOCKED;
    }

    /**
     * @return true if this seat is locked
     *
     * @param pn the number of the seat
     */
    public boolean isSeatLocked(int pn)
    {
        return (seatLocks[pn] == LOCKED);
    }

    /**
     * @return the player object for a player id; never null if pn is in range
     *
     * @param pn  the player number, in range 0 to {@link #maxPlayers}-1
     */
    public SOCPlayer getPlayer(int pn)
    {
        return players[pn];
    }

    /**
     * Get the player sitting in this game with this name.
     * @return the player object for a player nickname.
     *   if there is no match, return null
     *
     * @param nn  the nickname
     */
    public SOCPlayer getPlayer(String nn)
    {
        if (nn != null)
        {
            for (int i = 0; i < maxPlayers; i++)
            {
                if ((! isSeatVacant(i)) && nn.equals(players[i].getName()))
                {
                    return players[i];
                }
            }
        }

        return null;
    }

    /**
     * @return the name of the game
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return this game's options ({@link SOCGameOption}), or null
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     * @see #getGameOptionIntValue(String)
     */
    public Hashtable<String, SOCGameOption> getGameOptions()
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
        if (opts == null)
            return false;
        else
            return opts.containsKey(optKey);
    }

    /**
     * Is this boolean-valued game option currently set to true?
     * @param optKey Name of a {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_BOOL OTYPE_BOOL},
     *               {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL}
     *               or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     * @return True if option is set, false if not set or not defined in this game's options
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #getGameOptionIntValue(String)
     * @see #getGameOptionStringValue(String)
     */
    public boolean isGameOptionSet(final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return isGameOptionSet(opts, optKey);
    }

    /**
     * Is this boolean-valued game option currently set to true?
     * @param opts A hashtable of {@link SOCGameOption}, or null
     * @param optKey Name of a {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_BOOL OTYPE_BOOL},
     *               {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL}
     *               or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     * @return True if option is set, false if not set or not defined in this set of options
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     * @see #getGameOptionIntValue(Hashtable, String)
     * @see #getGameOptionStringValue(Hashtable, String)
     */
    public static boolean isGameOptionSet(Hashtable<String, SOCGameOption> opts, final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        if (opts == null)
            return false;
        SOCGameOption op = opts.get(optKey);
        if (op == null)
            return false;
        return op.getBoolValue();
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
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     */
    public int getGameOptionIntValue(final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return getGameOptionIntValue(opts, optKey);
    }

    /**
     * What is this integer game option's current value?
     *<P>
     * Does not reference {@link SOCGameOption#getBoolValue()}, only the int value,
     * so this will return a value even if the bool value is false.
     * @param opts A hashtable of {@link SOCGameOption}, or null
     * @param optKey A {@link SOCGameOption} of type {@link SOCGameOption#OTYPE_INT OTYPE_INT},
     *               {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL},
     *               {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM}
     *               or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     * @return Option's current {@link SOCGameOption#getIntValue() intValue},
     *         or 0 if not defined in the set of options;
     *         OTYPE_ENUM's and _ENUMBOOL's choices give an intVal in range 1 to n.
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     * @see #getGameOptionIntValue(Hashtable, String, int, boolean)
     */
    public static int getGameOptionIntValue(Hashtable<String, SOCGameOption> opts, final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return getGameOptionIntValue(opts, optKey, 0, false);
    }

    /**
     * What is this integer game option's current value?
     *<P>
     * Can optionally reference {@link SOCGameOption#getBoolValue()}, not only the int value.
     * @param opts A hashtable of {@link SOCGameOption}, or null
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
     * @since 1.1.14
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     * @see #getGameOptionIntValue(Hashtable, String)
     */
    public static int getGameOptionIntValue(Hashtable<String, SOCGameOption> opts, final String optKey, final int defValue, final boolean onlyIfBoolSet)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        if (opts == null)
            return defValue;
        SOCGameOption op = opts.get(optKey);
        if (op == null)
            return defValue;
        if (onlyIfBoolSet && ! op.getBoolValue())
            return defValue;
        return op.getIntValue();
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
     */
    public String getGameOptionStringValue(final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        return getGameOptionStringValue(opts, optKey);
    }

    /**
     * What is this string game option's current value?
     * @param opts A hashtable of {@link SOCGameOption}, or null
     * @param optKey A {@link SOCGameOption} of type
     *               {@link SOCGameOption#OTYPE_STR OTYPE_STR}
     *               or {@link SOCGameOption#OTYPE_STRHIDE OTYPE_STRHIDE}
     * @return Option's current {@link SOCGameOption#getStringValue() getStringValue}
     *         or null if not defined in this set of options
     * @since 1.1.07
     * @see #isGameOptionDefined(String)
     * @see #isGameOptionSet(String)
     */
    public static String getGameOptionStringValue(Hashtable<String, SOCGameOption> opts, final String optKey)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        if (opts == null)
            return null;
        SOCGameOption op = opts.get(optKey);
        if (op == null)
            return null;
        return op.getStringValue();
    }

    /**
     * For use at server; lowest version of client which can connect to
     * this game (based on game options/features added in a given version),
     * or -1 if unknown or if this game has no opts.
     * Calculated by {@link SOCGameOption#optionsMinimumVersion(Hashtable)}.
     *<P>
     * For options where the minimum version changes with its current value, some
     * option version data is hardcoded in {@link SOCGameOption#getMinVersion(Hashtable)},
     * executed on the server with a newer version than an older client.  So, the
     * version returned may be too low when called at that client.  The server
     * will let the client know if it's too old to join or create a game due
     * to options.
     *
     * @return game version, in same integer format as {@link soc.util.Version#versionNumber()}.
     * @since 1.1.06
     */
    public int getClientVersionMinRequired()
    {
        return clientVersionMinRequired;
    }

    /**
     * @return whether this game was created by board reset of an earlier game
     */
    public boolean isBoardReset()
    {
        return isFromBoardReset;
    }

    /**
     * Get the game board.
     * When {@link #hasSeaBoard}, <tt>getBoard()</tt> can be cast to {@link SOCBoardLarge}.
     * @return the game board
     */
    public SOCBoard getBoard()
    {
        return board;
    }

    /**
     * @return the list of players
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
    protected void setPlayer(int pn, SOCPlayer pl)
    {
        if (pl != null)
            players[pn] = pl;
        else
            throw new IllegalArgumentException("null pl");
    }

    /**
     * @return the number of the current player
     */
    public int getCurrentPlayerNumber()
    {
        return currentPlayerNumber;
    }

    /**
     * Set the number of the current player, and check for winner.
     * If you want to update other game status, call {@link #updateAtTurn()} afterwards.
     * Called only at client - server instead calls {@link #endTurn()}
     * or {@link #advanceTurn()}.
     * Check for gamestate {@link #OVER} after calling setCurrentPlayerNumber.
     * This is needed because a player can win only during their own turn;
     * if they reach winning points ({@link #vp_winner} or more) during another
     * player's turn, they don't win immediately.  When it later becomes their turn,
     * and setCurrentPlayerNumber is called, gamestate may become {@link #OVER}.
     *
     * @param pn  the player number, or -1 permitted in state {@link #OVER}
     * @see #endTurn()
     * @see #checkForWinner()
     */
    public void setCurrentPlayerNumber(int pn)
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
     * The number of normal rounds (each player has 1 turn per round, after initial placements), including this round.
     *  This is 0 during initial piece placement, and 1 when the first player is about to
     *  roll dice for the first time.  It becomes 2 when that first player's turn begins again.
     *  @since 1.1.07
     */
    public int getRoundCount()
    {
        return roundCount;
    }

    /**
     * @return the current dice result
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
    public void setCurrentDice(int dr)
    {
        currentDice = dr;
    }

    /**
     * Current game state.  For general information about
     * what states are expected when, please see the javadoc for {@link #NEW}.
     *
     * @return the current game state
     * @see #isInitialPlacement()
     * @see #isSpecialBuilding()
     */
    public int getGameState()
    {
        return gameState;
    }

    /**
     * set the current game state.
     * If the new state is {@link #OVER}, and no playerWithWin yet determined, call checkForWinner.
     * For general information about what states are expected when,
     * please see the javadoc for {@link #NEW}.
     *<P>
     * This method is generally called at the client, due to messages from the server
     * based on the server's complete game data.
     *
     * @param gs  the game state
     * @see #checkForWinner()
     */
    public void setGameState(int gs)
    {
        if ((gs == PLAY) && (gameState == SPECIAL_BUILDING))
            oldGameState = PLAY1;  // Needed for isSpecialBuilding() to work at client
        else
            oldGameState = gameState;

        gameState = gs;
        if ((gameState == OVER) && (playerWithWin == -1))
            checkForWinner();
    }
   
    /**
     * If the game board was reset, get the old game state.
     *
     * @return the old game state
     * @throws IllegalStateException Game state must be RESET_OLD
     *    when called; during normal game play, oldGameState is private.
     */
    public int getResetOldGameState() throws IllegalStateException
    {
        if (gameState != RESET_OLD)
            throw new IllegalStateException
                ("Current state is not RESET_OLD: " + gameState);

        return oldGameState;
    }

    /**
     * Are we in the Initial Placement part of the game?
     * Includes game states {@link #START1A} - {@link #START3B}
     * and {@link #STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}.
     *
     * @return true if in Initial Placement
     * @since 1.1.12
     */
    public final boolean isInitialPlacement()
    {
        return (gameState >= START1A) && (gameState <= STARTS_WAITING_FOR_PICK_GOLD_RESOURCE);
    }

    /**
     * If true, this turn is being ended. Controller of game should call {@link #endTurn()}
     * whenever possible.  Usually set if we have called {@link #forceEndTurn()}, and
     * forced the current player to discard randomly, and are waiting for other players
     * to discard in gamestate {@link #WAITING_FOR_DISCARDS}.  Once all players have
     * discarded, the turn should be ended.
     * @see #forceEndTurn()
     */
    public boolean isForcingEndTurn()
    {
        return forcingEndTurn;
    }

    /**
     * @return the number of dev cards in the deck
     */
    public int getNumDevCards()
    {
        return numDevCards;
    }

    /**
     * set the number of dev cards in the deck
     *
     * @param  nd  the number of dev cards in the deck
     */
    public void setNumDevCards(int nd)
    {
        numDevCards = nd;
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
     * @throws IllegalStateException if the board has the v1 or v2 encoding
     */
    public void setPlayersLandHexCoordinates()
        throws IllegalStateException
    {
        final int bef = board.getBoardEncodingFormat();
        if (bef < SOCBoard.BOARD_ENCODING_LARGE)
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
        {
            currentPlayerNumber = maxPlayers - 1;
        }
        while (isSeatVacant (currentPlayerNumber))
        {
            --currentPlayerNumber;
            if (currentPlayerNumber < 0)
            {
                currentPlayerNumber = maxPlayers - 1;
            }
            if (currentPlayerNumber == prevCPN)
            {
                gameState = OVER;  // Looped around, no one is here
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

        //D.ebugPrintln("ADVANCE TURN FORWARDS");
        forcingEndTurn = false;
        currentPlayerNumber++;

        if (currentPlayerNumber == maxPlayers)
        {
            currentPlayerNumber = 0;
        }
        while (isSeatVacant (currentPlayerNumber))
        {
            ++currentPlayerNumber;
            if (currentPlayerNumber == maxPlayers)
            {
                currentPlayerNumber = 0;
            }
            if (currentPlayerNumber == prevCPN)
            {
                gameState = OVER;  // Looped around, no one is here
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
                && (gameState == PLAY)
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
     * Can this player place a ship on this edge?
     * The edge must return {@link SOCPlayer#isPotentialShip(int)}
     * and must not be adjacent to {@link SOCBoardLarge#getPirateHex()}.
     * Does not check game state, resources, or pieces remaining.
     * @param pl  Player
     * @param shipEdge  Edge to place a ship
     * @return true if this player's ship could be placed there
     * @since 2.0.00
     * @see #canMoveShip(int, int, int)
     */
    public boolean canPlaceShip(SOCPlayer pl, final int shipEdge)
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
     * Put this piece on the board and update all related game state.
     * May change current player and gamestate.
     * Calls {@link #checkForWinner()}; gamestate may become {@link #OVER}.
     *<P>
     * For example, if game state when called is {@link #START2A} (or {@link #START3A} in
     * some scenarios), this is their final initial settlement, so give
     * the player some resources, and call their {@link SOCPlayer#clearPotentialSettlements()}.
     * If {@link #hasSeaBoard}, you should check {@link SOCPlayer#getNeedToPickGoldHexResources()} != 0
     * after calling, to see if they placed next to a gold hex.
     *<P>
     * Calls {@link SOCBoard#putPiece(SOCPlayingPiece)} and each player's
     * {@link SOCPlayer#putPiece(SOCPlayingPiece, boolean) SOCPlayer.putPiece(pp, false)}.
     * Updates longest road if necessary.
     * Calls {@link #advanceTurnStateAfterPutPiece()}.
     * (player.putPiece may also score Special Victory Point(s), see below.)
     * If placing this piece reveals any {@link SOCBoardLarge#FOG_HEX fog hex}, does that first of all.
     *<P>
     * If the piece is a city, putPiece removes the settlement there.
     *<P>
     *<b>Note:</b> Because <tt>pp</tt> is not checked for validity, please call
     * methods such as {@link SOCPlayer#canPlaceSettlement(int)}
     * to verify <tt>pp</tt> before calling this method, and also check
     * {@link #getGameState()} to ensure that piece type can be placed now.
     * For ships, call {@link #canPlaceShip(SOCPlayer, int)} to check
     * the potentials and pirate ship location.
     *<P>
     * For some scenarios on the {@link SOCGame#hasSeaBoard large sea board}, placing
     * a settlement in a new Land Area may award the player a Special Victory Point (SVP).
     * This method will increment {@link SOCPlayer#getSpecialVP()}
     * and set the player's {@link SOCScenarioPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA} flag.
     *<P>
     * During {@link #isDebugFreePlacement()}, the gamestate is not changed,
     * unless the current player gains enough points to win.
     *
     * @param pp the piece to put on the board; coordinates are not checked for validity
     */
    public void putPiece(SOCPlayingPiece pp)
    {
        putPieceCommon(pp, false);
    }

    /**
     * Put a piece or temporary piece on the board, and update all related game state.
     * Update player potentials, longest road, etc.
     * Common to {@link #putPiece(SOCPlayingPiece)} and {@link #putTempPiece(SOCPlayingPiece)}.
     * See {@link #putPiece(SOCPlayingPiece)} javadoc for more information on what putPieceCommon does.
     *
     * @param pp  The piece to put on the board; coordinates are not checked for validity
     * @param isTempPiece  Is this a temporary piece?  If so, do not change current
     *                     player or gamestate, or call our {@link SOCScenarioEventListener}.
     * @since 1.1.14
     */
    private void putPieceCommon(SOCPlayingPiece pp, final boolean isTempPiece)
    {
        final int coord = pp.getCoordinates();

        /**
         * on large board, look for fog and reveal its hex if we're
         * placing a road or ship touching the fog hex's corner.
         * During initial placement, a settlement could reveal up to 3.
         */
        if (hasSeaBoard && isAtServer && ! (pp instanceof SOCVillage))
        {
            if (pp instanceof SOCRoad)
            {
                final int[] endHexes = ((SOCBoardLarge) board).getAdjacentHexesToEdgeEnds(coord);
                putPieceCommon_checkFogHexes(endHexes, false);
            }
            else if (isInitialPlacement())
            {
                final Collection<Integer> hexColl = board.getAdjacentHexesToNode(coord);
                int[] seHexes = new int[hexColl.size()];
                int i;
                Iterator<Integer> hi;
                for (i = 0, hi = hexColl.iterator(); hi.hasNext(); ++i)
                    seHexes[i] = hi.next();
                putPieceCommon_checkFogHexes(seHexes, true);                
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

        if (pp instanceof SOCVillage)
        {
            return;  // <--- Early return: Piece is part of board initial layout, not player info ---
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
            SOCSettlement se = new SOCSettlement(ppPlayer, coord, board);

            for (int i = 0; i < maxPlayers; i++)
            {
                players[i].removePiece(se, pp);
            }

            board.removePiece(se);
        }

        /**
         * if this their final initial settlement, give the player some resources.
         * (skip for temporary pieces)
         */
        if ((! isTempPiece)
            && (pieceType == SOCPlayingPiece.SETTLEMENT)
            && ((gameState == START2A) || (gameState == START3A)))
        {
            final boolean init3 = isGameOptionDefined(SOCGameOption.K_SC_3IP);
            final int lastInitSettle = init3 ? START3A : START2A;
            if ( (gameState == lastInitSettle)
                 || (debugFreePlacementStartPlaced
                     && (ppPlayer.getPieces().size() == (init3 ? 5 : 3))) )
            {
                SOCResourceSet resources = new SOCResourceSet();
                Vector<Integer> hexes = board.getAdjacentHexesToNode(coord);
                int goldHexAdjacent = 0;

                for (Integer hex : hexes)
                {
                    final int hexCoord = hex.intValue();

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
         * if this their final initial road or ship, clear potentialSettlements.
         * (skip for temporary pieces)
         */
        if ((! isTempPiece)
            && (pp instanceof SOCRoad)
            && ((gameState == START2B) || (gameState == START3B)))
        {
            final boolean init3 = isGameOptionDefined(SOCGameOption.K_SC_3IP);
            final int lastInitState = init3 ? START3B : START2B;
            if ((gameState == lastInitState)
                || (debugFreePlacementStartPlaced
                    && (ppPlayer.getPieces().size() == (init3 ? 6 : 4))))
            {
                ppPlayer.clearPotentialSettlements();
            }
        }

        /**
         * update which player has longest road or trade route
         */
        if (pieceType != SOCPlayingPiece.CITY)
        {
            if (pp instanceof SOCRoad)
            {
                /**
                 * the affected player is the one who build the road or ship
                 */
                updateLongestRoad(ppPlayer.getPlayerNumber());
            }
            else
            {
                /**
                 * this is a settlement, check if it cut anyone else's road or trade route
                 */
                int[] roads = new int[maxPlayers];
                for (int i = 0; i < maxPlayers; i++)
                {
                    roads[i] = 0;
                }

                Vector<Integer> adjEdges = board.getAdjacentEdgesToNode(coord);

                for (Integer adj : adjEdges)
                {
                    final int adjEdge = adj.intValue();

                    /**
                     * look for other player's roads adjacent to this node
                     */
                    for (SOCRoad road : board.getRoads())
                    {
                        if (adjEdge == road.getCoordinates())
                        {
                            roads[road.getPlayerNumber()]++;
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
                    if ((i != ppPlayer.getPlayerNumber()) && (roads[i] == 2))
                    {
                        updateLongestRoad(i);

                        /**
                         * check to see if this created a tie
                         */
                        break;
                    }
                }
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
        if (pp.getType() == SOCPlayingPiece.SHIP)
        {
            placedShipsThisTurn.add(new Integer(coord));
        }

        /**
         * check if the game is over
         */
        if (oldGameState != SPECIAL_BUILDING)
            checkForWinner();

        /**
         * update the state of the game, and possibly current player
         */
        if (active && ! debugFreePlacement)
        {
            advanceTurnStateAfterPutPiece();
        }
    }

    /**
     * On the large sea board, look for and reveal any adjacent fog hex,
     * if we're placing a road or ship touching the fog hex's corner node.
     * Reveal it before placing the new piece, so it's easier for
     * players and bots to updatePotentials (their data about the
     * board reachable through their roads/ships).
     * Each revealed fog hex triggers {@link SOCScenarioGameEvent#SGE_FOG_HEX_REVEALED}
     * and gives the current player that resource (if not desert or water or gold).
     * The server should send the clients messages to reveal the hex
     * and give the resource to that player.
     *<P>
     * During initial placement, placing a settlement could reveal up to 3 hexes.
     * Called only at server.
     *
     * @param hexCoords  Hex coordinates to check type for {@link SOCBoardLarge#FOG_HEX}
     * @param initialSettlement  Are we checking for initial settlement placement?
     *     If so, keep checking after finding a fog hex.
     * @since 2.0.00
     */
    private final void putPieceCommon_checkFogHexes(final int[] hexCoords, final boolean initialSettlement)
    {
        for (int i = 0; i < hexCoords.length; ++i)
        {
            final int hexCoord = hexCoords[i];
            if ((hexCoord != 0) && (board.getHexTypeFromCoord(hexCoord) == SOCBoardLarge.FOG_HEX))
            {
                ((SOCBoardLarge) board).revealFogHiddenHex(hexCoord);
                if (currentPlayerNumber != -1)
                {
                    final int res = board.getHexTypeFromNumber(hexCoord);
                    if ((res >= SOCResourceConstants.CLAY) && (res <= SOCResourceConstants.WOOD))
                        players[currentPlayerNumber].getResources().add(1, res);
                }

                if (scenarioEventListener != null)
                    scenarioEventListener.gameEvent
                        (this, SOCScenarioGameEvent.SGE_FOG_HEX_REVEALED, new Integer(hexCoord));

                if (! initialSettlement)
                    break;
                    // No need to keep looking, because only one end of the road or ship's
                    // edge is new; player was already at the other end, so it can't be fog.
            }
        }
    }

    /**
     * After placing a piece on the board, update the state of
     * the game, and possibly current player, for play to continue.
     *<P>
     * Also used in {@link #forceEndTurn()} to continue the game
     * after a cancelled piece placement in {@link #START1A}..{@link #START3B} .
     * If the current player number changes here, {@link #isForcingEndTurn()} is cleared.
     *<P>
     * In {@link #START2B} or {@link #START3B}, calls {@link #updateAtTurn()} after last initial road placement.
     *
     * @return true if the turn advances, false if all players have left and
     *          the gamestate has been changed here to {@link #OVER}.
     */
    private boolean advanceTurnStateAfterPutPiece()
    {
        //D.ebugPrintln("CHANGING GAME STATE FROM "+gameState);
        switch (gameState)
        {
        case START1A:
            gameState = START1B;

            break;

        case START1B:
            {
                int tmpCPN = currentPlayerNumber + 1;
                if (tmpCPN >= maxPlayers)
                {
                    tmpCPN = 0;
                }
                while (isSeatVacant (tmpCPN))
                {
                    ++tmpCPN;
                    if (tmpCPN >= maxPlayers)
                    {
                        tmpCPN = 0;
                    }
                    if (tmpCPN == currentPlayerNumber)
                    {
                        gameState = OVER;  // Looped around, no one is here
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
            if (hasSeaBoard && (players[currentPlayerNumber].getNeedToPickGoldHexResources() > 0))
                gameState = STARTS_WAITING_FOR_PICK_GOLD_RESOURCE;
            else
                gameState = START2B;
            break;

        case START2B:
            {
                int tmpCPN = currentPlayerNumber - 1;

                // who places next? same algorithm as advanceTurnBackwards.
                if (tmpCPN < 0)
                {
                    tmpCPN = maxPlayers - 1;
                }
                while (isSeatVacant (tmpCPN))
                {
                    --tmpCPN;
                    if (tmpCPN < 0)
                    {
                        tmpCPN = maxPlayers - 1;
                    }
                    if (tmpCPN == currentPlayerNumber)
                    {
                        gameState = OVER;  // Looped around, no one is here
                        return false;
                    }
                }
    
                if (tmpCPN == lastPlayerNumber)
                {
                    // All have placed their second settlement/road.
                    if (! isGameOptionSet(SOCGameOption.K_SC_3IP))
                    {
                        // Begin play.
                        // Player number is unchanged; "virtual" endTurn here.
                        // Don't clear forcingEndTurn flag, if it's set.
                        gameState = PLAY;
                        updateAtTurn();
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
            if (hasSeaBoard && (players[currentPlayerNumber].getNeedToPickGoldHexResources() > 0))
                gameState = STARTS_WAITING_FOR_PICK_GOLD_RESOURCE;
            else
                gameState = START3B;
            break;

        case START3B:
            {
                // who places next? same algorithm as advanceTurn.
                int tmpCPN = currentPlayerNumber + 1;
                if (tmpCPN >= maxPlayers)
                {
                    tmpCPN = 0;
                }
                while (isSeatVacant (tmpCPN))
                {
                    ++tmpCPN;
                    if (tmpCPN >= maxPlayers)
                    {
                        tmpCPN = 0;
                    }
                    if (tmpCPN == currentPlayerNumber)
                    {
                        gameState = OVER;  // Looped around, no one is here
                        return false;
                    }
                }
    
                if (tmpCPN == firstPlayerNumber)
                {
                    // All have placed their third settlement/road.
                    // Begin play.  The first player to roll is firstPlayerNumber.
                    // "virtual" endTurn here.
                    // Don't clear forcingEndTurn flag, if it's set.
                    currentPlayerNumber = firstPlayerNumber;
                    gameState = PLAY;
                    updateAtTurn();
                }
                else
                {
                    if (advanceTurn())
                        gameState = START3A;
                }
            }
            break;

        case PLACING_ROAD:
        case PLACING_SETTLEMENT:
        case PLACING_CITY:
        case PLACING_SHIP:
            if (oldGameState != SPECIAL_BUILDING)
            {
                gameState = PLAY1;
            }
            else
            {
                gameState = SPECIAL_BUILDING;
            }

            break;

        case PLACING_FREE_ROAD1:
            gameState = PLACING_FREE_ROAD2;

            break;

        case PLACING_FREE_ROAD2:
            gameState = oldGameState;

            break;
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
     * @return  The ship, if the player can move the ship now; null otherwise
     * @see canMoveShip(int, int, int)
     * @since 2.0.00
     */
    public SOCShip canMoveShip(final int pn, final int fromEdge)
    {
        if (movedShipThisTurn || ! (hasSeaBoard && (currentPlayerNumber == pn) && (gameState == PLAY1)))
            return null;
        if (placedShipsThisTurn.contains(new Integer(fromEdge)))
            return null;

        // check fromEdge vs. pirate hex
        {
            SOCBoardLarge bL = (SOCBoardLarge) board;
            final int ph = bL.getPirateHex();
            if ((ph != 0) && bL.isEdgeAdjacentToHex(fromEdge, ph))
                return null;
        }

        final SOCPlayer pl = players[pn];
        final SOCRoad pieceAtFrom = pl.getRoadOrShip(fromEdge);
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
     * Only the ship at the newer end of an open trade route can be moved.
     * So, to move a ship, one of <tt>fromEdge</tt>'s end nodes must be
     * clear: No settlement or city, and no other adjacent ship on the other
     * side of the node.
     *<P>
     * The new location <tt>toEdge</tt> must also be a potential ship location,
     * even if <tt>fromEdge</tt> was unoccupied; calls
     * {@link SOCPlayer#isPotentialShip(int, int) pn.isPotentialShip(toEdge, fromEdge)}
     * to check that.
     *<P>
     * You cannot move a ship to or from an edge of the pirate ship's hex.
     *<P>
     * Trade routes can branch, so it may be that more than one ship
     * could be moved.  The game limits players to one move per turn.
     *
     * @param pn   Player number
     * @param fromEdge  Edge coordinate to move the ship from; must contain this player's ship.
     * @param toEdge    Edge coordinate to move to; must be different than <tt>fromEdge</tt>.
     *            Checks {@link SOCPlayer#isPotentialShip(int) players[pn].isPotentialShip(toEdge)}.
     * @return  The ship, if the player can move the ship now; null otherwise
     * @see #canMoveShip(int, int)
     * @see #moveShip(SOCShip, int)
     * @since 2.0.00
     */
    public SOCShip canMoveShip(final int pn, final int fromEdge, final int toEdge)
    {
        if (fromEdge == toEdge)
            return null;
        final SOCPlayer pl = players[pn];
        if (! pl.isPotentialShip(toEdge, fromEdge))
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
     * Calls {@link #undoPutPieceCommon(SOCPlayingPiece) undoPutPieceCommon(sh, false)}
     * and {@link #putPiece(SOCPlayingPiece)}.
     * Updates longest trade route.
     * Not for use with temporary pieces.
     *<P>
     *<b>Note:</b> Because <tt>sh</tt> and <tt>toEdge</tt>
     * are not checked for validity, please call
     * {@link #canMoveShip(int, int, int)} before calling this method.
     *<P>
     * The call to putPiece incorrectly adds the moved ship's
     * new location to <tt>placedShipsThisTurn</tt>, but since
     * we can only move 1 ship per turn, the add is harmless.
     *<P>
     * During {@link #isDebugFreePlacement()}, the gamestate is not changed,
     * unless the current player gains enough points to win.
     *
     * @param sh the ship to move on the board; its coordinate must be
     *           the edge to move from. Must not be a temporary ship.
     * @param toEdge    Edge coordinate to move to
     * @since 2.0.00
     */
    public void moveShip(SOCShip sh, final int toEdge)
    {
        undoPutPieceCommon(sh, false);
        SOCShip sh2 = new SOCShip(sh.getPlayer(), toEdge, board);
        putPiece(sh2);  // calls checkForWinner, etc
        movedShipThisTurn = true;
    }

    /**
     * undo the putting of a temporary or initial piece
     * or a ship being moved.
     * If state is START2B or START3B and resources were given, they will be returned.
     *
     * @param pp  the piece to remove from the board
     * @param isTempPiece  Is this a temporary piece?  If so, do not call the
     *                     game's {@link SOCScenarioEventListener}.
     */
    protected void undoPutPieceCommon(SOCPlayingPiece pp, final boolean isTempPiece)
    {
        //D.ebugPrintln("@@@ undoPutTempPiece "+pp);
        board.removePiece(pp);

        //
        // call undoPutPiece() on every player so that
        // they can update their potentials
        //
        for (int i = 0; i < maxPlayers; i++)
        {
            players[i].undoPutPiece(pp);   // If state START2B or START3B, will also zero resources
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
        undoPutPieceCommon(pp, true);

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
     */
    public void undoPutInitSettlement(SOCPlayingPiece pp)
    {
        if ((gameState != START1B) && (gameState != START2B) && (gameState != START3B))
            throw new IllegalStateException("Cannot remove at this game state: " + gameState);
        if (pp.getType() != SOCPlayingPiece.SETTLEMENT)
            throw new IllegalArgumentException("Not a settlement: type " + pp.getType());
        if (pp.getCoordinates() != pp.getPlayer().getLastSettlementCoord())
            throw new IllegalArgumentException("Not coordinate of last settlement");

        undoPutPieceCommon(pp, false);  // Will also zero resources via player.undoPutPiece

        if (gameState == START1B)
            gameState = START1A;
        else if (gameState == START2B)
            gameState = START2A;
        else // gameState == START3B
            gameState = START3A;
    }

    /**
     * do the things involved in starting a game:
     * shuffle the tiles and cards,
     * make a board,
     * choose first player.
     * gameState becomes {@link #START1A}.
     *<P>
     * Called only at server, not client.
     */
    public void startGame()
    {
        isAtServer = true;
        pendingMessagesOut = new ArrayList<Object>();

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
            HashSet<Integer> psList = ((SOCBoardLarge) board).getLegalAndPotentialSettlements();
            final HashSet<Integer>[] las = ((SOCBoardLarge) board).getLandAreasLegalNodes();
            for (int i = 0; i < maxPlayers; ++i)
                players[i].setPotentialAndLegalSettlements(psList, true, las);
        }

        /**
         * shuffle the development cards
         */
        if (maxPlayers > 4)
            devCardDeck = new int[NUM_DEVCARDS_6PLAYER];
        else
            devCardDeck = new int[NUM_DEVCARDS_STANDARD];

        int i;
        int j;

        if (isGameOptionSet("DH"))  // House Rules dev cards
        {
            // Some knights become other card types
            for (i = 0; i < 4; i++)
                devCardDeck[i] = SOCDevCardConstants.SWAP;
            for (i = 4; i < 8; i++)
                devCardDeck[i] = SOCDevCardConstants.DESTROY;
            for (i = 8; i < 14; i++)
                devCardDeck[i] = SOCDevCardConstants.KNIGHT;

        } else {

            // Standard set of of knights
            for (i = 0; i < 14; i++)
            {
                devCardDeck[i] = SOCDevCardConstants.KNIGHT;
            }
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

        devCardDeck[20] = SOCDevCardConstants.CAP;
        devCardDeck[21] = SOCDevCardConstants.LIB;
        devCardDeck[22] = SOCDevCardConstants.UNIV;
        devCardDeck[23] = SOCDevCardConstants.TEMP;
        devCardDeck[24] = SOCDevCardConstants.TOW;

        if (maxPlayers > 4)
        {
            for (i = 25; i < 31; i++)
            {
                devCardDeck[i] = SOCDevCardConstants.KNIGHT;
            }
            devCardDeck[31] = SOCDevCardConstants.ROADS;
            devCardDeck[32] = SOCDevCardConstants.MONO;
            devCardDeck[33] = SOCDevCardConstants.DISC;
        }

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

        allOriginalPlayers = true;
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
     * Sets who the first player is.
     * Based on <code>pn</code> and on vacant seats, also recalculates lastPlayer.
     *
     * @param pn  the seat number of the first player, or -1 if not set yet
     */
    public void setFirstPlayer(int pn)
    {
        firstPlayerNumber = pn;
        if (pn < 0)  // -1 == not set yet; use <0 to be defensive in while-loop
        {
            lastPlayerNumber = -1;
            return;
        }
        lastPlayerNumber = pn - 1;

        if (lastPlayerNumber < 0)
        {
            lastPlayerNumber = maxPlayers - 1;
        }
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
                D.ebugPrintln("** setFirstPlayer: Should not happen: All seats blank");
                lastPlayerNumber = -1;
                break;
            }
        }
    }

    /**
     * @return the seat number of the first player
     */
    public int getFirstPlayer()
    {
        return firstPlayerNumber;
    }

    /**
     * Can this player end the current turn?
     *<P>
     * In some states, the current player can't end their turn yet
     * (such as needing to move the robber, or choose resources for a
     *  year-of-plenty card, or discard if a 7 is rolled).
     * 
     * @param pn  player number of the player who wants to end the turn
     * @return true if okay for this player to end the turn
     *    (They are current player, game state is {@link #PLAY1} or {@link #SPECIAL_BUILDING})
     *
     * @see #endTurn()
     * @see #forceEndTurn()
     */
    public boolean canEndTurn(int pn)
    {
        if (currentPlayerNumber != pn)
        {
            return false;
        }
        else if ((gameState != PLAY1) && (gameState != SPECIAL_BUILDING))
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    /**
     * end the turn for the current player, and check for winner.
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
     *<P>
     * In 1.1.09 and later, player is allowed to Special Build at start of their
     * own turn, only if they haven't yet rolled or played a dev card.
     * To do so, call {@link #askSpecialBuild(int, boolean)} and then {@link #endTurn()}.
     *
     * @see #checkForWinner()
     * @see #forceEndTurn()
     * @see #isForcingEndTurn()
     */
    public void endTurn()
    {
        if (! advanceTurnToSpecialBuilding())
        {
            // "Normal" end-turn:

            gameState = PLAY;
            if (! advanceTurn())
                return;
        }

        updateAtTurn();
        players[currentPlayerNumber].setPlayedDevCard(false);  // client calls this in handleSETPLAYEDDEVCARD

        if ((players[currentPlayerNumber].getTotalVP() >= vp_winner) || hasScenarioWinCondition)
            checkForWinner();  // Will do nothing during Special Building Phase
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
     *<LI> If game state is {@link #PLAY}, increment turnCount (and roundCount if necessary).
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
            placedShipsThisTurn.clear();
        }

        if (gameState == PLAY)
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
     *       - Have forced current player to discard randomly, must now
     *         wait for other players to discard.
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
     * See also <tt>SOCServer.forceEndGameTurn, SOCServer.endGameTurnOrForce</tt>.
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
     */
    public SOCForceEndTurnResult forceEndTurn()
        throws IllegalStateException
    {
        if ((gameState < START1A) || (gameState >= OVER))
            throw new IllegalStateException("Game not active: state " + gameState);

        forcingEndTurn = true;

        switch (gameState)
        {
        case START1A:
        case START1B:
        case START3A:
        case START3B:
            return forceEndTurnStartState(true);
                // FORCE_ENDTURN_UNPLACE_START_ADV
                // or FORCE_ENDTURN_UNPLACE_START_ADVBACK

        case START2A:
        case START2B:
            return forceEndTurnStartState(false);
                // FORCE_ENDTURN_UNPLACE_START_ADVBACK
                // or FORCE_ENDTURN_UNPLACE_START_TURN

        case PLAY:
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
            cancelBuildRoad(currentPlayerNumber);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE, ROAD_SET);

        case PLACING_SETTLEMENT:
            cancelBuildSettlement(currentPlayerNumber);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE, SETTLEMENT_SET);

        case PLACING_CITY:
            cancelBuildCity(currentPlayerNumber);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE, CITY_SET);

        case PLACING_ROBBER:
            {
                boolean isFromDevCard = placingRobberForKnightCard;
                gameState = PLAY1;
                if (isFromDevCard)
                {
                    placingRobberForKnightCard = false;
                    players[currentPlayerNumber].getDevCards().add(1, SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT);
                }
                return new SOCForceEndTurnResult
                    (SOCForceEndTurnResult.FORCE_ENDTURN_UNPLACE_ROBBER,
                     isFromDevCard ? SOCDevCardConstants.KNIGHT : -1);
            }

        case PLACING_FREE_ROAD1:
        case PLACING_FREE_ROAD2:
            gameState = PLAY1;
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_RSRC_RET_UNPLACE);

        case WAITING_FOR_DISCARDS:
            return forceEndTurnChkDiscardOrGain(currentPlayerNumber, true);  // sets gameState, discards randomly

        case WAITING_FOR_CHOICE:
            gameState = PLAY1;
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE);

        case WAITING_FOR_DISCOVERY:
            gameState = PLAY1;
            players[currentPlayerNumber].getDevCards().add(1, SOCDevCardSet.OLD, SOCDevCardConstants.DISC);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE,
                 SOCDevCardConstants.DISC);

        case WAITING_FOR_MONOPOLY:
            gameState = PLAY1;
            players[currentPlayerNumber].getDevCards().add(1, SOCDevCardSet.OLD, SOCDevCardConstants.MONO);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE,
                 SOCDevCardConstants.MONO);

        // TODO STARTS_WAITING_FOR_PICK_GOLD_RESOURCE
        case WAITING_FOR_PICK_GOLD_RESOURCE:
            return forceEndTurnChkDiscardOrGain(currentPlayerNumber, false);  // sets gameState, picks randomly

        case WAITING_FOR_DESTROY:
            gameState = PLAY1;
            players[currentPlayerNumber].getDevCards().add(1, SOCDevCardSet.OLD, SOCDevCardConstants.DESTROY);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE,
                 SOCDevCardConstants.DESTROY);

        case WAITING_FOR_SWAP:
            gameState = PLAY1;
            players[currentPlayerNumber].getDevCards().add(1, SOCDevCardSet.OLD, SOCDevCardConstants.SWAP);
            return new SOCForceEndTurnResult
                (SOCForceEndTurnResult.FORCE_ENDTURN_LOST_CHOICE,
                 SOCDevCardConstants.SWAP);

        default:
            throw new IllegalStateException("Internal error in force, un-handled gamestate: "
                    + gameState);
        }

        // Always returns within switch
    }

    /**
     * Special forceEndTurn() treatment for start-game states.
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
     */
    private SOCForceEndTurnResult forceEndTurnStartState(final boolean advTurnForward)
    {
        final int cpn = currentPlayerNumber;
        int cancelResType;  // Turn result type
        boolean updateFirstPlayer, updateLastPlayer;  // are we forcing the very first player's (or last player's) turn?
        updateFirstPlayer = (cpn == firstPlayerNumber);
        updateLastPlayer = (cpn == lastPlayerNumber);

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
                if (! isGameOptionSet(SOCGameOption.K_SC_3IP))
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

        // update these so the game knows when to stop initial placement
        if (updateFirstPlayer)
            firstPlayerNumber = currentPlayerNumber;
        if (updateLastPlayer)
            lastPlayerNumber = currentPlayerNumber;

        return new SOCForceEndTurnResult(cancelResType, updateFirstPlayer, updateLastPlayer);
    }

    /**
     * Randomly discard from this player's hand by calling {@link #discard(int, SOCResourceSet)},
     * or gain random resources by calling {@link #pickGoldHexResources(int, SOCResourceSet)}.
     * Then look at other players' hand size. If no one else must discard or pick,
     * ready to end turn, set state {@link #PLAY1}.
     * Otherwise, must wait for them; if so,
     * set game state ({@link #WAITING_FOR_DISCARDS} or {@link #WAITING_FOR_PICK_GOLD_RESOURCE}).
     * When called, assumes {@link #isForcingEndTurn()} flag is already set.
     *
     * @param pn Player number to force to randomly discard or gain
     * @param isDiscard  True to discard resources, false to gain
     * @return The force result, including any discarded resources.
     *         Type will be {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD}
     *         or {@link SOCForceEndTurnResult#FORCE_ENDTURN_RSRC_DISCARD_WAIT}.
     */
    private SOCForceEndTurnResult forceEndTurnChkDiscardOrGain(final int pn, final boolean isDiscard)
    {
        // select random cards, and discard or gain
        SOCResourceSet picks = new SOCResourceSet();
        SOCResourceSet hand = players[pn].getResources();
        if (isDiscard)
        {
            discardOrGainPickRandom(hand, hand.getTotal() / 2, true, picks, rand);
            discard(pn, picks);  // Checks for other discarders, sets gameState
        } else {
            // TODO for init place (STARTS_WAITING_FOR_PICK_GOLD_RESOURCE):
            //    even more randomly pick one, then set state START2A or START2B or START3A or START3B;
            //    then call forceEnd again; maybe not here but after here
            discardOrGainPickRandom(hand, players[pn].getNeedToPickGoldHexResources(), false, picks, rand);
            pickGoldHexResources(pn, picks);  // Checks for other players, sets gameState
            // TODO - what if not waiting for current pl to pick gains, but other pl?
            //   (discard could be same scenario)
            // TODO return type; ones below are for discards
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
     * Choose discards at random; does not actually discard anything.
     * For discards, randomly choose from contents of <tt>fromHand</tt>.
     * For gains, randomly choose resource types least plentiful in <tt>fromHand</tt>.
     *
     * @param fromHand     Discard from this set; this set's contents will be changed by calling this method
     * @param numToPick    This many must be discarded or added
     * @param picks        Add the picked resources to this set (typically new and empty when called)
     * @param rand         Source of random
     * @param isDiscard    True to discard resources, false to gain
     * @throws IllegalArgumentException if <tt>isDiscard</tt> and
     *     <tt>numDiscards</tt> &gt; {@link SOCResourceSet#getKnownTotal() fromHand.getKnownTotal()}
     */
    public static void discardOrGainPickRandom
        (SOCResourceSet fromHand, int numToPick, final boolean isDiscard, SOCResourceSet picks, Random rand)
        throws IllegalArgumentException
    {
        // resources, to be shuffled and chosen from;
        // discards from fromHand, or possible new picks.
        Vector<Integer> tempHand = new Vector<Integer>(16);

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
                    tempHand.addElement(Integer.valueOf(rsrcType));
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
            // lowestNum until we've found at least numDiscards resources.
            int toAdd = numToPick;
            Vector<Integer> alreadyPicked = new Vector<Integer>();
            do
            {
                for (int rsrcType = SOCResourceConstants.CLAY;
                         rsrcType <= SOCResourceConstants.WOOD; ++rsrcType)
                {
                    final int num = fromHand.getAmount(rsrcType);
                    if (num == lowestNum)
                    {
                        tempHand.addElement(new Integer(rsrcType));
                        --toAdd;  // might go below 0, that's okay: we'll shuffle.
                    }
                    else if (num < lowestNum)
                    {
                        // Already added in previous iterations.
                        // Add more of this type only if we need more.
                        alreadyPicked.addElement(Integer.valueOf(rsrcType));
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
            picks.add(1, tempHand.elementAt(idx).intValue());
            tempHand.removeElementAt(idx);
        }
    }

    /**
     * Force this non-current player to discard randomly.  Used at server when a
     * player must discard and they lose connection while the game is waiting for them.
     *<P>
     * On return, gameState will be:
     *<UL>
     * <LI> {@link #WAITING_FOR_DISCARDS} if other players still must discard
     * <LI> {@link #WAITING_FOR_PICK_GOLD_RESOURCE} if other players stll must pick their resources
     * <LI> {@link #PLAY1} if everyone has discarded, and {@link #isForcingEndTurn()} is set
     * <LI> {@link #PLACING_ROBBER} if everyone has discarded, and {@link #isForcingEndTurn()} is not set
     *</UL>
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
     */
    public SOCResourceSet playerDiscardRandom(final int pn, final boolean isDiscard)
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
        else if (gameState != PLAY)
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
     * gameState becomes either {@link #WAITING_FOR_DISCARDS},
     * {@link #WAITING_FOR_ROBBER_OR_PIRATE}, or {@link #PLACING_ROBBER}.
     *<br>
     * Checks game option N7: Roll no 7s during first # rounds
     *<br>
     * For scenario option {@link SOCGameOption#K_SC_CLVI}, calls
     * {@link SOCBoardLarge#distributeClothFromRoll(SOCGame, int)}.
     * Cloth are worth VP, so check for game state {@link #OVER}
     * if results include {@link RollResult#cloth}.
     *<P>
     * Called at server only.
     * @return The roll results: Dice numbers, and any scenario-specific results
     *         such as {@link RollResult#cloth}.  The game reuses the same instance
     *         each turn, so its field contents will change when <tt>rollDice()</tt>
     *         is called again.
     */
    public RollResult rollDice()
    {
        // N7: Roll no 7s during first # rounds.
        //     Use > not >= because roundCount includes current round
        final boolean okToRoll7 =
            (! isGameOptionSet("N7")) || (roundCount > getGameOptionIntValue("N7"));

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

        /**
         * handle the seven case
         */
        if (currentDice == 7)
        {
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
                placingRobberForKnightCard = false;
                oldGameState = PLAY1;
                if (canChooseMovePirate())
                {
                    gameState = WAITING_FOR_ROBBER_OR_PIRATE;
                } else {
                    robberyWithPirateNotRobber = false;
                    gameState = PLACING_ROBBER;
                }
            }

            currentRoll.cloth = null;
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

            /**
             * distribute cloth from villages
             */
            if (hasSeaBoard && isGameOptionSet(SOCGameOption.K_SC_CLVI))
            {
                // distribute will usually return null
                final int[] rollCloth = ((SOCBoardLarge) board).distributeClothFromRoll(this, currentDice);
                currentRoll.cloth = rollCloth;
                if (rollCloth != null)
                    checkForWinner();
            }

            /**
             * done, next game state
             */
            if (gameState != OVER)
            {
                if (! anyGoldHex)
                    gameState = PLAY1;
                else
                    gameState = WAITING_FOR_PICK_GOLD_RESOURCE;
            }
        }

        currentRoll.diceA = die1;
        currentRoll.diceB = die2;
        return currentRoll;
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
    private SOCResourceSet getResourcesGainedFromRoll(SOCPlayer player, final int roll)
    {
        SOCResourceSet resources = new SOCResourceSet();
        SOCResourceSet missedResources = new SOCResourceSet();
        final int robberHex = board.getRobberHex();

        /**
         * check the hexes touching settlements
         */
        getResourcesGainedFromRollPieces(roll, resources, missedResources, robberHex, player.getSettlements(), 1);

        /**
         * check the hexes touching cities
         */
        getResourcesGainedFromRollPieces(roll, resources, missedResources, robberHex, player.getCities(), 2);
        
        if (missedResources.getTotal() > 0)
        {
            //System.out.println
            // ("SOCGame#getResourcesGainedFromRoll Player ["+player.getName()+"] would have gotten resources, but robber stole them: "+missedResources.toString());
        }

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
     * @param sEnum  Enumeration of the player's {@link SOCPlayingPiece}s;
     *             should be either {@link SOCSettlement}s or {@link SOCCity}s
     * @param incr   Add this many resources (1 or 2) per playing piece
     * @since 2.0.00
     */
    private final void getResourcesGainedFromRollPieces
        (int roll, SOCResourceSet resources, SOCResourceSet missedResources,
         int robberHex, Collection<? extends SOCPlayingPiece> sEnum, int incr)
    {
        for (SOCPlayingPiece sc : sEnum)
        {
            Collection<Integer> hexes = board.getAdjacentHexesToNode(sc.getCoordinates());

            for (Integer hex : hexes)
            {
                final int hexCoord = hex.intValue();
                SOCResourceSet rset = hexCoord != robberHex ? resources : missedResources;
                if (board.getNumberOnHexFromCoord(hexCoord) == roll)
                {
                    switch (board.getHexTypeFromCoord(hexCoord))
                    {
                    case SOCBoard.CLAY_HEX:
                        rset.add(incr, SOCResourceConstants.CLAY);
                        break;

                    case SOCBoard.ORE_HEX:
                        rset.add(incr, SOCResourceConstants.ORE);
                        break;

                    case SOCBoard.SHEEP_HEX:
                        rset.add(incr, SOCResourceConstants.SHEEP);
                        break;

                    case SOCBoard.WHEAT_HEX:
                        rset.add(incr, SOCResourceConstants.WHEAT);
                        break;

                    case SOCBoard.WOOD_HEX:
                        rset.add(incr, SOCResourceConstants.WOOD);
                        break;

                    case SOCBoardLarge.GOLD_HEX:  // if not hasSeaBoard, == SOCBoard.MISC_PORT_HEX
                        if (hasSeaBoard)
                            rset.add(incr, SOCResourceConstants.GOLD_LOCAL);
                        break;

                    }
                }
            }
        }
    }

    /**
     * @return true if the player can discard these resources
     * @see #discard(int, SOCResourceSet)
     *
     * @param pn  the number of the player that is discarding
     * @param rs  the resources that the player is discarding
     */
    public boolean canDiscard(int pn, SOCResourceSet rs)
    {
        if (gameState != WAITING_FOR_DISCARDS)
        {
            return false;
        }

        SOCResourceSet resources = players[pn].getResources();

        if (!players[pn].getNeedToDiscard())
        {
            return false;
        }

        if (rs.getTotal() != (resources.getTotal() / 2))
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
     * Assumes {@link #canDiscard(int, SOCResourceSet)} already called to validate.
     *<P>
     * Special case:
     * If {@link #isForcingEndTurn()}, and no one else needs to discard,
     * gameState becomes {@link #PLAY1} but the caller must call
     * {@link #endTurn()} as soon as possible.
     *
     * @param pn   the number of the player
     * @param rs   the resources that are being discarded
     */
    public void discard(int pn, SOCResourceSet rs)
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
            placingRobberForKnightCard = false;  // known because robber doesn't trigger discard
            if (! forcingEndTurn)
            {
                if (canChooseMovePirate())
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
    public boolean canPickGoldHexResources(final int pn, final SOCResourceSet rs)
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
     * gameState to {@link #WAITING_FOR_PICK_GOLD_RESOURCE}
     * or {@link #PLAY1} accordingly.
     * (Or, during initial placement, set it to {@link #START2B} or {@link #START3B}.)
     *<P>
     * Assumes {@link #canPickGoldHexResources(int, SOCResourceSet)} already called to validate.
     *<P>
     * Called at server only; clients will instead get <tt>SOCPlayerElement</tt> messages
     * for the resources picked and the "need to pick" flag, and will call
     * {@link SOCPlayer#getResources()}<tt>.add</tt> and {@link SOCPlayer#setNeedToPickGoldHexResources(int)}.
     *
     * @param pn   the number of the player
     * @param rs   the resources that are being picked
     * @since 2.0.00
     */
    public void pickGoldHexResources(final int pn, final SOCResourceSet rs)
    {
        players[pn].getResources().add(rs);
        players[pn].setNeedToPickGoldHexResources(0);

        // initial placement?
        if (gameState == STARTS_WAITING_FOR_PICK_GOLD_RESOURCE)
        {
            if (! isGameOptionSet(SOCGameOption.K_SC_3IP))
                gameState = START2B;
            else
                gameState = START3B;
            return;
        }

        /**
         * check if we're still waiting for players to pick
         */
        gameState = PLAY1;

        for (int i = 0; i < maxPlayers; i++)
        {
            if (players[i].getNeedToPickGoldHexResources() > 0)
            {
                gameState = WAITING_FOR_PICK_GOLD_RESOURCE;
                break;
            }
        }

    }

    /**
     * Based on game options, can the pirate ship be moved instead of the robber?
     * True only if {@link #hasSeaBoard}.
     * For scenario option {@link SOCGameOption#K_SC_CLVI _SC_CLVI}, the player
     * must have {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     * @return  true if the pirate ship can be moved
     * @see #WAITING_FOR_ROBBER_OR_PIRATE
     * @see #chooseMovePirate(boolean)
     * @since 2.0.00
     */
    public boolean canChooseMovePirate()
    {
        if (! hasSeaBoard)
            return false;
        if (isGameOptionSet(SOCGameOption.K_SC_CLVI)
            && ! players[currentPlayerNumber].hasScenarioPlayerEvent
                 (SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE))
            return false;

        return true;
    }

    /**
     * Choose to move the pirate or the robber, from game state
     * {@link #WAITING_FOR_ROBBER_OR_PIRATE}.
     * Game state becomes {@link #PLACING_ROBBER} or {@link #PLACING_PIRATE}.
     * {@link #getRobberyPirateFlag()} is set or cleared accordingly.
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
     * Must be current player.  Game state must be {@link #PLACING_ROBBER}.
     * 
     * @return true if the player can move the robber to the coordinates
     *
     * @param pn  the number of the player that is moving the robber
     * @param co  the new robber hex coordinates; not validated
     * @see #moveRobber(int, int)
     * @see #canMovePirate(int, int)
     */
    public boolean canMoveRobber(int pn, int co)
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
        case SOCBoardLarge.FOG_HEX:
            // Must check these because the original board has port types (water hexes)
            // with the same numeric values as GOLD_HEX and FOG_HEX.
            return (board instanceof SOCBoardLarge);

        default:
            return false;  // Land hexes only (Could check board.max_robber_hex, if we didn't special-case desert,gold,fog)
        }
    }

    /**
     * move the robber.
     *<P>
     * If no victims (players to possibly steal from): State becomes oldGameState.
     * If just one victim: call stealFromPlayer, State becomes oldGameState.
     * If multiple possible victims: Player must choose a victim; State becomes {@link #WAITING_FOR_CHOICE}.
     *<P>
     * Assumes {@link #canMoveRobber(int, int)} has been called already to validate the move.
     * Assumes gameState {@link #PLACING_ROBBER}.
     *
     * @param pn  the number of the player that is moving the robber
     * @param rh  the robber's new hex coordinate; must be &gt; 0, not validated beyond that
     *
     * @return returns a result that says if a resource was stolen, or
     *         if the player needs to make a choice.  It also returns
     *         what was stolen and who was the victim.
     * @throws IllegalArgumentException if <tt>rh</tt> &lt;= 0
     */
    public SOCMoveRobberResult moveRobber(final int pn, final int rh)
        throws IllegalArgumentException
    {
        SOCMoveRobberResult result = new SOCMoveRobberResult();

        board.setRobberHex(rh, true);  // if co invalid, throws IllegalArgumentException
        robberyWithPirateNotRobber = false;
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;

        /**
         * do the robbing thing
         */
        Vector<SOCPlayer> victims = getPossibleVictims();

        if (victims.isEmpty())
        {
            gameState = oldGameState;
        }
        else if (victims.size() == 1)
        {
            SOCPlayer victim = victims.firstElement();
            final int loot = stealFromPlayer(victim.getPlayerNumber(), false);
            result.setLoot(loot);
        }
        else
        {
            /**
             * the current player needs to make a choice
             */
            gameState = WAITING_FOR_CHOICE;
        }

        result.setVictims(victims);

        return result;
    }

    /**
     * Can this player currently move the pirate ship to these coordinates?
     * Must be a water hex, per {@link SOCBoardLarge#isHexOnWater(int)}.
     * Must be different from current pirate coordinates.
     * Game must have {@link #hasSeaBoard}.
     * Must be current player.  Game state must be {@link #PLACING_PIRATE}.
     * For scenario option {@link SOCGameOption#K_SC_CLVI _SC_CLVI}, the player
     * must have {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
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
        if (isGameOptionSet(SOCGameOption.K_SC_CLVI)
            && ! players[pn].hasScenarioPlayerEvent(SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE))
            return false;

        return (board.isHexOnWater(hco));
    }

    /**
     * move the pirate ship.
     *<P>
     * If no victims (players to possibly steal from): State becomes oldGameState.
     *<br>
     * If multiple possible victims: Player must choose a victim; State becomes {@link #WAITING_FOR_CHOICE}.
     *    Once chosen, call {@link #choosePlayerForRobbery(int)} to choose a victim.
     *<br>
     * If just one victim: call stealFromPlayer, State becomes oldGameState.
     * If cloth robbery gives player enough VP to win, sets gameState to {@link #OVER}.
     *<br>
     *    Or: If just one victim but {@link #canChooseRobClothOrResource(int)},
     *    state becomes {@link #WAITING_FOR_ROB_CLOTH_OR_RESOURCE}.
     *    Once chosen, call {@link #stealFromPlayer(int, boolean)}.
     *<P>
     * Assumes {@link #canMovePirate(int, int)} has been called already to validate the move.
     * Assumes gameState {@link #PLACING_PIRATE}.
     *
     * @param pn  the number of the player that is moving the pirate ship
     * @param rh  the robber's new hex coordinate; should be a water hex
     *
     * @return returns a result that says if a resource was stolen, or
     *         if the player needs to make a choice.  It also returns
     *         what was stolen and who was the victim.
     * @throws IllegalArgumentException if <tt>ph</tt> &lt;= 0
     * @since 2.0.00
     */
    public SOCMoveRobberResult movePirate(final int pn, final int ph)
        throws IllegalArgumentException
    {
        SOCMoveRobberResult result = new SOCMoveRobberResult();

        ((SOCBoardLarge) board).setPirateHex(ph, true);  // if ph invalid, throws IllegalArgumentException
        robberyWithPirateNotRobber = true;
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;

        /**
         * do the robbing thing
         */
        Vector<SOCPlayer> victims = getPossibleVictims();

        if (victims.isEmpty())
        {
            gameState = oldGameState;
        }
        else if (victims.size() == 1)
        {
            SOCPlayer victim = victims.firstElement();
            if (! canChooseRobClothOrResource(victim.getPlayerNumber()))
            {
                // steal item, also sets gameState
                final int loot = stealFromPlayer(victim.getPlayerNumber(), false);
                result.setLoot(loot);
            } else {
                /**
                 * the current player needs to make a choice
                 * of whether to steal cloth or a resource
                 */
                gameState = WAITING_FOR_ROB_CLOTH_OR_RESOURCE;
            }
        }
        else
        {
            /**
             * the current player needs to make a choice
             * of which player to steal from
             */
            gameState = WAITING_FOR_CHOICE;
        }

        result.setVictims(victims);

        return result;
    }

    /**
     * When moving the robber or pirate, can this player be chosen to be robbed?
     * Game state must be {@link #WAITING_FOR_CHOICE} or {@link #WAITING_FOR_PICK_GOLD_RESOURCE}.
     * To choose the player and rob, call {@link #choosePlayerForRobbery(int)}.
     *
     * @return true if the current player can choose this player to rob
     * @param pn  the number of the player to rob
     *
     * @see #getRobberyPirateFlag()
     * @see #getPossibleVictims()
     * @see #canChooseRobClothOrResource(int)
     * @see #stealFromPlayer(int, boolean)
     */
    public boolean canChoosePlayer(int pn)
    {
        if ((gameState != WAITING_FOR_CHOICE) && (gameState != WAITING_FOR_ROB_CLOTH_OR_RESOURCE))
        {
            return false;
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
     * The current player has choosen a victim to rob.
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
     *
     * @param pn  the number of the player being robbed
     * @return the type of resource that was stolen, as in {@link SOCResourceConstants}, or 0
     *     if must choose first (state {@link #WAITING_FOR_ROB_CLOTH_OR_RESOURCE}),
     *     or {@link SOCResourceConstants#CLOTH_STOLEN_LOCAL} for cloth.
     * @since 2.0.00
     */
    public int choosePlayerForRobbery(final int pn)
    {
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
     * Used with scenario option {@link SOCGameOption#K_SC_CLVI _SC_CLVI}.
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
     * @return a list of {@link SOCPlayer players} touching a hex
     *   with settlements/cities, or an empty Vector if none
     *
     * @param hex  the coordinates of the hex
     */
    public Vector<SOCPlayer> getPlayersOnHex(final int hex)
    {
        Vector<SOCPlayer> playerList = new Vector<SOCPlayer>(3);

        final int[] nodes = board.getAdjacentNodesToHex(hex);

        for (int i = 0; i < maxPlayers; i++)
        {
            Vector<SOCSettlement> settlements = players[i].getSettlements();
            Vector<SOCCity> cities = players[i].getCities();
            boolean touching = false;

            for (SOCSettlement ss : settlements)
            {
                final int seCoord = ss.getCoordinates();
                for (int d = 0; d < 6; ++d)
                {
                    if (seCoord == nodes[d])
                    {
                        touching = true;
                        break;
                    }
                }
            }

            if (!touching)
            {
                for (SOCCity ci : cities)
                {
                    final int ciCoord = ci.getCoordinates();
                    for (int d = 0; d < 6; ++d)
                    {
                        if (ciCoord == nodes[d])
                        {
                            touching = true;
                            break;
                        }
                    }
                }
            }

            if (touching)
                playerList.addElement(players[i]);
        }

        return playerList;
    }

    /**
     * @param hex  the coordinates of the hex
     * @return a list of {@link SOCPlayer players} touching a hex
     *   with ships, or an empty Vector if none
     *
     * @since 2.0.00
     */
    public Vector<SOCPlayer> getPlayersShipsOnHex(final int hex)
    {
        Vector<SOCPlayer> playerList = new Vector<SOCPlayer>(3);

        final int[] edges = ((SOCBoardLarge) board).getAdjacentEdgesToHex(hex);

        for (int i = 0; i < maxPlayers; i++)
        {
            Vector<SOCRoad> roads_ships = players[i].getRoads();
            boolean touching = false;
            for (SOCRoad rs : roads_ships)
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
                playerList.addElement(players[i]);
        }

        return playerList;
    }

    /**
     * Does the current or most recent robbery use the pirate ship, not the robber?
     * If true, victims will be based on adjacent ships, not settlements/cities.
     * @return true for pirate ship, false for robber
     * @since 2.0.00
     */
    public boolean getRobberyPirateFlag()
    {
        return robberyWithPirateNotRobber;
    }

    /**
     * Given the robber or pirate's current position on the board,
     * and {@link #getRobberyPirateFlag()},
     * get the list of victims with adjacent settlements/cities or ships.
     * Victims are players with resources; for scenario option
     * {@link SOCGameOption#K_SC_CLVI _SC_CLVI}, also players with cloth
     * when robbing with the pirate.
     * @return a list of possible players to rob, or an empty Vector
     * @see #canChoosePlayer(int)
     * @see #choosePlayerForRobbery(int)
     */
    public Vector<SOCPlayer> getPossibleVictims()
    {
        Vector<SOCPlayer> victims = new Vector<SOCPlayer>();
        Vector<SOCPlayer> candidates;
        if (robberyWithPirateNotRobber)
        {
            candidates = getPlayersShipsOnHex(((SOCBoardLarge) board).getPirateHex());
        } else {
            candidates = getPlayersOnHex(board.getRobberHex());
        }

        for (SOCPlayer pl : candidates)
        {
            int pn = pl.getPlayerNumber();

            if ((pn != currentPlayerNumber) && (! isSeatVacant(pn))
                && ( (pl.getResources().getTotal() > 0) || (robberyWithPirateNotRobber && (pl.getCloth() > 0)) ))
            {
                victims.addElement(pl);
            }
        }

        return victims;
    }

    /**
     * the current player has choosen a victim to rob.
     * perform the robbery.  Set gameState back to oldGameState.
     *<P>
     * For the cloth game scenario, can steal cloth, and can gain victory points from having
     * cloth. If cloth robbery gives player enough VP to win, sets gameState to {@link #OVER}.
     *<P>
     * Does not validate <tt>pn</tt>; assumes {@link #canChoosePlayer(int)}
     * and {@link #choosePlayerForRobbery(int)} have been called already.
     *
     * @param pn  the number of the player being robbed
     * @param choseCloth  player has both resources and {@link SOCPlayer#getCloth() cloth},
     *                    the robbing player chose to steal cloth.
     *                    Even if false (no choice made), will steal cloth
     *                    if player has 0 {@link SOCPlayer#getResources() resources}.
     * @return the type of resource that was stolen, as in {@link SOCResourceConstants},
     *         or {@link SOCResourceConstants#CLOTH_STOLEN_LOCAL} for cloth.
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
            int[] rsrcs = new int[nRsrcs];
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
     * Are there currently any trade offers?
     * Calls each player's {@link SOCPlayer#getCurrentOffer()}.
     * @return true if any, false if not
     * @since 1.1.12
     */
    public boolean hasTradeOffers()
    {
        for (int i = 0; i < maxPlayers; ++i)
        {
            if ((seats[i] != VACANT) && (players[i].getCurrentOffer() != null))
                return true;
        }
        return false;
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
     * @see #canMakeBankTrade(SOCResourceSet, SOCResourceSet)
     */
    public boolean canMakeTrade(int offering, int accepting)
    {
        D.ebugPrintln("*** canMakeTrade ***");
        D.ebugPrintln("*** offering = " + offering);
        D.ebugPrintln("*** accepting = " + accepting);

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

        D.ebugPrintln("*** offer = " + offer);

        if ((offer.getGiveSet().getTotal() == 0) || (offer.getGetSet().getTotal() == 0))
        {
            return false;
        }

        D.ebugPrintln("*** offeringPlayer.getResources() = " + offeringPlayer.getResources());

        if (!(offeringPlayer.getResources().contains(offer.getGiveSet())))
        {
            return false;
        }

        D.ebugPrintln("*** acceptingPlayer.getResources() = " + acceptingPlayer.getResources());

        if (!(acceptingPlayer.getResources().contains(offer.getGetSet())))
        {
            return false;
        }

        return true;
    }

    /**
     * perform a trade between two players.
     * the trade performed is described in the offering player's
     * current offer.
     *<P>
     * Assumes {@link #canMakeTrade(int, int)} already was called.
     * If game option "NT" is set, players can trade only
     * with the bank, not with other players.
     *
     * @param offering  the number of the player making the offer
     * @param accepting the number of the player accepting the offer
     * @see #makeBankTrade(SOCResourceSet, SOCResourceSet)
     */
    public void makeTrade(int offering, int accepting)
    {
        if (isGameOptionSet("NT"))
            return;

        SOCResourceSet offeringPlayerResources = players[offering].getResources();
        SOCResourceSet acceptingPlayerResources = players[accepting].getResources();
        SOCTradeOffer offer = players[offering].getCurrentOffer();

        offeringPlayerResources.subtract(offer.getGiveSet());
        acceptingPlayerResources.subtract(offer.getGetSet());
        offeringPlayerResources.add(offer.getGetSet());
        acceptingPlayerResources.add(offer.getGiveSet());

        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
    }

    /**
     * Can we undo this bank trade?
     * True only if the last action this turn was a bank trade with the same resources.
     *<P>
     * To undo the bank trade, call {@link #canMakeBankTrade(SOCResourceSet, SOCResourceSet)}
     * with give/get swapped from the original call, then call
     * {@link #makeBankTrade(SOCResourceSet, SOCResourceSet)} the same way.
     *
     * @param undo_gave  Undo giving these resources (get these back from the bank)
     * @param undo_got   Undo getting these resources (give these back to the bank)
     * @return  true if the current player can undo a bank trade of these resources
     * @since 1.1.13
     */
    public boolean canUndoBankTrade(SOCResourceSet undo_gave, SOCResourceSet undo_got)
    {
        if (! lastActionWasBankTrade)
            return false;
        final SOCPlayer currPlayer = players[currentPlayerNumber];
        return ((currPlayer.lastActionBankTrade_get != null)
                && currPlayer.lastActionBankTrade_get.equals(undo_got)
                && currPlayer.lastActionBankTrade_give.equals(undo_gave));
    }

    /**
     * @return true if the current player can make a
     *         particular bank/port trade
     *
     * @param  give  what the player will give to the bank
     * @param  get   what the player wants from the bank
     * @see #canUndoBankTrade(SOCResourceSet, SOCResourceSet)
     */
    public boolean canMakeBankTrade(SOCResourceSet give, SOCResourceSet get)
    {
        if (gameState != PLAY1)
        {
            return false;
        }

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
                if (give.getAmount(i) > 0)
                {
                    if (((give.getAmount(i) % 2) == 0) && currPlayer.getPortFlag(i))
                    {
                        groupCount += (give.getAmount(i) / 2);
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
     * {@link #canMakeBankTrade(SOCResourceSet, SOCResourceSet)} first.
     *<P>
     * Undo was added in version 1.1.13; if the player's previous action
     * this turn was a bank trade, it can be undone by calling
     * {@link #canUndoBankTrade(SOCResourceSet, SOCResourceSet)},
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
        SOCResourceSet playerResources = currPlayer.getResources();

        if (lastActionWasBankTrade
            && (currPlayer.lastActionBankTrade_get != null)
            && currPlayer.lastActionBankTrade_get.equals(give)
            && currPlayer.lastActionBankTrade_give.equals(get))
        {
            playerResources.subtract(give);
            playerResources.add(get);
            lastActionTime = System.currentTimeMillis();
            lastActionWasBankTrade = false;
            currPlayer.lastActionBankTrade_give = null;
            currPlayer.lastActionBankTrade_get = null;
            return;
        }

        playerResources.subtract(give);
        playerResources.add(get);
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = true;
        currPlayer.lastActionBankTrade_give = give;
        currPlayer.lastActionBankTrade_get = get;
    }

    /**
     * @return true if the player has the resources, pieces, and
     *         room to build a road
     *
     * @param pn  the number of the player
     */
    public boolean couldBuildRoad(int pn)
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
    public boolean couldBuildSettlement(int pn)
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
    public boolean couldBuildCity(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();

        return ((resources.getAmount(SOCResourceConstants.ORE) >= 3) && (resources.getAmount(SOCResourceConstants.WHEAT) >= 2) && (players[pn].getNumPieces(SOCPlayingPiece.CITY) >= 1) && (players[pn].hasPotentialCity()));
    }

    /**
     * @return true if the player has the resources
     *         to buy a dev card, and if there are dev cards
     *         left to buy
     *
     * @param pn  the number of the player
     * @see #buyDevCard()
     */
    public boolean couldBuyDevCard(int pn)
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
    public boolean couldBuildShip(int pn)
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
    public void buyRoad(int pn)
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
    public void buySettlement(int pn)
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
    public void buyCity(int pn)
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
    public void buyShip(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.subtract(1, SOCResourceConstants.SHEEP);
        resources.subtract(1, SOCResourceConstants.WOOD);
        oldGameState = gameState;  // PLAY1 or SPECIAL_BUILDING
        gameState = PLACING_SHIP;
    }

    /**
     * a player is UNbuying a road; return resources, set gameState PLAY1
     * (or SPECIAL_BUILDING)
     *
     * @param pn  the number of the player
     */
    public void cancelBuildRoad(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.add(1, SOCResourceConstants.CLAY);
        resources.add(1, SOCResourceConstants.WOOD);
        if (oldGameState != SPECIAL_BUILDING)
            gameState = PLAY1;
        else
            gameState = SPECIAL_BUILDING;
    }

    /**
     * a player is UNbuying a settlement; return resources, set gameState PLAY1
     * (or SPECIAL_BUILDING)
     *
     * @param pn  the number of the player
     */
    public void cancelBuildSettlement(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.add(1, SOCResourceConstants.CLAY);
        resources.add(1, SOCResourceConstants.SHEEP);
        resources.add(1, SOCResourceConstants.WHEAT);
        resources.add(1, SOCResourceConstants.WOOD);
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
    public void cancelBuildCity(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.add(3, SOCResourceConstants.ORE);
        resources.add(2, SOCResourceConstants.WHEAT);
        if (oldGameState != SPECIAL_BUILDING)
            gameState = PLAY1;
        else
            gameState = SPECIAL_BUILDING;
    }

    /**
     * a player is UNbuying a ship; return resources, set gameState PLAY1
     * (or SPECIAL_BUILDING)
     *
     * @param pn  the number of the player
     * @since 2.0.00
     */
    public void cancelBuildShip(int pn)
    {
        SOCResourceSet resources = players[pn].getResources();
        resources.add(1, SOCResourceConstants.SHEEP);
        resources.add(1, SOCResourceConstants.WOOD);
        if (oldGameState != SPECIAL_BUILDING)
            gameState = PLAY1;
        else
            gameState = SPECIAL_BUILDING;
    }

    /**
     * the current player is buying a dev card.
     *<P>
     *<b>Note:</b> Not checked for validity; please call {@link #couldBuyDevCard(int)} first.
     *
     * @return the card that was drawn; a dev card type from {@link SOCDevCardConstants}.
     */
    public int buyDevCard()
    {
        numDevCards--;
        final int card = devCardDeck[numDevCards];

        SOCResourceSet resources = players[currentPlayerNumber].getResources();
        resources.subtract(1, SOCResourceConstants.ORE);
        resources.subtract(1, SOCResourceConstants.SHEEP);
        resources.subtract(1, SOCResourceConstants.WHEAT);
        players[currentPlayerNumber].getDevCards().add(1, SOCDevCardSet.NEW, card);
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
        checkForWinner();

        return (card);
    }

    /**
     * Can this player currently play a knight card?
     * gameState must be {@link #PLAY} or {@link #PLAY1}.
     * Must have a {@link SOCDevCardConstants#KNIGHT} and must
     * not have already played a dev card this turn.
     * @return true if the player can play a knight card
     *
     * @param pn  the number of the player
     */
    public boolean canPlayKnight(int pn)
    {
        if (!((gameState == PLAY) || (gameState == PLAY1)))
        {
            return false;
        }

        if (players[pn].hasPlayedDevCard())
        {
            return false;
        }

        if (players[pn].getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) == 0)
        {
            return false;
        }

        return true;
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
     */
    public boolean canPlayRoadBuilding(int pn)
    {
        if (!((gameState == PLAY) || (gameState == PLAY1)))
        {
            return false;
        }

        final SOCPlayer player = players[pn];

        if (player.hasPlayedDevCard())
        {
            return false;
        }

        if (player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) == 0)
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
    public boolean canPlayDiscovery(int pn)
    {
        if (!((gameState == PLAY) || (gameState == PLAY1)))
        {
            return false;
        }

        if (players[pn].hasPlayedDevCard())
        {
            return false;
        }

        if (players[pn].getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC) == 0)
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
    public boolean canPlayMonopoly(int pn)
    {
        if (!((gameState == PLAY) || (gameState == PLAY1)))
        {
            return false;
        }

        if (players[pn].hasPlayedDevCard())
        {
            return false;
        }

        if (players[pn].getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO) == 0)
        {
            return false;
        }

        return true;
    }

    /**
     * The current player plays a Knight card.
     * gameState becomes either {@link #PLACING_ROBBER}
     * or {@link #WAITING_FOR_ROBBER_OR_PIRATE}.
     * Assumes {@link #canPlayKnight(int)} already called, and the play is allowed.
     */
    public void playKnight()
    {
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
        players[currentPlayerNumber].setPlayedDevCard(true);
        players[currentPlayerNumber].getDevCards().subtract(1, SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT);
        players[currentPlayerNumber].incrementNumKnights();
        updateLargestArmy();
        checkForWinner();
        placingRobberForKnightCard = true;
        oldGameState = gameState;
        if (canChooseMovePirate())
        {
            gameState = WAITING_FOR_ROBBER_OR_PIRATE;
        } else {
            robberyWithPirateNotRobber = false;
            gameState = PLACING_ROBBER;
        }
    }

    /**
     * The current player plays a Road Building card.
     * This card directs the player to place 2 roads or ships,
     * or 1 road and 1 ship.
     * Checks of game rules online show "MAY" or "CAN", not "MUST" place 2.
     * If they have 2 or more roads or ships, may place 2; gameState becomes PLACING_FREE_ROAD1.
     * If they have just 1 road/ship, may place that; gameState becomes PLACING_FREE_ROAD2.
     * If they have 0 roads, cannot play the card.
     * Assumes {@link #canPlayRoadBuilding(int)} has already been called, and move is valid.
     */
    public void playRoadBuilding()
    {
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
        final SOCPlayer player = players[currentPlayerNumber];
        player.setPlayedDevCard(true);
        player.getDevCards().subtract(1, SOCDevCardSet.OLD, SOCDevCardConstants.ROADS);
        oldGameState = gameState;

        final int roadShipCount = player.getNumPieces(SOCPlayingPiece.ROAD)
            + player.getNumPieces(SOCPlayingPiece.SHIP);
        if (roadShipCount > 1)
        {
            gameState = PLACING_FREE_ROAD1;  // First of 2 free roads / ships
        } else {
            gameState = PLACING_FREE_ROAD2;  // "Second", just 1 free road or ship
        }
    }

    /**
     * the current player plays a Discovery card
     */
    public void playDiscovery()
    {
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
        players[currentPlayerNumber].setPlayedDevCard(true);
        players[currentPlayerNumber].getDevCards().subtract(1, SOCDevCardSet.OLD, SOCDevCardConstants.DISC);
        oldGameState = gameState;
        gameState = WAITING_FOR_DISCOVERY;
    }

    /**
     * the current player plays a monopoly card
     */
    public void playMonopoly()
    {
        lastActionTime = System.currentTimeMillis();
        lastActionWasBankTrade = false;
        players[currentPlayerNumber].setPlayedDevCard(true);
        players[currentPlayerNumber].getDevCards().subtract(1, SOCDevCardSet.OLD, SOCDevCardConstants.MONO);
        oldGameState = gameState;
        gameState = WAITING_FOR_MONOPOLY;
    }

    /**
     * @return true if the current player can
     *         do the discovery card action and the
     *         pick contains exactly 2 resources
     *
     * @param pick  the resources that the player wants
     */
    public boolean canDoDiscoveryAction(SOCResourceSet pick)
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
     */
    public void updateLargestArmy()
    {
        int size;

        if (playerWithLargestArmy == -1)
        {
            size = 2;
        }
        else
        {
            size = players[playerWithLargestArmy].getNumKnights();
        }

        for (int i = 0; i < maxPlayers; i++)
        {
            if (players[i].getNumKnights() > size)
            {
                playerWithLargestArmy = i;
            }
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
     *
     * this version recalculates the longest road only for
     * the player who is affected by the most recently
     * placed piece, by calling their {@link SOCPlayer#calcLongestRoad2()}.
     * Assumes all other players' longest road has been updated already.
     * All players' {@link SOCPlayer#getLongestRoadLength()} is called here.
     *<P>
     * if there is a tie, the last player to have LR keeps it.
     * if two or more players are tied for LR and none of them
     * used to have LR, then no one has LR.
     *<P>
     * If {@link SOCGameOption#K_SC_0RVP} is set, does nothing.
     *
     * @param pn  the number of the player who is affected
     */
    public void updateLongestRoad(int pn)
    {
        if (isGameOptionSet(SOCGameOption.K_SC_0RVP))
            return;  // <--- No longest road ---

        //D.ebugPrintln("## updateLongestRoad("+pn+")");
        int longestLength;
        int playerLength;
        int tmpPlayerWithLR = -1;

        players[pn].calcLongestRoad2();
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
            {
                if (players[i].getLongestRoadLength() == longestLength)
                {
                    playersWithLR++;
                }
            }

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
     * game is over.  Set game state to OVER,
     * set player with win.
     *<P>
     * Per rules FAQ, a player can win only during their own turn.
     * If a player reaches winning points ({@link #vp_winner} or more) but it's
     * not their turn, there is not yet a winner. This could happen if,
     * for example, the longest road is broken by a new settlement, and
     * the next-longest road is not the current player's road.
     *<P>
     * The win is determined not by who has the highest point total, but
     * solely by reaching enough victory points ({@link #vp_winner}) during your own turn.
     *<P>
     * Some game scenarios have other special win conditions ({@link #hasScenarioWinCondition}):
     *<UL>
     *<LI> Scenario {@link SOCGameOption#K_SC_CLVI _SC_CLVI} will end the game if
     *     less than half the {@link SOCVillage}s have cloth remaining.  The player
     *     with the most VP wins; if tied, the tied player with the most cloth wins.
     *     The winner is not necessarily the current player.
     *</UL>
     *
     * @see #getGameState()
     * @see #getPlayerWithWin()
     */
    public void checkForWinner()
    {
        if (gameState == SPECIAL_BUILDING)
            return;  // Can't win in this state, it's not really anyone's turn

        final int pn = currentPlayerNumber;
        if ((pn < 0) || (pn >= maxPlayers))
            return;

        if ((players[pn].getTotalVP() >= vp_winner))
        {
            gameState = OVER;
            playerWithWin = pn;
            return;
        }

        if (! hasScenarioWinCondition)
            return;

        if (isGameOptionSet(SOCGameOption.K_SC_CLVI))
        {
            // Check if less than half the villages have cloth remaining
            checkForWinner_SC_CLVI();
            if (gameState == OVER)
                currentPlayerNumber = playerWithWin;  // don't call setCurrentPlayerNumber, would recurse here
        }

    }

    /**
     * Check if less than half the villages have cloth remaining.
     * If so, end the game; see {@link #checkForWinner()} for details.
     * @since 2.0.00
     */
    private final void checkForWinner_SC_CLVI()
    {
        int nv = 0;
        final HashMap<Integer, SOCVillage> allv = ((SOCBoardLarge) board).getVillages();
        for (SOCVillage v : allv.values())
            if (v.getCloth() > 0)
                ++nv;
        if (nv >= (allv.size()+1) / 2)
            return;  // at least half the villages have cloth; +1 is for odd # of villages (int 7 / 2 = 3)

        gameState = OVER;

        // find player with most VP, or most cloth
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
            return;
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
            return;
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
                return;
            }
        } while (advanceTurn());
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

        players = null;
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
     * Old game's previous state is saved to {@link #getResetOldGameState()}.
     * Please call destroyGame() on old game when done examining its state.
     *<P>
     * Assumes that if the game had more than one human player,
     * they've already voted interactively to reset the board.
     * @see #resetVoteBegin(int)
     */
    public SOCGame resetAsCopy()
    {
        // the constructor will set most fields based on game options
        SOCGame cp = new SOCGame(name, active, SOCGameOption.cloneOptions(opts));

        cp.isFromBoardReset = true;
        oldGameState = gameState;  // for getResetOldGameState()
        active = false;
        gameState = RESET_OLD;

        // Most fields are NOT copied since this is a "reset", not an identical-state game.
        cp.isAtServer = isAtServer;
        cp.isPractice = isPractice;
        cp.ownerName = ownerName;
        cp.scenarioEventListener = scenarioEventListener;

        // Game min-version from options
        cp.clientVersionMinRequired = clientVersionMinRequired;

        // Per-player state
        for (int i = 0; i < maxPlayers; i++)
        {
            boolean wasRobot = false;
            if ((seats[i] != VACANT) && (players[i] != null) && (players[i].getName() != null))
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
                if (cp.seats[i] == VACANT)
                    cp.seatLocks[i] = true;
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
     */
    public void resetVoteBegin(int reqPN) throws IllegalArgumentException, IllegalStateException
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

        if (gameState >= PLAY)
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
     */
    public boolean resetVoteRegister(int pn, boolean votingYes)
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
     */
    public int getResetPlayerVote(int pn)
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
     * @param pn  The player's number
     * @throws IllegalStateException  if game is not 6-player, or pn is current player,
     *            or {@link SOCPlayer#hasAskedSpecialBuild() pn.hasAskedSpecialBuild()}
     *            or {@link SOCPlayer#hasSpecialBuilt() pn.hasSpecialBuilt()} is true,
     *            or if gamestate is earlier than {@link #PLAY}, or >= {@link #OVER},
     *            or if the first player is asking before completing their first turn.
     * @throws IllegalArgumentException  if pn is not a valid player (vacant seat, etc).
     * @see #canBuyOrAskSpecialBuild(int)
     * @since 1.1.08
     */
    public boolean canAskSpecialBuild(final int pn, final boolean throwExceptions)
        throws IllegalStateException, IllegalArgumentException
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

        if ((gameState < PLAY) || (gameState >= OVER)
              || pl.hasSpecialBuilt()
              || pl.hasAskedSpecialBuild())
        {
            if (throwExceptions)
                throw new IllegalStateException("cannot ask at this time");
            else
                return false;
        }

        if ((pn == currentPlayerNumber)
            && ((gameState != PLAY)
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
     *            or if gamestate is earlier than {@link #PLAY}, or >= {@link #OVER}.
     * @throws IllegalArgumentException  if pn is not a valid player (vacant seat, etc).
     * @since 1.1.08
     */
    public void askSpecialBuild(final int pn, final boolean onlyIfCan)
        throws IllegalStateException, IllegalArgumentException
    {
        if ((! onlyIfCan) || canAskSpecialBuild(pn, true))
        {
            players[pn].setAskedSpecialBuild(true);
            askedSpecialBuildPhase = true;
        }
    }

    /**
     * Are we in the 'free placement' debug mode?
     * See server.processDebugCommand_freePlace,
     * SOCPlayerInterface.setDebugPaintPieceMode.
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
            final boolean has3rdInitPlace = isGameOptionSet(SOCGameOption.K_SC_3IP);
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
                    gameState = PLAY;
                    updateAtTurn();  // "virtual" endTurn here,
                      // just like advanceTurnStateAfterPutPiece().
                } else {
                    gameState = START3A;
                }
            }
            else if (npiece == 6)
            {
                currentPlayerNumber = firstPlayerNumber;
                gameState = PLAY;
                updateAtTurn();  // "virtual" endTurn here,
                  // just like advanceTurnStateAfterPutPiece().
            }
        }

        debugFreePlacement = debugOn;
        debugFreePlacementStartPlaced = false;
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
     * Dice roll result, for reporting from {@link SOCGame#rollDice()}.
     * Each game has 1 instance of this object, which is updated each turn.
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
         * Null, or distributed cloth (for that game scenario), in the same
         * format as {@link SOCVillage#distributeCloth(SOCGame)}.
         */
        public int[] cloth;

        /** Convenience: Set diceA and dice, clear {@link #cloth}. */
        public void update (final int dA, int dB)
        {
            diceA = dA;
            diceB = dB;
        }

    }  // nested class RollResult

}
