/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2011 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Hashtable;
import java.util.Vector;

/**
 * A representation of a larger (up to 127 x 127 hexes) JSettlers board,
 * with an arbitrary mix of land and water tiles.
 * Implements {@link SOCBoard#BOARD_ENCODING_LARGE}.
 *<P>
 * Server and client must be 1.2.00 or newer ({@link #VERSION_FOR_ENCODING_LARGE}).
 *<P>
 * Unlike earlier encodings, here the "hex number" ("ID") is not an index into a dense array
 * of land hexes.  Thus it's not efficient to iterate through all hex numbers. <br>
 * Instead: Hex ID = (r << 8) | c   // 2 bytes: 0xRRCC
 *<P>
 * <b>Coordinate system</b> is a square grid of rows and columns, different from previous encodings:
 *<P>
 * <b>Hexes</b> (represented as coordinate of their centers),
 * <b>nodes</b> (corners of hexes; where settlements/cities are placed),
 * and <b>edges</b> (between nodes; where roads are placed),
 * share the same grid of coordinates.
 * Each hex is 2 units wide and 2 tall, with vertical sides (west,edge edges)
 * and sloped tops & bottoms (NW, NE, SW, SE edges).
 *<P>
 * See <tt>src/docs/TODO.gif</tt><br>
 * Coordinates start at the upper-left and continue to the right and down.
 * The first two rows of hexes are: <pre>
 *    (1,2)  (1,4)  (1,6) ..
 * (3,1) (3,3)  (3,5)  (3,7) .. </pre>
 * All water and land hexes are within the coordinates.
 * Rows increase going north to south, Columns increase west to east.
 *<BR>
 * TODO ports/nodes
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.2.00
 */
public class SOCBoardLarge extends SOCBoard
{
    /** This board encoding was introduced in version 1.2.00 (1200) */
    public static final int VERSION_FOR_ENCODING_LARGE = 1200;

    private static final int BOARDHEIGHT_LARGE = 15, BOARDWIDTH_LARGE = 22;  // hardcode size for now

    // TODO hexLayoutLg, numberLayoutLg: Will only need half the rows, half the columns

    /**
     * Hex layout: water/land resource types, and port types and facings.
     * One element per hex.
     * Order: [row][column].
     * <P>
     * Each element has the same format as SOCBoard.hexLayout:
     * Each element's value encodes hex type and, if a
     * port, its facing ({@link #FACING_NE} to {@link #FACING_NW}).
     *<P>
     * For land hexes, the dice number on <tt>hexLayoutLg</tt>[r][c] is {@link #numberLayoutLg}[r][c].
     *<P>
     * Key to the hexLayoutLg[][] values:
       <pre>
       0 : desert  {@link #DESERT_HEX}
       1 : clay    {@link #CLAY_HEX}
       2 : ore     {@link #ORE_HEX}
       3 : sheep   {@link #SHEEP_HEX}
       4 : wheat   {@link #WHEAT_HEX}
       5 : wood    {@link #WOOD_HEX} also: {@link #MAX_LAND_HEX} {@link #MAX_ROBBER_HEX}
       6 : water   {@link #WATER_HEX}
       7 : misc port ("3:1") facing land in direction 1 ({@link #FACING_NE NorthEast})
                             (this port type is {@link #MISC_PORT} in {@link #getPortTypeFromNodeCoord(int)})
       8 : misc port facing 2 ({@link #FACING_E})
       9 : misc port facing 3 ({@link #FACING_SE})
       10 : misc port facing 4 ({@link #FACING_SW})
       11 : misc port facing 5 ({@link #FACING_W})
       12 : misc port facing 6 ({@link #FACING_NW NorthWest})
       16+: non-misc ("2:1") encoded port
       </pre>
        Non-misc ports are encoded here in binary like this:<pre>
      (port facing, 1-6)        (kind of port)
              \--> [0 0 0][0 0 0 0] <--/       </pre>
        Kind of port:<pre>
        1 : clay  (port type {@link #CLAY_PORT} in {@link #getPortTypeFromNodeCoord(int)})
        2 : ore    {@link #ORE_PORT}
        3 : sheep  {@link #SHEEP_PORT}
        4 : wheat  {@link #WHEAT_PORT}
        5 : wood   {@link #WOOD_PORT}
        </pre>
        <em>Port facing</em> is the edge of the port's hex
        touching land, which contains 2 nodes where player can build a
        port settlement/city; facing is a number 1-6
        ({@link #FACING_NE}-{@link #FACING_NW}). <pre>
      6 &lt;--.    .--> 1
            \/\/
            /  \
       5&lt;--|    |--> 2
           |    |
            \  /
            /\/\
      4 &lt;--.    .--> 3  </pre>
     *<P>
     */
    private int[][] hexLayoutLg;

