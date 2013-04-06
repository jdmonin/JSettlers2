/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 *
 * This file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2013 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;

/**
 * A listener on the {@link SOCPlayerClient} to decouple the presentation from the networking.
 * This presents the facade of the UI to the networking layer.
 * The game data ({@link SOCGame}, {@link SOCPlayer} methods) will be updated before
 * these methods are called, so you can call game-object methods for more details on the new event.
 * @author paulbilnoski
 * @since 2.0.00
 */
public interface PlayerClientListener
{
    /**
     * Receive a notification that the current player has rolled the dice.
     * Call this after updating game state with the roll result.
     * @param player May be {@code null} if the current player was null when the dice roll was received from the server.
     * @param result The sum of the dice rolled. May be <tt>-1</tt> for some game events.
     */
    void diceRolled(SOCPlayer player, int result);

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
     * A client has sat down to become an active player.
     * {@link #playerJoined(String)} was called earlier on this client.
     * {@link SOCGame#addPlayer(String, int)} has just been called.
     * @param playerNumber  New player's playerNumber in the game; the seat number they've sat down at
     * @param nickname  New player's name
     */
    void playerSitdown(int playerNumber, String nickname);

    /**
     * Game's current player has changed. Update displays.
     * @param playerNumber  New current player number whose turn it is.
     */
    void playerTurnSet(int playerNumber);

    /**
     * A player has placed a piece on the board.
     * @param pieceType A piece type identifier, such as {@link SOCPlayingPiece#CITY}
     */
    void playerPiecePlaced(SOCPlayer player, int coordinate, int pieceType);

    /**
     * A player has moved a piece on the board.
     * Most pieces are not movable.  {@link SOCShip} can sometimes be moved.
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
     * A player has been awarded Special Victory Point(s).
     * @param player The player awarded special victory points. Will not be {@code null}
     * @param numSvp The count of how many new special victory points were awarded
     * @param awardDescription A user-display message describing the reason for the award
     */
    void playerSVPAwarded(SOCPlayer player, int numSvp, String awardDescription);

    /**
     * A player is drawing or playing a development card.
     * @param player  The player
     */
    void playerDevCardUpdated(SOCPlayer player);

    /**
     * A player has changed their face icon.
     * @param player  The player
     * @param faceId  New face icon number;
     *            1 and higher are human face images, 0 is the default robot, -1 is the smarter robot.
     */
    void playerFaceChanged(SOCPlayer player, int faceId);

    /**
     * Update one part of the player's status, such as their number of settlements remaining.
     * @param player May be {@code null}
     * @param utype The type of element to update
     */
    void playerElementUpdated(SOCPlayer player, UpdateType utype);

    /**
     * A player's total resource count has been updated.
     * @param player  The player
     */
    void playerResourcesUpdated(SOCPlayer player);

    /**
     * A player's game stats, such as resource totals received from dice rolls, should be displayed.
     * Called at end of game, or when the player uses the *STATS* command.
     * @param stats  Player statistic details
     */
    void playerStats(EnumMap<PlayerClientListener.UpdateType, Integer> stats);

    /**
     * The game requests that the client player discard a particular number of resource cards.
     * @param countToDiscard  Must choose and discard this many cards
     */
    void requestedDiscard(int countToDiscard);

    /**
     * The client player gets some free resources of their choice.
     * Used with "Year of Plenty"/"Discovery" cards, and the Gold Hex.
     * @param countToSelect  Must choose this many resources
     * @see #requestedGoldResourceCountUpdated(SOCPlayer, int)
     */
    void requestedResourceSelect(int countToSelect);

    /**
     * This player must pick this many gold-hex resources, or no longer needs to pick them.
     * Informational only: do not ask the client player to pick resources,
     * {@link #requestedResourceSelect(int)} is used for that.
     * @param player  The player
     * @param countToSelect  Number of free resources they must pick, or 0 if they've just picked them
     */
    void requestedGoldResourceCountUpdated(SOCPlayer player, int countToSelect);

    /**
     * This player must choose a player for robbery.
     * @param choices   The potential victim players to choose from
     * @param isNoneAllowed  If true, player can choose to rob no one (game scenario <tt>SC_PIRI</tt>)
     * @see SOCPlayerClient.GameManager#choosePlayer(SOCGame, int)
     */
    void requestedChoosePlayer(List<SOCPlayer> choices, boolean isNoneAllowed);

    void requestedChooseRobResourceType(SOCPlayer player);
    void requestedTrade(SOCPlayer offerer);

    /**
     * @param offerer May be {@code null}
     */
    void requestedTradeClear(SOCPlayer offerer);
    void requestedTradeRejection(SOCPlayer rejecter);

    /**
     * @param playerToReset May be {@code null} to clear all seats
     */
    void requestedTradeReset(SOCPlayer playerToReset);
    void requestedSpecialBuild(SOCPlayer player);
    void requestedDiceRoll();

    /** The largest army might have changed, so update */
    void largestArmyRefresh(SOCPlayer old, SOCPlayer potentialNew);

    /** The longest road might have changed, so update */
    void longestRoadRefresh(SOCPlayer old, SOCPlayer potentialNew);

    /**
     * The current game members (players and observers) are listed, and the game is about to start.
     * @param names  Game member names; to see if each is a player, call {@link SOCGame#getPlayer(String)}.
     */
    void membersListed(Collection<String> names);
    void boardLayoutUpdated();
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
    void boardReset(SOCGame newGame, int newSeatNumber, int requestingPlayerNumber);
    void boardResetVoteRequested(SOCPlayer requestor);
    void boardResetVoteCast(SOCPlayer voter, boolean vote);
    void boardResetVoteRejected();
    void robberMoved();
    void devCardDeckUpdated();
    void seatLockUpdated();

    void gameStarted();

    /**
     * Update interface after game state has changed.
     * Please call after {@link SOCGame#setGameState(int)}.
     * @param gameState One of the codes from SOCGame, such as {@link soc.game.SOCGame#NEW}
     */
    void gameStateChanged(int gameState);
    void gameEnded(Map<SOCPlayer, Integer> scores);
    
    void gameDisconnected(String errorMessage);

    void messageBroadcast(String message);

    /**
     * A game text message was received from server, or a chat message from another player.
     * @param nickname  Player's nickname, or {@code null} for messages from the server itself
     * @param message  Message text
     */
    void messageSent(String nickname, String message);
    
    void buildRequestCanceled(SOCPlayer player);

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
        Unknown,
        Resources,
        
        Road,
        Settlement,
        City,
        Ship,
        Knight,
        Warship,
        Cloth,
        
        VictoryPoints,
        SpecialVictoryPoints,
        DevCards,
        LongestRoad,
        LargestArmy
    }

}
