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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import soc.game.SOCBoard;  // for javadocs
import soc.game.SOCBoardLarge;  // for javadocs
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.proto.Data;
import soc.proto.GameMessage;

/**
 * Common helper functions for building Protobuf messages to send from the server or client
 * and translating received protobuf fields into game objects like {@link SOCResourceSet}.
 *
 * @since 3.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public abstract class ProtoMessageBuildHelper
{
    //
    // Board Coordinates
    //

    /**
     * Build a protobuf {@code Data.EdgeCoord.Builder} from this edge coordinate.
     * @param edge  An edge coordinate encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC
     * @return  A {@code Data.EdgeCoord.Builder} from {@code edge}
     * @see #toBoardCoord(int, soc.proto.Data.BoardCoord.CoordTypeCase)
     * @see #toEdgeList(Collection)
     * @see #fromEdgeCoord(soc.proto.Data.EdgeCoord)
     */
    public static final Data.EdgeCoord.Builder toEdgeCoord(final int edge)
    {
        final int r = edge >> 8, c = edge & 0xFF;
        return Data.EdgeCoord.newBuilder().setRow(r).setColumn(c);
    }

    /**
     * Build a protobuf {@code Data.HexCoord.Builder} from this hex coordinate.
     * @param hex  A hex coordinate encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC
     * @return  A {@code Data.HexCoord.Builder} from {@code hex}
     * @see #toBoardCoord(int, soc.proto.Data.BoardCoord.CoordTypeCase)
     * @see #toHexList(Collection)
     * @see #fromHexCoord(soc.proto.Data.HexCoord)
     */
    public static final Data.HexCoord.Builder toHexCoord(final int hex)
    {
        final int r = hex >> 8, c = hex & 0xFF;
        return Data.HexCoord.newBuilder().setRow(r).setColumn(c);
    }

    /**
     * Build a protobuf {@code Data.NodeCoord.Builder} from this node coordinate.
     * @param node  A node coordinate encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC
     * @return  A {@code Data.NodeCoord.Builder} from {@code node}
     * @see #toBoardCoord(int, soc.proto.Data.BoardCoord.CoordTypeCase)
     * @see #toNodeList(Collection)
     * @see #fromNodeCoord(soc.proto.Data.NodeCoord)
     */
    public static final Data.NodeCoord.Builder toNodeCoord(final int node)
    {
        final int r = node >> 8, c = node & 0xFF;
        return Data.NodeCoord.newBuilder().setRow(r).setColumn(c);
    }

    /**
     * Get the {@link SOCBoardLarge}-encoded coordinate of this protobuf {@link Data.EdgeCoord}.
     * @param ec  A protobuf {@code EdgeCoord}, or {@code null}
     * @return  The coordinate, encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC,
     *     or 0 if {@code ec == null}
     * @see #toEdgeCoord(int)
     */
    public static final int fromEdgeCoord(final Data.EdgeCoord ec)
    {
        return (ec == null) ? 0 : ((ec.getRow() << 8) | ec.getColumn());
    }

    /**
     * Get the {@link SOCBoardLarge}-encoded coordinate of this protobuf {@link Data.NodeCoord}.
     * @param nc  A protobuf {@code NodeCoord}, or {@code null}
     * @return  The coordinate, encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC,
     *     or 0 if {@code nc == null}
     * @see #toNodeCoord(int)
     */
    public static final int fromNodeCoord(final Data.NodeCoord nc)
    {
        return (nc == null) ? 0 : ((nc.getRow() << 8) | nc.getColumn());
    }

    /**
     * Get the {@link SOCBoardLarge}-encoded coordinate of this protobuf {@link Data.HexCoord}.
     * @param hc  A protobuf {@code HexCoord}, or {@code null}
     * @return  The coordinate, encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC,
     *     or 0 if {@code hc == null}
     * @see #toHexCoord(int)
     */
    public static final int fromHexCoord(final Data.HexCoord hc)
    {
        return (hc == null) ? 0 : ((hc.getRow() << 8) | hc.getColumn());
    }

    /**
     * Build a protobuf {@code Data.BoardCoord.Builder} from this piece coordinate and type.
     * @param coord  A piece coordinate encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC
     * @param pieceType  Piece type, such as {@link soc.game.SOCPlayingPiece#CITY},
     *     to choose which field to set in BoardCoord's {@code OneOf}:
     *     {@code edge_coord} for roads and ships, {@code node_coord} for all other types.
     * @return A {@code Data.BoardCoord.Builder} from {@code coord}
     * @see #toBoardCoord(int, soc.proto.Data.BoardCoord.CoordTypeCase)
     * @see #fromBoardCoord(soc.proto.Data.BoardCoord)
     */
    public static final Data.BoardCoord.Builder toBoardCoord
        (final int coord, final int pieceType)
        throws IllegalArgumentException
    {
        final Data.BoardCoord.CoordTypeCase coordType;
        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:
            // fall through

        case SOCPlayingPiece.SHIP:
            coordType = Data.BoardCoord.CoordTypeCase.EDGE_COORD;
            break;

        default:
            coordType = Data.BoardCoord.CoordTypeCase.NODE_COORD;
        }

        return toBoardCoord(coord, coordType);
    }

    /**
     * Build a protobuf {@code Data.BoardCoord.Builder} from this piece coordinate.
     * @param coord  A piece coordinate encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC
     * @param coordType  Type of coordinate, to choose which field to set in BoardCoord's {@code OneOf}:
     *     {@link Data.BoardCoord.CoordCase#EDGE_COORD},
     *     {@link Data.BoardCoord.CoordCase#HEX_COORD HEX_COORD},
     *     or {@link Data.BoardCoord.CoordCase#NODE_COORD NODE_COORD}.
     * @return A {@code Data.BoardCoord.Builder} from {@code coord}
     * @throws IllegalArgumentException if {@code coordType} is {@code null} or
     *     isn't one of the three allowed types
     * @see #toBoardCoord(int, int)
     * @see #fromBoardCoord(soc.proto.Data.BoardCoord)
     */
    public static final Data.BoardCoord.Builder toBoardCoord
        (final int coord, final Data.BoardCoord.CoordTypeCase coordType)
        throws IllegalArgumentException
    {
        if (coordType == null)
            throw new IllegalArgumentException("coordType: null");

        final Data.BoardCoord.Builder b = Data.BoardCoord.newBuilder();

        switch (coordType)
        {
        case EDGE_COORD:
            b.setEdgeCoord(toEdgeCoord(coord));  break;

        case HEX_COORD:
            b.setHexCoord(toHexCoord(coord));  break;

        case NODE_COORD:
            b.setNodeCoord(toNodeCoord(coord));  break;

        default:
            throw new IllegalArgumentException("coordType: " + coordType);
        }

        return b;
    }

    /**
     * Get the {@link SOCBoardLarge}-encoded coordinate of this protobuf {@link Data.BoardCoord}.
     * Its edge, node, or hex coordinate's row and column will be extracted from its
     * {@code OneOf} field and encoded together as an {@code int}.
     * @param pc  A protobuf {@code BoardCoord}; not {@code null}
     * @return  The piece coordinate, encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC,
     *     or 0 if the coordinate type is unknown
     * @throws NullPointerException if {@code pc == null} or its
     *     {@link Data.BoardCoord#getCoordTypeCase()} is {@code null}
     * @see #toBoardCoord(int, soc.proto.Data.BoardCoord.CoordTypeCase)
     * @see #toBoardCoord(int, int)
     */
    public static final int fromBoardCoord(final Data.BoardCoord pc)
        throws NullPointerException
    {
        int r = 0, c = 0;

        switch (pc.getCoordTypeCase())
        {
        case EDGE_COORD:
            {
                Data.EdgeCoord ec = pc.getEdgeCoord();
                if (ec != null)
                {
                    r = ec.getRow();
                    c = ec.getColumn();
                }
            }
            break;

        case NODE_COORD:
            {
                Data.NodeCoord nc = pc.getNodeCoord();
                if (nc != null)
                {
                    r = nc.getRow();
                    c = nc.getColumn();
                }
            }
            break;

        case HEX_COORD:
            {
                Data.HexCoord hc = pc.getHexCoord();
                if (hc != null)
                {
                    r = hc.getRow();
                    c = hc.getColumn();
                }
            }
            break;

        default:
            // unknown type; coord remains 0
        }

        return (r << 8) | c;
    }

    /**
     * Build a protobuf {@code Data._EdgeList.Builder} from these edge coordinates.
     * @param edges  A collection of edge coordinates encoded in the
     *     {@link SOCBoardLarge} coordinate system ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC.
     *     Not {@code null}.
     * @return  A protobuf edge list from {@code edges}
     * @throws NullPointerException if {@code edges == null}
     * @see #toEdgeCoord(int)
     * @see #fromEdgeList(soc.proto.Data._EdgeList)
     */
    public static final Data._EdgeList.Builder toEdgeList(final Collection<Integer> edges)
        throws NullPointerException
    {
        Data._EdgeList.Builder b = Data._EdgeList.newBuilder();

        for (final int ec : edges)
        {
            final int r = ec >> 8, c = ec & 0xFF;
            b.addEdge(Data.EdgeCoord.newBuilder().setRow(r).setColumn(c));
        }

        return b;
    }

    /**
     * Build a protobuf {@code Data._HexList.Builder} from these hex coordinates.
     * @param edges  A collection of hexes coordinates encoded in the
     *     {@link SOCBoardLarge} coordinate system ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC.
     *     Not {@code null}.
     * @return  A protobuf hex list from {@code hexes}
     * @throws NullPointerException if {@code hexes == null}
     * @see #toHexCoord(int)
     * @see #fromHexList(soc.proto.Data._HexList)
     */
    public static final Data._HexList.Builder toHexList(final Collection<Integer> hexes)
        throws NullPointerException
    {
        Data._HexList.Builder b = Data._HexList.newBuilder();

        for (final int hc : hexes)
        {
            final int r = hc >> 8, c = hc & 0xFF;
            b.addHex(Data.HexCoord.newBuilder().setRow(r).setColumn(c));
        }

        return b;
    }

    /**
     * Build a protobuf {@code Data._NodeList.Builder} from these node coordinates.
     * @param nodes  A collection of nodes coordinates encoded in the
     *     {@link SOCBoardLarge} coordinate system ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC.
     *     Not {@code null}.
     * @return  A protobuf node list from {@code nodes}
     * @throws NullPointerException if {@code nodes == null}
     * @see #toNodeCoord(int)
     * @see #fromNodeList(soc.proto.Data._NodeList)
     */
    public static final Data._NodeList.Builder toNodeList(final Collection<Integer> nodes)
        throws NullPointerException
    {
        Data._NodeList.Builder b = Data._NodeList.newBuilder();

        for (final int nc : nodes)
        {
            final int r = nc >> 8, c = nc & 0xFF;
            b.addNode(Data.NodeCoord.newBuilder().setRow(r).setColumn(c));
        }

        return b;
    }

    /**
     * Make a List of {@link SOCBoardLarge}-encoded coordinates from this protobuf list of edges.
     * @param edges  A protobuf edge list. Not {@code null}.
     * @return  The coordinate list, encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC
     * @throws NullPointerException if {@code edges == null}
     * @see #toEdgeList(Collection)
     * @see #fromEdgeCoord(soc.proto.Data.EdgeCoord)
     */
    public static final List<Integer> fromEdgeList(final Data._EdgeList edges)
        throws NullPointerException
    {
        ArrayList<Integer> elist = new ArrayList<Integer>(edges.getEdgeCount());

        for (final Data.EdgeCoord edge : edges.getEdgeList())
            elist.add(fromEdgeCoord(edge));

        return elist;
    }

    /**
     * Make a List of {@link SOCBoardLarge}-encoded coordinates from this protobuf list of hexes.
     * @param hexes  A protobuf hex list. Not {@code null}.
     * @return  The coordinate list, encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC
     * @throws NullPointerException if {@code hexes == null}
     * @see #toHexList(Collection)
     * @see #fromHexCoord(soc.proto.Data.HexCoord)
     */
    public static final List<Integer> fromHexList(final Data._HexList hexes)
        throws NullPointerException
    {
        ArrayList<Integer> hlist = new ArrayList<Integer>(hexes.getHexCount());

        for (final Data.HexCoord hex : hexes.getHexList())
            hlist.add(fromHexCoord(hex));

        return hlist;
    }

    /**
     * Make a List of {@link SOCBoardLarge}-encoded coordinates from this protobuf list of nodes.
     * @param hexes  A protobuf node list. Not {@code null}.
     * @return  The coordinate list, encoded in the {@link SOCBoardLarge} coordinate system
     *     ({@link SOCBoard#BOARD_ENCODING_LARGE}): 0xRRCC
     * @throws NullPointerException if {@code nodes == null}
     * @see #toNodeList(Collection)
     * @see #fromNodeCoord(soc.proto.Data.NodeCoord)
     */
    public static final List<Integer> fromNodeList(final Data._NodeList nodes)
        throws NullPointerException
    {
        ArrayList<Integer> nlist = new ArrayList<Integer>(nodes.getNodeCount());

        for (final Data.NodeCoord node : nodes.getNodeList())
            nlist.add(fromNodeCoord(node));

        return nlist;
    }

    //
    // Resource Sets
    //

    /**
     * Build a protobuf {@code Data.ResourceSet.Builder} from this {@link SOCResourceSet}'s known resources.
     * Unknown resources are ignored.
     * @param rs  Resource set to build from; not {@code null}
     * @return  A {@code Data.ResourceSet.Builder} from {@code rs}
     * @throws NullPointerException if {@code rs == null}
     * @see #fromResourceSet(soc.proto.Data.ResourceSet)
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
     * Build a {@link SOCResourceSet} from this protobuf {@code Data.ResourceSet.Builder}'s known resources.
     * Unknown resources are ignored.
     * @param rs  Protobuf ResourceSet to build from; not {@code null}
     * @return  A {@link SOCResourceSet} from {@code rs}
     * @throws NullPointerException if {@code rs == null}
     * @see #toResourceSet(SOCResourceSet)
     */
    public static final SOCResourceSet fromResourceSet(final Data.ResourceSet rs)
        throws NullPointerException
    {
        return new SOCResourceSet(rs.getClay(), rs.getOre(), rs.getSheep(), rs.getWheat(), rs.getWood(), 0);
    }

    //
    // Dev Cards
    //

    /**
     * Return the protobuf {@code Data.DevCardValue} enum value (object) for this development card constant.
     * @param card  Type of development card, like {@link SOCDevCardConstants#ROADS}
     *     or {@link SOCDevCardConstants#UNKNOWN}
     * @return  Protobuf enum value for {@code card}, like {@link Data.DevCardValue#ROAD_BUILDING}
     *     or {@link Data.DevCardValue#UNKNOWN_DEV_CARD}, or {@code null} if not recognized
     * @see #isDevCardVP(DevCardValue)
     * @see #fromDevCardValue(soc.proto.Data.DevCardValue)
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
     * Return the {@link SOCDevCardConstants} for this protobuf {@code Data.DevCardValue} enum value (object).
     * @param card Protobuf enum value for {@code card}, like {@link Data.DevCardValue#ROAD_BUILDING}
     *     or {@link Data.DevCardValue#UNKNOWN_DEV_CARD}, or {@code null}
     * @return Type of development card, like {@link SOCDevCardConstants#ROADS}
     *     or {@link SOCDevCardConstants#UNKNOWN} if {@code null} or unknown
     * @see #toDevCardValue(int)
     */
    public static final int fromDevCardValue(final Data.DevCardValue card)
    {
        if (card == null)
            return SOCDevCardConstants.UNKNOWN;

        final int ctype;

        switch (card)
        {
        case ROAD_BUILDING:
            ctype = SOCDevCardConstants.ROADS;  break;
        case YEAR_OF_PLENTY:
            ctype = SOCDevCardConstants.DISC;  break;
        case MONOPOLY:
            ctype = SOCDevCardConstants.MONO;  break;
        case VP_GREAT_HALL:
            ctype = SOCDevCardConstants.CAP;  break;
        case VP_MARKET:
            ctype = SOCDevCardConstants.MARKET;  break;
        case VP_UNIVERSITY:
            ctype = SOCDevCardConstants.UNIV;  break;
        case VP_LIBRARY:
            ctype = SOCDevCardConstants.TEMP;  break;
        case VP_CHAPEL:
            ctype = SOCDevCardConstants.CHAPEL;  break;
        case KNIGHT:
            ctype = SOCDevCardConstants.KNIGHT;  break;
        default:
            ctype = SOCDevCardConstants.UNKNOWN;
        }

        return ctype;
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

    //
    // Misc: PlayerElements, SeatLockState, ...
    //

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
