/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 *
 * This file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2013-2017 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import soc.game.SOCGame;
import soc.game.SOCGameOption;  // for javadocs only
import soc.game.SOCInventoryItem;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.game.SOCSpecialItem;

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
 * before taking that kind of automatic action. If the UI might do something when that dialog is
 * no longer visible, such as start a countdown to automatically roll dice, register a listener
 * by calling {@link #setNonBlockingDialogDismissListener(NonBlockingDialogDismissListener)}.
 *<P>
 * The classic UI implementing this interface is {@link SOCPlayerInterface.ClientBridge}.
 *
 * @author paulbilnoski
 * @since 2.0.00
 */
public interface PlayerClientListener
{
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
     * A player has been awarded Special Victory Point(s).
     * @param player The player awarded special victory points. Will not be {@code null}
     * @param numSvp The count of how many new special victory points were awarded
     * @param awardDescription A user-display message describing the reason for the award
     */
    void playerSVPAwarded(SOCPlayer player, int numSvp, String awardDescription);

    /**
     * A player is drawing or playing a development card, or a card or special
     * {@link SOCInventoryItem} has been added or removed from their hand's inventory.
     *<P>
     * If this inventory update comes from playing a special {@link SOCInventoryItem}, call
     * {@link #playerCanCancelInvItemPlay(SOCPlayer, boolean)} after calling this method.
     *
     * @param player  The player
     * @param addedPlayable  True if the update added a dev card or item that's playable now
     */
    void playerDevCardUpdated(SOCPlayer player, final boolean addedPlayable);

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
     * @param isBad True if this update is bad news from a message that the player has lost resources
     *     or pieces (to the robber, monopoly, etc)
     */
    void playerElementUpdated(SOCPlayer player, UpdateType utype, boolean isBad);

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
     * @param countToSelect  Number of free resources they must pick, or 0 if they've just picked them
     */
    void requestedGoldResourceCountUpdated(SOCPlayer player, int countToPick);

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

    /**
     * The robber or pirate was moved onto a hex.
     * @param newHex  The new robber/pirate hex coordinate, or 0 to take the pirate off the board
     * @param isPirate  True if the pirate, not the robber, was moved
     */
    void robberMoved(int newHex, boolean isPirate);

    void devCardDeckUpdated();
    void seatLockUpdated();

    // This javadoc also appears in SOCPlayerInterface; please also update there if it changes.
    /**
     * Is a dialog or popup message currently visible while gameplay continues?
     * See interface javadoc for details and implications.
     *<P>
     * To do things when the dialog is no longer visible, you can register a listener with
     * {@link #setNonBlockingDialogDismissListener(NonBlockingDialogDismissListener)}.
     *
     * @return  True if such a dialog is visible
     */
    boolean isNonBlockingDialogVisible();

    // This javadoc also appears in SOCPlayerInterface; please also update there if it changes.
    /**
     * Set or clear the {@link NonBlockingDialogDismissListener listener}
     * for when {@link #isNonBlockingDialogVisible()}'s dialog is no longer visible.
     * @param li  Listener, or {@code null} to clear
     */
    void setNonBlockingDialogDismissListener(NonBlockingDialogDismissListener li);

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
     * If the game is now starting, please call in this order:
     *<code><pre>
     *   game.setGameState(newState);
     *   {@link #gameStarted()};
     *   {@link #gameStateChanged(int)};
     *</pre></code>
     * @param gameState One of the states from SOCGame, such as {@link soc.game.SOCGame#NEW}
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

    /**
     * A player's {@link soc.message.SOCSimpleRequest "simple request"} has been sent to the entire game, or the server
     * has replied to our own simple request, and this should be displayed.
     * This method lets us display simple things from the server without defining a lot of small similar methods.
     *<P>
     * If other game data messages are sent (resource gains/loss, etc), or other client code must update that data
     * based on info in the SOCSimpleRequest, this method will be called only after other game data is updated.
     * Some SimpleRequest {@code reqtype}s update the game data: Client must call
     * {@link SOCDisplaylessPlayerClient#handleSIMPLEREQUEST(Map, soc.message.SOCSimpleRequest)}
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
     * @param gi  Picked this index within game's Special Item list, or -1
     * @param pi  Picked this index within {@code pl}'s Special Item list, or -1
     * @param isPick  True if calling for {@code PICK}, false if server has {@code DECLINE}d the client player's request
     * @param coord  Optional coordinates on the board for this item, or -1. An edge or a node, depending on item type
     * @param level  Optional level of construction or strength, or 0
     * @param sv  Optional string value from {@link SOCSpecialItem#getStringValue()}, or {@code null}
     * @see #playerSetSpecialItem(String, SOCGame, SOCPlayer, int, int, boolean)
     * @see SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)
     */
    void playerPickSpecialItem
        (final String typeKey, final SOCGame ga, final SOCPlayer pl, final int gi, final int pi, final boolean isPick,
         final int coord, final int level, final String sv);

    /**
     * Show the results of a player's {@code SET} or {@code CLEAR} of a known {@link SOCSpecialItem Special Item}.
     *<P>
     * To see which scenario and option {@code typeKey}s set or clear Special Items,
     * and scenario-specific usage details, see the {@link SOCSpecialItem} class javadoc.
     *
     * @param typeKey  Item's {@code typeKey}, as described in the {@link SOCSpecialItem} class javadoc
     * @param ga  Game containing {@code pl} and special items
     * @param pl  Requesting player; never {@code null}
     * @param gi  Set or clear this index within game's Special Item list, or -1
     * @param pi  Set or clear this index within {@code pl}'s Special Item list, or -1
     * @param isSet  True if player has set, false if player has cleared, this item index
     * @see #playerPickSpecialItem(String, SOCGame, SOCPlayer, int, int, boolean, int, int, String)
     * @see SOCSpecialItem#playerSetItem(String, SOCGame, SOCPlayer, int, int, boolean)
     */
    void playerSetSpecialItem
        (final String typeKey, final SOCGame ga, final SOCPlayer pl, final int gi, final int pi, final boolean isSet);

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
        /** Update Total Resource count only. */
        Resources,
        /** Update Total Resource count, and also each box (Clay,Ore,Sheep,Wheat,Wood) if shown. */
        ResourceTotalAndDetails,

        Road,
        Settlement,
        City,
        Ship,
        Knight,

        /**
         * Total number of resources picked/gained from gold hex reveals
         * in sea board scenarios; announced in game window's activity pane and used in stats.
         * Update not sent if gain is 0.
         */
        GoldGains,

        /** Number of Warships built, in {@link SOCGameOption#K_SC_PIRI _SC_PIRI} scenario */
        Warship,

        /** Cloth Count update, in {@link SOCGameOption#K_SC_CLVI _SC_CLVI} scenario */
        Cloth,

        /** Wonder build level, in {@link SOCGameOption#K_SC_WOND _SC_WOND} scenario */
        WonderLevel,

        VictoryPoints,
        SpecialVictoryPoints,
        DevCards,
        LongestRoad,
        LargestArmy
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
