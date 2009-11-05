/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2009 Jeremy D. Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
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
 * The first axis runs northwest to southeast; the second runs southwest to northeast.
 * Having six sides, hexes run in a straight line west to east, separated by vertical edges;
 * both coordinates increase along a west-to-east line.
 *<P>
 * Current coordinate encoding: ({@link #BOARD_ENCODING_ORIGINAL})
 *<BR>
 * All coordinates are encoded as two-digit hex integers, one digit per axis (thus 00 to FF).
 * The center hex is encoded as 77; see the dissertation PDF's appendix for diagrams.
 * Unfortunately this format means the board can't be expanded without changing its
 * encoding, which is used across the network.
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
     * Facing 2 is E, 3 is SE, 4 is SW, etc: see {@link #hexLayout}.
     * @since 1.1.08
     */
    private final static int PORTS_FACING_V1[] = { 3, 4, 4, 5, 6, 6, 1, 2, 2};

    /**
     * Each port's 2 node coordinates on standard board.
     * Same order as {@link #PORTS_FACING_V1}:
     * Clockwise from upper-left (hex coordinate 0x17).
     * @since 1.1.08
     */
    private final static int PORTS_NODE_V1[] = 
    {
        0x27, 0x38,  // Port touches the upper-left land hex, port facing SE
        0x5A, 0x6B,  // Touches middle land hex of top row, port facing SW
        0x9C, 0xAD,  // Touches rightmost land hex of row above middle, SW
        0xCD, 0xDC,  // Rightmost of middle-row land hex, W
        0xC9, 0xDA,  // Rightmost land hex below middle, NW
        0xA5, 0xB6,  // Port touches middle hex of bottom row, facing NW
        0x72, 0x83,  // Leftmost of bottom row, NE
        0x43, 0x52,  // Leftmost land hex of row below middle, E
        0x25, 0x34   // Leftmost land hex above middle, facing E
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
     * Facing 2 is E, 3 is SE, 4 is SW, etc: see {@link #hexLayout}.
     * @since 1.1.08
     */
    private final static int PORTS_FACING_V2[] =
        { 3, 4, 4, 5, 5, 6, 6, 1, 1, 2, 3 };

    /**
     * Each port's 2 node coordinates on 6-player board.
     * Same order as {@link #PORTS_FACING_V2}:
     * Clockwise from upper-left (hex coordinate 0x17, which is land in the V2 layout).
     * @since 1.1.08
     */
    private final static int PORTS_NODE_V2[] = 
    {
        0x07, 0x18,  // Port touches the upper-left land hex, port facing SE
        0x3A, 0x4B,  // Touches middle land hex of top row, port facing SW
        0x7C, 0x8D,  // Touches rightmost land hex of row below top, SW
        0xAD, 0xBC,  // Touches rightmost land hex of row above middle, W
        0xCB, 0xDA,  // Touches rightmost land hex of row below middle, W
        0xC7, 0xD8,  // Touches rightmost land hex of row above bottom, NW
        0xA3, 0xB4,  // Touches middle land hex of bottom row, NW
        0x70, 0x81,  // Touches bottom-left land hex, NE
        0x30, 0x41,  // Touches leftmost land hex of row below middle, NE
        0x01, 0x10,  // Leftmost hex of middle row, E
        0x03, 0x14   // Touches leftmost land hex of row above middle, SE
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
     * Ports are not part of {@link #hexLayout} because their
     * coordinates wouldn't fit within 2 hex digits.
     * Instead, see {@link #getPortsLayout()} or {@link #getPortCoordinates(int)}.
     * @since 1.1.08
     */
    public static final int BOARD_ENCODING_6PLAYER = 2;

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
     * The encoding format of board coordinates,
     * or {@link #BOARD_ENCODING_ORIGINAL} (default, original).
     * The board size determines the required encoding format.
     *<UL>
     *<LI> 1 - Original format: hexadecimal 0x00 to 0xFF.
     *       Coordinate range is 0 to 15 (in decimal).
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
    public static final int MAXHEX = 0xDD;  // See also hardcoded checks in {@link #getAdjacentHexes_AddIfOK.

    /**
     * smallest coordinate value for a hex, in the current encoding.
     */
    public static final int MINHEX = 0x11;

    /**
     * largest coordinate value for an edge, in the current encoding
     */
    public static final int MAXEDGE = 0xCC;

    /**
     * smallest coordinate value for an edge, in the current encoding
     */
    public static final int MINEDGE = 0x22;

    /**
     * largest coordinate value for a node, in the current encoding
     */
    public static final int MAXNODE = 0xDC;

    /**
     * smallest coordinate value for a node, in the current encoding
     */
    public static final int MINNODE = 0x23;

    /**
     * largest coordinate value for a node plus one, in the current encoding
     */
    public static final int MAXNODEPLUSONE = MAXNODE + 1;

    /***************************************
     * Hex data array, one element per water or land (or port, which is special water) hex.
     * Each element's coordinates on the board ("hex ID") is {@link #numToHexID}[i].
     * Each element's value encodes hex type and (if a
     * port) facing. (Facing is defined just below.)
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
       7 : misc port ("3:1") facing 1 ({@link #MISC_PORT} in {@link #getPortTypeFromHex(int)})
       8 : misc port facing 2
       9 : misc port facing 3
       10 : misc port facing 4
       11 : misc port facing 5
       12 : misc port facing 6
       16+: non-misc ("2:1") encoded port
       </pre>
        Non-misc ports are encoded here in binary like this:<pre>
      (port facing, 1-6)        (kind of port)
              \--> [0 0 0][0 0 0 0] <--/       </pre>
        Kind of port:<pre>
        1 : clay  ({@link #CLAY_PORT} in {@link #getPortTypeFromHex(int)})
        2 : ore    {@link #ORE_PORT}
        3 : sheep  {@link #SHEEP_PORT}
        4 : wheat  {@link #WHEAT_PORT}
        5 : wood   {@link #WOOD_PORT}
        </pre>
        <em>Port facing</em> is the edge of the port's hex
        touching land, which contains 2 nodes where player can build a
        port settlement/city; facing is a number 1-6. <pre>
        6___    ___1
            \/\/
            /  \
       5___|    |___2
           |    |
            \  /
        4___/\/\___3  </pre>

         @see #getHexTypeFromNumber(int)
     *
     **/
    private int[] hexLayout =   // initially all WATER_HEX
    {
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, WATER_HEX
    };

    /**
     * On the 6-player (v2 layout) board, each port's type.  Null otherwise.
     * (In the standard (v1) board, these are part of {@link #hexLayout}.) 
     * Initialized in {@link #makeNewBoard(Hashtable)}.
     * @since 1.1.08
     */
    private int[] portsLayout;

    /**
     * Map of dice rolls to values in {@link #numberLayout}
     */
    private int[] boardNum2Num = { -1, -1, 0, 1, 2, 3, 4, -1, 5, 6, 7, 8, 9 };
 
    /**
     * Map of values in {@link #numberLayout} to dice rolls:<pre>
     *    -1 : robber
     *     0 : 2
     *     1 : 3
     *     2 : 4
     *     3 : 5
     *     4 : 6
     *     5 : 8 (7 is skipped)
     *     6 : 9
     *     7 : 10
     *     8 : 11
     *     9 : 12 </pre>
     */
    private int[] num2BoardNum = { 2, 3, 4, 5, 6, 8, 9, 10, 11, 12 };

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
     *  For number value mapping, see {@link #num2BoardNum}.
     *  For coord mapping, see {@link #numToHexID}
     */
    private int[] numberLayout = 
    {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1
    };

    /** Hex coordinates ("IDs") of each hex number ("hex number" means index within
     *  {@link #hexLayout}).  The hexes in here are the board's land hexes and also
     *  the surrounding ring of water/port hexes.
     * @see #hexIDtoNum
     * @see #nodesOnBoard
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
    };

    /**
     * translate hex ID (hex coordinate) to an array index within {@link #hexLayout},
     * which is sometimes called its "hex number".
     * The numbers in here are the board's land hexes and also the surrounding
     * ring of water/port hexes.  Initialized in constructor.  Length is >= {@link #MAXHEX}.
     * A value of -1 means the ID isn't a valid hex number on the board.
     * @see #numToHexID
     */
    private int[] hexIDtoNum;

    /**
     * offset to add to hex coord to get all node coords
     * -- see getAdjacent* methods instead
     * private int[] hexNodes = { 0x01, 0x12, 0x21, 0x10, -0x01, -0x10 };
     */

    /**
     * offset of all hexes adjacent to a node
     * -- @see #getAdjacentHexesToNode(int) instead
     * private int[] nodeToHex = { -0x21, 0x01, -0x01, -0x10, 0x10, -0x12 };
     */

    /**
     * the hex coordinate that the robber is in; placed on desert in constructor
     */
    private int robberHex;

    /**
     * where the ports are; coordinates per port type.
     * Indexes are port types, {@link #MISC_PORT} to {@link #WOOD_PORT}.
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
     * a list of nodes on the board; key is node's Integer coordinate, value is Boolean.
     * nodes on outer edges of surrounding water/ports are not on the board.
     * See dissertation figure A.2.
     */
    protected Hashtable nodesOnBoard;

    /**
     * Create a new Settlers of Catan Board
     */
    public SOCBoard()
    {
        boardWidth = 0x10;
        boardHeight = 0x10;
        boardEncodingFormat = BOARD_ENCODING_ORIGINAL;  // See javadoc of boardEncodingFormat

        robberHex = -1;  // Soon placed on desert

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
        ports = new Vector[6];
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
         * initialize the list of nodes on the board;
         * nodes on outer edges of surrounding water/ports are not on the board.
         * See dissertation figure A.2.
         */
        Boolean t = new Boolean(true);

        for (i = 0x27; i <= 0x8D; i += 0x11)  //  Top horizontal row: each top corner across 3 hexes
        {
            nodesOnBoard.put(new Integer(i), t);
        }

        for (i = 0x25; i <= 0xAD; i += 0x11)  // Next: each top corner of row of 4 / bottom corner of the top 3 hexes
        {
            nodesOnBoard.put(new Integer(i), t);
        }

        for (i = 0x23; i <= 0xCD; i += 0x11)  // Next: top corners of middle row of 5 hexes
        {
            nodesOnBoard.put(new Integer(i), t);
        }

        for (i = 0x32; i <= 0xDC; i += 0x11) // Next: bottom corners of middle row of 5 hexes
        {
            nodesOnBoard.put(new Integer(i), t);
        }

        for (i = 0x52; i <= 0xDA; i += 0x11)  // Bottom corners of row of 4 / top corners of the bottom 3 hexes
        {
            nodesOnBoard.put(new Integer(i), t);
        }

        for (i = 0x72; i <= 0xD8; i += 0x11)  // Last horizontal row: each bottom corner across 3 hexes
        {
            nodesOnBoard.put(new Integer(i), t);
        }
    }

    /**
     * Auxiliary method for initializing part of the hexIDtoNum array.
     * Between begin and end, increment coord by 0x22, which moves 1 hex to the right.
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
     * Shuffle the hex tiles and layout a board
     * @param opts {@link SOCGameOption Game options}, which may affect board size and layout, or null
     * @exception IllegalArgumentException if <tt>opts</tt> calls for a 6-player board, but
     *        the current {@link #getBoardEncodingFormat()} is a larger value than {@link #BOARD_ENCODING_6PLAYER}.
     */
    public void makeNewBoard(Hashtable opts)
    {
        final boolean is6player;
        {
            if (opts != null)
            {
                SOCGameOption opt_6player = (SOCGameOption) opts.get("DEBUG56PLBOARD");
                is6player = (opt_6player != null) && opt_6player.getBoolValue();
                if (is6player)
                {
                    if (boardEncodingFormat > BOARD_ENCODING_6PLAYER)
                        throw new IllegalArgumentException
                            ("Cannot create 6-player when starting from a newer encoding");
                    boardEncodingFormat = BOARD_ENCODING_6PLAYER;
                }
            } else {
                is6player = false;
            }
        }
        int[] landHex = { 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5 };
        int[] number = { 3, 0, 4, 1, 5, 7, 6, 9, 8, 2, 5, 7, 6, 2, 3, 4, 1, 8 };
        int[] numPath = { 29, 30, 31, 26, 20, 13, 7, 6, 5, 10, 16, 23, 24, 25, 19, 12, 11, 17, 18 };

        SOCGameOption opt_breakClumps = (opts != null ? (SOCGameOption)opts.get("BC") : null);

        // shuffle and place the land hexes, numbers, and robber:
        // sets robberHex, contents of hexLayout[] and numberLayout[].
        // Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
        makeNewBoard_placeHexes
            (landHex, numPath, number, opt_breakClumps);

        // copy and shuffle the ports, and check vs game option BC
        final int[] portTypes = (is6player) ? PORTS_TYPE_V2 : PORTS_TYPE_V1;
        int[] portHex = new int[portTypes.length];
    	System.arraycopy(portTypes, 0, portHex, 0, portTypes.length);
        makeNewBoard_shufflePorts
            (portHex, opt_breakClumps);
        if (is6player)
        	portsLayout = portHex;  // No need to remember for 4-player standard layout

        // place the ports (hex numbers and facing) within hexLayout

        /*
        placePort(portHex[0], 0, 3);  // Facing 3 is SE: see hexLayout's javadoc.
                                      //   0 is hex number (index within hexLayout)
        placePort(portHex[1], 2, 4);  // Facing 4 is SW, at hex number 2
        placePort(portHex[2], 8, 4);  // SW
        placePort(portHex[3], 21, 5); // W
        placePort(portHex[4], 32, 6); // NW
        placePort(portHex[5], 35, 6); // NW
        placePort(portHex[6], 33, 1); // NE
        placePort(portHex[7], 22, 2); // E
        placePort(portHex[8], 9, 2);  // E
        */
        for (int i = 0; i < PORTS_FACING_V1.length; ++i)
            placePort(portHex[i], PORTS_HEXNUM_V1[i], PORTS_FACING_V1[i]);

        // fill out the ports[] vectors with node coordinates
        // where a trade port can be placed

        for (int i = 0, ni=0; i < PORTS_FACING_V1.length; ++i)
        {
            ports[portHex[i]].addElement(new Integer(PORTS_NODE_V1[ni]));  ++ni; 
            ports[portHex[i]].addElement(new Integer(PORTS_NODE_V1[ni]));  ++ni; 
        }
        /*
        ports[portHex[0]].addElement(new Integer(0x27));  // Port touches the upper-left land hex, port facing SE
        ports[portHex[0]].addElement(new Integer(0x38));  // [port's hex is NW of the upper-left land hex]

        ports[portHex[1]].addElement(new Integer(0x5A));  // Touches middle land hex of top row, port facing SW
        ports[portHex[1]].addElement(new Integer(0x6B));  // [The port hex itself is 2 to right of prev port hex.]

        ports[portHex[2]].addElement(new Integer(0x9C));  // Touches rightmost land hex of row above middle, SW
        ports[portHex[2]].addElement(new Integer(0xAD));

        ports[portHex[3]].addElement(new Integer(0xCD));  // Rightmost of middle-row land hex, W
        ports[portHex[3]].addElement(new Integer(0xDC));

        ports[portHex[4]].addElement(new Integer(0xC9));  // Rightmost land hex below middle, NW
        ports[portHex[4]].addElement(new Integer(0xDA));

        ports[portHex[5]].addElement(new Integer(0xA5));  // Port touches middle hex of bottom row, facing NW
        ports[portHex[5]].addElement(new Integer(0xB6));  // [The port hex itself is 2 to right of next port hex.]

        ports[portHex[6]].addElement(new Integer(0x72));  // Leftmost of bottom row, NE
        ports[portHex[6]].addElement(new Integer(0x83));

        ports[portHex[7]].addElement(new Integer(0x43));  // Leftmost land hex of row below middle, E
        ports[portHex[7]].addElement(new Integer(0x52));

        ports[portHex[8]].addElement(new Integer(0x25));  // Leftmost land hex above middle, facing E
        ports[portHex[8]].addElement(new Integer(0x34));
        */

    }

    /**
     * For makeNewBoard, place the land hexes, number, and robber,
     * after shuffling landHex[].
     * Sets robberHex, contents of hexLayout[] and numberLayout[].
     * Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
     * (for land hex resource types).
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
                if (landHex[i] == 0)
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
     * Auxiliary method for placing the port hexes, changing an element of {@link #hexLayout}.
     * @param port Port type; in range {@link #MISC_PORT} to {@link #WOOD_PORT}.
     * @param hex  Hex coordinate within {@link #hexLayout}
     * @param face Facing of port; 1 to 6; for facing direction, see {@link #hexLayout}
     */
    private final void placePort(int port, int hex, int face)
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

    /**
     * @return the hex layout; meaning of values same as {@link #hexLayout}.
     */
    public int[] getHexLayout()
    {
        return hexLayout;
    }

    /**
     * @return the number layout
     */
    public int[] getNumberLayout()
    {
        return numberLayout;
    }

    /**
     * On the 6-player (v2 layout) board, each port's type, such as {@link #SHEEP_PORT}.
     * (In the standard board (v1), these are part of {@link #hexLayout}.)
     * Same order as {@link #PORTS_FACING_V2}: Clockwise from upper-left.
     *
     * @return the ports layout, or null otherwise
     * @see #getPortCoordinates(int)
     * @since 1.1.08
     */
    public int[] getPortsLayout()
    {
    	return portsLayout;
    }

    /**
     * @return coordinate where the robber is
     */
    public int getRobberHex()
    {
        return robberHex;
    }

    /**
     * set the hexLayout
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
         * fill in the port node information
         */
        for (int i = 0, ni=0; i < PORTS_FACING_V1.length; ++i)
        {
            int hexnum = PORTS_HEXNUM_V1[i];
            ports[getPortTypeFromHex(hexLayout[hexnum])].addElement(new Integer(PORTS_NODE_V1[ni]));  ++ni;
            ports[getPortTypeFromHex(hexLayout[hexnum])].addElement(new Integer(PORTS_NODE_V1[ni]));  ++ni;
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
    }

    /**
     * @return the type of port given a hex type;
     *         in range {@link #MISC_PORT} to {@link #WOOD_PORT}.
     *         If called on a non-port hex, returns 0 
     *         (which is <tt>MISC_PORT</tt>).
     * @param hex  the hex type, as in {@link #hexLayout}
     * @see #getHexTypeFromCoord(int)
     */
    public int getPortTypeFromHex(int hex)
    {
        int portType = 0;

        if ((hex >= 7) && (hex <= 12))
        {
            portType = 0;
        }
        else
        {
            portType = hex & 0xF;
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
     * @param rh  the robber hex coordinate
     */
    public void setRobberHex(int rh)
    {
        robberHex = rh;
    }

    /**
     * @return the list of coordinates for a type of port;
     *         each element is an Integer
     *
     * @param portType  the type of port;
     *        in range {@link #MISC_PORT} to {@link #WOOD_PORT}.
     * @see #getPortsLayout()
     */
    public Vector getPortCoordinates(int portType)
    {
        return ports[portType];
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
            return num2BoardNum[num];
        }
    }

    /**
     * Given a hex coordinate, return the type of hex
     *
     * @param hex  the coordinates ("ID") for a hex
     * @return the type of hex:
     *         Land in range {@link #CLAY_HEX} to {@link #WOOD_HEX},
     *         or {@link #DESERT_HEX},
     *         or {@link #MISC_PORT_HEX} for any port,
     *         or {@link #WATER_HEX}.
     *
     * @see #getPortTypeFromHex(int)
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
     * @see #getPortTypeFromHex(int)
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
     * @return the nodes that touch this edge, as a Vector of Integer coordinates
     */
    public static Vector getAdjacentNodesToEdge(int coord)
    {
        Vector nodes = new Vector(2);
        int tmp;

        /**
         * if the coords are (even, even), then
         * the road is '|'.
         */
        if ((((coord & 0x0F) + (coord >> 4)) % 2) == 0)
        {
            tmp = coord + 0x01;

            if ((tmp >= MINNODE) && (tmp <= MAXNODE))
            {
                nodes.addElement(new Integer(tmp));
            }

            tmp = coord + 0x10;

            if ((tmp >= MINNODE) && (tmp <= MAXNODE))
            {
                nodes.addElement(new Integer(tmp));
            }
        }
        else
        {
            /* otherwise the road is either '/' or '\' */
            tmp = coord;

            if ((tmp >= MINNODE) && (tmp <= MAXNODE))
            {
                nodes.addElement(new Integer(tmp));
            }

            tmp = coord + 0x11;

            if ((tmp >= MINNODE) && (tmp <= MAXNODE))
            {
                nodes.addElement(new Integer(tmp));
            }
        }

        return nodes;
    }

    /**
     * @return the adjacent edges to this edge, as a Vector of Integer coordinates
     */
    public static Vector getAdjacentEdgesToEdge(int coord)
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

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x01;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x10;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord - 0x01;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
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

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x01;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x11;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord - 0x01;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
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

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x11;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord + 0x10;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord - 0x11;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
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
     * @return the edges touching this node, as a Vector of Integer coordinates
     */
    public static Vector getAdjacentEdgesToNode(int coord)
    {
        Vector edges = new Vector(3);
        int tmp;

        /**
         * if the coords are (even, odd), then
         * the node is 'Y'.
         */
        if (((coord >> 4) % 2) == 0)
        {
            tmp = coord - 0x11;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord - 0x01;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }
        }
        else
        {
            /**
             * otherwise the coords are (odd, even),
             * and the EDGE is 'upside down Y'.
             */
            tmp = coord - 0x10;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }

            tmp = coord - 0x11;

            if ((tmp >= MINEDGE) && (tmp <= MAXEDGE))
            {
                edges.addElement(new Integer(tmp));
            }
        }

        return edges;
    }

    /**
     * @return the nodes adjacent to this node, as a Vector of Integer coordinates
     */
    public static Vector getAdjacentNodesToNode(int coord)
    {
        Vector nodes = new Vector(3);
        int tmp;

        tmp = coord - 0x11;

        if ((tmp >= MINNODE) && (tmp <= MAXNODE))
        {
            nodes.addElement(new Integer(tmp));
        }

        tmp = coord + 0x11;

        if ((tmp >= MINNODE) && (tmp <= MAXNODE))
        {
            nodes.addElement(new Integer(tmp));
        }

        /**
         * if the coords are (even, odd), then
         * the node is 'Y'.
         */
        if (((coord >> 4) % 2) == 0)
        {
            tmp = (coord + 0x10) - 0x01;

            if ((tmp >= MINNODE) && (tmp <= MAXNODE))
            {
                nodes.addElement(new Integer((coord + 0x10) - 0x01));
            }
        }
        else
        {
            /**
             * otherwise the coords are (odd, even),
             * and the node is 'upside down Y'.
             */
            tmp = coord - 0x10 + 0x01;

            if ((tmp >= MINNODE) && (tmp <= MAXNODE))
            {
                nodes.addElement(new Integer(coord - 0x10 + 0x01));
            }
        }

        return nodes;
    }
    
    /**
     * Make a list of all valid hex coordinates (or, only land) adjacent to this hex.
     * Valid coordinates are those within the board data structures,
     * within {@link #MINHEX} to {@link #MAXHEX}, and valid according to {@link #hexIDtoNum}.
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
     * @return true if the node is on the board
     * @param node Node coordinate
     */
    public boolean isNodeOnBoard(int node)
    {
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
