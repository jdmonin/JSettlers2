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
}
