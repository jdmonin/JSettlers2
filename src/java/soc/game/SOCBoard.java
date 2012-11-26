/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2012 Jeremy D Monin <jeremy@nand.net>
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

import java.io.Serializable;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;


/**
 * This is a representation of the board in Settlers of Catan.
 * Board initialization is done in {@link #makeNewBoard(Hashtable)}; that method
 * has some internal comments on structures, coordinates, layout and values.
 *<P>
 * A {@link SOCGame} uses this board; the board is not given a reference to the game, to enforce layering
 * and keep the board logic simple.  Game rules should be enforced at the game, not the board.
 *<P>
 * Because some game variants may need different board layouts or features,
 * you may need a subclass of SOCBoard: Use {@link #createBoard(Hashtable, int)}
 * whenever you need to construct a new SOCBoard.
 *<P>
 * To identify nearby nodes, edges, hexes, etc, use the methods
 * with names such as {@link #getAdjacentHexesToNode(int)}.
 *<P>
 * Other methods to examine the board: {@link SOCGame#getPlayersOnHex(int)},
 * {@link SOCGame#putPiece(SOCPlayingPiece)}, etc.
 *<P>
 * <b>Coordinate system,</b> as seen in appendix A of Robert S Thomas' dissertation:
 *<P>
 * <b>Hexes</b> (represented as coordinate of their centers),
 * <b>nodes</b> (corners of hexes; where settlements/cities are placed),
 * and <b>edges</b> (between nodes; where roads are placed),
 * share the same grid of coordinates.
 * Each hex is 2 units wide, in a 2-D coordinate system.
 *<P>
 * Current coordinate encoding: ({@link #BOARD_ENCODING_ORIGINAL})
 *<P>
 * <b>On the 4-player board:</b> See <tt>src/docs/hexcoord.gif</tt><br>
 * Coordinates start with hex (1,1) on the far west, and go to (D,D) on the east.
 * The ring of water hexes surrounding land, is within these coordinates. (Land
 * hexes in that row are (3,3) to (B,B). 
 * The first axis runs northwest to southeast; the second runs southwest to northeast.
 * Having six sides, hexes run in a straight line west to east, separated by vertical edges;
 * both coordinates increase along a west-to-east line.
 *<P>
 * All coordinates are encoded as two-digit hex integers, one digit per axis (thus 00 to FF).
 * The center hex is encoded as 77; see the dissertation PDF's appendix for diagrams.
 * Unfortunately this format means the board can't be expanded without changing its
 * encoding, which is used across the network.
 *<P>
 * <b>On the 6-player board:</b> See <tt>src/docs/hexcoord-6player.gif</tt><br>
 * The 6-player board is rotated 90 degrees clockwise from the 4-player board,
 * so coordinates start with hex (1,1) as the northernmost land hex, and
 * hex (B,B) is the southernmost land hex.  The ring of water hexes are outside
 * this coordinate grid.
 *<P>
 * @author Robert S Thomas
 */
public class SOCBoard implements Serializable, Cloneable
{
    //
    // Hex types
    //
    /** Desert; lowest-numbered hex type */
    public static final int DESERT_HEX = 0;
    public static final int CLAY_HEX = 1;
    public static final int ORE_HEX = 2;
    public static final int SHEEP_HEX = 3;
    public static final int WHEAT_HEX = 4;
    /** Wood; highest-numbered land hex type (also MAX_LAND_HEX, MAX_ROBBER_HEX) */
    public static final int WOOD_HEX = 5;
    /** Highest-numbered land hex type (currently wood; also currently MAX_ROBBER_HEX)  @since 1.1.07 */
    public static final int MAX_LAND_HEX = 5;  // Also MAX_ROBBER_HEX

    /** Water hex; higher-numbered than all land hex types */
    public static final int WATER_HEX = 6;
    /** Misc (3-for-1) port type; lowest-numbered port-hextype integer */
    public static final int MISC_PORT_HEX = 7;  // Must be first port-hextype integer
    public static final int CLAY_PORT_HEX = 8;
    public static final int ORE_PORT_HEX = 9;
    public static final int SHEEP_PORT_HEX = 10;
    public static final int WHEAT_PORT_HEX = 11;
    /** Wood port type; highest-numbered port-hextype integer */
    public static final int WOOD_PORT_HEX = 12;  // Must be last port-hextype integer

    /** Misc (3-for-1) port; lowest-numbered port-type integer */
    public static final int MISC_PORT = 0;  // Must be first port-type integer; must be 0 (hardcoded in places here)
    /** Clay port type */
    public static final int CLAY_PORT = 1;
    /** Ore port type */
    public static final int ORE_PORT = 2;
    /** Sheep port type */
    public static final int SHEEP_PORT = 3;
    /** Wheat port type */
    public static final int WHEAT_PORT = 4;
    /** Wood port type; highest-numbered port-type integer */
    public static final int WOOD_PORT = 5;  // Must be last port-type integer
    
    /** Highest-numbered hex type which may hold a robber: highest land: {@link #MAX_LAND_HEX}. */
    public static final int MAX_ROBBER_HEX = MAX_LAND_HEX;

    /**
     * Facing is the direction (1-6) to the hex touching a hex or edge,
     * or from a node to another node 2 nodes away.
     * Facing 1 is NE, 2 is E, 3 is SE, 4 is SW, etc;
     * used in {@link #hexLayout} for ports, and elsewhere.<pre>
      6 &lt;--.    .--> 1
            \/\/
            /  \
       5&lt;--|    |--> 2
           |    |
            \  /
            /\/\
      4 &lt;--.    .--> 3  </pre>
     * @since 1.1.08
     */
    public static final int FACING_NE = 1, FACING_E = 2, FACING_SE = 3,
        FACING_SW = 4, FACING_W = 5, FACING_NW = 6;

    /**
     * Port Placement constants begin here
     * ------------------------------------------------------------------------------------
     */

    /**
     * Each port's type, such as {@link #SHEEP_PORT}, on standard board.
     * Same order as {@link #PORTS_FACING_V1}. {@link #MISC_PORT} is 0.
     * @since 1.1.08
     */
    private final static int PORTS_TYPE_V1[] = { 0, 0, 0, 0, CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT};

    /**
     * Each port's hex number within {@link #hexLayout} on standard board.
     * Same order as {@link #PORTS_FACING_V1}:
     * Clockwise from upper-left (hex coordinate 0x17).
     * @since 1.1.08
     */
    private final static int PORTS_HEXNUM_V1[] = { 0, 2, 8, 21, 32, 35, 33, 22, 9 };

    /**
     * Each port's <em>facing,</em> on standard board.
     * Ordered clockwise from upper-left (hex coordinate 0x17).
     * Port Facing is the direction from the port hex, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     * Facing 2 is east ({@link #FACING_E}), 3 is SE, 4 is SW, etc; see {@link #hexLayout}.
     * @since 1.1.08
     */
    private final static int PORTS_FACING_V1[] =
    {
        FACING_SE, FACING_SW, FACING_SW, FACING_W, FACING_NW, FACING_NW, FACING_NE, FACING_E, FACING_E
    };

