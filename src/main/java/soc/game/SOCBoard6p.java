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
 * The standard (non-sea) board layout for the 6-player extension,
 * rotated 90 degrees clockwise at the client.
 * In v3.0.00 and newer all boards use the same v3 coordinate encoding
 * ({@link SOCBoard#BOARD_ENCODING_LARGE}).
 * @since 2.0.00
 */
public abstract class SOCBoard6p extends SOCBoard
{
    private static final long serialVersionUID = 3000L;  // last structural change v2.0.00

    /**
     * Land-hex coordinates in 6-player board ({@link #BOARD_ENCODING_6PLAYER}).
     * @since 1.1.08.
     */
    public final static int[] HEXCOORDS_LAND_V2 =
    {
        0x11, 0x13, 0x15, 0x17,      // First diagonal row (moving NE from 0x11)
        0x31, 0x33, 0x35, 0x37, 0x39,
        0x51, 0x53, 0x55, 0x57, 0x59, 0x5B,
        0x71, 0x73, 0x75, 0x77, 0x79, 0x7B,
        0x93, 0x95, 0x97, 0x99, 0x9B,
        0xB5, 0xB7, 0xB9, 0xBB       // Last diagonal row (NE from 0xB5)
    };

    /**
     * largest coordinate value for an edge, in the v2 encoding
     * @since 1.1.08
     */
    protected static final int MAXEDGE_V2 = 0xCC;

    /**
     * smallest coordinate value for an edge, in the v2 encoding
     * @since 1.1.08
     */
    protected static final int MINEDGE_V2 = 0x00;

    /**
     * smallest coordinate value for a node on land, in the v2 encoding
     * @since 1.1.08
     */
    protected static final int MINNODE_V2 = 0x01;

    /**
     * Each port's <em>facing,</em> on 6-player board.
     * Ordered clockwise from upper-left (hex coordinate 0x17, which is land in the v2 layout).
     * Port Facing is the direction from the port hex/edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     * Within the board orientation (not the rotated visual orientation),
     * facing 2 is east ({@link #FACING_E}), 3 is SE, 4 is SW, etc.
     * @since 1.1.08
     */
    final static int[] PORTS_FACING_V2 =
    {
        FACING_SE, FACING_SW, FACING_SW, FACING_W, FACING_W, FACING_NW,
        FACING_NW, FACING_NE, FACING_NE, FACING_E, FACING_SE
    };

    /**
     * Each port's edge coordinate on the 6-player board.
     * This is the edge whose 2 end nodes can be used to build port settlements/cities.
     * Same order as {@link #PORTS_FACING_V2}:
     * Clockwise from upper-left (hex coordinate 0x17, which is land in the v2 layout).
     * @since 1.1.08
     */
    final static int[] PORTS_EDGE_V2 =
    {
        0x07,  // Port touches the upper-left land hex, port facing land to its SouthEast
        0x3A,  // Touches middle land hex of top row, port facing SW
        0x7C,  // Touches rightmost land hex of row below top, SW
        0xAC,  // Touches rightmost land hex of row above middle, W
        0xCA,  // Touches rightmost land hex of row below middle, W
        0xC7,  // Touches rightmost land hex of row above bottom, NW
        0xA3,  // Touches middle land hex of bottom row, NW
        0x70,  // Touches bottom-left land hex, NE
        0x30,  // Touches leftmost land hex of row below middle, NE
        0x00,  // Leftmost hex of middle row, E
        0x03   // Touches leftmost land hex of row above middle, SE
    };

    /**
     * unused default constructor: everything is {@link SOCBoardLarge} in v3
     */
    private SOCBoard6p()
        throws IllegalArgumentException
    {
        super(null, 6, BOARD_ENCODING_6PLAYER);

        minEdge = MINEDGE_V2;
        maxEdge = MAXEDGE_V2;
        minNode = MINNODE_V2;
    }

    @Override
    public int[] getPortsFacing()
    {
        return SOCBoard6p.PORTS_FACING_V2;
    }

    @Override
    public int[] getPortsEdges()
    {
        return SOCBoard6p.PORTS_EDGE_V2;
    }

    @Override
    public int[] getLandHexCoords()
    {
        return SOCBoard6p.HEXCOORDS_LAND_V2;
    }

    @Override
    public int getPortsCount()
    {
        return SOCBoard6p.PORTS_FACING_V2.length;
    }
}