    /**
     * Dice number from hex coordinate.
     * One element per water, land, or port hex; non-land hexes are 0.
     * Order: [row][column].
     */
    private int[][] numberLayoutLg;

    public SOCBoardLarge(Hashtable gameOpts, int maxPlayers)
            throws IllegalArgumentException
    {
        super(BOARD_ENCODING_LARGE);
        setBoardBounds(BOARDWIDTH_LARGE, BOARDHEIGHT_LARGE);
        hexLayoutLg = new int[BOARDHEIGHT_LARGE][BOARDWIDTH_LARGE];  // TODO initial set to WATER_HEX
        numberLayoutLg = new int[BOARDHEIGHT_LARGE][BOARDWIDTH_LARGE];  // TODO initial set to 0
    }

    // TODO override makeNewBoard; set hexLayoutLg, numberLayoutLg, portsLayout, robberHex,
    //  fill ports, pieces, roads, nodesOnBoard

    // TODO hexLayoutLg, numberLayoutLg will only ever use the odd row numbers
    // TODO portsFacing, port layout should be separate like v2 portsLayout, instead of part of hexLayoutLg

    // TODO override anything related to the unused super fields:
    //  hexLayout, numberLayout, minNode, minEdge, maxEdge,
    //  numToHexID, hexIDtoNum, nodeIDtoPortType,
    //  HEXNODES, NODE_2_AWAY :
    //  getPortFacing(),
    //  getAdjacentNodesToEdge(), getAdjacentNodesToEdge_arr(), getAdjacentEdgesToEdge(),
    //  getAdjacentHexesToNode(), getAdjacentEdgesToNode(), getAdjacentEdgesToNode_arr(),
    //  getAdjacentEdgeToNode(), getEdgeBetweenAdjacentNodes(), isEdgeAdjacentToNode(),
    //  getAdjacentNodeToNode2Away(), isNode2AwayFromNode(), getAdjacentEdgeToNode2Away(),
    //  getAdjacentNodeToHex(), getAdjacentHexToEdge(), edgeCoordToString()
    // DONE:
    //  getNumberOnHexFromCoord(), getHexTypeFromCoord()
    //     incl not-valid getNumberOnHexFromNumber, getHexTypeFromNumber [using num==coord]
    //  getAdjacentNodeToNode(), getAdjacentHexesToHex()
    //
    // Not valid for this layout: TODO look for callers:
    //   getHexLandCoords(), getPortTypeFromNodeCoord(), getNumberOnHexFromNumber(),
    //   getHexNumFromCoord(), getHexTypeFromNumber(), getMinNode(), getMinEdge(), getMaxEdge()
    //
    // Not valid if arrays are 2D:
    //   getHexLayout(), getNumberLayout(), setHexLayout(), setPortsLayout(), setNumberLayout()
    //   Consider get/set layouts as a 1D array: r, c, contents. Only include odd-numbered rows.
    //
    // Maybe not valid:
    //   getPortsFacing(), getPortsEdges(), getPortTypeFromHexType()

