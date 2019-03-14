/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2019 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;


/**
 * This is a representation of the board in Settlers of Catan.
 * Board initialization is done in {@link #makeNewBoard(Map)}; that method
 * has some internal comments on structures, coordinates, layout and values.
 *<P>
 * Because some game variants may need different board layouts or features,
 * you will need a subclass of SOCBoard like {@link SOCBoardLarge}: Use
 * {@link SOCBoard.BoardFactory#createBoard(Map, boolean, int)}
 * whenever you need to construct a new SOCBoard.
 *<P>
 * A {@link SOCGame} uses this board; the board is not given a reference to the game, to enforce layering
 * and keep the board logic simple.  Game rules should be enforced at the game, not the board.
 * Calling board methods won't change the game state.
 *<P>
 * To identify nearby nodes, edges, hexes, etc, use the methods
 * with names such as {@link #getAdjacentHexesToNode(int)}.
 *<P>
 * Other methods to examine the board: {@link SOCGame#getPlayersOnHex(int, java.util.Set)},
 * {@link SOCGame#putPiece(SOCPlayingPiece)}, etc.
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
 *      {@link #getAdjacentHexToEdge(int, int)}
 *    </td>
 *    <td><!-- Hex adjac to node -->
 *      {@link #getAdjacentHexesToNode(int)}
 *    </td>
 *</TR>
 *<TR><td> Edge </td>
 *    <td><!-- Edge adjac to hex -->
 *      -
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
 *      {@link #getAdjacentNodeToHex(int, int)} <br>
 *      {@link #getAdjacentNodesToHex(int)} <br>
 *      {@link #getAdjacentNodesToHex_arr(int)}
 *    </td>
 *    <td><!-- Node adjac to edge -->
 *      {@link #getAdjacentNodesToEdge(int)} <br>
 *      {@link #getAdjacentNodesToEdge_arr(int)} <br>
 *      {@link #getAdjacentNodeFarEndOfEdge(int, int)}
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
 *      {@link #getPreviousRobberHex()} <br>
 *      {@link #getHexLayout()} <br>
 *      {@link #getLandHexCoords()}
 *    </td>
 *    <td><!-- edge -->
 *      {@link #roadOrShipAtEdge(int)} <br>
 *      {@link #getPortsEdges()}
 *    </td>
 *    <td><!-- node -->
 *      {@link #isNodeOnLand(int)} <br>
 *      {@link #settlementAtNode(int)} <br>
 *      {@link #getPortTypeFromNodeCoord(int)}
 *    </td>
 *</TR>
 *</table>
 *  See also {@link SOCBoardLarge} which has more geometry methods.
 *<P>
 * <b>Coordinate system,</b> as seen in appendix A of Robert S Thomas' dissertation:
 *<P>
 * <b>Hexes</b> (represented as coordinate of their centers),
 * <b>nodes</b> (corners of hexes; where settlements/cities are placed),
 * and <b>edges</b> (between nodes; where roads are placed),
 * share the same grid of coordinates.
 * Each hex is 2 units wide, in a 2-D coordinate system.
 *<P>
 * To explore coordinates at the client, type debug command {@code =*= showcoords}
 * to show a tooltip with current board coordinates at the mouse pointer.
 * To turn this off, type {@code =*= hidecoords}.
 *<P>
 * Current coordinate encodings: v1 ({@link #BOARD_ENCODING_ORIGINAL}),
 *   v2 ({@link #BOARD_ENCODING_6PLAYER}), v3 ({@link #BOARD_ENCODING_LARGE}).
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
 * For the large sea board (encoding v3: {@link #BOARD_ENCODING_LARGE}), see subclass {@link SOCBoardLarge}.
 * Remember that road and ship pieces extend the {@link SOCRoutePiece} class.
 * Most methods of {@link SOCBoard}, {@link SOCGame} and {@link SOCPlayer} differentiate them
 * ({@link SOCPlayer#hasPotentialRoad()} vs {@link SOCPlayer#hasPotentialShip()}),
 * but a few methods group them together:
 *<UL>
 *<LI> {@link #roadOrShipAtEdge(int)}
 *<LI> {@link #getRoadsAndShips()}
 *</UL>
 * On the large sea board, there can optionally be multiple "land areas"
 * (groups of islands), if {@link SOCBoardLarge#getLandAreasLegalNodes()} != null.
 *
 * @author Robert S Thomas
 * @see SOCBoardLarge
 */
