/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017-2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.proto.Data;
import soc.proto.GameMessage;

/**
 * Common helper functions for building Protobuf messages to send from the server or client.
 *
 * @since 3.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public abstract class ProtoMessageBuildHelper
{
    /**
     * Build a protobuf {@code Data.ResourceSet.Builder} from this {@code SOCResourceSet}'s known resources.
     * Unknown resources are ignored.
     * @param rs  Resource set to build from; not {@code null}
     * @return  A {@code Data.ResourceSet.Builder} from {@code rs}
     * @throws NullPointerException if {@code rs == null}
     */
    public static final Data.ResourceSet.Builder toResourceSet(final SOCResourceSet rs)
        throws NullPointerException
    {
        Data.ResourceSet.Builder rsb = Data.ResourceSet.newBuilder();

        int n = rs.getAmount(SOCResourceConstants.CLAY);
        if (n != 0)
            rsb.setClay(n);
        n = rs.getAmount(SOCResourceConstants.ORE);
        if (n != 0)
            rsb.setOre(n);
        n = rs.getAmount(SOCResourceConstants.SHEEP);
        if (n != 0)
            rsb.setSheep(n);
        n = rs.getAmount(SOCResourceConstants.WHEAT);
        if (n != 0)
            rsb.setWheat(n);
        n = rs.getAmount(SOCResourceConstants.WOOD);
        if (n != 0)
            rsb.setWood(n);

        return rsb;
    }

    /**
     * Return the protobuf {@code Data.DevCardValue} enum value (object) for this development card constant.
     * @param card  Type of development card, like {@link SOCDevCardConstants#ROADS}
     *     or {@link SOCDevCardConstants#UNKNOWN}
     * @return  Protobuf enum value for {@code card}, like {@link Data.DevCardValue#ROAD_BUILDING}
     *     or {@link Data.DevCardValue#UNKNOWN_DEV_CARD}, or {@code null} if not recognized
     * @see #isDevCardVP(DevCardValue)
     */
    public static final Data.DevCardValue toDevCardValue(final int card)
    {
        final Data.DevCardValue dcv;

        switch (card)
        {
        case SOCDevCardConstants.UNKNOWN:
            dcv = Data.DevCardValue.UNKNOWN_DEV_CARD;  break;
        case SOCDevCardConstants.ROADS:
            dcv = Data.DevCardValue.ROAD_BUILDING;  break;
        case SOCDevCardConstants.DISC:
            dcv = Data.DevCardValue.YEAR_OF_PLENTY;  break;
        case SOCDevCardConstants.MONO:
            dcv = Data.DevCardValue.MONOPOLY;  break;
        case SOCDevCardConstants.CAP:
            dcv = Data.DevCardValue.VP_GREAT_HALL;  break;
        case SOCDevCardConstants.MARKET:
            dcv = Data.DevCardValue.VP_MARKET;  break;
        case SOCDevCardConstants.UNIV:
            dcv = Data.DevCardValue.VP_UNIVERSITY;  break;
        case SOCDevCardConstants.TEMP:
            dcv = Data.DevCardValue.VP_LIBRARY;  break;
        case SOCDevCardConstants.CHAPEL:
            dcv = Data.DevCardValue.VP_CHAPEL;  break;
        case SOCDevCardConstants.KNIGHT:
            dcv = Data.DevCardValue.KNIGHT;  break;
        default:
            dcv = null;
        }

        return dcv;
    }

    /**
     * Is this development card worth a Victory Point?
     * True if its cardNumber modulo 100 &gt;= 50.
     * @param card  The card
     * @return True if {@code card} is worth a VP
     */
    public static final boolean isDevCardVP(final Data.DevCardValue card)
    {
        return (card.getNumber() % 100) >= 50;
    }

    /**
     * Return the protobuf {@code GameMessage._PlayerElementAction} enum value (object)
     * for this {@link SOCPlayerElement} Action Type constant.
     * @param action Action type: {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN},
     *     or {@link SOCPlayerElement#LOSE}
     * @return Protobuf enum value for {@code action}, like {@link GameMessage._PlayerElementAction#GAIN},
     *    or {@code null} if not recognized
     */
    public static final GameMessage._PlayerElementAction toPlayerElementAction(final int action)
    {
        final GameMessage._PlayerElementAction act;

        switch (action)
        {
        case SOCPlayerElement.SET:
            act = GameMessage._PlayerElementAction.SET;  break;
        case SOCPlayerElement.GAIN:
            act = GameMessage._PlayerElementAction.GAIN;  break;
        case SOCPlayerElement.LOSE:
            act = GameMessage._PlayerElementAction.LOSE;  break;
        default:
            act = null;
        }

        return act;
    }

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