    // TODO override initPlayerLegalRoads, initPlayerLegalAndPotentialSettlements;
    //  their return format might not scale to this size

    /**
     * Given a hex coordinate, return the dice-roll number on that hex
     *
     * @param hex  the coordinates for a hex
     *
     * @return the dice-roll number on that hex, or 0 if not a hex coordinate
     */
    public int getNumberOnHexFromCoord(final int hex)
    {
        return getNumberOnHexFromNumber(hex);
    }

    /**
     * Given a hex number, return the (dice-roll) number on that hex
     *
     * @param hex  the number of a hex, or -1 if invalid
     *
     * @return the dice-roll number on that hex, or 0
     */
    public int getNumberOnHexFromNumber(final int hex)
    {
        final int r = hex >> 8,
            c = hex & 0xFF;
        if ((r < 0) || (c < 0) || (r >= boardHeight) || (c >= boardWidth))
            return 0;

        int num = numberLayoutLg[r][c];
        if (num < 0)
            return 0;
        else
            return num;
    }

    /**
     * Given a hex coordinate, return the type of hex
     *
     * @param hex  the coordinates ("ID") for a hex
     * @return the type of hex:
     *         Land in range {@link #CLAY_HEX} to {@link #WOOD_HEX},
     *         or {@link #DESERT_HEX},
     *         or {@link #MISC_PORT_HEX} or another port type ({@link #CLAY_PORT_HEX}, etc),
     *         or {@link #WATER_HEX}.
     *
     * @see #getPortTypeFromHexType(int)
     * @see #getHexNumFromCoord(int)
     * @see #getHexLandCoords()
     */
    public int getHexTypeFromCoord(final int hex)
    {
        return getHexTypeFromNumber(hex);
    }

    /**
     * Given a hex number, return the type of hex
     *
     * @param hex  the number of a hex, or -1 for invalid
     * @return the type of hex:
     *         Land in range {@link #CLAY_HEX} to {@link #WOOD_HEX},
     *         {@link #DESERT_HEX}, or {@link #WATER_HEX},
     *         or {@link #MISC_PORT_HEX} or another port type ({@link #CLAY_PORT_HEX}, etc).
     *         Invalid hex numbers return -1.
     *
     * @see #getPortTypeFromHexType(int)
     * @see #getHexTypeFromCoord(int)
     */
    public int getHexTypeFromNumber(final int hex)
    {
        final int r = hex >> 8,
            c = hex & 0xFF;
        if ((r < 0) || (c < 0) || (r >= boardHeight) || (c >= boardWidth))
            return -1;
        final int hexType = hexLayoutLg[r][c];

        if (hexType < 7)
        {
            return hexType;
        }
        else if ((hexType >= 7) && (hexType <= 12))
        {
            return MISC_PORT_HEX;
        }
        else
        {
            switch (hexType & 7)
            {
            case 1:
                return CLAY_PORT_HEX;

            case 2:
                return ORE_PORT_HEX;

            case 3:
                return SHEEP_PORT_HEX;

            case 4:
                return WHEAT_PORT_HEX;

            case 5:
                return WOOD_PORT_HEX;
            }
        }

        return -1;
    }

