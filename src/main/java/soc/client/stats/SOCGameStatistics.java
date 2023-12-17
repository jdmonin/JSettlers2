/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 *
 * This file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017,2020,2023 Jeremy D Monin <jeremy@nand.net>
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
package soc.client.stats;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import soc.game.SOCGame;
import soc.game.SOCPlayer;

/**
 * Track game statistics with event methods such as {@link #diceRolled(DiceRollEvent)}.
 * Events notify any listeners, to update stats if shown on screen in {@link GameStatisticsFrame}.
 *
 * @since 2.0.00
 */
public class SOCGameStatistics
{
    private final AtomicReference<Listener> listener;
    private final DiceRolls rolls;

    public interface Listener
    {
        void statsUpdated(SOCGameStatistics stats, GameStatisticsEvent event);

        //HACK: because the frame itself has no lifecycle and there is not an easy way to pass disposal
        //      in to its parent (building panel), pass dispose notification through this listener interface
        void statsDisposing();
    }

    public interface ListenerRegistration
    {
        void unregister();
    }

    /**
     * Game statistics event types, for listener callback to more efficiently refresh.
     * @author jdmonin
     * @since 2.7.00
     */
    public static class GameStatisticsEvent {}

    public static class DiceRollEvent
        extends GameStatisticsEvent
    {
        /** Dice result number (2-12) */
        public final int roll;

        /** Player who rolled this result */
        public final SOCPlayer player;

        public DiceRollEvent(int rolledNumber, SOCPlayer rollingPlayer)
        {
            roll = rolledNumber;
            player = rollingPlayer;
        }

        /** Includes class name, dice number, player, player number: {@code "DiceRollEvent[7 Robot 2:0]"} */
        @Override
        public String toString()
        {
            return "DiceRollEvent[" + roll + " " + player.getName() + ":" + player.getPlayerNumber() + "]";
        }
    }

    /**
     * A resource has been received by the client player from dice roll, or player has been requested
     * to pick free resources for their gold hex gains.
     * Currently holds no details, because GameStatisticsFrame gets all trade stats from their {@link SOCPlayer} data.
     * @author jdmonin
     * @since 2.7.00
     */
    public static class ResourceRollReceivedEvent
        extends GameStatisticsEvent
    {
        public static final ResourceRollReceivedEvent SINGLETON = new ResourceRollReceivedEvent();
    }

    /**
     * A resource trade (with bank, port, or a player) has been made by the client player.
     * Currently holds no details, because GameStatisticsFrame gets all trade stats from their {@link SOCPlayer} data.
     * @author jdmonin
     * @since 2.7.00
     */
    public static class ResourceTradeEvent
        extends GameStatisticsEvent
    {
        public static final ResourceTradeEvent SINGLETON = new ResourceTradeEvent();
    }

    public SOCGameStatistics(final SOCGame game)
    {
        listener = new AtomicReference<Listener>();
        final List<SOCPlayer> ps = Arrays.asList(game.getPlayers());
        rolls = new DiceRolls(ps);
    }

    public void dispose()
    {
        Listener old = listener.get();
        if (old != null)
            old.statsDisposing();
    }

    public ListenerRegistration addListener(Listener statsListener)
    {
        Listener old = listener.getAndSet(statsListener);
        if (old != null)
            old.statsDisposing();

        return new ListenerRegistration()
        {
            public void unregister()
            {
                SOCGameStatistics.this.listener.set(null);
            }
        };
    }

    protected void fire(final GameStatisticsEvent evt)
    {
        Listener ears = listener.get();
        if (ears != null)
            ears.statsUpdated(this, evt);
    }

    /** Update stats and call listeners for a Dice Roll event. */
    public void diceRolled(DiceRollEvent evt)
    {
        try
        {
            rolls.rollCounts[evt.player.getPlayerNumber()].incrementAndGet(evt.roll);
            fire(evt);
        }
        catch (Exception e)
        {
            System.err.println("Failed updating dice roll " + evt);
            e.printStackTrace();
        }
    }

    /**
     * Call listeners when client player receives resources from dice roll or request to pick gold hex resources.
     * (a {@link ResourceRollReceivedEvent}).
     * @since 2.7.00
     */
    public void resourceRollReceived()
    {
        fire(ResourceRollReceivedEvent.SINGLETON);
    }

    /**
     * Call listeners when client player trades resources (a {@link ResourceTradeEvent}).
     * @since 2.7.00
     */
    public void resourceTraded()
    {
        fire(ResourceTradeEvent.SINGLETON);
    }

    /**
     * Get how many times a dice result has been rolled by one player.
     * @param roll  Dice roll result (2-12)
     * @param pn  Player number
     * @return Player's roll result count, or -1 if out of range
     */
    public int getRollCount(int roll, int pn)
    {
        if (roll < 2 || roll > 12)
            return -1;
        if ((pn < 0) || (pn >= rolls.rollCounts.length))
            return -1;

        return rolls.rollCounts[pn].get(roll);
    }

    /**
     * Tracks the number of times each dice value is rolled by each player.
     */
    private static class DiceRolls
    {
        /**
         * Array (indexed by player number) of atomic arrays:
         * Each atomic array is 13 elements for dice roll counts 2-12; 0 and 1 are unused.
         */
        AtomicIntegerArray[] rollCounts;

        public DiceRolls(List<SOCPlayer> players)
        {
            rollCounts = new AtomicIntegerArray[players.size()];
            for (int i = 0; i < rollCounts.length; ++i)
                rollCounts[i] = new AtomicIntegerArray(13);
        }
    }

}