public abstract class SOCBoard implements Serializable, Cloneable
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    //
    // Hex types
    //

    /**
     * Water hex; lower-numbered than all land hex types.
     * Before v2.0.00, value was 6 instead of 0.
     * @see #isHexOnLand(int)
     * @see #isHexOnWater(int)
     * @see #CLAY_HEX
     * @see #MAX_LAND_HEX
     */
    public static final int WATER_HEX = 0;

    /**
     * Clay; lowest-numbered hex type.
     * Same numeric value as {@link SOCResourceConstants#CLAY}.
     * @see #WATER_HEX
     */
    public static final int CLAY_HEX = 1;
    public static final int ORE_HEX = 2;
    public static final int SHEEP_HEX = 3;
    public static final int WHEAT_HEX = 4;
    /**
     * Wood.  As with all land resource hex types,
     * same numeric value as its resource constant
     * {@link SOCResourceConstants#WOOD}.
     */
    public static final int WOOD_HEX = 5;

    /**
     * Desert; highest-numbered hex type.
     * Also {@link #MAX_LAND_HEX} for the v1 and v2 board encodings.
     * Before v2.0.00, value was 0 instead of 6.
     */
    public static final int DESERT_HEX = 6;

    /**
     * Highest-numbered land hex type ({@link #DESERT_HEX}).
     *<P>
     * The v3 encoding has a higher {@link SOCBoardLarge#GOLD_HEX} and {@link SOCBoardLarge#FOG_HEX},
     * but they aren't encodable in this class (v1 or v2 encoding) because of {@link #MISC_PORT_HEX}.
     * @since 1.1.07
     * @see #isHexOnLand(int)
     * @see #isHexOnWater(int)
     * @see #WATER_HEX
     */
    private static final int MAX_LAND_HEX = 6;

    /** Misc (3-for-1) port type; lowest-numbered port-hextype integer */
    public static final int MISC_PORT_HEX = 7;  // Must be first port-hextype integer
    public static final int CLAY_PORT_HEX = 8;
    public static final int ORE_PORT_HEX = 9;
    public static final int SHEEP_PORT_HEX = 10;
    public static final int WHEAT_PORT_HEX = 11;
    /** Wood port type; highest-numbered port-hextype integer */
    public static final int WOOD_PORT_HEX = 12;  // Must be last port-hextype integer

    /**
     * Misc (3-for-1) port; lowest-numbered port-type integer.
     *<P>
     * Other code such as <tt>SOCBoardPanel.drawHex</tt> relies on the
     * fact that {@link #MISC_PORT} == 0 == {@link #WATER_HEX},
     * and that the range {@link #CLAY_PORT} - {@link #WOOD_PORT} are 1 - 5,
     * and {@link #CLAY_HEX} == {@link #CLAY_PORT}.
     */
    public static final int MISC_PORT = 0;  // Must be first port-type integer; must be 0 (hardcoded in places here)
    /** Clay port type. <tt>CLAY_PORT</tt> == {@link #CLAY_HEX}. */
    public static final int CLAY_PORT = 1;
    /** Ore port type. <tt>ORE_PORT</tt> == {@link #ORE_HEX}. */
    public static final int ORE_PORT = 2;
    /** Sheep port type. <tt>SHEEP_PORT</tt> == {@link #SHEEP_HEX}.  */
    public static final int SHEEP_PORT = 3;
    /** Wheat port type. <tt>WHEAT_PORT</tt> == {@link #WHEAT_HEX}.  */
    public static final int WHEAT_PORT = 4;
    /**
     * Wood port type; highest-numbered port-type integer.
     * <tt>WOOD_PORT</tt> == {@link #WOOD_HEX}.
     */
    public static final int WOOD_PORT = 5;  // Must be last port-type integer

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
     * "Visual" width of original 4-player board, including ring of port hexes, in half-hex coordinate units.
     * The original board uses a non-orthogonal coordinate system; see {@link #BOARD_ENCODING_ORIGINAL}.
     * @since 2.0.00
     */
    public static final int WIDTH_VISUAL_ORIGINAL = 13;

    /**
     * "Visual" height of original 4-player board, including ring of port hexes, in half-hex coordinate units.
     * The original board uses a non-orthogonal coordinate system; see {@link #BOARD_ENCODING_ORIGINAL}.
     * @since 2.0.00
     */
    public static final int HEIGHT_VISUAL_ORIGINAL = 14;

    /**
     * Board Encoding fields begin here
     * ------------------------------------------------------------------------------------
     */

    // If you add a new BOARD_ENCODING_* constant:
    // - Update MAX_BOARD_ENCODING
    // - Update the getBoardEncodingFormat javadocs
    // - Do where-used on the existing encoding constants and MAX_BOARD_ENCODING
    //   to look for other places you may need to check for the new constant

    /**
     * Classic 4-player original format (v1) used with {@link SOCBoard4p} for {@link #getBoardEncodingFormat()}:
     * Hexadecimal 0x00 to 0xFF along 2 diagonal axes.
     * Coordinate range on each axis is 0 to 15 decimal.<BR>
     * The two axes' ranges in hex:<pre>
     *   Hexes: 11 to DD
     *   Nodes: 01 or 10, to FE or EF
     *   Edges: 00 to EE </pre>
     *<P>
     * See the Dissertation PDF for details.
     * @since 1.1.06
     */
    public static final int BOARD_ENCODING_ORIGINAL = 1;

    /**
     * Classic 6-player format (v2) used with {@link SOCBoard6p} for {@link #getBoardEncodingFormat()}:
     * Land hexes are same encoding as {@link #BOARD_ENCODING_ORIGINAL}.
     * Land starts 1 extra hex west of classic 4-player board,
     * and has an extra row of land at north and south end.
     *<P>
     * Ports are not part of {@link #hexLayout} because their
     * coordinates wouldn't fit within 2 hex digits.
     * Instead, see {@link #getPortTypeFromNodeCoord(int)},
     *   {@link #getPortsEdges()}, {@link #getPortsFacing()},
     *   {@link #getPortCoordinates(int)} or {@link #getPortsLayout()}.
     * @since 1.1.08
     */
    public static final int BOARD_ENCODING_6PLAYER = 2;

    /**
     * Sea board format (v3) used with {@link SOCBoardLarge} for {@link #getBoardEncodingFormat()}:
     * Allows up to 127 x 127 board with an arbitrary mix of land and water tiles.
     * Land, water, and port locations/facings are no longer hardcoded.
     * Use {@link #getPortsCount()} to get the number of ports.
     * For other port information, use the same methods as in {@link #BOARD_ENCODING_6PLAYER}.
     *<P>
     * Activated with {@link SOCGameOption} {@code "SBL"}.
     *<P>
     * Although this is encoding "v3", it was added to JSettlers in v2.0.00
     * ({@link SOCBoardLarge#MIN_VERSION}).
     * @since 2.0.00
     */
    public static final int BOARD_ENCODING_LARGE = 3;

    /**
     * Largest value of {@link #getBoardEncodingFormat()} supported in this version.
     * @since 1.1.08
     */
    public static final int MAX_BOARD_ENCODING = 3;

    /**
     * Maximum valid coordinate value; size of board in coordinates (not in number of hexes across).
     * Default size per {@link #BOARD_ENCODING_ORIGINAL} is: <pre>
     *   Hexes: 11 to DD
     *   Nodes: 01 or 10, to FE or EF
     *   Edges: 00 to EE </pre>
     * Although this field is protected (not private), please treat it as read-only.
     * @since 1.1.06
     */
    protected int boardWidth, boardHeight;

    /**
     * Minimum and maximum edge and node coordinates in this board's encoding.
     * ({@link #MAXNODE} is the same in the v1 and v2 current encodings.)
     * Not used in v3 ({@link #BOARD_ENCODING_LARGE}).
     * @since 1.1.08
     */
    protected int minNode, minEdge, maxEdge;

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
     *<LI> 3 - Large board ({@link #BOARD_ENCODING_LARGE}).
     *       Coordinate range for rows,columns is each 0 to 255 decimal,
     *       or altogether 0x0000 to 0xFFFF hex.
     *       Arbitrary mix of land and water tiles.
     *       Added in 2.0.00, implemented in {@link SOCBoardLarge}.
     *       Activated with {@link SOCGameOption} <tt>"SBL"</tt>.
     *</UL>
     * @since 1.1.06
     */
    protected final int boardEncodingFormat;

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
     * largest coordinate value for a node on land, in the v1 and v2 encodings
     */
    private static final int MAXNODE = 0xDC;

    /***************************************
     * Hex data array, one element per water or land (or port, which is special water) hex.
     * Each element's coordinates on the board ("hex ID") is {@link #numToHexID}[i].
     * Each element's value encodes hex type and, if a
     * port, its facing ({@link #FACING_NE} to {@link #FACING_NW}).
     *<P>
     * For land hexes, the dice number on <tt>hexLayout</tt>[i] is {@link #numberLayout}[i].
     *<P>
     * <b>Key to the hexLayout[] values:</b>
     *<br>
     * Note that hexLayout contains ports only for the v1 encoding ({@link #BOARD_ENCODING_ORIGINAL});
     * v2 and v3 use {@link #portsLayout} instead, and hexLayout contains only water and the land
     * hex types.  The v3 encoding ({@link #BOARD_ENCODING_LARGE}) doesn't use {@code hexLayout} at all,
     * instead it has a 2-dimensional {@code hexLayoutLg} structure.
       <pre>
       0 : water   {@link #WATER_HEX} (was 6 before v2.0.00)
       1 : clay    {@link #CLAY_HEX}
       2 : ore     {@link #ORE_HEX}
       3 : sheep   {@link #SHEEP_HEX}
       4 : wheat   {@link #WHEAT_HEX}
       5 : wood    {@link #WOOD_HEX}
       6 : desert  {@link #DESERT_HEX} (was 0 before v2.0.00)  also: {@link #MAX_LAND_HEX}
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
    private int[] hexLayout =   // initially all WATER_HEX
    {
        WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX,
        WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX,
        WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX,
        WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX,
        WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX,
        WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX,
        WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX
    };

    /**
     * Port information; varies by board layout encoding format.
     * Initialized in {@link #makeNewBoard(Map)} if not <tt>null</tt>.
     *<UL>
     *<LI> v1: Not used in the original board, these are part of {@link #hexLayout} instead,
     *         and this field is <tt>null</tt>.
     *
     *<LI> v2: On the 6-player (v2 layout) board, each port's type.
     * Same value range as in {@link #hexLayout}.
     * 1 element per port. Same ordering as {@link #PORTS_FACING_V2}.
     *
     *<LI> v3: {@link #BOARD_ENCODING_LARGE} stores more information
     * within the port layout array.  <em>n</em> = {@link #getPortsCount()}.
     * The port types are stored at the beginning, from index 0 to <em>n</em> - 1.
     * The next <em>n</em> indexes store each port's edge coordinate.
     * The next <em>n</em> store each port's facing (towards land).
     * <P>
     * One scenario there has movable ports; a port's edge might temporarily be -1.
     * Ignore this port if so, it's not currently placed on the board.
     *</UL>
     *
     * @see #ports
     * @since 1.1.08
     */
    protected int[] portsLayout;

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
     *  The robber hex is 0.  Water hexes are -1.
     *<P>
     *  Used in the v1 and v2 encodings (ORIGINAL, 6PLAYER).
     *  The v3 encoding ({@link #BOARD_ENCODING_LARGE}) doesn't use this array,
     *  instead it uses {@link SOCBoardLarge#numberLayoutLg}.
     */
    private int[] numberLayout =
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
     *<LI> {@link #BOARD_ENCODING_LARGE}: Does not use numToHexID or hexLayout; hex coordinate == hex number.
     *</UL>
     *
     * The hex coordinate layout given here can also be seen in RST's dissertation figure A.1.
     *
     * @see #hexIDtoNum
     * @see #nodesOnLand
     * @see SOCBoard4p#HEXCOORDS_LAND_V1
     * @see SOCBoard6p#HEXCOORDS_LAND_V2
     * @see #getLandHexCoords()
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
     * @see #nodeIDtoPortType
     */
    private int[] hexIDtoNum;

    /**
     * translate node ID (node coordinate) to a port's type ({@link #MISC_PORT} to {@link #WOOD_PORT}).
     * Initialized in {@link #makeNewBoard(Map)}.
     * Key = node coordinate as Integer; value = port type as Integer.
     * If the node ID isn't present, that coordinate isn't a valid port on the board.
     * @see #ports
     * @see #portsLayout
     * @see #hexIDtoNum
     * @since 1.1.08
     */
    protected HashMap<Integer,Integer> nodeIDtoPortType;
        // was int[] in v1.1.08 and later 1.x.xx versions; HashMap in v2.0.00+

    /**
     * Offset to add to hex coordinate to get all adjacent node coords, starting at
     * index 0 at the top (northern corner of hex) and going clockwise (RST dissertation figure A.5).
     * Because we're looking at nodes and not edges (corners, not sides, of the hex),
     * these are offset from the set of "facing" directions by 30 degrees.
     * -- see getAdjacent* methods instead
     */
    private final static int[] HEXNODES = { 0x01, 0x12, 0x21, 0x10, -0x01, -0x10 };

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
    private final static int[] NODE_2_AWAY = { -9, 0x02, 0x22, 0x20, -0x02, -0x22, -0x20 };

    /**
     * the hex coordinate that the robber is in, or -1; placed on desert in {@link #makeNewBoard(Map)}.
     * Once the robber is placed on the board, it cannot be removed (cannot become -1 again).
     */
    private int robberHex = -1;  // Soon placed on desert, when makeNewBoard is called

    /**
     * the previous hex coordinate that the robber is in; -1 unless
     * {@link #setRobberHex(int, boolean) setRobberHex(rh, true)} was called.
     * @since 1.1.11
     */
    private int prevRobberHex = -1;

    /**
     * Maximum land hex type value for the robber; can be used for array sizing.
     * Same value range as {@link #getHexTypeFromCoord(int)} for the current board encoding.
     * ({@link #BOARD_ENCODING_LARGE adds values {@link SOCBoardLarge#GOLD_HEX}
     * and {@link SOCBoardLarge#FOG_HEX}, for example.)
     * @see SOCGame#canMoveRobber(int, int)
     * @since 2.0.00
     */
    public final int max_robber_hextype;

    /**
     * where the ports of each type are; coordinates per port type.
     * Indexes are port types, {@link #MISC_PORT} to {@link #WOOD_PORT}.
     * Values are Vectors of Integer node coordinates; each port
     *    will have 2 Integers because it touches 2 nodes.
     *    So, the number of ports of a type is ports[type].size() / 2.
     * @see #portsLayout
     * @see #getPortsEdges()
     */
    @SuppressWarnings("unchecked")
    protected Vector<Integer>[] ports = new Vector[6];  // 1 per resource type, MISC_PORT to WOOD_PORT

    /**
     * roads on the board; Vector of {@link SOCRoad}s.
     * On the large sea board ({@link SOCBoardLarge}), also
     * contains all {@link SOCShip}s on the board.
     *<P>
     * Before v2.0.00 this field was {@code roads}.
     */
    protected Vector<SOCRoutePiece> roadsAndShips = new Vector<SOCRoutePiece>(60);

    /**
     * settlements on the board
     */
    protected Vector<SOCSettlement> settlements = new Vector<SOCSettlement>(20);

    /**
     * cities on the board
     */
    protected Vector<SOCCity> cities = new Vector<SOCCity>(16);

    /**
     * random number generator
     */
    protected Random rand = new Random();

    /**
     * a list of nodes on the land of the board; key is node's Integer coordinate, value is Boolean.
     * nodes on outer edges of surrounding water/ports are not on the board.
     *<P>
     * See dissertation figure A.2.
     * See also {@link #initPlayerLegalSettlements()} and {@link #getLandHexCoords()}.
     *<P>
     * On the large sea board, there can optionally be multiple "land areas"
     * (groups of islands), if {@link SOCBoardLarge#getLandAreasLegalNodes()} != null.
     * In that case, <tt>nodesOnLand</tt> contains all nodes of all land areas.
     */
    protected HashSet<Integer> nodesOnLand = new HashSet<Integer>();

    /**
     * Minimal super constructor for subclasses.
     * Initializes common fields like {@link #ports} as empty structures,
     * but does not set up layout-specific fields like {@link #portsLayout}.
     *<P>
     * Most likely you should also call {@link #setBoardBounds(int, int)}.
     *
     * @param boardEncodingFmt  A format constant in the currently valid range:
     *         Must be >= {@link #BOARD_ENCODING_ORIGINAL} and &lt;= {@link #MAX_BOARD_ENCODING}.
     * @param maxRobberHextype  Maximum land hextype value, or maximum hex type
     *         the robber can be placed at.  Same value range as {@link #max_robber_hextype}
     *         and as your subclass's {@link #getHexTypeFromCoord(int)} method.
     * @since 2.0.00
     * @throws IllegalArgumentException if <tt>boardEncodingFmt</tt> is out of range
     */
    protected SOCBoard(final int boardEncodingFmt, final int maxRobberHextype)
        throws IllegalArgumentException
    {
        if ((boardEncodingFmt < 1) || (boardEncodingFmt > MAX_BOARD_ENCODING))
            throw new IllegalArgumentException(Integer.toString(boardEncodingFmt));

        boardEncodingFormat = boardEncodingFmt;
        max_robber_hextype = maxRobberHextype;

        // Reminder: Most field initialization is done at its declaration
        // (robberHex, prevRobberHex, roads, settlements, cities)

        /**
         * initialize the port vector array
         */
        ports[MISC_PORT] = new Vector<Integer>(8);
        for (int i = CLAY_PORT; i <= WOOD_PORT; i++)
            ports[i] = new Vector<Integer>(2);
    }

    /**
     * Create a new Settlers of Catan Board, with the v1 or v2 encoding.
     * (For the v3 encoding, instead use a {@link SOCBoardLarge} constructor.)
     * @param gameOpts  if game has options, map of {@link SOCGameOption}; otherwise null.
     * @param maxPlayers Maximum players; must be 4 or 6. (Added in 1.1.08)
     * @param boardEncodingFmt  A format constant in the currently valid range:
     *         Must be >= {@link #BOARD_ENCODING_ORIGINAL} and &lt;= {@link #MAX_BOARD_ENCODING}.
     * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
     * @see BoardFactory#createBoard(Map, boolean, int)
     */
    protected SOCBoard(Map<String, SOCGameOption> gameOpts, final int maxPlayers, final int boardEncodingFmt)
        throws IllegalArgumentException
    {
        this(boardEncodingFmt, MAX_LAND_HEX);

        if ((maxPlayers != 4) && (maxPlayers != 6))
            throw new IllegalArgumentException("maxPlayers: " + maxPlayers);

        boardWidth = 0x10;
        boardHeight = 0x10;

        /**
         * initialize the hexIDtoNum array;
         * see dissertation figure A.1 for coordinates
         */
        hexIDtoNum = new int[0xEE];  // Length must be >= MAXHEX

        for (int i = 0; i < 0xEE; i++)
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

        initNodesOnLand();
    }

    /**
     * As part of the constructor, check the {@link #boardEncodingFormat}
     * and initialize {@link #nodesOnLand} accordingly.
     * @see #initPlayerLegalSettlements()
     * @since 2.0.00
     */
    private void initNodesOnLand()
    {
        final boolean is6player = (boardEncodingFormat == BOARD_ENCODING_6PLAYER);

        nodesOnLand = new HashSet<Integer>();

        /**
         * initialize the list of nodes on the land of the board;
         * nodes on outer edges of surrounding water/ports are not on the board.
         * See dissertation figure A.2.
         * Classic 6-player layout starts land 1 extra hex (2 nodes) west of 4-player board,
         * and has an extra row of land hexes at north and south end.
         * Same node coordinates are needed in initPlayerLegalSettlements.
         */
        final int westAdj = (is6player) ? 0x22 : 0x00;

        // Set each row of valid node coordinates:
        int i;

        if (is6player)
        {
            for (i = 0x07; i <= 0x6D; i += 0x11)
                nodesOnLand.add(Integer.valueOf(i));
        }

        for (i = 0x27 - westAdj; i <= 0x8D; i += 0x11)  //  Northernmost horizontal row: each north corner across 3 hexes
            nodesOnLand.add(Integer.valueOf(i));

        for (i = 0x25 - westAdj; i <= 0xAD; i += 0x11)  // Next: each north corner of row of 4 / south corner of the northernmost 3 hexes
            nodesOnLand.add(Integer.valueOf(i));

        for (i = 0x23 - westAdj; i <= 0xCD; i += 0x11)  // Next: north corners of middle row of 5 hexes
            nodesOnLand.add(Integer.valueOf(i));

        for (i = 0x32 - westAdj; i <= 0xDC; i += 0x11) // Next: south corners of middle row of 5 hexes
            nodesOnLand.add(Integer.valueOf(i));

        for (i = 0x52 - westAdj; i <= 0xDA; i += 0x11)  // South corners of row of 4 / north corners of the southernmost 3 hexes
            nodesOnLand.add(Integer.valueOf(i));

        for (i = 0x72 - westAdj; i <= 0xD8; i += 0x11)  // Southernmost horizontal row: each south corner across 3 hexes
            nodesOnLand.add(Integer.valueOf(i));

        if (is6player)
        {
            for (i = 0x70; i <= 0xD6; i += 0x11)
                nodesOnLand.add(Integer.valueOf(i));
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
    private void initHexIDtoNumAux(int begin, int end, int num)
    {
        int i;

        for (i = begin; i <= end; i += 0x22)
        {
            hexIDtoNum[i] = num;
            num++;
        }
    }

    /**
     * Fill the board layout for a game being started:
     * Shuffle the hex tiles and layout a board.
     * This is called at server, but not at client;
     * client instead calls methods such as {@link #setHexLayout(int[])}
     * or {@link SOCBoardLarge#setLandHexLayout(int[])}.
     * @param opts {@link SOCGameOption Game options}, which may affect
     *          tile placement on board, or null.  <tt>opts</tt> must be
     *          the same as passed to constructor, and thus give the same size and layout
     *          (same {@link #getBoardEncodingFormat()}).
     */
    public void makeNewBoard(final Map<String, SOCGameOption> opts)
    {
        final boolean is6player = (boardEncodingFormat == BOARD_ENCODING_6PLAYER);

        final SOCGameOption opt_breakClumps = (opts != null ? opts.get("BC") : null);

        // shuffle and place the land hexes, numbers, and robber:
        // sets robberHex, contents of hexLayout[] and numberLayout[].
        // Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
        {
            final int[] landHex = is6player ? SOCBoard6p.makeNewBoard_landHexTypes_v2 : SOCBoard4p.makeNewBoard_landHexTypes_v1;
            final int[][] numPaths = is6player ? SOCBoard6p.makeNewBoard_numPaths_v2 : SOCBoard4p.makeNewBoard_numPaths_v1;
            final int[] numPath = numPaths[ Math.abs(rand.nextInt() % numPaths.length) ];
            final int[] numbers = is6player ? SOCBoard6p.makeNewBoard_diceNums_v2 : SOCBoard4p.makeNewBoard_diceNums_v1;
            makeNewBoard_placeHexes(landHex, numPath, numbers, opt_breakClumps);
        }

        // copy and shuffle the ports, and check vs game option BC
        final int[] portTypes = (is6player) ? SOCBoard6p.PORTS_TYPE_V2 : SOCBoard4p.PORTS_TYPE_V1;
        int[] portHex = new int[portTypes.length];
        System.arraycopy(portTypes, 0, portHex, 0, portTypes.length);
        makeNewBoard_shufflePorts(portHex, opt_breakClumps);
        if (is6player)
            portsLayout = portHex;  // No need to remember for 4-player classic layout

        // place the ports (hex numbers and facing) within hexLayout and nodeIDtoPortType.
        // fill out the ports[] vectors with node coordinates where a trade port can be placed.
        nodeIDtoPortType = new HashMap<Integer,Integer>();
        if (is6player)
        {
            for (int i = 0; i < SOCBoard6p.PORTS_FACING_V2.length; ++i)
            {
                final int ptype = portHex[i];
                final int[] nodes = getAdjacentNodesToEdge_arr(SOCBoard6p.PORTS_EDGE_V2[i]);
                placePort(ptype, -1, SOCBoard6p.PORTS_FACING_V2[i], nodes[0], nodes[1]);
            }
        } else {
            for (int i = 0; i < SOCBoard4p.PORTS_FACING_V1.length; ++i)
            {
                final int ptype = portHex[i];
                final int[] nodes = getAdjacentNodesToEdge_arr(SOCBoard4p.PORTS_EDGE_V1[i]);
                placePort(ptype, SOCBoard4p.PORTS_HEXNUM_V1[i], SOCBoard4p.PORTS_FACING_V1[i], nodes[0], nodes[1]);
            }
        }

    }

    /**
     * For {@link #makeNewBoard(Map)}, place the land hexes, number, and robber,
     * after shuffling landHex[].
     * Sets robberHex, contents of hexLayout[] and numberLayout[].
     * Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
     * (for land hex resource types).
     * Called from {@link #makeNewBoard(Map)} at server only; client has its board layout sent from the server.
     *<P>
     * This method does not clear out {@link #hexLayout} or {@link #numberLayout}
     * before it starts placement.  Since hexLayout's land hex coordinates are hardcoded within
     * {@link #numToHexID}, it can only be called once per board layout.
     *
     * @param landHex  Resource type to place into {@link #hexLayout} for each land hex; will be shuffled.
     *                    Values are {@link #CLAY_HEX}, {@link #DESERT_HEX}, etc.
     * @param numPath  Indexes within {@link #hexLayout} (also within {@link #numberLayout}) for each land hex;
     *                    same array length as <tt>landHex[]</tt>
     * @param number   Numbers to place into {@link #numberLayout} for each land hex;
     *                    array length is <tt>landHex[].length</tt> minus 1 for each desert in <tt>landHex[]</tt>
     * @param optBC    Game option "BC" from the options for this board, or <tt>null</tt>.
     * @throws IllegalArgumentException if {@link #makeNewBoard_checkLandHexResourceClumps(Vector, int)}
     *                 finds an invalid or uninitialized hex coordinate (hex type -1)
     */
    private void makeNewBoard_placeHexes
        (int[] landHex, final int[] numPath, final int[] number, SOCGameOption optBC)
        throws IllegalArgumentException
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
                    if (idx == i)
                        continue;
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
                Vector<Integer> unvisited = new Vector<Integer>();  // contains each land hex's coordinate
                for (int i = 0; i < landHex.length; ++i)
                {
                    unvisited.addElement(Integer.valueOf(numToHexID[numPath[i]]));
                }
                clumpsNotOK = makeNewBoard_checkLandHexResourceClumps(unvisited, clumpSize);
            }  // if (checkClumps)

        } while (clumpsNotOK);

    }  // makeNewBoard_placeHexes

    /**
     * Depth-first search to check land hexes for resource clumps.
     *<P>
     * Start with the set of all land hexes, and consider them 'unvisited'.
     * Look at each hex in the set, marking them as visited and moving
     * them into new subsets ("clumps") composed of adjacent hexes of the
     * same resource type. Build clumps by immediately visiting those adjacent
     * hexes, and their unvisited same-type adjacents, etc.
     * Once we've visited each hex, check if any clump subset's
     * size is larger than the allowed size.
     *<P>
     * For the Fog Island (scenario option _SC_FOG on {@link SOCBoardLarge}),
     * one land area contains some water.  So, <tt>unvisited</tt> may contain
     * a few water hexes.  For performance, in general you should omit water
     * hex locations from <tt>unvisited</tt>.
     *<P>
     * Called from {@link #makeNewBoard_placeHexes(int[], int[], int[], SOCGameOption)}.
     * Before v2.0.00, this was part of makeNewBoard_placeHexes.
     *
     * @param unvisited  Contains each land hex's coordinate as an Integer;
     *          <b>Note:</b> This vector will be modified by the method. <br>
     *          See note above about occasional water hex coordinates in <tt>univisited</tt>.
     * @param clumpSize  Clumps of this size or more are too large.
     *          Minimum value is 3, smaller values will always return false.
     * @return  true if large clumps found, false if okay
     * @throws IllegalArgumentException if a hex type is -1 (uninitialized or not a valid hex coordinate)
     * @since 2.0.00
     */
    protected boolean makeNewBoard_checkLandHexResourceClumps(Vector<Integer> unvisited, final int clumpSize)
        throws IllegalArgumentException
    {
        if (clumpSize < 3)
            return false;

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
        //   Sets will contain each land hex's coordinate ("ID").
        //
        // - clumps := new empty set (will be a vector of vectors)
        //     At end of search, each element of this set will be
        //       a subset (a vector) of adjacent hexes
        // - clumpsNotOK := false
        // - unvisited-set := new set (vector) of all land hexes
        // - iterate through unvisited-set; for each hex:
        //     - remove this from unvisited-set
        //     - if hex is water, done looking at this hex
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
        //   Sets will contain each land hex's coordinate ("ID").
        //   We're operating on Integer instances, which is okay because
        //   vector methods such as contains() and remove() test obj.equals()
        //   to determine if the Integer is a member.
        //   (getAdjacent() returns Integers with the same value
        //    as unvisited's members.)

        boolean clumpsNotOK = false;    // will set true in while-loop body

        // - unvisited-set := new set (vector) of all land hexes
        // - iterate through unvisited-set

        while (unvisited.size() > 0)
        {
            //   for each hex:

            //     - remove this from unvisited-set
            Integer hexCoordObj = unvisited.elementAt(0);
            final int hexCoord = hexCoordObj.intValue();
            final int resource = getHexTypeFromCoord(hexCoord);
            unvisited.removeElementAt(0);
            if (resource == -1)  // would cause inf loop, this "clump" can't be broken up
                throw new IllegalArgumentException("hex type -1 at coord 0x" + Integer.toHexString(hexCoord));

            //     - skip water hexes; water is never a clump
            if (resource == SOCBoard.WATER_HEX)
                continue;

            //     - look at its adjacent hexes of same type
            //          assertion: they are all unvisited, because this hex was unvisited
            //                     and this is the top-level loop
            //     - if none, done looking at this hex
            //     - build a new clump-vector of this + all adj of same type
            //     - remove all adj-of-same-type from unvisited-set

            // set of adjacent will become the clump, or be emptied completely
            Vector<Integer> adjacent = getAdjacentHexesToHex(hexCoord, false);
            if (adjacent == null)
                continue;

            Vector<Integer> clump = null;
            for (int i = 0; i < adjacent.size(); ++i)
            {
                Integer adjCoordObj = adjacent.elementAt(i);
                final int adjCoord = adjCoordObj.intValue();
                if (resource == getHexTypeFromCoord(adjCoord))
                {
                    // keep this one
                    if (clump == null)
                        clump = new Vector<Integer>();
                    clump.addElement(adjCoordObj);
                    unvisited.remove(adjCoordObj);
                }
            }
            if (clump == null)
                continue;

            clump.insertElementAt(hexCoordObj, 0);  // put the first hex into clump

            //     - grow the clump: iterate through each hex in clump-vector (skip its first hex,
            //       because we already have its adjacent hexes)
            for (int ic = 1; ic < clump.size(); )  // ++ic is within loop body, if nothing inserted
            {
                // precondition: each hex already in clump set, is not in unvisited-vec
                final int chexCoord = clump.elementAt(ic);

                //  - look at its adjacent unvisited hexes of same type
                //  - if none, done looking at this hex
                //  - remove same-type adjacents from unvisited-set
                //  - insert them into clump-vector
                //    (will continue the iteration with them)

                Vector<Integer> adjacent2 = getAdjacentHexesToHex(chexCoord, false);
                if (adjacent2 == null)
                {
                    ++ic;
                    continue;
                }
                boolean didInsert = false;
                for (int ia = 0; ia < adjacent2.size(); ++ia)
                {
                    Integer adjCoordObj = adjacent2.elementAt(ia);
                    final int adjCoord = adjCoordObj;
                    if ((resource == getHexTypeFromCoord(adjCoord))
                        && unvisited.contains(adjCoordObj))
                    {
                        // keep this one
                        clump.insertElementAt(adjCoordObj, ic);
                        unvisited.remove(adjCoordObj);
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
        return clumpsNotOK;
    }

    /**
     * For makeNewBoard, shuffle portHex[].
     * Sets no fields, only rearranges the contents of that array.
     * Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
     * (for ports, the types here are: 3-for-1, or 2-for-1).
     * If the clumps couldn't be broken after 100 tries, give up trying.
     * (Maybe the set doesn't have enough 3-for-1, or is too small overall.)
     *
     * @param portHex Contains port types, 1 per port, as they will appear in clockwise
     *            order around the edge of the board. Must not all be the same type.
     *            {@link #MISC_PORT} is the 3-for-1 port type value.
     * @param opt_breakClumps Game option "BC", or null
     * @throws IllegalStateException if opt_breakClumps is set, and all portHex[] elements have the same value
     */
    protected void makeNewBoard_shufflePorts(int[] portHex, SOCGameOption opt_breakClumps)
        throws IllegalStateException
    {
        boolean portsOK = true;
        int redoCount = 0;

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

                                ++redoCount;
                                if (redoCount > 100)
                                {
                                    return;  // <--- Early return: Give up after 100 attempts ---
                                }

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
     * Adds the 2 nodes to {@link #ports}<tt>[ptype]</tt>.
     * @param ptype Port type; in range {@link #MISC_PORT} to {@link #WOOD_PORT}.
     * @param hex  Hex number (index) within {@link #hexLayout}, or -1 if
     *           {@link #BOARD_ENCODING_6PLAYER} or if you don't want to change
     *           <tt>hexLayout[hex]</tt>'s value.
     * @param face Facing of port towards land; 1 to 6 ({@link #FACING_NE} to {@link #FACING_NW}).
     *           Ignored if <tt>hex == -1</tt>.
     * @param node1 Node coordinate 1 of port
     * @param node2 Node coordinate 2 of port
     */
    protected final void placePort(final int ptype, final int hex, final int face, final int node1, final int node2)
    {
        if (hex != -1)
        {
            if (ptype == MISC_PORT)
            {
                // generic port == 6 + facing
                hexLayout[hex] = face + 6;
            }
            else
            {
                hexLayout[hex] = (face << 4) + ptype;
            }
        }

        final Integer node1Int = Integer.valueOf(node1),
                      node2Int = Integer.valueOf(node2),
                      ptypeInt = Integer.valueOf(ptype);

        nodeIDtoPortType.put(node1Int, ptypeInt);
        nodeIDtoPortType.put(node2Int, ptypeInt);

        ports[ptype].addElement(node1Int);
        ports[ptype].addElement(node2Int);
    }

    /**
     * Create and initialize a {@link SOCPlayer}'s legalRoads set.
     *<P>
     * Previously part of {@link SOCPlayer}, but moved here in version 1.1.12
     * to better encapsulate the board coordinate encoding.
     *<P>
     * <b>Note:</b> If your board is board layout v3 ({@link SOCBoardLarge}):
     * Because the v3 board layout varies:
     * At the server, call this after {@link #makeNewBoard(Map)}.
     * At the client, call this after {@link SOCBoardLarge#setLegalSettlements(java.util.Collection, int, HashSet[])}.
     *
     * @return the set of legal edge coordinates for roads, as a new Set of {@link Integer}s
     * @since 1.1.12
     */
    public HashSet<Integer> initPlayerLegalRoads()
    {
        // Classic 6-player layout starts land 1 extra hex (2 nodes) west of 4-player board,
        // and has an extra row of land hexes at north and south end.
        final boolean is6player =
            (boardEncodingFormat == BOARD_ENCODING_6PLAYER);
        final int westAdj = (is6player) ? 0x22 : 0x00;

        HashSet<Integer> legalRoads = new HashSet<Integer>(97);  // 4-pl board 72 roads; load factor 0.75

        // Set each row of valid road (edge) coordinates:
        int i;

        if (is6player)
        {
            for (i = 0x07; i <= 0x5C; i += 0x11)
                legalRoads.add(Integer.valueOf(i));

            for (i = 0x06; i <= 0x6C; i += 0x22)
                legalRoads.add(Integer.valueOf(i));
        }

        for (i = 0x27 - westAdj; i <= 0x7C; i += 0x11)
            legalRoads.add(Integer.valueOf(i));

        for (i = 0x26 - westAdj; i <= 0x8C; i += 0x22)
            legalRoads.add(Integer.valueOf(i));

        for (i = 0x25 - westAdj; i <= 0x9C; i += 0x11)
            legalRoads.add(Integer.valueOf(i));

        for (i = 0x24 - westAdj; i <= 0xAC; i += 0x22)
            legalRoads.add(Integer.valueOf(i));

        for (i = 0x23 - westAdj; i <= 0xBC; i += 0x11)
            legalRoads.add(Integer.valueOf(i));

        for (i = 0x22 - westAdj; i <= 0xCC; i += 0x22)
            legalRoads.add(Integer.valueOf(i));

        for (i = 0x32 - westAdj; i <= 0xCB; i += 0x11)
            legalRoads.add(Integer.valueOf(i));

        for (i = 0x42 - westAdj; i <= 0xCA; i += 0x22)
            legalRoads.add(Integer.valueOf(i));

        for (i = 0x52 - westAdj; i <= 0xC9; i += 0x11)
            legalRoads.add(Integer.valueOf(i));

        for (i = 0x62 - westAdj; i <= 0xC8; i += 0x22)
            legalRoads.add(Integer.valueOf(i));

        for (i = 0x72 - westAdj; i <= 0xC7; i += 0x11)
            legalRoads.add(Integer.valueOf(i));

        if (is6player)
        {
            for (i = 0x60; i <= 0xC6; i += 0x22)
                legalRoads.add(Integer.valueOf(i));

            for (i = 0x70; i <= 0xC5; i += 0x11)
                legalRoads.add(Integer.valueOf(i));
        }

        return legalRoads;
    }

    /**
     * Create and initialize a set of legal settlement nodes to give to a {@link SOCPlayer}.
     *<P>
     * For v1 and v2, you can clone the returned <tt>legalSettlements</tt>
     * to <tt>player.potentialSettlements</tt>.
     *<P>
     * For v3 ({@link SOCBoardLarge}), the potentials may be only a subset of
     * <tt>legalSettlements</tt>; after {@link #makeNewBoard(Map)}, call
     * {@link SOCBoardLarge#getLegalSettlements()} instead of this method.
     *<P>
     * Previously part of {@link SOCPlayer}, but moved here in version 1.1.12
     * to better encapsulate the board coordinate encoding.
     * In encoding v1 and v2, this is always the same coordinates as {@link #nodesOnLand}.
     *<P>
     * Before v2.0.00 this method was {@code initPlayerLegalAndPotentialSettlements}.
     * @see #isNodeOnLand(int)
     * @since 1.1.12
     */
    public HashSet<Integer> initPlayerLegalSettlements()
    {
        HashSet<Integer> legalSettlements = new HashSet<Integer>(nodesOnLand);
        return legalSettlements;
    }

    /**
     * @return the hex layout; meaning of values same as {@link #hexLayout}.
     *     Please treat the returned array as read-only.
     * @see #getLandHexCoords()
     * @throws UnsupportedOperationException if the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     */
    public int[] getHexLayout()
        throws UnsupportedOperationException
    {
        return hexLayout;
    }

    /**
     * The hex coordinates of all land hexes.
     * May be constant or built at call time; see subclass javadocs for this method.
     *<P>
     * Before v2.0.00 this was {@code getHexLandCoords()}.
     *
     * @return land hex coordinates, in no particular order, or null if none (all water).
     * @since 1.1.08
     */
    public abstract int[] getLandHexCoords();

    /**
     * Is this the coordinate of a land hex (not water)?
     * @param hexCoord  Hex coordinate, between 0 and {@link #MAXHEX}
     * @return  True if land, false if water or not a valid hex coordinate
     * @see #isHexOnWater(int)
     * @since 2.0.00
     */
    public boolean isHexOnLand(final int hexCoord)
    {
        int hnum = hexIDtoNum[hexCoord];
        if (hnum < 0)
            return false;
        else
            return ((hexLayout[hnum] <= MAX_LAND_HEX) && (hexLayout[hnum] != WATER_HEX));
    }

    /**
     * Is this the coordinate of a water hex (not land)?
     * @param hexCoord  Hex coordinate, between 0 and {@link #MAXHEX}
     * @return  True if water, false if land or not a valid hex coordinate
     * @see #isHexOnLand(int)
     * @since 2.0.00
     */
    public boolean isHexOnWater(final int hexCoord)
    {
        int hnum = hexIDtoNum[hexCoord];
        if (hnum < 0)
            return false;
        else
            return ((hexLayout[hnum] > MAX_LAND_HEX) || (hexLayout[hnum] == WATER_HEX));
    }

    /**
     * The dice-number layout of dice rolls at each hex number.
     * Valid for the v1 and v2 encodings, not v3 ({@link #BOARD_ENCODING_LARGE}).
     * For v3, call {@link #getLandHexCoords()} and {@link #getNumberOnHexFromCoord(int)} instead.
     * @return the number layout; each element is valued 2-12.
     *     The robber hex is 0.  Water hexes are -1.
     * @throws UnsupportedOperationException if the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     */
    public int[] getNumberLayout()
        throws UnsupportedOperationException
    {
        return numberLayout;
    }

    /**
     * On the 6-player (v2 layout) board, each port's type, such as {@link #SHEEP_PORT}.
     * Same value range as in {@link #hexLayout}.
     * (In the classic 4-player board (v1), these are part of {@link #hexLayout} instead.)
     * Same order as {@link #getPortsFacing()}: Clockwise from upper-left.
     * The number of ports is {@link #getPortsCount()}.
     *<P>
     * <b>Note:</b> The v3 layout ({@link #BOARD_ENCODING_LARGE}) stores more information
     * within the port layout array.  The port types are stored at the beginning, from index
     * 0 to {@link #getPortsCount()}-1.  If you call {@link #setPortsLayout(int[])}, be sure
     * to give it the entire array returned from here.
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
     * The length of this array is always {@link #getPortsCount()}.
     *<P>
     * This method should not be called frequently.
     *
     * @return the ports' facing
     * @see #getPortsEdges()
     * @since 1.1.08
     */
    public abstract int[] getPortsFacing();

    /**
     * Each port's edge coordinate.
     * This is the edge whose 2 end nodes can be used to build port settlements/cities.
     * Same order as {@link #getPortsLayout()}: Clockwise from upper-left.
     * The length of this array is always {@link #getPortsCount()}.
     *<P>
     * This method should not be called frequently.
     *<P>
     * A scenario of {@link SOCBoardLarge} has movable ports; a port's edge there might
     * temporarily be -1.  Ignore this port if so, it's not currently placed on the board.
     * This happens only with {@link SOCBoardLarge} (layout encoding v3), not the original or 6-player
     * (v1 or v2) {@link SOCBoard} layouts.
     *
     * @return the ports' edges
     * @see #getPortsFacing()
     * @see #getPortCoordinates(int)
     * @since 1.1.08
     */
    public abstract int[] getPortsEdges();

    /**
     * @return coordinate where the robber is, or -1 if not on the board
     * @see #getPreviousRobberHex()
     * @see SOCBoardLarge#getPirateHex()
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
     * set the hexLayout.
     *
     * @param hl  the hex layout.
     *   For {@link #BOARD_ENCODING_ORIGINAL}: if <tt>hl[0]</tt> is {@link #WATER_HEX},
     *    the board is assumed empty and ports arrays won't be filled.
     * @throws UnsupportedOperationException if the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     */
    public void setHexLayout(int[] hl)
        throws UnsupportedOperationException
    {
        hexLayout = hl;

        if (hl[0] == WATER_HEX)
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
            nodeIDtoPortType = new HashMap<Integer, Integer>();
        else
            nodeIDtoPortType.clear();

        for (int i = 0; i < SOCBoard4p.PORTS_FACING_V1.length; ++i)
        {
            final int hexnum = SOCBoard4p.PORTS_HEXNUM_V1[i];
            final int ptype = getPortTypeFromHexType(hexLayout[hexnum]);
            final int[] nodes = getAdjacentNodesToEdge_arr(SOCBoard4p.PORTS_EDGE_V1[i]);
            placePort(ptype, -1, -1, nodes[0], nodes[1]);
        }
    }

    /**
     * On the 6-player (v2 layout) board, each port's type, such as {@link #SHEEP_PORT}.
     * (In the classic 4-player board (v1), these are part of {@link #hexLayout} instead.)
     * Same order as {@link SOCBoard6p#PORTS_FACING_V2}: Clockwise from upper-left.
     *<P>
     * <b>Note:</b> The v3 layout ({@link #BOARD_ENCODING_LARGE}) stores more information
     * within the port layout array.  If you call this method, be sure
     * you are giving all information returned by {@link #getPortsLayout()}, not just the
     * port types.
     *
     * @see #getPortsLayout()
     * @since 1.1.08
     */
    public void setPortsLayout(int[] portTypes)
    {
        portsLayout = portTypes;

        // Clear any previous port layout info
        if (nodeIDtoPortType == null)
            nodeIDtoPortType = new HashMap<Integer,Integer>();
        else
            nodeIDtoPortType.clear();
        for (int i = 0; i < ports.length; ++i)
            ports[i].removeAllElements();

        // Place the new ports
        for (int i = 0; i < SOCBoard6p.PORTS_FACING_V2.length; ++i)
        {
            final int ptype = portTypes[i];
            final int[] nodes = getAdjacentNodesToEdge_arr(SOCBoard6p.PORTS_EDGE_V2[i]);
            placePort(ptype, -1, SOCBoard6p.PORTS_FACING_V2[i], nodes[0], nodes[1]);
        }

        // The v3 layout overrides this method in SOCBoardLarge.
    }

    /**
     * Given a hex type, return the port type.
     * Used in {@link #BOARD_ENCODING_ORIGINAL}
     * to set up port info in {@link #setHexLayout(int[])}.
     * @return the type of port given a hex type;
     *         in range {@link #MISC_PORT} to {@link #WOOD_PORT}.
     *         If called on a non-port hex, returns 0
     *         (which is <tt>MISC_PORT</tt>).
     * @param hexType  the hex type, as in {@link #hexLayout}
     * @see #getHexTypeFromCoord(int)
     * @see #getPortTypeFromNodeCoord(int)
     */
    private int getPortTypeFromHexType(final int hexType)
    {
        int portType;

        if ((hexType >= MISC_PORT_HEX) && (hexType <= 12))
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
     * Set the number layout.
     * Valid for the v1 and v2 encodings, not v3 ({@link #BOARD_ENCODING_LARGE}).
     * For v3, call {@link SOCBoardLarge#setLandHexLayout(int[])} instead.
     *
     * @param nl  the number layout, from {@link #getNumberLayout()}
     * @throws UnsupportedOperationException if the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     */
    public void setNumberLayout(int[] nl)
        throws UnsupportedOperationException
    {
        numberLayout = nl;
    }

    /**
     * set where the robber is
     *
     * @param rh  the new robber hex coordinate; must be &gt; 0, not validated beyond that
     * @param rememberPrevious  Should we remember the old robber hex? (added in 1.1.11)
     * @see #getRobberHex()
     * @see #getPreviousRobberHex()
     * @see SOCBoardLarge#setPirateHex(int, boolean)
     * @throws IllegalArgumentException if <tt>rh</tt> &lt;= 0
     */
    public void setRobberHex(final int rh, final boolean rememberPrevious)
        throws IllegalArgumentException
    {
        if ((rh <= 0) && (rh != prevRobberHex))
            throw new IllegalArgumentException();
        if (rememberPrevious)
            prevRobberHex = robberHex;
        else
            prevRobberHex = -1;
        robberHex = rh;
    }

    /**
     * Get the number of ports on this board.  The original and 6-player
     * board layouts each have a constant number of ports.  The v3 layout
     * ({@link #BOARD_ENCODING_LARGE}) has a varying amount of ports,
     * set during {@link #makeNewBoard(Map)}.
     *
     * @return the number of ports on this board; might be 0 if
     *   {@link #makeNewBoard(Map)} hasn't been called yet.
     * @since 2.0.00
     */
    public abstract int getPortsCount();

    /**
     * @return the list of node coordinates for a type of port;
     *         each element is an Integer
     *
     * @param portType  the type of port;
     *        in range {@link #MISC_PORT} to {@link #WOOD_PORT}.
     * @see #getPortTypeFromNodeCoord(int)
     * @see #getPortsLayout()
     * @see #getPortsEdges()
     */
    public Vector<Integer> getPortCoordinates(int portType)
    {
        return ports[portType];
    }

    /**
     * What type of port is at this node?
     * @param nodeCoord  the coordinates for a node
     * @return the type of port (in range {@link #MISC_PORT} to {@link #WOOD_PORT}),
     *         or -1 if no port at this node
     * @since 1.1.08
     * @see #getPortDescForType(int, boolean)
     */
    public int getPortTypeFromNodeCoord(final int nodeCoord)
    {
        if ((nodeCoord < 0) || (nodeIDtoPortType == null))
            return -1;

        Integer ptype = nodeIDtoPortType.get(Integer.valueOf(nodeCoord));
        if (ptype != null)
            return ptype.intValue();
        else
            return -1;
    }

    /**
     * String keys without/with articles for each port type, for {@link #getPortDescForType(int, boolean)}.
     * Index 0 == {@link #MISC_PORT}, 1 == {@link #CLAY_PORT}, etc.
     * @since 2.0.00
     */
    private static final String[][] PORT_DESC_FOR_TYPE =
    { {
            "game.port.three", "game.port.clay", "game.port.ore", "game.port.sheep",
            "game.port.wheat", "game.port.wood", "game.port.generic"
        }, {
            "game.aport.three", "game.aport.clay", "game.aport.ore", "game.aport.sheep",
            "game.aport.wheat", "game.aport.wood", "game.aport.generic"
    } };

    /**
     * Descriptive text key for a given port type, for i18n
     * {@link soc.util.SOCStringManager#get(String) SOCStringManager.get(key)}.
     *<P>
     * From v1.1.08 through all v1.x.xx, this method was in {@code SOCBoardPanel}.
     *
     * @param portType Port type, as from {@link #getPortTypeFromNodeCoord(int)}.
     *           Should be in range {@link #MISC_PORT} to {@link #WOOD_PORT}, or -1.
     * @param withArticle  If true, string key's value format is "a 2:1 clay port", if false "2:1 Clay port"
     * @return Key for port text description, or {@code null} for -1 (no port at node).
     *    Text format is "3:1 Port" or "2:1 Wood port".  Defaults to generic type if {@code portType} value is unknown.
     * @since 2.0.00
     */
    public static String getPortDescForType(final int portType, final boolean withArticle)
    {
        if (portType == -1)
            return null;  // <--- No port found ---

        final String[] portDescs = PORT_DESC_FOR_TYPE[(withArticle) ? 1 : 0];
        if ((portType >= 0) && (portType <= WOOD_PORT))
            return portDescs[portType];              // "game.port.three", etc
        else
            return portDescs[portDescs.length - 1];  // "game.port.generic"
    }

    /**
     * Given a hex coordinate, return the dice-roll number on that hex
     *
     * @param hex  the coordinates ("ID") for a hex
     *
     * @return the dice-roll number on that hex, or 0 if not a hex coordinate
     * @see #getNumberOnHexFromNumber(int)
     */
    public int getNumberOnHexFromCoord(final int hex)
    {
        if ((hex >= 0) && (hex < hexIDtoNum.length))
            return getNumberOnHexFromNumber(hexIDtoNum[hex]);
        else
            return 0;
    }

    /**
     * Given a hex number (index), return the (dice-roll) number on that hex
     *
     * @param hex  the number of a hex, or -1 if invalid
     *
     * @return the dice-roll number on that hex, or 0
     * @see #getNumberOnHexFromCoord(int)
     */
    public int getNumberOnHexFromNumber(final int hex)
    {
        if ((hex < 0) || (hex >= numberLayout.length))
            return 0;
        int num = numberLayout[hex];

        if (num < 0)
            return 0;
        else
            return num;
    }

    /**
     * Given a hex coordinate, return the hex number (index).
     * Valid only for the v1 and v2 board encoding, not v3.
     *
     * @param hexCoord  the coordinates ("ID") for a hex
     * @return the hex number (index in numberLayout), or -1 if hexCoord isn't a hex coordinate on the board
     * @see #getHexTypeFromCoord(int)
     * @since 1.1.08
     * @throws UnsupportedOperationException if the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     */
    public int getHexNumFromCoord(final int hexCoord)
        throws UnsupportedOperationException
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
     *         or {@link #WATER_HEX}
     *         or -1 for invalid hex coordinate
     *
     * @see #getHexNumFromCoord(int)
     * @see #getLandHexCoords()
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
     * @see #getHexTypeFromCoord(int)
     */
    public int getHexTypeFromNumber(final int hex)
    {
        if ((hex < 0) || (hex >= hexLayout.length))
            return -1;

        final int hexType = hexLayout[hex];

        if (hexType < MISC_PORT_HEX)
        {
            return hexType;
        }
        else if ((hexType >= MISC_PORT_HEX) && (hexType <= 12))
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
     * Put a piece on the board.
     *<P>
     * Call this only after calling
     * {@link SOCPlayer#putPiece(SOCPlayingPiece, boolean) pl.putPiece(pp, isTempPiece)}
     * for each player.
     *
     * @param pp  Piece to place on the board; coordinates are not checked for validity
     * @see #removePiece(SOCPlayingPiece)
     */
    public void putPiece(SOCPlayingPiece pp)
    {
        switch (pp.getType())
        {
        case SOCPlayingPiece.SHIP:  // fall through to ROAD
        case SOCPlayingPiece.ROAD:
            roadsAndShips.addElement((SOCRoutePiece) pp);
            break;

        case SOCPlayingPiece.SETTLEMENT:
            settlements.addElement((SOCSettlement) pp);
            break;

        case SOCPlayingPiece.CITY:
            cities.addElement((SOCCity) pp);
            break;

        }
    }

    /**
     * remove a piece from the board.
     *<P>
     * If you're calling {@link SOCPlayer#undoPutPiece(SOCPlayingPiece)},
     * call this method first.
     * @param piece  Piece to be removed from the board
     *     (identified by its piece type, coordinate, and player number)
     * @see #putPiece(SOCPlayingPiece)
     */
    public void removePiece(SOCPlayingPiece piece)
    {
        // Vector.removeElement works because SOCPlayingPiece.equals compares
        // the piece type, player number, and coordinate.
        // Even if piece isn't the same object (reference) as the one in
        // the vector, it's removed from the vector if those fields are equal.

        switch (piece.getType())
        {
        case SOCPlayingPiece.SHIP:  // fall through to ROAD
        case SOCPlayingPiece.ROAD:
            roadsAndShips.removeElement(piece);
            break;

        case SOCPlayingPiece.SETTLEMENT:
            settlements.removeElement(piece);
            break;

        case SOCPlayingPiece.CITY:
            cities.removeElement(piece);
            break;
        }
    }

    /**
     * Get the list of roads and ships.
     *<P>
     * Before v2.0.00 this method was {@code getRoads}.
     */
    public Vector<SOCRoutePiece> getRoadsAndShips()
    {
        return roadsAndShips;
    }

    /**
     * get the list of settlements
     */
    public Vector<SOCSettlement> getSettlements()
    {
        return settlements;
    }

    /**
     * get the list of cities
     */
    public Vector<SOCCity> getCities()
    {
        return cities;
    }

    /**
     * Width of this board in half-hex coordinate units (not in number of hexes across).
     * The maximum column coordinate.
     * For the classic 4-player board, see also {@link #WIDTH_VISUAL_ORIGINAL}.
     * @since 1.1.06
     */
    public int getBoardWidth()
    {
        return boardWidth;
    }

    /**
     * Height of this board in half-hex coordinate units (not in number of hexes across).
     * The maximum row coordinate.
     * For the classic 4-player board, see also {@link #HEIGHT_VISUAL_ORIGINAL}.
     * @since 1.1.06
     */
    public int getBoardHeight()
    {
        return boardHeight;
    }

    /**
     * For subclass constructor usage, set the board height and width.
     * Does not set node or edge ranges (minNode, maxEdge, etc) because these
     * limits aren't used in all encodings.
     * @param boardH  New maximum row coordinate, for {@link #getBoardHeight()}
     * @param boardW  New maximum column coordinate, for {@link #getBoardWidth()}
     * @since 2.0.00
     */
    protected void setBoardBounds(final int boardH, final int boardW)
    {
        boardHeight = boardH;
        boardWidth = boardW;
    }

    /**
     * Get the encoding format of this board (for coordinates, etc).
     * The board size determines the required encoding format.
     *<P>
     * See the encoding constants' javadocs for more documentation:
     *<UL>
     * <LI> v1 - Original format: hexadecimal 0x00 to 0xFF.
     *       {@link #BOARD_ENCODING_ORIGINAL}<BR>
     *       Coordinate range is 0 to 15 (in decimal).
     *       Port types and facings encoded within {@link #hexLayout}.
     * <LI> v2 - 6-player board, variant of original format: hexadecimal 0x00 to 0xFF.
     *       {@link #BOARD_ENCODING_6PLAYER}<BR>
     *       Coordinate range is 0 to 15 (in decimal).
     *       Port types stored in {@link #portsLayout}.<BR>
     *       Added in 1.1.08.
     * <LI> v3 - Large sea board ({@link SOCBoardLarge}).
     *       {@link #BOARD_ENCODING_LARGE}<BR>
     *       Coordinate range for rows,columns is each 0 to 255 decimal,
     *       or altogether 0x0000 to 0xFFFF hex.
     *       Arbitrary mix of land and water tiles.<BR>
     *       Added in 2.0.00.
     *       Activated with {@link SOCGameOption} <tt>"SBL"</tt>.
     *</UL>
     * @return board coordinate-encoding format, from the list above
     * @see SOCBoard.BoardFactory#createBoard(Map, boolean, int)
     * @since 1.1.06
     */
    public int getBoardEncodingFormat()
    {
        return boardEncodingFormat;
    }

    /**
     * Adjacent node coordinates to an edge, within valid range to be on the board.
     *<P>
     * For v1 and v2 encoding, this range is {@link SOCBoard4p#MINNODE_V1} to {@link #MAXNODE},
     *   or {@link SOCBoard6p#MINNODE_V2} to {@link #MAXNODE}.
     * For v3 encoding, nodes are around all valid land or water hexes,
     *   and the board size is {@link #getBoardHeight()} x {@link #getBoardHeight()}.
     * @return the nodes that touch this edge, as a Vector of Integer coordinates
     * @see #getAdjacentNodesToEdge_arr(int)
     */
    public Vector<Integer> getAdjacentNodesToEdge(final int coord)
    {
        Vector<Integer> nodes = new Vector<Integer>(2);
        final int[] narr = getAdjacentNodesToEdge_arr(coord);
        if ((narr[0] >= minNode) && (narr[0] <= MAXNODE))
            nodes.addElement(Integer.valueOf(narr[0]));
        if ((narr[1] >= minNode) && (narr[1] <= MAXNODE))
            nodes.addElement(Integer.valueOf(narr[1]));
        return nodes;
    }

    /**
     * Adjacent node coordinates to an edge.
     * Does not check against range {@link SOCBoard4p#MINNODE_V1} to {@link #MAXNODE},
     * so nodes in the water (off the land board) may be returned.
     * @param coord  Edge coordinate; not checked for validity
     * @return the nodes that touch this edge, as an array of 2 integer coordinates
     * @see #getAdjacentNodesToEdge(int)
     * @see #getAdjacentNodeFarEndOfEdge(int, int)
     * @since 1.1.08
     */
    public int[] getAdjacentNodesToEdge_arr(final int coord)
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
     * Get an edge's other adjacent node (its other end).
     * Calls {@link #getAdjacentNodesToEdge_arr(int)} and
     * returns the node that isn't <tt>nodeCoord</tt>.
     * @param edgeCoord  Edge coordinate; not checked for validity
     * @param nodeCoord  Node at one end of <tt>edgeCoord</tt>; the opposite end node
     *           will be returned.
     * @return the edge's other end node, opposite <tt>nodeCoord</tt>
     * @since 2.0.00
     */
    public int getAdjacentNodeFarEndOfEdge(final int edgeCoord, final int nodeCoord)
    {
        final int[] nodes = getAdjacentNodesToEdge_arr(edgeCoord);
        if (nodeCoord == nodes[0])
            return nodes[1];
        else
            return nodes[0];
    }

    /**
     * Get the edge coordinates of the 2 to 4 edges adjacent to this edge.
     * @param coord  Edge coordinate; for the 6-player encoding, use 0, not -1, for edge 0x00.
     *    Not checked for validity.
     * @return the valid adjacent edges to this edge, as a Vector of 2 to 4 Integer coordinates
     */
    public Vector<Integer> getAdjacentEdgesToEdge(int coord)
    {
        Vector<Integer> edges = new Vector<Integer>(4);
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
                edges.addElement(Integer.valueOf(tmp));
            }

            tmp = coord + 0x01;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(Integer.valueOf(tmp));
            }

            tmp = coord + 0x10;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(Integer.valueOf(tmp));
            }

            tmp = coord - 0x01;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(Integer.valueOf(tmp));
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
                edges.addElement(Integer.valueOf(tmp));
            }

            tmp = coord + 0x01;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(Integer.valueOf(tmp));
            }

            tmp = coord + 0x11;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(Integer.valueOf(tmp));
            }

            tmp = coord - 0x01;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(Integer.valueOf(tmp));
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
                edges.addElement(Integer.valueOf(tmp));
            }

            tmp = coord + 0x11;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(Integer.valueOf(tmp));
            }

            tmp = coord + 0x10;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(Integer.valueOf(tmp));
            }

            tmp = coord - 0x11;

            if ((tmp >= minEdge) && (tmp <= maxEdge))
            {
                edges.addElement(Integer.valueOf(tmp));
            }
        }

        return edges;
    }

    /**
     * Get the coordinates of the valid hexes adjacent to this node.
     * These hexes may contain land or water.
     * @param coord  Node coordinate.  Is not checked for validity.
     * @return the coordinates (Integers) of the 1 to 3 hexes touching this node
     */
    public Vector<Integer> getAdjacentHexesToNode(int coord)
    {
        Vector<Integer> hexes = new Vector<Integer>(3);
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
                hexes.addElement(Integer.valueOf(tmp));
            }

            tmp = coord + 0x10;

            if ((tmp >= MINHEX) && (tmp <= MAXHEX))
            {
                hexes.addElement(Integer.valueOf(tmp));
            }

            tmp = coord - 0x12;

            if ((tmp >= MINHEX) && (tmp <= MAXHEX))
            {
                hexes.addElement(Integer.valueOf(tmp));
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
                hexes.addElement(Integer.valueOf(tmp));
            }

            tmp = coord + 0x01;

            if ((tmp >= MINHEX) && (tmp <= MAXHEX))
            {
                hexes.addElement(Integer.valueOf(tmp));
            }

            tmp = coord - 0x01;

            if ((tmp >= MINHEX) && (tmp <= MAXHEX))
            {
                hexes.addElement(Integer.valueOf(tmp));
            }
        }

        return hexes;
    }

    /**
     * Get the valid edge coordinates adjacent to this node.
     * Calls {@link #getAdjacentEdgeToNode(int, int)}.
     * @return the edges touching this node, as a Vector of Integer coordinates
     * @see #getAdjacentEdgeToNode(int, int)
     */
    public Vector<Integer> getAdjacentEdgesToNode(final int coord)
    {
        Vector<Integer> edges = new Vector<Integer>(3);
        int[] edgea = getAdjacentEdgesToNode_arr(coord);
        for (int i = edgea.length - 1; i>=0; --i)
            if (edgea[i] != -9)
                edges.addElement(Integer.valueOf(edgea[i]));
        return edges;
    }

    /**
     * Get the valid edge coordinates adjacent to this node.
     * In the 6-player layout, valid land nodes/edges are
     * found on the outer ring of the board coordinate
     * system, but some of their adjacent nodes/edges may be
     * "off the board" and thus invalid.
     * Calls {@link #getAdjacentEdgeToNode(int, int)}.
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
     * @return  is the edge in-bounds and adjacent?
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
     * Calls {@link #getAdjacentNodeToNode(int, int)}.
     * @return the nodes adjacent to this node, as a Vector of Integer coordinates
     * @see #isNodeAdjacentToNode(int, int)
     */
    public Vector<Integer> getAdjacentNodesToNode(final int coord)
    {
        Vector<Integer> nodes = new Vector<Integer>(3);
        int[] nodea = getAdjacentNodesToNode_arr(coord);
        for (int i = nodea.length - 1; i>=0; --i)
            if (nodea[i] != -9)
                nodes.addElement(Integer.valueOf(nodea[i]));
        return nodes;
    }

    /**
     * Get the valid node coordinates adjacent to this node.
     * In the 6-player layout, valid land nodes/edges are
     * found on the outer ring of the board coordinate
     * system, but some of their adjacent nodes/edges may be
     * "off the board" and thus invalid.
     *<P>
     * Calls {@link #getAdjacentNodeToNode(int, int)}.
     * @param coord  Node coordinate.  Is not checked for validity.
     * @return the nodes touching this node, as an array of 3 coordinates.
     *    Unused elements of the array are set to -9.
     * @see #isNodeAdjacentToNode(int, int)
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
     * Are these nodes adjacent to each other?
     * @param nodeA  One node coordinate; not validated
     * @param nodeB  Other node coordinate; not validated
     * @return  True if {@link #getAdjacentNodesToNode(int) getAdjacentNodesToNode(nodeA)}
     *            contains <tt>nodeB</tt>
     * @see #getAdjacentNodesToNode(int)
     * @since 2.0.00
     */
    public final boolean isNodeAdjacentToNode(final int nodeA, final int nodeB)
    {
        for (int i = 0; i < 3; ++i)
        {
            if (getAdjacentNodeToNode(nodeA, i) == nodeB)
                return true;
        }
        return false;
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
     * @see #isNodeAdjacentToNode(int, int)
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
        case 0:  // NW or SW (upper-left or lower-left edge)
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
     * @since 1.1.12
     */
    public int getAdjacentNodeToNode2Away(final int nodeCoord, final int facing)
        throws IllegalArgumentException
    {
        if ((facing < 1) || (facing > 6))
            throw new IllegalArgumentException("bad facing: " + facing);

        // See RST dissertation figure A.2.
        int node = nodeCoord + NODE_2_AWAY[facing];
        if (! isNodeOnLand(node))
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
     * @param hexCoord Coordinate ("ID") of this hex; not checked for validity
     * @param includeWater Should water hexes be returned (not only land ones)?
     *         Port hexes are water hexes.
     * @return the hexes that touch this hex, as a Vector of Integer coordinates,
     *         or null if none are adjacent (will <b>not</b> return a 0-length vector)
     * @since 1.1.07
     */
    public Vector<Integer> getAdjacentHexesToHex(final int hexCoord, final boolean includeWater)
    {
        Vector<Integer> hexes = new Vector<Integer>();

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
        (Vector<Integer> addTo, final boolean includeWater, int hexCoord, final int d1, final int d2)
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
                || ((hexLayout[hexIDtoNum[hexCoord]] <= MAX_LAND_HEX)
                    && (hexLayout[hexIDtoNum[hexCoord]] != WATER_HEX)) ))
            addTo.addElement(Integer.valueOf(hexCoord));
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
     * @since 1.1.08
     * @throws IllegalArgumentException if dir &lt; 0 or dir &gt; 5
     */
    public int getAdjacentNodeToHex(final int hexCoord, final int dir)
        throws IllegalArgumentException
    {
        if ((dir >= 0) && (dir < HEXNODES.length))
            return hexCoord + HEXNODES[dir];
        throw new IllegalArgumentException("dir");
    }

    /**
     * A list of the node coordinates adjacent to this hex in all 6 directions.
     * Since all hexes have 6 nodes, all node coordinates are valid
     * if the hex coordinate is valid.
     *
     * @param hexCoord Coordinate of this hex
     * @return {@link ArrayList} with the Node coordinate in all 6 directions,
     *           clockwise from top (northern point of hex):
     *           0 is north, 1 is northeast, etc, 5 is northwest.
     *           Never returns {@code null} or empty.
     * @since 2.0.00
     * @see #getAdjacentNodesToHex_arr(int)
     * @see #getAdjacentNodeToHex(int, int)
     */
    public List<Integer> getAdjacentNodesToHex(final int hexCoord)
    {
        final int[] arr = getAdjacentNodesToHex_arr(hexCoord);
        final ArrayList<Integer> li = new ArrayList<Integer>(6);
        for (int dir = 0; dir < 6; ++dir)
            li.add(Integer.valueOf(arr[dir]));

        return li;
    }

    /**
     * An array of the node coordinates adjacent to this hex in all 6 directions.
     * Since all hexes have 6 nodes, all node coordinates are valid
     * if the hex coordinate is valid.
     *
     * @param hexCoord Coordinate of this hex
     * @return Array with the Node coordinate in all 6 directions,
     *           clockwise from top (northern point of hex):
     *           0 is north, 1 is northeast, etc, 5 is northwest.
     *           Never returns {@code null} or empty.
     * @since 2.0.00
     * @see #getAdjacentNodesToHex(int)
     * @see #getAdjacentNodeToHex(int, int)
     */
    public int[] getAdjacentNodesToHex_arr(final int hexCoord)
    {
        int[] node = new int[6];
        for (int dir = 0; dir < 6; ++dir)
            node[dir] = hexCoord + HEXNODES[dir];

        return node;
    }

    /**
     * The hex touching an edge in a given direction,
     * either along its length or at one end node.
     * Each edge touches up to 4 valid hexes.
     * @param edgeCoord The edge's coordinate. {@link #maxEdge} is 0xCC in v1 and v2 encoding.
     * @param facing  Facing from edge; 1 to 6.
     *           This will be either a direction perpendicular to the edge,
     *           or towards one end. Each end has two facing directions angled
     *           towards it; both will return the same hex.
     *           Facing 2 is {@link #FACING_E}, 3 is {@link #FACING_SE}, 4 is SW, etc.
     * @return hex coordinate of hex in the facing direction,
     *           or 0 if a hex digit would be below 0 after subtraction
     *           or above F after addition.
     * @throws IllegalArgumentException if facing &lt; 1 or facing &gt; 6
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
     * @return  Settlement or city at <tt>nodeCoord</tt>, or null
     */
    public SOCPlayingPiece settlementAtNode(final int nodeCoord)
    {
        for (SOCSettlement p : settlements)
        {
            if (nodeCoord == p.getCoordinates())
            {
                return p;  // <-- Early return: Found it ---
            }
        }

        for (SOCCity p : cities)
        {
            if (nodeCoord == p.getCoordinates())
            {
                return p;  // <-- Early return: Found it ---
            }
        }

        return null;
    }

    /**
     * If there's a road or ship placed at this edge, find it.
     *<P>
     * Before v2.0.00 this method was {@code roadAtEdge}.
     *
     * @param edgeCoord Location coordinate (as returned by SOCBoardPanel.findEdge)
     * @return road or ship, or null.  Use {@link SOCPlayingPiece#getType()}
     *   or {@link SOCRoutePiece#isRoadNotShip()} to determine the returned piece type.
     *   At most one road or ship can be placed at any one edge.
     */
    public SOCRoutePiece roadOrShipAtEdge(int edgeCoord)
    {
        for (SOCRoutePiece p : roadsAndShips)
        {
            if (edgeCoord == p.getCoordinates())
                return p;  // <-- Early return: Found it ---
        }

        return null;
    }

    /**
     * Is this node on a land hex, and thus a legal settlement location?
     * @param node  Node coordinate, not checked for validity
     * @return  True if node is on a land hex (including if coastal), not water,
     *     and thus a legal settlement coordinate, based on the set of all nodes on land
     * @see #initPlayerLegalSettlements()
     */
    public boolean isNodeOnLand(int node)
    {
        if (node < 0)
            return false;

        return nodesOnLand.contains(Integer.valueOf(node));
    }

    /**
     * Get the dice roll numbers for hexes adjacent to this node.
     * @return a string representation of a node coordinate's adjacent hex dice roll numbers,
     *     such as "5/3/6", or if no hexes adjacent, "(node 0x___)"
     * @see #getAdjacentHexesToNode(int)
     */
    public String nodeCoordToString(int node)
    {
        String str;
        Vector<Integer> hexes = getAdjacentHexesToNode(node);
        if (hexes.isEmpty())
        {
            // Early Return: No adjacent hexes
            return "(node 0x" + Integer.toHexString(node) + ")";
        }

        int hex = hexes.get(0).intValue();
        int number = getNumberOnHexFromCoord(hex);

        if (number == 0)
        {
            str = "-";
        }
        else
        {
            str = Integer.toString(number);
        }

        for (int i = 1; i<hexes.size(); ++i)
        {
            hex = hexes.get(i).intValue();
            number = getNumberOnHexFromCoord(hex);

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
     * Get the dice roll numbers for hexes on either side of this edge.
     * @return a string representation of an edge coordinate's dice numbers, such as "5/3";
     *      if a hex isn't a land hex, its number will be 0.
     * @see #getNumberOnHexFromCoord(int)
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

    //
    // Nested classes for board factory
    //

    /**
     * Board Factory for creating new boards for games at the client or server.
     * (The server's version of {@link SOCBoardLarge} isolates makeNewBoard methods.)
     * Called by game constructor via <tt>static {@link SOCGame#boardFactory}</tt>.
     *<P>
     * The default factory is {@link SOCBoard.DefaultBoardFactory}.
     * For a server-side board factory, see {@link soc.server.SOCBoardAtServer.BoardFactoryAtServer}.
     * @author Jeremy D Monin
     * @since 2.0.00
     */
    public interface BoardFactory
    {
        /**
         * Create a new Settlers of Catan Board based on <tt>gameOpts</tt>; this is a factory method.
         * @param gameOpts  game's options if any, otherwise null
         * @param largeBoard  true if a Sea Board should be created: {@link SOCBoardLarge} with
         *     v3 encoding {@link SOCBoard#BOARD_ENCODING_LARGE BOARD_ENCODING_LARGE}, game rules for
         *     ships, etc. If true, assumes {@code gameOpts != null} and {@code gameOpts} contains {@code "SBL"}.
         * @param maxPlayers Maximum players; must be default 4, or 6 from SOCGameOption "PL" &gt; 4 or "PLB"
         * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
         */
        SOCBoard createBoard
            (final Map<String,SOCGameOption> gameOpts, final boolean largeBoard, final int maxPlayers)
            throws IllegalArgumentException;

    }  // nested class BoardFactory

    /**
     * Default implementation of {@link BoardFactory}, used at client.
     * Called by game constructor via <tt>static {@link SOCGame#boardFactory}</tt>.
     * @author Jeremy D Monin
     * @since 2.0.00
     */
    public static class DefaultBoardFactory implements BoardFactory
    {
        /**
         * Create a new Settlers of Catan Board based on <tt>gameOpts</tt>; this is a factory method.
         * Static for fallback access from other factory implementations.
         *
         * @param gameOpts  if game has options, map of {@link SOCGameOption}; otherwise null.
         * @param largeBoard  true if {@link SOCBoardLarge} should be used (v3 encoding)
         * @param maxPlayers Maximum players; must be default 4, or 6 from SOCGameOption "PL" &gt; 4 or "PLB"
         * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
         */
        public static SOCBoard staticCreateBoard
            (final Map<String,SOCGameOption> gameOpts, final boolean largeBoard, final int maxPlayers)
            throws IllegalArgumentException
        {
            if (! largeBoard)
            {
                if (maxPlayers == 6)
                    return new SOCBoard6p(gameOpts);
                else
                    return new SOCBoard4p(gameOpts);
            } else {
                return new SOCBoardLarge(gameOpts, maxPlayers);
            }
        }

        /**
         * Create a new Settlers of Catan Board based on <tt>gameOpts</tt>; this is a factory method.
         *<P>
         * From v1.1.11 through all 1.x.xx, this was SOCBoard.createBoard.  Moved to new factory class in 2.0.00.
         *
         * @param gameOpts  if game has options, map of {@link SOCGameOption}; otherwise null.
         * @param largeBoard  true if {@link SOCBoardLarge} should be used (v3 encoding)
         * @param maxPlayers Maximum players; must be default 4, or 6 from SOCGameOption "PL" &gt; 4 or "PLB"
         * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
         */
        public SOCBoard createBoard
            (final Map<String,SOCGameOption> gameOpts, final boolean largeBoard, final int maxPlayers)
            throws IllegalArgumentException
        {
            return staticCreateBoard(gameOpts, largeBoard, maxPlayers);
        }

    }  // nested class DefaultBoardFactory

}
