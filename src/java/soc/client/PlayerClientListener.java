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
import java.util.Map;

import soc.game.SOCGame;
import soc.game.SOCPlayer;

/**
 * A listener on the {@link SOCPlayerClient} to decouple the presentation from the networking.
 * This presents the facade of the UI to the networking layer.
 */
public interface PlayerClientListener
{
    /**
     * Receive a notification that the current player has rolled the dice.
     * @param player May be {@code null} if the current player was null when the dice roll was received from the server.
     * @param result The sum of the dice rolled. May be <tt>-1</tt> for some game events.
     */
    void diceRolled(SOCPlayer player, int result);
    
    void playerJoined(String nickname);
    
    /**
     * @param nickname The player name. Will not be {@code null}
     * @param player May be {@code null} if the current player is an observer.
     */
    void playerLeft(String nickname, SOCPlayer player);
    void playerSitdown(int seatNumber, String nickname);

    /**
     * Game's current player has changed. Update displays.
     * @param seatNumber  New current player number
     */
    void playerTurnSet(int seatNumber);

    /**
     * @param pieceType A piece type identifier, such as {@link SOCPlayingPiece#CITY}
     */
    void playerPiecePlaced(SOCPlayer player, int coordinate, int pieceType);
    
    /**
     * @param pieceType A piece type identifier, such as {@link SOCPlayingPiece#CITY}
     */
    void playerPieceMoved(SOCPlayer player, int sourceCoordinate, int targetCoordinate, int pieceType);
    /**
     * @param player The player awarded special victory points. Will not be {@code null}
     * @param numSvp The count of how many new special victory points were awarded
     * @param awardDescription A user-display message describing the reason for the award
     */
    void playerSVPAwarded(SOCPlayer player, int numSvp, String awardDescription);
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
    
    void membersListed(Collection<String> names);
    void boardLayoutUpdated();
    void boardUpdated();
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
     * @param gameState One of the codes from SOCGame, such as {@link soc.game.SOCGame#NEW}
     */
    void gameStateChanged(int gameState);
    void gameEnded(Map<SOCPlayer, Integer> scores);
    
    void gameDisconnected(String errorMessage);

    void messageBroadcast(String message);
    void messageSent(String nickname, String message);
    
    void buildRequestCanceled(SOCPlayer player);
    
    void debugFreePlaceModeToggled(boolean isEnabled);
    
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
