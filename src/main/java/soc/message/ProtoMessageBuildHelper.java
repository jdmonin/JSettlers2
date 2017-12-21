/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;

import soc.game.SOCGame;
import soc.proto.Data;

/**
 * Common helper functions for building Protobuf messages to send from the server or client.
 *
 * @since 3.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public abstract class ProtoMessageBuildHelper
{

    /**
     * Return the protobuf {@code Data.SeatLockState} enum value (object) for this game state.
     * @param sls  Seat lock state from {@link SOCGame#getSeatLock(int)}, like {@link SOCGame.SeatLockState#LOCKED}
     * @return  Protobuf enum value for {@code sls}, like {@link Data.SeatLockState#LOCKED},
     *    or {@code Data.SeatLockState#UNRECOGNIZED}; never null
     */
    public static final Data.SeatLockState toMsgSeatLockState(final SOCGame.SeatLockState sls)
    {
        switch (sls)
        {
        case CLEAR_ON_RESET:
            return Data.SeatLockState.CLEAR_ON_RESET;
        case LOCKED:
            return Data.SeatLockState.LOCKED;
        case UNLOCKED:
            return Data.SeatLockState.UNLOCKED;
        default:
            return Data.SeatLockState.UNRECOGNIZED;
        }
    }

}
