/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 *
 * This file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2013-2023 Jeremy D Monin <jeremy@nand.net>
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
 */
package soc.client;

import soc.baseclient.SOCDisplaylessPlayerClient;  // for javadocs only

import java.util.List;
import java.util.Map;

import soc.game.ResourceSet;
import soc.game.SOCGame;
import soc.game.SOCGameOption;  // for javadocs only
import soc.game.SOCGameOptionSet;  // for javadocs only
import soc.game.SOCInventory;   // for javadocs only
import soc.game.SOCInventoryItem;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;  // for javadocs only
import soc.game.SOCResourceSet;
import soc.game.SOCSpecialItem;
import soc.message.SOCDeclinePlayerRequest;
import soc.message.SOCPickResources;  // for reason codes in javadocs
import soc.message.SOCPlayerElement.PEType;
import soc.message.SOCPlayerStats;

/**
 * A listener on the {@link SOCPlayerClient} to decouple the presentation from the networking.
 * This presents the facade of the UI to the networking layer.
 * The game data ({@link SOCGame}, {@link SOCPlayer} methods) will be updated before
 * these methods are called, so you can call game-object methods for more details on the new event.
 *<P>
 * Some game events may cause a non-blocking popup message or dialog to be shown while gameplay continues.
 *<BR>
 * Some examples in the classic UI:
 * <UL>
 *  <LI> The Build a Wonder/Wonder Info dialog in the {@code _SC_WOND} scenario
 *  <LI> {@link #scen_SC_PIRI_pirateFortressAttackResult(boolean, int, int)}
 * </UL>
 * When this is the case, it might make sense for the UI to not take automatic actions on
 * the client player's behalf, such as rolling dice at the start of the turn: In a 6-player game
 * the player may want to click Special Building first because the dice roll might make them discard,
 * but the non-blocking dialog was obscuring the Special Building button.
 *<P>
 * To prevent such surprises for the client player, call {@link #isNonBlockingDialogVisible()}
 * before taking that kind of automatic action.
 *<P>
 * The classic UI implementing this interface is {@link SOCPlayerInterface.ClientBridge}.
 *
 * @author paulbilnoski
 * @since 2.0.00
 */
public interface PlayerClientListener
{
    /**
     * Get the game shown in this UI. This reference changes if board is reset.
     * @return game; not null
     * @since 2.5.00
     * @see #getClientPlayerNumber()
     */
    SOCGame getGame();

    /**
     * Get the client's player number if client is a player in a game.
     * @return Client player's {@link SOCPlayer#getPlayerNumber()} if playing, or -1 if observing or not yet seated
     * @see #isClientCurrentPlayer()
     */
    int getClientPlayerNumber();

    /**
     * Is the client player active in this game, and the current player?
     * @see #getClientPlayerNumber()
     * @since 2.5.00
     */
    boolean isClientCurrentPlayer();

    /**
     * Receive a notification that the current player has rolled the dice.
     * Call this after updating game state with the roll result.
     * After a call to {@code diceRolled}, {@link #diceRolledResources(List, List)} is often called next.
     * @param player May be {@code null} if the current player was null when the dice roll was received from the server.
     * @param resultSum The sum of the dice rolled. May be <tt>-1</tt> for some game events.
     */
    void diceRolled(SOCPlayer player, int resultSum);

    /**
     * Receive a notification that the dice roll resulted in players gaining resources.
     * Call this after updating player resources with the gains.
     * Often follows a call to {@link #diceRolled(SOCPlayer, int)}.
     * @param pnum  Player numbers, same format as {@link soc.message.SOCDiceResultResources#playerNum}
     * @param rsrc Resources gained by each {@code pn}, same format as {@link soc.message.SOCDiceResultResources#playerRsrc}
     */
    void diceRolledResources(List<Integer> pnum, List<SOCResourceSet> rsrc);

    /**
     * A client (us or someone else) has joined the game.
     * They will be an observer until {@link #playerSitdown(int, String)} is called,
     * then they will be an active player.
     * @param nickname  New client's player/observer name
     */
    void playerJoined(String nickname);

    /**
     * A client player or observer is leaving the game.
     * @param nickname The player name. Will not be {@code null}
     * @param player May be {@code null} if the current player is an observer.
     */
    void playerLeft(String nickname, SOCPlayer player);

