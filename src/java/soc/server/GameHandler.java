/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013-2016 Jeremy D Monin <jeremy@nand.net>
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
import soc.message.SOCMessageForGame;
import soc.server.genericServer.StringConnection;
import soc.util.SOCGameList;

/**
 * Server class to handle game-specific actions and messages for a type of game.
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
 * <LI> Actions and requests from players arrive here via {@link #processCommand(SOCGame, SOCMessageForGame, StringConnection)},
 *      called for each {@link SOCMessageForGame} sent to the server about this handler's game.
 * <LI> Communication to game members is done by handler methods calling server methods
 *      such as {@link SOCServer#messageToGame(String, soc.message.SOCMessage)}
 *      or {@link SOCServer#messageToPlayer(StringConnection, soc.message.SOCMessage)}.
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
     * Process one command from a client player of this game.
     *<P>
     * Some game messages (such as player sits down, or board reset voting) are handled the same for all game types.
     * These are handled at {@link SOCServer}; they should be ignored here and not appear in your switch statement.
     *<P>
     * Called from {@link SOCServer} message treater loop.  Caller will catch any thrown Exceptions.
     *
     * @param ga  Game in which client {@code c} is sending {@code msg}.
     *            Never null; from {@link SOCMessageForGame#getGame()}.
     * @param mes  Message from client {@code c}. Never null.
     * @param c    Client sending {@code msg}. Never null.
     * @return  true if processed, false if ignored or unknown message type
     */
    public abstract boolean processCommand(SOCGame ga, SOCMessageForGame mes, StringConnection c);

    /**
     * Look for a potential debug command in a text message sent by the "debug" client/player.
     * If game debug is on, called for every game text message (chat message) received from that player.
     *<P>
     * Server-wide debug commands are processed before gametype-specific debug commands;
     * see {@link SOCServer#processDebugCommand(StringConnection, String, String, String)}.
     *
     * @param debugCli  Client sending the potential debug command
     * @param gaName  Game in which the message is sent
     * @param dcmd   Text message which may be a debug command
     * @param dcmdU  {@code dcmd} as uppercase, for efficiency (it's already been uppercased in caller)
     * @return true if {@code dcmd} is a recognized debug command, false otherwise
     * @see #getDebugCommandsHelp()
     */
    public abstract boolean processDebugCommand(StringConnection debugCli, final String gaName, final String dcmd, final String dcmdU);

    /**
     * Get the debug commands for this game type, if any, used with
     * {@link #processDebugCommand(StringConnection, String, String, String)}.
     * If client types the {@code *help*} debug command, the server will
     * send them all the general debug command help, and these strings.
     * @return  a set of lines of help text to send to a client after sending {@link SOCServer#DEBUG_COMMANDS_HELP},
     *          or {@code null} if no gametype-specific debug commands
     */
    public abstract String[] getDebugCommandsHelp();

    /**
     * Client has been approved to join game; send the entire state of the game to client.
     * Unless <tt>isTakingOver</tt>, send client join event to other players.
     * Assumes NEWGAME (or NEWGAMEWITHOPTIONS) has already been sent out.
     * First message sent to connecting client is JOINGAMEAUTH, unless isReset.
     *<P>
     * Among other messages, player names are sent via SITDOWN, and pieces on board
     * sent by PUTPIECE.  See comments here for further details.
     * If <tt>isTakingOver</tt>, assume the game already started and send any details
     * about pieces, number of items, cards in hand, etc.
     * The group of messages sent here ends with GAMEMEMBERS, SETTURN and GAMESTATE.
     *<P>
     * @param gameData Game to join
     * @param c        The connection of joining client
     * @param isReset  Game is a board-reset of an existing game.  This is always false when
     *                 called from SOCServer instead of from inside the GameHandler.
     *                 Not all game types may be reset.
     * @param isTakingOver  Client is re-joining; this connection replaces an earlier one which
     *                      is defunct because of a network problem.
     *                      If <tt>isTakingOver</tt>, don't send anything to other players.
     *
     * @see SOCServer#connectToGame(StringConnection, String, java.util.Map)
     * @see SOCServer#createOrJoinGameIfUserOK(StringConnection, String, String, String, java.util.Map)
     */
    public abstract void joinGame(SOCGame gameData, StringConnection c, boolean isReset, boolean isTakingOver);

    /**
     * When player has just sat down at a seat, send them all the private information.
     * Cards in their hand, resource counts, anything else that isn't public to all players.
     * Because they've just sat and become an active player, send the gameState, and prompt them if
     * the game is waiting on any decision by their player number (discard, pick a free resource, etc).
     *<P>
     * Called from {@link SOCServer#sitDown(SOCGame, StringConnection, int, boolean, boolean)}.
     *<P>
     * <b>Locks:</b> Assumes ga.takeMonitor() is held, and should remain held.
     *
     * @param ga     the game
     * @param c      the connection for the player
     * @param pn     which seat the player is taking
     * @since 1.1.08
     */
    public abstract void sitDown_sendPrivateInfo(SOCGame ga, StringConnection c, final int pn);

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
     * Call {@link SOCGame#removePlayer(String)}.
     * If the game still has other players, continue it, otherwise it will be ended after
     * returning from {@code leaveGame}. Send messages out to other game members.
     *<P>
     * <B>Locks:</b> Has {@link SOCGameList#takeMonitorForGame(String) gameList.takeMonitorForGame(gm)}
     * when calling this method; does not have {@link SOCGame#takeMonitor()}.
     *
     * @param ga  The game
     * @param c  The member connection which left.
     *           The server has already removed {@code c} from the list of game members.
     *           If {@code c} is being dropped because of an error,
     *           {@link StringConnection#disconnect()} has already been called.
     *           Don't exclude {@code c} from any communication about leaving the game,
     *           in case they are still connected and in other games.
     * @return true if the game should be ended and deleted (does not have other observers or non-robot players,
     *           and game's {@code isBotsOnly} flag is false)
     */
    public abstract boolean leaveGame(SOCGame ga, StringConnection c);

}
