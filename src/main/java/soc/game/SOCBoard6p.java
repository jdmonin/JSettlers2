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
 * The standard (non-sea) board layout for the 6-player extension.
 * @since 2.0.00
 */
public class SOCBoard6p extends SOCBoard
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

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
     * Land hex types on the 6-player board layout (v2).
     * For more information see {@link #makeNewBoard_placeHexes(int[], int[], int[], SOCGameOption)}.
     * @since 2.0.00
     */
    public static final int[] makeNewBoard_landHexTypes_v2 =
        {
            DESERT_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
            SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
            WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
            WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX,   // last line of v1's hexes
            DESERT_HEX, CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, SHEEP_HEX, SHEEP_HEX,
            WHEAT_HEX, WHEAT_HEX, WOOD_HEX, WOOD_HEX
        };

    /**
     * Dice numbers in the 6-player board layout, in order along {@code numPath}
     * ({@link #makeNewBoard_numPaths_v2}).
     * For more information see {@link #makeNewBoard_placeHexes(int[], int[], int[], SOCGameOption)}.
     * @since 2.0.00
     */
    public static final int[] makeNewBoard_diceNums_v2 =
        {
            2,   5,  4,  6 , 3, // A-E
            9,   8, 11, 11, 10, // F-J
            6,   3,  8,  4,  8, // K-O
            10, 11, 12, 10,  5, // P-T
            4,   9,  5,  9, 12, // U-Y
            3,   2,  6          // Za-Zc
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
     * Each port's type, such as {@link #SHEEP_PORT}, on 6-player board.
     * Same order as {@link #PORTS_FACING_V2}. {@link #MISC_PORT} is 0.
     * @since 1.1.08
     */
    public final static int PORTS_TYPE_V2[] =
        { 0, 0, 0, 0, CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT, MISC_PORT, SHEEP_PORT };

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
     * Possible number paths for 6-player board.
     * {@link #makeNewBoard(Map)} randomly chooses one path (one 1-dimensional array)
     * to be used as <tt>numPath[]</tt> in
     * {@link #makeNewBoard_placeHexes(int[], int[], int[], SOCGameOption)}.
     */
    final static int[][] makeNewBoard_numPaths_v2 =
    {
        // Numbers are indexes within hexLayout (also in numberLayout) for each land hex.
        // See the hexLayout javadoc for how the indexes are arranged on the board layout,
        // and remember that the 6-player board is visually rotated clockwise 90 degrees;
        // visual "North" used here is West internally in the board layout.

        // clockwise from north
        {
            15, 9, 4, 0, 1, 2, 7, 13, 20, 26, 31, 35, 34, 33, 28, 22,  // outermost hexes
            16, 10, 5, 6, 12, 19, 25, 30, 29, 23,  // middle ring of hexes
            17, 11, 18, 24                         // center hexes
        },

        // counterclockwise from north
        {
            15, 22, 28, 33, 34, 35, 31, 26, 20, 13, 7, 2, 1, 0, 4, 9,
            16, 23, 29, 30, 25, 19, 12, 6, 5, 10,
            17, 24, 18, 11
        },

        // clockwise from south
        {
            20, 26, 31, 35, 34, 33, 28, 22, 15, 9, 4, 0, 1, 2, 7, 13,
            19, 25, 30, 29, 23, 16, 10, 5, 6, 12,
            18, 24, 17, 11
        },

        // counterclockwise from south
        {
            20, 13, 7, 2, 1, 0, 4, 9, 15, 22, 28, 33, 34, 35, 31, 26,
            19, 12, 6, 5, 10, 16, 23, 29, 30, 25,
            18, 11, 17, 24
        }
    };

    public SOCBoard6p(final Map<String, SOCGameOption> gameOpts)
        throws IllegalArgumentException
    {
        super(gameOpts, 6, BOARD_ENCODING_6PLAYER);

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