    /**
     * A client has sat down to become an active player,
     * or server is sending already-seated player info as client joins a game.
     * {@link #playerJoined(String)} was called earlier on this client.
     * {@link SOCGame#addPlayer(String, int)} has just been called.
     * @param playerNumber  New player's playerNumber in the game; the seat number they've sat down at
     * @param nickname  New player's name
     */
    void playerSitdown(int playerNumber, String nickname);

    /**
     * Game's current player and state has changed. Update displays.
     * (Caller has already called {@link SOCGame#setGameState(int)}, {@link SOCGame#setCurrentPlayerNumber(int)},
     * {@link SOCGame#updateAtTurn()}.)
     * @param playerNumber  New current player number whose turn it is.
     */
    void playerTurnSet(int playerNumber);

    /**
     * A player has placed a piece on the board; update game data and displays.
     * @param pieceType A piece type identifier, such as {@link SOCPlayingPiece#CITY}
     */
    void playerPiecePlaced(SOCPlayer player, int coordinate, int pieceType);

    /**
     * A player has moved a piece on the board; update game data and displays.
     * Most pieces are not movable.  {@link soc.game.SOCShip SOCShip} pieces can sometimes be moved.
     * Not used when the robber or pirate is moved; see {@link #robberMoved()}.
     * @param pieceType A piece type identifier, such as {@link SOCPlayingPiece#CITY}
     */
    void playerPieceMoved(SOCPlayer player, int sourceCoordinate, int targetCoordinate, int pieceType);

    /**
     * A player's piece has been removed from the board.
     * Updates game state and refreshes the game board display.
     * Currently, only ships can be removed, in game scenario {@code _SC_PIRI}.
     * @param player  Player who owns the ship
     * @param pieceCoordinate  Ship's node coordinate
     * @param pieceType  The piece type identifier {@link SOCPlayingPiece#SHIP}
     */
    void playerPieceRemoved(SOCPlayer player, int pieceCoordinate, int pieceType);

    /**
     * Placing or moving a player's piece is being undone.
     * Is called after game data has been updated.
     * @param player  The player who owns the piece; not null
     * @param coordinate  The location of the piece whose placement or move is being undone
     * @param movedFromCoordinate  If undoing a ship move, the piece's former location before the move; otherwise 0
     * @param pieceType  The piece type, such as {@link SOCPlayingPiece#CITY}
     * @see #playerPiecePlacementUndoDeclined(int)
     * @since 2.7.00
     */
    void playerPiecePlacementUndone(SOCPlayer player, int coordinate, int movedFromCoordinate, int pieceType);

    /**
     * Server has declined our request to undo placing or moving a player's piece.
     * @param pieceType  The piece type, such as {@link SOCPlayingPiece#CITY}
     * @param isMove  True if declining a move, not a placement
     * @see #playerPiecePlacementUndone(SOCPlayer, int, int, int)
     * @since 2.7.00
     */
    void playerPiecePlacementUndoDeclined(int pieceType, boolean isMove);

    /**
     * A player has been awarded Special Victory Point(s).
     * @param player The player awarded special victory points. Will not be {@code null}
     * @param numSvp The count of how many new special victory points were awarded
     * @param awardDescription A user-display message describing the reason for the award
     */
    void playerSVPAwarded(SOCPlayer player, int numSvp, String awardDescription);

    /**
     * A player is drawing or playing a development card, or a card or special
     * {@link SOCInventoryItem} has been added or removed from their hand's inventory.
     * Also called at end of game, once per player when server sends list of revealed VP cards.
     *<P>
     * If this inventory update comes from playing a special {@link SOCInventoryItem}, call
     * {@link #playerCanCancelInvItemPlay(SOCPlayer, boolean)} after calling this method.
     *<P>
     * Before v2.2.00 this method was {@code playerDevCardUpdated}.
     *
     * @param player  The player
     * @param addedPlayable  True if the update added a dev card or item that's playable now
     *     ({@link SOCInventory#OLD}, not {@link SOCInventory#NEW NEW})
     * @see UpdateType#DevCards
     */
    void playerDevCardsUpdated(SOCPlayer player, final boolean addedPlayable);

    /**
     * A player is playing or placing a special {@link SOCInventoryItem}, such as
     * a gift trade port in scenario {@code _SC_FTRI}.  Set a flag that indicates
     * if this play or placement can be canceled (returned to player's inventory).
     *<P>
     * It makes sense to call this for only the client player, since we don't cancel
     * other players' item plays.
     *
     * @param player  The player
     * @param canCancel  True if {@link SOCInventoryItem#canCancelPlay}
     */
    void playerCanCancelInvItemPlay(SOCPlayer player, final boolean canCancel);

