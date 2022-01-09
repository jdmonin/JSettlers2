/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013-2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.server;

import soc.game.SOCGame;
import soc.game.SOCGameOptionSet;
import soc.game.SOCPlayer;
import soc.message.SOCGameState;
import soc.message.SOCMessageForGame;
import soc.message.SOCRollDicePrompt;
import soc.server.genericServer.Connection;
import soc.util.SOCGameList;

/**
 * Server class to handle game-specific actions for a type of game and send resulting messages to clients.
 * Each game type's inbound messages are processed through its {@link GameMessageHandler}.
 *<P>
 * Currently, these concepts are common to all hosted game types:
 *<UL>
 * <LI> A game has members (clients) who are each a player or observer
 * <LI> Some players may be bots
 * <LI> Human players sit down (choosing a player number) before starting the game
 * <LI> Some player positions (seats) may be locked so a bot won't sit there
 * <LI> Once a human player decides to start the game, randomly chosen bots join and sit down in unlocked empty seats
 * <LI> Games can be new, starting, active, or over
 * <LI> Once started, games have a current player
 * <LI> If bots' turns or actions take too long, can force the end of their turn
 * <LI> Game board can be reset to a new game with the same settings and players (bots, if any, are randomly chosen again).
 *      See {@link SOCGame#resetAsCopy()} and {@link SOCGameListAtServer#resetBoard(String)}.
 *</UL>
 *
 * Interface and interaction:
 *<UL>
 * <LI> The {@link SOCServer} manages the "boundary" of the game: The list of all games;
 *      joining and leaving players and bots; creating and destroying games; game start time and the board reset framework.
 * <LI> Every server/client interaction about the game, including startup and end-game details, player actions and
 *      requests, and ending a player's turn, is taken care of within the {@code GameHandler} and {@link SOCGame}.
 *      Game reset details are handled by {@link SOCGame#resetAsCopy()}.
 * <LI> Actions and requests from players arrive here via
 *      {@link GameMessageHandler#dispatch(SOCGame, SOCMessageForGame, Connection)},
 *      called for each {@link SOCMessageForGame} sent to the server about this handler's game.
 * <LI> Communication to game members is done by handler methods calling server methods
 *      such as {@link SOCServer#messageToGame(String, boolean, soc.message.SOCMessage)}
 *      or {@link SOCServer#messageToPlayer(Connection, String, int, soc.message.SOCMessage)}.
 *</UL>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public abstract class GameHandler
{
    protected final SOCServer srv;

    protected GameHandler(final SOCServer server)
    {
        srv = server;
    }

    /**
     * Get this game type's GameMessageHandler.
     */
    public abstract GameMessageHandler getMessageHandler();

    /**
     * Look for a potential debug command in a text message sent by the "debug" client/player.
     * If game debug is on, called for every game text message (chat message) received from that player.
     *<P>
     * Server-wide debug commands are processed before gametype-specific debug commands;
     * see {@link SOCServer#processDebugCommand(Connection, SOCGame, String, String)}.
     *
     * @param debugCli  Client sending the potential debug command
     * @param ga  Game in which the message is sent; not null
     * @param dcmd   Text message which may be a debug command
     * @param dcmdU  {@code dcmd} as uppercase, for efficiency (it's already been uppercased in caller)
     * @return true if {@code dcmd} is a recognized debug command, false otherwise
     * @see #getDebugCommandsHelp()
     */
    public abstract boolean processDebugCommand
        (final Connection debugCli, final SOCGame ga, final String dcmd, final String dcmdU);

    /**
     * Get the debug commands for this game type, if any, used with
     * {@link #processDebugCommand(Connection, SOCGame, String, String)}.
     * If client types the {@code *help*} debug command, the server will
     * send them all the general debug command help, and these strings.
     * @return  a set of lines of help text to send to a client after sending {@link SOCServer#DEBUG_COMMANDS_HELP},
     *          or {@code null} if no gametype-specific debug commands
     */
    public abstract String[] getDebugCommandsHelp();

    /**
     * When creating a new game at the server, check its options and
     * if any of them require any optional client features, calculate
     * the required features and call {@link SOCGame#setClientFeaturesRequired(soc.util.SOCFeatureSet)}.
     * @param ga  Game to check features; not {@code null}
     */
    public abstract void calcGameClientFeaturesRequired(SOCGame ga);

    /**
     * Client has been approved to join game; send JOINGAMEAUTH and the entire state of the game to client.
     * Unless {@code isRejoinOrLoadgame}, announce {@link SOCJoinGame} client join event to other players.
     *<P>
     * Assumes {@link SOCServer#connectToGame(Connection, String, SOCGameOptionSet, SOCGame)} was already called.
     * Assumes NEWGAME (or NEWGAMEWITHOPTIONS) has already been sent out.
     * First message sent to connecting client is JOINGAMEAUTH, unless isReset.
     *<P>
     * Among other messages, player names are sent via SITDOWN, and pieces on board
     * sent by PUTPIECE. See comments here for further details. The group of messages
     * sent here to the client ends with GAMEMEMBERS, SETTURN and GAMESTATE.
     * If game has started (state &gt;= {@link SOCGame#START2A START2A}), they're
     * then prompted with a GAMESERVERTEXT to take over a bot in order to play.
     *<P>
     * If {@code isRejoinOrLoadgame}, assume the game already started and also include any details
     * about pieces, number of items, cards in hand, etc.
     *<P>
     * @param gameData Game to join
     * @param c        The connection of joining client
     * @param isReset  Game is a board-reset of an existing game.  This is always false when
     *                 called from SOCServer instead of from inside the GameHandler.
     *                 Not all game types may be reset.
     * @param isLoading  Game is being reloaded from snapshot by {@code c}'s request; state is {@link SOCGame#LOADING}
     * @param isRejoinOrLoadgame  If true, client is re-joining; {@code c} replaces an earlier connection which
     *          is defunct/frozen because of a network problem. Also true when a human player joins a
     *          game being reloaded and has the same nickname as a player there.
     *          If {@code isRejoinOrLoadgame}, sends {@code c} their hand's private info for game in progress.
     * @see SOCServer#createOrJoinGameIfUserOK(Connection, String, String, String, SOCGameOptionSet)
     * @since 1.1.00
     */
    public abstract void joinGame
        (SOCGame gameData, Connection c, boolean isReset, boolean isLoading, boolean isRejoinOrLoadgame);

    /**
     * When player has just sat down at a seat, send them all the private information.
     * Cards in their hand, resource counts, anything else that isn't public to all players.
     * Because they've just sat and become an active player, send the gameState, and prompt them if
     * the game is waiting on any decision by their player number (discard, pick a free resource, etc).
     *<P>
     * Called from {@link SOCServer#sitDown(SOCGame, Connection, int, boolean, boolean)}.
     *<P>
     * <b>Locks:</b> Assumes ga.takeMonitor() is held, and should remain held.
     *
     * @param ga     the game
     * @param c      the connection for the player
     * @param pn     which seat the player is taking
     * @param isRejoinOrLoadgame  If true, client is re-joining; {@code c} replaces an earlier connection which
     *          is defunct/frozen because of a network problem. Also true when a human player joins a
     *          game being reloaded and has the same nickname as a player there.
     * @since 1.1.08
     */
    public abstract void sitDown_sendPrivateInfo
        (SOCGame ga, Connection c, final int pn, final boolean isRejoinOrLoadgame);

    /**
     * Send all game members the current state of the game with a {@link SOCGameState} message.
     * Assumes current player does not change during this state.
     * May also send other messages to the current player.
     * If state is {@link SOCGame#ROLL_OR_CARD}, sends game a {@link SOCRollDicePrompt}.
     *<P>
     * Be sure that callers to {@code sendGameState} don't assume the game will still
     * exist after calling this method, if the game state was {@link SOCGame#OVER OVER}.
     *<P>
     * This method has always been part of the server package.
     * v2.3.00 is the first version to expose it as part of {@code GameHandler}.
     *
     * @param ga  the game
     * @since 2.3.00
     */
    public abstract void sendGameState(SOCGame ga);

    /**
     * Do the things you need to do to start a game and send its data to the clients.
     * Players are already seated when this method is called.
     *<P>
     * Send all game members the piece counts, other public information for the game and each player,
     * set up and send the board layout, game state, and finally send the {@link soc.message.SOCStartGame STARTGAME}
     * and {@link soc.message.SOCTurn TURN} messages.
     *<P>
     * Set game state to {@link SOCGame#READY} or higher, from an earlier/lower state.
     *
     * @param ga  the game
     */
    public abstract void startGame(SOCGame ga);

    /**
     * Announces this player's new trade offer to their game,
     * or send that info to one client that's joining the game now.
     * If player isn't currently offering, does nothing.
     *<P>
     * If announcing to entire game (not {@code toJoiningClient}),
     * treats this as a new offer and calls {@link SOCServer#recordGameEvent(String, soc.message.SOCMessage)}.
     *
     * @param pl  Send this player's {@link SOCPlayer#getCurrentOffer()}, if any
     * @param toJoiningClient  Null or a single client to send offer info to
     * @since 2.4.00
     */
    public abstract void sendTradeOffer(SOCPlayer pl, Connection toJoiningClient);

    /**
     * The server's timer thread thinks this game is inactive because of a robot bug.
     * Check the game.  If this is the case, end the current turn, forcing if necessary.
     * Use a separate thread so the main timer thread isn't tied up; see {@link SOCForceEndTurnThread}.
     *<P>
     * The server checks {@link SOCGame#lastActionTime} to decide inaction.
     * The game could also seem inactive if we're waiting for another human player to decide something.
     * Games with state &gt;= {@link SOCGame#OVER}, and games which haven't started yet
     * ({@link SOCGame#getCurrentPlayerNumber()} == -1), are ignored.
     *<P>
     * The default timeout is {@link SOCServer#ROBOT_FORCE_ENDTURN_SECONDS}.  You may calculate and use
     * a longer timeout if it makes sense in the current conditions, such as waiting for a human player
     * to ignore or respond to a trade offer.
     *
     * @param ga  Game to check
     * @param currentTimeMillis  The time when called, from {@link System#currentTimeMillis()}
     */
    public abstract void endTurnIfInactive(final SOCGame ga, final long currentTimeMillis);

    /**
     * This member (player or observer) has left the game.
     * Check the game and clean up, forcing end of current turn if necessary.
     * Call {@link SOCGame#removePlayer(String, boolean)}.
     * If the game still has other players, continue it, otherwise it will be ended after
     * returning from {@code leaveGame}. Send messages out to other game members
     * notifying them the player has left.
     *<P>
     * See javadoc return value for behavior if the sole human player has left
     * and only bots and/or observers remain.
     *<P>
     * <B>Locks:</b> Has {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gm)}
     * when calling this method; does not have {@link SOCGame#takeMonitor()}.
     *
     * @param ga  The game
     * @param c  The member connection which left.
     *           The server has already removed {@code c} from the list of game members.
     *           If {@code c} is being dropped because of an error,
     *           {@link Connection#disconnect()} has already been called.
     *           Don't exclude {@code c} from any communication about leaving the game,
     *           in case they are still connected and in other games.
     * @param hasReplacement  If true the leaving connection is a bot, and there's a waiting client who will be told
     *           next to sit down in this bot's seat, so that isn't really becoming vacant
     * @param hasHumanReplacement  if true, {@code hasReplacement}'s client is human, not a bot
     * @return true if the game should be ended and deleted (does not have other observers or non-robot players,
     *           not {@code hasHumanReplacement}, and game's {@code isBotsOnly} flag is false).
     *           <P>
     *           If {@code isBotsOnly} is false, and the game is now only robots and observers,
     *           game should end unless the "allow robots-only games" property is nonzero: Check
     *           {@link SOCServer#getConfigIntProperty(String, int) SOCServer.getConfigIntProperty}
     *           ({@link SOCServer#PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL PROP_JSETTLERS_BOTS_BOTGAMES_TOTAL}, 0).
     */
    public abstract boolean leaveGame(SOCGame ga, Connection c, boolean hasReplacement, boolean hasHumanReplacement);

    /**
     * When a human player has left an active game, or a game is starting and a
     * bot from that game's {@link SOCServer#robotJoinRequests} has disconnected,
     * look for a robot player which can take a seat and continue the game.
     *<P>
     * If found the bot should be added to {@link SOCServer#robotJoinRequests} and
     * should be sent a JoinGameRequest message. Otherwise the game should be sent a
     * ServerText message explaining failure to find any robot; human players
     * might need to leave the game and start a new one.
     * @param ga   Game to look in
     * @param reqInfo  Info related to the join request, such as the seat number to fill.
     *     If {@code ! gameIsActive}, this comes from {@link SOCServer#robotJoinRequests}
     *     via {@link SOCServer#leaveConnection(Connection)}.
     * @param gameIsActive  True if for active game, not a game still starting
     * @return true if an available bot was found
     */
    public abstract boolean findRobotAskJoinGame
        (SOCGame ga, Object reqInfo, boolean gameIsActive);

}
