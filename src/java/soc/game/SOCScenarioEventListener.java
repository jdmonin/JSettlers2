/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012 Jeremy D Monin <jeremy@nand.net>
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
package soc.game;

/**
 * Listener for scenario events on the {@link SOCGame#hasSeaBoard large sea board}.
 *<P>
 * <em>Threads:</em> These events occur in game methods (or player methods) that change game state.
 * So, whatever thread changed the game state, that same thread will run the listener callback method.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public interface SOCScenarioEventListener
{
    /**
     * A per-player scenario event has occurred.
     * @param ga  Game
     * @param pl  Player
     * @param evt  Event code
     */
    public void playerEvent(final SOCGame ga, final SOCPlayer pl, final SOCScenarioPlayerEvent evt);

    // A per-game scenario event has occurred. (None defined yet)
    // public void gameEvent(final SOCGame ga, final SOCScenarioGameEvent evt);
}