    /**
     * A player has changed their face icon.
     * @param player  The player
     * @param faceId  New face icon number;
     *            1 and higher are human face images, 0 is the default robot, -1 is the smarter robot.
     */
    void playerFaceChanged(SOCPlayer player, int faceId);

    /**
     * Update one part of the player's status, such as their number of clay resources or settlements remaining.
     * @param player May be {@code null}
     * @param utype The type of element to update
     * @param isGoodNews True if this update is an unexpected gain (resource from fog hex reveal, etc)
     * @param isBadNews True if this update is bad news from a message that the player has lost resources
     *     or pieces (to the robber, monopoly, etc)
     */
    void playerElementUpdated(SOCPlayer player, UpdateType utype, boolean isGoodNews, boolean isBadNews);

    /**
     * A player's total resource count has been updated.
     * @param player  The player
     */
    void playerResourcesUpdated(SOCPlayer player);

    /**
     * A player has chosen their two free Discovery/Year of Plenty resources,
     * or free Gold Hex resources. Is called after client's game data has been updated.
     * Should indicate that the trade has happened as if sent a {@code SOCGameServerText} about it,
     * unless {@code reasonCode} is 0.
     * @param player  The player; not null
     * @param resSet  Resources chosen; not null
     * @param reasonCode  Reason code from {@link SOCPickResources}, such as
     *     {@link SOCPickResources#REASON_DISCOVERY} or {@link SOCPickResources#REASON_GOLD_HEX}, or 0
     * @since 2.5.00
     */
    void playerPickedResources(SOCPlayer player, SOCResourceSet resSet, int reasonCode);

    /**
     * Display or format one type of a player's stats, such as resource trades.
     * Called at end of game or when the player uses the *STATS* command.
     * In client v2.7.00 and newer, is called after player data has been updated
     * by {@link SOCDisplaylessPlayerClient#handlePLAYERSTATS(SOCPlayerStats, SOCGame, int)}.
     * @param statsType  Type of statistics, such as {@link SOCPlayerStats#STYPE_TRADES}
     *     or {@link SOCPlayerStats#STYPE_RES_ROLL}.
     *     If type is unrecognized, do nothing.
     * @param stats  Player statistic details for {@code statsType}.
     *     Before v2.7.00, this method would need to interpret and display the contents.
     *     Starting at that version, displays values from player data instead and {@code stats[]} is used only as
     *     a hint of how much stats data (how many types of trades, etc) was sent by the server
     *     and can be {@code null} to use client's defaults.
     * @param withHeading  If true, print or return a heading row before the data row(s)
     * @param doDisplay  If true, display the formatted stats immediately instead of returning them.
     * @return formatted and localized stats strings, or {@code null} if {@code doDisplay} is true
     *     or {@code statsType} not recognized
     * @since 2.6.00
     */
    List<String> playerStats(int statsType, int[] stats, final boolean withHeading, final boolean doDisplay);

    /**
     * The game requests that the client player discard a particular number of resource cards.
     * @param countToDiscard  Must choose and discard this many cards
     */
    void requestedDiscard(int countToDiscard);

    /**
     * The client player must pick which free resource(s) to receive.
     * Used with "Year of Plenty"/"Discovery" cards, and the Gold Hex.
     * @param countToPick  Must choose this many resources
     * @see #requestedGoldResourceCountUpdated(SOCPlayer, int)
     */
    void promptPickResources(int countToPick);

    /**
     * This player must pick this many gold-hex resources, or no longer needs to pick them.
     * Update displays accordingly. This method is informational only: Do not ask
     * the client player to pick resources, {@link #promptPickResources(int)} is used for that.
     * @param player  The player
     * @param countToPick  Number of free resources they must pick, or 0 if they've just picked them
     */
    void requestedGoldResourceCountUpdated(SOCPlayer player, int countToPick);

    /**
     * This player has just discarded some resources. Player data has been updated.
     * Announce the discard and update displays.
     * @param player  Player discarding resources; not {@code null}
     * @param discards  The known or unknown resources discarded; not {@code null}
     * @since 2.5.00
     */
    void playerDiscarded(SOCPlayer player, ResourceSet discards);

    /**
     * This player must choose a player for robbery.
     * @param choices   The potential victim players to choose from
     * @param isNoneAllowed  If true, player can choose to rob no one (game scenario <tt>SC_PIRI</tt>)
     * @see GameMessageSender#choosePlayer(SOCGame, int)
     */
    void requestedChoosePlayer(List<SOCPlayer> choices, boolean isNoneAllowed);

