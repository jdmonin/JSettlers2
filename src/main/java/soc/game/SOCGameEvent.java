/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012-2014,2019-2020 Jeremy D Monin <jeremy@nand.net>
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
 * Scenario event codes which affect the game or board, not only a specific player.
 * Used by {@link SOCGameEventListener}s.
 * Each event also has a {@link SOCGameOption} to indicate its scenario rules are active; see enum value javadocs.
 *<P>
 * Not all scenario-related rules changes have or need an event, only those that <em>sometimes</em>
 * fire and sometimes don't when a player takes an action.  See {@link SOCPlayerEvent} for more details.
 *<br>
 * <b>Game Event example:</b> Not every hex is a fog hex ({@link #SGE_FOG_HEX_REVEALED}), so fog isn't
 * revealed every time a player builds a settlement or road.  If this happened each time, the scenario-related
 * code might be clearer if it was located at the game/server logic for placing pieces, instead of code
 * elsewhere in an event listener.
 *
 * @see SOCPlayerEvent
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public enum SOCGameEvent
{
    /**
     * Normal game play is starting at the end of initial placement,
     * and one or more sets of special nodes (Added Layout Parts "N1" - "N9")
     * was emptied out.  Any graphical client should redraw the board
     * to show any remaining sets.
     *<P>
     * Fired locally at server or client from {@code SOCGame.updateAtGameFirstTurn()},
     * not sent over the network. In
     * {@link SOCGameEventListener#gameEvent(SOCGame, SOCGameEvent, Object)},
     * the {@code detail} parameter is unused.
     */
    SGE_STARTPLAY_BOARD_SPECIAL_NODES_EMPTIED(0),

    /**
     * A hex hidden by fog has been revealed by road or ship placement.
     * Game option {@link SOCGameOptionSet#K_SC_FOG _SC_FOG}.
     * Revealing a normal land hex (clay, ore, sheep, wheat, wood)
     * gives the player 1 of that resource immediately.
     *<P>
     * This event fires only at the server.
     * During initial placement, placing a settlement at a node
     * next to fog could reveal up to 3 hexes.  This event
     * fires once for each hex revealed.
     *<P>
     * In {@link SOCGameEventListener#gameEvent(SOCGame, SOCGameEvent, Object)},
     * <tt>detail</tt> is the revealed hex's coordinate as an Integer.
     */
    SGE_FOG_HEX_REVEALED(0x01),

    /**
     * Special win condition for this scenario:
     * A player has won because fewer than 4 villages have cloth remaining
     * ({@link SOCScenario#SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN}).
     * The winning player's VP total might be less than {@link SOCGame#vp_winner}.
     *<P>
     * In {@link SOCGameEventListener#gameEvent(SOCGame, SOCGameEvent, Object)},
     * <tt>detail</tt> is the winning {@link SOCPlayer}.
     *<P>
     * Checked in private method <tt>SOCGame.checkForWinner_SC_CLVI()</tt>
     *<P>
     * Before firing this method, game at server sets state to {@link SOCGame#OVER}
     * and updates {@link SOCGame#getPlayerWithWin()}.
     *<P>
     * Triggered by game on server only, not sent to client.
     */
    SGE_CLVI_WIN_VILLAGE_CLOTH_EMPTY(0x02),

    /**
     * The last pirate fortress was recaptured by the current player,
     * and the pirate fleet defeated and removed from the board.
     *<P>
     * Triggered by game on server only, not sent to client.
     *
     * @see SOCPlayerEvent#PIRI_FORTRESS_RECAPTURED
     */
    SGE_PIRI_LAST_FORTRESS_FLEET_DEFEATED(0);

    /**
     * Value for sending event codes over a network.
     * Each event code must be a different bit. (0x01, 0x02, 0x04, etc)
     * Not all events are sent over a network; local-only events can use 0 for their {@code flagValue}.
     */
    public final int flagValue;

    private SOCGameEvent(final int fv)
    {
        flagValue = fv;
    }

}
