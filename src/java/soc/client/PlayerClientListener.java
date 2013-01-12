/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 *
 * This file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
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

import soc.game.SOCGame;
import soc.game.SOCPlayer;

/**
 * A listener on the {@link SOCPlayerClient} to decouple the presentation from the networking.
 * This presents the facade of the UI to the networking layer.
 * <br/>
 * The notification methods of this API accept "event" objects rather than multiple arguments for
 * several reasons. The primary reasons are that the methods are definitively identified as event
 * handling methods and not confused with other like-named methods on another interface, and the
 * event objects can easily be augmented with paramters in the future without breaking the API
 * of implementors of this interface. Also, the event objects can be made immutable which greatly
 * simplifies determining thread safety.
 * <br/>
 * The event objects are defined as interfaces to allow for flexibility in implementation. Instead
 * of several general-purpose concrete types with various configurations of values for parameters,
 * the interfaces are defined with only the information they are known to have.
 */
public interface PlayerClientListener
{
    /**
     * Receive a notification that the current player has rolled the dice.
     * @param evt
     */
    void diceRolled(DiceRollEvent evt);
    
    void playerJoined(PlayerJoinEvent evt);
    void playerLeft(PlayerLeaveEvent evt);
    void playerSitdown(PlayerSeatEvent evt);
    void playerTurnSet(PlayerTurnEvent evt);
    void playerPiecePlaced(PlayerPiecePlacedEvent evt);
    void playerPieceMoved(PlayerPieceMovedEvent evt);
    void playerSVPAwarded(PlayerSvpEvent evt);
    void playerDevCardUpdated(SOCPlayer player);
    void playerFaceChanged(SOCPlayer player, int faceId);
    
    /**
     * @param player May be {@code null}
     * @param utype The type of element to update
     */
    void playerElementUpdated(SOCPlayer player, UpdateType utype);
    void playerResourcesUpdated(SOCPlayer player);
    void playerStats(EnumMap<PlayerClientListener.UpdateType, Integer> stats);
    
    void requestedDiscard(int countToDiscard);
    void requestedResourceSelect(int countToSelect);
    void requestedGoldResourceSelect(SOCPlayer player, int countToSelect);
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
    
    void membersListed(MemberListEvent evt);
    void boardLayoutUpdated(BoardLayoutEvent evt);
    void boardUpdated(BoardUpdateEvent evt);
    void boardPotentialsUpdated();
    void boardReset(SOCGame newGame, int newSeatNumber, int requestingPlayerNumber);
    void boardResetVoteRequested(SOCPlayer requestor);
    void boardResetVoteCast(SOCPlayer voter, boolean vote);
    void boardResetVoteRejected();
    void robberMoved();
    void devCardDeckUpdated();
    void seatLockUpdated();
    
    void gameStarted(GameStartEvent evt);
    void gameStateChanged(GameStateEvent evt);
    void gameEnded(GameEndedEvent evt);
    
    void gameDisconnected(String errorMessage);

    void messageBroadcast(String message);
    void messageSent(String nickname, String message);
    
    void buildRequestCanceled(SOCPlayer player);
    
    interface DiceRollEvent
    {
        /**
         * The sum of the dice rolled. May be <tt>-1</tt> for some game events.
         */
        int getResult();
        
        /**
         * May be {@code null} if the current player was null when the dice roll was received from the server.
         */
        SOCPlayer getPlayer();
    }
    
    interface PlayerJoinEvent
    {
        /**
         * The player name. Will not be {@code null}
         */
        String getNickname();
    }
    
    interface PlayerLeaveEvent
    {
        /**
         * The player name. Will not be {@code null}
         */
        String getNickname();
        
        /**
         * May be {@code null} if the current player is an observer.
         */
        SOCPlayer getPlayer();
    }
    
    interface PlayerSeatEvent
    {
        int getSeatNumber();
        /**
         * The player name. Will not be {@code null}
         */
        String getNickname();
    }
    
    interface PlayerTurnEvent
    {
        int getSeatNumber();
    }
    
    interface PlayerPiecePlacedEvent
    {
        SOCPlayer getPlayer();
        int getCoordinate();
        /**
         * @return A piece type identifier, such as {@link SOCPlayingPiece#CITY}
         */
        int getPieceType();
    }
    
    interface PlayerPieceMovedEvent
    {
        SOCPlayer getPlayer();
        int getSourceCoordinate();
        int getTargetCoordinate();
        /**
         * @return A piece type identifier, such as {@link SOCPlayingPiece#CITY}
         */
        int getPieceType();
    }
    
    interface PlayerSvpEvent
    {
        /**
         * @return The player awarded special victory points. Will not be {@code null}
         */
        SOCPlayer getPlayer();
        /** The count of how many new special victory points were awarded */
        int getNumSvp();
        /** A user-display message describing the reason for the award */
        String getAwardDescription();
    }
    
    interface MemberListEvent
    {
        Collection<String> getNames();
    }
    
    interface BoardLayoutEvent
    {
        // nothing yet
    }
    
    interface BoardUpdateEvent
    {
        // nothing yet
    }
    
    interface GameStartEvent
    {
        // nothing yet
    }
    
    interface GameStateEvent
    {
        /**
         * @return One of the codes from SOCGame, such as {@link soc.game.SOCGame#NEW}
         */
        int getGameState();
    }
    
    interface GameEndedEvent
    {
        int[] getScores();
    }
    
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