    void requestedChooseRobResourceType(SOCPlayer player);

    /**
     * A robbery has just occurred; show result details. Is called after game data has been updated.
     *
     * @param perpPN  Perpetrator's player number, or -1 if none
     *     (used by {@code SC_PIRI} scenario, future use by other scenarios/expansions)
     * @param victimPN  Victim's player number, or -1 if none (for future use by scenarios/expansions)
     * @param resType  Resource type being stolen, like {@link SOCResourceConstants#SHEEP}
     *     or {@link SOCResourceConstants#UNKNOWN}. Ignored if {@code resSet != null} or {@code peType != null}.
     * @param resSet  Resource set being stolen, if not using {@code resType} or {@code peType}
     * @param peType  PlayerElement type such as {@link PEType#SCENARIO_CLOTH_COUNT},
     *     or {@code null} if a resource like sheep is being stolen (use {@code resType} or {@code resSet} instead).
     * @param isGainLose  If true, the amount here is a delta Gained/Lost by players, not a total to Set
     * @param amount  Amount being stolen if {@code isGainLose}, otherwise {@code perpPN}'s new total amount
     * @param victimAmount  {@code victimPN}'s new total amount if not {@code isGainLose}, 0 otherwise
     * @param extraValue  Optional information related to the robbery, or 0; for use by scenarios/expansions
     * @since 2.5.00
     */
    void reportRobberyResult
        (final int perpPN, final int victimPN, final int resType, final SOCResourceSet resSet, final PEType peType,
         final boolean isGainLose, final int amount, final int victimAmount, final int extraValue);

    /**
     * This player has just made a successful trade with the bank or a port. Implementation may call
     * <tt>{@link #playerElementUpdated(SOCPlayer, UpdateType, boolean, boolean) playerElementUpdated}(player,
     * {@link UpdateType#ResourceTotalAndDetails}, false, false)</tt>.
     *
     * @param player  Player making the bank/port trade
     * @param give  Resources given by player in trade
     * @param get   Resources received by player in trade
     * @see #playerTradeAccepted(SOCPlayer, SOCPlayer, SOCResourceSet, SOCResourceSet)
     */
    void playerBankTrade(SOCPlayer player, SOCResourceSet give, SOCResourceSet get);

    /**
     * The player {@code offerer} has just made a trade offer to other players,
     * or updated the resources of their already-displayed offer.
     * Show its details in their part of the game interface.
     * For offer details call {@code offerer.}{@link SOCPlayer#getCurrentOffer() getCurrentOffer()}.
     *
     * @param offerer  Player with a new trade offer
     * @param fromPN  {@code offerer}'s player number
     */
    void requestedTrade(SOCPlayer offerer, int fromPN);

    /**
     * Clear any trade offer to other players, and reset all trade resource square values to 0.
     * May also be called after a successful bank trade, to reset those resources.
     * @param offerer May be {@code null} to clear all seats
     * @param isBankTrade  If true, is being called after a successful bank trade.
     *     If bank trade wasn't sent from player's Trade Panel, should do nothing:
     *     Don't reset square values to 0.
     */
    void requestedTradeClear(SOCPlayer offerer, final boolean isBankTrade);

    /**
     * A player has rejected the current trade offer(s).
     * Indicate that in the display, for example by showing something like "no thanks"
     * in their part of the game interface.
     * @param rejecter  Player rejecting all trade offers
     */
    void requestedTradeRejection(SOCPlayer rejecter);

    /**
     * A player has accepted a trade offer from another player.
     * Call this after updating player resource data, but before
     * calling <tt>offerer.{@link SOCPlayer#setCurrentOffer(soc.game.SOCTradeOffer) setCurrentOffer(null)}</tt>.
     *<P>
     * Newer servers' trade acceptance announcements include the offer details
     * for {@code toOffering} and {@code toAccepting}; if null, implementer
     * should call <tt>offerer.{@link SOCPlayer#getCurrentOffer() getCurrentOffer()}</tt> for those details.
     * (Older servers instead announced with PlayerElement messages sent before the Accept.)
     *
     * @param offerer  Player who made the trade offer
     * @param acceptor  Player who accepted the trade offer
     * @param toOffering  Resources given to offering player in trade, or {@code null} if not announced by server
     * @param toAccepting  Resources given to accepting player in trade, or {@code null} if not announced by server
     * @see #playerBankTrade(SOCPlayer, SOCResourceSet, SOCResourceSet)
     */
    void playerTradeAccepted
        (SOCPlayer offerer, SOCPlayer acceptor, SOCResourceSet toOffering, SOCResourceSet toAccepting);

