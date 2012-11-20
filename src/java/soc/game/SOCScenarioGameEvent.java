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
 * Scenario event codes which affect the game or board, not a specific player.
 * Used by {@link SOCScenarioEventListener}s.
 * Each event also has a {@link SOCGameOption} to indicate its scenario rules are active; see enum value javadocs.
 * @see SOCScenarioPlayerEvent
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public enum SOCScenarioGameEvent
{
    /**
     * A hex hidden by fog has been revealed by road placement.
     * Game option {@link SOCGameOption#K_SC_FOG _SC_FOG}.
     * Revealing a land hex (clay, ore, sheep, wheat, wood)
     * gives the player 1 of that resource immediately.
     *<P>
     * This event fires only at the server.
     * During initial placement, placing a settlement at a node
     * next to fog could reveal up to 3 hexes.  This event
     * fires once for each hex revealed.
     *<P>
     * In {@link SOCScenarioEventListener#gameEvent(SOCGame, SOCScenarioGameEvent, Object)},
     * <tt>detail</tt> is the revealed hex's coordinate as an Integer.
     */
    SGE_FOG_HEX_REVEALED(0x01);

    /**
     * Value for sending event codes over a network.
     * Each event code must be a different bit. (0x01, 0x02, 0x04, etc)
     */
    public final int flagValue;

    private SOCScenarioGameEvent(final int fv)
    {
        flagValue = fv; 
    }
}