    /**
     * Given a node, get the valid adjacent node in a given direction, if any.
     * At the edge of the layout, some adjacent nodes/edges may be
     * "off the board" and thus invalid.
     *
     * @param nodeCoord  Node coordinate to go from; not checked for validity.
     * @param nodeDir  0 for northwest or southwest; 1 for northeast or southeast;
     *     2 for north or south
     * @return  The adjacent node in that direction, or -9 if none (if off the board)
     * @throws IllegalArgumentException if <tt>nodeDir</tt> is less than 0 or greater than 2
     * @see #getAdjacentNodesToNode(int)
     * @see #getAdjacentNodeToNode2Away(int, int)
     * @see #getAdjacentEdgeToNode(int, int)
     */
    public int getAdjacentNodeToNode(final int nodeCoord, final int nodeDir)
        throws IllegalArgumentException
    {
        int r = (nodeCoord >> 8),
            c = (nodeCoord & 0xFF);

        // Both 'Y' nodes and 'A' nodes have adjacent nodes
        // towards east at (0,+1) and west at (0,-1).
        // Offset to third adjacent node varies.

        switch (nodeDir)
        {
        case 0:  // NW or SW
            --c;
            break;

        case 1:  // NE or SE
            ++c;
            break;

        case 2:  // N or S
            /**
             * if the (row/2, col) coords are (even, odd) or (odd,even),
             * then the node is 'Y'.
             */
            final int s = r / 2;
            if ((s % 2) != (c % 2))
                // Node is 'Y': Next node is south.
                r += 2;
            else
                // Node is 'upside down Y' ('A'):
                // Next node is north.
                r -= 2;
            break;

        default:
            throw new IllegalArgumentException("nodeDir out of range: " + nodeDir);
        }

        if ((r < 0) || (c < 0) || (r >= boardHeight) || (c >= boardWidth))
            return -9;
        else
            return ((r << 8) | c);
    }

    /**
     * Make a list of all valid hex coordinates (or, only land) adjacent to this hex.
     * Valid coordinates are those within the board coordinate limits
     * ({@link #getBoardHeight()}, {@link #getBoardWidth()}).
     *<P>
     * Coordinate offsets - adjacent hexes to hex:<PRE>
     *   (-2,-1) (-2,+1)
     *
     * (0,-2)   x   (0,+2)
     *
     *   (+2,-1) (+2,+1)  </PRE>
     *
     * @param hexCoord  Coordinate ("ID") of this hex
     * @param includeWater  Should water hexes be returned (not only land ones)?
     * @return the hexes that touch this hex, as a Vector of Integer coordinates,
     *         or null if none are adjacent (will <b>not</b> return a 0-length vector)
     */
    public Vector getAdjacentHexesToHex(final int hexCoord, final boolean includeWater)
    {
        Vector hexes = new Vector();

        final int r = hexCoord >> 8,
            c = hexCoord & 0xFF;

        getAdjacentHexes_AddIfOK_Lg(hexes, includeWater, r - 2, c - 1);  // NW (northwest)
        getAdjacentHexes_AddIfOK_Lg(hexes, includeWater, r - 2, c + 1);  // NE
        getAdjacentHexes_AddIfOK_Lg(hexes, includeWater, r,     c - 2);  // W
        getAdjacentHexes_AddIfOK_Lg(hexes, includeWater, r,     c + 2);  // E
        getAdjacentHexes_AddIfOK_Lg(hexes, includeWater, r + 2, c - 1);  // SW
        getAdjacentHexes_AddIfOK_Lg(hexes, includeWater, r + 2, c + 1);  // SE

        if (hexes.size() > 0)
            return hexes;
        else
            return null;
    }

    /**
     * Check one possible coordinate for getAdjacentHexesToHex.
     * @param addTo the list we're building of hexes that touch this hex, as a Vector of Integer coordinates.
     * @param includeWater Should water hexes be returned (not only land ones)?
     * @param hexCoord Coordinate ("ID") of this hex
     * @param r  Row
     * @param c  Column
     */
    private final void getAdjacentHexes_AddIfOK_Lg
        (Vector addTo, final boolean includeWater, final int r, final int c)
    {
        if ((r < 0) || (c < 0) || (r >= boardHeight) || (c >= boardWidth))
            return;  // <--- Off the board in one coordinate ----
        if ((r % 2) == 0)
            return;  // not a valid hex row
        if (includeWater
            || (hexLayoutLg[r][c] <= MAX_LAND_HEX))
        {
            addTo.addElement(new Integer((r << 8) | c));
        }
    }

}