    /**
     * Server has rejected client player's attempt to trade with the bank,
     * make a trade offer, or accept another player's offer.
     * @param offeringPN  Player number offering the disallowed trade,
     *     or -1 if bank trade. Always -1 if {@code isNotTurn}.
     * @param isOffer  True if this is about a proposed trade offer, not acceptance of an existing offer
     * @param isNotTurn  True if was disallowed because this trade can be done only during client player's turn
     * @since 2.5.00
     */
    void playerTradeDisallowed(int offeringPN, boolean isOffer, boolean isNotTurn);

    /**
     * Clear any visible trade messages/responses.
     * @param playerToReset May be {@code null} to clear all seats
     */
    void requestedTradeReset(SOCPlayer playerToReset);

    /**
     * Clear a player's current offer.
     * If player is client, clear the numbers in the resource "offer" squares,
     * and disable the "offer" and "clear" buttons (since no resources are selected).
     * Otherwise just hide the last-displayed offer.
     *
     * @param player  Player to clear, or {@code null} for all players
     * @param updateSendCheckboxes If true, and player is client, update the
     *    selection checkboxes for which opponents are sent the offer.
     *    If it's currently that client player's turn, check all boxes where the seat isn't empty.
     *    Otherwise, check only the box for the opponent whose turn it is.
     * @since 2.5.00
     */
    void clearTradeOffer(SOCPlayer player, boolean updateSendCheckboxes);

    void requestedSpecialBuild(SOCPlayer player);

    /**
     * Server is prompting a player to roll the dice (or play a dev card) to begin their turn.
     * This prompt is sent to all game members, not just the current player.
     * Also may be called during initial placement when it's a player's turn to place.
     * @param pn  Player number being prompted
     */
    void requestedDiceRoll(final int pn);

    /** The largest army might have changed, so update */
    void largestArmyRefresh(SOCPlayer old, SOCPlayer potentialNew);

    /** The longest road might have changed, so update */
    void longestRoadRefresh(SOCPlayer old, SOCPlayer potentialNew);

    /**
     * The current game members (players and observers) are listed, and the game is about to start.
     * @param names  Game member names; to see if each is a player, call {@link SOCGame#getPlayer(String)}.
     */
    void membersListed(List<String> names);

    /**
     * An entire board layout has been received from the server.
     * Calls {@link #boardUpdated()} and also updates
     * related counters/displays elsewhere in the game's UI.
     */
    void boardLayoutUpdated();

    /**
     * Part of the board contents have been updated.
     * For example, a fog hex was revealed, or a trade port
     * was removed from or added to the board.
     * Redraws all layers of the entire board.
     * @see #boardLayoutUpdated()
     */
    void boardUpdated();

    /**
     * A playing piece's value was updated:
     * {@code _SC_CLVI} village cloth count, or
     * {@code _SC_PIRI} pirate fortress strength.
     * Repaint that piece (if needed) on the board.
     * @param piece  Piece that was updated, includes its new value
     */
    void pieceValueUpdated(SOCPlayingPiece piece);

    void boardPotentialsUpdated();

    /**
     * Handle board reset (new game with same players, same game name).
     * Most GUI panels are destroyed and re-created.  Player chat text is kept.
     *
     * @param newGame New game object
     * @param newSeatNumber  Our player number in {@code newGame},
     *     which is always the same as in the pre-reset game,
     *     or -1 if server didn't send a player number
     * @param requestingPlayerNumber Player who requested the board reset
     */
    void boardReset(SOCGame newGame, int newSeatNumber, int requestingPlayerNumber);

    void boardResetVoteRequested(SOCPlayer requestor);
    void boardResetVoteCast(SOCPlayer voter, boolean vote);
    void boardResetVoteRejected();

    /**
     * The robber or pirate was moved onto a hex.
     * @param newHex  The new robber/pirate hex coordinate, or 0 to take the pirate off the board
     * @param isPirate  True if the pirate, not the robber, was moved
     */
    void robberMoved(int newHex, boolean isPirate);

    void devCardDeckUpdated();

    /** One or all player seats' Seat Lock Status have been updated in game data; refresh all players' displays. */
    void seatLockUpdated();

