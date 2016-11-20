/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 *
 * This file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

public class SOCGameStatistics
{
    private final AtomicReference<Listener> listener;
    private final SOCGame game;
    private final DiceRolls rolls;

    public interface Listener
    {
        void statsUpdated(SOCGameStatistics stats);

        //HACK: because the frame itself has no lifecycle and there is not an easy way to pass disposal
        //      in to its parent (building panel), pass dispose notification through this listener interface
        void statsDisposing();
    }

    public interface ListenerRegistration
    {
        void unregister();
    }

    public static class DiceRollEvent
    {
        public final int roll;
        public final SOCPlayer player;

        public DiceRollEvent(int roll, SOCPlayer p)
        {
            this.roll = roll;
            player = p;
        }

        @Override
        public String toString()
        {
            return "DiceRollEvent["+roll+" "+player.getName()+":"+player.getPlayerNumber()+"]";
        }
    }

    public SOCGameStatistics(SOCGame game)
    {
        this.game = game;
        listener = new AtomicReference<Listener>();
        List<SOCPlayer> ps = Arrays.asList(game.getPlayers());
        rolls = new DiceRolls(ps);
    }

    public void dispose()
    {
        Listener old = this.listener.get();
        if (old != null)
        {
            old.statsDisposing();
        }
    }

    public ListenerRegistration addListener(Listener listener)
    {
        Listener old = this.listener.getAndSet(listener);
        if (old != null)
        {
            old.statsDisposing();
        }

        return new ListenerRegistration()
        {
            public void unregister()
            {
                SOCGameStatistics.this.listener.set(null);
            }
        };
    }

    protected void fire()
    {
        Listener ears = listener.get();
        if (ears != null)
            ears.statsUpdated(this);
    }

    public void diceRolled(DiceRollEvent evt)
    {
        try
        {
            rolls.rollCounts[evt.player.getPlayerNumber()].incrementAndGet(evt.roll);
            fire();
        }
        catch (Exception e)
        {
            System.err.println("Failed updating dice roll "+evt);
            e.printStackTrace();
        }
    }

    /**
     * @param roll
     * @param playerId
     * @return {@code null} If out of range
     */
    public Integer getRollCount(int roll, int playerId)
    {
        if (roll < 2 || roll > 12)
            return null;
        if (playerId < 0 || playerId >= game.getPlayers().length)
            return null;
        int r = rolls.rollCounts[playerId].get(roll);
        return Integer.valueOf(r);
    }

    /**
     * Tracks the number of times each dice value is rolled by each player.
     */
    private static class DiceRolls
    {
        // indexed by player-id, each array is 13 elements for dice roll counts, 0 and 1 are unused
        AtomicIntegerArray[] rollCounts;

        public DiceRolls(List<SOCPlayer> players)
        {
            rollCounts = new AtomicIntegerArray[players.size()];
            for (int i=0; i<players.size(); ++i)
                rollCounts[i] = new AtomicIntegerArray(13);
        }
    }
}