    /**
     * Each port's 2 node coordinates on standard board.
     * Same order as {@link #PORTS_FACING_V1}:
     * Clockwise from upper-left (hex coordinate 0x17).
     * @since 1.1.08
     */
    private final static int PORTS_EDGE_V1[] = 
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
     * Each port's type, such as {@link #SHEEP_PORT}, on 6-player board.
     * Same order as {@link #PORTS_FACING_V2}. {@link #MISC_PORT} is 0.
     * @since 1.1.08
     */
    private final static int PORTS_TYPE_V2[] =
        { 0, 0, 0, 0, CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT, MISC_PORT, SHEEP_PORT };

    /**
     * Each port's <em>facing,</em> on 6-player board.
     * Ordered clockwise from upper-left (hex coordinate 0x17, which is land in the V2 layout).
     * Port Facing is the direction from the port hex, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     * Within the board orientation (not the rotated visual orientation),
     * facing 2 is east ({@link #FACING_E}), 3 is SE, 4 is SW, etc.
     * @since 1.1.08
     */
    private final static int PORTS_FACING_V2[] =
    {
        FACING_SE, FACING_SW, FACING_SW, FACING_W, FACING_W, FACING_NW,
        FACING_NW, FACING_NE, FACING_NE, FACING_E, FACING_SE
    };

    /**
     * Each port's edge coordinate on the 6-player board.
     * This is the edge whose 2 end nodes can be used to build port settlements/cities.
     * Same order as {@link #PORTS_FACING_V2}:
     * Clockwise from upper-left (hex coordinate 0x17, which is land in the V2 layout).
     * @since 1.1.08
     */
    private final static int PORTS_EDGE_V2[] = 
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
     * Board Encoding fields begin here
     * ------------------------------------------------------------------------------------
     */

    /**
     * Original format (1) for {@link #getBoardEncodingFormat()}:
     * Hexadecimal 0x00 to 0xFF along 2 diagonal axes.
     * Coordinate range on each axis is 0 to 15 decimal. In hex:<pre>
     *   Hexes: 11 to DD
     *   Nodes: 01 or 10, to FE or EF
     *   Edges: 00 to EE </pre>
     *<P>
     * See the Dissertation PDF for details.
     * @since 1.1.06
     */
    public static final int BOARD_ENCODING_ORIGINAL = 1;

    /**
     * 6-player format (2) for {@link #getBoardEncodingFormat()}:
     * Land hexes are same encoding as {@link #BOARD_ENCODING_ORIGINAL}.
     * Land starts 1 extra hex west of standard board,
     * and has an extra row of land at north and south end.
     * Ports are not part of {@link #hexLayout} because their
     * coordinates wouldn't fit within 2 hex digits.
     * Instead, see {@link #getPortTypeFromNodeCoord(int)},
     *   {@link #getPortCoordinates(int)} or {@link #getPortsLayout()}.
     * @since 1.1.08
     */
    public static final int BOARD_ENCODING_6PLAYER = 2;

    /**
     * Largest value of {@link #getBoardEncodingFormat()} supported in this version.
     * @since 1.1.08
     */
    public static final int MAX_BOARD_ENCODING = 2;

    /**
     * Size of board in coordinates (not in number of hexes across).
     * Default size per BOARD_ENCODING_ORIGINAL is: <pre>
     *   Hexes: 11 to DD
     *   Nodes: 01 or 10, to FE or EF
     *   Edges: 00 to EE </pre>
     * @since 1.1.06
     */
    private int boardWidth, boardHeight;

    /**
     * Minimum and maximum edge and node coordinates in this board's encoding.
     * ({@link #MAXNODE} is the same in both current encodings.)
     * @since 1.1.08
     */
    private int minNode, minEdge, maxEdge;

    /**
     * The encoding format of board coordinates,
     * or {@link #BOARD_ENCODING_ORIGINAL} (default, original).
     * The board size determines the required encoding format.
     *<UL>
     *<LI> 1 - Original format: hexadecimal 0x00 to 0xFF.
     *       Coordinate range is 0 to 15 (in decimal).
     *       Port types and facings encoded within {@link #hexLayout}.
     *<LI> 2 - 6-player board, variant of original format: hexadecimal 0x00 to 0xFF.
     *       Coordinate range is 0 to 15 (in decimal).
     *       Port types stored in {@link #portsLayout}.
     *       Added in 1.1.08.
     *</UL>
     * @since 1.1.06
     */
    private int boardEncodingFormat;

    /**
     * Board Encoding fields end here
     * ------------------------------------------------------------------------------------
     */

    /**
     * largest coordinate value for a hex, in the current encoding.
     */
    protected static final int MAXHEX = 0xDD;  // See also hardcoded checks in {@link #getAdjacentHexes_AddIfOK.

    /**
     * smallest coordinate value for a hex, in the current encoding.
     */
    protected static final int MINHEX = 0x11;

    /**
     * largest coordinate value for an edge, in the v1 encoding.
     * Named <tt>MAXEDGE</tt> before v1.1.11 ; the name change is a
     * reminder that {@link #MAXEDGE_V2} represents a different encoding.
     * @since 1.1.11
     */
    protected static final int MAXEDGE_V1 = 0xCC;

    /**
     * largest coordinate value for an edge, in the v2 encoding
     * @since 1.1.08
     */
    protected static final int MAXEDGE_V2 = 0xCC;

    /**
     * smallest coordinate value for an edge, in the v1 encoding.
     * Named <tt>MINEDGE</tt> before v1.1.11 ; the name change is a
     * reminder that {@link #MINEDGE_V2} has a different value.
     * @since 1.1.11
     */
    protected static final int MINEDGE_V1 = 0x22;

    /**
     * smallest coordinate value for an edge, in the v2 encoding
     * @since 1.1.08
     */
    protected static final int MINEDGE_V2 = 0x00;

    /**
     * largest coordinate value for a node on land, in the v1 and v2 encodings
     */
    public static final int MAXNODE = 0xDC;

    /**
     * smallest coordinate value for a node on land, in the v1 encoding.
     * Named <tt>MINNODE</tt> before v1.1.11 ; the name change is a
     * reminder that {@link #MINNODE_V2} has a different value.
     * @since 1.1.11
     */
    protected static final int MINNODE_V1 = 0x23;

    /**
     * largest coordinate value for a node on land plus one, in the v1 and v2 encodings
     */
    public static final int MAXNODEPLUSONE = MAXNODE + 1;

    /**
     * smallest coordinate value for a node on land, in the v2 encoding
     * @since 1.1.08
     */
    protected static final int MINNODE_V2 = 0x01;

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

    /***************************************
     * Hex data array, one element per water or land (or port, which is special water) hex.
     * Each element's coordinates on the board ("hex ID") is {@link #numToHexID}[i].
     * Each element's value encodes hex type and, if a
     * port, its facing ({@link #FACING_NE} to {@link #FACING_NW}).
     *<P>
     * For land hexes, the dice number on <tt>hexLayout</tt>[i] is {@link #numberLayout}[i].
     *<P>
     * Key to the hexLayout[] values:
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
     *  For board encoding formats {@link #BOARD_ENCODING_ORIGINAL} and
     *  {@link #BOARD_ENCODING_6PLAYER}, hexLayout indexes are arranged
     *  this way per {@link #numToHexID}: <pre>
       0   1   2   3

     4   5   6   7   8

   9  10  11  12  13  14

15  16  17  18  19  20  21

  22  23  24  25  26  27

    28  29  30  31  32

      33  34  35  36  </pre>
     *
     *  The 6-player board is visually rotated clockwise 90 degrees; the
     *  client's visual "North" (index 15, hex coordinate 0x11) on that
     *  board is West internally in the board layout.
     *
         @see #getHexTypeFromNumber(int)
         @see #getAdjacentNodeToHex(int, int)
     *
     **/
    private int[] hexLayout =   // initially all WATER_HEX (== 6)
    {
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, WATER_HEX
    };

    /**
     * On the 6-player (v2 layout) board, each port's type.  Null otherwise.
     * Same value range as in {@link #hexLayout}.
     * (In the standard (v1) board, these are part of {@link #hexLayout} instead.) 
     * 1 element per port. Same ordering as {@link #PORTS_FACING_V2}.
     * Initialized in {@link #makeNewBoard(Hashtable)}.
     * @see #ports
     * @since 1.1.08
     */
    private int[] portsLayout;

    /*
       private int numberLayout[] = { -1, -1, -1, -1,
       -1, 8, 9, 6, -1,
       -1, 2, 4, 3, 7, -1,
       -1, -1, 1, 8, 2, 5, -1,
       -1, 5, 7, 6, 1, -1,
       -1, 3, 0, 4, -1,
       -1, -1, -1, -1 };
     */
    /** Dice number from hex numbers.
     *  For coord mapping, see {@link #numToHexID}
     *<P>
     *  <tt>numberLayout</tt>[i] is the dice number for the land hex stored in {@link #hexLayout}[i].
     */
    private int[] numberLayout =    // TODO largerboard: assumes hexLayout.length == 37 (is valid for ORIGINAL, 6PLAYER encodings)
    {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1
    };

    /** Hex coordinates ("IDs") of each hex number ("hex number" means index within
     *  {@link #hexLayout}).
     *<UL>
     *<LI> {@link #BOARD_ENCODING_ORIGINAL}:  The hexes in here are the board's land hexes and also
     *     the surrounding ring of water/port hexes.
     *<LI> {@link #BOARD_ENCODING_6PLAYER}:  The hexes in here are the board's land hexes and also
     *     the unused hexes (rightmost column: 7D - DD - D7).
     *</UL>
     * @see #hexIDtoNum
     * @see #nodesOnBoard
     * @see #HEXCOORDS_LAND_V1
     * @see #HEXCOORDS_LAND_V2
     */
    private int[] numToHexID = 
    {
        0x17, 0x39, 0x5B, 0x7D,
        
        0x15, 0x37, 0x59, 0x7B, 0x9D,
        
        0x13, 0x35, 0x57, 0x79, 0x9B, 0xBD,
        
        0x11, 0x33, 0x55, 0x77, 0x99, 0xBB, 0xDD,
        
        0x31, 0x53, 0x75, 0x97, 0xB9, 0xDB,
        
        0x51, 0x73, 0x95, 0xB7, 0xD9,
        
        0x71, 0x93, 0xB5, 0xD7

        // The hex coordinate layout given here can also
        // be seen in RST's dissertation figure A.1.
    };

    /**
     * translate hex ID (hex coordinate) to an array index within {@link #hexLayout},
     * which is sometimes called its "hex number".
     * The numbers in here are the board's land hexes and also the surrounding
     * ring of water/port hexes.  Initialized in constructor.  Length is >= {@link #MAXHEX}.
     * A value of -1 means the ID isn't a valid hex number on the board.
     * @see #numToHexID
     * @see #nodeIDtoPortType
     */
    private int[] hexIDtoNum;

    /**
     * translate node ID (node coordinate) to a port's type ({@link #MISC_PORT} to {@link #WOOD_PORT}).
     * Initialized in {@link #makeNewBoard(Hashtable)}.  Length is >= {@link #MAXNODE}.
     * A value of -1 means the ID isn't a valid port on the board.
     * @see #ports
     * @see #portsLayout
     * @see #hexIDtoNum
     * @since 1.1.08
     */
    private int[] nodeIDtoPortType;

    /**
     * Offset to add to hex coordinate to get all adjacent node coords, starting at
     * index 0 at the top (northern point of hex) and going clockwise (RST dissertation figure A.5).
     * Because we're looking at nodes and not edges (points, not sides, of the hex),
     * these are offset from the set of "facing" directions by 30 degrees.
     * -- see getAdjacent* methods instead
     */
    private final int[] HEXNODES = { 0x01, 0x12, 0x21, 0x10, -0x01, -0x10 };

    /**
     * offset of all hexes adjacent to a node
     * -- @see #getAdjacentHexesToNode(int) instead
     * private int[] nodeToHex = { -0x21, 0x01, -0x01, -0x10, 0x10, -0x12 };
     */

    /**
     * Offsets from a node to another node 2 away,
     * indexed by the facing directions: {@link #FACING_NE} is 1,
     * {@link #FACING_E} is 2, etc; {@link #FACING_NW} is 6.
     * Used by {@link #getAdjacentNodeToNode2Away(int, int)}. 
     * See RST dissertation figure A.2.
     * @since 1.1.12
     */
    private final int[] NODE_2_AWAY = { -9, 0x02, 0x22, 0x20, -0x02, -0x22, -0x20 };

    /**
     * the hex coordinate that the robber is in; placed on desert in constructor
     */
    private int robberHex;

    /**
     * the previous hex coordinate that the robber is in; -1 unless
     * {@link #setRobberHex(int, boolean) setRobberHex(rh, true)} was called.
     * @since 1.1.11
     */
    private int prevRobberHex;

    /**
     * where the ports are; coordinates per port type.
     * Indexes are port types, {@link #MISC_PORT} to {@link #WOOD_PORT}.
     * @see #portsLayout
     */
    private Vector[] ports;

    /**
     * pieces on the board; Vector of SOCPlayingPiece
     */
    private Vector pieces;

    /**
     * roads on the board; Vector of SOCPlayingPiece
     */
    private Vector roads;

    /**
     * settlements on the board; Vector of SOCPlayingPiece
     */
    private Vector settlements;

    /**
     * cities on the board; Vector of SOCPlayingPiece
     */
    private Vector cities;

    /**
     * random number generator
     */
    private Random rand = new Random();

    /**
     * a list of nodes on the land of the board; key is node's Integer coordinate, value is Boolean.
     * nodes on outer edges of surrounding water/ports are not on the board.
     * See dissertation figure A.2.
     * See also {@link SOCPlayer#initLegalAndPotentialSettlements()}.
     */
    protected Hashtable nodesOnBoard;

    /**
     * Create a new Settlers of Catan Board based on <tt>gameOpts</tt>; this is a factory method.
     * @param gameOpts  if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     * @param maxPlayers Maximum players; must be 4 or 6.
     * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
     * @since 1.1.11
     */
    public static SOCBoard createBoard(Hashtable gameOpts, final int maxPlayers)
        throws IllegalArgumentException
    {
        return new SOCBoard(gameOpts, maxPlayers);
    }

    /**
     * Create a new Settlers of Catan Board.
     * @param gameOpts  if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     * @param maxPlayers Maximum players; must be 4 or 6. (Added in 1.1.08)
     * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
     * @see #createBoard(Hashtable, int)
     */
    protected SOCBoard(Hashtable gameOpts, final int maxPlayers)
        throws IllegalArgumentException
    {
        if ((maxPlayers != 4) && (maxPlayers != 6))
            throw new IllegalArgumentException("maxPlayers: " + maxPlayers);
        boardWidth = 0x10;
        boardHeight = 0x10;
        final boolean is6player = (maxPlayers == 6);

        if (is6player)
        {
            boardEncodingFormat = BOARD_ENCODING_6PLAYER;
            minEdge = MINEDGE_V2;
            maxEdge = MAXEDGE_V2;
            minNode = MINNODE_V2;
        } else {
            boardEncodingFormat = BOARD_ENCODING_ORIGINAL;  // See javadoc of boardEncodingFormat
            minEdge = MINEDGE_V1;
            maxEdge = MAXEDGE_V1;
            minNode = MINNODE_V1;
        }

        robberHex = -1;  // Soon placed on desert, when makeNewBoard is called
        prevRobberHex = -1;

        /**
         * generic counter
         */
        int i;

        /**
         * initialize the pieces vectors
         */
        pieces = new Vector(96);
        roads = new Vector(60);
        settlements = new Vector(20);
        cities = new Vector(16);

        /**
         * initialize the port vector
         */
        ports = new Vector[6];  // 1 per resource type, MISC_PORT to WOOD_PORT
        ports[MISC_PORT] = new Vector(8);

        for (i = CLAY_PORT; i <= WOOD_PORT; i++)
        {
            ports[i] = new Vector(2);
        }

        /**
         * initialize the hexIDtoNum array;
         * see dissertation figure A.1 for coordinates
         */
        hexIDtoNum = new int[0xEE];  // Length must be >= MAXHEX

        for (i = 0; i < 0xEE; i++)
        {
            hexIDtoNum[i] = -1;  // -1 means off the board
        }

        // Sets up the board as land hexes with surrounding ring of water/port hexes.

        initHexIDtoNumAux(0x17, 0x7D, 0);  // Top horizontal row: 4 hexes across
        initHexIDtoNumAux(0x15, 0x9D, 4);  // Next horiz row: 5 hexes
        initHexIDtoNumAux(0x13, 0xBD, 9);  // Next: 6
        initHexIDtoNumAux(0x11, 0xDD, 15); // Middle horizontal row: 7
        initHexIDtoNumAux(0x31, 0xDB, 22); // Next: 6
        initHexIDtoNumAux(0x51, 0xD9, 28); // Next: 5
        initHexIDtoNumAux(0x71, 0xD7, 33); // Bottom horizontal row: 4 hexes across

        nodesOnBoard = new Hashtable();

        /**
         * initialize the list of nodes on the land of the board;
         * nodes on outer edges of surrounding water/ports are not on the board.
         * See dissertation figure A.2.
         * 6-player starts land 1 extra hex (2 nodes) west of standard board,
         * and has an extra row of land hexes at north and south end.
         * See also SOCPlayer.initLegalAndPotentialSettlements.
         */
        final Boolean t = new Boolean(true);
        final int westAdj = (is6player) ? 0x22 : 0x00;

        if (is6player)
        {
            for (i = 0x07; i <= 0x6D; i += 0x11)
                nodesOnBoard.put(new Integer(i), t);
        }

        for (i = 0x27 - westAdj; i <= 0x8D; i += 0x11)  //  Northernmost horizontal row: each north corner across 3 hexes
            nodesOnBoard.put(new Integer(i), t);

        for (i = 0x25 - westAdj; i <= 0xAD; i += 0x11)  // Next: each north corner of row of 4 / south corner of the northernmost 3 hexes
            nodesOnBoard.put(new Integer(i), t);

        for (i = 0x23 - westAdj; i <= 0xCD; i += 0x11)  // Next: north corners of middle row of 5 hexes
            nodesOnBoard.put(new Integer(i), t);

        for (i = 0x32 - westAdj; i <= 0xDC; i += 0x11) // Next: south corners of middle row of 5 hexes
            nodesOnBoard.put(new Integer(i), t);

        for (i = 0x52 - westAdj; i <= 0xDA; i += 0x11)  // South corners of row of 4 / north corners of the southernmost 3 hexes
            nodesOnBoard.put(new Integer(i), t);

        for (i = 0x72 - westAdj; i <= 0xD8; i += 0x11)  // Southernmost horizontal row: each south corner across 3 hexes
            nodesOnBoard.put(new Integer(i), t);

        if (is6player)
        {
            for (i = 0x70; i <= 0xD6; i += 0x11)
                nodesOnBoard.put(new Integer(i), t);
        }
    }

    /**
     * Auxiliary method for initializing part of the hexIDtoNum array.
     * Between begin and end, increment coord by 0x22, which moves 1 hex to the east.
     * See dissertation figure A.1.
     * @param begin Beginning of coordinate range
     * @param end   Ending coordinate - same horizontal row as begin
     * @param num   Number to assign to first {@link #hexIDtoNum}[] within this coordinate range;
     *              corresponds to hex's index ("hex number") within {@link #hexLayout}.
     */
    private final void initHexIDtoNumAux(int begin, int end, int num)
    {
        int i;

        for (i = begin; i <= end; i += 0x22)
        {
            hexIDtoNum[i] = num;
            num++;
        }
    }

    /**
     * Possible number paths for 4-player original board.
     * {@link #makeNewBoard(Hashtable)} randomly chooses one path (one 1-dimensional array)
     * to be used as <tt>numPath[]</tt> in
     * {@link #makeNewBoard_placeHexes(int[], int[], int[], SOCGameOption)}.
     */
    private final static int[][] makeNewBoard_numPaths_v1 =
    {
        // Numbers are indexes within hexLayout (also in numberLayout) for each land hex.
        // See the hexLayout javadoc for how the indexes are arranged on the board layout.

        // counterclockwise from southwest
        {
            29, 30, 31, 26, 20, 13, 7, 6, 5, 10, 16, 23,  // outermost hexes
            24, 25, 19, 12, 11, 17,    18  // middle ring, center hex
        },

        // clockwise from southwest
        {
            29, 23, 16, 10, 5, 6, 7, 13, 20, 26, 31, 30,
            24, 17, 11, 12, 19, 25,    18
        },

        // counterclockwise from east corner
        {
            20, 13, 7, 6, 5, 10, 16, 23, 29, 30, 31, 26,
            19, 12, 11, 17, 24, 25,    18
        },

        // clockwise from east corner
        {
            20, 26, 31, 30, 29, 23, 16, 10, 5, 6, 7, 13,
            19, 25, 24, 17, 11, 12,    18
        },

        // counterclockwise from northwest
        {
            5, 10, 16, 23, 29, 30, 31, 26, 20, 13, 7, 6,
            11, 17, 24, 25, 19, 12,    18
        },

        // clockwise from northwest
        {
            5, 6, 7, 13, 20, 26, 31, 30, 29, 23, 16, 10,
            11, 12, 19, 25, 24, 17,    18
        }
    };

    /**
     * Possible number paths for 6-player board.
     * {@link #makeNewBoard(Hashtable)} randomly chooses one path (one 1-dimensional array)
     * to be used as <tt>numPath[]</tt> in
     * {@link #makeNewBoard_placeHexes(int[], int[], int[], SOCGameOption)}.
     */
    private final static int[][] makeNewBoard_numPaths_6pl =
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

    /**
     * Shuffle the hex tiles and layout a board.
     * This is called at server, but not at client;
     * client instead calls methods such as {@link #setHexLayout(int[])}.
     * @param opts {@link SOCGameOption Game options}, which may affect
     *          tile placement on board, or null.  <tt>opts</tt> must be
     *          the same as passed to constructor, and thus give the same size and layout
     *          (same {@link #getBoardEncodingFormat()}).
     */
    public void makeNewBoard(Hashtable opts)
    {
        final boolean is6player = (boardEncodingFormat == BOARD_ENCODING_6PLAYER);

        // For purpose/format of these arrays, see the
        // makeNewBoard_placeHexes javadoc.
        final int[] landHex_v1 = { 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5 };
        final int[] landHex_6pl = { 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 
            DESERT_HEX, CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, SHEEP_HEX, SHEEP_HEX,
            WHEAT_HEX, WHEAT_HEX, WOOD_HEX, WOOD_HEX };
        final int[] number_v1 = { 5, 2, 6, 3, 8, 10, 9, 12, 11, 4, 8, 10, 9, 4, 5, 6, 3, 11 };
        final int[] number_6pl =
            {
                2,   5,  4,  6 , 3, // A-E
                9,   8, 11, 11, 10, // F-J
                6,   3,  8,  4,  8, // K-O
                10, 11, 12, 10,  5, // P-T
                4,   9,  5,  9, 12, // U-Y
                3,   2,  6          // Za-Zc        
            };

        SOCGameOption opt_breakClumps = (opts != null ? (SOCGameOption)opts.get("BC") : null);

        // shuffle and place the land hexes, numbers, and robber:
        // sets robberHex, contents of hexLayout[] and numberLayout[].
        // Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
        {
            final int[] landHex = is6player ? landHex_6pl : landHex_v1;
            final int[][] numPaths = is6player ? makeNewBoard_numPaths_6pl : makeNewBoard_numPaths_v1;
            final int[] numPath = 
                numPaths[ Math.abs(rand.nextInt() % numPaths.length) ];
            final int[] numbers = is6player ? number_6pl : number_v1;
            makeNewBoard_placeHexes
                (landHex, numPath, numbers, opt_breakClumps);
        }

        // copy and shuffle the ports, and check vs game option BC
        final int[] portTypes = (is6player) ? PORTS_TYPE_V2 : PORTS_TYPE_V1;
        int[] portHex = new int[portTypes.length];
    	System.arraycopy(portTypes, 0, portHex, 0, portTypes.length);
        makeNewBoard_shufflePorts
            (portHex, opt_breakClumps);
        if (is6player)
            portsLayout = portHex;  // No need to remember for 4-player standard layout

        // place the ports (hex numbers and facing) within hexLayout and nodeIDtoPortType.
        // fill out the ports[] vectors with node coordinates where a trade port can be placed.
        nodeIDtoPortType = new int[MAXNODEPLUSONE];
        for (int i = 0; i <= MAXNODE; ++i)
            nodeIDtoPortType[i] = -1;  // -1 means not a port (or not a valid node coord)
        if (is6player)
        {
            for (int i = 0; i < PORTS_FACING_V2.length; ++i)
            {
                final int ptype = portHex[i];
                final int[] nodes = getAdjacentNodesToEdge_arr(PORTS_EDGE_V2[i]);
                placePort(ptype, -1, PORTS_FACING_V2[i], nodes[0], nodes[1]);
                ports[ptype].addElement(new Integer(nodes[0]));
                ports[ptype].addElement(new Integer(nodes[1]));
            }            
        } else {
            for (int i = 0; i < PORTS_FACING_V1.length; ++i)
            {
                final int ptype = portHex[i];
                final int[] nodes = getAdjacentNodesToEdge_arr(PORTS_EDGE_V1[i]);
                placePort(ptype, PORTS_HEXNUM_V1[i], PORTS_FACING_V1[i], nodes[0], nodes[1]);
                ports[ptype].addElement(new Integer(nodes[0]));
                ports[ptype].addElement(new Integer(nodes[1]));
            }
        }

    }

    /**
     * For {@link #makeNewBoard(Hashtable)}, place the land hexes, number, and robber,
     * after shuffling landHex[].
     * Sets robberHex, contents of hexLayout[] and numberLayout[].
     * Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
     * (for land hex resource types).
     * @param landHex  Resource type to place into {@link #hexLayout} for each land hex; will be shuffled.
     *                    Values are {@link #CLAY_HEX}, {@link #DESERT_HEX}, etc.
     * @param numPath  Indexes within {@link #hexLayout} (also within {@link #numberLayout}) for each land hex;
     *                    same array length as <tt>landHex[]</tt>
     * @param number   Numbers to place into {@link #numberLayout} for each land hex;
     *                    array length is <tt>landHex[].length</tt> minus 1 for each desert in <tt>landHex[]</tt>
     * @param optBC  The game options for this board; only option "BC" is checked for.
     */
    private final void makeNewBoard_placeHexes
        (int[] landHex, final int[] numPath, final int[] number, SOCGameOption optBC)
    {
        final boolean checkClumps = (optBC != null) && optBC.getBoolValue();
        final int clumpSize = checkClumps ? optBC.getIntValue() : 0;
        boolean clumpsNotOK = checkClumps;

        do   // will re-do placement until clumpsNotOK is false
        {
            // shuffle the land hexes 10x
            for (int j = 0; j < 10; j++)
            {
                int idx, tmp;
                for (int i = 0; i < landHex.length; i++)
                {
                    // Swap a random card below the ith card with the ith card
                    idx = Math.abs(rand.nextInt() % (landHex.length - i));
                    tmp = landHex[idx];
                    landHex[idx] = landHex[i];
                    landHex[i] = tmp;
                }
            }

            int cnt = 0;
            for (int i = 0; i < landHex.length; i++)
            {
                // place the land hexes
                hexLayout[numPath[i]] = landHex[i];

                // place the robber on the desert
                if (landHex[i] == DESERT_HEX)
                {
                    robberHex = numToHexID[numPath[i]];
                    numberLayout[numPath[i]] = -1;
                }
                else
                {
                    // place the numbers
                    numberLayout[numPath[i]] = number[cnt];
                    cnt++;
                }
            }  // for(i in landHex)

            if (checkClumps)
            {
                /**
                 * Depth-first search to check land hexes for resource clumps.
                 *
                 * Start with the set of all land hexes, and consider them 'unvisited'.
                 * Look at each hex in the set, marking them as visited and moving
                 * them into new subsets ("clumps") composed of adjacent hexes of the
                 * same resource type. Build clumps by immediately visiting those adjacent
                 * hexes, and their unvisited same-type adjacents, etc.
                 * Once we've visited each hex, check if any clump subset's
                 * size is larger than the allowed size.
                 *
                 * Pseudocode:
                // Using vectors to represent sets.
                //   Sets will contain each hex's index within hexLayout.
                //
                // - clumps := new empty set (will be a vector of vectors)
                //     At end of search, each element of this set will be
                //       a subset (a vector) of adjacent hexes
                // - clumpsNotOK := false
                // - unvisited-set := new set (vector) of all land hexes
                // - iterate through unvisited-set; for each hex:
                //     - remove this from unvisited-set
                //     - look at its adjacent hexes of same type
                //          assertion: they are all unvisited, because this hex was unvisited
                //                     and this is the top-level loop
                //     - if none, done looking at this hex
                //     - remove all adj-of-same-type from unvisited-set
                //     - build a new clump-vector of this + all adj of same type
                //     - grow the clump: iterate through each hex in clump-vector (skip its first hex,
                //       because we already have its adjacent hexes)
                //          precondition: each hex already in the clump set, is not in unvisited-vec
                //          - look at its adjacent unvisited hexes of same type
                //          - if none, done looking at this hex
                //          - remove same-type adjacents from unvisited-set
                //          - insert them into clump-vector (will continue the iteration with them)
                //     - add clump-vector to set-of-all-clumps
                //          OR, immediately check its size vs clumpSize
                //   postcondition: have visited each hex
                // - iterate through set-of-all-clumps
                //      if size >= clumpSize then clumpsNotOK := true. Stop.
                // - read clumpsNotOK.
                 */

                // Actual code along with pseudocode:

                // Using vectors to represent sets.
                //   Sets will contain each hex's index within hexLayout.
                //   We're operating on Integer instances, which is okay because
                //   vector methods such as contains() and remove() test obj.equals()
                //   to determine if the Integer is a member.
                //   (getAdjacent() returns new Integer objs with the same value
                //    as unvisited's members.)

                // - unvisited-set := new set (vector) of all land hexes
                clumpsNotOK = false;    // will set true in while-loop body
                Vector unvisited = new Vector();
                for (int i = 0; i < landHex.length; ++i)
                {
                    unvisited.addElement(new Integer(numPath[i]));
                }

                // - iterate through unvisited-set

                while (unvisited.size() > 0)
                {
                    //   for each hex:

                    //     - remove this from unvisited-set
                    Integer hexIdxObj = (Integer) unvisited.elementAt(0);
                    int hexIdx = hexIdxObj.intValue();
                    int resource = hexLayout[hexIdx];
                    unvisited.removeElementAt(0);

                    //     - look at its adjacent hexes of same type
                    //          assertion: they are all unvisited, because this hex was unvisited
                    //                     and this is the top-level loop
                    //     - if none, done looking at this hex
                    //     - build a new clump-vector of this + all adj of same type
                    //     - remove all adj-of-same-type from unvisited-set

                    // set of adjacent will become the clump, or be emptied completely
                    Vector adjacent = getAdjacentHexesToHex(numToHexID[hexIdx], false);
                    if (adjacent == null)
                        continue;
                    Vector clump = null;
                    for (int i = 0; i < adjacent.size(); ++i)
                    {
                        Integer adjCoordObj = (Integer) adjacent.elementAt(i);
                        int adjIdx = hexIDtoNum[adjCoordObj.intValue()];
                        if (resource == hexLayout[adjIdx])
                        {
                            // keep this one
                            if (clump == null)
                                clump = new Vector();
                            Integer adjIdxObj = new Integer(adjIdx);
                            clump.addElement(adjIdxObj);
                            unvisited.remove(adjIdxObj);
                        }
                    }
                    if (clump == null)
                        continue;
                    clump.insertElementAt(hexIdxObj, 0);  // put the first hex into clump

                    //     - grow the clump: iterate through each hex in clump-vector (skip its first hex,
                    //       because we already have its adjacent hexes)
                    for (int ic = 1; ic < clump.size(); )  // ++ic is within loop body, if nothing inserted
                    {
                        // precondition: each hex already in clump set, is not in unvisited-vec
                        Integer chexIdxObj = (Integer) clump.elementAt(ic);
                        int chexIdx = chexIdxObj.intValue();

                        //  - look at its adjacent unvisited hexes of same type
                        //  - if none, done looking at this hex
                        //  - remove same-type adjacents from unvisited-set
                        //  - insert them into clump-vector
                        //    (will continue the iteration with them)

                        Vector adjacent2 = getAdjacentHexesToHex(numToHexID[chexIdx], false);
                        if (adjacent2 == null)
                        {
                            ++ic;
                            continue;
                        }
                        boolean didInsert = false;
                        for (int ia = 0; ia < adjacent2.size(); ++ia)
                        {
                            Integer adjCoordObj = (Integer) adjacent2.elementAt(ia);
                            int adjIdx = hexIDtoNum[adjCoordObj.intValue()];
                            Integer adjIdxObj = new Integer(adjIdx);
                            if ((resource == hexLayout[adjIdx])
                                && unvisited.contains(adjIdxObj))
                            {
                                // keep this one
                                clump.insertElementAt(adjIdxObj, ic);
                                unvisited.remove(adjIdxObj);
                                didInsert = true;
                            }
                        }
                        if (! didInsert)
                            ++ic;

                    }  // for each in clump

                    //     - immediately check clump's size vs clumpSize
                    if (clump.size() >= clumpSize)
                    {
                        clumpsNotOK = true;
                        break;
                    }

                }  // for each in unvisited

            }  // if (checkClumps)

        } while (clumpsNotOK);

    }  // makeNewBoard_placeHexes

    /**
     * For makeNewBoard, shuffle portHex[].
     * Sets no fields, only rearranges the contents of that array.
     * Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
     * (for ports, the types here are: 3-for-1, or 2-for-1).
     * @param portHex Contains port types, 1 per port, as they will appear in clockwise
     *            order around the edge of the board. Must not all be the same type.
     *            {@link #MISC_PORT} is the 3-for-1 port type value.
     * @param opt_breakClumps Game option "BC", or null
     * @throws IllegalStateException if opt_breakClumps is set, and all portHex[] elements have the same value
     */
    private void makeNewBoard_shufflePorts(int[] portHex, SOCGameOption opt_breakClumps)
        throws IllegalStateException
    {
        boolean portsOK = true;
        do
        {
            int count, i;
            for (count = 0; count < 10; count++)
            {
                int idx, tmp;
                for (i = 1; i < portHex.length; i++) // don't swap 0 with 0!
                {
                    // Swap a random card below the ith card with the ith card
                    idx = Math.abs(rand.nextInt() % (portHex.length - i));
                    tmp = portHex[idx];
                    portHex[idx] = portHex[i];
                    portHex[i] = tmp;
                }
            }

            if ((opt_breakClumps != null) && opt_breakClumps.getBoolValue())
            {
                // Start with port 0, and go around the circle; after that,
                // check type of highest-index vs 0, for wrap-around.

                portsOK = true;
                int clumpsize = opt_breakClumps.getIntValue();
                boolean ptype = (0 == portHex[0]);
                count = 1;  // # in a row
                for (i = 1; i < portHex.length; ++i)
                {
                    if (ptype != (0 == portHex[i]))
                    {
                        ptype = (0 == portHex[i]);
                        count = 1;
                    } else {
                        ++count;
                        if (count >= clumpsize)
                            portsOK = false;
                            // don't break: need to check them all,
                            // in case all portHex[i] are same value
                    }
                }  // for(i)

                if (ptype == (0 == portHex[0]))
                {
                    // check wrap-around
                    if (count == portHex.length)
                        throw new IllegalStateException("portHex types all same");
                    if (! portsOK)
                        continue;
                    for (i = 0; i < portHex.length; ++i)
                    {
                        if (ptype != (0 == portHex[i]))
                        {
                            break;
                        } else {
                            ++count;
                            if (count >= clumpsize)
                            {
                                portsOK = false;
                                break;
                            }
                        }                        
                    }
                }
            }  // if opt("BC")

        } while (! portsOK);

    }

    /**
     * Auxiliary method for placing the port hexes, changing an element of {@link #hexLayout}
     * and setting 2 elements of {@link #nodeIDtoPortType}.
     * @param port Port type; in range {@link #MISC_PORT} to {@link #WOOD_PORT}.
     * @param hex  Hex coordinate within {@link #hexLayout}, or -1 if {@link #BOARD_ENCODING_6PLAYER}
     * @param face Facing of port; 1 to 6 ({@link #FACING_NE} to {@link #FACING_NW})
     * @param node1 Node coordinate 1 of port
     * @param node2 Node coordinate 2 of port
     */
    private final void placePort(int port, int hex, int face, int node1, int node2)
    {
        if (hex != -1)
        {
            if (port == MISC_PORT)
            {
                // generic port == 6 + facing
                hexLayout[hex] = face + 6;
            }
            else
            {
                hexLayout[hex] = (face << 4) + port;
            }
        }
        nodeIDtoPortType[node1] = port;
        nodeIDtoPortType[node2] = port;
    }

    /**
     * Create and initialize a {@link SOCPlayer}'s legalRoads array.
     *<P>
     * Previously part of {@link SOCPlayer}, but moved here in version 1.1.12
     * to better encapsulate the board coordinate encoding.
     * @since 1.1.12
     */
    boolean[] initPlayerLegalRoads()
    {
        // 6-player starts land 1 extra hex (2 edges) west of standard board,
        // and has an extra row of land hexes at north and south end.
        final boolean is6player =
            (boardEncodingFormat == BOARD_ENCODING_6PLAYER);
        final int westAdj = (is6player) ? 0x22 : 0x00;

        boolean[] legalRoads = new boolean[0xEF];
        for (int i = 0; i < 0xEF; i++)
            legalRoads[i] = false;

        // Set each row of valid road (edge) coordinates:
        int i;

        if (is6player)
        {
            for (i = 0x07; i <= 0x5C; i += 0x11)
                legalRoads[i] = true;

            for (i = 0x06; i <= 0x6C; i += 0x22)
                legalRoads[i] = true;
        }

        for (i = 0x27 - westAdj; i <= 0x7C; i += 0x11)
            legalRoads[i] = true;

        for (i = 0x26 - westAdj; i <= 0x8C; i += 0x22)
            legalRoads[i] = true;

        for (i = 0x25 - westAdj; i <= 0x9C; i += 0x11)
            legalRoads[i] = true;

        for (i = 0x24 - westAdj; i <= 0xAC; i += 0x22)
            legalRoads[i] = true;

        for (i = 0x23 - westAdj; i <= 0xBC; i += 0x11)
            legalRoads[i] = true;

        for (i = 0x22 - westAdj; i <= 0xCC; i += 0x22)
            legalRoads[i] = true;

        for (i = 0x32 - westAdj; i <= 0xCB; i += 0x11)
            legalRoads[i] = true;

        for (i = 0x42 - westAdj; i <= 0xCA; i += 0x22)
            legalRoads[i] = true;

        for (i = 0x52 - westAdj; i <= 0xC9; i += 0x11)
            legalRoads[i] = true;

        for (i = 0x62 - westAdj; i <= 0xC8; i += 0x22)
            legalRoads[i] = true;

        for (i = 0x72 - westAdj; i <= 0xC7; i += 0x11)
            legalRoads[i] = true;

        if (is6player)
        {
            for (i = 0x60; i <= 0xC6; i += 0x22)
                legalRoads[i] = true;

            for (i = 0x70; i <= 0xC5; i += 0x11)
                legalRoads[i] = true;

        }

        return legalRoads;
    }

    /**
     * Create and initialize a {@link SOCPlayer}'s legal settlements array.
     * You can use {@link System#arraycopy(Object, int, Object, int, int)}
     * to copy the returned <tt>legalSettlements[]</tt>
     * to <tt>potentialSettlements[]</tt>.
     *<P>
     * Previously part of {@link SOCPlayer}, but moved here in version 1.1.12
     * to better encapsulate the board coordinate encoding.
     * @since 1.1.12
     * @see #nodesOnBoard
     */
    boolean[] initPlayerLegalAndPotentialSettlements()
    {
        // 6-player starts land 1 extra hex (2 nodes) west of standard board,
        // and has an extra row of land hexes at north and south end.
        final boolean is6player =
            (boardEncodingFormat == BOARD_ENCODING_6PLAYER);
        final int westAdj = (is6player) ? 0x22 : 0x00;

        boolean[] legalSettlements = new boolean[0xFF];
        for (int i = 0; i < 0xFF; i++)
            legalSettlements[i] = false;

        // Set each row of valid node coordinates:
        int i;

        if (is6player)
        {
            for (i = 0x07; i <= 0x6D; i += 0x11)
                legalSettlements[i] = true;
        }

        for (i = 0x27 - westAdj; i <= 0x8D; i += 0x11)
            legalSettlements[i] = true;

        for (i = 0x25 - westAdj; i <= 0xAD; i += 0x11)
            legalSettlements[i] = true;

        for (i = 0x23 - westAdj; i <= 0xCD; i += 0x11)
            legalSettlements[i] = true;

        for (i = 0x32 - westAdj; i <= 0xDC; i += 0x11)
            legalSettlements[i] = true;

        for (i = 0x52 - westAdj; i <= 0xDA; i += 0x11)
            legalSettlements[i] = true;

        for (i = 0x72 - westAdj; i <= 0xD8; i += 0x11)
            legalSettlements[i] = true;

        if (is6player)
        {
            for (i = 0x70; i <= 0xD6; i += 0x11)
                legalSettlements[i] = true;
        }

        return legalSettlements;
    }

    /**
     * @return the hex layout; meaning of values same as {@link #hexLayout}.
     * @see #getHexLandCoords()
     */
    public int[] getHexLayout()
    {
        return hexLayout;
    }

    /**
     * The hex coordinates of all land hexes.
     * @return land hex coordinates, in no particular order.
     * @since 1.1.08
     */
    public int[] getHexLandCoords()
    {
        switch (boardEncodingFormat)
        {
        case BOARD_ENCODING_6PLAYER:
            return HEXCOORDS_LAND_V2;
        default:
            return HEXCOORDS_LAND_V1;
        }        
    }

    /**
     * The dice-number layout of dice rolls at each hex number.
     * @return the number layout; each element is valued 2-12.
     *     The robber hex is 0.  Water hexes are -1. 
     */
    public int[] getNumberLayout()
    {
        return numberLayout;
    }

    /**
     * On the 6-player (v2 layout) board, each port's type, such as {@link #SHEEP_PORT}.
     * Same value range as in {@link #hexLayout}.
     * (In the standard board (v1), these are part of {@link #hexLayout} instead.)
     * Same order as {@link #getPortsFacing()}: Clockwise from upper-left.
     *
     * @return the ports layout, or null if not used in this board encoding format
     * @see #getPortTypeFromNodeCoord(int)
     * @see #getPortCoordinates(int)
     * @since 1.1.08
     */
    public int[] getPortsLayout()
    {
    	return portsLayout;
    }

    /**
     * Each port's <em>facing</em>, such as {@link #FACING_NW}.
     * Port Facing is the direction from the port hex, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     * Same order as {@link #getPortsLayout()}: Clockwise from upper-left.
     * @return the ports' facing
     * @see #getPortsEdges()
     * @since 1.1.8
     */
    public int[] getPortsFacing()
    {
        switch (boardEncodingFormat)
        {
        case BOARD_ENCODING_6PLAYER:
            return PORTS_FACING_V2;
        default:
            return PORTS_FACING_V1;
        }
    }

    /**
     * Each port's edge coordinate.
     * This is the edge whose 2 end nodes can be used to build port settlements/cities.
     * Same order as {@link #getPortsLayout()}: Clockwise from upper-left.
     * @return the ports' edges
     * @see #getPortsFacing()
     * @since 1.1.8
     */
    public int[] getPortsEdges()
    {
        switch (boardEncodingFormat)
        {
        case BOARD_ENCODING_6PLAYER:
            return PORTS_EDGE_V2;
        default:
            return PORTS_EDGE_V1;
        }    
    }

    /**
     * @return coordinate where the robber is
     * @see #getPreviousRobberHex()
     */
    public int getRobberHex()
    {
        return robberHex;
    }

    /**
     * If the robber has been moved by calling {@link #setRobberHex(int, boolean)}
     * where <tt>rememberPrevious == true</tt>, get the previous coordinate
     * of the robber.
     * @return hex coordinate where the robber was, or -1
     * @see #getRobberHex()
     * @since 1.1.11
     */
    public int getPreviousRobberHex()
    {
        return prevRobberHex;
    }

    /**
     * set the board encoding format.
     * Intended for client-side use.
     * @param fmt  Board encoding format number
     * @throws IllegalArgumentException if fmt &lt; 1 or > {@link #MAX_BOARD_ENCODING}
     */
    public void setBoardEncodingFormat(int fmt)
        throws IllegalArgumentException
    {
        if ((fmt < 1) || (fmt > MAX_BOARD_ENCODING))
            throw new IllegalArgumentException("Format out of range: " + fmt);
        boardEncodingFormat = fmt;
    }

    /**
     * set the hexLayout.
     * Please call {@link #setBoardEncodingFormat(int)} first,
     * unless the format is {@link #BOARD_ENCODING_ORIGINAL}.
     *
     * @param hl  the hex layout
     */
    public void setHexLayout(int[] hl)
    {
        hexLayout = hl;

        if (hl[0] == 6)
        {
            /**
             * this is a blank board
             */
            return;
        }

        /**
         * fill in the port node information, if it's part of the hex layout
         */
        if (boardEncodingFormat != BOARD_ENCODING_ORIGINAL)
            return;  // <---- port nodes are outside the hex layout ----

        if (nodeIDtoPortType == null)
        {
            nodeIDtoPortType = new int[MAXNODEPLUSONE];
            for (int i = 0; i <= MAXNODE; ++i)
                nodeIDtoPortType[i] = -1;  // -1 means not a port (or not a valid node coord)
        }
        for (int i = 0; i < PORTS_FACING_V1.length; ++i)
        {
            final int hexnum = PORTS_HEXNUM_V1[i];
            final int ptype = getPortTypeFromHexType(hexLayout[hexnum]);
            final int[] nodes = getAdjacentNodesToEdge_arr(PORTS_EDGE_V1[i]);
            ports[ptype].addElement(new Integer(nodes[0]));
            ports[ptype].addElement(new Integer(nodes[1]));
            nodeIDtoPortType[nodes[0]] = ptype;
            nodeIDtoPortType[nodes[1]] = ptype;
        }
    }

    /**
     * On the 6-player (v2 layout) board, each port's type, such as {@link #SHEEP_PORT}.
     * (In the standard board (v1), these are part of {@link #hexLayout}.)
     * Same order as {@link #PORTS_FACING_V2}: Clockwise from upper-left.
     * @see #getPortsLayout()
     * @since 1.1.08
     */
    public void setPortsLayout(int[] portTypes)
    {
        portsLayout = portTypes;
        if (nodeIDtoPortType == null)
        {
            nodeIDtoPortType = new int[MAXNODEPLUSONE];
            for (int i = 0; i <= MAXNODE; ++i)
                nodeIDtoPortType[i] = -1;  // -1 means not a port (or not a valid node coord)
        }
        for (int i = 0; i < ports.length; ++i)
            ports[i].removeAllElements();
        for (int i = 0; i < PORTS_FACING_V2.length; ++i)
        {
            final int ptype = portTypes[i];
            final int[] nodes = getAdjacentNodesToEdge_arr(PORTS_EDGE_V2[i]);
            placePort(ptype, -1, PORTS_FACING_V2[i], nodes[0], nodes[1]);
            ports[ptype].addElement(new Integer(nodes[0])); 
            ports[ptype].addElement(new Integer(nodes[1])); 
        }
    }

    /**
     * @return the type of port given a hex type;
     *         in range {@link #MISC_PORT} to {@link #WOOD_PORT}.
     *         If called on a non-port hex, returns 0 
     *         (which is <tt>MISC_PORT</tt>).
     * @param hex  the hex type, as in {@link #hexLayout}
     * @see #getHexTypeFromCoord(int)
     * @see #getPortTypeFromNodeCoord(int)
     */
    public int getPortTypeFromHexType(final int hexType)
    {
        int portType = 0;

        if ((hexType >= 7) && (hexType <= 12))
        {
            portType = 0;
        }
        else
        {
            portType = hexType & 0xF;
        }

        return portType;
    }

    /**
     * set the number layout
     *
     * @param nl  the number layout
     */
    public void setNumberLayout(int[] nl)
    {
        numberLayout = nl;
    }

    /**
     * set where the robber is
     *
     * @param rh  the new robber hex coordinate; not validated
     * @param rememberPrevious  Should we remember the old robber hex? (added in 1.1.11)
     * @see #getRobberHex()
     * @see #getPreviousRobberHex()
     */
    public void setRobberHex(final int rh, final boolean rememberPrevious)
    {
        if (rememberPrevious)
            prevRobberHex = robberHex;
        else
            prevRobberHex = -1;
        robberHex = rh;
    }

    /**
     * @return the list of coordinates for a type of port;
     *         each element is an Integer
     *
     * @param portType  the type of port;
     *        in range {@link #MISC_PORT} to {@link #WOOD_PORT}.
     * @see #getPortTypeFromNodeCoord(int)
     * @see #getPortsLayout()
     */
    public Vector getPortCoordinates(int portType)
    {
        return ports[portType];
    }

    /**
     * Get a port's <em>facing</em>.
     * Facing is the direction from the port hex, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     * @param portNum Port number, in range 0 to n-1,
     *           Ordered clockwise from upper-left.
     * @return facing (1-6), or 0 if not a valid port number.
     *            Facing 2 is {@link #FACING_E}, 3 is {@link #FACING_SE}, 4 is SW, etc.
     * @since 1.1.08
     */
    public int getPortFacing(int portNum)
    {
        int[] pfacings;
        if (boardEncodingFormat == BOARD_ENCODING_ORIGINAL)
            pfacings = PORTS_FACING_V1;
        else
            pfacings = PORTS_FACING_V2;
        if ((portNum >= 0) && (portNum < pfacings.length))
            return pfacings[portNum];
        else
            return 0;
    }
    /**
     * What type of port is at this node?
     * @param nodeCoord
     * @return the type of port (in range {@link #MISC_PORT} to {@link #WOOD_PORT}),
     *         or -1 if no port at this node
     * @see #getPortTypeFromHexType(int)
     * @since 1.1.08
     */
    public int getPortTypeFromNodeCoord(final int nodeCoord)
    {
        if ((nodeIDtoPortType != null)
            && (nodeCoord >= 0) && (nodeCoord < nodeIDtoPortType.length))
            return nodeIDtoPortType[nodeCoord];
        else
            return -1;
    }

    /**
     * Given a hex coordinate, return the number on that hex
     *
     * @param hex  the coordinates for a hex
     *
     * @return the number on that hex, or 0 if not a hex coordinate
     */
    public int getNumberOnHexFromCoord(final int hex)
    {
        if ((hex >= 0) && (hex < hexIDtoNum.length))
            return getNumberOnHexFromNumber(hexIDtoNum[hex]);
        else
            return 0;
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
        if ((hex < 0) || (hex >= numberLayout.length))
            return 0;
        int num = numberLayout[hex];

        if (num < 0)
        {
            return 0;
        }
        else
        {
            return num;
        }
    }

    /**
     * Given a hex coordinate, return the hex number
     *
     * @param hexCoord  the coordinates ("ID") for a hex
     * @return the hex number, or -1 if hexCoord isn't a hex coordinate on the board
     * @see #getHexTypeFromCoord(int)
     * @since 1.1.08
     */
    public int getHexNumFromCoord(final int hexCoord)
    {
        if ((hexCoord >= 0) && (hexCoord <= hexIDtoNum.length))
            return hexIDtoNum[hexCoord];
        else
            return -1;
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
        return getHexTypeFromNumber(hexIDtoNum[hex]);
    }

    /**
     * Given a hex number, return the type of hex
     *
     * @param hex  the number of a hex (its index in {@link #hexLayout}, not its coordinate), or -1 for invalid
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
        if ((hex < 0) || (hex >= hexLayout.length))
            return -1;
        final int hexType = hexLayout[hex];

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
     * put a piece on the board
     * @param pp  Piece to place on the board; coordinates are not checked for validity
     */
    public void putPiece(SOCPlayingPiece pp)
    {
        pieces.addElement(pp);

        switch (pp.getType())
        {
        case SOCPlayingPiece.ROAD:
            roads.addElement(pp);

            break;

        case SOCPlayingPiece.SETTLEMENT:
            settlements.addElement(pp);

            break;

        case SOCPlayingPiece.CITY:
            cities.addElement(pp);

            break;
        }
    }

    /**
     * remove a piece from the board
     */
    public void removePiece(SOCPlayingPiece piece)
    {
        Enumeration pEnum = pieces.elements();

        while (pEnum.hasMoreElements())
        {
            SOCPlayingPiece p = (SOCPlayingPiece) pEnum.nextElement();

            if ((piece.getType() == p.getType()) && (piece.getCoordinates() == p.getCoordinates()))
            {
                pieces.removeElement(p);

                switch (piece.getType())
                {
                case SOCPlayingPiece.ROAD:
                    roads.removeElement(p);

                    break;

                case SOCPlayingPiece.SETTLEMENT:
                    settlements.removeElement(p);

                    break;

                case SOCPlayingPiece.CITY:
                    cities.removeElement(p);

                    break;
                }

                break;
            }
        }
    }

    /**
     * get the list of pieces on the board
     */
    public Vector getPieces()
    {
        return pieces;
    }

    /**
     * get the list of roads
     */
    public Vector getRoads()
    {
        return roads;
    }

    /**
     * get the list of settlements
     */
    public Vector getSettlements()
    {
        return settlements;
    }

    /**
     * get the list of cities
     */
    public Vector getCities()
    {
        return cities;
    }

    /**
     * Width of this board in coordinates (not in number of hexes across.)
     * For the default size, see {@link #BOARD_ENCODING_ORIGINAL}.
     * @since 1.1.06
     */
    public int getBoardWidth()
    {
        return boardWidth;
    }

    /**
     * Height of this board in coordinates (not in number of hexes across.)
     * For the default size, see {@link #BOARD_ENCODING_ORIGINAL}.
     * @since 1.1.06
     */
    public int getBoardHeight()
    {
        return boardHeight;
    }

    /**
     * Get the encoding format of this board (for coordinates, etc).
     * See the encoding constants' javadocs for more documentation.
     * @return board coordinate-encoding format, such as {@link #BOARD_ENCODING_ORIGINAL}
     * @since 1.1.06
     */
    public int getBoardEncodingFormat()
    {
        return boardEncodingFormat;
    }

    /**
     * Get the minimum node coordinate in this board encoding format.
     * Note that the maximum is currently {@link #MAXNODE}, so it has no getter.
     * @return minimum possible node coordinate
     * @since 1.1.08
     */
    public int getMinNode()
    {
        return minNode;
    }

    /**
     * Get the minimum edge coordinate in this board encoding format.
     * @return minimum possible edge coordinate
     * @since 1.1.11
     */
    public int getMinEdge()
    {
        return minEdge;
    }

    /**
     * Get the maximum edge coordinate in this board encoding format.
     * @return maximum possible edge coordinate
     * @since 1.1.11
     */
    public int getMaxEdge()
    {
        return minEdge;
    }

    /**
     * Adjacent node coordinates to an edge, within range {@link #getMinNode()} to {@link #MAXNODE}.
     * @return the nodes that touch this edge, as a Vector of Integer coordinates
     * @see #getAdjacentNodesToEdge_arr(int)
     */
    public Vector getAdjacentNodesToEdge(final int coord)
    {
        Vector nodes = new Vector(2);
        final int[] narr = getAdjacentNodesToEdge_arr(coord);
        if ((narr[0] >= minNode) && (narr[0] <= MAXNODE))
            nodes.addElement(new Integer(narr[0]));
        if ((narr[1] >= minNode) && (narr[1] <= MAXNODE))
            nodes.addElement(new Integer(narr[1]));
        return nodes;
    }

    /**
     * Adjacent node coordinates to an edge.
     * Does not check against range {@link #MINNODE_V1} to {@link #MAXNODE},
     * so nodes in the water (off the land board) may be returned.
     * @return the nodes that touch this edge, as an array of 2 integer coordinates
     * @see #getAdjacentNodesToEdge(int)
     * @since 1.1.08
     */
    public static int[] getAdjacentNodesToEdge_arr(final int coord)
    {
        int[] nodes = new int[2];

        /**
         * if the coords are (even, even), then
         * the road is '|'.
         */
        if ((((coord & 0x0F) + (coord >> 4)) % 2) == 0)
        {
            nodes[0] = coord + 0x01;
            nodes[1] = coord + 0x10;
        }
        else
        {
            /* otherwise the road is either '/' or '\' */
            nodes[0] = coord;
            nodes[1] = coord + 0x11;
        }

        return nodes;
    }

    /**
     * @param coord  Edge coordinate; for the 6-player encoding, use 0, not -1, for edge 0x00.
     * @return the adjacent edges to this edge, as a Vector of Integer coordinates
     */
    public Vector getAdjacentEdgesToEdge(int coord)
    {
        Vector edges = new Vector(4);
        int tmp;

        /**
         * if the coords are (even, even), then
         * the road is '|'.
         */
        if ((((coord & 0x0F) + (coord >> 4)) % 2) == 0)
        {
            tmp = coord - 0x10;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x01;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x10;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord - 0x01;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }
        }

        /**
         * if the coords are (even, odd), then
         * the road is '/'.
         */
        else if (((coord >> 4) % 2) == 0)
        {
            tmp = coord - 0x11;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x01;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x11;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord - 0x01;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }
        }
        else
        {
            /**
             * otherwise the coords are (odd, even),
             * and the road is '\'
             */
            tmp = coord - 0x10;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x11;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x10;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord - 0x11;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(new Integer(tmp));
            }
        }

        return edges;
    }

    /**
     * @return the coordinates (Integers) of the 1 to 3 hexes touching this node
     */
    public static Vector getAdjacentHexesToNode(int coord)
    {
        Vector hexes = new Vector(3);
        int tmp;

        /**
         * if the coords are (even, odd), then
         * the node is 'Y'.
         */
        if (((coord >> 4) % 2) == 0)
        {
            tmp = coord - 0x10;

            if ((tmp >= MINHEX) && (tmp <= MAXHEX))
            {
                hexes.addElement(new Integer(tmp));                
            }

            tmp = coord + 0x10;

            if ((tmp >= MINHEX) && (tmp <= MAXHEX))
            {
                hexes.addElement(new Integer(tmp));
            }

            tmp = coord - 0x12;

            if ((tmp >= MINHEX) && (tmp <= MAXHEX))
            {
                hexes.addElement(new Integer(tmp));
            }
        }
        else
        {
            /**
             * otherwise the coords are (odd, even),
             * and the node is 'upside down Y'.
             */
            tmp = coord - 0x21;

            if ((tmp >= MINHEX) && (tmp <= MAXHEX))
            {
                hexes.addElement(new Integer(tmp));
            }

            tmp = coord + 0x01;

            if ((tmp >= MINHEX) && (tmp <= MAXHEX))
            {
                hexes.addElement(new Integer(tmp));
            }

            tmp = coord - 0x01;

            if ((tmp >= MINHEX) && (tmp <= MAXHEX))
            {
                hexes.addElement(new Integer(tmp));
            }
        }

        return hexes;
    }

    /**
     * Get the valid edge coordinates adjacent to this node.
     * @return the edges touching this node, as a Vector of Integer coordinates
     * @see #getAdjacentEdgeToNode(int, int)
     */
    public Vector getAdjacentEdgesToNode(final int coord)
    {
        Vector edges = new Vector(3);
        int[] edgea = getAdjacentEdgesToNode_arr(coord);
        for (int i = edgea.length - 1; i>=0; --i)
            if (edgea[i] != -9)
                edges.addElement(new Integer(edgea[i]));
        return edges;
    }

    /**
     * Get the valid edge coordinates adjacent to this node.
     * In the 6-player layout, valid land nodes/edges are
     * found on the outer ring of the board coordinate
     * system, but some of their adjacent nodes/edges may be
     * "off the board" and thus invalid.
     * @param coord  Node coordinate.  Is not checked for validity.
     * @return the edges touching this node, as an array of 3 coordinates.
     *    Unused elements of the array are set to -9.
     * @since 1.1.08
     */
    public final int[] getAdjacentEdgesToNode_arr(final int coord)
    {
        int[] edges = new int[3];
        for (int i = 0; i < 3; ++i)
            edges[i] = getAdjacentEdgeToNode(coord, i);

        return edges;
    }

    /**
     * Given a node, get the valid adjacent edge in a given direction, if any.
     *<P>
     * In the 6-player layout, valid land nodes/edges are
     * found on the outer ring of the board coordinate
     * system, but some of their adjacent nodes/edges may be
     * "off the board" and thus invalid.
     *
     * @param nodeCoord  Node coordinate to go from; not checked for validity.
     * @param nodeDir  0 for northwest or southwest; 1 for northeast or southeast;
     *     2 for north or south
     * @return  The adjacent edge in that direction, or -9 if none (if off the board)
     * @throws IllegalArgumentException if <tt>nodeDir</tt> is less than 0 or greater than 2
     * @since 1.1.12
     * @see #getAdjacentEdgesToNode(int)
     * @see #getEdgeBetweenAdjacentNodes(int, int)
     * @see #getAdjacentNodeToNode(int, int)
     */
    public int getAdjacentEdgeToNode(final int nodeCoord, final int nodeDir)
        throws IllegalArgumentException
    {
        // See RST dissertation figures A.2 (nodes), A.3 (edges),
        // and A.8 and A.10 (computing adjacent edges to a node).

        // Bounds checks:
        // - For west and east edges of board:
        //   minEdge,maxEdge can cover "tens" hex-digit.
        //   Use 0x0F to check the units hex-digit.
        // - For north and south edges of board:
        //   Use % 0x22 to check.  (Coordinates of
        //   nodes going east are +0x22 at each hex.)

        /**
         * if the coords are (even, odd), then
         * the node is 'Y'.
         * otherwise the coords are (odd, even),
         * and the node is 'upside down Y' (or 'A').
         */
        final boolean evenOddHex = (((nodeCoord >> 4) % 2) == 0);

        final int tmp, edge;
        switch (nodeDir)
        {
        case 0:  // NW or SW
            if (evenOddHex)
            {
                // upper left '\' edge;
                // minEdge covers bounds-check, units digit will never be 0 (since it's odd)
                tmp = nodeCoord - 0x11;
                if ((tmp >= minEdge) && (tmp <= maxEdge))
                    edge = tmp;
                else
                    edge = -9;
            } else {
                // lower left '/' edge
                tmp = nodeCoord - 0x11;
                if (((nodeCoord & 0x0F) > 0) && (tmp >= minEdge) && (tmp <= maxEdge))
                    edge = tmp;
                else
                    edge = -9;              
            }
            break;

        case 1:  // NE or SE
            if (evenOddHex)
            {
                // upper right '/' edge
                tmp = nodeCoord;
                if (((nodeCoord & 0x0F) < 0x0D) && (tmp >= minEdge) && (tmp <= maxEdge))
                    edge = tmp;
                else
                    edge = -9;
            } else {
                // lower right '\' edge; maxEdge covers the bounds-check
                tmp = nodeCoord;
                if ((tmp >= minEdge) && (tmp <= maxEdge))
                    edge = tmp;
                else
                    edge = -9;                
            }
            break;

        case 2:  // N or S
            if (evenOddHex)
            {
                // Southernmost row of Y-nodes stats at 0x81 and moves += 0x22 to the east.
                // lower middle '|' edge
                boolean hasSouthernEdge = (nodeCoord < 0x81) || (0 != ((nodeCoord - 0x81) % 0x22));
                tmp = nodeCoord - 0x01;
                if (hasSouthernEdge && (0 < (nodeCoord & 0x0F)) && (tmp >= minEdge) && (tmp <= maxEdge))
                    edge = tmp;
                else
                    edge = -9;
            } else {
                // Northernmost row of A-nodes stats at 0x18 and moves += 0x22 to the east.
                // upper middle '|' edge
                boolean hasNorthernEdge = (nodeCoord < 0x18) || (nodeCoord > 0x7E)
                  || (0 != ((nodeCoord - 0x18) % 0x22));
                tmp = nodeCoord - 0x10;
                if (hasNorthernEdge && (tmp >= minEdge) && (tmp <= maxEdge))
                    edge = tmp;
                else
                    edge = -9;
            }
            break;

        default:
            throw new IllegalArgumentException("nodeDir out of range: " + nodeDir);
        }

        return edge;
    }

    /**
     * Given a pair of adjacent node coordinates, get the edge coordinate
     * that connects them.
     *<P>
     * Does not check actual roads or other pieces on the board, only uses the
     * calculations in Robert S Thomas' dissertation figures A.7 - A.10.
     *
     * @param nodeA  Node coordinate adjacent to <tt>nodeB</tt>; not checked for validity
     * @param nodeB  Node coordinate adjacent to <tt>nodeA</tt>; not checked for validity
     * @return edge coordinate, or -9 if <tt>nodeA</tt> and <tt>nodeB</tt> aren't adjacent
     * @since 1.1.12
     * @see #getAdjacentEdgesToNode(int)
     * @see #getAdjacentEdgeToNode(int, int)
     */
    public int getEdgeBetweenAdjacentNodes(final int nodeA, final int nodeB)
    {
        // A.7:  Adjacent hexes and nodes to an [Even,Odd] Node
        // A.9:  Adjacent hexes and nodes to an [Odd,Even] Node
        // A.8:  Adjacent edges to an [Even,Odd] Node
        // A.10: Adjacent edges to an [Odd,Even] Node

        final int edge;

        switch (nodeA - nodeB)  // nodeB to nodeA: fig A.7, A.9
        {
        case 0x11:
            // Edge is NW or SW of nodeA (fig A.8, A.10)
            // so it's (-1,-1), but we know nodeB == nodeA + (1,1)
            // so, each +1 and -1 cancel out if we use nodeB's coordinate.
            edge = nodeB;
            break;

        case -0x11:
            // Edge is NE or SE of nodeA
            edge = nodeA;
            break;

        case 0x0F:  // +0x10, -0x01 for (+1,-1)
            // it is a '|' road for an 'A' node
            // So: edge is north of nodeA (fig A.10)
            edge = (nodeA - 0x10);
            break;

        case -0x0F:  // -0x10, +0x01 for (-1,+1)
            // it is a '|' road for a 'Y' node
            // So: edge is south of nodeA (fig A.8)
            edge = (nodeA - 0x01);
            break;

        default:
            edge = -9;  // not adjacent nodes
        }

        return edge;
    }

    /**
     * Determine if this node and edge are adjacent.
     * Checking is not as strict as in {@link #getAdjacentEdgesToNode(int)},
     * so there may be a false positive, but not a false negative.
     *
     * @param nodeCoord  Node coordinate; not bounds-checked
     * @param edgeCoord  Edge coordinate; checked against minEdge, maxEdge.
     *   For the 6-player encoding, use 0, not -1, to indicate edge 0x00.
     * @return  is the edge adjacent?
     * @since 1.1.11
     * @see #getEdgeBetweenAdjacentNodes(int, int)
     */
    public boolean isEdgeAdjacentToNode(final int nodeCoord, final int edgeCoord)
    {
        if ((edgeCoord < minEdge) || (edgeCoord > maxEdge))
            return false;

        // See dissertation figures A.8, A.10
        if ((edgeCoord == nodeCoord) || (edgeCoord == (nodeCoord - 0x11)))
            return true;
        /**
         * if the coords are (even, odd), then
         * the node is 'Y' (figure A.8), otherwise is 'A' (figure A.10).
         */
        if (((nodeCoord & 0x0F) % 2) == 1)
            return (edgeCoord == (nodeCoord - 0x01));  // (even, odd)
        else
            return (edgeCoord == (nodeCoord - 0x10));  // (odd, even)
    }

    /**
     * Get the valid node coordinates adjacent to this node.
     * @return the nodes adjacent to this node, as a Vector of Integer coordinates
     * @see #getAdjacentNodeToNode(int, int)
     */
    public Vector getAdjacentNodesToNode(final int coord)
    {
        Vector nodes = new Vector(3);
        int[] nodea = getAdjacentNodesToNode_arr(coord);
        for (int i = nodea.length - 1; i>=0; --i)
            if (nodea[i] != -9)
                nodes.addElement(new Integer(nodea[i]));
        return nodes;
    }

    /**
     * Get the valid node coordinates adjacent to this node.
     * In the 6-player layout, valid land nodes/edges are
     * found on the outer ring of the board coordinate
     * system, but some of their adjacent nodes/edges may be
     * "off the board" and thus invalid.
     * @param coord  Node coordinate.  Is not checked for validity.
     * @return the nodes touching this node, as an array of 3 coordinates.
     *    Unused elements of the array are set to -9.
     * @since 1.1.08
     */
    public final int[] getAdjacentNodesToNode_arr(final int coord)
    {
        int nodes[] = new int[3];
        for (int i = 0; i < 3; ++i)
            nodes[i] = getAdjacentNodeToNode(coord, i);

        return nodes;
    }

    /**
     * Given a node, get the valid adjacent node in a given direction, if any.
     *<P>
     * In the 6-player layout, valid land nodes/edges are
     * found on the outer ring of the board coordinate
     * system, but some of their adjacent nodes/edges may be
     * "off the board" and thus invalid.
     *
     * @param nodeCoord  Node coordinate to go from; not checked for validity.
     * @param nodeDir  0 for northwest or southwest; 1 for northeast or southeast;
     *     2 for north or south
     * @return  The adjacent node in that direction, or -9 if none (if off the board)
     * @throws IllegalArgumentException if <tt>nodeDir</tt> is less than 0 or greater than 2
     * @since 1.1.12
     * @see #getAdjacentNodesToNode(int)
     * @see #getAdjacentNodeToNode2Away(int, int)
     * @see #getAdjacentEdgeToNode(int, int)
     */
    public int getAdjacentNodeToNode(final int nodeCoord, final int nodeDir)
        throws IllegalArgumentException
    {
        // See RST dissertation figures A.2 (nodes)
        // and A.7 and A.9 (computing adjacent nodes to a node).

        // Both 'Y' nodes and 'A' nodes have adjacent nodes
        // to east at (+1,+1) and west at (-1,-1).
        // Offset to third adjacent node varies.

        final int tmp, node;
        switch (nodeDir)
        {
        case 0:  // NW or SW
            tmp = nodeCoord - 0x11;
            if ((tmp >= minNode) && (tmp <= MAXNODE) && ((nodeCoord & 0x0F) > 0))
                node = tmp;
            else
                node = -9;
            break;

        case 1:  // NE or SE
            tmp = nodeCoord + 0x11;
            if ((tmp >= minNode) && (tmp <= MAXNODE) && ((nodeCoord & 0x0F) < 0xD))
                node = tmp;
            else
                node = -9;
            break;

        case 2:  // N or S
            /**
             * if the coords are (even, odd), then
             * the node is 'Y'.
             */
            if (((nodeCoord >> 4) % 2) == 0)
            {
                // Node directly to south of coord.
                // Southernmost row of Y-nodes stats at 0x81 and moves += 0x22 to the east.
                boolean hasSouthernEdge = (nodeCoord < 0x81) || (0 != ((nodeCoord - 0x81) % 0x22));
                tmp = (nodeCoord + 0x10) - 0x01;
                if (hasSouthernEdge && (tmp >= minNode) && (tmp <= MAXNODE))
                    node = tmp;
                else
                    node = -9;
            }
            else
            {
                /**
                 * otherwise the coords are (odd, even),
                 * and the node is 'upside down Y' ('A').
                 */
                // Node directly to north of coord.
                // Northernmost row of A-nodes stats at 0x18 and moves += 0x22 to the east.
                boolean hasNorthernEdge = (nodeCoord < 0x18) || (nodeCoord > 0x7E)
                  || (0 != ((nodeCoord - 0x18) % 0x22));
                tmp = nodeCoord - 0x10 + 0x01;
                if (hasNorthernEdge && (tmp >= minNode) && (tmp <= MAXNODE))
                    node = tmp;
                else
                    node = -9;
            }
            break;

        default:
            throw new IllegalArgumentException("nodeDir out of range: " + nodeDir);
        }

        return node;
    }

    /**
     * Offsets from a node to another node 2 away,
     * indexed by the facing directions: {@link #FACING_NE} is 1,
     * {@link #FACING_E} is 2, etc; {@link #FACING_NW} is 6.
     * 
     * @param nodeCoord  Starting node's coordinate
     * @param facing    Facing from node; 1 to 6.
     *           This will be one of the 6 directions
     *           from a node to another node 2 away.
     *           Facing 2 is {@link #FACING_E}, 3 is {@link #FACING_SE}, 4 is SW, etc.
     * @return the node coordinate, or -9 if that node is not
     *   {@link #isNodeOnBoard(int) on the board}.
     * @see #getAdjacentNodeToNode(int, int)
     * @see #getAdjacentEdgeToNode2Away(int, int)
     * @see #isNode2AwayFromNode(int, int)
     * @throws IllegalArgumentException if facing < 1 or facing &gt; 6
     * @since 1.1.12
     */
    public int getAdjacentNodeToNode2Away(final int nodeCoord, final int facing)
        throws IllegalArgumentException
    {
        if ((facing < 1) || (facing > 6))
            throw new IllegalArgumentException("bad facing: " + facing);

        // See RST dissertation figure A.2.
        int node = nodeCoord + NODE_2_AWAY[facing];
        if (! isNodeOnBoard(node))
            node = -9;
        return node;
    }

    /**
     * Determine if these 2 nodes are 2 nodes apart on the board,
     * by the node coordinate arithmetic.
     *
     * @param n1  Node coordinate; not validated
     * @param n2  Node coordinate; not validated
     * @return are these nodes 2 away from each other?
     * @see #getAdjacentNodeToNode2Away(int, int)
     * @since 1.1.12
     */
    public boolean isNode2AwayFromNode(final int n1, final int n2)
    {
        final int d = n2 - n1;
        for (int facing = 1; facing <= 6; ++facing)
        {
            if (d == NODE_2_AWAY[facing])
                return true;
        }
        return false;
    }

    /**
     * Given an initial node, and a second node 2 nodes away,
     * calculate the edge coordinate (adjacent to the initial
     * node) going towards the second node.
     * @param node  Initial node coordinate; not validated
     * @param node2away  Second node coordinate; should be 2 away,
     *     but this is not validated
     * @return  An edge coordinate, adjacent to initial node,
     *   in the direction of the second node.
     * @see #getAdjacentNodeToNode2Away(int, int)
     * @since 1.1.12
     */
    public int getAdjacentEdgeToNode2Away
        (final int node, final int node2away)
    {
        final int roadEdge;

        /**
         * See RST dissertation figures A.2, A.8, A.10.
         *
         * if the coords are (even, odd), then
         * the node is 'Y'.
         */
        if (((node >> 4) % 2) == 0)
        {
            if ((node2away == (node - 0x02)) || (node2away == (node + 0x20)))
            {
                // south
                roadEdge = node - 0x01;
            }
            else if (node2away < node)
            {
                // NW
                roadEdge = node - 0x11;
            }
            else
            {
                // NE
                roadEdge = node;
            }
        }
        else
        {
            if ((node2away == (node - 0x20)) || (node2away == (node + 0x02)))
            {
                // north
                roadEdge = node - 0x10;
            }
            else if (node2away > node)
            {  // SE
                roadEdge = node;
            }
            else
            {  // SW
                roadEdge = node - 0x11;
            }
        }
        return roadEdge;
    }

    /**
     * Make a list of all valid hex coordinates (or, only land) adjacent to this hex.
     * Valid coordinates are those within the board data structures,
     * within {@link #MINHEX} to {@link #MAXHEX}, and valid according to {@link #getHexNumFromCoord(int)}.
     *<P>
     * Coordinate offsets, from Dissertation figure A.4 - adjacent hexes to hex:<PRE>
     *    (-2,0)   (0,+2)
     *
     * (-2,-2)   x    (+2,+2)
     *
     *    (0,-2)   (+2,0)  </PRE>
     *
     * @param hexCoord Coordinate ("ID") of this hex
     * @param includeWater Should water hexes be returned (not only land ones)?
     *         Port hexes are water hexes.
     * @return the hexes that touch this hex, as a Vector of Integer coordinates,
     *         or null if none are adjacent (will <b>not</b> return a 0-length vector)
     * @since 1.1.07
     */
    public Vector getAdjacentHexesToHex(final int hexCoord, final boolean includeWater)
    {
        Vector hexes = new Vector();

        getAdjacentHexes_AddIfOK(hexes, includeWater, hexCoord, -2,  0);  // NW (northwest)
        getAdjacentHexes_AddIfOK(hexes, includeWater, hexCoord,  0, +2);  // NE
        getAdjacentHexes_AddIfOK(hexes, includeWater, hexCoord, -2, -2);  // W
        getAdjacentHexes_AddIfOK(hexes, includeWater, hexCoord, +2, +2);  // E
        getAdjacentHexes_AddIfOK(hexes, includeWater, hexCoord,  0, -2);  // SW
        getAdjacentHexes_AddIfOK(hexes, includeWater, hexCoord,  2,  0);  // SE

        if (hexes.size() > 0)
            return hexes;
        else
            return null;
    }

    /**
     * Check one possible coordinate for getAdjacentHexesToHex.
     * @param addTo the list we're building of hexes that touch this hex, as a Vector of Integer coordinates.
     * @param includeWater Should water hexes be returned (not only land ones)?
     *         Port hexes are water hexes.
     * @param hexCoord Coordinate ("ID") of this hex
     * @param d1  Delta along axis 1
     * @param d2  Delta along axis 2
     * @since 1.1.07
     */
    private final void getAdjacentHexes_AddIfOK
        (Vector addTo, final boolean includeWater, int hexCoord, final int d1, final int d2)
    {
        int a1 = ((hexCoord & 0xF0) >> 4) + d1;  // Axis-1 coordinate
        int a2 = (hexCoord & 0x0F) + d2;         // Axis-2 coordinate
        if ((a1 < 1) || (a1 > 0xD) || (a2 < 1) || (a2 > 0xD))
            return;  // <--- Off the board in one coordinate ----

        hexCoord += (d1 << 4);  // this shift works for both + and - (confirmed by testing)
        hexCoord += d2;
        if ((hexCoord >= MINHEX) && (hexCoord <= MAXHEX)
            && (hexIDtoNum[hexCoord] != -1)
            && (includeWater
                || hexLayout[hexIDtoNum[hexCoord]] <= MAX_LAND_HEX))
            addTo.addElement(new Integer(hexCoord));
    }

    /**
     * The node coordinate adjacent to this hex in a given direction.
     * Since all hexes have 6 nodes, all node coordinates are valid so long as
     * the hex coordinate is valid.
     *
     * @param hexCoord Coordinate ("ID") of this hex
     * @param dir  Direction, clockwise from top (northern point of hex):
     *           0 is north, 1 is northeast, etc, 5 is northwest.
     * @return Node coordinate in that direction
     * @since 1.1.08
     * @throws IllegalArgumentException if dir < 0 or dir &gt; 5
     */
    public int getAdjacentNodeToHex(final int hexCoord, final int dir)
        throws IllegalArgumentException
    {
        if ((dir >= 0) && (dir < HEXNODES.length))
            return hexCoord + HEXNODES[dir];
        else
            throw new IllegalArgumentException("dir");
    }

    /**
     * The hex touching an edge in a given direction,
     * either along its length or at one end node.
     * @param edgeCoord The edge's coordinate. {@link #maxEdge} is 0xCC in v1 and v2 encoding.
     * @param facing  Facing from edge; 1 to 6.
     *           This will be either a direction perpendicular to the edge,
     *           or towards one end. Each end has two facing directions angled
     *           towards it; both will return the same hex.
     *           Facing 2 is {@link #FACING_E}, 3 is {@link #FACING_SE}, 4 is SW, etc.
     * @return hex coordinate of hex in the facing direction,
     *           or 0 if a hex digit would be below 0 after subtraction
     *           or above F after addition.
     * @throws IllegalArgumentException if facing < 1 or facing &gt; 6
     * @since 1.1.08
     */
    public int getAdjacentHexToEdge(final int edgeCoord, final int facing)
        throws IllegalArgumentException
    {
        int hex = 0;

        // Calculated as in RST dissertation figures A.11 - A.13,
        // and A.1 / A.3 for hex/edge coordinates past ends of the edge.
        // No valid edge has an F digit, so we need only
        // bounds-check subtraction vs 0, and addition+2 vs D.

        /**
         * if the coords are (even, even), then
         * the edge is '|'.  (Figure A.13)
         */
        if ((((edgeCoord & 0x0F) + (edgeCoord >> 4)) % 2) == 0)
        {
            switch (facing)
            {
            case FACING_E:
                hex = edgeCoord + 0x11;
                break;
            case FACING_W:
                if ((0 != (edgeCoord & 0x0F)) && (0 != (edgeCoord & 0xF0)))
                    hex = edgeCoord - 0x11;
                break;
            case FACING_NE: case FACING_NW:
                if (0 != (edgeCoord & 0xF0))
                    hex = edgeCoord + 0x01 - 0x10;
                break;
            case FACING_SE: case FACING_SW:
                if (0 != (edgeCoord & 0x0F))
                    hex = edgeCoord - 0x01 + 0x10;
                break;
            }
        }

        /**
         * if the coords are (even, odd), then
         * the edge is '/'.  (Figure A.11)
         */
        else if (((edgeCoord >> 4) % 2) == 0)
        {
            switch (facing)
            {
            case FACING_NW:
                if (0 != (edgeCoord & 0xF0))
                    hex = edgeCoord - 0x10;
                break;
            case FACING_SE:
                hex = edgeCoord + 0x10;
                break;
            case FACING_NE: case FACING_E:
                if ((edgeCoord & 0x0F) <= 0xD)
                    hex = edgeCoord + 0x12;
                break;
            case FACING_SW: case FACING_W:
                if ((0 != (edgeCoord & 0xF0))
                    && ((edgeCoord & 0x0F) >= 2))
                    hex = edgeCoord - 0x12;   
                break;
            }
        }
        else
        {
            /**
             * otherwise the coords are (odd, even),
             * and the edge is '\'.  (Figure A.12)
             */
            switch (facing)
            {
            case FACING_NE:
                hex = edgeCoord + 0x01;
                break;
            case FACING_SW:
                if (0 != (edgeCoord & 0x0F))
                    hex = edgeCoord - 0x01;
                break;
            case FACING_E: case FACING_SE:
                if ((edgeCoord >> 4) <= 0xD)
                    hex = edgeCoord + 0x21;
                break;
            case FACING_W: case FACING_NW:
                if ((0 != (edgeCoord & 0x0F))
                    && ((edgeCoord >> 4) >= 2))
                    hex = edgeCoord - 0x21;
                break;
            }
        }

        return hex;
    }

    /**
     * If there's a settlement or city at this node, find it.
     * 
     * @param nodeCoord Location coordinate (as returned by SOCBoardPanel.findNode)
     * @return Settlement or city or null
     */
    public SOCPlayingPiece settlementAtNode(int nodeCoord)
    {
        Enumeration pEnum = pieces.elements();

        while (pEnum.hasMoreElements())
        {
            SOCPlayingPiece p = (SOCPlayingPiece) pEnum.nextElement();
            int typ = p.getType(); 

            if ((nodeCoord == p.getCoordinates()) &&
                ( (typ == SOCPlayingPiece.SETTLEMENT) || (typ == SOCPlayingPiece.CITY) ))
            {
                return p;  // <-- Early return: Found it ---
            }
        }
        
        return null;
    }
    
    /**
     * If there's a road placed at this node, find it.
     * 
     * @param edgeCoord Location coordinate (as returned by SOCBoardPanel.findEdge) 
     * @return road or null
     */
    public SOCPlayingPiece roadAtEdge(int edgeCoord)
    {
        Enumeration pEnum = roads.elements();

        while (pEnum.hasMoreElements())
        {
            SOCPlayingPiece p = (SOCPlayingPiece) pEnum.nextElement();
            if (edgeCoord == p.getCoordinates())
            {
                return p;  // <-- Early return: Found it ---
            }
        }
        
        return null;
    }

    /**
     * @return true if the node is on the land of the board (not water)
     * @param node Node coordinate
     */
    public boolean isNodeOnBoard(int node)
    {
        if (node < 0)
            return false;
        return nodesOnBoard.containsKey(new Integer(node));
    }

    /**
     * @return a string representation of a node coordinate
     */
    public String nodeCoordToString(int node)
    {
        String str;
        Enumeration hexes = getAdjacentHexesToNode(node).elements();
        if (! hexes.hasMoreElements())
        {
            return "(node 0x" + Integer.toHexString(node) + ")";
        }
        Integer hex = (Integer) hexes.nextElement();
        int number = getNumberOnHexFromCoord(hex.intValue());

        if (number == 0)
        {
            str = "-";
        }
        else
        {
            str = Integer.toString(number);
        }

        while (hexes.hasMoreElements())
        {
            hex = (Integer) hexes.nextElement();
            number = getNumberOnHexFromCoord(hex.intValue());

            if (number == 0)
            {
                str += "/-";
            }
            else
            {
                str += ("/" + number);
            }
        }

        return str;
    }

    /**
     * @return a string representation of an edge coordinate
     */
    public String edgeCoordToString(int edge)
    {
        String str;
        int number1;
        int number2;

        /**
         * if the coords are (even, even), then
         * the road is '|'.
         */
        if ((((edge & 0x0F) + (edge >> 4)) % 2) == 0)
        {
            number1 = getNumberOnHexFromCoord(edge - 0x11);
            number2 = getNumberOnHexFromCoord(edge + 0x11);
        }

        /**
         * if the coords are (even, odd), then
         * the road is '/'.
         */
        else if (((edge >> 4) % 2) == 0)
        {
            number1 = getNumberOnHexFromCoord(edge - 0x10);
            number2 = getNumberOnHexFromCoord(edge + 0x10);
        }
        else
        {
            /**
             * otherwise the coords are (odd, even),
             * and the road is '\'
             */
            number1 = getNumberOnHexFromCoord(edge - 0x01);
            number2 = getNumberOnHexFromCoord(edge + 0x01);
        }

        str = number1 + "/" + number2;

        return str;
    }

}