    // This javadoc also appears in SOCPlayerInterface; please also update there if it changes.
    /**
     * Is a dialog or popup message currently visible while gameplay continues?
     * See {@link PlayerClientListener} interface javadoc for details and implications.
     *
     * @return  True if such a dialog is visible
     */
    boolean isNonBlockingDialogVisible();

    /**
     * Game play is starting (leaving state {@link SOCGame#NEW}).
     * Next move is for players to make their starting placements.
     *<P>
     * Call {@link SOCGame#setGameState(int)} before calling this method.
     * Call this method before calling {@link #gameStateChanged(int)}.
     */
    void gameStarted();

    /**
     * Update interface after game state has changed.
     * Please call {@link SOCGame#setGameState(int)} first.
     *<P>
     * Is also called as part of handling a TURN message from server, so don't immediately assume
     * the current player can (for example) roll dice when called for {@link SOCGame#ROLL_OR_CARD}:
     * That state may be intended for the next player. If so, {@link #playerTurnSet(int)} will be called very soon.
     *<P>
     * If the game is now starting, please call in this order:
     *<code><pre>
     *   game.setGameState(newState);
     *   {@link #gameStarted()};
     *   {@link #gameStateChanged(int)};
     *</pre></code>
     *
     * @param gameState One of the states from SOCGame, such as {@link soc.game.SOCGame#NEW}
     * @param isForDecline If true, server has sent us a {@link SOCDeclinePlayerRequest};
     *     {@code gameState} might not have changed since last call to {@code gameStateChanged(..)}.
     */
    void gameStateChanged(int gameState, boolean isForDecline);

    /**
     * Update game data and interface after game is over.
     * Call each player's {@link SOCPlayer#forceFinalVP(int)},
     * then if {@link SOCGame#getPlayerWithWin()} == null, call {@link SOCGame#checkForWinner()}.
     * Reveal actual total scores, list other players' VP cards, etc.
     * @param scores  Each player's actual total score, including hidden VP cards.
     *     Map contains each player object in the game, including empty seats,
     *     so its size is {@link SOCGame#maxPlayers}.
     */
    void gameEnded(Map<SOCPlayer, Integer> scores);

    /**
     * Game was deleted or a server/network error occurred; stop playing.
     * @param wasDeleted  True if game was deleted, isn't from an error;
     *     this can happen while observing a game
     * @param errorMessage  Error message if any, or {@code null}
     */
    void gameDisconnected(boolean wasDeleted, String errorMessage);

    /**
     * Print a broadcast message into this display's chat area.
     * @param message  Message text
     * @see MainDisplay#chatMessageBroadcast(String)
     */
    void messageBroadcast(String message);

    /**
     * Print a line of text in the game text area, like {@link SOCPlayerInterface#print(String)}.
     * @since 2.5.00
     * @see #printText(String, boolean)
     */
    void printText(String txt);

    /**
     * Print a line of text in the game text area, like {@link SOCPlayerInterface#print(String, boolean)}.
     * Optionally add a {@code "* "} prefix, as used in game actions and announcements from the server.
     * @param txt  the text to print
     * @param addStarPrefix  If true, print {@code "* "} before {@code txt}
     *    unless {@code txt} already starts with a {@code '*'}
     * @since 2.7.00
     * @see #printText(String)
     */
    void printText(String txt, boolean addStarPrefix);

    /**
     * A game text message was received from server, or a chat message from another player.
     * @param nickname  Player's nickname, {@code null} for messages from the server itself,
     *     or {@code ":"} for server messages which should appear in the chat area (recap, etc).
     *     For {@code ":"}, the message text will probably end with " ::" because the original client would
     *     begin the text line with ":: " from {@code nickname + ": "}.
     * @param message  Message text
     * @see MainDisplay#chatMessageReceived(String, String, String)
     */
    void messageReceived(String nickname, String message);

    /**
     * A player's {@link soc.message.SOCSimpleRequest "simple request"} has been sent to the entire game, or the server
     * has replied to our own simple request, and this should be displayed.
     * This method lets us display simple things from the server without defining a lot of small similar methods.
     *<P>
     * If other game data messages are sent (resource gains/loss, etc), or other client code must update that data
     * based on info in the SOCSimpleRequest, this method will be called only after other game data is updated.
     * Some SimpleRequest {@code reqtype}s update the game data: Client must call
     * {@link SOCDisplaylessPlayerClient#handleSIMPLEREQUEST(soc.message.SOCSimpleRequest, SOCGame)}
     * to update game before calling this method.
     *
     * @param pn  The player number requesting or acting, or -1 if our own request was declined
     * @param reqtype  The request type, from {@link soc.message.SOCSimpleRequest} constants for simplicity
     * @param value1  First optional detail value, or 0
     * @param value2  Second optional detail value, or 0
     * @see #simpleAction(int, int, int, int)
     */
    void simpleRequest(int pn, int reqtype, int value1, int value2);

