/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012-2013,2015-2017 Jeremy D Monin <jeremy@nand.net>
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
 * {@link SOCGameOption#K_SC_PIRI _SC_PIRI} the Knight/Soldier card is used only to
 * convert ships to warships.  This happens every time the card is played, so there's
 * no event for it.  The game/server logic for playing dev cards checks for {@code _SC_PIRI}
 * right there, instead of code elsewhere in an event listener.  However, in
 * {@link SOCGameOption#K_SC_SANY _SC_SANY} the player will <em>sometimes</em> get an
 * SVP for settling a new island; it doesn't happen each time the player builds a settlement.
 * So, a scenario event communicates the new SVP there.
 *
 * @see SOCScenarioGameEvent
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public enum SOCScenarioPlayerEvent
{
    // Note: Some server code assumes that player events are fired only during SOCGameMessageHandler.handlePUTPIECE.
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
     *<P>
     * Villages are in a game only if scenario option {@link SOCGameOption#K_SC_CLVI _SC_CLVI} is set.
     */
    CLOTH_TRADE_ESTABLISHED_VILLAGE(0x04),

    /**
     * Player's corresponding pirate fortress ({@link SOCFortress}) has been recaptured.
     * Scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI}.
     *<P>
     * The fortress has now been recaptured as a settlement owned by the player.
     * The new {@link SOCSettlement} will be passed as {@code obj} to
     * {@link SOCScenarioEventListener#playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)}.
     */
    PIRI_FORTRESS_RECAPTURED(0x08),

    /**
     * Dev Card awarded for reaching a Special Edge that gives that reward
     * (Special Edge type {@link SOCBoardLarge#SPECIAL_EDGE_DEV_CARD}).
     * Once the edge is claimed, no other player can be rewarded at that edge, but there are others on the board.
     * Game option {@link SOCGameOption#K_SC_FTRI _SC_FTRI}.
     *<P>
     * The edge coordinate and dev card type will be passed in an {@link soc.util.IntPair IntPair} as {@code obj} to
     * {@link SOCScenarioEventListener#playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)}.
     * The server has the full game state and knows the dev card type revealed. At the client, the event's dev card
     * type will be {@link SOCDevCardConstants#UNKNOWN}, and the server must send a message to the player's client
     * with the awarded card type, as if they have just purchased it. Other players' clients will be sent
     * {@code UNKNOWN} since each player's dev cards in hand are a secret.
     *<P>
     * At server and at each client, the game will clear the Special Edge's type before firing the event.
     * After the event, for clarity the server will also send a message to the game to clear the edge.
     */
    DEV_CARD_REACHED_SPECIAL_EDGE(0x10),

    /**
     * Special victory point awarded for reaching a Special Edge that gives that reward
     * (Special Edge type {@link SOCBoardLarge#SPECIAL_EDGE_SVP}).
     * Once the edge is claimed, no other player can be rewarded at that edge, but there are others on the board.
     * Game option {@link SOCGameOption#K_SC_FTRI _SC_FTRI}.
     *<P>
     * The edge coordinate will be passed as {@code obj} to
     * {@link SOCScenarioEventListener#playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)}.
     *<P>
     * At server and at each client, the game will clear the Special Edge's type before firing the event.
     * After the event, for clarity the server will also send a message to the game to clear the edge.
     */
    SVP_REACHED_SPECIAL_EDGE(0x20),

    /**
     * Player's ships have reached a "gift" port, and removed that trade port from the board.  It must be
     * placed elsewhere now or later. Occurs only in scenario game option {@link SOCGameOption#K_SC_FTRI _SC_FTRI}.
     *<P>
     * An {@link soc.util.IntPair IntPair} with the port's edge coordinate and type (in range
     * {@link SOCBoard#MISC_PORT MISC_PORT} to {@link SOCBoard#WOOD_PORT WOOD_PORT}) will be passed as {@code obj} to
     * {@link SOCScenarioEventListener#playerEvent(SOCGame, SOCPlayer, SOCScenarioPlayerEvent, boolean, Object)}.
     * If the game state became {@link SOCGame#PLACING_INV_ITEM}, the player must now pick a coastal edge with an adjacent
     * settlement to place the port.  Otherwise the port's been added to their inventory as a {@link SOCInventoryItem}
     * to be placed later when possible.  Placement is done (now or later) by calling {@link SOCGame#placePort(int)}.
     *<P>
     * This event is fired at <b>server only,</b> not at client, in {@link SOCGame#removePort(SOCPlayer, int)}.
     * The server will send messages to the game's clients about the event's result.
     */
    REMOVED_TRADE_PORT(0);

    /**
     * Value for sending event codes over a network.
     * Not all player events are sent over the network; if not sent, event's {@code flagValue} can be 0.
     *<P>
     * Each event code must be a different bit. (0x01, 0x02, 0x04, etc)
     * Some events happen only once per player, {@code flagValue} is
     * also used in the player's bitmap field that tracks those
     * ({@link SOCPlayer#getScenarioPlayerEvents()}).
     */
    public final int flagValue;

    private SOCScenarioPlayerEvent(final int fv)
    {
        flagValue = fv;
    }

}
