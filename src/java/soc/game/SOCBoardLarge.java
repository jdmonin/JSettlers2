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

/**
 * A representation of a larger (up to 127 x 127) JSettlers board,
 * with an arbitrary mix of land and water tiles.
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.2.00
 */
public class SOCBoardLarge extends SOCBoard
{
    private static final int BOARDWIDTH_LARGE = 22, BOARDHEIGHT_LARGE = 15;

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
        hexLayoutLg = new int[BOARDHEIGHT_LARGE][BOARDWIDTH_LARGE];
        numberLayoutLg = new int[BOARDHEIGHT_LARGE][BOARDWIDTH_LARGE];
    }

    // TODO override makeNewBoard; set hexLayoutLg, numberLayoutLg, portsLayout, robberHex,
    //  fill ports, pieces, roads, nodesOnBoard

    // TODO should portsFacing be separate like v2 portsLayout, instead of part of hexLayoutLg?

    // TODO override anything related to the unused super fields:
    //  hexLayout, numberLayout, minNode, minEdge, maxEdge,
    //  numToHexID, hexIDtoNum, nodeIDtoPortType,
    //  HEXNODES, NODE_2_AWAY :
    //  getPortFacing(), getNumberOnHexFromCoord(), getHexTypeFromCoord(),
    //  getAdjacentNodesToEdge(), getAdjacentNodesToEdge_arr(), getAdjacentEdgesToEdge(),
    //  getAdjacentHexesToNode(), getAdjacentEdgesToNode(), getAdjacentEdgesToNode_arr(),
    //  getAdjacentEdgeToNode(), getEdgeBetweenAdjacentNodes(), isEdgeAdjacentToNode(),
    //  getAdjacentNodesToNode(), getAdjacentNodesToNode_arr(), getAdjacentNodeToNode(),
    //  getAdjacentNodeToNode2Away(), isNode2AwayFromNode(), getAdjacentEdgeToNode2Away(),
    //  getAdjacentHexesToHex(), getAdjacentNodeToHex(), getAdjacentHexToEdge(),
    //  edgeCoordToString().
    //
    // Not valid for this layout:
    //   getHexLandCoords(), getPortTypeFromNodeCoord(), getNumberOnHexFromNumber(),
    //   getHexNumFromCoord(), getHexTypeFromNumber(), getMinNode(), getMinEdge(), getMaxEdge()
    //
    // Not valid if arrays are 2D:
    //   getHexLayout(), getNumberLayout(), setHexLayout(), setPortsLayout(), setNumberLayout()
    //
    // Maybe not valid:
    //   getPortsFacing(), getPortsEdges(), getPortTypeFromHexType()

    // TODO override initPlayerLegalRoads, initPlayerLegalAndPotentialSettlements;
    //  their return format might not scale to this size

}