    /**
     * A {@link soc.message.SOCSimpleAction "simple action"} has occurred in the game and should be displayed.
     * This method lets us show simple things from the server without defining a lot of small similar methods.
     *<P>
     * This will be called only after other game data is updated (number of dev cards, resource gains/loss, etc).
     *
     * @param pn  The player number acting or acted on, or -1 if this action isn't about a specific player
     * @param acttype  The action type, from {@link soc.message.SOCSimpleAction} constants for simplicity
     * @param value1  First optional detail value, or 0
     * @param value2  Second optional detail value, or 0
     * @see #simpleRequest(int, int, int, int)
     */
    void simpleAction(int pn, int acttype, int value1, int value2);

    /**
     * Let client player know the server has declined their request or requested action.
     * Because this only updates the display, call before updating game state if was incorrect at client;
     * that update may cause a prompt or dialog to be shown.
     * @param reasonCode  Reason the request was declined:
     *     {@link SOCDeclinePlayerRequest#REASON_NOT_NOW}, {@link SOCDeclinePlayerRequest#REASON_NOT_YOUR_TURN}, etc
     * @param detailValue1  Optional detail, may be used by some {@code reasonCode}s
     * @param detailValue2  Optional detail, may be used by some {@code reasonCode}s
     * @param reasonText  Optional localized reason text, or {@code null} to print text based on {@code reasonCode}
     * @since 2.5.00
     */
    void playerRequestDeclined
        (final int reasonCode, final int detailValue1, final int detailValue2, final String reasonText);

    /**
     * A player has canceled their current build.
     * Called when canceling their most recent initial settlement build,
     * or during regular gameplay to return resources to player's hand
     * and change gameState back to {@link SOCGame#PLAY1}
     * after sending a build request from the build panel.
     * @param player  Player who cancelled their own build; not null
     */
    void buildRequestCanceled(SOCPlayer player);

    /**
     * Client player's request to play a special {@link SOCInventoryItem} was rejected by the server.
     * @param type  Item type from {@link SOCInventoryItem#itype}
     * @param reasonCode  Optional reason code for the {@link soc.message.SOCInventoryItemAction#CANNOT_PLAY} action,
     *            corresponding to {@link SOCGame#canPlayInventoryItem(int, int)} return codes, or 0
     */
    void invItemPlayRejected(final int type, final int reasonCode);

    /**
     * Show the results of a player's {@code PICK} of a known {@link SOCSpecialItem Special Item},
     * or the server's {@code DECLINE} of the client player's pick request.
     *<P>
     * To see which scenario and option {@code typeKey}s pick Special Items,
     * and scenario-specific usage details, see the {@link SOCSpecialItem} class javadoc.
     *<P>
     * {@code coord} and {@code level} are sent for convenience, and sometimes may not be from the Special Item you need;
     * see {@link soc.message.SOCSetSpecialItem#OP_PICK} for details.
     *
     * @param typeKey  Item's {@code typeKey}, as described in the {@link SOCSpecialItem} class javadoc
     * @param ga  Game containing {@code pl} and special items
     * @param pl  Player who picked: Never {@code null} when {@code isPick},
     *                is {@code null} if server declined our player's request
     * @param gidx  Picked this index within game's Special Item list, or -1
     * @param pidx  Picked this index within {@code pl}'s Special Item list, or -1
     * @param isPick  True if calling for {@code PICK}, false if server has {@code DECLINE}d the client player's request
     * @param coord  Optional coordinates on the board for this item, or -1. An edge or a node, depending on item type
     * @param level  Optional level of construction or strength, or 0
     * @param sv  Optional string value from {@link SOCSpecialItem#getStringValue()}, or {@code null}
     * @see #playerSetSpecialItem(String, SOCGame, SOCPlayer, int, int, boolean)
     * @see SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)
     */
    void playerPickSpecialItem
        (final String typeKey, final SOCGame ga, final SOCPlayer pl, final int gidx, final int pidx,
         final boolean isPick, final int coord, final int level, final String sv);

