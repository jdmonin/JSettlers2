/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2017 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Map;

/**
 * A standard 4p board with 19 land hexes and 18 sea hexes layed out in a
 * concentric way. In v3.0.00 and newer all boards use the same v3 coordinate encoding
 * ({@link SOCBoard#BOARD_ENCODING_LARGE}).
 * @since 2.0.00
 */
public abstract class SOCBoard4p extends SOCBoard
{
    private static final long serialVersionUID = 3000L;  // last structural change v2.0.00

    /**
     * Land-hex coordinates in standard board ({@link #BOARD_ENCODING_ORIGINAL}).
     * @since 1.1.08
     */
    public final static int[] HEXCOORDS_LAND_V1 =
    {
        0x33, 0x35, 0x37, 0x53, 0x55, 0x57, 0x59, 0x73, 0x75, 0x77, 0x79, 0x7B,
        0x95, 0x97, 0x99, 0x9B, 0xB7, 0xB9, 0xBB
    };

    /**
     * largest coordinate value for an edge, in the v1 encoding.
     * Named <tt>MAXEDGE</tt> before v1.1.11 ; the name change is a
     * reminder that {@link SOCBoard6p#MAXEDGE_V2} represents a different encoding.
     * @since 1.1.11
     */
    protected static final int MAXEDGE_V1 = 0xCC;

    /**
     * smallest coordinate value for an edge, in the v1 encoding.
     * Named <tt>MINEDGE</tt> before v1.1.11 ; the name change is a
     * reminder that {@link SOCBoard6p#MINEDGE_V2} has a different value.
     * @since 1.1.11
     */
    protected static final int MINEDGE_V1 = 0x22;

    /**
     * smallest coordinate value for a node on land, in the v1 encoding.
     * Named <tt>MINNODE</tt> before v1.1.11 ; the name change is a
     * reminder that {@link SOCBoard6p#MINNODE_V2} has a different value.
     * @since 1.1.11
     */
    protected static final int MINNODE_V1 = 0x23;

    /**
     * Each port's hex number within {@link #hexLayout} on standard board.
     * Same order as {@link #PORTS_FACING_V1}:
     * Clockwise from upper-left (hex coordinate 0x17).
     * @since 1.1.08
     */
    final static int[] PORTS_HEXNUM_V1 = { 0, 2, 8, 21, 32, 35, 33, 22, 9 };

    /**
     * Each port's <em>facing</em> towards land, on the standard board.
     * Ordered clockwise from upper-left (hex coordinate 0x17).
     * Port Facing is the direction from the port hex/edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     * Facing 2 is east ({@link #FACING_E}), 3 is SE, 4 is SW, etc; see {@link #hexLayout}.
     * @since 1.1.08
     */
    final static int[] PORTS_FACING_V1 =
    {
        FACING_SE, FACING_SW, FACING_SW, FACING_W, FACING_NW, FACING_NW, FACING_NE, FACING_E, FACING_E
    };

    /**
     * Each port's 2 node coordinates on standard board.
     * Same order as {@link #PORTS_FACING_V1}:
     * Clockwise from upper-left (hex coordinate 0x17).
     * @since 1.1.08
     */
    final static int[] PORTS_EDGE_V1 =
    {
        0x27,  // Port touches the upper-left land hex, port facing land to its SouthEast
        0x5A,  // Touches middle land hex of top row, port facing SW
        0x9C,  // Touches rightmost land hex of row above middle, SW
        0xCC,  // Rightmost of middle-row land hex, W
        0xC9,  // Rightmost land hex below middle, NW
        0xA5,  // Port touches middle hex of bottom row, facing NW
        0x72,  // Leftmost of bottom row, NE
        0x42,  // Leftmost land hex of row below middle, E
        0x24   // Leftmost land hex above middle, facing E
    };

    /**
     * unused default constructor: everything is SOCBoardLarge in v3
     */
    private SOCBoard4p()
    {
        super(null, 4, BOARD_ENCODING_ORIGINAL);

        minEdge = MINEDGE_V1;
        maxEdge = MAXEDGE_V1;
        minNode = MINNODE_V1;
    }

    @Override
    public int[] getPortsFacing()
    {
        return SOCBoard4p.PORTS_FACING_V1;
    }

    @Override
    public int[] getPortsEdges()
    {
        return SOCBoard4p.PORTS_EDGE_V1;
    }

    @Override
    public int[] getLandHexCoords()
    {
        return SOCBoard4p.HEXCOORDS_LAND_V1;
    }

    @Override
    public int getPortsCount()
    {
        return SOCBoard4p.PORTS_FACING_V1.length;
    }

}
