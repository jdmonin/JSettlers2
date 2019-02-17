/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2011-2019 Jeremy D Monin <jeremy@nand.net>
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import soc.util.IntPair;

/**
 * Sea board layout: A representation of a larger (up to 127 x 127 hexes) JSettlers board,
 * with an arbitrary mix of land and water tiles.
 * Implements {@link SOCBoard#BOARD_ENCODING_LARGE}.
 * Activated with {@link SOCGameOption} {@code "SBL"}.
 * For the board layout geometry, see the "Coordinate System" section here.
 *<P>
 * A {@link SOCGame} uses this board; the board is not given a reference to the game, to enforce layering
 * and keep the board logic simple.  Game rules should be enforced at the game, not the board.
 * Calling board methods won't change the game state.
 *<P>
 * To generate a new game's board layout, use subclass {@code soc.server.SOCBoardAtServer}.
 * Game boards are initially all water.  The layout contents are set up later by calling
 * {@code SOCBoardAtServer.makeNewBoard(Map)} when the game is about to begin,
 * then sent to the clients over the network.  The client calls methods such as {@link #setLandHexLayout(int[])},
 * {@link #setPortsLayout(int[])}, {@link SOCGame#putPiece(SOCPlayingPiece)}, and
 * {@link #setLegalSettlements(Collection, int, HashSet[])} with data from the server.
 *<P>
 * See {@code SOCBoardAtServer}'s class javadoc, and its {@code makeNewBoard(Map)} javadoc, for more details on layout creation.
 *<P>
 * On this large sea board, there can optionally be multiple "land areas"
 * (groups of islands, or subsets of islands), if {@link #getLandAreasLegalNodes()} != null.
 * Land areas are groups of nodes on land; call {@link #getNodeLandArea(int)} to find a node's land area number.
 * The starting land area is {@link #getStartingLandArea()}, if players must start in a certain area.
 * In some game scenarios, players and the robber can be
 * {@link #getPlayerExcludedLandAreas() excluded} from placing in some land areas.
 *<P>
 * Server and client must be v2.0.00 or newer ({@link #MIN_VERSION}).
 * The board layout is sent using {@link #getLandHexLayout()} and {@link #getPortsLayout()},
 * followed by the robber hex and pirate hex (if they're &gt; 0),
 * and then (a separate message) the legal settlement/city nodes and land areas.
 *<P>
 * Road and ship pieces extend the {@link SOCRoutePiece} class; road-related getters/setters
 * will work on both types, but check {@link SOCRoutePiece#isRoadNotShip()} to differentiate.
 * You cannot place both a road and a ship on the same coastal edge coordinate.
 *
 *<H4> Layout Parts: </H4>
 * Some parts of the layout are stored and sent as int parameters or lists of ints.
 * Each part has a key name like {@code "LH"} or {@code "RH"}. See
 * {@link #getAddedLayoutParts()} for more explanation.
 *<P>
 * Some scenarios may add other "layout parts" related to their scenario board layout.
 * For example, scenario {@code _SC_PIRI} adds {@code "PP"} for the path the pirate fleet follows.
 * Call {@link #getAddedLayoutPart(String)} to get one of these Added Layout Parts when needed.
 * That method's javadoc also documents the list of <B>all known Layout Parts</B>.
 *
 * <h4> Geometry/Navigation methods: </h4>
 *<br><table border=1>
 *<TR><td>&nbsp;</td><td colspan=3>Adjacent to a:</td></TR>
 *<TR><td>Get the:</td> <td> Hex </td><td> Edge </td><td> Node </td></TR>
 *<TR><td> Hex </td>
 *    <td><!-- Hex adjac to hex -->
 *      {@link #getAdjacentHexesToHex(int, boolean)} <br>
 *      {@link #isHexAdjacentToHex(int, int)}
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
 *      {@link #getAdjacentEdgesToHex_arr(int)} <br>
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
 *      {@link #getAdjacentEdgesToNode_coastal(int)} <br>
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
 *      {@link #isHexInBounds(int, int)} <br>
 *      {@link #isHexOnLand(int)} <br>
 *      {@link #isHexOnWater(int)} <br>
 *      {@link #isHexCoastline(int)} <br>
 *      {@link #getNumberOnHexFromCoord(int)} <br>
 *      {@link #getNumberOnHexFromNumber(int)} <br>
 *      {@link #getHexTypeFromCoord(int)} <br>
 *      {@link #getHexTypeFromNumber(int)} <br>
 *      {@link #getRobberHex()} <br>
 *      {@link #getPirateHex()} <br>
 *      {@link #getPreviousRobberHex()} <br>
 *      {@link #getPreviousPirateHex()} <br>
 *      {@link #getLandHexLayout()} <br>
 *      {@link #getLandHexCoords()} <br>
 *      {@link #getLandHexCoordsSet()} <br>
 *      {@link #getFogHiddenHexes()} <br>
 *      {@link #isHexAtBoardMargin(int)} <br>
 *      {@link #isHexInLandAreas(int, int[])}
 *    </td>
 *    <td><!-- edge -->
 *      {@link #isEdgeInBounds(int, int)} <br>
 *      {@link #isEdgeCoastline(int)} <br>
 *      {@link #isEdgeLegalRoad(int)} <br>
 *      {@link #roadOrShipAtEdge(int)} <br>
 *      {@link #getPortsEdges()}
 *    </td>
 *    <td><!-- node -->
 *      {@link #isNodeInBounds(int, int)} <br>
 *      {@link #isNodeOnLand(int)} <br>
 *      {@link #isNodeCoastline(int)} <br>
 *      {@link #settlementAtNode(int)} <br>
 *      {@link #getPortEdgeFromNode(int)} <br>
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
 * (between two hex coordinates, which are to the west and east of
 * the edge); vertical edge row coordinates are odd like hexes.
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
    /** SOCBoardLarge serial, to suppress warning. SOCBoardLarge isn't sent over the network as a serialized object. */
    private static final long serialVersionUID = 2000L;

    /** Minimum client and server version required: v2.0.00. */
    public static final int MIN_VERSION = 2000;

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
     * Hex type for the Fog Hex, with actual type revealed when roads or ships are placed.
     * Used with some scenarios (see {@link SOCGameOption#K_SC_FOG}).
     * Bots can treat this as {@link SOCBoard#DESERT_HEX DESERT_HEX} until revealed.
     *<P>
     * To simplify the bot, client, and network, hexes can be hidden only at the server during
     * {@link #makeNewBoard(Map)} before the board layout is finished and sent to the client.
     *<P>
     * The numeric value (8) for <tt>FOG_HEX</tt> is the same as
     * the v1/v2 encoding's {@link SOCBoard#CLAY_PORT_HEX}, but the
     * ports aren't encoded as hexes for this encoding, so there is no ambiguity
     * as long as callers of {@link #getHexTypeFromCoord(int)}
     * check the board encoding format.
     *
     * @see #revealFogHiddenHexPrep(int)
     * @see #revealFogHiddenHex(int, int, int)
     */
    public static final int FOG_HEX = 8;

    /**
     * Maximum land hex type (== {@link #FOG_HEX}) for this encoding.
     * If you add a hex type, search for this and for FOG_HEX for likely changes.
     * Be sure to also update the client's SOCBoardPanel.loadImages.
     */
    protected static final int MAX_LAND_HEX_LG = FOG_HEX;

    /**
     * Default size of the large board.
     * Can override in constructor.
     * See {@link SOCBoard#getBoardHeight() getBoardHeight()}, {@link SOCBoard#getBoardWidth() getBoardWidth()}.
     */
    public static final int BOARDHEIGHT_LARGE = 16, BOARDWIDTH_LARGE = 18;

    /**
     * Special Edge Type code that gives a development card when player reaches a special edge.
     * After the player gets their reward, the special edge is cleared, no other player can be rewarded there.
     * These edges are Added Layout Part {@code "CE"}.
     *
     *<H3>Special Edges:</h3>
     * For some scenarios, some edges have a special purpose or reward.
     * For example, {@link SOCScenario#K_SC_FTRI SC_FTRI} gives dev cards
     * or special victory points when a ship reaches one.
     *<P>
     * Some related methods:
     *<UL>
     * <LI> {@link #hasSpecialEdges()} - does this board have any special edges?
     * <LI> {@link #getSpecialEdgeType(int)} - check if an edge is special
     * <LI> {@link #getSpecialEdges()} - get all special edges, if any
     * <LI> {@link #setSpecialEdge(int, int)} - set or clear an edge
     * <LI> {@link #clearSpecialEdges(int)} - clear all edges of one special type
     *</UL>
     *<P>
     * The Special Edge Types are constants such as {@link #SPECIAL_EDGE_DEV_CARD}
     * or {@link #SPECIAL_EDGE_SVP} used by game and board logic.  Game events or
     * {@link SOCScenarioPlayerEvent}s can happen when something occurs at such an edge.
     * Often these will clear the edge's type, it will no longer be special.
     *<P>
     * During board setup, lists of edges of a special type are sent with the board layout as
     * Added Layout Parts such as {@code "CE"} and {@code "VE"} used with {@link #getAddedLayoutPart(String)}.
     * Calls to {@link #setAddedLayoutParts(HashMap)} or {@link #setAddedLayoutPart(String, int[])} add them
     * to the Special Edges mapping if it recognizes the layout part as a special edge type.
     *<P>
     * During game play, when a Special Edge is set or cleared the server will send a message to the game
     * with the edge coordinate and its new special type code.
     *<P>
     * If you add a Special Edge Type code, it should probably be added to:
     *<UL>
     * <LI> {@link #setAddedLayoutPart(String, int[])}
     * <LI> {@link #setAddedLayoutParts(HashMap)}
     * <LI> {@link #SPECIAL_EDGE_TYPES}
     * <LI> {@link SOCPlayer#putPiece_roadOrShip_checkNewShipTradeRouteAndSpecialEdges(SOCShip, SOCBoardLarge, boolean)}
     * <LI> SOCBoardPanel.drawBoardEmpty_specialEdges
     * <LI> SOCBoardPanel.BoardToolTip.handleHover
     * <LI> Any game or board code that needs to check for relevant actions at edges
     *</UL>
     * To find where to add the new type, search those for {@code SPECIAL_EDGE_DEV_CARD}.
     *<P>
     * Not many board layouts and scenarios have Special Edges, so usually {@link #hasSpecialEdges()} == {@code false}.
     */
    public static final int SPECIAL_EDGE_DEV_CARD = 1;

    /**
     * Special Edge Type code that gives a Special Victory Point when player reaches a special edge.
     * After the player gets their reward, the special edge is cleared, no other player can be rewarded there.
     * These edges are Added Layout Part {@code "VE"}.
     *<P>
     * For more information on Special Edges, see {@link #SPECIAL_EDGE_DEV_CARD}.
     */
    public static final int SPECIAL_EDGE_SVP = 2;

    /**
     * Names of optional Added Layout Parts which contain Special Edges when present, currently {@code "CE"} and {@code "VE"}.
     * {@link #SPECIAL_EDGE_TYPES}[i] is the Special Edge type for {@code SPECIAL_EDGE_LAYOUT_PARTS}[i],
     * edge coordinates of that special type are {@link #getAddedLayoutPart(String) getAddedLayoutPart}
     * ({@code SPECIAL_EDGE_LAYOUT_PARTS}[i]).
     *<P>
     * For more information on Special Edges, see {@link #SPECIAL_EDGE_DEV_CARD}.
     */
    public static final String[] SPECIAL_EDGE_LAYOUT_PARTS = { "CE", "VE" };

    /**
     * Special Edge type codes, such as {@link #SPECIAL_EDGE_DEV_CARD}, for
     * all Added Layout Parts which contain Special Edges when present.
     * {@code SPECIAL_EDGE_TYPES}[i] is the Special Edge type code for {@link #SPECIAL_EDGE_LAYOUT_PARTS}[i].
     */
    public static final int[] SPECIAL_EDGE_TYPES = { SPECIAL_EDGE_DEV_CARD, SPECIAL_EDGE_SVP };

    /**
     * For {@link #getAdjacentHexesToHex(int, boolean)}, the offsets to add to the hex
     * row and column to get all adjacent hex coords, starting at
     * index 0 at the northeastern edge of the hex and going clockwise.
     *<P>
     * Coordinate offsets - adjacent hexes to hex:<PRE>
     *   (-2,-1) (-2,+1)
     *
     * (0,-2)   x   (0,+2)
     *
     *   (+2,-1) (+2,+1)  </PRE>
     *<P>
     * For each direction, array of delta to the row & column. (Not to the encoded coordinate.)
     * Indexed by the facing direction - 1: {@link SOCBoard#FACING_NE FACING_NE} is 1,
     * {@link SOCBoard#FACING_E FACING_E} is 2, etc; {@link SOCBoard#FACING_NW FACING_NW} is 6.
     * Index here for {@link SOCBoard#FACING_NE FACING_NE} is 0, {@link SOCBoard#FACING_NW FACING_NW} is 5.
     */
    private final static int[][] A_HEX2HEX = {
        { -2, +1 }, { 0, +2 }, { +2, +1 },  // NE, E, SE
        { +2, -1 }, { 0, -2 }, { -2, -1 }   // SW, W, NW
    };

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
    //    The unused elements are wasted space locally but aren't sent over the network.

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
     * For the set of all land hex coordinates, see {@link #landHexLayout}.
     * Hexes obscured by {@link #FOG_HEX}, if any, are stored in {@link #fogHiddenHexes}.
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
     * Unless a hex's type here is {@link #WATER_HEX}, it's a land hex.
     *<P>
     * @see SOCBoard#portsLayout
     */
    protected int[][] hexLayoutLg;

    /**
     * The set of land hex coordinates within {@link #hexLayoutLg},
     * as returned by {@link #getLandHexCoords()}, or <tt>null</tt>.
     * That method fills it from {@link #landHexLayout}.  If the board
     * layout changes, this field again becomes <tt>null</tt> until the
     * next call to {@link #getLandHexCoords()}.
     */
    protected volatile int[] cachedGetLandHexCoords;

    /**
     * The set of land hex coordinates within {@link #hexLayoutLg}.
     * Sent from server to client, along with the land hex types / dice numbers,
     * via {@link #getLandHexLayout()} / {@link #setLandHexLayout(int[])}.
     * When changing contents, set {@link #cachedGetLandHexCoords} to {@code null}.
     */
    protected HashSet<Integer> landHexLayout;

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
     * To get a specific node's land area, call {@link #getNodeLandArea(int)}.
     */
    protected HashSet<Integer>[] landAreasLegalNodes;

    /**
     * Maximum players (default 4, or 6 from SOCGameOption "PL" &gt; 4 or "PLB").
     * Some scenarios are laid out differently for 6 players.
     * Some are laid out differently for 3 players, so also check SOCGameOption "PL".
     */
    protected final int maxPlayers;

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
    protected int startingLandArea;

    /**
     * The legal set of land edge coordinates to build roads,
     * based on {@link #nodesOnLand}.
     * Calculated in {@link #initLegalRoadsFromLandNodes()}, after {@link #nodesOnLand} is filled by
     * {@code SOCBoardAtServer.makeNewBoard_fillNodesOnLandFromHexes(int[], int, int, int, boolean)}.
     * Used by {@link #initPlayerLegalRoads()}.
     * @see #legalShipEdges
     */
    protected HashSet<Integer> legalRoadEdges;

    /**
     * The legal set of water/coastline edge coordinates to build ships, based on {@link #hexLayoutLg}.
     * Calculated in {@link #initLegalShipEdges()}, after {@link #hexLayoutLg} is filled by
     * {@code SOCBoardAtServer.makeNewBoard_fillNodesOnLandFromHexes(int[], int, int, int, boolean)}.
     *<P>
     * Used by {@link #initPlayerLegalShips()}.
     * Updated in {@link #revealFogHiddenHex(int, int, int)} for {@link SOCBoard#WATER_HEX WATER_HEX}.
     *<P>
     * With scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI}, the legal edges vary per player
     * and are based on {@code SOCBoardAtServer.PIR_ISL_SEA_EDGES}, so {@code legalShipEdges}
     * is empty.
     *
     * @see #legalRoadEdges
     */
    protected HashSet<Integer> legalShipEdges;

    /**
     * Dice number from hex coordinate.
     * Order: [row][column].
     * For land hexes, <tt>numberLayoutLg</tt>[r][c] is the dice number on {@link #hexLayoutLg}[r][c].
     * One element per water, land, or port hex; desert, fog, and non-land hexes are 0.
     * Hex dice numbers obscured by {@link #FOG_HEX}, if any, are stored in {@link #fogHiddenHexes} (server only).
     * Because of bit shifts there, <tt>numberLayoutLg</tt> values must stay within the range 0 to 255.
     *<P>
     * If {@link #villages} are used, each village's dice number is stored in the {@link SOCVillage}.
     */
    protected int[][] numberLayoutLg;

    /**
     * For some scenarios, keyed lists of additional layout parts to add to game layout when sent from server to client.
     * For example, scenario {@link SOCScenario#K_SC_PIRI SC_PIRI} adds
     * <tt>"PP" = { 0x..., 0x... }</tt> for the fixed Pirate Path, and
     * {@link SOCScenario#K_SC_CLVI SC_CLVI} adds {@code "CV"} for the cloth village locations.
     * Null for most scenarios. Initialized in {@code SOCBoardAtServer.makeNewBoard}.
     */
    private HashMap<String, int[]> addedLayoutParts;

    /**
     * Map of special edge coordinates to types.  If an edge has no key
     * (coordinate) here, it is not a special edge.  Not all possible edges are
     * in this map, but all special edges are.  Any edge has at most one Special Edge Type.
     *<P>
     * The Special Edge Types are constants such as {@link #SPECIAL_EDGE_DEV_CARD}
     * or {@link #SPECIAL_EDGE_SVP}.  See the {@link #SPECIAL_EDGE_DEV_CARD} javadoc
     * for more about Special Edges and related methods.
     *<P>
     * Calls to {@link #setAddedLayoutPart(String, int[])} add them to the {@code specialEdges} map
     * if it recognizes the layout part as a special edge type.
     *<P>
     * Not many board layouts and scenarios have special edges, so this map is usually empty.
     */
    private HashMap<Integer, Integer> specialEdges = new HashMap<Integer, Integer>();

    /**
     * Fog hex coordinates and (at server) actual hex types and dice numbers hidden under each {@link #FOG_HEX}.
     * Key is the hex coordinate; value is
     * <tt>({@link #hexLayoutLg}[coord] &lt;&lt; 8) | ({@link #numberLayoutLg}[coord] & 0xFF)</tt>.
     * May be empty, never {@code null}.
     *<P>
     * Values are filled at server only (SOCBoardAtServer.makeNewBoard_hideHexesInFog);
     * the client doesn't know what's under the fog until hexes are revealed by messages from server.
     * @see #revealFogHiddenHexPrep(int)
     * @see #revealFogHiddenHex(int, int, int)
     */
    protected final HashMap<Integer, Integer> fogHiddenHexes;

    /**
     * For some scenarios, villages on the board. Null otherwise.
     * Each village has a {@link SOCVillage#diceNum dice number} and a {@link SOCVillage#getCloth() cloth count}.
     */
    protected HashMap<Integer, SOCVillage> villages;

    /**
     * For some scenarios, how many cloth does the board have in its "general supply"?
     * This supply is used if a village's {@link SOCVillage#takeCloth(int)}
     * returns less than the amount needed.
     */
    private int numCloth;

    /**
     * Land area numbers from which the player is excluded and cannot place settlements, or null.
     * This is never a long list.  Used in some game scenarios.
     */
    private int[] playerExcludedLandAreas;

    /**
     * Land areas numbers from which the robber is excluded and cannot be placed, or null.
     * Used in some game scenarios.
     */
    private int[] robberExcludedLandAreas;

    /**
     * This board layout's number of ports;
     * 0 if {@link #makeNewBoard(Map)} hasn't been called yet.
     * Port types, edges and facings are all stored in {@link SOCBoard#portsLayout}.
     */
    protected int portsCount;

    /**
     * the hex coordinate that the pirate is in, or 0; placed in {@link #makeNewBoard(Map)}.
     * Once the pirate is placed on the board it can't be removed (cannot become 0 again),
     * except in scenario {@link SOCGameOption#K_SC_PIRI} when the pirate fleet is defeated
     * (see {@link soc.server.SOCBoardAtServer#movePirateHexAlongPath(int)}).
     */
    protected int pirateHex;

    /**
     * the previous hex coordinate that the pirate is in; 0 unless
     * {@link #setPirateHex(int, boolean) setPirateHex(rh, true)} was called.
     */
    private int prevPirateHex;

    /**
     * Create a new Settlers of Catan Board, with the v3 encoding.
     * The board will be empty (all hexes are water, no dice numbers on any hex), see class javadoc
     * for how the board is filled when the game begins.
     * Board height and width will be the default, {@link #BOARDHEIGHT_LARGE} by {@link #BOARDWIDTH_LARGE}.
     *<P>
     * @param gameOpts  if game has options, map of {@link SOCGameOption}; otherwise null.
     * @param maxPlayers Maximum players; must be default 4, or 6 from SOCGameOption "PL" &gt; 4 or "PLB"
     * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
     */
    public SOCBoardLarge(final Map<String,SOCGameOption> gameOpts, int maxPlayers)
        throws IllegalArgumentException
    {
        this(gameOpts, maxPlayers, getBoardSize(gameOpts, maxPlayers));
    }

    /**
     * Create a new Settlers of Catan Board, with the v3 encoding and a certain size.
     * The board will be empty (all hexes are water, no dice numbers on any hex), see class javadoc
     * for how the board is filled when the game begins.
     * @param gameOpts  if game has options, map of {@link SOCGameOption}; otherwise null.
     * @param maxPlayers Maximum players; must be default 4, or 6 from SOCGameOption "PL" &gt; 4 or "PLB"
     * @param boardHeightWidth  Board's height and width.
     *        The constants for default size are {@link #BOARDHEIGHT_LARGE}, {@link #BOARDWIDTH_LARGE}.
     * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6, or <tt>boardHeightWidth</tt> is null
     */
    public SOCBoardLarge
        (final Map<String,SOCGameOption> gameOpts, final int maxPlayers, final IntPair boardHeightWidth)
        throws IllegalArgumentException
    {
        super(BOARD_ENCODING_LARGE, MAX_LAND_HEX_LG);

        if ((maxPlayers != 4) && (maxPlayers != 6))
            throw new IllegalArgumentException("maxPlayers: " + maxPlayers);
        if (boardHeightWidth == null)
            throw new IllegalArgumentException("boardHeightWidth null");

        this.maxPlayers = maxPlayers;

        final int bH = boardHeightWidth.a, bW = boardHeightWidth.b;
        setBoardBounds(bH, bW);

        hexLayoutLg = new int[bH + 1][bW + 1];
        numberLayoutLg = new int[bH + 1][bW + 1];
        landHexLayout = new HashSet<Integer>();
        fogHiddenHexes = new HashMap<Integer, Integer>();
        legalRoadEdges = new HashSet<Integer>();
        legalShipEdges = new HashSet<Integer>();

        // Assume 1 Land Area, unless or until makeNewBoard says otherwise
        landAreasLegalNodes = null;
        startingLandArea = 0;

        // Only odd-numbered rows are valid,
        // but we fill all rows here just in case.
        for (int r = 0; r <= bH; ++r)
        {
            Arrays.fill(hexLayoutLg[r], WATER_HEX);
            Arrays.fill(numberLayoutLg[r], 0);
        }

        portsCount = 0;
        pirateHex = 0;
        prevPirateHex = 0;
    }

    @Override
    public int getBoardEncodingFormat()
    {
        return BOARD_ENCODING_LARGE;
    }

    /**
     * Get the board size for client's constructor:
     * Default size {@link #BOARDHEIGHT_LARGE} by {@link #BOARDWIDTH_LARGE},
     * unless <tt>gameOpts</tt> contains <tt>"_BHW"</tt> Board Height and Width.
     * @param gameOpts  Game options, or null
     * @param maxPlayers  Maximum players; must be default 4, or 6 from game option "PL" &gt; 4 or "PLB".
     * @return a new IntPair(height, width)
     * @see soc.server.SOCBoardAtServer#getBoardSize(Map, int)
     */
    private static IntPair getBoardSize(final Map<String, SOCGameOption> gameOpts, int maxPlayers)
    {
        SOCGameOption bhwOpt = null;
        if (gameOpts != null)
            bhwOpt = gameOpts.get("_BHW");

        if ((bhwOpt == null) || (bhwOpt.getIntValue() == 0))
        {
            return new IntPair(BOARDHEIGHT_LARGE, BOARDWIDTH_LARGE);
        } else {
            final int bhw = bhwOpt.getIntValue();
            return new IntPair(bhw >> 8, bhw & 0xFF);
        }
    }

    // TODO hexLayoutLg, numberLayoutLg will only ever use the odd row numbers

    // TODO unlike roads, is there ever a time when sea edges are _not_ legal?
    //  (assuming water hexes on one or both sides of the edge)


    ////////////////////////////////////////////
    //
    // Make New Board: These methods are called at
    // the client when it receives a layout, and at
    // the server from SOCBoardAtServer.makeNewBoard
    //


    /**
     * Shuffle the hex tiles and layout a board.
     * This is called at server, but not at client;
     * client instead calls methods such as {@link #setLandHexLayout(int[])}
     * and {@link #setLegalSettlements(Collection, int, HashSet[])}.
     * Call {@code soc.server.SOCBoardAtServer.makeNewBoard(..)} instead of this stub super method.
     * @throws UnsupportedOperationException if called at client
     */
    @Override
    public void makeNewBoard(final Map<String, SOCGameOption> opts)
        throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("Use SOCBoardAtServer instead");
    }

    /**
     * Once the legal settlement/city nodes ({@link #nodesOnLand})
     * are established from land hexes, fill {@link #legalRoadEdges}.
     * Not iterative; clears all previous legal roads.
     *<P>
     * For scenarios, if Added Layout Part {@code "AL"} is present, checks it for
     * references to node lists (Parts {@code "N1", "N2"}, etc) and if found, adds their
     * edges now so that initial settlements' roads can be built towards those nodes.
     * For more info see the "Added Layout Parts" section of {@link #getAddedLayoutPart(String)}'s javadoc.
     *<P>
     * Called at server and at client. At server, call this only after the very last call to
     * {@code SOCBoardAtServer.makeNewBoard_fillNodesOnLandFromHexes(int[], int, int, int, boolean)}.
     * At client, called from {@link #setLegalSettlements(Collection, int, HashSet[])}.
     *
     * @throws IllegalStateException if Part {@code "AL"} is present but badly formed (node list number 0, or a
     *     node list number not followed by a land area number) or refers to a node list Part ({@code "N1", "N2"}, etc)
     *     not present in the layout
     * @see #initLegalShipEdges()
     */
    protected void initLegalRoadsFromLandNodes()
        throws IllegalStateException
    {
        // About corners/concave parts:
        //   Set of the valid nodes will contain both ends of the edge;
        //   anything concave across a sea would be missing at least 1 node, in the water along the way.

        legalRoadEdges.clear();

        // Go from nodesOnLand.  If Part "AL" refers to node lists, build and
        // use a temporary landNodes set with nodesOnLand + those nodes
        // instead. ("AL" is rare)

        HashSet<Integer> landNodes = nodesOnLand;
        final int[] partAL = getAddedLayoutPart("AL");
        if (partAL != null)
        {
            boolean foundNodes = false;

            // Strictly parse the contents of "AL", throw exceptions if a problem is found.
            // Part "AL" will be parsed again in SOCGame.updateAtGameFirstTurn().
            // If you update the "AL" parser here, update the similar one there too.

            for (int i = 0; i < partAL.length; ++i)
            {
                final int elem = partAL[i];
                if (elem < 0)
                    continue;  // edge list number, skip it
                else if (elem == 0)
                    throw new IllegalStateException("Bad Layout Part: AL[" + i + "] == 0");
                else if (i == (partAL.length - 1))
                    throw new IllegalStateException("Bad Layout Part: AL[" + i + "] must be followed by LA#");

                ++i;  // skip land area number that follows elem (don't parse or verify LA#)

                final String nodeListKey = "N" + elem;
                final int[] nodeList = getAddedLayoutPart(nodeListKey);
                if (nodeList == null)
                    throw new IllegalStateException
                        ("Bad Layout Part: AL[" + i + "] == " + elem + " but Part " + nodeListKey + " missing");

                if (! foundNodes)
                {
                    landNodes = new HashSet<Integer>(nodesOnLand);
                    foundNodes = true;
                }

                for (int j = 0; j < nodeList.length; ++j)
                    landNodes.add(Integer.valueOf(nodeList[j]));
            }
        }

        // Go from nodesOnLand or landNodes, iterate all nodes:

        for (Integer nodeVal : landNodes)
        {
            final int node = nodeVal.intValue();
            for (int dir = 0; dir < 3; ++dir)
            {
                int nodeAdjac = getAdjacentNodeToNode(node, dir);
                if (landNodes.contains(Integer.valueOf(nodeAdjac)))
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
                        legalRoadEdges.add(Integer.valueOf(edge));
                        // it's ok to add if this set already contains an Integer equal to that edge.
                }
            }
        }

    }

    /**
     * Add nodes to Nodes On Land and optionally to a a Land Area's legal nodes.
     *<P>
     * Called at server and client from {@link SOCGame#updateAtGameFirstTurn()}
     * for node lists referenced in Added Layout Part {@code "AL"}.
     * For details see {@code "AL"} in the "Other layout parts" section of the
     * javadoc for message {@link soc.message.SOCBoardLayout2 BOARDLAYOUT2}.
     *<P>
     * Currently does not add the new nodes' edges to {@link #legalRoadEdges},
     * because it assumes the nodes are from Part {@code "AL"}, so their edges
     * were added in {@link #initLegalRoadsFromLandNodes()}.
     *
     * @param nodes  Node coordinates to add. Not checked for validity, not checked to be land not water
     * @param lan  Land Area number to add legal nodes, or 0 to add only to Nodes On Land
     * @since 2.0.00
     */
    public final void addLegalNodes(final int[] nodes, final int lan)
    {
        HashSet<Integer> area = (lan > 0) ? landAreasLegalNodes[lan] : null;

        for (int i = 0; i < nodes.length; ++i)
        {
            Integer iobj = Integer.valueOf(nodes[i]);
            nodesOnLand.add(iobj);
            if (area != null)
                area.add(iobj);
        }

        // If new nodes weren't in layout part "AL", would need to add their edges
        // to legalRoadEdges; could refactor initLegalRoadsFromLandNodes.
    }

    /**
     * Once the legal settlement/city nodes ({@link #nodesOnLand})
     * are established from land hexes, fill {@link #legalShipEdges}.
     * Contains all 6 edges of each water hex.
     * Contains all coastal edges of each land hex at the edges of the board.
     *<P>
     * Not iterative; clears all previous legal ship edges.
     * Call this only after the very last call to
     * {@code SOCBoardAtServer.makeNewBoard_fillNodesOnLandFromHexes(int[], int, int, int, boolean)}
     * so that all land hexes are already placed.
     *<P>
     * Called at server and at client.
     * @see #initPlayerLegalShips()
     * @see #initLegalRoadsFromLandNodes()
     */
    protected void initLegalShipEdges()
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
                c = 1;  // odd hex row hexes start at 1
            } else {
                c = 2;  // top row, even row hexes start at 2
            }
            for (; c < boardWidth; c += 2)
            {
                if (hexLayoutLg[r][c] == WATER_HEX)
                {
                    legalShipEdges.addAll(getAdjacentEdgesToHex(rshift | c));
                } else {
                    // Land hex; check if it's at the
                    // edge of the board; this check is also isHexAtBoardMargin(hc)
                    if ((r == 1) || (r == (boardHeight-1))
                        || (c <= 2) || (c >= (boardWidth-2)))
                    {
                        for (final int side : getAdjacentEdgesToHex_arr(rshift | c))
                            if (isEdgeCoastline(side))
                                legalShipEdges.add(Integer.valueOf(side));
                    }
                }

            }
        }

    }  // initLegalShipEdges

    /**
     * Prepare to reveal one land or water hex hidden by {@link #FOG_HEX fog} (server-side call).
     * Gets the hidden hex type and dice number, removes this hex's info from the set of hidden hexes.
     *<P>
     * This method <b>does not reveal the hex</b>:
     * Game should call {@link #revealFogHiddenHex(int, int, int)}
     * and update any player or piece info affected.
     * @param hexCoord  Coordinate of the hex to reveal
     * @return The revealed hex type and dice number, encoded as an int.
     *  Decode this way:
     *        <PRE>
        final int hexType = encodedHexInfo >> 8;
        int diceNum = encodedHexInfo & 0xFF;
        if (diceNum == 0xFF)
            diceNum = 0;  </PRE>
     * <tt>hexType</tt> is the same type of value as {@link #getHexTypeFromCoord(int)}.
     *
     * @throws IllegalArgumentException if <tt>hexCoord</tt> isn't currently a {@link #FOG_HEX}
     * @see SOCGame#revealFogHiddenHex(int, int, int)
     */
    public int revealFogHiddenHexPrep(final int hexCoord)
        throws IllegalArgumentException
    {
        final Integer encoded = fogHiddenHexes.remove(Integer.valueOf(hexCoord));
        if ((encoded == null) || (getHexTypeFromCoord(hexCoord) != FOG_HEX))
            throw new IllegalArgumentException("Not fog: 0x" + Integer.toHexString(hexCoord));

        return encoded;

        // The note about 0xFF -> 0 is because DESERT_HEX and FOG_HEX store their
        // dice numbers as -1, but getNumberOnHexFromCoord(hexCoord) returns 0.
    }

    /**
     * Reveal one land or water hex hidden by fog. Called at server and clients,
     * by {@link SOCGame#revealFogHiddenHex(int, int, int)}
     * before updating nearby legal nodes and edges.
     *<P>
     * If revealed to be a {@link #WATER_HEX} which is in {@link #landHexLayout},
     * removes any non-coastal edges and corners from {@link #legalRoadEdges} and {@link #nodesOnLand}.
     * Those had been added at the server during {@code SOCBoardAtServer.makeNewBoard}
     * so the clients couldn't deduce that this fog-hidden hex was a water hex.
     *
     * @param hexCoord  Coordinate of the hex to reveal
     * @param hexType   Revealed hex type, same value as {@link #getHexTypeFromCoord(int)}
     * @param diceNum   Revealed hex dice number, same value as {@link #getNumberOnHexFromCoord(int)}, or 0
     * @throws IllegalArgumentException if {@code hexCoord} isn't currently a {@link #FOG_HEX}
     *     or if {@code diceNum} &lt; 0
     * @return true if the revealed {@code hexType} is {@link #WATER_HEX} and some of its surrounding edges or nodes
     *     may have been removed from {@link #legalRoadEdges} or {@link #nodesOnLand}.
     *     If true, caller should call each player's {@link SOCPlayer#updatePotentialsAndLegalsAroundRevealedHex(int)}.
     * @see #revealFogHiddenHexPrep(int)
     */
    boolean revealFogHiddenHex(final int hexCoord, final int hexType, int diceNum)
        throws IllegalArgumentException
    {
        final int r = hexCoord >> 8,
                  c = hexCoord & 0xFF;
        if (hexLayoutLg[r][c] != FOG_HEX)
            throw new IllegalArgumentException("Not fog: 0x" + Integer.toHexString(hexCoord));
        if (diceNum < 0)
            throw new IllegalArgumentException("diceNum: " + diceNum);

        boolean wasWaterRemovedLegals = false;

        hexLayoutLg[r][c] = hexType;
        numberLayoutLg[r][c] = diceNum;
        fogHiddenHexes.remove(Integer.valueOf(hexCoord));  // needed at client, redundant at server

        if (hexType == WATER_HEX)
        {
            // Previously not a legal ship edge, because
            // we didn't know if the fog hid land or water
            legalShipEdges.addAll(getAdjacentEdgesToHex(hexCoord));

            if (landHexLayout.contains(hexCoord))
            {
                // Remove any non-coastal edges/corners from legalRoadEdges, nodesOnLand, landAreaLegalNodes:
                // Reverses the addition process done in SOCBoardAtServer.makeNewBoard_hideHexesInFog.

                for (final Integer edgeObj : getAdjacentEdgesToHex(hexCoord))
                    if (! isEdgeCoastline(edgeObj))
                    {
                        legalRoadEdges.remove(edgeObj);
                        wasWaterRemovedLegals = true;
                    }

                for (final Integer nodeObj : getAdjacentNodesToHex(hexCoord))
                {
                    if (isNodeCoastline(nodeObj))
                        continue;  // has adjacent land: don't remove from land node sets

                    nodesOnLand.remove(nodeObj);
                    if (landAreasLegalNodes != null)
                    {
                        for (int i = 1; i < landAreasLegalNodes.length; ++i)
                        {
                            HashSet<Integer> laln = landAreasLegalNodes[i];
                            if (laln != null)
                                laln.remove(nodeObj);
                        }
                    }
                    wasWaterRemovedLegals = true;
                }
            }
        }

        return wasWaterRemovedLegals;
    }


    ////////////////////////////////////////////
    //
    // Board info getters
    //


    /**
     * Get the hex layout -- Not valid for this encoding.
     * Valid only for the v1 and v2 board encoding, not v3.
     * Always throws UnsupportedOperationException for SOCBoardLarge.
     * Call {@link #getLandHexCoords()} instead.
     * For sending a <tt>SOCBoardLayout2</tt> message, call {@link #getLandHexLayout()} instead.
     * @throws UnsupportedOperationException since the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     * @see SOCBoard#getHexLayout()
     */
    @Override
    @Deprecated
    public int[] getHexLayout()
        throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Set the hexLayout -- Not valid for this encoding.
     * Valid only for the v1 and v2 board encoding, not v3.
     * Always throws UnsupportedOperationException for SOCBoardLarge.
     * Call {@link #setLandHexLayout(int[])} instead.
     * @param hl  the hex layout
     * @throws UnsupportedOperationException since the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     * @see SOCBoard#setHexLayout(int[])
     */
    @Override
    @Deprecated
    public void setHexLayout(final int[] hl)
        throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Get the dice-number layout of dice rolls at each hex number -- Not valid for this encoding.
     * Call {@link #getLandHexCoords()} and {@link #getNumberOnHexFromCoord(int)} instead.
     * @throws UnsupportedOperationException since the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     * @see SOCBoard#getNumberLayout()
     */
    @Override
    @Deprecated
    public int[] getNumberLayout()
        throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Set the number layout -- Not valid for this encoding.
     * Call {@link SOCBoardLarge#setLandHexLayout(int[])} instead.
     *
     * @param nl  the number layout, from {@link #getNumberLayout()}
     * @throws UnsupportedOperationException since the board encoding doesn't support this method;
     *     the v1 and v2 encodings do, but v3 ({@link #BOARD_ENCODING_LARGE}) does not.
     * @see SOCBoard#setNumberLayout(int[])
     */
    @Override
    @Deprecated
    public void setNumberLayout(int[] nl)
        throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Get the keyed lists of additional layout parts to add to game layout, used only in some scenarios.
     * For example, scenario {@link SOCScenario#K_SC_PIRI SC_PIRI} adds
     * <tt>"PP" = { 0x..., 0x... }</tt> for the fixed Pirate Path, and
     * {@link SOCScenario#K_SC_CLVI SC_CLVI} adds {@code "CV"} for the cloth village locations.
     * {@link SOCScenario#K_SC_WOND SC_WOND} adds Node Lists {@code "N1"}-{@code "N3"} for wonder placement locations.
     *<P>
     * At the client, most of these are get/set with specific methods like {@link #setVillageAndClothLayout(int[])}.
     * A few such as {@code "PP"}, {@code "LS"}, and {@code "N1"}-{@code "N3"} just call {@link #getAddedLayoutPart(String)}.
     *<P>
     * Please treat the returned value as read-only.
     * The layout parts and their keynames are documented at {@link #getAddedLayoutPart(String)}.
     *<P>
     * Added during {@code SOCBoardAtServer.makeNewBoard}
     * or {@code SOCBoardAtServer.startGame_putInitPieces}.
     *
     * @return  The added layout parts, or null if none
     * @see #getAddedLayoutPart(String)
     * @see #setAddedLayoutPart(String, int[])
     */
    public HashMap<String, int[]> getAddedLayoutParts()
    {
        if ((addedLayoutParts != null) && addedLayoutParts.isEmpty())
            return null;
        else
            return addedLayoutParts;
    }

    /**
     * Get one "added layout part" by its key name.
     * For more explanation, see {@link #getAddedLayoutParts()}.
     *
     *<H3>All Layout Parts:</H3>
     *
     * All known Layout Parts and their keynames are documented here.
     *
     *<H4>Typical parts of the board layout:</H4>
     *
     * Not all layouts or {@link #getBoardEncodingFormat()}s include all these parts.
     *
     *<UL>
     *<LI> HL (board encoding v1 & v2 only): The hexes, from {@link SOCBoard#getHexLayout()}.<br>
     *         Not sent if <tt>LH</tt> is sent.  See note below on value mapping.
     *<LI> NL (encoding v1 & v2 only): The dice numbers, from {@link SOCBoard#getNumberLayout()}
     *<LI> RH: The robber hex, from {@link SOCBoard#getRobberHex()}, if &gt; 0
     *<LI> PL: The ports, from {@link SOCBoard#getPortsLayout()}
     *<LI> PH: The pirate hex, from {@link #getPirateHex()}, if &gt; 0
     *<LI> LH: The land hexes (encoding v3 only), from {@link #getLandHexLayout()}.<br>
     *         These land hexes also may be logically grouped into several
     *         "land areas" (groups of islands, or subsets of islands).  Those areas
     *         are sent to the client using a {@code SOCPotentialSettlements} message.
     *<LI> PX: Players are excluded from settling these land area numbers (usually none)
     *<LI> RX: Robber is excluded from these land area numbers (usually none)
     *</UL>
     * These typical Layout Parts each use specific board methods to get or set them,
     * instead of being returned from {@code getAddedLayoutPart(..)}.
     *
     *<H4>Added Layout Parts:</H4>
     *
     * A few game scenarios in jsettlers v2.0.00 and newer may add other parts,
     * which use this method to get them by their keynames:
     *<UL>
     *<LI> AL: Added List numbers of nodes or edges, originally for {@code _SC_WOND}: After Initial Placement,
     *         the lists referred here are added to all players' legal nodes / legal edges.
     *         <UL>
     *         <LI> Negative numbers (not implemented yet) in {@code AL} refer to layout parts
     *              {@code E1} through {@code E9} for edges to add.
     *         <LI> Positive numbers refer to parts {@code N1} through {@code N9} for nodes to add.
     *              A positive number is always followed in {@code AL} by a Land Area Number, or 0, to add the nodes to.
     *              For each added node, its edges to adjacent legal nodes will also be added if not already there.
     *              If a layout part's set of nodes is used only during initial placement, and its contents should be
     *              emptied after that, the Land Area Number will be negative here.
     *         </UL>
     *<LI> CV: Cloth Village layout, for {@code _SC_CLVI}, from {@link #getVillageAndClothLayout()};
     *         at the client, call {@link #setVillageAndClothLayout(int[])} if this layout part is sent.
     *<LI> CE: dev Card Edge, for {@code _SC_FTRI}; edge coordinates where ship placement gives a free development card.
     *         As these are each claimed during game play, 0 replaces their coordinate in the layout part element.
     *<LI> VE: Victory point Edge, for {@code _SC_FTRI}; edge coordinates where ship placement gives a special victory point.
     *         As these are each claimed during game play, 0 replaces their coordinate in the layout part element.
     *<LI> LS: Each player's lone additional Legal Settlement location, for {@code _SC_PIRI}: Node coordinates,
     *         one per player number, for the player's lone build location on the way to the pirate fortress.
     *<LI> PP: Pirate fleet Path, for {@code _SC_PIRI}; hex coordinates for {@link #movePirateHexAlongPath(int)}
     *<LI> E1 through E9: Reserved but not implemented: Special edge lists.  Can be used for any purpose by the
     *         scenario, and/or for additional legal edges (see layout part {@code "AL"}).
     *<LI> N1 through N9: Special node lists, originally for {@code _SC_WOND}.  Can be used for any purpose by a
     *         scenario, and/or for additional legal nodes (see layout part {@code "AL"}).
     *<LI> VS: Visual Shift rightwards and/or downwards to use when rendering the board at the client.
     *         Right and Down are positive, Up and Left are negative. Unit is 1/4 of hex size. For example,
     *         a Visual Shift of {Down 2, Right 3} would have {@code SOCBoardPanel} render a "margin" of
     *         water of size 2/4 of a hex above, and 3/4 of a hex to the left of, the board's in-game boundary.
     *         The client should show the board's entire {@link #getBoardHeight()} and {@link #getBoardWidth()}
     *         plus this margin.
     *</UL>
     * The "CE" and "VE" layout parts are lists of Special Edges on the board.  During game play, these
     * edges may change.  The server announces each change with a
     * {@code SOCSimpleAction(BOARD_EDGE_SET_SPECIAL)} message.
     * If you add a layout part which is a Special Edge type, be sure to update
     * {@link #SPECIAL_EDGE_LAYOUT_PARTS} and {@link #SPECIAL_EDGE_TYPES}
     * so players joining during the game will get updated Special Edge data.
     *
     * @param key  Key name (short and uppercase)
     * @return  The added layout part, or null if none with that key
     * @see #setAddedLayoutPart(String, int[])
     */
    public int[] getAddedLayoutPart(final String key)
    {
        if (addedLayoutParts == null)
            return null;
        else
            return addedLayoutParts.get(key);
    }

    /**
     * Set all the "added layout parts", for use at client.
     * See {@link #setAddedLayoutPart(String, int[])} for details about the added layout parts.
     *<P>
     * If any layout part key in {@code adds} is recognized here as a Special Edge Type
     * for {@link #getSpecialEdgeType(int)}, this method also adds them to the
     * {@code specialEdges} map. Current Special Edge Type layout parts are
     * listed in the {@link #setAddedLayoutPart(String, int[])} javadoc.
     *
     * @param adds  Added parts, or null if none
     * @see #setAddedLayoutPart(String, int[])
     */
    public void setAddedLayoutParts(HashMap<String, int[]> adds)
    {
        if ((adds != null) && adds.isEmpty())
            addedLayoutParts = null;
        else
            addedLayoutParts = adds;

        if (adds == null)
            return;

        // check for any Special Edge type
        for (int i = 0; i < SPECIAL_EDGE_LAYOUT_PARTS.length; ++i)
        {
            final int[] edgelist = adds.get(SPECIAL_EDGE_LAYOUT_PARTS[i]);  // "CE", "VE", etc
            if (edgelist != null)
                setSpecialEdges(edgelist, SPECIAL_EDGE_TYPES[i]);  // SPECIAL_EDGE_DEV_CARD, etc
        }

    }

    /**
     * Set one "added layout part" by its key name.
     * Should be set only during {@code SOCBoardAtServer.makeNewBoard}
     * or {@code SOCBoardAtServer.startGame_putInitPieces}, not changed afterwards.
     * Document the new {@code key} at {@link #getAddedLayoutPart(String)}.
     *<P>
     * If the layout part {@code key} is recognized here as a Special Edge Type
     * for {@link #getSpecialEdgeType(int)}, this method also adds them to the
     * {@code specialEdges} map. Current Special Edge Type layout parts are
     * {@code "CE"} and {@code "VE"}.
     *
     * @param key  Key name (short and uppercase)
     * @param v    Value (typically a list of coordinates)
     * @see #setAddedLayoutParts(HashMap)
     */
    public void setAddedLayoutPart(final String key, final int[] v)
    {
        if (addedLayoutParts == null)
            addedLayoutParts = new HashMap<String, int[]>();
        addedLayoutParts.put(key, v);

        // check for any Special Edge type
        for (int i = 0; i < SPECIAL_EDGE_LAYOUT_PARTS.length; ++i)
            if (key.equals(SPECIAL_EDGE_LAYOUT_PARTS[i]))   // "CE", "VE", etc
                setSpecialEdges(v, SPECIAL_EDGE_TYPES[i]);  // SPECIAL_EDGE_DEV_CARD, etc
    }

    /**
     * If this scenario has dev cards or items waiting to be claimed by any player,
     * draw the next item from that stack.
     *<P>
     * This is called at server, but not at client; client instead receives messages from the server
     * when the player claims such an item.  It's declared here in SOCBoardLarge instead of
     * {@code SOCBoardAtServer} so that game methods can call it without importing the server-side class.
     *<P>
     * In {@link SOCGameOption#K_SC_FTRI _SC_FTRI}, each item is a {@link SOCDevCardConstants} card type.
     *
     * @return The next item from the stack, or {@code null} if empty or unused
     * @throws UnsupportedOperationException if called at client
     */
    public Integer drawItemFromStack()
        throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("Use SOCBoardAtServer instead");
    }

    /**
     * For scenario option {@link SOCGameOption#K_SC_FTRI _SC_FTRI}, place a "gift" port at this edge.
     * Port's facing direction is calculated by checking {@code edge}'s adjacent hexes for land and water;
     * if a hex is off the edge of the board, it's considered water.
     *<P>
     * Called from {@link SOCGame#placePort(SOCPlayer, int, int)} which validates {@code ptype}.
     *
     * @param edge  An available coastal edge
     * @param ptype  The type of port (in range {@link SOCBoard#MISC_PORT MISC_PORT}
     *          to {@link SOCBoard#WOOD_PORT WOOD_PORT}). Not validated here.
     * @throws IllegalArgumentException  if {@code edge} is between 2 land hexes or 2 water hexes
     */
    final void placePort(final int edge, final int ptype)
        throws IllegalArgumentException
    {
        placePort(edge, getPortFacingFromEdge(edge), ptype);
    }

    /**
     * Solely for debugging, a version of {@link #placePort(int, int)} which takes a
     * {@code facing} direction instead of calculating that, and bypasses any checks that
     * {@code edge} is a valid coastal edge within the board's coordinate boundaries.
     * @param edge  An available edge. Not validated here.
     * @param facing  Port's facing direction (towards land) from {@link #getPortFacingFromEdge(int)}
     * @param ptype  The type of port (in range {@link SOCBoard#MISC_PORT MISC_PORT}
     *          to {@link SOCBoard#WOOD_PORT WOOD_PORT}). Not validated here.
     */
    final void placePort(final int edge, final int facing, final int ptype)
    {
        // Adding a new port has similar tasks to setPortsLayout:
        // If you update this method, consider updating that one too.

        // - Update portsLayout
        int i;  // will fill this index in portsLayout
        for (i = 0; i < portsCount; ++i)
        {
            if (portsLayout[i + portsCount] < 0)
                break;  // found an empty element, probably from removing this port to place it
        }
        if (i == portsCount)
        {
            // No empty element: Must expand portsLayout to make room
            int[] npl = new int[3 * (portsCount + 1)];
            System.arraycopy(portsLayout, 0,              npl, 0,                  portsCount);
            System.arraycopy(portsLayout, portsCount,     npl, portsCount+1,       portsCount);
            System.arraycopy(portsLayout, 2 * portsCount, npl, 2 * (portsCount+1), portsCount);
            portsLayout = npl;
            ++portsCount;
            // now, i == portsCount-1, the newly created element
        }
        portsLayout[i] = ptype;
        portsLayout[i + portsCount] = edge;
        portsLayout[i + (2*portsCount)] = facing;

        // - call placePort
        final int[] nodes = getAdjacentNodesToEdge_arr(edge);
        placePort(ptype, -1, facing, nodes[0], nodes[1]);
    }

    /**
     * For game scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * move the pirate fleet's position along its path.
     *<P>
     * This is called at server, but not at client; client instead calls {@link #setPirateHex(int, boolean)}.
     * <B>See {@link soc.server.SOCBoardAtServer#movePirateHexAlongPath(int)}</B> instead of this stub super method.
     * @param numSteps  Number of steps to move along the path
     * @return  new pirate hex coordinate
     * @throws UnsupportedOperationException if called at client
     * @throws IllegalStateException if this board doesn't have layout part "PP" for the Pirate Path.
     */
    public int movePirateHexAlongPath(final int numSteps)
        throws UnsupportedOperationException, IllegalStateException
    {
        throw new UnsupportedOperationException("Use SOCBoardAtServer instead");
    }

    /**
     * Set where the pirate is, or take the pirate off the board.
     *<P>
     * For scenario {@link SOCGameOption#K_SC_PIRI _SC_PIRI}, the
     * server should call {@link #movePirateHexAlongPath(int)}
     * instead of directly calling this method.
     *<P>
     * Currently, the only scenario that removes the pirate from
     * the board is {@code _SC_PIRI}.
     *
     * @param ph  the new pirate hex coordinate, or 0 to take the pirate off the board;
     *            must be &gt;= 0, not validated beyond that
     * @param rememberPrevious  Should we remember the old pirate hex?
     * @see #getPirateHex()
     * @see #getPreviousPirateHex()
     * @see #setRobberHex(int, boolean)
     * @throws IllegalArgumentException if <tt>ph</tt> &lt; 0
     */
    public void setPirateHex(final int ph, final boolean rememberPrevious)
        throws IllegalArgumentException
    {
        if (ph < 0)
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
     * Given a hex coordinate / hex number, return the (dice-roll) number on that hex.
     * Water, desert, and fog hexes have 0 for their dice numbers. So do land hexes without dice numbers,
     * as seen in some scenario layouts like {@link SOCGameOption#K_SC_FTRI SC_FTRI}.
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

        return numberLayoutLg[r][c];
    }

    /**
     * Given a hex coordinate, return the hex number (index) -- <b>Not valid for this encoding</b>.
     *<P>
     * To get the dice number on a hex, call {@link #getNumberOnHexFromCoord(int)}.
     *<P>
     * Valid only for the v1 and v2 board encoding, not v3.
     * Always throws UnsupportedOperationException for SOCBoardLarge.
     * Hex numbers (indexes within an array of land hexes) aren't used in this encoding,
     * hex coordinates are used instead.
     * @see #getHexTypeFromCoord(int)
     * @throws UnsupportedOperationException since the board encoding doesn't support this method
     */
    @Override
    @Deprecated
    public int getHexNumFromCoord(final int hexCoord)
        throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("Not valid for SOCBoardLarge; try getNumberOnHexFromCoord");
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
     * If there's a village placed at this node during board setup, find it.
     * Only some scenarios use villages.
     *
     * @param nodeCoord  Node coordinate
     * @return village, or null.
     * @see #getVillages()
     */
    public SOCVillage getVillageAtNode(final int nodeCoord)
    {
        if ((villages == null) || villages.isEmpty())
            return null;

        return villages.get(Integer.valueOf(nodeCoord));
    }

    /**
     * Is this edge along the coastline (land/water border)?
     * Off the edge of the board is considered water.
     * {@link #FOG_HEX} is considered land here.
     * @param edge  Edge coordinate, not checked for validity
     * @return  true if this edge's hexes are land and water,
     *           or a land hex at the edge of the board
     * @see #isHexCoastline(int)
     * @see #isNodeCoastline(int)
     * @see #getAdjacentEdgesToNode_coastal(int)
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
     * Is this edge a legal road location?
     * @param edge  Edge coordinate, not checked for validity
     * @return  True if edge is a legal road coordinate, based on the set of all nodes on land
     * @see #initPlayerLegalRoads()
     */
    public final boolean isEdgeLegalRoad(final int edge)
    {
        return legalRoadEdges.contains(edge);
    }

    /**
     * The hex coordinates of all land hexes.  Please treat as read-only.
     * @return land hex coordinates, as a set of {@link Integer}s; never null, may be empty
     * @since 2.0.00
     * @see #getLandHexCoords()
     */
    public HashSet<Integer> getLandHexCoordsSet()
    {
        return landHexLayout;
    }

    /**
     * The hex coordinates of all land hexes; built from {@link #getLandHexCoordsSet()} and then cached
     * until the layout changes.
     *<P>
     * Before v2.0.00 this was {@code getHexLandCoords()}.
     *
     * @return land hex coordinates, in no particular order, or null if none (all water).
     * @see #getLandHexCoordsSet()
     * @see #getFogHiddenHexes()
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
     * Put a piece on the board.
     *<P>
     * Except for {@link SOCVillage}, call
     * {@link SOCPlayer#putPiece(SOCPlayingPiece, boolean) pl.putPiece(pp)}
     * for each player before calling this method.
     *
     * @param pp  Piece to place on the board; coordinates are not checked for validity
     */
    @Override
    public void putPiece(SOCPlayingPiece pp)
    {
        switch (pp.getType())
        {
        case SOCPlayingPiece.VILLAGE:
            if (villages == null)
                villages = new HashMap<Integer, SOCVillage>();
            villages.put(pp.getCoordinates(), (SOCVillage) pp);
            break;

        default:
            super.putPiece(pp);
        }
    }

    /**
     * Add one legal settlement location to each player.
     * The new location is alone by itself, outside of the other Land Areas where they can place.
     * Used in some scenarios ({@link SOCScenario#K_SC_PIRI _SC_PIRI}) when {@link SOCGame#hasSeaBoard}.
     * Assumes there aren't any settlements or cities on the board yet; does not check for adjacent
     * pieces before making the location legal.
     * @param ga  Game, to get players; {@link SOCBoard} doesn't keep a reference to its game
     * @param ls  Each player's lone settlement node coordinate to add, indexed by player number,
     *            or {@code null} to do nothing.  If an element is 0, nothing is added for that player.
     * @throws IllegalArgumentException if {@code ls.length} != {@link SOCGame#maxPlayers ga.maxPlayers}
     */
    public void addLoneLegalSettlements(SOCGame ga, final int[] ls)
        throws IllegalArgumentException
    {
        if (ls == null)
            return;
        if (ls.length != ga.maxPlayers)
            throw new IllegalArgumentException();

        for (int pn = 0; pn < ls.length; ++pn)
            if (ls[pn] != 0)
                ga.getPlayer(pn).addLegalSettlement(ls[pn], false);
    }

    /**
     * Does this board contain any Special Edges?
     * @return  True if a method setting special edges has been called, and the Special Edges map is not empty.
     */
    public boolean hasSpecialEdges()
    {
        return ! specialEdges.isEmpty();
    }

    /**
     * Get this edge's Special Edge Type, if any.
     * @param edge  Edge coordinate
     * @return  A Special Edge Type code such as {@link #SPECIAL_EDGE_DEV_CARD} or
     *          or {@link #SPECIAL_EDGE_SVP}, or 0 if the edge isn't special or
     *          isn't a valid edge.
     * @see #getSpecialEdges()
     * @see #setSpecialEdge(int, int)
     * @see #setSpecialEdges(int[], int)
     * @see #clearSpecialEdges(int)
     */
    public int getSpecialEdgeType(final int edge)
    {
        Integer typeObj = specialEdges.get(Integer.valueOf(edge));
        if (typeObj == null)
            return 0;
        else
            return typeObj.intValue();
    }

    /**
     * Get all Special Edge coordiates and their types.
     * Please treat the returned iterator as read-only.
     * @return An iterator over all items:<br>
     *     entry key = edge coordinate<br>
     *     value = edge's Special Edge Type code such as {@link #SPECIAL_EDGE_DEV_CARD}<br>
     *     If the board has no special edges, the iterator is empty not null.
     * @see #getSpecialEdgeType(int)
     */
    public Iterator<Map.Entry<Integer, Integer>> getSpecialEdges()
    {
        return specialEdges.entrySet().iterator();
    }

    /**
     * Set an edge as a Special Edge, or clear that status and make it a normal edge.
     * If an edge is already a different special edge type, its type will be changed to {@code seType}.
     * @param edge  Edge coordinate
     * @param seType  A special edge type code such as {@link #SPECIAL_EDGE_DEV_CARD} or
     *          or {@link #SPECIAL_EDGE_SVP}, or 0 if the edge isn't special
     */
    public void setSpecialEdge(final int edge, final int seType)
    {
        final Integer edgeObj = Integer.valueOf(edge);
        if (seType != 0)
        {
            specialEdges.put(edgeObj, Integer.valueOf(seType));
        } else {
            specialEdges.remove(edgeObj);  // ok to call if edgeObj not in map
        }
    }

    /**
     * Set a list of edges as a certain type of special edge.
     * If any of these edges is already a different special edge type, its type will be changed to {@code seType}.
     * This method adds but does not remove any edges previously marked as that type, they will still be special.
     * To clear previous edges of this special type, call {@link #clearSpecialEdges(int)}
     * before calling this method.
     * @param edges  List of edges to mark as special type code {@code seType}
     * @param seType  A special edge type code such as {@link #SPECIAL_EDGE_DEV_CARD} or
     *          or {@link #SPECIAL_EDGE_SVP}, or 0 to clear them (no longer special).
     */
    public void setSpecialEdges(final int[] edges, final int seType)
    {
        if (seType != 0)
        {
            final Integer setypeObj = Integer.valueOf(seType);
            for (int i = 0; i < edges.length; ++i)
                specialEdges.put(Integer.valueOf(edges[i]), setypeObj);
        } else {
            for (int i = 0; i < edges.length; ++i)
                specialEdges.remove(Integer.valueOf(edges[i]));
        }
    }

    /**
     * Clear all edges marked as one special type.  All special edges of that type will no longer be special.
     * @param seType  A special edge type code such as {@link #SPECIAL_EDGE_DEV_CARD} or
     *          or {@link #SPECIAL_EDGE_SVP}.  0 is ignored.
     */
    public void clearSpecialEdges(final int seType)
    {
        if (seType == 0)
            return;

        final Iterator<Map.Entry<Integer, Integer>> seIter = specialEdges.entrySet().iterator();
        while (seIter.hasNext())
        {
            Map.Entry<Integer, Integer> entry = seIter.next();
            if (entry.getValue() == seType)
                seIter.remove();
        }
    }

    /**
     * Get the village and cloth layout, for sending from server to client
     * for scenario game option {@link SOCGameOption#K_SC_CLVI}.
     *<P>
     * Index 0 is the board's "general supply" cloth count {@link #getCloth()}.
     * Index 1 is each village's starting cloth count, from {@link SOCVillage#STARTING_CLOTH}.
     * Then, 2 int elements per village: Coordinate, Dice Number.
     * If any village has a different amount of cloth, server should follow with
     * messages to set those villages' cloth counts.
     * @return the layout, or null if no villages.
     * @see #setVillageAndClothLayout(int[])
     * @see #getVillages()
     */
    public int[] getVillageAndClothLayout()
    {
        if ((villages == null) || villages.isEmpty())
            return null;

        int[] vcl = new int[ (2 * villages.size()) + 2 ];
        vcl[0] = numCloth;
        vcl[1] = SOCVillage.STARTING_CLOTH;  // in case it changes in a later version
        int i=2;
        Iterator<SOCVillage> villIter = villages.values().iterator();
        while (villIter.hasNext())
        {
            final SOCVillage v = villIter.next();
            vcl[i++] = v.getCoordinates();
            vcl[i++] = v.diceNum;
        }

        return vcl;
    }

    /**
     * For {@link #makeNewBoard(Map)}, with the {@link SOCGameOption#K_SC_CLVI Cloth Village} scenario,
     * create {@link SOCVillage}s at these node locations.  Adds to {@link #villages}.
     * Also set the board's "general supply" of cloth ({@link #setCloth(int)}).
     * @param villageNodesAndDice  Starting cloth count and each village's node coordinate and dice number,
     *        grouped in pairs, from {@link #getVillageAndClothLayout()}.
     * @throws NullPointerException  if array null
     * @throws IllegalArgumentException  if array length odd or &lt; 4
     */
    public void setVillageAndClothLayout(final int[] villageNodesAndDice)
        throws NullPointerException, IllegalArgumentException
    {
        final int L = villageNodesAndDice.length;
        if ((L < 4) || (L % 2 != 0))
            throw new IllegalArgumentException("bad length: " + L);

        if (villages == null)
            villages = new HashMap<Integer, SOCVillage>();

        setCloth(villageNodesAndDice[0]);
        final int startingCloth = villageNodesAndDice[1];
        for (int i = 2; i < L; i += 2)
        {
            final int node = villageNodesAndDice[i];
            villages.put(Integer.valueOf(node), new SOCVillage(node, villageNodesAndDice[i+1], startingCloth, this));
        }
    }

    /**
     * Get the {@link SOCVillage}s on the board, for scenario game option {@link SOCGameOption#K_SC_CLVI}.
     * Treat the returned data as read-only.
     * @return  villages, or null
     * @see #getVillageAtNode(int)
     * @see #getVillageAndClothLayout()
     * @see #getCloth()
     */
    public HashMap<Integer, SOCVillage> getVillages()
    {
        return villages;
    }

    /**
     * Get how many cloth does the board have in its "general supply" (used in some scenarios).
     * This supply is used if a village's {@link SOCVillage#takeCloth(int)}
     * returns less than the amount needed.
     * @see #takeCloth(int)
     * @see #distributeClothFromRoll(SOCGame, int)
     * @see #getVillageAtNode(int)
     */
    public int getCloth()
    {
        return numCloth;
    }

    /**
     * Set how many cloth the board currently has in its "general supply".
     * For use at client based on messages from server.
     * @param numCloth  Number of cloth
     */
    public void setCloth(final int numCloth)
    {
        this.numCloth = numCloth;
    }

    /**
     * Take this many cloth, if available, from the board's "general supply".
     * @param numTake  Number of cloth to try and take
     * @return  Number of cloth actually taken, a number from 0 to <tt>numTake</tt>.
     * @see #getCloth()
     * @see #distributeClothFromRoll(SOCGame, int)
     * @see SOCVillage#takeCloth(int)
     */
    public int takeCloth(int numTake)
    {
        if (numTake > numCloth)
        {
            numTake = numCloth;
            numCloth = 0;
        } else {
            numCloth -= numTake;
        }
        return numTake;
    }

    /**
     * Game action: Distribute cloth to players on a dice roll.
     * Calls {@link SOCVillage#distributeCloth(SOCGame)} for matching village, if any.
     * That calls {@link #takeCloth(int)}, {@link SOCPlayer#setCloth(int)}, etc.
     * Each player trading with that village gets at most 1 cloth.
     * For scenario game option {@link SOCGameOption#K_SC_CLVI _SC_CLVI}.
     * This and any other dice-roll methods are called at server only.
     * @param game  Game with this board
     * @param dice  Rolled dice number
     * @return  null, or results as an array:
     *   [ Cloth amount taken from general supply, Matching village node coordinate,
     *     Cloth amount given to player 0, to player 1, ... to player n ].
     */
    public int[] distributeClothFromRoll(SOCGame game, final int dice)
    {
        if ((villages == null) || villages.isEmpty())
            return null;

        Iterator<SOCVillage> villIter = villages.values().iterator();
        while (villIter.hasNext())
        {
            SOCVillage v = villIter.next();
            if (v.diceNum != dice)
                continue;

            return v.distributeCloth(game);
        }

        return null;
    }

    /**
     * Is this the coordinate of a land hex (not water)?
     * @param hexCoord  Hex coordinate, within the board's bounds
     * @return  True if land, false if water or not a valid hex coordinate
     * @see #isHexOnWater(int)
     * @see #isHexCoastline(int)
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
     * @see #isHexCoastline(int)
     * @since 2.0.00
     */
    @Override
    public boolean isHexOnWater(final int hexCoord)
    {
        return (getHexTypeFromCoord(hexCoord) == WATER_HEX);
    }

    /**
     * Is this land hex along the coastline (land/water border)?
     * Off the edge of the board is considered water.
     * {@link #FOG_HEX} is considered land here.
     * @param hexCoord  Hex coordinate, within the board's bounds
     * @return  true if this hex is adjacent to water, or at the edge of the board
     * @see #isEdgeCoastline(int)
     * @see #isNodeCoastline(int)
     * @throws IllegalArgumentException  if hexCoord is water or not a valid hex coordinate
     */
    public boolean isHexCoastline(final int hexCoord)
        throws IllegalArgumentException
    {
        final int htype = getHexTypeFromCoord(hexCoord);
        if ((htype <= WATER_HEX) || (htype > MAX_LAND_HEX_LG))
            throw new IllegalArgumentException("Not land (" + htype + "): 0x" + Integer.toHexString(hexCoord));

        // How many land hexes are adjacent?
        // Water, or off the board, aren't included.
        // So if there are 6, hex isn't coastal.

        final Vector<Integer> adjac = getAdjacentHexesToHex(hexCoord, false);
        return (adjac == null) || (adjac.size() < 6);
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

        final int[] hnodes = getAdjacentNodesToHex_arr(hexCoord);
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
     *<P>
     * Some nodes on land do not have a land area; instead of using this method
     * to determine if a node is on land, use {@link #isNodeOnLand(int)} or {@link #isNodeCoastline(int)}.
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
     * Is this node along the coastline (land/water border)?
     * Off the edge of the board is considered water.
     * {@link #FOG_HEX} is considered land here.
     * @param node  Node coordinate, not checked for validity
     * @return  true if this node's adjacent hexes are land and water,
     *           or if any adjacent hex would be off the edge of the board
     * @see #isEdgeCoastline(int)
     * @see #isHexCoastline(int)
     * @see #getAdjacentEdgesToNode_coastal(int)
     */
    public final boolean isNodeCoastline(final int node)
    {
        final List<Integer> hexes = getAdjacentHexesToNode(node);

        boolean hasLand = false, hasWater = (hexes.size() < 3);  // check size because we treat off-board as water
        for (Integer hex : hexes)
        {
            final int htype = getHexTypeFromCoord(hex);
            if ((htype <= MAX_LAND_HEX_LG) && (htype != WATER_HEX))
                hasLand = true;
            else
                hasWater = true;
        }

        return (hasLand && hasWater);
    }

    /**
     * Build an array with the land hex layout, for sending from server to client.
     * Contains 3 int elements per land hex:
     * Coordinate, Hex type (resource, as in {@link #SHEEP_HEX}), Dice Number (0 for desert, fog, water).
     * 0 is also the Dice Number for land hexes without dice numbers, as seen in some scenario layouts
     * like {@link SOCGameOption#K_SC_FTRI SC_FTRI}.
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
     * Set the land hex layout at client, sent from server.
     * Contains 3 int elements per land hex: Coordinate, Hex type (resource), Dice Number.
     * Clears landHexLayout, diceLayoutLg, numberLayoutLg,
     * nodesOnLand and legalRoadEdges before beginning.
     *<P>
     * After calling this, please call
     * {@link SOCGame#setPlayersLandHexCoordinates() game.setPlayersLandHexCoordinates()}.
     * After {@link #makeNewBoard(Map)} calculates the potential/legal settlements,
     * call each player's {@link SOCPlayer#setPotentialAndLegalSettlements(Collection, boolean, HashSet[])}.
     * @param  lh  the layout, or null if no land hexes, built by server's call to {@link #getLandHexLayout()}
     */
    public void setLandHexLayout(final int[] lh)
    {
        // Clear the previous contents:
        landHexLayout.clear();
        fogHiddenHexes.clear();
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
        final Integer ZERO_OBJ = Integer.valueOf(0);  // if needed for fogHiddenHexes
        for (int i = 0, ih = 0; i < lh.length; ++ih)
        {
            final int hexCoord = lh[i];  ++i;
            final int r = hexCoord >> 8,
                      c = hexCoord & 0xFF;
            hcoords[ih] = hexCoord;
            landHexLayout.add(Integer.valueOf(hexCoord));
            final int htype = lh[i];  ++i;
            hexLayoutLg[r][c] = htype;
            numberLayoutLg[r][c] = lh[i];  ++i;
            if (htype == FOG_HEX)
                fogHiddenHexes.put(Integer.valueOf(hexCoord), ZERO_OBJ);
        }
        cachedGetLandHexCoords = hcoords;
    }

    /**
     * Get the hex coordinates which are currently {@link #FOG_HEX}. Please treat as read-only.
     * At server, also contains each one's hidden hex type and dice number.
     * @return A map where key = hex coordinate, value = 0 or encoded hex type and dice number.
     *     May be empty, will never be {@code null}.
     * @see #getLandHexCoords()
     */
    public final HashMap<Integer, Integer> getFogHiddenHexes()
    {
        return fogHiddenHexes;
    }

    /**
     * Get the starting land area, if multiple "land areas" are used
     * and the players must start the game in a certain land area.
     *<P>
     * This is enforced during {@link #makeNewBoard(Map)}, by using
     * that land area for the only initial potential/legal settlement locations.
     *
     * @return the starting land area number; also its index in
     *   {@link #getLandAreasLegalNodes()}.
     *   0 if players can start anywhere and/or
     *   <tt>landAreasLegalNodes == null</tt>.
     * @see SOCPlayer#getStartingLandAreasEncoded()
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
     * See also {@link #getLegalSettlements()}
     * which returns the starting land area's nodes, or if no starting
     * land area, all nodes of all land areas.
     *<P>
     * See also {@link #getStartingLandArea()} to
     * see if the players must start the game in a certain land area.
     *
     * @return the land areas' nodes, or {@code null} if only one land area (one group of islands).
     *     Each index holds the nodes for that land area number.
     *     Index 0 is unused ({@code null}).
     * @see #getNodeLandArea(int)
     * @see #getFogHiddenHexes()
     */
    public HashSet<Integer>[] getLandAreasLegalNodes()
    {
        return landAreasLegalNodes;
    }

    /**
     * Get the legal settlement nodes, after {@link #makeNewBoard(Map)}.
     * For use mainly by SOCGame at server, and SOCPlayer at client when joining a game.
     *<P>
     * At the client, this returns an empty set if
     * {@link #setLegalSettlements(Collection, int, HashSet[])}
     * hasn't yet been called while the game is starting or while joining a game in progress.
     *<P>
     * See also {@link #getLandAreasLegalNodes()} which returns
     * all the legal nodes when multiple "land areas" are used.
     *
     * @return  the starting land area's legal nodes, or if no starting land area
     *     ({@link #getStartingLandArea()} == 0), all nodes of all land areas
     * @see SOCPlayer#setPotentialAndLegalSettlements(Collection, boolean, HashSet[])
     */
    public HashSet<Integer> getLegalSettlements()
    {
        if ((landAreasLegalNodes == null) || (startingLandArea == 0))
            return nodesOnLand;
        else
            return landAreasLegalNodes[startingLandArea];
    }

    /**
     * Set the board's legal settlement nodes, and calculate the Nodes On Land, legal roads and ship edges.
     * Called at client only, when server sends potential settlements following the board layout message.
     *<P>
     * Nodes On Land will be the union of all {@code lan[]} nodes; legal roads are calculated from Nodes On Land.
     * If {@code sla != 0}, then {@code lan[sla]} is also the set of potential settlement locations for initial
     * placement. If any nodes have been removed from {@code lan[sla]} due to scenario rules, but will be valid
     * after initial placement, those nodes must be referenced in Added Layout Part {@code "AL"} for that to
     * automatically happen and for their adjacent edges to be part of the legal roads calculated here. For details
     * see {@code "AL"} in the "Added Layout Parts" section of {@link #getAddedLayoutPart(String)}'s javadoc.
     *<P>
     * Call this only after {@link #setLandHexLayout(int[])}.
     * After calling this method, you can get the new legal road set
     * with {@link #initPlayerLegalRoads()}.
     *<P>
     * If this method hasn't yet been called, {@link #getLegalSettlements()} returns an empty set.
     *<P>
     * In some scenarios ({@code _SC_PIRI}), not all sea edges are legal for ships.
     * See {@link SOCPlayer#setRestrictedLegalShips(int[])}
     * and {@code SOCBoardAtServer.getLegalSeaEdges(SOCGame, int)}.
     *<P>
     * Server doesn't need to call this method, because {@code SOCBoardAtServer.makeNewBoard(Map)}
     * sets the contents of the same data structures.
     *
     * @param psNodes  The set of potential settlement node coordinates as {@link Integer}s;
     *    either a {@link HashSet} or {@link Vector}.
     *    If <tt>lan == null</tt>, this will also be used as the
     *    legal set of settlement nodes on land.
     * @param sla  The required starting Land Area number, or 0
     * @param lan If non-null, all Land Areas' legal node coordinates.
     *     Index 0 is ignored; land area numbers start at 1.
     * @throws IllegalStateException if Added Layout Part {@code "AL"} is present but badly formed (node list number 0,
     *     or a node list number not followed by a land area number). This Added Layout Part is rarely used,
     *     and this would be discovered quickly while testing the board layout that contained it.
     * @see SOCPlayer#setPotentialAndLegalSettlements(Collection, boolean, HashSet[])
     */
    public void setLegalSettlements
        (final Collection<Integer> psNodes, final int sla, final HashSet<Integer>[] lan)
        throws IllegalStateException
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

        initLegalRoadsFromLandNodes();  // throws IllegalStateException if malformed Added Layout Part "AL"
        initLegalShipEdges();
    }

    /**
     * Create and initialize a {@link SOCPlayer}'s legalRoads set.
     *<P>
     * Because the v3 board layout varies:
     * At the server, call this after {@link #makeNewBoard(Map)}.
     * At the client, call this after {@link #setLegalSettlements(Collection, int, HashSet[])}.
     *
     * @return the set of legal edge coordinates for roads, as a new Set of {@link Integer}s
     * @since 1.1.12
     * @see #isEdgeLegalRoad(int)
     */
    @Override
    public HashSet<Integer> initPlayerLegalRoads()
    {
        return new HashSet<Integer>(legalRoadEdges);
    }

    /**
     * Create and initialize a {@link SOCPlayer}'s legalShips set.
     * Contains all 6 edges of each water hex.
     *<P>
     * Because the v3 board layout varies:
     * At the server, call this after {@link #makeNewBoard(Map)}.
     * At the client, call this after {@link #setLegalSettlements(Collection, int, HashSet[])}.
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
     * @see #isHexAdjacentToHex(int, int)
     * @see #isHexInBounds(int, int)
     * @see #isHexCoastline(int)
     */
    @Override
    public Vector<Integer> getAdjacentHexesToHex(final int hexCoord, final boolean includeWater)
    {
        Vector<Integer> hexes = new Vector<Integer>();

        final int r = hexCoord >> 8,
                  c = hexCoord & 0xFF;

        for (int dir = 0; dir < 6; ++dir)
            getAdjacentHexes2Hex_AddIfOK(hexes, includeWater, r + A_HEX2HEX[dir][0], c + A_HEX2HEX[dir][1]);

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
        if (! isHexInBounds(r, c))  // also checks that it's a valid hex row
            return;

        if (includeWater
            || ((hexLayoutLg[r][c] <= MAX_LAND_HEX_LG)
                && (hexLayoutLg[r][c] != WATER_HEX)) )
        {
            addTo.addElement(Integer.valueOf((r << 8) | c));
        }
    }

    /**
     * Are these hexes adjacent?
     * @param hex1Coord  First hex coordinate; not checked for validity
     * @param hex2Coord  Second hex coordinate; not checked for validity
     * @return  true if adjacent
     * @see #getAdjacentHexesToHex(int, boolean)
     */
    public boolean isHexAdjacentToHex(final int hex1Coord, final int hex2Coord)
    {
        for (int dir = 0; dir < 6; ++dir)
        {
            if (hex2Coord == (hex1Coord + (A_HEX2HEX[dir][0] * 0x100) + A_HEX2HEX[dir][1]))
                return true;
        }
        return false;
    }

    /**
     * A list of the edge coordinates adjacent to this hex in all 6 directions.
     * (The 6 sides of this hex.)
     * Since all hexes have 6 edges, all edge coordinates are valid
     * if the hex coordinate is valid.
     *
     * @param hexCoord Coordinate of this hex; not checked for validity
     * @return  {@link ArrayList} with the 6 edges adjacent to this hex. Never returns {@code null} or empty.
     * @see #getAdjacentEdgesToHex_arr(int)
     * @see #isEdgeAdjacentToHex(int, int)
     */
    public List<Integer> getAdjacentEdgesToHex(final int hexCoord)
    {
        final ArrayList<Integer> edges = new ArrayList<Integer>(6);
        for (int dir = 0; dir < 6; ++dir)
            edges.add(Integer.valueOf(hexCoord + A_EDGE2HEX[dir][0] + A_EDGE2HEX[dir][1]));

        return edges;
    }

    /**
     * An array of the edge coordinates adjacent to this hex in all 6 directions.
     * (The 6 sides of this hex.)
     * Since all hexes have 6 edges, all edge coordinates are valid
     * if the hex coordinate is valid.
     *
     * @param hexCoord Coordinate of this hex; not checked for validity
     * @return  Array of the 6 edges adjacent to this hex. Never returns {@code null} or empty.
     * @see #getAdjacentEdgesToHex(int)
     * @see #isEdgeAdjacentToHex(int, int)
     */
    public int[] getAdjacentEdgesToHex_arr(final int hexCoord)
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
     * An array of the node coordinates adjacent to this hex in all 6 directions.
     * (The 6 corners of this hex.)
     * Since all hexes have 6 nodes, all node coordinates are valid
     * if the hex coordinate is valid.
     *
     * @param hexCoord Coordinate of this hex; not checked for validity
     * @return Array with node coordinate in all 6 directions,
     *           clockwise from top (northern point of hex):
     *           Index 0 is north, 1 is northeast, etc, 5 is northwest.
     *           Never returns {@code null} or empty.
     * @since 2.0.00
     * @see #getAdjacentNodesToHex(int)
     * @see #getAdjacentNodeToHex(int, int)
     */
    @Override
    public int[] getAdjacentNodesToHex_arr(final int hexCoord)
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
            if ((r > 1) && (c > 0))
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
            if ((r < (boardHeight-1)) && (c > 0))
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
     * @return the valid adjacent edges to this edge, as a Vector of Integer coordinates.
     *     If {@code coord} is off the board, none of its adjacents will be in bounds,
     *     and this method will return an empty list; never returns {@code null}.
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
            if (isEdgeInBounds(er, ec))
                edge.addElement(Integer.valueOf( (er << 8) | ec ));
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
        nodes.addElement(Integer.valueOf(narr[0]));
        nodes.addElement(Integer.valueOf(narr[1]));
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
                hexes.addElement(Integer.valueOf(nodeCoord - 0x0100));

            if (r < (boardHeight-1))
            {
                // SW: (r+1, c-1)
                if (c > 1)
                    hexes.addElement(Integer.valueOf((nodeCoord + 0x0100) - 1));

                // SE: (r+1, c+1)
                if (c < (boardWidth-1))
                    hexes.addElement(Integer.valueOf((nodeCoord + 0x0100) + 1));
            }
        }
        else
        {
            // South: (r+1, c)
            if (r < (boardHeight-1))
                hexes.addElement(Integer.valueOf(nodeCoord + 0x0100));

            if (r > 1)
            {
                // NW: (r-1, c-1)
                if (c > 1)
                    hexes.addElement(Integer.valueOf((nodeCoord - 0x0100) - 1));

                // NE: (r-1, c+1)
                if (c < (boardWidth-1))
                    hexes.addElement(Integer.valueOf((nodeCoord - 0x0100) + 1));
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
     * Get the coastal (land+water) edges adjacent to this node, if any.
     *<P>
     * Coastal edges are those with an adjacent land hex and an adjacent water hex.
     * {@link #FOG_HEX} is considered land here.
     * Hexes off the board are considered water.
     * Edges off the board are ignored, not checked here.
     * @param node  Node coordinate; not validated.  Should be a coastal node, with adjacent
     *          water and land hexes, although you can pass inland or at-sea nodes to this method.
     * @return  Coastal edges adjacent to {@code node}, or an empty list if none
     * @see #isEdgeCoastline(int)
     * @see #isNodeCoastline(int)
     */
    public final List<Integer> getAdjacentEdgesToNode_coastal(final int node)
    {
        ArrayList<Integer> coastEdges = new ArrayList<Integer>(3);

        for (int edge : getAdjacentEdgesToNode_arr(node))
        {
            if (edge == -9)
                continue;  // edge off the board

            boolean hasLand = false, hasWater = false;
            for (int hex : getAdjacentHexesToEdge_arr(edge))
            {
                if (hex == 0)
                {
                    hasWater = true;  // hex off the board
                } else {
                    final int htype = getHexTypeFromCoord(hex);
                    if ((htype <= MAX_LAND_HEX_LG) && (htype != WATER_HEX))
                        hasLand = true;
                    else
                        hasWater = true;
                }
            }

            if (hasLand && hasWater)
                coastEdges.add(Integer.valueOf(edge));
        }

        return coastEdges;
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
     * Is this hex coordinate within the board's boundaries,
     * not off the side of the board?
     * @param r  Hex coordinate's row; bounds-checked and validity-checked (only odd rows are hex coordinate rows)
     * @param c  Hex coordinate's column; bounds-checked but not validity-checked (could be a column betwen two hexes,
     *            or could be c == 1 although half the hex rows start at 2)
     * @see #getAdjacentHexesToHex(int, boolean)
     * @see #isHexAtBoardMargin(int)
     */
    public final boolean isHexInBounds(final int r, final int c)
    {
        if ((r <= 0) || (c <= 0) || (r >= boardHeight) || (c >= boardWidth))
            return false;  // not within the board's valid hex boundaries

        return ((r % 2) == 1);  // valid hex row number?
    }

    /**
     * Is this hex coordinate at the board's boundaries,
     * with one or more adjacent hexes off the side of the board?
     * @param hexCoord  Hex coordinate; not validity-checked
     * @see #isHexInBounds(int, int)
     */
    public final boolean isHexAtBoardMargin(final int hexCoord)
    {
        final int r = hexCoord >> 8,
                  c = hexCoord & 0xFF;

        return (r == 1) || (r == (boardHeight-1))
               || (c <= 2) || (c >= (boardWidth-2));

    }

    /**
     * Is this an edge coordinate within the board's boundaries,
     * not overlapping or off the side of the board?
     * TODO description... valid range for nodes(corners) of hexes laid out,
     * but doesn't check for "misalignment" in the middle of the board.
     * @param r  Node coordinate's row
     * @param c  Node coordinate's column
     * @see #isHexInBounds(int, int)
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
     * @see #isHexInBounds(int, int)
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
        // Tasks here are similar to adding a new port in placePort(type, edge):
        // If you update this method, consider updating that one too.

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
            nodeIDtoPortType = new HashMap<Integer, Integer>();
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

            if (edge < 0)
                continue;  // this port isn't currently placed on its SOCBoardLarge board: skip it

            final int[] nodes = getAdjacentNodesToEdge_arr(edge);
            placePort(ptype, -1, facing, nodes[0], nodes[1]);
        }
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
     * Find the port edge, if any, that touches this node.
     * @param node  Node coordinate.  If coordinate is not valid, it won't have a port.
     * @return  Port edge adjacent to this node, or -9 if none
     * @since 2.0.00
     */
    public int getPortEdgeFromNode(final int node)
    {
        final int[] ed = getAdjacentEdgesToNode_arr(node);  // unused will be -9

        // Note: Assumes ports will never share a node and be on adjacent edges.

        final int n = portsCount;
        for (int i = 0; i < n; ++i)
        {
            final int portEdge = portsLayout[n + i];
            if (portEdge < 0)
                continue;  // Guards against unused (-1) and any possible
                           // matching -9 from getAdjacentEdgesToNode_arr

            for (int j = 0; j < 3; ++j)
                if (portEdge == ed[j])
                    return portEdge;  // <--- Found a port on this edge ---
        }

        return -9;  // edge not found in port layout
    }

    /**
     * Given a coastal edge, find the "port facing" direction (towards land) for that edge.
     * Calculated by checking {@code edge}'s adjacent hexes for land and water;
     * if a hex is off the edge of the board, it's considered water.
     *
     * @param edge  A coastal edge; not validated here, must be a possible coordinate for an edge
     * @return  Coastal edge's port facing (towards land): In the range {@link SOCBoard#FACING_NE FACING_NE},
     *     {@link SOCBoard#FACING_E FACING_E}, ... {@link SOCBoard#FACING_NW FACING_NW}
     * @throws IllegalArgumentException  if {@code edge} is between 2 land hexes or 2 water hexes
     */
    public int getPortFacingFromEdge(final int edge)
        throws IllegalArgumentException
    {
        return getPortFacingFromEdge(edge, false);
    }

    /**
     * Solely for debugging, a version of {@link #getPortFacingFromEdge(int)} which can optionally
     * return 1 of the 2 valid facings for a given edge coordinate without checking the edge's adjacent
     * hexes for land and sea.
     * @param edge  Edge coordinate, not validated here
     * @param skipCoastalCheck  If true, skip checks for adjacent coastal hexes and return a facing
     *     based only on {@code edge}'s angle
     * @return  Edge's facing direction. See {@link #getPortFacingFromEdge(int)} for return value
     *     when {@code skipCoastalCheck} is false. When true, return value is 1 of the 2 valid facings
     *     from the edge's angle based on its coordinates:
     *     <UL>
     *       <LI> {@code "|"} edges: {@link #FACING_E}
     *       <LI> {@code "/"} edges: {@link #FACING_NW}
     *       <LI> {@code "\"} edges or invalid: {@link #FACING_NE}
     *     </UL>
     * @throws IllegalArgumentException  if {@code edge} is between 2 land hexes or 2 water hexes
     *     and {@code skipCoastalCheck} is false
     */
    final int getPortFacingFromEdge(final int edge, final boolean skipCoastalCheck)
        throws IllegalArgumentException
    {
        // similar to code in SOCBoardAtServer.makeNewBoard_checkPortLocationsConsistent

        final int r = (edge >> 8), c = (edge & 0xFF);
        final int facing;

        final int f1, f2;  // facings which make sense for this type of edge

        // "|" if r is odd
        if ((r % 2) == 1)
        {
            f1 = FACING_E;   f2 = FACING_W;
        }

        // "/" if (r/2,c) is even,odd or odd,even
        else if ((c % 2) != ((r/2) % 2))
        {
            f1 = FACING_NW;  f2 = FACING_SE;
        }
        else
        {
            // "\" if (r/2,c) is odd,odd or even,even
            f1 = FACING_NE;  f2 = FACING_SW;
        }

        if (skipCoastalCheck)
        {
            return f1;  // <--- Early return: called for debugging purposes ---
        }

        // if f1 faces land, f2 should face water
        int hex = getAdjacentHexToEdge(edge, f2);
        if ((hex == 0) || (getHexTypeFromCoord(hex) == WATER_HEX))
        {
            facing = f1;
        } else {
            // if f2 faces land, f1 should face water
            hex = getAdjacentHexToEdge(edge, f1);
            if ((hex == 0) || (getHexTypeFromCoord(hex) == WATER_HEX))
            {
                facing = f2;
            } else {
                throw new IllegalArgumentException("Edge 0x" + Integer.toHexString(edge) + " is between land hexes");
            }
        }

        // Hex in opposite-from-facing direction is water.
        // Make sure hex in facing direction is land.
        hex = getAdjacentHexToEdge(edge, facing);
        if ((hex == 0) || (getHexTypeFromCoord(hex) == WATER_HEX))
            throw new IllegalArgumentException("Edge 0x" + Integer.toHexString(edge) + " is between water hexes");

        return facing;
    }

    /**
     * For scenario option {@link SOCGameOption#K_SC_FTRI _SC_FTRI},
     * can a "gift" port at this edge be removed for placement elsewhere?
     *<P>
     * A port can't be removed from a Land Area where the player can place settlements.
     * So, must be in no land area (LA == 0), or in {@link #getPlayerExcludedLandAreas()}.
     * Does not check whether {@link SOCGameOption#K_SC_FTRI} is set.
     *
     * @param edge  Port's edge coordinate
     * @return  True if that edge has a port which can be removed
     * @see #removePort(int)
     * @see SOCGame#canRemovePort(SOCPlayer, int)
     * @see SOCGame#canPlacePort(SOCPlayer, int)
     */
    public boolean canRemovePort(final int edge)
    {
        final int n = portsCount;
        int i;
        for (i = 0; i < n; ++i)
        {
            if (edge == portsLayout[n + i])
                break;
        }
        if (i == n)
            return false;  // edge not found in port layout

        final int[] portNodes = getAdjacentNodesToEdge_arr(edge);
        for (i = 0; i <= 1; ++i)
        {
            final int la = getNodeLandArea(portNodes[i]);

            boolean ok = false;
            if (la == 0)
            {
                ok = true;
            }
            else if (playerExcludedLandAreas != null)
            {
                for (int j = 0; j < playerExcludedLandAreas.length; ++j)
                {
                    if (la == playerExcludedLandAreas[j])
                    {
                        ok = true;
                        break;
                    }
                }
            }

            if (! ok)
                return false;
        }

        return true;  // both nodes in land area 0 or in playerExcludedLandAreas
    }

    /**
     * For scenario option {@link SOCGameOption#K_SC_FTRI _SC_FTRI},
     * remove a "gift" port at this edge for placement elsewhere.
     *<P>
     * Assumes {@link #canRemovePort(int)} has already been called to validate.
     * See that method for necessary board conditions.
     *
     * @param edge  A port edge to be removed
     * @return  The type of port removed (in range {@link SOCBoard#MISC_PORT MISC_PORT}
     *     to {@link SOCBoard#WOOD_PORT WOOD_PORT})
     * @see SOCGame#removePort(SOCPlayer, int)
     * @see SOCGame#placePort(SOCPlayer, int, int)
     * @throws IllegalArgumentException  if {@code edge} not found in port layout
     */
    public int removePort(final int edge)
        throws IllegalArgumentException
    {
        final int n = portsCount;
        for (int i = 0; i < n; ++i)
        {
            if (edge == portsLayout[n + i])
            {
                portsLayout[n + i] = -1;

                final int[] nodes = getAdjacentNodesToEdge_arr(edge);
                final int node1Int = Integer.valueOf(nodes[0]),
                          node2Int = Integer.valueOf(nodes[1]);
                nodeIDtoPortType.remove(node1Int);
                nodeIDtoPortType.remove(node2Int);

                final int ptype = portsLayout[i];
                ports[ptype].remove((Object) node1Int);  // explicit cast: auto-unboxing would call remove(int)
                ports[ptype].remove((Object) node2Int);

                return portsLayout[i];  // pType
            }
        }

        // edge not found
        throw new IllegalArgumentException();
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

}