    /**
     * Show the results of a player's {@code SET} or {@code CLEAR} of a known {@link SOCSpecialItem Special Item}.
     *<P>
     * To see which scenario and option {@code typeKey}s set or clear Special Items,
     * and scenario-specific usage details, see the {@link SOCSpecialItem} class javadoc.
     *
     * @param typeKey  Item's {@code typeKey}, as described in the {@link SOCSpecialItem} class javadoc
     * @param ga  Game containing {@code pl} and special items
     * @param pl  Requesting player; never {@code null}
     * @param gidx  Set or clear this index within game's Special Item list, or -1
     * @param pidx  Set or clear this index within {@code pl}'s Special Item list, or -1
     * @param isSet  True if player has set, false if player has cleared, this item index
     * @see #playerPickSpecialItem(String, SOCGame, SOCPlayer, int, int, boolean, int, int, String)
     * @see SOCSpecialItem#playerSetItem(String, SOCGame, SOCPlayer, int, int, boolean)
     */
    void playerSetSpecialItem
        (final String typeKey, final SOCGame ga, final SOCPlayer pl,
         final int gidx, final int pidx, final boolean isSet);

    /**
     * In scenario _SC_PIRI, present the server's response to a Pirate Fortress Attack request from the
     * current player (the client or another player), which may be: Rejected, Lost, Tied, or Won.
     *<P>
     * This will be called only after other game pieces are updated (fortress strength, player's ships lost).
     *
     * @param wasRejected  True if the server rejected our player's request to attack
     * @param defStrength  Pirate defense strength, unless {@code wasRejected}
     * @param resultShipsLost  Result and number of ships lost by the player:
     *            0 if player won (or if rejected); 1 if tied; 2 if player lost to the pirates.
     */
    void scen_SC_PIRI_pirateFortressAttackResult(boolean wasRejected, int defStrength, int resultShipsLost);

    void debugFreePlaceModeToggled(boolean isEnabled);

    /**
     * Player data update types for {@link PlayerClientListener#playerElementUpdated(SOCPlayer, UpdateType)}.
     */
    enum UpdateType
    {
        Clay,
        Ore,
        Sheep,
        Wheat,
        Wood,

        /** amount of resources of unknown type (not same as total resource count) */
        Unknown,

        /**
         * Update Total Resource count only.
         * @see #ResourceTotalAndDetails
         */
        Resources,

        /**
         * Update Total Resource count, and also each box (Clay,Ore,Sheep,Wheat,Wood) if shown.
         * May update other parts of the window beyond that player's hand: Enable/disable trade offer Accept buttons, etc.
         * @see #Resources
         */
        ResourceTotalAndDetails,

        Road,
        Settlement,
        City,
        Ship,
        Knight,

        /*
         * Total number of resources picked/gained from gold hex reveals
         * in sea board scenarios; announced in game window's activity pane and used in stats.
         * Update not sent if gain is 0.
         */
        // GoldGains -- removed in v2.7.00

        /** Number of Warships built, in {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI} scenario */
        Warship,

        /** Cloth Count update, in {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI} scenario */
        Cloth,

        /** Wonder build level, in {@link SOCGameOptionSet#K_SC_WOND _SC_WOND} scenario */
        WonderLevel,

        /** Victory Points, from {@link SOCPlayer#getTotalVP()} **/
        VictoryPoints,

        SpecialVictoryPoints,

        /**
         * Total count of development cards/items, from player's {@link SOCInventory#getTotal()}.
         * Doesn't update the inventory item list if that's shown:
         * Call {@link PlayerClientListener#playerDevCardsUpdated(SOCPlayer, boolean)} if needed.
         */
        DevCards,

        LongestRoad,
        LargestArmy,

        /**
         * Number of undos remaining, from {@link SOCPlayer#getUndosRemaining()}.
         * If not using {@link SOCGameOption} {@code "UBL"}, updating this has no effect.
         */
        UndosRemaining
    }

    /** Listener to be called when {@link PlayerClientListener#isNonBlockingDialogVisible()} becomes false. */
    public interface NonBlockingDialogDismissListener
    {
        /**
         * Called on UI thread when a non-blocking dialog has just been dismissed.
         * {@link PlayerClientListener#isNonBlockingDialogVisible()} is now false.
         * @param srcDialog  The dialog instance that was dismissed (JDialog, etc).
         * @param wasCanceled  True if the dialog was closed or canceled, instead of selecting an action.
         *     Message popup dialogs have the "OK" button as their action and use {@code false} for this parameter.
         */
        public void dialogDismissed(Object srcDialog, boolean wasCanceled);
    }

}
