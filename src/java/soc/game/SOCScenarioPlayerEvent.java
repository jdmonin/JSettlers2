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
 * Each event also has a {@link SOCGameOption} to indicate its scenario rules are active; see enum value javadocs.
 * @see SOCScenarioGameEvent
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public enum SOCScenarioPlayerEvent
{
    /**
     * Special victory point awarded for first settlement in any land area past the starting land area.
     * Once per player per game (not once per player in each other land area).
     * Game option {@link SOCGameOption#K_SC_SANY _SC_SANY}.
     */
    SVP_SETTLED_ANY_NEW_LANDAREA(0x01),

    /**
     * 2 SVP awarded each time player settles in another new land area past the starting land area.
     * Once per area per player per game.
     * Game option {@link SOCGameOption#K_SC_SEAC _SC_SEAC}.
     */
    SVP_SETTLED_EACH_NEW_LANDAREA(0x02),

    /**
     * Cloth trade route established with a neutral {@link SOCVillage village}.
     * (Player cannot move the Pirate before Cloth Trade is established.)
     * Once per player per game, although the player is free to make routes to other villages.
     * Game option {@link SOCGameOption#K_SC_CLVI _SC_CLVI}.
     */
    CLOTH_TRADE_ESTABLISHED_VILLAGE(0x04);

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
