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
 * Per-player scenario event codes.
 * Used by {@link SOCScenarioEventListener}s.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public enum SOCScenarioPlayerEvent
{
    /**
     * Special victory point awarded for first settlement in any land area past the starting land area.
     * Once per player per game (not once per player in each other land area).
     */
    SVP_SETTLED_ANY_NEW_LANDAREA(1);

    /**
     * Value for sending event codes over a network.
     * Each event code must be a different bit. (0x01, 0x02, 0x04, etc)
     */
    public final int flagValue;

    private SOCScenarioPlayerEvent(final int fv)
    {
        flagValue = fv; 
    }
}
