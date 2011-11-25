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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

/**
 * A representation of a larger (up to 127 x 127 hexes) JSettlers board,
 * with an arbitrary mix of land and water tiles.
 * Implements {@link SOCBoard#BOARD_ENCODING_LARGE}.
 * Activated with {@link SOCGameOption} <tt>"PLL"</tt>.
 *<P>
 * Server and client must be 1.2.00 or newer ({@link #VERSION_FOR_ENCODING_LARGE}).
 * The board layout is sent using {@link #getLandHexLayout()} and {@link #getPortsLayout()},
 * followed by the robber hex and (separately) the legal settlement/city nodes.
 *<P>
 * Unlike earlier encodings, here the "hex number" ("ID") is not an index into a dense array
 * of land hexes.  Thus it's not efficient to iterate through all hex numbers. <br>
 * Instead: Hex ID = (r &lt;&lt; 8) | c   // 2 bytes: 0xRRCC
 *<P>
 * <b>Coordinate system</b> is a square grid of rows and columns, different from previous encodings:
 *<P>
 * <b>Hexes</b> (represented as coordinate of their centers),
 * <b>nodes</b> (corners of hexes; where settlements/cities are placed),
 * and <b>edges</b> (between nodes; where roads are placed),
 * share the same grid of coordinates.
 * Each hex is 2 units wide and 2 tall, with vertical sides (west,edge edges)
 * and sloped tops and bottoms (NW, NE, SW, SE edges).
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

    private static final int BOARDHEIGHT_LARGE = 16, BOARDWIDTH_LARGE = 22;  // hardcode size for now

    /**
     * For {@link #getAdjacentNodeToHex(int, int)}, the offset to add to the hex
     * coordinate to get all adjacent node coords, starting at
     * index 0 at the top (northern corner of hex) and going clockwise.
     * Because we're looking at nodes and not edges (corners, not sides, of the hex),
     * these are offset from the set of "facing" directions by 30 degrees.
     *<P>
     * For each direction, array of adds to the coordinate to change the row & column.
     * The row delta in hex is +-0xRR00, the column is small (+-1) so doesn't need hex format.
     */
    private final static int[][] A_NODE2HEX = {
        { -0x100, 0 }, { -0x100, +1 }, { +0x100, +1 },  // N, NE, SE
        { +0x100, 0 }, { +0x100, -1 }, { -0x100, -1 }   // S, SW, NW
    };

    /**
     * Used by {@link #getAdjacentEdgesToEdge(int)}.
     * for each of the 3 edge directions, the (r,c) offsets for the 4 adjacent edges.
     *<P>
     * Decimal, not hex, because we need bounds-checking.
     *<br>
     * Order of directions: | / \
     */
    private final static int[][] A_EDGE2EDGE = {
        { -1,-1,  -1,0,  +1,-1,  +1,0 },  // "|"
        { 0,-1,   +1,0,  -1,+1,  0,+1 },  // "/"
        { 0,-1,   -1,0,  +1,+1,  0,+1 }   // "\"
    };

    /**
     * (r,c) Offsets from a node to another node 2 away,
     * Indexed by the facing directions: {@link #FACING_NE} is 1,
     * {@link #FACING_E} is 2, etc; {@link #FACING_NW} is 6.
     * Used by {@link #getAdjacentNodeToNode2Away(int, int)}. 
     * The array contains 2 elements per facing.
     */
    private final static int[] NODE_TO_NODE_2_AWAY = {
        -9,-9,          // not valid
        -2,+1,   0,+2,  // NE, E
        +2,+1,  +2,-1,  // SE, SW
         0,-2,  -2,-1   // W, NW
    };

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
     * For the set of all land hex coordinates, see {@link #landHex}.
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
     * The set of land hex coordinates within {@link #hexLayoutLg},
     * as returned by {@link #getLandHexCoords()}, or <tt>null</tt>.
     * That method fills it from {@link #landHexLayout}.  If the board
     * layout changes, this field again becomes <tt>null</tt> until the
     * next call to {@link #getLandHexCoords()}.
     */
    private int[] cachedGetlandHexCoords;

    /**
     * The set of land hex coordinates within {@link #hexLayoutLg}.
     * Sent from server to client, along with the land hex types / dice numbers,
     * via {@link #getLandHexLayout()} / {@link #setLandHexLayout(int[])}.
     */
    private HashSet landHexLayout;

    /**
     * The legal set of land edge coordinates to build roads,
     * based on {@link #nodesOnLand}.
     * Calculated in {@link makeNewBoard_makeLegalRoadsFromLandNodes()},
     * after {@link #nodesOnLand} is filled by
     * {@link makeNewBoard_fillNodesOnLandFromHexes(int[])}.
     */
    private HashSet legalRoadEdges;

    /**
     * Dice number from hex coordinate.
     * One element per water, land, or port hex; non-land hexes are 0.
     * Order: [row][column].
     */
    private int[][] numberLayoutLg;

    /**
     * This board layout's number of ports;
     * 0 if {@link #makeNewBoard(Hashtable)} hasn't been called yet.
     * Port types, edges and facings are all stored in {@link SOCBoard#portsLayout}.
     */
    private int portsCount;

    /**
     * Create a new Settlers of Catan Board, with the v3 encoding.
     * @param gameOpts  if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     * @param maxPlayers Maximum players; must be 4 or 6
     * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
     */
    public SOCBoardLarge(Hashtable gameOpts, int maxPlayers)
            throws IllegalArgumentException
    {
        super(BOARD_ENCODING_LARGE);
        if ((maxPlayers != 4) && (maxPlayers != 6))
            throw new IllegalArgumentException("maxPlayers: " + maxPlayers);
        // TODO maxPlayers 6 not yet supported in our board layout for "PLL"

        setBoardBounds(BOARDWIDTH_LARGE, BOARDHEIGHT_LARGE);

        hexLayoutLg = new int[BOARDHEIGHT_LARGE+1][BOARDWIDTH_LARGE+1];
        numberLayoutLg = new int[BOARDHEIGHT_LARGE+1][BOARDWIDTH_LARGE+1];
        landHexLayout = new HashSet();
        legalRoadEdges = new HashSet();

        // Only odd-numbered rows are valid,
        // but we fill all rows here just in case.
        for (int r = 0; r <= boardHeight; ++r)
        {            
            Arrays.fill(hexLayoutLg[r], WATER_HEX);
            Arrays.fill(numberLayoutLg[r], 0);
        }

        portsCount = 0;
    }

    // TODO hexLayoutLg, numberLayoutLg will only ever use the odd row numbers

    // TODO override anything related to the unused super fields:
    //  hexLayout, numberLayout, minNode, minEdge, maxEdge,
    //  numToHexID, hexIDtoNum, nodeIDtoPortType :
    //  nodeCoordToString(), edgeCoordToString()
    // DONE:
    //  getNumberOnHexFromCoord(), getHexTypeFromCoord()
    //     TODO incl not-valid getNumberOnHexFromNumber, getHexTypeFromNumber [using num==coord]
    //
    // Not valid for this layout: TODO look for callers:
    //   getHexLandCoords(), getPortTypeFromNodeCoord(), getNumberOnHexFromNumber(),
    //   getHexNumFromCoord(), getHexTypeFromNumber(), getMinNode(), getMinEdge(), getMaxEdge()
    //
    // Not valid if arrays are 2D:
    //   getHexLayout(), getNumberLayout(), setHexLayout(), setNumberLayout()
    //   getHexLandCoords()
    //   Consider get/set layouts as a 1D array: r, c, contents. Only include odd-numbered rows.
    //
    // Maybe not valid:
    //   getPortTypeFromHexType()

    // TODO unlike roads, is there ever a time when sea edges are _not_ legal?
    //  (assuming water hexes on one or both sides of the edge)


    ////////////////////////////////////////////
    //
    // Make New Board
    //


    /**
     * Shuffle the hex tiles and layout a board.
     * This is called at server, but not at client;
     * client instead calls methods such as {@link #setLandHexLayout(int[])}.
     * @param opts {@link SOCGameOption Game options}, which may affect
     *          tile placement on board, or null.  <tt>opts</tt> must be
     *          the same as passed to constructor, and thus give the same size and layout
     *          (same {@link #getBoardEncodingFormat()}).
     */
    public void makeNewBoard(Hashtable opts)
    {
        SOCGameOption opt_breakClumps = (opts != null ? (SOCGameOption)opts.get("BC") : null);

        // shuffle and place the land hexes, numbers, and robber:
        // sets robberHex, contents of hexLayout[] and numberLayout[].
        // Adds to landHexLayout and nodesOnLand.
        // Clears cachedGetlandHexCoords.
        // Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
        // - Mainland:
        makeNewBoard_placeHexes
            (makeNewBoard_landHexTypes_v1, LANDHEX_DICEPATH_MAINLAND, makeNewBoard_diceNums_v1, opt_breakClumps);
        // - Islands:
        makeNewBoard_placeHexes
            (LANDHEX_TYPE_ISLANDS, LANDHEX_COORD_ISLANDS_ALL, LANDHEX_DICENUM_ISLANDS, (SOCGameOption) null);
        // Set up legalRoadEdges:
        makeNewBoard_makeLegalRoadsFromLandNodes();

        // copy and shuffle the ports, and check vs game option BC
        int[] portTypes_main = new int[PORTS_TYPE_V1.length],
              portTypes_islands = new int[PORT_TYPE_ISLANDS.length];
        System.arraycopy(PORTS_TYPE_V1, 0, portTypes_main, 0, portTypes_main.length);
        System.arraycopy(PORT_TYPE_ISLANDS, 0, portTypes_islands, 0, portTypes_islands.length);
        makeNewBoard_shufflePorts
            (portTypes_main, opt_breakClumps);
        makeNewBoard_shufflePorts
            (portTypes_islands, null);

        // copy port types to beginning of portsLayout[]
        portsCount = PORTS_TYPE_V1.length + PORT_TYPE_ISLANDS.length;
        if ((portsLayout == null) || (portsLayout.length != (3 * portsCount)))
            portsLayout = new int[3 * portsCount];
        System.arraycopy(portTypes_main, 0,
            portsLayout, 0, portTypes_main.length);
        System.arraycopy(portTypes_islands, 0,
            portsLayout, portTypes_main.length, portTypes_islands.length);

        // place the ports (hex numbers and facing) within portsLayout[] and nodeIDtoPortType.
        // fill out the ports[] vectors with node coordinates where a trade port can be placed.
        nodeIDtoPortType = new Hashtable();

        // - main island:
        // i == port type array index
        // j == port edge & facing array index
        final int L = portTypes_main.length;
        for (int i = 0, j = 0; i < L; ++i)
        {
            final int ptype = portTypes_main[i];  // also == portsLayout[i]
            final int edge = PORT_EDGE_FACING_MAINLAND[j];
            ++j;
            final int facing = PORT_EDGE_FACING_MAINLAND[j];
            ++j;
            final int[] nodes = getAdjacentNodesToEdge_arr(edge);
            placePort(ptype, -1, facing, nodes[0], nodes[1]);
            portsLayout[i + portsCount] = edge;
            portsLayout[i + (2 * portsCount)] = facing;
        }

        // - outlying islands:
        // i == port type array index
        // j == port edge & facing array index
        for (int i = 0, j = 0; i < PORT_TYPE_ISLANDS.length; ++i)
        {
            final int ptype = portTypes_islands[i];  // also == portsLayout[i+L]
            final int edge = PORT_EDGE_FACING_ISLANDS[j];
            ++j;
            final int facing = PORT_EDGE_FACING_ISLANDS[j];
            ++j;
            final int[] nodes = getAdjacentNodesToEdge_arr(edge);
            placePort(ptype, -1, facing, nodes[0], nodes[1]);
            portsLayout[L + i + portsCount] = edge;
            portsLayout[L + i + (2 * portsCount)] = facing;
        }
    }

    /**
     * For {@link #makeNewBoard(Hashtable)}, place the land hexes, number, and robber,
     * after shuffling landHexType[].
     * Sets robberHex, contents of hexLayoutLg[] and numberLayoutLg[].
     * Adds to {@link #landHexLayout} and {@link #nodesOnLand}.
     * Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
     * (for land hex resource types).
     *<P>
     * This method does not clear out {@link #hexLayoutLg} or {@link #numberLayoutLg}
     * before it starts placement.  You can call it multiple times to set up multiple
     * areas of land hexes.
     *<P>
     * This method clears {@link #cachedGetlandHexCoords} to <tt>null</tt>.
     *
     * @param landHexType  Resource type to place into {@link #hexLayout} for each land hex; will be shuffled.
     *                    Values are {@link #CLAY_HEX}, {@link #DESERT_HEX}, etc.
     * @param numPath  Coordinates within {@link #hexLayoutLg} (also within {@link #numberLayoutLg}) for each land hex;
     *                    same array length as <tt>landHexType[]</tt>
     *                    <BR> Also must contain the coordinate of each land hex.
     * @param number   Numbers to place into {@link #numberLayoutLg} for each land hex;
     *                    array length is <tt>landHexType[].length</tt> minus 1 for each desert in <tt>landHexType[]</tt>
     * @param optBC    Game option "BC" from the options for this board, or <tt>null</tt>.
     */
    private final void makeNewBoard_placeHexes
        (int[] landHexType, final int[] numPath, final int[] number, SOCGameOption optBC)
    {
        final boolean checkClumps = (optBC != null) && optBC.getBoolValue();
        final int clumpSize = checkClumps ? optBC.getIntValue() : 0;
        boolean clumpsNotOK = checkClumps;

        if (numPath.length > 0)
            cachedGetlandHexCoords = null;  // invalidate the previous cached set

        do   // will re-do placement until clumpsNotOK is false
        {
            // shuffle the land hexes 10x
            for (int j = 0; j < 10; j++)
            {
                int idx, tmp;
                for (int i = 0; i < landHexType.length; i++)
                {
                    // Swap a random card below the ith card with the ith card
                    idx = Math.abs(rand.nextInt() % (landHexType.length - i));
                    if (idx == i)
                        continue;
                    tmp = landHexType[idx];
                    landHexType[idx] = landHexType[i];
                    landHexType[i] = tmp;
                }
            }

            int cnt = 0;
            for (int i = 0; i < landHexType.length; i++)
            {
                final int r = numPath[i] >> 8,
                          c = numPath[i] & 0xFF;

                // place the land hexes
                hexLayoutLg[r][c] = landHexType[i];

                // place the robber on the desert
                if (landHexType[i] == DESERT_HEX)
                {
                    setRobberHex(numPath[i], false);
                    numberLayoutLg[r][c] = -1;
                    // TODO do we want to not set robberHex? or a specific point?
                }
                else
                {
                    // place the numbers
                    numberLayoutLg[r][c] = number[cnt];
                    cnt++;
                }
            }  // for (i in landHex)

            if (checkClumps)
            {
                Vector unvisited = new Vector();  // contains each land hex's coordinate
                for (int i = 0; i < landHexType.length; ++i)
                    unvisited.addElement(new Integer(numPath[i]));

                clumpsNotOK = makeNewBoard_checkLandHexResourceClumps
                    (unvisited, clumpSize);
            }

        } while (clumpsNotOK);

        // Now that we know this layout is okay,
        // add the hex coordinates to landHexLayout,
        // and the hexes' nodes to nodesOnLand.

        for (int i = 0; i < landHexType.length; i++)
            landHexLayout.add(new Integer(numPath[i]));
        makeNewBoard_fillNodesOnLandFromHexes(numPath);

    }  // makeNewBoard_placeHexes

    /**
     * Calculate the board's legal settlement/city nodes, based on land hexes.
     * All corners of these hexes are legal for settlements/cities.
     *<P>
     * Iterative: Can call multiple times, giving different hexes each time.
     * Each call will add those hexes to {@link #nodesOnLand}.
     * Before the first call, clear <tt>nodesOnLand</tt>.
     * @param landHexCoords  Coordinates of a contiguous group of land hexes
     * @see #makeNewBoard_makeLegalRoadsFromLandNodes()
     */
    private void makeNewBoard_fillNodesOnLandFromHexes
        (final int landHexCoords[])
    {
        for (int i = 0; i < landHexCoords.length; ++i)
        {
            final int[] nodes = getAdjacentNodesToHex(landHexCoords[i]);
            for (int j = 0; j < 6; ++j)
                nodesOnLand.add(new Integer(nodes[j]));
            // it's ok to add if this set already contains an Integer equal to nodes[j].
        }

    }  // makeNewBoard_makeLegalNodesFromHexes

    /**
     * Once the legal settlement/city nodes ({@link #nodesOnLand})
     * are established from land hexes, fill {@link #legalRoadEdges}.
     * Not iterative; clears all previous legal roads.
     * @see #makeNewBoard_fillNodesOnLandFromHexes(int[])
     */
    private void makeNewBoard_makeLegalRoadsFromLandNodes()
    {
        // About corners/concave parts:
        //   Set of the valid nodes will contain both ends of the edge;
        //   anything concave across a sea would be missing at least 1 node, in the water along the way.

        // Go from nodesOnLand, iterate all nodes:

        legalRoadEdges.clear();

        Iterator nodes = nodesOnLand.iterator();
        while (nodes.hasNext())
        {
            final int node = ((Integer) nodes.next()).intValue();
            for (int dir = 0; dir < 3; ++dir)
            {
                int nodeAdjac = getAdjacentNodeToNode(node, dir);
                if (nodesOnLand.contains(new Integer(nodeAdjac)))
                {
                    legalRoadEdges.add
                        (new Integer(getAdjacentEdgeToNode(node, dir)));
                    // it's ok to add if this set already contains an Integer equal to that edge.
                }
            }
        }

    }  // makeNewBoard_makeLegalRoadsFromNodes


    ////////////////////////////////////////////
    //
    // Board info getters
    //

    
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
     * Given a hex coordinate / hex number, return the (dice-roll) number on that hex
     *
     * @param hex  the coordinates for a hex, or -1 if invalid
     *
     * @return the dice-roll number on that hex, or 0
     */
    public int getNumberOnHexFromNumber(final int hex)
    {
        if (hex == -1)
            return 0;

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
     * @see #getLandHexCoords()
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
     * The hex coordinates of all land hexes.
     *<P>
     * Before v1.2.00, this was <tt>getHexLandCoords()</tt>.
     *
     * @return land hex coordinates, in no particular order, or null if none (all water).
     * @since 1.1.08
     */
    public int[] getLandHexCoords()
    {
        final int LHL = landHexLayout.size();
        if (LHL == 0)
            return null;
        if ((cachedGetlandHexCoords != null) && (LHL == cachedGetlandHexCoords.length))
            return cachedGetlandHexCoords;

        int[] hexCoords = new int[LHL];
        Iterator hexes = landHexLayout.iterator();
        for (int i = 0; hexes.hasNext(); ++i)
            hexCoords[i] = ((Integer) hexes.next()).intValue();

        cachedGetlandHexCoords = hexCoords;
        return hexCoords;
    }

    /**
     * Get the land hex layout, for sending from server to client.
     * Contains 3 int elements per land hex: Coordinate, Hex type (resource), Dice Number.
     * @return the layout, or null if no land hexes.
     */
    public int[] getLandHexLayout()
    {
        final int LHL = landHexLayout.size();
        if (LHL == 0)
            return null;

        int[] lh = new int[3 * LHL];
        int i = 0;
        Iterator hexes = landHexLayout.iterator();
        while (hexes.hasNext())
        {
            final int hexCoord = ((Integer) hexes.next()).intValue();
            final int r = hexCoord >> 8,
                      c = hexCoord & 0xFF;
            lh[i] = hexCoord;  ++i;
            lh[i] = hexLayoutLg[r][c];  ++i;
            lh[i] = numberLayoutLg[r][c];  ++i;
        }
        return lh;
    }

    /**
     * Set the land hex layout, sent from server to client.
     * Contains 3 int elements per land hex: Coordinate, Hex type (resource), Dice Number.
     * Clears landHexLayout, diceLayoutLg, numberLayoutLg,
     * nodesOnLand and legalRoadEdges before beginning.
     * After calling this, please call
     * {@link SOCGame#setPlayersLandHexCoordinates() game.setPlayersLandHexCoordinates()}.
     * Once the potential/legal settlements are known, call each player's
     * {@link SOCPlayer#setPotentialSettlements(Collection, boolean)}.
     * @param  lh  the layout, or null if no land hexes
     */
    public void setLandHexLayout(final int[] lh)
    {
        // Clear the previous contents:
        landHexLayout.clear();
        nodesOnLand.clear();
        legalRoadEdges.clear();
        cachedGetlandHexCoords = null;
        for (int r = 0; r <= boardHeight; ++r)
        {            
            Arrays.fill(hexLayoutLg[r], WATER_HEX);
            Arrays.fill(numberLayoutLg[r], 0);
        }

        if (lh == null)
            return;  // all water for now

        int[] hcoords = new int[lh.length / 3];
        for (int i = 0, ih = 0; i < lh.length; ++ih)
        {
            final int hexCoord = lh[i];  ++i;
            final int r = hexCoord >> 8,
                      c = hexCoord & 0xFF;
            hcoords[ih] = hexCoord;
            landHexLayout.add(new Integer(hexCoord));
            hexLayoutLg[r][c] = lh[i];  ++i;
            numberLayoutLg[r][c] = lh[i];  ++i;
        }
        cachedGetlandHexCoords = hcoords;
    }

    /**
     * Get the legal and potential settlements, after {@link #makeNewBoard(Hashtable)}.
     * For use only by SOCGame at server.
     */
    HashSet getLegalAndPotentialSettlements()
    {
        return nodesOnLand;
    }

    /**
     * Set the legal and potential settlements, and recalculate
     * the legal roads; called at client only.
     *<P>
     * Call this only after {@link #setLandHexLayout(int[])}.
     * After calling this method, you can get the new legal road set
     * with {@link #initPlayerLegalRoads()}.
     * @param landNodes  The set of settlement node coordinates as {@link Integer}s;
     *    typically a {@link HashSet} or {@link Vector}
     */
    public void setLegalAndPotentialSettlements(Collection landNodes)
    {
        if (landNodes instanceof HashSet)
        {
            nodesOnLand = (HashSet) (((HashSet) landNodes).clone());
        } else {
            nodesOnLand.clear();
            nodesOnLand.addAll(landNodes);
        }
        makeNewBoard_makeLegalRoadsFromLandNodes();
    }

    /**
     * Create and initialize a {@link SOCPlayer}'s legalRoads set.
     *<P>
     * Because the v3 board layout varies:
     * At the server, call this after {@link #makeNewBoard(Hashtable)}.
     * At the client, call this after {@link #setLegalAndPotentialSettlements(Collection)}.
     *
     * @return the set of legal edge coordinates for roads, as a new Set of {@link Integer}s
     * @since 1.1.12
     */
    HashSet initPlayerLegalRoads()
    {
        return (HashSet) (legalRoadEdges.clone());
    }


    ////////////////////////////////////////
    //
    // GetAdjacent____ToHex
    //


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

        getAdjacentHexes2Hex_AddIfOK(hexes, includeWater, r - 2, c - 1);  // NW (northwest)
        getAdjacentHexes2Hex_AddIfOK(hexes, includeWater, r - 2, c + 1);  // NE
        getAdjacentHexes2Hex_AddIfOK(hexes, includeWater, r,     c - 2);  // W
        getAdjacentHexes2Hex_AddIfOK(hexes, includeWater, r,     c + 2);  // E
        getAdjacentHexes2Hex_AddIfOK(hexes, includeWater, r + 2, c - 1);  // SW
        getAdjacentHexes2Hex_AddIfOK(hexes, includeWater, r + 2, c + 1);  // SE

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
     * @param r  Hex row coordinate
     * @param c  Hex column coordinate
     */
    private final void getAdjacentHexes2Hex_AddIfOK
        (Vector addTo, final boolean includeWater, final int r, final int c)
    {
        if ((r <= 0) || (c <= 0) || (r >= boardHeight) || (c >= boardWidth))
            return;  // not within the board's valid hex boundaries
        if ((r % 2) == 0)
            return;  // not a valid hex row

        if (includeWater
            || (hexLayoutLg[r][c] <= MAX_LAND_HEX))
        {
            addTo.addElement(new Integer((r << 8) | c));
        }
    }

    /**
     * The node coordinate adjacent to this hex in a given direction.
     * Since all hexes have 6 nodes, all node coordinates are valid
     * if the hex coordinate is valid.
     *
     * @param hexCoord Coordinate ("ID") of this hex
     * @param dir  Direction, clockwise from top (northern point of hex):
     *           0 is north, 1 is northeast, etc, 5 is northwest.
     * @return Node coordinate in that direction
     * @throws IllegalArgumentException if dir &lt; 0 or dir &gt; 5
     */
    public int getAdjacentNodeToHex(final int hexCoord, final int dir)
        throws IllegalArgumentException
    {
        if ((dir >= 0) && (dir <= 5))
            return hexCoord + A_NODE2HEX[dir][0] + A_NODE2HEX[dir][1];
        else
            throw new IllegalArgumentException("dir");
    }

    /**
     * The node coordinates adjacent to this hex in all 6 directions.
     * Since all hexes have 6 nodes, all node coordinates are valid
     * if the hex coordinate is valid.
     *
     * @param hexCoord Coordinate of this hex
     * @return Node coordinate in all 6 directions,
     *           clockwise from top (northern point of hex):
     *           0 is north, 1 is northeast, etc, 5 is northwest.
     * @since 1.2.00
     * @see #getAdjacentNodeToHex(int, int)
     */
    public int[] getAdjacentNodesToHex(final int hexCoord)
    {
        int[] node = new int[6];
        for (int dir = 0; dir < 6; ++dir)
            node[dir] = hexCoord + A_NODE2HEX[dir][0] + A_NODE2HEX[dir][1];
        return node;
    }


    ////////////////////////////////////////////
    //
    // GetAdjacent____ToEdge
    //
    // Determining (r,c) edge direction: | / \
    //   "|" if r is odd
    //   Otherwise: s = r/2
    //   "/" if (s,c) is even,odd or odd,even
    //   "\" if (s,c) is odd,odd or even,even


    /**
     * The hex touching an edge in a given direction,
     * either along its length or at one end node.
     * Each edge touches up to 4 valid hexes.
     * @param edgeCoord The edge's coordinate. Not checked for validity.
     * @param facing  Facing from edge; 1 to 6.
     *           This will be either a direction perpendicular to the edge,
     *           or towards one end. Each end has two facing directions angled
     *           towards it; both will return the same hex.
     *           Facing 2 is {@link #FACING_E}, 3 is {@link #FACING_SE}, 4 is SW, etc.
     * @return hex coordinate of hex in the facing direction,
     *           or 0 if that hex would be off the edge of the board.
     * @throws IllegalArgumentException if facing &lt; 1 or facing &gt; 6
     */
    public int getAdjacentHexToEdge(final int edgeCoord, final int facing)
        throws IllegalArgumentException
    {
        if ((facing < 1) && (facing > 6))
            throw new IllegalArgumentException();
        int r = (edgeCoord >> 8),
            c = (edgeCoord & 0xFF);

        // "|" if r is odd
        if ((r%2) == 1)
        {
            switch (facing)
            {
            case FACING_E:
                ++c;
                break;
            case FACING_W:
                --c;
                break;
            case FACING_NE: case FACING_NW:
                r = r - 2;
                break;
            case FACING_SE: case FACING_SW:
                r = r + 2;
                break;
            }
        }

        // "/" if (s,c) is even,odd or odd,even
        else if ((c % 2) != ((r/2) % 2))
        {
            switch (facing)
            {
            case FACING_NW:
                --r;
                break;
            case FACING_SE:
                ++r;
                ++c;
                break;
            case FACING_NE: case FACING_E:
                --r;
                c = c + 2;
                break;
            case FACING_SW: case FACING_W:
                ++r;
                --c;
                break;
            }
        }
        else
        {
            // "\" if (s,c) is odd,odd or even,even
            switch (facing)
            {
            case FACING_NE:
                --r;
                ++c;
                break;
            case FACING_SW:
                ++r;
                break;
            case FACING_E: case FACING_SE:
                ++r;
                c = c + 2;
                break;
            case FACING_W: case FACING_NW:
                --r;
                --c;
                break;
            }
        }

        if ((r > 0) && (c > 0) && (r < boardHeight) && (c < boardWidth))
            return ( (r << 8) | c );   // bounds-check OK: within the outer edge
        else
            return 0;  // hex is not on the board
    }

    /**
     * Get the edge coordinates of the 2 to 4 edges adjacent to this edge.
     * @param coord  Edge coordinate; not checked for validity
     * @return the valid adjacent edges to this edge, as a Vector of Integer coordinates
     */
    public Vector getAdjacentEdgesToEdge(final int coord)
    {
        final int r = (coord >> 8),
                  c = (coord & 0xFF);

        // Get offsets for edge direction
        final int[] offs;
        {
            final int dir;
            if ( (r%2) == 1 )
                dir = 0;  // "|"
            else if ( (c%2) != ((r/2) % 2) )
                dir = 1;  // "/"
            else
                dir = 2;  // "\"
            offs = A_EDGE2EDGE[dir];
        }

        Vector edge = new Vector(4);
        for (int i = 0; i < 8; )
        {
            final int er = r + offs[i];  ++i;
            final int ec = c + offs[i];  ++i;
            if (isEdgeInBounds(r,c))
                edge.addElement(new Integer( (er << 8) | ec ));
        }
        return edge;
    }

    /**
     * Adjacent node coordinates to an edge (that is, the nodes that are the two ends of the edge).
     * @return the nodes that touch this edge, as a Vector of Integer coordinates
     * @see #getAdjacentNodesToEdge_arr(int)
     */
    public Vector getAdjacentNodesToEdge(final int coord)
    {
        Vector nodes = new Vector(2);
        final int[] narr = getAdjacentNodesToEdge_arr(coord);
        nodes.addElement(new Integer(narr[0]));
        nodes.addElement(new Integer(narr[1]));
        return nodes;
    }

    /**
     * Adjacent node coordinates to an edge (that is, the nodes that are the two ends of the edge).
     * @return the nodes that touch this edge, as an array of 2 integer coordinates
     * @see #getAdjacentNodesToEdge(int)
     */
    public int[] getAdjacentNodesToEdge_arr(final int coord)
    {
        int[] nodes = new int[2];

        final int r = coord >> 8;
        if ((r%2) == 1)
        {
            // "|" if r is odd
            nodes[0] = coord;           // (r,c)
            nodes[1] = coord + 0x0001;  // (r,c+1)
        }
        else
        {
            // either "/" or "\"
            nodes[0] = coord - 0x0100;  // (r-1,c)
            nodes[1] = coord + 0x0100;  // (r+1,c)
        }

        return nodes;

        // Bounds-check OK: if edge coord is valid, its nodes are both valid
    }


    ////////////////////////////////////////////
    //
    // GetAdjacent____ToNode
    //
    // Determining (r,c) node direction: Y or A
    //  s = r/2
    //  "Y" if (s,c) is even,odd or odd,even
    //  "A" if (s,c) is odd,odd or even,even


    /**
     * Get the coordinates of the valid hexes adjacent to this node.
     * @param coord  Node coordinate.  Is not checked for validity.
     * @return the coordinates (Integers) of the 1 to 3 hexes touching this node
     */
    public Vector getAdjacentHexesToNode(final int nodeCoord)
    {
        // Determining (r,c) node direction: Y or A
        //  s = r/2
        //  "Y" if (s,c) is even,odd or odd,even
        //  "A" if (s,c) is odd,odd or even,even
        // Bounds check for hexes: r > 0, c > 0, r < height, c < width

        final int r = (nodeCoord >> 8), c = (nodeCoord & 0xFF);
        Vector hexes = new Vector(3);

        final boolean nodeIsY = ( (c % 2) != ((r/2) % 2) );
        if (nodeIsY)
        {
            // North: (r-1, c)
            if (r > 0)
                hexes.addElement(new Integer(nodeCoord - 0x0100));

            if (r < boardHeight)
            {
                // SW: (r+1, c-1)
                if (c > 0)
                    hexes.addElement(new Integer((nodeCoord + 0x0100) - 1));

                // SE: (r+1, c+1)
                if (c < boardWidth)
                    hexes.addElement(new Integer((nodeCoord + 0x0100) + 1));
            }
        }
        else
        {
            // South: (r+1, c)
            if (r < boardHeight)
                hexes.addElement(new Integer(nodeCoord + 0x0100));

            if (r > 0)
            {
                // NW: (r-1, c-1)
                if (c > 0)
                    hexes.addElement(new Integer((nodeCoord - 0x0100) - 1));

                // NE: (r-1, c+1)
                if (c < boardWidth)
                    hexes.addElement(new Integer((nodeCoord - 0x0100) + 1));
            }
        }

        return hexes;
    }

    /**
     * Given a node, get the valid adjacent edge in a given direction, if any.
     *<P>
     * Along the edge of the board layout, valid land nodes/edges
     * have some adjacent nodes/edges which may be
     * "off the board" and thus invalid; check the return value.
     *
     * @param nodeCoord  Node coordinate to go from; not checked for validity.
     * @param nodeDir  0 for northwest or southwest; 1 for northeast or southeast;
     *     2 for north or south
     * @return  The adjacent edge in that direction, or -9 if none (if off the board)
     * @throws IllegalArgumentException if <tt>nodeDir</tt> is less than 0 or greater than 2
     * @see #getAdjacentEdgesToNode(int)
     * @see #getEdgeBetweenAdjacentNodes(int, int)
     * @see #getAdjacentNodeToNode(int, int)
     */
    public int getAdjacentEdgeToNode(final int nodeCoord, final int nodeDir)
        throws IllegalArgumentException
    {
        // Determining (r,c) node direction: Y or A
        //  s = r/2
        //  "Y" if (s,c) is even,odd or odd,even
        //  "A" if (s,c) is odd,odd or even,even

        int r = (nodeCoord >> 8), c = (nodeCoord & 0xFF);

        switch (nodeDir)
        {
        case 0:  // NW or SW (upper-left or lower-left edge)
            --c;  // (r, c-1)
            break;

        case 1:  // NE or SE
            // (r, c) is already correct
            break;

        case 2:  // N or S
            final boolean nodeIsY = ( (c % 2) != ((r/2) % 2) );
            if (nodeIsY)
                ++r;  // S: (r+1, c)
            else
                --r;  // N: (r-1, c)
            break;

        default:
            throw new IllegalArgumentException("nodeDir out of range: " + nodeDir);
        }

        if (isEdgeInBounds(r, c))
            return ((r << 8) | c);
        else
            return -9;
    }

    /**
     * Given a pair of adjacent node coordinates, get the edge coordinate
     * that connects them.
     *<P>
     * Does not check actual roads or other pieces on the board.
     *
     * @param nodeA  Node coordinate adjacent to <tt>nodeB</tt>; not checked for validity
     * @param nodeB  Node coordinate adjacent to <tt>nodeA</tt>; not checked for validity
     * @return edge coordinate, or -9 if <tt>nodeA</tt> and <tt>nodeB</tt> aren't adjacent
     * @see #getAdjacentEdgesToNode(int)
     * @see #getAdjacentEdgeToNode(int, int)
     */
    public int getEdgeBetweenAdjacentNodes(final int nodeA, final int nodeB)
    {
        final int edge;

        switch (nodeB - nodeA)
        {
        case 0x01:  // c+1, same r
            // nodeB and edge are NE or SE of nodeA
            edge = nodeA;
            break;

        case -0x01:  // c-1, same r
            // nodeB and edge are NW or SW of nodeA
            edge = nodeB;
            break;

        case 0x0200:  // r+2, same c
            // nodeB,edge are S of nodeA
            edge = nodeA + 0x0100;
            break;

        case -0x0200:  // r-2, same c
            // nodeB,edge are N of nodeA
            edge = nodeA - 0x0100;
            break;

        default:
            edge = -9;  // not adjacent nodes
        }

        return edge;
    }

    /**
     * Determine if this node and edge are adjacent.
     *
     * @param nodeCoord  Node coordinate; not bounds-checked
     * @param edgeCoord  Edge coordinate; bounds-checked against board boundaries.
     * @return  is the edge in-bounds and adjacent?
     * @see #getEdgeBetweenAdjacentNodes(int, int)
     */
    public boolean isEdgeAdjacentToNode(final int nodeCoord, final int edgeCoord)
    {
        final int edgeR = (edgeCoord >> 8), edgeC = (edgeCoord & 0xFF);
        if (! isEdgeInBounds(edgeR, edgeC))
            return false;

        if ((edgeCoord == nodeCoord) || (edgeCoord == (nodeCoord - 0x01)))
            return true;  // same row; NE,SE,NW or SW

        final int nodeC = (nodeCoord & 0xFF);
        if (edgeC != nodeC)
            return false;  // not same column; not N or S

        final int nodeR = (nodeCoord >> 8);
        final boolean nodeIsY = ( (nodeC % 2) != ((nodeR/2) % 2) );
        if (nodeIsY)
            return (edgeR == (nodeR + 1));  // S
        else
            return (edgeR == (nodeR - 1));  // N
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
        case 0:  // NW or SW (upper-left or lower-left edge)
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

        if (isNodeInBounds(r, c))
            return ((r << 8) | c);
        else
            return -9;
    }


    ////////////////////////////////////////////
    //
    // 2 Away
    //


    /**
     * Given an initial node, and a second node 2 nodes away,
     * calculate the road/edge coordinate (adjacent to the initial
     * node) going towards the second node.
     * @param node  Initial node coordinate; not validated
     * @param node2away  Second node coordinate; should be 2 away,
     *     but this is not validated
     * @return  An edge coordinate, adjacent to initial node,
     *   in the direction of the second node.
     * @see #getAdjacentNodeToNode2Away(int, int)
     */
    public int getAdjacentEdgeToNode2Away
        (final int node, final int node2away)
    {
        // Determining (r,c) node direction: Y or A
        //  s = r/2
        //  "Y" if (s,c) is even,odd or odd,even
        //  "A" if (s,c) is odd,odd or even,even

        final int r = (node >> 8), c = (node & 0xFF),
            r2 = (node2away >> 8), c2 = (node2away & 0xFF);
        final int roadEdge;

        final boolean nodeIsY = ( (c % 2) != ((r/2) % 2) );
        if (nodeIsY)
        {
            if (r2 > r)
            {
                // south
                roadEdge = node + 0x0100;  // (+1, 0)
            }
            else if (c2 < c)
            {
                // NW
                roadEdge = node - 1; // (0, -1)
            }
            else
            {
                // NE
                roadEdge = node;  // (0, +0)
            }
        }
        else
        {
            if (r2 < r)
            {
                // north
                roadEdge = node - 0x0100;  // (-1, 0)
            }
            else if (c2 < c)
            {  // SW
                roadEdge = node - 1;  // (0, -1)
            }
            else
            {  // SE
                roadEdge = node;  // (0, +0)
            }
        }

        return roadEdge;
    }


    /**
     * Get the coordinate of another node 2 away, based on a starting node.
     * Facing is indexed by the facing directions: {@link #FACING_NE} is 1,
     * {@link #FACING_E} is 2, etc; {@link #FACING_NW} is 6.
     *
     * @param nodeCoord  Starting node's coordinate
     * @param facing    Facing from node; 1 to 6.
     *           This will be one of the 6 directions
     *           from a node to another node 2 away.
     *           Facing 2 is {@link #FACING_E}, 3 is {@link #FACING_SE}, 4 is SW, etc.
     * @return the node coordinate, or -9 if that node is not
     *   {@link #isNodeOnLand(int) on the board}.
     * @see #getAdjacentNodeToNode(int, int)
     * @see #getAdjacentEdgeToNode2Away(int, int)
     * @see #isNode2AwayFromNode(int, int)
     * @throws IllegalArgumentException if facing &lt; 1 or facing &gt; 6
     */
    public int getAdjacentNodeToNode2Away(final int nodeCoord, int facing)
        throws IllegalArgumentException
    {
        if ((facing < 1) || (facing > 6))
            throw new IllegalArgumentException("bad facing: " + facing);

        int r = (nodeCoord >> 8), c = (nodeCoord & 0xFF);
        facing = facing * 2;  // array has 2 elements per facing
        r = r + NODE_TO_NODE_2_AWAY[facing];
        ++facing;
        c = c + NODE_TO_NODE_2_AWAY[facing];
        if (! isNodeInBounds(r, c))
            return -9;
        else
            return ((r << 8) | c);
    }

    /**
     * Determine if these 2 nodes are 2 nodes apart on the board,
     * by the node coordinate arithmetic.
     *
     * @param n1  Node coordinate; not validated
     * @param n2  Node coordinate; not validated
     * @return are these nodes 2 away from each other?
     * @see #getAdjacentNodeToNode2Away(int, int)
     */
    public boolean isNode2AwayFromNode(final int n1, final int n2)
    {
        final int dr = (n2 >> 8) - (n1 >> 8),      // delta for rows
                  dc = (n2 & 0xFF) - (n1 & 0xFF);  // delta for cols
        for (int facing = 1; facing <= 6; ++facing)
        {
            int i = 2 * facing;
            if ((dr == NODE_TO_NODE_2_AWAY[i])
                && (dc == NODE_TO_NODE_2_AWAY[i+1]))
                    return true;
        }
        return false;
    }


    ////////////////////////////////////////////
    //
    // Check coordinates for validity.
    //


    /**
     * Is this an edge coordinate within the board's boundaries,
     * not overlapping or off the side of the board?
     * TODO description... valid range for nodes(corners) of hexes laid out,
     * but doesn't check for "misalignment" in the middle of the board.
     * @param r  Node coordinate's row
     * @param c  Node coordinate's column
     * @see #isEdgeInBounds(int, int)
     * @see #isNodeOnLand(int)
     */
    public final boolean isNodeInBounds(final int r, final int c)
    {
        /*
        We only need to check when r==0 or r==h:
          For rows in the middle, all cols are valid nodes on hexes above and/or below
        First row of hexes is offset +1 column, so:
        Node (0,0) is never valid
        Node (0,w) valid only if 1st row of hexes longer than 2nd row
          so, if w odd
        Node (h,0) valid only if last hex row begins in column 0, not 1
          so, if h/2 even
        Node (h,w) valid only if last hex row ends in column w, not w-1
          so, if w odd and r/2 odd, or w even and r/2 even
         */

        if ((r > 0) && (r < boardHeight))
            return ((c >= 0) && (c <= boardWidth));
        if ((r < 0) || (r > boardHeight))
            return false;

        // r == 0 or r == boardHeight.
        if ((c > 0) && (c < boardWidth))
            return true;

        // c == 0 or c == boardWidth.
        if (r == 0)
        {
            if (c == 0)
                return false;
            else
                return ((boardWidth % 2) == 1); 
        } else {
            // r == boardHeight
            if (c == 0)
                return (((r/2) % 2) == 0);
            else
                return ((boardWidth % 2) == ((r/2) % 2));
        }
    }

    /**
     * Is this an edge coordinate within the board's boundaries,
     * not overlapping or off the side of the board?
     * TODO description... valid range for edges(sides) of hexes laid out,
     * but doesn't check for "misalignment" in the middle of the board.
     * @param r  Edge coordinate's row
     * @param c  Edge coordinate's column
     * @see #isNodeInBounds(int, int)
     */
    public final boolean isEdgeInBounds(final int r, final int c)
    {
        /*
        For even rows in the middle, 0 <= c < w are valid edges on hexes above and/or below
        For odd rows in the middle (vertical edges), 0 <= c <= w can be valid
        For r==0 or r==h:
        First row of hexes is offset +1 column, so:
        Edge (0,0) is never valid
        Edge (h,0) is valid if its left-end node (h,0) is valid
        Edge (0,w-1) or (h,w-1) is valid if its right-end node (*,w) is valid
         */

        if (c < 0)
            return false;
        if ((r > 0) && (r < boardHeight))
        {
            if ((r % 2) == 0)
                return (c < boardWidth);
            else
                return (c <= boardWidth);
        }
        if ((r < 0) || (r > boardHeight))
            return false;

        // r == 0 or r == boardHeight.
        if (c == 0)
        {
            if (r == 0)
                return false;
            else
                return isNodeInBounds(r, 0);
        }
        else if (c < (boardWidth - 1))
            return true;
        else if (c == boardWidth)
            return false;

        // c == boardWidth - 1.
        return isNodeInBounds(r, c+1);
    }


    ////////////////////////////////////////////
    //
    // Ports
    //


    public int getPortsCount()
    {
        return portsCount;
    }

    /**
     * Set the port information at the client, sent from the server from
     * {@link #getPortsLayout()}.
     *<P>
     * <b>Note:</b> This v3 layout ({@link #BOARD_ENCODING_LARGE}) stores more information
     * within the port layout array.  If you call {@link #setPortsLayout(int[])}, be sure
     * you are giving all information returned by {@link #getPortsLayout()}, not just the
     * port types.
     */
    public void setPortsLayout(final int[] portTypesAndInfo)
    {
        /**
         * n = port count = portTypesAndInfo.length / 3.
         * The port types are stored at the beginning, from index 0 to n - 1.
         * The next n indexes store each port's edge coordinate.
         * The next n store each port's facing (towards land).
         */
        portsLayout = portTypesAndInfo;

        portsCount = portTypesAndInfo.length / 3;

        // Clear any previous port layout info
        if (nodeIDtoPortType == null)
            nodeIDtoPortType = new Hashtable();
        else
            nodeIDtoPortType.clear();
        for (int i = 0; i < ports.length; ++i)
            ports[i].removeAllElements();

        // Place the new ports
        for (int i = 0; i < portsCount; ++i)
        {
            final int ptype = portTypesAndInfo[i];
            final int edge = portTypesAndInfo[i + portsCount],
                      facing = portTypesAndInfo[i + (2 * portsCount)];

            final int[] nodes = getAdjacentNodesToEdge_arr(edge);
            placePort(ptype, -1, facing, nodes[0], nodes[1]);
            ports[ptype].addElement(new Integer(nodes[0])); 
            ports[ptype].addElement(new Integer(nodes[1])); 
        }
    }

    public int getPortFacing(int portNum)
    {
        if ((portNum < 0) || (portNum >= portsCount))
            return 0;
        return portsLayout[portNum - (2 * portsCount)];
    }

    public int[] getPortsEdges()
    {
        int[] edge = new int[portsCount];
        System.arraycopy(portsLayout, portsCount, edge, 0, portsCount);
        return edge;
    }

    public int[] getPortsFacing()
    {
        int[] facing = new int[portsCount];
        System.arraycopy(portsLayout, 2 * portsCount, facing, 0, portsCount);
        return facing;
    }


    ////////////////////////////////////////////
    //
    // Sample Layout
    //


    /**
     * My sample board layout: Main island's ports, clockwise from its northwest.
     * Each port has 2 elements.
     * First: Coordinate, in hex: 0xRRCC.
     * Second: Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     */
    private static final int PORT_EDGE_FACING_MAINLAND[] =
    {
        0x0002, FACING_SE,  0x0005, FACING_SW,
        0x0208, FACING_SW,  0x050A, FACING_W,
        0x0808, FACING_NW,  0x0A05, FACING_NW,
        0x0A02, FACING_NE,  0x0701, FACING_E,
        0x0301, FACING_E
    };

    /**
     * My sample board layout: Outlying islands' ports.
     * Each port has 2 elements.
     * First: Coordinate, in hex: 0xRRCC
     * Second: Facing
     */
    private static final int PORT_EDGE_FACING_ISLANDS[] =
    {
        0x060D, FACING_NW,   // - northeast island
        0x0A0E, FACING_SW,  0x0E0B, FACING_NW,        // - southeast island
        0x0E05, FACING_SE    // - southwest island
    };

    /**
     * Port types for the 4 outlying-island ports.
     * For the mainland's port types, use {@link SOCBoard#PORTS_TYPE_V1}.
     */
    private static final int PORT_TYPE_ISLANDS[] =
    {
        0, SHEEP_PORT, WHEAT_PORT, WOOD_PORT
    };

    /**
     * Sample board layout: Dice-number path (hex coordinates)
     * on the main island, spiraling inward from the shore.
     * The outlying islands have no dice path.
     * For the mainland's dice numbers, see SOCBoard.makeNewBoard.numPath_v1.
     * @see #LANDHEX_COORD_MAINLAND
     */
    private static final int LANDHEX_DICEPATH_MAINLAND[] =
    {
        // clockwise from northwest
        0x0103, 0x0105, 0x0107, 0x0308, 0x0509,
        0x0708, 0x0907, 0x0905, 0x0903, 0x0702,
        0x0501, 0x0302, 0x0304, 0x0306, 0x0507,
        0x0706, 0x0704, 0x0503, 0x0505
    };

    /**
     * My sample board layout: Main island's land hex coordinates, each row west to east.
     * @see #LANDHEX_DICEPATH_MAINLAND
     */
    private static final int LANDHEX_COORD_MAINLAND[] =
    {
        0x0103, 0x0105, 0x0107,
        0x0302, 0x0304, 0x0306, 0x0308,
        0x0501, 0x0503, 0x0505, 0x0507, 0x0509,
        0x0702, 0x0704, 0x0706, 0x0708,
        0x0903, 0x0905, 0x0907
    };

    /**
     * My sample board layout: All the outlying islands' land hex coordinates.
     * @see #LANDHEX_COORD_ISLANDS_EACH
     */
    private static final int LANDHEX_COORD_ISLANDS_ALL[] =
    {
        0x010D, 0x030C, 0x030E, 0x050D, 0x050F,
        0x0B0C, 0x0B0E, 0x0B10, 0x0D0B, 0x0D0D,
        0x0D01, 0x0D03, 0x0F04, 0x0F06
    };

    /**
     * My sample board layout: Each outlying island's land hex coordinates.
     * @see #LANDHEX_COORD_ISLANDS_ALL
     */
    private static final int LANDHEX_COORD_ISLANDS_EACH[][] =
    {
        { 0x010D, 0x030C, 0x030E, 0x050D, 0x050F },
        { 0x0B0C, 0x0B0E, 0x0B10, 0x0D0B, 0x0D0D },
        { 0x0D01, 0x0D03, 0x0F04, 0x0F06 }
    };

    /**
     * My sample board layout: Land hex types,
     * to be used with (for the main island) {@link #makeNewBoard_landHexTypes_v1}[].
     */
    private static final int LANDHEX_TYPE_ISLANDS[] =
    {
        CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, WHEAT_HEX, WHEAT_HEX, DESERT_HEX,
        WOOD_HEX, WOOD_HEX, DESERT_HEX, DESERT_HEX // TODO: should be GOLD_HEX, GOLD_HEX
    };

    /**
     * My sample board layout: Dice numbers for the outlying islands.
     * These islands have no defined NumPath; as long as 6 and 8 aren't
     * adjacent, and as long as GOLD_HEXes have rare numbers, all is OK.
     */
    private static final int LANDHEX_DICENUM_ISLANDS[] =
    {
        5, 4, 6, 3, 8,
        10, 9, 11, 5, 9,
        4, 10, 5  // leave 1 un-numbered, for the DESERT_HEX
    };

}

