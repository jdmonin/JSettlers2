/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012-2013 Jeremy D Monin <jeremy@nand.net>
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
 *<P>
 * Not all scenario-related rules changes have or need an event. For example, in
 * {@link SOCGameOption#K_SC_PIRI _SC_PIRI}, the Knight/Soldier card is used only to
 * convert ships to warships.  This happens every time the card is played, so there's
 * no event for it.  The game/server logic for playing dev cards checks for _SC_PIRI
 * right there, instead of code elsewhere in an event listener.  However, in
 * {@link SOCGameOption#K_SC_SANY _SC_SANY}, the player will <em>sometimes</em> get an
 * SVP for settling a new island; it doesn't happen each time the player builds a settlement.
 * So, a scenario event communicates the new SVP.
 *
 * @see SOCScenarioGameEvent
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public enum SOCScenarioPlayerEvent
{
    // Note: Some SOCServer code assumes that player events are fired only during handlePUTPIECE.
    // If a new player event breaks this assumption, adjust SOCServer.playerEvent(...) and related code;
    // search where SOCGame.pendingMessagesOut is used.

    /**
     * Special victory point awarded for first settlement in any land area past the starting land area.
     * Once per player per game (not once per player in each other land area).
     * Game option {@link SOCGameOption#K_SC_SANY _SC_SANY}.
     *<P>
     * The new {@link SOCSettlement} will be passed as <tt>obj</tt> to
     * {@link SOCScenarioEventListener#playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)}.
     */
    SVP_SETTLED_ANY_NEW_LANDAREA(0x01),

    /**
     * 2 SVP awarded each time player settles in another new land area past the starting land area.
     * Once per area per player per game.
     * Game option {@link SOCGameOption#K_SC_SEAC _SC_SEAC}.
     *<P>
     * The new {@link SOCSettlement} will be passed as <tt>obj</tt> to
     * {@link SOCScenarioEventListener#playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)}.
     *<P>
     * Because there can be many land areas, this event flag isn't part of
     * {@link SOCPlayer#getScenarioPlayerEvents()}; instead see
     * {@link SOCPlayer#getScenarioSVPLandAreas()}.
     */
    SVP_SETTLED_EACH_NEW_LANDAREA(0x02),

    /**
     * Cloth trade route established with a neutral {@link SOCVillage village}.
     * (Player cannot move the Pirate before Cloth Trade is established.)
     * Once per player per game, although the player is free to make routes to other villages.
     * This event flag doesn't immediately give the player an SVP;
     * players gain VP by having pairs of cloth.
     * Villages are in a game only if option {@link SOCGameOption#K_SC_CLVI} is set.
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
