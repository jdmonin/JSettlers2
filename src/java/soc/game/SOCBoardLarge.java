/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2011-2012 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;

/**
 * A representation of a larger (up to 127 x 127 hexes) JSettlers board,
 * with an arbitrary mix of land and water tiles.
 * Implements {@link SOCBoard#BOARD_ENCODING_LARGE}.
 * Activated with {@link SOCGameOption} <tt>"PLL"</tt>.
 *<P>
 * On this large sea board, there can optionally be multiple "land areas"
 * (groups of islands, or subsets of islands), if {@link #getLandAreasLegalNodes()} != null.
 * Land areas are groups of nodes on land; call {@link #getNodeLandArea(int)} to find a node's land area number.
 * The starting land area is {@link #getStartingLandArea()}, if players must start in a certain area.
 * During board setup, {@link #makeNewBoard(Hashtable)} calls
 * {@link #makeNewBoard_placeHexes(int[], int[], int[], int, SOCGameOption)}
 * once for each land area.  In some game scenarios, players and the robber can be
 * {@link #getPlayerExcludedLandAreas() excluded} from placing in some land areas.
 *<P>
 * Server and client must be 2.0.00 or newer ({@link #VERSION_FOR_ENCODING_LARGE}).
 * The board layout is sent using {@link #getLandHexLayout()} and {@link #getPortsLayout()},
 * followed by the robber hex and pirate hex (if they're &gt; 0),
 * and then (a separate message) the legal settlement/city nodes and land areas.
 *<P>
 * Ship pieces extend the {@link SOCRoad} class; road-related getters/setters will work on them,
 * but check {@link SOCRoad#isRoadNotShip()} to differentiate.
 * You cannot place both a road and a ship on the same coastal edge coordinate.
 *<P>
 * <h4> Geometry/Navigation methods: </h4>
 *<br><table border=1>
 *<TR><td>&nbsp;</td><td colspan=3>Adjacent to a:</td></TR>
 *<TR><td>Get the:</td> <td> Hex </td><td> Edge </td><td> Node </td></TR>
 *<TR><td> Hex </td>
 *    <td><!-- Hex adjac to hex -->
 *      {@link #getAdjacentHexesToHex(int, boolean)}
 *    </td>
 *    <td><!-- Hex adjac to edge -->
 *      {@link #getAdjacentHexToEdge(int, int)} <br>
 *      {@link #getAdjacentHexesToEdge_arr(int)} <br>
 *      {@link #getAdjacentHexesToEdgeEnds(int)}
 *    </td>
 *    <td><!-- Hex adjac to node -->
 *      {@link #getAdjacentHexesToNode(int)}
 *    </td>
 *</TR>
 *<TR><td> Edge </td>
 *    <td><!-- Edge adjac to hex -->
 *      {@link #getAdjacentEdgesToHex(int)} <br>
 *      {@link #isEdgeAdjacentToHex(int, int)}
 *    </td>
 *    <td><!-- Edge adjac to edge -->
 *      {@link #getAdjacentEdgesToEdge(int)}
 *    </td>
 *    <td><!-- Edge adjac to node -->
 *      {@link #getAdjacentEdgeToNode(int, int)} <br>
 *      {@link #getAdjacentEdgeToNode2Away(int, int)} <br>
 *      {@link #getAdjacentEdgesToNode(int)} <br>
 *      {@link #getAdjacentEdgesToNode_arr(int)} <br>
 *      {@link #getEdgeBetweenAdjacentNodes(int, int)} <br>
 *      {@link #isEdgeAdjacentToNode(int, int)}
 *    </td>
 *</TR>
 *<TR><td> Node </td>
 *    <td><!-- Node adjac to hex -->
 *      {@link #getAdjacentNodeToHex(int)} <br>
 *      {@link #getAdjacentNodesToHex(int)}
 *    </td>
 *    <td><!-- Node adjac to edge -->
 *      {@link #getAdjacentNodeToEdge(int, int)} <br>
 *      {@link #getAdjacentNodesToEdge(int)} <br>
 *      {@link #getAdjacentNodesToEdge_arr(int)} <br>
 *      {@link #getAdjacentNodeFarEndOfEdge(int, int)} <br>
 *      {@link #getNodeBetweenAdjacentEdges(int, int)}
 *    </td>
 *    <td><!-- Node adjac to node -->
 *      {@link #getAdjacentNodeToNode(int, int)} <br>
 *      {@link #getAdjacentNodeToNode2Away(int, int)} <br>
 *      {@link #getAdjacentNodesToNode(int)} <br>
 *      {@link #getAdjacentNodesToNode_arr(int)} <br>
 *      {@link #isNodeAdjacentToNode(int, int)} <br>
 *      {@link #isNode2AwayFromNode(int, int)}
 *    </td>
 *</TR>
 *<TR><td>Other methods:</td> <td> Hex </td><td> Edge </td><td> Node </td></TR>
 *<TR valign=top><td>&nbsp;</td>
 *    <td><!-- hex -->
 *      {@link #isHexOnLand(int)} <br>
 *      {@link #isHexOnWater(int)} <br>
 *      {@link #getNumberOnHexFromCoord(int)} <br>
 *      {@link #getNumberOnHexFromNumber(int)} <br>
 *      {@link #getHexTypeFromCoord(int)} <br>
 *      {@link #getHexTypeFromNumber(int)} <br>
 *      {@link #getHexNumFromCoord(int)} <br>
 *      {@link #getRobberHex()} <br>
 *      {@link #getPirateHex()} <br>
 *      {@link #getPreviousRobberHex()} <br>
 *      {@link #getPreviousPirateHex()} <br>
 *      {@link #getHexLayout()} <br>
 *      {@link #getLandHexLayout()} <br>
 *      {@link #getLandHexCoords()} <br>
 *      {@link #getLandHexCoordsSet()} <br>
 *      {@link #isHexInLandAreas(int, int[])}
 *    </td>
 *    <td><!-- edge -->
 *      {@link #isEdgeInBounds(int)} <br>
 *      {@link #isEdgeCoastline(int)} <br>
 *      {@link #roadAtEdge(int)} <br>
 *      {@link #getPortsEdges()}
 *    </td>
 *    <td><!-- node -->
 *      {@link #isNodeInBounds(int)} <br>
 *      {@link #isNodeOnLand(int)} <br>
 *      {@link #settlementAtNode(int)} <br>
 *      {@link #getPortTypeFromNodeCoord(int)} <br>
 *      {@link #getNodeLandArea(int)} <br>
 *      {@link #isNodeInLandAreas(int, int[])} <br>
 *      {@link #getLandAreasLegalNodes()}
 *    </td>
 *</TR>
 *</table>
 *  Some of these geometry methods are specific to {@link SOCBoardLarge}
 *  and don't appear in the parent {@link SOCBoard}.
 *<P>
 * <h4> Coordinate System: </h4>
 *
 * See <tt>src/docs/hexcoord-sea.png</tt>
 *<P>
 * Unlike earlier encodings, here the "hex number" ("ID") is not an index into a dense array
 * of land hexes.  Thus it's not efficient to iterate through all hex numbers. <br>
 * Instead: Hex ID = (r &lt;&lt; 8) | c   // 2 bytes: 0xRRCC
 *<P>
 * The coordinate system is a square grid of rows and columns, different from previous encodings:
 *<P>
 * <b>Hexes</b> (represented as coordinate of their centers),
 * <b>nodes</b> (corners of hexes; where settlements/cities are placed),
 * and <b>edges</b> (between nodes; where roads are placed),
 * share the same grid of coordinates.
 * Each hex is 2 units wide and 2 tall, with vertical sides (west,edge edges)
 * and sloped tops and bottoms (NW, NE, SW, SE edges).
 *<P>
 * Coordinates start at the upper-left and continue to the right and down.
 * The first few rows of hexes are: <pre>
 *    (1,2)  (1,4)  (1,6) ..
 * (3,1) (3,3)  (3,5)  (3,7) ..
 *    (5,2)  (5,4)  (5,6) ..
 * (7,1) (7,3)  (7,5)  (7,7) ..
 *    (9,2)  (9,4)  (9,6) .. </pre>
 * All water and land hexes are within the coordinates.
 * Rows increase going north to south, Columns increase west to east.
 *<P>
 * Vertical edge coordinates are at the edge's center
 * (between two hex coordinates, to the west and east of the edge).
 * Otherwise, edges get the coordinate of the node at their western end.
 *<P>
 * The first few rows of nodes are: <pre>
 *       (0,2)  (0,4)  (0,6) ..
 *   (0,1)  (0,3)  (0,5) ..
 *
 *   (2,1)  (2,3)  (2,5) ..
 *(2,0)  (2,2)  (2,4)  (2,6) ..
 *
 *(4,0)  (4,2)  (4,4)  (4,6) ..
 *   (4,1)  (4,3)  (4,5) ..
 *
 *   (6,1)  (6,3)  (6,5) ..
 *(6,0)  (6,2)  (6,4)  (6,6) .. </pre>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCBoardLarge extends SOCBoard
{
    /**
     * This board encoding {@link SOCBoard#BOARD_ENCODING_LARGE}
     * was introduced in version 2.0.00 (2000)
     */
    public static final int VERSION_FOR_ENCODING_LARGE = 2000;

    /**
     * Hex type for the Gold Hex, where the adjacent players
     * choose their resource(s) every roll.
     *<P>
     * There is no 2-for-1 port (unlike {@link SOCBoard#SHEEP_PORT},
     * {@link SOCBoard#WOOD_PORT}, etc) for this hex type.
     * Gold is not a resource.
     *<P>
     * The numeric value (7) for <tt>GOLD_HEX</tt> is the same as
     * the v1/v2 encoding's {@link SOCBoard#MISC_PORT_HEX}, but the
     * ports aren't encoded as hexes for this encoding, so there is no ambiguity
     * as long as callers of {@link #getHexTypeFromCoord(int)}
     * check the board encoding format.
     */
    public static final int GOLD_HEX = 7;

    /**
     * Hex type for the Fog Hex, with actual land type revealed when roads are placed.
     * Used with some scenarios (see {@link SOCGameOption#K_SC_FOG}).
     * Bots should treat this as {@link SOCBoard#DESERT_HEX DESERT_HEX} until revealed.
     *<P>
     * The numeric value (8) for <tt>FOG_HEX</tt> is the same as
     * the v1/v2 encoding's {@link SOCBoard#CLAY_PORT_HEX}, but the
     * ports aren't encoded as hexes for this encoding, so there is no ambiguity
     * as long as callers of {@link #getHexTypeFromCoord(int)}
     * check the board encoding format.
     *
     * @see #revealFogHiddenHex(int)
     */
    public static final int FOG_HEX = 8;

    /**
     * Maximum land hex type (== {@link #FOG_HEX}) for this encoding.
     * If you add a hex type, search for this and for FOG_HEX for likely changes.
     * Be sure to also update the client's SOCBoardPanel.loadImages.
     */
    private static final int MAX_LAND_HEX_LG = FOG_HEX;

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
     * For {@link #getAdjacentEdgesToHex(int)}, the offset to add to the hex
     * coordinate to get all adjacent edge coords, starting at
     * index 0 at the top (northeastern edge of hex) and going clockwise.
     *<P>
     * For each direction, array of adds to the coordinate to change the row & column.
     * The row delta in hex is +-0xRR00, the column is small (+-1) so doesn't need hex format.
     */
    private final static int[][] A_EDGE2HEX = {
        { -0x100,  0 }, { 0x0, +1 }, { +0x100,  0 },  // NE, E, SE
        { +0x100, -1 }, { 0x0, -1 }, { -0x100, -1 }   // SW, W, NW
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
     * Hex layout: water/land resource types.
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
     * Hexes obscured by {@link #FOG_HEX}, if any, are stored in {@link #fogHiddenHexes} (server only).
     * Because of bit shifts there, please don't use the top 8 bits of <tt>hexLayoutLg</tt>.
     *<P>
     * Key to the hexLayoutLg[][] values:
       <pre>
       0 : water   {@link #WATER_HEX}
       1 : clay    {@link #CLAY_HEX}
       2 : ore     {@link #ORE_HEX}
       3 : sheep   {@link #SHEEP_HEX}
       4 : wheat   {@link #WHEAT_HEX}
       5 : wood    {@link #WOOD_HEX}
       6 : desert  {@link #DESERT_HEX}
       7 : gold    {@link #GOLD_HEX} (see its javadoc for rule)
       8 : fog     {@link #FOG_HEX}  (see its javadoc for rule)  also: {@link #MAX_LAND_HEX_LG}
       </pre>
     *<P>
     * @see SOCBoard#portsLayout
     */
    private int[][] hexLayoutLg;

    /**
     * The set of land hex coordinates within {@link #hexLayoutLg},
     * as returned by {@link #getLandHexCoords()}, or <tt>null</tt>.
     * That method fills it from {@link #landHexLayout}.  If the board
     * layout changes, this field again becomes <tt>null</tt> until the
     * next call to {@link #getLandHexCoords()}.
     */
    private int[] cachedGetLandHexCoords;

    /**
     * The set of land hex coordinates within {@link #hexLayoutLg}.
     * Sent from server to client, along with the land hex types / dice numbers,
     * via {@link #getLandHexLayout()} / {@link #setLandHexLayout(int[])}.
     */
    private HashSet<Integer> landHexLayout;

    /**
     * When the board has multiple "land areas" (groups of islands),
     * this array holds each land area's nodes for settlements/cities.
     * <tt>null</tt> otherwise.
     * Each index holds the nodes for that land area number.
     * Index 0 is unused (<tt>null</tt>).
     *<P>
     * The multiple land areas are used to restrict initial placement,
     * or for other purposes during the game.
     * If the players must start in a certain land area,
     * {@link #startingLandArea} != 0, and
     * <tt>landAreasLegalNodes[{@link #startingLandArea}]</tt>
     * is also the players' potential settlement nodes.
     *<P>
     * The set {@link SOCBoard#nodesOnLand} contains all nodes of all land areas.
     */
    private HashSet<Integer>[] landAreasLegalNodes;

    /**
     * When players must start the game in a certain land area,
     * the starting land area number; also its index in
     * {@link #landAreasLegalNodes}, because that set of
     * legal nodes is also the players' potential settlement nodes.
     * 0 if players can start anywhere and/or
     * {@link #landAreasLegalNodes} == <tt>null</tt>.
     *<P>
     * The startingLandArea and {@link #landAreasLegalNodes} are sent
     * from the server to client as part of a <tt>POTENTIALSETTLEMENTS</tt> message.
     */
    private int startingLandArea;

    /**
     * The legal set of land edge coordinates to build roads,
     * based on {@link #nodesOnLand}.
     * Calculated in {@link #makeNewBoard_makeLegalRoadsFromLandNodes()},
     * after {@link #nodesOnLand} is filled by
     * {@link #makeNewBoard_fillNodesOnLandFromHexes(int[], int, int, int)}.
     * Used by {@link #initPlayerLegalRoads()}.
     */
    private HashSet<Integer> legalRoadEdges;

    /**
     * The legal set of water/coastline edge coordinates to build ships,
     * based on {@link #hexLayoutLg}.
     * Calculated in {@link #makeNewBoard_makeLegalShipEdges()},
     * after {@link #hexLayoutLg} is filled by
     * {@link #makeNewBoard_fillNodesOnLandFromHexes(int[], int, int, int)}.
     * Used by {@link #initPlayerLegalShips()}.
     */
    private HashSet<Integer> legalShipEdges;

    /**
     * Dice number from hex coordinate.
     * Order: [row][column].
     * For land hexes, <tt>numberLayoutLg</tt>[r][c] is the dice number on {@link #hexLayoutLg}[r][c].
     * One element per water, land, or port hex; non-land hexes are 0.
     * Desert and fog hexes are -1, although {@link #getNumberOnHexFromNumber(int)} returns 0 for them.
     * Hex dice numbers obscured by {@link #FOG_HEX}, if any, are stored in {@link #fogHiddenHexes} (server only).
     * Because of bit shifts there, <tt>numberLayoutLg</tt> values must stay within the range -1 to 254.
     */
    private int[][] numberLayoutLg;

    /**
     * Actual land hex types and dice numbers hidden under {@link #FOG_HEX}.
     * Key is the hex coordinate; value is
     * <tt>({@link #hexLayoutLg}[coord] &lt;&lt; 8) | ({@link #numberLayoutLg}[coord] & 0xFF)</tt>.
     *<P>
     * Filled at server only; the client doesn't know what's under the fog until hexes are revealed.
     * @see #makeNewBoard_hideHexesInFog(int[])
     * @see #revealFogHiddenHex(int)
     */
    private HashMap<Integer, Integer> fogHiddenHexes;

    /**
     * Land area numbers from which the player is excluded and cannot place settlements, or null.
     * Used in some game scenarios.
     */
    private int[] playerExcludedLandAreas;

    /**
     * Land areas numbers from which the robber is excluded and cannot be placed, or null.
     * Used in some game scenarios.
     */
    private int[] robberExcludedLandAreas;

    /**
     * This board layout's number of ports;
     * 0 if {@link #makeNewBoard(Hashtable)} hasn't been called yet.
     * Port types, edges and facings are all stored in {@link SOCBoard#portsLayout}.
     */
    private int portsCount;

    /**
     * the hex coordinate that the pirate is in, or 0; placed in {@link #makeNewBoard(Hashtable)}.
     * Once the pirate is placed on the board, it cannot be removed (cannot become 0 again).
     */
    private int pirateHex;

    /**
     * the previous hex coordinate that the pirate is in; 0 unless
     * {@link #setPirateHex(int, boolean) setPirateHex(rh, true)} was called.
     */
    private int prevPirateHex;

    /**
     * Create a new Settlers of Catan Board, with the v3 encoding.
     * @param gameOpts  if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     * @param maxPlayers Maximum players; must be 4 or 6
     * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
     */
    public SOCBoardLarge(Hashtable<String,SOCGameOption> gameOpts, int maxPlayers)
            throws IllegalArgumentException
    {
        super(BOARD_ENCODING_LARGE, MAX_LAND_HEX_LG);
        if ((maxPlayers != 4) && (maxPlayers != 6))
            throw new IllegalArgumentException("maxPlayers: " + maxPlayers);
        // TODO maxPlayers 6 not yet supported in our board layout for "PLL"

        setBoardBounds(BOARDWIDTH_LARGE, BOARDHEIGHT_LARGE);

        hexLayoutLg = new int[BOARDHEIGHT_LARGE+1][BOARDWIDTH_LARGE+1];
        numberLayoutLg = new int[BOARDHEIGHT_LARGE+1][BOARDWIDTH_LARGE+1];
        landHexLayout = new HashSet<Integer>();
        fogHiddenHexes = new HashMap<Integer, Integer>();
        legalRoadEdges = new HashSet<Integer>();
        legalShipEdges = new HashSet<Integer>();

        // Assume 1 Land Area, unless or until makeNewBoard says otherwise
        landAreasLegalNodes = null;
        startingLandArea = 0;

        // Only odd-numbered rows are valid,
        // but we fill all rows here just in case.
        for (int r = 0; r <= boardHeight; ++r)
        {
            Arrays.fill(hexLayoutLg[r], WATER_HEX);
            Arrays.fill(numberLayoutLg[r], 0);
        }

        portsCount = 0;
        pirateHex = 0;
        prevPirateHex = 0;
    }

    // TODO hexLayoutLg, numberLayoutLg will only ever use the odd row numbers

    // Not valid if arrays are 2D:
    //   getHexLayout(), getNumberLayout(), setHexLayout(), setNumberLayout()
    //   Consider get/set layouts as a 1D array: r, c, contents. Only include odd-numbered rows.

    // TODO unlike roads, is there ever a time when sea edges are _not_ legal?
    //  (assuming water hexes on one or both sides of the edge)


    ////////////////////////////////////////////
    //
    // Make New Board
    //


    /**
     * Shuffle the hex tiles and layout a board.
     * This is called at server, but not at client;
     * client instead calls methods such as {@link #setLandHexLayout(int[])}
     * and {@link #setLegalAndPotentialSettlements(Collection, int, HashSet[])}.
     * @param opts {@link SOCGameOption Game options}, which may affect
     *          tile placement on board, or null.  <tt>opts</tt> must be
     *          the same as passed to constructor, and thus give the same size and layout
     *          (same {@link #getBoardEncodingFormat()}).
     */
    @Override
    public void makeNewBoard(Hashtable<String, SOCGameOption> opts)
    {
        SOCGameOption opt_breakClumps = (opts != null ? opts.get("BC") : null);

        // shuffle and place the land hexes, numbers, and robber:
        // sets robberHex, contents of hexLayout[] and numberLayout[].
        // Adds to landHexLayout and nodesOnLand.
        // Clears cachedGetlandHexCoords.
        // Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports

        landAreasLegalNodes = new HashSet[5];  // hardcoded max number of land areas
            // TODO revisit, un-hardcode, when we have multiple scenarios

        // - Mainland:
        makeNewBoard_placeHexes
            (makeNewBoard_landHexTypes_v1, LANDHEX_DICEPATH_MAINLAND, makeNewBoard_diceNums_v1, 1, opt_breakClumps);
        // - Outlying islands:
        makeNewBoard_placeHexes
            (LANDHEX_TYPE_ISLANDS, LANDHEX_COORD_ISLANDS_ALL, LANDHEX_DICENUM_ISLANDS, LANDHEX_LANDAREA_RANGES_ISLANDS, null);
        // - Players must start on mainland
        startingLandArea = 1;

        // Set up legalRoadEdges:
        makeNewBoard_makeLegalRoadsFromLandNodes();
        makeNewBoard_makeLegalShipEdges();

        // consistency-check land areas
        if (landAreasLegalNodes != null)
        {
            for (int i = 1; i < landAreasLegalNodes.length; ++i)
                if (landAreasLegalNodes[i] == null)
                    throw new IllegalStateException("inconsistent landAreasLegalNodes: idx " + i);
        }

        // Hide some land hexes behind fog, if the scenario does that
        {
            final SOCGameOption opt_scenFog = (opts != null ? opts.get(SOCGameOption.K_SC_FOG) : null);
            if ((opt_scenFog != null) && opt_scenFog.getBoolValue())
                makeNewBoard_hideHexesInFog(LANDHEX_COORD_MAINLAND_FOG);
        }

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
        nodeIDtoPortType = new Hashtable<Integer, Integer>();

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
            // portsLayout[i] is set already, from portTypes_main[i]
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
            // portsLayout[L+i] is set already, from portTypes_islands[i]
            portsLayout[L + i + portsCount] = edge;
            portsLayout[L + i + (2 * portsCount)] = facing;
        }
    }

    /**
     * For {@link #makeNewBoard(Hashtable)}, place the land hexes, number, and robber,
     * after shuffling landHexType[].
     * Sets robberHex, contents of hexLayoutLg[] and numberLayoutLg[].
     * Adds to {@link #landHexLayout} and {@link SOCBoard#nodesOnLand}.
     * Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
     * (for land hex resource types).
     * If <tt>landAreaNumber</tt> != 0, also adds to {@link #landAreasLegalNodes}.
     * Called from {@link #makeNewBoard(Hashtable)} at server only; client has its board layout sent from the server.
     *<P>
     * This method does not clear out {@link #hexLayoutLg} or {@link #numberLayoutLg}
     * before it starts placement.  You can call it multiple times to set up multiple
     * areas of land hexes: Call once for each land area.
     *<P>
     * This method clears {@link #cachedGetLandHexCoords} to <tt>null</tt>.
     *
     * @param landHexType  Resource type to place into {@link #hexLayoutLg} for each land hex; will be shuffled.
     *                    Values are {@link #CLAY_HEX}, {@link #DESERT_HEX}, etc.
     *                    There should be no {@link #FOG_HEX} in here; land hexes are hidden by fog later.
     * @param numPath  Coordinates within {@link #hexLayoutLg} (also within {@link #numberLayoutLg}) for each land hex;
     *                    same array length as <tt>landHexType[]</tt>
     * @param number   Numbers to place into {@link #numberLayoutLg} for each land hex;
     *                    array length is <tt>landHexType[].length</tt> minus 1 for each desert in <tt>landHexType[]</tt>
     * @param landAreaNumber  0 unless there will be more than 1 Land Area (group of islands).
     *                    If != 0, updates {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt>
     *                    with the same nodes added to {@link SOCBoard#nodesOnLand}.
     * @param optBC    Game option "BC" from the options for this board, or <tt>null</tt>.
     * @throws IllegalStateException  if <tt>landAreaNumber</tt> != 0 and either
     *             {@link #landAreasLegalNodes} == null, or not long enough, or
     *             {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt> != null
     *             because the land area has already been placed.
     * @throws IllegalArgumentException  if <tt>landHexType</tt> contains {@link #FOG_HEX}.
     * @see #makeNewBoard_placeHexes(int[], int[], int[], int[], SOCGameOption)
     */
    private final void makeNewBoard_placeHexes
        (int[] landHexType, final int[] numPath, final int[] number, final int landAreaNumber, SOCGameOption optBC)
        throws IllegalStateException, IllegalArgumentException
    {
        final int[] pathRanges = { landAreaNumber, numPath.length };  // 1 range, uses all of numPath
        makeNewBoard_placeHexes
            (landHexType, numPath, number, pathRanges, optBC);
    }

    /**
     * For {@link #makeNewBoard(Hashtable)}, place the land hexes, number, and robber
     * for multiple land areas, after shuffling their common landHexType[].
     * Sets robberHex, contents of hexLayoutLg[] and numberLayoutLg[].
     * Adds to {@link #landHexLayout} and {@link SOCBoard#nodesOnLand}.
     * Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
     * (for land hex resource types).
     * If Land Area Number != 0, also adds to {@link #landAreasLegalNodes}.
     * Called from {@link #makeNewBoard(Hashtable)} at server only; client has its board layout sent from the server.
     *<P>
     * This method does not clear out {@link #hexLayoutLg} or {@link #numberLayoutLg}
     * before it starts placement.  You can call it multiple times to set up multiple
     * areas of land hexes: Call once for each group of Land Areas which shares a numPath and landHexType.
     * For each land area, it updates {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt>
     * with the same nodes added to {@link SOCBoard#nodesOnLand}.
     *<P>
     * If part of the board will be hidden by {@link #FOG_HEX}, wait before doing that:
     * This method must shuffle and place the unobscured land hexes.
     * After the last call to <tt>makeNewBoard_placeHexes</tt>, call
     * {@link #makeNewBoard_hideHexesInFog(int[])}.
     *<P>
     * This method clears {@link #cachedGetLandHexCoords} to <tt>null</tt>.
     *
     * @param landHexType  Resource type to place into {@link #hexLayoutLg} for each land hex; will be shuffled.
     *                    Values are {@link #CLAY_HEX}, {@link #DESERT_HEX}, etc.
     *                    There should be no {@link #FOG_HEX} in here; land hexes are hidden by fog later.
     * @param numPath  Coordinates within {@link #hexLayoutLg} (also within {@link #numberLayoutLg}) for each land hex;
     *                    same array length as <tt>landHexType[]</tt>.
     *                    <BR> <tt>landAreaPathRanges[]</tt> tells how to split this array of land hex coordindates
     *                    into multiple Land Areas.
     * @param number   Numbers to place into {@link #numberLayoutLg} for each land hex;
     *                    array length is <tt>landHexType[].length</tt> minus 1 for each desert in <tt>landHexType[]</tt>
     * @param landAreaPathRanges  <tt>numPath[]</tt>'s Land Area Numbers, and the size of each land area.
     *                    Array length is 2 x the count of land areas included.
     *                    Index 0 is the first landAreaNumber, index 1 is the length of that land area (number of hexes).
     *                    Index 2 is the next landAreaNumber, index 3 is that one's length, etc.
     *                    The sum of those lengths must equal <tt>numPath.length</tt>.
     * @param optBC    Game option "BC" from the options for this board, or <tt>null</tt>.
     * @throws IllegalStateException  if land area number != 0 and either
     *             {@link #landAreasLegalNodes} == null, or not long enough, or any
     *             {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt> != null
     *             because the land area has already been placed.
     * @throws IllegalArgumentException if <tt>landAreaPathRanges</tt> is null or has an uneven length,
     *             or if the total length of its land areas != <tt>numPath.length</tt>,
     *             or if <tt>landHexType</tt> contains {@link #FOG_HEX}.
     * @see #makeNewBoard_placeHexes(int[], int[], int[], int, SOCGameOption)
     */
    private final void makeNewBoard_placeHexes
        (int[] landHexType, final int[] numPath, final int[] number, final int[] landAreaPathRanges, SOCGameOption optBC)
        throws IllegalStateException, IllegalArgumentException
    {
        final boolean checkClumps = (optBC != null) && optBC.getBoolValue();
        final int clumpSize = checkClumps ? optBC.getIntValue() : 0;
        boolean clumpsNotOK = checkClumps;

        // Validate landAreaPathRanges lengths within numPath
        if ((landAreaPathRanges == null) || ((landAreaPathRanges.length % 2) != 0))
            throw new IllegalArgumentException("landAreaPathRanges: uneven length");
        if (landAreaPathRanges.length <= 2)
        {
            final int L = landAreaPathRanges[1];
            if (L != numPath.length)
                throw new IllegalArgumentException
                    ("landAreaPathRanges: landarea " + landAreaPathRanges[0]
                     + ": range length " + L + " should be " + numPath.length);
        } else {
            int L = 0, i;
            for (i = 1; i < landAreaPathRanges.length; i += 2)
            {
                L += landAreaPathRanges[i];
                if (L > numPath.length)
                    throw new IllegalArgumentException
                        ("landAreaPathRanges: landarea " + landAreaPathRanges[i-1]
                          + ": total range length " + L + " should be " + numPath.length);
            }
            if (L < numPath.length)
                throw new IllegalArgumentException
                    ("landAreaPathRanges: landarea " + landAreaPathRanges[i-3]
                      + ": total range length " + L + " should be " + numPath.length);
        }

        // Shuffle, place, then check layout for clumps:

        if (numPath.length > 0)
            cachedGetLandHexCoords = null;  // invalidate the previous cached set

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
                else if (landHexType[i] == FOG_HEX)
                {
                    throw new IllegalArgumentException("landHexType can't contain FOG_HEX");
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
                // Check the newly placed land area(s) for clumps;
                // ones placed in previous method calls are ignored
                Vector<Integer> unvisited = new Vector<Integer>();  // contains each land hex's coordinate
                for (int i = 0; i < landHexType.length; ++i)
                    unvisited.addElement(new Integer(numPath[i]));

                clumpsNotOK = makeNewBoard_checkLandHexResourceClumps(unvisited, clumpSize);
            }

        } while (clumpsNotOK);

        // Now that we know this layout is okay,
        // add the hex coordinates to landHexLayout,
        // and the hexes' nodes to nodesOnLand.
        // Throws IllegalStateException if landAreaNumber incorrect
        // vs size/contents of landAreasLegalNodes
        // from previously placed land areas.

        for (int i = 0; i < landHexType.length; i++)
            landHexLayout.add(new Integer(numPath[i]));
        for (int i = 0, hexIdx = 0; i < landAreaPathRanges.length; i += 2)
        {
            final int landAreaNumber = landAreaPathRanges[i],
                      landAreaLength = landAreaPathRanges[i + 1],
                      nextHexIdx = hexIdx + landAreaLength;
            makeNewBoard_fillNodesOnLandFromHexes
                (numPath, hexIdx, nextHexIdx, landAreaNumber);
            hexIdx = nextHexIdx;
        }

    }  // makeNewBoard_placeHexes

    /**
     * Calculate the board's legal settlement/city nodes, based on land hexes.
     * All corners of these hexes are legal for settlements/cities.
     * Called from {@link #makeNewBoard_placeHexes(int[], int[], int[], int, SOCGameOption)}.
     * Can use all or part of a <tt>landHexCoords</tt> array.
     *<P>
     * Iterative: Can call multiple times, giving different hexes each time.
     * Each call will add those hexes to {@link #nodesOnLand}.
     * If <tt>landAreaNumber</tt> != 0, also adds them to {@link #landAreasLegalNodes}.
     * Call this method once for each land area.
     *<P>
     * Before the first call, clear <tt>nodesOnLand</tt>.
     *
     * @param landHexCoords  Coordinates of a contiguous group of land hexes.
     *                    If <tt>startIdx</tt> and <tt>pastEndIdx</tt> partially use this array,
     *                    only that part needs to be contiguous, the rest of <tt>landHexCoords</tt> is ignored.
     * @param startIdx    First index to use within <tt>landHexCoords[]</tt>; 0 if using the entire array
     * @param pastEndIdx  Just past the last index to use within <tt>landHexCoords[]</tt>;
     *                    If this call uses landHexCoords up through its end, this is <tt>landHexCoords.length</tt>
     * @param landAreaNumber  0 unless there will be more than 1 Land Area (groups of islands).
     *                    If != 0, updates {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt>
     *                    with the same nodes added to {@link SOCBoard#nodesOnLand}.
     * @see #makeNewBoard_makeLegalRoadsFromLandNodes()
     * @see #makeNewBoard_makeLegalShipEdges()
     * @throws IllegalStateException  if <tt>landAreaNumber</tt> != 0 and either
     *             {@link #landAreasLegalNodes} == null, or not long enough, or
     *             {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt> != null
     */
    private void makeNewBoard_fillNodesOnLandFromHexes
        (final int landHexCoords[], final int startIdx, final int pastEndIdx, final int landAreaNumber)
        throws IllegalStateException
    {
        if (landAreaNumber != 0)
        {
            if ((landAreasLegalNodes == null)
                || (landAreaNumber >= landAreasLegalNodes.length)
                || (landAreasLegalNodes[landAreaNumber] != null))
                throw new IllegalStateException();
            landAreasLegalNodes[landAreaNumber] = new HashSet<Integer>();
        }

        for (int i = startIdx; i < pastEndIdx; ++i)
        {
            final int[] nodes = getAdjacentNodesToHex(landHexCoords[i]);
            for (int j = 0; j < 6; ++j)
            {
                final Integer ni = new Integer(nodes[j]);
                nodesOnLand.add(ni);
                if (landAreaNumber != 0)
                    landAreasLegalNodes[landAreaNumber].add(ni);
            }
            // it's ok to add if this set already contains an Integer equal to nodes[j].
        }

    }  // makeNewBoard_makeLegalNodesFromHexes

    /**
     * Once the legal settlement/city nodes ({@link #nodesOnLand})
     * are established from land hexes, fill {@link #legalRoadEdges}.
     * Not iterative; clears all previous legal roads.
     * Call this only after the very last call to
     * {@link #makeNewBoard_fillNodesOnLandFromHexes(int[], int, int, int)}.
     */
    private void makeNewBoard_makeLegalRoadsFromLandNodes()
    {
        // About corners/concave parts:
        //   Set of the valid nodes will contain both ends of the edge;
        //   anything concave across a sea would be missing at least 1 node, in the water along the way.

        // Go from nodesOnLand, iterate all nodes:

        legalRoadEdges.clear();

        for (Integer nodeVal : nodesOnLand)
        {
            final int node = nodeVal.intValue();
            for (int dir = 0; dir < 3; ++dir)
            {
                int nodeAdjac = getAdjacentNodeToNode(node, dir);
                if (nodesOnLand.contains(new Integer(nodeAdjac)))
                {
                    final int edge = getAdjacentEdgeToNode(node, dir);

                    // Ensure it doesn't cross water
                    // by requiring land on at least one side of the edge
                    boolean hasLand = false;
                    final int[] hexes = getAdjacentHexesToEdge_arr(edge);
                    for (int i = 0; i <= 1; ++i)
                    {
                        if (hexes[i] != 0)
                        {
                            final int htype = getHexTypeFromCoord(hexes[i]);
                            if ((htype != WATER_HEX) && (htype <= MAX_LAND_HEX_LG))
                            {
                                hasLand = true;
                                break;
                            }
                        }
                    }

                    // OK to add
                    if (hasLand)
                        legalRoadEdges.add(new Integer(edge));
                        // it's ok to add if this set already contains an Integer equal to that edge.
                }
            }
        }

    }  // makeNewBoard_makeLegalRoadsFromNodes

    /**
     * Once the legal settlement/city nodes ({@link #nodesOnLand})
     * are established from land hexes, fill {@link #legalShipEdges}.
     * Contains all 6 edges of each water hex.
     * Contains all coastal edges of each land hex at the edges of the board.
     *<P>
     * Not iterative; clears all previous legal ship edges.
     * Call this only after the very last call to
     * {@link #makeNewBoard_fillNodesOnLandFromHexes(int[], int, int, int)}.
     */
    private void makeNewBoard_makeLegalShipEdges()
    {
        // All 6 edges of each water hex.
        // All coastal edges of each land hex at the edges of the board.
        // (Needed because there's no water hex next to it)

        legalShipEdges.clear();

        for (int r = 1; r < boardHeight; r += 2)
        {
            final int rshift = (r << 8);
            int c;
            if (((r/2) % 2) == 1)
            {
                c = 1;  // odd hex rows start at 1
            } else {
                c = 2;  // top row, even rows start at 2
            }
            for (; c < boardWidth; c += 2)
            {
                if (hexLayoutLg[r][c] == WATER_HEX)
                {
                    final int[] sides = getAdjacentEdgesToHex(rshift | c);
                    for (int i = 0; i < 6; ++i)
                        legalShipEdges.add(new Integer(sides[i]));
                } else {
                    // Land hex; check if it's at the
                    // edge of the board
                    if ((r == 1) || (r == (boardHeight-1))
                        || (c <= 2) || (c >= (boardWidth-2)))
                    {
                        final int[] sides = getAdjacentEdgesToHex(rshift | c);
                        for (int i = 0; i < 6; ++i)
                            if (isEdgeCoastline(sides[i]))
                                legalShipEdges.add(new Integer(sides[i]));
                    }
                }

            }
        }

    }  // makeNewBoard_makeLegalShipEdges

    /**
     * For {@link #makeNewBoard(Hashtable)}, hide these hexes under {@link #FOG_HEX} to be revealed later.
     * The hexes will be stored in {@link #fogHiddenHexes}; their {@link #hexLayoutLg} and {@link #numberLayoutLg}
     * elements will be set to {@link #FOG_HEX} and -1.
     *<P>
     * To simplify the bot, client, and network, hexes can be hidden only during makeNewBoard,
     * before the board layout is made and sent to the client.
     * 
     * @param hexCoords  Coordinates of each land hex to hide in the fog
     * @throws IllegalStateException  if any hexCoord is already {@link #FOG_HEX} within {@link #hexLayoutLg}
     * @see #revealFogHiddenHex(int)
     */
    protected void makeNewBoard_hideHexesInFog(final int[] hexCoords)
        throws IllegalStateException
    {
        for (int i = 0; i < hexCoords.length; ++i)
        {
            final int hexCoord = hexCoords[i];
            final int r = hexCoord >> 8,
                      c = hexCoord & 0xFF;
            final int hex = hexLayoutLg[r][c];
            if (hex == FOG_HEX)
                throw new IllegalStateException("Already fog: 0x" + Integer.toHexString(hexCoord));

            fogHiddenHexes.put(new Integer(hexCoord), (hex << 8) | (numberLayoutLg[r][c] & 0xFF));
            hexLayoutLg[r][c] = FOG_HEX;
            numberLayoutLg[r][c] = -1;
        }
    }

    /**
     * Reveal one land hex hidden by fog (server-side call).
     * Gets the hidden hex type and dice number, calls {@link #revealFogHiddenHex(int, int, int)}.
     * @param hexCoord  Coordinate of the land hex to reveal
     * @return The revealed hex type, same value as {@link #getHexTypeFromCoord(int) getHexTypeFromCoord(hexCoord)}
     * @throws IllegalArgumentException if <tt>hexCoord</tt> isn't currently a {@link #FOG_HEX}  
     */
    public int revealFogHiddenHex(final int hexCoord)
        throws IllegalArgumentException
    {
        final Integer encoded = fogHiddenHexes.remove(Integer.valueOf(hexCoord));
        if (encoded == null)
            throw new IllegalArgumentException("Not fog: 0x" + Integer.toHexString(hexCoord));

        final int hex = encoded >> 8;
        int diceNum = encoded & 0xFF;
        if (diceNum == 0xFF)
            diceNum = -1;

        revealFogHiddenHex(hexCoord, hex, diceNum);
        return hex;
    }

    /**
     * Reveal one land hex hidden by fog (client-side call).
     * @param hexCoord  Coordinate of the land hex to reveal
     * @param hexType   Revealed hex type, same value as {@link #getHexTypeFromCoord(int)}
     * @param diceNum   Revealed hex dice number, same value as {@link #getNumberOnHexFromCoord(int)}, or 0
     * @throws IllegalArgumentException if <tt>hexCoord</tt> isn't currently a {@link #FOG_HEX}
     * @see #revealFogHiddenHex(int)
     */
    public void revealFogHiddenHex(final int hexCoord, final int hexType, int diceNum)
        throws IllegalArgumentException
    {
        final int r = hexCoord >> 8,
                  c = hexCoord & 0xFF;
        if (hexLayoutLg[r][c] != FOG_HEX)
            throw new IllegalArgumentException("Not fog: 0x" + Integer.toHexString(hexCoord));

        if ((diceNum == 0) && (hexType == DESERT_HEX))
            diceNum = -1;  // internally, desert and fog hex dice numbers are stored as -1 not 0

        hexLayoutLg[r][c] = hexType;
        numberLayoutLg[r][c] = diceNum;
    }


    ////////////////////////////////////////////
    //
    // Board info getters
    //


    /**
     * Get the hex layout -- Not valid for this encoding.
     * Valid only for the v1 and v2 board encoding, not v3.
     * Always throws IllegalStateException for SOCBoardLarge.
     * Call {@link #getLandHexCoords()} instead.
     * @throws IllegalStateException since the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     * @see SOCBoard#getHexLayout()
     */
    @Override
    public int[] getHexLayout()
        throws IllegalStateException
    {
        throw new IllegalStateException();
    }

    /**
     * Set the hexLayout -- Not valid for this encoding.
     * Valid only for the v1 and v2 board encoding, not v3.
     * Always throws IllegalStateException for SOCBoardLarge.
     * Call {@link #setLandHexLayout(int[])} instead.
     * @param hl  the hex layout
     * @throws IllegalStateException since the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     * @see SOCBoard#setHexLayout(int[])
     */
    @Override
    public void setHexLayout(final int[] hl)
        throws IllegalStateException
    {
        throw new IllegalStateException();
    }

    /**
     * Get the dice-number layout of dice rolls at each hex number -- Not valid for this encoding.
     * Call {@link #getLandHexCoords()} and {@link #getNumberOnHexFromCoord(int)} instead.
     * @throws IllegalStateException since the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     * @see SOCBoard#getNumberLayout()
     */
    @Override
    public int[] getNumberLayout()
        throws IllegalStateException
    {
        throw new IllegalStateException();
    }

    /**
     * Set the number layout -- Not valid for this encoding.
     * Call {@link SOCBoardLarge#setLandHexLayout(int[])} instead.
     *
     * @param nl  the number layout, from {@link #getNumberLayout()}
     * @throws IllegalStateException since the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     * @see SOCBoard#setNumberLayout(int[])
     */
    @Override
    public void setNumberLayout(int[] nl)
        throws IllegalStateException
    {
        throw new IllegalStateException();
    }

    /**
     * Set where the pirate is.
     *
     * @param ph  the new pirate hex coordinate; must be &gt; 0, not validated beyond that
     * @param rememberPrevious  Should we remember the old pirate hex?
     * @see #getPirateHex()
     * @see #getPreviousPirateHex()
     * @see #setRobberHex(int, boolean)
     * @throws IllegalArgumentException if <tt>ph</tt> &lt;= 0
     */
    public void setPirateHex(final int ph, final boolean rememberPrevious)
        throws IllegalArgumentException
    {
        if (ph <= 0)
            throw new IllegalArgumentException();
        if (rememberPrevious)
            prevPirateHex = pirateHex;
        else
            prevPirateHex = 0;
        pirateHex = ph;
    }

    /**
     * @return coordinate where the pirate is, or 0 if not on the board
     * @see #getPreviousPirateHex()
     * @see #getRobberHex()
     */
    public int getPirateHex()
    {
        return pirateHex;
    }

    /**
     * If the pirate has been moved by calling {@link #setPirateHex(int, boolean)}
     * where <tt>rememberPrevious == true</tt>, get the previous coordinate
     * of the pirate.
     * @return hex coordinate where the pirate was, or 0
     * @see #getPirateHex()
     */
    public int getPreviousPirateHex()
    {
        return prevPirateHex;
    }

    /**
     * Get the land area numbers, if any, from which all players are excluded and cannot place settlements.
     * Used in some game scenarios.
     * @return land area numbers, or null if none
     * @see #setPlayerExcludedLandAreas(int[])
     * @see #getRobberExcludedLandAreas()
     */
    public int[] getPlayerExcludedLandAreas()
    {
        return playerExcludedLandAreas;
    }

    /**
     * Set or clear the land area numbers from which all players are excluded and cannot place settlements.
     * Used in some game scenarios.
     * @param px  Land area numbers, or null if none
     * @see #getPlayerExcludedLandAreas()
     * @see #setRobberExcludedLandAreas(int[])
     */
    public void setPlayerExcludedLandAreas(final int[] px)
    {
        playerExcludedLandAreas = px;
    }

    /**
     * Get the land area numbers, if any, from which the robber is excluded and cannot be placed.
     * Used in some game scenarios.
     * @return land area numbers, or null if none
     * @see #setRobberExcludedLandAreas(int[])
     * @see #getPlayerExcludedLandAreas()
     */
    public int[] getRobberExcludedLandAreas()
    {
        return robberExcludedLandAreas;
    }

    /**
     * Set or clear the land area numbers from which the robber is excluded and cannot be placed.
     * Used in some game scenarios.
     * @param rx  Land area numbers, or null if none
     * @see #getRobberExcludedLandAreas()
     * @see #setPlayerExcludedLandAreas(int[])
     */
    public void setRobberExcludedLandAreas(final int[] rx)
    {
        robberExcludedLandAreas = rx;
    }

    /**
     * Given a hex coordinate, return the dice-roll number on that hex
     *
     * @param hex  the coordinates for a hex
     *
     * @return the dice-roll number on that hex, or 0 if no number or not a hex coordinate
     */
    @Override
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
    @Override
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
     * Given a hex coordinate, return the hex number (index) -- Not valid for this encoding.
     * Valid only for the v1 and v2 board encoding, not v3.
     * Always throws IllegalStateException for SOCBoardLarge.
     * Hex numbers (indexes within an array of land hexes) aren't used in this encoding,
     * hex coordinates are used instead.
     * @see #getHexTypeFromCoord(int)
     * @throws IllegalStateException since the board encoding doesn't support this method
     */
    @Override
    public int getHexNumFromCoord(final int hexCoord)
        throws IllegalStateException
    {
        throw new IllegalStateException();
    }

    /**
     * Given a hex coordinate, return the type of hex.
     *<P>
     * Unlike the original {@link SOCBoard} encoding, port types are not
     * encoded in the hex layout; use {@link #getPortTypeFromNodeCoord(int)} instead.
     *<P>
     * The numeric value (7) for {@link #GOLD_HEX} is the same as
     * the v1/v2 encoding's {@link SOCBoard#MISC_PORT_HEX}, and
     * {@link #FOG_HEX} (8) is the same as {@link SOCBoard#CLAY_PORT_HEX}.
     * The ports aren't encoded that way in <tt>SOCBoardLarge</tt>, so there is no ambiguity
     * as long as callers check the board encoding format.
     *
     * @param hex  the coordinates ("ID") for a hex
     * @return the type of hex:
     *         Land in range {@link #CLAY_HEX} to {@link #WOOD_HEX},
     *         {@link #DESERT_HEX}, {@link #GOLD_HEX}, {@link #FOG_HEX},
     *         or {@link #WATER_HEX}.
     *         Invalid hex coordinates return -1.
     *
     * @see #getLandHexCoords()
     */
    @Override
    public int getHexTypeFromCoord(final int hex)
    {
        return getHexTypeFromNumber(hex);
    }

    /**
     * Given a hex number, return the type of hex.
     * In this encoding, hex numbers == hex coordinates.
     *<P>
     * Unlike the original {@link SOCBoard} encoding, port types are not
     * encoded in the hex layout; use {@link #getPortTypeFromNodeCoord(int)} instead.
     *
     * @param hex  the number of a hex, or -1 for invalid
     * @return the type of hex:
     *         Land in range {@link #CLAY_HEX} to {@link #WOOD_HEX},
     *         {@link #DESERT_HEX}, {@link #GOLD_HEX}, {@link #FOG_HEX},
     *         or {@link #WATER_HEX}.
     *         Invalid hex numbers return -1.
     *
     * @see #getHexTypeFromCoord(int)
     */
    @Override
    public int getHexTypeFromNumber(final int hex)
    {
        final int r = hex >> 8,     // retains sign bit; will handle hex == -1 as r < 0
                  c = hex & 0xFF;

        if ((r <= 0) || (c <= 0) || (r >= boardHeight) || (c >= boardWidth))
            return -1;  // out of bounds

        if (((r % 2) == 0)
            || ((c % 2) != ((r/2) % 2)))
            return -1;  // not a valid hex coordinate

        return hexLayoutLg[r][c];
    }

    /**
     * Is this edge along the coastline (land/water border)?
     * @param edge  Edge coordinate, not checked for validity
     * @return  true if this edge's hexes are land and water,
     *           or a land hex at the edge of the board 
     */
    public final boolean isEdgeCoastline(final int edge)
    {
        boolean hasLand = false, hasWater = false;
        final int[] hexes = getAdjacentHexesToEdge_arr(edge);

        for (int i = 0; i <= 1; ++i)
        {
            if (hexes[i] != 0)
            {
                final int htype = getHexTypeFromCoord(hexes[i]);
                if ((htype <= MAX_LAND_HEX_LG) && (htype != WATER_HEX))
                    hasLand = true;
                else
                    hasWater = true;
            } else {
                hasWater = true;  // treat off-board as water
            }
        }

        return (hasLand && hasWater);
    }

    /**
     * The hex coordinates of all land hexes.  Please treat as read-only.
     * If you add land hexes, call {@link #getLandHexCoords()} to recalculate this set.
     * @return land hex coordinates, as a set of {@link Integer}s
     * @since 2.0.00
     */
    public HashSet<Integer> getLandHexCoordsSet()
    {
        return landHexLayout;
    }

    /**
     * The hex coordinates of all land hexes.
     *<P>
     * Before v2.0.00, this was <tt>getHexLandCoords()</tt>.
     *
     * @return land hex coordinates, in no particular order, or null if none (all water).
     * @since 1.1.08
     */
    @Override
    public int[] getLandHexCoords()
    {
        final int LHL = landHexLayout.size();
        if (LHL == 0)
            return null;
        if ((cachedGetLandHexCoords != null) && (LHL == cachedGetLandHexCoords.length))
            return cachedGetLandHexCoords;

        int[] hexCoords = new int[LHL];
        int i=0;
        for (Integer hex : landHexLayout)
            hexCoords[i++] = hex.intValue();

        cachedGetLandHexCoords = hexCoords;
        return hexCoords;
    }

    /**
     * Is this the coordinate of a land hex (not water)? 
     * @param hexCoord  Hex coordinate, within the board's bounds
     * @return  True if land, false if water or not a valid hex coordinate
     * @see #isHexOnWater(int)
     */
    @Override
    public boolean isHexOnLand(final int hexCoord)
    {
        final int htype = getHexTypeFromCoord(hexCoord);
        return (htype != -1) && (htype != WATER_HEX) && (htype <= MAX_LAND_HEX_LG);
    }

    /**
     * Is this the coordinate of a water hex (not land)? 
     * @param hexCoord  Hex coordinate, within the board's bounds
     * @return  True if water, false if land or not a valid hex coordinate
     * @see #isHexOnLand(int)
     * @since 2.0.00
     */
    @Override
    public boolean isHexOnWater(final int hexCoord)
    {
        return (getHexTypeFromCoord(hexCoord) == WATER_HEX);
    }

    /**
     * Is this hex's land area in this list of land areas?
     * @param hexCoord  The hex coordinate, within the board's bounds
     * @param las  List of land area numbers, or null for an empty list
     * @return  True if any landarea in <tt>las[i]</tt> contains <tt>hexCoord</tt>;
     *    false otherwise, or if {@link #getLandAreasLegalNodes()} is <tt>null</tt>
     * @see #isHexOnLand(int)
     */
    public boolean isHexInLandAreas(final int hexCoord, final int[] las)
    {
        if ((las == null) || (landAreasLegalNodes == null))
            return false;

        // Because of how landareas are transmitted to the client,
        // we don't have a list of land area hexes, only land area nodes.
        // To contain a hex, the land area must contain all 6 of its corner nodes.

        final int[] hnodes = getAdjacentNodesToHex(hexCoord);
        final Integer hnode0 = Integer.valueOf(hnodes[0]);
        for (int a : las)
        {
            if (a >= landAreasLegalNodes.length)
                continue;  // bad argument

            final HashSet<Integer> lan = landAreasLegalNodes[a];
            if (lan == null)
                continue;  // index 0 is unused

            if (! lan.contains(hnode0))
                continue;  // missing at least 1 corner

            // check the other 5 hex corners
            boolean all = true;
            for (int i = 1; i < hnodes.length; ++i)
            {
                if (! lan.contains(Integer.valueOf(hnodes[i])))
                {
                    all = false;
                    break;
                }
            }

            if (all)
                return true;
        }

        return false;
    }

    /**
     * Is this node's land area in this list of land areas?
     * @param nodeCoord  The node's coordinate
     * @param las  List of land area numbers, or null for an empty list
     * @return  True if <tt>las</tt> contains {@link #getNodeLandArea(int) getNodeLandArea(nodeCoord)};
     *    false otherwise, or if {@link #getLandAreasLegalNodes()} is <tt>null</tt>
     */
    public boolean isNodeInLandAreas(final int nodeCoord, final int[] las)
    {
        if ((las == null) || (landAreasLegalNodes == null))
            return false;

        final Integer ncInt = Integer.valueOf(nodeCoord);
        for (int a : las)
        {
            if (a >= landAreasLegalNodes.length)
                continue;  // bad argument

            final HashSet<Integer> lan = landAreasLegalNodes[a];
            if (lan == null)
                continue;  // index 0 is unused

            if (lan.contains(ncInt))
                return true;
        }
        return false;
    }

    /**
     * Get a node's Land Area number, if applicable.
     * @param nodeCoord  the node's coordinate
     * @return  The node's land area, if any, or 0 if not found or if in water.
     *     If {@link #getLandAreasLegalNodes()} is <tt>null</tt>,
     *     always returns 1 if {@link #isNodeOnLand(int) isNodeOnLand(nodeCoord)}.
     * @see #getLandAreasLegalNodes()
     * @see #isNodeInLandAreas(int, int[])
     */
    public int getNodeLandArea(final int nodeCoord)
    {
        if (landAreasLegalNodes == null)
            return ( isNodeOnLand(nodeCoord) ? 1 : 0);

        final Integer nodeInt = Integer.valueOf(nodeCoord);
        for (int i = 1; i < landAreasLegalNodes.length; ++i)
            if (landAreasLegalNodes[i].contains(nodeInt))
                return i;

        return 0;
    }

    /**
     * Get the land hex layout, for sending from server to client.
     * Contains 3 int elements per land hex:
     * Coordinate, Hex type (resource, as in {@link #SHEEP_HEX}), Dice Number (-1 for desert).
     * @return the layout, or null if no land hexes.
     * @see #setLandHexLayout(int[])
     */
    public int[] getLandHexLayout()
    {
        final int LHL = landHexLayout.size();
        if (LHL == 0)
            return null;

        int[] lh = new int[3 * LHL];
        int i = 0;
        for (Integer hex : landHexLayout)
        {
            final int hexCoord = hex.intValue();
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
     *<P>
     * After calling this, please call
     * {@link SOCGame#setPlayersLandHexCoordinates() game.setPlayersLandHexCoordinates()}.
     * After {@link #makeNewBoard(Hashtable)} calculates the potential/legal settlements,
     * call each player's {@link SOCPlayer#setPotentialAndLegalSettlements(Collection, boolean, HashSet[])}.
     * @param  lh  the layout, or null if no land hexes, from {@link #getLandHexLayout()}
     */
    public void setLandHexLayout(final int[] lh)
    {
        // Clear the previous contents:
        landHexLayout.clear();
        nodesOnLand.clear();
        legalRoadEdges.clear();
        cachedGetLandHexCoords = null;
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
        cachedGetLandHexCoords = hcoords;
    }

    /**
     * Get the starting land area, if multiple "land areas" are used
     * and the players must start the game in a certain land area.
     * @return the starting land area number; also its index in
     *   {@link #getLandAreasLegalNodes()}.
     *   0 if players can start anywhere and/or
     *   <tt>landAreasLegalNodes == null</tt>.
     */
    public int getStartingLandArea()
    {
        return startingLandArea;
    }

    /**
     * Get the land areas' nodes, if multiple "land areas" are used.
     * For use only by SOCGame at server.
     * Please treat the returned object as read-only.
     *<P>
     * When the board has multiple "land areas" (groups of islands,
     * or subsets of islands), this array holds each land area's
     * nodes for settlements/cities.
     *<P>
     * The multiple land areas are used to restrict initial placement,
     * or for other purposes during the game.
     * If the players must start in a certain land area,
     * {@link #startingLandArea} != 0.
     *<P>
     * See also {@link #getLegalAndPotentialSettlements()}
     * which returns the starting land area's nodes, or if no starting
     * land area, all nodes of all land areas.
     *<P>
     * See also {@link #getStartingLandArea()} to
     * see if the players must start the game in a certain land area.
     *
     * @return the land areas' nodes, or <tt>null</tt> if only one land area (one group of islands).
     *     Each index holds the nodes for that land area number.
     *     Index 0 is unused.
     * @see #getNodeLandArea(int)
     */
    public HashSet<Integer>[] getLandAreasLegalNodes()
    {
        return landAreasLegalNodes;
    }

    /**
     * Get the legal and potential settlements, after {@link #makeNewBoard(Hashtable)}.
     * For use mainly by SOCGame at server.
     *<P>
     * Returns the starting land area's nodes, or if no starting
     * land area, all nodes of all land areas.
     *<P>
     * At the client, this returns an empty set if
     * {@link #setLegalAndPotentialSettlements(Collection, int, HashSet[])}
     * hasn't yet been called while the game is starting.
     *<P>
     * See also {@link #getLandAreasLegalNodes()} which returns
     * all the legal nodes when multiple "land areas" are used.
     */
    public HashSet<Integer> getLegalAndPotentialSettlements()
    {
        if ((landAreasLegalNodes == null) || (startingLandArea == 0))
            return nodesOnLand;
        else
            return landAreasLegalNodes[startingLandArea];
    }

    /**
     * Set the legal and potential settlements, and recalculate
     * the legal roads and ship edges; called at client only.
     *<P>
     * Call this only after {@link #setLandHexLayout(int[])}.
     * After calling this method, you can get the new legal road set
     * with {@link #initPlayerLegalRoads()}.
     *<P>
     * If this method hasn't yet been called, {@link #getLegalAndPotentialSettlements()}
     * returns an empty set.
     *
     * @param psNodes  The set of potential settlement node coordinates as {@link Integer}s;
     *    either a {@link HashSet} or {@link Vector}.
     *    If <tt>lan == null</tt>, this will also be used as the
     *    legal set of settlement nodes on land.
     * @param sla  The required starting Land Area number, or 0
     * @param lan If non-null, all Land Areas' legal node coordinates.
     *     Index 0 is ignored; land area numbers start at 1.
     */
    public void setLegalAndPotentialSettlements
        (final Collection<Integer> psNodes, final int sla, final HashSet<Integer>[] lan)
    {
        if (lan == null)
        {
            landAreasLegalNodes = null;
            startingLandArea = 0;

            if (psNodes instanceof HashSet)
            {
                nodesOnLand = new HashSet<Integer>(psNodes);
            } else {
                nodesOnLand.clear();
                nodesOnLand.addAll(psNodes);
            }
        }
        else
        {
            landAreasLegalNodes = lan; 
            startingLandArea = sla;

            nodesOnLand.clear();
            for (int i = 1; i < lan.length; ++i)
                nodesOnLand.addAll(lan[i]);
        }

        makeNewBoard_makeLegalRoadsFromLandNodes();
        makeNewBoard_makeLegalShipEdges();
    }

    /**
     * Create and initialize a {@link SOCPlayer}'s legalRoads set.
     *<P>
     * Because the v3 board layout varies:
     * At the server, call this after {@link #makeNewBoard(Hashtable)}.
     * At the client, call this after
     * {@link #setLegalAndPotentialSettlements(Collection, int, HashSet[])}.
     *
     * @return the set of legal edge coordinates for roads, as a new Set of {@link Integer}s
     * @since 1.1.12
     */
    @Override
    HashSet<Integer> initPlayerLegalRoads()
    {
        return new HashSet<Integer>(legalRoadEdges);
    }

    /**
     * Create and initialize a {@link SOCPlayer}'s legalShips set.
     * Contains all 6 edges of each water hex.
     *<P>
     * Because the v3 board layout varies:
     * At the server, call this after {@link #makeNewBoard(Hashtable)}.
     * At the client, call this after
     * {@link #setLegalAndPotentialSettlements(Collection, int, HashSet[])}.
     *
     * @return the set of legal edge coordinates for ships, as a new Set of {@link Integer}s
     * @since 2.0.00
     */
    HashSet<Integer> initPlayerLegalShips()
    {
        return new HashSet<Integer>(legalShipEdges);
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
     * @param hexCoord  Coordinate ("ID") of this hex; not checked for validity
     * @param includeWater  Should water hexes be returned (not only land ones)?
     * @return the hexes that touch this hex, as a Vector of Integer coordinates,
     *         or null if none are adjacent (will <b>not</b> return a 0-length vector)
     */
    @Override
    public Vector<Integer> getAdjacentHexesToHex(final int hexCoord, final boolean includeWater)
    {
        Vector<Integer> hexes = new Vector<Integer>();

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
     * @param r  Hex row coordinate
     * @param c  Hex column coordinate
     */
    private final void getAdjacentHexes2Hex_AddIfOK
        (Vector<Integer> addTo, final boolean includeWater, final int r, final int c)
    {
        if ((r <= 0) || (c <= 0) || (r >= boardHeight) || (c >= boardWidth))
            return;  // not within the board's valid hex boundaries
        if ((r % 2) == 0)
            return;  // not a valid hex row

        if (includeWater
            || ((hexLayoutLg[r][c] <= MAX_LAND_HEX_LG)
                && (hexLayoutLg[r][c] != WATER_HEX)) )
        {
            addTo.addElement(new Integer((r << 8) | c));
        }
    }

    /**
     * The edge coordinates adjacent to this hex in all 6 directions.
     * (The 6 sides of this hex.)
     * Since all hexes have 6 edges, all edge coordinates are valid
     * if the hex coordinate is valid.
     *
     * @param hexCoord Coordinate of this hex; not checked for validity
     * @return  The 6 edges adjacent to this hex
     * @since 2.0.00
     */
    public int[] getAdjacentEdgesToHex(final int hexCoord)
    {
        int[] edge = new int[6];
        for (int dir = 0; dir < 6; ++dir)
            edge[dir] = hexCoord + A_EDGE2HEX[dir][0] + A_EDGE2HEX[dir][1];
        return edge;
    }

    /**
     * Is this edge adjacent to this hex?  (Is it one of the 6 sides of
     * the hex?)
     * @param edgeCoord  Coordinate of the edge; not checked for validity
     * @param hexCoord   Hex coordinate; not checked for validity
     * @return  true if adjacent
     */
    public boolean isEdgeAdjacentToHex(final int edgeCoord, final int hexCoord)
    {
        for (int dir = 0; dir < 6; ++dir)
        {
            if (edgeCoord == (hexCoord + A_EDGE2HEX[dir][0] + A_EDGE2HEX[dir][1]))
                return true;
        }
        return false;
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
    @Override
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
     * (The 6 corners of this hex.)
     * Since all hexes have 6 nodes, all node coordinates are valid
     * if the hex coordinate is valid.
     *
     * @param hexCoord Coordinate of this hex; not checked for validity
     * @return Node coordinate in all 6 directions,
     *           clockwise from top (northern point of hex):
     *           0 is north, 1 is northeast, etc, 5 is northwest.
     * @since 2.0.00
     * @see #getAdjacentNodeToHex(int, int)
     */
    @Override
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
     * @see #getAdjacentHexesToEdge_arr(int)
     * @see #getAdjacentHexesToEdgeEnds(int)
     */
    @Override
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
     * The valid hex or two hexes touching an edge along its length.
     * @param edgeCoord The edge's coordinate. Not checked for validity.
     * @return hex coordinate of each adjacent hex,
     *           or 0 if that hex would be off the edge of the board.
     * @see #getAdjacentHexToEdge(int, int)
     * @see #getAdjacentHexesToEdgeEnds(int)
     */
    public int[] getAdjacentHexesToEdge_arr(final int edgeCoord)
        throws IllegalArgumentException
    {
        int[] hexes = new int[2];
        final int r = (edgeCoord >> 8),
                  c = (edgeCoord & 0xFF);

        // "|" if r is odd
        if ((r % 2) == 1)
        {
            // FACING_E: (r, c+1)
            if (c < (boardWidth-1))
                hexes[0] = edgeCoord + 1;

            // FACING_W: (r, c-1)
            if (c > 1)
                hexes[1] = edgeCoord - 1;
        }

        // "/" if (s,c) is even,odd or odd,even
        else if ((c % 2) != ((r/2) % 2))
        {
            // FACING_NW: (r-1, c)
            if (r > 1)
                hexes[0] = edgeCoord - 0x100;

            // FACING_SE: (r+1, c+1)
            if ((r < (boardHeight-1)) && (c < (boardWidth-1)))
                hexes[1] = edgeCoord + 0x101;
        }
        else
        {
            // "\" if (s,c) is odd,odd or even,even

            // FACING_NE: (r-1, c+1)
            if ((r > 1) && (c < (boardWidth-1)))
                hexes[0] = edgeCoord - 0x100 + 0x01;

            // FACING_SW: (r+1, c)
            if (r < (boardHeight-1))
                hexes[1] = edgeCoord + 0x100;
        }

        return hexes;
    }

    /**
     * The valid hex or two hexes past each end of an edge.
     * The edge connects these two hexes.
     * For a north-south edge, for example, they would be north and south of the edge.
     * @param edgeCoord The edge's coordinate. Not checked for validity.
     * @return 2-element array with hex coordinate of each hex,
     *           or 0 if that hex would be off the edge of the board.
     * @see #getAdjacentHexToEdge(int, int)
     * @see #getAdjacentHexesToEdge_arr(int)
     */
    public int[] getAdjacentHexesToEdgeEnds(final int edgeCoord)
        throws IllegalArgumentException
    {
        int[] hexes = new int[2];
        final int r = (edgeCoord >> 8),
                  c = (edgeCoord & 0xFF);

        // "|" if r is odd
        if ((r % 2) == 1)
        {
            // N: (r-2, c)
            if (r > 2)
                hexes[0] = edgeCoord - 0x200;

            // S: (r+2, c)
            if (r < (boardHeight-2))
                hexes[1] = edgeCoord + 0x200;
        }

        // "/" if (s,c) is even,odd or odd,even
        else if ((c % 2) != ((r/2) % 2))
        {
            // NE: (r-1, c+2)
            if ((r > 1) && (c < (boardWidth-2)))
                hexes[0] = edgeCoord - 0x100 + 0x02;

            // SW: (r+1, c-1)
            if ((r < (boardHeight-1)) && (c > 1))
                hexes[1] = edgeCoord + 0x100 - 0x01;
        }
        else
        {
            // "\" if (s,c) is odd,odd or even,even

            // NW: (r-1, c-1)
            if ((r > 1) && (c > 1))
                hexes[0] = edgeCoord - 0x101;

            // SE: (r+1, c+2)
            if ((r < (boardHeight-1)) && (c < (boardWidth-2)))
                hexes[1] = edgeCoord + 0x102;
        }

        return hexes;
    }

    /**
     * Get the edge coordinates of the 2 to 4 edges adjacent to this edge.
     * @param coord  Edge coordinate; not checked for validity
     * @return the valid adjacent edges to this edge, as a Vector of Integer coordinates
     */
    @Override
    public Vector<Integer> getAdjacentEdgesToEdge(final int coord)
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

        Vector<Integer> edge = new Vector<Integer>(4);
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
     * The node at the end of an edge in a given direction.
     * @param edgeCoord The edge's coordinate. Not checked for validity.
     * @param facing  Direction along the edge towards the node; 1 to 6.
     *           To face southeast along a diagonal edge, use {@link #FACING_SE}.
     *           To face north along a vertical edge, use either {@link #FACING_NW} or {@link #FACING_NE}.
     * @return node coordinate of node in the facing direction.
     *           If <tt>edgeCoord</tt> is valid, both its node coordinates
     *           are valid and on the board.
     * @throws IllegalArgumentException if facing &lt; 1 or facing &gt; 6,
     *           or if the facing is perpendicular to the edge direction.
     *           ({@link #FACING_E} or {@link #FACING_W} for a north-south vertical edge,
     *            {@link #FACING_NW} for a northeast-southwest edge, etc.)
     * @see #getAdjacentNodesToEdge(int)
     */
    public int getAdjacentNodeToEdge(final int edgeCoord, final int facing)
        throws IllegalArgumentException
    {
        if ((facing < 1) && (facing > 6))
            throw new IllegalArgumentException("facing out of range");
        int r = (edgeCoord >> 8),
            c = (edgeCoord & 0xFF);
        boolean perpendicular = false;

        // "|" if r is odd
        if ((r%2) == 1)
        {
            switch (facing)
            {
            case FACING_NE: case FACING_NW:
                --r;
                break;
            case FACING_SE: case FACING_SW:
                ++r;
                break;
            case FACING_E:
            case FACING_W:
                perpendicular = true;
            }
        }

        // "/" if (s,c) is even,odd or odd,even
        else if ((c % 2) != ((r/2) % 2))
        {
            switch (facing)
            {
            case FACING_NE: case FACING_E:
                ++c;
                break;
            case FACING_SW: case FACING_W:
                // this node coord == edge coord
                break;
            case FACING_NW:
            case FACING_SE:
                perpendicular = true;
            }
        }
        else
        {
            // "\" if (s,c) is odd,odd or even,even
            switch (facing)
            {
            case FACING_E: case FACING_SE:
                ++c;
                break;
            case FACING_W: case FACING_NW:
                // this node coord == edge coord
                break;
            case FACING_NE:
            case FACING_SW:
                perpendicular = true;
            }
        }

        if (perpendicular)
            throw new IllegalArgumentException
                ("facing " + facing + " perpendicular from edge 0x"
                 + Integer.toHexString(edgeCoord));

        return ( (r << 8) | c );

        // Bounds-check OK: if edge coord is valid, its nodes are both valid
    }

    /**
     * Adjacent node coordinates to an edge (that is, the nodes that are the two ends of the edge).
     * @return the nodes that touch this edge, as a Vector of Integer coordinates
     * @see #getAdjacentNodesToEdge_arr(int)
     * @see #getAdjacentNodeToEdge(int, int)
     */
    @Override
    public Vector<Integer> getAdjacentNodesToEdge(final int coord)
    {
        Vector<Integer> nodes = new Vector<Integer>(2);
        final int[] narr = getAdjacentNodesToEdge_arr(coord);
        nodes.addElement(new Integer(narr[0]));
        nodes.addElement(new Integer(narr[1]));
        return nodes;
    }

    /**
     * Adjacent node coordinates to an edge (that is, the nodes that are the two ends of the edge).
     * @return the nodes that touch this edge, as an array of 2 integer coordinates
     * @see #getAdjacentNodesToEdge(int)
     * @see #getAdjacentNodeToEdge(int, int)
     * @see #getNodeBetweenAdjacentEdges(int, int)
     */
    @Override
    public int[] getAdjacentNodesToEdge_arr(final int coord)
    {
        int[] nodes = new int[2];

        final int r = coord >> 8;
        if ((r%2) == 1)
        {
            // "|" if r is odd
            nodes[0] = coord - 0x0100;  // (r-1,c)
            nodes[1] = coord + 0x0100;  // (r+1,c)
        }
        else
        {
            // either "/" or "\"
            nodes[0] = coord;           // (r,c)
            nodes[1] = coord + 0x0001;  // (r,c+1)
        }

        return nodes;

        // Bounds-check OK: if edge coord is valid, its nodes are both valid
    }

    /**
     * Given a pair of adjacent edge coordinates, get the node coordinate
     * that connects them.
     *<P>
     * Does not check actual settlements or other pieces on the board.
     *
     * @param edgeA  Edge coordinate adjacent to <tt>edgeB</tt>; not checked for validity
     * @param edgeB  Edge coordinate adjacent to <tt>edgeA</tt>; not checked for validity
     * @return  node coordinate between edgeA and edgeB
     * @see #getAdjacentNodesToEdge(int)
     * @throws IllegalArgumentException  if edgeA and edgeB aren't adjacent
     */
    public int getNodeBetweenAdjacentEdges(final int edgeA, final int edgeB)
	throws IllegalArgumentException
    {
	final int node;

	switch (edgeB - edgeA)
	{
	// Any node, when neither edgeA, edgeB are north/south:

	case 0x01:  // edgeB is east of edgeA, at coord (r, c+1) compared to edgeA
	    node = edgeB;
	    break;

	case -0x01:  // edgeB west of edgeA (r, c-1)
	    node = edgeA;
	    break;

	// 'Y' node, south and NW edges:

	case -0x0101:  // edgeA is south, edgeB is NW (r-1, c-1)
	    node = edgeB + 1;
	    break;

	case 0x0101:  // edgeA is NW, edgeB is south (r+1, c+1)
	    node = edgeA + 1;
	    break;

	// 'Y' node, south and NE edges;
	// also 'A' node, north and SE edges:

	case 0x0100:  
	    if (((edgeB >> 8) % 2) == 1)
		node = edgeA;  // 'Y', edgeA is NE, edgeB is south (r+1, c)
	    else
		node = edgeB;  // 'A', edgeA is north, edgeB is SE (r+1, c)
	    break;

	case -0x0100:
	    if (((edgeA >> 8) % 2) == 1)
		node = edgeB;  // 'Y', edgeA is south, edgeB is NE (r-1, c)
	    else
		node = edgeA;  // 'A', edgeA is SE, edgeB is north (r-1, c)
	    break;

	// 'A' node, north and SW edges:

	case (0x0100 - 0x01):  // edgeA is north, edgeB is SW (r+1, c-1)
	    node = edgeB + 1;
	    break;

	case (0x01 - 0x0100):  // edgeA is SW, edgeB is north (r-1, c+1)
	    node = edgeA + 1;
	    break;

	default:
	    throw new IllegalArgumentException
		("Edges not adjacent: 0x" + Integer.toHexString(edgeA)
		 + ", 0x" + Integer.toHexString(edgeB));
	}

        return node;
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
     * Get the coordinates of the hexes adjacent to this node.
     * These hexes may contain land or water.
     * @param nodeCoord  Node coordinate.  Is not checked for validity.
     * @return the coordinates (Integers) of the 1 to 3 hexes touching this node,
     *         within the boundaries (1, 1, boardHeight-1, boardWidth-1)
     *         because hex coordinates (their centers) are fully within the board.
     */
    @Override
    public Vector<Integer> getAdjacentHexesToNode(final int nodeCoord)
    {
        // Determining (r,c) node direction: Y or A
        //  s = r/2
        //  "Y" if (s,c) is even,odd or odd,even
        //  "A" if (s,c) is odd,odd or even,even
        // Bounds check for hexes: r > 0, c > 0, r < height, c < width

        final int r = (nodeCoord >> 8), c = (nodeCoord & 0xFF);
        Vector<Integer> hexes = new Vector<Integer>(3);

        final boolean nodeIsY = ( (c % 2) != ((r/2) % 2) );
        if (nodeIsY)
        {
            // North: (r-1, c)
            if (r > 1)
                hexes.addElement(new Integer(nodeCoord - 0x0100));

            if (r < (boardHeight-1))
            {
                // SW: (r+1, c-1)
                if (c > 1)
                    hexes.addElement(new Integer((nodeCoord + 0x0100) - 1));

                // SE: (r+1, c+1)
                if (c < (boardWidth-1))
                    hexes.addElement(new Integer((nodeCoord + 0x0100) + 1));
            }
        }
        else
        {
            // South: (r+1, c)
            if (r < (boardHeight-1))
                hexes.addElement(new Integer(nodeCoord + 0x0100));

            if (r > 1)
            {
                // NW: (r-1, c-1)
                if (c > 1)
                    hexes.addElement(new Integer((nodeCoord - 0x0100) - 1));

                // NE: (r-1, c+1)
                if (c < (boardWidth-1))
                    hexes.addElement(new Integer((nodeCoord - 0x0100) + 1));
            }
        }

        return hexes;
    }

    /**
     * Given a node, get the valid adjacent edge in a given direction, if any.
     *<P>
     * Along the edge of the board layout, valid land nodes
     * have some adjacent edges which may be
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
    @Override
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
    @Override
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
    @Override
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
     * @see #isNodeAdjacentToNode(int, int)
     */
    @Override
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
    @Override
    public int getAdjacentEdgeToNode2Away(final int node, final int node2away)
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
    @Override
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
    @Override
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


    @Override
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
    @Override
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
            nodeIDtoPortType = new Hashtable<Integer, Integer>();
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
        }
    }

    @Override
    public int getPortFacing(int portNum)
    {
        if ((portNum < 0) || (portNum >= portsCount))
            return 0;
        return portsLayout[portNum - (2 * portsCount)];
    }

    @Override
    public int[] getPortsEdges()
    {
        int[] edge = new int[portsCount];
        System.arraycopy(portsLayout, portsCount, edge, 0, portsCount);
        return edge;
    }

    @Override
    public int[] getPortsFacing()
    {
        int[] facing = new int[portsCount];
        System.arraycopy(portsLayout, 2 * portsCount, facing, 0, portsCount);
        return facing;
    }

    /**
     * Get the dice roll numbers for hexes on either side of this edge.
     * @return a string representation of an edge coordinate's dice numbers, such as "5/3";
     *      if a hex isn't a land hex, its number will be 0.
     * @see #getNumberOnHexFromCoord(int)
     */
    @Override
    public String edgeCoordToString(final int edge)
    {
        final int[] hexes = getAdjacentHexesToEdge_arr(edge);
        final int[] dnums = new int[2];
        for (int i = 0; i <= 1; ++i)
            if (hexes[i] != 0)
                dnums[i] = getNumberOnHexFromCoord(hexes[i]);

        return dnums[0] + "/" + dnums[1];
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
        0x0003, FACING_SE,  0x0006, FACING_SW,
        0x0209, FACING_SW,  0x050B, FACING_W,
        0x0809, FACING_NW,  0x0A06, FACING_NW,
        0x0A03, FACING_NE,  0x0702, FACING_E,
        0x0302, FACING_E
    };

    /**
     * My sample board layout: Outlying islands' ports.
     * Each port has 2 elements.
     * First: Coordinate, in hex: 0xRRCC
     * Second: Facing
     */
    private static final int PORT_EDGE_FACING_ISLANDS[] =
    {
        0x060E, FACING_NW,   // - northeast island
        0x0A0F, FACING_SW,  0x0E0C, FACING_NW,        // - southeast island
        0x0E06, FACING_SE    // - southwest island
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
        0x0104, 0x0106, 0x0108, 0x0309, 0x050A,
        0x0709, 0x0908, 0x0906, 0x0904, 0x0703,
        0x0502, 0x0303, 0x0305, 0x0307, 0x0508,
        0x0707, 0x0705, 0x0504, 0x0506
    };

    /**
     * My sample board layout: Main island's land hex coordinates, each row west to east.
     * @see #LANDHEX_DICEPATH_MAINLAND
     */
    private static final int LANDHEX_COORD_MAINLAND[] =
    {
        0x0104, 0x0106, 0x0108,
        0x0303, 0x0305, 0x0307, 0x0309,
        0x0502, 0x0504, 0x0506, 0x0508, 0x050A,
        0x0703, 0x0705, 0x0707, 0x0709,
        0x0904, 0x0906, 0x0908
    };

    /**
     * My sample board layout: Main island's land hex coordinates in fog.
     * For testing only: An actual fog scenario would have a larger main island layout.
     * @see #LANDHEX_COORD_MAINLAND
     */
    private static final int LANDHEX_COORD_MAINLAND_FOG[] =
    {
        // 1st row ok
        0x0303, 0x0305, // 2nd row 1st,2nd
                0x0504, 0x0506, // middle row 2nd,3rd
                0x0705  // 4th row 2nd
        // 5th row ok
    };

    /**
     * My sample board layout: All the outlying islands' land hex coordinates.
     *<P>
     * The first outlying island (land area 2) is upper-right on board.
     * Second island (landarea 3) is lower-right.
     * Third island (landarea 4) is lower-left.
     * @see #LANDHEX_COORD_ISLANDS_EACH
     * @see #LANDHEX_LANDAREA_RANGES_ISLANDS
     */
    private static final int LANDHEX_COORD_ISLANDS_ALL[] =
    {
        0x010E, 0x030D, 0x030F, 0x050E, 0x0510,
        0x0B0D, 0x0B0F, 0x0B11, 0x0D0C, 0x0D0E,
        0x0D02, 0x0D04, 0x0F05, 0x0F07
    };

    /**
     * My sample board layout: Each outlying island's land hex coordinates.
     * @see #LANDHEX_COORD_ISLANDS_ALL
     * @see #LANDHEX_LANDAREA_RANGES_ISLANDS
     */
    private static final int LANDHEX_COORD_ISLANDS_EACH[][] =
    {
        { 0x010E, 0x030D, 0x030F, 0x050E, 0x0510 },
        { 0x0B0D, 0x0B0F, 0x0B11, 0x0D0C, 0x0D0E },
        { 0x0D02, 0x0D04, 0x0F05, 0x0F07 }
    };

    /**
     * Island hex counts and land area numbers within {@link #LANDHEX_COORD_ISLANDS_ALL}.
     * Allows us to shuffle them all together ({@link #LANDHEX_TYPE_ISLANDS}).
     * @see #LANDHEX_COORD_ISLANDS_EACH
     */
    private static final int LANDHEX_LANDAREA_RANGES_ISLANDS[] =
    {
        2, 5,  // landarea 2 is an island with 5 hexes
        3, 5,  // landarea 3
        4, 4   // landarea 4
    };

    /**
     * My sample board layout: Land hex types for the 3 small islands,
     * to be used with (for the main island) {@link #makeNewBoard_landHexTypes_v1}[].
     */
    private static final int LANDHEX_TYPE_ISLANDS[] =
    {
        CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, WHEAT_HEX, WHEAT_HEX, DESERT_HEX,
        WOOD_HEX, WOOD_HEX, GOLD_HEX, GOLD_HEX
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

